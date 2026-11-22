package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "we need a way to clean blood". Villagers don't leave blood lying in their homes: blood on a settlement's floors
 * indoors (Jaymo's Powers' lasting stains: pools, smears, footprints) is seen by a grown villager near it who has nothing
 * pressing to do, who grumbles about it and goes to scrub it up, swiping at it until it's gone, then the next stain in the
 * same room. Guards stay at their posts; a villager in a fight, a talk, tending the hurt or hauling the dead (as
 * {@link TalkBusy} has busy), afraid, held or downed leaves it. Blood with a body still lying in it (a corpse, the downed)
 * is left until the body is gone. Outdoors the rain does it. Nothing without Powers.
 */
final class BloodChores {
	/** How often blood is looked for (ticks); how often a chore is driven (ticks). */
	static final int EVERY = 100, DRIVE = 5;
	/** How near a seat blood is looked for; how near a villager must be to see it; how far the next stain in the room may be (blocks). */
	static final double WATCHED = 96.0, NOTICE = 14.0, ROOM = 7.0;
	/** The most stains looked at near a seat each time. */
	static final int MOST = 48;
	/** How close they scrub from (blocks); how much of a stain one swipe takes off, and how far round (blocks); how often (ticks). */
	static final double REACH = 1.7, SWIPE_RANGE = 0.9;
	static final float SWIPE = 0.3F;
	static final int SWIPE_GAP = 15;
	/** How long a chore lasts before they give up (ticks); how long before a stain they gave up on is minded again. */
	static final long CHORE_TICKS = 1200L, RESTED = 2400L;
	/** How fast they go to it. */
	static final float SPEED = 0.6F;
	/** Talk: how long between one villager's words about blood (ticks); how far they carry; the share of chores spoken of. */
	static final long TALK_GAP = 600L;
	static final double TALK_RANGE = 16.0;
	static final float TALK_CHANCE = 0.5F;

	private static final class Chore {
		final UUID cleaner;
		Vec3 spot;
		final long until;
		long swiped;

		Chore(UUID cleaner, Vec3 spot, long until) {
			this.cleaner = cleaner;
			this.spot = spot;
			this.until = until;
		}
	}

	/** Chores under way, by cleaner. */
	private static final Map<UUID, Chore> CHORES = new HashMap<>();
	/** Spots given up on lately (block → until when it's left be). */
	private static final Map<BlockPos, Long> RESTING = new HashMap<>();
	/** When each villager last spoke of blood. */
	private static final Map<UUID, Long> SPOKE = new HashMap<>();

