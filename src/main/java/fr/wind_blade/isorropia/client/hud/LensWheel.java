package fr.wind_blade.isorropia.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.wind_blade.isorropia.common.lenses.Lens;
import fr.wind_blade.isorropia.common.lenses.Lenses;
import fr.wind_blade.isorropia.common.network.IsorropiaNetwork;
import fr.wind_blade.isorropia.common.network.LensChangePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The lens wheel: hold the lens key with goggles on and the lenses carried in the inventory appear as two rings,
 * one either side of the crosshair, one per eye. Which ring the mouse is over is the slot the pick goes to, so
 * both of the goggles' slots are reachable, as they were upstream. Sneaking and tapping the key empties both.
 */
public final class LensWheel {
    private static final int RADIUS = 44;
    private static final int SLICE_SIZE = 22;
    /** Each ring sits this far from the crosshair, so the two eyes read as two separate wheels. */
    private static final int RING_OFFSET = 64;
    /** The wheel grows into place rather than appearing, as upstream's did. */
    private static final float GROWTH_STEP = 0.15F;
    private static final float MAX_SCALE = 1.3F;

    private static boolean open;
    private static boolean openedByKey;
    private static boolean removing;
    private static float scale;
    private static List<Slice> slices = List.of();
    private static Lens.Slot hoveredSlot = Lens.Slot.LEFT;
    private static int selected = -1;
    /** Set once the overlay has drawn, which the probes read to tell a hidden wheel from a broken one. */
    private static boolean hasDrawn;

    private LensWheel() {
    }

    private record Slice(ResourceLocation lens, int inventorySlot, int colour) {
    }

    /** Whether the wheel is up. */
    public static boolean isOpen() {
        return open;
    }

    /** True when the key is what opened it, which is when releasing the key should close it again. */
    public static boolean openedByKey() {
        return openedByKey;
    }

    public static boolean hasDrawn() {
        return hasDrawn;
    }

    /** The slot the mouse is over, which is the slot a pick would go to. */
    public static Lens.Slot hoveredSlot() {
        return hoveredSlot;
    }

    public static void open(boolean asRemoval, boolean byKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (open) {
            return;
        }
        open = true;
        openedByKey = byKey;
        removing = asRemoval;
        scale = 0.0F;
        selected = -1;
        List<Slice> found = new ArrayList<>();
        for (int index = 0; index < minecraft.player.getInventory().getContainerSize(); index++) {
            ItemStack stack = minecraft.player.getInventory().getItem(index);
            for (Lens lens : Lenses.all().values()) {
                ItemStack representative = fr.wind_blade.isorropia.common.items.ItemsIS.forLens(lens.id()).orElse(ItemStack.EMPTY);
                if (!representative.isEmpty() && representative.is(stack.getItem())) {
                    found.add(new Slice(lens.id(), index, lens.colour()));
                }
            }
        }
        slices = List.copyOf(found);
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    /** Closes the wheel and sends whatever was chosen. */
    public static void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!open) {
            return;
        }
        open = false;
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        } else {
            minecraft.mouseHandler.grabMouse();
        }
        if (removing) {
            // Upstream emptied both eyes at once, so neither slot is left holding a lens the wearer meant to shed.
            IsorropiaNetwork.sendToServer(LensChangePacket.removing(Lens.Slot.LEFT));
            IsorropiaNetwork.sendToServer(LensChangePacket.removing(Lens.Slot.RIGHT));
        } else if (selected >= 0 && selected < slices.size()) {
            Slice slice = slices.get(selected);
            IsorropiaNetwork.sendToServer(LensChangePacket.fitting(hoveredSlot, slice.inventorySlot(), slice.lens()));
        }
    }

    /** Draws the wheel and follows the mouse to pick a ring and a slice. */
    public static void render(GuiGraphics graphics, float partialTick) {
        scale = Math.min(MAX_SCALE, scale + GROWTH_STEP);
        Minecraft minecraft = Minecraft.getInstance();
        int centreX = graphics.guiWidth() / 2;
        int centreY = graphics.guiHeight() / 2;
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        int screenMouseX = (int) (mouseX * graphics.guiWidth() / minecraft.getWindow().getScreenWidth());
        int screenMouseY = (int) (mouseY * graphics.guiHeight() / minecraft.getWindow().getScreenHeight());
        hoveredSlot = screenMouseX < centreX ? Lens.Slot.LEFT : Lens.Slot.RIGHT;
        int ringX = centreX + ringOffset(hoveredSlot);
        int hovered = sliceAt(screenMouseX - ringX, screenMouseY - centreY);
        selected = hovered;

        hasDrawn = true;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(centreX, centreY, 0.0F);
        poseStack.scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        if (!removing) {
            for (Lens.Slot side : Lens.Slot.values()) {
                int originX = ringOffset(side);
                for (int i = 0; i < slices.size(); i++) {
                    double sliceAngle = Math.toRadians(i * (360.0 / slices.size()) - 90.0);
                    int x = originX + (int) Math.round(Math.cos(sliceAngle) * RADIUS);
                    int y = (int) Math.round(Math.sin(sliceAngle) * RADIUS);
                    // The ring's backing sits under the icons, so it has to move with them.
                    graphics.fill(x - SLICE_SIZE / 2, y - SLICE_SIZE / 2, x + SLICE_SIZE / 2, y + SLICE_SIZE / 2,
                            0x90100C18);
                }
            }
            for (Lens.Slot side : Lens.Slot.values()) {
                int originX = ringOffset(side);
                for (int i = 0; i < slices.size(); i++) {
                    double sliceAngle = Math.toRadians(i * (360.0 / slices.size()) - 90.0);
                    int x = originX + (int) Math.round(Math.cos(sliceAngle) * RADIUS);
                    int y = (int) Math.round(Math.sin(sliceAngle) * RADIUS);
                    ItemStack icon = fr.wind_blade.isorropia.common.items.ItemsIS.forLens(slices.get(i).lens())
                            .orElse(ItemStack.EMPTY);
                    if (!icon.isEmpty()) {
                        graphics.renderItem(icon, x - 8, y - 8);
                    }
                    if (side == hoveredSlot && i == hovered) {
                        graphics.fill(x - 10, y - 10, x + 10, y + 10,
                                0x40FFFFFF | slices.get(i).colour() & 0xFFFFFF);
                    }
                }
            }
        }
        poseStack.popPose();

        int colour = removing ? 0xFF5555 : 0xFFFFFF;
        graphics.drawCenteredString(minecraft.font,
                net.minecraft.network.chat.Component.translatable(removing ? "isorropia.lens.remove" : "isorropia.lens.choose"),
                centreX, centreY - 4, colour);
    }

    /** Which side of the crosshair a slot's ring hangs on. */
    private static int ringOffset(Lens.Slot slot) {
        return slot == Lens.Slot.LEFT ? -RING_OFFSET : RING_OFFSET;
    }

    /**
     * The slice the mouse points at around one ring, or -1 when it is not on the ring at all. A slice is chosen by
     * angle, so the whole sector is a target rather than the icon alone.
     */
    private static int sliceAt(int dx, int dy) {
        if (slices.isEmpty()) {
            return -1;
        }
        if (Math.sqrt((double) dx * dx + (double) dy * dy) > RADIUS + SLICE_SIZE) {
            return -1;
        }
        double step = 360.0 / slices.size();
        double degrees = (Math.toDegrees(Math.atan2(dy, dx)) + 360.0 + 90.0) % 360.0;
        return Math.min(slices.size() - 1, (int) Math.floor(degrees / step));
    }
}
