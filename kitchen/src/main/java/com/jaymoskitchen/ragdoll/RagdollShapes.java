package com.jaymoskitchen.ragdoll;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;

/**
 * (0.5.1) Which ragdoll a body gets. Tags first (data packs can add modded mobs), then a guess from its standing size
 * (see {@link RagdollRig#guess}). Players are always humanoid.
 */
public final class RagdollShapes {
	public static final TagKey<EntityType<?>> HUMANOID = tag("ragdoll_humanoid");
	public static final TagKey<EntityType<?>> QUADRUPED = tag("ragdoll_quadruped");
	public static final TagKey<EntityType<?>> BOX = tag("ragdoll_box");
	/** (0.5.47) Long bendy bodies (snakes): a head and a chain of loose joints. */
	public static final TagKey<EntityType<?>> CHAIN = tag("ragdoll_chain");

	private RagdollShapes() {
	}

	private static TagKey<EntityType<?>> tag(String name) {
		return TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id(name));
	}

	public static RagdollRig.Shape of(EntityType<?> type, float width, float height) {
		if (type == EntityTypes.PLAYER || type.builtInRegistryHolder().is(HUMANOID)) {
			return RagdollRig.Shape.HUMANOID;
		}
		if (type.builtInRegistryHolder().is(QUADRUPED)) {
			return RagdollRig.Shape.QUADRUPED;
		}
		if (type.builtInRegistryHolder().is(CHAIN)) {
			return RagdollRig.Shape.CHAIN;
		}
		if (type.builtInRegistryHolder().is(BOX)) {
			return RagdollRig.Shape.BOX;
		}
		RagdollRig.Shape guess = RagdollRig.guess(width, height);
		if (guess == RagdollRig.Shape.HUMANOID && wild(type.getCategory())) {
			// (0.5.64) a tall wild animal (a deer, an elephant) isn't a person: it gets four legs, or a box if it's slight
			return width >= 0.6F && height >= 0.6F ? RagdollRig.Shape.QUADRUPED : RagdollRig.Shape.BOX;
		}
		return guess;
	}

	/**
	 * (0.5.64) Andy (2026-11-05): "downed animals should fall to their side, i think they're trying to animate like
	 * humanoids". Whatever spawns as wildlife (animals, ambient things, sea life) is never guessed a person from its
	 * height: Cube Animals' roe deer (0.9 by 1.5) died sprawled like a man. Tagged humanoids (the wandering trader) still are.
	 */
	static boolean wild(MobCategory category) {
		return category == MobCategory.CREATURE || category == MobCategory.AMBIENT || category == MobCategory.WATER_CREATURE
			|| category == MobCategory.WATER_AMBIENT || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
	}

	public static RagdollRig rig(EntityType<?> type, float width, float height) {
		return RagdollRig.of(of(type, width, height), width, height);
	}
}
