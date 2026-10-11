package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * {@code /kingdom plot mark [width depth] | build <building> | list | clear} (lords), and {@code /kingdom project}: what
 * the builders are putting up; for operators {@code project start [building] | finish | cancel}.
 */
final class BuildCommands {
	private BuildCommands() {
	}

	/** The buildings the faction where you stand (or yours) can put up. */
	private static final SuggestionProvider<CommandSourceStack> BUILDINGS = (c, builder) -> {
		Faction f = find(c);
		return SharedSuggestionProvider.suggest(f == null ? List.of()
			: Blueprint.catalogue(f.type()).stream().map(Blueprint.Entry::id).distinct().toList(), builder);
	};

	static LiteralArgumentBuilder<CommandSourceStack> plot() {
		return Commands.literal("plot")
			.then(Commands.literal("mark").executes(c -> mark(c, 0, 0))
				.then(Commands.argument("width", IntegerArgumentType.integer(Plots.MIN_SIDE, Plots.MAX_SIDE))
					.then(Commands.argument("depth", IntegerArgumentType.integer(Plots.MIN_SIDE, Plots.MAX_SIDE))
						.executes(c -> mark(c, IntegerArgumentType.getInteger(c, "width"), IntegerArgumentType.getInteger(c, "depth"))))))
			.then(Commands.literal("build").then(Commands.argument("building", StringArgumentType.word()).suggests(BUILDINGS)
				.executes(c -> Plots.choose(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "building")) ? 1 : 0)))
			.then(Commands.literal("list").executes(BuildCommands::list))
			.then(Commands.literal("clear").executes(c -> Plots.clear(c.getSource().getPlayerOrException()) ? 1 : 0));
	}

	static LiteralArgumentBuilder<CommandSourceStack> project() {
		return Commands.literal("project").executes(BuildCommands::status)
			.then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> start(c, null))
				.then(Commands.argument("building", StringArgumentType.word()).suggests(BUILDINGS)
					.executes(c -> start(c, StringArgumentType.getString(c, "building")))))
			.then(Commands.literal("finish").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> each(c, true)))
			.then(Commands.literal("cancel").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> each(c, false)));
	}

	/** The faction whose land (or seat) you're at; or yours. */
	private static @Nullable Faction find(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		ServerPlayer player = c.getSource().getPlayer();
		if (player == null) {
			return null;
		}
		Faction f = Factions.around(server, player.level(), player.blockPosition(), Villages.VILLAGE_RADIUS);
		return f != null ? f : Factions.of(server, player.getUUID());
	}

	private static int mark(CommandContext<CommandSourceStack> c, int w, int d) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		return Plots.mark(player, player.blockPosition().below(), w, d, w == 0) ? 1 : 0;
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		Faction f = find(c);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		Works.Site works = Works.of(server, f.id());
		if (works.plots().isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.plot.none", f.name()), false);
			return 0;
		}
		ServerLevel level = Factions.level(server);
		for (Works.Plot p : works.plots()) {
			Component what = p.building().isPresent()
				? Component.translatableWithFallback("building.jaymoskingdoms." + p.building().get(), p.building().get())
				: Component.translatable("message.jaymoskingdoms.plot.anything");
			Component state = Component.translatable(p.done() ? "message.jaymoskingdoms.plot.built"
				: works.projects().stream().anyMatch(x -> x.plot() == p.id()) ? "message.jaymoskingdoms.plot.building" : "message.jaymoskingdoms.plot.waiting");
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.plot.line", p.id(), p.min().toShortString(), p.width(),
				p.depth(), what, state).withStyle(ChatFormatting.GRAY), false);
			if (c.getSource().getPlayer() != null && c.getSource().getPlayer().level() == level) {
				Plots.outline(level, p);
			}
		}
		return works.plots().size();
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		Faction f = find(c);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		Works.Site works = Works.of(server, f.id());
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.project.header", f.name(), works.built().size())
			.withStyle(ChatFormatting.GOLD), false);
		if (works.projects().isEmpty()) {
			List<Blueprint.Entry> next = Planner.eligible(server, f);
			Component what = next.isEmpty() ? Component.translatable("message.jaymoskingdoms.project.nothing")
				: Component.translatableWithFallback("building.jaymoskingdoms." + next.getFirst().id(), next.getFirst().id());
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.project.idle", what).withStyle(ChatFormatting.GRAY), false);
		}
		for (Works.Project p : works.projects()) {
			Planner.Layout l = Construction.layout(level, f, p);
			int total = l == null ? 1 : l.columns() + l.ops().size();
			int done = l == null ? 0 : p.stage() == Works.BUILD ? l.columns() + p.index() : p.stage() == Works.PREP ? p.index() : 0;
			Component stage = Component.translatable("message.jaymoskingdoms.project.stage." + p.stage());
			Component state = Component.translatable(!Economy.atPeace(server, f) ? "message.jaymoskingdoms.project.paused"
				: Construction.waiting(level, f) ? "message.jaymoskingdoms.project.short" : "message.jaymoskingdoms.project.going");
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.project.line",
				Component.translatableWithFallback("building.jaymoskingdoms." + p.entry(), p.entry()), p.origin().toShortString(), stage,
				100 * done / Math.max(1, total), state), false);
			Map<Item, Integer> left = Construction.remaining(level, f, p);
			String needs = left.isEmpty() ? "-" : left.entrySet().stream()
				.map(e -> e.getValue() + " " + new ItemStack(e.getKey()).getHoverName().getString()).collect(Collectors.joining(", "));
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.project.needs", needs).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	// ---- operators (debug: plain text) --------------------------------------------------------------------------

	private static int start(CommandContext<CommandSourceStack> c, @Nullable String building) {
		Faction f = find(c);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		ServerLevel level = Factions.level(c.getSource().getServer());
		Construction.stopWaiting(f);
		Works.Project p = Planner.plan(level, f, true, building);
		c.getSource().sendSuccess(() -> Component.literal(p == null ? f.name() + " found nothing to build, or nowhere to build it"
			: f.name() + " begins a " + p.entry() + " at " + p.origin().toShortString()), true);
		return p == null ? 0 : 1;
	}

	private static int each(CommandContext<CommandSourceStack> c, boolean finish) {
		Faction f = find(c);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		ServerLevel level = Factions.level(c.getSource().getServer());
		List<Works.Project> projects = Works.of(level.getServer(), f.id()).projects();
		for (Works.Project p : projects) {
			if (finish) {
				Construction.finishNow(level, f, p);
			} else {
				Construction.cancel(level, f, p);
			}
		}
		c.getSource().sendSuccess(() -> Component.literal(f.name() + ": " + projects.size() + " project(s) " + (finish ? "finished" : "cancelled")), true);
		return projects.size();
	}
}
