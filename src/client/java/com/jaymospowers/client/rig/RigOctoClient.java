package com.jaymospowers.client.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.rig.RigMoves;
import com.jaymospowers.rig.RigOcto;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Octo Core's four arms as everyone sees them ({@link RigOcto}): jointed gunmetal arms on the Core's spine, each a
 * shoulder strut, a forearm and a three-pronged claw, with a lit joint at the elbow. Folded, they lie short down the back;
 * out, the upper pair arcs up over the shoulders and the lower pair reaches down and out like legs. Each move poses them:
 * the upper pair reaches forward to grab and hold, the lower pair stabs forward and lifts for the skewer, all four spin
 * flat for the pinwheel, the lower pair plants behind for the vault and all four point down for the dive; the flurry and
 * the watch thrust one arm. Every angle eases toward its target, so the arms move like machinery, not snap. Also here: the
 * wall climb on the wearer's own client (the lower arms grip, and you go up a wall as on a ladder).
 * Drawn on the body bone, after the suit ({@link RigSuit}).
 */
public final class RigOctoClient {
	// ---- tuning ----
	/** How quickly each angle eases toward its target (share a frame), and how quickly the arms unfold. */
	static final float EASE = 0.22F, UNFOLD_EASE = 0.12F;
	/** Climbing: the rate up a wall (blocks a tick) and the slide when nothing is held (blocks a tick, down). */
	static final double CLIMB_UP = 0.2, SLIDE = 0.06;
	/** The pinwheel's spin (radians a tick). */
	static final float SPIN_RATE = 0.9F;

	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/rig/octo.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/rig/octo_glow.png");
	/** On the render state: per arm {pitch, yaw, elbow, reach, claw open}, then time; null when the arms aren't worn. */
	static final RenderStateDataKey<float[]> ARMS = RenderStateDataKey.create(() -> "jaymospowers:rig_octo");

	/** Arm order: upper right, upper left, lower right, lower left. Where each meets the back (body bone pixels). */
	private static final float[][] MOUNT = {{-2.5F, 2.5F, 3.0F}, {2.5F, 2.5F, 3.0F}, {-2.5F, 8.5F, 3.0F}, {2.5F, 8.5F, 3.0F}};
	private static final int VALUES = 5;

	private static final Map<UUID, float[]> POSE = new HashMap<>();
	private static final Map<UUID, Float> UNFOLD = new HashMap<>();
	private static ModelPart root;
	private static final ModelPart[] ARM = new ModelPart[4], FORE = new ModelPart[4], PRONG_A = new ModelPart[4], PRONG_B = new ModelPart[4],
		PRONG_C = new ModelPart[4];

