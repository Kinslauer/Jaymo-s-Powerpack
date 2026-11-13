package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Warped Clam: a still, shelled thing of the End's outer islands, ported from Inhabitants (JeremySeq / Team Synapse,
 * MIT; the model, texture and animations are theirs, by Catalyst: kingdoms/art/warped_clam-CREDITS.md). The behaviour is
 * written fresh for Kingdoms after Inhabitants' own. Andy (2026-11-12): "warped clam in the end, and as a side effect
 * will spawn in arsenal pocket dimensions" (the pocket rooms lie on the End's side of the rift).
 *
 * <p>It never moves and never hurts anyone. Stand on it and it snaps open and throws you high into the air (good with an
 * elytra). Like a real clam it slowly grows a pearl, an ender pearl, about once every day or two, sooner beside chorus,
 * which it lives off; then it sits open, the pearl showing and glimmering. Brush the pearl out and it closes and starts
 * another. Strike it and it shuts tight and keeps shut a while; kill it and only a pearl it had grown is left. A shovel
 * eases it a block over; anyone can lift it and carry it off.
 */
public class WarpedClam extends PathfinderMob implements GeoEntity {
	static final double HEALTH = 20.0, ARMOR = 10.0;
	/** A pearl grows in this long, plus up to this much more (ticks); this much faster with chorus this near (blocks). */
	static final int PEARL_MIN = 24000, PEARL_SPREAD = 24000, CHORUS_RANGE = 6, CHORUS_SPEEDUP = 2;
	/** Stood on: thrown up this hard, at most this often (ticks). */
	static final double LAUNCH = 1.6;
	static final int LAUNCH_COOLDOWN = 20;
	/** Struck: it stays shut this long (ticks). */
	static final int SHUT_AFTER_HIT = 600;
	/** Spawning: never within this of another clam (blocks; a pocket room holds one at most). */
	static final double SPAWN_APART = 16.0;
	public static final int SPAWN_WEIGHT = 10;

	static final RawAnimation OPEN = RawAnimation.begin().thenPlay("Opening").thenLoop("Opened");
	static final RawAnimation CLOSE = RawAnimation.begin().thenPlay("closing");
	static final RawAnimation PUSH = RawAnimation.begin().thenPlay("Pushing");

