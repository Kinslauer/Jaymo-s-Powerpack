package com.jaymospowers.tinkering.smithing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) How well a head was hammered out on the Smithing Anvil, after Overgeared's forging quality (Copyright (c)
 * phuccom000, MIT). It rides the head into the Tool Station and on the finished tool. Ungraded things (vanilla gear,
 * old tools) are left as they are. Tune the numbers here.
 */
public enum Grade {
	POOR("poor", ChatFormatting.GRAY, 0.75F, -1.0F, -0.1F, 0.85F, -1.0F),
	WELL("well", ChatFormatting.WHITE, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F),
	EXPERT("expert", ChatFormatting.GREEN, 1.1F, 0.5F, 0.05F, 1.1F, 0.5F),
	PERFECT("perfect", ChatFormatting.AQUA, 1.2F, 1.0F, 0.1F, 1.2F, 1.0F),
	MASTER("master", ChatFormatting.GOLD, 1.3F, 2.0F, 0.15F, 1.3F, 1.5F);

	public final String id;
	public final ChatFormatting colour;
	/** Durability times this; attack damage plus this; attack speed plus this; mining speed times this; armour plus this. */
	public final float durability, attack, attackSpeed, mining, armor;

	Grade(String id, ChatFormatting colour, float durability, float attack, float attackSpeed, float mining, float armor) {
		this.id = id;
		this.colour = colour;
		this.durability = durability;
		this.attack = attack;
		this.attackSpeed = attackSpeed;
		this.mining = mining;
		this.armor = armor;
	}

	/** "Poorly Forged" ... "Masterwork", in its colour. */
	public MutableComponent displayName() {
		return Component.translatable("grade.jaymospowers." + id).withStyle(colour);
	}

	public Grade lower() {
		return this == POOR ? POOR : values()[ordinal() - 1];
	}

	public Grade higher() {
		return this == MASTER ? MASTER : values()[ordinal() + 1];
	}

	public static Grade min(Grade a, Grade b) {
		return a.ordinal() <= b.ordinal() ? a : b;
	}

	public boolean atLeast(Grade other) {
		return ordinal() >= other.ordinal();
	}

	public static @Nullable Grade byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (Grade grade : values()) {
			if (grade.id.equals(id)) {
				return grade;
			}
		}
		return null;
	}
}
