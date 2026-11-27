package com.jaymoskingdoms;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Server to a player talking to an NPC: whose turn it is - {@link Speaker#PLAYER} the player just picked an
 * answer, {@link Speaker#VILLAGER} the villager is saying something - so the client's letterbox camera swaps to
 * whoever is speaking ({@link com.jaymoskingdoms.client.DialogueCinematic}). A {@code null} speaker ends it when
 * the talk screen closes.
 */
public record DialogueCue(int villager, @Nullable Speaker speaker) implements CustomPacketPayload {
	public static final Type<DialogueCue> TYPE = new Type<>(JaymosKingdoms.id("dialogue_cue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueCue> CODEC = CustomPacketPayload.codec(
		(p, buf) -> buf.writeInt(p.villager).writeVarInt(p.speaker == null ? -1 : p.speaker.ordinal()),
		buf -> new DialogueCue(buf.readInt(), readSpeaker(buf)));

	private static Speaker readSpeaker(RegistryFriendlyByteBuf buf) {
		int ordinal = buf.readVarInt();
		return ordinal >= 0 && ordinal < Speaker.values().length ? Speaker.values()[ordinal] : null;
	}

	/**
	 * The one talking right now. {@code LAST}: the villager, with their last word: the talk ends once it's been shown
	 * (bored of you, or left waiting: {@link Conversations}).
	 */
	public enum Speaker {
		PLAYER, VILLAGER, LAST
	}

	private DialogueCue() {
		this(-1, null);
	}

	@Override
	public Type<DialogueCue> type() {
		return TYPE;
	}

	/**
	 * Client to server: the talk with {@code villager} is over (their reply has been said and read, or the menu was left
	 * with nothing said): they may go back to their work ({@link Conversations}).
	 */
	public record Done(int villager) implements CustomPacketPayload {
		public static final Type<Done> TYPE = new Type<>(JaymosKingdoms.id("dialogue_done"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Done> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeInt(p.villager), buf -> new Done(buf.readInt()));

		@Override
		public Type<Done> type() {
			return TYPE;
		}
	}

	/**
	 * Client to server (Andy 2026-11-27: "it should go back to the wheel"): {@code villager}'s menu shut after a talk
	 * action and their reply has been shown: the talk goes back to the wheel, or ends if it's over ({@link Conversations#again}).
	 */
	public record Again(int villager) implements CustomPacketPayload {
		public static final Type<Again> TYPE = new Type<>(JaymosKingdoms.id("dialogue_again"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Again> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeInt(p.villager), buf -> new Again(buf.readInt()));

		@Override
		public Type<Again> type() {
			return TYPE;
		}
	}

	/**
	 * Client to server: the talk with {@code villager} is set aside for another screen that belongs to it (Family Tree, one
	 * of our talk screens), and goes on after ({@link Conversations#aside}).
	 */
	public record Aside(int villager) implements CustomPacketPayload {
		public static final Type<Aside> TYPE = new Type<>(JaymosKingdoms.id("dialogue_aside"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Aside> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeInt(p.villager), buf -> new Aside(buf.readInt()));

		@Override
		public Type<Aside> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Done.TYPE, Done.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Again.TYPE, Again.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Aside.TYPE, Aside.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Done.TYPE, (payload, context) -> Conversations.done(context.player(), payload.villager()));
		ServerPlayNetworking.registerGlobalReceiver(Again.TYPE, (payload, context) -> Conversations.again(context.player(), payload.villager()));
		ServerPlayNetworking.registerGlobalReceiver(Aside.TYPE, (payload, context) -> Conversations.aside(context.player(), payload.villager()));
		Conversations.register();
		TalkBusy.register();
		// (and who is busy, told to the players near them for the talk line by the crosshair)
		ContextBusy.register();
		RefusalHints.register();
	}

	/** Tell {@code player}'s client that {@code villager}'s turn is {@code speaker}; the talk holds them both still till it's over. */
	public static void cue(ServerPlayer player, Entity villager, Speaker speaker) {
		if (villager != null && ServerPlayNetworking.canSend(player, TYPE)) {
			Conversations.begin(player, villager, true);
			if (speaker == Speaker.PLAYER) {
				// (a pick on the wheel: it spends their patience)
				Conversations.picked(player, villager);
			}
			ServerPlayNetworking.send(player, new DialogueCue(villager.getId(), speaker));
		}
	}

	/** {@code player} opened {@code villager}'s talk menu: the talk (and its hold) begins. */
	public static void begin(ServerPlayer player, Entity villager) {
		if (villager != null && ServerPlayNetworking.canSend(player, TYPE)) {
			Conversations.begin(player, villager, false);
		}
	}

	/** The talk with {@code villager} was broken off here: end {@code player}'s cutscene. */
	static void end(ServerPlayer player, Entity villager) {
		end(player, villager.getId());
	}

	static void end(ServerPlayer player, int villagerId) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, new DialogueCue(villagerId, null));
		}
	}

	/** {@code villager} has said their last word in the talk: {@code player}'s cutscene shows it, then ends. */
	static void last(ServerPlayer player, Entity villager) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, new DialogueCue(villager.getId(), Speaker.LAST));
		}
	}
}
