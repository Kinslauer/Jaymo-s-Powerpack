/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/SliderModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.dungeon;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class SliderModel extends DungeonModel {
	public final ModelPart slider;

	private SliderModel(ModelPart root) {
		super(root);
		this.slider = root.getChild("slider");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();

		partDefinition.addOrReplaceChild("slider", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-16.0F, -32.0F, -16.0F, 32.0F, 32.0F, 32.0F, new CubeDeformation(0.0F))
				.texOffs(0, 64).addBox(-16.0F, -32.0F, -16.0F, 32.0F, 32.0F, 32.0F, new CubeDeformation(-0.8F)),
				PartPose.offset(0.0F, 24.0F, 0.0F));

		return LayerDefinition.create(meshDefinition, 128, 128);
	}

	static SliderModel create() {
		return new SliderModel(createBodyLayer().bakeRoot());
	}

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		// asleep it settles; awake it jitters a hair with the effort
		this.slider.y = 24.0F + Mth.sin(state.ageInTicks * (state.awake ? 0.9F : 0.1F)) * (state.awake ? 0.35F : 0.6F);
	}
}
