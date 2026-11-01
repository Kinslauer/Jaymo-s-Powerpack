package com.jaymospowers.power.xenoform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /powerdebug xenoform band}: a Xeno-Band in your hand;</li>
 * <li>{@code /powerdebug xenoform bands reset}: the world forgets every worn and loose band (0.18.92);</li>
 * <li>{@code /powerdebug xenoform hunter}: a band hunter comes for you now, if the rival band is out of the world (0.18.125);</li>
 * <li>{@code /powerdebug xenoform star}: a star falls where you're looking (about 30 blocks off);</li>
 * <li>{@code /powerdebug xenoform unlock <alien|all>}: the band takes in that alien;</li>
 * <li>{@code /powerdebug xenoform form <alien>}: become it now, recharge or not; {@code revert}: back to yourself;</li>
 * <li>{@code /powerdebug xenoform ready}: clears the recharge and every cooldown.</li>
 * </ul>
 */
final class XenoformDebugCommand {
	private XenoformDebugCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("xenoform")
				.then(Commands.literal("band")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						XenoBands.loosed(player.level().getServer());
						player.getInventory().placeItemBackInInventory(new ItemStack(XenoBandItem.BAND), net.minecraft.util.Prediction.SERVER_ONLY);
						c.getSource().sendSuccess(() -> Component.literal("A Xeno-Band."), false);
						return 1;
					}))
				.then(Commands.literal("bands")
					.then(Commands.literal("reset")
						.executes(c -> {
							XenoBands.reset(c.getSource().getServer());
							c.getSource().sendSuccess(() -> Component.literal("The world forgets every band: the next star may carry one."), true);
							return 1;
						})))
				.then(Commands.literal("hunter")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						if (!XenoBands.rivalFree(player.level().getServer())) {
							c.getSource().sendFailure(Component.literal("The rival band is already out (worn, loose or on a hunter). Try bands reset."));
							return 0;
						}
						if (RivalHunter.arrive(player.level(), player) == null) {
							c.getSource().sendFailure(Component.literal("Nowhere for him to stand (or no Kingdoms and MCA)."));
							return 0;
						}
						c.getSource().sendSuccess(() -> Component.literal("A band hunter is coming."), false);
						return 1;
					}))
				.then(Commands.literal("star")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						Vec3 look = player.getLookAngle().horizontal();
						look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
						Vec3 near = player.position().add(look.scale(30.0));
						if (!FallingStars.drop(player.level(), near)) {
							c.getSource().sendFailure(Component.literal("No good ground there (water?)."));
							return 0;
						}
						c.getSource().sendSuccess(() -> Component.literal("A star is falling."), false);
						return 1;
					}))
				.then(Commands.literal("unlock")
					.then(Commands.argument("alien", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(XenoForms.all().stream().map(f -> f.id),
							java.util.stream.Stream.of("all")), b))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							if (!XenoformPower.has(player)) {
								c.getSource().sendFailure(Component.literal("You don't have Xenoform."));
								return 0;
							}
							String name = StringArgumentType.getString(c, "alien");
							if (name.equals("all")) {
								XenoForms.all().forEach(f -> XenoformPower.unlock(player, f));
								c.getSource().sendSuccess(() -> Component.literal("Every alien unlocked."), false);
								return 1;
							}
							XenoForm form = XenoForms.get(name);
							if (form == null) {
								c.getSource().sendFailure(Component.literal("No such alien: " + name));
								return 0;
							}
							XenoformPower.unlock(player, form);
							c.getSource().sendSuccess(() -> Component.literal("Unlocked " + name + "."), false);
							return 1;
						})))
				.then(Commands.literal("form")
					.then(Commands.argument("alien", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(XenoForms.all().stream().map(f -> f.id), b))
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							XenoForm form = XenoForms.get(StringArgumentType.getString(c, "alien"));
							if (!XenoformPower.has(player) || form == null) {
								c.getSource().sendFailure(Component.literal("No Xenoform, or no such alien."));
								return 0;
							}
							XenoformPower.unlock(player, form);
							XenoformPower.revert(player, false, false);
							if (!XenoformPower.transform(player, form, true)) {
								c.getSource().sendFailure(Component.literal("Couldn't transform."));
								return 0;
							}
							c.getSource().sendSuccess(() -> Component.literal("You are " + form.id + "."), false);
							return 1;
						})))
				.then(Commands.literal("revert")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						XenoformPower.revert(player, false, true);
						c.getSource().sendSuccess(() -> Component.literal("Yourself again."), false);
						return 1;
					}))
				.then(Commands.literal("ready")
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						XenoformPower.ready(player);
						c.getSource().sendSuccess(() -> Component.literal("Xenoform recharge and cooldowns cleared."), false);
						return 1;
					}))
				.then(Commands.literal("trial")
					.then(Commands.literal("show")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							XenoformState.Trial trial = XenoformState.trial(player);
							c.getSource().sendSuccess(() -> Component.literal("The voices' trial: round " + trial.round() + ", stage " + trial.stage() + " of 4, answers "
								+ trial.answers() + ", Hush's deed " + XenoVoices.hushDeed(trial) + " " + trial.hushCount() + "/" + XenoVoices.hushGoal(trial)
								+ ", Clamour's deed " + XenoVoices.clamourDeed(trial) + " " + trial.clamourCount() + "/" + XenoVoices.clamourGoal(trial)
								+ (XenoVoices.eligible(player) ? "" : " (not ascended and corrupted: the voices sleep)")), false);
							return 1;
						}))
					.then(Commands.literal("reset")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							player.removeAttached(XenoformState.TRIAL);
							c.getSource().sendSuccess(() -> Component.literal("The voices' trial starts over."), false);
							return 1;
						}))
					.then(Commands.literal("content")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							XenoformState.Trial trial = XenoformState.trial(player);
							player.setAttached(XenoformState.TRIAL, new XenoformState.Trial(4, Math.max(1, trial.round()), java.util.List.of(), 0, 0));
							XenoformPower.unlock(player, XenoForms.NADIR);
							c.getSource().sendSuccess(() -> Component.literal("The voices are content: Nadir may be slammed once."), false);
							return 1;
						}))
					.then(Commands.literal("deeds")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							XenoformState.Trial trial = XenoformState.trial(player);
							player.setAttached(XenoformState.TRIAL, new XenoformState.Trial(3, Math.max(1, trial.round()),
								java.util.List.of("hush1=yes", "clamour1=yes", "hush2=yes", "clamour2=yes"), 0, 0));
							c.getSource().sendSuccess(() -> Component.literal("The voices' questions are answered: on to the deeds."), false);
							return 1;
						})))));
	}
}
