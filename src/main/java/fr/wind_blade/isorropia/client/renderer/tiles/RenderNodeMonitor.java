package fr.wind_blade.isorropia.client.renderer.tiles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.client.model.ObjMesh;
import fr.wind_blade.isorropia.common.blocks.BlockNodeMonitor;
import fr.wind_blade.isorropia.common.tiles.TileNodeMonitor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Thaumic Horizons' node monitor renderer: Thaumcraft's scanner at four tenths size, set against the node and turning
 * slowly while the node is healthy, with a hexagonal screen in its middle that blinks between its bright and dark
 * faces while the node is critical.
 */
public class RenderNodeMonitor implements BlockEntityRenderer<TileNodeMonitor> {
    private static final ResourceLocation SCANNER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/scanner.obj");
    private static final ResourceLocation SCREEN = Isorropia.id("models/obj/hexagon.obj");
    private static final ResourceLocation BODY = RenderDynamo.texture("nodemon");
    private static final ResourceLocation SCREEN_IDLE = RenderDynamo.texture("nodemonscreen");
    private static final ResourceLocation SCREEN_ALERT = RenderDynamo.texture("nodemonscreenactive");
    private static final float SCALE = 0.4F;
    /** How far from the centre the scanner sits towards the node, in the scaled frame. */
    private static final float SET_BACK = 0.8F;

    public RenderNodeMonitor(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileNodeMonitor monitor, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        Direction facing = monitor.getBlockState().getValue(BlockNodeMonitor.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(SCALE, SCALE, SCALE);
        // TH's six cases, one per face, each setting the scanner back against the node and turning it to face out.
        switch (facing) {
            case UP -> poseStack.translate(0.0F, -SET_BACK, 0.0F);
            case DOWN -> {
                poseStack.translate(0.0F, SET_BACK, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            }
            case NORTH -> {
                poseStack.translate(0.0F, 0.0F, SET_BACK);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
            case SOUTH -> {
                poseStack.translate(0.0F, 0.0F, -SET_BACK);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case WEST -> {
                poseStack.translate(SET_BACK, 0.0F, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            }
            case EAST -> {
                poseStack.translate(-SET_BACK, 0.0F, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            }
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(monitor.rotation(partialTick)));
        ObjMesh.get(SCANNER).renderAllExcept(List.of(), poseStack,
                buffers.getBuffer(RenderType.entityCutoutNoCull(BODY)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        ResourceLocation screen = monitor.blink() ? SCREEN_ALERT : SCREEN_IDLE;
        // The screen is a display, not a lit surface: it has to read in an unlit corner of a base.
        ObjMesh.get(SCREEN).renderAllExcept(List.of(), poseStack,
                buffers.getBuffer(RenderType.entityTranslucent(screen)),
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }
}
