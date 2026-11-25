package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Patch 7 of the fighting AI (band styles, st-dodge; the ideas are Hostile Humans', none of its code): the dodgers' ({@link NpcDodge#dodger})
 * two blades and the shield on the back.
 * <ul>
 * <li><b>Two blades</b> ({@link #dual}): a blade in each hand, neither two-handed, and its blows go hand after hand. Better Combat
 * knows no dual wielding for mobs (its bridge leaves those moves out), so we alternate ourselves: the off hand's turn swings the off
 * hand's own weapon (Better Combat's move for it, played mirrored; else the game's off-arm swing) and strikes with the off hand's
 * weapon in place of the main's ({@link #attackDamage}, {@link #weapon}): its damage under the same multipliers, its enchantments, its
 * wear.</li>
 * <li><b>A shield on the back</b>: carried in the body slot (it's drawn on the back, {@code BackGearLayer}), and of no use there. Shot at
 * or aimed at ({@link #threat}, from {@link ShieldSense}), it swaps to the off hand, which takes {@link #SWAP_TICKS}; the off-hand
 * blade goes onto the back in its place. With nothing shot or aimed at it for {@link #STOW_AFTER} ticks, and the shield down, it
 * goes back. A swap trades the two stacks between the slots, each with its own drop chance: nothing is made or lost. Never the very
 * stack an MCA villager's pack holds (it would be in two places). With its off hand empty (a shield broken, taken), the blade on its
 * back comes back to the hand. MCA's re-arm leaves its off hand be while it has gear on its back ({@link #keeps}), so it never has
 * two shields; a downed body's back gear is taken with its weapons ({@code Downed}).</li>
 * <li><b>Armed</b> ({@link #arm}, from the bandits' dress and an assassin's reveal): a bandit's shield goes onto his back, and
 * {@link #BANDIT_TWO_BLADES} of them take up a second blade; an assassin always fights with two (a hidden blade at each wrist, if his
 * is one), and {@link #ASSASSIN_BACK_SHIELD} of them carry a battered shield on the back.</li>
 * </ul>
 */
public final class DualBlades {
	/** Swapping the shield from the back to the hand (or back) takes this long (ticks); it goes back after this long with nothing shot at it. */
	static final int SWAP_TICKS = 10, STOW_AFTER = 60;
	/** The share of bandits who take up a second blade, and of assassins who carry a shield on the back. */
	static final float BANDIT_TWO_BLADES = 0.4F, ASSASSIN_BACK_SHIELD = 0.3F;
	/** The second blade they take up. */
	static final Item OFF_BLADE = Items.IRON_SWORD;

	private static final class State {
		/** The hand of its last blow (the off hand's), and when (game time). */
		boolean lastOff = true;
		long blowAt = Long.MIN_VALUE / 2;
		/** A swap under way: done at (game time; 0 none), and whether the shield goes to the hand. */
		long swapAt;
		boolean toHand;
		/** Last shot or aimed at (game time). */
		long threatAt;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private DualBlades() {
	}

	/** From {@link BandStyles#register}. */
	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DualBlades::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// (a dodger with a shield on its back or in its hand: watched for its swaps)
			if (entity instanceof Mob mob && (onBack(mob) || mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) && !mob.getItemBySlot(EquipmentSlot.BODY).isEmpty()
				|| bladeOnBack(mob))) {
				STATES.computeIfAbsent(mob, m -> new State());
			}
		});
	}

	private static State state(Mob mob) {
		return STATES.computeIfAbsent(mob, m -> new State());
	}

	// ---- two blades ------------------------------------------------------------------------------------------------------

	/** A dodger with a blade in each hand, neither two-handed (and no shield in the off hand). */
	static boolean dual(Mob mob) {
		ItemStack off = mob.getOffhandItem();
		if (off.isEmpty() || off.has(DataComponents.BLOCKS_ATTACKS) || !blade(off) || Blows.twoHanded(off)) {
			return false;
		}
		ItemStack main = mob.getMainHandItem();
		return blade(main) && !Blows.twoHanded(main) && NpcDodge.dodger(mob);
	}

	/** A blade to fight with: a weapon, or an assassin's hidden blade. */
	private static boolean blade(ItemStack held) {
		return Blows.weapon(held) || HiddenBlade.is(held);
	}

	/** (MobCombat, as a swing starts) Whether its next blow is the off hand's. */
	static boolean offNext(Mob mob) {
		if (!dual(mob)) {
			return false;
		}
		State s = STATES.get(mob);
		return s == null || !s.lastOff;
	}

	/** (MobCombat, as a swing's blow falls) The blow is {@code off}'s hand's. */
	static void swung(Mob mob, boolean off) {
		if (!off && STATES.get(mob) == null) {
			return;
		}
		State s = state(mob);
		s.lastOff = off;
		s.blowAt = mob.level().getGameTime();
	}

	/**
	 * (from {@code MobBlowMixin}, as {@code Mob.doHurtTarget} begins) Whether this blow of a two-bladed fighter is the off hand's: a
	 * swing's ({@link #swung}) says so; the game's blow (no swing of ours this tick) goes hand after hand, the off hand's swung for those
	 * watching. Decided once a tick, so every part of the blow agrees.
	 */
	private static boolean offBlow(Mob mob) {
		if (mob.getOffhandItem().isEmpty() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		State s = STATES.get(mob);
		long now = level.getGameTime();
		if (s != null && s.blowAt == now) {
			return s.lastOff;
		}
		boolean off = offNext(mob);
		if (!off && s == null) {
			return false;
		}
		s = state(mob);
		s.lastOff = off;
		s.blowAt = now;
		if (off) {
			mob.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		}
		return off;
	}

	/**
	 * (from {@code MobBlowMixin}: the blow's attack damage, before enchantments and criticals) The off hand's blow strikes with its
	 * own weapon's damage in the main's place, under the same multipliers (a combo's, any other).
	 */
	public static double attackDamage(Mob mob, double value) {
		if (!offBlow(mob)) {
			return value;
		}
		double main = weaponDamage(mob.getMainHandItem()), other = weaponDamage(mob.getOffhandItem());
		AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		double flat = attack == null ? 0.0 : attack.getBaseValue();
		if (attack != null) {
			for (AttributeModifier modifier : attack.getModifiers()) {
				if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
					flat += modifier.amount();
				}
			}
		}
		// (the multipliers scale what's added: the main weapon's share out, the off one's in)
		double swapped = flat > 1.0E-6 ? value * Math.max(0.0, flat - main + other) / flat : value - main + other;
		return Math.max(1.0, swapped);
	}

	/** (from {@code MobBlowMixin}: the blow's weapon) The off hand's blow is its weapon's: its enchantments, its bonus, its wear. */
	public static ItemStack weapon(Mob mob, ItemStack main) {
		return offBlow(mob) ? mob.getOffhandItem() : main;
	}

	/** A weapon's own attack damage (what it adds in the main hand). */
	private static double weaponDamage(ItemStack weapon) {
		return weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
	}

	// ---- the shield on the back ------------------------------------------------------------------------------------------

	/** It carries a shield on its back (in the body slot). */
	static boolean onBack(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.BODY).has(DataComponents.BLOCKS_ATTACKS);
	}

	/** Its off-hand blade is on its back (in the body slot), stowed for the shield. */
	private static boolean bladeOnBack(Mob mob) {
		return blade(mob.getItemBySlot(EquipmentSlot.BODY));
	}

	/** (from {@code Downed}) Gear carried on the back: a shield or a stowed blade (nothing else goes in a humanoid's body slot). */
	static boolean backGear(ItemStack body) {
		return body.has(DataComponents.BLOCKS_ATTACKS) || blade(body);
	}

	/**
	 * (from {@code McaEquipmentMixin}) MCA's re-arm leaves {@code slot} as it is: with a shield on its back, its off hand (empty or a blade)
	 * and any hand MCA would give a shield; with its blade on its back, the shield in its off hand. So it never holds a second shield.
	 */
	public static boolean keeps(Mob villager, EquipmentSlot slot, ItemStack stack) {
		if (slot.getType() != EquipmentSlot.Type.HAND) {
			return false;
		}
		if (onBack(villager)) {
			return slot == EquipmentSlot.OFFHAND || stack.has(DataComponents.BLOCKS_ATTACKS);
		}
		return slot == EquipmentSlot.OFFHAND && bladeOnBack(villager);
	}

	/**
	 * (from {@link ShieldSense}) A shot is on its way to {@code mob}, or someone aims at it: a shield on its back starts to come to its
	 * hand (it isn't up to anything till it's there); one already in hand from the back stays there a while longer.
	 */
	static void threat(Mob mob) {
		boolean back = onBack(mob);
		if (!back && !mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return;
		}
		if (!back && STATES.get(mob) == null) {
			return;
		}
		State s = state(mob);
		long now = mob.level().getGameTime();
		s.threatAt = now;
		if (back && s.swapAt == 0 && canSwap(mob)) {
			start(mob, s, now, true);
		}
	}

	/** Whether its hands are free to swap: alive and standing, nothing in use, not mid-swing, and nothing of an MCA pack's own. */
	private static boolean canSwap(Mob mob) {
		if (!mob.isAlive() || Downed.isDowned(mob) || mob.isSleeping() || mob.isUsingItem() || MobCombat.swinging(mob)) {
			return false;
		}
		return !packed(mob, mob.getOffhandItem()) && !packed(mob, mob.getItemBySlot(EquipmentSlot.BODY));
	}

	/** {@code stack} is the very stack an MCA villager's pack holds. */
	private static boolean packed(Mob mob, ItemStack stack) {
		if (stack.isEmpty() || !(mob instanceof Villager v)) {
			return false;
		}
		SimpleContainer pack = v.getInventory();
		for (int i = 0; i < pack.getContainerSize(); i++) {
			if (pack.getItem(i) == stack) {
				return true;
			}
		}
		return false;
	}

	/** It reaches for it (heard, and the off arm seen to move); the stacks change places {@link #SWAP_TICKS} later. */
	private static void start(Mob mob, State s, long now, boolean toHand) {
		s.swapAt = now + SWAP_TICKS;
		s.toHand = toHand;
		mob.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), mob.getSoundSource(), 0.8F, 1.0F);
	}

	/** The back's stack and the off hand's change places, each with its drop chance. */
	private static void exchange(Mob mob) {
		ItemStack body = mob.getItemBySlot(EquipmentSlot.BODY), off = mob.getOffhandItem();
		float bodyDrop = mob.getDropChances().byEquipment(EquipmentSlot.BODY), offDrop = mob.getDropChances().byEquipment(EquipmentSlot.OFFHAND);
		mob.setItemSlot(EquipmentSlot.BODY, off);
		mob.setItemSlot(EquipmentSlot.OFFHAND, body);
		mob.setDropChance(EquipmentSlot.BODY, offDrop);
		mob.setDropChance(EquipmentSlot.OFFHAND, bodyDrop);
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), mob.getSoundSource(), 0.8F, 1.1F);
	}

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, State> e : new ArrayList<>(STATES.entrySet())) {
			Mob mob = e.getKey();
			State s = e.getValue();
			if (mob == null || mob.isRemoved() || !mob.isAlive()) {
				STATES.remove(mob);
				continue;
			}
			long now = mob.level().getGameTime();
			if (s.swapAt > 0) {
				if (now >= s.swapAt) {
					s.swapAt = 0;
					// (still free to: the shield where it was, nothing taken up meanwhile)
					boolean shieldBack = onBack(mob);
					if (s.toHand == shieldBack && canSwap(mob)) {
						exchange(mob);
					}
				}
				continue;
			}
			// a shield in hand that came off the back goes back once nothing has been shot or aimed at it a while, and it is down
			// (a dodger's shield in the off hand is always the back's: its dress puts any it has on its back)
			if (mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) && !onBack(mob) && NpcDodge.dodger(mob) && now - s.threatAt > STOW_AFTER
				&& !MobShields.guarding(mob) && canSwap(mob) && (mob.getItemBySlot(EquipmentSlot.BODY).isEmpty() || bladeOnBack(mob))) {
				start(mob, s, now, false);
				continue;
			}
			// (its off hand empty, the shield gone from it: the blade on its back comes back to the hand)
			if (mob.getOffhandItem().isEmpty() && bladeOnBack(mob) && canSwap(mob)) {
				start(mob, s, now, false);
				continue;
			}
			if (now - s.blowAt > 1200 && now - s.threatAt > 1200 && !onBack(mob) && !mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) && !bladeOnBack(mob)) {
				STATES.remove(mob);
			}
		}
	}

	// ---- armed -----------------------------------------------------------------------------------------------------------

	/**
	 * (from the bandits' dress, {@code IllagerSites.dress}, and an assassin's reveal) A dodger's kit: a shield in its off hand goes onto its
	 * back; an assassin with none may carry a battered one there; and with its off hand free, an assassin, and some bandits, take up a
	 * second blade. Only on bodies whose hands aren't an MCA pack's.
	 */
	static void arm(Mob mob, boolean assassin) {
		if (packed(mob, mob.getOffhandItem()) || packed(mob, mob.getMainHandItem())) {
			return;
		}
		boolean watched = false;
		ItemStack off = mob.getOffhandItem();
		if (off.has(DataComponents.BLOCKS_ATTACKS) && mob.getItemBySlot(EquipmentSlot.BODY).isEmpty()) {
			exchange(mob);
			watched = true;
		} else if (assassin && off.isEmpty() && mob.getItemBySlot(EquipmentSlot.BODY).isEmpty() && mob.getRandom().nextFloat() < ASSASSIN_BACK_SHIELD) {
			mob.setItemSlot(EquipmentSlot.BODY, ShieldBearers.battered(mob, DyeColor.BLACK));
			mob.setDropChance(EquipmentSlot.BODY, 0.0F);
			watched = true;
		}
		ItemStack main = mob.getMainHandItem();
		if (mob.getOffhandItem().isEmpty() && blade(main) && !Blows.twoHanded(main) && (assassin || mob.getRandom().nextFloat() < BANDIT_TWO_BLADES)) {
			// (an assassin's hidden blade has its fellow at the other wrist)
			mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(HiddenBlade.is(main) ? HiddenBlade.ITEM : OFF_BLADE));
			mob.setDropChance(EquipmentSlot.OFFHAND, mob.getDropChances().byEquipment(EquipmentSlot.MAINHAND));
		}
		if (watched) {
			state(mob);
		}
	}
}
