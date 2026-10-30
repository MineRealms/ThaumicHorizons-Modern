package fr.wind_blade.isorropia.common.focus;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusUpgradeId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import dev.tc4port.thaumcraft.api.item.SpecialMiningApi;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.items.ItemFocusLiquefaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The liquefaction focus, as Thaumic Horizons built it for Thaumcraft 4: a fiery beam held on a target that
 * melts inanimate matter. Blocks are smelted as though they had passed through a furnace, ice becomes water,
 * unworked stone is slowly transmuted into lava, plant matter burns away, and flammable blocks are set alight.
 * The beam hurts creatures only weakly. Loose items on the ground are smelted in place.
 *
 * <p>Harder matter takes longer: the beam fills a measure of the block's hardness before the block gives way, so
 * a stone block takes far longer than a tuft of grass. The measure is kept per player and resets the moment the
 * beam moves to a different block.
 *
 * <p>Each melted block costs its vis; a creature costs the smaller critter rate instead. The nuggets upgrade
 * leaves infernal-furnace bonus metal behind, and purity transmutes ore into native clusters as it melts.
 */
public final class LiquefactionFocus {
    public static final ResourceLocation ACTION = Isorropia.id("liquefaction");
    public static final ResearchKey RESEARCH = ResearchKey.of(Isorropia.MODID, "liquefaction");
    /** How far the beam reaches. */
    public static final int RANGE = 10;
    /** The upgrade that speeds the beam up and sharpens it against creatures. */
    public static final FocusUpgradeId POTENCY = FocusUpgradeId.of(ResourceLocation.fromNamespaceAndPath("thaumcraft", "potency"));
    /** The upgrade that widens the beam to the blocks around the target. */
    public static final FocusUpgradeId ENLARGE = FocusUpgradeId.of(ResourceLocation.fromNamespaceAndPath("thaumcraft", "enlarge"));
    /** The upgrade that leaves infernal-furnace bonus metal behind. */
    public static final FocusUpgradeId NUGGETS = FocusUpgradeId.of(Isorropia.id("nuggets"));
    /** The upgrade that transmutes ore into native clusters. */
    public static final FocusUpgradeId PURITY = FocusUpgradeId.of(Isorropia.id("purity"));
    /** The named cost the catalog declares for striking a creature rather than a block. */
    private static final String CRITTER = "critter";
    /** The hiss of the beam repeats this often, as upstream's 1.2 second delay. */
    private static final long HUM_TICKS = 24L;
    /** The chance each nuggets level has to add one bonus item, as upstream's 45%. */
    private static final float NUGGET_CHANCE = 0.45F;
    /** Purity always converts an ore, matching upstream's effectively certain special-mining roll. */
    private static final float PURITY_CHANCE = 9999.0F;
    /** How long the beam sets a creature alight. */
    private static final int BURN_SECONDS = 4;
    /** The measure each player has filled on the block they are aiming at. */
    private static final Map<UUID, Progress> PROGRESS = new HashMap<>();
    /** When each player's beam last hissed. */
    private static final Map<UUID, Long> HUM = new HashMap<>();

    /** A player's beam on one block: which block, and how far it has melted into it. */
    private record Progress(long block, float measure) {
    }

    /**
     * What the beam turns a block into. Harder matter takes longer to melt; {@link #NONE} means the beam must
     * fall back to a furnace recipe or to fire.
     */
    private enum Melt {
        NONE(0.0F),
        /** Plant matter and other soft blocks simply vanish. */
        AIR(0.25F),
        /** Ice and packed ice thaw, or vanish in the Nether. */
        WATER(0.25F),
        /** Grass and mycelium become plain dirt. */
        DIRT(0.25F),
        /** Gunpowder blocks detonate. */
        EXPLODE(0.25F),
        /** Unworked stone is transmuted into lava, slowly. */
        LAVA(20.0F);

        private final float hardness;

        Melt(float hardness) {
            this.hardness = hardness;
        }

        float hardness() {
            return this.hardness;
        }
    }

    private LiquefactionFocus() {
    }

