package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import java.util.EnumSet;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Medieval picks (me-goats): the great goat of the high mountains, the Stoneborn's mount (lore: "The realm steels"; only a
 * Stoneborn can tame and ride one). Living-thing rules (CLAUDE.md):
 * <ul>
 * <li><b>Sympathy</b>: as a mountain goat, it is sure-footed (it steps up a block and a half, jumps high and shrugs off
 * falls), it grazes the grass and plants of the slopes, it keeps with its herd ({@code jaymoskingdoms:herds_together}),
 * and a goat that's struck butts back.</li>
 * <li><b>Feedback loops</b>: what it grazes is gone: short grass is eaten and grass is cropped to dirt, which only
 * grows back from the turf round it. A herd that has cropped its slope bare moves on, uphill, to fresh grazing.</li>
 * <li><b>Stewardship</b>: a tamed goat a keeper feeds grows to trust them ({@link #MAX_TRUST}): every point makes it a
 * little surer and quicker under its rider, one that trusts enough gives milk to its Stoneborn keeper, and a trusting pair
 * fed their wheat will breed (readiest in autumn, the goats' rut, with Kitchen's seasons). A goat left
 * alone just goes on grazing: nothing is lost.</li>
 * </ul>
 * Anyone else who tries to mount one, saddle it or open its packs gets reared at. Our own model and coat
 * ({@code client/beasts/GreatGoatModel}, {@code tools/gen_great_goat.py}).
 */
public class GreatGoat extends AbstractHorse {
	// ---- tuning ----
	/** Its body: health, speed, jump, how high it steps, and the fall it takes no harm from (blocks). */
	public static final double HEALTH = 30.0, SPEED = 0.2, JUMP = 0.8, STEP = 1.5, SAFE_FALL = 10.0;
	/** Grazing: how far it looks for something to crop (blocks), one look in this many ticks, and the bare looks that send it on. */
	static final int GRAZE_RANGE = 5, GRAZE_CHANCE = 240, BARE_LOOKS = 3;
	/** How far a herd moves on from a bare slope (blocks), and the spots it weighs (it takes the highest). */
	static final int MOVE_ON = 24, MOVE_ON_TRIES = 6;
	/** Its butt: the damage, the shove, and ticks before it butts again. */
	static final float BUTT_DAMAGE = 3.0F;
	static final double BUTT_SHOVE = 1.0, BUTT_REACH = 3.5;
	static final int BUTT_COOLDOWN = 40;
	/** Trust from each feeding of a tamed goat, the most it holds, and what each point adds to its jump and speed. */
	public static final int TRUST_PER_FEED = 1, MAX_TRUST = 20;
	/** Ticks between feedings that count toward trust (so a whole stack at once is no quicker than a few handfuls). */
	static final long TRUST_FEED_TICKS = 100L;
	static final double TRUST_JUMP = 0.008, TRUST_SPEED = 0.002;
	/** Ticks a graze takes, head down, before it looks up and moves again. */
	static final int GRAZE_ANIM_TICKS = 50;
	/** Trust before a keeper's goat can be brought into season with its food; in autumn (the rut, with Kitchen's seasons), this. */
	static final int BREED_TRUST = 8, BREED_TRUST_AUTUMN = 4;
	/** Trust before it gives its keeper milk, and ticks between milkings. */
	static final int MILK_TRUST = 5;
	static final long MILK_TICKS = 6000L;
	/** Natural spawns in the mountains: weight and herd size. */
	static final int SPAWN_WEIGHT = 5, SPAWN_LEAST = 2, SPAWN_MOST = 4;

	static final Identifier TRUST_JUMP_ID = JaymosKingdoms.id("great_goat_trust_jump"), TRUST_SPEED_ID = JaymosKingdoms.id("great_goat_trust_speed");

	public static EntityType<GreatGoat> TYPE;

	private int trust;
	private long lastMilked = -MILK_TICKS;
	private long lastTrustFeed = -TRUST_FEED_TICKS;
	private int bareLooks, buttCooldown, grazeTicks;

	public GreatGoat(EntityType<? extends GreatGoat> type, Level level) {
		super(type, level);
	}

	/** From {@link Beasts#register}. */
	static void register() {
		TYPE = Beasts.type("great_goat", EntityType.Builder.of(GreatGoat::new, MobCategory.CREATURE).sized(1.2F, 1.7F).clientTrackingRange(10),
			createAttributes());
		SpawnPlacements.register(TYPE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Goat::checkGoatSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN), MobCategory.CREATURE, TYPE, SPAWN_WEIGHT, SPAWN_LEAST, SPAWN_MOST);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createBaseHorseAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.JUMP_STRENGTH, JUMP)
			.add(Attributes.STEP_HEIGHT, STEP)
			.add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL);
	}

	/** Whether {@code player} may tame, ride and keep one: a Stoneborn (or a creative player). */
	public static boolean stoneborn(Player player) {
		return player.isCreative() || Heritage.is(player, Heritage.Kind.STONEBORN);
	}

	public int trust() {
		return trust;
	}

	@Override
	protected void addBehaviourGoals() {
		super.addBehaviourGoals();
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, stack -> stack.is(ItemTags.GOAT_FOOD), false));
		this.goalSelector.addGoal(5, new GrazeGoal());
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.GOAT_FOOD) || stack.is(Items.HAY_BLOCK);
	}

	/** It crops grass itself ({@link GrazeGoal}), not the horse's idle nibble. */
	@Override
	public boolean canEatGrass() {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (this.isBaby() || this.isVehicle()) {
			return super.mobInteract(player, hand);
		}
		// its keeper's reward: milk, once it trusts them
		if (stack.is(Items.BUCKET) && this.isTamed() && stoneborn(player) && this.trust >= MILK_TRUST
			&& this.level().getGameTime() - this.lastMilked >= MILK_TICKS) {
			if (!this.level().isClientSide()) {
				this.lastMilked = this.level().getGameTime();
				player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, Items.MILK_BUCKET.getDefaultInstance()));
				this.playSound(SoundEvents.GOAT_MILK, 1.0F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
		// anyone may feed one
		if (this.isFood(stack)) {
			return this.feed(player, stack);
		}
		// nobody but a Stoneborn gets on its back, saddles it or opens its packs: it rears at them
		if (!stoneborn(player)) {
			if (!this.level().isClientSide()) {
				this.makeMad();
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	/**
	 * A handful of its food: what a horse takes from it (healing, growing, temper), plus, for a tamed goat, a little trust
	 * (at most once every {@link #TRUST_FEED_TICKS}), and a keeper's goat that trusts enough comes into season (autumn is
	 * the rut). Eaten if any of that took.
	 */
	private InteractionResult feed(Player player, ItemStack stack) {
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean ate = this.handleEating(player, stack);
		long now = this.level().getGameTime();
		if (this.isTamed() && this.trust < MAX_TRUST && now - this.lastTrustFeed >= TRUST_FEED_TICKS) {
			this.lastTrustFeed = now;
			this.trust = Math.min(MAX_TRUST, this.trust + TRUST_PER_FEED);
			this.applyTrust();
			ate = true;
		}
		if (this.isTamed() && stoneborn(player) && this.getAge() == 0 && !this.isInLove() && this.trust >= this.breedTrust()) {
			this.setInLove(player);
			ate = true;
		}
		if (!ate) {
			return InteractionResult.PASS;
		}
		stack.consume(1, player);
		this.playSound(SoundEvents.GOAT_EAT, 1.0F, 0.8F);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** The trust its keeper needs to bring it into season: less in autumn, when real goats rut. */
	private int breedTrust() {
		return "autumn".equals(com.jaymoskingdoms.KitchenCompat.season(this.level())) ? BREED_TRUST_AUTUMN : BREED_TRUST;
	}

	/** Its trust in its keeper, in its legs. */
	private void applyTrust() {
		var jump = this.getAttribute(Attributes.JUMP_STRENGTH);
		var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (jump != null) {
			jump.addOrReplacePermanentModifier(new AttributeModifier(TRUST_JUMP_ID, this.trust * TRUST_JUMP, AttributeModifier.Operation.ADD_VALUE));
		}
		if (speed != null) {
			speed.addOrReplacePermanentModifier(new AttributeModifier(TRUST_SPEED_ID, this.trust * TRUST_SPEED, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		if (this.buttCooldown > 0) {
			this.buttCooldown--;
		}
		// it grazes itself (canEatGrass is false), so the horse's own reset never runs: lift its head after a graze or a feed
		if (!this.isEating()) {
			this.grazeTicks = 0;
		} else if (++this.grazeTicks > GRAZE_ANIM_TICKS) {
			this.grazeTicks = 0;
			this.setEating(false);
		}
		// however anyone else got on (a lead, a command), it won't carry them
		if (this.getFirstPassenger() instanceof Player rider && !stoneborn(rider)) {
			rider.stopRiding();
			this.makeMad();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive() && !this.isBaby() && this.buttCooldown <= 0 && source.getEntity() instanceof LivingEntity attacker
			&& attacker != this.getFirstPassenger() && attacker.isAlive() && this.distanceTo(attacker) <= BUTT_REACH) {
			// struck, it lowers its horns and butts back
			this.buttCooldown = BUTT_COOLDOWN;
			this.lookAt(attacker, 180.0F, 30.0F);
			attacker.hurtServer(level, this.damageSources().mobAttack(this), BUTT_DAMAGE);
			attacker.knockback(BUTT_SHOVE, this.getX() - attacker.getX(), this.getZ() - attacker.getZ(), this.damageSources().mobAttack(this), BUTT_DAMAGE);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 1.0F, 0.8F);
		}
		return hurt;
	}

	@Override
	public boolean canMate(Animal partner) {
		return partner != this && partner instanceof GreatGoat other && this.canParent() && other.canParent();
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		GreatGoat kid = TYPE.create(level, EntitySpawnReason.BREEDING);
		if (kid != null) {
			this.setOffspringAttributes(partner, kid);
		}
		return kid;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("trust", this.trust);
		output.putLong("last_milked", this.lastMilked);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.trust = input.getIntOr("trust", 0);
		this.lastMilked = input.getLongOr("last_milked", -MILK_TICKS);
		// a goat saved mid-graze (or frozen by the old missing reset) gets its head up
		this.setEating(false);
		this.applyTrust();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.GOAT_AMBIENT;
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
	protected @Nullable SoundEvent getEatingSound() {
		return SoundEvents.GOAT_EAT;
	}

	@Override
	protected @Nullable SoundEvent getAngrySound() {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.75F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (!state.liquid()) {
			this.playSound(SoundEvents.GOAT_STEP, 0.3F, 0.8F);
		}
	}

	@Override
	protected void playJumpSound() {
		this.playSound(SoundEvents.GOAT_LONG_JUMP, 0.5F, 0.8F);
	}

	/** Something it can crop: short grass, tall grass and ferns go whole; a grass block is cropped to dirt. */
	private static boolean grazeable(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.GRASS_BLOCK);
	}

	/**
	 * Now and then, when nobody rides it, the goat looks round its feet for something to crop and walks to it; at the spot
	 * it crops it (a grass block to dirt) and is fed by it. A goat that finds its slope bare {@link #BARE_LOOKS} times running
	 * moves on, uphill.
	 */
	final class GrazeGoal extends Goal {
		private @Nullable BlockPos target;
		private int ticks;

		GrazeGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (GreatGoat.this.isVehicle() || GreatGoat.this.isLeashed() || GreatGoat.this.getRandom().nextInt(GRAZE_CHANCE) != 0) {
				return false;
			}
			this.target = this.find();
			if (this.target == null) {
				if (++GreatGoat.this.bareLooks >= BARE_LOOKS) {
					GreatGoat.this.bareLooks = 0;
					this.moveOn();
				}
				return false;
			}
			GreatGoat.this.bareLooks = 0;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.target != null && this.ticks > 0 && !GreatGoat.this.isVehicle();
		}

		@Override
		public void start() {
			this.ticks = 200;
			BlockPos t = this.target;
			GreatGoat.this.getNavigation().moveTo(t.getX() + 0.5, t.getY() + 1, t.getZ() + 0.5, 0.8);
		}

		@Override
		public void stop() {
			this.target = null;
			GreatGoat.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			this.ticks--;
			BlockPos t = this.target;
			if (t == null) {
				return;
			}
			GreatGoat.this.getLookControl().setLookAt(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
			if (GreatGoat.this.distanceToSqr(t.getX() + 0.5, t.getY() + 1, t.getZ() + 0.5) > 2.6) {
				return;
			}
			Level level = GreatGoat.this.level();
			BlockState state = level.getBlockState(t);
			if (grazeable(state)) {
				// the sound and the bits of the block, then the block gone (or cropped to dirt)
				level.levelEvent(2001, t, net.minecraft.world.level.block.Block.getId(state));
				if (state.is(Blocks.GRASS_BLOCK)) {
					level.setBlock(t, Blocks.DIRT.defaultBlockState(), 2);
				} else {
					level.destroyBlock(t, false);
				}
				GreatGoat.this.heal(1.0F);
				GreatGoat.this.setEating(true);
				GreatGoat.this.playSound(SoundEvents.GOAT_EAT, 0.6F, 0.8F);
				if (GreatGoat.this.isBaby()) {
					GreatGoat.this.ageUp(20);
				}
			}
			this.target = null;
		}

		/** The nearest thing to crop round its feet (plants before turf), or null. */
		private @Nullable BlockPos find() {
			BlockPos at = GreatGoat.this.blockPosition();
			Level level = GreatGoat.this.level();
			BlockPos best = null;
			double bestDist = Double.MAX_VALUE;
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-GRAZE_RANGE, -2, -GRAZE_RANGE), at.offset(GRAZE_RANGE, 1, GRAZE_RANGE))) {
				BlockState state = level.getBlockState(pos);
				if (!grazeable(state) || state.is(Blocks.GRASS_BLOCK) && !level.getBlockState(pos.above()).isAir()) {
					continue;
				}
				double d = pos.distSqr(at) + (state.is(Blocks.GRASS_BLOCK) ? 4.0 : 0.0);
				if (d < bestDist) {
					bestDist = d;
					best = pos.immutable();
				}
			}
			return best;
		}

		/** Off to fresh grazing: the highest of a few spots a walk away. */
		private void moveOn() {
			Level level = GreatGoat.this.level();
			BlockPos at = GreatGoat.this.blockPosition();
			BlockPos best = null;
			for (int i = 0; i < MOVE_ON_TRIES; i++) {
				double angle = GreatGoat.this.getRandom().nextDouble() * Math.PI * 2.0;
				BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					at.offset((int) (Math.cos(angle) * MOVE_ON), 0, (int) (Math.sin(angle) * MOVE_ON)));
				if (best == null || spot.getY() > best.getY()) {
					best = spot;
				}
			}
			if (best != null) {
				GreatGoat.this.getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 0.9);
			}
		}
	}
}
