package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.power.zfighter.ZFighterKiGlow;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.LockOnRegions;
import com.jaymospowers.core.LockOnRegions.Region;
import com.jaymospowers.core.wounds.BodyPlan;
import java.util.List;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.45, Andy's NOEA picks) Lock-on, the client half, for everyone (Andy: "make lock on middle mouse, and it should
 * work across the board"). Middle mouse locks onto the living thing nearest the crosshair within {@link LockOn#RANGE}
 * blocks that he can see, and sends its id to the server ({@link LockOn.Packet}); out of sight for a moment, or past the
 * range, it lets go. While it holds the camera eases round to the target ({@link #EASE}), so whatever he aims (a power, a
 * bow, a blow) goes there, and a small bracket is drawn on it.
 *
 * <p>Andy (2026-11-13): "scrolling should pick the seperate regions you can target on the body (head, legs, limbs) and you
 * swap lock on targets now by having them right on your crosshair when you activate middle mouse". So while locked the
 * scroll wheel moves the aim down the body (scroll down) or up it (scroll up) through the target's regions
 * ({@link LockOnRegions}: head, torso, arms, legs, as its body has them); the camera eases onto that region, the bracket
 * sits on it with its name under it, and the server lands his hits there. Middle mouse while locked swaps to whatever
 * living thing is right on the crosshair ({@link #onCrosshair}), or lets go if nothing else is. To get another one onto the
 * crosshair the camera stops easing while he turns it himself ({@link #FREE_LOOK_TICKS}).
 */
public final class LockOnClient {
	static final String KEY_NAME = "key.jaymospowers.lock_on";
	/** Only things this far round from where he looks can be locked (cosine of the angle). */
	static final double PICK_COSINE = 0.7;
	/** The camera closes this share of the gap a tick, at most {@link #MAX_TURN} degrees. */
	static final float EASE = 0.3F;
	static final float MAX_TURN = 25.0F;
	/** Ticks out of sight before he lets go (as the server's). */
	static final int BLIND_TICKS = 15;
	/** The bracket's colour for anyone but a Z Fighter (his is his ki's). */
	static final int COLOR = 0xFFFFE6A0;
	/** The bracket's half size in pixels, and the length of each corner's arms. */
	static final int MARKER = 9;
	static final int ARM = 4;
	/** The bracket's half size on a region smaller than the torso. */
	static final int MARKER_SMALL = 6;
	/** He's turning the camera himself when it moved more than this many degrees in a tick that the ease didn't make. */
	static final float FREE_LOOK_DEGREES = 1.5F;
	/** After he turns it himself, the camera stays his for this many ticks before easing back onto the target. */
	static final int FREE_LOOK_TICKS = 10;
	/** How far round a thing's box the crosshair may pass and still be "right on" it for a swap (blocks). */
	static final double CROSSHAIR_SLACK = 0.3;
	/** One step of the region per this much wheel (a notch is 1; smooth wheels and trackpads send parts of one). */
	static final double SCROLL_STEP = 1.0;

	private static KeyMapping key;
	private static int locked = -1;
	private static int blind;
	/** The region he picked (kept across a swap when the new target has it; the torso when it hasn't). */
	private static Region region = Region.TORSO;
	private static double wheel;
	/** Where the ease left the camera last tick, and ticks the camera is still his own. */
	private static float easedYaw, easedPitch;
	private static boolean eased;
	private static int freeLook;
	/** The lock this side last made itself: one made another way (Kingdoms' Battle Stance) starts on the torso, as the server has it. */
	private static int lastLocked = -1;

	private LockOnClient() {
	}

	public static void init() {
		LockOn.clientTarget = player -> player == Minecraft.getInstance().player ? locked() : null;
		LockOn.clientRegion = player -> {
			LivingEntity target = player == Minecraft.getInstance().player ? locked() : null;
			return target == null ? Region.TORSO : LockOnRegions.fit(target, region);
		};
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_NAME, InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE,
			JaymosPowersClient.CATEGORY));
		ClientTickEvents.START_CLIENT_TICK.register(LockOnClient::input);
		ClientTickEvents.END_CLIENT_TICK.register(LockOnClient::ease);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("lock_on_marker"), LockOnClient::marker);
	}

	private static void release(boolean tell) {
		if (locked >= 0 && tell) {
			ClientPlayNetworking.send(new LockOn.Packet(-1, Region.TORSO.ordinal()));
		}
		locked = -1;
		blind = 0;
		eased = false;
		freeLook = 0;
		wheel = 0.0;
	}

	/** Locks onto {@code living} and tells the server (with the region picked). */
	private static void lock(LivingEntity living) {
		locked = living.getId();
		blind = 0;
		eased = false;
		freeLook = 0;
		lastLocked = locked;
		ClientPlayNetworking.send(new LockOn.Packet(locked, region.ordinal()));
	}

	/** The region picked, as the target's body has it. */
	public static Region region() {
		LivingEntity target = locked();
		return target == null ? Region.TORSO : LockOnRegions.fit(target, region);
	}

	/** (0.17.67) Who this player is locked onto, or null (the guard's lunge and timing bar). */
	public static @Nullable LivingEntity locked() {
		return target(Minecraft.getInstance());
	}

	private static @Nullable LivingEntity target(Minecraft minecraft) {
		if (locked < 0 || minecraft.level == null) {
			return null;
		}
		return minecraft.level.getEntity(locked) instanceof LivingEntity living ? living : null;
	}

	private static void input(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			if (locked >= 0) {
				release(false);
			}
			return;
		}
		boolean pressed = false;
		while (key.consumeClick()) {
			pressed = true;
		}
		if (!pressed || minecraft.gui.screen() != null || player.isSpectator()) {
			return;
		}
		if (locked >= 0) {
			// another one right on the crosshair: the lock moves to it; nothing else there: it lets go
			LivingEntity next = onCrosshair(minecraft, player);
			if (next != null) {
				lock(next);
			} else {
				release(true);
			}
			return;
		}
		LivingEntity pick = pick(minecraft, player);
		if (pick != null) {
			// a fresh lock starts on the torso
			region = Region.TORSO;
			lock(pick);
		}
	}

	/** The living thing (not the one locked) the crosshair is right on, nearest first, or null. */
	private static @Nullable LivingEntity onCrosshair(Minecraft minecraft, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(LockOn.RANGE));
		LivingEntity best = null;
		double bestAt = Double.MAX_VALUE;
		for (Entity entity : minecraft.level.getEntities(player, new AABB(eye, eye).inflate(LockOn.RANGE))) {
			if (entity.getId() == locked || !(entity instanceof LivingEntity living) || !candidate(player, living)) {
				continue;
			}
			var hit = living.getBoundingBox().inflate(CROSSHAIR_SLACK).clip(eye, end);
			if (hit.isPresent()) {
				double at = hit.get().distanceToSqr(eye);
				if (at < bestAt) {
					best = living;
					bestAt = at;
				}
			}
		}
		return best;
	}

	/** The visible living thing nearest the crosshair. */
	private static @Nullable LivingEntity pick(Minecraft minecraft, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestFacing = PICK_COSINE;
		for (Entity entity : minecraft.level.getEntities(player, new AABB(eye, eye).inflate(LockOn.RANGE))) {
			if (!(entity instanceof LivingEntity living) || !candidate(player, living)) {
				continue;
			}
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double facing = to.normalize().dot(look);
			if (facing > bestFacing) {
				best = living;
				bestFacing = facing;
			}
		}
		return best;
	}

	private static boolean candidate(LocalPlayer player, LivingEntity living) {
		if (!living.isAlive() || living.isSpectator() || living == player) {
			return false;
		}
		double distance = living.getBoundingBox().getCenter().distanceTo(player.getEyePosition());
		return distance <= LockOn.RANGE && distance > 1.0E-3 && player.hasLineOfSight(living);
	}

	/**
	 * The scroll wheel while locked: the aim moves down the target's body for a scroll down and up it for a scroll up
	 * (head, torso, arms, legs, as its body has them), stopping at the top and bottom. True when it took the scroll (the
	 * hotbar keeps still while locked). Sneak + the wheel stays the stance's and the ledge grab's.
	 */
	public static boolean scroll(double yOffset) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		LivingEntity current = target(minecraft);
		// (0.18.57) Sneak + the wheel is the stance's (StanceScroll)
		if (current == null || player == null || yOffset == 0.0 || minecraft.gui.screen() != null || minecraft.options.keyShift.isDown()) {
			return false;
		}
		if (wheel != 0.0 && Math.signum(wheel) != Math.signum(yOffset)) {
			wheel = 0.0;
		}
		wheel += yOffset;
		int steps = (int)(wheel / SCROLL_STEP);
		if (steps == 0) {
			return true;
		}
		wheel -= steps * SCROLL_STEP;
		List<Region> regions = LockOnRegions.regions(current);
		int at = regions.indexOf(LockOnRegions.fit(current, region));
		// a scroll down (negative) moves the aim down the body
		int next = Mth.clamp(at - steps, 0, regions.size() - 1);
		if (next != at) {
			region = regions.get(next);
			eased = false;
			freeLook = 0;
			ClientPlayNetworking.send(new LockOn.Packet(locked, region.ordinal()));
		}
		return true;
	}

	/** Every tick: lets go of a target that is gone, too far or unseen; otherwise turns the camera toward it. */
	private static void ease(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (locked < 0 || player == null) {
			return;
		}
		if (locked != lastLocked) {
			region = Region.TORSO;
			lastLocked = locked;
		}
		LivingEntity target = target(minecraft);
		if (target == null || !target.isAlive() || !player.isAlive() || player.distanceTo(target) > LockOn.RANGE) {
			release(true);
			return;
		}
		blind = player.hasLineOfSight(target) ? 0 : blind + 1;
		if (blind > BLIND_TICKS) {
			release(true);
			return;
		}
		if (minecraft.gui.screen() != null || minecraft.isPaused()) {
			return;
		}
		// turned by his own hand since the ease last set it: the camera is his for a moment (to put another on the crosshair)
		if (eased && Math.abs(Mth.wrapDegrees(player.getYRot() - easedYaw)) + Math.abs(player.getXRot() - easedPitch) > FREE_LOOK_DEGREES) {
			freeLook = FREE_LOOK_TICKS;
		}
		if (freeLook > 0) {
			freeLook--;
			easedYaw = player.getYRot();
			easedPitch = player.getXRot();
			eased = true;
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 to = LockOnRegions.point(target, LockOnRegions.fit(target, region), eye).subtract(eye);
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
		float dYaw = Mth.clamp(Mth.wrapDegrees(yaw - player.getYRot()) * EASE, -MAX_TURN, MAX_TURN);
		float dPitch = Mth.clamp((pitch - player.getXRot()) * EASE, -MAX_TURN, MAX_TURN);
		player.setYRot(player.getYRot() + dYaw);
		player.setXRot(Mth.clamp(player.getXRot() + dPitch, -90.0F, 90.0F));
		easedYaw = player.getYRot();
		easedPitch = player.getXRot();
		eased = true;
	}

	/** A bracket round the target, in his ki colour, breathing a little. */
	private static void marker(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LivingEntity target = target(minecraft);
		LocalPlayer player = minecraft.player;
		if (target == null || player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		Camera camera = minecraft.gameRenderer.mainCamera();
		Region picked = LockOnRegions.fit(target, region);
		// on the picked region, moved along with the body between ticks
		Vec3 centre = LockOnRegions.point(target, picked, player.getEyePosition()).add(target.getPosition(partial).subtract(target.position()));
		Vec3 d = centre.subtract(camera.position());
		Vector3fc forward = camera.forwardVector(), left = camera.leftVector(), up = camera.upVector();
		double z = d.x * forward.x() + d.y * forward.y() + d.z * forward.z();
		if (z < 0.1) {
			return;
		}
		double x = d.x * left.x() + d.y * left.y() + d.z * left.z();
		double y = d.x * up.x() + d.y * up.y() + d.z * up.z();
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		double tan = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
		int sx = (int)(w / 2.0 * (1.0 - x / z / (tan * w / h)));
		int sy = (int)(h / 2.0 * (1.0 - y / z / tan));
		Integer form = player.getAttached(ZFighterPower.FORM);
		int color = Powers.has(player, ZFighterPower.INSTANCE) ? 0xFF000000 | ZFighterKiGlow.color(player, form == null ? 0 : form) : COLOR;
		int s = (picked == Region.TORSO ? MARKER : MARKER_SMALL) + Math.round(1.5F * Mth.sin((player.tickCount + partial) * 0.3F));
		for (int sideX = -1; sideX <= 1; sideX += 2) {
			for (int sideY = -1; sideY <= 1; sideY += 2) {
				int cx = sx + sideX * s, cy = sy + sideY * s;
				graphics.fill(Math.min(cx, cx - sideX * ARM), cy, Math.max(cx, cx - sideX * ARM) + 1, cy + 1, color);
				graphics.fill(cx, Math.min(cy, cy - sideY * ARM), cx + 1, Math.max(cy, cy - sideY * ARM) + 1, color);
			}
		}
		// the region's name under it, where there's a choice of more than one
		if (LockOnRegions.regions(target).size() > 1) {
			Component name = picked.displayName(BodyPlan.of(target));
			graphics.centeredText(minecraft.font, name, sx, sy + s + 3, color);
		}
	}
}
