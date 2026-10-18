package fr.wind_blade.isorropia.common.focus;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.AspectQueryApi;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.damage.ThaumcraftDamageTypes;
import dev.tc4port.thaumcraft.api.event.GameplayHooks;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusUpgradeId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.item.ThaumcraftItemApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import dev.tc4port.thaumcraft.registry.CrystalEssences;
import dev.tc4port.thaumcraft.registry.TCSounds;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.items.ItemFocusDisintegration;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
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
 * The disintegration focus, as Thaumic Horizons built it for Thaumcraft 4: a purple beam held on a target that
 * unbinds it into raw magical essence. A block that carries aspects is worn down and, when it finally gives
 * way, dissolves into one crystal of crystallized essentia per point of every aspect it held. Loose items are
 * disintegrated the same way, whole stacks at once. Creatures are not the focus' business, but the beam still
 * sears them weakly, and the enervation upgrade leaves them slowed.
 *
 * <p>Only matter that carries aspects can be disintegrated; a plain block of dirt simply ignores the beam.
 * Harder matter takes longer: the beam fills a measure of the block's hardness before the block gives way.
 * The measure is kept per player and resets the moment the beam moves to a different block. The enlarge
 * upgrade, once the aim point breaks, takes the whole shell of blocks around it as well.
 *
 * <p>Each block costs its vis and a creature costs the smaller critter rate instead.
 */
public final class DisintegrationFocus {
    public static final ResourceLocation ACTION = Isorropia.id("disintegration");
    public static final ResearchKey RESEARCH = ResearchKey.of(Isorropia.MODID, "disintegration");
    /** How far the beam reaches. */
    public static final int RANGE = 10;
    /** The upgrade that speeds the beam up and sharpens it against creatures. */
    public static final FocusUpgradeId POTENCY = FocusUpgradeId.of(ResourceLocation.fromNamespaceAndPath("thaumcraft", "potency"));
    /** The upgrade that widens the beam to the blocks around the target once it breaks. */
    public static final FocusUpgradeId ENLARGE = FocusUpgradeId.of(ResourceLocation.fromNamespaceAndPath("thaumcraft", "enlarge"));
    /** The upgrade that slows the creatures the beam sears. */
    public static final FocusUpgradeId ENERVATION = FocusUpgradeId.of(Isorropia.id("enervation"));
    /** The named cost the catalog declares for striking a creature rather than a block. */
    private static final String CRITTER = "critter";
    /** The crackle of the beam repeats this often. Upstream's 100 ms delay is thinned to a quarter second. */
    private static final long HUM_TICKS = 5L;
    /** How long the enervation upgrade slows a creature. */
    private static final int SLOW_TICKS = 40;
    /** The measure each player has filled on the block they are aiming at. */
    private static final Map<UUID, Progress> PROGRESS = new HashMap<>();
    /** When each player's beam last crackled. */
    private static final Map<UUID, Long> HUM = new HashMap<>();

    /** A player's beam on one block: which block, and how far it has eaten into it. */
    private record Progress(long block, float measure) {
    }

    private DisintegrationFocus() {
    }

    /** Registers the beam with Thaumcraft's wand. Called on both sides, as the wand predicts on the client. */
    public static void register() {
        // The rates and the critter cost come from the focus catalog; declaring them stops a datapack dropping them.
        FocusApi.requireParameters(ItemFocusDisintegration.ID, Set.of("vis_discount", "break_speed", "enlarge"));
        FocusApi.requireActionCosts(ItemFocusDisintegration.ID, Set.of(CRITTER));
        FocusActionApi.register(FocusCompat.spec(ACTION, ItemFocusDisintegration.ID, FocusCompat.Aim.AIR,
                FocusCompat.Mode.CHANNEL, true, ResearchRequirement.required(RESEARCH), RANGE, 0),
                DisintegrationFocus::handle);
    }

    private static FocusActionResult handle(FocusActionContext context, VisAction action) {
        return switch (FocusCompat.phase(context)) {
            case PRESS -> FocusActionResult.START_USING;
            case STOP -> {
                if (action == VisAction.EXECUTE) {
                    stop(context);
                }
                yield FocusActionResult.SUCCESS;
            }
            case TICK -> tick(context, action);
        };
    }

