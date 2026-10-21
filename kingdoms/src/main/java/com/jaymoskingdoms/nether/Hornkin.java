package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.Heritage;
import java.util.EnumSet;
import java.util.List;
import com.jaymoskingdoms.BossBars;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
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
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Kingdoms 0.7.131) The Hornkin ({@link Nether}): bull-headed folk of the Nether's wastes and crimson forests, our own
 * take on Just-In NETHER's bullkin (that mod is All Rights Reserved: nothing of it is used, only the idea). They love
 * gold more than piglins do and the two have warred over it for as long as either remembers: they fight on sight, and
 * a dropped gold pile draws both. A Hornkin snatches gold off the ground, takes it out of chests, and goes for a player
 * wearing gold from twice as far off. Gold in hand, it stops to admire it; an ingot it trades for a few of its own
 * wares ({@link #WARES}) and keeps the rest of its hoard until it dies. Emberborn players are left alone unless they
 * strike first. Out of the Nether a Hornkin turns, as piglins do, into a {@link Kind#ZOMBIFIED} one.
 *
 * <p>Four kinds: the {@link Kind#WARRIOR} (an axe, and a horn charge that throws its foe), the {@link Kind#HURLER}
 * (lobs {@link MagmaBomb}s), the {@link Kind#BRUTE} (huge, slow, stamps the ground) and the zombified one.
 */
public class Hornkin extends Monster implements RangedAttackMob {
	public enum Kind {
		WARRIOR, HURLER, BRUTE, ZOMBIFIED, WARLORD
	}

	/** Health, blow, pace, how hard it is to shove, for each kind (in {@link Kind} order). */
	static final double[] HEALTH = {30.0, 22.0, 70.0, 26.0, 260.0}, DAMAGE = {6.0, 3.0, 11.0, 5.0, 13.0}, SPEED = {0.30, 0.28, 0.24, 0.23, 0.27},
		KNOCKBACK_RESIST = {0.3, 0.1, 0.9, 0.2, 1.0}, ARMOR = {2.0, 2.0, 6.0, 2.0, 10.0};
	/** (0.7.142) The warlord's rally: below this share of his health, at most this often (ticks), how far it reaches, how many warriors it calls. */
	static final float RALLY_BELOW = 0.5F;
	static final int RALLY_EVERY = 20 * 40, RALLY_TICKS = 20 * 15, RALLY_CALLS = 2;
	static final double RALLY_RANGE = 24.0;
	/** A shield caught by the warlord's horns is useless this long (ticks). */
	static final int SHIELD_BREAK_TICKS = 20 * 5;
	/** (0.7.142) A routed one (its fort's warlord dead) runs from players, and is gone after this long out of their reach (ticks, blocks). */
	static final int ROUT_TICKS = 20 * 60;
	static final double ROUT_GONE_RANGE = 24.0;
	/** A marching host takes strides of this many blocks towards its goal; it has arrived within this many. */
	static final double MARCH_STRIDE = 16.0, MARCH_ARRIVED = 10.0;
	/** How far it spots prey; a player wearing gold, from this much farther. */
	static final double SIGHT = 16.0, GOLD_SIGHT = 32.0;
	/** Its horn charge (warriors): from how far, how long it runs at most (ticks), how often (ticks), the gore's extra damage and throw. */
	static final double CHARGE_FROM = 5.0, CHARGE_TO = 12.0, CHARGE_SPEED = 0.55, GORE_REACH = 1.8, GORE_THROW = 1.4;
	static final int CHARGE_TICKS = 20, CHARGE_EVERY = 20 * 6;
	static final float GORE_BONUS = 1.5F;
	/** The brute's ground stamp: how near its foe must be, its reach, damage, throw, how often (ticks). */
	static final double STAMP_TRIGGER = 3.5, STAMP_RADIUS = 4.0, STAMP_THROW = 1.2;
	static final float STAMP_DAMAGE = 7.0F;
	static final int STAMP_EVERY = 20 * 7;
	/** A hurler's bombs: how often (ticks) and from how far (blocks). */
	static final int THROW_EVERY = 45;
	static final float THROW_RANGE = 14.0F;
	/** How long it stands admiring gold in its hand (ticks). */
	static final int ADMIRE_TICKS = 20 * 6;
	/** It looks for gold on the ground this far off, and in chests this near (blocks across, and up and down). */
	static final double GOLD_SEEK = 12.0;
	static final int CHEST_REACH = 6, CHEST_HEIGHT = 2, CHEST_EVERY = 20 * 5;
	/** How long it lasts out of the Nether before it turns (ticks, as a piglin). */
	static final int TURN_TICKS = AbstractPiglin.CONVERSION_TIME;
	/** Piglins near it turn on it, within this far (blocks), checked this often (ticks). */
	static final double FEUD_RANGE = 16.0;
	static final int FEUD_EVERY = 20;
	/** An ingot buys this many of its wares, at most. */
	static final int WARES_MOST = 3;
	/** (0.7.146) The truce: an Emberborn who hands it gold gets this many more wares, and a fair chance of a rarer one (a gold block: RARE_FOR_BLOCK of them). */
	static final int TRUCE_EXTRA = 2, RARE_FOR_BLOCK = 3;
	static final float TRUCE_RARE_CHANCE = 0.25F;
	/** (0.7.146) A raider goes home once it has this much gold; in a town it roams this far from the seat, and finds chests this far off. */
	static final int RAID_LOOT = 3, RAID_RADIUS = 28, RAID_CHEST_REACH = 10;
	/** (0.7.146) Home from a raid: in the portal at this distance; or gone anyway if it can't get there in this long. */
	static final double PORTAL_REACHED = 2.0;
	static final int HOMEWARD_MOST = 20 * 90;
	/** What a Hornkin trades for a gold ingot: its own wares, not a piglin's. */
	/** Built when traded, not at class load: an ItemStack can't be made before item components are bound
	 * (0.7.133 crashed the game at startup). */
	static final List<java.util.function.Supplier<ItemStack>> WARES = List.of(() -> new ItemStack(Items.QUARTZ, 4), () -> new ItemStack(Items.MAGMA_CREAM, 2), () -> new ItemStack(Items.FIRE_CHARGE, 2),
		() -> new ItemStack(Items.BLAZE_POWDER), () -> new ItemStack(Items.LEATHER, 3), () -> new ItemStack(Items.BONE, 3), () -> new ItemStack(Items.BLACKSTONE, 8),
		() -> new ItemStack(Items.GILDED_BLACKSTONE), () -> new ItemStack(Items.CRIMSON_FUNGUS, 2), () -> new ItemStack(Items.COOKED_PORKCHOP, 2),
		() -> new ItemStack(Items.GLOWSTONE_DUST, 4), () -> new ItemStack(Items.ARROW, 6));
	/** (0.7.146) What it keeps for an Emberborn it trades with in good faith. */
	static final List<java.util.function.Supplier<ItemStack>> TRUCE_WARES = List.of(() -> new ItemStack(Items.BLAZE_ROD, 2),
		() -> new ItemStack(Items.GILDED_BLACKSTONE, 2), () -> new ItemStack(Items.CRYING_OBSIDIAN, 2), () -> new ItemStack(Items.MAGMA_BLOCK, 4),
		() -> new ItemStack(Items.GHAST_TEAR), () -> new ItemStack(Items.NETHERITE_SCRAP));

	private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(Hornkin.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ADMIRING = SynchedEntityData.defineId(Hornkin.class, EntityDataSerializers.BOOLEAN);

	private final Kind kind;
	private int chargeTicks, chargeReady, stampReady, admireTicks, timeOut, rallyReady, routed;
	/** (0.7.142) Where its war host marches (a rival fort), if it's on the march. */
	private @Nullable BlockPos marchTo;
	/** (0.7.142) The warlord's boss bar. */
	private final @Nullable ServerBossEvent bar;
	/** The gold it keeps (dropped when it dies). */
	private int hoard;
	/** (0.7.146) Who handed it the gold it's admiring (the truce), if anyone. */
	private java.util.@Nullable UUID trader;
	/** (0.7.146) On a portal raid: its party, the spot by the portal it came out at (and goes home through), its time left, and whether it's going home. */
	private int raid, raidLeft;
	private @Nullable BlockPos portal;
	private boolean homeward;

	public Hornkin(EntityType<? extends Hornkin> type, Level level, Kind kind) {
		super(type, level);
		this.kind = kind;
		this.xpReward = kind == Kind.WARLORD ? 80 : kind == Kind.BRUTE ? 20 : 8;
		this.bar = kind == Kind.WARLORD ? new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.NOTCHED_10) : null;
		this.setCanPickUpLoot(kind != Kind.ZOMBIFIED);
	}

	public static AttributeSupplier.Builder createAttributes(Kind kind) {
		int i = kind.ordinal();
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH[i])
			.add(Attributes.ATTACK_DAMAGE, DAMAGE[i])
			.add(Attributes.MOVEMENT_SPEED, SPEED[i])
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESIST[i])
			.add(Attributes.FOLLOW_RANGE, GOLD_SIGHT)
			.add(Attributes.ARMOR, ARMOR[i]);
	}

	public Kind kind() {
		return this.kind;
	}

	public boolean charging() {
		return this.entityData.get(CHARGING);
	}

	public boolean admiring() {
		return this.entityData.get(ADMIRING);
	}

	boolean zombified() {
		return this.kind == Kind.ZOMBIFIED;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(CHARGING, false);
		builder.define(ADMIRING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(0, new AvoidEntityGoal<>(this, Player.class, 20.0F, 1.0, 1.3, e -> this.routed() && e.isAlive() && !e.isSpectator()));
		this.goalSelector.addGoal(1, new AdmireGoal());
		if (this.kind == Kind.HURLER) {
			this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, THROW_EVERY, THROW_RANGE));
		} else {
			this.goalSelector.addGoal(2, new MeleeAttackGoal(this, this.zombified() ? 1.0 : 1.15, false));
		}
		if (!this.zombified()) {
			this.goalSelector.addGoal(3, new SeekGoldGoal());
			this.goalSelector.addGoal(4, new RaidChestGoal());
		}
		this.goalSelector.addGoal(5, new MarchGoal());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> this.hunts(target)));
		if (this.zombified()) {
			this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
		} else {
			// the old feud: piglins and their brutes, on sight
			this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractPiglin.class, true));
		}
	}

	/** Whether it goes for {@code target} unprovoked: not while admiring gold; never an Emberborn; past {@link #SIGHT} only one wearing gold. */
	private boolean hunts(LivingEntity target) {
		if (this.admiring() || this.routed() || this.homeward || !(target instanceof Player player)) {
			return false;
		}
		if (!this.zombified() && Heritage.is(player, Heritage.Kind.EMBERBORN)) {
			return false;
		}
		return this.distanceTo(player) <= SIGHT || !this.zombified() && wearsGold(player);
	}

	static boolean wearsGold(LivingEntity entity) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (slot.isArmor() && entity.getItemBySlot(slot).is(ItemTags.PIGLIN_SAFE_ARMOR)) {
				return true;
			}
		}
		return false;
	}

	static boolean gold(ItemStack stack) {
		return stack.is(ItemTags.PIGLIN_LOVED);
	}

	// ---- coming into the world -----------------------------------------------------------------------------------------

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		switch (this.kind) {
			case WARRIOR -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextFloat() < 0.6F ? Items.GOLDEN_AXE : Items.GOLDEN_SWORD));
			case BRUTE, WARLORD -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
			case ZOMBIFIED -> this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
			case HURLER -> {
			}
		}
	}

	// ---- each tick -----------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.zombified()) {
			if (this.portal == null) {
				this.turnOutside(level);
			} else {
				// (0.7.146) a raid is short enough that the Overworld doesn't turn it
				this.raidStep(level);
			}
			if (this.isRemoved()) {
				return;
			}
			if ((this.tickCount + this.getId()) % FEUD_EVERY == 0) {
				this.provokePiglins(level);
			}
		}
		if (this.admireTicks > 0 && --this.admireTicks == 0) {
			this.doneAdmiring(level);
		}
		if (this.routed > 0) {
			this.flee(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (this.kind == Kind.WARRIOR || this.kind == Kind.WARLORD) {
			this.charge(level, target);
		}
		if (this.kind == Kind.BRUTE || this.kind == Kind.WARLORD) {
			if (this.stampReady > 0) {
				this.stampReady--;
			} else if (target != null && target.isAlive() && this.onGround() && this.distanceTo(target) < STAMP_TRIGGER) {
				this.stamp(level);
			}
		}
		if (this.bar != null) {
			if (this.rallyReady > 0) {
				this.rallyReady--;
			} else if (target != null && this.getHealth() < this.getMaxHealth() * RALLY_BELOW) {
				this.rally(level, target);
			}
			this.bar.setName(this.getDisplayName());
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
			BossBars.show(level, this, this.bar);
		}
	}

	/** (0.7.142) The warlord roars: every Hornkin near him fights harder and faster and turns on his foe, and a few more come running. */
	private void rally(ServerLevel level, LivingEntity foe) {
		this.rallyReady = RALLY_EVERY;
		for (Hornkin other : level.getEntitiesOfClass(Hornkin.class, this.getBoundingBox().inflate(RALLY_RANGE), h -> h.isAlive() && !h.zombified())) {
			other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, 0));
			other.addEffect(new MobEffectInstance(MobEffects.SPEED, RALLY_TICKS, 0));
			if (other != this) {
				other.setTarget(foe);
			}
		}
		NetherForts.Tag tag = NetherForts.tag(this);
		for (int i = 0; i < RALLY_CALLS; i++) {
			Hornkin called = Nether.HORNKIN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (called == null) {
				continue;
			}
			called.snapTo(this.getX() + this.random.nextGaussian() * 2.0, this.getY(), this.getZ() + this.random.nextGaussian() * 2.0,
				this.random.nextFloat() * 360.0F, 0.0F);
			called.finalizeSpawn(level, level.getCurrentDifficultyAt(this.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
			if (tag != null) {
				NetherForts.enlist(called, tag.fort(), NetherForts.GARRISON);
			}
			called.setTarget(foe);
			level.addFreshEntity(called);
		}
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(1.0), this.getZ(), 10, 0.5, 0.3, 0.5, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.7F);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(32.0))) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hornkin_rally", this.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** (0.7.142) Its warlord is dead: it runs from players, and when none is near once the rout is over, it's gone. */
	void rout() {
		this.routed = 1;
		this.marchTo = null;
		this.setTarget(null);
		this.clearHome();
	}

	boolean routed() {
		return this.routed > 0;
	}

	private void flee(ServerLevel level) {
		this.setTarget(null);
		if (++this.routed > ROUT_TICKS && level.getNearestPlayer(this, ROUT_GONE_RANGE) == null) {
			this.dropHoard(level);
			this.discard();
		}
	}

	/** (0.7.142) Sends it marching on {@code goal} (a rival fort), with its host. */
	void march(BlockPos goal) {
		this.marchTo = goal;
	}

	@Nullable BlockPos marchingTo() {
		return this.marchTo;
	}

	/** (0.7.146) Sends it raiding {@code town} for {@link #RAID_LOOT} gold or {@code ticks}, from (and back to) {@code portal}; see {@link PortalRaids}. */
	void raid(int party, BlockPos portal, BlockPos town, int ticks) {
		this.raid = party;
		this.portal = portal;
		this.raidLeft = ticks;
		this.marchTo = town;
		this.setPersistenceRequired();
	}

	boolean raiding() {
		return this.portal != null;
	}

	int raidId() {
		return this.raid;
	}

	/** (0.7.146) A raider turns for the portal (time's up, its loot's enough, or it's been bought off). */
	void goHome() {
		if (this.portal == null || this.homeward) {
			return;
		}
		this.homeward = true;
		this.raidLeft = HOMEWARD_MOST;
		this.clearHome();
		this.marchTo = this.portal;
		this.setTarget(null);
	}

	private void raidStep(ServerLevel level) {
		if (this.portal == null) {
			return;
		}
		if (!this.homeward) {
			if (--this.raidLeft <= 0 || this.hoard >= RAID_LOOT) {
				this.goHome();
			}
			return;
		}
		Vec3 to = Vec3.atBottomCenterOf(this.portal).subtract(this.position());
		if (--this.raidLeft <= 0 || to.horizontalDistance() < PORTAL_REACHED && Math.abs(to.y) < 3.0) {
			PortalRaids.leave(level, this);
		}
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		// (0.7.146) a raider goes home its own way (it carries its gold off through the portal: see raidStep)
		return this.portal == null && super.canUsePortal(ignorePassenger);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (this.bar != null) {
			this.bar.removePlayer(player);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		if (this.bar != null) {
			this.bar.removeAllPlayers();
		}
	}

	/** Out of the Nether (wherever piglins turn) it lasts {@link #TURN_TICKS}, then rots into a zombified Hornkin. */
	private void turnOutside(ServerLevel level) {
		if (!this.isNoAi() && level.environmentAttributes().getValue(EnvironmentAttributes.PIGLINS_ZOMBIFY, this.position())) {
			if (++this.timeOut > TURN_TICKS) {
				this.dropHoard(level);
				this.convertTo(Nether.ZOMBIFIED_HORNKIN, ConversionParams.single(this, true, true),
					zombified -> zombified.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0)));
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOGLIN_ANGRY, SoundSource.HOSTILE, 1.0F, 0.8F);
			}
		} else {
			this.timeOut = 0;
		}
	}

	/** Every piglin near that sees it, and isn't already angry at someone, goes for it (the feud cuts both ways). */
	private void provokePiglins(ServerLevel level) {
		for (AbstractPiglin piglin : level.getEntitiesOfClass(AbstractPiglin.class, this.getBoundingBox().inflate(FEUD_RANGE),
			p -> p.isAlive() && !p.isBaby() && p.hasLineOfSight(this))) {
			if (!piglin.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
				piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, this.getUUID(), 600L);
			}
		}
	}

	/** A warrior lowers its horns and runs at a foe a few strides off; the first it meets is gored and thrown. */
	private void charge(ServerLevel level, @Nullable LivingEntity target) {
		if (this.chargeTicks > 0) {
			this.chargeTicks--;
			if (target == null || !target.isAlive()) {
				this.endCharge();
				return;
			}
			Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-4) {
				Vec3 run = to.normalize().scale(CHARGE_SPEED);
				this.setDeltaMovement(run.x, this.getDeltaMovement().y, run.z);
				this.getLookControl().setLookAt(target);
			}
			if (this.distanceTo(target) < GORE_REACH) {
				DamageSource source = level.damageSources().mobAttack(this);
				float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * GORE_BONUS;
				if (this.kind == Kind.WARLORD && target instanceof Player player && player.isBlocking()) {
					// the warlord's horns go through a shield: it's useless a while
					player.getCooldowns().addCooldown(player.getUseItem(), SHIELD_BREAK_TICKS);
					player.stopUsingItem();
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
				}
				if (target.hurtServer(level, source, damage)) {
					Vec3 push = to.lengthSqr() > 1.0E-4 ? to.normalize().scale(GORE_THROW) : Vec3.ZERO;
					target.setDeltaMovement(push.x, 0.5, push.z);
					target.syncVelocity = true;
				}
				this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.0F, 1.2F);
				this.endCharge();
			} else if (this.chargeTicks == 0) {
				this.endCharge();
			} else if (this.tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.CRIMSON_SPORE, this.getX(), this.getY() + 0.1, this.getZ(), 4, 0.3, 0.05, 0.3, 0.0);
			}
			return;
		}
		if (this.chargeReady > 0) {
			this.chargeReady--;
			return;
		}
		if (target != null && target.isAlive() && this.onGround() && !this.admiring() && this.hasLineOfSight(target)) {
			double d = this.distanceTo(target);
			if (d > CHARGE_FROM && d < CHARGE_TO) {
				this.chargeTicks = CHARGE_TICKS;
				this.chargeReady = CHARGE_EVERY + this.random.nextInt(CHARGE_EVERY / 2);
				this.entityData.set(CHARGING, true);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 1.2F, 0.7F);
			}
		}
	}

	private void endCharge() {
		this.chargeTicks = 0;
		this.entityData.set(CHARGING, false);
	}

	/** The brute brings a foot down: everyone close (but its own) is hurt and thrown. */
	private void stamp(ServerLevel level) {
		this.stampReady = STAMP_EVERY + this.random.nextInt(STAMP_EVERY / 2);
		this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		DamageSource source = level.damageSources().mobAttack(this);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(STAMP_RADIUS, 1.5, STAMP_RADIUS),
			e -> e != this && e.isAlive() && !(e instanceof Hornkin) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			e.hurtServer(level, source, STAMP_DAMAGE);
			Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(STAMP_THROW);
			e.setDeltaMovement(push.x, 0.5, push.z);
			e.syncVelocity = true;
		}
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 0.1, this.getZ(), 12, 1.5, 0.1, 1.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		MagmaBomb bomb = new MagmaBomb(level, this);
		double dx = target.getX() - this.getX();
		double dy = target.getY(0.33) - bomb.getY();
		double dz = target.getZ() - this.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		bomb.shoot(dx, dy + flat * 0.25, dz, 1.0F, 6.0F);
		level.addFreshEntity(bomb);
		this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8F, 0.8F);
	}

	// ---- gold ----------------------------------------------------------------------------------------------------------

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return !this.zombified() && !this.admiring() && gold(stack) && this.getOffhandItem().isEmpty();
	}

	@Override
	protected void pickUpItem(ServerLevel level, ItemEntity entity) {
		ItemStack stack = entity.getItem();
		if (!this.wantsToPickUp(level, stack)) {
			return;
		}
		ItemStack one = stack.split(1);
		this.onItemPickup(entity);
		this.take(entity, 1);
		if (stack.isEmpty()) {
			entity.discard();
		}
		this.admire(level, one);
	}

	/** It stops, gold in its off hand, and looks at it a while; nothing provokes it meanwhile but a blow. */
	void admire(ServerLevel level, ItemStack gold) {
		this.setItemSlot(EquipmentSlot.OFFHAND, gold);
		this.setGuaranteedDrop(EquipmentSlot.OFFHAND);
		this.setPersistenceRequired();
		this.admireTicks = ADMIRE_TICKS;
		this.entityData.set(ADMIRING, true);
		this.setTarget(null);
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HOGLIN_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.3F);
	}

	/** Done admiring: an ingot it trades for some of its wares, thrown at its feet; the rest goes into its hoard. */
	private void doneAdmiring(ServerLevel level) {
		ItemStack gold = this.getOffhandItem();
		this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		this.entityData.set(ADMIRING, false);
		Player trader = this.trader == null ? null : level.getPlayerByUUID(this.trader);
		this.trader = null;
		boolean truce = trader != null && Heritage.is(trader, Heritage.Kind.EMBERBORN);
		if (this.portal != null && trader == null) {
			// (0.7.146) a raider keeps what it takes, to carry home
			if (!gold.isEmpty()) {
				this.hoard++;
			}
		} else if (gold.is(Items.GOLD_INGOT)) {
			int n = 1 + this.random.nextInt(WARES_MOST) + (truce ? TRUCE_EXTRA : 0);
			for (int i = 0; i < n; i++) {
				this.spawnAtLocation(level, WARES.get(this.random.nextInt(WARES.size())).get());
			}
			if (truce && this.random.nextFloat() < TRUCE_RARE_CHANCE) {
				this.spawnAtLocation(level, TRUCE_WARES.get(this.random.nextInt(TRUCE_WARES.size())).get());
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.HOSTILE, 1.0F, 0.6F);
		} else if (truce && gold.is(Items.GOLD_BLOCK)) {
			// (0.7.146) a whole block, in good faith: its best
			for (int i = 0; i < RARE_FOR_BLOCK; i++) {
				this.spawnAtLocation(level, TRUCE_WARES.get(this.random.nextInt(TRUCE_WARES.size())).get());
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.HOSTILE, 1.0F, 0.5F);
		} else if (!gold.isEmpty()) {
			this.hoard++;
		}
	}

	/**
	 * (0.7.146) The truce: an Emberborn can hand it gold (a trade, with better wares, see {@link #doneAdmiring}); and a
	 * raider handed a gold block by an Emberborn takes it and leads its whole party home ({@link PortalRaids#boughtOff}).
	 */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(this.level() instanceof ServerLevel level) || this.zombified() || this.routed() || this.getTarget() == player || !gold(held)
			|| !Heritage.is(player, Heritage.Kind.EMBERBORN)) {
			return super.mobInteract(player, hand);
		}
		if (this.portal != null && !this.homeward && held.is(Items.GOLD_BLOCK)) {
			held.consume(1, player);
			this.hoard++;
			PortalRaids.boughtOff(level, this, player);
			return InteractionResult.SUCCESS;
		}
		if (!this.wantsToPickUp(level, held)) {
			return super.mobInteract(player, hand);
		}
		ItemStack one = held.copyWithCount(1);
		held.consume(1, player);
		this.trader = player.getUUID();
		this.admire(level, one);
		return InteractionResult.SUCCESS;
	}

	private void dropHoard(ServerLevel level) {
		if (this.hoard > 0) {
			this.spawnAtLocation(level, new ItemStack(Items.GOLD_NUGGET, Math.min(64, this.hoard * 3)));
			this.hoard = 0;
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.dropHoard(level);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.admiring() && source.getEntity() instanceof LivingEntity) {
			// a blow ends it: the gold goes in the hoard and it turns on whoever struck
			this.admireTicks = 0;
			this.doneAdmiring(level);
		}
		return hurt;
	}

	// ---- sound and saving ----------------------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return this.zombified() ? SoundEvents.ZOGLIN_AMBIENT : SoundEvents.HOGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.zombified() ? SoundEvents.ZOGLIN_HURT : SoundEvents.HOGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.zombified() ? SoundEvents.ZOGLIN_DEATH : SoundEvents.HOGLIN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * (this.kind == Kind.WARLORD ? 0.55F : this.kind == Kind.BRUTE ? 0.65F : 0.8F);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return this.hoard == 0 && !this.admiring() && NetherForts.tag(this) == null && this.portal == null && super.removeWhenFarAway(distSqr);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Hoard", this.hoard);
		output.putInt("TimeOutside", this.timeOut);
		output.putInt("Admire", this.admireTicks);
		output.putInt("Routed", this.routed);
		if (this.marchTo != null) {
			output.putLong("MarchTo", this.marchTo.asLong());
		}
		if (this.portal != null) {
			output.putLong("RaidPortal", this.portal.asLong());
			output.putInt("Raid", this.raid);
			output.putInt("RaidLeft", this.raidLeft);
			output.putBoolean("Homeward", this.homeward);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.hoard = input.getIntOr("Hoard", 0);
		this.timeOut = input.getIntOr("TimeOutside", 0);
		this.admireTicks = input.getIntOr("Admire", 0);
		this.routed = input.getIntOr("Routed", 0);
		long march = input.getLongOr("MarchTo", Long.MIN_VALUE);
		this.marchTo = march == Long.MIN_VALUE ? null : BlockPos.of(march);
		long portal = input.getLongOr("RaidPortal", Long.MIN_VALUE);
		this.portal = portal == Long.MIN_VALUE ? null : BlockPos.of(portal);
		this.raid = input.getIntOr("Raid", 0);
		this.raidLeft = input.getIntOr("RaidLeft", 0);
		this.homeward = input.getBooleanOr("Homeward", false);
		this.entityData.set(ADMIRING, this.admireTicks > 0);
	}

	// ---- goals ---------------------------------------------------------------------------------------------------------

	/** Stands still and looks at the gold in its hand. */
	private final class AdmireGoal extends Goal {
		AdmireGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return Hornkin.this.admiring();
		}

		@Override
		public void start() {
			Hornkin.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			Hornkin.this.getLookControl().setLookAt(Hornkin.this.getX(), Hornkin.this.getEyeY() - 0.8, Hornkin.this.getZ());
		}
	}

	/** Walks to the nearest gold on the ground (the pickup itself is a mob's own, through {@link #wantsToPickUp}). */
	private final class SeekGoldGoal extends Goal {
		private @Nullable ItemEntity item;

		SeekGoldGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (Hornkin.this.getTarget() != null || Hornkin.this.admiring() || !Hornkin.this.getOffhandItem().isEmpty()
				|| (Hornkin.this.tickCount + Hornkin.this.getId()) % 10 != 0 || !(Hornkin.this.level() instanceof ServerLevel level)) {
				return false;
			}
			List<ItemEntity> gold = level.getEntitiesOfClass(ItemEntity.class, Hornkin.this.getBoundingBox().inflate(GOLD_SEEK, 4.0, GOLD_SEEK),
				e -> e.isAlive() && gold(e.getItem()));
			this.item = null;
			double best = Double.MAX_VALUE;
			for (ItemEntity e : gold) {
				double d = Hornkin.this.distanceToSqr(e);
				if (d < best) {
					best = d;
					this.item = e;
				}
			}
			return this.item != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.item != null && this.item.isAlive() && Hornkin.this.getTarget() == null && !Hornkin.this.admiring()
				&& !Hornkin.this.getNavigation().isDone();
		}

		@Override
		public void start() {
			if (this.item != null) {
				Hornkin.this.getNavigation().moveTo(this.item, 1.15);
			}
		}

		@Override
		public void stop() {
			this.item = null;
		}
	}

	/**
	 * (0.7.142) On the march, it strides towards its host's goal; at home in a fort, it keeps within its walls (the strolls
	 * already keep near home; this brings it back when a fight has drawn it out).
	 */
	private final class MarchGoal extends Goal {
		MarchGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (Hornkin.this.getTarget() != null || Hornkin.this.admiring() || Hornkin.this.routed()) {
				return false;
			}
			if (Hornkin.this.marchTo != null) {
				return true;
			}
			return Hornkin.this.hasHome() && !Hornkin.this.isWithinHome();
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse() && !Hornkin.this.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlockPos goal = Hornkin.this.marchTo != null ? Hornkin.this.marchTo : Hornkin.this.getHomePosition();
			Vec3 to = Vec3.atBottomCenterOf(goal).subtract(Hornkin.this.position());
			if (Hornkin.this.homeward) {
				// (0.7.146) home through the portal: right up to it (raidStep sees it through)
				Vec3 step = to.horizontalDistance() > MARCH_STRIDE ? Hornkin.this.position().add(to.normalize().scale(MARCH_STRIDE)) : Vec3.atBottomCenterOf(goal);
				Hornkin.this.getNavigation().moveTo(step.x, step.y, step.z, 1.1);
				return;
			}
			if (Hornkin.this.marchTo != null && to.horizontalDistance() < MARCH_ARRIVED) {
				// arrived: it holds the field here, at the walls it came to take (or, raiding, roams the town for gold)
				Hornkin.this.setHomeTo(goal, Hornkin.this.portal != null ? RAID_RADIUS : NetherForts.HOLD_RADIUS);
				Hornkin.this.marchTo = null;
				return;
			}
			Vec3 step = to.horizontalDistance() > MARCH_STRIDE ? Hornkin.this.position().add(to.normalize().scale(MARCH_STRIDE))
				: Vec3.atBottomCenterOf(goal);
			Hornkin.this.getNavigation().moveTo(step.x, step.y, step.z, 1.0);
		}
	}

	/** Now and then, with nothing to fight, it goes to a chest near it with gold inside and takes some. */
	private final class RaidChestGoal extends Goal {
		private @Nullable BlockPos chest;
		private int ticks;

		RaidChestGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			// (0.7.142) a fort's own leave its hoard be
			if (Hornkin.this.getTarget() != null || Hornkin.this.admiring() || !Hornkin.this.getOffhandItem().isEmpty() || NetherForts.tag(Hornkin.this) != null
				|| Hornkin.this.homeward
				|| (Hornkin.this.tickCount + Hornkin.this.getId()) % CHEST_EVERY != 0 || !(Hornkin.this.level() instanceof ServerLevel level)) {
				return false;
			}
			this.chest = null;
			BlockPos at = Hornkin.this.blockPosition();
			int reach = Hornkin.this.portal != null ? RAID_CHEST_REACH : CHEST_REACH;
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, -CHEST_HEIGHT, -reach), at.offset(reach, CHEST_HEIGHT, reach))) {
				if (level.getBlockEntity(pos) instanceof Container container && slotWithGold(container) >= 0) {
					this.chest = pos.immutable();
					return true;
				}
			}
			return false;
		}

		@Override
		public void start() {
			this.ticks = 20 * 10;
			if (this.chest != null) {
				Hornkin.this.getNavigation().moveTo(this.chest.getX() + 0.5, this.chest.getY(), this.chest.getZ() + 0.5, 1.0);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return this.chest != null && this.ticks > 0 && Hornkin.this.getTarget() == null && !Hornkin.this.admiring();
		}

		@Override
		public void tick() {
			this.ticks--;
			if (this.chest == null || !(Hornkin.this.level() instanceof ServerLevel level)) {
				return;
			}
			if (Hornkin.this.distanceToSqr(Vec3.atCenterOf(this.chest)) > 2.5 * 2.5) {
				if (Hornkin.this.getNavigation().isDone()) {
					Hornkin.this.getNavigation().moveTo(this.chest.getX() + 0.5, this.chest.getY(), this.chest.getZ() + 0.5, 1.0);
				}
				return;
			}
			if (level.getBlockEntity(this.chest) instanceof Container container) {
				int slot = slotWithGold(container);
				if (slot >= 0) {
					ItemStack taken = container.removeItem(slot, 1);
					container.setChanged();
					level.playSound(null, this.chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 0.8F);
					Hornkin.this.admire(level, taken);
				}
			}
			this.chest = null;
		}

		@Override
		public void stop() {
			this.chest = null;
		}
	}

	/** A slot of {@code container} with gold in it, or -1 (a chest whose loot isn't rolled yet is left for players to open first). */
	static int slotWithGold(Container container) {
		if (container instanceof RandomizableContainer loot && loot.getLootTable() != null) {
			return -1;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (gold(container.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && this.kind == Kind.ZOMBIFIED && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 8, 0), this);
		}
		return hit;
	}
}
