package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageHold;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.RubberFolk;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.7.290) Andy: "the absorb should animate the user's body leeching onto and over the enemy, like rubber, then it
 * engulfs the body and adds the mass to the user". A Rubber-folk swallowing a foe ({@link HeritageHold#ABSORBING}), as
 * everyone sees it, over {@link RubberFolk#ABSORB_TICKS}:
 * <ol>
 * <li><b>Leech</b> ({@link #LEECH}): a thick rope of their pink rubber stretches out of the reaching hand
 * ({@link BioAbsorbPose}) and slaps onto the foe.</li>
 * <li><b>Over</b> ({@link #OVER_FROM} .. {@link #OVER_TO}): it spreads over the foe from where it struck, a skin
 * creeping round to the far side until it's sealed in.</li>
 * <li><b>Engulf</b>: the sealed skin wobbles and the rope gulps, lumps running down it to the body, while the foe
 * shrinks inside (the server's shrink, {@link HeritageHold}).</li>
 * <li><b>Mass</b>: swallowed, their body swells ({@link #SWELL}) and settles into its new build (their MCA height and
 * width grow by the foe's bulk, a little with each pulse, {@code BodySizes.withMass}).</li>
 * </ol>
 * The rubber is drawn in the world ({@link #render}); the swell goes on the model as it's posed ({@link #apply}).
 * <p>
 * (0.7.421) Andy: "the rubber swallow should animate from the outstretched hand in first person". Your own first-person
 * arm reaches out ahead ({@link #firstPersonReach}), trembling with the pulses, and the rope leaves its fist: where the
 * game drew the fist ({@link #captureFist}) is carried into the world, onto the same spot on screen.
 */
public final class RubberEngulf {
	/** Pink rubber with a sheen ({@code tools/gen_rubber_skin.py}). */
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/heritage/rubber_engulf.png");
	/** The shares of the swallow: the rope out, the skin spreading over (from .. to), the gulps begin. */
	static final float LEECH = 0.25F, OVER_FROM = 0.2F, OVER_TO = 0.65F, GULP_FROM = 0.6F;
	/** The rope's thickness (blocks): as it shoots out, and once it's on; a gulp's lump, its length, and how many run. */
	static final float ROPE_THIN = 0.07F, ROPE_THICK = 0.16F, LUMP = 0.12F, LUMP_LENGTH = 0.12F, GULPS = 3.0F;
	/** How far the skin stands off the foe (a share of its size), and how much it wobbles. */
	static final float SKIN_OFF = 1.12F, WOBBLE = 0.06F;
	/** After a swallow: how long the body stays swollen (ticks), and how much bigger it gets round at the most. */
	static final float SWELL_TICKS = 16.0F, SWELL = 0.28F;
	/** The mesh: sides round the rope, pieces along it; rings and slices of the skin. */
	static final int SIDES = 8, PIECES = 14, RINGS = 10, SLICES = 14;

	/** First person: how long your arm takes to reach out (ticks), and its tremble (degrees). */
	static final float REACH_TICKS = 6.0F, FIRST_TREMBLE = 1.5F;
	/**
	 * First person, the right arm (hand view space, blocks): the game's own shoulder and fist, and where the reaching fist
	 * goes and the way the arm points there (in from below, at the middle of the screen). The left arm is the mirror.
	 */
	static final Vector3f SHOULDER = new Vector3f(0.479F, -0.844F, -0.528F), FIST = new Vector3f(0.755F, -0.438F, -1.095F),
		REACH_FIST = new Vector3f(0.26F, -0.24F, -1.15F), REACH_AIM = new Vector3f(0.12F, 0.3F, -1.0F).normalize();
	/** The reach, for the right arm: the turn about the shoulder, and the move after it. */
	static final Quaternionf REACH_TURN = new Quaternionf().rotationTo(new Vector3f(FIST).sub(SHOULDER).normalize(), REACH_AIM);
	static final Vector3f REACH_MOVE = new Vector3f(REACH_FIST).sub(new Vector3f(FIST).sub(SHOULDER).rotate(REACH_TURN).add(SHOULDER));
	/** Where the fist sits along the arm (model pixels from the shoulder pivot), the rope's root inside it. */
	static final float FIST_DOWN = 9.0F, FIST_IN = 1.0F;

	/** This frame's swell (0 .. 1) for a player who just swallowed, on their render state. */
	static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:rubber_swell");

	/** Entity id → the clock (ticks) their swallow began; → how far it had got last frame; → when they last swallowed. */
	private static final Map<Integer, Float> STARTED = new HashMap<>(), REACHED = new HashMap<>(), SWALLOWED = new HashMap<>();

	/** The camera's turn this frame (world render), for the hand pass that follows it. */
	private static final Quaternionf ORIENTATION = new Quaternionf();
	/** Your reaching fist in view space (blocks, hand projection), and the frame clock it was drawn at (-1: never). */
	private static final Vector3f FIST_VIEW = new Vector3f();
	private static float fistAt = -1.0F;
	/** The game is drawing your reaching arm right now ({@link #captureFist} keeps its fist). */
	private static boolean reaching;

	private RubberEngulf() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(RubberEngulf::render);
	}

	/** {@code entity} is a Rubber-folk. */
	static boolean rubber(Entity entity) {
		return Heritage.Kind.RUBBERFOLK.key().equals(entity.getAttached(Heritage.HERITAGE));
	}

	/** How far {@code player}'s swallow has got (0 .. 1), at {@code clock}; noted for when it ends. */
	private static float progress(Player player, float clock) {
		float started = STARTED.computeIfAbsent(player.getId(), k -> clock);
		float t = Mth.clamp((clock - started) / RubberFolk.ABSORB_TICKS, 0.0F, 1.0F);
		REACHED.put(player.getId(), t);
		return t;
	}

	/** {@code player} is no longer swallowing: if it got (nearly) all the way, they swallowed it, and swell. */
	private static void ended(Player player, float clock) {
		STARTED.remove(player.getId());
		Float reached = REACHED.remove(player.getId());
		if (reached != null && reached >= 0.9F) {
			SWALLOWED.put(player.getId(), clock);
		}
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		CameraRenderState cameraState = context.levelState().cameraRenderState;
		Vec3 camera = cameraState.pos;
		ORIENTATION.set(cameraState.orientation);
		for (Player player : level.players()) {
			float clock = player.tickCount + partialTick;
			Integer id = player.getAttached(HeritageHold.ABSORBING);
			Entity foe = id == null || !rubber(player) ? null : level.getEntity(id);
			if (!(foe instanceof LivingEntity)) {
				if (STARTED.containsKey(player.getId())) {
					ended(player, clock);
				}
				continue;
			}
			if (player.position().distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			float t = progress(player, clock);
			Vec3 fist = player == minecraft.player && cameraState.isFirstPerson ? firstPersonFist(cameraState, clock) : null;
			draw(context, level, camera, player, foe, t, clock, partialTick, fist);
		}
	}

	/**
	 * Where your reaching fist is in the world this frame (first person): the fist as the game last drew it, moved out to
	 * the same spot on screen through the world's own field of view. Null before the arm's been drawn (hand hidden).
	 */
	private static Vec3 firstPersonFist(CameraRenderState cameraState, float clock) {
		if (fistAt < 0.0F || Math.abs(clock - fistAt) > 2.0F) {
			return null;
		}
		// the hand is drawn at its own (fixed) field of view: the same spot on screen at the same depth is wider or narrower
		float spread = 1.0F / (cameraState.projectionMatrix.m11() * (float)Math.tan(cameraState.hudFov * 0.5F * Mth.DEG_TO_RAD));
		Vector3f world = new Vector3f(FIST_VIEW.x * spread, FIST_VIEW.y * spread, FIST_VIEW.z).rotate(cameraState.orientation);
		return cameraState.pos.add(world.x, world.y, world.z);
	}

	/**
	 * First person, before the game draws your {@code hand}: while you're swallowing, the main arm reaches out ahead,
	 * trembling with the pulses. Returns how far it's out (0 .. 1); 0 leaves the hand to the game. The caller draws the
	 * bare arm (whatever's held is hidden while the hand's out), between {@link #reaching(boolean)} true and false.
	 */
	public static float firstPersonReach(PoseStack poseStack, AvatarRenderState avatar, net.minecraft.world.InteractionHand hand) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || avatar.id != player.getId() || avatar.isInvisible || hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
			return 0.0F;
		}
		Float started = STARTED.get(player.getId());
		if (started == null || player.getAttached(HeritageHold.ABSORBING) == null) {
			return 0.0F;
		}
		float clock = player.tickCount + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float w = ease(Mth.clamp((clock - started) / REACH_TICKS, 0.0F, 1.0F));
		if (w <= 0.0F) {
			return 0.0F;
		}
		float side = avatar.mainArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		// about the shoulder: turned to point ahead, then moved so the fist sits a little right of and below the middle
		Quaternionf turn = new Quaternionf().slerp(new Quaternionf(REACH_TURN.x, side * REACH_TURN.y, side * REACH_TURN.z, REACH_TURN.w), w);
		poseStack.translate(side * REACH_MOVE.x * w, REACH_MOVE.y * w, REACH_MOVE.z * w);
		poseStack.translate(side * SHOULDER.x, SHOULDER.y, SHOULDER.z);
		poseStack.rotate(turn);
		poseStack.rotateDegrees(Axis.XP, Mth.sin(clock * 1.3F) * FIRST_TREMBLE * w);
		poseStack.translate(-side * SHOULDER.x, -SHOULDER.y, -SHOULDER.z);
		return w;
	}

	/** The game is (true) or done (false) drawing your reaching arm. */
	public static void reaching(boolean now) {
		reaching = now;
	}

	/** The game just drew a first-person {@code arm} at {@code poseStack}: if it's your reaching arm, keep where its fist is. */
	public static void captureFist(PoseStack poseStack, ModelPart arm, boolean right) {
		if (!reaching) {
			return;
		}
		Matrix4f at = new Matrix4f(poseStack.last().pose());
		at.translate(arm.x / 16.0F, arm.y / 16.0F, arm.z / 16.0F).rotate(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
		Vector3f fist = at.transformPosition(new Vector3f((right ? -FIST_IN : FIST_IN) / 16.0F, FIST_DOWN / 16.0F, 0.0F));
		// the hand pass starts from the camera's turn: take it off, leaving the fist in view space
		FIST_VIEW.set(fist).rotate(new Quaternionf(ORIENTATION).conjugate());
		Minecraft minecraft = Minecraft.getInstance();
		fistAt = minecraft.player == null ? -1.0F : minecraft.player.tickCount + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	/** The rope and the skin for one swallow, {@code t} of the way through. */
	private static void draw(LevelRenderContext context, ClientLevel level, Vec3 camera, Player player, Entity foe, float t, float clock, float partialTick,
		Vec3 firstPersonFist) {
		Vec3 body = player.getPosition(partialTick);
		Vec3 middle = foe.getPosition(partialTick).add(0.0, foe.getBbHeight() * 0.5, 0.0);
		Vec3 flat = new Vec3(middle.x - body.x, 0.0, middle.z - body.z);
		Vec3 toward = flat.lengthSqr() < 1.0E-6 ? player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : flat.normalize();
		// the reaching hand: out from the shoulder of the main arm, toward the foe
		Vec3 right = new Vec3(-toward.z, 0.0, toward.x).scale(player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0);
		Vec3 hand = firstPersonFist != null ? firstPersonFist
			: body.add(0.0, player.getBbHeight() * 0.72, 0.0).add(right.scale(0.33)).add(toward.scale(0.45));
		// the skin round the foe, and where the rope strikes it (its side facing the hand)
		double rx = foe.getBbWidth() * 0.5 * SKIN_OFF, ry = foe.getBbHeight() * 0.5 * SKIN_OFF;
		Vec3 back = hand.subtract(middle);
		Vec3 pole = back.lengthSqr() < 1.0E-6 ? toward.scale(-1.0) : back.normalize();
		Vec3 strike = middle.add(pole.x * rx, pole.y * ry, pole.z * rx);
		float reach = ease(Mth.clamp(t / LEECH, 0.0F, 1.0F));
		Vec3 tip = hand.add(strike.subtract(hand).scale(reach));
		float thick = Mth.lerp(ease(Mth.clamp((t - OVER_FROM) / (OVER_TO - OVER_FROM), 0.0F, 1.0F)), ROPE_THIN, ROPE_THICK);
		float over = ease(Mth.clamp((t - OVER_FROM) / (OVER_TO - OVER_FROM), 0.0F, 1.0F));
		float gulp = t < GULP_FROM ? -1.0F : (t - GULP_FROM) / (1.0F - GULP_FROM);
		int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, BlockPos.containing(middle));
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
			rope(buffer, pose, hand.subtract(camera), tip.subtract(camera), thick, gulp, clock, light);
			if (over > 0.0F) {
				skin(buffer, pose, middle.subtract(camera), rx, ry, pole, over, clock, light);
			}
		});
	}

	/**
	 * The rope from the hand to its tip: sagging a little, wobbling like rubber, swelling at the foe's end where it
	 * spreads, and (from the gulps on, {@code gulp} 0 .. 1) lumps running down it to the body.
	 */
	private static void rope(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, float thick, float gulp, float clock, int light) {
		Vec3 along = to.subtract(from);
		double length = along.length();
		if (length < 0.05) {
			return;
		}
		Vec3 dir = along.scale(1.0 / length);
		Vec3 u = Math.abs(dir.y) < 0.95 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 v = dir.cross(u).normalize();
		Vec3[] centre = new Vec3[PIECES + 1];
		double[] radius = new double[PIECES + 1];
		for (int i = 0; i <= PIECES; i++) {
			double s = i / (double)PIECES;
			double sag = Math.sin(s * Math.PI) * length * 0.08;
			double wobble = Math.sin(clock * 0.6 + s * 9.0) * 0.03 * Math.sin(s * Math.PI);
			centre[i] = from.add(along.scale(s)).add(0.0, -sag, 0.0).add(u.scale(wobble));
			// thicker where it meets the hand and where it spreads over the foe
			double r = thick * (1.0 + 0.35 * Math.pow(s, 4.0) + 0.2 * Math.pow(1.0 - s, 6.0));
			if (gulp >= 0.0F) {
				for (int g = 0; g < GULPS; g++) {
					// each lump starts at the foe and runs to the hand, one after another
					double at = 1.0 - Mth.clamp(gulp * GULPS - g, 0.0F, 1.0F);
					if (at > 0.0 && at < 1.0) {
						double d = (s - at) / LUMP_LENGTH;
						r += LUMP * Math.exp(-d * d);
					}
				}
			}
			radius[i] = r;
		}
		for (int i = 0; i < PIECES; i++) {
			for (int k = 0; k < SIDES; k++) {
				double a0 = k * Math.PI * 2.0 / SIDES, a1 = (k + 1) * Math.PI * 2.0 / SIDES;
				Vec3 n0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0))), n1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
				float v0 = i / (float)PIECES, v1 = (i + 1) / (float)PIECES;
				vertex(buffer, pose, centre[i].add(n0.scale(radius[i])), n0, k / (float)SIDES, v0, light);
				vertex(buffer, pose, centre[i + 1].add(n0.scale(radius[i + 1])), n0, k / (float)SIDES, v1, light);
				vertex(buffer, pose, centre[i + 1].add(n1.scale(radius[i + 1])), n1, (k + 1) / (float)SIDES, v1, light);
				vertex(buffer, pose, centre[i].add(n1.scale(radius[i])), n1, (k + 1) / (float)SIDES, v0, light);
			}
		}
	}

	/**
	 * The skin over the foe: an egg round its box (half-sizes {@code rx}, {@code ry}), spread from where the rope struck
	 * ({@code pole}) {@code over} of the way round to the far side, its rim lumpy where it's still creeping, the whole of it
	 * wobbling.
	 */
	private static void skin(VertexConsumer buffer, PoseStack.Pose pose, Vec3 centre, double rx, double ry, Vec3 pole, float over, float clock,
		int light) {
		Vec3 e1 = Math.abs(pole.y) < 0.95 ? pole.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : pole.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 e2 = pole.cross(e1).normalize();
		double cap = over * Math.PI;
		Vec3[][] point = new Vec3[RINGS + 1][SLICES + 1];
		Vec3[][] normal = new Vec3[RINGS + 1][SLICES + 1];
		for (int i = 0; i <= RINGS; i++) {
			for (int j = 0; j <= SLICES; j++) {
				double phi = j * Math.PI * 2.0 / SLICES;
				// the creeping rim isn't even: some fingers of it reach further round than others
				double reachHere = cap * (i == RINGS && over < 1.0F ? 0.85 + 0.15 * Math.sin(phi * 3.0 + clock * 0.4) : 1.0);
				double theta = reachHere * i / RINGS;
				Vec3 dir = pole.scale(Math.cos(theta)).add(e1.scale(Math.cos(phi) * Math.sin(theta))).add(e2.scale(Math.sin(phi) * Math.sin(theta)));
				double wobble = 1.0 + WOBBLE * Math.sin(clock * 0.5 + theta * 3.0 + phi * 2.0);
				point[i][j] = centre.add(dir.x * rx * wobble, dir.y * ry * wobble, dir.z * rx * wobble);
				Vec3 n = new Vec3(dir.x / rx, dir.y / ry, dir.z / rx);
				normal[i][j] = n.lengthSqr() < 1.0E-10 ? dir : n.normalize();
			}
		}
		for (int i = 0; i < RINGS; i++) {
			for (int j = 0; j < SLICES; j++) {
				float u0 = j / (float)SLICES, u1 = (j + 1) / (float)SLICES, v0 = i / (float)RINGS, v1 = (i + 1) / (float)RINGS;
				vertex(buffer, pose, point[i][j], normal[i][j], u0, v0, light);
				vertex(buffer, pose, point[i][j + 1], normal[i][j + 1], u1, v0, light);
				vertex(buffer, pose, point[i + 1][j + 1], normal[i + 1][j + 1], u1, v1, light);
				vertex(buffer, pose, point[i + 1][j], normal[i + 1][j], u0, v1, light);
			}
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 n, float u, float v, int light) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, (float)n.x, (float)n.y, (float)n.z);
	}

	private static float ease(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	/** Render-state extraction: how swollen a Rubber-folk who just swallowed is this frame. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Avatar)) {
			return;
		}
		if (entity.hasAttached(com.jaymoskingdoms.RubberStomach.HOLDING)) {
			// (0.7.312) someone alive inside: the belly stays full, and heaves as they fight
			float clock = entity.tickCount + partialTicks;
			state.setData(KEY, 0.55F + 0.12F * Mth.sin(clock * 0.35F) + 0.08F * Mth.sin(clock * 0.9F));
			return;
		}
		Float when = SWALLOWED.get(entity.getId());
		if (when == null) {
			state.setData(KEY, null);
			return;
		}
		float since = entity.tickCount + partialTicks - when;
		if (since < 0.0F || since > SWELL_TICKS) {
			SWALLOWED.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		// up fast as the mass goes in, then settling back with a wobble
		float x = since / SWELL_TICKS;
		float swell = x < 0.2F ? x / 0.2F : (1.0F - (x - 0.2F) / 0.8F) * (0.75F + 0.25F * Mth.cos((x - 0.2F) * 20.0F));
		state.setData(KEY, Mth.clamp(swell, 0.0F, 1.0F));
	}

	/** Right after a model was posed for this state: the body and arms swollen round with what went in. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		Float swell = living.getData(KEY);
		if (swell == null || swell <= 0.0F) {
			return;
		}
		float round = 1.0F + SWELL * swell, arms = 1.0F + SWELL * 0.6F * swell;
		m.body.xScale *= round;
		m.body.zScale *= round;
		m.rightArm.xScale *= arms;
		m.rightArm.zScale *= arms;
		m.leftArm.xScale *= arms;
		m.leftArm.zScale *= arms;
	}
}
