// Reports the client-side vat state, which is what the corpse renderer reads.
var mc = net.minecraft.client.Minecraft.getInstance();
var done = new java.util.concurrent.CompletableFuture<String>();
mc.execute(() -> {
    try {
        var pos = new net.minecraft.core.BlockPos(64, 300, 64);
        var be = mc.level.getBlockEntity(pos);
        if (be == null) {
            done.complete("no client BE at " + pos);
            return;
        }
        var vat = (fr.wind_blade.isorropia.common.tiles.TileVat) be;
        done.complete("client mode=" + vat.getMode()
                + " effigy=" + vat.hasEffigy()
                + " essentia=" + vat.getEffigyEssentia()
                + " selfInfusing=" + vat.isSelfInfusing()
                + " block=" + mc.level.getBlockState(pos));
    } catch (Throwable t) {
        done.complete("FAILED " + t);
    }
});
return done.get(20, java.util.concurrent.TimeUnit.SECONDS);
