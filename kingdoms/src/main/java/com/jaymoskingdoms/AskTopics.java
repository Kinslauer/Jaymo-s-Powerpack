package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Asking a villager anything (Andy's Buder's Lanterns picks, ideas only; nothing goes online). "Ask something..." on a villager's
 * talk screen ({@link TalkErrands}) opens a talk with a box to type in: the words are matched to a topic by {@link ChatTalk}'s
 * matcher (their name, work, family, the realm, news, a crime, the way somewhere, the weather...) and they answer in their own way,
 * by how they bear toward you. Beside the answer, {@link #FOLLOW_UPS} questions that fit it can be clicked instead of typed.
 * <p>The talk card ({@code TalkScreen}) is told just before a talk of this kind ({@link Typable}): it shows the box, and the last
 * {@code follow} answers in a side panel. What's typed comes back as {@link Typed}.
 */
public final class AskTopics {
	/** How many follow-up questions are offered; the longest a typed question may be. */
	public static final int FOLLOW_UPS = 3, LONGEST = 120;

	/** Server → player: {@code entityId}'s next talk card takes a typed question, and its last {@code follow} answers are follow-ups. */
	public record Typable(int entityId, int follow) implements CustomPacketPayload {
		public static final Type<Typable> TYPE = new Type<>(JaymosKingdoms.id("ask_typable"));
		static final StreamCodec<RegistryFriendlyByteBuf, Typable> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				buf.writeVarInt(p.follow);
			},
			buf -> new Typable(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Typable> type() {
			return TYPE;
		}
	}

	/** Player → server: they typed {@code text} to {@code entityId}. */
	public record Typed(int entityId, String text) implements CustomPacketPayload {
		public static final Type<Typed> TYPE = new Type<>(JaymosKingdoms.id("ask_typed"));
		static final StreamCodec<RegistryFriendlyByteBuf, Typed> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				buf.writeUtf(p.text, LONGEST);
			},
			buf -> new Typed(buf.readVarInt(), buf.readUtf(LONGEST)));

		@Override
		public Type<Typed> type() {
			return TYPE;
		}
	}

	/** What fits to ask next, after each topic (and after anything else: {@link #FIRST}). */
	private static final Map<ChatTalk.Topic, List<ChatTalk.Topic>> NEXT = new EnumMap<>(ChatTalk.Topic.class);
	private static final List<ChatTalk.Topic> FIRST = List.of(ChatTalk.Topic.NAME, ChatTalk.Topic.NEWS, ChatTalk.Topic.DIRECTIONS);

	static {
		NEXT.put(ChatTalk.Topic.NAME, List.of(ChatTalk.Topic.WORK, ChatTalk.Topic.FAMILY, ChatTalk.Topic.REALM));
		NEXT.put(ChatTalk.Topic.WORK, List.of(ChatTalk.Topic.TRADE, ChatTalk.Topic.NEWS, ChatTalk.Topic.FAMILY));
		NEXT.put(ChatTalk.Topic.FAMILY, List.of(ChatTalk.Topic.NAME, ChatTalk.Topic.MOOD, ChatTalk.Topic.REALM));
		NEXT.put(ChatTalk.Topic.REALM, List.of(ChatTalk.Topic.DIRECTIONS, ChatTalk.Topic.NEWS, ChatTalk.Topic.CRIME));
		NEXT.put(ChatTalk.Topic.NEWS, List.of(ChatTalk.Topic.CRIME, ChatTalk.Topic.DIRECTIONS, ChatTalk.Topic.ME));
		NEXT.put(ChatTalk.Topic.CRIME, List.of(ChatTalk.Topic.NEWS, ChatTalk.Topic.ME, ChatTalk.Topic.REALM));
		NEXT.put(ChatTalk.Topic.DIRECTIONS, List.of(ChatTalk.Topic.REALM, ChatTalk.Topic.WEATHER, ChatTalk.Topic.NEWS));
		NEXT.put(ChatTalk.Topic.WEATHER, List.of(ChatTalk.Topic.MOOD, ChatTalk.Topic.NEWS, ChatTalk.Topic.DIRECTIONS));
		NEXT.put(ChatTalk.Topic.MOOD, List.of(ChatTalk.Topic.FAMILY, ChatTalk.Topic.WORK, ChatTalk.Topic.WEATHER));
		NEXT.put(ChatTalk.Topic.ME, List.of(ChatTalk.Topic.NEWS, ChatTalk.Topic.CRIME, ChatTalk.Topic.NAME));
	}

	private AskTopics() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Typable.TYPE, Typable.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Typed.TYPE, Typed.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Typed.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.level().getEntity(payload.entityId()) instanceof Villager v && v.isAlive() && v.distanceTo(player) <= Menus.REACH) {
				String text = payload.text().strip();
				if (!text.isEmpty()) {
					ChatTalk.Topic topic = ChatTalk.topic(text.toLowerCase(Locale.ROOT));
					answer(player, v, topic);
				}
			}
		});
	}

	/** {@code speaker} can be asked things: a grown villager. */
	static boolean canAsk(LivingEntity speaker) {
		return speaker instanceof Villager v && !v.isBaby();
	}

	/** The first card: what do you want to know? */
	static void open(ServerPlayer player, LivingEntity speaker) {
		if (speaker instanceof Villager v) {
			show(player, v, Component.translatable("talk.jaymoskingdoms.ask.open"), FIRST);
		}
	}

	/** {@code v}'s answer on {@code topic} (null: they didn't follow), with what fits to ask next. */
	private static void answer(ServerPlayer player, Villager v, ChatTalk.@Nullable Topic topic) {
		if (topic == ChatTalk.Topic.BYE) {
			Speech.tell(player.level(), v, player, ChatTalk.answer(player, v, topic, v.getRandom()), Speech.WHITE);
			return;
		}
		Component said = ChatTalk.answer(player, v, topic, v.getRandom());
		show(player, v, said, topic == null ? FIRST : NEXT.getOrDefault(topic, FIRST));
	}

	private static void show(ServerPlayer player, Villager v, Component words, List<ChatTalk.Topic> next) {
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(Component.translatable("talk.jaymoskingdoms.ask.done"), p -> {
		}));
		int follow = Math.min(FOLLOW_UPS, next.size());
		for (int i = 0; i < follow; i++) {
			ChatTalk.Topic t = next.get(i);
			choices.add(Menus.Choice.of(Component.translatable("talk.jaymoskingdoms.ask.q." + t.name().toLowerCase(Locale.ROOT)), p -> answer(p, v, t)));
		}
		if (ServerPlayNetworking.canSend(player, Typable.TYPE)) {
			ServerPlayNetworking.send(player, new Typable(v.getId(), follow));
		}
		Menus.ask(player, v, words, choices);
	}
}
