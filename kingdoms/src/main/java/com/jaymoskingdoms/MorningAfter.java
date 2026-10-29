package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.332) The morning after. A werewolf player keeps a tally of what the beast kills ({@link #kill}). When a forced night
 * ends and they come back to themselves, their hands are bloody for {@link #BLOODY_TICKS} ({@link Morning}, drawn by
 * {@code WolfScreen}) and an overlay tells them what they did: how many things they killed, and (if any victim was a
 * named villager or a player they knew) that it included someone they knew, without saying who. The names stay hidden
 * until they examine a body they made (look at it and sneak; Jaymo's Kitchen's corpses only, through
 * {@link KitchenCompat}).
 */
public final class MorningAfter {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** How long the hands stay bloody (ticks: ten seconds). */
	static final int BLOODY_TICKS = 200;
	/** The second line of the summary comes this long after the first (ticks). */
	static final int SECOND_LINE_AFTER = 70;
	/** A body can be examined from this far (blocks); it belongs to a victim who died within this of it. */
	static final double EXAMINE_REACH = 4.5, BODY_NEAR = 3.5;
	/** The most victims kept for a night. */
	static final int MAX_VICTIMS = 64;

	/** Server → player: bloody hands for this long (ticks). */
	public record Morning(int ticks) implements CustomPacketPayload {
		public static final Type<Morning> TYPE = new Type<>(JaymosKingdoms.id("werewolf_morning"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Morning> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Morning::ticks, Morning::new);

		@Override
		public Type<Morning> type() {
			return TYPE;
		}
	}

	/** A named victim: who, what they were called, where they fell. */
	private record Victim(UUID id, String name, Vec3 at) {
	}

	/** A night's tally. */
	private static final class Night {
		int kills;
		boolean knew;
		final List<Victim> victims = new ArrayList<>();
	}

	/** An overlay line due at a game time. */
	private record Line(long due, Component text) {
	}

	/** The nights under way; the named victims of the last night (kept to examine); who's already been named to whom; the lines to come. */
	private static final Map<UUID, Night> NIGHTS = new HashMap<>();
	private static final Map<UUID, List<Victim>> LAST = new HashMap<>();
	private static final Map<UUID, Set<UUID>> NAMED = new HashMap<>();
	private static final Map<UUID, Deque<Line>> LINES = new HashMap<>();

	private MorningAfter() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Morning.TYPE, Morning.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(MorningAfter::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			NIGHTS.remove(id);
			LAST.remove(id);
			NAMED.remove(id);
			LINES.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NIGHTS.clear();
			LAST.clear();
			NAMED.clear();
			LINES.clear();
		});
	}

	// ---- the night -----------------------------------------------------------------------------------------------------

	/** The beast tore out of {@code player}: a new tally (and last night's bodies are forgotten). */
	static void began(ServerPlayer player, boolean forced) {
		LAST.remove(player.getUUID());
		NAMED.remove(player.getUUID());
		LINES.remove(player.getUUID());
		if (forced) {
			NIGHTS.put(player.getUUID(), new Night());
		} else {
			NIGHTS.remove(player.getUUID());
		}
	}

	/** The beast of {@code killer} killed {@code victim}. */
	static void kill(ServerPlayer killer, LivingEntity victim) {
		Night night = NIGHTS.get(killer.getUUID());
		if (night == null || victim instanceof ArmorStand) {
			return;
		}
		night.kills++;
		if (Introductions.named(victim)) {
			if (Introductions.knows(killer, victim)) {
				night.knew = true;
			}
			if (night.victims.size() < MAX_VICTIMS) {
				night.victims.add(new Victim(victim.getUUID(), victim.getName().getString(), victim.position()));
			}
		}
	}

	/** {@code player} is themselves again; {@code morning}: after a forced night, at dawn. */
	static void ended(ServerPlayer player, boolean morning) {
		Night night = NIGHTS.remove(player.getUUID());
		if (night == null || !morning || player.connection == null) {
			return;
		}
		long now = player.level().getGameTime();
		Deque<Line> lines = new ArrayDeque<>();
		if (night.kills == 0) {
			lines.add(new Line(now, Component.translatable("message.jaymoskingdoms.feral.morning.clean").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		} else {
			ServerPlayNetworking.send(player, new Morning(BLOODY_TICKS));
			lines.add(new Line(now, night.kills == 1
				? Component.translatable("message.jaymoskingdoms.feral.morning.one").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)
				: Component.translatable("message.jaymoskingdoms.feral.morning.kills", night.kills).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)));
			if (night.knew) {
				lines.add(new Line(now + SECOND_LINE_AFTER,
					Component.translatable("message.jaymoskingdoms.feral.morning.knew").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)));
			}
			if (!night.victims.isEmpty()) {
				LAST.put(player.getUUID(), night.victims);
			}
		}
		LINES.put(player.getUUID(), lines);
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (LINES.isEmpty() && LAST.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Deque<Line> lines = LINES.get(player.getUUID());
			if (lines != null) {
				if (!lines.isEmpty() && now >= lines.peek().due()) {
					player.sendOverlayMessage(lines.poll().text());
				}
				if (lines.isEmpty()) {
					LINES.remove(player.getUUID());
				}
			}
			if ((now + player.getId()) % 10 == 0 && LAST.containsKey(player.getUUID()) && player.isShiftKeyDown() && KitchenCompat.loaded()) {
				examine(player);
			}
		}
	}

	/** Sneaking, looking at a body near where a victim of theirs fell: they learn the name. */
	private static void examine(ServerPlayer player) {
		List<Victim> victims = LAST.get(player.getUUID());
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(EXAMINE_REACH));
		for (Entity body : player.level().getEntities((Entity)null, player.getBoundingBox().inflate(EXAMINE_REACH), KitchenCompat::isCorpse)) {
			Optional<Vec3> hit = body.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isEmpty()) {
				continue;
			}
			Set<UUID> named = NAMED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
			Victim best = null;
			double bestD = BODY_NEAR * BODY_NEAR;
			for (Victim v : victims) {
				double d = v.at().distanceToSqr(body.position());
				if (d < bestD && !named.contains(v.id())) {
					bestD = d;
					best = v;
				}
			}
			if (best != null) {
				named.add(best.id());
				learn(player, best);
				return;
			}
		}
	}

	/** They know {@code victim}'s name from now on. */
	private static void learn(ServerPlayer player, Victim victim) {
		Set<UUID> known = player.getAttached(Introductions.KNOWN);
		if (known == null || !known.contains(victim.id())) {
			// a new set, so it's synced
			Set<UUID> next = known == null ? new HashSet<>() : new HashSet<>(known);
			next.add(victim.id());
			player.setAttached(Introductions.KNOWN, next);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.morning.body", victim.name())
			.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}
}
