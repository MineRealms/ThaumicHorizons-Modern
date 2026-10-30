package fr.wind_blade.isorropia.common.compat;

import dev.tc4port.thaumcraft.api.wand.interaction.WandBlockUseApi;
import dev.tc4port.thaumcraft.api.wand.interaction.WandBlockUseContext;
import dev.tc4port.thaumcraft.api.wand.interaction.WandBlockUseHandler;
import fr.wind_blade.isorropia.common.blocks.BlocksIS;
import fr.wind_blade.isorropia.common.tiles.TileEtherealShard;
import fr.wind_blade.isorropia.common.tiles.TileVisDynamo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Machines own held wand use through TC4's public lifecycle on both logical sides. */
public final class HeldWandTargets {
    public static final double REACH = 6.0D;

    private HeldWandTargets() { }

    public static void register() {
        WandBlockUseHandler machines = new WandBlockUseHandler() {
            @Override
            public BlockHitResult findTarget(Player player, float partialTick) {
                var eye = player.getEyePosition(partialTick);
                var look = player.level().isClientSide ? player.getViewVector(partialTick) : player.getLookAngle();
                var hit = player.level().clip(new ClipContext(eye, eye.add(look.scale(REACH)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
                return hit.getType() == HitResult.Type.BLOCK ? hit : null;
            }

            @Override
            public boolean canStart(WandBlockUseContext context) {
                if (context.hand() != InteractionHand.MAIN_HAND) return false;
                var machine = context.level().getBlockEntity(context.hit().getBlockPos());
                return machine instanceof TileVisDynamo || machine instanceof TileEtherealShard;
            }

            @Override
            public void onStart(WandBlockUseContext context) {
                if (!(context.player() instanceof ServerPlayer player)) return;
                var machine = context.level().getBlockEntity(context.hit().getBlockPos());
                if (machine instanceof TileVisDynamo dynamo) dynamo.beginCharging(player, context.wand());
                else if (machine instanceof TileEtherealShard shard) shard.beginDraining(player, context.wand());
            }
        };
        WandBlockUseApi.register(BlocksIS.VIS_DYNAMO.get(), machines);
        WandBlockUseApi.register(BlocksIS.ETHEREAL_SHARD.get(), machines);
    }
}
