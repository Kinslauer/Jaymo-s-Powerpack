package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Leaning round a corner (Andy's Ragdolls and Cover picks; the idea is Leaning Or Not's, nothing of it copied). Sneaking, the
 * lean keys (Q left, E right by default) take the body and the view out sideways, the hips shifting over a bent leg: the eye moves out
 * {@link #REACH} blocks, the feet stay where they are, and what you shoot leaves from where the eye is now.
 * <p>
 * The state is one synced attachment, {@link #DIR} (-1 left, 0 upright, 1 right), set by the client's packet and dropped by
 * the server the moment the player stops sneaking, mounts, swims, falls, is downed or crawls. Everyone sees it: the other
 * players draw the lean (client/LeanClient). Cover reads it: a leaning body is aimed at where it has leaned out to, so a
 * wall hides the covered side and only the leaned-out part can be hit ({@link Cover}).
 * <p>
 * Shots: every projectile a leaning player looses (arrows, bolts, EarlyGuns' balls, WeaponMod's and TaCZ's bullets, which
 * are all projectile entities with their owner set) is moved out to the leaned eye as it enters the world, unless a block
 * stands in the way.
 */
public final class Lean {
	/**
	 * The eye's sideways move at a full lean (blocks): the hips slid out and the body above them tilted (client/LeanClient).
	 * Andy: "crouching giving you the deepest lean out": from Powers' low crouch it reaches further.
	 */
	public static final double REACH = 0.5, CROUCH_REACH = 0.7;
	/** The tilt of the body above the hips (sneaking, crouching) and the roll of the view at a full lean (degrees); the legs carry the rest. */
	public static final float TILT = 12.0F, CROUCH_TILT = 18.0F, ROLL = 7.0F;
	/** A shot is only moved if it starts this close to the eye (blocks, squared), so a loaded-from-disk arrow is left alone. */
	static final double NEAR_EYE_SQR = 2.25;
	/** A moved shot stops this short of a wall (blocks). */
	static final double WALL_GAP = 0.15;

	/** -1 leaning left, 1 leaning right, absent upright. Not saved. */
	public static final AttachmentType<Integer> DIR = AttachmentRegistry.create(JaymosKingdoms.id("lean"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Player to server: the lean they want (-1, 0, 1). */
	public record Want(int dir) implements CustomPacketPayload {
		public static final Type<Want> TYPE = new Type<>(JaymosKingdoms.id("lean"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Want> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeByte(p.dir), buf -> new Want(buf.readByte()));

		@Override
		public Type<Want> type() {
			return TYPE;
		}
	}

	private Lean() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Want.TYPE, Want.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Want.TYPE, (payload, context) -> set(context.player(), payload.dir()));
		ServerTickEvents.END_SERVER_TICK.register(Lean::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile shot && shot.tickCount == 0 && shot.getOwner() instanceof ServerPlayer owner && dir(owner) != 0) {
				leave(shot, owner);
			}
		});
	}

	/** Which way {@code entity} leans: -1, 0 or 1. */
	public static int dir(Entity entity) {
		return entity instanceof Player ? entity.getAttachedOrElse(DIR, 0) : 0;
	}

	/** Whether {@code player} may lean just now: sneaking on their feet and nothing else going on with the body. */
	public static boolean can(Player player) {
		return player.isShiftKeyDown() && player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSwimming()
			&& !player.isFallFlying() && !player.isSleeping() && !player.getAbilities().flying && !Downed.isDowned(player);
	}

	/**
	 * How far {@code entity}'s eye (and the upper body, by {@code height}, a share of 0 to 1 of the way up) lies out to the side
	 * of where it stands: along the player's right, zero when upright. The one place the geometry is worked out.
	 */
	public static Vec3 shift(Entity entity, double height) {
		int dir = dir(entity);
		return dir == 0 ? Vec3.ZERO : offset(entity.getYRot(), dir * height * reach(entity));
	}

	/** How far out {@code entity}'s eye goes at a full lean (blocks): further from the low crouch. */
	public static double reach(Entity entity) {
		return entity instanceof Player player && PowersCompat.lowStance(player) ? CROUCH_REACH : REACH;
	}

	/** The move out to the right of someone facing {@code yawDegrees}, {@code signedBlocks} out (- to the left). */
	public static Vec3 offset(double yawDegrees, double signedBlocks) {
		double yaw = Math.toRadians(yawDegrees);
		return new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw)).scale(signedBlocks);
	}

	private static void set(ServerPlayer player, int dir) {
		int want = Integer.signum(dir);
		if (want != 0 && !can(player)) {
			want = 0;
		}
		if (want == 0) {
			if (player.hasAttached(DIR)) {
				player.removeAttached(DIR);
			}
		} else if (dir(player) != want) {
			player.setAttached(DIR, want);
		}
	}

	/** A player who can no longer lean stands up straight. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.hasAttached(DIR) && !can(player)) {
				player.removeAttached(DIR);
			}
		}
	}

	/** Moves a shot just loosed out to the leaned eye, stopping short of any block between. */
	private static void leave(Projectile shot, ServerPlayer owner) {
		if (shot.position().distanceToSqr(owner.getEyePosition()) > NEAR_EYE_SQR) {
			return;
		}
		Vec3 from = shot.position();
		Vec3 out = shift(owner, 1.0);
		Vec3 to = from.add(out);
		var hit = shot.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		if (hit.getType() == HitResult.Type.BLOCK) {
			to = hit.getLocation().subtract(out.normalize().scale(WALL_GAP));
		}
		shot.setPos(to);
	}
}
