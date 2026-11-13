package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.DamageKinds;
import com.jaymoskingdoms.Doubles;
import com.jaymoskingdoms.Deafened;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.ShadowDrain;
import com.jaymoskingdoms.WorldTiers;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The creatures of the fifth edition's patch 9: the troll, rust beast, basilisk, clear cube, piercer, cloak flier, blood
 * bat, bog light, harpy, digger, shadow and shapeshifter, with the quicksand, the trap cover, the hand mirror and the ear
 * wax they go with. Where each is met is in {@link Haunts} (placed round the player) and the biome spawns below.
 */
public final class Creatures {
	public static EntityType<Troll> TROLL;
	public static EntityType<TrollLimb> TROLL_LIMB;
	public static EntityType<RustBeast> RUST_BEAST;
	public static EntityType<Basilisk> BASILISK;
	public static EntityType<ClearCube> CLEAR_CUBE;
	public static EntityType<Piercer> PIERCER;
	public static EntityType<CloakFlier> CLOAK_FLIER;
	public static EntityType<BloodBat> BLOOD_BAT;
	public static EntityType<BogLight> BOG_LIGHT;
	public static EntityType<Harpy> HARPY;
	public static EntityType<Digger> DIGGER;
	public static EntityType<Shadow> SHADOW;
	public static EntityType<Shapeshifter> SHAPESHIFTER;
	public static Item MIRROR, EAR_WAX;
	public static Item TROLL_EGG, RUST_BEAST_EGG, BASILISK_EGG, CLEAR_CUBE_EGG, PIERCER_EGG, CLOAK_FLIER_EGG, BLOOD_BAT_EGG, BOG_LIGHT_EGG,
		HARPY_EGG, DIGGER_EGG, SHADOW_EGG, SHAPESHIFTER_EGG;
	public static Block QUICKSAND, TRAP_COVER;
	public static Item QUICKSAND_ITEM, TRAP_COVER_ITEM;

	/** Spawn weights of the ones that wander in on their own. */
	static final int TROLL_WEIGHT = 3, RUST_WEIGHT = 4, BAT_WEIGHT = 2;

	private Creatures() {
	}

