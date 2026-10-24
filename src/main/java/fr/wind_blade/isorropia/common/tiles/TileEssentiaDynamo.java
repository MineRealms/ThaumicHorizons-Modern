package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import dev.tc4port.thaumcraft.api.node.VisNetworkApi;
import dev.tc4port.thaumcraft.api.node.VisSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Thaumic Horizons' essentia dynamo: essentia piped in from below is burnt into centivis for the vis network. Each
 * unit keeps the dynamo supplying for 21 ticks at twelve centivis a tick, shared evenly between the primals the
 * essentia is made of (ignis gives twelve ignis; a three-primal compound gives four of each), which TH's research
 * sums up as one unit a second. While it has twenty ticks or fewer left it pulls hard on the tube below, and when it
 * runs dry it draws the next unit itself.
 *
 * <p>Like the vis dynamo it joins the network through a vis relay on one of its faces.
 */
public class TileEssentiaDynamo extends BlockEntity implements VisSource, EssentiaTransport, DynamoVisual {
    /** Centivis supplied per tick, shared between the essentia's primals. */
    public static final int CENTIVIS_PER_TICK = 12;
    /** Ticks one unit of essentia keeps the dynamo supplying. */
    public static final int TICKS_PER_UNIT = 21;
    /** The suction the dynamo shows its tube while it can take another unit. */
    public static final int SUCTION = 128;

    /** Ticks left on the current unit; -1 when the dynamo has run dry, as in TH. */
    private int ticksProvided = -1;
    @Nullable
    private AspectId essentia;
    /** What the network may still take this tick. */
    private final EnumMap<VisChannel, Integer> provided = new EnumMap<>(VisChannel.class);
    @Nullable
    private BlockPos registeredEndpoint;

    // Shown on the client: whether the rings are up, whether it supplies, and in what colour.
    private boolean shownPrimed;
    private boolean shownSupplying;
    @Nullable
    private AspectId shownEssentia;
    private final DynamoMotion motion = new DynamoMotion();

    public TileEssentiaDynamo(BlockPos pos, BlockState state) {
        super(TilesIS.ESSENTIA_DYNAMO.get(), pos, state);
    }

