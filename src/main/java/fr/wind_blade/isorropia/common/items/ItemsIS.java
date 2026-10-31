package fr.wind_blade.isorropia.common.items;

import fr.wind_blade.isorropia.Isorropia;
import fr.wind_blade.isorropia.common.blocks.BlocksIS;
import fr.wind_blade.isorropia.common.entities.EntitiesIS;
import fr.wind_blade.isorropia.common.items.baubles.ItemSomaticBrain;
import fr.wind_blade.isorropia.common.items.misc.ItemCat;
import fr.wind_blade.isorropia.common.items.misc.ItemChocolateBucket;
import fr.wind_blade.isorropia.common.items.misc.ItemIncubatedEgg;
import fr.wind_blade.isorropia.common.items.misc.ItemJelly;
import fr.wind_blade.isorropia.common.items.misc.ItemPlanarConduit;
import fr.wind_blade.isorropia.common.items.misc.ItemLens;
import fr.wind_blade.isorropia.common.items.misc.ItemSyringeBlood;
import fr.wind_blade.isorropia.common.items.misc.ItemSyringeBloodSample;
import fr.wind_blade.isorropia.common.items.misc.ItemSyringeEmpty;
import fr.wind_blade.isorropia.common.items.misc.ItemSyringeInjection;
import fr.wind_blade.isorropia.common.lenses.Lenses;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
//? if neoforge {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}
import java.util.function.Supplier;

import java.util.Optional;
import java.util.function.Supplier;

