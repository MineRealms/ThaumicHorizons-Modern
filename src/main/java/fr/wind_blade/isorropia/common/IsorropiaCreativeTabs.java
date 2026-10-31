package fr.wind_blade.isorropia.common;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.items.ItemsIS;
import fr.wind_blade.isorropia.common.items.misc.ItemCat;
import fr.wind_blade.isorropia.common.items.misc.ItemJelly;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;

import java.util.Comparator;
import java.util.function.Supplier;

public final class IsorropiaCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Isorropia.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("isorropia", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.isorropia"))
            .icon(() -> ItemCat.create(ItemCat.EnumCat.CHICKEN))
            .displayItems((parameters, output) -> {
                // Registry entry types differ per loader (RegistryObject vs DeferredHolder), so the loop
                // variable is left to inference; both are Suppliers. Identity stands in for the id compare.
                ItemsIS.ITEMS.getEntries().forEach(entry -> {
                    if (entry.get() == ItemsIS.LIGHT.get()) {
                        // The illumination focus uses this registered item only for native placement.
                    } else if (entry.get() == ItemsIS.CAT.get()) {
                        // The catalogue is a research-page icon (data/isorropia/thaumcraft/research),
                        // not a usable item: Thaumic Horizons hid it from the creative menu, so showing
                        // its subtypes here just added unlabelled, recipe-less clutter.
                    } else if (entry.get() == ItemsIS.JELLY.get()) {
                        output.accept(ItemsIS.JELLY.get());
                        AspectApi.definitions().keySet().stream().sorted(Comparator.comparing(AspectId::toString))
                                .forEach(aspect -> output.accept(ItemJelly.create(ItemsIS.JELLY.get(), aspect)));
                    } else {
                        output.accept(entry.get());
                    }
                });
            })
            .build());

    private IsorropiaCreativeTabs() {
    }
}
