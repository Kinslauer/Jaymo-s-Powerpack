package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * (0.7.332) What the blood does when a mortal player is in a vampire's sight, short of seizing them:
 *
 * <ul>
 * <li><b>Whispers.</b> Short, dark-red, italic lines on the action bar ("so close", "no one would know"), a little more
 * often the higher the fever, never more than one every {@link #WHISPER_COOLDOWN} ticks.</li>
 * <li><b>Turn your head.</b> With the fever past the urge's line, now and then (at most every {@link #TURN_COOLDOWN}
 * ticks) the blood turns their camera slowly toward the player, with a heartbeat. The server only says when and toward
 * whom ({@link Turn}); the client does the turning ({@code BloodTurn}).</li>
 * </ul>
 */
public final class BloodTemptation {
	/** How many whisper lines there are ({@code message.jaymoskingdoms.blood_whisper.N}). */
	static final int WHISPER_LINES = 12;
	/** The chance of a whisper in the second a player is in sight: at no fever, and at a fever of 100. */
	static final float WHISPER_CHANCE = 0.04F, WHISPER_CHANCE_FULL = 0.35F;
	/** At least this long between whispers (ticks). */
	static final long WHISPER_COOLDOWN = 160L;
	/** Past the urge's line, the chance of a head-turn each second a player is in sight; and the cooldown (ticks; ~45 s). */
	static final float TURN_CHANCE = 0.3F;
	static final long TURN_COOLDOWN = 900L;
	/** How long the client takes to turn (ticks). */
	static final int TURN_TICKS = 20;

	/** Server → vampire: turn your head toward this player (their entity id) over {@code ticks}. */
	public record Turn(int target, int ticks) implements CustomPacketPayload {
		public static final Type<Turn> TYPE = new Type<>(JaymosKingdoms.id("blood_turn"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Turn> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Turn::target,
			ByteBufCodecs.VAR_INT, Turn::ticks, Turn::new);

		@Override
		public Type<Turn> type() {
			return TYPE;
		}
	}

	/** No whisper / turn before this game time (not kept). */
	private static final Map<UUID, Long> WHISPER_READY = new HashMap<>(), TURN_READY = new HashMap<>();

	private BloodTemptation() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Turn.TYPE, Turn.CODEC);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WHISPER_READY.clear();
			TURN_READY.clear();
		});
	}

	/** Once a second, while {@code target} is in {@code vampire}'s sight. */
	static void seen(ServerPlayer vampire, ServerPlayer target, float fever) {
		if (Seizures.held(vampire) || vampire.isSleeping()) {
			return;
		}
		long now = vampire.level().getGameTime();
		UUID id = vampire.getUUID();
		if (now >= WHISPER_READY.getOrDefault(id, 0L)
			&& vampire.getRandom().nextFloat() < Mth.lerp(fever / 100.0F, WHISPER_CHANCE, WHISPER_CHANCE_FULL)) {
			WHISPER_READY.put(id, now + WHISPER_COOLDOWN);
			vampire.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_whisper." + (1 + vampire.getRandom().nextInt(WHISPER_LINES)))
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}
		if (fever >= BloodFever.urgeAt(vampire) && now >= TURN_READY.getOrDefault(id, 0L) && vampire.getRandom().nextFloat() < TURN_CHANCE
			&& ServerPlayNetworking.canSend(vampire, Turn.TYPE)) {
			TURN_READY.put(id, now + TURN_COOLDOWN);
			ServerPlayNetworking.send(vampire, new Turn(target.getId(), TURN_TICKS));
			vampire.level().playSound(null, vampire.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 1.1F);
		}
	}
}
