package fr.wind_blade.isorropia.common.infusion;

import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.capabilities.LivingData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.player.PlayerWarpApi;
import dev.tc4port.thaumcraft.api.player.PlayerWarpView;

/**
 * The abilities a player can grow into a reincarnation body: named by {@code infusion} in a self infusion
 * recipe and handed over when a reborn player climbs out of the vat. Upstream handed these out as
 * {@code Integer.MAX_VALUE} potion effects and re-applied them every tick, so a longer or stronger
 * instance from elsewhere can only replace them until the next tick puts them back; that is what keeps
 * a self infusion from being washed out by a potion of the same effect.
 */
public final class SelfInfusions {
    public static final ResourceLocation QUICKSILVER_LIMB = id("quicksilver_limb");
    public static final ResourceLocation AWAKENED_BLOOD = id("awakened_blood");
    public static final ResourceLocation DIAMOND_SKIN = id("diamond_skin");
    public static final ResourceLocation SILVER_HEART = id("silver_heart");
    public static final ResourceLocation SYNTH_SKIN = id("synth_skin");
    public static final ResourceLocation AMPHIBIOUS = id("amphibious");
    public static final ResourceLocation WARPED_TUMOR = id("warped_tumor");
    public static final ResourceLocation SPIDER_CLIMB = id("spider_climb");
    public static final ResourceLocation CHAMELEON_SKIN = id("chameleon_skin");
    /** The portable arcane workbench is a container of its own; it arrives with the morphic fingers screen. */
    public static final ResourceLocation MORPHIC_FINGERS = id("morphic_fingers");
    public static final ResourceLocation THAUMIUM_FISTS = id("thaumium_fists");

    private SelfInfusions() {
    }

    private static ResourceLocation id(String path) {
        return Isorropia.id(path);
    }

    /** Runs the passive infusions for one player tick. */
    public static void tick(ServerPlayer player, LivingData data) {
        if (data.hasSelfInfusion(QUICKSILVER_LIMB)) {
            hold(player, MobEffects.JUMP, 0);
            hold(player, MobEffects.MOVEMENT_SPEED, 0);
        }
        // Upstream granted the regeneration unconditionally, not only while wounded.
        if (data.hasSelfInfusion(AWAKENED_BLOOD)) {
            hold(player, MobEffects.REGENERATION, 0);
        }
        if (data.hasSelfInfusion(DIAMOND_SKIN)) {
            // Upstream granted Resistance I, not II.
            hold(player, MobEffects.DAMAGE_RESISTANCE, 0);
        }
        if (data.hasSelfInfusion(SILVER_HEART)) {
            decayHarmfulEffects(player);
        }
        if (data.hasSelfInfusion(SYNTH_SKIN) && player.level().isDay()
                && player.level().canSeeSky(player.blockPosition())
                && player.getFoodData().needsFood()) {
            player.getFoodData().eat(1, 1.0F);
        }
        if (data.hasSelfInfusion(AMPHIBIOUS) && player.isInWater()) {
            player.setAirSupply(300);
        }
        if (data.hasSelfInfusion(SPIDER_CLIMB) && !data.isClimbDisabled()) {
            climb(player);
        }
        if (data.hasSelfInfusion(CHAMELEON_SKIN) && !data.isInvisibilityDisabled()) {
            hold(player, MobEffects.INVISIBILITY, 0);
        }
        if (data.hasSelfInfusion(WARPED_TUMOR)) {
            absorbWarp(player, data);
        }
        // Thaumium fists only bite while both hands are empty, so the bonus never stacks onto a weapon.
        if (data.hasSelfInfusion(THAUMIUM_FISTS)) {
            fists(player);
        }
    }

    //? if forge {
    private static final java.util.UUID FISTS_ID = java.util.UUID.fromString("0f3e9d7c-6a2b-4c58-9a41-2b7d5e3a1c60");

