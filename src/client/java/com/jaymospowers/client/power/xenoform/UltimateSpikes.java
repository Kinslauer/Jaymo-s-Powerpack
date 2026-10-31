package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.93, patch 2) An Ultimate's spikes, horns and crests, worn over its armoured skin ({@link XenoformLayer}). A
 * humanoid model with no body of its own, only the spikes hung on its head, body and limbs, so it takes the same pose
 * as the alien (the same animation, and every pose mixin that touches humanoid models). Its parts start where the
 * alien's do ({@link ModelPart#setInitialPose}). One plain strip of bone ({@code xenoform_ultimate_spikes.png}) tinted
 * per alien. Each alien has its own set ({@link Style}).
 */
final class UltimateSpikes extends HumanoidModel<AvatarRenderState> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/xenoform_ultimate_spikes.png");

	/**
	 * Which spikes an Ultimate wears: a crown on the head (count, length), a ridge down the back (count, length),
	 * spikes up off each shoulder, blades along each forearm (0 none, 1 one), and how far out from the bones they sit
	 * (a bulky alien's skin is puffed out).
	 */
	record Style(int crown, float crownLength, int ridge, float ridgeLength, int shoulder, int blade, float bulk) {}

	private static final Map<String, Style> STYLES = Map.ofEntries(
		Map.entry("bulwark", new Style(0, 0.0F, 3, 5.0F, 2, 0, 1.0F)),
		Map.entry("pyre", new Style(5, 5.0F, 2, 3.0F, 1, 0, 0.0F)),
		Map.entry("blur", new Style(2, 4.0F, 0, 0.0F, 0, 1, 0.0F)),
		Map.entry("prism", new Style(3, 4.0F, 3, 4.0F, 2, 1, 0.3F)),
		Map.entry("wisp", new Style(3, 3.0F, 0, 0.0F, 1, 0, 0.0F)),
		Map.entry("gremlin", new Style(2, 3.0F, 3, 2.5F, 0, 1, 0.0F)),
		Map.entry("knell", new Style(0, 0.0F, 4, 4.5F, 2, 0, 0.6F)),
		Map.entry("wyrm", new Style(2, 6.0F, 4, 3.5F, 1, 0, 0.3F)),
		Map.entry("duplex", new Style(1, 4.0F, 0, 0.0F, 2, 0, 0.0F)),
		Map.entry("rivet", new Style(1, 3.0F, 3, 2.5F, 2, 1, 0.0F)),
		Map.entry("gorger", new Style(0, 0.0F, 5, 3.0F, 2, 0, 0.6F)),
		Map.entry("slipfin", new Style(4, 3.5F, 3, 3.0F, 0, 1, 0.0F)),
		Map.entry("polestar", new Style(2, 5.0F, 2, 4.0F, 2, 0, 0.7F)),
		Map.entry("lurk", new Style(3, 6.0F, 4, 3.0F, 1, 1, 0.0F)),
		Map.entry("gyre", new Style(0, 0.0F, 5, 3.5F, 2, 0, 0.8F)),
		Map.entry("cortex", new Style(4, 3.0F, 2, 2.5F, 2, 1, 0.3F)),
		Map.entry("locust", new Style(2, 5.5F, 3, 2.5F, 0, 1, 0.0F)),
		Map.entry("croaker", new Style(2, 2.5F, 4, 2.5F, 1, 0, 0.4F)),
		Map.entry("rime", new Style(5, 5.0F, 4, 4.0F, 2, 1, 0.5F)),
		Map.entry("marrow", new Style(2, 4.5F, 5, 3.0F, 2, 1, 0.0F)),
		Map.entry("swathe", new Style(0, 0.0F, 3, 2.5F, 1, 1, 0.2F)),
		Map.entry("blight", new Style(3, 4.0F, 5, 4.5F, 2, 0, 0.4F)),
		Map.entry("howl", new Style(2, 3.5F, 5, 3.0F, 1, 1, 0.1F)),
		Map.entry("prowl", new Style(0, 0.0F, 4, 2.5F, 0, 1, 0.0F)),
		Map.entry("tusk", new Style(1, 3.0F, 5, 4.0F, 2, 0, 0.6F)),
		Map.entry("talon", new Style(3, 4.5F, 2, 3.0F, 1, 1, 0.0F)),
		Map.entry("murk", new Style(4, 3.0F, 0, 0.0F, 1, 0, 0.2F)),
		Map.entry("gnasher", new Style(0, 0.0F, 5, 4.0F, 1, 1, 0.5F)),
		Map.entry("lumen", new Style(5, 2.5F, 0, 0.0F, 0, 0, 0.0F)),
		Map.entry("keen", new Style(2, 4.0F, 3, 2.0F, 1, 1, 0.0F)),
		Map.entry("hive", new Style(2, 3.0F, 3, 2.5F, 1, 1, 0.1F)),
		Map.entry("skitter", new Style(0, 0.0F, 5, 3.0F, 2, 1, 0.3F)),
		Map.entry("shade", new Style(3, 5.0F, 0, 0.0F, 1, 1, 0.0F)),
		Map.entry("mirage", new Style(5, 3.0F, 0, 0.0F, 1, 0, 0.0F)));

	private static final Map<String, UltimateSpikes> MODELS = new HashMap<>();

	private UltimateSpikes(ModelPart root) {
		super(root);
	}

	/** {@code form}'s spikes, their parts started where {@code base}'s are; null for an alien with no Ultimate body. */
	static @Nullable UltimateSpikes of(String form, XenoFormModels.FormModel base) {
		Style style = STYLES.get(form);
		if (style == null) {
			return null;
		}
		return MODELS.computeIfAbsent(form, id -> {
			UltimateSpikes model = new UltimateSpikes(LayerDefinition.create(mesh(style), 64, 32).bakeRoot());
			model.head.setInitialPose(base.head.getInitialPose());
			model.hat.setInitialPose(base.hat.getInitialPose());
			model.body.setInitialPose(base.body.getInitialPose());
			model.rightArm.setInitialPose(base.rightArm.getInitialPose());
			model.leftArm.setInitialPose(base.leftArm.getInitialPose());
			model.rightLeg.setInitialPose(base.rightLeg.getInitialPose());
			model.leftLeg.setInitialPose(base.leftLeg.getInitialPose());
			model.resetPose();
			return model;
		});
	}

	private static MeshDefinition mesh(Style s) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		float b = s.bulk();
		// the crown: a fan of spikes over the brow, the middle ones tallest, leaning back
		for (int i = 0; i < s.crown(); i++) {
			float t = s.crown() == 1 ? 0.0F : i / (float)(s.crown() - 1) * 2.0F - 1.0F;
			float length = s.crownLength() * (1.0F - 0.35F * Math.abs(t));
			spike(head, "crown" + i, 1.0F, length, PartPose.offsetAndRotation(t * 3.0F, -8.0F - b, -1.0F, -0.45F, 0.0F, -t * 0.5F));
		}
		// the ridge: down the spine, leaning back, smaller toward the waist
		for (int i = 0; i < s.ridge(); i++) {
			float length = s.ridgeLength() * (1.0F - 0.2F * i);
			spike(body, "ridge" + i, 1.5F, length, PartPose.offsetAndRotation(0.0F, 1.5F + i * 3.0F, 2.0F + b, -1.2F + i * 0.1F, 0.0F, 0.0F));
		}
		// shoulders: spikes off the top of each upper arm, splayed out
		for (int i = 0; i < s.shoulder(); i++) {
			float lean = 0.35F + i * 0.4F;
			spike(rightArm, "shoulder" + i, 1.0F, 3.5F - i * 0.8F, PartPose.offsetAndRotation(-1.5F - i * 1.2F, -2.0F - b, 0.0F, 0.0F, 0.0F, -lean));
			spike(leftArm, "shoulder" + i, 1.0F, 3.5F - i * 0.8F, PartPose.offsetAndRotation(1.5F + i * 1.2F, -2.0F - b, 0.0F, 0.0F, 0.0F, lean));
		}
		// blades: one fin down the outside of each forearm, sweeping back
		if (s.blade() > 0) {
			rightArm.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 8).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 7.0F, 4.0F),
				PartPose.offsetAndRotation(-3.0F - b, 3.0F, 0.0F, 0.25F, 0.0F, 0.0F));
			leftArm.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 8).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 7.0F, 4.0F),
				PartPose.offsetAndRotation(3.0F + b, 3.0F, 0.0F, 0.25F, 0.0F, 0.0F));
		}
		return mesh;
	}

	/** One spike: a square base {@code width} wide, standing {@code length} up from its pose (model y is down). */
	private static void spike(PartDefinition on, String name, float width, float length, PartPose pose) {
		on.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0).addBox(-width / 2.0F, -length, -width / 2.0F, width, length, width), pose);
	}
}
