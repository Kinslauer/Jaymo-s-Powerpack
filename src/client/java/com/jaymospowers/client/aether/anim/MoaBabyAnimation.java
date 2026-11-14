/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/animation/MoaBabyAnimation.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, nothing else.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.anim;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public class MoaBabyAnimation {
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.0F).looping()
			.addAnimation("leg_l_1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(-32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.5F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.0F, KeyframeAnimations.degreeVec(-32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
			))
			.addAnimation("leg_r_1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.5F, KeyframeAnimations.degreeVec(-32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.0F, KeyframeAnimations.degreeVec(32.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
			))
			.build();

	public static final AnimationDefinition FLY = AnimationDefinition.Builder.withLength(0.25F).looping()
			.addAnimation("wing_l", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 60.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.125F, KeyframeAnimations.degreeVec(-60.0F, 0.0F, 60.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.25F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 60.0F), AnimationChannel.Interpolations.CATMULLROM)
			))
			.addAnimation("wing_r", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(5.0F, 0.0F, -60.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.125F, KeyframeAnimations.degreeVec(-60.0F, 0.0F, -60.0F), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.25F, KeyframeAnimations.degreeVec(5.0F, 0.0F, -60.0F), AnimationChannel.Interpolations.CATMULLROM)
			))
			.build();

	public static final AnimationDefinition SIT = AnimationDefinition.Builder.withLength(0.0F)
			.addAnimation("body_main", new AnimationChannel(AnimationChannel.Targets.POSITION,
					new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, -4.75F, 0.0F), AnimationChannel.Interpolations.LINEAR)
			))
			.addAnimation("leg_l_1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
			))
			.addAnimation("leg_l_2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(-45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
			))
			.addAnimation("leg_r_1", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
			))
			.addAnimation("leg_r_2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0F, KeyframeAnimations.degreeVec(-45.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
			))
			.build();
}
