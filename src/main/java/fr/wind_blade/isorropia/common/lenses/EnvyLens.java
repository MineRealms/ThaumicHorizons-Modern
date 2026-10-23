package fr.wind_blade.isorropia.common.lenses;

import fr.wind_blade.isorropia.common.capabilities.LivingData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Trickster's lens: while the wearer stares at a creature, the lens works out what it would drop and slowly
 * talks it out of the creature. When the patience runs out the drop falls where the creature stands and the
 * lens is done with it; the creature keeps its health, which is the trick.
 *
 * <p>The patience belongs to the creature, not the wearer: looking away and back at something else does not
 * carry the count over, which is how the original kept it. It is synced to the wearer alone, because the
 * wearer is the only one who sees the bar.
 */
public class EnvyLens extends Lens {
    /** How much envy each tick of looking adds. */
    private static final int ENVY_PER_TICK = 1;
    /** Patience is proportional to how much the creature is worth to the lens: its health times this. */
    private static final int HEALTH_FACTOR = 20;
    /** How far ahead the lens reaches; the readout asks the same question, so the bar and the work agree. */
    private static final double RANGE = 5.0;

    public EnvyLens() {
        super(id("envy_lens"), 0x00BA00);
    }

    @Override
    public void tick(ServerPlayer player, ItemStack goggles, Slot slot) {
        LivingEntity target = lookedAt(player);
        if (target == null) {
            return;
        }
        LivingData data = LivingData.get(target).orElse(null);
        if (data == null || data.hasLooted()) {
            return;
        }
        // A creature that drops nothing is not worth working on, so the lens gives up on it at once.
        if (target.getLootTable().equals(BuiltInLootTables.EMPTY)) {
            data.setHasLooted(true);
        } else {
            data.addEnvy(ENVY_PER_TICK);
            if (data.getEnvy() >= target.getMaxHealth() * HEALTH_FACTOR) {
                if (player.level() instanceof ServerLevel level) {
                    for (ItemStack stack : EnvyStealing.rollDrops(level, target)) {
                        target.spawnAtLocation(stack);
                    }
                }
                data.setHasLooted(true);
                data.clearEnvy();
            }
        }
        data.syncTo(player);
    }

    /** The creature the wearer is looking at, if it is close enough and in front of them. */
    @Nullable
    public static LivingEntity lookedAt(Player player) {
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        var end = eye.add(look.scale(RANGE));
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eye, end).inflate(1.0), entity -> entity != player && entity.isAlive())) {
            var hit = candidate.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }
}
