// Node jar capture of an ethereal shard: build TC4's glass-and-slab shell around a charged shard, run the real
// wand trigger, and read back what the jar holds and whether the shell was consumed.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var player = server.getPlayerList().getPlayer(mc.player.getUUID());
var sb = new StringBuilder();

var anchor = new net.minecraft.core.BlockPos(232, 311, 148);
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
if (!(level.getBlockEntity(shardPos) instanceof fr.wind_blade.isorropia.common.tiles.TileEtherealShard shard)) {
    return "FAILED no shard block entity";
}
var aer = dev.tc4port.thaumcraft.api.aspect.VisChannel.AER.aspectId();
var essence = dev.tc4port.thaumcraft.api.item.ThaumcraftItemApi.createWispEssence(
        dev.tc4port.thaumcraft.api.aspect.AspectAmounts.of(aer, 2));
essence.setCount(3);
shard.addEssence(essence);
shard.addToStore(aer, 5);
sb.append("capacity=").append(shard.capacity(aer)).append(" stored=").append(shard.stored(aer));
sb.append(" inTag=").append(level.getBlockState(shardPos).is(dev.tc4port.thaumcraft.api.ThaumcraftTags.NODE_JAR_HOSTS));
sb.append(" nodeView=").append(dev.tc4port.thaumcraft.api.node.NodeApi.view(level, shardPos) != null);
sb.append(" fits=").append(dev.tc4port.thaumcraft.block.NodeJarBlock.fits(level, anchor));

var wand = dev.tc4port.thaumcraft.api.wand.WandApi.createFullyCharged(
        dev.tc4port.thaumcraft.api.wand.WandMaterialId.parse("silverwood"),
        dev.tc4port.thaumcraft.api.wand.WandMaterialId.parse("thaumium"));
player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
var granted = dev.tc4port.thaumcraft.api.research.ResearchApi.complete(player,
        dev.tc4port.thaumcraft.api.research.ResearchKey.parse("NODEJAR"),
        new dev.tc4port.thaumcraft.api.research.ResearchCompletionContext(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("isorropia", "probe"),
                dev.tc4port.thaumcraft.api.research.ResearchCompletionReason.ADMIN_COMMAND));
sb.append(" research=").append(granted);
var clicked = anchor.offset(0, 1, 0);
var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(clicked),
        net.minecraft.core.Direction.UP, clicked, false);
var result = dev.tc4port.thaumcraft.block.NodeJarBlock.tryForm(
        new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
sb.append(" result=").append(result);
sb.append(" block=").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
        level.getBlockState(shardPos).getBlock()));
if (level.getBlockEntity(shardPos) instanceof dev.tc4port.thaumcraft.block.entity.NodeJarBlockEntity jar) {
    var state = jar.nodeState();
    sb.append(" jarAer=").append(state.currentVis().amount(aer));
    sb.append(" jarBase=").append(state.baseVis().amount(aer));
    sb.append(" jarId=").append(state.instanceId().isPresent());
}
sb.append(" shellGone=").append(level.getBlockState(anchor.offset(0, 2, 0)).isAir());
int drops = 0;
for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
    drops += e.getItem().getCount();
    e.discard();
}
sb.append(" drops=").append(drops);
return sb.toString();
