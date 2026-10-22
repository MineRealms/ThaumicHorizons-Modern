// Feed the vat the way Thaumcraft does: jar -> tube -> tube -> the vat's connector. A jar only gives through
// its top face. Every block sits OUTSIDE the vat shell: driving a tube through a vat block is a break, and
// breaking any vat block takes the whole vat apart.
StringBuilder sb = new StringBuilder();
var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
var level = server.overworld();
var parts = new String(java.nio.file.Files.readAllBytes(new java.io.File("rosetta_probes/vat_origin.txt").toPath())).trim().split(",");
var master = new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
var vat = (fr.wind_blade.isorropia.common.tiles.TileVat) level.getBlockEntity(master);
if (vat == null || !vat.isMaster()) {
    return "no vat master at " + master + " (run 02 first)";
}
var tubeBlock = dev.tc4port.thaumcraft.api.ThaumcraftContent.block("essentia_tube");
var jarBlock = dev.tc4port.thaumcraft.api.ThaumcraftContent.block("warded_jar");
var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
level.setChunkForced(master.getX() >> 4, master.getZ() >> 4, true);
// The connector on the vat's lower ring, and the plumbing leading to it from outside the shell. A warded jar
// only gives through its top face, so the first tube stands directly on the jar.
var connector = master.offset(0, -2, 1);
var tubeNear = master.offset(0, -2, 2);
var tubeFar = master.offset(0, -2, 3);
var jarPos = master.offset(0, -3, 3);
for (var pos : new net.minecraft.core.BlockPos[]{tubeNear, tubeFar, jarPos, master.offset(0, -3, 2), master.offset(0, -1, 2), master.offset(0, 0, 2), master.offset(0, 0, 3)}) {
    level.setBlockAndUpdate(pos, air);
}
level.setBlockAndUpdate(tubeNear, tubeBlock.defaultBlockState());
level.setBlockAndUpdate(tubeFar, tubeBlock.defaultBlockState());
level.setBlockAndUpdate(jarPos, jarBlock.defaultBlockState());
long filled = 0;
if (level.getBlockEntity(jarPos) instanceof dev.tc4port.thaumcraft.api.essentia.EssentiaTransport jar) {
    filled = jar.addEssentia(fr.wind_blade.isorropia.common.IsorropiaAspects.VICTUS, 32,
            net.minecraft.core.Direction.UP, dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.EXECUTE);
}
sb.append("connector=").append(level.getBlockState(connector).getBlock())
  .append(" jar=").append(jarPos).append(" filled=").append(filled)
  .append(" tubes=").append(tubeFar).append(",").append(tubeNear);

// A hurt pig and the vat in idle mode so the cure path runs.
var pig = vat.getContained();
if (pig == null || !pig.isAlive()) {
    if (pig != null) {
        vat.releaseContained();
    }
    pig = net.minecraft.world.entity.EntityType.PIG.create(level);
    pig.moveTo(master.getX() + 0.5, master.getY() - 1.6, master.getZ() + 0.5, 0.0F, 0.0F);
    level.addFreshEntity(pig);
    vat.setContained(pig);
}
vat.setHasEffigy(false);
vat.abortInfusion();
pig.setHealth(5.0F);
sb.append("\npig=").append(pig.getId()).append(" hp=").append(pig.getHealth())
  .append(" mode=").append(vat.getMode()).append(" demanded=").append(vat.visibleAspects().amounts());
return sb.toString();
