package fr.wind_blade.isorropia.common.blocks;

import dev.tc4port.thaumcraft.api.wand.WandApi;
import fr.wind_blade.isorropia.common.menu.VisDynamoMenu;
import fr.wind_blade.isorropia.common.tiles.TileVisDynamo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import fr.wind_blade.isorropia.common.platform.Menus;

import javax.annotation.Nullable;

/**
 * TH's vis dynamo. Holding a wand against it charges the tile, which then feeds the vis network through
 * an adjacent relay; clicking it with anything else opens TH's six-primal selection screen.
 */
public class BlockVisDynamo extends Block implements EntityBlock {
    public BlockVisDynamo(Properties properties) {
        super(properties);
    }

    /** The base and rings are drawn by the block entity renderer, as TH's dynamo was all TESR. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileVisDynamo(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? (lvl, pos, st, be) -> {
                    if (be instanceof TileVisDynamo dynamo) {
                        TileVisDynamo.clientTick(lvl, pos, st, dynamo);
                    }
                }
                : (lvl, pos, st, be) -> {
                    if (be instanceof TileVisDynamo dynamo) {
                        TileVisDynamo.serverTick(lvl, pos, st, dynamo);
                    }
                };
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
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof TileVisDynamo dynamo)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (WandApi.view(held).isEmpty()) {
            // TH opened the selection screen for anything but a wand.
            if (player instanceof ServerPlayer serverPlayer) {
                Menus.open(serverPlayer, new SimpleMenuProvider(
                        (id, inventory, opener) -> new VisDynamoMenu(id, inventory, dynamo), getName()), pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Let the wand start the registered public block-use session.
        return InteractionResult.PASS;
    }
}
