package com.jaymospowers.client.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.LowEndMode;
import com.jaymospowers.client.power.gravity.mixin.LensPostChainAccessor;
import com.jaymospowers.client.power.gravity.mixin.LensPostPassAccessor;
import com.jaymospowers.core.Lens;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/**
 * Light bending round a black hole, on this player's screen ({@link Lens}): the {@code lensing} post effect warps the
 * picture about the point where the nearest black hole in view stands. The effect is only asked for while one is in
 * front of the camera with nothing solid between, and never in low-end mode. Where the hole is on the screen changes
 * every frame, so the effect's {@code LensConfig} block is written here just before the effect is drawn.
 * <p>If anything about that goes wrong on a machine (a driver, another mod's renderer), the bending is switched off
 * for the rest of the session and the black hole looks as it did before.
 */
public final class LensClient {
	/** The post effect ({@code assets/jaymospowers/post_effect/lensing.json}) and its settings block. */
	public static final Identifier EFFECT = JaymosPowers.id("lensing");
	private static final String BLOCK = "LensConfig";
	/** The bending comes in and goes out over this many ticks. */
	static final int FADE_TICKS = 12;
	/** A hole farther than this from the camera is not drawn bent (blocks), nor one that would cover more than this share of the screen's height. */
	static final double SEEN_WITHIN = 96.0;
	static final float MOST_OF_SCREEN = 0.9F;

	private record Hole(Vec3 at, float radius, long from, long until) {
	}

	private static final List<Hole> HOLES = new ArrayList<>();
	/** What this frame's effect is drawn with: the centre on the screen, the reach, the screen's shape, the strength. */
	private static float centreX, centreY, reach, aspect, strength;
	private static @Nullable GpuBuffer block;
	private static boolean broken;

	private LensClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Lens.Open.TYPE, (open, context) -> context.client().execute(() -> {
			Minecraft minecraft = context.client();
			if (minecraft.level == null) {
				return;
			}
			long now = minecraft.level.getGameTime();
			Vec3 at = new Vec3(open.x(), open.y(), open.z());
			if (open.ticks() <= 0) {
				// closed early: it goes out over the fade
				HOLES.replaceAll(h -> h.at().distanceToSqr(at) < 1.0 && h.until() > now + FADE_TICKS ? new Hole(h.at(), h.radius(), h.from(), now + FADE_TICKS) : h);
				return;
			}
			HOLES.add(new Hole(new Vec3(open.x(), open.y(), open.z()), open.radius(), now, now + open.ticks()));
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HOLES.clear());
	}

	/** The post effect the screen wears this frame, or null. Works out where the nearest hole in view is. */
	public static @Nullable Identifier effect() {
		Minecraft minecraft = Minecraft.getInstance();
		if (broken || HOLES.isEmpty() || minecraft.level == null || LowEndMode.on()) {
			return null;
		}
		long now = minecraft.level.getGameTime();
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Matrix4f view = camera.getViewRotationProjectionMatrix(new Matrix4f());
		int width = minecraft.getWindow().getWidth(), height = minecraft.getWindow().getHeight();
		if (width <= 0 || height <= 0) {
			return null;
		}
		Hole best = null;
		double bestAt = SEEN_WITHIN * SEEN_WITHIN;
		for (Iterator<Hole> it = HOLES.iterator(); it.hasNext();) {
			Hole hole = it.next();
			if (now >= hole.until()) {
				it.remove();
				continue;
			}
			double far = hole.at().distanceToSqr(eye);
			if (far < bestAt) {
				bestAt = far;
				best = hole;
			}
		}
		if (best == null) {
			return null;
		}
		Vec3 d = best.at().subtract(eye);
		Vector4f centre = view.transform(new Vector4f((float) d.x, (float) d.y, (float) d.z, 1.0F));
		if (centre.w <= 0.5F) {
			// behind the camera, or on top of it
			return null;
		}
		org.joml.Vector3fc up = camera.upVector();
		Vector4f edge = view.transform(new Vector4f((float) d.x + up.x() * best.radius(), (float) d.y + up.y() * best.radius(),
			(float) d.z + up.z() * best.radius(), 1.0F));
		if (edge.w <= 0.01F) {
			return null;
		}
		float x = centre.x / centre.w * 0.5F + 0.5F, y = centre.y / centre.w * 0.5F + 0.5F;
		float size = Math.min(MOST_OF_SCREEN, Math.abs(edge.y / edge.w - centre.y / centre.w) * 0.5F);
		float shape = width / (float) height;
		if (x < -size / shape || x > 1.0F + size / shape || y < -size || y > 1.0F + size || size < 0.004F) {
			return null;
		}
		// nothing solid between the eye and the hole
		HitResult wall = minecraft.level.clip(new ClipContext(eye, best.at(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
			net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (wall.getType() != HitResult.Type.MISS && wall.getLocation().distanceToSqr(best.at()) > 1.0) {
			return null;
		}
		float in = (now - best.from() + partial) / FADE_TICKS, out = (best.until() - now - partial) / FADE_TICKS;
		centreX = x;
		centreY = y;
		reach = size;
		aspect = shape;
		strength = Math.clamp(Math.min(in, out), 0.0F, 1.0F);
		return strength <= 0.0F ? null : EFFECT;
	}

	/** Just before the frame's post effects are drawn: this frame's numbers go into the lensing effect's settings block. */
	public static void write(List<PostChain> applied) {
		if (broken) {
			return;
		}
		try {
			for (PostChain chain : applied) {
				if (!EFFECT.equals(chain.id())) {
					continue;
				}
				for (PostPass pass : ((LensPostChainAccessor) chain).jaymospowers$passes()) {
					Map<String, GpuBuffer> uniforms = ((LensPostPassAccessor) pass).jaymospowers$customUniforms();
					GpuBuffer had = uniforms.get(BLOCK);
					if (had == null) {
						continue;
					}
					if (had != block || had.isClosed()) {
						// the effect was (re)loaded: its block is made once and never written again, so ours takes its place
						block = RenderSystem.getDevice().createBuffer(() -> "Lensing settings", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, had.size());
						uniforms.put(BLOCK, block);
						had.close();
					}
					try (MemoryStack stack = MemoryStack.stackPush()) {
						ByteBuffer bytes = Std140Builder.onStack(stack, (int) block.size()).putVec2(centreX, centreY).putFloat(reach).putFloat(aspect)
							.putFloat(strength).get();
						RenderSystem.getDevice().createCommandEncoder().writeToBuffer(block.slice(0, bytes.remaining()), bytes);
					}
				}
			}
		} catch (Throwable e) {
			broken = true;
			JaymosPowers.LOGGER.warn("Light bending round black holes is off for this session", e);
		}
	}
}
