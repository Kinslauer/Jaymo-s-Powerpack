package com.jaymospowers.armory;

import com.jaymospowers.core.LockOn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.2) The rapier, after Just-In COMBAT's fencing weapon (its idea only: our own code and art,
 * tools/gen_rapier.py). Right-click lunges: a burst forward, and the first foe in front of the point while the lunge
 * lasts is run through, harder than a swing. Mobs that carry one (Kingdoms' duelling lords and guards, its tiered
 * monsters) lunge at their target too, from a few blocks off. Its left-click swings are Better Combat's rapier slash
 * and stab.
 */
public class RapierItem extends Item {
	/**
	 * The lunge: speed (blocks/tick) forward and up, and how long it can still run someone through (ticks). (0.17.122)
	 * Andy: shorter across the board, about 2.8 blocks; only a lunge at a foe you're locked onto (or a mob's at its
	 * target) runs further, just as far as it needs to reach them, up to the old 1.1 (about 4.7 blocks).
	 */
	static final double LUNGE_SPEED = 0.65, LUNGE_SPEED_MAX = 1.1, LUNGE_UP = 0.12;
	/** Roughly how far (blocks) a lunge carries for each block/tick of speed it starts with. */
	static final double LUNGE_CARRY = 4.3;
	static final int LUNGE_TICKS = 7;
	/** How far in front of the lunger the point reaches (blocks). */
	static final double LUNGE_REACH = 1.4;
	/** The thrust lands at this many times the wielder's attack damage. */
	static final float LUNGE_DAMAGE = 1.6F;
	/** The wait before lunging again (ticks): players, and mobs. */
	static final int COOLDOWN = 30, MOB_COOLDOWN = 70;
	/** A lunge costs this much hunger exhaustion. */
	static final float EXHAUSTION = 0.4F;
	/** Mobs lunge at a target between these distances (blocks), checked this often (ticks), at this chance each check. */
	static final double MOB_MIN_RANGE = 2.8, MOB_MAX_RANGE = 6.5;
	static final int MOB_CHECK_TICKS = 5;
	static final float MOB_CHANCE = 0.35F;
	/** Mobs near a player within this (blocks) are the ones looked at. */
	static final double MOB_SCAN_RANGE = 40.0;

	/** A lunge under way: until when it can strike, its thrust's damage, and (mobs) the only one it can strike. */
	private record Lunge(long until, float damage, @Nullable LivingEntity only) {
	}

	private static final Map<LivingEntity, Lunge> LUNGES = new WeakHashMap<>();
	/** When each mob may lunge again (game time). */
	private static final Map<Mob, Long> MOB_READY = new WeakHashMap<>();

