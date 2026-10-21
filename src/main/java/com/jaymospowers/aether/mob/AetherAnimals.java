package com.jaymospowers.aether.mob;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFlora;
import com.jaymospowers.aether.AetherItems;
import com.jaymospowers.aether.monster.AetherMonsters;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Aether II's animals beyond the first five (docs/aether.md, "Aether II animals"): the kirrid, burrukai and taegore (one
 * entity type each, with a zone), the skyroot lizard, the prismallard (and its egg), the glitterwing and shroudwing, the
 * aerwhale; their meat, pelts, plates and feathers; the moa egg block; and where all the animals (new and old) live: the
 * three climates' biome tags, never the biome files. Ids and numbers are from the behaviour notes; every number is a constant
 * here or at the top of its class.
 */
public final class AetherAnimals {
	// ---- tuning: spawn weight, smallest and largest group (Aether II's numbers) ----
	public static final int COMMON_WEIGHT = 14, RARE_WEIGHT = 1;
	public static final int KIRRID_MIN = 1, KIRRID_MAX = 2, BURRUKAI_MIN = 1, BURRUKAI_MAX = 1, TAEGORE_MIN = 1, TAEGORE_MAX = 2;
	public static final int BUNNY_MIN = 1, BUNNY_MAX = 3, HERD_MIN = 2, HERD_MAX = 4;
	public static final int PRISMALLARD_MIN = 1, PRISMALLARD_MAX = 3, INSECT_MIN = 1, INSECT_MAX = 2;
	/** A natural aerwhale spawn passes only one roll in this many; it needs open sky and light. */
	public static final int WHALE_ROLL = 40;
	/** Brightness an animal needs (above this). */
	public static final int LIGHT = 8;
	/** Leaves that drop something make a skyroot lizard one time in this many. */
	public static final int LIZARD_ODDS = 10;
	/** Food: restored hunger and saturation of each new food (raw, then cooked). */
	public static final int RAW_MEAT = 3, COOKED_MEAT = 8, LOIN = 2, CUTLET = 6, LEG = 2, ROAST = 6, LIZARD_SNACK = 4;
	public static final float RAW_SAT = 1.8F, COOKED_SAT = 12.8F, LOIN_SAT = 1.2F, CUTLET_SAT = 7.2F, LEG_SAT = 1.0F, ROAST_SAT = 6.0F, SNACK_SAT = 2.0F;

	public static EntityType<Kirrid> KIRRID;
	public static EntityType<Burrukai> BURRUKAI;
	public static EntityType<Taegore> TAEGORE;
	public static EntityType<SkyrootLizard> SKYROOT_LIZARD;
	public static EntityType<Prismallard> PRISMALLARD;
	public static EntityType<Insect> GLITTERWING, SHROUDWING;
	public static EntityType<Aerwhale> AERWHALE;
	public static EntityType<ThrownPrismallardEgg> PRISMALLARD_EGG_ENTITY;

	/** What they drop and what they are made into. */
	public static Item RAW_TAEGORE_MEAT, COOKED_TAEGORE_MEAT, BURRUKAI_RIB_CUT, COOKED_BURRUKAI_RIB_CUT, KIRRID_LOIN, KIRRID_CUTLET, PRISMALLARD_LEG,
		PRISMALLARD_ROAST, BEAST_PELT, BURRUKAI_PLATE, PRISMALLARD_FEATHER, MOA_FEATHER, KIRRID_PLATE, SKYROOT_LIZARD_ON_A_STICK, PRISMALLARD_EGG;
	public static Item KIRRID_SPAWN_EGG, BURRUKAI_SPAWN_EGG, TAEGORE_SPAWN_EGG, SKYROOT_LIZARD_SPAWN_EGG, PRISMALLARD_SPAWN_EGG, GLITTERWING_SPAWN_EGG,
		SHROUDWING_SPAWN_EGG, AERWHALE_SPAWN_EGG;

	public static MoaEggBlock MOA_EGG;
	public static Item MOA_EGG_ITEM;

	private AetherAnimals() {
	}

	// ---- what they eat ----

