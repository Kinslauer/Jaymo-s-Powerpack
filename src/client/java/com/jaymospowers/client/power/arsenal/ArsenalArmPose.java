package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import com.jaymospowers.power.arsenal.ArsenalReach;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The weapon arm during Reach Through (the same render-state approach as the other powers' poses): it rises and
 * reaches into the portal while it opens, hauls back toward the chest as the weapon is drawn out, and eases back to
 * normal as the portal closes.
 * <p>
 * Also the Void Flintlock's aim ({@link #AIM_KEY}): a hand holding a loaded one raises it level with the look, arm
 * straight out like a duellist's pistol; firing kicks it up, and once it's spent the arm drops back.
 */
public final class ArsenalArmPose {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:arsenal_reach_arm");

	/** Shoulder pivot above the feet and out to the side, and shoulder-to-hand length (model units / 16). */
	private static final double SHOULDER_HEIGHT = 22.0 / 16.0;
	private static final double SHOULDER_OUT = 5.0 / 16.0;
	private static final double ARM_LENGTH = 10.0 / 16.0;

	/** Flintlock aim: ticks to raise it, how far the hand turns in toward the sight line (radians). */
	private static final float AIM_RAISE_TICKS = 4.0F;
	private static final float AIM_INWARD = 0.08F;
	/** Recoil: the kick (radians, up), ticks to the top of it, ticks to settle; then ticks to lower a spent gun. */
	private static final float RECOIL_KICK = 0.55F;
	private static final float RECOIL_RISE_TICKS = 1.0F;
	private static final float RECOIL_SETTLE_TICKS = 6.0F;
	private static final float SPENT_LOWER_TICKS = 8.0F;

	public static final RenderStateDataKey<Aim> AIM_KEY = RenderStateDataKey.create(() -> "jaymospowers:arsenal_flintlock_aim");

	/** Each hand's raise (0-1) and recoil (radians). */
	public record Aim(float right, float rightKick, float left, float leftKick) {
	}

	/** Per entity and hand ({@code id * 2 + (left ? 1 : 0)}): when the gun came up, whether it's spent, when it fired. */
	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static final int MAX_TRACKS = 256;

	private record Track(float raisedAt, boolean spent, float firedAt) {
	}

	/** {@code reach}: 0-1 how far into the portal; {@code pull}: 0-1 how far it's hauled back; {@code amount}: blend. */
	public record Pose(float amount, float aimX, float aimY, float pull) {
	}

	private ArsenalArmPose() {
	}

	private static Vec3 shoulder(Player player, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
		double scale = player.getScale();
		return player.getPosition(partialTick).add(0.0, SHOULDER_HEIGHT * scale, 0.0)
			.add(ArsenalReach.right(bodyYaw).scale(ArsenalReach.side(player) * SHOULDER_OUT * scale));
	}

	/** Where the hand is during the pull: out toward the portal, coming back in to the chest (for the emerging weapon). */
	static Vec3 handPos(Player player, float partialTick) {
		Vec3 shoulder = shoulder(player, partialTick);
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
		Vec3 toPortal = ArsenalReach.portalCenter(player, partialTick).subtract(shoulder).normalize();
		Vec3 held = ArsenalReach.forward(bodyYaw).scale(0.8).add(0.0, -0.6, 0.0).normalize();
		float pull = pullAmount(player, partialTick);
		return shoulder.add(toPortal.lerp(held, pull).normalize().scale(ARM_LENGTH * player.getScale()));
	}

	private static float time(Player player, float partialTick) {
		ArsenalReach.Reach reach = player.getAttached(ArsenalReach.REACH);
		Minecraft minecraft = Minecraft.getInstance();
		if (reach == null || minecraft.level == null) {
			return -1.0F;
		}
		return minecraft.level.getGameTime() - reach.start() + partialTick;
	}

	private static float pullAmount(Player player, float partialTick) {
		float t = time(player, partialTick);
		float x = Mth.clamp((t - ArsenalReach.REACH_END) / (ArsenalReach.PULL_END - ArsenalReach.REACH_END), 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			state.setData(KEY, null);
			state.setData(AIM_KEY, null);
			return;
		}
		extractAim(player, state, partialTicks);
		float t = time(player, partialTicks);
		if (t < 0.0F || t > ArsenalReach.CLOSE_END) {
			state.setData(KEY, null);
			return;
		}
		// rise into it, hold through the pull, ease off as it closes
		float amount = t < ArsenalReach.OPEN_END ? t / ArsenalReach.OPEN_END
			: t < ArsenalReach.PULL_END ? 1.0F
			: 1.0F - (t - ArsenalReach.PULL_END) / (ArsenalReach.CLOSE_END - ArsenalReach.PULL_END);
		Vec3 shoulder = shoulder(player, partialTicks);
		Vec3 dir = ArsenalReach.portalCenter(player, partialTicks).subtract(shoulder);
		if (dir.lengthSqr() < 1.0E-6) {
			state.setData(KEY, null);
			return;
		}
		dir = dir.normalize();
		float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);
		float elevation = (float)Math.asin(Mth.clamp(dir.y, -1.0, 1.0));
		float yaw = (float)(Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
		float relative = Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD;
		state.setData(KEY, new Pose(Mth.clamp(amount, 0.0F, 1.0F), -Mth.HALF_PI - elevation, relative, pullAmount(player, partialTicks)));
	}

	private static void extractAim(Player player, AvatarRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || ArsenalFlintlock.FLINTLOCK == null) {
			state.setData(AIM_KEY, null);
			return;
		}
		float now = minecraft.level.getGameTime() + partialTicks;
		boolean free = !player.isSpectator() && !player.isSleeping() && !player.isVisuallySwimming() && !player.isFallFlying();
		ItemStack rightStack = player.getMainArm() == HumanoidArm.RIGHT ? player.getMainHandItem() : player.getOffhandItem();
		ItemStack leftStack = player.getMainArm() == HumanoidArm.RIGHT ? player.getOffhandItem() : player.getMainHandItem();
		float[] right = hand(player.getId() * 2, rightStack, free, now);
		float[] left = hand(player.getId() * 2 + 1, leftStack, free, now);
		state.setData(AIM_KEY, right[0] <= 0.0F && left[0] <= 0.0F ? null : new Aim(right[0], right[1], left[0], left[1]));
	}

	/** One hand's {raise, kick}, keeping its track (dropped once the hand holds no flintlock). */
	private static float[] hand(int key, ItemStack stack, boolean free, float now) {
		if (!stack.is(ArsenalFlintlock.FLINTLOCK)) {
			TRACKS.remove(key);
			return new float[] {0.0F, 0.0F};
		}
		boolean spent = Boolean.TRUE.equals(stack.get(ArsenalFlintlock.SPENT));
		Track track = TRACKS.get(key);
		if (track == null && TRACKS.size() > MAX_TRACKS) {
			// players who walked off holding one
			TRACKS.clear();
		}
		if (track == null) {
			// first seen: a gun already spent was fired out of sight, so no kick
			track = new Track(now, spent, spent ? now - RECOIL_SETTLE_TICKS - SPENT_LOWER_TICKS : Float.NaN);
		} else if (spent && !track.spent()) {
			track = new Track(track.raisedAt(), true, now);
		} else if (!spent && track.spent()) {
			// a fresh one in the same hand: bring it up again
			track = new Track(now, false, Float.NaN);
		}
		TRACKS.put(key, track);
		if (!free) {
			return new float[] {0.0F, 0.0F};
		}
		float raise = smooth((now - track.raisedAt()) / AIM_RAISE_TICKS);
		if (!spent) {
			return new float[] {raise, 0.0F};
		}
		float since = now - track.firedAt();
		float kick = since < RECOIL_RISE_TICKS ? since / RECOIL_RISE_TICKS
			: 1.0F - smooth((since - RECOIL_RISE_TICKS) / RECOIL_SETTLE_TICKS);
		float lower = 1.0F - smooth((since - RECOIL_RISE_TICKS - RECOIL_SETTLE_TICKS) / SPENT_LOWER_TICKS);
		return new float[] {raise * lower, Math.max(0.0F, kick) * RECOIL_KICK};
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The flintlock aim on a humanoid model, after it animates (the body, and before the held-item layers). */
	public static void applyAim(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Aim aim = avatar.getData(AIM_KEY);
		if (aim == null) {
			return;
		}
		ModelPart head = humanoid.head;
		if (aim.right() > 0.0F) {
			set(humanoid.rightArm, head.xRot - Mth.HALF_PI - aim.rightKick(), head.yRot - AIM_INWARD, 0.0F, aim.right());
		}
		if (aim.left() > 0.0F) {
			set(humanoid.leftArm, head.xRot - Mth.HALF_PI - aim.leftKick(), head.yRot + AIM_INWARD, 0.0F, aim.left());
		}
	}

	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		applyAim(model, state);
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		ModelPart arm = avatar.mainArm == HumanoidArm.LEFT ? humanoid.leftArm : humanoid.rightArm;
		float outward = arm == humanoid.rightArm ? 1.0F : -1.0F;
		// reaching: straight at the portal (pushing in a touch further); pulled: elbow back, weapon at the chest
		float reachX = pose.aimX() - 0.15F;
		float pulledX = -0.9F;
		float x = Mth.lerp(pose.pull(), reachX, pulledX);
		float y = Mth.lerp(pose.pull(), pose.aimY(), -outward * 0.35F);
		float z = Mth.lerp(pose.pull(), 0.0F, outward * 0.15F);
		set(arm, x, y, z, pose.amount());
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
