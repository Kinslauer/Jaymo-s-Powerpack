package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.OutOfPlay;
import com.jaymospowers.core.finisher.Finishers;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Combo Clash (HanHoYoung, "Combo Clash" by HanHoYoung; all rights reserved, shipped as its unmodified jar in this free
 * pack, which its licence allows with that credit on the pack page; nothing of it is copied here). Andy (2026-11-27):
 * "This is pretty much the EXACT combat system i am after ... I'd like dodges and 4 hit strings especially." Its combat
 * mode (its own key) gives the 4-hit M1 strings, uppercuts, downslams, block, perfect block, dashes and the evasive.
 * <p>
 * This side keeps its strings out of the hands of anyone who can't fight just now: someone downed or out cold, carried,
 * fainting, tied hand and foot, black while a new life is made ({@link OutOfPlay}), or crippled off their feet
 * ({@link Finishers#helpless}). It says so through Combo Clash's public M1 hook ({@code com.comboclash.api.ComboAPI},
 * {@code registerM1Hook}), reached by reflection, so nothing breaks when Combo Clash isn't installed. The client side
 * ({@code client.compat.ComboClashClient}) also turns its combat mode off for them, and steps our own swings, Alt dodge
 * and poses aside while it's on.
 * <p>
 * Andy (2026-11-28): "their dash was too long, the dash i want should be quick and short, and holding the button should
 * extend it into the long roll". Its dash is cut to {@link #DASH_TICKS} ticks at {@link #DASH_SCALE} of its speed (about
 * 2.3 blocks, where its own went nearly 5), the tick it starts and before it moves anyone; held, our long roll takes over
 * from it ({@code stamina.Dodge#clashRoll}).
 */
public final class ComboClashCompat {
	public static final String MOD_ID = "comboclash";
	/** Its dash, cut short: this many ticks, at this share of its speed. Its own is 5 ticks at 0.95 blocks a tick. */
	static final int DASH_TICKS = 3;
	static final double DASH_SCALE = 0.8;
	/** The length Combo Clash sets a fresh dash to (a dash at this is one it has only just started). */
	private static final int CLASH_DASH_TICKS = 5;

	private static Method peek;
	private static Field dashTicks, dashVec;

	private ComboClashCompat() {
	}

	/** Whether {@code player} can't throw a blow just now (both sides use it). */
	public static boolean cantFight(Player player) {
		return OutOfPlay.is(player) || Carry.bound(player) || Finishers.helpless(player);
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		try {
			Class<?> api = Class.forName("com.comboclash.api.ComboAPI");
			Class<?> hook = Class.forName("com.comboclash.api.ComboAPI$M1Hook");
			InvocationHandler handler = (proxy, method, args) -> {
				if (method.getName().equals("allowed") && args != null && args.length == 1 && args[0] instanceof Player player) {
					return !cantFight(player);
				}
				if (method.isDefault()) {
					return InvocationHandler.invokeDefault(proxy, method, args);
				}
				return objectMethod(proxy, method, args);
			};
			Object m1Hook = Proxy.newProxyInstance(hook.getClassLoader(), new Class<?>[] {hook}, handler);
			api.getMethod("registerM1Hook", hook).invoke(null, m1Hook);
		} catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
			JaymosPowers.LOGGER.warn("Couldn't reach Combo Clash's M1 hook (a newer Combo Clash?)", e);
		}
		try {
			peek = Class.forName("com.comboclash.combat.CombatManager").getMethod("peek", LivingEntity.class);
			Class<?> state = Class.forName("com.comboclash.combat.CombatState");
			dashTicks = state.getField("dashTicks");
			dashVec = state.getField("dashVec");
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't reach Combo Clash's dash (a newer Combo Clash?); it keeps its own length", e);
			return;
		}
		// before its own end of tick, which moves the dasher
		ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerTickEvents.END_SERVER_TICK.register(EARLY, server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (peek == null) {
					return;
				}
				shortenDash(player);
			}
		});
	}

	private static final net.minecraft.resources.Identifier EARLY = JaymosPowers.id("before_combo_clash");

	/** A dash Combo Clash has only just started on {@code player}: cut short. */
	private static void shortenDash(ServerPlayer player) {
		try {
			Object state = peek.invoke(null, player);
			if (state == null || dashTicks.getInt(state) != CLASH_DASH_TICKS || !(dashVec.get(state) instanceof Vec3 vec)) {
				return;
			}
			dashTicks.setInt(state, DASH_TICKS);
			dashVec.set(state, vec.scale(DASH_SCALE));
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't cut Combo Clash's dash short", e);
			peek = null;
		}
	}

	/** {@code equals}, {@code hashCode} and {@code toString} for the proxy. */
	private static Object objectMethod(Object proxy, Method method, Object[] args) {
		return switch (method.getName()) {
			case "equals" -> args != null && args.length == 1 && proxy == args[0];
			case "hashCode" -> System.identityHashCode(proxy);
			case "toString" -> "Jaymo's Powers' Combo Clash hook";
			default -> null;
		};
	}
}
