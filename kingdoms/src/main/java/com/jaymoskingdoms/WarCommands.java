package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.Banner;
import com.jaymoskingdoms.WarData.Side;
import com.jaymoskingdoms.WarData.War;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /kingdom war}: {@code status}; for lords, {@code declare <name>} and {@code peace offer|accept [name]}; for
 * operators, {@code start <a> <b>} (no warm-up), {@code end <name>} (a white peace), {@code party} (the nearest war
 * sends a party now) and {@code npc} (NPC kings think about war now). (0.7.224) {@code tensions}, {@code creed [creed]}; for
 * operators {@code tension <a> <b> <value>} ({@link GreatWars}).
 */
final class WarCommands {
	/** How many banners {@code status} lists per war. */
	static final int STATUS_BANNERS = 6;

	private WarCommands() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("war")
			.then(Commands.literal("status").executes(WarCommands::status))
			.then(Commands.literal("declare").then(Commands.argument("name", StringArgumentType.word()).executes(WarCommands::declare)))
			.then(Commands.literal("peace")
				.then(Commands.literal("offer").executes(c -> peace(c, false, null))
					.then(Commands.argument("name", StringArgumentType.word()).executes(c -> peace(c, false, StringArgumentType.getString(c, "name")))))
				.then(Commands.literal("accept").executes(c -> peace(c, true, null))
					.then(Commands.argument("name", StringArgumentType.word()).executes(c -> peace(c, true, StringArgumentType.getString(c, "name"))))))
			.then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("a", StringArgumentType.word()).then(Commands.argument("b", StringArgumentType.word()).executes(WarCommands::start))))
			.then(Commands.literal("end").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("name", StringArgumentType.word()).executes(WarCommands::end)))
			.then(Commands.literal("join").executes(WarCommands::join))
			// (0.7.224) creeds and tension (wagers are a speakeasy bookie's: Bookies)
			.then(Commands.literal("tensions").executes(WarCommands::tensions))
			.then(Commands.literal("creed").executes(WarCommands::creed)
				.then(Commands.argument("creed", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(GreatWars.Creed.values()).map(GreatWars.Creed::key), b))
					.executes(WarCommands::setCreed)))
			.then(Commands.literal("tension").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("a", StringArgumentType.word()).then(Commands.argument("b", StringArgumentType.word())
					.then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(WarCommands::tension)))))
			.then(Commands.literal("party").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(WarCommands::party))
			.then(Commands.literal("npc").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(WarCommands::npc));
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		long now = Factions.level(server).getGameTime();
		List<War> wars = WarData.wars(server);
		if (wars.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.war.status.none").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		for (War war : wars) {
			String a = Wars.name(server, war.attacker().principal()), d = Wars.name(server, war.defender().principal());
			Component when = war.active()
				? Component.translatable("message.jaymoskingdoms.war.status.active", (now - war.startsAt()) / 1200L,
					Math.max(0L, war.startsAt() + Wars.maxTicks(server, war) - now) / 1200L)
				: Component.translatable("message.jaymoskingdoms.war.status.warmup", Math.max(0L, war.startsAt() - now) / 1200L);
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.war.status.header", a, d, when).withStyle(ChatFormatting.GOLD), false);
			// (0.7.224) a great war, and what the attacker's creed is fighting for
			Faction attacker = Factions.get(server, war.attacker().principal());
			if (attacker != null) {
				GreatWars.Creed creed = GreatWars.creed(server, attacker);
				c.getSource().sendSuccess(() -> Component.translatable(GreatWars.great(server, war) ? "message.jaymoskingdoms.war.status.great"
					: "message.jaymoskingdoms.war.status.goal", Component.translatable("goal.jaymoskingdoms." + creed.key()), creed.named())
					.withStyle(ChatFormatting.GRAY), false);
			}
			for (Side side : new Side[] {war.attacker(), war.defender()}) {
				String allies = Wars.members(server, side.principal()).stream().skip(1).map(Faction::name).reduce((x, y) -> x + ", " + y).orElse("-");
				c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.war.status.side", Wars.name(server, side.principal()), side.score(),
					side.kills(), side.leaders(), side.chunks(), side.loot(), Wars.army(server, side.principal()), allies).withStyle(ChatFormatting.GRAY), false);
			}
			if (war.offerFrom() >= 0) {
				c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.war.status.offer", Wars.name(server, war.offerFrom()))
					.withStyle(ChatFormatting.YELLOW), false);
			}
			if (war.active()) {
				c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.war.status.terms", Wars.preview(server, war))
					.withStyle(ChatFormatting.GRAY), false);
			}
			List<Banner> banners = Banners.of(server, war.id());
			banners.stream().limit(STATUS_BANNERS).forEach(b -> {
				long need = b.seat() ? Banners.SEAT_HOLD_TICKS : Banners.HOLD_TICKS;
				c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.war.status.banner", Wars.name(server, b.faction()),
					b.pos().toShortString(), Wars.name(server, b.target()), b.held() * 100 / need).withStyle(ChatFormatting.GRAY), false);
			});
		}
		return wars.size();
	}

	/** The faction a lord speaks for (null, with a message, if they're no lord of a realm). */
	private static @Nullable Faction lordOf(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		Faction mine = Factions.of(c.getSource().getServer(), player.getUUID());
		if (mine == null || !mine.type().isRealm() || !Wars.isLord(mine, player.getUUID())) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.war.lords_only"));
			return null;
		}
		return mine;
	}

	private static int declare(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction mine = lordOf(c, player);
		if (mine == null) {
			return 0;
		}
		Faction on = Factions.byName(server, StringArgumentType.getString(c, "name"));
		if (on == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		String refused = Wars.declare(server, mine, on, false);
		if (refused != null) {
			c.getSource().sendFailure(Component.translatable(refused, on.name()));
			return 0;
		}
		return 1;
	}

	private static int peace(CommandContext<CommandSourceStack> c, boolean accept, @Nullable String name) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction mine = lordOf(c, player);
		if (mine == null) {
			return 0;
		}
		List<War> wars = Wars.warsOf(server, mine.id());
		War war = null;
		if (name != null) {
			Faction other = Factions.byName(server, name);
			for (War w : wars) {
				if (other != null && Wars.sideOf(server, w, other.id()) >= 0 && Wars.sideOf(server, w, other.id()) != Wars.sideOf(server, w, mine.id())) {
					war = w;
				}
			}
		} else if (wars.size() == 1) {
			war = wars.getFirst();
		}
		if (war == null) {
			c.getSource().sendFailure(Component.translatable(wars.isEmpty() ? "message.jaymoskingdoms.war.not_at_war" : "message.jaymoskingdoms.war.which"));
			return 0;
		}
		int side = Wars.sideOf(server, war, mine.id());
		if (accept && war.offerFrom() != Wars.otherSide(war, side)) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.war.no_offer"));
			return 0;
		}
		if (!accept && war.offerFrom() == side) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.war.offer_standing"));
			return 0;
		}
		Wars.offer(server, war, side);
		return 1;
	}

	// ---- creeds and tension (0.7.224) ------------------------------------------------------------------------------------

	/** How many tense pairs {@code tensions} lists. */
	static final int TENSIONS_LISTED = 8;

	private static int tensions(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		List<Component> lines = GreatWars.describeTensions(server, TENSIONS_LISTED);
		if (lines.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.creed.no_tension").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.creed.tensions").withStyle(ChatFormatting.GOLD), false);
		for (Component line : lines) {
			c.getSource().sendSuccess(() -> line.copy().withStyle(ChatFormatting.GRAY), false);
		}
		return lines.size();
	}

	/** Your realm's creed. */
	private static int creed(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction mine = Factions.of(server, player.getUUID());
		if (mine == null || !mine.type().isRealm()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.war.join_unsworn"));
			return 0;
		}
		GreatWars.Creed creed = GreatWars.creed(server, mine);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.creed.yours", mine.name(), creed.named(),
			Component.translatable("creed.jaymoskingdoms." + creed.key() + ".about")).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	/** A lord of a players' realm (no NPC ruler, no overlord) sets its creed. */
	private static int setCreed(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction mine = lordOf(c, player);
		if (mine == null) {
			return 0;
		}
		GreatWars.Creed creed = GreatWars.Creed.parse(StringArgumentType.getString(c, "creed"));
		if (creed == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.creed.unknown"));
			return 0;
		}
		if (Tempers.of(mine) != null || mine.overlord() >= 0 || !Wars.warsOf(server, mine.id()).isEmpty()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.creed.not_yours"));
			return 0;
		}
		GreatWars.setCreed(server, mine.id(), creed);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.creed.adopted", mine.name(), creed.named()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	// ---- operators (debug: plain text) -----------------------------------------------------------------------------

	private static int tension(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		Faction a = Factions.byName(server, StringArgumentType.getString(c, "a"));
		Faction b = Factions.byName(server, StringArgumentType.getString(c, "b"));
		if (a == null || b == null || a.id() == b.id()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		int value = IntegerArgumentType.getInteger(c, "value");
		GreatWars.setTension(server, a.id(), b.id(), value);
		c.getSource().sendSuccess(() -> Component.literal("Tension between " + a.name() + " and " + b.name() + " set to " + value
			+ " (rivals at " + Math.round(GreatWars.RIVAL_AT) + ", war at " + Math.round(GreatWars.WAR_AT) + ", looked at once a day)"), true);
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		Faction a = Factions.byName(server, StringArgumentType.getString(c, "a"));
		Faction b = Factions.byName(server, StringArgumentType.getString(c, "b"));
		if (a == null || b == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		String refused = Wars.declare(server, a, b, true);
		if (refused != null) {
			c.getSource().sendFailure(Component.translatable(refused, b.name()));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("War between " + a.name() + " and " + b.name() + " started"), true);
		return 1;
	}

	private static int end(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		Faction f = Factions.byName(server, StringArgumentType.getString(c, "name"));
		List<War> wars = f == null ? List.of() : Wars.warsOf(server, f.id());
		if (wars.isEmpty()) {
			c.getSource().sendFailure(Component.literal("No war for that faction"));
			return 0;
		}
		for (War war : wars) {
			Wars.end(server, war, Component.translatable("message.jaymoskingdoms.war.ended_op", Wars.name(server, war.attacker().principal()),
				Wars.name(server, war.defender().principal())));
		}
		return wars.size();
	}

	private static int party(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		for (War war : WarData.wars(server)) {
			if (!war.active()) {
				continue;
			}
			for (int side : new int[] {war.attacker().principal(), war.defender().principal()}) {
				Faction target = WarParties.send(level, war, side, true);
				if (target != null) {
					c.getSource().sendSuccess(() -> Component.literal(Wars.name(server, side) + "'s side sends a party against " + target.name()), true);
					return 1;
				}
			}
		}
		c.getSource().sendFailure(Component.literal("No war could send a party (none under way, parties already out, or no enemy seat loaded near "
			+ player.getGameProfile().name() + ")"));
		return 0;
	}

	/** (0.5.28) Marches with their side's war party ({@link WarParties#join}). */
	private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		WarParties.Joined joined = WarParties.join(Factions.level(c.getSource().getServer()), player);
		if (!joined.ok()) {
			c.getSource().sendFailure(joined.message());
			return 0;
		}
		c.getSource().sendSuccess(joined::message, false);
		return 1;
	}

	private static int npc(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		long now = Factions.level(server).getGameTime();
		int declared = 0;
		for (Faction f : Factions.all(server, FactionType.KINGDOM)) {
			if (!f.fallen() && f.overlord() < 0 && f.leader().isPresent() && Wars.warsOf(server, f.id()).isEmpty()) {
				Faction rival = Wars.declareOnRival(server, f, now);
				if (rival != null) {
					declared++;
				}
			}
		}
		int n = declared;
		c.getSource().sendSuccess(() -> Component.literal(n + " NPC declaration(s) (kings declare only on rivals with a weaker army)"), true);
		return n;
	}
}
