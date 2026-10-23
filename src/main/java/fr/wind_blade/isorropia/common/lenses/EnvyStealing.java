package fr.wind_blade.isorropia.common.lenses;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;

/** Rolls what a creature would drop, for the trickster's lens to take without killing it. */
public final class EnvyStealing {
    private EnvyStealing() {
    }

    /**
     * The roll is the creature's own, with nobody credited for the kill: upstream passed only the looted entity,
     * so a looting sword does not multiply what the lens sees, and neither do player-kill-only pools.
     */
    public static List<ItemStack> rollDrops(ServerLevel level, LivingEntity target) {
        //? if forge {
        ResourceLocation table = target.getLootTable();
        //?} else {
        /*net.minecraft.resources.ResourceKey<LootTable> table = target.getLootTable();
        *///?}
        if (table == null) {
            return List.of();
        }
        //? if forge {
        LootTable loot = level.getServer().getLootData().getLootTable(table);
        //?} else {
        /*LootTable loot = level.getServer().reloadableRegistries().getLootTable(table);
        *///?}
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, target)
                .withParameter(LootContextParams.ORIGIN, target.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        List<ItemStack> drops = new ArrayList<>();
        loot.getRandomItems(params, drops::add);
        return drops;
    }
}
