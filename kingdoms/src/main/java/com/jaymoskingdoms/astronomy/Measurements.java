package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Skills;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What an Astronomer's Journal records: measurements taken with an armillary sphere, each written once. A reading is
 * taken by the server, at the sphere, from the stars the reader has discovered ({@link StarDiscovery}) and the sky's model
 * ({@link Celestial}); the client only asks for it ({@link ArmillaryNet.Measure}).
 * <p>
 * A reading can fail, and says why in one line on the sphere's screen: no journal to write in, not under the
 * Overworld's sky, daylight, dazzled or blinded ({@link SunMoon#blinded}), clouds, no open sky above the sphere, or none of your stars above the horizon. One that
 * works writes down what is new:
 * <ul>
 * <li>the altitude and bearing of each discovered star above the horizon not yet measured ({@link #STARS_PER_READING} at
 * most, highest first);</li>
 * <li>the latitude, from Polaris's altitude, once;</li>
 * <li>the sphere's longitude, once for each {@link #LONGITUDE_STEP_DEG} band, when Astrometry makes the readings fine
 * enough to tell the bands apart;</li>
 * <li>the sky's yearly drift, once for each of the {@link #SKY_MONTHS} months of its year.</li>
 * </ul>
 * Only something not yet in the journal counts, so repeating a reading earns nothing. Each new measurement is Scholar
 * experience ({@link Skills.Pillar#SCHOLAR}): the pillar's levels are points, spent on Astrometry in the journal, and each
 * Astrometry rank makes the next readings finer ({@link #resolution}).
 */
public final class Measurements {
	// ---- tuning ----
	/** Scholar experience for each new measurement: a star's place, the latitude, a longitude band, a month of the sky's year. */
	public static final int XP_STAR = 6, XP_LATITUDE = 40, XP_LONGITUDE = 25, XP_SKY_MONTH = 30;
	/** The most new star places one reading writes down (the highest stars first). */
	public static final int STARS_PER_READING = 8;
	/** Ticks between one player's readings: the rings have to be set again. */
	public static final long READING_TICKS = 100L;
	/** How near the sphere a reader must stand (blocks). */
	static final double REACH = 6.0;
	/** A reading's finest step with no Astrometry (degrees); each rank divides it ({@link #resolution}). */
	public static final double BASE_RESOLUTION_DEG = 1.0;
	/** Longitudes count as different places this far apart (degrees: 500 blocks east or west). */
	public static final double LONGITUDE_STEP_DEG = 0.25;
	/** The sky's year is cut into this many months for the yearly-drift readings. */
	public static final int SKY_MONTHS = 12;
	/** Polaris's Hipparcos number: its altitude gives the latitude. */
	public static final int POLARIS = 11767;

	/** Keys of the measurements: a star's place ({@code star:<hip>}), the latitude, a longitude band, a month of the sky's year. */
	public static final String STAR = "star:", LATITUDE = "latitude", LONGITUDE = "lon:", MONTH = "month:";

	/**
	 * One measurement: what was measured ({@code key}), the day it was taken, its two values and the step they were
	 * rounded to (degrees). A star's place: altitude and bearing from north. The latitude: Polaris's altitude. A longitude:
	 * degrees east, and the band. A month of the sky's year: the month (1-12), and how far the sky had drifted (degrees).
	 */
	public record Entry(String key, long day, float a, float b, float resolution) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(Entry::key),
				Codec.LONG.optionalFieldOf("day", 0L).forGetter(Entry::day),
				Codec.FLOAT.optionalFieldOf("a", 0.0F).forGetter(Entry::a),
				Codec.FLOAT.optionalFieldOf("b", 0.0F).forGetter(Entry::b),
				Codec.FLOAT.optionalFieldOf("resolution", 1.0F).forGetter(Entry::resolution))
			.apply(i, Entry::new));
	}

	/** On a player: their journal's measurements, in the order taken. Saved, kept through death, synced to them alone. */
	public static final AttachmentType<List<Entry>> ENTRIES = AttachmentRegistry.create(JaymosKingdoms.id("astronomy_journal"),
		builder -> builder.persistent(Entry.CODEC.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.fromCodec(Entry.CODEC.listOf()), AttachmentSyncPredicate.targetOnly()));

	/** By player: the game time of their last reading. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Measurements() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ArmillaryNet.Measure.TYPE, (payload, context) -> read(context.player(), payload.pos()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/** {@code player}'s measurements (empty if none). */
	public static List<Entry> of(Player player) {
		return player.getAttachedOrElse(ENTRIES, List.of());
	}

	/** The step (degrees) readings are rounded to at Astrometry rank {@code astrometry}. */
	public static double resolution(int astrometry) {
		return BASE_RESOLUTION_DEG / (1 + Math.max(0, astrometry));
	}

	/** The first Astrometry rank whose readings are finer than a longitude band, so bands can be told apart. */
	public static int longitudeRank() {
		int rank = 0;
		while (resolution(rank) >= LONGITUDE_STEP_DEG) {
			rank++;
		}
		return rank;
	}

	/** Days in the sky's year: one full turn of the yearly drift. */
	public static double yearDays() {
		return 360.0 / Celestial.DAY_SHIFT_DEG;
	}

	/** The month of the sky's year (0 to {@link #SKY_MONTHS} - 1) on day {@code days}. */
	public static int skyMonth(long days) {
		double into = days - Math.floor(days / yearDays()) * yearDays();
		return Math.min(SKY_MONTHS - 1, (int) (into / (yearDays() / SKY_MONTHS)));
	}

	/** {@code value} rounded to the nearest {@code step}. */
	static float round(double value, double step) {
		return (float) (Math.round(value / step) * step);
	}

	/** {@code player} asked for a reading with the sphere at {@code pos}: take it, write down what is new, say how it went. */
	static void read(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level) || !(level.getBlockEntity(pos) instanceof ArmillaryBlockEntity sphere)
			|| player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < READING_TICKS && now >= last) {
			reply(player, "busy", 0, 0);
			return;
		}
		String failure = failure(level, pos, player);
		if (failure != null) {
			reply(player, failure, 0, 0);
			return;
		}
		LAST.put(player.getUUID(), now);
		sphere.record(level);

		int astrometry = Skills.of(player).rank(Skills.Perk.ASTROMETRY);
		double step = resolution(astrometry);
		long day = now / Celestial.DAY_TICKS;
		double lon = Celestial.longitude(pos.getX() + 0.5);
		double lst = Celestial.lstDeg(Math.floorMod(level.getOverworldClockTime(), Celestial.DAY_TICKS), 0.0F, lon, (int) day);

		// the discovered stars above the horizon, highest first
		List<double[]> up = new ArrayList<>();
		for (int hip : StarDiscovery.discovered(player)) {
			StarPositions.Place place = StarPositions.of(hip);
			if (place == null) {
				continue;
			}
			double alt = Celestial.altitude(place.ra(), place.dec(), lst);
			if (alt > 0.0) {
				up.add(new double[] {hip, Math.toDegrees(alt), Celestial.bearingDeg(Celestial.azimuthFromSouth(place.ra(), place.dec(), lst))});
			}
		}
		if (up.isEmpty()) {
			reply(player, "no_stars", 0, 0);
			return;
		}
		up.sort(Comparator.comparingDouble((double[] s) -> s[1]).reversed());

		List<Entry> had = of(player);
		Set<String> known = new HashSet<>();
		for (Entry e : had) {
			known.add(e.key());
		}
		List<Entry> fresh = new ArrayList<>();
		int xp = 0, stars = 0;
		for (double[] s : up) {
			int hip = (int) s[0];
			if (hip == POLARIS && known.add(LATITUDE)) {
				// Polaris sits within a degree of the pole, so its altitude is (nearly) the latitude
				fresh.add(new Entry(LATITUDE, day, round(s[1], step), 0.0F, (float) step));
				xp += XP_LATITUDE;
			}
			if (stars < STARS_PER_READING && known.add(STAR + hip)) {
				fresh.add(new Entry(STAR + hip, day, round(s[1], step), round(s[2], step), (float) step));
				xp += XP_STAR;
				stars++;
			}
		}
		if (step < LONGITUDE_STEP_DEG) {
			long band = (long) Math.floor(lon / LONGITUDE_STEP_DEG);
			if (known.add(LONGITUDE + band)) {
				fresh.add(new Entry(LONGITUDE + band, day, round(lon, step), band, (float) step));
				xp += XP_LONGITUDE;
			}
		}
		int month = skyMonth(day);
		if (known.add(MONTH + month)) {
			double drift = (day * Celestial.DAY_SHIFT_DEG) % 360.0;
			fresh.add(new Entry(MONTH + month, day, month + 1, round(drift, step), (float) step));
			xp += XP_SKY_MONTH;
		}
		if (fresh.isEmpty()) {
			// a reading already in the journal earns nothing
			reply(player, "nothing_new", 0, 0);
			return;
		}
		List<Entry> next = new ArrayList<>(had);
		next.addAll(fresh);
		player.setAttached(ENTRIES, List.copyOf(next));
		int before = Skills.of(player).xp(Skills.Pillar.SCHOLAR);
		Skills.grant(player, Skills.Pillar.SCHOLAR, xp);
		int gained = Skills.of(player).xp(Skills.Pillar.SCHOLAR) - before;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.8F, 1.0F);
		reply(player, "recorded", fresh.size(), gained);
	}

	/** Why a reading at {@code pos} can't be taken now (the outcome's name), or null if it can. */
	private static @Nullable String failure(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!AstronomersJournal.carries(player)) {
			return "no_journal";
		}
		if (level.dimension() != Level.OVERWORLD) {
			return "not_overworld";
		}
		if (!level.isDarkOutside()) {
			return "daylight";
		}
		if (SunMoon.blinded(player)) {
			return "dazzled";
		}
		if (level.isRaining() || level.isThundering()) {
			return "clouds";
		}
		if (!level.canSeeSky(pos.above())) {
			return "roofed";
		}
		return null;
	}

	private static void reply(ServerPlayer player, String outcome, int count, int xp) {
		if (ServerPlayNetworking.canSend(player, ArmillaryNet.Reading.TYPE)) {
			ServerPlayNetworking.send(player, new ArmillaryNet.Reading(outcome, count, xp));
		}
	}
}
