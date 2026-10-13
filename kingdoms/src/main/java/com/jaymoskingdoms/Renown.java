package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.51) Your alignment: the pillar of the skill tree ({@link Skills}) you've made your own, once it's at least level
 * {@value #ALIGN_LEVEL} and ahead of the others. It shows over your head to other players (Survivor, Rogue, Warrior;
 * "Master" at the top level), and word of it gets round: a settlement whose people see you comes to know what you are,
 * and each day word may travel on to another. Its people gossip about you, and where you're known for a Rogue they
 * keep a hand on their purses (pockets take longer to pick).
 */
public final class Renown {
	/** A pillar's level needed for it to be your alignment. */
	public static final int ALIGN_LEVEL = 5;
	/** How often a settlement's people may notice you (ticks). */
	static final int NOTICE_TICKS = 100;
	/** Each day, the chance word of you travels from each settlement that knows you to one more. */
	static final float SPREAD_CHANCE = 0.5F;
	/** Where you're known for a Rogue, a pocket takes this much longer to pick. */
	static final float ROGUE_WARY = 1.2F;
	/** The chance a gossip who's heard of you talks about you rather than something else. */
	static final float GOSSIP_CHANCE = 0.5F;

	/** Your alignment as it shows over your head: 0 none, else {@code 1 + pillar * 2 (+1 master)}. Synced to everyone; not saved. */
	public static final AttachmentType<Integer> TITLE = AttachmentRegistry.create(JaymosKingdoms.id("alignment"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** Settlements (by id) that know your alignment → the pillar they know you for. Saved, kept through death. */
	static final AttachmentType<Map<String, String>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("renown"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)).copyOnDeath());

	private static long lastDay = Long.MIN_VALUE;

	private Renown() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Renown::tick);
	}

	/** {@code player}'s alignment, or null: their best pillar if it's high enough and ahead of the rest. */
	public static Skills.@Nullable Pillar alignment(Skills.Progress p) {
		Skills.Pillar best = null;
		boolean tied = false;
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			if (best == null || p.xp(pillar) > p.xp(best)) {
				best = pillar;
				tied = false;
			} else if (p.xp(pillar) == p.xp(best)) {
				tied = true;
			}
		}
		return best == null || tied || p.level(best) < ALIGN_LEVEL ? null : best;
	}

	/** The title code for {@code p} (see {@link #TITLE}). */
	static int title(Skills.Progress p) {
		Skills.Pillar pillar = alignment(p);
		return pillar == null ? 0 : 1 + pillar.ordinal() * 2 + (p.level(pillar) >= Skills.MAX_LEVEL ? 1 : 0);
	}

	/** The title's pillar and whether it's a master's, from its code (null for none). */
	public static Skills.@Nullable Pillar titlePillar(int code) {
		return code <= 0 ? null : Skills.Pillar.values()[(code - 1) / 2];
	}

	public static boolean titleMaster(int code) {
		return code > 0 && (code - 1) % 2 == 1;
	}

	/** Puts {@code player}'s alignment over their head (for everyone who sees them). */
	static void update(ServerPlayer player) {
		int code = title(Skills.of(player));
		Integer now = player.getAttached(TITLE);
		if (code == 0) {
			if (now != null) {
				player.removeAttached(TITLE);
			}
		} else if (now == null || now != code) {
			player.setAttached(TITLE, code);
		}
	}

	/** What settlement {@code factionId}'s people have heard {@code player} is, or null if nothing. */
	static Skills.@Nullable Pillar heard(Player player, int factionId) {
		String key = player.getAttachedOrElse(KNOWN, Map.of()).get(Integer.toString(factionId));
		if (key == null) {
			return null;
		}
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			if (pillar.key().equals(key)) {
				return pillar;
			}
		}
		return null;
	}

	/** {@code player} is known for a Rogue by settlement {@code f}'s people. */
	static boolean knownRogue(Player player, @Nullable Faction f) {
		return f != null && heard(player, f.id()) == Skills.Pillar.STEALTH;
	}

	private static void learn(ServerPlayer player, int factionId, Skills.Pillar pillar) {
		Map<String, String> known = player.getAttachedOrElse(KNOWN, Map.of());
		String id = Integer.toString(factionId);
		if (pillar.key().equals(known.get(id))) {
			return;
		}
		Map<String, String> next = new HashMap<>(known);
		next.put(id, pillar.key());
		player.setAttached(KNOWN, Map.copyOf(next));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		long day = Math.floorDiv(overworld.getOverworldClockTime(), 24000L);
		boolean newDay = lastDay != Long.MIN_VALUE && day != lastDay;
		lastDay = day;
		if (!newDay && now % NOTICE_TICKS != 0L) {
			return;
		}
		RandomSource random = overworld.getRandom();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Skills.Pillar pillar = alignment(Skills.of(player));
			if (pillar != null && now % NOTICE_TICKS == 0L && player.level() instanceof ServerLevel level && !player.isSpectator()) {
				// (a settlement whose people see you comes to know what you are)
				Faction f = Theft.lawAt(level, player.blockPosition());
				if (f != null && !Theft.witnesses(level, f, player).isEmpty()) {
					learn(player, f.id(), pillar);
				}
			}
			if (newDay) {
				spread(server, player, random);
			}
		}
	}

	/** A new day: from each settlement that knows {@code player}, word may travel to one more. */
	private static void spread(MinecraftServer server, ServerPlayer player, RandomSource random) {
		Map<String, String> known = player.getAttachedOrElse(KNOWN, Map.of());
		if (known.isEmpty()) {
			return;
		}
		List<Faction> unaware = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && !known.containsKey(Integer.toString(f.id()))) {
				unaware.add(f);
			}
		}
		Map<String, String> next = new HashMap<>(known);
		for (String pillar : known.values()) {
			if (unaware.isEmpty()) {
				break;
			}
			if (random.nextFloat() < SPREAD_CHANCE) {
				Faction to = unaware.remove(random.nextInt(unaware.size()));
				next.put(Integer.toString(to.id()), pillar);
			}
		}
		if (next.size() != known.size()) {
			player.setAttached(KNOWN, Map.copyOf(next));
		}
	}
}
