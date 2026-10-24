package fr.wind_blade.isorropia.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The surface of a planar gateway. Thaumic Horizons had a tile for this but never registered it, so the whole portal
 * was a plain block that looked its destination up by walking the frame; this port keeps the tile and hands it the
 * destination outright, which is what {@link fr.wind_blade.isorropia.common.world.PocketGateways} writes when it
 * carves the plane-side twin.
 *
 * <p>A tile with a destination is the plane-side portal: stepping into it sends the visitor to the world-side
 * receptacle that opened it. A tile without one is the world-side portal, and the block walks up to its receptacle
 * instead. The tile also drives the swirl's grow-in, which TH's renderer read off the same field.</p>
 */
public class TilePortal extends BlockEntity {
    /** TH's swirl finished opening at thirty ticks. */
    private static final int OPEN_TICKS = 30;
    /** TH replayed the rift's ambience every two hundred and fifty ticks. */
    private static final int AMBIENT_INTERVAL = 250;

    private int planeId = -1;
    private int which;
    @Nullable
    private ResourceKey<Level> destination;
    @Nullable
    private BlockPos arrival;
    private int openCount = -1;
    private int ambient;

    public TilePortal(BlockPos pos, BlockState state) {
        super(TilesIS.PORTAL.get(), pos, state);
    }

    /** Records where this portal leads, as {@code PocketGateways} does when it carves the plane-side twin. */
    public void link(ResourceKey<Level> destination, BlockPos arrival) {
        this.destination = destination;
        this.arrival = arrival.immutable();
        setChanged();
        // The swirl's colour is read off the destination, so the client has to hear about the link at once.
        TileSync.toWatchers(this);
    }

    /** Records which of a plane's four sides this portal stands on, and the plane it belongs to. */
    public void setPlane(int planeId, int which) {
        this.planeId = planeId;
        this.which = which;
        setChanged();
    }

    @Nullable
    public ResourceKey<Level> destination() {
        return this.destination;
    }

    @Nullable
    public BlockPos arrival() {
        return this.arrival;
    }

    public int planeId() {
        return this.planeId;
    }

    public int which() {
        return this.which;
    }

    public int openCount() {
        return this.openCount;
    }

    /** The only server work is the rift's periodic sound; the teleport itself rides the block's collision hook. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TilePortal portal) {
        if (portal.destination == null) {
            return;
        }
        if (++portal.ambient >= AMBIENT_INTERVAL) {
            portal.ambient = 0;
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    dev.tc4port.thaumcraft.registry.TCSounds.EVIL_PORTAL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TilePortal portal) {
        if (portal.openCount < OPEN_TICKS) {
            portal.openCount++;
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
        tag.putInt("plane", this.planeId);
        tag.putInt("which", this.which);
        tag.putInt("ambient", this.ambient);
        if (this.destination != null) {
            tag.putString("destination", this.destination.location().toString());
        }
        if (this.arrival != null) {
            tag.putLong("arrival", this.arrival.asLong());
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
        this.planeId = tag.getInt("plane");
        this.which = tag.getInt("which");
        this.ambient = tag.getInt("ambient");
        ResourceLocation destination = ResourceLocation.tryParse(tag.getString("destination"));
        this.destination = destination == null ? null : ResourceKey.create(Registries.DIMENSION, destination);
        this.arrival = tag.contains("arrival") ? BlockPos.of(tag.getLong("arrival")) : null;
    }

    /** A newly placed portal starts closed, so it has to be sent even before it is ever linked. */
    //? if forge {
    @Override
    public CompoundTag getUpdateTag() {
    //?} else {
    /*@Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = new CompoundTag();
        //? if forge {
        saveAdditional(tag);
        //?} else {
        /*saveAdditional(tag, registries);
        *///?}
        return tag;
    }

    //? if forge {
    @Override
    public void handleUpdateTag(CompoundTag tag) {
    //?} else {
    /*@Override
        public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        load(tag);
        //?} else {
        /*loadAdditional(tag, registries);
        *///?}
    }

    @Nullable
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

    /** The landing spot in the world the visitor came from, as a vector, or null when this is a world-side portal. */
    @Nullable
    public net.minecraft.world.phys.Vec3 landingSpot() {
        if (this.destination == null || this.arrival == null) {
            return null;
        }
        return new net.minecraft.world.phys.Vec3(this.arrival.getX() + 0.5, this.arrival.getY(),
                this.arrival.getZ() + 0.5);
    }
}
