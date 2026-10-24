// Places an empty soul jar and an unlinked portal next to the vat, so the next probes can watch them being filled.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var jarPos = new net.minecraft.core.BlockPos(58, 300, 64);
var portalPos = new net.minecraft.core.BlockPos(56, 300, 64);
level.setChunkForced(jarPos.getX() >> 4, jarPos.getZ() >> 4, true);
level.setBlockAndUpdate(jarPos, fr.wind_blade.isorropia.common.blocks.BlocksIS.JAR_SOUL.get().defaultBlockState());
level.setBlockAndUpdate(portalPos, fr.wind_blade.isorropia.common.blocks.BlocksIS.PORTAL.get().defaultBlockState());
var jar = level.getBlockEntity(jarPos);
var portal = level.getBlockEntity(portalPos);
if (jar instanceof fr.wind_blade.isorropia.common.tiles.TileJarSoul soul) {
    soul.setEntityData(null);
}
if (portal instanceof fr.wind_blade.isorropia.common.tiles.TilePortal gate) {
    gate.link(null, portalPos);
}
return "placed jar=" + (jar == null ? "null" : jar.getClass().getSimpleName())
        + " portal=" + (portal == null ? "null" : portal.getClass().getSimpleName());
