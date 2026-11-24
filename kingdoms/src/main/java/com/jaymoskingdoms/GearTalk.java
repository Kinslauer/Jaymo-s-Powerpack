package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
<<<<<<< HEAD
 * Gear talk, steps 1 and 2 (docs/Gear-Talk-Plan.md; Andy, 2026-11-24: "players should get tags for the items / baubles they equip,
 * giving the people they meet different things to refer to them about"). What a player wears of the Frostborn's work (a
 * scouter, Rig limbs, the Rig), their stand-in limbs (a smith's wood or iron, or none at all), and a mask, a hood or the
 * symbiote's black suit become tags (a mask marks a vigilante, Lore.md "Costumes and masks"), and the people
=======
 * Gear talk, step 1 (docs/Gear-Talk-Plan.md; Andy, 2026-11-24: "players should get tags for the items / baubles they equip,
 * giving the people they meet different things to refer to them about"). What a player wears of the Frostborn's work (a
 * scouter, Rig limbs, the Rig) and their stand-in limbs (a smith's wood or iron, or none at all) become tags, and the people
>>>>>>> 17c7127e7159086c257b0ef43f1e7a637d9df232
 * they meet react to the most striking one they can see: a bubble the first time ({@link #tick}), and a "Have a word" talk
 * of its own ({@link TalkBook}, ids {@code gear_<line>}).
 * <p>Which line: {@link #LINES} is read top to bottom for the most striking tag, so each tag's rows go from the most
 * particular listener (their blood, temper, trade, what they wear themselves, your blood) to anyone. Plain folk never say
 * "cyborg"; only the Mad Tinkerer, the future's pilots and the Bio-built do (Andy's pick). A masked player's blood is never
 * used (their memory is kept under the mask's name already, {@link Recollections#key}). Armour over a limb hides it, unless
 * the one looking is sharp-eyed ({@link #sharpEyed}).
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
	/** The "Have a word" talk's weight in the draw against the others. */
	static final int TALK_WEIGHT = Talks.WEIGHT_SECRET;

	private static final String SCOUTER_ITEM = "jaymospowers:scouter";
	private static final int ARMS = FrostTech.RIGHT_ARM | FrostTech.LEFT_ARM, LEGS = FrostTech.RIGHT_LEG | FrostTech.LEFT_LEG;
	private static final String BUBBLE = "talk.jaymoskingdoms.gear.", REMARKED = "#gear.";
	private static final List<String> SMITHS = List.of("armorer", "weaponsmith", "toolsmith");

	/** The tags, most striking first. */
	enum Tag {
<<<<<<< HEAD
		MORE_MADE, SUITED, MASKED, RIG, STUMP, MADE_LIMB, SCOUTER, MENDED, HOODED;
=======
		MORE_MADE, RIG, STUMP, MADE_LIMB, SCOUTER, MENDED;
>>>>>>> 17c7127e7159086c257b0ef43f1e7a637d9df232

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Who is looking, at whom, and what they see. {@code mine} is the player's blood as shown (null when masked). */
	record Look(ServerPlayer player, LivingEntity v, Heritage.@Nullable Kind blood, Heritage.@Nullable Kind mine,
		Temperaments.@Nullable Temperament temper, String job, @Nullable String part, java.util.EnumSet<Tag> tags, int wood) {
		boolean temper(Temperaments.Temperament t) {
			return temper == t;
		}
	}

	/** One line: the tag it's about, who says it, and its id (bubble {@code talk.jaymoskingdoms.gear.<id>}, talk {@code word.gear_<id>}). */
	record Line(String id, Tag tag, Predicate<Look> who) {
	}

	private static Line line(String id, Tag tag, Predicate<Look> who) {
		return new Line(id, tag, who);
	}

	private static Line anyone(String id, Tag tag) {
		return new Line(id, tag, l -> true);
	}

	private static boolean cyborgSayer(Look l) {
		return "tinkerer".equals(l.part()) || ZFighterFolk.PILOT_KIND.equals(l.part()) || l.blood() == Heritage.Kind.BIOBUILT;
	}

	private static boolean smith(Look l) {
		return SMITHS.contains(l.job());
	}

	private static boolean frostborn(Look l) {
		return l.blood() == Heritage.Kind.FROSTBORN;
	}

	private static boolean kin(Look l) {
		return frostborn(l) && l.mine() == Heritage.Kind.FROSTBORN;
	}

	/** Every line, each tag's from the most particular listener down to anyone. */
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
<<<<<<< HEAD
		// the symbiote's black suit
		line("suited_devout", Tag.SUITED, l -> l.temper(Temperaments.Temperament.DEVOUT)),
		line("suited_timid", Tag.SUITED, l -> l.temper(Temperaments.Temperament.TIMID)),
		line("suited_brave", Tag.SUITED, l -> l.temper(Temperaments.Temperament.BRAVE) || l.temper(Temperaments.Temperament.HOTHEADED)),
		anyone("suited", Tag.SUITED),
		// a mask: a vigilante, and nobody likes those
		line("masked_guard", Tag.MASKED, l -> "guard".equals(l.job())),
		line("masked_kind", Tag.MASKED, l -> l.temper(Temperaments.Temperament.KINDHEARTED) || l.temper(Temperaments.Temperament.CHEERFUL)),
		line("masked_suspicious", Tag.MASKED, l -> l.temper(Temperaments.Temperament.SUSPICIOUS)),
		line("masked_timid", Tag.MASKED, l -> l.temper(Temperaments.Temperament.TIMID)),
		line("masked_greedy", Tag.MASKED, l -> l.temper(Temperaments.Temperament.GREEDY)),
		line("masked_hothead", Tag.MASKED, l -> l.temper(Temperaments.Temperament.HOTHEADED) || l.temper(Temperaments.Temperament.PROUD)),
		anyone("masked", Tag.MASKED),
=======
>>>>>>> 17c7127e7159086c257b0ef43f1e7a637d9df232
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
		// a scouter
		line("scouter_patrol", Tag.SCOUTER, l -> "patrolman".equals(l.part())),
		line("scouter_kin", Tag.SCOUTER, GearTalk::kin),
		line("scouter_wears", Tag.SCOUTER, l -> FrostTech.wearsWork(l.v())),
		line("scouter_suspicious", Tag.SCOUTER, l -> l.temper(Temperaments.Temperament.SUSPICIOUS)),
		anyone("scouter", Tag.SCOUTER),
		// a smith's wood or iron
		line("mended_too", Tag.MENDED, l -> FrostTech.wooden(l.v())),
		line("mended_smith", Tag.MENDED, l -> smith(l) && l.wood() != 0),
		line("mended_kind", Tag.MENDED, l -> l.temper(Temperaments.Temperament.KINDHEARTED)),
		line("mended_spiteful", Tag.MENDED, l -> l.temper(Temperaments.Temperament.SPITEFUL)),
<<<<<<< HEAD
		anyone("mended", Tag.MENDED),
		// a hood up, and no mask
		line("hooded_guard", Tag.HOODED, l -> "guard".equals(l.job())),
		line("hooded_suspicious", Tag.HOODED, l -> l.temper(Temperaments.Temperament.SUSPICIOUS)),
		anyone("hooded", Tag.HOODED));
=======
		anyone("mended", Tag.MENDED));
>>>>>>> 17c7127e7159086c257b0ef43f1e7a637d9df232

	/** The last look worked out, so a talk's many gates don't each work it out again. */
	private record Cached(UUID player, int v, long time, @Nullable Look look) {
	}

	private static @Nullable Cached last;
	/** When each player was last remarked on (game time). */
	private static final Map<UUID, Long> REMARKED_AT = new HashMap<>();

	private GearTalk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GearTalk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REMARKED_AT.clear();
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
<<<<<<< HEAD
		// (a costume's suit or the symbiote's covers every limb)
		if (PowersCompat.costumeShown(player) || PowersCompat.symbioteSuit(player)) {
			return 0;
		}
=======
>>>>>>> 17c7127e7159086c257b0ef43f1e7a637d9df232
		return (armsHidden ? 0 : ARMS) | (legsHidden ? 0 : LEGS);
	}

	private static String job(Villager v) {
		return Downed.isGuard(v) ? "guard" : BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
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

		java.util.EnumSet<Tag> tags = java.util.EnumSet.noneOf(Tag.class);
		if ((scouter ? 1 : 0) + rigSeen + rig >= MORE_MADE_PIECES) {
			tags.add(Tag.MORE_MADE);
		}
<<<<<<< HEAD
		if (PowersCompat.symbioteSuit(player)) {
			tags.add(Tag.SUITED);
		}
		if (Identities.masked(player)) {
			tags.add(Tag.MASKED);
		} else if (Identities.hooded(player)) {
			tags.add(Tag.HOODED);
		}
=======
>>>>>>> 17c7127e7159086c257b0ef43f1e7a637d9df232
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
		if (tags.isEmpty()) {
			return null;
		}
		Heritage.Kind mine = Prejudice.masked(player) ? null : Heritage.of(player);
		return new Look(player, v, VillagerHeritage.of(v), mine, temper, job(v), v.getAttached(ZFighterFolk.PART), tags, wood & seen);
	}

	/** The line {@code v} has for the most striking of {@code look}'s tags not in {@code skip}; null if none. */
	private static @Nullable Line choose(Look look, java.util.Set<String> skip) {
		for (Tag tag : look.tags()) {
			if (skip.contains(tag.key())) {
				continue;
			}
			for (Line l : LINES) {
				if (l.tag() == tag && l.who().test(look)) {
					return l;
				}
			}
		}
		return null;
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
		Line l = choose(look, java.util.Set.of());
		return l == null ? null : l.id();
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

	/** {@code v} remarks on the most striking thing they haven't yet remarked on; false if there was none. */
	private static boolean remark(ServerLevel level, ServerPlayer player, Villager v, long now) {
		Look look = look(player, v);
		if (look == null) {
			return false;
		}
		Map<String, Long> talks = Recollections.bearingOf(v, player).talks();
		java.util.Set<String> done = new java.util.HashSet<>();
		for (Tag tag : look.tags()) {
			if (talks.containsKey(REMARKED + tag.key())) {
				done.add(tag.key());
			}
		}
		Line l = choose(look, done);
		if (l == null) {
			return false;
		}
		Speech.sayTo(level, v, player, Component.translatable(BUBBLE + l.id()), Speech.WHITE);
		REMARKED_AT.put(player.getUUID(), now);
		long today = Deeds.today(level.getServer());
		Recollections.bear(v, player, b -> {
			Map<String, Long> m = new HashMap<>(b.talks());
			m.put(REMARKED + l.tag().key(), today);
			return new Recollections.Bearing(b.trust(), b.respect(), b.warmth(), b.settled(), Map.copyOf(m), b.quarrel());
		});
		return true;
	}
}
