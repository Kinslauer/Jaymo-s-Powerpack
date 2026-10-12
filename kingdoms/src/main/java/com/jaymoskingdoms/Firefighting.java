package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.11) A settlement's people answer a fire. A fire lit in or near a settlement ({@code BaseFireBlockMixin}) is
 * watched until it's out. Each grown villager near it chooses once how to answer: some fight it (they run to it and
 * put it out, either beating it out, now and then knocking out the burning block under it so it can't spread, or
 * pouring water on it, taken up again a moment later), some run to the nearest child and lead it away, the rest run
 * from it. Children always run. The kingdom's folk (guards, builders, farmers...) not already fighting put it out.
 */
public final class Firefighting {
	/** Villagers and folk this close to a fire answer it; a fire this close to a settlement's seat is watched. */
	static final double REACH = 18.0, WATCHED = 96.0;
	/** How often fires are answered (ticks); the most watched at once, and answered in one go. */
	static final int EVERY = 10, MOST_WATCHED = 512, MOST_ANSWERED = 48;
	/** Close enough to put it out (blocks); how far they run; how long poured water stays (ticks). */
	static final double PUT_OUT = 3.2;
	/** Firefighters go to a spot this far short of the fire (they won't walk into it). */
	static final double STAND_OFF = 2.0;
	static final int RUN = 12, WATER_TICKS = 30;
	/** The share of grown villagers who fight it, and who look after a child (the rest run); a choice is kept this long. */
	static final float FIGHT = 0.4F, GUARD = 0.3F;
	static final long CHOICE_TICKS = 600L;
	/** How fast they go: villagers fighting or fleeing, and folk. */
	static final float VILLAGER_SPEED = 0.7F;
	static final double FOLK_SPEED = 1.15;

	enum Answer { FIGHT, GUARD, RUN }

	private record Choice(Answer answer, long until) {
	}

	private record Water(BlockPos pos, long until) {
	}

	private static final Set<BlockPos> FIRES = new HashSet<>();
	private static final Map<UUID, Choice> CHOICES = new HashMap<>();
	private static final List<Water> WATER = new ArrayList<>();

