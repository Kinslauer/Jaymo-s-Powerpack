package com.jaymoskingdoms.glowwood.creatures;

import com.jaymoskingdoms.GlowHavenLife;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.glowwood.GlowWoods;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The glow-woods' creatures, the Myceliarch and the things it drops (ported from Spore &amp; Stem, Apache-2.0; see
 * {@code licenses/spore-and-stem}). Sporelings, trufflers and glowmoths live round the giant fungi: round Sylvan havens
 * ({@link GlowHavenLife}) and, sparsely, by the wild giants. Mimicaps and bloatshrooms keep to the old dark woods, never within
 * 64 blocks of a haven. Shamblers come only from the spore-rot's dead ({@link Mycosis}) and the Myceliarch's brood.
 */
public final class GlowCreatures {
	// ---- tuning ---------------------------------------------------------------------------------------------------
	/** The Myceliarch's health, and the weight of its blows. */
	public static final double MYCELIARCH_HEALTH = 300.0, MYCELIARCH_DAMAGE = 1.0;
	/** A tamed sporeling sheds a mushroom every this many to this many minutes. */
	public static final int SHED_MIN_MINUTES = 5, SHED_MAX_MINUTES = 10;
	/** A truffler's chance to dig up a truffle when it stops to sniff. */
	public static final double TRUFFLER_DIG_CHANCE = 0.15;
	/** Mimicaps and bloatshrooms never spawn this near a Sylvan haven (blocks). */
	public static final double HAVEN_CLEAR = 64.0;
	/** A giant must stand within this far (blocks) for a wild sporeling, truffler or moth to be born, and this many tries find it. */
	static final int GIANT_REACH = 14, GIANT_PROBES = 40;
	/** The deep woods: this many of the columns over a spot must be roofed (see {@link GlowHavenLife#shaded}). */
	static final int DEEP_LIGHT = 9;

