package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * The Training Post's four drills, played on a small screen on the client ({@code ZFighterDrillScreen}): Rhythm, Ki
 * Control, Shadow Boxing and Precision. Each ends with a score (0-100), sent here and paid in TP ({@link #TP_PER_POINT}
 * a point, before weights and heavy gravity), if he's still at a post and that drill has rested {@link #REST} ticks.
 */
public final class ZFighterDrills {
	public static final int RHYTHM = 0;
	public static final int KI_CONTROL = 1;
	public static final int SHADOW_BOXING = 2;
	public static final int PRECISION = 3;
	public static final int DRILLS = 4;
	public static final String[] KEYS = {"rhythm", "ki_control", "shadow_boxing", "precision"};
	public static final int MAX_SCORE = 100;
	static final float TP_PER_POINT = 0.5F;
	/** Each drill pays once in this long (ticks). */
	public static final int REST = 2400;
	static final int POST_RANGE = 4;

	/** Client → server: drill {@code drill} done with {@code score}. */
	public record Done(int drill, int score) implements CustomPacketPayload {
		public static final Type<Done> TYPE = new Type<>(JaymosPowers.id("zfighter_drill"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Done> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.drill);
				buf.writeVarInt(p.score);
			},
			buf -> new Done(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Done> type() {
			return TYPE;
		}
	}

	/** Player → drill → when it pays again (game time). */
	private static final Map<UUID, long[]> RESTING = new HashMap<>();

	private ZFighterDrills() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Done.TYPE, Done.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Done.TYPE, (payload, ctx) -> done(ctx.player(), payload.drill(), payload.score()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RESTING.clear());
		// a Z Fighter's use of the post is the drill screen (the client opens it): nothing else happens here
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> !level.isClientSide() && hand == InteractionHand.MAIN_HAND
			&& level.getBlockState(hit.getBlockPos()).is(ZFighterItems.TRAINING_POST) && Powers.has(player, ZFighterPower.INSTANCE)
			? InteractionResult.SUCCESS : InteractionResult.PASS);
	}

	/** Whether a Training Post stands within reach of {@code player}. Both sides. */
	public static boolean nearPost(net.minecraft.world.entity.player.Player player) {
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-POST_RANGE, -2, -POST_RANGE), at.offset(POST_RANGE, 2, POST_RANGE))) {
			if (player.level().getBlockState(pos).is(ZFighterItems.TRAINING_POST)) {
				return true;
			}
		}
		return false;
	}

	private static void done(ServerPlayer player, int drill, int score) {
		if (drill < 0 || drill >= DRILLS || !Powers.has(player, ZFighterPower.INSTANCE) || !nearPost(player)) {
			return;
		}
		score = Math.clamp(score, 0, MAX_SCORE);
		long now = player.level().getGameTime();
		long[] resting = RESTING.computeIfAbsent(player.getUUID(), id -> new long[DRILLS]);
		Component name = Component.translatable("drill.jaymospowers.zfighter." + KEYS[drill]);
		if (now < resting[drill]) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_drill_rest", name, (resting[drill] - now + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		resting[drill] = now + REST;
		float tp = score * TP_PER_POINT;
		ZFighterTraining.earn(player, tp);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_drill_done", name, score, Math.round(tp * ZFighterTraining.rate(player)))
			.withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F,
			0.8F + score / 100.0F);
	}
}
