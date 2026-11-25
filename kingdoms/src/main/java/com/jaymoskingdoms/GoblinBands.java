package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Village mods patch 8 (Andy's pick rb_band; the idea from Ribbits, whose code, art and sounds are not used): of an
 * evening a few of a goblin village's goblins sit round its fire with a drum, a bone flute, a gourd shaker and a bass jug,
 * and play. The tune is made up fresh each evening (a five-note scale, a drum and jug groove under it, the shaker on the
 * off-beats), from the village and the day, so two evenings and two villages don't sound alike. The four sounds are our
 * own ({@code tools/gen_goblin_band.py}), each a single note the game pitches for the tune.
 * <p>
 * A player the village spares ({@link GreenMarket#welcome}) who walks up and shakes a gourd shaker ({@link SwampBlocks}),
 * or holds a lute or a goat horn near the fire, plays along: the goblins cheer them and their shakes land on the beat.
 * Every goblin village has its band, the swamp's stilt villages ({@link SwampGoblins}) and the ones on land.
 */
public final class GoblinBands {
	// ---- tuning ----
	/** When the band plays: from late afternoon to the bedtime of {@link Homes} (time of day, ticks). */
	static final long START = 11000L, END = Homes.BEDTIME;
	/** A band strikes up only with a player this near its fire (blocks), and plays to anyone within twice it. */
	static final double HEARD = 48.0;
	/** The most goblins in a band, and the least it plays with. */
	static final int MOST = 4, LEAST = 2;
	/** Its players sit this near the fire (blocks). */
	static final int SEAT_RADIUS = 3;
	/** One step of the tune (ticks); a bar is sixteen steps. */
	static final int STEP = 4, STEPS = 16;
	/** How loud the band is (one is heard to 16 blocks). */
	static final float VOLUME = 1.6F;
	/** A player plays along this near the fire (blocks). */
	static final double JOIN_REACH = 8.0;

	/** The five-note scale the flute plays (semitones off A), and the jug's root and fifth. */
	private static final int[] SCALE = {-5, -3, 0, 2, 4, 7, 9, 12};
	private static final int[] JUG = {0, 0, 7, 5};

	/** A band playing at {@code fire}: its players, the evening's tune, who has joined in. */
	private static final class Band {
		final BlockPos fire;
		final List<UUID> players = new ArrayList<>();
		final long seed;
		final int[] melody = new int[STEPS];
		final boolean[] drum = new boolean[STEPS];
		final Map<UUID, Long> joined = new HashMap<>();
		int step;

		Band(BlockPos fire, long seed) {
			this.fire = fire;
			this.seed = seed;
			java.util.Random r = new java.util.Random(seed);
			int note = 2 + r.nextInt(3);
			for (int i = 0; i < STEPS; i++) {
				// the flute walks the scale a step or two at a time, resting now and then
				note = Math.max(0, Math.min(SCALE.length - 1, note + r.nextInt(5) - 2));
				this.melody[i] = r.nextInt(4) == 0 ? Integer.MIN_VALUE : SCALE[note];
				this.drum[i] = i % 8 == 0 || i % 8 == 6 || r.nextInt(6) == 0 && i % 2 == 0;
			}
		}
	}

	/** Village id → its band, while one plays. */
	private static final Map<Integer, Band> BANDS = new HashMap<>();

	private GoblinBands() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GoblinBands::play);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BANDS.clear());
	}

	static boolean evening(ServerLevel level) {
		long t = Royals.timeOfDay(level);
		return t >= START && t < END;
	}

	/** Every {@link Camps#CHECK_TICKS} while goblin village {@code village} is loaded: its band strikes up of an evening, and breaks up after. */
	static void tick(ServerLevel level, Faction village, Camps.CampData data) {
		MinecraftServer server = level.getServer();
		Band band = BANDS.get(village.id());
		BlockPos fire = data.center();
		if (!evening(level) || village.fallen() || !Raids.playerNear(level, fire, HEARD)) {
			if (band != null) {
				BANDS.remove(village.id());
				release(level, village, band);
			}
			return;
		}
		if (band == null) {
			band = new Band(fire, village.id() * 0x9E3779B97F4A7C15L ^ Production.day(server));
			BANDS.put(village.id(), band);
		}
		// its players: goblins of the village at the fire (not its merchants, its witch or its slingers on watch)
		band.players.removeIf(id -> !(level.getEntity(id) instanceof PathfinderMob m) || !m.isAlive() || m.getTarget() != null);
		if (band.players.size() < MOST) {
			AABB box = new AABB(fire).inflate(GoblinBuildings.HOME_RADIUS + 8.0);
			for (Worker w : Workers.near(level, box, x -> x.factionId() == village.id() && x.role() == Goblins.GOBLIN && x.mob().isAlive()
				&& x.mob().getTarget() == null && !GreenMarket.merchant(x.mob()) && !SwampGoblins.witch(x.mob()) && !Downed.isDowned(x.mob()))) {
				if (band.players.size() >= MOST) {
					break;
				}
				if (!band.players.contains(w.mob().getUUID())) {
					band.players.add(w.mob().getUUID());
				}
			}
		}
		for (UUID id : band.players) {
			if (level.getEntity(id) instanceof PathfinderMob m) {
				m.setHomeTo(fire, SEAT_RADIUS);
			}
		}
	}

	/** Its players go back about the village. */
	private static void release(ServerLevel level, Faction village, Band band) {
		int radius = Hordes.homeRadius(level.getServer(), village.id(), GoblinBuildings.HOME_RADIUS);
		for (UUID id : band.players) {
			if (level.getEntity(id) instanceof PathfinderMob m) {
				m.setHomeTo(band.fire, radius);
			}
		}
	}

	/** {@code id} is playing in a band just now (so the evening's homes leave it at the fire). */
	static boolean playing(UUID id) {
		for (Band b : BANDS.values()) {
			if (b.players.contains(id)) {
				return true;
			}
		}
		return false;
	}

	/** Each step of each band's tune. */
	private static void play(MinecraftServer server) {
		if (BANDS.isEmpty() || server.getTickCount() % STEP != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		for (Band band : BANDS.values()) {
			List<Entity> at = new ArrayList<>();
			for (UUID id : band.players) {
				Entity e = level.getEntity(id);
				if (e != null && e.distanceToSqr(Vec3.atCenterOf(band.fire)) < (SEAT_RADIUS + 3) * (SEAT_RADIUS + 3)) {
					at.add(e);
				}
			}
			int step = band.step++ % STEPS;
			if (at.size() < LEAST) {
				continue;
			}
			long now = level.getGameTime();
			band.joined.values().removeIf(t -> now - t > 20 * 30);
			boolean cheered = !band.joined.isEmpty();
			// drum (the first player), shaker on the off-beats (the second), flute and jug when there are more
			if (band.drum[step]) {
				note(level, at.get(0), SwampBlocks.DRUM, step % 8 == 0 ? 1.0F : 1.18F);
			}
			if (step % 2 == 1 || cheered && step % 4 == 2) {
				note(level, at.get(1), SwampBlocks.SHAKE, 0.95F + (step % 4 == 3 ? 0.1F : 0.0F));
			}
			if (at.size() >= 3 && band.melody[step] != Integer.MIN_VALUE) {
				note(level, at.get(2), SwampBlocks.FLUTE, pitch(band.melody[step]));
				level.sendParticles(ParticleTypes.NOTE, at.get(2).getX(), at.get(2).getEyeY() + 0.6, at.get(2).getZ(), 0,
					(band.melody[step] + 12) / 24.0, 0.0, 0.0, 1.0);
			}
			if (at.size() >= 4 && step % 4 == 0) {
				note(level, at.get(3), SwampBlocks.JUG, pitch(JUG[(step / 4) % JUG.length] - 12));
			}
			// whoever holds a lute or a goat horn at the fire, and the village spares, is one of the band tonight
			if (step == 0) {
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(band.fire).inflate(JOIN_REACH),
					p -> p.isHolding(com.jaymoskingdoms.nightfall.Nightfall.LUTE) || p.isHolding(Items.GOAT_HORN))) {
					join(level, band, p);
				}
			}
		}
	}

	private static void note(ServerLevel level, Entity who, Holder<SoundEvent> sound, float pitch) {
		level.playSound(null, who.getX(), who.getEyeY(), who.getZ(), sound, SoundSource.RECORDS, VOLUME, pitch);
	}

	/** The game's pitch for {@code semitones} off the sound's own note (it plays two octaves, half to double). */
	static float pitch(int semitones) {
		return (float)Math.max(0.5, Math.min(2.0, Math.pow(2.0, semitones / 12.0)));
	}

	/** {@code player} shook a gourd shaker: by a band's fire, they play along. */
	static void joined(ServerLevel level, ServerPlayer player) {
		Band band = near(player);
		if (band != null) {
			join(level, band, player);
		}
	}

	private static @Nullable Band near(ServerPlayer player) {
		for (Map.Entry<Integer, Band> e : BANDS.entrySet()) {
			if (player.distanceToSqr(Vec3.atCenterOf(e.getValue().fire)) < JOIN_REACH * JOIN_REACH) {
				return e.getValue();
			}
		}
		return null;
	}

	private static void join(ServerLevel level, Band band, ServerPlayer player) {
		int village = BANDS.entrySet().stream().filter(e -> e.getValue() == band).map(Map.Entry::getKey).findFirst().orElse(-1);
		Faction f = Factions.get(level.getServer(), village);
		if (f == null || !GreenMarket.welcome(player, f)) {
			return;
		}
		boolean fresh = band.joined.put(player.getUUID(), level.getGameTime()) == null;
		if (fresh) {
			// the goblins cheer the newcomer
			for (UUID id : band.players) {
				if (level.getEntity(id) instanceof Villager v) {
					level.sendParticles(ParticleTypes.HEART, v.getX(), v.getY() + v.getBbHeight() + 0.3, v.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
					v.playAmbientSound();
				}
			}
		}
	}

	static void clear() {
		BANDS.clear();
	}
}
