package fr.wind_blade.isorropia.common.tiles;

import fr.wind_blade.isorropia.common.infusion.SoulJar;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A jar with a creature in it. The creature is only ever the NBT the jar was filled with; the client builds one
 * entity from that tag to draw and keeps it, rather than rebuilding it every frame.
 */
public class TileJarSoul extends BlockEntity {
    @Nullable
    private CompoundTag entityData;
    @Nullable
    private LivingEntity displayed;

    public TileJarSoul(BlockPos pos, BlockState state) {
        super(TilesIS.JAR_SOUL.get(), pos, state);
    }

    @Nullable
    public CompoundTag getEntityData() {
        return entityData;
    }

    public void setEntityData(@Nullable CompoundTag data) {
        this.entityData = data;
        this.displayed = null;
        setChanged();
        TileSync.toWatchers(this);
    }

    /** The creature to draw in the jar, which only the client asks for. */
    @Nullable
    public LivingEntity getDisplayed() {
        if (displayed == null && entityData != null && level != null && level.isClientSide) {
            Entity created = EntityType.create(entityData, level).orElse(null);
            displayed = created instanceof LivingEntity living ? living : null;
        }
        return displayed;
    }

    //? if forge {
    @Override
    protected void saveAdditional(CompoundTag tag) {
    //?} else {
    /*@Override
        protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        super.saveAdditional(tag);        //?} else {
        /*        super.saveAdditional(tag, registries);
        *///?}
        if (entityData != null) {
            tag.put(SoulJar.ENTITY_TAG, entityData);
        }
    }

    //? if forge {
    @Override
    public void load(CompoundTag tag) {
    //?} else {
    /*@Override
        protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        //? if forge {
        super.load(tag);        //?} else {
        /*        super.loadAdditional(tag, registries);
        *///?}
        entityData = tag.contains(SoulJar.ENTITY_TAG) ? tag.getCompound(SoulJar.ENTITY_TAG) : null;
        displayed = null;
    }

    /** A jar is small, but what is inside it is the whole point of syncing a block update. */
    //? if forge {
    @Override
    public CompoundTag getUpdateTag() {
    //?} else {
    /*@Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        CompoundTag tag = new CompoundTag();
        if (entityData != null) {
            tag.put(SoulJar.ENTITY_TAG, entityData);
        }
        return tag;
    }

    //? if forge {
    @Override
    public void handleUpdateTag(CompoundTag tag) {
    //?} else {
    /*@Override
        public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
    *///?}
        entityData = tag.contains(SoulJar.ENTITY_TAG) ? tag.getCompound(SoulJar.ENTITY_TAG) : null;
        displayed = null;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
