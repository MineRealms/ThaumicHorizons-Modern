package fr.wind_blade.isorropia.client.renderer.entities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import fr.wind_blade.isorropia.client.model.RenderColors;
import fr.wind_blade.isorropia.common.entities.EntityHangingLabel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Half-block TC jar label flat against the wall, with the aspect icon (tinted with its colour) on top. */
public class RenderHangingLabel extends EntityRenderer<EntityHangingLabel> {
    private static final ResourceLocation LABEL = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/label.png");
    private static final float LABEL_HALF = 0.25F;
    private static final float ICON_HALF = 8.0F * 0.021F;
    /** The tag behind the aspect sprite, as Thaumcraft draws it. */
    private static final ResourceLocation TAG_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_back.png");
    private static final int TAG_COLOUR = 0x1A1A1A;
    private static final float TAG_ALPHA = 0.8F;

    public RenderHangingLabel(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EntityHangingLabel entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        // Same face-out orientation as a vanilla painting: HangingEntity.yRot is direction * 90 and
        // the quad faces -Z, so 180 - yRot points the label outwards without turning the aspect upside down.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(
                180.0F - Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.translate(0.0F, 0.0F, 0.03F);
        VertexConsumer label = buffers.getBuffer(RenderType.entityTranslucent(LABEL));
        quad(poseStack, label, 0.0F, LABEL_HALF, 0xFFFFFF, 1.0F, light);
        // A label has two sides, and upstream drew both.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        quad(poseStack, label, 0.0F, LABEL_HALF, 0xFFFFFF, 1.0F, light);
        poseStack.popPose();

        AspectId aspect = entity.getAspect();
        AspectDefinition definition = aspect == null ? null : AspectApi.get(aspect);
        if (definition != null) {
            // The aspect tag: a dark tile with the aspect's own sprite over it.
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, -0.001F);
            quad(poseStack, buffers.getBuffer(RenderType.entityTranslucent(TAG_BACKGROUND)), 0.0F, ICON_HALF,
                    TAG_COLOUR, TAG_ALPHA, light);
            quad(poseStack, buffers.getBuffer(RenderType.entityTranslucent(definition.texture())), -0.001F,
                    ICON_HALF, 0xFFFFFF, 1.0F, light);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /** A square on the plane {@code z}, facing -Z (away from the wall), upright for the viewer. */
    private static void quad(PoseStack poseStack, VertexConsumer consumer, float z, float half, int rgb, float alpha, int light) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        int a = (int) (alpha * 255.0F);
        vertex(consumer, matrix, normal, half, -half, z, 0.0F, 1.0F, r, g, b, a, light);
        vertex(consumer, matrix, normal, -half, -half, z, 1.0F, 1.0F, r, g, b, a, light);
        vertex(consumer, matrix, normal, -half, half, z, 1.0F, 0.0F, r, g, b, a, light);
        vertex(consumer, matrix, normal, half, half, z, 0.0F, 0.0F, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float u, float v,
            int r, int g, int b, int a, int light) {
        RenderColors.vertex(consumer, matrix, normal, x, y, z, u, v, OverlayTexture.NO_OVERLAY, light,
                r, g, b, a, 0.0F, 0.0F, -1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityHangingLabel entity) {
        return LABEL;
    }
}
