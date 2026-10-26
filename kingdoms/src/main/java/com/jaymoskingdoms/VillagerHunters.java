package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import com.jaymoskingdoms.monsters.MudGolem;
import com.jaymoskingdoms.monsters.Mimic;
import com.jaymoskingdoms.monsters.SilverSkeleton;
import com.jaymoskingdoms.monsters.Wraith;
import com.jaymoskingdoms.wild.Tarantula;
import com.jaymoskingdoms.wild.WildBeast;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * (0.7.217) Every monster goes for villagers (MCA's included, who are villagers underneath), not just zombies and
 * illagers: skeletons (every kind), creepers, spiders (only in the dark, as with players), endermen and witches get a
 * villager target beside their player one ({@link #TARGET_PRIORITY}); our own wraiths, mimics, mud golems, silver
 * skeletons, tarantulas and wild beasts have theirs in their own goals. Their blows on a villager land at
 * {@link #VILLAGER_DAMAGE}.
 *
 * <p>And iron golems now go for creepers (the game's own golem leaves them be), and kill one with a single blow: a
 * creeper is frail beside a golem.
 */
public final class VillagerHunters {
	/** The villager target's place: after players (2), as zombies hunt villagers (vanilla's 3). */
	static final int TARGET_PRIORITY = 3;
	/** A golem's creeper target: with its other monsters (vanilla's 3). */
	static final int GOLEM_PRIORITY = 3;
	/** The share of their blow that these hunters land on a villager. */
	public static final float VILLAGER_DAMAGE = 0.5F;
	/** A spider hunts only where it's darker than this (the game's own spider, hunting players). */
	static final float SPIDER_DARK = 0.5F;

	private VillagerHunters() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
	}

	private static void loaded(Entity entity) {
		if (entity instanceof Spider spider) {
			hunt(spider, (target, level) -> spider.getLightLevelDependentMagicValue() < SPIDER_DARK);
		} else if (entity instanceof AbstractSkeleton || entity instanceof Creeper || entity instanceof Enderman || entity instanceof Witch) {
			hunt((Mob)entity, null);
		} else if (entity instanceof IronGolem golem) {
			((MobAccessor)golem).jaymoskingdoms$targets().addGoal(GOLEM_PRIORITY, new NearestAttackableTargetGoal<>(golem, Creeper.class, true));
		}
	}

	private static void hunt(Mob hunter, net.minecraft.world.entity.ai.targeting.TargetingConditions.@org.jspecify.annotations.Nullable Selector when) {
		((MobAccessor)hunter).jaymoskingdoms$targets().addGoal(TARGET_PRIORITY, new NearestAttackableTargetGoal<>(hunter, AbstractVillager.class, true, when));
	}

	/** One of the monsters that hunt villagers by this class (or by their own goals, from the same patch). */
	static boolean hunter(@org.jspecify.annotations.Nullable Entity attacker) {
		return attacker instanceof AbstractSkeleton || attacker instanceof Creeper || attacker instanceof Spider || attacker instanceof Enderman
			|| attacker instanceof Witch || attacker instanceof Wraith || attacker instanceof Mimic || attacker instanceof MudGolem
			|| attacker instanceof SilverSkeleton || attacker instanceof Tarantula || attacker instanceof WildBeast;
	}

	/**
	 * What a blow does to {@code victim} (from {@code mixin.LivingEntityHurtMixin}): a hunter's on a villager at
	 * {@link #VILLAGER_DAMAGE}, and a golem's own swing on a creeper enough to kill it.
	 */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (victim instanceof AbstractVillager && hunter(source.getEntity())) {
			return amount * VILLAGER_DAMAGE;
		}
		if (victim instanceof Creeper && source.getEntity() instanceof IronGolem && source.getDirectEntity() == source.getEntity()) {
			return Math.max(amount, victim.getMaxHealth() * 4.0F + victim.getAbsorptionAmount());
		}
		return amount;
	}
}
