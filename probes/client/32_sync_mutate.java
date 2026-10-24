// Fills the jar with a pig and points the portal at the nether, after the client already holds both block entities.
// Neither call changes a block state, so this is exactly the case the old sendBlockUpdated idiom dropped.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var jarPos = new net.minecraft.core.BlockPos(58, 300, 64);
var portalPos = new net.minecraft.core.BlockPos(56, 300, 64);
var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
var tag = new net.minecraft.nbt.CompoundTag();
pig.saveWithoutId(tag);
tag.putString("id", "minecraft:pig");
var jar = (fr.wind_blade.isorropia.common.tiles.TileJarSoul) level.getBlockEntity(jarPos);
jar.setEntityData(tag);
var portal = (fr.wind_blade.isorropia.common.tiles.TilePortal) level.getBlockEntity(portalPos);
portal.link(net.minecraft.world.level.Level.NETHER, portalPos);
return "filled jar bytes=" + jar.getEntityData().size() + " portal=" + portal.destination().location();
