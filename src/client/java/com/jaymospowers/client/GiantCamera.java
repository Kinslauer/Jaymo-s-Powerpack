package com.jaymospowers.client;

import com.jaymospowers.client.power.chrono.ChronoFlowClient;
import com.jaymospowers.client.power.zfighter.ZFighterSpiritCamera;
import com.jaymospowers.core.GiantReach;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.141) A giant's camera, after the Size Changing Mod (KProject; All Rights Reserved, so only the idea): any player
 * grown {@link GiantReach#GIANT_FROM} times or more (the Great Ape, a Behemoth...), seen from behind (F5's view, ours or
 * Shoulder Surfing's), has the view back and up as far as he's grown, almost straight behind him so he sees past
 * himself. The crosshair then picks along the camera's own line, starting level with his eyes, so a left click lands on
 * whatever it's over, as long as that's within his (grown, {@link GiantReach}) reach of his eyes, which is what the
 * server checks. First person stays in his eyes, which vanilla already raises with his size.
 */
public final class GiantCamera {
	/** At a player's own size the view would sit this far back, up and to the right (the pack's shoulder preset)... */
	static final double BACK = 3.4, UP = 0.1, SIDE = 0.9;
	/** ...and for each block of height grown past a player's 1.8, this much further back and this much higher. */
	static final double BACK_PER_BLOCK = 0.75, UP_PER_BLOCK = 0.12;

	private GiantCamera() {
	}

	/** Whether the giant's view is in use this frame. */
	public static boolean active(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && minecraft.getCameraEntity() == player && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK
			&& player.getScale() >= GiantReach.GIANT_FROM && !ZFighterSpiritCamera.pulling() && !ChronoFlowClient.overShoulder(minecraft);
	}

	/** Where the view goes this frame, or null when it isn't a giant's. */
	public static @Nullable Vec3 position(Minecraft minecraft, float partialTicks) {
		if (!active(minecraft)) {
			return null;
		}
		LocalPlayer player = minecraft.player;
		Vec3 eyes = player.getEyePosition(partialTicks);
		double grown = Math.max(0.0, player.getBbHeight() - 1.8);
		float yaw = player.getViewYRot(partialTicks) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 want = eyes.subtract(player.getViewVector(partialTicks).scale(BACK + grown * BACK_PER_BLOCK))
			.add(0.0, UP + grown * UP_PER_BLOCK, 0.0)
			.add(right.scale(SIDE));
		return clipped(player, eyes, want);
	}

	/**
	 * What the crosshair is over, picked along the giant's camera line rather than from his eyes (vanilla's pick), or
	 * null when the giant's view isn't in use. Anything behind his eyes along that line is passed over.
	 */
	public static @Nullable HitResult pick(Minecraft minecraft, float partialTicks) {
		Vec3 camera = position(minecraft, partialTicks);
		if (camera == null) {
			return null;
		}
		LocalPlayer player = minecraft.player;
		Vec3 eyes = player.getEyePosition(partialTicks);
		Vec3 look = player.getViewVector(partialTicks);
		double entityReach = player.entityInteractionRange();
		double blockReach = player.blockInteractionRange();
		// from level with his eyes, out as far as anything could still be in reach
		Vec3 from = camera.add(look.scale(Math.max(0.0, eyes.subtract(camera).dot(look))));
		double far = Math.max(entityReach, blockReach) + from.distanceTo(eyes);
		Vec3 to = from.add(look.scale(far));
		BlockHitResult block = player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double blockDistance = block.getType() == HitResult.Type.MISS ? far : from.distanceTo(block.getLocation());
		AABB search = new AABB(from, from.add(look.scale(blockDistance))).inflate(1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, from, from.add(look.scale(blockDistance)), search,
			e -> !e.isSpectator() && e.isPickable() && !isPart(player, e), blockDistance * blockDistance);
		if (entity != null && player.isWithinEntityInteractionRange(entity.getEntity(), 0.0)) {
			return entity;
		}
		if (block.getType() != HitResult.Type.MISS && player.isWithinBlockInteractionRange(block.getBlockPos(), 0.0)) {
			return block;
		}
		return BlockHitResult.miss(to, Direction.getApproximateNearest(look), net.minecraft.core.BlockPos.containing(to));
	}

	/** Himself, or what he's riding or carrying. */
	private static boolean isPart(LocalPlayer player, Entity entity) {
		return entity == player || player.isPassengerOfSameVehicle(entity) || entity.hasPassenger(player) || player.hasPassenger(entity);
	}

	/** {@code want}, or short of it where a wall's in the way. */
	public static Vec3 clipped(LocalPlayer player, Vec3 eyes, Vec3 want) {
		HitResult hit = player.level().clip(new ClipContext(eyes, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 back = hit.getLocation().subtract(eyes);
			double length = back.length();
			want = length < 0.6 ? eyes : eyes.add(back.scale((length - 0.5) / length));
		}
		return want;
	}
}
