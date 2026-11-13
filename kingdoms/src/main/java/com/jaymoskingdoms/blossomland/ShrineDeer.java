package com.jaymoskingdoms.blossomland;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The small spotted deer of the blossom shrines.
 * <ul>
 * <li><b>What it does.</b> In the open it is shy and keeps its distance from people; inside a blossom settlement,
 * where nobody has hunted it in living memory, it is calm, comes to food held out and bows for it. It browses: a
 * hungry deer eats the saplings it finds ({@link #BROWSE_EVERY}).</li>
 * <li><b>What comes back.</b> Where deer are many, young trees do not get away, so the groves round a shrine stay
 * open and a forest does not close over the village. A sapling you want kept needs a fence.</li>
 * <li><b>Looked after.</b> A deer fed from the hand ({@link #FED_TICKS}) leaves the saplings alone for a day and
 * is calm with people wherever it is.</li>
 * </ul>
 */
public class ShrineDeer extends BlossomAnimal {
	static final double HEALTH = 10.0, SPEED = 0.25;
	static final long FED_TICKS = 24000L;
	static final int BROWSE_EVERY = 2400, BOW_TICKS = 30, CALM_EVERY = 100;
	static final Set<Item> FOOD = Set.of(Items.WHEAT, Items.CARROT, Items.BREAD, Items.APPLE, Items.BEETROOT);

	private int browseIn;
	private boolean hallowed;

	public ShrineDeer(EntityType<? extends ShrineDeer> type, Level level) {
		super(type, level);
		this.browseIn = this.random.nextInt(BROWSE_EVERY);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	static boolean food(ItemStack stack) {
		return FOOD.contains(stack.getItem());
	}

	/** Calm with people: in a blossom settlement's land, or fed. */
	boolean calm() {
		return this.hallowed || this.fed();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, this.fleePredators(12.0F, 1.5));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.8));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 9.0F, 1.3, 1.7, e -> !this.calm()));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, ShrineDeer::food, false) {
			@Override
			public boolean canUse() {
				return ShrineDeer.this.calm() && super.canUse();
			}
		});
		this.goalSelector.addGoal(4, new SeekBlock(8, 2, 100, 1.0, 1.6) {
			@Override
			protected boolean wants() {
				return ShrineDeer.this.browseIn == 0 && !ShrineDeer.this.fed();
			}

			@Override
			protected boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
				return state.is(BlockTags.SAPLINGS);
			}

			@Override
			protected void arrive(ServerLevel level, BlockPos pos) {
				level.destroyBlock(pos, false, ShrineDeer.this);
				ShrineDeer.this.headDown(BOW_TICKS);
				ShrineDeer.this.browseIn = BROWSE_EVERY;
			}
		});
		this.goalSelector.addGoal(5, new KeepTogether(12.0, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.browseIn > 0) {
			this.browseIn--;
		}
		if ((this.tickCount + this.getId()) % CALM_EVERY == 0) {
			this.hallowed = this.amongBlossomFolk();
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.calm() && food(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		InteractionResult fed = this.handFeed(player, hand, ShrineDeer::food, FED_TICKS);
		if (fed == InteractionResult.PASS) {
			return super.mobInteract(player, hand);
		}
		// it bows for what it is given
		this.getLookControl().setLookAt(player);
		this.headDown(BOW_TICKS);
		return fed;
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
		return 1.4F;
	}
}
