package fr.wind_blade.isorropia.common.focus;

import dev.tc4port.thaumcraft.api.ThaumcraftContent;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusUpgradeId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionSpec;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionTarget;
import dev.tc4port.thaumcraft.api.focus.action.FocusCastMode;
import dev.tc4port.thaumcraft.api.focus.action.FocusInput;
import dev.tc4port.thaumcraft.api.focus.action.FocusTargetKind;
import dev.tc4port.thaumcraft.api.presentation.ActionPresentationAudience;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.infusion.SoulJar;
import fr.wind_blade.isorropia.common.items.ItemFocusContainment;
import fr.wind_blade.isorropia.common.network.ClientFxPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The containment focus, as Thaumic Horizons built it for Thaumcraft 4: a beam held on a creature fills a measure
 * of containment, and once the measure passes twenty times the creature's remaining health the creature is drawn
 * into a warded jar. A wounded creature is therefore far quicker to bottle, as the research says. The measure
 * ebbs by one every tick, so letting go or looking away lets it drain.
 *
 * <p>The beam costs its vis every tick it is held, whether or not it is on a creature, and stops when the player
 * has no empty jar left to fill.
 */
public final class ContainmentFocus {
    public static final ResourceLocation ACTION = Isorropia.id("containment");
    public static final ResearchKey RESEARCH = ResearchKey.of(Isorropia.MODID, "containment");
    /** How far the beam reaches. */
    public static final int RANGE = 10;
    /** Creatures no jar can hold: bosses and Thaumcraft's eldritch guardians. */
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
            Isorropia.id("containment_immune"));
    /** TH's slow upgrade, offered at the second and fourth ranks. */
    public static final FocusUpgradeId SLOW = FocusUpgradeId.of(Isorropia.id("slow"));
    /** How long the slow upgrade's slowness lasts on a struck creature. */
    private static final int SLOW_TICKS = 40;
    /** How much the measure loses each tick it is not being filled. */
    private static final float EBB_PER_TICK = 1.0F;
    /** The measure needed is this many times the creature's remaining health. */
    private static final float HEALTH_FACTOR = 20.0F;
    /** The crackle while a creature is held repeats this often, as upstream's 1.2 second delay. */
    private static final long HUM_TICKS = 24L;
    /** Lightning crawls over a held creature this often. */
    private static final long ARC_TICKS = 5L;
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    /** A player's beam on one creature: the measure so far and when it was last topped up. */
    private record Hold(UUID target, float measure, long tick, long nextHum) {
    }

    private ContainmentFocus() {
    }

    /** Registers the beam with Thaumcraft's wand. Called on both sides, as the wand predicts on the client. */
    public static void register() {
        // The two rates come from the focus catalog; declaring them stops a datapack from dropping either.
        FocusApi.requireParameters(ItemFocusContainment.ID, Set.of("first_hold", "hold_rate"));
        FocusActionApi.register(new FocusActionSpec(ACTION, new FocusActionTarget.Focus(ItemFocusContainment.ID),
                FocusInput.SECONDARY, Set.of(FocusTargetKind.AIR), FocusCastMode.channel(1),
                ResearchRequirement.required(RESEARCH), RANGE, 0, ActionPresentationAudience.PREDICTED),
                ContainmentFocus::handle);
    }

    private static FocusActionResult handle(FocusActionContext context, VisAction action) {
        return switch (context.gesture()) {
            case PRESS -> FocusActionResult.START_USING;
            case HOLD -> tick(context, action);
            case RELEASE -> FocusActionResult.PASS;
        };
    }

    /** One tick of the held beam. Simulation must not change anything, so all effects wait for EXECUTE. */
    private static FocusActionResult tick(FocusActionContext context, VisAction action) {
        ServerPlayer player = context.player();
        if (!SoulJar.hasEmptyJar(player)) {
            if (action == VisAction.EXECUTE) {
                player.displayClientMessage(Component.translatable("isorropia.containment.jar"), true);
            }
            return FocusActionResult.STOP_USING;
        }
        if (!context.pay(context.configuredCost(), action).affordable()) {
            return FocusActionResult.FAILED;
        }
        if (action == VisAction.EXECUTE && context.authoritativeHit() instanceof EntityHitResult hit
                && hit.getEntity() instanceof Mob target && canContain(target)) {
            // TH's slow upgrade: a struck creature is slowed for two seconds, one amplifier per rank past the first.
            int slow = context.installedUpgrades().getOrDefault(SLOW, 0);
            if (slow > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, slow - 1));
            }
            hold(context, player, target);
        }
        return FocusActionResult.SUCCESS;
    }

    public static boolean canContain(Mob target) {
        return target.isAlive() && !target.getType().is(IMMUNE);
    }

    private static void hold(FocusActionContext context, ServerPlayer player, Mob target) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Hold last = HOLDS.get(player.getUUID());
        // A new creature starts from nothing; the same one keeps what has not yet ebbed away.
        float left = last == null || !last.target().equals(target.getUUID()) ? 0.0F
                : last.measure() - (now - last.tick()) * EBB_PER_TICK;
        float measure = left > 0.0F ? left + (float) context.parameter("hold_rate")
                : (float) context.parameter("first_hold");
        Vec3 centre = target.getBoundingBox().getCenter();
        long nextHum = last == null ? now : last.nextHum();
        if (now >= nextHum) {
            level.playSound(null, centre.x, centre.y, centre.z, ThaumcraftContent.sound("jacobs"), SoundSource.PLAYERS,
                    0.3F, 1.0F);
            nextHum = now + HUM_TICKS;
        }
        if (now % ARC_TICKS == 0L) {
            AABB box = target.getBoundingBox();
            ClientFxPacket.arc(level, pointIn(box, level.random), pointIn(box, level.random));
        }
        if (measure > target.getHealth() * HEALTH_FACTOR) {
            HOLDS.remove(player.getUUID());
            if (SoulJar.capture(player, target)) {
                level.playSound(null, centre.x, centre.y, centre.z, ThaumcraftContent.sound("craftfail"),
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return;
        }
        HOLDS.put(player.getUUID(), new Hold(target.getUUID(), measure, now, nextHum));
    }

    private static Vec3 pointIn(AABB box, RandomSource random) {
        return new Vec3(box.minX + random.nextDouble() * box.getXsize(), box.minY + random.nextDouble() * box.getYsize(),
                box.minZ + random.nextDouble() * box.getZsize());
    }

    /** Forgets a player's hold, so a disconnected player's measure does not linger. */
    public static void forget(UUID player) {
        HOLDS.remove(player);
    }
}