	private RigOctoClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RigOctoClient::climb);
	}

	private static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition back = mesh.getRoot();
		CubeDeformation none = new CubeDeformation(0.0F);
		for (int i = 0; i < 4; i++) {
			PartDefinition arm = back.addOrReplaceChild("arm_" + i, CubeListBuilder.create()
				// the mount on the spine, then the shoulder strut
				.texOffs(0, 24).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 2.0F, none)
				.texOffs(0, 0).addBox(-1.0F, -1.0F, 1.0F, 2.0F, 2.0F, 9.0F, none),
				PartPose.offset(MOUNT[i][0], MOUNT[i][1], MOUNT[i][2]));
			PartDefinition fore = arm.addOrReplaceChild("fore", CubeListBuilder.create()
				// the elbow's lit joint, then the forearm
				.texOffs(12, 24).addBox(-1.25F, -1.25F, -1.25F, 2.5F, 2.5F, 2.5F, none)
				.texOffs(0, 12).addBox(-0.75F, -0.75F, 1.0F, 1.5F, 1.5F, 8.0F, none),
				PartPose.offset(0.0F, 0.0F, 10.0F));
			PartDefinition claw = fore.addOrReplaceChild("claw", CubeListBuilder.create()
				.texOffs(22, 24).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 1.5F, none), PartPose.offset(0.0F, 0.0F, 9.0F));
			claw.addOrReplaceChild("prong_a", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(0.0F, -0.7F, 1.2F));
			claw.addOrReplaceChild("prong_b", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(-0.6F, 0.5F, 1.2F));
			claw.addOrReplaceChild("prong_c", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(0.6F, 0.5F, 1.2F));
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	private static void bake() {
		if (root != null) {
			return;
		}
		root = mesh().bakeRoot();
		for (int i = 0; i < 4; i++) {
			ARM[i] = root.getChild("arm_" + i);
			FORE[i] = ARM[i].getChild("fore");
			ModelPart claw = FORE[i].getChild("claw");
			PRONG_A[i] = claw.getChild("prong_a");
			PRONG_B[i] = claw.getChild("prong_b");
			PRONG_C[i] = claw.getChild("prong_c");
		}
	}

	// ---- poses ------------------------------------------------------------------------------------------------------------

	/** Writes arm {@code i}'s target into {@code t}: pitch (up is positive), yaw (out from the spine), elbow, reach, claw open. */
	private static void set(float[] t, int i, float pitch, float yaw, float elbow, float reach, float open) {
		int o = i * VALUES;
		// the left arms mirror the right
		t[o] = pitch;
		t[o + 1] = i % 2 == 0 ? -yaw : yaw;
		t[o + 2] = elbow;
		t[o + 3] = reach;
		t[o + 4] = open;
	}

	/** Out and at rest: the upper pair arched over the shoulders, the lower pair down and out. */
	private static void rest(float[] t, float time) {
		float sway = Mth.sin(time * 0.08F) * 0.05F;
		set(t, 0, 0.9F + sway, 0.5F, 1.1F, 1.0F, 0.3F);
		set(t, 1, 0.9F - sway, 0.5F, 1.1F, 1.0F, 0.3F);
		set(t, 2, -0.55F, 0.95F, -0.7F + sway, 1.0F, 0.3F);
		set(t, 3, -0.55F, 0.95F, -0.7F - sway, 1.0F, 0.3F);
	}

	/** Folded down the spine. */
	private static void folded(float[] t) {
		for (int i = 0; i < 4; i++) {
			set(t, i, -1.45F, 0.12F, 2.7F, 0.35F, 0.0F);
		}
	}

	/** An upper arm reaching straight ahead over the shoulder. */
	private static void forward(float[] t, int i, float reach, float open) {
		set(t, i, 1.35F, 0.25F, 1.45F, reach, open);
	}

	/** A lower arm reaching ahead past the hip, lifted by {@code lift}. */
	private static void low(float[] t, int i, float lift, float reach, float open) {
		set(t, i, -0.15F + lift, 2.45F, 0.1F, reach, open);
	}

	private static void target(Player player, float[] t, float now) {
		rest(t, now);
		RigMoves.Move move = RigMoves.get(player);
		int held = RigOcto.heldId(player);
		int kind = 0;
		float k = 0.0F;
		if (move != null) {
			float len = Math.max(1, RigMoves.length(move.kind()));
			float age = now - move.start();
			if (age >= 0.0F && age < len) {
				kind = move.kind();
				k = Mth.clamp(age / len, 0.0F, 1.0F);
			}
		}
		if (held >= 0 || kind == RigOcto.MOVE_GRAB || kind == RigOcto.MOVE_HOLD) {
			// both upper arms out ahead, claws closed on it
			boolean reaching = kind == RigOcto.MOVE_GRAB && k < 0.5F;
			forward(t, 0, reaching ? 1.6F : 1.15F, reaching ? 0.9F : 0.0F);
			forward(t, 1, reaching ? 1.6F : 1.15F, reaching ? 0.9F : 0.0F);
		}
		switch (kind) {
			case RigOcto.MOVE_HURL -> {
				// snapped forward, claws open
				forward(t, 0, 1.5F, 1.0F);
				forward(t, 1, 1.5F, 1.0F);
				t[2] = t[7] = 1.65F;
			}
			case RigOcto.MOVE_SKEWER -> {
				// the lower pair stabs ahead, then lifts what it ran through overhead and brings it down
				float lift = k < 0.25F ? 0.0F : k < 0.65F ? (k - 0.25F) / 0.4F * 1.5F : 1.5F - (k - 0.65F) / 0.35F * 1.8F;
				low(t, 2, lift, k < 0.25F ? 0.6F + k * 4.0F : 1.6F, 0.0F);
				low(t, 3, lift, k < 0.25F ? 0.6F + k * 4.0F : 1.6F, 0.0F);
			}
			case RigOcto.MOVE_PINWHEEL -> {
				// all four flat out, spinning (added after the ease, below)
				for (int i = 0; i < 4; i++) {
					set(t, i, i < 2 ? 0.15F : -0.15F, i < 2 ? 1.2F : 2.0F, 0.0F, 1.2F, 0.8F);
				}
			}
			case RigOcto.MOVE_VAULT -> {
				// the lower pair planted behind and below, pushing
				set(t, 2, -1.15F, 0.3F, 0.0F, 1.3F, 0.6F);
				set(t, 3, -1.15F, 0.3F, 0.0F, 1.3F, 0.6F);
			}
			case RigOcto.MOVE_DIVE -> {
				// all four point down at what's below
				for (int i = 0; i < 4; i++) {
					set(t, i, -1.5F, i < 2 ? 2.7F : 2.3F, 0.0F, 1.3F, 0.0F);
				}
			}
			case RigOcto.MOVE_ANCHOR -> {
				// the upper right arm shoots out on its struts and draws back
				float out = k < 0.3F ? k / 0.3F : 1.0F - (k - 0.3F) / 0.7F;
				forward(t, 0, 1.0F + out * 2.6F, 0.0F);
			}
			case RigOcto.MOVE_LANCE -> forward(t, 0, 1.0F + bump(k) * 0.9F, 0.0F);
			case RigOcto.MOVE_CRESCENT -> set(t, 1, 0.9F, Mth.lerp(k, 0.4F, 2.6F), 1.2F, 1.3F, 0.6F);
			case RigOcto.MOVE_CLAMP -> {
				forward(t, 0, 1.3F, 1.0F - k);
				forward(t, 1, 1.3F, 1.0F - k);
			}
			case RigOcto.MOVE_LAUNCH -> {
				low(t, 2, bump(k) * 1.4F, 1.4F, 0.2F);
				low(t, 3, bump(k) * 1.4F, 1.4F, 0.2F);
			}
			case RigOcto.MOVE_WATCH -> {
				// one arm darts out, a different one each time
				int i = (int)(move.start() % 4L);
				if (i < 2) {
					forward(t, i, 1.0F + bump(k) * 0.9F, 0.0F);
				} else {
					low(t, i, 0.2F, 1.0F + bump(k) * 0.8F, 0.0F);
				}
			}
			default -> {
			}
		}
	}

	/** Out and back over a move (0..1..0). */
	private static float bump(float k) {
		return k < 0.35F ? k / 0.35F : 1.0F - (k - 0.35F) / 0.65F;
	}

	// ---- render -----------------------------------------------------------------------------------------------------------

	/** (From {@link RigSuit#extract}) what the arms are doing on {@code player} this frame. */
	static void extract(Player player, AvatarRenderState state, float partialTicks) {
		UUID id = player.getUUID();
		if (!RigOcto.wears(player)) {
			state.setData(ARMS, null);
			POSE.remove(id);
			UNFOLD.remove(id);
			return;
		}
		float now = player.level().getGameTime() + partialTicks;
		float unfold = UNFOLD.getOrDefault(id, 0.0F);
		unfold += ((RigOcto.out(player) ? 1.0F : 0.0F) - unfold) * UNFOLD_EASE;
		UNFOLD.put(id, unfold);
		float[] want = new float[4 * VALUES];
		float[] fold = new float[4 * VALUES];
		target(player, want, now);
		folded(fold);
		for (int i = 0; i < want.length; i++) {
			want[i] = Mth.lerp(unfold, fold[i], want[i]);
		}
		float[] pose = POSE.get(id);
		if (pose == null) {
			pose = want.clone();
			POSE.put(id, pose);
		} else {
			for (int i = 0; i < pose.length; i++) {
				pose[i] += (want[i] - pose[i]) * EASE;
			}
		}
		float[] out = new float[4 * VALUES + 2];
		System.arraycopy(pose, 0, out, 0, pose.length);
		RigMoves.Move move = RigMoves.get(player);
		boolean spinning = move != null && move.kind() == RigOcto.MOVE_PINWHEEL && now - move.start() < RigOcto.PINWHEEL_TICKS;
		out[4 * VALUES] = spinning ? (now - move.start()) * SPIN_RATE : 0.0F;
		out[4 * VALUES + 1] = now;
		state.setData(ARMS, out);
	}

	/** (From {@link RigSuit}'s layer) draws the arms on the body bone. */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel parent) {
		float[] p = state.getData(ARMS);
		if (p == null) {
			return;
		}
		bake();
		float spin = p[4 * VALUES];
		for (int i = 0; i < 4; i++) {
			int o = i * VALUES;
			ModelPart arm = ARM[i];
			// a part's own pitch: up is negative in model space, and a part points back out of the spine (+z)
			arm.xRot = p[o];
			arm.yRot = p[o + 1] + (i % 2 == 0 ? -spin : spin);
			arm.zRot = 0.0F;
			arm.zScale = Math.max(0.2F, Math.min(1.0F, p[o + 3]));
			FORE[i].xRot = p[o + 2];
			// past full length the forearm telescopes (the anchor, a long reach)
			FORE[i].zScale = Math.max(0.3F, p[o + 3]);
			float open = Mth.clamp(p[o + 4], 0.0F, 1.0F) * 0.6F;
			PRONG_A[i].xRot = -open;
			PRONG_B[i].xRot = open * 0.5F;
			PRONG_B[i].yRot = -open;
			PRONG_C[i].xRot = open * 0.5F;
			PRONG_C[i].yRot = open;
		}
		poseStack.pushPose();
		parent.body.translateAndRotate(poseStack);
		collector.submitModelPart(root, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
		collector.submitModelPart(root, poseStack, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF);
		poseStack.popPose();
	}

	// ---- the climb --------------------------------------------------------------------------------------------------------

	/** On the wearer's own client: with the arms out, against a wall, the lower arms grip it. Forward or Jump climbs, Sneak holds still. */
	private static void climb(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
			|| !player.horizontalCollision || !RigOcto.out(player) || player.isInWater()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		double y;
		if (player.input.keyPresses.jump() || player.input.keyPresses.forward() && !player.isShiftKeyDown()) {
			y = CLIMB_UP;
		} else if (player.isShiftKeyDown()) {
			y = 0.0;
		} else {
			y = Math.max(v.y, -SLIDE);
		}
		player.setDeltaMovement(v.x, y, v.z);
		player.resetFallDistance();
	}
}
