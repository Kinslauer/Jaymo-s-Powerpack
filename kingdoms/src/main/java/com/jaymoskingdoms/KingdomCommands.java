package com.jaymoskingdoms;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * {@code /kingdom} (and its alias {@code /faction}, for villages and camps too): list, info, pledge, leave, deposit,
 * withdraw (lords), reputation; and for operators: found (a kingdom or village at your feet), storage, produce,
 * storehouse, crew, hire, relation.
 */
final class KingdomCommands {
	/** How many kinds of item {@code /kingdom storage} lists. */
	static final int STORAGE_LINES = 12;

	private KingdomCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("list").executes(KingdomCommands::list))
			.then(Commands.literal("info").executes(c -> info(c, null))
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> info(c, StringArgumentType.getString(c, "name")))))
			.then(Commands.literal("pledge").then(Commands.argument("name", StringArgumentType.word()).executes(KingdomCommands::pledge)))
			.then(Commands.literal("serve").then(Commands.argument("name", StringArgumentType.word()).executes(KingdomCommands::serve)))
			.then(Commands.literal("leave").executes(KingdomCommands::leave))
			.then(Commands.literal("deposit").then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 2304)).executes(KingdomCommands::deposit)))
			.then(Commands.literal("withdraw").then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 2304)).executes(KingdomCommands::withdraw)))
			.then(Commands.literal("reputation").executes(KingdomCommands::reputation))
			// (builders: plots and projects)
			.then(BuildCommands.plot()).then(BuildCommands.project())
			.then(Commands.literal("found").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> found(c, null))
				.then(Commands.literal("keep").executes(c -> found(c, Castle.Kind.KEEP)))
				.then(Commands.literal("castle").executes(c -> found(c, Castle.Kind.CASTLE)))
				.then(Commands.literal("tower").executes(c -> found(c, Castle.Kind.TOWER)))
				.then(Commands.literal("village").executes(KingdomCommands::foundVillage)))
			.then(Camps.command())
			.then(WarCommands.command())
			.then(Commands.literal("storage").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> storage(c, null))
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> storage(c, StringArgumentType.getString(c, "name")))))
			.then(Commands.literal("produce").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> produce(c, null))
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> produce(c, StringArgumentType.getString(c, "name")))))
			.then(Commands.literal("storehouse").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(KingdomCommands::storehouse))
			.then(Commands.literal("crew").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(KingdomCommands::crew))
			.then(Commands.literal("hire").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(KingdomCommands::hire))
			.then(Commands.literal("relation").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("a", StringArgumentType.word()).then(Commands.argument("b", StringArgumentType.word())
					.then(Commands.literal("allied").executes(c -> relation(c, Relation.ALLIED)))
					.then(Commands.literal("neutral").executes(c -> relation(c, Relation.NEUTRAL)))
					.then(Commands.literal("rival").executes(c -> relation(c, Relation.RIVAL)))
					.then(Commands.literal("war").executes(c -> relation(c, Relation.WAR)))))));
		dispatcher.register(Commands.literal("faction").redirect(root));
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		if (Factions.all(server).isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.none"), false);
			return 0;
		}
		for (Faction f : Factions.all(server)) {
			c.getSource().sendSuccess(() -> line(server, f), false);
		}
		return Factions.all(server).size();
	}

	private static Component line(MinecraftServer server, Faction f) {
		Component state = f.fallen() ? Component.translatable("message.jaymoskingdoms.state.ruin")
			: f.leader().isEmpty() ? Component.translatable("message.jaymoskingdoms.state.leaderless")
			: f.overlord() >= 0 ? Component.translatable("message.jaymoskingdoms.state.vassal", nameOf(server, f.overlord()))
			: Component.translatable("message.jaymoskingdoms.state.standing");
		return Component.literal(f.name()).withStyle(ChatFormatting.GOLD)
			.append(Component.translatable("message.jaymoskingdoms.line", Component.translatable(f.type().key()), f.color().getName(),
				f.seat().toShortString(), state, f.members().size(), f.treasury()).withStyle(ChatFormatting.GRAY));
	}

	private static String nameOf(MinecraftServer server, int id) {
		Faction f = Factions.get(server, id);
		return f == null ? "?" : f.name();
	}

	/** The named faction; or the one whose land you stand in (or whose seat is near); or the one you're sworn to. */
	private static @Nullable Faction find(CommandContext<CommandSourceStack> c, @Nullable String name) {
		MinecraftServer server = c.getSource().getServer();
		if (name != null) {
			return Factions.byName(server, name);
		}
		ServerPlayer player = c.getSource().getPlayer();
		if (player == null) {
			return null;
		}
		Faction f = Factions.around(server, player.level(), player.blockPosition(), Villages.VILLAGE_RADIUS);
		return f != null ? f : Factions.of(server, player.getUUID());
	}

	private static int info(CommandContext<CommandSourceStack> c, @Nullable String name) {
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		Faction found = find(c, name);
		if (found == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		Faction f = found;
		c.getSource().sendSuccess(() -> line(server, f), false);
		// (0.5.12) its ruler's temper
		Tempers.Temper temper = Tempers.of(f);
		if (temper != null) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.info.temper", Tempers.describe(temper)).withStyle(ChatFormatting.GRAY), false);
		}
		// relations
		List<String> relations = f.relations().entrySet().stream()
			.map(e -> nameOf(server, Integer.parseInt(e.getKey())) + " (" + e.getValue().id() + ")").toList();
		if (!relations.isEmpty() || f.overlord() >= 0) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.info.relations", String.join(", ", relations))
				.withStyle(ChatFormatting.GRAY), false);
		}
		// the economy
		boolean loaded = Storage.loaded(level, f);
		if (f.holdings().storage().isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.info.no_storage").withStyle(ChatFormatting.GRAY), false);
		} else if (!loaded) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.info.storage_unloaded").withStyle(ChatFormatting.GRAY), false);
		} else {
			Map<Item, Integer> needs = Economy.nextNeeds.apply(level, f);
			Map<Item, Integer> missing = Storage.missing(level, f, needs);
			int food = Storage.food(level, f);
			boolean healthy = missing.isEmpty() && food >= Economy.FOOD_RESERVE;
			String short_ = missing.isEmpty() ? "-" : missing.entrySet().stream()
				.map(e -> e.getValue() + " " + new ItemStack(e.getKey()).getHoverName().getString()).collect(Collectors.joining(", "));
			c.getSource().sendSuccess(() -> mark(healthy).append(Component.translatable("message.jaymoskingdoms.info.economy",
				food, Economy.FOOD_RESERVE, short_, Storage.freeSlots(level, f))), false);
		}
		boolean adequate = Economy.adequatePersonnel(f);
		c.getSource().sendSuccess(() -> mark(adequate).append(Component.translatable("message.jaymoskingdoms.info.personnel", Economy.staff(f),
			Economy.needed(f), f.holdings().vacant(), f.holdings().population(), Builders.crewCount(f))), false);
		boolean peace = Economy.atPeace(server, f);
		Component why = Economy.underAttack(server, f) ? Component.translatable("message.jaymoskingdoms.info.attacked")
			: Economy.atWar(server, f) ? Component.translatable("message.jaymoskingdoms.info.war",
				Relations.enemies(server, f).stream().map(Faction::name).collect(Collectors.joining(", ")))
			: Component.translatable("message.jaymoskingdoms.info.calm");
		c.getSource().sendSuccess(() -> mark(peace).append(Component.translatable("message.jaymoskingdoms.info.peace", why)), false);
		for (var entry : f.members().entrySet()) {
			ServerPlayer member = server.getPlayerList().getPlayer(entry.getKey());
			String who = member != null ? member.getGameProfile().name() : entry.getKey().toString().substring(0, 8);
			c.getSource().sendSuccess(() -> Component.literal("  " + who + ": ").append(Component.translatable(Rank.forStanding(entry.getValue().standing()).key()))
				.append(" (" + entry.getValue().standing() + ")").withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static MutableComponent mark(boolean good) {
		return Component.literal(good ? "  + " : "  - ").withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private static int pledge(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction k = Factions.byName(server, StringArgumentType.getString(c, "name"));
		if (k == null || k.fallen()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		if (!k.type().isRealm()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_pledge", k.name()));
			return 0;
		}
		if (Factions.of(server, player.getUUID()) != null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.already_sworn"));
			return 0;
		}
		if (Reputation.get(player, k.id()) < Reputation.WELCOME) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.not_welcome", k.name()));
			return 0;
		}
		Factions.update(server, k.id(), f -> f.withMember(player.getUUID(), new Faction.Member(Rank.PEASANT.ordinal(), 0)));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.pledged", player.getDisplayName(), k.name()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** (0.5.1) Takes up a ruler's offer to be his warden ({@link Wardens}). */
	private static int serve(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction k = Factions.byName(c.getSource().getServer(), StringArgumentType.getString(c, "name"));
		if (k == null || k.fallen()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		String failure = Wardens.serve(player, k);
		if (failure != null) {
			c.getSource().sendFailure(Component.translatable(failure));
			return 0;
		}
		return 1;
	}

	private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction k = Factions.of(server, player.getUUID());
		if (k == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.not_sworn"));
			return 0;
		}
		Factions.update(server, k.id(), f -> f.withMember(player.getUUID(), null));
		Wardens.dismiss(server, k.id(), player.getUUID());
		// leaving stings
		Reputation.change(player, k, -50);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.left", k.name()), false);
		return 1;
	}

	private static int deposit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction k = Factions.of(server, player.getUUID());
		if (k == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.not_sworn"));
			return 0;
		}
		int want = IntegerArgumentType.getInteger(c, "emeralds");
		int have = player.getInventory().countItem(Items.EMERALD);
		if (have < want) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.not_enough", have));
			return 0;
		}
		int take = want;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && take > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.EMERALD)) {
				int n = Math.min(take, stack.getCount());
				stack.shrink(n);
				take -= n;
			}
		}
		Faction paid = Factions.update(server, k.id(), f -> f.withTreasury(f.treasury() + want));
		Reputation.change(player, paid, want / 4);
		KingdomEvents.standing(server, player, paid, paid.member(player.getUUID()).standing() + want);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.deposited", want, k.name(), paid.treasury()), false);
		return want;
	}

	private static int withdraw(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction k = Factions.of(server, player.getUUID());
		Faction.Member member = k == null ? null : k.member(player.getUUID());
		if (member == null || Rank.forStanding(member.standing()) != Rank.LORD) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.lords_only"));
			return 0;
		}
		int want = (int)Math.min(IntegerArgumentType.getInteger(c, "emeralds"), k.treasury());
		Factions.update(server, k.id(), f -> f.withTreasury(f.treasury() - want));
		int left = want;
		while (left > 0) {
			int n = Math.min(64, left);
			ItemStack stack = new ItemStack(Items.EMERALD, n);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			left -= n;
		}
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.withdrew", want, k.name()), false);
		return want;
	}

	private static int reputation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		for (Faction k : Factions.all(server)) {
			int rep = Reputation.get(player, k.id());
			c.getSource().sendSuccess(() -> Component.literal(k.name() + ": ").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable("reputation.jaymoskingdoms." + Reputation.word(rep)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" (" + rep + ")").withStyle(ChatFormatting.GRAY)), false);
		}
		return 1;
	}

	// ---- operators (debug: plain text) --------------------------------------------------------------------------

	private static int found(CommandContext<CommandSourceStack> c, Castle.Kind kind) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction k = KingdomEvents.found(player.level(), player.blockPosition(), kind);
		c.getSource().sendSuccess(() -> Component.literal("Founded " + k.name()), true);
		return 1;
	}

	private static int foundVillage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction v = Villages.found(player.level(), player.blockPosition());
		c.getSource().sendSuccess(() -> Component.literal("Founded the village of " + v.name()), true);
		return 1;
	}

	private static int storage(CommandContext<CommandSourceStack> c, @Nullable String name) {
		Faction f = find(c, name);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		ServerLevel level = Factions.level(c.getSource().getServer());
		Map<Item, Integer> totals = Storage.totals(level, f);
		c.getSource().sendSuccess(() -> Component.literal(f.name() + ": " + f.holdings().storage().size() + " containers ("
			+ (Storage.loaded(level, f) ? "loaded" : "not all loaded") + "), depot " + f.holdings().depot().map(p -> p.toShortString()).orElse("none")
			+ ", " + Storage.freeSlots(level, f) + " free slots").withStyle(ChatFormatting.GOLD), false);
		totals.entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).limit(STORAGE_LINES).forEach(e ->
			c.getSource().sendSuccess(() -> Component.literal("  " + e.getValue() + " ").append(new ItemStack(e.getKey()).getHoverName())
				.withStyle(ChatFormatting.GRAY), false));
		return totals.size();
	}

	private static int produce(CommandContext<CommandSourceStack> c, @Nullable String name) {
		Faction f = find(c, name);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		Production.produce(Factions.level(c.getSource().getServer()), f, 1);
		Faction after = Factions.get(c.getSource().getServer(), f.id());
		c.getSource().sendSuccess(() -> Component.literal(f.name() + " produced a day's worth (population " + after.holdings().population() + ", crops "
			+ after.holdings().farms() + ", woodland " + after.holdings().forest() + "%, treasury " + after.treasury() + ")"), true);
		return 1;
	}

	private static int storehouse(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction f = find(c, null);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		var depot = Storehouse.buildNow(player, f);
		c.getSource().sendSuccess(() -> Component.literal(depot == null ? f.name() + " found no open ground to build on"
			: f.name() + " built its storage at " + depot.toShortString()), true);
		return depot == null ? 0 : 1;
	}

	private static int crew(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction f = find(c, null);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		List<Folk> crew = Builders.spawnCrew(player.level(), f);
		c.getSource().sendSuccess(() -> Component.literal(f.name() + " took on a crew of " + crew.size()), true);
		return crew.size();
	}

	private static int hire(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction f = find(c, null);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		Folk hired = Economy.hire(player.level(), f);
		c.getSource().sendSuccess(() -> Component.literal(hired == null ? f.name() + " took no one on"
			: f.name() + " took on a " + Folk.roleName(hired.role())), true);
		return hired == null ? 0 : 1;
	}

	private static int relation(CommandContext<CommandSourceStack> c, Relation relation) {
		MinecraftServer server = c.getSource().getServer();
		Faction a = Factions.byName(server, StringArgumentType.getString(c, "a"));
		Faction b = Factions.byName(server, StringArgumentType.getString(c, "b"));
		if (a == null || b == null || a.id() == b.id()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		Relations.set(server, a.id(), b.id(), relation);
		c.getSource().sendSuccess(() -> Component.literal(a.name() + " and " + b.name() + ": " + relation.id()), true);
		return 1;
	}
}
