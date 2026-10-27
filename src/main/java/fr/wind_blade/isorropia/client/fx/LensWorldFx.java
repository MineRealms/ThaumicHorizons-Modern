package fr.wind_blade.isorropia.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tc4port.thaumcraft.api.aspect.AspectQueryApi;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import dev.tc4port.thaumcraft.api.client.WorldRenderTransforms;
import dev.tc4port.thaumcraft.api.ThaumcraftTags;
import fr.wind_blade.isorropia.client.model.RenderColors;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.client.LensClientState;
import fr.wind_blade.isorropia.common.lenses.Lens;
import fr.wind_blade.isorropia.common.lenses.LensManager;
import fr.wind_blade.isorropia.common.lenses.Lenses;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The world half of the worn lenses: the kirlian lens' ripples around living things and the scholar's floating
 * name. The screen half lives in LensHud; the two share LensClientState so the name and the readout agree.
 *
 * <p>World overlays drawn after the level has composed need the world matrices reinstated, because the pose
 * stack handed to the stage no longer carries them. Thaumcraft's client API owns that scope, so the geometry is
 * submitted from a fresh pose that only holds the object transform, with positions expressed relative to the
 * camera.
 */
@Mod.EventBusSubscriber(modid = Isorropia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LensWorldFx {
    /** Kirlian reaches this far, and fades to nothing at the edge. */
    private static final double RANGE = 24.0;
    /** The most ripples one creature ever shows. */
    private static final double MAX_RIPPLES = 4.0;
    /** How tall a creature's ripple texture is drawn relative to its smaller dimension. */
    private static final float RIPPLE_DIVISOR = 12.0F;
    private static final float RIPPLE_STEPS = 16.0F;
    private static final float NAME_SCALE = 0.03F;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private static final ResourceLocation RIPPLE = Isorropia.id("textures/fx/ripple.png");
    private static final ResourceLocation VORTEX = Isorropia.id("textures/fx/vortex.png");
    private static final ResourceLocation ELDRITCH_VORTEX =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/vortex.png");

    private LensWorldFx() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        // Both lenses are worn, not held, and neither means anything in a detached camera.
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean worn = minecraft.options.getCameraType().isFirstPerson() && LensManager.isGoggles(head);
        // The world pass owns the shared target: it runs every frame whether or not the HUD is hidden, so the
        // floating name does not vanish when the player presses F1.
        Entity scholar = worn && fitted(head, Lenses.ORDO) ? minecraft.crosshairPickEntity : null;
        LensClientState.scholarTarget(scholar);
        boolean kirlian = worn && fitted(head, Lenses.AIR);
        if (!kirlian && scholar == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        // 1.21 hands the level-stage event a DeltaTracker instead of a ready float.
        //? if forge {
        float partialTick = event.getPartialTick();
        //?} else {
        /*float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        *///?}
        PoseStack poseStack = new PoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        try (ThaumcraftClientRenderApi.WorldRenderScope ignored = ThaumcraftClientRenderApi
                .beginAfterLevelWorldRender(WorldRenderTransforms.modelViewMatrix(), event.getProjectionMatrix(),
                        false)) {
            if (kirlian) {
                ripples(poseStack, buffers, player, cameraPos, partialTick);
                buffers.endBatch();
            }
            if (scholar != null) {
                name(poseStack, buffers, minecraft, scholar, cameraPos, partialTick);
                buffers.endBatch();
            }
        }
    }

    private static boolean fitted(ItemStack head, Lens lens) {
        return Lens.read(head, Lens.Slot.LEFT) == lens || Lens.read(head, Lens.Slot.RIGHT) == lens;
    }

    /**
     * The kirlian lens: every living thing within reach shows the state of its life as a stack of ripples that
     * grows with proximity, turning into a counter-rotating vortex for the undead and an eldritch maelstrom for
     * the eldritch. The count, sizes, spins and colours follow the original.
     */
    private static void ripples(PoseStack poseStack, MultiBufferSource.BufferSource buffers, LocalPlayer player,
            Vec3 cameraPos, float partialTick) {
        AABB box = player.getBoundingBox().inflate(RANGE);
        List<LivingEntity> creatures = player.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != player);
        for (LivingEntity creature : creatures) {
            double chebyshev = Math.max(Math.abs(creature.getX() - player.getX()),
                    Math.max(Math.abs(creature.getY() - player.getY()), Math.abs(creature.getZ() - player.getZ())));
            float alpha = (float) (1.0 - Math.min(chebyshev, RANGE) / RANGE);
            if (alpha <= 0.0F) {
                continue;
            }
            float size = Math.min(creature.getBbHeight(), creature.getBbWidth());
            double x = Mth.lerp(partialTick, creature.xOld, creature.getX());
            double y = Mth.lerp(partialTick, creature.yOld, creature.getY());
            double z = Mth.lerp(partialTick, creature.zOld, creature.getZ());
            double centreY = y + creature.getBbHeight() / 2.0;
            if (creature.getType().is(ThaumcraftTags.ELDRITCH_ENTITIES)) {
                vortex(poseStack, buffers, cameraPos, x, centreY, z, size * 0.8F, alpha, ELDRITCH_VORTEX,
                        creature.tickCount % 360, -(creature.tickCount % 360) * 2);
            } else if (creature.getMobType() == MobType.UNDEAD) {
                vortex(poseStack, buffers, cameraPos, x, centreY, z, size * 1.3F, alpha, VORTEX,
                        -(creature.tickCount % 360), creature.tickCount % 360 * 2);
            } else {
                ripple(poseStack, buffers, cameraPos, x, centreY, z, size, alpha,
                        player.position().distanceTo(creature.position()), creature.tickCount % 16);
            }
        }
    }

    /** Two counter-rotating copies of the same vortex, the second at twice the size. */
    private static void vortex(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cameraPos, double x,
            double y, double z, float size, float alpha, ResourceLocation texture, float spin, float counterSpin) {
        RenderType layer = ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(texture);
        VertexConsumer vertices = buffers.getBuffer(layer);
        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        quad(vertices, poseStack.last(), size, alpha);
        poseStack.mulPose(Axis.ZP.rotationDegrees(counterSpin));
        quad(vertices, poseStack.last(), size * 2.0F, alpha);
        poseStack.popPose();
    }

    /** A living creature's ripples: a handful of rings whose radii step outward with distance. */
    private static void ripple(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cameraPos, double x,
            double y, double z, float size, float alpha, double distance, int phase) {
        double count = Math.min(48.0 / Math.max(distance, 1.0E-4) + 1.0, MAX_RIPPLES);
        RenderType layer = ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(RIPPLE);
        VertexConsumer vertices = buffers.getBuffer(layer);
        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        for (int i = 0; i < count; i++) {
            float radius = (float) ((i * RIPPLE_STEPS / (count + 1.0) + phase) % RIPPLE_STEPS / RIPPLE_DIVISOR) * size;
            quad(vertices, poseStack.last(), radius, alpha);
        }
        poseStack.popPose();
    }

    /** One camera-facing quad of the given half-extent, white so the texture's own colour shows. */
    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, float halfSize, float alpha) {
        int alphaByte = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        vertex(vertices, pose, -halfSize, -halfSize, 0.0F, 1.0F, alphaByte);
        vertex(vertices, pose, halfSize, -halfSize, 1.0F, 1.0F, alphaByte);
        vertex(vertices, pose, halfSize, halfSize, 1.0F, 0.0F, alphaByte);
        vertex(vertices, pose, -halfSize, halfSize, 0.0F, 0.0F, alphaByte);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v,
            int alpha) {
        RenderColors.vertex(vertices, pose, x, y, 0.0F, u, v, OverlayTexture.NO_OVERLAY, FULL_BRIGHT,
                255, 255, 255, alpha, 0.0F, 0.0F, 1.0F);
    }

    /**
     * The scholar's floating name: the creature the lens is reading, labelled above its head and grown in from
     * nothing so a new target does not pop. It faces the wearer horizontally, as the original did, so the text
     * stays upright however the camera is pitched.
     */
    private static void name(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Minecraft minecraft,
            Entity target, Vec3 cameraPos, float partialTick) {
        float grown = LensClientState.nameSize();
        if (grown <= 0.0F) {
            return;
        }
        double x = Mth.lerp(partialTick, target.xOld, target.getX());
        double y = Mth.lerp(partialTick, target.yOld, target.getY());
        double z = Mth.lerp(partialTick, target.zOld, target.getZ());
        boolean hasAspects = !AspectQueryApi.entity(target).amounts().isEmpty();
        double labelY = y + target.getBbHeight() + (hasAspects ? 1.1 : 0.6);
        String text = target instanceof ItemEntity item ? item.getItem().getHoverName().getString()
                : target.getName().getString();
        float yaw = (float) (Math.atan2(cameraPos.x - x, cameraPos.z - z) * 180.0 / Math.PI);
        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(x - cameraPos.x, labelY - cameraPos.y, z - cameraPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        float scale = NAME_SCALE * grown;
        poseStack.scale(scale, scale, scale);
        font.drawInBatch(text, 1.0F - font.width(text) / 2.0F, 1.0F, 0xFFFFFF, false, poseStack.last().pose(),
                buffers, Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
        poseStack.popPose();
    }
}
