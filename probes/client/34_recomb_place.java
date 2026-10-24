// Places a recombinator with no redstone on it, so the client should see it switched off.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var pos = new net.minecraft.core.BlockPos(54, 300, 64);
level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
level.setBlockAndUpdate(pos, fr.wind_blade.isorropia.common.blocks.BlocksIS.RECOMBINATOR.get().defaultBlockState());
var be = level.getBlockEntity(pos);
if (be instanceof fr.wind_blade.isorropia.common.tiles.TileRecombinator recomb) {
    recomb.setShouldActivate(false);
}
return "placed " + (be == null ? "null" : be.getClass().getSimpleName());
