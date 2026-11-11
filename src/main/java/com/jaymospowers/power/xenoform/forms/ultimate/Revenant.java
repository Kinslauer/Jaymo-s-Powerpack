package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.21) Wisp's Ultimate, Revenant: the hooded legless ghost grown a fifth bigger, a bone mask and collar plates on its tattered
 * cloth, long tendrils trailing off the hem, ice-blue seams in the cloth, and a health bar of its own on top of Wisp's. Its model is
 * {@code UltimateRevenant}. A ghost still: it is never hurt by a fall.
 * <ul>
 * <li>R, Wraithwalk: Phase through a wall up to {@link #WRAITH_BLOCKS} thick; creatures on the way are passed through, hurt and slowed.</li>
 * <li>G, Veil: fade from sight until you strike; the first strike out of it hits harder.</li>
 * <li>B (hold), Drift: slow true flight along your look; Jump rises, sneaking sinks.</li>
 * <li>V, Banshee Wail: a long wail that hurts and weakens everything near; creatures flee, players are plunged into darkness.</li>
 * <li>C, Revenant: slip into the creature you look at and wear it; its own attack strikes what you aim at. C again lets go.</li>
 * </ul>
 */
public final class Revenant extends XenoUltimate {
	// ---- tuning ----
	/** Wraithwalk: cooldown, how thick a wall it crosses (blocks), how far away the wall's face may be, damage to what it passes, the Slowness level and its ticks, how close to its path a creature counts as passed. */
	public static final int WRAITH_COOLDOWN = 60, WRAITH_SLOW_TICKS = 60, WRAITH_SLOW_LEVEL = 1;
	public static final int WRAITH_BLOCKS = 5;
	public static final double WRAITH_REACH = 3.0, WRAITH_PASS_WIDTH = 1.3;
	public static final float WRAITH_DAMAGE = 3.0F;
	/** Veil: cooldown, and the extra damage of the first strike out of it. */
	public static final int VEIL_COOLDOWN = 40;
	public static final float VEIL_BONUS = 6.0F;
	/** Banshee Wail: cooldown, how long creatures keep fleeing, the darkness players get (ticks), the Weakness everything gets (ticks), reach, how far a fleeing creature runs, its speed, damage. */
	public static final int WAIL_COOLDOWN = 300, WAIL_TICKS = 60, WAIL_DARKNESS_TICKS = 100, WAIL_WEAKNESS_TICKS = 120;
	public static final double WAIL_RADIUS = 16.0, WAIL_FLEE = 16.0, WAIL_FLEE_SPEED = 1.5;
	public static final float WAIL_DAMAGE = 4.0F;
	/** Drift: how fast it floats forward (blocks a tick), how fast Jump lifts and sneaking sinks it. */
	public static final double DRIFT_SPEED = 0.2, DRIFT_RISE = 0.12, DRIFT_SINK = 0.1;
	/** Revenant: how far you can reach into a creature, how long you wear it, how fast it goes for you, C's cooldown. */
	public static final double POSSESS_REACH = 8.0, POSSESS_SPEED = 0.32;
	public static final int POSSESS_TICKS = 300, POSSESS_COOLDOWN = 300;
	/** Worn: how far past its own width the host strikes, ticks between its strikes, and what a host with no attack of its own deals. */
	public static final double HOST_REACH = 3.0;
	public static final int HOST_STRIKE_EVERY = 10;
	public static final float HOST_FALLBACK_DAMAGE = 3.0F;
	/** What the host gets when you let go: Weakness II, Glowing and Slowness III (ticks). */
	public static final int HOST_WEAK_TICKS = 200, HOST_GLOW_TICKS = 200, HOST_SLOW_TICKS = 60;
	/** The change-in: screen shake (degrees) and how far it is felt. */
	public static final float REVENANT_SHAKE = 1.0F;
	public static final double REVENANT_SHAKE_RANGE = 20.0;

	/** What each Revenant has under way (server thread only): when it took its host, whether it was swinging, and when the host last struck. */
	private static final class Work {
		long since;
		boolean wasSwinging;
		long lastStrike;
		/** (0.20.21) The host's UUID (its id changes through a portal) and whether it was NoAI before the possession. */
		UUID hostId;
		boolean hostWasNoAi;
	}

	private static final Map<UUID, Work> WORK = new HashMap<>();
	/** (0.20.21) Players whose Invisibility was added by this form (a drunk potion is never marked, so it is never taken). */
	private static final java.util.Set<UUID> OWN_INVIS = new java.util.HashSet<>();

	public Revenant(XenoForm base) {
		super(base, 1.2F, 12.0F, 5.0, 0.1, 0.0, 0.0);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.ECHO_SHARD, Items.PHANTOM_MEMBRANE, Items.SCULK_SHRIEKER, Items.FEATHER, Items.WITHER_SKELETON_SKULL};
	}

	/** The change-in film zooms on these in turn (model part names): the hem and its tendrils, the cloth body, the hood, the head. */
	@Override
	public String[] morphParts() {
		return new String[] {"tatters", "body", "hood", "head"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 30, 0.4, 0.7, 0.4, 0.05);
		ring(level, ParticleTypes.SOUL, player.position().add(0.0, 0.3, 0.0), 2.0, 24);
		sound(player, SoundEvents.PHANTOM_DEATH, 1.5F, 0.6F);
		sound(player, SoundEvents.WARDEN_ROAR, 0.5F, 1.8F);
		Shakes.heavy(player, player.position(), REVENANT_SHAKE_RANGE, REVENANT_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		release(player, live, false);
		WORK.remove(player.getUUID());
		unveil(player, live, false);
		dropOwnInvisibility(player); // (0.20.21)
		live.glide = false;
		live.wail = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			live.glide = down;
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && wraithwalk(player)) {
					cooldown(player, live, R, WRAITH_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (live.shroud) {
					unveil(player, live, true);
				} else if (ready(player, live, G)) {
					veil(player, live);
					cooldown(player, live, G, VEIL_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					wail(player, live);
					cooldown(player, live, V, WAIL_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: slip into the creature you look at, or let go of the one you wear. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (live.possessed >= 0) {
			release(player, live, true);
		} else if (ready(player, live, C) && possess(player, live)) {
			cooldown(player, live, C, POSSESS_COOLDOWN);
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.glide && player.getVehicle() == null && !player.isInWater()) {
			drift(player);
		}
		if (live.shroud) {
			addOwnInvisibility(player, 6000);
			if (player.tickCount % 5 == 0) {
				level.sendParticles(player, ParticleTypes.SCULK_SOUL, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
			}
		}
		if (live.wail > 0) {
			live.wail--;
			if (live.wail % 10 == 0) {
				scatter(player);
			}
		}
		if (live.possessed >= 0) {
			wearTick(player, live);
		}
		// always a little unreal
		if (player.tickCount % 5 == 0) {
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.35, 0.1, 0.35, 0.005);
		}
	}

	/** The veil lifts the moment you strike, and that first strike hits harder. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		if (live.shroud) {
			unveil(player, live, true);
			target.setInvulnerableTime(0);
			target.hurtServer(player.level(), player.damageSources().playerAttack(player), VEIL_BONUS);
		}
	}

	/** A ghost falls as it pleases. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.glide || live.possessed >= 0;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.glide;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return live.glide && player.getVehicle() == null && !player.isInWater() ? XenoformState.FLY_FLOAT : XenoformState.FLY_NONE;
	}

	// ---- R: Wraithwalk ----

	/** Through the wall ahead: the first place beyond it, within {@link #WRAITH_BLOCKS} of its face, where you fit. Whatever stands on the way is passed through. */
	private static boolean wraithwalk(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(WRAITH_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		Vec3 face = hit.getLocation();
		AABB box = player.getBoundingBox();
		for (double d = 0.6; d <= WRAITH_BLOCKS + 0.6; d += 0.25) {
			Vec3 point = face.add(look.scale(d));
			for (int drop = 0; drop <= 1; drop++) {
				Vec3 feet = new Vec3(point.x, point.y - player.getEyeHeight() - drop, point.z);
				AABB moved = box.move(feet.subtract(player.position()));
				if (moved.minY >= level.getMinY() && moved.maxY <= level.getMinY() + level.dimensionType().logicalHeight() // (0.20.21) never under the world or on the Nether roof
					&& level.mayInteract(player, BlockPos.containing(feet)) && level.noCollision(player, moved) && !level.containsAnyLiquid(moved)) { // (0.20.21) never into lava
					Vec3 from = player.position();
					passThrough(player, from, feet);
					player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), player.getYRot(), player.getXRot(), false);
					player.resetFallDistance();
					level.sendParticles(MagicFx.VOID, from.x, from.y + 1.0, from.z, 24, 0.3, 0.6, 0.3, 0.05);
					level.sendParticles(MagicFx.VOID, feet.x, feet.y + 1.0, feet.z, 24, 0.3, 0.6, 0.3, 0.05);
					level.sendParticles(ParticleTypes.SCULK_SOUL, feet.x, feet.y + 1.0, feet.z, 10, 0.3, 0.6, 0.3, 0.03);
					sound(player, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.1F);
					return true;
				}
			}
		}
		return false;
	}

	/** Every creature near the line from {@code from} to {@code to} is passed through: hurt, and slowed. */
	private static void passThrough(ServerPlayer player, Vec3 from, Vec3 to) {
		ServerLevel level = player.level();
		AABB sweep = new AABB(from, to).inflate(WRAITH_PASS_WIDTH + 1.0, 2.0, WRAITH_PASS_WIDTH + 1.0);
		Vec3 line = to.subtract(from);
		double length2 = Math.max(1.0E-4, line.lengthSqr());
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, sweep,
			e -> e != player && !e.isPassengerOfSameVehicle(player) && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !spared(player, e)
				&& !e.entityTags().contains(XenoformState.MINION_TAG))) { // (0.20.21) not the host you wear
			Vec3 at = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0);
			double t = Math.max(0.0, Math.min(1.0, at.subtract(from.add(0.0, 1.0, 0.0)).dot(line) / length2));
			Vec3 closest = from.add(0.0, 1.0, 0.0).add(line.scale(t));
			if (closest.distanceTo(at) <= WRAITH_PASS_WIDTH + living.getBbWidth() * 0.5) {
				if (!living.hurtServer(level, player.damageSources().playerAttack(player), WRAITH_DAMAGE) && living instanceof Player) {
					continue; // (0.20.21) a player it could not hurt (pvp off) is not slowed either
				}
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WRAITH_SLOW_TICKS, WRAITH_SLOW_LEVEL));
				level.sendParticles(ParticleTypes.SOUL, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 8, 0.3, 0.4, 0.3, 0.03);
			}
		}
	}

	// ---- G: Veil ----

	private static void veil(ServerPlayer player, XenoformPower.Live live) {
		live.shroud = true;
		addOwnInvisibility(player, 6000);
		// whoever was after you loses the thread
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.7, 0.3, 0.02);
		sound(player, SoundEvents.PHANTOM_AMBIENT, 0.8F, 1.4F);
	}

	private static void unveil(ServerPlayer player, XenoformPower.Live live, boolean fx) {
		if (!live.shroud) {
			return;
		}
		live.shroud = false;
		if (live.possessed < 0) {
			dropOwnInvisibility(player);
		}
		if (fx) {
			player.level().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
			sound(player, SoundEvents.PHANTOM_BITE, 0.8F, 1.2F);
		}
	}

	/** (0.20.21) Only the hidden Invisibility this form added goes; a drunk potion's (shown, with an icon) stays. */
	private static void dropOwnInvisibility(ServerPlayer player) {
		if (OWN_INVIS.remove(player.getUUID())) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	/** (0.20.21) Adds the hidden Invisibility only when there is none; an existing one (a potion) is left untouched and unmarked. */
	private static void addOwnInvisibility(ServerPlayer player, int ticks) {
		if (!player.hasEffect(MobEffects.INVISIBILITY)) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
			OWN_INVIS.add(player.getUUID());
		}
	}

	// ---- V: Banshee Wail ----

	private static void wail(ServerPlayer player, XenoformPower.Live live) {
		live.wail = WAIL_TICKS;
		ServerLevel level = player.level();
		scatter(player);
		for (LivingEntity living : around(player, WAIL_RADIUS)) {
			if (living.entityTags().contains(XenoformState.MINION_TAG)) {
				continue;
			}
			if (!living.hurtServer(level, player.damageSources().playerAttack(player), WAIL_DAMAGE) && living instanceof Player) {
				continue; // (0.20.21) a player the wail could not hurt (pvp off, blocked) is not weakened or blinded either
			}
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WAIL_WEAKNESS_TICKS, 0));
			if (living instanceof Player) {
				living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, WAIL_DARKNESS_TICKS, 0));
				living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0));
			}
		}
		ring(level, ParticleTypes.SCULK_SOUL, player.position().add(0.0, 1.0, 0.0), WAIL_RADIUS * 0.3, 20);
		ring(level, ParticleTypes.SOUL, player.position(), WAIL_RADIUS * 0.6, 32);
		ring(level, ParticleTypes.SOUL, player.position(), WAIL_RADIUS, 48);
		sound(player, SoundEvents.PHANTOM_SWOOP, 2.0F, 0.5F);
		sound(player, SoundEvents.WARDEN_ROAR, 1.0F, 1.6F);
		sound(player, SoundEvents.PHANTOM_AMBIENT, 2.0F, 0.5F);
		Shakes.heavy(player, player.position(), WAIL_RADIUS, 1.2F);
	}

	/** Every mob within reach drops what it was doing and runs. */
	private static void scatter(ServerPlayer player) {
		for (LivingEntity living : around(player, WAIL_RADIUS)) {
			if (!(living instanceof Mob mob) || mob.entityTags().contains(XenoformState.MINION_TAG)) {
				continue;
			}
			mob.setTarget(null);
			Vec3 away = mob.position().subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 to = mob.position().add(away.scale(WAIL_FLEE));
			mob.getNavigation().moveTo(to.x, to.y, to.z, WAIL_FLEE_SPEED);
		}
	}

	// ---- B: Drift ----

	/** Slow true flight: forward along your look, Jump up, sneaking down. */
	private static void drift(ServerPlayer player) {
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(DRIFT_SPEED);
		Input input = player.getLastClientInput();
		double up = input.jump() ? DRIFT_RISE : input.shift() ? -DRIFT_SINK : 0.0;
		player.setDeltaMovement(look.x, up, look.z);
		player.syncVelocity = true;
		player.resetFallDistance();
		ServerLevel level = player.level();
		if (player.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.6, player.getZ(), 1, 0.25, 0.25, 0.25, 0.0);
		}
		if (player.tickCount % 24 == 0) {
			sound(player, SoundEvents.PHANTOM_FLAP, 0.4F, 1.2F);
		}
	}

	// ---- C: Revenant ----

	private static boolean possess(ServerPlayer player, XenoformPower.Live live) {
		LivingEntity target = aimed(player, POSSESS_REACH, e -> e instanceof Mob && !(e instanceof Player) && !e.entityTags().contains(XenoformState.MINION_TAG));
		if (!(target instanceof Mob host) || host.isVehicle()) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 from = player.position();
		if (!player.startRiding(host, true, true)) {
			return false;
		}
		live.possessed = host.getId();
		Work work = WORK.computeIfAbsent(player.getUUID(), id -> new Work());
		work.hostId = host.getUUID();
		work.hostWasNoAi = host.isNoAi(); // (0.20.21) a mob that was NoAI stays NoAI
		host.setNoAi(true);
		host.setTarget(null);
		work.since = level.getGameTime();
		work.wasSwinging = player.isSwinging();
		work.lastStrike = 0L;
		addOwnInvisibility(player, 40);
		level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y + 1.0, from.z, 24, 0.3, 0.6, 0.3, 0.05);
		level.sendParticles(ParticleTypes.SOUL, host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ(), 24, 0.4, 0.5, 0.4, 0.05);
		sound(player, SoundEvents.PHANTOM_DEATH, 1.0F, 1.5F);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.2F);
		return true;
	}

	/** (0.20.21) The host by id, else the vehicle or any level's entity with its UUID (a portal re-creates it with a new id). */
	private static Entity findHost(ServerPlayer player, ServerLevel level, int id, Work work) {
		Entity e = level.getEntity(id);
		if (e != null || work.hostId == null) {
			return e;
		}
		if (player.getVehicle() instanceof Mob m && m.getUUID().equals(work.hostId)) {
			return m;
		}
		for (ServerLevel other : level.getServer().getAllLevels()) {
			Entity found = other.getEntity(work.hostId);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** Out of the creature: it comes to, dazed and marked; you stand beside it. */
	private static void release(ServerPlayer player, XenoformPower.Live live, boolean fx) {
		if (live.possessed < 0) {
			return;
		}
		ServerLevel level = player.level();
		Work work = WORK.computeIfAbsent(player.getUUID(), id -> new Work());
		Entity e = findHost(player, level, live.possessed, work);
		live.possessed = -1;
		if (player.getVehicle() == e || player.getVehicle() instanceof Mob && player.getVehicle().getUUID().equals(work.hostId)) {
			player.stopRiding();
		}
		if (e instanceof Mob host) {
			host.setNoAi(work.hostWasNoAi);
			if (host.isAlive()) {
				host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HOST_WEAK_TICKS, 1));
				host.addEffect(new MobEffectInstance(MobEffects.GLOWING, HOST_GLOW_TICKS, 0));
				host.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HOST_SLOW_TICKS, 2));
			}
		}
		if (!live.shroud) {
			dropOwnInvisibility(player);
		}
		if (fx) {
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
			sound(player, SoundEvents.PHANTOM_SWOOP, 1.0F, 0.8F);
		}
	}

	/** Wearing a host: it goes where you look, jumps for you, strikes what you aim at, and nothing hunts you. */
	private static void wearTick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Work work = WORK.computeIfAbsent(player.getUUID(), id -> new Work());
		if (!(findHost(player, level, live.possessed, work) instanceof Mob host) || !host.isAlive() || player.getVehicle() != host
			|| level.getGameTime() - work.since > POSSESS_TICKS) {
			release(player, live, true);
			return;
		}
		live.possessed = host.getId(); // (0.20.21) follows the host through a portal
		// it goes where you look
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(POSSESS_SPEED * (player.getXRot() > 60.0F ? 0.0 : 1.0));
		Vec3 motion = host.getDeltaMovement();
		double up = motion.y;
		if (host.horizontalCollision && host.onGround()) {
			up = 0.42;
		}
		if (host.isInWater()) {
			up = Math.max(up, 0.1);
		}
		host.setDeltaMovement(look.x, up, look.z);
		host.syncVelocity = true;
		host.setYRot(player.getYRot());
		host.setYBodyRot(player.getYRot());
		host.setYHeadRot(player.getYRot());
		host.setTarget(null);
		player.resetFallDistance();
		host.resetFallDistance();
		// a swing of yours is a strike of the host's
		boolean swinging = player.isSwinging();
		if (swinging && !work.wasSwinging && level.getGameTime() - work.lastStrike >= HOST_STRIKE_EVERY) {
			work.lastStrike = level.getGameTime();
			strike(player, host);
		}
		work.wasSwinging = swinging;
		if (player.tickCount % 10 == 0) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0), m -> m.getTarget() == player)) {
				mob.setTarget(null);
			}
			addOwnInvisibility(player, 40);
		}
		if (player.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
		}
	}

	/** The host's own attack (its {@code doHurtTarget}) on whatever you aim at, from its own width out. */
	private static void strike(ServerPlayer player, Mob host) {
		ServerLevel level = player.level();
		LivingEntity target = aimed(player, HOST_REACH + host.getBbWidth(), e -> e != host && e != player && (!(e instanceof ServerPlayer other) || player.canHarmPlayer(other)) && !e.entityTags().contains(XenoformState.MINION_TAG));
		if (target == null) {
			return;
		}
		target.setInvulnerableTime(0);
		if (host.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
			host.doHurtTarget(level, target);
		} else {
			target.hurtServer(level, player.damageSources().playerAttack(player), HOST_FALLBACK_DAMAGE);
		}
		level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.25, 0.3, 0.25, 0.02);
	}
}
