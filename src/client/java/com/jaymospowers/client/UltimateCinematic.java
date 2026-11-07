package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.power.xenoform.XenoMorph;
import com.jaymospowers.core.UltimateFilm;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.24) An ultimate as a short film ({@link UltimateFilm} says which shot and when). The caster's bars slide in,
 * the HUD clears and the camera makes its one move; anyone near only gets the bars. Your movement and aim stay yours
 * (the camera is only drawn from somewhere else), and the film gets out of the way at once on the skip key
 * (Backspace), an attack or use, or a hit taken. Same machinery as the beam battle's film and Jaymo's Kingdoms'
 * handshake, and it waits its turn behind the beam battle's.
 */
public final class UltimateCinematic {
	/** Each bar's height, as a share of the screen's; how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.11F, BAR_EASE = 0.3F;
	/** (0.20.60) How far every shot is brought down (blocks, times the caster's size), and the lowest it goes over their feet. */
	static final double SHOT_DROP = 0.6, SHOT_FLOOR = 0.5;
	/** Kept this far off a wall the camera would otherwise sink into (blocks). */
	static final double WALL_MARGIN = 0.3;
	/** {@link UltimateFilm#ZOOM}: how far back it goes (blocks), and the share of the film spent going out. */
	static final double ZOOM_OUT = 9.0;
	static final float ZOOM_OUT_SHARE = 0.6F;
	/** {@link UltimateFilm#SKYWARD}: how far behind (blocks), and how high it ends up looking. */
	static final double SKY_BACK = 4.0, SKY_LOOK_UP = 14.0;
	/** {@link UltimateFilm#CRANE}: its reach out (from, to) and its rise (from, to), and how far it circles (degrees). */
	static final double CRANE_FROM = 5.0, CRANE_TO = 9.0, CRANE_LOW = 1.5, CRANE_HIGH = 7.0, CRANE_TURN = 80.0;
	/** {@link UltimateFilm#ORBIT}: how far out (blocks) and how far it sweeps (degrees). */
	static final double ORBIT_OUT = 2.6, ORBIT_TURN = 100.0;
	/** {@link UltimateFilm#PROFILE}: how far to the side of the line (blocks). */
	static final double PROFILE_SIDE = 5.0;
	/** {@link UltimateFilm#PUSH_IN}: how much of the way to the subject it pushes. */
	static final double PUSH_SHARE = 0.45;
	/** {@link UltimateFilm#CHASE}: how far to the side and up it keeps (blocks). */
	static final double CHASE_SIDE = 4.0, CHASE_UP = 1.2;
	/** (0.18.46) {@link UltimateFilm#DUTCH}: the tilt (degrees, at its fullest), how far out it sits (from, to; blocks) and how low. */
	static final float DUTCH_ROLL = 17.0F;
	static final double DUTCH_FROM = 3.6, DUTCH_TO = 2.4, DUTCH_LOW = 0.5;
	/** (0.18.46) {@link UltimateFilm#CUTS}: how many angles the film jumps between, and how far out each sits (blocks). */
	static final int CUT_ANGLES = 3;
	static final double CUT_CLOSE = 2.2, CUT_WIDE = 5.5, CUT_HIGH = 3.2;
	/** (0.18.46) {@link UltimateFilm#CUTS}: the tilt of the sideways cut (degrees). */
	static final float CUT_ROLL = 11.0F;
	/**
	 * (0.20.9) {@link UltimateFilm#MORPH}: the share of a part's beat the camera takes to zoom in (the part grows while it holds), how
	 * far it keeps pushing in over the hold, how far out it starts and ends (times the body's height), and how far to the side (degrees).
	 * {@code *_FIRST} are the first-time cut's: bigger zooms, a slower push, and a shake on the last beat.
	 */
	static final float MORPH_ZOOM = 0.4F, MORPH_ZOOM_FIRST = 0.5F, MORPH_PUSH = 0.1F, MORPH_PUSH_FIRST = 0.18F, MORPH_BLEND = 0.3F;
	static final double MORPH_WIDE = 2.2, MORPH_WIDE_FIRST = 2.8, MORPH_CLOSE = 0.8, MORPH_CLOSE_FIRST = 0.55, MORPH_MIN_CLOSE = 0.7;
	static final double MORPH_FRAME = 2.0, MORPH_FRAME_FIRST = 2.4, MORPH_LIFT = 0.08, MORPH_SHAKE = 0.05;
	static final float MORPH_ROLL_FIRST = 3.0F;
	/**
	 * (0.20.93) {@link UltimateFilm#SPEAKER}: the shares of the film spent coming in from your shoulder and (from) where it starts to
	 * rise to the words; how far off the speaker's face it holds (blocks, times their size, before and after the slow push), how far
	 * round to the side (degrees), how high over the top of their head the middle of a speech bubble sits (blocks; Kingdoms' bubbles
	 * float over the name tag spot), and how far below the words the camera ends (blocks, so it looks a touch up at them).
	 */
	static final float SPEAKER_IN = 0.3F, SPEAKER_RISE = 0.62F;
	static final double SPEAKER_HOLD = 3.4, SPEAKER_PUSH = 3.0, SPEAKER_TURN = 18.0, SPEAKER_WORDS_UP = 1.0, SPEAKER_BELOW_WORDS = 0.15;
	/** The vanilla HUD hidden while your own film runs. */
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	private static @Nullable KeyMapping skipKey;
	/** Your own film (camera and HUD), or someone else's near you (bars only). */
	private static boolean running, witness;
	/** (0.18.106) Your own film plays after you died, before the death screen (it waits, {@code DeathScreenFilmMixin}). */
	private static boolean afterDeath;
	private static @Nullable CameraType viewBefore;
	private static int shot, length, age, target = -1, hurtBefore;
	/** (0.20.9) {@link UltimateFilm#MORPH}: whose body changes, the parts in order, whether it is the long first-time cut. */
	private static int casterId = -1;
	private static java.util.List<String> parts = java.util.List.of();
	private static boolean first;
	/** Whether attack or use was held last tick: only a fresh press skips (one held through the cast doesn't). */
	private static boolean handsBefore;
	private static float side = 1.0F, bars, barsO;
	private static Vec3 at = Vec3.ZERO, facing = new Vec3(0.0, 0.0, 1.0);

