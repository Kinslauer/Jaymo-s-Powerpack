package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.power.zfighter.ZFighterSonicSway;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.54, Andy: "work this kind of sonic sway animation in somewhere") The sonic sway ({@link ZFighterSonicSway}), on
 * everyone: the upper body (hips up) tips from side to side {@link #CYCLES} times a second, the head held more upright than
 * the body, the arms hanging loose and swinging a beat behind, the legs planted a little apart. A blow that misses snaps the
 * lean to the side away from it, and a blow slipped some other way plays one quick lean ({@link #FLICK}).
 *
 * <p>The afterimages are the player's own render state, kept from the frames the world drew (as Chrono's streak does), drawn
 * again every {@link #GHOST_EVERY} ticks at the end of each lean, a little to that side, in a pale translucent tint, fading
 * over {@link #GHOST_LIFE} ticks. Each wears the lean it was kept in.
 */
public final class ZFighterSwayPose {
	/** The lean at its extreme (radians, about 18 degrees), the cycles a second, and the head's share of it that is held back. */
	static final float ROLL = 0.32F, CYCLES = 3.2F, HEAD_HOLD = 0.6F;
	/** The body twists a little into the lean (share of the roll), sinks, and the arms hang this far out and swing this far. */
	static final float TWIST = 0.45F, SINK = 0.5F, ARM_OUT = 0.14F, ARM_SWING = 0.3F, ARM_LAG = 0.9F, STANCE = 0.08F;
	/** A blow's snap: the extra lean (radians) and how quickly it dies away (ticks), and the sway it quiets while it does. */
	static final float SNAP = 0.5F, SNAP_TICKS = 2.5F, SNAP_QUIET = 0.7F;
	/** The single lean's size (radians). */
	static final float FLICK = 0.45F;
	/** How quickly the pose comes up and dies away (share of a tick). */
	static final float EASE = 0.34F;
	/** Afterimages: one every this many ticks while swaying, how long they last, how far aside (blocks), how opaque, their tint. */
	static final int GHOST_EVERY = 2, GHOST_LIFE = 7;
	static final double GHOST_ASIDE = 0.32;
	static final float GHOST_ALPHA = 0.45F;
	static final int GHOST_RGB = 0xBFDCFF;
	/** Your own afterimages closer than this to the camera (blocks) are skipped in first person. */
	static final double GHOST_NEAR_CAMERA = 1.2;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** The pose on a render state: whose it is, how much shows (0-1), which kind, the age of the last snap (ticks) and its side. */
	public record Pose(int id, float amount, int mode, float snapAge, int snapLean) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_sway");

	/** One player's state: how much shows now and before, which kind, the raw value last seen, and the last snap. */
	private static final class State {
		float amount, amountO;
		int mode;
		int raw = -1;
		long snapTick = Long.MIN_VALUE / 2;
		int snapLean = 1;
	}

	private static final Int2ObjectMap<State> STATE = new Int2ObjectOpenHashMap<>();
	/** The world's latest frame of each swaying player, and the afterimages kept. */
	private static final Int2ObjectMap<AvatarRenderState> LATEST = new Int2ObjectOpenHashMap<>();
	private static final List<Ghost> GHOSTS = new ArrayList<>();

	private record Ghost(long tick, AvatarRenderState frame, double x, double y, double z) {
	}

	private ZFighterSwayPose() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterSwayPose::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(ZFighterSwayPose::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			STATE.clear();
			LATEST.clear();
			GHOSTS.clear();
		});
	}

	/** The sway's sideways swing at this time: -1 to 1, positive to his right. */
	static float swing(float ageInTicks) {
		return Mth.sin(ageInTicks * Mth.TWO_PI * CYCLES / 20.0F);
	}

	/** Every client tick: the state of each player, a snap noted when the server's value changes, and the afterimages. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATE.clear();
			GHOSTS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		for (AbstractClientPlayer player : level.players()) {
			Integer raw = player.getAttached(ZFighterSonicSway.SWAY);
			State s = STATE.get(player.getId());
			if (s == null) {
				if (raw == null) {
					continue;
				}
				s = new State();
				STATE.put(player.getId(), s);
			}
			s.amountO = s.amount;
			if (raw != null) {
				int mode = ZFighterSonicSway.mode(raw);
				// a new lean: the count moved on (a single lean is a lean as soon as it appears)
				boolean snap = s.raw < 0 ? mode == ZFighterSonicSway.MODE_FLICK : ZFighterSonicSway.count(raw) != ZFighterSonicSway.count(s.raw);
				s.raw = raw;
				s.mode = mode;
				s.amount = Math.min(1.0F, s.amount + EASE);
				if (snap) {
					s.snapTick = now;
					s.snapLean = ZFighterSonicSway.lean(raw);
					ghost(player.getId(), s.snapLean);
				}
			} else {
				s.raw = -1;
				s.amount = Math.max(0.0F, s.amount - EASE);
			}
			if (raw != null && s.mode == ZFighterSonicSway.MODE_SWAY && s.amount > 0.5F && now % GHOST_EVERY == 0) {
				AvatarRenderState frame = LATEST.get(player.getId());
				if (frame != null) {
					ghost(player.getId(), swing(frame.ageInTicks + 1.0F) >= 0.0F ? 1 : -1);
				}
			}
		}
		STATE.int2ObjectEntrySet().removeIf(entry -> entry.getValue().amount <= 0.0F && entry.getValue().amountO <= 0.0F || level.getEntity(entry.getIntKey()) == null);
		LATEST.int2ObjectEntrySet().removeIf(entry -> !STATE.containsKey(entry.getIntKey()));
		GHOSTS.removeIf(g -> now - g.tick() > GHOST_LIFE);
	}

	/** Keeps an afterimage of {@code id} at the end of a lean to {@code lean} (1 his right). */
	private static void ghost(int id, int lean) {
		AvatarRenderState frame = LATEST.get(id);
		ClientLevel level = Minecraft.getInstance().level;
		if (frame == null || level == null) {
			return;
		}
		// his right-hand side, from the way his body faces
		double yaw = Math.toRadians(frame.bodyRot);
		double aside = lean * GHOST_ASIDE;
		GHOSTS.add(new Ghost(level.getGameTime(), frame, frame.x - Math.cos(yaw) * aside, frame.y, frame.z - Math.sin(yaw) * aside));
	}

	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		State s = STATE.get(entity.getId());
		ClientLevel level = Minecraft.getInstance().level;
		if (s == null || level == null) {
			state.setData(KEY, null);
			return;
		}
		float amount = Mth.lerp(partialTicks, s.amountO, s.amount);
		if (amount <= 0.001F) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Pose(entity.getId(), amount, s.mode, level.getGameTime() - s.snapTick + partialTicks, s.snapLean));
	}

	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || avatar.deathTime > 0.0F) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		float a = ZFighterFlightPose.smooth(pose.amount());
		float t = avatar.ageInTicks;
		float lean;
		float arms;
		if (pose.mode() == ZFighterSonicSway.MODE_FLICK) {
			lean = pose.snapLean() * FLICK / ROLL * Mth.sin(Mth.PI * Mth.clamp(pose.snapAge() / ZFighterSonicSway.FLICK_TICKS, 0.0F, 1.0F));
			arms = lean * 0.6F;
		} else {
			float snap = (float)Math.exp(-Math.max(0.0F, pose.snapAge()) / SNAP_TICKS);
			lean = swing(t) * (1.0F - SNAP_QUIET * snap) + pose.snapLean() * SNAP / ROLL * snap;
			arms = swing(t - ARM_LAG);
		}
		// positive roll tips the top toward his left
		float roll = -lean * ROLL * a;
		h.head.zRot -= roll * HEAD_HOLD;
		h.rightArm.xRot -= 0.15F * a;
		h.leftArm.xRot -= 0.15F * a;
		h.rightArm.zRot += (ARM_OUT - arms * ARM_SWING) * a;
		h.leftArm.zRot += (-ARM_OUT - arms * ARM_SWING) * a;
		h.rightLeg.zRot += STANCE * a;
		h.leftLeg.zRot -= STANCE * a;
		PoseBlend.bend(h, 0.05F * a, -roll * TWIST, roll, SINK * a, 0.0F);
	}

	/** Draws the afterimages (the world's frame of each swaying player is kept for them). */
	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || STATE.isEmpty() && GHOSTS.isEmpty()) {
			return;
		}
		for (EntityRenderState state : context.levelState().entityRenderStates) {
			if (state instanceof AvatarRenderState avatar) {
				Pose pose = avatar.getData(KEY);
				if (pose != null) {
					LATEST.put(pose.id(), avatar);
				}
			}
		}
		if (GHOSTS.isEmpty()) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = level.getGameTime() + partialTick;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		var dispatcher = minecraft.getEntityRenderDispatcher();
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		for (Ghost ghost : GHOSTS) {
			float age = time - ghost.tick();
			float alpha = GHOST_ALPHA * Mth.clamp(1.0F - age / GHOST_LIFE, 0.0F, 1.0F);
			AvatarRenderState state = ghost.frame();
			if (alpha < 0.02F || age < 0.0F) {
				continue;
			}
			if (firstPerson && new Vec3(ghost.x(), ghost.y() + 1.0, ghost.z()).distanceToSqr(camera) < GHOST_NEAR_CAMERA * GHOST_NEAR_CAMERA) {
				continue;
			}
			AvatarRenderer<?> renderer = dispatcher.getRenderer(state);
			PlayerModel model = renderer.getModel();
			Identifier texture = renderer.getTextureLocation(state);
			poseStack.pushPose();
			// as LivingEntityRenderer.submit lays the body out
			poseStack.translate(ghost.x() - camera.x, ghost.y() - camera.y, ghost.z() - camera.z);
			poseStack.scale(state.scale, state.scale, state.scale);
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.bodyRot);
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.scale(0.9375F, 0.9375F, 0.9375F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			int tint = ARGB.color(Math.round(alpha * 255.0F), GHOST_RGB);
			collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint,
				null, EntityRenderState.NO_OUTLINE);
			poseStack.popPose();
		}
	}
}
