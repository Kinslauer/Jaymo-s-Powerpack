package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * The people of the world move at a person's pace. Andy (2026-11-19), on a night fight with a camp's raiders: "the npcs as a
 * whole move WAY too quickly, slow them down drastically so players can react".
 * <p>
 * Every humanoid NPC (our {@link Folk}: villagers' folk, guards, barbarians, orcs, pirates, looters, assassins; the cordon's
 * folk; MCA's and the game's villagers and traders; illagers and witches) has its speed cut by {@link #PACE} of its total,
 * on top of everything else that sets it (its kind's base, a world tier, a crew's pace, a swing's slow), so a charge and a
 * stroll slow alike. A mob's own walking goes with the square of its speed (the game feeds the speed in as both the pace
 * and the push), so {@link #PACE} of -0.3 leaves them about half as fast on the ground: a barbarian's charge, which ran down
 * a sprinting player, is now about a walking player's. Monsters and animals are left as they are.
 * <p>
 * The modifier is transient (not saved) and put back each time the body loads, so a change of {@link #PACE} reaches every
 * NPC already in the world.
 */
public final class NpcPace {
	/** The share of its speed every humanoid NPC loses (multiplies the total). */
	static final double PACE = -0.3;

	private static final Identifier PACE_ID = JaymosKingdoms.id("npc_pace");

	private NpcPace() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && humanoid(mob)) {
				AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.addOrUpdateTransientModifier(new AttributeModifier(PACE_ID, PACE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		});
	}

	/** Who counts as a humanoid NPC here. */
	static boolean humanoid(Entity e) {
		return e instanceof Folk || e instanceof CordonFolk || e instanceof AbstractVillager || e instanceof AbstractIllager || e instanceof Witch;
	}
}