	private UltimateCinematic() {
	}

	static void init() {
		skipKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.skip_cinematic", InputConstants.KEY_BACKSPACE,
			JaymosPowersClient.CATEGORY));
		ClientPlayNetworking.registerGlobalReceiver(UltimateFilm.Play.TYPE, (payload, context) -> start(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		for (Identifier id : HIDDEN) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (!running || witness) {
					old.extractRenderState(graphics, delta);
				} else {
					// (HUD rework) and every zoned piece of ours with it (the badge, chips, bars, docks)
					com.jaymospowers.client.hud.HudZones.hideFor(100L);
				}
			});
		}
		HudElementRegistry.addLast(JaymosPowers.id("ultimate_cinematic"), (HudElement)UltimateCinematic::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client));
	}

	/** Whether your own film is running (its camera and the cleared HUD). */
	public static boolean filming() {
		return running && !witness;
	}

	/** (0.18.106) Your own film is playing after your death: the death screen waits. */
	public static boolean deathFilm() {
		return running && !witness && afterDeath;
	}

	private static void start(Minecraft minecraft, UltimateFilm.Play play) {
		LocalPlayer self = minecraft.player;
		if (play.shot() == UltimateFilm.MORPH) {
			// (0.20.9) everyone's world shows the body changing, film or not
			XenoMorph.film(minecraft, play.caster(), play.ticks(), play.parts(), play.first());
			if (play.target() == UltimateFilm.MORPH_ONLY) {
				return;
			}
		}
		if (self == null || BeamCinematic.running() || filming() && (play.witness() || play.shot() == UltimateFilm.MORPH && play.caster() != self.getId())) {
			// a beam battle keeps its own film, and your own isn't cut short by someone else's
			return;
		}
		if (!play.witness() && !filming()) {
			viewBefore = minecraft.options.getCameraType();
		}
		running = true;
		witness = play.witness();
		afterDeath = play.afterDeath() && !play.witness();
		shot = play.shot();
		length = play.ticks();
		age = 0;
		target = play.target();
		casterId = play.caster();
		parts = play.parts();
		first = play.first();
		at = play.at();
		side = self.getRandom().nextBoolean() ? 1.0F : -1.0F;
		Vec3 look = Vec3.directionFromRotation(0.0F, self.getYRot());
		facing = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		hurtBefore = self.hurtTime;
		handsBefore = true;
		// presses from before it started don't skip it
		while (skipKey != null && skipKey.consumeClick()) {
		}
	}

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		LocalPlayer self = minecraft.player;
		if (running) {
			boolean skipped = false;
			while (skipKey != null && skipKey.consumeClick()) {
				skipped = true;
			}
			if (!witness && self != null) {
				// acting, or being hit, takes the view back at once
				boolean hands = minecraft.options.keyAttack.isDown() || minecraft.options.keyUse.isDown();
				// (0.18.106) after your death, only a fresh press (the death blow's hurt doesn't count)
				skipped |= hands && !handsBefore || !afterDeath && self.hurtTime > hurtBefore;
				handsBefore = hands;
				hurtBefore = self.hurtTime;
			}
			// (0.18.106) one after your death plays on while you're dead, and ends once you're back
			boolean gone = afterDeath ? age > 20 && self != null && self.isAlive() : self == null || !self.isAlive();
			if (self == null || gone || skipped || ++age >= length || !witness && BeamCinematic.running()) {
				stop(minecraft);
			}
		}
		if (running && !witness && minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			// the film needs the body drawn and no hand in view
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		bars += ((running ? 1.0F : 0.0F) - bars) * BAR_EASE;
		if (!running && bars < 0.01F) {
			bars = 0.0F;
		}
	}

	private static void stop(Minecraft minecraft) {
		if (running && !witness && viewBefore != null) {
			minecraft.options.setCameraType(viewBefore);
		}
		running = false;
		witness = false;
		afterDeath = false;
		viewBefore = null;
	}

	/**
	 * Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch, roll}; roll in degrees, 0 for most shots),
	 * or null when your film isn't running.
	 */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (!filming() || self == null || minecraft.getCameraEntity() != self) {
			return null;
		}
		float t = Mth.clamp((age + partialTicks) / length, 0.0F, 1.0F);
		float k = smooth(t);
		Vec3 eyes = self.getEyePosition(partialTicks);
		Vec3 feet = self.getPosition(partialTicks);
		Vec3 across = new Vec3(facing.z, 0.0, -facing.x).scale(side);
		Entity subject = target >= 0 ? self.level().getEntity(target) : null;
		Vec3 pos;
		Vec3 look;
		float roll = 0.0F;
		switch (shot) {
			case UltimateFilm.ZOOM -> {
				// out slowly and faster, then in faster still, back into your own eyes
				Vec3 ahead = Vec3.directionFromRotation(self.getViewXRot(partialTicks), self.getViewYRot(partialTicks));
				double d;
				if (t < ZOOM_OUT_SHARE) {
					float u = t / ZOOM_OUT_SHARE;
					d = ZOOM_OUT * u * u;
				} else {
					float v = (t - ZOOM_OUT_SHARE) / (1.0F - ZOOM_OUT_SHARE);
					d = ZOOM_OUT * (1.0 - v * v);
				}
				pos = eyes.subtract(ahead.scale(d)).add(0.0, d * 0.2, 0.0);
				look = eyes.add(ahead.scale(4.0));
				if (d < 0.6) {
					// close in, look the way you look (or the body fills the screen)
					return aimed(clipped(self, eyes, pos), look, 0.0F);
				}
				look = eyes;
			}
			case UltimateFilm.SKYWARD -> {
				Vec3 toward = feet.subtract(at).horizontal();
				toward = toward.lengthSqr() < 1.0E-4 ? facing.reverse() : toward.normalize();
				pos = at.add(toward.scale(SKY_BACK + k)).add(across.scale(0.8)).add(0.0, 1.2 - 0.9 * k, 0.0);
				look = at.add(0.0, Mth.lerp(k, 1.2, SKY_LOOK_UP), 0.0);
			}
			case UltimateFilm.CRANE -> {
				double angle = Math.atan2(-facing.z, -facing.x) + Math.toRadians(side * (40.0 + CRANE_TURN * k));
				double out = Mth.lerp(k, CRANE_FROM, CRANE_TO);
				pos = feet.add(Math.cos(angle) * out, Mth.lerp(k, CRANE_LOW, CRANE_HIGH), Math.sin(angle) * out);
				look = feet.add(0.0, 1.0, 0.0);
			}
			case UltimateFilm.ORBIT -> {
				// from the front, a little to one side, sweeping round
				double angle = Math.atan2(facing.z, facing.x) + Math.toRadians(side * (40.0 + ORBIT_TURN * k));
				pos = feet.add(Math.cos(angle) * ORBIT_OUT, self.getBbHeight() * 0.35 + 0.5 * k, Math.sin(angle) * ORBIT_OUT);
				look = feet.add(0.0, self.getBbHeight() * 0.7, 0.0);
			}
			case UltimateFilm.PROFILE -> {
				Vec3 line = at.subtract(eyes);
				double reach = Math.max(2.0, line.length());
				Vec3 axis = line.lengthSqr() < 1.0E-4 ? facing : line.normalize();
				Vec3 flat = axis.horizontal().lengthSqr() < 1.0E-4 ? facing : axis.horizontal().normalize();
				Vec3 sideways = new Vec3(flat.z, 0.0, -flat.x).scale(side);
				pos = eyes.add(sideways.scale(PROFILE_SIDE)).add(axis.scale(reach * (0.15 + 0.25 * k))).add(0.0, 0.8, 0.0);
				look = eyes.add(axis.scale(reach * (0.35 + 0.3 * k)));
			}
			case UltimateFilm.TRACK -> {
				pos = eyes.subtract(facing.scale(3.2)).add(across.scale(1.0)).add(0.0, -0.4, 0.0);
				look = subject != null ? subject.getPosition(partialTicks) : at;
			}
			case UltimateFilm.PUSH_IN -> {
				Vec3 on = subject != null ? subject.getPosition(partialTicks).add(0.0, subject.getBbHeight() * 0.6, 0.0) : at;
				Vec3 toward = on.subtract(eyes).horizontal();
				toward = toward.lengthSqr() < 1.0E-4 ? facing : toward.normalize();
				Vec3 shoulder = new Vec3(toward.z, 0.0, -toward.x).scale(side * 0.8);
				Vec3 from = eyes.subtract(toward.scale(2.5)).add(shoulder).add(0.0, 0.4, 0.0);
				pos = from.lerp(on, PUSH_SHARE * k);
				look = on;
			}
			case UltimateFilm.CHASE -> {
				Vec3 ahead = subject != null ? subject.getPosition(partialTicks) : at;
				Vec3 toward = ahead.subtract(feet).horizontal();
				toward = toward.lengthSqr() < 1.0E-4 ? facing : toward.normalize();
				Vec3 sideways = new Vec3(toward.z, 0.0, -toward.x).scale(side);
				pos = feet.add(sideways.scale(CHASE_SIDE)).subtract(toward.scale(1.0)).add(0.0, CHASE_UP, 0.0);
				look = feet.add(toward.scale(2.0)).add(0.0, 1.0, 0.0);
			}
			case UltimateFilm.DUTCH -> {
				// (0.18.46) a low, close shot at an angle: it leans in on the subject while the horizon tilts and settles back
				Vec3 on = subject != null ? subject.getPosition(partialTicks).add(0.0, subject.getBbHeight() * 0.55, 0.0) : at.add(0.0, 1.0, 0.0);
				Vec3 toward = on.subtract(eyes).horizontal();
				toward = toward.lengthSqr() < 1.0E-4 ? facing : toward.normalize();
				Vec3 sideways = new Vec3(toward.z, 0.0, -toward.x).scale(side);
				double out = Mth.lerp(k, DUTCH_FROM, DUTCH_TO);
				pos = on.subtract(toward.scale(out)).add(sideways.scale(out * 0.45)).add(0.0, DUTCH_LOW - 0.4, 0.0);
				look = on;
				// in fast, a hold, and out again
				roll = side * DUTCH_ROLL * Mth.clamp(Mth.sin(t * Mth.PI) * 2.5F, 0.0F, 1.0F);
			}
			case UltimateFilm.CUTS -> {
				// (0.18.46) hard cuts between angles round the subject: close and low, wide from the side (tilted), high behind
				Vec3 on = subject != null ? subject.getPosition(partialTicks).add(0.0, subject.getBbHeight() * 0.55, 0.0) : at.add(0.0, 1.0, 0.0);
				Vec3 toward = on.subtract(eyes).horizontal();
				toward = toward.lengthSqr() < 1.0E-4 ? facing : toward.normalize();
				Vec3 sideways = new Vec3(toward.z, 0.0, -toward.x).scale(side);
				int angle = Math.min(CUT_ANGLES - 1, (int)(t * CUT_ANGLES));
				// (the time inside this angle, so each one drifts a little but never blends into the next)
				float drift = t * CUT_ANGLES - angle;
				switch (angle) {
					case 0 -> pos = on.subtract(toward.scale(CUT_CLOSE - 0.5 * drift)).add(sideways.scale(0.5)).add(0.0, -0.5, 0.0);
					case 1 -> {
						pos = on.add(sideways.scale(CUT_WIDE - 0.8 * drift)).add(0.0, 0.3, 0.0);
						roll = -side * CUT_ROLL;
					}
					default -> pos = on.subtract(toward.scale(-CUT_CLOSE * 1.2)).add(sideways.scale(-1.0)).add(0.0, CUT_HIGH - 0.8 * drift, 0.0);
				}
				look = on;
			}
			case UltimateFilm.MORPH -> {
				return morph(self, partialTicks, t);
			}
			case UltimateFilm.SPEAKER -> {
				return speaker(self, subject, partialTicks, t, eyes);
			}
			default -> {
				return null;
			}
		}
		return aimed(clipped(self, look, lowered(self, pos, feet)), look, roll);
	}

	/**
	 * (0.20.93) {@link UltimateFilm#SPEAKER}: Andy: "pan into the text box and character, sacrificing the view of the character near
	 * the end to get high enough to view the whole chat bubble". In from over your shoulder to the speaker's face, seen from your side
	 * and a little round; a slow push while they talk, framed between their face and the words over them; then up, level with the
	 * words, so the whole bubble is in the frame and their face drops out of the bottom. Not brought down as other shots are.
	 */
	private static double[] speaker(LocalPlayer self, @Nullable Entity subject, float partialTicks, float t, Vec3 eyes) {
		double size = subject instanceof net.minecraft.world.entity.LivingEntity l ? Math.max(1.0, l.getScale()) : 1.0;
		Vec3 face = subject != null ? subject.getEyePosition(partialTicks) : at;
		// the middle of the words: over the top of their head (the bubble's own height above the name tag spot)
		double crown = subject != null ? subject.getBbHeight() - subject.getEyeHeight() : 0.3;
		Vec3 words = face.add(0.0, crown + SPEAKER_WORDS_UP, 0.0);
		Vec3 toward = eyes.subtract(face).horizontal();
		toward = toward.lengthSqr() < 1.0E-4 ? facing.reverse() : toward.normalize();
		double turn = Math.toRadians(side * SPEAKER_TURN);
		Vec3 out = new Vec3(toward.x * Math.cos(turn) - toward.z * Math.sin(turn), 0.0, toward.x * Math.sin(turn) + toward.z * Math.cos(turn));
		// framed between the face and the words while they talk
		Vec3 between = face.lerp(words, 0.45);
		Vec3 pos;
		Vec3 look;
		if (t < SPEAKER_IN) {
			// from over your shoulder, in to them
			float k = smooth(t / SPEAKER_IN);
			Vec3 shoulder = eyes.add(new Vec3(-toward.z, 0.0, toward.x).scale(side * 0.8)).add(toward.scale(1.5)).add(0.0, 0.4, 0.0);
			Vec3 held = between.add(out.scale(SPEAKER_HOLD * size)).add(0.0, -0.1, 0.0);
			pos = shoulder.lerp(held, k);
			look = face.lerp(between, k);
		} else if (t < SPEAKER_RISE) {
			// the slow push while they talk
			float k = smooth((t - SPEAKER_IN) / (SPEAKER_RISE - SPEAKER_IN));
			pos = between.add(out.scale(Mth.lerp(k, SPEAKER_HOLD, SPEAKER_PUSH) * size)).add(0.0, -0.1, 0.0);
			look = between;
		} else {
			// up level with the words: the whole bubble in frame, their face going out of the bottom
			float k = smooth((t - SPEAKER_RISE) / (1.0F - SPEAKER_RISE));
			Vec3 from = between.add(out.scale(SPEAKER_PUSH * size)).add(0.0, -0.1, 0.0);
			Vec3 to = words.add(out.scale(SPEAKER_PUSH * size)).add(0.0, -SPEAKER_BELOW_WORDS, 0.0);
			pos = from.lerp(to, k);
			look = between.lerp(words, k);
		}
		return aimed(clipped(self, look, pos), look, 0.0F);
	}

	/**
	 * (0.20.60) A shot brought down {@link #SHOT_DROP} (times the caster's size), so the caster is seen better (Andy: "all the cutscenes
	 * should be a little lower. you cant see the user well"); never below {@link #SHOT_FLOOR} over their feet unless it was already.
	 */
	static Vec3 lowered(Entity self, Vec3 pos, Vec3 feet) {
		double size = Math.max(1.0, self instanceof net.minecraft.world.entity.LivingEntity l ? l.getScale() : 1.0);
		double floor = Math.min(pos.y, feet.y + SHOT_FLOOR * size);
		return new Vec3(pos.x, Math.max(floor, pos.y - SHOT_DROP * size), pos.z);
	}

	/**
	 * (0.20.9) {@link UltimateFilm#MORPH}: wide on the whole body, then in turn on each changing part (a pleasing angle, alternating
	 * sides, zooming in while it grows, holding with a slow push), then a pull back to frame the whole new body. The beats are
	 * {@link XenoMorph#windowStart}'s, the same the body grows by. First time: wider start, closer zooms, a shake on the last beat.
	 * Whoever's body it is (a witness's film is the caster's), the camera is the viewer's own.
	 */
	private static double @Nullable [] morph(LocalPlayer self, float partialTicks, float t) {
		Entity who = casterId == self.getId() ? self : self.level().getEntity(casterId);
		if (who == null) {
			who = self;
		}
		net.minecraft.world.entity.LivingEntity living = who instanceof net.minecraft.world.entity.LivingEntity l ? l : null;
		float yaw = living != null ? Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot) : who.getYRot();
		double scale = living != null ? living.getScale() : 1.0;
		Vec3 feet = who.getPosition(partialTicks);
		double h = Math.max(1.0, who.getBbHeight());
		Vec3 whole = feet.add(0.0, h * 0.55, 0.0);
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		String form = who instanceof net.minecraft.world.entity.player.Player p ? p.getAttached(com.jaymospowers.power.xenoform.XenoformState.FORM) : null;
		com.jaymospowers.client.power.xenoform.XenoFormModels.FormModel model = com.jaymospowers.client.power.xenoform.XenoFormModels.ultimate(form);
		com.jaymospowers.client.power.xenoform.XenoFormModels.UltimateModel ult = model instanceof
			com.jaymospowers.client.power.xenoform.XenoFormModels.UltimateModel u ? u : null;
		int n = parts.size();
		float zoom = first ? MORPH_ZOOM_FIRST : MORPH_ZOOM;
		double wide = (first ? MORPH_WIDE_FIRST : MORPH_WIDE) * h;
		double close = Math.max(MORPH_MIN_CLOSE, (first ? MORPH_CLOSE_FIRST : MORPH_CLOSE) * h);
		double frame = (first ? MORPH_FRAME_FIRST : MORPH_FRAME) * h;
		float tail = 1.0F - XenoMorph.OUTRO;
		Vec3[] from;
		float roll = 0.0F;
		if (t < XenoMorph.INTRO && n > 0 || n == 0 && t < tail) {
			// the establishing shot: the whole body from a little to one side, easing in
			float u = n == 0 ? t / tail : t / XenoMorph.INTRO;
			from = shot(whole, forward, side * 35.0, wide * (1.0 - 0.12 * smooth(u)), h * MORPH_LIFT);
		} else if (t < tail) {
			float slot = XenoMorph.slot(n);
			int j = Math.min(n - 1, (int)((t - XenoMorph.INTRO) / slot));
			float u = Mth.clamp((t - XenoMorph.windowStart(j, n)) / slot, 0.0F, 1.0F);
			from = part(ult, parts.get(j), j, u, feet, yaw, scale, whole, forward, h, wide, close, zoom);
			if (u < MORPH_BLEND) {
				// carry on from where the last beat left the camera
				Vec3[] before = j == 0 ? shot(whole, forward, side * 35.0, wide * 0.88, h * MORPH_LIFT)
					: part(ult, parts.get(j - 1), j - 1, 1.0F, feet, yaw, scale, whole, forward, h, wide, close, zoom);
				float k = smooth(u / MORPH_BLEND);
				from = new Vec3[] {before[0].lerp(from[0], k), before[1].lerp(from[1], k)};
			}
		} else {
			// the pull back to the whole body
			float v = (t - tail) / XenoMorph.OUTRO;
			Vec3[] before = n == 0 ? shot(whole, forward, side * 35.0, wide * 0.88, h * MORPH_LIFT)
				: part(ult, parts.get(n - 1), n - 1, 1.0F, feet, yaw, scale, whole, forward, h, wide, close, zoom);
			Vec3[] end = shot(whole, forward, side * 12.0, frame, h * MORPH_LIFT * 1.5);
			float k = smooth(v);
			from = new Vec3[] {before[0].lerp(end[0], k), before[1].lerp(end[1], k)};
			if (first) {
				// the last beat lands with a shake and a tilt that settle
				float shake = Mth.sin(v * Mth.PI) * (1.0F - v);
				double time = (age + partialTicks) * 1.7;
				from[0] = from[0].add(Math.sin(time * 2.3) * MORPH_SHAKE * h * shake, Math.sin(time * 3.1) * MORPH_SHAKE * h * shake, 0.0);
				roll = (float)Math.sin(time * 1.9) * MORPH_ROLL_FIRST * shake;
			}
		}
		return aimed(clipped(self, from[1], from[0]), from[1], roll);
	}

	/** The camera on part {@code name} (beat {@code j}, {@code u} of the way through it): {position, look}. */
	private static Vec3[] part(com.jaymospowers.client.power.xenoform.XenoFormModels.@Nullable UltimateModel model, String name, int j, float u, Vec3 feet,
		float yaw, double scale, Vec3 whole, Vec3 forward, double h, double wide, double close, float zoom) {
		Vec3 focus = whole;
		float[] c = model != null ? model.centre(name) : null;
		if (c != null) {
			// the model's frame (right, up, back) turned the way the body faces, at the body's size
			double a = Math.toRadians(180.0 - yaw);
			focus = feet.add((c[0] * Math.cos(a) + c[2] * Math.sin(a)) * scale, c[1] * scale, (-c[0] * Math.sin(a) + c[2] * Math.cos(a)) * scale);
		}
		float k = smooth(Math.min(1.0F, u / zoom));
		double hold = (first ? MORPH_PUSH_FIRST : MORPH_PUSH) * Math.max(0.0F, u - zoom) / (1.0F - zoom);
		double dist = Mth.lerp(k, wide, close) * (1.0 - hold);
		double angle = side * (j % 2 == 0 ? 1.0 : -1.0) * (28.0 + 12.0 * (j % 3));
		return shot(focus, forward, angle, dist, h * MORPH_LIFT);
	}

	/** A camera {@code dist} from {@code focus}, out in front of the body turned {@code degrees} to one side, lifted: {position, look}. */
	private static Vec3[] shot(Vec3 focus, Vec3 forward, double degrees, double dist, double lift) {
		double angle = Math.atan2(forward.z, forward.x) + Math.toRadians(degrees);
		return new Vec3[] {focus.add(Math.cos(angle) * dist, lift, Math.sin(angle) * dist), focus};
	}

	private static double[] aimed(Vec3 pos, Vec3 look, float roll) {
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? facing : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch, roll};
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** {@code want}, or short of it where a wall stands between it and what it's looking at. */
	private static Vec3 clipped(LocalPlayer self, Vec3 from, Vec3 want) {
		HitResult hit = self.level().clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		Vec3 back = hit.getLocation().subtract(from);
		double length = back.length();
		return length > WALL_MARGIN ? from.add(back.scale((length - WALL_MARGIN) / length)) : from;
	}

	/** The bars. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float shown = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), barsO, bars);
		if (shown <= 0.0F) {
			return;
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int bar = Math.round(height * BAR_SHARE * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
	}
}