	private static final EntityDataAccessor<Boolean> PEARL = SynchedEntityData.defineId(WarpedClam.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> OPEN_NOW = SynchedEntityData.defineId(WarpedClam.class, EntityDataSerializers.BOOLEAN);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** (Server) ticks of growth until its pearl is ready; ticks it keeps shut; ticks until it can throw again. */
	private int growth = -1, shut, launchCooldown;
	/** (Server) chorus near it, looked for now and then. */
	private boolean chorus;
	/** (Client) whether it has been shown open, so a clam that was always shut doesn't play its closing. */
	private boolean shownOpen;

	public WarpedClam(EntityType<? extends WarpedClam> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	/** Its natural spawn: on solid ground, and none other near. */
	public static boolean checkSpawn(EntityType<WarpedClam> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		if (!(level instanceof ServerLevel server) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
			return false;
		}
		return server.getEntitiesOfClass(WarpedClam.class, new AABB(pos).inflate(SPAWN_APART), Entity::isAlive).isEmpty();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(PEARL, false);
		entityData.define(OPEN_NOW, false);
	}

	/** Has it a pearl grown? */
	public boolean pearl() {
		return this.entityData.get(PEARL);
	}

	/** Is it sitting open? */
	public boolean open() {
		return this.entityData.get(OPEN_NOW);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.launchCooldown > 0) {
			this.launchCooldown--;
		}
		if (this.shut > 0) {
			this.shut--;
		}
		if (!this.pearl()) {
			if (this.growth < 0) {
				this.growth = PEARL_MIN + this.random.nextInt(PEARL_SPREAD);
			}
			if (this.tickCount % 200 == 1) {
				this.chorus = this.byChorus(level);
			}
			this.growth -= this.chorus ? CHORUS_SPEEDUP : 1;
			if (this.growth <= 0) {
				this.entityData.set(PEARL, true);
				this.growth = -1;
			}
		}
		boolean open = this.pearl() && this.shut <= 0;
		if (open != this.open()) {
			this.entityData.set(OPEN_NOW, open);
			this.playSound(open ? SoundEvents.SHULKER_OPEN : SoundEvents.SHULKER_CLOSE, 0.8F, 0.8F);
		}
		if (this.launchCooldown <= 0) {
			this.launchStanding(level);
		}
	}

	/** Chorus growing near it: what it lives off. */
	private boolean byChorus(ServerLevel level) {
		BlockPos at = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-CHORUS_RANGE, -2, -CHORUS_RANGE), at.offset(CHORUS_RANGE, 3, CHORUS_RANGE))) {
			if (level.getBlockState(pos).is(Blocks.CHORUS_PLANT) || level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
				return true;
			}
		}
		return false;
	}

	/** Anyone standing on its shell is thrown straight up. */
	private void launchStanding(ServerLevel level) {
		AABB shell = this.getBoundingBox();
		AABB top = new AABB(shell.minX, shell.maxY - 0.05, shell.minZ, shell.maxX, shell.maxY + 0.35, shell.maxZ);
		boolean threw = false;
		for (LivingEntity on : level.getEntitiesOfClass(LivingEntity.class, top, e -> e != this && e.isAlive() && !e.isSpectator()
			&& e.getBoundingBox().minY >= shell.maxY - 0.1 && e.getDeltaMovement().y <= 0.05)) {
			Vec3 motion = on.getDeltaMovement();
			on.setDeltaMovement(motion.x, LAUNCH, motion.z);
			on.needsSync = true;
			on.resetFallDistance();
			threw = true;
		}
		if (threw) {
			this.launchCooldown = LAUNCH_COOLDOWN;
			this.triggerAnim("push", "push");
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.NEUTRAL, 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 0.4, this.getZ(), 12, 0.4, 0.1, 0.4, 0.05);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() && this.pearl() && this.open() && this.random.nextInt(6) == 0) {
			this.level().addParticle(ParticleTypes.PORTAL, this.getX() + (this.random.nextDouble() - 0.5) * 0.3, this.getY() + 0.3,
				this.getZ() + (this.random.nextDouble() - 0.5) * 0.3, 0.0, 0.15, 0.0);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.BRUSH) && this.pearl() && this.open()) {
			if (this.level() instanceof ServerLevel level) {
				this.entityData.set(PEARL, false);
				this.growth = -1;
				ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
				if (!player.getInventory().add(pearl)) {
					this.spawnAtLocation(level, pearl);
				}
				held.hurtAndBreak(1, player, hand);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		if (held.is(ItemTags.SHOVELS)) {
			if (this.level() instanceof ServerLevel level) {
				Direction way = player.getDirection();
				BlockPos to = this.blockPosition().relative(way);
				if (level.noCollision(this, this.getBoundingBox().move(way.getStepX(), 0.0, way.getStepZ()))
					&& level.getBlockState(to.below()).isFaceSturdy(level, to.below(), Direction.UP)) {
					this.setPos(this.getX() + way.getStepX(), this.getY(), this.getZ() + way.getStepZ());
					level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHOVEL_FLATTEN, SoundSource.NEUTRAL, 0.8F, 0.8F);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.shut = SHUT_AFTER_HIT;
		}
		return hurt;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (this.pearl()) {
			this.spawnAtLocation(level, new ItemStack(Items.ENDER_PEARL));
		}
	}

	// ---- still as a stone ----

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return this.isAlive();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<WarpedClam>("shell", 3, test -> {
			WarpedClam clam = test.animatable();
			if (clam.open()) {
				clam.shownOpen = true;
				return test.setAndContinue(OPEN);
			}
			return clam.shownOpen ? test.setAndContinue(CLOSE) : PlayState.STOP;
		}));
		controllers.add(new AnimationController<WarpedClam>("push", test -> PlayState.STOP).triggerableAnim("push", PUSH));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SHULKER_HURT_CLOSED;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SHULKER_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("pearl", this.pearl());
		output.putInt("growth", this.growth);
		output.putInt("shut", this.shut);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(PEARL, input.getBooleanOr("pearl", false));
		this.growth = input.getIntOr("growth", -1);
		this.shut = input.getIntOr("shut", 0);
	}
}
