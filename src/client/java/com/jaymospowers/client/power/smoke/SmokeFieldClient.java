package com.jaymospowers.client.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.smoke.SmokeNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the smoke the server sends ({@link SmokeNet.Field}, every few ticks) as soft volume: each block of smoke is
 * one or two big camera-facing puffs (a 2x2 sheet of ragged puffs, {@code textures/entity/smoke/puff.png}), slowly
 * turning and bobbing, overlapping their neighbours so a cloud reads as one mass, lit by the world's light (dark at
 * night, glowing by a torch), pale grey or sooty black by what made it and what it passed through. Between updates each block's thickness eases from the last value to the new one.
 * Your own smoke is drawn faint (you see through it). With your head in someone else's smoke the screen greys out.
 */
public final class SmokeFieldClient {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/smoke/puff.png");
	/** Ticks between the server's updates (matches the server's {@code SmokeField.SYNC}). */
	static final int SYNC = 4;
	/** Thickness of a byte step: the server sends 0-255 of its cap of 3. */
	static final float UNIT = 3.0F / 255.0F;
	/** Blocks from the camera that are drawn. */
	static final int RANGE = 56;
	/** How see-through your own smoke is drawn. */
	static final float OWN_ALPHA = 0.5F;
	/** The grey your view goes in thick smoke, and the thickness that greys it out completely. */
	static final int VIEW_GREY = 0x8E9094;
	static final float VIEW_FULL = 1.6F;
	static final float VIEW_MAX_ALPHA = 0.93F;

	private static Long2FloatOpenHashMap before = new Long2FloatOpenHashMap();
	private static Long2FloatOpenHashMap now = new Long2FloatOpenHashMap();
	private static LongOpenHashSet yours = new LongOpenHashSet();
	/** Each block's soot (0 pale grey to 1 black), from the latest update. */
	private static Long2FloatOpenHashMap soot = new Long2FloatOpenHashMap();
	private static int sinceUpdate;
	private static int age;

