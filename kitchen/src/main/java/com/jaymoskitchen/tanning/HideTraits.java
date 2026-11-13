package com.jaymoskitchen.tanning;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/**
 * The small trait leather armour takes from its hide ({@link HideOrigin}), one per piece; a full set of one hide adds
 * nothing more. None of it is written anywhere in game (Andy: "the traits are fine, but dont list them anywhere in
 * game"): the attribute ones are hidden from the tooltip, and the rest are just how the leather behaves.
 *
 * <table>
 * <tr><td>Cow</td><td>Sturdy</td><td>+15% durability</td></tr>
 * <tr><td>Horse</td><td>Supple</td><td>sprinting costs 10% less hunger in a full set</td></tr>
 * <tr><td>Goat</td><td>Sure-footed</td><td>10% less fall damage per piece</td></tr>
 * <tr><td>Llama</td><td>Warm-backed</td><td>half a warm piece ({@link com.jaymoskitchen.climate.BodyHeat})</td></tr>
 * <tr><td>Hoglin</td><td>Thick</td><td>+0.5 armour toughness</td></tr>
 * <tr><td>Mooshroom</td><td>Spore-proof</td><td>poison 20% shorter in a full set</td></tr>
 * <tr><td>Rabbit</td><td>Light</td><td>half a cool piece</td></tr>
 * <tr><td>Alligator, crocodile</td><td>Scaled</td><td>no extra wear from soaking ({@link #SOAK_WEAR_EVERY})</td></tr>
 * <tr><td>Rhino</td><td>Hide-plated</td><td>+1 armour, on the chestplate only</td></tr>
 * <tr><td>Zebra, deer</td><td>Hushed</td><td>mobs notice you 20% less far off in a full set</td></tr>
 * </table>
 */
public final class HideTraits {
	static final Identifier COW = mc("cow"), HORSE = mc("horse"), GOAT = mc("goat"), LLAMA = mc("llama"), HOGLIN = mc("hoglin"),
		MOOSHROOM = mc("mooshroom"), RABBIT = mc("rabbit"), RHINO = nat("rhino"), ALLIGATOR = nat("alligator"),
		CROCODILE = nat("crocodile"), ZEBRA = nat("zebra"), DEER = nat("deer");

	/** Sturdy: this much more durability. */
	static final float STURDY = 1.15F;
	/** Sure-footed: this much less fall damage, per piece. */
	static final double SURE_FOOTED = -0.1;
	/** Thick: this much armour toughness. Hide-plated: this much armour, chestplate only. */
	static final double THICK = 0.5, HIDE_PLATED = 1.0;
	/** Supple, Spore-proof and Hushed, per piece (a quarter of a full set's). */
	public static final float SUPPLE = 0.025F, SPORE_PROOF = 0.05F, HUSHED = 0.05F;
	/** Warm-backed and Light: each piece counts as this much of a warm or cool piece. */
	public static final float HALF_PIECE = 0.5F;
	/** Leather soaked through (in water) loses a point of durability every this many ticks, a piece; Scaled hide doesn't. */
	static final int SOAK_WEAR_EVERY = 600;

	static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private HideTraits() {
	}

	private static Identifier mc(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	private static Identifier nat(String path) {
		return Identifier.fromNamespaceAndPath("naturalist", path);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HideTraits::soakWear);
	}

	/** A leather piece just made of one animal's leather takes what it keeps on the item: durability and armour. */
	static void onMade(ItemStack piece, Identifier animal) {
		Equippable equippable = piece.get(DataComponents.EQUIPPABLE);
		if (equippable == null || !isLeather(piece)) {
			return;
		}
		if (animal.equals(COW) && piece.has(DataComponents.MAX_DAMAGE)) {
			piece.set(DataComponents.MAX_DAMAGE, Math.round(piece.getMaxDamage() * STURDY));
		}
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(equippable.slot());
		if (animal.equals(GOAT)) {
			hidden(piece, Attributes.FALL_DAMAGE_MULTIPLIER, SURE_FOOTED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, group, "sure_footed");
		} else if (animal.equals(HOGLIN)) {
			hidden(piece, Attributes.ARMOR_TOUGHNESS, THICK, AttributeModifier.Operation.ADD_VALUE, group, "thick");
		} else if (animal.equals(RHINO) && equippable.slot() == EquipmentSlot.CHEST) {
			hidden(piece, Attributes.ARMOR, HIDE_PLATED, AttributeModifier.Operation.ADD_VALUE, group, "hide_plated");
		}
	}

