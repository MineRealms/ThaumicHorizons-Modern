package fr.wind_blade.isorropia.common.focus;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.api.event.GameplayHooks;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusUpgradeId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionHandler;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionSpec;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionTarget;
import dev.tc4port.thaumcraft.api.focus.action.FocusCastMode;
import dev.tc4port.thaumcraft.api.focus.action.FocusGesture;
import dev.tc4port.thaumcraft.api.focus.action.FocusInput;
import dev.tc4port.thaumcraft.api.focus.action.FocusTargetKind;
import dev.tc4port.thaumcraft.api.presentation.ActionPresentationAudience;
import dev.tc4port.thaumcraft.api.focus.behavior.FocusPredictionContext;
import dev.tc4port.thaumcraft.api.focus.behavior.FocusUseResult;
import dev.tc4port.thaumcraft.api.wand.pipeline.WandCastStages;
import dev.tc4port.thaumcraft.api.wand.pipeline.WandContinuation;
import dev.tc4port.thaumcraft.api.wand.pipeline.WandPipelineApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.entities.AnimatedBlockType;
import fr.wind_blade.isorropia.common.entities.EntitiesIS;
import fr.wind_blade.isorropia.common.entities.EntityAnimatedBlock;
import fr.wind_blade.isorropia.common.items.ItemFocusAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The animation focus, as Thaumic Horizons built it for Thaumcraft 4: a wand pointed at a block calls it out of
 * the world and sets it walking. The conjured golem wears the block it was made from, takes that material's
 * health, armour and gait, follows whoever called it, and fights off monsters until its span runs out, when it
 * settles back down into the block it came from.
 *
 * <p>Only plain, breakable blocks answer the call: a block with a tile entity, a fluid or a plant is left alone,
 * and so is bedrock. A few transparent but solid blocks - cake, cactus, glass, ice, a cobweb, gunpowder - are
 * called out all the same.
 *
 * <p>Raising the golem costs the same vis of all six primal aspects, more for the tougher materials, and the
 * extend upgrade lengthens the span; the berserk upgrade turns the golem on anything but its caller, and the
 * detonation upgrade makes it burst when it falls.
 */
public final class AnimationFocus implements FocusActionHandler {
    public static final ResourceLocation ACTION = Isorropia.id("animation");
    public static final ResearchKey RESEARCH = ResearchKey.of(Isorropia.MODID, "animation");
    /** Initial action range; query supplies native block reach before AIM. */
    public static final int RANGE = 10;
    /** The upgrade that turns the golem on anything but its caller. */
    public static final FocusUpgradeId BERSERK = FocusUpgradeId.of(Isorropia.id("berserk"));
    /** The upgrade that makes the golem burst when it falls. */
    public static final FocusUpgradeId DETONATION = FocusUpgradeId.of(Isorropia.id("detonation"));

    AnimationFocus() {
    }

    /** Registers the cast with Thaumcraft's wand. Called on both sides, as the wand predicts on the client. */
    public static void register() {
        // The span comes from the focus catalog; declaring it stops a datapack from dropping it.
        FocusApi.requireParameters(ItemFocusAnimation.ID, Set.of("vis_discount", "duration"));
        WandPipelineApi.define(Isorropia.id("animation/predict"), WandCastStages.PREDICT, AnimationFocus::predict);
        FocusActionApi.register(new FocusActionSpec(ACTION, new FocusActionTarget.Focus(ItemFocusAnimation.ID),
                FocusInput.SECONDARY, Set.of(FocusTargetKind.AIR), FocusCastMode.INSTANT,
                ResearchRequirement.required(RESEARCH), RANGE, 0, ActionPresentationAudience.PREDICTED),
                new AnimationFocus(), AnimationFocus::authorize);
    }

    @Override
    public WandCastStages.Aim query(WandCastStages.Aim query) {
        //? if forge {
        return query.withRange(query.player().getBlockReach());
        //?} else {
        /*return query.withRange(query.player().blockInteractionRange());
        *///?}
    }

