package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The sea aliens call the sea (Gnasher, Murk, Slipfin share this). Nothing here is a key of its own: each is a variant of a
 * move or a Z tap on the form that fits it.
 * <ul>
 * <li>{@link #shoal}: under water, fish and sea creatures nearby fight for the player for a while (vanilla aquatic mobs, and
 * any the pack tags {@code jaymospowers:sea_helpers}); they never go for players, or anyone's pets.</li>
 * <li>{@link #tentacle}: a tentacle out of the sea seizes a creature, lifts it, and throws it where the player looks.</li>
 * <li>{@link #whirlpool}: a whirlpool where the player looks drags creatures and items into it.</li>
 * <li>{@link #answerDrowned}: with a trident in hand the drowned nearby turn on the player's foes instead.</li>
 * <li>{@link #dome}: a dome of water that stops projectiles for a few seconds.</li>
 * </ul>
 * One tick call ({@link #tick}) from each of the three forms runs everything that lasts.
 */
public final class SeaCalls {
	// ---- tuning ----
	/** Shoal: who is called (blocks), how many, how long they fight (ticks), the damage of a bite, ticks between bites, the cooldown. */
	public static final double SHOAL_RADIUS = 24.0, FOE_RADIUS = 20.0;
	public static final int SHOAL_MAX = 10, SHOAL_TICKS = 300, SHOAL_COOLDOWN = 600, BITE_EVERY = 20;
	public static final float BITE_DAMAGE = 2.0F, BITE_BIG = 3.5F;
	/** Tentacle: reach, ticks it lifts for, damage of the throw, throw speed, extra damage share in water, the cooldown. */
	public static final double TENTACLE_REACH = 14.0, THROW_SPEED = 1.5;
	public static final int LIFT_TICKS = 14, TENTACLE_COOLDOWN = 160;
	public static final float THROW_DAMAGE = 5.0F, WET_BONUS = 1.5F;
	/** Whirlpool: how far it can be put, its radius, the pull, how long, how close the grinding starts, its damage, the cooldown. */
	public static final double WHIRL_REACH = 16.0, WHIRL_RADIUS = 7.0, WHIRL_PULL = 0.16, WHIRL_SWIRL = 0.12, WHIRL_GRIND = 1.8;
	public static final int WHIRL_TICKS = 100, WHIRL_COOLDOWN = 300;
	public static final float WHIRL_DAMAGE = 1.5F;
	/** Drowned: how far they hear a trident, and how long an answer holds. */
	public static final double DROWNED_RADIUS = 32.0;
	public static final int ANSWER_TICKS = 60;
	/** Dome: radius, ticks it stands, the cooldown. */
	public static final double DOME_RADIUS = 4.5;
	public static final int DOME_TICKS = 100, DOME_COOLDOWN = 400;
	public static final int WATER = 0x3A86D8;

	private static final TagKey<EntityType<?>> SEA_HELPERS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("sea_helpers"));
	private static final int S_SHOAL = 0, S_TENTACLE = 1, S_WHIRL = 2, S_DOME = 3, SLOTS = 4;

	private static final Map<UUID, long[]> NEXT = new HashMap<>();
	/** Called helpers: entity id to the game time they stand down, and when they last bit. */
	private static final Map<UUID, Map<Integer, long[]>> HELPERS = new HashMap<>();
	private static final Map<UUID, Tentacle> TENTACLES = new HashMap<>();
	private static final Map<UUID, Whirl> WHIRLS = new HashMap<>();
	private static final Map<UUID, Dome> DOMES = new HashMap<>();

	private record Tentacle(int target, long since, Vec3 base) {
	}

	private record Whirl(ResourceKey<Level> dimension, Vec3 centre, long until) {
	}

	private record Dome(ResourceKey<Level> dimension, Vec3 centre, long until) {
	}

	private SeaCalls() {
	}

	private static boolean ready(ServerPlayer player, int slot) {
		return NEXT.computeIfAbsent(player.getUUID(), k -> new long[SLOTS])[slot] <= player.level().getGameTime();
	}

	private static void spend(ServerPlayer player, int slot, int cooldown) {
		NEXT.computeIfAbsent(player.getUUID(), k -> new long[SLOTS])[slot] = player.level().getGameTime() + cooldown;
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.sea." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** The form is over (or a relog): everything the player called goes. */
	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		NEXT.remove(id);
		HELPERS.remove(id);
		TENTACLES.remove(id);
		WHIRLS.remove(id);
		DOMES.remove(id);
	}

	/** Whether {@code e} may be called to fight: a sea creature (vanilla aquatic, or tagged) that is not hostile and is nobody's pet. */
	private static boolean helper(ServerPlayer player, Entity e) {
		if (!(e instanceof Mob mob) || !mob.isAlive() || e instanceof Enemy || e instanceof Player) {
			return false;
		}
		if (e instanceof OwnableEntity pet && pet.getOwner() != null && pet.getOwner() != player) {
			return false;
		}
		return e.getType().builtInRegistryHolder().is(EntityTypeTags.AQUATIC) || e.getType().builtInRegistryHolder().is(SEA_HELPERS);
	}

	/** Who the helpers may fight: monsters, and mobs hunting the player; never players, pets, the player's friends or other helpers. */
	private static boolean fair(ServerPlayer player, Entity e) {
		return e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand) && !(e instanceof Player)
			&& !(e instanceof OwnableEntity) && !player.isAlliedTo(e) && !helper(player, e) && !e.isPassengerOfSameVehicle(player)
			&& (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player);
	}

	private static @Nullable LivingEntity foe(ServerPlayer player, ServerLevel level, Vec3 from) {
		LivingEntity hurt = player.getLastHurtMob();
		if (hurt != null && fair(player, hurt) && hurt.distanceToSqr(player) <= FOE_RADIUS * FOE_RADIUS) {
			return hurt;
		}
		LivingEntity best = null;
		double near = Double.MAX_VALUE;
		for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FOE_RADIUS), c -> fair(player, c))) {
			double d = c.distanceToSqr(from);
			if (d < near) {
				near = d;
				best = c;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- shoal

	/** The sea answers: up to {@link #SHOAL_MAX} sea creatures near the player fight for {@link #SHOAL_TICKS} ticks. Needs the player in water. */
	public static boolean shoal(ServerPlayer player) {
		if (!player.isInWater()) {
			say(player, "no_water");
			return false;
		}
		if (!ready(player, S_SHOAL)) {
			return false;
		}
		ServerLevel level = player.level();
		Map<Integer, long[]> called = HELPERS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		long until = level.getGameTime() + SHOAL_TICKS;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SHOAL_RADIUS), m -> helper(player, m) && m.isInWater())) {
			if (called.size() >= SHOAL_MAX) {
				break;
			}
			called.put(mob.getId(), new long[] {until, 0L});
			mob.setTarget(null);
		}
		if (called.isEmpty()) {
			say(player, "no_shoal");
			return false;
		}
		spend(player, S_SHOAL, SHOAL_COOLDOWN);
		MagicFx.ring(level, player.position(), (float)SHOAL_RADIUS * 0.5F, WATER);
		MagicFx.pool(level, player.position(), 3.0F, WATER, 30);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DOLPHIN_PLAY, SoundSource.PLAYERS, 1.4F, 0.7F);
		return true;
	}

	private static void shoalTick(ServerPlayer player, ServerLevel level, long now) {
		Map<Integer, long[]> called = HELPERS.get(player.getUUID());
		if (called == null) {
			return;
		}
		boolean think = player.tickCount % 5 == 0;
		LivingEntity target = think ? foe(player, level, player.position()) : null;
		var it = called.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			Entity e = level.getEntity(entry.getKey());
			if (!(e instanceof Mob mob) || !mob.isAlive() || entry.getValue()[0] <= now) {
				it.remove();
				continue;
			}
			if (!think) {
				continue;
			}
			if (player.tickCount % 20 == 0) {
				level.sendParticles(MagicFx.mote(WATER, 0.5F), mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
			}
			if (target == null) {
				if (mob.distanceToSqr(player) > 36.0) {
					mob.getNavigation().moveTo(player, 1.3);
				}
				continue;
			}
			double reach = 1.6 + mob.getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
			if (mob.distanceTo(target) > reach) {
				mob.getNavigation().moveTo(target, 1.5);
				if (target.isInWater() || mob.distanceTo(target) < 4.0) {
					Vec3 to = target.getBoundingBox().getCenter().subtract(mob.position()).normalize();
					mob.setDeltaMovement(mob.getDeltaMovement().add(to.scale(0.1)));
				}
			} else if (now - entry.getValue()[1] >= BITE_EVERY) {
				entry.getValue()[1] = now;
				float damage = mob.getBbWidth() * mob.getBbHeight() > 1.0F ? BITE_BIG : BITE_DAMAGE;
				target.hurtServer(level, player.damageSources().playerAttack(player), damage);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.DOLPHIN_ATTACK, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
		}
		if (called.isEmpty()) {
			HELPERS.remove(player.getUUID());
		}
	}

	// ---------------------------------------------------------------- tentacle

	/** A tentacle out of the sea seizes {@code target}, lifts it, and throws it where the player looks. Stronger in water. */
	public static boolean tentacle(ServerPlayer player, @Nullable LivingEntity target) {
		if (target == null) {
			say(player, "no_target");
			return false;
		}
		if (!ready(player, S_TENTACLE) || TENTACLES.containsKey(player.getUUID())) {
			return false;
		}
		spend(player, S_TENTACLE, TENTACLE_COOLDOWN);
		TENTACLES.put(player.getUUID(), new Tentacle(target.getId(), player.level().getGameTime(), target.position().subtract(0.0, 0.0, 0.0)));
		player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1.4F, 0.5F);
		return true;
	}

	private static void tentacleTick(ServerPlayer player, ServerLevel level, long now) {
		Tentacle t = TENTACLES.get(player.getUUID());
		if (t == null) {
			return;
		}
		Entity e = level.getEntity(t.target);
		if (!(e instanceof LivingEntity held) || !held.isAlive()) {
			TENTACLES.remove(player.getUUID());
			return;
		}
		long age = now - t.since;
		Vec3 top = held.getBoundingBox().getCenter();
		Vec3 root = new Vec3(held.getX(), Math.min(t.base.y, held.getY()) - 0.5, held.getZ());
		DarktideStatus.stun(held, 4);
		if (age < LIFT_TICKS) {
			held.setDeltaMovement(0.0, 0.32, 0.0);
			held.syncVelocity = true;
			MagicFx.beam(level, root, top, 0.28F, 0x4A3A68, 2);
			return;
		}
		TENTACLES.remove(player.getUUID());
		boolean wet = held.isInWater() || player.isInWater();
		Vec3 out = player.getLookAngle().scale(THROW_SPEED).add(0.0, 0.5, 0.0);
		held.setDeltaMovement(out);
		held.syncVelocity = true;
		held.hurtServer(level, player.damageSources().playerAttack(player), THROW_DAMAGE * (wet ? WET_BONUS : 1.0F));
		MagicFx.stroke(level, top, top.add(out.scale(3.0)), 0.5F, 0x4A3A68);
		MagicFx.blast(level, top, 1.6F, WATER);
		level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.4F, 0.6F);
	}

	// ---------------------------------------------------------------- whirlpool

	/** A whirlpool where the player looks (water, a block or a creature within reach) that drags things in. */
	public static boolean whirlpool(ServerPlayer player, @Nullable LivingEntity aimed) {
		if (!ready(player, S_WHIRL)) {
			return false;
		}
		Vec3 centre;
		if (aimed != null) {
			centre = aimed.position();
		} else {
			HitResult hit = player.pick(WHIRL_REACH, 1.0F, true);
			centre = hit.getType() == HitResult.Type.MISS ? player.getEyePosition().add(player.getLookAngle().scale(WHIRL_REACH * 0.5)) : hit.getLocation();
		}
		ServerLevel level = player.level();
		spend(player, S_WHIRL, WHIRL_COOLDOWN);
		WHIRLS.put(player.getUUID(), new Whirl(level.dimension(), centre, level.getGameTime() + WHIRL_TICKS));
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.PLAYERS, 1.6F, 0.8F);
		return true;
	}

	private static void whirlTick(ServerPlayer player, ServerLevel level, long now) {
		Whirl w = WHIRLS.get(player.getUUID());
		if (w == null) {
			return;
		}
		if (now >= w.until || !w.dimension.equals(level.dimension())) {
			WHIRLS.remove(player.getUUID());
			return;
		}
		Vec3 c = w.centre;
		if (now % 10 == 0) {
			MagicFx.pool(level, c, (float)WHIRL_RADIUS * 0.8F, WATER, 14);
			MagicFx.ring(level, c, (float)WHIRL_RADIUS * 0.5F, WATER);
		}
		AABB box = new AABB(c, c).inflate(WHIRL_RADIUS, WHIRL_RADIUS * 0.6, WHIRL_RADIUS);
		for (Entity e : level.getEntities(player, box, x -> x.isAlive() && !x.isSpectator() && !(x instanceof Player p && p == player)
			&& (x instanceof ItemEntity || x instanceof LivingEntity l && !(l instanceof ArmorStand) && !(l instanceof OwnableEntity pet && pet.getOwner() == player) && !player.isAlliedTo(l)))) {
			Vec3 to = c.subtract(e.position());
			double d = to.length();
			if (d > WHIRL_RADIUS || d < 0.01) {
				continue;
			}
			Vec3 in = to.normalize();
			Vec3 swirl = new Vec3(-in.z, 0.0, in.x);
			double pull = WHIRL_PULL * (0.4 + 0.6 * (1.0 - d / WHIRL_RADIUS));
			e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(in.scale(pull)).add(swirl.scale(WHIRL_SWIRL)).add(0.0, d < WHIRL_GRIND ? 0.0 : 0.02, 0.0));
			e.syncVelocity = true;
			e.resetFallDistance();
			if (d < WHIRL_GRIND && now % 20 == 0 && e instanceof LivingEntity living) {
				living.hurtServer(level, player.damageSources().playerAttack(player), WHIRL_DAMAGE);
			}
		}
	}

	// ---------------------------------------------------------------- drowned

	/** Passive, with a trident in hand: the drowned within earshot stop hunting the player and turn on the player's foes. */
	public static void answerDrowned(ServerPlayer player) {
		if (player.tickCount % 20 != 0 || !player.getMainHandItem().is(Items.TRIDENT)) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity target = null;
		boolean looked = false;
		for (net.minecraft.world.entity.monster.zombie.Drowned drowned : level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Drowned.class, player.getBoundingBox().inflate(DROWNED_RADIUS), Entity::isAlive)) {
			if (!looked) {
				target = foe(player, level, player.position());
				looked = true;
			}
			if (drowned.getTarget() == player || drowned.getTarget() != null && !(drowned.getTarget() instanceof Enemy)) {
				drowned.setTarget(null);
			}
			if (target != null && target != drowned) {
				drowned.setTarget(target);
			}
			level.sendParticles(MagicFx.mote(WATER, 0.4F), drowned.getX(), drowned.getY() + drowned.getBbHeight(), drowned.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
		}
	}

	// ---------------------------------------------------------------- dome

	/** A dome of water round the player's spot that stops every projectile that is not theirs, for {@link #DOME_TICKS} ticks. */
	public static boolean dome(ServerPlayer player) {
		if (!ready(player, S_DOME)) {
			return false;
		}
		ServerLevel level = player.level();
		spend(player, S_DOME, DOME_COOLDOWN);
		DOMES.put(player.getUUID(), new Dome(level.dimension(), player.position(), level.getGameTime() + DOME_TICKS));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 1.6F, 0.8F);
		return true;
	}

	private static void domeTick(ServerPlayer player, ServerLevel level, long now) {
		Dome d = DOMES.get(player.getUUID());
		if (d == null) {
			return;
		}
		if (now >= d.until || !d.dimension.equals(level.dimension())) {
			DOMES.remove(player.getUUID());
			MagicFx.blast(level, d.centre.add(0.0, 1.0, 0.0), (float)DOME_RADIUS * 0.6F, WATER);
			return;
		}
		Vec3 c = d.centre.add(0.0, 0.5, 0.0);
		if (now % 4 == 0) {
			MagicFx.orb(level, c, (float)DOME_RADIUS, WATER, 6);
		}
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(DOME_RADIUS + 1.0))) {
			Entity owner = p.getOwner();
			if (owner == player || owner instanceof OwnableEntity pet && pet.getOwner() == player || owner != null && player.isAlliedTo(owner)) {
				continue;
			}
			Vec3 to = c.subtract(p.position());
			if (to.length() <= DOME_RADIUS && p.getDeltaMovement().dot(to) > 0.0) {
				MagicFx.blast(level, p.position(), 0.8F, WATER);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 1.6F);
				p.discard();
			}
		}
	}

	// ---------------------------------------------------------------- every tick

	/** Every server tick as Gnasher, Murk or Slipfin: whatever lasts runs on. */
	public static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		shoalTick(player, level, now);
		tentacleTick(player, level, now);
		whirlTick(player, level, now);
		domeTick(player, level, now);
	}

	/** Used by the forms to list who is currently fighting for the player. */
	public static List<Integer> helpers(ServerPlayer player) {
		Map<Integer, long[]> called = HELPERS.get(player.getUUID());
		return called == null ? List.of() : new ArrayList<>(called.keySet());
	}
}
