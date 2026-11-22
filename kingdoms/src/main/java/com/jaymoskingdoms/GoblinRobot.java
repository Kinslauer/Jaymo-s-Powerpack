package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The goblins' <b>engineer's hammer</b> and the clanking <b>robot</b> it calls up (survival pick "goblin extras", Andy,
 * 2026-11-22; Lore.md, "Goblin odd-jobs"): stolen Frostborn work ({@link FrostTech}), beaten back together by goblin hands,
 * so it never runs quite right. The robot is a clockwork golem ({@link ClockworkGolem}, drawn the same way, patched and
 * green) that fights for whoever struck the hammer: the summoner's foe is its foe, and it comes along behind them.
 * But it misfires ({@link #MISFIRE_PERCENT} of its blows do nothing), stalls with a shower of sparks now and then, wanders
 * off on its own and falls apart after {@link #LIFE} ticks. A village's goblin engineer ({@link #appoint}: one goblin
 * with a hammer in his hand) strikes it in a fight, once in {@link #ENGINEER_WAIT}. A hammer is dropped by an engineer
 * sometimes, and sold by the wandering goblin ({@link Pedlars}).
 */
public final class GoblinRobot extends ClockworkGolem {
	public static EntityType<GoblinRobot> ROBOT;
	public static Item HAMMER;

	/** The robot: health, blow, pace, armour (a clockwork golem's are 80, 12, 0.25 and 6). */
	static final double ROBOT_HEALTH = 45.0, ROBOT_DAMAGE = 6.0, ROBOT_SPEED = 0.26, ROBOT_ARMOR = 2.0;
	/** How long it holds together (ticks) before it falls apart. */
	static final int LIFE = 1800;
	/** Each tick, one in this likely it stalls (for STALL_MIN to STALL_MIN + STALL_MORE ticks, in sparks); one in this it wanders off (for STRAY ticks). */
	static final int STALL_ONE_IN = 150, STALL_MIN = 30, STALL_MORE = 30, WANDER_ONE_IN = 500, STRAY = 100;
	/** The share of its blows that do nothing (percent). */
	static final int MISFIRE_PERCENT = 20;
	/** How far behind its summoner it lets itself get (blocks), and how many at most a summoner has out at once. */
	static final double LEASH = 8.0;
	static final int MOST_OUT = 2;
	/** The hammer: uses, and the wait between strikes (ticks). */
	static final int HAMMER_USES = 6, HAMMER_WAIT = 600;
	/** A goblin engineer: how long between his robots (ticks), how near a foe must be (blocks), how likely a village has one (checked each {@link Camps#CHECK_TICKS}). */
	static final int ENGINEER_WAIT = 1200;
	static final double ENGINEER_RANGE = 16.0;
	static final float APPOINT_CHANCE = 0.25F;
	/** The share of the time an engineer's hammer drops when he dies. */
	static final float HAMMER_DROP = 0.15F;
	static final int SAY_COLOUR = 0x8FB35A;
	static final String ENGINEER_TAG = "jaymoskingdoms.goblin_engineer";

	/** Engineer → the game time he may strike the hammer again. */
	private static final Map<UUID, Long> ENGINEER_READY = new HashMap<>();

	private @Nullable UUID owner;
	private int lived, stall, stray;

	public GoblinRobot(EntityType<? extends GoblinRobot> type, Level level) {
		super(type, level);
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("goblin_robot"));
		ROBOT = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<GoblinRobot>of(GoblinRobot::new, MobCategory.MISC)
			.noLootTable().sized(1.4F, 2.7F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(ROBOT, ClockworkGolem.createGolemAttributes().add(Attributes.MAX_HEALTH, ROBOT_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, ROBOT_DAMAGE).add(Attributes.MOVEMENT_SPEED, ROBOT_SPEED).add(Attributes.ARMOR, ROBOT_ARMOR));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("engineers_hammer"));
		HAMMER = Registry.register(BuiltInRegistries.ITEM, itemKey, new Hammer(new Item.Properties().setId(itemKey).stacksTo(1).durability(HAMMER_USES)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(HAMMER));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ENGINEER_READY.clear());
	}

	// ---- the robot --------------------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new Stray(this));
		this.goalSelector.addGoal(2, new Follow(this));
	}

	/** The robot, set down by {@code summoner} (a player's own, or a goblin engineer's). */
	static @Nullable GoblinRobot summon(ServerLevel level, Vec3 at, LivingEntity summoner) {
		GoblinRobot robot = ROBOT.create(level, EntitySpawnReason.TRIGGERED);
		if (robot == null) {
			return null;
		}
		robot.snapTo(at.x, at.y, at.z, summoner.getYRot(), 0.0F);
		robot.owner = summoner.getUUID();
		robot.setPlayerCreated(summoner instanceof Player);
		if (!level.noCollision(robot)) {
			return null;
		}
		level.addFreshEntity(robot);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 1.0F, 0.6F);
		sparks(level, robot, 20);
		return robot;
	}

	private @Nullable LivingEntity summoner(ServerLevel level) {
		return this.owner != null && level.getEntity(this.owner) instanceof LivingEntity l && l.isAlive() ? l : null;
	}

	private static void sparks(ServerLevel level, Entity at, int count) {
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX(), at.getY(0.6), at.getZ(), count, 0.4, 0.6, 0.4, 0.3);
	}

	/** It strikes only what its summoner's foes are: never them, never a goblin of the one who made it. */
	@Override
	public boolean canAttack(LivingEntity target) {
		return !target.getUUID().equals(this.owner) && !(target instanceof GoblinRobot) && super.canAttack(target);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (++this.lived >= LIFE) {
			this.fallApart(level);
			return;
		}
		if (this.stall > 0) {
			if (--this.stall % 5 == 0) {
				sparks(level, this, 4);
			}
		} else if (this.random.nextInt(STALL_ONE_IN) == 0) {
			// (it stalls, in a shower of sparks)
			this.stall = STALL_MIN + this.random.nextInt(STALL_MORE);
			this.getNavigation().stop();
			sparks(level, this, 14);
			this.playSound(SoundEvents.COPPER_HIT, 1.0F, 0.5F);
		}
		if (this.stray > 0) {
			this.stray--;
		} else if (this.getTarget() == null && this.random.nextInt(WANDER_ONE_IN) == 0) {
			// (it wanders off on its own)
			this.stray = STRAY;
		}
		if (this.tickCount % 10 == 0 && this.stray == 0) {
			LivingEntity summoner = this.summoner(level);
			LivingEntity foe = summoner instanceof Mob m ? Rulers.target(m) : summoner == null ? null : summoner.getLastHurtMob();
			if (foe != null && foe.isAlive() && this.getTarget() != foe && this.canAttack(foe) && foe.distanceToSqr(this) < 24.0 * 24.0) {
				this.setTarget(foe);
			}
		}
	}

	/** Stalled, it stands where it is. */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || this.stall > 0;
	}

	/** Some of its blows misfire: the swing, the sparks, nothing struck. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (this.random.nextInt(100) < MISFIRE_PERCENT) {
			level.broadcastEntityEvent(this, (byte) 4);
			sparks(level, this, 8);
			this.playSound(SoundEvents.COPPER_BULB_TURN_OFF, 1.0F, 0.6F);
			return false;
		}
		return super.doHurtTarget(level, target);
	}

	private void fallApart(ServerLevel level) {
		sparks(level, this, 30);
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 12, 0.5, 0.8, 0.5, 0.02);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1.5F, 0.6F);
		this.scrap(level);
		this.discard();
	}

	private void scrap(ServerLevel level) {
		this.spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 1 + this.random.nextInt(3)));
		if (this.random.nextBoolean()) {
			this.spawnAtLocation(level, new ItemStack(Items.COPPER_INGOT));
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.scrap(level);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.owner != null) {
			output.putString("summoner", this.owner.toString());
		}
		output.putInt("lived", this.lived);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.owner = input.getString("summoner").map(UUID::fromString).orElse(null);
		this.lived = input.getIntOr("lived", 0);
	}

	/** Off on its own: a stretch of random walking, in no one's service. */
	private static final class Stray extends Goal {
		private final GoblinRobot robot;

		Stray(GoblinRobot robot) {
			this.robot = robot;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.robot.stray > 0 && this.robot.stall == 0;
		}

		@Override
		public void tick() {
			if (this.robot.getNavigation().isDone()) {
				Vec3 to = LandRandomPos.getPos(this.robot, 8, 3);
				if (to != null) {
					this.robot.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
				}
			}
		}
	}

	/** Behind its summoner, when they've left it more than {@link #LEASH} blocks behind. */
	private static final class Follow extends Goal {
		private final GoblinRobot robot;
		private @Nullable LivingEntity summoner;

		Follow(GoblinRobot robot) {
			this.robot = robot;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (this.robot.getTarget() != null || this.robot.stray > 0 || this.robot.stall > 0 || !(this.robot.level() instanceof ServerLevel level)) {
				return false;
			}
			this.summoner = this.robot.summoner(level);
			return this.summoner != null && this.robot.distanceToSqr(this.summoner) > LEASH * LEASH;
		}

		@Override
		public boolean canContinueToUse() {
			return this.summoner != null && this.summoner.isAlive() && this.robot.getTarget() == null && this.robot.stray == 0
				&& this.robot.distanceToSqr(this.summoner) > 3.0 * 3.0;
		}

		@Override
		public void tick() {
			if (this.summoner != null && this.robot.tickCount % 10 == 0) {
				this.robot.getNavigation().moveTo(this.summoner, 1.1);
			}
		}
	}

	// ---- the hammer -------------------------------------------------------------------------------------------------

	/** Strike it (use) and a robot stands up in front of you, yours for a while; the hammer wears. */
	static final class Hammer extends Item {
		Hammer(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (out(server, player) >= MOST_OUT) {
				return InteractionResult.FAIL;
			}
			Vec3 look = player.getLookAngle();
			GoblinRobot robot = summon(server, player.position().add(look.x * 2.5, 0.0, look.z * 2.5), player);
			if (robot == null) {
				return InteractionResult.FAIL;
			}
			held.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			player.getCooldowns().addCooldown(held, HAMMER_WAIT);
			return InteractionResult.SUCCESS;
		}
	}

	/** How many robots {@code summoner} has out near them. */
	private static int out(ServerLevel level, LivingEntity summoner) {
		return level.getEntitiesOfClass(GoblinRobot.class, summoner.getBoundingBox().inflate(64.0), r -> summoner.getUUID().equals(r.owner)).size();
	}

	// ---- the goblin engineer ----------------------------------------------------------------------------------------

	/**
	 * (From {@link DrunkGoblins#tick}, each time a village is looked over) a village with no engineer may make one of its
	 * common goblins: a hammer in his hand, dropped now and then when he dies.
	 */
	static void appoint(ServerLevel level, List<PathfinderMob> goblins) {
		if (goblins.isEmpty() || goblins.stream().anyMatch(g -> g.entityTags().contains(ENGINEER_TAG)) || level.getRandom().nextFloat() >= APPOINT_CHANCE) {
			return;
		}
		PathfinderMob goblin = goblins.get(level.getRandom().nextInt(goblins.size()));
		goblin.addTag(ENGINEER_TAG);
		arm(goblin);
	}

	private static void arm(PathfinderMob goblin) {
		goblin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(HAMMER));
		goblin.setDropChance(EquipmentSlot.MAINHAND, HAMMER_DROP);
	}

	/**
	 * (From {@code Greenskins.step}) one of a village's goblins: if he's its engineer and has a foe near, he strikes the hammer
	 * and a robot stands up beside him, and sets on his foe.
	 */
	static void engineer(ServerLevel level, PathfinderMob mob) {
		if (mob.tickCount % 20 != 0 || !mob.entityTags().contains(ENGINEER_TAG) || !mob.isAlive()) {
			return;
		}
		if (!mob.getMainHandItem().is(HAMMER)) {
			arm(mob);
		}
		LivingEntity foe = Rulers.target(mob);
		long now = level.getGameTime();
		if (foe == null || mob.distanceToSqr(foe) > ENGINEER_RANGE * ENGINEER_RANGE || now < ENGINEER_READY.getOrDefault(mob.getUUID(), 0L)
			|| out(level, mob) > 0) {
			return;
		}
		ENGINEER_READY.put(mob.getUUID(), now + ENGINEER_WAIT);
		Vec3 beside = mob.position().add(foe.position().subtract(mob.position()).normalize().scale(2.0));
		GoblinRobot robot = summon(level, beside, mob);
		if (robot != null) {
			mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			robot.setTarget(foe);
			Speech.say(level, mob, Dialects.tr(mob, "talk.jaymoskingdoms.engineer.strike"), SAY_COLOUR, 16.0);
		}
	}
}
