package fr.wind_blade.isorropia.common.vat;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import fr.wind_blade.isorropia.common.blocks.BlockCurativeVat;
import fr.wind_blade.isorropia.common.blocks.BlockCurativeVat.Type;
import fr.wind_blade.isorropia.common.blocks.BlocksIS;
import fr.wind_blade.isorropia.common.tiles.TileVat;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The curative vat: a 3x3x4 column of greatwood planks (top and bottom rings), alchemical constructs (top and
 * bottom centres), glass (middle rings) and two water sources (middle centres), formed with a wand exactly as
 * Thaumic Horizons' original does. Offsets are relative to the master, the top centre: dy 0 (top) to -3
 * (bottom), dx and dz -1..1.
 */
public final class VatStructure {
    public static final int HEIGHT = 4;

    private VatStructure() {
    }

    /** What a vat position is built from, which also decides what it turns back into. */
    public enum Part {
        WOOD, CONSTRUCT, GLASS, WATER;

        public boolean matches(BlockState state) {
            return switch (this) {
                case WOOD -> state.is(planks());
                case CONSTRUCT -> state.is(construct());
                case GLASS -> state.is(Blocks.GLASS);
                case WATER -> state.is(Blocks.WATER) && state.getFluidState().is(FluidTags.WATER)
                        && state.getFluidState().isSource();
            };
        }

        public BlockState original() {
            return switch (this) {
                case WOOD -> planks().defaultBlockState();
                case CONSTRUCT -> construct().defaultBlockState();
                case GLASS -> Blocks.GLASS.defaultBlockState();
                case WATER -> Blocks.WATER.defaultBlockState();
            };
        }
    }

    public static Part partAt(int dx, int dy, int dz) {
        boolean centre = dx == 0 && dz == 0;
        if (dy == 0 || dy == -HEIGHT + 1) {
            return centre ? Part.CONSTRUCT : Part.WOOD;
        }
        return centre ? Part.WATER : Part.GLASS;
    }

    public static Type typeAt(int dx, int dy, int dz) {
        boolean centre = dx == 0 && dz == 0;
        if (centre) {
            if (dy == 0) {
                return Type.TOP;
            }
            return dy == -HEIGHT + 1 ? Type.BOTTOM : Type.PLACEHOLDER;
        }
        if (dy == 0 || dy == -HEIGHT + 1) {
            return Type.PLACEHOLDER;
        }
        // The middle of each bottom edge is an essentia port.
        return dy == -HEIGHT + 1 + 1 && (dx == 0 || dz == 0) && Math.abs(dx + dz) == 1 ? Type.CONNECTOR : Type.GLASS;
    }

    /** The formed state of a position. The two water sources become the vat's interior. */
    public static BlockState formedAt(int dx, int dy, int dz) {
        if (partAt(dx, dy, dz) == Part.WATER) {
            return BlocksIS.VAT_INTERIOR.get().defaultBlockState();
        }
        return BlocksIS.CURATIVE_VAT.get().defaultBlockState()
                .setValue(BlockCurativeVat.VARIANT, typeAt(dx, dy, dz));
    }

    /** The unformed vat whose master would be at this offset from {@code clicked}, if one is standing there. */
    public static Optional<BlockPos> findUnformed(Level level, BlockPos clicked) {
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos master = clicked.offset(dx, dy, dz);
                    if (matchesUnformed(level, master)) {
                        return Optional.of(master);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean matchesUnformed(Level level, BlockPos master) {
        for (int dy = 0; dy > -HEIGHT; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!partAt(dx, dy, dz).matches(level.getBlockState(master.offset(dx, dy, dz)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void form(Level level, BlockPos master) {
        for (int dy = 0; dy > -HEIGHT; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(master.offset(dx, dy, dz), formedAt(dx, dy, dz), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Turns the vat back into its building blocks, skipping {@code skip} (the block being broken). */
    public static void restore(Level level, BlockPos master, @Nullable BlockPos skip) {
        for (int dy = 0; dy > -HEIGHT; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = master.offset(dx, dy, dz);
                    if (pos.equals(skip)) {
                        continue;
                    }
                    if (isVatBlock(level.getBlockState(pos))) {
                        level.setBlock(pos, partAt(dx, dy, dz).original(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    public static boolean isVatBlock(BlockState state) {
        return state.is(BlocksIS.CURATIVE_VAT.get()) || state.is(BlocksIS.VAT_INTERIOR.get());
    }

    /** What the block at {@code pos} was built from, for the drop when a player breaks a vat block. */
    public static Part partFor(BlockPos master, BlockPos pos) {
        return partAt(pos.getX() - master.getX(), pos.getY() - master.getY(), pos.getZ() - master.getZ());
    }

    /**
     * The master of the formed vat containing {@code pos}: the block entity at the top centre. A ring block
     * sits one to three blocks below the master and a block to the side, so the search covers the whole 3x3
     * footprint the block could belong to: a straight upward search finds nothing from the glass, the wood or
     * the connectors, which left them unable to answer a click, a tube or their own breaking.
     */
    @Nullable
    public static TileVat findMaster(Level level, BlockPos pos) {
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidate = pos.offset(-dx, dy, -dz);
                    if (level.getBlockEntity(candidate) instanceof TileVat vat && vat.isMaster()) {
                        return vat;
                    }
                }
            }
        }
        return null;
    }

    /** True if the vat position {@code pos} is the wizard's entry point (the water column). */
    public static boolean isInterior(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(BlocksIS.VAT_INTERIOR.get());
    }

    public static Block planks() {
        return ThaumcraftContent.block("greatwood_planks");
    }

    public static Block construct() {
        return ThaumcraftContent.block("alchemical_construct");
    }
}
