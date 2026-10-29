package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * (0.7.326) A clockwork golem: a copper-and-wood guardian, (0.7.332) made on Powers' infusion altar once its maker knows
 * the Golemancy research (a clock wound with copper, redstone and ichor; {@code data/jaymoskingdoms/alchemy/infusion}),
 * drawn as octopusstudio's Mech Golem (Blockbench Workshop, CC BY 4.0). It keeps the place it was set down as an iron
 * golem keeps a village: it fights the monsters that come near and never turns on its makers. With nothing to fight for
 * a while it winds down and folds itself into its shell, its lights out, hard to hurt but standing still; anything
 * hostile nearby or a blow wakes it, and it unfolds and gets to work. It's mended with copper ingots.
 * <p>
 * (0.7.332) A <i>rogue</i> one ({@link #makeRogue}), a remnant of the Aether like the monoliths, is a dungeon's mini-boss, set down by {@link GolemLairs}: tougher, with
 * a boss bar, it sleeps folded until a player comes near, fights only players, can't be mended, and drops gold and maybe a
 * diamond.
 */
public class ClockworkGolem extends IronGolem {
	public static EntityType<ClockworkGolem> TYPE;
	public static Item ITEM;

	static final double HEALTH = 80.0, SPEED = 0.25, DAMAGE = 12.0, ARMOR = 6.0;
	/** How long it waits with nothing to fight before it folds up (ticks), and how long it takes to unfold. */
	static final int WIND_DOWN = 600, UNFOLD_TICKS = 50;
	/** How much of a blow its shell keeps off while it's folded. */
	static final float SHELL_GUARD = 0.6F;
	/** How much a copper ingot mends. */
	static final float COPPER_MEND = 20.0F;
	public static final byte FOLD = 94, UNFOLD = 95;
	/** A rogue (dungeon) golem: its health, blow and armour. */
	static final double ROGUE_HEALTH = 220.0, ROGUE_DAMAGE = 16.0, ROGUE_ARMOR = 10.0;
	/** What a rogue golem drops besides its copper: gold ingots (min, max) and the chance of a diamond. */
	static final int ROGUE_GOLD_MIN = 3, ROGUE_GOLD_MAX = 6;
	static final float ROGUE_DIAMOND_CHANCE = 0.35F;

	private static final EntityDataAccessor<Boolean> FOLDED = SynchedEntityData.defineId(ClockworkGolem.class, EntityDataSerializers.BOOLEAN);
	/** (0.7.332) Synced so its eyes burn red. */
	private static final EntityDataAccessor<Boolean> ROGUE = SynchedEntityData.defineId(ClockworkGolem.class, EntityDataSerializers.BOOLEAN);

	private int idle;
	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), Component.translatable("entity.jaymoskingdoms.clockwork_golem.rogue"),
		BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
	private int unfolding;
	/** (Client) the tick it last folded or unfolded. */
	private int changedAt = -1000;

	public ClockworkGolem(EntityType<? extends ClockworkGolem> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("clockwork_golem"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<ClockworkGolem>of(ClockworkGolem::new, MobCategory.MISC)
			.sized(1.4F, 2.7F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createGolemAttributes());
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("clockwork_golem"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new Builder(new Item.Properties().setId(itemKey).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ITEM));
	}

	public static AttributeSupplier.Builder createGolemAttributes() {
		return IronGolem.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(FOLDED, false);
		entityData.define(ROGUE, false);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> this.rogue()));
	}

	public boolean rogue() {
		return this.entityData.get(ROGUE);
	}

	/** Turns it into a dungeon's mini-boss: tougher, hostile to players, folded until one comes near. */
	public void makeRogue() {
		this.entityData.set(ROGUE, true);
		this.setPlayerCreated(false);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ROGUE_HEALTH);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ROGUE_DAMAGE);
		this.getAttribute(Attributes.ARMOR).setBaseValue(ROGUE_ARMOR);
		this.setHealth(this.getMaxHealth());
		this.entityData.set(FOLDED, true);
		this.setPersistenceRequired();
	}

	/** A rogue fights players, and only else what hurt it (monsters around it are its dungeon's). */
	@Override
	public boolean canAttack(LivingEntity target) {
		return (!this.rogue() || target instanceof Player || target == this.getLastHurtByMob()) && super.canAttack(target);
	}

	public boolean folded() {
		return this.entityData.get(FOLDED);
	}

	/** (Client) how long since it last folded or unfolded (ticks). */
	public int sinceChanged() {
		return this.tickCount - this.changedAt;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.unfolding > 0) {
			this.unfolding--;
		}
		boolean busy = this.getTarget() != null || this.isVehicle() || this.isLeashed();
		if (busy) {
			this.idle = 0;
			this.fold(level, false);
		} else if (!this.folded() && ++this.idle >= WIND_DOWN) {
			this.fold(level, true);
		}
		if (this.rogue()) {
			this.bar.setVisible(!this.folded());
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		}
	}

	private void fold(ServerLevel level, boolean fold) {
		if (this.folded() == fold) {
			return;
		}
		this.entityData.set(FOLDED, fold);
		this.idle = 0;
		this.getNavigation().stop();
		if (!fold) {
			this.unfolding = UNFOLD_TICKS;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), fold ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL,
			0.8F, 0.6F + this.random.nextFloat() * 0.2F);
		level.broadcastEntityEvent(this, fold ? FOLD : UNFOLD);
	}

	/** Folded or still unfolding, it stands where it is. */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.folded() || this.unfolding > 0;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean wasFolded = this.folded();
		boolean hurt = super.hurtServer(level, source, wasFolded ? amount * (1.0F - SHELL_GUARD) : amount);
		if (wasFolded) {
			this.fold(level, false);
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		return !this.folded() && this.unfolding == 0 && super.doHurtTarget(level, target);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == FOLD || id == UNFOLD) {
			this.changedAt = this.tickCount;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Copper ingots mend it (its own, not an iron golem's iron). */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (this.rogue()) {
			return InteractionResult.PASS;
		}
		if (!stack.is(Items.COPPER_INGOT)) {
			return stack.is(Items.IRON_INGOT) ? InteractionResult.PASS : super.mobInteract(player, hand);
		}
		if (this.getHealth() >= this.getMaxHealth()) {
			return InteractionResult.PASS;
		}
		this.heal(COPPER_MEND);
		stack.consume(1, player);
		this.playSound(SoundEvents.COPPER_PLACE, 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (this.rogue()) {
			this.spawnAtLocation(level, new ItemStack(Items.GOLD_INGOT, ROGUE_GOLD_MIN + this.random.nextInt(ROGUE_GOLD_MAX - ROGUE_GOLD_MIN + 1)));
			if (this.random.nextFloat() < ROGUE_DIAMOND_CHANCE) {
				this.spawnAtLocation(level, new ItemStack(Items.DIAMOND));
			}
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (this.rogue()) {
			this.bar.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.rogue() && super.removeWhenFarAway(distance);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.COPPER_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.COPPER_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.COPPER_STEP, 1.0F, 0.7F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("folded", this.folded());
		output.putBoolean("rogue", this.rogue());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(FOLDED, input.getBooleanOr("folded", false));
		this.entityData.set(ROGUE, input.getBooleanOr("rogue", false));
		this.bar.setVisible(this.rogue() && !this.folded());
	}

	/** The golem, built: set it down on a block and it stands up, its maker's. */
	static final class Builder extends Item {
		Builder(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			if (context.getClickedFace() != Direction.UP && !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
				at = context.getClickedPos().above();
			}
			ClockworkGolem golem = TYPE.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
			if (golem == null) {
				return InteractionResult.FAIL;
			}
			float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() + 180.0F : 0.0F;
			golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			golem.yHeadRot = yaw;
			golem.yBodyRot = yaw;
			if (!level.noCollision(golem)) {
				return InteractionResult.FAIL;
			}
			golem.setPlayerCreated(true);
			golem.setPersistenceRequired();
			level.addFreshEntity(golem);
			level.playSound(null, at, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
			context.getItemInHand().consume(1, context.getPlayer());
			return InteractionResult.SUCCESS;
		}
	}
}
