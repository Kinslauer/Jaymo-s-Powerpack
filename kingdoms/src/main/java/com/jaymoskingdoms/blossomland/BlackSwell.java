package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.creatures.Looks;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Black Swell: a great dark round shape that comes up out of the sea in a fog bank at night, on the waters round
 * Mohknee. It cannot be hurt. It comes on slowly toward the nearest ship or boat ({@link #SEES} blocks); alongside
 * ({@link #REACH}) it pours the sea into a ship's hold every second and stoves in a small boat. A barrel put over the
 * side to it is what it wants: it takes the barrel down with it and is gone. Otherwise it goes when the fog or the
 * night does, or after {@link #STAYS} ticks.
 */
public class BlackSwell extends Mob implements Looks {
	/** How far under the surface it starts, and how far its crown stands out of the water once risen. */
	public static final double DEPTH = 7.0;
	static final double RISEN = 1.5, RISE = 0.06, SINK = 0.1, DRIFT = 0.07;
	static final double SEES = 64.0, REACH = 8.0, TAKES = 9.0;
	static final int STAYS = 2400, FLOOD = 1;
	static final float BOAT_HARM = 12.0F;

	private boolean sinking;

	public BlackSwell(EntityType<? extends BlackSwell> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		double surface = level.getSeaLevel() - 6.0 + RISEN;
		if (!this.sinking && (this.tickCount > STAYS || level.isBrightOutside())) {
			this.sinking = true;
		}
		if (this.sinking) {
			this.setPos(this.getX(), this.getY() - SINK, this.getZ());
			if (this.getY() < level.getSeaLevel() - 6.0 - DEPTH) {
				this.discard();
			}
			return;
		}
		// what it has been given
		List<ItemEntity> given = level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(TAKES, 4.0, TAKES),
			i -> i.isAlive() && (i.getItem().is(Items.BARREL) || i.getItem().is(BlossomFurnishings.TANSU.asItem())));
		if (!given.isEmpty()) {
			given.get(0).discard();
			this.sinking = true;
			level.playSound(null, this.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 4.0F, 0.5F);
			level.sendParticles(ParticleTypes.BUBBLE_POP, this.getX(), level.getSeaLevel(), this.getZ(), 80, 3.0, 0.2, 3.0, 0.02);
			return;
		}
		double y = Math.min(surface, this.getY() + RISE);
		Entity prey = this.prey(level);
		Vec3 to = Vec3.ZERO;
		if (prey != null) {
			Vec3 gap = new Vec3(prey.getX() - this.getX(), 0.0, prey.getZ() - this.getZ());
			double d = gap.length();
			if (d > REACH * 0.5) {
				to = gap.scale(DRIFT / d);
				this.setYRot((float)(Math.toDegrees(Math.atan2(gap.z, gap.x)) - 90.0));
				this.yBodyRot = this.getYRot();
				this.yHeadRot = this.getYRot();
			}
			if (d <= REACH && this.tickCount % 20 == 0 && y >= surface - 0.5) {
				if (prey instanceof Ship ship) {
					ship.shipWater(FLOOD);
				} else {
					prey.hurtServer(level, this.damageSources().mobAttack(this), BOAT_HARM);
				}
				level.playSound(null, prey.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.5F);
				level.sendParticles(ParticleTypes.SPLASH, prey.getX(), prey.getY() + 1.0, prey.getZ(), 40, 2.0, 0.5, 2.0, 0.1);
			}
		}
		this.setPos(this.getX() + to.x, y, this.getZ() + to.z);
		if (this.tickCount % 80 == 0) {
			level.playSound(null, this.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.4F);
		}
	}

	/** The nearest ship, or failing one the nearest small boat with someone in it. */
	private Entity prey(ServerLevel level) {
		Entity best = null;
		double bestD = SEES * SEES;
		for (Ship ship : Ships.in(level)) {
			double d = ship.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = ship;
			}
		}
		if (best != null) {
			return best;
		}
		for (AbstractBoat boat : level.getEntitiesOfClass(AbstractBoat.class, this.getBoundingBox().inflate(SEES, 8.0, SEES), b -> b.getFirstPassenger() instanceof Player)) {
			double d = boat.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = boat;
			}
		}
		return best;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// nothing aboard a ship can hurt it
		return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public int look() {
		return 0;
	}

	@Override
	public float fullness() {
		return 0.0F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("sinking", this.sinking);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.sinking = input.getBooleanOr("sinking", false);
		this.noPhysics = true;
	}
}
