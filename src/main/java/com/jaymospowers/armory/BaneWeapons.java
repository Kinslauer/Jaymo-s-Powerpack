package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.WeaponParts;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.19, Kingdoms Rivals patch 6b) A Bane's own weapon: the grave-knight's Gravesworn, the outlaw warlord's Grudge, the
 * demon-sworn's Hornscythe. Kingdoms arms each Bane with its kind's, steeped in one of the Sorcerer's elements
 * ({@link #ELEMENT}); end it with its marks and its head is yours (found only, like the kings' heads), put together at the Tool
 * Station, its element carried onto the weapon. Its blows burst with the element now and then; Kingdoms does that
 * ({@code BaneArms}), since the elements are Kingdoms' magic. Without Kingdoms it is a plain fine blade.
 */
public final class BaneWeapons {
	/** The weapon, its head (a {@link WeaponParts} part), the Kingdoms Bane kind that carries it, and its numbers. */
	public enum Kind {
		GRAVESWORN("gravesworn", WeaponParts.GRAVESWORN_BLADE, "knight", 7.0F, -3.0F),
		GRUDGE("grudge", WeaponParts.GRUDGE_HEAD, "warlord", 8.0F, -3.1F),
		HORNSCYTHE("hornscythe", WeaponParts.HORNSCYTHE_BLADE, "demon", 6.5F, -2.8F);

		public final String id, head, bane;
		final float damage, speed;

		Kind(String id, String head, String bane, float damage, float speed) {
			this.id = id;
			this.head = head;
			this.bane = bane;
			this.damage = damage;
			this.speed = speed;
		}
	}

	/** On a Bane's weapon and its head: its element (Kingdoms' Sorcerer element ids: fire, ice, death, blood...). */
	public static DataComponentType<String> ELEMENT;

	public static Item GRAVESWORN, GRUDGE, HORNSCYTHE;

	private BaneWeapons() {
	}

	/** Item properties, for {@code Armory.item}. */
	static Item.Properties props(Kind kind, Item.Properties p) {
		return p.sword(ToolMaterial.DIAMOND, kind.damage, kind.speed).rarity(Rarity.EPIC).fireResistant();
	}

	static void registerComponents() {
		ELEMENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("bane_element"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
	}

	public static List<Item> all() {
		return List.of(GRAVESWORN, GRUDGE, HORNSCYTHE);
	}

	/** (From the Tool Station) A Bane's head's element and its "taken from" line, carried onto the weapon it makes. */
	public static @Nullable ItemStack carry(ItemStack head, @Nullable ItemStack weapon) {
		if (weapon == null || weapon.isEmpty() || ELEMENT == null) {
			return weapon;
		}
		String element = head.get(ELEMENT);
		if (element != null) {
			weapon.set(ELEMENT, element);
		}
		if (head.has(DataComponents.LORE)) {
			weapon.set(DataComponents.LORE, head.get(DataComponents.LORE));
		}
		return weapon;
	}

	/** Its element's line, for the weapon's tooltip or its head's. */
	public static void tooltip(ItemStack stack, Consumer<Component> builder) {
		String element = ELEMENT == null ? null : stack.get(ELEMENT);
		if (element != null) {
			builder.accept(Component.translatable("tooltip.jaymospowers.bane_weapon.element",
				Component.translatable("element.jaymospowers.bane." + element)).withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** A Bane's weapon: its element's line under its name. */
	public static class BaneWeaponItem extends Item {
		public final Kind kind;

		public BaneWeaponItem(Item.Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			tooltip(stack, builder);
			builder.accept(Component.translatable("item.jaymospowers.bane_weapon." + this.kind.id + ".desc").withStyle(ChatFormatting.GRAY));
		}
	}
}
