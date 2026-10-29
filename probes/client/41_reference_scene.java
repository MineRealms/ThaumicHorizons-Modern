// Places Thaumcraft's own node transducer and node stabilizer beside the port's machines, so the screenshot shows
// whether a transform is off relative to the reference implementation.
var mc = net.minecraft.client.Minecraft.getInstance();
var done = new java.util.concurrent.CompletableFuture<String>();
mc.execute(() -> {
    try {
        var level = mc.level;
        level.setBlock(new net.minecraft.core.BlockPos(60, 301, 66),
                dev.tc4port.thaumcraft.registry.TCBlocks.NODE_TRANSDUCER.get().defaultBlockState(), 3);
        level.setBlock(new net.minecraft.core.BlockPos(62, 301, 66),
                dev.tc4port.thaumcraft.registry.TCBlocks.NODE_STABILIZER.get().defaultBlockState(), 3);
        done.complete("reference placed");
    } catch (Throwable t) {
        done.complete("FAILED " + t);
    }
});
return done.get(20, java.util.concurrent.TimeUnit.SECONDS);
