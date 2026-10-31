package fr.wind_blade.isorropia.common.blocks;

import fr.wind_blade.isorropia.common.items.ItemKeystone;
import fr.wind_blade.isorropia.common.tiles.TileSlot;
import fr.wind_blade.isorropia.common.world.PocketPlaneStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Thaumic Horizons' keystone receptacle, {@code BlockSlot}: the block at the top of a planar gateway that holds the
 * planar keystone and, once charged, cuts the portal. Right-clicking it with a wand opens or re-opens the gateway;
 * right-clicking it with an empty hand takes the keystone back out and closes it.
 */
public class BlockSlot extends Block implements EntityBlock {
    /** True while a keystone sits in the receptacle, which is what draws the tinted keystone cube. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FILLED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("filled");

    public BlockSlot(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileSlot(pos, state);
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
        return useWand(state, level, pos, player, hand, hit, level.isClientSide);
    }

    /** Shared admission for the native callback and the item-first wand pipeline. */
    public InteractionResult useWand(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, boolean client) {
        if (!(level.getBlockEntity(pos) instanceof TileSlot slot)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (slot.hasKeystone()) {
            if (held.isEmpty()) {
                if (client) return InteractionResult.SUCCESS;
                int planeId = slot.removeKeystone();
                if (slot.portalOpen()) {
                    slot.destroyPortal();
                }
                ItemStack keystone = keystoneFor((ServerLevel) level, planeId);
                if (!player.getInventory().add(keystone)) {
                    player.drop(keystone, false);
                }
                return InteractionResult.CONSUME;
            }
            if (!slot.portalOpen() && dev.tc4port.thaumcraft.api.wand.WandApi.view(held).isPresent()) {
                if (client) return InteractionResult.SUCCESS;
                slot.makePortal(held, player);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        if (held.getItem() instanceof ItemKeystone) {
            int planeId = ItemKeystone.boundPlane(held);
            if (planeId >= 0) {
                if (client) return InteractionResult.SUCCESS;
                slot.insertKeystone(planeId, ItemKeystone.boundColour(held));
                held.shrink(1);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    /** Rebuilds the keystone a receptacle was holding, name and colour included, as TH's did when it dropped one. */
    private static ItemStack keystoneFor(ServerLevel level, int planeId) {
        PocketPlaneStore.Plane plane = PocketPlaneStore.get(level.getServer()).plane(planeId);
        return plane == null ? ItemKeystone.of(planeId, "", 0xFFFFFF) : ItemKeystone.of(plane);
    }

    /** TH's {@code breakBlock}: taking the receptacle out closes its gateway and pops the keystone back out. */
    @Override
    //? if forge {
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
    //?} else {
    /*public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
    *///?}
        if (!(level instanceof ServerLevel) || !(level.getBlockEntity(pos) instanceof TileSlot slot)) {
            //? if forge {
            return;
            //?} else {
            /*return result;
            *///?}
        }
        if (slot.portalOpen()) {
            slot.destroyPortal();
        }
        if (slot.hasKeystone()) {
            ItemStack keystone = keystoneFor((ServerLevel) level, slot.removeKeystone());
            if (!player.getInventory().add(keystone)) {
                popResource(level, pos, keystone);
            }
        }
        //? if neoforge {
        /*return result;
        *///?}
    }
}
