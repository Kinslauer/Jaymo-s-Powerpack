package com.jaymoskingdoms.monsters;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A block of the big golem's ({@link BigGolem}) in the air: either pulled out of the ground toward the golem that called
 * it (it flies straight to its chest and joins its body), or hurled from one of its arms at someone (it flies in an arc,
 * hurts what it hits and breaks on what it lands on, leaving the block as an item). Neither is free: the first costs the
 * ground a block, the second costs the golem a block of its body.
 */
public class GolemRock extends Entity {
	/** How fast a pulled block flies (blocks a tick), and how near the golem's chest it must come to join it. */
	static final double PULL_SPEED = 0.45, JOIN_RANGE = 1.4;
	/** A hurled block: its pull of gravity (blocks a tick squared), and what it does to what it hits. */
	static final double GRAVITY = 0.04;
	static final float DAMAGE = 6.0F, DAMAGE_MORE_ON_HARD = 2.0F;
	static final double THROW_KNOCKBACK = 0.9;
	/** Gives up (ticks): a pulled block that never arrives is dropped. */
	static final int MAX_PULL_AGE = 200, MAX_THROW_AGE = 120;

	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(GolemRock.class, EntityDataSerializers.INT);

	private boolean hurled;
	private @Nullable UUID golem;

	public GolemRock(EntityType<? extends GolemRock> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A block pulled up out of the ground at {@code from}, bound for {@code golem}. */
	static GolemRock pulled(ServerLevel level, BlockPos from, BlockState state, BigGolem golem) {
		GolemRock rock = new GolemRock(MocBeasts.GOLEM_ROCK, level);
		rock.setPos(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
		rock.entityData.set(DATA_STATE, Block.getId(state));
		rock.golem = golem.getUUID();
		return rock;
	}

	/** A block hurled from {@code at} along {@code velocity}. */
	static GolemRock hurled(ServerLevel level, Vec3 at, BlockState state, BigGolem golem, Vec3 velocity) {
		GolemRock rock = new GolemRock(MocBeasts.GOLEM_ROCK, level);
		rock.setPos(at);
		rock.entityData.set(DATA_STATE, Block.getId(state));
		rock.golem = golem.getUUID();
		rock.hurled = true;
		rock.setDeltaMovement(velocity);
		return rock;
	}

	public BlockState blockState() {
		return Block.stateById(this.entityData.get(DATA_STATE));
	}

	private @Nullable BigGolem owner(ServerLevel level) {
		return this.golem != null && level.getEntity(this.golem) instanceof BigGolem g && g.isAlive() ? g : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.hurled) {
			this.fly(level);
		} else {
			this.draw(level);
		}
	}

	/** On its way to the golem. */
	private void draw(ServerLevel level) {
		BigGolem owner = this.owner(level);
		if (owner == null || this.tickCount > MAX_PULL_AGE) {
			this.fall(level);
			return;
		}
		Vec3 to = owner.chestPosition().subtract(this.position());
		double distance = to.length();
		if (distance < JOIN_RANGE) {
			if (!owner.receiveRock(this.blockState())) {
				this.fall(level);
				return;
			}
			this.discard();
			return;
		}
		Vec3 step = to.scale(Math.min(PULL_SPEED, distance) / distance);
		this.setDeltaMovement(step);
		this.setPos(this.position().add(step));
		if (this.tickCount % 4 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.blockState()), this.getX(), this.getY() + 0.5, this.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
		}
	}

	/** Flying at someone. */
	private void fly(ServerLevel level) {
		Vec3 v = this.getDeltaMovement().add(0.0, -GRAVITY, 0.0);
		this.setDeltaMovement(v);
		this.setPos(this.position().add(v));
		BigGolem owner = this.owner(level);
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.35), e -> e != owner && e.isAlive() && !(e instanceof BigGolem));
		if (!hit.isEmpty()) {
			LivingEntity victim = hit.get(0);
			DamageSource source = owner != null ? level.damageSources().mobAttack(owner) : level.damageSources().generic();
			float damage = DAMAGE + (level.getDifficulty() == net.minecraft.world.Difficulty.HARD ? DAMAGE_MORE_ON_HARD : 0.0F);
			victim.hurtServer(level, source, damage);
			victim.push(v.x * THROW_KNOCKBACK, 0.3, v.z * THROW_KNOCKBACK);
			victim.syncVelocity = true;
			this.shatter(level);
			return;
		}
		BlockPos at = BlockPos.containing(this.position());
		if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || this.tickCount > MAX_THROW_AGE) {
			this.shatter(level);
		}
	}

	/** It breaks on what it hit, leaving the block as an item. */
	private void shatter(ServerLevel level) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.blockState()), this.getX(), this.getY(), this.getZ(), 14, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, this.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.2F, 0.8F);
		this.fall(level);
	}

	/** It comes to nothing in the air: the block is dropped as an item where it is. */
	private void fall(ServerLevel level) {
		Block.popResource(level, this.blockPosition(), new ItemStack(this.blockState().getBlock()));
		this.discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_STATE, 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(input.getStringOr("block", "minecraft:stone")));
		this.entityData.set(DATA_STATE, Block.getId(block.defaultBlockState()));
		this.hurled = input.getBooleanOr("hurled", false);
		this.golem = input.read("golem", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("block", BuiltInRegistries.BLOCK.getKey(this.blockState().getBlock()).toString());
		output.putBoolean("hurled", this.hurled);
		if (this.golem != null) {
			output.store("golem", UUIDUtil.CODEC, this.golem);
		}
	}
}
