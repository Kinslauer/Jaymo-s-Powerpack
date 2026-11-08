package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Family farmhand's family ({@link FarmhandHome}). Andy: "those MCA should be far more wary of zombies and try to
 * hide at night, as a very last resort they will have to swing back and defend themselves. they are just deadweight and
 * they make life harder for the player. they need some kind of utility or function as of now."
 * <ul>
 * <li><b>Wary</b>: they spot a monster from {@link #WARY_RANGE} blocks and run, home if that's away from it, else
 * away, and keep {@link #KEEP_AWAY} blocks between them and it.</li>
 * <li><b>Hide at night</b>: from {@link #HIDE_FROM} to {@link #HIDE_TO} they go indoors (their house) and stay there;
 * once inside, MCA's own brain puts them to bed.</li>
 * <li><b>Last resort</b>: a grown one with a monster at arm's length and no ground gained running for
 * {@link #CORNER_TICKS} swings back, weakly ({@link #DEFEND_DAMAGE}). Children never do: they only run.</li>
 * <li><b>Work</b> (Andy picked all four): by day the grown ones tend the crops round the house (harvest what's ripe,
 * sow it again, sow bare farmland), keep the animals (feed pairs to breed while there are few, shear sheep, milk a cow
 * when the chest holds an empty bucket), and the child gathers up what's lying about (eggs, wool, saplings). Everything
 * goes into the house chest (one is put in the house if it has none). MCA's own chores are open to the player too (the
 * Work button, {@code McaFarmWorkButtonMixin}); a family member set to one leaves this round to MCA's chore.</li>
 * </ul>
 * Who they are and where their house and chest are is kept on the villagers themselves (entity tags), so it lasts.
 * Driven from {@link Workers#step} (MCA's villagers only), in place of their brain while they're running, hiding,
 * fighting or working.
 */
public final class FarmFamily {
	/** The family's mark, and where their home (the spot by the bed you woke at) and chest are (as "x,y,z"). */
	static final String TAG = "jaymoskingdoms.farm_family";
	static final String HOME_TAG = "jaymoskingdoms.farm_home:", CHEST_TAG = "jaymoskingdoms.farm_chest:";
	/** Sent ahead of MCA's screen ({@link InteractActions}): MCA's Work button shows for this one. */
	public static final String WORK_OFFER = "farm_work";

	// ---- wary ----
	/** How far they spot a monster (MCA's villagers notice one at about 8). */
	static final double WARY_RANGE = 20.0;
	/** Running, they keep at least this far from it before they stop. */
	static final double KEEP_AWAY = 14.0;
	/** How far a run takes them (blocks), and at what pace. */
	static final int RUN = 14;
	static final double RUN_SPEED = 0.85;
	/** How often they look about them (ticks). */
	static final int LOOK_EVERY = 10;

	// ---- last resort ----
	/** A monster this close (blocks), and no ground gained for this long (ticks): cornered. */
	static final double CORNERED = 2.3;
	static final int CORNER_TICKS = 30;
	/** Their weak swing: its damage, its knockback, and how often (ticks). */
	static final float DEFEND_DAMAGE = 1.5F;
	static final double DEFEND_KNOCKBACK = 0.5;
	static final int DEFEND_EVERY = 25;

	// ---- night ----
	/** When they head indoors and when they come out again (time of day: dusk to just before dawn). */
	static final long HIDE_FROM = 12000L, HIDE_TO = 23800L;
	/** How near their home spot counts as their house (blocks across, and up or down). */
	static final int HOUSE_REACH = 8, HOUSE_HEIGHT = 4;
	/** Their home spot open to the sky: inside means this close to it. */
	static final double OPEN_HOME_REACH = 2.0;

	// ---- work ----
	/** Working hours (time of day). */
	static final long WORK_FROM = 1000L, WORK_TO = 11500L;
	/** How far round the house they work the crops and the animals, and gather what's lying about (blocks). */
	static final int FARM_REACH = 24, FARM_HEIGHT = 4, GATHER_REACH = 16;
	/** How often an idle one looks for work (ticks). */
	static final int WORK_LOOK_TICKS = 60;
	/** They carry this many items before taking them to the chest. */
	static final int ARMFUL = 32;
	/** Breed a kind while there are fewer grown than this of it round the farm. */
	static final int HERD_KEEP = 8;
	/** Milk a cow while the chest has fewer milk buckets than this (and an empty one). */
	static final int MILK_KEEP = 2;
	/** An item lying about is gathered only once it's lain this long (ticks): not what a player has just dropped. */
	static final int GATHER_AGE = 200;
	/** How long a harvest or a beast takes them (ticks), and how close they must be. */
	static final int WORK_TICKS = 15;
	static final double WORK_REACH = 2.0, CHEST_REACH = 2.5;
	/** No closer to the goal for this long (ticks) and they give it up. */
	static final int STUCK_TICKS = 160;
	static final double WALK_SPEED = 0.55;

	private static final int IDLE = 0, CROP = 1, FEED = 2, SHEAR = 3, MILK = 4, GATHER = 5, DELIVER = 6;

	/** What each of them is about (forgotten on a restart: they just look again). */
	private static final class State {
		int phase = IDLE;
		@Nullable BlockPos goal;
		@Nullable LivingEntity beast;
		@Nullable ItemEntity item;
		final List<ItemStack> bag = new ArrayList<>();
		long actAt, improvedAt, lookAt;
		double best = Double.MAX_VALUE;
		// running
		boolean running;
		@Nullable LivingEntity threat;
		double farthest;
		long gainedAt, swungAt;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private FarmFamily() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
	}

	/** One of a farmhand's family. */
	static boolean is(net.minecraft.world.entity.Entity e) {
		return e.entityTags().contains(TAG);
	}

	/** {@code folk} are the family of the house round {@code home}: marked, and a chest found (or put) for them. */
	static void adopt(ServerLevel level, BlockPos home, List<Villager> folk) {
		BlockPos chest = findChest(level, home);
		if (chest == null) {
			chest = placeChest(level, home);
		}
		for (Villager v : folk) {
			v.addTag(TAG);
			v.addTag(HOME_TAG + pos(home));
			if (chest != null) {
				v.addTag(CHEST_TAG + pos(chest));
			}
			if (!v.isBaby()) {
				// (a hoe for MCA's Harvest chore, should the player set them to it)
				ItemStack left = McaCompat.stow(v, new ItemStack(Items.STONE_HOE));
				if (!left.isEmpty()) {
					v.spawnAtLocation(level, left);
				}
			}
		}
	}

	private static String pos(BlockPos p) {
		return p.getX() + "," + p.getY() + "," + p.getZ();
	}

	private static @Nullable BlockPos tagPos(PathfinderMob mob, String prefix) {
		for (String t : mob.entityTags()) {
			if (t.startsWith(prefix)) {
				String[] xyz = t.substring(prefix.length()).split(",");
				try {
					return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
				} catch (RuntimeException e) {
					return null;
				}
			}
		}
		return null;
	}

	// ---- their step -----------------------------------------------------------------------------------------------

	/**
	 * One of the family's server step (from {@link Workers#step}): true while we drive them (running, cornered, going
	 * home for the night, at work), false to let MCA's brain have them (indoors at night, idle, talking, following).
	 */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		BlockPos home = tagPos(mob, HOME_TAG);
		if (home == null || mob.isSleeping() || McaCompat.busy(mob) || mob.isPassenger()
			|| mob instanceof Villager v && McaCompat.following(v) != null) {
			return false;
		}
		State s = STATES.computeIfAbsent(mob.getUUID(), u -> new State());
		long now = level.getGameTime();
		if (now % LOOK_EVERY == Math.floorMod(mob.getId(), LOOK_EVERY) || s.threat != null && !s.threat.isAlive()) {
			s.threat = threat(level, mob, s.threat);
		}
		if (s.threat != null) {
			run(level, w, s, home, now);
			return true;
		}
		s.running = false;
		long t = Royals.timeOfDay(level);
		if (t >= HIDE_FROM && t < HIDE_TO) {
			drop(w, s);
			if (indoors(level, mob, home)) {
				// (inside: MCA's brain puts them to bed)
				return false;
			}
			walk(w, s, home, 1.0, now);
			w.setTask("going home");
			return true;
		}
		if (t < WORK_FROM || t >= WORK_TO || mob instanceof Villager v && McaCompat.choring(v)) {
			// (MCA's chore, if the player set one, is MCA's brain's to do)
			return false;
		}
		return work(level, w, s, home, now);
	}

	/** Night fell, or a monster came, mid-errand: the errand's dropped (what they carry they keep). */
	private static void drop(Worker w, State s) {
		if (s.phase != IDLE && s.phase != DELIVER) {
			s.phase = IDLE;
			s.beast = null;
			s.item = null;
		}
	}

	/** Inside their house: near their home spot, under a roof (or right at it, if it's open to the sky). */
	static boolean indoors(ServerLevel level, PathfinderMob mob, BlockPos home) {
		if (level.canSeeSky(home)) {
			return mob.position().distanceToSqr(Vec3.atBottomCenterOf(home)) <= OPEN_HOME_REACH * OPEN_HOME_REACH;
		}
		return insideHouse(level, mob.blockPosition(), home);
	}

	private static boolean insideHouse(ServerLevel level, BlockPos at, BlockPos home) {
		return Math.abs(at.getX() - home.getX()) <= HOUSE_REACH && Math.abs(at.getZ() - home.getZ()) <= HOUSE_REACH
			&& Math.abs(at.getY() - home.getY()) <= HOUSE_HEIGHT && !level.canSeeSky(at);
	}

	// ---- wary, and the last resort ----------------------------------------------------------------------------------

	/** The monster to run from: the one they were running from while it's still near, else the nearest they can see. */
	private static @Nullable LivingEntity threat(ServerLevel level, PathfinderMob mob, @Nullable LivingEntity had) {
		if (had != null && had.isAlive() && had.distanceToSqr(mob) < KEEP_AWAY * KEEP_AWAY) {
			return had;
		}
		LivingEntity best = null;
		double bestD = WARY_RANGE * WARY_RANGE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(WARY_RANGE),
			m -> m.isAlive() && m instanceof Enemy && !m.isPassenger())) {
			double d = m.distanceToSqr(mob);
			if (d < bestD && mob.hasLineOfSight(m)) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	/** Away from the monster (home if that's away from it), and if it's on them with nowhere left to run, a swing back. */
	private static void run(ServerLevel level, Worker w, State s, BlockPos home, long now) {
		PathfinderMob mob = w.mob();
		LivingEntity threat = s.threat;
		if (threat == null) {
			return;
		}
		double d = Math.sqrt(mob.distanceToSqr(threat));
		if (!s.running) {
			// (the run begins: whatever they were at is dropped)
			s.running = true;
			drop(w, s);
			s.farthest = d;
			s.gainedAt = now;
			level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.6F, 1.3F);
			w.setTask("running");
		}
		if (d > s.farthest + 0.5) {
			s.farthest = d;
			s.gainedAt = now;
		}
		if (d <= CORNERED && now - s.gainedAt > CORNER_TICKS && !mob.isBaby()) {
			defend(level, mob, threat, s, now);
			return;
		}
		if (mob.getNavigation().isDone() || now % 10 == 0) {
			Vec3 to = refuge(level, mob, threat, home);
			mob.getNavigation().moveTo(to.x, to.y, to.z, RUN_SPEED);
		}
	}

	/** Where to run: home if they're out and it's not toward the monster, else away from it. */
	private static Vec3 refuge(ServerLevel level, PathfinderMob mob, LivingEntity threat, BlockPos home) {
		boolean homeAway = home.distSqr(threat.blockPosition()) > mob.blockPosition().distSqr(threat.blockPosition());
		if (indoors(level, mob, home)) {
			// (inside: to the far side of the house, never back out of it)
			for (int i = 0; i < 6; i++) {
				Vec3 away = DefaultRandomPos.getPosAway(mob, HOUSE_REACH, HOUSE_HEIGHT, threat.position());
				if (away != null && insideHouse(level, BlockPos.containing(away), home)) {
					return away;
				}
			}
			return homeAway ? Vec3.atBottomCenterOf(home) : mob.position();
		}
		if (homeAway && home.closerThan(mob.blockPosition(), FARM_REACH * 2)) {
			return Vec3.atBottomCenterOf(home);
		}
		Vec3 away = DefaultRandomPos.getPosAway(mob, RUN, 7, threat.position());
		if (away != null) {
			return away;
		}
		Vec3 off = mob.position().subtract(threat.position()).multiply(1.0, 0.0, 1.0);
		if (off.lengthSqr() < 1.0E-3) {
			off = new Vec3(1.0, 0.0, 0.0);
		}
		return mob.position().add(off.normalize().scale(RUN));
	}

	/** Cornered: a weak swing at it, and a shove to win room to run again. */
	private static void defend(ServerLevel level, PathfinderMob mob, LivingEntity threat, State s, long now) {
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(threat, 30.0F, 30.0F);
		if (now - s.swungAt < DEFEND_EVERY) {
			return;
		}
		s.swungAt = now;
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		var hit = level.damageSources().mobAttack(mob);
		if (threat.hurtServer(level, hit, DEFEND_DAMAGE)) {
			threat.knockback(DEFEND_KNOCKBACK, mob.getX() - threat.getX(), mob.getZ() - threat.getZ(), hit, DEFEND_DAMAGE);
		}
		// (room won, or not: they try running again)
		s.gainedAt = now;
		s.farthest = Math.sqrt(mob.distanceToSqr(threat));
	}

	// ---- work -------------------------------------------------------------------------------------------------------

	private static boolean work(ServerLevel level, Worker w, State s, BlockPos home, long now) {
		switch (s.phase) {
			case CROP -> crop(level, w, s, now);
			case FEED, SHEAR, MILK -> beast(level, w, s, home, now);
			case GATHER -> gather(level, w, s, now);
			case DELIVER -> deliver(level, w, s, home, now);
			default -> {
				if (now >= s.lookAt) {
					s.lookAt = now + WORK_LOOK_TICKS;
					decide(level, w, s, home, now);
				}
			}
		}
		if (s.phase == IDLE) {
			w.setTask("idle");
			return false;
		}
		return true;
	}

	/** Their next errand: the chest with a full load; else a ripe crop, a beast to see to, or something lying about. */
	private static void decide(ServerLevel level, Worker w, State s, BlockPos home, long now) {
		PathfinderMob mob = w.mob();
		int carried = s.bag.stream().mapToInt(ItemStack::getCount).sum();
		if (carried == 0 && w.isCarrying()) {
			w.setCarried(ItemStack.EMPTY);
		}
		if (carried >= ARMFUL) {
			start(s, DELIVER, null, now);
			w.setTask("to the chest");
			return;
		}
		if (!mob.isBaby()) {
			BlockPos crop = cropToTend(level, mob, home, s);
			if (crop != null) {
				start(s, CROP, crop, now);
				w.setTask("tending the crops");
				return;
			}
			if (beastToTend(level, mob, s, home)) {
				s.goal = s.beast == null ? null : s.beast.blockPosition();
				s.best = Double.MAX_VALUE;
				s.improvedAt = now;
				s.actAt = 0;
				w.setTask("seeing to the animals");
				return;
			}
		}
		ItemEntity item = lying(level, home, mob);
		if (item != null) {
			start(s, GATHER, item.blockPosition(), now);
			s.item = item;
			w.setTask("gathering");
			return;
		}
		if (carried > 0) {
			start(s, DELIVER, null, now);
			w.setTask("to the chest");
		}
	}

	private static void start(State s, int phase, @Nullable BlockPos goal, long now) {
		s.phase = phase;
		s.goal = goal;
		s.best = Double.MAX_VALUE;
		s.improvedAt = now;
		s.actAt = 0;
	}

	/** The nearest ripe crop round the house, or bare farmland they have seed for. */
	private static @Nullable BlockPos cropToTend(ServerLevel level, PathfinderMob mob, BlockPos home, State s) {
		boolean seed = seedFor(s, level, mob, false) != null;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(home.offset(-FARM_REACH, -FARM_HEIGHT, -FARM_REACH), home.offset(FARM_REACH, FARM_HEIGHT, FARM_REACH))) {
			BlockState state = level.getBlockState(at);
			boolean ripe = state.getBlock() instanceof CropBlock c && c.isMaxAge(state);
			boolean bare = seed && state.isAir() && level.getBlockState(at.below()).is(Blocks.FARMLAND);
			if (ripe || bare) {
				double d = at.distSqr(mob.blockPosition());
				if (d < bestD) {
					bestD = d;
					best = at.immutable();
				}
			}
		}
		return best;
	}

	/** The crop a seed they have (in their arms, else the chest) grows; taken from where it was if {@code take}. */
	private static @Nullable Block seedFor(State s, ServerLevel level, PathfinderMob mob, boolean take) {
		for (ItemStack held : s.bag) {
			Block crop = crop(held);
			if (crop != null) {
				if (take) {
					held.shrink(1);
					s.bag.removeIf(ItemStack::isEmpty);
				}
				return crop;
			}
		}
		Container chest = chest(level, mob);
		if (chest != null) {
			for (int i = 0; i < chest.getContainerSize(); i++) {
				Block crop = crop(chest.getItem(i));
				if (crop != null) {
					if (take) {
						chest.removeItem(i, 1);
					}
					return crop;
				}
			}
		}
		return null;
	}

	private static @Nullable Block crop(ItemStack seed) {
		if (seed.is(Items.WHEAT_SEEDS)) {
			return Blocks.WHEAT;
		}
		if (seed.is(Items.CARROT)) {
			return Blocks.CARROTS;
		}
		if (seed.is(Items.POTATO)) {
			return Blocks.POTATOES;
		}
		if (seed.is(Items.BEETROOT_SEEDS)) {
			return Blocks.BEETROOTS;
		}
		return null;
	}

	private static void crop(ServerLevel level, Worker w, State s, long now) {
		PathfinderMob mob = w.mob();
		BlockPos at = s.goal;
		if (at == null || !reached(w, s, at, WORK_REACH, now)) {
			return;
		}
		if (s.actAt == 0) {
			s.actAt = now + WORK_TICKS;
			mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			return;
		}
		if (now < s.actAt) {
			mob.getLookControl().setLookAt(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
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
				bag(w, s, d);
			}
		} else if (state.isAir() && level.getBlockState(at.below()).is(Blocks.FARMLAND)) {
			Block sow = seedFor(s, level, mob, true);
			if (sow != null) {
				level.setBlock(at, sow.defaultBlockState(), Block.UPDATE_ALL);
				level.playSound(null, at, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
		}
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		s.phase = IDLE;
		s.lookAt = 0;
	}

	private static boolean livestock(Animal a) {
		return a instanceof AbstractCow || a instanceof Pig || a instanceof Sheep || a instanceof Chicken;
	}

	/** A beast to see to: a sheep to shear, a cow to milk (an empty bucket in the chest), or a pair to feed while few. */
	private static boolean beastToTend(ServerLevel level, PathfinderMob mob, State s, BlockPos home) {
		List<Animal> herd = level.getEntitiesOfClass(Animal.class, new AABB(home).inflate(FARM_REACH, FARM_HEIGHT, FARM_REACH),
			a -> a.isAlive() && livestock(a) && !a.isLeashed());
		herd.sort(Comparator.comparingDouble(a -> a.distanceToSqr(mob)));
		for (Animal a : herd) {
			if (a instanceof Sheep sheep && sheep.readyForShearing()) {
				s.phase = SHEAR;
				s.beast = a;
				return true;
			}
		}
		Container chest = chest(level, mob);
		if (chest != null && count(chest, Items.BUCKET) > 0 && count(chest, Items.MILK_BUCKET) < MILK_KEEP) {
			for (Animal a : herd) {
				if (a instanceof AbstractCow && !a.isBaby()) {
					s.phase = MILK;
					s.beast = a;
					return true;
				}
			}
		}
		Map<Class<?>, List<Animal>> kinds = new HashMap<>();
		for (Animal a : herd) {
			kinds.computeIfAbsent(a.getClass(), k -> new ArrayList<>()).add(a);
		}
		for (List<Animal> kind : kinds.values()) {
			List<Animal> ready = kind.stream().filter(a -> !a.isBaby() && a.getAge() == 0 && a.canFallInLove()).toList();
			if (kind.stream().filter(a -> !a.isBaby()).count() < HERD_KEEP && ready.size() >= 2 && food(s, level, mob, ready.getFirst(), false) >= 2) {
				s.phase = FEED;
				s.beast = ready.getFirst();
				return true;
			}
		}
		return false;
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	/** How much of {@code beast}'s food they have (arms, then chest); {@code take} takes two of it. */
	private static int food(State s, ServerLevel level, PathfinderMob mob, Animal beast, boolean take) {
		int have = 0, want = take ? 2 : 0;
		for (ItemStack held : s.bag) {
			if (beast.isFood(held)) {
				int n = Math.min(want, held.getCount());
				held.shrink(n);
				want -= n;
				have += held.getCount() + n;
			}
		}
		s.bag.removeIf(ItemStack::isEmpty);
		Container chest = chest(level, mob);
		if (chest != null) {
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack st = chest.getItem(i);
				if (!st.isEmpty() && beast.isFood(st)) {
					int n = Math.min(want, st.getCount());
					if (n > 0) {
						chest.removeItem(i, n);
						want -= n;
					}
					have += st.getCount() + n;
				}
			}
		}
		return have;
	}

	private static void beast(ServerLevel level, Worker w, State s, BlockPos home, long now) {
		PathfinderMob mob = w.mob();
		LivingEntity beast = s.beast;
		if (beast == null || !beast.isAlive()) {
			s.phase = IDLE;
			return;
		}
		if (!reached(w, s, beast.blockPosition(), WORK_REACH, now)) {
			return;
		}
		mob.getLookControl().setLookAt(beast, 30.0F, 30.0F);
		if (s.actAt == 0) {
			s.actAt = now + WORK_TICKS;
			return;
		}
		if (now < s.actAt) {
			return;
		}
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (s.phase == SHEAR && beast instanceof Sheep sheep && sheep.readyForShearing()) {
			// (the wool falls; they gather it up)
			sheep.shear(level, SoundSource.NEUTRAL, new ItemStack(Items.SHEARS));
		} else if (s.phase == MILK && beast instanceof AbstractCow) {
			Container chest = chest(level, mob);
			if (chest != null && take(chest, Items.BUCKET)) {
				level.playSound(null, beast.blockPosition(), SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
				bag(w, s, new ItemStack(Items.MILK_BUCKET));
			}
		} else if (s.phase == FEED && beast instanceof Animal a) {
			// two of its kind fed: they pair off
			List<Animal> mates = level.getEntitiesOfClass(Animal.class, new AABB(home).inflate(FARM_REACH, FARM_HEIGHT, FARM_REACH),
				x -> x != a && x.isAlive() && x.getClass() == a.getClass() && !x.isBaby() && x.getAge() == 0 && x.canFallInLove());
			if (!mates.isEmpty() && a.canFallInLove() && food(s, level, mob, a, false) >= 2) {
				food(s, level, mob, a, true);
				Animal mate = mates.stream().min(Comparator.comparingDouble(x -> x.distanceToSqr(a))).orElseThrow();
				a.setInLove(null);
				mate.setInLove(null);
				level.playSound(null, a.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F, 1.0F);
			}
		}
		s.beast = null;
		s.phase = IDLE;
		s.lookAt = 0;
	}

	private static boolean take(Container c, Item item) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				c.removeItem(i, 1);
				return true;
			}
		}
		return false;
	}

	/** Something lying round the house long enough to be nobody's just-dropped: eggs, wool, saplings, a harvest's spill. */
	private static @Nullable ItemEntity lying(ServerLevel level, BlockPos home, PathfinderMob mob) {
		// (not what a full chest left beside it: they'd only carry it back)
		BlockPos chest = tagPos(mob, CHEST_TAG);
		BlockPos left = chest != null ? chest : home;
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(home).inflate(GATHER_REACH, FARM_HEIGHT, GATHER_REACH),
				i -> i.isAlive() && !i.hasPickUpDelay() && i.getAge() >= GATHER_AGE && !i.isInWater() && !i.isInLava()
					&& i.distanceToSqr(Vec3.atCenterOf(left)) > CHEST_REACH * CHEST_REACH)
			.stream().min(Comparator.comparingDouble(i -> i.distanceToSqr(mob))).orElse(null);
	}

	private static void gather(ServerLevel level, Worker w, State s, long now) {
		ItemEntity item = s.item;
		if (item == null || !item.isAlive()) {
			s.phase = IDLE;
			s.lookAt = 0;
			return;
		}
		if (!reached(w, s, item.blockPosition(), WORK_REACH - 0.5, now)) {
			return;
		}
		w.mob().swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, item.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 1.2F);
		bag(w, s, item.getItem().copy());
		item.discard();
		s.item = null;
		s.phase = IDLE;
		s.lookAt = 0;
	}

	/** To the house chest with their load; what won't fit is left by it (no chest: by the home spot). */
	private static void deliver(ServerLevel level, Worker w, State s, BlockPos home, long now) {
		PathfinderMob mob = w.mob();
		BlockPos chestAt = tagPos(mob, CHEST_TAG);
		BlockPos to = chestAt != null ? chestAt : home;
		if (!reached(w, s, to, CHEST_REACH, now)) {
			return;
		}
		Container chest = chest(level, mob);
		for (ItemStack stack : s.bag) {
			ItemStack left = chest == null ? stack : HopperBlockEntity.addItem(null, chest, stack, null);
			if (!left.isEmpty()) {
				Block.popResource(level, to, left);
			}
		}
		s.bag.clear();
		w.setCarried(ItemStack.EMPTY);
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, to, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
		s.phase = IDLE;
		s.lookAt = 0;
	}

	/** Into their arms (the biggest stack shows in their hands). */
	private static void bag(Worker w, State s, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		for (ItemStack held : s.bag) {
			if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
				int n = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
				held.grow(n);
				stack.shrink(n);
			}
		}
		if (!stack.isEmpty()) {
			s.bag.add(stack.copy());
		}
		ItemStack show = s.bag.stream().max(Comparator.comparingInt(ItemStack::getCount)).orElse(ItemStack.EMPTY);
		w.setCarried(show.copyWithCount(1));
	}

	/** Walks them toward {@code to}; true once within {@code reach}. Stuck too long, the errand's given up. */
	private static boolean reached(Worker w, State s, BlockPos to, double reach, long now) {
		if (walk(w, s, to, reach, now)) {
			return true;
		}
		if (now - s.improvedAt > STUCK_TICKS) {
			s.phase = IDLE;
			s.beast = null;
			s.item = null;
			s.lookAt = now + WORK_LOOK_TICKS;
		}
		return false;
	}

	private static boolean walk(Worker w, State s, BlockPos to, double reach, long now) {
		PathfinderMob mob = w.mob();
		double d = Math.sqrt(mob.distanceToSqr(Vec3.atBottomCenterOf(to)));
		if (d <= reach) {
			mob.getNavigation().stop();
			return true;
		}
		if (d < s.best - 0.5) {
			s.best = d;
			s.improvedAt = now;
		}
		if (mob.getNavigation().isDone() || now % 20 == 0) {
			mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, WALK_SPEED);
		}
		return false;
	}

	// ---- the chest --------------------------------------------------------------------------------------------------

	/** Their house chest, if it's still there. */
	private static @Nullable Container chest(ServerLevel level, PathfinderMob mob) {
		BlockPos at = tagPos(mob, CHEST_TAG);
		if (at == null || !level.isLoaded(at)) {
			return null;
		}
		BlockState state = level.getBlockState(at);
		if (state.getBlock() instanceof ChestBlock block) {
			return ChestBlock.getContainer(block, state, level, at, true);
		}
		return level.getBlockEntity(at) instanceof BarrelBlockEntity barrel ? barrel : null;
	}

	/** A chest or barrel already in the house, or null. */
	private static @Nullable BlockPos findChest(ServerLevel level, BlockPos home) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(home.offset(-HOUSE_REACH, -HOUSE_HEIGHT, -HOUSE_REACH), home.offset(HOUSE_REACH, HOUSE_HEIGHT, HOUSE_REACH))) {
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
				double d = p.distSqr(home);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** A chest put on the floor beside the home spot (not on it), or null with no room. */
	private static @Nullable BlockPos placeChest(ServerLevel level, BlockPos home) {
		for (int r = 1; r <= 3; r++) {
			for (BlockPos p : BlockPos.betweenClosed(home.offset(-r, 0, -r), home.offset(r, 0, r))) {
				if (p.equals(home) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
					|| !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
					continue;
				}
				BlockPos at = p.immutable();
				level.setBlock(at, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
				return at;
			}
		}
		return null;
	}
}
