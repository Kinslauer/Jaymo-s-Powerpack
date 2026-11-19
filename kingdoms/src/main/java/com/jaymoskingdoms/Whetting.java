package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The town's smiths keep the guards' edges (Andy, 2026-11-19, after Hackers 'n Slashers' dulling blades: "you should see
 * the blacksmith sharpening its guards' blades"). Jaymo's Powers' blades wear dull as they are used (its {@code Edge}, kept
 * in the stack's custom data under {@link #EDGE}); a guard's wears like anyone's. In the working day a weaponsmith,
 * armourer or toolsmith with nothing else to do takes a dull blade off one of the town's guards, carries it to a grindstone
 * (their own workstation, or one near it), and is seen working it: the blade in their hand, sparks off the wheel, the
 * grinding sound, a swing now and then. Then it is keen again and goes back to the guard. Without Powers nothing dulls and
 * nothing is done.
 */
public final class Whetting {
	/** Powers' custom data key for a blade's dullness ({@code Edge.KEY}); it runs 0 to 300. */
	static final String EDGE = "jaymospowers_edge";
	/** How often smiths think of it (ticks); how far round a seat they're watched. */
	static final int EVERY = 20;
	static final double WATCHED = 80.0;
	/** The working day (time of day). */
	static final long FROM = 1000L, TO = 11000L;
	/** The chance, each think, that an idle smith sees to a guard's blade. */
	static final float START_CHANCE = 0.08F;
	/** A guard's blade this dull is worth the trouble (a blade is worn from 75). */
	static final int DULL_FROM = 90;
	/** How far from the smith a guard is looked for, how far a grindstone is looked for round their post, and how far one can be from them. */
	static final double GUARD_REACH = 24.0;
	static final int STONE_REACH = 8;
	static final double STONE_FAR = 48.0;
	/** Their pace; how close is there; the longest they try to get there, and how long the grinding takes (ticks). */
	static final float WALK = 0.55F;
	static final double ARRIVE = 2.0;
	static final int TRAVEL_TICKS = 700, GRIND_MIN = 120, GRIND_MORE = 80;
	/** The sparks off the wheel and how big (a fallback spark when Powers' own can't be drawn). */
	static final int SPARKS = 4;
	static final float SPARK_SCALE = 0.5F;

	private static final class Job {
		final UUID guard;
		final BlockPos stone;
		final long started;
		final int length;
		long arrived = -1;

		Job(UUID guard, BlockPos stone, long started, int length) {
			this.guard = guard;
			this.stone = stone;
			this.started = started;
			this.length = length;
		}
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private Whetting() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Whetting::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		RandomSource random = level.getRandom();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), Villager::isAlive);
			for (Villager v : folk) {
				if (!smith(v)) {
					continue;
				}
				Job job = JOBS.get(v.getUUID());
				if (job != null) {
					if (!step(level, v, job, t, now)) {
						end(v);
					}
				} else if (t >= FROM && t < TO && random.nextFloat() < START_CHANCE && Routines.idle(v) && !Rulers.is(v) && !SmithMending.working(v)) {
					start(level, v, folk, now);
				}
			}
		}
		JOBS.values().removeIf(j -> now - j.started > TRAVEL_TICKS + GRIND_MIN + GRIND_MORE + 200);
	}

	/** {@code smith} is at the grindstone with a guard's blade just now (so {@link SmithMending} leaves them be). */
	static boolean working(Villager smith) {
		return JOBS.containsKey(smith.getUUID());
	}

	/** A weaponsmith, an armourer or a toolsmith. */
	private static boolean smith(Villager v) {
		return v.getVillagerData().profession().is(VillagerProfession.WEAPONSMITH) || v.getVillagerData().profession().is(VillagerProfession.ARMORER)
			|| v.getVillagerData().profession().is(VillagerProfession.TOOLSMITH);
	}

	/** How dull the blade in {@code guard}'s hand is (0 when it has no edge to lose, or Powers isn't there). */
	static int dullness(Villager guard) {
		CustomData data = guard.getMainHandItem().get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(EDGE, 0);
	}

	private static void start(ServerLevel level, Villager smith, List<Villager> folk, long now) {
		Villager dull = null;
		int most = DULL_FROM - 1;
		for (Villager g : folk) {
			if (g != smith && McaCompat.isGuard(g) && !g.isSleeping() && g.distanceToSqr(smith) <= GUARD_REACH * GUARD_REACH && dullness(g) > most
				&& !claimed(g)) {
				dull = g;
				most = dullness(g);
			}
		}
		if (dull == null) {
			return;
		}
		BlockPos stone = grindstone(level, smith);
		if (stone == null) {
			return;
		}
		RandomSource random = level.getRandom();
		JOBS.put(smith.getUUID(), new Job(dull.getUUID(), stone, now, GRIND_MIN + random.nextInt(GRIND_MORE)));
		BehaviorUtils.setWalkAndLookTargetMemories(smith, stone, WALK, 1);
	}

	private static boolean claimed(Villager guard) {
		for (Job j : JOBS.values()) {
			if (j.guard.equals(guard.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** A grindstone for {@code smith}: their own workstation if it is one, else the nearest by it (or by them). */
	private static BlockPos grindstone(ServerLevel level, Villager smith) {
		BlockPos around = smith.blockPosition();
		Optional<GlobalPos> site = smith.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (site.isPresent() && site.get().dimension() == level.dimension() && site.get().pos().distSqr(around) <= STONE_FAR * STONE_FAR) {
			BlockPos pos = site.get().pos();
			if (level.getBlockState(pos).is(Blocks.GRINDSTONE)) {
				return pos;
			}
			around = pos;
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-STONE_REACH, -3, -STONE_REACH), around.offset(STONE_REACH, 3, STONE_REACH))) {
			if (level.getBlockState(p).is(Blocks.GRINDSTONE)) {
				double d = p.distSqr(around);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** One think of the work: false when it's over (done, or broken off). */
	private static boolean step(ServerLevel level, Villager smith, Job job, long t, long now) {
		if (!(level.getEntity(job.guard) instanceof Villager guard) || !guard.isAlive() || dullness(guard) <= 0 || !Routines.idle(smith)
			|| !level.getBlockState(job.stone).is(Blocks.GRINDSTONE) || t >= TO + 1000L) {
			return false;
		}
		Vec3 wheel = Vec3.atCenterOf(job.stone);
		if (job.arrived < 0) {
			boolean there = smith.position().distanceToSqr(Vec3.atBottomCenterOf(job.stone)) <= ARRIVE * ARRIVE;
			if (!there && now - job.started < TRAVEL_TICKS) {
				if (smith.getNavigation().isDone()) {
					BehaviorUtils.setWalkAndLookTargetMemories(smith, job.stone, WALK, 1);
				}
				return true;
			}
			job.arrived = now;
			// (the guard's blade, in the smith's hand as they work it)
			smith.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(guard.getMainHandItem().getItem()));
		}
		smith.getNavigation().stop();
		smith.getLookControl().setLookAt(wheel.x, wheel.y, wheel.z);
		RandomSource random = level.getRandom();
		long in = now - job.arrived;
		if (random.nextInt(2) == 0) {
			smith.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, job.stone, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.3F);
			Vec3 at = new Vec3(wheel.x, job.stone.getY() + 0.9, wheel.z);
			Vec3 out = smith.position().subtract(at);
			if (!PowersCompat.clashSparks(level, at, out, SPARKS, SPARK_SCALE)) {
				level.sendParticles(Fx.spark(0xFFC060, 0.3F), at.x, at.y, at.z, SPARKS, 0.15, 0.1, 0.15, 0.05);
			}
		}
		if (in >= job.length) {
			keen(guard);
			level.playSound(null, smith.getX(), smith.getY(), smith.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.2F, 1.8F);
			return false;
		}
		return true;
	}

	/** The guard's blade is keen again. */
	private static void keen(Villager guard) {
		ItemStack blade = guard.getMainHandItem();
		CustomData.update(DataComponents.CUSTOM_DATA, blade, tag -> tag.remove(EDGE));
		CustomData left = blade.get(DataComponents.CUSTOM_DATA);
		if (left != null && left.isEmpty()) {
			blade.remove(DataComponents.CUSTOM_DATA);
		}
	}

	/** The smith's hand is empty again and the work forgotten. */
	private static void end(Villager smith) {
		Job job = JOBS.remove(smith.getUUID());
		if (job != null && job.arrived >= 0) {
			smith.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}
}
