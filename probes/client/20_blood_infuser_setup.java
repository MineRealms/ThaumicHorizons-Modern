var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var player = server.getPlayerList().getPlayer(mc.player.getUUID());
var sb = new StringBuilder();
var base = player.blockPosition().offset(3, 0, 0);
for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++)
    level.setBlockAndUpdate(base.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
level.setBlockAndUpdate(base.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
var block = fr.wind_blade.isorropia.common.blocks.BlocksIS.BLOOD_INFUSER.get();
sb.append("placed=").append(level.setBlockAndUpdate(base, block.defaultBlockState()));
var be = level.getBlockEntity(base);
sb.append(" be=").append(be != null).append(":").append(be == null ? "-" : be.getClass().getSimpleName());
if (be instanceof fr.wind_blade.isorropia.common.tiles.TileBloodInfuser inf) {
    inf.container().setItem(0, new net.minecraft.world.item.ItemStack(fr.wind_blade.isorropia.common.items.ItemsIS.SYRINGE_BLOOD.get()));
    inf.setMode(1);
    var aspect = dev.tc4port.thaumcraft.api.aspect.AspectId.parse("thaumcraft:ignis");
    sb.append(" selected=").append(inf.addSelected(aspect));
    sb.append(" acquired=").append(inf.addEssentia(aspect, 2, net.minecraft.core.Direction.NORTH, dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.EXECUTE));
    sb.append(" mode=").append(inf.getMode());
}
sb.append(" pos=").append(base.toShortString());
return sb.toString();