	/** Oranges and blue berries tempt and breed the Aether's grazers. */
	public static boolean isAnimalFood(ItemStack stack) {
		return stack.is(AetherFlora.ORANGE) || stack.is(AetherItems.BLUE_BERRY);
	}

	/** A moa chick (and a grown moa, to heal) eats aechor petals and blue berries. */
	public static boolean isMoaFood(ItemStack stack) {
		return stack.is(AetherItems.BLUE_BERRY) || AetherMonsters.AECHOR_PETAL != null && stack.is(AetherMonsters.AECHOR_PETAL);
	}

	/** A stick for the lizard: a plain stick, or a skyroot stick if another part of the mod makes one. */
	public static boolean isStick(ItemStack stack) {
		if (stack.is(Items.STICK)) {
			return true;
		}
		return BuiltInRegistries.ITEM.getOptional(JaymosPowers.id("skyroot_stick")).map(stack::is).orElse(false);
	}

	public static void init() {
		MOA_EGG = (MoaEggBlock)block("moa_egg", MoaEggBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.METAL)
			.noOcclusion().pushReaction(PushReaction.POPPED));
		ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("moa_egg"));
		MOA_EGG_ITEM = Registry.register(BuiltInRegistries.ITEM, eggKey, new BlockItem(MOA_EGG, new Item.Properties().setId(eggKey).useBlockDescriptionPrefix()));

		food();
		entities();
		placements();
		spawns();
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> lizardFromLeaves(context));
		tab();
	}

	private static void food() {
		RAW_TAEGORE_MEAT = meat("raw_taegore_meat", RAW_MEAT, RAW_SAT);
		COOKED_TAEGORE_MEAT = meat("cooked_taegore_meat", COOKED_MEAT, COOKED_SAT);
		BURRUKAI_RIB_CUT = meat("burrukai_rib_cut", RAW_MEAT, RAW_SAT);
		COOKED_BURRUKAI_RIB_CUT = meat("cooked_burrukai_rib_cut", COOKED_MEAT, COOKED_SAT);
		KIRRID_LOIN = meat("kirrid_loin", LOIN, LOIN_SAT);
		KIRRID_CUTLET = meat("kirrid_cutlet", CUTLET, CUTLET_SAT);
		PRISMALLARD_LEG = meat("prismallard_leg", LEG, LEG_SAT);
		PRISMALLARD_ROAST = meat("prismallard_roast", ROAST, ROAST_SAT);
		SKYROOT_LIZARD_ON_A_STICK = meat("skyroot_lizard_on_a_stick", LIZARD_SNACK, SNACK_SAT);
		BEAST_PELT = item("beast_pelt", Item::new, new Item.Properties());
		BURRUKAI_PLATE = item("burrukai_plate", Item::new, new Item.Properties());
		PRISMALLARD_FEATHER = item("prismallard_feather", Item::new, new Item.Properties());
		MOA_FEATHER = item("moa_feather", Item::new, new Item.Properties());
		KIRRID_PLATE = item("kirrid_plate", Item::new, new Item.Properties());
		PRISMALLARD_EGG = item("prismallard_egg", PrismallardEggItem::new, new Item.Properties().stacksTo(16));
	}

	private static void entities() {
		KIRRID = type("kirrid", EntityType.Builder.of(Kirrid::new, MobCategory.CREATURE).sized(0.95F, 1.35F).eyeHeight(1.1F).clientTrackingRange(10));
		BURRUKAI = type("burrukai", EntityType.Builder.of(Burrukai::new, MobCategory.CREATURE).sized(1.5F, 1.95F).eyeHeight(1.2F).clientTrackingRange(10));
		TAEGORE = type("taegore", EntityType.Builder.of(Taegore::new, MobCategory.CREATURE).sized(1.3F, 1.7F).eyeHeight(0.85F).clientTrackingRange(10));
		SKYROOT_LIZARD = type("skyroot_lizard", EntityType.Builder.of(SkyrootLizard::new, MobCategory.CREATURE).sized(0.95F, 0.35F).clientTrackingRange(8));
		PRISMALLARD = type("prismallard", EntityType.Builder.of(Prismallard::new, MobCategory.CREATURE).sized(0.5F, 0.6F).eyeHeight(0.55F).clientTrackingRange(8));
		GLITTERWING = type("glitterwing", EntityType.Builder.of(Insect::glitterwing, MobCategory.AMBIENT).sized(0.75F, 0.75F).eyeHeight(0.25F).clientTrackingRange(8));
		SHROUDWING = type("shroudwing", EntityType.Builder.of(Insect::shroudwing, MobCategory.AMBIENT).sized(0.5F, 0.5F).eyeHeight(0.25F).clientTrackingRange(8));
		AERWHALE = type("aerwhale", EntityType.Builder.of(Aerwhale::new, MobCategory.CREATURE).sized(3.0F, 3.0F).fireImmune().clientTrackingRange(16));
		ResourceKey<EntityType<?>> eggKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("prismallard_egg"));
		PRISMALLARD_EGG_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, eggKey, EntityType.Builder.<ThrownPrismallardEgg>of(ThrownPrismallardEgg::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(eggKey));

		FabricDefaultAttributeRegistry.register(KIRRID, Kirrid.createAttributes());
		FabricDefaultAttributeRegistry.register(BURRUKAI, Burrukai.createAttributes());
		FabricDefaultAttributeRegistry.register(TAEGORE, Taegore.createAttributes());
		FabricDefaultAttributeRegistry.register(SKYROOT_LIZARD, SkyrootLizard.createAttributes());
		FabricDefaultAttributeRegistry.register(PRISMALLARD, Prismallard.createAttributes());
		FabricDefaultAttributeRegistry.register(GLITTERWING, Insect.createAttributes());
		FabricDefaultAttributeRegistry.register(SHROUDWING, Insect.createAttributes());
		FabricDefaultAttributeRegistry.register(AERWHALE, Aerwhale.createAttributes());

		KIRRID_SPAWN_EGG = egg("kirrid_spawn_egg", KIRRID);
		BURRUKAI_SPAWN_EGG = egg("burrukai_spawn_egg", BURRUKAI);
		TAEGORE_SPAWN_EGG = egg("taegore_spawn_egg", TAEGORE);
		SKYROOT_LIZARD_SPAWN_EGG = egg("skyroot_lizard_spawn_egg", SKYROOT_LIZARD);
		PRISMALLARD_SPAWN_EGG = egg("prismallard_spawn_egg", PRISMALLARD);
		GLITTERWING_SPAWN_EGG = egg("glitterwing_spawn_egg", GLITTERWING);
		SHROUDWING_SPAWN_EGG = egg("shroudwing_spawn_egg", SHROUDWING);
		AERWHALE_SPAWN_EGG = egg("aerwhale_spawn_egg", AERWHALE);
	}

	// ---- where they may stand ----

	private static <T extends Animal> void ground(EntityType<T> type) {
		SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
	}

	private static boolean bright(LevelAccessor level, BlockPos pos) {
		return level.getRawBrightness(pos, 0) > LIGHT;
	}

	private static void placements() {
		ground(KIRRID);
		ground(BURRUKAI);
		ground(TAEGORE);
		// the prismallard: floating on a lake, with open air over it
		SpawnPlacements.register(PRISMALLARD, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(t, level, reason, pos, random) -> level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getBlockState(pos).isAir() && bright(level, pos));
		// insects: in the air in daylight (the glitterwings of the dark in the dark: any light), not over water
		SpawnPlacements.register(GLITTERWING, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(t, level, reason, pos, random) -> level.getBlockState(pos).isAir() && !level.getFluidState(pos.below()).is(FluidTags.WATER));
		SpawnPlacements.register(SHROUDWING, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(t, level, reason, pos, random) -> level.getBlockState(pos).isAir() && bright(level, pos) && !level.getFluidState(pos.below()).is(FluidTags.WATER));
		// the whale: open sky, light, no fluid, one roll in forty
		SpawnPlacements.register(AERWHALE, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(t, level, reason, pos, random) -> level.getFluidState(pos).isEmpty() && bright(level, pos) && level.canSeeSky(pos)
				&& (reason != EntitySpawnReason.NATURAL || random.nextInt(WHALE_ROLL) == 0));
	}

	// ---- where they live ----

	private static void spawns() {
		// the three climates (the irradiated wastes have no animals, nor do the caves)
		Predicate<BiomeSelectionContext> zones = BiomeSelectors.tag(Zone.tagOf(Zone.HIGHFIELDS)).or(BiomeSelectors.tag(Zone.tagOf(Zone.MAGNETIC)))
			.or(BiomeSelectors.tag(Zone.tagOf(Zone.ARCTIC)));
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, TAEGORE, COMMON_WEIGHT, TAEGORE_MIN, TAEGORE_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, BURRUKAI, COMMON_WEIGHT, BURRUKAI_MIN, BURRUKAI_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, KIRRID, COMMON_WEIGHT, KIRRID_MIN, KIRRID_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, AetherMobs.AERBUNNY, COMMON_WEIGHT, BUNNY_MIN, BUNNY_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, AetherMobs.PHYG, RARE_WEIGHT, HERD_MIN, HERD_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, AetherMobs.FLYING_COW, RARE_WEIGHT, HERD_MIN, HERD_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, AetherMobs.SHEEPUFF, RARE_WEIGHT, HERD_MIN, HERD_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.AMBIENT, GLITTERWING, RARE_WEIGHT, INSECT_MIN, INSECT_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.AMBIENT, SHROUDWING, RARE_WEIGHT, INSECT_MIN, INSECT_MAX);
		BiomeModifications.addSpawn(zones, MobCategory.CREATURE, AERWHALE, RARE_WEIGHT, 1, 1);
		// lakes: the highfields', but not the dry Flourishing Field
		Predicate<BiomeSelectionContext> lakes = BiomeSelectors.tag(Zone.tagOf(Zone.HIGHFIELDS))
			.and(BiomeSelectors.excludeByKey(ResourceKey.create(Registries.BIOME, JaymosPowers.id("flourishing_field"))));
		BiomeModifications.addSpawn(lakes, MobCategory.CREATURE, PRISMALLARD, RARE_WEIGHT, PRISMALLARD_MIN, PRISMALLARD_MAX);
	}

	// ---- skyroot lizards from leaves ----

	/** One time in {@link #LIZARD_ODDS} that a leaf block's loot drops (broken or decayed), a lizard of that leaf's kind appears. */
	private static void lizardFromLeaves(net.minecraft.world.level.storage.loot.LootContext context) {
		var state = context.getOptional(LootContextParams.BLOCK_STATE);
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		if (state == null || origin == null) {
			return;
		}
		int variant = SkyrootLizard.variantOfLeaves(state.getBlock());
		ServerLevel level = context.getLevel();
		if (variant < 0 || level.getRandom().nextInt(LIZARD_ODDS) != 0) {
			return;
		}
		SkyrootLizard lizard = SKYROOT_LIZARD.create(level, EntitySpawnReason.TRIGGERED);
		if (lizard != null) {
			lizard.setVariant(variant);
			lizard.snapTo(origin.x, origin.y, origin.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(lizard);
		}
	}

	// ---- registration ----

	private static void tab() {
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("aether"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> {
			for (Item item : List.of(RAW_TAEGORE_MEAT, COOKED_TAEGORE_MEAT, BURRUKAI_RIB_CUT, COOKED_BURRUKAI_RIB_CUT, KIRRID_LOIN, KIRRID_CUTLET,
				PRISMALLARD_LEG, PRISMALLARD_ROAST, SKYROOT_LIZARD_ON_A_STICK, BEAST_PELT, BURRUKAI_PLATE, PRISMALLARD_FEATHER, MOA_FEATHER, KIRRID_PLATE,
				PRISMALLARD_EGG, KIRRID_SPAWN_EGG, BURRUKAI_SPAWN_EGG, TAEGORE_SPAWN_EGG, SKYROOT_LIZARD_SPAWN_EGG, PRISMALLARD_SPAWN_EGG,
				GLITTERWING_SPAWN_EGG, SHROUDWING_SPAWN_EGG, AERWHALE_SPAWN_EGG)) {
				output.accept(item);
			}
		});
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item meat(String id, int nutrition, float saturation) {
		return item(id, Item::new, new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(),
			Consumables.defaultFood().build()));
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item egg(String id, EntityType<? extends Mob> type) {
		return item(id, p -> new SpawnEggItem(p.spawnEgg(type)), new Item.Properties());
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
