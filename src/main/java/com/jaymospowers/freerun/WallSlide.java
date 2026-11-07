package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

/**
 * (ParCool track A) The wall slide, Andy's pick from ParCool's moves (our own code after the idea): falling beside or
 * facing a wall, hold right-click with an empty hand (the ledge grab's own click) to press against it and slow the fall
 * to {@link #FALL_SPEED}. Every tick on the wall sheds {@link #FALL_CUT} blocks of the fall, and a slide of
 * {@link #SAFE_TICKS} or more takes all of it. Ice, glass and leaves are too slippery ({@link FreerunWalls#slippery}).
 * It costs Feathers: {@link #START_COST} to press on, then {@link #HOLD_COST} every {@link #COST_TICKS}. A long slide
 * burns bare hands ({@link #BURN_DAMAGE} every {@link #BURN_TICKS}) unless something guards them ({@link #addHandGuard}:
 * the traceur gloves).
 * <p>Like the other freerun moves, the move is the client's ({@code client/freerun/WallSlideClient}); it tells the
 * server as it presses on and lets go ({@link Slide}). The server checks the wall and the Feathers, sheds the fall,
 * burns the hands, and plays the scrape.
 */
public final class WallSlide {
	// ===== the knobs ================================================================================================
	/** Pressed to the wall you fall no faster than this (blocks/tick), braking toward it by this share of the excess each tick. */
	public static final double FALL_SPEED = 0.15, BRAKE = 0.35;
	/** Within this much (blocks/tick) of {@link #FALL_SPEED}, the fall is simply held at it. */
	public static final double SNAP = 0.12;
	/** You must be falling at least this fast (blocks/tick) to press on: not at the top of a jump. */
	public static final double MIN_FALL = 0.08;
	/** The wall must be within this far of the body (blocks): the client's reach, and the server's (looser) check. */
	public static final double REACH = 0.35, SERVER_REACH = 0.8;
	/** Pressed into the wall this hard each tick (blocks/tick), so you stay on it. */
	public static final double HUG = 0.03;
	/** Every tick on the wall sheds this much of the fall (blocks of fall distance); this long on it (ticks) sheds all of it. */
	public static final double FALL_CUT = 0.5;
	public static final int SAFE_TICKS = 30;
	/** Feathers (half-feathers) to press on, then to stay on, every this many ticks. */
	public static final int START_COST = 1, HOLD_COST = 1, COST_TICKS = 20;
	/** Bare hands burn on a long slide: this much damage (half a heart) every this many ticks on the wall. */
	public static final float BURN_DAMAGE = 1.0F;
	public static final int BURN_TICKS = 40;
	/** The scrape's sound, every this many ticks. */
	public static final int SOUND_TICKS = 6;
	/** Server: a slide not heard to end is dropped after this long (ticks: the longest fall there is). */
	static final int MAX_TICKS = 1200;
	// ================================================================================================================

	/** Client → server: pressed against the wall on side {@code face} (a horizontal direction's 2D value), or -1: let go. */
	public record Slide(int face) implements CustomPacketPayload {
		public static final Type<Slide> TYPE = new Type<>(JaymosPowers.id("wall_slide"));
		public static final StreamCodec<ByteBuf, Slide> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Slide::face, Slide::new);

