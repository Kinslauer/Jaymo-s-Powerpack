package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.kitchen.OvenHob;
import com.jaymoskitchen.net.Payloads;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Cooking by hand, at the block, with no screen. Sneak and right-click a station with an ingredient to put it on; the
 * station reads what is on it ({@link Recipes}) and sneak and scroll picks between the dishes it makes; right-click with
 * the dish's tool (see {@link CookTools.Tool}) to begin. From then on there is one dish at the station
 * ({@link Run}), and anyone with the right tool in hand can step in and work it. It is judged once, as the old screen
 * games judged it, and as left if the last cook goes more than eight blocks off or logs out. The game rule
 * {@code jaymoskitchen:hands_on_cooking} (on by default) turns it off: the screens come back as they were.
 */
public final class HandsOn {
	/** What a {@link Payloads.HandsInput} carries: an angle round the centre (NaN: not on it), use held (1 or 0), where across the worktop, how far the view moved up or down (degrees). */
	public static final int IN_ANGLE = 0, IN_HOLD = 1, IN_ACROSS = 2, IN_ROLL = 3;
	/** A held input that hasn't been refreshed for this many ticks has been let go. */
	public static final int HOLD_STALE = 12;
	/** Ticks things lie on a station with nobody within reach before they're put back on the floor. */
	static final int IDLE_TICKS = 20 * 90;
	/** Ticks between checks that the game rule hasn't changed under the clients. */
	static final int MODE_CHECK = 20;

	public static GameRule<Boolean> ENABLED;

	/** Client side: whether the station at a position is known to have a dish running (so a left-click there is a chop, not mining). */
	public static Predicate<BlockPos> clientRuns = pos -> false;
	/** Client side: whether the station at a position is known to have a step recipe running (so an ingredient held there goes in the dish). */
	public static Predicate<BlockPos> clientSteps = pos -> false;
	/** Client side: whether hands-on cooking is on (from the server). */
	public static boolean clientOn;
	/** Client side: true once for each press of the attack key (a held key asks every tick; only the press is a chop). */
	public static BooleanSupplier clientPress = () -> true;

	/** Makes the run for a chosen dish. */
	@FunctionalInterface
	interface Factory {
		Run make(Table table, Pick pick, ServerPlayer starter, int now);
	}

	private static final Map<Pick.Kind, Factory> FACTORIES = new EnumMap<>(Pick.Kind.class);
	private record Spot(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final Map<Spot, Table> TABLES = new HashMap<>();
	private static final Map<UUID, Boolean> MODE_SENT = new HashMap<>();

	private HandsOn() {
	}

	public static boolean enabled(ServerLevel level) {
		return level.getGameRules().get(ENABLED);
	}

	/** Whether a dish cooked this way is done by hand here. */
	public static boolean supports(Pick.Kind kind) {
		return FACTORIES.containsKey(kind);
	}

	static void factory(Pick.Kind kind, Factory factory) {
		FACTORIES.put(kind, factory);
	}

	public static void register() {
		ENABLED = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.PLAYER).buildAndRegister(JaymosKitchen.id("hands_on_cooking"));
		CookTools.register();
		HandsBlocks.register();
		Runs.register();
		UseBlockCallback.EVENT.register(HandsOn::use);
		AttackBlockCallback.EVENT.register(HandsOn::attack);
		ServerTickEvents.END_SERVER_TICK.register(HandsOn::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			boolean on = enabled(handler.player.level());
			MODE_SENT.put(handler.player.getUUID(), on);
			ServerPlayNetworking.send(handler.player, new Payloads.HandsMode(on));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MODE_SENT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// nothing half-cooked is lost: a dish is judged as left, and what lay on a station goes on the floor
			for (Table table : new ArrayList<>(TABLES.values())) {
				if (table.run != null) {
					table.run.leave();
				}
				table.clear(null);
			}
			TABLES.clear();
			MODE_SENT.clear();
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.HandsInput.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			Table table = TABLES.get(new Spot(player.level().dimension(), payload.pos()));
			if (table != null && table.run != null && !table.run.over && table.inReach(player) && enabled(player.level())) {
				table.run.input(player, payload.what(), payload.value(), player.level().getServer().getTickCount());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.HandsPick.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			Table table = TABLES.get(new Spot(player.level().dimension(), payload.pos()));
			if (table != null && table.run == null && table.inReach(player) && enabled(player.level())) {
				table.choose(payload.delta() < 0 ? -1 : 1);
				hintPick(player, table);
			}
		});
	}

	/** The block a click at {@code hit} is about: a spit is the fire it stands over. */
	static BlockPos stationPos(ServerLevel level, BlockPos hit) {
		return level.getBlockState(hit).is(com.jaymoskitchen.cooking.spit.SpitBlock.SPIT) ? hit.below() : hit;
	}

	private static Spot spot(ServerLevel level, BlockPos pos) {
		return new Spot(level.dimension(), pos.immutable());
	}

