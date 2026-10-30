// Second half of the shard jar check: after a charged shard is captured, release the jar with a wand and read
// the node back. This is the round trip a player actually performs when moving a shard.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var player = server.getPlayerList().getPlayer(mc.player.getUUID());
var sb = new StringBuilder();

var anchor = new net.minecraft.core.BlockPos(232, 311, 148);
var shardPos = anchor.offset(1, 0, 1);
var box = new net.minecraft.world.phys.AABB(anchor).inflate(8);
for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) e.discard();
if (!(level.getBlockEntity(shardPos) instanceof dev.tc4port.thaumcraft.block.entity.NodeJarBlockEntity jar)) {
    return "FAILED no jar at " + shardPos + " block=" + level.getBlockState(shardPos).getBlock();
}
var aer = dev.tc4port.thaumcraft.api.aspect.VisChannel.AER.aspectId();
sb.append("before=").append(jar.nodeState().currentVis().amount(aer));
dev.tc4port.thaumcraft.block.NodeJarBlock.releaseNode(level, shardPos, jar);
sb.append(" block=").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
        level.getBlockState(shardPos).getBlock()));
if (level.getBlockEntity(shardPos) instanceof dev.tc4port.thaumcraft.block.entity.AuraNodeBlockEntity node) {
    sb.append(" nodeAer=").append(node.nodeState().currentVis().amount(aer));
    sb.append(" nodeBase=").append(node.nodeState().baseVis().amount(aer));
    sb.append(" nodeType=").append(node.nodeState().type());
    sb.append(" id=").append(node.nodeState().instanceId().isPresent());
}
sb.append(" canTap=").append(dev.tc4port.thaumcraft.api.node.NodeApi.view(level, shardPos) != null);
return sb.toString();
