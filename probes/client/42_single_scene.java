// Clears the earlier rows and leaves one transducer and one attenuator, both facing south, for a clean close-up.
var mc = net.minecraft.client.Minecraft.getInstance();
var done = new java.util.concurrent.CompletableFuture<String>();
mc.execute(() -> {
    try {
        var level = mc.level;
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var stone = net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = 38; x <= 62; x++) {
            for (int z = 60; z <= 76; z++) {
                level.setBlock(new net.minecraft.core.BlockPos(x, 301, z), air, 3);
                level.setBlock(new net.minecraft.core.BlockPos(x, 300, z), stone, 3);
            }
        }
        var ampPos = new net.minecraft.core.BlockPos(50, 301, 66);
        level.setBlock(ampPos, fr.wind_blade.isorropia.common.blocks.BlocksIS.TRANSDUCTION_AMPLIFIER.get()
                .defaultBlockState().setValue(fr.wind_blade.isorropia.common.blocks.BlockTransductionAmplifier.FACING,
                        net.minecraft.core.Direction.SOUTH), 3);
        if (level.getBlockEntity(ampPos) instanceof fr.wind_blade.isorropia.common.tiles.TileTransductionAmplifier tile) {
            for (var entry : java.util.Map.of("activated", (Object) true, "count", 50, "status", 1,
                    "facing", net.minecraft.core.Direction.SOUTH, "animationTicks", 17).entrySet()) {
                var f = tile.getClass().getDeclaredField(entry.getKey());
                f.setAccessible(true);
                f.set(tile, entry.getValue());
            }
        }
        var stabPos = new net.minecraft.core.BlockPos(50, 301, 70);
        level.setBlock(stabPos, fr.wind_blade.isorropia.common.blocks.BlocksIS.VORTEX_STABILIZER.get()
                .defaultBlockState().setValue(fr.wind_blade.isorropia.common.blocks.BlockVortexStabilizer.FACING,
                        net.minecraft.core.Direction.SOUTH), 3);
        done.complete("single scene ready");
    } catch (Throwable t) {
        done.complete("FAILED " + t);
    }
});
return done.get(20, java.util.concurrent.TimeUnit.SECONDS);
