package com.jaymospowers.client.aether.dungeon;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The Slider: one carved block, two blocks to a side, that hovers a little as it waits. It never turns: the face stays where it was set. */
final class SliderModel extends DungeonModel {
	private final ModelPart body;

	private SliderModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
	}

	static SliderModel create() {
		return new SliderModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -16.0F, -32.0F, -16.0F, 32, 32, 32, 0.0F, 24.0F, 0.0F);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		// asleep it settles; awake it jitters a hair with the effort
		this.body.y = 24.0F + Mth.sin(state.ageInTicks * (state.awake ? 0.9F : 0.1F)) * (state.awake ? 0.35F : 0.6F);
	}
}
