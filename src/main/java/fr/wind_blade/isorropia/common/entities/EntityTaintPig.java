package fr.wind_blade.isorropia.common.entities;

import dev.tc4port.thaumcraft.api.ThaumcraftTags;
import dev.tc4port.thaumcraft.api.damage.ThaumcraftDamageTypes;
import fr.wind_blade.isorropia.common.entities.ai.EatTaintGoal;
import fr.wind_blade.isorropia.common.entities.ai.TaintFoodIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
//? if neoforge {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}

import java.util.List;
import java.util.Set;

/** A pig that grazes on taint, cleansing it, and is immune to taint and its afflictions. */
public class EntityTaintPig extends Pig {
    private static final ResourceLocation TAINT_CRAWLER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "taint_spider");
    private static final Set<ResourceLocation> CURED_EFFECTS = Set.of(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "flux_taint"),
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "vis_exhaust"),
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "infectious_vis_exhaust"));

    public EntityTaintPig(EntityType<? extends Pig> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(3, new EatTaintGoal(this));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.2D, new TaintFoodIngredient(), false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                entity -> TAINT_CRAWLER.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()))));
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !isCured(effect.getEffect()) && super.canBeAffected(effect);
    }

    // 1.21 keys the active-effect map by Holder<MobEffect>, and removeEffect takes the holder.
    //? if forge {
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        for (MobEffect effect : List.copyOf(this.getActiveEffectsMap().keySet())) {
            if (isCured(effect)) {
                this.removeEffect(effect);
            }
        }
    }

    private static boolean isCured(MobEffect effect) {
        return CURED_EFFECTS.contains(ForgeRegistries.MOB_EFFECTS.getKey(effect));
    }
    //?} else {
    /*@Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        for (net.minecraft.core.Holder<MobEffect> effect : List.copyOf(this.getActiveEffectsMap().keySet())) {
            if (isCured(effect)) {
                this.removeEffect(effect);
            }
        }
    }

    private static boolean isCured(net.minecraft.core.Holder<MobEffect> effect) {
        return effect.unwrapKey().map(key -> key.location()).filter(CURED_EFFECTS::contains).isPresent();
    }
    *///?}

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.getEntity() != null && source.getEntity().getType().is(ThaumcraftTags.TAINTED_ENTITIES) || source.is(ThaumcraftDamageTypes.FLUX_TAINT) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }
}