	public static void register() {
		TROLL = type("troll", EntityType.Builder.of(Troll::new, MobCategory.MONSTER).sized(1.0F, 2.8F).clientTrackingRange(8));
		TROLL_LIMB = type("troll_limb", EntityType.Builder.of(TrollLimb::new, MobCategory.MONSTER).sized(0.5F, 0.6F).clientTrackingRange(6));
		RUST_BEAST = type("rust_beast", EntityType.Builder.of(RustBeast::new, MobCategory.MONSTER).sized(1.3F, 1.1F).clientTrackingRange(8));
		BASILISK = type("basilisk", EntityType.Builder.of(Basilisk::new, MobCategory.MONSTER).sized(1.6F, 1.1F).clientTrackingRange(10));
		CLEAR_CUBE = type("clear_cube", EntityType.Builder.of(ClearCube::new, MobCategory.MONSTER).sized(1.9F, 1.9F).clientTrackingRange(8));
		PIERCER = type("piercer", EntityType.Builder.of(Piercer::new, MobCategory.MONSTER).sized(0.5F, 1.0F).clientTrackingRange(8));
		CLOAK_FLIER = type("cloak_flier", EntityType.Builder.of(CloakFlier::new, MobCategory.MONSTER).sized(1.3F, 0.5F).clientTrackingRange(8));
		BLOOD_BAT = type("blood_bat", EntityType.Builder.of(BloodBat::new, MobCategory.MONSTER).sized(0.5F, 0.9F).clientTrackingRange(5));
		BOG_LIGHT = type("bog_light", EntityType.Builder.of(BogLight::new, MobCategory.MONSTER).sized(0.5F, 0.5F).clientTrackingRange(10));
		HARPY = type("harpy", EntityType.Builder.of(Harpy::new, MobCategory.MONSTER).sized(0.8F, 1.7F).clientTrackingRange(10));
		DIGGER = type("digger", EntityType.Builder.of(Digger::new, MobCategory.MONSTER).sized(0.55F, 1.1F).clientTrackingRange(8));
		SHADOW = type("shadow", EntityType.Builder.of(Shadow::new, MobCategory.MONSTER).sized(0.6F, 1.9F).clientTrackingRange(8));
		SHAPESHIFTER = type("shapeshifter", EntityType.Builder.of(Shapeshifter::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(TROLL, Troll.createAttributes());
		FabricDefaultAttributeRegistry.register(TROLL_LIMB, TrollLimb.createAttributes());
		FabricDefaultAttributeRegistry.register(RUST_BEAST, RustBeast.createAttributes());
		FabricDefaultAttributeRegistry.register(BASILISK, Basilisk.createAttributes());
		FabricDefaultAttributeRegistry.register(CLEAR_CUBE, ClearCube.createAttributes());
		FabricDefaultAttributeRegistry.register(PIERCER, Piercer.createAttributes());
		FabricDefaultAttributeRegistry.register(CLOAK_FLIER, CloakFlier.createAttributes());
		FabricDefaultAttributeRegistry.register(BLOOD_BAT, BloodBat.createAttributes());
		FabricDefaultAttributeRegistry.register(BOG_LIGHT, BogLight.createAttributes());
		FabricDefaultAttributeRegistry.register(HARPY, Harpy.createAttributes());
		FabricDefaultAttributeRegistry.register(DIGGER, Digger.createAttributes());
		FabricDefaultAttributeRegistry.register(SHADOW, Shadow.createAttributes());
		FabricDefaultAttributeRegistry.register(SHAPESHIFTER, Shapeshifter.createAttributes());

		SpawnPlacements.register(TROLL, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
		SpawnPlacements.register(RUST_BEAST, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(type, level, reason, pos, random) -> pos.getY() < 50 && !level.canSeeSky(pos) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random));
		SpawnPlacements.register(BLOOD_BAT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(type, level, reason, pos, random) -> level.getLevel().isDarkOutside() && Mob.checkMobSpawnRules(type, level, reason, pos, random));
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.SWAMP, net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP), MobCategory.MONSTER, TROLL, TROLL_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, RUST_BEAST, RUST_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, BLOOD_BAT, BAT_WEIGHT, 1, 3);

		MIRROR = item("hand_mirror", Item::new, new Item.Properties().stacksTo(1));
		EAR_WAX = item("ear_wax", Wax::new, new Item.Properties().stacksTo(16));
		ResourceKey<Block> sandKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("quicksand"));
		QUICKSAND = Registry.register(BuiltInRegistries.BLOCK, sandKey, new QuicksandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
			.strength(0.5F).sound(SoundType.SAND).noCollision().noOcclusion().setId(sandKey)));
		ResourceKey<Block> coverKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("trap_cover"));
		TRAP_COVER = Registry.register(BuiltInRegistries.BLOCK, coverKey, new TrapCoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
			.strength(0.2F).sound(SoundType.GRASS).setId(coverKey)));
		QUICKSAND_ITEM = blockItem("quicksand", QUICKSAND);
		TRAP_COVER_ITEM = blockItem("trap_cover", TRAP_COVER);

		TROLL_EGG = egg("troll", TROLL);
		RUST_BEAST_EGG = egg("rust_beast", RUST_BEAST);
		BASILISK_EGG = egg("basilisk", BASILISK);
		CLEAR_CUBE_EGG = egg("clear_cube", CLEAR_CUBE);
		PIERCER_EGG = egg("piercer", PIERCER);
		CLOAK_FLIER_EGG = egg("cloak_flier", CLOAK_FLIER);
		BLOOD_BAT_EGG = egg("blood_bat", BLOOD_BAT);
		BOG_LIGHT_EGG = egg("bog_light", BOG_LIGHT);
		HARPY_EGG = egg("harpy", HARPY);
		DIGGER_EGG = egg("digger", DIGGER);
		SHADOW_EGG = egg("shadow", SHADOW);
		SHAPESHIFTER_EGG = egg("shapeshifter", SHAPESHIFTER);
		Item[] eggs = {TROLL_EGG, RUST_BEAST_EGG, BASILISK_EGG, CLEAR_CUBE_EGG, PIERCER_EGG, CLOAK_FLIER_EGG, BLOOD_BAT_EGG, BOG_LIGHT_EGG,
			HARPY_EGG, DIGGER_EGG, SHADOW_EGG, SHAPESHIFTER_EGG};
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			for (Item egg : eggs) {
				output.accept(egg);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(MIRROR);
			output.accept(EAR_WAX);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(QUICKSAND_ITEM);
			output.accept(TRAP_COVER_ITEM);
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof Troll troll) || troll.mayDie((net.minecraft.server.level.ServerLevel) entity.level()));
		DamageKinds.addKinder(source -> source.typeHolder().is(ClearCube.ACID) ? DamageKinds.Kind.ACID : null);
		DamageKinds.family("troll", e -> e instanceof Troll, DamageKinds.kinds(DamageKinds.Kind.FIRE, 1.3F, DamageKinds.Kind.ACID, 1.5F), true, null, false, false, false);
		DamageKinds.family("clear_cube", e -> e instanceof ClearCube, DamageKinds.kinds(DamageKinds.Kind.PIERCING, 0.5F, DamageKinds.Kind.ACID, 0.0F,
			DamageKinds.Kind.POISON, 0.0F, DamageKinds.Kind.FIRE, 1.2F), true, null, false, true, false);

		Latches.register();
		Gaze.register();
		Song.register();
		Packs.register();
		BogLights.register();
		ShadowDrain.register();
		Haunts.register();
		OldRules.register();
		Doubles.register();
	}

	/** How strong a thing {@code e} is for the world tiers (0 Hearth to 2 Wildlands), or -1 when it is not one of these. */
	public static int tier(LivingEntity e) {
		if (e instanceof CloakFlier) {
			return 2;
		}
		if (e instanceof BloodBat || e instanceof Digger || e instanceof BogLight) {
			return 0;
		}
		if (e instanceof Beast || e instanceof Harpy || e instanceof Shapeshifter) {
			return 1;
		}
		return -1;
	}

	/** Whether {@code player} has opened {@code tier} (0 Hearth...). */
	public static boolean open(ServerPlayer player, int tier) {
		return player.isCreative() || WorldTiers.progress(player).open() >= tier;
	}

	/** The ear wax: stopped ears hear no song, no shout and no spell's word. */
	static final class Wax extends Item {
		Wax(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (Deafened.is(player)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				Deafened.ring(player, Song.WAXED_TICKS);
				level.playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
				stack.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	private static Item egg(String name, EntityType<? extends Mob> type) {
		return item(name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}
}
