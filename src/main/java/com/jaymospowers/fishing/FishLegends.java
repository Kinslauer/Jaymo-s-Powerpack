package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Track C: legendary trophy fish (Legendary Fishing's idea, our own code). Every water ({@link #CELL} blocks square, its
 * fresh and its salt kept apart) has one old giant: a named fish of a kind that lives there, far over the usual size
 * ({@link Species#rollKg}, legend). It keeps deep and it's wary ({@link #WARY}), and comes up only to see who's fishing
 * its water ({@link #stir}); hooked, it fights long and hard ({@link FishingReal#LEGEND_PULL}). The water remembers it,
 * so it's the same fish, at the same weight, every time it rises. Landed and kept, its water is empty a long while
 * ({@link #RETURN_DAYS}) until another grows into its place; put back, it lives on (fisherfolk of the Deep Mother say
 * that's the only right thing to do with an elder of the water).
 */
public final class FishLegends {
	/** One legend in each square this many blocks across. */
	static final int CELL_SHIFT = 8;
	static final int CELL = 1 << CELL_SHIFT;
	/** How keen a legend is on bait, against an ordinary fish of its kind. */
	static final float WARY = 0.15F;
	/** It only rises where the water's at least this deep under the hook. */
	static final int DEEP_ENOUGH = 4;
	/** Chance, each check (FishBites ticks a line every 4 ticks), that it rises to a hook in its water; checks per cell are spaced. */
	static final float STIR_CHANCE = 0.04F;
	static final long STIR_EVERY = 200L;
	/** It comes up this far from the hook. */
	static final int RISE_LEAST = 10, RISE_MORE = 10;
	/** With nobody this near it goes back down. */
	public static final double STAY_RANGE = 96.0;
	/** Days after one is landed and kept before another legend grows in its water. */
	static final int RETURN_DAYS = 120;

	/** A water's legend: its species, name and weight, and the game time its water has one again (0: it's there now). */
	public record Legend(String species, String name, float kg, long goneUntil) {
		public static final Codec<Legend> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("species").forGetter(Legend::species),
			Codec.STRING.fieldOf("name").forGetter(Legend::name),
			Codec.FLOAT.fieldOf("kg").forGetter(Legend::kg),
			Codec.LONG.optionalFieldOf("gone_until", 0L).forGetter(Legend::goneUntil)).apply(i, Legend::new));

		@Nullable Species kind() {
			return Species.byId(species);
		}
	}

	/** Every water's legend, by its cell ("x,z,fresh|sea"). Kept on the overworld; saved. */
	public static final AttachmentType<Map<String, Legend>> LEGENDS = AttachmentRegistry.create(JaymosPowers.id("fish_legends"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Legend.CODEC)));

	/** Names fisherfolk give the old giants: a word for its look or temper, and a name. */
	private static final String[] FIRST = {"Old", "Grey", "Mother", "Black", "Long", "Great", "Iron", "Silent", "Sly", "Grandfather",
		"Lady", "Gnarled", "Mossy", "Scarred", "Pale", "Red"};
	private static final String[] LAST = {"Gnasher", "Tom", "Bess", "Whiskers", "Knuckles", "Mossback", "Ironjaw", "Ghost", "Brindle",
		"Scarside", "Hookbreaker", "Deepwater", "Moll", "Barnaby", "Nick", "Grendel", "Mags", "Ezra", "Gertie", "Sorrow"};

	/** Legends up now, by cell, and when each cell was last stirred. Not saved: they go down when everyone leaves. */
	private static final Map<String, UUID> UP = new HashMap<>();
	private static final Map<String, Long> STIRRED = new HashMap<>();

	private FishLegends() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			UP.clear();
			STIRRED.clear();
		});
	}

	static String cell(BlockPos pos, boolean sea) {
		return (pos.getX() >> CELL_SHIFT) + "," + (pos.getZ() >> CELL_SHIFT) + "," + (sea ? "sea" : "fresh");
	}

	/** The legend of the water at {@code pos}, made up the first time anyone asks, or null if no legendary kind lives there. */
	static @Nullable Legend legendAt(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		boolean sea = Species.sea(biome);
		String key = cell(pos, sea);
		ServerLevel home = level.getServer().overworld();
		Map<String, Legend> all = home.getAttachedOrElse(LEGENDS, Map.of());
		Legend known = all.get(key);
		long now = level.getGameTime();
		if (known != null && (known.goneUntil == 0L || known.goneUntil > now)) {
			return known;
		}
		// a new one: which kinds of legend live in this water, picked by the place so it's the same for everyone
		List<Species> kinds = new ArrayList<>();
		for (Species sp : Species.values()) {
			if (sp.legendary() && sp.ours() && sp.livesIn(biome)) {
				kinds.add(sp);
			}
		}
		if (kinds.isEmpty()) {
			return null;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ key.hashCode() * 31L ^ (known == null ? 0L : known.goneUntil));
		Species sp = kinds.get(random.nextInt(kinds.size()));
		String name = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
		Legend made = new Legend(sp.id, name, sp.rollKg(random, true), 0L);
		put(home, key, made);
		return made;
	}

	private static void put(ServerLevel home, String key, Legend legend) {
		Map<String, Legend> all = new HashMap<>(home.getAttachedOrElse(LEGENDS, Map.of()));
		all.put(key, legend);
		home.setAttached(LEGENDS, all);
	}

	/**
	 * {@code player} is fishing at {@code pos} (called each check of a baited line): now and then, in deep enough water,
	 * the water's legend comes up to look, somewhere out from the hook, with a swirl at the top.
	 */
	static void stir(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (level.dimension() != Level.OVERWORLD || !level.getFluidState(pos).is(FluidTags.WATER)) {
			return;
		}
		boolean sea = Species.sea(level.getBiome(pos));
		String key = cell(pos, sea);
		long now = level.getGameTime();
		Long last = STIRRED.get(key);
		if (last != null && now - last < STIR_EVERY) {
			return;
		}
		STIRRED.put(key, now);
		UUID up = UP.get(key);
		if (up != null) {
			Entity e = level.getEntity(up);
			if (e != null && e.isAlive()) {
				return;
			}
			UP.remove(key);
		}
		if (player.getRandom().nextFloat() >= STIR_CHANCE || depthAt(level, pos) < DEEP_ENOUGH) {
			return;
		}
		Legend legend = legendAt(level, pos);
		if (legend == null || legend.goneUntil > now) {
			return;
		}
		Species sp = legend.kind();
		EntityType<?> type = sp == null ? null : FishSpecies.type(sp);
		if (type == null) {
			return;
		}
		RandomSource random = player.getRandom();
		for (int tries = 0; tries < 8; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = RISE_LEAST + random.nextInt(RISE_MORE);
			BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
				pos.offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist))).below();
			int depth = depthAt(level, at);
			if (depth < DEEP_ENOUGH) {
				continue;
			}
			BlockPos deep = at.below(depth - 2);
			Entity e = type.create(level, EntitySpawnReason.EVENT);
			if (!(e instanceof WildFish fish)) {
				return;
			}
			fish.snapTo(deep.getX() + 0.5, deep.getY() + 0.5, deep.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			fish.makeLegend(legend.name, legend.kg);
			level.addFreshEntity(fish);
			UP.put(key, fish.getUUID());
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 30, 0.8, 0.1, 0.8, 0.05);
			level.playSound(null, at, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.NEUTRAL, 0.8F, 0.6F);
			return;
		}
	}

	/** How many blocks of water there are under the top at {@code pos} (counting down from the top of its water). */
	static int depthAt(ServerLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos at = pos.mutable();
		while (at.getY() < level.getMaxY() && level.getFluidState(at.above()).is(FluidTags.WATER)) {
			at.move(0, 1, 0);
		}
		int depth = 0;
		while (depth < 64 && level.getFluidState(at).is(FluidTags.WATER)) {
			depth++;
			at.move(0, -1, 0);
		}
		return depth;
	}

	/** {@code player} landed {@code fish}, its water's legend: the water is without one for a long time, unless it goes back. */
	static void landed(ServerLevel level, WildFish fish, ServerPlayer player) {
		boolean sea = Species.sea(level.getBiome(fish.blockPosition()));
		ServerLevel home = level.getServer().overworld();
		for (Map.Entry<String, Legend> e : home.getAttachedOrElse(LEGENDS, Map.of()).entrySet()) {
			if (e.getValue().name.equals(fish.legendName()) && e.getValue().goneUntil == 0L) {
				put(home, e.getKey(), new Legend(e.getValue().species, e.getValue().name, e.getValue().kg,
					level.getGameTime() + RETURN_DAYS * 24000L));
				UP.remove(e.getKey());
				return;
			}
		}
		UP.remove(cell(fish.blockPosition(), sea));
	}

	/** A landed legend went back into the water ({@code fish}): it's its water's legend again. */
	static void released(ServerLevel level, WildFish fish) {
		ServerLevel home = level.getServer().overworld();
		for (Map.Entry<String, Legend> e : home.getAttachedOrElse(LEGENDS, Map.of()).entrySet()) {
			if (e.getValue().name.equals(fish.legendName())) {
				put(home, e.getKey(), new Legend(e.getValue().species, e.getValue().name, Math.max(fish.weight(), e.getValue().kg), 0L));
				UP.put(e.getKey(), fish.getUUID());
				return;
			}
		}
	}

	/** What the fisherfolk say keeps the water at {@code pos}: its legend, or null if it's gone or none lives there. */
	public static @Nullable Legend rumour(ServerLevel level, BlockPos pos) {
		Legend legend = legendAt(level, pos);
		return legend == null || legend.goneUntil > level.getGameTime() ? null : legend;
	}
}
