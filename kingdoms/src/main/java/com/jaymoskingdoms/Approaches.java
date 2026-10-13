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
 */
public final class Approaches {
	/** How often players are looked at for someone to come over (ticks); how far away that someone can be (blocks). */
	static final int SCAN_TICKS = 40;
	static final double SEEK_RANGE = 16.0;
	/** Chance a scan that could start an approach does (so it doesn't come the moment the wait's over). */
	static final float APPROACH_CHANCE = 0.3F;
	/** (0.5.47) How often gossip is about a butcher (good, bad, ours or far off), when Jaymo's Kitchen is there. */
	static final float BUTCHER_GOSSIP_CHANCE = 0.5F;
	/** Between approaches to one player (ticks): at least this, plus up to this; the first after joining. */
	static final int PLAYER_GAP = 20 * 90, PLAYER_GAP_SPREAD = 20 * 150, FIRST_GAP = 20 * 45;
	/** The same villager comes to the same player at most once in this long (ticks: a day); a guard halts a stranger once a day per settlement. */
	static final long MET_TICKS = 24000L;
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
		GOSSIP = 9, GREET = 10, CHILD = 11, WARN = 12;
	static final String[] TOPICS = {"quest_offer", "quest_check", "accuse", "halt", "question", "hungry", "bargain", "gift", "war", "gossip",
		"greet", "child", "warn"};
	/** How many ways each topic has of opening (lang keys {@code talk.jaymoskingdoms.<topic>.<n>}). */
	static final int[] OPENINGS = {3, 3, 3, 3, 0, 3, 3, 3, 2, 10, 3, 3, 3};
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
		/** What they said on arriving (shown again on the answer screen). */
		Component line = Component.empty();
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
			if (a == null || a.mob != entity || !a.arrived || a.done || !asks(a.topic)) {
				return InteractionResult.PASS;
			}
			if (a.topic == QUEST_OFFER || a.topic == QUEST_CHECK) {
				// (their quests, straight away: the screen holds them still from here)
				a.done = true;
				Quests.open(sp, a.mob);
			} else {
				ask(sp, a);
			}
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Approach a = ACTIVE.remove(handler.player.getUUID());
			if (a != null) {
				release(a);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
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
			}
		}
		if (server.getTickCount() % SCAN_TICKS == 0) {
			for (ServerPlayer player : level.players()) {
				consider(level, player, now, -1, false);
			}
			if (server.getTickCount() % (SCAN_TICKS * 300) == 0) {
				MET.values().removeIf(t -> now - t > MET_TICKS);
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
		if (mob instanceof PathfinderMob pm && McaCompat.busy(pm)) {
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
			if (night(level)) {
				w[WARN] = 3;
			}
			if (suspected) {
				w[ACCUSE] = 6;
			}
			Long halted = MET.get("halt/" + f.id() + "/" + player.getUUID());
			if (!sworn && rep < KNOWN_REP && (halted == null || now - halted >= MET_TICKS)) {
				w[HALT] = 4;
			}
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
			return topic == HALT || topic == WARN || topic == GOSSIP || topic == ACCUSE;
		}
		if (mob.isBaby()) {
			return topic == CHILD;
		}
		return topic == QUESTION || topic == HUNGRY || topic == GIFT || topic == GREET || topic == GOSSIP || topic == ACCUSE
			|| (topic == BARGAIN && mob instanceof Villager v && DEALS.containsKey(profession(v)));
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
			default -> a.variant = OPENINGS[a.topic] > 0 ? random.nextInt(OPENINGS[a.topic]) : 0;
		}
	}

	// ---- talking ---------------------------------------------------------------------------------------------------------

	private static String key(Approach a) {
		return "talk.jaymoskingdoms." + TOPICS[a.topic];
	}

	/** They've reached the player: they say their piece (a gift is handed over), and if they're asking, how to answer. */
	private static void arrive(ServerLevel level, Approach a, ServerPlayer player) {
		Faction f = Factions.get(level.getServer(), a.faction);
		String realm = f == null ? "?" : f.name();
		Component name = player.getName();
		a.line = switch (a.topic) {
			case QUESTION -> Component.translatable("talk.jaymoskingdoms.question." + a.variant);
			case CHILD -> Component.translatable("talk.jaymoskingdoms.child." + a.variant);
			case BARGAIN -> Component.translatable(key(a) + "." + a.variant, a.goods.getCount(), a.goods.getHoverName(), a.price);
			case WAR -> Component.translatable(key(a) + "." + a.variant, a.enemy);
			case HALT -> Component.translatable(key(a) + "." + a.variant, realm);
			case GREET -> Component.translatable(key(a) + "." + a.variant, name);
			case GOSSIP -> {
				Component butcher = level.getRandom().nextFloat() < BUTCHER_GOSSIP_CHANCE ? KitchenCompat.butcherGossip(player, a.mob) : null;
				yield butcher != null ? butcher : Component.translatable(key(a) + "." + a.variant);
			}
			default -> Component.translatable(key(a) + "." + a.variant);
		};
		int color = a.topic == ACCUSE ? Speech.RED : a.topic == HALT || a.topic == WARN ? Speech.YELLOW
			: a.topic == QUEST_OFFER || a.topic == QUEST_CHECK || a.topic == GIFT ? Speech.GOLD : Speech.WHITE;
		Speech.say(level, a.mob, a.line, color, SPEECH_RANGE);
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
		}
	}

	/** The player right-clicked them: the answers. */
	private static void ask(ServerPlayer player, Approach a) {
		List<Option> options = options(player, a);
		if (ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			ServerPlayNetworking.send(player, new TalkNet.Ask(a.mob.getId(), a.mob.getDisplayName(), a.line,
				options.stream().map(Option::label).toList(), options.stream().map(Option::enabled).toList()));
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
			default -> List.of();
		};
	}

	private static void answer(ServerPlayer player, TalkNet.Answer answer) {
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
		if (answer.option() >= options.size() || !options.get(answer.option()).enabled() || a.mob.distanceTo(player) > TALK_RANGE) {
			return;
		}
		a.done = true;
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
			default -> {
			}
		}
	}

	/** They asked and the player walked off (or shut them out). */
	private static void walkedOff(ServerLevel level, Approach a, ServerPlayer player) {
		Faction f = Factions.get(level.getServer(), a.faction);
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

	private static int emeralds(ServerPlayer player) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(Items.EMERALD)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(ServerPlayer player, Item item, int n) {
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
