package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.61) Andy: "daggers and knives should be further differentiated, knife is for parrying and dagger will still
 * throw", then picked: Balkon's WeaponMod knife parries. Both used to throw on right click.
 * <ul>
 * <li>Right click with a WeaponMod knife ({@code weaponmod:knife.<tier>}) raises the weapon guard ({@code combat/Guard}:
 * block, parry windows, feathers), drawn as a guard, and letting go does nothing. Sneak + right click still throws it,
 * WeaponMod's own way. Which one a raise is gets decided as it starts ({@link #THROW}).</li>
 * <li>The knife and the Armory dagger of each tier swap their held damage: the parrying knife hits as the dagger did, the
 * thrown dagger as the knife did. Throws keep their own damage.</li>
 * </ul>
 * WeaponMod is optional and found by registry id: without it nothing here does anything. Hooked in by combat/Guard (the
 * raise), mixin/WeaponModMeleeUseMixin (duration, pose, release) and client HammerPoseMixin/HammerFirstPersonMixin.
 */
public final class KnifeGuard {
	/** Armory tier prefix for each WeaponMod tier (copper has no Armory dagger and is left alone). */
	private static final Map<String, String> TIERS = Map.of("wood", "wooden", "stone", "stone", "iron", "iron", "gold", "golden",
		"diamond", "diamond", "netherite", "netherite");

	/** Whether this player's current (or last) raise of a knife is a throw (sneaking) rather than the guard. */
	private static final AttachmentType<Boolean> THROW = AttachmentRegistry.create(JaymosPowers.id("knife_throw"));

	/** Each knife's and dagger's held damage (over the hand's), as registered, read before either is swapped. */
	private static @Nullable Map<Item, Double> original;

	private KnifeGuard() {
	}

	public static void init() {
		// the weapon guard is for weapons (combat/Guard.weaponGuard): make sure WeaponMod's knife counts as one
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> isKnife(item) && !item.components().has(DataComponents.WEAPON),
			(builder, lookup, item) -> builder.set(DataComponents.WEAPON, new Weapon(1, 0.0F))));
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> swapped(item) != null, (builder, lookup, item) -> {
			Item other = swapped(item);
			Map<Item, Double> damage = originals();
			Double theirs = other == null ? null : damage.get(other);
			ItemAttributeModifiers mine = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
			if (theirs == null || mine == null || !damage.containsKey(item)) {
				return;
			}
			ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder();
			for (ItemAttributeModifiers.Entry entry : mine.modifiers()) {
				if (!entry.attribute().is(Attributes.ATTACK_DAMAGE) || !entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)) {
					attributes.add(entry.attribute(), entry.modifier(), entry.slot());
				}
			}
			attributes.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, theirs,
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
			builder.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes.build());
		}));
	}

	public static boolean isKnife(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id.getNamespace().equals("weaponmod") && id.getPath().startsWith("knife.");
	}

	/**
	 * Right click with a knife in the main hand (both sides, from combat/Guard before the item's own use): sneaking, it's
	 * WeaponMod's throw ({@link InteractionResult#PASS}); otherwise the guard goes up.
	 */
	public static InteractionResult use(Player player, InteractionHand hand) {
		boolean thrown = player.isShiftKeyDown();
		player.setAttached(THROW, thrown);
		if (thrown) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/** {@code entity} holds a knife up as a guard (not drawn back for WeaponMod's throw). */
	public static boolean guarding(LivingEntity entity) {
		return entity.isUsingItem() && isKnife(entity.getUseItem().getItem()) && raisedAsGuard(entity);
	}

	/** A knife {@code entity} is using (or starting to) is up as the guard: a player who wasn't sneaking as it went up. */
	public static boolean raisedAsGuard(LivingEntity entity) {
		return entity instanceof Player && !entity.getAttachedOrElse(THROW, false);
	}

	/** Held up as a guard, a knife lasts as long as it's held (WeaponMod's own use might end at once). */
	public static int guardDuration() {
		return 72000;
	}

	/** The pose a knife held in use is drawn with, for {@code entity}; null leaves WeaponMod's. */
	public static @Nullable ItemUseAnimation pose(LivingEntity entity) {
		return guarding(entity) ? ItemUseAnimation.BLOCK : null;
	}

	/** The knife or dagger of the same tier whose damage {@code item} takes, or null for anything else. */
	private static @Nullable Item swapped(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		if (id.getNamespace().equals("weaponmod") && id.getPath().startsWith("knife.")) {
			String prefix = TIERS.get(id.getPath().substring("knife.".length()));
			return prefix == null ? null : BuiltInRegistries.ITEM.getOptional(JaymosPowers.id(prefix + "_dagger")).orElse(null);
		}
		if (id.getNamespace().equals(JaymosPowers.MOD_ID) && id.getPath().endsWith("_dagger")) {
			String prefix = id.getPath().substring(0, id.getPath().length() - "_dagger".length());
			for (Map.Entry<String, String> tier : TIERS.entrySet()) {
				if (tier.getValue().equals(prefix)) {
					return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("weaponmod", "knife." + tier.getKey())).orElse(null);
				}
			}
		}
		return null;
	}

	/** Read once, on the first swap, before any of them has been changed. */
	private static Map<Item, Double> originals() {
		if (original == null) {
			Map<Item, Double> damage = new HashMap<>();
			for (Map.Entry<String, String> tier : TIERS.entrySet()) {
				for (Identifier id : new Identifier[] {Identifier.fromNamespaceAndPath("weaponmod", "knife." + tier.getKey()),
					JaymosPowers.id(tier.getValue() + "_dagger")}) {
					BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
						Double base = baseDamage(item.components().get(DataComponents.ATTRIBUTE_MODIFIERS));
						if (base != null) {
							damage.put(item, base);
						}
					});
				}
			}
			original = damage;
		}
		return original;
	}

	private static @Nullable Double baseDamage(@Nullable ItemAttributeModifiers modifiers) {
		if (modifiers == null) {
			return null;
		}
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.modifier().id().equals(Item.BASE_ATTACK_DAMAGE_ID)) {
				return entry.modifier().amount();
			}
		}
		return null;
	}
}
