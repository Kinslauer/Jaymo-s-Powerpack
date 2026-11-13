package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Saves;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The cloak flier (the darkmantle): a flier like a dark cloak that hangs from cave ceilings. It snuffs light: torches near it
 * are put out (they drop as sticks and coal), candles and campfires go dark. It drops on a lone walker and wraps their head,
 * blinding them and tightening; swatting it, or a pull of the empty hand ({@link Latches}), gets it off, and it cannot
 * stand a light carried in the hand: held fire makes it let go.
 */
public class CloakFlier extends Beast implements Clinger {
	static final double HEALTH = 14.0, SPEED = 0.42;
	/** It snuffs light this far round it (blocks), looking this often (ticks). */
	static final int SNUFF_RADIUS = 5, SNUFF_EVERY = 20;
	/** It wakes for something this near (blocks), and gives up the hunt beyond this. */
	static final double WAKES = 7.0, LOSES = 20.0;
	/** Wrapped: blinded; a squeeze this often (ticks) for this much; the longest it holds (ticks). */
	static final int SQUEEZE_EVERY = 30, HOLDS = 20 * 20;
	static final float SQUEEZE = 1.0F;
	/** A pull of the empty hand: the base chance (Might adds to it). */
	static final float PULL_BASE = 0.35F;
	/** Looks: hanging, hunting, wrapped. */
	public static final int HANGING = 0, HUNTING = 1, WRAPPED = 2;

	private @Nullable UUID preyId;
	private int wrapped;

	public CloakFlier(EntityType<? extends CloakFlier> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public @Nullable LivingEntity host(ServerLevel level) {
		return this.look() == WRAPPED && this.preyId != null && level.getEntity(this.preyId) instanceof LivingEntity l ? l : null;
	}

	@Override
	public boolean pulled(ServerLevel level, ServerPlayer by) {
		if (by.getRandom().nextFloat() < Math.min(0.95F, PULL_BASE + Saves.chance(by, Saves.Kind.GRAB))) {
			this.fallOff(level);
			return true;
		}
		LivingEntity host = this.host(level);
		if (host != null) {
			host.hurtServer(level, level.damageSources().mobAttack(this), SQUEEZE);
		}
		return false;
	}

	private void fallOff(ServerLevel level) {
		LivingEntity host = this.host(level);
		this.setLook(HUNTING);
		this.wrapped = 0;
		this.preyId = null;
		this.setNoGravity(false);
		if (host != null) {
			host.removeEffect(MobEffects.BLINDNESS);
			Vec3 away = this.position().subtract(host.position()).normalize().scale(0.4);
			this.setDeltaMovement(away.x, 0.1, away.z);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAT_HURT, SoundSource.HOSTILE, 0.8F, 0.5F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean wrapped = this.look() == WRAPPED;
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && wrapped && this.isAlive()) {
			this.fallOff(level);
		}
		return hurt;
	}

	private boolean prey(LivingEntity e) {
		return e.isAlive() && !(e instanceof Enemy) && (e instanceof Player p && !p.isCreative() && !p.isSpectator() || e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % SNUFF_EVERY == 0) {
			this.snuff(level);
		}
		switch (this.look()) {
			case HANGING -> {
				this.setNoGravity(true);
				this.setDeltaMovement(Vec3.ZERO);
				BlockPos roof = BlockPos.containing(this.getX(), this.getBoundingBox().maxY + 0.1, this.getZ());
				boolean lost = level.getBlockState(roof).isAir();
				if (lost || this.tickCount % 10 == 0) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(WAKES), this::prey)) {
						if (lost || this.hasLineOfSight(e)) {
							this.preyId = e.getUUID();
							this.setLook(HUNTING);
							break;
						}
					}
				}
			}
			case HUNTING -> this.hunt(level);
			default -> this.wrap(level);
		}
	}

	private void hunt(ServerLevel level) {
		LivingEntity prey = this.preyId != null && level.getEntity(this.preyId) instanceof LivingEntity l && l.isAlive() && l.distanceTo(this) < LOSES ? l : null;
		if (prey == null) {
			this.preyId = null;
			this.setLook(HANGING);
			// back up to the ceiling
			this.setDeltaMovement(0.0, 0.1, 0.0);
			return;
		}
		this.setNoGravity(true);
		Vec3 to = prey.getEyePosition().subtract(this.position());
		double d = to.length();
		Vec3 dir = d < 1.0E-4 ? Vec3.ZERO : to.scale(1.0 / d);
		Vec3 v = this.getDeltaMovement();
		this.setDeltaMovement(v.add(dir.scale(SPEED).subtract(v).scale(0.3)));
		this.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90.0F);
		if (d < 0.9 && !heldLight(prey)) {
			this.setLook(WRAPPED);
			this.wrapped = 0;
			level.playSound(null, prey.getX(), prey.getEyeY(), prey.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 0.8F, 0.6F);
		}
	}

	/** A torch or lantern held in either hand. */
	private static boolean heldLight(LivingEntity who) {
		return who.getMainHandItem().is(net.minecraft.world.item.Items.TORCH) || who.getOffhandItem().is(net.minecraft.world.item.Items.TORCH)
			|| who.getMainHandItem().is(net.minecraft.world.item.Items.SOUL_TORCH) || who.getOffhandItem().is(net.minecraft.world.item.Items.SOUL_TORCH)
			|| who.getMainHandItem().is(net.minecraft.world.item.Items.LANTERN) || who.getOffhandItem().is(net.minecraft.world.item.Items.LANTERN);
	}

	private void wrap(ServerLevel level) {
		LivingEntity host = this.host(level);
		if (host == null || !host.isAlive()) {
			this.fallOff(level);
			return;
		}
		this.wrapped++;
		this.setPos(host.getX(), host.getEyeY() - 0.2, host.getZ());
		this.setDeltaMovement(Vec3.ZERO);
		host.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, false), this);
		if (this.wrapped % SQUEEZE_EVERY == 0) {
			host.hurtServer(level, level.damageSources().mobAttack(this), SQUEEZE);
		}
		if (this.wrapped > HOLDS || heldLight(host)) {
			this.fallOff(level);
		}
	}

	/** Puts out the light round it: torches, candles, campfires. */
	private void snuff(ServerLevel level) {
		BlockPos at = this.blockPosition();
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-SNUFF_RADIUS, -SNUFF_RADIUS, -SNUFF_RADIUS), at.offset(SNUFF_RADIUS, SNUFF_RADIUS, SNUFF_RADIUS))) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)) {
				level.destroyBlock(p, true);
				n++;
			} else if (s.is(BlockTags.CANDLES) && s.getValue(CandleBlock.LIT)) {
				level.setBlock(p, s.setValue(CandleBlock.LIT, false), 3);
				n++;
			} else if (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)) {
				level.setBlock(p, s.setValue(CampfireBlock.LIT, false), 3);
				n++;
			}
			if (n >= 4) {
				break;
			}
		}
		if (n > 0) {
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 0.8F);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 8, 0.4, 0.3, 0.4, 0.02);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PHANTOM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PHANTOM_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Pose", this.look() == WRAPPED ? HUNTING : this.look());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setLook(input.getIntOr("Pose", HANGING));
	}
}
