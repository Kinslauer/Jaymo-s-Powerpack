package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Cube Animals' crocodile hunts like a crocodile. On its own it notices a player only within 6 blocks, so one lying in
 * a river let Andy walk the bank beside it (2026-11-14: "Crocodiles are not hostile"). A real crocodile is an ambush
 * hunter: it waits in the water and takes whatever comes down to drink, and it hunts on land at night. So in water,
 * or at night, it notices prey out to {@link #HUNT_RANGE}; basking on the bank by day it keeps its own short range and
 * leaves you be unless you walk right up to it. Nothing about the crocodile itself is changed, only how far it looks.
 * It takes monsters too (Andy, 2026-11-22: "crocodiles in the overworld should also be hostile to other hostile
 * mobs"): a crocodile with nothing in its jaws goes for the nearest monster it can see within that same reach, so a
 * zombie that wades into its river is food like anything else.
 */
public final class Crocodiles {
	/** Cube Animals' crocodile. */
	static final Identifier CROCODILE = Identifier.fromNamespaceAndPath("cubeanimals", "crocodile");
	/** Blocks of notice added in water or at night (its own 6 becomes 16). */
	static final double HUNT_RANGE = 10.0;
	/** How often it's checked (ticks). */
	static final int CHECK_TICKS = 20;

	private static final Identifier HUNTING = JaymosKingdoms.id("crocodile_hunting");
	private static final Set<Mob> CROCODILES = Collections.newSetFromMap(new WeakHashMap<>());

	private Crocodiles() {
	}

	public static void register() {
		DeathRoll.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && CROCODILE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
				CROCODILES.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> CROCODILES.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (CROCODILES.isEmpty() || server.getTickCount() % CHECK_TICKS != 0) {
				return;
			}
			for (Mob croc : CROCODILES.toArray(Mob[]::new)) {
				if (croc.isAlive()) {
					hunt(croc, !croc.isBaby() && (croc.isInWater() || croc.level().isDarkOutside()));
					if (!croc.isBaby() && croc.getTarget() == null) {
						takeMonster(croc);
					}
				}
			}
		});
	}

	/** Sets the crocodile on the nearest monster in sight within its notice range (not another crocodile). */
	private static void takeMonster(Mob croc) {
		double reach = croc.getAttributeValue(Attributes.FOLLOW_RANGE);
		LivingEntity prey = null;
		double best = reach * reach;
		for (LivingEntity e : croc.level().getEntitiesOfClass(LivingEntity.class, croc.getBoundingBox().inflate(reach),
				e -> e instanceof Enemy && e.isAlive() && e.getType() != croc.getType())) {
			double d = e.distanceToSqr(croc);
			if (d < best && croc.hasLineOfSight(e)) {
				best = d;
				prey = e;
			}
		}
		if (prey != null) {
			croc.setTarget(prey);
		}
	}

	private static void hunt(Mob croc, boolean hunting) {
		AttributeInstance range = croc.getAttribute(Attributes.FOLLOW_RANGE);
		if (range == null) {
			return;
		}
		if (!hunting) {
			range.removeModifier(HUNTING);
		} else if (range.getModifier(HUNTING) == null) {
			range.addOrUpdateTransientModifier(new AttributeModifier(HUNTING, HUNT_RANGE, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
