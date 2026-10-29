// Places both dynamos in their running state on the client, so the ring lift and the lightning sheet can be seen.
var mc = net.minecraft.client.Minecraft.getInstance();
var done = new java.util.concurrent.CompletableFuture<String>();
mc.execute(() -> {
    try {
        var level = mc.level;
        var sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            var pos = new net.minecraft.core.BlockPos(48 + i * 3, 301, 74);
            var block = i == 0
                    ? fr.wind_blade.isorropia.common.blocks.BlocksIS.VIS_DYNAMO.get()
                    : fr.wind_blade.isorropia.common.blocks.BlocksIS.ESSENTIA_DYNAMO.get();
            level.setBlock(pos, block.defaultBlockState(), 3);
            var be = level.getBlockEntity(pos);
            if (be == null) {
                sb.append("no-be ");
                continue;
            }
            var shown = be.getClass().getDeclaredField(i == 0 ? "shownSupplying" : "shownPrimed");
            shown.setAccessible(true);
            shown.set(be, true);
            var motionField = be.getClass().getDeclaredField("motion");
            motionField.setAccessible(true);
            var motion = motionField.get(be);
            for (var entry : java.util.Map.of("rise", (Object) 0.3F, "lastRise", 0.3F, "rotation", 45.0F,
                    "lastRotation", 45.0F, "tumble", 30.0F, "lastTumble", 30.0F).entrySet()) {
                var f = motion.getClass().getDeclaredField(entry.getKey());
                f.setAccessible(true);
                f.set(motion, entry.getValue());
            }
            sb.append(be.getClass().getSimpleName()).append("=ok ");
        }
        done.complete(sb.toString());
    } catch (Throwable t) {
        done.complete("FAILED " + t);
    }
});
return done.get(20, java.util.concurrent.TimeUnit.SECONDS);
