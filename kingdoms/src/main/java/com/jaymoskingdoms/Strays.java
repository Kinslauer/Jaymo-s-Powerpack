package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.566, Andy: "villagers should react to animals being somewhere they shouldnt, like deer in the speakeasy for
 * example") A beast indoors in a settlement (a deer in the speakeasy, a pig in someone's kitchen) is noticed: a villager
 * who sees it cries out (a speech bubble), and someone comes to drive it out: a guard if one is near, else a worker or
 * any grown villager. They walk up and shoo it, swatting it toward the nearest way out (a closed door is opened for it
 * and shut again after). A guard, a den hand or a butcher who can't get it out (a speakeasy has only its ladder) puts it
 * down instead; anyone else calls for a guard.
 * <p>
 * Indoors means a built floor (not earth, sand, hay or stone) under a roof that isn't a tree, or anywhere in a realm's
 * speakeasy. Not counted: a stable, barn or pen the settlement built; an animal ridden, leashed, tamed, named or
 * someone's; the house animals ({@code jaymoskingdoms:house_animals}: cats, parrots, bees and the like).
 */
final class Strays {
	/** Animals that belong indoors. */
	static final TagKey<EntityType<?>> HOUSE_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("house_animals"));
	/** How often strays are looked for (ticks); how often a shooing is driven (ticks). */
	static final int EVERY = 40, DRIVE = 5;
	/** How near a seat strays are looked for; how near a villager must be to notice one; how near a guard is called from (blocks). */
	static final double WATCHED = 96.0, NOTICE = 12.0, CALL = 32.0;
	/** How close a shooer swats from (blocks); how hard; how often (ticks). */
	static final double REACH = 2.5, SWAT_PUSH = 0.45;
	static final int SWAT_GAP = 20;
	/** How long a shooing lasts before they give up or put it down (ticks); how long before the same animal is minded again. */
	static final long SHOO_TICKS = 600L, RESTED = 1200L;
	/** How far a way out is looked for (blocks, across and up/down); how many ways out are tried. */
	static final int EXIT_RANGE = 14, EXIT_Y = 3, EXIT_TRIES = 16;
	/** How fast they go: the shooer, the animal driven. */
	static final float SHOOER_SPEED = 0.8F;
	static final double ANIMAL_SPEED = 1.6;
	/** How long between one villager's words about strays (ticks); how far their words carry. */
	static final long TALK_GAP = 200L;
	static final double TALK_RANGE = 20.0;

	private static final class Shoo {
		final UUID animal;
		UUID shooer;
		final boolean den;
		final long until;
		@Nullable BlockPos exit;
		@Nullable BlockPos door;
		long swatted;

		Shoo(UUID animal, UUID shooer, boolean den, long until) {
			this.animal = animal;
			this.shooer = shooer;
			this.den = den;
			this.until = until;
		}
	}

	/** Shooings under way, by animal. */
	private static final Map<UUID, Shoo> SHOOS = new HashMap<>();
	/** Animals minded lately (animal → until when it's left be). */
	private static final Map<UUID, Long> RESTING = new HashMap<>();
	/** When each villager last spoke of a stray. */
	private static final Map<UUID, Long> SPOKE = new HashMap<>();

	private Strays() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Strays::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHOOS.clear();
			RESTING.clear();
			SPOKE.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % DRIVE == 0 && !SHOOS.isEmpty()) {
			Iterator<Shoo> it = SHOOS.values().iterator();
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
			Underworld.Den den = f.type().isRealm() ? Underworld.builtDen(server, f) : null;
			AABB room = den == null ? null : Speakeasy.room(den.hatch());
			List<Works.Built> pens = pens(server, f);
			for (Animal a : level.getEntitiesOfClass(Animal.class, new AABB(f.seat()).inflate(WATCHED), a -> wild(a)
				&& !SHOOS.containsKey(a.getUUID()) && !RESTING.containsKey(a.getUUID()))) {
				boolean inDen = room != null && room.contains(a.position());
				if (inDen || indoors(level, a.blockPosition()) && !penned(pens, a.blockPosition())) {
					notice(level, f, a, inDen, now);
				}
			}
		}
	}

	/** An animal that answers to nobody and doesn't belong indoors. */
	private static boolean wild(Animal a) {
		return a.isAlive() && !a.isVehicle() && !a.isPassenger() && !a.isLeashed() && !a.hasCustomName()
			&& !a.getType().builtInRegistryHolder().is(HOUSE_ANIMALS)
			&& !(a instanceof AbstractHorse h && h.isTamed()) && !(a instanceof OwnableEntity o && o.getOwnerReference() != null)
			&& !Downed.isDowned(a);
	}

	/** {@code f}'s stables, barns and pens: animals are kept there. */
	private static List<Works.Built> pens(MinecraftServer server, Faction f) {
		List<Works.Built> out = new ArrayList<>();
		for (Works.Built b : Works.of(server, f.id()).built()) {
			String e = b.entry();
			if (e.contains("stable") || e.contains("barn") || e.contains("pen") || e.contains("coop") || e.contains("kennel")
				|| e.contains("pasture") || e.contains("butcher") || e.contains("ranch")) {
				out.add(b);
			}
		}
		return out;
	}

	private static boolean penned(List<Works.Built> pens, BlockPos at) {
		for (Works.Built b : pens) {
			if (at.getX() >= b.min().getX() - 1 && at.getX() <= b.max().getX() + 1 && at.getZ() >= b.min().getZ() - 1
				&& at.getZ() <= b.max().getZ() + 1 && at.getY() >= b.min().getY() - 2 && at.getY() <= b.max().getY() + 2) {
				return true;
			}
		}
		return false;
	}

	/** In a building: a built floor under a roof that isn't a tree's. */
	static boolean indoors(ServerLevel level, BlockPos at) {
		if (level.canSeeSky(at.above())) {
			return false;
		}
		BlockState floor = level.getBlockState(at.below());
		if (floor.isAir()) {
			floor = level.getBlockState(at.below(2));
		}
		if (floor.isAir() || floor.is(BlockTags.DIRT) || floor.is(BlockTags.SAND) || floor.is(BlockTags.BASE_STONE_OVERWORLD)
			|| floor.is(BlockTags.LEAVES) || floor.is(BlockTags.SNOW) || floor.is(Blocks.HAY_BLOCK) || floor.is(Blocks.GRAVEL)
			|| floor.is(Blocks.FARMLAND) || floor.is(Blocks.DIRT_PATH) || !floor.getFluidState().isEmpty()) {
			return false;
		}
		for (int dy = 2; dy <= 8; dy++) {
			BlockPos up = at.above(dy);
			BlockState state = level.getBlockState(up);
			if (state.isAir() || state.getCollisionShape(level, up).isEmpty()) {
				continue;
			}
			return !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
		}
		return false;
	}

	/** A villager near {@code a} who sees it cries out, and someone comes to shoo it. */
	private static void notice(ServerLevel level, Faction f, Animal a, boolean den, long now) {
		Villager seer = null;
		double best = NOTICE * NOTICE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, a.getBoundingBox().inflate(NOTICE), v -> v.isAlive() && !v.isBaby()
			&& !Downed.isDowned(v) && !Frights.afraid(v.getUUID()))) {
			double d = v.distanceToSqr(a);
			if (d < best && v.hasLineOfSight(a)) {
				best = d;
				seer = v;
			}
		}
		if (seer == null) {
			return;
		}
		say(level, seer, den ? "den" : "notice", a, Speech.GOLD, now);
		level.playSound(null, seer.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
		PathfinderMob shooer = shooer(level, f, a, seer);
		SHOOS.put(a.getUUID(), new Shoo(a.getUUID(), shooer.getUUID(), den, now + SHOO_TICKS));
	}

	/** Who drives it out: the nearest guard of {@code f}'s within call, else the nearest worker who isn't busy, else {@code seer}. */
	private static PathfinderMob shooer(ServerLevel level, Faction f, Animal a, @Nullable PathfinderMob fallback) {
		AABB box = a.getBoundingBox().inflate(CALL);
		PathfinderMob guard = nearest(a, Workers.near(level, box, w -> w.factionId() == f.id() && able(w.mob()) && Downed.isGuard(w.mob())));
		if (guard != null) {
			return guard;
		}
		PathfinderMob worker = nearest(a, Workers.near(level, box.deflate(CALL - NOTICE * 1.5), w -> w.factionId() == f.id() && able(w.mob())));
		return worker != null ? worker : fallback;
	}

	private static boolean able(PathfinderMob m) {
		return m.isAlive() && !m.isBaby() && !Downed.isDowned(m) && !Frights.afraid(m.getUUID()) && !shooing(m.getUUID());
	}

	private static boolean shooing(UUID mob) {
		for (Shoo s : SHOOS.values()) {
			if (s.shooer.equals(mob)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable PathfinderMob nearest(Animal a, List<Worker> workers) {
		PathfinderMob best = null;
		double bestD = Double.MAX_VALUE;
		for (Worker w : workers) {
			double d = w.mob().distanceToSqr(a);
			if (d < bestD) {
				bestD = d;
				best = w.mob();
			}
		}
		return best;
	}

	/** One step of a shooing; true once it's over. */
	private static boolean drive(ServerLevel level, Shoo s, long now) {
		if (!(level.getEntity(s.animal) instanceof Animal a) || !a.isAlive() || !wild(a)) {
			shut(level, s);
			return true;
		}
		if (!(level.getEntity(s.shooer) instanceof PathfinderMob m) || !m.isAlive() || Downed.isDowned(m)) {
			RESTING.put(s.animal, now + RESTED / 4);
			shut(level, s);
			return true;
		}
		if (!s.den && !indoors(level, a.blockPosition())) {
			// out: done
			if (level.getRandom().nextBoolean()) {
				say(level, m, "gone", a, Speech.WHITE, now);
			}
			RESTING.put(s.animal, now + RESTED);
			shut(level, s);
			return true;
		}
		if (now > s.until) {
			return giveUp(level, s, a, m, now);
		}
		if (s.exit == null && now % (DRIVE * 4) == 0) {
			s.exit = exit(level, a, s);
		}
		walk(m, a);
		if (m.distanceToSqr(a) <= REACH * REACH && now - s.swatted >= SWAT_GAP) {
			s.swatted = now;
			swat(level, m, a, s, now);
		}
		if (s.exit != null && (now / DRIVE) % 2 == 0) {
			a.getNavigation().moveTo(s.exit.getX() + 0.5, s.exit.getY(), s.exit.getZ() + 0.5, ANIMAL_SPEED);
		}
		return false;
	}

	/** A swat with the flat of the hand (or blade): no harm, a push toward the way out, and a word. */
	private static void swat(ServerLevel level, PathfinderMob m, Animal a, Shoo s, long now) {
		m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		m.getLookControl().setLookAt(a);
		Vec3 push = s.exit != null ? Vec3.atBottomCenterOf(s.exit).subtract(a.position()) : a.position().subtract(m.position());
		push = push.multiply(1.0, 0.0, 1.0);
		if (push.lengthSqr() > 1.0E-4) {
			push = push.normalize().scale(SWAT_PUSH);
			a.push(push.x, 0.15, push.z);
			a.needsSync = true;
		}
		level.playSound(null, a.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.6F, 1.2F);
		if (level.getRandom().nextInt(3) == 0) {
			say(level, m, "shoo", a, Speech.YELLOW, now);
		}
	}

	/** Out of time: a guard, a den hand or a butcher puts it down; anyone else calls for a guard. */
	private static boolean giveUp(ServerLevel level, Shoo s, Animal a, PathfinderMob m, long now) {
		if (Downed.isGuard(m) || s.den && den(m) || butcher(m)) {
			say(level, m, "cull", a, Speech.WHITE, now);
			m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			a.hurtServer(level, level.damageSources().mobAttack(m), a.getMaxHealth() * 4.0F);
			shut(level, s);
			return true;
		}
		Faction f = Theft.lawAt(level, a.blockPosition());
		PathfinderMob guard = null;
		if (f != null) {
			guard = nearest(a, Workers.near(level, a.getBoundingBox().inflate(CALL), w -> w.factionId() == f.id() && able(w.mob())
				&& Downed.isGuard(w.mob())));
		}
		say(level, m, "call", a, Speech.GOLD, now);
		if (guard == null) {
			RESTING.put(s.animal, now + RESTED);
			shut(level, s);
			return true;
		}
		SHOOS.put(s.animal, new Shoo(s.animal, guard.getUUID(), s.den, now + SHOO_TICKS));
		return false;
	}

	private static boolean den(PathfinderMob m) {
		return m.entityTags().contains(Underworld.FENCE_TAG) || m.entityTags().contains(Underworld.DICER_TAG)
			|| m.entityTags().contains(Bookies.TAG);
	}

	private static boolean butcher(PathfinderMob m) {
		return m instanceof Villager v && !v.isBaby()
			&& BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath().equals("butcher");
	}

	/** The nearest open-sky spot {@code a} can walk to; else a closed door near it is opened (and the next try goes through it). */
	private static @Nullable BlockPos exit(ServerLevel level, Animal a, Shoo s) {
		BlockPos at = a.blockPosition();
		List<BlockPos> spots = new ArrayList<>();
		for (int dx = -EXIT_RANGE; dx <= EXIT_RANGE; dx++) {
			for (int dz = -EXIT_RANGE; dz <= EXIT_RANGE; dz++) {
				for (int dy = -EXIT_Y; dy <= EXIT_Y; dy++) {
					BlockPos p = at.offset(dx, dy, dz);
					if (standing(level, p) && level.canSeeSky(p)) {
						spots.add(p);
						break;
					}
				}
			}
		}
		spots.sort((p, q) -> Double.compare(p.distSqr(at), q.distSqr(at)));
		int tries = 0;
		for (BlockPos p : spots) {
			if (tries++ >= EXIT_TRIES) {
				break;
			}
			Path path = a.getNavigation().createPath(p, 0);
			if (path != null && path.canReach()) {
				return p;
			}
		}
		if (s.door == null && !s.den) {
			BlockPos door = door(level, at);
			if (door != null) {
				BlockState state = level.getBlockState(door);
				if (state.getBlock() instanceof DoorBlock db && !db.isOpen(state)) {
					db.setOpen(null, level, state, door, true);
					s.door = door;
				}
			}
		}
		return null;
	}

	private static boolean standing(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
			&& !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() && level.getFluidState(p).isEmpty();
	}

	/** The nearest wooden door's lower half within reach, or null. */
	private static @Nullable BlockPos door(ServerLevel level, BlockPos at) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-10, -2, -10), at.offset(10, 2, 10))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock && !level.getBlockState(p.below()).is(state.getBlock())) {
				double d = p.distSqr(at);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** The door opened for it is shut again. */
	private static void shut(ServerLevel level, Shoo s) {
		if (s.door != null) {
			BlockState state = level.getBlockState(s.door);
			if (state.getBlock() instanceof DoorBlock db && db.isOpen(state)) {
				db.setOpen(null, level, state, s.door, false);
			}
			s.door = null;
		}
	}

	/** It goes after {@code a}: through its brain if it walks by one, else its navigation. */
	private static void walk(PathfinderMob m, Animal a) {
		if (m.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			BehaviorUtils.setWalkAndLookTargetMemories(m, new EntityTracker(a, false), SHOOER_SPEED, 1);
		} else {
			m.getNavigation().moveTo(a, SHOOER_SPEED);
		}
	}

	/** {@code speaker} says a line of {@code family} about {@code a}, unless it spoke of strays lately. */
	private static void say(ServerLevel level, PathfinderMob speaker, String family, Animal a, int color, long now) {
		Long last = SPOKE.get(speaker.getUUID());
		if (last != null && last > now) {
			return;
		}
		SPOKE.put(speaker.getUUID(), now + TALK_GAP);
		Speech.say(level, speaker, Component.translatable(Oddities.line("talk.jaymoskingdoms.stray." + family, level.getRandom()),
			a.getDisplayName()), color, TALK_RANGE);
	}
}
