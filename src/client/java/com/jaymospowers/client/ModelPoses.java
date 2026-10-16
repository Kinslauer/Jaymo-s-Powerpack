package com.jaymospowers.client;

import com.jaymospowers.client.power.arsenal.ArsenalArmPose;
import com.jaymospowers.client.power.chrono.ChronoFlowPose;
import com.jaymospowers.client.power.chrono.ChronoReelPose;
import com.jaymospowers.client.power.darktide.DarktidePose;
import com.jaymospowers.client.power.gravity.GravityArmPose;
import com.jaymospowers.client.power.gravity.GravityBoardPose;
import com.jaymospowers.client.power.gravity.GravityKneelPose;
import com.jaymospowers.client.power.web.WebArms;
import com.jaymospowers.client.power.web.WebBodyPose;
import com.jaymospowers.client.power.web.WebFeral;
import com.jaymospowers.client.power.web.WebParkourPose;
import com.jaymospowers.client.power.web.WebRagdoll;
import com.jaymospowers.client.power.web.WebSuitGrowth;
import com.jaymospowers.client.power.web.WebWallRunPose;
import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.client.power.xenoform.XenoformFlightPose;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
import com.jaymospowers.client.power.zfighter.ZFighterPowerClient;
import net.minecraft.client.model.Model;

/**
 * Every one of our body poses laid over a model that has just animated, in the order the model-feature mixins lay them
 * (each power's {@code ...ModelFeatureRendererMixin}, when the model is finally drawn). This is the same pass for the
 * moment the player renderer animates its model for the render layers ({@code PoseLayersLivingEntityRendererMixin}):
 * without it, layers that hang things off the body's parts (YDM's Weapon Master's holstered weapons, the Xeno-Band...)
 * saw the plain walking model and swung along with walking legs in mid-flight.
 */
public final class ModelPoses {
	private ModelPoses() {
	}

	public static void apply(Model<?> model, Object state) {
		CrawlPose.applyModel(model, state);
		MeditatePose.applyModel(model, state);
		CarryPose.applyModel(model, state);
		WindPose.applyModel(model, state);
		ArsenalArmPose.applyModel(model, state);
		FreefallPose.applyModel(model, state);
		WebArms.applyModel(model, state);
		WebFeral.applyModel(model, state);
		WebSuitGrowth.applyModel(model, state);
		WebRagdoll.applyModel(model, state);
		WebBodyPose.applyModel(model, state);
		com.jaymospowers.client.power.web.WebCounterPose.applyModel(model, state);
		com.jaymospowers.client.power.web.WebMorphLayer.applyModel(model, state);
		WebWallRunPose.applyModel(model, state);
		WebParkourPose.applyModel(model, state);
		com.jaymospowers.client.power.chrono.ChronoRunPose.applyModel(model, state);
		ChronoFlowPose.applyModel(model, state);
		ChronoReelPose.applyModel(model, state);
		GravityBoardPose.applyModel(model, state);
		GravityKneelPose.applyModel(model, state);
		GravityArmPose.applyModel(model, state);
		DarktidePose.applyModel(model, state);
		XenoformFlightPose.applyModel(model, state);
		XenoformSlap.applyModel(model, state);
		ZFighterPowerClient.applyModel(model, state);
		com.jaymospowers.client.power.smoke.SmokeMovePose.applyModel(model, state);
		com.jaymospowers.client.rig.RigPose.applyModel(model, state);
	}
}
