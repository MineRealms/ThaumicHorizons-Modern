package fr.wind_blade.isorropia.common.blocks;

import dev.tc4port.thaumcraft.api.wand.WandApi;
import fr.wind_blade.isorropia.common.infusion.SoulJar;
import fr.wind_blade.isorropia.common.items.ItemsIS;
import fr.wind_blade.isorropia.common.tiles.TileJarSoul;
import fr.wind_blade.isorropia.common.platform.StackData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** A jar holding one creature. What is inside travels with the jar, in the block entity and on the item. */
public class BlockJarSoul extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public BlockJarSoul(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // 1.21 made Block#codec() abstract; 1.20.1 has no such hook.
    //? if neoforge {
    /*@Override
    protected com.mojang.serialization.MapCodec<BlockJarSoul> codec() {
        return simpleCodec(BlockJarSoul::new);
    }
    *///?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileJarSoul(pos, state);
    }

    /** Whatever was in the jar on the item goes into the jar in the world. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CompoundTag tag = StackData.read(stack);
        CompoundTag data = tag.isEmpty() ? null : tag.getCompound(SoulJar.ENTITY_TAG);
        if (data != null && !data.isEmpty() && level.getBlockEntity(pos) instanceof TileJarSoul jar) {
            jar.setEntityData(data);
        }
    }

    /**
     * A wand on a full jar lets the creature out, which is how upstream emptied one. Breaking the jar drops it
     * with the creature still inside, so a captured animal can be carried around.
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
        if (hand != InteractionHand.MAIN_HAND || WandApi.view(player.getItemInHand(hand)).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof TileJarSoul jar) || jar.getEntityData() == null) {
            return InteractionResult.PASS;
        }
        if (client) {
            return InteractionResult.SUCCESS;
        }
        LivingEntity freed = SoulJar.releaseAt(jar.getEntityData(), level, pos);
        if (freed != null) {
            level.removeBlock(pos, false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be,
            ItemStack tool) {
        if (be instanceof TileJarSoul jar && jar.getEntityData() != null) {
            ItemStack dropped = new ItemStack(ItemsIS.JAR_SOUL.get());
            StackData.update(dropped, tag -> tag.put(SoulJar.ENTITY_TAG, jar.getEntityData().copy()));
            Block.popResource(level, pos, dropped);
        } else {
            Block.popResource(level, pos, new ItemStack(ItemsIS.JAR_SOUL.get()));
        }
        super.playerDestroy(level, player, pos, state, be, tool);
    }
}
