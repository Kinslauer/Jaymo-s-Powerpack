package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-21): "i want to be able to SEE the villagers doing their jobs". {@link Jobs} and {@link Meals} only had a
 * villager look at its workstation, swing an empty arm and puff a little smoke now and then; this is what you see instead,
 * while a player is near enough to see it ({@link #WATCHED}):
 * <ul>
 * <li><b>the tool of the trade in hand</b> at work: a smith's hammer, a butcher's cleaver, a cook's knife, a fletcher's
 * knife, a mason's pick, a librarian's book, and so on ({@link #tool}), worked at the workstation; put away when they stop;</li>
 * <li><b>the smith at the forge</b>: the furnace by their workstation lights while they heat the metal, then the hot ingot in
 * their tongs hand goes to the anvil and is hammered (the anvil rings, sparks fly), quenched in a water cauldron if there is
 * one (a hiss and steam), and set aside, and again;</li>
 * <li><b>the cook at the stove</b> ({@link #cook}, from {@link Meals#cookAt}): the raw food cut up at the counter, cooked at
 * the lit smoker (or furnace or fire), then set out on the counter as a dish you can see lying there a while.</li>
 * </ul>
 * It is all to be seen: what is made is still {@link Jobs}' and {@link Meals}' batches from the storehouse, so nothing comes
 * from nowhere. A furnace is lit only while it is empty (nobody's smelting in it) and put out again after; the tools are only
 * put in an empty hand and taken back only if they are still there, and never dropped when a villager dies.
 */
final class WorkScenes {
	/** How often the scenes move on (ticks); how near a player must be for a villager's work to be shown. */
	static final int EVERY = 5;
	static final double WATCHED = 48.0;
	/** Starting work this near the workstation; the forge's furnace, anvil and water this near it. */
	static final double START_AT = Jobs.AT_SITE + 1.0;
	static final int FORGE_REACH = 6;
	/** The forge, in ticks: heating the metal, hammering it, quenching it, and a rest before the next piece (the least, and up to so much more). */
	static final int HEAT_TICKS = 160, HAMMER_TICKS = 220, QUENCH_TICKS = 40, REST_MIN = 60, REST_MORE = 120;
	/** A hammer blow every so many ticks. */
	static final int STRIKE_EVERY = 10;
	/** Any other trade: a stroke of its tool every so often (the least, and up to so much more). */
	static final int STROKE_MIN = 25, STROKE_MORE = 40;
	/** The cook, in ticks: cutting at the counter, cooking at the stove, setting the dish out, and a rest. */
	static final int PREP_TICKS = 120, COOK_TICKS = 200, SERVE_TICKS = 30, COOK_REST = 80;
	/** The cook's stove and counter this near where the meals are handed over (inside {@link Meals#AT_KITCHEN}, so they stay at work). */
	static final int KITCHEN_REACH = 3;
	/** Dishes set out on one counter at most, and how long one lies there (ticks). */
	static final int PLATES = 3, PLATE_TICKS = 2400;
	static final float PLATE_SCALE = 0.45F;
	/** How near a spot counts as there; how long the walk to it may take before they make do where they are. */
	static final double ARRIVE = 1.4;
	static final int TRAVEL_TICKS = 160;
	static final double WALK = 0.5;
	/** A scene not moved on for this long (ticks) is over (the villager stopped work, left, or was unloaded). */
	static final int STALE = 40;
	/** The sparks off the anvil. */
	static final int SPARKS = 5;
	static final float SPARK_SCALE = 0.45F;

	private static final int TRADE = 0, HEAT = 1, HAMMER = 2, QUENCH = 3, REST = 4, PREP = 5, COOK = 6, SERVE = 7, COOK_RESTING = 8;
	private static final String PLATE_TAG = "jaymoskingdoms.work_plate";
	/** Marks what the work put in a villager's hand, so it is taken back even after a restart broke the scene off. */
	private static final String MARK = "jaymoskingdoms_work";

	private static final class Scene {
		int phase;
		long since;
		long arrived = -1L;
		long touched;
		long next;
		int length;
		/** The block worked at just now and where to stand for it. */
		BlockPos at;
		BlockPos stand;
		/** The smith's furnace, anvil and water; the cook's stove and counter top. */
		@Nullable BlockPos furnace, anvil, water, stove, counter;
		/** What was put in their hands (taken back only if still there), and the furnace lit for the work. */
		@Nullable Item main, off;
		@Nullable BlockPos lit;
		Item raw = Items.BEEF, cooked = Items.COOKED_BEEF;

		Scene(int phase, BlockPos at, BlockPos stand, long now) {
			this.phase = phase;
			this.at = at;
			this.stand = stand;
			this.since = now;
			this.touched = now;
		}
	}

	private record Plate(Display.ItemDisplay display, long gone) {
	}

	private static final Map<UUID, Scene> SCENES = new HashMap<>();
	/** The dishes out on each counter (by the stove's position), oldest first. */
	private static final Map<BlockPos, List<Plate>> PLATED = new HashMap<>();
	private static final Set<UUID> LIVE_PLATES = new HashSet<>();

	/** The raw food a cook works and what it becomes, the first the storehouse has. */
	private static final Item[][] DISHES = {
		{Items.BEEF, Items.COOKED_BEEF}, {Items.PORKCHOP, Items.COOKED_PORKCHOP}, {Items.MUTTON, Items.COOKED_MUTTON},
		{Items.CHICKEN, Items.COOKED_CHICKEN}, {Items.RABBIT, Items.COOKED_RABBIT}, {Items.COD, Items.COOKED_COD},
		{Items.SALMON, Items.COOKED_SALMON}, {Items.POTATO, Items.BAKED_POTATO}, {Items.WHEAT, Items.BREAD}};

	private WorkScenes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WorkScenes::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			// the tools are the trade's, never loot
			if (entity instanceof Villager v && SCENES.containsKey(v.getUUID())) {
				end(v, SCENES.remove(v.getUUID()));
			}
			return true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// what the work put in their hands, left there when the world was last shut, is put away
			if (entity instanceof Villager v && !SCENES.containsKey(v.getUUID())) {
				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
					if (isMarked(v.getItemBySlot(slot))) {
						v.setItemSlot(slot, ItemStack.EMPTY);
					}
				}
			}
			// a dish left out when the world was last shut is cleared away
			if (entity instanceof Display.ItemDisplay && entity.entityTags().contains(PLATE_TAG) && !LIVE_PLATES.contains(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel level = Factions.level(server);
			for (Map.Entry<UUID, Scene> e : SCENES.entrySet()) {
				if (level.getEntity(e.getKey()) instanceof Villager v) {
					end(v, e.getValue());
				} else {
					unlight(level, e.getValue());
				}
			}
			SCENES.clear();
			for (List<Plate> plates : PLATED.values()) {
				for (Plate p : plates) {
					p.display().discard();
				}
			}
			PLATED.clear();
			LIVE_PLATES.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		boolean hours = t >= Jobs.WORK_FROM && t < Jobs.WORK_TO;
		if (hours) {
			for (Faction f : Factions.all(server)) {
				if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
					continue;
				}
				for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
					v -> v.isAlive() && !v.isBaby() && !Visitors.is(v) && !Envoys.is(v))) {
					Holder<VillagerProfession> trade = v.getVillagerData().profession();
					if (trade.value() == Professions.COOK || tool(trade) == null) {
						// (the cook's scene is the kitchen's, from Meals)
						continue;
					}
					work(level, v, trade, now);
				}
			}
		}
		// a scene nobody moved on is over: tools put away, the furnace out
		for (Iterator<Map.Entry<UUID, Scene>> it = SCENES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Scene> e = it.next();
			if (now - e.getValue().touched > STALE) {
				if (level.getEntity(e.getKey()) instanceof Villager v) {
					end(v, e.getValue());
				} else {
					unlight(level, e.getValue());
				}
				it.remove();
			}
		}
		for (Iterator<List<Plate>> it = PLATED.values().iterator(); it.hasNext();) {
			List<Plate> plates = it.next();
			plates.removeIf(p -> {
				if (now >= p.gone() || p.display().isRemoved()) {
					p.display().discard();
					LIVE_PLATES.remove(p.display().getUUID());
					return true;
				}
				return false;
			});
			if (plates.isEmpty()) {
				it.remove();
			}
		}
	}

	/** Free to be seen at work: awake, not busy with anything else, not a settlement's worker (who has its own work). */
	private static boolean free(Villager v) {
		return !v.isSleeping() && Routines.idle(v) && v.getTradingPlayer() == null && !Whetting.working(v) && !SmithMending.working(v)
			&& !Meals.eating(v) && !Downed.isDowned(v) && !(v instanceof Worker w && w.role() >= 0) && !KitchenCompat.butchering(v);
	}

	/** {@code v} is seen at their work just now. */
	static boolean working(Villager v) {
		return SCENES.containsKey(v.getUUID());
	}

	private static boolean watched(ServerLevel level, Villager v) {
		return level.hasNearbyAlivePlayer(v.getX(), v.getY(), v.getZ(), WATCHED);
	}

	/** One think of a tradesman at work by their workstation. */
	private static void work(ServerLevel level, Villager v, Holder<VillagerProfession> trade, long now) {
		Optional<GlobalPos> held = v.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE);
		Scene s = SCENES.get(v.getUUID());
		if (held == null || held.isEmpty() || held.get().dimension() != level.dimension() || !free(v) || !watched(level, v)) {
			return;
		}
		BlockPos site = held.get().pos();
		double far = s == null ? START_AT : FORGE_REACH + 2.0;
		if (v.position().distanceToSqr(Vec3.atCenterOf(site)) > far * far) {
			return;
		}
		if (s == null) {
			s = begin(level, v, trade, site, now);
			SCENES.put(v.getUUID(), s);
			hold(v, s, EquipmentSlot.MAINHAND, tool(trade));
		}
		s.touched = now;
		keepTool(v, s, tool(trade));
		switch (s.phase) {
			case HEAT -> heat(level, v, s, now);
			case HAMMER -> hammer(level, v, s, now);
			case QUENCH -> quench(level, v, s, now);
			case REST -> rest(level, v, s, now);
			default -> stroke(level, v, s, now);
		}
	}

	/** A new scene: a smith with a furnace or an anvil by their workstation goes to the forge; anyone else works where they are. */
	private static Scene begin(ServerLevel level, Villager v, Holder<VillagerProfession> trade, BlockPos site, long now) {
		if (smith(trade)) {
			BlockPos furnace = nearest(level, site, FORGE_REACH, st -> st.is(Blocks.FURNACE) || st.is(Blocks.BLAST_FURNACE));
			BlockPos anvil = nearest(level, site, FORGE_REACH, st -> st.is(BlockTags.ANVIL));
			if (furnace != null || anvil != null) {
				BlockPos first = furnace != null ? furnace : anvil;
				Scene s = new Scene(furnace != null ? HEAT : HAMMER, first, stand(level, first, v), now);
				s.furnace = furnace;
				s.anvil = anvil != null ? anvil : site;
				s.water = nearest(level, site, FORGE_REACH, st -> st.is(Blocks.WATER_CAULDRON));
				if (furnace == null) {
					hold(v, s, EquipmentSlot.OFFHAND, hotIngot());
				}
				return s;
			}
		}
		Scene s = new Scene(TRADE, site, v.blockPosition(), now);
		s.arrived = now;
		s.next = now + STROKE_MIN + v.getRandom().nextInt(STROKE_MORE);
		return s;
	}

	// ---- any trade ---------------------------------------------------------------------------------------------------

	/** Their tool worked at the workstation: a stroke now and then, the trade's own sound with every few. */
	private static void stroke(ServerLevel level, Villager v, Scene s, long now) {
		Vec3 at = Vec3.atCenterOf(s.at);
		v.getLookControl().setLookAt(at.x, at.y, at.z);
		if (now >= s.next) {
			RandomSource random = v.getRandom();
			s.next = now + STROKE_MIN + random.nextInt(STROKE_MORE);
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			if (random.nextInt(3) == 0) {
				v.playWorkSound();
			}
		}
	}

	// ---- the smith ---------------------------------------------------------------------------------------------------

	private static boolean smith(Holder<VillagerProfession> trade) {
		return trade.is(VillagerProfession.ARMORER) || trade.is(VillagerProfession.TOOLSMITH) || trade.is(VillagerProfession.WEAPONSMITH);
	}

	/** The metal in the fire: the furnace lit (if nobody's using it), its flames and crackle, the smith turning it now and then. */
	private static void heat(ServerLevel level, Villager v, Scene s, long now) {
		if (s.furnace == null || !level.getBlockState(s.furnace).hasProperty(AbstractFurnaceBlock.LIT)) {
			next(level, v, s, HAMMER, now);
			return;
		}
		if (!arrive(v, s, now)) {
			return;
		}
		Vec3 mouth = Vec3.atCenterOf(s.furnace);
		v.getLookControl().setLookAt(mouth.x, mouth.y, mouth.z);
		light(level, s, s.furnace);
		RandomSource random = v.getRandom();
		Vec3 front = Vec3.atCenterOf(s.furnace).add(Vec3.atLowerCornerOf(s.stand.subtract(s.furnace)).normalize().scale(0.55));
		if (random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.FLAME, front.x, front.y - 0.1, front.z, 2, 0.12, 0.08, 0.12, 0.005);
		}
		if (random.nextInt(6) == 0) {
			level.playSound(null, s.furnace, level.getBlockState(s.furnace).is(Blocks.BLAST_FURNACE) ? SoundEvents.BLASTFURNACE_FIRE_CRACKLE
				: SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
		if (random.nextInt(8) == 0) {
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		if (now - s.arrived >= HEAT_TICKS) {
			unlight(level, s);
			hold(v, s, EquipmentSlot.OFFHAND, hotIngot());
			next(level, v, s, HAMMER, now);
		}
	}

	/** At the anvil: a hammer blow every {@link #STRIKE_EVERY} ticks, the anvil ringing and sparks off the metal. */
	private static void hammer(ServerLevel level, Villager v, Scene s, long now) {
		BlockPos anvil = s.anvil != null ? s.anvil : s.at;
		if (!s.at.equals(anvil)) {
			s.at = anvil;
			s.stand = stand(level, anvil, v);
		}
		if (!arrive(v, s, now)) {
			return;
		}
		Vec3 top = new Vec3(anvil.getX() + 0.5, anvil.getY() + 1.0, anvil.getZ() + 0.5);
		v.getLookControl().setLookAt(top.x, top.y, top.z);
		RandomSource random = v.getRandom();
		if (now >= s.next) {
			s.next = now + STRIKE_EVERY;
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35F, 0.85F + random.nextFloat() * 0.35F);
			Vec3 out = v.position().subtract(top);
			if (!PowersCompat.clashSparks(level, top, out, SPARKS, SPARK_SCALE)) {
				level.sendParticles(Fx.spark(0xFFB040, 0.3F), top.x, top.y, top.z, SPARKS, 0.15, 0.05, 0.15, 0.05);
			}
		}
		if (now - s.arrived >= HAMMER_TICKS) {
			next(level, v, s, s.water != null ? QUENCH : REST, now);
			if (s.water == null) {
				hold(v, s, EquipmentSlot.OFFHAND, Items.IRON_INGOT);
			}
		}
	}

	/** The worked piece into the water: a hiss and a cloud of steam, and it's cool. */
	private static void quench(ServerLevel level, Villager v, Scene s, long now) {
		if (s.water == null || !level.getBlockState(s.water).is(Blocks.WATER_CAULDRON)) {
			next(level, v, s, REST, now);
			return;
		}
		if (!s.at.equals(s.water)) {
			s.at = s.water;
			s.stand = stand(level, s.water, v);
		}
		if (!arrive(v, s, now)) {
			return;
		}
		Vec3 top = new Vec3(s.water.getX() + 0.5, s.water.getY() + 1.0, s.water.getZ() + 0.5);
		v.getLookControl().setLookAt(top.x, top.y - 0.3, top.z);
		if (now == s.arrived) {
			v.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, s.water, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.5F, 1.4F);
		}
		level.sendParticles(ParticleTypes.CLOUD, top.x, top.y, top.z, 2, 0.15, 0.05, 0.15, 0.02);
		if (now - s.arrived >= QUENCH_TICKS) {
			hold(v, s, EquipmentSlot.OFFHAND, Items.IRON_INGOT);
			next(level, v, s, REST, now);
		}
	}

	/** A breather, the finished piece looked over, and back to the fire. */
	private static void rest(ServerLevel level, Villager v, Scene s, long now) {
		if (s.arrived < 0) {
			s.arrived = now;
			s.length = REST_MIN + v.getRandom().nextInt(REST_MORE);
		}
		v.getNavigation().stop();
		if (now - s.arrived >= s.length) {
			hold(v, s, EquipmentSlot.OFFHAND, null);
			if (s.furnace != null) {
				s.at = s.furnace;
				s.stand = stand(level, s.furnace, v);
				next(level, v, s, HEAT, now);
			} else {
				hold(v, s, EquipmentSlot.OFFHAND, hotIngot());
				next(level, v, s, HAMMER, now);
			}
		}
	}

	// ---- the cook ----------------------------------------------------------------------------------------------------

	/**
	 * (From {@link Meals#cookAt}, the cook at work by {@code kitchen}, where an eatery's meals are handed over.) The food cut
	 * up at the counter, cooked at the stove, set out on the counter, and again.
	 */
	static boolean cook(ServerLevel level, Faction f, Villager v, BlockPos kitchen, long now) {
		if (!watched(level, v)) {
			return false;
		}
		Scene s = SCENES.get(v.getUUID());
		if (s == null) {
			BlockPos stove = isStove(level.getBlockState(kitchen)) ? kitchen : nearest(level, kitchen, KITCHEN_REACH, WorkScenes::isStove);
			BlockPos counter = nearest(level, kitchen, KITCHEN_REACH, st -> counterTop(st) && !isStove(st));
			if (counter != null && !level.getBlockState(counter.above()).isAir()) {
				counter = null;
			}
			BlockPos first = counter != null ? counter : stove != null ? stove : kitchen;
			s = new Scene(PREP, first, stand(level, first, v), now);
			s.stove = stove;
			s.counter = counter;
			SCENES.put(v.getUUID(), s);
			pickDish(level, f, s);
			hold(v, s, EquipmentSlot.MAINHAND, tool(Holder.direct(Professions.COOK)));
			hold(v, s, EquipmentSlot.OFFHAND, s.raw);
		}
		s.touched = now;
		keepTool(v, s, tool(Holder.direct(Professions.COOK)));
		RandomSource random = v.getRandom();
		switch (s.phase) {
			case PREP -> {
				if (!arrive(v, s, now)) {
					return true;
				}
				look(v, s.at, 1.0);
				if (now >= s.next) {
					s.next = now + 12 + random.nextInt(8);
					v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.playSound(null, s.at, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.4F, 1.3F + random.nextFloat() * 0.3F);
				}
				if (now - s.arrived >= PREP_TICKS) {
					if (s.stove != null) {
						s.at = s.stove;
						s.stand = stand(level, s.stove, v);
					}
					next(level, v, s, COOK, now);
				}
			}
			case COOK -> {
				if (!arrive(v, s, now)) {
					return true;
				}
				look(v, s.at, 0.6);
				if (s.stove != null) {
					light(level, s, s.stove);
					if (random.nextBoolean()) {
						level.sendParticles(ParticleTypes.SMOKE, s.stove.getX() + 0.5, s.stove.getY() + 1.1, s.stove.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
					}
					if (random.nextInt(5) == 0) {
						level.playSound(null, s.stove, random.nextBoolean() ? SoundEvents.SMOKER_SMOKE : SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.0F);
					}
				}
				if (random.nextInt(10) == 0) {
					v.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
				}
				if (now - s.arrived >= COOK_TICKS) {
					unlight(level, s);
					hold(v, s, EquipmentSlot.OFFHAND, s.cooked);
					if (s.counter != null) {
						s.at = s.counter;
						s.stand = stand(level, s.counter, v);
					}
					next(level, v, s, SERVE, now);
				}
			}
			case SERVE -> {
				if (!arrive(v, s, now)) {
					return true;
				}
				look(v, s.at, 1.0);
				if (now == s.arrived) {
					v.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
					if (s.counter != null) {
						plate(level, s.stove != null ? s.stove : kitchen, s.counter, s.cooked, now);
					}
					hold(v, s, EquipmentSlot.OFFHAND, null);
				}
				if (now - s.arrived >= SERVE_TICKS) {
					next(level, v, s, COOK_RESTING, now);
				}
			}
			default -> {
				if (s.arrived < 0) {
					s.arrived = now;
				}
				if (now - s.arrived >= COOK_REST) {
					pickDish(level, f, s);
					hold(v, s, EquipmentSlot.OFFHAND, s.raw);
					BlockPos first = s.counter != null ? s.counter : s.stove != null ? s.stove : kitchen;
					s.at = first;
					s.stand = stand(level, first, v);
					next(level, v, s, PREP, now);
				}
			}
		}
		return true;
	}

	private static boolean isStove(BlockState st) {
		return st.is(Blocks.SMOKER) || st.is(Blocks.FURNACE) || st.getBlock() instanceof CampfireBlock && st.getValue(CampfireBlock.LIT);
	}

	/** A counter to set a dish on: the top of a raised slab or a full table-high block (a barrel's lid will do). */
	private static boolean counterTop(BlockState st) {
		return st.getBlock() instanceof SlabBlock && st.getValue(SlabBlock.TYPE) == SlabType.TOP || st.is(Blocks.BARREL);
	}

	/** What the cook works next: the first raw food the storehouse has (as {@link Meals}' batches cook), else a joint of beef. */
	private static void pickDish(ServerLevel level, Faction f, Scene s) {
		boolean stocked = Storage.loaded(level, f);
		for (Item[] dish : DISHES) {
			if (stocked && Storage.count(level, f, dish[0]) > 0) {
				s.raw = dish[0];
				s.cooked = dish[1];
				return;
			}
		}
		s.raw = Items.BEEF;
		s.cooked = Items.COOKED_BEEF;
	}

	/** The dish lying on the counter at {@code counter} for a while; the oldest taken away when there are {@link #PLATES}. */
	private static void plate(ServerLevel level, BlockPos kitchen, BlockPos counter, Item dish, long now) {
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) {
			return;
		}
		CompoundTag tag = new CompoundTag();
		ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), new ItemStack(dish))
			.result().ifPresent(item -> tag.put("item", item));
		tag.putString("item_display", "fixed");
		CompoundTag transform = new CompoundTag();
		transform.put("translation", floats(0.0F, 0.0F, 0.0F));
		// lying flat on the board
		transform.put("left_rotation", floats(-0.70710677F, 0.0F, 0.0F, 0.70710677F));
		transform.put("scale", floats(PLATE_SCALE, PLATE_SCALE, PLATE_SCALE));
		transform.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
		tag.put("transformation", transform);
		display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		display.addTag(PLATE_TAG);
		RandomSource random = level.getRandom();
		double x = counter.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
		double z = counter.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
		display.snapTo(x, counter.getY() + 1.02, z, random.nextFloat() * 360.0F, 0.0F);
		if (!level.addFreshEntity(display)) {
			return;
		}
		LIVE_PLATES.add(display.getUUID());
		List<Plate> plates = PLATED.computeIfAbsent(kitchen.immutable(), k -> new ArrayList<>());
		plates.add(new Plate(display, now + PLATE_TICKS));
		while (plates.size() > PLATES) {
			Plate old = plates.removeFirst();
			old.display().discard();
			LIVE_PLATES.remove(old.display().getUUID());
		}
		level.playSound(null, counter, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
	}

	private static ListTag floats(float... values) {
		ListTag list = new ListTag();
		for (float value : values) {
			list.add(FloatTag.valueOf(value));
		}
		return list;
	}

	// ---- shared ------------------------------------------------------------------------------------------------------

	/** The tool of each trade (null for one with no scene here: a nitwit, an unemployed villager, a settlement's own workers). */
	static @Nullable Item tool(Holder<VillagerProfession> trade) {
		VillagerProfession p = trade.value();
		if (p == Professions.COOK) {
			return item("jaymoskitchen:iron_knife", Items.WOODEN_SHOVEL);
		}
		if (smith(trade)) {
			return item("jaymospowers:steel_smithing_hammer", Items.IRON_PICKAXE);
		}
		if (trade.is(VillagerProfession.BUTCHER)) {
			return item("jaymospowers:iron_cleaver", Items.IRON_AXE);
		}
		if (trade.is(VillagerProfession.FLETCHER)) {
			return item("jaymoskitchen:flint_knife", Items.FLINT);
		}
		if (trade.is(VillagerProfession.MASON)) {
			return Items.IRON_PICKAXE;
		}
		if (trade.is(VillagerProfession.LIBRARIAN)) {
			return Items.BOOK;
		}
		if (trade.is(VillagerProfession.CARTOGRAPHER)) {
			return Items.MAP;
		}
		if (trade.is(VillagerProfession.CLERIC)) {
			return Items.GLASS_BOTTLE;
		}
		if (trade.is(VillagerProfession.FARMER)) {
			return Items.IRON_HOE;
		}
		if (trade.is(VillagerProfession.FISHERMAN)) {
			return Items.FISHING_ROD;
		}
		if (trade.is(VillagerProfession.SHEPHERD)) {
			return Items.SHEARS;
		}
		if (trade.is(VillagerProfession.LEATHERWORKER)) {
			return Items.LEATHER;
		}
		return null;
	}

	/** One of {@code item}, marked as the work's (see {@link #MARK}). */
	static ItemStack marked(Item item) {
		ItemStack stack = new ItemStack(item);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	static boolean isMarked(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARK, false);
	}

	private static Item hotIngot() {
		return item("jaymospowers:hot_ingot", Items.IRON_INGOT);
	}

	private static Item item(String id, Item fallback) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? fallback : BuiltInRegistries.ITEM.getOptional(key).orElse(fallback);
	}

	/**
	 * Puts {@code item} in {@code v}'s hand for the work (null takes back what was put there). Only into an empty hand, and
	 * only what is still the work's comes back out, so nothing of theirs is ever lost.
	 */
	private static void hold(Villager v, Scene s, EquipmentSlot slot, @Nullable Item item) {
		boolean main = slot == EquipmentSlot.MAINHAND;
		Item had = main ? s.main : s.off;
		if (had != null && v.getItemBySlot(slot).is(had)) {
			v.setItemSlot(slot, ItemStack.EMPTY);
		}
		Item put = null;
		if (item != null && v.getItemBySlot(slot).isEmpty()) {
			v.setItemSlot(slot, marked(item));
			put = item;
		}
		if (main) {
			s.main = put;
		} else {
			s.off = put;
		}
	}

	/** The tool back in an emptied hand (a villager showing a player its wares lays it down, and picks it up after). */
	private static void keepTool(Villager v, Scene s, @Nullable Item tool) {
		if (tool != null && v.getMainHandItem().isEmpty()) {
			v.setItemSlot(EquipmentSlot.MAINHAND, marked(tool));
			s.main = tool;
		}
	}

	/** The scene's over: hands as they were, the furnace out. */
	private static void end(Villager v, Scene s) {
		hold(v, s, EquipmentSlot.MAINHAND, null);
		hold(v, s, EquipmentSlot.OFFHAND, null);
		if (v.level() instanceof ServerLevel level) {
			unlight(level, s);
		}
	}

	/** Lights an idle furnace or smoker for the work (one with nothing in it, so nobody's smelting is touched). */
	private static void light(ServerLevel level, Scene s, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (s.lit != null || !state.hasProperty(AbstractFurnaceBlock.LIT) || state.getValue(AbstractFurnaceBlock.LIT) || !idleFurnace(level, pos)) {
			return;
		}
		level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
		s.lit = pos.immutable();
	}

	/** Puts out the furnace the work lit, if it's still idle (a player's fuel since then keeps it burning its own way). */
	private static void unlight(ServerLevel level, Scene s) {
		if (s.lit == null) {
			return;
		}
		BlockPos pos = s.lit;
		s.lit = null;
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT) && idleFurnace(level, pos)) {
			level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, false), 3);
		}
	}

	private static boolean idleFurnace(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof Container c && c.getItem(0).isEmpty() && c.getItem(1).isEmpty();
	}

	/** The next step of the work, walking to it first. */
	private static void next(ServerLevel level, Villager v, Scene s, int phase, long now) {
		s.phase = phase;
		s.since = now;
		s.arrived = -1L;
		s.next = now;
	}

	/** True once {@code v} stands where the step is worked (or has tried long enough and makes do there). */
	private static boolean arrive(Villager v, Scene s, long now) {
		if (s.arrived >= 0) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			v.getNavigation().stop();
			return true;
		}
		boolean there = v.position().distanceToSqr(Vec3.atBottomCenterOf(s.stand)) <= ARRIVE * ARRIVE;
		if (there || now - s.since > TRAVEL_TICKS) {
			s.arrived = now;
			s.next = now;
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			v.getNavigation().stop();
			return true;
		}
		if (v.getNavigation().isDone()) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, s.stand, (float) WALK, 0);
		}
		return false;
	}

	private static void look(Villager v, BlockPos at, double up) {
		v.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + up, at.getZ() + 0.5);
	}

	/** Where to stand to work {@code block}: in front of it if it faces a way, else the nearest open spot beside it. */
	private static BlockPos stand(ServerLevel level, BlockPos block, Villager v) {
		BlockState state = level.getBlockState(block);
		if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
			BlockPos front = block.relative(state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
			if (open(level, front)) {
				return front;
			}
		}
		// the open side nearest them (a cook stays on the kitchen side of the counter)
		BlockPos best = null;
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos beside = block.relative(side);
			if (open(level, beside) && (best == null || beside.distSqr(v.blockPosition()) < best.distSqr(v.blockPosition()))) {
				best = beside;
			}
		}
		return best != null ? best : Construction.standable(level, block, 2);
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	private static @Nullable BlockPos nearest(ServerLevel level, BlockPos around, int reach, java.util.function.Predicate<BlockState> test) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(around.offset(-reach, -2, -reach), around.offset(reach, 2, reach))) {
			if (test.test(level.getBlockState(p))) {
				double d = p.distSqr(around);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}
}
