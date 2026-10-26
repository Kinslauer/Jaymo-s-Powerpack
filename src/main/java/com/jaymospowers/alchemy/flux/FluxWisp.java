package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.Phials;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Flux Wisp: a small glowing mote of spoiled magic that drifts out of badly blighted land at night, or hunts a person
 * who is far gone in corruption. It is weak (any hit kills it) and slow, drifts at the nearest player and, on touching,
 * leaves {@link #TOUCH_CORRUPTION} lingering corruption and a sting of magic damage; it then needs
 * {@link #TOUCH_COOLDOWN} ticks before it can touch again. Daylight thins it away. Killed, it leaves a Flux phial.
 * Drawn as a flat glowing sprite (it is an {@link ItemSupplier} so vanilla's thrown-item renderer can show it).
 */
public class FluxWisp extends Monster implements ItemSupplier {
	// ---- tuning ----
	public static final double HEALTH = 1.0, FOLLOW = 32.0, SPEED = 0.1;
	/** How hard it pushes through the air each tick (low: it drifts). */
	public static final float THRUST = 0.02F;
	/** What a touch does: magic damage, lingering corruption, and ticks until the next touch. */
	public static final float TOUCH_DAMAGE = 1.0F;
	public static final int TOUCH_CORRUPTION = 3, TOUCH_COOLDOWN = 60;
	/** Flux held in the phial it drops. */
	public static final int DROPS = 2;
	/** In daylight, after this many ticks of life, it fades away. */
	public static final int DAY_LIFE = 100;

	private int cooldown;

	public FluxWisp(EntityType<? extends FluxWisp> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.setNoGravity(true);
		this.xpReward = 1;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FLYING_SPEED, 0.25).add(Attributes.ATTACK_DAMAGE, 0.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new DriftGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.6));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		return navigation;
	}

	@Override
	protected float getFlyingSpeed() {
		return THRUST;
	}

	@Override
	public boolean causeFallDamage(double distance, float damageModifier, DamageSource source) {
		return false;
	}

	/** The sprite the renderer shows. */
	@Override
	public ItemStack getItem() {
		return new ItemStack(FluxEffects.WISP_ITEM);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level) {
			if (this.cooldown > 0) {
				this.cooldown--;
			}
			LivingEntity target = this.getTarget();
			if (this.cooldown == 0 && target instanceof ServerPlayer player && player.isAlive() && !player.isCreative() && !player.isSpectator()
				&& this.getBoundingBox().inflate(0.25).intersects(player.getBoundingBox())) {
				this.cooldown = TOUCH_COOLDOWN;
				Corruption.add(player, Corruption.Kind.LINGERING, TOUCH_CORRUPTION);
				player.hurtServer(level, this.damageSources().magic(), TOUCH_DAMAGE);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.wisp.touch"), true);
			}
			if (level.isBrightOutside() && this.tickCount > DAY_LIFE && this.random.nextInt(40) == 0) {
				level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.1, 0.1, 0.1, 0.01);
				this.discard();
			}
		} else if (this.random.nextInt(2) == 0) {
			this.level().addParticle(ParticleTypes.WITCH, this.getRandomX(0.4), this.getY() + 0.1 + this.random.nextDouble() * 0.3, this.getRandomZ(0.4),
				0.0, -0.01, 0.0);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, Phials.of(Essence.FLUX, DROPS));
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	/** Drifts straight at its target, slowly. */
	private static final class DriftGoal extends Goal {
		private final FluxWisp wisp;

		DriftGoal(FluxWisp wisp) {
			this.wisp = wisp;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.wisp.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.wisp.getTarget();
			if (target != null) {
				this.wisp.getMoveControl().setWantedPosition(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 0.8);
			}
		}
	}
}
