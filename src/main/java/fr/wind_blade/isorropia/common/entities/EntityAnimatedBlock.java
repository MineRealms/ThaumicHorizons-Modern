package fr.wind_blade.isorropia.common.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A block called out of the world and set walking: the conjured golem of Thaumic Horizons' animation focus. It
 * carries the block it was made from, wears that material's health, armour and gait, follows whoever called it,
 * and fights off monsters. Called out of a block of gunpowder, or given the detonation upgrade, it goes off when
 * it falls. When its span runs out, or something breaks it, it settles back down into the block it came from.
 */
public class EntityAnimatedBlock extends PathfinderMob {
    /** The block the golem is made of, synced so every client can draw it. */
    private static final EntityDataAccessor<BlockState> DATA_STATE =
            SynchedEntityData.defineId(EntityAnimatedBlock.class, EntityDataSerializers.BLOCK_STATE);
    /** Which material the golem counts as, for its stats and colour. */
    private static final EntityDataAccessor<Byte> DATA_TYPE =
            SynchedEntityData.defineId(EntityAnimatedBlock.class, EntityDataSerializers.BYTE);
    /** Whether it goes berserk (attacks anything but its owner) and whether it detonates when it falls. */
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(EntityAnimatedBlock.class, EntityDataSerializers.BYTE);
    public static final byte FLAG_BERSERK = 1;
    public static final byte FLAG_DETONATION = 2;
    /** Upstream's sentinel for a golem that never runs out of time. */
    public static final int PERMANENT = -420;
    private static final String NBT_STATE = "BlockState";
    private static final String NBT_TYPE = "GolemType";
    private static final String NBT_LIFETIME = "TicksAlive";
    private static final String NBT_OWNER = "Owner";
    private static final String NBT_FLAGS = "Flags";

    @Nullable
    private UUID owner;
    private int lifetime = PERMANENT;
    private int regenTimer;
    private boolean settled;

    public EntityAnimatedBlock(EntityType<? extends EntityAnimatedBlock> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    //? if forge {
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, Blocks.AIR.defaultBlockState());
        this.entityData.define(DATA_TYPE, (byte) AnimatedBlockType.ROCK.ordinal());
        this.entityData.define(DATA_FLAGS, (byte) 0);
    }
    //?} else {
    /*@Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, Blocks.AIR.defaultBlockState());
        builder.define(DATA_TYPE, (byte) AnimatedBlockType.ROCK.ordinal());
        builder.define(DATA_FLAGS, (byte) 0);
    }
    *///?}

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new OwnerFollowGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // TH's AIHurtByTarget never retaliates against the golem's own maker.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return EntityAnimatedBlock.this.getOwner() != EntityAnimatedBlock.this.getLastHurtByMob() && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::isBerserkTarget));
    }

    /** Raises the golem out of a block: its material, how long it lasts, and its two upgrades. */
    public void loadGolem(BlockState state, int lifetime, boolean berserk, boolean detonation, @Nullable Player owner) {
        AnimatedBlockType type = AnimatedBlockType.of(state);
        this.entityData.set(DATA_STATE, state);
        this.entityData.set(DATA_TYPE, (byte) type.ordinal());
        this.entityData.set(DATA_FLAGS, (byte) ((berserk ? FLAG_BERSERK : 0) | (detonation ? FLAG_DETONATION : 0)));
        this.lifetime = lifetime;
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
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D + type.strength);
        }
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_STATE);
    }

    public AnimatedBlockType getAnimatedType() {
        return AnimatedBlockType.values()[this.entityData.get(DATA_TYPE) & 0xFF];
    }

    public boolean isBerserk() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_BERSERK) != 0;
    }

    public boolean isDetonation() {
        return (this.entityData.get(DATA_FLAGS) & FLAG_DETONATION) != 0;
    }

    @Nullable
    public LivingEntity getOwner() {
        if (this.owner == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getPlayerByUUID(this.owner);
    }

    private boolean isBerserkTarget(LivingEntity candidate) {
        if (!this.isBerserk() || candidate instanceof EntityAnimatedBlock) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        return candidate != owner && candidate.isAlive() && !(candidate instanceof Player player && player.isCreative());
    }

    @Override
    public boolean fireImmune() {
        return this.getAnimatedType().fireResist;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isDeadOrDying() || !this.isAlive()) {
            // A dying golem must never be healed back above zero: the regen tick used to revive it
            // during the death animation, which left a 0-HP construct walking around.
            return;
        }
        if (this.lifetime != PERMANENT) {
            if (this.lifetime > 0) {
                this.lifetime--;
            } else {
                if (!this.level().isClientSide) {
                    this.settle();
                }
                this.remove(Entity.RemovalReason.KILLED);
                return;
            }
        }
        AnimatedBlockType type = this.getAnimatedType();
        if (--this.regenTimer <= 0) {
            this.regenTimer = type.regenDelay;
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide) {
            this.settle();
        }
        super.die(cause);
    }

    /** The golem comes apart: it bursts, or it settles back into the block it was conjured from. */
    private void settle() {
        if (this.settled) {
            return;
        }
        this.settled = true;
        BlockState state = this.getBlockState();
        if (state.isAir()) {
            return;
        }
        if (this.isDetonation() || state.is(Blocks.TNT)) {
            this.level().explode(this, this.getX(), this.getY(), this.getZ(),
                    this.isDetonation() ? 3.0F : 4.0F, Level.ExplosionInteraction.MOB);
            return;
        }
        BlockPos target = this.placementSpot();
        if (target != null) {
            this.level().setBlock(target, state, 3);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, net.minecraft.world.level.block.Block.getId(state));
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
        tag.putInt(NBT_STATE, net.minecraft.world.level.block.Block.getId(this.getBlockState()));
        tag.putByte(NBT_TYPE, this.entityData.get(DATA_TYPE));
        tag.putInt(NBT_LIFETIME, this.lifetime);
        tag.putByte(NBT_FLAGS, this.entityData.get(DATA_FLAGS));
        if (this.owner != null) {
            tag.putUUID(NBT_OWNER, this.owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STATE, net.minecraft.world.level.block.Block.stateById(tag.getInt(NBT_STATE)));
        this.entityData.set(DATA_TYPE, tag.getByte(NBT_TYPE));
        this.entityData.set(DATA_FLAGS, tag.getByte(NBT_FLAGS));
        this.lifetime = tag.contains(NBT_LIFETIME) ? tag.getInt(NBT_LIFETIME) : PERMANENT;
        this.owner = tag.hasUUID(NBT_OWNER) ? tag.getUUID(NBT_OWNER) : null;
        this.applyTypeStats(this.getAnimatedType());
    }

    /** Walks back toward whoever called the golem, once they have drifted a few blocks apart. */
    private static final class OwnerFollowGoal extends Goal {
        private final EntityAnimatedBlock golem;
        private final double speed;
        @Nullable
        private LivingEntity owner;
        private int recalc;

        OwnerFollowGoal(EntityAnimatedBlock golem, double speed) {
            this.golem = golem;
            this.speed = speed;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity candidate = this.golem.getOwner();
            if (candidate == null || this.golem.distanceToSqr(candidate) < 36.0D) {
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
                this.recalc = this.adjustedTickDelay(10);
                this.golem.getNavigation().moveTo(this.owner, this.speed);
            }
        }
    }
}
