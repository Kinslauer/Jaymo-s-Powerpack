package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * (Kingdoms 0.7.131) The Nether's own faction, stage 1: the {@link Hornkin} and their feud with the piglins over gold
 * (our own take on Just-In NETHER, which is All Rights Reserved: nothing of it is used). They come in war parties in
 * the Nether wastes and the crimson forests, where the piglins are. Their forts and warlords are the next stage (see
 * project-files nether-faction/Nether-Plan.md).
 */
public final class Nether {
	/** Natural spawns (a zombified piglin is 100 in the wastes), and how many come together. */
	static final int WARRIOR_WEIGHT = 8, WARRIOR_MIN = 2, WARRIOR_MAX = 4, HURLER_WEIGHT = 4, HURLER_MIN = 1, HURLER_MAX = 2, BRUTE_WEIGHT = 1;

	public static EntityType<Hornkin> HORNKIN, HORNKIN_HURLER, HORNKIN_BRUTE, ZOMBIFIED_HORNKIN, HORNKIN_WARLORD;
	public static EntityType<MagmaBomb> MAGMA_BOMB;
	public static Item HORNKIN_SPAWN_EGG, HORNKIN_HURLER_SPAWN_EGG, HORNKIN_BRUTE_SPAWN_EGG, ZOMBIFIED_HORNKIN_SPAWN_EGG, HORNKIN_WARLORD_SPAWN_EGG;

	private Nether() {
	}

	public static void register() {
		HORNKIN = hornkin("hornkin", Hornkin.Kind.WARRIOR, 0.7F, 2.1F);
		HORNKIN_HURLER = hornkin("hornkin_hurler", Hornkin.Kind.HURLER, 0.7F, 2.1F);
		HORNKIN_BRUTE = hornkin("hornkin_brute", Hornkin.Kind.BRUTE, 1.0F, 2.8F);
		ZOMBIFIED_HORNKIN = hornkin("zombified_hornkin", Hornkin.Kind.ZOMBIFIED, 0.7F, 2.1F);
		// (0.7.142) the war forts' warlords (never spawned wild: see NetherForts)
		HORNKIN_WARLORD = hornkin("hornkin_warlord", Hornkin.Kind.WARLORD, 1.2F, 3.4F);
		MAGMA_BOMB = type("magma_bomb", EntityType.Builder.<MagmaBomb>of(MagmaBomb::new, MobCategory.MISC).noLootTable().sized(0.3F, 0.3F)
			.clientTrackingRange(4).updateInterval(10));

		for (EntityType<Hornkin> type : java.util.List.of(HORNKIN, HORNKIN_HURLER, HORNKIN_BRUTE)) {
			SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(t, level, reason, pos, random) -> !level.getBlockState(pos.below()).is(Blocks.NETHER_WART_BLOCK)
					&& Monster.checkAnyLightMonsterSpawnRules(t, level, reason, pos, random));
		}
		var wastes = BiomeSelectors.includeByKey(Biomes.NETHER_WASTES, Biomes.CRIMSON_FOREST);
		BiomeModifications.addSpawn(wastes, MobCategory.MONSTER, HORNKIN, WARRIOR_WEIGHT, WARRIOR_MIN, WARRIOR_MAX);
		BiomeModifications.addSpawn(wastes, MobCategory.MONSTER, HORNKIN_HURLER, HURLER_WEIGHT, HURLER_MIN, HURLER_MAX);
		BiomeModifications.addSpawn(wastes, MobCategory.MONSTER, HORNKIN_BRUTE, BRUTE_WEIGHT, 1, 1);

		HORNKIN_SPAWN_EGG = egg("hornkin_spawn_egg", HORNKIN);
		HORNKIN_HURLER_SPAWN_EGG = egg("hornkin_hurler_spawn_egg", HORNKIN_HURLER);
		HORNKIN_BRUTE_SPAWN_EGG = egg("hornkin_brute_spawn_egg", HORNKIN_BRUTE);
		ZOMBIFIED_HORNKIN_SPAWN_EGG = egg("zombified_hornkin_spawn_egg", ZOMBIFIED_HORNKIN);
		HORNKIN_WARLORD_SPAWN_EGG = egg("hornkin_warlord_spawn_egg", HORNKIN_WARLORD);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.insertAfter(Items.PIGLIN_BRUTE_SPAWN_EGG, HORNKIN_SPAWN_EGG);
			output.insertAfter(HORNKIN_SPAWN_EGG, HORNKIN_HURLER_SPAWN_EGG);
			output.insertAfter(HORNKIN_HURLER_SPAWN_EGG, HORNKIN_BRUTE_SPAWN_EGG);
			output.insertAfter(HORNKIN_BRUTE_SPAWN_EGG, HORNKIN_WARLORD_SPAWN_EGG);
			output.insertAfter(HORNKIN_WARLORD_SPAWN_EGG, ZOMBIFIED_HORNKIN_SPAWN_EGG);
		});
		// (0.7.142) stage 2: the war forts, their warlords and war hosts (and their attachments)
		JaymosKingdoms.LOGGER.debug("nether fort attachments: {} {}", NetherForts.FORTS, NetherForts.TAG);
		NetherForts.register();
		// (0.7.146) stage 3: portal raids on the towns, trophies
		NetherTrophies.register();
		PortalRaids.register();
	}

	private static EntityType<Hornkin> hornkin(String name, Hornkin.Kind kind, float width, float height) {
		EntityType<Hornkin> type = type(name, EntityType.Builder.<Hornkin>of((t, level) -> new Hornkin(t, level, kind), MobCategory.MONSTER)
			.sized(width, height).fireImmune().clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(type, Hornkin.createAttributes(kind));
		return type;
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item egg(String name, EntityType<Hornkin> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
	}
}
