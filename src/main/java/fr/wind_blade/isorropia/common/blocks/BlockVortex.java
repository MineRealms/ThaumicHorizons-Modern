package fr.wind_blade.isorropia.common.blocks;

import fr.wind_blade.isorropia.common.tiles.TileVortex;
import fr.wind_blade.isorropia.common.tiles.TilesIS;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Thaumic Horizons' vortex. An unbreakable, hueless, glowing rift that a node collapses into when an energized node is
 * amplified past its limit or when one is placed by hand. It draws itself entirely with a block entity renderer and has
 * no collision, so entities fall straight through and are then dragged in. Right-clicking it with a wand pulls a
 * stored void-crafted item back out (TH's {@code IWandable}).
 */
public class BlockVortex extends Block implements EntityBlock {
    /** TH gave the rift a 0.3-0.7 target box while leaving its collision empty. */
    private static final VoxelShape SHAPE = Shapes.box(0.3, 0.3, 0.3, 0.7, 0.7, 0.7);

    public BlockVortex(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * TH's wand interaction: a wand right-click pulls the rift's oldest stored item back out. TC4R runs this from the
     * wand item's block-use phase, so a non-wand click is left to ordinary use.
     */
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
            return InteractionResult.PASS;
        }
        if (!client && level.getBlockEntity(pos) instanceof TileVortex vortex) {
            vortex.popStored(player);
        }
        return InteractionResult.sidedSuccess(client);
    }

    /** A vortex placed by hand swallows a random handful of aspects, as TH's did. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player && !level.isClientSide
                && level.getBlockEntity(pos) instanceof TileVortex vortex) {
            vortex.rollAspects(level.getRandom());
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileVortex(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != TilesIS.VORTEX.get()) {
            return null;
        }
        return level.isClientSide
                ? (lvl, p, s, be) -> TileVortex.clientTick(lvl, p, s, (TileVortex) be)
                : (lvl, p, s, be) -> TileVortex.serverTick(lvl, p, s, (TileVortex) be);
    }
}
