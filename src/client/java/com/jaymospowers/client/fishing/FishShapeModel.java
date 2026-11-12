package com.jaymospowers.client.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Track C: one model class for every new species, built from its generated shape ({@link FishShapes}). Each part has a
 * role in how it moves: a fish's stalk and tail beat (harder out of water), its side fins row, an eel's body runs in
 * waves, a flatfish ripples up and down, legs walk, claws and feelers twitch, a shrimp's tail curls, a jellyfish's
 * tentacles trail as its bell pulses.
 */
public final class FishShapeModel extends EntityModel<LivingEntityRenderState> {
	/** Roles, as tools/gen_fishing_species.py numbers them. */
	static final int STATIC = 0, STALK = 1, TAIL = 2, FIN_L = 3, FIN_R = 4, SEG = 5, LEG = 6, CLAW = 7, TENTACLE = 8, BELL = 9,
		FLAT_STALK = 10, FLAT_TAIL = 11, CURL = 12;
	static final float TAIL_SWING = 0.35F, TAIL_SPEED = 0.6F, OUT_OF_WATER = 1.7F;

	private record Moving(ModelPart part, int role, int index) {
	}

	private final List<Moving> moving = new ArrayList<>();

	private FishShapeModel(ModelPart root, FishShapes.Shape shape, boolean translucent) {
		super(root, translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout);
		Map<String, ModelPart> parts = new HashMap<>();
		for (FishShapes.P p : shape.parts()) {
			ModelPart parent = p.parent() == null ? root : parts.get(p.parent());
			if (parent == null || !parent.hasChild(p.name())) {
				continue;
			}
			ModelPart part = parent.getChild(p.name());
			parts.put(p.name(), part);
			if (p.role() != STATIC) {
				moving.add(new Moving(part, p.role(), p.index()));
			}
		}
	}

	static FishShapeModel create(String species, boolean translucent) {
		FishShapes.Shape shape = FishShapes.ALL.get(species);
		return new FishShapeModel(layer(shape).bakeRoot(), shape, translucent);
	}

	static LayerDefinition layer(FishShapes.Shape shape) {
		MeshDefinition mesh = new MeshDefinition();
		Map<String, PartDefinition> defs = new HashMap<>();
		for (FishShapes.P p : shape.parts()) {
			PartDefinition parent = p.parent() == null ? mesh.getRoot() : defs.get(p.parent());
			if (parent == null) {
				parent = mesh.getRoot();
			}
			PartDefinition def = parent.addOrReplaceChild(p.name(), CubeListBuilder.create().texOffs(p.u(), p.v())
				.addBox(p.x(), p.y(), p.z(), p.w(), p.h(), p.d()), PartPose.offsetAndRotation(p.px(), p.py(), p.pz(), p.rx(), p.ry(), p.rz()));
			defs.put(p.name(), def);
		}
		return LayerDefinition.create(mesh, shape.texW(), shape.texH());
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float amp = state.isInWater ? 1.0F : OUT_OF_WATER;
		float walk = state.walkAnimationPos, walkSpeed = Math.min(1.0F, state.walkAnimationSpeed * 4.0F);
		for (Moving m : moving) {
			ModelPart p = m.part;
			switch (m.role) {
				case STALK -> p.yRot = -amp * TAIL_SWING * Mth.sin(TAIL_SPEED * age);
				case TAIL -> p.yRot = -amp * TAIL_SWING * 0.6F * Mth.sin(TAIL_SPEED * age - 0.6F);
				case FIN_L -> p.zRot += 0.2F * Mth.sin(0.25F * age);
				case FIN_R -> p.zRot -= 0.2F * Mth.sin(0.25F * age);
				case SEG -> p.yRot = amp * 0.3F * Mth.sin(0.35F * age - m.index * 0.9F);
				case FLAT_STALK -> p.xRot = amp * 0.25F * Mth.sin(0.3F * age);
				case FLAT_TAIL -> p.xRot = amp * 0.2F * Mth.sin(0.3F * age - 0.6F);
				case LEG -> p.xRot += walkSpeed * 0.5F * Mth.sin(walk * 1.5F + m.index * 1.3F) + 0.05F * Mth.sin(0.1F * age + m.index);
				case CLAW -> p.yRot += 0.12F * Mth.sin(0.08F * age + m.index * 2.0F);
				case TENTACLE -> p.xRot += 0.18F * Mth.sin(0.12F * age + m.index * 0.8F);
				case CURL -> p.xRot += 0.08F * Mth.sin(0.15F * age + m.index * 0.7F);
				case BELL -> p.y += 0.6F * Mth.sin(0.16F * age);
				default -> {
				}
			}
		}
	}
}
