package com.jaymospowers.power.web;

import com.jaymospowers.core.Agility;
import com.jaymospowers.core.Powers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The web slinger's uptilt (Andy: "web users should get access to the uptilt attack as they get increased agility, their mid
 * air uptilts should involve using the webs to swing them up or pendulum kick the enemy higher, like the insomniac games").
 * <ul>
 * <li><b>Unlocking:</b> a web slinger with {@link #AGILITY_POINTS} Agility (Jaymo's Kingdoms; the points the longbow asks for
 * too) launches with the sneaking blow like anyone with Might's Air Combo ({@link com.jaymospowers.mortal.AirCombo}); that
 * perk still opens it for everyone else. Without Kingdoms Agility is nothing, so web slingers there keep the perk route.</li>
 * <li><b>In the air:</b> each further blow on a launched foe uses the webs to throw it higher than the plain juggle. Where
 * there is a block overhead of you, a web from it swings you up past the foe in a {@link WebPose#SWING_UP_KICK} (the foe goes
 * up with the kick) or in a {@link WebPose#PENDULUM_KICK}, a swing through it that kicks it up and ahead, the two taking turns. With nothing
 * above you but a block over the foe, a web on the foe yanks it up with you ({@link WebPose#WEB_YANK_UP}). In open sky there
 * is nothing to stick to, and the plain juggle goes on. The lines show for a moment.</li>
 * </ul>
 */
public final class WebUptilt {
	/**
	 * The Agility points ({@code 0..100}) a web slinger needs for the launcher. Agility's share is {@code points * 0.0015}
	 * (Kingdoms' {@code Stats}: 100 points are +15%), which is what {@link Agility#factor} reports; 20 is what a longbow asks and
	 * is reached in the first world tier (whose cap is 40).
	 */
	public static final int AGILITY_POINTS = 20;
	/** {@link Agility#factor} at that many points (1 plus the share), the small slack covering float error. */
	public static final double AGILITY_FACTOR = 1.0 + AGILITY_POINTS * 0.0015 - 1.0E-6;

	/** How far up the webs look for something to stick to (blocks), and the least height over the head that counts. */
	static final double SWING_REACH = 24.0, LEAST_OVER = 3.0;
	/** The foe's throw up (blocks/tick) for each move (the plain juggle's is 0.5), and what it gains out along the swing. */
	static final double SWING_UP_BUMP = 0.85, PENDULUM_BUMP = 0.8, PENDULUM_OUT = 0.3, YANK_BUMP = 0.95;
	/** What the slinger gains: lift (blocks/tick) and the share of the way toward the anchor they swing. */
	static final double SWING_UP_LIFT = 0.8, PENDULUM_LIFT = 0.55, PENDULUM_FORWARD = 0.7, YANK_LIFT = 0.6;
	/** What the kick adds to the blow, and how long a layer of web stays on a foe yanked up. */
	static final float KICK_DAMAGE = 2.0F;
	static final int YANK_WEB_TICKS = 30;

	private WebUptilt() {
	}

	/** Whether {@code player} is a web slinger whose Agility opens the launcher. */
	public static boolean unlocked(Player player) {
		return Powers.has(player, WebPower.INSTANCE) && Agility.factor(player) >= AGILITY_FACTOR;
	}

	/**
	 * A juggle blow ({@code juggles} counting this one) from web slinger {@code player} on a foe in the air: one of the web moves
	 * if there is something to stick to. False in open sky or for anyone else, and the plain juggle goes on.
	 */
	public static boolean juggle(ServerLevel level, ServerPlayer player, LivingEntity target, int juggles) {
		if (!Powers.has(player, WebPower.INSTANCE) || WebPower.ragdolled(player) || WebSymbiote.driving(player)) {
			return false;
		}
		Vec3 foe = target.getBoundingBox().getCenter();
		Vec3 toward = foe.subtract(player.position()).multiply(1.0, 0.0, 1.0);
		toward = toward.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : toward.normalize();
		Vec3 over = overhead(player, player.getEyePosition(), toward);
		int side = player.getRandom().nextBoolean() ? 1 : -1;
		if (over != null) {
			boolean pendulum = juggles % 2 == 0;
			Vec3 to = over.subtract(player.position());
			Vec3 flatTo = to.multiply(1.0, 0.0, 1.0);
			flatTo = flatTo.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flatTo.normalize();
			if (pendulum) {
				// swung through the bottom of the arc: past the foe, kicking it up and on ahead
				WebHaul.push(player, toward.scale(PENDULUM_FORWARD).add(0.0, PENDULUM_LIFT, 0.0));
				WebHaul.push(target, toward.scale(PENDULUM_OUT).add(0.0, PENDULUM_BUMP, 0.0));
			} else {
				// hauled straight up the web, past the foe, taking it up with the kick
				WebHaul.push(player, flatTo.scale(0.25).add(0.0, SWING_UP_LIFT, 0.0));
				WebHaul.push(target, new Vec3(0.0, SWING_UP_BUMP, 0.0));
			}
			WebStrikeLines.show(player, over, -1, over.add(-toward.z * 0.6, 0.0, toward.x * 0.6), -1);
			finish(level, player, target, pendulum ? WebPose.PENDULUM_KICK : WebPose.SWING_UP_KICK, side, over);
			return true;
		}
		Vec3 above = overhead(player, foe, toward);
		if (above == null) {
			return false;
		}
		// a web from the block over the foe, and another onto it: it is yanked up, and you with it
		Webbed.wrap(target, YANK_WEB_TICKS);
		WebHaul.push(target, new Vec3(0.0, YANK_BUMP, 0.0));
		WebHaul.push(player, new Vec3(player.getDeltaMovement().x * 0.5, YANK_LIFT, player.getDeltaMovement().z * 0.5));
		WebStrikeLines.show(player, foe, target.getId(), above, -1);
		finish(level, player, target, WebPose.WEB_YANK_UP, side, above);
		return true;
	}

	private static void finish(ServerLevel level, ServerPlayer player, LivingEntity target, int kind, int side, Vec3 anchor) {
		WebPower.pose(player, kind, side);
		player.resetFallDistance();
		if (target.isAlive()) {
			target.setInvulnerableTime(0);
			target.hurtServer(level, player.damageSources().mobAttack(player), KICK_DAMAGE);
		}
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, anchor.x, anchor.y, anchor.z, 6, 0.15, 0.15, 0.15, 0.01);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.9F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 1.1F);
	}

	/** A block to stick a web to over {@code from}, straight up or leaning toward or away from the foe; null in open sky. */
	private static @Nullable Vec3 overhead(ServerPlayer player, Vec3 from, Vec3 toward) {
		double[] lean = {0.0, 0.5, -0.5};
		for (double l : lean) {
			Vec3 end = from.add(toward.x * l * SWING_REACH * 0.5, SWING_REACH, toward.z * l * SWING_REACH * 0.5);
			BlockHitResult hit = WebAnchors.clip(player, from, end);
			if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y - from.y >= LEAST_OVER) {
				return hit.getLocation();
			}
		}
		return null;
	}
}
