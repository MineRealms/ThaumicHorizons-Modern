package fr.wind_blade.isorropia.common.entities;

import dev.tc4port.thaumcraft.common.GolemUpgradeId;
import dev.tc4port.thaumcraft.item.GolemCoreItem;
import dev.tc4port.thaumcraft.item.GolemUpgradeItem;
import dev.tc4port.thaumcraft.registry.TCEntities;
import dev.tc4port.thaumcraft.registry.TCSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Thaumic Horizons' working golem: a block raised into a construct that keeps its maker's material — its health,
 * armour, gait and heft all come from the block it was. Coreless it is a companion that shadows its owner and tears
 * into nearby monsters, and (unlike the animation focus's short-lived conjuring) it lasts as long as nothing breaks
 * it. A golem of TNT goes out with a bang, a detonated one does the same on cue, and a golem raised from nothing at
 * all is a voidling, which lasts forever and eventually stops being a golem at all.
 *
 * <p>TC4R's own golems are a closed system ({@code ThaumcraftGolemEntity} is final and its material catalogue is a
 * fixed enum), so this golem is its own entity. It accepts TC4R's golem cores and upgrades by right-click: a core
 * makes it a settled home guardian rather than a follower, while TH's work behaviours stay with TC4R's golems.</p>
 */
public class EntityGolemTH extends PathfinderMob {
    /** The block the golem is made of, synced so every client can draw it. */
    private static final EntityDataAccessor<BlockState> DATA_STATE =
            SynchedEntityData.defineId(EntityGolemTH.class, EntityDataSerializers.BLOCK_STATE);
    /** Which material the golem counts as, for its stats and colour. */
    private static final EntityDataAccessor<Byte> DATA_TYPE =
            SynchedEntityData.defineId(EntityGolemTH.class, EntityDataSerializers.BYTE);
    /** Whether it goes berserk (attacks anything but its owner) and whether it goes out with a bang. */
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(EntityGolemTH.class, EntityDataSerializers.BYTE);
    /** The installed core's index into TC4R's built-in cores, or -1 while coreless. */
    private static final EntityDataAccessor<Byte> DATA_CORE =
            SynchedEntityData.defineId(EntityGolemTH.class, EntityDataSerializers.BYTE);
    /**
     * One packed nibble per upgrade slot, each an (index into TC4R's built-in upgrades) + 1, or 0 for empty. The
     * golem carries at most three slots, and 1.20.1's sync layer has no byte-array serializer, so an int it is.
     */
    private static final EntityDataAccessor<Integer> DATA_UPGRADES =
            SynchedEntityData.defineId(EntityGolemTH.class, EntityDataSerializers.INT);

    public static final byte FLAG_BERSERK = 1;
    public static final byte FLAG_EXPLOSIVE = 2;
    /** Upstream's sentinel for a golem that never runs out of time. */
    public static final int PERMANENT = -420;
    /** A coreless golem raised from powder lasts this long (TH's placer value). */
    public static final int POWDER_LIFETIME = 1200;

    private static final String NBT_STATE = "BlockState";
    private static final String NBT_TYPE = "GolemType";
    private static final String NBT_LIFETIME = "TicksAlive";
    private static final String NBT_OWNER = "Owner";
    private static final String NBT_FLAGS = "Flags";
    private static final String NBT_CORE = "Core";
    private static final String NBT_UPGRADES = "Upgrades";
    private static final String NBT_VOID_COUNT = "VoidCount";

    /** TH's voidling warnings, delivered to the owner as the construct slips its control enchantments. */
    private static final String[] VOID_WARNINGS = {"thaumichorizons.golemWarning1", "thaumichorizons.golemWarning2",
            "thaumichorizons.golemWarning3"};

    @Nullable
    private UUID owner;
    private int lifetime = PERMANENT;
    private int regenTimer;
    private int voidCount;
    private boolean settled;

