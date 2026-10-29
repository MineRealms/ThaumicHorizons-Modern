package fr.wind_blade.isorropia.client;

import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.client.model.JellyColors;
import fr.wind_blade.isorropia.client.model.ModelJelly;
import fr.wind_blade.isorropia.client.model.ModelJellyRabbit;
import fr.wind_blade.isorropia.client.renderer.entities.RenderChickenVariant;
import fr.wind_blade.isorropia.client.renderer.entities.RenderChocolateCow;
import fr.wind_blade.isorropia.client.renderer.entities.RenderEmber;
import fr.wind_blade.isorropia.client.renderer.entities.RenderGravekeeper;
import fr.wind_blade.isorropia.client.renderer.entities.RenderGuardianPanther;
import fr.wind_blade.isorropia.client.renderer.entities.RenderHangingLabel;
import fr.wind_blade.isorropia.client.renderer.entities.RenderHellHound;
import fr.wind_blade.isorropia.client.renderer.entities.RenderJellyRabbit;
import fr.wind_blade.isorropia.client.renderer.entities.RenderOrePig;
import fr.wind_blade.isorropia.client.renderer.entities.RenderSheeder;
import fr.wind_blade.isorropia.client.renderer.entities.RenderTaintPig;
import fr.wind_blade.isorropia.client.model.MatrixCubeModel;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderDynamo;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderEtherealShard;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderJarSoul;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderNodeMonitor;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderTransductionAmplifier;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderVortexStabilizer;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderModifiedMatrix;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderSoulBeacon;
import fr.wind_blade.isorropia.client.renderer.tiles.RenderVat;
import fr.wind_blade.isorropia.common.entities.EntitiesIS;
import fr.wind_blade.isorropia.common.items.ItemFocusIllumination;
import fr.wind_blade.isorropia.common.items.ItemsIS;
import fr.wind_blade.isorropia.common.items.misc.ItemCat;
import fr.wind_blade.isorropia.common.items.misc.ItemJelly;
import fr.wind_blade.isorropia.common.tiles.TilesIS;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Isorropia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ItemsIS.CAT.get(), Isorropia.id("cat"),
                (stack, level, entity, seed) -> ItemCat.getType(stack).ordinal() / 100.0F));
        // Menu screens: registered here on 1.20.1; 1.21 only accepts them through RegisterMenuScreensEvent below.
        //? if forge {
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                fr.wind_blade.isorropia.common.menu.MenusIS.MORPHIC_FINGERS.get(),
                fr.wind_blade.isorropia.client.gui.MorphicFingersScreen::new));
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                fr.wind_blade.isorropia.common.menu.MenusIS.VIS_DYNAMO.get(),
                fr.wind_blade.isorropia.client.gui.VisDynamoScreen::new));
        event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(
                fr.wind_blade.isorropia.common.menu.MenusIS.BLOOD_INFUSER.get(),
                fr.wind_blade.isorropia.client.gui.BloodInfuserScreen::new));
        //?}
        event.enqueueWork(() -> ItemProperties.register(ItemsIS.FOCUS_ILLUMINATION.get(), Isorropia.id("colour"),
                (stack, level, entity, seed) -> ItemFocusIllumination.colour(stack) / 100.0F));
        event.enqueueWork(fr.wind_blade.isorropia.client.fx.ContainmentFx::register);
    }

    //? if neoforge {
    /*@SubscribeEvent
    public static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(fr.wind_blade.isorropia.common.menu.MenusIS.MORPHIC_FINGERS.get(),
                fr.wind_blade.isorropia.client.gui.MorphicFingersScreen::new);
        event.register(fr.wind_blade.isorropia.common.menu.MenusIS.VIS_DYNAMO.get(),
                fr.wind_blade.isorropia.client.gui.VisDynamoScreen::new);
        event.register(fr.wind_blade.isorropia.common.menu.MenusIS.BLOOD_INFUSER.get(),
                fr.wind_blade.isorropia.client.gui.BloodInfuserScreen::new);
    }
    *///?}

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModelJellyRabbit.LAYER, ModelJellyRabbit::createBodyLayer);
        event.registerLayerDefinition(ModelJelly.LAYER, ModelJelly::createBodyLayer);
        event.registerLayerDefinition(MatrixCubeModel.LAYER, MatrixCubeModel::createBodyLayer);
        event.registerLayerDefinition(fr.wind_blade.isorropia.client.model.EtherealShardModel.LAYER,
                fr.wind_blade.isorropia.client.model.EtherealShardModel::createBodyLayer);
        event.registerLayerDefinition(fr.wind_blade.isorropia.client.model.DynamoBaseModel.LAYER,
                fr.wind_blade.isorropia.client.model.DynamoBaseModel::createBodyLayer);
        event.registerLayerDefinition(fr.wind_blade.isorropia.client.model.AttenuatorModel.LAYER,
                fr.wind_blade.isorropia.client.model.AttenuatorModel::createBodyLayer);
        event.registerLayerDefinition(fr.wind_blade.isorropia.client.model.ModelSomaticBrain.LAYER,
                fr.wind_blade.isorropia.client.model.ModelSomaticBrain::createBodyLayer);
        event.registerLayerDefinition(fr.wind_blade.isorropia.client.model.RecombinatorModel.LAYER,
                fr.wind_blade.isorropia.client.model.RecombinatorModel::createBodyLayer);
        event.registerLayerDefinition(fr.wind_blade.isorropia.client.model.ModelSyringe.LAYER,
                fr.wind_blade.isorropia.client.model.ModelSyringe::createBodyLayer);
        event.registerLayerDefinition(fr.wind_blade.isorropia.client.model.BloodInfuserModel.LAYER,
                fr.wind_blade.isorropia.client.model.BloodInfuserModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntitiesIS.INCUBATED_EGG.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(EntitiesIS.TAINT_PIG.get(), RenderTaintPig::new);
        event.registerEntityRenderer(EntitiesIS.GRAVEKEEPER.get(), RenderGravekeeper::new);
        event.registerEntityRenderer(EntitiesIS.SHEEDER.get(), RenderSheeder::new);
        event.registerEntityRenderer(EntitiesIS.CHROMATIC_SHEEP.get(), SheepRenderer::new);
        event.registerEntityRenderer(EntitiesIS.SCHOLAR_CHICKEN.get(),
                context -> new RenderChickenVariant<>(context, Isorropia.id("textures/entity/scholar_chicken.png")));
        event.registerEntityRenderer(EntitiesIS.ORE_PIG.get(), RenderOrePig::new);
        event.registerEntityRenderer(EntitiesIS.JELLY_RABBIT.get(), RenderJellyRabbit::new);
        event.registerEntityRenderer(EntitiesIS.HANGING_LABEL.get(), RenderHangingLabel::new);
        event.registerEntityRenderer(EntitiesIS.CHOCOLATE_COW.get(), RenderChocolateCow::new);
        event.registerEntityRenderer(EntitiesIS.GOLDEN_CHICKEN.get(),
                context -> new RenderChickenVariant<>(context, Isorropia.id("textures/entity/golden_chicken.png")));
        event.registerEntityRenderer(EntitiesIS.GUARDIAN_PANTHER.get(), RenderGuardianPanther::new);
        event.registerEntityRenderer(EntitiesIS.NETHER_HOUND.get(), RenderHellHound::new);
        event.registerEntityRenderer(EntitiesIS.EMBER.get(), RenderEmber::new);
        event.registerEntityRenderer(EntitiesIS.ANIMATED_BLOCK.get(),
                fr.wind_blade.isorropia.client.renderer.entities.RenderAnimatedBlock::new);
        event.registerEntityRenderer(EntitiesIS.GOLEM_TH.get(),
                fr.wind_blade.isorropia.client.renderer.entities.RenderGolemTH::new);
        event.registerEntityRenderer(EntitiesIS.ALCHEMITE_PRIMED.get(),
                fr.wind_blade.isorropia.client.renderer.entities.RenderAlchemitePrimed::new);
        event.registerEntityRenderer(EntitiesIS.SYRINGE.get(),
                fr.wind_blade.isorropia.client.renderer.entities.RenderSyringe::new);
        // TH drew the thrown blast phial as its own item icon, tinted by the potion it carries.
        event.registerEntityRenderer(EntitiesIS.BLAST_PHIAL.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetupCurios(FMLClientSetupEvent event) {
        event.enqueueWork(() -> top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(
                ItemsIS.SOMATIC_BRAIN.get(), fr.wind_blade.isorropia.client.curio.SomaticBrainCurioRenderer::new));
    }

    //? if forge {
    @SubscribeEvent
    public static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(),
                "lens_wheel",
                (gui, graphics, partialTick, width, height) -> {
                    if (fr.wind_blade.isorropia.client.hud.LensWheel.isOpen()) {
                        fr.wind_blade.isorropia.client.hud.LensWheel.render(graphics, partialTick);
                    }
                });
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(),
                "lens_readouts",
                (gui, graphics, partialTick, width, height) ->
                        fr.wind_blade.isorropia.client.hud.LensHud.render(graphics, partialTick));
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(),
                "matrix_readout",
                (gui, graphics, partialTick, width, height) ->
                        fr.wind_blade.isorropia.client.hud.MatrixReadout.render(graphics));
    }
    //?} else {
    /*@SubscribeEvent
    public static void onRegisterOverlays(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, Isorropia.id("lens_wheel"),
                (graphics, deltaTracker) -> {
                    if (fr.wind_blade.isorropia.client.hud.LensWheel.isOpen()) {
                        fr.wind_blade.isorropia.client.hud.LensWheel.render(graphics,
                                deltaTracker.getGameTimeDeltaPartialTick(false));
                    }
                });
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, Isorropia.id("lens_readouts"),
                (graphics, deltaTracker) ->
                        fr.wind_blade.isorropia.client.hud.LensHud.render(graphics,
                                deltaTracker.getGameTimeDeltaPartialTick(false)));
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, Isorropia.id("matrix_readout"),
                (graphics, deltaTracker) ->
                        fr.wind_blade.isorropia.client.hud.MatrixReadout.render(graphics));
    }
    *///?}

    @SubscribeEvent
    public static void onRegisterBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TilesIS.VAT.get(), RenderVat::new);
        event.registerBlockEntityRenderer(TilesIS.MODIFIED_MATRIX.get(), RenderModifiedMatrix::new);
        event.registerBlockEntityRenderer(TilesIS.JAR_SOUL.get(), RenderJarSoul::new);
        event.registerBlockEntityRenderer(TilesIS.SOUL_BEACON.get(), RenderSoulBeacon::new);
        // Thaumic Horizons' machines: both dynamos share one renderer, each with its own ring and base.
        event.registerBlockEntityRenderer(TilesIS.VIS_DYNAMO.get(), context -> new RenderDynamo<>(context,
                RenderDynamo.texture("goldring"), RenderDynamo.texture("dynamobase")));
        event.registerBlockEntityRenderer(TilesIS.ESSENTIA_DYNAMO.get(), context -> new RenderDynamo<>(context,
                RenderDynamo.texture("thaumiumring"), RenderDynamo.texture("dynamoessentiabase")));
        event.registerBlockEntityRenderer(TilesIS.NODE_MONITOR.get(), RenderNodeMonitor::new);
        event.registerBlockEntityRenderer(TilesIS.ETHEREAL_SHARD.get(), RenderEtherealShard::new);
        event.registerBlockEntityRenderer(TilesIS.TRANSDUCTION_AMPLIFIER.get(), RenderTransductionAmplifier::new);
        event.registerBlockEntityRenderer(TilesIS.VORTEX_STABILIZER.get(), RenderVortexStabilizer::new);
        event.registerBlockEntityRenderer(TilesIS.RECOMBINATOR.get(),
                fr.wind_blade.isorropia.client.renderer.tiles.RenderRecombinator::new);
        event.registerBlockEntityRenderer(TilesIS.VORTEX.get(),
                fr.wind_blade.isorropia.client.renderer.tiles.RenderVortex::new);
        event.registerBlockEntityRenderer(TilesIS.PORTAL.get(),
                fr.wind_blade.isorropia.client.renderer.tiles.RenderPortal::new);
        event.registerBlockEntityRenderer(TilesIS.BLOOD_INFUSER.get(),
                fr.wind_blade.isorropia.client.renderer.tiles.RenderBloodInfuser::new);
    }

    /** The keystone in a receptacle, and the same keystone as an item, are tinted with the plane they belong to. */
    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null
                    && level.getBlockEntity(pos) instanceof fr.wind_blade.isorropia.common.tiles.TileSlot slot
                    && slot.hasKeystone()) {
                return 0xFF000000 | slot.planeColour();
            }
            return -1;
        }, fr.wind_blade.isorropia.common.blocks.BlocksIS.SLOT.get());
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | JellyColors.of(ItemJelly.getAspect(stack)) : -1,
                ItemsIS.JELLY.get());
        event.register((stack, tintIndex) -> tintIndex == 0
                ? 0xFF000000 | fr.wind_blade.isorropia.common.items.ItemKeystone.boundColour(stack) : -1,
                ItemsIS.KEYSTONE.get());
    }
}
