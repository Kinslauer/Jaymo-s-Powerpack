package com.jaymospowers.aether.monster;

import java.util.EnumSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The aechor plant: a squat, rooted flower that spits a ring of toxic darts at anyone close enough to see. Only a plant
 * tool (a hoe, shears or an axe) hurts it; a blow from anything else is refused with a word. Arrows, blasts and the
 * like hurt it as usual. Its petals drop only when a player kills it.
 */
public class AechorPlant extends PlantMonster {
	// ---- tuning ----
	public static final double HEALTH = 10.0, RANGE = 8.0;
	/** A ring of darts every 60 ticks, the bud swelling for the last 10; they leave 35 degrees up and fall in a ring. */
	public static final int RING = 10, CYCLE = 60, WIND_UP = 10;
	public static final double ELEVATION = 35.0;
	public static final float DART_SPEED = 0.65F;

	/** 1 while the bud swells. */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(AechorPlant.class, EntityDataSerializers.INT);

	public AechorPlant(EntityType<? extends AechorPlant> type, Level level) {
		super(type, level, 0, 0, 0);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return plantAttributes(HEALTH, RANGE);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new RingGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ACTION, 0);
	}

	/** Whether the bud is swelling for a shot. */
	public boolean budding() {
		return this.entityData.get(ACTION) == 1;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(AetherEffects.TOXIN) && !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
			ItemStack held = player.getMainHandItem();
			if (!held.is(ItemTags.HOES) && !held.is(ItemTags.AXES) && !held.is(Items.SHEARS)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.aether.plant_tool"));
				return false;
			}
		}
		return super.hurtServer(level, source, amount);
	}

	/** Every cycle, once it sees its target: swell, then spit a ring of darts. */
	private static final class RingGoal extends Goal {
		private final AechorPlant plant;
		private int tick;

		RingGoal(AechorPlant plant) {
			this.plant = plant;
			this.setFlags(EnumSet.of(Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.plant.getTarget();
			return target != null && target.isAlive() && this.plant.distanceTo(target) <= RANGE && this.plant.hasLineOfSight(target);
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public void stop() {
			this.tick = 0;
			this.plant.entityData.set(ACTION, 0);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			AechorPlant p = this.plant;
			this.tick++;
			if (this.tick == CYCLE - WIND_UP) {
				p.entityData.set(ACTION, 1);
			}
			if (this.tick >= CYCLE) {
				this.tick = 0;
				p.entityData.set(ACTION, 0);
				Vec3 from = p.position().add(0.0, p.getBbHeight() * 0.8, 0.0);
				double pitch = ELEVATION * Mth.DEG_TO_RAD;
				for (int i = 0; i < RING; i++) {
					double yaw = i * Mth.TWO_PI / RING;
					Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
					AetherShot.fire(AetherShot.Kind.TOXIC_DART, p, from, from.add(dir), DART_SPEED, 0.0F);
				}
				p.playSound(MonsterSounds.get("entity.llama.spit"), 1.0F, 0.7F);
			}
		}
	}
}
