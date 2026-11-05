package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Andy (2026-11-03): "goblins who stay in orc lands are slaves or toothpicks (thrown, worked, used up)". A few
 * goblins live chained in an orc camp ({@link #ORCS_PER_SLAVE}: one to every so many of its orcs, {@link #MOST} at most):
 * <ul>
 * <li>They are {@link Folk} of role {@link #SLAVE} in the camp's own faction, off its roster and never fighting (a goblin
 * of a village isn't one: see {@link Goblins}). A chain hangs from the off hand. They haul and carry for the camp (a log
 * from the depot to a spot in the camp), and when the camp raises a building they carry logs to it ({@link #haul}).</li>
 * <li><b>Toothpicks:</b> in a fight an orc grabs a slave near him and throws it at his foe ({@link #toss}). It lands
 * stunned and hurt, and may die of it; a foe it lands on is hit too.</li>
 * <li>Now and then one runs off when no orc is watching ({@link #RUN_CHANCE}) for the nearest goblin village, or, if there
 * is none to reach, to raise a new one of its own ({@link #arrive}). A camp that falls frees all of its slaves, and a
 * player may free one by breaking its chain with shears, an axe or a pickaxe ({@link #free}): the goblin tribe thinks
 * well of them for it and the orc tribe the worse.</li>
 * </ul>
 * What a slave is doing is kept in tags on it ({@link #RUNAWAY}, {@link #FREED}) and in its {@link Folk#site}, so a
 * restart doesn't lose it.
 */
public final class Slaves {
	/** The role (a folk id; clear of the goblins' 30-32, the casters' 40-42 and the orcs' 50-53). */
	public static final int SLAVE = 33;
	/** A camp keeps one slave to every this many orcs, and no more than this many. */
	static final int ORCS_PER_SLAVE = 5, MOST = 4;
	/** A slave: health, pace, size, the camp's keep of it (blocks round the fire). */
	static final double HEALTH = 12.0, SPEED = 0.28, SCALE = Goblins.SCALE;
	static final int HOME_RADIUS = 10;
	/** Rags. */
	static final int RAG = 0x4A4036;
	/** A camp looks to take another slave at this chance each look (every {@link Camps#CHECK_TICKS}); slaves are looked for this far from the fire. */
	static final float RESTOCK_CHANCE = 0.25F;
	static final double SEARCH_RADIUS = 48.0;
	/** Work: how often a slave moves on (ticks), how long it rests between loads (ticks), its pace, how near is there, the most that carry to a building. */
	static final int WORK_EVERY = 20, REST_TICKS = 100;
	static final double WORK_SPEED = 0.9, THERE = 2.5;
	static final int HAUL_MAX = 3;
	/** (0.7.490) While a slave is on a job its keep moves to the job's site, this many blocks round it, so the camp doesn't pull it back. */
	static final int JOB_RADIUS = 4;
	static final double HAUL_RANGE = 40.0;
	/** What they carry. */
	static final Item[] LOADS = {Items.DARK_OAK_LOG, Items.DARK_OAK_LOG, Items.BONE, Items.COBBLESTONE};
	/** Running off: how often one thinks of it (ticks), the chance then, how near an awake orc keeps it from running (blocks). */
	static final int RUN_EVERY = 200;
	static final float RUN_CHANCE = 0.04F;
	static final double WATCH_RANGE = 10.0;
	/** A runaway makes for the nearest goblin village within this far (blocks), else raises its own after going this far from its camp. */
	static final double JOIN_REACH = 600.0, RUN_DISTANCE = 140.0, RUN_STEP = 48.0;
	/** A runaway walks to a waypoint this far ahead, at this pace, and has arrived this near its site (blocks). */
	static final double WAYPOINT = 16.0, RUN_SPEED = 1.15, ARRIVE = 12.0;
	/** Failing to find ground to raise a village on, it is lost to the wild at this chance. */
	static final float LOST_CHANCE = 0.34F;
	/** A runaway at a village is let in when it's this near the village's seat. */
	static final double JOIN_NEAR = 40.0;
	/** Toothpicks: how near a slave must be for the grab, how far the foe, the throw's pace and lift, the chance each look (every 10 ticks) and the wait between (ticks). */
	static final double GRAB_RANGE = 8.0, TOSS_MIN = 3.0, TOSS_MAX = 14.0, TOSS_SPEED = 1.0, TOSS_LIFT = 0.4;
	static final float TOSS_CHANCE = 0.25F;
	static final int TOSS_COOLDOWN = 20 * 10;
	/** It has landed after this long in the air (ticks) at the soonest, and is given up on after this long. */
	static final int AIR_MIN = 4, AIR_MAX = 100;
	/** Landing: its hurt is this much plus up to this much more (it has {@link #HEALTH}); it's stunned this long (ticks); a foe this near is hit this hard. */
	static final int TOSS_DAMAGE = 4, TOSS_DAMAGE_SPAN = 10, STUN_TICKS = 20 * 4;
	static final double HIT_RADIUS = 2.5;
	static final float HIT_DAMAGE = 4.0F;
	/** Freeing one: the goblin tribe's favour, the orc tribe's grudge. */
	static final int FREED_FAVOUR = 12, FREED_GRUDGE = 3;
	/** A nearby orc cries out at a freeing this far (blocks). */
	static final double SHOUT_RANGE = 24.0;
	/** How many lines of each kind there are (lang). */
	static final int TOSS_LINES = 4, SQUEAK_LINES = 3, RUN_LINES = 3, FREED_LINES = 4, SHOUT_LINES = 3, JOIN_LINES = 3;
	/** How far lines are heard (blocks). */
	static final double SAY_RANGE = 24.0;

	/** A slave that has run off, and one a player has freed. */
	static final String RUNAWAY = "jaymoskingdoms.slave_runaway", FREED = "jaymoskingdoms.slave_freed";

	/** A slave in the air: when it was thrown and by whom. */
	private record Toss(long at, UUID orc) {
	}

	private static final Map<UUID, Toss> THROWN = new HashMap<>();
	/** An orc → the game time it may throw again (memory only). */
	private static final Map<UUID, Long> TOSS_READY = new HashMap<>();

	private Slaves() {
	}

	/** Defines the role (call once at init). */
	static void init() {
		Folk.defineRole(new Folk.Role(SLAVE, "goblin_slave", false, false, null, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
			folk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEED);
			folk.getAttribute(Attributes.SCALE).setBaseValue(SCALE);
			ItemStack rags = new ItemStack(Items.LEATHER_CHESTPLATE);
			rags.set(DataComponents.DYED_COLOR, new DyedItemColor(RAG));
			folk.setItemSlot(EquipmentSlot.CHEST, rags);
			folk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_CHAIN));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.goblin_slave_of", faction.name()));
		}));
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !is(entity) || player.isSpectator() || !(player instanceof ServerPlayer sp)
				|| !(level instanceof ServerLevel server) || entity.entityTags().contains(FREED)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			if (held.is(Items.SHEARS) || held.is(ItemTags.AXES) || held.is(ItemTags.PICKAXES)) {
				held.hurtAndBreak(2, sp, hand);
				free(server, (Folk)entity, sp);
			} else {
				sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.slave.chained"), true);
			}
			return InteractionResult.SUCCESS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	/** {@code e} is a goblin slave. */
	public static boolean is(net.minecraft.world.entity.Entity e) {
		return e instanceof Folk f && f.role() == SLAVE;
	}

	private static boolean chained(Folk slave) {
		return !slave.entityTags().contains(FREED);
	}

	// ---- taking them ---------------------------------------------------------------------------------------------------------

	/** The slaves of camp {@code camp} near {@code center} (loaded), runaways aside. */
	private static List<Folk> kept(ServerLevel level, int camp, BlockPos center) {
		return level.getEntitiesOfClass(Folk.class, new AABB(center).inflate(SEARCH_RADIUS, 12.0, SEARCH_RADIUS),
			f -> f.role() == SLAVE && f.factionId() == camp && f.isAlive() && !f.entityTags().contains(RUNAWAY));
	}

	/** (0.7.490) How many slaves camp {@code camp} keeps in the loaded world: they eat and work (HordeEconomy), yet are no folk of its cap. */
	static int count(ServerLevel level, int camp, BlockPos center) {
		return kept(level, camp, center).size();
	}

	/** (From {@link OrcCamps#tick}) a loaded camp with fewer slaves than it can keep takes another now and then. */
	static void restock(ServerLevel level, Faction camp, Camps.CampData data) {
		if (!OrcCamps.wholeCampLoaded(level, data.center()) || level.getRandom().nextFloat() >= RESTOCK_CHANCE) {
			return;
		}
		int cap = Math.min(MOST, OrcCamps.fighters(camp) / ORCS_PER_SLAVE);
		if (kept(level, camp.id(), data.center()).size() >= cap) {
			return;
		}
		Folk slave = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (slave == null) {
			return;
		}
		BlockPos at = OrcCamps.spot(level, data.center(), level.getRandom());
		slave.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		slave.setup(SLAVE, camp, data.center(), HOME_RADIUS);
		level.addFreshEntity(slave);
	}

	// ---- their day -------------------------------------------------------------------------------------------------------------

	/** A slave's own step, every tick (from {@link Folk#customServerAiStep}): in the air, then its day. */
	static void tick(ServerLevel level, Folk slave) {
		Toss toss = THROWN.get(slave.getUUID());
		if (toss != null) {
			flight(level, slave, toss);
			return;
		}
		if (slave.tickCount % WORK_EVERY != 0 || slave.isSleeping()) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, slave.factionId());
		Camps.CampData data = camp == null ? null : Camps.data(server, camp.id());
		boolean runaway = slave.entityTags().contains(RUNAWAY);
		if (!runaway && (camp == null || camp.fallen() || data == null || data.clearedAt() >= 0 || !Camps.orc(server, camp.id()))) {
			// (its camp is gone: nobody keeps it any more)
			runOff(level, slave, false);
			return;
		}
		if (chained(slave) && !slave.getOffhandItem().is(Items.IRON_CHAIN)) {
			slave.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_CHAIN));
		}
		if (runaway) {
			run(level, slave, camp, data);
		} else {
			work(level, slave, camp, data);
			if (slave.tickCount % RUN_EVERY == 0 && !slave.isCarrying() && level.getRandom().nextFloat() < RUN_CHANCE && unwatched(level, slave)) {
				runOff(level, slave, false);
			}
		}
	}

	/** No awake orc of its camp is near enough to see it go. */
	private static boolean unwatched(ServerLevel level, Folk slave) {
		return level.getEntitiesOfClass(PathfinderMob.class, slave.getBoundingBox().inflate(WATCH_RANGE),
			e -> e instanceof Worker w && w.factionId() == slave.factionId() && Orcs.isOrc(w.role()) && !e.isSleeping()).isEmpty();
	}

	/** The hauling: to the depot for a load, with it to a spot in the camp (or the building going up), then a rest. */
	private static void work(ServerLevel level, Folk slave, Faction camp, Camps.CampData data) {
		RandomSource random = level.getRandom();
		BlockPos site = slave.site();
		String task = slave.task();
		if (task.isEmpty()) {
			if (slave.tickCount % REST_TICKS == 0 || slave.tickCount % REST_TICKS == WORK_EVERY && random.nextBoolean()) {
				// a load to fetch
				slave.setTask("fetch");
				slave.setSite(Builders.depot(camp));
			}
			return;
		}
		if (site == null) {
			slave.setTask("");
			slave.setCarried(ItemStack.EMPTY);
			slave.setHomeTo(data.center(), HOME_RADIUS);
			return;
		}
		// (0.7.490) its keep goes with the job, or the camp's keep would pull it back short of a far site
		if (!site.equals(slave.getHomePosition()) || slave.getHomeRadius() != JOB_RADIUS) {
			slave.setHomeTo(site, JOB_RADIUS);
		}
		if (slave.distanceToSqr(Vec3.atCenterOf(site)) > THERE * THERE) {
			if (slave.getNavigation().isDone()) {
				slave.getNavigation().moveTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, WORK_SPEED);
			}
			return;
		}
		if (task.equals("fetch")) {
			slave.setCarried(new ItemStack(LOADS[random.nextInt(LOADS.length)]));
			slave.setTask("haul");
			slave.setSite(OrcCamps.spot(level, data.center(), random));
		} else {
			// put down where the orcs want it
			slave.setCarried(ItemStack.EMPTY);
			slave.setTask("");
			slave.setSite(null);
			slave.setHomeTo(data.center(), HOME_RADIUS);
			level.playSound(null, slave.getX(), slave.getY(), slave.getZ(), SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL, 0.8F, 1.0F);
		}
	}

	/** (From {@link OrcCamps#tick}, and (0.7.490) {@link Hordes}' own building) a building has gone up at {@code where}: the camp's slaves carry logs to it. */
	static void haul(ServerLevel level, int camp, BlockPos center, BlockPos where) {
		List<Folk> hands = kept(level, camp, center);
		hands.removeIf(f -> f.entityTags().contains(FREED));
		hands.sort(java.util.Comparator.comparingDouble(f -> f.distanceToSqr(Vec3.atCenterOf(where))));
		int sent = 0;
		for (Folk slave : hands) {
			if (sent >= HAUL_MAX || slave.distanceToSqr(Vec3.atCenterOf(where)) > HAUL_RANGE * HAUL_RANGE) {
				break;
			}
			slave.setCarried(new ItemStack(Items.DARK_OAK_LOG));
			slave.setTask("haul");
			slave.setSite(where);
			slave.setHomeTo(where, JOB_RADIUS);
			sent++;
		}
	}

	// ---- toothpicks ------------------------------------------------------------------------------------------------------------

	/**
	 * (From {@link Orcs#tick}) {@code orc} of camp {@code camp} is in a fight with {@code foe}: now and then he grabs a slave
	 * near him and throws it at them.
	 */
	static void toss(ServerLevel level, PathfinderMob orc, int camp, LivingEntity foe) {
		long now = level.getGameTime();
		double d = orc.distanceTo(foe);
		if (d < TOSS_MIN || d > TOSS_MAX || now < TOSS_READY.getOrDefault(orc.getUUID(), 0L) || level.getRandom().nextFloat() >= TOSS_CHANCE) {
			return;
		}
		Folk slave = null;
		for (Folk f : level.getEntitiesOfClass(Folk.class, orc.getBoundingBox().inflate(GRAB_RANGE),
			f -> f.role() == SLAVE && f.factionId() == camp && f.isAlive() && !THROWN.containsKey(f.getUUID()) && !f.entityTags().contains(RUNAWAY))) {
			if (slave == null || f.distanceToSqr(orc) < slave.distanceToSqr(orc)) {
				slave = f;
			}
		}
		if (slave == null) {
			return;
		}
		TOSS_READY.put(orc.getUUID(), now + TOSS_COOLDOWN);
		Vec3 aim = foe.getEyePosition().subtract(orc.getEyePosition());
		Vec3 flat = new Vec3(aim.x, 0.0, aim.z);
		Vec3 dir = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		slave.setCarried(ItemStack.EMPTY);
		slave.setTask("");
		slave.setSite(null);
		slave.getNavigation().stop();
		slave.snapTo(orc.getX() + dir.x * 0.6, orc.getY() + orc.getBbHeight() * 0.8, orc.getZ() + dir.z * 0.6);
		double lift = TOSS_LIFT + d * 0.025;
		slave.setDeltaMovement(dir.x * TOSS_SPEED * Math.min(1.6, 0.6 + d / 10.0), lift, dir.z * TOSS_SPEED * Math.min(1.6, 0.6 + d / 10.0));
		slave.syncVelocity = true;
		THROWN.put(slave.getUUID(), new Toss(now, orc.getUUID()));
		orc.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, orc.getX(), orc.getY(), orc.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.6F);
		RandomSource random = level.getRandom();
		Speech.say(level, orc, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.toss_" + random.nextInt(TOSS_LINES)), Speech.RED, SAY_RANGE);
		Speech.say(level, slave, Dialects.trBrood(Dialects.GOBLIN, "speech.jaymoskingdoms.slave.squeak_" + random.nextInt(SQUEAK_LINES)), Speech.YELLOW, SAY_RANGE);
	}

	/** A thrown slave in the air: when it comes down it is hurt and stunned, and a foe under it is hit. */
	private static void flight(ServerLevel level, Folk slave, Toss toss) {
		long age = level.getGameTime() - toss.at;
		if (age < AIR_MIN || !(slave.onGround() || slave.isInWater()) && age < AIR_MAX) {
			return;
		}
		THROWN.remove(slave.getUUID());
		LivingEntity orc = level.getEntity(toss.orc) instanceof LivingEntity l && l.isAlive() ? l : null;
		RandomSource random = level.getRandom();
		level.sendParticles(ParticleTypes.CRIT, slave.getX(), slave.getY() + 0.3, slave.getZ(), 10, 0.3, 0.2, 0.3, 0.1);
		level.playSound(null, slave.getX(), slave.getY(), slave.getZ(), SoundEvents.GENERIC_HURT, SoundSource.NEUTRAL, 1.0F, 1.6F);
		int camp = slave.factionId();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, slave.getBoundingBox().inflate(HIT_RADIUS),
			e -> e != slave && e.isAlive() && Orcs.prey(camp, e))) {
			e.hurtServer(level, orc == null ? level.damageSources().generic() : level.damageSources().mobAttack(orc), HIT_DAMAGE);
		}
		if (slave.isAlive()) {
			slave.hurtServer(level, level.damageSources().fall(), TOSS_DAMAGE + random.nextInt(TOSS_DAMAGE_SPAN + 1));
		}
		if (slave.isAlive()) {
			slave.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, 5));
			slave.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 0));
		}
	}

	// ---- running off -----------------------------------------------------------------------------------------------------------

	/**
	 * {@code slave} runs for it: for the nearest goblin village within reach, or, with none to reach, a long way from its camp
	 * (to raise a village of its own there). {@code freed} if a player broke its chain.
	 */
	static void runOff(ServerLevel level, Folk slave, boolean freed) {
		MinecraftServer server = level.getServer();
		slave.addTag(RUNAWAY);
		if (freed) {
			slave.addTag(FREED);
			slave.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		slave.setCarried(ItemStack.EMPTY);
		slave.setTask("run");
		Faction village = nearestVillage(server, slave.blockPosition(), JOIN_REACH);
		if (village != null) {
			slave.setSite(village.seat());
		} else {
			slave.setSite(farSite(level, slave, RUN_DISTANCE));
		}
		Speech.say(level, slave, Dialects.trBrood(Dialects.GOBLIN, "speech.jaymoskingdoms.slave.run_" + level.getRandom().nextInt(RUN_LINES)), Speech.YELLOW, SAY_RANGE);
	}

	/** A site {@code distance} blocks off, away from the slave's camp (or any way at all if it has none). */
	private static BlockPos farSite(ServerLevel level, Folk slave, double distance) {
		MinecraftServer server = level.getServer();
		Camps.CampData data = Camps.data(server, slave.factionId());
		RandomSource random = level.getRandom();
		double dx = data == null ? 0.0 : slave.getX() - data.center().getX(), dz = data == null ? 0.0 : slave.getZ() - data.center().getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		double angle = len < 1.0 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(dz, dx) + (random.nextDouble() - 0.5) * 1.2;
		return BlockPos.containing(slave.getX() + Math.cos(angle) * distance, slave.getY(), slave.getZ() + Math.sin(angle) * distance);
	}

	/** The nearest standing goblin village (with a king, and room under its folk cap) within {@code reach} of {@code from}. */
	private static @Nullable Faction nearestVillage(MinecraftServer server, BlockPos from, double reach) {
		Faction best = null;
		double bestD = reach * reach;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = f.fallen() ? null : Camps.data(server, f.id());
			if (data == null || data.clearedAt() >= 0 || f.leader().isEmpty() || !Camps.goblin(server, f.id()) || !hasRoom(server, f)) {
				continue;
			}
			double d = Raids.horizontal(f.seat(), from);
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/** (0.7.490) Whether goblin village {@code village} is under its Hordes folk cap, so a runaway may join it. */
	private static boolean hasRoom(MinecraftServer server, Faction village) {
		return GoblinVillages.goblins(village) < Hordes.folkCap(server, village.id(), false);
	}

	/** A runaway on its way: a waypoint at a time toward its site, and what to do when it gets there. */
	private static void run(ServerLevel level, Folk slave, @Nullable Faction camp, Camps.CampData data) {
		BlockPos site = slave.site();
		if (site == null) {
			slave.setSite(farSite(level, slave, RUN_STEP));
			return;
		}
		double dx = site.getX() + 0.5 - slave.getX(), dz = site.getZ() + 0.5 - slave.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len <= ARRIVE) {
			arrive(level, slave);
			return;
		}
		BlockPos goal = site;
		if (len > WAYPOINT) {
			BlockPos column = BlockPos.containing(slave.getX() + dx / len * WAYPOINT, slave.getY(), slave.getZ() + dz / len * WAYPOINT);
			goal = level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column;
		}
		// (its post moves with it, or it would be pulled back to the camp)
		slave.setHomeTo(goal, 6);
		slave.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, RUN_SPEED);
	}

	/** A runaway has reached its site: a goblin village there takes it in; else it raises one if the ground will have it. */
	private static void arrive(ServerLevel level, Folk slave) {
		MinecraftServer server = level.getServer();
		Faction village = nearestVillage(server, slave.blockPosition(), JOIN_NEAR);
		if (village != null) {
			join(level, slave, village);
			return;
		}
		BlockPos at = slave.blockPosition();
		RandomSource random = level.getRandom();
		int floor = Integer.MIN_VALUE;
		boolean fit = Camps.open(server, level, at) && GoblinVillages.land(level, at) && cornersLoaded(level, at);
		if (fit) {
			floor = IllagerBuildings.floor(level, at, GoblinVillages.MAX_SPREAD, GoblinBuildings.CLEAR_RADIUS);
		}
		if (fit && floor != Integer.MIN_VALUE) {
			Faction founded = GoblinVillages.found(level, at, floor, net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random));
			JaymosKingdoms.LOGGER.info("A runaway goblin slave raised {} at {}", founded.name(), at);
			// ((0.7.490) only if its starting goblins left room under the cap; else it runs on)
			Faction fresh = Factions.get(server, founded.id());
			if (fresh != null && hasRoom(server, fresh)) {
				join(level, slave, fresh);
			} else {
				slave.setSite(farSite(level, slave, RUN_STEP));
			}
		} else if (random.nextFloat() < LOST_CHANCE && !Raids.playerNear(level, at, Raids.VANISH_RANGE)) {
			// (lost to the wild, unseen)
			slave.discard();
		} else {
			slave.setSite(farSite(level, slave, RUN_STEP));
		}
	}

	private static boolean cornersLoaded(ServerLevel level, BlockPos at) {
		int r = GoblinBuildings.CLEAR_RADIUS;
		for (BlockPos corner : new BlockPos[] {at.offset(-r, 0, -r), at.offset(r, 0, -r), at.offset(-r, 0, r), at.offset(r, 0, r)}) {
			if (!level.isLoaded(corner)) {
				return false;
			}
		}
		return true;
	}

	/** The slave is a goblin of {@code village} from now on. */
	private static void join(ServerLevel level, Folk slave, Faction village) {
		MinecraftServer server = level.getServer();
		slave.removeTag(RUNAWAY);
		slave.removeTag(FREED);
		slave.setCarried(ItemStack.EMPTY);
		slave.setTask("");
		slave.setSite(null);
		slave.getNavigation().stop();
		slave.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		slave.setup(Goblins.GOBLIN, village, village.seat(), GoblinBuildings.HOME_RADIUS);
		Factions.update(server, village.id(), f -> f.withHoldings(f.holdings().adjust(Goblins.GOBLIN, 1)));
		Speech.say(level, slave, Dialects.trBrood(Dialects.GOBLIN, "speech.jaymoskingdoms.slave.join_" + level.getRandom().nextInt(JOIN_LINES)), Speech.YELLOW, SAY_RANGE);
	}

	// ---- freeing one -------------------------------------------------------------------------------------------------------------

	/** {@code player} breaks {@code slave}'s chain: it runs for freedom, the goblin tribe thinks well of them and the orc tribe the worse. */
	private static void free(ServerLevel level, Folk slave, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		level.playSound(null, slave.getX(), slave.getY(), slave.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.2F, 0.9F);
		level.playSound(null, slave.getX(), slave.getY(), slave.getZ(), SoundEvents.ANVIL_BREAK, SoundSource.NEUTRAL, 0.4F, 1.4F);
		level.sendParticles(ParticleTypes.CRIT, slave.getX(), slave.getY() + 0.8, slave.getZ(), 14, 0.3, 0.4, 0.3, 0.15);
		String orcs = Tribes.of(server, slave.factionId());
		if (orcs != null) {
			Tribes.add(server, player.getUUID(), Tribes.rival(orcs), -FREED_FAVOUR);
			Tribes.add(server, player.getUUID(), orcs, FREED_GRUDGE);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.slave.freed"), true);
		Speech.sayTo(level, slave, player, Dialects.trBrood(Dialects.GOBLIN, "speech.jaymoskingdoms.slave.freed_" + random.nextInt(FREED_LINES)), Speech.YELLOW);
		for (PathfinderMob orc : level.getEntitiesOfClass(PathfinderMob.class, slave.getBoundingBox().inflate(SHOUT_RANGE),
			e -> e instanceof Worker w && w.factionId() == slave.factionId() && Orcs.isOrc(w.role()) && e.isAlive() && !e.isSleeping())) {
			Speech.say(level, orc, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.freed_" + random.nextInt(SHOUT_LINES)), Speech.RED, SAY_RANGE);
			Rulers.charge(orc, player);
			break;
		}
		runOff(level, slave, true);
	}

	static void clear() {
		THROWN.clear();
		TOSS_READY.clear();
	}
}
