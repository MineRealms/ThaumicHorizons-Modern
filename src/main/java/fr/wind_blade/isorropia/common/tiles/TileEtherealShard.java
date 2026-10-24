package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.api.item.ThaumcraftItemApi;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import dev.tc4port.thaumcraft.api.node.VisNetworkApi;
import dev.tc4port.thaumcraft.api.research.ResearchApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.wand.VisTransferResult;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandMaterialId;
import dev.tc4port.thaumcraft.api.wand.WandView;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thaumic Horizons' ethereal shard: an artificial aura node core that stores vis but makes none of its own.
 *
 * <p>A player right-clicks the shard with ethereal essence to raise its capacity for that essence's aspect.
 * While it has room, the shard pulls centivis out of the vis network — the one an energized node feeds — and
 * banks it, one whole vis per hundred centivis. A wizard can then hold a wand against the shard to draw that
 * vis back out as though it were an ordinary node, which is what TH's {@code TileSyntheticNode} did.</p>
 *
 * <p>It is deliberately not a network source: TH's tile reported {@code isSource() == false}, so it only ever
 * consumes. It also answers the thaumometer as a node, because TH's shard was one.</p>
 */
public class TileEtherealShard extends BlockEntity implements AuraNodeView {
    /** Centivis the shard asks the network for, per aspect, per tick (TH: 10). */
    public static final int DRAW_PER_TICK = 10;
    /** Centivis that make one whole vis — TH's fractional accumulator divisor. */
    public static final int CENTIVIS_PER_VIS = 100;
    /** Capacity one ethereal essence adds (TH: 4 per essence in the stack). */
    public static final int CAPACITY_PER_ESSENCE = 4;
    /** Aspects a broken shard returns per four capacity (TH: capacity / 4). */
    public static final int CAPACITY_PER_DROPPED_ESSENCE = 4;
    /** Aspect amount carried by each essence a broken shard returns (TH: 2). */
    public static final int DROPPED_ESSENCE_ASPECTS = 2;
    /** Ticks between wand taps (TH: 5). */
    public static final int TAP_INTERVAL = 5;
    /** How far the wizard may aim from the shard and still keep drawing from it. */
    private static final double REACH = 6.0D;

    /** TH's {@code aspectsMax}: how much of each aspect the shard may ever hold. */
    private final Map<AspectId, Integer> capacity = new LinkedHashMap<>();
    /** TH's {@code aspects}: whole vis currently banked. */
    private final Map<AspectId, Integer> stored = new LinkedHashMap<>();
    /** TH's {@code fractionalAspects}: centivis not yet worth a whole vis. */
    private final Map<AspectId, Integer> fractional = new LinkedHashMap<>();

    /** Client-side only: TH turned the crystal one degree per client tick. */
    private float rotation;
    private float previousRotation;
    private int drainColour = 0xFFFFFF;

    @Nullable
    private UUID drainPlayer;
    @Nullable
    private AspectId drainAspect;
    private long drainUntil;

