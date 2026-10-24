package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbiote;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;

/**
 * Living Armor moves like a beast. Standing, it looms like a giant: leaning forward at the hip, head thrust forward, the
 * arms hanging heavy and wide, legs apart and bent, twitching now and then.
 * Sprinting, it drops onto all fours and gallops, the arms its front legs (not your own body in first person: bent
 * double, its back and front legs swung up in front of the camera; it keeps the standing hunch there). Swelling into the big body it doubles
 * over in pain first, clutching itself and shuddering, then straightens into the hunch. Rearing for a slam in the
 * air (see WebSlam) both fists come up over its head. The big body worn alone, its tendrils drawn in
 * ({@link WebSymbioteClient#TENDRILS_IN}), is no beast: it stands and walks upright, a heavy, imposing brute (chest
 * up, arms held out wide by the lats and swinging heavy, legs planted apart, the whole of it rolling and dropping onto
 * each step; sprinting, it leans into a two-legged charge, arms pumping; {@link #brute}). Everything is set outright
 * from the render state, so applying it twice changes nothing.
 */
public final class WebFeral {
	/** How far the body bends forward at the hip (radians): hunched, and running on all fours. */
	static final float IDLE_BEND = 0.35F, RUN_BEND = 1.35F;
	/** Doubled over in pain while it grows; the knees bent in the hunch (radians), and how far that sinks it (pixels). */
	private static final float PAIN_BEND = 1.3F, KNEE_BEND = 0.3F;
	/** The big body's head: how hard it twitches now and then, and how far it's tilted standing (radians; were 0.25/0.12). */
	private static final float HEAD_TWITCH = 0.1F, HEAD_TILT = 0.04F;
	/**
	 * Its share of the growth: doubling over until {@code PAIN_IN}, writhing as the goo flows over it until
	 * {@code PAIN_OUT}, then rising as it swells into the brute (WebHulk.SWELL_FROM).
	 */
	private static final float PAIN_IN = 0.15F, PAIN_OUT = 0.62F;
	/** The hip, in model pixels below the neck. */
	private static final float HIP = 12.0F;
	/**
	 * The upright brute (the big body with its tendrils drawn in): leaning forward {@code BRUTE_LEAN} at the hip, into a
	 * sprint {@code BRUTE_SPRINT_LEAN} (radians); the legs swinging {@code BRUTE_STRIDE} (the game's walk swings 1.4),
	 * {@code BRUTE_SPRINT_STRIDE} sprinting, planted {@code BRUTE_STANCE} apart; the arms held out {@code BRUTE_SPREAD},
	 * hanging {@code BRUTE_HANG} forward and swinging {@code BRUTE_ARMS} ({@code BRUTE_SPRINT_ARMS} sprinting).
	 */
	static final float BRUTE_LEAN = 0.1F, BRUTE_SPRINT_LEAN = 0.38F, BRUTE_STRIDE = 1.1F, BRUTE_SPRINT_STRIDE = 1.35F, BRUTE_STANCE = 0.1F;
	static final float BRUTE_SPREAD = 0.3F, BRUTE_HANG = -0.12F, BRUTE_ARMS = 0.55F, BRUTE_SPRINT_ARMS = 1.0F;
	/**
	 * Its weight: with each step the torso rolls {@code BRUTE_ROLL} onto the leg it lands on and twists
	 * {@code BRUTE_TWIST} with the swing of the arms (radians), the body drops {@code BRUTE_BOB} (pixels) onto each foot
	 * and the head nods {@code BRUTE_NOD}; standing, the chest heaves {@code BRUTE_BREATH}. It takes about
	 * {@code 1 / SPRINT_RATE} seconds to lean into a sprint.
	 */
	static final float BRUTE_ROLL = 0.05F, BRUTE_TWIST = 0.1F, BRUTE_BOB = 0.6F, BRUTE_NOD = 0.06F, BRUTE_BREATH = 0.03F, SPRINT_RATE = 4.0F;

	private WebFeral() {
	}

