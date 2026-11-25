package com.jaymoskingdoms.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The illager kinds ported from Illager Invasion (MIT, by Dricky, Safro, Zeckemith and Fuzs; there's no 26.3 build of it):
 * the provoker, invoker, basher, archivist, illager inquisitor (theirs is "inquisitor": ours is the temple champion),
 * marauder, alchemist, firecaller and the invoker's surrendered, with their fangs, hatchets and flying magma. Their
 * numbers are theirs. Camps and raids send them ({@link IllagerKinds}).
 */
public final class ModEntityTypes {
	public static EntityType<Provoker> PROVOKER_ENTITY_TYPE;
	public static EntityType<Invoker> INVOKER_ENTITY_TYPE;
	public static EntityType<Basher> BASHER_ENTITY_TYPE;
	public static EntityType<Archivist> ARCHIVIST_ENTITY_TYPE;
	public static EntityType<Inquisitor> INQUISITOR_ENTITY_TYPE;
	public static EntityType<Marauder> MARAUDER_ENTITY_TYPE;
	public static EntityType<Alchemist> ALCHEMIST_ENTITY_TYPE;
	public static EntityType<Firecaller> FIRECALLER_ENTITY_TYPE;
	public static EntityType<Surrendered> SURRENDERED_ENTITY_TYPE;
	public static EntityType<ThrownHatchet> HATCHET_ENTITY_TYPE;
	public static EntityType<InvokerFangs> INVOKER_FANGS_ENTITY_TYPE;
	public static EntityType<FlyingMagma> FLYING_MAGMA_ENTITY_TYPE;

	private ModEntityTypes() {
	}

	public static void register() {
		ModSoundEvents.bootstrap();
		PROVOKER_ENTITY_TYPE = type("provoker", EntityType.Builder.of(Provoker::new, MobCategory.MONSTER).sized(0.5F, 1.92F).notInPeaceful());
		INVOKER_ENTITY_TYPE = type("invoker", EntityType.Builder.of(Invoker::new, MobCategory.MONSTER).sized(0.5F, 1.92F).fireImmune().notInPeaceful());
		BASHER_ENTITY_TYPE = type("basher", EntityType.Builder.of(Basher::new, MobCategory.MONSTER).sized(0.5F, 1.92F).notInPeaceful());
		ARCHIVIST_ENTITY_TYPE = type("archivist", EntityType.Builder.of(Archivist::new, MobCategory.MONSTER).sized(0.5F, 1.92F).notInPeaceful());
		INQUISITOR_ENTITY_TYPE = type("illager_inquisitor", EntityType.Builder.of(Inquisitor::new, MobCategory.MONSTER).sized(0.5F, 2.48F).notInPeaceful());
		MARAUDER_ENTITY_TYPE = type("marauder", EntityType.Builder.of(Marauder::new, MobCategory.MONSTER).sized(0.5F, 1.92F).canSpawnFarFromPlayer().notInPeaceful());
		ALCHEMIST_ENTITY_TYPE = type("alchemist", EntityType.Builder.of(Alchemist::new, MobCategory.MONSTER).sized(0.5F, 1.92F).notInPeaceful());
		FIRECALLER_ENTITY_TYPE = type("firecaller", EntityType.Builder.of(Firecaller::new, MobCategory.MONSTER).sized(0.5F, 1.92F).fireImmune().notInPeaceful());
		SURRENDERED_ENTITY_TYPE = type("surrendered", EntityType.Builder.of(Surrendered::new, MobCategory.MONSTER).sized(0.5F, 1.42F).eyeHeight(1.1F)
			.fireImmune().notInPeaceful());
		HATCHET_ENTITY_TYPE = type("hatchet", EntityType.Builder.<ThrownHatchet>of(ThrownHatchet::new, MobCategory.MISC).sized(0.35F, 0.35F)
			.clientTrackingRange(4).updateInterval(10));
		INVOKER_FANGS_ENTITY_TYPE = type("invoker_fangs", EntityType.Builder.<InvokerFangs>of(InvokerFangs::new, MobCategory.MISC).sized(0.65F, 1.05F));
		FLYING_MAGMA_ENTITY_TYPE = type("flying_magma", EntityType.Builder.<FlyingMagma>of(FlyingMagma::new, MobCategory.MISC).sized(0.95F, 1.05F));

		FabricDefaultAttributeRegistry.register(ALCHEMIST_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.38));
		FabricDefaultAttributeRegistry.register(ARCHIVIST_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.36));
		FabricDefaultAttributeRegistry.register(BASHER_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 32.0)
			.add(Attributes.MOVEMENT_SPEED, 0.31).add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.ATTACK_KNOCKBACK, 0.2));
		FabricDefaultAttributeRegistry.register(FIRECALLER_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 32.0)
			.add(Attributes.MOVEMENT_SPEED, 0.38));
		FabricDefaultAttributeRegistry.register(INQUISITOR_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 80.0)
			.add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.ATTACK_DAMAGE, 10.0).add(Attributes.ATTACK_KNOCKBACK, 1.6)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8));
		FabricDefaultAttributeRegistry.register(INVOKER_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 250.0)
			.add(Attributes.MOVEMENT_SPEED, 0.36).add(Attributes.KNOCKBACK_RESISTANCE, 0.3).add(Attributes.ATTACK_DAMAGE, 8.0));
		FabricDefaultAttributeRegistry.register(MARAUDER_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3));
		FabricDefaultAttributeRegistry.register(PROVOKER_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.38));
		FabricDefaultAttributeRegistry.register(SURRENDERED_ENTITY_TYPE, Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14.0)
			.add(Attributes.ATTACK_DAMAGE, 5.0));

		Item[] eggs = {egg("provoker", PROVOKER_ENTITY_TYPE), egg("invoker", INVOKER_ENTITY_TYPE), egg("basher", BASHER_ENTITY_TYPE),
			egg("archivist", ARCHIVIST_ENTITY_TYPE), egg("illager_inquisitor", INQUISITOR_ENTITY_TYPE), egg("marauder", MARAUDER_ENTITY_TYPE),
			egg("alchemist", ALCHEMIST_ENTITY_TYPE), egg("firecaller", FIRECALLER_ENTITY_TYPE), egg("surrendered", SURRENDERED_ENTITY_TYPE)};
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			for (Item egg : eggs) {
				output.accept(egg);
			}
		});
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item egg(String name, EntityType<? extends Mob> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_spawn_egg"));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key)));
	}
}
