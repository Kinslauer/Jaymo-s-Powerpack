package com.jaymospowers.client.power.wind;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.power.wind.WindCold;
import com.jaymospowers.power.wind.WindWeather;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Jung) <b>Airflow sense</b>: crouching and holding ability 3's key (V), a Wind user feels the air that moving things
 * stir ({@link #route}: that press is the sense's, and doesn't start the ability's channel). At the edge of the screen,
 * on the side each one is on (ahead is the top edge, behind the bottom, left and right the sides), a few wavy lines
 * ripple: bigger and clearer the nearer it is, smaller and fainter the further. Several in the same direction make one
 * bigger ripple that runs faster: a crowd. Each fades over a moment once its maker stops. It feels through walls and in
 * the dark, and it feels the unseen. It is a sense of direction and nearness only: no outline and no height.
 *
 * <p>What it can't feel is anything that is still or moving softly: standing, sleeping, or sneaking
 * ({@link #MOVE_MIN} is above a sneak's pace). It reaches {@link #RANGE} blocks (four chunks) under a clear day sky,
 * less at night, in the rain, in a storm (where the gale also throws the ripples about so they wander off true), with
 * no sky overhead and where there is no weather. In the rain or the cold it loses soft movement. Under water it feels
 * nothing.
 *
 * <p>Client side only, from what this client already knows of the things round it: nothing is sent, and it costs
 * nothing. From {@link #MIN_MASTERY} unlocked abilities.
 *
 * <p>(Jung) Listening is a deep focus: over {@link #FOCUS_IN} ticks every game sound, music too, sinks to {@link #HUSH}
 * of its volume (menu clicks keep theirs; {@code WindSenseSoundMixin}, and {@link #refresh} for what is already
 * playing), and only a soft, steady breeze is heard ({@link Breeze}). The world drains to grey and white
 * ({@link #greyEffect}, a post effect), under a light grey veil and a soft white frame. There is no tunnel: the middle of the view stays as clear as the edges, only greyer. Letting go brings it all
 * back over {@link #FOCUS_OUT} ticks.
 */
public final class WindAirSense {
	// ---- tuning ----
	/** Unlocked abilities needed to feel anything. */
	static final int MIN_MASTERY = 1;
	/** How far it reaches (blocks; four chunks), at full under a clear day sky. */
	static final double RANGE = 64.0;
	/**
	 * The reach elsewhere (shares of {@link #RANGE}): a clear night, the rain, a storm (the gale drowns the stirrings),
	 * under a roof or underground, and in a dimension with no weather.
	 */
	static final double NIGHT_RANGE = 0.75, RAIN_RANGE = 0.6, STORM_RANGE = 0.5, SHELTERED_RANGE = 0.5, OTHERWORLD_RANGE = 0.75;
	/** Slower than this (blocks a tick) stirs too little to feel: a sneak is about 0.065, a walk about 0.22. */
	static final double MOVE_MIN = 0.08;
	/** In the rain or the cold, soft movement is lost: the least it feels is this many times {@link #MOVE_MIN}. */
	static final double DULLED_MIN = 1.5;
	/** This fast (blocks a tick) or faster is felt at full strength. */
	static final double MOVE_FULL = 0.3;
	/** The faintest a felt movement starts at, and what a streak loses each tick once its maker stops (so about a second). */
	static final float STRENGTH_MIN = 0.2F, FADE = 0.05F;
	/** Things within this many degrees of each other are felt as one crowd, and at most this many crowds are drawn (the nearest). */
	static final float CROWD_DEGREES = 20.0F;
	static final int MAX_CROWDS = 12;
	/** How far in from the screen's edge the ripples sit (pixels). */
	static final float EDGE_INSET = 14.0F;
	/** A ripple's length along the edge (pixels), for the nearest and for the furthest thing felt. */
	static final float LENGTH_NEAR = 64.0F, LENGTH_FAR = 18.0F;
	/** How high its waves rise (pixels), near and far (a crowd raises them only up to {@link #CROWD_RISE_MAX} times), and their wavelength (pixels). */
	static final float RISE_NEAR = 3.0F, RISE_FAR = 1.0F, WAVELENGTH = 14.0F;
	/** Its lines: three for something near, one for something far, this far apart (pixels), drawn this thick near and far. */
	static final int LINES_NEAR = 3;
	static final float LINE_GAP = 6.0F, THICK_NEAR = 2.0F, THICK_FAR = 1.0F;
	/** How fast its waves run along it (radians a tick), and how much each more thing in a crowd adds to that and to its size, up to the caps. */
	static final float RUN = 0.2F, CROWD_RUN = 0.12F, RUN_MAX = 0.65F, CROWD_SIZE = 0.3F, SIZE_MAX = 2.0F, CROWD_RISE_MAX = 1.5F;
	/** In a storm each ripple wanders this far off true (degrees), this fast (radians a tick). */
	static final float STORM_WOBBLE = 20.0F, STORM_WOBBLE_RATE = 0.35F;
	/** The lines' colour, and their opacity (0-255) for the nearest and the furthest; under each, a dark edge this share as opaque, so they show on a pale sky. */
	static final int COLOR = 0xE8FBFF, ALPHA_NEAR = 235, ALPHA_FAR = 110;
	static final float EDGE = 0.6F;
	/** The focus: ticks to come on and to go off, and the world's sounds at full focus (a share of their volume). */
	static final float FOCUS_IN = 8.0F, FOCUS_OUT = 6.0F, HUSH = 0.05F;
	/** The breeze heard in the focus: its volume at full focus and its pitch (the flight's wind, low and soft). */
	static final float BREEZE_VOLUME = 0.18F, BREEZE_PITCH = 0.55F;
	/** A light grey veil over the whole view, HUD too (0xRRGGBB, and its most opaque, 0-255); the world's own colour goes with {@link #greyEffect}. */
	static final int GREY = 0xC8CCD0, GREY_ALPHA = 0x20;
	/** The white frame: its most opaque at the very edge (0-255), its depth (a share of the smaller screen side), and its soft steps. */
	static final int FRAME_ALPHA = 0x6E, FRAME_STEPS = 12;
	static final float FRAME_DEPTH = 0.2F;

	/** entity id → how strongly its movement is felt now (0..1]. */
	private static final Map<Integer, Float> SIGNALS = new HashMap<>();

	/** Things felt in one direction: their summed directions (screen x, y), how many, the nearest's nearness (0..1) and the strongest's stirring. */
	private static final class Crowd {
		float x, y, near, strength;
		int count;
		final int seed;

		Crowd(int seed) {
			this.seed = seed;
		}
	}

	/** The breeze playing in the focus, if it is; and the hush the sound categories were last set for. */
	private static @org.jspecify.annotations.Nullable Breeze breeze;
	private static float refreshed = 1.0F;

	private WindAirSense() {
	}

	/** Whether the sense is on right now (crouched, V held), and whether this hold of V is the sense's (until V is let go). */
	private static boolean listening, latched;
	/** How far into the focus (0..1), now and last tick. */
	private static float focus, focusO;

	static void clear() {
		SIGNALS.clear();
		listening = latched = false;
		focus = focusO = 0.0F;
		if (breeze != null) {
			breeze.end();
			breeze = null;
		}
		refresh(Minecraft.getInstance());
	}

	/** (WindSenseSoundMixin) What the game's sounds are multiplied by just now: 1, down to {@link #HUSH} in full focus. */
	public static float hush() {
		return 1.0F - (1.0F - HUSH) * focus;
	}

	/** The grey-and-white post effects, a quarter, a half, three quarters and all the way (assets/jaymospowers/post_effect/wind_sense_grey_N.json). */
	private static final net.minecraft.resources.Identifier[] GREY_EFFECTS = {
		com.jaymospowers.JaymosPowers.id("wind_sense_grey_1"), com.jaymospowers.JaymosPowers.id("wind_sense_grey_2"),
		com.jaymospowers.JaymosPowers.id("wind_sense_grey_3"), com.jaymospowers.JaymosPowers.id("wind_sense_grey_4")};

	/**
	 * (WindSenseGameRendererMixin; Jung: "make the screen go grey and white colour wise") The post effect the world
	 * wears this frame: drained toward grey and white as far as the focus has come in, in quarters; null out of it, and
	 * in low-end mode (the grey veil over the HUD is still drawn).
	 */
	public static net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier greyEffect() {
		int step = Math.round(focus * GREY_EFFECTS.length);
		return step <= 0 || com.jaymospowers.client.LowEndMode.on() ? null : GREY_EFFECTS[Math.min(step, GREY_EFFECTS.length) - 1];
	}

	/** (WindSenseSoundMixin) Whether {@code sound} is the focus's own breeze (never hushed). */
	public static boolean isBreeze(net.minecraft.client.resources.sounds.SoundInstance sound) {
		return sound == breeze;
	}

	/**
	 * Has every sound category work its playing sounds' volumes out again (the menus' and the master's aside), so what
	 * is already playing, music too, follows the hush ({@code WindSenseSoundMixin} applies it as each volume is worked
	 * out). Only a refresh: the categories' own gains are left alone, as they are the music's fade between tracks and
	 * would otherwise square every volume below 100% once the focus ends.
	 */
	private static void refresh(Minecraft minecraft) {
		float hush = hush();
		if (hush == refreshed || minecraft.getSoundManager() == null) {
			return;
		}
		refreshed = hush;
		for (net.minecraft.sounds.SoundSource source : net.minecraft.sounds.SoundSource.values()) {
			if (source != net.minecraft.sounds.SoundSource.MASTER && source != net.minecraft.sounds.SoundSource.UI) {
				minecraft.getSoundManager().refreshCategoryVolume(source);
			}
		}
	}

	/** The soft wind heard in the focus, rising and falling with it; it ends itself once the focus is gone. */
	private static final class Breeze extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
		Breeze() {
			// (on the master category, which the hush never touches, so the breeze is the one thing heard)
			super(net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, net.minecraft.sounds.SoundSource.MASTER,
				net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
			this.volume = 0.01F;
			this.pitch = BREEZE_PITCH;
		}

		@Override
		public void tick() {
			if (focus <= 0.0F) {
				this.stop();
				return;
			}
			this.volume = Math.max(0.01F, BREEZE_VOLUME * focus);
		}

		void end() {
			this.stop();
		}
	}

	/**
	 * From the core's key loop, for each ability key each tick: true if this hold of ability 3's key is the airflow
	 * sense's, so the core reports the key as up and the ability doesn't channel. It becomes the sense's when the key
	 * goes down while crouched; the sense is on for as long as both are held, and the hold stays the sense's until the
	 * key is let go (standing up mid-hold stops the sense without starting the channel).
	 */
	public static boolean route(LocalPlayer player, com.jaymospowers.api.PowerKey key, boolean physical, boolean wasDown) {
		if (key != com.jaymospowers.api.PowerKey.ABILITY_3) {
			return false;
		}
		if (!physical) {
			listening = latched = false;
			return false;
		}
		if (!latched) {
			if (wasDown || !player.isShiftKeyDown()
				|| !com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.wind.WindPower.INSTANCE)) {
				return false;
			}
			latched = true;
		}
		listening = player.isShiftKeyDown();
		return true;
	}

	/** Whether {@code player} feels the air right now. */
	private static boolean feels(Player player) {
		return Mastery.unlocked(player) >= MIN_MASTERY && player.isAlive() && !player.isSpectator() && !player.isEyeInFluid(FluidTags.WATER);
	}

	/** How far {@code player} feels (blocks): {@link #RANGE} under a clear day sky, less under any other. */
	private static double range(Player player) {
		return RANGE * switch (WindWeather.sky(player)) {
			case CLEAR_DAY -> 1.0;
			case CLEAR_NIGHT -> NIGHT_RANGE;
			case RAIN -> RAIN_RANGE;
			case STORM, STORMBOUND -> STORM_RANGE;
			case SHELTERED -> SHELTERED_RANGE;
			case OTHERWORLD -> OTHERWORLD_RANGE;
		};
	}

	/** Every tick, for the local Wind user: what moved near them, and the fading of what has stopped. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		focusO = focus;
		focus = listening && feels(player) ? Math.min(1.0F, focus + 1.0F / FOCUS_IN) : Math.max(0.0F, focus - 1.0F / FOCUS_OUT);
		if (focus > 0.0F && (breeze == null || breeze.isStopped())) {
			breeze = new Breeze();
			minecraft.getSoundManager().play(breeze);
		}
		// (each tick it moves: the focus takes only a few ticks to come and go)
		refresh(minecraft);
		ClientLevel level = minecraft.level;
		if (level == null || !feels(player)) {
			SIGNALS.clear();
			return;
		}
		SIGNALS.replaceAll((id, strength) -> strength - FADE);
		SIGNALS.values().removeIf(strength -> strength <= 0.0F);
		double range = range(player);
		WindWeather.Kind sky = WindWeather.sky(player);
		double least = sky == WindWeather.Kind.RAIN || WindCold.isCold(player) ? MOVE_MIN * DULLED_MIN : MOVE_MIN;
		Vec3 eye = player.getEyePosition();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range), LivingEntity::isAlive)) {
			if (entity == player || entity.isSpectator() || entity.distanceToSqr(player) > range * range
				|| entity.getVehicle() == player || player.getVehicle() == entity) {
				continue;
			}
			double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
			double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (moved < least) {
				continue;
			}
			float strength = (float)Mth.clamp((moved - least) / (MOVE_FULL - least), STRENGTH_MIN, 1.0);
			SIGNALS.merge(entity.getId(), strength, (was, now) -> Math.max(was, now));
		}
	}

	/** The focus's grey and white frame, then a ripple at the screen's edge for each crowd of things felt. */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		focus(graphics, Mth.lerp(partial, focusO, focus));
		if (!listening || level == null || !feels(player)) {
			return;
		}
		float yaw = player.getViewYRot(partial);
		Vec3 eye = player.getEyePosition(partial);
		double range = range(player);
		WindWeather.Kind sky = WindWeather.sky(player);
		boolean storm = sky == WindWeather.Kind.STORM || sky == WindWeather.Kind.STORMBOUND;
		float time = player.tickCount + partial;
		// what is felt, gathered by direction: things within CROWD_DEGREES of each other make one crowd
		int bins = Math.round(360.0F / CROWD_DEGREES);
		Map<Integer, Crowd> crowds = new HashMap<>();
		for (Map.Entry<Integer, Float> signal : SIGNALS.entrySet()) {
			Entity entity = level.getEntity(signal.getKey());
			if (entity == null) {
				continue;
			}
			Vec3 to = entity.getEyePosition(partial).subtract(eye);
			float near = 1.0F - (float)Mth.clamp(to.length() / range, 0.0, 1.0);
			// 0: straight ahead (the top edge), clockwise round to behind (the bottom)
			float bearing = (float)Math.toDegrees(Mth.atan2(to.z, to.x)) - 90.0F - yaw;
			if (storm) {
				// the gale throws it about: each wanders on its own beat
				bearing += Mth.sin(time * STORM_WOBBLE_RATE + signal.getKey() * 1.7F) * STORM_WOBBLE;
			}
			int bin = Math.floorMod(Math.round(bearing / CROWD_DEGREES), bins);
			Crowd crowd = crowds.computeIfAbsent(bin, Crowd::new);
			float angle = bearing * Mth.DEG_TO_RAD;
			crowd.x += Mth.sin(angle);
			crowd.y -= Mth.cos(angle);
			crowd.count++;
			crowd.near = Math.max(crowd.near, near);
			crowd.strength = Math.max(crowd.strength, signal.getValue());
		}
		List<Crowd> drawn = new ArrayList<>(crowds.values());
		drawn.sort((a, b) -> Float.compare(b.near, a.near));
		for (int c = 0; c < Math.min(drawn.size(), MAX_CROWDS); c++) {
			ripple(graphics, drawn.get(c), time);
		}
	}

	/**
	 * One crowd's ripple: a few wavy lines lying along the screen's edge where its direction meets it, each a little
	 * shorter and further in than the last, their waves running along them. Nearer is longer, higher, thicker, more
	 * lines and clearer; more things in the crowd make it bigger and run faster.
	 */
	private static void ripple(GuiGraphicsExtractor graphics, Crowd crowd, float time) {
		float length = Mth.sqrt(crowd.x * crowd.x + crowd.y * crowd.y);
		if (length < 1.0E-4F) {
			return;
		}
		float dx = crowd.x / length, dy = crowd.y / length;
		float width = graphics.guiWidth(), height = graphics.guiHeight();
		float halfW = width / 2.0F - EDGE_INSET, halfH = height / 2.0F - EDGE_INSET;
		// where the direction from the middle of the screen meets the inset edge, and along which edge it lies
		float toSide = Math.abs(dx) < 1.0E-4F ? Float.MAX_VALUE : halfW / Math.abs(dx);
		float toTop = Math.abs(dy) < 1.0E-4F ? Float.MAX_VALUE : halfH / Math.abs(dy);
		boolean alongX = toTop <= toSide;
		float reach = Math.min(toSide, toTop);
		float px = width / 2.0F + dx * reach, py = height / 2.0F + dy * reach;
		// in: toward the middle of the screen
		float inX = alongX ? 0.0F : -Math.signum(dx), inY = alongX ? -Math.signum(dy) : 0.0F;
		float near = crowd.near;
		float size = Math.min(SIZE_MAX, 1.0F + CROWD_SIZE * (crowd.count - 1));
		float run = Math.min(RUN_MAX, RUN + CROWD_RUN * (crowd.count - 1));
		float span = Mth.lerp(near, LENGTH_FAR, LENGTH_NEAR) * size;
		float rise = Mth.lerp(near, RISE_FAR, RISE_NEAR) * Math.min(CROWD_RISE_MAX, size);
		float thick = Mth.lerp(near, THICK_FAR, THICK_NEAR);
		int lines = 1 + Math.round((LINES_NEAR - 1) * near);
		// a faint one is still seen: brighter the nearer, and the more it stirs
		float clear = Math.max(near, crowd.strength * 0.5F);
		int alpha = Math.round(Mth.lerp(clear, ALPHA_FAR, ALPHA_NEAR) * Math.min(1.0F, crowd.strength + 0.4F));
		// keep the whole of it on the screen
		float limit = alongX ? width / 2.0F - EDGE_INSET : height / 2.0F - EDGE_INSET;
		float along0 = alongX ? px - width / 2.0F : py - height / 2.0F;
		along0 = Mth.clamp(along0, -limit + span / 2.0F, limit - span / 2.0F);
		float centreX = alongX ? width / 2.0F + along0 : px, centreY = alongX ? py : height / 2.0F + along0;
		// the dark edges first, all of them, then the lines over them
		for (int pass = 0; pass < 2; pass++) {
			for (int line = 0; line < lines; line++) {
				float lineSpan = span * (1.0F - 0.2F * line);
				// (the lines nearly in step, like wind lines one behind another)
				float phase = time * run + line * 0.5F + crowd.seed * 2.3F;
				float offIn = line * LINE_GAP;
				int steps = Math.max(2, Math.round(lineSpan / 2.0F));
				for (int k = 0; k <= steps; k++) {
					float u = -lineSpan / 2.0F + lineSpan * k / steps;
					// the ends taper, so each line rises out of nothing and sinks back
					float envelope = Mth.sin(Mth.PI * k / steps);
					float across = offIn + rise * envelope * Mth.sin(Mth.TWO_PI * u / WAVELENGTH - phase);
					float x = alongX ? centreX + u : centreX + inX * across;
					float y = alongX ? centreY + inY * across : centreY + u;
					int a = Math.round(alpha * (0.35F + 0.65F * envelope) * (1.0F - 0.25F * line));
					if (a < 6) {
						continue;
					}
					int x0 = Math.round(x - (alongX ? 1.0F : thick / 2.0F)), y0 = Math.round(y - (alongX ? thick / 2.0F : 1.0F));
					int x1 = x0 + (alongX ? 2 : Math.max(1, Math.round(thick))), y1 = y0 + (alongX ? Math.max(1, Math.round(thick)) : 2);
					if (pass == 0) {
						graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, Math.round(a * EDGE) << 24);
					} else {
						graphics.fill(x0, y0, x1, y1, a << 24 | COLOR);
					}
				}
			}
		}
	}

	/**
	 * The focus over the whole view, {@code amount} of the way in: an even grey that drains the colour (the middle as
	 * much as the edges, so no tunnel), and a white frame, densest at the very edge and gone a fifth of the way in.
	 */
	private static void focus(GuiGraphicsExtractor graphics, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		// eased, so it settles in rather than snapping
		float eased = amount * amount * (3.0F - 2.0F * amount);
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int grey = Math.round(GREY_ALPHA * eased);
		if (grey > 0) {
			graphics.fill(0, 0, width, height, grey << 24 | GREY);
		}
		int alpha = Math.round(FRAME_ALPHA * eased);
		int depth = Math.max(8, Math.round(Math.min(width, height) * FRAME_DEPTH));
		int step = Math.max(1, depth / FRAME_STEPS);
		// nested bands, each step in fainter than the last: brightest at the very edge
		for (int i = 0; i < FRAME_STEPS; i++) {
			int a = alpha * (FRAME_STEPS - i) * (FRAME_STEPS - i) / (FRAME_STEPS * FRAME_STEPS);
			if (a <= 0) {
				break;
			}
			int c = a << 24 | 0xFFFFFF, o = i * step;
			graphics.fill(o, o, width - o, o + step, c);
			graphics.fill(o, height - o - step, width - o, height - o, c);
			graphics.fill(o, o + step, o + step, height - o - step, c);
			graphics.fill(width - o - step, o + step, width - o, height - o - step, c);
		}
	}
}
