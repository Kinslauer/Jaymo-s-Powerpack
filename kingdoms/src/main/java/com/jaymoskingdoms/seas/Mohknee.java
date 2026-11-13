package com.jaymoskingdoms.seas;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.phys.Vec3;

/**
 * Mohknee, the blossom folk's continent (docs/BlossomLands-Plan.md, patch 1; Andy: "i would like all this to be on a
 * seperate continent spoken about by villagers, and reached by LONG sailing", "it is called Mohknee and you can
 * encounter tideborn islands on the way"). It is a place in the Overworld, not a world of its own: one round land far
 * to the east of the world's origin, with {@link #SEA} blocks of open deep sea on every side and a few islands out on
 * the crossing for the Tideborn's island villages.
 * <p>
 * The land itself is made by data: {@code data/minecraft/worldgen/density_function/overworld/continents.json} (and
 * {@code temperature}, {@code vegetation}, {@code erosion}) blend the world's own shape with
 * {@code data/jaymoskingdoms/worldgen/density_function/mohknee/*}, all written by {@code tools/gen_mohknee.py}. The
 * numbers below are that tool's: change both together. This class only answers where the land is, for everything
 * that must know: Blossom settlements rise only here ({@code BlossomVillages}), every settlement here is of the
 * Blossom land whatever biome its bell stands in ({@code Palette}), and the home continent's villages and other
 * mods' surface buildings are not started here ({@code MohkneeStructureMixin}).
 * <p>
 * Ships top out at 0.8 blocks a tick ({@code Ship.FASTEST}): the crossing is two days and nights for an ordinary ship.
 * Only new chunks take the land's shape, so a world already explored past {@link #CENTRE_X} - {@link #edge()} blocks
 * east keeps what it has there.
 */
public final class Mohknee {
	/** The land's middle (blocks): due east of the world's origin. */
	public static final int CENTRE_X = 28000, CENTRE_Z = 0;
	/** The land's radius, and how far the shore strays from a circle. */
	public static final int LAND = 3000, WOBBLE = 450;
	/** Open sea on every side past the land, and the band beyond it where the home world's shape comes back. */
	public static final int SEA = 16000, FADE = 1500;

	/**
	 * The islands out on the crossing (x, z, size: an island's shore lies about three quarters of its size from its
	 * middle), three on the way from the west and the rest for other headings. Each carries one village
	 * ({@code jaymoskingdoms:crossing_isle_village}), which {@code Havens} makes the Tideborn's as it does any island's.
	 */
	public static final int[][] ISLANDS = {{12500, -900, 240}, {17000, 1100, 220}, {21500, -500, 250}, {28000, -9000, 230}, {28000, 9500, 230},
		{20500, -8000, 220}, {20000, 8500, 240}, {38500, 600, 230}};
	/** Set while a crossing island's own village ({@code IslandStructure} with only_at) starts the village it stands as: the only one let through there. */
	public static final ThreadLocal<Boolean> OWN_VILLAGE = new ThreadLocal<>();

	/** The map's most pixels a side (it is painted in one tick). */
	static final int MAP_MOST = 1000;
	/** A map reaching no further than this also reads the ground's height. */
	static final int MAP_CLOSE = 1200;

	private Mohknee() {
	}

	/** How far from the land's middle its sea ends and the home world begins. */
	public static int edge() {
		return LAND + SEA + FADE;
	}

	/** The distance (blocks, along the ground) from {@code x, z} to the land's middle. */
	public static double distance(double x, double z) {
		return Math.sqrt((x - CENTRE_X) * (x - CENTRE_X) + (z - CENTRE_Z) * (z - CENTRE_Z));
	}

	/** {@code x, z} is on Mohknee or just off its shore (the Overworld is taken as read: ask {@link #on(Level, BlockPos)} with a level). */
	public static boolean on(int x, int z) {
		return distance(x, z) < LAND + WOBBLE;
	}

	/** {@code pos} in {@code level} is on Mohknee: the Overworld only. */
	public static boolean on(Level level, BlockPos pos) {
		return level.dimension() == Level.OVERWORLD && on(pos.getX(), pos.getZ());
	}

	/** {@code x, z} is on (or just off) one of the crossing's islands. */
	public static boolean island(int x, int z) {
		for (int[] i : ISLANDS) {
			double dx = x - i[0], dz = z - i[1];
			if (dx * dx + dz * dz < (double)(i[2] + 80) * (i[2] + 80)) {
				return true;
			}
		}
		return false;
	}

	/** {@code generator} is the Overworld's own (the game's overworld settings, large biomes and amplified included). */
	public static boolean overworld(ChunkGenerator generator) {
		return generator instanceof NoiseBasedChunkGenerator noise && (noise.stable(NoiseGeneratorSettings.OVERWORLD)
			|| noise.stable(NoiseGeneratorSettings.LARGE_BIOMES) || noise.stable(NoiseGeneratorSettings.AMPLIFIED));
	}

	/** {@code x, z} is out on the crossing: the open sea between the home world and the land. */
	public static boolean crossing(int x, int z) {
		double d = distance(x, z);
		return d >= LAND + WOBBLE && d < LAND + SEA;
	}

	// ---- operators ------------------------------------------------------------------------------------------------------

