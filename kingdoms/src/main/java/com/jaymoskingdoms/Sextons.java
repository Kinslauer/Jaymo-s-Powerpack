package com.jaymoskingdoms;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "there should be a groundskeeper job or something similar that buries / disposes of dead bodies / limbs, repairs
 * golems, and washes blood/grime". The <b>sexton</b> ({@link Professions#SEXTON}), the Lantern Warden's keeper in a town:
 * <ul>
 * <li><b>Job site:</b> the lych lantern ({@link LychLanternBlock}), a real one, so a villager without a trade takes it
 * up as the game's own job sites are taken. Every town keeps a sexton as it keeps a cook ({@link TownTrades}); a town
 * with a sexton and a burying ground but no lantern gets one, set up by the sexton beside its first grave.</li>
 * <li><b>The day:</b> at dusk ({@link #LIGHT_FROM}) the sexton walks to the lantern and lights it, at dawn puts it out;
 * between, in the working day ({@link #WORK_FROM}-{@link #WORK_TO}), they see to the town.</li>
 * <li><b>The dead:</b> a free sexton is sent for a body before anyone else, to bury the town's own and burn the rest
 * ({@link Bearers}, {@link Undertakers}: the guards keep their posts).</li>
 * <li><b>What's left lying:</b> a severed head or limb (Powers' parts) left a while ({@link #PART_SETTLE}, so it's yours
 * first) is buried where it lies, spade in hand, if the ground there takes a spade; on a street or a floor it goes in a
 * sack and is buried on the nearest open ground. The mess a blast leaves (Kitchen's gibs) is shovelled up with it.
 * Never a kept trophy, nor anything a player is carrying.</li>
 * <li><b>Golems:</b> mended with iron from the storehouse, as a smith does ({@link SmithMending}).</li>
 * <li><b>Blood and grime:</b> a guard or a golem who comes back bloody or filthy is washed down, bucket in hand
 * ({@link PowersCompat#washAll}); blood in the streets is scrubbed out as well as indoors ({@link BloodChores}).</li>
 * </ul>
 * They speak of it now and then, aloud (speech bubbles, never chat). A player's body is never theirs to touch.
 */
public final class Sextons {
	/** How often tasks are driven (ticks); how often a sexton looks for work; how far round a seat sextons are watched. */
	static final int DRIVE = 5, THINK = 40;
	static final double WATCHED = 96.0;
	/** The working day; the lantern is lit from dusk, and put out after dawn (time of day). */
	static final long WORK_FROM = 2000L, WORK_TO = 11000L, LIGHT_FROM = 11000L, LIGHT_TO = 13000L, SNUFF_TO = 2000L;
	/** How far a sexton looks for work, and for their lantern. */
	static final double REACH = 40.0;
	static final int LANTERN_REACH = 48;
	/** A severed part lies this long untouched (ticks) before it's buried. */
	static final long PART_SETTLE = 1200L;
	/** Close enough to pick a part up or dig; to light the lantern; to wash someone. */
	static final double ARRIVE = 1.8, LANTERN_ARRIVE = 2.2, WASH_ARRIVE = 2.4;
	/** Digging: how long (ticks), a spadeful this often; gibs this close to the hole are shovelled in with it. */
	static final int DIG_TICKS = 60, DIG_EVERY = 10;
	static final double GIB_SWEEP = 3.0;
	/** Open ground for a bagged part: looked for this far off at most, in rings this far apart, no higher or lower than this. */
	static final int SPOT_REACH = 24, RING_STEP = 2, RING_ANGLES = 16, SPOT_RISE = 6;
	/** Washing: how long (ticks), a splash this often, how much of the dirt comes off; the same body is left this long after. */
	static final int WASH_TICKS = 80, WASH_EVERY = 10, WASH_AMOUNT = 60;
	static final long WASHED_GAP = 2400L;
	/** The chance each think that a sexton with nothing else on goes to wash someone (so they don't hound the guards). */
	static final float WASH_CHANCE = 0.25F;
	/** A zombie's rotten blood worth washing off (share, 0 to 1). */
	static final float ZOMBIE_BLOOD = 0.1F;
	/** How long a task may take before it's given up (ticks); one given up on is left this long. */
	static final long GIVE_UP = 1200L, RETRY = 1200L;
	/** Their pace. */
	static final float WALK = 0.55F;
	/** Talk: lines per family, the chance a task is spoken of, how long between one sexton's words, how far they carry. */
	static final int LINES = 6;
	static final float TALK_CHANCE = 0.4F;
	static final long TALK_GAP = 600L;
	static final double TALK_RANGE = 14.0;
	/** Where a new lantern goes, from the first headstone (x, z), tried in turn. */
	private static final int[][] LANTERN_SPOTS = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}, {-3, 0}, {3, 0}, {0, -3}, {0, 3}};

	public static Block LANTERN;
	public static Item LANTERN_ITEM;
	static ResourceKey<PoiType> POI;

	enum Kind { BURY, SWEEP, WASH, LIGHT, SNUFF, SETUP }

	enum Stage { GO, HAUL, WORK }

	private static final class Task {
		final Kind kind;
		final @Nullable UUID target;
		/** Where the work is done: the hole, the lantern, the new lantern's spot (null to follow the target). */
		@Nullable BlockPos at;
		final long until;
		Stage stage = Stage.GO;
		long working = -1;
		/** A part already in the sack. */
		boolean bagged;

		Task(Kind kind, @Nullable UUID target, @Nullable BlockPos at, long until) {
			this.kind = kind;
			this.target = target;
			this.at = at;
			this.until = until;
		}
	}

	private static final Map<UUID, Task> TASKS = new HashMap<>();
	/** Severed parts seen lying, and when first. */
	private static final Map<UUID, Long> SEEN = new HashMap<>();
	/** Parts, bodies and spots given up on lately, till when; bodies washed lately, till when. */
	private static final Map<UUID, Long> LEFT = new HashMap<>(), WASHED = new HashMap<>();
	/** When each sexton last spoke. */
	private static final Map<UUID, Long> SPOKE = new HashMap<>();
	private static @Nullable Class<?> gib;
	private static boolean lookedForGib;

	private Sextons() {
	}

	/** The lych lantern, its item and its point of interest, and the profession that works at it ({@link Professions#register}). */
	static VillagerProfession profession() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("lych_lantern"));
		LANTERN = Registry.register(BuiltInRegistries.BLOCK, key, new LychLanternBlock(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.POPPED)
			.lightLevel(state -> state.getValue(LychLanternBlock.LIT) ? LychLanternBlock.LIGHT : 0)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("lych_lantern"));
		LANTERN_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(LANTERN, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(LANTERN_ITEM));
		PoiHelper.register(JaymosKingdoms.id("sexton"), 1, 1, LANTERN);
		POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, JaymosKingdoms.id("sexton"));
		Int2ObjectOpenHashMap<ResourceKey<TradeSet>> trades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 4; level++) {
			trades.put(level, ResourceKey.create(Registries.TRADE_SET, JaymosKingdoms.id("sexton/level_" + level)));
		}
		return new VillagerProfession(Component.translatable("entity.minecraft.villager.jaymoskingdoms.sexton"), h -> h.is(POI), h -> h.is(POI),
			ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_MASON, trades);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Sextons::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TASKS.clear();
			SEEN.clear();
			LEFT.clear();
			WASHED.clear();
			SPOKE.clear();
		});
	}

	/** {@code v} is a grown sexton. */
	public static boolean is(Villager v) {
		return !v.isBaby() && Professions.SEXTON != null && v.getVillagerData().profession().value() == Professions.SEXTON;
	}

	/** {@code mob} is at a sexton's task just now. */
	static boolean busy(Entity mob) {
		return TASKS.containsKey(mob.getUUID());
	}

	/** A sexton in the working day with nothing else on: no task, no body to carry, no golem to mend, no blood to scrub. */
	static boolean free(Villager v) {
		long t = v.level() instanceof ServerLevel level ? Royals.timeOfDay(level) : 0L;
		return t >= WORK_FROM && t < WORK_TO && idle(v);
	}

	/** Grown, awake, on their feet and at nothing else, whatever the hour. */
	private static boolean idle(Villager v) {
		return v.isAlive() && !v.isBaby() && !v.isSleeping() && !TASKS.containsKey(v.getUUID()) && Routines.idle(v) && Bearers.freeVillager(v)
			&& !SmithMending.working(v) && !BloodChores.scrubbing(v) && !Downed.isDowned(v) && !TalkBusy.occupied(v) && !Meals.eating(v);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % DRIVE != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		if (!TASKS.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Task>> it = TASKS.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Task> e = it.next();
				Villager v = level.getEntity(e.getKey()) instanceof Villager found ? found : null;
				if (v == null || !step(level, v, e.getValue(), now)) {
					it.remove();
					if (v != null) {
						v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					}
				}
			}
		}
		if (now % THINK != 0) {
			return;
		}
		LEFT.values().removeIf(until -> until < now);
		WASHED.values().removeIf(until -> until < now);
		SPOKE.values().removeIf(until -> until < now);
		SEEN.values().removeIf(first -> now - first > GIVE_UP * 10L);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			AABB town = new AABB(f.seat()).inflate(WATCHED, 32.0, WATCHED);
			for (Entity part : level.getEntities((Entity)null, town, PowersCompat::litterPart)) {
				SEEN.putIfAbsent(part.getUUID(), now);
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, town, Sextons::is)) {
				if (idle(v)) {
					think(level, f, v, t, now);
				}
			}
		}
	}

	/** What {@code v} takes up now, by the hour: the lantern at dusk and dawn, the town's work by day. */
	private static void think(ServerLevel level, Faction f, Villager v, long t, long now) {
		BlockPos lantern = lanternOf(level, v);
		if (t >= LIGHT_FROM && t < LIGHT_TO) {
			if (lantern != null && !level.getBlockState(lantern).getValue(LychLanternBlock.LIT)) {
				start(v, new Task(Kind.LIGHT, null, lantern, now + GIVE_UP), null);
			}
			return;
		}
		if (t < SNUFF_TO || t >= 23000L) {
			if (lantern != null && level.getBlockState(lantern).getValue(LychLanternBlock.LIT)) {
				start(v, new Task(Kind.SNUFF, null, lantern, now + GIVE_UP), null);
			}
			return;
		}
		if (t < WORK_FROM || t >= WORK_TO) {
			return;
		}
		AABB near = v.getBoundingBox().inflate(REACH, 12.0, REACH);
		// what a fight left lying first, then the mess a blast left, then the dirty, then a lantern for the burying ground
		Entity part = nearest(v, level.getEntities((Entity)null, near, e -> PowersCompat.litterPart(e) && !LEFT.containsKey(e.getUUID())
			&& now - SEEN.getOrDefault(e.getUUID(), now) >= PART_SETTLE && !claimed(e.getUUID()) && ours(level, f, e.blockPosition())));
		if (part != null) {
			start(v, new Task(Kind.BURY, part.getUUID(), null, now + GIVE_UP), "bury");
			return;
		}
		Entity mess = nearest(v, level.getEntities((Entity)null, near, e -> isGib(e) && ours(level, f, e.blockPosition())));
		if (mess != null && !sweeping(mess.blockPosition())) {
			start(v, new Task(Kind.SWEEP, null, mess.blockPosition(), now + GIVE_UP / 2), "bury");
			return;
		}
		if (level.getRandom().nextFloat() < WASH_CHANCE) {
			LivingEntity dirty = (LivingEntity)nearest(v, level.getEntitiesOfClass(LivingEntity.class, near, e -> e != v && dirty(e)
				&& !WASHED.containsKey(e.getUUID()) && !claimed(e.getUUID()) && ours(level, f, e.blockPosition())));
			if (dirty != null) {
				WASHED.put(dirty.getUUID(), now + WASHED_GAP);
				start(v, new Task(Kind.WASH, dirty.getUUID(), null, now + GIVE_UP), "wash");
				return;
			}
		}
		if (lantern == null && !LEFT.containsKey(v.getUUID())) {
			BlockPos spot = lanternSpot(level, f);
			if (spot != null) {
				start(v, new Task(Kind.SETUP, null, spot, now + GIVE_UP), "setup");
			} else {
				// no burying ground yet, or no room by it: looked at again later
				LEFT.put(v.getUUID(), now + RETRY * 5L);
			}
		}
	}

	private static void start(Villager v, Task task, @Nullable String family) {
		TASKS.put(v.getUUID(), task);
		v.setItemSlot(EquipmentSlot.MAINHAND, switch (task.kind) {
			case BURY, SWEEP, SETUP -> new ItemStack(Items.IRON_SHOVEL);
			case WASH -> new ItemStack(Items.WATER_BUCKET);
			case LIGHT, SNUFF -> ItemStack.EMPTY;
		});
		if (family != null && v.level() instanceof ServerLevel level && level.getRandom().nextFloat() < TALK_CHANCE) {
			say(level, v, family);
		}
	}

	/** One step of {@code v}'s task; false once it's over (done, or broken off). */
	private static boolean step(ServerLevel level, Villager v, Task task, long now) {
		if (!v.isAlive() || v.isSleeping() || v.getTarget() != null || Downed.isDowned(v) || Frights.afraid(v.getUUID()) || McaCompat.panicking(v)
			|| TalkBusy.occupied(v) || now > task.until) {
			if (task.target != null && now > task.until) {
				LEFT.put(task.target, now + RETRY);
			}
			return false;
		}
		return switch (task.kind) {
			case BURY -> bury(level, v, task, now);
			case SWEEP -> sweep(level, v, task, now);
			case WASH -> wash(level, v, task, now);
			case LIGHT, SNUFF -> lantern(level, v, task);
			case SETUP -> setUp(level, v, task);
		};
	}

	// ---- burying what's left lying ------------------------------------------------------------------------------------

	private static boolean bury(ServerLevel level, Villager v, Task task, long now) {
		Entity part = task.target == null ? null : level.getEntity(task.target);
		switch (task.stage) {
			case GO -> {
				if (part == null || !PowersCompat.litterPart(part)) {
					// gone (eaten, picked up, rotted away)
					return false;
				}
				if (v.distanceToSqr(part) > ARRIVE * ARRIVE) {
					walk(v, part.blockPosition());
					return true;
				}
				BlockPos ground = BlockPos.containing(part.position()).below();
				if (diggable(level.getBlockState(ground)) && level.getBlockState(ground.above()).canBeReplaced()) {
					// buried where it lies
					task.at = ground.above();
					task.stage = Stage.WORK;
					return true;
				}
				BlockPos spot = openGround(level, v.blockPosition());
				if (spot == null) {
					LEFT.put(part.getUUID(), now + RETRY);
					return false;
				}
				// into the sack, and off to open ground with it
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.NEUTRAL, 0.8F, 0.8F);
				part.discard();
				task.bagged = true;
				task.at = spot;
				task.stage = Stage.HAUL;
				v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BUNDLE));
			}
			case HAUL -> {
				if (task.at == null) {
					return false;
				}
				if (v.distanceToSqr(Vec3.atBottomCenterOf(task.at)) > ARRIVE * ARRIVE) {
					walk(v, task.at);
					return true;
				}
				v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
				task.stage = Stage.WORK;
			}
			case WORK -> {
				if (task.at == null || !task.bagged && (part == null || !PowersCompat.litterPart(part))) {
					return false;
				}
				if (!dig(level, v, task, now)) {
					return true;
				}
				if (!task.bagged && part != null) {
					part.discard();
				}
				shovelGibs(level, Vec3.atBottomCenterOf(task.at));
				level.playSound(null, task.at, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
				return false;
			}
		}
		return true;
	}

	private static boolean sweep(ServerLevel level, Villager v, Task task, long now) {
		if (task.at == null) {
			return false;
		}
		Vec3 at = Vec3.atBottomCenterOf(task.at);
		if (level.getEntities((Entity)null, new AABB(at, at).inflate(GIB_SWEEP), Sextons::isGib).isEmpty()) {
			// sunk away before they got there, or swept up
			return false;
		}
		if (task.stage == Stage.GO) {
			if (v.distanceToSqr(at) > ARRIVE * ARRIVE) {
				walk(v, task.at);
				return true;
			}
			task.stage = Stage.WORK;
		}
		if (dig(level, v, task, now)) {
			shovelGibs(level, at);
			return false;
		}
		// a spadeful at a time
		if ((now - task.working) % DIG_EVERY == 0) {
			shovelGibs(level, at, 1);
		}
		return true;
	}

	/** A spadeful at the ground at {@code task.at}; true once the hole's dug and filled. */
	private static boolean dig(ServerLevel level, Villager v, Task task, long now) {
		BlockPos hole = task.at;
		if (task.working < 0) {
			task.working = now;
		}
		v.getNavigation().stop();
		v.getLookControl().setLookAt(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5);
		if ((now - task.working) % DIG_EVERY == 0) {
			BlockState ground = level.getBlockState(hole.below());
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
				hole.getX() + 0.5, hole.getY() + 0.1, hole.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.1);
			level.playSound(null, hole, SoundEvents.SHOVEL_FLATTEN.value(), SoundSource.NEUTRAL, 0.7F, 0.8F + level.getRandom().nextFloat() * 0.2F);
		}
		return now - task.working >= DIG_TICKS;
	}

	private static void shovelGibs(ServerLevel level, Vec3 at) {
		shovelGibs(level, at, Integer.MAX_VALUE);
	}

	/** Up to {@code most} of the gibs lying round {@code at} go in the hole. */
	private static void shovelGibs(ServerLevel level, Vec3 at, int most) {
		int n = 0;
		for (Entity g : level.getEntities((Entity)null, new AABB(at, at).inflate(GIB_SWEEP), Sextons::isGib)) {
			if (n++ >= most) {
				return;
			}
			g.discard();
		}
	}

	/** Whether another sexton is already shovelling up the mess near {@code pos}. */
	private static boolean sweeping(BlockPos pos) {
		for (Task t : TASKS.values()) {
			if (t.kind == Kind.SWEEP && t.at != null && t.at.distSqr(pos) < GIB_SWEEP * GIB_SWEEP * 4) {
				return true;
			}
		}
		return false;
	}

	/** Ground a spade goes into: earth, sand, gravel. */
	static boolean diggable(BlockState ground) {
		return ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL);
	}

	/**
	 * The nearest open ground to {@code from} that takes a spade (out under the sky, nothing on it, not much higher or
	 * lower), within {@link #SPOT_REACH}; null if there's none.
	 */
	private static @Nullable BlockPos openGround(ServerLevel level, BlockPos from) {
		for (int r = RING_STEP; r <= SPOT_REACH; r += RING_STEP) {
			for (int a = 0; a < RING_ANGLES; a++) {
				double angle = Math.PI * 2.0 * a / RING_ANGLES;
				int x = from.getX() + (int)Math.round(Math.cos(angle) * r), z = from.getZ() + (int)Math.round(Math.sin(angle) * r);
				if (!level.isLoaded(new BlockPos(x, from.getY(), z))) {
					continue;
				}
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				if (Math.abs(top.getY() - from.getY()) <= SPOT_RISE && diggable(level.getBlockState(top.below()))
					&& level.getBlockState(top).canBeReplaced() && level.getFluidState(top).isEmpty() && level.canSeeSky(top)) {
					return top;
				}
			}
		}
		return null;
	}

	/** Kitchen's gib (a chunk of a body blown apart), reached by name. */
	private static boolean isGib(Entity e) {
		if (!lookedForGib) {
			lookedForGib = true;
			try {
				gib = Class.forName("com.jaymoskitchen.corpse.Gib");
			} catch (ReflectiveOperationException | LinkageError ex) {
				gib = null;
			}
		}
		return gib != null && gib.isInstance(e) && !e.isRemoved();
	}

	// ---- washing the guards and the golems ----------------------------------------------------------------------------

	/** A guard or a golem with blood or grime on them that's worth a word (Powers), and not in a fight. */
	private static boolean dirty(LivingEntity e) {
		if (!e.isAlive() || Downed.isDowned(e) || !(Downed.isGuard(e) || e instanceof AbstractGolem) || e instanceof Mob m && m.getTarget() != null) {
			return false;
		}
		return PowersCompat.bloodSeen(e) >= 0 || PowersCompat.grimeSeen(e) >= 0 || PowersCompat.zombieBlood(e) >= ZOMBIE_BLOOD;
	}

	private static boolean wash(ServerLevel level, Villager v, Task task, long now) {
		if (task.target == null || !(level.getEntity(task.target) instanceof LivingEntity body) || !body.isAlive() || Downed.isDowned(body)
			|| body instanceof Mob m && m.getTarget() != null) {
			return false;
		}
		if (task.stage == Stage.GO) {
			if (v.distanceToSqr(body) > WASH_ARRIVE * WASH_ARRIVE) {
				if (v.getNavigation().isDone() || now % 20 == 0) {
					walk(v, body.blockPosition());
				}
				return true;
			}
			task.stage = Stage.WORK;
			task.working = now;
		}
		if (v.distanceToSqr(body) > WASH_ARRIVE * WASH_ARRIVE * 2.0) {
			// they walked off: after them
			task.stage = Stage.GO;
			return true;
		}
		v.getNavigation().stop();
		if (body instanceof Mob m) {
			// held still for it
			m.getNavigation().stop();
			m.getLookControl().setLookAt(v);
		}
		v.getLookControl().setLookAt(body, 30.0F, 30.0F);
		if ((now - task.working) % WASH_EVERY == 0) {
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			Vec3 at = body.position().add(0.0, body.getBbHeight() * 0.6, 0.0);
			level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 10, body.getBbWidth() * 0.4, body.getBbHeight() * 0.3, body.getBbWidth() * 0.4, 0.1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		}
		if (now - task.working < WASH_TICKS) {
			return true;
		}
		PowersCompat.washAll(body, WASH_AMOUNT);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.7F, 1.0F);
		return false;
	}

	// ---- the lych lantern ---------------------------------------------------------------------------------------------

	/** {@code v}'s lantern: the job site they hold, else the nearest one round them; null if none. */
	static @Nullable BlockPos lanternOf(ServerLevel level, Villager v) {
		Optional<GlobalPos> site = v.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE) ? v.getBrain().getMemory(MemoryModuleType.JOB_SITE) : Optional.empty();
		if (site.isPresent() && site.get().dimension() == level.dimension() && level.getBlockState(site.get().pos()).is(LANTERN)) {
			return site.get().pos();
		}
		return level.getPoiManager().findClosest(h -> h.is(POI), v.blockPosition(), LANTERN_REACH, PoiManager.Occupancy.ANY)
			.filter(p -> level.getBlockState(p).is(LANTERN)).orElse(null);
	}

	private static boolean lantern(ServerLevel level, Villager v, Task task) {
		BlockPos at = task.at;
		if (at == null || !level.getBlockState(at).is(LANTERN)) {
			return false;
		}
		if (v.distanceToSqr(Vec3.atCenterOf(at)) > LANTERN_ARRIVE * LANTERN_ARRIVE) {
			walk(v, at);
			return true;
		}
		boolean light = task.kind == Kind.LIGHT;
		v.getNavigation().stop();
		v.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5);
		v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.setBlockAndUpdate(at, level.getBlockState(at).setValue(LychLanternBlock.LIT, light));
		level.playSound(null, at, light ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
		if (!light) {
			level.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 4, 0.05, 0.05, 0.05, 0.01);
		}
		if (level.getRandom().nextFloat() < TALK_CHANCE) {
			say(level, v, light ? "light" : "snuff");
		}
		return false;
	}

	/** Where {@code f}'s burying ground could have its lantern: open ground beside its first headstone; null with no graves, or no lantern needed. */
	private static @Nullable BlockPos lanternSpot(ServerLevel level, Faction f) {
		BlockPos first = Bearers.firstGrave(level, f.id());
		if (first == null || !level.isLoaded(first)
			|| level.getPoiManager().findClosest(h -> h.is(POI), first, LANTERN_REACH / 2, PoiManager.Occupancy.ANY).isPresent()) {
			return null;
		}
		for (int[] o : LANTERN_SPOTS) {
			int x = first.getX() + o[0], z = first.getZ() + o[1];
			BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(top.getY() - first.getY()) <= 2 && level.getBlockState(top).canBeReplaced() && level.getBlockState(top.above()).isAir()
				&& level.getFluidState(top).isEmpty() && level.getBlockState(top.below()).isFaceSturdy(level, top.below(), Direction.UP)
				&& !level.getBlockState(top.below()).is(Graves.GRAVE) && !level.getBlockState(top.below()).is(BlockTags.WALLS)) {
				return top;
			}
		}
		return null;
	}

	private static boolean setUp(ServerLevel level, Villager v, Task task) {
		BlockPos at = task.at;
		if (at == null || !level.getBlockState(at).canBeReplaced()) {
			return false;
		}
		if (v.distanceToSqr(Vec3.atBottomCenterOf(at)) > LANTERN_ARRIVE * LANTERN_ARRIVE) {
			walk(v, at);
			return true;
		}
		v.getNavigation().stop();
		v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		// the lamp hung out toward whoever set it up
		Direction facing = Direction.getApproximateNearest(v.getX() - (at.getX() + 0.5), 0.0, v.getZ() - (at.getZ() + 0.5));
		if (facing.getAxis().isVertical()) {
			facing = Direction.NORTH;
		}
		level.setBlockAndUpdate(at, LANTERN.defaultBlockState().setValue(LychLanternBlock.FACING, facing));
		level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		return false;
	}

	// ---- shared -------------------------------------------------------------------------------------------------------

	/** {@code pos} lies in {@code f}'s own land. */
	private static boolean ours(ServerLevel level, Faction f, BlockPos pos) {
		Faction law = Theft.lawAt(level, pos);
		return law != null && law.id() == f.id();
	}

	private static boolean claimed(UUID target) {
		for (Task t : TASKS.values()) {
			if (target.equals(t.target)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Entity nearest(Villager v, List<? extends Entity> of) {
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : of) {
			double d = v.distanceToSqr(e);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static void walk(Villager v, BlockPos to) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, to, WALK, 1);
	}

	/** {@code v} says a line of {@code family} ({@code talk.jaymoskingdoms.sexton.<family>.<n>}), unless they spoke lately. */
	static void say(ServerLevel level, Villager v, String family) {
		long now = level.getGameTime();
		Long until = SPOKE.get(v.getUUID());
		if (until != null && until > now) {
			return;
		}
		SPOKE.put(v.getUUID(), now + TALK_GAP);
		Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.sexton." + family + "." + level.getRandom().nextInt(LINES)), Speech.WHITE, TALK_RANGE);
	}
}
