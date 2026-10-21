package com.jaymospowers.tinkering;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What a material or a tool brings beyond its stats. Each part brings its material's trait, so two bone parts make
 * Piercing II. The effects live in {@link ToolStats} (wear, speed, attack) and {@link TraitEffects} (hits, mining, kills).
 */
public enum Trait {
	CULTIVATED("cultivated", ChatFormatting.GOLD),
	STONEBOUND("stonebound", ChatFormatting.GRAY),
	JAGGED("jagged", ChatFormatting.DARK_GRAY),
	PIERCE("pierce", ChatFormatting.WHITE),
	DWARVEN("dwarven", ChatFormatting.GOLD),
	MAGNETIC("magnetic", ChatFormatting.GRAY),
	TANNED("tanned", ChatFormatting.GOLD),
	STRINGY("stringy", ChatFormatting.WHITE),
	SOLAR_POWERED("solar_powered", ChatFormatting.GREEN),
	SMITE("smite", ChatFormatting.YELLOW),
	SEVERING("severing", ChatFormatting.RED),
	STRIPPING("stripping", ChatFormatting.GOLD),
	TILLING("tilling", ChatFormatting.GREEN),
	SWEEPING("sweeping", ChatFormatting.AQUA),
	AREA("area", ChatFormatting.AQUA),
	SEARING("searing", ChatFormatting.RED),
	ENHANCED("enhanced", ChatFormatting.LIGHT_PURPLE),
	CRUMBLING("crumbling", ChatFormatting.LIGHT_PURPLE),
	TASTY("tasty", ChatFormatting.LIGHT_PURPLE),
	OVERSLIME("overslime", ChatFormatting.GREEN),
	TEMPERED("tempered", ChatFormatting.DARK_AQUA);

	public final String id;
	public final ChatFormatting colour;

	Trait(String id, ChatFormatting colour) {
		this.id = id;
		this.colour = colour;
	}

	public Component name(int level) {
		Component name = Component.translatable("trait.jaymospowers." + id);
		return level > 1 ? Component.empty().append(name).append(" ").append(Component.translatable("enchantment.level." + level)) : name;
	}

	public Component description() {
		return Component.translatable("trait.jaymospowers." + id + ".desc");
	}
}