    public TileEtherealShard(BlockPos pos, BlockState state) {
        super(TilesIS.ETHEREAL_SHARD.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileEtherealShard shard) {
        if (level instanceof ServerLevel server) {
            shard.drawFromNetwork(server);
            shard.continueDraining(server);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileEtherealShard shard) {
        shard.previousRotation = shard.rotation;
        shard.rotation += 1.0F;
        if (shard.rotation > 360.0F) {
            shard.rotation -= 360.0F;
            shard.previousRotation -= 360.0F;
        }
        AspectDefinition aspect = shard.drainAspect == null ? null : AspectApi.get(shard.drainAspect);
        int target = aspect == null ? 0xFFFFFF : aspect.color();
        int red = ((target >> 16 & 255) + 4 * (shard.drainColour >> 16 & 255)) / 5;
        int green = ((target >> 8 & 255) + 4 * (shard.drainColour >> 8 & 255)) / 5;
        int blue = ((target & 255) + 4 * (shard.drainColour & 255)) / 5;
        shard.drainColour = red << 16 | green << 8 | blue;
    }

    /** The crystal's angle, interpolated for this frame. */
    public float rotation(float partialTick) {
        return this.previousRotation + (this.rotation - this.previousRotation) * partialTick;
    }

    // -- capacity and storage ----------------------------------------------------------------------------------

    /** Capacity per aspect, for the renderer and for tests. */
    public Map<AspectId, Integer> capacity() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.capacity));
    }

    /** Whole vis per aspect, for the renderer and for tests. */
    public Map<AspectId, Integer> stored() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.stored));
    }

    public int capacity(AspectId aspect) {
        return this.capacity.getOrDefault(aspect, 0);
    }

    public int stored(AspectId aspect) {
        return this.stored.getOrDefault(aspect, 0);
    }

    /**
     * TH's {@code addEssence}: one held stack of ethereal essence raises the capacity for its aspect by four per
     * essence, and is consumed whole. The aspect the essence carries decides which capacity grows, not how much.
     */
    public boolean addEssence(ItemStack essence) {
        if (!essence.is(ThaumcraftContent.item("wisp_essence"))) {
            return false;
        }
        AspectAmounts carried = AspectApi.getItemAspects(essence);
        if (carried.amounts().isEmpty() || essence.isEmpty()) {
            return false;
        }
        AspectId aspect = carried.amounts().keySet().iterator().next();
        int added = CAPACITY_PER_ESSENCE * essence.getCount();
        this.capacity.merge(aspect, added, Integer::sum);
        this.stored.putIfAbsent(aspect, 0);
        this.fractional.putIfAbsent(aspect, 0);
        essence.setCount(0);
        syncToClients();
        return true;
    }

    /**
     * TH's {@code addToContainer}: stores what fits and reports how much did not.
     */
    public int addToStore(AspectId aspect, int amount) {
        int room = Math.max(0, capacity(aspect) - stored(aspect));
        int accepted = Math.min(amount, room);
        if (accepted > 0) {
            this.stored.merge(aspect, accepted, Integer::sum);
            syncToClients();
        }
        return amount - accepted;
    }

    /**
     * TH's {@code addFractionalToContainer}: centivis accumulate until they are worth a whole vis. As in TH, the
     * accumulator is spent even if the store filled up mid-way, and nothing is banked for an aspect already full.
     */
    private void addFractional(AspectId aspect, int centivis) {
        if (stored(aspect) >= capacity(aspect)) {
            return;
        }
        int total = this.fractional.getOrDefault(aspect, 0) + centivis;
        while (total > CENTIVIS_PER_VIS) {
            addToStore(aspect, 1);
            total -= CENTIVIS_PER_VIS;
        }
        this.fractional.put(aspect, total);
        setChanged();
    }

    // -- refilling from the network ----------------------------------------------------------------------------

    /**
     * TH's {@code updateEntity}: for every aspect with room, ask the vis network for centivis and bank them.
     * Primal aspects draw straight from their own channel; a compound aspect only charges if every primal it is
     * built from answered, and it charges by the least of them — which is what TH's minimum-of-primals did.
     */
    private void drawFromNetwork(ServerLevel server) {
        if (this.capacity.isEmpty()) {
            return;
        }
        for (AspectId aspect : List.copyOf(this.capacity.keySet())) {
            if (stored(aspect) >= capacity(aspect)) {
                continue;
            }
            int drawn = draw(server, aspect);
            if (drawn > 0) {
                addFractional(aspect, drawn);
            }
        }
    }

    private int draw(ServerLevel server, AspectId aspect) {
        Optional<VisChannel> channel = VisChannel.fromAspectId(aspect);
        if (channel.isPresent()) {
            return VisNetworkApi.drain(server, this.worldPosition, channel.get(), DRAW_PER_TICK, VisAction.EXECUTE);
        }
        Set<VisChannel> primals = primalsOf(aspect);
        if (primals.isEmpty()) {
            return 0;
        }
        int least = DRAW_PER_TICK;
        for (VisChannel primal : primals) {
            least = Math.min(least, VisNetworkApi.drain(server, this.worldPosition, primal, DRAW_PER_TICK,
                    VisAction.EXECUTE));
        }
        return least;
    }

    /** Every primal channel a compound aspect is ultimately built from. */
    private static Set<VisChannel> primalsOf(AspectId aspect) {
        Set<VisChannel> primals = EnumSet.noneOf(VisChannel.class);
        collectPrimals(aspect, primals);
        return primals;
    }

    private static void collectPrimals(AspectId aspect, Set<VisChannel> primals) {
        AspectDefinition definition = AspectApi.get(aspect);
        if (definition == null) {
            return;
        }
        if (definition.primal()) {
            definition.visChannel().ifPresent(primals::add);
            return;
        }
        for (AspectId component : definition.components()) {
            collectPrimals(component, primals);
        }
    }

    // -- drawing back out with a wand --------------------------------------------------------------------------

    /**
     * TH's {@code onUsingWandTick}: takes {@code tap} whole vis of one random aspect the wizard still has room
     * for, and hands it to the wand as centivis. The node-tapping researches apply exactly as they do on a real
     * node, since TH's shard was one.
     */
    public boolean tapIntoWand(ServerPlayer player, ItemStack wand) {
        WandView view = WandApi.view(wand).orElse(null);
        if (view == null) {
            return false;
        }
        int tap = 1;
        if (ResearchApi.isComplete(player, ResearchKey.parse("NODETAPPER1"))) {
            tap++;
        }
        if (ResearchApi.isComplete(player, ResearchKey.parse("NODETAPPER2"))) {
            tap++;
        }
        boolean preserve = !player.isShiftKeyDown()
                && ResearchApi.isComplete(player, ResearchKey.parse("NODEPRESERVE"))
                && !view.rod().equals(WandMaterialId.WOOD)
                && !view.cap().equals(WandMaterialId.IRON);

        List<VisChannel> eligible = new ArrayList<>();
        for (VisChannel channel : VisChannel.values()) {
            if (stored(channel.aspectId()) > (preserve ? 1 : 0)
                    && view.visCentivis().getOrDefault(channel, 0) < view.capacityCentivis().getOrDefault(channel, 0)) {
                eligible.add(channel);
            }
        }
        if (eligible.isEmpty()) {
            return false;
        }
        VisChannel channel = eligible.get(player.getRandom().nextInt(eligible.size()));
        int available = stored(channel.aspectId());
        int amount = Math.min(tap, available);
        if (preserve && amount == available) {
            amount--;
        }
        if (amount <= 0) {
            return false;
        }
        VisTransferResult result = WandApi.insert(player, wand,
                VisCost.ofCentivis(channel, amount * CENTIVIS_PER_VIS), VisAction.EXECUTE);
        int added = result.transferred(channel);
        if (added <= 0) {
            return false;
        }
        int drained = Math.min(amount, Math.max(1, (added + CENTIVIS_PER_VIS - 1) / CENTIVIS_PER_VIS));
        this.stored.put(channel.aspectId(), Math.max(0, available - drained));
        this.drainPlayer = player.getUUID();
        this.drainAspect = channel.aspectId();
        // A successful pull is refreshed every five use ticks; the short grace window masks packet jitter.
        this.drainUntil = player.level().getGameTime() + 7L;
        syncToClients();
        return true;
    }

    /** Starts a held draw. The block has already put the wizard into a use state. */
    public void beginDraining(ServerPlayer player, ItemStack wand) {
        // An empty shard can gain vis while the wand is held; keep trying even if the first tap fails.
        this.drainPlayer = player.getUUID();
        // TH only transfers on held-use ticks, not in the right-click that establishes the use state.
    }

    /** The wizard is still holding a wand against this block, or the draw lapses. */
    @Nullable
    public ServerPlayer resolveDrainer(ServerLevel server) {
        if (this.drainPlayer == null) {
            return null;
        }
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(this.drainPlayer);
        if (player == null || player.level() != server || !player.isUsingItem()
                || WandApi.view(player.getUseItem()).isEmpty()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        HitResult hit = server.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        return hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit
                && blockHit.getBlockPos().equals(this.worldPosition) ? player : null;
    }

    /** Called from the block's ticker: keeps tapping while the wizard holds the wand against the shard. */
    public void continueDraining(ServerLevel server) {
        ServerPlayer player = resolveDrainer(server);
        if (player == null) {
            if (this.drainPlayer != null) {
                this.drainPlayer = null;
                this.drainAspect = null;
                syncToClients();
            }
            return;
        }
        if (server.getGameTime() % TAP_INTERVAL == 0) {
            tapIntoWand(player, player.getUseItem());
        }
    }

    // -- what a broken shard gives back ------------------------------------------------------------------------

    /**
     * TH's {@code breakBlock}: every bound aspect comes back as ethereal essence, four capacity per essence, so a
     * shard returns exactly what was fed into it.
     */
    public List<ItemStack> essenceDrops() {
        List<ItemStack> drops = new ArrayList<>();
        for (Map.Entry<AspectId, Integer> entry : this.capacity.entrySet()) {
            int count = entry.getValue() / CAPACITY_PER_DROPPED_ESSENCE;
            if (count <= 0) {
                continue;
            }
            while (count > 0) {
                ItemStack essence = ThaumcraftItemApi.createWispEssence(
                        AspectAmounts.of(entry.getKey(), DROPPED_ESSENCE_ASPECTS));
                int size = Math.min(count, essence.getMaxStackSize());
                essence.setCount(size);
                drops.add(essence);
                count -= size;
            }
        }
        return drops;
    }

    // -- reading it as a node ----------------------------------------------------------------------------------

    /**
     * TH's shard was a real {@code INode}, so the thaumometer reads it as one. Its identity falls back to the
     * block position, which is what TC4R uses for fixed owners, and it has no type or modifier of its own.
     */
    @Override
    public AuraNodeState nodeState() {
        // Exhausted aspects must remain visible to the thaumometer and the node monitor.
        Map<AspectId, Integer> current = new LinkedHashMap<>();
        this.capacity.forEach((aspect, amount) -> current.put(aspect, stored(aspect)));
        return new AuraNodeState(Optional.empty(), NodeTypeId.NORMAL, NodeModifierId.NONE,
                new NodeVis(this.capacity), new NodeVis(current));
    }

    // -- the drawing beam --------------------------------------------------------------------------------------

    public int drainColour() {
        return this.drainColour;
    }

    //? if forge {
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(this.worldPosition).inflate(REACH);
    }
    //?}

    public boolean draining(Level level) {
        return this.drainPlayer != null && this.drainAspect != null && level.getGameTime() <= this.drainUntil;
    }

    @Nullable
    public UUID drainPlayer() {
        return this.drainPlayer;
    }

    @Nullable
    public AspectId drainAspect() {
        return this.drainAspect;
    }

    // -- persistence and sync ----------------------------------------------------------------------------------

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
        if (this.drainPlayer != null) {
            tag.putUUID("drainPlayer", this.drainPlayer);
        }
        if (this.drainAspect != null) {
            tag.putString("drainAspect", this.drainAspect.serialized());
        }
        tag.putLong("drainUntil", this.drainUntil);
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
        tag.put("capacity", writeAspects(this.capacity));
        tag.put("stored", writeAspects(this.stored));
        tag.put("fractional", writeAspects(this.fractional));
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
        readAspects(tag.getList("capacity", Tag.TAG_COMPOUND), this.capacity);
        readAspects(tag.getList("stored", Tag.TAG_COMPOUND), this.stored);
        readAspects(tag.getList("fractional", Tag.TAG_COMPOUND), this.fractional);
        this.drainPlayer = tag.hasUUID("drainPlayer") ? tag.getUUID("drainPlayer") : null;
        this.drainAspect = tag.contains("drainAspect") ? AspectId.parse(tag.getString("drainAspect")) : null;
        this.drainUntil = tag.getLong("drainUntil");
    }

    private static ListTag writeAspects(Map<AspectId, Integer> amounts) {
        ListTag list = new ListTag();
        amounts.forEach((aspect, amount) -> {
            if (amount > 0) {
                CompoundTag entry = new CompoundTag();
                entry.putString("key", aspect.serialized());
                entry.putInt("amount", amount);
                list.add(entry);
            }
        });
        return list;
    }

    private static void readAspects(ListTag list, Map<AspectId, Integer> into) {
        into.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("key")) {
                into.put(AspectId.parse(entry.getString("key")), entry.getInt("amount"));
            }
        }
    }
}
