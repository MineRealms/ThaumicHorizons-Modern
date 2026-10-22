package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import dev.tc4port.thaumcraft.api.machine.InfusionPedestal;
import dev.tc4port.thaumcraft.api.node.VisNetworkApi;
import fr.wind_blade.isorropia.common.IsorropiaAspects;
import fr.wind_blade.isorropia.common.blocks.BlockCurativeVat;
import fr.wind_blade.isorropia.common.blocks.BlocksIS;
import fr.wind_blade.isorropia.common.compat.ThaumBridge;
import fr.wind_blade.isorropia.common.network.ClientFxPacket;
import fr.wind_blade.isorropia.common.sounds.SoundsIS;
import fr.wind_blade.isorropia.common.curative.CurativeEffects;
import fr.wind_blade.isorropia.common.infusion.CelestialBody;
import fr.wind_blade.isorropia.common.infusion.CreatureInfusionRecipe;
import fr.wind_blade.isorropia.common.infusion.InfusionRecipes;
import fr.wind_blade.isorropia.common.infusion.SelfInfusionRecipe;
import fr.wind_blade.isorropia.common.vat.VatStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The curative vat. While idle it heals the creature soaking in it, one essentia at a time. A wand tap on the
 * modified matrix above it matches the items on the pedestals around the vat and the creature inside against a
 * {@link CreatureInfusionRecipe} (or a {@link SelfInfusionRecipe} in a reincarnation body) and the infusion
 * runs one cycle per second, draining celestial aura, essentia, pedestal items and vis while instability
 * builds up behind every step and occasionally throws something back.
 *
 * <p>A creature is put in the vat in a soul jar and the player climbs in through any of its blocks; upstream
 * let a creature infusion take a contained player as its subject too — the effigy path it used instead needs
 * the vat gui this port does not have — and only organ recipes target "any", so a player can gain organs but
 * is never turned into another species.
 *
 * <p>Every vat block is this same block entity; only the block at the top centre (variant TOP) is the master
 * and holds the state. The other vat blocks forward their essentia endpoints to it.
 */
public class TileVat extends BlockEntity implements EssentiaTransport, AspectContainerView {
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
    public static final int MODE_IDLE = 0;
    public static final int MODE_INFUSING = 2;
    public static final int MODE_EFFIGY = 3;
    public static final int MODE_SELF_READY = 4;

    /** One infusion cycle. */
    public static final int CYCLE_TICKS = 20;
    private static final int PEDESTAL_RANGE = 12;
    private static final int PEDESTAL_RANGE_Y = 10;
    private static final float MAX_INSTABILITY = 25.0F;
    private static final float FLUX_EXPUNGE_AT = 30.0F;
    private static final float FLUX_DESTROY_AT = 300.0F;
    private static final int EFFIGY_ESSENTIA = 30;
    private static final int SUCTION = 128;

    private int mode = MODE_IDLE;
    private boolean active;
    private float stability;
    private float fluxStocked;
    private boolean expunge;
    private float celestialAura;
    private int celestialAuraNeeded;
    private CelestialBody celestialBody = CelestialBody.NONE;
    private boolean hasEffigy;
    private int effigyEssentia;
    private int cooldown;
    private int count;
    private int cycleTicks = CYCLE_TICKS;
    /** Rises while the vat works and falls when it stops; the matrix turns and shakes with it. */
    private float startUp;
    /** How far into an infusion the vat is, capped, used for the rendering of instability. */
    private int craftCount;
    private float stabilityReplenish;
    /** The running recipe's instability, as the client last heard it; the client knows no recipes. */
    private int syncedInstability;
    /** True while the sun or moon is being drawn on; the client rains light into the vat for as long as it is. */
    private boolean drawingAura;
    /** The game tick the client last spawned the absorption for; it is spawned once a tick, not once a frame. */
    public long lastAuraEffectTick = -1L;

    private final Map<AspectId, Integer> stored = new LinkedHashMap<>();
    private final Map<AspectId, Integer> needed = new LinkedHashMap<>();
    private final Map<AspectId, Integer> demanded = new LinkedHashMap<>();
    private final Map<VisChannel, Integer> visNeeded = new LinkedHashMap<>();
    private final Set<ResourceLocation> selfInfusions = new LinkedHashSet<>();

    @Nullable
    private ResourceLocation creatureRecipe;
    @Nullable
    private ResourceLocation selfRecipe;
    @Nullable
    private UUID infusionBy;
    private final List<ItemStack> components = new ArrayList<>();
    private final List<ItemStack> consumed = new ArrayList<>();
    @Nullable
    private List<BlockPos> pedestals;

    @Nullable
    private LivingEntity contained;
    @Nullable
    private UUID containedId;
    @Nullable
    private CompoundTag containedNbt;
    private boolean breaking;

    public TileVat(BlockPos pos, BlockState state) {
        super(TilesIS.VAT.get(), pos, state);
    }

    /** True while the vat is taking itself apart, so the blocks it replaces do not start over. */
    public boolean isBreaking() {
        return breaking;
    }

    /** Starts venting the flux the vat has accumulated; the matrix asks for this when the player sneaks. */
    public void ventFlux() {
        if (!expunge && fluxStocked > 0.0F) {
            expunge = true;
            sync();
        }
    }