	/** {@code /kingdom mohknee}: where the land lies from here; {@code map [blocks per pixel] [reach] [x z]}: a picture of what the world makes of it. */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("mohknee").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(Mohknee::where)
			.then(Commands.literal("map").executes(c -> map(c, 48, edge() + 1500, CENTRE_X, CENTRE_Z))
				.then(Commands.argument("scale", IntegerArgumentType.integer(4, 256))
					.executes(c -> map(c, IntegerArgumentType.getInteger(c, "scale"), edge() + 1500, CENTRE_X, CENTRE_Z))
					.then(Commands.argument("reach", IntegerArgumentType.integer(100, 40000))
						.executes(c -> map(c, IntegerArgumentType.getInteger(c, "scale"), IntegerArgumentType.getInteger(c, "reach"), CENTRE_X, CENTRE_Z))
						.then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
							.executes(c -> map(c, IntegerArgumentType.getInteger(c, "scale"), IntegerArgumentType.getInteger(c, "reach"),
								IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))));
	}

	private static int where(CommandContext<CommandSourceStack> c) {
		Vec3 at = c.getSource().getPosition();
		double dx = CENTRE_X - at.x, dz = CENTRE_Z - at.z;
		double shore = Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - LAND);
		// 0 is north (-z), clockwise
		int bearing = (int)Math.round((Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0);
		String text = on((int)at.x, (int)at.z) ? "You are on Mohknee (its middle is at " + CENTRE_X + ", " + CENTRE_Z + ")."
			: "Mohknee's shore is about " + Math.round(shore) + " blocks away, bearing " + bearing + " degrees (its middle is at " + CENTRE_X + ", "
				+ CENTRE_Z + "; its sea begins " + (CENTRE_X - edge()) + " east).";
		c.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	/**
	 * Paints what the world's own generator makes of the land and its sea, without loading a chunk: sea by depth, land by
	 * biome, {@code reach} blocks each way from its middle, to {@code mohknee.png} in the game's folder, and counts the
	 * land's biomes. It runs in one tick, so the picture is kept to {@link #MAP_MOST} pixels a side.
	 */
	private static int map(CommandContext<CommandSourceStack> c, int scale, int reach, int midX, int midZ) {
		ServerLevel level = c.getSource().getServer().overworld();
		RandomState state = level.getChunkSource().randomState();
		Climate.Sampler sampler = state.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		BiomeResolver biomes = level.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(state);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		// a close look also reads the ground's height (slow): land the sea has drowned is shown as shallows
		boolean close = reach <= MAP_CLOSE;
		int size = reach * 2 / scale;
		if (size > MAP_MOST) {
			c.getSource().sendFailure(Component.literal("Too fine: at most " + MAP_MOST + " pixels a side (reach * 2 / scale)."));
			return 0;
		}
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Map<String, Integer> counts = new TreeMap<>();
		int landCells = 0, seaInRing = 0, ringCells = 0;
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				int x = midX - reach + px * scale, z = midZ - reach + py * scale;
				float continent = Offshore.continentalness(sampler, x, z);
				int colour;
				if (continent < Offshore.SEA) {
					colour = continent < Offshore.DEEP ? 0x10244A : 0x2A5C9A;
				} else if (close && generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state) < generator.getSeaLevel()) {
					// land by the continent noise, but its ground is under the sea (the higher sea drowns low shores)
					colour = 0x3A8FA0;
				} else {
					Holder<Biome> biome = biomes.getNoiseBiome(x >> 2, 256 >> 2, z >> 2);
					String name = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
					colour = colour(name);
					if (on(x, z)) {
						counts.merge(name, 1, Integer::sum);
						landCells++;
					}
				}
				if (crossing(x, z)) {
					ringCells++;
					if (continent < Offshore.SEA) {
						seaInRing++;
					}
				}
				image.setRGB(px, py, colour);
			}
		}
		File file = new File("mohknee.png");
		try {
			ImageIO.write(image, "png", file);
		} catch (IOException e) {
			c.getSource().sendFailure(Component.literal("Could not write " + file.getAbsolutePath() + ": " + e.getMessage()));
			return 0;
		}
		StringBuilder text = new StringBuilder("Mohknee map (" + scale + " blocks a pixel) written to " + file.getAbsolutePath() + ". Crossing: "
			+ (ringCells == 0 ? 0 : seaInRing * 100 / ringCells) + "% sea. Land biomes:");
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			text.append(' ').append(e.getKey()).append(' ').append(e.getValue() * 100 / Math.max(1, landCells)).append('%');
		}
		String out = text.toString();
		c.getSource().sendSuccess(() -> Component.literal(out), false);
		return 1;
	}

	private static int colour(String biome) {
		if (biome.contains("cherry")) {
			return 0xF2A6C8;
		} else if (biome.contains("river")) {
			return 0x4F86C6;
		} else if (biome.contains("beach") || biome.contains("shore")) {
			return 0xE6D9A0;
		} else if (biome.contains("peak") || biome.contains("slope") || biome.contains("snow") || biome.contains("frozen") || biome.contains("ice")
			|| biome.equals("grove")) {
			return 0xF4F4F4;
		} else if (biome.contains("meadow")) {
			return 0x9BD67A;
		} else if (biome.contains("taiga")) {
			return 0x2F6B4F;
		} else if (biome.contains("forest")) {
			return 0x3E8E3A;
		} else if (biome.contains("plains")) {
			return 0x7DBB55;
		} else if (biome.contains("desert") || biome.contains("badlands") || biome.contains("savanna")) {
			return 0xD98C3A;
		} else if (biome.contains("jungle") || biome.contains("swamp")) {
			return 0x1F5F2A;
		}
		return 0x808080;
	}
}
