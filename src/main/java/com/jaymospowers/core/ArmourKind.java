package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
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
 * <p>
 * Armour can be worn in layers beyond the four slots: Jaymo's Kingdoms' harness (padding and mail under the chest piece,
 * plate on the arms and legs) is handed over as {@link #harness}. Its pieces are known by their tags alone
 * ({@link #layer}); each adds its weight to the load ({@link #HARNESS_SHARE}), and {@code combat/Blows} lets the layers
 * under the one struck soak what they are good against.
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
	/** How much of a body's load each harness piece is, by its weight (a whole layered harness on top of plate is about 0.6 more), and the most a load can be. */
	static final float HARNESS_SHARE = 0.1F, MAX_LOAD = 1.6F;
	/** Words in an item's id that make it plate, mail or padding (plate is asked first: "platemail" is plate). */
	static final String[] PLATE_WORDS = {"plate", "cuirass", "armet", "bascinet", "sallet", "barbute", "greathelm", "kettlehat"};
	static final String[] MAIL_WORDS = {"chain", "mail", "hauberk", "coif", "lamellar", "scale", "brigandine"};
	static final String[] CLOTH_WORDS = {"leather", "cloth", "wool", "gambeson", "padded", "quilted", "hide", "pelt", "fur", "robe", "hood",
		"tunic", "silk", "linen", "straw", "kaftan"};
	// ================================================================================================================

	public static final TagKey<Item> CLOTH_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("cloth_armor")),
		MAIL_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("mail_armor")),
		PLATE_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("plate_armor")),
		/** Padding with iron studs hammered in: a cut meets it a little less well than plain padding ({@code WeaponKind.Blow}). */
		STUDDED_TAG = TagKey.create(Registries.ITEM, JaymosPowers.id("studded_armor"));

	/**
	 * What a body wears in layers beyond its four slots, outermost first, at a place: "torso" (under the chest piece),
	 * "arms" (the limb's own plate, then the sleeves under it; nothing when no limb piece is worn), "legs" (over the
	 * leggings), "all" (every piece with weight). Jaymo's Kingdoms sets this at server start; without it there are none.
	 */
	public static BiFunction<LivingEntity, String, List<ItemStack>> harness = (body, place) -> List.of();

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

	/** Whether {@code stack} is padding with mail on it (in both the mail and the cloth tags): a blow meets the better of the two. */
	public static boolean padded(ItemStack stack) {
		return stack.is(MAIL_TAG) && stack.is(CLOTH_TAG);
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

	/** What a harness piece (worn in a layer, not a slot) is made like, by its tag alone; {@link #NONE} untagged or broken. */
	public static ArmourKind layer(ItemStack stack) {
		if (stack.isEmpty() || ArmourWear.broken(stack) || stack.isBroken()) {
			return NONE;
		}
		return stack.is(PLATE_TAG) ? PLATE : stack.is(MAIL_TAG) ? MAIL : stack.is(CLOTH_TAG) ? CLOTH : NONE;
	}

	/** The harness layers {@code body} wears at {@code place} ({@link #harness}), outermost first, as kinds (none left out). */
	public static List<ArmourKind> layers(LivingEntity body, String place) {
		List<ArmourKind> out = new ArrayList<>();
		for (ItemStack stack : harness.apply(body, place)) {
			ArmourKind kind = layer(stack);
			if (kind != NONE) {
				out.add(kind);
			}
		}
		return out;
	}

	/** What stands between a blow and {@code body} at {@code slot}: the piece worn there, or {@link #NONE} if it's bare or broken. */
	public static ArmourKind worn(LivingEntity body, EquipmentSlot slot) {
		ItemStack piece = body.getItemBySlot(slot);
		return ArmourWear.broken(piece) ? NONE : of(piece);
	}

	/**
	 * How heavily {@code body} is armoured, 0 (nothing on) to 1 (plate from head to foot): each piece's {@link #weight} by
	 * its slot's share. A broken piece weighs what it always did. Each harness piece adds its weight by
	 * {@link #HARNESS_SHARE}, up to {@link #MAX_LOAD} in all.
	 */
	public static float load(LivingEntity body) {
		boolean soaked = com.jaymospowers.core.wet.Wet.soaked(body);
		float load = slot(body, EquipmentSlot.HEAD, soaked) * HEAD_SHARE + slot(body, EquipmentSlot.CHEST, soaked) * CHEST_SHARE
			+ slot(body, EquipmentSlot.LEGS, soaked) * LEGS_SHARE + slot(body, EquipmentSlot.FEET, soaked) * FEET_SHARE;
		for (ItemStack piece : harness.apply(body, "all")) {
			load += weight(kind(piece), soaked) * HARNESS_SHARE * fit(piece, body);
		}
		return Math.min(MAX_LOAD, load);
	}

	private static float slot(LivingEntity body, EquipmentSlot slot, boolean soaked) {
		ItemStack piece = body.getItemBySlot(slot);
		return weight(of(piece), soaked) * fit(piece, body);
	}

	/**
	 * (Harness 5) A piece an armourer fitted to {@code body} (Jaymo's Kingdoms' {@code jaymoskingdoms:fitted}, the
	 * wearer's UUID) sits lighter on them: its weight times {@link #FITTED}. On anyone else it weighs what it always did.
	 */
	static final float FITTED = 0.75F;
	private static net.minecraft.core.component.@org.jspecify.annotations.Nullable DataComponentType<?> fittedType;
	private static boolean fittedLooked;

	private static float fit(ItemStack piece, LivingEntity body) {
		if (piece.isEmpty()) {
			return 1.0F;
		}
		if (!fittedLooked) {
			fittedLooked = true;
			fittedType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymoskingdoms", "fitted"));
		}
		return fittedType != null && body.getUUID().equals(piece.get(fittedType)) ? FITTED : 1.0F;
	}

	/** Soaked padding holds its weight again in water ({@code Wet}). */
	static final float SOAKED_CLOTH = 2.0F;

	private static float weight(ArmourKind kind, boolean soaked) {
		return soaked && kind == CLOTH ? kind.weight * SOAKED_CLOTH : kind.weight;
	}

	/** A harness piece's kind, broken or not (it weighs the same). */
	private static ArmourKind kind(ItemStack stack) {
		return stack.isEmpty() ? NONE : stack.is(PLATE_TAG) ? PLATE : stack.is(MAIL_TAG) ? MAIL : stack.is(CLOTH_TAG) ? CLOTH : NONE;
	}
}
