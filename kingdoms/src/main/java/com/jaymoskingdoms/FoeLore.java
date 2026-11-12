package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Knowing the foe, both ways (docs/Pawn-Plan.md rows B1 and A2; after the pawns' foe-knowledge stars in Dragon's Dogma,
 * a commercial game: ideas only).
 * <ul>
 * <li><b>Companions</b> keep stars on each kind of creature ({@link #STARS}, up to three), earned by the ones they help
 * bring down ({@link #KILLS_PER_STAR}). With stars they hit that kind harder ({@link #DAMAGE_PER_STAR}), favour it in a
 * fight, and say they know it when one turns up ("know"). A star earned beside a player who isn't theirs is
 * remembered with that player's name, and they say who taught them ("know.taught").</li>
 * <li><b>Camps, bands and realms</b> keep stars on each player ({@link #KNOWN}, on the player): how often they've met
 * you ({@link #MEETING_GAP} apart) and how you hurt them (shot, blade, blunt, magic, fire). From the third meeting on
 * they come ready for it: shields against a shooter, footing against a brawler, speed against a caster, fire cloth
 * against fire ("knows.*"). A band that has never met you but met someone who fights like you says so, naming them
 * ("knows.other").</li>
 * </ul>
 */
public final class FoeLore {
	/** Kills a companion helps with for each star, and the most stars. */
	static final int[] KILLS_PER_STAR = {2, 6, 14};
	static final int MAX_STARS = 3;
	/** More damage per star against a kind they know. */
	static final float DAMAGE_PER_STAR = 0.08F;
	/** Score a leaning gives a foe of a kind they know (per star). */
	static final double SCORE_PER_STAR = 4.0;
	/** Companions within this of a kill (blocks) who were on it share it. */
	static final double SHARE = 16.0;
	/** Two meetings at least this far apart (ticks); counters from this many meetings. */
	static final long MEETING_GAP = 6000L;
	static final int READY_AFTER = 3;
	/** A companion says it knows a kind at most this often (ticks). */
	static final long KNOW_GAP = 1200L;
	/** A band's counter lasts this long (ticks) once put on. */
	static final int COUNTER_TICKS = 1200;

	/** On a companion: kills per creature kind ("ns:path" -> count). Saved. */
	public static final AttachmentType<Map<String, Integer>> STARS = AttachmentRegistry.create(JaymosKingdoms.id("foe_stars"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));
	/** On a companion: who taught them each kind (kind -> player name), when it wasn't their own. Saved. */
	public static final AttachmentType<Map<String, String>> TAUGHT = AttachmentRegistry.create(JaymosKingdoms.id("foe_taught"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** On a player: what each side ("f:id", "illager") knows of them: [meetings, shot, blade, blunt, magic, fire]. Saved. */
	public static final AttachmentType<Map<String, List<Integer>>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("foe_known"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT.listOf())).copyOnDeath());

	/** The ways a player hurts a band, as counted in {@link #KNOWN} (after the meeting count). */
	enum Way {
		SHOT, BLADE, BLUNT, MAGIC, FIRE
	}

	private static final Identifier FOOTING = JaymosKingdoms.id("foe_footing"), RUSH = JaymosKingdoms.id("foe_rush");

	/** Side and player -> when they last met (game time). Server memory. */
	private static final Map<String, Long> MET = new HashMap<>();
	/** Mobs already readied against a player this fight (mob uuid + player uuid). */
	private static final Set<String> READIED = new HashSet<>();
	private static final Map<UUID, Long> SAID_KNOW = new HashMap<>();
	private static final Map<String, Long> SAID_OTHER = new HashMap<>();

	private FoeLore() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(FoeLore::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer player && entity instanceof Mob m) {
				hurtBy(m, player, source);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(FoeLore::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MET.clear();
			READIED.clear();
			SAID_KNOW.clear();
			SAID_OTHER.clear();
		});
	}

	// ---- companions' stars -----------------------------------------------------------------------------------------------

	static String kind(LivingEntity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
	}

	/** How many stars {@code m} has on {@code e}'s kind (0 to {@link #MAX_STARS}). */
	static int stars(Mob m, LivingEntity e) {
		Map<String, Integer> kills = m.getAttached(STARS);
		if (kills == null) {
			return 0;
		}
		int n = kills.getOrDefault(kind(e), 0), stars = 0;
		while (stars < MAX_STARS && n >= KILLS_PER_STAR[stars]) {
			stars++;
		}
		return stars;
	}

	/** Extra score a leaning gives {@code e} for {@code m} knowing its kind. */
	static double knownScore(Mob m, LivingEntity e) {
		return SCORE_PER_STAR * stars(m, e);
	}

	/** (From the hurt mixin) a companion hits a kind it knows harder. */
	public static float dealt(LivingEntity self, DamageSource source, float amount) {
		if (source.getEntity() instanceof Mob by && by != self && by.hasAttached(STARS)) {
			int stars = stars(by, self);
			if (stars > 0) {
				return amount * (1.0F + DAMAGE_PER_STAR * stars);
			}
		}
		return amount;
	}

	/** A kill: every companion who was on it (or struck it) learns that kind a little. */
	private static void died(LivingEntity dead, DamageSource source) {
		if (!(dead.level() instanceof ServerLevel level) || dead instanceof Player) {
			return;
		}
		String kind = kind(dead);
		for (Villager v : level.getEntitiesOfClass(Villager.class, dead.getBoundingBox().inflate(SHARE),
			v -> v.isAlive() && Comrades.side(v).startsWith("p:") && !Comrades.side(v).equals(Comrades.side(dead)))) {
			if (source.getEntity() != v && Comrades.target(v) != dead && v.getLastHurtMob() != dead) {
				continue;
			}
			learn(level, v, kind, dead);
		}
	}

	private static void learn(ServerLevel level, Villager v, String kind, LivingEntity dead) {
		int before = stars(v, dead);
		Map<String, Integer> kills = new HashMap<>(v.getAttachedOrElse(STARS, Map.of()));
		kills.merge(kind, 1, Integer::sum);
		v.setAttached(STARS, kills);
		int after = stars(v, dead);
		if (after <= before) {
			return;
		}
		// a new star: whom they learned it beside, if not their own
		String side = Comrades.side(v);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(SHARE))) {
			if (!side.equals("p:" + p.getUUID()) && !p.isSpectator()) {
				Map<String, String> taught = new HashMap<>(v.getAttachedOrElse(TAUGHT, Map.of()));
				taught.put(kind, p.getGameProfile().name());
				v.setAttached(TAUGHT, taught);
				break;
			}
		}
		Callouts.say(v, "know.star", dead, true, dead.getType().getDescription());
	}

	/** (From {@link Comrades#think}) {@code m} has just taken {@code foe}: if it knows the kind, it says so. */
	static boolean taken(Mob m, LivingEntity foe) {
		int stars = stars(m, foe);
		if (stars == 0) {
			return false;
		}
		long now = m.level().getGameTime();
		Long last = SAID_KNOW.get(m.getUUID());
		if (last != null && now - last < KNOW_GAP) {
			return false;
		}
		SAID_KNOW.put(m.getUUID(), now);
		String teacher = m.getAttachedOrElse(TAUGHT, Map.of()).get(kind(foe));
		if (teacher != null) {
			return Callouts.say(m, "know.taught", foe, true, Component.literal(teacher), foe.getType().getDescription());
		}
		return Callouts.say(m, "know", foe, false, foe.getType().getDescription());
	}

	// ---- what bands know of players --------------------------------------------------------------------------------------

	/** A side that keeps what it learns of players: a faction (camp, village, realm) or the illagers. */
	static boolean keeps(String side) {
		return side.startsWith("f:") || side.equals("illager");
	}

	private static void hurtBy(Mob m, ServerPlayer player, DamageSource source) {
		String side = Comrades.side(m);
		if (!keeps(side)) {
			return;
		}
		Way way = way(source);
		long now = m.level().getGameTime();
		String key = side + "|" + player.getUUID();
		Map<String, List<Integer>> known = new HashMap<>(player.getAttachedOrElse(KNOWN, Map.of()));
		List<Integer> row = new ArrayList<>(known.getOrDefault(side, List.of()));
		while (row.size() < 1 + Way.values().length) {
			row.add(0);
		}
		Long last = MET.put(key, now);
		if (last == null || now - last > MEETING_GAP) {
			row.set(0, row.get(0) + 1);
			if (row.get(0) == 1) {
				likeAnother(m, player, side, way);
			}
		}
		if (way != null) {
			row.set(1 + way.ordinal(), row.get(1 + way.ordinal()) + 1);
		}
		known.put(side, List.copyOf(row));
		player.setAttached(KNOWN, known);
	}

	static @Nullable Way way(DamageSource source) {
		CaptainWays.Cause c = CaptainWays.cause(source);
		if (c == null) {
			return null;
		}
		return switch (c) {
			case FIRE -> Way.FIRE;
			case SHOT -> Way.SHOT;
			case BLUNT -> Way.BLUNT;
			case BLADE -> Way.BLADE;
			case MAGIC -> Way.MAGIC;
			case BEAST -> null;
		};
	}

	/** How many times {@code side} has met {@code player}, and the way they've hurt it most (null if none yet). */
	static int meetings(Player player, String side) {
		List<Integer> row = player.getAttachedOrElse(KNOWN, Map.of()).get(side);
		return row == null || row.isEmpty() ? 0 : row.get(0);
	}

	static @Nullable Way most(Player player, String side) {
		List<Integer> row = player.getAttachedOrElse(KNOWN, Map.of()).get(side);
		if (row == null) {
			return null;
		}
		Way best = null;
		int bestN = 0;
		for (Way w : Way.values()) {
			int n = row.size() > 1 + w.ordinal() ? row.get(1 + w.ordinal()) : 0;
			if (n > bestN) {
				bestN = n;
				best = w;
			}
		}
		return best;
	}

	/** First meeting: if another player this side knows well fights the same way, one of them says so. */
	private static void likeAnother(Mob m, ServerPlayer player, String side, @Nullable Way way) {
		if (way == null || !(m.level() instanceof ServerLevel level)) {
			return;
		}
		Long last = SAID_OTHER.get(side + "|" + player.getUUID());
		if (last != null) {
			return;
		}
		for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
			if (other != player && meetings(other, side) >= 2 && most(other, side) == way) {
				SAID_OTHER.put(side + "|" + player.getUUID(), level.getGameTime());
				Callouts.say(m, "knows.other", player, true, other.getDisplayName());
				return;
			}
		}
	}

	/** Every second: band members fighting a player they know well come ready for how that player fights. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Mob m : Comrades.fighters(level)) {
				if (!(Comrades.target(m) instanceof ServerPlayer player)) {
					continue;
				}
				String side = Comrades.side(m);
				if (!keeps(side) || meetings(player, side) < READY_AFTER) {
					continue;
				}
				Way way = most(player, side);
				if (way != null && READIED.add(m.getUUID() + "|" + player.getUUID())) {
					counter(m, way);
					Callouts.say(m, "knows." + way.name().toLowerCase(java.util.Locale.ROOT), player, false, player.getDisplayName());
				}
			}
		}
		if (READIED.size() > 4096) {
			READIED.clear();
		}
	}

	/** {@code m}'s answer to {@code way}. */
	private static void counter(Mob m, Way way) {
		switch (way) {
			case SHOT -> {
				if (m.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
					m.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
					// (theirs to keep, not yours to take)
					m.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
				}
			}
			case BLADE, BLUNT -> {
				AttributeInstance kb = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
				if (kb != null) {
					kb.addOrUpdateTransientModifier(new AttributeModifier(FOOTING, 0.5, AttributeModifier.Operation.ADD_VALUE));
				}
				m.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, COUNTER_TICKS, 0));
			}
			case MAGIC -> {
				AttributeInstance speed = m.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.addOrUpdateTransientModifier(new AttributeModifier(RUSH, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				}
			}
			case FIRE -> m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, COUNTER_TICKS, 0));
		}
	}
}
