package fr.wind_blade.isorropia.common.compat;

import dev.tc4port.thaumcraft.api.flux.FluxApi;
import dev.tc4port.thaumcraft.registry.TCEffects;
import dev.tc4port.thaumcraft.registry.TCFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;

/**
 * Shared flux, status-effect and stabilizer lookups used by the addon.
 */
public final class ThaumBridge {
    /** The tag the infusion matrix itself reads to recognize stabilizers. */
    public static final TagKey<Block> STABILIZERS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "infusion_stabilizers"));

    private ThaumBridge() {
    }

    /** Spills one to nine quanta of flux, goo or gas, at a position. */
    public static void releaseFlux(ServerLevel level, BlockPos pos, int quanta) {
        if (level.isEmptyBlock(pos)) {
            FluxApi.place(level, pos, level.random.nextBoolean() ? TCFluids.FLUX_GOO.get() : TCFluids.FLUX_GAS.get(),
                    Math.max(1, Math.min(quanta, 8)), Block.UPDATE_ALL);
        } else {
            FluxApi.add(level, pos, Math.max(1, Math.min(quanta, 8)));
        }
    }

    // 1.21's MobEffectInstance takes a Holder; the TC4R registry entry is a DeferredHolder, which
    // is a Holder, so the neo side hands it over directly.
    //? if forge {
    public static MobEffect fluxTaint() {
        return TCEffects.FLUX_TAINT.get();
    }

    public static MobEffect visExhaust() {
        return TCEffects.VIS_EXHAUST.get();
    }
    //?} else {
    /*public static net.minecraft.core.Holder<MobEffect> fluxTaint() {
        return (net.minecraft.core.Holder<MobEffect>) TCEffects.FLUX_TAINT;
    }

    public static net.minecraft.core.Holder<MobEffect> visExhaust() {
        return (net.minecraft.core.Holder<MobEffect>) TCEffects.VIS_EXHAUST;
    }
    *///?}
}
