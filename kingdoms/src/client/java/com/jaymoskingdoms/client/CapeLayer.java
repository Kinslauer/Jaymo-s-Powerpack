package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Capes;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * (gb-capes) A realm cape ({@link Capes}) hung from the shoulders of whoever wears its armour, drawn as cloth: a chain of
 * strips, each a damped spring that follows the one above a little late (our own copy of the idea of Powers' cloth
 * cape, as the mods don't share code). Walking lifts it out behind, the stride sways it, a turn swings the hem out and
 * a fall floats it up; standing, it settles to hang. In the realm's field colour, edged at the sides and hem in its
 * charge colour (kingdoms/tools/gen_realm_capes.py draws the cloth).
 * <p>
 * The sim is client-side, looks only: a few floats per wearer, stepped once a game tick when drawn, drawn between ticks;
 * a wearer not drawn for {@link #KEEP_TICKS} is dropped.
 */
public final class CapeLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	// ===== the knobs ================================================================================================
	/** Strips the cape is cut into, its length and its width at the shoulders and at the hem (pixels). */
	static final int STRIPS = 5;
	static final float LENGTH = 18.0F, TOP_WIDTH = 9.0F, HEM_WIDTH = 11.0F;
	/** How far behind the body's middle it hangs (pixels: the body is 2 deep each way, the chest armour sits over it). */
	static final float BACK = 3.2F;
	/** The edge band, in the charge colour (pixels at the sides and the hem). */
	static final float EDGE = 0.8F, HEM = 1.6F;
	/** Each strip's spring toward its target a tick (0-1), and the share of its speed it loses a tick. */
	static final float STIFFNESS = 0.14F, DAMPING = 0.2F;
	/** How much of the strip above's angle a strip aims for (the rest is its own drive). */
	static final float FOLLOW = 0.62F;
	/** Walking forward lifts it out behind: radians per block a tick, at most this much. */
	static final float LEAN_PER_SPEED = 3.0F, LEAN_MAX = 1.0F;
	/** The stride sways it this much (radians, at a full stride). */
	static final float STRIDE_SWAY = 0.12F;
	/** Hanging still it sits this far off the legs (radians). */
	static final float REST = 0.08F;
	/** Turning swings the hem out this many radians per degree a tick, more further down. */
	static final float TURN_SWING = 0.045F;
	/** Falling lifts it this many radians per block a tick, most at the hem, at most this much. */
	static final float BILLOW = 1.1F, BILLOW_MAX = 0.9F;
	/** The most one strip bends from the one above (radians). */
	static final float MAX_BEND = 1.1F;
	static final int KEEP_TICKS = 100;
	// ================================================================================================================

	private static final Identifier CLOTH = JaymosKingdoms.id("textures/entity/realm_cape.png");
	/** A wearer's cape: its field and edge colours (RGB), and each strip's pitch and roll from straight down (radians). */
	record Cape(int field, int edge, float[] pitch, float[] roll) {
	}

	static final RenderStateDataKey<Cape> CAPE = RenderStateDataKey.create(() -> "jaymoskingdoms:realm_cape");

	private static final class Sim {
		final float[] pitch = new float[STRIPS], pitchV = new float[STRIPS], roll = new float[STRIPS], rollV = new float[STRIPS];
		final float[] pitchBefore = new float[STRIPS], rollBefore = new float[STRIPS];
		long tick = Long.MIN_VALUE, seen;
		float yaw;
	}

	private static final Map<Integer, Sim> SIMS = new HashMap<>();
	/** Each chest stack's cape colours, read once (ItemStack is compared by identity); {@link #NONE} for none. */
	private static final Map<net.minecraft.world.item.ItemStack, int[]> COLOURS = new java.util.WeakHashMap<>();
	private static final int[] NONE = new int[0];

	private CapeLayer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
		super(renderer);
	}

	/** Puts this layer on every humanoid renderer. */
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static CapeLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer) {
		return new CapeLayer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: {@code entity}'s cape, if its chest armour carries one, stepped and sampled. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		net.minecraft.world.item.ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		int[] colours = chest.isEmpty() ? NONE : COLOURS.computeIfAbsent(chest, s -> {
			int[] read = Capes.of(s);
			return read == null ? NONE : read;
		});
		Minecraft minecraft = Minecraft.getInstance();
		if (colours == NONE || minecraft.level == null || entity.isInvisible()) {
			state.setData(CAPE, null);
			return;
		}
		long now = minecraft.level.getGameTime();
		Sim sim = SIMS.get(entity.getId());
		if (sim == null) {
			if (SIMS.size() > 256) {
				SIMS.values().removeIf(s -> now - s.seen > KEEP_TICKS);
			}
			sim = new Sim();
			sim.yaw = entity.yBodyRot;
			SIMS.put(entity.getId(), sim);
		}
		sim.seen = now;
		if (sim.tick == Long.MIN_VALUE || now - sim.tick > 3L) {
			sim.tick = now - 1L;
		}
		while (sim.tick < now) {
			sim.tick++;
			System.arraycopy(sim.pitch, 0, sim.pitchBefore, 0, STRIPS);
			System.arraycopy(sim.roll, 0, sim.rollBefore, 0, STRIPS);
			step(sim, entity);
		}
		float partial = Mth.clamp(partialTicks, 0.0F, 1.0F);
		float[] pitches = new float[STRIPS], rolls = new float[STRIPS];
		float above = 0.0F, aboveRoll = 0.0F;
		for (int i = 0; i < STRIPS; i++) {
			float p = Mth.lerp(partial, sim.pitchBefore[i], sim.pitch[i]), r = Mth.lerp(partial, sim.rollBefore[i], sim.roll[i]);
			if (i > 0) {
				p = above + Mth.clamp(p - above, -MAX_BEND, MAX_BEND);
				r = aboveRoll + Mth.clamp(r - aboveRoll, -MAX_BEND, MAX_BEND);
			}
			pitches[i] = p;
			rolls[i] = r;
			above = p;
			aboveRoll = r;
		}
		state.setData(CAPE, new Cape(colours[0], colours[1], pitches, rolls));
	}

	/** One game tick of the chain, driven by how {@code entity} moved this tick. */
	private static void step(Sim sim, LivingEntity entity) {
		float yawRad = entity.yBodyRot * Mth.DEG_TO_RAD;
		double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
		float forward = (float)(dx * -Mth.sin(yawRad) + dz * Mth.cos(yawRad));
		float lean = Mth.clamp(forward * LEAN_PER_SPEED, 0.0F, LEAN_MAX);
		float sway = Mth.sin(entity.walkAnimation.position() * 0.6662F) * entity.walkAnimation.speed() * STRIDE_SWAY;
		float drive = REST + lean + Math.abs(sway);
		float driveRoll = sway * 0.5F;
		float turn = Mth.wrapDegrees(entity.yBodyRot - sim.yaw);
		sim.yaw = entity.yBodyRot;
		float billow = Mth.clamp((float)(entity.yo - entity.getY()) * BILLOW, 0.0F, BILLOW_MAX);
		for (int i = 0; i < STRIPS; i++) {
			float depth = (i + 1) / (float)STRIPS;
			float ownPitch = drive + billow * depth, ownRoll = driveRoll - turn * TURN_SWING * depth;
			float aimPitch = i == 0 ? ownPitch : Mth.lerp(FOLLOW, ownPitch, sim.pitch[i - 1]);
			float aimRoll = i == 0 ? ownRoll : Mth.lerp(FOLLOW, ownRoll, sim.roll[i - 1]);
			sim.pitchV[i] += (aimPitch - sim.pitch[i]) * STIFFNESS - sim.pitchV[i] * DAMPING;
			sim.rollV[i] += (aimRoll - sim.roll[i]) * STIFFNESS - sim.rollV[i] * DAMPING;
			sim.pitch[i] += sim.pitchV[i];
			sim.roll[i] += sim.rollV[i];
			// (hanging, it can't swing forward through the legs)
			if (sim.pitch[i] < 0.0F) {
				sim.pitch[i] = 0.0F;
				sim.pitchV[i] = 0.0F;
			}
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		Cape cape = state.getData(CAPE);
		if (cape == null || state.isInvisible) {
			return;
		}
		int field = ARGB.opaque(cape.field()), edge = ARGB.opaque(cape.edge());
		// the hem's points, down the chain from the shoulders (pixels, body space: y down, +z behind)
		float[] ys = new float[STRIPS + 1], zs = new float[STRIPS + 1], xs = new float[STRIPS + 1];
		ys[0] = 0.0F;
		zs[0] = BACK;
		xs[0] = 0.0F;
		float len = LENGTH / STRIPS;
		for (int i = 0; i < STRIPS; i++) {
			float pitch = cape.pitch()[i], roll = cape.roll()[i];
			ys[i + 1] = ys[i] + len * Mth.cos(pitch) * Mth.cos(roll);
			zs[i + 1] = zs[i] + len * Mth.sin(pitch) * Mth.cos(roll);
			xs[i + 1] = xs[i] + len * Mth.sin(roll);
		}
		poseStack.pushPose();
		this.getParentModel().body.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCull(CLOTH), (pose, buffer) -> {
			for (int i = 0; i < STRIPS; i++) {
				float w0 = Mth.lerp(i / (float)STRIPS, TOP_WIDTH, HEM_WIDTH) / 2.0F, w1 = Mth.lerp((i + 1) / (float)STRIPS, TOP_WIDTH, HEM_WIDTH) / 2.0F;
				float v0 = i / (float)STRIPS, v1 = (i + 1) / (float)STRIPS;
				quad(buffer, pose, xs[i] - w0, xs[i + 1] - w1, xs[i] + w0, xs[i + 1] + w1, ys[i], ys[i + 1], zs[i], zs[i + 1], 0.0F, 1.0F, v0, v1, field, lightCoords, 0.0F);
				// the edges, a hair behind the cloth so they don't flicker into it
				quad(buffer, pose, xs[i] - w0, xs[i + 1] - w1, xs[i] - w0 + EDGE, xs[i + 1] - w1 + EDGE, ys[i], ys[i + 1], zs[i], zs[i + 1], 0.0F, 0.1F, v0, v1,
					edge, lightCoords, 0.02F);
				quad(buffer, pose, xs[i] + w0 - EDGE, xs[i + 1] + w1 - EDGE, xs[i] + w0, xs[i + 1] + w1, ys[i], ys[i + 1], zs[i], zs[i + 1], 0.9F, 1.0F, v0, v1,
					edge, lightCoords, 0.02F);
			}
			// the hem band, along the bottom of the last strip
			int last = STRIPS - 1;
			float t = 1.0F - HEM / len;
			float hy = Mth.lerp(t, ys[last], ys[STRIPS]), hz = Mth.lerp(t, zs[last], zs[STRIPS]), hx = Mth.lerp(t, xs[last], xs[STRIPS]);
			float hw = Mth.lerp((last + t) / STRIPS, TOP_WIDTH, HEM_WIDTH) / 2.0F, ew = HEM_WIDTH / 2.0F;
			quad(buffer, pose, hx - hw, xs[STRIPS] - ew, hx + hw, xs[STRIPS] + ew, hy, ys[STRIPS], hz, zs[STRIPS], 0.0F, 1.0F, 0.9F, 1.0F, edge, lightCoords, 0.02F);
		});
		poseStack.popPose();
	}

	/**
	 * One strip of cloth, top edge from {@code xl0}..{@code xr0} at ({@code y0}, {@code z0}), bottom edge from {@code xl1}..{@code xr1}
	 * at ({@code y1}, {@code z1}), in pixels; {@code out} pushes it that much further back.
	 */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float xl0, float xl1, float xr0, float xr1, float y0, float y1, float z0, float z1,
		float u0, float u1, float v0, float v1, int colour, int light, float out) {
		float s = 1.0F / 16.0F;
		float dy = y1 - y0, dz = z1 - z0;
		float n = Mth.sqrt(dy * dy + dz * dz);
		// the cloth's face points back, away from the body
		float ny = n < 1.0E-4F ? 0.0F : -dz / n, nz = n < 1.0E-4F ? 1.0F : dy / n;
		z0 += out;
		z1 += out;
		vertex(buffer, pose, xl0 * s, y0 * s, z0 * s, u0, v0, colour, light, ny, nz);
		vertex(buffer, pose, xl1 * s, y1 * s, z1 * s, u0, v1, colour, light, ny, nz);
		vertex(buffer, pose, xr1 * s, y1 * s, z1 * s, u1, v1, colour, light, ny, nz);
		vertex(buffer, pose, xr0 * s, y0 * s, z0 * s, u1, v0, colour, light, ny, nz);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int colour, int light, float ny, float nz) {
		buffer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, ny, nz);
	}
}
