package fr.wind_blade.isorropia.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.network.IsorropiaNetwork;
import fr.wind_blade.isorropia.common.network.ToggleInfusionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
//? if forge {
import net.minecraftforge.event.TickEvent;
//?} else {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
*///?}
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * TH's two infusion switches: keyC turns the spider climb off and on, keyX does the same for the chameleon
 * skin. Upstream left both unbound; the defaults here are C and X so they are usable out of the box.
 */
public final class InfusionKeys {
    public static final KeyMapping CLIMB = new KeyMapping("key.isorropia.climb",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, LensKeys.CATEGORY);
    public static final KeyMapping CHAMELEON = new KeyMapping("key.isorropia.chameleon",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, LensKeys.CATEGORY);

    private InfusionKeys() {
    }

    @Mod.EventBusSubscriber(modid = Isorropia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        private Register() {
        }

        @SubscribeEvent
        public static void onRegisterInfusionKeys(RegisterKeyMappingsEvent event) {
            event.register(CLIMB);
            event.register(CHAMELEON);
        }
    }

    @Mod.EventBusSubscriber(modid = Isorropia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Poller {
        private Poller() {
        }

        //? if forge {
        @SubscribeEvent
        public static void onInfusionKeyTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            poll();
        }
        //?} else {
        /*@SubscribeEvent
        public static void onInfusionKeyTick(ClientTickEvent.Post event) {
            poll();
        }
        *///?}

        private static void poll() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }
            if (CLIMB.consumeClick()) {
                IsorropiaNetwork.sendToServer(new ToggleInfusionPacket(ToggleInfusionPacket.CLIMB));
            }
            if (CHAMELEON.consumeClick()) {
                IsorropiaNetwork.sendToServer(new ToggleInfusionPacket(ToggleInfusionPacket.CHAMELEON));
            }
        }
    }
}
