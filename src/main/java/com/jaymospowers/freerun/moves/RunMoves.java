package com.jaymospowers.freerun.moves;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.freerun.LandingRoll;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (ParCool track B, Andy 2026-11-07: "make a checklist of everything we dont have yet", then his picks) The running,
 * falling, swimming and hiding moves, everyone's, after ParCool's ideas (alRex-U, LGPL, Forge-only: read for the idea,
 * nothing of its code or art taken). Their costs come out of {@code Feathers}.
 * <ul>
 * <li>{@link LongJump}: in a fast run, tap Sneak as you jump: a long, flat leap.</li>
 * <li>{@link ChargeJump}: stand still and hold Sneak to wind up, Jump to leap up to twice as high.</li>
 * <li>{@link TrickJump}: a jump out of a fast run now and then flips on its own.</li>
 * <li>Safety tap ({@link LandingRoll#safetyTap}): the dodge at touchdown with no direction held, a crouched landing
 * that soaks up part of the fall without the roll.</li>
 * <li>{@link SkyDive}: in a long fall, Jump spreads you out: a slower fall, faster steering; Jump again ends it.</li>
 * <li>{@link FastSwim}: swimming flat out for a while breaks into a faster butterfly stroke.</li>
 * <li>{@link Hiding}: sneak into a leaves block or a hay bale to hide inside it.</li>
 * </ul>
 * Like the other freerun moves they are the client's ({@code client/freerun/moves/RunMovesClient} moves the body), and it
 * tells the server each one ({@link Ask}), which checks it, charges the feathers and shows it to everyone
 * ({@link #POSE}, drawn by {@code RunMovesPose}).
 */
public final class RunMoves {
	/** What an {@link Ask} is for. */
	public static final int LONG_JUMP = 1, CHARGE_JUMP = 2, TRICK_JUMP = 3, SAFETY_TAP = 4, SKY_DIVE = 5, SKY_DIVE_END = 6, HIDE = 7, UNHIDE = 8;

	/**
	 * A short whole-body move everyone draws ({@code RunMovesPose}): the kind ({@link #LONG_JUMP} or {@link #SAFETY_TAP})
	 * in the top byte, the game time it began below. Missing: none.
	 */
	public static final AttachmentType<Long> POSE = AttachmentRegistry.create(JaymosPowers.id("run_move_pose"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Client → server: {@code move} ({@link #LONG_JUMP} and the rest), with what it needs ({@code arg}). */
	public record Ask(int move, long arg) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosPowers.id("run_move"));
		public static final StreamCodec<ByteBuf, Ask> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Ask::move, ByteBufCodecs.VAR_LONG, Ask::arg, Ask::new);

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	private RunMoves() {
	}

	/** From {@code JaymosPowers.onInitialize}. */
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, ctx) -> ask(ctx.player(), payload));
		SkyDive.init();
		FastSwim.init();
		Hiding.init();
	}

	private static void ask(ServerPlayer player, Ask ask) {
		switch (ask.move()) {
			case LONG_JUMP -> LongJump.jumped(player);
			case CHARGE_JUMP -> ChargeJump.jumped(player, (int)Math.max(0L, Math.min(ChargeJump.FULL_TICKS, ask.arg())));
			case TRICK_JUMP -> TrickJump.flip(player, (int)ask.arg());
			case SAFETY_TAP -> LandingRoll.safetyTap(player);
			case SKY_DIVE -> SkyDive.start(player);
			case SKY_DIVE_END -> SkyDive.stop(player);
			case HIDE -> Hiding.hide(player, ask.arg());
			case UNHIDE -> Hiding.leave(player);
			default -> {
			}
		}
	}

	/** Shows {@code kind} on {@code player}'s body for everyone, from now. */
	public static void pose(ServerPlayer player, int kind) {
		player.setAttached(POSE, (long)kind << 56 | player.level().getGameTime() & 0x00FF_FFFF_FFFF_FFFFL);
	}

	/** The move {@code player}'s body shows (any side): {kind, start game time}, or null. */
	public static long @Nullable [] pose(Player player) {
		Long packed = player.getAttached(POSE);
		return packed == null ? null : new long[] {packed >>> 56, packed & 0x00FF_FFFF_FFFF_FFFFL};
	}
}
