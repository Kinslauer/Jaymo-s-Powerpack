package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.PatronChampion;
import java.util.List;
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
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Sun-Marshal's moves: Sunbeam (a ring follows you, then holds), Royal Guard (strike him from behind), Noon (break
 * his sight); in a group, the sun-guard.
 */
final class SunMarshalMoves implements Moves {
	static final int BEAM_WINDUP = 25, BEAM_COOLDOWN = 140, BEAM_FOLLOW = 15, BEAM_FIRE = 100;
	static final double BEAM_RANGE = 20.0, BEAM_RADIUS = 1.5;
	/** The column of light: how high it stands (blocks) and how long it shows (ticks). */
	static final double BEAM_HEIGHT = 14.0;
	static final int BEAM_SHOWN = 14;
	static final float BEAM_SHARE = 1.0F;
	static final int GUARD_COOLDOWN = 300, GUARD_TICKS = 80;
	static final double GUARD_FRONT = 0.2;
	static final float GUARD_WEAR = 0.5F, GUARD_BACK = 1.25F;
	static final int NOON_WINDUP = 40, NOON_COOLDOWN = 360, NOON_WEAK = 100, NOON_FIRE = 60;
	static final double NOON_RANGE = 10.0;
	static final float NOON_SHARE = 1.2F;

	private Vec3 held = Vec3.ZERO;
	private int guard;
	private final List<Move> moves = List.of(
		new Move("sunbeam", BEAM_WINDUP, BEAM_COOLDOWN, 0.0, BEAM_RANGE, this::beamTell, this::beamLand),
		new Move("royal_guard", 0, GUARD_COOLDOWN, 0.0, 1000.0, (l, c, t, left) -> { }, (l, c, t) -> this.guard = GUARD_TICKS),
		new Move("noon", NOON_WINDUP, NOON_COOLDOWN, 0.0, NOON_RANGE, this::noonTell, this::noonLand));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void beamTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (target != null && left > BEAM_WINDUP - BEAM_FOLLOW) {
			this.held = target.position();
		}
		MoveKit.markSpot(level, this.held, BEAM_RADIUS, c.patron().colour);
	}

	private void beamLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(BEAM_RANGE + 6.0)) {
			Vec3 d = e.position().subtract(this.held);
			if (Math.hypot(d.x, d.z) <= BEAM_RADIUS && Math.abs(d.y) < 3.0) {
				c.hurtFoe(level, e, MoveKit.share(c, BEAM_SHARE), false);
				e.igniteForTicks(BEAM_FIRE);
			}
		}
		// the column of light itself, as wide as what it burns
		Fx.beam(level, this.held.add(0.0, BEAM_HEIGHT, 0.0), this.held, (float)BEAM_RADIUS * 0.5F, c.patron().colour, BEAM_SHOWN);
		MoveKit.sound(level, this.held, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 1.6F);
	}

	@Override
	public void tick(ServerLevel level, PatronChampion c) {
		if (this.guard > 0) {
			this.guard--;
			if (this.guard % 5 == 0) {
				Vec3 f = c.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
				level.sendParticles(Fx.EMBER, c.getX() + f.x * 0.8, c.getY() + 1.3, c.getZ() + f.z * 0.8, 3, 0.3, 0.6, 0.3, 0.0);
			}
		}
	}

	@Override
	public float hurt(PatronChampion c, DamageSource source, float amount) {
		if (this.guard <= 0) {
			return amount;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return amount;
		}
		Vec3 to = from.subtract(c.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-4) {
			return amount;
		}
		if (c.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().dot(to.normalize()) > GUARD_FRONT) {
			c.wearPosture(amount * GUARD_WEAR);
			if (c.level() instanceof ServerLevel level) {
				MoveKit.sound(level, c.position(), SoundEvents.SHIELD_BLOCK, 1.0F, 1.0F);
			}
			return 0.0F;
		}
		return amount * GUARD_BACK;
	}

	@Override
	public void clear(PatronChampion c) {
		this.guard = 0;
	}

	private void noonTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == NOON_WINDUP) {
			MoveKit.sound(level, c.position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 1.0F);
		}
		double h = 12.0 * (1.0 - left / (double)NOON_WINDUP);
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3.0 + left * 0.3;
			level.sendParticles(Fx.LIGHT, c.getX() + Math.cos(a) * 1.2, c.getY() + h, c.getZ() + Math.sin(a) * 1.2, 1, 0.0, 0.0, 0.0, 0.0);
		}
		MoveKit.ring(level, c.position(), NOON_RANGE, c.patron().colour);
	}

	private void noonLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(NOON_RANGE)) {
			if (c.hasLineOfSight(e)) {
				c.hurtFoe(level, e, MoveKit.share(c, NOON_SHARE), false);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, NOON_WEAK, 0));
				e.igniteForTicks(NOON_FIRE);
			}
		}
		level.sendParticles(Fx.LIGHT, c.getX(), c.getY() + 1.5, c.getZ(), 60, 1.0, 1.0, 1.0, 0.3);
		MoveKit.sound(level, c.position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		for (int i = 0; i < MoveKit.adds(group); i++) {
			Pillager guard = EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
			if (guard == null) {
				continue;
			}
			guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
			guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
			MoveKit.name(guard, "sun_guard");
			c.spawnAdd(level, guard);
		}
	}
}