    /** Registers the beam with Thaumcraft's wand. Called on both sides, as the wand predicts on the client. */
    public static void register() {
        // The rates and the critter cost come from the focus catalog; declaring them stops a datapack dropping them.
        FocusApi.requireParameters(ItemFocusLiquefaction.ID, Set.of("vis_discount", "break_speed", "enlarge"));
        FocusApi.requireActionCosts(ItemFocusLiquefaction.ID, Set.of(CRITTER));
        FocusActionApi.register(FocusCompat.spec(ACTION, ItemFocusLiquefaction.ID, FocusCompat.Aim.AIR,
                FocusCompat.Mode.CHANNEL, true, ResearchRequirement.required(RESEARCH), RANGE, 0),
                new dev.tc4port.thaumcraft.api.focus.action.FocusActionHandler() {
                    @Override public HitResult target(dev.tc4port.thaumcraft.api.focus.behavior.FocusUseContext use,
                            dev.tc4port.thaumcraft.api.wand.pipeline.WandCastStages.Aim query) {
                        return ordinaryTarget(query);
                    }
                    @Override public FocusActionResult handle(FocusActionContext context, VisAction action) {
                        return LiquefactionFocus.handle(context, action);
                    }
                }, LiquefactionFocus::authorize);
    }

    private static FocusActionResult handle(FocusActionContext context, VisAction action) {
        return switch (FocusCompat.phase(context)) {
            case PRESS -> FocusActionResult.START_USING;
            case STOP -> {
                forget(context.player().getUUID());
                yield FocusActionResult.SUCCESS;
            }
            case TICK -> tick(context, action);
        };
    }

