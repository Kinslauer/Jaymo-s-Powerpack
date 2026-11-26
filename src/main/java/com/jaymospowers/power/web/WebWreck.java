package com.jaymospowers.power.web;

import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wrecking ball: with both swing webs stuck near each other in the ground, a punch gathers the loose debris around them (dropped
 * items and falling blocks within {@link #GATHER}; a corpse near adds its weight but is left lying) into a ball held on both
 * lines ({@link WebPose#WRECK_SWING}), and swings it round you. It knocks foes down, swats arrows and other missiles out of the air
 * ({@link WebWhip}'s sweep does the same for a creature), and letting go of the punch throws it along your look to smash where it
 * lands. Needs {@link #LEAST} pieces. Sent as a {@link WebWhip.Hold} of mode {@link WebWhip#BALL}.
 */
public final class WebWreck {
	/** How far from the webs debris is gathered (blocks), the fewest pieces that make a ball, and the most that count. */
	static final double GATHER = 7.0;
	static final int LEAST = 2, MOST = 24;
	/** A corpse near counts for this many pieces of weight. */
	static final int CORPSE_WEIGHT = 4;
	/** The swing: radians a tick round you, how far out and how high (blocks), the longest it is held (ticks). */
	static final double STEP = 0.45, RADIUS = 3.2, HEIGHT = 1.1;
	static final int HOLD_TICKS = 120, COOLDOWN = 60;
	/** The ball's reach (blocks), what a hit does (a base and more per piece, capped), the shove, and the wait before the same foe is hit again (ticks). */
	static final double HIT_REACH = 1.5, PUSH = 1.0, LIFT = 0.4;
	static final float HIT_BASE = 3.0F, HIT_PER_PIECE = 0.25F, HIT_MOST = 9.0F;
	static final int HIT_GAP = 8;
	/** Swatting a missile: how close it must come to the ball (blocks). */
	static final double SWAT_REACH = 2.0;
	/** The throw: speed (blocks/tick), gravity, the longest flight (ticks), its damage (a base and more per piece) and reach. */
	static final double THROW_SPEED = 1.7, THROW_GRAVITY = 0.03, THROW_REACH = 1.8;
	static final int FLIGHT_TICKS = 30;
	static final float THROW_BASE = 6.0F, THROW_PER_PIECE = 0.4F;

	private static final class Ball {
		final int pieces;
		final BlockState look;
		final long start;
		double angle;
		Vec3 at;
		Vec3 velocity;
		boolean thrown;
		long flightStart;
		final Map<Integer, Long> hit = new HashMap<>();

		Ball(int pieces, BlockState look, long start, double angle) {
			this.pieces = pieces;
			this.look = look;
			this.start = start;
			this.angle = angle;
		}

		/** Where it is going round {@code player}: out at the end of the lines, rising and dipping a little. */
		Vec3 at(ServerPlayer player) {
			return player.position().add(Math.cos(angle) * RADIUS, HEIGHT + 0.3 * Math.sin(angle * 0.5), Math.sin(angle) * RADIUS);
		}
	}

	private static final Map<UUID, Ball> LIVE = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebWreck() {
	}

	/** A punch with both webs on the ground near each other: gather and swing. */
	static void begin(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !Powers.has(player, WebPower.INSTANCE) || LIVE.containsKey(player.getUUID()) || WebWhip.active(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		WebLine main = player.getAttached(WebPower.line(WebNet.MAIN));
		WebLine off = player.getAttached(WebPower.line(WebNet.OFF));
		if (main == null || off == null || main.kind() != WebLine.SWING || off.kind() != WebLine.SWING || main.entityId() >= 0 || off.entityId() >= 0
			|| main.anchor().distanceTo(off.anchor()) > WebWhip.NEAR || main.anchor().distanceTo(player.position()) > WebWhip.RANGE) {
			return;
		}
		Vec3 centre = main.anchor().add(off.anchor()).scale(0.5);
		AABB area = new AABB(centre, centre).inflate(GATHER);
		int pieces = 0;
		BlockState look = null;
		Set<ItemEntity> items = new HashSet<>(level.getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive()));
		Set<FallingBlockEntity> falling = new HashSet<>(level.getEntitiesOfClass(FallingBlockEntity.class, area, e -> e.isAlive()));
		for (ItemEntity item : items) {
			pieces += Math.max(1, Math.min(4, item.getItem().getCount() / 8 + 1));
		}
		for (FallingBlockEntity block : falling) {
			pieces++;
			if (look == null) {
				look = block.getBlockState();
			}
		}
		int corpses = 0;
		for (net.minecraft.world.entity.Entity e : level.getEntities(player, area, e -> com.jaymospowers.power.gravity.GravityPower.isCorpse(e))) {
			if (!KitchenCompat.isHeavy(e) && !KitchenCompat.isCarried(e)) {
				corpses++;
			}
		}
		if (pieces + corpses * CORPSE_WEIGHT < LEAST) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 2.0F);
			return;
		}
		int total = Math.min(MOST, pieces + corpses * CORPSE_WEIGHT);
		for (ItemEntity item : items) {
			level.sendParticles(ParticleTypes.POOF, item.getX(), item.getY() + 0.2, item.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
			item.discard();
		}
		for (FallingBlockEntity block : falling) {
			block.discard();
		}
		if (look == null) {
			look = level.getBlockState(BlockPos.containing(centre.x, centre.y - 0.5, centre.z));
			if (look.isAir()) {
				look = Blocks.COBBLESTONE.defaultBlockState();
			}
		}
		WebPower.handOverSwings(player);
		ServerPlayNetworking.send(player, new WebWhip.Taken());
		Vec3 away = centre.subtract(player.position());
		Ball ball = new Ball(total, look, now, Math.atan2(away.z, away.x));
		ball.at = ball.at(player);
		LIVE.put(player.getUUID(), ball);
		WebPower.pose(player, WebPose.WRECK_SWING, 1);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, centre.x, centre.y, centre.z, 20, GATHER * 0.2, 0.4, GATHER * 0.2, 0.05);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.3F);
	}

	/** The punch is let go of: the ball is thrown. */
	static void release(ServerPlayer player) {
		Ball ball = LIVE.get(player.getUUID());
		if (ball == null || ball.thrown) {
			return;
		}
		ball.thrown = true;
		ball.flightStart = player.level().getGameTime();
		ball.velocity = player.getLookAngle().scale(THROW_SPEED);
		WebPower.pose(player, WebPose.PIN_THROW, 1);
		WebStrikeLines.clear(player);
		player.removeAttached(WebPower.line(WebNet.MAIN));
		player.removeAttached(WebPower.line(WebNet.OFF));
		player.level().playSound(null, ball.at.x, ball.at.y, ball.at.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	static boolean active(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Ball ball = LIVE.get(player.getUUID());
		if (ball == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - ball.start;
		if (!player.isAlive() || player.isSpectator()) {
			LIVE.remove(player.getUUID());
			return;
		}
		if (ball.thrown) {
			fly(level, player, ball);
			return;
		}
		if (t >= HOLD_TICKS) {
			release(player);
			return;
		}
		ball.angle += STEP;
		ball.at = ball.at(player);
		WebStrikeLines.toPoint(player, ball.at);
		if (t % 4 == 0) {
			WebPower.pose(player, WebPose.WRECK_SWING, 1);
		}
		puff(level, ball, 3);
		// knocks down whatever it meets
		for (LivingEntity e : foes(level, player, ball.at, HIT_REACH)) {
			Long last = ball.hit.get(e.getId());
			if (last != null && t - last < HIT_GAP) {
				continue;
			}
			ball.hit.put(e.getId(), t);
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().mobAttack(player), Math.min(HIT_MOST, HIT_BASE + HIT_PER_PIECE * ball.pieces));
			Vec3 out = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(PUSH);
			WebHaul.push(e, out.add(0.0, LIFT, 0.0));
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		// and swats arrows and the like out of the air
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(ball.at, ball.at).inflate(SWAT_REACH),
			p -> p.isAlive() && p.getOwner() != player && p.position().distanceTo(ball.at) <= SWAT_REACH)) {
			level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
			p.discard();
		}
	}

	/** The thrown ball: on along its arc until it meets a foe or a block, and smashes there. */
	private static void fly(ServerLevel level, ServerPlayer player, Ball ball) {
		long t = level.getGameTime() - ball.flightStart;
		Vec3 next = ball.at.add(ball.velocity);
		BlockHitResult wall = level.clip(new ClipContext(ball.at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean block = wall.getType() == HitResult.Type.BLOCK;
		if (block) {
			next = wall.getLocation();
		}
		ball.at = next;
		ball.velocity = ball.velocity.add(0.0, -THROW_GRAVITY, 0.0);
		puff(level, ball, 6);
		boolean met = block || t >= FLIGHT_TICKS;
		for (LivingEntity e : foes(level, player, ball.at, THROW_REACH)) {
			met = true;
			break;
		}
		if (!met) {
			return;
		}
		LIVE.remove(player.getUUID());
		READY.put(player.getUUID(), level.getGameTime() + COOLDOWN);
		player.removeAttached(WebPower.POSE);
		float damage = THROW_BASE + THROW_PER_PIECE * ball.pieces;
		for (LivingEntity e : foes(level, player, ball.at, THROW_REACH + 1.0)) {
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().mobAttack(player), damage);
			Vec3 flat = e.position().subtract(ball.at).multiply(1.0, 0.0, 1.0);
			Vec3 out = flat.lengthSqr() < 1.0E-4 ? ball.velocity.multiply(1.0, 0.0, 1.0).normalize() : flat.normalize();
			if (e.isAlive()) {
				WebHaul.push(e, out.scale(PUSH).add(0.0, LIFT * 1.5, 0.0));
			}
		}
		Debris.smash(level, ball.at, 1.2 + ball.pieces * 0.03, player);
		level.sendParticles(ParticleTypes.EXPLOSION, ball.at.x, ball.at.y, ball.at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, ball.at.x, ball.at.y, ball.at.z, 18, 0.5, 0.5, 0.5, 0.1);
		level.playSound(null, ball.at.x, ball.at.y, ball.at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.0F);
		com.jaymospowers.core.Shakes.heavy(player, ball.at, 12.0, 3.0F);
	}

	/** The ball's look: rubble of the gathered kind, with web through it. */
	private static void puff(ServerLevel level, Ball ball, int count) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ball.look), ball.at.x, ball.at.y, ball.at.z, count, 0.25, 0.25, 0.25, 0.05);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, ball.at.x, ball.at.y, ball.at.z, 1, 0.2, 0.2, 0.2, 0.0);
	}

	/** Foes near {@code at} that the slinger may hit. */
	private static java.util.List<LivingEntity> foes(ServerLevel level, ServerPlayer player, Vec3 at, double reach) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach), e -> e != player && e.isAlive() && !e.isSpectator()
			&& !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)) && !(e instanceof Player other && !player.canHarmPlayer(other))
			&& e.getBoundingBox().distanceToSqr(at) <= reach * reach);
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		READY.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
		READY.clear();
	}
}
