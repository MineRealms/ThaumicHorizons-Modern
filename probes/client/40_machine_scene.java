// Lays out the node machines the way a player would meet them, on the client only, and forces the states their
// renderers read so one screenshot shows every facing and both the idle and the running look.
var mc = net.minecraft.client.Minecraft.getInstance();
var done = new java.util.concurrent.CompletableFuture<String>();
mc.execute(() -> {
    try {
        var level = mc.level;
        var stone = net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = 38; x <= 62; x++) {
            for (int z = 62; z <= 74; z++) {
                level.setBlock(new net.minecraft.core.BlockPos(x, 300, z), stone, 3);
            }
        }
        var sb = new StringBuilder();
        var dirs = new net.minecraft.core.Direction[] {
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST };
        var ampBlock = fr.wind_blade.isorropia.common.blocks.BlocksIS.TRANSDUCTION_AMPLIFIER.get();
        var stabBlock = fr.wind_blade.isorropia.common.blocks.BlocksIS.VORTEX_STABILIZER.get();
        var monBlock = fr.wind_blade.isorropia.common.blocks.BlocksIS.NODE_MONITOR.get();
        for (int i = 0; i < 4; i++) {
            var dir = dirs[i];
            var ampPos = new net.minecraft.core.BlockPos(42 + i * 3, 301, 66);
            level.setBlock(ampPos, ampBlock.defaultBlockState()
                    .setValue(fr.wind_blade.isorropia.common.blocks.BlockTransductionAmplifier.FACING, dir), 3);
            var amp = level.getBlockEntity(ampPos);
            if (amp instanceof fr.wind_blade.isorropia.common.tiles.TileTransductionAmplifier tile) {
                for (var entry : java.util.Map.of("activated", (Object) true, "count", 50, "status", 1,
                        "facing", dir, "animationTicks", 17).entrySet()) {
                    var f = tile.getClass().getDeclaredField(entry.getKey());
                    f.setAccessible(true);
                    f.set(tile, entry.getValue());
                }
                sb.append("amp").append(dir).append("=ok ");
            }
            var stabPos = new net.minecraft.core.BlockPos(42 + i * 3, 301, 70);
            level.setBlock(stabPos, stabBlock.defaultBlockState()
                    .setValue(fr.wind_blade.isorropia.common.blocks.BlockVortexStabilizer.FACING, dir), 3);
            var stab = level.getBlockEntity(stabPos);
            if (stab instanceof fr.wind_blade.isorropia.common.tiles.TileVortexStabilizer tile) {
                var f = tile.getClass().getDeclaredField("redstoned");
                f.setAccessible(true);
                f.set(tile, true);
                sb.append("stab").append(dir).append("=ok ");
            }
            var monPos = new net.minecraft.core.BlockPos(42 + i * 3, 301, 74);
            level.setBlock(monPos, monBlock.defaultBlockState()
                    .setValue(fr.wind_blade.isorropia.common.blocks.BlockNodeMonitor.FACING, dir), 3);
            sb.append("mon").append(dir).append("=").append(level.getBlockEntity(monPos) == null ? "no-be" : "ok").append(' ');
        }
        level.setBlock(new net.minecraft.core.BlockPos(56, 301, 66),
                fr.wind_blade.isorropia.common.blocks.BlocksIS.VIS_DYNAMO.get().defaultBlockState(), 3);
        level.setBlock(new net.minecraft.core.BlockPos(58, 301, 66),
                fr.wind_blade.isorropia.common.blocks.BlocksIS.ESSENTIA_DYNAMO.get().defaultBlockState(), 3);
        done.complete(sb.toString());
    } catch (Throwable t) {
        done.complete("FAILED " + t);
    }
});
return done.get(20, java.util.concurrent.TimeUnit.SECONDS);
