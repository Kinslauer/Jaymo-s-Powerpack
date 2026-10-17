package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /powerdebug zfighter ready}: clears every cooldown and fills the ki;</li>
 * <li>{@code /powerdebug zfighter hardship <n>}: sets the hardship ({@value ZFighterBattle#SUPER_HARDSHIP} opens the way
 * to Super);</li>
 * <li>{@code /powerdebug zfighter foes}: lists every foe that has beaten you and the rematches still owed;
 * {@code foes clear} forgets them;</li>
 * <li>{@code /powerdebug zfighter tp <n>}: sets your Training Points;</li>
 * <li>{@code /powerdebug zfighter mastery <form> <percent>}: sets a form's mastery (1-3);</li>
 * <li>{@code /powerdebug zfighter train <method> <points>}: trains by one of {@link ZFighterGrowth}'s methods (0-17, as if
 * earned: tired by today's use; survival only); {@code train newday}: every method fresh again;</li>
 * <li>{@code /powerdebug zfighter raid}: a tyrant lands near you; {@code tournament}: a tournament opens near you,
 * {@code tournament start} ends its sign-up.</li>
 * </ul>
 */
final class ZFighterDebugCommand {
	private ZFighterDebugCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("zfighter")
				.then(Commands.literal("ready")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						ZFighterPower.set(player, ZFighterPower.state(player).cleared());
						ZFighterPower.live(player).ki = ZFighterPower.maxKi(player);
						c.getSource().sendSuccess(() -> Component.literal("Z Fighter cooldowns cleared, ki full."), false);
						return 1;
					}))
				.then(Commands.literal("hardship")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.setAttached(ZFighterPower.HARDSHIP, IntegerArgumentType.getInteger(c, "n"));
							c.getSource().sendSuccess(() -> Component.literal("Hardship: " + ZFighterPower.hardship(player) + "/"
								+ ZFighterBattle.SUPER_HARDSHIP), false);
							return 1;
						})))
				.then(Commands.literal("tp")
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 10000000))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.setAttached(ZFighterTraining.DATA, ZFighterTraining.of(player).withTp(IntegerArgumentType.getInteger(c, "n")));
							c.getSource().sendSuccess(() -> Component.literal("TP: " + ZFighterTraining.of(player).tp()), false);
							return 1;
						})))
				.then(Commands.literal("mastery")
					.then(Commands.argument("form", IntegerArgumentType.integer(1, ZFighterTraining.FORMS))
						.then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
							.executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								int form = IntegerArgumentType.getInteger(c, "form");
								player.setAttached(ZFighterTraining.DATA, ZFighterTraining.of(player).withMastery(form,
									IntegerArgumentType.getInteger(c, "percent") * 100));
								c.getSource().sendSuccess(() -> Component.literal("Form " + form + " mastery: "
									+ ZFighterTraining.of(player).mastery(form) / 100 + "%"), false);
								return 1;
							}))))
				.then(Commands.literal("train")
					.then(Commands.argument("method", IntegerArgumentType.integer(0, ZFighterGrowth.METHODS - 1))
						.then(Commands.argument("points", IntegerArgumentType.integer(1, 100000))
							.executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								int method = IntegerArgumentType.getInteger(c, "method");
								ZFighterGrowth.train(player, method, IntegerArgumentType.getInteger(c, "points"));
								int stat = ZFighterGrowth.METHOD_STAT[method];
								c.getSource().sendSuccess(() -> Component.literal(ZFighterGrowth.METHOD_KEYS[method] + " -> " + ZFighterTraining.STAT_KEYS[stat]
									+ " level " + ZFighterTraining.of(player).stat(stat) + ", bar " + Math.round(ZFighterGrowth.of(player).progress(stat)) + "/"
									+ Math.round(ZFighterGrowth.bar(ZFighterTraining.of(player).stat(stat))) + ", today x"
									+ ZFighterGrowth.of(player).factor(method, ZFighterGrowth.day(player)) + " (survival only)"), false);
								return 1;
							})))
					.then(Commands.literal("newday")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							ZFighterGrowth.Data data = ZFighterGrowth.of(player);
							player.setAttached(ZFighterGrowth.DATA, new ZFighterGrowth.Data(data.progress(), data.trained(), data.day(), List.of()));
							c.getSource().sendSuccess(() -> Component.literal("Every training method is fresh again."), false);
							return 1;
						})))
				.then(Commands.literal("raid")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						boolean landed = ZFighterRaids.land(player.level(), player.blockPosition(), "Debug");
						c.getSource().sendSuccess(() -> Component.literal(landed ? "A tyrant lands nearby." : "No tyrant."), false);
						return 1;
					}))
				.then(Commands.literal("tournament")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						boolean opened = ZFighterTournaments.open(player.level(), player.blockPosition(), "Debug");
						c.getSource().sendSuccess(() -> Component.literal(opened ? "A tournament opens nearby." : "A tournament is already on."), false);
						return 1;
					})
					.then(Commands.literal("start")
						.executes(c -> {
							boolean closed = ZFighterTournaments.closeSignUp();
							c.getSource().sendSuccess(() -> Component.literal(closed ? "Sign-up closed." : "No tournament is signing up."), false);
							return 1;
						})))
				.then(Commands.literal("foes")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						List<String> foes = player.getAttachedOrElse(ZFighterPower.DEFEATS, List.of());
						List<String> grudges = player.getAttachedOrElse(ZFighterPower.GRUDGES, List.of());
						c.getSource().sendSuccess(() -> Component.literal("Beaten by (" + foes.size() + "): " + String.join(", ", foes)
							+ "; rematches owed: " + String.join(", ", grudges)
							+ (Powers.has(player, ZFighterPower.INSTANCE) ? "" : " (not a Z Fighter)")), false);
						return 1;
					})
					.then(Commands.literal("clear")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.removeAttached(ZFighterPower.DEFEATS);
							player.removeAttached(ZFighterPower.GRUDGES);
							c.getSource().sendSuccess(() -> Component.literal("Foes forgotten."), false);
							return 1;
						})))));
	}
}
