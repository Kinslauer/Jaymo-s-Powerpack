package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Ship;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.246) Andy: "I want each start to have some kind of story adjacent reference from the villagers you meet. at least
 * pertaining to the start. the rest can be emergent." And "I shouldnt have to introduce myself to the ship crew, also
 * they should know who I am and refer to me as a powder monkey or deckhand."
 * <ul>
 * <li>Where a life story set you down ({@link #LANDING}: the start, the place, the realm or camp it was about, when).</li>
 * <li>For {@link #FRESH} ticks after, a villager you first talk to (MCA's menu) near that place, or in the land of the
 * realm your story was about, says something of how you came ({@code story.jaymoskingdoms.met.<start>.N}) instead of
 * MCA's stranger's greeting ("Are you an adventurer?"); each once.</li>
 * <li>Your shipmates ({@link #shipmate}: the crew of the whaler you signed on to) know you from the start: you know
 * their names, they never greet you as a stranger, and they call you by your berth ({@link #berth}).</li>
 * <li>(0.7.503) Andy: "the relevant folk should know you as a sworn start". The king, the lords and the guards of the
 * realm a Sworn start swore you to ({@link #swornRealm}) know you from the start, for as long as you stay its member:
 * you know their names (no introduction), they've met you, they start warm to you ({@link #SWORN_HEARTS}), and they
 * open their menu as your lord or your fellow guard ({@code story.jaymoskingdoms.sworn.<ruler|guard>.N}).</li>
 * </ul>
 */
public final class StoryTalk {
	/** How long (game ticks) after a story's start the folk about it still talk of it: three days. */
	static final long FRESH = 3L * 24000L;
	/** How near (blocks) the landing a villager is "about it". */
	static final double NEAR = 112.0;
	/** Lines for each start ({@code story.jaymoskingdoms.met.<start>.0-2}). */
	static final int MET_LINES = 3;
	/** Lines a shipmate greets you with ({@code story.jaymoskingdoms.mate.0-4}). */
	static final int MATE_LINES = 5;
	/** Below this scale you're the ship's boy: everyone calls you powder monkey. */
	static final float SMALL = 0.85F;
	/** (0.7.503) How often (ticks) a sworn player's own realm's rulers and guards about them come to know them, and how near (blocks). */
	static final int SWORN_TICKS = 40;
	static final double SWORN_RANGE = 24.0;
	/** (0.7.503) Hearts a sworn player starts with: with their ruler (a king or a lord), with a fellow guard. */
	static final int SWORN_HEARTS_RULER = 30, SWORN_HEARTS = 20;
	/** (0.7.503) Lines a sworn player's ruler and fellow guards open with ({@code story.jaymoskingdoms.sworn.<ruler|guard>.0-2}). */
	static final int SWORN_LINES = 3;

	/** Where a story set a player down. */
	record Landing(String start, BlockPos at, int realm, long time, String dimension) {
		static final Codec<Landing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("start").forGetter(Landing::start),
			BlockPos.CODEC.fieldOf("at").forGetter(Landing::at),
			Codec.INT.optionalFieldOf("realm", -1).forGetter(Landing::realm),
			Codec.LONG.fieldOf("time").forGetter(Landing::time),
			// (0.7.349) the world it was in, now that a start can set them down off the overworld
			Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(Landing::dimension)).apply(i, Landing::new));
	}

	public static final AttachmentType<Landing> LANDING = AttachmentRegistry.create(JaymosKingdoms.id("story_landing"),
		builder -> builder.persistent(Landing.CODEC).copyOnDeath());

	/** (0.7.503) On a sworn player: the rulers and guards of their realm who've come to know them (each once). Saved, kept through death. */
	static final AttachmentType<Set<UUID>> SWORN_KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("sworn_known"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath());

	private StoryTalk() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("story talk attachments: {} {}", LANDING, SWORN_KNOWN);
		ServerTickEvents.END_SERVER_TICK.register(StoryTalk::tick);
	}

	/** (0.7.503) Sworn players' own rulers and guards about them come to know them (once each: name, met, hearts). */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SWORN_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = (ServerLevel) player.level();
			Faction realm = swornRealm(level, player);
			if (realm == null) {
				continue;
			}
			for (Worker w : Workers.near(level, player.getBoundingBox().inflate(SWORN_RANGE), x -> serves(server, x.mob(), realm))) {
				LivingEntity mob = w.mob();
				Set<UUID> known = player.getAttachedOrElse(SWORN_KNOWN, Set.of());
				if (known.contains(mob.getUUID())) {
					// they know you already: what they think of you since is yours to keep
					continue;
				}
				Set<UUID> next = new HashSet<>(known);
				next.add(mob.getUUID());
				player.setAttached(SWORN_KNOWN, next);
				Introductions.learn(player, mob);
				McaCompat.met(mob, player);
				if (mob instanceof Villager v) {
					int want = Rulers.is(mob) ? SWORN_HEARTS_RULER : SWORN_HEARTS;
					int now = McaCompat.heartsOf(v, player);
					if (now < want) {
						McaCompat.hearts(v, player, want - now);
					}
				}
			}
		}
	}

	/**
	 * (0.7.503) The realm a Sworn start swore {@code player} to, while they're still one of its members and it stands;
	 * null otherwise (another start, or they've left it or it's fallen).
	 */
	static @Nullable Faction swornRealm(ServerLevel level, ServerPlayer player) {
		Landing l = player.getAttached(LANDING);
		if (l == null || l.realm() < 0 || !l.start().equals(LifeStory.Start.SWORN.name().toLowerCase(Locale.ROOT))) {
			return null;
		}
		Faction f = Factions.get(level.getServer(), l.realm());
		return f != null && !f.fallen() && f.member(player.getUUID()) != null ? f : null;
	}

	/** (0.7.503) {@code e} is a ruler (king, lord) or a guard of {@code realm}, or of one of its fiefs. */
	static boolean serves(MinecraftServer server, @Nullable Entity e, Faction realm) {
		if (!(e instanceof Worker w) || !e.isAlive()) {
			return false;
		}
		int role = w.role();
		if (role != Folk.KING && role != Folk.LORD && role != Folk.GUARD) {
			return false;
		}
		int id = w.factionId();
		if (id == realm.id()) {
			return true;
		}
		Faction f = id < 0 ? null : Factions.get(server, id);
		return f != null && f.overlord() == realm.id();
	}

	/** (0.7.503) {@code villager} is one of the rulers or guards of the realm {@code player} is sworn to. */
	public static boolean swornFellow(ServerPlayer player, LivingEntity villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		Faction realm = swornRealm(level, player);
		return realm != null && serves(level.getServer(), villager, realm);
	}

	/**
	 * (From LifeStory, once they're set down) {@code player}'s story started as {@code start} where they stand, about
	 * {@code realm} (a realm or camp id; -1: whoever holds the land they woke in, if anyone).
	 */
	static void landed(ServerPlayer player, LifeStory.Start start, int realm) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos at = player.blockPosition();
		if (realm < 0 && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
			Faction holds = Banners.owner(level.getServer(), ChunkPos.containing(at));
			realm = holds == null ? -1 : holds.id();
		}
		player.setAttached(LANDING, new Landing(start.name().toLowerCase(Locale.ROOT), at, realm, level.getGameTime(),
			level.dimension().identifier().toString()));
	}

	/**
	 * (MCA's menu opening on {@code villager}) What they open with, knowing you as they do: a shipmate's greeting by your
	 * berth, or a word on how your story started; null if they know nothing of you (MCA's own greeting). {@code told}:
	 * whether they've said their piece of your story already (each says it once).
	 */
	public static @Nullable Component opening(ServerPlayer player, LivingEntity villager, boolean told) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return null;
		}
		if (shipmate(level, player, villager)) {
			return Component.translatable("story.jaymoskingdoms.mate." + villager.getRandom().nextInt(MATE_LINES), berth(player, villager));
		}
		Faction sworn = swornRealm(level, player);
		if (sworn != null && serves(level.getServer(), villager, sworn)) {
			// (0.7.503) your own lord, or a fellow guard: every time, as one of theirs
			String who = Rulers.is(villager) ? "ruler" : "guard";
			return Component.translatable("story.jaymoskingdoms.sworn." + who + "." + villager.getRandom().nextInt(SWORN_LINES),
				player.getDisplayName(), Component.literal(sworn.name()));
		}
		if (told) {
			return null;
		}
		Landing l = player.getAttached(LANDING);
		if (l == null || level.getGameTime() - l.time() > FRESH || !about(level, l, villager)) {
			return null;
		}
		Faction realm = l.realm() < 0 ? null : Factions.get(level.getServer(), l.realm());
		Component realmName = realm == null ? Component.translatable("story.jaymoskingdoms.met.no_realm") : Component.literal(realm.name());
		return Component.translatable("story.jaymoskingdoms.met." + l.start() + "." + villager.getRandom().nextInt(MET_LINES), realmName);
	}

	/** Whether {@code villager} would know {@code player} as a stranger who isn't one: a shipmate, or one about their story. */
	public static boolean knowsOf(ServerPlayer player, LivingEntity villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		if (shipmate(level, player, villager) || swornFellow(player, villager)) {
			return true;
		}
		Landing l = player.getAttached(LANDING);
		return l != null && level.getGameTime() - l.time() <= FRESH && about(level, l, villager);
	}

	/** {@code villager} was about where {@code l} set its player down: near it, or in the land of the realm it was about. */
	private static boolean about(ServerLevel level, Landing l, LivingEntity villager) {
		if (!level.dimension().identifier().toString().equals(l.dimension())) {
			return false;
		}
		if (villager.blockPosition().closerThan(l.at(), NEAR)) {
			return true;
		}
		if (l.realm() < 0) {
			return false;
		}
		Faction holds = Banners.owner(level.getServer(), ChunkPos.containing(villager.blockPosition()));
		return holds != null && holds.id() == l.realm();
	}

	/**
	 * Whether {@code mob} is one of the crew of a whaler {@code player} signed on to (aboard her, or in port waiting
	 * on her next voyage).
	 */
	public static boolean shipmate(ServerLevel level, ServerPlayer player, LivingEntity mob) {
		if (!Crewmen.tagged(mob) || !mob.entityTags().contains(Crewmen.sideTag(Crew.Side.WHALER))) {
			return false;
		}
		Whalers.Voyage v = voyageOf(level, mob);
		return v != null && v.hands.contains(player.getUUID());
	}

	/**
	 * What {@code crewman} calls {@code player}: (0.7.270) by the berth they signed on in ({@link Whalers#berth}: harpooner,
	 * deckhand, powder monkey); a deckhand is a powder monkey to the sharp-tongued.
	 */
	public static Component berth(ServerPlayer player, LivingEntity crewman) {
		Whalers.Voyage v = voyageOf((ServerLevel) crewman.level(), crewman);
		String berth = v != null && v.hands.contains(player.getUUID()) ? Whalers.berth(v, player)
			: player.getScale() < SMALL ? Whalers.MONKEY : Whalers.DECKHAND;
		Temperaments.Temperament t = Temperaments.of(crewman);
		if (berth.equals(Whalers.DECKHAND) && (t == Temperaments.Temperament.SPITEFUL || t == Temperaments.Temperament.PROUD
			|| t == Temperaments.Temperament.HOTHEADED)) {
			berth = Whalers.MONKEY;
		}
		return Whalers.berthName(berth);
	}

	/** (0.7.270) The voyage of the whaler {@code mob} is crew of (aboard her, or in port waiting on her), or null. */
	static Whalers.@Nullable Voyage voyageOf(ServerLevel level, LivingEntity mob) {
		int realm = mob.getVehicle() instanceof Ship ship && ship.crew != null ? ship.crew.faction : Crewmen.faction(mob);
		return realm < 0 ? null : Whalers.voyage(level.getServer(), realm);
	}

	/** (From Crewmen, aboard) {@code player} is one of {@code mob}'s shipmates: they know each other's names. */
	static void knowEachOther(ServerPlayer player, LivingEntity mob) {
		Introductions.learn(player, mob);
		McaCompat.met(mob, player);
	}
}
