package com.jaymospowers.client;

import com.jaymospowers.core.MountClimb;
import com.jaymospowers.core.MountClimb.Kind;
import com.jaymospowers.core.MountLeap;
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
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
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

	/**
	 * What the model needs this frame: the clip, how far through it, the body carried by {@code offset} (world blocks). A
	 * slip off a mount that shied starts from the pose the climb had reached ({@code prev} at {@code prevShare}) and eases
	 * into its own.
	 */
	public record State(Kind kind, float share, Vec3 offset, @Nullable Kind prev, float prevShare) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:mount_climb");
	/** The rider in the saddle reaching a hand back to someone climbing up behind them: how far the arm is out, 0 to 1. */
	public static final RenderStateDataKey<Float> REACH = RenderStateDataKey.create(() -> "jaymospowers:mount_climb_reach");
	/** The arm that reaches back (vanilla's right arm: xRot back and down, zRot out). */
	static final float REACH_X = 55.0F * Mth.DEG_TO_RAD, REACH_Z = 25.0F * Mth.DEG_TO_RAD;
	/** A slip eases from the climb's pose into its own over this share of itself. */
	static final float SLIP_BLEND = 0.35F;

	private static final class Climb {
		final Kind kind;
		final int mountId;
		final float ticks;
		/** Climbing on: where the player stands and faces. Off: the seat they left, and the mount's facing. */
		final Vec3 from;
		final float fromYaw;
		int age;
		/** Seen on the client alone (SeatSteps): where {@link #from} lies against the mount, so the start of the path moves with it. */
		@Nullable Vec3 rel;
		/** A slip off a shying mount: the body's offset when it began, and the climb it came from and how far along. */
		Vec3 start = Vec3.ZERO;
		@Nullable Kind prev;
		float prevShare;

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

		/** Where the path starts this frame: {@link #from}, carried along by the mount if this climb follows it. */
		Vec3 start(Entity who, float partialTicks) {
			Entity mount = this.rel == null ? null : who.level().getEntity(this.mountId);
			return mount == null ? this.from : mount.getPosition(partialTicks).add(this.rel);
		}
	}

	/** A rider's seat as last seen: the mount, where they sat, which way the mount faced. */
	private record Seat(int mountId, Vec3 at, float yaw, Vec3 mountAt) {
	}

	private static final Map<Integer, Climb> CLIMBS = new HashMap<>();
	private static final Map<Integer, Seat> SEATS = new HashMap<>();
	/** How fast each rider's mount was going (blocks a tick, level) when last seen under them: a gallop makes the leap off (MountLeap). */
	private static final Map<Integer, Double> RIDE_SPEED = new HashMap<>();
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
		ClientPlayNetworking.registerGlobalReceiver(MountClimb.Stop.TYPE, (p, ctx) -> {
			Climb c = CLIMBS.remove(p.player());
			ClientLevel level = Minecraft.getInstance().level;
			Entity player = level == null ? null : level.getEntity(p.player());
			if (c != null && p.reason() == MountClimb.SHIED && c.kind.on() && player instanceof Player who) {
				// the mount shied: down the flank from wherever the climb had got to, not a snap back
				float s = c.share(0.0F);
				Vec3 at = offset(who, c, 0.0F);
				Climb slip = new Climb(Kind.OFF_SLIP, c.mountId, Math.max(1.0F, Kind.OFF_SLIP.seconds * 20.0F), who.position(), onYaw(c, s, who, bodyYaw(who)));
				slip.start = at == null ? Vec3.ZERO : at;
				slip.prev = c.kind;
				slip.prevShare = s;
				CLIMBS.put(p.player(), slip);
			}
		});
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
				// seated and played to its end (the server may seat them a tick or two early), or never will be: over
				return player.isPassenger() && c.age >= c.ticks || c.age > c.ticks + SEAT_WAIT;
			}
			// a leap is over when they land (the dodge's roll takes it from there)
			return c.age > c.ticks || c.kind == Kind.OFF_LEAP && c.age > 2 && player.onGround();
		});
		SEATS.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player));
		RIDE_SPEED.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player));
		for (Player player : level.players()) {
			Entity vehicle = player.getVehicle();
			if (vehicle != null) {
				if (MountClimb.climbable(vehicle)) {
					Seat before = SEATS.get(player.getId());
					RIDE_SPEED.put(player.getId(), before != null && before.mountId == vehicle.getId()
						? Math.hypot(vehicle.getX() - before.mountAt.x, vehicle.getZ() - before.mountAt.z) : 0.0);
					SEATS.put(player.getId(), new Seat(vehicle.getId(), player.position(), bodyYaw(vehicle), vehicle.position()));
				} else {
					SEATS.remove(player.getId());
				}
				continue;
			}
			Seat seat = SEATS.remove(player.getId());
			double speed = RIDE_SPEED.getOrDefault(player.getId(), 0.0);
			RIDE_SPEED.remove(player.getId());
			if (seat != null && player.isAlive() && !player.isSpectator() && player.position().distanceTo(seat.at) < OFF_REACH) {
				Kind kind = MountLeap.leaps(player, speed) ? Kind.OFF_LEAP : offKind(seat, player.position());
				CLIMBS.put(player.getId(), new Climb(kind, seat.mountId, MountClimb.seconds(kind, player) * 20.0F, seat.at, seat.yaw));
			}
		}
	}

	/** Off over the rump if vanilla set them down behind the mount, else down its side. */
	private static Kind offKind(Seat seat, Vec3 landed) {
		return offKind(seat.mountAt, seat.yaw, landed);
	}

	/** Off over the rump if {@code landed} is behind a mount at {@code mountAt} facing {@code yaw}, else down its side. */
	static Kind offKind(Vec3 mountAt, float yaw, Vec3 landed) {
		double dx = landed.x - mountAt.x, dz = landed.z - mountAt.z;
		float to = (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		return Math.abs(Mth.wrapDegrees(to - yaw)) > 130.0F ? Kind.OFF_BACK : Kind.OFF_FORWARD;
	}

	/** Whether {@code who} is partway through a climb already. */
	static boolean playing(int who) {
		return CLIMBS.containsKey(who);
	}

	/**
	 * A climb the server didn't send (SeatSteps: stepping into a cart or boat, and the villagers and guards who mount and
	 * dismount on their own): {@code who} was seen at {@code from}, facing {@code fromYaw}, before the seat took them. The
	 * path's start rides with the mount, which doesn't stand still for them as a player's does.
	 */
	static void play(LivingEntity who, Kind kind, int mountId, Vec3 from, float fromYaw) {
		Climb c = new Climb(kind, mountId, Math.max(1.0F, MountClimb.seconds(kind, who) * 20.0F), from, fromYaw);
		Entity mount = who.level().getEntity(mountId);
		if (mount != null) {
			c.rel = from.subtract(mount.position());
		}
		CLIMBS.put(who.getId(), c);
	}

	static float yawOf(Entity entity) {
		return bodyYaw(entity);
	}

	private static float bodyYaw(Entity entity) {
		return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
	}

	/** Whether the local player is partway through a climb: their keys are held still (MountClimbInputMixin). */
	public static boolean climbingLocal() {
		Player player = Minecraft.getInstance().player;
		Climb c = player == null ? null : CLIMBS.get(player.getId());
		return c != null && !player.isPassenger() && !c.kind.carriesMomentum();
	}

	/**
	 * How far {@code player}'s body is carried from where they really are this frame (world blocks), or null if they
	 * aren't climbing. On: from the ground up to the saddle. Off: from the seat they left down to where they stand.
	 */
	public static @Nullable Vec3 offset(Player player, float partialTicks) {
		Climb c = CLIMBS.get(player.getId());
		return c == null ? null : offset(player, c, partialTicks);
	}

	private static @Nullable Vec3 offset(Entity player, Climb c, float partialTicks) {
		float s = c.share(partialTicks);
		Vec3 at = player.getPosition(partialTicks);
		Vec3 from = c.start(player, partialTicks);
		if (c.kind.on()) {
			// where they end up: the seat the mount offers (the second seat, for a pillion climb: Kingdoms places it for a
			// rider not yet aboard), or, once they're seated, simply where they are, so the path runs on to its end
			Vec3 seat = at;
			if (!player.isPassenger()) {
				Entity mount = player.level().getEntity(c.mountId);
				if (mount == null) {
					return null;
				}
				seat = mount.getPassengerRidingPosition(player).subtract(player.getVehicleAttachmentPoint(mount))
					.subtract(mount.position()).add(mount.getPosition(partialTicks));
			}
			Vec3 d = seat.subtract(from);
			float across, up, hump;
			switch (c.kind) {
				case ON_BACK -> {
					across = smooth(0.05F, 0.85F, s);
					up = smooth(0.1F, 0.7F, s);
					hump = 0.25F;
				}
				case ON_PILLION -> {
					// pulled up by the hand: lifted early, over the rump
					across = smooth(0.1F, 0.8F, s);
					up = smooth(0.1F, 0.65F, s);
					hump = 0.2F;
				}
				case ON_VAULT -> {
					// carried on at the pace they ran in (quickest at the start, easing into the saddle), over the rump in an arc
					across = 1.0F - (1.0F - smooth(0.0F, 0.9F, s)) * (1.0F - smooth(0.0F, 0.9F, s));
					up = smooth(0.0F, 0.75F, s);
					hump = 0.45F;
				}
				case STEP_UP -> {
					// a foot on the step and a pull up the rail: up first, then across onto the bench
					across = smooth(0.2F, 0.85F, s);
					up = smooth(0.1F, 0.65F, s);
					hump = 0.12F;
				}
				case STEP_IN -> {
					// over the gunwale, high at the middle, and down into the boat
					across = smooth(0.2F, 0.8F, s);
					up = smooth(0.25F, 0.7F, s);
					hump = 0.2F;
				}
				case ON_KNEEL -> {
					// a low step across onto the lowered saddle
					across = smooth(0.15F, 0.85F, s);
					up = smooth(0.2F, 0.7F, s);
					hump = 0.05F;
				}
				default -> {
					across = smooth(0.15F, 0.85F, s);
					up = smooth(0.2F, 0.7F, s);
					hump = 0.1F;
				}
			}
			// a vault rises over the rump and settles; a stirrup climb lifts a little past the seat and sits down
			float over = Mth.sin(Mth.PI * Mth.clamp((s - 0.2F) / 0.6F, 0.0F, 1.0F)) * hump;
			Vec3 path = new Vec3(d.x * across, d.y * up + over, d.z * across);
			return path.subtract(at.subtract(from));
		}
		if (c.kind == Kind.OFF_SLIP) {
			// down the flank: the height goes first, the rest of the way across after
			float down = 1.0F - smooth(0.0F, 0.6F, s);
			float across = 1.0F - smooth(0.0F, 0.9F, s);
			return new Vec3(c.start.x * across, c.start.y * down, c.start.z * across);
		}
		if (c.kind == Kind.OFF_LEAP) {
			// the body really is in the air, flung by the server: nothing to carry
			return Vec3.ZERO;
		}
		Vec3 d = from.subtract(at);
		float across = 1.0F - smooth(0.2F, 0.85F, s);
		// down from the saddle like a drop: slow off the seat, quick to the ground
		float t = Mth.clamp((s - 0.25F) / 0.55F, 0.0F, 1.0F);
		float down = 1.0F - t * t;
		return new Vec3(d.x * across, d.y * down, d.z * across);
	}

	/** The way a climber's body faces on the way up: from where they stood round to the mount's own. */
	private static float onYaw(Climb c, float s, Entity player, float fallback) {
		Entity mount = player.level().getEntity(c.mountId);
		return Mth.rotLerp(smooth(0.1F, 0.8F, s), c.fromYaw, mount == null ? fallback : bodyYaw(mount));
	}

	/** The seated first rider's hand reaching back to a pillion climber, as an arm weight (0-1); null if there's no one to reach for. */
	private static @Nullable Float reach(Player player, float partialTicks) {
		Entity vehicle = player.getVehicle();
		if (vehicle == null || vehicle.getFirstPassenger() != player) {
			return null;
		}
		for (Climb c : CLIMBS.values()) {
			if (c.kind == Kind.ON_PILLION && c.mountId == vehicle.getId()) {
				float s = c.share(partialTicks);
				float w = Math.min(smooth(0.0F, 0.2F, s), 1.0F - smooth(0.8F, 1.0F, s));
				return w > 0.0F ? w : null;
			}
		}
		return null;
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
		state.setData(REACH, null);
		if (!(entity instanceof Player player)) {
			return;
		}
		state.setData(REACH, reach(player, partialTicks));
		Climb c = CLIMBS.get(player.getId());
		Vec3 offset = c == null ? null : offset(player, partialTicks);
		if (offset == null) {
			return;
		}
		float s = c.share(partialTicks);
		float yaw;
		if (c.kind.on()) {
			yaw = onYaw(c, s, player, state.bodyRot);
		} else {
			yaw = Mth.rotLerp(smooth(0.2F, 0.9F, s), c.fromYaw, state.bodyRot);
		}
		// the head keeps looking where it did
		state.yRot = Mth.wrapDegrees(state.yRot + state.bodyRot - yaw);
		state.bodyRot = yaw;
		state.setData(KEY, new State(c.kind, s, offset, c.prev, c.prevShare));
	}

	/** From the living-entity renderer, for whoever isn't a player (a villager or guard mounting, dismounting): as {@link #extract}. */
	public static void extractMob(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity instanceof Player) {
			return;
		}
		state.setData(KEY, null);
		Climb c = CLIMBS.get(entity.getId());
		Vec3 offset = c == null ? null : offset(entity, c, partialTicks);
		if (offset == null) {
			return;
		}
		float s = c.share(partialTicks);
		float yaw = c.kind.on() ? onYaw(c, s, entity, state.bodyRot) : Mth.rotLerp(smooth(0.2F, 0.9F, s), c.fromYaw, state.bodyRot);
		state.yRot = Mth.wrapDegrees(state.yRot + state.bodyRot - yaw);
		state.bodyRot = yaw;
		state.setData(KEY, new State(c.kind, s, offset, null, 0.0F));
	}

	/** From the player renderer's rotations: the body carried along its path (in world space, under the body's turn). */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float bodyRot) {
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
		if (!(model instanceof HumanoidModel<?> h) || !(renderState instanceof LivingEntityRenderState state)) {
			return;
		}
		State climb = state.getData(KEY);
		KeyframeClip clip = climb == null ? null : clip(climb.kind);
		if (clip != null) {
			KeyframeClip from = climb.prev == null ? null : clip(climb.prev);
			float blend = from == null ? 1.0F : smooth(0.0F, SLIP_BLEND, climb.share);
			pose(h.body, clip, from, "body", climb, blend);
			pose(h.rightArm, clip, from, "right_arm", climb, blend);
			pose(h.leftArm, clip, from, "left_arm", climb, blend);
			pose(h.rightLeg, clip, from, "right_leg", climb, blend);
			pose(h.leftLeg, clip, from, "left_leg", climb, blend);
			return;
		}
		Float reach = state.getData(REACH);
		if (reach != null) {
			// a hand back for the one climbing up behind
			h.rightArm.xRot = Mth.lerp(reach, h.rightArm.xRot, REACH_X);
			h.rightArm.yRot = Mth.lerp(reach, h.rightArm.yRot, 0.0F);
			h.rightArm.zRot = Mth.lerp(reach, h.rightArm.zRot, REACH_Z);
		}
	}

	/** One bone: the clip's pose, eased in from {@code from}'s pose (where a slip began) by {@code blend}. */
	private static void pose(ModelPart part, KeyframeClip clip, @Nullable KeyframeClip from, String bone, State climb, float blend) {
		if (!clip.has(bone)) {
			return;
		}
		float x = clip.partX(bone, climb.share), y = clip.partY(bone, climb.share), z = clip.partZ(bone, climb.share);
		if (from != null && from.has(bone)) {
			x = Mth.lerp(blend, from.partX(bone, climb.prevShare), x);
			y = Mth.lerp(blend, from.partY(bone, climb.prevShare), y);
			z = Mth.lerp(blend, from.partZ(bone, climb.prevShare), z);
		}
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	private static @Nullable KeyframeClip clip(Kind kind) {
		if (!CLIPS.containsKey(kind)) {
			CLIPS.put(kind, KeyframeClip.load("jaymospowers", kind.clip, kind.name().toLowerCase(java.util.Locale.ROOT)));
		}
		return CLIPS.get(kind);
	}
}
