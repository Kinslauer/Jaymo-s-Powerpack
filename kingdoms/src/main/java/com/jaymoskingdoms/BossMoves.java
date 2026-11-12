package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Bosses act between your blows (fifth edition's legendary actions, Andy's pick D2). A big boss that has taken a few of one
 * player's blows quickly answers with a light extra move of its kind, and gives itself away first so it can be learned:
 * <ul>
 * <li><b>Tail sweep</b> (dragons): it rears and growls, a ring is drawn on the ground round it, and a moment later the tail
 * whips round and everyone on the ground inside the ring is hurt and thrown back. Jump it, or step out.</li>
 * <li><b>Short dash</b> (brutes: the minotaur, kings and warlords, champions, the grizzly): it paws the ground and a line of
 * crits is drawn toward the one who hit it, then it charges that line. Step aside.</li>
 * <li><b>Shriek</b> (casters and the great dead: the necromancer, the Wither, the Warden): it draws breath, a sigil blooms
 * under it, then it screams: everyone near is hurt and their ears ring ({@link Deafened}).</li>
 * </ul>
 * The answer comes on every {@link #HITS}th blow (the one before it is signalled with a low growl) and each boss needs
 * {@link #COOLDOWN} ticks between moves. Holding, netting or stunning the boss in the moment between tell and move cuts it off.
 */
public final class BossMoves {
	/** Blows from one player (within {@link #WINDOW} ticks of each other) that bring an answer. */
	static final int HITS = 4;
	/** Blows further apart than this (ticks) don't add up. */
	static final int WINDOW = 20 * 8;
	/** From the tell to the move (ticks); the least between one move and the next (ticks). */
	static final int TELL = 16, COOLDOWN = 20 * 9;
	/** A move's damage as a share of the boss's own attack, and its least and most. */
	static final float SHARE = 0.5F, LEAST = 3.0F, MOST = 9.0F;
	/** Tail sweep: its radius is this plus the boss's width times the second; how hard it throws. */
	static final double SWEEP_BASE = 3.0, SWEEP_PER_WIDTH = 0.7, SWEEP_THROW = 1.1;
	/** Dash: how long it lasts (ticks), how fast (blocks a tick), and how far it can reach for the one it has marked. */
	static final int DASH_TICKS = 9;
	static final double DASH_SPEED = 1.15, DASH_REACH = 18.0;
	/** Shriek: how far it carries, and how long ears ring (ticks). */
	static final double SHRIEK_RANGE = 9.0;
	static final int SHRIEK_RING = 20 * 4;
	/** The colour of the tells (a pale ember) and of the move itself. */
	static final int TELL_COLOUR = 0xE0B060, MOVE_COLOUR = 0xB04030;

	/** The kinds of move. */
	public enum Family {
		SWEEP, DASH, SHRIEK
	}

	private static final class Count {
		int hits;
		long last, readyAt;
		UUID by;
	}

	private static final class Move {
		final LivingEntity boss;
		final Family family;
		final UUID target;
		final long fireAt;
		long dashUntil;
		Vec3 dir = Vec3.ZERO;
		final List<UUID> struck = new ArrayList<>();

		Move(LivingEntity boss, Family family, UUID target, long fireAt) {
			this.boss = boss;
			this.family = family;
			this.target = target;
			this.fireAt = fireAt;
		}
	}

	private static final Map<LivingEntity, Count> COUNTS = new WeakHashMap<>();
	private static final List<Move> MOVES = new ArrayList<>();

	private BossMoves() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> struck(entity, source, taken));
		ServerTickEvents.END_SERVER_TICK.register(BossMoves::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			COUNTS.clear();
			MOVES.clear();
		});
	}

	/** Which move {@code boss} has, or null: only a boss that is a mob, and not a small one. */
	public static Family family(LivingEntity boss) {
		if (!(boss instanceof Mob) || !Legendary.boss(boss) || boss instanceof Folk f && !f.leader()) {
			return null;
		}
		if (boss instanceof com.jaymoskingdoms.dragons.WindDragon || boss.getType() == net.minecraft.world.entity.EntityTypes.ENDER_DRAGON) {
			return Family.SWEEP;
		}
		if (boss instanceof GreatNecromancer || boss.getType() == net.minecraft.world.entity.EntityTypes.WITHER
			|| boss.getType() == net.minecraft.world.entity.EntityTypes.WARDEN || boss.getType() == net.minecraft.world.entity.EntityTypes.ELDER_GUARDIAN) {
			return Family.SHRIEK;
		}
		return Family.DASH;
	}

	private static void struck(LivingEntity boss, DamageSource source, float taken) {
		if (taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || !boss.isAlive() || !(boss.level() instanceof ServerLevel level)) {
			return;
		}
		Family family = family(boss);
		if (family == null) {
			return;
		}
		long now = level.getGameTime();
		Count c = COUNTS.computeIfAbsent(boss, b -> new Count());
		if (now < c.readyAt || pending(boss)) {
			return;
		}
		if (c.by == null || !c.by.equals(player.getUUID()) || now - c.last > WINDOW) {
			c.hits = 0;
		}
		c.by = player.getUUID();
		c.last = now;
		c.hits++;
		if (c.hits == HITS - 1) {
			// it draws breath: the next blow is answered
			level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 0.7F, 0.6F);
		} else if (c.hits >= HITS) {
			c.hits = 0;
			c.readyAt = now + COOLDOWN;
			tell(level, new Move(boss, family, player.getUUID(), now + TELL));
		}
	}

	private static boolean pending(LivingEntity boss) {
		for (Move m : MOVES) {
			if (m.boss == boss) {
				return true;
			}
		}
		return false;
	}

	/** The tell: what the move is, drawn and heard before it lands. */
	private static void tell(ServerLevel level, Move move) {
		LivingEntity boss = move.boss;
		MOVES.add(move);
		switch (move.family) {
			case SWEEP -> {
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.2F, 0.7F);
				Fx.sigil(level, boss.position().add(0.0, 0.1, 0.0), (float)(sweepRadius(boss) * 2.0), TELL_COLOUR, TELL);
				Fx.ring(level, boss.position(), (float)sweepRadius(boss) * 0.6F, TELL_COLOUR);
			}
			case DASH -> {
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 0.8F);
				Vec3 dir = dashLine(level, move);
				for (int i = 1; i <= 12; i++) {
					Vec3 p = boss.position().add(dir.scale(i * 1.2));
					level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.15, p.z, 2, 0.1, 0.0, 0.1, 0.0);
				}
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, boss.getX(), boss.getY() + 0.1, boss.getZ(), 6, boss.getBbWidth() * 0.4, 0.0, boss.getBbWidth() * 0.4, 0.02);
			}
			case SHRIEK -> {
				level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.2F, 0.9F);
				Fx.sigil(level, boss.position().add(0.0, 0.1, 0.0), (float)SHRIEK_RANGE, TELL_COLOUR, TELL);
			}
		}
	}

	static double sweepRadius(LivingEntity boss) {
		return SWEEP_BASE + boss.getBbWidth() * SWEEP_PER_WIDTH;
	}

	/** The flat direction from the boss to the one it marked (the way it faces when there's none). */
	private static Vec3 dashLine(ServerLevel level, Move move) {
		LivingEntity boss = move.boss;
		Vec3 dir = boss.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (level.getPlayerByUUID(move.target) instanceof ServerPlayer p && p.isAlive()) {
			dir = p.position().subtract(boss.position()).multiply(1.0, 0.0, 1.0);
		}
		return dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
	}

	private static float damage(LivingEntity boss) {
		double attack = boss.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? boss.getAttributeValue(Attributes.ATTACK_DAMAGE) : 6.0;
		return (float)Math.max(LEAST, Math.min(MOST, attack * SHARE));
	}

	/** Whether {@code boss} can't act just now: held, netted, charmed, or stunned. */
	private static boolean cut(LivingEntity boss) {
		return HeldFast.locked(boss) || Nets.netted(boss) || Charm.charmed(boss) || PowersCompat.stunned(boss);
	}

	private static void tick(MinecraftServer server) {
		if (MOVES.isEmpty()) {
			return;
		}
		for (Iterator<Move> it = MOVES.iterator(); it.hasNext();) {
			Move m = it.next();
			if (!m.boss.isAlive() || m.boss.isRemoved() || !(m.boss.level() instanceof ServerLevel level) || cut(m.boss)) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (m.dashUntil > 0L) {
				if (now >= m.dashUntil || !dash(level, m)) {
					it.remove();
				}
				continue;
			}
			if (now < m.fireAt) {
				continue;
			}
			switch (m.family) {
				case SWEEP -> {
					sweep(level, m.boss);
					it.remove();
				}
				case SHRIEK -> {
					shriek(level, m.boss);
					it.remove();
				}
				case DASH -> {
					m.dir = dashLine(level, m);
					m.dashUntil = now + DASH_TICKS;
					level.playSound(null, m.boss.getX(), m.boss.getY(), m.boss.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.2F, 0.8F);
				}
			}
		}
	}

	private static DamageSource blow(ServerLevel level, LivingEntity boss) {
		return level.damageSources().mobAttack(boss);
	}

	private static void sweep(ServerLevel level, LivingEntity boss) {
		double r = sweepRadius(boss);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.6F, 0.6F);
		Fx.ring(level, boss.position(), (float)r, MOVE_COLOUR);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(r, 1.5, r),
			p -> p.isAlive() && !p.isSpectator() && p.distanceTo(boss) <= r + p.getBbWidth() * 0.5)) {
			// over it: in the air above the tail, a jump clears it
			if (!p.onGround() && p.getY() > boss.getY() + 0.6) {
				continue;
			}
			if (p.hurtServer(level, blow(level, boss), damage(boss))) {
				Vec3 away = p.position().subtract(boss.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				p.setDeltaMovement(p.getDeltaMovement().add(away.x * SWEEP_THROW, 0.35, away.z * SWEEP_THROW));
				p.syncVelocity = true;
			}
		}
	}

	/** One tick of the charge; false once it has hit something solid or the one marked. */
	private static boolean dash(ServerLevel level, Move m) {
		LivingEntity boss = m.boss;
		Vec3 v = m.dir.scale(DASH_SPEED);
		boss.setDeltaMovement(v.x, Math.min(boss.getDeltaMovement().y, 0.0), v.z);
		boss.syncVelocity = true;
		boss.syncVelocity = true;
		level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.1, boss.getZ(), 3, boss.getBbWidth() * 0.3, 0.0, boss.getBbWidth() * 0.3, 0.02);
		boolean hit = false;
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(0.6), p -> p.isAlive() && !p.isSpectator())) {
			if (m.struck.contains(p.getUUID())) {
				continue;
			}
			m.struck.add(p.getUUID());
			if (p.hurtServer(level, blow(level, boss), damage(boss))) {
				p.setDeltaMovement(p.getDeltaMovement().add(m.dir.x * 0.9, 0.3, m.dir.z * 0.9));
				p.syncVelocity = true;
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.7F);
				hit = true;
			}
		}
		return !hit && !boss.horizontalCollision;
	}

	private static void shriek(ServerLevel level, LivingEntity boss) {
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4F, 0.9F);
		Fx.blast(level, new Vec3(boss.getX(), boss.getY(0.6), boss.getZ()), (float)SHRIEK_RANGE * 0.6F, MOVE_COLOUR);
		Fx.ring(level, boss.position(), (float)SHRIEK_RANGE, MOVE_COLOUR);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(SHRIEK_RANGE), p -> p.isAlive() && !p.isSpectator())) {
			Deafened.ring(p, SHRIEK_RING);
			p.hurtServer(level, blow(level, boss), damage(boss));
		}
	}
}
