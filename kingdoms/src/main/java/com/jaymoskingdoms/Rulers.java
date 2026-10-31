package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.138) Andy (2026-10-21): "Guards and kings dont seem to be integrated with the mca villagers, they dont have lineages
 * or heritages ... I've even waited to see a successor installed and it was never anyone from the kingdom/village." He
 * chose for them to stay real MCA villagers.
 * <p>
 * With Minecraft Comes Alive, a realm's ruler (a kingdom's king, a fiefdom's lord) is one of its own villagers: they keep
 * their name, face, family, hearts and heritage, and wear the crown ({@link #fit}). Its first ruler is crowned from the
 * townsfolk when it's founded; when a ruler dies, the heir is of their family first (their children, brothers and
 * sisters by MCA's tree), then their widow or widower, then anyone grown in the town ({@link #house}). A realm with no
 * villagers at all has one move in to take the throne. A folk ruler still standing from before steps down for one of
 * the townsfolk ({@link #retire}). Without MCA (and in barbarian camps) nothing changes.
 * <p>
 * Their day is the folk ruler's ({@link Royals.Round}): out on a round of the town by day with an escort of the realm's
 * guards, then court at the throne ({@link #step}); (0.7.175) from mid-afternoon they're among their people, and they
 * sleep in their own bed ({@link #offDuty}). A ruler is no fighter: with a foe near or a
 * fresh wound, they run as villagers do, and the guards close in; badly hurt, they sound the horn ({@link #rally}). Talk
 * to them as any villager; "Seek an audience" in MCA's menu is the folk ruler's right click ({@link KingdomEvents#audience}).
 * Their bar shows to those who strike them, as a folk ruler's does ({@link BossBars}).
 */
public final class Rulers {
	/** Health a villager gains on the throne: a king, a lord (folk rulers have far more, but they fight). */
	static final double KING_HEALTH = 80.0, LORD_HEALTH = 50.0;
	/** A foe this near, or a blow this recent (ticks), and a ruler leaves their day to run (MCA's panic). */
	static final double THREAT_RANGE = 10.0;
	static final int HURT_TICKS = 100;
	/** A guard of the escort leaves the column for a foe this near (their own brain takes the fight). */
	static final double ESCORT_THREAT_RANGE = 16.0;
	/** Badly hurt (this share of their health), they sound the horn once; the guards this near come running. */
	static final float RALLY_AT = 0.5F;
	static final double RALLY_RANGE = 48.0;
	/** (0.7.171) How often a villager ruler's crown is looked for (ticks): dressed again if it's gone. */
	static final int REGALIA_TICKS = 100;
	/** How often a folk ruler of an MCA realm looks to hand over the throne (ticks). */
	static final int RETIRE_TICKS = 200;
	/**
	 * (0.7.175) A villager ruler's day (time of day): their round from dawn until ROUND_TO, court at the throne until
	 * COURT_TO, then their own hours among their people, and the night in their own bed.
	 */
	static final long ROUND_TO = 5000L, COURT_TO = 8000L;
	/**
	 * (0.7.175) A guard's hours off, among the townsfolk: half of them (by who they are) from GUARD_EARLY to GUARD_LATE,
	 * the rest from GUARD_LATE to COURT_TO, and all of them in the evening, from GUARD_EVENING. On an errand, at the
	 * ruler's back, in a fight or with a prisoner, they're on duty whatever the hour.
	 */
	static final long GUARD_EARLY = 2000L, GUARD_LATE = 5000L, GUARD_EVENING = 9000L;

	/** A villager ruler's added health (an attribute modifier). */
	static final Identifier HEALTH = JaymosKingdoms.id("ruler_health");

	/**
	 * Who holds (or last held) a realm's throne, and their blood (a purist's realm is all of it, {@link Purists}); (0.7.171)
	 * and whether she's a queen (or a lady); (0.7.175) and the day they were crowned (-1: before that was written down).
	 */
	record Reign(UUID ruler, String blood, boolean female, long since) {
		static final Codec<Reign> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("ruler").forGetter(Reign::ruler),
				Codec.STRING.optionalFieldOf("blood", "").forGetter(Reign::blood),
				Codec.BOOL.optionalFieldOf("female", false).forGetter(Reign::female),
				Codec.LONG.optionalFieldOf("since", -1L).forGetter(Reign::since))
			.apply(i, Reign::new));
	}

	/** Each realm's reign (by faction id). Saved on the Overworld. */
	static final AttachmentType<Map<String, Reign>> REIGNS = AttachmentRegistry.create(JaymosKingdoms.id("reigns"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Reign.CODEC)));

	/** Each villager ruler's day ({@link Royals.Round}), and their boss bar. */
	private static final Map<PathfinderMob, Royals.Round> ROUNDS = new WeakHashMap<>();
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** Rulers who have sounded the horn since they were last whole. */
	private static final Map<UUID, Boolean> RALLIED = new HashMap<>();
	private static @Nullable MinecraftServer server;

	private Rulers() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			ROUNDS.clear();
			for (ServerBossEvent bar : BARS.values()) {
				bar.removeAllPlayers();
			}
			BARS.clear();
			RALLIED.clear();
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> dropBar(entity.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> dropBar(entity.getUUID()));
	}

	private static void dropBar(UUID id) {
		ServerBossEvent bar = BARS.remove(id);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	// ---- who ----------------------------------------------------------------------------------------------------------

	/** {@code f}'s rulers are its villagers (MCA, a kingdom or a fiefdom). */
	static boolean inPlace(Faction f) {
		return McaCompat.LOADED && f.type().isRealm();
	}

	/** A ruler's role: a king, a lord. */
	static boolean leaderRole(int role) {
		return role == Folk.KING || role == Folk.LORD;
	}

	/** {@code e}'s role ({@link Folk#role}, a villager's job for a settlement), or -1. */
	static int role(@Nullable Entity e) {
		return e instanceof Worker w ? w.role() : -1;
	}

	/** The faction {@code e} serves (a folk's, a villager's in a settlement's service), or -1. */
	static int faction(@Nullable Entity e) {
		return e instanceof Worker w ? w.factionId() : -1;
	}

	/** {@code e} sits on a throne: a folk ruler, or a villager crowned. */
	public static boolean is(@Nullable Entity e) {
		if (e instanceof Folk folk) {
			return folk.leader();
		}
		return e instanceof Worker w && leaderRole(w.role()) && e.isAlive();
	}

	/**
	 * "Seek an audience" (added to MCA's {@code main} menu by our dialogue data): shown to a villager on a throne, and
	 * (0.7.184) to a realm's guard, who takes you to the ruler ({@link Audiences}).
	 */
	public static final String ANSWER = "jaymoskingdoms_audience";

	/** {@code player} seeks an audience with villager ruler {@code ruler} (from MCA's menu): as with a folk ruler, and a king's work. */
	public static void audience(ServerPlayer player, PathfinderMob ruler) {
		if (!is(ruler)) {
			return;
		}
		KingdomEvents.audience(player, ruler, faction(ruler));
		// (0.7.180) the court names who you stand before
		Introductions.learn(player, ruler);
		if (role(ruler) == Folk.KING) {
			Quests.open(player, ruler);
		}
	}

	/** {@code f}'s ruler, if they're in the world and alive (a folk or a villager), else null. */
	static @Nullable PathfinderMob of(ServerLevel level, Faction f) {
		if (f.leader().isEmpty()) {
			return null;
		}
		return level.getEntity(f.leader().get()) instanceof PathfinderMob ruler && ruler.isAlive() ? ruler : null;
	}

	/** What {@code mob} is fighting: a folk's target, an MCA guard's (their brain's), or null. */
	static @Nullable LivingEntity target(Mob mob) {
		LivingEntity target = mob.getTarget();
		if (target != null) {
			return target;
		}
		// (0.7.150) folk brains don't register ATTACK_TARGET, and getMemory throws for those (crashed the server
		// when a crook was caught near a folk guard)
		return mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
			? mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : null;
	}

	/** {@code f}'s ruler's blood, if a villager crowned holds (or last held) its throne. */
	static Heritage.@Nullable Kind blood(Faction f) {
		MinecraftServer s = server;
		if (s == null || f.leader().isEmpty()) {
			return null;
		}
		Reign reign = Factions.level(s).getAttachedOrElse(REIGNS, Map.<String, Reign>of()).get(Integer.toString(f.id()));
		return reign == null || !reign.ruler().equals(f.leader().get()) ? null : Heritage.Kind.byKey(reign.blood());
	}

	/**
	 * (0.7.171) Andy: "the mca kings should now be queens if they are female". {@code f}'s ruler is a woman: a queen (a
	 * lady, of a fiefdom). False for a folk ruler or no ruler.
	 */
	static boolean queen(Faction f) {
		MinecraftServer s = server;
		if (s == null || f.leader().isEmpty()) {
			return false;
		}
		Reign reign = Factions.level(s).getAttachedOrElse(REIGNS, Map.<String, Reign>of()).get(Integer.toString(f.id()));
		return reign != null && reign.female() && reign.ruler().equals(f.leader().get());
	}

	/**
	 * {@code key}, or its queen's (lady's) wording ({@code <key>.her}) if the ruler it speaks of is a woman and the line
	 * has one (a line that never names the ruler has none).
	 */
	public static String her(String key, boolean female) {
		return female && herKeys().contains(key) ? key + ".her" : key;
	}

	/** The server, while one is running. */
	static @Nullable MinecraftServer server() {
		return server;
	}

	/** {@code key} as said of {@code f}'s ruler: a queen's wording if she's a woman. */
	public static String her(String key, @Nullable Faction f) {
		return f != null && queen(f) ? her(key, true) : key;
	}

	/** {@code key} as said by {@code speaker} of the ruler of the realm they're in (a guard, a townsman). */
	public static String herHere(String key, Entity speaker) {
		MinecraftServer s = speaker.level().getServer();
		return s == null ? key : her(key, Factions.realmNear(s, speaker.blockPosition(), Economy.POPULATION_RANGE));
	}

	/** The lines with a queen's wording ({@code <key>.her} in our lang file), read once. */
	private static Set<String> herKeys() {
		Set<String> keys = HER_KEYS;
		if (keys == null) {
			Set<String> found = new HashSet<>();
			FabricLoader.getInstance().getModContainer(JaymosKingdoms.MOD_ID).flatMap(m -> m.findPath("assets/jaymoskingdoms/lang/en_us.json"))
				.ifPresent(path -> {
					try (java.io.Reader in = java.nio.file.Files.newBufferedReader(path)) {
						for (String key : com.google.gson.JsonParser.parseReader(in).getAsJsonObject().keySet()) {
							if (key.endsWith(".her")) {
								found.add(key.substring(0, key.length() - ".her".length()));
							}
						}
					} catch (java.io.IOException | RuntimeException e) {
						JaymosKingdoms.LOGGER.warn("Couldn't read the queens' wordings: {}", e.toString());
					}
				});
			keys = HER_KEYS = Set.copyOf(found);
		}
		return keys;
	}

	private static volatile @Nullable Set<String> HER_KEYS;

	/** A ruler's title: King, Queen, Lord or Lady. */
	static Component title(int role, boolean female) {
		return Component.translatable("title.jaymoskingdoms." + key(role, female));
	}

	private static String key(int role, boolean female) {
		return role == Folk.LORD ? (female ? "lady" : "lord") : (female ? "queen" : "king");
	}

	/**
	 * (0.7.438, Havens patch 6) {@code f}'s ruler's title: a haven's ruler takes their blood's (Tide-Mother, Stone-Elder,
	 * Grove-Warden, War-Chief; a mixed haven's High Elder), anyone else's is King, Queen, Lord or Lady.
	 */
	static Component title(@Nullable MinecraftServer server, @Nullable Faction f, int role, boolean female) {
		String haven = server == null ? "" : Havens.of(server, f);
		return haven.isEmpty() ? title(role, female) : Component.translatable("title.jaymoskingdoms.haven." + haven + "." + key(role, female));
	}

	/** Who last ruled {@code faction} (alive or dead), if a villager: their family is first in line. */
	static @Nullable UUID house(MinecraftServer server, int faction) {
		Reign reign = Factions.level(server).getAttachedOrElse(REIGNS, Map.<String, Reign>of()).get(Integer.toString(faction));
		return reign == null ? null : reign.ruler();
	}

	private static void reign(MinecraftServer server, int faction, LivingEntity ruler) {
		ServerLevel level = Factions.level(server);
		Map<String, Reign> reigns = new HashMap<>(level.getAttachedOrElse(REIGNS, Map.<String, Reign>of()));
		Heritage.Kind kind = VillagerHeritage.of(ruler);
		Reign before = reigns.get(Integer.toString(faction));
		// (0.7.175) the day they were crowned, kept while it's the same reign (one from before then counts from today)
		long since = before != null && before.ruler().equals(ruler.getUUID()) && before.since() >= 0 ? before.since() : Production.day(server);
		reigns.put(Integer.toString(faction), new Reign(ruler.getUUID(), kind == null ? "" : kind.key(), McaCompat.female(ruler), since));
		level.setAttached(REIGNS, Map.copyOf(reigns));
	}

	/** (0.7.175) {@code f}'s villager ruler's reign, if one holds its throne now. */
	static @Nullable Reign reignOf(MinecraftServer server, Faction f) {
		if (f.leader().isEmpty()) {
			return null;
		}
		Reign reign = Factions.level(server).getAttachedOrElse(REIGNS, Map.<String, Reign>of()).get(Integer.toString(f.id()));
		return reign != null && reign.ruler().equals(f.leader().get()) ? reign : null;
	}

	// ---- the crown ------------------------------------------------------------------------------------------------------

	/**
	 * {@code faction}'s throne goes to one of its villagers, as its {@code role} (a king, a lord): its last ruler's family
	 * first. Returns the faction as stored, with them on it; null if no villager could be had (without MCA).
	 */
	static @Nullable Faction crown(ServerLevel level, Faction faction, int role) {
		MinecraftServer s = level.getServer();
		Worker w = McaCompat.enlistRuler(level, faction, role, house(s, faction.id()));
		if (w == null) {
			return null;
		}
		PathfinderMob mob = w.mob();
		fit(mob, role, faction);
		Castle.Keep keep = Factions.keep(s, faction.id());
		mob.setHomeTo(keep != null ? keep.throne() : faction.seat(), keep != null ? 3 : Folk.HOME_RADIUS);
		Faction crowned = Factions.update(s, faction.id(), f -> f.withLeader(Optional.of(mob.getUUID())));
		reign(s, faction.id(), mob);
		RALLIED.remove(mob.getUUID());
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getY() + 1.0, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
		JaymosKingdoms.LOGGER.info("{} crowned {}, one of its own", faction.name(), mob.getName().getString());
		return crowned;
	}

	/**
	 * A villager takes the throne of {@code faction} as {@code role}: (0.7.171) royal clothes from MCA's library if it has
	 * any for them, a crown and (without those clothes) royal attire ({@link Regalia}), and a ruler's health.
	 */
	static void fit(PathfinderMob mob, int role, Faction faction) {
		// (0.7.181) a fresh set of regalia: what they had put by ({@link RoyalDress}) was regalia too
		RoyalDress.reset(mob);
		if (mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)) {
			// (the plain gold helmet rulers wore before 0.7.171)
			mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		}
		boolean robed = McaCompat.redress(mob);
		Regalia.dress(mob, role, faction, McaCompat.female(mob), robed);
		dropBar(mob.getUUID());
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(HEALTH);
			health.addPermanentModifier(new AttributeModifier(HEALTH, role == Folk.LORD ? LORD_HEALTH : KING_HEALTH, AttributeModifier.Operation.ADD_VALUE));
			mob.setHealth(mob.getMaxHealth());
		}
		mob.setPersistenceRequired();
	}

	/** Off the throne (it fell, or went to another; their role already gone): the regalia, royal clothes and health go. */
	static void unfit(PathfinderMob mob) {
		RoyalDress.reset(mob);
		Regalia.undress(mob);
		McaCompat.redress(mob);
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getModifier(HEALTH) != null) {
			health.removeModifier(HEALTH);
			mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
		}
		dropBar(mob.getUUID());
		ROUNDS.remove(mob);
	}

	/**
	 * A folk guard posted at home in a realm whose guards are its villagers: free (no task, no escort, no fight, no
	 * caravan, war party or assault, not downed or making an arrest), so an MCA guard may take its post.
	 */
	static boolean guardMayConvert(ServerLevel level, Folk folk) {
		// ((0.7.293) and a wall archer at his post, WallArchers; (0.7.295) a guard or archer in the saddle too, Cavalry)
		boolean rider = Cavalry.mounted(folk) && (folk.role() == Folk.GUARD || folk.role() == Folk.ARCHER);
		if (folk.role() != Folk.GUARD && !WallArchers.posted(level, folk) && !rider || !folk.task().isEmpty() || folk.escort() != null || folk.getTarget() != null
			|| folk.isPassenger() && !rider
			|| Caravans.isCaravanFolk(folk) || WarParties.isSoldier(folk) || Assault.attacker(folk) || Downed.isDowned(folk) || Arrests.busy(folk)) {
			return false;
		}
		Faction f = Factions.get(level.getServer(), folk.factionId());
		return f != null && !f.fallen() && f.type().isRealm() && Workers.inPlace(f, folk.role())
			&& folk.getHomePosition().closerThan(f.seat(), Economy.POPULATION_RANGE);
	}

	/**
	 * A folk ruler of a realm whose rulers are its villagers (one standing from before): he steps down, and one of the
	 * townsfolk is crowned in his place. True if so (he's gone).
	 */
	static boolean retire(ServerLevel level, Folk folk) {
		if (!folk.leader() || folk.tickCount % RETIRE_TICKS != 0 || folk.getTarget() != null || Carriages.riding(folk)) {
			return false;
		}
		MinecraftServer s = level.getServer();
		Faction f = Factions.get(s, folk.factionId());
		if (f == null || f.fallen() || !inPlace(f) || f.successionAt() >= 0 || !f.leader().map(folk.getUUID()::equals).orElse(false)) {
			return false;
		}
		Component before = folk.getDisplayName();
		Royals.release(level, folk);
		Faction crowned = crown(level, f, folk.role());
		if (crowned == null) {
			return false;
		}
		PathfinderMob heir = of(level, crowned);
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		folk.discard();
		KingdomEvents.announce(s, Component.translatable("message.jaymoskingdoms.ruler.steps_down", before, f.name(),
			heir == null ? Component.literal("?") : heir.getDisplayName()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	// ---- their day ------------------------------------------------------------------------------------------------------

	/**
	 * A villager ruler's step (from {@link Workers#step}): true while their day at court is theirs to keep (in place of
	 * their brain), false to live as a villager just now (asleep, spoken to, in danger, or with no keep to hold court in).
	 */
	static boolean step(ServerLevel level, Enlisted w) {
		PathfinderMob mob = w.mob();
		MinecraftServer s = level.getServer();
		Faction f = Factions.get(s, w.factionId());
		if (f == null) {
			return false;
		}
		// (a kingdom bent the knee, or rose again: its ruler's title follows)
		int role = f.type() == FactionType.FIEFDOM ? Folk.LORD : Folk.KING;
		if (w.role() != role) {
			w.enlist(role, f.id());
			fit(mob, role, f);
		} else if (mob.tickCount % REGALIA_TICKS == 0 && !Regalia.crowned(mob) && !RoyalDress.nightwear(mob)) {
			// (0.7.171) crowned before the regalia (or it was taken off them): they're dressed for the throne
			fit(mob, role, f);
			if (f.leader().map(mob.getUUID()::equals).orElse(false)) {
				// (and whether she's a queen is written down)
				reign(s, f.id(), mob);
			}
		}
		if (mob.tickCount % 5 == 0) {
			ServerBossEvent bar = BARS.computeIfAbsent(mob.getUUID(), id -> new ServerBossEvent(id,
				Component.translatable("entity.jaymoskingdoms.ruler_titled", title(s, f, role, McaCompat.female(mob)), mob.getDisplayName(), f.name()),
				role == Folk.LORD ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
			bar.setProgress(mob.getHealth() / mob.getMaxHealth());
			BossBars.show(level, mob, bar);
		}
		rally(level, mob, f);
		if (Arrests.judging(mob)) {
			// a captive brought before them: they stand and give judgement
			mob.getNavigation().stop();
			return true;
		}
		Royals.Round round = ROUNDS.computeIfAbsent(mob, Royals.Round::new);
		if (f.leader().map(mob.getUUID()::equals).orElse(false) && mob.tickCount % REGALIA_TICKS == 0
			&& (reignOf(s, f) == null || reignOf(s, f).since() < 0)) {
			// (0.7.175) crowned before the day was written down
			reign(s, f.id(), mob);
		}
		if (ValorKings.standsAndFights(level, mob, f)) {
			// (0.7.361) one with King's Valor doesn't run: they fight
			round.stop();
			return true;
		}
		if (mob.isSleeping() || McaCompat.busy(mob) || Quests.listening(mob) || mob instanceof net.minecraft.world.entity.npc.villager.Villager v
			&& Neighbours.meeting(v) || threatened(level, mob, f)) {
			round.stop();
			return false;
		}
		if (offDuty(Royals.timeOfDay(level))) {
			// (0.7.175) Andy: "allow them to maintain and build relationships with their people". Their hours off: they
			// live as their townsfolk do (MCA's day, our routines and neighbours), free of the throne's few blocks,
			// and sleep in their own bed
			round.leave(level);
			if (mob.hasHome() && mob.getHomeRadius() < Royals.ROAM_RADIUS) {
				mob.setHomeTo(f.seat(), Royals.ROAM_RADIUS);
			}
			return false;
		}
		if (round.canUse()) {
			round.tick();
			return true;
		}
		// at the throne: they hold court there
		if (Factions.keep(s, f.id()) == null) {
			return false;
		}
		mob.getNavigation().stop();
		return true;
	}

	/** (0.7.175) A villager ruler's own hours (time of day {@code t}): their meal, after court, and the night. */
	static boolean offDuty(long t) {
		// (0.7.181) and their midday meal, between the round and the afternoon's audience
		return t >= COURT_TO || t < Royals.DAWN || t >= RoyalDress.MEAL_FROM && t < RoyalDress.MEAL_TO;
	}

	/**
	 * (0.7.175) {@code v} (a realm's ruler or guard, {@code w}) is free at {@code t} to live as their townsfolk do: their
	 * routines and their neighbours ({@link Routines#freeOf}), so friendships, courtships and feuds go on with them too.
	 */
	static boolean offDuty(PathfinderMob v, Worker w, long t) {
		if (leaderRole(w.role())) {
			return offDuty(t) && !Arrests.judging(v);
		}
		if (w.role() != Folk.GUARD || w instanceof Folk || Royals.escortOf(w) != null || Arrests.busy(v) || target(v) != null || Retinue.sent(v)
			|| Audiences.leading(v)) {
			return false;
		}
		if (t >= GUARD_EVENING) {
			return true;
		}
		boolean early = (v.getUUID().getLeastSignificantBits() & 1L) == 0L;
		return early ? t >= GUARD_EARLY && t < GUARD_LATE : t >= GUARD_LATE && t < COURT_TO;
	}

	/** A foe near, or a fresh wound: they leave their day and run. */
	private static boolean threatened(ServerLevel level, PathfinderMob mob, Faction f) {
		if (mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < HURT_TICKS) {
			return true;
		}
		if (mob.tickCount % 10 != 0) {
			return false;
		}
		return !level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(THREAT_RANGE),
			e -> e.isAlive() && (e instanceof Enemy || e instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()
				&& Reputation.get(p, f.id()) <= Reputation.HOSTILE)).isEmpty();
	}

	/** Badly hurt: the horn, a second wind, and every guard near sent at whoever did it. Once, until they're whole again. */
	private static void rally(ServerLevel level, PathfinderMob mob, Faction f) {
		if (mob.getHealth() >= mob.getMaxHealth()) {
			RALLIED.remove(mob.getUUID());
			return;
		}
		if (mob.getHealth() > mob.getMaxHealth() * RALLY_AT || RALLIED.containsKey(mob.getUUID())) {
			return;
		}
		RALLIED.put(mob.getUUID(), true);
		mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, 1));
		mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60, 0));
		LivingEntity foe = mob.getLastHurtByMob();
		if (foe != null) {
			for (Worker g : Workers.near(level, mob.getBoundingBox().inflate(RALLY_RANGE), g -> g.factionId() == f.id() && g.role() == Folk.GUARD && g.isAlive())) {
				charge(g.mob(), foe);
			}
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(),
			net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 1.0F);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(32.0))) {
			player.sendSystemMessage(Component.translatable(her("message.jaymoskingdoms.king_rallies", McaCompat.female(mob)), mob.getDisplayName())
				.withStyle(ChatFormatting.GOLD));
		}
	}

	/** {@code guard} goes for {@code foe}: a folk by its target, an MCA guard by its brain's. */
	static void charge(PathfinderMob guard, LivingEntity foe) {
		if (guard instanceof Folk) {
			guard.setTarget(foe);
		} else {
			guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, foe);
		}
	}

	/**
	 * One of a realm's MCA guards on their ruler's escort or leading a prisoner (from {@link Workers#step}): true while
	 * that's theirs to do (in place of their brain), false when their brain should have them (a fight, nothing to do).
	 */
	static boolean guardStep(ServerLevel level, Enlisted w) {
		PathfinderMob guard = w.mob();
		if (Arrests.busy(guard)) {
			// (the arrest walks them)
			return true;
		}
		if (Audiences.leading(guard) && target(guard) == null && !guard.isSleeping() && !McaCompat.busy(guard) && Audiences.lead(guard)) {
			// (0.7.184) walking someone who asked for an audience to the ruler
			return true;
		}
		if (Royals.escortOf(w) == null && Retinue.sent(guard) && target(guard) == null && !guard.isSleeping() && !McaCompat.busy(guard)) {
			// (0.7.175) sent after a stranger who's been hanging about the ruler, or one thrown out of the city ({@link Retinue})
			Retinue.harass(guard);
			return true;
		}
		UUID id = Royals.escortOf(w);
		if (id == null) {
			return false;
		}
		if (!(level.getEntity(id) instanceof PathfinderMob king) || !king.isAlive()) {
			// (their ruler fell, or is out of reach: back to their post)
			Royals.unfollow(w);
			return false;
		}
		if (guard.isSleeping() || McaCompat.busy(guard) || target(guard) != null || guard.tickCount % 10 == 0 && foeNear(level, guard)) {
			return false;
		}
		Royals.keepUp(level, guard, king, Royals.slotOf(w));
		return true;
	}

	static boolean foeNear(ServerLevel level, PathfinderMob guard) {
		return !level.getEntitiesOfClass(LivingEntity.class, guard.getBoundingBox().inflate(ESCORT_THREAT_RANGE),
			e -> e.isAlive() && e != guard && (e instanceof Enemy || McaCompat.foe(guard, e))).isEmpty();
	}

	/** The guards (folk and villagers) of {@code f} in {@code box}. */
	static int guards(ServerLevel level, Faction f, AABB box) {
		return Workers.near(level, box, g -> g.isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD).size();
	}
}
