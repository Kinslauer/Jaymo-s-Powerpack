package com.jaymoskingdoms.beasts;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The satyrs (Andy, 2026-11-14, after Mythika's; our own make: none of Mythika's code, art or music). Goat below the
 * waist, man above, horned. They live in small groves in the woods and meadows and never stray far from where their
 * grove first met ({@link #HOME_RANGE}).
 * <p>
 * By day they wander. At dusk the grove gathers at a fire: the nearest lit campfire to its home, or, if there is none, one
 * the eldest lays itself (and puts out again at dawn). The eldest plays the reed pipes (our own tunes,
 * {@code sounds/satyr/pipes_*}); the rest dance round the fire or sit by it.
 * <p>
 * They are neutral. Strike one and the whole grove ({@link #GROVE_RANGE}) turns on you, the music stopping dead, until you
 * are dead, gone, or {@link #ANGER_TICKS} have passed: they kick, and charge with their horns down. Bring them something
 * to drink or sweet (the tag {@code jaymoskingdoms:satyr_gifts}: wine, mead, honey, berries) and the grove takes you as a
 * friend; at their fire a friend is given the revel ({@link #REVEL_RANGE}: regeneration and good luck while the pipes
 * play). Hitting them ends that.
 */
public class Satyr extends PathfinderMob implements GeoEntity {
	static final double HEALTH = 30.0, ARMOR = 2.0, DAMAGE = 5.0, SPEED = 0.3, FOLLOW = 32.0;
	/** How far from its grove's home it strays (blocks), and how far a grove's anger and friendship carry. */
	static final int HOME_RANGE = 24;
	static final double GROVE_RANGE = 32.0;
	/** How long a grove stays angry (ticks; 2 minutes), and how far off its foe has to get to be let go. */
	static final int ANGER_TICKS = 2400;
	static final double LET_GO = 48.0;
	/** The butt: from how far, how long, its lunge and its blow, how often. */
	static final double BUTT_MIN = 2.5, BUTT_MAX = 7.0, LUNGE = 0.85, BUTT_KNOCK = 1.4;
	static final int BUTT_TICKS = 16, BUTT_LUNGE_AT = 5, BUTT_HIT_AT = 8, BUTT_COOLDOWN = 80;
	static final float BUTT_DAMAGE = 7.0F;
	/** The revels: from what time of day to what (ticks of the day), how near the fire's home it may be, how near the fire they stand. */
	static final int DUSK = 11800, DAWN = 22800;
	static final double FIRE_SEARCH = 16.0, RING = 3.0, AT_FIRE = 4.5;
	/** How often a dancer or sitter changes its mind, and the gap the piper leaves between tunes (ticks). */
	static final int TURN_TICKS = 200, TUNE_GAP = 120;
	/** The revel a friend at their fire is given: how near, for how long each time (ticks). */
	static final double REVEL_RANGE = 10.0;
	static final int REVEL_TICKS = 120;
	static final int ANIMATION_BLEND = 4;
	/** What satyrs take as a gift. */
	public static final TagKey<Item> GIFTS = TagKey.create(Registries.ITEM, JaymosKingdoms.id("satyr_gifts"));
	/** Its coats: chestnut, black, and the grey of the eldest. */
	public static final int COATS = 3, GREY = 2;

	/** What a satyr is doing at the fire. */
	public static final byte NONE = 0, PIPE = 1, DANCE = 2, SIT = 3;

	private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Satyr.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> REVEL = SynchedEntityData.defineId(Satyr.class, EntityDataSerializers.BYTE);
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	private static final RawAnimation PIPING = RawAnimation.begin().thenLoop("pipe");
	private static final RawAnimation DANCING = RawAnimation.begin().thenLoop("dance");
	private static final RawAnimation SITTING = RawAnimation.begin().thenLoop("sit");
	private static final RawAnimation BUTT = RawAnimation.begin().thenPlay("butt");
	private static final RawAnimation KICK = RawAnimation.begin().thenPlay("kick");

	/** A grove: those that spawned together, and where. */
	record Grove(UUID id, BlockPos home) implements SpawnGroupData {
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private UUID grove = UUID.randomUUID();
	private @Nullable BlockPos home, fire;
	/** The eldest lays the grove's fire and plays; it remembers the fire it laid (to put out at dawn). */
	private boolean eldest, laidFire;
	private final Set<UUID> friends = new HashSet<>();
	private long angerUntil;
	private int buttLeft, buttCooldown, turn, tuneLeft, fireCheck, ringSlot;
	/** The tune it is playing, if any (to stop it if the grove is struck). */
	private @Nullable SoundEvent playing;

	public Satyr(EntityType<? extends Satyr> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.ATTACK_KNOCKBACK, 0.6);
	}

	/** Natural spawns: on grass in the light, like the woods' animals. */
	public static boolean checkSpawn(EntityType<Satyr> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(COAT, 0);
		entityData.define(REVEL, NONE);
	}

	/** (Both sides) which coat it wears. */
	public int coat() {
		return this.entityData.get(COAT);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Satyr>("move", ANIMATION_BLEND, test -> {
			Satyr satyr = test.animatable();
			if (test.isMoving()) {
				return test.setAndContinue(satyr.isAggressive() || satyr.entityData.get(REVEL) == DANCE ? RUN : WALK);
			}
			return switch (satyr.entityData.get(REVEL)) {
				case PIPE -> test.setAndContinue(PIPING);
				case DANCE -> test.setAndContinue(DANCING);
				case SIT -> test.setAndContinue(SITTING);
				default -> test.setAndContinue(IDLE);
			};
		}));
		controllers.add(new AnimationController<Satyr>("action", ANIMATION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("butt", BUTT).triggerableAnim("kick", KICK));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
			@Override
			public boolean canUse() {
				return Satyr.this.entityData.get(REVEL) == NONE && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	// ---- the grove ---------------------------------------------------------------------------------------------------

	/** Its grove-mates within {@link #GROVE_RANGE}, itself among them. */
	private List<Satyr> grove(ServerLevel level) {
		return level.getEntitiesOfClass(Satyr.class, this.getBoundingBox().inflate(GROVE_RANGE), s -> s.isAlive() && s.grove.equals(this.grove));
	}

	/** Someone struck one of the grove: they all go for them, and the music stops. */
	private void rouse(ServerLevel level, LivingEntity foe) {
		for (Satyr s : this.grove(level)) {
			s.friends.remove(foe.getUUID());
			s.angerUntil = level.getGameTime() + ANGER_TICKS;
			s.entityData.set(REVEL, NONE);
			s.stopTune(level);
			if (!s.isNoAi()) {
				s.setTarget(foe);
			}
		}
	}

	/** A gift: the whole grove takes the giver as a friend. */
	private void befriend(ServerLevel level, Player giver) {
		for (Satyr s : this.grove(level)) {
			s.friends.add(giver.getUUID());
		}
		level.sendParticles(ParticleTypes.HEART, this.getX(), this.getEyeY() + 0.4, this.getZ(), 5, 0.4, 0.3, 0.4, 0.0);
		this.playSound(SoundEvents.GOAT_AMBIENT, 1.0F, 1.3F);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(GIFTS) || this.getTarget() != null) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			this.befriend(level, player);
			held.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity foe && !(foe instanceof Satyr s && s.grove.equals(this.grove))
			&& !(foe instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.rouse(level, foe);
		}
		return hurt;
	}

	// ---- its days and nights ------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.buttCooldown--;
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || level.getGameTime() > this.angerUntil || this.distanceTo(target) > LET_GO
			|| target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(null);
			target = null;
		}
		this.setAggressive(target != null);
		if (target != null) {
			this.fight(level, target);
			return;
		}
		long time = level.getOverworldClockTime() % 24000L;
		boolean night = time >= DUSK && time < DAWN;
		if (night && this.home != null) {
			this.revel(level);
		} else {
			if (this.entityData.get(REVEL) != NONE) {
				this.entityData.set(REVEL, NONE);
			}
			this.stopTune(level);
			if (this.laidFire && this.fire != null) {
				// dawn: the eldest puts out the fire it laid
				BlockState state = level.getBlockState(this.fire);
				if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
					level.setBlock(this.fire, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
					level.playSound(null, this.fire, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.6F, 1.0F);
				}
				this.laidFire = false;
			}
			this.fire = null;
		}
	}

	/** At night: to the fire, and the eldest plays while the rest dance or sit. */
	private void revel(ServerLevel level) {
		if (--this.fireCheck <= 0 || this.fire != null && !lit(level, this.fire)) {
			this.fireCheck = 100;
			this.fire = this.findFire(level);
			if (this.fire == null && this.eldest) {
				this.fire = this.layFire(level);
			}
		}
		if (this.fire == null) {
			return;
		}
		Vec3 centre = Vec3.atBottomCenterOf(this.fire);
		double d = this.position().distanceTo(centre);
		byte revel = this.entityData.get(REVEL);
		if (d > AT_FIRE && revel != DANCE) {
			if (this.tickCount % 20 == 0) {
				Vec3 spot = this.ringSpot(centre, this.ringSlot);
				this.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9);
			}
			if (revel != NONE) {
				this.entityData.set(REVEL, NONE);
			}
			return;
		}
		if (this.eldest) {
			this.entityData.set(REVEL, PIPE);
			this.getNavigation().stop();
			this.getLookControl().setLookAt(centre.x, centre.y + 0.5, centre.z);
			this.pipe(level);
			return;
		}
		if (--this.turn <= 0 || revel == NONE) {
			this.turn = TURN_TICKS + this.random.nextInt(TURN_TICKS);
			this.entityData.set(REVEL, this.random.nextInt(3) == 0 ? SIT : DANCE);
		}
		if (this.entityData.get(REVEL) == DANCE) {
			// round the fire, a step of the ring at a time
			if (this.getNavigation().isDone()) {
				this.ringSlot = (this.ringSlot + 1) % 8;
				Vec3 spot = this.ringSpot(centre, this.ringSlot);
				this.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.75);
			}
		} else {
			this.getNavigation().stop();
			this.getLookControl().setLookAt(centre.x, centre.y + 0.5, centre.z);
		}
	}

	private Vec3 ringSpot(Vec3 centre, int slot) {
		double a = Math.PI * 2.0 * slot / 8.0 + (this.grove.hashCode() & 7);
		return centre.add(Math.cos(a) * RING, 0.0, Math.sin(a) * RING);
	}

	private static boolean lit(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
	}

	/** The nearest lit campfire within {@link #FIRE_SEARCH} of the grove's home. */
	private @Nullable BlockPos findFire(ServerLevel level) {
		if (this.home == null) {
			return null;
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int r = (int) FIRE_SEARCH;
		for (BlockPos pos : BlockPos.betweenClosed(this.home.offset(-r, -4, -r), this.home.offset(r, 4, r))) {
			if (lit(level, pos)) {
				double d = pos.distSqr(this.home);
				if (d < bestD) {
					bestD = d;
					best = pos.immutable();
				}
			}
		}
		return best;
	}

	/** The eldest lays a fire at the grove's home: on open dry ground, nothing in the way. */
	private @Nullable BlockPos layFire(ServerLevel level) {
		if (this.home == null) {
			return null;
		}
		for (int tries = 0; tries < 12; tries++) {
			BlockPos column = this.home.offset(this.random.nextInt(7) - 3, 0, this.random.nextInt(7) - 3);
			BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (Math.abs(at.getY() - this.home.getY()) > 3 || !level.getBlockState(at).isAir() || !level.getFluidState(at).isEmpty()
				|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
				|| level.getBlockState(at.below()).ignitedByLava()) {
				continue;
			}
			level.setBlock(at, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
			level.playSound(null, at, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			this.laidFire = true;
			return at;
		}
		return null;
	}

	/** The eldest's pipes: a tune, a rest, another; and the revel for any friend of the grove near the fire. */
	private void pipe(ServerLevel level) {
		if (--this.tuneLeft <= 0) {
			int i = this.random.nextInt(Satyrs.TUNES.size());
			this.playing = Satyrs.TUNES.get(i).value();
			level.playSound(null, this.getX(), this.getY(), this.getZ(), this.playing, SoundSource.NEUTRAL, Satyrs.TUNE_VOLUME, 1.0F);
			this.tuneLeft = Satyrs.TUNE_TICKS[i] + TUNE_GAP;
		}
		if (this.tickCount % 40 == 0 && this.tuneLeft > TUNE_GAP && this.fire != null) {
			for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(this.fire).inflate(REVEL_RANGE),
				p -> this.friends.contains(p.getUUID()))) {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REVEL_TICKS, 0, true, true));
				p.addEffect(new MobEffectInstance(MobEffects.LUCK, REVEL_TICKS, 0, true, true));
			}
		}
	}

	/** The pipes stop: the tune is cut off for everyone near. */
	private void stopTune(ServerLevel level) {
		if (this.playing == null) {
			return;
		}
		ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(this.playing.location(), SoundSource.NEUTRAL);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < 96.0 * 96.0) {
				p.connection.send(stop);
			}
		}
		this.playing = null;
		this.tuneLeft = 0;
	}

	// ---- fighting -------------------------------------------------------------------------------------------------------

	/** Kicks up close (its melee goal), and from a few strides off it lowers its horns and charges. */
	private void fight(ServerLevel level, LivingEntity target) {
		if (this.buttLeft > 0) {
			int run = BUTT_TICKS - this.buttLeft--;
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (run == BUTT_LUNGE_AT) {
				Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize();
				this.setDeltaMovement(to.x * LUNGE, 0.25, to.z * LUNGE);
				this.needsSync = true;
			} else if (run == BUTT_HIT_AT && this.distanceTo(target) < 2.2) {
				DamageSource source = this.damageSources().mobAttack(this);
				if (target.hurtServer(level, source, BUTT_DAMAGE)) {
					target.knockback(BUTT_KNOCK, this.getX() - target.getX(), this.getZ() - target.getZ(), source, BUTT_DAMAGE);
					this.playSound(SoundEvents.GOAT_RAM_IMPACT, 1.0F, 1.0F);
				}
			}
			return;
		}
		double d = this.distanceTo(target);
		if (d > BUTT_MIN && d < BUTT_MAX && this.buttCooldown <= 0 && this.onGround() && this.hasLineOfSight(target)) {
			this.buttLeft = BUTT_TICKS;
			this.buttCooldown = BUTT_COOLDOWN;
			this.getNavigation().stop();
			this.triggerAnim("action", "butt");
			this.playSound(SoundEvents.GOAT_PREPARE_RAM, 1.0F, 1.0F);
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		this.triggerAnim("action", "kick");
		return super.doHurtTarget(level, target);
	}

	// ---- coming into the world, and saving ------------------------------------------------------------------------------

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		if (data instanceof Grove g) {
			this.grove = g.id();
			this.home = g.home();
			this.entityData.set(COAT, this.random.nextInt(GREY));
		} else {
			// the first of a grove is its eldest, and its home is where it stands
			this.grove = UUID.randomUUID();
			this.home = this.blockPosition();
			this.eldest = true;
			this.entityData.set(COAT, GREY);
			data = new Grove(this.grove, this.home);
		}
		this.setHomeTo(this.home, HOME_RANGE);
		return data;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("grove", UUIDUtil.CODEC, this.grove);
		if (this.home != null) {
			output.putLong("grove_home", this.home.asLong());
		}
		if (this.fire != null && this.laidFire) {
			output.putLong("laid_fire", this.fire.asLong());
		}
		output.putBoolean("eldest", this.eldest);
		output.putInt("coat", this.coat());
		output.putLong("anger_until", this.angerUntil);
		output.store("friends", UUIDUtil.CODEC.listOf(), List.copyOf(this.friends));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.grove = input.read("grove", UUIDUtil.CODEC).orElse(this.grove);
		long home = input.getLongOr("grove_home", Long.MIN_VALUE);
		if (home != Long.MIN_VALUE) {
			this.home = BlockPos.of(home);
			this.setHomeTo(this.home, HOME_RANGE);
		}
		long fire = input.getLongOr("laid_fire", Long.MIN_VALUE);
		if (fire != Long.MIN_VALUE) {
			this.fire = BlockPos.of(fire);
			this.laidFire = true;
		}
		this.eldest = input.getBooleanOr("eldest", false);
		this.entityData.set(COAT, Math.clamp(input.getIntOr("coat", 0), 0, COATS - 1));
		this.angerUntil = input.getLongOr("anger_until", 0L);
		this.friends.clear();
		input.read("friends", UUIDUtil.CODEC.listOf()).ifPresent(this.friends::addAll);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.level() instanceof ServerLevel level) {
			this.stopTune(level);
		}
		super.remove(reason);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.entityData.get(REVEL) == PIPE ? null : SoundEvents.GOAT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GOAT_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.85F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.GOAT_STEP, 0.4F, 1.1F);
	}
}
