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
 * <li>Keen's and Talon's glide: tipped forward, rolling and bobbing slowly.</li>
 * <li>(0.20.23) Wisp's (and Revenant's) float: upright, as a ghost hangs in the air (Andy: "it should float upright instead of
 * an angled glide"). The body stays upright and sways a little (its own bob, {@code WispKi}, carries on), the arms hang
 * loose at its sides and drift, the head stays level; sneaking to sink doesn't crouch it ({@code BillowPoseMixin}).</li>
 * <li>Pyre flies on fire: its arms stay swept back as jets (no fist out ahead), and small flames spurt from its hands
 * and feet while it hovers ({@link #tick}); at speed its comet's tail takes over ({@link PyreComet}).</li>
 * </ul>
 */
public final class XenoformFlightPose {
	/** One player's flight this frame: the glide's amount, the wings' (flying, folded back), Pyre's jet, and the time. */
	public record Flight(float glide, float wings, float fold, float jet, float time, float drift, float lean, float bank) {
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
	/** (0.20.23) The float's slow side-to-side sway (degrees). */
	static final float FLOAT_SWAY = 3.0F;
	/** The float on the move: tipped forward this far at {@code FLOAT_FAST} blocks a tick (degrees), and rolled into a turn this far at most. */
	static final float FLOAT_LEAN = 24.0F, FLOAT_BANK = 14.0F, FLOAT_FAST = 0.3F;
	/** A turn of this many degrees a tick banks it right over. */
	static final float FLOAT_BANK_TURN = 12.0F;
	/** At full lean the arms come this far forward (radians). */
	static final float FLOAT_REACH = -1.25F;
	/** Pyre's foot flames: particles per foot a tick, and their push out behind; only while cruising less than this much. */
	static final int FOOT_FLAMES = 1;
	static final float JETS_UNTIL = 0.5F;
	static final double FOOT_PUSH = 0.12;
	/** Pyre ablaze: one flame in this many ticks rises off its body (not in water or rain, nor round your own camera). */
	static final int BODY_FLAME_EVERY = 3;

	/** Pyre's jet arms: swept back this far (radians) and out from the body this far. */
	static final float JET_ARM_BACK = 0.35F, JET_ARM_OUT = 0.3F;

	/** entity id → {glide, glide before, wings, wings before, fold, fold before, jet, jet before, float, float before, lean, lean before, bank, bank before}. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();

	private XenoformFlightPose() {
	}

	/** A flying alien that should cruise in Wind's pose (WindPose asks). */
	public static boolean cruising(Player player) {
		int kind = XenoformState.flight(player);
		if (!XenoformState.jet(kind) && kind != XenoformState.FLY_WINGS && kind != XenoformState.FLY_VOID) {
			return false;
		}
		return speed(player) >= CRUISE_SPEED;
	}

	/** (0.18.91) Nadir off the ground and not cruising: he floats in Wind's hover pose (WindHoverSway asks). */
	public static boolean floating(Player player) {
		return XenoformState.flight(player) == XenoformState.FLY_VOID && speed(player) < CRUISE_SPEED;
	}

	private static double speed(Player player) {
		double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Every client tick: the eased amounts for everyone in view, Pyre's foot flames, and the flames off its body. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			if (level == null) {
				AMOUNTS.clear();
			}
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			if ("pyre".equals(player.getAttached(XenoformState.FORM)) && !player.isInvisible() && !player.isInWaterOrRain()
				&& !(player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson())
				&& player.getRandom().nextInt(BODY_FLAME_EVERY) == 0) {
				bodyFlame(level, player);
			}
			int kind = XenoformState.flight(player);
			float[] a = AMOUNTS.get(player.getId());
			if (a == null) {
				if (kind == XenoformState.FLY_NONE) {
					continue;
				}
				a = new float[14];
				AMOUNTS.put(player.getId(), a);
			}
			a[1] = a[0];
			a[3] = a[2];
			a[5] = a[4];
			a[7] = a[6];
			a[9] = a[8];
			a[11] = a[10];
			a[13] = a[12];
			a[8] = ease(a[8], kind == XenoformState.FLY_FLOAT);
			// the float leans the way it goes and rolls into its turns, late and soft
			float leanTo = 0.0F, bankTo = 0.0F;
			if (kind == XenoformState.FLY_FLOAT) {
				float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
				float forward = (float)(-Mth.sin(yaw) * (player.getX() - player.xo) + Mth.cos(yaw) * (player.getZ() - player.zo));
				leanTo = Mth.clamp(forward / FLOAT_FAST, -0.4F, 1.0F);
				bankTo = Mth.clamp(Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO) / FLOAT_BANK_TURN, -1.0F, 1.0F) * Math.abs(leanTo);
			}
			a[10] += (leanTo - a[10]) * 0.2F;
			a[12] += (bankTo - a[12]) * 0.25F;
			a[6] = ease(a[6], XenoformState.jet(kind));
			a[0] = ease(a[0], kind == XenoformState.FLY_GLIDE);
			a[2] = ease(a[2], kind == XenoformState.FLY_WINGS);
			float foldTarget = kind == XenoformState.FLY_WINGS ? Mth.clamp(((float)speed(player) - FOLD_START) / (FOLD_FULL - FOLD_START), 0.0F, 1.0F) : 0.0F;
			a[4] = a[4] + (foldTarget - a[4]) * EASE * 1.5F;
			// (0.18.89) hovering, small jets from hands and feet; streaking along, the comet (PyreComet) is the fire
			if (XenoformState.jet(kind) && !player.isInvisible() && WindPose.amount(player, 1.0F) < JETS_UNTIL) {
				footFlames(level, player);
			}
		}
		AMOUNTS.entrySet().removeIf(entry -> {
			float[] a = entry.getValue();
			return a[0] <= 0.0F && a[1] <= 0.0F && a[2] <= 0.0F && a[3] <= 0.0F && a[4] <= 0.001F && a[6] <= 0.0F && a[7] <= 0.0F
				&& a[8] <= 0.0F && a[9] <= 0.0F && Math.abs(a[10]) <= 0.01F && Math.abs(a[12]) <= 0.01F
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

	/** A flame off a random spot on Pyre's body, drifting up. */
	private static void bodyFlame(ClientLevel level, Player player) {
		RandomSource random = player.getRandom();
		double width = player.getBbWidth() * 0.45;
		level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
			player.getX() + (random.nextDouble() - 0.5) * 2.0 * width, player.getY() + random.nextDouble() * player.getBbHeight(),
			player.getZ() + (random.nextDouble() - 0.5) * 2.0 * width, 0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
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
			Mth.lerp(partialTicks, a[7], a[6]), entity.tickCount + partialTicks, Mth.lerp(partialTicks, a[9], a[8]),
			Mth.lerp(partialTicks, a[11], a[10]), Mth.lerp(partialTicks, a[13], a[12])));
	}

	/** After the body's turned to face its way: the glide tips it forward, rolling and bobbing slowly. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack, float entityScale) {
		Flight flight = state.getData(KEY);
		if (flight == null || state.deathTime > 0.0F) {
			return;
		}
		if (flight.drift() > 0.001F) {
			// (0.20.23) the float: upright, only a slow sway (the forms bob on their own)
			float f = smooth(flight.drift()), t = flight.time();
			float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
			poseStack.translate(0.0F, middle, 0.0F);
			poseStack.rotateDegrees(Axis.XP, FLOAT_LEAN * flight.lean() * f);
			poseStack.rotateDegrees(Axis.ZP, (Mth.sin(t * 0.07F) * FLOAT_SWAY + FLOAT_BANK * flight.bank()) * f);
			poseStack.translate(0.0F, -middle, 0.0F);
		}
		if (flight.glide() <= 0.001F) {
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
		if (flight.drift() > 0.001F) {
			// (0.20.23) the float: arms hanging loose at the sides, a little forward and out, drifting out of step
			float f = smooth(flight.drift()), t = flight.time();
			// at speed they come up and reach out ahead, the way a ghost goes through the air
			float reach = smooth(Math.max(0.0F, flight.lean()));
			float armX = Mth.lerp(reach, -0.2F, FLOAT_REACH);
			pose(h.rightArm, armX + Mth.sin(t * 0.09F) * 0.12F, 0.0F, 0.18F * (1.0F - reach) + Mth.sin(t * 0.07F) * 0.06F, f);
			pose(h.leftArm, armX + Mth.sin(t * 0.09F + 1.9F) * 0.12F, 0.0F, -0.18F * (1.0F - reach) - Mth.sin(t * 0.07F + 1.3F) * 0.06F, f);
			// the head level, turning to look as it likes (lifted against the lean, to see ahead)
			pose(h.head, h.head.xRot * 0.5F + Mth.sin(t * 0.05F) * 0.04F - FLOAT_LEAN * Mth.DEG_TO_RAD * flight.lean() * 0.8F, h.head.yRot, Mth.sin(t * 0.06F) * 0.05F, f);
			// the legs (when there are any) hang straight down, swaying a little
			pose(h.rightLeg, 0.08F + Mth.sin(t * 0.08F) * 0.06F, 0.0F, 0.03F, f);
			pose(h.leftLeg, 0.08F + Mth.sin(t * 0.08F + 2.2F) * 0.06F, 0.0F, -0.03F, f);
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
