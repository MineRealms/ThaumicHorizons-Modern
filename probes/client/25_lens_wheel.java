// Wears the goggles, hands the player every lens, and opens the wheel for a screenshot.
var mc = net.minecraft.client.Minecraft.getInstance();
var server = mc.getSingleplayerServer();
var level = server.overworld();
var player = server.getPlayerList().getPlayer(mc.player.getUUID());
player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
        new net.minecraft.world.item.ItemStack(dev.tc4port.thaumcraft.api.ThaumcraftContent.item("goggles_of_revealing")));
for (var item : new net.minecraft.world.item.Item[]{
        fr.wind_blade.isorropia.common.items.ItemsIS.AIR_LENS.get(),
        fr.wind_blade.isorropia.common.items.ItemsIS.FIRE_LENS.get(),
        fr.wind_blade.isorropia.common.items.ItemsIS.ORDO_LENS.get(),
        fr.wind_blade.isorropia.common.items.ItemsIS.ENVY_LENS.get()}) {
    player.getInventory().add(new net.minecraft.world.item.ItemStack(item));
}
// Fit the fire lens through the same call the packet uses, then check it took.
var fire = fr.wind_blade.isorropia.common.lenses.Lenses.FIRE;
int slot = -1;
for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
    if (player.getInventory().getItem(i).is(fr.wind_blade.isorropia.common.items.ItemsIS.FIRE_LENS.get())) {
        slot = i;
        break;
    }
}
boolean fitted = fr.wind_blade.isorropia.common.lenses.LensManager.fit(player,
        fr.wind_blade.isorropia.common.lenses.Lens.Slot.LEFT, fire, player.getInventory().getItem(slot));
var goggles = fr.wind_blade.isorropia.common.lenses.LensManager.goggles(player);
var sb = new StringBuilder("fitted=" + fitted + " goggles=" + !goggles.isEmpty()
        + " tags=" + (goggles.getTag() == null ? "none" : goggles.getTag().toString()));
// The wheel is opened on the client thread, and the screenshot happens in a later call.
mc.execute(() -> {
    mc.options.hideGui = false;
    fr.wind_blade.isorropia.client.hud.LensWheel.open(false, false);
});
sb.append(" wheelOpen=").append(fr.wind_blade.isorropia.client.hud.LensWheel.isOpen());
return sb.toString();
