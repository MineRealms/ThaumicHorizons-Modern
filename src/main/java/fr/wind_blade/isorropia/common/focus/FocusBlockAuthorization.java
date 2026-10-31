package fr.wind_blade.isorropia.common.focus;

import dev.tc4port.thaumcraft.api.focus.action.FocusAuthorization;
import dev.tc4port.thaumcraft.api.event.GameplayHooks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Validate this beam's affected blocks before its planned Vis debit. */
final class FocusBlockAuthorization {
    private FocusBlockAuthorization() { }

    static FocusAuthorization check(ServerPlayer player, List<BlockPos> positions, BlockPos center, int radius) {
        var level = player.serverLevel();
        var before = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius)))
            before.put(pos.immutable(), level.isLoaded(pos) ? level.getBlockState(pos) : null);
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos) || !player.mayBuild() || !level.mayInteract(player, pos))
                return FocusAuthorization.deny();
            var state = level.getBlockState(pos);
            if (GameplayHooks.fireBlockBreak(level, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()
                    || !level.isLoaded(pos) || level.getBlockState(pos) != state) return FocusAuthorization.deny();
        }
        if (player.serverLevel() != level || !player.isAlive() || player.isSpectator() || !player.mayBuild())
            return FocusAuthorization.deny();
        for (BlockPos pos : positions)
            if (!level.mayInteract(player, pos)) return FocusAuthorization.deny();
        // Every event has run: a later neighbour event must not change an earlier authorized target
        // or expose a new enlarged candidate for execution.
        for (var entry : before.entrySet()) {
            var state = level.isLoaded(entry.getKey()) ? level.getBlockState(entry.getKey()) : null;
            if (state != entry.getValue()) return FocusAuthorization.deny();
        }
        return FocusAuthorization.allow();
    }
}
