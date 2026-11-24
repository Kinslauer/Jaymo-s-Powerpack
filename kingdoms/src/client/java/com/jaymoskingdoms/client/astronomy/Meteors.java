package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.astronomy.Celestial;
import com.jaymoskingdoms.astronomy.SunMoon;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shooting stars (Reference Mods patch 14, X6, after ImmersiveVFX's idea; our own code), drawn on the night sky's dome
 * with the stars ({@link SkyClient}). Now and then on a dark night a streak crosses the sky: a bright head and a tail
 * that fades behind it, gone in a second; one in {@link #FIREBALL_OF} is a fireball, wider, warmer and slower.
 * <p>
 * Meteor showers come on the nights they come in the real year, from the real radiants ({@link #SHOWERS}): the
 * Quadrantids, Lyrids, Eta Aquariids, Perseids, Orionids, Leonids and Geminids, each strongest on its peak night and
 * weaker the night before and after. A shower's meteors all fly out from its radiant, so a watcher who traces them back
 * finds the constellation it's named for. The night is counted from dusk ({@link #nightOfYear}), the same count Powers'
 * falling stars use to come down more often on a shower's peak night (the rare one that lands leaves its iron).
 * <p>
 * Like the stars: only in the overworld, only at night, fewer near a settlement's lights and none in rain, none behind the
 * skyline.
 */
public final class Meteors {
	/** The odds a tick of a sporadic meteor under a dark sky, and of a shower's meteor at a peak of {@link #ZHR_FULL}. */
	static final float SPORADIC = 1.0F / 900.0F, PEAK = 1.0F / 45.0F;
	static final float ZHR_FULL = 150.0F;
	/** The night before and after a peak, a shower comes at this share of its rate. */
	static final float SHOULDER = 0.3F;
	/** One meteor in this many is a fireball. */
	static final int FIREBALL_OF = 30;
	/** How fast a meteor crosses (degrees a tick), how long its tail grows (degrees), and how long it lasts (ticks). */
	static final float SPEED_MIN = 1.0F, SPEED_MAX = 2.2F, TAIL_MIN = 5.0F, TAIL_MAX = 13.0F;
	static final int LIFE_MIN = 10, LIFE_MAX = 22;
	/** A shower's meteors first show this far from its radiant (degrees). */
	static final float FROM_RADIANT_MIN = 8.0F, FROM_RADIANT_MAX = 45.0F;
	/** Head width on the dome (blocks), a fireball's, and the night's darkness under which none show. */
	static final float WIDTH = 0.16F, FIREBALL_WIDTH = 0.42F, DARK_ENOUGH = 0.35F;
	/** The most crossing at once. */
	static final int MOST = 12;
	/** Days in the sky's year ({@link Celestial#DAY_SHIFT_DEG}: one turn of the stars). */
	static final int YEAR = 365;
	static final int WHITE_BLUE = 0xE6EEFF, FIREBALL_COLOUR = 0xFFE2A8;

	/** A meteor shower: its name, the night of the year it peaks (1 January is night 0), its radiant and its rate at peak. */
	record Shower(String name, int night, float ra, float dec, float zhr) {
	}

	static final Shower[] SHOWERS = {
		new Shower("quadrantids", 2, 230.0F, 49.0F, 110.0F),
		new Shower("lyrids", 111, 271.0F, 34.0F, 18.0F),
		new Shower("eta_aquariids", 125, 338.0F, -1.0F, 50.0F),
		new Shower("perseids", 223, 48.0F, 58.0F, 100.0F),
		new Shower("orionids", 293, 95.0F, 16.0F, 20.0F),
		new Shower("leonids", 320, 152.0F, 22.0F, 15.0F),
		new Shower("geminids", 347, 112.0F, 33.0F, 150.0F)};

	private static final Identifier WHITE = JaymosKingdoms.id("textures/astronomy/white.png");

	/** One streak: where it starts and the way it goes on the sky (unit vectors), how fast, how long its tail, its life. */
	private static final class Meteor {
		final Vec3 start, along;
		final float speed, tail, bright;
		final int life;
		final boolean fireball;
		int age;

		Meteor(Vec3 start, Vec3 along, float speed, float tail, int life, float bright, boolean fireball) {
			this.start = start;
			this.along = along;
			this.speed = speed;
			this.tail = tail;
			this.life = life;
			this.bright = bright;
			this.fireball = fireball;
		}

		/** Where it is on the sky {@code angle} radians along its path. */
		Vec3 at(double angle) {
			return this.start.scale(Math.cos(angle)).add(this.along.scale(Math.sin(angle)));
		}
	}

	private static final List<Meteor> CROSSING = new ArrayList<>();

	private Meteors() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Meteors::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Meteors::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CROSSING.clear());
	}

	/** The night of the year now, counted from dusk to dusk (1 January's night is 0). */
	public static int nightOfYear(long clockTime) {
		return Math.floorMod(Math.floorDiv(clockTime + 12000L, 24000L), YEAR);
	}

	/** How strong {@code shower} is on {@code night} of the year (0..1 of its peak). */
	static float strength(Shower shower, int night) {
		int off = Math.floorMod(night - shower.night + YEAR / 2, YEAR) - YEAR / 2;
		return off == 0 ? 1.0F : Math.abs(off) == 1 ? SHOULDER : 0.0F;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || level.dimension() != Level.OVERWORLD) {
			CROSSING.clear();
			return;
		}
		CROSSING.removeIf(m -> ++m.age >= m.life);
		float dark = SkyMath.night(level, 0.0F) * (1.0F - SkyClient.pollution());
		if (dark < DARK_ENOUGH || CROSSING.size() >= MOST) {
			return;
		}
		RandomSource random = level.getRandom();
		if (random.nextFloat() < SPORADIC * dark) {
			sporadic(random);
		}
		long clock = level.getOverworldClockTime();
		int night = nightOfYear(clock);
		long time = Math.floorMod(clock, 24000L);
		double lonDeg = player.getX() * SkyMath.LON_DEG_PER_BLOCK;
		int days = (int) (level.getGameTime() / 24000L);
		for (Shower shower : SHOWERS) {
			float strength = strength(shower, night);
			if (strength > 0.0F && random.nextFloat() < PEAK * shower.zhr / ZHR_FULL * strength * dark) {
				Vec3 radiant = direction(shower.ra, shower.dec, time, lonDeg, days);
				if (radiant.y > 0.0) {
					fromRadiant(radiant, random);
				}
			}
		}
	}

	/** The unit direction to a sky place now (as {@link SkyMath#direction}). */
	private static Vec3 direction(float ra, float dec, long time, double lonDeg, int days) {
		double lst = Celestial.lstDeg(time, 0.0F, lonDeg, days);
		double alt = Celestial.altitude(ra, dec, lst), az = Celestial.azimuthFromSouth(ra, dec, lst);
		double h = Math.cos(alt);
		return new Vec3(-h * Math.sin(az), Math.sin(alt), h * Math.cos(az));
	}

	/** A meteor of no shower: anywhere in the upper sky, going any way. */
	private static void sporadic(RandomSource random) {
		double az = random.nextDouble() * Math.PI * 2.0, alt = Math.toRadians(25.0 + random.nextDouble() * 50.0);
		Vec3 start = new Vec3(Math.cos(alt) * Math.sin(az), Math.sin(alt), Math.cos(alt) * Math.cos(az));
		Vec3 any = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5, random.nextGaussian());
		add(start, any.subtract(start.scale(any.dot(start))), random);
	}

	/** A shower's meteor: it shows a little way off its radiant and flies straight away from it. */
	private static void fromRadiant(Vec3 radiant, RandomSource random) {
		Vec3 any = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
		Vec3 side = any.subtract(radiant.scale(any.dot(radiant)));
		if (side.lengthSqr() < 1.0E-6) {
			return;
		}
		double off = Math.toRadians(Mth.lerp(random.nextFloat(), FROM_RADIANT_MIN, FROM_RADIANT_MAX));
		Vec3 start = radiant.scale(Math.cos(off)).add(side.normalize().scale(Math.sin(off)));
		if (start.y < 0.15) {
			return;
		}
		add(start, start.scale(start.dot(radiant)).subtract(radiant), random);
	}

	private static void add(Vec3 start, Vec3 along, RandomSource random) {
		if (along.lengthSqr() < 1.0E-6) {
			return;
		}
		boolean fireball = random.nextInt(FIREBALL_OF) == 0;
		float speed = (float) Math.toRadians(Mth.lerp(random.nextFloat(), SPEED_MIN, SPEED_MAX)) * (fireball ? 0.6F : 1.0F);
		float tail = (float) Math.toRadians(Mth.lerp(random.nextFloat(), TAIL_MIN, TAIL_MAX)) * (fireball ? 1.4F : 1.0F);
		int life = Mth.nextInt(random, LIFE_MIN, LIFE_MAX) + (fireball ? 14 : 0);
		CROSSING.add(new Meteor(start.normalize(), along.normalize(), speed, tail, life, 0.6F + random.nextFloat() * 0.4F, fireball));
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (CROSSING.isEmpty() || level == null || minecraft.player == null || level.dimension() != Level.OVERWORLD
			|| SunMoon.blinded(minecraft.player)) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float dark = SkyMath.night(level, partial) * (1.0F - SkyClient.pollution());
		if (dark <= 0.0F) {
			return;
		}
		float dome = SkyClient.DOME * 0.98F;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(WHITE), (pose, buffer) -> {
			for (Meteor m : CROSSING) {
				float age = m.age + partial, t = age / m.life;
				double head = m.speed * age, tail = Math.max(0.0, head - m.tail * Math.min(1.0F, t * 3.0F));
				Vec3 h = m.at(head), back = m.at(tail);
				if (!SkyClient.clear(h)) {
					continue;
				}
				float glow = (float) Math.sqrt(Math.max(0.0F, Mth.sin(Mth.PI * Math.min(1.0F, t)))) * m.bright * dark;
				int alpha = Math.round(255.0F * glow);
				if (alpha < 6) {
					continue;
				}
				int colour = m.fireball ? FIREBALL_COLOUR : WHITE_BLUE;
				int lit = alpha << 24 | colour, faded = colour;
				Vec3 hd = h.scale(dome), td = back.scale(dome);
				Vec3 side = hd.subtract(td).cross(hd);
				if (side.lengthSqr() < 1.0E-8) {
					continue;
				}
				float width = m.fireball ? FIREBALL_WIDTH : WIDTH;
				side = side.normalize().scale(width * 0.5);
				SkyClient.quad(buffer, pose,
					new float[] {(float) (hd.x + side.x), (float) (hd.y + side.y), (float) (hd.z + side.z)}, lit, 0.0F, 0.0F,
					new float[] {(float) (hd.x - side.x), (float) (hd.y - side.y), (float) (hd.z - side.z)}, lit, 1.0F, 0.0F,
					new float[] {(float) td.x, (float) td.y, (float) td.z}, faded, 1.0F, 1.0F,
					new float[] {(float) td.x, (float) td.y, (float) td.z}, faded, 0.0F, 1.0F);
				SkyClient.billboard(buffer, pose, (float) hd.x, (float) hd.y, (float) hd.z, width * 1.8F, Math.round(alpha * 0.8F) << 24 | colour);
			}
		});
	}
}
