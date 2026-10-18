package fr.wind_blade.isorropia.client.renderer.tiles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import fr.wind_blade.isorropia.client.model.RenderColors;
import fr.wind_blade.isorropia.client.model.ObjMesh;
import fr.wind_blade.isorropia.common.blocks.BlockVortexStabilizer;
import fr.wind_blade.isorropia.common.tiles.TileVortexStabilizer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Thaumic Horizons' vortex attenuator. The emitter head reuses Thaumcraft's stabilizer model; while a redstone signal
 * feeds it, a cerulean ribbon of light runs along its facing to whatever the beam lands on, as TH's {@code beamBore}
 * drew with colour {@code 33023} (a deep sky blue).
 */
public class RenderVortexStabilizer implements BlockEntityRenderer<TileVortexStabilizer> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "textures/models/node_stabilizer.obj");
    private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "textures/models/node_stabilizer.png");
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "textures/models/node_stabilizer_over.png");
    private static final ResourceLocation BEAM = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "textures/misc/wispy.png");
    /** TH's beamBore colour: a cerulean blue. */
    private static final int BEAM_COLOUR = 33023;
    private static final float BEAM_HALF_WIDTH = 0.07F;
    private static final int STEPS_PER_BLOCK = 8;

    private final ObjMesh mesh = ObjMesh.get(MODEL);

    public RenderVortexStabilizer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileVortexStabilizer stabilizer, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        Direction facing = stabilizer.getBlockState().getValue(BlockVortexStabilizer.FACING);
        boolean active = stabilizer.isRedstoned();
        int colour = active ? BEAM_COLOUR : 0x00FFFFFF;
        float red = (colour >> 16 & 255) / 255.0F;
        float green = (colour >> 8 & 255) / 255.0F;
        float blue = (colour & 255) / 255.0F;

        // The emitter head, using Thaumcraft's stabilizer transform so it reads as a machine in the block.
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.0F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        orient(facing, poseStack);
        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(BASE));
        mesh.render("lock", poseStack, base, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(OVERLAY));
        mesh.render("lock", poseStack, glow, LightTexture.FULL_BRIGHT, overlay, red, green, blue, 1.0F);
        poseStack.popPose();

        if (active && stabilizer.hasBeam() && stabilizer.beamEnd() != null) {
            renderBeam(stabilizer, poseStack, buffers, facing, red, green, blue);
        }
    }

    private static void renderBeam(TileVortexStabilizer stabilizer, PoseStack poseStack, MultiBufferSource buffers,
            Direction facing, float red, float green, float blue) {
        BlockPos pos = stabilizer.getBlockPos();
        BlockPos end = stabilizer.beamEnd();
        Vec3 from = Vec3.atCenterOf(pos)
                .add(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5);
        Vec3 to = Vec3.atCenterOf(end);
        float distance = (float) from.distanceTo(to);
        if (distance < 1.0E-3F) {
            return;
        }
        int steps = Math.max(1, Math.round(distance) * STEPS_PER_BLOCK);
        float time = (float) (System.nanoTime() / 30_000_000L);
        VertexConsumer vertices = buffers.getBuffer(ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(BEAM));
        int rgb = Math.round(red * 255.0F) << 16 | Math.round(green * 255.0F) << 8 | Math.round(blue * 255.0F);
        RibbonPoint previous = new RibbonPoint(from, 0.0F, 0.0F);
        for (int i = 1; i <= steps; i++) {
            float fraction = i / (float) steps;
            float alpha = 1.0F - Math.abs(i - steps / 2.0F) / (steps / 2.0F);
            Vec3 point = from.add(to.subtract(from).scale(fraction));
            float u = fraction * 4.0F + time * 0.02F;
            RibbonPoint next = new RibbonPoint(point, u, alpha);
            ribbonQuad(vertices, poseStack.last(), previous, next, new Vec3(0.0, BEAM_HALF_WIDTH, 0.0), rgb);
            ribbonQuad(vertices, poseStack.last(), previous, next, new Vec3(BEAM_HALF_WIDTH, 0.0, 0.0), rgb);
            previous = next;
        }
    }

    private static void ribbonQuad(VertexConsumer vertices, PoseStack.Pose pose, RibbonPoint first, RibbonPoint second,
            Vec3 width, int colour) {
        vertex(vertices, pose, first.position().subtract(width), first.u(), 1.0F, colour, first.alpha());
        vertex(vertices, pose, first.position().add(width), first.u(), 0.0F, colour, first.alpha());
        vertex(vertices, pose, second.position().add(width), second.u(), 0.0F, colour, second.alpha());
        vertex(vertices, pose, second.position().subtract(width), second.u(), 1.0F, colour, second.alpha());
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 pos, float u, float v, int colour,
            float alpha) {
        RenderColors.vertex(vertices, pose, (float) pos.x, (float) pos.y, (float) pos.z, u, v,
                OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, colour >> 16 & 255, colour >> 8 & 255, colour & 255,
                Mth.clamp(Math.round(alpha * 255.0F), 0, 255), 0.0F, 0.0F, 1.0F);
    }

    private record RibbonPoint(Vec3 position, float u, float alpha) {
    }

    /**
     * Rotate the head so its mounting plate lies against the face it was placed on and the stem points at the node,
     * the way the transduction amplifier is oriented. Without this the head always stood upright, so one hung on a
     * wall or ceiling read as a quarter turn out of place.
     */
    private static void orient(Direction facing, PoseStack poseStack) {
        switch (facing) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            default -> { /* UP: the model already points up */ }
        }
    }
}
