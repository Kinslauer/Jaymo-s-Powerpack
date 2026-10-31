package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.War;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.224) Andy (2026-10-26): "War wagers can only be done with bookie npcs in speakeasies." Every den keeps a bookie
 * ({@link Underworld}). Right-click him and he reads you his book: the wars still taking wagers (each side at his odds,
 * by the armies and sellswords behind it) and, while the pit's card is called, the next bout ({@link FightPit}). Hold
 * the emeralds you mean to stake and click a line. Winnings come to you when the war or the bout is decided
 * ({@link GreatWars#owe}).
 *
 * <p>(0.7.449) Andy (2026-10-31): "if you pay the bookie outside of the speakeasy, he should lead you to it." Now and
 * then, by day, the bookie goes up into town. Out there he won't open his book or take a coin: he says to follow him
 * and walks you to the hatch, waiting when you fall behind, and goes down first. But "guards overhearing talk of the
 * speakeasy will begin to crackdown, so deals out in the open are frowned upon and rare": asked with a guard in earshot,
 * he's never heard of any den, and the guard has ({@link Underworld#crackDown}); while the watch cracks down he leads
 * nobody. Come evening he walks home himself.
 */
final class Bookies {
	/** The bookie (scoreboard tag). */
	static final String TAG = "jaymoskingdoms.bookie";
	/** A bet is placed within this of a bookie. */
	static final double REACH = 6.0;
	/** War odds: the chance given each side is its share of the armies squeezed into this range; the house keeps {@link FightPit#HOUSE}. */
	static final double LEAST_CHANCE = 0.15, CHANCE_SPAN = 0.7;
	static final double WAR_SHORTEST = 1.2, WAR_LONGEST = 5.0;

	/** (0.7.449) Out in town: from this time of day until this; then home. */
	static final long OUT_FROM = 1000L, OUT_UNTIL = 11000L;
	/** (0.7.449) Leading: his pace; he waits for one this far behind; gives up on one this far off, or after this long (ticks). */
	static final float PACE = 0.6F;
	static final double WAIT_RANGE = 7.0, LOST_RANGE = 32.0, HATCH_REACH = 1.8;
	static final long WALK_TICKS = 4800L, NAG_TICKS = 100L;
	/** (0.7.449) Someone this close sees him go home (he walks it); with nobody about, he's simply home. */
	static final double SEEN_RANGE = 32.0;

	/** (0.7.449) A bookie's walk back to the den: leading {@code player} (or on his own). */
	static final class Walk {
		@Nullable UUID player;
		final BlockPos hatch;
		final long started;
		long nagged;

		Walk(@Nullable UUID player, BlockPos hatch, long started) {
			this.player = player;
			this.hatch = hatch;
			this.started = started;
		}
	}

	/** (0.7.449) Bookie → his walk to the den. */
	private static final Map<UUID, Walk> WALKS = new HashMap<>();

	private Bookies() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Bookies::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WALKS.clear());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("bookie")
			.then(Commands.literal("war").then(Commands.argument("war", IntegerArgumentType.integer(0))
				.then(Commands.argument("side", IntegerArgumentType.integer(0)).executes(Bookies::betWar))))
			.then(Commands.literal("bout").then(Commands.argument("fighter", IntegerArgumentType.integer(0, 1)).executes(Bookies::betBout)))
			.then(Commands.literal("fight").executes(Bookies::fight))
			.then(Commands.literal("patch")
				.then(Commands.literal("yes").executes(c -> patch(c, true)))
				.then(Commands.literal("no").executes(c -> patch(c, false))))));
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
		if (outside(level, player, bookie)) {
			return;
		}
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

	static MutableComponent click(Component text, String command) {
		return text.copy().withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.translatable("bookie.jaymoskingdoms.hover"))));
	}

	/** The bookie within reach of {@code player} (null, with a word, if there's none). */
	private static @Nullable Villager reach(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		Villager bookie = near(player.level(), player.getBoundingBox().inflate(REACH));
		if (bookie == null) {
			c.getSource().sendFailure(Component.translatable("bookie.jaymoskingdoms.none"));
			return null;
		}
		// (0.7.449) no money changes hands in the street
		return outside(player.level(), player, bookie) ? null : bookie;
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

	/** {@code player} answers the pit's attendant's offer ({@link FightPit#answer}). */
	private static int patch(CommandContext<CommandSourceStack> c, boolean yes) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		player.sendSystemMessage(FightPit.answer(player, yes).copy().withStyle(ChatFormatting.GRAY));
		return 1;
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

	// ---- (0.7.449) out in town -----------------------------------------------------------------------------------------

	/** The den {@code bookie} keeps the book in, if he's out of it (null if he's home, or has none). */
	private static Underworld.@Nullable Den away(ServerLevel level, Villager bookie) {
		Faction f = Theft.lawAt(level, bookie.blockPosition());
		Underworld.Den d = f == null ? null : Underworld.builtDen(level.getServer(), f);
		return d == null || Speakeasy.room(d.hatch()).inflate(1.0).contains(bookie.position()) ? null : d;
	}

	/**
	 * {@code player} talks business with {@code bookie}: if he's out in the street he won't, but leads them to the den
	 * (or, a guard in earshot, knows of no den at all). True if he's out.
	 */
	static boolean outside(ServerLevel level, ServerPlayer player, Villager bookie) {
		Underworld.Den d = away(level, bookie);
		if (d == null) {
			return false;
		}
		Faction f = Factions.get(level.getServer(), d.realm());
		RandomSource random = level.getRandom();
		bookie.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Walk walk = WALKS.get(bookie.getUUID());
		if (walk != null && player.getUUID().equals(walk.player)) {
			Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.street.this_way." + random.nextInt(Underworld.LINES)), Speech.GOLD);
			return true;
		}
		List<Worker> guards = f == null ? List.of() : Underworld.guards(level, f, bookie.getBoundingBox().inflate(Underworld.GUARD_EARSHOT));
		if (!guards.isEmpty()) {
			Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.street.hush." + random.nextInt(Underworld.LINES)), Speech.WHITE);
			Underworld.crackDown(level, f, guards.getFirst().mob(), player);
			return true;
		}
		if (Underworld.crackdown(level.getServer(), d.realm())) {
			Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.street.lying_low." + random.nextInt(Underworld.LINES)), Speech.WHITE);
			return true;
		}
		Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.street.follow." + random.nextInt(Underworld.LINES)), Speech.GOLD);
		WALKS.put(bookie.getUUID(), new Walk(player.getUUID(), d.hatch(), level.getGameTime()));
		return true;
	}

	/** Whether {@code bookie} is walking back to his den (leading someone, or not). */
	static boolean walking(UUID bookie) {
		return WALKS.containsKey(bookie);
	}

	/**
	 * (From {@link Underworld}'s home check) {@code bookie} is out of {@code d}: whether he may stay out. By day he may;
	 * after, he walks home if anyone's about to see, or is simply there if not (false).
	 */
	static boolean outAndAbout(ServerLevel level, Villager bookie, Underworld.Den d) {
		long t = Royals.timeOfDay(level);
		if (t >= OUT_FROM && t < OUT_UNTIL && !Underworld.crackdown(level.getServer(), d.realm())) {
			return true;
		}
		if (level.getNearestPlayer(bookie, SEEN_RANGE) == null) {
			return false;
		}
		WALKS.put(bookie.getUUID(), new Walk(null, d.hatch(), level.getGameTime()));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (WALKS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 10 != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Iterator<Map.Entry<UUID, Walk>> it = WALKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Walk> e = it.next();
			Walk walk = e.getValue();
			if (!(level.getEntity(e.getKey()) instanceof Villager bookie) || !bookie.isAlive() || Downed.isDowned(bookie) || now - walk.started > WALK_TICKS) {
				it.remove();
				continue;
			}
			ServerPlayer player = walk.player == null ? null : server.getPlayerList().getPlayer(walk.player);
			if (walk.player != null && (player == null || player.level() != level || player.distanceTo(bookie) > LOST_RANGE)) {
				// they didn't follow: he goes home on his own
				if (player != null && player.level() == level) {
					Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.street.lost." + random.nextInt(Underworld.LINES)), Speech.WHITE);
				}
				walk.player = null;
				player = null;
			}
			BlockPos hatch = walk.hatch;
			Vec3 over = Vec3.atBottomCenterOf(hatch.above());
			boolean behind = player != null && player.distanceTo(bookie) > WAIT_RANGE;
			if (bookie.position().subtract(over).horizontalDistanceSqr() <= HATCH_REACH * HATCH_REACH && Math.abs(bookie.getY() - over.y) < 2.0 && !behind) {
				// there: up with the hatch, and down he goes first
				Vec3 spot = Speakeasy.bookieSpot(hatch);
				level.playSound(null, hatch, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
				if (player != null) {
					Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.street.down." + random.nextInt(Underworld.LINES)), Speech.GOLD);
				}
				bookie.getNavigation().stop();
				bookie.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				bookie.snapTo(spot.x, spot.y, spot.z, bookie.getYRot(), 0.0F);
				it.remove();
				continue;
			}
			if (behind) {
				// he waits for them
				bookie.getNavigation().stop();
				bookie.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				bookie.getLookControl().setLookAt(player, 30.0F, 30.0F);
				if (now - walk.nagged >= NAG_TICKS) {
					walk.nagged = now;
					Speech.tell(level, bookie, player, Component.translatable("bookie.jaymoskingdoms.street.keep_up." + random.nextInt(Underworld.LINES)), Speech.WHITE);
				}
				continue;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(bookie, Construction.leg(level, bookie, hatch.above()), PACE, 1);
		}
	}
}
