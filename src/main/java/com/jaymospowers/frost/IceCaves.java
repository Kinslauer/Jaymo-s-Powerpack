package com.jaymospowers.frost;

import com.jaymospowers.JaymosPowers;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Ice Caves (26.4's cave biome, built for 26.3 from the snapshot notes; none of Mojang's code or art). Andy,
 * 2026-11-12, on the left-out list: "add it all".
 * <ul>
 * <li>Where: under the colder lands, a cave biome like the lush and dripstone caves ({@link #placing}). The driest cold
 * ground (the tundra and dry snowy plains) keeps its old rock: no meltwater, no ice.</li>
 * <li>The rock: packed ice, with bands of calcite (material rule {@code jaymospowers:ice_caves}, run from our copy of
 * {@code minecraft:overworld}). Ores sit in pockets of stone or deepslate inside the ice ({@link #standIn}), so every ore,
 * the world tier's veins (Kingdoms' GroundChanges) and the great veins (BigVeins) still come out here.</li>
 * <li>What grows in it ({@link IceCaveFeatures}): large icicles of packed ice on the floors and ceilings, icicles, ice
 * crystals, snow patches, no glow lichen; and crevasses, winding cracks in the floor roofed over with brittle ice.</li>
 * <li>Who lives there: the usual cave monsters, strays (which spawn here without the sky, {@link #straySpawnsHere}) and
 * Frostbites.</li>
 * <li>Water that touches blue ice from the side freezes into brittle ice ({@link #freezeOnBlueIce}).</li>
 * </ul>
 */
public final class IceCaves {
	// ---- tuning ----
	/** Colder than this (temperature, -1 to 1) lies over ice caves: the frozen row and the colder half of the cold one. */
	public static final float WARMEST = -0.3F;
	/** Drier than this (humidity) has no ice caves under it: the tundra and the dry end of the snowy plains. */
	public static final float DRIEST = -0.35F;
	/** How deep the biome reaches (the climate's depth: 0 is the surface); like the lush and dripstone caves. */
	public static final float DEPTH_MIN = 0.2F, DEPTH_MAX = 0.9F;
	/**
	 * The biome's offset: where a lush, dripstone or sulfur cave would be (they have none), that cave wins and the ice
	 * cave gives way; everywhere else under the cold it beats the surface biome reaching down.
	 */
	public static final float OFFSET = 0.1F;
	/** An ore pocket is deepslate below this height, stone above. */
	public static final int DEEPSLATE_BELOW = 0;

	public static final ResourceKey<Biome> ICE_CAVES = ResourceKey.create(Registries.BIOME, JaymosPowers.id("ice_caves"));
	/** The ice cave's own rock (packed ice and calcite): where ores lay a pocket of stone first. */
	public static final TagKey<Block> ROCK = TagKey.create(Registries.BLOCK, JaymosPowers.id("ice_cave_rock"));
	/** Biomes where strays spawn with no sky over them (26.4's {@code spawns_strays_without_powder_snow}). */
	public static final TagKey<Biome> STRAYS_UNDERGROUND = TagKey.create(Registries.BIOME, JaymosPowers.id("spawns_strays_underground"));
	/** What's light enough to cross brittle ice (26.4's {@code does_not_break_brittle_ice}). */
	public static final TagKey<EntityType<?>> LIGHT_ON_ICE = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("does_not_break_brittle_ice"));

	private IceCaves() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("crevasse"), IceCaveFeatures.Crevasse.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("large_icicle"), IceCaveFeatures.LargeIcicle.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("ice_cave_icicles"), IceCaveFeatures.Icicles.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("ice_cave_floor"), IceCaveFeatures.Floor.CODEC);
	}

	/**
	 * Wraps the Overworld biome list as it is built: when the underground biomes start (the dripstone caves come first),
	 * the ice caves go in beside them, under the cold and not the driest of it.
	 */
	public static Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> placing(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		return pair -> {
			out.accept(pair);
			if (pair.getSecond() == Biomes.DRIPSTONE_CAVES) {
				Climate.Parameter full = Climate.Parameter.span(-1.0F, 1.0F);
				out.accept(Pair.of(Climate.parameters(Climate.Parameter.span(-1.0F, WARMEST), Climate.Parameter.span(DRIEST, 1.0F), full, full,
					Climate.Parameter.span(DEPTH_MIN, DEPTH_MAX), full, OFFSET), ICE_CAVES));
			}
		};
	}

	/**
	 * If {@code state} at {@code pos} is ice cave rock, the stone (or deepslate, deep down) an ore would have found there;
	 * otherwise null. Ore features try their targets against it, so ore comes out in the ice as it does in stone.
	 */
	public static @Nullable BlockState standIn(LevelReader level, BlockPos pos, BlockState state) {
		if (!state.is(ROCK) || !level.getBiome(pos).is(ICE_CAVES)) {
			return null;
		}
		return standIn(pos);
	}

	/** The pocket rock at this height. */
	public static BlockState standIn(BlockPos pos) {
		return (pos.getY() < DEEPSLATE_BELOW ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
	}

	/**
	 * After an ore is laid at {@code ore}: if any of the six blocks round it is ice cave rock and this is an ice cave, those
	 * become the pocket's stone. (An ore laid in stone an earlier ore's pocket left still gets its own pocket.)
	 */
	public static void pocket(LevelReader level, Function<BlockPos, BlockState> getter, BlockPos ore, PocketWriter writer) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		boolean ice = false;
		for (Direction d : Direction.values()) {
			at.setWithOffset(ore, d);
			ice |= getter.apply(at).is(ROCK);
		}
		if (!ice || !level.getBiome(ore).is(ICE_CAVES)) {
			return;
		}
		for (Direction d : Direction.values()) {
			at.setWithOffset(ore, d);
			writer.write(at, standIn(at));
		}
	}

	/** Writes one pocket block if the block there is still ice cave rock. */
	@FunctionalInterface
	public interface PocketWriter {
		void write(BlockPos pos, BlockState stone);
	}

	/** Whether worldgen may write at {@code pos} (inside the chunks being made, and inside the world's height). */
	public static boolean writable(WorldGenLevel level, BlockPos pos) {
		return !level.isOutsideBuildHeight(pos) && (!(level instanceof WorldGenRegion region) || region.isWithinWriteZone(pos));
	}

	/** Whether strays may spawn at {@code pos} with no sky over them. */
	public static boolean straySpawnsHere(LevelReader level, BlockPos pos) {
		return level.getBiome(pos).is(STRAYS_UNDERGROUND);
	}

	/**
	 * Water at {@code pos} with blue ice beside it (north, south, east or west) freezes into brittle ice. True if it did,
	 * and the water then doesn't flow.
	 */
	public static boolean freezeOnBlueIce(Level level, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(d)).is(Blocks.BLUE_ICE)) {
				level.setBlockAndUpdate(pos, Frost.BRITTLE_ICE.defaultBlockState());
				level.playSound(null, pos, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.6F, 1.6F + level.getRandom().nextFloat() * 0.3F);
				return true;
			}
		}
		return false;
	}
}
