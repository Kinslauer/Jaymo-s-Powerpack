package com.jaymospowers.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * What a piece worn over a humanoid's body (a bauble's look, an Artifacts trinket, the hooded cloak, Kitchen's quiver)
 * takes from the body as it is drawn. Each of those is a HumanoidModel that sets itself up again from the render state
 * when it is drawn, so it was always in the plain pose. Andy (2026-11-13), on the Warrior Helmet: "the helm was clipping
 * through player heads, it didnt move with the mca model", and asked what else did the same.
 * <ul>
 * <li>Fresh Animations: Entity Model Features animates the body just before the render layers and lays the pose on
 * vanilla armour only; these pieces stayed in the vanilla walk while the body bobbed and swayed under them. The pose is
 * kept just before the layers ({@link #keep}, only when EMF really animated it, as {@code CostumeBody} does) and laid on
 * the piece ({@link #fit}).</li>
 * <li>MCA's head size: MCA draws a head on its bodies scaled by the wearer's head size (children and teens differ from
 * 1); the piece's head takes the same scale from the model's origin, as the costume's head pieces do.</li>
 * </ul>
 * Our own poses are laid over these pieces as they are drawn, as before.
 */
public final class WornPose {
	/** The body's pose (root, head, body, arms, legs) as EMF animated it. */
	private static final RenderStateDataKey<PartPose[]> BODY = RenderStateDataKey.create(() -> "jaymospowers:worn_body_pose");
	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");

	private WornPose() {
	}

	/** Just before the render layers (LivingEntityRenderer.submit): keeps the body's pose if EMF has just animated it. */
	public static void keep(EntityRenderState state, Model<?> body) {
		if (!(body instanceof HumanoidModel<?> humanoid) || !EmfCompat.bodyAnimated(state, body)) {
			return;
		}
		ModelPart[] parts = parts(humanoid);
		PartPose[] pose = new PartPose[parts.length];
		for (int i = 0; i < parts.length; i++) {
			pose[i] = parts[i].storePose();
		}
		state.setData(BODY, pose);
	}

	/** After {@code worn} has set itself up for {@code state}: the body's kept pose, and MCA's head size. */
	public static void fit(HumanoidModel<?> worn, EntityRenderState state) {
		PartPose[] body = state.getData(BODY);
		if (body != null) {
			ModelPart[] to = parts(worn);
			for (int i = 0; i < to.length; i++) {
				to[i].loadPose(body[i]);
			}
		}
		if (MCA) {
			float s = McaHeadScale.of(state);
			if (s > 0.0F && s != 1.0F) {
				ModelPart head = worn.head;
				head.x *= s;
				head.y *= s;
				head.z *= s;
				head.xScale *= s;
				head.yScale *= s;
				head.zScale *= s;
			}
		}
	}

	private static ModelPart[] parts(HumanoidModel<?> model) {
		return new ModelPart[] {model.root(), model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
	}
}
