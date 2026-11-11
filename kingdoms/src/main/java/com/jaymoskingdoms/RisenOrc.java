package com.jaymoskingdoms;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;

/**
 * A risen orc: one of the dead orcs of the sorcerer's legion (Lore.md, "The host and the sorcerer"), called up again at the
 * ford for the Second Defence ({@link SecondDefence}). A zombie's brain in an orc's body, drawn by the client's
 * {@code RisenOrcRenderer} in an orc grunt's hide gone grey. Heavier and harder-hitting than a zombie; the sun doesn't burn
 * it, water doesn't turn it, it makes no zombie villagers and drops nothing.
 */
public final class RisenOrc extends Zombie {
	static final double HEALTH = 30.0, DAMAGE = 5.0, ARMOR = 4.0, SPEED = 0.24, KNOCKBACK_RESIST = 0.4;

	public static EntityType<RisenOrc> TYPE;

	public RisenOrc(EntityType<? extends RisenOrc> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("risen_orc"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<RisenOrc>of(RisenOrc::new, MobCategory.MONSTER)
			.noLootTable()
			.sized(0.7F, 1.95F)
			.eyeHeight(1.74F)
			.clientTrackingRange(10)
			.build(key));
		FabricDefaultAttributeRegistry.register(TYPE, attributes());
	}

	static AttributeSupplier.Builder attributes() {
		return Zombie.createAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ARMOR, ARMOR)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESIST);
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	public boolean convertVillagerToZombieVillager(ServerLevel level, Villager villager) {
		return false;
	}
}
