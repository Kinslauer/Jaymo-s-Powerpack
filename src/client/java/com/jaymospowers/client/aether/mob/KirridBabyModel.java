/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/kirrid/AbstractKirridBabyModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, made the shared base of our kid models, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;

import com.jaymospowers.client.aether.anim.KirridBabyAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;

abstract class KirridBabyModel extends AetherAnimalModel {
	private final KeyframeAnimation jumpAnimation;
	private final KeyframeAnimation eatAnimation;
	private final KeyframeAnimation walkAnimation;
	protected final ModelPart body_main;
	protected final ModelPart wool;
	protected final ModelPart head;
	protected final ModelPart plate;
	protected final ModelPart ears;
	protected final ModelPart leg_b_l;
	protected final ModelPart leg_b_r;
	protected final ModelPart tail;
	protected final ModelPart leg_f_l;
	protected final ModelPart leg_f_r;

	KirridBabyModel(ModelPart root) {
		super(root);
		this.jumpAnimation = KirridBabyAnimations.JUMP.bake(root);
		this.eatAnimation = KirridBabyAnimations.EAT.bake(root);
		this.walkAnimation = KirridBabyAnimations.WALK.bake(root);
		this.body_main = root.getChild("body_main");
		this.wool = this.body_main.getChild("wool");
		this.head = this.body_main.getChild("head");
		this.plate = this.head.getChild("plate");
		this.ears = this.head.getChild("ears");
		this.leg_b_l = this.body_main.getChild("leg_b_l");
		this.leg_b_r = this.body_main.getChild("leg_b_r");
		this.tail = this.body_main.getChild("tail");
		this.leg_f_l = this.body_main.getChild("leg_f_l");
		this.leg_f_r = this.body_main.getChild("leg_f_r");
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
		this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
		boolean leaping = state.air > 0.0F && state.walkAnimationSpeed > 0.1F;
		if (leaping) {
			this.jumpAnimation.apply((long)(state.actionTime * 50.0F), 1.0F);
		} else {
			this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.0F);
		}
		if (state.eating > 0.0F) {
			this.eatAnimation.apply((long)(state.ageInTicks * 50.0F), 1.0F);
		}
		// a kid has no plate yet (it grows in), and its wool comes off like a grown one's
		this.plate.visible = false;
		this.wool.visible = !state.sheared;
	}
}
