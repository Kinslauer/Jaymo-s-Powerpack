package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * (0.5.55) The wild beasts rebuilt after Better Animals Plus (its models and art are CC BY-NC-SA 4.0, by cybercat5555,
 * itsmeowdev and Wolverbyte; credited in {@code assets/jaymoskingdoms/textures/entity/wild/CREDITS.txt}, and this
 * package and its client side are under that licence too): feral wolves, coyotes, tarantulas and moose. They never spawn
 * on their own; they come as wilderness encounters ({@code Encounters}, biome tags {@code jaymoskingdoms:has_<beast>}).
 */
public final class Wild {
	public static EntityType<FeralWolf> FERAL_WOLF;
	public static EntityType<Coyote> COYOTE;
	public static EntityType<Tarantula> TARANTULA;
	public static EntityType<Moose> MOOSE;

	private Wild() {
	}

	public static void register() {
		FERAL_WOLF = type("feral_wolf", EntityType.Builder.of(FeralWolf::new, MobCategory.CREATURE).sized(1.2F, 1.3F).clientTrackingRange(10));
		COYOTE = type("coyote", EntityType.Builder.of(Coyote::new, MobCategory.CREATURE).sized(0.8F, 0.9F).clientTrackingRange(10));
		TARANTULA = type("tarantula", EntityType.Builder.of(Tarantula::new, MobCategory.MONSTER).sized(1.4F, 0.9F).clientTrackingRange(8));
		MOOSE = type("moose", EntityType.Builder.of(Moose::new, MobCategory.CREATURE).sized(2.0F, 2.8F).clientTrackingRange(10));
		FabricDefaultAttributeRegistry.register(FERAL_WOLF, FeralWolf.createAttributes());
		FabricDefaultAttributeRegistry.register(COYOTE, Coyote.createAttributes());
		FabricDefaultAttributeRegistry.register(TARANTULA, Tarantula.createAttributes());
		FabricDefaultAttributeRegistry.register(MOOSE, Moose.createAttributes());
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
