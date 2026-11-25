package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Tendril Lash: the tendrils sweep round you in one wide whip and strike every enemy within {@link #REACH} once. The blow
 * grows with the bond's stage ({@link #DAMAGE}, by {@link WebSymbioteMind#stage}: Attached to Dominant). It costs
 * {@link #COST} satiety and cools down for {@link #COOLDOWN} ticks. On the radial's Lash chip
 * ({@link WebSymbiote#LASH}) and the skill bar ({@link WebBarMoves#LASH}). An enemy is anything hostile, or a creature
 * that has set its sights on you; never another player.
 *
 * <p>This class also keeps the cooldown slots of the strain moves of this patch ({@link #cooling}, {@link #cool}).
 */
public final class WebLash {
	/** The whip reaches this far round you (blocks); each enemy takes the stage's damage (Attached, Integrated, Cooperative, Dominant). */
	public static final double REACH = 4.5;
	static final float[] DAMAGE = {3.0F, 4.5F, 6.5F, 9.0F};
	static final double KNOCKBACK = 0.5, LIFT = 0.15;
	/** Satiety spent, and ticks before the next lash. */
	public static final int COST = 4, COOLDOWN = 140;
	/** The cooldown slots of the moves of this patch (the radial's six abilities and its chips come before them). */
	static final int SLOT_LASH = WebSymbiote.LASH, SLOT_NIGHTSTEP = 14, SLOT_RUPTURE = 15, SLOT_ONSLAUGHT = 16, SLOTS = 18;
	/** The whip's colour, and the screen shake of a lash. */
	static final int INK = 0x2A1238;
	static final float SHAKE = 0.6F;

	private WebLash() {
	}

	/** Whether the lash can go at all now: bonded, awake, the tendrils given, not at the wheel. */
	public static boolean available(ServerPlayer player) {
		return WebSymbiote.isBonded(player) && player.isAlive() && !player.isSpectator() && WebSymbiote.tendrilsUnlocked(player)
			&& !WebSymbiote.driving(player) && !WebSymbioteBond.dormant(player);
	}

	/** The move's cooldown {@code slot} is still running. */
	static boolean cooling(ServerPlayer player, int slot) {
		WebSymbioteState.Cooldowns cooldowns = player.getAttached(WebSymbiote.COOLDOWNS);
		return cooldowns != null && cooldowns.end(slot) > player.level().getGameTime();
	}

	/** Starts {@code slot}'s cooldown, {@code ticks} long. */
	static void cool(ServerPlayer player, int slot, int ticks) {
		WebSymbioteState.Cooldowns cooldowns = player.getAttached(WebSymbiote.COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebSymbioteState.Cooldowns.NONE;
		}
		player.setAttached(WebSymbiote.COOLDOWNS, cooldowns.with(slot, player.level().getGameTime() + ticks, SLOTS));
	}

	/** Takes {@code cost} satiety off (not in creative); false, with its complaint, if it has too little. */
	static boolean pay(ServerPlayer player, int cost) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (player.isCreative()) {
			return true;
		}
		if (state.satiety() < cost) {
			WebSymbiote.nag(player, "weak");
			return false;
		}
		WebSymbiote.set(player, state.withSatiety(state.satiety() - cost));
		return true;
	}

	/** An enemy of yours for a move that strikes "every enemy": hostile, or something with its sights on you. */
	static boolean foe(ServerPlayer player, net.minecraft.world.entity.Entity entity) {
		return entity instanceof LivingEntity living && living != player && living.isAlive() && !living.isSpectator() && !(living instanceof net.minecraft.world.entity.player.Player)
			&& (living instanceof Enemy || living instanceof Mob mob && mob.getTarget() == player);
	}

	/** The lash: every enemy in reach is whipped once. False if it didn't go. */
	public static boolean use(ServerPlayer player) {
		if (!available(player)) {
			return false;
		}
		if (cooling(player, SLOT_LASH)) {
			return false;
		}
		ServerLevel level = player.level();
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
			e -> foe(player, e) && e.distanceTo(player) - e.getBbWidth() * 0.5 <= REACH);
		if (!WebStamina.afford(player, WebStamina.LASH) || !pay(player, COST)) {
			return false;
		}
		WebStamina.spend(player, WebStamina.LASH);
		cool(player, SLOT_LASH, COOLDOWN);
		int stage = WebSymbioteMind.stage(WebSymbiote.state(player).bond());
		float damage = DAMAGE[Math.min(stage, DAMAGE.length - 1)];
		Vec3 shoulder = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
		Vec3 forward = player.getLookAngle().horizontal();
		forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		MagicFx.swing(level, shoulder, forward, side, REACH, 300.0, 0.3F, INK, 10);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, shoulder.x, shoulder.y, shoulder.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, shoulder.x, shoulder.y, shoulder.z, SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.8F);
		for (LivingEntity foe : foes) {
			Vec3 at = foe.getBoundingBox().getCenter();
			MagicFx.stroke(level, shoulder, at, 0.12F, INK);
			foe.hurtServer(level, level.damageSources().playerAttack(player), damage);
			Vec3 away = at.subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? forward : away.normalize();
			foe.push(away.x * KNOCKBACK, LIFT, away.z * KNOCKBACK);
			foe.needsSync = true;
			foe.syncVelocity = true;
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.2);
			level.sendParticles(WebSymbiote.dust(1.2F), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
		}
		Shakes.send(player, SHAKE);
		WebSymbiote.narrate(player, "lash");
		return true;
	}
}
