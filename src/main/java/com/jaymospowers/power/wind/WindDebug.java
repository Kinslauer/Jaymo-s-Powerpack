package com.jaymospowers.power.wind;

import com.jaymospowers.core.Fate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Test commands (gamemasters), all acting on the player running them:
 * <ul>
 * <li>{@code /powerdebug wind stormbound <minutes>}: pretend you turned Stormbound that many minutes ago (the storm blows
 * itself out after 3 Minecraft days, 60 minutes);</li>
 * <li>{@code /powerdebug wind weather}: the sky, the eased factor, corruption and the calm;</li>
 * <li>{@code /powerdebug wind calm <seconds>}: set the Eye of the Storm's progress;</li>
 * <li>{@code /powerdebug wind cooldowns}: clear every cooldown, the lance's too;</li>
 * <li>{@code /powerdebug wind strike}: call Stormbound lightning where you look (you're immune).</li>
 * </ul>
 */
final class WindDebug {
	private WindDebug() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("powerdebug")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("wind")
					.then(Commands.literal("stormbound")
						.then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1000))
							.executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								int minutes = IntegerArgumentType.getInteger(c, "minutes");
								WindStorm.setStormboundTicks(player, minutes * 1200L);
								c.getSource().sendSuccess(() -> Component.literal("Stormbound for " + minutes + " min (blows out at "
									+ WindStorm.CURE_TICKS / 1200 + "; stormbound: " + Fate.isCorrupted(player) + ")"), false);
								return minutes;
							})))
					.then(Commands.literal("weather")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							String text = String.format(Locale.ROOT, "sky %s, target %s (%.2f), factor %.2f, corruption %d, stormbound %s, calm %.1f/%ds, stormbound %d/%d min, kills %d",
								WindWeather.sky(player), WindWeather.kind(player), WindWeather.kind(player).factor, WindWeather.factor(player),
								Fate.corruption(player), Fate.isCorrupted(player), WindStorm.calmTicks(player) / 20.0F, WindStorm.EYE_TICKS / 20,
								WindStorm.stormboundTicks(player) / 1200, WindStorm.CURE_TICKS / 1200, WindTempest.breezeKills(player));
							c.getSource().sendSuccess(() -> Component.literal(text), false);
							return 1;
						}))
					.then(Commands.literal("calm")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(0, 60))
							.executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								int seconds = IntegerArgumentType.getInteger(c, "seconds");
								WindStorm.setCalmTicks(player, seconds * 20);
								c.getSource().sendSuccess(() -> Component.literal("Calm set to " + seconds + " s (only holds while in the eye)"), false);
								return seconds;
							})))
					.then(Commands.literal("cooldowns")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							WindPower.INSTANCE.clearCooldowns(player);
							c.getSource().sendSuccess(() -> Component.literal("Wind cooldowns cleared"), false);
							return 1;
						}))
					.then(Commands.literal("strike")
						.executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							Vec3 eye = player.getEyePosition();
							Vec3 end = eye.add(player.getLookAngle().scale(48.0));
							HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
							WindStorm.strike(player, hit.getLocation());
							return 1;
						})))));
	}
}
