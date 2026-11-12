package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hive's commanded swarm: {@link #FLYERS} flyers simulated on the server (drawn as motes and miasma from their positions)
 * that share Hive's health pool. Z picks the formation (close in, surround, strafe, scout ahead); B sends the swarm out,
 * again sends one as a living missile (spent, it returns after {@link #MISSILE_RETURN} ticks); sneak + B calls them all
 * back as a shell round Hive that soaks hits. A flyer that is swatted or shot is downed for a while and Hive takes
 * {@link #SHARE} of the blow.
 */
public final class HiveSwarm {
	// ---- tuning ----
	public static final int FLYERS = 5;
	public static final int CLOSE = 0, SURROUND = 1, STRAFE = 2, SCOUT = 3, FORMATIONS = 4;
	public static final double SPEED = 0.75, LEASH = 40.0, FOE_RANGE = 20.0;
	/** Damage per sting in each formation (close in stings often, surround dives, strafe passes). */
	public static final float CLOSE_DAMAGE = 0.8F, SURROUND_DAMAGE = 2.0F, STRAFE_DAMAGE = 1.6F;
	/** Close: ticks between a flyer's stings; surround: the dive's period; ring radius. */
	public static final int CLOSE_EVERY = 15, DIVE_PERIOD = 40, STRAFE_EVERY = 20;
	public static final double SURROUND_RADIUS = 5.0;
	/** Scouts fly this far ahead and mark every foe within SCOUT_SEE for SCOUT_MARK ticks. */
	public static final double SCOUT_AHEAD = 14.0, SCOUT_SEE = 7.0;
	public static final int SCOUT_MARK = 80;
	/** The living missile: speed, reach in ticks, damage, how long before the flyer is back. */
	public static final double MISSILE_SPEED = 1.3;
	public static final int MISSILE_TICKS = 30, MISSILE_RETURN = 200;
	public static final float MISSILE_DAMAGE = 7.0F;
	/** A flyer downed by a swat or an arrow: how long it is out, the blow it counts as, and Hive's share of any blow to a flyer. */
	public static final int DOWN_TICKS = 100;
	public static final float SWAT_DAMAGE = 2.0F, SHARE = 0.5F;
	/** The shell: how much of a blow Hive still takes, and how long the flyer that soaked it is out. */
	public static final float SHELL_SHARE = 0.4F;
	public static final int SHELL_DOWN = 160;
	public static final int AMBER = 0xE0A020, DARK = 0x2A2010;

	private static final class Swarm {
		final Vec3[] pos = new Vec3[FLYERS];
		final Vec3[] dir = new Vec3[FLYERS];
		final long[] backAt = new long[FLYERS];
		final int[] flight = new int[FLYERS];
		final int[] target = new int[FLYERS];
		final long[] hitNext = new long[FLYERS];
		int formation;
		boolean shell, out;
		int quarry = -1;
	}

	private static final Map<UUID, Swarm> SWARMS = new HashMap<>();
	/** Set while Hive is taking its share of a blow to the swarm, so that blow is not soaked a second time. */
	private static boolean passing;

	private HiveSwarm() {
	}

	public static boolean out(ServerPlayer player) {
		Swarm swarm = SWARMS.get(player.getUUID());
		return swarm != null && swarm.out;
	}

	public static void clear(ServerPlayer player) {
		SWARMS.remove(player.getUUID());
	}

	/** Z tap: the next formation. */
	public static void cycle(ServerPlayer player) {
		Swarm swarm = SWARMS.computeIfAbsent(player.getUUID(), k -> fresh(player));
		swarm.formation = (swarm.formation + 1) % FORMATIONS;
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.hive.form." + swarm.formation).withStyle(ChatFormatting.GOLD));
	}

	private static Swarm fresh(ServerPlayer player) {
		Swarm swarm = new Swarm();
		for (int i = 0; i < FLYERS; i++) {
			swarm.pos[i] = player.position().add(0.0, 1.0, 0.0);
			swarm.dir[i] = Vec3.ZERO;
			swarm.target[i] = -1;
		}
		return swarm;
	}

	/** B: the swarm leaves Hive and goes for {@code quarry} (or whatever is near). */
	public static void deploy(ServerPlayer player, @Nullable LivingEntity quarry) {
		Swarm swarm = SWARMS.computeIfAbsent(player.getUUID(), k -> fresh(player));
		swarm.shell = false;
		swarm.out = true;
		swarm.quarry = quarry == null ? -1 : quarry.getId();
		for (int i = 0; i < FLYERS; i++) {
			swarm.pos[i] = player.position().add(0.0, 1.0, 0.0);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, 1.5F, 1.0F);
	}

	/** B again: one flyer streaks at {@code target} (or where the player looks). False when none is free. */
	public static boolean missile(ServerPlayer player, @Nullable LivingEntity target) {
		Swarm swarm = SWARMS.get(player.getUUID());
		long now = player.level().getGameTime();
		if (swarm == null || !swarm.out) {
			return false;
		}
		for (int i = 0; i < FLYERS; i++) {
			if (swarm.backAt[i] <= now && swarm.flight[i] == 0) {
				swarm.pos[i] = player.getEyePosition();
				swarm.dir[i] = player.getLookAngle();
				swarm.flight[i] = MISSILE_TICKS;
				swarm.target[i] = target == null ? -1 : target.getId();
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.2F, 1.6F);
				return true;
			}
		}
		return false;
	}

	/** Sneak + B: the flyers come home as a shell, or fly out again. */
	public static void shell(ServerPlayer player) {
		Swarm swarm = SWARMS.computeIfAbsent(player.getUUID(), k -> fresh(player));
		if (!swarm.out) {
			deploy(player, null);
			swarm.shell = true;
		} else {
			swarm.shell = !swarm.shell;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.hive.shell_" + (swarm.shell ? "on" : "off")).withStyle(ChatFormatting.GOLD));
	}

	/**
	 * A blow of {@code amount} is about to land on Hive. While the shell is up a flyer takes it and Hive only {@link #SHELL_SHARE}
	 * of it. False: cancel the original blow.
	 */
	public static boolean soak(ServerPlayer player, DamageSource source, float amount) {
		Swarm swarm = SWARMS.get(player.getUUID());
		if (passing || swarm == null || !swarm.out || !swarm.shell || amount <= 0.0F || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		long now = player.level().getGameTime();
		for (int i = 0; i < FLYERS; i++) {
			if (swarm.backAt[i] <= now && swarm.flight[i] == 0) {
				swarm.backAt[i] = now + SHELL_DOWN;
				puff(player.level(), swarm.pos[i]);
				pass(player, source, amount * SHELL_SHARE);
				return false;
			}
		}
		swarm.shell = false;
		return true;
	}

	private static void pass(ServerPlayer player, DamageSource source, float amount) {
		passing = true;
		try {
			player.hurtServer(player.level(), source, amount);
		} finally {
			passing = false;
		}
	}

	private static void puff(ServerLevel level, Vec3 at) {
		MagicFx.burst(level, at.x, at.y, at.z, AMBER, 0.6F, 6, 0.2, 0.2, 0.2);
	}

	private static boolean foe(ServerPlayer player, Entity e) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand)
			&& !(e instanceof OwnableEntity pet && pet.getOwner() == player) && !player.isAlliedTo(e) && !e.isPassengerOfSameVehicle(player);
	}

	private static @Nullable LivingEntity quarry(ServerPlayer player, ServerLevel level, Swarm swarm) {
		Entity e = swarm.quarry >= 0 ? level.getEntity(swarm.quarry) : null;
		if (e instanceof LivingEntity living && foe(player, living) && living.distanceTo(player) <= LEASH) {
			return living;
		}
		LivingEntity best = null;
		double near = FOE_RANGE * FOE_RANGE;
		for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FOE_RANGE),
			c -> foe(player, c) && (c instanceof Enemy || c instanceof Mob mob && mob.getTarget() == player))) {
			if (c.distanceToSqr(player) < near) {
				near = c.distanceToSqr(player);
				best = c;
			}
		}
		swarm.quarry = best == null ? -1 : best.getId();
		return best;
	}

	/** Every tick as Hive. */
	public static void tick(ServerPlayer player) {
		Swarm swarm = SWARMS.get(player.getUUID());
		if (swarm == null || !swarm.out) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long t = player.tickCount;
		LivingEntity q = swarm.shell ? null : quarry(player, level, swarm);
		Vec3 look = player.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize();
		for (int i = 0; i < FLYERS; i++) {
			if (swarm.flight[i] > 0) {
				missileStep(player, level, swarm, i, now);
				continue;
			}
			if (swarm.backAt[i] > now) {
				continue;
			}
			Vec3 want = want(player, swarm, i, q, t, look, side);
			Vec3 pos = swarm.pos[i];
			Vec3 step = want.subtract(pos);
			double len = step.length();
			pos = len > SPEED ? pos.add(step.scale(SPEED / len)) : want;
			if (pos.distanceTo(player.position()) > LEASH) {
				pos = player.position().add(0.0, 1.0, 0.0);
			}
			swarm.pos[i] = pos;
			if (!swarm.shell) {
				act(player, level, swarm, i, q, t, now);
				swat(player, level, swarm, i, now);
			} else {
				shot(player, level, swarm, i, now);
			}
			draw(level, pos, t, i);
		}
		if (swarm.formation == SCOUT && !swarm.shell && t % 10 == 0) {
			mark(player, level, swarm, now);
		}
	}

	private static Vec3 want(ServerPlayer player, Swarm swarm, int i, @Nullable LivingEntity q, long t, Vec3 look, Vec3 side) {
		double a = t * 0.3 + i * Math.PI * 2.0 / FLYERS;
		if (swarm.shell) {
			return player.position().add(Math.cos(a) * 1.4, 0.5 + 0.5 * (1 + Math.sin(a * 1.7)) * 0.5 + (i % 3) * 0.35, Math.sin(a) * 1.4);
		}
		if (swarm.formation == SCOUT) {
			Vec3 base = player.getEyePosition().add(look.scale(SCOUT_AHEAD));
			return base.add(side.scale((i - (FLYERS - 1) * 0.5) * 3.0)).add(0.0, Math.sin(t * 0.1 + i) * 1.5, 0.0);
		}
		if (q == null) {
			return player.position().add(Math.cos(a * 0.5) * 2.5, 1.4 + Math.sin(a) * 0.6, Math.sin(a * 0.5) * 2.5);
		}
		Vec3 c = q.getBoundingBox().getCenter();
		switch (swarm.formation) {
			case SURROUND -> {
				double phase = (t + i * (DIVE_PERIOD / FLYERS)) % DIVE_PERIOD;
				double r = phase < 8 ? 0.6 : SURROUND_RADIUS;
				double ang = i * Math.PI * 2.0 / FLYERS + t * 0.02;
				return c.add(Math.cos(ang) * r, 0.5 + Math.sin(i) * 0.4, Math.sin(ang) * r);
			}
			case STRAFE -> {
				Vec3 axis = c.subtract(player.position()).multiply(1.0, 0.0, 1.0);
				axis = axis.lengthSqr() < 1.0E-4 ? look.multiply(1.0, 0.0, 1.0) : axis.normalize();
				Vec3 perp = new Vec3(-axis.z, 0.0, axis.x);
				double p = ((t * 0.7 + i * 5) % 24) - 12;
				return c.add(axis.scale(p)).add(perp.scale((i - (FLYERS - 1) * 0.5) * 0.6)).add(0.0, 0.4, 0.0);
			}
			default -> {
				return c.add(Math.cos(a * 2.0) * 1.1, Math.sin(a) * 0.5, Math.sin(a * 2.0) * 1.1);
			}
		}
	}

	/** What a flyer does on its quarry: sting (close), dive (surround), cut across (strafe). */
	private static void act(ServerPlayer player, ServerLevel level, Swarm swarm, int i, @Nullable LivingEntity q, long t, long now) {
		if (q == null || swarm.formation == SCOUT) {
			return;
		}
		Vec3 c = q.getBoundingBox().getCenter();
		boolean near = swarm.pos[i].distanceToSqr(c) <= 2.25 + q.getBbWidth() * q.getBbWidth();
		float damage = 0.0F;
		switch (swarm.formation) {
			case CLOSE -> damage = near && (t + i * 3) % CLOSE_EVERY == 0 ? CLOSE_DAMAGE : 0.0F;
			case SURROUND -> damage = near && (t + i * (DIVE_PERIOD / FLYERS)) % DIVE_PERIOD == 8 ? SURROUND_DAMAGE : 0.0F;
			default -> damage = near && swarm.hitNext[i] <= now ? STRAFE_DAMAGE : 0.0F;
		}
		if (damage > 0.0F) {
			swarm.hitNext[i] = now + STRAFE_EVERY;
			q.setInvulnerableTime(0);
			q.hurtServer(level, player.damageSources().playerAttack(player), damage);
			q.addEffect(new MobEffectInstance(MobEffects.POISON, 30, 0), player);
			level.playSound(null, q.getX(), q.getY(), q.getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
	}

	/** An enemy next to a flyer swats it down; Hive takes its share. */
	private static void swat(ServerPlayer player, ServerLevel level, Swarm swarm, int i, long now) {
		Vec3 at = swarm.pos[i];
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(0.8), m -> foe(player, m) && m.getTarget() == player)) {
			if (level.getRandom().nextInt(8) == 0) {
				down(player, level, swarm, i, now, level.damageSources().mobAttack(mob), SWAT_DAMAGE);
				return;
			}
		}
		shot(player, level, swarm, i, now);
	}

	/** A flyer in the way of a shot soaks it. */
	private static void shot(ServerPlayer player, ServerLevel level, Swarm swarm, int i, long now) {
		Vec3 at = swarm.pos[i];
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(0.7))) {
			Entity owner = p.getOwner();
			if (owner == player || owner instanceof OwnableEntity pet && pet.getOwner() == player || owner != null && player.isAlliedTo(owner)) {
				continue;
			}
			p.discard();
			down(player, level, swarm, i, now, level.damageSources().generic(), SWAT_DAMAGE);
			return;
		}
	}

	private static void down(ServerPlayer player, ServerLevel level, Swarm swarm, int i, long now, DamageSource source, float amount) {
		swarm.backAt[i] = now + DOWN_TICKS;
		puff(level, swarm.pos[i]);
		pass(player, source, amount * SHARE);
	}

	private static void missileStep(ServerPlayer player, ServerLevel level, Swarm swarm, int i, long now) {
		Vec3 from = swarm.pos[i];
		Entity target = swarm.target[i] >= 0 ? level.getEntity(swarm.target[i]) : null;
		Vec3 dir = swarm.dir[i];
		if (target instanceof LivingEntity living && living.isAlive()) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(from).normalize();
			dir = dir.scale(0.6).add(to.scale(0.4)).normalize();
		}
		swarm.dir[i] = dir;
		Vec3 next = from.add(dir.scale(MISSILE_SPEED));
		swarm.pos[i] = next;
		swarm.flight[i]--;
		if (swarm.flight[i] % 2 == 0) {
			MagicFx.beam(level, from, next, 0.06F, AMBER, 4);
		}
		boolean spent = swarm.flight[i] <= 0 || !level.getBlockState(BlockPos.containing(next)).getCollisionShape(level, BlockPos.containing(next)).isEmpty();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(1.0), e -> foe(player, e))) {
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().playerAttack(player), MISSILE_DAMAGE);
			e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(0.5)));
			e.syncVelocity = true;
			spent = true;
			break;
		}
		if (spent) {
			swarm.flight[i] = 0;
			swarm.backAt[i] = now + MISSILE_RETURN;
			MagicFx.blast(level, next, 1.2F, AMBER);
			level.playSound(null, next.x, next.y, next.z, SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
	}

	/** Scouts outline every foe they see, for the player to follow. */
	private static void mark(ServerPlayer player, ServerLevel level, Swarm swarm, long now) {
		for (int i = 0; i < FLYERS; i++) {
			if (swarm.flight[i] > 0 || swarm.backAt[i] > now) {
				continue;
			}
			Vec3 at = swarm.pos[i];
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SCOUT_SEE),
				e -> foe(player, e) && (e instanceof Enemy || e instanceof Mob))) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SCOUT_MARK, 0, false, false), player);
			}
		}
	}

	private static void draw(ServerLevel level, Vec3 at, long t, int i) {
		level.sendParticles(MagicFx.mote(AMBER, 0.5F), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
		if ((t + i) % 2 == 0) {
			level.sendParticles(MagicFx.miasma(DARK, 0.5F), at.x, at.y, at.z, 1, 0.08, 0.08, 0.08, 0.0);
		}
	}

	/** Used by Hive for the formation name in the HUD-less status line. */
	public static int formation(ServerPlayer player) {
		Swarm swarm = SWARMS.get(player.getUUID());
		return swarm == null ? CLOSE : Mth.clamp(swarm.formation, 0, FORMATIONS - 1);
	}
}