	/** An attribute the piece gives that its tooltip doesn't show. */
	private static void hidden(ItemStack piece, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double amount,
		AttributeModifier.Operation operation, EquipmentSlotGroup group, String name) {
		ItemAttributeModifiers had = piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		java.util.List<ItemAttributeModifiers.Entry> entries = new java.util.ArrayList<>(had.modifiers());
		AttributeModifier modifier = new AttributeModifier(JaymosKitchen.id("hide_" + name + "_" + group.getSerializedName()), amount, operation);
		entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, group, ItemAttributeModifiers.Display.hidden()));
		piece.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
	}

	/** How many worn armour pieces are of this animal's leather. */
	public static int worn(LivingEntity wearer, Identifier... animals) {
		int n = 0;
		for (EquipmentSlot slot : ARMOUR) {
			Identifier animal = HideOrigin.of(wearer.getItemBySlot(slot));
			for (Identifier want : animals) {
				if (want.equals(animal)) {
					n++;
					break;
				}
			}
		}
		return n;
	}

	/** Supple: what sprinting costs in hunger, for this wearer. */
	public static float sprintCost(LivingEntity wearer, float cost) {
		int n = worn(wearer, HORSE);
		return n == 0 ? cost : cost * (1.0F - SUPPLE * n);
	}

	/** Spore-proof: poison put on this wearer runs shorter. */
	public static @Nullable MobEffectInstance shorterPoison(LivingEntity wearer, MobEffectInstance effect) {
		if (!effect.is(MobEffects.POISON) || effect.isInfiniteDuration()) {
			return null;
		}
		int n = worn(wearer, MOOSHROOM);
		if (n == 0) {
			return null;
		}
		int shorter = Math.max(1, Math.round(effect.getDuration() * (1.0F - SPORE_PROOF * n)));
		return new MobEffectInstance(effect.getEffect(), shorter, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	/** Hushed: how far off mobs notice this wearer, as a share. */
	public static double hushed(LivingEntity wearer) {
		int n = worn(wearer, ZEBRA, DEER);
		return 1.0 - HUSHED * n;
	}

	/** Warm-backed and Light: half a warm or cool piece for each piece of llama or rabbit leather. */
	public static float warmPieces(LivingEntity wearer) {
		return HALF_PIECE * worn(wearer, LLAMA);
	}

	public static float coolPieces(LivingEntity wearer) {
		return HALF_PIECE * worn(wearer, RABBIT);
	}

	/**
	 * Leather left soaking wears: each leather piece worn in water loses a point now and then, as wet leather goes stiff and
	 * cracks. Scaled hide (alligator, crocodile) doesn't.
	 */
	private static void soakWear(MinecraftServer server) {
		if (server.getTickCount() % SOAK_WEAR_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isInWater() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			for (EquipmentSlot slot : ARMOUR) {
				ItemStack piece = player.getItemBySlot(slot);
				if (!isLeather(piece) || piece.getDamageValue() >= piece.getMaxDamage() - 1) {
					continue;
				}
				Identifier animal = HideOrigin.of(piece);
				if (ALLIGATOR.equals(animal) || CROCODILE.equals(animal)) {
					continue;
				}
				piece.setDamageValue(piece.getDamageValue() + 1);
			}
		}
	}

	static boolean isLeather(ItemStack piece) {
		return piece.is(Items.LEATHER_HELMET) || piece.is(Items.LEATHER_CHESTPLATE) || piece.is(Items.LEATHER_LEGGINGS) || piece.is(Items.LEATHER_BOOTS);
	}
}
