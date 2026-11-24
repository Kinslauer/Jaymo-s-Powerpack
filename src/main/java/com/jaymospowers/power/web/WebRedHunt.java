package com.jaymospowers.power.web;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The red one hunts (Andy, 2026-11-24: "the red symbiote will primarily be in this form hunting others. up to 500 blocks
 * away, nothing is safe"; "Everyone is prey; load terrain for pursuit"). A humanoid the red strain rides (WebInhabit), or a
 * risen body it holds (WebBehemoth), doesn't wait for something to wander near: every {@code SCAN_EVERY} ticks it picks the
 * nearest living thing within {@code RANGE} blocks (a player, a villager, a pet, a guard, another strain's host: whatever
 * WebBehemoth#prey allows) and runs it down in its slender body, path by path ({@code STEP} blocks at a time when it's far),
 * leaping the last stretch in a sudden lunge ({@code LUNGE_MIN} to {@code LUNGE_MAX} blocks out, every
 * {@code LUNGE_EVERY} ticks at most), its tendrils lashing as it closes (WebBehemoth#lash). Its prey killed, it feeds on the
 * body (never on the living: the kill first) and the host it wears lasts {@code FEED_TICKS} longer; hunting, the host
 * wastes away only {@code HUNT_DECAY_SHARE} as fast (WebInhabit), so the hunt can cover the distance.
 * <p>
 * The terrain it runs through is loaded for it, on the server only and bounded: while it hunts it holds one chunk ticket
 * of radius {@code HOLD_RADIUS} where it is and one of {@code AHEAD_RADIUS} where it's heading, moved as it goes (the
 * old ones released at once), each timing out by itself after {@code TICKET_LIFE} ticks if it weren't refreshed, and
 * never saved; at most {@code MAX_HUNTERS} hunters hold tickets at once (more hunt only through loaded ground). Every ticket
 * is released the moment the hunt ends: its prey dead, lost, or out of range, nothing left in range, the host dead, let go
 * or unloaded, the server stopping.
 */
public final class WebRedHunt {
	// ---- tuning ----
	/** How far it hunts (blocks), and how often it looks for prey when it has none (ticks). */
	static final double RANGE = 500.0;
	static final int SCAN_EVERY = 40;
	/** Pursuit: a fresh path every {@code REPATH_EVERY} ticks, at most {@code STEP} blocks toward the prey, at {@code SPEED} of its own. */
	static final int REPATH_EVERY = 10;
	static final double STEP = 24.0, SPEED = 1.35;
	/** The lunge: from this far out (blocks), this hard (blocks a tick) and this far up, at most once every so many ticks. */
	public static final double LUNGE_MIN = 3.5, LUNGE_MAX = 9.0;
	static final double LUNGE_SPEED = 1.15, LUNGE_UP = 0.38;
	public static final int LUNGE_EVERY = 50;
	/** The lunge shakes the screens of those within this many blocks, this hard. */
	static final double LUNGE_SHAKE_RANGE = 12.0;
	static final float LUNGE_SHAKE = 0.35F;
	/** Feeding on what it killed (within {@code FEED_REACH} blocks): its host lasts this much longer, and heals this share. */
	static final int FEED_TICKS = 900;
	static final double FEED_REACH = 8.0;
	static final float FEED_HEAL = 0.35F;
	/** Hunting, its host wastes this much as fast as it otherwise would (WebInhabit). */
	public static final float HUNT_DECAY_SHARE = 0.15F;
	/** Terrain held for it: around it and ahead of it (chunk radii), each ticket's own time-out (ticks), and how many hunters hold any. */
	static final int HOLD_RADIUS = 2, AHEAD_RADIUS = 1, MAX_HUNTERS = 6;
	static final long TICKET_LIFE = 60L;
	/** Refreshed this often (ticks), well inside their time-out. */
	static final int REFRESH_EVERY = 20;

	/** Loads the ground it runs on and lets things there move (its own type: never saved, times out by itself). */
	private static final TicketType HUNT_LOAD = new TicketType(TICKET_LIFE, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);

	/** A hunt: whom, where its tickets are (null: none held), its last lunge. */
	private static final class Hunt {
		@Nullable UUID prey;
		@Nullable ChunkPos held, ahead;
		long lunged = -LUNGE_EVERY;
		Vec3 heading = Vec3.ZERO;
	}

	private static final Map<Mob, Hunt> HUNTS = new WeakHashMap<>();

	private WebRedHunt() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				end(level, mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Map.Entry<Mob, Hunt> entry : new ArrayList<>(HUNTS.entrySet())) {
				if (entry.getKey().level() instanceof ServerLevel level) {
					release(level, entry.getValue());
				}
			}
			HUNTS.clear();
		});
	}

	/** It's out hunting (its host wastes slower). */
	public static boolean hunting(Mob host) {
		Hunt hunt = HUNTS.get(host);
		return hunt != null && hunt.prey != null;
	}

	/** A red host's turn (from WebInhabit's and WebBehemoth's ticks): find prey, run it down, hold the ground it runs over. */
	static void tick(ServerLevel level, Mob host) {
		if (!host.isAlive() || WebSymbioteStrains.of(host) != WebSymbioteStrain.RED) {
			end(level, host);
			return;
		}
		Hunt hunt = HUNTS.computeIfAbsent(host, h -> new Hunt());
		LivingEntity prey = hunt.prey == null ? null : level.getEntity(hunt.prey) instanceof LivingEntity living ? living : null;
		if (hunt.prey != null && (prey == null || !prey.isAlive())) {
			// gone: killed (it feeds on the body, if it's near) or lost
			if (prey != null && prey.distanceTo(host) <= FEED_REACH) {
				feed(level, host, prey);
			}
			hunt.prey = null;
			prey = null;
		}
		if (prey != null && (prey.distanceToSqr(host) > RANGE * RANGE || !WebBehemoth.prey(host, prey))) {
			hunt.prey = null;
			prey = null;
		}
		if (prey == null && host.tickCount % SCAN_EVERY == 0) {
			prey = nearest(level, host);
			hunt.prey = prey == null ? null : prey.getUUID();
		}
		if (prey == null) {
			// nothing to hunt: nothing held
			release(level, hunt);
			return;
		}
		host.setTarget(prey);
		Vec3 to = prey.position().subtract(host.position());
		double distance = to.length();
		if (host.tickCount % REPATH_EVERY == 0) {
			Vec3 goal = prey.position();
			if (distance > STEP) {
				// far off: a leg of the way at a time, on the ground there
				Vec3 step = host.position().add(to.scale(STEP / distance));
				int x = (int)Math.floor(step.x), z = (int)Math.floor(step.z);
				double y = level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : host.getY();
				goal = new Vec3(step.x, y, step.z);
			}
			hunt.heading = goal;
			host.getNavigation().moveTo(goal.x, goal.y, goal.z, SPEED);
		}
		long now = level.getGameTime();
		if (host.onGround() && distance >= LUNGE_MIN && distance <= LUNGE_MAX && now - hunt.lunged >= LUNGE_EVERY && host.hasLineOfSight(prey)) {
			// the last stretch in one bound
			Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize();
			host.setDeltaMovement(flat.scale(LUNGE_SPEED).add(0.0, LUNGE_UP, 0.0));
			host.syncVelocity = true;
			hunt.lunged = now;
			level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.8F, 1.4F);
			// (felt by anyone near, as the symbiote's every move is)
			com.jaymospowers.core.Shakes.around(level, host.position(), LUNGE_SHAKE_RANGE, LUNGE_SHAKE);
		}
		hold(level, host, hunt);
	}

	/** Stops the hunt and lets go of everything it held. */
	static void end(ServerLevel level, Mob host) {
		Hunt hunt = HUNTS.remove(host);
		if (hunt != null) {
			release(level, hunt);
		}
	}

	/**
	 * The nearest prey within range for a web slinger the red one has seized (WebSymbiote#RED_HUNT_BOND): anything living
	 * and loaded but themselves, a player who can't be hurt, a stand or another symbiote of the red's own.
	 */
	static @Nullable LivingEntity nearest(ServerLevel level, net.minecraft.server.level.ServerPlayer hunter) {
		return nearest(level, hunter, null);
	}

	/** The same, but not {@code skip} (the prey it just gave up on). */
	static @Nullable LivingEntity nearest(ServerLevel level, net.minecraft.server.level.ServerPlayer hunter, @Nullable Entity skip) {
		LivingEntity best = null;
		double bestDistance = RANGE * RANGE;
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && entity != hunter && entity != skip && living.isAlive()
				&& !(living instanceof net.minecraft.world.entity.decoration.ArmorStand)
				&& !(living instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator()))
				&& WebSymbioteStrains.of(living) != WebSymbioteStrain.RED) {
				double d = living.distanceToSqr(hunter);
				if (d < bestDistance) {
					best = living;
					bestDistance = d;
				}
			}
		}
		return best;
	}

	/** The nearest prey within range: anything living it may hunt, anywhere loaded in its world. */
	private static @Nullable LivingEntity nearest(ServerLevel level, Mob host) {
		LivingEntity best = null;
		double bestDistance = RANGE * RANGE;
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && entity != host) {
				double d = living.distanceToSqr(host);
				if (d < bestDistance && WebBehemoth.prey(host, living)) {
					best = living;
					bestDistance = d;
				}
			}
		}
		return best;
	}

	/** Its prey dead at its feet: it feeds on the body, and the host it wears lasts longer. */
	private static void feed(ServerLevel level, Mob host, LivingEntity body) {
		Long until = host.getAttached(WebInhabit.HOST);
		if (until != null) {
			host.setAttached(WebInhabit.HOST, until + FEED_TICKS);
		}
		host.heal(host.getMaxHealth() * FEED_HEAL);
		Vec3 at = body.position();
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + body.getBbHeight() * 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(WebSymbioteStrains.dust(host, 1.2F), at.x, at.y + 0.3, at.z, 16, 0.4, 0.2, 0.4, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.2F, 0.5F);
	}

	/** The ground around it and ahead of it held loaded (moved as it goes, refreshed; capped at {@code MAX_HUNTERS} hunters). */
	private static void hold(ServerLevel level, Mob host, Hunt hunt) {
		if (hunt.held == null && holders() >= MAX_HUNTERS) {
			return;
		}
		ChunkPos here = host.chunkPosition();
		ChunkPos ahead = ChunkPos.containing(net.minecraft.core.BlockPos.containing(hunt.heading));
		boolean moved = !here.equals(hunt.held) || !ahead.equals(hunt.ahead);
		if (!moved && host.tickCount % REFRESH_EVERY != 0) {
			return;
		}
		if (moved) {
			release(level, hunt);
		}
		// (adding the same ticket again only resets its time-out)
		level.getChunkSource().addTicketWithRadius(HUNT_LOAD, here, HOLD_RADIUS);
		level.getChunkSource().addTicketWithRadius(HUNT_LOAD, ahead, AHEAD_RADIUS);
		hunt.held = here;
		hunt.ahead = ahead;
	}

	private static void release(ServerLevel level, Hunt hunt) {
		if (hunt.held != null) {
			level.getChunkSource().removeTicketWithRadius(HUNT_LOAD, hunt.held, HOLD_RADIUS);
			hunt.held = null;
		}
		if (hunt.ahead != null) {
			level.getChunkSource().removeTicketWithRadius(HUNT_LOAD, hunt.ahead, AHEAD_RADIUS);
			hunt.ahead = null;
		}
	}

	private static int holders() {
		int n = 0;
		for (Hunt hunt : HUNTS.values()) {
			if (hunt.held != null) {
				n++;
			}
		}
		return n;
	}
}
