package com.jaymospowers.core;

import com.jaymospowers.power.web.WebSymbiote;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What fire does to whatever it reaches. Every fire effect calls this on everything in its area, so flames behave the
 * same everywhere: creatures, players, corpses, dropped items, armor stands and projectiles are set alight (unless
 * fire-immune; items and armor stands then burn up as in vanilla, a flaming arrow sets alight what it hits), a
 * creeper's fuse is lit as with flint and steel, a TNT minecart is set off and primed TNT burns down faster. A bonded
 * symbiote within {@code SYMBIOTE_RANGE} of the flames flinches as if its host were burning.
 */
public final class Flames {
	/** How close (blocks) a symbiote has to be to the flames to flinch. */
	public static final double SYMBIOTE_RANGE = 4.0;
	/** Primed TNT's fuse is cut to this share of what's left, but never below {@code TNT_MIN_FUSE_TICKS}. */
	static final double TNT_FUSE_SHARE = 0.5;
	static final int TNT_MIN_FUSE_TICKS = 2;

	private Flames() {
	}

	/** Fire reaches {@code entity}, set by {@code by} (if anyone): see the class. True if it did anything. */
	public static boolean touch(ServerLevel level, Entity entity, int seconds, @Nullable Entity by) {
		boolean done = ignite(level, entity, seconds, by);
		if (done) {
			scare(level, entity.getBoundingBox());
		}
		return done;
	}

	/**
	 * Fire fills {@code box}: everything in it that {@code filter} accepts (never {@code by} itself, nor spectators) is
	 * touched, and symbiotes near the box flinch. Returns how many things it touched.
	 */
	public static int area(ServerLevel level, AABB box, int seconds, @Nullable Entity by, Predicate<Entity> filter) {
		int count = 0;
		for (Entity entity : level.getEntities((Entity)null, box, e -> e != by && !e.isSpectator() && e.isAlive() && filter.test(e))) {
			if (ignite(level, entity, seconds, by)) {
				count++;
			}
		}
		scare(level, box);
		return count;
	}

	/** Fire in a ball of {@code radius} around {@code centre} (distance to each thing's nearest point). */
	public static int area(ServerLevel level, Vec3 centre, double radius, int seconds, @Nullable Entity by, Predicate<Entity> filter) {
		double r2 = radius * radius;
		return area(level, new AABB(centre, centre).inflate(radius), seconds, by,
			e -> e.getBoundingBox().distanceToSqr(centre) <= r2 && filter.test(e));
	}

	/** Symbiotes bonded and out within {@code SYMBIOTE_RANGE} of the flames in {@code box} flinch. */
	public static void scare(ServerLevel level, AABB box) {
		AABB near = box.inflate(SYMBIOTE_RANGE);
		for (ServerPlayer player : level.players()) {
			if (near.intersects(player.getBoundingBox())) {
				WebSymbiote.nearFlames(player);
			}
		}
	}

	private static boolean ignite(ServerLevel level, Entity entity, int seconds, @Nullable Entity by) {
		if (entity.isRemoved() || entity.isSpectator()) {
			return false;
		}
		if (entity instanceof MinecartTNT cart) {
			// as when it burns on the rails: a short fuse, then it goes up
			if (!cart.isPrimed()) {
				cart.destroy(level, level.damageSources().onFire());
			}
			return true;
		}
		if (entity instanceof PrimedTnt tnt) {
			int fuse = tnt.getFuse();
			if (fuse > TNT_MIN_FUSE_TICKS) {
				tnt.setFuse(Math.max(TNT_MIN_FUSE_TICKS, (int)(fuse * TNT_FUSE_SHARE)));
			}
			return true;
		}
		boolean done = false;
		if (entity instanceof Creeper creeper && !creeper.isIgnited()) {
			level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.FLINTANDSTEEL_USE, creeper.getSoundSource(), 1.0F,
				level.getRandom().nextFloat() * 0.4F + 0.8F);
			creeper.ignite();
			done = true;
		}
		if (!entity.fireImmune() && seconds > 0) {
			entity.igniteForSeconds(seconds);
			done = true;
		}
		return done;
	}
}
