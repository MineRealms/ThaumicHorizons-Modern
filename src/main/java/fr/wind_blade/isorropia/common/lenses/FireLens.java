package fr.wind_blade.isorropia.common.lenses;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Heinrich's lens: the wearer sees in the dark for as long as it is fitted, and loses that sight when it comes
 * out. Upstream applied the effect on the client every tick and dropped it on removal; here it is the same
 * bargain, applied on the server so it survives a relog.
 *
 * <p>Thaumic Horizons' own weakness is kept: the lens is fire, so it stops working while the wearer's eyes are
 * underwater. Ownership of the effect is tracked explicitly, because upstream marked it with a custom
 * {@code PotionEffect} subclass and there is no such thing here: a night vision potion the wearer drank must
 * outlive the lens, even in its last few seconds.</p>
 */
public class FireLens extends Lens {
    /** A little under the effect's full length, so it is refreshed before it can lapse. */
    private static final int DURATION = 255;
    private static final int REFRESH_BELOW = 242;
    /** Set on the wearer while the night vision in effect is this lens' doing and nobody else's. */
    private static final String GRANTED = "isorropia:lens_night_vision";

    public FireLens() {
        super(id("fire_lens"), 0xFF7E2E);
    }

    @Override
    public void tick(ServerPlayer player, ItemStack goggles, Slot slot) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        boolean granted = grantedByLens(player, current);
        // Upstream's one weakness: fire cannot work beneath the waves.
        if (player.isUnderWater()) {
            if (granted) {
                player.removeEffect(MobEffects.NIGHT_VISION);
                release(player);
            }
            return;
        }
        if (current == null) {
            apply(player);
        } else if (granted && current.getDuration() < REFRESH_BELOW) {
            apply(player);
        }
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        // Only take back what this lens gave: a potion's night vision outlives the lens.
        if (grantedByLens(player, player.getEffect(MobEffects.NIGHT_VISION))) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        release(player);
    }

    private static void apply(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0, false, false));
        player.getPersistentData().putBoolean(GRANTED, true);
    }

    private static void release(ServerPlayer player) {
        player.getPersistentData().remove(GRANTED);
    }

    /**
     * True while the effect is the lens' own. The lens only ever sets {@link #DURATION}, and a potion replaces it
     * with a much longer one, so a longer effect means the wearer now owns it and the lens must let go.
     */
    private static boolean grantedByLens(ServerPlayer player, @Nullable MobEffectInstance effect) {
        if (!player.getPersistentData().getBoolean(GRANTED)) {
            return false;
        }
        if (effect == null) {
            release(player);
            return false;
        }
        if (effect.getDuration() > DURATION) {
            release(player);
            return false;
        }
        return true;
    }
}
