package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Tomes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.390) The spell tome afloat ({@link Tomes#AFLOAT}): drawn open in the air before whoever casts with it, off the right
 * shoulder, bobbing slowly and swinging round after their aim a little behind it, tipped back so its pages face them. Its
 * pages are the same frames the book has in the hand ({@code tome_pose}, {@link TomeClient#pose}): a leaf turns as it rises,
 * at each new lock and with every bolt or spell. While it floats the hand holding it is drawn empty (first person:
 * {@code TomeFloatHandMixin}; everyone else: {@code TomeHeldItemMixin}).
 */
public final class TomeFloat {
	/** How quickly it swings round after the aim (per second: higher follows tighter). */
	static final float FOLLOW = 9.0F;
	/** How far it bobs up and down (blocks), and how long one bob takes (seconds); and its slight sway (degrees). */
	static final float BOB = 0.035F, BOB_SECONDS = 2.8F, SWAY = 3.0F;
	/** Its size, how far it's tipped back (degrees), and how far turned in toward the eyes from the right shoulder. */
	static final float SCALE = 0.55F, TILT = 28.0F, TURN = -14.0F;
	/** A leaf turned by a new lock or the book rising takes this long (ms), as one turned in the hand. */
	static final long TURN_MS = TomeClient.FLIP_MS;

	/** Each floating book's own aim (a little behind its owner's), and when its number last changed. */
	private static final class Drift {
		float yaw, pitch;
		long at, turnedAt;
		int stamp;
	}

	private static final Map<Integer, Drift> DRIFTS = new HashMap<>();
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private TomeFloat() {
	}

	static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(TomeFloat::render);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				DRIFTS.clear();
				return;
			}
			DRIFTS.keySet().removeIf(id -> !(minecraft.level.getEntity(id) instanceof Player player) || !afloat(player));
		});
	}

	/** Whether {@code player}'s tome floats (your own as soon as you start to charge, before the server's word comes back). */
	public static boolean afloat(Player player) {
		if (!Tomes.isTome(player.getMainHandItem())) {
			return false;
		}
		Integer stamp = player.getAttached(Tomes.AFLOAT);
		return stamp != null && stamp > 0 || player == Minecraft.getInstance().player && TomeClient.charging();
	}

	/** How far (0 to 1) a leaf has turned since {@code player}'s book rose or took a new lock, or -1 once it has. */
	static float turning(Player player, long now) {
		Drift drift = DRIFTS.get(player.getId());
		if (drift == null || now - drift.turnedAt >= TURN_MS) {
			return -1.0F;
		}
		return (now - drift.turnedAt) / (float) TURN_MS;
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		long now = Util.getMillis();
		PoseStack pose = context.poseStack();
		for (Player player : minecraft.level.players()) {
			if (!afloat(player) || player.isInvisibleTo(minecraft.player)) {
				continue;
			}
			float yaw = player.getViewYRot(partial), pitch = player.getViewXRot(partial);
			Integer stamp = player.getAttached(Tomes.AFLOAT);
			int number = stamp == null ? 0 : stamp;
			Drift drift = DRIFTS.get(player.getId());
			if (drift == null) {
				// just risen: it starts where they aim
				drift = new Drift();
				drift.yaw = yaw;
				drift.pitch = pitch;
				drift.at = now;
				drift.turnedAt = now;
				drift.stamp = number;
				DRIFTS.put(player.getId(), drift);
			}
			if (number != drift.stamp) {
				drift.stamp = number;
				drift.turnedAt = now;
			}
			float follow = 1.0F - (float) Math.exp(-FOLLOW * Math.min(now - drift.at, 250L) / 1000.0F);
			drift.yaw += Mth.wrapDegrees(yaw - drift.yaw) * follow;
			drift.pitch += (pitch - drift.pitch) * follow;
			drift.at = now;

			// each book bobs in its own time
			float time = (now % 100_000L) / 1000.0F + (player.getId() & 31) * 0.37F;
			float bob = Mth.sin(time * Mth.TWO_PI / BOB_SECONDS) * BOB;
			Vec3 eye = player.getEyePosition(partial).subtract(camera);
			ItemStack stack = player.getMainHandItem();
			minecraft.getItemModelResolver().updateForTopItem(ITEM, stack, ItemDisplayContext.NONE, minecraft.level, player, player.getId());
			pose.pushPose();
			pose.translate(eye.x, eye.y, eye.z);
			// the frame of its aim: +z ahead, +y up, -x to the right (as Tomes.floatAt)
			pose.rotateDegrees(Axis.YP, -drift.yaw);
			pose.rotateDegrees(Axis.XP, drift.pitch);
			pose.translate((float) -Tomes.FLOAT_SIDE, (float) Tomes.FLOAT_UP + bob, (float) Tomes.FLOAT_AHEAD);
			// the open pages (the model's north) turned toward the eyes, tipped back, swaying a little
			pose.rotateDegrees(Axis.YP, TURN);
			pose.rotateDegrees(Axis.XP, TILT);
			pose.rotateDegrees(Axis.ZP, Mth.sin(time * Mth.TWO_PI / (BOB_SECONDS * 1.7F)) * SWAY);
			pose.scale(SCALE, SCALE, SCALE);
			int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partial);
			ITEM.submit(pose, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}
}
