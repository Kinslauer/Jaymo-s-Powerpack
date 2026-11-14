package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/**
 * What a piece of worn armour is made like, for the blows that meet it ({@code combat/Blows}) and for how fast it
 * tires its wearer ({@code stamina/Exertion}): padding (cloth, leather, hides, furs), mail, or plate.
 * <p>
 * A piece's kind is read, in this order:
 * <ol>
 * <li>its item tag: {@link #PLATE_TAG}, {@link #MAIL_TAG} or {@link #CLOTH_TAG} (vanilla's sets, ours, and what Jaymo's
 * Kingdoms adds of its own and of Epic Knights'; a data pack can file any other mod's);</li>
 * <li>whether it protects at all: a piece with no armour points (a straw hat, a mask) is no armour;</li>
 * <li>its name ({@link #PLATE_WORDS}, {@link #MAIL_WORDS}, {@link #CLOTH_WORDS});</li>
 * <li>the sound it makes going on (leather's: padding; chain's: mail);</li>
 * <li>and anything left is plate.</li>
 * </ol>
 * An empty slot, and a piece worn out ({@link ArmourWear#broken}), is {@link #NONE}: nothing between the blow and the body.
 */
public enum ArmourKind {
	/** Nothing there, or a broken piece. */
	NONE(0.0F),
	/** Padding: cloth, leather, hides and furs. */
	CLOTH(0.2F),
	MAIL(0.6F),
	PLATE(1.0F);

	// ===== the knobs ================================================================================================
	/** How much of a body's load each slot's piece is (they add up to 1: a full suit of plate is a load of 1). */
	static final float HEAD_SHARE = 0.15F, CHEST_SHARE = 0.4F, LEGS_SHARE = 0.3F, FEET_SHARE = 0.15F;
	/** Words in an item's id that make it plate, mail or padding (plate is asked first: "platemail" is plate). */
	static final String[] PLATE_WORDS = {"plate", "cuirass", "armet", "bascinet", "sallet", "barbute", "greathelm", "kettlehat"};
	static final String[] MAIL_WORDS = {"chain", "mail", "hauberk", "coif", "lamellar", "scale", "brigandine"};
	static final String[] CLOTH_WORDS = {"leather", "cloth", "wool", "gambeson", "padded", "quilted", "hide", "pelt", "fur", "robe", "hood",
		"tunic", "silk", "linen", "straw", "kaftan"};
	// ================================================================================================================

	public static final TagKey<Item> CLOTH_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("cloth_armor")),
		MAIL_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("mail_armor")),
		PLATE_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("plate_armor"));

	/** How heavy a piece of this kind is to fight in, as a share of plate's weight. */
	public final float weight;

	ArmourKind(float weight) {
		this.weight = weight;
	}

	/** {@code armour.jaymospowers.<kind>}: its name on a piece's tooltip. */
	public String key() {
		return "armour.jaymospowers." + name().toLowerCase(Locale.ROOT);
	}

	/** What {@code stack} is made like, whole or broken; {@link #NONE} if it isn't a helmet, chest piece, leggings or boots. */
	public static ArmourKind of(ItemStack stack) {
		if (stack.isEmpty() || stack.has(DataComponents.GLIDER)) {
			return NONE;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
			return NONE;
		}
		if (stack.is(PLATE_TAG)) {
			return PLATE;
		}
		if (stack.is(MAIL_TAG)) {
			return MAIL;
		}
		if (stack.is(CLOTH_TAG)) {
			return CLOTH;
		}
		if (!protects(stack)) {
			return NONE;
		}
		// (every "chestplate" has "plate" in it: that word says nothing)
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT).replace("chestplate", "chest");
		if (has(path, PLATE_WORDS)) {
			return PLATE;
		}
		if (has(path, MAIL_WORDS)) {
			return MAIL;
		}
		if (has(path, CLOTH_WORDS)) {
			return CLOTH;
		}
		Holder<SoundEvent> sound = equippable.equipSound();
		if (sound.value() == SoundEvents.ARMOR_EQUIP_LEATHER.value()) {
			return CLOTH;
		}
		if (sound.value() == SoundEvents.ARMOR_EQUIP_CHAIN.value()) {
			return MAIL;
		}
		return PLATE;
	}

	private static boolean has(String path, String[] words) {
		for (String word : words) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static boolean protects(ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().is(Attributes.ARMOR) && entry.modifier().amount() > 0.0) {
				return true;
			}
		}
		return false;
	}

	/** What stands between a blow and {@code body} at {@code slot}: the piece worn there, or {@link #NONE} if it's bare or broken. */
	public static ArmourKind worn(LivingEntity body, EquipmentSlot slot) {
		ItemStack piece = body.getItemBySlot(slot);
		return ArmourWear.broken(piece) ? NONE : of(piece);
	}

	/**
	 * How heavily {@code body} is armoured, 0 (nothing on) to 1 (plate from head to foot): each piece's {@link #weight} by
	 * its slot's share. A broken piece weighs what it always did.
	 */
	public static float load(LivingEntity body) {
		return of(body.getItemBySlot(EquipmentSlot.HEAD)).weight * HEAD_SHARE + of(body.getItemBySlot(EquipmentSlot.CHEST)).weight * CHEST_SHARE
			+ of(body.getItemBySlot(EquipmentSlot.LEGS)).weight * LEGS_SHARE + of(body.getItemBySlot(EquipmentSlot.FEET)).weight * FEET_SHARE;
	}
}
