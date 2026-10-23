package fr.wind_blade.isorropia.common.lenses;

import dev.tc4port.thaumcraft.api.item.GogglesOverlayGear;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.items.ItemsIS;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The lenses a player is wearing. They live in the revealing goggles' own tag, one per slot, which is how the
 * original kept them: the goggles are the mount, and a lens is useless without them.
 */
public final class LensManager {
    /** The goggles the lenses fit into. */
    private static final ResourceLocation GOGGLES = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "goggles_of_revealing");
    private static final ResourceLocation RUNIC_GOGGLES = ResourceLocation.fromNamespaceAndPath("thaumcraft",
            "runic_goggles");

    private LensManager() {
    }

    /** The goggles the player is wearing, if any. */
    public static ItemStack goggles(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        return isGoggles(helmet) ? helmet : ItemStack.EMPTY;
    }

    public static boolean isGoggles(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        //? if forge {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        //?} else {
        /*ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        *///?}
        if (GOGGLES.equals(id) || RUNIC_GOGGLES.equals(id)) {
            return true;
        }
        // Any other gear Thaumcraft treats as revealing is a valid mount too.
        return stack.getItem() instanceof GogglesOverlayGear;
    }

    /**
     * Fits a lens, which costs the lens item from the player's hands. Whatever was in the slot is handed back
     * first, as upstream did: changing a lens must never destroy the one that was there.
     */
    public static boolean fit(ServerPlayer player, Lens.Slot slot, Lens lens, ItemStack lensItem) {
        ItemStack goggles = goggles(player);
        if (goggles.isEmpty()) {
            return false;
        }
        Lens previous = Lens.read(goggles, slot);
        if (previous == lens) {
            return false;
        }
        if (previous != null) {
            previous.onRemoved(player);
        }
        Lens.write(goggles, slot, lens);
        if (!player.getAbilities().instabuild) {
            lensItem.shrink(1);
        }
        if (previous != null) {
            give(player, itemFor(previous));
        }
        syncTo(player);
        return true;
    }

    /** Takes a lens out, handing the item back to the player. */
    public static boolean remove(ServerPlayer player, Lens.Slot slot) {
        ItemStack goggles = goggles(player);
        Lens lens = Lens.read(goggles, slot);
        if (lens == null) {
            return false;
        }
        Lens.write(goggles, slot, null);
        lens.onRemoved(player);
        give(player, itemFor(lens));
        syncTo(player);
        return true;
    }

    /** Hands an item over, dropping it at the player's feet when there is no room, so nothing is ever lost. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** The item that represents a lens, for handing one back or showing it in a menu. */
    public static ItemStack itemFor(Lens lens) {
        Optional<ItemStack> fromRegistry = ItemsIS.forLens(lens.id());
        return fromRegistry.orElse(ItemStack.EMPTY);
    }

    /** Runs every worn lens, which the server does once per player tick. */
    public static void tick(ServerPlayer player) {
        ItemStack goggles = goggles(player);
        if (goggles.isEmpty()) {
            return;
        }
        for (Lens.Slot slot : Lens.Slot.values()) {
            Lens lens = Lens.read(goggles, slot);
            if (lens != null) {
                lens.tick(player, goggles, slot);
            }
        }
    }

    /** Tells the wearer's client what is fitted, so the menu and the readouts agree with the server. */
    public static void syncTo(ServerPlayer player) {
        ItemStack goggles = goggles(player);
        for (Lens.Slot slot : Lens.Slot.values()) {
            Lens lens = goggles.isEmpty() ? null : Lens.read(goggles, slot);
            Isorropia.LOGGER.debug("Lens {} in {}", lens == null ? "none" : lens.id(), slot);
        }
        fr.wind_blade.isorropia.common.capabilities.LivingData.get(player).ifPresent(data -> data.sync());
    }
}
