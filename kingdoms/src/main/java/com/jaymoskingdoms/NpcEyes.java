package com.jaymoskingdoms;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * NPC eyes (docs/HostileHumans-Plan.md, patch 4): what {@link Senses} gives monsters noticing players, given to everyone who
 * fights.
 * <ul>
 * <li><b>Between NPCs</b> ({@link #factor}, from {@code LivingEntityVisibilityMixin}; {@link #sees}, from
 * {@code ComradesSightMixin}): when an NPC looks for an NPC to fight, the same light, sneaking, standing still, view cone,
 * height and distraction rules apply as for a player, and one already in a fight with it, in sight, is seen at full range.
 * Goal-based mobs (the folk, illagers, zombies) and MCA's guards (whose sensor tests {@code TargetingConditions} too) all ask
 * {@code getVisibilityPercent}, so this one hook covers both; the fighters' own pick of a foe ({@code Comrades#candidates}) is
 * filtered by {@link #sees}.</li>
 * <li><b>Any part of the body</b> ({@link #bodyVisible}, from {@code LivingEntitySightMixin}): a sight check that fails to the
 * eyes tries the chest, the shoulders and the feet, within {@link #BODY_RANGE} blocks and {@link #BODY_RAYS_PER_MOB} rays a
 * tick for each mob. That is for noticing: a shot is aimed only along a clear line ({@link #clearShot}, {@link #aimAtWhatShows}).</li>
 * <li><b>Feet</b> ({@link #atFeet}, used by {@link Senses#inCone}): something low in front of the body within {@link #FEET_RANGE}
 * blocks at foot height is in view though far below the look.</li>
 * <li><b>A blow from behind</b> ({@link #struck}, {@link Senses.TurnGoal}): a fighter hit by a foe it didn't see stops and turns
 * to it for up to {@link #TURN_TICKS}; one it still can't see after is let go ({@link Senses#lose}).</li>
 * <li><b>A sighting passed on</b> ({@link #share}): a fighter who sees its foe tells up to {@link #SHARE_MAX} fighters of its
 * side within {@link #SHARE_RANGE} blocks; they know where it is for {@link #SHARE_TICKS} ({@link Senses#knows}), and the
 * idle ones go to look; they never pass it on.</li>
 * <li><b>The dev view</b> ({@link View}): a spectator who is an operator (or in a dev game) and looks at an NPC is sent what it
 * sees and why it acts, drawn on their own screen by {@code SensesClient}. No command, nothing for players.</li>
 * </ul>
 */
public final class NpcEyes {
	// ---- tuning ----
	/**
	 * (ey-body) How far (blocks) the body's other points are tried, and the most rays a tick each mob may cast for them (its own
	 * allowance, so what one mob sees never hangs on which mobs ticked before it).
	 */
	static final double BODY_RANGE = 40.0;
	static final int BODY_RAYS_PER_MOB = 12;
	/** A shot is turned to the part of the body that shows only if it starts this close to its shooter's eye (blocks, squared), so a shot loaded from disk is left be. */
	static final double NEAR_EYE_SQR = 2.25;
	/** (ey-body) Chest, feet and shoulders, as shares of the body's height; the shoulders sit out at the body's edge (half its width). */
	static final double CHEST = 0.6, FEET = 0.1, SHOULDER = 0.82;
	/** (ey-feet) Within this many blocks, a foe whose feet are no higher than {@link #FEET_LEVEL} over the looker's feet and whose eyes are under {@link #LOW_SHARE} of its height, in front of its body. */
	static final double FEET_RANGE = 3.0, FEET_LEVEL = 0.6, LOW_SHARE = 0.5;
	/** (ey-behind) The most ticks (a second) it stands turning to a blow, and how fast its body comes round after the head. */
	static final int TURN_TICKS = 20;
	static final float TURN_BODY = 0.3F;
	/** (ey-share) How often (ticks) a fighter who sees its foe tells, how far (blocks), how many friends, and how long they know (10 s). */
	static final int SHARE_EVERY = 20, SHARE_MAX = 3, SHARE_TICKS = 200;
	static final double SHARE_RANGE = 12.0;
	/** (ey-dev) How often (ticks) the view is sent, and how far (blocks) a spectator's look reaches. */
	public static final int DEV_EVERY = 5;
	static final double DEV_RANGE = 48.0;
	/** A dev game (the Gradle run configs set it, as for {@code DevCommands}) shows the view to any spectator. */
	private static final String DEV_PROPERTY = "jaymoskingdoms.devCommands";

	/** Why a mob is doing what it does, for the dev view. */
	public static final int IDLE = 0, FIGHTING = 1, BLIND = 2, SEARCHING = 3, HEEDING = 4, TURNING = 5, TOLD = 6, RETURNING = 7;

	/** Server → a spectating operator: the NPC they look at. {@code mob} -1: none. */
	public record View(int mob, int foe, int why, float cos, float range, boolean sees, int blind, @Nullable Vec3 seen, @Nullable Vec3 spot,
		@Nullable Vec3 heard, @Nullable Vec3 told) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(JaymosKingdoms.id("eyes_view"));
		public static final View NONE = new View(-1, -1, IDLE, 0.0F, 0.0F, false, 0, null, null, null, null);
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.mob + 1);
				if (p.mob < 0) {
					return;
				}
				buf.writeVarInt(p.foe + 1);
				buf.writeByte(p.why);
				buf.writeFloat(p.cos);
				buf.writeFloat(p.range);
				buf.writeVarInt(p.blind);
				buf.writeByte((p.sees ? 1 : 0) | (p.seen != null ? 2 : 0) | (p.spot != null ? 4 : 0) | (p.heard != null ? 8 : 0) | (p.told != null ? 16 : 0));
				for (Vec3 at : new Vec3[] {p.seen, p.spot, p.heard, p.told}) {
					if (at != null) {
						buf.writeDouble(at.x);
						buf.writeDouble(at.y);
						buf.writeDouble(at.z);
					}
				}
			},
			buf -> {
				int mob = buf.readVarInt() - 1;
				if (mob < 0) {
					return NONE;
				}
				int foe = buf.readVarInt() - 1, why = buf.readByte();
				float cos = buf.readFloat(), range = buf.readFloat();
				int blind = buf.readVarInt(), flags = buf.readByte();
				Vec3[] at = new Vec3[4];
				for (int i = 0; i < at.length; i++) {
					if ((flags & (2 << i)) != 0) {
						at[i] = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
					}
				}
				return new View(mob, foe, why, cos, range, (flags & 1) != 0, blind, at[0], at[1], at[2], at[3]);
			});

		@Override
		public Type<View> type() {
			return TYPE;
		}
	}

	/** Spectator → the mob they were last sent (so the end of a look is sent once). */
	private static final Map<UUID, Integer> VIEWING = new HashMap<>();
	/** Each mob's body rays: the tick they were counted in, and how many it has cast in it. */
	private static final Map<Mob, long[]> RAYS = new WeakHashMap<>();

	private NpcEyes() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> struck(entity, source));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile shot && shot.tickCount == 0) {
				aimAtWhatShows(shot);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(NpcEyes::tickView);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIEWING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> VIEWING.clear());
	}

	// ---- ey-npc: NPCs seen by NPCs ---------------------------------------------------------------------------------------------

	/**
	 * What {@code looker}'s range for noticing {@code target} is multiplied by, when {@code target} is one of the people of the
	 * world (a humanoid: {@link Senses#humanoid}) and {@code looker} a mob that sees as monsters and people do: the rules of
	 * {@link Senses#factor} for a player, less what is only a player's (perks, hoods, stench). 1 for anyone else. A foe it is
	 * already in a fight with and has in sight, or has been told of ({@link Senses#knows}), is seen at full range.
	 */
	static double factor(LivingEntity target, ServerLevel level, Mob looker) {
		if (!(target instanceof Mob foe) || !Senses.humanoid(foe) || !(Senses.sighted(looker) || Senses.humanoid(looker))) {
			return 1.0;
		}
		// (its own foe, or one fighting it)
		if (Senses.knows(looker, foe) || (Senses.foeOf(looker) == foe || Senses.foeOf(foe) == looker) && looker.hasLineOfSight(foe)) {
			return 1.0;
		}
		double dist = target.distanceTo(looker);
		double light = (NightLights.npc(looker) ? NightLights.light(level, looker, target, dist) : level.getMaxLocalRawBrightness(BlockPos.containing(target.getEyePosition()))) / 15.0;
		double seen = looker.getType().builtInRegistryHolder().is(Senses.DARKVISION) ? Math.max(light, Senses.DARKVISION_LIGHT) : light;
		double f = Senses.LIGHT_MIN + (Senses.LIGHT_MAX - Senses.LIGHT_MIN) * seen;
		if (target.isCrouching()) {
			f *= Senses.SNEAK;
		}
		boolean moving = target.getDeltaMovement().horizontalDistanceSqr() > Senses.MOVING_SQR;
		if (!moving) {
			f *= Senses.STILL;
		}
		if (!Senses.close(looker, target)) {
			if (!Senses.inCone(looker, target) || Senses.steeplyAbove(looker, target)) {
				f *= Senses.humanoid(looker) ? Senses.HUMANOID_BEHIND : Senses.BEHIND;
			}
			if (target.getEyeY() - looker.getEyeY() > Senses.ABOVE_FROM) {
				f *= Senses.ABOVE;
			}
		}
		if (target.isOnFire() || target.hasEffect(MobEffects.GLOWING)) {
			f *= Senses.LIT;
		}
		double least = Senses.boss(looker) ? Senses.BOSS_SIGHT_MIN : Senses.MIN_FACTOR;
		f = Math.max(least, f);
		if (!target.isCrouching() && moving && looker.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)) {
			// (walking about close by is heard, whatever the light or the way it faces)
			double range = looker.getAttributeValue(Attributes.FOLLOW_RANGE);
			if (range > 0.0) {
				f = Math.max(f, Senses.HEARD / range);
			}
		}
		if (Senses.distracted(looker, target)) {
			f = Math.max(least, f * Senses.DISTRACTED);
		}
		return f;
	}

	/**
	 * Whether {@code looker} makes out {@code foe} as one to pick in a fight (from {@code Comrades#candidates}): the one it is
	 * already after, or one inside its follow range as {@code getVisibilityPercent} (so {@link #factor}, or a player's, and
	 * vanilla's) leaves it.
	 */
	public static boolean sees(Mob looker, LivingEntity foe) {
		if (Senses.foeOf(looker) == foe || !(looker.level() instanceof ServerLevel level) || !looker.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)) {
			return true;
		}
		double reach = Math.max(looker.getAttributeValue(Attributes.FOLLOW_RANGE) * foe.getVisibilityPercent(level, looker), 2.0);
		return looker.distanceToSqr(foe) <= reach * reach;
	}

	// ---- ey-body: sight from any part of the body -----------------------------------------------------------------------------

	/**
	 * (from {@code LivingEntitySightMixin}, after the line from {@code looker}'s eyes to {@code target}'s failed) whether any other
	 * part of the body shows: the chest, a shoulder (so one round a corner is seen), the feet. Only for mobs that see as people
	 * do, a few blocks off, and while this tick's ray allowance lasts; a body in the dark is no more seen
	 * ({@link NightLights#makesOut}).
	 */
	public static boolean bodyVisible(LivingEntity looker, Entity target) {
		if (!(looker instanceof Mob mob) || !(target instanceof LivingEntity foe) || !(mob.level() instanceof ServerLevel level) || foe.level() != mob.level()
			|| mob.distanceToSqr(foe) > BODY_RANGE * BODY_RANGE || !(Senses.sighted(mob) || Senses.humanoid(mob))) {
			return false;
		}
		long now = level.getGameTime();
		long[] rays = RAYS.computeIfAbsent(mob, m -> new long[2]);
		if (rays[0] != now) {
			rays[0] = now;
			rays[1] = 0;
		}
		Vec3 from = eye(mob);
		for (Vec3 to : points(from, foe)) {
			if (rays[1] >= BODY_RAYS_PER_MOB) {
				return false;
			}
			rays[1]++;
			if (clear(mob, from, to)) {
				return NightLights.makesOut(mob, foe);
			}
		}
		return false;
	}

	/** The chest, both shoulders (out at the body's edge, as seen from {@code from}) and the feet of {@code foe}. */
	private static Vec3[] points(Vec3 from, LivingEntity foe) {
		double dx = foe.getX() - from.x, dz = foe.getZ() - from.z, len = Math.sqrt(dx * dx + dz * dz);
		double side = foe.getBbWidth() * 0.5;
		double ax = len < 1.0E-4 ? 0.0 : -dz / len * side, az = len < 1.0E-4 ? 0.0 : dx / len * side;
		double h = foe.getBbHeight();
		return new Vec3[] {
			new Vec3(foe.getX(), foe.getY() + h * CHEST, foe.getZ()),
			new Vec3(foe.getX() + ax, foe.getY() + h * SHOULDER, foe.getZ() + az),
			new Vec3(foe.getX() - ax, foe.getY() + h * SHOULDER, foe.getZ() - az),
			new Vec3(foe.getX(), foe.getY() + h * FEET, foe.getZ())
		};
	}

	/** {@code mob}'s eye, out where it has leaned to if it leans. */
	private static Vec3 eye(Mob mob) {
		Vec3 eye = mob.getEyePosition();
		return Lean.dir(mob) != 0 ? eye.add(Lean.shift(mob, 1.0)) : eye;
	}

	private static boolean clear(Mob mob, Vec3 from, Vec3 to) {
		return mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
	}

	/**
	 * Whether {@code shooter} has a clear line, from its eye (leaned out, if it leans), to where a shot is aimed: {@code target}'s head
	 * or the middle of its body. Body-point sight ({@link #bodyVisible}) is for noticing: a shoulder showing round a wall is seen, not a
	 * mark to loose at, so a shooter without this line goes to find one ({@link RangedTactics}) and throws nothing ({@link WeaponSense}).
	 */
	public static boolean clearShot(Mob shooter, LivingEntity target) {
		if (target.level() != shooter.level()) {
			return false;
		}
		Vec3 from = eye(shooter);
		return clear(shooter, from, target.getBoundingBox().getCenter()) || clear(shooter, from, target.getEyePosition());
	}

	/**
	 * A shot an NPC has just loosed at its foe (an arrow, a bolt, a javelin, a ball) when the line to the foe's middle and head is
	 * blocked and only another part of the body shows (it was seen by {@link #bodyVisible}): turned, its speed and arc kept, toward the
	 * part that shows, so it isn't loosed into the wall. Leaning shooters' shots start from the leaned eye ({@link Lean}).
	 */
	private static void aimAtWhatShows(Projectile shot) {
		if (!(shot.getOwner() instanceof Mob shooter) || !(shot.level() instanceof ServerLevel) || !(Senses.sighted(shooter) || Senses.humanoid(shooter))
			|| shot.position().distanceToSqr(shooter.getEyePosition()) > NEAR_EYE_SQR) {
			return;
		}
		LivingEntity foe = Comrades.target(shooter);
		if (foe == null || foe.level() != shot.level() || shooter.distanceToSqr(foe) > BODY_RANGE * BODY_RANGE) {
			return;
		}
		// (where it leaves from, whether or not the lean has moved it out yet)
		Vec3 from = new Vec3(shooter.getX(), shot.getY(), shooter.getZ()).add(Lean.shift(shooter, 1.0));
		Vec3 middle = foe.getBoundingBox().getCenter();
		if (clear(shooter, from, middle) || clear(shooter, from, foe.getEyePosition())) {
			return;
		}
		Vec3 v = shot.getDeltaMovement();
		double speed = v.length();
		if (speed < 1.0E-4) {
			return;
		}
		for (Vec3 to : points(from, foe)) {
			if (clear(shooter, from, to)) {
				Vec3 turn = to.subtract(from).normalize().subtract(middle.subtract(from).normalize());
				shot.setDeltaMovement(v.scale(1.0 / speed).add(turn).normalize().scale(speed));
				return;
			}
		}
	}

	// ---- ey-feet -----------------------------------------------------------------------------------------------------------------

	/**
	 * {@code target} is something low (baby zombies, spiders, crawlers) at {@code looker}'s feet, in front of its body, within
	 * {@link #FEET_RANGE}: in view though far below the look.
	 */
	static boolean atFeet(LivingEntity looker, LivingEntity target) {
		double dy = target.getY() - looker.getY();
		if (dy > FEET_LEVEL || target.getEyeY() > looker.getY() + looker.getBbHeight() * LOW_SHARE) {
			return false;
		}
		double dx = target.getX() - looker.getX(), dz = target.getZ() - looker.getZ();
		if (dx * dx + dz * dz > FEET_RANGE * FEET_RANGE) {
			return false;
		}
		double yaw = Math.toRadians(looker.yBodyRot);
		return -Math.sin(yaw) * dx + Math.cos(yaw) * dz > 0.0;
	}

	// ---- ey-behind -----------------------------------------------------------------------------------------------------------------

	/**
	 * {@code entity} was hurt by {@code source}: a fighter hit by a foe it didn't see (not in its view, or past a wall, and not
	 * the one it is after) stops and turns to where the blow came from ({@link Senses.TurnGoal}). One already turning isn't
	 * held longer, so blows can't lock it.
	 */
	private static void struck(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Mob mob) || !mob.isAlive() || !(source.getEntity() instanceof LivingEntity striker) || striker == mob) {
			return;
		}
		Senses.Track track = Senses.track(mob);
		if (track == null || track.turnLeft > 0 || Downed.isDowned(mob) || Downed.isDowned(striker)) {
			return;
		}
		if (mob.hasLineOfSight(striker) && (Senses.inCone(mob, striker) || Senses.foeOf(mob) == striker)) {
			return;
		}
		track.turnLeft = TURN_TICKS;
		track.turnAt = striker.getEyePosition();
		track.turnWho = striker.getUUID();
	}

	// ---- ey-share ----------------------------------------------------------------------------------------------------------------

	/**
	 * {@code mob} has {@code foe} in sight (from {@link Senses.WatchGoal}): every {@link #SHARE_EVERY} ticks it tells up to
	 * {@link #SHARE_MAX} fighters of its side ({@link #fighter}) within {@link #SHARE_RANGE} blocks, the nearest first. They know where the foe is
	 * for {@link #SHARE_TICKS} (a friend who knows only that never tells on), and one that is after no one goes to look.
	 */
	static void share(Mob mob, LivingEntity foe) {
		if (mob.tickCount % SHARE_EVERY != Math.floorMod(mob.getId(), SHARE_EVERY / 2) * 2 || Downed.isDowned(foe) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		String side = Comrades.side(mob);
		List<Mob> friends = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(SHARE_RANGE),
			f -> f != mob && f.isAlive() && f.distanceToSqr(mob) <= SHARE_RANGE * SHARE_RANGE && Senses.track(f) != null && Senses.foeOf(f) != foe && !Downed.isDowned(f)
				&& fighter(f) && Comrades.side(f).equals(side));
		friends.sort(Comparator.comparingDouble(f -> f.distanceToSqr(mob)));
		long until = level.getGameTime() + SHARE_TICKS;
		UUID who = foe instanceof ServerPlayer player ? player.getUUID() : null;
		for (int i = 0; i < Math.min(SHARE_MAX, friends.size()); i++) {
			Mob friend = friends.get(i);
			Senses.Track track = Senses.track(friend);
			if (track == null) {
				continue;
			}
			track.toldWho = foe.getUUID();
			track.toldAt = foe.position();
			track.toldUntil = until;
			if (Senses.foeOf(friend) == null && !Comrades.busy(friend)) {
				Senses.lure(friend, foe.position(), who, Senses.LOST_LURE);
			}
		}
	}

	/**
	 * Whether {@code f} is one of its side's fighters, to be told of a foe: a grown fighter of the folk, a guard, a camp's or a ship's
	 * fighter, or a villager with a weapon in hand; never a child, nor a farmer or a townsman with nothing to fight with. Monsters and
	 * the like are all fighters.
	 */
	private static boolean fighter(Mob f) {
		if (f.isBaby()) {
			return false;
		}
		if (f instanceof Folk folk) {
			return folk.combatant();
		}
		if (f instanceof net.minecraft.world.entity.npc.villager.Villager) {
			return Downed.isGuard(f) || Barbarians.isCampVillager(f) || f instanceof Worker w && Pirates.isPirate(w.role()) || Rearm.isWeapon(f.getMainHandItem());
		}
		return true;
	}

	// ---- ey-dev ------------------------------------------------------------------------------------------------------------------

	/** A spectator who may see it: an operator, or anyone in a dev game. */
	private static boolean mayView(ServerPlayer player) {
		return player.isSpectator() && (Boolean.getBoolean(DEV_PROPERTY) || Commands.LEVEL_GAMEMASTERS.check(player.permissions()));
	}

	private static void tickView(MinecraftServer server) {
		if (server.getTickCount() % DEV_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Mob looked = mayView(player) ? lookedAt(player) : null;
			if (looked == null) {
				if (VIEWING.remove(player.getUUID()) != null) {
					ServerPlayNetworking.send(player, View.NONE);
				}
				continue;
			}
			VIEWING.put(player.getUUID(), looked.getId());
			ServerPlayNetworking.send(player, view(looked));
		}
	}

	/** The NPC {@code player} is spectating through, or looking at (through blocks, as a spectator does). */
	private static @Nullable Mob lookedAt(ServerPlayer player) {
		if (player.getCamera() instanceof Mob seen && seen.isAlive()) {
			return seen;
		}
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F), end = eye.add(look.scale(DEV_RANGE));
		AABB box = player.getBoundingBox().expandTowards(look.scale(DEV_RANGE)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof Mob m && m.isAlive() && (Senses.track(m) != null || Senses.humanoid(m)), DEV_RANGE * DEV_RANGE);
		return hit != null && hit.getEntity() instanceof Mob mob ? mob : null;
	}

	/** What {@code mob} sees and why it acts as it does. */
	private static View view(Mob mob) {
		Senses.Track track = Senses.track(mob);
		LivingEntity foe = Senses.foeOf(mob);
		long now = mob.level().getGameTime();
		boolean told = track != null && track.toldWho != null && now < track.toldUntil;
		boolean sees = foe != null && mob.hasLineOfSight(foe);
		int why = IDLE;
		if (track != null && track.turnLeft > 0) {
			why = TURNING;
		} else if (track != null && track.returning) {
			why = RETURNING;
		} else if (track != null && track.searching) {
			why = told && track.who != null && track.who.equals(track.toldWho) ? TOLD : SEARCHING;
		} else if (track != null && track.heedLeft > 0) {
			why = HEEDING;
		} else if (foe != null) {
			why = sees ? FIGHTING : BLIND;
		}
		float range = (float) (mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? mob.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0);
		return new View(mob.getId(), foe == null ? -1 : foe.getId(), why, (float) (Senses.boss(mob) ? Senses.BOSS_VIEW_COS : Senses.VIEW_COS), range, sees,
			track == null ? 0 : track.blind, track != null && (foe != null || track.searching) ? track.seen : null,
			track != null && (track.searching || track.returning) ? (track.returning ? track.home : track.spot) : null,
			track != null && track.heedLeft > 0 ? track.heedAt : null, told ? track.toldAt : null);
	}
}
