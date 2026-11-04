package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.BossPosture;
import com.jaymoskingdoms.PatronChampion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Hearth-Warden's moves: Banked Coals (marked spots burst), Kept Fire (a channel that mends her; break it),
 * Hearth's Wrath (a fiery cone); in a group, embers rise to help her.
 */
final class HearthWardenMoves implements Moves {
	static final int COALS_WINDUP = 20, COALS_COOLDOWN = 140, COALS_MOST = 4;
	static final double COALS_RANGE = 16.0, COALS_RADIUS = 2.0;
	static final float COALS_SHARE = 0.6F;
	static final int COALS_FIRE = 60;
	static final int FIRE_COOLDOWN = 400, FIRE_TICKS = 60, FIRE_EVERY = 10;
	static final float FIRE_HEAL = 0.015F, FIRE_BREAK = 0.06F;
	static final int WRATH_WINDUP = 25, WRATH_COOLDOWN = 180, WRATH_FIRE = 80;
	static final double WRATH_RANGE = 10.0, WRATH_DEGREES = 70.0, WRATH_PUSH = 1.2;
	static final float WRATH_SHARE = 0.9F;
	static final float EMBER_HEALTH = 20.0F;

	private final List<Vec3> spots = new ArrayList<>();
	private int channel;
	private float taken;
	private final List<Move> moves = List.of(
		new Move("banked_coals", COALS_WINDUP, COALS_COOLDOWN, 0.0, COALS_RANGE, this::coalsTell, this::coalsLand),
		new Move("kept_fire", 0, FIRE_COOLDOWN, 0.0, 1000.0, (l, c, t, left) -> { }, this::keptFire),
		new Move("hearths_wrath", WRATH_WINDUP, WRATH_COOLDOWN, 0.0, WRATH_RANGE, this::wrathTell, this::wrathLand));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void coalsTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == COALS_WINDUP) {
			this.spots.clear();
			for (LivingEntity e : c.foes(COALS_RANGE)) {
				if (this.spots.size() < COALS_MOST) {
					this.spots.add(e.position());
				}
			}
		}
		for (Vec3 s : this.spots) {
			MoveKit.ring(level, s, COALS_RADIUS, c.patron().colour);
			level.sendParticles(ParticleTypes.FLAME, s.x, s.y + 0.2, s.z, 3, 0.6, 0.05, 0.6, 0.01);
		}
	}

	private void coalsLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (Vec3 s : this.spots) {
			level.sendParticles(ParticleTypes.LAVA, s.x, s.y + 0.3, s.z, 20, 0.8, 0.2, 0.8, 0.1);
			level.sendParticles(ParticleTypes.FLAME, s.x, s.y + 0.3, s.z, 30, 0.8, 0.5, 0.8, 0.08);
			for (LivingEntity e : c.foes(COALS_RANGE + 6.0)) {
				if (e.position().distanceTo(s) <= COALS_RADIUS && Math.abs(e.getY() - s.y) < 3.0) {
					c.hurtFoe(level, e, MoveKit.share(c, COALS_SHARE), false);
					e.igniteForTicks(COALS_FIRE);
				}
			}
		}
		MoveKit.sound(level, c.position(), SoundEvents.FIRECHARGE_USE, 1.5F, 0.8F);
		this.spots.clear();
	}

	private void keptFire(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		this.channel = FIRE_TICKS;
		this.taken = 0.0F;
		c.speak("phase.2");
	}

	@Override
	public void tick(ServerLevel level, PatronChampion c) {
		if (this.channel <= 0) {
			return;
		}
		if (BossPosture.kneeling(c)) {
			this.channel = 0;
			return;
		}
		this.channel--;
		if (this.channel % FIRE_EVERY == 0) {
			c.heal(c.getMaxHealth() * FIRE_HEAL);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + 1.5, c.getZ(), 6, 0.5, 0.8, 0.5, 0.02);
			MoveKit.sound(level, c.position(), SoundEvents.FURNACE_FIRE_CRACKLE, 1.0F, 1.0F);
		}
		if (this.channel % 4 == 0) {
			level.sendParticles(ParticleTypes.FLAME, c.getX(), c.getY() + 1.0, c.getZ(), 4, 0.6, 0.8, 0.6, 0.02);
		}
	}

	@Override
	public float hurt(PatronChampion c, DamageSource source, float amount) {
		if (this.channel > 0) {
			this.taken += amount;
			if (this.taken >= c.getMaxHealth() * FIRE_BREAK) {
				this.channel = 0;
				if (c.level() instanceof ServerLevel l) {
					MoveKit.sound(l, c.position(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.0F);
				}
			}
		}
		return amount;
	}

	@Override
	public void clear(PatronChampion c) {
		this.channel = 0;
		this.spots.clear();
	}

	private void wrathTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		Vec3 look = c.getLookAngle();
		for (int i = 0; i < 8; i++) {
			double a = (c.getRandom().nextDouble() - 0.5) * Math.toRadians(WRATH_DEGREES);
			double d = 1.0 + c.getRandom().nextDouble() * (WRATH_RANGE - 1.0);
			Vec3 dir = look.yRot((float)a);
			level.sendParticles(ParticleTypes.FLAME, c.getX() + dir.x * d, c.getY() + 1.0, c.getZ() + dir.z * d, 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	private void wrathLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(WRATH_RANGE)) {
			if (MoveKit.inCone(c, e, WRATH_RANGE, WRATH_DEGREES)) {
				c.hurtFoe(level, e, MoveKit.share(c, WRATH_SHARE), false);
				e.igniteForTicks(WRATH_FIRE);
				MoveKit.push(e, c.position(), WRATH_PUSH, 0.3);
			}
		}
		MoveKit.sound(level, c.position(), SoundEvents.BLAZE_SHOOT, 2.0F, 0.7F);
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		for (int i = 0; i < MoveKit.adds(group); i++) {
			Blaze blaze = EntityTypes.BLAZE.create(level, EntitySpawnReason.EVENT);
			if (blaze == null) {
				continue;
			}
			var health = blaze.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(EMBER_HEALTH);
			}
			blaze.setHealth(EMBER_HEALTH);
			MoveKit.name(blaze, "ember_spirit");
			c.spawnAdd(level, blaze);
		}
	}
}