	public RapierItem(Properties properties) {
		super(properties);
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RapierItem::tick);
	}

	/** Whether {@code stack} is a rapier. */
	public static boolean is(ItemStack stack) {
		return stack.getItem() instanceof RapierItem;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(stack) || player.isPassenger()) {
			return InteractionResult.PASS;
		}
		// the burst forward on both sides, so the client doesn't wait for the server to move; locked on, at the foe
		LivingEntity locked = LockOn.target(player);
		Vec3 look = locked != null ? locked.position().subtract(player.position()) : player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.directionFromRotation(0.0F, player.getYRot());
		double speed = locked != null ? speedToReach(player, locked) : LUNGE_SPEED;
		player.setDeltaMovement(flat.x * speed, Math.max(player.getDeltaMovement().y, 0.0) + LUNGE_UP, flat.z * speed);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		if (level instanceof ServerLevel serverLevel) {
			player.syncVelocity = true;
			player.causeFoodExhaustion(EXHAUSTION);
			player.swing(hand, stack.getAttackAnimation(), true);
			begin(serverLevel, player, null);
		}
		return InteractionResult.SUCCESS;
	}

	/** A lunge just fast enough that the point reaches {@code target}: never shorter than the plain one, never past the max. */
	private static double speedToReach(LivingEntity lunger, LivingEntity target) {
		double dx = target.getX() - lunger.getX(), dz = target.getZ() - lunger.getZ();
		double gap = Math.sqrt(dx * dx + dz * dz) - (lunger.getBbWidth() + target.getBbWidth()) / 2.0;
		return Mth.clamp((gap - LUNGE_REACH * 0.5) / LUNGE_CARRY, LUNGE_SPEED, LUNGE_SPEED_MAX);
	}

	/** Starts the lunge's window: the point strikes the first foe it meets (or only {@code only}). */
	private static void begin(ServerLevel level, LivingEntity lunger, @Nullable LivingEntity only) {
		float damage = (float) (lunger.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 2.0
			: lunger.getAttributeValue(Attributes.ATTACK_DAMAGE)) * LUNGE_DAMAGE;
		LUNGES.put(lunger, new Lunge(level.getGameTime() + LUNGE_TICKS, damage, only));
		level.playSound(null, lunger.getX(), lunger.getY(), lunger.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, lunger.getSoundSource(), 0.7F, 1.6F);
	}

	private static void tick(MinecraftServer server) {
		if (!LUNGES.isEmpty()) {
			for (Map.Entry<LivingEntity, Lunge> entry : new ArrayList<>(LUNGES.entrySet())) {
				LivingEntity lunger = entry.getKey();
				Lunge lunge = entry.getValue();
				if (lunger == null || !(lunger.level() instanceof ServerLevel level) || !lunger.isAlive()
					|| level.getGameTime() > lunge.until() || !is(lunger.getMainHandItem())) {
					LUNGES.remove(lunger);
					continue;
				}
				LivingEntity hit = pointMeets(level, lunger, lunge.only());
				if (hit != null) {
					LUNGES.remove(lunger);
					runThrough(level, lunger, hit, lunge.damage());
				}
			}
		}
		if (server.getTickCount() % MOB_CHECK_TICKS == 0) {
			mobsLunge(server);
		}
	}

	/** The first foe in front of the point, or null. */
	private static @Nullable LivingEntity pointMeets(ServerLevel level, LivingEntity lunger, @Nullable LivingEntity only) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, lunger.getYRot()).scale(LUNGE_REACH);
		AABB reach = lunger.getBoundingBox().expandTowards(ahead).inflate(0.25, 0.1, 0.25);
		if (only != null) {
			return only.isAlive() && only.getBoundingBox().intersects(reach) ? only : null;
		}
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, reach, e -> e != lunger && e.isAlive() && e.isAttackable()
			&& !(e instanceof ArmorStand) && !e.isSpectator() && !lunger.isAlliedTo(e) && !lunger.isPassengerOfSameVehicle(e)
			&& !(e instanceof OwnableEntity owned && owned.getOwner() == lunger))) {
			double d = other.distanceToSqr(lunger);
			if (d < bestDist) {
				best = other;
				bestDist = d;
			}
		}
		return best;
	}

	/** The thrust lands: the blow, a spray of sparks, and the lunge stops dead. */
	private static void runThrough(ServerLevel level, LivingEntity lunger, LivingEntity target, float damage) {
		target.setInvulnerableTime(0);
		boolean landed = target.hurtServer(level, lunger instanceof Player player ? level.damageSources().playerAttack(player)
			: level.damageSources().mobAttack(lunger), damage);
		if (landed) {
			lunger.getMainHandItem().hurtAndBreak(1, lunger, InteractionHand.MAIN_HAND);
			if (lunger instanceof Mob mob) {
				mob.setLastHurtMob(target);
			}
		}
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.25);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.3F);
		Vec3 v = lunger.getDeltaMovement();
		lunger.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
		lunger.syncVelocity = true;
	}

	/** Mobs with a rapier near players: now and then they lunge at their target from a few blocks off. */
	private static void mobsLunge(MinecraftServer server) {
		List<Mob> seen = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(MOB_SCAN_RANGE),
				m -> m.isAlive() && m.getTarget() != null && is(m.getMainHandItem()))) {
				if (seen.contains(mob)) {
					continue;
				}
				seen.add(mob);
				tryLunge(level, mob);
			}
		}
	}

	private static void tryLunge(ServerLevel level, Mob mob) {
		LivingEntity target = mob.getTarget();
		long now = level.getGameTime();
		if (target == null || !target.isAlive() || !mob.onGround() || mob.isPassenger() || LUNGES.containsKey(mob)
			|| now < MOB_READY.getOrDefault(mob, 0L) || mob.getRandom().nextFloat() >= MOB_CHANCE) {
			return;
		}
		double dist = mob.distanceTo(target);
		if (dist < MOB_MIN_RANGE || dist > MOB_MAX_RANGE || !mob.getSensing().hasLineOfSight(target)) {
			return;
		}
		Vec3 to = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ()).normalize();
		float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		// a little faster the further off, so the point arrives
		double speed = speedToReach(mob, target);
		mob.setDeltaMovement(to.x * speed, LUNGE_UP, to.z * speed);
		mob.syncVelocity = true;
		mob.swing(InteractionHand.MAIN_HAND, mob.getMainHandItem().getAttackAnimation(), false);
		MOB_READY.put(mob, now + MOB_COOLDOWN);
		begin(level, mob, target);
	}

	/** Whether {@code entity} is lunging right now (the clash and the like can tell a thrust from a swing). */
	public static boolean lunging(Entity entity) {
		return entity instanceof LivingEntity living && LUNGES.containsKey(living);
	}
}
