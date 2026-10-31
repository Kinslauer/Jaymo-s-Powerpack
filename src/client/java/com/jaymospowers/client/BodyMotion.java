package com.jaymospowers.client;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (Motion patch 1) The small motions every player's body makes, after Actions &amp; Stuff's torso lag, strafe lean, hurt
 * flinch and scared idle. All of it is laid over whatever the model already does (Fresh Animations included, never
 * paused for it), and only when none of our own poses is on, so any pose wins. Worked out from synced state (head and
 * body turn, movement, hurt time, lock-on, health, the player's temperament), so everyone in view sees the same.
 * <ul>
 * <li><b>Torso twist:</b> the chest turns a share of the way after the head ({@link #TORSO_SHARE}, at most
 * {@link #TORSO_MAX}), the shoulders carried round with it.</li>
 * <li><b>Strafe lean:</b> locked on ({@link LockOn#locked}), side steps lean the body into the move.</li>
 * <li><b>Flinch:</b> a hit jerks the body away from it while the red flash lasts; never while downed.</li>
 * <li><b>Temperament</b> (Jaymo's Kingdoms gives each player one from their life story, for good): timid and
 * suspicious players hunch and glance about with their hands close when it's dark, they're hurt or a monster is near;
 * hotheaded ones stand with their arms tense and ready; gloomy ones slump; cheerful ones walk with a bounce; brave ones
 * barely flinch.</li>
 * </ul>
 */
public final class BodyMotion {
	// ===== the knobs ================================================================================================
	/** Torso twist: the share of the head's turn (from the body) the chest follows, and at most this many degrees. */
	static final float TORSO_SHARE = 0.35F, TORSO_MAX = 22.0F;
	/** Strafe lean: degrees at full sideways speed; blocks a tick that counts as full; how fast it eases (share a frame). */
	static final float STRAFE_LEAN = 9.0F, STRAFE_FULL_SPEED = 0.18F, STRAFE_EASE = 0.2F;
	/** Flinch: degrees at the start of a hit, and the share of it a brave player shows. */
	static final float FLINCH_DEGREES = 9.0F, BRAVE_FLINCH = 0.35F;
	/** Scared idle: light at or under this is dark, health share at or under this is hurt, a monster this near (blocks). */
	static final int SCARED_LIGHT = 4;
	static final float SCARED_HEALTH = 0.4F;
	static final double SCARED_MONSTER_RANGE = 10.0;
	/** Scared idle: the hunch (degrees), the glance (radians of head turn, its speed), the hands drawn in. */
	static final float SCARED_HUNCH = 7.0F, GLANCE = 0.55F, GLANCE_SPEED = 0.07F, HANDS_IN = 0.35F;
	/** Gloomy: the slump (degrees) and the head hung (radians). Hotheaded: arms forward and out (radians). */
	static final float GLOOMY_SLUMP = 4.0F, GLOOMY_HEAD = 0.3F, HOT_ARMS_FORWARD = 0.3F, HOT_ARMS_OUT = 0.12F;
	/** Cheerful: the bounce in the walk (blocks at full walking speed). */
	static final float CHEERFUL_BOUNCE = 0.06F;
	/** Idles only show when walking slower than this (walk animation speed), and ease in over this many ticks. */
	static final float IDLE_BELOW = 0.35F, IDLE_EASE_TICKS = 10.0F;
	/** How often the scared check looks around (ticks): checking every frame for monsters would cost too much. */
	static final int SCARED_CHECK_TICKS = 10;
	// ================================================================================================================

	/** Temperaments with their own look here. */
	enum Mood {
		NONE, SCARED, HOTHEADED, GLOOMY, CHEERFUL
	}

	/**
	 * {@code twist}: degrees the chest turns after the head; {@code strafe}: −1 (left) to 1 (right); {@code flinch}: 0-1
	 * left of a hit, {@code hurtDir} where it came from; {@code mood} and {@code moodIn} (0-1) the temperament's look;
	 * {@code time} ticks, for the glance; {@code bounce}: the walk's bounce now (blocks).
	 */
	public record Motion(float twist, float strafe, float flinch, float hurtDir, Mood mood, float moodIn, float time, float bounce) {
	}

	public static final RenderStateDataKey<Motion> KEY = RenderStateDataKey.create(() -> "jaymospowers:body_motion");

	private static final Map<Integer, Float> STRAFE = new HashMap<>();
	private static final Map<Integer, Float> MOOD_IN = new HashMap<>();
	private static final Map<Integer, Float> LAST_FRAME = new HashMap<>();
	/** Per entity id: the last scared check's game time and answer. */
	private static final Map<Integer, Long> SCARED_AT = new HashMap<>();
	private static final Map<Integer, Boolean> SCARED = new HashMap<>();

	private static boolean temperamentLooked;
	private static @Nullable AttachmentType<String> temperament;

	private BodyMotion() {
	}

	/** Jaymo's Kingdoms' temperament attachment, when it's here. */
	@SuppressWarnings("unchecked")
	static @Nullable String temperament(Entity entity) {
		if (!temperamentLooked) {
			temperamentLooked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
				try {
					temperament = (AttachmentType<String>)Class.forName("com.jaymoskingdoms.Temperaments").getField("TEMPERAMENT").get(null);
				} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
					temperament = null;
				}
			}
		}
		return temperament == null ? null : entity.getAttached(temperament);
	}

	/** Timid and suspicious players: the ones who jump at shadows (scared idle, the panicked look back). */
	public static boolean jumpy(Entity entity) {
		String t = temperament(entity);
		return "timid".equals(t) || "suspicious".equals(t);
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !(state instanceof AvatarRenderState avatar) || player.isSleeping()
			|| player.isPassenger() || state.pose != Pose.STANDING && state.pose != Pose.CROUCHING || avatar.isFallFlying
			|| avatar.isVisuallySwimming || Downed.isDowned(player) || player.isSpectator()) {
			forget(entity.getId());
			state.setData(KEY, null);
			return;
		}
		int id = entity.getId();
		float time = entity.level().getGameTime() + partialTicks;
		float last = LAST_FRAME.getOrDefault(id, time);
		LAST_FRAME.put(id, time);
		float frame = Mth.clamp(time - last, 0.0F, 2.0F);

		float twist = Mth.clamp(Mth.wrapDegrees(state.yRot) * TORSO_SHARE, -TORSO_MAX, TORSO_MAX);

		// sideways speed in the body's frame (from positions, which are right for every player in view)
		float side = 0.0F;
		if (LockOn.locked(player) && player.onGround()) {
			double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
			float yaw = state.bodyRot * Mth.DEG_TO_RAD;
			double right = dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw);
			side = Mth.clamp((float)(right / STRAFE_FULL_SPEED), -1.0F, 1.0F);
		}
		float strafe = STRAFE.getOrDefault(id, 0.0F);
		strafe += (side - strafe) * Math.min(1.0F, STRAFE_EASE * frame * 3.0F);
		STRAFE.put(id, strafe);

		String t = temperament(player);
		float flinch = 0.0F;
		if (player.hurtTime > 0 && player.hurtDuration > 0) {
			flinch = Mth.clamp((player.hurtTime - partialTicks) / player.hurtDuration, 0.0F, 1.0F);
			if ("brave".equals(t)) {
				flinch *= BRAVE_FLINCH;
			}
		}

		Mood mood = switch (t == null ? "" : t) {
			case "timid", "suspicious" -> scared(player) ? Mood.SCARED : Mood.NONE;
			case "hotheaded" -> Mood.HOTHEADED;
			case "gloomy" -> Mood.GLOOMY;
			case "cheerful" -> Mood.CHEERFUL;
			default -> Mood.NONE;
		};
		boolean idle = state.walkAnimationSpeed < IDLE_BELOW && !avatar.isUsingItem && avatar.swingAnimation <= 0.0F;
		boolean shows = mood == Mood.CHEERFUL || mood != Mood.NONE && idle;
		float in = MOOD_IN.getOrDefault(id, 0.0F);
		in = Mth.clamp(in + (shows ? frame : -frame) / IDLE_EASE_TICKS, 0.0F, 1.0F);
		MOOD_IN.put(id, in);
		float bounce = mood == Mood.CHEERFUL && player.onGround()
			? Math.abs(Mth.sin(state.walkAnimationPos * 0.6662F)) * CHEERFUL_BOUNCE * Math.min(1.0F, state.walkAnimationSpeed) : 0.0F;

		if (Math.abs(twist) < 0.5F && Math.abs(strafe) < 0.01F && flinch <= 0.0F && in <= 0.0F && bounce <= 0.0F) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Motion(twist, strafe, flinch, player.getHurtDir(), in > 0.0F ? mood : Mood.NONE, smooth(in), time, bounce));
	}

	/** Dark around them, badly hurt, or a monster close by (looked at every {@link #SCARED_CHECK_TICKS}). */
	static boolean scared(Player player) {
		int id = player.getId();
		long now = player.level().getGameTime();
		Long at = SCARED_AT.get(id);
		if (at != null && now - at < SCARED_CHECK_TICKS && now >= at) {
			return SCARED.getOrDefault(id, false);
		}
		boolean scared = player.getHealth() <= player.getMaxHealth() * SCARED_HEALTH
			|| player.level().getMaxLocalRawBrightness(player.blockPosition()) <= SCARED_LIGHT
			|| !player.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(SCARED_MONSTER_RANGE), Entity::isAlive).isEmpty();
		SCARED_AT.put(id, now);
		SCARED.put(id, scared);
		return scared;
	}

	private static void forget(int id) {
		STRAFE.remove(id);
		MOOD_IN.remove(id);
		LAST_FRAME.remove(id);
		SCARED_AT.remove(id);
		SCARED.remove(id);
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	private static @Nullable Motion motion(LivingEntityRenderState state) {
		if (!(state instanceof AvatarRenderState avatar) || state.deathTime > 0.0F || PoseTakeover.posed(avatar)) {
			return null;
		}
		return state.getData(KEY);
	}

	/** After the body faces its way: the strafe lean, the flinch, the hunch or slump, the bounce (about the feet). */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Motion m = motion(state);
		if (m == null) {
			return;
		}
		if (m.bounce() > 0.0F) {
			poseStack.translate(0.0F, m.bounce(), 0.0F);
		}
		// local +X is the body's right: a negative turn about Z tips the top to the right, a negative turn about X forward
		if (Math.abs(m.strafe()) > 0.01F) {
			poseStack.rotateDegrees(Axis.ZP, -m.strafe() * STRAFE_LEAN);
		}
		float forward = switch (m.mood()) {
			case SCARED -> SCARED_HUNCH;
			case GLOOMY -> GLOOMY_SLUMP;
			default -> 0.0F;
		} * m.moodIn();
		if (forward > 0.0F) {
			poseStack.rotateDegrees(Axis.XP, -forward);
		}
		if (m.flinch() > 0.0F) {
			// tipped away from where the hit came from: its angle from straight ahead of the body, positive to the right
			// (the hurt direction is counted from the look, 90 straight ahead)
			float from = (m.hurtDir() - 90.0F + state.yRot) * Mth.DEG_TO_RAD;
			float f = FLINCH_DEGREES * m.flinch() * m.flinch();
			// a positive turn about X tips the top back, about Z to the left
			poseStack.rotateDegrees(Axis.XP, f * Mth.cos(from));
			poseStack.rotateDegrees(Axis.ZP, f * Mth.sin(from));
		}
	}

	/** After the model animates: the chest turned after the head, and the temperament's arms and head. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Motion m = motion(living);
		if (m == null) {
			return;
		}
		// a swing already turns the chest its own way
		if (Math.abs(m.twist()) >= 0.5F && h.body.yRot == 0.0F) {
			float a = m.twist() * Mth.DEG_TO_RAD;
			h.body.yRot = a;
			h.rightArm.z = Mth.sin(a) * 5.0F;
			h.rightArm.x = -Mth.cos(a) * 5.0F;
			h.leftArm.z = -Mth.sin(a) * 5.0F;
			h.leftArm.x = Mth.cos(a) * 5.0F;
			h.rightArm.yRot += a;
			h.leftArm.yRot += a;
		}
		float e = m.moodIn();
		switch (m.mood()) {
			case SCARED -> {
				// glancing about now and then, hands drawn in to the chest
				float glance = Mth.sin(m.time() * GLANCE_SPEED) * Mth.sin(m.time() * GLANCE_SPEED * 0.37F + 1.3F);
				h.head.yRot += glance * GLANCE * e;
				h.rightArm.xRot = Mth.lerp(e, h.rightArm.xRot, -0.45F);
				h.leftArm.xRot = Mth.lerp(e, h.leftArm.xRot, -0.45F);
				h.rightArm.zRot = Mth.lerp(e, h.rightArm.zRot, -HANDS_IN);
				h.leftArm.zRot = Mth.lerp(e, h.leftArm.zRot, HANDS_IN);
				h.rightArm.yRot = Mth.lerp(e, h.rightArm.yRot, -HANDS_IN);
				h.leftArm.yRot = Mth.lerp(e, h.leftArm.yRot, HANDS_IN);
			}
			case HOTHEADED -> {
				h.rightArm.xRot = Mth.lerp(e, h.rightArm.xRot, -HOT_ARMS_FORWARD);
				h.leftArm.xRot = Mth.lerp(e, h.leftArm.xRot, -HOT_ARMS_FORWARD);
				h.rightArm.zRot = Mth.lerp(e, h.rightArm.zRot, HOT_ARMS_OUT);
				h.leftArm.zRot = Mth.lerp(e, h.leftArm.zRot, -HOT_ARMS_OUT);
			}
			case GLOOMY -> {
				h.head.xRot += GLOOMY_HEAD * e;
				h.rightArm.zRot = Mth.lerp(e, h.rightArm.zRot, 0.0F);
				h.leftArm.zRot = Mth.lerp(e, h.leftArm.zRot, 0.0F);
			}
			default -> {
			}
		}
	}
}
