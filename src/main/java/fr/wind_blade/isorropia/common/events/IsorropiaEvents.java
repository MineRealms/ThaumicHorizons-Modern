package fr.wind_blade.isorropia.common.events;

import dev.tc4port.thaumcraft.api.damage.ThaumcraftDamageTypes;
import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.capabilities.LivingData;
import fr.wind_blade.isorropia.common.focus.ContainmentFocus;
import fr.wind_blade.isorropia.common.infusion.CreatureAbilities;
import fr.wind_blade.isorropia.common.infusion.SelfInfusions;
import fr.wind_blade.isorropia.common.infusion.SoulBeacon;
import fr.wind_blade.isorropia.common.lenses.LensManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
//? if forge {
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
//?} else {
/*import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
*///?}
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/**
 * Everything the infusion system hangs off the world with: the per-entity infusion data, the wand gesture that
 * forms a vat, the abilities a body carries, and what happens when one of those bodies dies.
 */
@Mod.EventBusSubscriber(modid = Isorropia.MODID)
public final class IsorropiaEvents {
    private static final ResourceLocation LIVING_DATA = Isorropia.id("living_data");
    private IsorropiaEvents() {
    }

    // NeoForge 1.21.1 has no attach event: LivingData rides a lazily-created data attachment instead.
    //? if forge {
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity living && !living.getCapability(LivingData.CAPABILITY).isPresent()) {
            event.addCapability(LIVING_DATA, new LivingData.Provider(living));
        }
    }
    //?}

    /** Infusions survive a dimension change; a self-infused body does not survive dying in one. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        LivingData.get(event.getOriginal()).ifPresent(
                old -> LivingData.get(event.getEntity()).ifPresent(fresh -> fresh.deserialize(old.serialize())));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LivingData.get(player).ifPresent(data -> data.syncTo(player));
        }
    }

    /** New observers need the infusion state before predicting a wand interaction. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity living) {
            LivingData.get(living).ifPresent(data -> data.syncTo(player));
        }
    }

    /** A player who leaves stops holding anything in the containment beam. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ContainmentFocus.forget(event.getEntity().getUUID());
    }

    //? if forge {
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        playerTick(player);
    }
    //?} else {
    /*@SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            playerTick(player);
        }
    }
    *///?}

    private static void playerTick(ServerPlayer player) {
        LivingData.get(player).ifPresent(data -> SelfInfusions.tick(player, data));
        LensManager.tick(player);
    }

    // NeoForge 1.21 dropped LivingTickEvent; EntityTickEvent fires for every entity, so narrow it there.
    //? if forge {
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity instanceof Player) {
            return;
        }
        LivingData.get(entity).ifPresent(data -> CreatureAbilities.tick(entity, data));
    }
    //?} else {
    /*@SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity.level().isClientSide || entity instanceof Player) {
            return;
        }
        LivingData.get(entity).ifPresent(data -> CreatureAbilities.tick(entity, data));
    }
    *///?}

    /** A silver heart shrugs off taint; a shock skin answers a blow; an ender heart slips out of the way. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        LivingData data = LivingData.get(entity).orElse(null);
        if (data == null) {
            return;
        }
        if (data.hasSelfInfusion(SelfInfusions.SILVER_HEART) && isTaint(event.getSource())) {
            event.setCanceled(true);
            return;
        }
        // Shock skin answers any attacker; the ender heart only dodges what was thrown or shot at it.
        if (data.hasInfusion(CreatureAbilities.SHOCK_SKIN)
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != entity) {
            CreatureAbilities.shockBack(entity, attacker);
        }
        Entity direct = event.getSource().getDirectEntity();
        if (data.hasInfusion(CreatureAbilities.ENDER_HEART) && direct != null && direct != event.getSource().getEntity()) {
            CreatureAbilities.teleportFromRanged(entity);
            event.setCanceled(true);
        }
    }

    /**
     * A body that takes a killing blow: the tumor gives its warp back and every ability the body was grown with
     * is lost. A wizard bound to a soul beacon is then reborn in the body waiting in the vat, with that body's
     * abilities only, which is the whole point of growing one. Upstream does the same, in the same order.
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide
                || player.getHealth() - event.getAmount() > 0.0F) {
            return;
        }
        LivingData.get(player).ifPresent(data -> {
            if (data.getTumorWarpTotal() > 0) {
                SelfInfusions.releaseTumorWarp(player, data);
            }
            if (!data.getSelfInfusions().isEmpty()) {
                data.resetSelfInfusions();
            }
            data.sync();
        });
        SoulBeacon.tryRevive(event, player);
    }

    private static boolean isTaint(DamageSource source) {
        return source.is(ThaumcraftDamageTypes.FLUX_TAINT);
    }
}
