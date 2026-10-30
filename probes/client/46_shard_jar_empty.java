// Edge case: capture a shard that was never fed any essence. The jar must not turn into an empty node that
// regenerates as a free full node when placed, and the shell must still be consumed.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var player = server.getPlayerList().getPlayer(mc.player.getUUID());
var sb = new StringBuilder();

var anchor = new net.minecraft.core.BlockPos(232, 311, 160);
var shardPos = anchor.offset(1, 0, 1);
var box = new net.minecraft.world.phys.AABB(anchor).inflate(8);
for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) e.discard();
for (int x = -2; x <= 4; x++) {
    for (int y = -2; y <= 6; y++) {
        for (int z = -2; z <= 4; z++) {
            level.setBlock(anchor.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
var glass = net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState();
var slab = net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState();
for (int x = 0; x < 3; x++) {
    for (int z = 0; z < 3; z++) {
        level.setBlock(anchor.offset(x, 2, z), slab, 3);
        level.setBlock(anchor.offset(x, 1, z), glass, 3);
        level.setBlock(anchor.offset(x, 0, z), glass, 3);
        level.setBlock(anchor.offset(x, -1, z), glass, 3);
    }
}
level.setBlock(shardPos, fr.wind_blade.isorropia.common.blocks.BlocksIS.ETHEREAL_SHARD.get().defaultBlockState(), 3);
var wand = dev.tc4port.thaumcraft.api.wand.WandApi.createFullyCharged(
        dev.tc4port.thaumcraft.api.wand.WandMaterialId.parse("silverwood"),
        dev.tc4port.thaumcraft.api.wand.WandMaterialId.parse("thaumium"));
player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
var clicked = anchor.offset(0, 1, 0);
var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(clicked),
        net.minecraft.core.Direction.UP, clicked, false);
sb.append("fits=").append(dev.tc4port.thaumcraft.block.NodeJarBlock.fits(level, anchor));
sb.append(" result=").append(dev.tc4port.thaumcraft.block.NodeJarBlock.tryForm(
        new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit)));
sb.append(" block=").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
        level.getBlockState(shardPos).getBlock()));
if (level.getBlockEntity(shardPos) instanceof dev.tc4port.thaumcraft.block.entity.NodeJarBlockEntity jar) {
    sb.append(" jarVis=").append(jar.nodeState().currentVis().amounts());
    sb.append(" jarId=").append(jar.nodeState().instanceId().isPresent());
}
sb.append(" shellGone=").append(level.getBlockState(anchor.offset(0, 2, 0)).isAir());
return sb.toString();
