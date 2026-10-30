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
	TEMPERED("tempered", ChatFormatting.DARK_AQUA),
	// (0.15.x) the melee weapons and big tools' own traits (their effects: ToolActions, TraitEffects)
	PADDED("padded", ChatFormatting.WHITE),
	OFFHAND("offhand_attack", ChatFormatting.AQUA),
	SHEARS("shears", ChatFormatting.GREEN),
	HARVEST("harvest", ChatFormatting.GREEN),
	PATHING("pathing", ChatFormatting.GOLD),
	KNOCKBACK("knockback", ChatFormatting.WHITE),
	WAR_CHARGE("war_charge", ChatFormatting.RED),
	CHARGE_ATTACK("charge_attack", ChatFormatting.RED),
	BLOCKING("blocking", ChatFormatting.AQUA),
	BONKING("bonking", ChatFormatting.YELLOW),
	TIMBER("timber", ChatFormatting.GREEN),
	VEIN("vein", ChatFormatting.AQUA),
	// (0.15.x) feather: arrows and bows fly truer
	AERODYNAMIC("aerodynamic", ChatFormatting.WHITE),
	// (0.18.56) back home: star metal's own trait, and the two edges a quench in a far world's cold or ember gives
	STARLIT("starlit", ChatFormatting.LIGHT_PURPLE),
	COLD_EDGE("cold_edge", ChatFormatting.AQUA),
	HOT_EDGE("hot_edge", ChatFormatting.GOLD);

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
