package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (BWM port, after its ExplosionTracker) One line in the server log for each explosion a player is behind: where, in
 * which dimension, what went off, and who lit, shot or owned it, so griefing on the friends' server can be traced.
 * Every vanilla blast comes through {@code ServerExplosionMixin}; the Armory's mining charge calls {@link #log} itself.
 */
public final class ExplosionLog {
	/** Also log blasts no player can be traced to (creepers on their own, beds in the Nether...). Noisy. */
	static final boolean LOG_ALL = false;

	private ExplosionLog() {
	}

	/** A vanilla explosion, as it goes off. */
	public static void explosion(ServerExplosion explosion) {
		Entity direct = explosion.getDirectSourceEntity();
		log(explosion.level(), explosion.center(), direct == null ? "none" : BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).toString(),
			player(explosion, direct));
	}

	/** Logs a blast at {@code at}, set off by {@code source} (an entity or block id), behind which is {@code player} (or no one). */
	public static void log(ServerLevel level, Vec3 at, String source, @Nullable Player player) {
		if (player == null && !LOG_ALL) {
			return;
		}
		JaymosPowers.LOGGER.info("Explosion at {} in {} from {}{}", String.format(Locale.ROOT, "%.1f, %.1f, %.1f", at.x, at.y, at.z),
			level.dimension().identifier(), source,
			player == null ? ", no player" : ", by " + player.getScoreboardName() + " (" + player.getUUID() + ")");
	}

	/** The player behind it: the igniter or owner the explosion knows, or the owner up a chain of owned things (an arrow's shooter's TNT...). */
	private static @Nullable Player player(ServerExplosion explosion, @Nullable Entity direct) {
		if (explosion.getIndirectSourceEntity() instanceof Player player) {
			return player;
		}
		if (explosion.getDamageSource().getEntity() instanceof Player player) {
			return player;
		}
		Entity at = direct;
		for (int i = 0; i < 4 && at != null; i++) {
			if (at instanceof Player player) {
				return player;
			}
			at = at instanceof TraceableEntity traceable ? traceable.getOwner() : null;
		}
		return null;
	}
}
