package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * The harsh lands. Andy (2026-11-19): "spawns should follow the global map, the story starts should take that into effect
 * too. starting you in the more temperate area and increasing difficulty as you go hotter or [colder]." The home world's
 * climate is the continental heat map ({@code tools/gen_continents.py}: latitude, harsher inland, a wobble of noise), and
 * its temperature is what the land's biomes were chosen by. The temperate middle is the land as it is; each step out
 * toward the ice or the burning sands is one {@link #steps} harsher, up to {@link #MOST} at the far pole and the deep
 * equator.
 * <ul>
 * <li>Every hostile of the harsh lands takes the world tier ({@link WorldTiers}) of the player near it plus the land's
 * steps, up to Legend: more health, armour and damage, the tier's gear ({@link MonsterGear}), its spoils and experience,
 * and the tier's odds of being a marked monster ({@link MarkedMonsters}). A Hearth player walking north meets
 * Borderlands foes in the cold, Wildlands ones in the snows and Dread ones at the pole. The steps are the land a monster
 * was first met in, kept on it, so one that follows you home stays as hard as it was.</li>
 * <li>More of them: the monster cap a chunk is spawned under ({@code mixin.tweaks.MonsterCapMixin}) rises with its
 * steps, from the temperate lands' {@link #MONSTER_SHARE}[0] (Andy's cut, unchanged) toward vanilla's.</li>
 * <li>Starts: every life story's start is set out from, and put down in, the temperate lands ({@code LifeStory}).</li>
 * </ul>
 * Only the Overworld (or the Overworld's band of a One Dimension world) has a climate here; Mohknee's is mild by its own
 * density function. What spawns where already follows the map, since the biomes do (their spawn lists,
 * {@code wild.PredatorRanges}).
 */
public final class HarshLands {
	/**
	 * The temperature edges of the steps (the game's -1 to 1, the biome builder's own bands): inside {@link #COLD_EDGES}[0] to
	 * {@link #HOT_EDGES}[0] is temperate (0 steps); past each further edge one step more (cold or warm, frozen or hot, the far
	 * pole or the deep equator).
	 */
	static final float[] COLD_EDGES = {-0.15F, -0.45F, -0.8F};
	static final float[] HOT_EDGES = {0.2F, 0.55F, 0.85F};
	/** The harshest land's steps. */
	public static final int MOST = 3;
	/** Per step, the share of vanilla's monster cap a chunk of that land spawns under (temperate: Andy's 0.5 of 2026-11-05). */
	public static final float[] MONSTER_SHARE = {0.5F, 0.6F, 0.75F, 0.9F};

	/** The steps of the land a hostile was first met in (saved with it). */
	static final AttachmentType<Integer> LAND_STEPS = AttachmentRegistry.create(JaymosKingdoms.id("harsh_land_steps"),
		builder -> builder.persistent(Codec.INT));
	/** Each chunk's steps, per world (they never change); dropped when it grows past {@link #CACHE_MOST}. */
	private static final Map<ServerLevel, Long2ByteOpenHashMap> KNOWN = new WeakHashMap<>();
	private static final int CACHE_MOST = 65536;
	/** The steps of the chunk whose monster cap the spawner is asking for, on the server thread only (-1: not asking). */
	private static final ThreadLocal<Integer> ASKING = ThreadLocal.withInitial(() -> -1);

	private HarshLands() {
	}

	/** Loads the class at start-up, so {@link #LAND_STEPS} is known before any saved monster is read. */
	static void register() {
	}

	/** How many steps harsher than the temperate lands the land at {@code pos} is (0 to {@link #MOST}; 0 off the Overworld). */
	public static int at(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || !Stacked.at(level, pos, Level.OVERWORLD)) {
			return 0;
		}
		return chunk(level, ChunkPos.pack(pos));
	}

	/** The steps of chunk {@code key} ({@link ChunkPos#pack}) of the Overworld {@code level}. */
	private static int chunk(ServerLevel level, long key) {
		Long2ByteOpenHashMap known = KNOWN.computeIfAbsent(level, l -> new Long2ByteOpenHashMap());
		if (known.containsKey(key)) {
			return known.get(key);
		}
		if (known.size() >= CACHE_MOST) {
			known.clear();
		}
		ChunkPos chunk = ChunkPos.unpack(key);
		byte steps = (byte) steps(temperature(level, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()));
		known.put(key, steps);
		return steps;
	}

	/** The climate's temperature at (x, z): the number the land's biomes were picked by (biome noise only, nothing made). */
	static float temperature(ServerLevel level, int x, int z) {
		Climate.Sampler sampler = level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		return Climate.unquantizeCoord(sampler.sample(x >> 2, 0, z >> 2).temperature());
	}

	/** The steps for a temperature. */
	static int steps(float temperature) {
		int steps = 0;
		for (int i = 0; i < COLD_EDGES.length; i++) {
			if (temperature < COLD_EDGES[i] || temperature > HOT_EDGES[i]) {
				steps = i + 1;
			}
		}
		return steps;
	}

	/** Whether {@code pos} is in the temperate lands (always, off the Overworld). */
	public static boolean temperate(ServerLevel level, BlockPos pos) {
		return at(level, pos) == 0;
	}

	/** The steps of the land {@code mob} was first met in (worked out and kept the first time it's asked). */
	public static int of(Mob mob) {
		Integer kept = mob.getAttached(LAND_STEPS);
		if (kept != null) {
			return kept;
		}
		if (!(mob.level() instanceof ServerLevel level)) {
			return 0;
		}
		int steps = at(level, mob.blockPosition());
		mob.setAttached(LAND_STEPS, steps);
		return steps;
	}

	/** {@code tier} (a player's world tier) as a hostile of {@code mob}'s land takes it: its steps higher, up to Legend. */
	public static int tierFor(Mob mob, int tier) {
		return Math.min(WorldTiers.Tier.LEGEND.ordinal(), tier + of(mob));
	}

	// ---- the monster cap, by the land ----------------------------------------------------------------------------------

	/** Marks the monster cap asked for next as that of a chunk {@code steps} harsh (-1: done asking). */
	public static void asking(int steps) {
		ASKING.set(steps);
	}

	/** The steps of the chunk whose monster cap is being asked for now; -1 when none is. */
	public static int asking() {
		return ASKING.get();
	}

	/** The steps of chunk {@code pos} of {@code level}, for its monster cap. */
	public static int capSteps(ServerLevel level, ChunkPos pos) {
		return at(level, pos.getMiddleBlockPosition(level.getSeaLevel()));
	}
}
