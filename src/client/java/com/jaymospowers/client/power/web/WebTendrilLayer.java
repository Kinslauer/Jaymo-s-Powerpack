package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
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
 */
final class WebTendrilLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_tendril.png");
	/** Ticks of Living Armor until the tendrils are fully grown. */
	static final float GROWN_TICKS = 80.0F;
	/** Segments per tendril, and each one's length (model pixels). */
	private static final int SEGMENTS = 7;
	private static final float SEGMENT_LENGTH = 4.5F;
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
	};
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

	private final TendrilModel model;
	private static TendrilModel firstPersonModel;

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
				parent = parent.addOrReplaceChild("s" + k, CubeListBuilder.create().texOffs(0, (k % 2) * 8)
					.addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, SEGMENT_LENGTH), PartPose.offset(0.0F, 0.0F, SEGMENT_LENGTH - 0.4F));
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
		poseStack.popPose();
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
		poseStack.pushPose();
		poseStack.translate(0.0F, FP_ROOT_UP, FP_ROOT_BACK);
		// camera space to model space: model -Z is ahead, -Y up, +X the wearer's left
		poseStack.scale(-FP_SCALE, -FP_SCALE, FP_SCALE);
		collector.submitModel(firstPersonModel, own, poseStack, RenderTypes.entityCutout(TEXTURE), avatar.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/**
	 * Hung off a wall: where each tethered tendril has to point to reach its spot (yaw and pitch against the body, as a
	 * strike's aim is measured) and how far (blocks; growing out over {@link #TETHER_REACH_TICKS}, one after another).
	 * The roots are placed roughly: the body upright, turned as it faces.
	 */
	static float[] tethers(net.minecraft.world.entity.player.Player player, WebSymbiote.Wall wall, float partial) {
		float[] out = new float[ROOTS.length * 3];
		float scale = player.getScale();
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		net.minecraft.world.phys.Vec3 feet = player.getPosition(partial);
		double yaw = bodyYaw * Mth.DEG_TO_RAD;
		net.minecraft.world.phys.Vec3 ahead = new net.minecraft.world.phys.Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		net.minecraft.world.phys.Vec3 left = new net.minecraft.world.phys.Vec3(ahead.z, 0.0, -ahead.x);
		float age = player.level().getGameTime() - wall.landed() + partial;
		// the bites slide a little after you as you sway on them (along the wall only)
		net.minecraft.world.phys.Vec3 normal = net.minecraft.core.Direction.from3DDataValue(wall.face()).getUnitVec3();
		net.minecraft.world.phys.Vec3 drift = feet.subtract(wall.rest());
		drift = drift.subtract(normal.scale(drift.dot(normal))).scale(WebSymbiote.TETHER_FOLLOW);
		for (int i = 0; i < wall.points().size() && i + FIRST_TETHER < ROOTS.length; i++) {
			int t = i + FIRST_TETHER;
			float[] r = ROOTS[t];
			// model space: +X the wearer's left, +Y down from the neck (24 pixels over the feet), +Z back
			net.minecraft.world.phys.Vec3 root = feet.add(left.scale(r[0] / 16.0F * scale)).add(0.0, (24.0F - r[1]) / 16.0F * scale, 0.0)
				.subtract(ahead.scale(r[2] / 16.0F * scale));
			net.minecraft.world.phys.Vec3 to = wall.points().get(i).add(drift).subtract(root);
			float aim = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
			out[t * 3] = Mth.wrapDegrees(aim - bodyYaw) * Mth.DEG_TO_RAD;
			out[t * 3 + 1] = (float)Mth.atan2(to.y, to.horizontalDistance());
			out[t * 3 + 2] = (float)to.length() / scale * Mth.clamp((age - i) / TETHER_REACH_TICKS, 0.0F, 1.0F);
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
				if (combo == WebTendrilCombos.BARRAGE && base.visible && tendrils.legs() == null && tendrils.tethers() == null) {
					// stabbing, over and over: the free tendrils take turns, one stab a beat, cocked back then driven
					// straight into it; the rest wait poised, half drawn back
					float beat = state.ageInTicks / WebTendrilCombos.BARRAGE_CYCLE;
					boolean turn = t == FIRST_TETHER + Math.floorMod(Mth.floor(beat), ROOTS.length - FIRST_TETHER);
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
				float[] legs = tendrils.legs();
				if (legs != null && (legs[t * 6] != 0.0F || legs[t * 6 + 1] != 0.0F || legs[t * 6 + 2] != 0.0F)) {
					// walking you up the wall: bowed out from the back, its tip planted (or stepping) on the surface
					bend(this.parts[t], legs[t * 6], legs[t * 6 + 1], legs[t * 6 + 2], legs[t * 6 + 3], legs[t * 6 + 4], legs[t * 6 + 5], eased);
					continue;
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
		}
	}
}
