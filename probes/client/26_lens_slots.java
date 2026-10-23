// Fits a lens to each eye, then swaps one, and checks the replaced lens came back instead of vanishing.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var player = server.getPlayerList().getPlayer(mc.player.getUUID());
player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
        new net.minecraft.world.item.ItemStack(dev.tc4port.thaumcraft.api.ThaumcraftContent.item("goggles_of_revealing")));
for (var item : new net.minecraft.world.item.Item[]{
        fr.wind_blade.isorropia.common.items.ItemsIS.AIR_LENS.get(),
        fr.wind_blade.isorropia.common.items.ItemsIS.FIRE_LENS.get(),
        fr.wind_blade.isorropia.common.items.ItemsIS.ORDO_LENS.get()}) {
    player.getInventory().add(new net.minecraft.world.item.ItemStack(item));
}
int fire = -1;
int air = -1;
int ordo = -1;
for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
    var stack = player.getInventory().getItem(i);
    if (stack.is(fr.wind_blade.isorropia.common.items.ItemsIS.FIRE_LENS.get())) fire = i;
    if (stack.is(fr.wind_blade.isorropia.common.items.ItemsIS.AIR_LENS.get())) air = i;
    if (stack.is(fr.wind_blade.isorropia.common.items.ItemsIS.ORDO_LENS.get())) ordo = i;
}
boolean left = fr.wind_blade.isorropia.common.lenses.LensManager.fit(player,
        fr.wind_blade.isorropia.common.lenses.Lens.Slot.LEFT,
        fr.wind_blade.isorropia.common.lenses.Lenses.FIRE, player.getInventory().getItem(fire));
boolean right = fr.wind_blade.isorropia.common.lenses.LensManager.fit(player,
        fr.wind_blade.isorropia.common.lenses.Lens.Slot.RIGHT,
        fr.wind_blade.isorropia.common.lenses.Lenses.AIR, player.getInventory().getItem(air));
// Swapping the left lens must hand the fire lens back, not eat it.
boolean swapped = fr.wind_blade.isorropia.common.lenses.LensManager.fit(player,
        fr.wind_blade.isorropia.common.lenses.Lens.Slot.LEFT,
        fr.wind_blade.isorropia.common.lenses.Lenses.ORDO, player.getInventory().getItem(ordo));
int fireBack = 0;
for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
    if (player.getInventory().getItem(i).is(fr.wind_blade.isorropia.common.items.ItemsIS.FIRE_LENS.get())) fireBack++;
}
var goggles = fr.wind_blade.isorropia.common.lenses.LensManager.goggles(player);
var leftLens = fr.wind_blade.isorropia.common.lenses.Lens.read(goggles,
        fr.wind_blade.isorropia.common.lenses.Lens.Slot.LEFT);
var rightLens = fr.wind_blade.isorropia.common.lenses.Lens.read(goggles,
        fr.wind_blade.isorropia.common.lenses.Lens.Slot.RIGHT);
return "left=" + left + " right=" + right + " swapped=" + swapped
        + " leftNow=" + (leftLens == null ? "none" : leftLens.id().getPath())
        + " rightNow=" + (rightLens == null ? "none" : rightLens.id().getPath())
        + " fireBack=" + fireBack;
