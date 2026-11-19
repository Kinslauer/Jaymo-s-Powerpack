package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbioteWings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote's wings (see {@link WebSymbioteWings}), drawn for everyone off the back of a host whose wings are spread:
 * two great bat-like spans, a black ribbed frame (a leading arm that rises to a wrist, four ribs fanning from it, spikes
 * at the joints; {@link WebTubes}) with a translucent raw-red membrane stretched between ribs and scalloped at its
 * trailing edge ({@code textures/entity/web_symbiote_wings.png}, {@code tools/gen_web_extras.py}). Every strain's are
 * the same red. They spread and fold over {@link #SPREAD_TICKS}, sweep down on every flap
 * ({@link WebSymbioteWings#FLAPPED}) and flutter a little in a glide. All the numbers are model pixels (+X out from the
 * spine, +Y down, +Z behind), the right wing's; the left is its mirror.
 */
final class WebSymbioteWingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_wings.png");
	/** How far the wings are spread (0 folded, 1 out), and the beat since the last flap (0-1, or absent). */
	static final RenderStateDataKey<Float> SPREAD = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_wings");
	static final RenderStateDataKey<Float> FLAP = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_wings_flap");

	/** Ticks to spread or fold. */
	static final float SPREAD_TICKS = 12.0F;
	/** How far a flap sweeps (radians) and how much a glide flutters. */
	static final float FLAP_SWEEP = 0.7F, FLUTTER = 0.05F;
	/** The membrane's trailing edge is pulled in toward the wrist this much between ribs. */
	static final double SCALLOP = 0.22;
	/** The wings are drawn this much bigger on the big body. */
	static final float BIG_BODY_SCALE = 1.25F;

	// the joints, spread (S shoulder, E elbow, W wrist, F1-F4 rib tips from the outermost in, B hip) and folded (on the back)
	private static final Vec3 S = new Vec3(3, 3, 2.5);
	private static final Vec3[] OPEN = {new Vec3(18, -12, 6), new Vec3(36, -26, 9), new Vec3(58, -8, 14), new Vec3(50, 14, 16), new Vec3(36, 32, 16),
		new Vec3(20, 40, 14), new Vec3(3, 26, 4)};
	private static final Vec3[] SHUT = {new Vec3(6, -4, 5), new Vec3(5, -14, 6), new Vec3(5, -10, 7), new Vec3(5, -2, 7.5), new Vec3(5, 6, 7.5),
		new Vec3(4, 12, 6.5), new Vec3(3, 12, 4)};
	private static final int E = 0, W = 1, F1 = 2, F2 = 3, F3 = 4, F4 = 5, B = 6;

	/** Each entity's spread now, and the nanotime it was last moved at. */
	private static final Map<Integer, float[]> SPREADS = new HashMap<>();

	WebSymbioteWingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	/** From the avatar renderer's extractRenderState: the wings' spread and beat for this player. */
	static void extract(Entity entity, AvatarRenderState state) {
		boolean out = entity instanceof Player player && WebSymbioteWings.out(player);
		int id = entity.getId();
		long now = System.nanoTime();
		float[] spread = SPREADS.get(id);
		if (spread == null) {
			if (!out) {
				state.setData(SPREAD, null);
				state.setData(FLAP, null);
				return;
			}
			spread = new float[] {0.0F, now};
			SPREADS.put(id, spread);
		}
		float dt = Mth.clamp((now - spread[1]) / 1.0E9F * 20.0F, 0.0F, 3.0F);
		spread[1] = now;
		spread[0] = Mth.clamp(spread[0] + (out ? dt : -dt) / SPREAD_TICKS, 0.0F, 1.0F);
		if (spread[0] <= 0.0F && !out) {
			SPREADS.remove(id);
			state.setData(SPREAD, null);
			state.setData(FLAP, null);
			return;
		}
		state.setData(SPREAD, spread[0]);
		Long flapped = entity.getAttached(WebSymbioteWings.FLAPPED);
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float age = flapped == null ? -1.0F : entity.level().getGameTime() + partial - flapped;
		state.setData(FLAP, age >= 0.0F && age < WebSymbioteWings.FLAP_ANIM_TICKS ? age / WebSymbioteWings.FLAP_ANIM_TICKS : null);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Float spread = state.getData(SPREAD);
		if (spread == null || spread < 0.02F || state.isInvisible) {
			return;
		}
		float ease = spread * spread * (3.0F - 2.0F * spread);
		Float beat = state.getData(FLAP);
		float angle = beat != null ? FLAP_SWEEP * Mth.cos(beat * Mth.TWO_PI)
			: FLUTTER * Mth.sin(state.ageInTicks * 0.2F);
		boolean big = WebSuitGrowth.bigBody(state);
		PlayerModel parent = this.getParentModel();
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		parent.body.translateAndRotate(poseStack);
		if (big) {
			poseStack.scale(BIG_BODY_SCALE, BIG_BODY_SCALE, BIG_BODY_SCALE);
		}
		Vec3[][] wings = {pose(ease, angle, 1.0), pose(ease, angle, -1.0)};
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
			for (Vec3[] wing : wings) {
				bones(buffer, pose, wing, ease, light);
			}
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TEXTURE), (pose, buffer) -> {
			for (Vec3[] wing : wings) {
				membrane(buffer, pose, wing, light);
			}
		});
		poseStack.popPose();
	}

	/** The joints of one wing now (side +1 right, -1 left): spread to {@code ease}, swept by {@code angle}; index 0 is the shoulder. */
	private static Vec3[] pose(float ease, float angle, double side) {
		Vec3[] out = new Vec3[OPEN.length + 1];
		out[0] = mirror(S, side);
		float sin = Mth.sin(angle), cos = Mth.cos(angle);
		for (int i = 0; i < OPEN.length; i++) {
			Vec3 p = SHUT[i].lerp(OPEN[i], ease);
			// the sweep turns it about the shoulder, in the plane across the back (raising the tip is up: -Y)
			double dx = p.x - S.x, dy = p.y - S.y;
			Vec3 swept = new Vec3(S.x + dx * cos + dy * sin, S.y + dy * cos - dx * sin, p.z);
			out[i + 1] = mirror(swept, side);
		}
		return out;
	}

	private static Vec3 mirror(Vec3 p, double side) {
		return new Vec3(p.x * side, p.y, p.z);
	}

	/** The frame: the leading arm, four ribs fanning from the wrist, and spikes at the joints. */
	private static void bones(VertexConsumer buffer, PoseStack.Pose pose, Vec3[] w, float ease, int light) {
		Vec3 shoulder = w[0], elbow = w[1 + E], wrist = w[1 + W];
		tube(buffer, pose, shoulder, elbow, 2.1, 1.6, WebTubes.BLACK, light);
		tube(buffer, pose, elbow, wrist, 1.6, 1.2, WebTubes.BLACK, light);
		for (int i = F1; i <= F4; i++) {
			tube(buffer, pose, wrist, w[1 + i], 1.3, 0.35, WebTubes.BLACK, light);
		}
		// the trailing edge's last rib runs down to the hip
		tube(buffer, pose, w[1 + F4], w[1 + B], 0.8, 0.5, WebTubes.BLACK, light);
		// spikes: at the elbow and the wrist, up and back; the rib tips carry on a little
		spike(buffer, pose, elbow, new Vec3(0.2 * Math.signum(elbow.x), -1.0, 0.5), 7.0 * ease, 1.8, light);
		spike(buffer, pose, wrist, new Vec3(0.4 * Math.signum(wrist.x), -1.0, 0.25), 11.0 * ease, 2.0, light);
		for (int i = F1; i <= F4; i++) {
			Vec3 tip = w[1 + i];
			spike(buffer, pose, tip, tip.subtract(wrist), 4.0 * ease, 0.5, light);
		}
	}

	private static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, double ra, double rb, int swatch, int light) {
		WebTubes.cord(buffer, pose, a.scale(1.0 / 16.0), b.scale(1.0 / 16.0), ra / 16.0, rb / 16.0, swatch, light);
	}

	private static void spike(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, Vec3 dir, double length, double radius, int light) {
		if (length < 0.2 || dir.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 tip = at.add(dir.normalize().scale(length));
		WebTubes.prism(buffer, pose, at.scale(1.0 / 16.0), tip.scale(1.0 / 16.0), null, radius / 16.0, radius / 16.0, 0.0, 0.0, WebTubes.SHEEN, light);
	}

	/** The membrane: a fan of scalloped panels from the wrist, and the inner panels down to the shoulder and hip. */
	private static void membrane(VertexConsumer buffer, PoseStack.Pose pose, Vec3[] w, int light) {
		Vec3 shoulder = w[0], wrist = w[1 + W];
		Vec3 f1 = w[1 + F1], f2 = w[1 + F2], f3 = w[1 + F3], f4 = w[1 + F4], hip = w[1 + B];
		Vec3 m12 = scallop(f1, f2, wrist), m23 = scallop(f2, f3, wrist), m34 = scallop(f3, f4, wrist);
		// (the right wing's texture coordinates are laid from the open pose; the left mirrors them)
		boolean left = shoulder.x < 0.0;
		tri(buffer, pose, wrist, f1, m12, left, light);
		tri(buffer, pose, wrist, m12, f2, left, light);
		tri(buffer, pose, wrist, f2, m23, left, light);
		tri(buffer, pose, wrist, m23, f3, left, light);
		tri(buffer, pose, wrist, f3, m34, left, light);
		tri(buffer, pose, wrist, m34, f4, left, light);
		tri(buffer, pose, shoulder, wrist, f4, left, light);
		tri(buffer, pose, shoulder, f4, hip, left, light);
	}

	/** The point halfway between two rib tips, pulled in toward the wrist. */
	private static Vec3 scallop(Vec3 a, Vec3 b, Vec3 wrist) {
		Vec3 mid = a.add(b).scale(0.5);
		return mid.add(wrist.subtract(mid).scale(SCALLOP));
	}

	/** One panel, drawn both ways round so it shows from either side; its picture laid on by where it sits in the open wing. */
	private static void tri(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, boolean left, int light) {
		Vec3 normal = b.subtract(a).cross(c.subtract(a));
		normal = normal.lengthSqr() < 1.0E-9 ? new Vec3(0.0, 0.0, 1.0) : normal.normalize();
		Vec3 back = normal.reverse();
		vertex(buffer, pose, a, left, normal, light);
		vertex(buffer, pose, b, left, normal, light);
		vertex(buffer, pose, c, left, normal, light);
		vertex(buffer, pose, c, left, normal, light);
		vertex(buffer, pose, c, left, back, light);
		vertex(buffer, pose, b, left, back, light);
		vertex(buffer, pose, a, left, back, light);
		vertex(buffer, pose, a, left, back, light);
	}

	/**
	 * A vertex, its picture coordinates from where it is now seen as the open wing's plane (so a spread wing shows the
	 * texture as drawn, and a folding one stretches it): u = x / 64, v = (y + 32) / 72, as the generator lays the veins.
	 */
	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, boolean left, Vec3 n, int light) {
		float x = (float)(left ? -p.x : p.x);
		float u = Mth.clamp(x / 64.0F, 0.0F, 1.0F);
		float v = Mth.clamp(((float)p.y + 32.0F) / 72.0F, 0.0F, 1.0F);
		WebTubes.vertex(buffer, pose, p.scale(1.0 / 16.0), u, v, n, light);
	}

	/** The nanotime-keyed spreads of entities that are gone are dropped now and then. */
	static void forgetAll() {
		SPREADS.clear();
	}
}
