package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.NodeApi;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import dev.tc4port.thaumcraft.block.entity.AuraNodeBlockEntity;
import dev.tc4port.thaumcraft.registry.TCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thaumic Horizons' teleological recombinator. Sits directly above an ordinary aura node. While a redstone signal feeds
 * it, the machine warms up; once it has run for more than fifty ticks it repeatedly reaches into the surrounding volume
 * and, whenever it finds a second node, fuses a primal from each into the compound aspect they form. The compound is
 * added to the node below and the two consumed primals are removed, echoing a primal from the donor node's capacity.
 *
 * <p>TC4R's {@link NodeApi#replaceLoadedStates} is exactly the two-node transaction this needs: the receiver and the
 * donor are edited in one commit, and both edits are refused if either node changed underneath us. As with the node
 * converter, only ordinary nodes are mutable, so the machine simply does nothing while a node is energized.</p>
 */
public class TileRecombinator extends BlockEntity {
    /** Ticks spent running, mirrored from TH (starts at -1, clamps at 50 while winding down). */
    private int count = -1;
    private boolean activated;
    private boolean shouldActivate;
    private boolean fireOnce;

    // Client-only animation clock so the pearl keeps bobbing without a round trip.
    private int animationTicks;

    public TileRecombinator(BlockPos pos, BlockState state) {
        super(TilesIS.RECOMBINATOR.get(), pos, state);
    }

    public boolean isActivated() {
        return activated;
    }

    public int count() {
        return count;
    }

    public int animationTicks() {
        return animationTicks;
    }

    public void setShouldActivate(boolean value) {
        this.shouldActivate = value;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileRecombinator recombinator) {
        if (!recombinator.fireOnce) {
            recombinator.shouldActivate = level.hasNeighborSignal(pos);
            recombinator.fireOnce = true;
        }
        if (recombinator.activated) {
            recombinator.count++;
        } else if (recombinator.count > 0) {
            if (recombinator.count > 50) {
                recombinator.count = 50;
            }
            recombinator.count--;
        }
        if (recombinator.shouldActivate && !recombinator.activated) {
            recombinator.activated = true;
            recombinator.syncToClients();
        } else if (!recombinator.shouldActivate && recombinator.activated) {
            recombinator.activated = false;
            recombinator.syncToClients();
        }
        if (!recombinator.activated || recombinator.count <= 50) {
            return;
        }
        AuraNodeBlockEntity receiver = ordinaryNode(level, pos.below());
        if (receiver == null) {
            return;
        }
        RandomSource random = level.getRandom();
        int dx = random.nextInt(5) - random.nextInt(5);
        int dy = random.nextInt(5) - random.nextInt(5) - 1;
        int dz = random.nextInt(5) - random.nextInt(5);
        if (dx == 0 && dy == -1 && dz == 0) {
            return;
        }
        AuraNodeBlockEntity donor = ordinaryNode(level, pos.offset(dx, dy, dz));
        if (donor == null) {
            return;
        }
        recombinator.merge((ServerLevel) level, receiver, donor);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileRecombinator recombinator) {
        recombinator.animationTicks++;
    }

    @Nullable
    private static AuraNodeBlockEntity ordinaryNode(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof AuraNodeBlockEntity node && node.getBlockState().is(TCBlocks.AURA_NODE.get())
                ? node : null;
    }

    /**
     * TH picked a fusable pair with four passes (primal/primal, primal/any, any/primal, any/any). A compound aspect's
     * two components are always primals, so only the first pass can ever produce a match; the other three collapse
     * onto it and are omitted here.
     */
    private void merge(ServerLevel level, AuraNodeBlockEntity receiver, AuraNodeBlockEntity donor) {
        AuraNodeState receiverState = receiver.nodeState();
        AuraNodeState donorState = donor.nodeState();
        Map<AspectId, Integer> receiverBase = receiverState.baseVis().amounts();
        Map<AspectId, Integer> receiverCurrent = receiverState.currentVis().amounts();
        Map<AspectId, Integer> donorBase = donorState.baseVis().amounts();
        Map<AspectId, Integer> donorCurrent = donorState.currentVis().amounts();
        if (donorCurrent.isEmpty()) {
            return;
        }
        Optional<AspectId> fused = chooseFusion(level.getRandom(), receiverBase.keySet(), donorBase.keySet());
        if (fused.isEmpty()) {
            return;
        }
        AspectDefinition definition = AspectApi.get(fused.get());
        if (definition == null || definition.components().size() != 2) {
            return;
        }
        AspectId first = definition.components().get(0);
        AspectId second = definition.components().get(1);
        // The component drawn from the receiver is whichever one it actually holds; the other comes from the donor.
        AspectId fromReceiver = receiverBase.getOrDefault(first, 0) > 0 ? first : second;
        AspectId fromDonor = fromReceiver.equals(first) ? second : first;

        Map<AspectId, Integer> newReceiverBase = new LinkedHashMap<>(receiverBase);
        newReceiverBase.merge(fused.get(), 1, Integer::sum);
        decrement(newReceiverBase, fromReceiver);
        Map<AspectId, Integer> newReceiverCurrent = new LinkedHashMap<>(receiverCurrent);
        newReceiverCurrent.merge(fused.get(), 1, Integer::sum);
        decrement(newReceiverCurrent, fromReceiver);
        Map<AspectId, Integer> newDonorCurrent = new LinkedHashMap<>(donorCurrent);
        decrement(newDonorCurrent, fromDonor);
        Map<AspectId, Integer> newDonorBase = new LinkedHashMap<>(donorBase);
        if (level.getRandom().nextInt(3) == 0) {
            decrement(newDonorBase, fromDonor);
        }

        NodeApi.replaceLoadedStates(level, receiver.getBlockPos(), receiverState,
                receiverState.withVis(new NodeVis(newReceiverBase), new NodeVis(newReceiverCurrent)),
                donor.getBlockPos(), donorState,
                donorState.withVis(new NodeVis(newDonorBase), new NodeVis(newDonorCurrent)), VisAction.EXECUTE);
    }

    /** Distinct compound aspects fusable from a primal held by each node, chosen with the machine's own entropy. */
    private static Optional<AspectId> chooseFusion(RandomSource random, Set<AspectId> receiverBase, Set<AspectId> donorBase) {
        Set<AspectId> fused = new LinkedHashSet<>();
        for (AspectId receiver : receiverBase) {
            if (!primal(receiver)) {
                continue;
            }
            for (AspectId donor : donorBase) {
                if (!primal(donor)) {
                    continue;
                }
                AspectApi.combination(receiver, donor).ifPresent(fused::add);
            }
        }
        if (fused.isEmpty()) {
            return Optional.empty();
        }
        List<AspectId> choices = List.copyOf(fused);
        return Optional.of(choices.get(random.nextInt(choices.size())));
    }

    private static boolean primal(AspectId aspect) {
        AspectDefinition definition = AspectApi.get(aspect);
        return definition != null && definition.primal();
    }

    /** Erodes one point, clamped at zero: TC4R keeps exhausted aspects rather than dropping them. */
    private static void decrement(Map<AspectId, Integer> amounts, AspectId aspect) {
        amounts.computeIfPresent(aspect, (id, amount) -> Math.max(0, amount - 1));
    }

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
        this.shouldActivate = tag.getBoolean("shouldactivate");
        this.count = tag.getInt("count");
    }
}
