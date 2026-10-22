package fr.wind_blade.isorropia.common.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fluid column inside a formed vat; drawn by the vat renderer, never by the block model. Upstream's
 * interior block forwarded every right-click to the vat, which is what let a wizard standing in the liquid
 * climb back out by looking at their feet; the click is forwarded here too.
 */
public class BlockLiquidVat extends Block {
    public BlockLiquidVat(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    //? if forge {
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return BlockCurativeVat.interact(state, level, pos, player, hand, hit);
    }
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack held,
            BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        return fr.wind_blade.isorropia.common.platform.BlockInteractions.toItemResult(
                BlockCurativeVat.interact(state, level, pos, player, hand, hit));
    }

    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        return BlockCurativeVat.interact(state, level, pos, player, hand, hit);
    }
    *///?}
}
