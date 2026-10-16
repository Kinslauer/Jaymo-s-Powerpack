package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebMorph;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebTendrilCombos;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Living Armor's tendrils: thick black cords growing out of the wearer's back and shoulders, arching up and out and
 * swaying on their own. The longer the armour is up, the thicker and longer they get (to {@link #GROWN_TICKS}); every
 * swing of the wearer's arm, they whip forward over the shoulders. Everyone sees them.
 *
 * <p>A strike is a slam (reared high over the head, then crashing straight down onto the target) or a swipe (drawn out
 * wide to one side, then swept across through the target), as the server picked it (see {@link WebSymbiote.Strike});
 * swinging held debris it's slower and reaches much further. In first person (your own body drawn by First-person
 * Model, or {@link #submitFirstPerson} next to the hands without it) they reach forward over the shoulders into view.
 * On the move they stay supple, streaming back and rippling; only the walkers planted on a wall are rigid limbs.
 *
 * <p>The weapon on left click ({@link WebMorph}) forms here while Living Armor is up: the striking side's shoulder
 * tendril (braided with the one inside it, for the mace and the hammer-fist) rears back and coils while the symbiote
 * gathers, its tip swelling into the weapon head ({@link #heads}, the same heads as the arm's), then the strike lashes
 * through it: a wind-up of its own for each weapon, the lash rolling out root first with the tip trailing, a ripple
 * back up it at the impact, and back to rest ({@code TendrilModel.morph}).
 */
final class WebTendrilLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_tendril.png");
	/** Ticks of Living Armor until the tendrils are fully grown. */
	static final float GROWN_TICKS = 80.0F;
	/** Segments per tendril, and each one's length (model pixels). */
	private static final int SEGMENTS = 7;
	private static final float SEGMENT_LENGTH = 4.5F;
	/** The last segments' thickness (model pixels; the rest are 3): each tendril tapers to a point. */
	private static final float[] TIP = {2.5F, 1.9F, 1.2F};
	/** Where each tendril roots (model space: +Y down, +Z back), its outward lean and its upward tilt (radians). */
	private static final float[][] ROOTS = {
		{-3.0F, 1.5F, 2.0F, -0.6F, -1.0F},
		{3.0F, 1.5F, 2.0F, 0.6F, -1.0F},
		{-1.5F, 0.5F, 2.0F, -0.22F, -1.35F},
		{1.5F, 0.5F, 2.0F, 0.22F, -1.35F},
		{-3.5F, 4.5F, 2.0F, -1.0F, -0.55F},
		{3.5F, 4.5F, 2.0F, 1.0F, -0.55F},
		{-2.0F, 8.0F, 2.0F, -1.25F, -0.15F},
		{2.0F, 8.0F, 2.0F, 1.25F, -0.15F},
		// the spares: grown only while four of the others walk you along, to stand in for them
		{-2.5F, 3.0F, 2.5F, -0.8F, -0.8F},
		{2.5F, 3.0F, 2.5F, 0.8F, -0.8F},
		{-1.0F, 5.5F, 2.5F, -0.45F, -1.1F},
		{1.0F, 5.5F, 2.5F, 0.45F, -1.1F},
	};
	/** The first of the spares (see {@link #ROOTS}): hidden unless the tendrils are walking you. */
	static final int FIRST_SPARE = 8;
	/** The barrage's stabbers (Andy): the outermost pair, left and right by turns; walking, the outer spares. */
	private static final int[] BARRAGE_FREE = {4, 5}, BARRAGE_FREE_WALKING = {8, 9};
	/** Roots higher than this (model y) are the upper ones: the strikers, and the ones seen in first person. */
	private static final float UPPER_ROOT_Y = 6.0F;
	/** A whole tendril's length at its rest size (model pixels): the segments overlap a little. */
	private static final float CHAIN = (SEGMENTS - 1) * (SEGMENT_LENGTH - 0.4F) + SEGMENT_LENGTH;

	// ---- tethers (hung off a wall) ----
	/** The first tendril on a tether (the two above it, the shoulders', stay free for R and G); each reaches its spot in this long. */
	static final int FIRST_TETHER = 2;
	private static final float TETHER_REACH_TICKS = 3.0F;
	/** A tether's thickness (of a free one's), and how it thrums under the strain. */
	private static final float TETHER_THICK = 0.8F, TETHER_THRUM = 0.012F;

	// ---- the roar ----
	/** Flailing: how wild (radians of yaw and pitch) and how fast; flung out at the roar: how wide, and how much longer. */
	private static final float FLAIL_YAW = 0.9F, FLAIL_PITCH = 0.85F, FLAIL_SPEED = 0.9F, FLING_WIDE = 2.0F, FLING_STRETCH = 0.55F;
	/** They're flung out over this share of the roar, just before it lands. */
	private static final float FLING_LEAD = 0.1F;

	// ---- strikes ----
	/** A slam: reared to this elevation (radians; past straight up, so it's over the head), and it comes down this far below the target. */
	private static final float SLAM_HIGH = 1.95F, SLAM_DIP = 0.25F;
	/** A swipe: drawn out this far to its side (radians of yaw past the target), a little above it. */
	private static final float SWIPE_WIDE = 1.4F, SWIPE_LIFT = 0.15F;
	/** After the hit it follows through this much further (share of the swing), then eases back to rest. */
	private static final float FOLLOW_THROUGH = 0.35F;
	/** Each segment's curl: reared (back over), striking (whipping down), and a swipe's trailing bend. */
	private static final float CURL_REARED = 0.15F, CURL_STRIKE = -0.12F, CURL_SWIPE = 0.1F;
	/** How much longer a striking tendril gets: a plain lash, swinging debris, and extra from your own eyes. */
	private static final float STRETCH = 0.35F, STRETCH_HEAVY = 0.9F, STRETCH_FIRST_PERSON = 0.4F;
	/** Feral (a young bond): extra sway speed, how hard a sudden jerk throws a tendril, and how much the bulge swells. */
	private static final float FERAL_SWAY = 0.08F, FERAL_JERK = 0.9F, FERAL_PULSE = 0.06F;
	/**
	 * Supple while you move: they stream back behind you (lower by {@code DRAG_LOWER} of their tilt, drawn in toward the
	 * middle by {@code DRAG_NARROW} of their lean, uncurling to {@code DRAG_CURL}), the ripple rolls out along them faster
	 * and harder ({@code DRAG_WAVE_SPEED}, {@code DRAG_WAVE}) and they slither side to side with your stride
	 * ({@code DRAG_SLITHER}). All of it at this walk speed. Only a tendril planted on a wall (climbing) goes rigid.
	 */
	private static final float DRAG_SPEED = 0.4F, DRAG_LOWER = 0.55F, DRAG_NARROW = 0.35F, DRAG_CURL = 0.1F;
	private static final float DRAG_WAVE_SPEED = 0.3F, DRAG_WAVE = 0.6F, DRAG_SLITHER = 0.16F;
	/**
	 * Barrage: every free tendril stabs the pinned creature in turn, each on its own beat. Drawn back (this much shorter,
	 * cocked this far up and out) until {@code STAB_COCK} of its cycle, thrust in (this much longer) by {@code STAB_HIT}.
	 */
	private static final float STAB_COCK = 0.45F, STAB_HIT = 0.6F, STAB_SHORT = 0.6F, STAB_LONG = 1.25F, STAB_UP = 0.5F, STAB_OUT = 0.45F;
	/** Barrage: how far a tendril waiting its turn is drawn back (0-1 of a full cock). */
	private static final float STAB_POISED = 0.4F;
	/**
	 * Barrage blade tips (model pixels): the spear's socket and point lengths and half-width; the hooked blade's length and
	 * how far it curves over.
	 */
	private static final float BLADE_SOCKET = 2.5F, BLADE_POINT = 8.0F, BLADE_WIDTH = 2.0F, BLADE_HOOK = 9.0F, BLADE_CURVE = 3.5F;
	/** The bulge (thickness pulse) every tendril has, wild or not. */
	private static final float PULSE = 0.04F;
	/** Combos: a clap's tendrils hauled out this wide each side (yaw) and this much up; a shatter's reared this far apart. */
	private static final float CLAP_WIDE = 1.5F, CLAP_LIFT = 0.25F, SHATTER_SPREAD = 0.6F;

	// ---- first person ----
	/** Your own upper tendrils: out to the side this far (yaw), rising this steeply, curling forward and inward into view. */
	private static final float FP_YAW = 1.1F, FP_PITCH = 0.95F, FP_CURL_DOWN = -0.22F, FP_CURL_IN = 0.12F;
	/** They reach this much further than in third person, and sway this much at the edges of the screen. */
	private static final float FP_LENGTH = 1.35F, FP_SWAY = 0.12F;
	/** Without First-person Model: where they root (camera space: right, up, back, in blocks) and how big they're drawn. */
	private static final float FP_ROOT_UP = -0.28F, FP_ROOT_BACK = 0.05F, FP_SCALE = 0.6F;

	// ---- the weapon tendril (WebMorph with Living Armor up) ----
	/**
	 * Gathering, it rears into a coil: out this far to its side (yaw), raised this high (radians), each segment curled
	 * over this much, drawn in to this share of its length, shuddering this much until the head has formed.
	 */
	private static final float MORPH_READY_YAW = 1.0F, MORPH_READY_PITCH = 1.2F, MORPH_READY_CURL = 0.32F, MORPH_READY_LENGTH = 0.75F;
	private static final float MORPH_SHUDDER = 0.07F;
	/** It's this much thicker than the others while it carries the weapon; the head is drawn this big; the tip swells this much under it. */
	private static final float MORPH_THICK = 1.2F, MORPH_HEAD_SCALE = 1.1F, MORPH_TIP_SWELL = 0.6F;
	/** The lash rolls out root first: the tip trails behind by this much curl (a whip-blade's far more). */
	private static final float MORPH_LAG = 0.35F, MORPH_WHIP_LAG = 0.9F;
	/** At the impact a ripple runs back up it (curl), dying away over this many ticks. */
	private static final float MORPH_RIPPLE = 0.3F, MORPH_RIPPLE_TICKS = 5.0F;
	/** It eases back to rest over the strike's last ticks; the hook claw reels in over these after it bites. */
	private static final float MORPH_RETURN_TICKS = 5.0F, MORPH_REEL_TICKS = 4.0F;
	/** The spear's wind-up: coiled like a spring (alternate segments bent this far each way), drawn in short. */
	private static final float MORPH_SPRING = 0.45F;
	/** Wind-ups: the hammer-fist dropped this low (radians below level); the whip-blade thrown this far back over the shoulder. */
	private static final float MORPH_HAMMER_LOW = 1.0F, MORPH_WHIP_BACK = 2.6F;
	/** Braided (mace, hammer-fist): the two twist round each other this much (radians a segment). */
	private static final float MORPH_BRAID = 0.16F;
	/** It never stretches further than this (blocks, model space). */
	private static final float MORPH_MAX_REACH = 10.5F;

	private final TendrilModel model;
	private static TendrilModel firstPersonModel;
	/** The same tendrils on the mobs the symbiote rides ({@link #submitOnMob}). */
	private static TendrilModel mobModel;

	WebTendrilLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.model = new TendrilModel(createLayer().bakeRoot());
	}

	private static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int t = 0; t < ROOTS.length; t++) {
			float[] r = ROOTS[t];
			PartDefinition parent = root.addOrReplaceChild("tendril" + t, CubeListBuilder.create().texOffs(0, 0)
				.addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, SEGMENT_LENGTH), PartPose.offset(r[0], r[1], r[2]));
			for (int k = 1; k < SEGMENTS; k++) {
				// the last few narrow to a point, like the Spike Burst's spikes
				float thick = k < SEGMENTS - TIP.length ? 3.0F : TIP[k - (SEGMENTS - TIP.length)];
				parent = parent.addOrReplaceChild("s" + k, CubeListBuilder.create().texOffs(0, (k % 2) * 8)
					.addBox(-thick / 2.0F, -thick / 2.0F, 0.0F, thick, thick, SEGMENT_LENGTH), PartPose.offset(0.0F, 0.0F, SEGMENT_LENGTH - 0.4F));
			}
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
		if (tendrils == null || state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		// rooted to the torso, so they lean with a crouch (or the hunch)
		WebFeral.applyModel(this.getParentModel(), state);
		this.getParentModel().body.translateAndRotate(poseStack);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		if (tendrils.combo() == WebTendrilCombos.BARRAGE) {
			blades(this.model, state, poseStack, collector, state.lightCoords);
		}
		heads(this.model, state, poseStack, collector, state.lightCoords);
		poseStack.popPose();
	}

	/**
	 * The weapon on a tendril ({@link WebMorph}): the head at the weapon tendril's tip (the pose stack in body space),
	 * growing as it gathers, melting back at the end. The same heads as the weapon arm's ({@link WebMorphLayer#weapon}).
	 */
	private static void heads(TendrilModel model, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		WebMorphLayer.Strike strike = state.getData(WebMorphLayer.KEY);
		WebMorphLayer.Gather gather = state.getData(WebMorphLayer.GATHER);
		if (!(strike != null && strike.tendril() || strike == null && gather != null && gather.tendril())) {
			return;
		}
		model.setupAnim(state);
		int t = model.weaponTendril;
		if (t < 0 || model.weaponGrow < 0.05F || !model.parts[t][0].visible) {
			return;
		}
		PoseStack chain = new PoseStack();
		model.root().translateAndRotate(chain);
		for (int k = 0; k < SEGMENTS; k++) {
			model.parts[t][k].translateAndRotate(chain);
		}
		org.joml.Matrix4f m = chain.last().pose();
		org.joml.Vector3f end = m.transformPosition(new org.joml.Vector3f(0.0F, 0.0F, (SEGMENT_LENGTH - 0.6F) / 16.0F));
		org.joml.Vector3f dir = m.transformDirection(new org.joml.Vector3f(0.0F, 0.0F, 1.0F)).normalize();
		net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(end.x, end.y, end.z).scale(16.0);
		net.minecraft.world.phys.Vec3 way = new net.minecraft.world.phys.Vec3(dir.x, dir.y, dir.z);
		WebMorphLayer.Strike s = model.weaponStrike;
		float grow = model.weaponGrow * MORPH_HEAD_SCALE;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE),
			(pose, buffer) -> WebMorphLayer.weapon(buffer, pose, s, at, way, grow, light));
	}

	/**
	 * Barrage: each stabbing tendril's tip hardens into a blade (the pose stack in body space). Turn about, a long spear
	 * point with barbs swept back, or a hooked blade curving over like a claw, its inner edge pale.
	 */
	private static void blades(TendrilModel model, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		model.setupAnim(state);
		java.util.List<net.minecraft.world.phys.Vec3[]> tips = new java.util.ArrayList<>();
		java.util.List<Integer> kinds = new java.util.ArrayList<>();
		for (int t = 0; t < ROOTS.length; t++) {
			if (!model.stabbing[t] || !model.parts[t][0].visible) {
				continue;
			}
			// walk the chain to its tip (in body space, blocks): where it ends, the way it points, and its "up"
			PoseStack chain = new PoseStack();
			model.root().translateAndRotate(chain);
			for (int k = 0; k < SEGMENTS; k++) {
				model.parts[t][k].translateAndRotate(chain);
			}
			org.joml.Matrix4f m = chain.last().pose();
			org.joml.Vector3f end = m.transformPosition(new org.joml.Vector3f(0.0F, 0.0F, (SEGMENT_LENGTH - 0.6F) / 16.0F));
			org.joml.Vector3f dir = m.transformDirection(new org.joml.Vector3f(0.0F, 0.0F, 1.0F)).normalize();
			org.joml.Vector3f up = m.transformDirection(new org.joml.Vector3f(0.0F, -1.0F, 0.0F)).normalize();
			tips.add(new net.minecraft.world.phys.Vec3[] {new net.minecraft.world.phys.Vec3(end.x, end.y, end.z) .scale(16.0),
				new net.minecraft.world.phys.Vec3(dir.x, dir.y, dir.z), new net.minecraft.world.phys.Vec3(up.x, up.y, up.z)});
			kinds.add(t % 2);
		}
		if (tips.isEmpty()) {
			return;
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
			for (int i = 0; i < tips.size(); i++) {
				net.minecraft.world.phys.Vec3[] tip = tips.get(i);
				blade(buffer, pose, tip[0], tip[1], tip[2], kinds.get(i), light);
			}
		});
	}

	/** One blade tip at {@code at} (pixels), pointing along {@code dir}, {@code up} across it: 0 a spear point, 1 a hooked blade. */
	private static void blade(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, net.minecraft.world.phys.Vec3 at,
		net.minecraft.world.phys.Vec3 dir, net.minecraft.world.phys.Vec3 up, int kind, int light) {
		net.minecraft.world.phys.Vec3 across = dir.cross(up);
		across = across.lengthSqr() < 1.0E-6 ? new net.minecraft.world.phys.Vec3(1.0, 0.0, 0.0) : across.normalize();
		up = across.cross(dir).normalize();
		if (kind == 0) {
			// the spear: a socket swelling into a long leaf of a point, two barbs swept back from its base
			net.minecraft.world.phys.Vec3 belly = at.add(dir.scale(BLADE_SOCKET));
			net.minecraft.world.phys.Vec3 tip = belly.add(dir.scale(BLADE_POINT));
			WebTubes.prism(buffer, pose, px(at), px(belly), up, 0.8 / 16.0, 0.7 / 16.0, BLADE_WIDTH / 16.0, 0.75 / 16.0, WebTubes.SHEEN, light);
			WebTubes.prism(buffer, pose, px(belly), px(tip), up, BLADE_WIDTH / 16.0, 0.75 / 16.0, 0.03 / 16.0, 0.03 / 16.0, WebTubes.BLACK, light);
			for (int side = -1; side <= 1; side += 2) {
				net.minecraft.world.phys.Vec3 e = up.scale(side * BLADE_WIDTH * 0.85);
				WebTubes.prism(buffer, pose, px(belly.add(e)), px(tip), up, 0.3 / 16.0, 0.3 / 16.0, 0.03 / 16.0, 0.03 / 16.0, WebTubes.BONE, light);
				net.minecraft.world.phys.Vec3 barb = at.add(dir.scale(BLADE_SOCKET * 0.4)).add(up.scale(side * 0.6));
				WebTubes.cord(buffer, pose, px(barb), px(barb.add(up.scale(side * 2.2)).add(dir.scale(-2.4))), 0.5 / 16.0, 0.03 / 16.0, WebTubes.BONE,
					light);
			}
		} else {
			// the hook: a flat blade curving up and over like a claw, broad at the root, its inner (underside) edge pale
			int pieces = 6;
			net.minecraft.world.phys.Vec3 prev = at;
			for (int i = 1; i <= pieces; i++) {
				double f = i / (double)pieces;
				net.minecraft.world.phys.Vec3 p = at.add(dir.scale(BLADE_HOOK * f)).add(up.scale(BLADE_CURVE * f * f));
				double w0 = BLADE_WIDTH * (1.0 - 0.9 * (f - 1.0 / pieces)), w1 = BLADE_WIDTH * (1.0 - 0.9 * f) + 0.03;
				WebTubes.prism(buffer, pose, px(prev), px(p), up, w0 / 16.0, 0.4 / 16.0, w1 / 16.0, 0.3 / 16.0, WebTubes.SHEEN, light);
				// the edge, along the inside of the curve
				net.minecraft.world.phys.Vec3 e0 = prev.add(up.scale(-w0 * 0.85)), e1 = p.add(up.scale(-w1 * 0.85));
				WebTubes.prism(buffer, pose, px(e0), px(e1), up, 0.35 / 16.0, 0.25 / 16.0, 0.2 / 16.0, 0.15 / 16.0, WebTubes.BONE, light);
				prev = p;
			}
		}
	}

	private static net.minecraft.world.phys.Vec3 px(net.minecraft.world.phys.Vec3 v) {
		return v.scale(1.0 / 16.0);
	}

	/**
	 * Without First-person Model: your own tendrils drawn from your eyes, after the hands (camera space), rising from
	 * behind your shoulders and curling in at the edges of the screen; strikes and grabs reach out in front.
	 */
	static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState avatar) {
		WebSymbioteClient.Tendrils tendrils = avatar.getData(WebSymbioteClient.TENDRILS);
		if (tendrils == null || avatar.isInvisible || FirstPersonCompat.bodyShown()) {
			return;
		}
		if (firstPersonModel == null) {
			firstPersonModel = new TendrilModel(createLayer().bakeRoot());
		}
		// the strike's aim is worked out against the body; here everything is against the view
		float yaw = tendrils.aimYaw() - avatar.yRot * Mth.DEG_TO_RAD;
		float pitch = tendrils.aimPitch() + avatar.xRot * Mth.DEG_TO_RAD;
		float wallYaw = tendrils.wallYaw() - avatar.yRot * Mth.DEG_TO_RAD;
		float wallPitch = tendrils.wallPitch() + avatar.xRot * Mth.DEG_TO_RAD;
		AvatarRenderState own = new AvatarRenderState();
		own.setData(WebSymbioteClient.TENDRILS, new WebSymbioteClient.Tendrils(tendrils.growth(), tendrils.time(), tendrils.strike(), yaw, pitch,
			yaw >= 0.0F ? -1.0F : 1.0F, 0.0F, tendrils.held(), tendrils.armor(), tendrils.wall(), wallYaw, wallPitch, tendrils.heavy(),
			tendrils.variant(), true, tendrils.combo(), tendrils.rear(), tendrils.hit(), tendrils.feral(), tendrils.roar(), tendrils.tethers(), null));
		own.ageInTicks = avatar.ageInTicks;
		// the weapon on a tendril, against the view too
		WebMorphLayer.Strike strike = avatar.getData(WebMorphLayer.KEY);
		own.setData(WebMorphLayer.KEY, strike == null ? null : new WebMorphLayer.Strike(strike.weapon(), strike.side(), strike.age(),
			strike.aimYaw() - avatar.yRot * Mth.DEG_TO_RAD, strike.aimPitch() - avatar.xRot * Mth.DEG_TO_RAD, strike.reach(), strike.tendril()));
		own.setData(WebMorphLayer.GATHER, avatar.getData(WebMorphLayer.GATHER));
		poseStack.pushPose();
		poseStack.translate(0.0F, FP_ROOT_UP, FP_ROOT_BACK);
		// camera space to model space: model -Z is ahead, -Y up, +X the wearer's left
		poseStack.scale(-FP_SCALE, -FP_SCALE, FP_SCALE);
		collector.submitModel(firstPersonModel, own, poseStack, RenderTypes.entityCutout(TEXTURE), avatar.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		heads(firstPersonModel, own, poseStack, collector, avatar.lightCoords);
		poseStack.popPose();
	}

	/**
	 * A mob the symbiote rides (a behemoth, or a mob it inhabits; see WebBehemothClient): the same Living Armor tendrils
	 * out of its back, from the mob's own model space (the pose stack as its render layers get it), rooted
	 * {@code rootY}, {@code rootZ} (model pixels: down from the model's top, back from its middle) and drawn
	 * {@code size} times a player's. Its lashes and slams swing them as a wearer's strikes do.
	 */
	static void submitOnMob(PoseStack poseStack, SubmitNodeCollector collector, int light,
		net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, WebSymbioteClient.Tendrils tendrils, float rootY, float rootZ,
		float size) {
		if (mobModel == null) {
			mobModel = new TendrilModel(createLayer().bakeRoot());
		}
		AvatarRenderState own = new AvatarRenderState();
		own.setData(WebSymbioteClient.TENDRILS, tendrils);
		own.ageInTicks = state.ageInTicks;
		own.walkAnimationPos = state.walkAnimationPos;
		own.walkAnimationSpeed = state.walkAnimationSpeed;
		poseStack.pushPose();
		poseStack.translate(0.0F, rootY / 16.0F, rootZ / 16.0F);
		poseStack.scale(size, size, size);
		collector.submitModel(mobModel, own, poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}

	/**
	 * Hung off a wall by Tendril Grab, drawn the way Wall Crawl's walkers are: each tethered tendril a jointed limb from
	 * its root to its bite (the chord, model space, blocks; reaching out one after another over
	 * {@link #TETHER_REACH_TICKS}), its knee jutting out from the wall. As {@link WebTendrilWalk#legs} lays them out, for
	 * {@link #bend}; the hold itself (the elastic pull back to the bites) is the server's, as before.
	 */
	static float[] tetherLegs(net.minecraft.world.entity.player.Player player, WebSymbiote.Wall wall, float partial) {
		float[] out = new float[ROOTS.length * 6];
		float scale = player.getScale();
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		net.minecraft.world.phys.Vec3 feet = player.getPosition(partial);
		double yaw = bodyYaw * Mth.DEG_TO_RAD;
		net.minecraft.world.phys.Vec3 ahead = new net.minecraft.world.phys.Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		net.minecraft.world.phys.Vec3 left = new net.minecraft.world.phys.Vec3(ahead.z, 0.0, -ahead.x);
		float age = player.level().getGameTime() - wall.landed() + partial;
		net.minecraft.world.phys.Vec3 normal = net.minecraft.core.Direction.from3DDataValue(wall.face()).getUnitVec3();
		net.minecraft.world.phys.Vec3 drift = feet.subtract(wall.rest());
		drift = drift.subtract(normal.scale(drift.dot(normal))).scale(WebSymbiote.TETHER_FOLLOW);
		for (int i = 0; i < wall.points().size() && i + FIRST_TETHER < FIRST_SPARE; i++) {
			int t = i + FIRST_TETHER;
			float[] r = ROOTS[t];
			net.minecraft.world.phys.Vec3 root = feet.add(left.scale(r[0] / 16.0F * scale)).add(0.0, (24.0F - r[1]) / 16.0F * scale, 0.0)
				.subtract(ahead.scale(r[2] / 16.0F * scale));
			float reach = Mth.clamp((age - i) / TETHER_REACH_TICKS, 0.0F, 1.0F);
			// reaching out: from just off the back toward the bite
			net.minecraft.world.phys.Vec3 chord = wall.points().get(i).add(drift).subtract(root).scale(Math.max(0.05F, reach));
			out[t * 6] = (float)chord.dot(left);
			out[t * 6 + 1] = (float)-chord.y;
			out[t * 6 + 2] = (float)-chord.dot(ahead);
			out[t * 6 + 3] = (float)normal.dot(left);
			out[t * 6 + 4] = (float)-normal.y;
			out[t * 6 + 5] = (float)-normal.dot(ahead);
		}
		return out;
	}

	/** Climbing: the walking tendrils' chords and bows (see {@link WebTendrilWalk#legs}), or null when not climbing. */
	static float @org.jspecify.annotations.Nullable [] walk(net.minecraft.world.entity.player.Player player, float partial) {
		return WebTendrilWalk.legs(player, partial, ROOTS);
	}

	/**
	 * A walking tendril as a rigid limb from its root to its planted tip ({@code chord} away, in blocks, model space):
	 * straight to a knee, then straight on to the tip. The knee juts out toward {@code bow} (the surface's outward way)
	 * as far as the limb's fixed length ({@link WebTendrilWalk#LIMB}) needs; only a tip planted further than that
	 * stretches it. Each segment is turned (against the one before it) to lie along its section.
	 */
	private static void bend(ModelPart[] chain, float cx, float cy, float cz, float bx, float by, float bz, float growth) {
		org.joml.Vector3f chord = new org.joml.Vector3f(cx, cy, cz).mul(16.0F);
		float len = chord.length();
		if (len < 1.0E-3F) {
			return;
		}
		org.joml.Vector3f bow = new org.joml.Vector3f(bx, by, bz);
		bow.sub(new org.joml.Vector3f(chord).mul(bow.dot(chord) / (len * len)));
		if (bow.lengthSquared() < 1.0E-6F) {
			bow.set(0.0F, -1.0F, 0.0F);
		}
		bow.normalize();
		float limb = Math.max(WebTendrilWalk.LIMB, len / WebTendrilWalk.STRAIGHTEST);
		// the knee sits where segment KNEE starts
		float upper = limb * WebTendrilWalk.KNEE * (SEGMENT_LENGTH - 0.4F) / CHAIN;
		float lower = limb - upper;
		float along = (upper * upper - lower * lower + len * len) / (2.0F * len);
		float out = Mth.sqrt(Math.max(0.0F, upper * upper - along * along));
		org.joml.Vector3f knee = new org.joml.Vector3f(chord).mul(along / len).add(new org.joml.Vector3f(bow).mul(out));
		// grown out along the limb as the tendrils bud
		float size = limb / CHAIN * Math.max(0.05F, growth);
		org.joml.Quaternionf frame = new org.joml.Quaternionf();
		org.joml.Quaternionf inverse = new org.joml.Quaternionf();
		for (int k = 0; k < SEGMENTS; k++) {
			org.joml.Vector3f dir = k < WebTendrilWalk.KNEE ? new org.joml.Vector3f(knee) : new org.joml.Vector3f(chord).sub(knee);
			if (dir.lengthSquared() < 1.0E-8F) {
				dir.set(0.0F, 0.0F, 1.0F);
			}
			dir.normalize();
			frame.invert(inverse);
			inverse.transform(dir);
			ModelPart part = chain[k];
			part.yRot = (float)Math.atan2(dir.x, dir.z);
			part.xRot = (float)-Math.asin(Mth.clamp(dir.y, -1.0F, 1.0F));
			part.zRot = 0.0F;
			part.xScale = k == 0 ? size : 1.0F;
			part.yScale = k == 0 ? size : 1.0F;
			part.zScale = k == 0 ? size : 1.0F;
			part.visible = true;
			frame.mul(new org.joml.Quaternionf().rotationZYX(0.0F, part.yRot, part.xRot));
		}
	}

	/** Eases 0-1. */
	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/**
	 * The base yaw that points a tendril {@code yaw} radians off straight ahead (+ to the wearer's right, as the strike's
	 * aim is measured), taken the short way round from {@code rest} (a right-hand tendril, rest below 0, doesn't swing
	 * round the left of the body). With it, the base's xRot is simply the elevation (+ up): the cords are built
	 * pointing back (+Z), and model space has +Y down and +X on the wearer's left.
	 */
	private static float aimYaw(float yaw, float rest) {
		float aim = Mth.PI + yaw;
		if (rest < 0.0F && aim > 0.0F) {
			aim -= Mth.TWO_PI;
		}
		return aim;
	}

	/** The tendrils' pose, worked out per wearer at draw time. */
	private static final class TendrilModel extends Model<AvatarRenderState> {
		private final ModelPart[][] parts = new ModelPart[ROOTS.length][SEGMENTS];
		/** Which ones are stabbing in a barrage (they wear blade tips), as last posed. */
		private final boolean[] stabbing = new boolean[ROOTS.length];
		/** The weapon on a tendril, as last posed: the one wearing the head (-1 none), its strike, and how grown the head is. */
		private int weaponTendril = -1;
		private WebMorphLayer.Strike weaponStrike;
		private float weaponGrow;

		TendrilModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			for (int t = 0; t < ROOTS.length; t++) {
				ModelPart part = root.getChild("tendril" + t);
				this.parts[t][0] = part;
				for (int k = 1; k < SEGMENTS; k++) {
					part = part.getChild("s" + k);
					this.parts[t][k] = part;
				}
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			java.util.Arrays.fill(this.stabbing, false);
			WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
			if (tendrils == null) {
				return;
			}
			float growth = tendrils.growth();
			float time = tendrils.time();
			boolean fp = tendrils.firstPerson();
			// they bud from tiny pods on the back and grow out to their full thickness and length
			float eased = growth * growth * (3.0F - 2.0F * growth);
			float thick = Mth.lerp(Mth.sqrt(growth), 0.3F, 1.3F);
			float length = Mth.lerp(eased, 0.04F, 1.3F);

			// the strike's swing (the same for every striker)
			float strike = tendrils.strike();
			boolean heavy = tendrils.heavy();
			int variant = tendrils.variant();
			int combo = tendrils.combo();
			// where it stops rearing and where it lands (a strike's own, a frenzy's quick one, a combo's, a thrash's cycle)
			float rearShare = tendrils.rear();
			float hitShare = tendrils.hit();
			float feral = tendrils.feral();
			float weight = 0.0F, swing = 0.0F;
			if (strike >= 0.0F) {
				if (strike < rearShare) {
					// rearing (or drawing out to the side)
					weight = smooth(strike / rearShare);
				} else if (strike < hitShare) {
					// coming in, faster and faster: lands at the hit
					weight = 1.0F;
					float k = (strike - rearShare) / (hitShare - rearShare);
					swing = k * k;
				} else {
					// following through, then easing back to rest
					float f = (strike - hitShare) / (1.0F - hitShare);
					weight = 1.0F - smooth(f * 1.4F - 0.4F);
					swing = 1.0F + FOLLOW_THROUGH * Math.min(1.0F, f * 3.0F);
				}
			}
			float strikeYaw, strikePitch, curlX, curlY;
			float sweepSide = variant == WebSymbiote.STRIKE_SWIPE_LEFT ? 1.0F : -1.0F;
			if (variant == WebSymbiote.STRIKE_SLAM) {
				strikeYaw = tendrils.aimYaw();
				strikePitch = Mth.lerp(swing, SLAM_HIGH, tendrils.aimPitch() - SLAM_DIP);
				curlX = Mth.lerp(Math.min(1.0F, swing), CURL_REARED, CURL_STRIKE);
				curlY = 0.0F;
			} else {
				strikeYaw = tendrils.aimYaw() - sweepSide * SWIPE_WIDE * (1.0F - swing);
				strikePitch = tendrils.aimPitch() + SWIPE_LIFT;
				curlX = CURL_STRIKE * 0.5F;
				// the cord trails behind the sweep
				curlY = -sweepSide * CURL_SWIPE * (swing > 1.0F ? -1.0F : 1.0F);
			}
			// a slam strikes with the tendrils on the target's side; a swipe with those on the side it sweeps from
			float strikers = variant == WebSymbiote.STRIKE_SLAM ? tendrils.side() : sweepSide;
			float stretch = 1.0F + (heavy ? STRETCH_HEAVY : STRETCH) * weight * Math.min(1.0F, swing) + (fp ? STRETCH_FIRST_PERSON * weight : 0.0F);
			// moving, they stream back behind you, rippling (climbing, the walkers are planted limbs; see bend)
			float drag = fp ? 0.0F : smooth(Mth.clamp(state.walkAnimationSpeed / DRAG_SPEED, 0.0F, 1.0F));

			for (int t = 0; t < ROOTS.length; t++) {
				float[] r = ROOTS[t];
				boolean upper = r[1] < UPPER_ROOT_Y;
				float side = Math.signum(r[0]);
				ModelPart base = this.parts[t][0];
				// the top two (right, left) are the ones that reach out and hold: while holding, they're drawn reaching
				base.visible = !(t == 0 && (tendrils.held() & 1) != 0 || t == 1 && (tendrils.held() & 2) != 0);
				float[] legs = tendrils.legs();
				if (t >= FIRST_SPARE && legs == null) {
					// a spare: only out while the others walk
					base.visible = false;
					continue;
				}
				if (legs != null && (legs[t * 6] != 0.0F || legs[t * 6 + 1] != 0.0F || legs[t * 6 + 2] != 0.0F)) {
					// walking you along: bowed out from the back, its tip planted (or stepping) on whatever it holds
					bend(this.parts[t], legs[t * 6], legs[t * 6 + 1], legs[t * 6 + 2], legs[t * 6 + 3], legs[t * 6 + 4], legs[t * 6 + 5], eased);
					continue;
				}
				if (combo == WebTendrilCombos.BARRAGE && base.visible && tendrils.tethers() == null) {
					// stabbing, over and over: the free tendrils take turns, one stab a beat, cocked back then driven
					// straight into it; the rest wait poised, half drawn back
					float beat = state.ageInTicks / WebTendrilCombos.BARRAGE_CYCLE;
					int[] free = legs == null ? BARRAGE_FREE : BARRAGE_FREE_WALKING;
					boolean turn = t == free[Math.floorMod(Mth.floor(beat), free.length)];
					float p = beat - Mth.floor(beat);
					float reach;
					float cock;
					if (!turn) {
						cock = STAB_POISED;
						reach = Mth.lerp(cock, 1.0F, STAB_SHORT);
					} else if (p < STAB_COCK) {
						cock = smooth(p / STAB_COCK);
						reach = Mth.lerp(cock, 1.0F, STAB_SHORT);
					} else if (p < STAB_HIT) {
						float k = (p - STAB_COCK) / (STAB_HIT - STAB_COCK);
						cock = 1.0F - k;
						reach = Mth.lerp(k * k, STAB_SHORT, STAB_LONG);
					} else {
						float k = smooth((p - STAB_HIT) / (1.0F - STAB_HIT));
						cock = 0.0F;
						reach = Mth.lerp(k, STAB_LONG, 1.0F);
					}
					base.yRot = aimYaw(tendrils.aimYaw() - side * STAB_OUT * cock, r[3]);
					base.xRot = tendrils.aimPitch() + STAB_UP * cock;
					base.xScale = thick;
					base.yScale = thick;
					base.zScale = length * reach * (fp ? 1.0F + STRETCH_FIRST_PERSON : 1.0F);
					this.stabbing[t] = true;
					// rigid as a spear
					for (int k = 1; k < SEGMENTS; k++) {
						ModelPart segment = this.parts[t][k];
						segment.xRot = 0.0F;
						segment.yRot = 0.0F;
						segment.xScale = 1.0F;
						segment.yScale = 1.0F;
					}
					continue;
				}
				// a wild one never settles: quicker sway, and now and then a tendril jerks out on its own
				float sway = Mth.sin(time * (0.07F + FERAL_SWAY * feral) + t * 1.3F) * 0.2F;
				float jerk = (float)Math.pow(Math.max(0.0F, Mth.sin(time * 0.31F + t * 2.7F) * Mth.sin(time * 0.13F + t * 1.1F)), 8.0)
					* FERAL_JERK * feral;
				sway = sway + jerk;
				float restYaw = r[3] * (1.0F - DRAG_NARROW * drag) + sway * 0.5F;
				float restPitch = r[4] * (1.0F - DRAG_LOWER * drag) + sway;
				float restCurlX = Mth.lerp(drag, 0.28F, DRAG_CURL);
				float restCurlY = 0.0F;
				float restLength = length;
				if (fp && upper) {
					// your own, from your eyes: forward over the shoulders, curling in at the edges of the view
					float fpSway = Mth.sin(time * 0.09F + t * 1.7F) * FP_SWAY;
					restYaw = aimYaw(-side * (FP_YAW + fpSway), side);
					restPitch = FP_PITCH + fpSway * 0.5F;
					restCurlX = FP_CURL_DOWN;
					restCurlY = side * FP_CURL_IN;
					restLength = length * FP_LENGTH;
				}
				// a combo: every upper tendril joins in, from both sides
				boolean striking = weight > 0.0F && upper && (side == strikers || combo >= 0);
				float w = striking ? weight : 0.0F;
				float tYaw = strikeYaw, tPitch = strikePitch, tCurlX = curlX, tCurlY = curlY;
				if (combo == WebTendrilCombos.CLAP) {
					// each side hauled out wide, then clapped in to meet in the middle (crossing a little after)
					tYaw = tendrils.aimYaw() - side * CLAP_WIDE * (1.0F - swing);
					tPitch = tendrils.aimPitch() + CLAP_LIFT * (1.0F - Math.min(1.0F, swing));
					tCurlX = CURL_STRIKE * 0.5F;
					tCurlY = -side * CURL_SWIPE * (swing > 1.0F ? -1.0F : 1.0F);
				} else if (combo == WebTendrilCombos.SHATTER) {
					// both reared high and wide, then brought down together
					tYaw = tendrils.aimYaw() - side * SHATTER_SPREAD * (1.0F - Math.min(1.0F, swing));
				}
				base.yRot = Mth.lerp(w, restYaw, aimYaw(tYaw, restYaw));
				base.xRot = Mth.lerp(w, restPitch, tPitch);
				base.xScale = thick;
				base.yScale = thick;
				base.zScale = restLength * (striking ? stretch : 1.0F);
				float segCurlX = Mth.lerp(w, restCurlX, tCurlX);
				float segCurlY = Mth.lerp(w, restCurlY, tCurlY);
				float calm = 1.0F - w;
				if (tendrils.wall() == 1) {
					// every one of them reaching for the wall, spread a little
					calm = 0.0F;
					segCurlX = CURL_STRIKE * 0.3F;
					segCurlY = 0.0F;
					base.yRot = aimYaw(tendrils.wallYaw() - r[3] * 0.25F, r[3]);
					base.xRot = tendrils.wallPitch() - (r[1] - 4.0F) * 0.04F;
					base.zScale = length * 1.6F * (fp ? 1.0F + STRETCH_FIRST_PERSON : 1.0F);
				} else if (tendrils.wall() == 2 && tendrils.tethers() == null) {
					// back to the wall: fanned out straight behind, driven into it
					calm = 0.0F;
					segCurlX = 0.0F;
					segCurlY = 0.0F;
					base.yRot = r[3] * 0.85F;
					base.xRot = (r[1] - 4.0F) * 0.13F;
					base.zScale = length * 1.15F;
				}
				float[] tethers = tendrils.tethers();
				if (tethers != null && t >= FIRST_TETHER && tethers[t * 3 + 2] > 0.0F) {
					// on its tether: drawn straight and taut to its spot, thrumming with the strain
					base.visible = !fp;
					float thrum = Mth.sin(state.ageInTicks * 2.3F + t * 1.9F) * TETHER_THRUM;
					base.yRot = aimYaw(tethers[t * 3], r[3]);
					base.xRot = tethers[t * 3 + 1] + thrum;
					base.xScale = thick * TETHER_THICK;
					base.yScale = thick * TETHER_THICK;
					base.zScale = tethers[t * 3 + 2] * 16.0F / CHAIN;
					for (int k = 1; k < SEGMENTS; k++) {
						ModelPart segment = this.parts[t][k];
						segment.xRot = thrum * 0.5F;
						segment.yRot = 0.0F;
						segment.xScale = 1.0F;
						segment.yScale = 1.0F;
					}
					continue;
				}
				if (tethers != null && t >= FIRST_TETHER) {
					// not bitten in yet: reaching back for the wall
					base.yRot = r[3] * 0.85F;
					base.xRot = (r[1] - 4.0F) * 0.13F;
				}
				float roar = tendrils.roar();
				if (roar >= 0.0F) {
					// roaring: every one of them thrashing wildly, then flung out wide and long as it roars, then settling
					float blast = WebSymbiote.ROAR_BLAST_AT / (float)WebSymbiote.ROAR_TICKS;
					float fling = roar < blast - FLING_LEAD ? 0.0F : roar < blast ? smooth((roar - blast + FLING_LEAD) / FLING_LEAD)
						: 1.0F - smooth((roar - blast) / (1.0F - blast));
					float thrash = 1.0F - fling;
					float a = state.ageInTicks * FLAIL_SPEED;
					float flailYaw = r[3] * 1.3F + Mth.sin(a + t * 2.3F) * FLAIL_YAW + Mth.sin(a * 1.9F + t) * 0.3F;
					float flailPitch = r[4] * 0.7F + Mth.sin(a * 1.2F + t * 1.7F) * FLAIL_PITCH;
					base.yRot = Mth.lerp(fling, flailYaw, r[3] * FLING_WIDE);
					base.xRot = Mth.lerp(fling, flailPitch, r[4] * 0.6F);
					base.zScale = length * (1.0F + FLING_STRETCH * fling + 0.15F * thrash);
					for (int k = 1; k < SEGMENTS; k++) {
						ModelPart segment = this.parts[t][k];
						// whipping: big waves rolling down each one (straightened as they're flung)
						segment.xRot = Mth.sin(a * 1.3F + t + k * 0.9F) * 0.45F * thrash;
						segment.yRot = Mth.cos(a * 1.1F + t * 2.0F + k * 0.8F) * 0.4F * thrash;
						segment.xScale = 0.9F;
						segment.yScale = 0.9F;
					}
					continue;
				}
				// on the move the ripple rolls out toward the tips, quicker and bigger, and the stride sets them slithering
				float slither = Mth.sin(state.walkAnimationPos * 0.6662F + t * 1.3F) * DRAG_SLITHER * drag;
				for (int k = 1; k < SEGMENTS; k++) {
					ModelPart segment = this.parts[t][k];
					float wave = Mth.lerp(drag, Mth.sin(time * 0.12F + t * 0.9F + k * 0.7F),
						Mth.sin(time * (0.12F + DRAG_WAVE_SPEED) + t * 0.9F - k * 0.9F) * (1.0F + DRAG_WAVE)) * (0.18F + 0.07F * k);
					// each segment curls further over, rippling; striking straightens them into a whip
					float flexX = segCurlX + wave * 0.5F * calm + wave * 0.1F * (1.0F - calm);
					float flexY = segCurlY + wave * 0.55F * calm + slither * calm * (k / (float)SEGMENTS);
					segment.xRot = flexX;
					segment.yRot = flexY;
					// a slow swallow-like bulge travels out along each one, faster and harder the wilder it is
					float pulse = 0.9F + (PULSE + FERAL_PULSE * feral) * Mth.sin(time * (0.2F + 0.15F * feral) - k * 0.8F + t * 1.9F);
					segment.xScale = pulse;
					segment.yScale = pulse;
				}
			}
			this.morph(state, tendrils, thick, length, fp);
		}

		/**
		 * The weapon on a tendril ({@link WebMorph} with Living Armor up), over whatever pose it had: gathering, the
		 * striking side's shoulder tendril rears into a coil, out to its side and up, its tip swelling; striking, it winds up
		 * its own way for the weapon (the mace reared over the head, the hammer-fist dropped low for an uppercut, the scythe
		 * drawn out wide, the spear coiled short like a spring, the whip-blade thrown far back over the shoulder, the hook
		 * claw raised), lashes out at the target root first with the tip trailing, takes a ripple back up it at the impact
		 * (the hook claw reeling in), and eases back to rest. The mace and the hammer-fist are two tendrils braided.
		 */
		private void morph(AvatarRenderState state, WebSymbioteClient.Tendrils tendrils, float thick, float length, boolean fp) {
			this.weaponTendril = -1;
			WebMorphLayer.Strike s = state.getData(WebMorphLayer.KEY);
			WebMorphLayer.Gather g = state.getData(WebMorphLayer.GATHER);
			if (s != null && !s.tendril()) {
				s = null;
			}
			if (s != null) {
				g = null;
			} else if (g != null && !g.tendril()) {
				g = null;
			}
			if (s == null && g == null) {
				return;
			}
			int side = s != null ? s.side() : g.side();
			int weapon = s != null ? s.weapon() : g.weapon();
			// model +X is the wearer's left: tendril 0 roots on the right shoulder, 1 the left; 2 and 3 just inside them
			int primary = side > 0 ? 0 : 1;
			int partner = side > 0 ? 2 : 3;
			if (!this.parts[primary][0].visible) {
				// it's holding something
				return;
			}
			boolean braid = (weapon == WebMorph.MACE || weapon == WebMorph.HAMMER) && tendrils.tethers() == null && this.parts[partner][0].visible;
			float stretchFp = fp ? 1.0F + STRETCH_FIRST_PERSON : 1.0F;
			// the coil it gathers into
			float coil = g != null ? smooth(g.age() / WebMorph.FORMED_TICKS) : 1.0F;
			float shudder = g != null && g.age() < WebMorph.FORMED_TICKS ? Mth.sin(g.age() * 2.7F) * MORPH_SHUDDER : 0.0F;
			float yaw = side * MORPH_READY_YAW * coil, pitch = MORPH_READY_PITCH * coil + shudder;
			float curl = MORPH_READY_CURL * coil, curlY = 0.0F;
			float len = length * Mth.lerp(coil, 1.0F, MORPH_READY_LENGTH);
			float on, lag = 0.0F, dPitch = 0.0F, dYaw = 0.0F, ripple = 0.0F, rippleAt = 0.0F, spring = 0.0F, swell;
			if (s == null) {
				on = smooth(g.age() / 4.0F);
				this.weaponGrow = WebMorphLayer.grown(g);
				this.weaponStrike = new WebMorphLayer.Strike(weapon, side, 0.0F, 0.0F, 0.0F, 0.0F, true);
				swell = Math.min(1.0F, this.weaponGrow);
			} else {
				float age = s.age();
				float ay = s.aimYaw(), ap = -s.aimPitch();
				float reach = Mth.clamp(s.reach(), 0.5F, MORPH_MAX_REACH) * 16.0F / CHAIN;
				// wound up (wy, wp, wc, wcy, wl), and at the impact (iy, ip, ic, icy, il)
				float wy = ay, wp = ap, wc = 0.0F, wcy = 0.0F, wl = length;
				float iy = ay, ip = ap, ic = 0.0F, icy = 0.0F, il = reach;
				switch (weapon) {
					case WebMorph.MACE -> {
						wp = SLAM_HIGH;
						wc = CURL_REARED;
						ip = ap - SLAM_DIP;
						ic = CURL_STRIKE;
					}
					case WebMorph.HAMMER -> {
						wy = ay + side * 0.3F;
						wp = -MORPH_HAMMER_LOW;
						wc = -0.25F;
						wl = length * 0.9F;
						ip = ap + 0.1F;
						ic = 0.06F;
					}
					case WebMorph.SCYTHE -> {
						wy = ay + side * SWIPE_WIDE;
						wp = ap + SWIPE_LIFT;
						wc = CURL_STRIKE * 0.5F;
						wcy = side * CURL_SWIPE;
						iy = ay - side * 0.3F;
						ip = ap + SWIPE_LIFT * 0.5F;
						ic = CURL_STRIKE * 0.5F;
						icy = side * CURL_SWIPE * 0.5F;
					}
					case WebMorph.SPEAR -> {
						wy = ay + side * 0.3F;
						wp = ap + 0.35F;
						wc = 0.0F;
						wl = length * 0.55F;
						il = reach * 1.05F;
					}
					case WebMorph.WHIP -> {
						wy = ay + side * 0.35F;
						wp = MORPH_WHIP_BACK;
						wc = 0.3F;
						wl = length * 1.1F;
						ip = ap - 0.05F;
						ic = -0.04F;
					}
					default -> {
						// the hook claw
						wy = ay + side * 0.25F;
						wp = ap + 0.6F;
						wc = 0.25F;
						wl = length * 0.8F;
					}
				}
				if (age < WebMorph.FORM) {
					// from the coil into the wind-up
					float p = smooth(age / WebMorph.FORM);
					yaw = Mth.lerp(p, yaw, wy);
					pitch = Mth.lerp(p, pitch, wp);
					curl = Mth.lerp(p, curl, wc);
					curlY = Mth.lerp(p, curlY, wcy);
					len = Mth.lerp(p, len, wl);
					spring = weapon == WebMorph.SPEAR ? p : 0.0F;
				} else if (age < WebMorph.HIT) {
					// the lash, faster and faster, landing at the hit
					float k = (age - WebMorph.FORM) / (WebMorph.HIT - WebMorph.FORM);
					float sw = k * k;
					yaw = Mth.lerp(sw, wy, iy);
					pitch = Mth.lerp(sw, wp, ip);
					curl = Mth.lerp(sw, wc, ic);
					curlY = Mth.lerp(sw, wcy, icy);
					len = Mth.lerp(sw, wl, il);
					lag = 1.0F - sw;
					dPitch = Math.signum(ip - wp);
					dYaw = Math.signum(iy - wy);
					spring = weapon == WebMorph.SPEAR ? 1.0F - k : 0.0F;
				} else {
					// the impact, and on through it a little
					float after = age - WebMorph.HIT;
					float follow = Math.min(1.0F, after / 3.0F);
					yaw = iy;
					pitch = ip;
					curl = ic;
					curlY = icy;
					len = il;
					switch (weapon) {
						case WebMorph.MACE -> pitch -= 0.2F * follow;
						case WebMorph.WHIP -> pitch -= 0.25F * follow;
						case WebMorph.HAMMER -> pitch += 0.35F * follow;
						case WebMorph.SCYTHE -> yaw -= side * 0.5F * follow;
						case WebMorph.HOOK -> {
							// it bites and reels in
							float reel = smooth(after / MORPH_REEL_TICKS);
							len = Mth.lerp(reel, il, length * MORPH_READY_LENGTH);
							curl = Mth.lerp(reel, ic, 0.2F);
						}
						default -> {
						}
					}
					ripple = 1.0F - smooth(after / MORPH_RIPPLE_TICKS);
					rippleAt = after;
				}
				on = 1.0F - smooth((age - (WebMorph.TICKS - MORPH_RETURN_TICKS)) / MORPH_RETURN_TICKS);
				this.weaponGrow = WebMorphLayer.formed(age);
				this.weaponStrike = s;
				swell = this.weaponGrow;
			}
			float trail = lag * (weapon == WebMorph.WHIP ? MORPH_WHIP_LAG : MORPH_LAG);
			for (int n = 0; n < (braid ? 2 : 1); n++) {
				int t = n == 0 ? primary : partner;
				float[] r = ROOTS[t];
				ModelPart base = this.parts[t][0];
				base.visible = true;
				base.yRot = Mth.lerp(on, base.yRot, aimYaw(yaw, r[3]));
				base.xRot = Mth.lerp(on, base.xRot, pitch);
				float th = thick * MORPH_THICK * (braid ? 0.8F : 1.0F);
				base.xScale = Mth.lerp(on, base.xScale, th);
				base.yScale = Mth.lerp(on, base.yScale, th);
				base.zScale = Mth.lerp(on, base.zScale, len * stretchFp);
				float twist = n == 0 ? 0.0F : Mth.PI;
				for (int k = 1; k < SEGMENTS; k++) {
					ModelPart segment = this.parts[t][k];
					float u = k / (float)(SEGMENTS - 1);
					// the tip trails behind the way it's going, a ripple runs back up it at the impact, a spear coiled like a spring
					float cx = curl - dPitch * trail * u + ripple * MORPH_RIPPLE * Mth.sin(k * 1.1F - rippleAt * 2.2F)
						+ spring * MORPH_SPRING * (k % 2 == 0 ? 1.0F : -1.0F);
					float cy = curlY - dYaw * trail * u * 0.8F;
					if (braid) {
						// the two wound round each other
						cx += MORPH_BRAID * 0.5F * Mth.cos(k * 1.7F + twist);
						cy += MORPH_BRAID * Mth.sin(k * 1.7F + twist);
					}
					segment.xRot = Mth.lerp(on, segment.xRot, cx);
					segment.yRot = Mth.lerp(on, segment.yRot, cy);
					// the tip swells into the head
					float size = k == SEGMENTS - 1 ? 1.0F + MORPH_TIP_SWELL * swell : 1.0F;
					segment.xScale = Mth.lerp(on, segment.xScale, size);
					segment.yScale = Mth.lerp(on, segment.yScale, size);
				}
			}
			this.weaponTendril = primary;
		}
	}
}

