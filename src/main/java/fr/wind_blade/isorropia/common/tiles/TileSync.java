package fr.wind_blade.isorropia.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Hands a block entity's synced tag to the players watching it.
 *
 * <p>This exists because the obvious call does not do the job. {@code Level#sendBlockUpdated(pos, state, state,
 * UPDATE_CLIENTS)} with the state the block already has only queues the position for the chunk's block broadcast
 * ({@code ServerLevel#sendBlockUpdated} forwards to {@code getChunkSource().blockChanged}), and that broadcast carries
 * block entity data solely when the block state itself changed. A machine whose renderer depends on a synced field —
 * the soul in a jar, the glow of a dynamo, a beam — therefore kept drawing whatever the chunk was loaded with, and
 * only a reload put it right. The update packet is sent here instead, which is what the broadcast would have sent.</p>
 */
public final class TileSync {
    private TileSync() {
    }

    /** Sends {@code entity}'s update packet to everyone tracking its chunk. Does nothing on the client. */
    public static void toWatchers(BlockEntity entity) {
        Level level = entity.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(entity);
        BlockPos pos = entity.getBlockPos();
        for (ServerPlayer watcher : server.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false)) {
            watcher.connection.send(packet);
        }
    }
}
