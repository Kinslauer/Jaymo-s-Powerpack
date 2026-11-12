package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Keeping watch (fifth edition's watches during a long rest, Andy's pick E2). Sleeping out (a bed in the open, or under
 * only a tent's wool or leaves) with friends or companions:
 * <ul>
 * <li><b>A watcher</b> is someone awake near the sleepers: another player, or a companion who takes orders, or a tame wolf.
 * By a lit campfire they see far ({@link #SIGHT_FIRE} blocks); in the dark, not far ({@link #SIGHT_DARK}).</li>
 * <li><b>Unwatched camps</b> draw visitors in the night, now and then: a pack of wolves, a bandit, or a pack thief who
 * creeps in, takes something from a sleeper's pack and runs. A watched camp is left alone.</li>
 * <li><b>The watcher shouts.</b> When a monster comes in sight of a watcher near the camp, they cry out (a speech bubble and
 * a bell) and every sleeper in the camp wakes at once, ready for it.</li>
 * </ul>
 * Visitors are marked {@link #VISITOR}; they go away if the night does not end with them dead.
 */
public final class NightWatch {
	/** Sleepers this near each other (blocks) are one camp. */
	static final double CAMP = 14.0;
	/** A watcher this near (blocks) to a sleeper is keeping watch; what they see of an approaching monster, by a fire and without. */
	static final double WATCH_NEAR = 14.0, SIGHT_FIRE = 28.0, SIGHT_DARK = 14.0;
	/** A lit campfire this near a watcher is their fire (blocks). */
	static final int FIRE_NEAR = 8;
	/** How often (ticks) camps are looked at. */
	static final int EVERY = 20;
	/** A sleeper has been asleep this long (ticks) before a visit comes for them. */
	static final int ASLEEP_FOR = 100;
	/** The chance of a visit each look at an unwatched camp, and the least game time (ticks) between visits at one camp. */
	static final float VISIT_CHANCE = 0.03F;
	static final long VISIT_GAP = 3600L;
	/** Where visitors come in from (blocks away from a sleeper). */
	static final int COME_MIN = 16, COME_MAX = 24;
	/** The pack of wolves (least, and extra at most); a bandit's and a thief's pace (a speed effect level). */
	static final int WOLVES_LEAST = 2, WOLVES_EXTRA = 2;
	/** A watcher cries out at most this often for one camp (ticks). */
	static final long SHOUT_GAP = 600L;
	/** Visitors with no player near go away after this long (ticks) or this far (blocks). */
	static final long VISIT_LIFE = 2400L;
	static final double FAR = 48.0;
	static final int SPEECH_LINES = 4;

	public static final String VISITOR = "jaymoskingdoms.night_visitor", THIEF = "jaymoskingdoms.night_thief";

	/** When each camp (by its first sleeper) was last visited or shouted for. */
	private static final Map<UUID, Long> VISITED = new HashMap<>(), SHOUTED = new HashMap<>();
	/** When each sleeper fell asleep (game time). */
	private static final Map<UUID, Long> FELL = new HashMap<>();
	/** The thieves: who they go for, and whether they have taken something (and are running). */
	private static final Map<UUID, UUID> MARKS = new HashMap<>();
	private static final Set<UUID> LOADED_DOWN = new HashSet<>();
	private static final Map<UUID, Long> BORN = new HashMap<>();

	private NightWatch() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NightWatch::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VISITED.clear();
			SHOUTED.clear();
			FELL.clear();
			MARKS.clear();
			LOADED_DOWN.clear();
			BORN.clear();
		});
	}

	// ---- who sleeps out --------------------------------------------------------------------------------------------------------

	/** Whether the sleeper's bed is out in the open: under the sky, or under only wool, carpet or leaves (a tent, a lean-to). */
	static boolean outdoors(ServerLevel level, BlockPos bed) {
		for (BlockPos p = bed.above(); p.getY() < level.getMaxY(); p = p.above()) {
			BlockState state = level.getBlockState(p);
			if (state.isAir() || state.getCollisionShape(level, p).isEmpty()) {
				if (level.canSeeSky(p)) {
					return true;
				}
				continue;
			}
			if (!(state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.BANNERS))) {
				return false;
			}
			if (p.getY() - bed.getY() > 4) {
				return false;
			}
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
				continue;
			}
			long now = level.getGameTime();
			List<ServerPlayer> sleepers = new ArrayList<>();
			for (ServerPlayer p : level.players()) {
				if (p.isSleeping() && !p.isSpectator() && p.getSleepingPos().isPresent() && outdoors(level, p.getSleepingPos().get())) {
					FELL.putIfAbsent(p.getUUID(), now);
					if (now - FELL.get(p.getUUID()) >= ASLEEP_FOR) {
						sleepers.add(p);
					}
				} else {
					FELL.remove(p.getUUID());
				}
			}
			Set<ServerPlayer> done = new HashSet<>();
			for (ServerPlayer first : sleepers) {
				if (!done.add(first)) {
					continue;
				}
				List<ServerPlayer> camp = new ArrayList<>(List.of(first));
				for (ServerPlayer other : sleepers) {
					if (other != first && other.distanceToSqr(first) <= CAMP * CAMP && done.add(other)) {
						camp.add(other);
					}
				}
				camp(level, camp, now);
			}
			visitors(level, now);
		}
	}

	private static void camp(ServerLevel level, List<ServerPlayer> camp, long now) {
		ServerPlayer first = camp.get(0);
		LivingEntity watcher = watcher(level, camp);
		if (watcher == null) {
			if (now - VISITED.getOrDefault(first.getUUID(), -VISIT_GAP) >= VISIT_GAP && level.isDarkOutside() && level.getRandom().nextFloat() < VISIT_CHANCE) {
				VISITED.put(first.getUUID(), now);
				visit(level, camp);
			}
			return;
		}
		// a watcher: they cry out at a monster they see coming
		if (now - SHOUTED.getOrDefault(first.getUUID(), -SHOUT_GAP) < SHOUT_GAP) {
			return;
		}
		double sight = byFire(level, watcher) ? SIGHT_FIRE : SIGHT_DARK;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, watcher.getBoundingBox().inflate(sight), m -> m.isAlive() && approaching(m, camp))) {
			if (mob.distanceToSqr(watcher) <= sight * sight && watcher.hasLineOfSight(mob)) {
				SHOUTED.put(first.getUUID(), now);
				shout(level, watcher, camp);
				return;
			}
		}
	}

	/** A monster that is a danger to the camp: hostile, not a ward of anyone, near it or hunting a sleeper. */
	private static boolean approaching(Mob mob, List<ServerPlayer> camp) {
		if (!(mob instanceof Enemy) || mob instanceof Folk || mob instanceof OwnableEntity || mob.getTarget() == null && mob.hasEffect(MobEffects.INVISIBILITY)) {
			return false;
		}
		for (ServerPlayer p : camp) {
			if (mob.getTarget() == p || mob.distanceToSqr(p) <= SIGHT_FIRE * SIGHT_FIRE) {
				return true;
			}
		}
		return false;
	}

	private static boolean byFire(ServerLevel level, Entity who) {
		BlockPos at = who.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-FIRE_NEAR, -2, -FIRE_NEAR), at.offset(FIRE_NEAR, 2, FIRE_NEAR))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof CampfireBlock && CampfireBlock.isLitCampfire(state)) {
				return true;
			}
		}
		return false;
	}

	/** Someone awake near the sleepers who keeps watch: a player, a companion, a tame wolf. */
	static @Nullable LivingEntity watcher(ServerLevel level, List<ServerPlayer> camp) {
		LivingEntity best = null;
		boolean bestFire = false;
		for (ServerPlayer sleeper : camp) {
			AABB box = sleeper.getBoundingBox().inflate(WATCH_NEAR);
			List<LivingEntity> candidates = new ArrayList<>();
			for (ServerPlayer p : level.players()) {
				if (!p.isSleeping() && !p.isSpectator() && p.isAlive() && !PowersCompat.helpless(p) && box.intersects(p.getBoundingBox())) {
					candidates.add(p);
				}
			}
			candidates.addAll(level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isSleeping() && !PowersCompat.helpless(v) && takesOrdersFromCamp(v, camp)));
			candidates.addAll(level.getEntitiesOfClass(Wolf.class, box, w -> w.isAlive() && w.isTame() && camp.stream().anyMatch(p -> w.getOwnerReference() != null && p.getUUID().equals(w.getOwnerReference().getUUID()))));
			for (LivingEntity c : candidates) {
				boolean fire = byFire(level, c);
				if (best == null || fire && !bestFire) {
					best = c;
					bestFire = fire;
				}
			}
		}
		return best;
	}

	private static boolean takesOrdersFromCamp(Villager v, List<ServerPlayer> camp) {
		for (ServerPlayer p : camp) {
			if (Followers.takesOrders(v, p)) {
				return true;
			}
		}
		return false;
	}

	/** The watcher cries out and the whole camp wakes. */
	private static void shout(ServerLevel level, LivingEntity watcher, List<ServerPlayer> camp) {
		Speech.say(level, watcher, Component.translatable("speech.jaymoskingdoms.watch.shout_" + level.getRandom().nextInt(SPEECH_LINES)), Speech.RED, 32.0);
		if (watcher instanceof Wolf wolf) {
			level.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.WOLF_SHAKE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		for (ServerPlayer sleeper : camp) {
			level.playSound(null, sleeper.getX(), sleeper.getY(), sleeper.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.4F);
			sleeper.stopSleepInBed(true, true);
		}
	}

	// ---- visitors --------------------------------------------------------------------------------------------------------------

	private static void visit(ServerLevel level, List<ServerPlayer> camp) {
		ServerPlayer target = camp.get(level.getRandom().nextInt(camp.size()));
		BlockPos spot = approach(level, target.blockPosition(), level.getRandom());
		if (spot == null) {
			return;
		}
		int pick = level.getRandom().nextInt(100);
		boolean wild = !level.getBiome(spot).is(net.minecraft.tags.BiomeTags.IS_OCEAN);
		if (pick < (wild ? 40 : 0)) {
			wolves(level, spot, target);
		} else if (pick < 75) {
			bandit(level, spot, target);
		} else {
			thief(level, spot, target);
		}
	}

	private static @Nullable BlockPos approach(ServerLevel level, BlockPos camp, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0, r = COME_MIN + random.nextDouble() * (COME_MAX - COME_MIN);
			BlockPos near = BlockPos.containing(camp.getX() + Math.cos(angle) * r, camp.getY(), camp.getZ() + Math.sin(angle) * r);
			if (!level.isPositionEntityTicking(near)) {
				continue;
			}
			BlockPos spot = Construction.standable(level, near, 3);
			if (level.getBlockState(spot.below()).isSolid() && Math.abs(spot.getY() - camp.getY()) <= 6) {
				return spot;
			}
		}
		return null;
	}

	private static void wolves(ServerLevel level, BlockPos spot, ServerPlayer target) {
		int n = WOLVES_LEAST + level.getRandom().nextInt(WOLVES_EXTRA + 1);
		for (int i = 0; i < n; i++) {
			Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
			if (wolf == null) {
				continue;
			}
			wolf.snapTo(spot.getX() + 0.5 + i * 1.2, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			wolf.setPersistentAngerTarget(EntityReference.of(target));
			wolf.startPersistentAngerTimer();
			wolf.setTarget(target);
			mark(level, wolf);
		}
		level.playSound(null, spot, SoundEvents.WOLF_GROWL_BABY.value(), SoundSource.NEUTRAL, 1.5F, 0.55F);
	}

	private static void bandit(ServerLevel level, BlockPos spot, ServerPlayer target) {
		Vindicator bandit = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
		if (bandit == null) {
			return;
		}
		bandit.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		dress(bandit, new ItemStack(Items.IRON_AXE));
		bandit.setTarget(target);
		mark(level, bandit);
	}

	/** The pack thief: empty-handed and quick, he creeps to a sleeper, takes something from their pack, and runs. */
	private static void thief(ServerLevel level, BlockPos spot, ServerPlayer target) {
		Vindicator thief = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
		if (thief == null) {
			return;
		}
		thief.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		dress(thief, ItemStack.EMPTY);
		thief.addTag(THIEF);
		thief.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 120, 1, true, false, false));
		MARKS.put(thief.getUUID(), target.getUUID());
		mark(level, thief);
	}

	private static void dress(Mob mob, ItemStack hand) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, hand);
		mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		if (Bandits.MASK != null) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Bandits.MASK));
			mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
		}
		mob.setCanPickUpLoot(false);
		mob.setPersistenceRequired();
	}

	private static void mark(ServerLevel level, Mob mob) {
		mob.addTag(VISITOR);
		BORN.put(mob.getUUID(), level.getGameTime());
		level.addFreshEntity(mob);
	}

	/** The visitors: thieves go for their sleeper, and any that outstay the night go home. */
	private static void visitors(ServerLevel level, long now) {
		if (BORN.isEmpty()) {
			return;
		}
		var it = BORN.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Entity found = level.getEntity(e.getKey());
			if (found == null) {
				continue;
			}
			if (!(found instanceof Mob mob) || !mob.isAlive()) {
				it.remove();
				MARKS.remove(e.getKey());
				LOADED_DOWN.remove(e.getKey());
				continue;
			}
			ServerPlayer near = level.getNearestPlayer(mob, FAR) instanceof ServerPlayer p ? p : null;
			if (now - e.getValue() > VISIT_LIFE && (near == null || mob.distanceToSqr(near) > 24.0 * 24.0) || near == null) {
				if (LOADED_DOWN.remove(e.getKey()) && !mob.getMainHandItem().isEmpty()) {
					// out of sight, he lets go of what he took, where he stands: it can be found
					mob.spawnAtLocation(level, mob.getMainHandItem().copy());
				}
				mob.discard();
				it.remove();
				MARKS.remove(e.getKey());
				continue;
			}
			if (mob.entityTags().contains(THIEF)) {
				thieve(level, (PathfinderMob) mob);
			}
		}
	}

	private static void thieve(ServerLevel level, PathfinderMob thief) {
		UUID mark = MARKS.get(thief.getUUID());
		if (LOADED_DOWN.contains(thief.getUUID())) {
			// he has something: he runs from the camp
			Vec3 away = thief.position().subtract(level.getNearestPlayer(thief, FAR) == null ? thief.position() : level.getNearestPlayer(thief, FAR).position());
			Vec3 to = thief.position().add(away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize().scale(20.0));
			thief.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
			return;
		}
		if (mark == null || !(level.getPlayerByUUID(mark) instanceof ServerPlayer victim) || !victim.isAlive()) {
			MARKS.remove(thief.getUUID());
			return;
		}
		if (thief.distanceToSqr(victim) > 2.4 * 2.4) {
			thief.getNavigation().moveTo(victim, 1.3);
			return;
		}
		// at the sleeper's side: something from their pack
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
			if (!victim.getInventory().getItem(i).isEmpty() && i < 36) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			MARKS.remove(thief.getUUID());
			thief.discard();
			return;
		}
		int slot = slots.get(thief.getRandom().nextInt(slots.size()));
		ItemStack taken = victim.getInventory().getItem(slot).copy();
		victim.getInventory().setItem(slot, ItemStack.EMPTY);
		thief.setItemSlot(EquipmentSlot.MAINHAND, taken);
		thief.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
		LOADED_DOWN.add(thief.getUUID());
		level.playSound(null, thief.getX(), thief.getY(), thief.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 0.8F, 0.8F);
		level.sendParticles(ParticleTypes.SMOKE, thief.getX(), thief.getY(1.0), thief.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
	}
}