		@Override
		public Type<Slide> type() {
			return TYPE;
		}
	}

	/** Server: a slide in progress (the wall's side and the ticks on it). */
	private static final class Sliding {
		final Direction face;
		int ticks;

		Sliding(Direction face) {
			this.face = face;
		}
	}

	private static final Map<UUID, Sliding> SLIDING = new HashMap<>();
	/** What keeps a player's hands from burning on the wall (the traceur gloves add theirs). */
	private static final List<Predicate<Player>> HAND_GUARDS = new CopyOnWriteArrayList<>();

	private WallSlide() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Slide.TYPE, Slide.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Slide.TYPE, (payload, ctx) -> slide(ctx.player(), payload.face()));
		ServerTickEvents.END_SERVER_TICK.register(WallSlide::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SLIDING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SLIDING.clear());
	}

	// ---- the hook for gear ------------------------------------------------------------------------------------------

	/**
	 * Something that keeps a player's hands from burning on a wall slide (true: guarded). The traceur gloves (track B,
	 * {@code freerun/gear/}) register theirs here once, at init: {@code WallSlide.addHandGuard(player -> wearsGloves(player))}.
	 */
	public static void addHandGuard(Predicate<Player> guard) {
		HAND_GUARDS.add(guard);
	}

	/** Whether anything guards this player's hands on a wall slide ({@link #addHandGuard}). */
	public static boolean handsGuarded(Player player) {
		for (Predicate<Player> guard : HAND_GUARDS) {
			if (guard.test(player)) {
				return true;
			}
		}
		return false;
	}

	// ---- both sides -------------------------------------------------------------------------------------------------

	/** Whether this player may wall slide at all right now: free to freerun ({@link Freerun#canMove}), in the air, not swimming. */
	public static boolean canSlide(Player player) {
		return Freerun.canMove(player) && !player.onGround() && !player.isInWater() && !player.isInLava();
	}

	/** A wall to slide on, on side {@code face} within {@code reach}: there knee-high and chest-high, and nothing slippery in it. */
	public static boolean slideWall(Level level, Player player, Direction face, double reach) {
		if (!FreerunWalls.wallAtBox(level, player, player.getBoundingBox(), face, reach)) {
			return false;
		}
		for (double up : new double[] {0.5, 1.2}) {
			BlockState state = level.getBlockState(wallBlock(player, face, reach, up));
			if (FreerunWalls.slippery(state)) {
				return false;
			}
		}
		return true;
	}

	/** The block of the wall on side {@code face}, {@code up} blocks over the feet. */
	public static BlockPos wallBlock(Player player, Direction face, double reach, double up) {
		double out = player.getBbWidth() * 0.5 + reach;
		return BlockPos.containing(player.getX() + face.getStepX() * out, player.getY() + up, player.getZ() + face.getStepZ() * out);
	}

	/** Whether this player is wall sliding (the server's own record; on a client, see {@code WallSlideClient}). */
	public static boolean sliding(ServerPlayer player) {
		return SLIDING.containsKey(player.getUUID());
	}

	// ---- server -----------------------------------------------------------------------------------------------------

	private static void slide(ServerPlayer player, int face) {
		if (face < 0 || face > 3) {
			SLIDING.remove(player.getUUID());
			return;
		}
		Direction side = Direction.from2DDataValue(face);
		if (SLIDING.containsKey(player.getUUID())) {
			return;
		}
		if (!canSlide(player) || Ledge.powerMoving(player) || !slideWall(player.level(), player, side, SERVER_REACH)
			|| !Feathers.spend(player, START_COST)) {
			return;
		}
		SLIDING.put(player.getUUID(), new Sliding(side));
		Freerun.moved(player);
		scrape(player, side, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Sliding>> it = SLIDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Sliding> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Sliding sliding = entry.getValue();
			if (player == null || !player.isAlive() || !canSlide(player) || sliding.ticks > MAX_TICKS
				|| !slideWall(player.level(), player, sliding.face, SERVER_REACH)) {
				it.remove();
				continue;
			}
			sliding.ticks++;
			if (sliding.ticks % COST_TICKS == 0 && !Feathers.spend(player, HOLD_COST)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.freerun.wall_slide.tired").withStyle(ChatFormatting.GRAY));
				it.remove();
				continue;
			}
			// the wall takes the fall off you as you go
			if (sliding.ticks >= SAFE_TICKS) {
				player.resetFallDistance();
			} else {
				player.fallDistance = Math.max(0.0, player.fallDistance - FALL_CUT);
			}
			if (sliding.ticks % BURN_TICKS == 0 && !handsGuarded(player)) {
				ServerLevel level = player.level();
				player.hurtServer(level, level.damageSources().generic(), BURN_DAMAGE);
				if (sliding.ticks == BURN_TICKS) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.freerun.wall_slide.burn").withStyle(ChatFormatting.GRAY));
				}
			}
			if (sliding.ticks % SOUND_TICKS == 0) {
				scrape(player, sliding.face, 0.35F);
			}
		}
	}

	/** The wall's own scrape under the hands. */
	private static void scrape(ServerPlayer player, Direction face, float volume) {
		ServerLevel level = player.level();
		SoundType sound = level.getBlockState(wallBlock(player, face, SERVER_REACH * 0.5, 1.2)).getSoundType();
		level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), sound.getStepSound(), SoundSource.PLAYERS,
			sound.getVolume() * volume, sound.getPitch() * 0.8F);
	}
}
