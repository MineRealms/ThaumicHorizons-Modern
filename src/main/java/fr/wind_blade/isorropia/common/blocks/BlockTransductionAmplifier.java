package fr.wind_blade.isorropia.common.blocks;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import fr.wind_blade.isorropia.common.tiles.TileTransductionAmplifier;
import fr.wind_blade.isorropia.common.tiles.TilesIS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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

import javax.annotation.Nullable;

/**
 * Thaumic Horizons' transduction amplifier. It can only be placed against an energized aura node and points at it
 * (its {@link #FACING} is the side the node sits on). A redstone signal flips it between idle and amplifying; while
 * amplifying it asks the node for ten extra vis per aspect, layered through {@link fr.wind_blade.isorropia.common.compat.AuraNodeBonus}
 * so the bonus survives the node's per-tick structure recompute. Several amplifiers around one node add up and, past a
 * threshold, arc lightning and leak flux, as TH's multi-transducer logic did.
 */
public class BlockTransductionAmplifier extends Block implements EntityBlock {
    /** The side the adjacent energized node is on; the amplifier body juts out the opposite way. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BlockTransductionAmplifier(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    private static AuraNodeView energizedNode(BlockGetter level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof AuraNodeView node && level.getBlockState(pos).is(ThaumcraftContent.block("energized_aura_node"))
                ? node : null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Direction side : Direction.values()) {
            if (energizedNode(level, pos.relative(side)) != null) {
                return defaultBlockState().setValue(FACING, side);
            }
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return energizedNode(level, pos.relative(state.getValue(FACING))) != null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
            boolean movedByPiston) {
        if (!level.isClientSide) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof TileTransductionAmplifier amplifier) {
                amplifier.setShouldActivate(level.hasNeighborSignal(pos));
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileTransductionAmplifier(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != TilesIS.TRANSDUCTION_AMPLIFIER.get()) {
            return null;
        }
        return level.isClientSide
                ? (lvl, p, s, be) -> TileTransductionAmplifier.clientTick(lvl, p, s, (TileTransductionAmplifier) be)
                : (lvl, p, s, be) -> TileTransductionAmplifier.serverTick(lvl, p, s, (TileTransductionAmplifier) be);
    }
}