public final class ItemsIS {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Isorropia.MODID);

    public static final Supplier<Item> BASE_LENS = simple("lens");
    public static final Supplier<Item> CHOCOLATE_BAR = simple("chocolate_bar");
    public static final Supplier<Item> GOLD_EGG = simple("gold_egg");
    public static final Supplier<ItemLens> AIR_LENS = ITEMS.register("lens_air", ItemLens::new);
    public static final Supplier<ItemLens> ORDO_LENS = ITEMS.register("lens_ordo", ItemLens::new);
    public static final Supplier<ItemLens> FIRE_LENS = ITEMS.register("lens_fire", ItemLens::new);
    public static final Supplier<ItemLens> ENVY_LENS = ITEMS.register("lens_envy", ItemLens::new);
    public static final Supplier<ItemFocusContainment> FOCUS_CONTAINMENT = ITEMS.register("focus_containment",
            () -> new ItemFocusContainment(new Item.Properties()));
    public static final Supplier<ItemSomaticBrain> SOMATIC_BRAIN = ITEMS.register("somatic_brain",
            () -> new ItemSomaticBrain(new Item.Properties().stacksTo(1)));
    public static final Supplier<ItemIncubatedEgg> INCUBATED_EGG = ITEMS.register("incubated_egg",
            () -> new ItemIncubatedEgg(new Item.Properties().stacksTo(16)));
    public static final Supplier<Item> INK_EGG = simple("ink_egg");
    public static final Supplier<Item> FLESH = simple("flesh");
    public static final Supplier<ItemCat> CAT = ITEMS.register("cat", ItemCat::new);
    public static final Supplier<ItemJelly> JELLY = ITEMS.register("jelly", () -> new ItemJelly(new Item.Properties()));
    public static final Supplier<ItemChocolateBucket> CHOCOLATE_MILK_BUCKET = ITEMS.register("chocolate_milk_bucket",
            () -> new ItemChocolateBucket(new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
    public static final Supplier<Item> CHOCOLATE_CREAM = ITEMS.register("chocolate_cream",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(3.0F).build())));
    public static final Supplier<Item> CORPSE_EFFIGY = ITEMS.register("corpse_effigy",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final Supplier<BlockItem> CHOCOLATE_BLOCK = blockItem("chocolate", BlocksIS.CHOCOLATE);
    public static final Supplier<BlockItem> MODIFIED_MATRIX = blockItem("modified_matrix", BlocksIS.MODIFIED_MATRIX);
    public static final Supplier<ItemJarSoul> JAR_SOUL = ITEMS.register("jar_soul",
            () -> new ItemJarSoul(BlocksIS.JAR_SOUL.get(), new Item.Properties()));
    public static final Supplier<BlockItem> SOUL_BEACON = blockItem("soul_beacon", BlocksIS.SOUL_BEACON);

    public static final Supplier<ForgeSpawnEggItem> TAINT_PIG_SPAWN_EGG = spawnEgg("taint_pig", EntitiesIS.TAINT_PIG, 0x3A005B);
    public static final Supplier<ForgeSpawnEggItem> GRAVEKEEPER_SPAWN_EGG = spawnEgg("gravekeeper", EntitiesIS.GRAVEKEEPER, 0xFFFF00);
    public static final Supplier<ForgeSpawnEggItem> SHEEDER_SPAWN_EGG = spawnEgg("sheeder", EntitiesIS.SHEEDER, 0xFFFFFF);
    public static final Supplier<ForgeSpawnEggItem> CHROMATIC_SHEEP_SPAWN_EGG = spawnEgg("chromatic_sheep", EntitiesIS.CHROMATIC_SHEEP, 0xFFFFFF);
    public static final Supplier<ForgeSpawnEggItem> SCHOLAR_CHICKEN_SPAWN_EGG = spawnEgg("scholar_chicken", EntitiesIS.SCHOLAR_CHICKEN, 0x000000);
    public static final Supplier<ForgeSpawnEggItem> ORE_PIG_SPAWN_EGG = spawnEgg("ore_pig", EntitiesIS.ORE_PIG, 0x939393);
    public static final Supplier<ForgeSpawnEggItem> JELLY_RABBIT_SPAWN_EGG = spawnEgg("jelly_rabbit", EntitiesIS.JELLY_RABBIT, 0x7FD25B);
    public static final Supplier<ForgeSpawnEggItem> CHOCOLATE_COW_SPAWN_EGG = spawnEgg("chocolate_cow", EntitiesIS.CHOCOLATE_COW, 0x844000);
    public static final Supplier<ForgeSpawnEggItem> GOLDEN_CHICKEN_SPAWN_EGG = spawnEgg("golden_chicken", EntitiesIS.GOLDEN_CHICKEN, 0xD3B000);
    public static final Supplier<ForgeSpawnEggItem> GUARDIAN_PANTHER_SPAWN_EGG = spawnEgg("guardian_panther", EntitiesIS.GUARDIAN_PANTHER, 0xFFC300);
    public static final Supplier<ForgeSpawnEggItem> NETHER_HOUND_SPAWN_EGG = spawnEgg("nether_hound", EntitiesIS.NETHER_HOUND, 0xFF0C00);

    // New items go at the end: inserting one in the middle shifts every later item's numeric id, which Forge
    // warns about on a world that already knows the old ids.
    public static final Supplier<ItemFocusIllumination> FOCUS_ILLUMINATION = ITEMS.register("focus_illumination",
            () -> new ItemFocusIllumination(new Item.Properties()));
    public static final Supplier<ItemFocusLiquefaction> FOCUS_LIQUEFACTION = ITEMS.register("focus_liquefaction",
            () -> new ItemFocusLiquefaction(new Item.Properties()));
    public static final Supplier<ItemFocusDisintegration> FOCUS_DISINTEGRATION = ITEMS.register("focus_disintegration",
            () -> new ItemFocusDisintegration(new Item.Properties()));
    public static final Supplier<ItemFocusAnimation> FOCUS_ANIMATION = ITEMS.register("focus_animation",
            () -> new ItemFocusAnimation(new Item.Properties()));
    public static final Supplier<MachineBlockItem> VIS_DYNAMO = machineItem("vis_dynamo", BlocksIS.VIS_DYNAMO);
    public static final Supplier<MachineBlockItem> ESSENTIA_DYNAMO = machineItem("essentia_dynamo", BlocksIS.ESSENTIA_DYNAMO);
    public static final Supplier<MachineBlockItem> NODE_MONITOR = machineItem("node_monitor", BlocksIS.NODE_MONITOR);
    public static final Supplier<BlockItem> ALCHEMITE = blockItem("alchemite", BlocksIS.ALCHEMITE);
    /** Drawn by its block entity renderer in the world, in the hand and in the inventory, as TH's was. */
    public static final Supplier<MachineBlockItem> ETHEREAL_SHARD = machineItem("ethereal_shard", BlocksIS.ETHEREAL_SHARD);
    /** Drawn by its block entity renderer, as TH's amplifier was with the node-converter model. */
    public static final Supplier<MachineBlockItem> TRANSDUCTION_AMPLIFIER = machineItem("transduction_amplifier", BlocksIS.TRANSDUCTION_AMPLIFIER);
    /** Drawn by its block entity renderer (emitter head + beam). */
    public static final Supplier<MachineBlockItem> VORTEX_STABILIZER = machineItem("vortex_stabilizer", BlocksIS.VORTEX_STABILIZER);
    /** Drawn by its block entity renderer (clawed pedestal, pearl, lightning ring). */
    public static final Supplier<MachineBlockItem> RECOMBINATOR = machineItem("recombinator", BlocksIS.RECOMBINATOR);
    public static final Supplier<MachineBlockItem> BLOOD_INFUSER = machineItem("blood_infuser", BlocksIS.BLOOD_INFUSER);
    /** The rift itself: creative-only, since in play it is made by collapsing an over-amplified node. */
    public static final Supplier<BlockItem> VORTEX = blockItem("vortex", BlocksIS.VORTEX);
    /** The pocket plane's shell: creative-only, laid down by the planar generator. */
    public static final Supplier<BlockItem> VOID = blockItem("void", BlocksIS.VOID);
    /** The pocket plane's default ground. */
    public static final Supplier<BlockItem> DUST = blockItem("dust", BlocksIS.DUST);

    // Planar line (TH planar conduit): ingredient for the transduction amplifier, vortex stabiliser, recombinator, ...
    public static final Supplier<ItemPlanarConduit> PLANAR_CONDUIT = ITEMS.register("planar_conduit",
            ItemPlanarConduit::new);

    // Void crafting (TH rift hand-off): matter dropped into a fully damped rift is transformed and held inside it,
    // to be pulled back out with a wand. A void ingot soaks into putty; an inert crystal wand comes back charged.
    /** TH: 16 to a stack, and it repairs any damaged tool, weapon or armour on the crafting grid. */
    public static final Supplier<Item> VOID_PUTTY = ITEMS.register("void_putty",
            () -> new Item(new Item.Properties().stacksTo(16)));
    /** The inert wand; the rift turns it into a crystal rod and cap charged to the brim. */
    public static final Supplier<Item> CRYSTAL_WAND = ITEMS.register("crystal_wand",
            () -> new Item(new Item.Properties().stacksTo(1)));

    // Syringe toolkit (TH blood infusion): the warded syringe draws the blood the Seroconverter later treats.
    public static final Supplier<ItemSyringeEmpty> SYRINGE_EMPTY = ITEMS.register("syringe_empty",
            () -> new ItemSyringeEmpty(new Item.Properties()));
    /** The wizard's own blood, and the blast phial's reagent. */
    public static final Supplier<ItemSyringeBlood> SYRINGE_BLOOD = ITEMS.register("syringe_blood",
            () -> new ItemSyringeBlood(new Item.Properties()));
    public static final Supplier<ItemSyringeBloodSample> SYRINGE_BLOOD_SAMPLE = ITEMS.register(
            "syringe_blood_sample", () -> new ItemSyringeBloodSample(new Item.Properties()));
    /** One injection, as TH held only a single syringe: an injection carries its own essences. */
    public static final Supplier<ItemSyringeInjection> SYRINGE_INJECTION = ITEMS.register("syringe_injection",
            () -> new ItemSyringeInjection(new Item.Properties().stacksTo(1), false));
    /**
     * TH's phial form of the same item, told apart by damage there and by its own id here. Unlike the injection it
     * stacks: TH set a stack size of one on the shared class yet had its recipe yield four phials, which 1.7
     * tolerated and the modern recipe codec rejects, so the phial - the thrown ammunition - is the form that stacks.
     */
    public static final Supplier<ItemSyringeInjection> BLAST_PHIAL = ITEMS.register("blast_phial",
            () -> new ItemSyringeInjection(new Item.Properties(), true));

    // Planar gateway (TH planar keystone): the key to one pocket plane, and the gateway's own blocks.
    /** TH: one keystone per plane, and it never stacks. */
    public static final Supplier<ItemKeystone> KEYSTONE = ITEMS.register("keystone",
            () -> new ItemKeystone(new Item.Properties().stacksTo(1)));
    /** The keystone receptacle: the block the player builds into the top of the gateway wall. */
    public static final Supplier<BlockItem> KEYSTONE_RECEPTACLE = blockItem("keystone_receptacle", BlocksIS.SLOT);
    /** The gateway frame: creative-only, since in play the receptacle turns the player's wall into it. */
    public static final Supplier<BlockItem> GATEWAY = blockItem("gateway", BlocksIS.GATEWAY);
    /** The gateway surface: creative-only, since in play it is cut by the receptacle. */
    public static final Supplier<BlockItem> PORTAL = blockItem("portal", BlocksIS.PORTAL);

    // Working golems (TH golem powder): sprinkled on plain matter the powder raises it into a working golem.
    /** TH: 64 to a stack, one pinch per golem. */
    public static final Supplier<ItemGolemPowder> GOLEM_POWDER = ITEMS.register("golem_powder",
            () -> new ItemGolemPowder(new Item.Properties()));

    /** Native placement carrier for the illumination focus; omitted from the creative tab. */
    public static final Supplier<BlockItem> LIGHT = blockItem("light", BlocksIS.LIGHT);

    private ItemsIS() {
    }

    /** The item a lens is made of, so one can be handed back or shown in a menu. */
    public static java.util.Optional<ItemStack> forLens(net.minecraft.resources.ResourceLocation lens) {
        if (Lenses.AIR.id().equals(lens)) {
            return Optional.of(new ItemStack(AIR_LENS.get()));
        }
        if (Lenses.FIRE.id().equals(lens)) {
            return Optional.of(new ItemStack(FIRE_LENS.get()));
        }
        if (Lenses.ORDO.id().equals(lens)) {
            return Optional.of(new ItemStack(ORDO_LENS.get()));
        }
        if (Lenses.ENVY.id().equals(lens)) {
            return Optional.of(new ItemStack(ENVY_LENS.get()));
        }
        return Optional.empty();
    }

    private static Supplier<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    /** Thaumic Horizons' egg colours: the creature's base colour over the shared purple spots. */
    private static Supplier<ForgeSpawnEggItem> spawnEgg(String name, Supplier<? extends EntityType<? extends Mob>> type, int base) {
        return ITEMS.register(name + "_spawn_egg", () -> new ForgeSpawnEggItem(type, base, 0xA500FF, new Item.Properties()));
    }

    private static Supplier<MachineBlockItem> machineItem(String name, Supplier<? extends Block> block) {
        return ITEMS.register(name, () -> new MachineBlockItem(block.get(), new Item.Properties()));
    }

    private static Supplier<BlockItem> blockItem(String name, Supplier<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
