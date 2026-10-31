package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.431) Havens patch 3, <b>captains</b> (ideas from Shadow of War, a commercial game, and King's Monsters, ARR: our own
 * names, code and lines; never called by that game's name). Under every goblin and orc king stands a ladder of named
 * captains ({@link #LADDER} a tribe, ranks 1 to {@link #RANK_MAX}): each with a byname, gear and size by rank, posted to one
 * of the tribe's camps (or leading a hunting band, {@link Tribes}), and 2 to 4 {@link Trait traits}: strengths, weaknesses
 * and fears, tied to our systems (wounds and bleeding, crippling blows, unaware blows, fire, beasts).
 * <ul>
 * <li><b>Learning their traits:</b> one you question ("What does your captain fear?", {@link Questioning}), a chief's orders
 * in a camp's store (a written page; read it and you know), or the pedlar's rumours ({@link #PEDLAR_ANSWER}). What you know
 * is in your journal.</li>
 * <li><b>They remember every meeting:</b> the first time each one sees you, a short letterbox film and his name; after
 * that, a line about how it went last time: you ran, you beat him and left him, you spared him, he killed you.</li>
 * <li><b>Kill a player, climb the ladder:</b> a captain who kills a player goes up a rank, gains a strength and wears their
 * head; one of the tribe's common folk who does it becomes a captain. Either way he's that player's <b>sworn foe</b>,
 * written in their journal until he falls.</li>
 * <li><b>When a king dies</b> (his camp falls, so another is the seat), the strongest captain takes the new seat: the
 * chief there is him from then on.</li>
 * </ul>
 * Captains who fall stay fallen for now (patch 4 brings some back); new ones rise, one a day, to fill the ladder.
 */
public final class Captains {
	// ================================================================================================================
	// Tuning
	/** Captains a tribe keeps under its king, the most it may have (kills make more), and their highest rank. */
	static final int LADDER = 4, LADDER_MAX = 7, RANK_MAX = 5;
	/** A new captain rises to fill the ladder at most this often (ticks). */
	static final long RISE_GAP = 24000L;
	/** How often captains are seen to (ticks); how near a player must be to a camp for its captain to be about. */
	static final int EVERY = 100;
	static final double POST_NEAR = 80.0, MUSTER_HIDE = 20.0;
	/** How far a posted captain keeps to his camp. */
	static final int HOME_RADIUS = 20;
	/** His build: size, health (times his kind's) and blows, each with more for every rank. */
	static final double SCALE_BASE = 1.1, SCALE_PER_RANK = 0.03, HEALTH_BASE = 1.6, HEALTH_PER_RANK = 0.3, DAMAGE_PER_RANK = 1.5;
	/** Meetings: how near he sees you, how long until it counts as another meeting (ticks), the first meeting's film. */
	static final double MEET_RANGE = 14.0, SAY_RANGE = 24.0;
	static final long MEET_GAP = 6000L;
	static final int FILM_TICKS = 50;
	static final double FILM_WITNESSES = 20.0;
	/** A fight with him is over this long after the last blow (ticks); further off than this, you ran. */
	static final long FIGHT_COOLS = 200L, FIGHT_FORGOTTEN = 6000L;
	static final double FLED_RANGE = 40.0;
	/** Strengths: a thick hide's share of a melee blow, an arrow-proof hide's of a shot; heavy hands' chance to cripple. */
	static final float THICK_HIDE = 0.7F, ARROW_PROOF = 0.25F, HEAVY_HANDED = 0.4F;
	/** Hard to kill: below this share of his health, once, he's back up to this share. Blood rage below this share. */
	static final float HARD_AT = 0.25F, HARD_TO = 0.5F, RAGE_AT = 0.5F;
	/** Venom on his blows (ticks); his rally's reach and how long it lasts (ticks). */
	static final int VENOM_TICKS = 100, RALLY_TICKS = 600;
	static final double RALLY_RANGE = 24.0;
	/** Weaknesses: shots, blunt blows and fists (glass jaw), a blow he didn't see coming (blind side), fire: times as hard. */
	static final float ARROW_SOFT = 2.0F, GLASS_JAW = 1.75F, BLIND_SIDE = 2.5F, BURNS = 2.5F;
	/** Fears: how near the thing he fears, how long he runs (ticks), and how long before it can send him running again. */
	static final double FEAR_RANGE = 7.0, FLEE_DISTANCE = 12.0;
	static final int FEAR_TICKS = 100, FEAR_AGAIN = 400;
	/** Bleeding frightens one who fears blood only below this share of his health. */
	static final float BLOOD_AT = 0.75F;
	/** The pedlar's rumour of a captain (emeralds). */
	static final int RUMOUR_PRICE = 2;
	/** (Andy) The scene before you respawn: how long it runs, and when in it the killer's raised (ticks). */
	static final int SCENE_TICKS = 90, SCENE_RAISED = 45;
	static final double CHEER_RANGE = 20.0;
	/** Lines for each (lang {@code talk.jaymoskingdoms.captain.<what>.<n>}). */
	static final int LINES = 3;
	// ================================================================================================================

	/** MCA's talk answer on a tribe's pedlar: what he's heard of its captains. */
	public static final String PEDLAR_ANSWER = "jaymoskingdoms_pedlar_captains";
	/** Tags: a captain's body; his id (after the {@code =}); each of his traits (after the last dot). */
	static final String TAG = "jaymoskingdoms.captain", ID_TAG = TAG + "=", TRAIT_TAG = TAG + ".trait.";
	/** A chief's orders: the captain and the trait they give away (custom data). */
	static final String ORDERS_CAPTAIN = "jaymoskingdoms_orders_captain", ORDERS_TRAIT = "jaymoskingdoms_orders_trait";

	enum Sort {
		STRENGTH, WEAKNESS, FEAR
	}

	/** What a captain is good at, bad at, or afraid of. */
	enum Trait {
		/** Melee blows land at {@link #THICK_HIDE}. */
		THICK_HIDE(Sort.STRENGTH),
		/** Shots land at {@link #ARROW_PROOF}. */
		ARROW_PROOF(Sort.STRENGTH),
		/** Fire never hurts him. */
		FIRE_BORN(Sort.STRENGTH),
		/** His heavy blows cripple ({@link CripplingBlows}) at {@link #HEAVY_HANDED}. */
		HEAVY_HANDED(Sort.STRENGTH),
		/** Once, near death, he's back on his feet. */
		HARD_TO_KILL(Sort.STRENGTH),
		/** Badly hurt, he fights harder and faster. */
		BLOOD_RAGE(Sort.STRENGTH),
		/** His blows poison. */
		VENOMOUS(Sort.STRENGTH),
		/** In a fight he calls his own near to his foe, and they fight harder. */
		RALLYING(Sort.STRENGTH),
		/** Shots land at {@link #ARROW_SOFT} times. */
		SOFT_TO_ARROWS(Sort.WEAKNESS),
		/** Blunt blows and fists land at {@link #GLASS_JAW} times, and stagger him. */
		GLASS_JAW(Sort.WEAKNESS),
		/** A blow from anyone he isn't fighting lands at {@link #BLIND_SIDE} times. */
		BLIND_SIDE(Sort.WEAKNESS),
		/** Fire at {@link #BURNS} times. */
		BURNS_EASILY(Sort.WEAKNESS),
		/** On fire, or someone near him burning: he runs. */
		FEARS_FIRE(Sort.FEAR),
		/** A wolf, a bear, a ravager or a rider near: he runs. */
		FEARS_BEASTS(Sort.FEAR),
		/** Bleeding and hurt: he runs. */
		FEARS_BLOOD(Sort.FEAR);

		final Sort sort;

		Trait(Sort sort) {
			this.sort = sort;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		String tag() {
			return TRAIT_TAG + key();
		}

		/** The two can't go together in one captain. */
		boolean clashes(Trait other) {
			return pair(other, ARROW_PROOF, SOFT_TO_ARROWS) || pair(other, FIRE_BORN, BURNS_EASILY) || pair(other, FIRE_BORN, FEARS_FIRE);
		}

		private boolean pair(Trait other, Trait a, Trait b) {
			return this == a && other == b || this == b && other == a;
		}

		static @Nullable Trait parse(String key) {
			for (Trait t : values()) {
				if (t.key().equals(key)) {
					return t;
				}
			}
			return null;
		}
	}

	/** One player's meetings with a captain: how many, how the last went ({@code met}, {@code fled}, {@code beaten}, {@code spared}, {@code killed}, {@code questioned}), and when. */
	record Meeting(int times, String last, long when) {
		static final Codec<Meeting> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("times").forGetter(Meeting::times),
				Codec.STRING.fieldOf("last").forGetter(Meeting::last),
				Codec.LONG.fieldOf("when").forGetter(Meeting::when))
			.apply(i, Meeting::new));
	}

	/**
	 * A captain: his id, tribe, name (with his byname), rank, traits, the camp he's posted to (−1 none), his body in the
	 * world, whether he's a camp's chief (his body is the chief's), whether he's fallen, the head he wears (a player's id,
	 * or empty), the players he's killed, and his meetings by player id.
	 */
	record Captain(String id, String tribe, String name, int rank, List<String> traits, int post, Optional<UUID> body, boolean chief, boolean fallen,
		String trophy, List<String> slain, Map<String, Meeting> met) {
		static final Codec<Captain> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Captain::id),
				Codec.STRING.fieldOf("tribe").forGetter(Captain::tribe),
				Codec.STRING.fieldOf("name").forGetter(Captain::name),
				Codec.INT.fieldOf("rank").forGetter(Captain::rank),
				Codec.STRING.listOf().fieldOf("traits").forGetter(Captain::traits),
				Codec.INT.optionalFieldOf("post", -1).forGetter(Captain::post),
				UUIDUtil.STRING_CODEC.optionalFieldOf("body").forGetter(Captain::body),
				Codec.BOOL.optionalFieldOf("chief", false).forGetter(Captain::chief),
				Codec.BOOL.optionalFieldOf("fallen", false).forGetter(Captain::fallen),
				Codec.STRING.optionalFieldOf("trophy", "").forGetter(Captain::trophy),
				Codec.STRING.listOf().optionalFieldOf("slain", List.of()).forGetter(Captain::slain),
				Codec.unboundedMap(Codec.STRING, Meeting.CODEC).optionalFieldOf("met", Map.of()).forGetter(Captain::met))
			.apply(i, Captain::new));

		boolean has(Trait t) {
			return traits.contains(t.key());
		}

		Captain withBody(@Nullable UUID uuid) {
			return new Captain(id, tribe, name, rank, traits, post, Optional.ofNullable(uuid), chief, fallen, trophy, slain, met);
		}

		Captain withPost(int camp, boolean asChief) {
			return new Captain(id, tribe, name, rank, traits, camp, body, asChief, fallen, trophy, slain, met);
		}

		Captain fell() {
			return new Captain(id, tribe, name, rank, traits, post, Optional.empty(), chief, true, trophy, slain, met);
		}

		Captain met(UUID player, Meeting m) {
			Map<String, Meeting> all = new HashMap<>(met);
			all.put(player.toString(), m);
			return new Captain(id, tribe, name, rank, traits, post, body, chief, fallen, trophy, slain, Map.copyOf(all));
		}

		Captain climbed(int newRank, List<String> newTraits, String head, List<String> newSlain) {
			return new Captain(id, tribe, name, newRank, newTraits, post, body, chief, fallen, head, newSlain, met);
		}
	}

	/** On the Overworld: every captain by id. Saved. */
	static final AttachmentType<Map<String, Captain>> CAPTAINS = AttachmentRegistry.create(JaymosKingdoms.id("captains"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Captain.CODEC)));
	/** On the Overworld: tribe → when its last captain rose; tribe → the seat camp it last had. Saved. */
	static final AttachmentType<Map<String, Long>> RISEN = AttachmentRegistry.create(JaymosKingdoms.id("captains_risen"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	static final AttachmentType<Map<String, Integer>> SEATS = AttachmentRegistry.create(JaymosKingdoms.id("captains_seats"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** On the Overworld: camps whose store has been given a chief's orders. Saved. */
	static final AttachmentType<List<Integer>> ORDERED = AttachmentRegistry.create(JaymosKingdoms.id("captains_ordered"),
		builder -> builder.persistent(Codec.INT.listOf()));
	/** On a player: the captains sworn to kill them (ids). Saved, kept through death. */
	static final AttachmentType<List<String>> SWORN = AttachmentRegistry.create(JaymosKingdoms.id("sworn_foes"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** On a player: what they know of each captain (id → trait keys). Saved, kept through death. */
	static final AttachmentType<Map<String, List<String>>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("captains_known"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())).copyOnDeath());

	/** A fight between a captain and a player: its last blow, and whether the player had him down. */
	private static final class Fight {
		long last;
		boolean downed;
	}

	/** Captain id → player → their fight (kept in memory). */
	private static final Map<String, Map<UUID, Fight>> FIGHTS = new HashMap<>();
	/** A captain's body → until when he runs, where from, and when his fear can take him again. */
	private static final Map<UUID, Long> FEARED = new HashMap<>(), FEAR_READY = new HashMap<>();
	private static final Map<UUID, Vec3> FEARED_FROM = new HashMap<>();
	/** Bodies who've rallied in this fight, or got back up (hard to kill). */
	private static final Set<UUID> RALLIED = new HashSet<>(), GOT_UP = new HashSet<>();
	/** A killer in his scene over a player's body: until when (game time), and where the body lies. */
	private static final Map<UUID, Long> SCENE = new HashMap<>();
	private static final Map<UUID, Vec3> SCENE_AT = new HashMap<>();

	/** Something to do a few ticks on (server tick count), kept in memory. */
	private record Later(int due, Runnable what) {
	}

	private static final List<Later> LATER = new ArrayList<>();

	private static final String[] ORC_BYNAMES = {"the Cleaver", "Gut-Ripper", "the Hound", "Flesh-Eater", "the Slaver", "Black-Tongue", "the Drowner",
		"Ear-Taker", "the Lash", "Stone-Fist", "the Mad", "Rot-Breath", "the Tracker", "Neck-Snapper", "the Proud", "Pit-Born"};
	private static final String[] GOBLIN_BYNAMES = {"the Sneak", "Knife-Licker", "the Rat", "Bone-Picker", "the Twitch", "Snot-Nose", "the Poisoner",
		"Purse-Cutter", "the Gnawer", "Wobble-Eye", "the Cackler", "Trap-Maker"};

	private Captains() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				tick(Factions.level(server));
			}
			if (!LATER.isEmpty()) {
				List<Later> due = new ArrayList<>();
				LATER.removeIf(l -> l.due() <= server.getTickCount() && due.add(l));
				due.forEach(l -> l.what().run());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIGHTS.clear();
			SCENE.clear();
			SCENE_AT.clear();
			LATER.clear();
			FEARED.clear();
			FEAR_READY.clear();
			FEARED_FROM.clear();
			RALLIED.clear();
			GOT_UP.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			String id = idOf(entity);
			if (id == null) {
				return;
			}
			Captain c = get(level.getServer(), id);
			if (c == null || c.fallen()) {
				// (a captain who fell, or a body left from before he moved on)
				if (!(entity instanceof Worker w && Greenskins.chief(w.role()))) {
					entity.discard();
				}
				return;
			}
			Entity other = c.body().map(level::getEntity).orElse(null);
			if (other != null && other != entity && other.isAlive()) {
				// (two of him: the one already about stays)
				entity.discard();
			} else if (other == null) {
				put(level.getServer(), c.withBody(entity.getUUID()));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Captains::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> hurt(victim, source, taken, blocked));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (player instanceof ServerPlayer sp && held.is(Items.WRITTEN_BOOK)) {
				CustomData data = held.get(DataComponents.CUSTOM_DATA);
				if (data != null) {
					CompoundTag tag = data.copyTag();
					String captain = tag.getStringOr(ORDERS_CAPTAIN, "");
					Trait trait = Trait.parse(tag.getStringOr(ORDERS_TRAIT, ""));
					if (!captain.isEmpty() && trait != null) {
						// (a chief's orders, read: you know it now; the book opens as any)
						learn(sp, captain, trait);
					}
				}
			}
			return InteractionResult.PASS;
		});
	}

	// ---- who they are ------------------------------------------------------------------------------------------------------

	/** {@code e} is a captain's body. */
	public static boolean is(Entity e) {
		return e.entityTags().contains(TAG);
	}

	/** {@code e} is a captain with {@code t}. */
	static boolean has(Entity e, Trait t) {
		return e.entityTags().contains(t.tag());
	}

	/** The captain id {@code e} carries, or null. */
	static @Nullable String idOf(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(ID_TAG)) {
				return tag.substring(ID_TAG.length());
			}
		}
		return null;
	}

	private static Map<String, Captain> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CAPTAINS, Map.of());
	}

	static @Nullable Captain get(MinecraftServer server, String id) {
		return all(server).get(id);
	}

	private static void put(MinecraftServer server, Captain c) {
		Map<String, Captain> all = new HashMap<>(all(server));
		all.put(c.id(), c);
		Factions.level(server).setAttached(CAPTAINS, Map.copyOf(all));
	}

	/** {@code tribe}'s captains still standing, strongest first. */
	static List<Captain> of(MinecraftServer server, String tribe) {
		List<Captain> out = new ArrayList<>();
		for (Captain c : all(server).values()) {
			if (!c.fallen() && c.tribe().equals(tribe)) {
				out.add(c);
			}
		}
		out.sort(Comparator.comparingInt(Captain::rank).thenComparingInt(c -> c.slain().size()).reversed());
		return out;
	}

	private static @Nullable Captain of(Entity e) {
		String id = idOf(e);
		MinecraftServer server = e.level().getServer();
		return id == null || server == null ? null : get(server, id);
	}

	private static List<Trait> traits(Captain c) {
		List<Trait> out = new ArrayList<>();
		for (String key : c.traits()) {
			Trait t = Trait.parse(key);
			if (t != null) {
				out.add(t);
			}
		}
		return out;
	}

	/** 2 to 4 traits: one or two strengths, one or two weaknesses or fears. */
	private static List<String> roll(RandomSource random) {
		List<Trait> picked = new ArrayList<>();
		int strengths = 1 + random.nextInt(2), flaws = 1 + random.nextInt(2);
		for (int n = 0; n < 40 && (strengths > 0 || flaws > 0); n++) {
			Trait t = Trait.values()[random.nextInt(Trait.values().length)];
			boolean strong = t.sort == Sort.STRENGTH;
			if ((strong ? strengths : flaws) <= 0 || picked.contains(t) || picked.stream().anyMatch(t::clashes)) {
				continue;
			}
			picked.add(t);
			if (strong) {
				strengths--;
			} else {
				flaws--;
			}
		}
		return picked.stream().map(Trait::key).toList();
	}

	/** A strength {@code c} hasn't yet (and that doesn't clash with his traits), or null. */
	private static @Nullable Trait newStrength(Captain c, RandomSource random) {
		List<Trait> have = traits(c), open = new ArrayList<>();
		for (Trait t : Trait.values()) {
			if (t.sort == Sort.STRENGTH && !have.contains(t) && have.stream().noneMatch(t::clashes)) {
				open.add(t);
			}
		}
		return open.isEmpty() ? null : open.get(random.nextInt(open.size()));
	}

	/** A new captain's name: one of his kind's names and a byname. */
	private static String rollName(String tribe, RandomSource random) {
		boolean orcs = tribe.startsWith(Tribes.ORC + "@");
		String[] bynames = orcs ? ORC_BYNAMES : GOBLIN_BYNAMES;
		return Greenskins.name(orcs ? Heritage.Kind.ORC : Heritage.Kind.GOBLIN, random.nextLong()) + " " + bynames[random.nextInt(bynames.length)];
	}

	private static String newId(RandomSource random) {
		return Long.toString(random.nextLong() & Long.MAX_VALUE, 36);
	}

	// ---- every so often: the ladders filled, the posted shown, kings crowned, orders left -----------------------------

	private static void tick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Map<Integer, Faction> near = new HashMap<>();
		Set<String> tribes = new HashSet<>();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = camp.fallen() ? null : Camps.data(server, camp.id());
			String tribe = data == null || data.clearedAt() >= 0 ? null : Tribes.of(server, camp.id());
			BlockPos center = data == null ? null : data.center();
			if (tribe == null || level.getNearestPlayer(center.getX(), center.getY(), center.getZ(), POST_NEAR, false) == null) {
				continue;
			}
			near.put(camp.id(), camp);
			tribes.add(tribe);
		}
		for (String tribe : tribes) {
			fill(level, tribe);
		}
		seats(level);
		for (Captain c : List.copyOf(all(server).values())) {
			Faction camp = c.fallen() ? null : near.get(c.post());
			if (camp == null && !c.fallen() && tribes.contains(c.tribe()) && !standing(server, c.post())) {
				// (his camp is gone: he goes to another of the tribe's)
				repost(server, c);
			} else if (camp != null) {
				embody(level, c, camp);
			}
		}
		orders(level, near);
	}

	/** Camp {@code id} still stands (not fallen, not cleared). */
	private static boolean standing(MinecraftServer server, int id) {
		Faction f = id < 0 ? null : Factions.get(server, id);
		Camps.CampData data = f == null || f.fallen() ? null : Camps.data(server, id);
		return data != null && data.clearedAt() < 0;
	}

	/** {@code tribe}'s ladder filled: all at once the first time, then one new captain a day. */
	private static void fill(ServerLevel level, String tribe) {
		MinecraftServer server = level.getServer();
		List<Captain> standing = of(server, tribe);
		Map<String, Long> risen = level.getAttachedOrElse(RISEN, Map.of());
		long now = level.getGameTime();
		Long last = risen.get(tribe);
		if (standing.size() >= LADDER || last != null && now - last < RISE_GAP) {
			return;
		}
		List<Faction> camps = Tribes.camps(server, tribe);
		if (camps.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		int count = last == null ? LADDER - standing.size() : 1;
		for (int i = 0; i < count; i++) {
			Captain c = new Captain(newId(random), tribe, rollName(tribe, random), 1 + random.nextInt(3), roll(random), quietest(server, tribe, camps),
				Optional.empty(), false, false, "", List.of(), Map.of());
			put(server, c);
		}
		Map<String, Long> fresh = new HashMap<>(risen);
		fresh.put(tribe, now);
		level.setAttached(RISEN, Map.copyOf(fresh));
	}

	/** The camp of {@code camps} with the fewest of {@code tribe}'s captains posted to it. */
	private static int quietest(MinecraftServer server, String tribe, List<Faction> camps) {
		Map<Integer, Integer> posted = new HashMap<>();
		for (Captain c : of(server, tribe)) {
			posted.merge(c.post(), 1, Integer::sum);
		}
		Faction best = camps.getFirst();
		for (Faction f : camps) {
			if (posted.getOrDefault(f.id(), 0) < posted.getOrDefault(best.id(), 0)) {
				best = f;
			}
		}
		return best.id();
	}

	private static void repost(MinecraftServer server, Captain c) {
		List<Faction> camps = Tribes.camps(server, c.tribe());
		put(server, camps.isEmpty() ? c.withPost(-1, false).withBody(null) : c.withPost(quietest(server, c.tribe(), camps), false).withBody(null));
	}

	/** {@code c}, posted to {@code camp} with a player near: about in the world (mustered if he isn't). */
	private static void embody(ServerLevel level, Captain c, Faction camp) {
		MinecraftServer server = level.getServer();
		Camps.CampData data = Camps.data(server, camp.id());
		if (data == null) {
			return;
		}
		BlockPos center = data.center();
		Entity body = c.body().map(level::getEntity).orElse(null);
		if (body != null && body.isAlive()) {
			return;
		}
		AABB around = new AABB(center).inflate(96.0);
		List<Worker> found = c.chief()
			? Workers.near(level, around, w -> w.factionId() == camp.id() && Greenskins.chief(w.role()) && w.mob().isAlive())
			: Workers.near(level, around, w -> c.id().equals(idOf(w.mob())) && w.mob().isAlive());
		if (!found.isEmpty()) {
			bind(level, c, found.getFirst().mob());
			return;
		}
		if (c.chief() || !level.isLoaded(center) || level.getNearestPlayer(center.getX(), center.getY(), center.getZ(), MUSTER_HIDE, false) != null) {
			return;
		}
		muster(level, c, camp, center, center, HOME_RADIUS);
	}

	/** {@code c} as one of {@code camp}'s, at it: a goblin or a grunt as an MCA villager, dressed and built for his rank. */
	private static void muster(ServerLevel level, Captain c, Faction camp, BlockPos center, BlockPos home, int radius) {
		boolean orcs = c.tribe().startsWith(Tribes.ORC + "@");
		RandomSource random = level.getRandom();
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		folk.setup(orcs ? Orcs.GRUNT : Goblins.GOBLIN, camp, home, radius);
		gear(folk, c.rank(), orcs);
		folk.setCustomName(Component.literal(c.name()));
		level.addFreshEntity(folk);
		PathfinderMob mob = McaCompat.raider(level, folk);
		if (mob == null) {
			mob = folk;
		}
		build(mob, c.rank());
		mob.setPersistenceRequired();
		bind(level, c, mob);
	}

	/** A captain's gear by rank: a better weapon from 3, mail from 2, iron from 4. */
	private static void gear(Folk folk, int rank, boolean orcs) {
		folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(orcs ? rank >= 3 ? Items.IRON_AXE : Items.STONE_AXE : rank >= 3 ? Items.IRON_SWORD : Items.STONE_SWORD));
		if (rank >= 2) {
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(rank >= 4 ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(rank >= 4 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
		}
		if (rank >= RANK_MAX) {
			folk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		}
	}

	/** His build for {@code rank}: bigger, tougher and harder-hitting than his kind. */
	private static void build(PathfinderMob mob, int rank) {
		AttributeInstance size = mob.getAttribute(Attributes.SCALE);
		if (size != null) {
			size.setBaseValue(size.getBaseValue() * (SCALE_BASE + SCALE_PER_RANK * rank));
		}
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(health.getBaseValue() * (HEALTH_BASE + HEALTH_PER_RANK * rank));
		}
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(damage.getBaseValue() + DAMAGE_PER_RANK * rank);
		}
		mob.setHealth(mob.getMaxHealth());
	}

	/** One rank more on {@code mob}'s build. */
	private static void climb(LivingEntity mob) {
		AttributeInstance size = mob.getAttribute(Attributes.SCALE);
		if (size != null) {
			size.setBaseValue(size.getBaseValue() + SCALE_PER_RANK);
		}
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(health.getBaseValue() * (1.0 + HEALTH_PER_RANK / HEALTH_BASE));
		}
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(damage.getBaseValue() + DAMAGE_PER_RANK);
		}
		mob.heal(mob.getMaxHealth());
	}

	/** {@code mob} is {@code c} from now on: his name, his tags, the head he wears. */
	private static void bind(ServerLevel level, Captain c, LivingEntity mob) {
		bind(level, c, mob, true);
	}

	/** As {@link #bind}; {@code dress}: the head he wears goes on now (else at his scene's height, {@link #slew}). */
	private static void bind(ServerLevel level, Captain c, LivingEntity mob, boolean dress) {
		McaCompat.rename(mob, c.name());
		for (String tag : List.copyOf(mob.entityTags())) {
			if (tag.startsWith(ID_TAG) || tag.startsWith(TRAIT_TAG)) {
				mob.removeTag(tag);
			}
		}
		mob.addTag(TAG);
		mob.addTag(ID_TAG + c.id());
		for (Trait t : traits(c)) {
			mob.addTag(t.tag());
		}
		if (dress) {
			trophy(mob, c);
		}
		put(level.getServer(), c.withBody(mob.getUUID()));
	}

	/** The head of the last player he killed, worn (it drops when he dies). */
	private static void trophy(LivingEntity mob, Captain c) {
		if (c.trophy().isEmpty() || mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) {
			return;
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		try {
			head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(UUID.fromString(c.trophy())));
		} catch (IllegalArgumentException e) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.HEAD, head);
		if (mob instanceof Mob m) {
			m.setDropChance(EquipmentSlot.HEAD, 1.0F);
		}
	}

	/** A king fell (his tribe has another seat): the strongest of its captains takes it, as its chief from now on. */
	private static void seats(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Set<String> tribes = new HashSet<>();
		for (Captain c : all(server).values()) {
			if (!c.fallen()) {
				tribes.add(c.tribe());
			}
		}
		Map<String, Integer> seats = level.getAttachedOrElse(SEATS, Map.of());
		Map<String, Integer> fresh = null;
		for (String tribe : tribes) {
			Faction seat = Tribes.seat(server, tribe);
			int now = seat == null ? -1 : seat.id();
			Integer was = seats.get(tribe);
			if (was != null && was == now) {
				continue;
			}
			if (fresh == null) {
				fresh = new HashMap<>(seats);
			}
			fresh.put(tribe, now);
			if (was != null && was >= 0 && now >= 0) {
				crown(level, tribe, seat);
			}
		}
		if (fresh != null) {
			level.setAttached(SEATS, Map.copyOf(fresh));
		}
	}

	private static void crown(ServerLevel level, String tribe, Faction seat) {
		MinecraftServer server = level.getServer();
		for (Captain c : of(server, tribe)) {
			if (c.chief() && c.post() == seat.id()) {
				// (the chief there is one of them already)
				return;
			}
		}
		Captain heir = of(server, tribe).stream().filter(c -> !c.chief()).findFirst().orElse(null);
		if (heir == null) {
			return;
		}
		Entity old = heir.body().map(level::getEntity).orElse(null);
		if (old != null) {
			level.sendParticles(ParticleTypes.POOF, old.getX(), old.getY() + 1.0, old.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			old.discard();
		}
		put(server, heir.withPost(seat.id(), true).withBody(null));
		Post.news(server, Component.translatable("news.jaymoskingdoms.captain.crowned", heir.name(), Tribes.name(tribe)));
	}

	/** A chief's orders in every camp's store that hasn't had them: a page giving away one of a captain's traits. */
	private static void orders(ServerLevel level, Map<Integer, Faction> near) {
		MinecraftServer server = level.getServer();
		List<Integer> ordered = level.getAttachedOrElse(ORDERED, List.of());
		Set<Integer> fresh = null;
		for (Faction camp : near.values()) {
			String tribe = ordered.contains(camp.id()) ? null : Tribes.of(server, camp.id());
			List<Captain> captains = tribe == null ? List.of() : of(server, tribe);
			if (captains.isEmpty() || !Storage.loaded(level, camp)) {
				continue;
			}
			Captain c = captains.stream().filter(x -> x.post() == camp.id()).findFirst().orElse(captains.get(level.getRandom().nextInt(captains.size())));
			List<Trait> traits = traits(c);
			if (traits.isEmpty()) {
				continue;
			}
			Trait t = traits.get(level.getRandom().nextInt(traits.size()));
			if (Storage.put(level, camp, ordersBook(c, t)).isEmpty()) {
				if (fresh == null) {
					fresh = new HashSet<>(ordered);
				}
				fresh.add(camp.id());
			}
		}
		if (fresh != null) {
			level.setAttached(ORDERED, List.copyOf(fresh));
		}
	}

	private static ItemStack ordersBook(Captain c, Trait t) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = List.of(Filterable.passThrough(Component.translatable("book.jaymoskingdoms.captain_orders.page",
			Component.literal(c.name()), told(c, t))));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Orders"), "", 0, pages, true));
		book.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.captain_orders"));
		CompoundTag tag = new CompoundTag();
		tag.putString(ORDERS_CAPTAIN, c.id());
		tag.putString(ORDERS_TRAIT, t.key());
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}

	// ---- his day -------------------------------------------------------------------------------------------------------

	/**
	 * (From {@code Workers.step}) a captain's step: his fears, rage and rally, the players he meets, the head he wears;
	 * on a hunting band, the trail. True if this was his step (his brain waits).
	 */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		Captain c = of(mob);
		if (c == null || c.fallen()) {
			return false;
		}
		if (Downed.isDowned(mob)) {
			Map<UUID, Fight> fights = FIGHTS.get(c.id());
			if (fights != null) {
				fights.values().forEach(f -> f.downed = true);
			}
			return false;
		}
		long now = level.getGameTime();
		Long scene = SCENE.get(mob.getUUID());
		if (scene != null && now < scene) {
			// (his scene over a player's body: he stands over it)
			mob.getNavigation().stop();
			Vec3 body = SCENE_AT.get(mob.getUUID());
			if (body != null) {
				mob.getLookControl().setLookAt(body.x, body.y, body.z);
			}
			return true;
		}
		if (scene != null) {
			SCENE.remove(mob.getUUID());
			SCENE_AT.remove(mob.getUUID());
		}
		if (afraid(level, mob, now)) {
			return true;
		}
		LivingEntity target = Rulers.target(mob);
		if (has(mob, Trait.BLOOD_RAGE) && target != null && mob.getHealth() < mob.getMaxHealth() * RAGE_AT && mob.tickCount % 20 == 0) {
			mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 1, false, true));
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, true));
		}
		if (has(mob, Trait.RALLYING) && target != null && RALLIED.add(mob.getUUID())) {
			rally(level, mob, w.factionId(), target);
		} else if (target == null && mob.tickCount % 200 == 0) {
			RALLIED.remove(mob.getUUID());
		}
		if (mob.tickCount % 20 == 0) {
			meet(level, c, mob, now);
			fights(level, c, mob, now);
			trophy(mob, c);
		}
		return Tribes.hunter(mob) && Tribes.hunterStep(level, w);
	}

	/** Whether {@code mob} runs from what he fears (and does). */
	private static boolean afraid(ServerLevel level, PathfinderMob mob, long now) {
		UUID id = mob.getUUID();
		Long until = FEARED.get(id);
		if (until != null && now < until) {
			flee(mob, FEARED_FROM.getOrDefault(id, mob.position()));
			return true;
		}
		if (until != null) {
			FEARED.remove(id);
			FEARED_FROM.remove(id);
		}
		if (mob.tickCount % 10 != 0 || now < FEAR_READY.getOrDefault(id, 0L)) {
			return false;
		}
		Trait fear = null;
		Vec3 from = null;
		if (has(mob, Trait.FEARS_FIRE)) {
			if (mob.isOnFire()) {
				fear = Trait.FEARS_FIRE;
				LivingEntity by = mob.getLastHurtByMob();
				from = by != null ? by.position() : mob.position();
			} else {
				LivingEntity burning = level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(FEAR_RANGE), e -> e != mob && e.isOnFire())
					.stream().findFirst().orElse(null);
				if (burning != null && burning.isOnFire()) {
					fear = Trait.FEARS_FIRE;
					from = burning.position();
				}
			}
		}
		if (fear == null && has(mob, Trait.FEARS_BEASTS)) {
			for (Entity e : level.getEntities(mob, mob.getBoundingBox().inflate(FEAR_RANGE), Captains::beast)) {
				fear = Trait.FEARS_BEASTS;
				from = e.position();
				break;
			}
		}
		if (fear == null && has(mob, Trait.FEARS_BLOOD) && mob.getHealth() < mob.getMaxHealth() * BLOOD_AT && PowersCompat.cuts(mob) > 0) {
			fear = Trait.FEARS_BLOOD;
			LivingEntity by = mob.getLastHurtByMob();
			from = by != null ? by.position() : mob.position();
		}
		if (fear == null) {
			return false;
		}
		FEARED.put(id, now + FEAR_TICKS);
		FEARED_FROM.put(id, from);
		FEAR_READY.put(id, now + FEAR_TICKS + FEAR_AGAIN);
		mob.setTarget(null);
		Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.captain.afraid." + fear.key() + "." + mob.getRandom().nextInt(LINES)), Speech.RED, SAY_RANGE);
		flee(mob, from);
		return true;
	}

	/** What a captain who fears beasts runs from: a wolf, a bear, a ravager, or anyone on horseback. */
	private static boolean beast(Entity e) {
		if (!e.isAlive()) {
			return false;
		}
		EntityType<?> type = e.getType();
		return type == EntityTypes.WOLF || type == EntityTypes.POLAR_BEAR || type == EntityTypes.RAVAGER
			|| e instanceof AbstractHorse && e.isVehicle() || e.getVehicle() instanceof AbstractHorse;
	}

	private static void flee(PathfinderMob mob, Vec3 from) {
		mob.setTarget(null);
		if (mob.tickCount % 10 != 0 && !mob.getNavigation().isDone()) {
			return;
		}
		Vec3 away = mob.position().subtract(from);
		if (away.lengthSqr() < 0.01) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		}
		Vec3 to = mob.position().add(away.normalize().scale(FLEE_DISTANCE));
		mob.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
	}

	/** His own near come to his foe, fighting harder. */
	private static void rally(ServerLevel level, PathfinderMob mob, int camp, LivingEntity foe) {
		for (Worker w : Workers.near(level, mob.getBoundingBox().inflate(RALLY_RANGE), x -> x.factionId() == camp && x.mob() != mob && x.mob().isAlive())) {
			w.mob().setTarget(foe);
			w.mob().addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, 0, false, true));
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2F, 1.1F);
		Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.captain.rally." + mob.getRandom().nextInt(LINES)), Speech.RED, SAY_RANGE);
	}

	// ---- meetings ------------------------------------------------------------------------------------------------------

	/** Players he sees: the first time a film and his name, after that a word about last time. */
	private static void meet(ServerLevel level, Captain c, PathfinderMob mob, long now) {
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(MEET_RANGE),
			p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && mob.hasLineOfSight(p))) {
			Meeting m = c.met().get(p.getStringUUID());
			if (m != null && now - m.when() < MEET_GAP) {
				continue;
			}
			Component line;
			if (m == null) {
				PowersCompat.film(mob, p, PowersCompat.FILM_PUSH_IN, FILM_TICKS, mob.getEyePosition(), mob, FILM_WITNESSES);
				line = Component.translatable("talk.jaymoskingdoms.captain.first." + mob.getRandom().nextInt(LINES), Recollections.nameTo(p, mob),
					Component.literal(c.name()), Tribes.name(c.tribe()));
			} else {
				line = Component.translatable("talk.jaymoskingdoms.captain.again." + m.last() + "." + mob.getRandom().nextInt(LINES),
					Recollections.nameTo(p, mob), Component.literal(c.name()));
			}
			Speech.say(level, mob, line, Speech.RED, SAY_RANGE);
			c = c.met(p.getUUID(), new Meeting(m == null ? 1 : m.times() + 1, m == null ? "met" : m.last(), now));
			put(level.getServer(), c);
		}
	}

	/** Fights over: a player who got away ran, or beat him and left him. */
	private static void fights(ServerLevel level, Captain c, PathfinderMob mob, long now) {
		Map<UUID, Fight> fights = FIGHTS.get(c.id());
		if (fights == null) {
			return;
		}
		for (var it = fights.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Fight f = e.getValue();
			if (now - f.last > FIGHT_FORGOTTEN) {
				it.remove();
				continue;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (now - f.last < FIGHT_COOLS || p != null && p.isAlive() && p.level() == level && p.distanceTo(mob) < FLED_RANGE) {
				continue;
			}
			it.remove();
			if (p == null || p.isAlive()) {
				outcome(level.getServer(), c.id(), e.getKey(), f.downed ? "beaten" : "fled", now);
			}
		}
		if (fights.isEmpty()) {
			FIGHTS.remove(c.id());
		}
	}

	/** {@code player}'s last meeting with captain {@code id} went as {@code how}. */
	private static void outcome(MinecraftServer server, String id, UUID player, String how, long now) {
		Captain c = get(server, id);
		if (c == null) {
			return;
		}
		Meeting m = c.met().get(player.toString());
		put(server, c.met(player, new Meeting(m == null ? 1 : m.times(), how, m == null ? now : m.when())));
	}

	private static void fought(Captain c, UUID player, long now) {
		FIGHTS.computeIfAbsent(c.id(), k -> new HashMap<>()).computeIfAbsent(player, k -> new Fight()).last = now;
	}

	/** (From {@link Questioning#ended}) {@code player} let {@code body} go after holding them down. */
	static void spared(ServerPlayer player, LivingEntity body, boolean asked) {
		Captain c = of(body);
		if (c == null) {
			return;
		}
		Map<UUID, Fight> fights = FIGHTS.get(c.id());
		if (fights != null) {
			fights.remove(player.getUUID());
		}
		outcome(player.level().getServer(), c.id(), player.getUUID(), asked ? "questioned" : "spared", Factions.level(player.level().getServer()).getGameTime());
	}

	// ---- blows and deaths ------------------------------------------------------------------------------------------------

	/** (From {@code LivingEntityHurtMixin}) a blow on a captain, as his traits take it. */
	public static float dealt(LivingEntity self, DamageSource source, float amount) {
		if (amount <= 0.0F || !is(self)) {
			return amount;
		}
		boolean fire = source.is(DamageTypeTags.IS_FIRE), shot = source.is(DamageTypeTags.IS_PROJECTILE);
		if (fire && has(self, Trait.FIRE_BORN)) {
			return 0.0F;
		}
		float out = amount;
		if (fire && has(self, Trait.BURNS_EASILY)) {
			out *= BURNS;
		}
		if (shot) {
			out *= has(self, Trait.ARROW_PROOF) ? ARROW_PROOF : has(self, Trait.SOFT_TO_ARROWS) ? ARROW_SOFT : 1.0F;
		}
		if (source.getEntity() instanceof LivingEntity by && source.getDirectEntity() == by) {
			if (has(self, Trait.THICK_HIDE)) {
				out *= THICK_HIDE;
			}
			ItemStack held = by.getMainHandItem();
			if (has(self, Trait.GLASS_JAW) && (held.isEmpty() || PowersCompat.blunt(held))) {
				out *= GLASS_JAW;
				self.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2, false, true));
			}
		}
		if (has(self, Trait.BLIND_SIDE) && source.getEntity() instanceof LivingEntity by && self instanceof Mob mob && Rulers.target(mob) != by) {
			out *= BLIND_SIDE;
		}
		return out;
	}

	/** A blow landed: fights noted, venom, and one hard to kill back on his feet. */
	private static void hurt(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Entity by = source.getEntity();
		if (is(victim)) {
			Captain c = of(victim);
			if (c != null && by instanceof ServerPlayer player) {
				fought(c, player.getUUID(), now);
			}
			if (victim.isAlive() && has(victim, Trait.HARD_TO_KILL) && victim.getHealth() < victim.getMaxHealth() * HARD_AT && GOT_UP.add(victim.getUUID())) {
				victim.setHealth(victim.getMaxHealth() * HARD_TO);
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2F, 1.2F);
				Speech.say(level, victim, Component.translatable("talk.jaymoskingdoms.captain.not_yet." + victim.getRandom().nextInt(LINES)), Speech.RED, SAY_RANGE);
			}
		}
		if (by instanceof LivingEntity attacker && is(attacker)) {
			Captain c = of(attacker);
			if (c != null && victim instanceof ServerPlayer player) {
				fought(c, player.getUUID(), now);
			}
			if (!blocked && taken > 0.0F && victim.isAlive() && has(attacker, Trait.VENOMOUS) && source.getDirectEntity() == attacker) {
				victim.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_TICKS, 0), attacker);
			}
		}
	}

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity killer && killer.isAlive()) {
			slew(level, killer, player);
			return;
		}
		Captain c = is(entity) ? of(entity) : null;
		if (c == null || c.fallen() || !c.body().map(entity.getUUID()::equals).orElse(true)) {
			return;
		}
		MinecraftServer server = level.getServer();
		put(server, c.fell());
		FIGHTS.remove(c.id());
		UUID id = entity.getUUID();
		FEARED.remove(id);
		FEAR_READY.remove(id);
		FEARED_FROM.remove(id);
		RALLIED.remove(id);
		GOT_UP.remove(id);
		if (source.getEntity() instanceof ServerPlayer player) {
			List<String> sworn = player.getAttachedOrElse(SWORN, List.of());
			if (sworn.contains(c.id())) {
				List<String> left = new ArrayList<>(sworn);
				left.remove(c.id());
				player.setAttached(SWORN, List.copyOf(left));
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captain.sworn_fallen", c.name()));
			}
			Post.news(server, Component.translatable("news.jaymoskingdoms.captain.fallen", c.name(), Tribes.name(c.tribe()), player.getDisplayName()));
		}
	}

	/**
	 * {@code killer} killed {@code player}: a captain climbs a rank, gains a strength and wears their head; one of a tribe's
	 * common folk becomes a captain. Either way he's sworn to them now.
	 */
	private static void slew(ServerLevel level, LivingEntity killer, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		Captain c = is(killer) ? of(killer) : null;
		boolean risen = false;
		if (c == null || c.fallen()) {
			String tribe = Tribes.of(killer);
			if (tribe == null || !(killer instanceof Worker w) || of(server, tribe).size() >= LADDER_MAX) {
				return;
			}
			boolean chief = Greenskins.chief(w.role());
			String[] bynames = tribe.startsWith(Tribes.ORC + "@") ? ORC_BYNAMES : GOBLIN_BYNAMES;
			String name = chief ? killer.getName().getString() : killer.getName().getString() + " " + bynames[random.nextInt(bynames.length)];
			c = new Captain(newId(random), tribe, name, 1, roll(random), w.factionId(), Optional.of(killer.getUUID()), chief, false, "", List.of(), Map.of());
			risen = true;
		} else {
			List<String> traits = new ArrayList<>(c.traits());
			Trait gained = newStrength(c, random);
			if (gained != null) {
				traits.add(gained.key());
			}
			int rank = Math.min(RANK_MAX, c.rank() + 1);
			if (rank > c.rank()) {
				climb(killer);
			}
			c = c.climbed(rank, List.copyOf(traits), c.trophy(), c.slain());
		}
		List<String> slain = new ArrayList<>(c.slain());
		slain.add(player.getDisplayName().getString());
		c = c.climbed(c.rank(), c.traits(), player.getStringUUID(), List.copyOf(slain));
		Meeting m = c.met().get(player.getStringUUID());
		c = c.met(player.getUUID(), new Meeting(m == null ? 1 : m.times() + 1, "killed", now));
		if (killer.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) {
			// (the new head goes on in place of the old)
			killer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		}
		bind(level, c, killer, false);
		scene(level, killer, player, c.id(), risen);
		FIGHTS.getOrDefault(c.id(), Map.of()).remove(player.getUUID());
		List<String> sworn = new ArrayList<>(player.getAttachedOrElse(SWORN, List.of()));
		if (!sworn.contains(c.id())) {
			sworn.add(c.id());
			player.setAttached(SWORN, List.copyOf(sworn));
		}
		Speech.say(level, killer, Component.translatable("talk.jaymoskingdoms.captain.gloat." + random.nextInt(LINES), player.getDisplayName()), Speech.RED, SAY_RANGE);
		Post.news(server, Component.translatable(risen ? "news.jaymoskingdoms.captain.risen" : "news.jaymoskingdoms.captain.slew", c.name(),
			player.getDisplayName(), Tribes.name(c.tribe())));
	}

	/**
	 * (Andy: "a short scene before you respawn of the goblin getting promoted") {@code player}'s camera on {@code killer}
	 * before their death screen (Powers' film after death): he stands over the body and gloats, and halfway through he's
	 * raised (a roar, the head put on, his own cheering him by name). Without Powers' film it all still happens in the world.
	 */
	private static void scene(ServerLevel level, LivingEntity killer, ServerPlayer player, String id, boolean risen) {
		long now = level.getGameTime();
		SCENE.put(killer.getUUID(), now + SCENE_TICKS);
		SCENE_AT.put(killer.getUUID(), player.position());
		PowersCompat.deathFilm(player, PowersCompat.FILM_CUTS, SCENE_TICKS, killer);
		int due = level.getServer().getTickCount() + SCENE_RAISED;
		UUID killerId = killer.getUUID();
		LATER.add(new Later(due, () -> raised(level, killerId, id, risen)));
	}

	/** The height of his scene: the head goes on, a roar, and his own near cheer him by name. */
	private static void raised(ServerLevel level, UUID killerId, String id, boolean risen) {
		Captain c = get(level.getServer(), id);
		if (!(level.getEntity(killerId) instanceof LivingEntity killer) || !killer.isAlive() || c == null || c.fallen()) {
			return;
		}
		trophy(killer, c);
		level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, killer.getRandom().nextFloat() * 0.2F + 0.9F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, killer.getX(), killer.getY() + killer.getBbHeight(), killer.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
		level.sendParticles(ParticleTypes.CRIT, killer.getX(), killer.getY() + killer.getBbHeight() * 0.6, killer.getZ(), 30, 0.6, 0.6, 0.6, 0.3);
		if (killer instanceof Mob mob) {
			mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}
		Speech.say(level, killer, Component.translatable("talk.jaymoskingdoms.captain." + (risen ? "risen" : "climbed") + "." + killer.getRandom().nextInt(LINES),
			Component.literal(c.name())), Speech.RED, SAY_RANGE);
		int faction = killer instanceof Worker w ? w.factionId() : -1;
		int cheers = 0;
		for (Worker w : Workers.near(level, killer.getBoundingBox().inflate(CHEER_RANGE), x -> x.factionId() == faction && x.mob() != killer && x.mob().isAlive())) {
			if (cheers++ >= 3) {
				break;
			}
			Speech.say(level, w.mob(), Component.translatable("talk.jaymoskingdoms.captain.cheer." + w.mob().getRandom().nextInt(LINES), Component.literal(c.name())),
				Speech.RED, SAY_RANGE);
		}
	}

	// ---- what you know of them -------------------------------------------------------------------------------------------

	/** How {@code t} of {@code c} is told (hearsay). */
	static Component told(Captain c, Trait t) {
		return Component.translatable("captain.jaymoskingdoms.trait." + t.key() + ".told", Component.literal(c.name()));
	}

	/** {@code player} knows {@code t} of captain {@code id} now. */
	static void learn(ServerPlayer player, String id, Trait t) {
		Map<String, List<String>> known = new HashMap<>(player.getAttachedOrElse(KNOWN, Map.of()));
		List<String> of = new ArrayList<>(known.getOrDefault(id, List.of()));
		if (of.contains(t.key())) {
			return;
		}
		of.add(t.key());
		known.put(id, List.copyOf(of));
		player.setAttached(KNOWN, Map.copyOf(known));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captain.learned"));
	}

	private static List<String> known(ServerPlayer player, String id) {
		return player.getAttachedOrElse(KNOWN, Map.of()).getOrDefault(id, List.of());
	}

	/** A captain of {@code tribe} (those at {@code camp} first) with a trait {@code player} doesn't know of the {@code sorts}, and it; or null. */
	private static Map.@Nullable Entry<Captain, Trait> unknown(ServerPlayer player, String tribe, int camp, Set<Sort> sorts, RandomSource random) {
		List<Captain> captains = new ArrayList<>(of(player.level().getServer(), tribe));
		captains.sort(Comparator.comparingInt(c -> c.post() == camp ? 0 : 1));
		for (Captain c : captains) {
			List<String> known = known(player, c.id());
			List<Trait> open = traits(c).stream().filter(t -> sorts.contains(t.sort) && !known.contains(t.key())).toList();
			if (!open.isEmpty()) {
				return Map.entry(c, open.get(random.nextInt(open.size())));
			}
		}
		return null;
	}

	/** (Questioning) {@code body}'s tribe has a captain with something {@code player} doesn't know. */
	static boolean asks(ServerPlayer player, LivingEntity body) {
		String tribe = Tribes.of(body);
		return tribe != null && unknown(player, tribe, Rulers.faction(body), EnumSet.allOf(Sort.class), body.getRandom()) != null;
	}

	/** (Questioning) "What does your captain fear?": a fear or weakness of one of their captains ({@code lies}: one he hasn't). */
	static Component questioned(ServerPlayer player, LivingEntity body, boolean lies) {
		String tribe = Tribes.of(body);
		RandomSource random = body.getRandom();
		Map.Entry<Captain, Trait> e = tribe == null ? null : unknown(player, tribe, Rulers.faction(body), EnumSet.of(Sort.WEAKNESS, Sort.FEAR), random);
		if (e == null && tribe != null) {
			e = unknown(player, tribe, Rulers.faction(body), EnumSet.allOf(Sort.class), random);
		}
		if (e == null) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.captain.none." + random.nextInt(LINES), Recollections.nameTo(player, body));
		}
		Captain c = e.getKey();
		Trait t = e.getValue();
		if (lies) {
			List<Trait> false_ = new ArrayList<>();
			for (Trait x : Trait.values()) {
				if (x.sort != Sort.STRENGTH && !c.has(x)) {
					false_.add(x);
				}
			}
			if (!false_.isEmpty()) {
				t = false_.get(random.nextInt(false_.size()));
			}
		} else {
			learn(player, c.id(), t);
		}
		return Component.translatable("dialogue.chat.jaymoskingdoms.captain.told." + random.nextInt(LINES), Recollections.nameTo(player, body), told(c, t));
	}

	/** (MCA's talk) a tribe's pedlar: what he's heard of its captains, for {@link #RUMOUR_PRICE} emeralds. */
	public static boolean pedlarHears(LivingEntity villager) {
		return Pedlars.pedlar(villager) && Tribes.of(villager) != null;
	}

	/** (MCA's talk) {@code player} asks the pedlar what he's heard of the captains: a rumour, paid for. */
	public static Component pedlarTells(ServerPlayer player, LivingEntity pedlar) {
		String tribe = Tribes.of(pedlar);
		RandomSource random = pedlar.getRandom();
		Map.Entry<Captain, Trait> e = tribe == null ? null : unknown(player, tribe, -1, EnumSet.allOf(Sort.class), random);
		if (e == null) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.captain.pedlar.none." + random.nextInt(LINES), Recollections.nameTo(player, pedlar));
		}
		var inv = player.getInventory();
		int have = 0;
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			if (inv.getItem(slot).is(Items.EMERALD)) {
				have += inv.getItem(slot).getCount();
			}
		}
		if (have < RUMOUR_PRICE) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.captain.pedlar.price." + random.nextInt(LINES), Recollections.nameTo(player, pedlar),
				Component.literal(Integer.toString(RUMOUR_PRICE)));
		}
		int owed = RUMOUR_PRICE;
		for (int slot = 0; slot < inv.getContainerSize() && owed > 0; slot++) {
			ItemStack s = inv.getItem(slot);
			if (s.is(Items.EMERALD)) {
				int take = Math.min(owed, s.getCount());
				s.shrink(take);
				owed -= take;
			}
		}
		learn(player, e.getKey().id(), e.getValue());
		return Component.translatable("dialogue.chat.jaymoskingdoms.captain.pedlar.told." + random.nextInt(LINES), Recollections.nameTo(player, pedlar),
			told(e.getKey(), e.getValue()));
	}

	/** (The journal) {@code player}'s sworn foes, then the other captains they know something of. */
	static List<Journal.Entry> journal(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		List<Journal.Entry> out = new ArrayList<>();
		List<String> sworn = player.getAttachedOrElse(SWORN, List.of());
		List<String> ids = new ArrayList<>(sworn);
		for (String id : player.getAttachedOrElse(KNOWN, Map.of()).keySet()) {
			if (!ids.contains(id)) {
				ids.add(id);
			}
		}
		for (String id : ids) {
			Captain c = get(server, id);
			if (c == null || c.fallen()) {
				continue;
			}
			boolean foe = sworn.contains(id);
			Faction post = c.post() < 0 ? null : Factions.get(server, c.post());
			MutableComponent text = Component.translatable("journal.jaymoskingdoms.captain.rank." + Math.max(1, Math.min(RANK_MAX, c.rank())));
			List<String> known = known(player, id);
			if (known.isEmpty()) {
				text.append(" ").append(Component.translatable("journal.jaymoskingdoms.captain.nothing_known"));
			} else {
				MutableComponent list = Component.empty();
				for (int i = 0; i < known.size(); i++) {
					if (i > 0) {
						list.append(", ");
					}
					list.append(Component.translatable("captain.jaymoskingdoms.trait." + known.get(i)));
				}
				text.append(" ").append(Component.translatable("journal.jaymoskingdoms.captain.known", list));
			}
			out.add(new Journal.Entry(false, false,
				Component.translatable(foe ? "journal.jaymoskingdoms.captain.sworn" : "journal.jaymoskingdoms.captain.captain", c.name()),
				Tribes.name(c.tribe()), text, Component.translatable(foe ? "journal.jaymoskingdoms.captain.sworn.objective" : "journal.jaymoskingdoms.captain.objective"),
				post == null ? Optional.empty() : Optional.of(post.seat())));
		}
		return out;
	}

	/**
	 * (From {@link Tribes}' hunting bands) one of {@code tribe}'s captains not about now leads the band: mustered with it at
	 * {@code at}. Null if none is free.
	 */
	static @Nullable PathfinderMob leadBand(ServerLevel level, String tribe, Faction camp, BlockPos at, BlockPos home) {
		for (Captain c : of(level.getServer(), tribe)) {
			Entity body = c.body().map(level::getEntity).orElse(null);
			if (c.chief() || body != null && body.isAlive()) {
				continue;
			}
			muster(level, c, camp, at, home, Raids.RAID_HOME_RADIUS);
			Captain now = get(level.getServer(), c.id());
			Entity led = now == null ? null : now.body().map(level::getEntity).orElse(null);
			return led instanceof PathfinderMob mob ? mob : null;
		}
		return null;
	}

	/** (CripplingBlows) {@code attacker}'s chance to cripple, if his traits set one; else -1. */
	static float cripples(LivingEntity attacker) {
		return has(attacker, Trait.HEAVY_HANDED) ? HEAVY_HANDED : -1.0F;
	}

}
