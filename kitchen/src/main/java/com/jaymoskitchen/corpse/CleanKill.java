package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.butchery.Butchery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Andy: "Yeah 1 and 3" (clean kills). How an animal died decides what its carcass gives, as a hunter's kill does: a
 * blade or an arrow leaves it whole; teeth and claws tear the hide ({@link #MAULED}); fire ruins it ({@link #BURNT});
 * a fall, a blast, a club or bare fists bruise the meat ({@link #BATTERED}). The loss is taken from the body's loot as
 * it's made, so every way of butchering it (ground, block, hook, a butcher villager) sees the same carcass. What was
 * lost is kept with the body: a practised butcher ({@link Hanging#SKILL_SAVES}) trims round the damage and has it back,
 * all but a burnt hide.
 */
public final class CleanKill {
	/** Weapons that bruise the meat: maces, hammers, clubs, staves. */
	public static final TagKey<Item> BRUISING_WEAPONS = TagKey.create(Registries.ITEM, JaymosKitchen.id("bruising_weapons"));
	static final String CLEAN = "", MAULED = "mauled", BURNT = "burnt", BATTERED = "battered";
	/** A battered carcass loses one piece of meat in this many (never its last). */
	static final int BRUISED_ONE_IN = 3;
	/** A mauled carcass keeps one skin in this many. */
	static final int TORN_KEEPS_ONE_IN = 2;

	private CleanKill() {
	}

	/** How {@code mob}, just now dying, was killed. */
	static String of(LivingEntity mob) {
		DamageSource last = mob.getLastDamageSource();
		if (mob.isOnFire() || last != null && last.is(DamageTypeTags.IS_FIRE)) {
			return BURNT;
		}
		if (last == null) {
			return CLEAN;
		}
		if (last.is(DamageTypeTags.IS_FALL) || last.is(DamageTypeTags.IS_EXPLOSION)) {
			return BATTERED;
		}
		Entity direct = last.getDirectEntity();
		if (direct instanceof Player) {
			ItemStack weapon = last.getWeaponItem();
			return weapon == null || weapon.isEmpty() || weapon.is(BRUISING_WEAPONS) ? BATTERED : CLEAN;
		}
		// teeth and claws (a wolf, a bear, a hunter's own dog); an armed mob's blade is as clean as a player's
		return direct instanceof Mob biter && biter.getMainHandItem().isEmpty() ? MAULED : CLEAN;
	}

	/** Takes from {@code loot} what dying that way cost; what was taken. */
	static List<ItemStack> spoil(String death, List<ItemStack> loot) {
		List<ItemStack> lost = new ArrayList<>();
		for (int i = 0; i < loot.size(); i++) {
			ItemStack stack = loot.get(i);
			int count = stack.getCount();
			int keep = switch (death) {
				case BURNT -> stack.is(Butchery.SKINS) ? 0 : count;
				case MAULED -> stack.is(Butchery.SKINS) ? count / TORN_KEEPS_ONE_IN : count;
				case BATTERED -> stack.has(DataComponents.FOOD) ? Math.max(1, count - (count + BRUISED_ONE_IN - 1) / BRUISED_ONE_IN) : count;
				default -> count;
			};
			if (keep < count) {
				lost.add(stack.copyWithCount(count - keep));
				loot.set(i, stack.copyWithCount(keep));
			}
		}
		loot.removeIf(ItemStack::isEmpty);
		return lost;
	}
}
