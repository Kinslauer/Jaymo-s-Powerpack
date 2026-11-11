package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The imps of the Nether ({@link Imp}): the demons' small flying kin, who came up with them to the sorcerer's battle.
 * They fly in flights over the wastes, the crimson forest and the basalt deltas; the zombified ones drift over the
 * wastes and the soul sand valleys. Their fireballs ({@link ImpFireball}) light everything round where they land
 * ({@link #blaze}).
 */
public final class Imps {
	/** Natural spawns (a zombified piglin is 100 in the wastes), and how many come together. */
	static final int IMP_WEIGHT = 6, IMP_MIN = 2, IMP_MAX = 4, ZOMBIFIED_WEIGHT = 3, ZOMBIFIED_MIN = 1, ZOMBIFIED_MAX = 3;
	static final float WIDTH = 0.7F, HEIGHT = 1.4F;
	/** How far round an imp fires are lit when its own ball goes off in its hands. */
	static final int DETONATE_BLAZE_RADIUS = 3;
	/** Of a blaze's fires, the share that may go on bare ground when there is nothing near that burns. */
	static final float BARE_GROUND_SHARE = 0.35F;

	public static EntityType<Imp> IMP, ZOMBIFIED_IMP;
	public static EntityType<ImpFireball> IMP_FIREBALL;

	private Imps() {
	}

	public static void register() {
		IMP = Beasts.type("imp", EntityType.Builder.<Imp>of((type, level) -> new Imp(type, level, false), MobCategory.MONSTER)
			.sized(WIDTH, HEIGHT).fireImmune().clientTrackingRange(10), Imp.createAttributes(false));
		ZOMBIFIED_IMP = Beasts.type("zombified_imp", EntityType.Builder.<Imp>of((type, level) -> new Imp(type, level, true), MobCategory.MONSTER)
			.sized(WIDTH, HEIGHT).fireImmune().clientTrackingRange(10), Imp.createAttributes(true));
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("imp_fireball"));
		IMP_FIREBALL = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<ImpFireball>of(ImpFireball::new, MobCategory.MISC)
			.noLootTable().sized(1.0F, 1.0F).clientTrackingRange(6).updateInterval(10).build(key));

		for (EntityType<Imp> type : List.of(IMP, ZOMBIFIED_IMP)) {
			SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Monster::checkAnyLightMonsterSpawnRules);
		}
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.NETHER_WASTES, Biomes.CRIMSON_FOREST, Biomes.BASALT_DELTAS),
			MobCategory.MONSTER, IMP, IMP_WEIGHT, IMP_MIN, IMP_MAX);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY),
			MobCategory.MONSTER, ZOMBIFIED_IMP, ZOMBIFIED_WEIGHT, ZOMBIFIED_MIN, ZOMBIFIED_MAX);
	}

	/**
	 * Sets what is round {@code center} ablaze: up to {@code fires} fires within {@code radius} blocks, on leaves, logs
	 * and anything else that burns first, the rest on bare ground. Nothing where mob griefing is off.
	 */
	public static void blaze(ServerLevel level, BlockPos center, int radius, int fires) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		List<BlockPos> fuel = new ArrayList<>(), bare = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (pos.distSqr(center) > radius * radius + 1 || !level.getBlockState(pos).isAir() || !BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
				continue;
			}
			boolean burns = false;
			for (Direction side : Direction.values()) {
				if (level.getBlockState(pos.relative(side)).ignitedByLava()) {
					burns = true;
					break;
				}
			}
			(burns ? fuel : bare).add(pos.immutable());
		}
		Collections.shuffle(fuel, new java.util.Random(level.getRandom().nextLong()));
		Collections.shuffle(bare, new java.util.Random(level.getRandom().nextLong()));
		int lit = 0;
		for (BlockPos pos : fuel) {
			if (lit >= fires) {
				return;
			}
			level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
			lit++;
		}
		int onGround = Math.min(fires - lit, Math.max(1, Math.round(fires * BARE_GROUND_SHARE)));
		for (int i = 0; i < onGround && i < bare.size(); i++) {
			level.setBlock(bare.get(i), BaseFireBlock.getState(level, bare.get(i)), Block.UPDATE_ALL);
		}
	}
}
