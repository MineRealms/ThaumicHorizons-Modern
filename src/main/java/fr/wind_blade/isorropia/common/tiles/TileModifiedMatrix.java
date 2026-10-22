package fr.wind_blade.isorropia.common.tiles;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * The runic matrix adapted to the curative vat: it sits on top of the vat, shows what the vat holds through
 * goggles, and is where a wand starts an infusion from the pedestals.
 */
public class TileModifiedMatrix extends BlockEntity implements AspectContainerView {
    public TileModifiedMatrix(BlockPos pos, BlockState state) {
        super(TilesIS.MODIFIED_MATRIX.get(), pos, state);
    }

    @Nullable
    public TileVat getVat() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof TileVat vat
                && vat.isMaster() ? vat : null;
    }

    /**
     * A wand right-click, as upstream: one tap takes stock of the stabilisers and starts an infusion from the
     * pedestals. Sneaking vents the vat's stored flux instead, and a tap that finds nothing to infuse says so
     * and leaves the vat as it was.
     */
    public boolean onWandUse(Player player) {
        TileVat vat = getVat();
        if (vat == null || level == null) {
            return false;
        }
        if (vat.isInfusing() || vat.isExpunging()) {
            return false;
        }
        if (player.isShiftKeyDown() && vat.getFluxStocked() > 0.0F) {
            vat.ventFlux();
            return true;
        }
        vat.surveyStabilizers();
        if (vat.startInfusion(player)) {
            return true;
        }
        // The refusal has to be spoken: research, pedestals and a creature in the vat are all required, and a
        // tap that silently did nothing read as a broken machine.
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(vat.hasPedestalItems()
                    ? "isorropia.curative.no_recipe"
                    : "isorropia.curative.no_pedestals"), false);
        }
        return false;
    }

    @Override
    public AspectAmounts visibleAspects() {
        TileVat vat = getVat();
        return vat == null ? new AspectAmounts(Map.of()) : vat.visibleAspects();
    }
}
