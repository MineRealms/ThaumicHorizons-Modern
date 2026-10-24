// Forms the vat only, so the effigy can be added in a second call and the client's sync observed.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var master = new net.minecraft.core.BlockPos(64, 300, 64);
level.setChunkForced(master.getX() >> 4, master.getZ() >> 4, true);
var box = new net.minecraft.world.phys.AABB(master).inflate(12, 8, 12);
for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
    entity.discard();
}
fr.wind_blade.isorropia.common.vat.VatStructure.form(level, master);
var vat = (fr.wind_blade.isorropia.common.tiles.TileVat) level.getBlockEntity(master);
vat.abortInfusion();
vat.setHasEffigy(false);
return "formed mode=" + vat.getMode() + " effigy=" + vat.hasEffigy();