    /** Declares this tile as a network source; call once from common setup. */
    // TC4R 20740: VisNetworkApi.registerProvider is gone; sources are discovered through
    // VisCapabilities.SOURCE queried on the block entity, and essentia machines resolve peers through
    // EssentiaCapabilities.TRANSPORT the same way (EssentiaApi.transport no longer falls back to an
    // instanceof check on a Level). Expose both; NeoForge wires the vis cap from Isorropia's
    // RegisterCapabilitiesEvent listener and gets the transport cap from TC4R's blanket registration.
    //? if forge {
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        if (cap == dev.tc4port.thaumcraft.api.node.VisCapabilities.SOURCE) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> this).cast();
        }
        if (cap == dev.tc4port.thaumcraft.api.essentia.EssentiaCapabilities.TRANSPORT) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> (EssentiaTransport) this).cast();
        }
        return super.getCapability(cap, side);
    }
    //?}

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEssentiaDynamo dynamo) {
        if (level instanceof ServerLevel server) {
            dynamo.tick(server);
            dynamo.showChanges();
        }
    }

    /** The rings rise while there is essentia to burn; the renderer draws the essentia climbing in. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, TileEssentiaDynamo dynamo) {
        dynamo.motion.tick(dynamo.shownPrimed);
    }

    /** TH's updateEntity: supply while a unit lasts, draw the next when it runs out, wait for one when dry. */
    private void tick(ServerLevel server) {
        if (ticksProvided > 0) {
            provided.clear();
            if (essentia != null) {
                Set<VisChannel> primals = primalsOf(essentia);
                int each = primals.isEmpty() ? 0 : CENTIVIS_PER_TICK / primals.size();
                primals.forEach(channel -> provided.put(channel, each));
            }
            ticksProvided--;
            setChanged();
        } else if (ticksProvided == 0) {
            if (!drawEssentia(server)) {
                ticksProvided = -1;
                provided.clear();
                setChanged();
            }
        } else {
            drawEssentia(server);
        }
        syncRegistration(server);
    }

    /**
     * Pulls one unit from the tube below, as TH's drawEssentia: the tube must offer upward, hold less suction than
     * the dynamo, and the dynamo's suction must meet the tube's minimum.
     */
    private boolean drawEssentia(ServerLevel server) {
        EssentiaTransport below = ThaumcraftApiHelper.getConnectableTransport(server, worldPosition, Direction.DOWN);
        if (below == null || !below.canOutputTo(Direction.UP)) {
            return false;
        }
        int suction = suctionAmount(Direction.DOWN);
        AspectId offered = below.essentiaType(Direction.UP);
        if (offered == null || below.suctionAmount(Direction.UP) >= suction || suction < below.minimumSuction()) {
            return false;
        }
        if (EssentiaApi.take(server, below, offered, 1, Direction.UP, EssentiaTransferMode.EXECUTE) <= 0) {
            return false;
        }
        burn(offered);
        return true;
    }

    /** TH's addEssentia: twenty-one more ticks of supply from the unit just taken. */
    private void burn(AspectId aspect) {
        essentia = aspect;
        ticksProvided += TICKS_PER_UNIT;
        setChanged();
    }

    /** The primals an aspect is made of, followed down through its components as TH's reduceToPrimals did. */
    public static Set<VisChannel> primalsOf(AspectId aspect) {
        EnumSet<VisChannel> primals = EnumSet.noneOf(VisChannel.class);
        collectPrimals(aspect, primals, 0);
        return primals;
    }

    private static void collectPrimals(AspectId aspect, Set<VisChannel> primals, int depth) {
        AspectDefinition definition = AspectApi.contains(aspect) ? AspectApi.get(aspect) : null;
        if (definition == null || depth > 16) {
            return;
        }
        if (definition.visChannel().isPresent()) {
            primals.add(definition.visChannel().get());
            return;
        }
        for (AspectId component : definition.components()) {
            collectPrimals(component, primals, depth + 1);
        }
    }

    private void showChanges() {
        boolean primed = ticksProvided >= 0;
        boolean supplying = ticksProvided > 0;
        if (primed != shownPrimed || supplying != shownSupplying || !java.util.Objects.equals(essentia, shownEssentia)) {
            shownPrimed = primed;
            shownSupplying = supplying;
            shownEssentia = essentia;
            TileSync.toWatchers(this);
        }
    }

    /**
     * Stays a network source from the first unit until the dynamo runs dry, as TH only removed it in killMe: leaving
     * and rejoining in the one tick between two units made the relay drop and re-adopt it every 22 ticks.
     */
    private void syncRegistration(ServerLevel server) {
        if (ticksProvided < 0 && registeredEndpoint == null) {
            return;
        }
        BlockPos endpoint = networkEndpoint();
        boolean shouldRegister = ticksProvided >= 0 && endpoint != null;
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

    // ---------------------------------------------------------------- vis network

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
        if (amount <= 0 || ticksProvided < 0) {
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

    // ---------------------------------------------------------------- essentia: one face, in only

    @Override
    public boolean isConnectable(Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return null;
    }

    /** Hungry while twenty ticks or fewer remain, so a tube keeps it fed without a gap; TH's 128. */
    @Override
    public int suctionAmount(Direction face) {
        return face == Direction.DOWN && ticksProvided <= TICKS_PER_UNIT - 1 ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (amount <= 0 || face != Direction.DOWN || suctionAmount(face) <= 0) {
            return 0;
        }
        if (mode.executes()) {
            burn(aspect);
        }
        return 1;
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return essentia;
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

    // ---------------------------------------------------------------- client view

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
        return shownEssentia == null ? 0xFFFFFF : AspectApi.get(shownEssentia).color();
    }

    //? if forge {
    @Override
    public CompoundTag getUpdateTag() {
    //?} else {
    /*@Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Primed", ticksProvided >= 0);
        tag.putBoolean("Supplying", ticksProvided > 0);
        if (essentia != null) {
            tag.putString("Essentia", essentia.serialized());
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
        shownPrimed = tag.getBoolean("Primed");
        shownSupplying = tag.getBoolean("Supplying");
        shownEssentia = tag.contains("Essentia") ? AspectId.parse(tag.getString("Essentia")) : null;
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
            handleUpdateTag(packet.getTag());
            //?} else {
            /*handleUpdateTag(packet.getTag(), registries);
            *///?}
        }
    }

    // ---------------------------------------------------------------- lifecycle

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
        tag.putInt("Ticks", ticksProvided);
        if (essentia != null) {
            tag.putString("Essentia", essentia.serialized());
        }
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
        ticksProvided = tag.contains("Ticks") ? tag.getInt("Ticks") : -1;
        essentia = tag.contains("Essentia") ? AspectId.parse(tag.getString("Essentia")) : null;
        registeredEndpoint = null;
    }

    /** The essentia the client sees climbing into the dynamo, or null when it is not supplying. */
    @Nullable
    public AspectId trailEssentia() {
        return shownSupplying ? shownEssentia : null;
    }

    /** The game tick the client last drew the climbing essentia for; the renderer draws it once a tick. */
    public long lastTrailTick = -1L;

    /** The centivis per tick this dynamo offers on a channel right now; for probes. */
    public int providedCentivis(VisChannel channel) {
        return provided.getOrDefault(channel, 0);
    }

    public int ticksProvided() {
        return ticksProvided;
    }
}
