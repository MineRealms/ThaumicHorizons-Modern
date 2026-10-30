package fr.wind_blade.isorropia;

import com.mojang.logging.LogUtils;
import fr.wind_blade.isorropia.common.IsorropiaCreativeTabs;
import fr.wind_blade.isorropia.common.blocks.BlocksIS;
import fr.wind_blade.isorropia.common.compat.HeldWandTargets;
import fr.wind_blade.isorropia.common.config.ConfigIS;
import fr.wind_blade.isorropia.common.entities.AlchemiteDispenseBehavior;
import fr.wind_blade.isorropia.common.entities.EntitiesIS;
import fr.wind_blade.isorropia.common.focus.ContainmentFocus;
import fr.wind_blade.isorropia.common.focus.AnimationFocus;
import fr.wind_blade.isorropia.common.focus.DisintegrationFocus;
import fr.wind_blade.isorropia.common.focus.IlluminationFocus;
import fr.wind_blade.isorropia.common.focus.LiquefactionFocus;
import fr.wind_blade.isorropia.common.items.ItemsIS;
import fr.wind_blade.isorropia.common.items.crafting.RecipesIS;
import fr.wind_blade.isorropia.common.menu.MenusIS;
import fr.wind_blade.isorropia.common.network.IsorropiaNetwork;
import fr.wind_blade.isorropia.common.sounds.SoundsIS;
import fr.wind_blade.isorropia.common.tiles.TileEssentiaDynamo;
import fr.wind_blade.isorropia.common.tiles.TileVisDynamo;
import fr.wind_blade.isorropia.common.tiles.TilesIS;
import fr.wind_blade.isorropia.common.wand.CrystalWandParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
//? if forge {
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//?}
import org.slf4j.Logger;

@Mod(Isorropia.MODID)
public class Isorropia {
    public static final String MODID = "isorropia";
    public static final Logger LOGGER = LogUtils.getLogger();

    //? if forge {
    public Isorropia(FMLJavaModLoadingContext context) {
        register(context.getModEventBus());
        context.registerConfig(ModConfig.Type.COMMON, ConfigIS.SPEC);
        IsorropiaNetwork.init();
    }
    //?} else {
    /*public Isorropia(net.neoforged.bus.api.IEventBus modBus, net.neoforged.fml.ModContainer container) {
        register(modBus);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ConfigIS.SPEC);
        fr.wind_blade.isorropia.common.capabilities.LivingData.ATTACHMENTS.register(modBus);
        IsorropiaNetwork.init(modBus);
    }
    *///?}

    private void register(IEventBus modBus) {
        BlocksIS.BLOCKS.register(modBus);
        ItemsIS.ITEMS.register(modBus);
        SoundsIS.SOUNDS.register(modBus);
        TilesIS.TILES.register(modBus);
        MenusIS.MENUS.register(modBus);
        RecipesIS.SERIALIZERS.register(modBus);
        EntitiesIS.ENTITIES.register(modBus);
        modBus.addListener(EntitiesIS::onAttributes);
        IsorropiaCreativeTabs.TABS.register(modBus);
        // The wand predicts the beam on the client, so the action is registered on both sides.
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(ContainmentFocus::register));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(IlluminationFocus::register));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(LiquefactionFocus::register));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(DisintegrationFocus::register));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(AnimationFocus::register));
        // The focus profiles ship in a pack of their own so the 20740 and 20744 catalog shapes can both be served.
        modBus.addListener(fr.wind_blade.isorropia.common.focus.FocusCatalogPack::onAddPackFinders);
        // TC4R 20740: vis sources are a BlockCapability, and TC4R only registers its own node BE
        // for it. NeoForge resolves caps through this central registry (no getCapability override),
        // so the two dynamos are wired here; the essentia transport cap needs nothing - TC4R
        // blanket-registers every BE type that implements EssentiaTransport.
        //? if neoforge {
        /*modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(dev.tc4port.thaumcraft.api.node.VisCapabilities.SOURCE,
                    TilesIS.VIS_DYNAMO.get(), (be, side) -> (dev.tc4port.thaumcraft.api.node.VisSource) be);
            event.registerBlockEntity(dev.tc4port.thaumcraft.api.node.VisCapabilities.SOURCE,
                    TilesIS.ESSENTIA_DYNAMO.get(), (be, side) -> (dev.tc4port.thaumcraft.api.node.VisSource) be);
        });
        *///?}
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(AlchemiteDispenseBehavior::register));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(HeldWandTargets::register));
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CrystalWandParts::register));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
