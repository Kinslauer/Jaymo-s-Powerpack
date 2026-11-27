package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * It walks while you sleep. A host it barely trusts is left to sleep alone: a little after they lie down the symbiote
 * slips out of the body as the blob it was in the crater ({@link WebSymbioteBlob}, for real, in the world), roams and
 * hunts hostile mobs round the bed (small animals when there's nothing else), and is back inside by dawn; the morning
 * says what it killed. While it's out the host is unsuited (no powers: {@link WebSymbioteBond#dormant}).
 * <p>
 * How the night goes by decides how far it gets. With Kingdoms' timelapse (the world ticking five times as fast, the
 * clock six times as fast again) the blob has a whole night of real ticks and really goes round. With vanilla's
 * instant skip the clock jumps a whole night in one tick, so the ticks it had aren't enough: the hours skipped are
 * made up in hops ({@link #HOP_TICKS}), each one the blob actually going to the nearest quarry (a move in the world,
 * with a kill) a moment apart, before it comes home.
 * <p>
 * If the host wakes early (or dawn comes) it hurries back at {@link #HURRY_SPEED}, and past {@link #RETURN_TICKS}
 * it's simply in, however far it was.
 */
final class WebSymbioteSleep {
	// ---- tuning: whether it goes ----
	/** Trust (0-100) it needs to be at or under to leave the host alone; then the chance each night. */
	static final int LOW_TRUST = 30;
	static final float WALK_CHANCE = 0.8F;
	/** The host has lain in bed this long (ticks) before it slips out. */
	static final int LIE_TICKS = 24;
	// ---- tuning: the hunt ----
	/** It sees prey this far from itself, and never roams further than this from the bed. */
	static final double HUNT_RANGE = 40.0, ROAM_RADIUS = 56.0;
	/** Near enough to strike, how often (ticks), how hard; and its speed on the chase. */
	static final double REACH = 2.2, HUNT_SPEED = 1.9;
	static final int LASH_EVERY = 14;
	static final float LASH_DAMAGE = 14.0F;
	/** It won't take on anything with more health than this (no bosses). */
	static final float MAX_PREY_HEALTH = 60.0F;
	/** A small animal: no wider than this and no more health than this. */
	static final float SMALL_WIDTH = 0.7F, SMALL_HEALTH = 10.0F;
	/** It looks about this often (ticks), wanders to a new spot this often when nothing's near, and drops a quarry it can't reach after this long. */
	static final int SCAN_EVERY = 8, IDLE_EVERY = 70, PATIENCE = 200;
	static final double WANDER_RADIUS = 24.0;
	/** It's out no longer than this (ticks), whatever the sky does. */
	static final int MAX_OUT = 16000;
	// ---- tuning: the skipped hours ----
	/** The clock moving more than this in one tick is a skip, not a timelapse (whose clock runs 6 a tick). */
	static final long JUMP_MIN = 200L;
	/** One hop for every this many ticks skipped (at least one, at most {@code HOPS_MAX}), a few ticks apart, reaching this far. */
	static final int HOP_TICKS = 2000, HOPS_MAX = 6, HOP_GAP = 7;
	static final double HOP_RANGE = 56.0;
	// ---- tuning: coming home ----
	static final double HURRY_SPEED = 2.6, TUCK_REACH = 1.8;
	static final int RETURN_TICKS = 100;
	static final int OUT_LINES = 3, EARLY_LINES = 2, BACK_LINES = 2;

	/** The host's symbiote is out walking (synced to the owner: it blanks the powers on the client too). */
	static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> OUT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_out"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** One host's night. */
	private static final class Walk {
		UUID blob;
		BlockPos bed;
		long since, returnAt = -1L, lashAt, nextHop, lastClock;
		boolean returning;
		int hops, kills, flags;
		/** The quarry now, since when, and those it couldn't get at. */
		@Nullable UUID prey;
		long preySince;
		final Set<UUID> skipped = new HashSet<>();
		final Map<EntityType<?>, Integer> kinds = new LinkedHashMap<>();
	}

	private static final Map<UUID, Walk> WALKS = new HashMap<>();
	/** Hosts already rolled for this sleep. */
	private static final Set<UUID> ROLLED = new HashSet<>();

	private WebSymbioteSleep() {
	}

	static void register() {
		// a host who dies takes the walker with them (WebSymbioteLife makes the one blob that holds their things)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && WALKS.containsKey(player.getUUID())) {
				recall(player);
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			recall(handler.player);
			ROLLED.remove(handler.player.getUUID());
		});
	}

	/** Its symbiote is out walking (either side). */
	public static boolean out(net.minecraft.world.entity.player.Player player) {
		return player.getAttached(OUT) != null;
	}

	/**
	 * Every tick for a bonded host (from {@code WebSymbiote.tick}). True while it's out: nothing else of the symbiote runs.
	 */
	static boolean tick(ServerPlayer player, WebSymbioteState state, long now) {
		UUID id = player.getUUID();
		Walk walk = WALKS.get(id);
		if (walk == null) {
			if (!player.isSleeping()) {
				ROLLED.remove(id);
				return false;
			}
			if (player.getSleepTimer() >= LIE_TICKS && ROLLED.add(id) && goes(player, state)) {
				begin(player, state, now);
				return true;
			}
			return false;
		}
		ServerLevel level = player.level();
		Entity found = level.getEntity(walk.blob);
		WebSymbioteBlob blob = found instanceof WebSymbioteBlob b ? b : null;
		// the clock jumped: a skipped night, made up in hops
		long clock = level.getOverworldClockTime();
		long jump = clock - walk.lastClock;
		walk.lastClock = clock;
		if (jump >= JUMP_MIN) {
			walk.hops = Math.min(HOPS_MAX, walk.hops + Math.max(1, (int)(jump / HOP_TICKS)));
		}
		if (walk.hops > 0 && now >= walk.nextHop) {
			walk.nextHop = now + HOP_GAP;
			walk.hops--;
			if (blob != null) {
				hop(level, walk, blob);
			}
		}
		if (!walk.returning && walk.hops == 0 && (!player.isSleeping() || !level.isDarkOutside() || now - walk.since > MAX_OUT)) {
			walk.returning = true;
			walk.returnAt = now;
			if (!player.isSleeping() && blob != null && blob.distanceTo(player) > TUCK_REACH * 4.0) {
				WebSymbiote.whisper(player, "sleep.early." + (1 + player.getRandom().nextInt(EARLY_LINES)));
			}
		}
		if (walk.returning && walk.hops == 0
			&& (blob == null || blob.distanceTo(player) <= TUCK_REACH || now - walk.returnAt >= RETURN_TICKS)) {
			finish(player, level, walk, blob);
			return false;
		}
		return true;
	}

	/** Barely trusted, awake enough to be left, and the night's a night. */
	private static boolean goes(ServerPlayer player, WebSymbioteState state) {
		return WebSymbioteMind.mind(player).trust() <= LOW_TRUST && player.getRandom().nextFloat() < WALK_CHANCE
			&& !player.isCreative() && !player.isSpectator() && player.isAlive() && player.level().isDarkOutside()
			&& !WebSymbioteBond.dormant(player) && !WebSymbiote.driving(player) && state.bonded();
	}

	private static void begin(ServerPlayer player, WebSymbioteState state, long now) {
		ServerLevel level = player.level();
		WebSymbioteBlob blob = WebSymbioteBlob.release(level, player.position().add(0.0, 0.1, 0.0));
		WebSymbioteStrains.mark(blob, state.kind());
		blob.sleepwalk(player.getUUID());
		Walk walk = new Walk();
		walk.blob = blob.getUUID();
		walk.bed = player.blockPosition();
		walk.since = now;
		walk.lastClock = level.getOverworldClockTime();
		walk.flags = state.flags() & WebSymbioteState.SUIT;
		WALKS.put(player.getUUID(), walk);
		// the body is left bare and still: no suit, no armour, no powers till it's back
		WebTendrils.releaseAll(player, false);
		WebSymbiote.removeArmor(player);
		WebSymbiote.set(player, state.withFlag(WebSymbioteState.SUIT, false).withFlag(WebSymbioteState.ARMOR, false).withFlag(WebSymbioteState.BULK, false));
		player.setAttached(OUT, Boolean.TRUE);
		WebSymbiote.whisper(player, "sleep.out." + (1 + player.getRandom().nextInt(OUT_LINES)));
	}

	/** Back in: the blob goes into the host, the suit comes back as it was (not the armour or the big body), and the morning's word. */
	private static void finish(ServerPlayer player, ServerLevel level, Walk walk, @Nullable WebSymbioteBlob blob) {
		UUID id = player.getUUID();
		WALKS.remove(id);
		player.removeAttached(OUT);
		if (blob != null) {
			level.sendParticles(ParticleTypes.SQUID_INK, blob.getX(), blob.getY() + 0.3, blob.getZ(), 20, 0.3, 0.2, 0.3, 0.06);
			blob.discard();
		}
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.8, player.getZ(), 24, 0.3, 0.5, 0.3, 0.05);
		level.sendParticles(WebSymbioteStrains.dust(player, 1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.6, 0.3, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
		WebSymbioteState state = WebSymbiote.state(player);
		if (state.bonded() && (walk.flags & WebSymbioteState.SUIT) != 0) {
			WebSymbiote.set(player, state.withFlag(WebSymbioteState.SUIT, true));
		}
		int pick = 1 + player.getRandom().nextInt(BACK_LINES);
		if (walk.kills == 0) {
			WebSymbiote.whisper(player, "sleep.back.none." + pick);
			return;
		}
		EntityType<?> most = walk.kinds.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
		Component name = most == null ? Component.empty() : most.getDescription();
		if (walk.kills == 1) {
			WebSymbiote.whisper(player, "sleep.back.one." + pick, name);
		} else {
			WebSymbiote.whisper(player, "sleep.back.many." + pick, walk.kills, name);
		}
	}

	/** The host is gone (dead, left, not bonded): the walker goes into nothing. */
	private static void recall(ServerPlayer player) {
		Walk walk = WALKS.remove(player.getUUID());
		player.removeAttached(OUT);
		if (walk != null && player.level().getEntity(walk.blob) instanceof WebSymbioteBlob blob) {
			blob.discard();
		}
	}

	// ---- the walker ----

	/** A tick of the blob that's out (from {@code WebSymbioteBlob.tick}, which does nothing else for it). */
	static void blobTick(WebSymbioteBlob blob, ServerLevel level) {
		UUID owner = blob.sleepOwner;
		Walk walk = owner == null ? null : WALKS.get(owner);
		ServerPlayer host = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (walk == null || !blob.getUUID().equals(walk.blob) || host == null || host.level() != level || !host.isAlive()
			|| !WebSymbiote.state(host).bonded()) {
			// nobody it belongs to (a host who left or died, a reload)
			if (host != null && walk != null) {
				recall(host);
			}
			blob.discard();
			return;
		}
		long now = level.getGameTime();
		if (blob.tickCount % 3 == 0) {
			level.sendParticles(WebSymbioteStrains.dust(blob, 0.9F), blob.getX(), blob.getY() + 0.1, blob.getZ(), 1, 0.3, 0.02, 0.3, 0.0);
		}
		if (walk.returning) {
			blob.getNavigation().moveTo(host, HURRY_SPEED);
			return;
		}
		LivingEntity prey = walk.prey == null ? null : level.getEntity(walk.prey) instanceof LivingEntity living ? living : null;
		if (prey != null && (!prey.isAlive() || now - walk.preySince > PATIENCE)) {
			if (prey.isAlive()) {
				walk.skipped.add(prey.getUUID());
			}
			prey = null;
			walk.prey = null;
		}
		if (prey == null && blob.tickCount % SCAN_EVERY == 0) {
			prey = quarry(level, blob.position(), HUNT_RANGE, walk);
			walk.prey = prey == null ? null : prey.getUUID();
			walk.preySince = now;
		}
		if (prey == null) {
			if (blob.tickCount % IDLE_EVERY == 0 || blob.getNavigation().isDone()) {
				wander(level, blob, walk);
			}
			return;
		}
		if (blob.distanceTo(prey) <= REACH + prey.getBbWidth() * 0.5) {
			if (now >= walk.lashAt) {
				walk.lashAt = now + LASH_EVERY;
				lash(level, walk, blob, prey);
			}
		} else {
			blob.getNavigation().moveTo(prey, HUNT_SPEED);
		}
	}

	private static void wander(ServerLevel level, WebSymbioteBlob blob, Walk walk) {
		double angle = blob.getRandom().nextDouble() * Math.PI * 2.0;
		double dist = 6.0 + blob.getRandom().nextDouble() * WANDER_RADIUS;
		double x = Mth.clamp(blob.getX() + Math.cos(angle) * dist, walk.bed.getX() - ROAM_RADIUS, walk.bed.getX() + ROAM_RADIUS);
		double z = Mth.clamp(blob.getZ() + Math.sin(angle) * dist, walk.bed.getZ() - ROAM_RADIUS, walk.bed.getZ() + ROAM_RADIUS);
		blob.getNavigation().moveTo(x, blob.getY(), z, HUNT_SPEED * 0.6);
	}

	/** A hostile it can take (the nearest), else, failing any, a small animal. */
	private static @Nullable LivingEntity quarry(ServerLevel level, Vec3 from, double range, Walk walk) {
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(from, from).inflate(range);
		Vec3 bed = Vec3.atCenterOf(walk.bed);
		List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e.isAlive() && !e.isSpectator() && !walk.skipped.contains(e.getUUID()) && !e.hasCustomName() && e.getMaxHealth() <= MAX_PREY_HEALTH
				&& e.position().distanceTo(bed) <= ROAM_RADIUS && !(e instanceof net.minecraft.world.entity.player.Player) && !WebSymbioteStrains.symbiotic(e)));
		return near.stream().filter(e -> e instanceof Enemy).min(Comparator.comparingDouble(e -> e.distanceToSqr(from)))
			.or(() -> near.stream().filter(WebSymbioteSleep::small).min(Comparator.comparingDouble(e -> e.distanceToSqr(from)))).orElse(null);
	}

	private static boolean small(LivingEntity e) {
		return e instanceof Animal && !(e instanceof TamableAnimal tame && tame.isTame()) && e.getBbWidth() <= SMALL_WIDTH && e.getMaxHealth() <= SMALL_HEALTH;
	}

	/** A hunting lash: a tendril, a blow, and a kill counted. */
	private static void lash(ServerLevel level, Walk walk, WebSymbioteBlob blob, LivingEntity prey) {
		Vec3 from = blob.position().add(0.0, 0.3, 0.0);
		Vec3 to = prey.position().add(0.0, prey.getBbHeight() * 0.6, 0.0);
		blob.tendril(level, from, to);
		level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.8F, 0.8F);
		EntityType<?> type = prey.getType();
		prey.hurtServer(level, level.damageSources().mobAttack(blob), LASH_DAMAGE);
		if (!prey.isAlive()) {
			walk.kills++;
			walk.kinds.merge(type, 1, Integer::sum);
			walk.prey = null;
			level.sendParticles(ParticleTypes.SQUID_INK, to.x, to.y, to.z, 20, 0.3, 0.4, 0.3, 0.1);
		}
	}

	/** One of the skipped hours made up: it goes to the nearest quarry, wherever that is, and strikes it. */
	private static void hop(ServerLevel level, Walk walk, WebSymbioteBlob blob) {
		LivingEntity prey = quarry(level, blob.position(), HOP_RANGE, walk);
		if (prey == null) {
			return;
		}
		Vec3 from = blob.position();
		Vec3 beside = prey.position().add(prey.getBbWidth() + 0.6, 0.0, 0.0);
		blob.snapTo(beside.x, prey.getY(), beside.z, blob.getYRot(), 0.0F);
		blob.getNavigation().stop();
		WebSymbiote.trail(level, from.add(0.0, 0.3, 0.0), blob.position().add(0.0, 0.3, 0.0));
		level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 0.3, from.z, 12, 0.3, 0.2, 0.3, 0.05);
		lash(level, walk, blob, prey);
		walk.skipped.add(prey.getUUID());
	}
}