	static @Nullable Table tableAt(ServerLevel level, BlockPos pos) {
		return TABLES.get(spot(level, pos));
	}

	private static Table tableFor(ServerLevel level, BlockPos pos) {
		return TABLES.computeIfAbsent(spot(level, pos), spot -> new Table(level, spot.pos(), level.getServer().getTickCount()));
	}

	private static boolean worktop(ServerLevel level, BlockPos pos, Direction face) {
		return Station.of(level, pos) != null || HandsBlocks.isStation(level.getBlockState(pos)) || Prep.isPrepSurface(level, pos, level.getBlockState(pos), face);
	}

	// --- clicks ---

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			// an ingredient held at a step recipe goes in the dish: the click is sent on and nothing else is done with it (it isn't eaten)
			return hand == InteractionHand.MAIN_HAND && !player.isSpectator() && clientOn && clientSteps.test(hit.getBlockPos())
				&& SequenceRun.ingredient(player.getMainHandItem()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(level instanceof ServerLevel server)
			|| !(player instanceof ServerPlayer cook) || !enabled(server)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = stationPos(server, hit.getBlockPos());
		int now = server.getServer().getTickCount();
		ItemStack held = cook.getMainHandItem();
		boolean sneaking = cook.isShiftKeyDown();
		Table table = tableAt(server, pos);
		if (table != null && table.run != null) {
			if (!table.inReach(cook) || table.run.over) {
				return InteractionResult.PASS;
			}
			// the click may finish the dish, and the station then has no run
			Run run = table.run;
			if (run.use(cook, held, sneaking, now)) {
				run.touch(cook, now);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		}
		if (!worktop(server, pos, hit.getDirection())) {
			return InteractionResult.PASS;
		}
		if (sneaking && !held.isEmpty()) {
			Table here = tableFor(server, pos);
			if (here.put(cook, held)) {
				hintPick(cook, here);
				return InteractionResult.SUCCESS_SERVER;
			}
			if (here.empty()) {
				TABLES.remove(spot(server, pos));
			}
			return InteractionResult.PASS;
		}
		if (table == null || table.placed.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (sneaking) {
			if (table.takeBack(cook)) {
				hintPick(cook, table);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		}
		Pick pick = table.chosen();
		if (pick == null || !FACTORIES.containsKey(pick.kind())) {
			return InteractionResult.PASS;
		}
		CookTools.Tool tool = pick.kind().tool();
		if (!tool.holds(held)) {
			if (CookTools.isTool(held)) {
				cook.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hands.wrong_tool", pickName(pick),
					Component.translatable(tool.translationKey())));
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		}
		if (!Runs.ready(table, pick, cook)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		if (pick.dish() != null && !cook.hasInfiniteMaterials() && server.getBlockEntity(pos) instanceof OvenBlockEntity oven
			&& !OvenHob.spend(oven, OvenHob.DISH_COST)) {
			cook.sendOverlayMessage(Component.translatable("message.jaymoskitchen.oven_low_heat"));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (pick.kind() == Pick.Kind.SEQUENCE) {
			// a step recipe uses nothing up yet: what was put on only chose the dish, and each ingredient goes in by hand
			table.clear(cook);
		} else {
			table.consume(pick, cook);
		}
		begin(table, pick, cook, now);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void begin(Table table, Pick pick, ServerPlayer starter, int now) {
		Run run = FACTORIES.get(pick.kind()).make(table, pick, starter, now);
		table.run = run;
		run.touch(starter, now);
		table.sync(now, true);
	}

	/** Whether this dish would be cooked by hand if started now. */
	public static boolean takesOver(ServerLevel level, Dish dish) {
		return enabled(level) && FACTORIES.containsKey(Pick.of(dish).kind());
	}

	/**
	 * A dish picked on the old screen (or in the cooking table) at {@code pos}: if the way it's cooked is done by hand
	 * here, its ingredients come from the pockets and kitchen as they always did (a step recipe's are only checked for:
	 * they go in by hand) and the dish starts at the block, no screen. False if the screens must handle it.
	 */
	public static boolean fromScreen(ServerPlayer player, BlockPos pos, Dish dish, boolean[] refused) {
		ServerLevel level = player.level();
		Pick pick = Pick.of(dish);
		if (!enabled(level) || !FACTORIES.containsKey(pick.kind())) {
			return false;
		}
		Table table = tableFor(level, pos);
		if (table.run != null || !Runs.ready(table, pick, player)) {
			refused[0] = true;
			return true;
		}
		if (level.getBlockEntity(pos) instanceof OvenBlockEntity oven && !player.hasInfiniteMaterials()
			&& !OvenHob.spend(oven, OvenHob.DISH_COST)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.oven_low_heat"));
			refused[0] = true;
			return true;
		}
		if (!player.hasInfiniteMaterials()) {
			if (!Kitchen.hasIngredients(Kitchen.sources(player, pos), dish.ingredients())) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.missing_ingredients"));
				refused[0] = true;
				return true;
			}
			// a step recipe's ingredients go in by hand, one step at a time
			if (pick.kind() != Pick.Kind.SEQUENCE) {
				Kitchen.takeIngredients(player, pos, dish.ingredients());
			}
		}
		begin(table, pick, player, level.getServer().getTickCount());
		return true;
	}

	/** The same for the prep board's screen: dough or a joint picked there starts at the block. False if the screen must handle it. */
	public static boolean fromPrep(ServerPlayer player, BlockPos pos, Prep.Recipe recipe, boolean[] refused) {
		ServerLevel level = player.level();
		Pick pick = new Pick(recipe == Prep.Recipe.JOINT ? Pick.Kind.JOINT : Pick.Kind.DOUGH, null, 0);
		if (!enabled(level) || !FACTORIES.containsKey(pick.kind())) {
			return false;
		}
		Table table = tableFor(level, pos);
		if (table.run != null || !Kitchen.inReach(player, pos)) {
			refused[0] = true;
			return true;
		}
		if (!player.hasInfiniteMaterials()) {
			if (!Kitchen.hasIngredients(Kitchen.sources(player, pos), pick.ingredients())) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.missing_ingredients"));
				refused[0] = true;
				return true;
			}
			Kitchen.takeIngredients(player, pos, pick.ingredients());
		}
		begin(table, pick, player, level.getServer().getTickCount());
		return true;
	}

	private static InteractionResult attack(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			if (!clientOn || !clientRuns.test(pos)) {
				return InteractionResult.PASS;
			}
			// SUCCESS is what sends the click on to the server (a FAIL stops here); the block is never mined either way
			return clientPress.getAsBoolean() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer cook) || !enabled(server)) {
			return InteractionResult.PASS;
		}
		Table table = tableAt(server, stationPos(server, pos));
		if (table == null || table.run == null || table.run.over || !table.inReach(cook)) {
			return InteractionResult.PASS;
		}
		int now = server.getServer().getTickCount();
		if (table.run.attack(cook, cook.getMainHandItem(), now)) {
			table.run.touch(cook, now);
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	// --- hints ---

	static Component pickName(Pick pick) {
		return pick.dish() != null ? Component.translatable(ModItems.dish(pick.dish()).getDescriptionId())
			: Component.translatable("hands.pick." + pick.kind().name().toLowerCase());
	}

	/** Whether cooking is done by hand here (the server's game rule, as the client was told). */
	public static boolean on(Level level) {
		return level instanceof ServerLevel server ? enabled(server) : clientOn;
	}

	/** Sneak and an empty hand at a station: what is on it and what it would make (there is no screen to open). */
	public static void hintAt(ServerPlayer player, BlockPos pos) {
		Table table = tableAt(player.level(), stationPos(player.level(), pos));
		if (table == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hands.empty"));
		} else if (table.run == null) {
			hintPick(player, table);
		}
	}

	/** What the station would make now, in the action-bar line. */
	static void hintPick(ServerPlayer player, Table table) {
		Pick pick = table.chosen();
		if (pick == null) {
			player.sendOverlayMessage(Component.translatable(table.placed.isEmpty() ? "message.jaymoskitchen.hands.empty"
				: "message.jaymoskitchen.hands.nothing_yet", table.placed.size()));
			return;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hands.picked", pickName(pick),
			Math.min(table.pickIndex, table.picks.size() - 1) + 1, table.picks.size(),
			Component.translatable(pick.kind().tool().translationKey())));
	}

	// --- the tick ---

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % MODE_CHECK == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean on = enabled(player.level());
				if (MODE_SENT.put(player.getUUID(), on) != Boolean.valueOf(on)) {
					ServerPlayNetworking.send(player, new Payloads.HandsMode(on));
				}
			}
		}
		Iterator<Table> it = TABLES.values().iterator();
		while (it.hasNext()) {
			Table table = it.next();
			Run run = table.run;
			if (run != null) {
				Station here = table.station();
				boolean gone = run.dish != null && run.kind() != Pick.Kind.SUSHI && (run.station == null || here == null || !run.station.cooks(table.level, table.pos, run.dish));
				if (run.over) {
					table.run = null;
				} else if (gone || run.lostCooks()) {
					run.leave();
				} else {
					run.tick(now);
				}
				if (table.run == run && !run.over) {
					table.sync(now, false);
				}
			} else if (table.verdictUntil > 0 && now >= table.verdictUntil) {
				table.verdictUntil = 0;
				table.changed();
			}
			if (table.run == null && !table.placed.isEmpty() && now % 20 == 0) {
				boolean near = false;
				for (ServerPlayer player : table.level.players()) {
					near |= table.inReach(player);
				}
				if (near) {
					table.lastNear = now;
					table.sync(now, false);
				} else if (now - table.lastNear > IDLE_TICKS) {
					table.clear(null);
				}
			}
			if (table.empty()) {
				table.sync(now, true);
				it.remove();
			}
		}
	}
}
