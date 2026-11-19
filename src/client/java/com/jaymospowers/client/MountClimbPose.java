package com.jaymospowers.client;

import com.jaymospowers.core.MountClimb;
import com.jaymospowers.core.MountClimb.Kind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
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
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How climbing onto and off a mount looks ({@link MountClimb}). Each climb is a keyframed clip
 * ({@code geckolib/animations/entity/player_mount_*.animation.json}, written by {@code tools/gen_mount_climb_clips.py})
 * played on the player model through {@link KeyframeClip}, while the body is carried between where the player stood and
 * the saddle ({@link #offset}) and turns to face the way the mount does. Fresh Animations steps aside while it plays
 * ({@link PoseTakeover#posedOtherwise}). In first person the view rides along ({@link #eyeShift}).
 * <p>
 * Climbing on starts when the server says so (it holds the ride till the climb is done); climbing off starts here, when
 * a player who was in the saddle is next seen on the ground, from the seat they left to where vanilla set them down.
 */
public final class MountClimbPose {
	/** Getting off is shown only if the rider landed within this many blocks of the seat (not thrown or teleported). */
	static final double OFF_REACH = 4.0;
	/** A finished climb onto a mount holds its last frame this many ticks, waiting for the server to seat the player. */
	static final int SEAT_WAIT = 10;

	/** What the model needs this frame: the clip, how far through it, the body carried by {@code offset} (world blocks). */
	public record State(Kind kind, float share, Vec3 offset) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:mount_climb");

	private static final class Climb {
		final Kind kind;
		final int mountId;
		final float ticks;
		/** Climbing on: where the player stands and faces. Off: the seat they left, and the mount's facing. */
		final Vec3 from;
		final float fromYaw;
		int age;

		Climb(Kind kind, int mountId, float ticks, Vec3 from, float fromYaw) {
			this.kind = kind;
			this.mountId = mountId;
			this.ticks = ticks;
			this.from = from;
			this.fromYaw = fromYaw;
		}

		float share(float partialTicks) {
			return Mth.clamp((this.age + partialTicks) / this.ticks, 0.0F, 1.0F);
		}
	}

	/** A rider's seat as last seen: the mount, where they sat, which way the mount faced. */
	private record Seat(int mountId, Vec3 at, float yaw, Vec3 mountAt) {
	}

	private static final Map<Integer, Climb> CLIMBS = new HashMap<>();
	private static final Map<Integer, Seat> SEATS = new HashMap<>();
	private static final Map<Kind, KeyframeClip> CLIPS = new EnumMap<>(Kind.class);

	private MountClimbPose() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MountClimb.Start.TYPE, (p, ctx) -> {
			ClientLevel level = Minecraft.getInstance().level;
			Entity player = level == null ? null : level.getEntity(p.player());
			if (player instanceof Player) {
				CLIMBS.put(p.player(), new Climb(Kind.of(p.kind()), p.mount(), Math.max(1, p.ticks()), player.position(), bodyYaw(player)));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(MountClimb.Stop.TYPE, (p, ctx) -> CLIMBS.remove(p.player()));
		ClientTickEvents.END_CLIENT_TICK.register(MountClimbPose::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			CLIMBS.clear();
			SEATS.clear();
			return;
		}
		CLIMBS.entrySet().removeIf(e -> {
			Climb c = e.getValue();
			c.age++;
			Entity player = level.getEntity(e.getKey());
			if (player == null) {
				return true;
			}
			if (c.kind.on()) {
				// seated (or never will be): the climb is over
				return player.isPassenger() || c.age > c.ticks + SEAT_WAIT;
			}
			return c.age > c.ticks;
		});
		SEATS.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player));
		for (Player player : level.players()) {
			Entity vehicle = player.getVehicle();
			if (vehicle != null) {
				if (MountClimb.climbable(vehicle)) {
					SEATS.put(player.getId(), new Seat(vehicle.getId(), player.position(), bodyYaw(vehicle), vehicle.position()));
				} else {
					SEATS.remove(player.getId());
				}
				continue;
			}
			Seat seat = SEATS.remove(player.getId());
			if (seat != null && player.isAlive() && !player.isSpectator() && player.position().distanceTo(seat.at) < OFF_REACH) {
				Kind kind = offKind(seat, player.position());
				CLIMBS.put(player.getId(), new Climb(kind, seat.mountId, MountClimb.seconds(kind, player) * 20.0F, seat.at, seat.yaw));
			}
		}
	}

	/** Off over the rump if vanilla set them down behind the mount, else down its side. */
	private static Kind offKind(Seat seat, Vec3 landed) {
		double dx = landed.x - seat.mountAt.x, dz = landed.z - seat.mountAt.z;
		float to = (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		return Math.abs(Mth.wrapDegrees(to - seat.yaw)) > 130.0F ? Kind.OFF_BACK : Kind.OFF_FORWARD;
	}

	private static float bodyYaw(Entity entity) {
		return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
	}

	/** Whether the local player is partway through a climb: their keys are held still (MountClimbInputMixin). */
	public static boolean climbingLocal() {
		Player player = Minecraft.getInstance().player;
		return player != null && !player.isPassenger() && CLIMBS.containsKey(player.getId());
	}

	/**
	 * How far {@code player}'s body is carried from where they really are this frame (world blocks), or null if they
	 * aren't climbing. On: from the ground up to the saddle. Off: from the seat they left down to where they stand.
	 */
	public static @Nullable Vec3 offset(Player player, float partialTicks) {
		Climb c = CLIMBS.get(player.getId());
		if (c == null) {
			return null;
		}
		float s = c.share(partialTicks);
		Vec3 at = player.getPosition(partialTicks);
		if (c.kind.on()) {
			Entity mount = player.level().getEntity(c.mountId);
			if (mount == null || player.isPassenger()) {
				return null;
			}
			Vec3 seat = mount.getPassengerRidingPosition(player).subtract(player.getVehicleAttachmentPoint(mount))
				.subtract(mount.position()).add(mount.getPosition(partialTicks));
			Vec3 d = seat.subtract(at);
			float across = smooth(c.kind == Kind.ON_BACK ? 0.05F : 0.15F, 0.85F, s);
			float up = smooth(c.kind == Kind.ON_BACK ? 0.1F : 0.2F, 0.7F, s);
			// a vault rises over the rump and settles; a stirrup climb lifts a little past the seat and sits down
			float over = Mth.sin(Mth.PI * Mth.clamp((s - 0.2F) / 0.6F, 0.0F, 1.0F)) * (c.kind == Kind.ON_BACK ? 0.25F : 0.1F);
			return new Vec3(d.x * across, d.y * up + over, d.z * across);
		}
		Vec3 d = c.from.subtract(at);
		float across = 1.0F - smooth(0.2F, 0.85F, s);
		// down from the saddle like a drop: slow off the seat, quick to the ground
		float t = Mth.clamp((s - 0.25F) / 0.55F, 0.0F, 1.0F);
		float down = 1.0F - t * t;
		return new Vec3(d.x * across, d.y * down, d.z * across);
	}

	/** First person, climbing: the eye carried with the body (added to the camera), or null. */
	public static @Nullable Vec3 eyeShift(Player player, float partialTicks) {
		return offset(player, partialTicks);
	}

	private static float smooth(float from, float to, float s) {
		float t = Mth.clamp((s - from) / (to - from), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** From the player renderer: the climb onto the render state, and the body turned to face the mount's way. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		state.setData(KEY, null);
		if (!(entity instanceof Player player)) {
			return;
		}
		Climb c = CLIMBS.get(player.getId());
		Vec3 offset = c == null ? null : offset(player, partialTicks);
		if (offset == null) {
			return;
		}
		float s = c.share(partialTicks);
		float yaw;
		if (c.kind.on()) {
			Entity mount = player.level().getEntity(c.mountId);
			float mountYaw = mount == null ? state.bodyRot : bodyYaw(mount);
			yaw = Mth.rotLerp(smooth(0.1F, 0.8F, s), c.fromYaw, mountYaw);
		} else {
			yaw = Mth.rotLerp(smooth(0.2F, 0.9F, s), c.fromYaw, state.bodyRot);
		}
		// the head keeps looking where it did
		state.yRot = Mth.wrapDegrees(state.yRot + state.bodyRot - yaw);
		state.bodyRot = yaw;
		state.setData(KEY, new State(c.kind, s, offset));
	}

	/** From the player renderer's rotations: the body carried along its path (in world space, under the body's turn). */
	public static void rotate(AvatarRenderState state, PoseStack poseStack, float bodyRot) {
		State climb = state.getData(KEY);
		if (climb == null) {
			return;
		}
		float turn = 180.0F - bodyRot;
		poseStack.rotateDegrees(Axis.YP, -turn);
		poseStack.translate(climb.offset.x, climb.offset.y, climb.offset.z);
		poseStack.rotateDegrees(Axis.YP, turn);
	}

	/** The clip's limbs, over vanilla's walk (ModelPoses). The head is left to look where the player looks. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(model instanceof HumanoidModel<?> h) || !(renderState instanceof AvatarRenderState state)) {
			return;
		}
		State climb = state.getData(KEY);
		KeyframeClip clip = climb == null ? null : clip(climb.kind);
		if (clip == null) {
			return;
		}
		pose(h.body, clip, "body", climb.share);
		pose(h.rightArm, clip, "right_arm", climb.share);
		pose(h.leftArm, clip, "left_arm", climb.share);
		pose(h.rightLeg, clip, "right_leg", climb.share);
		pose(h.leftLeg, clip, "left_leg", climb.share);
	}

	private static void pose(ModelPart part, KeyframeClip clip, String bone, float share) {
		if (clip.has(bone)) {
			part.xRot = clip.partX(bone, share);
			part.yRot = clip.partY(bone, share);
			part.zRot = clip.partZ(bone, share);
		}
	}

	private static @Nullable KeyframeClip clip(Kind kind) {
		if (!CLIPS.containsKey(kind)) {
			CLIPS.put(kind, KeyframeClip.load("jaymospowers", kind.clip, kind.name().toLowerCase(java.util.Locale.ROOT)));
		}
		return CLIPS.get(kind);
	}
}
