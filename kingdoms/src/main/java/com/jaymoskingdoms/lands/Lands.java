package com.jaymoskingdoms.lands;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.blossomland.BlossomAnimals;
import com.jaymoskingdoms.seas.Mohknee;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Four new lands (docs/Climate-Plan.md, track E), each its own biome in {@code data/jaymoskingdoms/worldgen/biome}:
 * <ul>
 * <li><b>Steam valley</b> ({@link #STEAM_VALLEY}), a rare cold-mountain valley on Mohknee: pools of hot water on terraces of
 * calcite ({@link LandFeatures.SpringTerrace}), each over a magma floor so that bathing in it really warms the body
 * ({@code BodyHeat.hotSpring}). Its base temperature is held out of the seasons' reach, so it is mild all winter.</li>
 * <li><b>Ashlands</b> ({@link #ASHLANDS}), a black-sand land on Mohknee with lava vents, ash and rich ore. Very hot: it
 * is harsh land for foes ({@code HarshLands} reads it as {@link #VOLCANIC_TEMPERATURE} whatever the climate map says).
 * Black sand dug there gives Mohknee's iron sand ({@code BlossomSteel}).</li>
 * <li><b>Salt pans</b> ({@link #SALT_PANS}), white crusts of salt in the home continent's dry hot belt (desert country).
 * The crust gives Kitchen's salt, which cures meat; salty food parches the eater ({@link SaltyFood}).</li>
 * <li><b>Steppe</b> ({@link #STEPPE}), dry cold grassland on the home continent: mild days, bitter nights.</li>
 * </ul>
 * <b>Where they lie.</b> The Overworld's biome points are the game's own ({@code OverworldBiomeBuilder}); Fabric has no
 * Overworld biome API in 26.3. So {@code mixin.OverworldBiomeBuilderMixin} registers the four biomes with points no climate
 * ever reaches (so they join the biome source's possible biomes, with their features and structures' checks), and
 * {@code mixin.MultiNoiseBiomeSourceMixin} wraps the source's resolvers with {@link #pick}: where the game picked one of a
 * land's host biomes and a slow noise of ours stands high, the land takes its place. Caves, seas, rivers and beaches are
 * never hosts, so caves and the climate map are as they were. The noises are fixed (the same layout in every world); the
 * climate they sit on is the world's own.
 */
public final class Lands {
	public static final ResourceKey<Biome> STEAM_VALLEY = key("steam_valley"), ASHLANDS = key("ashlands"), SALT_PANS = key("salt_desert"),
		STEPPE = key("steppe_plains");
	static final ResourceKey<Biome>[] ALL = keys(STEAM_VALLEY, ASHLANDS, SALT_PANS, STEPPE);

	// ---- tuning ----
	/** A slow noise's value (about -0.8 to 0.8) a land needs, which sets its share of its host biomes, and the noise's blocks per wave. */
	static final double SPRING_FROM = 0.22, SPRING_WAVE = 260.0;
	static final double VOLCANO_FROM = 0.51, VOLCANO_WAVE = 900.0;
	static final double SALT_FROM = 0.33, SALT_WAVE = 450.0;
	static final double STEPPE_FROM = 0.05, STEPPE_WAVE = 800.0;
	/** The continentalness a land needs (inland): past the coast and its beaches. */
	static final float VOLCANO_INLAND = 0.1F, STEPPE_INLAND = 0.15F;
	/** The salt pans lie on flat ground: the erosion at least this. */
	static final float SALT_FLAT = -0.1F;
	/** HarshLands reads the ashlands as this warm (the game's -1 to 1): two steps harsher than the temperate lands. */
	public static final float VOLCANIC_TEMPERATURE = 0.7F;
	/** The steppe's night under the open sky takes this much more body heat than a mild land's (BodyHeat.DRY_NIGHT less NIGHT: dry land loses more). */
	static final double STEPPE_NIGHT = -20.0;
	/** Snow monkeys in the steam valley: weight and troop size. */
	static final int MONKEY_WEIGHT = 6, MONKEY_MIN = 3, MONKEY_MAX = 5;
	/** The parameter points the biomes are parked at (depth, which no biome is looked up at). */
	static final float PARKED_DEPTH = 60.0F;

	/** What each land is dropped over: the game's own biomes that stand where it may lie. */
	private static final Set<ResourceKey<Biome>> SPRING_HOSTS = Set.of(Biomes.SNOWY_SLOPES, Biomes.GROVE, Biomes.SNOWY_TAIGA, Biomes.TAIGA,
		Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA);
	private static final Set<ResourceKey<Biome>> VOLCANO_HOSTS = Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.FOREST, Biomes.FLOWER_FOREST,
		Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
		Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.SNOWY_PLAINS,
		Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.STONY_PEAKS, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS);
	private static final Set<ResourceKey<Biome>> STEPPE_HOSTS = Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS);

	private static final SimplexNoise SPRING_NOISE = new SimplexNoise(new LegacyRandomSource(0x57EA4L)),
		VOLCANO_NOISE = new SimplexNoise(new LegacyRandomSource(0xA5411L)), SALT_NOISE = new SimplexNoise(new LegacyRandomSource(0x5A17L)),
		STEPPE_NOISE = new SimplexNoise(new LegacyRandomSource(0x57E99EL));

	private Lands() {
	}

	@SafeVarargs
	private static ResourceKey<Biome>[] keys(ResourceKey<Biome>... keys) {
		return keys;
	}

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, JaymosKingdoms.id(name));
	}

	public static void register() {
		LandBlocks.register();
		LandFeatures.register();
		// the snow monkeys of the cherry hills bathe in the steam valley's pools
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(STEAM_VALLEY), MobCategory.CREATURE, BlossomAnimals.SNOW_MONKEY, MONKEY_WEIGHT, MONKEY_MIN, MONKEY_MAX);
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				// the steppe's night: dry land loses more body heat than its biome temperature says (BodyHeat takes DRY_NIGHT only above 1.5)
				Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("addWeather", ToDoubleFunction.class)
					.invoke(null, (ToDoubleFunction<ServerPlayer>) Lands::nightFrost);
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for the steppe's cold nights: {}", e.toString());
			}
		}
	}

	/** Body heat taken from a player under the open sky of the steppe at night. */
	static double nightFrost(ServerPlayer player) {
		var level = player.level();
		BlockPos head = BlockPos.containing(player.getEyePosition());
		if (level.isBrightOutside() || !level.canSeeSky(head) || !level.getBiome(player.blockPosition()).is(STEPPE)) {
			return 0.0;
		}
		return STEPPE_NIGHT;
	}

	// ---- where the lands lie ----

	/** A slow noise at block (x, z), two octaves, about -0.8 to 0.8. */
	static double field(SimplexNoise noise, double x, double z, double wave) {
		double s = 1.0 / wave;
		return 0.75 * noise.get(x * s, z * s) + 0.25 * noise.get(x * s * 2.7, z * s * 2.7);
	}

	/** The block of a quart position (its middle). */
	static int block(int quart) {
		return (quart << 2) + 2;
	}

	/** Whether the ashlands lie at block (x, z) of Mohknee, given the climate's continentalness there (quantized). */
	public static boolean volcanic(int x, int z, long continentalness) {
		return Mohknee.on(x, z) && field(VOLCANO_NOISE, x, z, VOLCANO_WAVE) > VOLCANO_FROM
			&& Climate.unquantizeCoord(continentalness) > VOLCANO_INLAND;
	}

	/**
	 * The biome at quart position ({@code qx, qy, qz}) when the game picked {@code base}: one of our lands in its place where it lies.
	 * {@code ours} maps our biomes' keys to their holders in the biome source.
	 */
	public static Holder<Biome> pick(Holder<Biome> base, int qx, int qy, int qz, Climate.Sampler sampler, Map<ResourceKey<Biome>, Holder<Biome>> ours) {
		ResourceKey<Biome> host = base.unwrapKey().orElse(null);
		if (host == null) {
			return base;
		}
		int x = block(qx), z = block(qz);
		if (Mohknee.on(x, z)) {
			if (SPRING_HOSTS.contains(host) && field(SPRING_NOISE, x, z, SPRING_WAVE) > SPRING_FROM) {
				return ours.getOrDefault(STEAM_VALLEY, base);
			}
			if (VOLCANO_HOSTS.contains(host) && field(VOLCANO_NOISE, x, z, VOLCANO_WAVE) > VOLCANO_FROM
				&& Climate.unquantizeCoord(sampler.sample(qx, qy, qz).continentalness()) > VOLCANO_INLAND) {
				return ours.getOrDefault(ASHLANDS, base);
			}
			return base;
		}
		if (host == Biomes.DESERT && field(SALT_NOISE, x, z, SALT_WAVE) > SALT_FROM
			&& Climate.unquantizeCoord(sampler.sample(qx, qy, qz).erosion()) > SALT_FLAT) {
			return ours.getOrDefault(SALT_PANS, base);
		}
		if (STEPPE_HOSTS.contains(host) && field(STEPPE_NOISE, x, z, STEPPE_WAVE) > STEPPE_FROM
			&& Climate.unquantizeCoord(sampler.sample(qx, qy, qz).continentalness()) > STEPPE_INLAND) {
			return ours.getOrDefault(STEPPE, base);
		}
		return base;
	}

	/** Our four biomes' holders among the biome source's possible biomes. */
	public static Map<ResourceKey<Biome>, Holder<Biome>> holders(Set<Holder<Biome>> possible) {
		Map<ResourceKey<Biome>, Holder<Biome>> out = new HashMap<>();
		for (Holder<Biome> holder : possible) {
			holder.unwrapKey().ifPresent(k -> {
				for (ResourceKey<Biome> ours : ALL) {
					if (ours.equals(k)) {
						out.put(k, holder);
					}
				}
			});
		}
		return out;
	}

	/** The points the four biomes are parked at in the Overworld's biome list: nothing is ever looked up there. */
	public static Climate.ParameterPoint parked(int index) {
		return Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, PARKED_DEPTH + index, 0.0F, 0.0F);
	}

	public static ResourceKey<Biome>[] all() {
		return ALL.clone();
	}

	/**
	 * True on a thread while the game builds its built-in registries' Overworld biome list
	 * ({@code MultiNoiseBiomeSourceParameterLists.bootstrap}, which TaCZ runs at start). Those registries hold only the game's
	 * own biomes, so our lands must stay out of that list or building them fails ("No value registered for key
	 * jaymoskingdoms:salt_desert"). A world's own list is read with the data packs' biomes and still gets them.
	 */
	private static final ThreadLocal<Boolean> BUILT_IN = ThreadLocal.withInitial(() -> false);

	public static void buildingBuiltIn(boolean on) {
		BUILT_IN.set(on);
	}

	public static boolean buildingBuiltIn() {
		return BUILT_IN.get();
	}
}
