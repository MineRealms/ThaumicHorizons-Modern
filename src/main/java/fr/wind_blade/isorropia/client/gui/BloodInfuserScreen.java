package fr.wind_blade.isorropia.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.client.platform.QuadBatch;
import fr.wind_blade.isorropia.common.menu.BloodInfuserMenu;
import fr.wind_blade.isorropia.common.tiles.TileBloodInfuser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TH's {@code GuiBloodInfuser}: the dial's eight units top left, the wizard's discovered aspects in a
 * scrollable two-row grid, single-shot and automatic dots, and two drawers that slide out on the right —
 * the injection's predicted effects, and the effect weights of whichever aspect the cursor rests on.
 */
public class BloodInfuserScreen extends AbstractContainerScreen<BloodInfuserMenu> {
    private static final ResourceLocation TEXTURE = Isorropia.id("textures/gui/guibloodinfuser.png");
    private static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "textures/aspects/_unknown.png");
    private static final ResourceLocation PARTICLES = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "textures/misc/particles.png");
    private static final int FLASH_FRAMES = 8;
    private static final float FLASH_CELL = 0.0624375F;

    private List<AspectId> known = List.of();
    /** TH cached the predicted injection on open and after each dial click. */
    private List<MobEffectInstance> cachedEffects = List.of();
    /** Mirrors of the server tile; kept even if the client block entity is briefly unavailable. */
    private int cachedMode;
    private Map<AspectId, Integer> cachedDial = Map.of();
    private Map<AspectId, Integer> cachedAcquired = Map.of();
    @Nullable
    private AspectId mousedOver;
    @Nullable
    private Map<MobEffect, Integer> mousedEffects;
    private int offset;
    private int flashTimer;
    private int flashX;
    private int flashY;
    private int flashColour;
    /** Drawer slide, in pixels: TH walked these two pixels a frame. */
    private int topOut = 2;
    private int bottomOut = 2;

    public BloodInfuserScreen(BloodInfuserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 219;
    }

    @Nullable
    private TileBloodInfuser tile() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        BlockEntity entity = this.minecraft.level.getBlockEntity(this.menu.pos());
        return entity instanceof TileBloodInfuser infuser ? infuser : null;
    }

    @Override
    protected void init() {
        super.init();
        this.refresh();
    }

    private void refresh() {
        // The owner-synced knowledge state can briefly be absent (a respawned player before its sync lands);
        // TH's own screen degraded to an empty dial there, so we show an empty grid rather than crash the menu.
        try {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.known = BloodInfuserMenu.knownSorted(this.minecraft.player);
            }
        } catch (IllegalStateException absent) {
            this.known = List.of();
        }
        TileBloodInfuser infuser = this.tile();
        if (infuser != null) {
            this.cachedMode = infuser.getMode();
            this.cachedDial = new LinkedHashMap<>(infuser.dialled());
            this.cachedAcquired = new LinkedHashMap<>(infuser.acquired());
            this.cachedEffects = infuser.getCurrentEffects();
        }
    }

    /** TH's flat dial: one entry per dialled unit, in dial order. */
    private List<AspectId> dialFlat() {
        List<AspectId> flat = new ArrayList<>();
        for (Map.Entry<AspectId, Integer> entry : this.cachedDial.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                flat.add(entry.getKey());
            }
        }
        return flat;
    }

    private boolean canScrollLeft() {
        return this.offset > 0;
    }

    private boolean canScrollRight() {
        return this.offset + 16 < this.known.size();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21 draws (and blurs) the background inside Screen#render; a second call throws.
        //? if forge {
        renderBackground(graphics);
        //?}
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        this.drawEssentiaSelected(graphics);
        this.drawAspectList(graphics);
        RenderSystem.enableBlend();
        if (this.canScrollLeft()) {
            graphics.blit(TEXTURE, 111, 75, 177, 136, 24, 8);
        } else {
            graphics.blit(TEXTURE, 111, 75, 177, 144, 24, 8);
        }
        if (this.canScrollRight()) {
            graphics.blit(TEXTURE, 135, 75, 201, 136, 24, 8);
        } else {
            graphics.blit(TEXTURE, 135, 75, 201, 144, 24, 8);
        }
        if (this.cachedMode == 1) {
            graphics.blit(TEXTURE, 38, 57, 178, 130, 6, 6);
        } else if (this.cachedMode == 2) {
            graphics.blit(TEXTURE, 38, 70, 178, 130, 6, 6);
        }
        RenderSystem.disableBlend();
        this.trackHover(mouseX, mouseY);
        this.drawWeightsDrawer(graphics);
        this.drawEffectsDrawer(graphics);
        if (this.flashTimer > 0) {
            this.flashTimer--;
            this.drawFlash(graphics);
        }
    }

    /** TH's {@code drawEssentiaSelected}: dial order, two per row; units not yet soaked in blink. */
    private void drawEssentiaSelected(GuiGraphics graphics) {
        int flat = 0;
        for (Map.Entry<AspectId, Integer> entry : this.cachedDial.entrySet()) {
            AspectId aspect = entry.getKey();
            for (int unit = 1; unit <= entry.getValue() && flat < 8; unit++, flat++) {
                int x = 54 + (flat % 2 == 1 ? 17 : 0);
                int y = 12 + 17 * (flat / 2);
                boolean pending = this.cachedAcquired.getOrDefault(aspect, 0) < unit;
                if (this.known.contains(aspect)) {
                    ThaumcraftClientRenderApi.drawAspect(graphics, aspect, x, y, pending ? 0.4F : 1.0F);
                } else {
                    int tint = pending ? 0x404040
                            : (AspectApi.get(aspect) == null ? 0x404040 : AspectApi.get(aspect).color());
                    drawQuestionMark(graphics, x, y, tint);
                }
            }
        }
    }

    private static void drawQuestionMark(GuiGraphics graphics, int x, int y, int colour) {
        float red = (colour >> 16 & 0xFF) / 255.0F;
        float green = (colour >> 8 & 0xFF) / 255.0F;
        float blue = (colour & 0xFF) / 255.0F;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(red, green, blue, 0.8F);
        graphics.blit(UNKNOWN, x, y, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.flush();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private void drawAspectList(GuiGraphics graphics) {
        for (int i = 0; i < 16 && this.offset + i < this.known.size(); ++i) {
            AspectId aspect = this.known.get(this.offset + i);
            ThaumcraftClientRenderApi.drawAspect(graphics, aspect, 14 + 18 * (i / 2), 83 + ((i % 2 == 0) ? 18 : 0), 1.0F);
        }
    }

    /** TH's hover rules: the grid picks a new aspect, the arrow strip clears the pick, elsewhere it sticks. */
    private void trackHover(int mouseX, int mouseY) {
        int x = mouseX - 15;
        int y = mouseY - 83;
        if (x >= 0 && y >= 0 && x <= 144 && y <= 34) {
            for (int i = 0; i < 16 && this.offset + i < this.known.size(); ++i) {
                int cx = mouseX - (14 + 18 * (i / 2));
                int cy = mouseY - (83 + ((i % 2 == 0) ? 18 : 0));
                if (cx >= 0 && cy >= 0 && cx <= 16 && cy <= 16) {
                    AspectId aspect = this.known.get(this.offset + i);
                    if (!aspect.equals(this.mousedOver)) {
                        this.mousedOver = aspect;
                        Map<MobEffect, Integer> weights = TileBloodInfuser.effectsFor(aspect);
                        this.mousedEffects = weights == null ? null : new HashMap<>(weights);
                    }
                    return;
                }
            }
        }
        x = mouseX - 110;
        y = mouseY - 74;
        if (x >= 0 && y >= 0 && x <= 49 && y <= 9) {
            this.mousedOver = null;
        }
    }

    /** TH's bottom drawer: the hovered aspect's effect affinities and their raw weights. */
    private void drawWeightsDrawer(GuiGraphics graphics) {
        if (this.mousedOver == null) {
            if (this.bottomOut > 2) {
                this.bottomOut -= 2;
            }
        } else if (this.bottomOut < 38) {
            this.bottomOut += 2;
        }
        RenderSystem.enableBlend();
        graphics.blit(TEXTURE, 171, 69, 215 - this.bottomOut, 194, this.bottomOut, 59);
        RenderSystem.disableBlend();
        if (this.bottomOut == 38 && this.mousedEffects != null) {
            int j = 0;
            int k = 0;
            for (Map.Entry<MobEffect, Integer> weight : this.mousedEffects.entrySet()) {
                //? if forge {
                this.drawEffectIcon(graphics, weight.getKey(), j * 18 + 171, k * 18 + 72);
                //?} else {
                /*this.drawEffectIcon(graphics, net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(weight.getKey()), j * 18 + 171, k * 18 + 72);
                *///?}
                graphics.drawString(this.font, "" + weight.getValue(), j * 18 + 171, k * 18 + 72, 0xFF808080, false);
                if (j == 0) {
                    j = 1;
                } else {
                    j = 0;
                    ++k;
                }
            }
        }
    }

    /** TH's top drawer: the injection the current dial will bottle, with each effect's amplifier plus one. */
    private void drawEffectsDrawer(GuiGraphics graphics) {
        if (!this.cachedEffects.isEmpty()) {
            if (this.topOut < 38) {
                this.topOut += 2;
            }
        } else if (this.topOut > 2) {
            this.topOut -= 2;
        }
        RenderSystem.enableBlend();
        graphics.blit(TEXTURE, 171, 25, 215 - this.topOut, 152, this.topOut, 41);
        RenderSystem.disableBlend();
        if (this.topOut == 38) {
            int j = 0;
            int k = 0;
            for (MobEffectInstance effect : this.cachedEffects) {
                this.drawEffectIcon(graphics, effect.getEffect(), j * 18 + 171, k * 18 + 28);
                graphics.drawString(this.font, "" + (effect.getAmplifier() + 1), j * 18 + 171, k * 18 + 28,
                        0xFF808080, false);
                if (j == 0) {
                    j = 1;
                } else {
                    j = 0;
                    ++k;
                }
            }
        }
    }

    //? if forge {
    private void drawEffectIcon(GuiGraphics graphics, MobEffect effect, int x, int y) {
    //?} else {
    /*private void drawEffectIcon(GuiGraphics graphics, net.minecraft.core.Holder<MobEffect> effect, int x, int y) {
    *///?}
        TextureAtlasSprite sprite = this.minecraft.getMobEffectTextures().get(effect);
        graphics.blit(x, y, 0, 18, 18, sprite);
    }

    private void drawFlash(GuiGraphics graphics) {
        float red = (this.flashColour >> 16 & 255) / 255.0F;
        float green = (this.flashColour >> 8 & 255) / 255.0F;
        float blue = (this.flashColour & 255) / 255.0F;
        graphics.flush();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PARTICLES);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = graphics.pose().last().pose();
        float x = this.flashX;
        float y = this.flashY;
        QuadBatch quads = QuadBatch.positionTexColor(pose);
        quads.vertex(x, y + 16.0F, 0.0F, FLASH_CELL + this.flashTimer / 8.0F + 0.5F, 0.5F + FLASH_CELL, red, green, blue, 1.0F);
        quads.vertex(x + 16.0F, y + 16.0F, 0.0F, FLASH_CELL + this.flashTimer / 8.0F + 0.5F, 0.5F, red, green, blue, 1.0F);
        quads.vertex(x + 16.0F, y, 0.0F, this.flashTimer / 8.0F + 0.5F, 0.5F, red, green, blue, 1.0F);
        quads.vertex(x, y, 0.0F, this.flashTimer / 8.0F + 0.5F, 0.5F + FLASH_CELL, red, green, blue, 1.0F);
        quads.draw();
        RenderSystem.disableBlend();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The dial is checked before the container screen sees the click: vanilla consumes clicks in some
        // empty areas, and TH's own screen always ran its widgets regardless.
        int x = (int) mouseX - this.leftPos;
        int y = (int) mouseY - this.topPos;
        if (this.clickDial(x, y, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** TH's widget hit tests; coordinates are GUI-relative. */
    private boolean clickDial(int x, int y, double mouseX, double mouseY) {
        int clickX = x - 38;
        int clickY = y - 57;
        if (clickX >= 0 && clickY >= 0 && clickX <= 6 && clickY <= 6) {
            // TH: the single-shot dot toggles between idle and one treatment.
            this.sendButton(this.cachedMode == 1 ? 0 : 1);
            return true;
        }
        clickY = y - 70;
        if (clickX >= 0 && clickY >= 0 && clickX <= 6 && clickY <= 6) {
            this.sendButton(this.cachedMode == 2 ? 0 : 2);
            return true;
        }
        clickX = x - 111;
        clickY = y - 75;
        if (this.canScrollLeft() && clickX >= 0 && clickY >= 0 && clickX <= 24 && clickY <= 8) {
            this.offset -= 2;
            return true;
        }
        clickX = x - 135;
        if (this.canScrollRight() && clickX >= 0 && clickY >= 0 && clickX <= 24 && clickY <= 8) {
            this.offset += 2;
            return true;
        }
        List<AspectId> flat = this.dialFlat();
        for (int i = 0; i < 16; ++i) {
            clickX = x - (14 + 18 * (i / 2));
            clickY = y - (83 + ((i % 2 == 0) ? 18 : 0));
            if (this.offset + i < this.known.size() && flat.size() < TileBloodInfuser.MAX_SELECTED
                    && clickX >= 0 && clickX <= 16 && clickY >= 0 && clickY <= 16) {
                AspectId aspect = this.known.get(this.offset + i);
                this.sendButton(-(i + this.offset) - 1);
                this.flash(aspect, mouseX, mouseY);
                return true;
            }
        }
        for (int i = 0; i < 8 && i < flat.size(); ++i) {
            clickX = x - (54 + ((i % 2 == 1) ? 17 : 0));
            clickY = y - (12 + 17 * (i / 2));
            AspectId aspect = flat.get(i);
            if (clickX >= 0 && clickX <= 16 && clickY >= 0 && clickY <= 16 && this.known.contains(aspect)) {
                // TH removed by the aspect's index in the opener's sorted known list.
                this.sendButton(3 + this.known.indexOf(aspect));
                this.flash(aspect, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
        if (id >= 0 && id <= 2) {
            this.cachedMode = id;
        }
        this.refresh();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The tile's update packet can land a tick after the button click; keep the dial in step.
        if (this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getGameTime() % 5 == 0) {
            this.refresh();
        }
    }

    private void flash(AspectId aspect, double mouseX, double mouseY) {
        AspectDefinition definition = AspectApi.get(aspect);
        this.flashColour = definition == null ? 0xFFFFFF : definition.color();
        this.flashTimer = FLASH_FRAMES;
        this.flashX = (int) mouseX - this.leftPos - 8;
        this.flashY = (int) mouseY - this.topPos - 8;
    }
}
