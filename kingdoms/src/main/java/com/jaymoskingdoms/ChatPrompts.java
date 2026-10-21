package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (0.7.109) MCA's villager chat AI (off unless an op switches it on: {@code /mca chatAI default}) knows a villager's
 * name, personality, family and village. This adds what Kingdoms knows: the realm they live in, its ruler and his
 * temper, its wars, how the realm takes the player (reputation, rank, wanted, deeds, renown), the tasks the player is
 * doing for them, their friends and foes among the neighbours, and everything they remember of the player
 * ({@link Recollections}). Written in plain English, since it's for the AI, not the screen.
 */
public final class ChatPrompts {
	/** Friends and foes among the neighbours they mention, at most. */
	static final int NEIGHBOURS = 2;

	private ChatPrompts() {
	}

	/** (mixin on MCA's ChatAIContext) What goes on the end of {@code villager}'s prompt when {@code player} talks to them. */
	public static void append(StringBuilder sb, ServerPlayer player, Villager villager) {
		try {
			String realm = realm(player, villager);
			if (!realm.isEmpty()) {
				sb.append("Your realm: ").append(realm).append('\n');
			}
			String neighbours = neighbours(villager);
			if (!neighbours.isEmpty()) {
				sb.append("Your neighbours: ").append(neighbours).append('\n');
			}
			List<String> memories = Recollections.promptLines(villager, player);
			String name = Recollections.nameTo(player, villager).getString();
			if (memories.isEmpty()) {
				sb.append("You have no memories of dealings with ").append(name).append(" yet.\n");
			} else {
				sb.append("What you remember of ").append(name).append(":\n");
				for (String m : memories) {
					sb.append("  * ").append(m).append('\n');
				}
				int lean = Recollections.leaning(villager, player);
				sb.append(lean <= Recollections.REFUSE ? "You can hardly bear to speak to them.\n"
					: lean <= Recollections.COLD ? "You don't trust them.\n" : lean >= Recollections.WARM ? "You think well of them.\n" : "");
			}
			sb.append("Bring these things up when they fit the conversation. Don't invent any other history with ").append(name)
				.append(". Keep your replies short, like spoken words.\n");
		} catch (RuntimeException e) {
			// (never break MCA's chat over it)
			JaymosKingdoms.LOGGER.warn("Couldn't add Kingdoms context to a villager chat prompt", e);
		}
	}

	private static String realm(ServerPlayer player, Villager villager) {
		MinecraftServer server = player.level().getServer();
		if (server == null || villager.level() != Factions.level(server)) {
			return "";
		}
		Faction f = Factions.around(server, villager.level(), villager.blockPosition(), Villages.VILLAGE_RADIUS * 2.0);
		if (f == null || f.fallen()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		String kind = f.type() == FactionType.KINGDOM ? "the kingdom" : f.type() == FactionType.FIEFDOM ? "the fiefdom" : f.type() == FactionType.VILLAGE
			? "the free village" : "the camp";
		sb.append("You live in ").append(kind).append(" of ").append(f.name()).append('.');
		if (f.type().isRealm()) {
			String ruler = ruler(server, f);
			Tempers.Temper t = Tempers.of(f);
			sb.append(" It is ruled by ").append(ruler);
			if (t != null) {
				sb.append(", who is ").append(Tempers.describe(t, f).getString());
			}
			sb.append('.');
		}
		List<String> wars = new ArrayList<>();
		for (Faction e : Relations.enemies(server, f)) {
			if (e.type() != FactionType.BARBARIAN) {
				wars.add(e.name());
			}
		}
		if (!wars.isEmpty()) {
			sb.append(" It is at war with ").append(String.join(", ", wars)).append('.');
		}
		boolean masked = Identities.hidden(player, f.id());
		String name = Recollections.nameTo(player, villager).getString();
		int rep = Reputation.get(player, f.id());
		sb.append(" Your people think of ").append(name).append(" as ").append(Reputation.word(rep)).append('.');
		if (masked) {
			sb.append(' ').append(name).append(" wears a costume and a mask: you don't know who they really are.");
		}
		Faction.Member member = masked ? null : f.member(player.getUUID());
		if (member != null) {
			sb.append(' ').append(name).append(" is sworn to your realm, with the rank of ").append(Rank.forStanding(member.standing()).name().toLowerCase(java.util.Locale.ROOT))
				.append('.');
		} else if (!masked) {
			Faction theirs = Factions.of(server, player.getUUID());
			if (theirs != null && theirs.id() != f.id()) {
				sb.append(' ').append(name).append(" is sworn to ").append(theirs.name()).append(" (").append(Relations.between(f, theirs).name().toLowerCase(java.util.Locale.ROOT))
					.append(" to yours).");
			}
		}
		if (Theft.wanted(player, f.id())) {
			sb.append(" The guards are hunting ").append(name).append(" for a crime.");
		}
		Deeds.Deed d = masked ? null : Deeds.known(player, f.id(), Deeds.today(server));
		if (d != null) {
			sb.append(" Word is ").append(name).append(" put out ").append(d.fires()).append(" fires, saved ").append(d.saves()).append(" lives and brought ")
				.append(d.rescues()).append(" captives home").append(d.heardFrom() >= 0 ? " elsewhere" : " here").append('.');
		}
		Skills.Pillar pillar = masked ? null : Renown.heard(player, f.id());
		if (pillar != null) {
			sb.append(" Folk say ").append(name).append(" is known for ").append(switch (pillar) {
				case SURVIVAL -> "living off the land";
				case STEALTH -> "stealth and thievery";
				case STRENGTH -> "strength at arms";
			}).append('.');
		}
		for (Quest q : Quests.log(player)) {
			if (q.giver().uuid().equals(villager.getUUID())) {
				sb.append(' ').append(name).append(" is doing a task for you: ").append(Quests.title(q).getString()).append('.');
			}
		}
		if (!Languages.understands(player, villager)) {
			sb.append(' ').append(name).append(" is a foreigner who barely speaks your tongue: use very few, simple words.");
		}
		return sb.toString();
	}

	private static String ruler(MinecraftServer server, Faction f) {
		if (f.leader().isPresent() && Factions.level(server).getEntity(f.leader().get()) instanceof Folk king) {
			return king.getDisplayName().getString();
		}
		return "its ruler";
	}

	private static String neighbours(Villager villager) {
		Map<UUID, Integer> regard = villager.getAttachedOrElse(Neighbours.REGARD, Map.of());
		if (regard.isEmpty() || !(villager.level() instanceof ServerLevel level)) {
			return "";
		}
		List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(regard.entrySet());
		sorted.sort(Map.Entry.comparingByValue());
		List<String> out = new ArrayList<>();
		int foes = 0, friends = 0;
		for (Map.Entry<UUID, Integer> e : sorted) {
			if (e.getValue() > Neighbours.DISLIKE || foes >= NEIGHBOURS) {
				break;
			}
			Entity n = level.getEntity(e.getKey());
			if (n != null) {
				out.add("you " + (e.getValue() <= Neighbours.HATE ? "hate " : "dislike ") + n.getName().getString());
				foes++;
			}
		}
		for (int i = sorted.size() - 1; i >= 0 && friends < NEIGHBOURS; i--) {
			Map.Entry<UUID, Integer> e = sorted.get(i);
			if (e.getValue() < Neighbours.LIKE) {
				break;
			}
			Entity n = level.getEntity(e.getKey());
			if (n != null) {
				out.add("you " + (e.getValue() >= Neighbours.LOVE ? "love " : "like ") + n.getName().getString());
				friends++;
			}
		}
		return String.join("; ", out);
	}
}
