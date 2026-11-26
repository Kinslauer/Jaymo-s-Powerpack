package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Who goes where when the weather or the dark turns (Reference Mods patch 4, U7; Andy: "villagers also need to run for
 * cover at night, near fire, or monsters. only the brave and guards should ever fight"). It extends the fear that is
 * already there ({@link Frights}, {@link Firefighting}) rather than a second system: cover is a {@link Frights#shelter}.
 * <ul>
 * <li><b>Heavy rain</b>: whoever carries an umbrella opens it on their way ({@link Parasols}); of the rest, half pull
 * a hood up and carry on (taken off when it eases), half run for cover: home if it's near, else the nearest roofed spot.</li>
 * <li><b>Night</b>: whoever is caught out under the open sky goes under cover too.</li>
 * <li><b>Guards and the brave</b> ({@link Temperaments#fearless}) stand: they don't take cover, and don't run from
 * monsters or fire ({@link Frights#standsFirm}, {@link Firefighting}). A brave villager goes at a monster that comes
 * near and strikes it ({@link #fight}); everyone else runs.</li>
 * </ul>
 */
final class TakeCover {
	/** How often the people are looked at (ticks); how close to a seat they're watched. */
	static final int EVERY = 20, LOOK = 10;
	static final double WATCHED = 96.0;
	/** Rain this hard is heavy (the sky's rain level, 0..1). */
	static final float HEAVY = 0.8F;
	/** How long they stay under cover (ticks). */
	static final long COVER_TICKS = 400L;
	/** A roofed spot is looked for within this many blocks, in this many tries. */
	static final int SEARCH = 12, TRIES = 30;
	/** Home counts as near within this many blocks. */
	static final int HOME_NEAR = 40;
	/** A brave villager's blow: reach, damage, ticks between blows, speed going at it. */
	static final double REACH = 2.0;
	static final float PUNCH = 2.0F, SPEED = 0.8F;
	static final int PUNCH_TICKS = 25;
	static final String HOOD = "jaymoskingdoms_rainhood";
	/** A hood's cloth (RGB). */
	static final int HOOD_CLOTH = 0x6B5A44;

	/** Who has a hood up, and who may strike next. */
	private static final Set<UUID> HOODED = new HashSet<>();
	private static final Map<UUID, Long> NEXT_PUNCH = new HashMap<>();

	private TakeCover() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TakeCover::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOODED.clear();
			NEXT_PUNCH.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % LOOK != 0) {
			return;
		}
		boolean heavy = level.isRaining() && level.getRainLevel(1.0F) >= HEAVY;
		if (!heavy && !HOODED.isEmpty() && now % EVERY == 0) {
			unhood(level);
		}
		boolean night = Vampires.night(level.getOverworldClockTime());
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			boolean sweep = now % EVERY == 0 && (heavy || night);
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), v -> v.isAlive() && !v.isBaby())) {
				if (Temperaments.fearless(v) && !BlastFright.standsGround(v)) {
					fight(level, v, f, now);
				}
				if (sweep && wantsCover(v)) {
					cover(level, v, heavy, night, now);
				}
			}
		}
	}

	/** Who takes cover: not a guard at a post, not the brave, not anyone busy being held, hurt or asleep. */
	private static boolean wantsCover(Villager v) {
		return !Temperaments.fearless(v) && !BlastFright.standsGround(v) && !v.isSleeping() && !v.isPassenger() && !Downed.isDowned(v)
			&& !v.hasAttached(Captives.CAPTIVE) && !Thralls.isThrall(v) && !Drifters.is(v);
	}

	private static void cover(ServerLevel level, Villager v, boolean heavy, boolean night, long now) {
		BlockPos at = v.blockPosition();
		if (!level.canSeeSky(at.above())) {
			return;
		}
		boolean rained = heavy && level.isRainingAt(at);
		if (!rained && !night) {
			return;
		}
		if (rained && Parasols.owns(v)) {
			return; // it's open in the rain already (Parasols), and they carry on
		}
		if (rained && !night && Math.floorMod(v.getUUID().hashCode(), 2) == 0) {
			hood(v);
			return;
		}
		BlockPos to = shelter(level, v);
		if (to != null) {
			Frights.shelter(level, v, to, now + COVER_TICKS);
		}
	}

	/** Home if it's near, else the nearest roofed spot within reach; null if there's none. */
	private static @Nullable BlockPos shelter(ServerLevel level, Villager v) {
		Optional<GlobalPos> home = v.getBrain().getMemoryInternal(MemoryModuleType.HOME);
		if (home != null && home.isPresent() && home.get().dimension() == level.dimension() && home.get().pos().closerThan(v.blockPosition(), HOME_NEAR)) {
			return home.get().pos();
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < TRIES; i++) {
			BlockPos spot = Construction.standable(level, v.blockPosition().offset(v.getRandom().nextInt(2 * SEARCH + 1) - SEARCH, 0,
				v.getRandom().nextInt(2 * SEARCH + 1) - SEARCH), 1);
			double d = spot.distSqr(v.blockPosition());
			if (d < bestD && Math.abs(spot.getY() - v.getBlockY()) <= 3 && !level.canSeeSky(spot.above(2))) {
				bestD = d;
				best = spot;
			}
		}
		return best;
	}

	// ---- hoods ----

	private static void hood(Villager v) {
		if (!v.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			return; // already covered
		}
		ItemStack hood = Outfits.dyed(Items.LEATHER_HELMET, HOOD_CLOTH);
		CustomData.update(DataComponents.CUSTOM_DATA, hood, tag -> tag.putBoolean(HOOD, true));
		v.setItemSlot(EquipmentSlot.HEAD, hood);
		v.setDropChance(EquipmentSlot.HEAD, 0.0F);
		HOODED.add(v.getUUID());
	}

	/** The rain has eased: the hoods come down. */
	private static void unhood(ServerLevel level) {
		HOODED.removeIf(id -> {
			if (level.getEntity(id) instanceof LivingEntity who) {
				ItemStack head = who.getItemBySlot(EquipmentSlot.HEAD);
				CustomData data = head.get(DataComponents.CUSTOM_DATA);
				if (data != null && data.copyTag().getBooleanOr(HOOD, false)) {
					who.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				}
			}
			return true;
		});
	}

	// ---- the brave ----

	/** A brave villager goes at a monster that has come near, and strikes it. */
	private static void fight(ServerLevel level, Villager v, Faction f, long now) {
		LivingEntity monster = Frights.monster(level, v, f);
		if (monster == null) {
			return;
		}
		if (v.distanceTo(monster) > REACH) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(monster, false), SPEED, 1);
			return;
		}
		if (now >= NEXT_PUNCH.getOrDefault(v.getUUID(), 0L)) {
			NEXT_PUNCH.put(v.getUUID(), now + PUNCH_TICKS);
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			monster.hurtServer(level, level.damageSources().mobAttack(v), PUNCH);
		}
	}
}
