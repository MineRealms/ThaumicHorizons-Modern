// Switches the recombinator on the way redstone would, and reports the server side so the client reading in 36 can be
// compared against it. The tile flips activated on its own tick and syncs from there, which is the path being tested.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var pos = new net.minecraft.core.BlockPos(54, 300, 64);
var recomb = (fr.wind_blade.isorropia.common.tiles.TileRecombinator) level.getBlockEntity(pos);
recomb.setShouldActivate(true);
return "server shouldActivate=true activated=" + recomb.isActivated();
