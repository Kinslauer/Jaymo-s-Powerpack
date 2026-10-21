package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's farmers (0.5.1, {@link Folk#FARMER}): each keeps one of its farms (a farm piece of its village, or a
 * farm its builders put up), his home in the middle of it. By day he walks his rows harvesting what's ripe and sowing it
 * again (bare farmland too), and at a pen he keeps the herd: he pairs off the beasts while there are few, and butchers
 * one when there are more than {@link #HERD_KEEP} of a kind. What he gathers he carries (you see it in his hands) to the
 * settlement's storage once his arms are full or the work runs out. The farms are kept in the faction's works as
 * {@link #VILLAGE_FARM} (a village's own) or {@code farm} (built).
 */
public final class Farmers {
	/** A village's own farm, kept among a faction's buildings ({@link Works.Built}) so its farmer can be found one. */
	public static final String VILLAGE_FARM = "village_farm";
	/**
	 * How often an idle farmer looks for work (ticks); how far round the middle of his farm he works (beyond its own
	 * half-width); and how far from it he may go (to the storage and back).
	 */
	static final int LOOK_TICKS = 40, REACH_SLACK = 3, ROAM = 72;
	/** He carries this many items before taking them to storage. */
	static final int ARMFUL = 32;
	/** A herd is kept at this many head of a kind: more and one is butchered; fewer than this and two are paired off. */
	static final int HERD_KEEP = 6, HERD_BREED_BELOW = 5;
	/** How long a harvest or a slaughter takes him (ticks), and how close he must be. */
	static final int WORK_TICKS = 20;
	static final double WORK_REACH = 2.0, DEPOT_REACH = 2.5;
	/** No closer to his goal for this long (ticks) and he gives it up. */
	static final int STUCK_TICKS = 200;
	static final double WALK_SPEED = 0.6;

	private static final int IDLE = 0, HARVEST = 1, HERD = 2, DELIVER = 3;

	private static final class Job {
		int phase = IDLE;
		@Nullable BlockPos goal;
		@Nullable Animal beast;
		int reach = -1;
		final List<ItemStack> bag = new ArrayList<>();
		long actAt, improvedAt;
		double best = Double.MAX_VALUE;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();

	private Farmers() {
	}

	static void clear() {
		JOBS.clear();
	}

	/** (0.5.26) {@code w} is a farmer walking a load to storage. */
	static boolean delivering(Worker w) {
		Job job = w.role() == Folk.FARMER ? JOBS.get(w.getUUID()) : null;
		return job != null && job.phase == DELIVER && !job.bag.isEmpty();
	}

	/** (0.5.26) A rickshaw met him on his way to storage: his load's taken (returned) and he goes back to work. */
	static List<ItemStack> handOver(Worker w) {
		Job job = w.role() == Folk.FARMER ? JOBS.get(w.getUUID()) : null;
		if (job == null || job.phase != DELIVER) {
			return List.of();
		}
		List<ItemStack> load = new ArrayList<>(job.bag);
		job.bag.clear();
		w.setCarried(ItemStack.EMPTY);
		job.phase = IDLE;
		w.setTask("idle");
		return load;
	}

	// ---- the farms --------------------------------------------------------------------------------------------------

	/** The faction's farms (village farms, the ones its builders put up, fields and pens, and fields its lords marked). */
	static List<Works.Built> farms(MinecraftServer server, Faction faction) {
		List<Works.Built> out = new ArrayList<>();
		for (Works.Built b : Works.of(server, faction.id()).built()) {
			// (0.7.116) and fields a lord marked ({@link Areas})
			if (b.entry().equals(VILLAGE_FARM) || b.entry().equals("farm") || b.entry().equals("pen") || b.entry().equals(Areas.FIELD)) {
				out.add(b);
			}
		}
		return out;
	}

	/** Keeps a village's farm among the faction's buildings (so its builders build round it, and a farmer keeps it). */
	static void adopt(MinecraftServer server, Faction faction, BlockPos min, BlockPos max) {
		Works.update(server, faction.id(), s -> {
			List<Works.Built> built = new ArrayList<>(s.built());
			built.add(new Works.Built(VILLAGE_FARM, min, max));
			return s.withBuilt(built);
		});
	}

	/** It has a farm without a farmer (counting its roster against its farms). */
	static boolean wanted(MinecraftServer server, Faction faction) {
		return faction.holdings().count(Folk.FARMER) + faction.holdings().vacancies().getOrDefault(Folk.roleName(Folk.FARMER), 0)
			< farms(server, faction).size();
	}

	/**
	 * A farmer for {@code faction} (counted in its roster) at the farm with fewest farmers, his home in its middle;
	 * {@code from} where he comes from (a villager taken into service), else he starts there. Null if it has no farm.
	 */
	static @Nullable Worker spawn(ServerLevel level, Faction faction, @Nullable BlockPos from) {
		List<Works.Built> farms = farms(level.getServer(), faction);
		if (farms.isEmpty()) {
			return null;
		}
		List<Worker> farmers = Workers.near(level, new AABB(faction.seat()).inflate(Builders.CREW_RANGE),
			f -> f.isAlive() && f.factionId() == faction.id() && f.role() == Folk.FARMER);
		Works.Built farm = farms.stream().min(Comparator.comparingLong(b -> farmers.stream().filter(f -> inside(b, field(f))).count()))
			.orElseThrow();
		BlockPos middle = new BlockPos((farm.min().getX() + farm.max().getX()) / 2, farm.min().getY(), (farm.min().getZ() + farm.max().getZ()) / 2);
		// (on the farm's own ground: not on top of whatever stands over its middle, a tree or a tower)
		BlockPos home = Construction.standable(level, middle.above(), 4);
		BlockPos at = from != null ? from : home;
		Worker folk = Workers.body(level, faction, Folk.FARMER, at, home, ROAM);
		if (folk == null) {
			return null;
		}
		folk.setSite(home);
		Factions.update(level.getServer(), faction.id(), f -> f.withHoldings(f.holdings().adjust(Folk.FARMER, 1)));
		return folk;
	}

	private static boolean inside(Works.Built b, BlockPos pos) {
		return pos.getX() >= b.min().getX() - 1 && pos.getX() <= b.max().getX() + 1 && pos.getZ() >= b.min().getZ() - 1 && pos.getZ() <= b.max().getZ() + 1;
	}

	/** The middle of his farm. */
	static BlockPos field(Worker farmer) {
		return farmer.site() != null ? farmer.site() : farmer.getHomePosition();
	}

	/** How far round {@link #field} he works: his farm's half-width and a little (looked up once). */
	private static int reach(ServerLevel level, Worker farmer, Job job) {
		if (job.reach < 0) {
			job.reach = 6;
			Faction f = Factions.get(level.getServer(), farmer.factionId());
			if (f != null) {
				for (Works.Built b : farms(level.getServer(), f)) {
					if (inside(b, field(farmer))) {
						job.reach = Math.max(b.max().getX() - b.min().getX(), b.max().getZ() - b.min().getZ()) / 2 + REACH_SLACK;
						break;
					}
				}
			}
		}
		return job.reach;
	}

	// ---- his day ----------------------------------------------------------------------------------------------------

	/** One farmer's tick (from {@link Folk}). */
	static void tick(ServerLevel level, Worker farmer) {
		if (farmer.getLastHurtByMob() != null && farmer.ticksAlive() - farmer.getLastHurtByMobTimestamp() < 100) {
			// (running for his life)
			return;
		}
		Faction f = Factions.get(level.getServer(), farmer.factionId());
		if (f == null || f.fallen()) {
			return;
		}
		Job job = JOBS.computeIfAbsent(farmer.getUUID(), u -> new Job());
		long now = level.getGameTime();
		switch (job.phase) {
			case HARVEST -> harvest(level, f, farmer, job, now);
			case HERD -> herd(level, f, farmer, job, now);
			case DELIVER -> deliver(level, f, farmer, job, now);
			default -> {
				if (now % LOOK_TICKS == Math.floorMod(farmer.getId(), LOOK_TICKS)) {
					decide(level, farmer, job, now);
				}
			}
		}
	}

	/** His next job: his arms full (or nothing left to do and something in them) to storage; else the ripest row, or the herd. */
	private static void decide(ServerLevel level, Worker farmer, Job job, long now) {
		int carried = job.bag.stream().mapToInt(ItemStack::getCount).sum();
		if (carried == 0 && farmer.isCarrying()) {
			// (what he held when the world was last saved: only its token was kept)
			farmer.setCarried(ItemStack.EMPTY);
		}
		int r = reach(level, farmer, job);
		BlockPos crop = carried >= ARMFUL || !Royals.daytime(level) ? null : crop(level, farmer, r);
		Animal beast = crop != null || carried >= ARMFUL || !Royals.daytime(level) ? null : beast(level, farmer, r);
		if (crop != null) {
			start(job, HARVEST, crop, now);
			farmer.setTask("harvesting");
		} else if (beast != null) {
			start(job, HERD, beast.blockPosition(), now);
			job.beast = beast;
			farmer.setTask("herding");
		} else if (carried > 0) {
			start(job, DELIVER, null, now);
			farmer.setTask("delivering");
		} else {
			farmer.setTask("idle");
		}
	}

	private static void start(Job job, int phase, @Nullable BlockPos goal, long now) {
		job.phase = phase;
		job.goal = goal;
		job.best = Double.MAX_VALUE;
		job.improvedAt = now;
		job.actAt = 0;
	}

	/** The nearest ripe crop (or bare farmland to sow) in his reach, or null. */
	private static @Nullable BlockPos crop(ServerLevel level, Worker farmer, int r) {
		BlockPos home = field(farmer);
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(home.offset(-r, -3, -r), home.offset(r, 3, r))) {
			BlockState state = level.getBlockState(at);
			boolean ripe = state.getBlock() instanceof CropBlock c && c.isMaxAge(state);
			boolean bare = state.isAir() && level.getBlockState(at.below()).is(Blocks.FARMLAND);
			if (ripe || bare) {
				double d = at.distSqr(farmer.blockPosition());
				if (d < bestD) {
					bestD = d;
					best = at.immutable();
				}
			}
		}
		return best;
	}

	/** A beast of his herd to see to: one of a kind he has too many of (to butcher), or two of a kind too few (to pair). */
	private static @Nullable Animal beast(ServerLevel level, Worker farmer, int r) {
		BlockPos home = field(farmer);
		List<Animal> herd = level.getEntitiesOfClass(Animal.class, new AABB(home).inflate(r, 3, r), a -> a.isAlive() && livestock(a));
		Map<Class<?>, List<Animal>> kinds = new HashMap<>();
		for (Animal a : herd) {
			kinds.computeIfAbsent(a.getClass(), k -> new ArrayList<>()).add(a);
		}
		for (List<Animal> kind : kinds.values()) {
			List<Animal> grown = kind.stream().filter(a -> !a.isBaby()).toList();
			if (grown.size() > HERD_KEEP) {
				return grown.getFirst();
			}
			if (kind.size() < HERD_BREED_BELOW) {
				List<Animal> ready = grown.stream().filter(a -> a.getAge() == 0 && a.canFallInLove()).toList();
				if (ready.size() >= 2) {
					return ready.getFirst();
				}
			}
		}
		return null;
	}

	private static boolean livestock(Animal a) {
		return a instanceof AbstractCow || a instanceof Pig || a instanceof Sheep || a instanceof Chicken;
	}

	private static void harvest(ServerLevel level, Faction f, Worker farmer, Job job, long now) {
		BlockPos at = job.goal;
		if (at == null) {
			job.phase = IDLE;
			return;
		}
		if (!walk(farmer, job, at, WORK_REACH, now)) {
			if (now - job.improvedAt > STUCK_TICKS) {
				job.phase = IDLE;
			}
			return;
		}
		if (job.actAt == 0) {
			job.actAt = now + WORK_TICKS;
			farmer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			return;
		}
		if (now < job.actAt) {
			farmer.getLookControl().setLookAt(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			return;
		}
		BlockState state = level.getBlockState(at);
		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			// cut, and sown again from what it gave
			List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, at, null));
			Item seed = state.getBlock().asItem();
			for (ItemStack d : drops) {
				if (d.is(seed)) {
					d.shrink(1);
					break;
				}
			}
			level.levelEvent(2001, at, Block.getId(state));
			level.setBlock(at, crop.getStateForAge(0), Block.UPDATE_ALL);
			for (ItemStack d : drops) {
				if (!d.isEmpty()) {
					bag(farmer, job, d);
				}
			}
		} else if (state.isAir() && level.getBlockState(at.below()).is(Blocks.FARMLAND)) {
			level.setBlock(at, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
			level.playSound(null, at, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		farmer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		job.phase = IDLE;
		decide(level, farmer, job, now);
	}

	private static void herd(ServerLevel level, Faction f, Worker farmer, Job job, long now) {
		Animal beast = job.beast;
		if (beast == null || !beast.isAlive()) {
			job.phase = IDLE;
			return;
		}
		if (!walk(farmer, job, beast.blockPosition(), WORK_REACH, now)) {
			if (now - job.improvedAt > STUCK_TICKS) {
				job.phase = IDLE;
			}
			return;
		}
		farmer.getLookControl().setLookAt(beast, 30.0F, 30.0F);
		if (job.actAt == 0) {
			job.actAt = now + WORK_TICKS;
			return;
		}
		if (now < job.actAt) {
			return;
		}
		farmer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		BlockPos home = field(farmer);
		int r = reach(level, farmer, job);
		List<Animal> kind = level.getEntitiesOfClass(Animal.class, new AABB(home).inflate(r, 3, r), a -> a.isAlive() && a.getClass() == beast.getClass());
		if (kind.stream().filter(a -> !a.isBaby()).count() > HERD_KEEP) {
			// butchered: its meat (and a sheep's wool, a chicken's feathers) into his arms
			Item meat = beast instanceof AbstractCow ? Items.BEEF : beast instanceof Pig ? Items.PORKCHOP : beast instanceof Sheep ? Items.MUTTON : Items.CHICKEN;
			bag(farmer, job, new ItemStack(meat, 1 + level.getRandom().nextInt(3)));
			if (beast instanceof AbstractCow) {
				bag(farmer, job, new ItemStack(Items.LEATHER, level.getRandom().nextInt(3)));
			} else if (beast instanceof Sheep) {
				bag(farmer, job, new ItemStack(Castle.byName("white_wool", Blocks.AIR).getBlock().asItem(), 1));
			} else if (beast instanceof Chicken) {
				bag(farmer, job, new ItemStack(Items.FEATHER, level.getRandom().nextInt(3)));
			}
			level.sendParticles(ParticleTypes.POOF, beast.getX(), beast.getY() + 0.5, beast.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, beast.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.8F, 1.0F);
			beast.discard();
		} else {
			// paired off with another of its kind
			for (Animal a : kind) {
				if (!a.isBaby() && a.getAge() == 0 && a.canFallInLove()) {
					a.setInLove(null);
					if (a != beast && beast.isInLove()) {
						break;
					}
				}
			}
		}
		job.beast = null;
		job.phase = IDLE;
		decide(level, farmer, job, now);
	}

	/** To the storage with what he carries; it goes in (what won't fit is dropped there). */
	private static void deliver(ServerLevel level, Faction f, Worker farmer, Job job, long now) {
		BlockPos depot = Builders.depot(f);
		if (!walk(farmer, job, depot, DEPOT_REACH, now)) {
			if (now - job.improvedAt > STUCK_TICKS * 2) {
				// (no way there: on he goes, carrying it, and tries again later)
				job.phase = IDLE;
			}
			return;
		}
		for (ItemStack stack : job.bag) {
			ItemStack left = Storage.put(level, f, stack);
			if (!left.isEmpty()) {
				Block.popResource(level, depot, left);
			}
		}
		job.bag.clear();
		farmer.setCarried(ItemStack.EMPTY);
		farmer.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, depot, SoundEvents.BARREL_CLOSE, SoundSource.NEUTRAL, 0.5F, 1.0F);
		job.phase = IDLE;
		farmer.setTask("idle");
	}

	/** Into his arms (the biggest stack shows in his hands). */
	private static void bag(Worker farmer, Job job, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		for (ItemStack held : job.bag) {
			if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
				int n = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
				held.grow(n);
				stack.shrink(n);
			}
		}
		if (!stack.isEmpty()) {
			job.bag.add(stack.copy());
		}
		ItemStack show = job.bag.stream().max(Comparator.comparingInt(ItemStack::getCount)).orElse(ItemStack.EMPTY);
		farmer.setCarried(show.copyWithCount(1));
	}

	/** Walks him toward {@code to}; true once he's within {@code reach}. */
	private static boolean walk(Worker farmer, Job job, BlockPos to, double reach, long now) {
		double d = Math.sqrt(farmer.distanceToSqr(Vec3.atBottomCenterOf(to)));
		if (d <= reach) {
			farmer.getNavigation().stop();
			return true;
		}
		if (d < job.best - 0.5) {
			job.best = d;
			job.improvedAt = now;
		}
		if (now - job.improvedAt > STUCK_TICKS) {
			// (no way there: he turns up there in a puff, as builders do)
			ServerLevel level = (ServerLevel)farmer.level();
			BlockPos at = Construction.standable(level, to, 3);
			level.sendParticles(ParticleTypes.POOF, farmer.getX(), farmer.getY() + 1.0, farmer.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			farmer.getNavigation().stop();
			farmer.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			return false;
		}
		if (farmer.getNavigation().isDone() || now % 20 == 0) {
			BlockPos leg = Construction.leg((ServerLevel)farmer.level(), farmer.mob(), to);
			farmer.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, WALK_SPEED);
		}
		return false;
	}
}
