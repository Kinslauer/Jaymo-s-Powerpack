package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.forms.Slipfin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.37, Ultimates patch 2) Slipfin's Ultimate, Brinecrown: the axolotl grown long and plated (pearly scale plates, a spiked fin
 * sail along the spine and tail, long frilled gill crowns with glowing tips), with a health bar of its own on top of Slipfin's. It is
 * always soaked through and swims faster. Its model is {@code UltimateBrinecrown}.
 * <ul>
 * <li>R, Tide Jet: a longer, wider, harder Bubble Jet that slows what it hits and puts out fire.</li>
 * <li>G, Undertow Slip: Slip that frees you from any hold and throws everything near away from you in a burst of water.</li>
 * <li>V, Torrent Dart: a longer lunge, with a splash wake, that bites every creature on the way.</li>
 * <li>B, Bloom Regrow: Regrow with stronger regeneration, that also mends your allies nearby.</li>
 * <li>C, Brinecrown: a downpour over {@link #STORM_RADIUS} blocks around you for {@link #STORM_TICKS} ticks. Fire in it goes out,
 * foes in it are slowed, and every {@link #WAVE_EVERY} ticks a wave pushes them away.</li>
 * </ul>
 * Nothing here is kept in the world: the downpour is only particles and a timer on the player's {@code Live#bubbleUntil}, and the fires
 * it puts out are gone for good (never rebuilt). {@link #onExit} ends all of it, so a drop, a break, a revert, a death or a quick change
 * leaves nothing running.
 */
public final class Brinecrown extends XenoUltimate {
	// ---- tuning ----
	/** Tide Jet: cooldown, reach, half-width, shove, damage (dry, under water), ticks of Slowness I. */
	public static final int JET_COOLDOWN = 20, JET_SLOW_TICKS = 40;
	public static final double JET_REACH = 18.0, JET_WIDTH = 2.0, JET_PUSH = 1.0;
	public static final float JET_DAMAGE = 5.0F, JET_WET_DAMAGE = 10.0F;
	/** Undertow Slip: cooldown, the burst's reach, its throw out and up, ticks of Speed III. */
	public static final int SLIP_COOLDOWN = 100, SLIP_SPEED_TICKS = 60;
	public static final double SLIP_RADIUS = 3.0, SLIP_OUT = 1.1, SLIP_UP = 0.35;
	/** Torrent Dart: the lunge in water and on land, the reach of the bite, damage, cooldown, ticks the lunge bites for. */
	public static final double DART_WATER = 3.0, DART_LAND = 1.6, DART_HIT_REACH = 2.0;
	public static final float DART_DAMAGE = 8.0F;
	public static final int DART_COOLDOWN = 30, DART_TICKS = 10;
	/** Bloom Regrow: cooldown (30 s), ticks of Regeneration III for you and of Regeneration I for allies, how far allies are mended. */
	public static final int REGROW_COOLDOWN = 600, REGROW_TICKS = 120, ALLY_REGEN_TICKS = 120;
	public static final double ALLY_RADIUS = 6.0;
	/** Brinecrown: the downpour's reach, length (12 s), cooldown (30 s), ticks between waves (2 s), a wave's shove out and up. */
	public static final double STORM_RADIUS = 10.0;
	public static final int STORM_TICKS = 240, STORM_COOLDOWN = 600, WAVE_EVERY = 40;
	public static final double WAVE_PUSH = 1.0, WAVE_UP = 0.2;
	/** The downpour's Slowness II lasts this long after a foe was last in it, and is renewed this often. */
	public static final int STORM_SLOW_TICKS = 30, STORM_SLOW_EVERY = 10;
	/** Particles per tick of the downpour (rain, splashes), how often fires are looked for and how many places are tried each time. */
	public static final int RAIN_DROPS = 10, RAIN_SPLASHES = 5, DOUSE_EVERY = 5, DOUSE_TRIES = 24;
	/** The water-breathing swimmer's dolphin's grace (Slipfin's was II). */
	public static final int SWIM_GRACE = 2;
	/** Water movement efficiency added while swimming, on top of the grace: Slipfin has none, so this is the faster swim (0.21.37). */
	public static final double SWIM_EFFICIENCY = 0.6;
	private static final Identifier SWIM_ID = com.jaymospowers.JaymosPowers.id("xenoform_brinecrown_swim");
	/** Screen shake (degrees) when the downpour breaks, and how far it is felt. */
	public static final float STORM_SHAKE = 0.8F;
	public static final double STORM_SHAKE_RANGE = 14.0;

	public Brinecrown(XenoForm base) {
		super(base, 1.3F, 12.0F, 6.0, 0.15, 0.35, 0.0);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.HEART_OF_THE_SEA, Items.PRISMARINE_SHARD, Items.TROPICAL_FISH, Items.GLOW_BERRIES, Items.AXOLOTL_BUCKET};
	}

	/** The change-in film zooms on these in turn (model part names): the tail and its sail, the rear trunk, the jaw, the gill crowns. */
	@Override
	public String[] morphParts() {
		return new String[] {"tail_0+tail_1+tail_2+tail_3", "rear", "jaw", "gill_0+gill_1+gill_2+gill_3+gill_4+gill_5"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.dry = 0;
		live.horn = 0;
		live.spun.clear();
		live.bubbleUntil = 0L;
		burst(player.level(), ParticleTypes.SPLASH, player.position().add(0.0, 1.0, 0.0), 60, 0.8, 0.2);
		sound(player, SoundEvents.BUCKET_EMPTY_AXOLOTL, 1.4F, 0.7F);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.horn = 0;
		live.spun.clear();
		live.bubbleUntil = 0L;
		live.dry = 0;
		AttributeInstance swim = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
		if (swim != null) {
			swim.removeModifier(SWIM_ID);
		}
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_DROWNING);
	}

	/** The dart's lunge is faster than the movement check allows for. */
	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.horn > 0;
	}

	// ---- who may be hurt ----

	/** A player the attacker may not hurt (pvp off) is never hit, pushed or slowed. */
	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}

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

	/** Hurts {@code target} for {@code damage}; true when the blow landed (or the target is no player, who has no pvp to refuse it). */
	private static boolean hit(ServerPlayer player, LivingEntity target, float damage) {
		return target.hurtServer(player.level(), player.damageSources().playerAttack(player), damage) || !(target instanceof ServerPlayer);
	}

	/** Puts out a fire block at {@code pos} if the player may change blocks there (never in adventure mode, never where interaction is refused). */
	private static boolean douse(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!player.mayBuild() || !level.mayInteract(player, pos) || !(level.getBlockState(pos).getBlock() instanceof BaseFireBlock)) {
			return false;
		}
		level.removeBlock(pos, false);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.02);
		sound(player, SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.6F, 1.2F);
		return true;
	}

	// ---- keys ----

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					jet(player, level);
					cooldown(player, live, R, JET_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					undertow(player, level);
					cooldown(player, live, G, SLIP_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					dart(player, live);
					cooldown(player, live, V, DART_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					regrow(player, level);
					cooldown(player, live, B, REGROW_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: Brinecrown. The downpour starts; tapping it again while it falls does nothing. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		long now = player.level().getGameTime();
		if (live.bubbleUntil > now || !ready(player, live, C)) {
			return;
		}
		live.bubbleUntil = now + STORM_TICKS;
		sound(player, SoundEvents.BUCKET_EMPTY_AXOLOTL, 2.0F, 0.6F);
		sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.6F);
		Shakes.heavy(player, player.position(), STORM_SHAKE_RANGE, STORM_SHAKE);
		cooldown(player, live, C, STORM_COOLDOWN);
	}

	// ---- R: Tide Jet ----

	private static void jet(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		boolean wet = player.isUnderWater();
		float damage = wet ? JET_WET_DAMAGE : JET_DAMAGE;
		for (LivingEntity e : foes(player, JET_REACH)) {
			Vec3 c = e.getBoundingBox().getCenter().subtract(eye);
			double along = c.dot(look);
			if (along <= 0.0 || along > JET_REACH || c.subtract(look.scale(along)).length() > JET_WIDTH + e.getBbWidth() * 0.5 || !player.hasLineOfSight(e)) {
				continue;
			}
			e.clearFire();
			if (!hit(player, e, damage)) {
				continue;
			}
			e.setDeltaMovement(e.getDeltaMovement().add(look.scale(JET_PUSH * (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)))));
			e.syncVelocity = true;
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, JET_SLOW_TICKS, 0, false, true));
		}
		// the jet ends at the first wall; fires on its line go out (only where it may change blocks)
		Vec3 end = eye.add(look.scale(JET_REACH));
		BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? JET_REACH : Math.sqrt(wall.getLocation().distanceToSqr(eye));
		Set<BlockPos> seen = new HashSet<>();
		for (int i = 1; i <= 18; i++) {
			double d = i * JET_REACH / 18.0;
			if (d > reach) {
				break;
			}
			Vec3 p = eye.add(look.scale(d));
			level.sendParticles(wet ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, p.x, p.y, p.z, 6, 0.2, 0.2, 0.2, 0.06);
			BlockPos at = BlockPos.containing(p);
			if (seen.add(at)) {
				douse(player, level, at);
			}
		}
		sound(player, wet ? SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE : SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2F, 1.2F);
	}

	// ---- G: Undertow Slip ----

	private static void undertow(ServerPlayer player, ServerLevel level) {
		Slipfin.free(player);
		for (LivingEntity e : foes(player, SLIP_RADIUS)) {
			double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			shove(e, player.position(), SLIP_OUT * resist, SLIP_UP * resist);
		}
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, SLIP_SPEED_TICKS, 2, false, true));
		Vec3 at = player.position().add(0.0, 0.6, 0.0);
		burst(level, ParticleTypes.SPLASH, at, 60, 0.6, 0.15);
		ring(level, ParticleTypes.BUBBLE_POP, at, SLIP_RADIUS, 24);
		sound(player, SoundEvents.SLIME_SQUISH, 1.0F, 1.4F);
		sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 1.0F);
		player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.xenoform.slipfin.slip")
			.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
	}

	// ---- V: Torrent Dart ----

	private static void dart(ServerPlayer player, XenoformPower.Live live) {
		Vec3 look = player.getLookAngle();
		boolean wet = player.isInWater();
		double speed = wet ? DART_WATER : DART_LAND;
		player.setDeltaMovement(look.x * speed, wet ? look.y * speed : Math.max(0.4, look.y * speed), look.z * speed);
		player.syncVelocity = true;
		live.horn = DART_TICKS;
		live.spun.clear();
		sound(player, wet ? SoundEvents.DOLPHIN_SPLASH : SoundEvents.AXOLOTL_SPLASH, 1.2F, 1.2F);
	}

	// ---- B: Bloom Regrow ----

	private static void regrow(ServerPlayer player, ServerLevel level) {
		com.jaymospowers.core.wounds.Breaks.clearAll(player);
		com.jaymospowers.core.Dismember.regrow(player, false);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGROW_TICKS, 2, false, true));
		burst(level, ParticleTypes.HEART, player.position().add(0.0, 1.0, 0.0), 8, 0.4, 0.05);
		// allies nearby (team, own pets) bloom too
		AABB box = player.getBoundingBox().inflate(ALLY_RADIUS);
		for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && spared(player, e) && e.distanceToSqr(player) <= ALLY_RADIUS * ALLY_RADIUS)) {
			ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ALLY_REGEN_TICKS, 0, false, true));
			level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
		}
		ring(level, ParticleTypes.HAPPY_VILLAGER, player.position().add(0.0, 0.3, 0.0), ALLY_RADIUS, 36);
		sound(player, SoundEvents.AXOLOTL_SPLASH, 1.0F, 0.8F);
	}

	// ---- the tick ----

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// always soaked through: it never dries out
		live.dry = 0;
		// slick: whatever grabs it can't keep hold
		if (player.tickCount % 5 == 0 && (com.jaymospowers.core.Carry.isCarried(player) || player.getAttached(com.jaymospowers.core.wounds.Tripped.TRIP) != null)) {
			Slipfin.free(player);
		}
		// the dart bites every creature it passes through, each once
		if (live.horn > 0) {
			live.horn--;
			Vec3 p = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
			level.sendParticles(player.isUnderWater() ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, p.x, p.y, p.z, 12, 0.35, 0.3, 0.35, 0.08);
			for (LivingEntity e : foes(player, DART_HIT_REACH)) {
				if (live.spun.add(e.getId())) {
					hit(player, e, DART_DAMAGE);
					sound(player, SoundEvents.AXOLOTL_ATTACK, 1.0F, 1.0F);
				}
			}
			if (live.horn == 0) {
				live.spun.clear();
			}
		}
		AttributeInstance swim = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
		if (swim != null) {
			if (player.isInWater()) {
				swim.addOrUpdateTransientModifier(new AttributeModifier(SWIM_ID, SWIM_EFFICIENCY, AttributeModifier.Operation.ADD_VALUE));
			} else {
				swim.removeModifier(SWIM_ID);
			}
		}
		if (player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 10, SWIM_GRACE - 1, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 10, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
			player.setAirSupply(player.getMaxAirSupply());
		}
		if (live.bubbleUntil > 0L) {
			downpour(player, live, level);
		}
	}

	/** One tick of the downpour. It follows the player and ends with the timer (or at once if the clock is somehow out of step). */
	private static void downpour(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		long now = level.getGameTime();
		long left = live.bubbleUntil - now;
		if (left <= 0L || left > STORM_TICKS) {
			live.bubbleUntil = 0L;
			sound(player, SoundEvents.AXOLOTL_SPLASH, 1.0F, 0.7F);
			return;
		}
		RandomSource random = level.getRandom();
		// the rain: falling water from above and splashes where it lands
		for (int i = 0; i < RAIN_DROPS; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(random.nextDouble()) * STORM_RADIUS;
			level.sendParticles(ParticleTypes.FALLING_WATER, player.getX() + Math.cos(a) * r, player.getY() + 3.0 + random.nextDouble() * 3.0, player.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		for (int i = 0; i < RAIN_SPLASHES; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(random.nextDouble()) * STORM_RADIUS;
			level.sendParticles(ParticleTypes.SPLASH, player.getX() + Math.cos(a) * r, player.getY() + 0.1, player.getZ() + Math.sin(a) * r, 1, 0.1, 0.0, 0.1, 0.0);
		}
		if (left % 20 == 0) {
			sound(player, SoundEvents.WEATHER_RAIN, 0.8F, 1.0F);
		}
		// fire in it goes out: the player, everything burning in it and fire blocks the player may put out
		player.clearFire();
		if (player.tickCount % DOUSE_EVERY == 0) {
			AABB box = player.getBoundingBox().inflate(STORM_RADIUS);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.isOnFire() && e.distanceToSqr(player) <= STORM_RADIUS * STORM_RADIUS)) {
				e.clearFire();
			}
			BlockPos centre = player.blockPosition();
			for (int i = 0; i < DOUSE_TRIES; i++) {
				BlockPos at = centre.offset(random.nextInt(21) - 10, random.nextInt(8) - 3, random.nextInt(21) - 10);
				if (at.distSqr(centre) <= STORM_RADIUS * STORM_RADIUS) {
					douse(player, level, at);
				}
			}
		}
		// foes in it are slowed (II), and a wave every couple of seconds pushes them out
		if (player.tickCount % STORM_SLOW_EVERY == 0 || left % WAVE_EVERY == 0 && left < STORM_TICKS) {
			boolean wave = left % WAVE_EVERY == 0 && left < STORM_TICKS;
			for (LivingEntity e : foes(player, STORM_RADIUS)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STORM_SLOW_TICKS, 1, false, true));
				if (wave) {
					double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					shove(e, player.position(), WAVE_PUSH * resist, WAVE_UP * resist);
				}
			}
			if (wave) {
				Vec3 at = player.position().add(0.0, 0.3, 0.0);
				ring(level, ParticleTypes.SPLASH, at, STORM_RADIUS * 0.3, 16);
				ring(level, ParticleTypes.SPLASH, at, STORM_RADIUS * 0.6, 28);
				ring(level, ParticleTypes.SPLASH, at, STORM_RADIUS, 44);
				sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.5F, 0.6F);
				sound(player, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.0F, 0.8F);
				Shakes.heavy(player, at, STORM_SHAKE_RANGE, STORM_SHAKE);
			}
		}
	}
}
