package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.api.node.VisNetworkApi;
import dev.tc4port.thaumcraft.api.node.VisSource;
import dev.tc4port.thaumcraft.api.wand.VisTransferResult;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * TH's vis dynamo: a wand-fed centivis reservoir that feeds the energized-node network.
 *
 * <p>A wizard holds a wand against the dynamo. Every five ticks the dynamo takes 40 centivis of each
 * enabled primal out of the wand and offers 5 centivis of it back to the network for ten ticks, which
 * is the same trade TH's {@code TileVisDynamo} made. It only becomes a network source while it has
 * something to offer and while one of the six neighbours is a vis relay, exactly as TH's tile had to
 * sit on the vis net.</p>
 */
public class TileVisDynamo extends BlockEntity implements VisSource, DynamoVisual {
    /** Centivis the wand pays per enabled primal, per pulse (TH: 40). */
    public static final int WAND_COST = 40;
    /** Centivis offered back per enabled primal, per pulse (TH: 5). */
    public static final int OFFERED = 5;
    /** Ticks between pulses while the wand is held (TH: 5). */
    public static final int PULSE_INTERVAL = 5;
    /** Ticks one pulse keeps the dynamo supplying (TH: 10). */
    public static final int SUPPLY_TICKS = 10;
    /** How far the wizard may aim from the dynamo and still keep charging it. */
    private static final double REACH = 6.0D;

    private final EnumMap<VisChannel, Boolean> enabled = new EnumMap<>(VisChannel.class);
    /** The offer one pulse left behind, refilled into {@link #provided} every supplying tick. */
    private final EnumMap<VisChannel, Integer> offering = new EnumMap<>(VisChannel.class);
    /** Centivis the network may still take this tick. */
    private final EnumMap<VisChannel, Integer> provided = new EnumMap<>(VisChannel.class);

    private int ticksProvided;
    private int pulseCooldown;
    @Nullable
    private UUID chargingPlayer;
    /** Endpoint we last handed to the network, so a moved relay is re-registered. */
    @Nullable
    private BlockPos registeredEndpoint;

    // What the client is told: whether the dynamo is supplying, and which primals it offers (the lightning's colour).
    private boolean shownSupplying;
    private int shownChannels;
    private final DynamoMotion motion = new DynamoMotion();
    private int colour = 0xFFFFFF;

    public TileVisDynamo(BlockPos pos, BlockState state) {
        super(TilesIS.VIS_DYNAMO.get(), pos, state);
        for (VisChannel channel : VisChannel.values()) {
            this.enabled.put(channel, Boolean.TRUE);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileVisDynamo dynamo) {
        if (level instanceof ServerLevel server) {
            dynamo.tick(server);
            dynamo.showChanges();
        }
    }

    /** The rings rise and turn, and the lightning drifts to the colour of what is offered, as TH animated them. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, TileVisDynamo dynamo) {
        dynamo.motion.tick(dynamo.shownSupplying);
        if (dynamo.shownSupplying) {
            int target = averageColour(dynamo.shownChannels);
            dynamo.colour = ease(dynamo.colour, target);
        }
    }

    /** Tells the client when the dynamo starts or stops supplying, or offers a different set of primals. */
    private void showChanges() {
        boolean supplying = ticksProvided > 0;
        int channels = channelMask(offering);
        if (supplying != shownSupplying || channels != shownChannels) {
            shownSupplying = supplying;
            shownChannels = channels;
            TileSync.toWatchers(this);
        }
    }

    private static int channelMask(Map<VisChannel, Integer> offer) {
        int mask = 0;
        for (VisChannel channel : offer.keySet()) {
            mask |= 1 << channel.index();
        }
        return mask;
    }

    /** TH's lightning colour: the plain average of the offered primals' colours. */
    private static int averageColour(int mask) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (VisChannel channel : VisChannel.values()) {
            if ((mask & 1 << channel.index()) != 0) {
                int rgb = AspectApi.get(channel.aspectId()).color();
                red += rgb >> 16 & 255;
                green += rgb >> 8 & 255;
                blue += rgb & 255;
                count++;
            }
        }
        return count == 0 ? 0xFFFFFF : (red / count) << 16 | (green / count) << 8 | blue / count;
    }

    /** One fifth of the way from the current colour to the target, per channel, as TH eased it each wand tick. */
    private static int ease(int current, int target) {
        int red = ((target >> 16 & 255) + (current >> 16 & 255) * 4) / 5;
        int green = ((target >> 8 & 255) + (current >> 8 & 255) * 4) / 5;
        int blue = ((target & 255) + (current & 255) * 4) / 5;
        return red << 16 | green << 8 | blue;
    }

