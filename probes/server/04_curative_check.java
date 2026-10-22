// Did the vat actually pull victus out of the jar and use it to heal the pig?
StringBuilder sb = new StringBuilder();
var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
var level = server.overworld();
var parts = new String(java.nio.file.Files.readAllBytes(new java.io.File("rosetta_probes/vat_origin.txt").toPath())).trim().split(",");
var master = new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
var vat = (fr.wind_blade.isorropia.common.tiles.TileVat) level.getBlockEntity(master);
var contained = vat.getContained();
sb.append("contained=").append(contained == null ? "NONE" : contained.getType() + " hp=" + contained.getHealth() + "/" + contained.getMaxHealth());
var jar = level.getBlockEntity(master.offset(0, -3, 3));
if (jar instanceof dev.tc4port.thaumcraft.api.essentia.EssentiaJarView view) {
    sb.append("\njarAspects=").append(view.visibleAspects().amounts());
}
sb.append("\nvatStored=").append(vat.visibleAspects().amounts());
sb.append("\nvatMode=").append(vat.getMode()).append(" stability=").append(vat.getStability()).append(" flux=").append(vat.getFluxStocked());
return sb.toString();
