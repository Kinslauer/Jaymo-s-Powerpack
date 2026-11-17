package com.jaymoskingdoms;

import com.jaymoskingdoms.wild.SpiderMinds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hearing (the medieval picks' Mob Senses rows, written into our own senses; Mob Senses itself is not shipped). Every noise
 * the world makes ({@link Awareness}' game events, {@link Footsteps}, thrown things, guns, voices) comes through
 * {@link #noise}, and every listener near enough hears it with its own ears:
 * <ul>
 * <li><b>Who hears</b> ({@link #listens}): every monster that minds what it hears (skeletons, spiders, illagers, the zombies,
 * the night's creatures), a camp's outlaws (orcs, raiders, bandits: {@link Outlaws}), wolves and foxes
 * ({@link Footsteps#HEARS}) and guards; a few deaf kinds ({@link #DEAF}) don't, nor bosses.</li>
 * <li><b>Walls</b>: each listener's share comes through the blocks between ({@link SoundRay}): stone takes a lot, wool more,
 * wood and glass less, an open door nothing. The rays are cheap but not free: only listeners the sound could reach at all
 * are traced, the nearest {@link #LISTENERS_PER_SOUND}, and no more than {@link #RAYS_PER_TICK} a tick (the rest wait a
 * tick or two, and are dropped after {@link #PENDING_TICKS}).</li>
 * <li><b>Cover</b> ({@link #weather}, {@link #cover}): rain outdoors takes {@link #RAIN} off a sound's reach, a storm
 * {@link #STORM}; a loud noise ({@link #COVER_MIN} blocks or more: thunder, a bell, a blast, a gunshot) drowns quieter ones
 * near it for {@link #COVER_TICKS}.</li>
 * <li><b>What they do</b> ({@link #heard}): a faint sound at the edge of hearing ({@link #EDGE_SHARE} of its reach left)
 * only makes a monster stop and turn its head; a second brings it over ({@link Senses#hear}). One that comes calls its
 * friends near and they spread round the spot in a ring ({@link #ring}); a band's leader shouts it, and his reaches
 * further. Spiders cast about in the dark where it came from ({@link SpiderMinds#heard}); guards turn, wake or come
 * ({@link Footsteps#heard}).</li>
 * <li><b>Decoys</b> ({@link #decoy}): an item a player drops or tosses (coins too, {@link CoinToss}) makes a noise where it
 * lands: a bell carries {@link #DECOY_BELL}, iron and coin {@link #DECOY_METAL}, wool, feathers and cloth land soft
 * ({@link #DECOY_SOFT}). It pulls guards to it as well as monsters.</li>
 * <li><b>Guns</b> ({@link #GUNSHOTS}, and any of TaCZ's): a shot carries {@link #SHOT} blocks from the shooter (TaCZ's with a
 * suppressor fitted or built in, {@link #SUPPRESSED}; black powder is never quiet), and where the ball or bullet strikes,
 * {@link #IMPACT_MIN} or more from the muzzle, it makes {@link #IMPACT} more there: enough to lure a horde off.</li>
 * <li><b>Voices</b> ({@link #spoke}, from {@code VoiceRange} with Simple Voice Chat): what a player says is a noise from
 * their head, measured there against their own normal voice; at night guards hear trespassers by it too.</li>
 * </ul>
 * The stealth meter ({@link Senses#sync}) reads back how far the player's own noise carried ({@link #noiseOf}) and how
 * they last spoke ({@link #voiceOf}).
 */
public final class Hearing {
	// ---- tuning ----
	/** The kinds of noise. Urgent ones are never faint: a mob goes straight there. */
	public enum Kind {
		STEP(false), ACTION(false), DECOY(false), VOICE(false), SHOT(true), IMPACT(true), BLAST(true);

		final boolean urgent;

		Kind(boolean urgent) {
			this.urgent = urgent;
		}
	}

	/** The deaf: {@code jaymoskingdoms:deaf}. */
	public static final TagKey<EntityType<?>> DEAF = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("deaf"));
	/** Gun balls and bullets, and cannon shot: {@code jaymoskingdoms:gunshots} (and anything of TaCZ's). */
	public static final TagKey<EntityType<?>> GUNSHOTS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("gunshots"));

	/** Too faint to hear (what's left of a reach, blocks); a sound with less than this share of its reach left is at the edge of hearing. */
	static final double FAINTEST = 1.0, EDGE_SHARE = 0.3;
	/** The most listeners traced for one sound, and wall traces a tick; a queued trace older than this (ticks) is dropped. */
	static final int LISTENERS_PER_SOUND = 24, RAYS_PER_TICK = 48, PENDING_TICKS = 20, MAX_PENDING = 512;
	/** Nearer than this (blocks) no wall is traced. */
	static final double NO_RAY = 1.5;

	/** Rain outdoors leaves this share of a sound's reach, a thunderstorm this. */
	static final double RAIN = 0.75, STORM = 0.55;
	/** A sound this loud (reach, blocks) covers quieter ones for this long (ticks): those under {@link #COVER_SHARE} of it, within {@link #COVER_AREA} of its reach, carry {@link #COVERED} as far. */
	static final double COVER_MIN = 24.0, COVER_SHARE = 0.6, COVER_AREA = 0.5, COVERED = 0.5;
	static final int COVER_TICKS = 40, MAX_COVERS = 16;

	/** A dropped or tossed item where it lands (reach, blocks): a bell, metal and coin, anything else, soft things. */
	static final double DECOY_BELL = 40.0, DECOY_METAL = 20.0, DECOY_PLAIN = 10.0, DECOY_SOFT = 3.0;
	/** How long (ticks) a dropped item is watched for landing. */
	static final int DECOY_FLIGHT = 100;

	/** A shot (reach, blocks), a suppressed one, the ball's strike, and how far from the muzzle a strike must be to count. */
	static final double SHOT = 44.0, SUPPRESSED = 16.0, IMPACT = 20.0, IMPACT_MIN = 8.0;
	/** TaCZ guns with a suppressor built in, by gun id (a fitted muzzle is read from its attachment). */
	static final String[] SILENT_GUNS = {"vss", "as_val", "asval", "mp5sd", "honey_badger", "silenced", "suppressed"};

	/** Voices: sent into the world this often (ticks); the meter shows the last for this long (ms). */
	static final int VOICE_EVERY = 5;
	static final long VOICE_SHOWN_MS = 600L;
	/** The meter shows a player's loudest own noise of the last this many ticks. */
	static final int NOISE_SHOWN = 20;

	/**
	 * The ring: how far a mob's call carries to its friends (blocks; a leader's: {@link BandLeaders#BAND}), how many come, how
	 * wide they spread round the spot (growing a little with their number, never past {@link #RING_RADIUS_MAX}), how often
	 * one calls (ticks), and how long (ticks) one sent to its place in a ring keeps it against the same player's noises.
	 */
	static final double RING_RANGE = 12.0, RING_RADIUS = 2.5, RING_RADIUS_PER = 0.2, RING_RADIUS_MAX = 4.0, RING_LOUDNESS = 0.9;
	static final int RING_MAX = 6, RING_GAP = 200, RING_HOLD = 100;
	/** A band's "spread out" is shouted by its leader only, at most once a band in this long (ticks). */
	static final int SPREAD_GAP = 100;
	/** A ring place is moved to standable ground within this many blocks up or down, or else it is the spot itself. */
	static final int STAND_UP = 2, STAND_DOWN = 4;

	/** How a voice sounds, for the meter. */
	public static final int QUIET = 0, WHISPER = 1, TALK = 2, SHOUT = 3;

	/** A noise: where, how far it carries, whose (if anyone's), what kind, when (game time); and whether a ring was called on it. */
	static final class Sound {
		final ServerLevel level;
		final Vec3 at;
		final double reach;
		final @Nullable UUID who;
		final Kind kind;
		final long made;
		boolean rung;

		Sound(ServerLevel level, Vec3 at, double reach, @Nullable UUID who, Kind kind, long made) {
			this.level = level;
			this.at = at;
			this.reach = reach;
			this.who = who;
			this.kind = kind;
			this.made = made;
		}
	}

	/** A mob sent to its place in a ring: whose noise it was, and until when it keeps the place. */
	private record Held(UUID who, long until) {
	}

	private record Pending(Mob mob, Sound sound) {
	}

	private record Cover(Vec3 at, double reach, long until) {
	}

	/** Shots in flight: where they left the muzzle and whose. */
	private record Shot(Vec3 from, @Nullable UUID who) {
	}

	private static final Deque<Pending> PENDING = new ArrayDeque<>();
	private static final Map<ServerLevel, Deque<Cover>> COVERS = new WeakHashMap<>();
	/** Dropped items in the air → game time to stop watching. */
	private static final Map<ItemEntity, Long> FALLING = new WeakHashMap<>();
	private static final Map<Entity, Shot> SHOTS = new WeakHashMap<>();
	/** Strikes noted as their shot is taken out of the world, made into noise on the next tick. */
	private static final List<Sound> STRUCK = new ArrayList<>();
	private static final Map<Mob, Long> RANG = new WeakHashMap<>();
	private static final Map<Mob, Held> HELD = new WeakHashMap<>();
	/** A band's side → when its leader last shouted to spread out. */
	private static final Map<String, Long> SPREAD = new HashMap<>();
	/** Player → {reach x 10, game time} of their loudest recent noise. */
	private static final Map<UUID, long[]> NOISE = new HashMap<>();
	/** From Simple Voice Chat's thread: player → the farthest their voice carried since the last send (blocks). */
	private static final Map<UUID, Double> VOICES = new ConcurrentHashMap<>();
	/** Player → {how it sounded, wall clock ms}. */
	private static final Map<UUID, long[]> SPOKE = new ConcurrentHashMap<>();

	private Hearing() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Hearing::tick);
		ServerEntityEvents.ENTITY_LOAD.register(Hearing::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(Hearing::unloaded);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			NOISE.remove(id);
			VOICES.remove(id);
			SPOKE.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			COVERS.clear();
			FALLING.clear();
			SHOTS.clear();
			STRUCK.clear();
			RANG.clear();
			HELD.clear();
			SPREAD.clear();
			NOISE.clear();
			VOICES.clear();
			SPOKE.clear();
		});
	}

	// ---- who hears -----------------------------------------------------------------------------------------------------

	/** {@code mob} hears noises now: awake to them, not deaf, after no one. */
	static boolean listens(Mob mob) {
		if (!mob.isAlive() || mob.getTarget() != null || mob.isNoAi() || mob.getType().builtInRegistryHolder().is(DEAF)) {
			return false;
		}
		if (Footsteps.guard(mob) || mob instanceof Spider || mob.getType().builtInRegistryHolder().is(Footsteps.HEARS)) {
			return true;
		}
		if (mob instanceof Folk folk) {
			return Outlaws.is(folk);
		}
		return (mob instanceof Enemy || mob.getType().builtInRegistryHolder().is(Awareness.AWARE)) && Senses.minded(mob);
	}

	private static boolean silent(ServerPlayer player) {
		return player.isCreative() || player.isSpectator() || !player.isAlive();
	}

	// ---- a noise -------------------------------------------------------------------------------------------------------

	/**
	 * A noise at {@code at} carrying {@code reach} blocks (before walls and cover), {@code who}'s if anyone's: every listener
	 * it could reach is queued to hear it through the walls.
	 */
	public static void noise(ServerLevel level, Vec3 at, double reach, @Nullable UUID who, Kind kind) {
		if (reach < FAINTEST) {
			return;
		}
		long now = level.getGameTime();
		double r = cover(level, at, reach * weather(level, at), now);
		if (who != null && kind != Kind.DECOY && kind != Kind.IMPACT) {
			long[] was = NOISE.get(who);
			if (was == null || now - was[1] > NOISE_SHOWN || was[0] <= r * 10.0) {
				NOISE.put(who, new long[] {Math.round(r * 10.0), now});
			}
		}
		if (r < FAINTEST) {
			return;
		}
		double r2 = r * r;
		List<Mob> heard = level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(r), m -> m.distanceToSqr(at) < r2 && listens(m));
		if (heard.size() > LISTENERS_PER_SOUND) {
			heard.sort(Comparator.comparingDouble(m -> m.distanceToSqr(at)));
			heard = heard.subList(0, LISTENERS_PER_SOUND);
		}
		Sound sound = new Sound(level, at, r, who, kind, now);
		for (Mob mob : heard) {
			if (PENDING.size() >= MAX_PENDING) {
				PENDING.pollFirst();
			}
			PENDING.addLast(new Pending(mob, sound));
		}
	}

	/** What share of a sound's reach is left at {@code at} by the weather: rain outdoors muffles, a storm more. */
	static double weather(ServerLevel level, Vec3 at) {
		if (!level.isRaining()) {
			return 1.0;
		}
		BlockPos pos = BlockPos.containing(at.x, at.y + 0.5, at.z);
		if (!level.isRainingAt(pos) && !level.isRainingAt(pos.above())) {
			return 1.0;
		}
		return level.isThundering() ? STORM : RAIN;
	}

	/** A quieter noise under a recent loud one near it is drowned; a loud one covers what follows it. */
	private static double cover(ServerLevel level, Vec3 at, double reach, long now) {
		Deque<Cover> covers = COVERS.computeIfAbsent(level, l -> new ArrayDeque<>());
		covers.removeIf(c -> c.until <= now);
		double r = reach;
		for (Cover c : covers) {
			double area = c.reach * COVER_AREA;
			if (reach < c.reach * COVER_SHARE && c.at.distanceToSqr(at) < area * area) {
				r = reach * COVERED;
				break;
			}
		}
		if (reach >= COVER_MIN) {
			covers.addLast(new Cover(at, reach, now + COVER_TICKS));
			while (covers.size() > MAX_COVERS) {
				covers.removeFirst();
			}
		}
		return r;
	}

	// ---- the tick: queued listeners, falling decoys, voices ----------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!STRUCK.isEmpty()) {
			List<Sound> struck = List.copyOf(STRUCK);
			STRUCK.clear();
			for (Sound s : struck) {
				noise(s.level, s.at, s.reach, s.who, s.kind);
			}
		}
		int rays = 0;
		while (!PENDING.isEmpty() && rays < RAYS_PER_TICK) {
			Pending p = PENDING.pollFirst();
			Sound s = p.sound;
			Mob mob = p.mob;
			if (mob.isRemoved() || mob.level() != s.level || s.level.getGameTime() - s.made > PENDING_TICKS || !listens(mob)) {
				continue;
			}
			Vec3 ears = mob.getEyePosition();
			double d = ears.distanceTo(s.at);
			if (s.reach - d < FAINTEST) {
				continue;
			}
			double through = 1.0;
			if (d > NO_RAY) {
				through = SoundRay.through(s.level, s.at, ears);
				rays++;
			}
			double loudness = s.reach * through - d;
			if (loudness >= FAINTEST) {
				heard(mob, s, loudness);
			}
		}
		if (!FALLING.isEmpty()) {
			falling();
		}
		if (!VOICES.isEmpty() && server.getTickCount() % VOICE_EVERY == 0) {
			voices(server);
		}
	}

	/** {@code mob} heard {@code s} as loud as {@code loudness} (blocks of its reach left where it stands). */
	private static void heard(Mob mob, Sound s, double loudness) {
		ServerLevel level = s.level;
		Held held = HELD.get(mob);
		if (held != null) {
			if (s.made < held.until && s.who != null && s.who.equals(held.who)) {
				// sent to its place in a ring round this player's noise: it keeps it, and calls no ring of its own
				return;
			}
			if (s.made >= held.until) {
				HELD.remove(mob);
			}
		}
		ServerPlayer player = s.who == null ? null : level.getServer().getPlayerList().getPlayer(s.who);
		boolean animal = mob.getType().builtInRegistryHolder().is(Footsteps.HEARS);
		if (Footsteps.guard(mob) || animal && mob instanceof TamableAnimal tame && tame.isTame()) {
			Footsteps.heard(level, mob, player, s.at, s.reach, loudness, s.kind);
			return;
		}
		if (mob instanceof Spider spider) {
			if (SpiderMinds.heard(spider, s.at) && s.who != null) {
				ring(level, mob, s, loudness);
			}
			return;
		}
		if (animal) {
			Footsteps.heard(level, mob, player, s.at, s.reach, loudness, s.kind);
		}
		boolean faint = !s.kind.urgent && loudness < s.reach * EDGE_SHARE;
		if (Senses.hear(mob, s.at, s.who, loudness, faint) == Senses.LURED && s.who != null) {
			ring(level, mob, s, loudness);
		}
	}

	// ---- the ring ------------------------------------------------------------------------------------------------------

	/**
	 * {@code caller} heard {@code s} (a player's) and goes to look: it calls (its own cry, or a band leader's shout) and the
	 * idle friends near it come too, spread round the spot in a ring instead of all on the one block. One ring a sound: the
	 * first to hear it calls, and those it sends keep their places ({@link #HELD}) against what they hear of it themselves.
	 */
	static void ring(ServerLevel level, Mob caller, Sound s, double loudness) {
		long now = level.getGameTime();
		Long last = RANG.get(caller);
		UUID who = s.who;
		if (s.rung || who == null || last != null && now - last < RING_GAP) {
			return;
		}
		Vec3 spot = s.at;
		RANG.put(caller, now);
		double range = BandLeaders.isLeader(caller) ? BandLeaders.BAND : RING_RANGE;
		String side = Comrades.side(caller);
		List<Mob> friends = new ArrayList<>(level.getEntitiesOfClass(Mob.class, caller.getBoundingBox().inflate(range),
			m -> m != caller && m.distanceToSqr(caller) < range * range && listens(m) && !Footsteps.guard(m) && friends(caller, side, m)));
		if (friends.isEmpty()) {
			return;
		}
		friends.sort(Comparator.comparingDouble(m -> m.distanceToSqr(caller)));
		if (friends.size() > RING_MAX) {
			friends = friends.subList(0, RING_MAX);
		}
		s.rung = true;
		// a band leader's shout (once a band in a while), or the mob's own cry
		Long spread = SPREAD.get(side);
		if (BandLeaders.isLeader(caller) && (spread == null || now - spread >= SPREAD_GAP) && Callouts.say(caller, "lead.spread", null, true)) {
			SPREAD.put(side, now);
		} else {
			caller.playAmbientSound();
		}
		int n = friends.size() + 1;
		double base = Math.atan2(caller.getZ() - spot.z, caller.getX() - spot.x);
		for (int i = 0; i < friends.size(); i++) {
			Mob friend = friends.get(i);
			Vec3 at = ringSpot(level, spot, base, i + 1, n, RING_RADIUS_MAX);
			boolean went = friend instanceof Spider spider ? SpiderMinds.heard(spider, at) : Senses.lure(friend, at, who, loudness * RING_LOUDNESS);
			if (went) {
				hold(friend, who, now);
			}
		}
	}

	/** {@code mob} was sent to its place in a ring round {@code who}'s noise: it keeps it a while. */
	static void hold(Mob mob, UUID who, long now) {
		HELD.put(mob, new Held(who, now + RING_HOLD));
	}

	/**
	 * The {@code i}th of {@code n} places round {@code spot}, the first ({@code i} 0) at angle {@code base}, no further out
	 * than {@code most}, on standable ground near it (else the spot itself).
	 */
	static Vec3 ringSpot(ServerLevel level, Vec3 spot, double base, int i, int n, double most) {
		double a = base + Mth.TWO_PI * i / n;
		double r = Math.min(most, RING_RADIUS + RING_RADIUS_PER * n);
		double x = spot.x + Math.cos(a) * r, z = spot.z + Math.sin(a) * r;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int y0 = Mth.floor(spot.y);
		for (int k = 0; k <= STAND_UP + STAND_DOWN; k++) {
			// (0, +1, -1, +2, -2, ...: the nearest height first)
			int dy = (k & 1) == 1 ? (k + 1) / 2 : -(k / 2);
			if (dy > STAND_UP || -dy > STAND_DOWN) {
				continue;
			}
			pos.set(Mth.floor(x), y0 + dy, Mth.floor(z));
			if (standable(level, pos)) {
				return new Vec3(x, pos.getY(), z);
			}
		}
		return spot;
	}

	/** A mob can stand at {@code pos}: open there and above, solid ground below, no fire or lava. */
	private static boolean standable(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		net.minecraft.world.level.block.state.BlockState floor = level.getBlockState(below);
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& level.getFluidState(pos).isEmpty() && !floor.getCollisionShape(level, below).isEmpty()
			&& !floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) && !level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE);
	}

	/** {@code m} is {@code caller}'s friend: of its side, or both plain monsters. */
	private static boolean friends(Mob caller, String side, Mob m) {
		return Comrades.side(m).equals(side) || caller instanceof Enemy && m instanceof Enemy && !(caller instanceof Folk) && !(m instanceof Folk);
	}

	// ---- decoys --------------------------------------------------------------------------------------------------------

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity instanceof ItemEntity item) {
			if (item.getOwner() instanceof ServerPlayer player && !silent(player) && !item.onGround()) {
				FALLING.put(item, level.getGameTime() + DECOY_FLIGHT);
			}
			return;
		}
		if (entity instanceof Projectile shot && gun(shot)) {
			fired(level, shot);
		}
	}

	/**
	 * {@code shot} (a ball, a bullet, cannon shot) has just left the muzzle, where it is now: the bang, heard far ({@link #SHOT},
	 * a suppressed TaCZ gun's {@link #SUPPRESSED}), and the shot watched for where it strikes. Once a shot: a gun that fires
	 * something not tagged ({@link #GUNSHOTS}), such as a musket's arrow without WeaponMod, calls this itself.
	 */
	public static void fired(ServerLevel level, Entity shot) {
		if (SHOTS.containsKey(shot)) {
			return;
		}
		Entity owner = shot instanceof Projectile p ? p.getOwner() : null;
		if (owner instanceof ServerPlayer player && silent(player)) {
			return;
		}
		if (owner instanceof LivingEntity shooter && Workshop.muffled(shooter.getMainHandItem())) {
			// (class trees) a Muffled barrel: nothing to hear
			return;
		}
		Vec3 from = shot.position();
		UUID who = owner instanceof ServerPlayer ? owner.getUUID() : null;
		SHOTS.put(shot, new Shot(from, who));
		noise(level, from, owner instanceof LivingEntity shooter && suppressed(shooter.getMainHandItem()) ? SUPPRESSED : SHOT, who, Kind.SHOT);
	}

	/** {@code shot} struck at {@code at}: if far enough from the muzzle, a noise there (next tick). */
	public static void struck(Entity shot, Vec3 at) {
		Shot s = SHOTS.remove(shot);
		if (s != null && shot.level() instanceof ServerLevel level && at.distanceToSqr(s.from) >= IMPACT_MIN * IMPACT_MIN) {
			STRUCK.add(new Sound(level, at, IMPACT, s.who, Kind.IMPACT, level.getGameTime()));
		}
	}

	/** {@code shot} spent itself in the air (flew its full way): no strike. */
	public static void spent(Entity shot) {
		SHOTS.remove(shot);
	}

	private static void unloaded(Entity entity, ServerLevel level) {
		if (SHOTS.isEmpty() || !SHOTS.containsKey(entity)) {
			return;
		}
		Entity.RemovalReason why = entity.getRemovalReason();
		if (why != null && why.shouldDestroy()) {
			// where the ball or bullet struck (heard next tick, out of the middle of the world taking it away)
			struck(entity, entity.position());
		} else {
			SHOTS.remove(entity);
		}
	}

	/** Dropped items still in the air: the ones down make their noise. */
	private static void falling() {
		for (Iterator<Map.Entry<ItemEntity, Long>> it = FALLING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<ItemEntity, Long> e = it.next();
			ItemEntity item = e.getKey();
			if (item.isRemoved() || !(item.level() instanceof ServerLevel level) || level.getGameTime() > e.getValue()) {
				it.remove();
				continue;
			}
			if (item.onGround() || item.isInWater()) {
				it.remove();
				Entity owner = item.getOwner();
				noise(level, item.position(), decoy(item.getItem()), owner == null ? null : owner.getUUID(), Kind.DECOY);
			}
		}
	}

	/** How far {@code stack} is heard landing: a bell far, iron and coin well, soft things hardly. */
	static double decoy(ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.BELL) {
			return DECOY_BELL;
		}
		if (stack.is(ItemTags.WOOL) || stack.is(ItemTags.WOOL_CARPETS) || item == Items.FEATHER || item == Items.STRING || item == Items.LEATHER
			|| item == Items.PAPER || item == Items.RABBIT_HIDE || item == Items.WHEAT) {
			return DECOY_SOFT;
		}
		if (item instanceof BlockItem block) {
			SoundType s = block.getBlock().defaultBlockState().getSoundType();
			if (s == SoundType.WOOL || s == SoundType.MOSS_CARPET) {
				return DECOY_SOFT;
			}
			if (s == SoundType.METAL || s == SoundType.ANVIL || s == SoundType.CHAIN || s == SoundType.IRON || s == SoundType.COPPER
				|| s == SoundType.LANTERN || s == SoundType.NETHERITE_BLOCK) {
				return DECOY_METAL;
			}
		}
		if (CoinToss.isCoin(stack) || Currency.byItem(item) != null) {
			return DECOY_METAL;
		}
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		for (String metal : new String[] {"iron", "chain", "copper", "steel", "bronze", "netherite", "silver", "anvil", "bell"}) {
			if (path.contains(metal)) {
				return DECOY_METAL;
			}
		}
		return DECOY_PLAIN;
	}

	// ---- guns ----------------------------------------------------------------------------------------------------------

	/** A gun's ball or bullet, or cannon shot. */
	static boolean gun(Entity e) {
		if (e.getType().builtInRegistryHolder().is(GUNSHOTS) || SHOTS.containsKey(e)) {
			return true;
		}
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
		return "tacz".equals(id.getNamespace());
	}

	/** {@code held} is a TaCZ gun with a suppressor fitted or built in. */
	static boolean suppressed(ItemStack held) {
		if (held.isEmpty() || !"tacz".equals(BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace())) {
			return false;
		}
		CustomData data = held.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		String tag = data.copyTag().toString().toLowerCase(Locale.ROOT);
		if (tag.contains("silencer") || tag.contains("suppressor")) {
			return true;
		}
		String gun = data.copyTag().getStringOr("GunId", "").toLowerCase(Locale.ROOT);
		for (String silent : SILENT_GUNS) {
			if (gun.contains(silent)) {
				return true;
			}
		}
		return false;
	}

	// ---- voices --------------------------------------------------------------------------------------------------------

	/**
	 * From Simple Voice Chat's thread ({@code VoiceRange}): {@code who} spoke, their voice carrying {@code range} blocks,
	 * sounding as {@code how} ({@link #WHISPER}, {@link #TALK}, {@link #SHOUT}).
	 */
	public static void spoke(UUID who, double range, int how) {
		VOICES.merge(who, range, Math::max);
		SPOKE.put(who, new long[] {how, System.currentTimeMillis()});
	}

	private static void voices(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Double>> it = VOICES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Double> e = it.next();
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player != null && !silent(player) && player.level() instanceof ServerLevel level) {
				noise(level, player.getEyePosition(), e.getValue(), player.getUUID(), Kind.VOICE);
			}
		}
	}

	// ---- the meter -----------------------------------------------------------------------------------------------------

	/** How far {@code player}'s own loudest noise of the last moment carried (blocks), 0 if none. */
	static double noiseOf(ServerPlayer player) {
		long[] n = NOISE.get(player.getUUID());
		return n == null || player.level().getGameTime() - n[1] > NOISE_SHOWN ? 0.0 : n[0] / 10.0;
	}

	/** How {@code player} last spoke, if just now: {@link #QUIET} otherwise. */
	static int voiceOf(UUID player) {
		long[] s = SPOKE.get(player);
		return s == null || System.currentTimeMillis() - s[1] > VOICE_SHOWN_MS ? QUIET : (int) s[0];
	}
}
