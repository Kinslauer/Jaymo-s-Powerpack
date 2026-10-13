package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.39) The townsfolk come to you. Every so often one of a settlement's people who can see you (a villager, MCA's or
 * plain, a child, or one of its guards) walks over and starts something, aloud over their head ({@link Speech}): a
 * question, a quest, a request, a bit of news. When they're asking, right-click them to answer (a quest opens their
 * quests); walk off and they take it as they will.
 *
 * <p>What they start suits who they are and what they know of you ({@link #pick}):
 * <ul>
 * <li>quests: a giver with work (the gold !) offers it; one whose errand you carry asks how it goes;</li>
 * <li>a crime of yours they've heard of ({@link #suspect}): confess and pay, deny it, or threaten them;</li>
 * <li>a guard halts a stranger and asks their business; a guard warns you in at nightfall;</li>
 * <li>questions for a stranger (where you're bound, what you think of the place), answered warmly, plainly or rudely;</li>
 * <li>a hungry one asks for food; a craftsman offers a bargain from their trade; a friend brings a small gift;</li>
 * <li>a realm at war asks whether you'll stand with it; gossip; a friend's greeting;</li>
 * <li>children: tag, or whether you're a knight.</li>
 * </ul>
 * Answers move hearts (MCA) and reputation a little. It's paced: one approach per player every few minutes, the same
 * villager once a day, never mid-fight, never from people who hate you (their guards hunt you instead).
 *
 * <p>(0.6.2) Guards have more business than halting strangers (which they now do once in three days per settlement,
 * and a guard comes over at most once in {@link #GUARD_GAP}), all shaped by their king's temper ({@link Tempers}): a
 * search of your pack, a toll, a bout for a wager, and a test of your loyalty. How you answer can cost you emeralds,
 * good name, a slap, or the guards' blades (a tyrant's and a paranoid king's guards arrest those who defy them).
 *
 * <p>(0.6.2) Missed it? For a little while after they've spoken (and while a bit of news is still being told), right-click
 * them to beg their pardon (they say it again; a question you walked off on is asked again) or to ask them to tell you
 * more (each line has a follow-up). The answer screen offers "tell me more" too.
 */
public final class Approaches {
	/** How often players are looked at for someone to come over (ticks); how far away that someone can be (blocks). */
	static final int SCAN_TICKS = 40;
	static final double SEEK_RANGE = 16.0;
	/** Chance a scan that could start an approach does (so it doesn't come the moment the wait's over). */
	static final float APPROACH_CHANCE = 0.3F;
	/** (0.5.51) How often gossip is about a butcher (good, bad, ours or far off), when Jaymo's Kitchen is there. */
	static final float BUTCHER_GOSSIP_CHANCE = 0.5F;
	/** Between approaches to one player (ticks): at least this, plus up to this; the first after joining. */
	static final int PLAYER_GAP = 20 * 90, PLAYER_GAP_SPREAD = 20 * 150, FIRST_GAP = 20 * 45;
	/** The same villager comes to the same player at most once in this long (ticks: a day). */
	static final long MET_TICKS = 24000L;
	/** (0.6.2) A guard halts a stranger once in this long per settlement (ticks: three days); a guard comes over at most this often. */
	static final long HALT_TICKS = 72000L;
	static final int GUARD_GAP = 20 * 60 * 8;
	/**
	 * (0.6.2) Guards' other business, by their king's temper (TYRANT, NOBLE, WARLORD, PARANOID, REVELER; last, a place with
	 * no ruler): how keen they are to search you, ask a toll, offer a bout, test your loyalty.
	 */
	static final int[] SEARCH_W = {3, 1, 1, 4, 1, 1}, TOLL_W = {4, 1, 2, 2, 2, 1}, BOUT_W = {1, 1, 4, 1, 3, 1}, LOYALTY_W = {3, 1, 1, 4, 1, 0};
	/** The toll (emeralds, to the treasury), by temper; the bribe a search can be waved off with; a bout's wager. */
	static final int[] TOLLS = {3, 1, 2, 2, 1, 1};
	static final int BRIBE = 2, WAGER = 2;
	/** How long defying a guard who arrests you keeps you wanted (ticks). */
	static final long ARREST_TICKS = 1200L;
	/** Good name, by temper: a clean search, refusing one, refusing a toll, winning a bout, praising the king, keeping quiet, speaking ill. */
	static final int[] SEARCH_CLEAN_REP = {1, 3, 3, 2, 2, 2}, SEARCH_REFUSE_REP = {-6, -2, -4, -6, 0, -2}, TOLL_REFUSE_REP = {-6, -1, -3, -5, 0, -2},
		BOUT_WON_REP = {2, 2, 5, 1, 3, 2}, PRAISE_REP = {3, 1, 2, 2, 2, 1}, SILENT_REP = {-1, 0, 0, -2, 0, 0}, ILL_REP = {-8, -2, -6, -8, -1, -2};
	/** Stolen goods found on a search; a bribe a guard won't take; paying a toll; insulting a guard; haggling and failing. */
	static final int SEARCH_FOUND_REP = -4, BRIBE_REFUSED_REP = -6, TOLL_PAID_REP = 1, REVELER_TOLL_REP = 3, INSULT_REP = -4, HAGGLE_FAILED_REP = -2;
	/** Haggling a toll down to half: the chance, by temper. */
	static final float[] HAGGLE_CHANCE = {0.25F, 0.6F, 0.4F, 0.4F, 0.75F, 0.5F};
	/** A bout: the chance to win (base, per armour point, per point of attack damage, never under or over). */
	static final float BOUT_BASE = 0.3F, BOUT_ARMOUR = 0.015F, BOUT_ATTACK = 0.03F, BOUT_MIN = 0.15F, BOUT_MAX = 0.85F;
	/** A guard's slap (only above this health). */
	static final float SLAP = 1.0F, SLAP_SPARES = 10.0F;
	/** How sure a paranoid king's guard is that praise is honest. */
	static final float PARANOID_BELIEVES = 0.5F;
	/** Walking over: how close is close enough to talk; how long before they give up; how far off the player may get first. */
	static final double ARRIVE_RANGE = 2.6, GIVE_UP_RANGE = 28.0;
	static final int WALK_TICKS = 20 * 20;
	/** Walking speed: a villager's (brain), a folk guard's (navigation). */
	static final float VILLAGER_SPEED = 0.6F;
	static final double FOLK_SPEED = 0.9;
	/** Asked: how long they wait for an answer; how far you can step off before they take it you've left. */
	static final int ASK_TICKS = 20 * 25;
	static final double LEAVE_RANGE = 9.0;
	/** Said their piece (news, a gift, a warning): how long they stay before going back about their day. */
	static final int LINGER_TICKS = 20 * 4;
	/** How far their words carry; how close you must be to answer. */
	static final double SPEECH_RANGE = 14.0, TALK_RANGE = 8.0;
	/** A crime of yours is held against you this long (ticks: two days). */
	static final long SUSPECT_TICKS = 48000L;
	/** (0.6.2) After they've had their say, how long you can still beg their pardon (ticks); how long they stay to repeat it. */
	static final int PARDON_TICKS = 20 * 45, PARDON_HOLD_TICKS = 20 * 15;
	/** A player hurt by a mob this recently is left alone (ticks). */
	static final int FIGHT_TICKS = 200;

	/** Reputation below which (but not hostile) the folk eye you warily; at or above which a guard won't halt you. */
	static final int WARY_REP = -30, KNOWN_REP = 100;
	/** A friend: MCA hearts (or reputation, without MCA) at which they bring gifts, and greet you by name. */
	static final int GIFT_HEARTS = 40, GIFT_REP = 250, GREET_HEARTS = 15, GREET_REP = 120;

	/** Confessing to a crime: the fine (emeralds) and what it wins back. Denying: the chance you're believed, and what a lie costs. */
	static final int FINE = 5, CONFESS_REP = 12, LIE_REP = -6, BELIEVED_REP = 2;
	static final float BELIEVED_CHANCE = 0.45F, BELIEVED_SWORN = 0.25F;
	/** Threatening them; brushing off a guard; walking off on a guard or an accuser. */
	static final int THREATEN_REP = -12, RUDE_GUARD_REP = -5, IGNORE_GUARD_REP = -3, IGNORE_ACCUSER_REP = -5;
	/** Answering a question: warmly, rudely (hearts, reputation). */
	static final int WARM_HEARTS = 2, WARM_REP = 1, RUDE_HEARTS = -3, RUDE_REP = -2;
	/** Giving food to someone hungry; standing with a realm at war; playing with a child; buying a bargain. */
	static final int FED_HEARTS = 4, FED_REP = 4, STAND_REP = 4, PLAY_HEARTS = 2, BOUGHT_HEARTS = 1;
	/** A bargain costs this much less for a friend (emeralds, never under one). */
	static final int FRIEND_DISCOUNT = 1;
	/** How far a guard looks for someone with work, to send a stranger to. */
	static final double WORK_SEARCH = 48.0;

	/** What they come over about. */
	static final int QUEST_OFFER = 0, QUEST_CHECK = 1, ACCUSE = 2, HALT = 3, QUESTION = 4, HUNGRY = 5, BARGAIN = 6, GIFT = 7, WAR = 8,
		GOSSIP = 9, GREET = 10, CHILD = 11, WARN = 12, SEARCH = 13, TOLL = 14, BOUT = 15, LOYALTY = 16;
	static final String[] TOPICS = {"quest_offer", "quest_check", "accuse", "halt", "question", "hungry", "bargain", "gift", "war", "gossip",
		"greet", "child", "warn", "search", "toll", "bout", "loyalty"};
	/** How many ways each topic has of opening (lang keys {@code talk.jaymoskingdoms.<topic>.<n>}); 0 for the guards' own, one per temper. */
	static final int[] OPENINGS = {3, 3, 3, 3, 0, 3, 3, 3, 2, 10, 3, 3, 3, 0, 0, 0, 0};
	/** (0.6.2) Temper keys, in {@link Tempers.Temper} order, then a place with no ruler of its own. */
	static final String[] TEMPERS = {"tyrant", "noble", "warlord", "paranoid", "reveler", "plain"};
	static final int TYRANT = 0, NOBLE = 1, WARLORD = 2, PARANOID = 3, REVELER = 4, PLAIN = 5;
	/** (0.5.52) Lines a gossip has about a player whose alignment they've heard of, per pillar. */
	static final int GOSSIP_ABOUT_YOU = 3;
	/** Stranger's questions (each with a warm, a plain and a rude answer, and a reply to each). */
	static final int QUESTIONS = 8;
	/** A child's: play tag, are you a knight (you're armoured), can I see your sword (you've one in hand). */
	static final int CHILD_TAG = 0, CHILD_KNIGHT = 1, CHILD_SWORD = 2;

	/** One of a settlement's people on their way to a player, or talking with them. */
	private static final class Approach {
		final Mob mob;
		final UUID player;
		final int topic, faction;
		long until;
		boolean arrived, done;
		/** Which opening (or question, or child's game) it is. */
		int variant;
		/** What they said on arriving (shown again on the answer screen); (0.6.2) what more they'd say if asked, and its colour. */
		Component line = Component.empty(), more = Component.empty();
		int color = Speech.WHITE;
		/** (0.6.2) They've told you more; you answered them (nothing to beg pardon for); you walked off on them once already. */
		boolean elaborated, answered, snubbed;
		/** (0.6.2) Till when you can still beg their pardon. */
		long pardonUntil;
		/** A bargain's goods and price; a war's enemy. */
		ItemStack goods = ItemStack.EMPTY;
		int price;
		String enemy = "";

		Approach(Mob mob, UUID player, int topic, int faction, long until) {
			this.mob = mob;
			this.player = player;
			this.topic = topic;
			this.faction = faction;
			this.until = until;
		}
	}

	/** One answer on the screen. */
	private record Option(Component label, boolean enabled) {
	}

	/** Player → their approach (one at a time). */
	private static final Map<UUID, Approach> ACTIVE = new HashMap<>();
	/** (0.6.2) Player → the last one who spoke to them unanswered, while they can still beg their pardon. */
	private static final Map<UUID, Approach> RECENT = new HashMap<>();
	/** (0.6.2) Player → the "pardon?" screen they have open: whose, and what each button is. */
	private static final Map<UUID, Pardon> PARDONING = new HashMap<>();

	/** What a button on the "pardon?" screen does. */
	private enum Button {
		PARDON, MORE, BYE
	}

	private record Pardon(Approach approach, List<Button> buttons) {
	}
	/** Player → when someone may next come over (game time). */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	/** "villager/player" or "halt/faction/player" → when they last met (game time). */
	private static final Map<String, Long> MET = new HashMap<>();
	/** Player → faction → when their latest crime there was (seen, or noticed later). */
	private static final Map<UUID, Map<Integer, Long>> SUSPECTS = new HashMap<>();

	/** A craftsman's bargain, by profession: goods, how many, price (emeralds). */
	private record Deal(Item item, int count, int price) {
	}

	private static final Map<String, Deal> DEALS = Map.ofEntries(
		Map.entry("farmer", new Deal(Items.BREAD, 10, 2)),
		Map.entry("fisherman", new Deal(Items.COOKED_COD, 8, 2)),
		Map.entry("butcher", new Deal(Items.COOKED_PORKCHOP, 8, 3)),
		Map.entry("librarian", new Deal(Items.BOOKSHELF, 3, 3)),
		Map.entry("cleric", new Deal(Items.EXPERIENCE_BOTTLE, 4, 4)),
		Map.entry("armorer", new Deal(Items.IRON_INGOT, 6, 4)),
		Map.entry("weaponsmith", new Deal(Items.IRON_SWORD, 1, 3)),
		Map.entry("toolsmith", new Deal(Items.IRON_PICKAXE, 1, 4)),
		Map.entry("cartographer", new Deal(Items.COMPASS, 1, 3)),
		Map.entry("fletcher", new Deal(Items.ARROW, 24, 2)),
		Map.entry("shepherd", new Deal(Items.WOOL.pick(DyeColor.WHITE), 12, 2)),
		Map.entry("leatherworker", new Deal(Items.LEATHER, 8, 3)),
		Map.entry("mason", new Deal(Items.BRICKS, 24, 2)));

	/** Small gifts a friend brings. */
	private static final List<Item> GIFTS = List.of(Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.BREAD, Items.APPLE, Items.COOKIE,
		Items.PUMPKIN_PIE, Items.EMERALD, Items.HONEY_BOTTLE, Items.CAKE);

	/** Food nobody hands over to the hungry (it's bad for them, or too dear). */
	private static final Set<Item> NOT_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT,
		Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.SUSPICIOUS_STEW);

	private Approaches() {
	}

	static void register() {
		TalkNet.register();
		ServerPlayNetworking.registerGlobalReceiver(TalkNet.Answer.TYPE, (payload, context) -> answer(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Approaches::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			Approach a = ACTIVE.get(sp.getUUID());
			if (a == null || a.mob != entity || !a.arrived || a.done) {
				// (0.6.2) someone who spoke to you a moment ago: beg their pardon, or ask them to say more
				Approach r = RECENT.get(sp.getUUID());
				if (r == null || r.mob != entity || sp.isShiftKeyDown() || !(world instanceof ServerLevel level) || level.getGameTime() > r.pardonUntil
					|| !r.mob.isAlive() || r.mob.getTarget() != null || talking(r.mob) || r.mob.distanceTo(sp) > TALK_RANGE) {
					return InteractionResult.PASS;
				}
				pardonScreen(sp, r);
				return InteractionResult.SUCCESS;
			}
			if (!asks(a.topic)) {
				// (0.6.2) still telling you their news
				if (sp.isShiftKeyDown()) {
					return InteractionResult.PASS;
				}
				pardonScreen(sp, a);
				return InteractionResult.SUCCESS;
			}
			if (a.topic == QUEST_OFFER || a.topic == QUEST_CHECK) {
				// (their quests, straight away: the screen holds them still from here)
				a.done = true;
				a.answered = true;
				Quests.open(sp, a.mob);
			} else {
				ask(sp, a);
			}
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RECENT.remove(handler.player.getUUID());
			PARDONING.remove(handler.player.getUUID());
			Approach a = ACTIVE.remove(handler.player.getUUID());
			if (a != null) {
				release(a);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			RECENT.clear();
			PARDONING.clear();
			NEXT.clear();
			MET.clear();
			SUSPECTS.clear();
		});
	}

	// ---- what others tell us ---------------------------------------------------------------------------------------

	/** (From {@link Theft}) {@code player} did a crime against {@code faction}, seen or noticed: its people will ask about it. */
	static void suspect(ServerPlayer player, int faction, long now) {
		SUSPECTS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(faction, now);
	}

	private static boolean suspected(ServerPlayer player, int faction, long now) {
		Map<Integer, Long> mine = SUSPECTS.get(player.getUUID());
		Long at = mine == null ? null : mine.get(faction);
		return at != null && now - at < SUSPECT_TICKS;
	}

	private static void clearSuspicion(ServerPlayer player, int faction) {
		Map<Integer, Long> mine = SUSPECTS.get(player.getUUID());
		if (mine != null) {
			mine.remove(faction);
		}
	}

	/** Whether {@code mob} is on its way to someone, or talking with them (they're left be by other work that asks). */
	static boolean talking(Mob mob) {
		for (Approach a : ACTIVE.values()) {
			if (a.mob == mob) {
				return true;
			}
		}
		return false;
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty() && server.getTickCount() % SCAN_TICKS != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Iterator<Approach> it = ACTIVE.values().iterator(); it.hasNext();) {
			Approach a = it.next();
			if (!step(server, level, a, now)) {
				release(a);
				it.remove();
				if (a.arrived && !a.answered) {
					// (0.6.2) unanswered: for a while you can still ask what they said
					a.pardonUntil = now + PARDON_TICKS;
					RECENT.put(a.player, a);
				}
			}
		}
		if (server.getTickCount() % SCAN_TICKS == 0) {
			for (ServerPlayer player : level.players()) {
				consider(level, player, now, -1, false);
			}
			if (server.getTickCount() % (SCAN_TICKS * 300) == 0) {
				MET.values().removeIf(t -> now - t > HALT_TICKS);
				RECENT.values().removeIf(r -> now > r.pardonUntil);
				SUSPECTS.values().forEach(m -> m.values().removeIf(t -> now - t > SUSPECT_TICKS));
				SUSPECTS.values().removeIf(Map::isEmpty);
			}
		}
	}

	/** One tick of an approach; false when it's over. */
	private static boolean step(MinecraftServer server, ServerLevel level, Approach a, long now) {
		ServerPlayer player = server.getPlayerList().getPlayer(a.player);
		if (player == null || !a.mob.isAlive() || a.mob.isRemoved() || player.level() != level || !player.isAlive() || player.isSpectator()
			|| a.mob.getTarget() != null) {
			return false;
		}
		double d = a.mob.distanceTo(player);
		if (!a.arrived) {
			if (now > a.until || d > GIVE_UP_RANGE || a.mob.isSleeping() || fighting(player)) {
				return false;
			}
			if (d <= ARRIVE_RANGE) {
				a.arrived = true;
				a.until = now + (asks(a.topic) ? ASK_TICKS : LINGER_TICKS);
				hold(a.mob, player);
				arrive(level, a, player);
			} else if (now % 10 == 0) {
				walk(a.mob, player);
			}
			return true;
		}
		if (a.done) {
			return false;
		}
		if (now > a.until || d > LEAVE_RANGE) {
			if (asks(a.topic)) {
				walkedOff(level, a, player);
			}
			return false;
		}
		hold(a.mob, player);
		return true;
	}

	private static boolean fighting(ServerPlayer player) {
		return player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < FIGHT_TICKS;
	}

	private static void walk(Mob mob, ServerPlayer player) {
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(player, true), VILLAGER_SPEED, 2);
		} else {
			mob.getNavigation().moveTo(player, FOLK_SPEED);
		}
	}

	private static void hold(Mob mob, ServerPlayer player) {
		if (mob instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (!mob.isPassenger()) {
			mob.getNavigation().stop();
		}
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
	}

	private static void release(Approach a) {
		if (a.mob instanceof Villager v && v.isAlive()) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}

	/** Topics that wait for an answer (the rest say their piece and go). */
	static boolean asks(int topic) {
		return topic != GIFT && topic != GOSSIP && topic != GREET && topic != WARN;
	}

	// ---- choosing who comes, and why --------------------------------------------------------------------------------------

	/**
	 * Someone near {@code player} may come over. {@code forced} (the test command): now, whatever the wait, with
	 * {@code topic} if it's not -1 (and the nearest who could speak of it).
	 */
	private static boolean consider(ServerLevel level, ServerPlayer player, long now, int topic, boolean forced) {
		UUID id = player.getUUID();
		if (ACTIVE.containsKey(id) || !player.isAlive() || player.isSpectator() || player.isSleeping() || fighting(player)
			|| (!forced && player.containerMenu != player.inventoryMenu)) {
			return false;
		}
		if (!forced) {
			Long next = NEXT.get(id);
			if (next == null) {
				NEXT.put(id, now + FIRST_GAP);
				return false;
			}
			if (now < next || level.getRandom().nextFloat() > APPROACH_CHANCE) {
				return false;
			}
		}
		RandomSource random = level.getRandom();
		List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SEEK_RANGE), e -> e instanceof Villager || e instanceof Folk);
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		Mob best = null;
		Faction bestFaction = null;
		int bestTopic = -1, total = 0;
		for (Mob mob : near) {
			if (!free(mob) || (!forced && met(mob, player, now)) || !mob.hasLineOfSight(player)) {
				continue;
			}
			Faction f = home(level, mob);
			if (f == null) {
				continue;
			}
			int[] choice = pick(level, mob, f, player, now, random, topic);
			if (choice == null) {
				continue;
			}
			if (forced) {
				best = mob;
				bestFaction = f;
				bestTopic = choice[0];
				break;
			}
			// (a weighted draw over everyone who'd come, each with what they'd come about)
			total += choice[1];
			if (random.nextInt(total) < choice[1]) {
				best = mob;
				bestFaction = f;
				bestTopic = choice[0];
			}
		}
		if (best == null) {
			return false;
		}
		Approach a = new Approach(best, id, bestTopic, bestFaction.id(), now + WALK_TICKS);
		prepare(level, a, player, bestFaction, random);
		ACTIVE.put(id, a);
		MET.put(best.getUUID() + "/" + id, now);
		if (bestTopic == HALT) {
			MET.put("halt/" + bestFaction.id() + "/" + id, now);
		}
		if (guard(best)) {
			MET.put("guard/" + id, now);
		}
		NEXT.put(id, now + PLAYER_GAP + random.nextInt(PLAYER_GAP_SPREAD));
		walk(best, player);
		return true;
	}

	private static boolean met(Mob mob, ServerPlayer player, long now) {
		Long at = MET.get(mob.getUUID() + "/" + player.getUUID());
		return at != null && now - at < MET_TICKS;
	}

	/** Whether {@code mob} is free to come over: awake, not working, fighting, riding, or already talking to someone. */
	private static boolean free(Mob mob) {
		if (!mob.isAlive() || mob.isSleeping() || mob.isPassenger() || mob.isVehicle() || mob.getTarget() != null || mob.isNoAi()
			|| Theft.rioting(mob) || Quests.listening(mob) || talking(mob) || Rickshaws.onDuty(mob)) {
			return false;
		}
		if (mob instanceof PathfinderMob pm && McaCompat.busy(pm) || Hermits.is(mob)) {
			// (0.5.62: a floating island's hermit greets visitors their own way)
			return false;
		}
		if (mob instanceof Folk folk) {
			return folk.role() == Folk.GUARD || folk.role() == Folk.ARCHER;
		}
		// (a worker at their work stays at it)
		return !(mob instanceof Worker w) || w.role() < 0 || w.role() == Folk.GUARD || Workers.IDLE.contains(w.task());
	}

	private static boolean guard(Mob mob) {
		return mob instanceof Folk || (mob instanceof Worker w && w.role() == Folk.GUARD);
	}

	/** The settlement {@code mob} belongs to (a guard's own; a villager's, children too), or null. */
	private static @Nullable Faction home(ServerLevel level, Mob mob) {
		MinecraftServer server = level.getServer();
		Faction f;
		if (mob instanceof Folk folk) {
			f = Factions.get(server, folk.factionId());
		} else {
			if (mob instanceof Villager v) {
				String job = profession(v);
				if (job.equals("outlaw") || job.equals("cultist") || job.equals("nitwit")) {
					return null;
				}
			}
			f = Factions.around(server, level, mob.blockPosition(), Villages.VILLAGE_RADIUS);
			if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
				f = Factions.at(server, level, mob.blockPosition());
			}
		}
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	private static String profession(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
	}

	/** Night (the dead walk): only guards come over, to send you in. */
	private static boolean night(ServerLevel level) {
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return t >= 12500L && t < 23500L;
	}

	/**
	 * What {@code mob} would come over to {@code player} about, and how keen they are: {topic, weight}; or null if
	 * nothing. With {@code only} not -1, that topic or nothing.
	 */
	private static int @Nullable [] pick(ServerLevel level, Mob mob, Faction f, ServerPlayer player, long now, RandomSource random, int only) {
		MinecraftServer server = level.getServer();
		int rep = Reputation.get(player, f.id());
		if (rep <= Reputation.HOSTILE || Theft.wanted(player, f.id())) {
			// (they hate you, or their guards are after you: nobody stops for a chat)
			return null;
		}
		boolean sworn = f.member(player.getUUID()) != null;
		boolean suspected = suspected(player, f.id(), now);
		int hearts = hearts(mob, player);
		boolean friend = McaCompat.isMca(mob) ? hearts >= GIFT_HEARTS : rep >= GIFT_REP;
		boolean known = McaCompat.isMca(mob) ? hearts >= GREET_HEARTS : rep >= GREET_REP;
		int[] w = new int[TOPICS.length];
		if (guard(mob)) {
			Long lastGuard = MET.get("guard/" + player.getUUID());
			if (only < 0 && !suspected && lastGuard != null && now - lastGuard < GUARD_GAP) {
				// (0.6.2) the guards have had a word with you lately
				return null;
			}
			if (night(level)) {
				w[WARN] = 3;
			}
			if (suspected) {
				w[ACCUSE] = 6;
			}
			Long halted = MET.get("halt/" + f.id() + "/" + player.getUUID());
			if (!sworn && rep < KNOWN_REP && (halted == null || now - halted >= HALT_TICKS)) {
				w[HALT] = 2;
			}
			// (0.6.2) the rest of a guard's business, by his king's temper
			int tv = temper(server, f);
			if (!sworn) {
				w[SEARCH] = SEARCH_W[tv];
				w[TOLL] = TOLL_W[tv];
			}
			w[BOUT] = BOUT_W[tv];
			w[LOYALTY] = LOYALTY_W[tv];
			if (!war(server, f).isEmpty()) {
				w[WAR] = 1;
			}
			w[GOSSIP] = 1;
		} else if (night(level) && only < 0) {
			return null;
		} else if (mob.isBaby()) {
			w[CHILD] = 3;
		} else {
			boolean carrying = false, ready = false;
			for (Quest q : Quests.log(player)) {
				if (q.giver().uuid().equals(mob.getUUID())) {
					carrying = true;
					ready |= Quests.ready(player, q);
				}
			}
			if (carrying) {
				w[QUEST_CHECK] = ready ? 8 : 4;
			} else if (Boolean.TRUE.equals(mob.getAttached(Quests.MARK)) && rep >= Reputation.WELCOME && Quests.settlement(level, mob) != null) {
				w[QUEST_OFFER] = 4;
			}
			if (suspected) {
				w[ACCUSE] = 5;
			}
			if (!war(server, f).isEmpty()) {
				w[WAR] = 2;
			}
			if (!sworn && rep < KNOWN_REP) {
				w[QUESTION] = 3;
			} else {
				w[QUESTION] = 1;
			}
			w[HUNGRY] = rep < WARY_REP ? 0 : 1;
			if (mob instanceof Villager v && DEALS.containsKey(profession(v)) && rep >= WARY_REP) {
				w[BARGAIN] = 2;
			}
			if (friend) {
				w[GIFT] = 2;
			}
			if (known || sworn) {
				w[GREET] = 2;
			}
			w[GOSSIP] = 2;
		}
		if (only >= 0) {
			return w[only] > 0 || testable(mob, only) ? new int[] {only, 1} : null;
		}
		int total = 0;
		for (int x : w) {
			total += x;
		}
		if (total == 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (int t = 0; t < w.length; t++) {
			roll -= w[t];
			if (roll < 0) {
				return new int[] {t, w[t]};
			}
		}
		return null;
	}

	/** (The test command) {@code mob} can be made to come over about {@code topic} whatever they know: anything but quests and wars they haven't got. */
	private static boolean testable(Mob mob, int topic) {
		if (guard(mob)) {
			return topic == HALT || topic == WARN || topic == GOSSIP || topic == ACCUSE || topic == SEARCH || topic == TOLL || topic == BOUT
				|| topic == LOYALTY;
		}
		if (mob.isBaby()) {
			return topic == CHILD;
		}
		return topic == QUESTION || topic == HUNGRY || topic == GIFT || topic == GREET || topic == GOSSIP || topic == ACCUSE
			|| (topic == BARGAIN && mob instanceof Villager v && DEALS.containsKey(profession(v)));
	}

	/** (0.6.2) {@code f}'s ruler's temper (its liege's, for a fief), as an index into {@link #TEMPERS}. */
	private static int temper(MinecraftServer server, Faction f) {
		Tempers.Temper t = Tempers.of(f);
		if (t == null) {
			t = Tempers.of(Wars.top(server, f));
		}
		return t == null ? PLAIN : t.ordinal();
	}

	private static int hearts(Mob mob, ServerPlayer player) {
		return mob instanceof Villager v ? McaCompat.heartsOf(v, player) : 0;
	}

	/** The realms {@code f}'s side is at war with (by name). */
	private static List<String> war(MinecraftServer server, Faction f) {
		List<String> enemies = new ArrayList<>();
		for (WarData.War war : Wars.warsOf(server, f.id())) {
			int side = Wars.sideOf(server, war, f.id());
			if (side >= 0) {
				enemies.add(Wars.name(server, Wars.otherSide(war, side)));
			}
		}
		return enemies;
	}

	/** Fixes what this approach will be about in particular: which opening, question, bargain or enemy. */
	private static void prepare(ServerLevel level, Approach a, ServerPlayer player, Faction f, RandomSource random) {
		switch (a.topic) {
			case QUESTION -> a.variant = random.nextInt(QUESTIONS);
			case CHILD -> {
				List<Integer> games = new ArrayList<>(List.of(CHILD_TAG));
				if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
					games.add(CHILD_KNIGHT);
				}
				if (player.getMainHandItem().has(DataComponents.WEAPON)) {
					games.add(CHILD_SWORD);
				}
				a.variant = games.get(random.nextInt(games.size()));
			}
			case BARGAIN -> {
				Deal deal = a.mob instanceof Villager v ? DEALS.get(profession(v)) : null;
				if (deal == null) {
					deal = DEALS.get("farmer");
				}
				a.goods = new ItemStack(deal.item(), deal.count());
				boolean friend = McaCompat.isMca(a.mob) ? hearts(a.mob, player) >= GREET_HEARTS : Reputation.get(player, f.id()) >= GREET_REP;
				a.price = Math.max(1, deal.price() - (friend ? FRIEND_DISCOUNT : 0));
				a.variant = random.nextInt(OPENINGS[BARGAIN]);
			}
			case WAR -> {
				List<String> enemies = war(level.getServer(), f);
				a.enemy = enemies.isEmpty() ? "?" : enemies.get(random.nextInt(enemies.size()));
				a.variant = random.nextInt(OPENINGS[WAR]);
			}
			case GOSSIP -> {
				// (0.5.52) word of what you are has reached them: they talk about you (Renown)
				Skills.Pillar heard = Renown.heard(player, f.id());
				if (heard != null && random.nextFloat() < Renown.GOSSIP_CHANCE) {
					a.enemy = heard.key();
					a.variant = random.nextInt(GOSSIP_ABOUT_YOU);
				} else {
					a.variant = random.nextInt(OPENINGS[GOSSIP]);
				}
			}
			case SEARCH, TOLL, BOUT, LOYALTY -> {
				// (0.6.2) one way of putting it per temper
				a.variant = temper(level.getServer(), f);
				a.price = a.topic == SEARCH ? BRIBE : a.topic == TOLL ? TOLLS[a.variant] : WAGER;
			}
			default -> a.variant = OPENINGS[a.topic] > 0 ? random.nextInt(OPENINGS[a.topic]) : 0;
		}
	}

	// ---- talking ---------------------------------------------------------------------------------------------------------

	/** (0.6.2) The guards' business that's put one way per temper. */
	private static boolean guardsOwn(int topic) {
		return topic == SEARCH || topic == TOLL || topic == BOUT || topic == LOYALTY;
	}

	private static String key(Approach a) {
		return "talk.jaymoskingdoms." + TOPICS[a.topic];
	}

	/** They've reached the player: they say their piece (a gift is handed over), and if they're asking, how to answer. */
	private static void arrive(ServerLevel level, Approach a, ServerPlayer player) {
		Faction f = Factions.get(level.getServer(), a.faction);
		String realm = f == null ? "?" : f.name();
		Component name = player.getName();
		// (the line's key and its arguments; "<key>.more" is what they'd add if asked)
		String line = key(a) + "." + (guardsOwn(a.topic) ? TEMPERS[a.variant] : a.variant), more = line + ".more";
		Object[] args = switch (a.topic) {
			case SEARCH, TOLL, BOUT -> new Object[] {a.price};
			case BARGAIN -> new Object[] {a.goods.getCount(), a.goods.getHoverName(), a.price};
			case WAR -> new Object[] {a.enemy};
			case HALT -> new Object[] {realm};
			case GREET -> new Object[] {name};
			default -> new Object[0];
		};
		Component said = null;
		if (a.topic == GOSSIP) {
			if (!a.enemy.isEmpty()) {
				// (0.5.52) word of what you are (Renown)
				line = key(a) + ".you." + a.enemy + "." + a.variant;
				more = key(a) + ".you." + a.enemy + ".more";
				args = new Object[] {name};
			} else {
				// (0.6.9) something strange seen lately; (0.5.56) a monster of legend seen out there; (0.5.51) a butcher
				said = Oddities.gossip(level, a.mob);
				if (said == null && level.getRandom().nextFloat() < Sightings.GOSSIP_CHANCE) {
					said = Sightings.gossip(level, a.mob);
				}
				if (said == null && level.getRandom().nextFloat() < BUTCHER_GOSSIP_CHANCE) {
					said = KitchenCompat.butcherGossip(player, a.mob);
				}
				if (said != null) {
					more = key(a) + ".more";
				}
			}
		}
		a.line = said != null ? said : Component.translatable(line, args);
		a.more = Component.translatable(more, args);
		a.color = a.topic == ACCUSE ? Speech.RED : a.topic == HALT || a.topic == WARN || a.topic == SEARCH || a.topic == TOLL || a.topic == LOYALTY ? Speech.YELLOW
			: a.topic == QUEST_OFFER || a.topic == QUEST_CHECK || a.topic == GIFT ? Speech.GOLD : Speech.WHITE;
		Speech.say(level, a.mob, a.line, a.color, SPEECH_RANGE);
		level.playSound(null, a.mob.getX(), a.mob.getY(), a.mob.getZ(), a.mob instanceof Villager ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_TRADE,
			SoundSource.NEUTRAL, 0.8F, a.mob.isBaby() ? 1.5F : 1.0F);
		if (a.topic == GIFT) {
			ItemStack gift = new ItemStack(GIFTS.get(level.getRandom().nextInt(GIFTS.size())));
			if (!player.getInventory().add(gift)) {
				player.drop(gift, false, Prediction.SERVER_ONLY);
			}
			a.mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		if (asks(a.topic)) {
			player.sendOverlayMessage(Component.translatable("talk.jaymoskingdoms.hint", a.mob.getDisplayName()).withStyle(ChatFormatting.GOLD));
		} else {
			player.sendOverlayMessage(Component.translatable("talk.jaymoskingdoms.hint.pardon", a.mob.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
	}

	/** The player right-clicked them: the answers (and, until they've said it, "tell me more"). */
	private static void ask(ServerPlayer player, Approach a) {
		PARDONING.remove(player.getUUID());
		List<Option> options = new ArrayList<>(options(player, a));
		if (!a.elaborated) {
			options.add(new Option(Component.translatable("talk.jaymoskingdoms.pardon.more"), true));
		}
		if (ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			ServerPlayNetworking.send(player, new TalkNet.Ask(a.mob.getId(), a.mob.getDisplayName(), heard(a),
				options.stream().map(Option::label).toList(), options.stream().map(Option::enabled).toList()));
		}
	}

	/** What they've told you: the line, and what more they said if you asked. */
	private static Component heard(Approach a) {
		return a.elaborated ? Component.translatable("talk.jaymoskingdoms.pardon.both", a.line, a.more) : a.line;
	}

	// ---- (0.6.2) begging their pardon --------------------------------------------------------------------------------

	/** The "pardon?" screen: before they've repeated it, what you can ask; after, what they said, and what more you can ask. */
	private static void pardonScreen(ServerPlayer player, Approach a) {
		pardonScreen(player, a, false);
	}

	private static void pardonScreen(ServerPlayer player, Approach a, boolean repeated) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		List<Button> buttons = new ArrayList<>();
		if (!repeated && !a.elaborated) {
			buttons.add(Button.PARDON);
		}
		if (!a.elaborated) {
			buttons.add(Button.MORE);
		}
		buttons.add(Button.BYE);
		Component words = repeated || a.elaborated ? heard(a) : Component.translatable("talk.jaymoskingdoms.pardon.prompt", a.mob.getDisplayName());
		List<Component> labels = buttons.stream().map(b -> (Component) Component.translatable(switch (b) {
			case PARDON -> "talk.jaymoskingdoms.pardon.again";
			case MORE -> "talk.jaymoskingdoms.pardon.more";
			case BYE -> repeated || a.elaborated ? "talk.jaymoskingdoms.pardon.thanks" : "talk.jaymoskingdoms.pardon.never_mind";
		})).toList();
		PARDONING.put(player.getUUID(), new Pardon(a, buttons));
		ServerPlayNetworking.send(player, new TalkNet.Ask(a.mob.getId(), a.mob.getDisplayName(), words, labels,
			labels.stream().map(l -> true).toList()));
	}

	/** The player picked {@code option} on the "pardon?" screen. */
	private static void pardoned(ServerLevel level, ServerPlayer player, Pardon p, int option) {
		Approach a = p.approach();
		if (option < 0 || option >= p.buttons().size() || p.buttons().get(option) == Button.BYE || !a.mob.isAlive()
			|| a.mob.distanceTo(player) > TALK_RANGE) {
			return;
		}
		long now = level.getGameTime();
		boolean more = p.buttons().get(option) == Button.MORE;
		if (more) {
			a.elaborated = true;
		}
		Speech.say(level, a.mob, more ? a.more : a.line, a.color, SPEECH_RANGE);
		level.playSound(null, a.mob.getX(), a.mob.getY(), a.mob.getZ(), a.mob instanceof Villager ? SoundEvents.VILLAGER_AMBIENT : SoundEvents.VILLAGER_TRADE,
			SoundSource.NEUTRAL, 0.8F, a.mob.isBaby() ? 1.5F : 1.0F);
		Approach active = ACTIVE.get(a.player);
		if (active != null && active != a) {
			// (someone else is already on their way over: they just say it, and you read it here)
			pardonScreen(player, a, true);
			return;
		}
		if (active == null && talking(a.mob)) {
			pardonScreen(player, a, true);
			return;
		}
		// (they stay to say it; a question you walked off on is put to you again)
		RECENT.remove(a.player);
		a.done = false;
		a.until = now + (asks(a.topic) ? ASK_TICKS : PARDON_HOLD_TICKS);
		ACTIVE.put(a.player, a);
		hold(a.mob, player);
		if (!asks(a.topic)) {
			pardonScreen(player, a, true);
		} else if (a.topic == QUEST_OFFER || a.topic == QUEST_CHECK) {
			if (more) {
				pardonScreen(player, a, true);
			} else {
				a.done = true;
				a.answered = true;
				Quests.open(player, a.mob);
			}
		} else {
			ask(player, a);
		}
	}

	private static List<Option> options(ServerPlayer player, Approach a) {
		String k = key(a) + ".answer.";
		return switch (a.topic) {
			case ACCUSE -> List.of(new Option(Component.translatable(k + "confess", FINE), emeralds(player) >= FINE),
				new Option(Component.translatable(k + "deny"), true), new Option(Component.translatable(k + "threaten"), true));
			case HALT -> List.of(new Option(Component.translatable(k + "trade"), true), new Option(Component.translatable(k + "work"), true),
				new Option(Component.translatable(k + "rude"), true));
			case QUESTION -> List.of(new Option(Component.translatable("talk.jaymoskingdoms.question." + a.variant + ".warm"), true),
				new Option(Component.translatable("talk.jaymoskingdoms.question." + a.variant + ".plain"), true),
				new Option(Component.translatable("talk.jaymoskingdoms.question." + a.variant + ".rude"), true));
			case HUNGRY -> List.of(new Option(Component.translatable(k + "give"), !food(player).isEmpty()), new Option(Component.translatable(k + "refuse"), true));
			case BARGAIN -> List.of(new Option(Component.translatable(k + "buy", a.price), emeralds(player) >= a.price),
				new Option(Component.translatable(k + "refuse"), true));
			case WAR -> List.of(new Option(Component.translatable(k + "stand"), true), new Option(Component.translatable(k + "refuse"), true));
			case CHILD -> List.of(new Option(Component.translatable("talk.jaymoskingdoms.child." + a.variant + ".yes"), true),
				new Option(Component.translatable("talk.jaymoskingdoms.child.answer.no"), true));
			case SEARCH -> List.of(new Option(Component.translatable(k + "allow"), true), new Option(Component.translatable(k + "refuse"), true),
				new Option(Component.translatable(k + "bribe", a.price), emeralds(player) >= a.price));
			case TOLL -> List.of(new Option(Component.translatable(k + "pay", a.price), emeralds(player) >= a.price),
				new Option(Component.translatable(k + "haggle"), emeralds(player) >= 1), new Option(Component.translatable(k + "refuse"), true));
			case BOUT -> List.of(new Option(Component.translatable(k + "accept", a.price), emeralds(player) >= a.price),
				new Option(Component.translatable(k + "decline"), true), new Option(Component.translatable(k + "insult"), true));
			case LOYALTY -> List.of(new Option(Component.translatable(k + "praise"), true), new Option(Component.translatable(k + "silent"), true),
				new Option(Component.translatable(k + "ill"), true));
			default -> List.of();
		};
	}

	private static void answer(ServerPlayer player, TalkNet.Answer answer) {
		if (Skills.answer(player, answer)) {
			// (0.5.52) a court wizard's offer to unmake your skills
			return;
		}
		Pardon pardon = PARDONING.remove(player.getUUID());
		if (pardon != null && pardon.approach().mob.getId() == answer.entityId()) {
			if (player.level() instanceof ServerLevel level) {
				pardoned(level, player, pardon, answer.option());
			}
			return;
		}
		Approach a = ACTIVE.get(player.getUUID());
		if (a == null || a.mob.getId() != answer.entityId() || !a.arrived || a.done || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (answer.option() < 0) {
			// (they closed the screen without answering: it's as good as walking off)
			a.done = true;
			walkedOff(level, a, player);
			return;
		}
		List<Option> options = options(player, a);
		if (answer.option() == options.size() && !a.elaborated && a.mob.distanceTo(player) <= TALK_RANGE) {
			// (0.6.2) "tell me more": they do, and wait on your answer a while longer
			a.elaborated = true;
			a.until = Math.max(a.until, level.getGameTime() + ASK_TICKS);
			Speech.say(level, a.mob, a.more, a.color, SPEECH_RANGE);
			ask(player, a);
			return;
		}
		if (answer.option() >= options.size() || !options.get(answer.option()).enabled() || a.mob.distanceTo(player) > TALK_RANGE) {
			return;
		}
		a.done = true;
		a.answered = true;
		Faction f = Factions.get(level.getServer(), a.faction);
		if (f == null) {
			return;
		}
		answered(level, a, player, f, answer.option());
	}

	private static void reply(ServerLevel level, Approach a, String key, int color, Object... args) {
		Speech.say(level, a.mob, Component.translatable(key, args), color, SPEECH_RANGE);
	}

	private static void answered(ServerLevel level, Approach a, ServerPlayer player, Faction f, int option) {
		String k = key(a) + ".reply.";
		RandomSource random = level.getRandom();
		switch (a.topic) {
			case ACCUSE -> {
				if (option == 0) {
					take(player, Items.EMERALD, FINE);
					Reputation.change(player, f, CONFESS_REP);
					clearSuspicion(player, f.id());
					reply(level, a, k + "confess", Speech.WHITE);
				} else if (option == 1) {
					float chance = BELIEVED_CHANCE + (f.member(player.getUUID()) != null ? BELIEVED_SWORN : 0.0F);
					if (random.nextFloat() < chance) {
						Reputation.change(player, f, BELIEVED_REP);
						clearSuspicion(player, f.id());
						reply(level, a, k + "believed", Speech.WHITE);
					} else {
						Reputation.change(player, f, LIE_REP);
						reply(level, a, k + "liar", Speech.RED);
					}
				} else {
					Reputation.change(player, f, THREATEN_REP);
					reply(level, a, k + "threatened", Speech.RED);
					flee(a.mob, player);
				}
			}
			case HALT -> {
				if (option == 0) {
					reply(level, a, k + "trade", Speech.YELLOW);
				} else if (option == 1) {
					Component giver = giver(level, a.mob, player);
					if (giver != null) {
						reply(level, a, k + "work", Speech.YELLOW, giver);
					} else {
						reply(level, a, k + "no_work", Speech.YELLOW);
					}
				} else {
					Reputation.change(player, f, RUDE_GUARD_REP);
					reply(level, a, k + "rude", Speech.RED);
				}
			}
			case QUESTION -> {
				String tone = option == 0 ? "warm" : option == 1 ? "plain" : "rude";
				if (option == 0) {
					McaCompat.hearts(a.mob, player, WARM_HEARTS);
					Reputation.change(player, f, WARM_REP);
				} else if (option == 2) {
					McaCompat.hearts(a.mob, player, RUDE_HEARTS);
					Reputation.change(player, f, RUDE_REP);
				}
				reply(level, a, "talk.jaymoskingdoms.question." + a.variant + ".reply." + tone, option == 2 ? Speech.RED : Speech.WHITE);
			}
			case HUNGRY -> {
				ItemStack food = food(player);
				if (option == 0 && !food.isEmpty()) {
					food.shrink(1);
					McaCompat.hearts(a.mob, player, FED_HEARTS);
					Reputation.change(player, f, FED_REP);
					a.mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.playSound(null, a.mob.getX(), a.mob.getY(), a.mob.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
					reply(level, a, k + "fed", Speech.GOLD);
				} else {
					reply(level, a, k + "refused", Speech.WHITE);
				}
			}
			case BARGAIN -> {
				if (option == 0 && emeralds(player) >= a.price) {
					take(player, Items.EMERALD, a.price);
					ItemStack goods = a.goods.copy();
					if (!player.getInventory().add(goods)) {
						player.drop(goods, false, Prediction.SERVER_ONLY);
					}
					McaCompat.hearts(a.mob, player, BOUGHT_HEARTS);
					level.playSound(null, a.mob.getX(), a.mob.getY(), a.mob.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
					reply(level, a, k + "sold", Speech.GOLD);
				} else {
					reply(level, a, k + "refused", Speech.WHITE);
				}
			}
			case WAR -> {
				if (option == 0) {
					if (!Wars.enemy(player, f)) {
						Reputation.change(player, f, STAND_REP);
					}
					reply(level, a, k + "stand", Speech.GOLD);
				} else {
					reply(level, a, k + "refuse", Speech.WHITE);
				}
			}
			case CHILD -> {
				if (option == 0) {
					McaCompat.hearts(a.mob, player, PLAY_HEARTS);
					reply(level, a, "talk.jaymoskingdoms.child." + a.variant + ".reply", Speech.WHITE);
					if (a.variant == CHILD_TAG) {
						flee(a.mob, player);
					}
				} else {
					reply(level, a, "talk.jaymoskingdoms.child.reply.no", Speech.WHITE);
				}
			}
			case SEARCH, TOLL, BOUT, LOYALTY -> guardsBusiness(level, a, player, f, option);
			default -> {
			}
		}
	}

	// ---- (0.6.2) the guards' business, by temper -----------------------------------------------------------------------

	/** The player answered a guard's search, toll, bout or loyalty test: what comes of it depends on the king. */
	private static void guardsBusiness(ServerLevel level, Approach a, ServerPlayer player, Faction f, int option) {
		int tv = a.variant;
		String temper = TEMPERS[tv];
		String k = key(a) + ".reply.";
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		switch (a.topic) {
			case SEARCH -> {
				if (option == 0) {
					if (suspected(player, f.id(), now)) {
						// (what was taken turns up: it goes back, with a fine)
						int fine = Math.min(FINE, emeralds(player));
						take(player, Items.EMERALD, fine);
						toTreasury(level, f, fine);
						clearSuspicion(player, f.id());
						Reputation.change(player, f, SEARCH_FOUND_REP);
						reply(level, a, k + "found." + temper, Speech.RED, fine);
						if (fine < FINE && (tv == TYRANT || tv == PARANOID)) {
							// (and they can't pay it)
							Theft.arrest(player, level, f, ARREST_TICKS);
						}
					} else {
						Reputation.change(player, f, SEARCH_CLEAN_REP[tv]);
						if (tv == TYRANT && emeralds(player) >= 1) {
							// (a fee for the trouble of searching you)
							take(player, Items.EMERALD, 1);
							toTreasury(level, f, 1);
						}
						reply(level, a, k + "clean." + temper, Speech.WHITE);
					}
				} else if (option == 1) {
					Reputation.change(player, f, SEARCH_REFUSE_REP[tv]);
					reply(level, a, k + "refuse." + temper, tv == TYRANT || tv == PARANOID || tv == WARLORD ? Speech.RED : Speech.WHITE);
					if (tv == TYRANT || tv == PARANOID) {
						Theft.arrest(player, level, f, ARREST_TICKS);
					}
				} else if (tv == TYRANT || tv == REVELER || tv == PLAIN) {
					// (a bribe they'll take: whatever you carry, they never saw it)
					take(player, Items.EMERALD, a.price);
					clearSuspicion(player, f.id());
					reply(level, a, k + "bribe." + temper, Speech.WHITE);
				} else {
					Reputation.change(player, f, BRIBE_REFUSED_REP);
					reply(level, a, k + "bribe." + temper, Speech.RED);
					if (tv == PARANOID) {
						Theft.arrest(player, level, f, ARREST_TICKS);
					}
				}
			}
			case TOLL -> {
				if (option == 0 && emeralds(player) >= a.price) {
					take(player, Items.EMERALD, a.price);
					toTreasury(level, f, a.price);
					Reputation.change(player, f, tv == REVELER ? REVELER_TOLL_REP : TOLL_PAID_REP);
					if (tv == REVELER) {
						give(player, random.nextBoolean() ? new ItemStack(Items.COOKIE, 3) : new ItemStack(Items.HONEY_BOTTLE));
					}
					reply(level, a, k + "paid." + temper, Speech.GOLD);
				} else if (option == 1) {
					if (random.nextFloat() < HAGGLE_CHANCE[tv]) {
						int half = Math.max(1, a.price / 2);
						half = Math.min(half, emeralds(player));
						take(player, Items.EMERALD, half);
						toTreasury(level, f, half);
						reply(level, a, k + "haggled." + temper, Speech.WHITE, half);
					} else if (tv == TYRANT) {
						// (no haggling with the king's men: they take it, and a slap for asking)
						int all = Math.min(a.price, emeralds(player));
						take(player, Items.EMERALD, all);
						toTreasury(level, f, all);
						slap(level, a.mob, player);
						reply(level, a, k + "haggle_failed." + temper, Speech.RED);
					} else {
						Reputation.change(player, f, HAGGLE_FAILED_REP);
						reply(level, a, k + "haggle_failed." + temper, Speech.YELLOW);
					}
				} else {
					Reputation.change(player, f, TOLL_REFUSE_REP[tv]);
					reply(level, a, k + "refuse." + temper, tv == TYRANT || tv == PARANOID ? Speech.RED : Speech.WHITE);
					if (tv == TYRANT) {
						Theft.arrest(player, level, f, ARREST_TICKS);
					}
				}
			}
			case BOUT -> {
				if (option == 0 && emeralds(player) >= a.price) {
					float chance = BOUT_BASE + player.getArmorValue() * BOUT_ARMOUR + (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * BOUT_ATTACK;
					a.mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.playSound(null, a.mob.getX(), a.mob.getY(), a.mob.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.8F, 1.0F);
					if (random.nextFloat() < Math.clamp(chance, BOUT_MIN, BOUT_MAX)) {
						give(player, new ItemStack(Items.EMERALD, a.price));
						Reputation.change(player, f, BOUT_WON_REP[tv]);
						reply(level, a, k + "won." + temper, Speech.GOLD, a.price);
					} else {
						take(player, Items.EMERALD, a.price);
						slap(level, a.mob, player);
						if (tv == WARLORD) {
							// (he respects anyone who'd step up)
							Reputation.change(player, f, 1);
						}
						reply(level, a, k + "lost." + temper, Speech.WHITE, a.price);
					}
				} else if (option == 1) {
					if (tv == WARLORD) {
						Reputation.change(player, f, -2);
					}
					reply(level, a, k + "decline." + temper, Speech.WHITE);
				} else {
					Reputation.change(player, f, tv == TYRANT || tv == WARLORD || tv == PARANOID ? INSULT_REP : tv == REVELER ? -1 : -3);
					reply(level, a, k + "insult." + temper, tv == REVELER || tv == NOBLE ? Speech.WHITE : Speech.RED);
					if (tv == TYRANT || tv == WARLORD || tv == PARANOID) {
						// (he draws on you: just him, and only till one of you yields)
						a.mob.setTarget(player);
					}
				}
			}
			case LOYALTY -> {
				if (option == 0) {
					if (tv == PARANOID && random.nextFloat() >= PARANOID_BELIEVES) {
						Reputation.change(player, f, -1);
						reply(level, a, k + "praise_doubted.paranoid", Speech.YELLOW);
					} else {
						Reputation.change(player, f, PRAISE_REP[tv]);
						if (tv == REVELER) {
							give(player, new ItemStack(Items.COOKIE, 2));
						}
						reply(level, a, k + "praise." + temper, Speech.GOLD);
					}
				} else if (option == 1) {
					Reputation.change(player, f, SILENT_REP[tv]);
					reply(level, a, k + "silent." + temper, tv == PARANOID ? Speech.YELLOW : Speech.WHITE);
				} else {
					Reputation.change(player, f, ILL_REP[tv]);
					reply(level, a, k + "ill." + temper, tv == REVELER || tv == NOBLE ? Speech.WHITE : Speech.RED);
					if (tv == TYRANT) {
						slap(level, a.mob, player);
					}
					if (tv == TYRANT || tv == PARANOID) {
						Theft.arrest(player, level, f, ARREST_TICKS);
					}
				}
			}
			default -> {
			}
		}
	}

	private static void toTreasury(ServerLevel level, Faction f, int n) {
		if (n > 0) {
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + n));
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/** A guard's open hand: a little hurt (never below {@link #SLAP_SPARES}) and a push. */
	private static void slap(ServerLevel level, Mob guard, ServerPlayer player) {
		guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (player.getHealth() > SLAP_SPARES) {
			player.hurtServer(level, level.damageSources().mobAttack(guard), SLAP);
		}
		Vec3 away = player.position().subtract(guard.position()).normalize().scale(0.6);
		player.push(away.x, 0.25, away.z);
		player.syncVelocity = true;
	}

	/** They asked and the player walked off (or shut them out). */
	private static void walkedOff(ServerLevel level, Approach a, ServerPlayer player) {
		// (0.6.2) asked again after begging their pardon: walking off a second time costs nothing more
		Faction f = a.snubbed ? null : Factions.get(level.getServer(), a.faction);
		a.snubbed = true;
		switch (a.topic) {
			case HALT -> {
				if (f != null) {
					Reputation.change(player, f, IGNORE_GUARD_REP);
				}
				reply(level, a, "talk.jaymoskingdoms.halt.ignored", Speech.RED);
			}
			case ACCUSE -> {
				if (f != null) {
					Reputation.change(player, f, IGNORE_ACCUSER_REP);
				}
				reply(level, a, "talk.jaymoskingdoms.accuse.ignored", Speech.RED);
			}
			case QUEST_OFFER, QUEST_CHECK -> {
			}
			case SEARCH, TOLL -> {
				// (0.6.2) walking off on a search or a toll is refusing it
				if (f != null) {
					guardsBusiness(level, a, player, f, a.topic == SEARCH ? 1 : 2);
				} else {
					reply(level, a, "talk.jaymoskingdoms.halt.ignored", Speech.RED);
				}
			}
			default -> reply(level, a, a.mob.isBaby() ? "talk.jaymoskingdoms.child.ignored" : "talk.jaymoskingdoms.ignored", Speech.WHITE);
		}
	}

	/** {@code mob} runs off away from {@code player} (a scared accuser; a child playing tag). */
	private static void flee(Mob mob, ServerPlayer player) {
		Vec3 away = mob.position().subtract(player.position()).normalize().scale(10.0).add(mob.position());
		BlockPos to = BlockPos.containing(away);
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, 0.75F, 1);
		} else {
			mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.2);
		}
	}

	/** Someone near the guard with work on offer (the gold !), to send a stranger to. */
	private static @Nullable Component giver(ServerLevel level, Mob guard, ServerPlayer player) {
		return level.getEntitiesOfClass(Villager.class, guard.getBoundingBox().inflate(WORK_SEARCH), v -> Boolean.TRUE.equals(v.getAttached(Quests.MARK)))
			.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(player))).map(Villager::getDisplayName).orElse(null);
	}

	// ---- the player's pack ---------------------------------------------------------------------------------------------

	static int emeralds(ServerPlayer player) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(Items.EMERALD)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	static void take(ServerPlayer player, Item item, int n) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (n <= 0) {
				return;
			}
			if (stack.is(item)) {
				int k = Math.min(n, stack.getCount());
				stack.shrink(k);
				n -= k;
			}
		}
	}

	/** Something the player could hand a hungry villager (the first plain food in their pack), or empty. */
	private static ItemStack food(ServerPlayer player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && stack.has(DataComponents.FOOD) && !NOT_FOOD.contains(stack.getItem())) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---- the test command ------------------------------------------------------------------------------------------------

	/** {@code /kingdom talk [topic]} (ops): the nearest who could, comes over now (about {@code topic}, if given). */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("talk").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> force(c, -1))
			.then(Commands.argument("topic", StringArgumentType.word()).suggests((c, b) -> {
				for (String t : TOPICS) {
					b.suggest(t);
				}
				return b.buildFuture();
			}).executes(c -> {
				String name = StringArgumentType.getString(c, "topic").toLowerCase(Locale.ROOT);
				for (int t = 0; t < TOPICS.length; t++) {
					if (TOPICS[t].equals(name)) {
						return force(c, t);
					}
				}
				c.getSource().sendFailure(Component.translatable("talk.jaymoskingdoms.command.unknown", name));
				return 0;
			}));
	}

	private static int force(CommandContext<CommandSourceStack> c, int topic) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		Approach old = ACTIVE.remove(player.getUUID());
		if (old != null) {
			release(old);
		}
		if (!consider(level, player, level.getGameTime(), topic, true)) {
			c.getSource().sendFailure(Component.translatable("talk.jaymoskingdoms.command.nobody"));
			return 0;
		}
		Approach a = ACTIVE.get(player.getUUID());
		c.getSource().sendSuccess(() -> Component.translatable("talk.jaymoskingdoms.command.coming", a.mob.getDisplayName(), TOPICS[a.topic])
			.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}
}
