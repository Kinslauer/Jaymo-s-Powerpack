package com.jaymospowers.power.zfighter;

import com.jaymospowers.boxing.Boxing;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Airborne, flat, pinned (Andy's Awakening pick: "Z Fighter's hold combos and the boxing styles"). The kit's moves leave a foe
 * in one of three states: in the air (a knee, the dragon fist, a launch, a finisher), flat (swept off its feet, knocked out,
 * down on the ground) or pinned (staggered, held reeling, shocked or webbed still). A Z Fighter's harm, or a boxer's punch,
 * that lands on a body in one of those states hits harder: {@link #SAME_MOVE} if the same move put it there, {@link #OTHER_MOVE}
 * when a different move did (the point of mixing them). Each state-hit in a row adds {@link #CHAIN_STEP} more, up to
 * {@link #CHAIN_MOST}: the combo. A spell of {@link #WINDOW} ticks without one and the chain is over.
 * <p>
 * Read on the hurt path ({@link ZFighterHooks#modifyDamage}, every creature's {@code hurtServer}).
 */
final class ZFighterStates {
	// ---- tuning ----
	/** The bonus for a state put there by the same move, by another move. */
	static final float SAME_MOVE = 0.05F, OTHER_MOVE = 0.10F;
	/** Each state-hit in a row adds this, up to this. */
	static final float CHAIN_STEP = 0.025F, CHAIN_MOST = 0.15F;
	/** How long a move is remembered as what put a foe in its state, and keeps the chain (ticks). */
	static final int WINDOW = 40;
	/** Slowness at or over these amplifiers: tripped flat (the leg sweep's), held pinned (a shock, a web, a hold). */
	static final int TRIPPED = 4, HELD = 6;

	static final int NONE = 0, AIRBORNE = 1, FLAT = 2, PINNED = 3;

	private static final class Mark {
		UUID by;
		String move;
		long at;
		int chain;
	}

	/** The last kit move that landed on each creature. Not saved. */
	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private ZFighterStates() {
	}

	/** The state {@code e} is in now. */
	static int state(LivingEntity e) {
		// something that flies of itself isn't "in the air" for this
		boolean flier = e.isNoGravity() || e instanceof Mob m && (m.getNavigation() instanceof FlyingPathNavigation || m.getMoveControl() instanceof FlyingMoveControl)
			|| e instanceof Player p && p.getAbilities().flying || e.isFallFlying();
		if (!flier && !e.onGround() && !e.isInWater() && !e.isPassenger() && !e.onClimbable()) {
			return AIRBORNE;
		}
		MobEffectInstance slow = e.getEffect(MobEffects.SLOWNESS);
		int amp = slow == null ? -1 : slow.getAmplifier();
		if (ZFighterPoise.knockedOut(e) || e.getPose() == Pose.SLEEPING || e.getPose() == Pose.SWIMMING && !e.isInWater()
			|| amp >= TRIPPED && amp < HELD) {
			return FLAT;
		}
		if (ZFighterPoise.stunned(e) || amp >= HELD) {
			return PINNED;
		}
		return NONE;
	}

	/** The harm a Z Fighter's or a boxer's hit on {@code victim} does, for the state it's in. */
	static float apply(LivingEntity victim, DamageSource source, float damage) {
		if (damage <= 0.0F || victim.level().isClientSide() || !(source.getEntity() instanceof ServerPlayer player) || player == victim) {
			return damage;
		}
		boolean boxer = Boxing.boxing(player) && source.getDirectEntity() == player;
		if (!boxer && !Powers.has(player, ZFighterPower.INSTANCE)) {
			return damage;
		}
		long now = victim.level().getGameTime();
		String move = move(player, source, boxer);
		Mark mark = MARKS.get(victim.getUUID());
		boolean fresh = mark != null && mark.by.equals(player.getUUID()) && now - mark.at <= WINDOW;
		int state = state(victim);
		float bonus = 0.0F;
		if (state != NONE) {
			int chain = fresh ? mark.chain : 0;
			bonus = (fresh && !mark.move.equals(move) ? OTHER_MOVE : SAME_MOVE) + Math.min(CHAIN_MOST, CHAIN_STEP * chain);
			if (victim.level() instanceof ServerLevel level) {
				Vec3 c = victim.getBoundingBox().getCenter();
				level.sendParticles(ParticleTypes.CRIT, true, false, c.x, c.y, c.z, 4 + chain, 0.2, 0.2, 0.2, 0.3);
			}
		}
		if (mark == null) {
			if (MARKS.size() > 256) {
				MARKS.values().removeIf(m -> now - m.at > WINDOW);
			}
			mark = new Mark();
			MARKS.put(victim.getUUID(), mark);
		}
		mark.chain = state != NONE ? (fresh ? mark.chain + 1 : 1) : fresh ? mark.chain : 0;
		mark.by = player.getUUID();
		mark.move = move;
		mark.at = now;
		return damage * (1.0F + bonus);
	}

	/** What move this hit is: the boxer's punch, the hold combo's blow, or else the kind of harm (and what carried it). */
	private static String move(ServerPlayer player, DamageSource source, boolean boxer) {
		if (boxer) {
			Boxing.Punch punch = Boxing.lastPunch(player);
			return "box:" + (punch == null ? "punch" : punch.name());
		}
		if (ZFighterCombo.running(player)) {
			Integer packed = player.getAttached(ZFighterCombo.MOVE);
			return "combo:" + (packed == null ? 0 : ZFighterCombo.move(packed));
		}
		Entity direct = source.getDirectEntity();
		return source.typeHolder().getRegisteredName() + (direct == null || direct == player ? "" : ":" + direct.getType().getDescriptionId());
	}
}
