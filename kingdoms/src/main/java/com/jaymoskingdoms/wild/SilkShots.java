package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.Hearing;
import com.jaymoskingdoms.PowersCompat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (medieval picks, spider nests) Silk thrown through the air: a netcaster's net (de-netcaster) and the brood queen's spit
 * (de-broodqueen). Each flies at the spot its thrower aimed at, slow enough to see coming and step out of, and wraps
 * whoever it meets (Jaymo's Powers' Webbed through {@link PowersCompat#web}, so many layers at once; without Powers a
 * cobweb at their feet for a while). Spiders and their queen pass through their own silk.
 */
public final class SilkShots {
	/** How long (ticks) a shot may fly before it falls apart. */
	static final int MOST_TICKS = 60;

	/** A shot in flight: where it is, where it's going, how fast, how wide it catches, and what it does. */
	private static final class Shot {
		final ServerLevel level;
		final LivingEntity by;
		final Vec3 aim;
		Vec3 at;
		final double speed, catchRadius;
		final int layers, holdTicks;
		final boolean net;
		int age;

		Shot(ServerLevel level, LivingEntity by, Vec3 from, Vec3 aim, double speed, double catchRadius, int layers, int holdTicks, boolean net) {
			this.level = level;
			this.by = by;
			this.at = from;
			this.aim = aim;
			this.speed = speed;
			this.catchRadius = catchRadius;
			this.layers = layers;
			this.holdTicks = holdTicks;
			this.net = net;
		}
	}

	private static final List<Shot> SHOTS = new ArrayList<>();

	private SilkShots() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/**
	 * {@code by} throws silk from {@code from} at {@code aim}: {@code speed} blocks a tick, catching what comes within
	 * {@code catchRadius}, wrapping it in {@code layers} layers that hold {@code holdTicks}. {@code net}: spread wide as it
	 * flies (a net) rather than a glob (spit).
	 */
	public static void loose(ServerLevel level, LivingEntity by, Vec3 from, Vec3 aim, double speed, double catchRadius, int layers, int holdTicks,
			boolean net) {
		SHOTS.add(new Shot(level, by, from, aim, speed, catchRadius, layers, holdTicks, net));
		level.playSound(null, from.x, from.y, from.z, net ? SoundEvents.COBWEB_PLACE : SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.2F, net ? 0.6F : 0.5F);
		Hearing.noise(level, from, 10.0, null, Hearing.Kind.ACTION);
	}

	private static void tick() {
		for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
			Shot shot = it.next();
			if (++shot.age > MOST_TICKS || !shot.by.isAlive() && shot.age > 2) {
				it.remove();
				continue;
			}
			Vec3 to = shot.aim.subtract(shot.at);
			double left = to.length();
			Vec3 next = left <= shot.speed ? shot.aim : shot.at.add(to.scale(shot.speed / left));
			// a wall stops it where it meets it
			var clip = shot.level.clip(new ClipContext(shot.at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot.by));
			boolean landed = clip.getType() != HitResult.Type.MISS || next.equals(shot.aim);
			if (clip.getType() != HitResult.Type.MISS) {
				next = clip.getLocation();
			}
			draw(shot, shot.at, next);
			shot.at = next;
			if (wrap(shot) || landed) {
				splat(shot);
				it.remove();
			}
		}
	}

	/** Wraps whoever the shot is touching now; true if it caught anyone. */
	private static boolean wrap(Shot shot) {
		boolean caught = false;
		for (LivingEntity who : shot.level.getEntitiesOfClass(LivingEntity.class,
				new net.minecraft.world.phys.AABB(shot.at, shot.at).inflate(shot.catchRadius), e -> e.isAlive() && e != shot.by && !kin(e))) {
			web(shot.level, who, shot.layers, shot.holdTicks, shot.by);
			caught = true;
		}
		return caught;
	}

	/** Spiders, and their queen, are never caught in their own silk. */
	public static boolean kin(LivingEntity e) {
		return e instanceof Spider || e instanceof com.jaymoskingdoms.wild.nest.BroodQueen || e.isSpectator();
	}

	/** {@code who} wrapped in {@code layers} of web for {@code ticks} by {@code by}: Powers' Webbed, else a cobweb at their feet. */
	public static void web(ServerLevel level, LivingEntity who, int layers, int ticks, LivingEntity by) {
		if (!PowersCompat.web(who, ticks, by, layers)) {
			SpiderKinds.cobweb(level, who.blockPosition(), ticks);
		}
		if (by instanceof Mob mob && mob.getTarget() == null) {
			mob.setTarget(who);
		}
	}

	private static void draw(Shot shot, Vec3 from, Vec3 to) {
		int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 3.0));
		for (int i = 0; i < steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps);
			if (shot.net) {
				// a net: spread wide across its flight
				shot.level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 3, 0.45, 0.45, 0.45, 0.0);
			} else {
				shot.level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}

	private static void splat(Shot shot) {
		Vec3 p = shot.at;
		shot.level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, shot.net ? 24 : 10, 0.6, 0.4, 0.6, 0.02);
		shot.level.playSound(null, BlockPos.containing(p), SoundEvents.COBWEB_HIT, SoundSource.HOSTILE, 1.0F, 0.7F);
	}
}
