package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.War;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.224) Andy (2026-10-26): "War wagers can only be done with bookie npcs in speakeasies." Every den keeps a bookie
 * ({@link Underworld}). Right-click him and he reads you his book: the wars still taking wagers (each side at his odds,
 * by the armies and sellswords behind it) and, while the pit's card is called, the next bout ({@link FightPit}). Hold
 * the emeralds you mean to stake and click a line. Winnings come to you when the war or the bout is decided
 * ({@link GreatWars#owe}).
 */
final class Bookies {
	/** The bookie (scoreboard tag). */
	static final String TAG = "jaymoskingdoms.bookie";
	/** A bet is placed within this of a bookie. */
	static final double REACH = 6.0;
	/** War odds: the chance given each side is its share of the armies squeezed into this range; the house keeps {@link FightPit#HOUSE}. */
	static final double LEAST_CHANCE = 0.15, CHANCE_SPAN = 0.7;
	static final double WAR_SHORTEST = 1.2, WAR_LONGEST = 5.0;

	private Bookies() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("bookie")
			.then(Commands.literal("war").then(Commands.argument("war", IntegerArgumentType.integer(0))
				.then(Commands.argument("side", IntegerArgumentType.integer(0)).executes(Bookies::betWar))))
			.then(Commands.literal("bout").then(Commands.argument("fighter", IntegerArgumentType.integer(0, 1)).executes(Bookies::betBout)))
			.then(Commands.literal("fight").executes(Bookies::fight))));
	}

	/** A bookie in {@code box}, if any. */
	static @Nullable Villager near(ServerLevel level, AABB box) {
		List<Villager> found = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && v.entityTags().contains(TAG));
		return found.isEmpty() ? null : found.getFirst();
	}

	/** The odds on {@code side} of {@code war}: by the armies on each side (with their sellswords). */
	static double warOdds(MinecraftServer server, War war, int side) {
		int mine = Math.max(1, Wars.army(server, side)), theirs = Math.max(1, Wars.army(server, Wars.otherSide(war, side)));
		double p = LEAST_CHANCE + CHANCE_SPAN * mine / (double)(mine + theirs);
		double o = FightPit.HOUSE / p;
		return Math.round(Math.max(WAR_SHORTEST, Math.min(WAR_LONGEST, o)) * 10.0) / 10.0;
	}

	private static String odds(double o) {
		return String.format(Locale.ROOT, "%.1f", o);
	}

	/** {@code player} speaks to {@code bookie}: his book, each line a bet to click. */
	static void open(ServerLevel level, ServerPlayer player, Villager bookie) {
		MinecraftServer server = level.getServer();
		bookie.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.greet." + level.getRandom().nextInt(Underworld.LINES)), Speech.GOLD);
		level.playSound(null, bookie.getX(), bookie.getY(), bookie.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.7F, 1.0F);
		player.sendSystemMessage(Component.translatable("bookie.jaymoskingdoms.book").withStyle(ChatFormatting.GOLD));
		int lines = 0;
		Faction here = Theft.lawAt(level, bookie.blockPosition());
		FightPit.Bout bout = here == null ? null : FightPit.bout(here.id());
		if (bout != null && bout.phase == FightPit.Phase.BETTING) {
			player.sendSystemMessage(Component.translatable("bookie.jaymoskingdoms.pit").withStyle(ChatFormatting.GRAY));
			for (int i = 0; i < 2; i++) {
				player.sendSystemMessage(Component.literal("  ").append(click(Component.translatable("bookie.jaymoskingdoms.back",
					FightPit.describe(level, bout, i)), "/bookie bout " + i)));
			}
			lines++;
		}
		// (Andy: "Sure add that") a place on the card for yourself
		if (here != null && FightPit.pit(server, here.id()) != null && (bout == null || bout.phase == FightPit.Phase.BETTING && !bout.player[0] && !bout.player[1])) {
			player.sendSystemMessage(Component.literal("  ").append(click(Component.translatable("bookie.jaymoskingdoms.fight", FightPit.ENTRY), "/bookie fight")));
			lines++;
		}
		for (War war : WarData.wars(server)) {
			if (!GreatWars.wagersOpen(server, war)) {
				continue;
			}
			Component head = Component.translatable(GreatWars.great(server, war) ? "bookie.jaymoskingdoms.war.great" : "bookie.jaymoskingdoms.war",
				Wars.name(server, war.attacker().principal()), Wars.name(server, war.defender().principal()));
			player.sendSystemMessage(head.copy().withStyle(ChatFormatting.GRAY));
			for (int side : new int[] {war.attacker().principal(), war.defender().principal()}) {
				player.sendSystemMessage(Component.literal("  ").append(click(Component.translatable("bookie.jaymoskingdoms.back_side", Wars.name(server, side),
					Wars.army(server, side), odds(warOdds(server, war, side))), "/bookie war " + war.id() + " " + side)));
			}
			lines++;
		}
		player.sendSystemMessage(Component.translatable(lines == 0 ? "bookie.jaymoskingdoms.nothing" : "bookie.jaymoskingdoms.how",
			Math.max(GreatWars.WAGER_MOST, FightPit.MOST_STAKE)).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static MutableComponent click(Component text, String command) {
		return text.copy().withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.translatable("bookie.jaymoskingdoms.hover"))));
	}

	/** The bookie within reach of {@code player} (null, with a word, if there's none). */
	private static @Nullable Villager reach(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		Villager bookie = near(player.level(), player.getBoundingBox().inflate(REACH));
		if (bookie == null) {
			c.getSource().sendFailure(Component.translatable("bookie.jaymoskingdoms.none"));
		}
		return bookie;
	}

	/** The emeralds in {@code player}'s hand (null, with a word, if none). */
	private static @Nullable ItemStack stake(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (!held.is(Items.EMERALD)) {
			c.getSource().sendFailure(Component.translatable("bookie.jaymoskingdoms.hold"));
			return null;
		}
		return held;
	}

	private static int betWar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Villager bookie = reach(c, player);
		ItemStack held = bookie == null ? null : stake(c, player);
		if (held == null) {
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		War war = WarData.war(server, IntegerArgumentType.getInteger(c, "war"));
		int side = IntegerArgumentType.getInteger(c, "side");
		if (war == null || !war.involves(side)) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.wager.closed"));
			return 0;
		}
		int stake = Math.min(held.getCount(), GreatWars.WAGER_MOST);
		GreatWars.Taken taken = GreatWars.wager(player, war, side, stake, (float)warOdds(server, war, side));
		return settle(player, bookie, held, taken);
	}

	private static int betBout(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Villager bookie = reach(c, player);
		ItemStack held = bookie == null ? null : stake(c, player);
		if (held == null) {
			return 0;
		}
		Faction here = Theft.lawAt(player.level(), bookie.blockPosition());
		if (here == null) {
			c.getSource().sendFailure(Component.translatable("fightpit.jaymoskingdoms.bet.closed"));
			return 0;
		}
		int stake = Math.min(held.getCount(), FightPit.MOST_STAKE);
		GreatWars.Taken taken = FightPit.bet(player.level(), player, here.id(), IntegerArgumentType.getInteger(c, "fighter"), stake);
		return settle(player, bookie, held, taken);
	}

	/** A place on the pit's card for {@code player}, the entry paid from their hand. */
	private static int fight(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Villager bookie = reach(c, player);
		ItemStack held = bookie == null ? null : stake(c, player);
		if (held == null) {
			return 0;
		}
		Faction here = Theft.lawAt(player.level(), bookie.blockPosition());
		if (here == null) {
			c.getSource().sendFailure(Component.translatable("fightpit.jaymoskingdoms.enter.no_pit"));
			return 0;
		}
		if (held.getCount() < FightPit.ENTRY) {
			c.getSource().sendFailure(Component.translatable("fightpit.jaymoskingdoms.enter.fee", FightPit.ENTRY));
			return 0;
		}
		return settle(player, bookie, held, FightPit.enter(player.level(), player, here));
	}

	/** The bookie pockets what he took and says so. */
	private static int settle(ServerPlayer player, Villager bookie, ItemStack held, GreatWars.Taken taken) {
		ServerLevel level = player.level();
		if (taken.kept() > 0) {
			held.shrink(taken.kept());
			level.playSound(null, bookie.getX(), bookie.getY(), bookie.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 1.0F);
		}
		Speech.tell(level, bookie, player, taken.said(), taken.kept() > 0 ? Speech.GOLD : Speech.WHITE);
		player.sendSystemMessage(taken.said().copy().withStyle(taken.kept() > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		return taken.kept() > 0 ? 1 : 0;
	}
}
