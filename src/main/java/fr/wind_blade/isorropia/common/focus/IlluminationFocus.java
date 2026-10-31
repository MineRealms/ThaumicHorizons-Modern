package fr.wind_blade.isorropia.common.focus;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusUpgradeId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
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
import fr.wind_blade.isorropia.common.blocks.BlockLight;
import fr.wind_blade.isorropia.common.blocks.BlocksIS;
import fr.wind_blade.isorropia.common.items.ItemFocusIllumination;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Set;

/**
 * The illumination focus, as Thaumic Horizons built it for Thaumcraft 4: a wand aimed at a block face leaves a
 * conjured mote of light hovering in the air beside it. The mote is weightless and replaceable, so it never
 * gets in the way of building, and the wand's solar upgrade turns it into a mote that burns the undead.
 *
 * <p>The mote's colour is the focus' own, kept as a focus property so the item and the block agree on it.
 */
public final class IlluminationFocus {
    public static final ResourceLocation ACTION = Isorropia.id("illumination");
    public static final ResearchKey RESEARCH = ResearchKey.of(Isorropia.MODID, "illumination");
    /** The mote is left on the face the wand is pointed at, within this reach. */
    public static final int RANGE = 10;
    /** The upgrade that turns the mote solar, burning the undead that touch it. */
    public static final FocusUpgradeId SOLAR = FocusUpgradeId.of(Isorropia.id("solar"));
    /** The focus property holding the mote's colour, an index into {@link BlockLight#COLOURS}. */
    public static final ResourceLocation COLOUR = Isorropia.id("colour");
    /** Upstream conjured the focus in the twelfth colour; absent a property, the port does the same. */
    public static final int DEFAULT_COLOUR = 11;

    private IlluminationFocus() {
    }

    /** Registers the cast with Thaumcraft's wand. Called on both sides, as the wand predicts on the client. */
    public static void register() {
        // The rate comes from the focus catalog; declaring it stops a datapack from dropping it.
        FocusApi.requireParameters(ItemFocusIllumination.ID, Set.of("vis_discount"));
        WandPipelineApi.define(Isorropia.id("illumination/predict"), WandCastStages.PREDICT, IlluminationFocus::predict);
        FocusActionApi.register(new FocusActionSpec(ACTION, new FocusActionTarget.Focus(ItemFocusIllumination.ID),
                FocusInput.SECONDARY, Set.of(FocusTargetKind.BLOCK), FocusCastMode.INSTANT,
                ResearchRequirement.required(RESEARCH), RANGE, 0, ActionPresentationAudience.PREDICTED),
                IlluminationFocus::handle);
    }

    private static FocusUseResult predict(FocusPredictionContext request,
            WandContinuation<FocusPredictionContext, FocusUseResult> next) {
        if (request.input() != FocusInput.SECONDARY || request.use().gesture() != FocusGesture.PRESS
                || request.use().target() != dev.tc4port.thaumcraft.api.focus.action.FocusTargetKind.BLOCK) {
            return next.proceed(request);
        }
        var aimed = request.aim(ACTION);
        if (!(aimed.use().hit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !available(aimed.use().player().level(), hit)) return FocusUseResult.PASS;
        return next.proceed(aimed.configuredCost().map(aimed::withCost).orElseGet(aimed::withoutCost));
    }

    private static boolean available(Level level, BlockHitResult hit) {
        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        return !level.isOutsideBuildHeight(target) && level.hasChunkAt(target)
                && level.getBlockState(target).canBeReplaced();
    }

    private static FocusActionResult handle(FocusActionContext context, VisAction action) {
        if (context.gesture() != FocusGesture.PRESS) {
            return FocusActionResult.PASS;
        }
        if (!(context.authoritativeHit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !available(context.player().level(), hit)) return FocusActionResult.PASS;
        if (!context.pay(context.configuredCost(), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        // Simulation must not change the world, so the mote waits for EXECUTE.
        if (action != VisAction.EXECUTE) {
            return FocusActionResult.SUCCESS;
        }
        ServerLevel level = context.player().serverLevel();
        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        BlockState mote = BlocksIS.LIGHT.get().defaultBlockState()
                .setValue(BlockLight.COLOUR, colour(context))
                .setValue(BlockLight.SOLAR, solar(context));
        level.setBlock(target, mote, 3);
        level.playSound(null, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                ThaumcraftContent.sound("wand"), SoundSource.PLAYERS, 1.0F, 1.0F);
        return FocusActionResult.SUCCESS;
    }

    /** The colour the focus was conjured in, bounded to the palette and falling back to upstream's own. */
    private static int colour(FocusActionContext context) {
        String value = context.installedFocus().properties().get(COLOUR);
        if (value == null) {
            return DEFAULT_COLOUR;
        }
        try {
            return Math.max(0, Math.min(BlockLight.COLOURS.length - 1, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return DEFAULT_COLOUR;
        }
    }

    private static boolean solar(FocusActionContext context) {
        return context.installedUpgrades().getOrDefault(SOLAR, 0) > 0;
    }
}
