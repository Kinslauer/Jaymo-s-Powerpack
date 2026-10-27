package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.power.zfighter.ZFighterKiGlow;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.LockOn;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.45, Andy's NOEA picks) Lock-on, the client half, for everyone (Andy: "make lock on middle mouse, and it should
 * work across the board. You can swap targets to the left and right by scrolling"). Middle mouse locks onto the living
 * thing nearest the crosshair within {@link LockOn#RANGE} blocks that he can see, and sends its id to the server
 * ({@link LockOn.Packet}); pressed again, or out of sight for a moment, or past the range, it lets go. While it holds the
 * camera eases round to the target ({@link #EASE}), so whatever he aims (a power, a bow, a blow) goes there, a small
 * bracket is drawn on it, and the scroll wheel moves the lock to the next one to the left or right ({@link #scroll}).
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

	private static KeyMapping key;
	private static int locked = -1;
	private static int blind;

	private LockOnClient() {
	}

	public static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_NAME, InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE,
			JaymosPowersClient.CATEGORY));
		ClientTickEvents.START_CLIENT_TICK.register(LockOnClient::input);
		ClientTickEvents.END_CLIENT_TICK.register(LockOnClient::ease);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("lock_on_marker"), LockOnClient::marker);
	}

	private static void release(boolean tell) {
		if (locked >= 0 && tell) {
			ClientPlayNetworking.send(new LockOn.Packet(-1));
		}
		locked = -1;
		blind = 0;
	}

	/** (0.17.64) Who this player is locked onto, or null (the guard's lunge and timing bar). */
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
			release(true);
			return;
		}
		LivingEntity pick = pick(minecraft, player);
		if (pick != null) {
			locked = pick.getId();
			blind = 0;
			ClientPlayNetworking.send(new LockOn.Packet(locked));
		}
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
	 * The scroll wheel while locked: the lock moves to the nearest one beside the target, to the right for a scroll
	 * down and to the left for a scroll up (the hotbar's way round). True when it took the scroll (the hotbar keeps
	 * still); with nothing on that side the lock stays.
	 */
	public static boolean scroll(double yOffset) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		LivingEntity current = target(minecraft);
		if (current == null || player == null || yOffset == 0.0 || minecraft.gui.screen() != null) {
			return false;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		double from = bearing(camera, current);
		boolean right = yOffset < 0.0;
		LivingEntity best = null;
		double bestGap = Double.MAX_VALUE;
		Vec3 eye = player.getEyePosition();
		for (Entity entity : minecraft.level.getEntities(player, new AABB(eye, eye).inflate(LockOn.RANGE))) {
			if (entity == current || !(entity instanceof LivingEntity living) || !candidate(player, living)) {
				continue;
			}
			double gap = right ? from - bearing(camera, living) : bearing(camera, living) - from;
			if (gap > 1.0E-3 && gap < bestGap) {
				best = living;
				bestGap = gap;
			}
		}
		if (best != null) {
			locked = best.getId();
			blind = 0;
			ClientPlayNetworking.send(new LockOn.Packet(locked));
		}
		return true;
	}

	/** How far round to the left of the camera's line it is, in radians (negative: to the right). */
	private static double bearing(Camera camera, LivingEntity living) {
		Vec3 d = living.getBoundingBox().getCenter().subtract(camera.position());
		Vector3fc forward = camera.forwardVector(), left = camera.leftVector();
		return Math.atan2(d.x * left.x() + d.y * left.y() + d.z * left.z(), d.x * forward.x() + d.y * forward.y() + d.z * forward.z());
	}

	/** Every tick: lets go of a target that is gone, too far or unseen; otherwise turns the camera toward it. */
	private static void ease(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (locked < 0 || player == null) {
			return;
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
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
		float dYaw = Mth.clamp(Mth.wrapDegrees(yaw - player.getYRot()) * EASE, -MAX_TURN, MAX_TURN);
		float dPitch = Mth.clamp((pitch - player.getXRot()) * EASE, -MAX_TURN, MAX_TURN);
		player.setYRot(player.getYRot() + dYaw);
		player.setXRot(Mth.clamp(player.getXRot() + dPitch, -90.0F, 90.0F));
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
		Vec3 centre = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0);
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
		int s = MARKER + Math.round(1.5F * Mth.sin((player.tickCount + partial) * 0.3F));
		for (int sideX = -1; sideX <= 1; sideX += 2) {
			for (int sideY = -1; sideY <= 1; sideY += 2) {
				int cx = sx + sideX * s, cy = sy + sideY * s;
				graphics.fill(Math.min(cx, cx - sideX * ARM), cy, Math.max(cx, cx - sideX * ARM) + 1, cy + 1, color);
				graphics.fill(cx, Math.min(cy, cy - sideY * ARM), cx + 1, Math.max(cy, cy - sideY * ARM) + 1, color);
			}
		}
	}
}
