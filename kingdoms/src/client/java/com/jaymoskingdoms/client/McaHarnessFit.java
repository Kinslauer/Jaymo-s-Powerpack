package com.jaymoskingdoms.client;

import net.conczin.mca.Config;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The harness ({@link HeraldryLayer}) fitted to a body MCA draws: every player, MCA's villagers and our folk. Andy
 * (2026-11-19), a fat, stout player in spaulders, arm harness, cuisses and a hood: "the harness armor doesnt fit different
 * body types correctly ... it looked very....clippy".
 * <p>
 * MCA stretches the whole body to the wearer's width and height before anything is drawn, the harness with it, so those
 * already fit. What it does to a body beyond that, the harness missed, where MCA's own armour model has it:
 * <ul>
 * <li><b>The head's size</b>: MCA draws every head scaled by the wearer's head gene ({@link McaHeadSize}). A helm, a hood
 * or a crest stayed at the plain size, perched on top of a big head with the face showing under it.</li>
 * <li><b>Breasts</b>: MCA hangs them off the chest, out past a cuirass, a coat or a tabard. Whatever the harness hangs on
 * the body is drawn deeper from the back by as much as they stand out ({@link #bosom}).</li>
 * <li><b>MCA's clothes</b>: their sleeve and trouser layer stands 0.3125 off the limb and the arming doublet and mail under
 * the harness 0.4 ({@link McaUnderArmour}), where the arm harness and some leg plates sat at 0.3 to 0.4: the cloth showed
 * through the plate in patches. The pieces on the arms and legs are drawn {@link #LIMB} wider and deeper, about the
 * limb's own middle.</li>
 * </ul>
 * Only loaded with MCA; bodies MCA doesn't draw are left as they were.
 */
final class McaHarnessFit {
	/** How much wider and deeper the pieces on an MCA body's arms and legs are drawn (clears its clothes and the suit under them). */
	static final float LIMB = 1.07F;
	/** The middle of each limb across, from its pivot (pixels; the model's x): right arm, left arm, legs. */
	static final float RIGHT_ARM = -1.0F, LEFT_ARM = 1.0F, LEG = 0.0F;
	/** The back of the body (pixels; the model's z, front at -z), which the body's pieces are deepened from. */
	static final float BACK = 2.0F;
	/** How deep the harness's body pieces stand from the back at the chest (the cuirass's breast, 2.7 before the chest). */
	static final float CHEST_DEPTH = 4.7F;
	/** Room left in front of MCA's breasts, per unit of their own scale (their clothes, the suit). */
	static final float BOSOM_ROOM = 0.5F;
	/** The most the body's pieces are deepened. */
	static final float BOSOM_MAX = 1.6F;

	private McaHarnessFit() {
	}

	/** Fits {@code piece} (one harness model, posed as the body) to the body MCA is drawing for {@code state}, if it is. */
	static void fit(HumanoidModel<?> piece, HumanoidRenderState state) {
		if (state.isBaby || !(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
			return;
		}
		VillagerVisuals visuals = holder.mca$getVisuals();
		if (visuals == null) {
			return;
		}
		McaHeadSize.apply(piece, state);
		widen(piece.rightArm, LIMB, RIGHT_ARM);
		widen(piece.leftArm, LIMB, LEFT_ARM);
		widen(piece.rightLeg, LIMB, LEG);
		widen(piece.leftLeg, LIMB, LEG);
		float bosom = bosom(visuals);
		if (bosom > 1.0F) {
			scaleAbout(piece.body, 1.0F, bosom, 0.0F, BACK);
		}
	}

	/**
	 * How much deeper the body's pieces must be drawn to stand over MCA's breasts (1 for none). MCA draws them
	 * ({@code CommonVillagerModel.renderCommon}, {@code applyVillagerDimensions}) as a 6x3x3 box on the chest turned 54
	 * degrees, at z {@code -1.5 + s/4}, the lot scaled by {@code 0.75 + 0.75s} front to back, where {@code s} is the breast
	 * gene times the age's breast size: its front stands {@code (0.75 + 0.75s)(3.39 - s/4)} before the body's middle.
	 */
	static float bosom(VillagerVisuals visuals) {
		if (!visuals.female() || !Config.getInstance().enableBoobs) {
			return 1.0F;
		}
		float s = visuals.breastSize() * visuals.dimensions().breasts();
		if (s <= 0.0F) {
			return 1.0F;
		}
		float scale = 0.75F + 0.75F * s;
		float front = scale * (3.39F - 0.25F * s + BOSOM_ROOM);
		return Mth.clamp((BACK + front) / CHEST_DEPTH, 1.0F, BOSOM_MAX);
	}

	/** {@code limb} drawn {@code k} times wider and deeper about its middle across ({@code middle} on the model's x). */
	private static void widen(ModelPart limb, float k, float middle) {
		scaleAbout(limb, k, k, middle, 0.0F);
	}

	/**
	 * Scales {@code part} by {@code kx} across and {@code kz} front to back, keeping the point ({@code px}, any, {@code pz})
	 * of its own frame where it was. A part is drawn as move, turn, scale: so the move takes up what the new scale shifts,
	 * turned as the part is turned.
	 */
	static void scaleAbout(ModelPart part, float kx, float kz, float px, float pz) {
		float sx = part.xScale, sz = part.zScale;
		Vector3f shift = new Vector3f(sx * px * (1.0F - kx), 0.0F, sz * pz * (1.0F - kz));
		if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
			new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(shift);
		}
		part.x += shift.x;
		part.y += shift.y;
		part.z += shift.z;
		part.xScale = sx * kx;
		part.zScale = sz * kz;
	}
}
