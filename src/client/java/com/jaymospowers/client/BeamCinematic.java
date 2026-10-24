package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BeamClash;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.143) A beam battle ({@link BeamClash}) as a film, for the two in it: black bars slide down and up over the
 * top and bottom of the screen, the HUD clears away (the clash's own panel moves into the bottom bar), and the camera
 * cuts every few seconds between shots of the struggle: a wide profile drifting along the beams, a slow orbit round the
 * knot, over each fighter's shoulder at the other, low under the knot, and a crane high above. The idea is Ji AFK
 * Cinematic's AFK camera (Ji Mods Source License: no copies of its code, so this is our own). Your aim still follows
 * the mouse, so looking away still lets a ki wave go. A moment after it ends, to see who's thrown, the view comes back.
 */
public final class BeamCinematic {
	/** Each shot holds this many ticks, give or take {@link #SHOT_SPREAD}. */
	static final int SHOT_TICKS = 70, SHOT_SPREAD = 20;
	/** The view stays this long (ticks) after the clash ends, to see how it ends. */
	static final int LINGER = 30;
	/** Each bar's height, as a share of the screen's; how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.12F, BAR_EASE = 0.2F;
	/** Kept this far off a wall the camera would otherwise sink into (blocks). */
	static final double WALL_MARGIN = 0.3;
	/** The shots. */
	static final int PROFILE = 0, ORBIT = 1, MY_SHOULDER = 2, THEIR_SHOULDER = 3, LOW = 4, CRANE = 5, SHOTS = 6;
	/** The vanilla HUD hidden while it runs (chat and the rest stay). */
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	private static boolean running;
	private static @Nullable CameraType viewBefore;
	private static int linger;
	private static int shot = -1, shotTicks, shotLength;
	private static float side = 1.0F, turn;
	private static float bars, barsO;
	/** The last clash seen: the knot and the other fighter, for the linger. */
	private static Vec3 knot = Vec3.ZERO, otherEyes = Vec3.ZERO;
	private static int other = -1;

	private BeamCinematic() {
	}

	static void init() {
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
		HudElementRegistry.addLast(JaymosPowers.id("beam_cinematic"), (HudElement) BeamCinematic::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client));
	}

	/** Whether the film is running (its camera, its bars and the cleared HUD). */
	public static boolean running() {
		return running;
	}

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		LocalPlayer self = minecraft.player;
		BeamClash.State state = self == null || !self.isAlive() ? null : self.getAttached(BeamClash.CLASH);
		if (state != null) {
			knot = state.knot();
			other = state.opponent();
			linger = LINGER;
			if (!running) {
				running = true;
				viewBefore = minecraft.options.getCameraType();
				nextShot(self.getRandom());
			}
		} else if (running && (self == null || --linger <= 0)) {
			stop(minecraft);
		}
		if (running) {
			// the film needs the body drawn and no hand in view
			if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			Entity them = self == null ? null : self.level().getEntity(other);
			if (them != null) {
				otherEyes = them.getEyePosition();
			}
			if (++shotTicks >= shotLength && self != null) {
				nextShot(self.getRandom());
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
		shot = -1;
	}

	/** A different shot from the last, held a while, from a side picked at random. */
	private static void nextShot(RandomSource random) {
		int next = random.nextInt(SHOTS - 1);
		shot = shot >= 0 && next >= shot ? next + 1 : next;
		shotTicks = 0;
		shotLength = SHOT_TICKS - SHOT_SPREAD + random.nextInt(SHOT_SPREAD * 2 + 1);
		side = random.nextBoolean() ? 1.0F : -1.0F;
		turn = random.nextFloat() * 360.0F;
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when the film isn't running. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (!running || self == null || minecraft.getCameraEntity() != self) {
			return null;
		}
		Vec3 me = self.getEyePosition(partialTicks);
		Entity them = self.level().getEntity(other);
		Vec3 you = them != null ? them.getEyePosition(partialTicks) : otherEyes;
		Vec3 flat = new Vec3(you.x - me.x, 0.0, you.z - me.z);
		double apart = Math.max(flat.length(), 1.0);
		Vec3 axis = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : flat.normalize();
		Vec3 across = new Vec3(axis.z, 0.0, -axis.x).scale(side);
		// the middle of the fight, pulled toward the knot so the struggle sits mid-frame
		Vec3 focus = me.add(you).scale(0.5).lerp(knot, 0.5);
		float t = Mth.clamp((shotTicks + partialTicks) / shotLength, 0.0F, 1.0F);
		float eased = t * t * (3.0F - 2.0F * t);
		double wide = Math.max(6.0, apart * 0.75 + 4.0);
		Vec3 pos;
		Vec3 look;
		switch (shot) {
			case ORBIT -> {
				double angle = Math.toRadians(turn + eased * 70.0F * side);
				double radius = Math.max(6.0, apart * 0.7 + 3.0);
				pos = focus.add(Math.cos(angle) * radius, 2.5 + 1.5 * eased, Math.sin(angle) * radius);
				look = focus;
			}
			case MY_SHOULDER -> {
				pos = me.subtract(axis.scale(3.0 + eased * 0.8)).add(across.scale(1.1)).add(0.0, 0.5, 0.0);
				look = knot.lerp(you, 0.4);
			}
			case THEIR_SHOULDER -> {
				pos = you.add(axis.scale(3.0 + eased * 0.8)).add(across.scale(1.1)).add(0.0, 0.5, 0.0);
				look = knot.lerp(me, 0.4);
			}
			case LOW -> {
				pos = knot.add(across.scale(3.5 + eased)).add(axis.scale((eased - 0.5) * 2.0)).add(0.0, -1.2, 0.0);
				look = knot.add(0.0, 0.6, 0.0);
			}
			case CRANE -> {
				pos = focus.add(across.scale(4.0)).add(0.0, 9.0 - 3.0 * eased, 0.0);
				look = focus;
			}
			default -> {
				// a wide profile, drifting along the beams
				pos = focus.add(across.scale(wide)).add(axis.scale((eased - 0.5) * apart * 0.5)).add(0.0, 1.5, 0.0);
				look = focus;
			}
		}
		pos = clipped(self, look, pos);
		Vec3 dir = look.subtract(pos);
		if (dir.lengthSqr() < 1.0E-6) {
			dir = axis;
		}
		dir = dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
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

	/** The bars, and the clash's panel set in the bottom one. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float shown = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), barsO, bars);
		if (shown <= 0.0F) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int bar = Math.round(height * BAR_SHARE * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
		if (running) {
			BeamClashClient.panel(graphics, delta, width / 2 - BeamClashClient.PANEL_W / 2, height - bar + Math.max(1, (bar - BeamClashClient.PANEL_H) / 2));
		}
	}
}
