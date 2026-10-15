package com.jaymoskingdoms.client;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * A model worn over a humanoid's body by one of our layers (Armory's raised pieces, a villager wizard's hat). It is a
 * HumanoidModel on purpose: every pose laid over a humanoid model as it is drawn (Powers' crawl, flight, combos and
 * meditation, our mobs' Better Combat swings, pullers, pickpockets) lands on it exactly as on the vanilla armour, so the
 * pieces stay on the limbs they belong to. Before those, it copies the wearer's own animation (zombie arms, a mod's player
 * animation) and the body's animated pose.
 * <p>
 * Entity Model Features (Fresh Animations) animates the player's body when it is drawn, and lays the same pose on the
 * vanilla armour (player armour models are player models), but not on our pieces: they stayed in the vanilla walk while
 * the arms and legs under them swung, bobbed and swayed Fresh Animations' way, and poked through the pauldrons and
 * skirts. So the body's pose is kept on the render state just before the layers, when EMF has animated it
 * ({@link EmfWornPose}), and our pieces take it.
 */
public class WornModel extends HumanoidModel<HumanoidRenderState> {
	private static final boolean EMF = FabricLoader.getInstance().isModLoaded("entity_model_features");
	/** The body's pose as EMF animated it, kept for the models drawn over it. */
	private static final RenderStateDataKey<PartPose[]> BODY = RenderStateDataKey.create(() -> "jaymoskingdoms:worn_body_pose");

	private final Supplier<HumanoidModel<?>> wearer;

	public WornModel(ModelPart root, Supplier<HumanoidModel<?>> wearer) {
		super(root);
		this.wearer = wearer;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void setupAnim(HumanoidRenderState state) {
		resetPose();
		HumanoidModel parent = this.wearer.get();
		parent.setupAnim(state);
		ModelPart[] from = parts(parent);
		ModelPart[] to = parts(this);
		for (int i = 0; i < to.length; i++) {
			to[i].loadPose(from[i].storePose());
			to[i].xScale = from[i].xScale;
			to[i].yScale = from[i].yScale;
			to[i].zScale = from[i].zScale;
			to[i].visible = from[i].visible;
		}
		PartPose[] body = state.getData(BODY);
		if (body != null) {
			for (int i = 0; i < to.length; i++) {
				to[i].loadPose(body[i]);
			}
		}
	}

	/** Just before the layers: keeps the body's pose if EMF has just animated it. */
	public static void keepBody(EntityRenderState state, Model<?> body) {
		if (EMF && body instanceof HumanoidModel<?> humanoid && EmfWornPose.animatedNow(state, humanoid)) {
			ModelPart[] parts = parts(humanoid);
			PartPose[] pose = new PartPose[parts.length];
			for (int i = 0; i < parts.length; i++) {
				pose[i] = parts[i].storePose();
			}
			state.setData(BODY, pose);
		}
	}

	private static ModelPart[] parts(HumanoidModel<?> model) {
		return new ModelPart[] {model.root(), model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
	}
}
