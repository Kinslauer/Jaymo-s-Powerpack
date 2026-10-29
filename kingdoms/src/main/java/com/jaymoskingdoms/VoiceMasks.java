package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.330) What a player's voice turns into, for Simple Voice Voice Changer (shipped in the pack with Simple Voice Chat;
 * the client applies it, {@code client/VoiceChangerCompat}, only with that mod loaded). Andy (2026-10-29) asked for
 * proximity chat with the voice changer; its own studio stays the player's, and these sit on top while they last:
 * <ul>
 * <li>{@link Mask#BEAST}: a werewolf in beast form growls its words (forced: a curse isn't something you switch off).</li>
 * <li>{@link Mask#APE}: a Great Ape booms (forced too).</li>
 * <li>{@link Mask#GOBLIN}: in a goblin {@link Disguise}, a goblin's reedy voice.</li>
 * <li>{@link Mask#MASKED}: behind a full mask (a costume's, a bandit's; {@link Identities}), a voice nobody knows. A hood
 * leaves the voice alone, as it leaves the face known.</li>
 * </ul>
 * The disguises follow the player's own on/off switch for the voice changer; the curses don't.
 */
public final class VoiceMasks {
	/** How often each player's mask is looked at (ticks). */
	static final int CHECK_TICKS = 10;

	/** Saved by ordinal in the packet only, so the order may change between versions (both sides ship together). */
	public enum Mask { NONE, BEAST, APE, GOBLIN, MASKED }

	/** Server → player: the voice their own voice is to be turned into now. */
	public record Sync(int mask) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("voice_mask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.mask), buf -> new Sync(buf.readVarInt()));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** The mask each player was last sent. */
	private static final Map<UUID, Mask> SENT = new HashMap<>();

	private VoiceMasks() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(VoiceMasks::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Mask mask = of(player);
			if (SENT.getOrDefault(player.getUUID(), Mask.NONE) != mask && ServerPlayNetworking.canSend(player, Sync.TYPE)) {
				ServerPlayNetworking.send(player, new Sync(mask.ordinal()));
				SENT.put(player.getUUID(), mask);
			}
		}
	}

	/** What {@code player}'s voice is to sound like now. */
	static Mask of(ServerPlayer player) {
		if (WerewolfPlayers.beastUp(player)) {
			return Mask.BEAST;
		}
		if (PowersCompat.greatApe(player)) {
			return Mask.APE;
		}
		if (Disguise.wearsDisguise(player)) {
			return Mask.GOBLIN;
		}
		if (Identities.masked(player)) {
			return Mask.MASKED;
		}
		return Mask.NONE;
	}
}
