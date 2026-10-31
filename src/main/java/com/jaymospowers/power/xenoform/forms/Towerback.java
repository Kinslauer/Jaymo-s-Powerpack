package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.119, Scan G) Towerback, the true giant: a hulk of grey stone, moss on its shoulders and boulders for pauldrons, a
 * heavy brow over two pale glowing eyes, and a broken watchtower grown out of its back. 120 health, fists of 12, slow and
 * huge: five times your size (about 9 blocks). Tap Z to rise to monolith-giant size, {@link #MONOLITH}x (about 29
 * blocks, the scale attribute's limit; the Aether Giants are 30.6), under open sky only; tap again to come back down.
 * Up there it walks through whatever is in its way, everything above the ground it stands on ({@link #trample}).
 * <p>
 * Every step stomps, as the Giants' do ({@code kingdoms/remnants/AetherGiant}, copied here so Kingdoms needn't be
 * loaded): the ground cracks round the foot and a shockwave runs out along it, hurting whoever stands on the ground in
 * its way; jump it and it misses you. Falls never hurt it: a long drop lands as a double stomp. Scanned from the
 * Highfield, Arctic or Irradiated Giant, or the Kingdoms war elephant ({@code XenoDna}).
 * <ul>
 * <li>R, Grab: as the Giants' grab. It rears back with a roar ({@link #WINDUP} ticks; get out of reach and the hand
 * closes on nothing), snatches what it aimed at, lifts it to its chest and squeezes, then slams it into the ground. The
 * letterbox film plays for both.</li>
 * <li>G, Stomp: one great stamp; its shockwave runs out twice as far as a step's and throws everything up.</li>
 * <li>V, Swat: a backhand across the front of it that flings everything there aside.</li>
 * <li>B, Roar: monsters round it are weakened and slowed, and lose their nerve (and their target).</li>
 * </ul>
 */
public final class Towerback extends XenoForm {
	// ---- tuning ----
	/** Its sizes: the form's own (5x, about 9 blocks) and, Z tapped, the monolith Giants' (the scale attribute's limit). */
	public static final float BIG = 5.0F, MONOLITH = 16.0F;
	/** At monolith size: health on top, hitting power on top (both added), and this much faster (share of its speed). */
	public static final double MONOLITH_HEALTH = 80.0, MONOLITH_DAMAGE = 8.0, MONOLITH_SPEED = 0.6, MONOLITH_STEP = 3.0;
	/** How long between size changes (ticks). */
	public static final int SIZE_COOLDOWN = 60;
	/** Its stride: a step every this many blocks walked per scale (5x: ~3.5 blocks; 16x: ~11). */
	public static final double STRIDE_PER_SCALE = 0.7;
	/**
	 * Each step's shockwave, as the Giants': it runs out along the ground this fast (blocks a tick, per scale) to this
	 * far (per scale), hurting whoever stands on the ground in its way, most near the foot and this share of that at the
	 * edge, with a jolt up and away. It follows steps up or down this tall.
	 */
	public static final double WAVE_SPEED_PER_SCALE = 0.25, WAVE_RANGE_PER_SCALE = 1.6, WAVE_JOLT_UP = 0.35, WAVE_JOLT_AWAY = 0.4;
	public static final float WAVE_DAMAGE = 5.0F, WAVE_EDGE = 0.5F;
	public static final int WAVE_CLIMB = 3;
	/** The shake each step gives (strength per scale, felt this far per scale). */
	public static final float STEP_SHAKE = 0.18F;
	public static final double SHAKE_RANGE_PER_SCALE = 6.0;
	/** A drop this far (blocks) lands as a double stomp. */
	public static final double LANDING = 4.0;
	/** At monolith size: every this many ticks of walking it smashes through what's in its way; this share drops. */
	public static final int TRAMPLE_EVERY = 5, TRAMPLE_MAX = 400;
	public static final float TRAMPLE_DROPS = 0.25F;
	/** The grab, as the Giants' (ticks): the wind-up, the snatch, the hold and squeeze, the slam, straightening up. */
	public static final int WINDUP = 30, SNATCH = 8, HOLD = 40, SLAM = 8, RECOVER = 20;
	public static final int TOTAL = WINDUP + SNATCH + HOLD + SLAM + RECOVER;
	public static final int LIFT_TICKS = 10, SQUEEZE_EVERY = 10, GRAB_COOLDOWN = 300;
	/** Its hand reaches this far per scale (plus half its width), aims this much further, and this far up or down per scale. */
	public static final double GRAB_REACH_PER_SCALE = 0.9, GRAB_AIM = 6.0, GRAB_CLIMB_PER_SCALE = 0.8;
	public static final float SQUEEZE_DAMAGE = 4.0F, SLAM_DAMAGE = 30.0F;
	public static final double SLAM_THROW = 1.2, FILM_RANGE = 48.0;
	/** Where its right hand holds its catch: shares of its width ahead and to the side, of its height up. */
	public static final double HAND_AHEAD = 0.55, HAND_SIDE = 0.45, HAND_UP = 0.55;
	/** Stomp (G): its wave runs this many times as far as a step's and hits this hard; thrown this high. */
	public static final double STOMP_RANGE = 2.0, STOMP_UP = 0.9;
	public static final float STOMP_DAMAGE = 10.0F, STOMP_SHAKE = 0.3F;
	public static final int STOMP_COOLDOWN = 120;
	/** Swat (V): reach per scale (plus half its width), damage, how hard and high it flings. */
	public static final double SWAT_REACH_PER_SCALE = 1.2, SWAT_THROW = 2.4, SWAT_UP = 0.6, SWAT_ARC = 0.2;
	public static final float SWAT_DAMAGE = 12.0F;
	public static final int SWAT_COOLDOWN = 80;
	/** Roar (B): its reach per scale, how long the weakness and slowness last, its cooldown. */
	public static final double ROAR_RANGE_PER_SCALE = 3.0;
	public static final int ROAR_TICKS = 160, ROAR_COOLDOWN = 400;
	/** What it hits harder at monolith size (its damage, squeeze, slam and waves times this). */
	public static final float MONOLITH_HIT = 1.6F;

	private static final Identifier SIZE_ID = com.jaymospowers.JaymosPowers.id("xenoform_towerback_size");
	private static final Identifier HEALTH_ID = com.jaymospowers.JaymosPowers.id("xenoform_towerback_health");
	private static final Identifier DAMAGE_ID = com.jaymospowers.JaymosPowers.id("xenoform_towerback_damage");
	private static final Identifier SPEED_ID = com.jaymospowers.JaymosPowers.id("xenoform_towerback_speed");
	private static final Identifier STEP_ID = com.jaymospowers.JaymosPowers.id("xenoform_towerback_step");

	/** One step's shockwave running out along the ground. */
	public static final class Wave {
		final Vec3 at;
		final double range, speed, up;
		final float damage;
		int age;
		final IntSet hit = new IntOpenHashSet();

		Wave(Vec3 at, double range, double speed, float damage, double up) {
			this.at = at;
			this.range = range;
			this.speed = speed;
			this.damage = damage;
			this.up = up;
		}
	}

	public Towerback() {
		super("towerback", BIG, 120.0F, 12.0, -0.2, 1.0, 1.0, Items.CHISELED_STONE_BRICKS, 0x8A8F96);
	}

	@Override
	public boolean scans(Entity creature) {
		// only by id: the Giants and the war elephant ({@code XenoDna})
		return false;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		endGrab(player, live);
		monolith(player, false);
		live.towering = false;
		live.waves.clear();
	}

	@Override
	public boolean onModeTap(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < live.sizeNext || live.grabStart >= 0L) {
			return true;
		}
		if (!live.towering && !openSky(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.towerback.no_room").withStyle(ChatFormatting.GRAY));
			return true;
		}
		live.towering = !live.towering;
		monolith(player, live.towering);
		live.sizeNext = now + SIZE_COOLDOWN;
		live.strideAt = player.moveDist;
		sound(player, SoundEvents.RAVAGER_ROAR, 3.0F, live.towering ? 0.4F : 0.7F);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.5F);
		burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, player.position().add(0.0, 0.5, 0.0), 30, player.getBbWidth(), 0.03);
		footfall(player, live, level, STOMP_RANGE, STOMP_DAMAGE * 0.5F, STOMP_UP * 0.5);
		return true;
	}

	/** Nothing but sky over its head (leaves don't count): room to rise to monolith size. */
	private static boolean openSky(ServerPlayer player) {
		ServerLevel level = player.level();
		AABB box = player.getBoundingBox();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				double x = dx == 0 ? player.getX() : dx < 0 ? box.minX : box.maxX, z = dz == 0 ? player.getZ() : dz < 0 ? box.minZ : box.maxZ;
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0.0, z));
				if (top.getY() > player.getY() + 1.0) {
					return false;
				}
			}
		}
		return true;
	}

	/** Its monolith size on or off: the scale, the health and hitting power on top, the longer stride and step. */
	private static void monolith(ServerPlayer player, boolean on) {
		if (on) {
			modify(player, Attributes.SCALE, SIZE_ID, MONOLITH - BIG, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			modify(player, Attributes.MAX_HEALTH, HEALTH_ID, MONOLITH_HEALTH, AttributeModifier.Operation.ADD_VALUE);
			modify(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, MONOLITH_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
			modify(player, Attributes.MOVEMENT_SPEED, SPEED_ID, MONOLITH_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			modify(player, Attributes.STEP_HEIGHT, STEP_ID, MONOLITH_STEP, AttributeModifier.Operation.ADD_VALUE);
			player.heal((float)MONOLITH_HEALTH);
		} else {
			for (var attribute : java.util.List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED,
				Attributes.STEP_HEIGHT)) {
				AttributeInstance instance = player.getAttribute(attribute);
				if (instance != null) {
					instance.removeModifier(SIZE_ID);
					instance.removeModifier(HEALTH_ID);
					instance.removeModifier(DAMAGE_ID);
					instance.removeModifier(SPEED_ID);
					instance.removeModifier(STEP_ID);
				}
			}
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
		player.refreshDimensions();
	}

	private static void modify(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id,
		double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** How big it is now (its scale: 5, or 16 at monolith size). */
	private static float size(ServerPlayer player) {
		return Math.max(1.0F, player.getScale());
	}

	/** Its hits at monolith size land this much harder. */
	private static float heft(XenoformPower.Live live) {
		return live.towering ? MONOLITH_HIT : 1.0F;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL)) {
			// it lands as a stomp
			footfall(player, live, player.level(), 1.0, WAVE_DAMAGE, WAVE_JOLT_UP);
			footfall(player, live, player.level(), 1.0, WAVE_DAMAGE, WAVE_JOLT_UP);
			return false;
		}
		return true;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down || live.grabStart >= 0L) {
			return;
		}
		ServerLevel level = player.level();
		float size = size(player);
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R)) {
					return;
				}
				LivingEntity target = aimed(player, handReach(player) + GRAB_AIM, e -> e instanceof LivingEntity l && canGrab(player, l));
				if (target == null) {
					return;
				}
				beginGrab(player, live, level, target);
				cooldown(player, live, R, GRAB_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					player.swing(InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
					footfall(player, live, level, STOMP_RANGE, STOMP_DAMAGE, STOMP_UP);
					Shakes.heavy(player, player.position(), SHAKE_RANGE_PER_SCALE * size, STOMP_SHAKE * size);
					sound(player, SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.4F);
					cooldown(player, live, G, STOMP_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					swat(player, live, level, size);
					cooldown(player, live, V, SWAT_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					roar(player, level, size);
					cooldown(player, live, B, ROAR_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** A backhand across the front of it: everything there is struck and flung aside. */
	private static void swat(ServerPlayer player, XenoformPower.Live live, ServerLevel level, float size) {
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 facing = Vec3.directionFromRotation(0.0F, player.getYRot());
		// the swing goes right to left: thrown off to its left
		Vec3 aside = facing.yRot(Mth.HALF_PI);
		double reach = player.getBbWidth() * 0.5 + SWAT_REACH_PER_SCALE * size;
		for (LivingEntity e : around(player, reach)) {
			Vec3 to = e.position().subtract(player.position()).horizontal();
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(facing) < SWAT_ARC || Math.abs(e.getY() - player.getY()) > player.getBbHeight()) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), SWAT_DAMAGE * heft(live));
			Vec3 fling = aside.add(facing.scale(0.5)).normalize().scale(SWAT_THROW);
			e.setDeltaMovement(fling.x, SWAT_UP, fling.z);
			e.syncVelocity = true;
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.4, 0.4, 0.4, 0.2);
		}
		for (int i = 1; i <= 8; i++) {
			Vec3 p = player.position().add(0.0, player.getBbHeight() * 0.4, 0.0).add(facing.yRot((4 - i) * 0.25F).scale(reach * 0.8));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.4F);
		sound(player, SoundEvents.RAVAGER_ATTACK, 2.0F, 0.5F);
	}

	/** A roar: the monsters round it are weakened and slowed, and lose their nerve. */
	private static void roar(ServerPlayer player, ServerLevel level, float size) {
		double range = ROAR_RANGE_PER_SCALE * size;
		for (LivingEntity e : around(player, range)) {
			if (!(e instanceof Enemy) && !(e instanceof Mob mob && mob.getTarget() == player)) {
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_TICKS, 1), player);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROAR_TICKS, 2), player);
			if (e instanceof Mob mob) {
				mob.setTarget(null);
			}
			shove(e, player.position(), 0.6, 0.2);
		}
		Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(player.getBbWidth() * 0.6));
		for (int i = 1; i <= 4; i++) {
			Vec3 p = mouth.add(player.getLookAngle().scale(i * size * 0.6));
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 30, range * 0.3, 0.2, range * 0.3, 0.02);
		sound(player, SoundEvents.WARDEN_ROAR, 3.0F, 0.5F);
		sound(player, SoundEvents.RAVAGER_ROAR, 3.0F, 0.4F);
		Shakes.heavy(player, player.position(), range, 0.6F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (live.grabStart >= 0L) {
			grabTick(player, live, level, now);
		} else {
			// every stride shakes the ground (sprinting it skims over it instead)
			double stride = STRIDE_PER_SCALE * size(player);
			if (player.moveDist - live.strideAt >= stride) {
				live.strideAt = player.moveDist;
				if (player.onGround() && !player.isSprinting()) {
					footfall(player, live, level, 1.0, WAVE_DAMAGE, WAVE_JOLT_UP);
				}
			}
			if (live.towering && now % TRAMPLE_EVERY == 0 && player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
				trample(player, level);
			}
		}
		runWaves(player, live, level);
	}

	/**
	 * One of its steps (or a stomp, {@code range}/{@code damage}/{@code up} bigger): the foot (left, then right) comes
	 * down, the ground cracks round it, the screen shakes, and a shockwave starts out along the ground.
	 */
	private static void footfall(ServerPlayer player, XenoformPower.Live live, ServerLevel level, double range, float damage, double up) {
		float size = size(player);
		live.leftFoot = !live.leftFoot;
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double side = (live.leftFoot ? 1.0 : -1.0) * player.getBbWidth() * 0.25;
		Vec3 foot = player.position().add(Math.cos(yaw) * side, 0.0, Math.sin(yaw) * side);
		BlockPos under = BlockPos.containing(foot.x, foot.y - 0.5, foot.z);
		BlockState ground = level.getBlockState(under);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), foot.x, foot.y + 0.1, foot.z, 40, size * 0.3, 0.1, size * 0.3, 0.25);
			level.playSound(null, foot.x, foot.y, foot.z, ground.getSoundType().getBreakSound(), net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 0.5F);
		}
		level.sendParticles(ParticleTypes.POOF, foot.x, foot.y + 0.2, foot.z, 8, size * 0.25, 0.1, size * 0.25, 0.04);
		level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.RAVAGER_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 3.0F,
			0.35F + player.getRandom().nextFloat() * 0.1F);
		GroundCracks.crack(level, under, player.getRandom(), Math.max(2, Math.round(size * 0.8F)));
		Shakes.heavy(player, foot, SHAKE_RANGE_PER_SCALE * size, STEP_SHAKE * size);
		live.waves.add(new Wave(foot, WAVE_RANGE_PER_SCALE * size * range, WAVE_SPEED_PER_SCALE * size, damage * heft(live), up));
	}

	/**
	 * Its shockwaves run out: everyone the wave front passes this tick who stands on the ground near its level (not
	 * jumping, flying or riding) is hurt, less towards its edge, and jolted up and away. A ring of dust marks the front.
	 */
	private static void runWaves(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		for (var it = live.waves.iterator(); it.hasNext();) {
			Wave wave = it.next();
			double from = wave.speed * wave.age, to = Math.min(wave.speed * ++wave.age, wave.range);
			AABB box = new AABB(wave.at.x - to, wave.at.y - WAVE_CLIMB, wave.at.z - to, wave.at.x + to, wave.at.y + WAVE_CLIMB, wave.at.z + to);
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && e.onGround() && !e.isPassenger()
				&& !e.isSpectator() && !(e instanceof ArmorStand) && !spared(player, e) && !(e instanceof Player p && p.isCreative()))) {
				double d = Math.sqrt(Mth.square(hit.getX() - wave.at.x) + Mth.square(hit.getZ() - wave.at.z));
				if (d > to || d < from - 1.0 || !wave.hit.add(hit.getId())) {
					continue;
				}
				float share = 1.0F - (1.0F - WAVE_EDGE) * (float)(d / wave.range);
				hit.hurtServer(level, player.damageSources().playerAttack(player), wave.damage * share);
				Vec3 away = d < 0.01 ? Vec3.ZERO : new Vec3(hit.getX() - wave.at.x, 0.0, hit.getZ() - wave.at.z).scale(WAVE_JOLT_AWAY / d);
				hit.push(away.x, wave.up, away.z);
				hit.needsSync = true;
			}
			int points = Math.max(12, (int)(to * Mth.TWO_PI / 1.5));
			for (int i = 0; i < points; i++) {
				double a = (i + level.getRandom().nextDouble()) * Mth.TWO_PI / points;
				level.sendParticles(ParticleTypes.POOF, wave.at.x + Math.cos(a) * to, wave.at.y + 0.15, wave.at.z + Math.sin(a) * to, 1, 0.05, 0.02, 0.05, 0.02);
			}
			if (to >= wave.range) {
				it.remove();
			}
		}
	}

	/**
	 * At monolith size whatever it walks into, it walks through: everything above the ground it stands on that can be
	 * broken (never bedrock and the like, never chests or other blocks that hold things), a share of it dropped.
	 */
	private static void trample(ServerPlayer player, ServerLevel level) {
		if (!player.mayBuild()) {
			return;
		}
		Vec3 ahead = player.getDeltaMovement().horizontal().normalize();
		AABB box = player.getBoundingBox().move(ahead.x, 0.0, ahead.z).inflate(0.5, 0.0, 0.5);
		int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY) + 1, z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX), y1 = Mth.floor(box.maxY), z1 = Mth.floor(box.maxZ);
		int broken = 0;
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F
				|| !level.mayInteract(player, pos)) {
				continue;
			}
			level.destroyBlock(pos, player.getRandom().nextFloat() < TRAMPLE_DROPS, player);
			if (++broken >= TRAMPLE_MAX) {
				return;
			}
		}
	}

	// ---- the grab, as the Giants' -------------------------------------------------------------------------------------

	/** What it may grab: anything living, not too big to lift, not riding, not its own friends, not a creative player. */
	private static boolean canGrab(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && !e.isRemoved() && !(e instanceof ArmorStand) && !e.isPassenger() && !spared(player, e)
			&& !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && e.getBbHeight() <= player.getBbHeight() * 0.5F;
	}

	private static double handReach(ServerPlayer player) {
		return player.getBbWidth() * 0.5 + GRAB_REACH_PER_SCALE * size(player);
	}

	private static boolean inHandsReach(ServerPlayer player, LivingEntity prey) {
		double reach = handReach(player), dx = prey.getX() - player.getX(), dz = prey.getZ() - player.getZ();
		return dx * dx + dz * dz <= reach * reach && Math.abs(prey.getY() - player.getY()) <= GRAB_CLIMB_PER_SCALE * size(player);
	}

	/** It rears back for the grab: a roar, its hand drawn up behind it; everyone sees it coming. */
	private static void beginGrab(ServerPlayer player, XenoformPower.Live live, ServerLevel level, LivingEntity target) {
		long now = level.getGameTime();
		live.grabStart = now;
		live.grabPrey = target.getId();
		live.grabHolding = false;
		live.grabFrom = null;
		live.grabTo = null;
		player.setAttached(XenoformState.GRAB, now);
		// it stands its ground while it grabs
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TOTAL, 9, false, false));
		sound(player, SoundEvents.WARDEN_ROAR, 3.0F, 0.5F);
		sound(player, SoundEvents.RAVAGER_ROAR, 2.5F, 0.45F);
	}

	/** Its grab, tick by tick: the wind-up, the snatch, the hold and squeeze, the slam, and straightening up. */
	private static void grabTick(ServerPlayer player, XenoformPower.Live live, ServerLevel level, long now) {
		int t = (int)(now - live.grabStart);
		if (t < 0 || t >= TOTAL) {
			endGrab(player, live);
			return;
		}
		LivingEntity prey = live.grabHolding && level.getEntity(live.grabPrey) instanceof LivingEntity l && l.isAlive() && !l.isRemoved() ? l : null;
		if (t < WINDUP) {
			if (t % 3 == 0) {
				Vec3 hand = hand(player, 0.0);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, hand.x, hand.y, hand.z, 3, 0.6, 0.6, 0.6, 0.02);
			}
			if (t == WINDUP - 1) {
				snatch(player, live, level);
			}
			return;
		}
		if (prey == null) {
			// its hand closed on nothing (or its catch is gone): it straightens up
			if (t < WINDUP + SNATCH + HOLD + SLAM) {
				live.grabStart = now - (WINDUP + SNATCH + HOLD + SLAM);
				live.grabHolding = false;
				player.setAttached(XenoformState.GRAB, live.grabStart);
				player.removeEffect(MobEffects.SLOWNESS);
			}
			return;
		}
		Vec3 from = live.grabFrom != null ? live.grabFrom : prey.position();
		int held = t - WINDUP - SNATCH;
		if (held < 0) {
			carry(player, prey, from);
			return;
		}
		if (held < HOLD) {
			double lift = Math.min(1.0, held / (double)LIFT_TICKS);
			lift = lift * lift * (3.0 - 2.0 * lift);
			carry(player, prey, from.lerp(hand(player, 0.0), lift));
			if (held > 0 && held % SQUEEZE_EVERY == 0) {
				prey.hurtServer(level, player.damageSources().playerAttack(player), SQUEEZE_DAMAGE * heft(live));
				level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.RAVAGER_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 0.3F);
				level.sendParticles(ParticleTypes.CRIT, prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ(), 12, 0.6, 0.6, 0.6, 0.1);
			}
			if (held == HOLD - 1) {
				live.grabTo = groundAhead(player, level);
			}
			return;
		}
		int slam = held - HOLD;
		if (slam < SLAM) {
			Vec3 to = live.grabTo != null ? live.grabTo : groundAhead(player, level);
			double drop = Math.min(1.0, (slam + 1) / (double)SLAM);
			carry(player, prey, hand(player, 0.0).lerp(to, drop * drop));
			if (slam == SLAM - 1) {
				slam(player, live, level, prey, to);
			}
		}
	}

	/** Its hand closes: on its target if that's still within reach, else on nothing. */
	private static void snatch(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		LivingEntity target = level.getEntity(live.grabPrey) instanceof LivingEntity l ? l : null;
		if (target == null || !canGrab(player, target) || !inHandsReach(player, target)) {
			Vec3 miss = hand(player, player.getBbWidth() * 0.3);
			level.playSound(null, miss.x, miss.y, miss.z, SoundEvents.RAVAGER_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 0.5F);
			level.sendParticles(ParticleTypes.POOF, miss.x, player.getY() + 0.5, miss.z, 20, 1.5, 0.3, 1.5, 0.05);
			return;
		}
		live.grabHolding = true;
		live.grabFrom = target.position();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.6F);
		if (target instanceof Player p) {
			p.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ape_grabbed", player.getDisplayName()).withStyle(ChatFormatting.RED));
		}
		// the film: its catch (a player) sees the giant's face come down on them, the giant watches over its shoulder,
		// and everyone near gets the bars (the catch's own film first, so the bars don't take it over)
		int ticks = SNATCH + HOLD + SLAM;
		if (target instanceof ServerPlayer preyPlayer) {
			UltimateFilm.play(preyPlayer, UltimateFilm.PUSH_IN, ticks, player.position(), player, 0.0);
		}
		UltimateFilm.play(player, UltimateFilm.PUSH_IN, ticks, target.position(), target, FILM_RANGE);
	}

	/** Its catch slammed into the ground at {@code at}: the blow, the ground thrown up and cracked, its catch flung clear. */
	private static void slam(ServerPlayer player, XenoformPower.Live live, ServerLevel level, LivingEntity prey, Vec3 at) {
		prey.resetFallDistance();
		prey.hurtServer(level, player.damageSources().playerAttack(player), SLAM_DAMAGE * heft(live));
		Vec3 away = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(SLAM_THROW);
		prey.setDeltaMovement(away.x, 0.5, away.z);
		prey.needsSync = true;
		prey.syncVelocity = true;
		live.grabHolding = false;
		BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
		BlockState ground = level.getBlockState(under);
		double dust = Math.max(1.5, player.getBbWidth() * 0.5);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, 80, dust, 0.3, dust, 0.3);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 1.0, 0.2, 1.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 2.5F, 0.5F);
		GroundCracks.crack(level, under, level.getRandom(), Math.max(2, (int)Math.round(dust)));
		Shakes.heavy(player, at, FILM_RANGE, 1.2F);
	}

	/** Its catch carried to {@code to} (its feet), held still and facing it. */
	private static void carry(ServerPlayer player, LivingEntity prey, Vec3 to) {
		if (prey instanceof ServerPlayer caught) {
			caught.teleportTo(player.level(), to.x, to.y, to.z, Relative.ROTATION, 0.0F, 0.0F, false);
		} else {
			prey.setPos(to.x, to.y, to.z);
			Vec3 face = player.getEyePosition().subtract(prey.getBoundingBox().getCenter());
			float yaw = (float)(Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
			prey.setYRot(yaw);
			prey.setYBodyRot(yaw);
			prey.setYHeadRot(yaw);
		}
		prey.setDeltaMovement(Vec3.ZERO);
		prey.needsSync = true;
		prey.syncVelocity = true;
		prey.resetFallDistance();
	}

	/** Where its right hand holds its catch (the catch's feet), {@code ahead} blocks further forward. */
	private static Vec3 hand(ServerPlayer player, double ahead) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double w = player.getBbWidth(), forward = w * HAND_AHEAD + ahead, side = w * HAND_SIDE;
		return new Vec3(player.getX() + fx * forward - fz * side, player.getY() + player.getBbHeight() * HAND_UP, player.getZ() + fz * forward + fx * side);
	}

	/** The ground ahead of it, under its right hand's side, where its catch is slammed. */
	private static Vec3 groundAhead(ServerPlayer player, ServerLevel level) {
		Vec3 over = hand(player, player.getBbWidth() * 0.2);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(over.x, 0.0, over.z));
		double y = top.getY();
		if (Math.abs(y - player.getY()) > GRAB_CLIMB_PER_SCALE * size(player)) {
			y = player.getY();
		}
		return new Vec3(over.x, y, over.z);
	}

	/** The grab is over (or the form is): whatever it held is let go. */
	private static void endGrab(ServerPlayer player, XenoformPower.Live live) {
		if (live.grabStart >= 0L) {
			player.removeEffect(MobEffects.SLOWNESS);
		}
		live.grabStart = -1L;
		live.grabPrey = -1;
		live.grabHolding = false;
		live.grabFrom = null;
		live.grabTo = null;
		player.removeAttached(XenoformState.GRAB);
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CAMPFIRE_COSY_SMOKE, player.position().add(0.0, 1.0, 0.0), 60, 2.0, 0.05);
		sound(player, SoundEvents.WARDEN_ROAR, 3.0F, 0.4F);
		return true;
	}

	/** (Client) How far into its grab a Towerback is (ticks, {@link #TOTAL} long), or -1. */
	public static float grabPhase(Entity entity, float partialTicks) {
		Long start = entity.getAttached(XenoformState.GRAB);
		if (start == null) {
			return -1.0F;
		}
		float t = entity.level().getGameTime() - start + partialTicks;
		return t >= 0.0F && t < TOTAL ? t : -1.0F;
	}
}
