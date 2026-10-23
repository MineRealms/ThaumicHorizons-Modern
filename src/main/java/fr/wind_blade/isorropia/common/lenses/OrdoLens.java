package fr.wind_blade.isorropia.common.lenses;

import fr.wind_blade.isorropia.common.compat.ScanBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Scholar's lens: whatever the wearer looks at is studied, exactly as if they had held a thaumometer up to it.
 * The scan itself is Thaumcraft's; the lens only decides when to ask for it, and the client draws the reading.
 *
 * <p>It asks every tick, which is how the 1.12 lens behaved. The older Thaumic Horizons lens made the wearer
 * stare for 250 ticks first; that wait was dropped when the lens moved into Isorropia and is not brought back
 * here.</p>
 */
public class OrdoLens extends Lens {
    public OrdoLens() {
        super(id("ordo_lens"), 0xD5D4EC);
    }

    @Override
    public void tick(ServerPlayer player, ItemStack goggles, Slot slot) {
        ScanBridge.scanLookedAt(player);
    }
}
