package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wall walk (once Gravity is fully mastered, or ascended or corrupted): sneak while facing a wall you're pressed
 * against, or while your head is against a ceiling, and "down" becomes that surface. You walk on it, upside down on
 * a ceiling, sideways on a wall (W goes the way you look along it, so looking up a wall walks you up it). Sneak again
 * to let go and fall, or Jump to push off. Walk over the top of a wall and you step up onto the ground above. The
 * walking is the client's ({@code GravityWalkClient}); the server checks the surface is there, keeps the fall from
 * counting, drains a little hunger, and shows everyone the footsteps (and the body standing on the surface:
 * {@code GravityWalkPose}).
 */
public final class GravityWalk {
	/** Walking speed on a surface, and sprinting (blocks/tick; vanilla walking is ~0.22). */
	public static final double SPEED = 0.17, SPRINT_SPEED = 0.26;
	/** How hard you're held against the surface each tick (blocks/tick into it). */
	public static final double STICK = 0.08;
	/** Jumping off: away from the surface, and up. */
	public static final double JUMP_OUT = 0.5, JUMP_UP = 0.25;
	/** Walking off the top of a wall: the step up and over. */
	public static final double MANTLE_UP = 0.5, MANTLE_IN = 0.3;
	/** Hunger it costs per tick on a surface. */
	static final float EXHAUSTION = 0.006F;
	/** Footstep sound and dust every this many ticks while moving. */
	static final int STEP_TICKS = 6;
	/** The server lets the client be this far off the surface (lag) before it calls it a fall. */
	static final double SERVER_REACH = 0.7;

	/** Client → server: walking on the surface on side {@code face} (a direction's 3D index: UP is a ceiling), or -1 to let go. */
	public record Walk(int face) implements CustomPacketPayload {
		public static final Type<Walk> TYPE = new Type<>(JaymosPowers.id("gravity_walk"));
		public static final StreamCodec<ByteBuf, Walk> CODEC = ByteBufCodecs.VAR_INT.map(Walk::new, Walk::face);

		@Override
		public Type<Walk> type() {
			return TYPE;
		}
	}

	/** On a player walking on a wall or ceiling: the surface's side (a direction's 3D index), for everyone to draw it. */
	public static final AttachmentType<Integer> SURFACE = AttachmentRegistry.create(JaymosPowers.id("gravity_walk"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final Map<UUID, Direction> WALKING = new HashMap<>();

	private GravityWalk() {
	}

	/** Whether this player may walk on walls: Gravity fully mastered, or past it (ascended, corrupted). */
	public static boolean allowed(Player player) {
		return Powers.has(player, GravityPower.INSTANCE)
			&& (Mastery.unlocked(player) >= Mastery.ALL || Fate.isAscended(player) || Fate.isCorrupted(player));
	}

	/** Whether there's something solid against the player on side {@code face}, within {@code reach}. */
	public static boolean surfaceAt(Level level, Player player, Direction face, double reach) {
		// the standing body, even while crouched (sneak is how you step on, and crouching would pull your head off a ceiling)
		AABB box = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(player.position());
		if (face == Direction.UP) {
			return !level.noCollision(player, box.move(0.0, reach, 0.0));
		}
		AABB body = new AABB(box.minX, box.minY + 0.2, box.minZ, box.maxX, box.maxY - 0.2, box.maxZ);
		return !level.noCollision(player, body.move(face.getStepX() * reach, 0.0, face.getStepZ() * reach));
	}

	/** Whether this player is walking on a wall or ceiling right now. */
	static boolean walking(ServerPlayer player) {
		return WALKING.containsKey(player.getUUID());
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Walk.TYPE, Walk.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Walk.TYPE, (payload, ctx) -> walk(ctx.player(), payload.face()));
	}

	private static void walk(ServerPlayer player, int face) {
		Direction side = face >= 0 && face < Direction.values().length ? Direction.from3DDataValue(face) : null;
		if (side == null || side == Direction.DOWN || !allowed(player) || !player.isAlive() || player.isPassenger()
			|| player.getAttached(GravityPower.ROCK_FLIGHT) != null || !surfaceAt(player.level(), player, side, SERVER_REACH)) {
			stop(player);
			return;
		}
		boolean fresh = !WALKING.containsKey(player.getUUID());
		WALKING.put(player.getUUID(), side);
		player.setAttached(SURFACE, side.get3DDataValue());
		player.resetFallDistance();
		if (fresh) {
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F, 1.9F);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.9, player.getZ(), 14, 0.3, 0.5, 0.3, 0.05);
		}
	}

	static void stop(ServerPlayer player) {
		if (WALKING.remove(player.getUUID()) != null || player.getAttached(SURFACE) != null) {
			player.removeAttached(SURFACE);
		}
	}

	/** From the server tick. */
	static void tick(MinecraftServer server) {
		if (WALKING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Direction>> it = WALKING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Direction> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Direction face = entry.getValue();
			if (player == null || !player.isAlive() || player.isSpectator() || !allowed(player) || player.isInWater() || player.isPassenger()
				|| player.getAttached(GravityPower.ROCK_FLIGHT) != null || !surfaceAt(player.level(), player, face, SERVER_REACH)) {
				it.remove();
				if (player != null) {
					player.removeAttached(SURFACE);
				}
				continue;
			}
			// standing on it isn't falling
			player.resetFallDistance();
			player.causeFoodExhaustion(EXHAUSTION);
			double moved = player.position().distanceToSqr(player.xo, player.yo, player.zo);
			if (moved > 0.003 && player.tickCount % STEP_TICKS == 0) {
				footstep(player, face);
			}
		}
	}

	/** A footfall on the surface: its own step sound and a puff of its dust, for everyone. */
	private static void footstep(ServerPlayer player, Direction face) {
		ServerLevel level = player.level();
		Vec3 mid = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 at = face == Direction.UP ? player.position().add(0.0, player.getBbHeight() + 0.1, 0.0)
			: mid.add(face.getStepX() * 0.5, 0.0, face.getStepZ() * 0.5);
		BlockPos pos = BlockPos.containing(at);
		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return;
		}
		SoundType sound = state.getSoundType();
		level.playSound(null, at.x, at.y, at.z, sound.getStepSound(), SoundSource.PLAYERS, sound.getVolume() * 0.35F, sound.getPitch());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.03);
	}

	static void clear() {
		WALKING.clear();
	}
}
