package fr.wind_blade.isorropia.common.infusion;

import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.capabilities.LivingData;
import fr.wind_blade.isorropia.common.network.ClientFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IForgeShearable;

import java.util.List;

/**
 * What an infusion does to the creature that carries it, outside the attribute modifiers the recipes add.
 * These are the effects upstream keys off the infusion id, so the ids here are the recipe ids.
 */
public final class CreatureAbilities {
    public static final ResourceLocation SELF_SHEARING = id("self_shearing");
    public static final ResourceLocation AWAKENED_BLOOD = id("awakened_blood");
    public static final ResourceLocation PORTABILITY = id("portability");
    public static final ResourceLocation ENDER_HEART = id("ender_heart");
    public static final ResourceLocation SHOCK_SKIN = id("shock_skin");
    public static final ResourceLocation RUNIC_HIDE = id("runic_hide");
    public static final ResourceLocation ELDRITCH_FANGS = id("eldritch_fangs");

    private static final int SHEAR_INTERVAL = 100;
    private static final int BLOOD_INTERVAL = 50;
    private static final int RUNIC_CAPACITY = 6;
    private static final int RUNIC_RECHARGE = 100;
    private static final String NBT_RUNIC = "isorropia$runic";

    private CreatureAbilities() {
    }

    private static ResourceLocation id(String path) {
        return Isorropia.id(path);
    }

    /** Per-tick effects: a self-shearing coat and a blood infusion that knits itself closed. */
    public static void tick(LivingEntity entity, LivingData data) {
        if (data.hasInfusion(SELF_SHEARING) && entity.tickCount % SHEAR_INTERVAL == 0) {
            shear(entity);
        }
        if (data.hasInfusion(AWAKENED_BLOOD) && entity.tickCount % BLOOD_INTERVAL == 0
                && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(1.0F);
        }
        if (data.hasInfusion(RUNIC_HIDE)) {
            // TH's runic creature: six charges, one back every five seconds.
            net.minecraft.nbt.CompoundTag tag = entity.getPersistentData();
            if (!tag.contains(NBT_RUNIC)) {
                tag.putInt(NBT_RUNIC, RUNIC_CAPACITY);
            } else if (entity.tickCount % RUNIC_RECHARGE == 0 && tag.getInt(NBT_RUNIC) < RUNIC_CAPACITY) {
                tag.putInt(NBT_RUNIC, tag.getInt(NBT_RUNIC) + 1);
            }
        }
    }

    /** TH's runic hide: charges soak damage point for point; starvation, drowning and wither were exempt. */
    public static void absorbRunic(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
            float amount, java.util.function.Consumer<Float> remaining) {
        net.minecraft.nbt.CompoundTag tag = entity.getPersistentData();
        int charge = tag.getInt(NBT_RUNIC);
        if (charge <= 0) {
            return;
        }
        if (source.is(net.minecraft.world.damagesource.DamageTypes.STARVE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                || source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (charge > amount) {
            tag.putInt(NBT_RUNIC, charge - (int) amount);
            remaining.accept(0.0F);
        } else {
            tag.putInt(NBT_RUNIC, 0);
            remaining.accept(amount - charge);
        }
    }

    /** TH's eldritch fangs: a bite from the infused creature poisons whatever it hits. */
    public static void fangsPoison(LivingEntity attacker, LivingEntity victim) {
        if (attacker == victim) {
            return;
        }
        LivingData data = LivingData.get(attacker).orElse(null);
        if (data != null && data.hasInfusion(ELDRITCH_FANGS)) {
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.POISON, 40, 0, false, true, true));
        }
    }

    /** Whatever can be sheared drops its coat on the ground, with a small kick, as upstream's auto-shear. */
    private static void shear(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        List<ItemStack> drops = List.of();
        if (entity instanceof Sheep sheep && !sheep.isSheared()) {
            sheep.setSheared(true);
            Block wool = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.withDefaultNamespace(sheep.getColor().getSerializedName() + "_wool"));
            drops = List.of(new ItemStack(wool, 1 + level.random.nextInt(3)));
        } else if (entity instanceof IForgeShearable shearable
                //? if forge {
                && shearable.isShearable(ItemStack.EMPTY, level, entity.blockPosition())) {
            // The shearer is optional for a modded shearable, and an auto-shearing coat has none.
            drops = shearable.onSheared(null, ItemStack.EMPTY, level, entity.blockPosition(), 0);
            //?} else {
            /*&& shearable.isShearable(null, ItemStack.EMPTY, level, entity.blockPosition())) {
            // The shearer is optional for a modded shearable, and an auto-shearing coat has none.
            drops = shearable.onSheared(null, ItemStack.EMPTY, level, entity.blockPosition());
            *///?}
        }
        if (drops.isEmpty()) {
            return;
        }
        for (ItemStack stack : drops) {
            ItemEntity dropped = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
            dropped.setDeltaMovement((level.random.nextDouble() - level.random.nextDouble()) * 0.1,
                    level.random.nextDouble() * 0.05,
                    (level.random.nextDouble() - level.random.nextDouble()) * 0.1);
            level.addFreshEntity(dropped);
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** A shock skin answers a blow with lightning, drawn from the creature to whoever struck it. */
    public static void shockBack(LivingEntity entity, LivingEntity attacker) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        attacker.hurt(level.damageSources().lightningBolt(), 4.0F);
        ClientFxPacket.arc(level, entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0),
                attacker.position().add(0.0, attacker.getBbHeight() / 2.0, 0.0));
    }

    /**
     * An ender heart teleports out of harm's way when something shoots its owner, which is upstream's reaction
     * to indirect damage. The destination is a random nearby spot rather than a line of sight check.
     */
    public static void teleportFromRanged(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos target = entity.blockPosition().offset(
                    level.random.nextInt(17) - 8, level.random.nextInt(9) - 4, level.random.nextInt(17) - 8);
            if (level.isEmptyBlock(target) && level.isEmptyBlock(target.above())) {
                entity.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, entity.getYRot(), entity.getXRot());
                entity.setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
    }

    /** Sends a portability-infused creature into a soul jar, if the player is carrying one. */
    public static boolean jarInto(Player player, LivingEntity target) {
        return SoulJar.capture(player, target);
    }
}
