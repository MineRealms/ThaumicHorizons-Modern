package fr.wind_blade.isorropia.common.events;

import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.ThaumcraftTags;
import dev.tc4port.thaumcraft.api.player.PlayerKnowledgeApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.entities.EntityHangingLabel;
import fr.wind_blade.isorropia.common.entities.OrePigDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Isorropia.MODID)
public final class CreatureEventHandler {
    public static final ResearchKey HANGING_LABEL_RESEARCH = ResearchKey.of(Isorropia.MODID, "hanginglabel");

    private CreatureEventHandler() {
    }

    /** Sticks a jar label to the side of any block that is not itself a jar. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Direction face = event.getFace();
        if (level.isClientSide || face == null || face.getAxis().isVertical() || !EssentiaContainerApi.isLabel(stack)) {
            return;
        }
        BlockPos clicked = event.getPos();
        BlockPos pos = clicked.relative(face);
        if (level.getBlockState(clicked).is(ThaumcraftTags.ESSENTIA_JARS) || !player.mayUseItemAt(pos, face, stack)
                || !PlayerKnowledgeApi.view(player).completedResearch().contains(HANGING_LABEL_RESEARCH)) {
            return;
        }
        EntityHangingLabel label = new EntityHangingLabel(level, pos, face, EssentiaContainerApi.labelAspect(stack).orElse(null));
        if (!label.survives()) {
            return;
        }
        label.playPlacementSound();
        level.addFreshEntity(label);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        OrePigDrops.invalidate();
    }
}
