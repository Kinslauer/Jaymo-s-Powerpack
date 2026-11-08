package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.xenoform.forms.Wyrm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.37) Wyrm's Ultimate, Elder Wyrm: the same small four-legged drake with the ender wings on its shoulders, grown from 1.2x to
 * 2.0x, with black scale plates down its back and flanks, long spikes, a barbed blade for a spade and a swept-back crown of horns,
 * void purple seams, with a health bar of its own on top of Wyrm's. Its model is {@code UltimateElderWyrm}. Water and rain still
 * burn it.
 * <ul>
 * <li>R, Void Breath: a line of three clouds of purple fire ahead of you that burn whatever stands in them.</li>
 * <li>G (hold), Elder Wings: fly where you look, faster, for free; sneak while flying to dive, and landing from a dive throws
 * everything near upward.</li>
 * <li>V, Tempest Beat: a wider, harder gust that swats arrows and other projectiles away.</li>
 * <li>B, Void Rift: step through the void up to {@link #RIFT_RANGE} blocks, leaving a cloud where you stood and another where
 * you land, which hits what is near it.</li>
 * <li>C, Endfire Volley: five dragon fireballs fan out where you look, each bursting into a cloud of its own.</li>
 * </ul>
 * The clouds are plain {@link AreaEffectCloud}s with no potion in them, only for the look: the burn is dealt here, to the creatures
 * {@link #spared} and pvp allow, so a pet, a teammate or a player pvp protects is never hurt, and nothing is left behind when the
 * Ultimate ends, the player dies, changes dimension or quits, or the server stops.
 */
public final class ElderWyrm extends XenoUltimate {
	// ---- tuning ----
	/** Void Breath: cooldown, how far ahead each cloud sits (blocks), its radius, how long it lasts, the burn each pulse deals, ticks between pulses and to the first. */
	public static final int BREATH_COOLDOWN = 40, BREATH_TICKS = 100, PULSE_EVERY = 20, PULSE_FIRST = 4;
	public static final double[] BREATH_AHEAD = {3.0, 7.0, 11.0};
	public static final float BREATH_RADIUS = 3.0F, BREATH_DAMAGE = 6.0F;
	/** Elder Wings: how fast it flies (blocks/tick), how fast it dives, how it eases toward the look, the slow fall after letting go (ticks). */
	public static final double WING_SPEED = 1.3, DIVE_SPEED = 2.0, WING_EASE = 0.25;
	public static final int WING_SLOW_FALL_TICKS = 60;
	/** The dive's landing: ticks of diving that count, ticks of grace after a dive stops, radius, damage, how high it throws and how far out. */
	public static final int DIVE_MIN_TICKS = 4, DIVE_GRACE = 10;
	public static final double DIVE_RADIUS = 4.0, DIVE_UP = 1.0, DIVE_OUT = 0.4;
	public static final float DIVE_DAMAGE = 4.0F, DIVE_SHAKE = 1.2F;
	/** Tempest Beat: cooldown, radius, damage, shove out and up, the speed a swatted projectile leaves at, the recoil, the shake. */
	public static final int TEMPEST_COOLDOWN = 100;
	public static final double TEMPEST_RADIUS = 9.0, TEMPEST_OUT = 2.4, TEMPEST_UP = 0.9, SWAT_SPEED = 1.6, TEMPEST_RECOIL = 0.4;
	public static final float TEMPEST_DAMAGE = 6.0F, ELDER_SHAKE = 1.4F;
	public static final double TEMPEST_SHAKE_RANGE = 24.0, ELDER_SHAKE_RANGE = 28.0;
	/** Void Rift: cooldown, reach, the clouds' radius, life and burn, and the landing's reach and damage. */
	public static final int RIFT_COOLDOWN = 80, RIFT_CLOUD_TICKS = 60;
	public static final double RIFT_RANGE = 24.0, LAND_RADIUS = 2.0;
	public static final float RIFT_CLOUD_RADIUS = 2.0F, RIFT_CLOUD_DAMAGE = 3.0F, LAND_DAMAGE = 4.0F;
	/** Endfire Volley: cooldown, fireballs, the angle between neighbours (degrees), how far ahead they start, how long one flies before it is removed (ticks), the cloud each bursts into. */
	public static final int VOLLEY_COOLDOWN = 400, VOLLEY_BALLS = 5, VOLLEY_LIFE = 100, VOLLEY_CLOUD_TICKS = 100;
	public static final double VOLLEY_FAN = 15.0, VOLLEY_START = 1.6;
	public static final float VOLLEY_CLOUD_RADIUS = 2.5F, VOLLEY_DAMAGE = 5.0F;
	/** A cloud burns what stands within this far above and below its middle (blocks). */
	private static final double CLOUD_HEIGHT = 2.5;

	/** How far from a cloud a tagged fireball may be and still own it (0.21.37). */
	private static final double CLOUD_SEARCH_XZ = 6.0, CLOUD_SEARCH_Y = 4.0;
	private static final String FIREBALL_TAG = "jaymospowers_endfire";

	/** A burning cloud, or a line of them, and the burn it deals every pulse. */
	private static final class Cast {
		final ServerLevel level;
		final List<Vec3> spots = new ArrayList<>();
		final List<Integer> clouds = new ArrayList<>();
		final float radius, damage;
		final int life;
		int age;

		Cast(ServerLevel level, float radius, float damage, int life) {
			this.level = level;
			this.radius = radius;
			this.damage = damage;
			this.life = life;
		}
	}

	/** What each Elder Wyrm has under way (server thread only). */
	private static final class Work {
		final ServerLevel level;
		final List<Cast> casts = new ArrayList<>();
		final List<Integer> fireballs = new ArrayList<>();
		int diveTicks, diveGrace;

		Work(ServerLevel level) {
			this.level = level;
		}
	}

	private static final Map<UUID, Work> WORK = new HashMap<>();
	/** True while this class is adding an entity itself, so the load hook below leaves it alone. */
	private static boolean spawning;

	static {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (spawning) {
				return;
			}
			if (entity instanceof DragonFireball ball && ball.entityTags().contains(FIREBALL_TAG)) {
				// one that loads back from a save has no Elder Wyrm left to answer for it
				level.getServer().execute(ball::discard);
			} else if (entity instanceof AreaEffectCloud cloud) {
				adopt(cloud, cloud.getOwner() instanceof ServerPlayer owner ? owner : null, level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORK.clear());
	}

	public ElderWyrm(XenoForm base) {
		super(base, 2.0F, 24.0F, 8.0, 0.05, 0.7, 0.8);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.DRAGON_BREATH, Items.ELYTRA, Items.WIND_CHARGE, Items.ENDER_PEARL, Items.DRAGON_HEAD};
	}

	/** The change-in film zooms on these in turn (model part names): the tail's tip and its blade, the torso, the neck, the crown of horns. */
	@Override
	public String[] morphParts() {
		return new String[] {"tail3+spade", "torso", "neck1+neck2", "right_horn+left_horn"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.wings = false;
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 80, 1.0, 1.0, 1.0, 0.5);
		ring(level, PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), player.position(), 3.0, 24);
		sound(player, SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.9F);
		Shakes.heavy(player, player.position(), ELDER_SHAKE_RANGE, ELDER_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.wings = false;
		Work work = WORK.remove(player.getUUID());
		if (work != null) {
			clear(work);
		}
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_2) {
			if (down != live.wings) {
				live.wings = down;
				if (down) {
					sound(player, SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 0.9F);
					cooldown(player, live, G, 1);
				} else if (!player.onGround()) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, WING_SLOW_FALL_TICKS, 0, false, false));
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && breath(player, work(player))) {
					cooldown(player, live, R, BREATH_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					tempest(player);
					cooldown(player, live, V, TEMPEST_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && rift(player, work(player))) {
					cooldown(player, live, B, RIFT_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: five dragon fireballs fan out where you look, each bursting into a cloud of purple fire. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (!ready(player, live, C)) {
			return;
		}
		if (volley(player, work(player))) {
			cooldown(player, live, C, VOLLEY_COOLDOWN);
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Work work = WORK.get(player.getUUID());
		if (work != null && work.level != level) {
			// another dimension: what was started in this one is gone
			WORK.remove(player.getUUID());
			clear(work);
			work = null;
		}
		boolean diving = live.wings && player.isShiftKeyDown() && !player.onGround();
		if (live.wings) {
			Vec3 want = player.getLookAngle().scale(diving ? DIVE_SPEED : WING_SPEED);
			player.setDeltaMovement(player.getDeltaMovement().lerp(want, WING_EASE));
			player.syncVelocity = true;
			player.resetFallDistance();
			if (diving) {
				work = work(player);
			}
			if (player.tickCount % 16 == 0) {
				sound(player, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 0.8F);
				level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.5, player.getZ(), 10, 0.9, 0.5, 0.9, 0.1);
			}
		}
		if (work != null) {
			landing(player, work, diving);
			workTick(player, work);
		}
		if (player.tickCount % 3 == 0) {
			level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), player.getX(), player.getY() + 2.0, player.getZ(), 1, 1.0, 1.0, 1.0, 0.0);
		}
		Wyrm.ambient(player);
	}

	/** Its own clouds can't burn it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !(source.getDirectEntity() instanceof AreaEffectCloud cloud && cloud.getOwner() == player);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.wings || player.hasEffect(MobEffects.SLOW_FALLING);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.wings;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return live.wings && !player.onGround() ? XenoformState.FLY_WINGS : XenoformState.FLY_NONE;
	}

	// ---- what is under way ----

	private static Work work(ServerPlayer player) {
		return WORK.computeIfAbsent(player.getUUID(), id -> new Work(player.level()));
	}

	/** Every cloud and fireball it started is removed. */
	private static void clear(Work work) {
		for (Cast cast : work.casts) {
			dropClouds(cast);
		}
		work.casts.clear();
		for (int id : work.fireballs) {
			Entity ball = work.level.getEntity(id);
			if (ball instanceof DragonFireball) {
				ball.discard();
			}
		}
		work.fireballs.clear();
	}

	private static void dropClouds(Cast cast) {
		for (int id : cast.clouds) {
			Entity cloud = cast.level.getEntity(id);
			if (cloud instanceof AreaEffectCloud) {
				cloud.discard();
			}
		}
		cast.clouds.clear();
	}

	/** The pulses of every cloud, the clouds that are over, and the fireballs that are spent. */
	private static void workTick(ServerPlayer player, Work work) {
		for (int i = work.casts.size() - 1; i >= 0; i--) {
			Cast cast = work.casts.get(i);
			cast.age++;
			if (cast.age >= PULSE_FIRST && (cast.age - PULSE_FIRST) % PULSE_EVERY == 0) {
				pulse(player, cast);
			}
			if (cast.age >= cast.life) {
				dropClouds(cast);
				work.casts.remove(i);
			}
		}
		for (int i = work.fireballs.size() - 1; i >= 0; i--) {
			Entity ball = work.level.getEntity(work.fireballs.get(i));
			if (!(ball instanceof DragonFireball) || !ball.isAlive()) {
				work.fireballs.remove(i);
			} else if (ball.tickCount > VOLLEY_LIFE) {
				ball.discard();
				work.fireballs.remove(i);
			}
		}
		if (work.casts.isEmpty() && work.fireballs.isEmpty() && work.diveTicks == 0 && work.diveGrace == 0) {
			WORK.remove(player.getUUID());
		}
	}

	/** A fireball's cloud as it is made: its potion is taken out (the burn is dealt here instead) and it is kept track of. */
	private static void adopt(AreaEffectCloud cloud, ServerPlayer owner, ServerLevel level) {
		// the vanilla hit moves its cloud onto the nearest creature within 4/2/4 of the ball, so look that far and a little more (0.21.37)
		boolean ours = cloud.getOwner() != null && !level.getEntitiesOfClass(DragonFireball.class, cloud.getBoundingBox().inflate(CLOUD_SEARCH_XZ, CLOUD_SEARCH_Y, CLOUD_SEARCH_XZ),
			ball -> ball.entityTags().contains(FIREBALL_TAG) && ball.getOwner() == cloud.getOwner()).isEmpty();
		// only the dragon-breath cloud of the ball itself: a potion cloud or another creature's breath is left alone (0.21.37)
		ours = ours && cloud.getParticle().getType() == ParticleTypes.DRAGON_BREATH && cloud.tickCount == 0;
		if (!ours) {
			return;
		}
		Work work = owner == null ? null : WORK.get(owner.getUUID());
		if (work == null || work.level != level) {
			// a deflected ball's cloud, or one left after the Ultimate ended: it must not stay a real Instant Damage cloud
			level.getServer().execute(cloud::discard);
			return;
		}
		cloud.setPotionContents(PotionContents.EMPTY);
		cloud.setRadius(VOLLEY_CLOUD_RADIUS);
		cloud.setRadiusPerTick(0.0F);
		cloud.setRadiusOnUse(0.0F);
		cloud.setDurationOnUse(0);
		cloud.setDuration(VOLLEY_CLOUD_TICKS);
		Cast cast = new Cast(level, VOLLEY_CLOUD_RADIUS, VOLLEY_DAMAGE, VOLLEY_CLOUD_TICKS);
		cast.spots.add(cloud.position());
		cast.clouds.add(cloud.getId());
		work.casts.add(cast);
	}

	// ---- who may be hurt ----

	/** Every creature within {@code radius} that this one's moves may hurt: not its pets or team, and not a player pvp protects. */
	private static List<LivingEntity> foes(ServerPlayer player, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity living : around(player, radius)) {
			if (harmable(player, living)) {
				out.add(living);
			}
		}
		return out;
	}

	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}

	/** Every creature standing in {@code cast}'s clouds that may be hurt, once each. */
	private static void pulse(ServerPlayer player, Cast cast) {
		ServerLevel level = cast.level;
		Set<LivingEntity> hit = new LinkedHashSet<>();
		for (Vec3 spot : cast.spots) {
			AABB box = new AABB(spot, spot).inflate(cast.radius, CLOUD_HEIGHT, cast.radius);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, living -> living != player && living.isAlive() && !living.isSpectator()
				&& !(living instanceof ArmorStand) && !living.isPassengerOfSameVehicle(player) && !spared(player, living) && harmable(player, living))) {
				double dx = e.getX() - spot.x, dz = e.getZ() - spot.z;
				if (dx * dx + dz * dz <= cast.radius * cast.radius) {
					hit.add(e);
				}
			}
			level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), spot.x, spot.y + 0.3, spot.z, 14, cast.radius * 0.5, 0.2, cast.radius * 0.5, 0.01);
		}
		for (LivingEntity e : hit) {
			e.hurtServer(level, player.damageSources().playerAttack(player), cast.damage);
		}
	}

	/** A cloud at {@code at}, only for the look, added to {@code cast}. */
	private static void cloud(ServerPlayer player, Cast cast, Vec3 at) {
		ServerLevel level = cast.level;
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		cloud.setOwner(player);
		cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
		cloud.setRadius(cast.radius);
		cloud.setRadiusPerTick(0.0F);
		cloud.setDuration(cast.life);
		spawning = true;
		try {
			level.addFreshEntity(cloud);
		} finally {
			spawning = false;
		}
		cast.spots.add(at);
		cast.clouds.add(cloud.getId());
	}

	/** {@code point} brought down to the ground under it, if that is near. */
	private static Vec3 grounded(ServerPlayer player, Vec3 point) {
		BlockHitResult ground = player.level().clip(new ClipContext(point, point.add(0.0, -6.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return ground.getType() == HitResult.Type.BLOCK ? ground.getLocation().add(0.0, 0.1, 0.0) : point;
	}

	// ---- R: Void Breath ----

	/** Three clouds, 3, 7 and 11 blocks ahead, none put behind a wall. False if there was no room for any. */
	private static boolean breath(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Cast cast = new Cast(level, BREATH_RADIUS, BREATH_DAMAGE, BREATH_TICKS);
		for (double ahead : BREATH_AHEAD) {
			Vec3 want = eye.add(look.scale(ahead));
			BlockHitResult wall = level.clip(new ClipContext(eye, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (wall.getType() == HitResult.Type.BLOCK) {
				break; // the breath stops at a wall
			}
			Vec3 at = grounded(player, want);
			if (level.getWorldBorder().isWithinBounds(at)) {
				cloud(player, cast, at);
			}
		}
		if (cast.spots.isEmpty()) {
			return false;
		}
		work.casts.add(cast);
		for (double d = 1.0; d < BREATH_AHEAD[BREATH_AHEAD.length - 1]; d += 0.5) {
			Vec3 p = eye.add(look.scale(d));
			level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), p.x, p.y - 0.2, p.z, 3, 0.2, 0.2, 0.2, 0.02);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.ENDER_DRAGON_SHOOT, 1.4F, 0.9F);
		return true;
	}

	// ---- G: Elder Wings ----

	/** A dive that ends on the ground throws everything within {@link #DIVE_RADIUS} upward. */
	private static void landing(ServerPlayer player, Work work, boolean diving) {
		if (diving) {
			work.diveTicks++;
			work.diveGrace = DIVE_GRACE;
		} else if (work.diveGrace > 0) {
			work.diveGrace--;
		}
		if (work.diveTicks == 0) {
			return;
		}
		if (work.diveGrace == 0) {
			work.diveTicks = 0; // it stopped diving and never landed
			return;
		}
		if (!(player.onGround() || player.isInWater()) || work.diveTicks < DIVE_MIN_TICKS) {
			return;
		}
		work.diveTicks = 0;
		work.diveGrace = 0;
		ServerLevel level = player.level();
		for (LivingEntity living : foes(player, DIVE_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), DIVE_DAMAGE);
			shove(living, player.position(), DIVE_OUT, DIVE_UP);
		}
		ring(level, ParticleTypes.CLOUD, player.position(), DIVE_RADIUS * 0.5, 16);
		ring(level, PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), player.position(), DIVE_RADIUS, 24);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
		sound(player, SoundEvents.ENDER_DRAGON_FLAP, 1.5F, 0.6F);
		Shakes.heavy(player, player.position(), TEMPEST_SHAKE_RANGE, DIVE_SHAKE);
	}

	// ---- V: Tempest Beat ----

	private static void tempest(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity living : foes(player, TEMPEST_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), TEMPEST_DAMAGE);
			shove(living, player.position(), TEMPEST_OUT, TEMPEST_UP);
		}
		// arrows and other projectiles are swatted away, none of its own, its pets' or its team's
		Vec3 centre = player.getBoundingBox().getCenter();
		for (Projectile shot : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(TEMPEST_RADIUS),
			p -> p.isAlive() && p.distanceToSqr(player) <= TEMPEST_RADIUS * TEMPEST_RADIUS)) {
			Entity owner = shot.getOwner();
			if (owner == player || owner != null && spared(player, owner) || shot.entityTags().contains(FIREBALL_TAG)) {
				continue;
			}
			Vec3 away = shot.position().subtract(centre);
			away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle() : away.normalize();
			shot.setDeltaMovement(away.scale(SWAT_SPEED));
			shot.syncVelocity = true;
			level.sendParticles(ParticleTypes.CLOUD, shot.getX(), shot.getY(), shot.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
		}
		Vec3 back = player.getLookAngle().horizontal();
		if (back.lengthSqr() > 1.0E-4) {
			player.setDeltaMovement(player.getDeltaMovement().add(back.normalize().scale(-TEMPEST_RECOIL)).add(0.0, 0.2, 0.0));
			player.syncVelocity = true;
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		ring(level, ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), TEMPEST_RADIUS * 0.5, 24);
		ring(level, ParticleTypes.GUST, player.position(), TEMPEST_RADIUS, 12);
		sound(player, SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.5F);
		sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.7F);
		Shakes.heavy(player, player.getEyePosition().add(player.getLookAngle().scale(2.0)), TEMPEST_SHAKE_RANGE, ELDER_SHAKE);
	}

	// ---- B: Void Rift ----

	/** Through the void to where you look. False if there was nowhere to land. */
	private static boolean rift(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RIFT_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 point = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(look.scale(0.6)) : eye.add(look.scale(RIFT_RANGE));
		AABB box = player.getBoundingBox();
		for (int drop = 0; drop <= 3; drop++) {
			Vec3 feet = new Vec3(point.x, point.y - player.getEyeHeight() - drop, point.z);
			BlockPos block = BlockPos.containing(feet);
			if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(feet)
				|| !level.noCollision(player, box.move(feet.subtract(player.position())))) {
				continue;
			}
			Vec3 from = player.position();
			Cast cast = new Cast(level, RIFT_CLOUD_RADIUS, RIFT_CLOUD_DAMAGE, RIFT_CLOUD_TICKS);
			cloud(player, cast, from.add(0.0, 0.1, 0.0));
			player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), player.getYRot(), player.getXRot(), false);
			player.resetFallDistance();
			cloud(player, cast, feet.add(0.0, 0.1, 0.0));
			work.casts.add(cast);
			for (LivingEntity living : foes(player, LAND_RADIUS)) {
				living.hurtServer(level, player.damageSources().playerAttack(player), LAND_DAMAGE);
			}
			level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 50, 0.4, 0.9, 0.4, 0.4);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1.0, feet.z, 50, 0.4, 0.9, 0.4, 0.4);
			sound(player, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.6F);
			return true;
		}
		return false;
	}

	// ---- C: Endfire Volley ----

	/** Five dragon fireballs in a fan where you look. False if none could be launched. */
	private static boolean volley(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		int launched = 0;
		for (int i = 0; i < VOLLEY_BALLS; i++) {
			double turn = Math.toRadians((i - (VOLLEY_BALLS - 1) / 2.0) * VOLLEY_FAN);
			Vec3 dir = look.yRot((float)turn);
			Vec3 start = eye.add(dir.scale(VOLLEY_START));
			if (level.clip(new ClipContext(eye, start, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK) {
				continue; // no fireball from inside a wall
			}
			DragonFireball ball = new DragonFireball(level, player, dir);
			ball.setPos(start.x, start.y, start.z);
			ball.addTag(FIREBALL_TAG);
			spawning = true;
			try {
				if (!level.addFreshEntity(ball)) {
					continue;
				}
			} finally {
				spawning = false;
			}
			work.fireballs.add(ball.getId());
			launched++;
		}
		if (launched == 0) {
			return false;
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		ParticleOptions fire = PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);
		burst(level, fire, eye.add(look.scale(1.2)), 30, 0.6, 0.05);
		sound(player, SoundEvents.ENDER_DRAGON_GROWL, 1.5F, 1.1F);
		sound(player, SoundEvents.ENDER_DRAGON_SHOOT, 1.5F, 0.8F);
		Shakes.heavy(player, player.position(), TEMPEST_SHAKE_RANGE, ELDER_SHAKE * 0.6F);
		return true;
	}
}
