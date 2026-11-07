package com.jaymospowers.client.compat;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.geom.PartPose;

/**
 * Fresh Animations (run by Entity Model Features) re-animates animal models after Critter Armory captured their pose
 * for its armour layers, so Critter Armory's pig, sheep and horse armour is drawn in the vanilla pose and slides off
 * the animated model. This compat keeps Fresh Animations' pose on the render state just before the render layers, so
 * Critter Armory's armour models take it - the same shape as Kingdoms' WornModel and ArmorPoseLivingEntityRendererMixin
 * keep Fresh Animations' pose on our own armour. Only active when both Entity Model Features and Critter Armory are
 * loaded; Critter Armory is linked, never edited.
 */
public final class CritterArmoryFreshAnimationsCompat {
	public static final boolean EMF = FabricLoader.getInstance().isModLoaded("entity_model_features");
	public static final boolean CRITTER_ARMORY = FabricLoader.getInstance().isModLoaded("critterarmory");

	/** The body's pose as Fresh Animations animated it, kept on the render state for Critter Armory's armour models. */
	public static final RenderStateDataKey<PartPose[]> CRITTER_ARMOR_EMF_POSES =
			RenderStateDataKey.create(() -> "jaymospowers:critter_armor_emf_poses");

	private CritterArmoryFreshAnimationsCompat() {
	}

	/** Whether both Fresh Animations' engine and Critter Armory are loaded, so the compat has something to do. */
	public static boolean active() {
		return EMF && CRITTER_ARMORY;
	}
}