    public EntityGolemTH(EntityType<? extends EntityGolemTH> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    //? if forge {
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, Blocks.AIR.defaultBlockState());
        this.entityData.define(DATA_TYPE, (byte) AnimatedBlockType.ROCK.ordinal());
        this.entityData.define(DATA_FLAGS, (byte) 0);
        this.entityData.define(DATA_CORE, (byte) -1);
        this.entityData.define(DATA_UPGRADES, 0);
    }
    //?} else {
    /*@Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, Blocks.AIR.defaultBlockState());
        builder.define(DATA_TYPE, (byte) AnimatedBlockType.ROCK.ordinal());
        builder.define(DATA_FLAGS, (byte) 0);
        builder.define(DATA_CORE, (byte) -1);
        builder.define(DATA_UPGRADES, 0);
    }
    *///?}

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(5, new OwnerFollowGoal(this, 3.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // TH's AIHurtByTarget never retaliates against the golem's own maker.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return EntityGolemTH.this.getOwner() != EntityGolemTH.this.getLastHurtByMob() && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::isBerserkTarget));
    }

    /** Raises the golem out of a block: its material, how long it lasts, and whether it is berserk or explosive. */
    public void loadGolem(@Nullable BlockState state, int lifetime, boolean berserk, boolean explosive,
                          @Nullable Player owner) {
        AnimatedBlockType type = state == null || state.isAir() ? AnimatedBlockType.VOID : AnimatedBlockType.of(state);
        this.entityData.set(DATA_STATE, state == null ? Blocks.AIR.defaultBlockState() : state);
        this.entityData.set(DATA_TYPE, (byte) type.ordinal());
        this.entityData.set(DATA_FLAGS, (byte) ((berserk ? FLAG_BERSERK : 0) | (explosive ? FLAG_EXPLOSIVE : 0)));
        this.entityData.set(DATA_CORE, (byte) -1);
        this.entityData.set(DATA_UPGRADES, 0);
        this.lifetime = lifetime;
        this.voidCount = 0;
        this.owner = owner == null ? null : owner.getUUID();
        this.setPersistenceRequired();
        this.applyTypeStats(type);
        this.setHealth(this.getMaxHealth());
    }

    private void applyTypeStats(AnimatedBlockType type) {
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(type.health);
        }
        if (this.getAttribute(Attributes.ARMOR) != null) {
            this.getAttribute(Attributes.ARMOR).setBaseValue(type.armor);
        }
        if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(type.speed);
        }
        if (this.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D + this.getGolemStrength());
        }
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_STATE);
    }

    public AnimatedBlockType getGolemType() {
        return AnimatedBlockType.values()[this.entityData.get(DATA_TYPE) & 0xFF];
    }

    public boolean isBerserk() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_BERSERK) != 0;
    }

    public boolean isExplosive() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_EXPLOSIVE) != 0;
    }

    /** TH's core slot: -1 while coreless, else the index into TC4R's built-in cores. */
    public int getCore() {
        return this.entityData.get(DATA_CORE);
    }

    public int getUpgrade(int slot) {
        if (slot < 0 || slot >= this.getGolemType().upgrades) {
            return -1;
        }
        return (this.entityData.get(DATA_UPGRADES) >> (slot * 4) & 0xF) - 1;
    }

    /** How many copies of one upgrade the golem carries; TH allowed at most two of each. */
    public int getUpgradeAmount(int id) {
        int count = 0;
        for (int slot = 0; slot < this.getGolemType().upgrades; slot++) {
            if (this.getUpgrade(slot) == id) {
                count++;
            }
        }
        return count;
    }

    public boolean setUpgrade(int slot, int id) {
        if (slot < 0 || slot >= this.getGolemType().upgrades || this.getUpgrade(slot) != -1) {
            return false;
        }
        int packed = this.entityData.get(DATA_UPGRADES);
        packed &= ~(0xF << (slot * 4));
        packed |= ((id + 1) & 0xF) << (slot * 4);
        this.entityData.set(DATA_UPGRADES, packed);
        this.applyTypeStats(this.getGolemType());
        return true;
    }

    public void setCore(int id) {
        this.entityData.set(DATA_CORE, (byte) id);
        // A cored golem is a settled guardian: it holds its ground instead of shadowing its maker.
        this.restrictTo(this.blockPosition(), 32);
    }

    /** TH's strength: the material's own heft plus one point per earth upgrade. */
    public int getGolemStrength() {
        return this.getGolemType().strength + this.getUpgradeAmount(1);
    }

    /** TH's haul: the material's carry, scaled up by the earth upgrade. */
    public int getCarryLimit() {
        int base = this.getGolemType().carry;
        return base + Math.min(16, Math.max(4, base)) * this.getUpgradeAmount(1);
    }

    @Override
    public boolean fireImmune() {
        return this.getGolemType().fireResist;
    }

    @Nullable
    public LivingEntity getOwner() {
        if (this.owner == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getPlayerByUUID(this.owner);
    }

    private boolean isBerserkTarget(LivingEntity candidate) {
        if (!this.isBerserk() || candidate instanceof EntityGolemTH || candidate instanceof EntityAnimatedBlock) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return candidate != owner && candidate.isAlive()
                && !(candidate instanceof Player player && player.isCreative());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isDeadOrDying() || !this.isAlive()) {
            // A dying golem must never be healed back above zero: the regen tick used to revive it
            // during the death animation, which left a 0-HP construct walking around.
            return;
        }
        AnimatedBlockType type = this.getGolemType();
        // A coreless golem's clock runs down, and it drifts after its maker; a cored one simply holds its home.
        if (this.getCore() == -1 && this.lifetime != PERMANENT) {
            if (this.lifetime > 0) {
                this.lifetime--;
                LivingEntity maker = this.getOwner();
                if (maker != null) {
                    this.restrictTo(maker.blockPosition(), 16);
                }
            } else {
                // A timed-out golem settles into its block and is removed at once. Going through
                // die() left a 0-HP "animated block" with an empty health bar ticking for 20 frames.
                if (!this.level().isClientSide) {
                    this.settle();
                }
                this.remove(Entity.RemovalReason.KILLED);
                return;
            }
        }
        if (this.getCore() == -1 && type == AnimatedBlockType.VOID) {
            this.voidlingClock();
        }
        if (--this.regenTimer <= 0) {
            this.regenTimer = type.regenDelay;
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
        }
    }

    /** TH's voidling: it obeys forever, but every so often it slips, and the last slip is not a golem any more. */
    private void voidlingClock() {
        if (this.tickCount % 10 != 0 || this.random.nextInt(500) != 0) {
            return;
        }
        LivingEntity maker = this.getOwner();
        if (this.voidCount < VOID_WARNINGS.length) {
            if (maker instanceof Player player) {
                player.displayClientMessage(Component.translatable(VOID_WARNINGS[this.voidCount])
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), false);
            }
        } else if (this.level() instanceof ServerLevel serverLevel) {
            this.die(this.damageSources().generic());
            var guardian = TCEntities.ELDRITCH_GUARDIAN.get().create(serverLevel);
            if (guardian != null) {
                guardian.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                guardian.restrictTo(this.blockPosition(), 32);
                serverLevel.addFreshEntity(guardian);
            }
            serverLevel.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(), 20, 0.5D, 0.5D, 0.5D, 0.02D);
            return;
        }
        this.voidCount++;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // TH's cactus golem shrugs off cactus pricks and pays them back; the order upgrade does the same for anyone.
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker != this
                && (this.getBlockState().is(Blocks.CACTUS) || this.getUpgradeAmount(5) > 0)) {
            float thorns = this.getUpgradeAmount(5) > 0
                    ? this.getUpgradeAmount(5) * 2.0F + this.random.nextInt(2 * Math.max(1, this.getUpgradeAmount(5)))
                    : this.random.nextInt(2);
            attacker.hurt(this.damageSources().thorns(this), thorns);
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // A golem of cobweb gumms up whatever it strikes.
        if (target instanceof LivingEntity living && this.getBlockState().is(Blocks.COBWEB)) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
        }
        return super.doHurtTarget(target);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.getCore() == -1 && stack.getItem() instanceof GolemCoreItem coreItem && coreItem.coreId(stack) != null) {
            int core = dev.tc4port.thaumcraft.common.GolemCoreId.BUILT_INS.indexOf(coreItem.coreId(stack));
            if (core >= 0) {
                this.setCore(core);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                this.level().playSound(null, this.blockPosition(), TCSounds.WAND.get(), SoundSource.NEUTRAL, 0.5F, 1.0F);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (this.getCore() != -1 && stack.getItem() instanceof GolemUpgradeItem upgradeItem) {
            int id = GolemUpgradeId.BUILT_INS.indexOf(GolemUpgradeItem.upgradeId(stack));
            if (id >= 0 && this.getUpgradeAmount(id) < 2) {
                for (int slot = 0; slot < this.getGolemType().upgrades; slot++) {
                    if (this.getUpgrade(slot) == -1 && this.setUpgrade(slot, id)) {
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        this.level().playSound(null, this.blockPosition(), TCSounds.WAND.get(), SoundSource.NEUTRAL,
                                0.5F, 1.0F);
                        return InteractionResult.sidedSuccess(this.level().isClientSide);
                    }
                }
            }
            return InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide) {
            this.settle();
        }
        super.die(cause);
    }

    /** The golem comes apart: it bursts, or it settles back into the block it was raised from. */
    private void settle() {
        if (this.settled) {
            return;
        }
        this.settled = true;
        BlockState state = this.getBlockState();
        if (state.isAir()) {
            return;
        }
        if (this.isExplosive() || state.is(Blocks.TNT)) {
            this.level().explode(this, this.getX(), this.getY(), this.getZ(),
                    this.isExplosive() ? 3.0F : 4.0F, Level.ExplosionInteraction.MOB);
            return;
        }
        BlockPos target = this.placementSpot();
        if (target != null) {
            this.level().setBlock(target, state, 3);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(state));
            }
        } else {
            this.spawnAtLocation(state.getBlock());
        }
    }

    /** The nearest spot the block can settle back into, starting under the golem and fanning out. */
    @Nullable
    private BlockPos placementSpot() {
        BlockPos base = this.blockPosition();
        if (this.level().getBlockState(base).canBeReplaced()) {
            return base;
        }
        for (int y = 1; y >= -1; y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos candidate = base.offset(x, y, z);
                    if (this.level().getBlockState(candidate).canBeReplaced()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(NBT_STATE, Block.getId(this.getBlockState()));
        tag.putByte(NBT_TYPE, this.entityData.get(DATA_TYPE));
        tag.putInt(NBT_LIFETIME, this.lifetime);
        tag.putByte(NBT_FLAGS, this.entityData.get(DATA_FLAGS));
        tag.putByte(NBT_CORE, this.entityData.get(DATA_CORE));
        tag.putInt(NBT_UPGRADES, this.entityData.get(DATA_UPGRADES));
        tag.putInt(NBT_VOID_COUNT, this.voidCount);
        if (this.owner != null) {
            tag.putUUID(NBT_OWNER, this.owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STATE, Block.stateById(tag.getInt(NBT_STATE)));
        this.entityData.set(DATA_TYPE, tag.getByte(NBT_TYPE));
        this.entityData.set(DATA_FLAGS, tag.getByte(NBT_FLAGS));
        this.entityData.set(DATA_CORE, tag.getByte(NBT_CORE));
        this.entityData.set(DATA_UPGRADES, tag.getInt(NBT_UPGRADES));
        this.lifetime = tag.contains(NBT_LIFETIME) ? tag.getInt(NBT_LIFETIME) : PERMANENT;
        this.voidCount = tag.getInt(NBT_VOID_COUNT);
        this.owner = tag.hasUUID(NBT_OWNER) ? tag.getUUID(NBT_OWNER) : null;
        this.applyTypeStats(this.getGolemType());
    }

    /** Walks back toward whoever raised the golem, once they have drifted a few blocks apart. */
    private static final class OwnerFollowGoal extends Goal {
        private final EntityGolemTH golem;
        private final double speed;
        @Nullable
        private LivingEntity owner;
        private int recalc;

        OwnerFollowGoal(EntityGolemTH golem, double speedMultiplier) {
            this.golem = golem;
            this.speed = golem.getGolemType().speed * speedMultiplier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity candidate = this.golem.getOwner();
            if (this.golem.getCore() != -1 || candidate == null || this.golem.distanceToSqr(candidate) < 36.0D) {
                return false;
            }
            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null && this.owner.isAlive() && this.golem.distanceToSqr(this.owner) > 16.0D;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            this.golem.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            this.golem.getLookControl().setLookAt(this.owner, 10.0F, 40.0F);
            if (--this.recalc <= 0) {
                this.recalc = 10;
                this.golem.getNavigation().moveTo(this.owner, this.speed);
            }
        }
    }
}
