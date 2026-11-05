package com.jaymospowers.core;

import com.jaymospowers.tinkering.smithing.Smithing;
import com.jaymospowers.tinkering.smithing.Wear;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * (0.20.68) Andy (2026-11-05): "armor should have far less durability across the board, absorbing more damage from
 * attacks, but when it does break it doesnt disappear, it remains repairable but you start taking full damage ...
 * players and npc alike. axes and other weapons like maces deal more armor damage ... repairs are not possible on the
 * battlefield."
 * <ul>
 * <li>Every armour piece (helmet, chestplate, leggings, boots; any mod's) has {@link #DURABILITY} of its old durability.</li>
 * <li>Worn armour counts {@link #ARMOR_ABSORB} times its armour and {@link #TOUGHNESS_ABSORB} times its toughness against
 * a hit (vanilla's 80% cap stays), so it takes more of each blow while it lasts.</li>
 * <li>At 0 it isn't destroyed: it stays on at full damage taken ({@code ItemStack#isBroken}, which vanilla already skips
 * for attributes; its enchantments go quiet too), and an anvil brings it back. Forged (graded) pieces keep their own
 * "Broken" rule ({@link Wear}).</li>
 * <li>Axes, maces and other blunt weapons ({@link Fractures#blunt}) wear armour {@link #HEAVY_WEAR} times faster.</li>
 * <li>NPCs' armour wears too (vanilla only wore players'). They have no anvil, so a mob's armour mends by itself, but only
 * off the battlefield: {@link #MOB_REST_TICKS} after its last fight.</li>
 * <li>No repairs in a fight: an anvil won't hand over a repaired piece, and Mending takes no XP, while you are
 * {@link #inCombat}.</li>
 * </ul>
 */
public final class ArmourWear {
	/** Share of its old durability every armour piece keeps. */
	public static final float DURABILITY = 0.3F;
	/** Worn armour counts this many times its armour points against a hit. */
	public static final float ARMOR_ABSORB = 1.4F;
	/** ...and this many times its toughness. */
	public static final float TOUGHNESS_ABSORB = 1.25F;
	/** Axes, maces and blunt weapons wear armour this many times faster. */
	public static final float HEAVY_WEAR = 2.5F;
	/** Hurt or hit within this many ticks: in a fight. */
	public static final int COMBAT_TICKS = 200;
	/** A hostile mob after you within this many blocks: in a fight. */
	public static final double ENEMY_RANGE = 16.0;
	/** An NPC's armour starts to mend this many ticks after its last fight. */
	public static final int MOB_REST_TICKS = 600;
	/** How often (ticks) a resting NPC's armour mends, and the share of each piece's durability it gets back each time. */
	public static final int MOB_MEND_EVERY = 100;
	public static final float MOB_MEND_SHARE = 0.1F;
	/** Ticks between "not in a fight" reminders. */
	private static final int REMIND_TICKS = 20;

	private ArmourWear() {
	}

	public static void init() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(ArmourWear::armourItem, (builder, lookup, item) -> {
			Integer max = item.components().get(DataComponents.MAX_DAMAGE);
			if (max != null) {
				builder.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(max * DURABILITY)));
			}
		}));
	}

	private static boolean armourItem(net.minecraft.world.item.Item item) {
		var components = item.components();
		Equippable equippable = components.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR && components.has(DataComponents.MAX_DAMAGE)
			&& !components.has(DataComponents.GLIDER);
	}

	/** A worn-armour piece (helmet, chest, legs, boots) that can wear out; not an elytra. */
	public static boolean armour(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.has(DataComponents.GLIDER)) {
			return false;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	/** An armour piece that breaks our way here (forged ones break by {@link Wear}'s rule instead). */
	public static boolean applies(ItemStack stack) {
		return armour(stack) && !stack.has(Smithing.GRADE);
	}

	/** Worn out and doing nothing until repaired. */
	public static boolean broken(ItemStack stack) {
		return armour(stack) && (stack.isBroken() || Wear.broken(stack));
	}

	/** The wear a hit does to each piece: more from axes, maces and other heavy or blunt weapons. */
	public static float wear(DamageSource source, float damage) {
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && !weapon.isEmpty() && (weapon.is(ItemTags.AXES) || Fractures.blunt(weapon))) {
			return damage * HEAVY_WEAR;
		}
		return damage;
	}

	/** In a fight: hurt or hitting within {@link #COMBAT_TICKS}, or a hostile mob after you nearby. */
	public static boolean inCombat(LivingEntity entity) {
		int now = entity.tickCount;
		int hit = entity.getLastHurtMobTimestamp();
		if (hit > 0 && now - hit < COMBAT_TICKS) {
			return true;
		}
		if (entity.getLastHurtByMob() != null && now - entity.getLastHurtByMobTimestamp() < COMBAT_TICKS) {
			return true;
		}
		if (entity instanceof Mob mob) {
			return mob.getTarget() != null && mob.getTarget().isAlive();
		}
		return !entity.level().getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(ENEMY_RANGE),
			mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() == entity).isEmpty();
	}

	/** Whether a repair of {@code before} into {@code after} must wait because {@code player} is fighting (and tells them). */
	public static boolean refuseRepair(Player player, ItemStack before, ItemStack after) {
		if (!armour(after) || before.isEmpty() || after.getDamageValue() >= before.getDamageValue() || player.hasInfiniteMaterials()
			|| !inCombat(player)) {
			return false;
		}
		if (player instanceof ServerPlayer server && server.tickCount % REMIND_TICKS == 0) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.armour_wear.no_repair"));
		}
		return true;
	}

	/** Mending in a fight: not now. */
	public static boolean refuseMending(ServerPlayer player) {
		return !player.hasInfiniteMaterials() && inCombat(player);
	}

	/** (from a mob's tick) An NPC off the battlefield mends its worn armour a little at a time. */
	public static void rest(Mob mob) {
		if ((mob.tickCount + mob.getId()) % MOB_MEND_EVERY != 0 || mob.level().isClientSide() || !mob.isAlive()) {
			return;
		}
		if (mob.getTarget() != null || mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < MOB_REST_TICKS
			|| mob.getLastHurtMobTimestamp() > 0 && mob.tickCount - mob.getLastHurtMobTimestamp() < MOB_REST_TICKS) {
			return;
		}
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
				continue;
			}
			ItemStack piece = mob.getItemBySlot(slot);
			if (!armour(piece) || piece.getDamageValue() <= 0) {
				continue;
			}
			int mend = Math.max(1, Math.round(piece.getMaxDamage() * MOB_MEND_SHARE));
			piece.setDamageValue(Math.max(0, piece.getDamageValue() - mend));
			if (piece.has(Wear.BROKEN) && !Wear.broken(piece)) {
				Wear.mend(piece);
			}
		}
	}
}
