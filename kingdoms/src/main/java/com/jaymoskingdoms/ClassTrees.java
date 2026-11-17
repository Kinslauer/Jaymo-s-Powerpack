package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.trees.TreeLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Class levels and class trees (docs/ClassTrees-Plan.md). Every class has its own level, 0 to {@link #MAX_LEVEL}, beside the
 * pillars, with its own experience earned by doing the class's work ({@link #give}); each level is a point spent only in
 * that class's own tree, on the K screen's class tab. The pillars' points stay free for the general skills.
 *
 * <p>A tree is a set of {@link Node}s: the class's own perks, and the old class pages that moved off the pillars (Rogue's
 * Arts, Spellcraft, Sorcery and Forbidden Arts, Archery, Warfare), which keep their {@link Skills.Perk} and so their saved
 * ranks: everything that reads a rank by its perk still works. A node may belong to more than one class (the Workshop's
 * bench is the Marksman's and the Tinkerer's); a rank is paid from whichever of the player's classes owns it and has the
 * points, and the key it was paid from is saved beside it, so each class's points count only what they paid for.
 *
 * <p>Crossroads: two (or three) nodes that face each other ({@link Node#forks}); a rank in one shuts the others until a
 * respec. A second class levels at {@link Classes#SECOND_SHARE} speed and its perks work at that share ({@link #strength}).
 * A respec (the court wizard, a bottle o' enchanting) gives the class points back and keeps the levels; a Writ of Rebirth
 * unmakes the class and its level ({@link #unmake}). A character made before class trees came starts at the class level
 * its old pages were bought at, and keeps every rank ({@link #migrate}).
 *
 * <p>A player may also start with no class yet ({@link Classes#NONE_KEY}) and learn one later from a class's teacher.
 */
public final class ClassTrees {
	/** A class's highest level. */
	public static final int MAX_LEVEL = 25;
	/** From class level {@code l} to the next takes {@code XP_BASE + XP_STEP * l} (the plan's numbers). */
	public static final int XP_BASE = 30, XP_STEP = 15;
	/** Practice: each class level makes the class key's starting move this much stronger by itself ({@link #practice}). */
	public static final float PRACTICE_PER_LEVEL = 0.02F;
	/** Every this many class levels is a milestone: the big fanfare (no bonus point: the trees are counted to the level). */
	static final int MILESTONE_EVERY = 5;
	/** Where a class's experience is saved in {@link Skills.Progress#xp()}: this, then the class's key. */
	static final String XP_PREFIX = "class_";
	/** Saved in the experience map once a character has been moved onto class trees. */
	static final String MIGRATED = "_classtrees";

	/**
	 * Class experience, by source (docs/ClassTrees-Plan.md's table). A blow-by-blow source gives at most one grant every
	 * {@link #BLOW_EVERY} ticks per class, so a flurry can't be farmed.
	 */
	public static final int XP_BLOW = 1, XP_MOUNTED_BLOW = 2, XP_FOE_HIT = 2, XP_LONG_SHOT = 3, XP_BLOCK = 1, XP_RAGE_KILL = 2,
		XP_SPELL = 2, XP_SPELL_LANDED = 1, XP_SCROLL = 10, XP_SIGIL = 1, XP_PACT = 2, XP_DEBT = 8, XP_TURNING = 3, XP_HEAL = 2,
		XP_BLESS = 3, XP_RITE = 4, XP_SMITE = 3, XP_HANDS = 2, XP_OATH_DAY = 10, XP_STUDY = 8, XP_SHAPE = 1, XP_CATCH = 3,
		XP_STUN = 2, XP_TRACK = 2, XP_MADE = 3, XP_RIDDEN = 1, XP_JUMP = 1, XP_DISCIPLINE = 5, XP_INFUSE = 3, XP_STUDIED = 8,
		XP_POCKET = 2, XP_LOCK = 3, XP_THEFT = 2, XP_SNEAK = 2, XP_ORDER = 1;
	static final long BLOW_EVERY = 30L;
	/**
	 * (Scaling audit) Class experience earned in a tick that also paid pillar experience is worth this share, so one blow
	 * doesn't pay in full twice. The rest is kept as a fraction toward the next.
	 */
	static final float SHARED_XP = 0.5F;
	/** The tag Jaymo's Powers puts on a player for gear made at a tool station ({@code Tinkering.MADE_TAG}); taken off as it's read. */
	static final String MADE_TAG = "jaymospowers_made";
	/** A long shot (a Marksman's) is one from this far or further (blocks). */
	static final double LONG_SHOT = 32.0;
	/** Riding: this many blocks in the saddle is {@link #XP_RIDDEN}. */
	static final double RIDDEN_BLOCKS = 60.0;
	/** A Druid in a shape gets {@link #XP_SHAPE} this often (ticks). */
	static final int SHAPE_EVERY = 600;

	/** The classes' colours (their tab and fanfare). */
	static final Map<PlayerClass, Integer> COLOURS = new EnumMap<>(PlayerClass.class);

	static {
		COLOURS.put(PlayerClass.THIEF, 0x7A6FD0);
		COLOURS.put(PlayerClass.WARLORD, 0xC0392B);
		COLOURS.put(PlayerClass.MAGE, 0x4A7BD0);
		COLOURS.put(PlayerClass.BRUTE, 0xD06A2F);
		COLOURS.put(PlayerClass.MARKSMAN, 0x9A8A6A);
		COLOURS.put(PlayerClass.SORCERER, 0xB04AD0);
		COLOURS.put(PlayerClass.RIDER, 0xA0703A);
		COLOURS.put(PlayerClass.CLERIC, 0xE8D890);
		COLOURS.put(PlayerClass.DRUID, 0x5AAF4A);
		COLOURS.put(PlayerClass.MONK, 0xE0A040);
		COLOURS.put(PlayerClass.PALADIN, 0xFFE9A0);
		COLOURS.put(PlayerClass.RANGER, 0x3E8A5A);
		COLOURS.put(PlayerClass.WARLOCK, 0x7A3AA0);
		COLOURS.put(PlayerClass.TINKERER, 0xC08A3A);
	}

	/**
	 * A perk in a class tree. {@code perk}: an old page's perk that moved here (its ranks are saved under its own key), or
	 * null for a tree's own (saved under "ct_" and its key). {@code row}/{@code col}: where it sits on the tab (rows from the
	 * top, columns 0 to {@link #COLUMNS} - 1). {@code requires}: node keys it needs a rank in first. {@code forks}: the nodes
	 * it faces at a crossroads (a rank in any shuts the rest).
	 */
	public record Node(String key, Set<PlayerClass> classes, int maxRank, int cost, int minLevel, int row, int col, List<String> requires,
		List<String> forks, Skills.@Nullable Perk perk) {
		/** The key its ranks are saved under. */
		public String rankKey() {
			return this.perk != null ? this.perk.key() : "ct_" + this.key;
		}

		/** The key the ranks paid from {@code cls}'s points are saved under. */
		public String paidKey(PlayerClass cls) {
			return rankKey() + "@" + XP_PREFIX + cls.key();
		}

		/** Its name's translation key (an old perk keeps its own). */
		public String nameKey() {
			return this.perk != null ? "skill.jaymoskingdoms.perk." + this.perk.key() : "classtree.jaymoskingdoms." + this.key;
		}

		public String descKey() {
			return nameKey() + ".desc";
		}

		public boolean of(PlayerClass cls) {
			return this.classes.contains(cls);
		}
	}

	/** The tab's columns. */
	public static final int COLUMNS = 5;

	private static final Map<String, Node> NODES = new LinkedHashMap<>();
	private static final Map<Skills.Perk, Node> BY_PERK = new EnumMap<>(Skills.Perk.class);
	private static boolean built;
	private static volatile boolean ready;

	private ClassTrees() {
	}

	/** Adds a tree's own perk. */
	public static Node add(String key, Set<PlayerClass> classes, int maxRank, int cost, int minLevel, int row, int col, List<String> requires,
		List<String> forks) {
		Node node = new Node(key, Set.copyOf(classes), maxRank, cost, minLevel, row, col, List.copyOf(requires), List.copyOf(forks), null);
		NODES.put(key, node);
		return node;
	}

	/** Moves an old page's {@code perk} into the tree of {@code classes}, at {@code minLevel} there. */
	public static Node moved(Skills.Perk perk, Set<PlayerClass> classes, int minLevel, int row, int col, String... requires) {
		Node node = new Node(perk.key(), Set.copyOf(classes), perk.maxRank, perk.cost, minLevel, row, col, List.of(requires), List.of(), perk);
		NODES.put(node.key(), node);
		BY_PERK.put(perk, node);
		return node;
	}

	/**
	 * What a class could do before class trees that is a tree perk now (a Monk's stunning blow, a Paladin's smite): a
	 * character who held the class when they moved onto the trees keeps it, as a free rank ({@link #migrate}).
	 */
	private static final List<Kept> KEPT = new ArrayList<>();
	/** Saved beside a kept rank, so a respec leaves it (as it leaves what a trainer taught). */
	static final String KEPT_MARK = "@kept";

	/** A tree perk {@code key} kept free by whoever held {@code cls} on the move (and, if {@code had} isn't null, a rank saved under it). */
	private record Kept(PlayerClass cls, @Nullable String had, String key) {
	}

	/** {@code keys} were {@code cls}'s from the start before class trees: kept free by those who had the class then. */
	public static void kept(PlayerClass cls, String... keys) {
		for (String key : keys) {
			KEPT.add(new Kept(cls, null, key));
		}
	}

	/** As {@link #kept(PlayerClass, String...)}, for those who had learned {@code had} (a rank key) as well. */
	public static void keptIf(PlayerClass cls, String had, String... keys) {
		for (String key : keys) {
			KEPT.add(new Kept(cls, had, key));
		}
	}

	/** Every tree, built once (each class's own file adds its nodes: {@link TreeLayout}). */
	private static void build() {
		if (ready) {
			return;
		}
		// the server and the client thread can both ask first (singleplayer): one fills the trees, the other waits
		synchronized (ClassTrees.class) {
			if (!built) {
				built = true;
				TreeLayout.build();
				ready = true;
			}
		}
	}

	public static List<Node> all() {
		build();
		return List.copyOf(NODES.values());
	}

	public static @Nullable Node node(String key) {
		build();
		return NODES.get(key);
	}

	/** {@code cls}'s tree. */
	public static List<Node> tree(PlayerClass cls) {
		build();
		List<Node> out = new ArrayList<>();
		for (Node n : NODES.values()) {
			if (n.of(cls)) {
				out.add(n);
			}
		}
		return out;
	}

	/** The node an old page's {@code perk} became, or null when it stayed on a pillar. */
	public static @Nullable Node moved(Skills.Perk perk) {
		build();
		return BY_PERK.get(perk);
	}

	public static int colour(PlayerClass cls) {
		return COLOURS.getOrDefault(cls, 0xFFD54A);
	}

	// ---- levels and points -------------------------------------------------------------------------------------------

	public static int toNext(int level) {
		return XP_BASE + XP_STEP * level;
	}

	public static int total(int level) {
		int sum = 0;
		for (int l = 0; l < level; l++) {
			sum += toNext(l);
		}
		return sum;
	}

	public static int levelOf(int xp) {
		int level = 0;
		while (level < MAX_LEVEL && xp >= total(level + 1)) {
			level++;
		}
		return level;
	}

	public static int xp(Skills.Progress p, PlayerClass cls) {
		return p.xp().getOrDefault(XP_PREFIX + cls.key(), 0);
	}

	public static int level(Skills.Progress p, PlayerClass cls) {
		return levelOf(xp(p, cls));
	}

	public static int level(Player player, PlayerClass cls) {
		return level(Skills.of(player), cls);
	}

	public static int rank(Skills.Progress p, Node node) {
		return p.ranks().getOrDefault(node.rankKey(), 0);
	}

	public static int rank(Player player, String key) {
		Node node = node(key);
		return node == null ? 0 : rank(Skills.of(player), node);
	}

	/** Points left to spend in {@code cls}'s tree: one per class level, less what was paid from it. */
	public static int points(Skills.Progress p, PlayerClass cls) {
		int spent = 0;
		for (Node n : all()) {
			if (n.of(cls)) {
				spent += p.ranks().getOrDefault(n.paidKey(cls), 0) * n.cost();
			}
		}
		return level(p, cls) - spent;
	}

	/** How many ranks of the trees' own perks are learned (the old pages' are counted with the pillars'): the respec's price. */
	static int spent(Skills.Progress p) {
		int n = 0;
		for (Node node : all()) {
			if (node.perk() == null) {
				n += rank(p, node);
			}
		}
		return n;
	}

	/**
	 * Practice: how much stronger {@code player}'s starting move for {@code cls} is by their level alone (1 at level 0). A
	 * second class's practice counts at its share.
	 */
	public static float practice(Player player, PlayerClass cls) {
		return 1.0F + PRACTICE_PER_LEVEL * level(player, cls) * Math.max(Classes.share(player, cls), Classes.SECOND_SHARE);
	}

	/** {@code node}'s ranks as they work for {@code player}: a second class's at its share. */
	public static float strength(Player player, Node node) {
		int rank = rank(Skills.of(player), node);
		if (rank == 0) {
			return 0.0F;
		}
		float share = 0.0F;
		for (PlayerClass cls : node.classes()) {
			share = Math.max(share, Classes.share(player, cls));
		}
		return rank * (player.isCreative() ? 1.0F : share);
	}

	/** {@code player}'s rank in tree perk {@code key}, as a whole number of working ranks (a second class's rounds down, at least one). */
	public static int has(Player player, String key) {
		Node node = node(key);
		if (node == null) {
			return 0;
		}
		float s = strength(player, node);
		return s <= 0.0F ? 0 : Math.max(1, (int) s);
	}

	/** {@code player}'s working ranks in tree perk {@code key}, as a share (a second class's at its share): for scaling an effect. */
	public static float power(Player player, String key) {
		Node node = node(key);
		return node == null ? 0.0F : strength(player, node);
	}

	private static final Map<UUID, Long> TOLD = new HashMap<>();

	/**
	 * Whether {@code player} has tree perk {@code key} (creative players always do); if not, the action bar names the perk
	 * with a dull sound, as a locked ability does ({@link TreeLocks#refuse}), at most every {@link TreeLocks#TELL_EVERY} ticks.
	 */
	public static boolean check(ServerPlayer player, String key) {
		if (player.isCreative() || has(player, key) > 0) {
			return true;
		}
		Node node = node(key);
		long now = player.level().getGameTime();
		Long last = TOLD.get(player.getUUID());
		if (node != null && (last == null || now - last >= TreeLocks.TELL_EVERY || now < last)) {
			TOLD.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tree_lock.locked", Component.translatable(node.nameKey())));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5F, 0.7F);
		}
		return false;
	}

	/** The class among {@code player}'s that would pay for a rank of {@code node}: the one with the most points, if enough. */
	static @Nullable PlayerClass payer(Player player, Skills.Progress p, Node node) {
		PlayerClass best = null;
		int most = -1;
		for (PlayerClass cls : Classes.both(player)) {
			if (node.of(cls) && level(p, cls) >= node.minLevel()) {
				int pts = points(p, cls);
				if (pts >= node.cost() && pts > most) {
					best = cls;
					most = pts;
				}
			}
		}
		return best;
	}

	/** Why {@code node} can't take another rank for {@code player} now (a translation key), or null if it can. */
	public static @Nullable String blocked(Player player, Node node) {
		Skills.Progress p = Skills.of(player);
		List<PlayerClass> held = Classes.both(player);
		if (held.stream().noneMatch(node::of) && !player.isCreative()) {
			return "gui.jaymoskingdoms.classtree.blocked.class";
		}
		if (!SunArts.learnable(player, node.key())) {
			return "gui.jaymoskingdoms.classtree.blocked.sun";
		}
		if (rank(p, node) >= node.maxRank()) {
			return "gui.jaymoskingdoms.skills.blocked.max";
		}
		for (String f : node.forks()) {
			Node other = node(f);
			if (other != null && rank(p, other) > 0) {
				return "gui.jaymoskingdoms.classtree.blocked.fork";
			}
		}
		for (String need : node.requires()) {
			Node other = node(need);
			if (other != null && rank(p, other) == 0) {
				return "gui.jaymoskingdoms.skills.blocked.requires";
			}
		}
		if (held.stream().filter(node::of).noneMatch(c -> level(p, c) >= node.minLevel())) {
			return "gui.jaymoskingdoms.skills.blocked.level";
		}
		if (payer(player, p, node) == null) {
			return "gui.jaymoskingdoms.skills.blocked.points";
		}
		return null;
	}

	// ---- spending ----------------------------------------------------------------------------------------------------

	/** Player → server: a rank of tree perk {@code key}. */
	public record Spend(String key) implements CustomPacketPayload {
		public static final Type<Spend> TYPE = new Type<>(JaymosKingdoms.id("class_tree_spend"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spend> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeUtf(p.key), buf -> new Spend(buf.readUtf(64)));

		@Override
		public Type<Spend> type() {
			return TYPE;
		}
	}

	private static void spend(ServerPlayer player, String key) {
		Node node = node(key);
		if (node == null || blocked(player, node) != null) {
			return;
		}
		Skills.Progress p = Skills.of(player);
		Skills.Perk perk = node.perk();
		if (perk != null && (Knowledge.lockedPerk(player, perk) || TreeLocks.hidden(player, perk))) {
			if (Knowledge.lockedPerk(player, perk)) {
				Knowledge.refuse(player, Knowledge.HIGHER_ARTS);
			}
			return;
		}
		PlayerClass payer = payer(player, p, node);
		if (payer == null) {
			return;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		ranks.merge(node.rankKey(), 1, Integer::sum);
		ranks.merge(node.paidKey(payer), 1, Integer::sum);
		player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
		if (perk != null) {
			Learnables.spent(player, perk);
		}
		Skills.refresh(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	// ---- experience --------------------------------------------------------------------------------------------------

	private static final Map<UUID, Map<String, Long>> NEXT_BLOW = new HashMap<>();
	private static final Map<UUID, Vec3> RIDE_FROM = new HashMap<>();
	private static final Map<UUID, Double> RIDDEN = new HashMap<>();
	/** Class experience earned this tick and not yet paid; each player's pillar experience at the last paying; the fractions kept. */
	private static final Map<UUID, Map<PlayerClass, Integer>> OWED = new HashMap<>();
	private static final Map<UUID, Long> PILLARS = new HashMap<>();
	private static final Map<UUID, Map<PlayerClass, Float>> PART = new HashMap<>();

	/**
	 * Gives {@code player} {@code amount} experience as {@code cls}, if they hold it: Wits and blood quicken it as they do the
	 * pillars', a second class learns at {@link Classes#SECOND_SHARE} speed. It is paid as the tick ends ({@link #settle}), at
	 * {@link #SHARED_XP} if a pillar was paid in the same tick. A level reached is announced.
	 */
	public static void give(ServerPlayer player, PlayerClass cls, int amount) {
		if (amount <= 0 || !Classes.is(player, cls)) {
			return;
		}
		float f = Stats.xpFactor(player) * Heritage.xpFactor(player) * (Classes.classOf(player) == cls ? 1.0F : Classes.SECOND_SHARE);
		OWED.computeIfAbsent(player.getUUID(), u -> new EnumMap<>(PlayerClass.class)).merge(cls, Math.max(1, Math.round(amount * f)), Integer::sum);
	}

	/** The tick's end: what each player earned as their classes is paid, at {@link #SHARED_XP} where a pillar was paid in the same tick. */
	private static void settle(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Skills.Progress p = Skills.of(player);
			long pillars = 0L;
			for (Skills.Pillar pillar : Skills.Pillar.values()) {
				pillars += p.xp(pillar);
			}
			Long before = PILLARS.put(player.getUUID(), pillars);
			Map<PlayerClass, Integer> owed = OWED.remove(player.getUUID());
			if (owed == null) {
				continue;
			}
			boolean shared = before != null && pillars > before;
			for (Map.Entry<PlayerClass, Integer> e : owed.entrySet()) {
				int add = e.getValue();
				if (shared) {
					Map<PlayerClass, Float> part = PART.computeIfAbsent(player.getUUID(), u -> new EnumMap<>(PlayerClass.class));
					float sum = part.getOrDefault(e.getKey(), 0.0F) + add * SHARED_XP;
					add = (int) sum;
					part.put(e.getKey(), sum - add);
				}
				pay(player, e.getKey(), add);
			}
		}
		// whoever left before the tick ended
		OWED.clear();
	}

	private static void pay(ServerPlayer player, PlayerClass cls, int add) {
		if (add <= 0 || !Classes.is(player, cls)) {
			return;
		}
		Skills.Progress p = Skills.of(player);
		int before = xp(p, cls);
		int after = (int) Math.min(total(MAX_LEVEL), (long) before + add);
		if (after == before) {
			return;
		}
		Map<String, Integer> xp = new HashMap<>(p.xp());
		xp.put(XP_PREFIX + cls.key(), after);
		player.setAttached(Skills.PROGRESS, new Skills.Progress(Map.copyOf(xp), p.ranks()));
		int was = levelOf(before), now = levelOf(after);
		if (now > was) {
			Component name = Component.translatable("class.jaymoskingdoms." + cls.key());
			boolean top = now >= MAX_LEVEL, milestone = now / MILESTONE_EVERY > was / MILESTONE_EVERY;
			int colour = colour(cls);
			if (!top && !milestone) {
				Fanfare.burst(player, colour, Fanfare.MINOR);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.classtree.level", name, now).withColor(colour));
				return;
			}
			Fanfare.fanfare(player, Component.translatable(top ? "message.jaymoskingdoms.classtree.top_title" : "message.jaymoskingdoms.classtree.level_title", name),
				Component.translatable("message.jaymoskingdoms.skills.level_subtitle", now),
				Component.translatable("message.jaymoskingdoms.classtree.milestone", name, now), colour, top ? Fanfare.GRAND : Fanfare.MAJOR);
		}
	}

	/** The same, at most once every {@link #BLOW_EVERY} ticks per class (blows, hits, blocks). */
	public static void giveBlow(ServerPlayer player, PlayerClass cls, int amount) {
		giveEvery(player, cls, amount, "blow", BLOW_EVERY);
	}

	/** The same, at most once every {@code ticks} for {@code what} (so a source can't be farmed). */
	public static void giveEvery(ServerPlayer player, PlayerClass cls, int amount, String what, long ticks) {
		if (!Classes.is(player, cls)) {
			return;
		}
		long now = player.level().getGameTime();
		Map<String, Long> next = NEXT_BLOW.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		String key = cls.key() + ":" + what;
		if (next.getOrDefault(key, 0L) > now) {
			return;
		}
		next.put(key, now + ticks);
		give(player, cls, amount);
	}

	/** Something made in a crafting grid: a Marksman's guns and shot, a Tinkerer's gadgets. */
	public static void crafted(Player player, ItemStack made) {
		if (!(player instanceof ServerPlayer sp) || made.isEmpty()) {
			return;
		}
		net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(made.getItem());
		String path = id.getPath();
		if (made.is(ClassPerks.GUNS) || path.contains("cartridge") || path.contains("bullet") || path.contains("musket_ball") || path.endsWith("_shot")) {
			giveEvery(sp, PlayerClass.MARKSMAN, XP_MADE, "made", 10L);
		}
		if (id.getNamespace().equals(JaymosKingdoms.MOD_ID) && Tinkerers.GADGETS.contains(path)) {
			giveEvery(sp, PlayerClass.TINKERER, XP_MADE, "made", 10L);
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Spend.TYPE, Spend.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Spend.TYPE, (payload, context) -> context.server().execute(() -> spend(context.player(), payload.key())));
		ServerPlayerEvents.JOIN.register(ClassTrees::migrate);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TOLD.clear();
			NEXT_BLOW.clear();
			RIDE_FROM.clear();
			RIDDEN.clear();
			OWED.clear();
			PILLARS.clear();
			PART.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ClassTrees::damaged);
		ServerLivingEntityEvents.AFTER_DEATH.register(ClassTrees::died);
		ServerTickEvents.END_SERVER_TICK.register(ClassTrees::tick);
		// a Warlord's parry (Jaymo's Powers' guard)
		PowersCompat.onParry((player, attacker) -> giveBlow(player, PlayerClass.WARLORD, XP_BLOCK));
	}

	/** Blows landed and taken: each class's own kind of fighting. */
	private static void damaged(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (victim instanceof ServerPlayer guard && blocked) {
			// a Warlord's block
			giveBlow(guard, PlayerClass.WARLORD, XP_BLOCK);
		}
		if (victim instanceof ServerPlayer brute && taken > 0.0F && BruteRage.raging(brute) > 0) {
			giveBlow(brute, PlayerClass.BRUTE, XP_BLOW);
		}
		if (!(source.getEntity() instanceof ServerPlayer player) || player == victim || taken <= 0.0F) {
			return;
		}
		Entity direct = source.getDirectEntity();
		boolean mounted = player.getVehicle() instanceof AbstractHorse;
		if (mounted) {
			giveBlow(player, PlayerClass.RIDER, XP_MOUNTED_BLOW);
		}
		if (direct == player) {
			ItemStack held = player.getMainHandItem();
			if (held.isEmpty() || Monks.fists(held)) {
				giveBlow(player, PlayerClass.MONK, XP_BLOW);
			}
			if (CombatArts.fullStrength(player, player.level().getGameTime())) {
				giveBlow(player, PlayerClass.WARLORD, XP_BLOW);
			}
			if (BruteRage.raging(player) > 0) {
				giveBlow(player, PlayerClass.BRUTE, XP_BLOW);
			}
			return;
		}
		if (direct instanceof AbstractArrow) {
			if (player.getMainHandItem().is(ClassPerks.GUNS) || player.getOffhandItem().is(ClassPerks.GUNS)) {
				giveBlow(player, PlayerClass.MARKSMAN, XP_BLOW);
				if (player.distanceTo(victim) >= LONG_SHOT) {
					give(player, PlayerClass.MARKSMAN, XP_LONG_SHOT);
				}
			} else {
				giveBlow(player, PlayerClass.RANGER, XP_BLOW);
			}
		} else if (direct != null && (player.getMainHandItem().is(ClassPerks.GUNS) || player.getOffhandItem().is(ClassPerks.GUNS))) {
			// a gun's own shot (Balkon's bullets are not arrows)
			giveBlow(player, PlayerClass.MARKSMAN, XP_BLOW);
			if (player.distanceTo(victim) >= LONG_SHOT) {
				give(player, PlayerClass.MARKSMAN, XP_LONG_SHOT);
			}
		}
		if (Classes.is(player, PlayerClass.RANGER) && Rangers.isFoe(Rangers.foe(player), victim)) {
			giveBlow(player, PlayerClass.RANGER, XP_FOE_HIT);
		}
		if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
			// magic that landed
			giveBlow(player, PlayerClass.MAGE, XP_SPELL_LANDED);
		}
	}

	/** A kill with a bludgeon or an axe: the Brute's. */
	private static void died(LivingEntity dead, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
			ItemStack held = player.getMainHandItem();
			if (held.is(ClassPerks.BLUDGEONS) || held.is(ItemTags.AXES)) {
				give(player, PlayerClass.BRUTE, XP_RAGE_KILL);
			}
		}
	}

	/** Distance in the saddle and time in a shape, once a second; every tick, the class experience earned in it is paid. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			settle(server);
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.entityTags().contains(MADE_TAG)) {
				// gear made at one of Jaymo's Powers' tool stations
				player.removeTag(MADE_TAG);
				give(player, PlayerClass.TINKERER, XP_MADE);
			}
			if (player.getVehicle() instanceof AbstractHorse horse && Classes.is(player, PlayerClass.RIDER)) {
				Vec3 at = horse.position();
				Vec3 from = RIDE_FROM.put(player.getUUID(), at);
				if (from != null) {
					double d = RIDDEN.getOrDefault(player.getUUID(), 0.0) + Math.min(40.0, from.distanceTo(at));
					if (d >= RIDDEN_BLOCKS) {
						d -= RIDDEN_BLOCKS;
						give(player, PlayerClass.RIDER, XP_RIDDEN);
					}
					RIDDEN.put(player.getUUID(), d);
				}
			} else {
				RIDE_FROM.remove(player.getUUID());
			}
			if (server.getTickCount() % SHAPE_EVERY == 0 && Druids.shaped(player)) {
				give(player, PlayerClass.DRUID, XP_SHAPE);
			}
		}
		settle(server);
	}

	// ---- unmaking and the move onto class trees ----------------------------------------------------------------------

	/** A Writ of Rebirth: {@code classes}' levels go, and every rank paid from them (what a trainer taught stays). */
	static void unmake(ServerPlayer player, List<PlayerClass> classes) {
		Skills.Progress p = Skills.of(player);
		Map<String, Integer> xp = new HashMap<>(p.xp());
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		for (PlayerClass cls : classes) {
			xp.remove(XP_PREFIX + cls.key());
			for (Node n : all()) {
				Integer paid = ranks.remove(n.paidKey(cls));
				if (paid != null && paid > 0) {
					int left = ranks.getOrDefault(n.rankKey(), 0) - paid;
					if (left > 0) {
						ranks.put(n.rankKey(), left);
					} else {
						ranks.remove(n.rankKey());
					}
				}
			}
		}
		player.setAttached(Skills.PROGRESS, new Skills.Progress(Map.copyOf(xp), Map.copyOf(ranks)));
	}

	/**
	 * Once, for a character made before class trees: the old pages' ranks are paid from the class that owns them (the class
	 * starts at the level of the pillar they were bought from, so nobody loses a move); ranks in a page no class of theirs
	 * owns now (a Rider's Warfare, a Marksman's Archery) come back as pillar points.
	 */
	static void migrate(ServerPlayer player) {
		Skills.Progress p = Skills.of(player);
		if (p.xp().containsKey(MIGRATED)) {
			return;
		}
		Map<String, Integer> xp = new HashMap<>(p.xp());
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		List<PlayerClass> held = Classes.both(player);
		for (Node n : all()) {
			Skills.Perk perk = n.perk();
			if (perk == null) {
				continue;
			}
			int rank = ranks.getOrDefault(perk.key(), 0);
			if (rank == 0) {
				continue;
			}
			PlayerClass owner = held.stream().filter(n::of).findFirst().orElse(null);
			if (owner == null) {
				ranks.remove(perk.key());
				ranks.remove(Learnables.taughtKey(perk));
				continue;
			}
			int taught = ranks.containsKey(Learnables.taughtKey(perk)) ? 1 : 0;
			if (rank - taught > 0) {
				ranks.merge(n.paidKey(owner), rank - taught, Integer::sum);
			}
			int level = p.level(perk.pillar);
			String k = XP_PREFIX + owner.key();
			xp.put(k, Math.max(xp.getOrDefault(k, 0), total(Math.min(MAX_LEVEL, Math.max(level, n.minLevel())))));
		}
		// what the class could do before it was a tree perk: kept free (no class paid for it)
		for (Kept k : KEPT) {
			if (held.contains(k.cls()) && (k.had() == null || p.ranks().getOrDefault(k.had(), 0) > 0)) {
				Node n = NODES.get(k.key());
				if (n != null && ranks.getOrDefault(n.rankKey(), 0) == 0) {
					ranks.put(n.rankKey(), 1);
					ranks.put(n.rankKey() + KEPT_MARK, 1);
				}
			}
		}
		xp.put(MIGRATED, 1);
		player.setAttached(Skills.PROGRESS, new Skills.Progress(Map.copyOf(xp), Map.copyOf(ranks)));
	}
}
