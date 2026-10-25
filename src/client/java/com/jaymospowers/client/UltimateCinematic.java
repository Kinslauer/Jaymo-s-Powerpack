package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
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
	/** Kept this far off a wall the camera would otherwise sink into (blocks). */
	static final double WALL_MARGIN = 0.3;
	/** {@link UltimateFilm#ZOOM}: how far back it goes (blocks), and the share of the film spent going out. */
	static final double ZOOM_OUT = 9.0;
	static final float ZOOM_OUT_SHARE = 0.6F;
	/** {@link UltimateFilm#SKYWARD}: how far behind (blocks), and how high it ends up looking. */
	static final double SKY_BACK = 4.0, SKY_LOOK_UP = 14.0;
	/** {@link UltimateFilm#CRANE}: its reach out (from, to) and its rise (from, to), and how far it circles (degrees). */
	static final double CRANE_FROM = 5.0, CRANE_TO = 9.0, CRANE_LOW = 1.5, CRANE_HIGH = 9.0, CRANE_TURN = 80.0;
	/** {@link UltimateFilm#ORBIT}: how far out (blocks) and how far it sweeps (degrees). */
	static final double ORBIT_OUT = 2.6, ORBIT_TURN = 100.0;
	/** {@link UltimateFilm#PROFILE}: how far to the side of the line (blocks). */
	static final double PROFILE_SIDE = 5.0;
	/** {@link UltimateFilm#PUSH_IN}: how much of the way to the subject it pushes. */
	static final double PUSH_SHARE = 0.45;
	/** {@link UltimateFilm#CHASE}: how far to the side and up it keeps (blocks). */
	static final double CHASE_SIDE = 4.0, CHASE_UP = 1.2;
	/** The vanilla HUD hidden while your own film runs. */
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	private static @Nullable KeyMapping skipKey;
	/** Your own film (camera and HUD), or someone else's near you (bars only). */
	private static boolean running, witness;
	private static @Nullable CameraType viewBefore;
	private static int shot, length, age, target = -1, hurtBefore;
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

	private static void start(Minecraft minecraft, UltimateFilm.Play play) {
		LocalPlayer self = minecraft.player;
		if (self == null || BeamCinematic.running() || filming() && play.witness()) {
			// a beam battle keeps its own film, and your own isn't cut short by someone else's
			return;
		}
		if (!play.witness() && !filming()) {
			viewBefore = minecraft.options.getCameraType();
		}
		running = true;
		witness = play.witness();
		shot = play.shot();
		length = play.ticks();
		age = 0;
		target = play.target();
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
				skipped |= hands && !handsBefore || self.hurtTime > hurtBefore;
				handsBefore = hands;
				hurtBefore = self.hurtTime;
			}
			if (self == null || !self.isAlive() || skipped || ++age >= length || !witness && BeamCinematic.running()) {
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
		viewBefore = null;
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when your film isn't running. */
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
					return aimed(clipped(self, eyes, pos), look);
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
			default -> {
				return null;
			}
		}
		return aimed(clipped(self, look, pos), look);
	}

	private static double[] aimed(Vec3 pos, Vec3 look) {
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? facing : dir.normalize();
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
