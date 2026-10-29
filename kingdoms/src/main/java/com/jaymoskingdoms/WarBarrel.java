package com.jaymoskingdoms;

import com.jaymoskingdoms.beasts.ActingMonster;
import com.jaymoskingdoms.beasts.Beasts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.334) The goblins' war barrel, drawn as polyjo's The Cogwork Golem (2025 Version) (Blockbench Workshop, CC BY
 * 4.0): a walking barrel of planks and iron hoops with a lantern on a pole, the goblin villages' war machine. Each
 * village keeps one by its gate ({@link #hire}, when it rises and, if it has lost it, as it grows). It stands still
 * until a foe of the village comes near, wakes with a clank ("activate"), and lumbers after them, ramming with its
 * iron-banded bulk. Slow, heavy, hard to hurt, and never turns on a goblin. With its village gone it stops and stays.
 * <p>
 * (0.7.334) A <i>friendly</i> one is built on Powers' infusion altar once its maker knows the Golemancy research, like the
 * clockwork golem ({@code data/jaymoskingdoms/alchemy/infusion/war_barrel.json}). Set down, it keeps that place for its
 * maker ({@link #owner}): it wakes for monsters that come near and rams them, never turns on its maker or another
 * friendly barrel, doesn't keep anyone from sleeping, and is mended with iron ingots.
 */
public class WarBarrel extends ActingMonster {
	static final double HEALTH = 120.0, ARMOR = 12.0, DAMAGE = 12.0, SPEED = 0.18;
	/** A foe this near (blocks) wakes it. */
	static final double WAKE_RANGE = 14.0;
	/** Waking (ticks); its ram: how often, how far it reaches, how hard it throws. */
	static final int WAKE_TICKS = 40, RAM_GAP = 30;
	static final double RAM_REACH = 2.2, RAM_KNOCK = 1.6;
	/** How far from its gate it goes, how far off it's counted, and back to sleep after this long with no foe (ticks). */
	static final int HOME_RADIUS = 18, REST_TICKS = 300;
	static final double COUNT_RANGE = 64.0;
	/** The chance a village without one builds one, each day it grows. */
	static final float BUILD_CHANCE = 0.25F;
	/** How much an iron ingot mends a friendly one. */
	static final float IRON_MEND = 15.0F;

	public static Item ITEM;

	private static final String[] ACTIONS = {"activate"};

	/** The goblin village it serves; -1 for none. */
	private int camp = -1;
	/** (0.7.334) Who built it, for a friendly one; null for a goblin village's. */
	private @Nullable UUID owner;
	private boolean awake;
	private int ramGap, quiet;

	public WarBarrel(EntityType<? extends WarBarrel> type, Level level) {
		super(type, level);
		this.xpReward = 25;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 24.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.prey(level, target)));
	}

	/** (0.7.334) Built by a player through Golemancy. */
	public boolean friendly() {
		return this.owner != null;
	}

	private boolean owner(LivingEntity e) {
		return this.owner != null && this.owner.equals(e.getUUID());
	}

	/** On the same side: for a friendly one its maker and the other friendly barrels; for a village's, goblins and its barrels. */
	private boolean ally(LivingEntity e) {
		if (this.friendly()) {
			return this.owner(e) || e instanceof WarBarrel b && b.friendly();
		}
		return Goblins.isGoblin(e) || e instanceof WarBarrel b && !b.friendly();
	}

	/** What it goes for: a friendly one, monsters (creepers left be, as iron golems do); a village's, the goblins' prey
	 * ({@link Goblins#prey}); with no village, nothing it isn't hit by. */
	private boolean prey(ServerLevel level, LivingEntity target) {
		if (this.ally(target)) {
			return false;
		}
		if (this.friendly()) {
			return target instanceof Enemy && !(target instanceof Creeper);
		}
		return this.camp >= 0 && Goblins.prey(this.camp, target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && this.ally(target) ? null : target);
	}

	@Override
	protected boolean strikes(LivingEntity e) {
		return !this.ally(e) && (e == this.getTarget() || e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this
			|| this.level() instanceof ServerLevel level && this.prey(level, e));
	}

	/** A friendly one is mended with iron ingots. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.friendly() || !stack.is(Items.IRON_INGOT) || this.getHealth() >= this.getMaxHealth()) {
			return super.mobInteract(player, hand);
		}
		this.heal(IRON_MEND);
		stack.consume(1, player);
		this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 0.8F);
		return InteractionResult.SUCCESS;
	}

	/** A friendly one stays, even on peaceful. */
	@Override
	public void checkDespawn() {
		if (!this.friendly()) {
			super.checkDespawn();
		}
	}

	@Override
	public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
		return !this.friendly() && super.isPreventingPlayerRest(level, player);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.camp >= 0 && this.tickCount % 100 == 0) {
			Camps.CampData data = Camps.data(level.getServer(), this.camp);
			if (Factions.get(level.getServer(), this.camp) == null || data == null || data.clearedAt() >= 0) {
				this.camp = -1;
				this.clearHome();
			}
		}
		LivingEntity target = this.getTarget();
		if (!this.awake) {
			this.getNavigation().stop();
			if (target != null && target.isAlive() && (this.distanceTo(target) < WAKE_RANGE || this.getLastHurtByMob() == target)) {
				this.awake = true;
				this.act("activate", WAKE_TICKS);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.5F, 0.6F);
			}
			return;
		}
		if (this.acting()) {
			this.getNavigation().stop();
			return;
		}
		if (target == null || !target.isAlive()) {
			if (++this.quiet > REST_TICKS) {
				this.awake = false;
				this.quiet = 0;
			}
			return;
		}
		this.quiet = 0;
		this.getLookControl().setLookAt(target, 20.0F, 20.0F);
		this.getNavigation().moveTo(target, 1.0);
		if (--this.ramGap <= 0 && this.distanceTo(target) < RAM_REACH + 1.0) {
			this.ramGap = RAM_GAP;
			if (this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), RAM_REACH, true, RAM_KNOCK, 0.3) > 0) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.5F, 0.6F);
				level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 2.0, this.getZ(), 10, 0.5, 0.5, 0.5, 0.02);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		// (its blows are its rams)
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("camp", this.camp);
		output.putBoolean("awake", this.awake);
		output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.camp = input.getIntOr("camp", -1);
		this.awake = input.getBooleanOr("awake", false);
		this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.WOOD_STEP, 1.0F, 0.5F);
	}

	// ---- building ------------------------------------------------------------------------------------------------------

	/** Goblin village {@code camp} sets its war barrel by its gate, unless it has one about it already. */
	static void hire(ServerLevel level, int camp) {
		Camps.CampData data = Camps.data(level.getServer(), camp);
		if (Beasts.WAR_BARREL == null || data == null || !level.isLoaded(data.center())) {
			return;
		}
		if (!level.getEntitiesOfClass(WarBarrel.class, new AABB(data.center()).inflate(COUNT_RANGE), b -> b.camp == camp).isEmpty()) {
			return;
		}
		WarBarrel barrel = Beasts.WAR_BARREL.create(level, EntitySpawnReason.EVENT);
		if (barrel == null) {
			return;
		}
		Direction gate = data.gate();
		// (inside the ring, before its gate)
		BlockPos column = new CampBuildings.Placer(data.center(), gate, 0).at(0, 0, -(GoblinBuildings.RING_RADIUS - 4));
		BlockPos at = GoblinVillages.spot(level, column, level.getRandom());
		barrel.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, gate.toYRot(), 0.0F);
		barrel.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		barrel.camp = camp;
		barrel.setHomeTo(at, HOME_RADIUS);
		barrel.setPersistenceRequired();
		level.addFreshEntity(barrel);
	}

	/** A day's growth: a village that has lost its barrel now and then builds another. */
	static void grow(ServerLevel level, int camp, int days) {
		MinecraftServer server = level.getServer();
		Camps.CampData data = Camps.data(server, camp);
		if (Factions.get(server, camp) != null && data != null && data.clearedAt() < 0 && level.getRandom().nextFloat() < 1.0F - Math.pow(1.0F - BUILD_CHANCE, days)) {
			hire(level, camp);
		}
	}

	// ---- golemancy ----------------------------------------------------------------------------------------------------

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("war_barrel"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Builder(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ITEM));
	}

	/** The barrel, built: set it down on a block and it keeps that place, its maker's. */
	static final class Builder extends Item {
		Builder(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			if (Beasts.WAR_BARREL == null) {
				return InteractionResult.FAIL;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			if (context.getClickedFace() != Direction.UP && !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
				at = context.getClickedPos().above();
			}
			WarBarrel barrel = Beasts.WAR_BARREL.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
			if (barrel == null) {
				return InteractionResult.FAIL;
			}
			float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() + 180.0F : 0.0F;
			barrel.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			barrel.yHeadRot = yaw;
			barrel.yBodyRot = yaw;
			if (!level.noCollision(barrel)) {
				return InteractionResult.FAIL;
			}
			if (context.getPlayer() != null) {
				barrel.owner = context.getPlayer().getUUID();
			}
			barrel.setHomeTo(at, HOME_RADIUS);
			barrel.setPersistenceRequired();
			level.addFreshEntity(barrel);
			level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
			context.getItemInHand().consume(1, context.getPlayer());
			return InteractionResult.SUCCESS;
		}
	}
}
