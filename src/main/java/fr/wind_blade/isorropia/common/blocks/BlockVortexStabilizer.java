package fr.wind_blade.isorropia.common.blocks;

import fr.wind_blade.isorropia.common.tiles.TileVortexStabilizer;
import fr.wind_blade.isorropia.common.tiles.TilesIS;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Thaumic Horizons' vortex attenuator. A redstone-powered emitter that fires a cerulean beam along its {@link #FACING}
 * up to ten blocks; whatever aura node the beam lands on stops behaving as a hungry node until the beam is switched
 * off (or the attenuator is removed). The facing is set by the surface it is placed on and can be turned with a wand.
 */
public class BlockVortexStabilizer extends Block implements EntityBlock {
    /** The direction the beam fires. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BlockVortexStabilizer(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** TH's onBlockPlaced: the beam fires away from the face the block was placed against. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** A wand re-aims an already-placed attenuator at the face the player clicked. */
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
        return useWand(state, level, pos, player, hand, hit, level.isClientSide);
    }

    /** Shared admission for the native callback and the item-first wand pipeline. */
    public InteractionResult useWand(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, boolean client) {
        if (dev.tc4port.thaumcraft.api.wand.WandApi.view(player.getItemInHand(hand)).isEmpty()) {
            //? if forge {
            return super.use(state, level, pos, player, hand, hit);
            //?} else {
            /*return InteractionResult.PASS;
            *///?}
        }
        if (!client) {
            level.setBlock(pos, state.setValue(FACING, hit.getDirection()), 3);
        }
        return InteractionResult.sidedSuccess(client);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
            boolean movedByPiston) {
        if (!level.isClientSide) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof TileVortexStabilizer stabilizer) {
                stabilizer.setRedstoned(level.hasNeighborSignal(pos));
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof TileVortexStabilizer stabilizer) {
                // TH killMe: put the node back the way it was before the block (and its tile) disappears.
                stabilizer.reHungrifyTarget();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileVortexStabilizer(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != TilesIS.VORTEX_STABILIZER.get()) {
            return null;
        }
        return level.isClientSide
                ? (lvl, p, s, be) -> TileVortexStabilizer.clientTick(lvl, p, s, (TileVortexStabilizer) be)
                : (lvl, p, s, be) -> TileVortexStabilizer.serverTick(lvl, p, s, (TileVortexStabilizer) be);
    }
}
