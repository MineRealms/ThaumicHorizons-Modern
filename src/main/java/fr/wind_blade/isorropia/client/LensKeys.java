package fr.wind_blade.isorropia.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.client.hud.LensWheel;
import fr.wind_blade.isorropia.common.lenses.LensManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
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
 * The lens key. Holding it opens the wheel, which draws one ring per goggles slot and fits to whichever ring the
 * mouse is over; sneaking and tapping it empties both, so a lens can be changed without a menu at all.
 */
public final class LensKeys {
    public static final String CATEGORY = "key.categories.misc";
    public static final KeyMapping LENS = new KeyMapping("key.isorropia.lens",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private LensKeys() {
    }

    @Mod.EventBusSubscriber(modid = Isorropia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        private Register() {
        }

        // Handler names must be unique per package: the event bus merges handlers whose class simple name,
        // method and event match (see tools/check_listener_names.py).
        @SubscribeEvent
        public static void onRegisterLensKey(RegisterKeyMappingsEvent event) {
            event.register(LENS);
        }
    }

    /** Key state has no event of its own, so it is polled once a tick. */
    @Mod.EventBusSubscriber(modid = Isorropia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Poller {
        private Poller() {
        }

        //? if forge {
        @SubscribeEvent
        public static void onLensKeyTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            pollLensKeys();
        }
        //?} else {
        /*@SubscribeEvent
        public static void onLensKeyTick(ClientTickEvent.Post event) {
            pollLensKeys();
        }
        *///?}

        private static void pollLensKeys() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }
            boolean down = LENS.isDown();
            if (!down) {
                if (LensWheel.isOpen() && LensWheel.openedByKey()) {
                    LensWheel.close();
                }
                return;
            }
            if (LensWheel.isOpen()) {
                return;
            }
            if (!LensManager.isGoggles(minecraft.player.getItemBySlot(EquipmentSlot.HEAD))) {
                return;
            }
            LensWheel.open(minecraft.player.isShiftKeyDown(), true);
        }
    }
}
