/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/kirrid/AbstractKirridModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, made the shared base of our zone models, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;

import com.jaymospowers.client.aether.anim.KirridAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;

abstract class KirridModel extends AetherAnimalModel {
	private final KeyframeAnimation jumpAnimation;
	private final KeyframeAnimation ramAnimation;
	private final KeyframeAnimation eatAnimation;
	private final KeyframeAnimation walkAnimation;
	protected final ModelPart head;
	protected final ModelPart neck;
	public final ModelPart body;
	public final ModelPart wool;

	KirridModel(ModelPart root) {
		super(root);
		this.jumpAnimation = KirridAnimations.JUMP.bake(root);
		this.ramAnimation = KirridAnimations.START_RAM.bake(root);
		this.eatAnimation = KirridAnimations.EAT.bake(root);
		this.walkAnimation = KirridAnimations.WALK.bake(root);
		this.body = root.getChild("body");
		this.neck = this.body.getChild("neck");
		this.head = this.neck.getChild("head");
		this.wool = this.body.getChild("wool");
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
		this.wool.visible = !state.sheared;
	}
}
