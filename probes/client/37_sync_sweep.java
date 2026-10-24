// Places one of each synced machine and pushes its update tag, so any tile whose getUpdateTag cannot be built from a
// freshly placed block shows up as an exception instead of killing a server tick later. Every one of them now sends
// through TileSync, which builds the tag on demand.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var blocks = new java.util.LinkedHashMap<String, net.minecraft.world.level.block.Block>();
blocks.put("jar_soul", fr.wind_blade.isorropia.common.blocks.BlocksIS.JAR_SOUL.get());
blocks.put("portal", fr.wind_blade.isorropia.common.blocks.BlocksIS.PORTAL.get());
blocks.put("recombinator", fr.wind_blade.isorropia.common.blocks.BlocksIS.RECOMBINATOR.get());
blocks.put("keystone_receptacle", fr.wind_blade.isorropia.common.blocks.BlocksIS.SLOT.get());
blocks.put("blood_infuser", fr.wind_blade.isorropia.common.blocks.BlocksIS.BLOOD_INFUSER.get());
blocks.put("ethereal_shard", fr.wind_blade.isorropia.common.blocks.BlocksIS.ETHEREAL_SHARD.get());
blocks.put("transduction_amplifier", fr.wind_blade.isorropia.common.blocks.BlocksIS.TRANSDUCTION_AMPLIFIER.get());
blocks.put("vortex_stabilizer", fr.wind_blade.isorropia.common.blocks.BlocksIS.VORTEX_STABILIZER.get());
blocks.put("vis_dynamo", fr.wind_blade.isorropia.common.blocks.BlocksIS.VIS_DYNAMO.get());
blocks.put("essentia_dynamo", fr.wind_blade.isorropia.common.blocks.BlocksIS.ESSENTIA_DYNAMO.get());
var sb = new StringBuilder();
int x = 40;
for (var entry : blocks.entrySet()) {
    var pos = new net.minecraft.core.BlockPos(x, 300, 70);
    level.setChunkForced(x >> 4, 70 >> 4, true);
    try {
        level.setBlockAndUpdate(pos, entry.getValue().defaultBlockState());
        var be = level.getBlockEntity(pos);
        if (be == null) {
            sb.append(entry.getKey()).append("=no-be ");
        } else {
            var tag = be.getUpdateTag();
            fr.wind_blade.isorropia.common.tiles.TileSync.toWatchers(be);
            sb.append(entry.getKey()).append("=ok(").append(tag.size()).append(") ");
        }
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    } catch (Throwable t) {
        sb.append(entry.getKey()).append("=FAILED ").append(t).append(' ');
    }
    x += 2;
}
return sb.toString();
