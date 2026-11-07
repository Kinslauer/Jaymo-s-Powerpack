package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Parkour for web slingers (our own take on ParCool's moves, which is Forge-only):
 * <ul>
 * <li><b>Vault:</b> faster than a sprint (see {@link #VAULT_MIN_SPEED}), run at a one-block obstacle and you plant a hand on it and swing your legs over.</li>
 * <li><b>Wall cling:</b> in the air against a wall in front of you, hold Sneak to hang on it; A/D shimmy along, W climbs
 * (and pulls you over the top), let go of Sneak to drop. You hold on longer with mastery, then start to slip. It takes
 * webbed fists to start one (X; Sneak + X when bonded); in the symbiote's suit its tendrils hold you instead, and never slip.</li>
 * <li><b>Wall jump:</b> in the air against a wall with webbed fists on (or clinging), Jump kicks you off it, up and away; holding forward
 * from a cling climbs instead, kicking up more than out. Each wall face once until you land, so two facing walls can be
 * climbed like a chimney.</li>
 * <li><b>Slide:</b> sprinting, press Sneak to drop into a slide that carries you on fast and low; Jump out of it keeps
 * the speed.</li>
 * </ul>
 * The moves are the client's (see {@code WebParkourClient}); the server checks the wall is there, keeps a cling or a
 * kick from counting as a fall, lets a cling hang in the air, and shows everyone the pose, sounds and dust.
 */
public final class WebParkour {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** How long a cling holds before you start to slip (ticks), by mastery tier (0-4 abilities unlocked, then ascended). */
	public static final int[] CLING_HOLD_TICKS = {40, 60, 80, 100, 120, 200};
	/** Slipping down a wall after the hold (blocks/tick), and the most a cling lasts at all. */
	public static final double CLING_SLIP = 0.05;
	public static final int CLING_MAX_TICKS = 400;
	/** Shimmying along the wall while clinging (blocks/tick). */
	public static final double CLING_SHIMMY = 0.08;
	/** Climbing up the wall while clinging (W, blocks/tick), and the pull up and over when the wall runs out above. */
	public static final double CLING_CLIMB = 0.09, MANTLE_UP = 0.45, MANTLE_IN = 0.22;
	/** A wall jump: up (by tier) and out from the wall; climbing (forward held from a cling) goes up this much more and out this much. */
	public static final double[] WALL_JUMP_UP = {0.5, 0.54, 0.58, 0.62, 0.66, 0.8};
	public static final double WALL_JUMP_OUT = 0.45, CLIMB_UP_BONUS = 0.1, CLIMB_OUT = 0.12;
	/** Of your speed along the wall, how much a wall jump keeps. */
	public static final double WALL_JUMP_KEEP = 0.5;
	/** A vault: up, and at least this fast forward (blocks/tick). */
	public static final double VAULT_UP = 0.5, VAULT_FORWARD = 0.42;
	/**
	 * A vault only comes out of real speed: the fastest you moved across the ground (blocks/tick) in the last
	 * {@link #VAULT_SPEED_TICKS} ticks must reach this. A plain sprint is about 0.28, so this is ~15% over it: a
	 * sprint-jump landing, Speed, a slide or a swing's run-out will do, a flat sprint won't (you just bump or step up).
	 */
	public static final double VAULT_MIN_SPEED = 0.32;
	public static final int VAULT_SPEED_TICKS = 5;
	/** A slide: the push at the start (times your speed, at least the minimum), how much speed is left each tick, how long at most, and the slowest it keeps going. */
	public static final double SLIDE_BOOST = 1.5, SLIDE_MIN = 0.5, SLIDE_KEEP = 0.94, SLIDE_STOP = 0.14;
	public static final int SLIDE_TICKS = 24;
	/**
	 * Jumping out of a slide: the upward kick (a vanilla jump is 0.42), and the speed along the ground it leaves with
	 * (the slide's speed times this, plus that, at most the cap), so a slide is a run-up for a long, high leap.
	 */
	public static final double SLIDE_JUMP_UP = 0.62, SLIDE_JUMP_KEEP = 1.3, SLIDE_JUMP_ADD = 0.12, SLIDE_JUMP_MAX = 0.95;
	/** Ticks before another vault or slide. */
	public static final int VAULT_COOLDOWN = 8, SLIDE_COOLDOWN = 12;
	/** How long the vault and wall jump poses last (ticks). */
	public static final int VAULT_TICKS = 10, WALL_JUMP_TICKS = 9;
	// ================================================================================================================

	public static final int NONE = 0, VAULT = 1, CLING = 2, WALL_JUMP = 3, SLIDE = 4;
	/** Vaults and slides each come in this many looks, picked at random (never the same one twice running). */
	public static final int VARIANTS = 4;

	/** Client → server: started move {@code kind} against wall side {@code face} (a horizontal direction's 3D index, or -1), or {@link #NONE} for done. */
	public record Move(int kind, int face) implements CustomPacketPayload {
		public static final Type<Move> TYPE = new Type<>(JaymosPowers.id("web_parkour"));
		public static final StreamCodec<ByteBuf, Move> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Move::kind, ByteBufCodecs.VAR_INT, Move::face, Move::new);

		@Override
		public Type<Move> type() {
			return TYPE;
		}
	}

	/**
	 * The move a web slinger is in, for everyone to draw: {@code (variant * 8 + kind) * 8 + face + 1} (face -1..5). See
	 * {@link #kind}, {@link #face}, {@link #variant}.
	 */
	public static final AttachmentType<Integer> MOVE = AttachmentRegistry.create(JaymosPowers.id("web_parkour"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private record Doing(int kind, int face, int ticks) {
	}

	private static final Map<UUID, Doing> DOING = new HashMap<>();
	/** Per player: the last vault look and the last slide look, so the next one differs. */
	private static final Map<UUID, int[]> LAST_VARIANT = new HashMap<>();

	private WebParkour() {
	}

	public static int kind(int move) {
		return move / 8 % 8;
	}

	/** Which look of a vault or slide (0 to {@link #VARIANTS} - 1). */
	public static int variant(int move) {
		return move / 64;
	}

	public static int face(int move) {
		return move % 8 - 1;
	}

	/** A cling may start: webbed fists on, or the symbiote's suit (its tendrils hold the wall). */
	public static boolean canCling(net.minecraft.world.entity.player.Player player) {
		return player.getAttached(WebStrength.FISTS) != null || WebSymbiote.suitShown(player);
	}

	/** A wall jump off a wall you aren't clinging to: only with webbed fists on. */
	public static boolean canWallJump(net.minecraft.world.entity.player.Player player) {
		return player.getAttached(WebStrength.FISTS) != null;
	}

	/** Clinging to a wall: the server lets them hang in the air. */
	public static boolean clinging(ServerPlayer player) {
		Doing doing = DOING.get(player.getUUID());
		return doing != null && doing.kind() == CLING;
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Move.TYPE, Move.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Move.TYPE, (payload, ctx) -> move(ctx.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(WebParkour::tick);
	}

	private static void move(ServerPlayer player, Move move) {
		int kind = move.kind();
		Direction side = move.face() >= 0 && move.face() < Direction.values().length ? Direction.from3DDataValue(move.face()) : null;
		boolean wallMove = kind == CLING || kind == WALL_JUMP;
		if (kind == CLING && !canCling(player) || kind == WALL_JUMP && !canWallJump(player) && !clinging(player)) {
			stop(player);
			return;
		}
		if (kind <= NONE || kind > SLIDE || !player.isAlive() || player.isSpectator() || player.isPassenger() || !Powers.has(player, WebPower.INSTANCE)
			|| wallMove && (side == null || !side.getAxis().isHorizontal() || !WebWallRun.wallAt(player.level(), player, side, 0.8))) {
			stop(player);
			return;
		}
		DOING.put(player.getUUID(), new Doing(kind, side == null ? -1 : side.get3DDataValue(), 0));
		int variant = 0;
		if (kind == VAULT || kind == SLIDE) {
			int[] last = LAST_VARIANT.computeIfAbsent(player.getUUID(), id -> new int[] {-1, -1});
			int slot = kind == VAULT ? 0 : 1;
			variant = player.getRandom().nextInt(last[slot] < 0 ? VARIANTS : VARIANTS - 1);
			if (last[slot] >= 0 && variant >= last[slot]) {
				variant++;
			}
			last[slot] = variant;
		}
		player.setAttached(MOVE, (variant * 8 + kind) * 8 + (side == null ? -1 : side.get3DDataValue()) + 1);
		if (kind != SLIDE) {
			player.resetFallDistance();
		}
		ServerLevel level = player.level();
		switch (kind) {
			case VAULT -> {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3F, 1.6F);
				dust(level, player, null, 4);
			}
			case CLING -> {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPIDER_STEP, SoundSource.PLAYERS, 0.6F, 1.2F);
				dust(level, player, side, 5);
			}
			case WALL_JUMP -> {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.7F, 1.4F);
				dust(level, player, side, 8);
			}
			case SLIDE -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SAND_STEP, SoundSource.PLAYERS, 0.8F, 0.7F);
			default -> {
			}
		}
	}

	private static void stop(ServerPlayer player) {
		DOING.remove(player.getUUID());
		player.removeAttached(MOVE);
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Doing>> it = DOING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Doing> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Doing doing = entry.getValue();
			int max = switch (doing.kind()) {
				case VAULT -> VAULT_TICKS;
				case WALL_JUMP -> WALL_JUMP_TICKS;
				case SLIDE -> SLIDE_TICKS + 2;
				default -> CLING_MAX_TICKS + 20;
			};
			Direction side = doing.face() >= 0 ? Direction.from3DDataValue(doing.face()) : null;
			boolean lost = doing.kind() == CLING && (player == null || player.onGround() || side == null || !WebWallRun.wallAt(player.level(), player, side, 0.8));
			if (player == null || !player.isAlive() || doing.ticks() >= max || lost || !Powers.has(player, WebPower.INSTANCE)) {
				it.remove();
				if (player != null) {
					player.removeAttached(MOVE);
				}
				continue;
			}
			entry.setValue(new Doing(doing.kind(), doing.face(), doing.ticks() + 1));
			if (doing.kind() == CLING || doing.kind() == WALL_JUMP) {
				player.resetFallDistance();
			}
			if (doing.kind() == SLIDE && doing.ticks() % 2 == 0 && player.onGround()) {
				dust(player.level(), player, null, 2);
			}
		}
	}

	/** A puff of the block's dust: off the wall at {@code side}, or the ground underfoot. */
	public static void dust(ServerLevel level, ServerPlayer player, @Nullable Direction side, int count) {
		BlockPos pos = side == null ? player.blockPosition().below()
			: BlockPos.containing(player.getX() + side.getStepX() * 0.8, player.getY() + 1.0, player.getZ() + side.getStepZ() * 0.8);
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		Vec3 at = side == null ? player.position().add(0.0, 0.1, 0.0) : player.position().add(side.getStepX() * 0.4, 1.0, side.getStepZ() * 0.4);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, count, 0.15, 0.15, 0.15, 0.05);
		if (side != null) {
			SoundType sound = state.getSoundType();
			level.playSound(null, at.x, at.y, at.z, sound.getStepSound(), SoundSource.PLAYERS, sound.getVolume() * 0.4F, sound.getPitch());
		}
	}
}
