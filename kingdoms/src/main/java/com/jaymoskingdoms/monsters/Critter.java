package com.jaymoskingdoms.monsters;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.DifficultyInstance;
import com.jaymoskingdoms.SmallLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The small life of Mo' Creatures (after its cricket, snail, fly, maggot, roach, mouse and mole, GPL-3, see
 * {@code licenses/mocreatures}): one body for all seven, told apart by {@link Kind}. None of them lives in the world by
 * itself: {@link SmallLife} brings each where the world gives it a reason (crickets on summer-warm nights in the grass,
 * snails after rain, flies and maggots at bodies left in the open, roaches in a filthy home, mice in a grain store, moles in
 * a field) and sends it away when the reason is gone. Each carries a {@link #life} that runs down whatever happens, so
 * nothing piles up, and is cheap: no persistence, a one-point health, no goals beyond walking about and keeping out of
 * the way.
 *
 * <p>Andy: "you should be able to pickup maggots as an item for bait". A tap of use on a maggot takes it off the body alive,
 * as one of Jaymo's Powers' maggots for the hook ({@link #MAGGOT_BAIT}); a stomp still bursts it (SpiderSquash). Without
 * Powers there's no bait to take, and use does nothing.
 * <p>Andy: "the maggot entity always drops maggots plural, the entity should be drawn as multiple maggots". A maggot is a
 * heap of them, writhing over each other ({@code MaggotModel}), half a block across: the whole heap is taken for one bait
 * and bursts together underfoot.
 */
public class Critter extends PathfinderMob {
	/** The seven kinds: the size of their body, whether they fly, how many coats (textures) each has, and its health. */
	public enum Kind {
		CRICKET("cricket", 0.35F, 0.3F, false, 2, 1.0), SNAIL("snail", 0.3F, 0.3F, false, 6, 1.0), FLY("fly", 0.15F, 0.12F, true, 1, 1.0),
		MAGGOT("maggot", 0.5F, 0.15F, false, 1, 1.0), ROACH("roach", 0.4F, 0.25F, false, 1, 2.0), MOUSE("mouse", 0.3F, 0.3F, false, 3, 2.0),
		MOLE("mole", 0.5F, 0.45F, false, 1, 5.0);

		public final String id;
		public final float width, height;
		public final boolean flies;
		public final int coats;
		public final double health;

		Kind(String id, float width, float height, boolean flies, int coats, double health) {
			this.id = id;
			this.width = width;
			this.height = height;
			this.flies = flies;
			this.coats = coats;
			this.health = health;
		}
	}

	/** A maggot taken alive becomes this bait (Jaymo's Powers' fishing, looked up by its id). */
	static final String MAGGOT_BAIT = "jaymospowers:maggots";

	/** How long one lasts at most (ticks) unless the world keeps it; the movement speeds. */
	static final int LIFE_TICKS = 12000;
	static final double WALK = 0.2, SNAIL = 0.04, FLIGHT = 0.4;
	/** A snail never jumps: against a ledge it slides up the face this fast (blocks a tick: a block in under two seconds),
	 * not at a spider's scramble. */
	static final double SNAIL_CLIMB = 0.03;

	private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Critter.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> HIDING = SynchedEntityData.defineId(Critter.class, EntityDataSerializers.BOOLEAN);

	public final Kind kind;
	private int life = LIFE_TICKS;
	private int hills;
	/** What it came for: the body, the stain, the store or the field (the corpse's spot, a stain, a barrel, a hill). */
	private @Nullable BlockPos anchor;

	public Critter(EntityType<? extends Critter> type, Level level, Kind kind) {
		super(type, level);
		this.kind = kind;
		if (kind.flies) {
			this.moveControl = new FlyingMoveControl<>(this, 20, true);
		}
		this.initGoals();
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes(Kind kind) {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, kind.health)
			.add(Attributes.MOVEMENT_SPEED, kind == Kind.SNAIL ? SNAIL : kind.flies ? FLIGHT : WALK).add(Attributes.FLYING_SPEED, FLIGHT);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COAT, 0);
		builder.define(HIDING, false);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		// (asked for while the body is still being made, before its kind is set: the fly is known by its type)
		if (this.getType() == MocWild.FLY) {
			FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
			nav.setCanFloat(true);
			return nav;
		}
		return super.createNavigation(level);
	}

	/** (Its goals are set once its kind is known: the game asks for them while the body is still being made.) */
	private void initGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		if (this.kind.flies) {
			this.goalSelector.addGoal(2, new HoverNearGoal());
			this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0));
			return;
		}
		if (this.kind != Kind.SNAIL && this.kind != Kind.MAGGOT) {
			this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
			this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 3.0F, 1.0, 1.5, p -> !p.isShiftKeyDown()));
		}
		if (this.kind == Kind.MOUSE) {
			this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Cat.class, 8.0F, 1.2, 1.6));
		}
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
	}

	public int coat() {
		return this.entityData.get(COAT);
	}

	public void setCoat(int coat) {
		this.entityData.set(COAT, Math.floorMod(coat, this.kind.coats));
	}

	/** A snail drawn into its shell. */
	public boolean hiding() {
		return this.entityData.get(HIDING);
	}

	public void setHiding(boolean hiding) {
		this.entityData.set(HIDING, hiding);
	}

	/** A mole's hills heaped so far (not kept past a reload: the world keeps the hills). */
	public int hills() {
		return this.hills;
	}

	public void heaped() {
		this.hills++;
	}

	public @Nullable BlockPos anchor() {
		return this.anchor;
	}

	public void setAnchor(@Nullable BlockPos anchor) {
		this.anchor = anchor;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (--this.life <= 0) {
			this.discard();
			return;
		}
		if (this.kind == Kind.SNAIL && this.hiding()) {
			// drawn into its shell it stays put until it comes out again
			this.getNavigation().stop();
		}
		if (this.tickCount % SmallLife.EVERY == 0) {
			SmallLife.act(level, this);
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		this.setCoat(this.random.nextInt(this.kind.coats));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.kind != Kind.MAGGOT || hand != InteractionHand.MAIN_HAND || !this.isAlive()) {
			return super.mobInteract(player, hand);
		}
		var bait = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(MAGGOT_BAIT));
		if (bait.isEmpty()) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			// picked off the body between finger and thumb, still wriggling, into the bait in your pack
			player.getInventory().placeItemBackInInventory(new ItemStack(bait.get()), net.minecraft.util.Prediction.SERVER_ONLY);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH_SMALL, this.getSoundSource(), 0.5F, 1.6F);
			this.discard();
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Andy: "the snails seem to jump like frogs or crickets instead of sliding." A snail glides on its foot: it never
	 * jumps (the game would hop it up every ledge on its path), it climbs a ledge by sliding up its face
	 * ({@link #onClimbable}), and drawn into its shell it doesn't move at all.
	 */
	@Override
	public void jumpFromGround() {
		if (this.kind != Kind.SNAIL) {
			super.jumpFromGround();
		}
	}

	/** A snail against a block's face climbs it, as real ones go up walls. */
	@Override
	public boolean onClimbable() {
		return this.kind == Kind.SNAIL ? this.horizontalCollision && !this.hiding() : super.onClimbable();
	}

	@Override
	public void travel(net.minecraft.world.phys.Vec3 input) {
		super.travel(this.kind == Kind.SNAIL && this.hiding() ? net.minecraft.world.phys.Vec3.ZERO : input);
		if (this.kind == Kind.SNAIL && this.horizontalCollision) {
			// (the game lifts any climber at a spider's pace: a snail creeps up instead)
			net.minecraft.world.phys.Vec3 move = this.getDeltaMovement();
			if (move.y > SNAIL_CLIMB) {
				this.setDeltaMovement(move.x, SNAIL_CLIMB, move.z);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return switch (this.kind) {
			case MOUSE -> SoundEvents.SILVERFISH_AMBIENT;
			case MOLE -> SoundEvents.ARMADILLO_AMBIENT;
			default -> null;
		};
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("coat", this.coat());
		output.putInt("life", this.life);
		if (this.anchor != null) {
			output.putLong("anchor", this.anchor.asLong());
		}
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setCoat(input.getIntOr("coat", 0));
		this.life = input.getIntOr("life", LIFE_TICKS);
		this.anchor = input.getLong("anchor").map(BlockPos::of).orElse(null);
	}

	public void setLife(int ticks) {
		this.life = ticks;
	}

	/** A fly keeps near what drew it. */
	private final class HoverNearGoal extends net.minecraft.world.entity.ai.goal.Goal {
		HoverNearGoal() {
			this.setFlags(java.util.EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos at = Critter.this.anchor;
			return at != null && Critter.this.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > 25.0;
		}

		@Override
		public void tick() {
			BlockPos at = Critter.this.anchor;
			if (at != null) {
				Critter.this.getNavigation().moveTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 1.0);
			}
		}
	}
}
