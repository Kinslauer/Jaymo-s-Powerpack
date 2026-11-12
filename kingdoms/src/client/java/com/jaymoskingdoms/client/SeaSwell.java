package com.jaymoskingdoms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The swell (Andy's ocean pick-list, "A higher, wavy sea"): out on open sea, long low waves roll across the water's
 * face, drawn as a sheet of water whose crests rise above the flat surface and move with time. Higher in rain and
 * highest under a thunderstorm, a little higher over the deep ocean; none on rivers, lakes or anything but open sea, and
 * none where a block stands on the water. Only the look: the water itself stays as it is (the ships already roll on
 * the swell, {@code ShipRenderer}).
 */
public final class SeaSwell {
	/** How far round the camera the swell is drawn (blocks), the size of its cells, and how near the sea the camera must be. */
	static final int RANGE = 44, CELL = 2, NEAR_SEA = 40;
	/** Crest height (blocks): fair, in rain, in a storm; deep water's share more. */
	static final float FAIR = 0.22F, RAIN = 0.4F, STORM = 0.8F, DEEP = 1.35F;
	/** The two wave trains: wavelengths (blocks), speeds (blocks a tick), directions (degrees), shares. */
	static final float LONG_WAVE = 22.0F, SHORT_WAVE = 9.0F, LONG_SPEED = 0.09F, SHORT_SPEED = 0.06F, LONG_DIR = 35.0F, SHORT_DIR = 80.0F,
		LONG_SHARE = 0.65F, SHORT_SHARE = 0.35F;
	/** How see-through the swell's water is (0..255), and how far above the still surface its trough lies. */
	static final int ALPHA = 0xB4;
	static final float LIFT = 0.015F;
	/** Open water is looked for again every this many ticks. */
	static final int LOOK_EVERY = 40;
	static final Identifier WATER = Identifier.withDefaultNamespace("block/water_still");

	/** Columns (packed x, z) with open sea at the surface, round {@link #lookedAt}. */
	private static final LongOpenHashSet OPEN = new LongOpenHashSet();
	private static final LongOpenHashSet DEEP_WATER = new LongOpenHashSet();
	private static BlockPos lookedAt = BlockPos.ZERO;
	private static long lookedTick = Long.MIN_VALUE;
	private static Level lookedLevel;

	private SeaSwell() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(SeaSwell::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || level.dimension() != Level.OVERWORLD) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		int sea = level.getSeaLevel();
		float surface = sea - 1 + 8.0F / 9.0F;
		if (camera.y < surface || camera.y > sea + NEAR_SEA) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long tick = level.getGameTime();
		look(level, BlockPos.containing(camera.x, sea, camera.z), sea, tick);
		if (OPEN.isEmpty()) {
			return;
		}
		float rain = level.getRainLevel(partial), storm = level.getThunderLevel(partial);
		float height = Mth.lerp(storm, Mth.lerp(rain, FAIR, RAIN), STORM);
		float t = tick + partial;
		TextureAtlasSprite sprite = minecraft.getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, WATER));
		BlockPos here = BlockPos.containing(camera.x, surface, camera.z);
		int rgb = BiomeColors.getAverageWaterColor(level, here);
		int argb = ALPHA << 24 | rgb & 0xFFFFFF;
		int light = net.minecraft.util.LightCoordsUtil.getLightCoords(level, here.above());
		int cx = Mth.floor(camera.x / CELL) * CELL, cz = Mth.floor(camera.z / CELL) * CELL;
		PoseStack poseStack = context.poseStack();
		float u0 = sprite.getU(0.0F), u1 = sprite.getU(1.0F), v0 = sprite.getV(0.0F), v1 = sprite.getV(1.0F);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
			for (int x = cx - RANGE; x < cx + RANGE; x += CELL) {
				for (int z = cz - RANGE; z < cz + RANGE; z += CELL) {
					if (!OPEN.contains(BlockPos.asLong(x, 0, z))) {
						continue;
					}
					float a = DEEP_WATER.contains(BlockPos.asLong(x, 0, z)) ? height * DEEP : height;
					float h00 = crest(x, z, t, a), h10 = crest(x + CELL, z, t, a), h11 = crest(x + CELL, z + CELL, t, a), h01 = crest(x, z + CELL, t, a);
					if (h00 <= 0.0F && h10 <= 0.0F && h11 <= 0.0F && h01 <= 0.0F) {
						continue;
					}
					float y = surface + LIFT;
					float x0 = (float)(x - camera.x), x1 = x0 + CELL, z0 = (float)(z - camera.z), z1 = z0 + CELL;
					float base = (float)(y - camera.y);
					// a rough normal from the slope across the cell
					float nx = (h00 + h01 - h10 - h11) / (2.0F * CELL), nz = (h00 + h10 - h01 - h11) / (2.0F * CELL);
					float len = Mth.sqrt(nx * nx + 1.0F + nz * nz);
					vertex(buffer, pose, argb, light, x0, base + h00, z0, u0, v0, nx / len, 1.0F / len, nz / len);
					vertex(buffer, pose, argb, light, x0, base + h01, z1, u0, v1, nx / len, 1.0F / len, nz / len);
					vertex(buffer, pose, argb, light, x1, base + h11, z1, u1, v1, nx / len, 1.0F / len, nz / len);
					vertex(buffer, pose, argb, light, x1, base + h10, z0, u1, v0, nx / len, 1.0F / len, nz / len);
				}
			}
		});
	}

	/** How high the swell's crest stands above the still surface at (x, z) at time {@code t}, for crests of {@code a} (0 in its troughs). */
	static float crest(float x, float z, float t, float a) {
		float l = wave(x, z, t, LONG_WAVE, LONG_SPEED, LONG_DIR), s = wave(x, z, t, SHORT_WAVE, SHORT_SPEED, SHORT_DIR);
		float h = (LONG_SHARE * l + SHORT_SHARE * s) * a;
		return Math.max(0.0F, h);
	}

	private static float wave(float x, float z, float t, float length, float speed, float dirDegrees) {
		float d = dirDegrees * Mth.DEG_TO_RAD;
		float along = x * Mth.cos(d) + z * Mth.sin(d);
		return Mth.sin((along - speed * t) * Mth.TWO_PI / length);
	}

	/** Which columns round {@code at} are open sea at the surface (looked again now and then, or when the camera moves on). */
	private static void look(ClientLevel level, BlockPos at, int sea, long tick) {
		if (level == lookedLevel && tick - lookedTick < LOOK_EVERY && at.distManhattan(lookedAt) < CELL * 4) {
			return;
		}
		lookedLevel = level;
		lookedTick = tick;
		lookedAt = at;
		OPEN.clear();
		DEEP_WATER.clear();
		int cx = Mth.floor(at.getX() / (float)CELL) * CELL, cz = Mth.floor(at.getZ() / (float)CELL) * CELL;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = cx - RANGE - CELL; x < cx + RANGE + CELL; x += CELL) {
			for (int z = cz - RANGE - CELL; z < cz + RANGE + CELL; z += CELL) {
				if (!open(level, p, x, z, sea) || !open(level, p, x + CELL - 1, z + CELL - 1, sea)) {
					continue;
				}
				p.set(x, sea - 1, z);
				var biome = level.getBiome(p);
				if (!biome.is(BiomeTags.IS_OCEAN)) {
					continue;
				}
				OPEN.add(BlockPos.asLong(x, 0, z));
				if (biome.is(BiomeTags.IS_DEEP_OCEAN)) {
					DEEP_WATER.add(BlockPos.asLong(x, 0, z));
				}
			}
		}
	}

	/** Still water at the sea's top with nothing on it. */
	private static boolean open(ClientLevel level, BlockPos.MutableBlockPos p, int x, int z, int sea) {
		p.set(x, sea - 1, z);
		if (!level.hasChunkAt(p) || !level.getBlockState(p).is(Blocks.WATER) || !level.getFluidState(p).isSource()) {
			return false;
		}
		p.set(x, sea, z);
		return level.getBlockState(p).isAir();
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int argb, int light, float x, float y, float z, float u, float v, float nx,
		float ny, float nz) {
		buffer.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