    private static FocusUseResult predict(FocusPredictionContext request,
            WandContinuation<FocusPredictionContext, FocusUseResult> next) {
        if (request.input() != FocusInput.SECONDARY || request.use().gesture() != FocusGesture.PRESS
                || request.use().target() != FocusTargetKind.AIR) {
            return next.proceed(request);
        }
        var aimed = request.aim(ACTION);
        if (!(aimed.use().hit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return FocusUseResult.PASS;
        }
        var level = aimed.use().player().level();
        var pos = hit.getBlockPos();
        if (!level.isLoaded(pos)) return FocusUseResult.PASS;
        var state = level.getBlockState(pos);
        if (!animatable(level, pos, state)) return FocusUseResult.PASS;
        return next.proceed(aimed.withCost(primalCost(AnimatedBlockType.of(state).visCost)));
    }

    @Override
    public FocusActionResult handle(FocusActionContext context, VisAction action) {
        if (context.gesture() != FocusGesture.PRESS) {
            return FocusActionResult.PASS;
        }
        if (!(context.authoritativeHit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return FocusActionResult.PASS;
        }
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        BlockPos pos = hit.getBlockPos();
        if (!level.isLoaded(pos)) return FocusActionResult.PASS;
        BlockState state = level.getBlockState(pos);
        if (!animatable(level, pos, state)) {
            return FocusActionResult.PASS;
        }
        // The material's cost is the same in both phases, so simulation and execution agree.
        AnimatedBlockType type = AnimatedBlockType.of(state);
        if (!context.pay(primalCost(type.visCost), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        // Simulation must not change the world, so the golem waits for EXECUTE.
        if (action != VisAction.EXECUTE) {
            return FocusActionResult.SUCCESS;
        }
        raise(context, level, player, pos, state);
        return FocusActionResult.SUCCESS;
    }

    private static dev.tc4port.thaumcraft.api.focus.action.FocusAuthorization authorize(
            dev.tc4port.thaumcraft.api.focus.action.FocusAuthorizationContext context) {
        if (!(context.authoritativeHit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return dev.tc4port.thaumcraft.api.focus.action.FocusAuthorization.allow();
        return FocusBlockAuthorization.check(context.player(), List.of(hit.getBlockPos()), hit.getBlockPos(), 0);
    }

    /** Execute the already-authorized block animation. */
    private static void raise(FocusActionContext context, ServerLevel level, ServerPlayer player, BlockPos pos,
            BlockState state) {
        EntityAnimatedBlock golem = EntitiesIS.ANIMATED_BLOCK.get().create(level);
        if (golem == null) {
            return;
        }
        golem.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        golem.loadGolem(state, lifetime(context), berserk(context), detonation(context), player);
        level.addFreshEntity(golem);
        level.removeBlock(pos, false);
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                ThaumcraftContent.sound("wand"), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Whether the block answers the call: plain, breakable matter, or one of the few solid transparent blocks. */
    private static boolean animatable(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return state.canOcclude() || whitelisted(state);
    }

    /** The solid but transparent blocks upstream let the focus animate anyway. */
    private static boolean whitelisted(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.CAKE || block == Blocks.CACTUS || block == Blocks.GLASS
                || block == Blocks.PACKED_ICE || block == Blocks.ICE || block == Blocks.COBWEB
                || block == Blocks.TNT;
    }

    /** The golem's span in ticks: upstream's base, lengthened by the extend upgrade. */
    private static int lifetime(FocusActionContext context) {
        return (int) Math.round(context.parameter("duration"));
    }

    private static boolean berserk(FocusActionContext context) {
        return context.installedUpgrades().getOrDefault(BERSERK, 0) > 0;
    }

    private static boolean detonation(FocusActionContext context) {
        return context.installedUpgrades().getOrDefault(DETONATION, 0) > 0;
    }

    /** The same number of centivis of every primal aspect, the golem's material setting how many. */
    private static VisCost primalCost(int amount) {
        Map<VisChannel, Integer> amounts = new EnumMap<>(VisChannel.class);
        for (VisChannel channel : VisChannel.values()) {
            amounts.put(channel, amount);
        }
        return VisCost.ofCentivis(amounts);
    }
}
