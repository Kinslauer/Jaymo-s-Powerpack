package com.jaymospowers.power.xenoform.nadir;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nadir's Rift (G): space, folded. A from-scratch take on the ideas of The Awakening's Rift power (an all-rights-reserved
 * mod: nothing of its code or art is used).
 * <ul>
 * <li>Tap at a creature within {@link #DROP_REACH}: Recursive Drop. A maw opens in the ground under it and another in the
 * sky above; it falls through one and out of the other, {@link #DROP_PASSES} times, hurt each pass, then is let go.</li>
 * <li>Tap at nothing: Rift Blink, a step through space to where you look (up to {@link #BLINK_REACH} blocks).</li>
 * <li>Hold {@link #SURFACE_HOLD} ticks: Surface, straight up through the rock to the highest safe ground above.</li>
 * <li>Sneak + G: the Void Pocket ({@link NadirStash}).</li>
 * </ul>
 */
public final class NadirRift {
	// ---- tuning ----
	public static final double BLINK_REACH = 16.0;
	public static final int BLINK_COOLDOWN = 30;
	public static final double DROP_REACH = 22.0;
	public static final int DROP_PASSES = 7, DROP_COOLDOWN = 240;
	public static final float DROP_DAMAGE = 2.0F;
	/** The sky maw opens this high over the floor maw (lower under a ceiling, but never under {@link #DROP_MIN_GAP}). */
	public static final double DROP_GAP = 7.0, DROP_MIN_GAP = 2.5;
	public static final int SURFACE_HOLD = 20, SURFACE_COOLDOWN = 200;

	private NadirRift() {
	}

	// ---- Rift Blink ----

	/** Steps the player to where they look; false (and nothing happens) if there's nowhere to stand. */
	public static boolean blink(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockHitResult hit = NadirAim.block(player, BLINK_REACH);
		Vec3 to;
		if (NadirAim.missed(hit)) {
			to = hit.getLocation().subtract(0.0, player.getBbHeight() * 0.5, 0.0);
		} else if (hit.getDirection() == Direction.UP) {
			to = hit.getLocation();
		} else {
			// just in front of the wall, or under the roof
			Vec3 back = player.getLookAngle().scale(-0.6);
			to = hit.getLocation().add(back);
			if (hit.getDirection() == Direction.DOWN) {
				to = to.subtract(0.0, player.getBbHeight(), 0.0);
			}
		}
		Vec3 spot = free(player, to);
		if (spot == null) {
			return false;
		}
		Vec3 from = player.position();
		tear(level, from, player.getBbHeight());
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		tear(level, spot, player.getBbHeight());
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}

	/** {@code at}, or the nearest spot a little above it, where the player fits; null if none. */
	private static Vec3 free(ServerPlayer player, Vec3 at) {
		for (double up = 0.0; up <= 2.0; up += 0.5) {
			Vec3 spot = at.add(0.0, up, 0.0);
			if (player.level().noCollision(player, player.getBoundingBox().move(spot.subtract(player.position())))) {
				return spot;
			}
		}
		return null;
	}

	private static void tear(ServerLevel level, Vec3 at, double height) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + height * 0.5, at.z, 40, 0.3, height * 0.4, 0.3, 0.15);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + height * 0.5, at.z, 8, 0.2, height * 0.3, 0.2, 0.02);
	}

	// ---- Recursive Drop ----

	/** Opens the two maws round {@code target}; false if there's no room between ground and ceiling. */
	public static boolean drop(ServerPlayer player, NadirState st, LivingEntity target) {
		ServerLevel level = player.level();
		BlockPos column = target.blockPosition();
		// the floor maw: the ground under it (or where it stands)
		int floor = column.getY();
		for (int i = 0; i < 12 && floor > level.getMinY() && level.getBlockState(new BlockPos(column.getX(), floor - 1, column.getZ())).getCollisionShape(level,
			new BlockPos(column.getX(), floor - 1, column.getZ())).isEmpty(); i++) {
			floor--;
		}
		// the sky maw: as high as the room allows
		double gap = 0.0;
		while (gap < DROP_GAP && clear(level, new BlockPos(column.getX(), floor + (int)Math.ceil(gap + target.getBbHeight()), column.getZ()))) {
			gap += 1.0;
		}
		if (gap < DROP_MIN_GAP) {
			return false;
		}
		st.dropTarget = target.getId();
		st.dropPasses = DROP_PASSES;
		st.dropX = target.getX();
		st.dropZ = target.getZ();
		st.dropFloor = floor;
		st.dropSky = floor + gap;
		maw(level, st.dropX, st.dropFloor + 0.05, st.dropZ);
		level.playSound(null, st.dropX, st.dropFloor, st.dropZ, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.8F);
		target.teleportTo(st.dropX, st.dropSky, st.dropZ);
		target.setDeltaMovement(0.0, -0.6, 0.0);
		target.syncVelocity = true;
		return true;
	}

	private static boolean clear(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** Every tick of a Recursive Drop: pinned in its column; each time it reaches the floor maw it comes out of the sky. */
	public static void tickDrop(ServerPlayer player, NadirState st) {
		if (st.dropTarget < 0) {
			return;
		}
		ServerLevel level = player.level();
		Entity e = level.getEntity(st.dropTarget);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.position().distanceToSqr(st.dropX, target.getY(), st.dropZ) > 9.0) {
			st.dropTarget = -1;
			return;
		}
		Vec3 v = target.getDeltaMovement();
		target.setDeltaMovement(0.0, Math.min(v.y, -0.35), 0.0);
		if (Math.abs(target.getX() - st.dropX) > 0.05 || Math.abs(target.getZ() - st.dropZ) > 0.05) {
			target.teleportTo(st.dropX, target.getY(), st.dropZ);
		}
		target.syncVelocity = true;
		if (player.tickCount % 3 == 0) {
			maw(level, st.dropX, st.dropFloor + 0.05, st.dropZ);
			maw(level, st.dropX, st.dropSky + target.getBbHeight() + 0.2, st.dropZ);
		}
		if (target.getY() > st.dropFloor + 0.4 && !target.onGround()) {
			return;
		}
		// through the floor maw
		target.hurtServer(level, player.damageSources().playerAttack(player), DROP_DAMAGE);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, st.dropX, st.dropFloor + 0.2, st.dropZ, 20, 0.3, 0.1, 0.3, 0.1);
		level.playSound(null, st.dropX, st.dropFloor, st.dropZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.7F + st.dropPasses * 0.1F);
		if (--st.dropPasses <= 0 || !target.isAlive()) {
			st.dropTarget = -1;
			return;
		}
		target.resetFallDistance();
		target.teleportTo(st.dropX, st.dropSky, st.dropZ);
		target.setDeltaMovement(0.0, -0.8, 0.0);
		target.syncVelocity = true;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, st.dropX, st.dropSky + target.getBbHeight(), st.dropZ, 20, 0.3, 0.1, 0.3, 0.1);
	}

	/** A flat ring of the void: the mouth of a maw. */
	private static void maw(ServerLevel level, double x, double y, double z) {
		for (int i = 0; i < 12; i++) {
			double a = i / 12.0 * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.PORTAL, x + Math.cos(a) * 0.9, y, z + Math.sin(a) * 0.9, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 2, 0.3, 0.0, 0.3, 0.0);
	}

	// ---- Surface ----

	/** Rifts the player up to the highest safe ground over their head; false if there is none. */
	public static boolean surface(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		boolean roofed = level.dimensionType().hasCeiling();
		int top = roofed ? level.getMaxY() : Math.min(level.getMaxY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) + 1);
		BlockPos best = null;
		for (int y = at.getY() + 2; y <= top; y++) {
			BlockPos feet = new BlockPos(at.getX(), y, at.getZ());
			BlockState below = level.getBlockState(feet.below());
			if (roofed && below.is(Blocks.BEDROCK)) {
				// the roof of the Nether: stop under it
				break;
			}
			if (stands(level, feet)) {
				best = feet;
			}
		}
		if (best == null) {
			return false;
		}
		Vec3 from = player.position();
		tear(level, from, player.getBbHeight());
		player.teleportTo(level, best.getX() + 0.5, best.getY(), best.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		tear(level, player.position(), player.getBbHeight());
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
		return true;
	}

	/** Solid, dry, cool ground with room for a body over it. */
	private static boolean stands(ServerLevel level, BlockPos feet) {
		BlockPos floor = feet.below();
		BlockState ground = level.getBlockState(floor);
		if (ground.getCollisionShape(level, floor).isEmpty() || ground.is(Blocks.MAGMA_BLOCK) || ground.is(BlockTags.FIRE)
			|| ground.is(Blocks.CAMPFIRE) || ground.is(Blocks.SOUL_CAMPFIRE) || !ground.getFluidState().isEmpty()) {
			return false;
		}
		for (int i = 0; i < 2; i++) {
			BlockPos p = feet.above(i);
			BlockState s = level.getBlockState(p);
			if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty() || s.is(BlockTags.FIRE)) {
				return false;
			}
		}
		return true;
	}
}
