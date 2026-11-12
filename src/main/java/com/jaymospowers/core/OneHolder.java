package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * One holder per power, an option for a world, off by default. While it is on, a power that a player already holds
 * (online or not) cannot awaken in, or be handed to, another: every way in ({@link Powers#set}, {@link Powers#awaken},
 * {@link Powers#awakenAs}, the sealed phial) refuses quietly with an oblique line, and the power's own item does
 * nothing for the second hand. The holder keeps it until they die for good ({@link Powers#set} to null at the end of a
 * life), give it up (the Draught of Release), or an operator frees it ({@code /power oneholder free <power>}).
 * <p>
 * Who holds what is recorded as powers change hands and when a player joins, whether the option is on or not, in a
 * world attachment ({@link #STATE}); a holder who last logged in before this existed is learned on their next join.
 * <p>
 * Exempt: the Z Fighter lineages (a heritage you are born to, not a power that is handed out) and Xenoform (the band
 * is already a single object in the world, passed by taking it). Operators' own {@code /power set} and the powers screen
 * ignore the option ({@link #bypassed}).
 * <p>
 * {@code /power oneholder on|off|status} and {@code free <power>}, gamemasters only.
 */
public final class OneHolder {
	/** How often the same refusal is shown to the same player at most (ticks), so a held-down use does not flood the bar. */
	static final int SAY_EVERY = 200;

	/** The world's option and who holds what: holder id → power id, holder id → last known name, and holders freed while offline (id → power). */
	public record State(boolean on, Map<String, String> holders, Map<String, String> names, Map<String, String> freed) {
		public static final State OFF = new State(false, Map.of(), Map.of(), Map.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("on", false).forGetter(State::on),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("holders", Map.of()).forGetter(State::holders),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("names", Map.of()).forGetter(State::names),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("freed", Map.of()).forGetter(State::freed))
			.apply(i, State::new));
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosPowers.id("one_holder"), builder -> builder.persistent(State.CODEC));

	private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.translatable("commands.jaymospowers.unknown"));
	private static final Map<java.util.UUID, Long> SAID = new java.util.HashMap<>();
	private static boolean bypass;

	private OneHolder() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SAID.remove(handler.player.getUUID()));
	}

	/** A player joins: a power an operator freed while they were away is taken now; otherwise what they hold is recorded. */
	private static void joined(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		State state = state(server);
		String id = player.getUUID().toString();
		Power power = Powers.of(player);
		String freed = state.freed().get(id);
		if (freed != null) {
			Map<String, String> rest = new LinkedHashMap<>(state.freed());
			rest.remove(id);
			server.overworld().setAttached(STATE, new State(state.on(), state.holders(), state.names(), Map.copyOf(rest)));
			if (power != null && power.id().toString().equals(freed)) {
				bypassed(() -> Powers.set(player, null));
				return;
			}
		}
		record(player, power);
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(STATE, State.OFF);
	}

	/** Whether the option is on in {@code server}'s world. */
	public static boolean on(MinecraftServer server) {
		return state(server).on();
	}

	/** Runs {@code action} with the option ignored (an operator's own hand). */
	public static void bypassed(Runnable action) {
		boolean was = bypass;
		bypass = true;
		try {
			action.run();
		} finally {
			bypass = was;
		}
	}

	/** Whether {@code power} is not covered by the option (a heritage, or already a single object in the world). */
	static boolean exempt(Power power) {
		return power instanceof com.jaymospowers.power.zfighter.ZFighterPower || power instanceof com.jaymospowers.power.xenoform.XenoformPower;
	}

	/** The id of the player who holds {@code power} and is not {@code player}, or null (also null with the option off). */
	private static @Nullable String heldByOther(ServerPlayer player, Power power) {
		if (bypass || exempt(power)) {
			return null;
		}
		State state = state(player.level().getServer());
		if (!state.on()) {
			return null;
		}
		String me = player.getUUID().toString();
		for (Map.Entry<String, String> e : state.holders().entrySet()) {
			if (e.getValue().equals(power.id().toString()) && !e.getKey().equals(me)) {
				return e.getKey();
			}
		}
		return null;
	}

	/** Whether {@code power} may not come to {@code player} now (someone else holds it), without saying anything. */
	public static boolean held(ServerPlayer player, Power power) {
		return heldByOther(player, power) != null;
	}

	/** {@link #held}, and if so a line to {@code player}, an oblique one (at most once in a while). */
	public static boolean refuses(ServerPlayer player, Power power) {
		if (!held(player, power)) {
			return false;
		}
		long now = player.level().getGameTime();
		Long last = SAID.get(player.getUUID());
		if (last == null || now - last >= SAY_EVERY) {
			SAID.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.oneholder.passed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		return true;
	}

	/** The powers fate may still give {@code player} (those not held by another, with the option on). */
	public static List<Power> open(ServerPlayer player, List<Power> choices) {
		return choices.stream().filter(p -> !held(player, p)).toList();
	}

	/** {@code player} now holds {@code power} (or nothing, for null): kept in step as powers change hands. */
	static void record(ServerPlayer player, @Nullable Power power) {
		MinecraftServer server = player.level().getServer();
		State state = state(server);
		String id = player.getUUID().toString();
		String now = power == null ? null : power.id().toString();
		String was = state.holders().get(id);
		String name = player.getGameProfile().name();
		if ((now == null ? was == null : now.equals(was)) && name.equals(state.names().get(id))) {
			return;
		}
		Map<String, String> holders = new LinkedHashMap<>(state.holders());
		Map<String, String> names = new LinkedHashMap<>(state.names());
		if (now == null) {
			holders.remove(id);
		} else {
			holders.put(id, now);
			names.put(id, name);
		}
		server.overworld().setAttached(STATE, new State(state.on(), Map.copyOf(holders), Map.copyOf(names), state.freed()));
	}

	private static String nameOf(State state, String id) {
		return state.names().getOrDefault(id, id);
	}

	/** The {@code oneholder} branch of {@code /power}. */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("oneholder")
			.then(Commands.literal("on").executes(c -> {
				toggle(c.getSource().getServer(), true);
				c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.oneholder.on"), true);
				return 1;
			}))
			.then(Commands.literal("off").executes(c -> {
				toggle(c.getSource().getServer(), false);
				c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.oneholder.off"), true);
				return 1;
			}))
			.then(Commands.literal("status").executes(c -> status(c.getSource())))
			.then(Commands.literal("free")
				.then(Commands.argument("power", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Powers.all().stream().map(p -> p.id().getPath()), b))
					.executes(c -> {
						Power power = Powers.byName(StringArgumentType.getString(c, "power"));
						if (power == null) {
							throw UNKNOWN.create();
						}
						int freed = free(c.getSource().getServer(), power);
						c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.oneholder.free", power.name(), freed), true);
						return freed;
					})));
	}

	private static void toggle(MinecraftServer server, boolean on) {
		State state = state(server);
		server.overworld().setAttached(STATE, new State(on, state.holders(), state.names(), state.freed()));
		if (on) {
			// whoever is on now is known at once
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				record(player, Powers.of(player));
			}
		}
	}

	/**
	 * Takes {@code power} from whoever holds it (and drops the record of them): an online holder loses it now; an
	 * offline one loses it when they next join ({@link State#freed}). Returns how many holders were freed.
	 */
	private static int free(MinecraftServer server, Power power) {
		State state = state(server);
		Map<String, String> holders = new LinkedHashMap<>(state.holders());
		Map<String, String> later = new LinkedHashMap<>(state.freed());
		int freed = 0;
		for (Map.Entry<String, String> e : List.copyOf(holders.entrySet())) {
			if (!e.getValue().equals(power.id().toString())) {
				continue;
			}
			holders.remove(e.getKey());
			freed++;
			ServerPlayer online = server.getPlayerList().getPlayer(java.util.UUID.fromString(e.getKey()));
			if (online != null) {
				bypassed(() -> Powers.set(online, null));
			} else {
				later.put(e.getKey(), power.id().toString());
			}
		}
		server.overworld().setAttached(STATE, new State(state.on(), Map.copyOf(holders), state.names(), Map.copyOf(later)));
		return freed;
	}

	private static int status(CommandSourceStack source) {
		State state = state(source.getServer());
		source.sendSuccess(() -> Component.translatable(state.on() ? "commands.jaymospowers.oneholder.status.on" : "commands.jaymospowers.oneholder.status.off"), false);
		if (state.holders().isEmpty()) {
			source.sendSuccess(() -> Component.translatable("commands.jaymospowers.oneholder.status.none"), false);
		}
		for (Map.Entry<String, String> e : state.holders().entrySet()) {
			Power power = Powers.get(Identifier.tryParse(e.getValue()));
			Component name = power == null ? Component.literal(e.getValue()) : power.name();
			boolean online = source.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(e.getKey())) != null;
			source.sendSuccess(() -> Component.translatable(online ? "commands.jaymospowers.oneholder.status.line.online" : "commands.jaymospowers.oneholder.status.line",
				name, nameOf(state, e.getKey())), false);
		}
		return state.holders().size();
	}
}
