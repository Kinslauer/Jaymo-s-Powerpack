package com.jaymoskingdoms.creatures;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;
import org.jspecify.annotations.Nullable;

/**
 * The rust beast, a cave animal that lives on iron. Its feelers turn iron to flakes: every blow of it rusts the iron a player
 * wears and carries in hand ({@link #rust}), and iron swung at it rusts too, so it is fought with wood, stone, bone or gold.
 * It smells iron: it follows a player wearing a lot of it, eats iron dropped on the ground, and gnaws iron ore (a block of
 * rust where the ore was).
 * <ul>
 * <li><b>Sympathy</b>: it finds iron as a beast would find food, and when it has eaten its fill ({@link #FULL_AT}) it dozes.</li>
 * <li><b>Feedback</b>: a well-fed beast stops hunting; one that has gnawed an ore vein leaves it picked clean, so a cave
 * with rust beasts has had its iron taken.</li>
 * <li><b>Stewardship</b>: iron ingots, nuggets or raw iron held out to it feed it, and a beast fed by a player is content
 * for {@link #CONTENT_TICKS}: it does not attack them, and noses the ore near them.</li>
 * </ul>
 */
public class RustBeast extends Beast {
	static final double HEALTH = 30.0, SPEED = 0.25, DAMAGE = 3.0;
	/** The share of an iron tool's or piece of armour's whole life a blow of the feelers takes, and an iron blow on the beast. */
	static final float FEELER_RUST = 0.25F, SWUNG_RUST = 0.2F;
	/** Fed, the hunger it loses, and what a full belly is. It sniffs for iron this far (blocks), and looks this often (ticks). */
	static final int FULL_AT = 6;
	static final double SNIFF = 16.0, ORE_SNIFF = 6.0;
	static final int LOOK_EVERY = 20;
	/** Content for this long (ticks) after a player feeds it. */
	static final long CONTENT_TICKS = 24000L * 2;
	/** A full belly digests one in this many ticks. */
	static final int DIGEST_EVERY = 20 * 60;
	/** Looks: hunting, dozing (full). */
	public static final int HUNTING = 0, DOZING = 1;

	private int eaten;
	private @Nullable UUID friend;
	private long contentUntil;

	public RustBeast(EntityType<? extends RustBeast> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.FOLLOW_RANGE, 20.0).add(Attributes.ARMOR, 4.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && this.hungry() && !this.contentWith(target) && ironCarried(target) > 0));
	}

	boolean hungry() {
		return this.eaten < FULL_AT;
	}

	boolean contentWith(LivingEntity who) {
		return this.friend != null && this.friend.equals(who.getUUID()) && this.level().getGameTime() < this.contentUntil;
	}

	/** Iron is anything made of iron: by name, a bucket, shears, or a shield of iron. */
	public static boolean isIron(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem() && !stack.is(Items.BUCKET)) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("iron") || path.contains("steel") || path.contains("chainmail") || stack.is(Items.SHEARS) || stack.is(Items.FLINT_AND_STEEL);
	}

	/** How many pieces of iron {@code who} wears or holds. */
	static int ironCarried(LivingEntity who) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND,
			EquipmentSlot.OFFHAND}) {
			if (isIron(who.getItemBySlot(slot))) {
				n++;
			}
		}
		return n;
	}

	/** Rusts one piece: it loses {@code share} of its whole life, and at the last of it, it is flakes (a broken piece stays, worn out). */
	static void rust(ServerLevel level, LivingEntity at, ItemStack stack, float share) {
		if (!isIron(stack)) {
			return;
		}
		int harm = Math.max(1, Math.round(stack.getMaxDamage() * share));
		stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + harm));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_SAND.defaultBlockState()), at.getX(), at.getY(0.6), at.getZ(), 8, 0.25, 0.4, 0.25, 0.02);
		level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity who) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND,
				EquipmentSlot.OFFHAND}) {
				rust(level, who, who.getItemBySlot(slot), FEELER_RUST);
			}
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// iron swung at it rusts in the hand
		if (source.getEntity() instanceof LivingEntity by && source.getDirectEntity() == by) {
			rust(level, by, by.getMainHandItem(), SWUNG_RUST);
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.IRON_INGOT) || held.is(Items.IRON_NUGGET) || held.is(Items.RAW_IRON)) {
			if (this.level() instanceof ServerLevel level) {
				held.consume(1, player);
				this.fed(level, held.is(Items.IRON_NUGGET) ? 1 : 2);
				this.friend = player.getUUID();
				this.contentUntil = level.getGameTime() + CONTENT_TICKS;
				level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY(0.9), this.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
				if (this.getTarget() == player) {
					this.setTarget(null);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	private void fed(ServerLevel level, int by) {
		this.eaten = Math.min(FULL_AT + 2, this.eaten + by);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.setLook(this.hungry() ? HUNTING : DOZING);
		if (this.tickCount % DIGEST_EVERY == 0 && this.eaten > 0) {
			this.eaten--;
		}
		if (this.tickCount % LOOK_EVERY != 0 || !this.hungry() && !this.contentActive(level)) {
			return;
		}
		// eats iron lying about
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.5), i -> isRawIron(i.getItem()))) {
			item.getItem().shrink(1);
			if (item.getItem().isEmpty()) {
				item.discard();
			}
			this.fed(level, 1);
			return;
		}
		if (this.getTarget() == null && this.hungry()) {
			// smells the ore: gnaws a nearby vein, and goes toward iron lying on the ground
			BlockPos ore = this.nearOre(level);
			if (ore != null) {
				this.getNavigation().moveTo(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5, 1.0);
				if (ore.distSqr(this.blockPosition()) <= 6.0) {
					BlockState stone = level.getBlockState(ore).is(Blocks.DEEPSLATE_IRON_ORE) ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
					level.setBlock(ore, stone, 3);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_SAND.defaultBlockState()), ore.getX() + 0.5, ore.getY() + 0.5, ore.getZ() + 0.5,
						14, 0.3, 0.3, 0.3, 0.02);
					this.fed(level, 2);
				}
			}
		}
	}

	private boolean contentActive(ServerLevel level) {
		return level.getGameTime() < this.contentUntil;
	}

	private static boolean isRawIron(ItemStack s) {
		return s.is(Items.IRON_INGOT) || s.is(Items.IRON_NUGGET) || s.is(Items.RAW_IRON) || s.is(Items.IRON_BLOCK);
	}

	private @Nullable BlockPos nearOre(ServerLevel level) {
		BlockPos at = this.blockPosition();
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-(int) ORE_SNIFF, -2, -(int) ORE_SNIFF), at.offset((int) ORE_SNIFF, 2, (int) ORE_SNIFF))) {
			if (level.getBlockState(p).is(BlockTags.IRON_ORES) && (best == null || p.distSqr(at) < best.distSqr(at))) {
				best = p.immutable();
			}
		}
		return best;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ARMADILLO_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ARMADILLO_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Eaten", this.eaten);
		output.putLong("ContentUntil", this.contentUntil);
		if (this.friend != null) {
			output.putString("Friend", this.friend.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.eaten = input.getIntOr("Eaten", 0);
		this.contentUntil = input.getLongOr("ContentUntil", 0L);
		this.friend = input.getString("Friend").map(UUID::fromString).orElse(null);
	}
}
