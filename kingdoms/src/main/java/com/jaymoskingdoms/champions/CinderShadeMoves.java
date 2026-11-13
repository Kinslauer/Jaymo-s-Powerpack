package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.CaptainWays;
import com.jaymoskingdoms.MobAmmo;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.PatronChampion;
import com.jaymoskingdoms.PatronChampions;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Cinder-Shade's moves: Smokestep (behind you at once), Ember Knives (a fan of her own daggers, heated red so
 * they burn: real knives she carries and must gather again, {@code MobAmmo.Kind.KNIVES}), Ash Cloud (fight her from outside
 * it); in a group, smoke doubles of herself.
 */
final class CinderShadeMoves implements Moves {
	static final int STEP_WINDUP = 10, STEP_COOLDOWN = 120, STEP_BLOW_TICKS = 20;
	static final double STEP_RANGE = 16.0, STEP_BEHIND = 2.0;
	static final float STEP_BLOW = 1.4F;
	static final int KNIVES_WINDUP = 15, KNIVES_COOLDOWN = 160, KNIVES_COUNT = 5;
	static final double KNIVES_MIN = 4.0, KNIVES_MAX = 20.0, KNIVES_DEGREES = 40.0;
	static final float KNIVES_DAMAGE = 5.0F;
	/** The dagger she carries and throws (Powers' iron dagger). */
	static final net.minecraft.resources.Identifier KNIFE = net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", "iron_dagger");
	static final int CLOUD_WINDUP = 20, CLOUD_COOLDOWN = 360, CLOUD_TICKS = 200, CLOUD_BLIND = 40, CLOUD_EVERY = 20;
	static final double CLOUD_RADIUS = 6.0;
	static final float CLOUD_TAKES = 0.5F;

	private Vec3 cloud = Vec3.ZERO;
	private int cloudLeft;
	private final List<Move> moves = List.of(
		new Move("smokestep", STEP_WINDUP, STEP_COOLDOWN, 0.0, STEP_RANGE, this::stepTell, this::stepLand),
		new Move("ember_knives", KNIVES_WINDUP, KNIVES_COOLDOWN, KNIVES_MIN, KNIVES_MAX, this::knivesTell, this::knivesLand),
		new Move("ash_cloud", CLOUD_WINDUP, CLOUD_COOLDOWN, 0.0, 1000.0, this::cloudTell, this::cloudLand));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void stepTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX(), c.getY() + 1.0, c.getZ(), 6, 0.4, 0.7, 0.4, 0.02);
	}

	private void stepLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		LivingEntity t = MoveKit.aim(c, target);
		if (t == null) {
			return;
		}
		Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(STEP_BEHIND));
		Vec3 spot = MoveKit.standable(level, behind.x, t.getY(), behind.z);
		if (spot == null) {
			spot = MoveKit.blinkSpot(level, t, 2.0, 3.0);
		}
		if (spot == null) {
			return;
		}
		c.blinkTo(spot);
		MoveKit.sound(level, spot, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.5F);
		c.setTarget(t);
		c.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
		c.nextBlow(STEP_BLOW, STEP_BLOW_TICKS);
		c.doHurtTarget(level, t);
	}

	private void knivesTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		level.sendParticles(ParticleTypes.SMALL_FLAME, c.getX(), c.getY() + 1.5, c.getZ(), 5, 0.5, 0.3, 0.5, 0.01);
	}

	private void knivesLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		LivingEntity t = MoveKit.aim(c, target);
		if (t == null) {
			return;
		}
		Item dagger = BuiltInRegistries.ITEM.getValue(KNIFE);
		if (dagger == Items.AIR) {
			return;
		}
		int thrown = 0;
		for (int i = 0; i < KNIVES_COUNT && MobAmmo.spend(c, MobAmmo.Kind.KNIVES); i++) {
			float off = (float)((i - (KNIVES_COUNT - 1) / 2.0) * KNIVES_DEGREES / (KNIVES_COUNT - 1));
			CaptainWays.throwBlade(level, c, t, new ItemStack(dagger), KNIVES_DAMAGE, off, true);
			thrown++;
		}
		if (thrown > 0) {
			MoveKit.sound(level, c.position(), SoundEvents.BLAZE_SHOOT, 1.5F, 1.4F);
		}
	}

	private void cloudTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		MoveKit.ring(level, c.position(), CLOUD_RADIUS * (1.0 - left / (double)CLOUD_WINDUP), c.patron().colour);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX(), c.getY() + 0.5, c.getZ(), 5, 1.0, 0.3, 1.0, 0.02);
	}

	private void cloudLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		this.cloud = c.position();
		this.cloudLeft = CLOUD_TICKS;
		MoveKit.sound(level, c.position(), SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.6F);
	}

	private boolean inside(PatronChampion c) {
		return this.cloudLeft > 0 && c.position().distanceTo(this.cloud) <= CLOUD_RADIUS;
	}

	@Override
	public void tick(ServerLevel level, PatronChampion c) {
		if (this.cloudLeft <= 0) {
			return;
		}
		this.cloudLeft--;
		if (this.cloudLeft % 3 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.cloud.x, this.cloud.y + 1.0, this.cloud.z, 6, CLOUD_RADIUS / 2.0, 0.8, CLOUD_RADIUS / 2.0, 0.01);
			level.sendParticles(ParticleTypes.ASH, this.cloud.x, this.cloud.y + 1.5, this.cloud.z, 6, CLOUD_RADIUS / 2.0, 1.0, CLOUD_RADIUS / 2.0, 0.01);
		}
		if (this.cloudLeft % CLOUD_EVERY == 0) {
			for (LivingEntity e : c.foes(CLOUD_RADIUS + 20.0)) {
				if (e.position().distanceTo(this.cloud) <= CLOUD_RADIUS) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, CLOUD_BLIND, 0));
				}
			}
			if (c.getTarget() != null && c.position().distanceTo(this.cloud) > CLOUD_RADIUS - 1.0) {
				c.getNavigation().moveTo(this.cloud.x, this.cloud.y, this.cloud.z, 1.0);
			}
		}
	}

	@Override
	public float hurt(PatronChampion c, DamageSource source, float amount) {
		return this.inside(c) ? amount * CLOUD_TAKES : amount;
	}

	@Override
	public void clear(PatronChampion c) {
		this.cloudLeft = 0;
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		var type = PatronChampions.typeOf(Patron.ASH_WHISPERER);
		for (int i = 0; i < MoveKit.adds(group) && type != null; i++) {
			PatronChampion twin = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (twin == null) {
				continue;
			}
			twin.makeDecoy();
			MoveKit.name(twin, "double");
			c.spawnAdd(level, twin);
		}
	}
}
