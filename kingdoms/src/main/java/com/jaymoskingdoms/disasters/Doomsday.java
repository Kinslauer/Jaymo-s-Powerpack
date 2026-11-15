package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Moons;
import com.jaymoskingdoms.WorldTiers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The world's ending (medieval picks, nd-doom; Andy: "I want to test this. this can be extremely bad but also very
 * cool."). Everything at once, for {@link #DAYS} days: the sky turns red, day and night ({@link RedSky}); stars fall and
 * punch down to the bedrock ({@link Bolides}); the sea floods in over the low land by every coast ({@link Calamities#flood});
 * great quakes run through the ground ({@link Earthquakes}) and open the mountains into volcanoes ({@link Volcanoes}, any
 * tall peak now, not only the hot lands'); and the tsunamis come in threes ({@link Tsunamis}). Then it passes: the sky
 * clears, the floods drain, the volcanoes cool, and the land is left scarred, craters, shafts, basalt and all.
 * <p>
 * It comes two ways. Rung: the Last Bell rung to its end ({@link LastBell}). Unrung, very rarely and late: on a Super
 * Blood Moon, once the world has come a long way (its floor at {@link #FLOOR_FROM} or someone at Legend,
 * {@link WorldTiers}) and the world is old ({@link #MIN_DAY}), a small chance that night is the omen of it: the night
 * goes with its signs and its tolling, and the ending comes at dawn.
 * <p>
 * Kept bounded: every block it changes goes through the shared {@link BlockBudget} (and the floods' own few a tick), its
 * events come round each player on their own clocks, and it never loads land. The state is saved with the world. An
 * operator can start and stop it to test it ({@code /kingdom disaster doom start|stop}).
 */
public final class Doomsday {
	/** How long it lasts (days); how long the red takes to come and go (ticks). */
	static final int DAYS = 3, FADE = 600;
	/** Unrung: the world's floor tier that lets it come (Wildlands), its least age (days), and the chance on a Super Blood
	 * Moon (doubled from Dread). */
	static final int FLOOR_FROM = 2, MIN_DAY = 60;
	static final float CHANCE = 0.03F;
	/** Round each player: a stone this often (ticks, the odds), a great quake, a mountain opening, the sea coming in,
	 * three waves (ticks). */
	static final int STONE_EVERY = 300, QUAKE_EVERY = 1100, VOLCANO_EVERY = 3000, FLOOD_EVERY = 1500, TSUNAMI_EVERY = 7200;
	static final float STONE_CHANCE = 0.6F;
	/** Stones fall this far round someone (blocks). */
	static final double STONE_MIN = 24.0, STONE_MAX = 110.0;
	/** The low toll heard all over the world this often while it lasts, and through the night of its omen (ticks). */
	static final int TOLL_EVERY = 1200;

	/** Whether it's on (or coming), when it starts and ends (game time), what brought it, and the last night rolled for it. */
	record State(boolean on, long start, long end, String cause, long rolled) {
		static final State NONE = new State(false, 0L, 0L, "", -1L);
		static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("on").forGetter(State::on),
			Codec.LONG.fieldOf("start").forGetter(State::start),
			Codec.LONG.fieldOf("end").forGetter(State::end),
			Codec.STRING.fieldOf("cause").forGetter(State::cause),
			Codec.LONG.fieldOf("rolled").forGetter(State::rolled)).apply(i, State::new));
	}

	static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("doomsday"), b -> b.persistent(State.CODEC));

	private Doomsday() {
	}

	static State state(ServerLevel level) {
		return level.getAttachedOrElse(STATE, State.NONE);
	}

	/** Whether the ending is going on now (not only foretold). */
	public static boolean active(ServerLevel level) {
		State s = state(level);
		long now = level.getGameTime();
		return s.on() && now >= s.start() && now < s.end();
	}

	/** How red the sky is, 0..1: in over its first moments, out over its last. */
	static float red(ServerLevel level) {
		State s = state(level);
		long now = level.getGameTime();
		if (!s.on() || now < s.start() || now >= s.end()) {
			return 0.0F;
		}
		return Mth.clamp(Math.min(now - s.start(), s.end() - now) / (float)FADE, 0.0F, 1.0F);
	}

	/** It begins: now ({@code delay} 0), or after {@code delay} ticks of signs; it lasts {@code days}. */
	static void start(MinecraftServer server, String cause, int days, long delay) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		State was = state(level);
		level.setAttached(STATE, new State(true, now + delay, now + delay + days * 24000L, cause, was.rolled()));
		toll(level, 1.0F);
		for (ServerPlayer p : level.players()) {
			Signs.omen(level, p.position(), Signs.GREAT_RADIUS, (int)Math.max(Signs.LEAD, delay) + Signs.GATHER, Magnitude.CATASTROPHIC);
		}
	}

	/** It passes: the red fades and nothing new falls. What's already falling, flooding or burning runs its course. */
	static void stop(MinecraftServer server) {
		ServerLevel level = server.overworld();
		State s = state(level);
		if (!s.on()) {
			return;
		}
		long now = level.getGameTime();
		if (now < s.start()) {
			end(level, s);
			return;
		}
		level.setAttached(STATE, new State(true, s.start(), Math.min(s.end(), now + FADE), s.cause(), s.rolled()));
	}

	private static void end(ServerLevel level, State s) {
		level.setAttached(STATE, new State(false, 0L, 0L, "", s.rolled()));
		if (s.cause().equals("bell")) {
			LastBell.passed(level);
		}
		toll(level, 0.6F);
	}

	/** The low toll of a bell that isn't there, heard by everyone. */
	private static void toll(ServerLevel level, float volume) {
		for (ServerPlayer p : level.players()) {
			level.playSound(null, p.getX(), p.getY() + 12.0, p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 4.0F * volume, 0.5F);
			level.playSound(null, p.getX(), p.getY() + 12.0, p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 3.0F * volume, 0.5F);
		}
	}

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		State s = state(level);
		long now = level.getGameTime();
		if (!s.on()) {
			if (now % 1000 == 0) {
				omen(server, level, s, now);
			}
			return;
		}
		if (now >= s.end()) {
			end(level, s);
			return;
		}
		if (now % TOLL_EVERY == 0) {
			toll(level, now < s.start() ? 0.5F : 1.0F);
		}
		if (now < s.start()) {
			// the night of its omen: the signs over everyone
			if (now % Signs.LEAD == 0) {
				for (ServerPlayer p : level.players()) {
					Signs.omen(level, p.position(), Signs.GREAT_RADIUS, Signs.LEAD + Signs.GATHER, Magnitude.CATASTROPHIC);
				}
			}
			return;
		}
		if (s.end() - now <= FADE || !level.getGameRules().get(Disasters.DISASTER_DAMAGE)) {
			// passing: nothing new
			return;
		}
		RandomSource random = level.getRandom();
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer p = players.get(i);
			// each player on a clock of their own, so the events don't all land on one tick
			long t = now + i * 97L;
			if (t % STONE_EVERY == 0 && random.nextFloat() < STONE_CHANCE) {
				double a = random.nextDouble() * Math.PI * 2.0, d = Mth.lerp(random.nextDouble(), STONE_MIN, STONE_MAX);
				Bolides.fall(level, p.position().add(Math.cos(a) * d, 0.0, Math.sin(a) * d), Magnitude.GREAT + random.nextInt(2), true);
			}
			if (t % QUAKE_EVERY == 0) {
				Earthquakes.start(level, p.position(), Magnitude.GREAT + random.nextInt(2), random, true);
			}
			if (t % VOLCANO_EVERY == 0) {
				Volcanoes.wakeAnyNear(level, p.blockPosition(), Magnitude.CATASTROPHIC);
			}
			if (t % FLOOD_EVERY == 0 && Tsunamis.coastNear(level, p.blockPosition()) != null) {
				Calamities.flood(level, p.blockPosition(), random, Magnitude.CATASTROPHIC);
			}
			if (t % TSUNAMI_EVERY == 0) {
				Tsunamis.toward(level, p.blockPosition(), Magnitude.CATASTROPHIC, 3);
			}
		}
	}

	/** Unrung: on a Super Blood Moon in an old, far-come world, very rarely, the omen of it. */
	private static void omen(MinecraftServer server, ServerLevel level, State s, long now) {
		if (Moons.now(server) != Moons.Moon.SUPER_BLOOD || level.getGameRules().get(Disasters.DISASTER_DAYS) <= 0) {
			return;
		}
		long night = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (night == s.rolled()) {
			return;
		}
		level.setAttached(STATE, new State(false, 0L, 0L, "", night));
		int floor = WorldTiers.floor(server);
		boolean legend = level.players().stream().anyMatch(p -> WorldTiers.of(p) == WorldTiers.Tier.LEGEND);
		if (night < MIN_DAY || floor < FLOOR_FROM && !legend) {
			return;
		}
		float chance = CHANCE * (floor >= WorldTiers.Tier.DREAD.ordinal() ? 2.0F : 1.0F);
		if (level.getRandom().nextFloat() >= chance) {
			return;
		}
		// the rest of the night is its omen; it comes at dawn
		long clock = Math.floorMod(level.getOverworldClockTime(), 24000L);
		long untilDawn = clock < 23000L ? 23000L - clock : 1L;
		start(server, "omen", DAYS, untilDawn);
	}

	/** (The operators' test switch) {@code /kingdom disaster doom start}: it begins now. */
	public static void opStart(MinecraftServer server) {
		start(server, "test", DAYS, 0L);
	}

	/** (The operators' test switch) {@code /kingdom disaster doom stop}: it passes. */
	public static void opStop(MinecraftServer server) {
		stop(server);
	}

	/** (The Last Bell) Rung to its end: it begins. */
	static void rung(MinecraftServer server) {
		start(server, "bell", DAYS, Signs.GATHER);
	}
}
