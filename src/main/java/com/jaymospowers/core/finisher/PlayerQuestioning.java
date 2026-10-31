package com.jaymospowers.core.finisher;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.96) Andy: "can you make one?" (a screen for a player held down for questioning). When the one held down
 * ({@link Finishers}' questioning holds) is a player, the asker gets a screen of questions and the replies, and the one held
 * gets a screen to answer from: the truth, a lie, silence, or words of their own. Their realm's truths come from Jaymo's
 * Kingdoms ({@link KingdomsQuestioning#playerAnswer}). The asker's perks: Will's Interrogation sees through a lie,
 * Strength's Rough Questions makes the threat hurt.
 */
public final class PlayerQuestioning {
	// ================================================================================================================
	// Tuning
	/** The longest an answer in your own words may be (characters). */
	public static final int WORDS_MAX = 120;
	/** (Rough Questions) What a threat costs the one held down (health; never below one heart). */
	static final float ROUGH_HURT = 2.0F;
	// ================================================================================================================

	/** The questions, by number: who leads you, where's home, a threat, swear (Will), let them go, finish them. */
	public static final int LEADER = 0, HOME = 1, THREATEN = 2, SWEAR = 3, SPARE = 4, FINISH = 5, QUESTIONS = 6;
	/** How the held answers: the truth, a lie, nothing, or their own words. */
	public static final int TRUTH = 0, LIE = 1, SILENT = 2, WORDS = 3;

	/** Server → asker: you're questioning this player ({@code swear}: Will's oath is yours to ask). */
	public record OpenAsk(Component held, boolean swear) implements CustomPacketPayload {
		public static final Type<OpenAsk> TYPE = new Type<>(JaymosPowers.id("question_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenAsk> CODEC = StreamCodec.composite(ComponentSerialization.STREAM_CODEC,
			OpenAsk::held, ByteBufCodecs.BOOL, OpenAsk::swear, OpenAsk::new);

		@Override
		public Type<OpenAsk> type() {
			return TYPE;
		}
	}

	/** Server → the held: this one holds you down for questioning. */
	public record OpenHeld(Component asker) implements CustomPacketPayload {
		public static final Type<OpenHeld> TYPE = new Type<>(JaymosPowers.id("question_held"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenHeld> CODEC = ComponentSerialization.STREAM_CODEC.map(OpenHeld::new, OpenHeld::asker);

		@Override
		public Type<OpenHeld> type() {
			return TYPE;
		}
	}

	/** Server → either: a line for the screen's log ({@code question} ≥ 0 to the held: it's put to you, answer it). */
	public record Said(Component line, int question) implements CustomPacketPayload {
		public static final Type<Said> TYPE = new Type<>(JaymosPowers.id("question_said"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Said> CODEC = StreamCodec.composite(ComponentSerialization.STREAM_CODEC, Said::line,
			ByteBufCodecs.VAR_INT, Said::question, Said::new);

		@Override
		public Type<Said> type() {
			return TYPE;
		}
	}

	/** Server → either: it's over, the screen closes. */
	public record Close() implements CustomPacketPayload {
		public static final Type<Close> TYPE = new Type<>(JaymosPowers.id("question_close"));
		public static final StreamCodec<ByteBuf, Close> CODEC = StreamCodec.unit(new Close());

		@Override
		public Type<Close> type() {
			return TYPE;
		}
	}

	/** Asker → server: put this question. */
	public record Ask(int question) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosPowers.id("question_put"));
		public static final StreamCodec<ByteBuf, Ask> CODEC = ByteBufCodecs.VAR_INT.map(Ask::new, Ask::question);

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** The held → server: how they answer, and their own words if that's how. */
	public record Answer(int how, String words) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(JaymosPowers.id("question_answer"));
		public static final StreamCodec<ByteBuf, Answer> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Answer::how,
			ByteBufCodecs.stringUtf8(WORDS_MAX), Answer::words, Answer::new);

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	/** One questioning: the asker, and the question waiting on the held (-1 for none). */
	private static final class Session {
		final UUID asker;
		int pending = -1;

		Session(UUID asker) {
			this.asker = asker;
		}
	}

	/** By the held player's id. */
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private PlayerQuestioning() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenAsk.TYPE, OpenAsk.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenHeld.TYPE, OpenHeld.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Said.TYPE, Said.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Close.TYPE, Close.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, ctx) -> ask(ctx.player(), payload.question()));
		ServerPlayNetworking.registerGlobalReceiver(Answer.TYPE, (payload, ctx) -> answer(ctx.player(), payload.how(), payload.words()));
	}

	/** {@code asker} holds {@code held} down for questioning: both screens open. */
	static void open(ServerPlayer asker, ServerPlayer held) {
		SESSIONS.put(held.getUUID(), new Session(asker.getUUID()));
		ServerPlayNetworking.send(asker, new OpenAsk(held.getDisplayName(), Finishers.questionPerk(asker, Finishers.WILL_PERK)));
		ServerPlayNetworking.send(held, new OpenHeld(asker.getDisplayName()));
	}

	/** It's over (let go, finished, or slipped away): both screens close. */
	static void ended(ServerPlayer asker, LivingEntity body) {
		if (!(body instanceof ServerPlayer held)) {
			return;
		}
		Session session = SESSIONS.get(held.getUUID());
		if (session == null || !session.asker.equals(asker.getUUID())) {
			return;
		}
		SESSIONS.remove(held.getUUID());
		ServerPlayNetworking.send(asker, new Close());
		ServerPlayNetworking.send(held, new Close());
	}

	/** Who {@code asker} is questioning just now, or null. */
	private static @Nullable ServerPlayer heldBy(ServerPlayer asker) {
		for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
			if (e.getValue().asker.equals(asker.getUUID())) {
				return asker.level().getServer().getPlayerList().getPlayer(e.getKey());
			}
		}
		return null;
	}

	private static void ask(ServerPlayer asker, int question) {
		ServerPlayer held = heldBy(asker);
		if (held == null || question < 0 || question >= QUESTIONS || !Finishers.questioning(asker)) {
			return;
		}
		Session session = SESSIONS.get(held.getUUID());
		switch (question) {
			case SPARE -> Finishers.questionDone(asker, false);
			case FINISH -> Finishers.questionDone(asker, true);
			case THREATEN -> {
				boolean rough = Finishers.questionPerk(asker, Finishers.ROUGH_PERK);
				if (rough && held.getHealth() > ROUGH_HURT + 2.0F) {
					// (Rough Questions) it bites
					held.setHealth(held.getHealth() - ROUGH_HURT);
					held.level().broadcastEntityEvent(held, (byte) 2);
					held.level().playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
				}
				Component line = Component.translatable(rough ? "message.jaymospowers.question.threat_rough" : "message.jaymospowers.question.threat",
					asker.getDisplayName());
				ServerPlayNetworking.send(held, new Said(line.copy().withStyle(ChatFormatting.RED), -1));
				ServerPlayNetworking.send(asker, new Said(Component.translatable("message.jaymospowers.question.you_threaten").withStyle(ChatFormatting.GRAY), -1));
			}
			default -> {
				if (question == SWEAR && !Finishers.questionPerk(asker, Finishers.WILL_PERK)) {
					return;
				}
				session.pending = question;
				Component put = Component.translatable("message.jaymospowers.question.q" + question);
				ServerPlayNetworking.send(held, new Said(Component.translatable("message.jaymospowers.question.asks", asker.getDisplayName(), put)
					.withStyle(ChatFormatting.GOLD), question));
				ServerPlayNetworking.send(asker, new Said(Component.translatable("message.jaymospowers.question.you_ask", put).withStyle(ChatFormatting.GRAY), -1));
			}
		}
	}

	private static void answer(ServerPlayer held, int how, String words) {
		Session session = SESSIONS.get(held.getUUID());
		ServerPlayer asker = session == null ? null : held.level().getServer().getPlayerList().getPlayer(session.asker);
		if (session == null || asker == null || session.pending < 0) {
			return;
		}
		int question = session.pending;
		session.pending = -1;
		Component said = switch (how) {
			case SILENT -> Component.translatable("message.jaymospowers.question.silent", held.getDisplayName());
			case WORDS -> Component.translatable("message.jaymospowers.question.says", held.getDisplayName(),
				Component.literal(words.strip().isEmpty() ? "..." : words.strip()));
			default -> {
				Component truth = question == SWEAR
					? Component.translatable("message.jaymospowers.question.sworn")
					: KingdomsQuestioning.playerAnswer(asker, held, question == LEADER ? "leader" : "home", how == LIE);
				yield Component.translatable("message.jaymospowers.question.says", held.getDisplayName(),
					truth == null ? Component.translatable("message.jaymospowers.question.nothing_to_tell") : truth);
			}
		};
		ServerPlayNetworking.send(held, new Said(Component.translatable("message.jaymospowers.question.you_said", said).withStyle(ChatFormatting.GRAY), -1));
		Component heard = said.copy().withStyle(ChatFormatting.WHITE);
		if (how == LIE && Finishers.questionPerk(asker, Finishers.WILL_PERK)) {
			// (Interrogation) no lie gets past you
			heard = Component.empty().append(heard).append(Component.translatable("message.jaymospowers.question.lie_seen").withStyle(ChatFormatting.RED));
		}
		ServerPlayNetworking.send(asker, new Said(heard, -1));
	}
}
