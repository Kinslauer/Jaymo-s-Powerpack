package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.29) Quests, from villagers and kings and nobody else. A villager with work for you wears a gold <b>!</b> over their
 * head (only about one villager in twenty ever has work to give, and a king seldom does; more of them do for one their
 * settlement thinks well of); talk to them (MCA's menu: Quests; a plain villager: sneak and use; a king: an
 * audience) and their quest is laid out with what it pays. Take it, do it, and come back: they wear a <b>?</b> while
 * you're about their business (gold once it's done). Turning it in pays out (emeralds, the giver's goods, enchanted books,
 * relics with passives) and builds your name: hearts with the MCA villager, reputation with their settlement, standing
 * if you're sworn to it.
 *
 * <p>What's asked suits who asks ({@link QuestBoard}); ruins, caches and champions are {@link QuestFoes}'; relics
 * {@link Relics}'. The log is the player's ({@link #LOG}); the offer is the giver's ({@link #OFFER}, {@link #MARK}).
 */
public final class Quests {
	/** How many quests a player may carry at once: villagers', and kings'. */
	static final int MAX_QUESTS = 5, MAX_ROYAL = 1;
	/** (0.7.168) How near (blocks) animals on your lead count for a lead quest. */
	static final double LEAD_RANGE = 12.0;
	/**
	 * (0.5.49) Quests are rare. The share of a settlement's villagers who ever give quests (one in twenty; the rest never
	 * do), and how much that share grows with a good name there (this much more at the best reputation, scaled down from it).
	 */
	static final float GIVER_SHARE = 0.05F, GIVER_SHARE_REPUTED = 0.05F;
	/** Chance one of those givers has work on a given day. */
	static final float OFFER_CHANCE = 0.5F;
	/** (0.5.49) Chance a king has work on a given day, and how much more at the best reputation with his realm. */
	static final float KING_CHANCE = 0.08F, KING_CHANCE_REPUTED = 0.12F;
	/** How often givers near players are looked at (ticks); how near (blocks). */
	static final int OFFER_TICKS = 100;
	static final double OFFER_RANGE = 40.0;
	/** How often quests' ruins are checked for players coming near, and the log re-sent if it changed (ticks). */
	static final int WATCH_TICKS = 20, SYNC_TICKS = 40;
	/** How near a player must stand to a giver to deal with them. */
	static final double TALK_RANGE = 8.0;
	/** The longest a giver stands still for a player reading their screen (ticks). */
	static final int HOLD_TICKS = 20 * 60 * 2;
	/** A chief's death counts for those with the quest this near it. */
	static final double CHIEF_CREDIT_RANGE = 160.0;
	/** (0.7.147) A king's bounty on a Nether warlord targets his fort as this plus its id. */
	static final String NETHER_FORT = "netherfort:";
	/** (0.5.49) Giving up a quest costs this much reputation with the giver's settlement (a king's, this much). */
	static final int ABANDON_REPUTATION = 10, ABANDON_ROYAL_REPUTATION = 40;
	/** A delivered letter: hearts with whoever gets it. */
	static final int LETTER_HEARTS = 5;
	/** (0.5.29) A king slain cunningly (under a false oath, or out on his round): emeralds this much more (percent), and this much more good name with the giver's realm. */
	static final int CUNNING_EMERALDS = 50, CUNNING_REPUTATION = 50;
	/** (0.5.29) A noble king out on his round stops for one who's been sent for him, when they come this close; once per this many ticks, held this long. */
	static final double ALMS_RANGE = 5.0;
	static final int ALMS_EVERY = 20 * 120, ALMS_HOLD = 20 * 8;
	/** The custom-data key on a quest's letter or keepsake: the quest's id. */
	static final String KEY = "jaymoskingdoms_quest";

	/** On a giver: the day their offer's next rolled, whether they have work, and the quest once it's been made. */
	record Offer(long nextDay, boolean has, Optional<Quest> quest) {
		static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("next_day", -1L).forGetter(Offer::nextDay),
				Codec.BOOL.optionalFieldOf("has", false).forGetter(Offer::has),
				Quest.CODEC.optionalFieldOf("quest").forGetter(Offer::quest))
			.apply(i, Offer::new));
	}

	/** Per player: the quests they carry. Saved, kept through death. */
	static final AttachmentType<List<Quest>> LOG = AttachmentRegistry.create(JaymosKingdoms.id("quests"),
		builder -> builder.persistent(Quest.CODEC.listOf()).copyOnDeath());
	/** Per giver: their offer. */
	static final AttachmentType<Offer> OFFER = AttachmentRegistry.create(JaymosKingdoms.id("quest_offer"), builder -> builder.persistent(Offer.CODEC));
	/** Per giver, seen by every client: they have work (the gold !). */
	public static final AttachmentType<Boolean> MARK = AttachmentRegistry.create(JaymosKingdoms.id("quest_mark"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** What each player's tracker was last sent (to send it again only when it changes). */
	private static final Map<UUID, Integer> SENT = new HashMap<>();
	/** Givers' screens to open at the end of this tick (player → giver). */
	private static final Map<ServerPlayer, LivingEntity> OPENING = new HashMap<>();
	/** Givers held still while a player reads their screen (giver → who's reading, until when). */
	private static final Map<LivingEntity, Hold> HELD = new HashMap<>();
	/** When each player was last stopped for by a noble king they were sent to kill (player → tick). */
	private static final Map<UUID, Integer> ALMS = new HashMap<>();

	private record Hold(ServerPlayer player, int until) {
	}

	private Quests() {
	}

	static void register() {
		QuestNet.register();
		QuestFoes.register();
		Relics.register();
		ServerPlayNetworking.registerGlobalReceiver(QuestNet.Act.TYPE, (payload, context) -> act(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Quests::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Quests::died);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (deliver(sp, entity)) {
				return InteractionResult.SUCCESS;
			}
			// a plain villager (MCA's have a Quests button): sneak and use
			if (entity instanceof Villager v && sp.isShiftKeyDown() && !McaCompat.isMca(v)) {
				open(sp, v);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player, true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SENT.clear();
			OPENING.clear();
			HELD.clear();
			ALMS.clear();
		});
	}

	// ---- the log ------------------------------------------------------------------------------------------------------

	static List<Quest> log(ServerPlayer player) {
		return player.getAttachedOrElse(LOG, List.of());
	}

	static @Nullable Quest quest(ServerPlayer player, String id) {
		for (Quest q : log(player)) {
			if (q.id().equals(id)) {
				return q;
			}
		}
		return null;
	}

	static void put(ServerPlayer player, Quest quest) {
		List<Quest> list = new ArrayList<>(log(player));
		list.replaceAll(q -> q.id().equals(quest.id()) ? quest : q);
		player.setAttached(LOG, List.copyOf(list));
	}

	private static void remove(ServerPlayer player, String id) {
		List<Quest> list = new ArrayList<>(log(player));
		list.removeIf(q -> q.id().equals(id));
		player.setAttached(LOG, List.copyOf(list));
	}

	/**
	 * (0.7.184) {@code player} carries a quest item ({@link Retinue#passage}): a letter or keepsake for a quest they're
	 * on, or the work a ruler of {@code faction} gave them, done and ready to turn in.
	 */
	static boolean questItem(ServerPlayer player, int faction) {
		for (Quest q : log(player)) {
			if ((q.kind() == Quest.DELIVER && !q.counted() || q.kind() == Quest.FETCH) && find(player, q.id()) >= 0) {
				return true;
			}
			if (q.giver().king() && q.giver().faction() == faction && ready(player, q)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code q} is done and can be turned in. */
	static boolean ready(ServerPlayer player, Quest q) {
		return switch (q.kind()) {
			case Quest.GATHER -> count(player, item(q.objective().target())) >= q.objective().count();
			case Quest.FETCH -> find(player, q.id()) >= 0;
			case Quest.BREAK_IN, Quest.STUD -> Stables.questHorse(player, q) != null;
			case Quest.LEAD -> led(player, q.objective().target()).size() >= q.objective().count();
			default -> q.counted();
		};
	}

	// ---- givers and their offers ----------------------------------------------------------------------------------------

	/** The settlement a giver gives for, or null if they can't give quests (a king of a kingdom; a grown villager in a settlement). */
	static @Nullable Faction settlement(ServerLevel level, LivingEntity giver) {
		MinecraftServer server = level.getServer();
		if (giver instanceof Folk folk) {
			Faction f = folk.role() == Folk.KING ? Factions.get(server, folk.factionId()) : null;
			return f != null && f.type() == FactionType.KINGDOM && !f.fallen() ? f : null;
		}
		if (!(giver instanceof Villager v) || v.isBaby()) {
			return null;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		if (job.equals("outlaw") || job.equals("cultist")) {
			return null;
		}
		Faction f = Factions.around(server, level, v.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			f = Factions.at(server, level, v.blockPosition());
		}
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	static long day(ServerLevel level) {
		return level.getOverworldClockTime() / 24000L;
	}

	/**
	 * A new day: the giver rolls whether they have work, as {@link #hasWork} decides for a player of {@code rep} with their
	 * settlement (whoever is about when the day's first roll is made); their mark follows.
	 */
	private static Offer refresh(ServerLevel level, LivingEntity giver, int rep) {
		Offer offer = giver.getAttachedOrElse(OFFER, new Offer(-1L, false, Optional.empty()));
		long day = day(level);
		if (offer.nextDay() <= day) {
			offer = new Offer(day + 1, hasWork(giver, rep, level.getRandom()), Optional.empty());
			giver.setAttached(OFFER, offer);
		}
		mark(giver, offer.has());
		return offer;
	}

	/**
	 * (0.5.49) Whether {@code giver} has work today: a king seldom ({@link #KING_CHANCE}); a villager only if they're one
	 * of the few who ever give quests ({@link #giver}), and then on about half their days. A good name ({@code rep} above
	 * {@link Reputation#WELCOME}) raises both a little.
	 */
	static boolean hasWork(LivingEntity giver, int rep, RandomSource random) {
		float reputed = Math.max(0, rep - Reputation.WELCOME) / (float) Reputation.MAX;
		if (giver instanceof Folk) {
			return random.nextFloat() < KING_CHANCE + KING_CHANCE_REPUTED * reputed;
		}
		return giver(giver, reputed) && random.nextFloat() < OFFER_CHANCE;
	}

	/**
	 * (0.5.49) Whether this villager is one who gives quests at all: fixed by who they are (their UUID), so the same
	 * few in each settlement are the ones with work, and a few more of them for one well thought of ({@code reputed}, 0..1).
	 */
	static boolean giver(LivingEntity villager, float reputed) {
		UUID id = villager.getUUID();
		long mixed = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
		float lot = Math.floorMod(mixed ^ (mixed >>> 31), 10000L) / 10000.0F;
		return lot < GIVER_SHARE + GIVER_SHARE_REPUTED * reputed;
	}

	/** (0.7.21) {@code giver} has {@code q} to offer until tomorrow (a grudge come to a head), whatever their day's lot. */
	static void offerNow(ServerLevel level, LivingEntity giver, Quest q) {
		giver.setAttached(OFFER, new Offer(day(level) + 1, true, Optional.of(q)));
		mark(giver, true);
	}

	/** (0.7.21) {@code giver} has a quest on offer just now. */
	static boolean offering(LivingEntity giver) {
		Offer offer = giver.getAttached(OFFER);
		return offer != null && offer.has() && offer.quest().isPresent();
	}

	private static void mark(LivingEntity giver, boolean has) {
		if (giver.getAttachedOrElse(MARK, false) != has) {
			if (has) {
				giver.setAttached(MARK, true);
			} else {
				giver.removeAttached(MARK);
			}
		}
	}

	/** The giver's quest, made now if it hasn't been yet (the ruins are looked for once, when someone first asks). */
	private static @Nullable Quest offered(ServerLevel level, LivingEntity giver, Faction f, int rep) {
		Offer offer = refresh(level, giver, rep);
		if (!offer.has()) {
			return null;
		}
		if (offer.quest().isPresent()) {
			return offer.quest().get();
		}
		Quest q = giver instanceof Folk ? QuestBoard.king(level, giver, f, level.getRandom()) : QuestBoard.villager(level, giver, f, level.getRandom());
		giver.setAttached(OFFER, new Offer(offer.nextDay(), q != null, Optional.ofNullable(q)));
		mark(giver, q != null);
		return q;
	}

	// ---- the giver's screen ---------------------------------------------------------------------------------------------

	/** {@code player} talks to {@code giver} about work: their quests (taken, ready, on offer) are shown. */
	public static void open(ServerPlayer player, LivingEntity giver) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!Languages.understands(player, giver)) {
			// (0.7.20) work's explained in words, and you don't have theirs
			Languages.shrug(player, giver);
			return;
		}
		Faction f = settlement(level, giver);
		Component name = giver.getDisplayName();
		if (f == null) {
			send(player, new QuestNet.Open(giver.getId(), name, List.of(), Component.translatable("quest.jaymoskingdoms.none")));
			return;
		}
		int rep = Reputation.get(player, f.id());
		if (rep <= Reputation.HOSTILE || (giver instanceof Folk && rep < Reputation.WELCOME)) {
			Speech.tell(level, giver, player, Component.translatable(giver instanceof Folk ? "quest.jaymoskingdoms.refuse.king" : "quest.jaymoskingdoms.refuse"),
				Speech.RED);
			send(player, new QuestNet.Open(giver.getId(), name, List.of(),
				Component.translatable(giver instanceof Folk ? "quest.jaymoskingdoms.untrusted.king" : "quest.jaymoskingdoms.untrusted", f.name())));
			return;
		}
		List<QuestNet.View> views = new ArrayList<>();
		for (Quest q : log(player)) {
			if (q.giver().uuid().equals(giver.getUUID())) {
				views.add(view(level, player, q, ready(player, q) ? QuestNet.READY : QuestNet.ACTIVE));
			}
		}
		if (views.isEmpty()) {
			// (0.7.0) a priest who knows the old rite sees what you've become, and sets you the hunt that lifts it
			Quest cure = Cures.offer(player, giver, f);
			if (cure != null) {
				giver.setAttached(OFFER, new Offer(day(level) + 1, true, Optional.of(cure)));
			} else if (giver.getAttachedOrElse(OFFER, new Offer(-1L, false, Optional.empty())).quest().filter(o -> o.template().startsWith("cure.")).isPresent()) {
				// (the hunt was for someone else: nothing for this one today)
				giver.setAttached(OFFER, new Offer(day(level) + 1, false, Optional.empty()));
			}
			Quest q = offered(level, giver, f, rep);
			if (q != null) {
				views.add(view(level, player, q, QuestNet.OFFER));
			}
		}
		Component note = views.isEmpty() ? Component.translatable(giver instanceof Folk ? "quest.jaymoskingdoms.nothing.king" : "quest.jaymoskingdoms.nothing")
			: Component.empty();
		// (they stand and hear the player out, rather than walking off mid-talk)
		HELD.put(giver, new Hold(player, level.getServer().getTickCount() + HOLD_TICKS));
		send(player, new QuestNet.Open(giver.getId(), name, views, note));
	}

	/**
	 * Opens {@code giver}'s quests for {@code player} at the end of the tick: MCA closes the player's screen after its
	 * menu's buttons are answered, which would close this one too.
	 */
	public static void openLater(ServerPlayer player, LivingEntity giver) {
		OPENING.put(player, giver);
	}

	/** Whether {@code giver} is standing still for a player reading their screen. */
	static boolean listening(LivingEntity giver) {
		return HELD.containsKey(giver);
	}

	private static void send(ServerPlayer player, QuestNet.Open open) {
		if (ServerPlayNetworking.canSend(player, QuestNet.Open.TYPE)) {
			ServerPlayNetworking.send(player, open);
		}
	}

	private static void act(ServerPlayer player, QuestNet.Act act) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (act.entityId() == QuestNet.BOARD) {
			// (0.7.125) the bounty board's screen
			Bounties.act(player, act);
			return;
		}
		if (act.entityId() == QuestNet.ORDERS) {
			// (0.7.256) a board's merchant orders
			MerchantOrders.act(player, act);
			return;
		}
		Entity found = level.getEntity(act.entityId());
		if (act.action() == QuestNet.CLOSE) {
			if (found instanceof LivingEntity giver && HELD.containsKey(giver) && HELD.get(giver).player() == player) {
				HELD.remove(giver);
			}
			return;
		}
		if (!(found instanceof LivingEntity giver) || !giver.isAlive() || giver.distanceToSqr(player) > TALK_RANGE * TALK_RANGE) {
			// they've gone (or died): the screen says so instead of doing nothing
			Component name = found == null ? Component.translatable("quest.jaymoskingdoms.giver") : found.getDisplayName();
			send(player, new QuestNet.Open(act.entityId(), name, List.of(), Component.translatable("quest.jaymoskingdoms.gone", name)));
			return;
		}
		Faction f = settlement(level, giver);
		if (f == null) {
			return;
		}
		switch (act.action()) {
			case QuestNet.ACCEPT -> accept(level, player, giver, f, act.questId());
			case QuestNet.TURN_IN -> turnIn(level, player, giver, f, act.questId());
			case QuestNet.ABANDON -> abandon(level, player, giver, act.questId());
			default -> {
			}
		}
		open(player, giver);
	}

	private static void accept(ServerLevel level, ServerPlayer player, LivingEntity giver, Faction f, String id) {
		Offer offer = giver.getAttachedOrElse(OFFER, new Offer(-1L, false, Optional.empty()));
		Quest q = offer.quest().orElse(null);
		if (!offer.has() || q == null || !q.id().equals(id)) {
			return;
		}
		long royal = log(player).stream().filter(Quest::royal).count();
		long common = log(player).size() - royal;
		if (q.royal() ? royal >= MAX_ROYAL : common >= MAX_QUESTS) {
			player.sendSystemMessage(Component.translatable(q.royal() ? "quest.jaymoskingdoms.full.royal" : "quest.jaymoskingdoms.full", MAX_QUESTS)
				.withStyle(ChatFormatting.RED));
			return;
		}
		if (q.kind() == Quest.WARBAND && !declared(level, player, f, q)) {
			return;
		}
		q = q.withGivenAt(level.getGameTime());
		List<Quest> list = new ArrayList<>(log(player));
		list.add(q);
		player.setAttached(LOG, List.copyOf(list));
		giver.setAttached(OFFER, new Offer(offer.nextDay(), false, Optional.empty()));
		mark(giver, false);
		if (q.kind() == Quest.DELIVER) {
			give(player, letter(q));
		} else if (QuestFoes.dig(q) && count(player, Items.BRUSH) == 0) {
			// (a dig: the giver lends a brush)
			give(player, new ItemStack(Items.BRUSH));
		}
		Speech.tell(level, giver, player, Component.translatable(q.royal() ? "quest.jaymoskingdoms.said.accept.king" : "quest.jaymoskingdoms.said.accept"),
			Speech.GOLD);
		player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.accepted", title(q)).withStyle(ChatFormatting.YELLOW));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		sync(player, true);
	}

	private static void turnIn(ServerLevel level, ServerPlayer player, LivingEntity giver, Faction f, String id) {
		Quest q = quest(player, id);
		if (q == null || !q.giver().uuid().equals(giver.getUUID()) || !ready(player, q)) {
			return;
		}
		if (q.kind() == Quest.GATHER) {
			take(player, item(q.objective().target()), q.objective().count());
		} else if (q.kind() == Quest.FETCH) {
			int slot = find(player, q.id());
			if (slot >= 0) {
				player.getInventory().getItem(slot).shrink(1);
			}
		} else if (q.kind() == Quest.BREAK_IN || q.kind() == Quest.STUD) {
			// (0.5.47) the horse goes to the giver's stable
			Stables.questTurnIn(level, player, giver, f, q);
		} else if (q.kind() == Quest.LEAD) {
			// (0.7.168) the animals are the giver's now: the leads come off and they stay in town
			List<Mob> led = led(player, q.objective().target());
			for (Mob animal : led.subList(0, Math.min(led.size(), q.objective().count()))) {
				animal.dropLeash();
				animal.setPersistenceRequired();
			}
		}
		remove(player, q.id());
		// (0.7.0) a priest's hunt: the rite, there and then
		Cures.rite(level, player, giver, q);
		for (ItemStack stack : rewards(level, q)) {
			give(player, stack);
		}
		Quest.Reward r = q.reward();
		if (r.hearts() > 0) {
			McaCompat.hearts(giver, player, r.hearts());
		}
		Reputation.change(player, f, r.reputation());
		Faction.Member member = f.member(player.getUUID());
		if (member != null && r.standing() > 0) {
			KingdomEvents.standing(level.getServer(), player, f, member.standing() + r.standing());
		}
		Speech.tell(level, giver, player, Component.translatable(q.royal() ? "quest.jaymoskingdoms.said.thanks.king" : "quest.jaymoskingdoms.said.thanks"),
			Speech.GOLD);
		// (0.7.109) and they remember it
		Recollections.remember(giver, player, Recollections.Kind.QUEST_DONE, title(q));
		player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.complete", title(q)).withStyle(ChatFormatting.GOLD));
		level.playSound(null, player.blockPosition(), q.royal() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
			1.0F, 1.0F);
		sync(player, true);
	}

	private static void abandon(ServerLevel level, ServerPlayer player, LivingEntity giver, String id) {
		Quest q = quest(player, id);
		if (q == null || !q.giver().uuid().equals(giver.getUUID())) {
			return;
		}
		remove(player, id);
		if (q.kind() == Quest.DELIVER) {
			int slot = find(player, id);
			if (slot >= 0) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
		player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.abandoned", title(q)).withStyle(ChatFormatting.GRAY));
		disappoint(player, q);
		sync(player, true);
	}

	/** (0.5.49) The giver's settlement thinks less of one who gave up their quest. */
	private static void disappoint(ServerPlayer player, Quest q) {
		Faction f = Factions.get(player.level().getServer(), q.giver().faction());
		if (f == null) {
			return;
		}
		int cost = q.giver().king() ? ABANDON_ROYAL_REPUTATION : ABANDON_REPUTATION;
		Reputation.change(player, f, -cost);
		if (Factions.level(player.level().getServer()).getEntity(q.giver().uuid()) instanceof net.minecraft.world.entity.LivingEntity giver) {
			// (0.7.109) the one who asked remembers it
			Recollections.remember(giver, player, Recollections.Kind.QUEST_FAILED, title(q));
		}
		player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.abandoned.reputation", cost, f.name()).withStyle(ChatFormatting.RED));
	}

	/**
	 * (0.7.168) {@code player} took {@code q} off a town notice ({@link TownNotices}): into their log, if they've room.
	 * False if their log is full.
	 */
	static boolean takeFromBoard(ServerLevel level, ServerPlayer player, Quest q) {
		long common = log(player).stream().filter(o -> !o.royal()).count();
		if (common >= MAX_QUESTS) {
			player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.full", MAX_QUESTS).withStyle(ChatFormatting.RED));
			return false;
		}
		List<Quest> list = new ArrayList<>(log(player));
		list.add(q.withGivenAt(level.getGameTime()));
		player.setAttached(LOG, List.copyOf(list));
		player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.accepted", title(q)).withStyle(ChatFormatting.YELLOW));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		sync(player, true);
		return true;
	}

	/** (0.7.168) The animals of {@code type} (an entity type id) {@code player} has on a lead close by. */
	static List<Mob> led(ServerPlayer player, String type) {
		return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LEAD_RANGE),
			m -> m.isAlive() && m.getLeashHolder() == player && BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString().equals(type));
	}

	/** Gives up quest {@code id} from anywhere ({@code /kingdom quests abandon}). */
	static boolean abandon(ServerPlayer player, String id) {
		Quest q = quest(player, id);
		if (q == null) {
			return false;
		}
		remove(player, id);
		disappoint(player, q);
		sync(player, true);
		return true;
	}

	/** (Testing) Every count-kept quest of {@code player}'s made up; how many. */
	static int finishAll(ServerPlayer player) {
		int n = 0;
		for (Quest q : log(player)) {
			if (q.kind() != Quest.GATHER && q.kind() != Quest.FETCH && q.kind() != Quest.BREAK_IN && q.kind() != Quest.STUD && q.kind() != Quest.LEAD
				&& !q.counted()) {
				put(player, q.withProgress(q.objective().count()));
				n++;
			}
		}
		sync(player, true);
		return n;
	}

	// ---- what a quest pays ------------------------------------------------------------------------------------------

	/** Everything {@code q} pays in goods, made up. */
	static List<ItemStack> rewards(ServerLevel level, Quest q) {
		List<ItemStack> out = new ArrayList<>();
		Quest.Reward r = q.reward();
		int emeralds = r.emeralds();
		while (emeralds > 0) {
			int n = Math.min(64, emeralds);
			out.add(new ItemStack(Items.EMERALD, n));
			emeralds -= n;
		}
		for (String spec : r.items()) {
			int star = spec.indexOf('*');
			Item item = item(star < 0 ? spec : spec.substring(0, star));
			int n = star < 0 ? 1 : Integer.parseInt(spec.substring(star + 1));
			if (item != Items.AIR) {
				while (n > 0) {
					int m = Math.min(n, new ItemStack(item).getMaxStackSize());
					out.add(new ItemStack(item, m));
					n -= m;
				}
			}
		}
		r.book().map(b -> Relics.book(level, b)).filter(s -> !s.isEmpty()).ifPresent(out::add);
		r.relic().map(s -> Relics.make(level, s, q.giver().name())).filter(s -> !s.isEmpty()).ifPresent(out::add);
		return out;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// ---- letters and keepsakes ----------------------------------------------------------------------------------------

	/** The sealed letter a delivery quest gives. */
	static ItemStack letter(Quest q) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("quest.jaymoskingdoms.letter", addressee(q))
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("quest.jaymoskingdoms.letter.from", q.giver().name()).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
		lore.add(Component.translatable("quest.jaymoskingdoms.letter.where", q.objective().place()).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		tag(stack, q.id());
		return stack;
	}

	/** The keepsake a fetch quest's cache holds. */
	static ItemStack keepsake(Quest q) {
		String[] spec = q.objective().otherName().split("\\|");
		Item base = item(spec[0]);
		ItemStack stack = new ItemStack(base == Items.AIR ? Items.GOLD_NUGGET : base);
		String key = spec.length > 1 ? spec[1] : "family_heirloom";
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("quest.jaymoskingdoms.keepsake." + key)
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("quest.jaymoskingdoms.keepsake.return", q.giver().name())
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		tag(stack, q.id());
		return stack;
	}

	private static void tag(ItemStack stack, String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
	}

	/** The quest a letter or keepsake belongs to. */
	static @Nullable String questOf(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(KEY).orElse(null);
	}

	/** The slot holding quest {@code id}'s letter or keepsake, or -1. */
	private static int find(ServerPlayer player, String id) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (id.equals(questOf(inv.getItem(i)))) {
				return i;
			}
		}
		return -1;
	}

	private static Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
	}

	private static int count(ServerPlayer player, Item item) {
		if (item == Items.AIR) {
			return 0;
		}
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item) && questOf(s) == null) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void take(ServerPlayer player, Item item, int n) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item) && questOf(s) == null) {
				int m = Math.min(n, s.getCount());
				s.shrink(m);
				n -= m;
			}
		}
	}

	/** A letter used on its addressee: delivered. True if that's what happened. */
	private static boolean deliver(ServerPlayer player, Entity entity) {
		ItemStack held = player.getMainHandItem();
		String id = questOf(held);
		Quest q = id == null ? null : quest(player, id);
		if (q == null || q.kind() != Quest.DELIVER || q.counted() || !(entity instanceof LivingEntity to)) {
			return false;
		}
		if (!addressedTo(player.level().getServer(), q, to)) {
			if (to instanceof Villager || to instanceof Folk) {
				player.sendOverlayMessage(Component.translatable("quest.jaymoskingdoms.letter.wrong", addressee(q)).withStyle(ChatFormatting.YELLOW));
			}
			return false;
		}
		held.shrink(1);
		put(player, q.withProgress(1));
		if (player.level() instanceof ServerLevel level) {
			Speech.say(level, to, Component.translatable("quest.jaymoskingdoms.said.letter"), Speech.WHITE, 16.0);
			level.playSound(null, to.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		McaCompat.hearts(to, player, LETTER_HEARTS);
		Recollections.remember(to, player, Recollections.Kind.LETTER, Component.literal(q.giver().name()));
		player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.letter.delivered", q.giver().name()).withStyle(ChatFormatting.GREEN));
		sync(player, true);
		return true;
	}

	private static boolean addressedTo(MinecraftServer server, Quest q, LivingEntity to) {
		if (q.objective().other().isPresent()) {
			return q.objective().other().get().equals(to.getUUID());
		}
		String[] parts = q.objective().target().split(":");
		if (parts.length >= 2 && parts[0].equals("leader")) {
			Faction f = Factions.get(server, Integer.parseInt(parts[1]));
			return f != null && f.leader().isPresent() && f.leader().get().equals(to.getUUID());
		}
		return false;
	}

	/** Who a letter's for: a name, or a settlement's ruler. */
	static Component addressee(Quest q) {
		if (q.objective().other().isPresent()) {
			return Component.literal(q.objective().otherName());
		}
		String[] parts = q.objective().target().split(":");
		MinecraftServer server = Rulers.server();
		Faction f = server == null || parts.length < 2 ? null : Factions.get(server, parseOr(parts[1]));
		return Component.translatable(Rulers.her("quest.jaymoskingdoms.ruler." + (parts.length >= 3 ? parts[2] : "elder"), f), q.objective().place());
	}

	// ---- progress -------------------------------------------------------------------------------------------------------

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		// a quest's champion: its quest is done, whoever struck the last blow
		QuestFoes.Tag foe = QuestFoes.tag(entity);
		if (foe != null && foe.champion()) {
			ServerPlayer owner = server.getPlayerList().getPlayer(foe.owner());
			Quest q = owner == null ? null : quest(owner, foe.quest());
			if (q != null) {
				put(owner, q.withProgress(1));
				owner.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.champion_slain", entity.getDisplayName(), q.giver().name())
					.withStyle(ChatFormatting.GOLD));
				sync(owner, true);
			}
		}
		// a camp's chief or warlord: done for everyone after them who's near
		int camp = campOf(entity);
		if (camp >= 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				for (Quest q : log(p)) {
					if (q.kind() == Quest.CHIEF && q.objective().target().equals("camp:" + camp) && !q.counted()
						&& (source.getEntity() == p || (p.level() == level && p.distanceToSqr(entity) < CHIEF_CREDIT_RANGE * CHIEF_CREDIT_RANGE))) {
						put(p, q.withProgress(1));
						p.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.champion_slain", entity.getDisplayName(), q.giver().name())
							.withStyle(ChatFormatting.GOLD));
						sync(p, true);
					}
				}
			}
		}
		// (0.5.29) a king some other king wanted dead
		if (entity instanceof Folk king && king.role() == Folk.KING) {
			regicide(level, king, king.factionId(), source);
		}
		// (0.5.56) a beast quest's beast, whoever struck it down; a survive quest's player fell before dawn
		if (Monsters.legend(entity.getType())) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				for (Quest q : log(p)) {
					if (q.kind() == Quest.HUNT && !q.counted() && q.objective().other().map(entity.getUUID()::equals).orElse(false)) {
						put(p, q.withProgress(1));
						p.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.champion_slain", entity.getDisplayName(), q.giver().name())
							.withStyle(ChatFormatting.GOLD));
						sync(p, true);
					}
				}
			}
		}
		if (entity instanceof ServerPlayer fallen) {
			for (Quest q : log(fallen)) {
				if (q.kind() == Quest.SURVIVE && !q.counted() && q.objective().spawned()) {
					Quest.Objective o = q.objective();
					put(fallen, q.withObjective(new Quest.Objective(o.target(), o.count(), o.progress(), o.pos(), o.other(), o.otherName(), o.place(), false)));
					fallen.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.survive_failed").withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		// so many slain
		if (source.getEntity() instanceof ServerPlayer killer) {
			for (Quest q : log(killer)) {
				if (q.kind() == Quest.SLAY && !q.counted() && matches(q.objective().target(), entity)) {
					Quest next = q.withProgress(q.objective().progress() + 1);
					put(killer, next);
					killer.sendOverlayMessage(objective(level, killer, next).copy().withStyle(next.counted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
					sync(killer, true);
				}
			}
			shareSlain(level, killer, entity);
		}
	}

	/**
	 * (0.7.264, Andy's NOEA picks) A party shares its kills: each other member of {@code killer}'s party within
	 * {@link Parties#SHARE_RANGE} blocks with the same slay quest (same target, not yet done) is credited the kill too.
	 * Slay quests only: the other kinds are about one person's own errand.
	 */
	private static void shareSlain(ServerLevel level, ServerPlayer killer, LivingEntity entity) {
		for (ServerPlayer p : Parties.near(killer, Parties.SHARE_RANGE)) {
			for (Quest q : log(p)) {
				if (q.kind() == Quest.SLAY && !q.counted() && matches(q.objective().target(), entity)) {
					Quest next = q.withProgress(q.objective().progress() + 1);
					put(p, next);
					p.sendOverlayMessage(objective(level, p, next).copy().withStyle(next.counted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
					sync(p, true);
				}
			}
		}
	}

	/**
	 * {@code king} has fallen: done for each player sent for him who struck him down or stood near. Done cunningly (sworn
	 * to his realm, so let close; or while he was out on his round) it pays more, and a false oath is broken with it.
	 */
	/** A king some other king wanted dead is dead ({@code realm}'s: a folk, or (0.7.138) a villager crowned, from {@link KingdomEvents}). */
	static void regicide(ServerLevel level, PathfinderMob king, int realm, DamageSource source) {
		MinecraftServer server = level.getServer();
		boolean out = Royals.out(king);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			for (Quest q : log(p)) {
				if (q.kind() != Quest.REGICIDE || q.counted() || !q.objective().target().equals("realm:" + realm)
					|| !(source.getEntity() == p || (p.level() == level && p.distanceToSqr(king) < CHIEF_CREDIT_RANGE * CHIEF_CREDIT_RANGE))) {
					continue;
				}
				Faction sworn = Factions.of(server, p.getUUID());
				boolean oath = sworn != null && sworn.id() == realm;
				Quest done = q.withProgress(1);
				p.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.regicide.done", king.getDisplayName(), q.giver().name())
					.withStyle(ChatFormatting.GOLD));
				if (oath || out) {
					Quest.Reward r = done.reward();
					done = done.withReward(new Quest.Reward(r.emeralds() * (100 + CUNNING_EMERALDS) / 100, r.items(), r.book(), r.relic(), r.hearts(),
						r.reputation() + CUNNING_REPUTATION, r.standing()));
					p.sendSystemMessage(Component.translatable(oath ? "quest.jaymoskingdoms.regicide.cunning.oath" : "quest.jaymoskingdoms.regicide.cunning.round")
						.withStyle(ChatFormatting.DARK_RED));
				}
				if (oath) {
					// (the oath was false, and now everyone knows it)
					Factions.update(server, realm, f -> f.withMember(p.getUUID(), null));
				}
				put(p, done);
				sync(p, true);
			}
		}
	}

	/**
	 * A noble king out on his round, near a player sent to kill him: he stops for them, as he would for anyone of his,
	 * and gives them bread. Close enough to touch.
	 */
	private static void alms(ServerPlayer player, int tick) {
		if (!(player.level() instanceof ServerLevel level) || tick - ALMS.getOrDefault(player.getUUID(), -ALMS_EVERY) < ALMS_EVERY) {
			return;
		}
		for (Quest q : log(player)) {
			if (q.kind() != Quest.REGICIDE || q.counted() || !q.objective().otherName().equals("round")) {
				continue;
			}
			int realm = Integer.parseInt(q.objective().target().substring("realm:".length()));
			for (Folk king : level.getEntitiesOfClass(Folk.class, player.getBoundingBox().inflate(ALMS_RANGE),
				k -> k.role() == Folk.KING && k.factionId() == realm && k.isAlive() && Royals.out(k))) {
				ALMS.put(player.getUUID(), tick);
				HELD.put(king, new Hold(player, tick + ALMS_HOLD));
				king.getNavigation().stop();
				king.getLookControl().setLookAt(player, 30.0F, 30.0F);
				give(player, new ItemStack(Items.BREAD, 2));
				Speech.say(level, king, Component.translatable("quest.jaymoskingdoms.regicide.alms"), Speech.GOLD, 16.0);
				player.sendOverlayMessage(Component.translatable("quest.jaymoskingdoms.regicide.alms.near", king.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
				return;
			}
		}
	}

	/** The realm a quest's objective names ("realm:<id>"), or -1. */
	private static int realmOf(Quest q) {
		String t = q.objective().target();
		return t.startsWith("realm:") ? Integer.parseInt(t.substring("realm:".length())) : -1;
	}

	/** (0.5.29) A warlord's war-party decree, taken: at war with its target already, or he declares it now. False if he can't. */
	private static boolean declared(ServerLevel level, ServerPlayer player, Faction f, Quest q) {
		MinecraftServer server = level.getServer();
		Faction target = Factions.get(server, realmOf(q));
		if (target != null && Wars.enemies(server, f.id(), target.id())) {
			return true;
		}
		if (target == null || Wars.declare(server, f, target, false) != null) {
			player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.warband.cannot", q.objective().place()).withStyle(ChatFormatting.RED));
			return false;
		}
		return true;
	}

	/** (0.5.29) The realm whose war party {@code player} was sent to lead (an unfinished war-party decree), or -1. */
	static int warbandRealm(ServerPlayer player) {
		for (Quest q : log(player)) {
			if (q.kind() == Quest.WARBAND && !q.counted()) {
				return q.giver().faction();
			}
		}
		return -1;
	}

	/** (0.5.29) {@code realm}'s war party sacked {@code target}'s seat with {@code player} marching in it. */
	static void sacked(ServerPlayer player, int realm, Faction target) {
		for (Quest q : log(player)) {
			if (q.kind() == Quest.WARBAND && !q.counted() && q.giver().faction() == realm) {
				put(player, q.withProgress(1));
				player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.warband.done", target.name(), q.giver().name()).withStyle(ChatFormatting.GOLD));
				sync(player, true);
			}
		}
	}

	/**
	 * (0.7.147) A Nether fort's warlord fell ({@code NetherForts}): a king's bounty on him ({@code "netherfort:" + fort}) is
	 * done for everyone after him who struck him down or was near.
	 */
	public static void netherWarlordSlain(ServerLevel level, LivingEntity entity, DamageSource source, int fort) {
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			for (Quest q : log(p)) {
				if (q.kind() == Quest.CHIEF && q.objective().target().equals(NETHER_FORT + fort) && !q.counted()
					&& (source.getEntity() == p || (p.level() == level && p.distanceToSqr(entity) < CHIEF_CREDIT_RANGE * CHIEF_CREDIT_RANGE))) {
					put(p, q.withProgress(1));
					p.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.champion_slain", entity.getDisplayName(), q.giver().name())
						.withStyle(ChatFormatting.GOLD));
					sync(p, true);
				}
			}
		}
	}

	/** The camp {@code entity} is chief or warlord of, or -1. */
	private static int campOf(Entity entity) {
		if (entity instanceof Folk folk && (folk.role() == Folk.CHIEF || folk.role() == Orcs.WARCHIEF || Pirates.isCoveCaptain(folk))) {
			return folk.factionId();
		}
		if (entity instanceof Worker w && !(entity instanceof Folk) && w.role() == Orcs.WARCHIEF) {
			// (0.7.292) a warchief as an MCA villager
			return w.factionId();
		}
		if (entity instanceof Worker w && entity instanceof net.minecraft.world.entity.LivingEntity living && Pirates.isCoveCaptain(living)) {
			// (0.7.242) a cove's captain at his wheel, one of MCA's villagers
			return w.factionId();
		}
		Illagers.Tag tag = Illagers.tag(entity);
		return tag != null && tag.role() == Illagers.WARLORD ? tag.camp() : -1;
	}

	/** A slay target: an entity type id, {@code #tag}, or {@code *monster}. */
	static boolean matches(String target, Entity entity) {
		Boolean cure = Cures.matches(target, entity);
		if (cure != null) {
			return cure;
		}
		if (target.equals("*monster")) {
			return entity instanceof Enemy;
		}
		if (target.startsWith("#")) {
			Identifier id = Identifier.tryParse(target.substring(1));
			return id != null && entity.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, id));
		}
		return target.equals(EntityType.getKey(entity.getType()).toString());
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (!OPENING.isEmpty()) {
			Map<ServerPlayer, LivingEntity> now = new HashMap<>(OPENING);
			OPENING.clear();
			now.forEach((player, giver) -> {
				if (!player.isRemoved() && giver.isAlive()) {
					open(player, giver);
				}
			});
		}
		if (!HELD.isEmpty()) {
			HELD.entrySet().removeIf(e -> {
				LivingEntity giver = e.getKey();
				ServerPlayer player = e.getValue().player();
				if (!giver.isAlive() || player.isRemoved() || player.level() != giver.level() || e.getValue().until() < tick
					|| player.distanceToSqr(giver) > TALK_RANGE * TALK_RANGE) {
					return true;
				}
				if (giver instanceof Mob mob && !mob.isPassenger()) {
					mob.getNavigation().stop();
					mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
				}
				return false;
			});
		}
		ServerLevel level = Factions.level(server);
		if (tick % OFFER_TICKS == 0) {
			// the givers near players roll their day's work, and wear their mark
			for (ServerPlayer player : level.players()) {
				for (LivingEntity giver : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(OFFER_RANGE),
					e -> e instanceof Villager || (e instanceof Folk folk && folk.role() == Folk.KING))) {
					Faction f = settlement(level, giver);
					if (f != null) {
						refresh(level, giver, Reputation.get(player, f.id()));
					} else {
						mark(giver, false);
					}
				}
			}
		}
		if (tick % WATCH_TICKS == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				watch(player);
				alms(player, tick);
			}
		}
		if (tick % SYNC_TICKS == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				sync(player, false);
			}
		}
	}

	/** A player near a quest's ruin: its cache or champion is set out; a cache glints for them. */
	private static void watch(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || level != Factions.level(level.getServer())) {
			return;
		}
		for (Quest q : log(player)) {
			if ((q.kind() == Quest.HUNT || q.kind() == Quest.SURVIVE) && q.objective().pos().isPresent()) {
				beast(level, player, q);
				continue;
			}
			if ((q.kind() != Quest.FETCH && q.kind() != Quest.CHAMPION) || q.objective().pos().isEmpty()) {
				continue;
			}
			BlockPos at = q.objective().pos().get();
			double dx = at.getX() - player.getX(), dz = at.getZ() - player.getZ();
			double d2 = dx * dx + dz * dz;
			if (!q.objective().spawned()) {
				if (d2 > QuestFoes.SET_OUT_RANGE * QuestFoes.SET_OUT_RANGE) {
					continue;
				}
				if (q.kind() == Quest.FETCH) {
					BlockPos chest = QuestFoes.cache(level, player, q, keepsake(q));
					if (chest != null) {
						Quest.Objective o = q.objective();
						put(player, q.withObjective(new Quest.Objective(o.target(), o.count(), o.progress(), Optional.of(chest), o.other(), o.otherName(), o.place(), true)));
						player.sendSystemMessage(Component.translatable(QuestFoes.dig(q) ? "quest.jaymoskingdoms.dig_near" : "quest.jaymoskingdoms.cache_near",
							keepsake(q).getHoverName()).withStyle(ChatFormatting.YELLOW));
						sync(player, true);
					}
				} else {
					Mob champion = QuestFoes.champion(level, player, q);
					if (champion != null) {
						put(player, q.withSpawned(Optional.of(champion.getUUID())));
						player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.champion_near", champion.getDisplayName()).withStyle(ChatFormatting.RED));
						sync(player, true);
					}
				}
			} else if (q.kind() == Quest.FETCH && d2 < 64.0 * 64.0 && find(player, q.id()) < 0 && level.getServer().getTickCount() % 40 == 0) {
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.0);
			}
		}
	}

	/**
	 * (0.5.56) A beast quest's player near where the beast was seen: it comes for them (slay); at night its kind comes for
	 * them, and dawn finds them alive (survive). A slay quest's beast that's gone (lost, despawned) comes again.
	 */
	private static void beast(ServerLevel level, ServerPlayer player, Quest q) {
		Quest.Objective o = q.objective();
		BlockPos at = o.pos().get();
		double dx = at.getX() - player.getX(), dz = at.getZ() - player.getZ();
		double d2 = dx * dx + dz * dz;
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(o.target())).orElse(null);
		if (type == null || q.counted() || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (q.kind() == Quest.HUNT) {
			boolean gone = o.spawned() && o.other().map(id -> level.getEntity(id) == null).orElse(true);
			if ((!o.spawned() || gone) && d2 < Sightings.HUNT_RANGE * Sightings.HUNT_RANGE) {
				List<Mob> came = Encounters.send(level, player, type, 1, Sightings.QUEST_HUNT_TICKS);
				if (!came.isEmpty()) {
					put(player, q.withSpawned(Optional.of(came.getFirst().getUUID())));
					player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.beast_near", came.getFirst().getDisplayName()).withStyle(ChatFormatting.RED));
					sync(player, true);
				}
			}
			return;
		}
		if (!o.spawned()) {
			if (level.isDarkOutside() && d2 < Sightings.SURVIVE_RANGE * Sightings.SURVIVE_RANGE) {
				// till dawn
				long time = level.getOverworldClockTime() % 24000L;
				int dawn = (int) Math.max(1200L, (time < 23000L ? 23000L : 47000L) - time);
				List<Mob> came = Encounters.send(level, player, type, 2 + level.getRandom().nextInt(2), dawn);
				if (!came.isEmpty()) {
					put(player, q.withSpawned(Optional.empty()));
					player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.survive_begun", came.getFirst().getDisplayName()).withStyle(ChatFormatting.RED));
					sync(player, true);
				}
			}
		} else if (!level.isDarkOutside()) {
			put(player, q.withProgress(1));
			player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.survived", q.giver().name()).withStyle(ChatFormatting.GOLD));
			sync(player, true);
		}
	}

	// ---- words -------------------------------------------------------------------------------------------------------

	/**
	 * (0.7.171) The realm whose ruler a quest's words speak of: a regicide's mark, else the king who gave it; null if
	 * neither (or no server).
	 */
	private static @Nullable Faction ruled(Quest q) {
		MinecraftServer server = Rulers.server();
		if (server == null) {
			return null;
		}
		if (q.kind() == Quest.REGICIDE && q.objective().target().startsWith("realm:")) {
			return Factions.get(server, parseOr(q.objective().target().substring("realm:".length())));
		}
		return q.giver().king() ? Factions.get(server, q.giver().faction()) : null;
	}

	private static int parseOr(String id) {
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	static Component title(Quest q) {
		String key = Rulers.her("quest.jaymoskingdoms." + q.template() + ".title", ruled(q));
		return switch (q.kind()) {
			// (who a letter's for and where; whose chief, or whose king)
			case Quest.DELIVER -> Component.translatable(key, addressee(q), q.objective().place());
			case Quest.CHIEF, Quest.WARBAND, Quest.RACE -> Component.translatable(key, q.objective().place());
			case Quest.STUD -> Component.translatable(key, breedName(q));
			case Quest.GRUDGE -> Component.translatable(key, q.objective().otherName());
			// (the reasons differ by the king's temper, the title only by how it's done)
			case Quest.REGICIDE -> Component.translatable(Rulers.her("quest.jaymoskingdoms.king.regicide." + q.objective().otherName() + ".title", ruled(q)),
				q.objective().place());
			default -> Component.translatable(key);
		};
	}

	private static Component ruin(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return Component.literal(id);
		}
		String pretty = key.getPath().replace('_', ' ');
		pretty = pretty.isEmpty() ? pretty : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
		return Component.translatableWithFallback("structure." + key.getNamespace() + "." + key.getPath(), pretty);
	}

	private static Component foes(String target) {
		String path = target.startsWith("*") ? target.substring(1) : target.substring(target.indexOf(':') + 1);
		return Component.translatable("quest.jaymoskingdoms.foes." + path);
	}

	/** (0.5.56) A beast quest's beast, by its entity id. */
	private static Component beastName(String id) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).map(EntityType::getDescription).orElse(Component.literal(id));
	}

	/** (0.5.47) A stud quest's breed. */
	private static Component breedName(Quest q) {
		return Component.translatable(com.jaymoskingdoms.equestrian.Breed.byId(q.objective().target()).key());
	}

	private static Component itemName(String id) {
		return new ItemStack(item(id)).getHoverName();
	}

	private static Component keepsakeName(Quest q) {
		String[] spec = q.objective().otherName().split("\\|");
		return Component.translatable("quest.jaymoskingdoms.keepsake." + (spec.length > 1 ? spec[1] : "family_heirloom"));
	}

	private static String x(Quest q) {
		return Integer.toString(q.objective().pos().map(BlockPos::getX).orElse(0));
	}

	private static String realmName(ServerLevel level, int id) {
		Faction f = Factions.get(level.getServer(), id);
		return f == null ? "?" : f.name();
	}

	private static String z(Quest q) {
		return Integer.toString(q.objective().pos().map(BlockPos::getZ).orElse(0));
	}

	/** The quest's story as the giver tells it. */
	static Component text(Quest q) {
		String key = Rulers.her("quest.jaymoskingdoms." + q.template() + ".text", ruled(q));
		Quest.Objective o = q.objective();
		return switch (q.kind()) {
			case Quest.SLAY -> Component.translatable(key, o.count(), foes(o.target()));
			case Quest.GATHER -> Component.translatable(key, o.count(), itemName(o.target()));
			case Quest.LEAD -> Component.translatable(key, o.count(), beastName(o.target()));
			case Quest.FETCH -> Component.translatable(key, keepsakeName(q), ruin(o.target()), x(q), z(q));
			case Quest.DELIVER -> Component.translatable(key, addressee(q), o.place(), x(q), z(q));
			case Quest.CHAMPION -> Component.translatable(key, Component.translatable("foe.jaymoskingdoms." + o.otherName()), ruin(o.target()), x(q), z(q));
			// (0.7.147 a Nether warlord's bounty names him too)
			case Quest.CHIEF -> Component.translatable(key, o.place(), x(q), z(q), o.otherName());
			case Quest.REGICIDE, Quest.WARBAND -> Component.translatable(key, o.place(), x(q), z(q));
			case Quest.BREAK_IN -> Component.translatable(key, o.place());
			case Quest.STUD -> Component.translatable(key, breedName(q), o.count());
			case Quest.RACE -> Component.translatable(key, o.place(), Stables.raceSeconds(q), x(q), z(q));
			case Quest.HUNT, Quest.SURVIVE -> Component.translatable(key, x(q), z(q));
			case Quest.GRUDGE -> Component.translatable(key, o.otherName(), o.count(), x(q), z(q));
			case Quest.RELIEF -> Component.translatable(key, o.count(), o.place());
			case Quest.ESCORT -> Component.translatable(key, o.place(), x(q), z(q));
			default -> Component.empty();
		};
	}

	/** What's left to do (or that it's done). */
	static Component objective(ServerLevel level, ServerPlayer player, Quest q) {
		if (ready(player, q)) {
			return Component.translatable("quest.jaymoskingdoms.objective.return", q.giver().name());
		}
		Quest.Objective o = q.objective();
		return switch (q.kind()) {
			case Quest.SLAY -> Component.translatable("quest.jaymoskingdoms.objective.slay", foes(o.target()), o.progress(), o.count());
			case Quest.GATHER -> Component.translatable("quest.jaymoskingdoms.objective.gather", itemName(o.target()),
				Math.min(count(player, item(o.target())), o.count()), o.count());
			case Quest.LEAD -> Component.translatable("quest.jaymoskingdoms.objective.lead", beastName(o.target()),
				Math.min(led(player, o.target()).size(), o.count()), o.count(), q.giver().name());
			case Quest.FETCH -> Component.translatable(QuestFoes.dig(q) ? "quest.jaymoskingdoms.objective.dig" : "quest.jaymoskingdoms.objective.fetch",
				keepsakeName(q), ruin(o.target()), x(q), z(q));
			case Quest.DELIVER -> Component.translatable("quest.jaymoskingdoms.objective.deliver", addressee(q), o.place(), x(q), z(q));
			case Quest.CHAMPION -> Component.translatable("quest.jaymoskingdoms.objective.champion", Component.translatable("foe.jaymoskingdoms." + o.otherName()),
				ruin(o.target()), x(q), z(q));
			case Quest.CHIEF -> Component.translatable(o.target().startsWith(NETHER_FORT) ? "quest.jaymoskingdoms.objective.nether_warlord"
				: "quest.jaymoskingdoms.objective.chief", o.place(), x(q), z(q));
			case Quest.REGICIDE -> Component.translatable(Rulers.her("quest.jaymoskingdoms.objective.regicide", ruled(q)), o.place(), x(q), z(q));
			case Quest.WARBAND -> Component.translatable("quest.jaymoskingdoms.objective.warband", realmName(level, q.giver().faction()), o.place());
			case Quest.BREAK_IN -> Component.translatable("quest.jaymoskingdoms.objective.break_in", q.giver().name());
			case Quest.STUD -> Component.translatable("quest.jaymoskingdoms.objective.stud", breedName(q), o.count(), q.giver().name());
			case Quest.RACE -> Component.translatable("quest.jaymoskingdoms.objective.race", o.place(), Stables.raceLeft(level, q), x(q), z(q));
			case Quest.HUNT -> Component.translatable("quest.jaymoskingdoms.objective.hunt", beastName(o.target()), x(q), z(q));
			case Quest.SURVIVE -> Component.translatable(o.spawned() ? "quest.jaymoskingdoms.objective.surviving" : "quest.jaymoskingdoms.objective.survive",
				beastName(o.target()), x(q), z(q));
			case Quest.GRUDGE -> o.pos().isPresent()
				? Component.translatable(o.spawned() ? "quest.jaymoskingdoms.objective.grudge_luring" : "quest.jaymoskingdoms.objective.grudge_lure",
					o.otherName(), q.giver().name(), x(q), z(q))
				: Component.translatable("quest.jaymoskingdoms.objective.grudge_beat", o.otherName(), o.progress(), o.count());
			case Quest.RELIEF -> Component.translatable("quest.jaymoskingdoms.objective.rubble", o.place(), o.progress(), o.count());
			case Quest.ESCORT -> Component.translatable(o.spawned() ? "quest.jaymoskingdoms.objective.escorting" : "quest.jaymoskingdoms.objective.escort",
				q.giver().name(), o.place(), x(q), z(q));
			default -> Component.empty();
		};
	}

	private static QuestNet.View view(ServerLevel level, ServerPlayer player, Quest q, int state) {
		List<Component> extras = new ArrayList<>();
		Quest.Reward r = q.reward();
		Faction f = Factions.get(level.getServer(), q.giver().faction());
		String settlement = f == null ? "" : f.name();
		if (r.reputation() > 0) {
			extras.add(Component.translatable("quest.jaymoskingdoms.reward.reputation", r.reputation(), settlement));
		}
		if (r.hearts() > 0 && McaCompat.LOADED) {
			extras.add(Component.translatable("quest.jaymoskingdoms.reward.hearts", r.hearts(), q.giver().name()));
		}
		if (r.standing() > 0 && f != null && f.member(player.getUUID()) != null) {
			extras.add(Component.translatable("quest.jaymoskingdoms.reward.standing", r.standing()));
		}
		if (q.kind() == Quest.REGICIDE && state != QuestNet.READY) {
			extras.add(Component.translatable("quest.jaymoskingdoms.regicide.hint." + q.objective().otherName(), CUNNING_EMERALDS));
		}
		MutableComponent title = title(q).copy();
		return new QuestNet.View(q.id(), state, q.royal(), title, text(q), objective(level, player, q), rewards(level, q), extras);
	}

	/** Sends {@code player} their log (for the tracker and the givers' ?), if it changed or {@code force}. */
	static void sync(ServerPlayer player, boolean force) {
		if (!ServerPlayNetworking.canSend(player, QuestNet.Log.TYPE) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		List<QuestNet.Tracked> tracked = new ArrayList<>();
		for (Quest q : log(player)) {
			boolean ready = ready(player, q);
			Optional<BlockPos> target = ready ? Optional.empty() : q.kind() == Quest.SLAY || q.kind() == Quest.GATHER ? Optional.empty() : q.objective().pos();
			tracked.add(new QuestNet.Tracked(q.giver().uuid(), q.royal(), title(q), objective(level, player, q), ready, target));
		}
		int hash = tracked.hashCode();
		Integer last = SENT.get(player.getUUID());
		if (!force && last != null && last == hash) {
			return;
		}
		SENT.put(player.getUUID(), hash);
		ServerPlayNetworking.send(player, new QuestNet.Log(tracked));
	}
}
