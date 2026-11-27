package com.jaymoskingdoms;

import com.jaymoskingdoms.inhabitants.fts.Froglin;
import com.jaymoskingdoms.inhabitants.fts.PlagueBearer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The creatures kept about a goblin village or an orc camp (the froglins; the From the Shadows port's mobs, see
 * licenses/from-the-shadows/): who they take for their own and who for prey, where they stand, and when a camp takes
 * more on. It lives here because a camp's machinery ({@link Camps}, {@link Goblins}, {@link Orcs}) keeps its helpers
 * package-private, the way {@link ScalebaneArcher} reaches them.
 */
public final class CampBeasts {
	private CampBeasts() {
	}

	/** Camp {@code camp} is a goblin village or an orc camp, and still stands (not cleared). */
	public static boolean standing(MinecraftServer server, int camp) {
		return camp >= 0 && (Camps.goblin(server, camp) || Camps.orc(server, camp)) && Factions.get(server, camp) != null && fire(server, camp) != null;
	}

	/** The fire of camp {@code camp}, if it stands. */
	public static @Nullable BlockPos fire(MinecraftServer server, int camp) {
		Camps.CampData data = Camps.data(server, camp);
		return data == null || data.clearedAt() >= 0 ? null : data.center();
	}

	/** What the creatures of camp {@code camp} go for on sight: whatever the camp's own people would. */
	public static boolean prey(MinecraftServer server, int camp, LivingEntity target) {
		return GoblinVillages.is(server, camp) ? Goblins.prey(camp, target) : Orcs.prey(camp, target);
	}

	/** {@code e} is on the camp's side: its folk, its villagers, any goblin or orc, a sellsword or a creature kept like these. */
	public static boolean ally(int camp, LivingEntity e) {
		if (e instanceof Folk folk && folk.factionId() == camp) {
			return true;
		}
		return e instanceof ScalebaneArcher s && s.camp() == camp || Goblins.isGoblin(e) || Orcs.isOrc(e) || Barbarians.isCampVillager(e);
	}

	/**
	 * The camp {@code e} is hired to or kept by (a scalebane sellsword, a froglin, a plague bearer), or -1: a wild one, or
	 * anything else.
	 */
	public static int hiredCamp(@Nullable Entity e) {
		if (e instanceof ScalebaneArcher s) {
			return s.camp();
		}
		if (e instanceof Froglin f) {
			return f.camp();
		}
		return e instanceof PlagueBearer p ? p.camp() : -1;
	}

	/**
	 * Andy (2026-11-26): the scalebound "should be on the same team, but only when hired to the camp. otherwise they are
	 * still hostile." {@code a} and {@code b} leave each other be because one is hired to (or kept by) a camp and the
	 * other is that camp's own: its folk, its MCA villagers, a camp allied with it, or another creature it keeps. A wild
	 * one gets nothing from this: the camps treat it as any monster, and it them as prey.
	 */
	public static boolean spares(@Nullable Entity a, @Nullable Entity b) {
		if (a == null || b == null || a == b) {
			return false;
		}
		int ca = hiredCamp(a), cb = hiredCamp(b);
		return ca >= 0 && ownSide(ca, b) || cb >= 0 && ownSide(cb, a);
	}

	/** {@code e} is on camp {@code camp}'s side: one of its people or an allied camp's, or a creature it (or an ally) keeps. */
	private static boolean ownSide(int camp, Entity e) {
		int other = e instanceof Worker w ? w.factionId() : hiredCamp(e);
		if (other < 0) {
			return false;
		}
		if (other == camp) {
			return true;
		}
		MinecraftServer server = e.level().getServer();
		return server != null && Relations.between(server, camp, other) == Relation.ALLIED && !Brutality.atWar(server, camp, other);
	}

	/** A spot about the fire of camp {@code camp} for a creature to be put. */
	public static BlockPos spot(ServerLevel level, int camp, BlockPos fire, RandomSource random) {
		return Camps.orc(level.getServer(), camp) ? OrcCamps.spot(level, fire, random) : GoblinVillages.spot(level, fire, random);
	}

	/** A new camp's creatures: called as a goblin village or an orc camp rises. */
	public static void rise(ServerLevel level, int camp, BlockPos fire) {
		Froglin.hire(level, camp, fire, Froglin.START);
		if (Camps.orc(level.getServer(), camp)) {
			PlagueBearer.rise(level, camp, fire);
			// (Andy, 2026-11-26: "the scalebound should spawn more with orcs") an orc camp hires scalebane bowmen too
			ScalebaneArcher.hire(level, camp, fire, ScalebaneArcher.START);
		}
	}

	/** A day's growth ({@code days}) of camp {@code camp}: it may take more creatures on. */
	public static void grow(ServerLevel level, int camp, int days) {
		BlockPos fire = fire(level.getServer(), camp);
		if (fire != null) {
			Froglin.grow(level, camp, fire, days);
			if (Camps.orc(level.getServer(), camp)) {
				PlagueBearer.grow(level, camp, fire, days);
				ScalebaneArcher.grow(level, camp, fire, days);
			}
		}
	}
}
