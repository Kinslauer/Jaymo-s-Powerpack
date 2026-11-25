package com.jaymoskingdoms;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 3 of the fighting AI (shields and blows; the ideas are Hostile Humans', none of its code): what a shield bearer sees
 * coming. Every mob with a shield in its off hand, whoever's AI it runs (MCA's brain, folk goals, the game's monsters):
 * <ul>
 * <li><b>Shots in the air</b> (sh-arc): each arrow, bolt, javelin or musket ball in flight (all {@link AbstractArrow}s, WeaponMod's
 * too) is stepped ahead up to {@link #LOOKAHEAD} ticks with its own drag and gravity. A bearer on that path, hit from the front
 * (inside its shield's blocking angle, {@link #FRONT_MARGIN} to spare) with no wall first and time for the shield to come up
 * ({@link MobShields#incoming}), raises it and holds it till the shot has landed. At most {@link #MOST_SHOTS} shots a tick.</li>
 * <li><b>Shooters in sight</b> (sh-shooter): a bearer in a fight looks every {@link #WATCH_EVERY} ticks for anyone within
 * {@link #WATCH_RANGE} with a shot ready ({@link MobShields#aiming}) whose aim is on it, its own foe or not, and that it can see
 * (its view cone, {@link NpcEyes}' light and distance, a clear line). The shield then stays up ({@link MobShields#watch}).
 * At most {@link #MOST_LOOKS} bearers look a tick.</li>
 * <li><b>A crossbowman's reload</b> (sh-busy): one with a shield keeps it up while his crossbow waits loaded (or, a folk
 * crossbowman, between his shots) and lowers it to fire ({@link MobShields#cover}, {@link MobShields#holdsFire}).</li>
 * </ul>
 * Bearers are known from their melee goal ({@link MobShields#guard}), from a shot's path, and from a look round each player every
 * {@link #FIND_EVERY} ticks; one out of a fight for {@link #FORGET_TICKS} is forgotten.
 */
public final class ShieldSense {
	/** Each shot is stepped ahead this far (ticks); slower than this (blocks a tick) it is no threat; the most shots stepped a tick. */
	static final int LOOKAHEAD = 10;
	static final double SLOWEST = 0.25;
	static final int MOST_SHOTS = 48;
	/** A body's box is grown by this (blocks) and the shot's own half-width to try the path against it. */
	static final double SLACK = 0.2;
	/** The hit must come at least this far (degrees) inside the shield's blocking angle. */
	static final double FRONT_MARGIN = 15.0;
	/** The shield's own delay and this many ticks more must fit before the shot lands; it is held this long past it. */
	static final int RISE_SPARE = 1, AFTER_HIT = 3;
	/** A shield's blocking angle (degrees each side) when its item says none. */
	static final double DEFAULT_BLOCK_ANGLE = 90.0;
	/** Shooters looked for this far (blocks) round a bearer, every this many ticks, by at most this many bearers a tick. */
	static final double WATCH_RANGE = 28.0;
	static final int WATCH_EVERY = 4, MOST_LOOKS = 8;
	/** An aim within this (degrees) of the bearer's body is on it; seen, the shield stays up this long past the look (ticks). */
	static final double AIM_CONE = 12.0;
	static final int WATCH_HOLD = 8;
	/** New bearers are looked for round each player this often (ticks), this far (blocks); one out of a fight this long is let go. */
	static final int FIND_EVERY = 20;
	static final double FIND_RANGE = 32.0;
	static final int FORGET_TICKS = 200;

	/** Shots that may be in the air. */
	private static final Set<AbstractArrow> FLYING = Collections.newSetFromMap(new WeakHashMap<>());
	/** Shield bearers to watch for → when last seen in a fight (game time). */
	private static final Map<Mob, Long> BEARERS = new WeakHashMap<>();
	private static int shotCursor, lookCursor;

	private ShieldSense() {
	}

	/** From {@link MobShields#register}. */
	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow shot) {
				FLYING.add(shot);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow shot) {
				FLYING.remove(shot);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ShieldSense::tick);
	}

	/** A bearer in a fight ({@link MobShields#guard}): watched. */
	static void seen(Mob mob) {
		BEARERS.put(mob, mob.level().getGameTime());
	}

	/** A shield in the off hand, or (st-dodge) one on the back that comes to hand when it's shot at ({@link DualBlades#threat}). */
	private static boolean bearer(Mob mob) {
		return mob.isAlive() && !mob.isRemoved() && (mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) || DualBlades.onBack(mob));
	}

	private static void tick(MinecraftServer server) {
		if (!FLYING.isEmpty()) {
			shots();
		}
		if (server.getTickCount() % FIND_EVERY == 0) {
			find(server);
		}
		if (!BEARERS.isEmpty()) {
			look(server.getTickCount());
		}
	}

	// ---- sh-arc: shots in the air ----------------------------------------------------------------------------------------

	private static void shots() {
		AbstractArrow[] all = FLYING.toArray(new AbstractArrow[0]);
		int n = Math.min(all.length, MOST_SHOTS);
		int from = all.length > MOST_SHOTS ? (shotCursor = (shotCursor + MOST_SHOTS) % all.length) : 0;
		for (int i = 0; i < n; i++) {
			step(all[(from + i) % all.length]);
		}
	}

	/** One shot stepped ahead: whoever with a shield is on its path and can take it on the shield raises it. */
	private static void step(AbstractArrow shot) {
		if (shot.isRemoved() || !(shot.level() instanceof ServerLevel level)) {
			FLYING.remove(shot);
			return;
		}
		Vec3 v = shot.getDeltaMovement();
		if (v.lengthSqr() < 1.0E-4 && shot.tickCount > 2) {
			// (stuck in the ground or a wall: done)
			FLYING.remove(shot);
			return;
		}
		// (a shield doesn't stop a piercing bolt; a loyal trident flying home is no shot)
		if (v.lengthSqr() < SLOWEST * SLOWEST || shot.isNoPhysics() || shot.getPierceLevel() > 0) {
			return;
		}
		double drag = drag(shot), gravity = shot.getGravity();
		Vec3[] path = new Vec3[LOOKAHEAD + 1];
		path[0] = shot.position();
		double minX = path[0].x, minY = path[0].y, minZ = path[0].z, maxX = minX, maxY = minY, maxZ = minZ;
		for (int t = 1; t <= LOOKAHEAD; t++) {
			// (as the shot ticks: it moves, then slows, then falls)
			path[t] = path[t - 1].add(v);
			v = v.scale(drag).add(0.0, -gravity, 0.0);
			minX = Math.min(minX, path[t].x);
			minY = Math.min(minY, path[t].y);
			minZ = Math.min(minZ, path[t].z);
			maxX = Math.max(maxX, path[t].x);
			maxY = Math.max(maxY, path[t].y);
			maxZ = Math.max(maxZ, path[t].z);
		}
		Entity owner = shot.getOwner();
		AABB swept = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1.0);
		List<Mob> onPath = level.getEntitiesOfClass(Mob.class, swept, m -> m != owner && bearer(m) && !Formations.spares(owner, m));
		for (Mob m : onPath) {
			threat(level, shot, m, path);
		}
	}

	/** The air's (or water's) drag on {@code shot} each tick, as its own tick uses it. */
	private static double drag(AbstractArrow shot) {
		if (Javelins.WEAPONMOD_LOADED) {
			Float own = WeaponModFlight.drag(shot);
			if (own != null) {
				return own;
			}
		}
		return shot.isInWater() && !(shot instanceof ThrownTrident) ? 0.6 : 0.99;
	}

	/** Whether {@code shot}, along {@code path}, lands on {@code bearer}'s front, and if it does, the shield up in time. */
	private static void threat(ServerLevel level, AbstractArrow shot, Mob bearer, Vec3[] path) {
		AABB body = bearer.getBoundingBox().inflate(shot.getBbWidth() / 2.0 + SLACK);
		if (body.contains(path[0])) {
			return;
		}
		for (int t = 1; t < path.length; t++) {
			Optional<Vec3> hit = body.clip(path[t - 1], path[t]);
			if (hit.isEmpty()) {
				continue;
			}
			double segment = path[t - 1].distanceTo(path[t]);
			double ticks = t - 1 + (segment < 1.0E-6 ? 0.0 : path[t - 1].distanceTo(hit.get()) / segment);
			// a wall first, and it never gets there
			for (int k = 1; k <= t; k++) {
				Vec3 to = k == t ? hit.get() : path[k];
				if (level.clip(new ClipContext(path[k - 1], to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shot)).getType() != HitResult.Type.MISS) {
					return;
				}
			}
			if (front(bearer, path[t - 1], FRONT_MARGIN)) {
				// (a shield on the back starts to come to hand: of no use for this shot)
				DualBlades.threat(bearer);
				MobShields.incoming(bearer, ticks, RISE_SPARE, AFTER_HIT);
			}
			return;
		}
	}

	/** Whether something at {@code from} is inside {@code bearer}'s shield's blocking angle, {@code margin} degrees to spare (vanilla's test). */
	static boolean front(Mob bearer, Vec3 from, double margin) {
		Vec3 to = new Vec3(from.x - bearer.getX(), 0.0, from.z - bearer.getZ());
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		double angle = Math.toDegrees(Math.acos(Math.clamp(to.normalize().dot(Vec3.directionFromRotation(0.0F, bearer.getYHeadRot())), -1.0, 1.0)));
		return angle <= blockAngle(bearer.getOffhandItem()) - margin;
	}

	/** The widest angle (degrees each side) {@code shield} blocks over. */
	private static double blockAngle(ItemStack shield) {
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null || blocks.damageReductions().isEmpty()) {
			return DEFAULT_BLOCK_ANGLE;
		}
		double widest = 0.0;
		for (BlocksAttacks.DamageReduction reduction : blocks.damageReductions()) {
			widest = Math.max(widest, reduction.horizontalBlockingAngle());
		}
		return widest;
	}

	// ---- sh-shooter: shooters in sight -----------------------------------------------------------------------------------

	/** Bearers in a fight near each player, not yet known. */
	private static void find(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FIND_RANGE), m -> !BEARERS.containsKey(m) && bearer(m))) {
				if (Rulers.target(m) != null) {
					BEARERS.put(m, level.getGameTime());
				}
			}
		}
	}

	private static void look(int tick) {
		Mob[] all = BEARERS.keySet().toArray(new Mob[0]);
		int looks = 0;
		int from = lookCursor = (lookCursor + 1) % Math.max(1, all.length);
		for (int i = 0; i < all.length && looks < MOST_LOOKS; i++) {
			Mob m = all[(from + i) % all.length];
			if (m == null) {
				continue;
			}
			if (!bearer(m)) {
				BEARERS.remove(m);
				continue;
			}
			if ((m.getId() + tick) % WATCH_EVERY != 0 || !(m.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			LivingEntity foe = Rulers.target(m);
			if (foe != null && foe.isAlive()) {
				BEARERS.put(m, now);
			} else {
				Long last = BEARERS.get(m);
				if (last == null || now - last > FORGET_TICKS) {
					BEARERS.remove(m);
				}
				continue;
			}
			if (DualBlades.onBack(m)) {
				// (st-dodge) a shield on the back: someone aiming at it brings it to hand
				looks++;
				if (!Downed.isDowned(m) && shooter(level, m) != null) {
					DualBlades.threat(m);
				}
				continue;
			}
			if (!MobShields.canRaise(m)) {
				continue;
			}
			looks++;
			// a crossbowman reloads behind his shield: his crossbow loaded and waiting, or (a folk crossbowman) between shots
			ItemStack main = m.getMainHandItem();
			if (main.getItem() instanceof CrossbowItem && !Downed.isDowned(foe)
				&& (CrossbowItem.isCharged(main) && !m.isUsingItem() || m instanceof Folk f && f.shooting())) {
				MobShields.cover(m, WATCH_EVERY + WATCH_HOLD);
			}
			LivingEntity shooter = shooter(level, m);
			if (shooter != null) {
				DualBlades.threat(m);
				MobShields.watch(m, WATCH_EVERY + WATCH_HOLD);
				if (m.distanceToSqr(foe) > MobShields.GUARD_RANGE * MobShields.GUARD_RANGE) {
					// (its own foe not at hand, it turns its shield square to the one aiming)
					m.getLookControl().setLookAt(shooter, 30.0F, 30.0F);
				}
			}
		}
	}

	/** Someone {@code bearer} can see who has a shot ready and aimed at it, or null. */
	private static @Nullable LivingEntity shooter(ServerLevel level, Mob bearer) {
		String side = Comrades.side(bearer);
		Vec3 body = bearer.position().add(0.0, bearer.getBbHeight() * 0.6, 0.0);
		double cos = Math.cos(Math.toRadians(AIM_CONE));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, bearer.getBoundingBox().inflate(WATCH_RANGE),
			e -> e != bearer && e.isAlive() && !e.isSpectator() && MobShields.aiming(e, bearer))) {
			if (Downed.isDowned(e)) {
				continue;
			}
			boolean onIt = e instanceof Mob shooter && Rulers.target(shooter) == bearer;
			if (!onIt) {
				Vec3 to = body.subtract(e.getEyePosition());
				if (to.lengthSqr() < 1.0E-4 || side.equals(Comrades.side(e)) || e.getViewVector(1.0F).dot(to.normalize()) < cos) {
					continue;
				}
			}
			if (front(bearer, e.position(), 0.0) && Senses.inCone(bearer, e) && NpcEyes.sees(bearer, e) && bearer.getSensing().hasLineOfSight(e)) {
				return e;
			}
		}
		return null;
	}
}
