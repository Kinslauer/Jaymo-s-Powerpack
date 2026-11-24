package com.jaymoskingdoms;

import com.jaymoskingdoms.inhabitants.fts.Froglin;
import com.jaymoskingdoms.inhabitants.fts.PlagueBearer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
		return e instanceof ScalebaneArcher || Goblins.isGoblin(e) || Orcs.isOrc(e) || Barbarians.isCampVillager(e);
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
		}
	}

	/** A day's growth ({@code days}) of camp {@code camp}: it may take more creatures on. */
	public static void grow(ServerLevel level, int camp, int days) {
		BlockPos fire = fire(level.getServer(), camp);
		if (fire != null) {
			Froglin.grow(level, camp, fire, days);
			if (Camps.orc(level.getServer(), camp)) {
				PlagueBearer.grow(level, camp, fire, days);
			}
		}
	}
}
