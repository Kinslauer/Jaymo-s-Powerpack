package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.astronomy.SunMoon;
import com.jaymoskingdoms.client.DarkNights;
import com.jaymoskingdoms.disasters.Storms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Layered, thicker clouds (Andy, on the weather patch). The game's flat single sheet of cloud is not drawn; instead two
 * decks hang in the sky, a low one and a high one, each a stack of thin slices of the same cloud field, each slice a
 * little higher, a little darker below and a little to one side of the one under it, so a cloud has a body, a bright top and
 * a grey belly, and the decks slide past each other at their own pace on the wind ({@link Storms#clientWindAngle}).
 * A third, faint deck of wisps rides above them. Some squares of a deck bump up with extra slices, more as rain gathers, and
 * fair weather leaves clear patches that a storm closes; each day rolls the decks' heights, pace and opacity afresh.
 * Fair weather shows scattered cloud ({@code cloud_light}); as rain gathers the heavy field ({@code cloud_heavy}) fades
 * in and the decks darken to grey. Dawn and dusk warm the undersides ({@link SkiesClient#glow}), night makes them dark.
 * The textures come from {@code tools/cloud_layers_art.py}.
 *
 * <p>The clouds are flat quads in the game's own shadow layer, as the sun shadows are ({@link SunShade}); each is a
 * square of {@link #CELL} blocks showing a window of the field that moves with the wind. They go (and the game's own
 * clouds come back) with the cloud setting off, in a shader pack's world, and outside the Overworld. Low-end mode keeps
 * one deck of two slices.
 */
public final class CloudDecks {
	// ---- tuning ----
	/** The decks' heights above sea level (blocks), the slices in each, and the gap between one slice and the next. */
	static final float[] DECK_HEIGHT = {150.0F, 188.0F, 236.0F};
	static final int SLICES = 4;
	/** The third deck is a faint, wispy one: its slices and its slice opacity. */
	static final int HIGH_SLICES = 2;
	static final float HIGH_ALPHA = 0.18F;
	static final float SLICE_GAP = 1.6F;
	/** Each slice leans this far (blocks) along the wind from the one below, which is what gives a cloud its sloping side. */
	static final float LEAN = 2.5F;
	/** A square of cloud is this many blocks across, and the sky is drawn this many squares out from the camera. */
	static final int CELL = 64, REACH = 6;
	/** The field is this many squares across before it repeats (its pixels are eight blocks each). */
	static final int FIELD_CELLS = 16;
	/** How opaque one slice is at most, and how dark the bottom slice is against the top one. */
	static final float SLICE_ALPHA = 0.36F, BELLY = 0.62F;
	/** How far the decks drift (blocks a tick) in still air and for each unit of wind speed, and how much faster the high deck goes. */
	static final float DRIFT = 0.03F, DRIFT_WIND = 1.2F;
	/** How fast each deck goes against the low one (the wispy deck races along). */
	static final float[] DECK_PACE = {1.0F, 1.35F, 1.8F};
	/** Lumpy tops: how many slices a bump may add above a deck's top, and the share of squares that bump in fair weather and in a storm. */
	static final int BUMP_SLICES = 3;
	static final float BUMP_FAIR = 0.12F, BUMP_STORM = 0.5F;
	/** The day's roll: how far a deck's height may move (blocks), the range of its drift speed and of its slice opacity, and how fast a change eases in (share of the way a tick). */
	static final float ROLL_HEIGHT = 12.0F, ROLL_DRIFT_MIN = 0.7F, ROLL_DRIFT_MAX = 1.4F, ROLL_ALPHA_MIN = 0.8F, ROLL_ALPHA_MAX = 1.15F;
	static final float EASE = 1.0F / 400.0F;
	/** Gaps: the share of squares cleared in fair weather (a storm closes them), and how wide the fade is at a gap's edge. */
	static final float GAP_FAIR = 0.22F, GAP_SOFT = 0.12F;
	/** Rain at which the sky is fully overcast (the heavy field is all that shows), and how grey the clouds go in it. */
	static final float OVERCAST_RAIN = 0.9F, GREY = 0.5F;
	/** How much of the glow tints an underside, and the darkness of the clouds at night. */
	static final float GLOW_TINT = 0.7F, NIGHT = 0.16F;
	/** How far in from the field's edge a square's picture is taken, so no seam shows between squares. */
	static final float INSET = 0.4F / 128.0F;
	/** How often a shader pack is looked for (ticks). */
	static final int SHADER_CHECK = 40;

	private static final int FULL_BRIGHT = 15728880;
	private static final Identifier LIGHT = JaymosKingdoms.id("textures/skies/cloud_light.png");
	private static final Identifier HEAVY = JaymosKingdoms.id("textures/skies/cloud_heavy.png");

	/** Whether the decks stand in for the game's clouds (set each tick; the cloud renderer asks). */
	private static boolean active;
	private static boolean shaders;
	private static int clock;
	/** How far the decks have drifted so far (blocks). */
	private static final int DECKS = DECK_HEIGHT.length;
	private static final double[] driftX = new double[DECKS], driftZ = new double[DECKS];
	private static final float[] speedX = new float[DECKS], speedZ = new float[DECKS];
	/** Today's roll for each deck, eased toward its target: a height offset and drift and opacity multipliers. */
	private static final float[] heightOff = new float[DECKS], driftMul = new float[DECKS], alphaMul = new float[DECKS];
	private static final float[] heightTo = new float[DECKS], driftTo = new float[DECKS], alphaTo = new float[DECKS];
	/** The day the roll is for, the one before it, and how far the gap pattern has crossfaded between them (0 to 1). */
	private static long rolledDay = Long.MIN_VALUE, prevDay;
	private static float gapMix = 1.0F;

	private CloudDecks() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			active = false;
			java.util.Arrays.fill(driftX, 0.0);
			java.util.Arrays.fill(driftZ, 0.0);
			rolledDay = Long.MIN_VALUE;
		});
		ClientTickEvents.END_CLIENT_TICK.register(CloudDecks::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(CloudDecks::render);
	}

	/** (CloudRendererMixin) Whether our decks are up, so the game's own cloud sheet is left out. */
	public static boolean replaces() {
		return active;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (clock++ % SHADER_CHECK == 0) {
			shaders = DarkNights.shaderPackOn();
		}
		active = level != null && !shaders && level.dimension() == Level.OVERWORLD && minecraft.options.getCloudStatus() != CloudStatus.OFF;
		if (!active || minecraft.isPaused()) {
			return;
		}
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		boolean first = rolledDay == Long.MIN_VALUE;
		if (day != rolledDay) {
			prevDay = first ? day : rolledDay;
			rolledDay = day;
			gapMix = first ? 1.0F : 0.0F;
			for (int d = 0; d < DECKS; d++) {
				heightTo[d] = (hash((int)day, d, 1) * 2.0F - 1.0F) * ROLL_HEIGHT;
				driftTo[d] = Mth.lerp(hash((int)day, d, 2), ROLL_DRIFT_MIN, ROLL_DRIFT_MAX);
				alphaTo[d] = Mth.lerp(hash((int)day, d, 3), ROLL_ALPHA_MIN, ROLL_ALPHA_MAX);
				if (first) {
					heightOff[d] = heightTo[d];
					driftMul[d] = driftTo[d];
					alphaMul[d] = alphaTo[d];
				}
			}
		}
		gapMix = Math.min(1.0F, gapMix + EASE / 3.0F); // linear, so the crossfade takes about a minute like the eased rolls
		float speed = DRIFT + Storms.clientWindSpeed * DRIFT_WIND;
		for (int d = 0; d < DECKS; d++) {
			heightOff[d] += (heightTo[d] - heightOff[d]) * EASE;
			driftMul[d] += (driftTo[d] - driftMul[d]) * EASE;
			alphaMul[d] += (alphaTo[d] - alphaMul[d]) * EASE;
			float pace = speed * DECK_PACE[d] * driftMul[d];
			speedX[d] = Mth.cos(Storms.clientWindAngle) * pace;
			speedZ[d] = Mth.sin(Storms.clientWindAngle) * pace;
			driftX[d] += speedX[d];
			driftZ[d] += speedZ[d];
		}
	}

	/** A steady pseudo-random number from 0 up to 1 for three whole numbers. */
	private static float hash(int a, int b, int c) {
		int h = a * 374761393 + b * 668265263 + c * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 16777216.0F;
	}

	static boolean lowEnd() {
		try {
			return (boolean)Class.forName("com.jaymospowers.client.LowEndMode").getMethod("on").invoke(null);
		} catch (ReflectiveOperationException | LinkageError e) {
			return false;
		}
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (!active || level == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float rain = Mth.clamp(Math.max(level.getRainLevel(partial), Storms.clientRain), 0.0F, 1.0F);
		float heavy = Mth.clamp(rain / OVERCAST_RAIN, 0.0F, 1.0F);
		// how bright the day is, from the Sun's height
		float day = Mth.clamp(((float)SunMoon.sun(level, camera).y + 0.12F) / 0.35F, 0.0F, 1.0F);
		float light = Mth.lerp(day, NIGHT, 1.0F) * Mth.lerp(heavy, 1.0F, GREY);
		float glow = SkiesClient.glow * GLOW_TINT;
		boolean low = lowEnd();
		int decks = low ? 1 : DECKS;
		PoseStack poseStack = context.poseStack();
		float sea = level.getSeaLevel();
		for (int deck = 0; deck < decks; deck++) {
			boolean wisp = deck == DECKS - 1;
			int slices = wisp ? HIGH_SLICES : low ? 2 : SLICES;
			// the low deck is the one that fills with rain's grey; the higher ones stay thin and bright
			float cover = wisp ? 0.0F : deck == 0 ? heavy : heavy * 0.5F;
			float dx = (float)(driftX[deck] + speedX[deck] * partial), dz = (float)(driftZ[deck] + speedZ[deck] * partial);
			// a deck is never put below the camera's own head on a mountain top
			float base = Math.max(sea + DECK_HEIGHT[deck] + heightOff[deck], (float)camera.y + 40.0F + deck * 30.0F);
			float alpha = (wisp ? HIGH_ALPHA : SLICE_ALPHA * (slices <= 2 ? 1.6F : 1.0F)) * alphaMul[deck];
			// bumps rise on the full decks; the wispy one and low-end mode stay flat
			int bumps = wisp || low ? 0 : BUMP_SLICES;
			float bumpChance = Mth.lerp(heavy, BUMP_FAIR, BUMP_STORM);
			float gapChance = GAP_FAIR * (1.0F - heavy);
			if (cover < 1.0F) {
				deck(context, poseStack, LIGHT, camera, base, dx, dz, slices, bumps, bumpChance, gapChance, light, glow, alpha * (1.0F - cover), deck, wisp);
			}
			if (cover > 0.0F) {
				deck(context, poseStack, HEAVY, camera, base, dx, dz, slices, bumps, bumpChance, gapChance, light, glow, alpha * cover, deck, wisp);
			}
		}
	}

	private static void deck(LevelRenderContext context, PoseStack poseStack, Identifier texture, Vec3 camera, float base, float dx, float dz, int slices, int bumps,
		float bumpChance, float gapChance, float light, float glow, float alpha, int deck, boolean wisp) {
		if (alpha <= 0.002F) {
			return;
		}
		float leanX = Mth.cos(Storms.clientWindAngle) * LEAN, leanZ = Mth.sin(Storms.clientWindAngle) * LEAN;
		int day = (int)rolledDay, before = (int)prevDay;
		float mix = gapMix;
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityShadow(texture), (pose, buffer) -> {
			for (int slice = 0; slice < slices + bumps; slice++) {
				boolean bump = slice >= slices;
				float top = bump || slices == 1 ? 1.0F : slice / (float)(slices - 1);
				// underside grey and warm at the horizon's glow, top bright; the wisps are near white all through
				float shade = Mth.lerp(top, wisp ? 0.9F : BELLY, 1.0F) * light;
				float warm = glow * (wisp ? 1.0F : 1.0F - top);
				float red = shade, green = shade * Mth.lerp(warm, 1.0F, 0.74F), blue = shade * Mth.lerp(warm, 1.0F, 0.52F);
				float y = base + slice * SLICE_GAP - (float)camera.y;
				float ox = dx + slice * leanX, oz = dz + slice * leanZ;
				// the squares are fixed to the field, which slides: find which ones are over the camera now
				int cx = Mth.floor((camera.x - ox) / CELL), cz = Mth.floor((camera.z - oz) / CELL);
				for (int i = -REACH; i <= REACH; i++) {
					for (int j = -REACH; j <= REACH; j++) {
						if (i * i + j * j > (REACH + 0.5F) * (REACH + 0.5F)) {
							continue;
						}
						int gx = cx + i, gz = cz + j;
						int fx = Math.floorMod(gx, FIELD_CELLS), fz = Math.floorMod(gz, FIELD_CELLS);
						if (bump) {
							// a bumped square adds up to BUMP_SLICES slices; more squares bump as the chance rises
							float h = hash(fx, fz, 40 + deck);
							if (h >= bumpChance || slice - slices >= 1 + (int)(h / bumpChance * bumps)) {
								continue;
							}
						}
						// clear patches: the day's pattern, crossfaded from yesterday's at the roll
						float gap = Mth.lerp(mix, gapFade(fx, fz, deck, before, gapChance), gapFade(fx, fz, deck, day, gapChance));
						if (gap <= 0.01F) {
							continue;
						}
						int colour = ARGB.colorFromFloat(alpha * gap, red, green, blue);
						float x0 = (float)(gx * CELL + ox - camera.x), z0 = (float)(gz * CELL + oz - camera.z);
						float u0 = fx / (float)FIELD_CELLS + INSET, v0 = fz / (float)FIELD_CELLS + INSET;
						float step = 1.0F / FIELD_CELLS - INSET * 2.0F;
						flat(buffer, pose, x0, z0, x0 + CELL, z0 + CELL, y, colour, u0, v0, u0 + step, v0 + step);
					}
				}
			}
		});
	}

	/** How much of a square shows (0 cleared, 1 whole) in the gap pattern rolled for a day. */
	private static float gapFade(int fx, int fz, int deck, int day, float gapChance) {
		float fade = Mth.clamp((hash(fx + day * 31, fz, 60 + deck) - gapChance) / GAP_SOFT, 0.0F, 1.0F);
		// the soft edges close with the gaps, so a full storm leaves no square even partly open
		return 1.0F - (1.0F - fade) * (gapChance / GAP_FAIR);
	}

	/** A level quad facing down, since clouds are seen from under. */
	private static void flat(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y, int colour, float u0, float v0, float u1,
		float v1) {
		vertex(buffer, pose, x0, y, z0, colour, u0, v0);
		vertex(buffer, pose, x1, y, z0, colour, u1, v0);
		vertex(buffer, pose, x1, y, z1, colour, u1, v1);
		vertex(buffer, pose, x0, y, z1, colour, u0, v1);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int colour, float u, float v) {
		buffer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0.0F, -1.0F, 0.0F);
	}
}
