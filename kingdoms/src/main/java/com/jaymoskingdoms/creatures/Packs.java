package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Goblins;
import com.jaymoskingdoms.UpperHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;

/**
 * Pack tactics (fifth edition's kobolds and wolves): goblins, diggers and wolves have the upper hand
 * ({@link UpperHand}) on whoever they strike when another of their own kind stands beside that target. The more the
 * wiser: two on one is a real fight, and one against a pack should not wait to be surrounded.
 */
public final class Packs {
	/** An ally this near (blocks) the one struck is "beside" them. */
	static final double BESIDE = 3.5;

	private Packs() {
	}

	static void register() {
		UpperHand.add((attacker, target) -> flanked(attacker, target) ? 1 : 0);
	}

	/** Which pack {@code e} runs with: 1 goblins, 2 diggers, 3 wolves, 0 none. */
	static int pack(LivingEntity e) {
		if (e instanceof Digger) {
			return 2;
		}
		if (e instanceof Wolf || e instanceof com.jaymoskingdoms.wild.FeralWolf) {
			return 3;
		}
		return Goblins.isGoblin(e) ? 1 : 0;
	}

	/** Whether {@code attacker} has another of its pack within {@link #BESIDE} of {@code target}. */
	static boolean flanked(LivingEntity attacker, LivingEntity target) {
		int mine = pack(attacker);
		if (mine == 0) {
			return false;
		}
		for (LivingEntity ally : attacker.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(BESIDE),
			e -> e != attacker && e != target && e.isAlive() && pack(e) == mine && !(e instanceof net.minecraft.world.entity.TamableAnimal t && t.isInSittingPose()))) {
			return true;
		}
		return false;
	}
}
