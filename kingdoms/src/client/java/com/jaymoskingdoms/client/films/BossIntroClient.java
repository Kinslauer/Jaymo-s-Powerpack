package com.jaymoskingdoms.client.films;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.films.BossIntros;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A great foe's entrance ({@link BossIntros}) on your own game, in the way of the handshake's and the clash's films:
 * black bars slide in, the HUD and the boss bar clear, the movement keys go dead, and the camera cuts through three shots.
 * <ol>
 * <li><b>The reveal:</b> low, from behind your shoulder, tilting up to it.</li>
 * <li><b>The creature:</b> alone, from near the ground in front of it, pushing slowly in; it roars and the frame shakes
 * (a calm one only calls, and nothing shakes).</li>
 * <li><b>The two of you:</b> side on and wide, you on one third and it on the other.</li>
 * </ol>
 * Andy's rules for our films hold: the camera stays on one side of the line between you and it (the 180 degree rule),
 * subjects sit on the thirds, and the shots are low. Every distance grows with the creature's size. A fresh attack or use
 * press, or holding sneak, skips it; a hit taken, its death, or yours ends it.
 */
public final class BossIntroClient {
	/** Each bar's height as a share of the screen's; how much of the way they slide a tick; how long before the end they go (ticks). */
	static final float BAR_SHARE = 0.12F, BAR_EASE = 0.25F;
	static final int BARS_OUT = 10;
	/** Where the shots change, as shares of the film. */
	static final float REVEAL_END = 0.27F, HERO_END = 0.72F;
	/** The reveal: how far behind you, out to the side and over your feet (blocks), and how far it creeps forward. */
	static final double REVEAL_BACK = 2.4, REVEAL_SIDE = 1.2, REVEAL_UP = 0.7, REVEAL_CREEP = 0.6;
	/** The creature's shot: out in front of it and to the side, in its sizes plus these (blocks); how much nearer it ends; how high over its feet, in its heights. */
	static final double HERO_OUT = 1.7, HERO_OUT_PLUS = 2.5, HERO_SIDE = 0.8, HERO_SIDE_PLUS = 1.0, HERO_PUSH = 0.3, HERO_UP = 0.12;
	/** It never comes nearer the creature than this share of the way from it to you. */
	static final double HERO_MAX_SHARE = 0.7;
	/** How far its frame is turned off the creature, as a share of the camera's distance (puts it on a third). */
	static final double THIRD = 0.14;
	/** The wide shot: out to the side by this share of the gap between you plus these blocks; the widest gap it frames; over your feet. */
	static final double WIDE_SHARE = 0.75, WIDE_PLUS = 3.0, WIDE_MAX_GAP = 24.0, WIDE_UP = 0.6;
	/** The roar's shake: how long (ticks), and how hard at its height (blocks). */
	static final int SHAKE_TICKS = 30;
	static final double SHAKE = 0.07;
	/** Kept this far off a wall the camera would otherwise sink into (blocks). */
	static final double WALL_MARGIN = 0.3;
	/** How long sneak is held to skip (ticks). */
	static final int SKIP_HOLD = 12;
	/** The vanilla HUD hidden while it runs (the boss bar too: it comes up as the film ends). */
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
		VanillaHudElements.BOSS_BAR};

	private static boolean running, calm;
	private static int boss = -1, tick, length, roarAt, sneak, hurtBefore;
	/** Which film this is ({@link BossIntros.Play#INTRO}, a turn or a fall: those two are the creature's own shot throughout). */
	private static int mode;
	private static boolean handsBefore;
	private static @Nullable CameraType viewBefore;
	private static float side = 1.0F, bars, barsO;
	/** Where the creature's feet were last seen, and its size (width, height). */
	private static Vec3 bossAt = Vec3.ZERO;
	private static double wide = 1.0, tall = 2.0;

	private BossIntroClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BossIntros.Play.TYPE, (payload, context) -> start(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		for (Identifier id : HIDDEN) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (!running) {
					old.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.addLast(JaymosKingdoms.id("boss_intro"), (HudElement)BossIntroClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			stop(client, false);
			bars = 0.0F;
			barsO = 0.0F;
		});
	}

	/** Whether the movement keys are dead: the film is on. */
	public static boolean locked() {
		return running;
	}

	private static void start(Minecraft minecraft, BossIntros.Play play) {
		LocalPlayer self = minecraft.player;
		Entity it = self == null || minecraft.level == null ? null : minecraft.level.getEntity(play.entity());
		// another film, a screen, or nothing to film: not now (the server offers it again later)
		if (running || it == null || minecraft.gui.screen() != null || LoreFilmClient.locked()
			|| com.jaymoskingdoms.client.spirits.ClashCinematic.locked() || minecraft.getCameraEntity() != self) {
			if (!(running && boss == play.entity())) {
				ClientPlayNetworking.send(new BossIntros.State(play.entity(), BossIntros.State.REFUSED));
			}
			return;
		}
		running = true;
		calm = play.calm();
		mode = play.mode();
		boss = play.entity();
		tick = 0;
		length = Math.max(20, play.ticks());
		roarAt = play.roarAt();
		sneak = 0;
		handsBefore = true;
		hurtBefore = self.hurtTime;
		viewBefore = minecraft.options.getCameraType();
		side = self.getRandom().nextBoolean() ? 1.0F : -1.0F;
		seen(it);
		ClientPlayNetworking.send(new BossIntros.State(boss, BossIntros.State.BEGAN));
	}

	private static void seen(Entity it) {
		bossAt = it.position();
		wide = Math.max(0.6, it.getBbWidth());
		tall = Math.max(0.9, it.getBbHeight());
	}

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		LocalPlayer self = minecraft.player;
		if (running) {
			Entity it = self == null || minecraft.level == null ? null : minecraft.level.getEntity(boss);
			boolean hands = minecraft.options.keyAttack.isDown() || minecraft.options.keyUse.isDown();
			sneak = minecraft.options.keyShift.isDown() ? sneak + 1 : 0;
			boolean skipped = hands && !handsBefore || sneak >= SKIP_HOLD || self != null && self.hurtTime > hurtBefore;
			handsBefore = hands;
			// (a fall is filmed through the creature's death: the camera stays where it was last seen)
			boolean gone = mode != BossIntros.Play.DEATH && (it == null || it instanceof LivingEntity living && !living.isAlive());
			if (self == null || !self.isAlive() || gone || skipped || ++tick >= length) {
				stop(minecraft, true);
			} else if (it == null) {
				hurtBefore = self.hurtTime;
			} else {
				hurtBefore = self.hurtTime;
				seen(it);
				if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
					minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				}
				// you face it, so the film hands the view back looking at it
				Vec3 to = it.getBoundingBox().getCenter().subtract(self.getEyePosition());
				float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
				float pitch = (float)-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
				self.setYRot(yaw);
				self.setYHeadRot(yaw);
				self.setXRot(Mth.clamp(pitch, -60.0F, 60.0F));
			}
		}
		boolean up = running && tick < length - BARS_OUT;
		bars += ((up ? 1.0F : 0.0F) - bars) * BAR_EASE;
		if (!up && bars < 0.01F) {
			bars = 0.0F;
		}
	}

	private static void stop(Minecraft minecraft, boolean tell) {
		if (!running) {
			return;
		}
		if (viewBefore != null) {
			minecraft.options.setCameraType(viewBefore);
		}
		running = false;
		viewBefore = null;
		if (tell && minecraft.getConnection() != null) {
			ClientPlayNetworking.send(new BossIntros.State(boss, BossIntros.State.ENDED));
		}
		boss = -1;
	}

	private static double smooth(double k) {
		k = Mth.clamp(k, 0.0, 1.0);
		return k * k * (3.0 - 2.0 * k);
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when the film isn't running. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (!running || self == null || minecraft.getCameraEntity() != self || minecraft.level == null) {
			return null;
		}
		Entity it = minecraft.level.getEntity(boss);
		Vec3 me = self.getPosition(partialTicks);
		Vec3 feet = it != null ? it.getPosition(partialTicks) : bossAt;
		Vec3 body = feet.add(0.0, tall * 0.6, 0.0);
		Vec3 flat = new Vec3(feet.x - me.x, 0.0, feet.z - me.z);
		double gap = Math.max(0.5, flat.length());
		Vec3 axis = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : flat.normalize();
		// (one side of the line between you for the whole film: the 180 degree rule)
		Vec3 across = new Vec3(axis.z, 0.0, -axis.x).scale(side);
		double size = Math.max(wide, tall);
		float t = tick + partialTicks;
		// (a turn or a fall is the creature's own shot from first frame to last)
		double k = mode == BossIntros.Play.INTRO ? t / length : REVEAL_END + (HERO_END - REVEAL_END) * Math.min(0.999, t / length);
		Vec3 pos;
		Vec3 look;
		if (k < REVEAL_END) {
			// the reveal: low behind your shoulder, tilting up from your back to it
			double s = smooth(k / REVEAL_END);
			pos = me.add(axis.scale(-REVEAL_BACK + REVEAL_CREEP * s)).add(across.scale(REVEAL_SIDE)).add(0.0, REVEAL_UP, 0.0);
			Vec3 from = me.add(axis.scale(3.0)).add(0.0, 1.2, 0.0);
			look = from.lerp(body, smooth(k / REVEAL_END * 1.6));
			pos = clipped(self, self.getEyePosition(partialTicks), pos);
		} else if (k < HERO_END) {
			// the creature alone: from the ground in front of it, pushing in; it stands on the far third, looking into the frame
			double s = smooth((k - REVEAL_END) / (HERO_END - REVEAL_END));
			double out = Math.min((size * HERO_OUT + HERO_OUT_PLUS) * (1.0 - HERO_PUSH * s), gap * HERO_MAX_SHARE);
			pos = feet.add(axis.scale(-out)).add(across.scale(size * HERO_SIDE + HERO_SIDE_PLUS)).add(0.0, Math.max(0.4, tall * HERO_UP), 0.0);
			Vec3 head = feet.add(0.0, tall * 0.75, 0.0);
			look = head.add(across.scale(pos.distanceTo(head) * THIRD));
			pos = clipped(self, head, pos);
		} else {
			// the two of you: side on and wide, drifting back, each on a third
			double s = smooth((k - HERO_END) / (1.0 - HERO_END));
			Vec3 mid = me.add(feet).scale(0.5);
			double out = Math.min(gap, WIDE_MAX_GAP) * WIDE_SHARE + WIDE_PLUS + size * 0.5 + s * 1.5;
			pos = new Vec3(mid.x, Math.min(me.y, feet.y) + WIDE_UP, mid.z).add(across.scale(out));
			look = mid.add(0.0, Math.max(1.0, tall * 0.4), 0.0);
			pos = clipped(self, look, pos);
		}
		// the roar's shake
		double shake = 0.0;
		float since = t - roarAt;
		if (!calm && since >= 0.0F && since < SHAKE_TICKS) {
			shake = SHAKE * (1.0 - since / SHAKE_TICKS) * Math.min(1.0, since / 3.0);
			pos = pos.add(Math.sin(t * 2.7) * shake, Math.sin(t * 3.9 + 1.0) * shake, Math.cos(t * 3.1) * shake);
		}
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? axis : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)) + Math.sin(t * 4.3) * shake * 20.0;
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0))) + Math.sin(t * 5.1) * shake * 20.0;
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
	}

	/** {@code want}, or short of it where a wall stands between it and what it's looking at. */
	private static Vec3 clipped(LocalPlayer self, Vec3 from, Vec3 want) {
		HitResult hit = self.level().clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		Vec3 back = hit.getLocation().subtract(from);
		double far = back.length();
		return far > WALL_MARGIN ? from.add(back.scale((far - WALL_MARGIN) / far)) : from;
	}

	/** The bars. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float shown = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), barsO, bars);
		if (shown <= 0.0F) {
			return;
		}
		if (running) {
			// the film is up (vanilla HUD hidden): hide the zoned HUD too
			HudZones.hideFor(100);
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int bar = Math.round(height * BAR_SHARE * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
	}
}
