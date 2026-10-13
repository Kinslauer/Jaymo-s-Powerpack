package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How the flying aliens look in the air, on everyone's screen (from the synced {@link XenoformState#FLIGHT}):
 * <ul>
 * <li>Pyre, Wyrm and Nadir cruise like Wind: {@link WindPose} leans, banks and stretches them out once they're moving
 * ({@link #cruising}).</li>
 * <li>Wyrm's wings beat hard while it flies and fold back along its body as it picks up speed (read in its model).</li>
 * <li>Wisp's glide is a ghost's drift: tipped forward, arms reaching out limp and swaying, the tatters streaming back,
 * the whole body rolling and bobbing slowly.</li>
 * <li>Pyre flies on fire: its arms stay swept back as jets (no fist out ahead), and open flames pour from its hands
 * and feet ({@link #tick}).</li>
 * </ul>
 */
public final class XenoformFlightPose {
	/** One player's flight this frame: the glide's amount, the wings' (flying, folded back), Pyre's jet, and the time. */
	public record Flight(float glide, float wings, float fold, float jet, float time) {
	}

	public static final RenderStateDataKey<Flight> KEY = RenderStateDataKey.create(() -> "jaymospowers:xenoform_flight");

	/** Moving at least this fast (blocks/tick) a cruising alien takes Wind's flight pose. */
	static final double CRUISE_SPEED = 0.25;
	/** The wings fold from this speed and are folded right back by {@code FOLD_FULL}. */
	static final float FOLD_START = 0.45F, FOLD_FULL = 0.8F;
	/** How fast the glide and the wings ease in and out, per tick. */
	static final float EASE = 0.15F;
	/** The glide's forward tip (degrees), its slow roll (degrees) and bob (blocks). */
	static final float GLIDE_LEAN = 38.0F, GLIDE_ROLL = 9.0F, GLIDE_BOB = 0.07F;
	/** Pyre's foot flames: particles per foot a tick, and their push out behind. */
	static final int FOOT_FLAMES = 2;
	static final double FOOT_PUSH = 0.12;

	/** Pyre's jet arms: swept back this far (radians) and out from the body this far. */
	static final float JET_ARM_BACK = 0.35F, JET_ARM_OUT = 0.3F;

	/** entity id → {glide, glide before, wings, wings before, fold, fold before, jet, jet before}. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();

	private XenoformFlightPose() {
	}

	/** A flying alien that should cruise in Wind's pose (WindPose asks). */
	public static boolean cruising(Player player) {
		int kind = XenoformState.flight(player);
		if (kind != XenoformState.FLY_JET && kind != XenoformState.FLY_WINGS && kind != XenoformState.FLY_VOID) {
			return false;
		}
		return speed(player) >= CRUISE_SPEED;
	}

	private static double speed(Player player) {
		double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Every client tick: the eased amounts for everyone in view, and Pyre's foot flames. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			if (level == null) {
				AMOUNTS.clear();
			}
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			int kind = XenoformState.flight(player);
			float[] a = AMOUNTS.get(player.getId());
			if (a == null) {
				if (kind == XenoformState.FLY_NONE) {
					continue;
				}
				a = new float[8];
				AMOUNTS.put(player.getId(), a);
			}
			a[1] = a[0];
			a[3] = a[2];
			a[5] = a[4];
			a[7] = a[6];
			a[6] = ease(a[6], kind == XenoformState.FLY_JET);
			a[0] = ease(a[0], kind == XenoformState.FLY_GLIDE);
			a[2] = ease(a[2], kind == XenoformState.FLY_WINGS);
			float foldTarget = kind == XenoformState.FLY_WINGS ? Mth.clamp(((float)speed(player) - FOLD_START) / (FOLD_FULL - FOLD_START), 0.0F, 1.0F) : 0.0F;
			a[4] = a[4] + (foldTarget - a[4]) * EASE * 1.5F;
			if (kind == XenoformState.FLY_JET && !player.isInvisible()) {
				footFlames(level, player);
			}
		}
		AMOUNTS.entrySet().removeIf(entry -> {
			float[] a = entry.getValue();
			return a[0] <= 0.0F && a[1] <= 0.0F && a[2] <= 0.0F && a[3] <= 0.0F && a[4] <= 0.001F && a[6] <= 0.0F && a[7] <= 0.0F
				|| level.getEntity(entry.getKey()) == null;
		});
	}

	private static float ease(float value, boolean on) {
		return on ? Math.min(1.0F, value + EASE) : Math.max(0.0F, value - EASE);
	}

	/** Pyre's arms are jets: Wind's cruise pose leaves them alone. */
	public static boolean handsAsJets(AvatarRenderState state) {
		Flight flight = state.getData(KEY);
		return flight != null && flight.jet() > 0.001F;
	}

	/** Open flames pouring from both feet and both hands, out behind along the flight (or straight down while upright). */
	private static void footFlames(ClientLevel level, Player player) {
		RandomSource random = player.getRandom();
		float cruise = WindPose.amount(player, 1.0F);
		double height = player.getBbHeight();
		Vec3 look = player.getLookAngle();
		Vec3 standing = player.position().add(0.0, 0.05, 0.0);
		Vec3 stretched = player.position().add(0.0, height * 0.5, 0.0).subtract(look.scale(height * 0.5));
		Vec3 feet = standing.lerp(stretched, cruise);
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		double width = height / 1.8 * 0.12;
		Vec3 side = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw)).scale(width);
		Vec3 out = new Vec3(0.0, -FOOT_PUSH, 0.0).lerp(look.scale(-FOOT_PUSH), cruise);
		// the hands, swept back at the sides: at the hips upright, alongside the waist stretched out
		Vec3 hands = player.position().add(0.0, height * 0.42, 0.0)
			.lerp(player.position().add(0.0, height * 0.5, 0.0).subtract(look.scale(height * 0.15)), cruise);
		for (int jet = 0; jet < 4; jet++) {
			int foot = jet % 2 == 0 ? -1 : 1;
			Vec3 at = jet < 2 ? feet.add(side.scale(foot)) : hands.add(side.scale(foot * 3.0));
			for (int i = 0; i < FOOT_FLAMES; i++) {
				level.addParticle(ParticleTypes.FLAME, at.x, at.y, at.z,
					out.x + random.nextGaussian() * 0.02, out.y + random.nextGaussian() * 0.02, out.z + random.nextGaussian() * 0.02);
			}
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, out.x * 1.5, out.y * 1.5, out.z * 1.5);
			}
		}
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		float[] a = AMOUNTS.get(entity.getId());
		if (a == null) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Flight(Mth.lerp(partialTicks, a[1], a[0]), Mth.lerp(partialTicks, a[3], a[2]), Mth.lerp(partialTicks, a[5], a[4]),
			Mth.lerp(partialTicks, a[7], a[6]), entity.tickCount + partialTicks));
	}

	/** After the body's turned to face its way: the glide tips it forward, rolling and bobbing slowly. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack, float entityScale) {
		Flight flight = state.getData(KEY);
		if (flight == null || flight.glide() <= 0.001F || state.deathTime > 0.0F) {
			return;
		}
		float g = smooth(flight.glide()), t = flight.time();
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, Mth.sin(t * 0.2F) * GLIDE_BOB * g, 0.0F);
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.XP, GLIDE_LEAN * g);
		poseStack.rotateDegrees(Axis.ZP, Mth.sin(t * 0.09F) * GLIDE_ROLL * g);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** Right after a humanoid model animates: the glide's limp, reaching arms and bowed head. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Flight flight = avatar.getData(KEY);
		if (flight == null) {
			return;
		}
		if (flight.jet() > 0.001F) {
			// Pyre: both arms swept back and a little out, palms behind, the flames pushing
			float j = smooth(flight.jet()), t = flight.time(), shake = Mth.sin(t * 1.7F) * 0.04F;
			pose(h.rightArm, JET_ARM_BACK + shake, 0.0F, JET_ARM_OUT, j);
			pose(h.leftArm, JET_ARM_BACK - shake, 0.0F, -JET_ARM_OUT, j);
		}
		if (flight.glide() <= 0.001F) {
			return;
		}
		float g = smooth(flight.glide()), t = flight.time();
		// arms out ahead and drooping, swaying out of step like something drowned
		pose(h.rightArm, -1.05F + Mth.sin(t * 0.15F) * 0.18F, 0.1F, 0.22F + Mth.sin(t * 0.11F) * 0.08F, g);
		pose(h.leftArm, -1.05F + Mth.sin(t * 0.15F + 1.9F) * 0.18F, -0.1F, -0.22F - Mth.sin(t * 0.11F + 1.3F) * 0.08F, g);
		// the head hangs a little, then lifts to see where it's going
		pose(h.head, -0.45F + Mth.sin(t * 0.07F) * 0.08F, h.head.yRot, Mth.sin(t * 0.09F + 0.5F) * 0.1F, g);
		// the legs (when there are any) trail, loose
		pose(h.rightLeg, 0.35F + Mth.sin(t * 0.13F) * 0.1F, 0.0F, 0.05F, g);
		pose(h.leftLeg, 0.35F + Mth.sin(t * 0.13F + 2.2F) * 0.1F, 0.0F, -0.05F, g);
	}

	private static void pose(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}
