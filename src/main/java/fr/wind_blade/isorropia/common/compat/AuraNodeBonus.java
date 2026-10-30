package fr.wind_blade.isorropia.common.compat;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.event.EnergizedNodeEvent;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.blocks.BlockTransductionAmplifier;
import fr.wind_blade.isorropia.common.tiles.TileTransductionAmplifier;
import java.util.LinkedHashMap;
import net.minecraft.core.Direction;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Live amplifier contributions; TC4 owns the output pool, caching and synchronization. */
@Mod.EventBusSubscriber(modid = Isorropia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuraNodeBonus {
    private AuraNodeBonus() { }

    @SubscribeEvent
    public static void onNodeOutput(EnergizedNodeEvent.Output event) {
        int amplifiers = 0;
        for (Direction direction : Direction.values()) {
            var position = event.node().relative(direction);
            if (!event.level().hasChunkAt(position)) continue;
            var entity = event.level().getBlockEntity(position);
            if (entity instanceof TileTransductionAmplifier amplifier && amplifier.isActivated()
                    && !amplifier.isRemoved()
                    && amplifier.getBlockState().getValue(BlockTransductionAmplifier.FACING) == direction.getOpposite()) {
                amplifiers++;
            }
        }
        if (amplifiers == 0) return;
        int bonus = amplifiers * 10;
        var output = new LinkedHashMap<>(event.output().amounts());
        output.replaceAll((aspect, amount) -> Math.addExact(amount, bonus));
        event.setOutput(new AspectAmounts(output));
    }
}
