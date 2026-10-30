package fr.wind_blade.isorropia.common.blocks;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import fr.wind_blade.isorropia.common.tiles.TileEtherealShard;
import fr.wind_blade.isorropia.common.tiles.TilesIS;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Thaumic Horizons' ethereal shard. Right-click it with ethereal essence to give it capacity for that aspect;
 * hold a wand against it to draw the vis back out. The crystal itself is drawn by the block entity renderer.
 */
public class BlockEtherealShard extends Block implements EntityBlock {
    /** TH's bounds: a narrow shard standing in the middle of the block, 0.3 to 0.7 across and full height. */
    private static final VoxelShape SHAPE = Block.box(4.8, 0.0, 4.8, 11.2, 16.0, 11.2);

    public BlockEtherealShard(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The whole shard is a block entity renderer's work, as TH's was. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    //? if forge {
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack held,
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        return fr.wind_blade.isorropia.common.platform.BlockInteractions.toItemResult(use(state, level, pos, player, hand, hit));
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
    *///?}
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!(level.getBlockEntity(pos) instanceof TileEtherealShard shard)) {
            return InteractionResult.PASS;
        }
        // Essence widens the shard; a wand draws from it. TH tested the essence first for the same reason.
        if (!WandApi.view(held).isEmpty()) {
            /*
             * Both sides must enter the held use: the client's release is what ends the draw, and the
             * server-side player is what the tile reads back each tick.
             */
            // Let the wand start the registered public block-use session.
            return InteractionResult.PASS;
        }
        if (!held.is(ThaumcraftContent.item("wisp_essence")) || AspectApi.getItemAspects(held).amounts().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return shard.addEssence(held) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** TH returned the bound essence on removal, including explosions and creative breaking. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof TileEtherealShard shard) {
            for (ItemStack drop : shard.essenceDrops()) {
                popResource(level, pos, drop);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEtherealShard(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != TilesIS.ETHEREAL_SHARD.get()) {
            return null;
        }
        return level.isClientSide
                ? (lvl, pos, st, be) -> TileEtherealShard.clientTick(lvl, pos, st, (TileEtherealShard) be)
                : (lvl, pos, st, be) -> TileEtherealShard.serverTick(lvl, pos, st, (TileEtherealShard) be);
    }
}
