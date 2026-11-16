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

	/** The one talking right now. */
	public enum Speaker {
		PLAYER, VILLAGER
	}

	private DialogueCue() {
		this(-1, null);
	}

	@Override
	public Type<DialogueCue> type() {
		return TYPE;
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** Tell {@code player}'s client that {@code villager}'s turn is {@code speaker}. */
	public static void cue(ServerPlayer player, Entity villager, Speaker speaker) {
		if (villager != null && ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, new DialogueCue(villager.getId(), speaker));
		}
	}
}
