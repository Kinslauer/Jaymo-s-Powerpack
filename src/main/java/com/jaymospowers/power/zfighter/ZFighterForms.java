package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * (0.17.50, Andy's NOEA picks) Patch 3, forms (track A): the Warrior-born's side. Form numbers beyond the three bought
 * Supers and Primal Super (4, {@link ApeRage#SUPER_4}):
 * <ul>
 * <li>{@link #FALSE_SUPER} (5), a near-death flare before the first Super ({@link ZFighterFalseSuper});</li>
 * <li>{@link #GOD} (6), the god form lifted by five at the Lookout ({@link ZFighterGodRitual}, {@link ZFighterGod}), with
 * silver instinct on top of it ({@link ZFighterInstinct});</li>
 * <li>{@link #LEGEND_1}-{@link #LEGEND_3} (7-9), the legendary line ({@link ZFighterLegendary}), a mutation's Super line.</li>
 * </ul>
 * The golden Great Ape ({@link ZFighterGoldenApe}) is no form of its own. The other track's forms start at 10. This class
 * holds the form numbers, the tier their effects play at, and the few calls {@link ZFighterPower} makes into all of this
 * ({@link #init}, {@link #tick}, {@link #ultimate}, {@link #attributes}).
 */
public final class ZFighterForms {
	public static final int FALSE_SUPER = 5, GOD = 6, LEGEND_1 = 7, LEGEND_2 = 8, LEGEND_3 = 9;
	public static final int FIRST = FALSE_SUPER, LAST = LEGEND_3;
	/** Colours of forms 5-9: a pale unsteady gold, the god's red, three greens (the legendary line grows darker, deeper). */
	private static final int[] COLORS = {0xFFE9A0, 0xE0302A, 0xA6E64A, 0x72DA45, 0x45C83C};
	/** How hard a form's particles, aura and shake play (the number of the Super it matches), by form 5-9. */
	private static final int[] TIERS = {2, 3, 2, 3, 4};

	private ZFighterForms() {
	}

	/** Whether {@code form} is one of this patch's. */
	public static boolean special(int form) {
		return form >= FIRST && form <= LAST;
	}

	/** The tier a form's effects play at: its own number up to Primal Super, a set one for the newer forms. */
	public static int tier(int form) {
		return special(form) ? TIERS[form - FIRST] : Math.clamp(form, 0, ApeRage.SUPER_4);
	}

	/** The aura colour of a form past Primal Super (clamped). */
	public static int color(int form) {
		return COLORS[Mth.clamp(form - FIRST, 0, COLORS.length - 1)];
	}

	/** The hair a form wears, as a Super's number (1-3): False Super the first Super's, the rest Super 3's mane. */
	public static int hairForm(int form) {
		return form == FALSE_SUPER ? 1 : Math.min(form, 3);
	}

	/** Whether the Spirit Bomb can be raised from this form: Super 3, Primal Super and the god form. */
	public static boolean bombForm(int form) {
		return form == ZFighterTraining.FORMS || form == ApeRage.SUPER_4 || form == GOD;
	}

	/** Called once from {@link ZFighterPower#init}. */
	static void init() {
		ZFighterFalseSuper.init();
		ZFighterGoldenApe.init();
		ZFighterGodRitual.init();
		ZFighterInstinct.init();
		ZFighterLegendary.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> debug(dispatcher));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ZFighterFalseSuper.clear();
			ZFighterGodRitual.clear();
			ZFighterInstinct.clear();
			ZFighterLegendary.clear();
		});
	}

	private static void forget(ServerPlayer player) {
		ZFighterFalseSuper.forget(player);
		ZFighterGodRitual.forget(player);
		ZFighterInstinct.forget(player);
		ZFighterLegendary.forget(player);
	}

	/** Every tick, per Z Fighter, from {@link ZFighterPower#tick}. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		ZFighterLegendary.tick(player, live);
		ZFighterFalseSuper.tick(player, live);
		ZFighterInstinct.tick(player, live);
		ZFighterGoldenApe.tick(player, live);
		if (live.form == GOD && player.tickCount % 40 == 0 && !live.charging) {
			ZFighterGod.glow(player);
		}
	}

	/** Part of {@link ZFighterPower#refresh}: the attribute modifiers these forms add. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		ZFighterGoldenApe.attributes(player, live);
		ZFighterLegendary.attributes(player, live);
		ZFighterFalseSuper.attributes(player, live);
	}

	/**
	 * C pressed (down), after the fusion dance and before the old chain: true when these forms answered it. The god form
	 * is taken from the top of the line (Super 3, Primal Super or Legend 3) once it is unlocked; in it, C raises the
	 * Spirit Bomb as in Super 3 (this returns false there). A legendary-born's C walks his own line; sneak+C steps down.
	 */
	static boolean ultimate(ServerPlayer player, ZFighterPower.Live live) {
		boolean sneak = player.isShiftKeyDown();
		int form = live.form;
		if (special(form)) {
			if (sneak) {
				stepDown(player, live);
				return true;
			}
			if (form == GOD) {
				return false;
			}
			if (ZFighterLegendary.legend(form)) {
				ZFighterLegendary.stepUp(player, live);
				return true;
			}
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_flare_hold").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (sneak) {
			return false;
		}
		boolean topOfLine = form == ApeRage.SUPER_4 || form == ZFighterTraining.FORMS && !ApeRage.super4(player);
		if (ZFighterGod.unlocked(player) && (topOfLine || form == LEGEND_3) && !ZFighterLineage.ape(player)) {
			ZFighterGod.enter(player, live);
			return true;
		}
		if (ZFighterLegendary.mutant(player) && form == 0) {
			ZFighterLegendary.stepUp(player, live);
			return true;
		}
		return false;
	}

	/** Sneak+C in one of these forms: down a step (the god form back to the top of the line, Legend 1 to base). */
	private static void stepDown(ServerPlayer player, ZFighterPower.Live live) {
		int form = live.form;
		if (form == FALSE_SUPER) {
			ZFighterFalseSuper.end(player, live, false);
			return;
		}
		if (form == GOD) {
			ZFighterInstinct.end(player, live, false);
			int top = ZFighterLegendary.line(player, Math.min(ZFighterTraining.formsOpen(player), ZFighterTraining.FORMS));
			if (top <= 0) {
				ZFighterPower.endSuper(player, live);
				return;
			}
			live.form = top;
		} else if (form == LEGEND_1) {
			ZFighterPower.endSuper(player, live);
			return;
		} else {
			live.form = form - 1;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.5F);
		ZFighterPower.refresh(player, live);
	}

	/** Test commands: {@code /powerdebug zfighter forms ...} (gamemasters). */
	private static void debug(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("powerdebug")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("zfighter")
				.then(Commands.literal("forms")
					.then(Commands.literal("flare").executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						boolean started = Powers.has(player, ZFighterPower.INSTANCE) && ZFighterFalseSuper.start(player, ZFighterPower.live(player));
						c.getSource().sendSuccess(() -> Component.literal(started ? "False Super flares." : "Can't flare now (not a Z Fighter, or already in a form)."), false);
						return 1;
					}))
					.then(Commands.literal("god")
						.then(Commands.literal("unlock").executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							ZFighterGod.unlock(player);
							c.getSource().sendSuccess(() -> Component.literal("God form unlocked (C from the top of your line)."), false);
							return 1;
						}))
						.then(Commands.literal("lock").executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							ZFighterGod.lock(player);
							c.getSource().sendSuccess(() -> Component.literal("God form locked again."), false);
							return 1;
						}))
						.then(Commands.literal("mastery")
							.then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
								ServerPlayer player = c.getSource().getPlayerOrException();
								ZFighterGod.setMastery(player, IntegerArgumentType.getInteger(c, "percent") * 100);
								c.getSource().sendSuccess(() -> Component.literal("God form mastery: " + ZFighterGod.mastery(player) / 100 + "%"), false);
								return 1;
							}))))
					.then(Commands.literal("lessons")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 100)).executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							ZFighterGodRitual.setLessons(player, IntegerArgumentType.getInteger(c, "n"));
							c.getSource().sendSuccess(() -> Component.literal("Good masters' lessons: " + ZFighterGodRitual.lessons(player)
								+ " (" + ZFighterGodRitual.spirits(player) + " ring spirits)."), false);
							return 1;
						})))
					.then(Commands.literal("mutation")
						.then(Commands.literal("on").executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							ZFighterLegendary.set(player, true);
							c.getSource().sendSuccess(() -> Component.literal("Legendary mutation on (Warrior-born only)."), false);
							return 1;
						}))
						.then(Commands.literal("off").executes(c -> {
							ServerPlayer player = c.getSource().getPlayerOrException();
							ZFighterLegendary.set(player, false);
							c.getSource().sendSuccess(() -> Component.literal("Legendary mutation off."), false);
							return 1;
						})))
					.then(Commands.literal("circle").executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						boolean painted = ZFighterGodRitual.paintNow(player.level().getServer());
						c.getSource().sendSuccess(() -> Component.literal(painted ? "Ritual circle painted on the Lookout." : "The Lookout isn't there yet."), false);
						return 1;
					})))));
	}
}
