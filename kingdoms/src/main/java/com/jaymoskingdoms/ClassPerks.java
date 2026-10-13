package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.6.0) What each class ({@link Classes}) is good with, as attribute modifiers put on the player whenever their class,
 * hand or armour changes, and as the few things that need a tick (an Archer's drawn bow). Every number is here.
 *
 * <ul>
 * <li><b>Thief:</b> light blades hit harder and swing faster; leather, chain and hide give a point more armour a piece.</li>
 * <li><b>Warlord:</b> two-handed weapons hit harder; heavy armour weighs half as much.</li>
 * <li><b>Mage:</b> two hearts fewer (the staff and the spells are theirs, stage 2).</li>
 * <li><b>Archer:</b> arrows hit harder ({@link Classes}), quicker on their feet; with a bow drawn they jump two blocks
 * and land soft.</li>
 * <li><b>Survivalist:</b> hunger drains slower, air lasts longer, a heart more, falls hurt less, a little quicker.</li>
 * <li><b>Brute:</b> maces, flails and axes hit harder, two hearts more, a raised shield stops what gets through other
 * shields and comes back sooner when knocked aside; slower, and heavy armour weighs twice as much.</li>
 * <li><b>Marksman:</b> a heart fewer; light armour gives a point more a piece (the guns are stage 3).</li>
 * <li><b>Sorcerer:</b> nothing passive: their element and blade are stage 2.</li>
 * </ul>
 */
public final class ClassPerks {
	// Thief
	static final double THIEF_DAMAGE = 0.25, THIEF_SPEED = 0.2, THIEF_ARMOR_PER_PIECE = 1.0;
	// Warlord
	static final double WARLORD_DAMAGE = 0.25, WARLORD_WEIGHT = 0.5;
	// Mage
	static final double MAGE_HEALTH = -4.0;
	// Archer
	static final double ARCHER_DAMAGE = 1.25, ARCHER_MOVE = 0.1, ARCHER_JUMP = 0.12, ARCHER_FALL = -0.5;
	// Survivalist
	static final float SURVIVALIST_EXHAUSTION = 0.7F;
	static final double SURVIVALIST_OXYGEN = 1.0, SURVIVALIST_HEALTH = 2.0, SURVIVALIST_FALL = -0.3, SURVIVALIST_MOVE = 0.05;
	// Brute
	static final double BRUTE_DAMAGE = 0.3, BRUTE_HEALTH = 4.0, BRUTE_MOVE = -0.08, BRUTE_WEIGHT = 2.0;
	/** Brute: the share of damage a raised shield still stops when it would otherwise get past; how long a knocked-aside shield stays down (ticks, vanilla 100). */
	static final float BRUTE_BLOCK_LEFTOVER = 0.5F;
	static final int BRUTE_SHIELD_DOWN = 50;
	// Marksman
	static final double MARKSMAN_HEALTH = -2.0, MARKSMAN_ARMOR_PER_PIECE = 1.0;
	/** Heavy armour's weight on everyone: this share off movement speed a piece. */
	static final double HEAVY_ARMOR_SLOW = 0.015;

	public static final TagKey<Item> LIGHT_BLADES = tag("light_blades"), TWO_HANDED = tag("two_handed"), BLUDGEONS = tag("bludgeons"),
		GUNS = tag("guns"), LIGHT_ARMOR = tag("light_armor"), HEAVY_ARMOR = tag("heavy_armor");

	static final String DAMAGE_ID = "class_damage", ATTACK_SPEED_ID = "class_attack_speed", ARMOR_ID = "class_armor", HEALTH_ID = "class_health",
		MOVE_ID = "class_move", WEIGHT_ID = "class_weight", OXYGEN_ID = "class_oxygen", FALL_ID = "class_fall", JUMP_ID = "class_jump",
		DRAW_FALL_ID = "class_draw_fall";

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Player → whether they had a bow drawn last tick; whether their shield was down. */
	private static final Map<UUID, Boolean> DRAWING = new HashMap<>(), SHIELD_DOWN = new HashMap<>();

	private ClassPerks() {
	}

	private static TagKey<Item> tag(String name) {
		return TagKey.create(Registries.ITEM, JaymosKingdoms.id(name));
	}

	static void clear() {
		DRAWING.clear();
		SHIELD_DOWN.clear();
	}

	/** The factor on {@code player}'s food exhaustion (a Survivalist's hunger drains slower). */
	public static float exhaustion(ServerPlayer player) {
		return Classes.is(player, PlayerClass.SURVIVALIST) ? SURVIVALIST_EXHAUSTION : 1.0F;
	}

	/** Everything a class does through attributes, from the class, the held weapon and the armour worn now. */
	static void attributes(ServerPlayer player) {
		PlayerClass cls = Classes.classOf(player);
		ItemStack held = player.getMainHandItem();
		int light = 0, heavy = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = player.getItemBySlot(slot);
			if (piece.is(LIGHT_ARMOR)) {
				light++;
			} else if (piece.is(HEAVY_ARMOR)) {
				heavy++;
			}
		}
		double damage = 0.0, attackSpeed = 0.0, armor = 0.0, health = 0.0, move = 0.0, weight = HEAVY_ARMOR_SLOW * heavy, oxygen = 0.0, fall = 0.0;
		if (cls != null) {
			switch (cls) {
				case THIEF -> {
					if (held.is(LIGHT_BLADES)) {
						damage = THIEF_DAMAGE;
						attackSpeed = THIEF_SPEED;
					}
					armor = THIEF_ARMOR_PER_PIECE * light;
				}
				case WARLORD -> {
					if (held.is(TWO_HANDED)) {
						damage = WARLORD_DAMAGE;
					}
					weight *= WARLORD_WEIGHT;
				}
				case MAGE -> health = MAGE_HEALTH;
				case ARCHER -> move = ARCHER_MOVE;
				case SURVIVALIST -> {
					oxygen = SURVIVALIST_OXYGEN;
					health = SURVIVALIST_HEALTH;
					fall = SURVIVALIST_FALL;
					move = SURVIVALIST_MOVE;
				}
				case BRUTE -> {
					if (held.is(BLUDGEONS)) {
						damage = BRUTE_DAMAGE;
					}
					health = BRUTE_HEALTH;
					move = BRUTE_MOVE;
					weight *= BRUTE_WEIGHT;
				}
				case MARKSMAN -> {
					health = MARKSMAN_HEALTH;
					armor = MARKSMAN_ARMOR_PER_PIECE * light;
				}
				case SORCERER -> {
				}
			}
		}
		float before = player.getMaxHealth();
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.ARMOR, ARMOR_ID, armor, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, MOVE_ID, move, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, WEIGHT_ID, -weight, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.OXYGEN_BONUS, OXYGEN_ID, oxygen, AttributeModifier.Operation.ADD_VALUE);
		// (a share off what's left: a power that takes fall damage to nothing keeps it at nothing, Andy's rule)
		Skills.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, fall, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// (fewer hearts: the health bar shrinks to fit; more: the new hearts come empty, to be eaten back)
		if (player.getHealth() > player.getMaxHealth() || before > player.getMaxHealth()) {
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
		if (!Classes.is(player, PlayerClass.ARCHER)) {
			drawing(player, false);
		}
	}

	/** An Archer's drawn bow: the two-block jump and the soft landing while it's drawn. */
	private static void drawing(ServerPlayer player, boolean drawing) {
		Boolean was = DRAWING.put(player.getUUID(), drawing);
		if (was != null && was == drawing) {
			return;
		}
		Skills.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, drawing ? ARCHER_JUMP : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, DRAW_FALL_ID, drawing ? ARCHER_FALL : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** A bow or crossbow being drawn or loaded. */
	static boolean drawingBow(ServerPlayer player) {
		if (!player.isUsingItem()) {
			return false;
		}
		ItemStack use = player.getUseItem();
		return use.is(Items.BOW) || use.is(Items.CROSSBOW) || use.is(ItemTags.BOW_ENCHANTABLE) || use.is(ItemTags.CROSSBOW_ENCHANTABLE);
	}

	static void tick(ServerPlayer player) {
		PlayerClass cls = Classes.classOf(player);
		if (cls == PlayerClass.ARCHER) {
			drawing(player, drawingBow(player));
		} else if (cls == PlayerClass.BRUTE) {
			// a shield knocked aside (an axe, a mace) comes back in half the time: the cooldown is put back shorter when it starts
			ItemStack shield = player.getOffhandItem().is(ItemTags.SHIELD_ENCHANTABLE) ? player.getOffhandItem()
				: player.getMainHandItem().is(ItemTags.SHIELD_ENCHANTABLE) ? player.getMainHandItem() : ItemStack.EMPTY;
			boolean down = !shield.isEmpty() && player.getCooldowns().isOnCooldown(shield);
			Boolean was = SHIELD_DOWN.put(player.getUUID(), down);
			if (down && (was == null || !was)) {
				player.getCooldowns().addCooldown(shield, BRUTE_SHIELD_DOWN);
			}
		}
	}

	/**
	 * A Brute with a shield raised: the share of {@code amount} that still gets through. Called for damage a raised shield
	 * didn't stop (what bypasses shields: explosions, magic, fire, and the leftover of a partial block).
	 */
	public static float blocked(ServerPlayer player, float amount) {
		return Classes.is(player, PlayerClass.BRUTE) && player.isBlocking() ? amount * (1.0F - BRUTE_BLOCK_LEFTOVER) : amount;
	}
}
