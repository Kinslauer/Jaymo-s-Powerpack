package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The barbarians: a camp's raiders ({@link Folk#BARBARIAN}: axes and swords, hide armour, war paint in their camp's
 * colour) and its chief ({@link Folk#CHIEF}: a mini-boss with a red boss bar, who rallies when hurt). They attack
 * players, every other faction's folk, villagers and iron golems on sight; a kingdom's (or fiefdom's) fighting folk
 * attack them on sight in turn. Their alerts only call their own side (see {@link Folk#sameSide}: camps are allied with
 * each other, so barbarians help barbarians).
 */
public final class Barbarians {
	/** A raider: health, blows, pace. */
	static final double BARBARIAN_HEALTH = 30.0, BARBARIAN_DAMAGE = 4.5, BARBARIAN_SPEED = 0.32;
	/** The chief: health, blows, how hard he is to shove, and his armour's toughness. */
	static final double CHIEF_HEALTH = 160.0, CHIEF_DAMAGE = 8.0, CHIEF_KNOCKBACK_RESIST = 0.6, CHIEF_TOUGHNESS = 2.0;
	/** How far they spot prey (and kingdom folk spot them). */
	static final double SIGHT = 24.0;
	/** One raider in this many wears a hide cap. */
	static final int CAP_ONE_IN = 3;
	/** Hide (leather dyed a raw brown), and the chief's darker furs. */
	static final int HIDE = 0x7A5230, DARK_FUR = 0x3B2A20;

	private Barbarians() {
	}

	/** Defines the barbarian and chief roles (call once at init). */
	static void init() {
		Folk.defineRole(new Folk.Role(Folk.BARBARIAN, "barbarian", true, false, null, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BARBARIAN_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BARBARIAN_DAMAGE);
			folk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BARBARIAN_SPEED);
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 8.0);
			Item[] weapons = {Items.STONE_AXE, Items.IRON_AXE, Items.STONE_SWORD, Items.IRON_SWORD, Items.STONE_AXE};
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[folk.getRandom().nextInt(weapons.length)]));
			folk.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, HIDE));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, HIDE));
			if (folk.getRandom().nextInt(CAP_ONE_IN) == 0) {
				folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, DARK_FUR));
			}
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.barbarian_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(Folk.CHIEF, "chief", true, true, BossEvent.BossBarColor.RED, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CHIEF_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(CHIEF_DAMAGE);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(CHIEF_KNOCKBACK_RESIST);
			folk.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(CHIEF_TOUGHNESS);
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 8.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, DARK_FUR));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, faction.color().getTextureDiffuseColor() & 0xFFFFFF));
			folk.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, DARK_FUR));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.chief_of", faction.name()));
		}));
	}

	private static ItemStack dyed(Item item, int rgb) {
		ItemStack stack = new ItemStack(item);
		stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	/** It's a camp's: a raider, the chief, or one of a camp's builders. */
	public static boolean isBarbarian(Folk folk) {
		return folk.style() == FactionType.BARBARIAN || folk.role() == Folk.BARBARIAN || folk.role() == Folk.CHIEF;
	}

	/** It fights for a camp (a raider or the chief). */
	public static boolean isRaider(Folk folk) {
		return folk.role() == Folk.BARBARIAN || folk.role() == Folk.CHIEF;
	}

	/**
	 * The barbarians' extra goals (called from {@link Folk#registerGoals}): raiders and chiefs hunt players, villagers,
	 * golems and every other faction's folk; a realm's or village's fighting folk hunt barbarians.
	 */
	static void addGoals(Folk folk, GoalSelector goals, GoalSelector targets) {
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, LivingEntity.class, true, (target, level) -> isRaider(folk) && prey(target)));
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, Folk.class, true,
			(target, level) -> folk.combatant() && !isBarbarian(folk) && target instanceof Folk other && isBarbarian(other)));
	}

	/** What a raider attacks on sight. */
	static boolean prey(LivingEntity target) {
		if (target instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator();
		}
		if (target instanceof Folk other) {
			return !isBarbarian(other);
		}
		return target instanceof Villager || target instanceof IronGolem;
	}
}
