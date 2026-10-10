package com.jaymoskingdoms;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code /kingdom}: list, info, pledge, leave, deposit, withdraw (lords), reputation; and for operators, found (a
 * kingdom at your feet).
 */
final class KingdomCommands {
	private KingdomCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("list").executes(KingdomCommands::list))
			.then(Commands.literal("info").executes(c -> info(c, null))
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> info(c, StringArgumentType.getString(c, "name")))))
			.then(Commands.literal("pledge").then(Commands.argument("name", StringArgumentType.word()).executes(KingdomCommands::pledge)))
			.then(Commands.literal("leave").executes(KingdomCommands::leave))
			.then(Commands.literal("deposit").then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 2304)).executes(KingdomCommands::deposit)))
			.then(Commands.literal("withdraw").then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 2304)).executes(KingdomCommands::withdraw)))
			.then(Commands.literal("reputation").executes(KingdomCommands::reputation))
			.then(Commands.literal("found").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(KingdomCommands::found)));
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		if (Kingdoms.all(server).isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.none"), false);
			return 0;
		}
		for (Kingdom k : Kingdoms.all(server)) {
			c.getSource().sendSuccess(() -> line(server, k), false);
		}
		return Kingdoms.all(server).size();
	}

	private static Component line(MinecraftServer server, Kingdom k) {
		String state = k.fallen() ? "in ruin" : k.king().isEmpty() ? "awaiting a king" : k.overlord() >= 0 ? "vassal of " + nameOf(server, k.overlord()) : "standing";
		return Component.literal(k.name()).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(" (" + k.color().getName() + ") at " + k.capital().toShortString() + ", " + state + ", " + k.members().size()
				+ " sworn, " + k.treasury() + " emeralds").withStyle(ChatFormatting.GRAY));
	}

	private static String nameOf(MinecraftServer server, int id) {
		Kingdom k = Kingdoms.get(server, id);
		return k == null ? "?" : k.name();
	}

	private static int info(CommandContext<CommandSourceStack> c, String name) {
		MinecraftServer server = c.getSource().getServer();
		Kingdom k;
		if (name != null) {
			k = Kingdoms.byName(server, name);
		} else {
			ServerPlayer player = c.getSource().getPlayer();
			k = player == null ? null : Kingdoms.at(server, player.level(), player.blockPosition());
			if (k == null && player != null) {
				k = Kingdoms.of(server, player.getUUID());
			}
		}
		if (k == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		Kingdom kingdom = k;
		c.getSource().sendSuccess(() -> line(server, kingdom), false);
		for (var entry : kingdom.members().entrySet()) {
			ServerPlayer member = server.getPlayerList().getPlayer(entry.getKey());
			String who = member != null ? member.getGameProfile().name() : entry.getKey().toString().substring(0, 8);
			c.getSource().sendSuccess(() -> Component.literal("  " + who + ": ").append(Component.translatable(Rank.forStanding(entry.getValue().standing()).key()))
				.append(" (" + entry.getValue().standing() + ")").withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int pledge(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Kingdom k = Kingdoms.byName(server, StringArgumentType.getString(c, "name"));
		if (k == null || k.fallen()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		if (Kingdoms.of(server, player.getUUID()) != null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.already_sworn"));
			return 0;
		}
		if (Reputation.get(player, k.id()) < Reputation.WELCOME) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.not_welcome", k.name()));
			return 0;
		}
		Kingdoms.put(server, k.withMember(player.getUUID(), new Kingdom.Member(Rank.PEASANT.ordinal(), 0)));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.pledged", player.getDisplayName(), k.name()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int leave(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Kingdom k = Kingdoms.of(server, player.getUUID());
		if (k == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.not_sworn"));
			return 0;
		}
		Kingdoms.put(server, k.withMember(player.getUUID(), null));
		// leaving stings
		Reputation.change(player, k, -50);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.left", k.name()), false);
		return 1;
	}

	private static int deposit(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Kingdom k = Kingdoms.of(server, player.getUUID());
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
		Kingdom paid = k.withTreasury(k.treasury() + want);
		Kingdoms.put(server, paid);
		Reputation.change(player, paid, want / 4);
		KingdomEvents.standing(server, player, paid, paid.member(player.getUUID()).standing() + want);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.deposited", want, k.name(), k.treasury() + want), false);
		return want;
	}

	private static int withdraw(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Kingdom k = Kingdoms.of(server, player.getUUID());
		Kingdom.Member member = k == null ? null : k.member(player.getUUID());
		if (member == null || Rank.forStanding(member.standing()) != Rank.LORD) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.lords_only"));
			return 0;
		}
		int want = (int)Math.min(IntegerArgumentType.getInteger(c, "emeralds"), k.treasury());
		Kingdoms.put(server, k.withTreasury(k.treasury() - want));
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

	private static int reputation(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		for (Kingdom k : Kingdoms.all(server)) {
			int rep = Reputation.get(player, k.id());
			c.getSource().sendSuccess(() -> Component.literal(k.name() + ": ").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable("reputation.jaymoskingdoms." + Reputation.word(rep)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" (" + rep + ")").withStyle(ChatFormatting.GRAY)), false);
		}
		return 1;
	}

	private static int found(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Kingdom k = KingdomEvents.found(player.level(), player.blockPosition());
		c.getSource().sendSuccess(() -> Component.literal("Founded " + k.name()), true);
		return 1;
	}
}
