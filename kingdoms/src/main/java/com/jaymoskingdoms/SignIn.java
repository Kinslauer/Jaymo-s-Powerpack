package com.jaymoskingdoms;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Andy, 2026-11-15: friends on offline (non-premium) accounts may join the server. The server keeps online-mode on and runs
 * EasyAuth (MIT, NikitaCartes; on the server only): a premium account is checked with Mojang as ever and walks straight
 * in, while an offline one has to give the password kept with its name first. Until it has, EasyAuth drops every mod
 * packet its client sends, so a newcomer's character creation (the life story, MCA's creator, heritage and class) would
 * hang behind screens that can't be shut. It waits here instead: the client is shown the sign-in screen
 * ({@code client/SignInScreen}, which sends EasyAuth's own login or register command), and creation starts once EasyAuth
 * lets them in ({@link #tick}). Without EasyAuth everyone counts as signed in and nothing changes.
 */
public final class SignIn {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("easyauth");
	/** How often players waiting to sign in are checked (ticks). */
	static final int CHECK_EVERY = 5;
	/** {@link Prompt#state}: signed in (take the screen down), give your password, choose a password. */
	public static final int DONE = 0, LOGIN = 1, REGISTER = 2;

	/** Players who joined without signing in yet, whose character creation waits. */
	private static final Set<UUID> WAITING = new HashSet<>();
	/** EasyAuth's {@code PlayerAuth} methods on a player, looked up on first use (null once they can't be). */
	private static @Nullable Method authenticated, entry;
	private static boolean lookedUp;

	/** Server → player: show the sign-in screen for {@code state}, or take it down ({@link #DONE}). */
	public record Prompt(int state) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Prompt> TYPE = new CustomPacketPayload.Type<>(JaymosKingdoms.id("sign_in"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Prompt> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Prompt::state, Prompt::new);

		@Override
		public CustomPacketPayload.Type<Prompt> type() {
			return TYPE;
		}
	}

	private SignIn() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Prompt.TYPE, Prompt.CODEC);
		if (!LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(SignIn::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WAITING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WAITING.clear());
	}

	/** Whether {@code player} may play: always without EasyAuth, else once it has let them in. */
	public static boolean signedIn(ServerPlayer player) {
		if (!LOADED) {
			return true;
		}
		lookUp();
		if (authenticated == null) {
			return true;
		}
		try {
			return (Boolean) authenticated.invoke(player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true;
		}
	}

	/**
	 * On joining: true (and the sign-in screen shown) if {@code player} must sign in before their character creation
	 * starts; it starts from {@link #tick} once they have.
	 */
	static boolean waitFor(ServerPlayer player) {
		if (signedIn(player)) {
			return false;
		}
		WAITING.add(player.getUUID());
		if (ServerPlayNetworking.canSend(player, Prompt.TYPE)) {
			ServerPlayNetworking.send(player, new Prompt(registered(player) ? LOGIN : REGISTER));
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (WAITING.isEmpty() || server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (UUID id : Set.copyOf(WAITING)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				WAITING.remove(id);
				continue;
			}
			if (!signedIn(player)) {
				continue;
			}
			WAITING.remove(id);
			if (ServerPlayNetworking.canSend(player, Prompt.TYPE)) {
				ServerPlayNetworking.send(player, new Prompt(DONE));
			}
			// what joining would have started: MCA's creator (held back by McaJoinDestinyMixin) and the life story
			if (McaCompat.destinyPending(player)) {
				McaCompat.launchDestiny(player);
			}
			if (LifeStory.owed(player) || !McaCompat.destinyPending(player)) {
				Classes.next(player);
			}
		}
	}

	/** Whether a password is kept for {@code player}'s name already (else they choose one). */
	private static boolean registered(ServerPlayer player) {
		if (entry == null) {
			return true;
		}
		try {
			Object e = entry.invoke(player);
			if (e == null) {
				return false;
			}
			Object password = e.getClass().getField("password").get(e);
			return password instanceof String s && !s.isEmpty();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true;
		}
	}

	private static void lookUp() {
		if (lookedUp) {
			return;
		}
		lookedUp = true;
		try {
			Class<?> auth = Class.forName("xyz.nikitacartes.easyauth.interfaces.PlayerAuth");
			authenticated = auth.getMethod("easyAuth$isAuthenticated");
			entry = auth.getMethod("easyAuth$getPlayerEntryV1");
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("EasyAuth is here but its sign-in state can't be read; character creation won't wait for it", e);
			authenticated = null;
			entry = null;
		}
	}
}
