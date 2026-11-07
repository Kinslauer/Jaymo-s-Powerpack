package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.PatronChampion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** (0.7.518) One move of a patron's champion: its wind-up and cooldown (ticks), the range it is used at, its telegraph and its blow. */
public record Move(String id, int windup, int cooldown, double minRange, double maxRange, Tell tell, Act act) {
	/** Runs every tick of the wind-up ({@code left} counts down to 0): the telegraph. */
	@FunctionalInterface
	public interface Tell {
		void tick(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left);
	}

	/** Runs once, when the wind-up ends. */
	@FunctionalInterface
	public interface Act {
		void land(ServerLevel level, PatronChampion c, @Nullable LivingEntity target);
	}
}