    private static dev.tc4port.thaumcraft.api.focus.action.FocusAuthorization authorize(
            dev.tc4port.thaumcraft.api.focus.action.FocusAuthorizationContext context) {
        var allowed = dev.tc4port.thaumcraft.api.focus.action.FocusAuthorization.allow();
        if (context.interaction().gesture() != dev.tc4port.thaumcraft.api.focus.action.FocusGesture.HOLD
                || !(context.authoritativeHit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return allowed;
        var level = context.player().serverLevel();
        BlockPos pos = hit.getBlockPos();
        if (!level.isLoaded(pos)) return dev.tc4port.thaumcraft.api.focus.action.FocusAuthorization.deny();
        var state = level.getBlockState(pos);
        Progress last = PROGRESS.get(context.player().getUUID());
        Melt melt = melt(state);
        float hardness = melt == Melt.LAVA ? Melt.LAVA.hardness()
                : (melt != Melt.NONE || isFlammable(level, pos, state)) ? 0.25F : 2.0F;
        float measure = last != null && last.block() == pos.asLong() ? last.measure() : 0;
        if (measure + context.parameter("break_speed") < hardness) return allowed;
        List<BlockPos> positions = new ArrayList<>();
        positions.add(pos);
        if (context.parameter("enlarge") > 0) positions.addAll(affectableNeighbours(level, pos));
        return FocusBlockAuthorization.check(context.player(), positions, pos, context.parameter("enlarge") > 0 ? 1 : 0);
    }

    /** One tick of the held beam. Simulation must not change anything, so all effects wait for EXECUTE. */
    private static FocusActionResult tick(FocusActionContext context, VisAction action) {
        HitResult hit = context.authoritativeHit();
        if (hit instanceof EntityHitResult entityHit) {
            var entity = entityHit.getEntity();
            if (entity.level() != context.player().level() || !entity.isAlive() || entity.isRemoved()
                    || context.player().level().getEntity(entity.getId()) != entity) return FocusActionResult.PASS;
            if (entityHit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                return burn(context, action, target);
            }
            if (entityHit.getEntity() instanceof ItemEntity drop) {
                return smeltDrop(context, action, drop);
            }
            return FocusActionResult.PASS;
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return tickBlock(context, action, blockHit.getBlockPos());
        }
        return FocusActionResult.PASS;
    }

    /** The nearest dropped item the beam points at, which the wand's pickable-only ray cannot see. */
    private static HitResult ordinaryTarget(dev.tc4port.thaumcraft.api.wand.pipeline.WandCastStages.Aim query) {
        var player = query.player();
        Vec3 start = player.getEyePosition();
        Vec3 reach = player.getViewVector(1.0F).normalize().scale(query.range());
        Vec3 end = start.add(reach);
        HitResult block = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        var search = player.getBoundingBox().expandTowards(reach).inflate(1.0D);
        EntityHitResult found = ProjectileUtil.getEntityHitResult(player, start, end, search,
                candidate -> !candidate.isSpectator() && candidate.isPickable(), query.range() * query.range());
        if (found != null) return found;
        // Preserve the original pickable-entity priority over dropped items.
        EntityHitResult drop = ProjectileUtil.getEntityHitResult(player, start, end, search,
                candidate -> candidate instanceof ItemEntity item,
                query.range() * query.range());
        return drop == null ? block : drop;
    }

    /** The beam hurts a creature only weakly; it is no weapon, merely a very hot draught. */
    private static FocusActionResult burn(FocusActionContext context, VisAction action, LivingEntity target) {
        if (!context.pay(context.configuredCost(CRITTER), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        if (action == VisAction.EXECUTE) {
            ServerLevel level = context.player().serverLevel();
            int potency = context.installedUpgrades().getOrDefault(POTENCY, 0);
            target.setSecondsOnFire(BURN_SECONDS);
            target.hurt(level.damageSources().inFire(), 1.0F + 0.5F * potency);
            hum(context, level, target.position());
            PROGRESS.remove(context.player().getUUID());
        }
        return FocusActionResult.SUCCESS;
    }

    /** Loose items struck by the beam are smelted in place, as though they had passed through a furnace. */
    private static FocusActionResult smeltDrop(FocusActionContext context, VisAction action, ItemEntity drop) {
        ServerLevel level = context.player().serverLevel();
        ItemStack input = drop.getItem();
        ItemStack result = smelt(level, input);
        if (result.isEmpty()) {
            return FocusActionResult.PASS;
        }
        if (!context.pay(context.configuredCost(), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        if (action == VisAction.EXECUTE) {
            drop.setItem(result.copyWithCount(input.getCount()));
            hum(context, level, drop.position());
            PROGRESS.remove(context.player().getUUID());
        }
        return FocusActionResult.SUCCESS;
    }

    /** The beam slowly melts into a block, and does its work once the measure passes the block's hardness. */
    private static FocusActionResult tickBlock(FocusActionContext context, VisAction action, BlockPos pos) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return FocusActionResult.PASS;
        BlockState state = level.getBlockState(pos);
        Melt melt = melt(state);
        ItemStack blockStack = new ItemStack(state.getBlock());
        boolean smeltable = !blockStack.isEmpty() && !smelt(level, blockStack).isEmpty();
        boolean flammable = isFlammable(level, pos, state);
        if (melt == Melt.NONE && !smeltable && !flammable) {
            return FocusActionResult.PASS;
        }
        // Meltable matter and anything that burns gives way quickly; only a plain furnace recipe is stubborn.
        float hardness = melt == Melt.LAVA ? Melt.LAVA.hardness()
                : (melt != Melt.NONE || flammable) ? 0.25F : 2.0F;
        float speed = (float) context.parameter("break_speed");
        UUID id = player.getUUID();
        Progress last = PROGRESS.get(id);
        float measure = last != null && last.block() == pos.asLong() ? last.measure() : 0.0F;
        if (measure + speed < hardness) {
            if (action == VisAction.EXECUTE) {
                hum(context, level, Vec3.atCenterOf(pos));
                PROGRESS.put(id, new Progress(pos.asLong(), measure + speed));
            }
            return FocusActionResult.SUCCESS;
        }
        // The block gives way this tick. The widened beam takes its neighbours with it, each costing its own vis.
        List<BlockPos> neighbours = context.parameter("enlarge") > 0 ? affectableNeighbours(level, pos) : List.of();
        for (int index = 0; index <= neighbours.size(); index++) {
            if (!context.pay(context.configuredCost(), action).affordable()) {
                return FocusActionResult.FAILED;
            }
        }
        if (action == VisAction.EXECUTE) {
            melt(level, context, pos, state, melt, blockStack, smeltable, flammable);
            for (BlockPos neighbour : neighbours) {
                BlockState neighbourState = level.getBlockState(neighbour);
                ItemStack neighbourStack = new ItemStack(neighbourState.getBlock());
                melt(level, context, neighbour, neighbourState, melt(neighbourState), neighbourStack,
                        !neighbourStack.isEmpty() && !smelt(level, neighbourStack).isEmpty(),
                        isFlammable(level, neighbour, neighbourState));
            }
            PROGRESS.remove(id);
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT,
                    SoundSource.PLAYERS, 0.3F, 1.0F);
        }
        return FocusActionResult.SUCCESS;
    }

    /** Applies the beam's effect to one block: melt it, smelt it, or set it alight, in that order of priority. */
    private static void melt(ServerLevel level, FocusActionContext context, BlockPos pos, BlockState state,
            Melt melt, ItemStack blockStack, boolean smeltable, boolean flammable) {
        switch (melt) {
            case LAVA -> level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
            case WATER -> level.setBlock(pos,
                    level.dimension() == Level.NETHER ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);
            case DIRT -> level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            case AIR -> level.removeBlock(pos, false);
            case EXPLODE -> {
                level.removeBlock(pos, false);
                level.addFreshEntity(new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.player()));
            }
            case NONE -> {
                if (smeltable) {
                    smeltBlock(level, context, pos, blockStack, state);
                } else if (flammable) {
                    level.setBlock(pos, BaseFireBlock.getState(level, pos), 3);
                }
            }
        }
    }

    /** Smelts a block into its furnace product, dropping it if it is not itself a block. */
    private static void smeltBlock(ServerLevel level, FocusActionContext context, BlockPos pos, ItemStack blockStack,
            BlockState state) {
        int nuggets = context.installedUpgrades().getOrDefault(NUGGETS, 0);
        // Purity purifies an ore into its native cluster instead of smelting it into plain metal.
        if (context.installedUpgrades().getOrDefault(PURITY, 0) > 0 && purify(level, pos, state, blockStack)) {
            dropNuggets(level, pos, blockStack, nuggets);
            return;
        }
        ItemStack result = smelt(level, blockStack);
        if (result.isEmpty()) {
            return;
        }
        result = result.copy();
        if (result.getItem() instanceof BlockItem blockItem) {
            level.setBlock(pos, blockItem.getBlock().defaultBlockState(), 3);
        } else {
            level.removeBlock(pos, false);
            Block.popResource(level, pos, result);
        }
        dropNuggets(level, pos, blockStack, nuggets);
    }

    /** Drops an ore's native clusters instead of its plain smelt, and reports whether anything was purified. */
    private static boolean purify(ServerLevel level, BlockPos pos, BlockState state, ItemStack blockStack) {
        boolean purified = false;
        for (ItemStack cluster : SpecialMiningApi.convert(blockStack, PURITY_CHANCE, level, state, pos)) {
            if (cluster.isEmpty() || ItemStack.isSameItemSameTags(cluster, blockStack)) {
                continue;
            }
            Block.popResource(level, pos, cluster);
            purified = true;
        }
        if (purified) {
            level.removeBlock(pos, false);
        }
        return purified;
    }

    /** Leaves the infernal furnace's bonus metal behind, one roll per nuggets level as upstream did. */
    private static void dropNuggets(ServerLevel level, BlockPos pos, ItemStack blockStack, int nuggets) {
        if (nuggets <= 0) {
            return;
        }
        for (ItemStack result : dev.tc4port.thaumcraft.api.recipe.InfernalSmeltingApi.bonusOutputs(level, blockStack)) {
            ItemStack stack = result.copy();
            for (int index = 0; index < nuggets; index++) {
                if (level.random.nextFloat() < NUGGET_CHANCE) stack.grow(1);
            }
            Block.popResource(level, pos, stack);
        }
    }

    /** The blocks around the target the widened beam would also affect, so their vis can be charged too. */
    private static List<BlockPos> affectableNeighbours(ServerLevel level, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos neighbour = pos.offset(x, y, z);
                    if (!level.isLoaded(neighbour)) continue;
                    BlockState state = level.getBlockState(neighbour);
                    ItemStack stack = new ItemStack(state.getBlock());
                    if (melt(state) != Melt.NONE || !stack.isEmpty() && !smelt(level, stack).isEmpty()
                            || isFlammable(level, neighbour, state)) {
                        result.add(neighbour);
                    }
                }
            }
        }
        return result;
    }

