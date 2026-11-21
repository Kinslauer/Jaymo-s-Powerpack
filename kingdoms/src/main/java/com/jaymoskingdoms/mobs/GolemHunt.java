package com.jaymoskingdoms.mobs;

import com.jaymoskingdoms.mixin.tweaks.CreeperAccessor;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Golems hunt creepers (the Clouds and Bits plan's Mobs patch): iron golems, and the town golems that fight
 * ({@code TownGolem}'s own goals), go for any creeper near them, and a creeper a golem hits has its fuse put out and
 * its swelling undone, so a golem that reaches one first stops it going off.
 */
public final class GolemHunt {
	/** How often (1 in this many ticks) a golem looks for a creeper to go for. */
	static final int LOOK_INTERVAL = 10;

	private GolemHunt() {
	}

	public static void register() {
		// iron golems already hunt creepers (VillagerHunters); here a golem's blow also puts the fuse out
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Creeper creeper && source.getEntity() instanceof AbstractGolem) {
				defuse(creeper);
			}
		});
	}

	/** The goal that sends {@code golem} at a creeper while {@code ready}: used for iron golems here and for the fighting town golems. */
	public static NearestAttackableTargetGoal<Creeper> creeperGoal(AbstractGolem golem, BooleanSupplier ready) {
		return new NearestAttackableTargetGoal<>(golem, Creeper.class, LOOK_INTERVAL, true, false, (target, level) -> ready.getAsBoolean());
	}

	/** Puts a creeper's fuse out: its swelling and its lit flag are cleared, so a hit stops the blast. */
	public static void defuse(Creeper creeper) {
		CreeperAccessor access = (CreeperAccessor)creeper;
		access.jaymoskingdoms$setSwell(0);
		access.jaymoskingdoms$setOldSwell(0);
		creeper.setSwellDir(-1);
		creeper.getEntityData().set(CreeperAccessor.jaymoskingdoms$ignited(), false);
	}
}
