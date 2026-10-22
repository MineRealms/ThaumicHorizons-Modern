// The real click path a player takes, start to finish: climb in through the glass, out through the water at
// your feet, a creature in through a soul jar, ONE wand tap on the matrix, and the corpse effigy in and back.
// Every step goes through BlockState#use / Block#use, exactly as a player's packet would drive it.
StringBuilder sb = new StringBuilder();
var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
var level = server.overworld();
var parts = new String(java.nio.file.Files.readAllBytes(new java.io.File("rosetta_probes/vat_origin.txt").toPath())).trim().split(",");
var master = new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
var vat = (fr.wind_blade.isorropia.common.tiles.TileVat) level.getBlockEntity(master);
var state = level.getBlockState(master);
level.setChunkForced(master.getX() >> 4, master.getZ() >> 4, true);
if (vat == null) {
    return "no vat at " + master;
}
// Clean slate, and the matrix the player would have built on top.
if (vat.getContained() != null) {
    vat.releaseContained();
}
vat.abortInfusion();
vat.setActive(false);
vat.setHasEffigy(false);
level.setBlock(master.above(), fr.wind_blade.isorropia.common.blocks.BlocksIS.MODIFIED_MATRIX.get().defaultBlockState(), 3);

var fake = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
var context = new dev.tc4port.thaumcraft.api.research.ResearchCompletionContext(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("isorropia", "probe"),
        dev.tc4port.thaumcraft.api.research.ResearchCompletionReason.ADMIN_COMMAND);
for (String key : new String[]{"isorropia:creatureinfusions", "isorropia:curativevat", "isorropia:selfinfusion"}) {
    dev.tc4port.thaumcraft.api.research.ResearchApi.complete(fake,
            dev.tc4port.thaumcraft.api.research.ResearchKey.parse(key), context);
}

var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.entity.player.Player, String> click = (pos, who) -> {
    var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
            net.minecraft.core.Direction.UP, pos, false);
    return String.valueOf(level.getBlockState(pos).use(level, who, hand, hit));
};

// 1. Climb in through the glass: the matrix on top means the top construct cannot be clicked from outside.
var glass = master.offset(1, -1, 0);
fake.setItemInHand(hand, net.minecraft.world.item.ItemStack.EMPTY);
sb.append("glassClick=").append(click.apply(glass, fake));
sb.append(" heldByGlass=").append(vat.getContained() == fake).append(" mode=").append(vat.getMode());

// 2. Out through the water at the player's feet, as upstream let you.
var water = master.below();
sb.append("\nwaterClick=").append(click.apply(water, fake));
sb.append(" stillHeld=").append(vat.getContained() == fake)
  .append(" y=").append(fake.getY()).append(" expect=").append(master.getY() + 2);

// 3. A creature in through a soul jar, captured the way the containment focus does it.
var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
pig.moveTo(master.getX() + 0.5, master.getY() + 1.0, master.getZ() + 0.5, 0.0F, 0.0F);
level.addFreshEntity(pig);
fake.getInventory().add(new net.minecraft.world.item.ItemStack(dev.tc4port.thaumcraft.api.ThaumcraftContent.item("warded_jar")));
boolean captured = fr.wind_blade.isorropia.common.infusion.SoulJar.capture(fake, pig);
var jarStack = fake.getMainHandItem();
sb.append("\ncaptured=").append(captured).append(" jarHolds=")
  .append(fr.wind_blade.isorropia.common.infusion.SoulJar.peek(jarStack, level) != null);
sb.append(" jarClick=").append(click.apply(master, fake));
sb.append(" held=").append(vat.getContained() != null && vat.getContained().getType() == net.minecraft.world.entity.EntityType.PIG);

// 4. ONE wand tap on the matrix starts the infusion, as upstream's single click did.
var pedestalBlock = dev.tc4port.thaumcraft.api.ThaumcraftContent.block("arcane_pedestal");
var items = new net.minecraft.world.item.ItemStack[]{
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLISTERING_MELON_SLICE),
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLISTERING_MELON_SLICE),
        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE),
        new net.minecraft.world.item.ItemStack(dev.tc4port.thaumcraft.api.ThaumcraftContent.item("silverwood_log"))};
var pedestalPositions = new net.minecraft.core.BlockPos[]{
        master.offset(2, 0, 0), master.offset(-2, 0, 0), master.offset(0, 0, 2), master.offset(0, 0, -2)};
