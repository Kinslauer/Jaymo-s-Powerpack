package com.jaymospowers.core.wounds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Test commands (gamemasters only):
 * <ul>
 * <li>{@code /wounds show [target]}: every part's health in hearts;</li>
 * <li>{@code /wounds part <part> <amount> [target]}: wounds a part by that many health points;</li>
 * <li>{@code /wounds heal [target]}: full health, every part whole.</li>
 * <li>(0.14.2) {@code /wounds break <part> [target]}: breaks a limb (its health gone, its effects on);</li>
 * <li>(0.14.2) {@code /wounds mend [target]}: sets every break, as a priest would (they heal from there).</li>
 * </ul>
 * Without a target, you.
 */
final class WoundsCommand {
	private static final SimpleCommandExceptionType NOT_LIVING = new SimpleCommandExceptionType(Component.translatable("commands.jaymospowers.wounds.not_living"));
	private static final SimpleCommandExceptionType UNKNOWN_PART = new SimpleCommandExceptionType(Component.translatable("commands.jaymospowers.wounds.unknown_part"));

	private WoundsCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("wounds")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("show")
				.executes(c -> show(c.getSource(), c.getSource().getPlayerOrException()))
				.then(Commands.argument("target", EntityArgument.entity())
					.executes(c -> show(c.getSource(), EntityArgument.getEntity(c, "target")))))
			.then(Commands.literal("heal")
				.executes(c -> heal(c.getSource(), c.getSource().getPlayerOrException()))
				.then(Commands.argument("target", EntityArgument.entity())
					.executes(c -> heal(c.getSource(), EntityArgument.getEntity(c, "target")))))
			.then(Commands.literal("mend")
				.executes(c -> mend(c.getSource(), c.getSource().getPlayerOrException()))
				.then(Commands.argument("target", EntityArgument.entity())
					.executes(c -> mend(c.getSource(), EntityArgument.getEntity(c, "target")))))
			.then(Commands.literal("break")
				.then(Commands.argument("part", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(BodyPart.values())
						.filter(p -> !p.vital()).map(p -> p.name().toLowerCase(Locale.ROOT)), b))
					.executes(c -> breakPart(c.getSource(), c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "part")))
					.then(Commands.argument("target", EntityArgument.entity())
						.executes(c -> breakPart(c.getSource(), EntityArgument.getEntity(c, "target"), StringArgumentType.getString(c, "part"))))))
			.then(Commands.literal("part")
				.then(Commands.argument("part", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(BodyPart.values())
						.map(p -> p.name().toLowerCase(Locale.ROOT)), b))
					.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 1000.0))
						.executes(c -> hurt(c.getSource(), c.getSource().getPlayerOrException(),
							StringArgumentType.getString(c, "part"), (float)DoubleArgumentType.getDouble(c, "amount")))
						.then(Commands.argument("target", EntityArgument.entity())
							.executes(c -> hurt(c.getSource(), EntityArgument.getEntity(c, "target"),
								StringArgumentType.getString(c, "part"), (float)DoubleArgumentType.getDouble(c, "amount"))))))));
	}

	private static LivingEntity living(Entity entity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (entity instanceof LivingEntity living && Wounds.plan(living) != null) {
			return living;
		}
		throw NOT_LIVING.create();
	}

	private static int show(CommandSourceStack source, Entity entity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		LivingEntity living = living(entity);
		BodyPlan plan = Wounds.plan(living);
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			String now = String.format(Locale.ROOT, "%.1f", Wounds.points(living, part) / 2.0F);
			String most = String.format(Locale.ROOT, "%.1f", Wounds.cap(living, plan, i) / 2.0F);
			source.sendSuccess(() -> Component.translatable("commands.jaymospowers.wounds.line", part.displayName(), now, most), false);
		}
		return plan.size();
	}

	/** The part called {@code name} that {@code living} has. */
	private static BodyPart part(LivingEntity living, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		BodyPart part = null;
		for (BodyPart candidate : BodyPart.values()) {
			if (candidate.name().toLowerCase(Locale.ROOT).equals(name)) {
				part = candidate;
			}
		}
		BodyPlan plan = Wounds.plan(living);
		if (part == null || plan == null || plan.index(part) < 0) {
			throw UNKNOWN_PART.create();
		}
		return part;
	}

	/** (0.14.2) */
	private static int breakPart(CommandSourceStack source, Entity entity, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		LivingEntity living = living(entity);
		BodyPart part = part(living, name);
		if (part.vital()) {
			throw UNKNOWN_PART.create();
		}
		Wounds.breakPart(living, part, source.getEntity());
		source.sendSuccess(() -> Component.translatable("commands.jaymospowers.wounds.broke", part.displayName()), true);
		return 1;
	}

	/** (0.14.2) */
	private static int mend(CommandSourceStack source, Entity entity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		LivingEntity living = living(entity);
		boolean any = Wounds.mendAll(living);
		source.sendSuccess(() -> Component.translatable(any ? "commands.jaymospowers.wounds.mend" : "commands.jaymospowers.wounds.mend_none"), true);
		return any ? 1 : 0;
	}

	private static int hurt(CommandSourceStack source, Entity entity, String name, float amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		LivingEntity living = living(entity);
		BodyPart part = part(living, name);
		Wounds.hurtPart(living, part, amount, source.getEntity());
		BodyPart hurt = part;
		source.sendSuccess(() -> Component.translatable("commands.jaymospowers.wounds.hurt", hurt.displayName(), String.format(Locale.ROOT, "%.1f", amount)), true);
		return 1;
	}

	private static int heal(CommandSourceStack source, Entity entity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		LivingEntity living = living(entity);
		Wounds.healAll(living);
		source.sendSuccess(() -> Component.translatable("commands.jaymospowers.wounds.heal"), true);
		return 1;
	}
}
