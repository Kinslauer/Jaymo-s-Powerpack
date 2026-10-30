package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.TinkerMaterial;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A liquid in the smeltery: molten metals and the odd extra the alloys need. They aren't world fluids, just amounts
 * the smeltery, faucet and casting blocks pass around (mB; an ingot is {@link #INGOT}). The id is its texture name.
 */
public enum Molten {
	IRON("iron", 0xFFA81212, TinkerMaterial.IRON),
	GOLD("gold", 0xFFF6D609, null),
	COPPER("copper", 0xFFE77C56, TinkerMaterial.COPPER),
	SILVER("silver", 0xFFD7E3EC, TinkerMaterial.SILVER),
	AMETHYST("amethyst", 0xFFA67DE6, null),
	ROSE_GOLD("rose_gold", 0xFFF7B2A0, TinkerMaterial.ROSE_GOLD),
	AMETHYST_BRONZE("amethyst_bronze", 0xFFD49BD9, TinkerMaterial.AMETHYST_BRONZE),
	PIG_IRON("pig_iron", 0xFFEFA9A9, TinkerMaterial.PIG_IRON),
	SLIMESTEEL("slimesteel", 0xFF74C8C7, TinkerMaterial.SLIMESTEEL),
	STEEL("steel", 0xFFF2B866, TinkerMaterial.STEEL),
	// (0.18.54) the far worlds' first metals (power/zfighter/farworld)
	PALE_ALLOY("pale_alloy", 0xFFD8E4F0, TinkerMaterial.PALE_ALLOY),
	RUST_IRON("rust_iron", 0xFFB5532A, TinkerMaterial.RUST_IRON),
	ICE_IRON("ice_iron", 0xFF8FD3F0, TinkerMaterial.ICE_IRON),
	CINDER_BRONZE("cinder_bronze", 0xFFE0782A, TinkerMaterial.CINDER_BRONZE),
	VOID_SHARD("void_shard", 0xFF7A3FD0, TinkerMaterial.VOID_SHARD),
	AMBER_BRONZE("amber_bronze", 0xFFE6A82E, TinkerMaterial.AMBER_BRONZE),
	SLIME("slime", 0xFF7EBE5A, null),
	HONEY("honey", 0xFFF9A42C, null),
	BLOOD("blood", 0xFF6B0000, null);

	/** mB in an ingot, a nugget and a block of metal (Tinkers' numbers). */
	public static final int INGOT = 90, NUGGET = 10, BLOCK = 810;

	public final String id;
	/** Its colour (ARGB), for the faucet's stream and anything without its texture. */
	public final int colour;
	/** The tool material it casts into parts of, or null. */
	public final @Nullable TinkerMaterial material;

	Molten(String id, int colour, @Nullable TinkerMaterial material) {
		this.id = id;
		this.colour = colour;
		this.material = material;
	}

	public Component displayName() {
		return Component.translatable("molten.jaymospowers." + id);
	}

	public static @Nullable Molten byId(String id) {
		for (Molten molten : values()) {
			if (molten.id.equals(id)) {
				return molten;
			}
		}
		return null;
	}

	public static @Nullable Molten of(@Nullable TinkerMaterial material) {
		for (Molten molten : values()) {
			if (molten.material != null && molten.material == material) {
				return molten;
			}
		}
		return null;
	}

	/** An amount as a player reads it: "2 ingots, 30 mB". */
	public static Component amount(int mb, boolean metal) {
		if (!metal || mb < INGOT) {
			return Component.translatable("molten.jaymospowers.mb", mb);
		}
		int ingots = mb / INGOT, rest = mb % INGOT;
		Component ing = Component.translatable(ingots == 1 ? "molten.jaymospowers.ingot" : "molten.jaymospowers.ingots", ingots);
		return rest == 0 ? ing : Component.translatable("molten.jaymospowers.and", ing, Component.translatable("molten.jaymospowers.mb", rest));
	}

	/** Measured in ingots (not slime, honey, blood or amethyst). */
	public boolean metal() {
		return this != SLIME && this != HONEY && this != BLOOD && this != AMETHYST;
	}
}