int placed = 0;
for (int i = 0; i < pedestalPositions.length; i++) {
    var pos = pedestalPositions[i];
    if (!level.getBlockState(pos).is(pedestalBlock)) {
        level.setBlockAndUpdate(pos, pedestalBlock.defaultBlockState());
    }
    if (level.getBlockEntity(pos) instanceof dev.tc4port.thaumcraft.api.machine.InfusionPedestal pedestal) {
        pedestal.setItem(0, items[i]);
        placed++;
    }
}
sb.append("\npedestals=").append(placed);
fake.setItemInHand(hand, dev.tc4port.thaumcraft.api.wand.WandApi.createFullyCharged(
        dev.tc4port.thaumcraft.api.wand.WandMaterialId.WOOD, dev.tc4port.thaumcraft.api.wand.WandMaterialId.IRON));
sb.append(" matrixClick=").append(click.apply(master.above(), fake));
sb.append(" infusing=").append(vat.isInfusing()).append(" recipe=").append(vat.getCreatureRecipe());

// A tap with nothing to infuse says why, and leaves the vat alone.
vat.abortInfusion();
for (var pos : pedestalPositions) {
    if (level.getBlockEntity(pos) instanceof dev.tc4port.thaumcraft.api.machine.InfusionPedestal pedestal) {
        pedestal.clearContent();
    }
}
sb.append("\nemptyTap=").append(click.apply(master.above(), fake)).append(" infusing=").append(vat.isInfusing());
for (int i = 0; i < pedestalPositions.length; i++) {
    if (level.getBlockEntity(pedestalPositions[i]) instanceof dev.tc4port.thaumcraft.api.machine.InfusionPedestal pedestal) {
        pedestal.setItem(0, items[i]);
    }
}
sb.append(" loadedTap=").append(click.apply(master.above(), fake)).append(" infusing=").append(vat.isInfusing());

// 5. Let the awakened-blood infusion run to the end, essentia fed the way a tube would.
var victus = fr.wind_blade.isorropia.common.IsorropiaAspects.VICTUS;
var cognitio = fr.wind_blade.isorropia.common.IsorropiaAspects.COGNITIO;
var subject = vat.getContained();
for (int round = 0; round < 60 && vat.isInfusing(); round++) {
    for (int i = 0; i < 8; i++) {
        vat.addEssentia(victus, 8, net.minecraft.core.Direction.UP, dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.EXECUTE);
        vat.addEssentia(cognitio, 8, net.minecraft.core.Direction.UP, dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.EXECUTE);
    }
    for (int tick = 0; tick < 20; tick++) {
        fr.wind_blade.isorropia.common.tiles.TileVat.serverTick(level, master, state, vat);
    }
}
var data = fr.wind_blade.isorropia.common.capabilities.LivingData.get(subject).orElse(null);
sb.append("\nfinished mode=").append(vat.getMode())
  .append(" organ(blood)=").append(data == null ? "NO CAPABILITY" : data.getOrgan("blood"))
  .append(" owner=").append(data != null && data.getOwner() != null);

// 6. The corpse effigy goes in, and an empty hand takes an unsoaked one back out.
if (vat.getContained() != null) {
    vat.releaseContained();
}
vat.setHasEffigy(false);
fake.setItemInHand(hand, new net.minecraft.world.item.ItemStack(fr.wind_blade.isorropia.common.items.ItemsIS.CORPSE_EFFIGY.get()));
sb.append("\neffigyClick=").append(click.apply(master, fake))
  .append(" hasEffigy=").append(vat.hasEffigy()).append(" mode=").append(vat.getMode())
  .append(" left=").append(fake.getMainHandItem().getCount());
fake.setItemInHand(hand, net.minecraft.world.item.ItemStack.EMPTY);
sb.append("\ntakeBackClick=").append(click.apply(master, fake))
  .append(" hasEffigy=").append(vat.hasEffigy()).append(" mode=").append(vat.getMode())
  .append(" back=").append(fake.getInventory().countItem(fr.wind_blade.isorropia.common.items.ItemsIS.CORPSE_EFFIGY.get()));

// Leave the scene as it was found: the vat formed, empty, with the matrix on top.
level.setBlock(master.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
return sb.toString();
