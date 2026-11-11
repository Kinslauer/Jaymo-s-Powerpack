package com.jaymospowers.power.web;

import com.jaymospowers.combat.HitStreak;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.23, Kingdoms Rivals patch 9) More for the web slinger, ideas only from the Arkham and Shadow of War games. The
 * web's combo is the hit streak ({@link HitStreak}): blows landed without being hit, kept through a dodge, a vault, a Web
 * Shot or a Yank ({@link HitStreak#kept}). Sneak + attack bare-handed ({@code combat/Takedowns}):
 * <ul>
 * <li><b>Wrap-and-slam:</b> at {@link HitStreak#READY} the foe in front of you is webbed and slammed down, downed; at
 * {@link HitStreak#DOUBLE} the nearest other with him. Spends that much of the streak.</li>
 * <li><b>String up:</b> from a perch ({@link #PERCH_DROP} or more above him), a foe below is snatched up on a line and
 * left hanging from the edge, webbed (no streak needed).</li>
 * </ul>
 * And: a Spider-Sense counter that throws a foe into a wall pins him there in webbing ({@link #thrown}); a plain blow on
 * one already down, mid-streak ({@link #GROUND_FROM}+), finishes him and counts on.
 */
public final class WebTakedowns {
	// ================================================================================================================
	// Tuning
	/** The takedown's reach (blocks), how long the downed stay out (ticks), its blow, the second foe's nearness. */
	static final double REACH = 4.0, SECOND_RANGE = 5.0;
	static final int OUT_TICKS = 400;
	static final float SLAM_DAMAGE = 3.0F;
	/** String up: how far above him you must stand, how far out (flat) he may be. */
	static final double PERCH_DROP = 2.5, PERCH_REACH = 4.0;
	/**
	 * Silent takedown: from behind (within {@code SILENT_REACH}, in the rear {@code SILENT_BEHIND} of his facing: the dot
	 * of his facing with the way to you below it), a foe not after you is muffled and cocooned, down for
	 * {@code SILENT_OUT_TICKS}, with no sound and no hit.
	 */
	static final double SILENT_REACH = 2.6, SILENT_BEHIND = -0.25;
	static final int SILENT_OUT_TICKS = 600, SILENT_WEB_TICKS = 600;
	/** A thrown foe that strikes a wall within this long (ticks) is pinned; pinned (stunned) this long. */
	static final int THROWN_TICKS = 20, PIN_TICKS = 100;
	/** A plain blow finishes one already down once the streak's this long. */
	static final int GROUND_FROM = 3;
	// ================================================================================================================

	private record Throw(UUID by, long until, Vec3 dir) {
	}

	private static final Map<LivingEntity, Throw> THROWN = new WeakHashMap<>();

	private WebTakedowns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!THROWN.isEmpty()) {
				tickThrown();
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server) || player.isShiftKeyDown()
				|| !player.getMainHandItem().isEmpty() || !Powers.has(player, WebPower.INSTANCE) || !(entity instanceof LivingEntity body)
				|| body instanceof Player || !body.isAlive() || !Finishers.helpless(body) || Finishers.targeted(body) || HitStreak.count(player) < GROUND_FROM) {
				return InteractionResult.PASS;
			}
			// (one already down, mid-streak: finished in passing, and the streak counts on)
			Vec3 at = body.getBoundingBox().getCenter();
			Downed.execute(server, body, sp);
			HitStreak.add(sp);
			server.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.3, 0.2, 0.3, 0.05);
			server.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
			return InteractionResult.SUCCESS;
		});
	}

	/** Sneak + attack at {@code target} (from {@code combat/Takedowns}). (0.21.24) True if it went. */
	public static boolean takedown(ServerPlayer player, LivingEntity target) {
		Vec3 flat = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (player.onGround() && player.getY() - target.getY() >= PERCH_DROP && flat.length() <= PERCH_REACH + target.getBbWidth() * 0.5) {
			stringUp(player, target, flat);
			return true;
		}
		if (silent(player, target)) {
			return true;
		}
		if (target.distanceTo(player) - target.getBbWidth() * 0.5 > REACH) {
			return false;
		}
		int streak = HitStreak.count(player);
		if (streak < HitStreak.READY) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.takedown.short", HitStreak.READY).withStyle(ChatFormatting.GRAY));
			return false;
		}
		boolean two = streak >= HitStreak.DOUBLE;
		HitStreak.spend(player, two ? HitStreak.DOUBLE : HitStreak.READY);
		wrapAndSlam(player, target);
		if (two) {
			LivingEntity second = second(player, target);
			if (second != null) {
				wrapAndSlam(player, second);
			}
		}
		HitStreak.kept(player);
		return true;
	}

	/** Sneaking up unseen behind a foe that is not after you: muffled and cocooned, no sound, no hit streak needed. */
	private static boolean silent(ServerPlayer player, LivingEntity target) {
		if (target instanceof Player || !target.isAlive() || Finishers.helpless(target) || WebHanging.webbedUp(target)
			|| target instanceof Mob mob && mob.getTarget() == player
			|| target.distanceTo(player) - target.getBbWidth() * 0.5 > SILENT_REACH) {
			return false;
		}
		Vec3 facing = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		Vec3 toYou = player.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		if (facing.lengthSqr() < 1.0E-4 || toYou.lengthSqr() < 1.0E-4 || Math.abs(player.getY() - target.getY()) > 2.0
			|| facing.normalize().dot(toYou.normalize()) > SILENT_BEHIND) {
			return false;
		}
		ServerLevel level = player.level();
		boolean dark = WebSymbiote.suitShown(player);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
		WebTraps.ensnare(level, target, WebBall.SLOWNESS_AMPLIFIER, SILENT_WEB_TICKS, dark, true);
		if (!Downed.knockOut(level, target, SILENT_OUT_TICKS, true, "message.jaymospowers.takedown.downed")) {
			DarktideStatus.stun(target, SILENT_OUT_TICKS / 4);
		}
		WebHanging.web(level, target, dark, SILENT_WEB_TICKS);
		WebFear.strungUp(level, target);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(dark ? WebSymbiote.dust(1.0F) : ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 18, 0.3, 0.5, 0.3, 0.03);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return true;
	}

	/** Webbed head to foot, and down into the ground. */
	private static void wrapAndSlam(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		boolean dark = WebSymbiote.suitShown(player);
		WebTraps.ensnare(level, target, WebBall.SLOWNESS_AMPLIFIER, WebBall.SLOWNESS_MAX_TICKS, dark);
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), SLAM_DAMAGE);
		if (target.isAlive() && !Downed.knockOut(level, target, OUT_TICKS, true, "message.jaymospowers.takedown.downed")) {
			DarktideStatus.stun(target, OUT_TICKS / 4);
		}
		move(target, new Vec3(0.0, -0.9, 0.0));
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(dark ? WebSymbiote.dust(1.4F) : ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 30, 0.4, 0.6, 0.4, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.1, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
		com.jaymospowers.core.Shakes.send(player, 0.9F, at);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
	}

	/** Snatched up from below on a line, and left hanging from the edge of your perch. */
	private static void stringUp(ServerPlayer player, LivingEntity target, Vec3 flat) {
		ServerLevel level = player.level();
		Vec3 dir = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().normalize() : flat.normalize();
		Vec3 anchor = player.position().add(dir.scale(0.45));
		BlockPos block = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
		Vec3 lift = anchor.subtract(target.position()).multiply(0.25, 0.0, 0.25).add(0.0, 1.0, 0.0);
		move(target, lift);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		WebHitches.hitch(level, player, target, anchor, block, Direction.UP);
		WebTraps.ensnare(level, target, WebBall.SLOWNESS_AMPLIFIER, WebBall.SLOWNESS_MAX_TICKS, WebSymbiote.suitShown(player));
		DarktideStatus.stun(target, PIN_TICKS);
		WebFear.strungUp(level, target);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.7F);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		HitStreak.kept(player);
	}

	/** (From {@code WebCounter}) A counter threw {@code target} along {@code push}: if it strikes a wall, it's pinned there. */
	static void thrown(ServerPlayer player, LivingEntity target, Vec3 push) {
		Vec3 dir = push.multiply(1.0, 0.0, 1.0);
		if (dir.lengthSqr() < 0.09 || target instanceof Player) {
			return;
		}
		THROWN.put(target, new Throw(player.getUUID(), player.level().getGameTime() + THROWN_TICKS, dir.normalize()));
	}

	private static void tickThrown() {
		for (Map.Entry<LivingEntity, Throw> e : java.util.List.copyOf(THROWN.entrySet())) {
			LivingEntity target = e.getKey();
			Throw t = e.getValue();
			if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || level.getGameTime() > t.until()) {
				THROWN.remove(target);
				continue;
			}
			if (!target.horizontalCollision) {
				continue;
			}
			THROWN.remove(target);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.by());
			if (player == null || player.level() != level) {
				continue;
			}
			// (into the wall: webbed to it where he struck)
			Vec3 center = target.getBoundingBox().getCenter();
			Vec3 anchor = center.add(t.dir().scale(target.getBbWidth() * 0.5 + 0.05));
			BlockPos wall = BlockPos.containing(center.add(t.dir().scale(target.getBbWidth() * 0.5 + 0.5)));
			if (level.getBlockState(wall).isAir()) {
				continue;
			}
			move(target, Vec3.ZERO);
			WebHitches.hitch(level, player, target, anchor, wall, Direction.getApproximateNearest(t.dir()).getOpposite());
			WebTraps.ensnare(level, target, WebBall.SLOWNESS_AMPLIFIER, WebBall.SLOWNESS_MAX_TICKS, WebSymbiote.suitShown(player));
			DarktideStatus.stun(target, PIN_TICKS);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, anchor.x, anchor.y, anchor.z, 24, 0.3, 0.5, 0.3, 0.05);
			level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
		}
	}

	/** The nearest other foe near {@code first}, for a takedown at 16. */
	private static @Nullable LivingEntity second(ServerPlayer player, LivingEntity first) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(SECOND_RANGE),
			e -> e != first && e != player && e.isAlive() && !Finishers.helpless(e) && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))) {
			double d = e.distanceToSqr(first);
			if (d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	private static void move(LivingEntity target, Vec3 velocity) {
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		target.syncVelocity = true;
	}
}
