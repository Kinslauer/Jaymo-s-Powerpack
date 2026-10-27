package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterLock;
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
 * (0.17.42, Andy's NOEA picks) Lock-on, the client half. The key (Left Alt by default, shared with the zoom mod's) locks
 * onto the living thing nearest the crosshair within {@link ZFighterLock#RANGE} blocks that he can see, and sends its id
 * to the server ({@link ZFighterLock.LockOn}); pressed again, or out of sight for a moment, or past the range, it lets
 * go. While it holds the camera eases round to the target ({@link #EASE}) and a small bracket is drawn on it.
 */
public final class ZFighterLockClient {
	static final String KEY_NAME = "key.jaymospowers.zfighter_lock";
	/** Only things this far round from where he looks can be locked (cosine of the angle). */
	static final double PICK_COSINE = 0.7;
	/** The camera closes this share of the gap a tick, at most {@link #MAX_TURN} degrees. */
	static final float EASE = 0.3F;
	static final float MAX_TURN = 25.0F;
	/** Ticks out of sight before he lets go (as the server's). */
	static final int BLIND_TICKS = 15;
	/** The bracket's half size in pixels, and the length of each corner's arms. */
	static final int MARKER = 9;
	static final int ARM = 4;

	private static KeyMapping key;
	private static int locked = -1;
	private static int blind;

	private ZFighterLockClient() {
	}

	static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_NAME, InputConstants.KEY_LALT, JaymosPowersClient.CATEGORY));
		ClientTickEvents.START_CLIENT_TICK.register(ZFighterLockClient::input);
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterLockClient::ease);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("zfighter_lock_marker"), ZFighterLockClient::marker);
	}

	private static void release(boolean tell) {
		if (locked >= 0 && tell) {
			ClientPlayNetworking.send(new ZFighterLock.LockOn(-1));
		}
		locked = -1;
		blind = 0;
	}

	private static @Nullable LivingEntity target(Minecraft minecraft) {
		if (locked < 0 || minecraft.level == null) {
			return null;
		}
		return minecraft.level.getEntity(locked) instanceof LivingEntity living ? living : null;
	}

	private static void input(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !Powers.has(player, ZFighterPower.INSTANCE)) {
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
			ClientPlayNetworking.send(new ZFighterLock.LockOn(locked));
		}
	}

	/** The visible living thing nearest the crosshair. */
	private static @Nullable LivingEntity pick(Minecraft minecraft, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestFacing = PICK_COSINE;
		for (Entity entity : minecraft.level.getEntities(player, new AABB(eye, eye).inflate(ZFighterLock.RANGE))) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
				continue;
			}
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > ZFighterLock.RANGE || distance < 1.0E-3) {
				continue;
			}
			double facing = to.scale(1.0 / distance).dot(look);
			if (facing > bestFacing && player.hasLineOfSight(living)) {
				best = living;
				bestFacing = facing;
			}
		}
		return best;
	}

	/** Every tick: lets go of a target that is gone, too far or unseen; otherwise turns the camera toward it. */
	private static void ease(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (locked < 0 || player == null) {
			return;
		}
		LivingEntity target = target(minecraft);
		if (target == null || !target.isAlive() || !player.isAlive() || player.distanceTo(target) > ZFighterLock.RANGE) {
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
		int color = 0xFF000000 | ZFighterKiGlow.color(player, form == null ? 0 : form);
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
