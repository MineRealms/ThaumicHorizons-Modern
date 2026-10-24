// Charges the effigy past its victus requirement so the vat reaches the ready state (mode 4).
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var master = new net.minecraft.core.BlockPos(64, 300, 64);
var vat = (fr.wind_blade.isorropia.common.tiles.TileVat) level.getBlockEntity(master);
if (vat == null) {
    return "no vat at " + master;
}
vat.abortInfusion();
vat.setHasEffigy(true);
var state = level.getBlockState(master);
for (int i = 0; i < 200; i++) {
    vat.addEssentia(fr.wind_blade.isorropia.common.IsorropiaAspects.VICTUS, 4, net.minecraft.core.Direction.UP,
            dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.EXECUTE);
    fr.wind_blade.isorropia.common.tiles.TileVat.serverTick(level, master, state, vat);
}
return "mode=" + vat.getMode() + " essentia=" + vat.getEffigyEssentia() + " soaked=" + vat.effigySoaked();