    @Override
    public DynamoMotion motion() {
        return motion;
    }

    @Override
    public boolean supplying() {
        return shownSupplying;
    }

    @Override
    public int glowColour() {
        return colour;
    }

    //? if forge {
    @Override
    public CompoundTag getUpdateTag() {
    //?} else {
    /*@Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Supplying", ticksProvided > 0);
        tag.putInt("Channels", channelMask(offering));
        return tag;
    }

    //? if forge {
    @Override
    public void handleUpdateTag(CompoundTag tag) {
    //?} else {
    /*@Override
        public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        shownSupplying = tag.getBoolean("Supplying");
        shownChannels = tag.getInt("Channels");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    //? if forge {
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet) {
    //?} else {
    /*@Override
        public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        if (packet.getTag() != null) {
            //? if forge {
            handleUpdateTag(packet.getTag());
            //?} else {
            /*handleUpdateTag(packet.getTag(), registries);
            *///?}
        }
    }

    /** Declares this tile as a network source; call once from common setup. */
    //? if forge {
    // TC4R 20740: the VisNetworkApi.registerProvider registry is gone; sources are discovered
    // through VisCapabilities.SOURCE queried on the block entity itself, so expose it here. NeoForge
    // wires the same capability from Isorropia's RegisterCapabilitiesEvent listener instead.
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        if (cap == dev.tc4port.thaumcraft.api.node.VisCapabilities.SOURCE) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> this).cast();
        }
        return super.getCapability(cap, side);
    }
    //?}

    /**
     * Starts the held charge. The block has already put the wizard into a use state; the tile only has
     * to remember who is holding the wand so its own ticker can keep pulsing.
     */
    public void beginCharging(ServerPlayer player, ItemStack wand) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!pulse(player, wand)) {
            return;
        }
        this.chargingPlayer = player.getUUID();
        this.ticksProvided = SUPPLY_TICKS;
        this.pulseCooldown = PULSE_INTERVAL;
        syncRegistration(server);
        setChanged();
    }

    private void tick(ServerLevel server) {
        if (ticksProvided <= 0) {
            if (chargingPlayer != null) {
                chargingPlayer = null;
                setChanged();
            }
            syncRegistration(server);
            return;
        }
        // TH rebuilt the network's view of the buffer from the last offer every supplying tick.
        provided.clear();
        provided.putAll(offering);
        ticksProvided--;
        ServerPlayer charger = resolveCharger(server);
        if (charger == null) {
            if (chargingPlayer != null) {
                chargingPlayer = null;
                setChanged();
            }
        } else if (--pulseCooldown <= 0) {
            if (pulse(charger, charger.getUseItem())) {
                ticksProvided = SUPPLY_TICKS;
                pulseCooldown = PULSE_INTERVAL;
            } else {
                chargingPlayer = null;
            }
            setChanged();
        }
        if (ticksProvided <= 0) {
            offering.clear();
            provided.clear();
        }
        syncRegistration(server);
    }

    /**
     * One charge pulse: pay the wand for every enabled primal and keep the ones it could fully pay for.
     * A partially paid channel is dropped, matching TH's {@code consumeVis(..., false)} return.
     */
    private boolean pulse(ServerPlayer player, ItemStack wand) {
        // TH replaced the offer outright each pulse, so a channel it could no longer pay for went quiet.
        offering.clear();
        if (WandApi.view(wand).isEmpty()) {
            return false;
        }
        for (VisChannel channel : VisChannel.values()) {
            if (!enabled.getOrDefault(channel, Boolean.TRUE)) {
                continue;
            }
            VisTransferResult result = WandApi.extract(player, wand, VisCost.ofCentivis(channel, WAND_COST),
                    VisAction.EXECUTE);
            if (result.transferred(channel) >= WAND_COST) {
                offering.put(channel, OFFERED);
            }
        }
        return !offering.isEmpty();
    }

    /** The wizard is still holding the wand against this block, or the charge lapses. */
    @Nullable
    private ServerPlayer resolveCharger(ServerLevel server) {
        if (chargingPlayer == null) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(chargingPlayer);
        if (player == null || !player.isUsingItem() || WandApi.view(player.getUseItem()).isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        HitResult hit = server.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit
                && blockHit.getBlockPos().equals(worldPosition) ? player : null;
    }

    /** Keeps the network registration in step with whether we are supplying and where our relay is. */
    private void syncRegistration(ServerLevel server) {
        if (ticksProvided <= 0 && registeredEndpoint == null) {
            return;
        }
        BlockPos endpoint = networkEndpoint();
        boolean shouldRegister = ticksProvided > 0 && endpoint != null;
        if (shouldRegister && !endpoint.equals(registeredEndpoint)) {
            if (registeredEndpoint != null) {
                VisNetworkApi.removeSource(server, worldPosition);
            }
            VisNetworkApi.registerSource(server, worldPosition);
            registeredEndpoint = endpoint;
        } else if (!shouldRegister && registeredEndpoint != null) {
            VisNetworkApi.removeSource(server, worldPosition);
            registeredEndpoint = null;
        }
    }

    @Override
    public boolean canSupplyVis() {
        return ticksProvided > 0 && level != null && !level.isClientSide;
    }

    @Nullable
    @Override
    public BlockPos networkEndpoint() {
        // TC4R 20740 sources are wireless: the endpoint is the machine itself (the aura node does
        // the same) and a vis relay chains to it through relay.parentPos, so the dynamo feeds the
        // network from its own position instead of hanging off the adjacent relay.
        return level == null || level.isClientSide ? null : worldPosition;
    }

    @Override
    public int takeVis(VisChannel channel, int amount, VisAction action, int reservedCentivis) {
        if (amount <= 0 || ticksProvided <= 0) {
            return 0;
        }
        int stored = provided.getOrDefault(channel, 0);
        int available = stored - Math.max(0, reservedCentivis);
        if (available <= 0) {
            return 0;
        }
        int moved = Math.min(available, amount);
        if (action == VisAction.EXECUTE) {
            provided.put(channel, stored - moved);
            setChanged();
        }
        return moved;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server && registeredEndpoint != null) {
            VisNetworkApi.removeSource(server, worldPosition);
        }
        registeredEndpoint = null;
        super.setRemoved();
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
        tag.putInt("ticks", ticksProvided);
        tag.putInt("pulseCooldown", pulseCooldown);
        if (chargingPlayer != null) {
            tag.putUUID("charger", chargingPlayer);
        }
        int[] mask = new int[VisChannel.values().length];
        for (Map.Entry<VisChannel, Boolean> entry : enabled.entrySet()) {
            mask[entry.getKey().index()] = entry.getValue() ? 1 : 0;
        }
        tag.putIntArray("enabled", mask);
        int[] offer = new int[VisChannel.values().length];
        for (Map.Entry<VisChannel, Integer> entry : offering.entrySet()) {
            offer[entry.getKey().index()] = entry.getValue();
        }
        tag.putIntArray("offering", offer);
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
        ticksProvided = tag.getInt("ticks");
        pulseCooldown = tag.getInt("pulseCooldown");
        chargingPlayer = tag.hasUUID("charger") ? tag.getUUID("charger") : null;
        if (tag.contains("enabled")) {
            int[] mask = tag.getIntArray("enabled");
            for (VisChannel channel : VisChannel.values()) {
                if (channel.index() < mask.length) {
                    enabled.put(channel, mask[channel.index()] != 0);
                }
            }
        }
        offering.clear();
        if (tag.contains("offering")) {
            int[] offer = tag.getIntArray("offering");
            for (VisChannel channel : VisChannel.values()) {
                if (channel.index() < offer.length && offer[channel.index()] > 0) {
                    offering.put(channel, offer[channel.index()]);
                }
            }
        }
        // A reloaded dynamo keeps supplying; the endpoint is re-resolved on the next tick.
        registeredEndpoint = null;
    }

    /** Reads the current per-channel offer; used by tests. */
    public int offeredCentivis(VisChannel channel) {
        return offering.getOrDefault(channel, 0);
    }

    public boolean isEnabled(VisChannel channel) {
        return enabled.getOrDefault(channel, Boolean.TRUE);
    }

    /** TH's {@code enchantItem}: one selection-screen button flips one primal. */
    public void toggle(VisChannel channel) {
        enabled.put(channel, !isEnabled(channel));
        setChanged();
    }

    /** The six switches as one bit mask, which the selection screen reads through a data slot. */
    public ContainerData switchData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int mask = 0;
                for (VisChannel channel : VisChannel.values()) {
                    if (isEnabled(channel)) {
                        mask |= 1 << channel.index();
                    }
                }
                return mask;
            }

            @Override
            public void set(int index, int value) {
                for (VisChannel channel : VisChannel.values()) {
                    enabled.put(channel, (value & 1 << channel.index()) != 0);
                }
                setChanged();
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }
}
