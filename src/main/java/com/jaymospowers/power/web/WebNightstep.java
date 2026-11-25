package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nightstep: the black strain, suited, steps from shadow to shadow. Only in the dark: the light where you stand and the
 * light where you land must both be at most {@link #NIGHTSTEP_LIGHT}. It carries you up to {@link #DAY_REACH} blocks the
 * way you look (up to {@link #NIGHT_REACH} at night), to the furthest dark footing in a clear line, and does nothing
 * (and costs nothing) if there is none. Costs {@link #COST} satiety, cools down {@link #COOLDOWN} ticks; on the skill bar
 * while suited ({@link WebBarMoves#NIGHTSTEP}).
 */
public final class WebNightstep {
	/** The brightest light (0-15) either end may have. */
	public static final int NIGHTSTEP_LIGHT = 7;
	/** How far a step goes (blocks) by day (underground, say) and at night. */
	static final int DAY_REACH = 6, NIGHT_REACH = 12;
	/** A step may climb or drop this many blocks. */
	static final int STEP_Y = 2;
	public static final int COST = 3, COOLDOWN = 100;
	/** Ticks of darkness over your eyes as you come out, and the shake. */
	static final int DARK_TICKS = 24;
	static final float SHAKE = 0.4F;
	static final int INK = 0x120818;

	private WebNightstep() {
	}

	/** Black, suited, awake: the step is open to you (both sides: the bar asks). */
	public static boolean available(net.minecraft.world.entity.player.Player player) {
		return WebSymbiote.isBonded(player) && WebSymbiote.state(player).kind() == WebSymbioteStrain.BLACK && WebExecutions.applies(player)
			&& player.isAlive() && !player.isSpectator();
	}

	/** The step: false (nothing spent) if it can't go. */
	public static boolean use(ServerPlayer player) {
		if (!available(player) || WebSymbioteBond.dormant(player) || WebLash.cooling(player, WebLash.SLOT_NIGHTSTEP)) {
			return false;
		}
		ServerLevel level = player.level();
		BlockPos here = player.blockPosition();
		if (level.getMaxLocalRawBrightness(here) > NIGHTSTEP_LIGHT) {
			WebSymbiote.nag(player, "nightstep_light");
			return false;
		}
		Vec3 dir = player.getLookAngle().horizontal();
		if (dir.lengthSqr() < 1.0E-4) {
			return false;
		}
		dir = dir.normalize();
		int reach = level.isDarkOutside() ? NIGHT_REACH : DAY_REACH;
		Vec3 land = null;
		for (int d = reach; d >= 2 && land == null; d--) {
			Vec3 flat = player.position().add(dir.scale(d));
			for (int dy = 0; dy <= STEP_Y * 2 && land == null; dy++) {
				int y = dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2;
				land = footing(level, player, BlockPos.containing(flat.x, player.getY() + y, flat.z));
			}
		}
		if (land == null) {
			WebSymbiote.nag(player, "nightstep_none");
			return false;
		}
		if (!WebLash.pay(player, COST)) {
			return false;
		}
		WebLash.cool(player, WebLash.SLOT_NIGHTSTEP, COOLDOWN);
		Vec3 from = player.position();
		shade(level, from);
		player.teleportTo(level, land.x, land.y, land.z, Set.of(), player.getYRot(), player.getXRot(), true);
		player.resetFallDistance();
		shade(level, land);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.5F);
		level.playSound(null, land.x, land.y, land.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.4F);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_TICKS, 0, false, false));
		Shakes.send(player, SHAKE);
		WebSymbiote.narrate(player, "nightstep");
		return true;
	}

	/** A dark, standing place at {@code pos} (feet) with a clear line from you, or null. */
	private static Vec3 footing(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP) || level.getMaxLocalRawBrightness(pos) > NIGHTSTEP_LIGHT) {
			return null;
		}
		Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (!level.noCollision(player, player.getBoundingBox().move(at.subtract(player.position())))) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 there = at.add(0.0, player.getEyeHeight(), 0.0);
		return level.clip(new ClipContext(eye, there, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS ? at : null;
	}

	/** Ink and a pool of shadow where the step starts or ends. */
	private static void shade(ServerLevel level, Vec3 at) {
		MagicFx.pool(level, at, 1.3F, INK, 20);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0, at.z, 20, 0.3, 0.8, 0.3, 0.04);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8, at.z, 8, 0.3, 0.6, 0.3, 0.01);
	}
}
