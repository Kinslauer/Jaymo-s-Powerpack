package com.jaymospowers.power.ember;

import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Cluster shells (Andy's Awakening pick: "Ember, or Pocket Arsenal's cannon battery"; built for Ember). Let Kindle go at full
 * heat while sneaking and the heat leaves as {@link #SHELLS} lobbed shells instead of the one fireball. Each shell bursts at the
 * top of its fall (or on what it strikes) into {@link #BOMBLETS} bomblets, and each bomblet leaves the ground burning for
 * {@link #BURN_TICKS} ticks: a burning patch that sets alight and hurts the foes who stand in it. No fire blocks are lit, so the
 * ground is scorched, not set ablaze.
 */
public final class EmberShells {
	// ---- tuning ----
	static final int SHELLS = 3, BOMBLETS = 6;
	/** The lob: speed (blocks a tick), the spread between shells (radians), the pull down a tick. */
	static final double SPEED = 1.1, SPREAD = 0.18, GRAVITY = 0.05;
	/** A shell splits when it starts to fall and is this close above the ground (blocks), or when it strikes. */
	static final double SPLIT_HEIGHT = 5.0;
	/** A bomblet's hit: radius (blocks) and damage, and the seconds it sets a foe alight. */
	static final double BOMB_RADIUS = 2.0;
	static final float BOMB_DAMAGE = 4.0F;
	static final int BOMB_BURN = 3;
	/** The burning patch: how long (ticks), its radius (blocks), damage to a foe in it each second. */
	static final int BURN_TICKS = 100;
	static final double PATCH_RADIUS = 1.6;
	static final float PATCH_DAMAGE = 1.0F;
	/** The longest a shell or bomblet flies (ticks). */
	static final int LIFE = 100;
	static final int COLOUR = 0xFF7A1A, GLOW = 0xFFC060;

	private static final class Shot {
		final ServerPlayer owner;
		final ServerLevel level;
		final boolean bomblet;
		Vec3 pos, vel;
		int age;

		Shot(ServerPlayer owner, ServerLevel level, boolean bomblet, Vec3 pos, Vec3 vel) {
			this.owner = owner;
			this.level = level;
			this.bomblet = bomblet;
			this.pos = pos;
			this.vel = vel;
		}
	}

	private record Patch(ServerPlayer owner, ServerLevel level, Vec3 at, long until) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static final List<Patch> PATCHES = new ArrayList<>();

	private EmberShells() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EmberShells::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SHOTS.clear();
			PATCHES.clear();
		});
	}

	/** Lobs the shells from {@code from} along {@code player}'s look. */
	static void lob(ServerPlayer player, Vec3 from) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		// lobbed: tipped up a little
		Vec3 base = new Vec3(look.x, look.y + 0.35, look.z).normalize().scale(SPEED);
		for (int i = 0; i < SHELLS; i++) {
			double turn = (i - (SHELLS - 1) / 2.0) * SPREAD;
			double cos = Math.cos(turn), sin = Math.sin(turn);
			SHOTS.add(new Shot(player, level, false, from, new Vec3(base.x * cos - base.z * sin, base.y, base.x * sin + base.z * cos)));
		}
		EmberHeat.cast(player);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		List<Shot> born = new ArrayList<>();
		Iterator<Shot> it = SHOTS.iterator();
		while (it.hasNext()) {
			Shot s = it.next();
			if (s.owner.isRemoved() || s.level != s.owner.level() || ++s.age > LIFE) {
				it.remove();
				continue;
			}
			s.vel = s.vel.add(0.0, -GRAVITY, 0.0);
			Vec3 next = s.pos.add(s.vel);
			BlockHitResult hit = s.level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, s.owner));
			boolean struck = hit.getType() != HitResult.Type.MISS
				|| !s.level.getEntitiesOfClass(LivingEntity.class, new AABB(s.pos, next).inflate(0.3), e -> FireKit.foe(s.owner, e)).isEmpty();
			s.pos = hit.getType() == HitResult.Type.MISS ? next : hit.getLocation();
			if (s.bomblet) {
				s.level.sendParticles(MagicFx.spark(GLOW, 0.6F), s.pos.x, s.pos.y, s.pos.z, 1, 0.02, 0.02, 0.02, 0.0);
				if (struck) {
					it.remove();
					burst(s);
				}
				continue;
			}
			s.level.sendParticles(MagicFx.mote(COLOUR, 1.2F), s.pos.x, s.pos.y, s.pos.z, 2, 0.05, 0.05, 0.05, 0.0);
			if (struck || s.vel.y < 0.0 && near(s.level, s.pos)) {
				it.remove();
				split(s, born);
			}
		}
		SHOTS.addAll(born);
		Iterator<Patch> pit = PATCHES.iterator();
		while (pit.hasNext()) {
			Patch p = pit.next();
			long now = p.level.getGameTime();
			if (now >= p.until || p.owner.isRemoved()) {
				pit.remove();
				continue;
			}
			if (now % 4 == 0) {
				p.level.sendParticles(MagicFx.mote(COLOUR, 0.9F), p.at.x, p.at.y + 0.1, p.at.z, 3, AbilityPower.size(p.owner, PATCH_RADIUS) * 0.5, 0.05, AbilityPower.size(p.owner, PATCH_RADIUS) * 0.5, 0.01);
			}
			if (now % 20 == 0) {
				for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, new AABB(p.at, p.at).inflate(AbilityPower.size(p.owner, PATCH_RADIUS), 1.0, AbilityPower.size(p.owner, PATCH_RADIUS)),
					e -> FireKit.foe(p.owner, e))) {
					FireKit.hurt(p.owner, e, PATCH_DAMAGE);
					Flames.touch(p.level, e, 2, p.owner);
				}
			}
		}
	}

	/** Within {@link #SPLIT_HEIGHT} of the ground below. */
	private static boolean near(ServerLevel level, Vec3 pos) {
		return level.clip(new ClipContext(pos, pos.add(0.0, -SPLIT_HEIGHT, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()))
			.getType() != HitResult.Type.MISS;
	}

	private static void split(Shot s, List<Shot> born) {
		MagicFx.blast(s.level, s.pos, 0.9F, COLOUR);
		s.level.playSound(null, s.pos.x, s.pos.y, s.pos.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.3F);
		for (int i = 0; i < BOMBLETS; i++) {
			double a = Math.PI * 2.0 * i / BOMBLETS + s.level.getRandom().nextDouble() * 0.4;
			double out = 0.18 + s.level.getRandom().nextDouble() * 0.12;
			born.add(new Shot(s.owner, s.level, true, s.pos, new Vec3(Math.cos(a) * out + s.vel.x * 0.3, 0.1, Math.sin(a) * out + s.vel.z * 0.3)));
		}
	}

	private static void burst(Shot s) {
		Vec3 at = s.pos;
		MagicFx.blast(s.level, at, (float)AbilityPower.size(s.owner, BOMB_RADIUS), COLOUR);
		s.level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
		for (LivingEntity e : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(AbilityPower.size(s.owner, BOMB_RADIUS)),
			e -> FireKit.foe(s.owner, e) && e.position().distanceToSqr(at) <= AbilityPower.size(s.owner, BOMB_RADIUS) * AbilityPower.size(s.owner, BOMB_RADIUS) * 1.5)) {
			FireKit.hurt(s.owner, e, BOMB_DAMAGE);
			Flames.touch(s.level, e, BOMB_BURN, s.owner);
		}
		MagicFx.pool(s.level, at, (float)AbilityPower.size(s.owner, PATCH_RADIUS), COLOUR, BURN_TICKS);
		PATCHES.add(new Patch(s.owner, s.level, at, s.level.getGameTime() + BURN_TICKS));
	}
}
