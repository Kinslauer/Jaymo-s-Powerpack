package com.jaymospowers.power.web;

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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wall run (Prince of Persia style): sprint and leap alongside a wall and you run along it for a moment, then Jump kicks
 * you off it, up and away. The kick gets higher with mastery and highest once ascended. A wall run can chain into one
 * on another wall, but not back onto the same wall face until you land. The run is the client's (see
 * {@code WebWallRunClient}); the server only checks there's a wall there, keeps the fall from counting, and shows
 * everyone the footsteps.
 */
public final class WebWallRun {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** How long one run lasts (ticks), by mastery tier (0-4 abilities unlocked, then ascended). */
	public static final int[] RUN_TICKS = {22, 26, 30, 34, 38, 50};
	/** The kick off the wall: upward speed (blocks/tick; a vanilla jump is 0.42), by the same tiers. */
	public static final double[] JUMP_UP = {0.48, 0.54, 0.6, 0.66, 0.72, 0.92};
	/** The kick's push away from the wall, and the share of the run's speed it keeps along the wall. */
	public static final double JUMP_OUT = 0.42;
	public static final double JUMP_KEEP = 0.85;
	/** Speed along the wall: at least this, at most that (blocks/tick; sprinting is ~0.28). */
	public static final double RUN_SPEED_MIN = 0.32, RUN_SPEED_MAX = 0.7;
	/** On stepping onto the wall you rise at least this fast (at most {@code RUN_LIFT_MAX}, however hard you leapt), then the run's own light gravity pulls you down to {@code RUN_FALL_MAX}. */
	public static final double RUN_LIFT = 0.16, RUN_LIFT_MAX = 0.24;
	public static final double RUN_GRAVITY = 0.018;
	public static final double RUN_FALL_MAX = 0.16;
	/** Moving at least this fast across the ground (blocks/tick) to step onto a wall. */
	public static final double ENTRY_SPEED = 0.2;
	/** How square to the wall you may come in: the most of your heading that may point into it (0 = dead parallel). */
	public static final double ENTRY_INTO = 0.75;
	/** Falling faster than this, a wall won't catch you. */
	public static final double ENTRY_FALL_MAX = 0.6;
	/** The server lets a reported run count for at most this long (ticks) without hearing it end. */
	static final int SERVER_MAX_TICKS = 60;
	/** Footstep sound and dust every this many ticks. */
	static final int STEP_TICKS = 4;
	// ================================================================================================================

	/** Client → server: running along the wall on side {@code face} (a horizontal direction's 3D index), or -1 for off the wall. */
	public record Run(int face) implements CustomPacketPayload {
		public static final Type<Run> TYPE = new Type<>(JaymosPowers.id("web_wall_run"));
		public static final StreamCodec<ByteBuf, Run> CODEC = ByteBufCodecs.VAR_INT.map(Run::new, Run::face);

		@Override
		public Type<Run> type() {
			return TYPE;
		}
	}

	private record Running(Direction face, int ticks) {
	}

	/** Running along a wall: the wall's side (a horizontal direction's 3D index), for everyone to draw the run (see {@code WebWallRunPose}). */
	public static final AttachmentType<Integer> WALL = AttachmentRegistry.create(JaymosPowers.id("web_wall_run"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Players running along a wall right now, and on which side. */
	private static final Map<UUID, Running> RUNNING = new HashMap<>();

	private WebWallRun() {
	}

	/** The player's tier: abilities unlocked (0-4), or 5 once ascended. */
	public static int tier(Player player) {
		return Fate.isAscended(player) ? JUMP_UP.length - 1 : Math.min(Mastery.unlocked(player), JUMP_UP.length - 2);
	}

	/**
	 * Whether there's a wall against the player's side {@code face}: something solid both low (knee) and high (chest)
	 * within {@code reach} of the body, so a step or a fence top doesn't count.
	 */
	public static boolean wallAt(Level level, Player player, Direction face, double reach) {
		AABB box = player.getBoundingBox();
		double dx = face.getStepX() * reach, dz = face.getStepZ() * reach;
		AABB low = new AABB(box.minX, box.minY + 0.3, box.minZ, box.maxX, box.minY + 0.9, box.maxZ).move(dx, 0.0, dz);
		AABB high = new AABB(box.minX, box.minY + 1.0, box.minZ, box.maxX, box.maxY - 0.15, box.maxZ).move(dx, 0.0, dz);
		return !level.noCollision(player, low) && !level.noCollision(player, high);
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Run.TYPE, Run.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Run.TYPE, (payload, ctx) -> run(ctx.player(), payload.face()));
		ServerTickEvents.END_SERVER_TICK.register(WebWallRun::tick);
	}

	private static void run(ServerPlayer player, int face) {
		Direction side = face >= 0 && face < Direction.values().length ? Direction.from3DDataValue(face) : null;
		// (the fall up to here still counts; only the run itself is free)
		if (side == null || !side.getAxis().isHorizontal() || !Powers.has(player, WebPower.INSTANCE) || !wallAt(player.level(), player, side, 0.6)) {
			RUNNING.remove(player.getUUID());
			player.removeAttached(WALL);
			return;
		}
		player.resetFallDistance();
		RUNNING.put(player.getUUID(), new Running(side, 0));
		player.setAttached(WALL, side.get3DDataValue());
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Running>> it = RUNNING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Running> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Running running = entry.getValue();
			if (player == null || !player.isAlive() || player.onGround() || running.ticks() >= SERVER_MAX_TICKS
				|| !Powers.has(player, WebPower.INSTANCE) || !wallAt(player.level(), player, running.face(), 0.6)) {
				it.remove();
				if (player != null) {
					player.removeAttached(WALL);
				}
				continue;
			}
			// running along the wall isn't falling
			player.resetFallDistance();
			entry.setValue(new Running(running.face(), running.ticks() + 1));
			if (running.ticks() % STEP_TICKS == 0) {
				footstep(player, running.face());
			}
		}
	}

	/** A footfall on the wall: its own step sound, and a puff of its dust, for everyone. */
	public static void footstep(ServerPlayer player, Direction face) {
		ServerLevel level = player.level();
		BlockPos pos = wallBlock(level, player, face);
		if (pos == null) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		SoundType sound = state.getSoundType();
		Vec3 at = player.position().add(face.getStepX() * 0.4, 0.4, face.getStepZ() * 0.4);
		level.playSound(null, at.x, at.y, at.z, sound.getStepSound(), SoundSource.PLAYERS, sound.getVolume() * 0.35F, sound.getPitch());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 4, 0.05, 0.2, 0.05, 0.05);
	}

	/** The wall block beside the player on {@code face} (knee height, then chest), or null. */
	private static @Nullable BlockPos wallBlock(ServerLevel level, ServerPlayer player, Direction face) {
		BlockPos feet = BlockPos.containing(player.getX() + face.getStepX() * 0.8, player.getY() + 0.5, player.getZ() + face.getStepZ() * 0.8);
		for (BlockPos pos : new BlockPos[] {feet, feet.above()}) {
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return pos;
			}
		}
		return null;
	}
}