    /** One tick of the held beam. Simulation must not change anything, so all effects wait for EXECUTE. */
    private static FocusActionResult tick(FocusActionContext context, VisAction action) {
        HitResult hit = context.authoritativeHit();
        if (hit instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                return dissolve(context, action, target);
            }
            if (entityHit.getEntity() instanceof ItemEntity drop) {
                return disintegrateItem(context, action, drop);
            }
            return FocusActionResult.PASS;
        }
        // The wand's own ray skips dropped items, which are not pickable; an essence-bearing one still counts.
        ItemEntity drop = pointedEssence(context);
        if (drop != null) {
            return disintegrateItem(context, action, drop);
        }
        if (hit instanceof BlockHitResult blockHit) {
            return tickBlock(context, action, blockHit.getBlockPos());
        }
        return FocusActionResult.PASS;
    }

    /** The nearest essence-bearing dropped item the beam points at, which the wand's pickable-only ray cannot see. */
    private static ItemEntity pointedEssence(FocusActionContext context) {
        ServerPlayer player = context.player();
        Vec3 start = player.getEyePosition();
        Vec3 reach = player.getViewVector(1.0F).normalize().scale(RANGE);
        Vec3 end = start.add(reach);
        HitResult block = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult found = ProjectileUtil.getEntityHitResult(player, start, end,
                player.getBoundingBox().expandTowards(reach).inflate(1.0D),
                candidate -> candidate instanceof ItemEntity item && disintegrable(item.getItem()),
                (double) RANGE * RANGE);
        return found != null && found.getEntity() instanceof ItemEntity item ? item : null;
    }

    /**
     * Whether the beam would unbind this dropped stack: it must carry aspects, and it must not be crystallized
     * essentia itself. Upstream's ray ignored crystal essence so the beam never eats the crystals it just made.
     */
    private static boolean disintegrable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() != CrystalEssences.item()
                && !AspectQueryApi.item(stack).amounts().isEmpty();
    }

    /** The beam sears a creature only weakly; the enervation upgrade leaves it dragging its feet. */
    private static FocusActionResult dissolve(FocusActionContext context, VisAction action, LivingEntity target) {
        if (!context.pay(context.configuredCost(CRITTER), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        if (action == VisAction.EXECUTE) {
            ServerLevel level = context.player().serverLevel();
            int potency = context.installedUpgrades().getOrDefault(POTENCY, 0);
            int enervation = context.installedUpgrades().getOrDefault(ENERVATION, 0);
            if (enervation > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0));
            }
            target.hurt(ThaumcraftDamageTypes.dissolve(level), 0.5F + 0.25F * potency + 0.5F * enervation);
            hum(context, level, target.position());
            PROGRESS.remove(context.player().getUUID());
        }
        return FocusActionResult.SUCCESS;
    }

    /** A whole dropped stack unbinds at once, one crystal per point of every aspect it carried. */
    private static FocusActionResult disintegrateItem(FocusActionContext context, VisAction action, ItemEntity drop) {
        ItemStack input = drop.getItem();
        if (!disintegrable(input)) {
            return FocusActionResult.PASS;
        }
        AspectAmounts aspects = AspectQueryApi.item(input);
        if (!context.pay(context.configuredCost(), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        if (action == VisAction.EXECUTE) {
            ServerLevel level = context.player().serverLevel();
            int count = input.getCount();
            Vec3 at = drop.position();
            for (Map.Entry<AspectId, Integer> entry : aspects.amounts().entrySet()) {
                spawnEssence(level, at.x, at.y, at.z, entry.getKey(), entry.getValue() * count);
            }
            drop.discard();
            hum(context, level, at);
            PROGRESS.remove(context.player().getUUID());
        }
        return FocusActionResult.SUCCESS;
    }

    /** The beam slowly eats into an aspect-bearing block, unbinding it once the measure passes its hardness. */
    private static FocusActionResult tickBlock(FocusActionContext context, VisAction action, BlockPos pos) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        ItemStack blockStack = new ItemStack(state.getBlock());
        AspectAmounts aspects = blockStack.isEmpty() ? AspectAmounts.EMPTY
                : AspectQueryApi.placedBlock(blockStack, state);
        if (aspects.amounts().isEmpty()) {
            return FocusActionResult.PASS;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return FocusActionResult.PASS;
        }
        float speed = (float) context.parameter("break_speed");
        UUID id = player.getUUID();
        int overlay = overlayId(player);
        Progress last = PROGRESS.get(id);
        if (last == null || last.block() != pos.asLong()) {
            // A new target: drop the old crack overlay and start the measure from scratch this tick.
            if (last != null) {
                level.destroyBlockProgress(overlay, BlockPos.of(last.block()), -1);
            }
            if (action == VisAction.EXECUTE) {
                PROGRESS.put(id, new Progress(pos.asLong(), 0.0F));
            }
            return FocusActionResult.SUCCESS;
        }
        float measure = last.measure();
        if (measure > 0.0F && action == VisAction.EXECUTE) {
            int stage = hardness <= 0.0F ? 9 : Math.min(9, (int) (measure / hardness * 9.0F));
            level.destroyBlockProgress(overlay, pos, stage);
        }
        if (measure + speed < hardness) {
            if (action == VisAction.EXECUTE) {
                hum(context, level, Vec3.atCenterOf(pos));
                PROGRESS.put(id, new Progress(pos.asLong(), measure + speed));
            }
            return FocusActionResult.SUCCESS;
        }
        // The block gives way this tick. The widened beam takes the shell of blocks around it, each costing its own vis.
        List<BlockPos> neighbours = context.parameter("enlarge") > 0 ? disintegrableNeighbours(level, pos) : List.of();
        for (int index = 0; index <= neighbours.size(); index++) {
            if (!context.pay(context.configuredCost(), action).affordable()) {
                return FocusActionResult.FAILED;
            }
        }
        if (action == VisAction.EXECUTE) {
            disintegrateBlock(level, context, pos, state, aspects);
            for (BlockPos neighbour : neighbours) {
                BlockState neighbourState = level.getBlockState(neighbour);
                ItemStack neighbourStack = new ItemStack(neighbourState.getBlock());
                AspectAmounts neighbourAspects = neighbourStack.isEmpty() ? AspectAmounts.EMPTY
                        : AspectQueryApi.placedBlock(neighbourStack, neighbourState);
                disintegrateBlock(level, context, neighbour, neighbourState, neighbourAspects);
            }
            level.destroyBlockProgress(overlay, pos, -1);
            PROGRESS.remove(id);
            hum(context, level, Vec3.atCenterOf(pos));
        }
        return FocusActionResult.SUCCESS;
    }

    /** Unbinds one block into its crystallized essentia, honouring the block-break event first. */
    private static void disintegrateBlock(ServerLevel level, FocusActionContext context, BlockPos pos, BlockState state,
            AspectAmounts aspects) {
        if (state.isAir() || aspects.amounts().isEmpty()) {
            return;
        }
        ServerPlayer player = context.player();
        if (GameplayHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()) {
            return;
        }
        for (Map.Entry<AspectId, Integer> entry : aspects.amounts().entrySet()) {
            spawnEssence(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, entry.getKey(), entry.getValue());
        }
        level.removeBlock(pos, false);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
    }

    /** The blocks around the target the widened beam would also unbind, so their vis can be charged too. */
    private static List<BlockPos> disintegrableNeighbours(ServerLevel level, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos neighbour = pos.offset(x, y, z);
                    BlockState state = level.getBlockState(neighbour);
                    ItemStack stack = new ItemStack(state.getBlock());
                    if (!stack.isEmpty() && !AspectQueryApi.placedBlock(stack, state).amounts().isEmpty()) {
                        result.add(neighbour);
                    }
                }
            }
        }
        return result;
    }

    /** Drops one invulnerable crystal of the given aspect, its stack count carrying the amount. */
    private static void spawnEssence(ServerLevel level, double x, double y, double z, AspectId aspect, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack essence = ThaumcraftItemApi.createCrystalEssence(aspect);
        essence.setCount(count);
        ItemEntity drop = new ItemEntity(level, x, y, z, essence);
        drop.setInvulnerable(true);
        level.addFreshEntity(drop);
    }

    private static void hum(FocusActionContext context, ServerLevel level, Vec3 at) {
        UUID id = context.player().getUUID();
        long now = level.getGameTime();
        Long next = HUM.get(id);
        if (next == null || now >= next) {
            level.playSound(null, at.x, at.y, at.z, TCSounds.ZAP.get(), SoundSource.PLAYERS, 0.3F, 1.0F);
            HUM.put(id, now + HUM_TICKS);
        }
    }

    /** Clears a player's beam state and crack overlay when the wand is released. */
    private static void stop(FocusActionContext context) {
        ServerPlayer player = context.player();
        UUID id = player.getUUID();
        Progress previous = PROGRESS.remove(id);
        HUM.remove(id);
        if (previous != null) {
            player.serverLevel().destroyBlockProgress(overlayId(player), BlockPos.of(previous.block()), -1);
        }
    }

    /** A stable, per-player crack-overlay id that is still broadcast to the caster. */
    private static int overlayId(ServerPlayer player) {
        return ~player.getId();
    }
}
