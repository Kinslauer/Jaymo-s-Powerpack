package com.jaymoskingdoms.client;

import com.jaymoskingdoms.McaCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Armour drawn by the Armor Model API (Rogues &amp; Warriors' Warrior Helmet and the rest of the RPG series' 3D sets). Its
 * model takes its pose by setting the body's model up again from the render state and copying the parts, so it missed
 * two things the body has by the time it is drawn. Andy (2026-11-13): "the helm was clipping through player heads, it
 * didnt move with the mca model".
 * <ul>
 * <li>Fresh Animations: Entity Model Features animates the body as it is drawn, and lays the same pose on vanilla armour
 * but not on these pieces, so the helmet stayed in the plain pose while the head under it bobbed and turned through it.
 * They take the pose kept for our own worn pieces ({@link WornModel#loadKeptBody}).</li>
 * <li>MCA's head size: MCA draws every head on its bodies (players and villagers) scaled by the wearer's head size
 * ({@code CommonVillagerModel.renderCommon}), and its own armour with it, but these pieces kept the plain size
 * ({@link McaHeadSize}).</li>
 * </ul>
 * A baby is left as the API fits it (it sizes each piece to the baby's own parts).
 */
public final class GeoArmourFit {
	private GeoArmourFit() {
	}

	/** After the API's model has copied the body: called from {@code GeoArmourPoseMixin}. */
	public static void fit(HumanoidModel<?> armour, HumanoidRenderState state) {
		if (state.isBaby) {
			return;
		}
		WornModel.loadKeptBody(state, armour);
		if (McaCompat.LOADED) {
			McaHeadSize.apply(armour, state);
		}
	}
}
