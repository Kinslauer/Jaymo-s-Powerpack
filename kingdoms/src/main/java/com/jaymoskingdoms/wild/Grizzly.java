package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.beasts.Acting;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.610) The grizzly, the wilds' first real danger (Andy: "an agressive mini boss grizzly bear ... add a real early
 * threat"). A huge, silver-backed bear of the taiga, the old forests and the windswept hills, one to a wide stretch of
 * land, which keeps a territory round where it was born ({@link #TERRITORY}). It doesn't flee from people as the brown
 * and black bears do (Bear Minimum's, which stay shy): it notices a player within {@link #NOTICE} blocks (half that
 * sneaking), rears up on its hind legs and roars its warning, and then comes. Up close it rakes with either paw; from
 * farther off it charges, and whoever it runs down it may maul, pinned under it while it bites; standing again it
 * brings both forepaws down in a slam that throws everyone about it. Badly hurt it roars again, and fights faster and
 * harder. Its boss bar shows to those who've hit it ({@code BossBars}).
 *
 * <p>Two ways out of a maul, as with a real bear: hurt it enough ({@link #MAUL_BREAK}) and it lets go, staggered; or
 * play dead (crouch and stop fighting) and, while it isn't enraged, it sniffs you over and wanders off, leaving you
 * alone for a while ({@link #CALM_TICKS}).
 *
 * <p>(0.7.611) Its model and coat are our own, built from {@code docs/Grizzly-Model-Spec.md} by the REA method (Andy: "Can
 * you use rea for models"): the client's {@code GrizzlyModel}, the coat painted by {@code tools/gen_grizzly.py}.
 */
public class Grizzly extends WildBeast implements Acting {
	static final double HEALTH = 120.0, ARMOR = 6.0, DAMAGE = 9.0, SPEED = 0.3, KNOCKBACK_RESISTANCE = 0.75;
	/** How far it notices a player (blocks), and how much of that a sneaking one is seen at. */
	static final double NOTICE = 24.0, SNEAK_NOTICE = 0.5;
	/** How near a villager may come before it goes for them (blocks). */
	static final double VILLAGER_NOTICE = 12.0;
	/** Its territory round where it was born (blocks): it wanders within, and comes back to it when its hunt is done. */
	static final int TERRITORY = 40;
	/** How fast it closes in, and enraged. */
	static final double CHASE = 1.3, CHASE_ENRAGED = 1.55;
	/** Its rear-up: how long (ticks), when the forepaws come down, how far the slam reaches, how hard, how often. */
	static final int REAR_TICKS = 34, REAR_SLAM = 26, REAR_COOLDOWN = 160;
	static final double SLAM_REACH = 3.5;
	static final float SLAM_DAMAGE = 10.0F;
	/** Its paw swipe: how long, when it lands, its reach. */
	static final int SWIPE_TICKS = 14, SWIPE_HIT = 7;
	static final double SWIPE_REACH = 2.6;
	/** Its charge: the most it runs (ticks), how fast (blocks a tick), its blow, how often, and the range it charges from. */
	static final int CHARGE_TICKS = 30, CHARGE_COOLDOWN = 180;
	static final double CHARGE_SPEED = 0.5, CHARGE_MIN = 5.0, CHARGE_MAX = 18.0;
	static final float CHARGE_DAMAGE = 11.0F;
	/** Its maul: how long (ticks), when it bites, how hard, how much damage frees its victim, its chance after a charge. */
	static final int MAUL_TICKS = 46, MAUL_COOLDOWN = 240;
	static final int[] MAUL_BITES = {10, 22, 34};
	static final float MAUL_BITE = 4.0F, MAUL_BREAK = 8.0F, MAUL_CHANCE = 0.6F;
	/** How long its victim must lie still (crouched, not striking it) to be left for dead (ticks), and how long it stays calm. */
	static final int PLAY_DEAD_TICKS = 30, CALM_TICKS = 1200;
	/** Its enraged roar: below this share of its health, how long the roar runs, and how far it frightens players. */
	static final float ENRAGE_AT = 0.35F;
	static final int ROAR_TICKS = 40;
	static final double ROAR_REACH = 10.0;
	/** Staggered after a broken maul or a charge into a wall (ticks). */
	static final int STAGGER_TICKS = 24;
	/** Ticks between blows, and enraged. */
	static final int GAP = 14, GAP_ENRAGED = 8;
	/** How far off a player can be and it still hunts them (blocks). */
	static final double LOSE_RANGE = 48.0;
	/** Spawning: no other grizzly this near (blocks), none near a settlement, and its share of tries that come to anything. */
	static final double ALONE = 128.0, SETTLEMENT_CLEARANCE = 160.0;
	static final float SPAWN_CHANCE = 0.5F;

	/** Its moves, synced by their place here (the client's GrizzlyModel poses each). */
	public static final String[] ACTIONS = {"rear", "swipe_left", "swipe_right", "charge", "maul", "roar", "stagger"};

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Grizzly.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(Grizzly.class, EntityDataSerializers.BOOLEAN);

	/** (Server) how long the current move has left, and has run (ticks). */
	private int actionLeft, actionRun;
	/** (Client) the tick its move began. */
	private int actionStartedAt;
	private int gap, rearCooldown, chargeCooldown = CHARGE_COOLDOWN / 2, maulCooldown;
	private Vec3 chargeDir = Vec3.ZERO;
	/** (Server) whom it's mauling, what they've done to it since, and how long they've lain still. */
	private @Nullable UUID mauling;
	private float maulHurt;
	private int stillFor;
	/** (Server) game time until which it leaves people be (after someone played dead). */
	private long calmUntil;
	/** (Server) whom it last warned, so a new quarry gets its rear-up and roar first. */
	private @Nullable UUID warned;
	private @Nullable ServerBossEvent bar;

	public Grizzly(EntityType<? extends Grizzly> type, Level level) {
		super(type, level);
		this.xpReward = 35;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** Natural spawning: as any animal, out under the sky, alone, rare, and away from settlements once the world is made. */
	public static boolean checkSpawn(EntityType<Grizzly> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!checkAnimalSpawnRules(type, level, reason, pos, random) || !level.canSeeSky(pos) || random.nextFloat() >= SPAWN_CHANCE) {
			return false;
		}
		if (reason == EntitySpawnReason.CHUNK_GENERATION) {
			// (off the server thread while the land is made: no looking about for neighbours)
			return true;
		}
		if (!level.getEntitiesOfClass(Grizzly.class, new AABB(pos).inflate(ALONE)).isEmpty()) {
			return false;
		}
		ServerLevel server = level.getLevel();
		return com.jaymoskingdoms.Factions.around(server.getServer(), server, pos, SETTLEMENT_CLEARANCE) == null;
	}

	@Override
	protected int variants() {
		return 1;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false, (target, level) -> this.notices(target, level)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && !this.calm(level) && target.distanceTo(this) < VILLAGER_NOTICE));
	}

	/** Whether it marks {@code target}: near enough (a sneaking one only half as far), and it isn't leaving people be. */
	private boolean notices(LivingEntity target, ServerLevel level) {
		if (level.getDifficulty() == Difficulty.PEACEFUL || this.calm(level)) {
			return false;
		}
		double range = target.isDiscrete() ? NOTICE * SNEAK_NOTICE : NOTICE;
		return target.distanceTo(this) < range;
	}

	private boolean calm(ServerLevel level) {
		return level.getGameTime() < this.calmUntil;
	}

	public boolean enraged() {
		return this.entityData.get(ENRAGED);
	}

	@Override
	public boolean hostileLook() {
		return this.enraged();
	}

	// ---- its moves -------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ACTION, -1);
		entityData.define(ENRAGED, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (ACTION.equals(key)) {
			this.actionStartedAt = this.tickCount;
		}
	}

	private void act(String anim, int ticks) {
		for (int i = 0; i < ACTIONS.length; i++) {
			if (ACTIONS[i].equals(anim)) {
				this.entityData.set(ACTION, i, true);
				this.actionLeft = ticks;
				this.actionRun = 0;
				return;
			}
		}
	}

	private void stopActing() {
		this.entityData.set(ACTION, -1);
		this.actionLeft = 0;
		this.mauling = null;
	}

	private boolean acting() {
		return this.actionLeft > 0;
	}

	private boolean acting(String anim) {
		return this.acting() && anim.equals(this.action());
	}

	@Override
	public @Nullable String action() {
		int i = this.entityData.get(ACTION);
		return i >= 0 && i < ACTIONS.length ? ACTIONS[i] : null;
	}

	@Override
	public int actionAge() {
		return this.tickCount - this.actionStartedAt;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide() && this.actionLeft > 0) {
			this.actionRun++;
			if (--this.actionLeft == 0) {
				this.entityData.set(ACTION, -1);
				if (this.mauling != null) {
					// done with them: flung off
					Entity victim = ((ServerLevel) this.level()).getEntity(this.mauling);
					this.endMaul(victim instanceof LivingEntity living ? living : null);
				}
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.updateBar(level);
		this.rearCooldown--;
		this.chargeCooldown--;
		this.maulCooldown--;
		if (!this.hasHome()) {
			this.setHomeTo(this.blockPosition(), TERRITORY);
		}
		if (!this.enraged() && this.getHealth() < this.getMaxHealth() * ENRAGE_AT) {
			this.entityData.set(ENRAGED, true);
			this.stopActing();
			this.act("roar", ROAR_TICKS);
			this.roar(level);
			return;
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || target.distanceTo(this) > LOSE_RANGE || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(null);
			target = null;
		}
		if (this.acting()) {
			this.moving(level, target);
			return;
		}
		if (target == null) {
			// its hunt done, back into its territory
			if (this.hasHome() && !this.isWithinHome() && this.getNavigation().isDone()) {
				BlockPos home = this.getHomePosition();
				this.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.9);
			}
			return;
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double d = this.distanceTo(target);
		// a new quarry: first it stands and roars its warning
		if (!target.getUUID().equals(this.warned)) {
			this.warned = target.getUUID();
			this.getNavigation().stop();
			this.act("rear", REAR_TICKS);
			this.rearCooldown = REAR_COOLDOWN;
			this.playSound(SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.6F);
			return;
		}
		double chase = this.enraged() ? CHASE_ENRAGED : CHASE;
		if (--this.gap > 0) {
			this.getNavigation().moveTo(target, chase);
			return;
		}
		int cooldownScale = this.enraged() ? 2 : 1;
		if (d > CHARGE_MIN && d < CHARGE_MAX && this.chargeCooldown <= 0 && this.hasLineOfSight(target)) {
			this.chargeDir = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize();
			this.chargeCooldown = CHARGE_COOLDOWN / cooldownScale;
			this.act("charge", CHARGE_TICKS);
			this.playSound(SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.5F);
		} else if (d < SLAM_REACH && this.rearCooldown <= 0) {
			this.rearCooldown = REAR_COOLDOWN / cooldownScale;
			this.act("rear", REAR_TICKS);
			this.playSound(SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.7F);
		} else if (d < SWIPE_REACH + 1.0) {
			this.act(this.random.nextBoolean() ? "swipe_left" : "swipe_right", SWIPE_TICKS);
		} else {
			this.getNavigation().moveTo(target, chase);
			return;
		}
		this.gap = this.enraged() ? GAP_ENRAGED : GAP;
	}

	/** One tick of the move it's making. */
	private void moving(ServerLevel level, @Nullable LivingEntity target) {
		String action = this.action();
		if ("charge".equals(action)) {
			this.charge(level, target);
			return;
		}
		this.getNavigation().stop();
		if ("maul".equals(action)) {
			this.maul(level);
			return;
		}
		if (target != null) {
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		if (("swipe_left".equals(action) || "swipe_right".equals(action)) && this.actionRun == SWIPE_HIT) {
			this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), SWIPE_REACH, true, 1.0, 0.15);
			this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
		} else if ("rear".equals(action) && this.actionRun == REAR_SLAM) {
			this.slam(level);
		}
	}

	/** Charging: head low along its line, running down whoever it meets; a wall stops it, dazed. */
	private void charge(ServerLevel level, @Nullable LivingEntity target) {
		this.getNavigation().stop();
		this.setYRot((float) (Math.atan2(this.chargeDir.z, this.chargeDir.x) * 180.0 / Math.PI) - 90.0F);
		this.yBodyRot = this.getYRot();
		this.setDeltaMovement(this.chargeDir.x * CHARGE_SPEED, this.getDeltaMovement().y, this.chargeDir.z * CHARGE_SPEED);
		LivingEntity struck = this.strikeFirst(level, CHARGE_DAMAGE, 0.7, 1.6, 0.35);
		if (struck != null) {
			this.playSound(SoundEvents.POLAR_BEAR_HURT, 1.5F, 0.5F);
			this.stopActing();
			this.gap = this.enraged() ? GAP_ENRAGED : GAP;
			if (struck == target && this.maulCooldown <= 0 && (struck instanceof Player || struck instanceof AbstractVillager)
				&& this.random.nextFloat() < MAUL_CHANCE) {
				this.startMaul(struck);
			}
		} else if (this.horizontalCollision && this.actionRun > 4) {
			this.playSound(SoundEvents.POLAR_BEAR_HURT, 1.0F, 0.4F);
			this.stopActing();
			this.act("stagger", STAGGER_TICKS);
		}
	}

	private void startMaul(LivingEntity victim) {
		this.mauling = victim.getUUID();
		this.maulHurt = 0.0F;
		this.stillFor = 0;
		this.maulCooldown = MAUL_COOLDOWN;
		this.act("maul", MAUL_TICKS);
	}

	/** Mauling: its victim pinned under it, bitten again and again, until it's done, beaten off, or they play dead. */
	private void maul(ServerLevel level) {
		Entity found = this.mauling == null ? null : level.getEntity(this.mauling);
		if (!(found instanceof LivingEntity victim) || !victim.isAlive() || victim.distanceTo(this) > 4.0) {
			this.endMaul(null);
			return;
		}
		// pinned: held under its forepaws, no getting up
		Vec3 front = Vec3.directionFromRotation(0.0F, this.getYRot()).scale(this.getBbWidth() * 0.6);
		Vec3 pin = this.position().add(front);
		victim.setDeltaMovement((pin.x - victim.getX()) * 0.5, Math.min(0.0, victim.getDeltaMovement().y), (pin.z - victim.getZ()) * 0.5);
		victim.needsSync = true;
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 5, false, false, false));
		this.getLookControl().setLookAt(victim, 60.0F, 60.0F);
		for (int bite : MAUL_BITES) {
			if (this.actionRun == bite) {
				victim.hurtServer(level, this.damageSources().mobAttack(this), MAUL_BITE);
				this.playSound(SoundEvents.POLAR_BEAR_HURT, 1.2F, 0.45F);
			}
		}
		// playing dead: crouched and not fighting back. An enraged bear doesn't fall for it
		if (victim instanceof Player player && player.isShiftKeyDown() && !this.enraged()) {
			if (++this.stillFor >= PLAY_DEAD_TICKS) {
				this.calmUntil = level.getGameTime() + CALM_TICKS;
				this.endMaul(null);
				this.setTarget(null);
				this.setLastHurtByMob(null);
				this.playSound(SoundEvents.POLAR_BEAR_AMBIENT, 1.0F, 0.6F);
			}
		} else {
			this.stillFor = 0;
		}
	}

	/** The maul over: if {@code toss}, its victim is flung off. */
	private void endMaul(@Nullable LivingEntity toss) {
		this.mauling = null;
		if (this.acting("maul")) {
			this.stopActing();
		}
		if (toss != null) {
			toss.knockback(1.2, this.getX() - toss.getX(), this.getZ() - toss.getZ(), this.damageSources().mobAttack(this), 0.0F);
			toss.setDeltaMovement(toss.getDeltaMovement().add(0.0, 0.3, 0.0));
			toss.needsSync = true;
		}
	}

	/** Both forepaws brought down from standing: the ground breaks round it, everyone near thrown up. */
	private void slam(ServerLevel level) {
		this.strike(level, SLAM_DAMAGE, SLAM_REACH, false, 1.2, 0.5);
		BlockPos below = this.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.1, this.getZ(), 50, 1.6, 0.2, 1.6, 0.2);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.6F, 0.7F);
	}

	/** Its enraged roar: every player near is shaken (slowed and weakened a few seconds). */
	private void roar(ServerLevel level) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5F, 0.6F);
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(ROAR_REACH),
			p -> !p.isCreative() && !p.isSpectator())) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
		}
	}

	/** Its blow lands on everything within {@code reach} it would hit (only those in front, if {@code front}). */
	private int strike(ServerLevel level, float damage, double reach, boolean front, double knock, double up) {
		Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
		int hit = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach, reach * 0.5, reach),
			e -> e != this && e.isAlive() && this.strikes(e))) {
			Vec3 to = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (to.length() > reach + e.getBbWidth() / 2.0 + this.getBbWidth() / 2.0) {
				continue;
			}
			if (front && to.length() > 0.5 && to.normalize().dot(look) < 0.2) {
				continue;
			}
			if (this.hit(level, e, damage, knock, up)) {
				hit++;
			}
		}
		return hit;
	}

	/** The first thing it runs into while charging, struck; or null. */
	private @Nullable LivingEntity strikeFirst(ServerLevel level, float damage, double reach, double knock, double up) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach, 0.2, reach),
			e -> e != this && e.isAlive() && this.strikes(e))) {
			if (this.hit(level, e, damage, knock, up)) {
				return e;
			}
		}
		return null;
	}

	private boolean hit(ServerLevel level, LivingEntity e, float damage, double knock, double up) {
		DamageSource source = this.damageSources().mobAttack(this);
		if (!e.hurtServer(level, source, damage)) {
			return false;
		}
		e.knockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ(), source, damage);
		if (up > 0.0) {
			e.setDeltaMovement(e.getDeltaMovement().add(0.0, up, 0.0));
			e.needsSync = true;
		}
		return true;
	}

	/** Whether its blows land on {@code e}: its quarry, any player in play, and anything fighting it. */
	private boolean strikes(LivingEntity e) {
		return e == this.getTarget() || e instanceof Player player && !player.isCreative() && !player.isSpectator()
			|| e instanceof Mob mob && mob.getTarget() == this;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (its blows are its own moves)
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.mauling != null && source.getEntity() != null && this.mauling.equals(source.getEntity().getUUID())) {
			// beaten off: enough from its own victim and it lets go, staggered
			this.maulHurt += amount;
			this.stillFor = 0;
			if (this.maulHurt >= MAUL_BREAK) {
				this.endMaul(null);
				this.act("stagger", STAGGER_TICKS);
			}
		}
		if (hurt && source.getEntity() != null) {
			// whoever hurts it ends its calm
			this.calmUntil = 0L;
		}
		return hurt;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.setHomeTo(this.blockPosition(), TERRITORY);
		com.jaymoskingdoms.BeastLairs.grizzly(level, this.blockPosition(), reason);
		return data;
	}

	// ---- its bar ---------------------------------------------------------------------------------------------------

	private void updateBar(ServerLevel level) {
		if (this.bar == null) {
			this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		com.jaymoskingdoms.BossBars.show(level, this, this.bar);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.remove(reason);
	}

	@Override
	public void die(DamageSource source) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.die(source);
	}

	// ---- its voice -------------------------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.POLAR_BEAR_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.POLAR_BEAR_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.POLAR_BEAR_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.POLAR_BEAR_STEP, 0.4F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		return 0.7F;
	}

	@Override
	protected float getSoundVolume() {
		return 1.4F;
	}
}
