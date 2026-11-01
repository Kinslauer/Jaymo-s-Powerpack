package com.jaymoskingdoms.client;

import com.jaymoskingdoms.McaCompat;
import net.minecraft.client.model.Model;

/**
 * (0.7.451) What's done to a model right after it's posed for drawing (the mixin on {@code ModelFeatureRenderer.prepareModel}),
 * in one place, so a model whose bones are read before it's drawn can be posed the same way first ({@link BioForms#submit}).
 */
public final class ModelPoses {
	private ModelPoses() {
	}

	/** After {@code model.setupAnim(state)}: the pulls, swings and reshapes over it, in order. */
	public static void after(Model<?> model, Object state) {
		PullerPose.apply(model, state);
		PickpocketPose.apply(model, state);
		BioAbsorbPose.apply(model, state);
		RubberEngulf.apply(model, state);
		// (0.7.457) Andy: "still having a problem with the way the character is drawn on the start path screen". A Bio-built's
		// hunch is added to the bones, and MCA's skin and clothes bodies take the pose it captured after the hunch was
		// already on: added again, the head sank into the chest and the arms pushed out
		if (!McaCompat.LOADED || !McaLookModels.posedByMca(model, state)) {
			BioForms.apply(model, state);
		}
		HandshakePose.apply(model, state);
		MobAnimations.apply(model, state);
		HiddenBladeClient.pose(model, state);
		GoblinBody.apply(model, state);
	}

	/** {@code model} posed now from {@code state}, exactly as it will be when drawn. */
	@SuppressWarnings("unchecked")
	public static void now(Model<?> model, Object state) {
		((Model<Object>)model).setupAnim(state);
		after(model, state);
	}
}
