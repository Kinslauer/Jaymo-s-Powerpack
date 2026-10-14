package com.jaymospowers.client;

import com.jaymospowers.client.power.arsenal.ArsenalArmPose;
import com.jaymospowers.client.power.chrono.ChronoFlowPose;
import com.jaymospowers.client.power.chrono.ChronoReelPose;
import com.jaymospowers.client.power.darktide.DarktidePose;
import com.jaymospowers.client.power.gravity.GravityBoardPose;
import com.jaymospowers.client.power.gravity.GravityKneelPose;
import com.jaymospowers.client.power.gravity.GravityWalkPose;
import com.jaymospowers.client.power.web.WebArms;
import com.jaymospowers.client.power.web.WebBodyPose;
import com.jaymospowers.client.power.web.WebRagdoll;
import com.jaymospowers.client.power.web.WebSymbioteClient;
import com.jaymospowers.client.power.web.WebWallRunPose;
import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.client.power.xenoform.XenoformFlightPose;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
import com.jaymospowers.client.power.zfighter.ZFighterChargePose;
import com.jaymospowers.client.power.zfighter.ZFighterFlightPose;
import com.jaymospowers.client.power.zfighter.ZFighterGuardPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Whether one of our own body poses (a power's flight, crawl or roll, carry, attack...) is on a player right now. Mods
 * that animate the whole player model step aside for it (see EmfCompat): their limb offsets would otherwise stay under
 * our rotations and pull the arms and legs off the body.
 */
public final class PoseTakeover {
	/** The living entity being drawn right now (LivingEntityRenderer.submit), for checks that only get the entity. */
	private static @Nullable LivingEntityRenderState drawing;

	private PoseTakeover() {
	}

	public static void beginDraw(LivingEntityRenderState state) {
		drawing = state;
	}

	public static void endDraw() {
		drawing = null;
	}

	/** One of our poses is on whatever is being drawn right now. */
	public static boolean drawingPosed() {
		return drawing instanceof AvatarRenderState avatar && posed(avatar);
	}

	public static boolean posed(AvatarRenderState s) {
		return s.getData(CrawlPose.KEY) != null || s.getData(FreefallPose.KEY) != null
			|| s.getData(CarryPose.CARRIER_KEY) != null || s.getData(CarryPose.HELD_KEY) != null || s.getData(CarryPose.THROWN_KEY) != null
			|| WindPose.posed(s)
			|| s.getData(GravityWalkPose.KEY) != null || s.getData(GravityBoardPose.KEY) != null || s.getData(GravityKneelPose.KEY) != null
			|| s.getData(DarktidePose.KEY) != null || s.getData(DarktidePose.CRAWL) != null
			|| s.getData(WebWallRunPose.KEY) != null || s.getData(WebBodyPose.KEY) != null || s.getData(WebArms.KEY) != null
			|| s.getData(WebRagdoll.KEY) != null || s.getData(WebSymbioteClient.SLAM) != null || s.getData(WebSymbioteClient.CHARGE) != null
			|| s.getData(WebSymbioteClient.ROAR) != null || s.getData(WebSymbioteClient.EATING) != null
			|| s.getData(ChronoFlowPose.KEY) != null || s.getData(ChronoReelPose.KEY) != null
			|| s.getData(XenoformFlightPose.KEY) != null || s.getData(XenoformSlap.KEY) != null
			|| s.getData(ZFighterFlightPose.KEY) != null || s.getData(ZFighterChargePose.KEY) != null
			|| Boolean.TRUE.equals(s.getData(ZFighterGuardPose.KEY))
			|| s.getData(ArsenalArmPose.KEY) != null || s.getData(ArsenalArmPose.AIM_KEY) != null;
	}
}