	private BloodChores() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BloodChores::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHORES.clear();
			RESTING.clear();
			SPOKE.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % DRIVE == 0 && !CHORES.isEmpty()) {
			Iterator<Chore> it = CHORES.values().iterator();
			while (it.hasNext()) {
				if (drive(level, it.next(), now)) {
					it.remove();
				}
			}
		}
		if (now % EVERY != 0) {
			return;
		}
		RESTING.values().removeIf(t -> t < now);
		SPOKE.values().removeIf(t -> t < now);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Vec3 stain : PowersCompat.bloodStains(level, Vec3.atCenterOf(f.seat()), WATCHED, MOST)) {
				BlockPos at = BlockPos.containing(stain.x, stain.y + 0.1, stain.z);
				if (!RESTING.containsKey(at) && !minded(stain) && Strays.indoors(level, at) && !bodyIn(level, stain)) {
					notice(level, stain, now);
				}
			}
		}
	}

	/** Whether someone is already scrubbing near this stain. */
	private static boolean minded(Vec3 stain) {
		for (Chore c : CHORES.values()) {
			if (c.spot.distanceToSqr(stain) < ROOM * ROOM) {
				return true;
			}
		}
		return false;
	}

	/** Whether a body lies in the blood (the downed, the dead, a corpse): it'd only bleed it full again. */
	private static boolean bodyIn(ServerLevel level, Vec3 stain) {
		return !level.getEntities((Entity) null, new AABB(stain, stain).inflate(1.5, 1.0, 1.5), e -> e instanceof LivingEntity body
			&& (!body.isAlive() || Downed.isDowned(body)) || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().contains("corpse")).isEmpty();
	}

	/** The nearest free grown villager who can see {@code stain} goes to scrub it, grumbling now and then. */
	private static void notice(ServerLevel level, Vec3 stain, long now) {
		Villager cleaner = null;
		double best = NOTICE * NOTICE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(stain, stain).inflate(NOTICE), BloodChores::free)) {
			double d = v.distanceToSqr(stain);
			if (d < best && sees(level, v, stain)) {
				best = d;
				cleaner = v;
			}
		}
		if (cleaner == null) {
			return;
		}
		CHORES.put(cleaner.getUUID(), new Chore(cleaner.getUUID(), stain, now + CHORE_TICKS));
		if (level.getRandom().nextFloat() < TALK_CHANCE) {
			say(level, cleaner, "start", now);
		}
	}

	/** Grown, on their feet, not a guard, and not busy with anything that can't wait. */
	private static boolean free(Villager v) {
		return v.isAlive() && !v.isBaby() && !Downed.isDowned(v) && !Downed.isGuard(v) && !Frights.afraid(v.getUUID()) && !Arrests.held(v)
			&& !Strays.shooing(v.getUUID()) && !CHORES.containsKey(v.getUUID()) && !TalkBusy.occupied(v);
	}

	/** Whether {@code v} can see the floor at {@code stain}: nothing solid between its eyes and the spot. */
	private static boolean sees(ServerLevel level, Villager v, Vec3 stain) {
		Vec3 eyes = v.getEyePosition(), spot = stain.add(0.0, 0.1, 0.0);
		HitResult hit = level.clip(new ClipContext(eyes, spot, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(spot) < 0.25;
	}

	/** One step of a chore; true once it's over. */
	private static boolean drive(ServerLevel level, Chore c, long now) {
		if (!(level.getEntity(c.cleaner) instanceof Villager v) || !v.isAlive() || Downed.isDowned(v) || Frights.afraid(v.getUUID())
			|| Arrests.held(v) || TalkBusy.occupied(v)) {
			return true;
		}
		if (PowersCompat.bloodStains(level, c.spot, SWIPE_RANGE, 1).isEmpty() || bodyIn(level, c.spot)) {
			// that one's done: the next stain in the room, if there is one
			Vec3 next = next(level, c.spot);
			if (next == null) {
				if (level.getRandom().nextFloat() < TALK_CHANCE * 0.5F) {
					say(level, v, "done", now);
				}
				return true;
			}
			c.spot = next;
		}
		if (now > c.until) {
			RESTING.put(BlockPos.containing(c.spot.x, c.spot.y + 0.1, c.spot.z), now + RESTED);
			return true;
		}
		if (v.distanceToSqr(c.spot) > REACH * REACH) {
			walk(v, BlockPos.containing(c.spot.x, c.spot.y + 0.1, c.spot.z));
			return false;
		}
		v.getNavigation().stop();
		v.getLookControl().setLookAt(c.spot.x, c.spot.y, c.spot.z);
		if (now - c.swiped >= SWIPE_GAP) {
			c.swiped = now;
			swipe(level, v, c.spot);
		}
		return false;
	}

	/** The nearest other indoor stain within {@link #ROOM} of {@code from} with no body in it, or null. */
	private static @Nullable Vec3 next(ServerLevel level, Vec3 from) {
		for (Vec3 stain : PowersCompat.bloodStains(level, from, ROOM, 8)) {
			BlockPos at = BlockPos.containing(stain.x, stain.y + 0.1, stain.z);
			if (!RESTING.containsKey(at) && Strays.indoors(level, at) && !bodyIn(level, stain)) {
				return stain;
			}
		}
		return null;
	}

	/** A swipe at the blood: the arm goes, water splashes, a brush scrubs, and some of it comes up. */
	private static void swipe(ServerLevel level, Villager v, Vec3 spot) {
		v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		PowersCompat.scrubStains(level, spot, SWIPE_RANGE, SWIPE);
		level.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y + 0.05, spot.z, 8, 0.3, 0.05, 0.3, 0.1);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	/** It goes to {@code to}: through its brain if it walks by one, else its navigation. */
	private static void walk(Villager v, BlockPos to) {
		if (v.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, SPEED, 1);
		} else {
			v.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, SPEED);
		}
	}

	/** {@code speaker} says a line of {@code family}, unless it spoke of blood lately. */
	private static void say(ServerLevel level, Villager speaker, String family, long now) {
		Long last = SPOKE.get(speaker.getUUID());
		if (last != null && last > now) {
			return;
		}
		SPOKE.put(speaker.getUUID(), now + TALK_GAP);
		Speech.say(level, speaker, Component.translatable(Oddities.line("talk.jaymoskingdoms.blood_chore." + family, level.getRandom())),
			Speech.WHITE, TALK_RANGE);
	}
}
