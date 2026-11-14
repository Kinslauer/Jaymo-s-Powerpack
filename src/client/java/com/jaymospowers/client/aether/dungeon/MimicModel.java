/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/MimicModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.dungeon;


import com.jaymospowers.client.aether.anim.MimicAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class MimicModel extends DungeonModel {
	public final ModelPart body;
	public final ModelPart head;
	public final ModelPart eye;
	private final ModelPart tongue_1;
	private final ModelPart tongue_2;
	private final ModelPart tongue_3;
	private final ModelPart leg_left;
	private final ModelPart leg_right;
	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation attackAnimation;
	private final KeyframeAnimation spawnAnimation;

	private MimicModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.eye = this.head.getChild("eye");
		this.tongue_1 = this.head.getChild("tongue_1");
		this.tongue_2 = this.tongue_1.getChild("tongue_2");
		this.tongue_3 = this.tongue_2.getChild("tongue_3");
		this.leg_left = this.body.getChild("leg_left");
		this.leg_right = this.body.getChild("leg_right");
		this.walkAnimation = MimicAnimations.walk.bake(root);
		this.attackAnimation = MimicAnimations.attack.bake(root);
		this.spawnAnimation = MimicAnimations.spawn.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.5F, 9.0F, 1.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r1 = head.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(84, 0).addBox(0.0F, -0.4F, -2.5F, 2.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(72, 0).mirror().addBox(-16.0F, -0.4F, -2.5F, 2.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(6.5F, -1.5F, -1.75F, 0.7854F, 0.0F, 0.0F));

		PartDefinition cube_r2 = head.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 28).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(-0.2F))
				.texOffs(0, 0).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 0.0F, -1.0F, -2.3562F, 0.0F, 0.0F));

		PartDefinition tooth_left_5_r1 = head.addOrReplaceChild("tooth_left_5_r1", CubeListBuilder.create().texOffs(122, 43).addBox(5.0F, -11.0F, -10.0F, 0.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(120, 41).addBox(3.0F, -11.0F, -5.0F, 4.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(122, 38).addBox(4.0F, -10.0F, -2.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(122, 35).addBox(4.0F, -10.0F, 1.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(120, 30).addBox(3.0F, -12.0F, 4.0F, 4.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(122, 15).addBox(-5.0F, -11.0F, -10.0F, 0.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(120, 13).addBox(-8.0F, -11.0F, -6.0F, 4.0F, 5.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(122, 10).addBox(-7.0F, -10.0F, 1.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(120, 5).addBox(-7.0F, -12.0F, 4.0F, 4.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 0.0F, -1.0F, 0.7854F, 0.0F, 0.0F));

		PartDefinition eye = head.addOrReplaceChild("eye", CubeListBuilder.create().texOffs(42, 0).addBox(-5.0F, -5.0F, 0.0F, 9.0F, 9.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition tongue_1 = head.addOrReplaceChild("tongue_1", CubeListBuilder.create().texOffs(46, 9).addBox(-4.0F, 0.0F, -10.0F, 8.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 5.0F, 1.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition tongue_2 = tongue_1.addOrReplaceChild("tongue_2", CubeListBuilder.create().texOffs(47, 19).addBox(-4.0F, 0.0F, -9.0F, 8.0F, 0.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -10.0F, 0.9599F, 0.0F, 0.0F));

		PartDefinition tongue_3 = tongue_2.addOrReplaceChild("tongue_3", CubeListBuilder.create().texOffs(50, 28).addBox(-4.0F, 0.0F, -6.0F, 8.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -9.0F, 1.0472F, 0.0F, 0.0F));

		PartDefinition leg_left = body.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(92, 35).addBox(0.0F, 0.5F, -1.0F, 5.0F, 14.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(88, 20).addBox(0.5F, 3.4F, -0.5F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, 0.5F, -0.5F));

		PartDefinition cube_r3 = leg_left.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(84, 10).addBox(0.0F, 1.6F, -2.0F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -2.0F, -1.25F, 0.7777F, 0.1231F, -0.124F));

		PartDefinition cube_r4 = leg_left.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(96, 10).addBox(-3.5F, -5.1F, 0.0F, 6.0F, 10.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5F, 1.5F, 4.0F, 0.0F, -0.4363F, 0.0F));

		PartDefinition leg_right = body.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(72, 35).addBox(-5.0F, 0.5F, -1.0F, 5.0F, 14.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(72, 20).addBox(-4.5F, 3.4F, -0.5F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-9.5F, 0.5F, -0.5F));

		PartDefinition cube_r5 = leg_right.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(72, 10).mirror().addBox(-3.0F, 1.6F, -2.0F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(1.0F, -2.0F, -1.25F, 0.7777F, -0.1231F, 0.124F));

		PartDefinition cube_r6 = leg_right.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(96, 0).mirror().addBox(-2.5F, -5.1F, 0.0F, 6.0F, 10.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-5.5F, 1.5F, 4.0F, 0.0F, 0.4363F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	static MimicModel create() {
		return new MimicModel(createBodyLayer().bakeRoot());
	}


	/** The lengths (ms) of the leap out of the chest and of the bite, both scrubbed. */
	private static final float SPAWN_MS = 708.0F, BITE_MS = 1000.0F;

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.0F, 1.0F);
		if (state.progress < 1.0F) {
			this.spawnAnimation.apply((long)(state.progress * SPAWN_MS), 1.0F);
		}
		if (state.attack > 0.0F) {
			this.attackAnimation.apply((long)(state.attack * BITE_MS), 1.0F);
		}
	}
}