	private SmokeFieldClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SmokeNet.Field.TYPE, (field, context) -> receive(field));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				clear();
				return;
			}
			if (!minecraft.isPaused()) {
				sinceUpdate++;
				age++;
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(SmokeFieldClient::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("smoke_view"), SmokeFieldClient::renderView);
	}

	private static void clear() {
		before = new Long2FloatOpenHashMap();
		now = new Long2FloatOpenHashMap();
		yours = new LongOpenHashSet();
		soot = new Long2FloatOpenHashMap();
	}

	private static void receive(SmokeNet.Field field) {
		byte[] cells = field.cells();
		Long2FloatOpenHashMap next = new Long2FloatOpenHashMap(cells.length / SmokeNet.Field.STRIDE * 2);
		LongOpenHashSet mine = new LongOpenHashSet();
		Long2FloatOpenHashMap soots = new Long2FloatOpenHashMap();
		int ox = BlockPos.getX(field.origin());
		int oy = BlockPos.getY(field.origin());
		int oz = BlockPos.getZ(field.origin());
		for (int i = 0; i + SmokeNet.Field.STRIDE <= cells.length; i += SmokeNet.Field.STRIDE) {
			long key = BlockPos.asLong(ox + cells[i], oy + cells[i + 1], oz + cells[i + 2]);
			next.put(key, (cells[i + 3] & 0xFF) * UNIT);
			if ((cells[i + 4] & SmokeNet.Field.YOURS) != 0) {
				mine.add(key);
			}
			soots.put(key, ((cells[i + 4] & 0xFF) >> 1) / 127.0F);
		}
		before = now;
		now = next;
		yours = mine;
		soot = soots;
		sinceUpdate = 0;
	}

	/** How thick the smoke in the block {@code key} is right now, eased between the last two updates. */
	static float density(long key, float partial) {
		float t = Mth.clamp((sinceUpdate + partial) / SYNC, 0.0F, 1.0F);
		return Mth.lerp(t, before.get(key), now.get(key));
	}

	// ---------------------------------------------------------------- the view in smoke

	private static void renderView(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || now.isEmpty() && before.isEmpty()) {
			return;
		}
		long key = minecraft.gameRenderer.mainCamera().blockPosition().asLong();
		if (yours.contains(key)) {
			return;
		}
		float d = density(key, delta.getGameTimeDeltaPartialTick(false));
		if (d < 0.08F) {
			return;
		}
		float alpha = Math.min(VIEW_MAX_ALPHA, d / VIEW_FULL * VIEW_MAX_ALPHA);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int)(alpha * 255.0F) << 24 | VIEW_GREY);
	}

	// ---------------------------------------------------------------- the smoke

	private static void render(LevelRenderContext context) {
		if (now.isEmpty() && before.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Quaternionf facing = context.levelState().cameraRenderState.orientation;
		float time = age + partial;
		// gather the puffs first, then draw them in one go
		int most = (now.size() + before.size()) * 3;
		float[] puffs = new float[most * PUFF];
		int[] lights = new int[most];
		int count = 0;
		LongOpenHashSet done = new LongOpenHashSet(now.size() + before.size());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (Long2FloatOpenHashMap map : new Long2FloatOpenHashMap[] {now, before}) {
			for (LongIterator it = map.keySet().iterator(); it.hasNext();) {
				long key = it.nextLong();
				if (!done.add(key)) {
					continue;
				}
				float d = density(key, partial);
				if (d < 0.03F) {
					continue;
				}
				int x = BlockPos.getX(key);
				int y = BlockPos.getY(key);
				int z = BlockPos.getZ(key);
				if (Math.abs(x + 0.5 - camera.x) > RANGE || Math.abs(y + 0.5 - camera.y) > RANGE || Math.abs(z + 0.5 - camera.z) > RANGE) {
					continue;
				}
				int light = LightCoordsUtil.getLightCoords(minecraft.level, cursor.set(x, y, z));
				float own = yours.contains(key) ? OWN_ALPHA : 1.0F;
				int n = d >= 1.8F ? 3 : d >= 0.6F ? 2 : 1;
				for (int i = 0; i < n; i++) {
					long h = mix(key * 31L + i);
					float phase = (h & 0xFFFF) / 65535.0F * Mth.TWO_PI;
					float px = x + 0.5F + ((h >>> 16 & 0xFF) / 255.0F - 0.5F) * 0.7F + 0.12F * Mth.sin(time * 0.021F + phase);
					float py = y + 0.5F + ((h >>> 24 & 0xFF) / 255.0F - 0.5F) * 0.6F + 0.15F * Mth.sin(time * 0.017F + phase * 1.7F);
					float pz = z + 0.5F + ((h >>> 32 & 0xFF) / 255.0F - 0.5F) * 0.7F + 0.12F * Mth.cos(time * 0.019F + phase);
					double dx = px - camera.x;
					double dy = py - camera.y;
					double dz = pz - camera.z;
					double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
					// right by the camera a puff would be one flat sprite filling the view: let the grey view do that
					float near = (float)Mth.clamp((distance - 0.4) / 1.2, 0.0, 1.0);
					float thin = Math.min(1.0F, d / 0.3F);
					float alpha = Math.min(0.85F, 0.22F + d * 0.35F) * thin * near * own / (1.0F + 0.35F * (n - 1));
					if (alpha < 0.01F) {
						continue;
					}
					int o = count++ * PUFF;
					puffs[o] = (float)dx;
					puffs[o + 1] = (float)dy;
					puffs[o + 2] = (float)dz;
					puffs[o + 3] = 0.7F + 0.32F * Math.min(d, 2.5F) + 0.06F * Mth.sin(time * 0.03F + phase);
					puffs[o + 4] = phase + time * 0.004F * ((h & 0x10000) == 0 ? 1.0F : -1.0F);
					puffs[o + 5] = alpha;
					puffs[o + 6] = h >>> 40 & 3;
					puffs[o + 7] = (h >>> 42 & 0x1F) / 31.0F * 0.15F + 0.85F * (1.0F - soot.get(key));
					lights[count - 1] = light;
				}
			}
		}
		if (count == 0) {
			return;
		}
		int total = count;
		Quaternionf camera0 = new Quaternionf(facing);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(TEXTURE, false),
			(pose, buffer) -> {
				Quaternionf turn = new Quaternionf();
				Vector3f corner = new Vector3f();
				for (int p = 0; p < total; p++) {
					int o = p * PUFF;
					turn.set(camera0).rotateZ(puffs[o + 4]);
					puff(buffer, pose, turn, corner, puffs[o], puffs[o + 1], puffs[o + 2], puffs[o + 3], puffs[o + 5],
						lights[p], (int)puffs[o + 6], puffs[o + 7]);
				}
			});
	}

	/** Floats kept per puff: x, y, z off the camera, half-size, turn, alpha, sheet cell, shade. */
	private static final int PUFF = 8;

	private static void puff(VertexConsumer buffer, PoseStack.Pose pose, Quaternionf turn, Vector3f corner, float x, float y, float z, float half,
			float alpha, int light, int cell, float shade) {
		float u0 = (cell & 1) * 0.5F;
		float v0 = (cell >> 1) * 0.5F;
		int grey = (int)(40 + 196 * shade);
		int color = (int)(alpha * 255.0F) << 24 | grey << 16 | grey << 8 | Math.min(255, grey + 6);
		vertex(buffer, pose, turn, corner, x, y, z, -half, -half, color, u0, v0 + 0.5F, light);
		vertex(buffer, pose, turn, corner, x, y, z, half, -half, color, u0 + 0.5F, v0 + 0.5F, light);
		vertex(buffer, pose, turn, corner, x, y, z, half, half, color, u0 + 0.5F, v0, light);
		vertex(buffer, pose, turn, corner, x, y, z, -half, half, color, u0, v0, light);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Quaternionf turn, Vector3f corner, float x, float y, float z, float cx,
			float cy, int color, float u, float v, int light) {
		turn.transform(corner.set(cx, cy, 0.0F));
		buffer.addVertex(pose, x + corner.x, y + corner.y, z + corner.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	/** A quick 64-bit mix, for each puff's own offsets. */
	private static long mix(long h) {
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return h;
	}
}
