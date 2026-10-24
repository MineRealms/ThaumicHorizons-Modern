package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import fr.wind_blade.isorropia.common.IsorropiaAspects;
import fr.wind_blade.isorropia.common.items.ItemsIS;
import fr.wind_blade.isorropia.common.items.misc.ItemSyringeBlood;
import fr.wind_blade.isorropia.common.menu.BloodInfuserMenu;
import fr.wind_blade.isorropia.common.platform.StackData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
//? if forge {
import net.minecraft.world.item.alchemy.PotionUtils;
//?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thaumic Horizons' Seroconverter (TH {@code TileBloodInfuser}): a blood syringe goes in the front slot, the
 * wizard dials up to eight units of the aspects they want, and the machine sucks those aspects one unit a tick
 * out of the essentia network until the syringe has soaked up exactly that much. It then bottles the mix as an
 * injection whose potion effects are the ones each aspect has an affinity for, strongest first, one effect per
 * two units of essentia — TH's research sums that up as "somewhat unpredictable".
 *
 * <p>Modes: idle, single treatment (stops after one injection), automatic (keeps going while blood and essentia
 * last).</p>
 */
public class TileBloodInfuser extends BlockEntity implements EssentiaTransport, MenuProvider {
    //? if forge {
    // TC4R 20740 resolves essentia peers through EssentiaCapabilities.TRANSPORT (EssentiaApi.transport
    // no longer falls back to an instanceof check on a Level), so expose the interface as a cap.
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        if (cap == dev.tc4port.thaumcraft.api.essentia.EssentiaCapabilities.TRANSPORT) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> (EssentiaTransport) this).cast();
        }
        return super.getCapability(cap, side);
    }
    //?}
    /** TH's {@code getSuctionAmount}: the pull the machine shows while it still wants a unit. */
    public static final int SUCTION = 128;
    /** TH dialled at most eight units of essentia into one syringe. */
    public static final int MAX_SELECTED = 8;

    /** The aspects the wizard has dialed, in dial order. */
    private final List<AspectId> selected = new ArrayList<>();
    /** The aspects that have actually soaked into the syringe so far. */
    private final Map<AspectId, Integer> acquired = new LinkedHashMap<>();
    @Nullable
    private AspectId currentlySucking;
    /** 0 idle, 1 single treatment, 2 automatic — TH's mode field. */
    private int mode;
    /** The colour the current mix bottles as; refreshed by {@link #getCurrentEffects()}. */
    private int colour = 0xFF0000;

    /** Slot 0 holds the blood syringe; slots 1..9 are the output rack. */
    private final net.minecraft.world.SimpleContainer items = new net.minecraft.world.SimpleContainer(10) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == 0 && stack.getItem() instanceof ItemSyringeBlood;
        }

        @Override
        public void setChanged() {
            TileBloodInfuser.this.setChanged();
        }
    };

    public TileBloodInfuser(BlockPos pos, BlockState state) {
        super(TilesIS.BLOOD_INFUSER.get(), pos, state);
    }

    /** The machine's slots, as the menu's inventory. */
    public net.minecraft.world.Container container() {
        return this.items;
    }

    // ---------------------------------------------------------------- menu plumbing

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.isorropia.blood_infuser");
    }

    @Override
    public BloodInfuserMenu createMenu(int id, Inventory inventory, Player player) {
        return new BloodInfuserMenu(id, inventory, this);
    }

    // ---------------------------------------------------------------- the effect table

    /**
     * TH's aspect-affinity table, keyed by this mapping's aspects. TH keyed it on TC6 constants; rows are keyed
     * here by TC4R id, and TH's cloth row lands on tutamen, its tree row on herba. TH's own potions (vis regen,
     * vacuum, shock, synthesis, vis exhaust, taint poison) have no 1.20.1 effect to land on, so their weights are
     * dropped.
     */
    private static final Map<AspectId, Map<MobEffect, Integer>> EFFECT_WEIGHTS = buildWeights();

    private static Map<AspectId, Map<MobEffect, Integer>> buildWeights() {
        Map<AspectId, Map<MobEffect, Integer>> table = new HashMap<>();
        table.put(IsorropiaAspects.AER, w(MobEffects.MOVEMENT_SPEED, 3, MobEffects.JUMP, 4,
                MobEffects.WATER_BREATHING, 3));
        table.put(IsorropiaAspects.TERRA, w(MobEffects.MOVEMENT_SLOWDOWN, 3, MobEffects.DIG_SPEED, 4,
                MobEffects.DAMAGE_BOOST, 1, MobEffects.DAMAGE_RESISTANCE, 2));
        table.put(IsorropiaAspects.IGNIS, w(MobEffects.MOVEMENT_SPEED, 1, MobEffects.DAMAGE_BOOST, 2,
                MobEffects.FIRE_RESISTANCE, 6, MobEffects.NIGHT_VISION, 1));
        table.put(IsorropiaAspects.AQUA, w(MobEffects.MOVEMENT_SPEED, 2, MobEffects.DIG_SPEED, 2,
                MobEffects.REGENERATION, 1, MobEffects.FIRE_RESISTANCE, 2, MobEffects.WATER_BREATHING, 3));
        table.put(IsorropiaAspects.ORDO, w(MobEffects.MOVEMENT_SLOWDOWN, 2, MobEffects.DIG_SLOWDOWN, 3,
                MobEffects.DAMAGE_RESISTANCE, 3, MobEffects.ABSORPTION, 2));
        table.put(IsorropiaAspects.PERDITIO, w(MobEffects.HARM, 1, MobEffects.CONFUSION, 1, MobEffects.HUNGER, 1,
                MobEffects.WEAKNESS, 2, MobEffects.POISON, 2, MobEffects.WITHER, 3));
        table.put(IsorropiaAspects.VACUOS, w(MobEffects.INVISIBILITY, 4, MobEffects.HUNGER, 2));
        table.put(IsorropiaAspects.LUX, w(MobEffects.BLINDNESS, 2, MobEffects.NIGHT_VISION, 8));
        table.put(IsorropiaAspects.TEMPESTAS, w(MobEffects.MOVEMENT_SPEED, 1, MobEffects.JUMP, 2,
                MobEffects.BLINDNESS, 1));
        table.put(IsorropiaAspects.MOTUS, w(MobEffects.MOVEMENT_SPEED, 5, MobEffects.JUMP, 5));
        table.put(IsorropiaAspects.GELUM, w(MobEffects.MOVEMENT_SLOWDOWN, 2, MobEffects.DIG_SLOWDOWN, 2,
                MobEffects.DAMAGE_RESISTANCE, 2, MobEffects.INVISIBILITY, 2, MobEffects.WEAKNESS, 2));
        table.put(IsorropiaAspects.VITREUS, w(MobEffects.DIG_SLOWDOWN, 2, MobEffects.DAMAGE_BOOST, 2,
                MobEffects.INVISIBILITY, 6));
        table.put(IsorropiaAspects.VICTUS, w(MobEffects.HEAL, 2, MobEffects.REGENERATION, 6,
                MobEffects.ABSORPTION, 2));
        table.put(IsorropiaAspects.VENENUM, w(MobEffects.DIG_SLOWDOWN, 1, MobEffects.CONFUSION, 1,
                MobEffects.HUNGER, 1, MobEffects.POISON, 6, MobEffects.WEAKNESS, 1));
        table.put(IsorropiaAspects.POTENTIA, w(MobEffects.MOVEMENT_SPEED, 2, MobEffects.DIG_SPEED, 2,
                MobEffects.DAMAGE_BOOST, 1, MobEffects.JUMP, 1));
        table.put(IsorropiaAspects.PERMUTATIO, w(MobEffects.MOVEMENT_SPEED, 2, MobEffects.DIG_SPEED, 2,
                MobEffects.JUMP, 1, MobEffects.WEAKNESS, 2));
        table.put(IsorropiaAspects.METALLUM, w(MobEffects.MOVEMENT_SLOWDOWN, 2, MobEffects.DIG_SPEED, 2,
                MobEffects.DAMAGE_BOOST, 2, MobEffects.DAMAGE_RESISTANCE, 4));
        table.put(IsorropiaAspects.MORTUUS, w(MobEffects.HARM, 6, MobEffects.CONFUSION, 1, MobEffects.HUNGER, 1,
                MobEffects.WEAKNESS, 1, MobEffects.WITHER, 1));
        table.put(IsorropiaAspects.VOLATUS, w(MobEffects.MOVEMENT_SPEED, 2, MobEffects.JUMP, 8));
        table.put(IsorropiaAspects.TENEBRAE, w(MobEffects.INVISIBILITY, 4, MobEffects.BLINDNESS, 5,
                MobEffects.NIGHT_VISION, 1));
        table.put(IsorropiaAspects.SPIRITUS, w(MobEffects.HEAL, 2, MobEffects.REGENERATION, 1,
                MobEffects.INVISIBILITY, 3, MobEffects.NIGHT_VISION, 2, MobEffects.ABSORPTION, 1));
        table.put(IsorropiaAspects.SANO, w(MobEffects.HEAL, 6, MobEffects.REGENERATION, 2, MobEffects.ABSORPTION, 2));
        table.put(IsorropiaAspects.ITER, w(MobEffects.MOVEMENT_SPEED, 8, MobEffects.JUMP, 2));
        table.put(IsorropiaAspects.ALIENIS, w(MobEffects.INVISIBILITY, 2, MobEffects.BLINDNESS, 2,
                MobEffects.NIGHT_VISION, 2));
        table.put(IsorropiaAspects.PRAECANTATIO, w(MobEffects.MOVEMENT_SPEED, 2, MobEffects.DIG_SPEED, 2,
                MobEffects.DAMAGE_BOOST, 2, MobEffects.DAMAGE_RESISTANCE, 2));
        table.put(IsorropiaAspects.VITIUM, w(MobEffects.WITHER, 2, MobEffects.POISON, 6));
        table.put(IsorropiaAspects.LIMUS, w(MobEffects.MOVEMENT_SLOWDOWN, 4, MobEffects.DIG_SLOWDOWN, 2,
                MobEffects.CONFUSION, 2, MobEffects.POISON, 2));
        table.put(IsorropiaAspects.HERBA, w(MobEffects.SATURATION, 2));
        table.put(IsorropiaAspects.BESTIA, w(MobEffects.MOVEMENT_SPEED, 1, MobEffects.DAMAGE_BOOST, 5,
                MobEffects.REGENERATION, 1, MobEffects.WATER_BREATHING, 1, MobEffects.NIGHT_VISION, 1,
                MobEffects.ABSORPTION, 1));
        table.put(IsorropiaAspects.CORPUS, w(MobEffects.REGENERATION, 2, MobEffects.ABSORPTION, 2,
                MobEffects.SATURATION, 6));
        table.put(IsorropiaAspects.EXANIMIS, w(MobEffects.HUNGER, 2, MobEffects.WITHER, 2, MobEffects.HARM, 6));
        table.put(IsorropiaAspects.HUMANUS, w(MobEffects.HEAL, 3, MobEffects.REGENERATION, 3,
                MobEffects.ABSORPTION, 4));
        table.put(IsorropiaAspects.COGNITIO, w(MobEffects.NIGHT_VISION, 2));
        table.put(IsorropiaAspects.SENSUS, w(MobEffects.INVISIBILITY, 4, MobEffects.NIGHT_VISION, 6));
        table.put(IsorropiaAspects.MESSIS, w(MobEffects.SATURATION, 6));
        table.put(IsorropiaAspects.PERFODIO, w(MobEffects.DIG_SPEED, 10));
        table.put(IsorropiaAspects.INSTRUMENTUM, w(MobEffects.MOVEMENT_SPEED, 1, MobEffects.DIG_SPEED, 8,
                MobEffects.DAMAGE_RESISTANCE, 1));
        table.put(IsorropiaAspects.METO, w(MobEffects.DIG_SPEED, 2, MobEffects.HEAL, 1, MobEffects.SATURATION, 5));
        table.put(IsorropiaAspects.TELUM, w(MobEffects.DIG_SPEED, 2, MobEffects.DAMAGE_BOOST, 8));
        table.put(IsorropiaAspects.TUTAMEN, w(MobEffects.DAMAGE_RESISTANCE, 8, MobEffects.FIRE_RESISTANCE, 2));
        table.put(IsorropiaAspects.FAMES, w(MobEffects.WEAKNESS, 1, MobEffects.SATURATION, 8));
        table.put(IsorropiaAspects.LUCRUM, w(MobEffects.SATURATION, 2));
        table.put(IsorropiaAspects.FABRICO, w(MobEffects.DIG_SLOWDOWN, 2, MobEffects.HEAL, 2,
                MobEffects.DAMAGE_RESISTANCE, 2, MobEffects.ABSORPTION, 4));
        table.put(IsorropiaAspects.MACHINA, w(MobEffects.MOVEMENT_SPEED, 2, MobEffects.DIG_SPEED, 4,
                MobEffects.DAMAGE_BOOST, 4));
        table.put(IsorropiaAspects.VINCULUM, w(MobEffects.MOVEMENT_SLOWDOWN, 8, MobEffects.DIG_SLOWDOWN, 2));
        return table;
    }

    private static Map<MobEffect, Integer> w(Object... pairs) {
        Map<MobEffect, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            //? if forge {
            map.put((MobEffect) pairs[i], (Integer) pairs[i + 1]);
            //?} else {
            /*map.put((MobEffect) ((net.minecraft.core.Holder<?>) pairs[i]).value(), (Integer) pairs[i + 1]);
            *///?}
        }
        return map;
    }

    /** The effects the given aspect has an affinity for, as TH's {@code getEffects}; null when none. */
    @Nullable
    public static Map<MobEffect, Integer> effectsFor(AspectId aspect) {
        return EFFECT_WEIGHTS.get(aspect);
    }

    // ---------------------------------------------------------------- TH's updateEntity

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileBloodInfuser infuser) {
        infuser.tick();
    }

    private void tick() {
        this.currentlySucking = null;
        ItemStack syringe = this.items.getItem(0);
        if (this.mode > 0 && !syringe.isEmpty() && this.emptyOutputSlot()) {
            for (AspectId aspect : this.selected) {
                if (this.acquired.getOrDefault(aspect, 0) < this.countSelected(aspect)) {
                    this.currentlySucking = aspect;
                    break;
                }
            }
            if (this.currentlySucking == null && !this.acquired.isEmpty()) {
                this.items.removeItem(0, 1);
                ItemStack injection = new ItemStack(ItemsIS.SYRINGE_INJECTION.get());
                List<MobEffectInstance> effects = this.getCurrentEffects();
                //? if forge {
                PotionUtils.setCustomEffects(injection, effects);
                //?} else {
                /*net.minecraft.nbt.ListTag saved = new net.minecraft.nbt.ListTag();
                for (net.minecraft.world.effect.MobEffectInstance instance : effects) {
                    if (instance.save() instanceof net.minecraft.nbt.CompoundTag compound) {
                        saved.add(compound);
                    }
                }
                StackData.update(injection, tag -> tag.put("effects", saved));
                *///?}
                StackData.update(injection, tag -> tag.putInt("color", this.colour));
                for (int i = 1; i <= 9; i++) {
                    if (this.items.getItem(i).isEmpty()) {
                        this.items.setItem(i, injection);
                        break;
                    }
                }
                this.acquired.clear();
                if (this.mode == 1) {
                    this.mode = 0;
                }
            } else {
                this.tryDrawEssentia();
            }
        }
        if (this.currentlySucking != null) {
            this.sync();
        }
    }

    private int countSelected(AspectId aspect) {
        int count = 0;
        for (AspectId id : this.selected) {
            if (id.equals(aspect)) {
                count++;
            }
        }
        return count;
    }

    private boolean emptyOutputSlot() {
        for (int i = 1; i <= 9; i++) {
            if (this.items.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** TH's {@code tryDrawEssentia}: one unit of the aspect being soaked from any neighbour but the top. */
    private void tryDrawEssentia() {
        if (!(this.level instanceof ServerLevel server) || this.currentlySucking == null) {
            return;
        }
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) {
                continue;
            }
            EssentiaTransport transport = ThaumcraftApiHelper.getConnectableTransport(server, this.worldPosition, dir);
            if (transport == null) {
                continue;
            }
            Direction opposite = dir.getOpposite();
            if (transport.essentiaAmount(opposite) > 0 && transport.suctionAmount(opposite) < this.suctionAmount(null)
                    && this.suctionAmount(null) >= transport.minimumSuction()) {
                int drawn = EssentiaApi.take(server, transport, this.currentlySucking, 1, opposite,
                        EssentiaTransferMode.EXECUTE);
                if (drawn > 0) {
                    this.acquired.merge(this.currentlySucking, drawn, Integer::sum);
                    this.setChanged();
                    this.sync();
                    return;
                }
            }
        }
    }

    /**
     * TH's {@code getCurrentEffects}: one effect per two units of essentia, each pick the strongest remaining
     * affinity; amplifier is the winning weight over thirty and duration its weight in hundred-ticks (instant
     * heal and harm last a single tick). Also blends the injection's colour from the aspects' own colours.
     */
    public List<MobEffectInstance> getCurrentEffects() {
        List<MobEffectInstance> effects = new ArrayList<>();
        Map<MobEffect, Integer> weights = new HashMap<>();
        int totalEssentia = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (Map.Entry<AspectId, Integer> entry : this.dialled().entrySet()) {
            Map<MobEffect, Integer> aspectWeights = EFFECT_WEIGHTS.get(entry.getKey());
            if (aspectWeights == null) {
                continue;
            }
            for (Map.Entry<MobEffect, Integer> weight : aspectWeights.entrySet()) {
                weights.merge(weight.getKey(), entry.getValue() * weight.getValue(), Integer::sum);
            }
            totalEssentia += entry.getValue();
            AspectDefinition definition = AspectApi.get(entry.getKey());
            int aspectColour = definition == null ? 0xFFFFFF : definition.color() & 0xFFFFFF;
            red += (long) (aspectColour >> 16 & 0xFF) * entry.getValue();
            green += (long) (aspectColour >> 8 & 0xFF) * entry.getValue();
            blue += (long) (aspectColour & 0xFF) * entry.getValue();
        }
        if (totalEssentia > 0) {
            red /= totalEssentia;
            green /= totalEssentia;
            blue /= totalEssentia;
        }
        this.colour = (int) (red << 16 | green << 8 | blue);
        for (int i = 0; i < (totalEssentia + 1) / 2; i++) {
            MobEffect largest = null;
            int largestWeight = 0;
            for (Map.Entry<MobEffect, Integer> weight : weights.entrySet()) {
                if (weight.getValue() > largestWeight) {
                    largestWeight = weight.getValue();
                    largest = weight.getKey();
                }
            }
            if (largest == null) {
                break;
            }
            //? if forge {
            boolean instant = largest == MobEffects.HEAL || largest == MobEffects.HARM;
            //?} else {
            /*boolean instant = largest == MobEffects.HEAL.value() || largest == MobEffects.HARM.value();
            *///?}
            //? if forge {
            effects.add(new MobEffectInstance(largest, instant ? 1 : 100 * largestWeight, largestWeight / 30));
            //?} else {
            /*effects.add(new MobEffectInstance(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(largest), instant ? 1 : 100 * largestWeight, largestWeight / 30));
            *///?}
            weights.remove(largest);
        }
        return effects;
    }

    /** TH's {@code color} tag value for the current selection (blended from the aspects' colours). */
    public int mixColour() {
        this.getCurrentEffects();
        return this.colour;
    }

    /** The dialled aspects with their multiplicities, for rendering and probes. */
    public Map<AspectId, Integer> dialled() {
        Map<AspectId, Integer> dial = new LinkedHashMap<>();
        for (AspectId aspect : this.selected) {
            dial.merge(aspect, 1, Integer::sum);
        }
        return dial;
    }

    /** TH's dial buttons: one more unit of the aspect, up to TH's eight. */
    public boolean addSelected(AspectId aspect) {
        if (this.selected.size() >= MAX_SELECTED) {
            return false;
        }
        this.selected.add(aspect);
        this.setChanged();
        this.sync();
        return true;
    }

    public boolean removeSelected(AspectId aspect) {
        boolean removed = this.selected.remove(aspect);
        if (removed) {
            this.setChanged();
            this.sync();
        }
        return removed;
    }

    public int getMode() {
        return this.mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        this.setChanged();
        this.sync();
    }

    /** TH's {@code hasBlood}: the renderer shows the syringe on the side while anything is loaded. */
    public boolean hasBlood() {
        for (int i = 0; i < 10; i++) {
            if (!this.items.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- essentia: in from the sides

    @Override
    public boolean isConnectable(Direction face) {
        return face != Direction.UP;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return face != Direction.UP;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return face == Direction.UP ? null : this.currentlySucking;
    }

    @Override
    public int suctionAmount(@Nullable Direction face) {
        return face == Direction.UP ? 0 : (this.currentlySucking != null ? SUCTION : 0);
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (amount <= 0 || face == Direction.UP) {
            return 0;
        }
        if (mode.executes()) {
            this.acquired.merge(aspect, amount, Integer::sum);
            this.setChanged();
            this.sync();
        }
        return 1;
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        return 0;
    }

    @Override
    public int minimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    // ---------------------------------------------------------------- state for the screen and probes

    public ItemStack syringe() {
        return this.items.getItem(0);
    }

    public ItemStack output(int slot) {
        return this.items.getItem(slot + 1);
    }

    @Nullable
    public AspectId currentlySucking() {
        return this.currentlySucking;
    }

    public int acquiredAmount(AspectId aspect) {
        return this.acquired.getOrDefault(aspect, 0);
    }

    // ---------------------------------------------------------------- sync + persistence

    private void sync() {
        TileSync.toWatchers(this);
    }

    /**
     * The ten slots with explicit indices — {@link net.minecraft.world.SimpleContainer#createTag} would save a
     * compact list, and its {@code fromTag} refills by list position, so empty slots would collapse and shift
     * everything left of the first gap.
     */
    //? if forge {
    private ListTag saveItems() {
    //?} else {
    /*private ListTag saveItems(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        ListTag list = new ListTag();
        for (int i = 0; i < 10; i++) {
            ItemStack stack = this.items.getItem(i);
            if (!stack.isEmpty()) {
                //? if forge {
                CompoundTag entry = stack.save(new CompoundTag());
                //?} else {
                /*CompoundTag entry = (CompoundTag) stack.save(registries);
                *///?}
                entry.putByte("Slot", (byte) i);
                list.add(entry);
            }
        }
        return list;
    }

    //? if forge {
    private void loadItems(ListTag list) {
    //?} else {
    /*private void loadItems(ListTag list, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        for (int i = 0; i < 10; i++) {
            this.items.setItem(i, ItemStack.EMPTY);
        }
        for (Tag entry : list) {
            int slot = ((CompoundTag) entry).getByte("Slot") & 0xFF;
            if (slot < 10) {
                //? if forge {
                this.items.setItem(slot, ItemStack.of((CompoundTag) entry));
                //?} else {
                /*this.items.setItem(slot, ItemStack.parseOptional(registries, (CompoundTag) entry));
                *///?}
            }
        }
    }

    private ListTag writeSelected() {
        ListTag selected = new ListTag();
        for (AspectId aspect : this.selected) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", aspect.serialized());
            selected.add(entry);
        }
        return selected;
    }

    private ListTag writeAcquired() {
        ListTag acquired = new ListTag();
        for (Map.Entry<AspectId, Integer> entry : this.acquired.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putString("key", entry.getKey().serialized());
            compound.putInt("amount", entry.getValue());
            acquired.add(compound);
        }
        return acquired;
    }

    private void readDial(CompoundTag tag) {
        this.selected.clear();
        for (Tag entry : tag.getList("Selected", Tag.TAG_COMPOUND)) {
            AspectId aspect = AspectId.parse(((CompoundTag) entry).getString("key"));
            if (aspect != null) {
                this.selected.add(aspect);
            }
        }
        this.acquired.clear();
        for (Tag entry : tag.getList("Acquired", Tag.TAG_COMPOUND)) {
            CompoundTag compound = (CompoundTag) entry;
            AspectId aspect = AspectId.parse(compound.getString("key"));
            if (aspect != null) {
                this.acquired.put(aspect, compound.getInt("amount"));
            }
        }
    }

    //? if forge {
    @Override
    public CompoundTag getUpdateTag() {
    //?} else {
    /*@Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", this.mode);
        //? if forge {
        tag.put("Items", this.saveItems());
        //?} else {
        /*tag.put("Items", this.saveItems(registries));
        *///?}
        tag.put("Selected", this.writeSelected());
        tag.put("Acquired", this.writeAcquired());
        if (this.currentlySucking != null) {
            tag.putString("Sucking", this.currentlySucking.serialized());
        }
        return tag;
    }

    //? if forge {
    @Override
    public void handleUpdateTag(CompoundTag tag) {
    //?} else {
    /*@Override
        public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        this.mode = tag.getInt("Mode");
        //? if forge {
        this.loadItems(tag.getList("Items", Tag.TAG_COMPOUND));
        //?} else {
        /*this.loadItems(tag.getList("Items", Tag.TAG_COMPOUND), registries);
        *///?}
        this.readDial(tag);
        this.currentlySucking = tag.contains("Sucking") ? AspectId.parse(tag.getString("Sucking")) : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    //? if forge {
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
    //?} else {
    /*@Override
        public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        if (packet.getTag() != null) {
            //? if forge {
            this.handleUpdateTag(packet.getTag());
            //?} else {
            /*this.handleUpdateTag(packet.getTag(), registries);
            *///?}
        }
    }

    //? if forge {
    @Override
    protected void saveAdditional(CompoundTag tag) {
    //?} else {
    /*@Override
        protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        super.saveAdditional(tag);        //?} else {
        /*        super.saveAdditional(tag, registries);
        *///?}
        tag.putInt("Mode", this.mode);
        //? if forge {
        tag.put("Items", this.saveItems());
        //?} else {
        /*tag.put("Items", this.saveItems(registries));
        *///?}
        tag.put("Selected", this.writeSelected());
        tag.put("Acquired", this.writeAcquired());
    }

    //? if forge {
    @Override
    public void load(CompoundTag tag) {
    //?} else {
    /*@Override
        protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        super.load(tag);        //?} else {
        /*        super.loadAdditional(tag, registries);
        *///?}
        this.mode = tag.getInt("Mode");
        //? if forge {
        this.loadItems(tag.getList("Items", Tag.TAG_COMPOUND));
        //?} else {
        /*this.loadItems(tag.getList("Items", Tag.TAG_COMPOUND), registries);
        *///?}
        this.readDial(tag);
        this.currentlySucking = null;
    }
}
