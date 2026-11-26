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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Cube Animals' crocodile hunts like a crocodile. On its own it notices a player only within 6 blocks, so one lying in
 * a river let Andy walk the bank beside it (2026-11-14: "Crocodiles are not hostile"). A real crocodile is an ambush
 * hunter: it waits in the water and takes whatever comes down to drink, and it hunts on land at night. So in water,
 * or at night, it notices prey out to {@link #HUNT_RANGE}; basking on the bank by day it keeps its own short range and
 * leaves you be unless you walk right up to it. Nothing about the crocodile itself is changed, only how far it looks.
 * It takes monsters too (Andy, 2026-11-22: "crocodiles in the overworld should also be hostile to other hostile
 * mobs"): a crocodile with nothing in its jaws goes for the nearest monster it can see within that same reach, so a
 * zombie that wades into its river is food like anything else. It takes big beasts too, the bigger the likelier
 * ({@link #takePrey}), and Naturalist's alligator hunts the same way ({@link DeathRoll#JAWS}).
 */
public final class Crocodiles {
	/** Blocks of notice added in water or at night (its own 6 becomes 16). */
	static final double HUNT_RANGE = 10.0;
	/** Blocks added to the distance when weighing prey by size (the bigger, the more a far beast is worth the swim). */
	static final double PREY_BIAS = 4.0;
	/** Villagers keep this far from a grown crocodile they can see (blocks), and walk off at this speed. */
	static final double WARY = 10.0, WARY_SPEED = 0.8;
	/** How often it's checked (ticks). */
	static final int CHECK_TICKS = 20;

	private static final Identifier HUNTING = JaymosKingdoms.id("crocodile_hunting");
	private static final Set<Mob> CROCODILES = Collections.newSetFromMap(new WeakHashMap<>());

	private Crocodiles() {
	}

	public static void register() {
		DeathRoll.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && DeathRoll.jaws(mob)) {
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
						takePrey(croc);
					}
					if (!croc.isBaby()) {
						wary(croc);
					}
				}
			}
		});
	}

	/**
	 * Sets the crocodile on prey in sight within its notice range: a monster, or (Andy, 2026-11-25: "go after bigger
	 * prey") a wild beast, the bigger the likelier ({@link #PREY_BIAS}: a cow or a deer before a chicken, a near one before
	 * a far one). Never another crocodile or alligator, or a beast too big for its jaws ({@link DeathRoll.Jaws#mostPrey});
	 * pets are taken like any beast (Andy: "they will NOT leave pets alone. nothing is safe"). An alligator goes for beasts
	 * only from the water. A fed one takes up nothing (Jaymo's Kitchen's scavengers).
	 */
	private static void takePrey(Mob croc) {
		double reach = croc.getAttributeValue(Attributes.FOLLOW_RANGE);
		DeathRoll.Jaws jaws = DeathRoll.jawsOf(croc);
		double most = DeathRoll.bulk(croc) * jaws.mostPrey();
		boolean beasts = !jaws.fromWater() || croc.isInWater();
		LivingEntity prey = null;
		double best = 0.0;
		for (LivingEntity e : croc.level().getEntitiesOfClass(LivingEntity.class, croc.getBoundingBox().inflate(reach),
				e -> e.isAlive() && !DeathRoll.jaws(e) && (e instanceof Enemy || beasts && e instanceof Animal animal
					&& DeathRoll.bulk(animal) <= most))) {
			double d = Math.sqrt(e.distanceToSqr(croc));
			if (d > reach) {
				continue;
			}
			double score = (e instanceof Enemy ? DeathRoll.bulk(croc) : DeathRoll.bulk(e)) / (d + PREY_BIAS);
			if (score > best && croc.hasLineOfSight(e)) {
				best = score;
				prey = e;
			}
		}
		if (prey != null) {
			croc.setTarget(prey);
		}
	}

	/**
	 * Andy (2026-11-25): "nothing is safe. thats why you should also see villagers more cautious if they are nearby".
	 * Villagers who see a grown crocodile or alligator within {@link #WARY} walk away from it (not one who's fighting).
	 */
	private static void wary(Mob croc) {
		for (net.minecraft.world.entity.npc.villager.AbstractVillager v : croc.level().getEntitiesOfClass(
				net.minecraft.world.entity.npc.villager.AbstractVillager.class, croc.getBoundingBox().inflate(WARY),
				v -> v.isAlive() && v.getTarget() == null && !v.isPassenger() && v.distanceToSqr(croc) < WARY * WARY)) {
			if (v.getNavigation().isInProgress() && v.getNavigation().getTargetPos() != null
				&& v.getNavigation().getTargetPos().distSqr(croc.blockPosition()) > WARY * WARY || !v.hasLineOfSight(croc)) {
				continue;
			}
			net.minecraft.world.phys.Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(v, (int) WARY + 4, 4, croc.position());
			if (away != null) {
				v.getNavigation().moveTo(away.x, away.y, away.z, WARY_SPEED);
			}
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
