package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.PatronChampion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Grave-Lamp's moves: Lamp Flare (look away), Soul Lanterns (three pulsing lights), Night Falls (darkness and
 * harder blows); in a group, lantern wisps.
 */
final class GraveLampMoves implements Moves {
	static final int FLARE_WINDUP = 20, FLARE_COOLDOWN = 180, FLARE_BLIND = 60, FLARE_SLOW = 60;
	static final double FLARE_RANGE = 20.0, FLARE_FACING = 0.5;
	static final int LAMPS_WINDUP = 15, LAMPS_COOLDOWN = 240, LAMPS_COUNT = 3, LAMPS_TICKS = 160, LAMPS_EVERY = 20;
	static final double LAMPS_RANGE = 16.0, LAMPS_RADIUS = 2.5;
	static final float LAMPS_SHARE = 0.25F;
	static final int NIGHT_WINDUP = 30, NIGHT_COOLDOWN = 400, NIGHT_DARK = 160, NIGHT_TICKS = 160;
	static final double NIGHT_RANGE = 24.0;
	static final float NIGHT_BLOW = 1.3F;
	static final int WISP_LIFE = 600;

	private record Lamp(Vec3 at, int[] left) {
	}

	private final List<Lamp> lamps = new ArrayList<>();
	private int night;
	private final List<Move> moves = List.of(
		new Move("lamp_flare", FLARE_WINDUP, FLARE_COOLDOWN, 0.0, FLARE_RANGE, this::flareTell, this::flareLand),
		new Move("soul_lanterns", LAMPS_WINDUP, LAMPS_COOLDOWN, 0.0, LAMPS_RANGE, this::lampsTell, this::lampsLand),
		new Move("night_falls", NIGHT_WINDUP, NIGHT_COOLDOWN, 0.0, 1000.0, this::nightTell, this::nightLand));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void swirl(ServerLevel level, PatronChampion c, int left, int total) {
		double a = left * 0.8;
		double r = 0.4 + 1.2 * (1.0 - left / (double)total);
		level.sendParticles(Fx.LIGHT, c.getX() + Math.cos(a) * r, c.getY() + 2.6, c.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(Fx.LIGHT, c.getX() - Math.cos(a) * r, c.getY() + 2.9, c.getZ() - Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
	}

	private void flareTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == FLARE_WINDUP) {
			MoveKit.sound(level, c.position(), SoundEvents.BELL_RESONATE, 1.5F, 0.8F);
		}
		this.swirl(level, c, left, FLARE_WINDUP);
	}

	private void flareLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(FLARE_RANGE)) {
			Vec3 to = c.getEyePosition().subtract(e.getEyePosition());
			if (to.lengthSqr() > 1.0E-4 && e.getLookAngle().dot(to.normalize()) > FLARE_FACING && c.hasLineOfSight(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLARE_BLIND, 0));
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FLARE_SLOW, 0));
			}
		}
		level.sendParticles(Fx.LIGHT, c.getX(), c.getY() + 2.6, c.getZ(), 40, 0.3, 0.3, 0.3, 0.3);
		MoveKit.sound(level, c.position(), SoundEvents.BELL_RESONATE, 2.0F, 1.2F);
	}

	private void lampsTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		this.swirl(level, c, left, LAMPS_WINDUP);
	}

	private void lampsLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		List<LivingEntity> foes = c.foes(LAMPS_RANGE);
		for (int i = 0; i < LAMPS_COUNT && !foes.isEmpty(); i++) {
			LivingEntity f = foes.get(i % foes.size());
			Vec3 spot = MoveKit.standable(level, f.getX() + c.getRandom().nextInt(5) - 2, f.getY(), f.getZ() + c.getRandom().nextInt(5) - 2);
			this.lamps.add(new Lamp(spot == null ? f.position() : spot, new int[] {LAMPS_TICKS}));
		}
		MoveKit.sound(level, c.position(), SoundEvents.SOUL_ESCAPE, 1.5F, 0.8F);
	}

	private void nightTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		MoveKit.ring(level, c.position(), NIGHT_RANGE * (1.0 - left / (double)NIGHT_WINDUP), c.patron().colour);
		level.sendParticles(ParticleTypes.SCULK_SOUL, c.getX(), c.getY() + 1.0, c.getZ(), 3, 0.6, 0.8, 0.6, 0.01);
	}

	private void nightLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(NIGHT_RANGE)) {
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, NIGHT_DARK, 0));
		}
		this.night = NIGHT_TICKS;
		MoveKit.sound(level, c.position(), SoundEvents.WARDEN_ROAR, 1.5F, 1.4F);
	}

	@Override
	public void tick(ServerLevel level, PatronChampion c) {
		if (this.night > 0) {
			this.night--;
			c.nextBlow(NIGHT_BLOW, 2);
		}
		for (var it = this.lamps.iterator(); it.hasNext();) {
			Lamp lamp = it.next();
			int left = --lamp.left()[0];
			if (left <= 0) {
				it.remove();
				continue;
			}
			Vec3 p = lamp.at();
			if (left % 4 == 0) {
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.8, p.z, 2, 0.1, 0.3, 0.1, 0.01);
			}
			if (left % LAMPS_EVERY == 0) {
				MoveKit.ring(level, p, LAMPS_RADIUS, c.patron().colour);
				for (LivingEntity e : c.foes(LAMPS_RANGE + 20.0)) {
					if (e.position().distanceTo(p) <= LAMPS_RADIUS && Math.abs(e.getY() - p.y) < 3.0) {
						c.hurtFoe(level, e, MoveKit.share(c, LAMPS_SHARE), true);
					}
				}
			}
		}
	}

	@Override
	public void clear(PatronChampion c) {
		this.lamps.clear();
		this.night = 0;
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		for (int i = 0; i < MoveKit.adds(group); i++) {
			Vex vex = EntityTypes.VEX.create(level, EntitySpawnReason.EVENT);
			if (vex == null) {
				continue;
			}
			// (0.7.518) EntityType.create skips finalizeSpawn, so the sword is given by hand
			vex.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
			vex.setLimitedLife(WISP_LIFE);
			MoveKit.name(vex, "wisp");
			c.spawnAdd(level, vex);
		}
	}
}
