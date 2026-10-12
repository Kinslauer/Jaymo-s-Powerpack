package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/**
 * (0.5.13) Whatever hunts villagers hunts a settlement's folk too: zombies (and husks, drowned, zombie villagers) go
 * for any of them, illagers and ravagers for a realm's (a camp's barbarians are no villagers). Builders, farmers,
 * miners and woodcutters run from them ({@link Folk}); guards fight. (With MCA a realm's workers are villagers
 * already, hunted as villagers are.)
 */
final class Prey {
	/** The goal's place among their targets: with the villagers they hunt (vanilla's 3). */
	static final int PRIORITY = 3;

	private Prey() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
	}

	private static void loaded(Entity entity) {
		if (entity instanceof Zombie zombie && !(zombie instanceof ZombifiedPiglin)) {
			hunt(zombie, false, false);
		} else if (entity instanceof AbstractIllager || entity instanceof Ravager) {
			hunt((Mob)entity, true, true);
		}
	}

	private static void hunt(Mob hunter, boolean mustSee, boolean realmsOnly) {
		((com.jaymoskingdoms.mixin.MobAccessor)hunter).jaymoskingdoms$targets().addGoal(PRIORITY, new NearestAttackableTargetGoal<>(hunter, Folk.class, mustSee,
			(target, level) -> target instanceof Folk folk && !folk.isBaby() && (!realmsOnly || folk.style() != FactionType.BARBARIAN)));
	}
}
