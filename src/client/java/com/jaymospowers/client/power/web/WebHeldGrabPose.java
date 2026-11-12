package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebHeldGrab;
import com.jaymospowers.power.web.WebLatch;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.7) Something held up in the left hand ({@link WebHeldGrab}): the left arm raised and bent in front, turned in a
 * little to hold it, the body turned toward it, the head glancing at it; and the six attacks on it, each a short move
 * over {@link WebHeldGrab#ANIM_TICKS} that peaks where the blow lands ({@link WebHeldGrab#HIT_TICK}): the bite (the head
 * lunges down into it, the teeth bared, WebHulk.teeth), the stab (the right hand drawn back, then driven in, a spike on
 * it), the weapon arm (raised high and brought down, a blade along it), the slam (both arms drive it into the ground
 * ahead, the body bent over it), the crush (the right hand joins the left and both squeeze, trembling) and the skewer
 * (the body twists and the right shoulder lifts as its tendril spears it; WebTendrilLayer.heldGrab draws the spike, the
 * blade and the tendril). The hold eases in and out ({@link WebEase}) from whatever the body was doing, and each
 * attack's weight rises and falls smoothly, so nothing snaps. Everyone sees it (the hold and the attack are synced).
 */
public final class WebHeldGrabPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** Ticks the hold pose takes to blend in and out. */
	static final float BLEND_TICKS = 5.0F;
	/** The left arm: raised this far forward (radians; -pi/2 is level), turned in across the front, and rolled a little (the bend). */
	static final float ARM_RAISE = -1.45F, ARM_IN = 0.3F, ARM_ROLL = 0.12F;
	/** The body: turned this far toward the left hand (radians), and bent forward a touch over what it holds. */
	static final float BODY_TURN = -0.2F, BODY_BEND = 0.1F;
	/** The head: glancing this far toward it (radians). */
	static final float HEAD_GLANCE = -0.15F;
	/** The bite: the head lunges this far forward and down (pixels), tipping this far (radians); the arm pulls it in by this. */
	static final float BITE_LUNGE = 2.5F, BITE_DOWN = 1.0F, BITE_TIP = 0.55F, BITE_PULL = 0.25F;
	/** The stab: the right arm drawn back to this (radians), driven in to this, turned this far across to the hand. */
	static final float STAB_BACK = 0.6F, STAB_IN = -1.45F, STAB_ACROSS = -0.45F;
	/** The weapon arm: raised this high behind (radians), brought down to this, out to the side by this, the body following by this. */
	static final float WEAPON_UP = -2.9F, WEAPON_DOWN = -1.2F, WEAPON_SIDE = -0.3F, WEAPON_ACROSS = -0.4F, WEAPON_LEAN = 0.25F;
	/** The slam: the arms driven down to this (radians), the body bent this far over it, sunk this far (pixels). */
	static final float SLAM_ARMS = -0.2F, SLAM_BEND = 0.6F, SLAM_SINK = 2.0F;
	/** The crush: the right arm joins the left at this (radians), across by this; both tremble this much, this fast. */
	static final float CRUSH_ARM = -1.35F, CRUSH_ACROSS = -0.55F, CRUSH_TREMBLE = 0.05F, CRUSH_TREMBLE_SPEED = 2.5F;
	/** The skewer: the body twists this far (radians) and the right shoulder lifts out by this, the arm back by this. */
	static final float SKEWER_TWIST = -0.25F, SKEWER_SHOULDER = 0.5F, SKEWER_ARM = 0.3F;
	/** The ankle swing: both hands on the ankles, leaning back against the weight (radians) and the shoulders winding round this far. */
	static final float SWING_ARMS = -1.15F, SWING_LEAN = -0.3F, SWING_TWIST = 0.9F;
	/** The ground and pound: the arms raised between blows (radians) and driven down to {@code SLAM_ARMS} on each; the body bends this far into each. */
	static final float POUND_RAISE = -2.3F, POUND_BEND = 0.5F;
	/** A friend over the shoulders: both arms up round their legs (radians), and the body a little forward. */
	static final float CARRY_ARM = -0.9F, CARRY_OUT = 0.35F, CARRY_BEND = 0.06F;
	/** Latched or riding: the arms reach round the mount (radians), the legs clamp round it, the body lean, and the bite's lunge (pixels) and tip. */
	static final float CLING_ARM = -1.75F, CLING_IN = 0.45F, CLING_LEG = -0.9F, CLING_LEG_OUT = 0.5F, CLING_BEND = 0.3F, CLING_LUNGE = 2.0F, CLING_TIP = 0.5F;
	// ================================================================================================================

	/**
	 * {@code in}: how far into the hold (0-1); {@code attack}: the attack under way ({@link WebHeldGrab#BITE}..., -1 none);
	 * {@code phase}: how far through it (0-1); {@code target}: the held thing's middle in the model's root space (pixels,
	 * +Y down), for things drawn at it (the skewer), or null.
	 */
	public record Grab(float in, int attack, float phase, float time, @Nullable Vec3 target, int mode) {
	}

	/** {@link Grab#mode}: in the hand, over the shoulders, latched onto a mount's back, riding a big one. */
	public static final int HAND = 0, SHOULDERS = 1, LATCHED = 2, RIDING = 3;

	public static final RenderStateDataKey<Grab> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_held_grab");

	/** Entity id → how far into the hold, eased. */
	private static final WebEase IN = new WebEase();

	private WebHeldGrabPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			state.setData(KEY, null);
			return;
		}
		WebHeldGrab.Held held = player.getAttached(WebHeldGrab.HELD);
		LivingEntity target = held == null ? null : WebHeldGrab.heldEntity(player);
		WebLatch.Latch latch = held == null ? player.getAttached(WebLatch.LATCH) : null;
		LivingEntity mount = latch == null ? null : WebLatch.mount(player);
		if (mount != null) {
			target = mount;
		}
		boolean on = held != null && target != null || mount != null;
		float in = WebEase.smooth(IN.to(entity.getId(), on ? 1.0F : 0.0F, BLEND_TICKS));
		if (in <= 0.001F) {
			state.setData(KEY, null);
			return;
		}
		float time = entity.level().getGameTime() + partialTicks;
		int attack = -1;
		float phase = 0.0F;
		if (mount != null) {
			float bite = WebLatch.biteTicks(player, partialTicks);
			if (bite >= 0.0F) {
				attack = WebHeldGrab.BITE;
				phase = bite / WebLatch.BITE_ANIM_TICKS;
			}
		} else if (on && held.attack() >= 0 && !held.carried()) {
			phase = (entity.level().getGameTime() - held.attackStart() + partialTicks) / (float)WebHeldGrab.animTicks(held.attack());
			if (phase >= 0.0F && phase < 1.0F) {
				attack = Mth.clamp(held.attack(), 0, WebHeldGrab.ATTACKS - 1);
			}
		}
		Vec3 at = null;
		if (target != null) {
			// its middle in the model's root space: the renderer turns the body to face its way, flips the model and lifts it 1.501
			Vec3 d = target.getPosition(partialTicks).add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(entity.getPosition(partialTicks));
			float yaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
			float scale = Math.max(state.scale, 0.01F);
			Vector3f q = Axis.YP.rotationDegrees(-(180.0F - yaw)).transform(new Vector3f((float)d.x / scale, (float)d.y / scale, (float)d.z / scale));
			at = new Vec3(-q.x, 1.501F - q.y, q.z).scale(16.0);
		}
		int mode = mount != null ? (latch.ride() ? RIDING : LATCHED) : held != null && held.carried() ? SHOULDERS : HAND;
		state.setData(KEY, new Grab(in, attack, phase, time, at, mode));
	}

	/** The bite is closing on it: the teeth bare (WebHulk.teeth). */
	static boolean biting(AvatarRenderState state) {
		Grab grab = state.getData(KEY);
		return grab != null && grab.attack() == WebHeldGrab.BITE && grab.phase() > 0.15F && grab.phase() < 0.85F
			&& (grab.mode() == HAND || grab.mode() >= LATCHED);
	}

	/** Where an attack lands, as a share of its animation. */
	private static float hitShare(int attack) {
		return WebHeldGrab.HIT_TICK[Mth.clamp(attack, 0, WebHeldGrab.ATTACKS - 1)] / (float)WebHeldGrab.animTicks(attack);
	}

	/** How far out an attack's reach is (0-1): rising to the blow, held a moment, then drawn back. Spikes, blades and the skewer grow by it. */
	static float grown(Grab grab) {
		if (grab.attack() < 0) {
			return 0.0F;
		}
		float hit = hitShare(grab.attack());
		float p = grab.phase();
		float hold = 0.12F;
		return p < hit ? smooth(p / hit) : p < hit + hold ? 1.0F : smooth(1.0F - (p - hit - hold) / Math.max(0.05F, 1.0F - hit - hold));
	}

	/** The wind-up and the blow: -1 fully drawn back just before the hit, +1 landed, 0 at rest at either end. */
	private static float strike(Grab grab) {
		float hit = hitShare(grab.attack());
		float p = grab.phase();
		if (p < hit) {
			// drawn back over the first part, then whipped through just before the blow
			float draw = hit * 0.7F;
			return p < draw ? -smooth(p / draw) : Mth.lerp(smooth((p - draw) / (hit - draw)), -1.0F, 1.0F);
		}
		return smooth(1.0F - (p - hit) / Math.max(0.05F, 1.0F - hit));
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** After the model animates: holding it up in the left hand, mauling it, eased from whatever the body was doing. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Grab grab = avatar.getData(KEY);
		if (grab == null || avatar.getData(WebArms.KEY) != null || avatar.getData(WebClimbPose.KEY) != null
			|| avatar.getData(WebExecutionPose.KEY) != null) {
			// ((0.16.8) an execution under way poses the whole body: the hold waits)
			return;
		}
		float[] was = WebFeral.snapshot(h);
		pose(h, avatar, grab);
		WebFeral.blend(h, was, grab.in());
	}

	/** The hold and the attack outright: set from the render state alone, so applying it twice changes nothing. */
	private static void pose(HumanoidModel<?> h, AvatarRenderState avatar, Grab grab) {
		boolean slim = avatar.skin != null && avatar.skin.model() == PlayerModelType.SLIM;
		float armY = slim ? 2.5F : 2.0F;
		if (grab.mode() == SHOULDERS) {
			poseCarry(h, avatar, armY);
			return;
		}
		if (grab.mode() >= LATCHED) {
			poseCling(h, avatar, grab, armY);
			return;
		}
		int attack = grab.attack();
		float w = attack < 0 ? 0.0F : grown(grab);
		float s = attack < 0 ? 0.0F : strike(grab);
		float turn = BODY_TURN + (attack == WebHeldGrab.SKEWER ? SKEWER_TWIST * w : 0.0F);
		float bend = BODY_BEND + (attack == WebHeldGrab.SLAM ? SLAM_BEND * w : attack == WebHeldGrab.BITE ? 0.2F * w
			: attack == WebHeldGrab.WEAPON && s > 0.0F ? WEAPON_LEAN * s : 0.0F);
		// the body turned to the hand and bent a little over what it holds; the head and arms carried round with it
		WebFeral.place(h.body, 0.0F, 0.0F, bend);
		WebFeral.place(h.head, 0.0F, 0.0F, bend);
		WebFeral.place(h.rightArm, -5.0F, armY, bend);
		WebFeral.place(h.leftArm, 5.0F, armY, bend);
		carry(h.rightArm, turn);
		carry(h.leftArm, turn);
		h.body.xRot = bend;
		h.body.yRot = turn;
		h.body.zRot = 0.0F;
		float look = avatar.xRot * Mth.DEG_TO_RAD;
		h.head.xRot = look + bend;
		h.head.yRot = avatar.yRot * Mth.DEG_TO_RAD + HEAD_GLANCE;
		h.head.zRot = 0.0F;
		// the left arm up and in, holding it
		h.leftArm.xRot = ARM_RAISE - bend;
		h.leftArm.yRot = ARM_IN + turn;
		h.leftArm.zRot = ARM_ROLL;
		// the right arm at rest (its vanilla swing is let go of while something's held)
		h.rightArm.xRot = -bend;
		h.rightArm.yRot = turn;
		h.rightArm.zRot = 0.0F;
		float sink = 0.0F;
		switch (attack) {
			case WebHeldGrab.BITE -> {
				// the head lunges down into it, the arm pulling it up to the teeth
				h.head.xRot += BITE_TIP * w;
				h.head.z -= BITE_LUNGE * w;
				h.head.y += BITE_DOWN * w;
				h.leftArm.xRot -= BITE_PULL * w;
				h.leftArm.yRot += BITE_PULL * w;
			}
			case WebHeldGrab.STAB -> {
				// drawn back, then driven across into it
				h.rightArm.xRot += s < 0.0F ? STAB_BACK * -s : STAB_IN * s;
				h.rightArm.yRot += STAB_ACROSS * Math.max(0.0F, s);
			}
			case WebHeldGrab.WEAPON -> {
				// raised high behind, brought down across it
				h.rightArm.xRot += s < 0.0F ? WEAPON_UP * -s : WEAPON_DOWN * s;
				h.rightArm.zRot += WEAPON_SIDE * Math.abs(s);
				h.rightArm.yRot += WEAPON_ACROSS * Math.max(0.0F, s);
			}
			case WebHeldGrab.SLAM -> {
				// both arms drive it down into the ground, the body folding over it
				h.leftArm.xRot = Mth.lerp(w, h.leftArm.xRot, SLAM_ARMS - bend);
				h.rightArm.xRot = Mth.lerp(w, h.rightArm.xRot, SLAM_ARMS * 2.0F - bend);
				h.rightArm.yRot += CRUSH_ACROSS * 0.5F * w;
				h.head.xRot += 0.4F * w;
				sink = SLAM_SINK * w;
			}
			case WebHeldGrab.CRUSH -> {
				// the right hand joins the left and both squeeze, shaking with it
				float tremble = Mth.sin(grab.time() * CRUSH_TREMBLE_SPEED) * CRUSH_TREMBLE * w;
				h.rightArm.xRot += CRUSH_ARM * w;
				h.rightArm.yRot += CRUSH_ACROSS * w;
				h.rightArm.zRot += tremble;
				h.leftArm.yRot += 0.3F * w;
				h.leftArm.zRot -= tremble;
				h.head.xRot += 0.2F * w;
			}
			case WebHeldGrab.SWING -> {
				// both hands on the ankles, leaning back against the weight, the shoulders winding round through the circle
				float u = Mth.clamp((grab.phase() * WebHeldGrab.SWING_ANIM_TICKS - WebHeldGrab.SWING_WINDUP)
					/ (WebHeldGrab.HIT_TICK[WebHeldGrab.SWING] - WebHeldGrab.SWING_WINDUP), 0.0F, 1.0F);
				h.body.xRot = bend + SWING_LEAN * w;
				h.body.yRot = turn + SWING_TWIST * Mth.sin(u * Mth.TWO_PI) * w;
				h.leftArm.xRot = Mth.lerp(w, h.leftArm.xRot, SWING_ARMS);
				h.rightArm.xRot = Mth.lerp(w, h.rightArm.xRot, SWING_ARMS);
				h.rightArm.yRot += CRUSH_ACROSS * 0.6F * w;
				h.leftArm.yRot -= 0.1F * w;
				h.head.yRot += h.body.yRot * 0.5F;
				h.head.xRot -= 0.1F * w;
			}
			case WebHeldGrab.POUND -> {
				// both arms hauled up and hammered down into the ground, three times, the body folding into each
				float t = grab.phase() * WebHeldGrab.POUND_ANIM_TICKS;
				float blow = 0.0F;
				for (int hit : WebHeldGrab.POUND_HITS) {
					blow = Math.max(blow, smooth(1.0F - Math.abs(t - hit) / 3.0F));
				}
				float up = w * (1.0F - blow);
				h.leftArm.xRot = Mth.lerp(w, h.leftArm.xRot, Mth.lerp(blow, POUND_RAISE, SLAM_ARMS) - bend);
				h.rightArm.xRot = Mth.lerp(w, h.rightArm.xRot, Mth.lerp(blow, POUND_RAISE, SLAM_ARMS * 2.0F) - bend);
				h.rightArm.yRot += CRUSH_ACROSS * 0.5F * w;
				h.body.xRot = bend + POUND_BEND * blow * w - 0.2F * up;
				h.head.xRot += 0.4F * blow * w;
				sink = SLAM_SINK * blow * w;
			}
			case WebHeldGrab.SKEWER -> {
				// the shoulder lifts and the body twists as the tendril goes in
				h.rightArm.zRot += SKEWER_SHOULDER * w;
				h.rightArm.xRot += SKEWER_ARM * w;
				h.head.xRot -= 0.15F * w;
			}
			default -> {
			}
		}
		h.root().y = sink;
	}

	/** A friend over the shoulders: both arms up and round their legs, the body upright and a touch forward. */
	private static void poseCarry(HumanoidModel<?> h, AvatarRenderState avatar, float armY) {
		WebFeral.place(h.body, 0.0F, 0.0F, CARRY_BEND);
		WebFeral.place(h.head, 0.0F, 0.0F, CARRY_BEND);
		WebFeral.place(h.rightArm, -5.0F, armY, CARRY_BEND);
		WebFeral.place(h.leftArm, 5.0F, armY, CARRY_BEND);
		h.body.xRot = CARRY_BEND;
		h.body.yRot = 0.0F;
		h.body.zRot = 0.0F;
		h.head.xRot = avatar.xRot * Mth.DEG_TO_RAD + CARRY_BEND;
		h.head.yRot = avatar.yRot * Mth.DEG_TO_RAD;
		h.head.zRot = 0.0F;
		h.leftArm.xRot = CARRY_ARM;
		h.leftArm.yRot = -0.1F;
		h.leftArm.zRot = -CARRY_OUT;
		h.rightArm.xRot = CARRY_ARM;
		h.rightArm.yRot = 0.1F;
		h.rightArm.zRot = CARRY_OUT;
		h.root().y = 0.0F;
	}

	/** Latched on a mount's back or riding it: arms round it, legs clamped, the body hunched in, the head lunging with each bite, the right hand raking. */
	private static void poseCling(HumanoidModel<?> h, AvatarRenderState avatar, Grab grab, float armY) {
		float bite = grab.attack() == WebHeldGrab.BITE ? grown(grab) : 0.0F;
		float rake = grab.attack() == WebHeldGrab.BITE ? Mth.sin(Mth.clamp(grab.phase(), 0.0F, 1.0F) * Mth.PI) : 0.0F;
		float bend = CLING_BEND;
		WebFeral.place(h.body, 0.0F, 0.0F, bend);
		WebFeral.place(h.head, 0.0F, 0.0F, bend);
		WebFeral.place(h.rightArm, -5.0F, armY, bend);
		WebFeral.place(h.leftArm, 5.0F, armY, bend);
		h.body.xRot = bend;
		h.body.yRot = 0.0F;
		h.body.zRot = 0.0F;
		h.head.xRot = avatar.xRot * Mth.DEG_TO_RAD * 0.5F + bend + CLING_TIP * bite;
		h.head.yRot = avatar.yRot * Mth.DEG_TO_RAD * 0.5F;
		h.head.zRot = 0.0F;
		h.head.z -= CLING_LUNGE * bite;
		h.leftArm.xRot = CLING_ARM;
		h.leftArm.yRot = CLING_IN;
		h.leftArm.zRot = 0.1F;
		h.rightArm.xRot = CLING_ARM + 1.0F * rake;
		h.rightArm.yRot = -CLING_IN;
		h.rightArm.zRot = -0.1F;
		h.rightLeg.xRot = CLING_LEG;
		h.rightLeg.yRot = 0.0F;
		h.rightLeg.zRot = CLING_LEG_OUT;
		h.leftLeg.xRot = CLING_LEG;
		h.leftLeg.yRot = 0.0F;
		h.leftLeg.zRot = -CLING_LEG_OUT;
		h.root().y = 0.0F;
	}

	/** Swings an arm's pivot round with the body's turn (about the model's up axis). */
	private static void carry(ModelPart arm, float turn) {
		float x = arm.x, z = arm.z;
		arm.x = x * Mth.cos(turn) + z * Mth.sin(turn);
		arm.z = -x * Mth.sin(turn) + z * Mth.cos(turn);
	}
}
