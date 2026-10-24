// Reads the jar and the portal back from the client, which is what their renderers see.
var mc = net.minecraft.client.Minecraft.getInstance();
var done = new java.util.concurrent.CompletableFuture<String>();
mc.execute(() -> {
    try {
        var jarPos = new net.minecraft.core.BlockPos(58, 300, 64);
        var portalPos = new net.minecraft.core.BlockPos(56, 300, 64);
        var sb = new StringBuilder();
        var jar = mc.level.getBlockEntity(jarPos);
        if (jar instanceof fr.wind_blade.isorropia.common.tiles.TileJarSoul soul) {
            sb.append("jar data=").append(soul.getEntityData() == null ? "none" : "set")
                    .append(" drawn=").append(soul.getDisplayed() == null ? "nothing" : "creature");
        } else {
            sb.append("jar=").append(jar == null ? "missing" : jar.getClass().getSimpleName());
        }
        sb.append(" | ");
        var portal = mc.level.getBlockEntity(portalPos);
        if (portal instanceof fr.wind_blade.isorropia.common.tiles.TilePortal gate) {
            sb.append("portal destination=").append(gate.destination());
        } else {
            sb.append("portal=").append(portal == null ? "missing" : portal.getClass().getSimpleName());
        }
        done.complete(sb.toString());
    } catch (Throwable t) {
        done.complete("FAILED " + t);
    }
});
return done.get(20, java.util.concurrent.TimeUnit.SECONDS);