    /** The furnace product of a stack, or an empty stack when it has none. */
    private static ItemStack smelt(ServerLevel level, ItemStack input) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        //? if forge {
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), level)
                .map(recipe -> recipe.getResultItem(level.registryAccess()))
        //?} else {
        /*return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                        new net.minecraft.world.item.crafting.SingleRecipeInput(input), level)
                .map(holder -> holder.value().getResultItem(level.registryAccess()))
        *///?}
                .filter(result -> !result.isEmpty())
                .orElse(ItemStack.EMPTY);
    }

    private static boolean isFlammable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.isFlammable(level, pos, Direction.UP);
    }

    /** What the beam turns a block into, mirroring upstream's material table. */
    private static Melt melt(BlockState state) {
        if (state.is(Blocks.STONE)) {
            return Melt.LAVA;
        }
        if (state.is(Blocks.TNT)) {
            return Melt.EXPLODE;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT_PATH)) {
            return Melt.DIRT;
        }
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE)) {
            return Melt.WATER;
        }
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.VINE) || state.is(Blocks.COBWEB)
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS) || state.is(BlockTags.SNOW)) {
            return Melt.AIR;
        }
        return Melt.NONE;
    }

    private static void hum(FocusActionContext context, ServerLevel level, Vec3 at) {
        UUID id = context.player().getUUID();
        long now = level.getGameTime();
        Long next = HUM.get(id);
        if (next == null || now >= next) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.3F, 1.0F);
            HUM.put(id, now + HUM_TICKS);
        }
    }

    /** Forgets a player's beam state, so a disconnected player's measure does not linger. */
    public static void forget(UUID player) {
        PROGRESS.remove(player);
        HUM.remove(player);
    }
}
