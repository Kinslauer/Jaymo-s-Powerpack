package com.jaymospowers.alchemy.brewing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.13) How well a brew was made in the cauldron, from Crude to Masterwork. Plain is the same as a vanilla potion.
 * Tune the multipliers here.
 */
public enum BrewGrade {
	CRUDE("crude", ChatFormatting.GRAY, 0.75F, 1.15F),
	PLAIN("plain", ChatFormatting.WHITE, 1.0F, 1.0F),
	FINE("fine", ChatFormatting.GREEN, 1.25F, 0.9F),
	EXPERT("expert", ChatFormatting.AQUA, 1.5F, 0.8F),
	MASTERWORK("masterwork", ChatFormatting.GOLD, 2.0F, 0.6F);

	public final String id;
	public final ChatFormatting colour;
	/** Effect durations times this; drinking time times this. */
	public final float duration, drink;

	BrewGrade(String id, ChatFormatting colour, float duration, float drink) {
		this.id = id;
		this.colour = colour;
		this.duration = duration;
		this.drink = drink;
	}

	/** "Crude Brew" ... "Masterwork Brew", in its colour. */
	public MutableComponent displayName() {
		return Component.translatable("brew_grade.jaymospowers." + id).withStyle(colour);
	}

	/** One grade up; Masterwork stays. */
	public BrewGrade higher() {
		return this == MASTERWORK ? MASTERWORK : values()[ordinal() + 1];
	}

	public boolean atLeast(BrewGrade other) {
		return ordinal() >= other.ordinal();
	}

	public static @Nullable BrewGrade byId(String id) {
		for (BrewGrade grade : values()) {
			if (grade.id.equals(id)) {
				return grade;
			}
		}
		return null;
	}
}