	// ---- tags -------------------------------------------------------------------------------------------------------
	/** What the rot and the spores pass over: the fungal creatures themselves. */
	public static final TagKey<EntityType<?>> FUNGAL = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("fungal"));
	/** What rises as a shambler when it dies of the rot. */
	public static final TagKey<EntityType<?>> MYCOSIS_RISERS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("mycosis_risers"));
	/** Where a Fruiting Heart calls up the Myceliarch. */
	public static final TagKey<Block> SUMMON_BLOCKS = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("myceliarch_summon_blocks"));
	public static final TagKey<Item> MYCOLEATHER_REPAIRS = TagKey.create(Registries.ITEM, JaymosKingdoms.id("mycoleather_repairs"));
	public static final TagKey<Item> BLOOM_REPAIRS = TagKey.create(Registries.ITEM, JaymosKingdoms.id("bloom_repairs"));
	private static final TagKey<net.minecraft.world.level.biome.Biome> HAS_GLOW_CREATURES =
		TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_glow_creatures"));
	private static final TagKey<net.minecraft.world.level.biome.Biome> HAS_DEEP_WOOD_MONSTERS =
		TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_deep_wood_monsters"));

	// ---- armour ------------------------------------------------------------------------------------------------------
	public static final ArmorMaterial MYCOLEATHER_ARMOR = new ArmorMaterial(14, defense(2, 5, 4, 2), 18, SoundEvents.ARMOR_EQUIP_LEATHER,
		0.0F, 0.0F, MYCOLEATHER_REPAIRS, asset("mycoleather"));
	public static final ArmorMaterial BLOOM_ARMOR = new ArmorMaterial(40, defense(3, 3, 3, 3), 22, SoundEvents.ARMOR_EQUIP_GOLD,
		2.0F, 0.0F, BLOOM_REPAIRS, asset("bloom"));

	// ---- registered -------------------------------------------------------------------------------------------------
	public static Holder<MobEffect> ROOTED;
	public static EntityType<SporelingEntity> SPORELING;
	public static EntityType<TrufflerEntity> TRUFFLER;
	public static EntityType<GlowmothEntity> GLOWMOTH;
	public static EntityType<MimicapEntity> MIMICAP;
	public static EntityType<BloatshroomEntity> BLOATSHROOM;
	public static EntityType<ShamblerEntity> SHAMBLER;
	public static EntityType<MyceliarchEntity> MYCELIARCH;
	public static EntityType<RootSpikeEntity> ROOT_SPIKE;
	public static EntityType<SporeGlobEntity> SPORE_GLOB;
	public static Item LUMEN_SPORES, MYCOLEATHER, MYCELIAL_CORE, SPORE_HEART, FRUITING_HEART, SPORE_GLOB_ITEM;
	public static Item MYCOLEATHER_HOOD, MYCOLEATHER_TUNIC, MYCOLEATHER_LEGGINGS, MYCOLEATHER_BOOTS, BLOOMREAVER, CROWN_OF_THE_BLOOM;
	private static final Map<String, Item> EGGS = new java.util.LinkedHashMap<>();

	private GlowCreatures() {
	}

	/** Slows and roots whatever carries it (the Mimicap's bite, the Myceliarch's roots). */
	static final class Rooted extends net.minecraft.world.effect.MobEffect {
		Rooted() {
			super(MobEffectCategory.HARMFUL, 0x6B4F2A);
		}
	}

	public static void register() {
		GlowSounds.register();
		Mycosis.register();
		ROOTED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id("rooted"), new Rooted()
			.addAttributeModifier(Attributes.MOVEMENT_SPEED, JaymosKingdoms.id("effect.rooted"), -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
			.addAttributeModifier(Attributes.JUMP_STRENGTH, JaymosKingdoms.id("effect.rooted"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

		SPORELING = living("sporeling", EntityType.Builder.of(SporelingEntity::new, MobCategory.CREATURE).sized(0.55F, 0.7F).eyeHeight(0.35F)
			.clientTrackingRange(8), SporelingEntity.createAttributes());
		TRUFFLER = living("truffler", EntityType.Builder.of(TrufflerEntity::new, MobCategory.CREATURE).sized(0.9F, 0.9F).eyeHeight(0.6F)
			.clientTrackingRange(10), TrufflerEntity.createAttributes());
		GLOWMOTH = living("glowmoth", EntityType.Builder.of(GlowmothEntity::new, MobCategory.AMBIENT).sized(0.5F, 0.4F).eyeHeight(0.2F)
			.clientTrackingRange(5), GlowmothEntity.createAttributes());
		MIMICAP = living("mimicap", EntityType.Builder.of(MimicapEntity::new, MobCategory.MONSTER).sized(1.0F, 0.9F).eyeHeight(0.6F)
			.clientTrackingRange(8), MimicapEntity.createAttributes());
		BLOATSHROOM = living("bloatshroom", EntityType.Builder.of(BloatshroomEntity::new, MobCategory.MONSTER).sized(0.8F, 1.0F).eyeHeight(0.7F)
			.clientTrackingRange(8), BloatshroomEntity.createAttributes());
		SHAMBLER = living("shambler", EntityType.Builder.of(ShamblerEntity::new, MobCategory.MONSTER).sized(0.7F, 2.1F).eyeHeight(1.8F)
			.clientTrackingRange(8), ShamblerEntity.createAttributes());
		MYCELIARCH = living("myceliarch", EntityType.Builder.of(MyceliarchEntity::new, MobCategory.MONSTER).sized(2.0F, 4.2F).eyeHeight(3.4F)
			.fireImmune().clientTrackingRange(10), MyceliarchEntity.createAttributes());
		ROOT_SPIKE = plain("root_spike", EntityType.Builder.<RootSpikeEntity>of(RootSpikeEntity::new, MobCategory.MISC).sized(0.8F, 1.2F)
			.clientTrackingRange(6).updateInterval(2));
		SPORE_GLOB = plain("spore_glob", EntityType.Builder.<SporeGlobEntity>of(SporeGlobEntity::new, MobCategory.MISC).sized(0.3F, 0.3F)
			.clientTrackingRange(4).updateInterval(10));

		LUMEN_SPORES = item("lumen_spores", Item::new, p -> p);
		MYCOLEATHER = item("mycoleather", Item::new, p -> p);
		MYCELIAL_CORE = item("mycelial_core", Item::new, p -> p.rarity(Rarity.UNCOMMON));
		SPORE_HEART = item("spore_heart", Item::new, p -> p.stacksTo(16).rarity(Rarity.EPIC).fireResistant());
		FRUITING_HEART = item("fruiting_heart", FruitingHeartItem::new, p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
		SPORE_GLOB_ITEM = item("spore_glob", Item::new, p -> p);
		MYCOLEATHER_HOOD = item("mycoleather_hood", p -> new MycoleatherArmorItem(ArmorType.HELMET, p), p -> p.humanoidArmor(MYCOLEATHER_ARMOR, ArmorType.HELMET));
		MYCOLEATHER_TUNIC = item("mycoleather_tunic", p -> new MycoleatherArmorItem(ArmorType.CHESTPLATE, p), p -> p.humanoidArmor(MYCOLEATHER_ARMOR, ArmorType.CHESTPLATE));
		MYCOLEATHER_LEGGINGS = item("mycoleather_leggings", p -> new MycoleatherArmorItem(ArmorType.LEGGINGS, p), p -> p.humanoidArmor(MYCOLEATHER_ARMOR, ArmorType.LEGGINGS));
		MYCOLEATHER_BOOTS = item("mycoleather_boots", p -> new MycoleatherArmorItem(ArmorType.BOOTS, p), p -> p.humanoidArmor(MYCOLEATHER_ARMOR, ArmorType.BOOTS));
		BLOOMREAVER = item("bloomreaver", BloomreaverItem::new, p -> p.rarity(Rarity.EPIC).fireResistant().sword(BloomreaverItem.TIER, 4.0F, -2.4F));
		CROWN_OF_THE_BLOOM = item("crown_of_the_bloom", CrownOfTheBloomItem::new,
			p -> p.rarity(Rarity.EPIC).fireResistant().humanoidArmor(BLOOM_ARMOR, ArmorType.HELMET).durability(600));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(LUMEN_SPORES);
			output.accept(MYCOLEATHER);
			output.accept(MYCELIAL_CORE);
			output.accept(SPORE_HEART);
			output.accept(FRUITING_HEART);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(MYCOLEATHER_HOOD);
			output.accept(MYCOLEATHER_TUNIC);
			output.accept(MYCOLEATHER_LEGGINGS);
			output.accept(MYCOLEATHER_BOOTS);
			output.accept(BLOOMREAVER);
			output.accept(CROWN_OF_THE_BLOOM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> EGGS.values().forEach(output::accept));

		// the wild: sparsely, by the giants
		SpawnPlacements.register(SPORELING, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SporelingEntity::checkSpawnRules);
		SpawnPlacements.register(TRUFFLER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, TrufflerEntity::checkSpawnRules);
		SpawnPlacements.register(GLOWMOTH, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GlowmothEntity::checkSpawnRules);
		SpawnPlacements.register(MIMICAP, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GlowCreatures::deepWoodAnyLight);
		SpawnPlacements.register(BLOATSHROOM, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GlowCreatures::deepWoodDark);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_GLOW_CREATURES), MobCategory.CREATURE, SPORELING, 3, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_GLOW_CREATURES), MobCategory.CREATURE, TRUFFLER, 2, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_GLOW_CREATURES), MobCategory.AMBIENT, GLOWMOTH, 3, 1, 3);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_DEEP_WOOD_MONSTERS), MobCategory.MONSTER, MIMICAP, 4, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_DEEP_WOOD_MONSTERS), MobCategory.MONSTER, BLOATSHROOM, 10, 1, 2);
	}

	// ---- spawn rules ------------------------------------------------------------------------------------------------

	/** Whether a giant fungus's cap or gills stand within {@link #GIANT_REACH} blocks of {@code pos} (sampled, not scanned). */
	public static boolean nearGiant(LevelAccessor level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < GIANT_PROBES; i++) {
			BlockPos probe = pos.offset(random.nextInt(GIANT_REACH * 2 + 1) - GIANT_REACH, random.nextInt(18) + 1, random.nextInt(GIANT_REACH * 2 + 1) - GIANT_REACH);
			if (GlowWoods.isCapOrGill(level.getBlockState(probe))) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code pos} is deep wood: roofed over, and far from every Sylvan haven. */
	private static boolean deepWood(LevelAccessor access, BlockPos pos) {
		if (!(access instanceof ServerLevelAccessor accessor)) {
			return false;
		}
		net.minecraft.server.level.ServerLevel level = accessor.getLevel();
		if (GlowHavenLife.sylvanNear(level, pos, HAVEN_CLEAR)) {
			return false;
		}
		int roofed = 0;
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				for (int y = 3; y <= 14; y++) {
					BlockState s = level.getBlockState(pos.offset(dx, y, dz));
					if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
						roofed++;
						break;
					}
				}
			}
		}
		return roofed >= DEEP_LIGHT - 2;
	}

	private static boolean deepWoodAnyLight(EntityType<MimicapEntity> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random) && (reason == EntitySpawnReason.SPAWNER || deepWood(level, pos));
	}

	private static boolean deepWoodDark(EntityType<BloatshroomEntity> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && (reason == EntitySpawnReason.SPAWNER || deepWood(level, pos));
	}

	// ---- registration helpers ---------------------------------------------------------------------------------------

	private static <T extends Mob> EntityType<T> living(String name, EntityType.Builder<T> builder, AttributeSupplier.Builder attributes) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		FabricDefaultAttributeRegistry.register((EntityType<? extends LivingEntity>)type, attributes);
		ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_spawn_egg"));
		EGGS.put(name, Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(type))));
		return type;
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> plain(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.apply(new Item.Properties().setId(key))));
	}

	private static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id(name));
	}

	private static Map<ArmorType, Integer> defense(int helmet, int chest, int legs, int boots) {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.HELMET, helmet);
		map.put(ArmorType.CHESTPLATE, chest);
		map.put(ArmorType.LEGGINGS, legs);
		map.put(ArmorType.BOOTS, boots);
		map.put(ArmorType.BODY, chest);
		return map;
	}
}