    private static void fists(Player player) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance damage =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damage == null) {
            return;
        }
        boolean empty = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        boolean present = damage.getModifier(FISTS_ID) != null;
        if (empty && !present) {
            damage.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    FISTS_ID, "isorropia:thaumium_fists", 5.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        } else if (!empty && present) {
            damage.removeModifier(FISTS_ID);
        }
    }
    //?} else {
    /*private static final net.minecraft.resources.ResourceLocation FISTS_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("isorropia", "thaumium_fists");

    private static void fists(Player player) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance damage =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damage == null) {
            return;
        }
        boolean empty = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        boolean present = damage.getModifier(FISTS_ID) != null;
        if (empty && !present) {
            damage.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    FISTS_ID, 5.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        } else if (!empty && present) {
            damage.removeModifier(FISTS_ID);
        }
    }
    *///?}

    //? if forge {
    private static void hold(Player player, MobEffect effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 40 || current.getAmplifier() != amplifier) {
            player.addEffect(new MobEffectInstance(effect, Integer.MAX_VALUE, amplifier, true, false, true));
        }
    }
    //?} else {
    /*private static void hold(Player player, net.minecraft.core.Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 40 || current.getAmplifier() != amplifier) {
            player.addEffect(new MobEffectInstance(effect, Integer.MAX_VALUE, amplifier, true, false, true));
        }
    }
    *///?}

    /** The silver heart burns off anything that would wear the body down. */
    private static void decayHarmfulEffects(Player player) {
        for (MobEffectInstance instance : java.util.List.copyOf(player.getActiveEffects())) {
            //? if forge {
            if (instance.getEffect().isBeneficial() || instance.getDuration() <= 2) {
                continue;
            }
            //?} else {
            /*if (instance.getEffect().value().isBeneficial() || instance.getDuration() <= 2) {
                continue;
            }
            *///?}
            // Shortening the instance is not exposed; re-adding it with a shorter duration is.
            // 1.21's MobEffectInstance#getEffect hands back a Holder.
            //? if forge {
            MobEffect effect = instance.getEffect();
            int shortened = instance.getDuration() - 2;
            player.removeEffectNoUpdate(effect);
            player.addEffect(new MobEffectInstance(effect, shortened, instance.getAmplifier(),
                    instance.isAmbient(), instance.isVisible(), instance.showIcon()));
            //?} else {
            /*net.minecraft.core.Holder<MobEffect> effect = instance.getEffect();
            int shortened = instance.getDuration() - 2;
            player.removeEffectNoUpdate(effect);
            player.addEffect(new MobEffectInstance(effect, shortened, instance.getAmplifier(),
                    instance.isAmbient(), instance.isVisible(), instance.showIcon()));
            *///?}
        }
    }

    /** Spider climb, upstream style: push up against a wall, down against the floor, and never take fall damage. */
    private static void climb(Player player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        boolean onWall = level.getBlockState(pos.north()).isSolid()
                || level.getBlockState(pos.south()).isSolid()
                || level.getBlockState(pos.east()).isSolid()
                || level.getBlockState(pos.west()).isSolid();
        boolean onFloor = level.getBlockState(pos.below()).isSolid();
        if (onWall && !onFloor) {
            Vec3 delta = player.getDeltaMovement();
            player.setDeltaMovement(delta.x, 0.2, delta.z);
            player.fallDistance = 0.0F;
        } else if (onWall) {
            Vec3 delta = player.getDeltaMovement();
            player.setDeltaMovement(delta.x, Math.min(delta.y, 0.0), delta.z);
            player.fallDistance = 0.0F;
        }
    }

    /**
     * The warped tumor soaks warp off the player and keeps it in the body, at most fifty points. When that
     * body dies the event handler pours the warp back and the abilities are lost with it, which is upstream's
     * bargain: reincarnation carries the warp forward, the tumor only holds it for a while.
     */
    private static void absorbWarp(ServerPlayer player, LivingData data) {
        if (player.tickCount % 30 != 0) {
            return;
        }
        int room = 50 - data.getTumorWarpTotal();
        if (room <= 0) {
            return;
        }
        PlayerWarpView warp = PlayerWarpApi.view(player);
        int permanent = Math.min(warp.permanent(), room);
        int sticky = Math.min(warp.sticky(), room - permanent);
        int temporary = Math.min(warp.temporary(), room - permanent - sticky);
        if (permanent <= 0 && sticky <= 0 && temporary <= 0) {
            return;
        }
        if (permanent > 0) {
            ThaumcraftApiHelper.addWarpToPlayer(player, -permanent, false);
        }
        if (sticky > 0) {
            ThaumcraftApiHelper.addStickyWarpToPlayer(player, -sticky);
        }
        if (temporary > 0) {
            ThaumcraftApiHelper.addWarpToPlayer(player, -temporary, true);
        }
        data.addTumorWarp(permanent, sticky, temporary);
    }

    /** Pours the tumor's stored warp back into the player; called when the body dies. */
    public static void releaseTumorWarp(ServerPlayer player, LivingData data) {
        if (data.getTumorWarpPermanent() > 0) {
            ThaumcraftApiHelper.addWarpToPlayer(player, data.getTumorWarpPermanent(), false);
        }
        if (data.getTumorWarpSticky() > 0) {
            ThaumcraftApiHelper.addStickyWarpToPlayer(player, data.getTumorWarpSticky());
        }
        if (data.getTumorWarpTemporary() > 0) {
            ThaumcraftApiHelper.addWarpToPlayer(player, data.getTumorWarpTemporary(), true);
        }
    }
}
