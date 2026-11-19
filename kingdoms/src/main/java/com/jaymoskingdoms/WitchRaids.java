package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (gv_witches, after Guard Villagers' witches: its idea only) A witch has a grudge against a town's people as well as against
 * players. Every witch of the world also picks out a villager she can see within {@link #INTERVAL} ticks' notice and throws
 * the game's own potions at them (harming, poison, slowness: her ranged attack does the choosing). When a villager is hurt by
 * a witch every guard within {@link #CALL} blocks of them turns on her ({@link Comrades#aim}), so a witch near a town is dealt
 * with by its guards and not only by whoever happens by.
 */
public final class WitchRaids {
	/** How often (1 in this many ticks) a witch looks for a villager to go for. */
	static final int INTERVAL = 10;
	/** The guards that answer a villager hurt by a witch are within this of the villager (blocks). */
	static final double CALL = 32.0;

	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private WitchRaids() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Witch witch && GIVEN.add(witch)) {
				((MobAccessor)witch).jaymoskingdoms$targets().addGoal(3, new NearestAttackableTargetGoal<>(witch, Villager.class, INTERVAL, true, false, (target, l) -> true));
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Entity by = source.getEntity();
			if (entity instanceof Villager victim && by instanceof Witch witch && witch.isAlive() && victim.level() instanceof ServerLevel level) {
				for (Mob guard : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(CALL), m -> m != victim && m.isAlive() && Downed.isGuard(m)
					&& !Downed.isDowned(m))) {
					Comrades.aim(guard, witch);
				}
			}
		});
	}
}
