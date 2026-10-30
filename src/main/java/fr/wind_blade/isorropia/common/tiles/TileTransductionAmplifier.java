package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.flux.FluxApi;
import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import dev.tc4port.thaumcraft.api.node.NodeApi;
import fr.wind_blade.isorropia.common.blocks.BlockTransductionAmplifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Redstone-controlled amplifier; the public energized-output event supplies its live +10 contribution. */
public class TileTransductionAmplifier extends BlockEntity {
    /** The four amplifiers counted around a node, as TH did: north/south/east/west of it. */
    private static final List<Direction> HORIZONTAL = Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    /** Ticks the amplifier has been running (clamped from above by TH's own logic). Starts at -1, i.e. before first run. */
    private int count = -1;
    /** The side the node sits on; read from block state on the first tick and on (re)load. */
    @Nullable
    private Direction facing;
    private boolean activated;
    private boolean shouldActivate;
    private boolean fireOnce;

    // Client-only: a derived status for the renderer (0 idle, 1 amplifying, 2 overcharged) and the animation clock.
    private int status;
    private int animationTicks;

    public TileTransductionAmplifier(BlockPos pos, BlockState state) {
        super(TilesIS.TRANSDUCTION_AMPLIFIER.get(), pos, state);
    }

    /** The energized node this amplifier points at, or null if none. */
    @Nullable
    public AuraNodeView nodeFor(Level level, BlockPos pos) {
        if (facing == null) {
            return null;
        }
        BlockPos nodePos = pos.relative(facing);
        return level.hasChunkAt(nodePos)
                && level.getBlockState(nodePos).is(ThaumcraftContent.block("energized_aura_node"))
                ? NodeApi.view(level, nodePos) : null;
    }

    public boolean isActivated() {
        return activated;
    }

    public int count() {
        return count;
    }

    public int status() {
        return status;
    }

    public int animationTicks() {
        return animationTicks;
    }

    public void setShouldActivate(boolean value) {
        this.shouldActivate = value;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileTransductionAmplifier amplifier) {
        if (!amplifier.fireOnce) {
            amplifier.facing = state.getValue(BlockTransductionAmplifier.FACING);
            amplifier.shouldActivate = level.hasNeighborSignal(pos);
            amplifier.fireOnce = true;
        }
        AuraNodeView node = amplifier.nodeFor(level, pos);
        if (node == null) {
            // TH killMe: the node is gone, so the amplifier falls away too. Only once the node's own chunk is loaded,
            // though: the node sits across a possible chunk border, and an unloaded neighbour must not cost us the
            // machine.
            if (amplifier.facing == null || level.hasChunkAt(pos.relative(amplifier.facing))) {
                level.destroyBlock(pos, true);
            }
            return;
        }
        if (amplifier.activated) {
            amplifier.count++;
        } else if (amplifier.count > 0) {
            if (amplifier.count > 50) {
                amplifier.count = 50;
            }
            amplifier.count--;
        }
        if (amplifier.shouldActivate && !amplifier.activated) {
            amplifier.activated = true;
            amplifier.syncToClients();
        } else if (!amplifier.shouldActivate && amplifier.activated) {
            amplifier.activated = false;
            amplifier.syncToClients();
        }
        if (!amplifier.activated || amplifier.count % 10 != 0) {
            return;
        }
        // Multi-amplifier effects, every ten ticks while running, as TH's updateEntity did.
        BlockPos nodePos = pos.relative(amplifier.facing);
        int transducers = 0;
        for (Direction horizontal : HORIZONTAL) {
            BlockEntity neighbour = level.getBlockEntity(nodePos.relative(horizontal));
            if (neighbour instanceof TileTransductionAmplifier other && other.activated) {
                transducers++;
            }
        }
        ServerLevel server = (ServerLevel) level;
        if (transducers > 3 && amplifier.count % 50 == 0) {
            // Four amplifiers tear the node open: TH swapped the node for a vortex carrying the node's base aspects.
            Map<AspectId, Integer> collapsed = node.energizedOutput()
                    .map(output -> output.baseOutput().amounts()).orElse(Map.of());
            level.setBlock(nodePos, fr.wind_blade.isorropia.common.blocks.BlocksIS.VORTEX.get().defaultBlockState(), 3);
            if (level.getBlockEntity(nodePos) instanceof TileVortex vortex) {
                vortex.setAspects(collapsed);
            }
        }
        if (transducers > 2 && level.getRandom().nextInt(4) == 2 && amplifier.count % 50 == 0) {
            int dx = level.random.nextInt(16) - 8;
            int dy = level.random.nextInt(16) - 8;
            int dz = level.random.nextInt(16) - 8;
            BlockPos target = pos.offset(dx, dy, dz);
            if (level.isEmptyBlock(target)) {
                var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", dy > 0 ? "flux_gas" : "flux_goo"));
                FluxApi.place(server, target, fluid, 9, 3);
            }
        }
        if (transducers > 1 && amplifier.count % 50 == 0) {
            AABB area = new AABB(pos).inflate(10.0);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
                target.hurt(level.damageSources().magic(), 4.0F);
                // The visual arc is spawned on the client in clientTick so it stays purely cosmetic.
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileTransductionAmplifier amplifier) {
        if (amplifier.facing == null) {
            amplifier.facing = state.getValue(BlockTransductionAmplifier.FACING);
        }
        amplifier.animationTicks++;
        if (amplifier.activated) {
            // Advance our own copy of the ramp so the pistons keep extending between syncs (the server end of the
            // count only reaches the client when the amplifier is switched on or off).
            if (amplifier.count < 50) {
                amplifier.count++;
            }
            // Count how many neighbouring amplifiers around our node are also running; TH's renderer keyed the
            // overcharge colour off this.
            int transducers = 0;
            BlockPos nodePos = pos.relative(amplifier.facing);
            for (Direction horizontal : HORIZONTAL) {
                BlockEntity neighbour = level.getBlockEntity(nodePos.relative(horizontal));
                if (neighbour instanceof TileTransductionAmplifier other && other.activated) {
                    transducers++;
                }
            }
            amplifier.status = transducers > 1 ? 2 : 1;
            if (amplifier.status == 2 && amplifier.animationTicks % 50 == 0) {
                spawnArc(level, pos);
            }
        } else {
            amplifier.status = 0;
        }
    }

    /** Cosmetic lightning toward nearby creatures while overcharged, matching TH's PacketFXBlockZap. */
    private static void spawnArc(Level level, BlockPos pos) {
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel client)) {
            return;
        }
        dev.tc4port.thaumcraft.api.client.ThaumcraftClientEffectsApi.spawnNodeLightning(
                client, Vec3atCenter(pos), Vec3atCenter(pos.offset(level.getRandom().nextInt(5) - 2, 1, level.getRandom().nextInt(5) - 2)));
    }

    private static net.minecraft.world.phys.Vec3 Vec3atCenter(BlockPos pos) {
        return net.minecraft.world.phys.Vec3.atCenterOf(pos);
    }

    /** Pushes the on/off and ramp state to clients so the renderer can light up and extend the pistons. */
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
        tag.putBoolean("active", this.activated);
        tag.putByte("dir", (byte) (facing != null ? facing.get3DDataValue() : -1));
        tag.putBoolean("shouldactivate", this.shouldActivate);
        tag.putInt("count", this.count);
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
        this.activated = tag.getBoolean("active");
        byte dir = tag.getByte("dir");
        this.facing = dir < 0 ? null : Direction.from3DDataValue(dir);
        this.shouldActivate = tag.getBoolean("shouldactivate");
        this.count = tag.getInt("count");
    }
}
