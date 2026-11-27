package com.jaymospowers.power.web;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Puts out the ground fire (Legends patch 4, {@code sy_snuff}): when fire is burning on the ground where you stand, a
 * tendril stamps it out. The symbiote is terrified of fire ({@link WebSymbiote#FLEE_TICKS}: it shoves you clear), so this
 * is what it does first when it can bear to, and the shove is what is left when it cannot: it will not smother while it
 * is too wound up ({@link #MAX_STRESS}), starving, sulking, or when lava or your own burning is part of it, and each
 * smothering costs it satiety and winds it up a little. Ordinary fire and soul fire on blocks both go (nothing else:
 * lava, magma, campfires and torches are left, and so is a fire the world may not be touched at). The fright of the
 * flames itself ({@link WebSymbiote#nearFlames}) is not changed. {@link WebPose#SNUFF} draws the body.
 */
public final class WebSymbioteSnuff {
	/** How close the fire must be (blocks, sideways and up/down), the most it stamps out at once, and the wait between (ticks). */
	static final double REACH = 2.5, REACH_UP = 1.0;
	static final int MOST = 12, COOLDOWN = 10;
	/** What it costs the symbiote (satiety) and how much it winds it up (stress); the stress past which it will not. */
	static final int COST = 1, STRESS_GAIN = 2, MAX_STRESS = 70;

	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebSymbioteSnuff() {
	}

	/**
	 * Called before the flinch-away check: true if it stamped the fire out (and the shove is not needed), false if there was
	 * nothing it could put out, or it could not bear to.
	 */
	static boolean smother(ServerPlayer player) {
		if (!(WebSymbiote.suitShown(player) || WebSymbiote.livingArmor(player)) || !player.isAlive() || player.isSpectator() || player.isOnFire()
			|| player.isInLava() || WebSymbiote.sulking(player)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		AABB box = player.getBoundingBox().inflate(REACH, REACH_UP, REACH);
		List<BlockPos> fires = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			BlockState block = level.getBlockState(pos);
			if (block.getFluidState().is(FluidTags.LAVA)) {
				// lava is not something it can stamp out: it flees
				return false;
			}
			if (block.is(BlockTags.FIRE) && fires.size() < MOST && level.mayInteract(player, pos)) {
				fires.add(pos.immutable());
			}
		}
		if (fires.isEmpty()) {
			return false;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		if (state.stress() >= MAX_STRESS || state.satiety() < COST && !player.isCreative() || now < READY.getOrDefault(player.getUUID(), 0L)) {
			return false;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		WebSymbiote.set(player, state.withSatiety(player.isCreative() ? state.satiety() : state.satiety() - COST)
			.withStress(Math.min(WebSymbiote.STRESS_MAX, state.stress() + STRESS_GAIN)));
		Vec3 back = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
		for (BlockPos pos : fires) {
			level.removeBlock(pos, false);
			Vec3 at = Vec3.atCenterOf(pos);
			MagicFx.stroke(level, back, at, 0.05F, 0x14141C);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 4, 0.2, 0.1, 0.2, 0.02);
			level.sendParticles(WebSymbiote.dust(1.0F), at.x, at.y, at.z, 6, 0.2, 0.1, 0.2, 0.0);
		}
		WebPower.pose(player, WebPose.SNUFF, 1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 0.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.5F);
		return true;
	}

	static void forget(ServerPlayer player) {
		READY.remove(player.getUUID());
	}

	static void clearAll() {
		READY.clear();
	}
}
