package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.entity.WispApi;
import dev.tc4port.thaumcraft.registry.TCItems;
import dev.tc4port.thaumcraft.registry.TCSounds;
import fr.wind_blade.isorropia.common.entities.EntitiesIS;
import fr.wind_blade.isorropia.common.entities.EntityGolemTH;
import fr.wind_blade.isorropia.common.items.ItemsIS;
import fr.wind_blade.isorropia.common.wand.CrystalWandParts;
import fr.wind_blade.isorropia.common.world.PocketPlane;
import fr.wind_blade.isorropia.common.world.PocketPlaneBuilder;
import fr.wind_blade.isorropia.common.world.PocketPlaneStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thaumic Horizons' vortex, ported to TC4R. It warms up for fifty ticks, then swallows the 5x5x5 around itself and
 * turns on the surrounding world: anything alive within fifteen blocks is dragged toward the rift and torn apart at
 * point-blank range, and up to three nearby soft blocks a tick are ground away. An attenuator's beam damps all of this
 * ({@link #beams} up to six); once six beams hold the rift open it stops eating and, as TH had it, a primordial pearl
 * dropped into it seeds a pocket plane ({@link #createdDimension}). The rift then doubles as the doorway into that
 * plane, carrying a traveller standing in it across and back.
 *
 * <p>While damped, the rift also runs TH's void crafting: matter dropped in is transformed and held inside it. A
 * void ingot soaks into void putty, salis mundus bursts into wisps, and an inert crystal wand comes back charged to
 * the brim ({@link #storedItems}). Right-clicking the rift with a wand pulls the oldest stored item back out
 * ({@link #popStored}). TH also forged golem animation powder here: powder tossed in is raised from the void itself
 * as permanent voidling golems ({@link #spawnVoidGolems}).</p>
 */
public class TileVortex extends BlockEntity implements AspectContainerView {
    /** TH's collapse trigger: the rift tears itself apart once it has eaten this long. */
    private static final int MAX_COUNT = 2400;
    /** Past this many beams the rift stops eating entirely. */
    private static final int MAX_BEAMS = 6;
    /** TH's {@code timeUntilPortal} for a rift crossing: longer than a gateway's, as the arrival rift is a doorway too. */
    private static final int VORTEX_PORTAL_COOLDOWN = 200;
    /** TH looped a ~27-second rift hum; replaying it at its own length keeps the ambience continuous. */
    private static final int VORTEX_SOUND_INTERVAL = 540;

    private int count;
    /** How many vortex attenuators are currently holding this rift open. */
    private int beams;
    private boolean ateDevices;
    private boolean collapsing;
    /**
     * TH's cheat rift: it never eats, never grinds and never collapses. A pocket plane's exit rift is one of these, so
     * each island keeps its doorway (and TH's creative-only variant was one too).
     */
    private boolean cheat;
    /**
     * The pocket plane this rift is the doorway of, or -1 for a rift in the world. TH's exit rift carried the plane's
     * number so a keystone could be imprinted on it; a rift born of a collapsing node carries none.
     */
    private int planeId = -1;
    /**
     * True once a pearl has seeded a pocket plane from this rift (TH's {@code createdDimension}), after which the rift
     * is a doorway rather than a predator. A plane's exit rift is born with it set.
     */
    private boolean createdDimension;
    /** The world a traveller is carried back to when leaving the plane. Only read on a plane's exit rift. */
    private ResourceKey<Level> returnDimension = Level.OVERWORLD;
    private final Map<AspectId, Integer> aspects = new LinkedHashMap<>();
    private final List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();
    /** Last beam count pushed to clients, so a same-tick change cannot send two packets. */
    private int lastSyncedBeams = -1;

    public TileVortex(BlockPos pos, BlockState state) {
        super(TilesIS.VORTEX.get(), pos, state);
    }

    public int count() {
        return count;
    }

    public int beams() {
        return beams;
    }

    public boolean collapsing() {
        return collapsing;
    }

    public boolean isCheat() {
        return cheat;
    }

    public void setCheat(boolean value) {
        this.cheat = value;
        syncToClients();
    }

    /** The pocket plane this rift is the doorway of, or -1 when it stands in the world. */
    public int planeId() {
        return planeId;
    }

    public void setPlaneId(int value) {
        this.planeId = value;
        syncToClients();
    }

    /** Whether a pearl has already seeded a plane from this rift; it cannot seed a second. */
    public boolean createdDimension() {
        return createdDimension;
    }

    /** Marks a plane's exit rift as a finished doorway (TH set this when it placed the rift). */
    public void markCreated(ResourceKey<Level> returnDimension) {
        this.createdDimension = true;
        this.returnDimension = returnDimension;
        syncToClients();
    }

    /** Read-only aspect view, ordered as TH's AspectList was. */
    public Map<AspectId, Integer> aspectMap() {
        return java.util.Collections.unmodifiableMap(aspects);
    }

    public List<net.minecraft.world.item.ItemStack> storedItems() {
        return List.copyOf(items);
    }

    public void setBeams(int value) {
        if (this.beams != value) {
            this.beams = value;
            syncToClients();
        }
    }

    /** Fills the rift with a random handful of aspects, as TH's {@code onBlockPlacedBy} did. */
    public void rollAspects(RandomSource random) {
        aspects.clear();
        List<AspectId> primals = List.copyOf(AspectApi.primals());
        List<AspectId> compounds = List.copyOf(AspectApi.registry().compounds());
        int chosen = random.nextInt(4) + 1;
        for (int a = 0; a < chosen; a++) {
            List<AspectId> pool = random.nextInt(3) == 0 && !compounds.isEmpty() ? compounds : primals;
            if (pool.isEmpty()) {
                continue;
            }
            aspects.merge(pool.get(random.nextInt(pool.size())), random.nextInt(30), Integer::sum);
        }
        syncToClients();
    }

    /** Replaces the rift's aspects wholesale, as the amplifier catastrophe does when a node collapses into one. */
    public void setAspects(Map<AspectId, Integer> source) {
        aspects.clear();
        aspects.putAll(source);
        syncToClients();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileVortex vortex) {
        if (vortex.beams != vortex.lastSyncedBeams) {
            vortex.syncToClients();
            vortex.lastSyncedBeams = vortex.beams;
        }
        if (vortex.collapsing) {
            vortex.count++;
            if (vortex.count > 25) {
                level.removeBlock(pos, false);
                collapse(level, pos);
            }
            return;
        }
        if (vortex.count < 50) {
            vortex.count++;
            return;
        }
        if (level.getGameTime() % VORTEX_SOUND_INTERVAL == 0) {
            // TH hummed this for every mature rift, doorway or not; replaying at the file's length loops it.
            level.playSound(null, pos, fr.wind_blade.isorropia.common.sounds.SoundsIS.VORTEX.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        if (!vortex.cheat) {
            if (!vortex.ateDevices) {
                // TH cleared the whole neighbourhood the moment the rift matured.
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx != 0 || dy != 0 || dz != 0) {
                                level.removeBlock(pos.offset(dx, dy, dz), false);
                            }
                        }
                    }
                }
                vortex.ateDevices = true;
            }
            if (vortex.beams < MAX_BEAMS) {
                vortex.pullEntities(level, pos);
                vortex.grindBlocks(level, pos);
            } else if (!vortex.createdDimension) {
                // Six beams fully damp the rift: it stops eating and, as TH had it, a pearl can seed a pocket plane.
                vortex.handlePocketPlaneStuff(level, pos);
            }
            vortex.count += Math.max(0, MAX_BEAMS - vortex.beams);
            if (vortex.count > MAX_COUNT && !level.dimension().equals(PocketPlane.LEVEL)) {
                vortex.collapsing = true;
                vortex.count = 0;
                vortex.syncToClients();
            }
        }
        if (vortex.createdDimension) {
            // A doorway, not a predator: a traveller standing in the rift is carried across.
            vortex.transferTravellers(level, pos);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TileVortex vortex) {
        // TH's handleHungryNode client half: while the rift is undamped it flings the blocks around it inward, exactly
        // as an ordinary hungry node does. TC4R keeps that effect for its aura nodes, so reuse it rather than redraw.
        if (!vortex.cheat && vortex.beams < MAX_BEAMS) {
            dev.tc4port.thaumcraft.client.HungryNodeClientEffect.tick(level, pos);
        }
    }

    /** TH's handleHungryNode entity half: drag in everything within fifteen blocks and maul what gets close. */
    private void pullEntities(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(15.0);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area)) {
            if (entity instanceof Player player && player.getAbilities().invulnerable) {
                continue;
            }
            if (entity.isAlive() && !entity.isInvulnerable()
                    && entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 2.0) {
                if (entity instanceof FallingBlockEntity) {
                    entity.discard();
                } else {
                    entity.hurt(level.damageSources().fellOutOfWorld(), 3.0F - this.beams / 2.0F);
                }
            }
            double dx = (pos.getX() + 0.5 - entity.getX()) / 15.0;
            double dy = (pos.getY() + 0.5 - entity.getY()) / 15.0;
            double dz = (pos.getZ() + 0.5 - entity.getZ()) / 15.0;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double falloff = 1.0 - distance;
            double modifier = 2.0 - this.beams / 3.0;
            if (falloff <= 0.0 || distance == 0.0) {
                continue;
            }
            falloff *= falloff;
            entity.push(dx / distance * falloff * 0.15 * modifier,
                    dy / distance * falloff * 0.25 * modifier,
                    dz / distance * falloff * 0.15 * modifier);
        }
    }

    /** TH's handleHungryNode block half: three attempts a tick to chew through a nearby soft block. */
    private void grindBlocks(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < 3; i++) {
            // Damped rifts only get through occasionally.
            if (this.beams > 0 && random.nextInt(Math.max(1, this.beams)) != 0) {
                continue;
            }
            int tx = pos.getX() + random.nextInt(16) - random.nextInt(16);
            int ty = pos.getY() + random.nextInt(16) - random.nextInt(16);
            int tz = pos.getZ() + random.nextInt(16) - random.nextInt(16);
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, tx, tz);
            if (ty > surface) {
                ty = surface;
            }
            Vec3 from = Vec3.atCenterOf(pos);
            Vec3 to = new Vec3(tx + 0.5, ty + 0.5, tz + 0.5);
            //? if neoforge {
            /*// 1.21.1 takes a CollisionContext and dereferences it: a null entity NPEs inside
            // EntityCollisionContext, so the rift crashed the server tick the moment it chewed.
            BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
            *///?} else {
            BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    (net.minecraft.world.entity.Entity) null));
            //?}
            if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().distSqr(pos) >= 256.0) {
                continue;
            }
            BlockPos target = hit.getBlockPos();
            BlockState targetState = level.getBlockState(target);
            float hardness = targetState.getDestroySpeed(level, target);
            if (hardness >= 0.0F && hardness < 10.0F) {
                level.destroyBlock(target, true);
            }
        }
    }

    /**
     * TH's rift-to-pocket-plane hand-off. Once six beams have fully damped the rift it stops eating, and anything
     * dropped into it is transformed: a primordial pearl erupts into a new pocket plane, while any other matter runs
     * TH's void crafting.
     */
    private void handlePocketPlaneStuff(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(1.0))) {
            if (!(entity instanceof ItemEntity item)) {
                continue;
            }
            if (item.getItem().is(TCItems.PRIMORDIAL_PEARL.get())) {
                RandomSource random = level.getRandom();
                level.explode(null, pos.getX() + random.nextFloat(), pos.getY() + random.nextFloat(),
                        pos.getZ() + random.nextFloat(), 1.0F, Level.ExplosionInteraction.NONE);
                createDimension(server, pos, item);
                item.discard();
            } else {
                handleVoidCrafting(server, pos, item);
            }
        }
    }

    /**
     * TH's void crafting. Matter passed through a damped rift is transformed and kept inside it: a void ingot becomes
     * void putty, salis mundus clumps into wisps, and an inert crystal wand comes back charged to the brim. Golem
     * animation powder is not transformed but raised: it comes out as permanent voidling golems owned by the thrower.
     */
    private void handleVoidCrafting(ServerLevel level, BlockPos pos, ItemEntity item) {
        ItemStack stack = item.getItem();
        if (stack.is(TCItems.VOID_INGOT.get())) {
            this.items.add(new ItemStack(ItemsIS.VOID_PUTTY.get(), stack.getCount()));
            item.discard();
        } else if (stack.is(salisMundus())) {
            for (int i = 0; i < stack.getCount(); i++) {
                spawnWisps(level, pos);
            }
            item.discard();
        } else if (stack.is(ItemsIS.CRYSTAL_WAND.get())) {
            this.items.add(CrystalWandParts.chargedWand());
            item.discard();
        } else if (stack.is(ItemsIS.GOLEM_POWDER.get())) {
            this.spawnVoidGolems(level, pos, item);
        }
    }

    /**
     * TH's voidling golems: golem animation powder tossed through the damped rift comes out raised from the void
     * itself, one golem per pinch. A voidling obeys forever and never needs a core, but it counts the thrower as its
     * maker — and coreless constructs of the void do not stay golems forever.
     */
    private void spawnVoidGolems(ServerLevel level, BlockPos pos, ItemEntity item) {
        Entity thrower = item.getOwner();
        for (int i = 0; i < item.getItem().getCount(); i++) {
            EntityGolemTH golem = EntitiesIS.GOLEM_TH.get().create(level);
            if (golem == null) {
                continue;
            }
            golem.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    thrower == null ? 0.0F : thrower.getYRot(), 0.0F);
            golem.loadGolem(null, EntityGolemTH.PERMANENT, false, false,
                    thrower instanceof Player player ? player : null);
            golem.restrictTo(golem.blockPosition(), 32);
            level.addFreshEntity(golem);
        }
        level.playSound(null, pos, TCSounds.WAND.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        item.discard();
    }

    /** TH's {@code spawnWisps}: one to four wisps of a random aspect drawn from the rift. */
    private void spawnWisps(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        AspectId aspect = randomAspect(random);
        if (aspect == null) {
            return;
        }
        int count = random.nextInt(4) + 1;
        Vec3 centre = Vec3.atCenterOf(pos);
        for (int i = 0; i < count; i++) {
            WispApi.spawn(level, centre, aspect);
        }
    }

    /** A random aspect from the rift; TH left a wisp's type unset when the rift carried none, so a primal stands in. */
    @Nullable
    private AspectId randomAspect(RandomSource random) {
        if (!aspects.isEmpty()) {
            List<AspectId> keys = new ArrayList<>(aspects.keySet());
            return keys.get(random.nextInt(keys.size()));
        }
        List<AspectId> primals = List.copyOf(AspectApi.primals());
        return primals.isEmpty() ? null : primals.get(random.nextInt(primals.size()));
    }

    /** TH's {@code itemResource} meta 14, the salis mundus that seeds wisps. */
    private static Item salisMundus() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("thaumcraft", "salis_mundus"));
    }

    /** TH's wand right-click: the oldest stored item is pushed back out of the rift. */
    public void popStored(Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!items.isEmpty()) {
            ItemStack stack = items.remove(0);
            ItemEntity drop = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, stack);
            level.addFreshEntity(drop);
            setChanged();
        }
        // TH played this on every wand click, whether or not anything came out.
        level.playSound(null, worldPosition, TCSounds.WAND.get(), SoundSource.BLOCKS, 0.5F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /** Stamps a fresh plane from the rift's aspects and turns this rift into its doorway (TH's {@code createDimension}). */
    private void createDimension(ServerLevel server, BlockPos pos, ItemEntity pearl) {
        var mcServer = server.getServer();
        ServerLevel pocket = PocketPlane.level(mcServer);
        if (pocket == null) {
            return;
        }
        ItemStack stack = pearl.getItem();
        String name;
        //? if forge {
        if (stack.hasCustomHoverName()) {
        //?} else {
        /*if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
        *///?}
            name = stack.getHoverName().getString();
        } else {
            String owner = ownerName(pearl);
            name = (owner == null ? "" : owner) + Component.translatable("isorropia.pocketplane").getString();
        }
        int id = PocketPlaneBuilder.create(pocket, PocketPlaneStore.get(mcServer), name, Map.copyOf(aspects),
                server.dimension(), pos);
        this.planeId = id;
        this.returnDimension = server.dimension();
        this.createdDimension = true;
        syncToClients();
    }

    /** The name of whoever threw the pearl, as TH's default plane name was built from it. */
    @Nullable
    private static String ownerName(ItemEntity pearl) {
        Entity owner = pearl.getOwner();
        return owner == null ? null : owner.getName().getString();
    }

    /** TH's doorway: a traveller standing in the rift is carried into the plane, and home again from its exit rift. */
    private void transferTravellers(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos))) {
            if (player.isPassenger() || player.isVehicle()) {
                continue;
            }
            if (player.getPortalCooldown() > 0) {
                // Mirror TH's reset-to-ten: a traveller held in the rift never bounces straight back.
                player.setPortalCooldown(Math.min(player.getPortalCooldown(), 10));
                continue;
            }
            if (!level.dimension().equals(PocketPlane.LEVEL)) {
                PocketPlane.enterPlane(player, this.planeId);
            } else {
                PocketPlaneStore.Plane plane = PocketPlaneStore.get(server.getServer()).plane(this.planeId);
                ServerLevel home = plane == null ? null : server.getServer().getLevel(plane.returnDimension());
                if (home == null) {
                    continue;
                }
                BlockPos spot = plane.returnVortex();
                PocketPlane.sendTo(player, home, new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
            }
            player.setPortalCooldown(VORTEX_PORTAL_COOLDOWN);
        }
    }

    /** TH's collapse: the rift vanishes in a blast and seeds flux over whatever it was holding. */
    private static void collapse(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        server.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0F,
                Level.ExplosionInteraction.BLOCK);
        for (int attempt = 0; attempt < 50; attempt++) {
            RandomSource random = server.getRandom();
            BlockPos target = pos.offset(random.nextInt(8) - random.nextInt(8),
                    random.nextInt(8) - random.nextInt(8), random.nextInt(8) - random.nextInt(8));
            if (server.getBlockState(target).isAir()) {
                var fluid = target.getY() < pos.getY()
                        ? dev.tc4port.thaumcraft.registry.TCFluids.FLUX_GOO.get()
                        : dev.tc4port.thaumcraft.registry.TCFluids.FLUX_GAS.get();
                dev.tc4port.thaumcraft.api.flux.FluxApi.place(server, target, fluid, 9, 3);
            }
        }
    }

    @Override
    public AspectAmounts visibleAspects() {
        return new AspectAmounts(aspects);
    }

    private void syncToClients() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
        tag.putInt("count", this.count);
        tag.putInt("beams", this.beams);
        tag.putBoolean("ateDevices", this.ateDevices);
        tag.putBoolean("collapsing", this.collapsing);
        tag.putBoolean("cheat", this.cheat);
        tag.putInt("planeId", this.planeId);
        tag.putBoolean("createdDimension", this.createdDimension);
        tag.putString("returnDimension", this.returnDimension.location().toString());
        ListTag aspectTags = new ListTag();
        for (Map.Entry<AspectId, Integer> entry : this.aspects.entrySet()) {
            CompoundTag aspectTag = new CompoundTag();
            aspectTag.putString("key", entry.getKey().serialized());
            aspectTag.putInt("amount", entry.getValue());
            aspectTags.add(aspectTag);
        }
        tag.put("aspects", aspectTags);
        ListTag itemTags = new ListTag();
        for (ItemStack item : this.items) {
            //? if forge {
            itemTags.add(item.save(new CompoundTag()));
            //?} else {
            /*itemTags.add(item.save(registries));
            *///?}
        }
        tag.put("items", itemTags);
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
        this.count = tag.getInt("count");
        this.beams = tag.getInt("beams");
        this.ateDevices = tag.getBoolean("ateDevices");
        this.collapsing = tag.getBoolean("collapsing");
        this.cheat = tag.getBoolean("cheat");
        this.planeId = tag.getInt("planeId");
        this.createdDimension = tag.getBoolean("createdDimension");
        this.returnDimension = tag.contains("returnDimension")
                ? ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(tag.getString("returnDimension")))
                : Level.OVERWORLD;
        this.lastSyncedBeams = this.beams;
        this.aspects.clear();
        ListTag aspectTags = tag.getList("aspects", Tag.TAG_COMPOUND);
        for (int i = 0; i < aspectTags.size(); i++) {
            CompoundTag aspectTag = aspectTags.getCompound(i);
            AspectId id = AspectId.parse(aspectTag.getString("key"));
            if (id != null) {
                this.aspects.put(id, aspectTag.getInt("amount"));
            }
        }
        this.items.clear();
        ListTag itemTags = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemTags.size(); i++) {
            //? if forge {
            ItemStack stack = ItemStack.of(itemTags.getCompound(i));
            //?} else {
            /*ItemStack stack = ItemStack.parseOptional(registries, itemTags.getCompound(i));
            *///?}
            if (!stack.isEmpty()) {
                this.items.add(stack);
            }
        }
    }
}
