package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.UltimateCinematic;
import com.jaymospowers.client.fx.MagicFxClient;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.power.chrono.ChronoWormhole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Chrono's wormhole, drawn by code (no painted texture; every shape is the beacon beam's own texture, tinted): the open hole
 * in the world, and the film that plays when you run into it ({@link UltimateFilm#WORMHOLE}) or drag a foe down it
 * ({@link UltimateFilm#DRAG}).
 * <ul>
 * <li><b>The hole:</b> a swirl of blue and gold arms turning about a glowing heart, a rim, and bolts that jump in to the
 * middle ({@link #drawHole}).</li>
 * <li><b>The tunnel:</b> the camera rides behind the runner inside a wall of dark blue-grey light, ribbons of gold, yellow, blue
 * and grey streaking past it toward a pale glowing sphere that rings with ripples; bright yellow lightning trails off the runner
 * back past the camera. The sphere swells to fill the screen at the end of the wormhole film.</li>
 * <li><b>The drag's world:</b> after the tunnel, over a flat-colour stand-in for the world from orbit (a blue disc, white
 * swirls turning on it), then the tunnel back, then the live world for the uppercut and slam. The picture of the world is
 * <b>art still to come</b>: put a PNG at {@link #WORLD_ART} and the disc shows it instead (no code change).</li>
 * </ul>
 * Colours and sizes follow the Speed Force portal of Legends Mod Core (LGPL-3.0): its lightning bloom, energy blooms and
 * swirl bloom ({@link #LIGHTNING}, {@link #ENERGY}, {@link #ENERGY_WHITE}, {@link #SWIRL}), its swirl's size
 * ({@link #SWIRL_SIZE}), the lightning's and energy's sizes and speeds, and the 15-tick life its effects run on
 * ({@link #CYCLE_TICKS}).
 */
public final class ChronoWormholeArt {
	// ---- from Legends Mod Core's speedforce_portal.fx (LGPL-3.0) ----
	/** Bloom colours of its lightning, its two energy smokes and its swirl. */
	static final int LIGHTNING = 0x63D1FF, ENERGY = 0x007FFF, ENERGY_WHITE = 0xF1F8FF, SWIRL = 0x0020FF;
	/** Its swirl's size, its lightning's size range, its energy's size range. */
	static final float SWIRL_SIZE = 8.0F, LIGHTNING_SIZE_MIN = 4.8F, LIGHTNING_SIZE_MAX = 7.2F, ENERGY_SIZE_MIN = 1.5F, ENERGY_SIZE_MAX = 1.9F;
	/** The speeds (blocks a second) of its lightning and its energy, and the life of every effect in it (ticks). */
	static final float LIGHTNING_SPEED = 5.0F, ENERGY_SPEED = 2.0F;
	static final int CYCLE_TICKS = 15;

	// ---- ours ----
	/** The gold half of the hole, the streaks' other colours, the dark of the wall. */
	static final int GOLD = 0xFFC840, YELLOW = 0xFFF04A, GREY = 0xA9B7CC, PALE = 0xBFE0FF, WALL = 0x16213A, SPACE = 0x04060F;
	/** The world from orbit's art: a PNG here replaces the flat stand-in (an art thread's job; nothing is painted by code). */
	static final Identifier WORLD_ART = JaymosPowers.id("textures/fx/world_from_orbit.png");
	/** The stand-in's blue and white. */
	static final int SEA = 0x1F5FBF, CLOUD = 0xFFFFFF;

	/** The hole: how big it is (blocks across the radius: the swirl's size scaled), its arms, their length in steps, how fast they turn. */
	static final float HOLE_RADIUS = SWIRL_SIZE * 0.3F;
	static final int ARMS = 3, ARM_STEPS = 14;
	static final float ARM_TWIST = 0.42F, ARM_SPIN = 0.35F;
	/** The tunnel: the wall's radius, the streak ribbons' radius and count, where it runs from and to (along the run). */
	static final float WALL_RADIUS = 3.6F, RIBBON_RADIUS = 3.4F;
	static final int RIBBONS = 28;
	static final double BACK = -12.0, FAR = 78.0;
	/** The camera rides this far behind the runner and this high (blocks). */
	static final double CAMERA_BACK = 3.3, CAMERA_UP = 1.25;
	/** The drag film's acts, as shares of it: the tunnel out, the world, the tunnel back, then the live world. */
	static final float ACT_WORLD = 0.28F, ACT_BACK = 0.6F, ACT_LIVE = 0.8F;
	/** Stand-in world: the disc's radius and how far it is at the start and end of its act (blocks). */
	static final float DISC_RADIUS = 52.0F;
	static final double DISC_FAR = 130.0, DISC_NEAR = 62.0;

	private static final float[] RIB_ANGLE = new float[RIBBONS], RIB_WIDTH = new float[RIBBONS], RIB_BASE = new float[RIBBONS],
		RIB_SPEED = new float[RIBBONS], RIB_LENGTH = new float[RIBBONS];
	private static final int[] RIB_COLOR = new int[RIBBONS];
	private static final int[] RIB_PALETTE = {GOLD, YELLOW, LIGHTNING, GREY, ENERGY, YELLOW, GREY, GOLD};
	private static final int STARS = 60;
	private static final float[] STAR_ANGLE = new float[STARS], STAR_Z = new float[STARS];

	static {
		RandomSource random = RandomSource.create(6120L);
		for (int i = 0; i < RIBBONS; i++) {
			RIB_ANGLE[i] = (i + random.nextFloat() * 0.8F) / RIBBONS * Mth.TWO_PI;
			RIB_WIDTH[i] = 0.07F + random.nextFloat() * 0.14F;
			RIB_BASE[i] = random.nextFloat() * (float)(FAR - BACK);
			// the lightning's speed (a second's travel) and the energy's, each a tick's worth
			RIB_SPEED[i] = (ENERGY_SPEED + random.nextFloat() * (LIGHTNING_SPEED - ENERGY_SPEED)) * 0.9F;
			RIB_LENGTH[i] = LIGHTNING_SIZE_MIN + random.nextFloat() * (LIGHTNING_SIZE_MAX - LIGHTNING_SIZE_MIN) + 4.0F;
			RIB_COLOR[i] = RIB_PALETTE[i % RIB_PALETTE.length];
		}
		for (int i = 0; i < STARS; i++) {
			STAR_ANGLE[i] = random.nextFloat() * Mth.TWO_PI;
			STAR_Z[i] = (float)(BACK + random.nextFloat() * (FAR - BACK));
		}
	}

	/** The film now: its shot, length, the way it runs, the foe being dragged, and whether the world's art is there. */
	private static boolean active;
	private static int shot, length, foeId = -1;
	private static Vec3 dir = new Vec3(0.0, 0.0, 1.0);
	private static boolean artThere;

	private ChronoWormholeArt() {
	}

	static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ChronoWormholeArt::render);
	}

	/** A film starts for you ({@code at}: a point ahead on the run for the wormhole, the foe for the drag). */
	public static void begin(LocalPlayer self, int shotKind, int ticks, Vec3 at, int target) {
		shot = shotKind;
		length = Math.max(1, ticks);
		foeId = shotKind == UltimateFilm.DRAG ? target : -1;
		Vec3 way = shotKind == UltimateFilm.WORMHOLE ? at.subtract(self.position()).horizontal() : Vec3.directionFromRotation(0.0F, self.getYRot());
		dir = way.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : way.normalize();
		artThere = Minecraft.getInstance().getResourceManager().getResource(WORLD_ART).isPresent();
		active = true;
	}

	/** Your film is over (played out or skipped): the server is told, and the trip lands. */
	public static void end() {
		if (!active) {
			return;
		}
		active = false;
		if (ClientPlayNetworking.canSend(ChronoWormhole.FilmEnd.TYPE)) {
			ClientPlayNetworking.send(new ChronoWormhole.FilmEnd());
		}
	}

	/** Which act of the film {@code t} (0-1) is in: 0 the tunnel, 1 the world, 2 the tunnel back, 3 the live world. */
	private static int act(float t) {
		if (shot != UltimateFilm.DRAG) {
			return 0;
		}
		return t < ACT_WORLD ? 0 : t < ACT_BACK ? 1 : t < ACT_LIVE ? 2 : 3;
	}

	/** The camera this frame ({x, y, z, yaw, pitch, roll}). */
	public static double[] view(LocalPlayer self, float partial, float t) {
		Vec3 base = self.getPosition(partial);
		float time = self.tickCount + partial;
		if (act(t) == 3) {
			// back in the live world: low and close, sweeping round for the uppercut and the slam
			double angle = Math.atan2(dir.z, dir.x) + Math.toRadians(150.0 + 70.0 * (t - ACT_LIVE) / (1.0F - ACT_LIVE));
			Vec3 pos = base.add(Math.cos(angle) * 3.2, 1.3, Math.sin(angle) * 3.2);
			return UltimateCinematic.aimed(pos, base.add(0.0, 1.5, 0.0).add(dir.scale(1.2)), 0.0F);
		}
		Vec3 pos = base.add(0.0, CAMERA_UP, 0.0).subtract(dir.scale(CAMERA_BACK));
		Vec3 look = base.add(0.0, CAMERA_UP - 0.1, 0.0).add(dir.scale(10.0));
		// a slow sway of the horizon
		float roll = Mth.sin(time * 0.31F) * 4.0F;
		return UltimateCinematic.aimed(pos, look, roll);
	}

	// ---------------------------------------------------------------- the film

	private static void render(LevelRenderContext context) {
		if (!active || !UltimateCinematic.filming()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = Mth.clamp(UltimateCinematic.progress(partial), 0.0F, 1.0F);
		int act = act(t);
		if (act == 3) {
			return;
		}
		float time = (minecraft.level.getGameTime() % 4000L) + partial;
		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		Frame frame = Frame.of(dir);
		// the camera sits at the origin of this space, a little behind the runner, looking down the run
		drawWall(collector, poseStack, frame, time, act == 1);
		if (act == 1) {
			float u = (t - ACT_WORLD) / (ACT_BACK - ACT_WORLD);
			drawWorld(collector, poseStack, frame, time, u);
		} else {
			float u = shot == UltimateFilm.WORMHOLE ? t : act == 0 ? t / ACT_WORLD * 0.5F : 0.5F + (t - ACT_BACK) / (ACT_LIVE - ACT_BACK) * 0.5F;
			drawRibbons(collector, poseStack, frame, time);
			drawSphere(collector, poseStack, frame, time, u);
		}
		drawRunner(collector, poseStack, frame, time);
	}

	/** The wall of the tunnel: dark blue-grey light the world can't be seen through (deep black in space). */
	private static void drawWall(SubmitNodeCollector collector, PoseStack poseStack, Frame frame, float time, boolean space) {
		int color = 0xFF000000 | (space ? SPACE : WALL);
		Vec3 a = frame.at(0.0, 0.0, BACK), b = frame.at(0.0, 0.0, FAR);
		float v0 = -time * 0.9F, v1 = v0 + (float)(FAR - BACK) * 0.25F;
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
			(pose, buffer) -> MagicFxClient.tube(buffer, pose, a, b, WALL_RADIUS, color, v0, v1));
		if (space) {
			// stars on the wall
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false), (pose, buffer) -> {
				for (int i = 0; i < STARS; i++) {
					double z = STAR_Z[i];
					Vec3 p = frame.radial(STAR_ANGLE[i], WALL_RADIUS - 0.2, z);
					MagicFxClient.tube(buffer, pose, p, p.add(frame.dir.scale(0.12)), 0.05F, 0xFFFFFFFF, 0.0F, 1.0F);
				}
			});
		}
	}

	/** Ribbons of gold, yellow, blue and grey streaking toward the camera along the wall. */
	private static void drawRibbons(SubmitNodeCollector collector, PoseStack poseStack, Frame frame, float time) {
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true), (pose, buffer) -> {
			float period = (float)(FAR - BACK);
			for (int i = 0; i < RIBBONS; i++) {
				// each runs in from the far end and out past the camera, then comes in again
				double z0 = BACK + Mth.positiveModulo(RIB_BASE[i] - time * RIB_SPEED[i] * 3.0F, period);
				double z1 = Math.min(FAR, z0 + RIB_LENGTH[i]);
				float wNear = RIB_WIDTH[i], wFar = RIB_WIDTH[i] * 0.3F;
				int rgb = RIB_COLOR[i];
				int nearColor = 0xA0 << 24 | rgb, farColor = 0x28 << 24 | rgb;
				Vec3 a0 = frame.radial(RIB_ANGLE[i] - wNear, RIBBON_RADIUS, z0), a1 = frame.radial(RIB_ANGLE[i] + wNear, RIBBON_RADIUS, z0);
				Vec3 b0 = frame.radial(RIB_ANGLE[i] - wFar, RIBBON_RADIUS, z1), b1 = frame.radial(RIB_ANGLE[i] + wFar, RIBBON_RADIUS, z1);
				quad(buffer, pose, a0, nearColor, 0.0F, 0.0F, a1, nearColor, 1.0F, 0.0F, b1, farColor, 1.0F, 1.0F, b0, farColor, 0.0F, 1.0F);
			}
		});
	}

	/** The pale glowing sphere at the vanishing point, ringed with ripples; it swells to fill the view as the wormhole ends. */
	private static void drawSphere(SubmitNodeCollector collector, PoseStack poseStack, Frame frame, float time, float u) {
		double z = shot == UltimateFilm.WORMHOLE ? Mth.lerp(Math.min(1.0F, u / 0.9F), 44.0F, 7.0F) : 36.0;
		float radius = shot == UltimateFilm.WORMHOLE && u > 0.86F ? 1.6F + (u - 0.86F) / 0.14F * 36.0F : 1.6F;
		Vec3 c = frame.at(0.0, 0.0, z);
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
			(pose, buffer) -> MagicFxClient.sphere(buffer, pose, c, radius, 0xFF000000 | 0xAFC8EC, time * 0.01F, 0.0F));
		MagicFxClient.drawOrb(collector, poseStack, c, radius * 2.2F, PALE, time, 0.8F);
		// ripples running out round it
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true), (pose, buffer) -> {
			for (int k = 0; k < 3; k++) {
				float phase = Mth.positiveModulo(time / CYCLE_TICKS + k / 3.0F, 1.0F);
				float r = radius * (1.4F + phase * 2.4F);
				int color = (int)(0x90 * (1.0F - phase)) << 24 | ENERGY_WHITE;
				ring(buffer, pose, frame, c, r, 0.05F + 0.04F * phase, color);
			}
		});
	}

	/** Bright yellow lightning trailing off the runner, back past the camera. */
	private static void drawRunner(SubmitNodeCollector collector, PoseStack poseStack, Frame frame, float time) {
		Vec3 back = frame.at(0.0, -0.3, CAMERA_BACK - 0.4);
		int phase = (int)(time / 2.0F);
		for (int i = 0; i < 6; i++) {
			double sx = (i - 2.5) * 0.35, sy = Math.sin(i * 2.1 + phase) * 0.5;
			Vec3 end = frame.at(sx * 2.0, sy - 0.2, -7.0 - i);
			MagicFxClient.drawBolt(collector, poseStack, back, end, 0.12F, YELLOW, 4100 + i * 17, phase, 0.15F + 0.1F * (i % 3));
		}
		if (foeId >= 0) {
			// the foe dragged along beside the runner: a dark shape on a lightning leash (no art for it, only its size)
			Entity foe = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(foeId);
			float w = foe == null ? 0.6F : foe.getBbWidth(), h = foe == null ? 1.8F : foe.getBbHeight();
			Vec3 feet = frame.at(0.9, -1.0 + Math.sin(time * 0.4) * 0.1, CAMERA_BACK + 1.1);
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false),
				(pose, buffer) -> MagicFxClient.tube(buffer, pose, feet, feet.add(0.0, h, 0.0), w * 0.5F, 0xFF10131E, 0.0F, 1.0F));
			MagicFxClient.drawBolt(collector, poseStack, back, feet.add(0.0, h * 0.5, 0.0), 0.1F, YELLOW, 4200, phase, 0.2F);
		}
	}

	/** The world from orbit's stand-in: a flat blue disc with white swirls turning on it, growing as the camera falls toward it. */
	private static void drawWorld(SubmitNodeCollector collector, PoseStack poseStack, Frame frame, float time, float u) {
		double z = Mth.lerp(u, DISC_FAR, DISC_NEAR);
		Vec3 c = frame.at(0.0, 0.0, z);
		float spin = time * 0.012F;
		if (artThere) {
			// the art thread's picture, turning slowly
			collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(WORLD_ART, false),
				(pose, buffer) -> fan(buffer, pose, frame, c, DISC_RADIUS, 0xFFFFFFFF, 0xFFFFFFFF, spin, true));
			return;
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false), (pose, buffer) -> {
			fan(buffer, pose, frame, c, DISC_RADIUS, 0xFF000000 | SEA, 0xFF000000 | mixRgb(SEA, 0x050A20, 0.55F), spin, false);
		});
		// white swirls: arms of cloud spiralling out of the middle
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true), (pose, buffer) -> {
			for (int arm = 0; arm < 6; arm++) {
				Vec3 last = null;
				for (int k = 0; k <= 18; k++) {
					float r = DISC_RADIUS * (0.12F + 0.8F * k / 18.0F);
					float angle = spin * 2.0F + arm * Mth.TWO_PI / 6.0F + k * 0.18F;
					Vec3 p = c.add(frame.side.scale(Mth.cos(angle) * r)).add(frame.up.scale(Mth.sin(angle) * r)).subtract(frame.dir.scale(0.4));
					if (last != null) {
						int color = (int)(0xB0 * (1.0F - k / 22.0F)) << 24 | CLOUD;
						MagicFxClient.tube(buffer, pose, last, p, 1.4F * (1.0F - k / 24.0F) + 0.3F, color, 0.0F, 1.0F);
					}
					last = p;
				}
			}
		});
	}

	// ---------------------------------------------------------------- the hole in the world

	/**
	 * A wormhole standing in the world, camera-relative at {@code at}, its swirl turned to face down {@code way}: three arms
	 * of blue and gold turning about a glowing heart, a gold rim, and bolts jumping in to the middle. {@code open} (0-1) is how
	 * far it has torn open (or is closing); {@code time} runs the turning.
	 */
	static void drawHole(SubmitNodeCollector collector, PoseStack poseStack, Vec3 at, Vec3 way, float time, float open) {
		if (open <= 0.01F) {
			return;
		}
		Frame frame = Frame.of(way);
		float radius = HOLE_RADIUS * open;
		MagicFxClient.drawOrb(collector, poseStack, at, radius * 0.9F, SWIRL, time, open);
		MagicFxClient.drawOrb(collector, poseStack, at, radius * 0.35F, ENERGY_WHITE, time + 9.0F, open);
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, false), (pose, buffer) -> {
			for (int arm = 0; arm < ARMS; arm++) {
				Vec3 last = null;
				for (int k = 1; k <= ARM_STEPS; k++) {
					float r = radius * k / ARM_STEPS;
					float angle = arm * Mth.TWO_PI / ARMS + k * ARM_TWIST - time * ARM_SPIN;
					Vec3 p = at.add(frame.side.scale(Mth.cos(angle) * r)).add(frame.up.scale(Mth.sin(angle) * r));
					if (last != null) {
						int rgb = (k + arm) % 3 == 0 ? ENERGY_WHITE : (k + arm) % 2 == 0 ? GOLD : LIGHTNING;
						MagicFxClient.tube(buffer, pose, last, p, 0.11F - 0.05F * k / ARM_STEPS, 0xFF000000 | rgb, time * 0.05F, time * 0.05F + 0.4F);
					}
					last = p;
				}
			}
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
			(pose, buffer) -> ring(buffer, pose, frame, at, radius, 0.14F, 0xB0000000 | GOLD));
		int phase = (int)(time / 2.0F);
		for (int i = 0; i < 3; i++) {
			float angle = (phase * 1.7F + i * 2.1F);
			Vec3 rim = at.add(frame.side.scale(Mth.cos(angle) * radius)).add(frame.up.scale(Mth.sin(angle) * radius));
			MagicFxClient.drawBolt(collector, poseStack, rim, at, 0.09F * open, LIGHTNING, 9000 + i * 31, phase, 0.2F);
		}
	}

	// ---------------------------------------------------------------- shapes

	/** A basis for a run: along it, and the two sides across it. */
	private static final class Frame {
		final Vec3 dir, side, up;

		private Frame(Vec3 dir, Vec3 side, Vec3 up) {
			this.dir = dir;
			this.side = side;
			this.up = up;
		}

		static Frame of(Vec3 way) {
			Vec3 d = way.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : way.normalize();
			Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
			Vec3 side = d.cross(up).normalize();
			return new Frame(d, side, side.cross(d).normalize());
		}

		/** A point {@code ahead} along the run and {@code across}, {@code high} off its line. */
		Vec3 at(double across, double high, double ahead) {
			return dir.scale(ahead).add(side.scale(across)).add(up.scale(high));
		}

		/** A point on a circle of {@code radius} round the run at {@code angle}, {@code ahead} along it. */
		Vec3 radial(float angle, double radius, double ahead) {
			return dir.scale(ahead).add(side.scale(Mth.cos(angle) * radius)).add(up.scale(Mth.sin(angle) * radius));
		}
	}

	/** A ring of {@code radius} about {@code c}, in the plane across the run, made of short tubes. */
	private static void ring(VertexConsumer buffer, PoseStack.Pose pose, Frame frame, Vec3 c, float radius, float thick, int color) {
		int segments = 28;
		Vec3 last = c.add(frame.side.scale(radius));
		for (int i = 1; i <= segments; i++) {
			float angle = Mth.TWO_PI * i / segments;
			Vec3 p = c.add(frame.side.scale(Mth.cos(angle) * radius)).add(frame.up.scale(Mth.sin(angle) * radius));
			MagicFxClient.tube(buffer, pose, last, p, thick, color, 0.0F, 1.0F);
			last = p;
		}
	}

	/** A disc facing down the run, {@code radius} across, a fan of wedges: {@code inner} at the middle, {@code outer} at the rim; the whole texture over it if {@code mapped}. */
	private static void fan(VertexConsumer buffer, PoseStack.Pose pose, Frame frame, Vec3 c, float radius, int inner, int outer, float spin, boolean mapped) {
		int wedges = 40;
		for (int i = 0; i < wedges; i++) {
			float a0 = Mth.TWO_PI * i / wedges + spin, a1 = Mth.TWO_PI * (i + 1) / wedges + spin;
			Vec3 p0 = c.add(frame.side.scale(Mth.cos(a0) * radius)).add(frame.up.scale(Mth.sin(a0) * radius));
			Vec3 p1 = c.add(frame.side.scale(Mth.cos(a1) * radius)).add(frame.up.scale(Mth.sin(a1) * radius));
			float u0 = mapped ? 0.5F + 0.5F * Mth.cos(a0 - spin) : 0.0F, v0 = mapped ? 0.5F + 0.5F * Mth.sin(a0 - spin) : 0.0F;
			float u1 = mapped ? 0.5F + 0.5F * Mth.cos(a1 - spin) : 0.0F, v1 = mapped ? 0.5F + 0.5F * Mth.sin(a1 - spin) : 1.0F;
			quad(buffer, pose, c, inner, mapped ? 0.5F : 0.5F, 0.5F, c, inner, 0.5F, 0.5F, p1, outer, u1, v1, p0, outer, u0, v0);
		}
	}

	/** Four corners, each with its own colour, drawn both ways round. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, int ca, float ua, float va, Vec3 b, int cb, float ub, float vb, Vec3 c,
		int cc, float uc, float vc, Vec3 d, int cd, float ud, float vd) {
		vertex(buffer, pose, a, ca, ua, va);
		vertex(buffer, pose, b, cb, ub, vb);
		vertex(buffer, pose, c, cc, uc, vc);
		vertex(buffer, pose, d, cd, ud, vd);
		vertex(buffer, pose, d, cd, ud, vd);
		vertex(buffer, pose, c, cc, uc, vc);
		vertex(buffer, pose, b, cb, ub, vb);
		vertex(buffer, pose, a, ca, ua, va);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, int color, float u, float v) {
		buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static int mixRgb(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}
}
