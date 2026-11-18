package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The written oath and pact (Andy, 2026-11-18: the Awakening pick "Rule writer" was meant as how Paladin oaths and Warlock
 * pacts work; he chose "Written oath and pact"). A Paladin's oath and a Warlock's pact are kept in a <b>Book of Vows</b>
 * ({@link #ITEM}, given on swearing):
 * <ul>
 * <li><b>The terms.</b> The oath's or the patron's own terms ({@link Vows}) are written in and can't be struck out. Up to
 * {@link #MOST} - 1 more may be sworn of your own, from any other term a vow knows; {@link Vows} then holds you to them as to
 * the rest. Striking out a term you swore breaks it: the oath breaks, or the patron collects.</li>
 * <li><b>The rules.</b> One rule line, and one more for each term you swore. A rule is one sentence of four parts: <b>who</b>
 * (I, my allies near me, the foes near me, the one I look at, everyone near me), <b>what</b> is true of them (the oath or
 * patron decides which: {@link #allowed}), <b>how strong</b> (1, and one more every {@link #LEVELS_A_STRENGTH} class levels, up
 * to 5) and <b>how long</b> (5 s to a minute). Enacting one costs <b>ink</b> from the book ({@link #cost}): grace for a
 * Paladin, credit with the patron for a Warlock; it comes back {@link #INK_REGEN} a second (twice that with no rule running).
 * One rule runs at a time; three from class level {@link #THREE_AT}.</li>
 * <li><b>Use</b> the book to read and write it; <b>sneak and use</b> it to enact the rule picked in it.</li>
 * </ul>
 * A Paladin whose oath is broken, or a Warlock whose patron withholds over an unpaid debt, enacts nothing; a broken oath or
 * a lost class ends what runs. "Have no powers" puts Jaymo's Powers' ruled tag ({@link Will#RULED_TAG}) on whom it's about:
 * power keys refused, casters hushed.
 */
public final class VowBook {
	// ---- tuning ----
	/** At most this many rule lines (one free, one more a sworn term). */
	public static final int MOST = 5;
	/** Ink: the most held, regained a second (twice with no rule running), the share of a repealed rule's ink given back. */
	public static final float INK_MOST = 150.0F, INK_REGEN = 1.0F, REPEAL_BACK = 0.5F;
	/** Class levels to each strength past the first; the class level from which three rules run at once. */
	public static final int LEVELS_A_STRENGTH = 5, THREE_AT = 20;
	/** How near "near" is (blocks). */
	static final double NEAR = 12.0;
	/** Wither's harm a second for each point of strength; burning's seconds a point. */
	static final float WITHER_PER = 0.75F;
	static final int BURN_PER = 2;
	static final int COLOUR = 0xE8C66A;

	// ---- a rule, packed in an int: who | what << 4 | strength << 8 | length << 12 ----
	public static final int SELF = 0, ALLIES = 1, FOES = 2, TARGET = 3, EVERYONE = 4, WHO_COUNT = 5;
	public static final int MEND = 0, WITHER = 1, SWIFT = 2, SLOW = 3, STRONG = 4, WEAK = 5, HARD = 6, FLOAT = 7, BURN = 8, MUTE = 9, GLOW = 10,
		BLIND = 11, WHAT_COUNT = 12;
	public static final int[] SECONDS = {5, 15, 30, 60};
	/** What each "what" costs in ink for 5 s at strength 1. */
	static final float[] WHAT_COST = {3.0F, 4.0F, 2.0F, 3.0F, 3.0F, 3.0F, 4.0F, 3.0F, 3.0F, 8.0F, 1.0F, 3.0F};
	/** How much more it costs for whom: yourself, your allies near, your foes near, the one you look at, everyone near. */
	static final float[] WHO_COST = {1.0F, 1.5F, 2.0F, 1.0F, 2.5F};
	/** How the length weighs on the cost (seconds / 5, to this power). */
	static final double LENGTH_WEIGHT = 0.6;
	public static final String[] WHO_NAMES = {"self", "allies", "foes", "target", "everyone"};
	public static final String[] WHAT_NAMES = {"mend", "wither", "swift", "slow", "strong", "weak", "hard", "float", "burn", "mute", "glow", "blind"};

	/** What each oath lets its Paladin write as true. */
	static final Map<String, List<Integer>> OATH_RULES = Map.of(
		"mercy", List.of(MEND, SWIFT, HARD, SLOW, GLOW),
		"justice", List.of(GLOW, SLOW, WEAK, MUTE, BLIND),
		"honour", List.of(STRONG, HARD, SWIFT, WEAK, GLOW));
	/** What each patron lets its Warlock write as true. */
	static final Map<String, List<Integer>> PACT_RULES = Map.ofEntries(
		Map.entry("hearthkeeper", List.of(MEND, HARD, SLOW, GLOW)),
		Map.entry("anvil_father", List.of(HARD, STRONG, BURN, SLOW)),
		Map.entry("deep_mother", List.of(FLOAT, SWIFT, SLOW, MEND, BLIND)),
		Map.entry("green_huntsman", List.of(SWIFT, GLOW, SLOW, WEAK, MEND)),
		Map.entry("lantern_warden", List.of(GLOW, BLIND, MUTE, MEND)),
		Map.entry("crowned_sun", List.of(BURN, STRONG, GLOW, BLIND, MEND)),
		Map.entry("ash_whisperer", List.of(WITHER, WEAK, BLIND, BURN, SLOW)),
		Map.entry("reaper", List.of(WITHER, WEAK, SLOW, BLIND)),
		Map.entry("truth", List.of(MEND, MUTE, GLOW, HARD)));

	/** The rules written, in order (saved, kept through death, seen by their writer's client). */
	public static final AttachmentType<List<Integer>> RULES = AttachmentRegistry.create(JaymosKingdoms.id("vow_rules"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST)), AttachmentSyncPredicate.targetOnly()));
	/** The terms sworn beyond the vow's own (saved, kept through death, seen by their writer's client). */
	public static final AttachmentType<List<String>> SWORN = AttachmentRegistry.create(JaymosKingdoms.id("vow_sworn"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MOST)), AttachmentSyncPredicate.targetOnly()));
	/** Whether a Paladin or Warlock who swore before the book existed has been given theirs. */
	static final AttachmentType<Boolean> GIVEN = AttachmentRegistry.create(JaymosKingdoms.id("vow_book_given"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	public static Item ITEM;
	/** Set by the client: open the book. */
	public static Consumer<Player> open = player -> {
	};

	/** Client → server: the book as written now (rules, sworn terms, the rule picked). */
	public record Write(List<Integer> rules, List<String> sworn, int picked) implements CustomPacketPayload {
		public static final Type<Write> TYPE = new Type<>(JaymosKingdoms.id("vow_write"));
		public static final StreamCodec<ByteBuf, Write> RAW = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST)), Write::rules,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MOST)), Write::sworn,
			ByteBufCodecs.VAR_INT, Write::picked, Write::new);
		public static final StreamCodec<RegistryFriendlyByteBuf, Write> CODEC = RAW.cast();

		@Override
		public Type<Write> type() {
			return TYPE;
		}
	}

	/** Client → server: every rule running ends. */
	public record Repeal() implements CustomPacketPayload {
		public static final Type<Repeal> TYPE = new Type<>(JaymosKingdoms.id("vow_repeal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Repeal> CODEC = StreamCodec.unit(new Repeal());

		@Override
		public Type<Repeal> type() {
			return TYPE;
		}
	}

	private static final class Running {
		int rule;
		long until;
		float paid;
		@Nullable LivingEntity target;
		final Set<LivingEntity> held = new HashSet<>();
	}

	private static final class Live {
		float ink = INK_MOST;
		int picked;
		final List<Running> running = new ArrayList<>();
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	/** Everyone a running rule says has no powers (how many rules say so). */
	private static final Map<UUID, Integer> MUTED = new HashMap<>();

	private VowBook() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("vow_book"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Book(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.WRITABLE_BOOK, ITEM));
		PayloadTypeRegistry.serverboundPlay().register(Write.TYPE, Write.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Repeal.TYPE, Repeal.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Write.TYPE, (payload, ctx) -> write(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Repeal.TYPE, (payload, ctx) -> repealAll(ctx.player(), true));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> giveOnce(handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			repealAll(handler.player, false);
			LIVE.remove(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(VowBook::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			MUTED.clear();
		});
	}

	// ---- who writes, and what ----------------------------------------------------------------------------------------

	/** The class whose vow {@code player} keeps in the book: Paladin, Warlock, or null. Both sides. */
	public static @Nullable PlayerClass vowClass(Player player) {
		if (Classes.is(player, PlayerClass.PALADIN)) {
			return PlayerClass.PALADIN;
		}
		return Classes.is(player, PlayerClass.WARLOCK) ? PlayerClass.WARLOCK : null;
	}

	/** The oath ("mercy"...) or patron ("hearthkeeper"...) the book is of; "" if none. Both sides. */
	public static String vow(Player player) {
		PlayerClass cls = vowClass(player);
		return cls == null ? "" : Classes.optionOf(player, cls);
	}

	/** What {@code player}'s oath or patron lets them write as true. Both sides. */
	public static List<Integer> allowed(Player player) {
		PlayerClass cls = vowClass(player);
		if (cls == null) {
			return List.of();
		}
		Map<String, List<Integer>> rules = cls == PlayerClass.PALADIN ? OATH_RULES : PACT_RULES;
		return rules.getOrDefault(vow(player), List.of(MEND, GLOW));
	}

	/** The vow's own terms, which can't be struck out. Both sides. */
	public static Set<String> ownTerms(Player player) {
		PlayerClass cls = vowClass(player);
		if (cls == null) {
			return Set.of();
		}
		Set<String> own = new LinkedHashSet<>((cls == PlayerClass.PALADIN ? Vows.OATHS : Vows.PACTS).getOrDefault(vow(player), Set.of()));
		if ("truth".equals(vow(player))) {
			own.addAll(Vows.TRUTH);
		}
		return own;
	}

	/** Every term a vow knows that {@code player} could swear on top of their own, in a steady order. Both sides. */
	public static List<String> swearable(Player player) {
		Set<String> all = new java.util.TreeSet<>();
		Vows.OATHS.values().forEach(all::addAll);
		Vows.PACTS.values().forEach(all::addAll);
		all.addAll(Vows.TRUTH);
		all.removeAll(ownTerms(player));
		return List.copyOf(all);
	}

	/** The terms {@code player} swore beyond their vow's own. Both sides. */
	public static List<String> sworn(Player player) {
		List<String> sworn = player.getAttached(SWORN);
		return sworn == null ? List.of() : sworn;
	}

	/** The rule lines {@code player} may write: one, and one more a sworn term. Both sides. */
	public static int lines(Player player) {
		return Math.min(MOST, 1 + sworn(player).size());
	}

	/** {@code player}'s level in the class the book is of. */
	private static int level(Player player) {
		PlayerClass cls = vowClass(player);
		return cls == null ? 0 : ClassTrees.level(player, cls);
	}

	/** The strongest a rule may be for {@code player} now. Both sides. */
	public static int strongest(Player player) {
		return Math.min(5, 1 + level(player) / LEVELS_A_STRENGTH);
	}

	/** How many rules may run at once. Both sides. */
	public static int atOnce(Player player) {
		return level(player) >= THREE_AT ? 3 : 1;
	}

	/** The rules written (a fresh book: "I mend, strength 1, for 15 seconds", or the vow's first rule). Both sides. */
	public static List<Integer> written(Player player) {
		List<Integer> rules = player.getAttached(RULES);
		if (rules != null && !rules.isEmpty()) {
			return rules;
		}
		List<Integer> allowed = allowed(player);
		return List.of(pack(SELF, allowed.isEmpty() ? MEND : allowed.getFirst(), 1, 1));
	}

	public static int pack(int who, int what, int strength, int length) {
		return clamp(who, WHO_COUNT) | clamp(what, WHAT_COUNT) << 4 | Math.max(1, Math.min(5, strength)) << 8 | clamp(length, SECONDS.length) << 12;
	}

	private static int clamp(int v, int count) {
		return Math.max(0, Math.min(count - 1, v));
	}

	public static int who(int rule) {
		return clamp(rule & 0xF, WHO_COUNT);
	}

	public static int what(int rule) {
		return clamp(rule >> 4 & 0xF, WHAT_COUNT);
	}

	public static int strength(int rule) {
		return Math.max(1, Math.min(5, rule >> 8 & 0xF));
	}

	public static int length(int rule) {
		return clamp(rule >> 12 & 0xF, SECONDS.length);
	}

	/** What a rule costs to enact (ink). */
	public static float cost(int rule) {
		return (float) (WHAT_COST[what(rule)] * strength(rule) * Math.pow(SECONDS[length(rule)] / 5.0, LENGTH_WEIGHT) * WHO_COST[who(rule)]);
	}

	/** The rule as a line: "The foes near me are slowed (strength 3, 30 s)". */
	public static Component sentence(int rule) {
		return Component.translatable("vow.jaymoskingdoms.sentence", Component.translatable("vow.jaymoskingdoms.who." + WHO_NAMES[who(rule)]),
			Component.translatable("vow.jaymoskingdoms.what." + WHAT_NAMES[what(rule)] + (who(rule) == SELF ? ".self" : "")), strength(rule),
			SECONDS[length(rule)]);
	}

	/** Jaymo's Kingdoms' {@link Vows} asks: whether {@code player} swore {@code term} beyond their vow's own. */
	static boolean swore(ServerPlayer player, String term) {
		List<String> sworn = player.getAttached(SWORN);
		return sworn != null && sworn.contains(term);
	}

	// ---- the book itself ---------------------------------------------------------------------------------------------

	/** A Paladin or Warlock is sworn: their book, if they have none on them. */
	static void give(ServerPlayer player) {
		player.setAttached(GIVEN, true);
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(ITEM)) {
				return;
			}
		}
		ItemStack book = new ItemStack(ITEM);
		if (!inv.add(book)) {
			player.spawnAtLocation(player.level(), book);
		}
	}

	/** One who swore before the book existed is given theirs, once. */
	private static void giveOnce(ServerPlayer player) {
		if (vowClass(player) != null && !player.getAttachedOrElse(GIVEN, false)) {
			give(player);
		}
	}

	/** The class is gone: what runs ends, and the book's terms and rules with it. */
	static void unmake(ServerPlayer player) {
		repealAll(player, false);
		LIVE.remove(player.getUUID());
		player.removeAttached(RULES);
		player.removeAttached(SWORN);
	}

	/** The book as the client wrote it: checked against what the vow allows; a sworn term struck out is broken. */
	private static void write(ServerPlayer player, Write payload) {
		PlayerClass cls = vowClass(player);
		if (cls == null) {
			return;
		}
		// the terms: only ones a vow knows and that aren't the vow's own already
		List<String> swearable = swearable(player);
		List<String> terms = new ArrayList<>();
		for (String t : payload.sworn()) {
			if (swearable.contains(t) && !terms.contains(t) && terms.size() < MOST - 1) {
				terms.add(t);
			}
		}
		List<String> struck = new ArrayList<>(sworn(player));
		struck.removeAll(terms);
		player.setAttached(SWORN, List.copyOf(terms));
		if (!struck.isEmpty() && !player.isCreative()) {
			// a term sworn is a term kept: striking it out breaks it
			if (cls == PlayerClass.PALADIN) {
				Paladins.breakOath(player, "struck");
			} else {
				Warlocks.collect(player);
			}
		}
		// the rules: as many as there are lines, each held to what the vow allows and how strong they can be
		List<Integer> allowed = allowed(player);
		List<Integer> kept = new ArrayList<>();
		for (int r : payload.rules()) {
			if (kept.size() >= lines(player) || !allowed.contains(what(r))) {
				continue;
			}
			kept.add(pack(who(r), what(r), Math.min(strength(r), strongest(player)), length(r)));
		}
		if (kept.isEmpty()) {
			player.removeAttached(RULES);
		} else {
			player.setAttached(RULES, List.copyOf(kept));
		}
		live(player).picked = Math.max(0, Math.min(payload.picked(), written(player).size() - 1));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	/** The book's item: use to read and write; sneak and use to enact the rule picked. */
	static final class Book extends Item {
		Book(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (vowClass(player) == null) {
				if (!level.isClientSide()) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow_book.unsworn").withStyle(ChatFormatting.GRAY));
				}
				return InteractionResult.SUCCESS;
			}
			if (player.isShiftKeyDown()) {
				if (player instanceof ServerPlayer sp) {
					enact(sp);
				}
				return InteractionResult.SUCCESS;
			}
			if (level.isClientSide()) {
				open.accept(player);
			} else {
				level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---- enacting ----------------------------------------------------------------------------------------------------

	private static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player.getUUID(), id -> new Live());
	}

	/** The rule picked in the book, enacted (if the vow lets them and the ink allows). */
	static void enact(ServerPlayer player) {
		PlayerClass cls = vowClass(player);
		if (cls == PlayerClass.PALADIN && !Paladins.keeping(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.broken"));
			return;
		}
		if (cls == PlayerClass.WARLOCK && withheld(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.withheld", Warlocks.debtName(Warlocks.pact(player).kind())));
			return;
		}
		ServerLevel level = player.level();
		Live live = live(player);
		List<Integer> rules = written(player);
		int rule = rules.get(Math.min(live.picked, rules.size() - 1));
		if (!allowed(player).contains(what(rule))) {
			return;
		}
		if (strength(rule) > strongest(player)) {
			rule = pack(who(rule), what(rule), strongest(player), length(rule));
		}
		if (live.running.size() >= atOnce(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow_book.full", atOnce(player)).withStyle(ChatFormatting.GRAY));
			return;
		}
		float cost = cost(rule);
		if (live.ink + 1.0E-3F < cost && !player.getAbilities().instabuild) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow_book.no_ink." + inkKind(cls), Math.round(cost), Math.round(live.ink))
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		Running run = new Running();
		run.rule = rule;
		run.until = level.getGameTime() + SECONDS[length(rule)] * 20L;
		run.paid = cost;
		if (who(rule) == TARGET) {
			run.target = Arcana.sighted(player);
			if (run.target == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow_book.no_one").withStyle(ChatFormatting.GRAY));
				return;
			}
		}
		if (!player.getAbilities().instabuild) {
			live.ink -= cost;
		}
		live.running.add(run);
		Fx.sigil(level, player.getEyePosition().add(player.getLookAngle().scale(0.8)), 0.6F, COLOUR, 20);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow_book.enacted", sentence(rule)).withStyle(ChatFormatting.GOLD));
		apply(level, player, run);
	}

	private static String inkKind(@Nullable PlayerClass cls) {
		return cls == PlayerClass.WARLOCK ? "credit" : "grace";
	}

	/** A Warlock owing past the patron's grace: no favours until it's paid. */
	private static boolean withheld(ServerPlayer player) {
		Warlocks.Pact pact = Warlocks.pact(player);
		return !player.isCreative() && pact.owing() && pact.casts() >= Warlocks.GRACE_CASTS;
	}

	/** Who the rule is about, now. */
	private static List<LivingEntity> about(ServerLevel level, ServerPlayer player, Running run) {
		int who = who(run.rule);
		if (who == SELF) {
			return List.of(player);
		}
		if (who == TARGET) {
			return run.target != null && run.target.isAlive() && run.target.level() == level ? List.of(run.target) : List.of();
		}
		return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(NEAR), e -> e != player && e.isAlive() && !e.isSpectator()
			&& e.distanceTo(player) <= NEAR && switch (who) {
				case ALLIES -> player.isAlliedTo(e) || ArtKeys.friend(player, e);
				case FOES -> ArtKeys.foe(player, e);
				default -> true;
			});
	}

	/** One second of a running rule on everyone it is about. */
	private static void apply(ServerLevel level, ServerPlayer player, Running run) {
		int s = strength(run.rule);
		int what = what(run.rule);
		List<LivingEntity> about = about(level, player, run);
		if (what == MUTE) {
			// who it no longer covers has their powers back
			for (Iterator<LivingEntity> it = run.held.iterator(); it.hasNext();) {
				LivingEntity e = it.next();
				if (!about.contains(e)) {
					unmute(e);
					it.remove();
				}
			}
		}
		for (LivingEntity e : about) {
			switch (what) {
				case MEND -> effect(e, player, MobEffects.REGENERATION, s - 1);
				case WITHER -> e.hurtServer(level, player.damageSources().indirectMagic(player, player), WITHER_PER * s);
				case SWIFT -> effect(e, player, MobEffects.SPEED, s - 1);
				case SLOW -> effect(e, player, MobEffects.SLOWNESS, s - 1);
				case STRONG -> effect(e, player, MobEffects.STRENGTH, s - 1);
				case WEAK -> effect(e, player, MobEffects.WEAKNESS, s - 1);
				case HARD -> effect(e, player, MobEffects.RESISTANCE, Math.min(3, s - 1));
				case FLOAT -> {
					effect(e, player, MobEffects.LEVITATION, s - 1);
					e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true), player);
				}
				case BURN -> e.igniteForSeconds(BURN_PER * s);
				case MUTE -> {
					if (run.held.add(e)) {
						MUTED.merge(e.getUUID(), 1, Integer::sum);
						e.addTag(Will.RULED_TAG);
					}
				}
				case GLOW -> effect(e, player, MobEffects.GLOWING, 0);
				case BLIND -> {
					effect(e, player, MobEffects.BLINDNESS, 0);
					if (s >= 3) {
						effect(e, player, MobEffects.DARKNESS, 0);
					}
				}
				default -> {
				}
			}
			Vec3 c = e.getBoundingBox().getCenter();
			level.sendParticles(Fx.rune(COLOUR, 0.6F), c.x, c.y + e.getBbHeight() * 0.5, c.z, 1, 0.2, 0.1, 0.2, 0.01);
		}
	}

	private static void effect(LivingEntity e, ServerPlayer by, Holder<MobEffect> effect, int amplifier) {
		e.addEffect(new MobEffectInstance(effect, 40, Math.max(0, amplifier), false, true), by);
	}

	private static void unmute(LivingEntity e) {
		Integer n = MUTED.get(e.getUUID());
		if (n == null || n <= 1) {
			MUTED.remove(e.getUUID());
			e.removeTag(Will.RULED_TAG);
		} else {
			MUTED.put(e.getUUID(), n - 1);
		}
	}

	private static void end(Running run) {
		run.held.forEach(VowBook::unmute);
		run.held.clear();
	}

	/** Every rule of theirs ends ({@code refund}: a share of the ink left in them comes back). */
	static void repealAll(ServerPlayer player, boolean refund) {
		Live live = LIVE.get(player.getUUID());
		if (live == null || live.running.isEmpty()) {
			return;
		}
		long now = player.level().getGameTime();
		for (Running run : live.running) {
			end(run);
			if (refund) {
				float left = Math.max(0.0F, (run.until - now) / (SECONDS[length(run.rule)] * 20.0F));
				live.ink = Math.min(INK_MOST, live.ink + run.paid * left * REPEAL_BACK);
			}
		}
		live.running.clear();
		if (refund) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 0.8F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow_book.repealed").withStyle(ChatFormatting.GOLD));
		}
	}

	/** Each second: ink comes back, rules run (and end when their time is up, or their writer can't keep them). */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || LIVE.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Live live = LIVE.get(player.getUUID());
			if (live == null) {
				continue;
			}
			PlayerClass cls = vowClass(player);
			if (cls == null || cls == PlayerClass.PALADIN && !Paladins.keeping(player) || !player.isAlive() || Downed.isDowned(player)) {
				repealAll(player, false);
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			live.ink = Math.min(INK_MOST, live.ink + INK_REGEN * (live.running.isEmpty() ? 2.0F : 1.0F));
			for (Iterator<Running> it = live.running.iterator(); it.hasNext();) {
				Running run = it.next();
				if (now >= run.until) {
					end(run);
					it.remove();
					continue;
				}
				apply(level, player, run);
			}
			if (!live.running.isEmpty() || live.ink < INK_MOST && player.getMainHandItem().is(ITEM)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.vow_book.ink." + inkKind(cls), gauge(live.ink / INK_MOST),
					Math.round(live.ink), live.running.size(), atOnce(player)).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static String gauge(float share) {
		int full = Math.round(Math.max(0.0F, Math.min(1.0F, share)) * 10);
		return "|".repeat(full) + ".".repeat(10 - full);
	}
}
