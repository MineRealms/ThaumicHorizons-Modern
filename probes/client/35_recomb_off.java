// Reads the recombinator back from the client: its renderer hangs the lightning ring and the beam off isActivated().
var mc = net.minecraft.client.Minecraft.getInstance();
var done = new java.util.concurrent.CompletableFuture<String>();
mc.execute(() -> {
    try {
        var pos = new net.minecraft.core.BlockPos(54, 300, 64);
        var be = mc.level.getBlockEntity(pos);
        if (be instanceof fr.wind_blade.isorropia.common.tiles.TileRecombinator recomb) {
            done.complete("client activated=" + recomb.isActivated() + " count=" + recomb.count());
        } else {
            done.complete("client " + (be == null ? "missing" : be.getClass().getSimpleName()));
        }
    } catch (Throwable t) {
        done.complete("FAILED " + t);
    }
});
return done.get(20, java.util.concurrent.TimeUnit.SECONDS);
