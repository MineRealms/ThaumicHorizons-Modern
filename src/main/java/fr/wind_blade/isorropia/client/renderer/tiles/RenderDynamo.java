package fr.wind_blade.isorropia.client.renderer.tiles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientEffectsApi;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import fr.wind_blade.isorropia.client.model.RenderColors;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.client.model.DynamoBaseModel;
import fr.wind_blade.isorropia.client.model.ObjMesh;
import fr.wind_blade.isorropia.common.tiles.DynamoMotion;
import fr.wind_blade.isorropia.common.tiles.DynamoVisual;
import fr.wind_blade.isorropia.common.tiles.TileEssentiaDynamo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Thaumic Horizons' dynamo renderer, shared by the vis and essentia dynamos: a one-pixel base plate, and above it
 * Thaumcraft's scanner ring drawn twice, a full-size ring turning about the vertical and tumbling forward and a
 * narrow ring inside it tumbling the other way. The rings rise three tenths of a block while the dynamo runs; once
 * they are up and it supplies, a sheet of lightning in the dynamo's colour flickers between them. The essentia
 * dynamo also shows its essentia climbing in from below.
 */
public class RenderDynamo<T extends BlockEntity & DynamoVisual> implements BlockEntityRenderer<T> {
    private static final ResourceLocation SCANNER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/scanner.obj");
    private static final ResourceLocation LIGHTNING = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/item/lightningringv.png");
    /** The lightning strip's frames, laid side by side; TH stepped them every 40 ms. */
    private static final int LIGHTNING_FRAMES = 16;
    private static final float LIGHTNING_HALF_SIZE = 0.2F;
    private static final int LIGHTNING_ALPHA = 255;
    private static final float RING_SCALE = 0.36F;
    private static final float RING_HEIGHT = 0.2F;

    private final ModelPart plate;
    private final ResourceLocation ringTexture;
    private final ResourceLocation baseTexture;

    public RenderDynamo(BlockEntityRendererProvider.Context context, ResourceLocation ringTexture,
            ResourceLocation baseTexture) {
        this.plate = DynamoBaseModel.plate(context.bakeLayer(DynamoBaseModel.LAYER));
        this.ringTexture = ringTexture;
        this.baseTexture = baseTexture;
    }

    public static ResourceLocation texture(String name) {
        return Isorropia.id("textures/entity/machines/" + name + ".png");
    }

    @Override
    public void render(T dynamo, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        DynamoMotion motion = dynamo.motion();
        if (dynamo instanceof TileEssentiaDynamo essentia) {
            essentiaTrail(essentia);
        }
        if (motion.risen() && dynamo.supplying()) {
            lightning(dynamo, poseStack, buffers);
        }
        plate.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(baseTexture)), light, overlay);
        poseStack.pushPose();
        poseStack.translate(0.5F, RING_HEIGHT + motion.rise(partialTick), 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(motion.rotation(partialTick)));
        float tumble = motion.tumble(partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(tumble));
        poseStack.scale(RING_SCALE, RING_SCALE, RING_SCALE);
        VertexConsumer rings = buffers.getBuffer(RenderType.entityCutoutNoCull(ringTexture));
        ObjMesh mesh = ObjMesh.get(SCANNER);
        mesh.renderAllExcept(List.of(), poseStack, rings, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-2.0F * tumble));
        poseStack.scale(0.5F, 1.0F, 0.5F);
        mesh.renderAllExcept(List.of(), poseStack, rings, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    /** TH's UtilsFX.renderFacingQuad: a camera-facing square of lightning frames, additive and nearly opaque. */
    private void lightning(T dynamo, PoseStack poseStack, MultiBufferSource buffers) {
        int frame = (int) ((System.nanoTime() / 40_000_000L + dynamo.getBlockPos().getX()) % LIGHTNING_FRAMES);
        float u0 = frame / (float) LIGHTNING_FRAMES;
        float u1 = (frame + 1) / (float) LIGHTNING_FRAMES;
        int rgb = dynamo.glowColour();
        int red = rgb >> 16 & 255;
        int green = rgb >> 8 & 255;
        int blue = rgb & 255;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer quad = buffers.getBuffer(RenderType.entityTranslucent(LIGHTNING));
        float s = LIGHTNING_HALF_SIZE;
        vertex(quad, pose, -s, -s, u0, 1.0F, red, green, blue);
        vertex(quad, pose, s, -s, u1, 1.0F, red, green, blue);
        vertex(quad, pose, s, s, u1, 0.0F, red, green, blue);
        vertex(quad, pose, -s, s, u0, 0.0F, red, green, blue);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int red,
            int green, int blue) {
        RenderColors.vertex(consumer, pose, x, y, 0.0F, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                red, green, blue, LIGHTNING_ALPHA, 0.0F, 0.0F, 1.0F);
    }

    /**
     * The essentia climbing from the tube below into the dynamo. TH spawned a trail every frame; this spawns one a
     * tick, so a fast frame rate does not thicken it.
     */
    private static void essentiaTrail(TileEssentiaDynamo dynamo) {
        AspectId essentia = dynamo.trailEssentia();
        if (essentia == null || !(dynamo.getLevel() instanceof ClientLevel level)
                || dynamo.lastTrailTick == level.getGameTime()) {
            return;
        }
        dynamo.lastTrailTick = level.getGameTime();
        ThaumcraftClientEffectsApi.spawnEssentiaTrail(level, Vec3.atBottomCenterOf(dynamo.getBlockPos()),
                Vec3.atCenterOf(dynamo.getBlockPos()), (int) level.getGameTime(), AspectApi.get(essentia).color(), 0.3F);
    }

    @Override
    public boolean shouldRenderOffScreen(T dynamo) {
        return false;
    }
}
