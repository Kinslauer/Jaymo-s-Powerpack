package com.jaymoskingdoms.client.dragons;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskingdoms.client.PowersClimbClient;
import com.jaymoskingdoms.dragons.WindDragon;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * A wind dragon being climbed onto (Jaymo's Powers' mount climb, {@code equestrian.Boarding} decides how): it lowers its
 * neck, so the rider climbs up it from its head, or its wings, so they climb up one from its side. Read each frame from
 * Powers' climb ({@link PowersClimbClient}), so the neck and wings go down and come back up in step with the rider; a
 * dragon nobody is climbing on is untouched. The GeckoLib dragons' renderers call {@link #extract} and {@link #apply}; the
 * Kintsugi model ({@link KintsugiDragonModel}) reads the same pose from its render state.
 */
final class DragonBoarding {
	/** How far a lowered neck bends down in all, spread over its bones (radians), and how much further the head tips. */
	static final float NECK_DOWN = 65.0F * Mth.DEG_TO_RAD, HEAD_DOWN = 20.0F * Mth.DEG_TO_RAD;
	/** How far each wing is lowered (or, for the ones that hang folded at rest, let out) towards a ramp (radians about z). */
	static final float WING_RAMP = 32.0F * Mth.DEG_TO_RAD;

	static final DataTicket<PowersClimbClient.Pose> POSE = DataTicket.create("jaymoskingdoms_dragon_boarding", PowersClimbClient.Pose.class);
	private static final PowersClimbClient.Pose NONE = new PowersClimbClient.Pose(0, 0, 0.0F, 0.0F);

	private DragonBoarding() {
	}

	/** One wing's root bone, and which way about z lowers it (+1 or -1: it depends on which side of the model it is built on). */
	record Wing(String bone, float sign) {
	}

	/** What the dragon is being asked for this frame (never null). */
	static PowersClimbClient.Pose pose(WindDragon dragon, float partialTicks) {
		PowersClimbClient.Pose p = PowersClimbClient.on(dragon.getId(), partialTicks);
		return p == null ? NONE : p;
	}

	/** From {@code addRenderData}: the climb onto the render state. */
	static void extract(WindDragon dragon, LivingEntityRenderState state, float partialTicks) {
		state.addGeckolibData(POSE, pose(dragon, partialTicks));
	}

	/**
	 * From {@code adjustModelBonesForRender}, after the look-about: the neck bones ({@code neck}, root to head, with {@code head}
	 * tipped further) bent down for a {@link PowersClimbClient#NECK} pose, or {@code wings} lowered for a {@link PowersClimbClient#WING} one.
	 * GeckoLib turns x the other way round from a ModelPart, so down is minus.
	 */
	static void apply(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots, String[] neck, String head, Wing... wings) {
		PowersClimbClient.Pose p = info.getOrDefaultGeckolibData(POSE, NONE);
		if (p.amount() <= 0.0F) {
			return;
		}
		if (p.style() == PowersClimbClient.NECK) {
			float each = NECK_DOWN * p.amount() / neck.length;
			for (String bone : neck) {
				snapshots.ifPresent(bone, b -> b.setRotX(b.getRotX() - each));
			}
			snapshots.ifPresent(head, b -> b.setRotX(b.getRotX() - HEAD_DOWN * p.amount()));
		} else if (p.style() == PowersClimbClient.WING) {
			for (Wing wing : wings) {
				snapshots.ifPresent(wing.bone(), b -> b.setRotZ(b.getRotZ() + wing.sign() * WING_RAMP * p.amount()));
			}
		}
	}
}