	/** Poses the model if this is Living Armor (and nothing else, a web swing or a dive, has the body). */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		boolean slim = avatar.skin != null && avatar.skin.model() == PlayerModelType.SLIM;
		Float charge = avatar.getData(WebSymbioteClient.CHARGE);
		WebSymbioteClient.Tendrils tendrils = avatar.getData(WebSymbioteClient.TENDRILS);
		// Living Armor on the run keeps galloping while it charges a leap; standing (or the plain suit), it coils
		if (tendrils != null && !tendrils.armor()) {
			// only the tendrils are out: no beast in the body
			tendrils = null;
		}
		// the big body worn alone (its tendrils drawn in): upright
		boolean upright = upright(avatar);
		Float slam = avatar.getData(WebSymbioteClient.SLAM);
		if (slam != null && (tendrils != null || upright)) {
			overhead(humanoid, avatar, slam, slim);
			return;
		}
		boolean galloping = tendrils != null && tendrils.run() > 0.3F && avatar.walkAnimationSpeed > 0.3F;
		if (charge != null && !galloping) {
			coil(humanoid, avatar, charge, slim);
			return;
		}
		if (tendrils == null && !upright || avatar.getData(WebArms.KEY) != null || avatar.isFallFlying || avatar.swimAmount > 0.0F
			|| avatar.isPassenger || avatar.isCrouching && charge == null) {
			return;
		}
		if (upright) {
			brute(humanoid, avatar, slim);
			return;
		}
		// eased between standing, walking and the gallop (see WebSymbioteClient.smoothRun)
		float run = ownView(avatar.id) ? 0.0F : tendrils.run() * Mth.clamp(0.4F + avatar.walkAnimationSpeed, 0.0F, 1.0F);
		pose(humanoid, avatar, run, slim);
		WebSuitGrowth.Growth growth = avatar.getData(WebSuitGrowth.KEY);
		if (growth != null && growth.to() == 2 && growth.from() < 2) {
			float t = growth.t();
			float pain = t < PAIN_IN ? smooth(t / PAIN_IN) : t < PAIN_OUT ? 1.0F : 1.0F - smooth((t - PAIN_OUT) / (1.0F - PAIN_OUT));
			agony(humanoid, avatar, pain, slim);
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/**
	 * Swelling into the big body, in pain: doubled over at the hip, head hanging, arms clutched in to the belly, knees
	 * buckling, the whole of it shuddering in spasms. {@code w}: how far into it (0 is the plain hunch).
	 */
	private static void agony(HumanoidModel<?> h, AvatarRenderState state, float w, boolean slim) {
		float age = state.ageInTicks;
		float spasm = (Mth.sin(age * 1.9F) * 0.6F + Mth.sin(age * 3.1F) * 0.4F) * 0.07F * w;
		float bend = Mth.lerp(w, IDLE_BEND, PAIN_BEND) + spasm;
		float armY = slim ? 2.5F : 2.0F;
		place(h.body, 0.0F, 0.0F, bend);
		place(h.head, 0.0F, 0.0F, bend);
		place(h.rightArm, -5.0F, armY, bend);
		place(h.leftArm, 5.0F, armY, bend);
		h.body.xRot = bend;
		// the head hangs, then jerks up now and then
		float jerk = (float)Math.pow(Math.max(0.0F, Mth.sin(age * 0.37F)), 12.0) * 0.6F;
		h.head.xRot = Mth.lerp(w, h.head.xRot, -bend * 0.2F - jerk);
		h.head.zRot = Mth.sin(age * 0.9F) * 0.12F * w;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			float side = arm == h.rightArm ? 1.0F : -1.0F;
			// clutched in to the belly
			arm.xRot = Mth.lerp(w, arm.xRot, -0.9F - bend * 0.3F + spasm * 2.0F);
			arm.yRot = Mth.lerp(w, arm.yRot, -side * 0.55F);
			arm.zRot = Mth.lerp(w, arm.zRot, -side * 0.25F);
		}
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			float side = leg == h.rightLeg ? 1.0F : -1.0F;
			leg.xRot = Mth.lerp(w, leg.xRot, -0.55F);
			leg.zRot = side * Mth.lerp(w, 0.14F, 0.22F);
		}
		h.root().y = Math.max(h.root().y, 12.0F * (1.0F - Mth.cos(0.55F * w)));
	}

	/**
	 * Rearing for a slam in the air: arched back a little, both arms raised high over the head with the fists together,
	 * knees drawn up. {@code c}: how far up (0-1).
	 */
	private static void overhead(HumanoidModel<?> h, AvatarRenderState state, float c, boolean slim) {
		float e = smooth(c);
		float arch = -0.25F * e;
		float armY = slim ? 2.5F : 2.0F;
		place(h.body, 0.0F, 0.0F, arch);
		place(h.head, 0.0F, 0.0F, arch);
		place(h.rightArm, -5.0F, armY, arch);
		place(h.leftArm, 5.0F, armY, arch);
		h.body.xRot = arch;
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F + 0.2F * e;
		float shake = Mth.sin(state.ageInTicks * 2.1F) * 0.03F * e;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			float side = arm == h.rightArm ? 1.0F : -1.0F;
			arm.xRot = Mth.lerp(e, 0.0F, -2.9F) + shake;
			arm.yRot = 0.0F;
			// the fists meet over the head
			arm.zRot = side * Mth.lerp(e, 0.4F, -0.3F);
		}
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			float side = leg == h.rightLeg ? 1.0F : -1.0F;
			leg.xRot = Mth.lerp(e, 0.0F, side > 0.0F ? -0.7F : -0.35F);
			leg.zRot = side * 0.1F * e;
		}
	}

	/**
	 * Coiling for the suit's charged leap: sinking into a deep crouch, bent low over the knees, arms swept back, head
	 * up at where it's going. {@code c}: how far into the charge (0-1).
	 */
	private static void coil(HumanoidModel<?> h, AvatarRenderState state, float c, boolean slim) {
		float e = c * (2.0F - c);
		float bend = 0.75F * e;
		float squat = 0.85F * e;
		float armY = slim ? 2.5F : 2.0F;
		place(h.body, 0.0F, 0.0F, bend);
		place(h.head, 0.0F, 0.0F, bend);
		place(h.rightArm, -5.0F, armY, bend);
		place(h.leftArm, 5.0F, armY, bend);
		h.body.xRot = bend;
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.4F - bend * 0.9F;
		// arms swept back behind, ready to swing through; a tremble as it builds
		float shiver = Mth.sin(state.ageInTicks * 2.3F) * 0.03F * e;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			float side = arm == h.rightArm ? 1.0F : -1.0F;
			arm.xRot = 0.9F * e - bend + shiver;
			arm.yRot = 0.0F;
			arm.zRot = side * (0.15F + 0.2F * e);
		}
		// knees forward: the legs swing forward from the hip and the whole body sinks so the feet stay down
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			float side = leg == h.rightLeg ? 1.0F : -1.0F;
			leg.xRot = -squat;
			leg.zRot = side * 0.12F * e;
		}
		float sink = 12.0F * (1.0F - Mth.cos(squat));
		ModelPart root = h.root();
		root.y = sink;
	}

	private static void pose(HumanoidModel<?> h, AvatarRenderState state, float run, boolean slim) {
		float bend = Mth.lerp(run, IDLE_BEND, RUN_BEND);
		float pos = state.walkAnimationPos;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float breathe = Mth.sin(state.ageInTicks * 0.08F);
		float armY = slim ? 2.5F : 2.0F;
		// the upper body turns about the hip
		place(h.body, 0.0F, 0.0F, bend);
		place(h.head, 0.0F, 0.0F, bend);
		place(h.rightArm, -5.0F, armY, bend);
		place(h.leftArm, 5.0F, armY, bend);
		h.body.xRot = bend + breathe * 0.03F;
		h.body.yRot = 0.0F;
		// the head stays up, looking ahead (and where the player looks, a little); now and then it twitches, a little
		// (it sits on the torso's neck, WebHulk.neck: Andy, "the head clips and animates separately it looks like")
		float twitch = (float)Math.pow(Math.max(0.0F, Mth.sin(state.ageInTicks * 0.23F) * Mth.sin(state.ageInTicks * 0.071F)), 10.0);
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F - bend * 0.85F + twitch * HEAD_TWITCH;
		h.head.yRot += Mth.sin(state.ageInTicks * 1.7F) * HEAD_TWITCH * 0.5F * twitch;
		h.head.zRot = (1.0F - run) * HEAD_TILT + twitch * HEAD_TWITCH * 0.5F;

		// hunched: arms hang heavy, forward and out, swinging as it walks
		float walkSwing = Mth.cos(pos * 0.6662F) * speed * 0.6F;
		float idleRight = 0.5F + walkSwing + breathe * 0.04F;
		float idleLeft = 0.5F - walkSwing + breathe * 0.04F;
		// galloping: both arms reach and pull together, the legs a beat behind
		float gallop = pos * 0.45F;
		float runArms = Mth.sin(gallop) * 0.95F * speed;
		float runLegs = Mth.sin(gallop + Mth.PI) * 0.95F * speed;
		boolean swinging = state.swingAnimation > 0.0F;
		ModelPart attacking = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT ? h.leftArm : h.rightArm;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			float side = arm == h.rightArm ? 1.0F : -1.0F;
			if (swinging && arm == attacking) {
				// a punch keeps its own swing, from the bent shoulder
				arm.xRot -= bend * 0.6F;
				continue;
			}
			float world = Mth.lerp(run, arm == h.rightArm ? idleRight : idleLeft, runArms);
			arm.xRot = world - bend;
			arm.yRot = 0.0F;
			arm.zRot = side * Mth.lerp(run, 0.55F + breathe * 0.03F, 0.12F);
		}
		// legs: planted wide, bent a little; running, they drive together
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			float side = leg == h.rightLeg ? 1.0F : -1.0F;
			if (run > 0.0F) {
				leg.xRot = Mth.lerp(run, leg.xRot, runLegs + 0.2F);
			}
			leg.zRot = side * Mth.lerp(run, 0.2F, 0.05F);
			if (run < 1.0F) {
				// knees bent in the hunch
				leg.xRot -= KNEE_BEND * (1.0F - run);
			}
		}
		// sunk a little on the bent legs, so the feet stay down
		h.root().y = 12.0F * (1.0F - Mth.cos(KNEE_BEND * (1.0F - run)));
	}

	/** The big body worn alone, its tendrils drawn in: it stands and walks upright ({@link #brute}). */
	static boolean upright(AvatarRenderState state) {
		return Boolean.TRUE.equals(state.getData(WebSymbioteClient.TENDRILS_IN));
	}

	/** How far each upright brute leans into a sprint (eased), and when that was last looked at. */
	private static final class Sprint {
		float value;
		long at;
	}

	private static final java.util.Map<Integer, Sprint> SPRINT = new java.util.HashMap<>();

	/** How far into a sprint this upright brute is now (0-1, eased in and out over {@code 1 / SPRINT_RATE} seconds). */
	private static float sprint(int id) {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		float target = minecraft.level != null && minecraft.level.getEntity(id) instanceof Player player && player.isSprinting() ? 1.0F : 0.0F;
		long now = System.nanoTime();
		Sprint sprint = SPRINT.computeIfAbsent(id, key -> {
			Sprint fresh = new Sprint();
			fresh.value = target;
			fresh.at = now;
			return fresh;
		});
		float seconds = Math.min(0.25F, (now - sprint.at) / 1.0E9F);
		sprint.at = now;
		sprint.value += (target - sprint.value) * Math.min(1.0F, seconds * SPRINT_RATE);
		if (SPRINT.size() > 64) {
			SPRINT.keySet().removeIf(key -> key != id && (minecraft.level == null || minecraft.level.getEntity(key) == null));
		}
		return sprint.value;
	}

	/** How far an upright brute is into its sprint, as last eased (0 if it hasn't been drawn). */
	private static float sprinted(int id) {
		Sprint sprint = SPRINT.get(id);
		return sprint == null ? 0.0F : sprint.value;
	}

	/** How far an upright brute's legs swing in its stride (as the game's walk swings 1.4; WebHulk.Model.limbs bends the knees to it). */
	static float stride(AvatarRenderState state) {
		return Mth.lerp(sprinted(state.id), BRUTE_STRIDE, BRUTE_SPRINT_STRIDE);
	}

	/**
	 * The big body upright (its tendrils drawn in): a heavy, imposing brute. It stands tall, chest out and leaning a
	 * little forward, legs planted apart, the arms held out wide by its lats and hanging heavy a little forward, the chest
	 * heaving. Walking, it strides the game's way (each arm with the other leg), the torso rolling onto each foot and
	 * twisting with the arms, the body dropping onto each step and the head nodding with it; sprinting, it leans into a
	 * two-legged charge, legs driving long and arms pumping. A punch, or an arm holding something, keeps its own pose.
	 */
	private static void brute(HumanoidModel<?> h, AvatarRenderState state, boolean slim) {
		float sprint = sprint(state.id);
		float pos = state.walkAnimationPos;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float idle = 1.0F - speed;
		float breathe = Mth.sin(state.ageInTicks * 0.08F);
		float phase = pos * 0.6662F;
		// footfalls at either end of the stride (sin 0), the body highest as the legs pass
		float footfall = 1.0F - Math.abs(Mth.sin(phase));
		float lean = Mth.lerp(sprint, BRUTE_LEAN, BRUTE_SPRINT_LEAN) - breathe * BRUTE_BREATH * idle;
		float armY = slim ? 2.5F : 2.0F;
		place(h.body, 0.0F, 0.0F, lean);
		place(h.head, 0.0F, 0.0F, lean);
		place(h.rightArm, -5.0F, armY, lean);
		place(h.leftArm, 5.0F, armY, lean);
		h.body.xRot = lean;
		// the right shoulder comes forward with the right arm (as the left leg does)
		h.body.yRot = -Mth.cos(phase) * BRUTE_TWIST * speed;
		h.body.zRot = Mth.sin(phase) * BRUTE_ROLL * speed;
		// the head held level, looking where the player looks, nodding onto each step
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD - lean * 0.7F + footfall * BRUTE_NOD * speed;
		h.head.zRot = 0.0F;

		float arms = Mth.lerp(sprint, BRUTE_ARMS, BRUTE_SPRINT_ARMS);
		ModelPart attacking = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT ? h.leftArm : h.rightArm;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			boolean right = arm == h.rightArm;
			float side = right ? 1.0F : -1.0F;
			// out wide over the lats (breathing out with the chest, held in a little in a sprint)
			arm.zRot = side * (BRUTE_SPREAD * (1.0F - 0.4F * sprint) + breathe * 0.02F * idle);
			if (state.swingAnimation > 0.0F && arm == attacking || (right ? state.rightArmPose : state.leftArmPose) != HumanoidModel.ArmPose.EMPTY) {
				// a punch, or something held: the game's own swing, from the leaning shoulder
				continue;
			}
			// the game's walk: each arm with the leg on the other side
			arm.xRot = Mth.cos(phase + (right ? Mth.PI : 0.0F)) * arms * speed + BRUTE_HANG - lean;
			arm.yRot = 0.0F;
		}
		float stride = Mth.lerp(sprint, BRUTE_STRIDE, BRUTE_SPRINT_STRIDE);
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			boolean right = leg == h.rightLeg;
			leg.xRot = Mth.cos(phase + (right ? 0.0F : Mth.PI)) * stride * speed;
			leg.yRot = 0.0F;
			leg.zRot = (right ? 1.0F : -1.0F) * BRUTE_STANCE * (1.0F - 0.5F * sprint);
		}
		// dropping onto each foot
		h.root().y = BRUTE_BOB * speed * footfall;
	}

	/** Puts a part's pivot where it ends up when everything above the hip bends forward by {@code bend}. */
	private static void place(ModelPart part, float restX, float restY, float bend) {
		float vy = restY - HIP;
		part.x = restX;
		part.y = HIP + vy * Mth.cos(bend);
		part.z = vy * Mth.sin(bend);
	}

	/** How far this player's Living Armor is bent right now (0 if it isn't up), for things drawn outside the model. */
	public static float bend(Player player, float partialTick) {
		if (!WebSymbiote.bulked(player) || player.isCrouching() || player.isFallFlying() || player.isSwimming() || player.isPassenger()) {
			return 0.0F;
		}
		if (!WebSymbiote.armored(player)) {
			// worn alone, upright (see brute)
			return Mth.lerp(sprinted(player.getId()), BRUTE_LEAN, BRUTE_SPRINT_LEAN);
		}
		float run = ownView(player.getId()) ? 0.0F : WebSymbioteClient.run(player) * Mth.clamp(0.4F + player.walkAnimation.speed(partialTick), 0.0F, 1.0F);
		return Mth.lerp(run, IDLE_BEND, RUN_BEND);
	}

	/** Your own body, drawn round the camera in first person (First-person Model): it doesn't drop to the gallop. */
	private static boolean ownView(int id) {
		net.minecraft.client.player.LocalPlayer self = net.minecraft.client.Minecraft.getInstance().player;
		return self != null && id == self.getId() && FirstPersonCompat.bodyShown();
	}

	/**
	 * Where a point on the body (model pixels: x to the wearer's left, y down from the neck, z back) is in the world,
	 * with the body bent {@code bend} at the hip.
	 */
	public static Vec3 bodyPoint(Player player, float partialTick, float bend, float x, float y, float z) {
		// the big body's frame: the hips LEG_LIFT higher, the torso TORSO long (WebHulk.shift)
		boolean big = WebSymbiote.bulked(player);
		float hip = big ? HIP - WebHulkMesh.LEG_LIFT : HIP;
		float vy = (y - HIP) * (big && y < HIP ? WebHulkMesh.TORSO / 12.0F : 1.0F);
		float my = hip + vy * Mth.cos(bend) - z * Mth.sin(bend);
		float mz = vy * Mth.sin(bend) + z * Mth.cos(bend);
		float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
		double scale = player.getScale() / 16.0;
		return player.getPosition(partialTick).add(0.0, (24.0 - my) * scale, 0.0).add(forward.scale(-mz * scale)).add(left.scale(x * scale));
	}
}
