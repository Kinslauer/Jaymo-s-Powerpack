package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raid;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.6) Bringing a raid: walk into a kingdom, fiefdom or village carrying Bad Omen (or the Raid Omen it turns into)
 * and its people see it on you. Whatever good name you had there is gone and it falls {@link #OMEN_LOSS} further, at
 * once. If the raid you brought comes and the settlement survives it, it bears a grudge ({@link #GRUDGES}, saved): your
 * reputation there only ever recovers at a tenth of the pace ({@link #GRUDGE_GAIN}) and never climbs back over
 * {@link #GRUDGE_CEILING} (so they'll never take your pledge again), and each day there's only a
 * {@link #FADE_CHANCE} chance it's finally forgiven. If the raid wipes it out, there's no one left to remember.
 */
public final class Omens {
	/** How near a settlement's seat (or inside its land) the omen is seen. */
	static final double SEEN_RANGE = 96.0;
	/** The reputation lost on top of every good word, the moment the omen is seen. */
	static final int OMEN_LOSS = 400;
	/** How long after the omen is seen the raid it brings is looked for (ticks). */
	static final long RAID_WAIT_TICKS = 6000L;
	/** A grudge: gains kept (times), the most it lets reputation climb to, and the daily chance it's forgiven. */
	static final float GRUDGE_GAIN = 0.1F;
	static final int GRUDGE_CEILING = -50;
	static final float FADE_CHANCE = 0.01F;
	/** The omen's seen once per settlement per this long (ticks; a day). */
	static final long SEEN_AGAIN_TICKS = 24000L;

	/** Settlement id → the players it holds a grudge against. Saved on the Overworld. */
	static final AttachmentType<Map<String, List<UUID>>> GRUDGES = AttachmentRegistry.create(JaymosKingdoms.id("grudges"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC.listOf())));

	/** A raid someone brought: who, on which settlement, when it was seen, and the raid once it's begun. */
	private static final class Brought {
		final UUID player;
		final int faction;
		final BlockPos seat;
		final long seen;
		@Nullable Raid raid;

		Brought(UUID player, int faction, BlockPos seat, long seen) {
			this.player = player;
			this.faction = faction;
			this.seat = seat;
			this.seen = seen;
		}
	}

	private static final List<Brought> BROUGHT = new ArrayList<>();
	/** Player → settlement → when the omen was last seen on them there (not saved). */
	private static final Map<UUID, Map<Integer, Long>> SEEN = new HashMap<>();
	private static long lastDay = Long.MIN_VALUE;

	private Omens() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Omens::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BROUGHT.clear();
			SEEN.clear();
			lastDay = Long.MIN_VALUE;
		});
	}

	/** Whether {@code faction} holds a grudge against {@code player}. */
	public static boolean grudge(MinecraftServer server, int faction, UUID player) {
		Map<String, List<UUID>> all = server.overworld().getAttached(GRUDGES);
		List<UUID> list = all == null ? null : all.get(Integer.toString(faction));
		return list != null && list.contains(player);
	}

	/** (Reputation.change) A gain, as a grudge lets it through: a tenth, rounded by chance. */
	static int gain(ServerPlayer player, Faction kingdom, int by) {
		if (by <= 0 || !grudge(player.level().getServer(), kingdom.id(), player.getUUID())) {
			return by;
		}
		float kept = by * GRUDGE_GAIN;
		int whole = (int)kept;
		return whole + (player.getRandom().nextFloat() < kept - whole ? 1 : 0);
	}

	/** (Reputation.change) The most a grudge lets reputation climb to. */
	static int ceiling(ServerPlayer player, Faction kingdom) {
		return grudge(player.level().getServer(), kingdom.id(), player.getUUID()) ? GRUDGE_CEILING : Reputation.MAX;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (now % 20 != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()
				|| !(player.hasEffect(MobEffects.BAD_OMEN) || player.hasEffect(MobEffects.RAID_OMEN))) {
				continue;
			}
			Faction f = Factions.around(server, level, player.blockPosition(), SEEN_RANGE);
			if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
				continue;
			}
			Map<Integer, Long> seen = SEEN.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			Long last = seen.get(f.id());
			if (last != null && now - last < SEEN_AGAIN_TICKS) {
				continue;
			}
			seen.put(f.id(), now);
			seenWith(player, f, now);
		}
		follow(level, now);
		forgive(level);
	}

	/** {@code f}'s people see the omen on {@code player}. */
	private static void seenWith(ServerPlayer player, Faction f, long now) {
		Reputation.wipe(player, f);
		Reputation.change(player, f, -OMEN_LOSS);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.omen.seen", f.name()).withStyle(ChatFormatting.DARK_RED));
		BROUGHT.add(new Brought(player.getUUID(), f.id(), f.seat(), now));
		JaymosKingdoms.LOGGER.info("{} brought a bad omen into {}", player.getName().getString(), f.name());
	}

	/** The raids brought, watched: one the settlement survives leaves a grudge. */
	private static void follow(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		for (Iterator<Brought> it = BROUGHT.iterator(); it.hasNext();) {
			Brought b = it.next();
			if (b.raid == null) {
				ServerPlayer player = server.getPlayerList().getPlayer(b.player);
				Raid raid = level.getRaidAt(b.seat);
				if (raid == null && player != null && player.level() == level) {
					raid = level.getRaidAt(player.blockPosition());
				}
				if (raid != null) {
					b.raid = raid;
				} else if (now - b.seen > RAID_WAIT_TICKS) {
					it.remove();
				}
				continue;
			}
			if (b.raid.isVictory()) {
				it.remove();
				Faction f = Factions.get(server, b.faction);
				if (f != null && !f.fallen()) {
					hold(level, f, b.player);
				}
			} else if (b.raid.isLoss() || b.raid.isStopped()) {
				it.remove();
			}
		}
	}

	/** {@code f} survived the raid {@code player} brought, and won't forget it. */
	private static void hold(ServerLevel level, Faction f, UUID player) {
		Map<String, List<UUID>> all = new HashMap<>(level.getAttachedOrElse(GRUDGES, Map.of()));
		List<UUID> list = new ArrayList<>(all.getOrDefault(Integer.toString(f.id()), List.of()));
		if (list.contains(player)) {
			return;
		}
		list.add(player);
		all.put(Integer.toString(f.id()), List.copyOf(list));
		level.setAttached(GRUDGES, Map.copyOf(all));
		ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
		if (online != null) {
			// what's left of their name there is held under the grudge's ceiling
			int over = Reputation.get(online, f.id()) - GRUDGE_CEILING;
			if (over > 0) {
				Reputation.change(online, f, -over);
			}
			online.sendSystemMessage(Component.translatable("message.jaymoskingdoms.omen.grudge", f.name()).withStyle(ChatFormatting.DARK_RED));
		}
		JaymosKingdoms.LOGGER.info("{} survived a raid and holds a grudge against {}", f.name(), player);
	}

	/** Once a day, each grudge has a slim chance of being forgiven. */
	private static void forgive(ServerLevel level) {
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (day == lastDay) {
			return;
		}
		boolean first = lastDay == Long.MIN_VALUE;
		lastDay = day;
		Map<String, List<UUID>> all = level.getAttached(GRUDGES);
		if (first || all == null || all.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		Map<String, List<UUID>> kept = new HashMap<>();
		Set<String> changed = new HashSet<>();
		for (Map.Entry<String, List<UUID>> e : all.entrySet()) {
			List<UUID> still = new ArrayList<>();
			for (UUID player : e.getValue()) {
				if (random.nextFloat() < FADE_CHANCE) {
					changed.add(e.getKey());
					forgiven(level, e.getKey(), player);
				} else {
					still.add(player);
				}
			}
			if (!still.isEmpty()) {
				kept.put(e.getKey(), List.copyOf(still));
			}
		}
		if (!changed.isEmpty()) {
			level.setAttached(GRUDGES, Map.copyOf(kept));
		}
	}

	private static void forgiven(ServerLevel level, String faction, UUID player) {
		ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
		Faction f = Factions.get(level.getServer(), Integer.parseInt(faction));
		if (online != null && f != null) {
			online.sendSystemMessage(Component.translatable("message.jaymoskingdoms.omen.forgiven", f.name()).withStyle(ChatFormatting.YELLOW));
		}
	}
}
