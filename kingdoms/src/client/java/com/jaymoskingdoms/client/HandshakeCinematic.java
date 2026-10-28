package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Handshakes;
import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) Your own handshake ({@link Handshakes}) as a short film: black bars slide in, the HUD clears, and the camera
 * cuts through three shots: a close side shot with the hands dead centre as they reach and come together and the first
 * pumps, a wide profile of the two of you drawing back from it, then over your shoulder onto their face as it ends.
 * You're turned to face them throughout. (0.7.205) Andy: "One of the frames the cutscene puts front and center NEEDS to
 * be the hands actually coming together": the hands' shot opens the film, so the hands meet in it ({@link
 * Handshakes#GRIP_TICK}), framed on where they meet. Like Jaymo's Powers' beam battle film (our own code, no one else's).
 * (0.7.288) A wave at a first meeting is filmed too, all in the wide shot, so you see yourself wave and them wave back.
 */
public final class HandshakeCinematic {
	/** The shots' cuts (ticks into the shake): the hands until {@link #WIDE_CUT}, wide until {@link #FACE_CUT}, then over the shoulder. */
	static final int WIDE_CUT = 24, FACE_CUT = 34;
	/** The view stays this long (ticks) after the hands let go, then comes back. */
	static final int LINGER = 8;
	/** Each bar's height, as a share of the screen's; how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.11F, BAR_EASE = 0.25F;
	/** Kept this far off a wall the camera would otherwise sink into (blocks). */
	static final double WALL_MARGIN = 0.3;
	/** The wide shot: how far out to the side it starts and ends (blocks), and how high over the hands. */
	static final double WIDE_FROM = 2.6, WIDE_TO = 3.8, WIDE_UP = 0.5;
	/** The hands' shot: how far out to the side (blocks), easing in to {@link #HANDS_IN}, how low under them, how far it drifts round (degrees). */
	static final double HANDS_OUT = 1.6, HANDS_IN = 1.25, HANDS_LOW = 0.1, HANDS_TURN = 12.0;
	/**
	 * Where the hands meet, over the feet (blocks): the shoulder's height, less the arm's drop as {@code HandshakePose}
	 * holds it out.
	 */
	static final double HANDS_Y = 1.15;
	/** The vanilla HUD hidden while it runs. */
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	private static boolean running, waving;
	private static @Nullable CameraType viewBefore;
	private static int linger, partner = -1;
	private static float side = 1.0F, bars, barsO;
	/** When the shake began (client game time), and where the partner's feet were last seen. */
	private static long since;
	private static Vec3 partnerAt = Vec3.ZERO;

	private HandshakeCinematic() {
	}

	static void register() {
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
		HudElementRegistry.addLast(JaymosKingdoms.id("handshake_cinematic"), (HudElement)HandshakeCinematic::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client));
	}

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		LocalPlayer self = minecraft.player;
		HandshakePose.Hand hand = self == null ? null : HandshakePose.hand(self.getId());
		if (hand != null && (hand.state() == Handshakes.Hand.SHAKING || hand.state() == Handshakes.Hand.WAVE)) {
			waving = hand.state() == Handshakes.Hand.WAVE;
			if (!running) {
				running = true;
				viewBefore = minecraft.options.getCameraType();
				side = self.getRandom().nextBoolean() ? 1.0F : -1.0F;
			}
			partner = hand.partner();
			since = hand.since();
			linger = LINGER;
		} else if (running && (self == null || hand != null || --linger <= 0)) {
			// (a hand held out again, or none: let go, or broken off)
			stop(minecraft);
		}
		if (running && self != null) {
			if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			Entity them = self.level().getEntity(partner);
			if (them != null) {
				partnerAt = them.position();
				// face them
				Vec3 to = them.getEyePosition().subtract(self.getEyePosition());
				float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
				self.setYRot(yaw);
				self.setYHeadRot(yaw);
				self.setXRot((float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
			}
		}
		bars += ((running ? 1.0F : 0.0F) - bars) * BAR_EASE;
		if (!running && bars < 0.01F) {
			bars = 0.0F;
		}
	}

	private static void stop(Minecraft minecraft) {
		if (running && viewBefore != null) {
			minecraft.options.setCameraType(viewBefore);
		}
		running = false;
		viewBefore = null;
		partner = -1;
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when the film isn't running. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (!running || self == null || minecraft.getCameraEntity() != self || minecraft.level == null) {
			return null;
		}
		Entity them = self.level().getEntity(partner);
		Vec3 me = self.getPosition(partialTicks);
		Vec3 you = them != null ? them.getPosition(partialTicks) : partnerAt;
		Vec3 flat = new Vec3(you.x - me.x, 0.0, you.z - me.z);
		Vec3 axis = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : flat.normalize();
		Vec3 across = new Vec3(axis.z, 0.0, -axis.x).scale(side);
		Vec3 hands = me.add(you).scale(0.5).add(0.0, HANDS_Y, 0.0);
		float t = minecraft.level.getGameTime() - since + partialTicks;
		Vec3 pos;
		Vec3 look;
		if (waving) {
			// (0.7.288) wide, drawing back a little while you wave and they wave back
			float k = smooth(t / (Handshakes.WAVE_BACK + Handshakes.WAVE_TICKS));
			pos = hands.add(across.scale(Mth.lerp(k, WIDE_FROM, WIDE_TO))).add(0.0, WIDE_UP, 0.0);
			look = hands.add(0.0, 0.25, 0.0);
		} else if (t < WIDE_CUT) {
			// side on and a touch low, the hands dead centre: one reaching in to the other, meeting, the first pumps
			float k = smooth(t / WIDE_CUT);
			double angle = Math.toRadians((k - 0.5) * HANDS_TURN);
			Vec3 out = across.scale(Math.cos(angle)).add(axis.scale(Math.sin(angle)));
			pos = hands.add(out.scale(Mth.lerp(k, HANDS_OUT, HANDS_IN))).add(0.0, -HANDS_LOW, 0.0);
			look = hands;
		} else if (t < FACE_CUT) {
			// wide, drawing back to take in the two of them
			float k = smooth((t - WIDE_CUT) / (FACE_CUT - WIDE_CUT));
			pos = hands.add(across.scale(Mth.lerp(k, WIDE_FROM, WIDE_TO))).add(0.0, WIDE_UP, 0.0);
			look = hands.add(0.0, 0.25, 0.0);
		} else {
			// over your shoulder onto their face, drifting in
			float k = smooth(Mth.clamp((t - FACE_CUT) / (Handshakes.SHAKE_TICKS - FACE_CUT), 0.0F, 1.0F));
			Vec3 eyes = self.getEyePosition(partialTicks);
			Vec3 shoulder = new Vec3(axis.z, 0.0, -axis.x).scale(-0.55);
			pos = eyes.subtract(axis.scale(1.3 - 0.25 * k)).add(shoulder).add(0.0, 0.15, 0.0);
			look = them != null ? them.getEyePosition(partialTicks) : you.add(0.0, self.getEyeHeight(), 0.0);
		}
		pos = clipped(self, look, pos);
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? axis : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
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
