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
import com.jaymospowers.client.power.web.WebParkourPose;
import com.jaymospowers.client.power.web.WebRagdoll;
import com.jaymospowers.client.power.web.WebSymbioteClient;
import com.jaymospowers.client.power.web.WebWallRunPose;
import com.jaymospowers.client.power.wind.WindHoverSway;
import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.client.power.xenoform.XenoformFlightPose;
import com.jaymospowers.client.power.xenoform.XenoformSlap;
import com.jaymospowers.client.power.zfighter.ZFighterChargePose;
import com.jaymospowers.client.power.zfighter.ZFighterFlightPose;
import com.jaymospowers.client.power.zfighter.ZFighterGuardPose;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Whether one of our own body poses (a power's flight, crawl or roll, carry, attack...) is on a player right now. Mods
 * that animate the whole player model step aside for it (see EmfCompat): their limb offsets would otherwise stay under
 * our rotations and pull the arms and legs off the body.
 */
public final class PoseTakeover {
	/** The living entity being drawn right now (LivingEntityRenderer.submit), for checks that only get the entity. */
	private static @Nullable LivingEntityRenderState drawing;
	/** The ids of the entities that had one of our poses on when last drawn (see {@link #posedEntity}). */
	private static final IntOpenHashSet POSED = new IntOpenHashSet();

	private PoseTakeover() {
	}

	public static void beginDraw(LivingEntityRenderState state) {
		drawing = state;
		if (state instanceof AvatarRenderState avatar) {
			if (posed(avatar)) {
				POSED.add(avatar.id);
				if (flying(avatar)) {
					// no walk in mid-air: the legs (and anything hung off them or the hips) stop stepping
					avatar.walkAnimationSpeed = 0.0F;
				}
			} else {
				POSED.remove(avatar.id);
			}
		}
	}

	/**
	 * Whether one of our poses was on this entity when it was last drawn. Entity Model Features animates a model again
	 * as it is finally drawn, after the renderer is done with the entity (so {@link #drawingPosed} is false by then),
	 * and that pass would lay Fresh Animations back over our poses: it asks by entity, so it's answered by entity.
	 */
	public static boolean posedEntity(Object entity) {
		return entity instanceof Entity e && POSED.contains(e.getId());
	}

	/**
	 * (0.11.43) Drawing a bow, loading or aiming a crossbow, or raising a trident: the game's own arm pose. Fresh
	 * Animations only takes these up when the arms sit at exactly the game's angles, and here (the villager body, our
	 * tweaks over the arms) they don't, so it held the arms down by the sides and the bow was drawn and loosed unseen.
	 */
	static boolean aiming(AvatarRenderState s) {
		return aims(s.rightArmPose) || aims(s.leftArmPose);
	}

	private static boolean aims(net.minecraft.client.model.HumanoidModel.ArmPose pose) {
		return pose == net.minecraft.client.model.HumanoidModel.ArmPose.BOW_AND_ARROW
			|| pose == net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_CHARGE
			|| pose == net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD
			|| pose == net.minecraft.client.model.HumanoidModel.ArmPose.THROW_TRIDENT;
	}

	/** One of the flights or glides: the body is in the air on purpose. */
	static boolean flying(AvatarRenderState s) {
		return s.getData(WindPose.KEY) != null || s.getData(WindHoverSway.KEY) != null || s.getData(FreefallPose.KEY) != null
			|| s.getData(com.jaymospowers.client.power.web.WebHangPose.KEY) != null
			|| s.getData(ZFighterFlightPose.KEY) != null || s.getData(XenoformFlightPose.KEY) != null
			|| s.getData(com.jaymospowers.client.power.smoke.SmokeFlightPose.KEY) != null
			|| s.getData(com.jaymospowers.client.rig.RigPose.KEY) != null && (s.getData(com.jaymospowers.client.rig.RigPose.KEY).hover() > 0.001F
				|| s.getData(com.jaymospowers.client.rig.RigPose.KEY).flight() > 0.001F)
			|| s.getData(WebArms.KEY) != null && s.getData(WebArms.KEY).wings() > 0.001F;
	}

	public static void endDraw() {
		drawing = null;
	}

	/** One of our poses is on whatever is being drawn right now. */
	public static boolean drawingPosed() {
		return drawing instanceof AvatarRenderState avatar && posed(avatar);
	}

	/** Kitchen's ragdoll pose key (a limp player), fetched once by reflection; null if this Kitchen has none. */
	private static net.fabricmc.fabric.api.client.rendering.v1.@Nullable RenderStateDataKey<?> ragdollKey;
	private static boolean ragdollLooked;

	/** (0.15.8) A player lying limp as Kitchen's ragdoll: its limbs are set last of all, so nothing may animate over them. */
	private static boolean ragdolled(AvatarRenderState s) {
		if (!ragdollLooked) {
			ragdollLooked = true;
			try {
				ragdollKey = (net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey<?>)
					Class.forName("com.jaymoskitchen.client.ragdoll.RagdollPose").getField("KEY").get(null);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				ragdollKey = null;
			}
		}
		return ragdollKey != null && s.getData(ragdollKey) != null;
	}

	public static boolean posed(AvatarRenderState s) {
		return aiming(s) || ragdolled(s) || s.getData(CrawlPose.KEY) != null || s.getData(LedgePose.KEY) != null
			|| s.getData(com.jaymospowers.client.freerun.RunPose.SLIDE) != null || s.getData(com.jaymospowers.client.freerun.BalancePose.KEY) != null || s.getData(com.jaymospowers.client.freerun.BarHangPose.KEY) != null || s.getData(MeditatePose.KEY) != null || s.getData(FreefallPose.KEY) != null
			|| s.getData(CarryPose.CARRIER_KEY) != null || s.getData(CarryPose.HELD_KEY) != null || s.getData(CarryPose.THROWN_KEY) != null
			|| WindPose.posed(s)
			|| s.getData(GravityWalkPose.KEY) != null || s.getData(GravityBoardPose.KEY) != null || s.getData(GravityKneelPose.KEY) != null
			|| s.getData(DarktidePose.KEY) != null || s.getData(DarktidePose.CRAWL) != null
			|| s.getData(WebWallRunPose.KEY) != null || s.getData(WebParkourPose.KEY) != null
			|| s.getData(WebBodyPose.KEY) != null || s.getData(WebArms.KEY) != null
			|| s.getData(com.jaymospowers.client.power.web.WebHangPose.KEY) != null
			|| s.getData(WebRagdoll.KEY) != null || s.getData(WebSymbioteClient.SLAM) != null || s.getData(WebSymbioteClient.CHARGE) != null
			|| s.getData(WebSymbioteClient.ROAR) != null || s.getData(WebSymbioteClient.EATING) != null
			// the symbiote's suit and Living Armor are models of their own over the body, posed the vanilla way (and Living
			// Armor hunches like a beast, WebFeral): Fresh Animations would move the body out from under them
			|| s.getData(WebSymbioteClient.SUIT) != null
			|| s.getData(WebSymbioteClient.TENDRILS) != null && s.getData(WebSymbioteClient.TENDRILS).armor()
			|| s.getData(com.jaymospowers.client.power.web.WebSuitGrowth.KEY) != null
			|| s.getData(ChronoFlowPose.KEY) != null || s.getData(ChronoReelPose.KEY) != null
			|| s.getData(XenoformFlightPose.KEY) != null || s.getData(XenoformSlap.KEY) != null
			|| s.getData(ZFighterFlightPose.KEY) != null || s.getData(com.jaymospowers.client.power.smoke.SmokeFlightPose.KEY) != null || s.getData(ZFighterChargePose.KEY) != null
			|| s.getData(com.jaymospowers.client.power.zfighter.ZFighterSpiritPose.KEY) != null
			|| s.getData(com.jaymospowers.client.power.smoke.SmokeMovePose.KEY) != null
			|| s.getData(com.jaymospowers.client.power.cryo.CryoPose.KEY) != null
			|| s.getData(com.jaymospowers.client.rig.RigPose.KEY) != null
			|| Boolean.TRUE.equals(s.getData(ZFighterGuardPose.KEY))
			|| Boolean.TRUE.equals(s.getData(com.jaymospowers.client.power.zfighter.ZFighterFiringPose.KEY))
			|| s.getData(com.jaymospowers.client.power.zfighter.RaceHoldClient.STRETCH) != null
			|| Boolean.TRUE.equals(s.getData(com.jaymospowers.client.power.ember.EmberKindleClient.KEY))
			|| s.getData(com.jaymospowers.client.power.zfighter.ZFighterComboPose.KEY) != null
			|| s.getData(com.jaymospowers.client.power.zfighter.ZFighterDiscPose.KEY) != null
			|| s.getData(ArsenalArmPose.KEY) != null || s.getData(ArsenalArmPose.AIM_KEY) != null;
	}
}
