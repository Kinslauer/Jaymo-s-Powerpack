package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * Gear talk (docs/Gear-Talk-Plan.md; Andy, 2026-11-24: "players should get tags for the items / baubles they equip,
 * giving the people they meet different things to refer to them about"). What a player wears becomes tags: the
 * Frostborn's work (a scouter, Rig limbs, the Rig), stand-in limbs (a smith's wood or iron, or none at all), a mask, a hood
 * or the symbiote's black suit (a mask marks a vigilante, Lore.md "Costumes and masks"), plate (a knight), a realm's arms,
 * nothing at all (ragged), and the themes of what they wear, read from the item tags {@code jaymoskingdoms:gear/<theme>}
 * (holy, jewels, fae, sky-made, beastly, grim, trade, fool, lucky, brawler, delver; any mod or datapack can add to them).
 * The people they meet react to the most striking thing they can see: a bubble the first time ({@link #tick}), and a "Have
 * a word" talk of its own ({@link TalkBook}, ids {@code gear_<line>}).
 * <p>Which line: two-tag lines first ({@link #COMBOS}), then {@link #LINES} top to bottom for the most striking tag, each
 * tag's rows going from the most particular listener (their blood, temper, trade, realm, what they wear themselves, your
 * blood) to anyone. A change is noticed ({@link #CHANGES}: "New arm? Last time it was wood."), and what one villager
 * remarks on, their neighbours hear about ({@link #GOSSIP}), and greet you by your look ("the one with the glass eye"),
 * in their words only, never over your head.
 * <p>Andy's picks: plain folk never say "cyborg", only the Mad Tinkerer, the future's pilots and the Bio-built do; a
 * masked player's blood is never used, and what folk remember and gossip of a masked look is kept under the mask's name
 * ({@link Recollections#key}); armour over a limb hides it, unless the one looking is sharp-eyed ({@link #sharpEyed}).
 */
final class GearTalk {
	/** How near (blocks) a villager must be to remark on what you wear, and how often (ticks) each player is looked at. */
	static final double REMARK_RANGE = 6.0;
	static final int LOOK_EVERY = 40;
	/** The chance a look turns into a remark, and the least time (ticks) between two remarks to one player. */
	static final float REMARK_CHANCE = 0.35F;
	static final int REMARK_GAP = 20 * 45;
	/** The share of folk sharp-eyed enough to see a limb under armour; the suspicious are this many times as likely. */
	static final float SHARP_EYED = 0.2F, SUSPICIOUS_SHARP = 2.5F;
	/** Pieces of Frostborn work (the scouter, each Rig limb, the Rig: some of it 1, the whole set 2) that make you "more made than flesh". */
	static final int MORE_MADE_PIECES = 3;
	/** Plate pieces (the harness's and the armour slots') that make a knight. */
	static final int KNIGHT_PLATES = 5;
	/** How far (blocks) word of what one villager remarked on spreads, and the most villagers who hear it. */
	static final double GOSSIP_RANGE = 32.0;
	static final int GOSSIP_HEARERS = 6;
	/** Gossip bubble lines ({@code talk.jaymoskingdoms.gear.gossip.<n>}), and how many rumours are kept at most. */
	static final int GOSSIP_LINES = 3, GOSSIP_KEPT = 4096;
	/** The "Have a word" talk's weight in the draw against the others. */
	static final int TALK_WEIGHT = Talks.WEIGHT_SECRET;

	private static final String SCOUTER_ITEM = "jaymospowers:scouter";
	private static final int ARMS = FrostTech.RIGHT_ARM | FrostTech.LEFT_ARM, LEGS = FrostTech.RIGHT_LEG | FrostTech.LEFT_LEG;
	private static final String BUBBLE = "talk.jaymoskingdoms.gear.", REMARKED = "#gear.";
	private static final List<String> SMITHS = List.of("armorer", "weaponsmith", "toolsmith");
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** The tags, most striking first. The themes ({@link #theme}) are read from item tags of the same name. */
	enum Tag {
		MORE_MADE, SUITED, MASKED, RIG, STUMP, MADE_LIMB, GRIM, SCOUTER, KNIGHT, REALM_ARMS, MENDED, HOODED,
		SKY_MADE(true), FAE(true), HOLY(true), JEWELS(true), BEASTLY(true), FOOL(true), BRAWLER(true), TRADE(true), DELVER(true),
		LUCKY(true), RAGGED;

		final @Nullable TagKey<Item> items;

		Tag() {
			this(false);
		}

		Tag(boolean theme) {
			this.items = theme ? TagKey.create(Registries.ITEM, JaymosKingdoms.id("gear/" + key(name()))) : null;
		}

		private static String key(String name) {
			return name.toLowerCase(java.util.Locale.ROOT);
		}

		String key() {
			return key(name());
		}
	}

	/** Whose arms you wear, to the one looking: none, their own realm's, an enemy's (at war with theirs), another realm's. */
	enum Arms {
		NONE, THEIRS, ENEMY, OTHER
	}

	/** Who is looking, at whom, and what they see. {@code mine} is the player's blood as shown (null when masked). */
	record Look(ServerPlayer player, LivingEntity v, Heritage.@Nullable Kind blood, Heritage.@Nullable Kind mine,
		Temperaments.@Nullable Temperament temper, String job, @Nullable String part, EnumSet<Tag> tags, int wood, boolean gilded,
		Arms arms, String armsName) {
		boolean temper(Temperaments.Temperament t) {
			return temper == t;
		}

		boolean temper(Temperaments.Temperament a, Temperaments.Temperament b) {
			return temper == a || temper == b;
		}

		boolean has(Tag t) {
			return tags.contains(t);
		}
	}

	/**
	 * One line: the tag it's about (and a second for a two-tag line), who says it, and its id (bubble
	 * {@code talk.jaymoskingdoms.gear.<id>}, talk {@code word.gear_<id>}). Its lines may use {@code %2$s}, the realm whose
	 * arms you wear, and {@code %3$s}, what they call you by your look.
	 */
	record Line(String id, Tag tag, @Nullable Tag and, Predicate<Look> who) {
		/** What a villager remembers having remarked on, under {@code #gear.}. */
		String key() {
			return and == null ? tag.key() : "combo." + id;
		}

		boolean fits(Look l) {
			return l.has(tag) && (and == null || l.has(and)) && who.test(l);
		}
	}

	private static Line line(String id, Tag tag, Predicate<Look> who) {
		return new Line(id, tag, null, who);
	}

	private static Line anyone(String id, Tag tag) {
		return new Line(id, tag, null, l -> true);
	}

	private static Line both(String id, Tag tag, Tag and) {
		return new Line(id, tag, and, l -> true);
	}

	private static boolean cyborgSayer(Look l) {
		return "tinkerer".equals(l.part()) || ZFighterFolk.PILOT_KIND.equals(l.part()) || l.blood() == Heritage.Kind.BIOBUILT;
	}

	private static boolean smith(Look l) {
		return SMITHS.contains(l.job());
	}

	private static boolean guard(Look l) {
		return "guard".equals(l.job());
	}

	private static boolean priest(Look l) {
		return "cleric".equals(l.job());
	}

	private static boolean frostborn(Look l) {
		return l.blood() == Heritage.Kind.FROSTBORN;
	}

	private static boolean kin(Look l) {
		return frostborn(l) && l.mine() == Heritage.Kind.FROSTBORN;
	}

	/** The two-tag lines, read before any one-tag line. */
	static final List<Line> COMBOS = List.of(
		both("combo_masked_limb", Tag.MASKED, Tag.MADE_LIMB),
		both("combo_holy_grim", Tag.HOLY, Tag.GRIM),
		both("combo_jewels_ragged", Tag.JEWELS, Tag.RAGGED),
		both("combo_knight_mended", Tag.KNIGHT, Tag.MENDED),
		both("combo_fool_arms", Tag.FOOL, Tag.REALM_ARMS),
		both("combo_scouter_fae", Tag.SCOUTER, Tag.FAE));

	/** Every one-tag line, each tag's from the most particular listener down to anyone. */
	static final List<Line> LINES = List.of(
		// more Frostborn work than flesh
		line("more_made_cyborg", Tag.MORE_MADE, GearTalk::cyborgSayer),
		line("more_made_kin", Tag.MORE_MADE, GearTalk::kin),
		line("more_made_frost", Tag.MORE_MADE, GearTalk::frostborn),
		line("more_made_warrior", Tag.MORE_MADE, l -> l.blood() == Heritage.Kind.WARRIORBORN),
		line("more_made_timid", Tag.MORE_MADE, l -> l.temper(Temperaments.Temperament.TIMID)),
		line("more_made_greedy", Tag.MORE_MADE, l -> l.temper(Temperaments.Temperament.GREEDY)),
		line("more_made_devout", Tag.MORE_MADE, l -> l.temper(Temperaments.Temperament.DEVOUT)),
		anyone("more_made", Tag.MORE_MADE),
		// the symbiote's black suit
		line("suited_devout", Tag.SUITED, l -> l.temper(Temperaments.Temperament.DEVOUT)),
		line("suited_timid", Tag.SUITED, l -> l.temper(Temperaments.Temperament.TIMID)),
		line("suited_brave", Tag.SUITED, l -> l.temper(Temperaments.Temperament.BRAVE, Temperaments.Temperament.HOTHEADED)),
		anyone("suited", Tag.SUITED),
		// a mask: a vigilante, and nobody likes those
		line("masked_guard", Tag.MASKED, GearTalk::guard),
		line("masked_kind", Tag.MASKED, l -> l.temper(Temperaments.Temperament.KINDHEARTED, Temperaments.Temperament.CHEERFUL)),
		line("masked_suspicious", Tag.MASKED, l -> l.temper(Temperaments.Temperament.SUSPICIOUS)),
		line("masked_timid", Tag.MASKED, l -> l.temper(Temperaments.Temperament.TIMID)),
		line("masked_greedy", Tag.MASKED, l -> l.temper(Temperaments.Temperament.GREEDY)),
		line("masked_hothead", Tag.MASKED, l -> l.temper(Temperaments.Temperament.HOTHEADED, Temperaments.Temperament.PROUD)),
		anyone("masked", Tag.MASKED),
		// the Rig
		line("rig_frost", Tag.RIG, GearTalk::frostborn),
		line("rig_timid", Tag.RIG, l -> l.temper(Temperaments.Temperament.TIMID)),
		anyone("rig", Tag.RIG),
		// a lost limb with nothing in its place
		line("stump_smith", Tag.STUMP, GearTalk::smith),
		line("stump_kind", Tag.STUMP, l -> l.temper(Temperaments.Temperament.KINDHEARTED)),
		anyone("stump", Tag.STUMP),
		// a Rig arm or leg
		line("made_limb_wooden", Tag.MADE_LIMB, l -> FrostTech.wooden(l.v())),
		line("made_limb_smith", Tag.MADE_LIMB, GearTalk::smith),
		line("made_limb_kin", Tag.MADE_LIMB, GearTalk::kin),
		line("made_limb_frost", Tag.MADE_LIMB, GearTalk::frostborn),
		line("made_limb_greedy", Tag.MADE_LIMB, l -> l.temper(Temperaments.Temperament.GREEDY)),
		anyone("made_limb", Tag.MADE_LIMB),
		// a dead thing's trinkets
		line("grim_priest", Tag.GRIM, GearTalk::priest),
		line("grim_devout", Tag.GRIM, l -> l.temper(Temperaments.Temperament.DEVOUT)),
		line("grim_timid", Tag.GRIM, l -> l.temper(Temperaments.Temperament.TIMID)),
		anyone("grim", Tag.GRIM),
		// a scouter
		line("scouter_patrol", Tag.SCOUTER, l -> "patrolman".equals(l.part())),
		line("scouter_kin", Tag.SCOUTER, GearTalk::kin),
		line("scouter_wears", Tag.SCOUTER, l -> FrostTech.wearsWork(l.v())),
		line("scouter_suspicious", Tag.SCOUTER, l -> l.temper(Temperaments.Temperament.SUSPICIOUS)),
		anyone("scouter", Tag.SCOUTER),
		// plate
		line("knight_guard", Tag.KNIGHT, GearTalk::guard),
		line("knight_gilded", Tag.KNIGHT, Look::gilded),
		line("knight_proud", Tag.KNIGHT, l -> l.temper(Temperaments.Temperament.PROUD)),
		line("knight_spiteful", Tag.KNIGHT, l -> l.temper(Temperaments.Temperament.SPITEFUL, Temperaments.Temperament.HOTHEADED)),
		anyone("knight", Tag.KNIGHT),
		// a realm's arms
		line("arms_theirs_guard", Tag.REALM_ARMS, l -> l.arms() == Arms.THEIRS && guard(l)),
		line("arms_theirs", Tag.REALM_ARMS, l -> l.arms() == Arms.THEIRS),
		line("arms_enemy_guard", Tag.REALM_ARMS, l -> l.arms() == Arms.ENEMY && guard(l)),
		line("arms_enemy", Tag.REALM_ARMS, l -> l.arms() == Arms.ENEMY),
		anyone("arms_other", Tag.REALM_ARMS),
		// a smith's wood or iron
		line("mended_too", Tag.MENDED, l -> FrostTech.wooden(l.v())),
		line("mended_smith", Tag.MENDED, l -> smith(l) && l.wood() != 0),
		line("mended_kind", Tag.MENDED, l -> l.temper(Temperaments.Temperament.KINDHEARTED)),
		line("mended_spiteful", Tag.MENDED, l -> l.temper(Temperaments.Temperament.SPITEFUL)),
		anyone("mended", Tag.MENDED),
		// a hood up, and no mask
		line("hooded_guard", Tag.HOODED, GearTalk::guard),
		line("hooded_suspicious", Tag.HOODED, l -> l.temper(Temperaments.Temperament.SUSPICIOUS)),
		anyone("hooded", Tag.HOODED),
		// the themes
		line("sky_devout", Tag.SKY_MADE, l -> l.temper(Temperaments.Temperament.DEVOUT)),
		line("sky_greedy", Tag.SKY_MADE, l -> l.temper(Temperaments.Temperament.GREEDY)),
		anyone("sky", Tag.SKY_MADE),
		line("fae_kind", Tag.FAE, l -> l.temper(Temperaments.Temperament.KINDHEARTED, Temperaments.Temperament.CHEERFUL)),
		anyone("fae", Tag.FAE),
		line("holy_priest", Tag.HOLY, GearTalk::priest),
		line("holy_devout", Tag.HOLY, l -> l.temper(Temperaments.Temperament.DEVOUT)),
		line("holy_spiteful", Tag.HOLY, l -> l.temper(Temperaments.Temperament.SPITEFUL, Temperaments.Temperament.GLOOMY)),
		anyone("holy", Tag.HOLY),
		line("jewels_greedy", Tag.JEWELS, l -> l.temper(Temperaments.Temperament.GREEDY)),
		line("jewels_timid", Tag.JEWELS, l -> l.temper(Temperaments.Temperament.TIMID, Temperaments.Temperament.KINDHEARTED)),
		anyone("jewels", Tag.JEWELS),
		line("beastly_timid", Tag.BEASTLY, l -> l.temper(Temperaments.Temperament.TIMID)),
		anyone("beastly", Tag.BEASTLY),
		line("fool_cheerful", Tag.FOOL, l -> l.temper(Temperaments.Temperament.CHEERFUL)),
		line("fool_proud", Tag.FOOL, l -> l.temper(Temperaments.Temperament.PROUD, Temperaments.Temperament.GLOOMY)),
		anyone("fool", Tag.FOOL),
		line("brawler_guard", Tag.BRAWLER, GearTalk::guard),
		line("brawler_hothead", Tag.BRAWLER, l -> l.temper(Temperaments.Temperament.HOTHEADED, Temperaments.Temperament.BRAVE)),
		anyone("brawler", Tag.BRAWLER),
		line("trade_smith", Tag.TRADE, GearTalk::smith),
		anyone("trade", Tag.TRADE),
		line("delver_stoneborn", Tag.DELVER, l -> l.blood() == Heritage.Kind.STONEBORN || l.blood() == Heritage.Kind.TIDEBORN),
		anyone("delver", Tag.DELVER),
		line("lucky_suspicious", Tag.LUCKY, l -> l.temper(Temperaments.Temperament.SUSPICIOUS, Temperaments.Temperament.GLOOMY)),
		anyone("lucky", Tag.LUCKY),
		line("ragged_kind", Tag.RAGGED, l -> l.temper(Temperaments.Temperament.KINDHEARTED)),
		line("ragged_greedy", Tag.RAGGED, l -> l.temper(Temperaments.Temperament.GREEDY, Temperaments.Temperament.PROUD)),
		anyone("ragged", Tag.RAGGED));

	/** What changed since a villager last remarked: they'd remarked on {@code was}, it's gone, and now they see {@code now}. Bubbles only. */
	record Change(String id, Tag was, Tag now) {
	}

	static final List<Change> CHANGES = List.of(
		new Change("changed_fitted", Tag.STUMP, Tag.MENDED),
		new Change("changed_fitted", Tag.STUMP, Tag.MADE_LIMB),
		new Change("changed_better", Tag.MENDED, Tag.MADE_LIMB),
		new Change("changed_lost", Tag.MENDED, Tag.STUMP),
		new Change("changed_lost", Tag.MADE_LIMB, Tag.STUMP),
		new Change("changed_fallen", Tag.KNIGHT, Tag.RAGGED),
		new Change("changed_risen", Tag.RAGGED, Tag.KNIGHT));

	/** The last look worked out, so a talk's many gates don't each work it out again. */
	private record Cached(UUID player, int v, long time, @Nullable Look look) {
	}

	private static @Nullable Cached last;
	/** When each player was last remarked on (game time). */
	private static final Map<UUID, Long> REMARKED_AT = new HashMap<>();
	/** Word that's got about: villager -> the memory key of a player ({@link Recollections#key}) -> the tags heard of. */
	private static final Map<UUID, Map<UUID, Set<Tag>>> GOSSIP = new HashMap<>();

	private GearTalk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GearTalk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REMARKED_AT.clear();
			GOSSIP.clear();
			last = null;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> REMARKED_AT.remove(handler.player.getUUID()));
	}

	// ---- what they see ----------------------------------------------------------------------------------------------------

	/** A number from 0 to 1, always the same for this looker and this player. */
	private static float roll(UUID a, UUID b) {
		long x = a.getMostSignificantBits() ^ Long.rotateLeft(b.getLeastSignificantBits(), 17) ^ 0x6A09E667F3BCC909L;
		x = (x ^ x >>> 30) * 0xBF58476D1CE4E5B9L;
		x = (x ^ x >>> 27) * 0x94D049BB133111EBL;
		x ^= x >>> 31;
		return (x >>> 40) / (float)(1 << 24);
	}

	/** Whether {@code v} sees what armour hides on {@code player}. */
	static boolean sharpEyed(LivingEntity v, ServerPlayer player, Temperaments.@Nullable Temperament temper) {
		float share = temper == Temperaments.Temperament.SUSPICIOUS ? SHARP_EYED * SUSPICIOUS_SHARP : SHARP_EYED;
		return roll(v.getUUID(), player.getUUID()) < share;
	}

	/** The limbs {@code v} can see on {@code player}: armour or harness over an arm or a leg hides it. */
	private static int visible(ServerPlayer player, boolean sharp) {
		if (sharp) {
			return ARMS | LEGS;
		}
		boolean armsHidden = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || !Harness.get(player, Harness.Slot.ARMS).isEmpty()
			|| !Harness.get(player, Harness.Slot.HANDS).isEmpty();
		boolean legsHidden = !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || !Harness.get(player, Harness.Slot.THIGHS).isEmpty()
			|| !Harness.get(player, Harness.Slot.SHINS).isEmpty();
		// (a costume's suit or the symbiote's covers every limb)
		if (PowersCompat.costumeShown(player) || PowersCompat.symbioteSuit(player)) {
			return 0;
		}
		return (armsHidden ? 0 : ARMS) | (legsHidden ? 0 : LEGS);
	}

	private static String job(Villager v) {
		return Downed.isGuard(v) ? "guard" : BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
	}

	/** Everything {@code player} wears that can be seen: the armour slots, the harness and the baubles. */
	private static List<ItemStack> worn(ServerPlayer player) {
		List<ItemStack> all = new ArrayList<>();
		for (EquipmentSlot slot : ARMOUR) {
			all.add(player.getItemBySlot(slot));
		}
		all.addAll(Harness.worn(player));
		all.addAll(PowersCompat.baubles(player));
		all.removeIf(ItemStack::isEmpty);
		return all;
	}

	/** Whether {@code arms} (from a tabard) are {@code f}'s. */
	private static boolean bears(HeraldryArmour.Arms arms, @Nullable DyedItemColor dyed, Faction f, MinecraftServer server) {
		Outfits.Heraldry h = Outfits.heraldry(f, server);
		return arms.ordinary() == h.ordinary() && arms.charge() == Outfits.rgb(h.charge())
			&& (dyed == null || (dyed.rgb() & 0xFFFFFF) == Outfits.rgb(h.field()));
	}

	/** Whose arms {@code player} wears (a tabard in the harness, or a realm's colours), to {@code v}; and that realm's name. */
	private static Object[] arms(ServerPlayer player, Villager v, MinecraftServer server) {
		ItemStack tabard = Harness.get(player, Harness.Slot.OVER);
		HeraldryArmour.Arms arms = tabard.get(HeraldryArmour.ARMS);
		DyedItemColor dyed = tabard.get(DataComponents.DYED_COLOR);
		// (no tabard's arms and no dyed surcoat: no colours to read, and no need to look through every realm)
		if (arms == null && player.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR) == null) {
			return new Object[] {Arms.NONE, ""};
		}
		Faction theirs = Bearings.realm(v);
		List<String> enemies = theirs == null ? List.of() : Approaches.war(server, theirs);
		Faction found = null;
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && (arms != null && bears(arms, dyed, f, server) || Colours.wears(player, f))) {
				found = f;
				// (their own realm's, or an enemy's, before any other that happens to share its arms)
				if (theirs != null && f.id() == theirs.id() || enemies.contains(f.name())) {
					break;
				}
			}
		}
		if (found == null) {
			return new Object[] {Arms.NONE, ""};
		}
		Arms whose = theirs != null && found.id() == theirs.id() ? Arms.THEIRS : enemies.contains(found.name()) ? Arms.ENEMY : Arms.OTHER;
		return new Object[] {whose, found.name()};
	}

	/** What {@code v} sees of {@code player}'s gear; null if there's nothing to remark on. */
	static @Nullable Look look(ServerPlayer player, Villager v) {
		long now = player.level().getGameTime();
		Cached c = last;
		if (c != null && c.player().equals(player.getUUID()) && c.v() == v.getId() && c.time() == now) {
			return c.look();
		}
		Look look = work(player, v);
		last = new Cached(player.getUUID(), v.getId(), now, look);
		return look;
	}

	private static @Nullable Look work(ServerPlayer player, Villager v) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return null;
		}
		Temperaments.Temperament temper = Temperaments.of(v);
		int seen = visible(player, sharpEyed(v, player, temper));
		int fitted = PowersCompat.fittedLimbs(player) & (ARMS | LEGS);
		int rigLimbs = PowersCompat.rigLimbs(player) & fitted;
		int iron = PowersCompat.ironLimbs(player) & fitted & ~rigLimbs;
		int wood = fitted & ~rigLimbs & ~iron;
		int stumps = PowersCompat.missingLimbs(player) & (ARMS | LEGS) & ~fitted;
		boolean scouter = SCOUTER_ITEM.equals(BuiltInRegistries.ITEM.getKey(player.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString());
		int rig = PowersCompat.rigWorn(player);
		int rigSeen = Integer.bitCount(rigLimbs & seen);
		List<ItemStack> worn = worn(player);

		EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
		if ((scouter ? 1 : 0) + rigSeen + rig >= MORE_MADE_PIECES) {
			tags.add(Tag.MORE_MADE);
		}
		if (PowersCompat.symbioteSuit(player)) {
			tags.add(Tag.SUITED);
		}
		if (Identities.masked(player)) {
			tags.add(Tag.MASKED);
		} else if (Identities.hooded(player)) {
			tags.add(Tag.HOODED);
		}
		if (rig > 0) {
			tags.add(Tag.RIG);
		}
		// (a lost limb shows through anything: the sleeve hangs empty)
		if (stumps != 0) {
			tags.add(Tag.STUMP);
		}
		if (rigSeen > 0) {
			tags.add(Tag.MADE_LIMB);
		}
		if (scouter) {
			tags.add(Tag.SCOUTER);
		}
		if (((wood | iron) & seen) != 0) {
			tags.add(Tag.MENDED);
		}
		int plates = 0;
		boolean gilded = false;
		boolean armoured = false;
		for (ItemStack stack : worn) {
			HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
			if (piece != null && piece.plate()) {
				plates++;
				gilded |= HeraldryArmour.gilded(stack);
			}
			for (Tag t : Tag.values()) {
				if (t.items != null && stack.is(t.items)) {
					tags.add(t);
				}
			}
		}
		for (EquipmentSlot slot : ARMOUR) {
			armoured |= !player.getItemBySlot(slot).isEmpty();
		}
		for (ItemStack stack : Harness.worn(player)) {
			armoured |= !stack.isEmpty();
		}
		if (plates >= KNIGHT_PLATES) {
			tags.add(Tag.KNIGHT);
		}
		if (!armoured && rig == 0 && !tags.contains(Tag.SUITED) && !PowersCompat.costumeShown(player)) {
			tags.add(Tag.RAGGED);
		}
		Object[] arms = arms(player, v, server);
		if (arms[0] != Arms.NONE) {
			tags.add(Tag.REALM_ARMS);
		}
		if (tags.isEmpty()) {
			return null;
		}
		Heritage.Kind mine = Prejudice.masked(player) ? null : Heritage.of(player);
		return new Look(player, v, VillagerHeritage.of(v), mine, temper, job(v), v.getAttached(ZFighterFolk.PART), tags, wood & seen, gilded,
			(Arms)arms[0], (String)arms[1]);
	}

	/** The line {@code v} has for what they see, skipping what they've remarked on ({@link Line#key} in {@code done}); null if none. */
	private static @Nullable Line choose(Look look, Set<String> done) {
		for (Line l : COMBOS) {
			if (!done.contains(l.key()) && l.fits(look)) {
				return l;
			}
		}
		for (Tag tag : look.tags()) {
			if (done.contains(tag.key())) {
				continue;
			}
			for (Line l : LINES) {
				if (l.tag() == tag && l.fits(look)) {
					return l;
				}
			}
		}
		return null;
	}

	/** What folk call {@code look}'s player by their look: their most striking tag ({@code talk.jaymoskingdoms.gear.look.<tag>}). */
	private static Component lookName(Look look) {
		return Component.translatable(BUBBLE + "look." + look.tags().iterator().next().key());
	}

	/** The arguments a gear line takes after the name: the realm whose arms you wear, and what they call you by your look. */
	static Object[] args(ServerPlayer player, Villager v) {
		Look look = look(player, v);
		return look == null ? new Object[] {"", ""} : new Object[] {look.armsName(), lookName(look)};
	}

	/** (TalkBook) The gear line {@code v} would talk to {@code player} about, or null. */
	static @Nullable String talkLine(ServerPlayer player, Villager v) {
		if (v.isBaby()) {
			return null;
		}
		Look look = look(player, v);
		if (look == null) {
			return null;
		}
		Line l = choose(look, Set.of());
		return l == null ? null : l.id();
	}

	/** (TalkBook) Every line there's a talk for: the two-tag ones and the rest. */
	static List<Line> allLines() {
		List<Line> all = new ArrayList<>(COMBOS);
		all.addAll(LINES);
		return all;
	}

	// ---- remarking on sight -------------------------------------------------------------------------------------------------

	private static boolean mayRemark(ServerPlayer player, Villager v) {
		return v.isAlive() && !v.isBaby() && !v.isSleeping() && McaCompat.isMca(v) && !Approaches.talking(v)
			&& !Bearings.cold(v, player) && v.hasLineOfSight(player);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((player.tickCount + player.getId()) % LOOK_EVERY != 0 || !player.isAlive() || player.isSpectator()
				|| !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			Long at = REMARKED_AT.get(player.getUUID());
			if (at != null && now - at < REMARK_GAP || player.getRandom().nextFloat() >= REMARK_CHANCE) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(REMARK_RANGE))) {
				if (mayRemark(player, v) && remark(level, player, v, now)) {
					break;
				}
			}
		}
	}

	/** {@code v} remarks on what's changed, or the most striking thing they haven't yet remarked on; false if there was nothing. */
	private static boolean remark(ServerLevel level, ServerPlayer player, Villager v, long now) {
		Look look = look(player, v);
		if (look == null) {
			return false;
		}
		Map<String, Long> talks = Recollections.bearingOf(v, player).talks();
		Set<String> done = new HashSet<>();
		for (String k : talks.keySet()) {
			if (k.startsWith(REMARKED)) {
				done.add(k.substring(REMARKED.length()));
			}
		}
		String said;
		Set<String> forget = new HashSet<>(), mark = new HashSet<>();
		Tag about = null;
		boolean heard = false;
		Change change = null;
		for (Change c : CHANGES) {
			if (done.contains(c.was().key()) && !look.has(c.was()) && look.has(c.now()) && !done.contains(c.now().key())) {
				change = c;
				break;
			}
		}
		if (change != null) {
			said = change.id();
			forget.add(change.was().key());
			mark.add(change.now().key());
		} else {
			Line l = choose(look, done);
			if (l == null) {
				return false;
			}
			mark.add(l.key());
			if (l.and() != null) {
				mark.add(l.tag().key());
				mark.add(l.and().key());
			}
			about = l.tag();
			UUID key = Recollections.key(player, v);
			Set<Tag> rumours = GOSSIP.getOrDefault(v.getUUID(), Map.of()).get(key);
			heard = l.and() == null && rumours != null && rumours.contains(about);
			said = heard ? "gossip." + (1 + v.getRandom().nextInt(GOSSIP_LINES)) : l.id();
		}
		Object[] args = {Recollections.nameTo(player, v), look.armsName(), lookName(look)};
		Speech.sayTo(level, v, player, Component.translatable(BUBBLE + said, args), Speech.WHITE);
		REMARKED_AT.put(player.getUUID(), now);
		long today = Deeds.today(level.getServer());
		Recollections.bear(v, player, b -> {
			Map<String, Long> m = new HashMap<>(b.talks());
			for (String k : forget) {
				m.remove(REMARKED + k);
			}
			for (String k : mark) {
				m.put(REMARKED + k, today);
			}
			return new Recollections.Bearing(b.trust(), b.respect(), b.warmth(), b.settled(), Map.copyOf(m), b.quarrel());
		});
		// (word gets about: what they saw for themselves, not what they'd heard)
		if (about != null && !heard) {
			spread(level, player, v, about);
		}
		return true;
	}

	/** {@code v}'s neighbours hear that {@code player} (under whatever name they're known by there) wears {@code tag}. */
	private static void spread(ServerLevel level, ServerPlayer player, Villager v, Tag tag) {
		int told = 0;
		for (Villager o : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(GOSSIP_RANGE))) {
			if (o == v || o.isBaby() || !McaCompat.isMca(o)) {
				continue;
			}
			if (GOSSIP.size() >= GOSSIP_KEPT && !GOSSIP.containsKey(o.getUUID())) {
				break;
			}
			GOSSIP.computeIfAbsent(o.getUUID(), id -> new HashMap<>()).computeIfAbsent(Recollections.key(player, o), id -> EnumSet.noneOf(Tag.class)).add(tag);
			if (++told >= GOSSIP_HEARERS) {
				break;
			}
		}
	}
}
