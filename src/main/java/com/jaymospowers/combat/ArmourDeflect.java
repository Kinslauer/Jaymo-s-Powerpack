package com.jaymospowers.combat;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * What each armour material makes of a cut, a thrust and a blunt blow, on top of what its make already does
 * ({@link WeaponKind.Blow#against}: padding, mail or plate). A share under 1 turns more of the blow. Leather soaks the
 * blunt blow but lets a blade through; chain turns a cut but a thrust finds its rings; iron turns blades and rings with
 * the blunt blow; gold is soft; diamond and netherite turn most of everything. A material not named here changes nothing.
 * The material is read from the piece's item id. Rust eats into it: rusted mail and plate turn less ({@link #RUSTED}).
 */
public final class ArmourDeflect {
	// ===== the knobs: cut, thrust, blunt ============================================================================
	private static final float[] LEATHER = {1.0F, 1.0F, 0.85F};
	private static final float[] CHAIN = {0.85F, 1.1F, 1.0F};
	private static final float[] IRON = {0.9F, 0.95F, 1.1F};
	private static final float[] GOLD = {1.05F, 1.05F, 1.1F};
	private static final float[] DIAMOND = {0.85F, 0.85F, 0.9F};
	private static final float[] NETHERITE = {0.8F, 0.8F, 0.85F};
	/** Fully rusted mail or plate ({@link com.jaymospowers.core.wet.Rust#MAX}) lets this much more of every blow through (after Rust, CC0). */
	private static final float RUSTED = 0.25F;
	// ================================================================================================================

	private ArmourDeflect() {
	}

	/** The share of a {@code blow} {@code piece}'s material lets through, against what its make already lets through (1: no change). */
	public static float share(ItemStack piece, WeaponKind.Blow blow) {
		float[] material = material(piece);
		float rusted = 1.0F + RUSTED * com.jaymospowers.core.wet.Rust.rust(piece) / com.jaymospowers.core.wet.Rust.MAX;
		return (material == null ? 1.0F : material[blow.ordinal()]) * rusted;
	}

	private static float[] material(ItemStack piece) {
		if (piece.isEmpty()) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath().toLowerCase(Locale.ROOT);
		if (path.contains("netherite")) {
			return NETHERITE;
		}
		if (path.contains("diamond")) {
			return DIAMOND;
		}
		if (path.contains("chain") || path.contains("mail")) {
			return CHAIN;
		}
		if (path.contains("iron") || path.contains("steel")) {
			return IRON;
		}
		if (path.contains("gold")) {
			return GOLD;
		}
		return path.contains("leather") ? LEATHER : null;
	}
}
