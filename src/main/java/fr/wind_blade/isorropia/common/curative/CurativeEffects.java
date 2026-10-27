package fr.wind_blade.isorropia.common.curative;

import dev.tc4port.thaumcraft.api.ThaumcraftTags;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import fr.wind_blade.isorropia.common.IsorropiaAspects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What a curative vat does for the creature soaking in it while it is not infusing: whenever an effect can
 * apply, the vat sucks one essentia of the effect's aspect and applies it, then waits out the cooldown.
 * The order is the upstream registration order: the first applicable effect wins.
 */
public final class CurativeEffects {
    public record Effect(AspectId aspect, Predicate<LivingEntity> canApply, Consumer<LivingEntity> apply, int cooldown) {
    }

    public static final List<Effect> EFFECTS = List.of(
            // Upstream gave mortuus the same undead-only condition as exanimis; kept as is.
            new Effect(IsorropiaAspects.IGNIS, entity -> entity.getType().is(ThaumcraftTags.TAINTED_ENTITIES),
                    entity -> entity.hurt(entity.damageSources().magic(), 1.0F), 4),
            new Effect(IsorropiaAspects.VICTUS, entity -> !entity.isInvertedHealAndHarm() && hurt(entity), entity -> entity.heal(1.0F), 50),
            new Effect(IsorropiaAspects.FAMES, entity -> entity instanceof Player player && player.getFoodData().needsFood(),
                    entity -> ((Player) entity).getFoodData().eat(1, 1.0F), 25),
            new Effect(IsorropiaAspects.MORTUUS, entity -> entity.isInvertedHealAndHarm() && hurt(entity), entity -> entity.heal(4.0F), 50),
            new Effect(IsorropiaAspects.EXANIMIS, entity -> entity.isInvertedHealAndHarm() && hurt(entity), entity -> entity.heal(8.0F), 40));

    private CurativeEffects() {
    }

    private static boolean hurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth();
    }
}
