package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.jaymoskingdoms.mixin.MobAccessor;
import net.minecraft.ChatFormatting;
import com.jaymoskingdoms.disasters.Mist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonSittingPhase;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.111) Mob senses, the Stealth tree's answer to being seen by monsters (the ideas of Dynamic Stealth, written afresh).
 * <ul>
 * <li><b>Sight</b> ({@link #factor}, from {@code LivingEntityVisibilityMixin}): how far a hostile or neutral mob notices a
 * player is its follow range times this. Vanilla's own sight (invisibility, mob heads) and Darktide's veil are applied
 * first and this multiplies on top. It is the light at the eyes (down to {@link #LIGHT_MIN}), sneaking ({@link #SNEAK}, or
 * Quiet Step's), standing still ({@link #STILL}), where the mob looks ({@link #BEHIND} outside its {@link #VIEW_COS} cone,
 * unless within {@link #BEHIND_CLOSE}), a hood ({@link Identities#HOOD_SIGHT}), glowing or burning ({@link #LIT}). A mob
 * already after you, with you in sight, sees you at full range. Creative and spectator players are untouched.
 * Andy (2026-11-13): "the unburied and digger and shadow have all been passive toward me ... they should be
 * hostile no?" They were nearly blind: in the dark sight fell to a quarter, and a mob not looking your way saw a third of
 * that again, so a Shadow (24 blocks' range) in its own darkness noticed a walking player at under two blocks unless it
 * happened to face them. Now the creatures of the night and the dark ({@link #DARKVISION}, the undead among them) see in
 * the dark as in dim light ({@link #DARKVISION_LIGHT}); anyone walking about within {@link #HEARD} blocks who isn't sneaking
 * is heard whatever the light or the way the mob faces (a Ranger's quiet tread, {@link Rangers#quiet}, makes that nearer); and whatever shows a boss bar
 * ({@link BossBars#boss}, the Unburied and the Snow Widow among them) senses as a boss.</li>
 * <li><b>Losing track</b> ({@link WatchGoal}, {@link SearchGoal}): a mob that has not had line of sight to its player target
 * for {@link #LOSE_TRACK_TICKS} drops them, walks to where they were last seen and looks around for
 * {@link #LOOK_TICKS}. Bosses never lose track, and the kingdoms' own folk are exempt. State lives in {@link #TRACKS}, one per mob (not an attachment: Fabric would copy it onto a converted mob). Spiders keep their own memory ({@code wild.SpiderMinds}).</li>
 * <li><b>Hearing</b> ({@link #hear}, from {@link Hearing}): a faint sound at the edge of hearing makes a mob stop and turn
 * its head toward it for {@link #HEED_TICKS} ({@link HeedGoal}); a second near it, {@link #HEED_SECOND} or more after the
 * first and within {@link #HEED_MEMORY}, brings it over,
 * as a loud one does at once. Lured, it searches the spot, then walks back to where it was ({@link #RETURN_SPEED}). A camp's
 * outlaws ({@link Folk}) hear and search too, but never lose a foe they are after.</li>
 * <li><b>The gauge</b> ({@link Gauge}): the nearest mob's state, sent a few times a second only when it changes (a beast
 * stalking you, {@code wild.WolfStalkers}, shows only once you can feel it: {@link #feelsStalkers}); and the
 * stealth meter beside it ({@link Meter}): how far your noise carries, how lit you are, whether you bleed, whether you talk.</li>
 * <li><b>Unaware blows</b> ({@link #dealt}): a melee hit on a monster that isn't after you and can't see you (behind it, or
 * it is searching) and struck from its rear ({@link #REAR_DOT}) is a sneak attack, only with the Sneak Attack perk (its ranks: {@link #SNEAK_ATTACK_RANK_MULT}). The hidden blade's
 * full-strength stab on an unaware one is an assassination ({@link HiddenBlade}, Assassinate).</li>
 * <li><b>Bosses and pets</b> (0.7.114): even they can get got. A boss is harder to catch: stealth cuts its sight no lower
 * than {@link #BOSS_SIGHT_MIN}, it sees in a wider cone ({@link #BOSS_VIEW_COS}), it is only unaware while it is after no
 * one at all (the dragon: only while perched), and a sneak attack or assassination on it deals at most {@link #BOSS_BLOW_SHARE} of its health. A tame pet
 * can be caught unaware like any other mob, except by its own owner (no accidental assassination of your own dog).</li>
 * </ul>
 */
public final class Senses {
	// ---- tuning ----
	/** Light at the eyes: sight in the dark (0) and in the light (15), as a share of a mob's range. */
	static final double LIGHT_MIN = 0.25, LIGHT_MAX = 1.0;
	/** Sneaking, standing still, glowing or burning, as shares of a mob's range. */
	static final double SNEAK = 0.6, STILL = 0.8, LIT = 1.5;
	/** Quiet Step: sneaking by rank 1 and rank 2 instead of {@link #SNEAK}. */
	static final double[] QUIET_STEP_SNEAK = {0.5, 0.4};
	/** Shadowed: per rank, this much less sight, in proportion to how dark it is where you stand. */
	static final double SHADOWED_PER_RANK = 0.1;
	/** A mob sees in front of it within this cosine of its look (0.5: a 120 degree cone); outside, this share, unless closer than {@link #BEHIND_CLOSE} blocks. */
	static final double VIEW_COS = 0.5, BEHIND = 0.3, BEHIND_CLOSE = 2.0;
	/** A mob with darkvision sees in the dark as in this much light (0 dark, 1 full light). */
	static final double DARKVISION_LIGHT = 0.6;
	/**
	 * (2026-11-19) Andy's clips: a camp's warband saw him from far below at dusk, high on a cliff and outside where they were
	 * looking. Someone more than {@link #ABOVE_FROM} blocks above a mob's eyes is seen at {@link #ABOVE} of the range (nobody
	 * scans the cliffs over their heads), and one steeper than {@link #UP_LIMIT} degrees over its look counts as outside its
	 * view cone, as one behind it ({@link #BEHIND}). Not within {@link #BEHIND_CLOSE}.
	 */
	static final double ABOVE_FROM = 4.0, ABOVE = 0.5, UP_LIMIT = 40.0;
	/** A player walking about (moving, not sneaking) this near (blocks) is heard, whatever the light or where the mob looks. */
	static final double HEARD = 8.0;
	/** Creatures of the night and the dark, that see in it: {@code jaymoskingdoms:darkvision}. */
	static final TagKey<EntityType<?>> DARKVISION = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("darkvision"));
	/** The least a mob's sight may be cut to. */
	static final double MIN_FACTOR = 0.05;
	/** Horizontal movement (blocks squared per tick) above which a player is moving, not standing still. */
	static final double MOVING_SQR = 0.0004;

	/** Ticks without line of sight before a mob drops its target; Slip the Net divides it by this. */
	static final int LOSE_TRACK_TICKS = 100, SLIP_THE_NET_DIVISOR = 2;
	/** Searching: the most ticks to walk to the last-seen spot, ticks spent looking around once there (5 s), and the radius of the looking (blocks). */
	static final int WALK_TICKS = 200, LOOK_TICKS = 100;
	static final double LOOK_RADIUS = 5.0, SEARCH_SPEED = 1.0;
	/** How near (blocks) is arrived. */
	static final double ARRIVED = 2.0;
	/** (0.7.155) Lures ({@link #lure}): how loud losing a target counts as, and the ticks a far lure allows per block of the walk. */
	static final double LOST_LURE = 8.0;
	static final int LURE_TICKS_PER_BLOCK = 12;

	/** (Andy, 2026-11-17: "stealth kills need to go onto the skill tree, attacking from behind should be intentional") A sneak attack, only with the Sneak Attack perk, multiplies the blow by this at rank 1 and 2. */
	static final float[] SNEAK_ATTACK_RANK_MULT = {2.5F, 3.0F};
	/** A sneak attack only from the mob's rear: the way from it to the attacker, flat, against its look, at most this (-0.3: the back 145 degrees or so). */
	static final double REAR_DOT = -0.3;

	/** Bosses: stealth cuts their sight no lower than this share; they see within this cosine of their look (-0.5: a 240 degree cone). */
	static final double BOSS_SIGHT_MIN = 0.4, BOSS_VIEW_COS = -0.5;
	/** Bosses: a sneak attack or assassination deals at most this share of their max health, and never less than this times the plain blow. */
	static final float BOSS_BLOW_SHARE = 0.15F, BOSS_BLOW_MIN_MULT = 1.5F;

	/** The gauge: how often it is worked out (ticks), how far it looks (blocks), and how many steps a "noticing" fill has. */
	static final int GAUGE_TICKS = 5;
	public static final int FILL_STEPS = 10;
	static final double GAUGE_RANGE = 40.0;
	/** Stealth level from which a beast stalking you shows on the eye (the Sixth Sense perk shows it at any level). */
	public static final int FEEL_STALKERS_LEVEL = 15;

	/** The gauge's states. */
	public static final int IDLE = 0, NOTICING = 1, SEARCHING = 2, SPOTTED = 3;

	/** Heeding (Hearing): how long a mob stands and looks toward a faint sound (ticks), and how long and how near (blocks) a second one there brings it over. */
	static final int HEED_TICKS = 60, HEED_MEMORY = 200;
	/** Only a sound this long (ticks) or more after the first it stopped for brings it over: it looks first. */
	static final int HEED_SECOND = 40;
	static final double HEED_NEAR = 8.0;
	/** After a search, a lured mob walks back to where it was, at this speed, if it is further than this (blocks). */
	static final double RETURN_SPEED = 0.8, RETURN_MIN = 4.0;
	/** What {@link #hear} did: nothing, stopped to listen, came to look. */
	static final int UNHEARD = 0, HEEDED = 1, LURED = 2;

	/**
	 * (0.7.136) Sneaking past: a sneaking, moving player with a monster in sight within {@link #SNEAK_PAST_RANGE} blocks
	 * (that isn't after them or hunting them) counts the time; every {@link #SNEAK_PAST_TICKS} of it is Stealth
	 * experience ({@link Skills#sneakPast}). Within {@link #SNEAK_PAST_CLOSE} blocks it counts twice as fast.
	 */
	static final double SNEAK_PAST_RANGE = 12.0, SNEAK_PAST_CLOSE = 6.0;
	static final int SNEAK_PAST_TICKS = 200;

	private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));

	/** What a mob remembers of the player it lost. Not saved: a reload forgets the hunt. */
	static final class Track {
		/** Ticks without line of sight to the target. */
		int blind;
		/** The player it was after, and where it last saw them. */
		@Nullable UUID who;
		@Nullable Vec3 seen;
		/** Searching, the spot, ticks left to get there (-1: arrived and looking), and ticks left looking. */
		boolean searching, started;
		@Nullable Vec3 spot;
		int walkLeft, lookLeft;
		/** (0.7.155) How loud what it is searching for was where it stood ({@link Awareness}); a fainter lure doesn't turn it. */
		double lure;
		/** Whether it drops a foe it can't see (a camp's outlaws keep after theirs). */
		boolean losesTrack = true;
		/** Heeding a faint sound: where, ticks left looking, until when a second there brings it, whose. */
		@Nullable Vec3 heedAt;
		int heedLeft;
		long heedSince, heedUntil;
		@Nullable UUID heedWho;
		/** Where it was when a sound drew it off, and whether it is walking back there. */
		@Nullable Vec3 home;
		boolean returning;
	}

	/** Every hostile or neutral mob's, made when it loads together with its goals. */
	private static final Map<Mob, Track> TRACKS = new WeakHashMap<>();

	/**
	 * Server → player: the stealth meter beside the gauge: how far their noise carries (blocks, to 63), how lit they are
	 * (0 to 15), whether they're bleeding, and how they're speaking ({@link Hearing#QUIET} to {@link Hearing#SHOUT}).
	 */
	public record Meter(int noise, int light, boolean bleeding, int voice) implements CustomPacketPayload {
		public static final Type<Meter> TYPE = new Type<>(JaymosKingdoms.id("stealth_meter"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Meter> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeByte(p.noise);
				buf.writeByte(p.light << 4 | (p.bleeding ? 8 : 0) | p.voice);
			},
			buf -> {
				int noise = buf.readByte() & 0xFF;
				int b = buf.readByte() & 0xFF;
				return new Meter(noise, b >> 4, (b & 8) != 0, b & 7);
			});

		int packed() {
			return this.noise << 16 | this.light << 8 | (this.bleeding ? 8 : 0) | this.voice;
		}

		@Override
		public Type<Meter> type() {
			return TYPE;
		}
	}

	/** Server → player: what to show on the awareness gauge (state, and for {@link #NOTICING} how full, in {@link #FILL_STEPS}). */
	public record Gauge(int state, int fill) implements CustomPacketPayload {
		public static final Type<Gauge> TYPE = new Type<>(JaymosKingdoms.id("senses_gauge"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Gauge> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeByte(p.state << 4 | p.fill),
			buf -> {
				int b = buf.readByte() & 0xFF;
				return new Gauge(b >> 4, b & 15);
			});

		@Override
		public Type<Gauge> type() {
			return TYPE;
		}
	}

	/**
	 * Server → player: how far a goblin village's suspicion of their disguise has got ({@code Disguise}), in
	 * {@link #FILL_STEPS}; the awareness eye fills with it too (Andy: "The eye should fill"). 0: none.
	 */
	public record Suspicion(int fill) implements CustomPacketPayload {
		public static final Type<Suspicion> TYPE = new Type<>(JaymosKingdoms.id("disguise_suspicion"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Suspicion> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeByte(p.fill), buf -> new Suspicion(buf.readByte()));

		@Override
		public Type<Suspicion> type() {
			return TYPE;
		}
	}

	/** Player → where they were last tick, and whether they moved. */
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Boolean> MOVING = new HashMap<>();
	/** Player → the gauge last sent, and the meter. */
	private static final Map<UUID, Integer> SENT = new HashMap<>();
	private static final Map<UUID, Integer> METERED = new HashMap<>();
	/** Player → {target entity id, game time} of their last sneak attack (one blow, hurt twice, counts once). */
	private static final Map<UUID, long[]> STRUCK = new HashMap<>();
	/** (0.7.136) Player → ticks spent sneaking past monsters toward the next Stealth experience. */
	private static final Map<UUID, Integer> SNEAKING_PAST = new HashMap<>();

	private Senses() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Gauge.TYPE, Gauge.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Meter.TYPE, Meter.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Suspicion.TYPE, Suspicion.CODEC);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			boolean folk = entity instanceof Folk;
			if (entity instanceof Mob mob && (minded(mob) && !(mob instanceof Spider) || folk) && !TRACKS.containsKey(mob)) {
				Track track = new Track();
				// (the folk hear and search, Hearing: only its outlaws are ever lured; none of them gives up a foe)
				track.losesTrack = !folk;
				TRACKS.put(mob, track);
				var goals = ((MobAccessor) mob).jaymoskingdoms$goals();
				// no flags: counts beside everything else; the search and the heeding want the legs and the eyes
				goals.addGoal(0, new WatchGoal(mob, track));
				goals.addGoal(folk ? 2 : 1, new SearchGoal(mob, track));
				goals.addGoal(folk ? 2 : 1, new HeedGoal(mob, track));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Senses::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_POS.clear();
			MOVING.clear();
			SENT.clear();
			METERED.clear();
			STRUCK.clear();
			SNEAKING_PAST.clear();
			TRACKS.clear();
		});
	}

	private static void forget(UUID id) {
		LAST_POS.remove(id);
		MOVING.remove(id);
		SENT.remove(id);
		METERED.remove(id);
		STRUCK.remove(id);
		SNEAKING_PAST.remove(id);
	}

	/**
	 * (2026-11-19) Whose sight of a player follows the light, the way they look and the height ({@link #factor}): every mob that
	 * {@link #sensing senses}, and the folk too (a camp's raiders and archers, a realm's guards) and the outlaws among the
	 * villagers ({@link Outlaws#is}). Andy: the camp folk saw him from as far as their follow range in the dark, from far below.
	 */
	static boolean sighted(Mob mob) {
		return sensing(mob) || mob instanceof Folk || Outlaws.is(mob);
	}

	/** A mob that senses (sight, the gauge, unaware blows): hostile or neutral, bosses too, not one of the kingdoms' own folk. */
	static boolean sensing(Mob mob) {
		return (mob instanceof Enemy || mob instanceof NeutralMob) && !(mob instanceof Folk);
	}

	/** A mob that also loses track and searches: one that senses, not a boss (a boss never gives up the hunt). */
	static boolean minded(Mob mob) {
		return sensing(mob) && !boss(mob);
	}

	static boolean boss(Mob mob) {
		EntityType<?> type = mob.getType();
		// and whatever shows a boss bar (the Unburied, the Snow Widow, rulers, warlords, the giants)
		return mob instanceof GreatNecromancer || BossBars.boss(mob) || type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN
			|| type.builtInRegistryHolder().is(BOSSES);
	}

	private static boolean touched(Entity player) {
		return player instanceof ServerPlayer p && (p.isCreative() || p.isSpectator());
	}

	// ---- sight -------------------------------------------------------------------------------------------------------

	/** Whether {@code looker} is after {@code player} and has them in sight (it keeps seeing them at full range). */
	private static boolean locked(Mob looker, ServerPlayer player) {
		return looker.getTarget() == player && looker.hasLineOfSight(player);
	}

	/** {@code player} is in front of {@code looker}, inside its view cone (a boss's is wider). */
	static boolean inCone(LivingEntity looker, LivingEntity player) {
		Vec3 to = player.getEyePosition().subtract(looker.getEyePosition());
		double len = to.length();
		if (len < 1.0E-4) {
			return true;
		}
		Vec3 view = looker.getViewVector(1.0F);
		if (looker instanceof EnderDragon) {
			// (the dragon flies along its yaw's back: its head is where a mob's back would be)
			view = view.multiply(-1.0, 1.0, -1.0);
		}
		return view.dot(to.scale(1.0 / len)) >= (looker instanceof Mob mob && boss(mob) ? BOSS_VIEW_COS : VIEW_COS);
	}

	/** (2026-11-19) {@code player} is more than {@link #UP_LIMIT} degrees above where {@code looker} looks (straight up is 90). */
	static boolean steeplyAbove(LivingEntity looker, LivingEntity player) {
		Vec3 to = player.getEyePosition().subtract(looker.getEyePosition());
		double flat = to.horizontalDistance();
		if (to.y <= 0.0) {
			return false;
		}
		// (its own look up or down: a mob's pitch is negative looking up)
		double up = Math.toDegrees(Math.atan2(to.y, Math.max(flat, 1.0E-4))) + looker.getXRot();
		return up > UP_LIMIT;
	}

	/**
	 * What a mob's range for noticing {@code player} is multiplied by, on top of vanilla's (see the class comment). 1 for a
	 * creative or spectator player, or anything that isn't a hostile or neutral mob. A boss's never drops below {@link #BOSS_SIGHT_MIN}.
	 */
	public static double factor(LivingEntity self, ServerLevel level, Entity looking) {
		if (!(self instanceof ServerPlayer player) || !(looking instanceof Mob looker) || touched(player) || !sighted(looker) || locked(looker, player)) {
			return 1.0;
		}
		return sight(player, level, looker);
	}

	/**
	 * (2026-11-19) Whether {@code looker} notices {@code player} within {@code range} blocks: in its line of sight and near enough
	 * for the light, where it looks, how high above it they are and the rest of {@link #factor} (a captain naming himself only
	 * once he's really seen you, {@code Captains.meet}). Creative and spectator players are never noticed this way.
	 */
	public static boolean notices(Mob looker, ServerPlayer player, double range) {
		if (touched(player) || !looker.hasLineOfSight(player)) {
			return false;
		}
		double f = locked(looker, player) ? 1.0 : sight(player, (ServerLevel) looker.level(), looker);
		return player.distanceTo(looker) <= range * f;
	}

	/** {@link #factor} for a player and a mob it applies to. */
	private static double sight(ServerPlayer player, ServerLevel level, Mob looker) {
		BlockPos eyes = BlockPos.containing(player.getEyePosition());
		double light = level.getMaxLocalRawBrightness(eyes) / 15.0;
		// the creatures of the dark see in it
		double seen = looker.getType().builtInRegistryHolder().is(DARKVISION) ? Math.max(light, DARKVISION_LIGHT) : light;
		double f = LIGHT_MIN + (LIGHT_MAX - LIGHT_MIN) * seen;
		f *= 1.0 - SHADOWED_PER_RANK * Skills.rank(player, Skills.Perk.SHADOWED) * (1.0 - light);
		if (player.isCrouching()) {
			int quiet = Skills.rank(player, Skills.Perk.QUIET_STEP);
			f *= Skills.sneakFactor(player, quiet <= 0 ? SNEAK : QUIET_STEP_SNEAK[Math.min(quiet, QUIET_STEP_SNEAK.length) - 1]);
		}
		if (!MOVING.getOrDefault(player.getUUID(), true)) {
			f *= STILL;
		}
		if (Pace.slow(player)) {
			f *= Pace.SIGHT;
		}
		f *= FieldWeather.sight(level, looker);
		if (player.distanceTo(looker) > BEHIND_CLOSE) {
			// (2026-11-19) well above it, and steeply up out of its sight, as if behind it
			double rise = player.getEyeY() - looker.getEyeY();
			if (!inCone(looker, player) || steeplyAbove(looker, player)) {
				f *= BEHIND;
			}
			if (rise > ABOVE_FROM) {
				f *= ABOVE;
			}
		}
		f *= Identities.hoodSight(player);
		if (player.isOnFire() || player.hasEffect(MobEffects.GLOWING)) {
			f *= LIT;
		}
		// (0.7.513) the old patron's trial and scar: the dead at night and wild beasts see you farther
		f *= Turning.sight(player, looker, level);
		// (Hardrock picks) a smelly player is noticed further off, keen noses most (Stench)
		f *= Stench.factor(player, looker);
		// a Ranger moves quietly through the country (Rangers)
		f *= Rangers.quiet(player);
		// a Skulker is hard to see in the dim and when sneaking, a shadow Monk too
		f *= FeatsBody.sight(player, light) * Paths.sight(player, light);
		// morning mist (Mist): a mob sees less far through it, so a player in it is noticed at half the range at its thickest
		f *= Mist.sight(level, player.getEyePosition());
		f = Math.max(boss(looker) ? BOSS_SIGHT_MIN : MIN_FACTOR, f);
		// (class trees) a Stalker Ranger, crouched and still in grass or leaves, isn't seen past six blocks
		f = RogueExtras.stalked(player, looker, !MOVING.getOrDefault(player.getUUID(), true), f);
		// heard: someone walking about close by, not sneaking, is noticed in the dark or behind its back
		if (!player.isCrouching() && MOVING.getOrDefault(player.getUUID(), true) && looker.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)) {
			double range = looker.getAttributeValue(Attributes.FOLLOW_RANGE);
			if (range > 0.0) {
				// (Hearing) quiet or loud ground, wet ground, and soft boots and leggings carry it less or further (ordinary ground as before)
				// (class trees) and Muffled boots (a Tinkerer's infusion) carry it half as far, a Second Storey Thief's tread too
				f = Math.max(f, HEARD * Rangers.quiet(player) * TinkerPerks.footfall(player) * RogueExtras.steps(player) * Footsteps.heardFloor(level, player) / range);
			}
		}
		return f;
	}

	// ---- losing track and searching ----------------------------------------------------------------------------------

	/** Counts the ticks a mob goes without seeing its target, and drops them when it has gone too long. Runs beside everything. */
	static final class WatchGoal extends Goal {
		private final Mob mob;
		private final Track track;

		WatchGoal(Mob mob, Track track) {
			this.mob = mob;
			this.track = track;
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.mob.getTarget();
			if (target != null) {
				this.track.heedLeft = 0;
				this.track.returning = false;
			} else if (this.track.heedLeft > 0) {
				this.track.heedLeft--;
			}
			if (!this.track.losesTrack) {
				// (a camp's outlaw: it hears and searches, but keeps after its foe)
				if (target != null) {
					this.track.searching = false;
				} else if (this.track.searching || this.track.returning) {
					search();
				}
				return;
			}
			if (target instanceof ServerPlayer player && !touched(player)) {
				this.track.searching = false;
				this.track.lure = 0.0;
				if (this.track.who == null || !this.track.who.equals(player.getUUID())) {
					// (a new target: where it is now counts as seen, whether or not the mob has a clear line to it yet)
					this.track.who = player.getUUID();
					this.track.seen = player.position();
					this.track.blind = 0;
				}
				Awareness.called(this.mob, player);
				if (this.mob.tickCount % 2 != 0) {
					return;
				}
				if (this.mob.hasLineOfSight(player)) {
					this.track.blind = 0;
					this.track.who = player.getUUID();
					this.track.seen = player.position();
					return;
				}
				this.track.blind += 2;
				int limit = LOSE_TRACK_TICKS / (Skills.rank(player, Skills.Perk.SLIP_THE_NET) > 0 ? SLIP_THE_NET_DIVISOR : 1);
				if (this.track.blind >= limit && this.track.seen != null && this.track.who != null && this.track.who.equals(player.getUUID())) {
					lose(player);
				}
				return;
			}
			this.track.blind = 0;
			if (target != null) {
				this.track.searching = false;
			}
			if (this.track.searching || this.track.returning) {
				search();
			}
		}

		/** Drops {@code player}; the mob goes to where it last saw them. */
		private void lose(ServerPlayer player) {
			this.mob.setTarget(null);
			if (this.mob instanceof NeutralMob neutral) {
				neutral.stopBeingAngry();
			}
			this.mob.setLastHurtByMob(null);
			this.mob.getNavigation().stop();
			this.track.blind = 0;
			this.track.searching = true;
			this.track.started = false;
			this.track.spot = this.track.seen;
			this.track.walkLeft = WALK_TICKS;
			this.track.lookLeft = -1;
			this.track.lure = LOST_LURE;
			this.track.home = null;
			this.track.returning = false;
		}

		/** The walk and the looking round count down here, so that a goal that outranks the search can't make it last for ever. */
		private void search() {
			if (this.track.returning) {
				Vec3 home = this.track.home;
				if (home == null || --this.track.walkLeft <= 0 || this.mob.position().distanceToSqr(home) < ARRIVED * ARRIVED) {
					this.track.returning = false;
					this.track.home = null;
				}
				return;
			}
			if (this.track.lookLeft < 0) {
				boolean near = this.track.spot == null || this.mob.position().distanceToSqr(this.track.spot) < ARRIVED * ARRIVED;
				if (--this.track.walkLeft <= 0 || near || this.track.started && this.mob.getNavigation().isDone() && this.mob.tickCount % 10 == 0) {
					this.track.lookLeft = LOOK_TICKS;
				}
			} else if (--this.track.lookLeft <= 0) {
				this.track.searching = false;
				this.track.spot = null;
				this.track.lure = 0.0;
				// (Hearing) drawn off by a sound, it goes back to where it was
				Vec3 home = this.track.home;
				if (home != null && this.mob.position().distanceToSqr(home) > RETURN_MIN * RETURN_MIN) {
					this.track.returning = true;
					this.track.walkLeft = WALK_TICKS;
				} else {
					this.track.home = null;
				}
			}
		}
	}

	/** Walks to where the target was last seen, then looks about. */
	static final class SearchGoal extends Goal {
		private final Mob mob;
		private final Track track;

		SearchGoal(Mob mob, Track track) {
			this.mob = mob;
			this.track = track;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return (this.track.searching && this.track.spot != null || this.track.returning && this.track.home != null) && this.mob.getTarget() == null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			this.track.started = true;
			Vec3 s = this.track.returning ? this.track.home : this.track.spot;
			if ((this.track.returning || this.track.lookLeft < 0) && s != null) {
				this.mob.getNavigation().moveTo(s.x, s.y, s.z, this.track.returning ? RETURN_SPEED : SEARCH_SPEED);
			}
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (this.track.returning) {
				Vec3 home = this.track.home;
				if (home != null && this.mob.tickCount % 20 == 0 && this.mob.getNavigation().isDone()) {
					this.mob.getNavigation().moveTo(home.x, home.y, home.z, RETURN_SPEED);
				}
				return;
			}
			Vec3 s = this.track.spot;
			if (s == null) {
				return;
			}
			if (this.track.lookLeft < 0) {
				if (this.mob.tickCount % 20 == 0 && this.mob.getNavigation().isDone()) {
					this.mob.getNavigation().moveTo(s.x, s.y, s.z, SEARCH_SPEED);
				}
				this.mob.getLookControl().setLookAt(s.x, s.y + 1.0, s.z);
				return;
			}
			this.mob.getNavigation().stop();
			if (this.mob.tickCount % 20 == 0) {
				double angle = this.mob.getRandom().nextDouble() * Mth.TWO_PI;
				this.mob.getLookControl().setLookAt(this.mob.getX() + Math.cos(angle) * LOOK_RADIUS, this.mob.getEyeY() + this.mob.getRandom().nextDouble() - 0.5,
					this.mob.getZ() + Math.sin(angle) * LOOK_RADIUS);
			}
		}
	}

	/** Stands still and looks toward a faint sound it heard (head turned to it, the body coming round), till it hears more or the moment passes. */
	static final class HeedGoal extends Goal {
		private final Mob mob;
		private final Track track;

		HeedGoal(Mob mob, Track track) {
			this.mob = mob;
			this.track = track;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.track.heedLeft > 0 && this.track.heedAt != null && !this.track.searching && this.mob.getTarget() == null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			Vec3 at = this.track.heedAt;
			if (at == null) {
				return;
			}
			this.mob.getNavigation().stop();
			this.mob.getLookControl().setLookAt(at.x, at.y + 1.0, at.z);
			// the body comes round to face it too
			this.mob.setYBodyRot(Mth.rotLerp(0.2F, this.mob.yBodyRot, this.mob.getYHeadRot()));
		}
	}

	/**
	 * {@code mob} heard something at {@code spot} ({@link Hearing}) as loud as {@code loudness}. A {@code faint} one (at the
	 * edge of its hearing) only makes it stop and look, unless it heard another near there a moment ago; anything else
	 * draws it there ({@link #lure}). Returns {@link #UNHEARD}, {@link #HEEDED} or {@link #LURED}.
	 */
	static int hear(Mob mob, Vec3 spot, @Nullable UUID who, double loudness, boolean faint) {
		Track t = TRACKS.get(mob);
		if (t == null || !mob.isAlive() || mob.getTarget() != null) {
			return UNHEARD;
		}
		long now = mob.level().getGameTime();
		boolean near = t.heedAt != null && now < t.heedUntil && t.heedAt.distanceToSqr(spot) < HEED_NEAR * HEED_NEAR;
		if (faint && !t.searching && near && now < t.heedSince + HEED_SECOND) {
			// (still listening: the steps it hears meanwhile only keep it listening, and it looks to the latest)
			t.heedAt = spot;
			t.heedLeft = Math.max(t.heedLeft, HEED_SECOND - (int) (now - t.heedSince));
			return HEEDED;
		}
		if (faint && !near && !t.searching) {
			t.heedAt = spot;
			t.heedWho = who;
			t.heedSince = now;
			t.heedLeft = HEED_TICKS;
			t.heedUntil = now + HEED_MEMORY;
			return HEEDED;
		}
		return lure(mob, spot, who, loudness) ? LURED : UNHEARD;
	}

	/** {@code mob} has stopped to listen for a sound {@code player} made. */
	static boolean heeding(Mob mob, Player player) {
		Track t = TRACKS.get(mob);
		return t != null && t.heedLeft > 0 && player.getUUID().equals(t.heedWho);
	}

	/**
	 * (0.7.155) {@code mob} heard, saw or smelled something at {@code spot} ({@link Awareness}) as loud as {@code loudness}, and
	 * goes to look, the same search as for a player it lost. Only when it is after no one, and only if it isn't already
	 * searching for something louder: mobs always go for the louder noise. {@code who}, if anyone, is the player it gave
	 * away (their gauge shows it searching, and it is unaware of them). False if it didn't turn.
	 */
	static boolean lure(Mob mob, Vec3 spot, @Nullable UUID who, double loudness) {
		Track t = TRACKS.get(mob);
		if (t == null || !mob.isAlive() || mob.getTarget() != null || t.searching && t.lure > loudness) {
			return false;
		}
		if (!t.searching && !t.returning) {
			// (where it was, to go back to after: Hearing)
			t.home = mob.position();
		}
		t.returning = false;
		t.heedLeft = 0;
		t.heedAt = null;
		t.searching = true;
		t.started = false;
		t.who = who;
		t.seen = spot;
		t.spot = spot;
		t.lure = loudness;
		t.walkLeft = Math.max(WALK_TICKS, (int) (mob.position().distanceTo(spot) * LURE_TICKS_PER_BLOCK));
		t.lookLeft = -1;
		mob.getNavigation().moveTo(spot.x, spot.y, spot.z, SEARCH_SPEED);
		return true;
	}

	/** {@code mob} is hunting for {@code player}, who slipped away. */
	static boolean searchingFor(Mob mob, Player player) {
		Track t = TRACKS.get(mob);
		return t != null && t.searching && player.getUUID().equals(t.who);
	}

	// ---- the tick: movement, the gauge ---------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		boolean gauge = server.getTickCount() % GAUGE_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Vec3 now = player.position();
			Vec3 was = LAST_POS.put(player.getUUID(), now);
			double dx = was == null ? 0.0 : now.x - was.x, dz = was == null ? 0.0 : now.z - was.z;
			MOVING.put(player.getUUID(), dx * dx + dz * dz > MOVING_SQR);
			if (gauge) {
				sync(player);
			}
		}
	}

	/** Works out what {@code player}'s gauge shows, and sends it if it changed. */
	private static void sync(ServerPlayer player) {
		int state = IDLE, fill = 0;
		// (0.7.136) how near the nearest monster in sight is, sneaking past it: 0 none, 1 near, 2 close
		int past = 0;
		boolean sneaking = player.isCrouching() && MOVING.getOrDefault(player.getUUID(), false);
		if (!touched(player) && player.isAlive() && player.level() instanceof ServerLevel level) {
			boolean feels = feelsStalkers(player);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(GAUGE_RANGE),
				m -> m.isAlive() && (sensing(m) || com.jaymoskingdoms.wild.WolfStalkers.after(m, player)))) {
				if (mob.getTarget() == player) {
					state = SPOTTED;
					break;
				}
				if (com.jaymoskingdoms.wild.WolfStalkers.stalking(mob, player)) {
					// a stalking beast keeps its interest to itself, unless the one it hunts can feel it: the eye fills as it creeps in
					if (feels && state <= NOTICING) {
						state = NOTICING;
						fill = Math.max(fill, Math.max(1, (int) Math.round(com.jaymoskingdoms.wild.WolfStalkers.closeness(mob, player, GAUGE_RANGE) * FILL_STEPS)));
					}
				} else if (searchingFor(mob, player)) {
					state = Math.max(state, SEARCHING);
				} else if (state <= NOTICING && heeding(mob, player)) {
					// (stopped to listen for you: the eye half fills)
					state = NOTICING;
					fill = Math.max(fill, FILL_STEPS / 2);
				} else if (mob instanceof Enemy) {
					if (state <= NOTICING) {
						int f = noticing(level, mob, player);
						if (f > 0) {
							state = NOTICING;
							fill = Math.max(fill, f);
						}
					}
					if (sneaking && past < 2 && mob.getTarget() == null) {
						double d = mob.distanceTo(player);
						if (d < SNEAK_PAST_RANGE && mob.hasLineOfSight(player)) {
							past = Math.max(past, d < SNEAK_PAST_CLOSE ? 2 : 1);
						}
					}
				}
			}
		}
		sneakPast(player, past, state == SPOTTED || state == SEARCHING);
		if (state != NOTICING) {
			fill = 0;
		}
		int packed = state << 4 | fill;
		Integer last = SENT.put(player.getUUID(), packed);
		if ((last == null ? 0 : last) != packed) {
			ServerPlayNetworking.send(player, new Gauge(state, fill));
		}
		meter(player);
	}

	/**
	 * Andy (2026-11-18): the predators that stalk you don't set off the eye "until your stealth is sufficient or a perk on
	 * tree": Stealth at {@link #FEEL_STALKERS_LEVEL}, or Sixth Sense.
	 */
	static boolean feelsStalkers(Player player) {
		return Skills.rank(player, Skills.Perk.SIXTH_SENSE) > 0 || Skills.of(player).level(Skills.Pillar.STEALTH) >= FEEL_STALKERS_LEVEL;
	}

	/** The stealth meter: how far {@code player}'s noise carries, how lit they are, whether they bleed or talk; sent when it changes. */
	private static void meter(ServerPlayer player) {
		Meter m;
		if (touched(player) || !player.isAlive()) {
			m = new Meter(0, 0, false, Hearing.QUIET);
		} else {
			BlockPos eyes = BlockPos.containing(player.getEyePosition());
			int light = Math.max(player.level().getMaxLocalRawBrightness(eyes), Awareness.heldLight(player));
			int noise = (int) Math.min(63L, Math.round(Hearing.noiseOf(player)));
			m = new Meter(noise, Mth.clamp(light, 0, 15), Awareness.bleeding(player), Hearing.voiceOf(player.getUUID()));
		}
		Integer last = METERED.put(player.getUUID(), m.packed());
		if (last == null || last != m.packed()) {
			ServerPlayNetworking.send(player, m);
		}
	}

	/**
	 * (0.7.136) Andy: "sneaking near enemies undetected should passively grow your stealth skill". Every gauge update
	 * {@code player} spends sneaking past an unaware monster ({@code past}: 1 near, 2 close) counts toward a little Stealth
	 * experience. Spotted or hunted, the count starts over.
	 */
	private static void sneakPast(ServerPlayer player, int past, boolean seen) {
		if (seen) {
			SNEAKING_PAST.remove(player.getUUID());
			return;
		}
		if (past <= 0) {
			return;
		}
		int ticks = SNEAKING_PAST.getOrDefault(player.getUUID(), 0) + GAUGE_TICKS * past;
		if (ticks >= SNEAK_PAST_TICKS) {
			ticks -= SNEAK_PAST_TICKS;
			Skills.sneakPast(player);
		}
		SNEAKING_PAST.put(player.getUUID(), ticks);
	}

	/** 0 if {@code mob} takes no notice of {@code player}; else 1 to {@link #FILL_STEPS}, as they come from where it could just see them to where it would spot them. */
	private static int noticing(ServerLevel level, Mob mob, ServerPlayer player) {
		double follow = mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
		// (0.7.600) a stalking wolf keeps its interest to itself
		if (mob.getTarget() != null || mob.distanceTo(player) >= follow || !mob.hasLineOfSight(player) || com.jaymoskingdoms.wild.WolfStalkers.stalking(mob)) {
			return 0;
		}
		double spot = Math.max(2.0, follow * player.getVisibilityPercent(level, mob));
		double dist = mob.distanceTo(player);
		if (dist >= follow || spot >= follow) {
			return dist < spot ? FILL_STEPS : 0;
		}
		double share = dist <= spot ? 1.0 : (follow - dist) / (follow - spot);
		return Math.max(1, (int) Math.round(share * FILL_STEPS));
	}

	// ---- unaware blows -------------------------------------------------------------------------------------------------

	/**
	 * {@code target} is a hostile or neutral mob that isn't after {@code player} and can't see them: they're behind it, or it
	 * is searching. A boss only while it is after no one at all (the dragon, which never targets anyone, only while perched);
	 * a tame pet (a wolf: other pets aren't hostile or neutral) never of its own owner.
	 */
	public static boolean unaware(LivingEntity target, ServerPlayer player) {
		if (!(target instanceof Mob mob) || !sensing(mob) || mob instanceof TamableAnimal tame && tame.isOwnedBy(player) || !mob.isAlive()
			|| mob.getTarget() == player || com.jaymoskingdoms.wild.WolfStalkers.stalking(mob, player) || boss(mob) && mob.getTarget() != null
			|| mob instanceof EnderDragon dragon && !(dragon.getPhaseManager().getCurrentPhase() instanceof AbstractDragonSittingPhase)) {
			return false;
		}
		return searchingFor(mob, player) || !inCone(mob, player);
	}

	/** {@code target} is still in its hurt cooldown and would turn this blow away (vanilla's {@code hurtServer}): no bonus, no feedback. */
	static boolean cooling(LivingEntity target, DamageSource source) {
		return target.getInvulnerableTime() > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN);
	}

	/**
	 * What an unaware blow of {@code blow} deals {@code target}: on a boss, no more than {@link #BOSS_BLOW_SHARE} of its health,
	 * never less than {@link #BOSS_BLOW_MIN_MULT} times {@code plain} (more than the plain blow, so the hurt mixin still sees
	 * the hidden blade's assassination as its own and doesn't add a sneak attack on top).
	 */
	static float unawareBlow(LivingEntity target, float plain, float blow) {
		if (!(target instanceof Mob mob) || !boss(mob)) {
			return blow;
		}
		return Math.max(plain * BOSS_BLOW_MIN_MULT, Math.min(blow, mob.getMaxHealth() * BOSS_BLOW_SHARE));
	}

	/** Whether {@code player}'s hidden blade can assassinate {@code target} (Assassinate, and it is unaware of them). */
	static boolean assassinable(LivingEntity target, ServerPlayer player) {
		return Skills.rank(player, Skills.Perk.ASSASSINATE) > 0 && unaware(target, player);
	}

	/** Whether {@code player} strikes {@code target} from its rear ({@link #REAR_DOT}), flat, against where it looks. */
	static boolean fromRear(LivingEntity target, ServerPlayer player) {
		Vec3 to = player.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		Vec3 look = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		if (target instanceof EnderDragon) {
			// (the dragon's look runs backwards, as in inCone)
			look = look.scale(-1.0);
		}
		if (to.lengthSqr() < 1.0E-4 || look.lengthSqr() < 1.0E-4) {
			return false;
		}
		return to.normalize().dot(look.normalize()) <= REAR_DOT;
	}

	/**
	 * What a blow deals, from {@code amount}: a player's melee blow on a monster that is unaware of them is a sneak attack
	 * (Sneak Attack's {@link #SNEAK_ATTACK_RANK_MULT}, with the perk and from its rear, {@link #REAR_DOT}). Never on a player, or one of the Combat Arts' own blows.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		float mult = sneakAttack(target, source);
		if (mult <= 0.0F || !(source.getEntity() instanceof ServerPlayer sp)) {
			return amount;
		}
		long now = sp.level().getGameTime();
		long[] last = STRUCK.get(sp.getUUID());
		if (last != null && last[0] == target.getId() && last[1] == now) {
			return amount;
		}
		STRUCK.put(sp.getUUID(), new long[] {target.getId(), now});
		if (sp.level() instanceof ServerLevel level) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.25, 0.3, 0.25, 0.2);
		}
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.senses.sneak_attack").withStyle(ChatFormatting.GRAY));
		Skills.sneakAttack(sp, now);
		// (0.7.514) Ambusher and Cutthroat
		Bridges.sneakAttack(sp, target);
		return unawareBlow(target, amount, amount * mult);
	}

	/**
	 * What {@link #dealt} would multiply this blow by as a sneak attack, or 0 if it isn't one. Changes nothing, so it can be
	 * asked earlier in the same blow ({@link ThiefSneak}, whose burst doesn't stack with it).
	 */
	static float sneakAttack(LivingEntity target, DamageSource source) {
		if (CombatArts.striking() || target instanceof Player || !(source.getEntity() instanceof ServerPlayer sp)
			|| source.getDirectEntity() != sp || !source.is(DamageTypes.PLAYER_ATTACK) || sp.isSpectator() || target == sp
			|| HiddenBlade.is(sp.getMainHandItem()) && !HiddenBlade.skilled(sp) || !unaware(target, sp)) {
			return 0.0F;
		}
		int rank = Skills.rank(sp, Skills.Perk.SNEAK_ATTACK);
		if (rank <= 0 || !fromRear(target, sp) || cooling(target, source)) {
			return 0.0F;
		}
		return SNEAK_ATTACK_RANK_MULT[Math.min(rank, SNEAK_ATTACK_RANK_MULT.length) - 1];
	}
}
