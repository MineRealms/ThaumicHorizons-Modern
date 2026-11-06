var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var sb = new StringBuilder();
var pos = new net.minecraft.core.BlockPos(64, 297, 68);
var be = level.getBlockEntity(pos);
sb.append("be=").append(be != null);
if (be instanceof fr.wind_blade.isorropia.common.tiles.TileBloodInfuser inf) {
    sb.append(" mode=").append(inf.getMode());
    sb.append(" syringe=").append(inf.syringe().getItem()).append("x").append(inf.syringe().getCount());
    sb.append(" out0=").append(inf.output(0).getItem()).append("x").append(inf.output(0).getCount());
    sb.append(" acquired=").append(inf.acquired());
    sb.append(" effects=").append(inf.getCurrentEffects().size());
}
sb.append(" tick=").append(level.getGameTime());
return sb.toString();