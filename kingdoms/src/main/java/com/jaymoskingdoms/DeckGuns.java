package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.ShipCannonBlock;
import com.jaymoskingdoms.sailing.ShotKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A long ship's guns, served by her pillagers (Andy, 2026-11-26: "if you cant get the pillagers to man that, cannons and
 * all, then give it to the pirates"). The guns are set in her rails when she's made ({@link SeaSites#guns}), each with a
 * barrel of powder and round shot behind its breech. Wherever an illager stands by a gun (Moog's own crew, or the camp
 * that has moved in aboard her), he lays it on his foe, or on the nearest player in its arc, loads it out of that barrel
 * and fires: one shot every {@link #RELOAD} ticks or so. Only real powder and shot are fired (Andy: no free projectiles),
 * so a gun whose barrel is spent is quiet. A player is fired on only once the land round her has opened up for him (her
 * hold's tier, {@link IllagerSites#PLANS}), as her people move in then; before that only her crew's crossbows speak.
 * She lies where Moog's mod floated her: her guns fire from where she is, and she doesn't sail.
 */
final class DeckGuns {
	/** How often (ticks) her guns are looked at, and how near a player must be for them to be. */
	static final int LOOK = 20;
	static final double PLAYER_NEAR = Corsairs.GUN_RANGE + 8.0;
	/** A gunner stands this near his gun (blocks). */
	static final double GUNNER_REACH = 4.0;
	/** Between a gun's shots (ticks), plus up to this more. */
	static final int RELOAD = 200, JITTER = 60;
	/** A gun reaches this far off its line (a cosine of the angle: about 50 degrees either way). */
	static final double ARC = 0.64;

	/** Gun (packed) → when it may fire next. Not saved. */
	private static final Map<Long, Long> NEXT = new HashMap<>();
	/** The rigged boats that have guns (rebuilt when the record changes). */
	private static @Nullable Map<String, SeaSites.Rig> seen;
	private static List<List<Long>> gunned = List.of();

	private DeckGuns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DeckGuns::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEXT.clear();
			seen = null;
			gunned = List.of();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % LOOK != 0) {
			return;
		}
		Map<String, SeaSites.Rig> rigs = SeaSites.rigs(server);
		if (rigs != seen) {
			List<List<Long>> next = new ArrayList<>();
			for (SeaSites.Rig r : rigs.values()) {
				if (!r.guns().isEmpty()) {
					next.add(r.guns());
				}
			}
			gunned = next;
			seen = rigs;
		}
		WorldTiers.Tier tier = IllagerSites.PLANS.get(SeaSites.LONG_SHIP).tier();
		for (List<Long> guns : gunned) {
			BlockPos first = BlockPos.of(guns.get(0));
			if (!Loaded.made(level, first) || !level.isPositionEntityTicking(first) || !Raids.playerNear(level, first, PLAYER_NEAR)) {
				continue;
			}
			for (long g : guns) {
				serve(level, BlockPos.of(g), tier, now);
			}
		}
	}

	/** The gun at {@code pos}, if a gunner stands by it and a foe is in its arc: laid, loaded (out of its barrel) and fired. */
	private static void serve(ServerLevel level, BlockPos pos, WorldTiers.Tier tier, long now) {
		long key = pos.asLong();
		Long next = NEXT.get(key);
		if (next != null && now < next) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ShipCannonBlock)) {
			return;
		}
		Direction facing = state.getValue(ShipCannonBlock.FACING);
		Vec3 centre = Vec3.atCenterOf(pos);
		Vec3 muzzle = centre.add(facing.getStepX() * ShipCannonBlock.MUZZLE, 0.15, facing.getStepZ() * ShipCannonBlock.MUZZLE);
		Mob gunner = gunner(level, centre);
		if (gunner == null) {
			return;
		}
		LivingEntity foe = foe(level, gunner, muzzle, facing, tier);
		if (foe == null) {
			return;
		}
		ShotKind kind = ShipCannonBlock.ready(level, pos);
		RandomSource random = level.getRandom();
		if (kind == null) {
			// (run back, or nothing left in the barrel: looked at again a reload on)
			NEXT.put(key, now + RELOAD);
			return;
		}
		gunner.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		Vec3 dir = lay(muzzle, foe.position().add(0.0, foe.getBbHeight() * 0.4, 0.0), kind, random);
		if (ShipCannonBlock.fireLaid(level, pos, dir, gunner)) {
			NEXT.put(key, now + RELOAD + random.nextInt(JITTER));
		}
	}

	/** The nearest illager standing by the gun at {@code centre} who can serve it (up, on his feet, not riding). */
	private static @Nullable Mob gunner(ServerLevel level, Vec3 centre) {
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(GUNNER_REACH),
			m -> m.isAlive() && m instanceof AbstractIllager && !m.isPassenger() && !Downed.isDowned(m))) {
			double d = m.distanceToSqr(centre);
			if (d < bestD) {
				best = m;
				bestD = d;
			}
		}
		return best;
	}

	/** Whom he lays it on: his own foe if it's in the gun's arc, else the nearest player in it whose land has opened up. */
	private static @Nullable LivingEntity foe(ServerLevel level, Mob gunner, Vec3 muzzle, Direction facing, WorldTiers.Tier tier) {
		LivingEntity own = gunner.getTarget();
		if (own != null && own.isAlive() && !(own instanceof ServerPlayer) && inArc(level, gunner, muzzle, facing, own)) {
			return own;
		}
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(muzzle, muzzle).inflate(Corsairs.GUN_RANGE),
			p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && WorldTiers.open(p, tier))) {
			double d = p.distanceToSqr(muzzle);
			if (d < bestD && inArc(level, gunner, muzzle, facing, p)) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}

	/** {@code e} is within the gun's reach, off her side (in its arc), and nothing stands between. */
	private static boolean inArc(ServerLevel level, Mob gunner, Vec3 muzzle, Direction facing, LivingEntity e) {
		double dx = e.getX() - muzzle.x, dz = e.getZ() - muzzle.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < Corsairs.GUN_NEAREST || d > Corsairs.GUN_RANGE || (dx * facing.getStepX() + dz * facing.getStepZ()) / d < ARC) {
			return false;
		}
		Vec3 eye = e.getEyePosition();
		return level.clip(new ClipContext(muzzle, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, gunner)).getType() == HitResult.Type.MISS;
	}

	/** The gun laid on {@code aim}: the low arc that carries this shot that far, a little off as a gunner's lay is (as Corsairs'). */
	private static Vec3 lay(Vec3 muzzle, Vec3 aim, ShotKind kind, RandomSource random) {
		double speed = Corsairs.SHOT_SPEED * kind.speed, fall = Corsairs.SHOT_FALL * kind.gravity;
		double dx = aim.x - muzzle.x, dz = aim.z - muzzle.z;
		double d = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
		double hx = dx / d, hz = dz / d;
		double reach = Mth.clamp(fall * d / (speed * speed), -1.0, 1.0);
		double elevation = 0.5 * Math.toDegrees(Math.asin(reach)) + Math.toDegrees(Math.atan2(aim.y - muzzle.y, d));
		elevation += (random.nextFloat() - 0.5F) * 2.0F * Corsairs.AIM_SPREAD;
		double turn = Math.toRadians((random.nextFloat() - 0.5F) * 2.0F * Corsairs.AIM_SPREAD);
		double c = Math.cos(turn), s = Math.sin(turn);
		double ax = hx * c - hz * s, az = hx * s + hz * c;
		double up = Math.toRadians(Mth.clamp(elevation, Corsairs.LOWEST, Corsairs.HIGHEST));
		return new Vec3(ax * Math.cos(up), Math.sin(up), az * Math.cos(up)).normalize();
	}
}
