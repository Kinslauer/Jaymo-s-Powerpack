package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.293) Andy, retiring the folk: "all mca please." A realm's archers on its walls, towers and watchtowers (the
 * keep's posts, {@link KingdomEvents#spawnArcher}) are MCA archers ({@link #posted}, {@code McaBridge.convertGuard};
 * new ones are its villagers taken on, {@link #enlist}). MCA's archers kite and keep their distance, which would walk
 * them off a wall, so a posted one is held to it ({@link #step}): strayed further than {@link #LEASH} from the post, it
 * walks back, unless a foe is on it.
 */
final class WallArchers {
	/** How far from his post a wall archer may stray (blocks), and how close a foe has to be for him to fight it where he is. */
	static final double LEASH = 3.0, CLOSE_FOE = 3.0;
	/** How fast he goes back. */
	static final double RETURN_SPEED = 0.8;

	private WallArchers() {
	}

	/** {@code folk} is an archer posted on one of its realm's keep's posts. */
	static boolean posted(ServerLevel level, Folk folk) {
		if (folk.role() != Folk.ARCHER || folk.isPassenger() || Cavalry.mounted(folk)) {
			return false;
		}
		Castle.Keep keep = Factions.keep(level.getServer(), folk.factionId());
		return keep != null && keep.posts().contains(folk.getHomePosition());
	}

	/** One of {@code keep}'s posts with nobody on it (folk or villager), or any of them. */
	static BlockPos freePost(ServerLevel level, Castle.Keep keep) {
		for (BlockPos post : keep.posts()) {
			if (Workers.near(level, new AABB(post).inflate(2.0), w -> w.isAlive() && w.role() == Folk.ARCHER).isEmpty()) {
				return post;
			}
		}
		return keep.posts().get(level.getRandom().nextInt(keep.posts().size()));
	}

	/** With MCA, one of {@code f}'s villagers made an archer on a free post of {@code keep}, counted in its roster; null if none could be had. */
	static @Nullable Worker enlist(ServerLevel level, Faction f, Castle.Keep keep) {
		BlockPos post = freePost(level, keep);
		Worker w = McaCompat.enlist(level, f, Folk.ARCHER, post, post, 1);
		if (w != null) {
			w.mob().snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			Factions.update(level.getServer(), f.id(), x -> x.withHoldings(x.holdings().adjust(Folk.ARCHER, 1)));
		}
		return w;
	}

	/**
	 * A villager wall archer's step ({@code Workers.step}): true while he's walking back to his post (in place of his
	 * brain), false to let MCA's archer brain shoot from it.
	 */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (mob instanceof Folk || !mob.hasHome() || mob.isSleeping() || mob.isPassenger() || McaCompat.busy(mob) || Downed.isDowned(mob)) {
			return false;
		}
		BlockPos post = mob.getHomePosition();
		if (mob.position().closerThan(net.minecraft.world.phys.Vec3.atBottomCenterOf(post), LEASH)) {
			return false;
		}
		LivingEntity foe = Rulers.target(mob);
		if (foe != null && foe.isAlive() && mob.distanceTo(foe) <= CLOSE_FOE) {
			return false;
		}
		if (mob.getNavigation().isDone() || mob.tickCount % 20 == 0) {
			mob.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
		}
		return true;
	}
}
