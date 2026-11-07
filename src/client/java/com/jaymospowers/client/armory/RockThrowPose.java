package com.jaymospowers.client.armory;

import com.jaymospowers.ground.FirstTools;
import com.jaymospowers.ground.ThrownRocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.90) Andy: "the player's rock throw should have an animated windup and release". The off hand rock throw
 * ({@link ThrownRocks}), on everyone:
 * <ul>
 * <li><b>Wind-up</b>, while the charge is held: the throwing arm drawn up and back over the shoulder, the body turned
 * that side away, the other arm reaching out at the mark; held there, trembling a little, until the rock goes.</li>
 * <li><b>Release:</b> the arm whips over and through across the body in {@link #WHIP_TICKS}, the body turning into it
 * and the other arm flung back, then eases out over the rest of {@link ThrownRocks#FOLLOW_TICKS}.</li>
 * </ul>
 * In first person the rock is drawn back up beside your head, then thrown forward out of view, and the hand dips and
 * comes back up (with the next rock, if there is one). Other players' throws come from {@link ThrownRocks#THROW}; your
 * own is counted on your client ({@link ThrownRocks#windUpHere}), which tells the server when the rock goes
 * ({@link ThrownRocks.Charge}), so it starts the moment you press and the rock leaves as the arm comes over.
 * <p>
 * (0.20.103) A full charge is held drawn back ({@link ThrownRocks#READY}) until use is let go, and the rock goes then.
 * The wind-up is drawn at its own pace (a charge sped up by momentum winds up faster).
 */
public final class RockThrowPose {
	// ===== the knobs ================================================================================================
	/** How fast the pose eases in and out (a share a tick). */
	static final float EASE = 0.34F;
	/** The whip, from drawn back to thrown through (ticks). */
	static final float WHIP_TICKS = 2.0F;
	/** Third person, the throwing arm (radians): drawn back up over the shoulder, and thrown through across the body. */
	static final float COCK_X = -3.3F, COCK_Y = 0.2F, COCK_Z = 0.3F;
	static final float THROUGH_X = -0.75F, THROUGH_Y = -0.45F, THROUGH_Z = 0.0F;
	/** Third person, the other arm: out at the mark while it winds, flung back on the throw. */
	static final float AIM_X = -1.25F, AIM_Y = 0.3F, AIM_Z = 0.0F;
	static final float BACK_X = 0.6F, BACK_Y = 0.0F, BACK_Z = -0.2F;
	/** Third person, the body's turn (radians, + the throwing shoulder back): wound up, and thrown into. */
	static final float TWIST_COCK = 0.5F, TWIST_THROUGH = -0.45F;
	/** The tremble of the drawn-back arm (radians) and how fast it shakes. */
	static final float SHAKE = 0.025F, SHAKE_BEAT = 2.7F;
	/** First person (blocks; x out to the side, y up, z toward you): the rock drawn back, and thrown out. */
	static final float FP_COCK_X = 0.08F, FP_COCK_Y = 0.24F, FP_COCK_Z = 0.28F;
	static final float FP_THROUGH_X = -0.16F, FP_THROUGH_Y = -0.02F, FP_THROUGH_Z = -0.5F;
	/** First person (degrees): drawn back tipped toward you and rolled out; thrown tipped forward. */
	static final float FP_COCK_PITCH = 28.0F, FP_COCK_ROLL = -14.0F, FP_THROUGH_PITCH = -45.0F;
	/** First person: how far the hand dips after the throw (blocks), and the tremble while drawn back. */
	static final float FP_DIP = 0.55F, FP_SHAKE = 0.006F;
	// ================================================================================================================

	/** {@code arm}: the throwing arm; {@code ticks}: into its stage; {@code amount}: how much of the pose shows (0-1). */
	public record Pose(int stage, HumanoidArm arm, float ticks, float amount, boolean ownView, int charge) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:rock_throw");

	/** One player's throw as drawn: how much shows (now and a tick before), its stage and ticks in it, the value last seen. */
	private static final class Throw {
		float amount, amountBefore, ticks, ticksBefore;
		int stage, value, lastValue;
	}

	/** Per entity id. */
	private static final Map<Integer, Throw> STATE = new HashMap<>();
	/** Client ticks (not counted while paused), for your own throw's count. */
	private static long ticks;
	/** Your own hold, as the server counts it: begun, last seen, and when its rock went (-1: not yet; begun -1: none). */
	private static long ownStart = -1, ownLast = -1, ownThrown = -1;
	/** (0.20.103) Your own hold: how long its charge takes, whether it's full, when the server was last told so. */
	private static int ownCharge = ThrownRocks.CHARGE;
	private static boolean ownReady;
	private static long ownHeard = -1;
	/** (0.20.103) Your own momentum (as the server counts it): throws in a row, the one it began with, the last (client ticks). */
	private static int ownStreak, ownMomentum;
	private static long ownLastThrow = -1;

	private RockThrowPose() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RockThrowPose::tick);
		ThrownRocks.windUpHere = (player, hand) -> {
			if (player == Minecraft.getInstance().player) {
				held();
			}
		};
	}

	/** A held use on your own client (one every 4 ticks while use is held): the charge counted, the server told. */
	private static void held() {
		long now = ticks;
		if (ownLastThrow >= 0 && now >= ownLastThrow && now - ownLastThrow < ThrownRocks.COOLDOWN || ownThrown >= 0) {
			// still cooling down on the server (its word may not be here yet), or following through
			return;
		}
		if (ownStart < 0 || now - ownLast > ThrownRocks.CHARGE_GAP || now < ownLast) {
			ownStart = now;
			ownLast = now;
			ownThrown = -1;
			ownReady = false;
			ownMomentum = ThrownRocks.momentum(ownStreak, ownLastThrow, now);
			ownCharge = ThrownRocks.charge(ownMomentum);
			ClientPlayNetworking.send(new ThrownRocks.Charge(ThrownRocks.WINDING_UP));
			LocalPlayer self = Minecraft.getInstance().player;
			if (self != null && ThrownRocks.gravity(self)) {
				// (0.21.8) a gravity user's rock goes at once (the server throws it on the wind-up's word)
				ownThrown = now;
				ownLastThrow = now;
			}
			return;
		}
		ownLast = now;
		if (!ownReady && now - ownStart >= ownCharge || ownReady && now - ownHeard >= ThrownRocks.KEEPALIVE) {
			// (0.20.103) full: held drawn back until use is let go
			ownReady = true;
			ownHeard = now;
			ClientPlayNetworking.send(new ThrownRocks.Charge(ThrownRocks.READY));
		}
	}

	/** Your own throw's stage now (0: none). */
	private static int ownStage() {
		if (ownStart < 0) {
			return 0;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (ownThrown >= 0) {
			if (ticks - ownThrown < ThrownRocks.FOLLOW_TICKS) {
				return ThrownRocks.THROWING;
			}
		} else if (minecraft.getConnection() != null) {
			LocalPlayer player = minecraft.player;
			boolean lost = player == null || minecraft.gui.screen() != null || !FirstTools.rock(player.getOffhandItem());
			boolean letGo = !minecraft.options.keyUse.isDown() || ticks - ownLast > ThrownRocks.CHARGE_GAP;
			if (!lost && !letGo) {
				return ownReady ? ThrownRocks.READY : ThrownRocks.WINDING_UP;
			}
			if (!lost && ownReady) {
				// (0.20.103) a full charge let go: the rock goes, and the next charge comes sooner
				ownThrown = ticks;
				ownLastThrow = ticks;
				ownStreak = Math.min(ThrownRocks.MOMENTUM_MAX, ownMomentum + 1);
				ClientPlayNetworking.send(new ThrownRocks.Charge(ThrownRocks.THROWING));
				return ThrownRocks.THROWING;
			}
			// let go before it was full (or the rock's gone, or a screen opened)
			ClientPlayNetworking.send(new ThrownRocks.Charge(0));
		}
		ownStart = -1;
		ownThrown = -1;
		ownReady = false;
		return 0;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATE.clear();
			ownStart = -1;
			return;
		}
		if (minecraft.isPaused()) {
			if (ownStart >= 0 && ownThrown < 0 && minecraft.getConnection() != null) {
				// (0.20.103) paused mid-hold: the hold is let down, not thrown on the way back
				ClientPlayNetworking.send(new ThrownRocks.Charge(0));
				ownStart = -1;
				ownReady = false;
			}
			return;
		}
		ticks++;
		for (AbstractClientPlayer player : level.players()) {
			int value;
			if (player == minecraft.player) {
				int stage = ownStage();
				// each hold its own number: begun on a new tick
				value = stage == 0 ? 0 : ThrownRocks.value(ownStart, ownCharge, stage);
				Integer synced = player.getAttached(ThrownRocks.THROW);
				if (stage == 0 && synced != null && (synced & ThrownRocks.MAIN_ARM) != 0) {
					// (0.21.8) your Lift's hurl, which the server starts
					value = synced;
				}
			} else {
				Integer synced = player.getAttached(ThrownRocks.THROW);
				value = synced == null ? 0 : synced;
			}
			Throw s = STATE.get(player.getId());
			if (s == null) {
				if (value == 0) {
					continue;
				}
				s = new Throw();
				STATE.put(player.getId(), s);
			}
			s.amountBefore = s.amount;
			s.ticksBefore = s.ticks;
			if (value != 0) {
				s.amount = Math.min(1.0F, s.amount + EASE);
				if (s.value != value) {
					s.stage = value & 3;
					s.ticks = 0.0F;
					s.ticksBefore = 0.0F;
					s.value = value;
					s.lastValue = value;
				} else {
					s.ticks++;
				}
			} else {
				// let go or followed through: the last pose eases out
				s.amount = Math.max(0.0F, s.amount - EASE);
				s.ticks++;
				s.value = 0;
			}
		}
		STATE.entrySet().removeIf(entry -> entry.getValue().amount <= 0.0F && entry.getValue().amountBefore <= 0.0F
			|| level.getEntity(entry.getKey()) == null);
	}

	private static @Nullable Pose pose(LivingEntity entity, float partialTicks) {
		Throw s = STATE.get(entity.getId());
		if (s == null || !(entity instanceof Player player)) {
			return null;
		}
		float amount = Mth.lerp(partialTicks, s.amountBefore, s.amount);
		if (amount <= 0.001F) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		int value = s.value != 0 ? s.value : s.lastValue;
		HumanoidArm arm = (value & ThrownRocks.MAIN_ARM) != 0 ? player.getMainArm() : player.getMainArm().getOpposite();
		return new Pose(s.stage, arm, Mth.lerp(partialTicks, s.ticksBefore, s.ticks), amount, ownView,
			Math.max(1, ThrownRocks.chargeOf(s.value != 0 ? s.value : s.lastValue)));
	}

	/** From HeldItems.extract (LivingEntityRenderer.extractRenderState). */
	static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		state.setData(KEY, state instanceof AvatarRenderState ? pose(entity, partialTicks) : null);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/**
	 * Where the pose is, 0 drawn back to 1 thrown through, and how much it shows over vanilla's own (0-1), for a stage
	 * {@code ticks} in: {through, weight, drawn back (0-1, for the wind-up's lift)}.
	 */
	private static float[] phase(Pose pose) {
		float t = pose.ticks();
		if (pose.stage() == ThrownRocks.THROWING) {
			float k = Mth.clamp(t / WHIP_TICKS, 0.0F, 1.0F);
			// fast through, then eased out over the follow-through
			float out = smooth((t - WHIP_TICKS) / Math.max(1.0F, ThrownRocks.FOLLOW_TICKS - WHIP_TICKS));
			return new float[] {k * k, (1.0F - out) * pose.amount(), 1.0F};
		}
		// (0.20.103) held at full charge: drawn right back
		return new float[] {0.0F, pose.amount(), pose.stage() == ThrownRocks.READY ? 1.0F : smooth(t / pose.charge())};
	}

	/** After the model animates (HeldItems.applyModel): the arms and the body's turn. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || avatar.deathTime > 0.0F) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		float[] p = phase(pose);
		float through = p[0], weight = p[1], drawn = p[2];
		if (weight <= 0.001F) {
			return;
		}
		boolean right = pose.arm() == HumanoidArm.RIGHT;
		float side = right ? 1.0F : -1.0F;
		ModelPart arm = right ? h.rightArm : h.leftArm, other = right ? h.leftArm : h.rightArm;
		float shake = pose.stage() != ThrownRocks.THROWING ? SHAKE * drawn * drawn * Mth.sin(pose.ticks() * SHAKE_BEAT) : 0.0F;
		// the wind-up comes up from wherever the arm was; the throw goes from drawn back to through
		float w = weight * drawn;
		set(arm, Mth.lerp(through, COCK_X, THROUGH_X) + shake, Mth.lerp(through, COCK_Y, THROUGH_Y) * side,
			Mth.lerp(through, COCK_Z, THROUGH_Z) * side + shake, w);
		set(other, Mth.lerp(through, AIM_X, BACK_X), Mth.lerp(through, AIM_Y, BACK_Y) * side, Mth.lerp(through, AIM_Z, BACK_Z) * side, w);
		if (pose.ownView()) {
			return;
		}
		// the shoulders turn with the body, the throwing one drawn back and then whipped through
		float twist = Mth.lerp(through, TWIST_COCK, TWIST_THROUGH) * side * w;
		h.body.yRot += twist;
		turn(h.rightArm, twist);
		turn(h.leftArm, twist);
	}

	/** An arm carried round with the body's turn (as vanilla's own attack swing moves the shoulders). */
	private static void turn(ModelPart arm, float twist) {
		float cos = Mth.cos(twist), sin = Mth.sin(twist);
		float x = arm.x, z = arm.z;
		arm.x = x * cos + z * sin;
		arm.z = z * cos - x * sin;
		arm.yRot += twist;
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	/**
	 * (0.21.8) Your own Lift's hurl in first person (FirstPersonArms, the empty main arm): {through (0 drawn back, 1 thrown
	 * through), weight}, or null.
	 */
	public static float @Nullable [] hurl(float partialTicks) {
		LocalPlayer player = Minecraft.getInstance().player;
		Pose pose = player == null ? null : pose(player, partialTicks);
		if (pose == null || pose.arm() != player.getMainArm() || pose.stage() != ThrownRocks.THROWING) {
			return null;
		}
		float[] p = phase(pose);
		return new float[] {p[0], p[1] * p[2]};
	}

	/** First person, just before the held rock is drawn (HeldItems.firstPerson): drawn back, thrown, the hand dipping after. */
	static void firstPerson(PoseStack poseStack, HumanoidArm arm, ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !FirstTools.rock(stack) || arm != player.getMainArm().getOpposite()) {
			return;
		}
		Pose pose = pose(player, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (pose == null) {
			return;
		}
		float[] p = phase(pose);
		float through = p[0], weight = p[1] * p[2];
		if (weight <= 0.001F) {
			return;
		}
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		float shake = pose.stage() != ThrownRocks.THROWING ? FP_SHAKE * p[2] * Mth.sin(pose.ticks() * SHAKE_BEAT) : 0.0F;
		// after the throw the hand dips out of view and comes back up as the pose eases out
		float dip = pose.stage() == ThrownRocks.THROWING && pose.ticks() > WHIP_TICKS ? Mth.sin((1.0F - p[1]) * Mth.PI) * FP_DIP : 0.0F;
		poseStack.translate(Mth.lerp(through, FP_COCK_X, FP_THROUGH_X) * side * weight + shake,
			Mth.lerp(through, FP_COCK_Y, FP_THROUGH_Y) * weight + shake - dip, Mth.lerp(through, FP_COCK_Z, FP_THROUGH_Z) * weight);
		poseStack.rotateDegrees(Axis.XP, Mth.lerp(through, FP_COCK_PITCH, FP_THROUGH_PITCH) * weight);
		poseStack.rotateDegrees(Axis.ZP, FP_COCK_ROLL * (1.0F - through) * side * weight);
	}
}
