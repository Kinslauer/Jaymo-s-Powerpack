package com.jaymoskitchen.ragdoll;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

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
		return RagdollRig.guess(width, height);
	}

	public static RagdollRig rig(EntityType<?> type, float width, float height) {
		return RagdollRig.of(of(type, width, height), width, height);
	}
}