	private Firefighting() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Firefighting::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIRES.clear();
			CHOICES.clear();
			WATER.clear();
		});
	}

	/** A fire was lit at {@code pos} ({@code BaseFireBlockMixin}): watched if it's in or near a settlement. */
	public static void lit(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel server) || FIRES.size() >= MOST_WATCHED || server != Factions.level(server.getServer())) {
			return;
		}
		if (Factions.around(server.getServer(), server, pos, WATCHED) != null) {
			FIRES.add(pos.immutable());
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		// poured water taken up again
		for (Iterator<Water> it = WATER.iterator(); it.hasNext(); ) {
			Water w = it.next();
			if (now >= w.until()) {
				if (level.getBlockState(w.pos()).is(Blocks.WATER)) {
					level.setBlock(w.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				it.remove();
			}
		}
		if (now % EVERY != 0 || FIRES.isEmpty()) {
			return;
		}
		FIRES.removeIf(p -> !level.isLoaded(p) || !level.getBlockState(p).is(BlockTags.FIRE));
		CHOICES.values().removeIf(c -> c.until() < now);
		int answered = 0;
		for (BlockPos fire : new ArrayList<>(FIRES)) {
			if (answered++ >= MOST_ANSWERED) {
				break;
			}
			answer(level, fire, now);
		}
	}

	/** Everyone near {@code fire} answers it. */
	private static void answer(ServerLevel level, BlockPos fire, long now) {
		AABB near = new AABB(fire).inflate(REACH);
		for (Villager v : level.getEntitiesOfClass(Villager.class, near, LivingEntity::isAlive)) {
			if (v.isBaby()) {
				run(level, v, fire);
				continue;
			}
			Choice choice = CHOICES.get(v.getUUID());
			if (choice == null) {
				choice = new Choice(choose(level, v), now + CHOICE_TICKS);
				CHOICES.put(v.getUUID(), choice);
				JaymosKingdoms.LOGGER.debug("a villager at {} answers the fire at {}: {}", v.blockPosition(), fire, choice.answer());
				level.playSound(null, v.blockPosition(), choice.answer() == Answer.RUN ? SoundEvents.VILLAGER_HURT : SoundEvents.VILLAGER_NO,
					SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			switch (choice.answer()) {
				case FIGHT -> {
					if (close(v, fire)) {
						putOut(level, v, fire, now);
						return;
					}
					BehaviorUtils.setWalkAndLookTargetMemories(v, beside(v, fire), VILLAGER_SPEED, 0);
				}
				case GUARD -> guard(level, v, fire);
				default -> run(level, v, fire);
			}
		}
		for (Folk folk : level.getEntitiesOfClass(Folk.class, near, f -> f.isAlive() && f.getTarget() == null)) {
			if (close(folk, fire)) {
				putOut(level, folk, fire, now);
				return;
			}
			BlockPos to = beside(folk, fire);
			folk.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, FOLK_SPEED);
		}
	}

	/** How a grown villager answers: fights it, looks after a child (if one's near), or runs. */
	private static Answer choose(ServerLevel level, Villager v) {
		float roll = level.getRandom().nextFloat();
		if (roll < FIGHT) {
			return Answer.FIGHT;
		}
		if (roll < FIGHT + GUARD && child(level, v) != null) {
			return Answer.GUARD;
		}
		return Answer.RUN;
	}

	private static @Nullable Villager child(ServerLevel level, Villager v) {
		Villager best = null;
		double bestD = REACH * REACH;
		for (Villager c : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(REACH), x -> x.isAlive() && x.isBaby())) {
			double d = c.distanceToSqr(v);
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return best;
	}

	/** A spot {@link #STAND_OFF} short of {@code fire}, on {@code who}'s side of it. */
	private static BlockPos beside(LivingEntity who, BlockPos fire) {
		Vec3 off = who.position().subtract(Vec3.atBottomCenterOf(fire)).multiply(1.0, 0.0, 1.0);
		if (off.lengthSqr() < 1.0E-3) {
			off = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 at = Vec3.atBottomCenterOf(fire).add(off.normalize().scale(STAND_OFF));
		return BlockPos.containing(at.x, fire.getY(), at.z);
	}

	private static boolean close(LivingEntity who, BlockPos fire) {
		return who.distanceToSqr(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5) <= PUT_OUT * PUT_OUT;
	}

	/** To the nearest child, then away with it. */
	private static void guard(ServerLevel level, Villager v, BlockPos fire) {
		Villager c = child(level, v);
		if (c == null) {
			run(level, v, fire);
			return;
		}
		if (v.distanceToSqr(c) > 4.0) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(c, false), VILLAGER_SPEED, 1);
			return;
		}
		BlockPos away = away(level, c, fire);
		BehaviorUtils.setWalkAndLookTargetMemories(v, away, VILLAGER_SPEED, 2);
		BehaviorUtils.setWalkAndLookTargetMemories(c, away, VILLAGER_SPEED, 2);
	}

	private static void run(ServerLevel level, Villager v, BlockPos fire) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, away(level, v, fire), VILLAGER_SPEED, 2);
	}

	/** A spot on the ground {@link #RUN} blocks from {@code fire}, the way {@code who} already is from it. */
	private static BlockPos away(ServerLevel level, LivingEntity who, BlockPos fire) {
		Vec3 off = who.position().subtract(Vec3.atCenterOf(fire)).multiply(1.0, 0.0, 1.0);
		if (off.lengthSqr() < 1.0E-3) {
			off = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 to = who.position().add(off.normalize().scale(RUN));
		int x = (int)Math.floor(to.x), z = (int)Math.floor(to.z);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** {@code who} puts out {@code fire}: water poured on it, or beaten out (now and then knocking out what burns under it). */
	private static void putOut(ServerLevel level, Mob who, BlockPos fire, long now) {
		who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		who.getLookControl().setLookAt(fire.getX() + 0.5, fire.getY() + 0.5, fire.getZ() + 0.5);
		if (level.getRandom().nextBoolean()) {
			level.setBlock(fire, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
			WATER.add(new Water(fire, now + WATER_TICKS));
			level.playSound(null, fire, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.0F);
		} else {
			level.setBlock(fire, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 1.4F);
			// what's burning under it, knocked out so it can't spread from there
			BlockPos under = fire.below();
			BlockState fuel = level.getBlockState(under);
			if (level.getRandom().nextBoolean() && fuel.ignitedByLava() && burningBeside(level, under)) {
				level.destroyBlock(under, true, who);
			}
		}
		FIRES.remove(fire);
	}

	/** Fire is still at {@code pos}'s side (it's burning). */
	private static boolean burningBeside(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (level.getBlockState(pos.relative(d)).is(BlockTags.FIRE)) {
				return true;
			}
		}
		return false;
	}
}
