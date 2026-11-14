/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/burrukai/AbstractBurrukaiModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, made the shared base of our zone models, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;

import com.jaymospowers.client.aether.anim.BurrukaiAnimation;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;

abstract class BurrukaiModel extends AetherAnimalModel {
	private final KeyframeAnimation ramAnimation;
	private final KeyframeAnimation walkAnimation;
	public final ModelPart body_main;
	public final ModelPart body_front;
	public final ModelPart body_rear;
	public final ModelPart tail;
	public final ModelPart body_plate_rear;
	public final ModelPart body_plate_front;
	public final ModelPart body_neck;
	public final ModelPart leg_front_left;
	public final ModelPart leg_front_left_lower;
	public final ModelPart leg_front_left_plate;
	public final ModelPart leg_front_right;
	public final ModelPart leg_front_right_lower;
	public final ModelPart leg_front_right_plate;
	public final ModelPart leg_rear_right;
	public final ModelPart leg_rear_right_lower;
	public final ModelPart leg_rear_left;
	public final ModelPart leg_rear_left_lower;
	public final ModelPart head_main;
	public final ModelPart head_front;
	public final ModelPart head_plate;
	public final ModelPart antler_right;
	public final ModelPart antler_left;
	public final ModelPart ear_left;
	public final ModelPart ear_right;
	public final ModelPart head_rear;
	public final ModelPart mouth;

	BurrukaiModel(ModelPart root) {
		super(root);
		this.ramAnimation = BurrukaiAnimation.RAM.bake(root);
		this.walkAnimation = BurrukaiAnimation.WALK.bake(root);
		this.body_main = root.getChild("body_main");
		this.body_front = this.body_main.getChild("body_front");
		this.body_rear = this.body_main.getChild("body_rear");
		this.tail = this.body_main.getChild("tail");
		this.body_plate_rear = this.body_main.getChild("body_plate_rear");
		this.body_plate_front = this.body_main.getChild("body_plate_front");
		this.body_neck = this.body_main.getChild("body_neck");
		this.leg_front_left = this.body_main.getChild("leg_front_left");
		this.leg_front_left_lower = this.leg_front_left.getChild("leg_front_left_lower");
		this.leg_front_left_plate = this.leg_front_left.getChild("leg_front_left_plate");
		this.leg_front_right = this.body_main.getChild("leg_front_right");
		this.leg_front_right_lower = this.leg_front_right.getChild("leg_front_right_lower");
		this.leg_front_right_plate = this.leg_front_right.getChild("leg_front_right_plate");
		this.leg_rear_right = this.body_main.getChild("leg_rear_right");
		this.leg_rear_right_lower = this.leg_rear_right.getChild("leg_rear_right_lower");
		this.leg_rear_left = this.body_main.getChild("leg_rear_left");
		this.leg_rear_left_lower = this.leg_rear_left.getChild("leg_rear_left_lower");
		this.head_main = this.body_main.getChild("head_main");
		this.head_front = this.head_main.getChild("head_front");
		this.head_plate = this.head_main.getChild("head_plate");
		this.antler_right = this.head_main.getChild("antler_right");
		this.antler_left = this.head_main.getChild("antler_left");
		this.ear_left = this.head_main.getChild("ear_left");
		this.ear_right = this.head_main.getChild("ear_right");
		this.head_rear = this.head_main.getChild("head_rear");
		this.mouth = this.head_main.getChild("mouth");
	}


	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.head_main.yRot = state.yRot * (float) (Math.PI / 180.0);
		this.head_main.xRot = state.xRot * (float) (Math.PI / 180.0);
		// winding up a charge: the ram animation, from when the wind-up began
		if (state.windup) {
			this.ramAnimation.apply((long)(state.actionTime * 50.0F), 1.0F);
		}
		this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.0F);
	}
}
