package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.62) Secret identities: Jaymo's Powers' costumes (stage 2). A player whose costume hides their face is tagged by
 * Powers ({@link #MASKED}{@code <key>}, and {@link #NAME}{@code <name>}); Kingdoms reads only the tags. To a
 * settlement's people a masked player is a stranger: the costume is someone of its own.
 * <ul>
 * <li>What a masked player does there goes on the costume's name, not theirs: its own reputation with each settlement,
 * its own crimes and wanted time ({@link Reputation}, {@link Theft} ask {@link #persona}). The guards hunt the costume
 * when they see it, not the player bare-faced; a member is a stranger in a mask, and greetings, MCA hearts and the
 * folk's approaches treat them as one.</li>
 * <li>Putting the mask on or taking it off where a settlement's people see it tells them who it is ({@link #link}): from
 * then on that settlement treats the costume as the player. Whatever good or ill name the costume made there is added
 * to the player's, its wanted time too. Word spreads, day by day, to the rest of that realm ({@link #spread}).</li>
 * <li>Powers give you away: each time the people of a settlement see a power used (a few seconds apart at least,
 * {@link #SIGHTING_GAP}), they remember who used it, the costume or the bare-faced player. Once they've seen both use
 * the same power {@link #SIGHTINGS_NEEDED} times, they put two and two together.</li>
 * <li>A paranoid king's guards stop masked strangers ({@link Approaches}' unmask topic): lift the mask for them, be
 * searched, or refuse and be sent out of the settlement ({@link #LEAVE_TICKS} to go, then the guards come).</li>
 * </ul>
 * {@code /kingdom identity} (ops) shows and clears what the settlements know.
 *
 * <p>(0.7.67) With Minecraft Comes Alive a masked player is a stranger to its villagers too: their memories of them are
 * kept under the costume's own stand-in id ({@link #standIn}; {@code mixin/mca/McaMemoriesMixin}), so the costume starts
 * at 0 hearts and the real hearts wait untouched for the bare face; and the player's names are taken out of what MCA
 * villagers say to them ({@link #withoutName}). (Chat, the tab list and every display name are Powers' business.)
 *
 * <p>(0.7.67) Tyrants and paranoid kings don't like vigilantes: a settlement under one ({@link #exiles}) that knows who
 * wears a costume exiles them. Their guards see them out of the city ({@link Retinue#exile}, where the realm has its own
 * ruler), and anyone still on its land after {@link #EXILE_GRACE} ticks is taken by force ({@link Theft#arrest}).
 */
public final class Identities {
	/** Powers' tags on a masked player: the identity's key; its name. Powers' tag for a power just used. */
	static final String MASKED = "jaymospowers_masked:", NAME = "jaymospowers_mask_name:", POWER = Oddities.PREFIX + Oddities.POWER;
	/** (0.7.102) Powers' tag on a player in a hood (a costume hood or peaked hood, head piece on, face showing): the design's key. */
	static final String HOODED = "jaymospowers_hooded:";

	// ---- tuning ----
	/** How often players are looked at (ticks). */
	static final int EVERY = 10;
	/** Seeing a mask go on or come off: how far (blocks, in sight), and how wide their gaze (cosine); closer than this they notice anyway. */
	static final double REVEAL_RANGE = 16.0, REVEAL_VIEW_COS = 0.2, REVEAL_CLOSE = 4.0;
	/** Seeing a power used: how far (blocks, in sight). */
	static final double POWER_RANGE = 24.0;
	/** Sightings of the same power, by the costume and by the bare-faced player, before a settlement works it out. */
	static final int SIGHTINGS_NEEDED = 3;
	/** One use counts as a sighting at most this often per player and power (ticks). */
	static final long SIGHTING_GAP = 600L;
	/** The chance each day that word of who wears a costume reaches each other settlement of the realm. */
	static final float SPREAD_CHANCE = 0.4F;
	/** A paranoid king's guards stop the same masked stranger at most this often per settlement (ticks: a day); how keen they are. */
	static final long CHALLENGE_GAP = 24000L;
	static final int CHALLENGE_WEIGHT = 20;
	/** Searched and let through: left be this long (ticks: a day). */
	static final long PASS_TICKS = 24000L;
	/** Refusing to unmask: the costume's name suffers this much; this long to be out of the settlement (ticks), or they come for it this long. */
	static final int REFUSE_REP = -8;
	static final long LEAVE_TICKS = 20L * 30, REFUSE_ARREST_TICKS = 2400L;
	/** A clean search of a masked stranger: what it does for the costume's name. */
	static final int SEARCH_CLEAN_REP = 1;
	/** Lines a witness has, per way of finding out. */
	static final int LINES = 3;
	/** (0.7.67) An exiled vigilante: how long the guards' ban runs from each time they're found inside; how long they may linger on the land; how long they're then hunted (ticks). */
	static final long EXILE_BAN_TICKS = 24000L * 3, EXILE_GRACE = 20L * 60, EXILE_ARREST_TICKS = 2400L;
	/** (0.7.67) What MCA villagers call a masked player whose costume has no name. */
	static final String UNNAMED = "Masked Stranger";
	/** (0.7.102) A hooded player is seen this share as far (witnesses, trespass, deeds). */
	static final double HOOD_SIGHT = 0.75;
	/** (0.7.102) A hood is a fragile mask: a witness this close (blocks), facing you, in the light, sees the face under it. */
	static final double HOOD_KNOWN_RANGE = 6.0;

	/** One costume's own name in the world: its name, its reputation, wanted time and who each settlement knows wears it (faction id keys). */
	record Identity(String name, Map<String, Integer> rep, Map<String, Long> wanted, Map<String, String> links) {
		static final Codec<Identity> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("name", "").forGetter(Identity::name),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("rep", Map.of()).forGetter(Identity::rep),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("wanted", Map.of()).forGetter(Identity::wanted),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("links", Map.of()).forGetter(Identity::links))
			.apply(i, Identity::new));

		static final Identity EMPTY = new Identity("", Map.of(), Map.of(), Map.of());

		Identity withName(String name) {
			return new Identity(name, rep, wanted, links);
		}

		Identity withRep(Map<String, Integer> rep) {
			return new Identity(name, Map.copyOf(rep), wanted, links);
		}

		Identity withWanted(Map<String, Long> wanted) {
			return new Identity(name, rep, Map.copyOf(wanted), links);
		}

		Identity withLinks(Map<String, String> links) {
			return new Identity(name, rep, wanted, Map.copyOf(links));
		}
	}

	/** Every costume identity the world knows of (key → identity), kept on the Overworld. */
	static final AttachmentType<Map<String, Identity>> ALL = AttachmentRegistry.create(JaymosKingdoms.id("identities"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Identity.CODEC)));
	/**
	 * Per player: the powers each settlement has seen them use (sightings by power); keyed by faction id bare-faced, or
	 * {@code <identity>@<faction id>} in a costume. Saved, kept through death.
	 */
	static final AttachmentType<Map<String, Map<String, Integer>>> SIGHTINGS = AttachmentRegistry.create(JaymosKingdoms.id("power_sightings"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT))).copyOnDeath());

	/** (0.7.67) On a player, synced: the identity they're masked as (absent bare-faced), for MCA on both sides. */
	static final AttachmentType<String> MASKED_AS = AttachmentRegistry.create(JaymosKingdoms.id("masked_as"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/** Player → the identity they were last seen fully masked as, and (0.7.102) hooded as with no mask ("" none). */
	private static final Map<UUID, String> WAS_MASK = new HashMap<>(), WAS_HOOD = new HashMap<>();
	/** Player/power → when a use of it last counted. */
	private static final Map<String, Long> LAST_SIGHTING = new HashMap<>();
	/** Player/faction → when its guards last stopped them masked (or let them through). */
	private static final Map<String, Long> CHALLENGED = new HashMap<>();
	/** Player → {faction id, the tick they must be gone by}: refused to unmask. */
	private static final Map<UUID, long[]> SENT_OFF = new HashMap<>();
	/** (0.7.67) Player → {faction id, the tick they were found there}: an exiled vigilante on a settlement's land. */
	private static final Map<UUID, long[]> EXILED_IN = new HashMap<>();
	private static long lastDay = Long.MIN_VALUE;

	private Identities() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Identities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WAS_MASK.remove(handler.player.getUUID());
			WAS_HOOD.remove(handler.player.getUUID());
			SENT_OFF.remove(handler.player.getUUID());
			EXILED_IN.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAS_MASK.clear();
			WAS_HOOD.clear();
			LAST_SIGHTING.clear();
			CHALLENGED.clear();
			SENT_OFF.clear();
			EXILED_IN.clear();
			lastDay = Long.MIN_VALUE;
		});
	}

	// ---- who is under the mask ---------------------------------------------------------------------------------------

	/**
	 * The identity {@code e} goes by right now, or null if their face shows: the costume's they are masked as (Powers'
	 * tag), else (0.7.102) the hood's they are hooded as.
	 */
	static @Nullable String key(Entity e) {
		String mask = maskKey(e);
		return mask != null ? mask : hoodKey(e);
	}

	/**
	 * (0.7.102) The identity {@code e} is fully masked as (Powers' tag), or null. (0.7.125) A bandit mask or bandana is a
	 * mask too ({@link Bandits}): Powers tags it with the same key, and without Powers it's seen to here.
	 */
	static @Nullable String maskKey(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(MASKED)) {
				return tag.substring(MASKED.length());
			}
		}
		return Bandits.wears(e) ? Bandits.key(e) : null;
	}

	/** (0.7.102) The identity {@code e} is hooded as (Powers' tag), or null. */
	static @Nullable String hoodKey(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(HOODED)) {
				return tag.substring(HOODED.length());
			}
		}
		return null;
	}

	/** (0.7.102) {@code e} wears a hood and no full mask. */
	static boolean hooded(Entity e) {
		return maskKey(e) == null && hoodKey(e) != null;
	}

	/** (0.7.102) {@code e} wears a full mask (a hood doesn't count: MCA's folk still know a hooded friend). */
	static boolean masked(Entity e) {
		return maskKey(e) != null;
	}

	/** (0.7.102) How far {@code e} is seen, as a share of the usual: {@link #HOOD_SIGHT} under a hood, 1 otherwise. */
	static double hoodSight(Entity e) {
		return hooded(e) ? HOOD_SIGHT : 1.0;
	}

	private static String tagName(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(NAME)) {
				return tag.substring(NAME.length());
			}
		}
		return "";
	}

	private static Component nameOf(String name) {
		return name.isEmpty() ? Component.translatable("identity.jaymoskingdoms.unnamed") : Component.literal(name);
	}

	/** (0.7.102) What {@code e} goes by under {@link #key}: the costume's name, "a hooded figure" for a nameless hood. */
	private static Component nameFor(Entity e) {
		if (Bandits.is(maskKey(e))) {
			// (0.7.125) a bandit, or a pirate by the water
			return Bandits.alias(e);
		}
		String name = tagName(e);
		return name.isEmpty() && hooded(e) ? Component.translatable("identity.jaymoskingdoms.hooded") : nameOf(name);
	}

	/**
	 * (0.7.92) {@code player}'s own name, as the folk know it: the one they picked in MCA's creator, never their username
	 * (unless that's all there is).
	 */
	static Component trueName(ServerPlayer player) {
		return McaCompat.LOADED ? Component.literal(McaBridge.name(player)) : player.getName();
	}

	/** What {@code player} goes by to anyone looking: the costume's name while masked, theirs ({@link #trueName}) otherwise. */
	static Component shownName(ServerPlayer player) {
		return key(player) != null ? nameFor(player) : trueName(player);
	}

	/** What {@code faction}'s people call {@code player}: their own name, unless they're a stranger in a mask there. */
	static Component nameTo(ServerPlayer player, int faction) {
		return persona(player, faction) != null ? nameFor(player) : trueName(player);
	}

	/**
	 * The identity {@code faction}'s people take {@code player} for: the costume's key while they're masked and that
	 * settlement doesn't know who wears it; null when they see (or know) the player.
	 */
	static @Nullable String persona(ServerPlayer player, int faction) {
		String key = key(player);
		if (key == null) {
			return null;
		}
		Identity id = get(player.level().getServer(), key);
		return id != null && player.getStringUUID().equals(id.links().get(Integer.toString(faction))) ? null : key;
	}

	/** {@code player} is a masked stranger to {@code faction}'s people. */
	static boolean hidden(ServerPlayer player, int faction) {
		return persona(player, faction) != null;
	}

	/**
	 * {@code player} is a masked stranger to {@code who} (whose settlement is where they live, or none: then anyone masked is).
	 * (0.7.102) Only a full mask: a hood doesn't make MCA's villagers forget you.
	 */
	static boolean hiddenFrom(ServerPlayer player, LivingEntity who) {
		if (!masked(player)) {
			return false;
		}
		Faction f = Aliens.factionOf(player.level().getServer(), who);
		return f == null || hidden(player, f.id());
	}

	/** (0.7.67) The identity {@code player} is masked as, on either side (the synced mirror of Powers' tag), or null. */
	public static @Nullable String maskedAs(Player player) {
		return player.getAttached(MASKED_AS);
	}

	/** (0.7.67) The id a costume goes by where a player's UUID would be (MCA's memories of it). */
	public static UUID standIn(String key) {
		return UUID.nameUUIDFromBytes(("jaymoskingdoms:identity/" + key).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * (0.7.67) {@code text} said to a masked {@code player}, with their name ({@code getName}, and any of {@code names},
	 * e.g. MCA's name for them) put back to the costume's. The same component if nothing changed, or they aren't masked.
	 */
	public static Component withoutName(ServerPlayer player, Component text, String... names) {
		if (!masked(player)) {
			return text;
		}
		String mask = Bandits.is(maskKey(player)) ? Bandits.alias(player).getString() : tagName(player).isEmpty() ? UNNAMED : tagName(player);
		Set<String> real = new HashSet<>();
		real.add(player.getName().getString());
		for (String n : names) {
			if (n != null && !n.isBlank()) {
				real.add(n);
			}
		}
		real.remove(mask);
		return real.isEmpty() ? text : scrub(text, real, mask);
	}

	private static Component scrub(Component c, Set<String> real, String mask) {
		boolean changed = false;
		ComponentContents contents = c.getContents();
		if (contents instanceof TranslatableContents t) {
			Object[] args = t.getArgs().clone();
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof String s) {
					String r = scrub(s, real, mask);
					if (!r.equals(s)) {
						args[i] = r;
						changed = true;
					}
				} else if (args[i] instanceof Component a) {
					Component b = scrub(a, real, mask);
					if (b != a) {
						args[i] = b;
						changed = true;
					}
				}
			}
			if (changed) {
				contents = new TranslatableContents(t.getKey(), t.getFallback(), args);
			}
		} else if (contents instanceof PlainTextContents p) {
			String r = scrub(p.text(), real, mask);
			if (!r.equals(p.text())) {
				contents = PlainTextContents.create(r);
				changed = true;
			}
		}
		List<Component> siblings = new ArrayList<>();
		for (Component s : c.getSiblings()) {
			Component r = scrub(s, real, mask);
			changed |= r != s;
			siblings.add(r);
		}
		if (!changed) {
			return c;
		}
		MutableComponent out = MutableComponent.create(contents).setStyle(c.getStyle());
		siblings.forEach(out::append);
		return out;
	}

	private static String scrub(String s, Set<String> real, String mask) {
		for (String r : real) {
			if (s.contains(r)) {
				s = s.replaceAll("(?<![\\p{L}\\p{N}_])" + Pattern.quote(r) + "(?![\\p{L}\\p{N}_])", Matcher.quoteReplacement(mask));
			}
		}
		return s;
	}

	/** (Approaches) Who a crime is held against: the player, or (a stable stand-in id for) the costume they hid behind. */
	static UUID suspectId(ServerPlayer player, int faction) {
		String persona = persona(player, faction);
		return persona == null ? player.getUUID() : standIn(persona);
	}

	// ---- the store -----------------------------------------------------------------------------------------------------

	static Map<String, Identity> all(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(ALL, Map.of());
	}

	static @Nullable Identity get(MinecraftServer server, String key) {
		return all(server).get(key);
	}

	private static Identity getOrNew(MinecraftServer server, String key) {
		Identity id = get(server, key);
		return id == null ? Identity.EMPTY : id;
	}

	private static void put(MinecraftServer server, String key, @Nullable Identity id) {
		Map<String, Identity> map = new HashMap<>(all(server));
		if (id == null) {
			map.remove(key);
		} else {
			map.put(key, id);
		}
		server.overworld().setAttached(ALL, Map.copyOf(map));
	}

	// ---- the costume's own name and crimes ---------------------------------------------------------------------------

	/** (Reputation) What {@code faction} thinks of identity {@code key}. */
	static int reputation(MinecraftServer server, String key, int faction) {
		Identity id = get(server, key);
		return id == null ? 0 : id.rep().getOrDefault(Integer.toString(faction), 0);
	}

	/** (Reputation) The costume {@code player} wears as {@code key} earns {@code by} with {@code f} (tempers already taken in). */
	static void changeReputation(ServerPlayer player, String key, Faction f, int by) {
		MinecraftServer server = player.level().getServer();
		Identity id = getOrNew(server, key);
		String fid = Integer.toString(f.id());
		int before = id.rep().getOrDefault(fid, 0);
		int after = Mth.clamp(before + by, Reputation.MIN, Reputation.MAX);
		Map<String, Integer> rep = new HashMap<>(id.rep());
		rep.put(fid, after);
		put(server, key, id.withRep(rep));
		Component name = nameFor(player);
		if (before > Reputation.HOSTILE && after <= Reputation.HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.identity.hostile", f.name(), name).withStyle(ChatFormatting.RED));
		} else if (before <= Reputation.HOSTILE && after > Reputation.HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.identity.forgiven", f.name(), name).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** (Theft) The tick {@code faction}'s guards stop hunting identity {@code key} (0: they aren't). */
	static long wantedUntil(MinecraftServer server, String key, int faction) {
		Identity id = get(server, key);
		Long until = id == null ? null : id.wanted().get(Integer.toString(faction));
		return until == null ? 0L : until;
	}

	/** (Theft) Every settlement hunting identity {@code key}, and till when. */
	static Map<String, Long> wantedBy(MinecraftServer server, String key) {
		Identity id = get(server, key);
		return id == null ? Map.of() : id.wanted();
	}

	/** (Theft) {@code faction} hunts identity {@code key} until at least {@code until}. */
	static void markWanted(MinecraftServer server, String key, int faction, long until) {
		Identity id = getOrNew(server, key);
		Map<String, Long> wanted = new HashMap<>(id.wanted());
		wanted.merge(Integer.toString(faction), until, Math::max);
		put(server, key, id.withWanted(wanted));
	}

	// ---- found out ---------------------------------------------------------------------------------------------------

	/**
	 * {@code f}'s people know now that {@code player} wears identity {@code key} ({@code how}: unmasked, masked, powers,
	 * lifted, word, command): the costume's good or ill name there is added to the player's, its wanted time too, and from
	 * now on that settlement treats the costume as them. {@code witness}, if any, says so (to the player). False if they knew.
	 */
	static boolean link(ServerLevel level, ServerPlayer player, String key, Faction f, @Nullable LivingEntity witness, String how) {
		MinecraftServer server = level.getServer();
		Identity id = getOrNew(server, key);
		String fid = Integer.toString(f.id());
		String me = player.getStringUUID();
		if (me.equals(id.links().get(fid))) {
			return false;
		}
		Map<String, String> links = new HashMap<>(id.links());
		links.put(fid, me);
		put(server, key, id.withLinks(links));
		// (what the costume did there is theirs now)
		int rep = id.rep().getOrDefault(fid, 0);
		if (rep != 0) {
			Map<String, Integer> mine = new HashMap<>(player.getAttachedOrElse(Reputation.REPUTATION, Map.of()));
			mine.put(fid, Mth.clamp(mine.getOrDefault(fid, 0) + rep, Reputation.MIN, Reputation.MAX));
			player.setAttached(Reputation.REPUTATION, Map.copyOf(mine));
		}
		Long until = id.wanted().get(fid);
		if (until != null && until > level.getGameTime()) {
			Map<String, Long> wanted = new HashMap<>(player.getAttachedOrElse(Theft.WANTED, Map.of()));
			wanted.merge(fid, until, Math::max);
			player.setAttached(Theft.WANTED, Map.copyOf(wanted));
		}
		Component name = Bandits.is(key) ? Bandits.alias(player) : id.name().isEmpty() ? nameFor(player) : nameOf(id.name());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.identity.known." + how, f.name(), name)
			.withStyle(how.equals("word") ? ChatFormatting.GRAY : ChatFormatting.GOLD, ChatFormatting.ITALIC));
		if (witness != null) {
			Speech.tell(level, witness, player, Component.translatable("talk.jaymoskingdoms.identity." + how + "." + level.getRandom().nextInt(LINES),
				name, trueName(player)), Speech.YELLOW);
		}
		JaymosKingdoms.LOGGER.debug("{} knows {} is {} ({})", f.name(), player.getName().getString(), name.getString(), how);
		if (exiles(server, f)) {
			// (0.7.67) a tyrant or a paranoid king won't have a vigilante in his realm
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.identity.exiled", f.name()).withStyle(ChatFormatting.DARK_RED));
			EXILED_IN.remove(player.getUUID());
		}
		return true;
	}

	/**
	 * (0.7.102) A hood is a fragile mask. A crime of {@code player}'s was seen by {@code seen} (nearest first): if one of
	 * {@code f}'s people is within {@link #HOOD_KNOWN_RANGE}, facing them (the {@link #REVEAL_VIEW_COS} gaze), and they
	 * are not in the dark, that witness sees the face under the hood and the settlement knows who it was ({@link #link},
	 * "recognised") before the crime is put to anyone's name. Seen from further, from behind or in the dark, it stays
	 * on the hooded figure. {@code turned}, if any, is a witness who has just turned on them (a pickpocket's mark): it
	 * counts as facing them. Golems can't tell a face, let alone say whose.
	 */
	static void recognise(ServerLevel level, ServerPlayer player, Faction f, List<LivingEntity> seen, @Nullable LivingEntity turned) {
		String key = hoodKey(player);
		if (key == null || masked(player) || seen.isEmpty() || persona(player, f.id()) == null || Heritage.dark(player)) {
			return;
		}
		for (LivingEntity e : seen) {
			if (e instanceof IronGolem || e.distanceTo(player) > HOOD_KNOWN_RANGE) {
				continue;
			}
			Vec3 look = e.getViewVector(1.0F);
			Vec3 to = player.getEyePosition().subtract(e.getEyePosition()).normalize();
			if (e == turned || look.dot(to) >= REVEAL_VIEW_COS) {
				link(level, player, key, f, e, "recognised");
				return;
			}
		}
	}

	/** {@code faction}'s people know {@code player} wears identity {@code key}. */
	static boolean linked(MinecraftServer server, String key, int faction, ServerPlayer player) {
		Identity id = get(server, key);
		return id != null && player.getStringUUID().equals(id.links().get(Integer.toString(faction)));
	}

	// ---- (Approaches) a paranoid king's guards and masked strangers -----------------------------------------------

	/** {@code f}'s ruler's temper (its liege's, where it has none of its own), or null. */
	private static Tempers.@Nullable Temper temper(MinecraftServer server, Faction f) {
		Tempers.Temper t = Tempers.of(f);
		return t != null ? t : Tempers.of(Wars.top(server, f));
	}

	/** Whether {@code f}'s ruler (or its liege) is paranoid. */
	static boolean paranoid(MinecraftServer server, Faction f) {
		return temper(server, f) == Tempers.Temper.PARANOID;
	}

	/** (0.7.67) Whether {@code f} exiles the vigilantes it knows: under a tyrant or a paranoid king. */
	static boolean exiles(MinecraftServer server, Faction f) {
		Tempers.Temper t = temper(server, f);
		return t == Tempers.Temper.TYRANT || t == Tempers.Temper.PARANOID;
	}

	/** (0.7.67) {@code f}'s people know {@code player} wears some costume. */
	static boolean vigilante(MinecraftServer server, ServerPlayer player, Faction f) {
		String fid = Integer.toString(f.id());
		String me = player.getStringUUID();
		for (Identity id : all(server).values()) {
			if (me.equals(id.links().get(fid))) {
				return true;
			}
		}
		return false;
	}

	/** A guard of {@code f} would stop {@code player}: masked, a stranger there, a paranoid king's realm, not stopped lately. */
	static boolean challenges(MinecraftServer server, ServerPlayer player, Faction f, long now) {
		if (!hidden(player, f.id()) || !paranoid(server, f)) {
			return false;
		}
		Long last = CHALLENGED.get(player.getUUID() + "/" + f.id());
		return last == null || now - last >= CHALLENGE_GAP;
	}

	/** A guard of {@code f} has stopped {@code player} (or let them through): not again for a while. */
	static void challenged(ServerPlayer player, Faction f, long now, boolean passed) {
		CHALLENGED.put(player.getUUID() + "/" + f.id(), passed ? now + PASS_TICKS - CHALLENGE_GAP : now);
	}

	/** {@code player} lifts their mask for {@code guard}: {@code f} knows who it is now. */
	static void lifted(ServerLevel level, ServerPlayer player, Faction f) {
		String key = key(player);
		if (key != null) {
			link(level, player, key, f, null, "lifted");
		}
	}

	/** {@code player} wouldn't unmask: out of {@code f}'s settlement soon, or its guards come for the costume. */
	static void sendOff(ServerLevel level, ServerPlayer player, Faction f) {
		SENT_OFF.put(player.getUUID(), new long[] {f.id(), level.getGameTime() + LEAVE_TICKS});
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.identity.sent_off", f.name(), LEAVE_TICKS / 20).withStyle(ChatFormatting.RED));
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		long day = Deeds.today(server);
		boolean newDay = lastDay != Long.MIN_VALUE && day != lastDay;
		lastDay = day;
		if (newDay) {
			spread(server, level);
			forgetOld(server, now);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String cur = key(player);
			String mask = maskKey(player);
			// (0.7.101: a hood worn under no mask)
			String hood = mask == null ? hoodKey(player) : null;
			String wasMask = WAS_MASK.put(player.getUUID(), mask == null ? "" : mask);
			String wasHood = WAS_HOOD.put(player.getUUID(), hood == null ? "" : hood);
			if (cur != null) {
				noteName(server, cur, tagName(player));
			}
			// (0.7.101: MCA's villagers forget you only behind a full mask, not a hood)
			if (!java.util.Objects.equals(mask, player.getAttached(MASKED_AS))) {
				if (mask == null) {
					player.removeAttached(MASKED_AS);
				} else {
					player.setAttached(MASKED_AS, mask);
				}
			}
			if (player.level() != level || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			// (0.7.102) masks and hoods each on their own, so going from one to the other in view still gives a mask away
			if (wasMask != null) {
				if (!wasMask.isEmpty() && !wasMask.equals(mask)) {
					revealed(level, player, wasMask, "unmasked");
				}
				if (mask != null && !mask.equals(wasMask)) {
					revealed(level, player, mask, "masked");
				}
			}
			// a hood lowered in view gives you away like a mask taken off; pulling one up gives nothing away (nor does
			// a mask going on over it: the face is hidden)
			if (wasHood != null && !wasHood.isEmpty() && !wasHood.equals(hood) && mask == null) {
				revealed(level, player, wasHood, "lowered");
			}
			powers(level, player, cur, now);
			sentOff(level, player, now);
			exiled(level, player, now);
		}
	}

	/** (0.7.67) A known vigilante on the land of a tyrant's or a paranoid king's settlement: seen out, or taken. */
	private static void exiled(ServerLevel level, ServerPlayer player, long now) {
		MinecraftServer server = level.getServer();
		Faction here = player.isCreative() ? null : Theft.lawAt(level, player.blockPosition());
		if (here == null || !exiles(server, here) || !vigilante(server, player, here)) {
			EXILED_IN.remove(player.getUUID());
			return;
		}
		long[] in = EXILED_IN.get(player.getUUID());
		if (in == null || in[0] != here.id()) {
			EXILED_IN.put(player.getUUID(), new long[] {here.id(), now});
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.identity.exiled.inside", here.name(), EXILE_GRACE / 20)
				.withStyle(ChatFormatting.DARK_RED));
			Map<Faction, LivingEntity> seen = witnesses(level, player, REVEAL_RANGE, false);
			for (Map.Entry<Faction, LivingEntity> e : seen.entrySet()) {
				if (e.getKey().id() == here.id()) {
					Speech.say(level, e.getValue(), Component.translatable("talk.jaymoskingdoms.identity.exiled." + level.getRandom().nextInt(LINES),
						trueName(player)), Speech.RED, 24.0);
				}
			}
			if (Tempers.of(here) != null) {
				Retinue.exile(level, here, player, EXILE_BAN_TICKS);
			}
			return;
		}
		if (now - in[1] >= EXILE_GRACE) {
			in[1] = now;
			Theft.arrest(player, level, here, EXILE_ARREST_TICKS);
		}
	}

	/** Keeps the identity's name as it was last worn. */
	private static void noteName(MinecraftServer server, String key, String name) {
		Identity id = get(server, key);
		if (id == null || !id.name().equals(name)) {
			put(server, key, (id == null ? Identity.EMPTY : id).withName(name));
		}
	}

	/** The mask went on or came off: every settlement whose people saw it (and didn't know) knows now. */
	private static void revealed(ServerLevel level, ServerPlayer player, String key, String how) {
		for (Map.Entry<Faction, LivingEntity> e : witnesses(level, player, REVEAL_RANGE, true).entrySet()) {
			if (!linked(level.getServer(), key, e.getKey().id(), player)) {
				link(level, player, key, e.getKey(), e.getValue(), how);
			}
		}
	}

	/** A settlement's people (villagers, folk) who can see {@code player}, the nearest of each settlement. */
	private static Map<Faction, LivingEntity> witnesses(ServerLevel level, ServerPlayer player, double range, boolean looking) {
		MinecraftServer server = level.getServer();
		List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
			e -> e.isAlive() && !e.isSleeping() && (e instanceof Villager || e instanceof Folk) && e.distanceTo(player) <= range));
		near.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		Map<Faction, LivingEntity> out = new LinkedHashMap<>();
		for (LivingEntity e : near) {
			if (e instanceof Folk folk && Assault.attacker(folk) || !e.hasLineOfSight(player)) {
				continue;
			}
			if (looking && e.distanceTo(player) > REVEAL_CLOSE) {
				Vec3 look = e.getViewVector(1.0F);
				Vec3 to = player.getEyePosition().subtract(e.getEyePosition()).normalize();
				if (look.dot(to) < REVEAL_VIEW_COS) {
					continue;
				}
			}
			Faction f = Aliens.factionOf(server, e);
			if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN || out.keySet().stream().anyMatch(x -> x.id() == f.id())) {
				continue;
			}
			out.put(f, e);
		}
		return out;
	}

	/** A power in use where people see it: they remember who used it, and may work out who wears the costume. */
	private static void powers(ServerLevel level, ServerPlayer player, @Nullable String mask, long now) {
		for (String tag : List.copyOf(player.entityTags())) {
			if (!tag.startsWith(POWER)) {
				continue;
			}
			String power = tag.substring(POWER.length());
			String at = player.getUUID() + "/" + power;
			Long last = LAST_SIGHTING.get(at);
			if (last != null && now - last < SIGHTING_GAP) {
				continue;
			}
			Map<Faction, LivingEntity> seen = witnesses(level, player, POWER_RANGE, false);
			if (seen.isEmpty()) {
				continue;
			}
			LAST_SIGHTING.put(at, now);
			for (Map.Entry<Faction, LivingEntity> e : seen.entrySet()) {
				sighted(level, player, mask, e.getKey(), e.getValue(), power);
			}
		}
	}

	private static void sighted(ServerLevel level, ServerPlayer player, @Nullable String mask, Faction f, LivingEntity witness, String power) {
		MinecraftServer server = level.getServer();
		String fid = Integer.toString(f.id());
		Map<String, Map<String, Integer>> all = new HashMap<>(player.getAttachedOrElse(SIGHTINGS, Map.of()));
		if (mask != null) {
			if (linked(server, mask, f.id(), player)) {
				return;
			}
			int n = bump(all, mask + "@" + fid, power);
			player.setAttached(SIGHTINGS, Map.copyOf(all));
			if (n >= SIGHTINGS_NEEDED && all.getOrDefault(fid, Map.of()).getOrDefault(power, 0) >= SIGHTINGS_NEEDED) {
				link(level, player, mask, f, witness, "powers");
			}
			return;
		}
		int n = bump(all, fid, power);
		player.setAttached(SIGHTINGS, Map.copyOf(all));
		if (n < SIGHTINGS_NEEDED) {
			return;
		}
		String suffix = "@" + fid;
		for (Map.Entry<String, Map<String, Integer>> e : all.entrySet()) {
			if (e.getKey().endsWith(suffix) && e.getValue().getOrDefault(power, 0) >= SIGHTINGS_NEEDED) {
				String key = e.getKey().substring(0, e.getKey().length() - suffix.length());
				if (!linked(server, key, f.id(), player)) {
					link(level, player, key, f, witness, "powers");
				}
			}
		}
	}

	private static int bump(Map<String, Map<String, Integer>> all, String slot, String power) {
		Map<String, Integer> counts = new HashMap<>(all.getOrDefault(slot, Map.of()));
		int n = counts.getOrDefault(power, 0) + 1;
		counts.put(power, n);
		all.put(slot, Map.copyOf(counts));
		return n;
	}

	/** Refused to unmask: still masked in that settlement when their time's up, its guards come for the costume. */
	private static void sentOff(ServerLevel level, ServerPlayer player, long now) {
		long[] s = SENT_OFF.get(player.getUUID());
		if (s == null || now < s[1]) {
			return;
		}
		SENT_OFF.remove(player.getUUID());
		Faction f = Factions.get(level.getServer(), (int)s[0]);
		Faction here = Theft.lawAt(level, player.blockPosition());
		if (f != null && here != null && here.id() == f.id() && hidden(player, f.id())) {
			Theft.arrest(player, level, f, REFUSE_ARREST_TICKS);
		}
	}

	/** A new day: word of who wears a costume reaches, maybe, the rest of each realm that knows (online players only). */
	private static void spread(MinecraftServer server, ServerLevel level) {
		List<Faction> settlements = Factions.all(server).stream().filter(f -> !f.fallen() && f.type() != FactionType.BARBARIAN).toList();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String me = player.getStringUUID();
			for (Map.Entry<String, Identity> e : all(server).entrySet()) {
				for (Map.Entry<String, String> link : e.getValue().links().entrySet()) {
					Faction from = me.equals(link.getValue()) ? Factions.get(server, Integer.parseInt(link.getKey())) : null;
					if (from == null || from.fallen()) {
						continue;
					}
					int realm = Wars.top(server, from).id();
					for (Faction to : settlements) {
						if (to.id() != from.id() && Wars.top(server, to).id() == realm && !linked(server, e.getKey(), to.id(), player)
							&& level.getRandom().nextFloat() < SPREAD_CHANCE) {
							link(level, player, e.getKey(), to, null, "word");
						}
					}
				}
			}
		}
	}

	/** Wanted times long run out are dropped. */
	private static void forgetOld(MinecraftServer server, long now) {
		for (Map.Entry<String, Identity> e : all(server).entrySet()) {
			Map<String, Long> still = new HashMap<>(e.getValue().wanted());
			if (still.values().removeIf(t -> t <= now)) {
				put(server, e.getKey(), e.getValue().withWanted(still));
			}
		}
		CHALLENGED.values().removeIf(t -> now - t > CHALLENGE_GAP + PASS_TICKS);
		LAST_SIGHTING.values().removeIf(t -> now - t > SIGHTING_GAP);
	}

	// ---- /kingdom identity (ops) ---------------------------------------------------------------------------------

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("identity").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(Identities::info)
			.then(Commands.literal("list").executes(Identities::list))
			.then(Commands.literal("link").then(Commands.argument("name", StringArgumentType.word()).executes(c -> link(c, true))))
			.then(Commands.literal("unlink").executes(c -> unlinkAll(c))
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> link(c, false))))
			.then(Commands.literal("forget").executes(Identities::forget));
	}

	private static Component word(Faction f) {
		return Component.literal(f.name());
	}

	/** What's known of the costume you wear (or that you wear none). */
	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		String key = key(player);
		if (key == null) {
			c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.bare").withStyle(ChatFormatting.GRAY), false);
		} else {
			Identity id = getOrNew(server, key);
			c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.masked", nameOf(id.name()), key).withStyle(ChatFormatting.GOLD), false);
			long now = server.overworld().getGameTime();
			for (Faction f : Factions.all(server)) {
				String fid = Integer.toString(f.id());
				Integer rep = id.rep().get(fid);
				Long until = id.wanted().get(fid);
				String who = id.links().get(fid);
				if (rep == null && until == null && who == null) {
					continue;
				}
				ServerPlayer knows = who == null ? null : server.getPlayerList().getPlayer(UUID.fromString(who));
				Component known = who == null ? Component.translatable("command.jaymoskingdoms.identity.unknown")
					: knows != null ? knows.getName() : Component.literal(who);
				long left = until == null ? 0L : Math.max(0L, until - now) / 20L;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.realm", word(f), rep == null ? 0 : rep, left, known), false);
			}
		}
		Map<String, Map<String, Integer>> seen = player.getAttachedOrElse(SIGHTINGS, Map.of());
		if (!seen.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.sightings", seen.toString()).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		Map<String, Identity> all = all(c.getSource().getServer());
		if (all.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.none"), false);
			return 0;
		}
		for (Map.Entry<String, Identity> e : all.entrySet()) {
			Identity id = e.getValue();
			c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.entry", nameOf(id.name()), e.getKey(), id.rep().size(),
				id.links().size()), false);
		}
		return all.size();
	}

	/** Makes {@code name}'s people know (or forget) that you wear the costume you have on. */
	private static int link(CommandContext<CommandSourceStack> c, boolean link) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		String key = key(player);
		Faction f = Factions.byName(server, StringArgumentType.getString(c, "name"));
		if (key == null || f == null) {
			c.getSource().sendFailure(Component.translatable(key == null ? "command.jaymoskingdoms.identity.bare" : "command.jaymoskingdoms.identity.no_realm"));
			return 0;
		}
		if (link) {
			link(Factions.level(server), player, key, f, null, "command");
		} else {
			Identity id = getOrNew(server, key);
			Map<String, String> links = new HashMap<>(id.links());
			links.remove(Integer.toString(f.id()));
			put(server, key, id.withLinks(links));
			c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.unlinked", word(f)), false);
		}
		return 1;
	}

	private static int unlinkAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		String key = key(player);
		if (key == null) {
			c.getSource().sendFailure(Component.translatable("command.jaymoskingdoms.identity.bare"));
			return 0;
		}
		put(server, key, getOrNew(server, key).withLinks(Map.of()));
		c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.unlinked_all"), false);
		return 1;
	}

	/** Wipes the costume you wear from the world's memory (its name, crimes, who wears it), and your power sightings. */
	private static int forget(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		String key = key(player);
		if (key != null) {
			put(c.getSource().getServer(), key, null);
		}
		player.removeAttached(SIGHTINGS);
		c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.identity.forgotten"), false);
		return 1;
	}
}
