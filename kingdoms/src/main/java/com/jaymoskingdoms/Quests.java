package com.jaymoskingdoms;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * (0.5.26) Quests, from villagers and kings and nobody else. A villager with work for you wears a gold <b>!</b> over their
 * head (a king nearly always has one); talk to them (MCA's menu: Quests; a plain villager: sneak and use; a king: an
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
	/** Chance a villager has work on a given day. */
	static final float OFFER_CHANCE = 0.35F;
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
	/** A delivered letter: hearts with whoever gets it. */
	static final int LETTER_HEARTS = 5;
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

	private static void put(ServerPlayer player, Quest quest) {
		List<Quest> list = new ArrayList<>(log(player));
		list.replaceAll(q -> q.id().equals(quest.id()) ? quest : q);
		player.setAttached(LOG, List.copyOf(list));
	}

	private static void remove(ServerPlayer player, String id) {
		List<Quest> list = new ArrayList<>(log(player));
		list.removeIf(q -> q.id().equals(id));
		player.setAttached(LOG, List.copyOf(list));
	}

	/** Whether {@code q} is done and can be turned in. */
	static boolean ready(ServerPlayer player, Quest q) {
		return switch (q.kind()) {
			case Quest.GATHER -> count(player, item(q.objective().target())) >= q.objective().count();
			case Quest.FETCH -> find(player, q.id()) >= 0;
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

	/** A new day: the giver rolls whether they have work (a king always has); their mark follows. */
	private static Offer refresh(ServerLevel level, LivingEntity giver) {
		Offer offer = giver.getAttachedOrElse(OFFER, new Offer(-1L, false, Optional.empty()));
		long day = day(level);
		if (offer.nextDay() <= day) {
			boolean has = giver instanceof Folk || level.getRandom().nextFloat() < OFFER_CHANCE;
			offer = new Offer(day + 1, has, Optional.empty());
			giver.setAttached(OFFER, offer);
		}
		mark(giver, offer.has());
		return offer;
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
	private static @Nullable Quest offered(ServerLevel level, LivingEntity giver, Faction f) {
		Offer offer = refresh(level, giver);
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
		Faction f = settlement(level, giver);
		Component name = giver.getDisplayName();
		if (f == null) {
			send(player, new QuestNet.Open(giver.getId(), name, List.of(), Component.translatable("quest.jaymoskingdoms.none")));
			return;
		}
		int rep = Reputation.get(player, f.id());
		if (rep <= Reputation.HOSTILE || (giver instanceof Folk && rep < Reputation.WELCOME)) {
			Speech.say(level, giver, Component.translatable(giver instanceof Folk ? "quest.jaymoskingdoms.refuse.king" : "quest.jaymoskingdoms.refuse"),
				Speech.RED, 16.0);
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
			Quest q = offered(level, giver, f);
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
		q = q.withGivenAt(level.getGameTime());
		List<Quest> list = new ArrayList<>(log(player));
		list.add(q);
		player.setAttached(LOG, List.copyOf(list));
		giver.setAttached(OFFER, new Offer(offer.nextDay(), false, Optional.empty()));
		mark(giver, false);
		if (q.kind() == Quest.DELIVER) {
			give(player, letter(q));
		}
		Speech.say(level, giver, Component.translatable(q.royal() ? "quest.jaymoskingdoms.said.accept.king" : "quest.jaymoskingdoms.said.accept"),
			Speech.GOLD, 16.0);
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
		}
		remove(player, q.id());
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
		Speech.say(level, giver, Component.translatable(q.royal() ? "quest.jaymoskingdoms.said.thanks.king" : "quest.jaymoskingdoms.said.thanks"),
			Speech.GOLD, 16.0);
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
		sync(player, true);
	}

	/** Gives up quest {@code id} from anywhere ({@code /kingdom quests abandon}). */
	static boolean abandon(ServerPlayer player, String id) {
		Quest q = quest(player, id);
		if (q == null) {
			return false;
		}
		remove(player, id);
		sync(player, true);
		return true;
	}

	/** (Testing) Every count-kept quest of {@code player}'s made up; how many. */
	static int finishAll(ServerPlayer player) {
		int n = 0;
		for (Quest q : log(player)) {
			if (q.kind() != Quest.GATHER && q.kind() != Quest.FETCH && !q.counted()) {
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
		return Component.translatable("quest.jaymoskingdoms.ruler." + (parts.length >= 3 ? parts[2] : "elder"), q.objective().place());
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
		}
	}

	/** The camp {@code entity} is chief or warlord of, or -1. */
	private static int campOf(Entity entity) {
		if (entity instanceof Folk folk && folk.role() == Folk.CHIEF) {
			return folk.factionId();
		}
		Illagers.Tag tag = Illagers.tag(entity);
		return tag != null && tag.role() == Illagers.WARLORD ? tag.camp() : -1;
	}

	/** A slay target: an entity type id, {@code #tag}, or {@code *monster}. */
	static boolean matches(String target, Entity entity) {
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
					if (settlement(level, giver) != null) {
						refresh(level, giver);
					} else {
						mark(giver, false);
					}
				}
			}
		}
		if (tick % WATCH_TICKS == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				watch(player);
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
						player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.cache_near", keepsake(q).getHoverName()).withStyle(ChatFormatting.YELLOW));
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

	// ---- words -------------------------------------------------------------------------------------------------------

	static Component title(Quest q) {
		return Component.translatable("quest.jaymoskingdoms." + q.template() + ".title");
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

	private static String z(Quest q) {
		return Integer.toString(q.objective().pos().map(BlockPos::getZ).orElse(0));
	}

	/** The quest's story as the giver tells it. */
	static Component text(Quest q) {
		String key = "quest.jaymoskingdoms." + q.template() + ".text";
		Quest.Objective o = q.objective();
		return switch (q.kind()) {
			case Quest.SLAY -> Component.translatable(key, o.count(), foes(o.target()));
			case Quest.GATHER -> Component.translatable(key, o.count(), itemName(o.target()));
			case Quest.FETCH -> Component.translatable(key, keepsakeName(q), ruin(o.target()), x(q), z(q));
			case Quest.DELIVER -> Component.translatable(key, addressee(q), o.place(), x(q), z(q));
			case Quest.CHAMPION -> Component.translatable(key, Component.translatable("foe.jaymoskingdoms." + o.otherName()), ruin(o.target()), x(q), z(q));
			case Quest.CHIEF -> Component.translatable(key, o.place(), x(q), z(q));
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
			case Quest.FETCH -> Component.translatable("quest.jaymoskingdoms.objective.fetch", keepsakeName(q), ruin(o.target()), x(q), z(q));
			case Quest.DELIVER -> Component.translatable("quest.jaymoskingdoms.objective.deliver", addressee(q), o.place(), x(q), z(q));
			case Quest.CHAMPION -> Component.translatable("quest.jaymoskingdoms.objective.champion", Component.translatable("foe.jaymoskingdoms." + o.otherName()),
				ruin(o.target()), x(q), z(q));
			case Quest.CHIEF -> Component.translatable("quest.jaymoskingdoms.objective.chief", o.place(), x(q), z(q));
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
