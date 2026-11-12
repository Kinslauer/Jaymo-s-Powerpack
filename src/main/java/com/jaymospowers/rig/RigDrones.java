package com.jaymospowers.rig;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.forms.HiveSwarm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rig's drones: small cold-metal flyers of the Frostborn kind, each a thruster ring and a sensor eye, launched from the Core. The flying,
 * fighting, missile and shell are Hive's swarm logic ({@link HiveSwarm}) run with a kit of their own (the Rig's cyan, no
 * venom, a smaller share of every blow to the wearer); what is the Rig's here is how many fly (one for each level of the
 * Mind, up to five), what they do with no foe to fight, and how they are drawn.
 * <ul>
 * <li>Formations: close in, surround, strafe, scout (picked at the wrist table, {@link RigTable}).</li>
 * <li><b>Mend</b>: each drone near the wearer slowly mends their hurts.</li>
 * <li><b>Fetch</b>: with nothing to fight, a drone takes a dropped item up and carries it back to the wearer's feet.</li>
 * <li><b>Patrol</b>: with nothing to fight, the drones walk a short route the wearer marked by looking at a spot and
 * pressing ({@link #mark}); up to {@link #ROUTE_MAX} marks, the oldest replaced by a fifth.</li>
 * <li>A missile (one drone streaks at the lock or what is under the crosshair; it is back after a while) and the shell
 * (all drones round the wearer, soaking blows).</li>
 * </ul>
 */
public final class RigDrones {
	/** The Core's charge to wind the drones up. */
	public static final int DEPLOY_COST = 60;
	/** The Rig's cyan (the palm blast's), its hull shadow, and the glint of the sensor eye. */
	public static final int CYAN = 0x5FE6FF, HULL_DARK = 0x1E4A5A, LENS = 0xDFF8FF;
	/** Drones' damage against Hive's stings, and the share of a blow to a drone (or the shell) that the wearer feels. */
	public static final float DAMAGE = 1.25F, SHARE = 0.35F, SHELL_SHARE = 0.3F;
	/** Mend: ticks between, health for each drone near the wearer, and how near counts. */
	public static final int MEND_EVERY = 40;
	public static final float MEND_EACH = 0.25F;
	public static final double MEND_RANGE = 8.0;
	/** Fetch: how far from a drone it sees an item, how old the item must be (ticks, so a thrown stack is left), how far from the wearer it must lie, and how near the wearer it lets go. */
	public static final double FETCH_RANGE = 16.0, FETCH_MIN_FROM_OWNER = 3.0, DROP_NEAR = 2.0, PICK_NEAR = 1.0;
	public static final int FETCH_MIN_AGE = 60, FETCH_LOOK_EVERY = 10;
	/** Patrol: the most marks, how far from the wearer one can be set (blocks), how near a drone must get to a mark, and the pause there (ticks). */
	public static final int ROUTE_MAX = 4, PATROL_HOLD = 30;
	public static final double ROUTE_REACH = 48.0, ROUTE_ARRIVE = 2.5, ROUTE_HEIGHT = 1.6, ROUTE_SPREAD = 1.2;
	/** The missile's reach to find a foe under the crosshair. */
	public static final double MISSILE_REACH = 32.0;

	/** What one wearer has told their drones. */
	static final class Crew {
		boolean fetch = true, mend = true, patrol;
		final List<Vec3> route = new ArrayList<>();
		int waypoint;
		long holdUntil;
		final int[] carry = new int[HiveSwarm.FLYERS];
		final int[] goal = new int[HiveSwarm.FLYERS];

		Crew() {
			java.util.Arrays.fill(carry, -1);
			java.util.Arrays.fill(goal, -1);
		}
	}

	private static final Map<UUID, Crew> CREWS = new HashMap<>();

	/** The kit: how many fly, where they go with no foe, how they are drawn. */
	public static final HiveSwarm.Kit KIT = new HiveSwarm.Kit(CYAN, HULL_DARK, false, SoundEvents.BEACON_ACTIVATE, SoundEvents.BEACON_POWER_SELECT,
		DAMAGE, SHARE, SHELL_SHARE, "message.jaymospowers.rig_picks.drone.", new HiveSwarm.Hook() {
			@Override
			public int count(ServerPlayer player) {
				return Mth.clamp(Rig.level(player), 1, HiveSwarm.FLYERS);
			}

			@Override
			public @Nullable Vec3 idle(ServerPlayer player, int i, Vec3 pos, long t) {
				return RigDrones.idle(player, i, pos, t);
			}

			@Override
			public void draw(ServerLevel level, Vec3 at, long t, int i) {
				RigDrones.draw(level, at, t, i);
			}
		});

	private RigDrones() {
	}

	static void init() {
		// a blow to the wearer: the shell takes it first
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player) || !HiveSwarm.out(KIT, player) || HiveSwarm.soak(KIT, player, source, amount));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CREWS.remove(handler.player.getUUID());
			HiveSwarm.clear(KIT, handler.player);
		});
	}

	static Crew crew(ServerPlayer player) {
		return CREWS.computeIfAbsent(player.getUUID(), k -> new Crew());
	}

	static boolean out(ServerPlayer player) {
		return HiveSwarm.out(KIT, player);
	}

	// ---- the keys -------------------------------------------------------------------------------------------------------

	/** The Drones entry: wind them up if they are home; if they are out, one streaks at the lock or the crosshair as a missile. */
	static void use(ServerPlayer player) {
		if (!out(player)) {
			deploy(player);
			return;
		}
		LivingEntity target = RigActives.locked(player);
		if (target == null) {
			target = PickKit.aimed(player, MISSILE_REACH);
		}
		if (!HiveSwarm.missile(KIT, player, target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.drone.none_free").withStyle(ChatFormatting.GRAY));
		}
	}

	static void deploy(ServerPlayer player) {
		if (out(player)) {
			return;
		}
		if (Rig.core(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.needs_core").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!Rig.spend(player, DEPLOY_COST)) {
			RigMind.empty(player);
			return;
		}
		HiveSwarm.deploy(KIT, player, PickKit.aimed(player, MISSILE_REACH));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.drone.out", Math.max(1, Rig.level(player))).withStyle(ChatFormatting.AQUA));
	}

	/** Brings them home for good (the wrist table's Stow). */
	static void stow(ServerPlayer player) {
		if (!out(player)) {
			return;
		}
		releaseItems(player);
		HiveSwarm.stow(KIT, player);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.drone.stowed").withStyle(ChatFormatting.AQUA));
	}

	/** The wrist table's orders, from {@link RigTable.Order}. */
	static void order(ServerPlayer player, int code, int arg) {
		Crew crew = crew(player);
		switch (code) {
			case RigTable.DEPLOY -> deploy(player);
			case RigTable.STOW -> stow(player);
			case RigTable.SHELL -> {
				if (Rig.core(player).isEmpty()) {
					break;
				}
				if (!out(player)) {
					deploy(player);
				}
				if (out(player)) {
					HiveSwarm.shell(KIT, player);
				}
			}
			case RigTable.MISSILE -> {
				if (out(player)) {
					use(player);
				}
			}
			case RigTable.FORMATION -> HiveSwarm.formation(KIT, player, arg);
			case RigTable.FETCH -> {
				crew.fetch = !crew.fetch;
				say(player, crew.fetch, "fetch");
				if (!crew.fetch) {
					releaseItems(player);
				}
			}
			case RigTable.MEND -> {
				crew.mend = !crew.mend;
				say(player, crew.mend, "mend");
			}
			case RigTable.PATROL -> {
				crew.patrol = !crew.patrol;
				say(player, crew.patrol, "patrol");
			}
			case RigTable.CLEAR_ROUTE -> {
				crew.route.clear();
				crew.waypoint = 0;
				crew.patrol = false;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.drone.route_clear").withStyle(ChatFormatting.GRAY));
			}
			default -> {
			}
		}
	}

	private static void say(ServerPlayer player, boolean on, String what) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.drone." + what + (on ? "_on" : "_off")).withStyle(ChatFormatting.AQUA));
	}

	/** The Mark entry: the spot under the crosshair becomes the next waypoint of the patrol. */
	static void mark(ServerPlayer player) {
		Crew crew = crew(player);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(ROUTE_REACH)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.drone.mark_miss").withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(0.4));
		if (crew.route.size() >= ROUTE_MAX) {
			crew.route.remove(0);
		}
		crew.route.add(at);
		crew.patrol = true;
		crew.waypoint = 0;
		MagicFx.ring(level, at, Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()), 0.7F, CYAN, 12);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.drone.mark", crew.route.size(), ROUTE_MAX).withStyle(ChatFormatting.AQUA));
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------

	static void tick(ServerPlayer player) {
		if (!out(player)) {
			return;
		}
		if (!player.isAlive() || player.isSpectator() || !Rig.wearsAny(player) || Rig.core(player).isEmpty()) {
			releaseItems(player);
			HiveSwarm.stow(KIT, player);
			return;
		}
		HiveSwarm.tick(KIT, player);
		Crew crew = crew(player);
		if (crew.mend && player.tickCount % MEND_EVERY == 0 && player.getHealth() < player.getMaxHealth() && !HiveSwarm.shelled(KIT, player)) {
			int near = 0;
			for (int i = 0; i < HiveSwarm.FLYERS; i++) {
				Vec3 at = HiveSwarm.freeAt(KIT, player, i);
				if (at != null && at.distanceToSqr(player.position()) <= MEND_RANGE * MEND_RANGE) {
					near++;
					player.level().sendParticles(MagicFx.spark(LENS, 0.3F), at.x, at.y - 0.2, at.z, 1, 0.1, 0.1, 0.1, 0.0);
				}
			}
			if (near > 0) {
				player.heal(near * MEND_EACH);
				Vec3 body = player.position().add(0.0, 1.0, 0.0);
				player.level().sendParticles(MagicFx.mote(CYAN, 0.4F), body.x, body.y, body.z, 3, 0.3, 0.5, 0.3, 0.0);
			}
		}
	}

	/** The drones' mastery: how many fly for this wearer now (the table shows it). */
	static int count(ServerPlayer player) {
		return Mth.clamp(Rig.level(player), 1, HiveSwarm.FLYERS);
	}

	/** Where each free drone is, for the wrist table's map. */
	static List<Vec3> positions(ServerPlayer player) {
		List<Vec3> out = new ArrayList<>();
		for (int i = 0; i < HiveSwarm.FLYERS; i++) {
			Vec3 at = HiveSwarm.freeAt(KIT, player, i);
			if (at != null) {
				out.add(at);
			}
		}
		return out;
	}

	// ---- with no foe: fetch and patrol ---------------------------------------------------------------------------------

	private static @Nullable Vec3 idle(ServerPlayer player, int i, Vec3 pos, long t) {
		Crew crew = crew(player);
		ServerLevel level = player.level();
		Vec3 home = player.getEyePosition().add(0.0, 0.3, 0.0);
		// carrying: the item rides under the drone until it is over the wearer's feet
		if (crew.carry[i] >= 0) {
			if (level.getEntity(crew.carry[i]) instanceof ItemEntity item && item.isAlive()) {
				item.setPos(pos.x, pos.y - 0.35, pos.z);
				item.setDeltaMovement(Vec3.ZERO);
				if (pos.distanceTo(home) <= DROP_NEAR) {
					drop(item);
					crew.carry[i] = -1;
					level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.3F);
				}
				return home;
			}
			crew.carry[i] = -1;
		}
		if (crew.fetch) {
			ItemEntity goal = crew.goal[i] >= 0 && level.getEntity(crew.goal[i]) instanceof ItemEntity it && it.isAlive() && !claimed(crew, i, it.getId()) ? it : null;
			if (goal == null && (t + i * 3L) % FETCH_LOOK_EVERY == 0) {
				goal = nearestItem(player, crew, i, pos);
			}
			if (goal != null) {
				crew.goal[i] = goal.getId();
				if (pos.distanceTo(goal.position()) <= PICK_NEAR) {
					crew.carry[i] = goal.getId();
					crew.goal[i] = -1;
					goal.setNoGravity(true);
					level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.3F);
					return home;
				}
				return goal.position().add(0.0, 0.4, 0.0);
			}
			crew.goal[i] = -1;
		}
		if (crew.patrol && !crew.route.isEmpty()) {
			Vec3 mark = crew.route.get(crew.waypoint % crew.route.size());
			double ang = i * Math.PI * 2.0 / HiveSwarm.FLYERS;
			Vec3 want = mark.add(Math.cos(ang) * ROUTE_SPREAD, ROUTE_HEIGHT, Math.sin(ang) * ROUTE_SPREAD);
			if (pos.distanceTo(want) <= ROUTE_ARRIVE && crew.holdUntil <= t) {
				crew.holdUntil = t + PATROL_HOLD;
				crew.waypoint = (crew.waypoint + 1) % crew.route.size();
			}
			return want;
		}
		return null;
	}

	private static boolean claimed(Crew crew, int me, int id) {
		for (int j = 0; j < crew.carry.length; j++) {
			if (j != me && (crew.carry[j] == id || crew.goal[j] == id)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable ItemEntity nearestItem(ServerPlayer player, Crew crew, int i, Vec3 pos) {
		ItemEntity best = null;
		double near = FETCH_RANGE * FETCH_RANGE;
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos).inflate(FETCH_RANGE),
			it -> it.isAlive() && it.getAge() >= FETCH_MIN_AGE && !it.hasPickUpDelay() && !it.isInLava() && !claimed(crew, i, it.getId())
				&& it.distanceToSqr(player) >= FETCH_MIN_FROM_OWNER * FETCH_MIN_FROM_OWNER)) {
			double d = item.position().distanceToSqr(pos);
			if (d < near) {
				near = d;
				best = item;
			}
		}
		return best;
	}

	private static void drop(ItemEntity item) {
		item.setNoGravity(false);
		item.setPickUpDelay(0);
	}

	/** Every carried item is let go where it is. */
	private static void releaseItems(ServerPlayer player) {
		Crew crew = CREWS.get(player.getUUID());
		if (crew == null) {
			return;
		}
		for (int i = 0; i < crew.carry.length; i++) {
			if (crew.carry[i] >= 0 && player.level().getEntity(crew.carry[i]) instanceof ItemEntity item) {
				drop(item);
			}
			crew.carry[i] = -1;
			crew.goal[i] = -1;
		}
	}

	// ---- the look ------------------------------------------------------------------------------------------------------

	/**
	 * A drone is a cyan core, a thruster ring that spins round it (two motes opposite each other), a sensor eye that glints now and
	 * then, and a glyph.
	 */
	private static void draw(ServerLevel level, Vec3 at, long t, int i) {
		level.sendParticles(MagicFx.mote(CYAN, 0.55F), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		double spin = t * 0.9 + i;
		double rx = Math.cos(spin) * 0.24, rz = Math.sin(spin) * 0.24;
		level.sendParticles(MagicFx.mote(HULL_DARK, 0.3F), at.x + rx, at.y + 0.12, at.z + rz, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(MagicFx.mote(HULL_DARK, 0.3F), at.x - rx, at.y + 0.12, at.z - rz, 1, 0.0, 0.0, 0.0, 0.0);
		if ((t + i) % 4 == 0) {
			level.sendParticles(MagicFx.spark(LENS, 0.3F), at.x, at.y - 0.1, at.z, 1, 0.04, 0.04, 0.04, 0.0);
		}
		if ((t + i * 5L) % 20 == 0) {
			level.sendParticles(MagicFx.rune(CYAN, 0.35F), at.x, at.y + 0.3, at.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** Used by the table: the player's settings as a bit set (1 fetch, 2 mend, 4 patrol). */
	static int flags(ServerPlayer player) {
		Crew crew = crew(player);
		return (crew.fetch ? 1 : 0) | (crew.mend ? 2 : 0) | (crew.patrol ? 4 : 0);
	}

	static List<Vec3> route(ServerPlayer player) {
		return new ArrayList<>(crew(player).route);
	}
}
