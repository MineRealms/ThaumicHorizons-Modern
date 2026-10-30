package fr.wind_blade.isorropia.common.compat;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.focus.action.FocusTargetKind;
import dev.tc4port.thaumcraft.api.focus.behavior.FocusUseContext;
import dev.tc4port.thaumcraft.api.focus.behavior.FocusUseResult;
import dev.tc4port.thaumcraft.api.research.ResearchApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.interaction.WandFormationApi;
import dev.tc4port.thaumcraft.api.wand.pipeline.WandContinuation;
import dev.tc4port.thaumcraft.api.wand.pipeline.WandPipelineApi;
import dev.tc4port.thaumcraft.api.wand.pipeline.WandUseStages;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.capabilities.LivingData;
import fr.wind_blade.isorropia.common.infusion.CreatureAbilities;
import fr.wind_blade.isorropia.common.infusion.SoulJar;
import fr.wind_blade.isorropia.common.vat.VatStructure;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Immediate local admission; structure conversion and capture remain server operations. */
public final class WandInteractions {
    private static final ResearchKey CURATIVE_VAT = ResearchKey.of(Isorropia.MODID, "curativevat");
    private static final Map<VisChannel, Integer> VAT_COST = Map.of(
            VisChannel.AQUA, 50, VisChannel.TERRA, 50, VisChannel.ORDO, 50);

    private WandInteractions() { }

    public static void register() {
        WandPipelineApi.aroundAll(Isorropia.id("machines"), WandUseStages.WANDABLE, -200, WandInteractions::machine);
        WandPipelineApi.aroundAll(Isorropia.id("curative_vat"), WandUseStages.BEFORE_FORMATION,
                -200, WandInteractions::vat);
        WandPipelineApi.aroundAll(Isorropia.id("portability"), WandUseStages.BEFORE_ENTITY,
                -200, WandInteractions::capture);
    }

    private static FocusUseResult machine(FocusUseContext use,
            WandContinuation<FocusUseContext, FocusUseResult> next) {
        if (use.target() != FocusTargetKind.BLOCK || !(use.hit() instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || WandApi.view(use.carrier()).isEmpty()) return next.proceed(use);
        Level level = use.player().level();
        var state = level.getBlockState(hit.getBlockPos());
        var block = state.getBlock();
        net.minecraft.world.InteractionResult result;
        if (block instanceof fr.wind_blade.isorropia.common.blocks.BlockSlot slot)
            result = slot.useWand(state, level, hit.getBlockPos(), use.player(), use.hand(), hit, use.isClientSide());
        else if (block instanceof fr.wind_blade.isorropia.common.blocks.BlockModifiedMatrix matrix)
            result = matrix.useWand(state, level, hit.getBlockPos(), use.player(), use.hand(), hit, use.isClientSide());
        else if (block instanceof fr.wind_blade.isorropia.common.blocks.BlockVortexStabilizer stabilizer)
            result = stabilizer.useWand(state, level, hit.getBlockPos(), use.player(), use.hand(), hit, use.isClientSide());
        else if (block instanceof fr.wind_blade.isorropia.common.blocks.BlockJarSoul jar)
            result = jar.useWand(state, level, hit.getBlockPos(), use.player(), use.hand(), hit, use.isClientSide());
        else if (block instanceof fr.wind_blade.isorropia.common.blocks.BlockVortex vortex)
            result = vortex.useWand(state, level, hit.getBlockPos(), use.player(), use.hand(), hit, use.isClientSide());
        else return next.proceed(use);
        return result == net.minecraft.world.InteractionResult.PASS ? next.proceed(use)
                : use.isClientSide() ? FocusUseResult.SUCCESS : FocusUseResult.CONSUME;
    }

    /** Runs before entity-owned trading, riding and other native interactions. */
    public static net.minecraft.world.InteractionResult beforeEntityUse(Player player, InteractionHand hand,
            net.minecraft.world.entity.Entity target) {
        return target instanceof LivingEntity living
                ? dev.tc4port.thaumcraft.api.wand.interaction.WandUseApi.beforeEntityInteraction(player, hand, living)
                : net.minecraft.world.InteractionResult.PASS;
    }

    private static FocusUseResult vat(FocusUseContext use,
            WandContinuation<FocusUseContext, FocusUseResult> next) {
        if (use.hand() != InteractionHand.MAIN_HAND || use.target() != FocusTargetKind.BLOCK
                || !(use.hit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || WandApi.view(use.carrier()).isEmpty() || !ResearchApi.isComplete(use.player(), CURATIVE_VAT)) {
            return next.proceed(use);
        }
        Level level = use.player().level();
        if (VatStructure.isVatBlock(level.getBlockState(hit.getBlockPos()))) return next.proceed(use);
        BlockPos master = VatStructure.findUnformed(level, hit.getBlockPos()).orElse(null);
        if (master == null) return next.proceed(use);
        var cost = WandApi.craftingCost(use.carrier(), use.player(), VAT_COST);
        if (use.isClientSide()) {
            return ThaumcraftApiHelper.consumeExactVisFromWand(use.carrier(), use.player(), cost,
                    VisAction.SIMULATE).affordable()
                    ? FocusUseResult.SUCCESS : refusedFormation();
        }
        boolean formed = WandFormationApi.convert(new UseOnContext(use.player(), use.hand(), hit), cost,
                () -> VatStructure.matchesUnformed(level, master) && VatStructure.form(level, master), () -> { });
        return formed ? FocusUseResult.SUCCESS : refusedFormation();
    }

    private static FocusUseResult refusedFormation() {
        return FocusUseResult.FAIL.withFeedback(FocusUseResult.Feedback.SWING);
    }

    private static FocusUseResult capture(FocusUseContext use,
            WandContinuation<FocusUseContext, FocusUseResult> next) {
        if (use.hand() != InteractionHand.MAIN_HAND || use.target() != FocusTargetKind.ENTITY
                || !(use.hit() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)
                || target instanceof Player || !target.isAlive() || WandApi.view(use.carrier()).isEmpty()) {
            return next.proceed(use);
        }
        LivingData data = LivingData.get(target).orElse(null);
        if (data == null || !data.hasInfusion(CreatureAbilities.PORTABILITY)) return next.proceed(use);
        if (!SoulJar.hasEmptyJar(use.player())) {
            if (!use.isClientSide()) use.player().displayClientMessage(
                    Component.translatable("isorropia.containment.fail"), true);
            return next.proceed(use);
        }
        if (use.isClientSide()) return FocusUseResult.SUCCESS;
        return CreatureAbilities.jarInto(use.player(), target) ? FocusUseResult.SUCCESS : FocusUseResult.FAIL;
    }
}