    public boolean isMaster() {
        // A vat block entity can outlive its block — a tube driven through the shell by a player or a probe
        // leaves the old entity behind — and reading the property off a foreign blockstate throws.
        BlockState state = getBlockState();
        return state.is(BlocksIS.CURATIVE_VAT.get())
                && state.getValue(BlockCurativeVat.VARIANT) == BlockCurativeVat.Type.TOP;
    }

    /** The master of the vat this block belongs to; this block entity itself for the top centre. */
    @Nullable
    public TileVat master() {
        if (isMaster()) {
            return this;
        }
        return level == null ? null : VatStructure.findMaster(level, worldPosition);
    }

    /** The vat's mesh is four blocks tall and two wide, so the renderer must not be clipped to this block. */
    //? if forge {
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(2.0, 4.0, 2.0);
    }
    //?}

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileVat vat) {
        if (vat.isMaster() && level instanceof ServerLevel server) {
            vat.tickMaster(server);
        }
    }

    /**
     * The idle modes are a reading of the vat's contents rather than a state of their own, so they are
     * recomputed instead of remembered: a creature in the vat always means curing, an effigy with nothing in
     * the vat always means soaking. A soaked effigy is the exception — a ready body keeps waiting in mode 4
     * until something takes it, which is what the soul beacon's rebirth and the self infusions read.
     */
    private void normalizeMode() {
        if (mode == MODE_INFUSING) {
            return;
        }
        if (mode == MODE_SELF_READY && contained == null && hasEffigy) {
            return;
        }
        mode = hasEffigy && contained == null ? MODE_EFFIGY : MODE_IDLE;
    }

    private void tickMaster(ServerLevel level) {
        count++;
        normalizeMode();
        updateStartUp();
        updateCraftCount();
        if (mode != MODE_INFUSING && !expunge && contained == null && !hasEffigy) {
            return;
        }
        if (fluxStocked >= FLUX_DESTROY_AT) {
            if (mode == MODE_INFUSING) {
                finishInfusion(false);
            }
            destroyStructure();
            return;
        }
        if (!expunge && fluxStocked >= FLUX_EXPUNGE_AT) {
            if (mode == MODE_INFUSING) {
                finishInfusion(false);
            }
            expunge = true;
            level.playSound(null, worldPosition.above(), SoundsIS.CURATIVE_EXPUNGE_FLUX_START.get(),
                    SoundSource.BLOCKS, 0.6F, 1.0F);
            sync();
        }
        if (expunge) {
            if (count % cycleTicks == 0) {
                fluxStocked = Math.max(0.0F, fluxStocked - 1.0F);
                ThaumBridge.releaseFlux(level, worldPosition.above(), 1);
                level.playSound(null, worldPosition.above(), SoundsIS.CURATIVE_EXPUNGE_FLUX_LOOP.get(),
                        SoundSource.BLOCKS, 0.5F, 1.0F);
                if (fluxStocked <= 0.0F) {
                    expunge = false;
                    level.playSound(null, worldPosition.above(), SoundsIS.CURATIVE_EXPUNGE_FLUX_END.get(),
                            SoundSource.BLOCKS, 0.6F, 1.0F);
                }
                sync();
            }
            return;
        }
        resolveContained(level);
        if (contained != null && !contained.isAlive()) {
            destroyStructure();
            return;
        }
        if (mode == MODE_IDLE) {
            curativeUpdate(level);
        } else if (mode == MODE_EFFIGY) {
            effigyUpdate(level);
        } else if (mode == MODE_INFUSING && active) {
            // Upstream drew its essentia every tick, not once per cycle: through a tube, one unit per aspect
            // per cycle would take a 50+50 recipe a hundred seconds to feed.
            pullInfusionEssentia(level);
            if (count % cycleTicks == 0) {
                if (count % (cycleTicks * 20) == 0) {
                    pedestals = null;
                    refreshStability(level);
                }
                infusionCycle(level);
            }
        }
    }

    /** The vat winds up to full over a few seconds and unwinds the same way, as upstream's updateStartUp. */
    private void updateStartUp() {
        float step = Math.max(startUp / 10.0F, 0.001F);
        if (active && mode == MODE_INFUSING) {
            startUp = Math.min(1.0F, startUp + step);
        } else {
            startUp = Math.max(0.0F, startUp - step);
        }
    }

    /** Climbing one a tick while infusing and dropping two while idle is what drives the matrix's glow. */
    private void updateCraftCount() {
        craftCount = mode == MODE_INFUSING ? Math.min(50, craftCount + 1) : Math.max(0, craftCount - 2);
    }

    /** Heals the creature in the vat: one essentia of whichever cure applies, then its cooldown. */
    private void curativeUpdate(ServerLevel level) {
        clearDemand();
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (contained == null) {
            return;
        }
        for (CurativeEffects.Effect effect : CurativeEffects.EFFECTS) {
            if (!effect.canApply().test(contained)) {
                continue;
            }
            if (draw(effect.aspect(), 1)) {
                effect.apply().accept(contained);
                cooldown = effect.cooldown();
            } else {
                demand(effect.aspect(), 1);
                pullEssentia(level, effect.aspect());
            }
            return;
        }
    }

    /** The corpse effigy soaks up victus until it is a body a soul can be reborn into. */
    private void effigyUpdate(ServerLevel level) {
        clearDemand();
        if (contained != null) {
            return;
        }
        if (effigyEssentia >= EFFIGY_ESSENTIA) {
            mode = MODE_SELF_READY;
            sync();
            return;
        }
        if (draw(IsorropiaAspects.VICTUS, 1)) {
            effigyEssentia++;
            sync();
        } else {
            demand(IsorropiaAspects.VICTUS, 1);
            pullEssentia(level, IsorropiaAspects.VICTUS);
        }
    }

    // ---------------------------------------------------------------- infusion

    /**
     * Starts an infusion from the pedestals around the vat, as a single wand tap on the matrix does upstream.
     * A contained player is a legitimate subject: the effigy path upstream used instead needs the vat gui this
     * port does not have, and only organ recipes target "any", so a player gains organs and is never turned
     * into another species. A tap that finds nothing to infuse changes nothing and the vat stays put.
     */
    public boolean startInfusion(Player player) {
        if (!(level instanceof ServerLevel server) || mode == MODE_INFUSING) {
            return false;
        }
        List<ItemStack> items = pedestalItems();
        if (items.isEmpty()) {
            return false;
        }
        boolean self = mode == MODE_SELF_READY && contained == null;
        Optional<CreatureInfusionRecipe> creature = self ? Optional.empty()
                : InfusionRecipes.findCreature(player, contained, items);
        Optional<SelfInfusionRecipe> own = self ? InfusionRecipes.findSelf(player, items) : Optional.empty();
        if (creature.isEmpty() && own.isEmpty()) {
            return false;
        }
        components.clear();
        components.addAll(items);
        consumed.clear();
        needed.clear();
        stored.clear();
        visNeeded.clear();
        infusionBy = player.getUUID();
        stability = 0.0F;
        celestialAura = 0.0F;
        celestialAuraNeeded = 0;
        celestialBody = CelestialBody.NONE;
        if (creature.isPresent()) {
            CreatureInfusionRecipe recipe = creature.get();
            creatureRecipe = recipe.id();
            selfRecipe = null;
            needed.putAll(recipe.aspects());
            visNeeded.putAll(recipe.vis());
            celestialBody = recipe.celestialBody();
            celestialAuraNeeded = recipe.celestialAura();
        } else {
            SelfInfusionRecipe recipe = own.get();
            selfRecipe = recipe.id();
            creatureRecipe = null;
            needed.putAll(recipe.aspects());
        }
        mode = MODE_INFUSING;
        active = true;
        count = 0;
        pedestals = null;
        refreshStability(server);
        server.playSound(null, worldPosition, SoundsIS.CURATIVE_INFUSION_START.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        sync();
        return true;
    }

    private int currentInstability() {
        if (creatureRecipe != null) {
            return InfusionRecipes.creatureRecipe(creatureRecipe).map(CreatureInfusionRecipe::instability).orElse(10);
        }
        if (selfRecipe != null) {
            return InfusionRecipes.selfRecipe(selfRecipe).map(SelfInfusionRecipe::instability).orElse(10);
        }
        return 0;
    }

    /**
     * One cycle of the running infusion. Instability settles first and can throw an event that costs the cycle;
     * otherwise the four steps run in order — aura, essentia, items, vis — and finishing them all completes it.
     */
    private void infusionCycle(ServerLevel level) {
        if (creatureRecipe == null && selfRecipe == null) {
            finishInfusion(false);
            return;
        }
        stability -= level.random.nextFloat() * (currentInstability() / stabilityBand().erosionDivisor);
        stability = Math.min(stability + stabilityReplenish, MAX_INSTABILITY);
        if (stability < 0.0F && level.random.nextInt(1500) < Math.abs(stability)) {
            instabilityEvent(level);
            stability = Math.min(stability + 5.0F + level.random.nextFloat() * 5.0F, MAX_INSTABILITY);
            sync();
            return;
        }
        if (selfRecipe == null && contained == null) {
            finishInfusion(false);
            return;
        }
        ClientFxPacket.rune(level, Vec3.atCenterOf(worldPosition).add(0.0, -2.5, 0.0), 25);
        if (count % 65 == 0) {
            level.playSound(null, worldPosition, SoundsIS.CURATIVE_INFUSION_LOOP.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        if (celestialStep(level) && essentiaStep(level) && ingredientStep(level) && visStep(level)) {
            finishInfusion(true);
        } else {
            sync();
        }
    }

    /**
     * Gathered aura is spent as a whole: the vat collects one point per cycle until it has what the recipe
     * asked for, then resumes the other steps.
     */
    private boolean celestialStep(ServerLevel level) {
        if (celestialAuraNeeded <= 0) {
            return true;
        }
        if (!celestialBody.canBeDrained(level, worldPosition.above())) {
            setDrawingAura(false);
            return false;
        }
        setDrawingAura(true);
        celestialAura += 1.0F;
        if (celestialAura >= celestialAuraNeeded) {
            celestialAura = 0.0F;
            celestialAuraNeeded = 0;
            setDrawingAura(false);
            return true;
        }
        return false;
    }

    /**
     * Pulls whatever essentia the infusion is still short of; the step passes when nothing is left. The drawing
     * itself happens every tick in {@link #pullInfusionEssentia} — this only publishes the demand so tubes
     * know what the vat will take.
     */
    private boolean essentiaStep(ServerLevel level) {
        clearDemand();
        boolean ready = true;
        for (Map.Entry<AspectId, Integer> entry : needed.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            demand(entry.getKey(), entry.getValue());
            ready = false;
        }
        if (ready) {
            needed.clear();
        }
        return ready;
    }

    /**
     * Takes one unit of each aspect the infusion still needs out of the neighbouring essentia endpoints, every
     * tick, as upstream did. Doing it once per cycle instead left a 50+50 recipe a hundred seconds to feed
     * through a saturated tube.
     */
    private void pullInfusionEssentia(ServerLevel level) {
        for (Map.Entry<AspectId, Integer> entry : needed.entrySet()) {
            if (entry.getValue() > 0) {
                pullEssentia(level, entry.getKey());
            }
        }
    }

    /** Consumes one pedestal item per cycle; passes once every ingredient has been taken. */
    private boolean ingredientStep(ServerLevel level) {
        if (components.isEmpty()) {
            return true;
        }
        ItemStack stack = components.remove(0);
        consumed.add(stack);
        for (BlockPos pos : pedestals(level)) {
            if (level.getBlockEntity(pos) instanceof InfusionPedestal pedestal
                    && !pedestal.item().isEmpty() && ItemStack.isSameItemSameTags(pedestal.item(), stack)) {
                // The ingredient leaves the pedestal for the vat, which is what the client draws flying.
                ClientFxPacket.ingredientFlight(level, Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0),
                        Vec3.atCenterOf(worldPosition).add(0.0, -0.5, 0.0));
                // The pedestal throws off the same debris as a broken block, as the original's packet did.
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos,
                        Block.getId(level.getBlockState(pos)));
                pedestal.clearContent();
                break;
            }
        }
        return components.isEmpty();
    }

    /**
     * Drains the whole-vis cost from the vis network, one channel per cycle. The drain is taken at the matrix
     * above the vat, as upstream's matrix was a network node; Thaumcraft's own network resolves any position
     * to the sources near it, so no node has to stand there — a node within reach is enough, as for every
     * other vis-driven machine.
     */
    private boolean visStep(ServerLevel level) {
        if (visNeeded.isEmpty()) {
            return true;
        }
        VisChannel channel = visNeeded.keySet().iterator().next();
        int whole = visNeeded.get(channel);
        int got = VisNetworkApi.drain(level, worldPosition.above(), channel, whole * 100, VisAction.EXECUTE) / 100;
        if (got <= 0) {
            return false;
        }
        if (got >= whole) {
            visNeeded.remove(channel);
        } else {
            visNeeded.put(channel, whole - got);
        }
        return visNeeded.isEmpty();
    }

    private void finishInfusion(boolean success) {
        if (level instanceof ServerLevel server) {
            if (success && creatureRecipe != null && contained != null) {
                Player player = infusionBy == null ? null : server.getPlayerByUUID(infusionBy);
                InfusionRecipes.creatureRecipe(creatureRecipe).ifPresent(recipe -> {
                    LivingEntity result = InfusionRecipes.apply(recipe, contained, player, consumed);
                    if (result != contained) {
                        setContained(result);
                    }
                });
            } else if (success && selfRecipe != null) {
                InfusionRecipes.selfRecipe(selfRecipe).ifPresent(recipe -> selfInfusions.add(recipe.infusion()));
            }
        }
        mode = contained == null && hasEffigy ? MODE_SELF_READY : MODE_IDLE;
        active = false;
        needed.clear();
        stored.clear();
        demanded.clear();
        visNeeded.clear();
        components.clear();
        creatureRecipe = null;
        selfRecipe = null;
        infusionBy = null;
        stability = 0.0F;
        celestialAura = 0.0F;
        celestialAuraNeeded = 0;
        celestialBody = CelestialBody.NONE;
        drawingAura = false;
        cooldown = 0;
        sync();
    }

    /** Gives up on a running infusion, keeping the ingredients that were not consumed yet. */
    public void abortInfusion() {
        if (mode == MODE_INFUSING) {
            finishInfusion(false);
        }
    }

    // ---------------------------------------------------------------- instability

    private void instabilityEvent(ServerLevel level) {
        fluxStocked += level.random.nextInt(10);
        int roll = level.random.nextInt(26);
        if (roll < 4) {
            ejectPedestalItem(level, true);
        } else if (roll < 7) {
            warpHarm(level);
        } else if (roll < 10) {
            zap(level);
        } else if (roll < 12) {
            ejectPedestalItem(level, true);
            harm(level);
        } else if (roll < 15) {
            ejectPedestalItem(level, roll != 13);
            fluxStocked += 5 + level.random.nextInt(5);
        } else if (roll < 18) {
            harm(level);
        } else if (roll < 20) {
            ejectPedestalItem(level, true);
            level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() - 1.5, worldPosition.getZ() + 0.5,
                    1.0F, Level.ExplosionInteraction.NONE);
        } else if (roll < 21) {
            harm(level);
            warpHarm(level);
        } else if (roll < 22) {
            level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() - 1.5, worldPosition.getZ() + 0.5,
                    1.5F + level.random.nextFloat(), Level.ExplosionInteraction.NONE);
        } else if (roll < 24) {
            if (contained != null) {
                contained.hurt(level.damageSources().magic(), level.random.nextInt(3));
            }
        } else {
            zap(level);
        }
    }

    /** Drops or destroys one of the items waiting on a pedestal. */
    private void ejectPedestalItem(ServerLevel level, boolean drop) {
        List<BlockPos> found = pedestals(level);
        for (int attempt = 0; attempt < 20 && !found.isEmpty(); attempt++) {
            BlockPos pos = found.get(level.random.nextInt(found.size()));
            if (!(level.getBlockEntity(pos) instanceof InfusionPedestal pedestal) || pedestal.item().isEmpty()) {
                continue;
            }
            ItemStack stack = pedestal.item();
            if (drop) {
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack.copy()));
            }
            pedestal.clearContent();
            // Upstream's arc leaps up off the robbed pedestal.
            ClientFxPacket.arc(level, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos.above()));
            return;
        }
    }

    private void zap(ServerLevel level) {
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(10.0))) {
            living.hurt(level.damageSources().magic(), 4.0F + level.random.nextInt(4));
            // A bolt runs from the vat up and out to whatever it caught.
            ClientFxPacket.arc(level, Vec3.atCenterOf(worldPosition).add(0.0, 1.0, 0.0), living.position());
        }
    }

    /** Flux taint or vis exhaustion, the two curses a botched infusion hands out. */
    private void harm(ServerLevel level) {
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(10.0))) {
            living.addEffect(new MobEffectInstance(ThaumBridge.fluxTaint(), 120));
            if (level.random.nextBoolean()) {
                living.addEffect(new MobEffectInstance(ThaumBridge.visExhaust(), 2400));
            }
        }
    }

    private void warpHarm(ServerLevel level) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(10.0))) {
            if (level.random.nextInt(4) == 0) {
                ThaumcraftApiHelper.addStickyWarpToPlayer(player, 1);
            } else {
                ThaumcraftApiHelper.addWarpToPlayer(player, 2 + level.random.nextInt(4), true);
            }
        }
    }

    // ---------------------------------------------------------------- essentia

    /**
     * Rebuilds what the vat is asking for. It is recomputed every cycle rather than accumulated: a demand
     * that never shrinks would keep a tube feeding essentia the vat has already got.
     */
    private void demand(AspectId aspect, int amount) {
        if (demanded.getOrDefault(aspect, 0) < amount) {
            demanded.put(aspect, amount);
        }
    }

    /** Whole units of an aspect the vat would take right now: the infusion's shortfall, or a cure's point. */
    private int demandFor(AspectId aspect) {
        if (this.mode == MODE_INFUSING && needed.containsKey(aspect)) {
            return needed.get(aspect);
        }
        return demanded.getOrDefault(aspect, 0);
    }

    private void clearDemand() {
        demanded.clear();
    }

    /** Takes essentia out of what the vat holds for a cure or an effigy. */
    private boolean draw(AspectId aspect, int amount) {
        int have = stored.getOrDefault(aspect, 0);
        if (have < amount) {
            return false;
        }
        if (have == amount) {
            stored.remove(aspect);
        } else {
            stored.put(aspect, have - amount);
        }
        return true;
    }

    /** Pulls one unit of an aspect out of a neighbouring essentia endpoint, if it will give it up. */
    private void pullEssentia(ServerLevel level, AspectId aspect) {
        for (BlockPos port : ports()) {
            for (Direction face : Direction.values()) {
                EssentiaTransport transport = ThaumcraftApiHelper.getConnectableTransport(level, port, face);
                if (transport == null || !transport.isConnectable(face.getOpposite())) {
                    continue;
                }
                int taken = EssentiaApi.take(level, transport, aspect, 1, face.getOpposite(), EssentiaTransferMode.EXECUTE);
                if (taken > 0) {
                    accept(aspect, taken, EssentiaTransferMode.EXECUTE);
                    return;
                }
            }
        }
    }

    /** The master plus its four ports: any block of a formed vat can be connected to a tube. */
    private List<BlockPos> ports() {
        List<BlockPos> result = new ArrayList<>(5);
        result.add(worldPosition);
        for (int dy = -1; dy > -VatStructure.HEIGHT; dy--) {
            result.add(worldPosition.offset(1, dy, 0));
            result.add(worldPosition.offset(-1, dy, 0));
            result.add(worldPosition.offset(0, dy, 1));
            result.add(worldPosition.offset(0, dy, -1));
        }
        return result;
    }

    /**
     * Takes essentia the vat is waiting for. An infusion drinks it straight out of the demand — a vat cannot
     * hold a recipe's worth in its glass — while a cure or an effigy keeps a point in store until its turn.
     * A simulated transfer reports what it would take and changes nothing.
     */
    private int accept(AspectId aspect, int amount, EssentiaTransferMode mode) {
        int wanted = demandFor(aspect);
        int taken = Math.min(amount, Math.max(wanted, 0));
        if (taken <= 0 || !mode.executes()) {
            return taken;
        }
        if (this.mode == MODE_INFUSING && needed.containsKey(aspect)) {
            needed.put(aspect, wanted - taken);
        } else {
            stored.merge(aspect, taken, Integer::sum);
        }
        setChanged();
        return taken;
    }

    @Override
    public boolean isConnectable(Direction face) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        TileVat master = master();
        if (master == null) {
            return null;
        }
        return master.demanded.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    @Override
    public int suctionAmount(Direction face) {
        TileVat master = master();
        return master != null && !master.demanded.isEmpty() ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        TileVat master = master();
        return master == null ? 0 : master.accept(aspect, amount, mode);
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
        return true;
    }

    @Override
    public AspectAmounts visibleAspects() {
        TileVat master = master();
        return new AspectAmounts(master == null ? Map.of() : Map.copyOf(master.stored));
    }

    // ---------------------------------------------------------------- pedestals

    private List<BlockPos> pedestals(ServerLevel level) {
        if (pedestals == null) {
            List<BlockPos> found = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-PEDESTAL_RANGE, -PEDESTAL_RANGE_Y, -PEDESTAL_RANGE),
                    worldPosition.offset(PEDESTAL_RANGE, PEDESTAL_RANGE_Y, PEDESTAL_RANGE))) {
                if (level.getBlockEntity(pos) instanceof InfusionPedestal) {
                    found.add(pos.immutable());
                }
            }
            pedestals = List.copyOf(found);
        }
        return pedestals;
    }

    private List<ItemStack> pedestalItems() {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>();
        for (BlockPos pos : pedestals(server)) {
            if (server.getBlockEntity(pos) instanceof InfusionPedestal pedestal && !pedestal.item().isEmpty()) {
                items.add(pedestal.item().copyWithCount(1));
            }
        }
        return items;
    }

    public void invalidatePedestals() {
        pedestals = null;
    }

    /** Stabilizers calm an infusion, but only in mirrored pairs — the same rule Thaumcraft's altar uses. */
    private void refreshStability(ServerLevel level) {
        stabilityReplenish = 0.0F;
        int radius = 8;
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-radius, -6, -radius), worldPosition.offset(radius, 4, radius))) {
            if (!level.getBlockState(pos).is(ThaumBridge.STABILIZERS)) {
                continue;
            }
            BlockPos mirror = new BlockPos(2 * worldPosition.getX() - pos.getX(), pos.getY(), 2 * worldPosition.getZ() - pos.getZ());
            stabilityReplenish += level.getBlockState(mirror).is(ThaumBridge.STABILIZERS) ? 0.1F : -0.2F;
        }
    }

    // ---------------------------------------------------------------- containment

    private static void setNoAi(@Nullable LivingEntity entity, boolean noAi) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(noAi);
        }
    }

    /**
     * Moves an occupant. A player has to be moved through their connection: a bare position change on the
     * server is undone by the client's next movement packet, which left players unable to get out. A player
     * without a connection — a fake one, from a probe or another mod — has only the bare move.
     */
    private static void place(LivingEntity entity, double x, double y, double z) {
        if (entity instanceof ServerPlayer player && player.connection != null) {
            player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        } else {
            entity.setPos(x, y, z);
        }
    }

    /** Puts a creature in the vat: held without AI and immune until it leaves again. */
    public void setContained(@Nullable LivingEntity entity) {
        setNoAi(contained, false);
        if (contained != null) {
            contained.setInvulnerable(false);
        }
        contained = entity;
        containedId = entity == null ? null : entity.getUUID();
        containedNbt = null;
        if (entity != null) {
            setNoAi(entity, true);
            entity.setInvulnerable(true);
            entity.setDeltaMovement(0.0, 0.0, 0.0);
            place(entity, worldPosition.getX() + 0.5, worldPosition.getY() - 1.6, worldPosition.getZ() + 0.5);
            if (level != null && !entity.isAddedToWorld()) {
                level.addFreshEntity(entity);
            }
            containedNbt = new CompoundTag();
            entity.save(containedNbt);
        }
        pedestals = null;
        normalizeMode();
        clearDemand();
        sync();
    }

    @Nullable
    public LivingEntity getContained() {
        return contained;
    }

    /** Reattaches the creature after a chunk reload, rebuilding it from its saved form if it never came back. */
    private void resolveContained(ServerLevel level) {
        if (contained != null) {
            return;
        }
        if (containedId != null && level.getEntity(containedId) instanceof LivingEntity living) {
            contained = living;
            return;
        }
        if (containedNbt != null) {
            Entity restored = EntityType.create(containedNbt, level).orElse(null);
            if (restored instanceof LivingEntity living) {
                level.addFreshEntity(living);
                contained = living;
            }
        }
    }

    /** Lets the creature out above the vat. */
    public void releaseContained() {
        if (contained == null) {
            return;
        }
        LivingEntity entity = contained;
        setContained(null);
        setNoAi(entity, false);
        entity.setInvulnerable(false);
        place(entity, worldPosition.getX() + 0.5, worldPosition.getY() + 2.0, worldPosition.getZ() + 0.5);
    }

    /** Breaks the vat open: whatever is inside dies, the consumed ingredients drop, the blocks come apart. */
    public void destroyStructure() {
        destroyStructure(null);
    }

    public void destroyStructure(@Nullable BlockPos skip) {
        if (level == null || breaking) {
            return;
        }
        breaking = true;
        if (contained != null) {
            LivingEntity entity = contained;
            setContained(null);
            setNoAi(entity, false);
            entity.hurt(level.damageSources().magic(), Float.MAX_VALUE);
        }
        if (level instanceof ServerLevel server) {
            for (ItemStack stack : consumed) {
                Block.popResource(server, worldPosition.above(), stack);
            }
        }
        consumed.clear();
        components.clear();
        if (level instanceof ServerLevel server) {
            ClientFxPacket.fluxBurst(server, Vec3.atCenterOf(worldPosition).add(0.0, 1.0, 0.0));
        }
        sync();
        VatStructure.restore(level, worldPosition, skip);
    }

    // ---------------------------------------------------------------- accessors

    public int getMode() {
        return mode;
    }

    public void setActive(boolean active) {
        this.active = active;
        sync();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isInfusing() {
        return mode == MODE_INFUSING;
    }

    /** Whether any pedestal around the vat holds something, for the wand tap's refusal message. */
    public boolean hasPedestalItems() {
        return !pedestalItems().isEmpty();
    }

    /** Whether the effigy has taken its victus and the vat is a ready body waiting for a soul. */
    public boolean effigySoaked() {
        return effigyEssentia >= EFFIGY_ESSENTIA;
    }

    /** True while a self infusion runs, which is the state that shows the revived body in the vat. */
    public boolean isSelfInfusing() {
        return mode == MODE_INFUSING && selfRecipe != null;
    }

    public boolean isExpunging() {
        return expunge;
    }

    public float getStability() {
        return stability;
    }

    /** Upstream's four stability bands. The band divides a recipe's instability into the erosion per cycle. */
    public enum StabilityBand {
        VERY_STABLE(5.0F), STABLE(6.0F), UNSTABLE(7.0F), VERY_UNSTABLE(8.0F);

        private final float erosionDivisor;

        StabilityBand(float erosionDivisor) {
            this.erosionDivisor = erosionDivisor;
        }

        public String translationKey() {
            return "tooltip.isorropia.stability." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public StabilityBand stabilityBand() {
        if (stability <= -MAX_INSTABILITY) {
            return StabilityBand.VERY_UNSTABLE;
        }
        if (stability < 0.0F) {
            return StabilityBand.UNSTABLE;
        }
        return stability > MAX_INSTABILITY / 2.0F ? StabilityBand.VERY_STABLE : StabilityBand.STABLE;
    }

    /** The most stability one cycle of the running infusion can cost. */
    public float lossPerCycle() {
        int instability = level != null && level.isClientSide ? syncedInstability : currentInstability();
        return instability / stabilityBand().erosionDivisor;
    }

    /** What the stabilisers around the vat give back each cycle; negative when they are out of symmetry. */
    public float getSymmetryStability() {
        return stabilityReplenish;
    }

    public boolean isDrawingAura() {
        return drawingAura;
    }

    private void setDrawingAura(boolean drawing) {
        if (drawingAura != drawing) {
            drawingAura = drawing;
            sync();
        }
    }

    /** Takes stock of the stabilisers again, as a wand tap on the matrix did upstream. */
    public void surveyStabilizers() {
        if (level instanceof ServerLevel server) {
            refreshStability(server);
            sync();
        }
    }

    /** The matrix is gone: the vat stops and its wind-up is lost at once rather than unwinding. */
    public void shutDown() {
        active = false;
        startUp = 0.0F;
        sync();
    }

    /** How unstable the vat has become, as a positive number; the matrix shakes harder the higher it is. */
    public float getRawStability() {
        return Math.max(0.0F, -stability);
    }

    public float getStartUp() {
        return startUp;
    }

    public int getCraftCount() {
        return craftCount;
    }

    public float getFluxStocked() {
        return fluxStocked;
    }

    public int getCelestialAuraNeeded() {
        return celestialAuraNeeded;
    }

    public float getCelestialAura() {
        return celestialAura;
    }

    public boolean hasEffigy() {
        return hasEffigy;
    }

    public void setHasEffigy(boolean hasEffigy) {
        this.hasEffigy = hasEffigy;
        if (!hasEffigy) {
            effigyEssentia = 0;
        }
        normalizeMode();
        clearDemand();
        sync();
    }

    public int getEffigyEssentia() {
        return effigyEssentia;
    }

    public Set<ResourceLocation> getSelfInfusions() {
        return Set.copyOf(selfInfusions);
    }

    /** Hands this body's abilities to the player reborn in it and empties the vat. */
    public void claimSelfInfusions(Player player) {
        fr.wind_blade.isorropia.common.capabilities.LivingData.get(player)
                .ifPresent(data -> selfInfusions.forEach(data::addSelfInfusion));
        selfInfusions.clear();
        hasEffigy = false;
        effigyEssentia = 0;
        mode = MODE_IDLE;
        sync();
    }

    // ---------------------------------------------------------------- persistence

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
        tag.putInt("Mode", mode);
        tag.putBoolean("Active", active);
        tag.putFloat("Stability", stability);
        tag.putFloat("Flux", fluxStocked);
        tag.putBoolean("Expunge", expunge);
        tag.putFloat("Aura", celestialAura);
        tag.putInt("AuraNeeded", celestialAuraNeeded);
        tag.putString("Body", celestialBody.serializedName());
        tag.putBoolean("Effigy", hasEffigy);
        tag.putInt("EffigyEssentia", effigyEssentia);
        tag.putInt("Cooldown", cooldown);
        tag.putFloat("StartUp", startUp);
        tag.putInt("CraftCount", craftCount);
        tag.put("Stored", writeAspects(stored));
        tag.put("Needed", writeAspects(needed));
        ListTag selfTag = new ListTag();
        selfInfusions.forEach(id -> selfTag.add(StringTag.valueOf(id.toString())));
        tag.put("SelfInfusions", selfTag);
        if (creatureRecipe != null) {
            tag.putString("CreatureRecipe", creatureRecipe.toString());
        }
        if (selfRecipe != null) {
            tag.putString("SelfRecipe", selfRecipe.toString());
        }
        if (infusionBy != null) {
            tag.putUUID("InfusionBy", infusionBy);
        }
        if (containedId != null) {
            tag.putUUID("Contained", containedId);
        }
        if (containedNbt != null) {
            tag.put("ContainedNbt", containedNbt);
        }
        ListTag consumedTag = new ListTag();
        //? if forge {
        consumed.forEach(stack -> consumedTag.add(stack.save(new CompoundTag())));
        //?} else {
        /*consumed.forEach(stack -> consumedTag.add(stack.save(registries)));
        *///?}
        tag.put("Consumed", consumedTag);
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
        mode = tag.getInt("Mode");
        active = tag.getBoolean("Active");
        stability = tag.getFloat("Stability");
        fluxStocked = tag.getFloat("Flux");
        expunge = tag.getBoolean("Expunge");
        celestialAura = tag.getFloat("Aura");
        celestialAuraNeeded = tag.getInt("AuraNeeded");
        celestialBody = CelestialBody.byName(tag.getString("Body"));
        hasEffigy = tag.getBoolean("Effigy");
        effigyEssentia = tag.getInt("EffigyEssentia");
        cooldown = tag.getInt("Cooldown");
        startUp = tag.getFloat("StartUp");
        craftCount = tag.getInt("CraftCount");
        readAspects(tag.getList("Stored", Tag.TAG_COMPOUND), stored);
        readAspects(tag.getList("Needed", Tag.TAG_COMPOUND), needed);
        selfInfusions.clear();
        for (Tag element : tag.getList("SelfInfusions", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id != null) {
                selfInfusions.add(id);
            }
        }
        creatureRecipe = tag.contains("CreatureRecipe") ? ResourceLocation.tryParse(tag.getString("CreatureRecipe")) : null;
        selfRecipe = tag.contains("SelfRecipe") ? ResourceLocation.tryParse(tag.getString("SelfRecipe")) : null;
        infusionBy = tag.hasUUID("InfusionBy") ? tag.getUUID("InfusionBy") : null;
        containedId = tag.hasUUID("Contained") ? tag.getUUID("Contained") : null;
        containedNbt = tag.contains("ContainedNbt") ? tag.getCompound("ContainedNbt") : null;
        consumed.clear();
        for (Tag element : tag.getList("Consumed", Tag.TAG_COMPOUND)) {
            //? if forge {
            ItemStack stack = ItemStack.of((CompoundTag) element);
            //?} else {
            /*ItemStack stack = ItemStack.parseOptional(registries, (CompoundTag) element);
            *///?}
            if (!stack.isEmpty()) {
                consumed.add(stack);
            }
        }
    }

    private static ListTag writeAspects(Map<AspectId, Integer> aspects) {
        ListTag list = new ListTag();
        aspects.forEach((aspect, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", aspect.toString());
            entry.putInt("Amount", amount);
            list.add(entry);
        });
        return list;
    }

    private static void readAspects(ListTag list, Map<AspectId, Integer> into) {
        into.clear();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            try {
                into.put(AspectId.parse(entry.getString("Id")), entry.getInt("Amount"));
            } catch (IllegalArgumentException ignored) {
                // An aspect that no longer exists: skip it rather than losing the whole vat.
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client only needs what the goggles and the matrix tooltip show. */
    //? if forge {
    @Override
    public CompoundTag getUpdateTag() {
    //?} else {
    /*@Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", mode);
        tag.putBoolean("Active", active);
        tag.putFloat("Stability", stability);
        tag.putFloat("Flux", fluxStocked);
        tag.putFloat("Aura", celestialAura);
        tag.putInt("AuraNeeded", celestialAuraNeeded);
        tag.putBoolean("Expunge", expunge);
        tag.putBoolean("Effigy", hasEffigy);
        tag.putInt("EffigyEssentia", effigyEssentia);
        tag.putFloat("StartUp", startUp);
        tag.putInt("CraftCount", craftCount);
        tag.putFloat("Symmetry", stabilityReplenish);
        tag.putInt("Instability", currentInstability());
        tag.putBoolean("DrawingAura", drawingAura);
        return tag;
    }

    /**
     * The chunk packet and block updates both carry the slim update tag, so the client must read it with its
     * own reader: the full {@link #load} expects fields the update tag never contains.
     */
    //? if forge {
    @Override
    public void handleUpdateTag(CompoundTag tag) {
    //?} else {
    /*@Override
        public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        readSync(tag);
    }

    //? if forge {
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
    //?} else {
    /*@Override
        public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            readSync(tag);
        }
    }

    private void readSync(CompoundTag tag) {
        mode = tag.getInt("Mode");
        active = tag.getBoolean("Active");
        stability = tag.getFloat("Stability");
        fluxStocked = tag.getFloat("Flux");
        celestialAura = tag.getFloat("Aura");
        celestialAuraNeeded = tag.getInt("AuraNeeded");
        expunge = tag.getBoolean("Expunge");
        hasEffigy = tag.getBoolean("Effigy");
        effigyEssentia = tag.getInt("EffigyEssentia");
        startUp = tag.getFloat("StartUp");
        craftCount = tag.getInt("CraftCount");
        stabilityReplenish = tag.getFloat("Symmetry");
        syncedInstability = tag.getInt("Instability");
        drawingAura = tag.getBoolean("DrawingAura");
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    public ResourceLocation getCreatureRecipe() {
        return creatureRecipe;
    }
}
