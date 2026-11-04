package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.PatronChampion;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Tide-Bride's moves: Undertow (pulls everyone in), Drowning Grip (one foe drowns until they hit her or break her
 * sight), Riptide Wave (a marked line); in a group, drowned ones rise.
 */
final class TideBrideMoves implements Moves {
	static final int TOW_WINDUP = 20, TOW_COOLDOWN = 160, TOW_SLOW = 40, TOW_BLOW_TICKS = 30;
	static final double TOW_RANGE = 10.0, TOW_PULL = 1.2;
	static final float TOW_BLOW = 1.3F;
	static final int GRIP_WINDUP = 15, GRIP_COOLDOWN = 260, GRIP_TICKS = 100, GRIP_STRIKES = 3, GRIP_UNSEEN = 40, GRIP_EVERY = 20;
	static final double GRIP_RANGE = 14.0;
	static final float GRIP_SHARE = 0.12F;
	static final int WAVE_WINDUP = 25, WAVE_COOLDOWN = 200;
	static final double WAVE_LENGTH = 14.0, WAVE_WIDTH = 3.0, WAVE_LIFT = 0.8;
	static final float WAVE_SHARE = 0.9F;

	private @Nullable LivingEntity gripped;
	private int grip, strikes, unseen;
	private Vec3 waveFrom = Vec3.ZERO, waveTo = Vec3.ZERO;
	private final List<Move> moves = List.of(
		new Move("undertow", TOW_WINDUP, TOW_COOLDOWN, 0.0, TOW_RANGE, this::towTell, this::towLand),
		new Move("drowning_grip", GRIP_WINDUP, GRIP_COOLDOWN, 0.0, GRIP_RANGE, this::gripTell, this::gripLand),
		new Move("riptide_wave", WAVE_WINDUP, WAVE_COOLDOWN, 0.0, GRIP_RANGE, this::waveTell, this::waveLand));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void towTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		double r = TOW_RANGE * left / (double)TOW_WINDUP;
		int n = Math.max(12, (int)(r * 6));
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2.0 / n;
			level.sendParticles(ParticleTypes.BUBBLE, c.getX() + Math.cos(a) * r, c.getY() + 0.6, c.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private void towLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(TOW_RANGE)) {
			MoveKit.pull(e, c.position(), TOW_PULL);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TOW_SLOW, 0));
		}
		c.nextBlow(TOW_BLOW, TOW_BLOW_TICKS);
		MoveKit.sound(level, c.position(), SoundEvents.TRIDENT_RIPTIDE_1, 1.5F, 0.8F);
	}

	private void gripTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (target != null) {
			level.sendParticles(ParticleTypes.BUBBLE, target.getX(), target.getY() + 1.0, target.getZ(), 6, 0.4, 0.8, 0.4, 0.02);
		}
	}

	private void gripLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		LivingEntity t = MoveKit.aim(c, target);
		if (t == null || !c.hasLineOfSight(t)) {
			return;
		}
		this.gripped = t;
		this.grip = GRIP_TICKS;
		this.strikes = 0;
		this.unseen = 0;
		MoveKit.sound(level, t.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.6F);
	}

	@Override
	public void tick(ServerLevel level, PatronChampion c) {
		if (this.grip <= 0 || this.gripped == null) {
			return;
		}
		LivingEntity g = this.gripped;
		if (!g.isAlive() || g.level() != c.level()) {
			this.grip = 0;
			return;
		}
		this.unseen = c.hasLineOfSight(g) ? 0 : this.unseen + 1;
		if (this.unseen >= GRIP_UNSEEN) {
			this.grip = 0;
			return;
		}
		this.grip--;
		level.sendParticles(ParticleTypes.BUBBLE, g.getX(), g.getEyeY(), g.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
		if (this.grip % GRIP_EVERY == 0) {
			g.hurtServer(level, c.damageSources().drown(), MoveKit.share(c, GRIP_SHARE));
		}
	}

	@Override
	public float hurt(PatronChampion c, DamageSource source, float amount) {
		if (this.grip > 0 && source.getEntity() == this.gripped && ++this.strikes >= GRIP_STRIKES) {
			this.grip = 0;
		}
		return amount;
	}

	@Override
	public void clear(PatronChampion c) {
		this.grip = 0;
		this.gripped = null;
	}

	private void waveTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == WAVE_WINDUP) {
			Vec3 dir = target == null ? c.getLookAngle() : target.position().subtract(c.position());
			dir = new Vec3(dir.x, 0.0, dir.z);
			dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : dir.normalize();
			this.waveFrom = c.position();
			this.waveTo = this.waveFrom.add(dir.scale(WAVE_LENGTH));
		}
		for (double d = 0.0; d <= WAVE_LENGTH; d += 1.0) {
			Vec3 p = this.waveFrom.add(this.waveTo.subtract(this.waveFrom).scale(d / WAVE_LENGTH));
			MoveKit.dust(level, new net.minecraft.core.particles.DustParticleOptions(c.patron().colour & 0xFFFFFF, 1.0F), p.add(0.0, 0.2, 0.0), 2,
				WAVE_WIDTH / 2.0, 0.0);
		}
	}

	private void waveLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(WAVE_LENGTH + 2.0)) {
			if (MoveKit.nearLine(this.waveFrom, this.waveTo, e, WAVE_WIDTH / 2.0)) {
				c.hurtFoe(level, e, MoveKit.share(c, WAVE_SHARE), false);
				MoveKit.push(e, this.waveFrom, 0.5, WAVE_LIFT);
				e.clearFire();
			}
		}
		for (double d = 0.0; d <= WAVE_LENGTH; d += 1.0) {
			Vec3 p = this.waveFrom.add(this.waveTo.subtract(this.waveFrom).scale(d / WAVE_LENGTH));
			level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.5, p.z, 12, 1.2, 0.6, 1.2, 0.2);
		}
		MoveKit.sound(level, c.position(), SoundEvents.GENERIC_SPLASH, 2.0F, 0.7F);
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		for (int i = 0; i < MoveKit.adds(group); i++) {
			Drowned drowned = EntityTypes.DROWNED.create(level, EntitySpawnReason.EVENT);
			if (drowned == null) {
				continue;
			}
			drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
			MoveKit.name(drowned, "drowned");
			c.spawnAdd(level, drowned);
		}
	}
}
