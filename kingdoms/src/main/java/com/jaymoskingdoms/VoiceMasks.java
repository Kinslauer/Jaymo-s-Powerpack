package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * <li>{@link Mask#TITAN}: Andy: "a transformed great ape, and every xeno form, gets a titan voice changer turned on": in
 * any of the Xeno-Band's alien forms, the voice changer's own Titan voice (forced, like the ape's).</li>
 * <li>{@link Mask#GOBLIN}: in a goblin {@link Disguise}, a goblin's reedy voice.</li>
 * <li>{@link Mask#MASKED}: behind a full mask (a costume's, a bandit's; {@link Identities}), a voice nobody knows. A hood
 * leaves the voice alone, as it leaves the face known.</li>
 * <li>{@link Mask#GHOST}: Andy: "the voice changer automatically makes you sound like a ghost if you speak otherwise it
 * is muted". Out of their body, left for dead (Powers' limbo), a player's voice is a ghost's: thin, hollow and far off
 * (forced). It comes from where the ghost is, not the body ({@link VoiceRange}), and a ghost whose client has no voice
 * changer to turn it ({@link #changer}) isn't heard at all.</li>
 * </ul>
 * The disguises follow the player's own on/off switch for the voice changer; the curses and the titans don't.
 * <p>The ape and the xeno forms speak with a titan's voice ({@link #titan}), and how much taller than a man each player
 * stands is kept too ({@link #grown}): Simple Voice Chat's plugin ({@link VoiceRange}) reads both from its own thread to
 * carry a titan's voice further and to let a giant hear the ground.
 */
public final class VoiceMasks {
	/** How often each player's mask is looked at (ticks). */
	static final int CHECK_TICKS = 10;

	/** Saved by ordinal in the packet only, so the order may change between versions (both sides ship together). */
	public enum Mask { NONE, BEAST, APE, GOBLIN, MASKED, TITAN, GHOST }

	/** A man's height (blocks): anyone taller counts as grown by the difference. */
	static final float MAN_HEIGHT = 1.8F;

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

	/** Player → server, once on joining: their client has the voice changer, so a voice we ask it to turn is turned. */
	public record Changer() implements CustomPacketPayload {
		public static final Changer INSTANCE = new Changer();
		public static final Type<Changer> TYPE = new Type<>(JaymosKingdoms.id("voice_changer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Changer> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Changer> type() {
			return TYPE;
		}
	}

	/** Who has the voice changer, and where each ghost out of its body is as of the last check; for voice chat's thread too. */
	private static final Set<UUID> CHANGERS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, net.minecraft.world.phys.Vec3> GHOSTS = new ConcurrentHashMap<>();

	/** The mask each player was last sent. */
	private static final Map<UUID, Mask> SENT = new HashMap<>();
	/** As of the last check, for voice chat's own thread: who speaks with a titan's voice, and how far past a man's height each tall player stands. */
	private static final Set<UUID> TITANS = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Float> GROWN = new ConcurrentHashMap<>();

	private VoiceMasks() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Changer.TYPE, Changer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Changer.TYPE, (payload, context) -> CHANGERS.add(context.player().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(VoiceMasks::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SENT.remove(handler.player.getUUID());
			TITANS.remove(handler.player.getUUID());
			GROWN.remove(handler.player.getUUID());
			CHANGERS.remove(handler.player.getUUID());
			GHOSTS.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SENT.clear();
			TITANS.clear();
			GROWN.clear();
			CHANGERS.clear();
			GHOSTS.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Mask mask = of(player);
			if (mask == Mask.APE || mask == Mask.TITAN) {
				TITANS.add(player.getUUID());
			} else {
				TITANS.remove(player.getUUID());
			}
			net.minecraft.world.phys.Vec3 ghost = mask == Mask.GHOST ? PowersCompat.limboGhost(player.getUUID()) : null;
			if (ghost != null) {
				GHOSTS.put(player.getUUID(), ghost);
			} else {
				GHOSTS.remove(player.getUUID());
			}
			float grown = player.getBbHeight() - MAN_HEIGHT;
			if (grown > 0.0F) {
				GROWN.put(player.getUUID(), grown);
			} else {
				GROWN.remove(player.getUUID());
			}
			if (SENT.getOrDefault(player.getUUID(), Mask.NONE) != mask && ServerPlayNetworking.canSend(player, Sync.TYPE)) {
				ServerPlayNetworking.send(player, new Sync(mask.ordinal()));
				SENT.put(player.getUUID(), mask);
			}
		}
	}

	/** What {@code player}'s voice is to sound like now. */
	static Mask of(ServerPlayer player) {
		// (out of the body nothing of the body speaks: not the beast, not the mask)
		if (PowersCompat.limboGhost(player.getUUID()) != null) {
			return Mask.GHOST;
		}
		if (WerewolfPlayers.beastUp(player)) {
			return Mask.BEAST;
		}
		if (PowersCompat.greatApe(player)) {
			return Mask.APE;
		}
		if (PowersCompat.xenoForm(player)) {
			return Mask.TITAN;
		}
		if (Disguise.wearsDisguise(player)) {
			return Mask.GOBLIN;
		}
		if (Identities.masked(player)) {
			return Mask.MASKED;
		}
		return Mask.NONE;
	}

	/** Whether {@code player} speaks with a titan's voice (the Great Ape, a xeno form), as of the last check. Any thread. */
	static boolean titan(UUID player) {
		return TITANS.contains(player);
	}

	/** Whether {@code player}'s client said it has the voice changer. Any thread. */
	static boolean changer(UUID player) {
		return CHANGERS.contains(player);
	}

	/** The players out of their bodies and where each one's ghost is, as of the last check. Any thread; not to be changed. */
	static Map<UUID, net.minecraft.world.phys.Vec3> ghosts() {
		return GHOSTS;
	}

	/** How far past a man's height {@code player} stands (blocks; 0 for anyone a man's size or less), as of the last check. Any thread. */
	static float grown(UUID player) {
		return GROWN.getOrDefault(player, 0.0F);
	}
}
