package fr.wind_blade.isorropia.common.lenses;

import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.platform.StackData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * A lens fitted into a pair of revealing goggles. It sits in one of the goggles' two slots and does its work on
 * the server while it is worn; what each lens shows the wearer is drawn by the client from the same state.
 */
public abstract class Lens {
    /** The goggles' own tag, holding one lens id per slot. */
    public static final String LENS_TAG = "isorropia:lenses";
    private static final String LEFT = "left";
    private static final String RIGHT = "right";

    private final ResourceLocation id;
    private final int colour;

    protected Lens(ResourceLocation id, int colour) {
        this.id = id;
        this.colour = colour;
    }

    public ResourceLocation id() {
        return id;
    }

    /** The colour the goggles tint this lens' display with. */
    public int colour() {
        return colour;
    }

    public Component displayName() {
        return Component.translatable("lens." + id.getPath());
    }

    /** Called on the server for a worn lens, once per player tick. */
    public void tick(ServerPlayer player, ItemStack goggles, Slot slot) {
    }

    /** Called when the lens stops being worn, so it can undo anything it applied. */
    public void onRemoved(ServerPlayer player) {
    }

    public enum Slot {
        LEFT("left"), RIGHT("right");

        private final String tag;

        Slot(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }

        public static Slot byTag(String tag) {
            return RIGHT.tag.equals(tag) ? RIGHT : LEFT;
        }
    }

    /** Writes this lens into one of the goggles' slots. */
    public static void write(ItemStack goggles, Slot slot, @Nullable Lens lens) {
        StackData.update(goggles, root -> {
            CompoundTag tag = root.getCompound(LENS_TAG);
            if (lens == null) {
                tag.remove(slot.tag());
            } else {
                tag.putString(slot.tag(), lens.id().toString());
            }
            root.put(LENS_TAG, tag);
        });
    }

    /** The lens fitted in one of the goggles' slots, or null for an empty slot. */
    @Nullable
    public static Lens read(ItemStack goggles, Slot slot) {
        CompoundTag tag = StackData.read(goggles).getCompound(LENS_TAG);
        String id = tag.getString(slot.tag());
        return id.isEmpty() ? null : Lenses.byId(ResourceLocation.tryParse(id));
    }

    public static ResourceLocation id(String path) {
        return Isorropia.id(path);
    }
}
