package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Grabbing what falls in a fight (docs/Pawn-Plan.md row H5; after the pawns who gather up loot mid-battle in Dragon's
 * Dogma, a commercial game: ideas only). A fighter who goes for the spoils ({@link Comrades.Leaning#SPOILS}, main or
 * second) goes after a weapon, a tool, a piece of armour or anything enchanted lying within {@link #LOOK} of it.
 * <ul>
 * <li><b>Friend</b>: your companion picks it up and puts it in your hands ("spoils.give"), or at your feet if they're full.</li>
 * <li><b>Foe</b>: a raider takes it up in an empty hand and fights with it (and drops it when it falls); a goblin who
 * gets something runs off with it ({@link #RUN_TICKS}, "spoils.grab"). Your dropped sword can end up in a goblin's
 * fist halfway across the field.</li>
 * </ul>
 */
public final class Scavengers {
	/** Things are seen this far off (blocks); picked up this close (blocks); a goblin runs this far (blocks) for this long (ticks). */
	static final double LOOK = 10.0, TAKE = 1.6, RUN = 20.0;
	static final int RUN_TICKS = 200;
	/** Fighters are looked at this often (ticks); one goes for loot at most this long (ticks), and not again for this long (ticks). */
	static final int EVERY = 10, FETCH_TICKS = 160, GAP = 300;
	/** Chance a fighter whose second leaning is the spoils goes for it (a main leaning always does). */
	static final float SECOND_CHANCE = 0.35F;
	static final double PACE = 1.1;

	private static final class Fetch {
		final ItemEntity item;
		int ticks;
		@Nullable Vec3 runTo;

		Fetch(ItemEntity item) {
			this.item = item;
		}
	}

	private static final Map<Mob, Fetch> FETCHING = new IdentityHashMap<>();
	private static final Map<ItemEntity, Mob> CLAIMED = new IdentityHashMap<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private Scavengers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Scavengers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FETCHING.clear();
			CLAIMED.clear();
			NEXT.clear();
		});
	}

	/** {@code m} is off after loot, or running off with it. */
	public static boolean busy(Mob m) {
		return FETCHING.containsKey(m);
	}

	/** Worth grabbing mid-fight. */
	static boolean worth(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL) || stack.has(DataComponents.EQUIPPABLE)
			|| stack.isEnchanted());
	}

	private static void tick(MinecraftServer server) {
		if (!FETCHING.isEmpty()) {
			for (Map.Entry<Mob, Fetch> e : new ArrayList<>(FETCHING.entrySet())) {
				step(e.getKey(), e.getValue());
			}
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		NEXT.values().removeIf(t -> t < now);
		for (ServerLevel level : server.getAllLevels()) {
			for (Mob m : Comrades.fighters(level)) {
				look(level, m, now);
			}
		}
	}

	private static void look(ServerLevel level, Mob m, long now) {
		if (FETCHING.containsKey(m) || NEXT.containsKey(m.getUUID()) || Comrades.busy(m) || !Temperaments.humanoid(m)) {
			return;
		}
		Comrades.Leanings l = Comrades.leanings(m);
		if (l.main() != Comrades.Leaning.SPOILS && (l.second() != Comrades.Leaning.SPOILS || m.getRandom().nextFloat() >= SECOND_CHANCE)) {
			return;
		}
		String side = Comrades.side(m);
		boolean friend = side.startsWith("p:");
		Player owner = friend ? Comrades.playerOf(level, side) : null;
		if (friend && owner == null || !friend && !m.getMainHandItem().isEmpty() && !m.getOffhandItem().isEmpty()) {
			// a raider with both hands full has nowhere to put it
			return;
		}
		ItemEntity best = null;
		double bestD = LOOK * LOOK;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, m.getBoundingBox().inflate(LOOK),
			i -> i.isAlive() && !i.hasPickUpDelay() && !CLAIMED.containsKey(i) && worth(i.getItem()))) {
			if (friend && item.getOwner() == owner) {
				// what you threw down yourself stays down
				continue;
			}
			double d = m.distanceToSqr(item);
			if (d < bestD && m.hasLineOfSight(item)) {
				bestD = d;
				best = item;
			}
		}
		if (best == null) {
			return;
		}
		NEXT.put(m.getUUID(), now + GAP);
		FETCHING.put(m, new Fetch(best));
		CLAIMED.put(best, m);
		Comrades.stand(m);
		walk(m, best.position());
	}

	private static void step(Mob m, Fetch f) {
		if (!m.isAlive() || m.isRemoved() || Downed.isDowned(m) || ++f.ticks > (f.runTo != null ? RUN_TICKS : FETCH_TICKS)) {
			end(m, f);
			return;
		}
		Comrades.stand(m);
		if (f.runTo != null) {
			// off with it
			if (f.ticks % 20 == 0) {
				walk(m, f.runTo);
			}
			return;
		}
		ItemEntity item = f.item;
		if (!item.isAlive() || item.isRemoved()) {
			end(m, f);
			return;
		}
		if (m.distanceToSqr(item) > TAKE * TAKE) {
			if (f.ticks % 10 == 0) {
				walk(m, item.position());
			}
			return;
		}
		take(m, f);
	}

	private static void take(Mob m, Fetch f) {
		ServerLevel level = (ServerLevel) m.level();
		ItemEntity item = f.item;
		ItemStack stack = item.getItem().copy();
		String side = Comrades.side(m);
		if (side.startsWith("p:")) {
			ServerPlayer owner = Comrades.playerOf(level, side);
			if (owner == null) {
				end(m, f);
				return;
			}
			item.discard();
			Callouts.say(m, "spoils.give", owner, true, stack.getHoverName());
			if (!owner.getInventory().add(stack) && !stack.isEmpty()) {
				ItemEntity drop = new ItemEntity(level, owner.getX(), owner.getY() + 0.3, owner.getZ(), stack);
				drop.setNoPickUpDelay();
				level.addFreshEntity(drop);
			}
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
			end(m, f);
			return;
		}
		EquipmentSlot hand = m.getMainHandItem().isEmpty() ? EquipmentSlot.MAINHAND : m.getOffhandItem().isEmpty() ? EquipmentSlot.OFFHAND : null;
		if (hand == null) {
			end(m, f);
			return;
		}
		item.discard();
		m.setItemSlot(hand, stack);
		// it's yours again when they fall
		m.setDropChance(hand, 2.0F);
		m.setPersistenceRequired();
		level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.5F, 0.9F);
		Callouts.say(m, "spoils.grab", null, false, stack.getHoverName());
		if (!Goblins.isGoblin(m)) {
			end(m, f);
			return;
		}
		// a goblin makes off with it
		Player from = level.getNearestPlayer(m, RUN * 2.0);
		Vec3 away = from == null ? m.getLookAngle() : m.position().subtract(from.position());
		away = away.multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		f.runTo = m.position().add(away.normalize().scale(RUN));
		f.ticks = 0;
		CLAIMED.remove(item);
		walk(m, f.runTo);
	}

	private static void end(Mob m, Fetch f) {
		FETCHING.remove(m);
		CLAIMED.remove(f.item);
		m.getNavigation().stop();
	}

	private static void walk(Mob m, Vec3 to) {
		if (m instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, BlockPos.containing(to), (float) PACE * 0.6F, 0);
		} else {
			m.getNavigation().moveTo(to.x, to.y, to.z, PACE);
		}
	}
}
