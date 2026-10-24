package fr.wind_blade.isorropia.client.renderer.tiles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.wind_blade.isorropia.client.model.RenderColors;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.client.ClientRenderTypes;
import fr.wind_blade.isorropia.client.fx.VatFx;
import fr.wind_blade.isorropia.client.model.ObjMesh;
import fr.wind_blade.isorropia.common.blocks.BlockCurativeVat;
import fr.wind_blade.isorropia.common.tiles.TileVat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The curative vat: its own mesh plus the body it is preparing, both brought over from the original mods.
 *
 * <p>The vat is not drawn by its blocks at all. Upstream left every vat block with an empty model and drew the
 * whole thing from {@code vat_redo.obj} — bottom slab, four pipes, glass wall, liquid, top slab and the four
 * dials — in two passes, liquid first without depth writes and the rest over it. This renderer does the same
 * from a single block entity, the TOP variant, which is the one holding the state; drawing it in one place also
 * fixes the draw order between the liquid, the body inside and the glass, which two separate renderers left to
 * the distance sort.
 */
public class RenderVat implements BlockEntityRenderer<TileVat> {
    private static final ResourceLocation MODEL = Isorropia.id("models/block/vat_redo.obj");
    private static final ResourceLocation SHELL_TEXTURE = Isorropia.id("textures/block/curative_vat.png");
    private static final ResourceLocation WATER_TEXTURE = Isorropia.id("textures/block/water_still.png");
    private static final ResourceLocation EFFIGY = Isorropia.id("textures/entity/corpseeffigy.png");
    private static final ResourceLocation REVIVED = Isorropia.id("textures/entity/corpseeffigyrevived.png");
    private static final List<String> WATER_GROUP = List.of("Water");
    /** How many blocks the mesh sits below the master: the master is the top layer. */
    private static final double LAYERS_BELOW = 3.0;
    /**
     * Upstream passed 0.1 to a 1.7/1.12 model, whose vertices are still in sixteenths of a block, so the body
     * came out a little larger than a player. A modern model is already in blocks, where the same number would
     * be a two-pixel speck; 0.9 is the size that fills the vat's two-block interior.
     */
    private static final float CORPSE_SCALE = 1.2F;

    private final HumanoidModel<?> corpse;

    public RenderVat(BlockEntityRendererProvider.Context context) {
        this.corpse = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER));
    }

    @Override
    public void render(TileVat vat, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            int overlay) {
        if (vat.getBlockState().getValue(BlockCurativeVat.VARIANT) != BlockCurativeVat.Type.TOP) {
            return;
        }
        celestialAbsorption(vat);
        ObjMesh mesh = ObjMesh.get(MODEL);
        // The liquid is drawn first and leaves the depth buffer alone, so whatever stands in it is drawn over
        // the surface rather than under an 85% opaque sheet, which is the order upstream had. The shell goes
        // over both.
        poseStack.pushPose();
        poseStack.translate(0.5, -LAYERS_BELOW, 0.5);
        VertexConsumer water = buffers.getBuffer(ClientRenderTypes.liquid(WATER_TEXTURE));
        mesh.render("Water", poseStack, water, light, overlay, 1.0F, 1.0F, 1.0F, 0.85F);
        poseStack.popPose();
        renderCorpse(vat, partialTick, poseStack, buffers, light, overlay);
        poseStack.pushPose();
        poseStack.translate(0.5, -LAYERS_BELOW, 0.5);
        // The mesh's winding is not reliable, so both passes run without backface culling.
        VertexConsumer shell = buffers.getBuffer(ClientRenderTypes.shell(SHELL_TEXTURE));
        mesh.renderAllExcept(WATER_GROUP, poseStack, shell, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    /**
     * The corpse effigy while the vat charges it, and the body itself while a self infusion runs. It hangs head
     * down inside the vat and bobs on the surface, which is how the vat has been drawn since 1.7.10.
     */
    private void renderCorpse(TileVat vat, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light,
            int overlay) {
        ResourceLocation skin = null;
        if (vat.getMode() == TileVat.MODE_EFFIGY) {
            skin = EFFIGY;
        } else if (vat.getMode() == TileVat.MODE_SELF_READY || vat.isSelfInfusing()) {
            skin = REVIVED;
        }
        if (skin == null) {
            return;
        }
        float ticks = Minecraft.getInstance().player == null ? 0.0F : Minecraft.getInstance().player.tickCount + partialTick;
        float bob = 0.1F * Mth.cos(ticks * 0.0174F);
        poseStack.pushPose();
        poseStack.translate(0.5, -0.45 - bob, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(CORPSE_SCALE, CORPSE_SCALE, CORPSE_SCALE);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        // The body hangs in a lit vat, so it is drawn full bright like upstream's renderer did.
        RenderColors.render(corpse, poseStack, consumer, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    /** The mesh reaches two blocks out and four down, so it must draw beyond the master's own block. */
    @Override
    public boolean shouldRenderOffScreen(TileVat vat) {
        return true;
    }

    /**
     * While the vat draws on the sun or moon, light rains into it from four and a half blocks above, as 1.12 drew
     * it each tick. The renderer runs every frame, so the tick it last spawned for is remembered on the vat.
     */
    private static void celestialAbsorption(TileVat vat) {
        if (!vat.isDrawingAura() || !(vat.getLevel() instanceof ClientLevel level)
                || vat.lastAuraEffectTick == level.getGameTime()) {
            return;
        }
        vat.lastAuraEffectTick = level.getGameTime();
        BlockPos pos = vat.getBlockPos();
        VatFx.celestialAbsorption(level, pos.getX() + 0.5, pos.getY() + 4.5, pos.getZ() + 0.5, true);
    }
}
