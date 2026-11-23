package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * An illager's talk card ({@link IllagerTalk}), drawn like MCA's villager screen (Andy, 2026-11-23: "these pillager
 * interactions should look and work just like MCA"): who they are down the left (name, what they are, mood, nature, how
 * they take you, hearts), what they said in the words box, and each answer placed where MCA keeps its like ({@link #slots}):
 * on the first page's column ({@link #MAIN}), behind Talk ({@link #TALK}) or Interact ({@link #INTERACT}), as the Gift
 * button ({@link #GIFT}), as the back arrow ({@link #BACK}), or straight in the column ({@link #LIST}: a parley, the trade
 * list, Talk Them Round's moves). An answer that ends the talk ({@link #ends}) shuts the screen when picked; the others
 * keep it open for the next card. Picks come back as {@link TalkNet.Answer}, through {@link Menus}.
 */
public record IllagerCard(int entityId, Component name, Component kind, Component nature, Component attitude, Component mood, int moodStep,
	int hearts, Component words, List<Component> options, List<Boolean> enabled, List<String> slots, List<Boolean> ends) implements CustomPacketPayload {
	public static final String MAIN = "main", TALK = "talk", INTERACT = "interact", GIFT = "gift", BACK = "back", LIST = "";
	public static final Type<IllagerCard> TYPE = new Type<>(JaymosKingdoms.id("illager_card"));
	public static final StreamCodec<RegistryFriendlyByteBuf, IllagerCard> CODEC = CustomPacketPayload.codec(
		(p, buf) -> {
			buf.writeVarInt(p.entityId);
			for (Component c : List.of(p.name, p.kind, p.nature, p.attitude, p.mood)) {
				ComponentSerialization.STREAM_CODEC.encode(buf, c);
			}
			buf.writeVarInt(p.moodStep);
			buf.writeVarInt(p.hearts);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.words);
			buf.writeVarInt(p.options.size());
			for (int i = 0; i < p.options.size(); i++) {
				ComponentSerialization.STREAM_CODEC.encode(buf, p.options.get(i));
				buf.writeBoolean(p.enabled.get(i));
				buf.writeUtf(p.slots.get(i));
				buf.writeBoolean(p.ends.get(i));
			}
		},
		buf -> {
			int entity = buf.readVarInt();
			Component[] head = new Component[5];
			for (int i = 0; i < head.length; i++) {
				head[i] = ComponentSerialization.STREAM_CODEC.decode(buf);
			}
			int moodStep = buf.readVarInt();
			int hearts = buf.readVarInt();
			Component words = ComponentSerialization.STREAM_CODEC.decode(buf);
			int n = buf.readVarInt();
			List<Component> options = new ArrayList<>();
			List<Boolean> enabled = new ArrayList<>();
			List<String> slots = new ArrayList<>();
			List<Boolean> ends = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				options.add(ComponentSerialization.STREAM_CODEC.decode(buf));
				enabled.add(buf.readBoolean());
				slots.add(buf.readUtf());
				ends.add(buf.readBoolean());
			}
			return new IllagerCard(entity, head[0], head[1], head[2], head[3], head[4], moodStep, hearts, words, options, enabled, slots, ends);
		});

	@Override
	public Type<IllagerCard> type() {
		return TYPE;
	}

	/** An answer on the card: what it does ({@link Menus.Choice}), where it sits, and whether picking it ends the talk. */
	record Option(Menus.Choice choice, String slot, boolean ends) {
		static Option of(Menus.Choice choice, String slot) {
			return new Option(choice, slot, false);
		}

		static Option ending(Menus.Choice choice, String slot) {
			return new Option(choice, slot, true);
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** {@code speaker} shows {@code player} the card headed by {@code head} (name, kind, nature, attitude, mood) with {@code options}. */
	static void send(ServerPlayer player, LivingEntity speaker, List<Component> head, int moodStep, int hearts, Component words, List<Option> options) {
		if (!ServerPlayNetworking.canSend(player, TYPE)) {
			return;
		}
		List<Menus.Choice> choices = new ArrayList<>();
		List<Component> labels = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		List<String> slots = new ArrayList<>();
		List<Boolean> ends = new ArrayList<>();
		for (Option o : options) {
			choices.add(o.choice());
			labels.add(o.choice().label());
			enabled.add(o.choice().enabled());
			slots.add(o.slot());
			ends.add(o.ends());
		}
		Menus.open(player, speaker, choices);
		ServerPlayNetworking.send(player, new IllagerCard(speaker.getId(), head.get(0), head.get(1), head.get(2), head.get(3), head.get(4), moodStep, hearts,
			words, labels, enabled, slots, ends));
	}
}
