/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/burrukai/AbstractBurrukaiBabyModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, renamed BurrukaiBabyBase, posed from our own render state (a calf never charges, so its ram animation is not played).
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;

import com.jaymospowers.client.aether.anim.BurrukaiAnimation;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;

abstract class BurrukaiBabyBase extends AetherAnimalModel {
	private final KeyframeAnimation ramAnimation;
	private final KeyframeAnimation walkAnimation;
	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart head;
	private final ModelPart ears;
	private final ModelPart leg_front_left;
	private final ModelPart leg_front_right;
	private final ModelPart leg_rear_left;
	private final ModelPart leg_rear_right;

	BurrukaiBabyBase(ModelPart root) {
		super(root);
		this.ramAnimation = BurrukaiAnimation.BABY_RAM.bake(root);
		this.walkAnimation = BurrukaiAnimation.BABY_WALK.bake(root);
		this.body = root.getChild("body");
		this.tail = this.body.getChild("tail");
		this.head = this.body.getChild("head");
		this.ears = this.head.getChild("ears");
		this.leg_front_left = this.body.getChild("leg_front_left");
		this.leg_front_right = this.body.getChild("leg_front_right");
		this.leg_rear_left = this.body.getChild("leg_rear_left");
		this.leg_rear_right = this.body.getChild("leg_rear_right");
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
		this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
		this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.0F);
	}
}
