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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Octo Core's four arms as everyone sees them ({@link RigOcto}): jointed gunmetal arms on the Core's spine, each a
 * shoulder strut, a forearm and a three-pronged claw, with a lit joint at the elbow. Both struts telescope (Andy: "they
 * can be that size at rest and should telescope further out"): a polished rod slides out of each, so an arm reaches from
 * its rest length to several blocks. Folded, they lie short down the back; out, the upper pair arcs up over the shoulders
 * and the lower pair reaches down and out like legs.
 * <p>Where an arm has somewhere to be in the world, it is posed there ({@link #reach}: two joints and the rods, worked out
 * from the point): walking or climbing, each claw on the block it planted on ({@link RigOctoWalk}); holding, the upper
 * claws on either side of what they hold, wherever it is (so a grab telescopes out to it and draws it in); the anchor's
 * claw on the wall or creature it bit; the jab and the cross punch out to the first thing down the look; the blast brings
 * all four claws together ahead of the chest. Other moves pose them by hand: the lower pair stabs and lifts for the
 * skewer, all four spin flat for the pinwheel, the lower pair plants behind for the vault and all four point down for the
 * dive; the flurry and the watch thrust one arm. Every angle eases toward its target, so the arms move like machinery.
 * <p>Also here, on the wearer's own client (movement is the client's): the wall climb (the arms grip, and you go up a wall
 * as on a ladder) and the arm walk (carried on the four arms, held up off the ground between them, the symbiote's stride).
 * Drawn on the body bone, after the suit ({@link RigSuit}).
 */
public final class RigOctoClient {
	// ---- tuning ----
	/** How quickly each angle eases toward its target (share a frame), with a claw placed in the world, and how quickly the arms unfold. */
	static final float EASE = 0.22F, EASE_PLACED = 0.55F, UNFOLD_EASE = 0.12F;
	/** Climbing: the rate up a wall (blocks a tick) and the slide when nothing is held (blocks a tick, down). */
	static final double CLIMB_UP = 0.2, SLIDE = 0.06;
	/**
	 * The arm walk: held {@code STRIDE_HEIGHT} blocks off the ground (eased there at {@code STRIDE_LIFT} of the gap a tick),
	 * walked along at {@code STRIDE_SPEED} ({@code STRIDE_SPRINT} sprinting), the grip taking {@code STRIDE_GRIP} of the
	 * change a tick; Jump climbs and Sneak lowers at {@code STRIDE_CLIMB}, and climbing needs {@code STRIDE_RISE_ANCHORS}
	 * ways to grab.
	 */
	static final double STRIDE_HEIGHT = 1.3, STRIDE_LIFT = 0.3, STRIDE_SPEED = 0.2, STRIDE_SPRINT = 0.3, STRIDE_GRIP = 0.35, STRIDE_CLIMB = 0.22;
	static final int STRIDE_RISE_ANCHORS = 4;
	/** The pinwheel's spin (radians a tick). */
	static final float SPIN_RATE = 0.9F;
	/** How far the rods may run out (times the struts' own length): walking, and reaching for something far off (a grab, the anchor, a punch). */
	static final float EXT_WALK = 3.5F, EXT_FAR = 16.0F;
	/** The jab and cross: how far down the look they punch with nothing in reach (blocks). */
	static final double PUNCH_EMPTY = 3.0;

	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/rig/octo.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/rig/octo_glow.png");
	/** On the render state: per arm {pitch, yaw, elbow, reach, claw open}, then the spin and time; null when the arms aren't worn. */
	static final RenderStateDataKey<float[]> ARMS = RenderStateDataKey.create(() -> "jaymospowers:rig_octo");

	/** Arm order: upper right, upper left, lower right, lower left. Where each meets the back (body bone pixels). */
	private static final float[][] MOUNT = {{-2.5F, 2.5F, 3.0F}, {2.5F, 2.5F, 3.0F}, {-2.5F, 8.5F, 3.0F}, {2.5F, 8.5F, 3.0F}};
	/** The arm's lengths (pixels): the shoulder strut (to the elbow, at rest), the forearm (to the claw), the claw to its prongs' tips. */
	private static final float UPPER = 10.0F, FORE = 9.0F, CLAW_TIP = 4.2F;
	/** How much each strut grows (pixels) for every unit of reach past 1: the rods sliding out. */
	private static final float UPPER_ROD = 9.0F, FORE_ROD = 8.0F;
	private static final int VALUES = 5;

	private static final Map<UUID, float[]> POSE = new HashMap<>();
	private static final Map<UUID, Float> UNFOLD = new HashMap<>();
	/** Where each wearer's anchor bit (worked out once, when the move starts): the move's start, and the point. */
	private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();

	private record Anchor(long start, Vec3 at) {
	}

	private static ModelPart root;
	private static final ModelPart[] ARM = new ModelPart[4], UPPER_RODS = new ModelPart[4], FORE_PART = new ModelPart[4], FORE_RODS = new ModelPart[4],
		CLAW = new ModelPart[4], PRONG_A = new ModelPart[4], PRONG_B = new ModelPart[4], PRONG_C = new ModelPart[4];

	private RigOctoClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RigOctoClient::move);
	}

	private static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition back = mesh.getRoot();
		CubeDeformation none = new CubeDeformation(0.0F);
		for (int i = 0; i < 4; i++) {
			PartDefinition arm = back.addOrReplaceChild("arm_" + i, CubeListBuilder.create()
				// the mount on the spine, then the shoulder strut's sleeve
				.texOffs(0, 24).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 2.0F, none)
				.texOffs(0, 0).addBox(-1.0F, -1.0F, 1.0F, 2.0F, 2.0F, 9.0F, none),
				PartPose.offset(MOUNT[i][0], MOUNT[i][1], MOUNT[i][2]));
			// the rod that slides out of it
			arm.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(24, 0).addBox(-0.6F, -0.6F, 0.0F, 1.2F, 1.2F, 9.0F, none),
				PartPose.offset(0.0F, 0.0F, 1.0F));
			PartDefinition fore = arm.addOrReplaceChild("fore", CubeListBuilder.create()
				// the elbow joint (lit), then the forearm's sleeve
				.texOffs(12, 24).addBox(-1.25F, -1.25F, -1.25F, 2.5F, 2.5F, 2.5F, none)
				.texOffs(0, 12).addBox(-0.75F, -0.75F, 1.0F, 1.5F, 1.5F, 8.0F, none),
				PartPose.offset(0.0F, 0.0F, UPPER));
			fore.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(24, 12).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F, none),
				PartPose.offset(0.0F, 0.0F, 1.0F));
			PartDefinition claw = fore.addOrReplaceChild("claw", CubeListBuilder.create()
				.texOffs(22, 24).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 1.5F, none), PartPose.offset(0.0F, 0.0F, FORE));
			claw.addOrReplaceChild("prong_a", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(0.0F, -0.7F, 1.2F));
			claw.addOrReplaceChild("prong_b", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(-0.6F, 0.5F, 1.2F));
			claw.addOrReplaceChild("prong_c", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(0.6F, 0.5F, 1.2F));
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static void bake() {
		if (root != null) {
			return;
		}
		root = mesh().bakeRoot();
		for (int i = 0; i < 4; i++) {
			ARM[i] = root.getChild("arm_" + i);
			UPPER_RODS[i] = ARM[i].getChild("rod");
			FORE_PART[i] = ARM[i].getChild("fore");
			FORE_RODS[i] = FORE_PART[i].getChild("rod");
			CLAW[i] = FORE_PART[i].getChild("claw");
			PRONG_A[i] = CLAW[i].getChild("prong_a");
			PRONG_B[i] = CLAW[i].getChild("prong_b");
			PRONG_C[i] = CLAW[i].getChild("prong_c");
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

	/**
	 * Arm {@code i} posed to put its claw's tips on {@code tip} (body model space, pixels: +X the wearer's left, +Y down, +Z
	 * back): aimed at it from its mount, the rods run out as far as it takes (up to {@code maxReach}) to leave the elbow a
	 * little bent, and the elbow raised in the arm's own upright plane so the knee juts up, the way a leg's does.
	 */
	static void reach(float[] t, int i, Vec3 tip, float open, float maxReach) {
		double dx = tip.x - MOUNT[i][0], dy = tip.y - MOUNT[i][1], dz = tip.z - MOUNT[i][2];
		double far = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (far < 1.0E-3) {
			return;
		}
		double pitch = Math.asin(Mth.clamp(-dy / far, -1.0, 1.0));
		double yaw = Math.atan2(dx, dz);
		// how far out the rods run: enough for the arm to reach with the elbow a little bent
		double ext = Mth.clamp((far / 0.92 - (UPPER + FORE + CLAW_TIP)) / (UPPER_ROD + FORE_ROD) + 1.0, 1.0, maxReach);
		double a = UPPER + UPPER_ROD * (ext - 1.0);
		double b = FORE + FORE_ROD * (ext - 1.0) + CLAW_TIP;
		double atShoulder = 0.0, atTip = 0.0;
		if (far < a + b) {
			atShoulder = Math.acos(Mth.clamp((a * a + far * far - b * b) / (2.0 * a * far), -1.0, 1.0));
			atTip = Math.acos(Mth.clamp((b * b + far * far - a * a) / (2.0 * b * far), -1.0, 1.0));
		}
		int o = i * VALUES;
		t[o] = (float)(pitch + atShoulder);
		t[o + 1] = (float)yaw;
		t[o + 2] = (float)-(atShoulder + atTip);
		t[o + 3] = (float)ext;
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

	/** Arm {@code i} part of the way ({@code k}) from {@code from}'s pose to {@code to}'s, into {@code t}. */
	private static void blend(float[] t, int i, float[] from, float[] to, float k) {
		int o = i * VALUES;
		for (int j = 0; j < VALUES; j++) {
			t[o + j] = Mth.lerp(k, from[o + j], to[o + j]);
		}
	}

	/** A point in the world, in the body's model space (pixels: +X the wearer's left, +Y down from the neck, +Z back). */
	private static Vec3 model(Player player, float partial, Vec3 world) {
		double yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 left = new Vec3(ahead.z, 0.0, -ahead.x);
		float scale = player.getScale();
		Vec3 neck = player.getPosition(partial).add(0.0, 24.0 / 16.0 * scale, 0.0);
		Vec3 c = world.subtract(neck);
		double px = 16.0 / scale;
		return new Vec3(c.dot(left) * px, -c.y * px, -c.dot(ahead) * px);
	}

	/** Where a punch lands: the first living thing down the look within the combo's reach, else a little way out. */
	private static Vec3 punchAt(Player player, float partial) {
		Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
		Vec3 end = eye.add(look.scale(RigOcto.COMBO_REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, RigOcto.COMBO_REACH * RigOcto.COMBO_REACH);
		return hit != null ? hit.getLocation() : eye.add(look.scale(PUNCH_EMPTY));
	}

	/** Where an anchor bites: the block or creature down the look, else as far as it reaches. */
	private static Vec3 anchorAt(Player player, float partial) {
		Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
		Vec3 end = eye.add(look.scale(RigOcto.ANCHOR_RANGE));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && e != player, eye.distanceToSqr(end));
		return hit != null ? hit.getEntity().getBoundingBox().getCenter() : end;
	}

	/** What the arms want to look like on {@code player} now; returns whether a claw is placed somewhere in the world. */
	private static boolean target(Player player, float partial, float[] t, float now) {
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
		boolean placed = false;
		// walking or climbing: the claws on whatever they planted on (the upper pair stays free while a move needs it)
		Vec3[] claws = RigOctoWalk.claws(player, partial, held >= 0 || kind != 0 && kind != RigOcto.MOVE_WATCH);
		if (claws != null) {
			for (int i = 0; i < 4; i++) {
				if (claws[i] != null) {
					reach(t, i, model(player, partial, claws[i]), 0.15F, EXT_WALK);
					placed = true;
				}
			}
		}
		float[] base = t.clone();
		Entity hold = held >= 0 ? player.level().getEntity(held) : null;
		if (hold != null) {
			// the upper claws on either side of it, telescoping out to it wherever it is
			boolean reaching = kind == RigOcto.MOVE_GRAB && k < 0.5F;
			Vec3 at = model(player, partial, hold.getBoundingBox().getCenter());
			double side = (hold.getBbWidth() * 0.5 + 0.1) * 16.0 / player.getScale();
			reach(t, 0, at.add(-side, 0.0, 0.0), reaching ? 0.9F : 0.0F, EXT_FAR);
			reach(t, 1, at.add(side, 0.0, 0.0), reaching ? 0.9F : 0.0F, EXT_FAR);
			placed = true;
		} else if (held >= 0 || kind == RigOcto.MOVE_GRAB || kind == RigOcto.MOVE_HOLD) {
			boolean reaching = kind == RigOcto.MOVE_GRAB && k < 0.5F;
			forward(t, 0, reaching ? 1.6F : 1.15F, reaching ? 0.9F : 0.0F);
			forward(t, 1, reaching ? 1.6F : 1.15F, reaching ? 0.9F : 0.0F);
		}
		if (kind != RigOcto.MOVE_ANCHOR) {
			ANCHORS.remove(player.getUUID());
		}
		switch (kind) {
			case RigOcto.MOVE_HURL -> {
				// snapped forward, claws open, the rods run out with the throw
				forward(t, 0, 1.8F, 1.0F);
				forward(t, 1, 1.8F, 1.0F);
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
					set(t, i, i < 2 ? 0.15F : -0.15F, i < 2 ? 1.2F : 2.0F, 0.0F, 1.4F, 0.8F);
				}
			}
			case RigOcto.MOVE_VAULT -> {
				// the lower pair planted behind and below, pushing
				set(t, 2, -1.15F, 0.3F, 0.0F, 1.6F, 0.6F);
				set(t, 3, -1.15F, 0.3F, 0.0F, 1.6F, 0.6F);
			}
			case RigOcto.MOVE_DIVE -> {
				// all four point down at what's below
				for (int i = 0; i < 4; i++) {
					set(t, i, -1.5F, i < 2 ? 2.7F : 2.3F, 0.0F, 1.6F, 0.0F);
				}
			}
			case RigOcto.MOVE_ANCHOR -> {
				// the upper right arm telescopes out to where it bit and stays on it while it hauls
				Anchor anchor = ANCHORS.get(player.getUUID());
				if (anchor == null || anchor.start() != move.start()) {
					anchor = new Anchor(move.start(), anchorAt(player, partial));
					ANCHORS.put(player.getUUID(), anchor);
				}
				float[] bit = t.clone();
				reach(bit, 0, model(player, partial, anchor.at()), 0.0F, EXT_FAR);
				blend(t, 0, base, bit, Math.min(1.0F, k / 0.25F));
				placed = true;
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
			case RigOcto.MOVE_JAB, RigOcto.MOVE_CROSS -> {
				// a telescoping punch out to the first thing down the look, and back: the right arm jabs, the left crosses
				int arm = kind == RigOcto.MOVE_JAB ? 0 : 1;
				float[] out = t.clone();
				reach(out, arm, model(player, partial, punchAt(player, partial)), 0.0F, EXT_FAR);
				blend(t, arm, base, out, punch(k));
				// the other arm guards, drawn in close over the shoulder
				forward(t, 1 - arm, 0.8F, 0.0F);
				placed = true;
			}
			case RigOcto.MOVE_BLAST -> {
				// all four claws close on one point ahead of the chest, open, and tremble as it builds
				Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
				Vec3 focus = model(player, partial, eye.add(look.scale(1.6)).subtract(0.0, 0.3, 0.0));
				float shake = k < 0.5F ? Mth.sin(now * 3.1F) * 0.35F : 0.0F;
				float[] out = t.clone();
				for (int i = 0; i < 4; i++) {
					double sx = (i % 2 == 0 ? -1.2 : 1.2) + shake, sy = i < 2 ? -1.0 : 1.0;
					reach(out, i, focus.add(sx, sy, 0.0), k < 0.5F ? 1.0F : 0.5F, EXT_FAR);
					blend(t, i, base, out, Math.min(1.0F, k / 0.2F));
				}
				placed = true;
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
		return placed;
	}

	/** Out and back over a move (0..1..0). */
	private static float bump(float k) {
		return k < 0.35F ? k / 0.35F : 1.0F - (k - 0.35F) / 0.65F;
	}

	/** A punch: out fast, held a moment, back (0..1..0). */
	private static float punch(float k) {
		return k < 0.25F ? k / 0.25F : k < 0.5F ? 1.0F : 1.0F - (k - 0.5F) / 0.5F;
	}

	// ---- render -----------------------------------------------------------------------------------------------------------

	/** (From {@link RigSuit#extract}) what the arms are doing on {@code player} this frame. */
	static void extract(Player player, AvatarRenderState state, float partialTicks) {
		UUID id = player.getUUID();
		if (!RigOcto.wears(player)) {
			state.setData(ARMS, null);
			POSE.remove(id);
			UNFOLD.remove(id);
			ANCHORS.remove(id);
			return;
		}
		float now = player.level().getGameTime() + partialTicks;
		boolean out = RigOcto.out(player);
		float unfold = UNFOLD.getOrDefault(id, 0.0F);
		unfold += ((out ? 1.0F : 0.0F) - unfold) * UNFOLD_EASE;
		UNFOLD.put(id, unfold);
		float[] want = new float[4 * VALUES];
		float[] fold = new float[4 * VALUES];
		boolean placed = false;
		if (out) {
			placed = target(player, partialTicks, want, now);
		} else {
			rest(want, now);
		}
		folded(fold);
		for (int i = 0; i < want.length; i++) {
			want[i] = Mth.lerp(unfold, fold[i], want[i]);
		}
		float[] pose = POSE.get(id);
		if (pose == null) {
			pose = want.clone();
			POSE.put(id, pose);
		} else {
			float ease = placed ? EASE_PLACED : EASE;
			for (int i = 0; i < pose.length; i++) {
				pose[i] += (want[i] - pose[i]) * ease;
			}
		}
		float[] data = new float[4 * VALUES + 2];
		System.arraycopy(pose, 0, data, 0, pose.length);
		RigMoves.Move move = RigMoves.get(player);
		boolean spinning = move != null && move.kind() == RigOcto.MOVE_PINWHEEL && now - move.start() < RigOcto.PINWHEEL_TICKS;
		data[4 * VALUES] = spinning ? (now - move.start()) * SPIN_RATE : 0.0F;
		data[4 * VALUES + 1] = now;
		state.setData(ARMS, data);
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
			float reach = p[o + 3];
			// short of full length the whole arm draws in (folded); past it the rods slide out of both struts
			arm.zScale = Mth.clamp(reach, 0.2F, 1.0F);
			float ext = Math.max(1.0F, reach);
			UPPER_RODS[i].zScale = ext;
			FORE_PART[i].z = UPPER + UPPER_ROD * (ext - 1.0F);
			FORE_PART[i].xRot = p[o + 2];
			FORE_RODS[i].zScale = ext;
			CLAW[i].z = FORE + FORE_ROD * (ext - 1.0F);
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

	// ---- the climb and the walk ---------------------------------------------------------------------------------------------

	/** On the wearer's own client: the arms carry you, up a wall or along on the arm walk. */
	private static void move(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
			|| player.isFallFlying() || !RigOcto.out(player) || player.isInWater()) {
			return;
		}
		if (player.horizontalCollision) {
			climb(player);
		} else if (RigOcto.walking(player)) {
			stride(player);
		}
	}

	/** Against a wall, the arms grip it. Forward or Jump climbs, Sneak holds still. */
	private static void climb(LocalPlayer player) {
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

	/** On the arm walk: held up between the arms and walked along (the symbiote's stride; see {@link #STRIDE_HEIGHT}). */
	private static void stride(LocalPlayer player) {
		Input input = player.input.keyPresses;
		if (input.shift() && player.onGround()) {
			// on your own feet
			return;
		}
		double scale = player.getScale();
		int anchors = RigOctoWalk.anchors(player, RigOctoWalk.STRIDE_REACH * scale);
		if (anchors < 2) {
			// nothing to grab: you fall
			return;
		}
		double ground = RigOctoWalk.groundBelow(player, RigOctoWalk.STRIDE_GROUND * scale);
		Vec3 move = wish(player, input);
		double vy;
		if (input.jump()) {
			vy = anchors >= STRIDE_RISE_ANCHORS ? STRIDE_CLIMB : 0.0;
		} else if (input.shift()) {
			vy = -STRIDE_CLIMB;
		} else if (ground >= 0.0) {
			// held up on them, rising and dipping a touch with each stride
			double bob = move.lengthSqr() > 0.0 ? Mth.sin(player.tickCount * 0.45F) * 0.06 : 0.0;
			vy = Mth.clamp((STRIDE_HEIGHT * scale + bob - ground) * STRIDE_LIFT, -STRIDE_CLIMB, STRIDE_CLIMB);
		} else {
			// hanging between whatever they hold
			vy = 0.0;
		}
		Vec3 want = move.scale(player.isSprinting() ? STRIDE_SPRINT : STRIDE_SPEED);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(Mth.lerp(STRIDE_GRIP, v.x, want.x), vy, Mth.lerp(STRIDE_GRIP, v.z, want.z));
		player.resetFallDistance();
	}

	/** The way the movement keys point, flat. */
	private static Vec3 wish(LocalPlayer player, Input input) {
		double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
		double strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
		if (forward == 0 && strafe == 0) {
			return Vec3.ZERO;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		return new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
	}
}
