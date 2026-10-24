package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import dev.tc4port.thaumcraft.block.entity.AuraNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Thaumic Horizons' vortex attenuator, ported to TC4R. Once every five ticks, while a redstone signal feeds it, it
 * casts a beam up to ten blocks along its facing. The first aura node the beam lands on is switched to a normal node,
 * so a hungry node stops pulling in and eating nearby matter; when the beam is broken or switched off (and no other
 * attenuator still holds that node) the node is returned to the type it had. A planar vortex in the beam instead gains
 * a beam of suppression: six of them pin a rift so it stops eating, which is what TH's pocket-plane hand-off waited on.
 */
public class TileVortexStabilizer extends BlockEntity {
    /** How far the beam reaches, and how often the target is re-acquired. */
    private static final int RANGE = 10;
    private static final int SCAN_INTERVAL = 5;

    private boolean redstoned;
    private boolean hasTarget;
    private boolean fireOnce;
    /** The power state at the last scan, so a flip is acted on at once instead of waiting for the next scan. */
    private boolean lastScanRedstoned;
    private boolean scannedOnce;
    /** The type the targeted node had before we normalised it, to restore on release. */
    @Nullable
    private NodeTypeId previousType;
    /** The node or vortex the beam is holding, or null. */
    @Nullable
    private BlockEntity target;
    /** Where the beam currently lands, kept in sync for the renderer (client) and the other-stabilizer scan. */
    @Nullable
    private BlockPos targetPos;

    public TileVortexStabilizer(BlockPos pos, BlockState state) {
        super(TilesIS.VORTEX_STABILIZER.get(), pos, state);
    }

    public void setRedstoned(boolean value) {
        if (this.redstoned != value) {
            this.redstoned = value;
            syncToClients();
        }
    }

    public boolean isRedstoned() {
        return redstoned;
    }

    public boolean hasBeam() {
        return redstoned && hasTarget;
    }

    @Nullable
    public BlockPos beamEnd() {
        return targetPos;
    }

    /** The beam's target, whichever kind it is. */
    @Nullable
    public BlockEntity target() {
        return target;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileVortexStabilizer stabilizer) {
        if (!stabilizer.fireOnce) {
            stabilizer.redstoned = level.hasNeighborSignal(pos);
            stabilizer.fireOnce = true;
        }
        // Re-scan on the regular cadence, and at once whenever the power state has changed (so switching on or off
        // takes effect on the very next tick rather than waiting for the next scan).
        boolean powerChanged = !stabilizer.scannedOnce || stabilizer.redstoned != stabilizer.lastScanRedstoned;
        if (!powerChanged && level.getGameTime() % SCAN_INTERVAL != 0L) {
            return;
        }
        stabilizer.scannedOnce = true;
        stabilizer.lastScanRedstoned = stabilizer.redstoned;
        Direction facing = state.getValue(fr.wind_blade.isorropia.common.blocks.BlockVortexStabilizer.FACING);
        // Walk the beam outward one block at a time: a node or a vortex is taken as the target even though neither has
        // a collision shape, and any block that does have one stops the beam.
        BlockEntity found = null;
        BlockPos hitPos = null;
        if (stabilizer.redstoned) {
            for (int step = 1; step <= RANGE; step++) {
                BlockPos stepPos = pos.relative(facing, step);
                BlockEntity entity = level.getBlockEntity(stepPos);
                if (entity instanceof AuraNodeBlockEntity || entity instanceof TileVortex) {
                    found = entity;
                    hitPos = stepPos;
                    break;
                }
                if (!level.getBlockState(stepPos).getCollisionShape(level, stepPos).isEmpty()) {
                    hitPos = stepPos;
                    break;
                }
            }
            if (hitPos == null) {
                hitPos = pos.relative(facing, RANGE);
            }
        }
        if (hitPos != null && !hitPos.equals(stabilizer.targetPos)) {
            // The beam moved: release the old target, then take the new one in the same tick.
            if (stabilizer.hasTarget) {
                stabilizer.reHungrifyTarget();
                stabilizer.hasTarget = false;
            }
            if (found != null) {
                stabilizer.hasTarget = true;
                stabilizer.target = found;
                if (found instanceof AuraNodeBlockEntity node) {
                    stabilizer.previousType = node.nodeState().type();
                }
                stabilizer.deHungrifyTarget();
            } else {
                stabilizer.target = null;
            }
            stabilizer.targetPos = hitPos;
            stabilizer.syncToClients();
        } else if (hitPos == null && stabilizer.hasTarget) {
            stabilizer.reHungrifyTarget();
            stabilizer.hasTarget = false;
            stabilizer.syncToClients();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileVortexStabilizer stabilizer) {
        // Nothing to advance; the renderer reads isRedstoned()/beamEnd() straight off the synced fields.
    }

    /** Suppress the target: a node becomes a normal node, a vortex gains a beam of suppression. */
    private void deHungrifyTarget() {
        if (target instanceof AuraNodeBlockEntity node) {
            node.setNodeState(node.nodeState().withType(NodeTypeId.NORMAL));
            node.setChanged();
        } else if (target instanceof TileVortex vortex) {
            vortex.setBeams(vortex.beams() + 1);
        }
    }

    /** Release the target: restore the node's original type unless another attenuator holds it, or drop our beam. */
    public void reHungrifyTarget() {
        if (target instanceof AuraNodeBlockEntity node) {
            if (previousType != null && !isNodeTargetedByOtherStabilizer()) {
                node.setNodeState(node.nodeState().withType(previousType));
                node.setChanged();
            }
        } else if (target instanceof TileVortex vortex) {
            vortex.setBeams(Math.max(0, vortex.beams() - 1));
        }
    }

    /** True if another attenuator is aimed at the same node, so we must not put it back. */
    private boolean isNodeTargetedByOtherStabilizer() {
        if (targetPos == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            for (int distance = 1; distance <= RANGE; distance++) {
                BlockEntity entity = level.getBlockEntity(targetPos.relative(direction.getOpposite(), distance));
                if (entity instanceof TileVortexStabilizer other && other != this && other.hasTarget
                        && targetPos.equals(other.targetPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Pushes the power state and beam landing point to clients so the beam can be drawn in the world. */
    private void syncToClients() {
        setChanged();
        TileSync.toWatchers(this);
    }

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
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    //? if forge {
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
    //?} else {
    /*@Override
        public void onDataPacket(net.minecraft.network.Connection connection,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        if (packet.getTag() != null) {
            //? if forge {
            handleUpdateTag(packet.getTag());
            //?} else {
            /*handleUpdateTag(packet.getTag(), registries);
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
        tag.putBoolean("active", this.redstoned);
        tag.putBoolean("hasTarget", this.hasTarget);
        if (this.targetPos != null) {
            tag.putLong("targetPos", this.targetPos.asLong());
        }
        if (this.previousType != null) {
            tag.putString("prevType", this.previousType.toString());
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
        this.redstoned = tag.getBoolean("active");
        this.hasTarget = tag.getBoolean("hasTarget");
        this.targetPos = tag.contains("targetPos") ? BlockPos.of(tag.getLong("targetPos")) : null;
        this.previousType = tag.contains("prevType") ? NodeTypeId.parse(tag.getString("prevType")) : null;
        // A freshly loaded attenuator re-reads the live redstone state on its first tick, as TH did.
        this.fireOnce = false;
        this.scannedOnce = false;
    }
}
