package fr.wind_blade.isorropia.client.model;

import fr.wind_blade.isorropia.Isorropia;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * Thaumic Horizons' vortex attenuator, rebuilt from {@code ModelVortexAttenuator}: a sixteen-pixel base plate, a
 * two-pixel rod and three square rings of decreasing size stacked up it. TH drew it from a 64 by 64 skin; the boxes
 * here use the same texture offsets and sit in block coordinates, so the renderer only has to place the base.
 */
public final class AttenuatorModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Isorropia.id("vortex_attenuator"), "main");

    private AttenuatorModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder body = CubeListBuilder.create()
                // Base plate, 16 x 4 x 16, sitting on the block floor.
                .texOffs(0, 0).addBox(-8.0F, 0.0F, -8.0F, 16.0F, 4.0F, 16.0F)
                // Rod, 2 x 12 x 2, from the plate up to the top of the block.
                .texOffs(0, 20).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 12.0F, 2.0F);
        body = ring(body, 5.0F, 6.0F, 0, 36, 28, 36);
        body = ring(body, 8.0F, 4.0F, 0, 48, 20, 48);
        body = ring(body, 11.0F, 2.0F, 0, 56, 12, 56);
        mesh.getRoot().addOrReplaceChild("body", body, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One square ring of four one-pixel bars, as TH's four ModelRenderers per ring. */
    private static CubeListBuilder ring(CubeListBuilder builder, float y, float radius, int northU, int northV,
            int sideU, int sideV) {
        return builder
                .texOffs(northU, northV).addBox(-radius, y, -radius, radius * 2.0F, 1.0F, 1.0F)
                .texOffs(northU, northV).addBox(-radius, y, radius - 1.0F, radius * 2.0F, 1.0F, 1.0F)
                .texOffs(sideU, sideV).addBox(radius - 1.0F, y, -radius + 1.0F, 1.0F, 1.0F, radius * 2.0F - 2.0F)
                .texOffs(sideU, sideV).addBox(-radius, y, -radius + 1.0F, 1.0F, 1.0F, radius * 2.0F - 2.0F);
    }

    public static ModelPart body(ModelPart root) {
        return root.getChild("body");
    }
}
