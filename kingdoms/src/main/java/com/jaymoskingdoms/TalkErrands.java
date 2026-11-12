package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Town business on a villager's talk screen, so none of it needs a typed command any more. Andy (2026-11-12), on calling
 * a festival, joining a war party, marking plots and sending a courier being commands only: "they all matter and propose
 * screens for all of them, available through talk options".
 * <p>
 * "Town business" ({@link #ANSWER}) in MCA's menu, on a grown villager of a realm or village, opens a talk screen
 * ({@link ZFighterFolk#ask}) of what can be done there: call a festival (a lord or ruler of the realm), march with your
 * realm's war party, mark a plot where you stand, choose what goes up on your plot (a second screen of the buildings),
 * give up your plot, and have something carried (what's in your hand, to whom: a second screen of the players about,
 * by the names they go by). Each does what its old command did, with the same rules and the same words back. Answers
 * that can't be had here are greyed.
 */
public final class TalkErrands {
	public static final String ANSWER = "jaymoskingdoms_errands";
	/** How many buildings, and players, a second screen lists. */
	static final int MOST = 12;

	private TalkErrands() {
	}

	/** Whether {@code v} has town business to talk of: a grown villager of a realm or village. */
	public static boolean canAsk(Villager v) {
		if (v.isBaby() || !(v.level() instanceof ServerLevel level)) {
			return false;
		}
		Faction f = Factions.around(level.getServer(), level, v.blockPosition(), Villages.VILLAGE_RADIUS);
		return f != null && !f.fallen() && f.type() != FactionType.BARBARIAN;
	}

	/** The talk screen: what can be done here. */
	public static void open(ServerPlayer player, LivingEntity speaker) {
		MinecraftServer server = player.level().getServer();
		Faction mine = Factions.of(server, player.getUUID());
		Faction.Member member = mine == null ? null : mine.member(player.getUUID());
		boolean lord = mine != null && mine.type().isRealm()
			&& (Crowns.rules(server, mine, player.getUUID()) || member != null && Rank.forStanding(member.standing()) == Rank.LORD);
		boolean atWar = mine != null && Economy.atWar(server, mine);
		boolean holding = !player.getMainHandItem().isEmpty();
		List<Component> options = List.of(
			Component.translatable("talk.jaymoskingdoms.errands.festival"),
			Component.translatable("talk.jaymoskingdoms.errands.war"),
			Component.translatable("talk.jaymoskingdoms.errands.plot_mark"),
			Component.translatable("talk.jaymoskingdoms.errands.plot_build"),
			Component.translatable("talk.jaymoskingdoms.errands.plot_clear"),
			Component.translatable("talk.jaymoskingdoms.errands.courier"),
			Component.translatable("talk.jaymoskingdoms.errands.leave"));
		List<Boolean> enabled = List.of(lord, atWar, true, true, true, holding, true);
		ZFighterFolk.ask(player, speaker, Component.translatable("talk.jaymoskingdoms.errands.ask"), options, enabled, pick -> {
			switch (pick) {
				case 0 -> run(player, "kingdom festival");
				case 1 -> {
					WarParties.Joined joined = WarParties.join(Factions.level(server), player);
					player.sendSystemMessage(joined.message().copy().withStyle(joined.ok() ? ChatFormatting.GOLD : ChatFormatting.RED));
				}
				case 2 -> Plots.mark(player, player.blockPosition().below(), 0, 0, true);
				case 3 -> buildings(player, speaker);
				case 4 -> Plots.clear(player);
				case 5 -> couriers(player, speaker);
				default -> {
				}
			}
		});
	}

	/** What can go up on your plot, by the faction you're at. */
	private static void buildings(ServerPlayer player, LivingEntity speaker) {
		MinecraftServer server = player.level().getServer();
		Faction f = Factions.around(server, player.level(), player.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null) {
			f = Factions.of(server, player.getUUID());
		}
		List<String> ids = new ArrayList<>();
		if (f != null) {
			for (Blueprint.Entry e : Blueprint.catalogue(f.type())) {
				if (!ids.contains(e.id())) {
					ids.add(e.id());
				}
			}
		}
		for (String id : Designs.ids(server)) {
			if (!ids.contains(id)) {
				ids.add(id);
			}
		}
		List<String> shown = ids.subList(0, Math.min(MOST, ids.size()));
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (String id : shown) {
			options.add(Component.translatableWithFallback("building.jaymoskingdoms." + id, id.replace('_', ' ')));
			enabled.add(true);
		}
		options.add(Component.translatable("talk.jaymoskingdoms.errands.leave"));
		enabled.add(true);
		ZFighterFolk.ask(player, speaker, Component.translatable("talk.jaymoskingdoms.errands.build_ask"), options, enabled, pick -> {
			if (pick >= 0 && pick < shown.size()) {
				Plots.choose(player, shown.get(pick));
			}
		});
	}

	/** Who your courier can carry to: the players about, by the names they go by here. */
	private static void couriers(ServerPlayer player, LivingEntity speaker) {
		List<ServerPlayer> others = new ArrayList<>();
		for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
			if (p != player && others.size() < MOST) {
				others.add(p);
			}
		}
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (ServerPlayer p : others) {
			options.add(Component.literal(McaBridgeNames.name(p)));
			enabled.add(true);
		}
		options.add(Component.translatable("talk.jaymoskingdoms.errands.leave"));
		enabled.add(true);
		ZFighterFolk.ask(player, speaker, Component.translatable(others.isEmpty() ? "talk.jaymoskingdoms.errands.courier_none"
			: "talk.jaymoskingdoms.errands.courier_ask", player.getMainHandItem().getHoverName()), options, enabled, pick -> {
			if (pick >= 0 && pick < others.size()) {
				// (the old command does the work: its rules and its words)
				run(player, "kingdom deliver " + others.get(pick).getGameProfile().name());
			}
		});
	}

	private static void run(ServerPlayer player, String command) {
		MinecraftServer server = player.level().getServer();
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	/** The name a player goes by in play (MCA's, with MCA; else their own). */
	static final class McaBridgeNames {
		static String name(ServerPlayer player) {
			return McaCompat.LOADED ? McaBridge.name(player) : player.getName().getString();
		}
	}
}
