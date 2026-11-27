package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nadir's Erase, the Void of the Hand of God mod (ideas only; it is All Rights Reserved): a flash, and the creature is
 * gone, its shadow burned onto whatever stood behind it (the walls, the ground), the way a great light leaves one.
 * Sneak + B at a creature erases it; sprint + B, everything in a cone ahead ({@link #CONE_REACH}, {@link #CONE_DEGREES}).
 * It deals {@link #DAMAGE}: what that kills is gone with the flash and leaves no body (its drops fall); a great foe that
 * lives through it is only scorched. The shadow lies there {@link #SHADOW_TICKS}.
 */
public final class NadirErase {
	// ---- tuning ----
	/** How far a single erase reaches, and the cone's reach and half-angle. */
	public static final double REACH = 32.0, CONE_REACH = 20.0, CONE_DEGREES = 30.0;
	/** What it deals (a full-charge meteor's heart), and the cooldowns (B's): one, and the cone. */
	static final float DAMAGE = 60.0F;
	public static final int COOLDOWN = 160, CONE_COOLDOWN = 600;
	/** The shadow: how far behind it is cast (blocks), the spacing of its marks, the most marks, how long it lies there. */
	static final double SHADOW_DEPTH = 12.0, SHADOW_STEP = 0.22;
	static final int SHADOW_MARKS = 64, SHADOW_TICKS = 2400;
	/** The flash's colour, and the shadow's: it glows the flash's colour at first and cools to the burned black. */
	static final int FLASH = 0xF4EEFF, BURN = 0x0B0910;
	/** Jaymo's Kitchen leaves no body for a creature with this tag (Corpses.NO_CORPSE_TAG). */
	static final String NO_CORPSE_TAG = "jaymoskitchen.no_corpse";

	private NadirErase() {
	}

	/** Erases the creature under the crosshair; false if there is none. */
	public static boolean one(ServerPlayer player) {
		LivingEntity target = NadirAim.aimed(player, REACH);
		if (target == null) {
			return false;
		}
		erase(player, target);
		return true;
	}

	/** Erases everything Nadir may hit in the cone ahead; false if there was nothing. */
	public static boolean cone(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double cos = Math.cos(Math.toRadians(CONE_DEGREES));
		List<LivingEntity> in = NadirAim.around(player, eye, CONE_REACH);
		in.removeIf(e -> e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) < cos || !player.hasLineOfSight(e));
		for (LivingEntity e : in) {
			erase(player, e);
		}
		if (!in.isEmpty()) {
			MagicFx.blast(player.level(), eye.add(look.scale(2.0)), 3.0F, FLASH);
		}
		return !in.isEmpty();
	}

	private static void erase(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		Vec3 c = target.getBoundingBox().getCenter();
		Vec3 way = c.subtract(player.getEyePosition());
		way = way.lengthSqr() < 1.0E-6 ? player.getViewVector(1.0F) : way.normalize();
		shadow(level, target, way);
		float size = Math.max(target.getBbWidth(), target.getBbHeight());
		MagicFx.blast(level, c, size * 1.8F, FLASH, 8);
		MagicFx.blast(level, c, size * 0.9F, 0x7A4FD0, 14);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4F, 0.4F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.6F, 0.5F);
		Shakes.around(level, c, 16.0, 0.5F);
		boolean body = !(target instanceof Player);
		if (body) {
			target.addTag(NO_CORPSE_TAG);
		}
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), DAMAGE);
		if (body && target.isDeadOrDying()) {
			// gone with the flash: no fall, no puff
			target.remove(Entity.RemovalReason.KILLED);
		} else if (body) {
			target.removeTag(NO_CORPSE_TAG);
		}
	}

	/**
	 * Its shadow, burned onto what stood behind it: the light came from Nadir, so each mark goes where a ray from the
	 * creature's outline, on past it, first meets a block. The outline is the shape of a body: a head on shoulders for
	 * something standing tall, an oval for anything else.
	 */
	private static void shadow(ServerLevel level, LivingEntity target, Vec3 way) {
		Vec3 side = way.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 up = side.cross(way).normalize();
		double w = target.getBbWidth(), h = target.getBbHeight();
		boolean tall = h > w * 1.4;
		Vec3 c = target.getBoundingBox().getCenter();
		double step = Math.max(SHADOW_STEP, Math.sqrt(w * h / SHADOW_MARKS));
		int marks = 0;
		for (double y = -h * 0.5; y <= h * 0.5 && marks < SHADOW_MARKS; y += step) {
			for (double x = -w * 0.6; x <= w * 0.6 && marks < SHADOW_MARKS; x += step) {
				if (!inside(x / w, y / h + 0.5, tall)) {
					continue;
				}
				Vec3 from = c.add(side.scale(x)).add(up.scale(y));
				BlockHitResult hit = level.clip(new ClipContext(from, from.add(way.scale(SHADOW_DEPTH)), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, target));
				if (hit.getType() != HitResult.Type.BLOCK) {
					continue;
				}
				marks++;
				MagicFx.scorch(level, hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()), (float)(step * 0.85), FLASH, BURN,
					SHADOW_TICKS);
			}
		}
	}

	/** Whether ({@code x} across, -0.6 to 0.6 of the width; {@code y} up, 0 to 1 of the height) is inside the outline. */
	private static boolean inside(double x, double y, boolean tall) {
		if (!tall) {
			double ex = x / 0.55, ey = (y - 0.5) / 0.5;
			return ex * ex + ey * ey <= 1.0;
		}
		if (y > 0.8) {
			// the head
			double hx = x / 0.28, hy = (y - 0.9) / 0.1;
			return hx * hx + hy * hy <= 1.0;
		}
		if (y > 0.4) {
			// shoulders and arms
			return Math.abs(x) <= 0.58;
		}
		// the legs, a gap between them
		return Math.abs(x) <= 0.4 && Math.abs(x) >= 0.04;
	}
}
