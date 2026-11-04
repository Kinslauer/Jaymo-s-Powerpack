package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.BossPosture;
import com.jaymoskingdoms.PatronChampion;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Forge-Thane's moves: Hammerfall (a leap onto a marked spot), Slag Spray (a cone of fire), Iron Skin (he takes
 * little and wears the weapons that strike him; a kneel ends it); in a group, slag rises from the floor.
 */
final class ForgeThaneMoves implements Moves {
	static final int FALL_WINDUP = 15, FALL_COOLDOWN = 160, FALL_AIR = 14, FALL_MOST = 40;
	static final double FALL_MIN = 4.0, FALL_MAX = 14.0, FALL_RADIUS = 4.0, FALL_LIFT = 0.5;
	static final float FALL_SHARE = 1.0F;
	static final int FALL_SLOW = 40;
	static final int SPRAY_WINDUP = 20, SPRAY_COOLDOWN = 200, SPRAY_FIRE = 80;
	static final double SPRAY_RANGE = 8.0, SPRAY_DEGREES = 60.0;
	static final float SPRAY_SHARE = 0.5F;
	static final int SKIN_COOLDOWN = 420, SKIN_TICKS = 120, SKIN_WEAR = 6;
	static final float SKIN_TAKES = 0.4F;

	private Vec3 mark = Vec3.ZERO;
	private int air = -1, skin, wornAt = -1;
	private final List<Move> moves = List.of(
		new Move("hammerfall", FALL_WINDUP, FALL_COOLDOWN, FALL_MIN, FALL_MAX, this::fallTell, this::fallLand),
		new Move("slag_spray", SPRAY_WINDUP, SPRAY_COOLDOWN, 0.0, SPRAY_RANGE, this::sprayTell, this::sprayLand),
		new Move("iron_skin", 0, SKIN_COOLDOWN, 0.0, 1000.0, (l, c, t, left) -> { }, (l, c, t) -> this.skin = SKIN_TICKS));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void fallTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == FALL_WINDUP && target != null) {
			this.mark = target.position();
		}
		MoveKit.markSpot(level, this.mark, FALL_RADIUS, c.patron().colour);
		level.sendParticles(ParticleTypes.CRIT, c.getX(), c.getY() + 0.2, c.getZ(), 3, 0.5, 0.0, 0.5, 0.0);
	}

	private void fallLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		Vec3 d = this.mark.subtract(c.position());
		double k = 0.09 / (1.0 - Math.pow(0.91, FALL_AIR));
		c.getNavigation().stop();
		c.setDeltaMovement(d.x * k, 0.7, d.z * k);
		c.needsSync = true;
		this.air = 0;
		MoveKit.sound(level, c.position(), SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.6F);
	}

	@Override
	public void tick(ServerLevel level, PatronChampion c) {
		if (this.skin > 0) {
			this.skin--;
			if (BossPosture.kneeling(c)) {
				this.skin = 0;
			} else if (this.skin % 6 == 0) {
				level.sendParticles(ParticleTypes.CRIT, c.getX(), c.getY() + 1.2, c.getZ(), 4, 0.5, 0.8, 0.5, 0.05);
			}
		}
		if (this.air >= 0) {
			this.air++;
			if (this.air > 3 && c.onGround() || this.air > FALL_MOST) {
				this.air = -1;
				for (LivingEntity e : c.foes(FALL_RADIUS + 2.0)) {
					if (e.distanceTo(c) <= FALL_RADIUS) {
						c.hurtFoe(level, e, MoveKit.share(c, FALL_SHARE), false);
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FALL_SLOW, 1));
						MoveKit.push(e, c.position(), 0.2, FALL_LIFT);
					}
				}
				level.sendParticles(ParticleTypes.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 3, FALL_RADIUS / 2.0, 0.1, FALL_RADIUS / 2.0, 0.0);
				MoveKit.ring(level, c.position(), FALL_RADIUS, c.patron().colour);
				MoveKit.sound(level, c.position(), SoundEvents.MACE_SMASH_GROUND, 2.0F, 0.8F);
			}
		}
	}

	@Override
	public float hurt(PatronChampion c, DamageSource source, float amount) {
		if (this.skin <= 0) {
			return amount;
		}
		return amount * SKIN_TAKES;
	}

	/** Iron Skin wears the striker's weapon, once per tick, only for blows that landed. */
	@Override
	public void afterHurt(PatronChampion c, DamageSource source) {
		if (this.skin <= 0 || c.tickCount == this.wornAt) {
			return;
		}
		Entity striker = source.getEntity();
		if (striker instanceof LivingEntity living && source.getDirectEntity() == striker) {
			this.wornAt = c.tickCount;
			ItemStack held = living.getMainHandItem();
			if (!held.isEmpty() && held.isDamageableItem()) {
				held.hurtAndBreak(SKIN_WEAR, living, EquipmentSlot.MAINHAND);
			}
			if (c.level() instanceof ServerLevel level) {
				MoveKit.sound(level, c.position(), SoundEvents.SMITHING_TABLE_USE, 1.0F, 1.0F);
			}
		}
	}

	@Override
	public void clear(PatronChampion c) {
		this.skin = 0;
		this.air = -1;
	}

	private void sprayTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		Vec3 look = c.getLookAngle();
		for (int i = 0; i < 8; i++) {
			double a = (c.getRandom().nextDouble() - 0.5) * Math.toRadians(SPRAY_DEGREES);
			double d = 1.0 + c.getRandom().nextDouble() * (SPRAY_RANGE - 1.0);
			Vec3 dir = look.yRot((float)a);
			level.sendParticles(ParticleTypes.DRIPPING_LAVA, c.getX() + dir.x * d, c.getY() + 1.4, c.getZ() + dir.z * d, 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	private void sprayLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		Vec3 look = c.getLookAngle();
		for (int i = 0; i < 40; i++) {
			double a = (c.getRandom().nextDouble() - 0.5) * Math.toRadians(SPRAY_DEGREES);
			double d = 1.0 + c.getRandom().nextDouble() * (SPRAY_RANGE - 1.0);
			Vec3 dir = look.yRot((float)a);
			level.sendParticles(ParticleTypes.FALLING_LAVA, c.getX() + dir.x * d, c.getY() + 1.4, c.getZ() + dir.z * d, 1, 0.1, 0.1, 0.1, 0.0);
		}
		for (LivingEntity e : c.foes(SPRAY_RANGE)) {
			if (MoveKit.inCone(c, e, SPRAY_RANGE, SPRAY_DEGREES)) {
				c.hurtFoe(level, e, MoveKit.share(c, SPRAY_SHARE), false);
				e.igniteForTicks(SPRAY_FIRE);
			}
		}
		MoveKit.sound(level, c.position(), SoundEvents.LAVA_POP, 2.0F, 0.6F);
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		for (int i = 0; i < MoveKit.adds(group); i++) {
			MagmaCube cube = EntityTypes.MAGMA_CUBE.create(level, EntitySpawnReason.EVENT);
			if (cube == null) {
				continue;
			}
			// size 1 so it never splits into untagged cubes
			cube.setSize(1, true);
			MoveKit.name(cube, "slag");
			c.spawnAdd(level, cube);
		}
	}
}
