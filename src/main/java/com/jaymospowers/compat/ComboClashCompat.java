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
 * <p>
 * Andy, the same day: "i like everything about except the air combos ... along with or enemy step for air combat". Its
 * air game is ours ({@code CombatManagerMixin}): every blow of its string counts as one on the ground (no hover in the
 * air, no air string, no downslam), its uppercut is our launcher ({@link #variant}, {@code mortal.AirCombo}), and up
 * there the attack key is vanilla's and our air combo's (juggle, slam, enemy step; {@code ComboClashClient}). And "there
 * should also be ways to cancel your string into combos using moves": a move made mid-string cuts it ({@link #giveWay}).
 */
public final class ComboClashCompat {
	public static final String MOD_ID = "comboclash";
	/** Its dash, cut short: this many ticks, at this share of its speed. Its own is 5 ticks at 0.95 blocks a tick. */
	static final int DASH_TICKS = 3;
	static final double DASH_SCALE = 0.8;
	/** The length Combo Clash sets a fresh dash to (a dash at this is one it has only just started). */
	private static final int CLASH_DASH_TICKS = 5;

	private static Method peek, giveWay;
	/**
	 * Its string's variants ({@code CombatManager.startM1}): 3 the fourth blow (the foe sent flying), 4 that blow with jump
	 * held (its uppercut), 5 that blow in the air (its downslam). Below 3, the first three blows.
	 */
	public static final int FOURTH = 3, UPPERCUT = 4, DOWNSLAM = 5;
	/** Server: the player whose blow Combo Clash is starting just now ({@code CombatManagerMixin}), and when each last threw a jump finisher. */
	private static ServerPlayer starting;
	private static final java.util.Map<java.util.UUID, Long> JUMP_FINISHER = new java.util.HashMap<>();
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
				if (method.getName().equals("landed") && args != null && args.length == 4 && args[0] instanceof ServerPlayer player
					&& args[1] instanceof LivingEntity target && args[2] instanceof Integer variant) {
					landed(player, target, variant);
					return null;
				}
				if (method.isDefault()) {
					return InvocationHandler.invokeDefault(proxy, method, args);
				}
				return objectMethod(proxy, method, args);
			};
			Object m1Hook = Proxy.newProxyInstance(hook.getClassLoader(), new Class<?>[] {hook}, handler);
			api.getMethod("registerM1Hook", hook).invoke(null, m1Hook);
			giveWay = api.getMethod("giveWay", ServerPlayer.class);
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
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> JUMP_FINISHER.remove(handler.player.getUUID()));
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

	/**
	 * Andy (2026-11-28): "there should also be ways to cancel your string into combos using moves". {@code player}'s
	 * string, if one is under way, gives way at once (its swing stops, nothing buffered follows), so a move made now
	 * plays clean: any power key, a combat art, the air combo's launcher, pull-up and enemy step. Nothing without Combo Clash.
	 */
	public static void giveWay(ServerPlayer player) {
		if (giveWay == null) {
			return;
		}
		try {
			giveWay.invoke(null, player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't cut Combo Clash's string short", e);
			giveWay = null;
		}
	}

	/** From {@code CombatManagerMixin}: Combo Clash is starting a blow of {@code player}'s. */
	public static void starting(ServerPlayer player) {
		starting = player;
	}

	/**
	 * From {@code CombatManagerMixin}: the variant Combo Clash picked for the blow it's starting, and the one it gets. Its
	 * uppercut and downslam (Andy: "i like everything about except the air combos") are a plain fourth blow; the uppercut's
	 * jump is remembered, and as that blow lands it's our launcher instead ({@link #landed}).
	 */
	public static int variant(int variant) {
		if (variant == UPPERCUT && starting != null) {
			JUMP_FINISHER.put(starting.getUUID(), starting.level().getGameTime());
		}
		return variant == UPPERCUT || variant == DOWNSLAM ? FOURTH : variant;
	}

	/** One of its blows landed on {@code target}: a jump finisher launches it as our air combo does. */
	private static void landed(ServerPlayer player, LivingEntity target, int variant) {
		Long at = JUMP_FINISHER.get(player.getUUID());
		if (variant != FOURTH || at == null) {
			return;
		}
		JUMP_FINISHER.remove(player.getUUID());
		if (player.level().getGameTime() - at <= 20) {
			com.jaymospowers.mortal.AirCombo.finisherLaunch(player, target);
		}
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
