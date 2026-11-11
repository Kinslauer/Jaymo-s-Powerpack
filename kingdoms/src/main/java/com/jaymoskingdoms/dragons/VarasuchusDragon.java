package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Fx;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Heritage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.522) Varasuchus, the crocodile dragon of the lesser dragons ({@code docs/SaintsDragons-Plan.md}): its moves and
 * numbers are ported from Saint's Dragons by Leon Saint (MIT licence; none of its art, models or sounds are used: the
 * model is our own, {@code kingdoms/art/varasuchus.bbmodel} animated by GeckoLib ({@link VarasuchusAnimations}), painted by
 * {@code tools/gen_varasuchus.py}). A walker and a swimmer
 * ({@link WindDragon#swims}) of the beaches and the swamps, high on long sprawling legs, armoured in bony scutes, with a
 * heavy blocky head and a crested tail long enough to wrap round to its front. Unlike the grazers it hunts: it goes for anyone who comes near, and it is quick in the water.
 * <p>
 * Its moves, besides the bite and the tail sweep every dragon has:
 * <ul>
 * <li><b>Claws</b> raked at whoever's before it ({@link #CLAW}), and a <b>horn gore</b> that tosses them ({@link #HORN}).</li>
 * <li><b>Dash:</b> at range it charges, clawing as it reaches them and lashing its tail round after ({@link #DASH_CLAW},
 * {@link #DASH_TAIL}).</li>
 * <li><b>Slash barrage:</b> up close, a flurry of claws ({@link #BARRAGE_HITS} blows).</li>
 * <li><b>Phase shift:</b> pressed, it slips past its foe in a blur and turns on them from behind, untouchable while it
 * does.</li>
 * <li><b>Tailguard:</b> struck, it may swing its tail round as a guard: the next blow at it (within a few blocks) is
 * parried as our guard parries ({@code MobParry}'s sparks and ring of steel), the striker hurt and thrown back.</li>
 * <li>Worn to half its health it rages: it rears up on its hind legs and fights with its arms, harder bites, horns and claws,
 * and its moves come quicker.</li>
 * </ul>
 * Tamed and ridden it's steered like a horse on land and swims where the rider looks in water; R charges where the
 * rider looks, goring at the end. Its favourite is raw salmon; Tideborn win it over twice as fast.
 */
public class VarasuchusDragon extends WindDragon implements GeoEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	static final double HEALTH = 200.0, ARMOR = 8.0, TOUGHNESS = 3.0, SPEED = 0.24, BITE = 15.0, RAGE_BITE = 25.0;
	/** Claws: damage (raging), pace. */
	static final float CLAW = 8.0F, RAGE_CLAW = 12.0F;
	static final int CLAW_EVERY = 30;
	/** The horn gore: damage (raging), the toss, pace. */
	static final float HORN = 8.0F, RAGE_HORN = 15.8F;
	static final double HORN_LIFT = 0.7, HORN_THROW = 0.6;
	static final int HORN_EVERY = 70;
	/** The dash: its range, speed, the claw and the tail lash, pace, how long it may run, the tick its tail comes round. */
	static final double DASH_MIN = 6.0, DASH_MAX = 16.0, DASH_SPEED = 0.75;
	static final float DASH_CLAW = 15.0F, DASH_TAIL = 10.0F;
	static final int DASH_EVERY = 120, DASH_TICKS = 24, DASH_TAIL_AFTER = 6;
	/** The slash barrage: blows, a tick gap between them, each one's damage, pace. */
	static final int BARRAGE_HITS = 8, BARRAGE_GAP = 6, BARRAGE_EVERY = 260;
	static final float BARRAGE = 5.0F;
	/** The phase shift: how far past its foe it comes out, pace, how long it can't be touched (ticks); a hit's chance of setting it off. */
	static final double SHIFT_PAST = 3.5;
	static final int SHIFT_EVERY = 200, SHIFT_TICKS = 10;
	static final float SHIFT_CHANCE = 0.2F;
	/** Tailguard: a hit's chance of raising it, its startup and how long it's held (ticks), pace; the parry's hurt, throw and reach. */
	static final float GUARD_CHANCE = 0.35F, PARRY = 10.0F;
	static final int GUARD_STARTUP = 12, GUARD_HOLD = 40, GUARD_EVERY = 160, STAGGER_TICKS = 20;
	static final double PARRY_THROW = 1.2, PARRY_REACH = 8.0;
	/** It rages below this share of its health: its moves come this much quicker. */
	static final float RAGE_AT = 0.5F, RAGE_PACE = 0.7F;
	/** Ridden: the charge's pace (ticks). */
	static final int RIDER_DASH_EVERY = 80;

	/** Its moves, for the model ({@link #action}). */
	public static final int NONE = 0, CLAW_MOVE = 1, HORN_MOVE = 2, DASH_MOVE = 3, BARRAGE_MOVE = 4, SHIFT_MOVE = 5, GUARD_MOVE = 6, PARRY_MOVE = 7,
		RAGE_MOVE = 8, BITE_MOVE = 9, SWEEP_MOVE = 10;
	/** How long each lasts (ticks). */
	static final int[] ACTION_TICKS = {0, 10, 14, DASH_TICKS + DASH_TAIL_AFTER + 6, BARRAGE_HITS * BARRAGE_GAP + 6, SHIFT_TICKS + 4,
		GUARD_STARTUP + GUARD_HOLD, 12, 30, 16, 26};

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(VarasuchusDragon.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_RAGING = SynchedEntityData.defineId(VarasuchusDragon.class, EntityDataSerializers.BOOLEAN);

	private int clawIn, hornIn, dashIn = DASH_EVERY / 2, barrageIn = BARRAGE_EVERY / 2, shiftIn, guardIn, riderIn;
	private boolean raging;
	/** The move under way: which, the tick it began, at whom, its aim; the dash's claw landed; the tick the dash reached them. */
	private int move = NONE, moveStart, dashHitAt = -1;
	private @Nullable LivingEntity moveTarget;
	private Vec3 moveAim = Vec3.ZERO;
	private boolean riderMove;

	public VarasuchusDragon(EntityType<? extends VarasuchusDragon> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	/** Drawn and animated by GeckoLib from our editable Blockbench model ({@link VarasuchusAnimations}). */
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		VarasuchusAnimations.register(controllers);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, NONE);
		builder.define(DATA_RAGING, false);
	}

	// ---- what sets it apart ---------------------------------------------------------------------------------------------

	@Override
	protected boolean flies() {
		return false;
	}

	@Override
	public boolean swims() {
		return true;
	}

	/** Quick in the water (Saint's Dragons gives it half again its pace there). */
	@Override
	public double swimSpeed() {
		return 0.5;
	}

	@Override
	protected boolean bossBar() {
		return false;
	}

	@Override
	protected boolean windDragon() {
		return false;
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.SALMON);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.TIDEBORN) ? BOND_FACTOR : 1.0F;
	}

	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
	}

	@Override
	protected double breathRange() {
		return 0.0;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, 3.35, index == 0 ? 0.5 : -0.55);
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return 5.0;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return 3.0;
	}

	@Override
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	private void begin(int move, @Nullable LivingEntity target, Vec3 aim) {
		this.move = move;
		this.moveStart = this.tickCount;
		this.moveTarget = target;
		this.dashHitAt = -1;
		Vec3 flat = aim.multiply(1.0, 0.0, 1.0);
		this.moveAim = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : flat.normalize();
		this.entityData.set(DATA_ACTION, move);
		this.triggerAnim("action", VarasuchusAnimations.name(move, standing()));
	}

	/**
	 * Raging (and wild, on land) it rises onto its hind legs and fights upright, its forelegs free for its claws (as the
	 * Saint's Dragons one does in its second phase).
	 */
	@Override
	protected boolean standing() {
		return this.entityData.get(DATA_RAGING) && !this.isTamed() && !grounded() && !this.isInWater();
	}

	private int pace(int ticks) {
		return this.raging ? Math.round(ticks * RAGE_PACE) : ticks;
	}

	// ---- ticking ----------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.riderIn--;
		this.guardIn--;
		this.shiftIn--;
		if (this.move != NONE) {
			moveTick(level, this.tickCount - this.moveStart);
		}
	}

	private void moveTick(ServerLevel level, int age) {
		switch (this.move) {
			case DASH_MOVE -> dashTick(level, age);
			case BARRAGE_MOVE -> {
				if (age > 0 && age % BARRAGE_GAP == 0 && age / BARRAGE_GAP <= BARRAGE_HITS) {
					for (LivingEntity e : ahead(level, 3.4 + this.getBbWidth() * 0.5, 2.0)) {
						e.setInvulnerableTime(0);
						e.hurtServer(level, this.damageSources().mobAttack(this), BARRAGE);
					}
					level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.1F + age % 3 * 0.1F);
				}
				if (this.moveTarget != null) {
					lookAt(this.moveTarget);
				}
			}
			default -> {
			}
		}
		if (age >= ACTION_TICKS[this.move]) {
			this.move = NONE;
			this.moveTarget = null;
			this.riderMove = false;
			this.entityData.set(DATA_ACTION, NONE);
		}
	}

	/** The dash: it runs on along its aim till it reaches someone (claws), then lashes its tail round. */
	private void dashTick(ServerLevel level, int age) {
		if (this.dashHitAt < 0 && age < DASH_TICKS) {
			if (!this.riderMove) {
				LivingEntity target = this.moveTarget;
				if (target != null && target.isAlive()) {
					Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
					if (to.lengthSqr() > 0.01) {
						this.moveAim = this.moveAim.lerp(to.normalize(), 0.3).normalize();
					}
				}
				Vec3 v = this.getDeltaMovement();
				this.setDeltaMovement(this.moveAim.x * DASH_SPEED, v.y, this.moveAim.z * DASH_SPEED);
				this.setYRot((float)(Mth.atan2(this.moveAim.z, this.moveAim.x) * Mth.RAD_TO_DEG) - 90.0F);
				this.yBodyRot = this.getYRot();
			}
			if (age % 4 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.2, this.getZ(), 3, 0.8, 0.05, 0.8, 0.02);
			}
			boolean hit = false;
			for (LivingEntity e : ahead(level, this.getBbWidth() * 0.5 + 2.5, 1.8)) {
				hit = true;
				if (e.hurtServer(level, this.damageSources().mobAttack(this), DASH_CLAW)) {
					e.push(this.moveAim.x * 0.6, 0.3, this.moveAim.z * 0.6);
					e.syncVelocity = true;
				}
			}
			if (hit || this.horizontalCollision) {
				this.dashHitAt = age;
				level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.6F, 0.7F);
				if (this.riderMove) {
					gore(level);
				}
			}
		} else if (this.dashHitAt < 0) {
			this.dashHitAt = age;
		} else if (age == this.dashHitAt + DASH_TAIL_AFTER) {
			// the tail swung round after it
			level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.5, 1.0, 4.5), this::spareable)) {
				e.setInvulnerableTime(0);
				if (e.hurtServer(level, this.damageSources().mobAttack(this), DASH_TAIL)) {
					Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
					away = away.lengthSqr() < 0.01 ? this.moveAim : away.normalize();
					e.push(away.x * 1.1, 0.35, away.z * 1.1);
					e.syncVelocity = true;
				}
			}
			this.move = DASH_MOVE;
			this.moveStart = this.tickCount - (ACTION_TICKS[DASH_MOVE] - 6);
		}
	}

	/** Whoever is in front of it within {@code reach} (and {@code side} blocks either side). */
	private java.util.List<LivingEntity> ahead(ServerLevel level, double reach, double side) {
		Vec3 from = this.position();
		return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach, 1.0, reach), e -> {
			if (!spareable(e)) {
				return false;
			}
			Vec3 to = e.position().subtract(from).multiply(1.0, 0.0, 1.0);
			double along = to.dot(this.moveAim);
			double across = Math.abs(to.x * -this.moveAim.z + to.z * this.moveAim.x);
			return along > 0.0 && along < reach + e.getBbWidth() * 0.5 && across < side + e.getBbWidth() * 0.5;
		});
	}

	/** (0.7.612) The plain bite and tail sweep every walker has, shown as moves of their own (docs/SaintsDragons-Animation-Spec.md). */
	@Override
	protected void bitten(LivingEntity target) {
		if (this.move == NONE) {
			begin(BITE_MOVE, target, target.position().subtract(this.position()));
		}
	}

	@Override
	protected void swept() {
		if (this.move == NONE) {
			begin(SWEEP_MOVE, null, Vec3.directionFromRotation(0.0F, this.getYRot()));
		}
	}

	// ---- fighting -------------------------------------------------------------------------------------------------------

	@Override
	protected void walkerFight(ServerLevel level, LivingEntity target) {
		if (this.move != NONE && this.move != GUARD_MOVE) {
			if (this.move != DASH_MOVE) {
				this.getNavigation().stop();
			}
			if (this.move != SHIFT_MOVE) {
				lookAt(target);
			}
			return;
		}
		groundFight(level, target, false);
		this.clawIn--;
		this.hornIn--;
		this.dashIn--;
		this.barrageIn--;
		if (this.move == GUARD_MOVE) {
			// (its tail held round as a guard: it only bites)
			return;
		}
		double distance = this.distanceTo(target);
		double halves = this.getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
		double reach = 3.2 + halves;
		Vec3 to = target.position().subtract(this.position());
		if (this.dashIn <= 0 && distance >= DASH_MIN && distance <= DASH_MAX && (this.onGround() || this.isInWater())) {
			this.dashIn = pace(DASH_EVERY);
			begin(DASH_MOVE, target, to);
			level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.8F);
		} else if (distance <= reach && this.barrageIn <= 0 && inFront(target)) {
			this.barrageIn = pace(BARRAGE_EVERY);
			begin(BARRAGE_MOVE, target, to);
		} else if (distance <= reach && this.hornIn <= 0 && inFront(target)) {
			this.hornIn = pace(HORN_EVERY);
			begin(HORN_MOVE, target, to);
			gore(level);
		} else if (distance <= reach && this.clawIn <= 0) {
			this.clawIn = pace(CLAW_EVERY);
			begin(CLAW_MOVE, target, to);
			if (target.hurtServer(level, this.damageSources().mobAttack(this), this.raging ? RAGE_CLAW : CLAW)) {
				level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.4F, 0.6F);
			}
		}
	}

	/** The horns driven up under whoever's before it: a toss. */
	private void gore(ServerLevel level) {
		for (LivingEntity e : ahead(level, 3.0 + this.getBbWidth() * 0.5, 1.6)) {
			e.setInvulnerableTime(0);
			if (e.hurtServer(level, this.damageSources().mobAttack(this), this.raging ? RAGE_HORN : HORN)) {
				e.push(this.moveAim.x * HORN_THROW, HORN_LIFT, this.moveAim.z * HORN_THROW);
				e.syncVelocity = true;
			}
		}
		level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.6F, 0.5F);
	}

	/** The phase shift: it slips past {@code foe} in a blur and comes out behind them, turned on them. */
	private void shift(ServerLevel level, LivingEntity foe) {
		Vec3 to = foe.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		Vec3 dir = to.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : to.normalize();
		Vec3 out = foe.position().add(dir.scale(SHIFT_PAST + this.getBbWidth() * 0.5));
		double y = floorBelow(level, net.minecraft.core.BlockPos.containing(out.x, foe.getY() + 2.0, out.z));
		if (Math.abs(y - foe.getY()) > 3.0 || !level.noCollision(this, this.getBoundingBox().move(out.x - this.getX(), y - this.getY(), out.z - this.getZ()))) {
			return;
		}
		this.shiftIn = pace(SHIFT_EVERY);
		Vec3 from = this.position();
		for (int i = 0; i <= 6; i++) {
			Vec3 p = from.lerp(new Vec3(out.x, y, out.z), i / 6.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1.2, p.z, 4, 0.6, 0.5, 0.6, 0.01);
		}
		level.playSound(null, this.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.4F, 0.6F);
		this.teleportTo(out.x, y, out.z);
		begin(SHIFT_MOVE, foe, dir.reverse());
		lookAt(foe);
		this.setYRot((float)(Mth.atan2(-dir.z, -dir.x) * Mth.RAD_TO_DEG) - 90.0F);
		this.yBodyRot = this.getYRot();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// slipping through, it can't be touched
		if (this.move == SHIFT_MOVE && this.tickCount - this.moveStart < SHIFT_TICKS && !source.isCreativePlayer()) {
			return false;
		}
		Entity striker = source.getEntity();
		// the tailguard: held, the blow is parried
		if (this.move == GUARD_MOVE && this.tickCount - this.moveStart >= GUARD_STARTUP && striker instanceof LivingEntity attacker
			&& attacker.distanceTo(this) < PARRY_REACH && !source.isCreativePlayer()) {
			parry(level, attacker);
			return false;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive() && this.move == NONE) {
			// (a flinch; a move under way plays on, as it did over the old model's flinch)
			this.triggerAnim("action", "hurt");
		}
		if (!hurt || !this.isAlive() || grounded()) {
			return hurt;
		}
		if (!this.raging && this.getHealth() < this.getMaxHealth() * RAGE_AT) {
			rage(level);
		} else if (striker instanceof LivingEntity attacker && attacker.distanceTo(this) < PARRY_REACH && this.move == NONE) {
			if (this.guardIn <= 0 && this.random.nextFloat() < GUARD_CHANCE) {
				this.guardIn = pace(GUARD_EVERY);
				begin(GUARD_MOVE, attacker, attacker.position().subtract(this.position()));
				level.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_TURTLE.value(), SoundSource.HOSTILE, 1.6F, 0.6F);
			} else if (!this.isVehicle() && this.shiftIn <= 0 && this.random.nextFloat() < SHIFT_CHANCE) {
				shift(level, attacker);
			}
		}
		return hurt;
	}

	/** A parried blow, as our guard parries: sparks and a ring, the striker hurt, slowed and thrown back. */
	private void parry(ServerLevel level, LivingEntity attacker) {
		Vec3 at = this.position().add(attacker.position().subtract(this.position()).normalize().scale(this.getBbWidth() * 0.6)).add(0.0, 1.2, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.5);
		level.sendParticles(Fx.CRACKLE, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.3);
		level.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.4F, 0.7F);
		level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.4F, 1.4F);
		attacker.hurtServer(level, this.damageSources().mobAttack(this), PARRY);
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 1, false, false));
		if (attacker instanceof Player player) {
			player.resetAttackStrengthTicker();
		}
		Vec3 back = attacker.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		back = back.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : back.normalize();
		attacker.push(back.x * PARRY_THROW, 0.35, back.z * PARRY_THROW);
		attacker.syncVelocity = true;
		begin(PARRY_MOVE, attacker, back);
	}

	/** Worn to half its health: it roars, and from now on it bites harder and its moves come quicker. */
	private void rage(ServerLevel level) {
		this.raging = true;
		this.entityData.set(DATA_RAGING, true);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RAGE_BITE);
		level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.45F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 2.6, this.getZ(), 6, 1.2, 0.4, 1.2, 0.0);
		begin(RAGE_MOVE, null, Vec3.directionFromRotation(0.0F, this.getYRot()));
	}

	/** Ridden, R charges where the rider looks (the rider steers it; it gores at the end). */
	@Override
	public void riderBreath(float yaw, float pitch) {
		if (this.riderIn > 0 || this.move != NONE || exhausted()) {
			return;
		}
		this.riderIn = RIDER_DASH_EVERY;
		this.riderMove = true;
		begin(DASH_MOVE, null, Vec3.directionFromRotation(0.0F, yaw));
		// (the rider steers it: the charge is a burst of pace)
		this.addEffect(new MobEffectInstance(MobEffects.SPEED, DASH_TICKS, 3, false, false));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Raging", this.raging);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.raging = input.getBooleanOr("Raging", false);
		this.entityData.set(DATA_RAGING, this.raging);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel) {
			this.triggerAnim("action", "death");
		}
		super.die(source);
	}

	/** How far through {@code action} its model is after {@code age} ticks (0 to 1). */
	public static float actionShare(int action, float age) {
		int length = action <= 0 || action >= ACTION_TICKS.length ? 1 : ACTION_TICKS[action];
		return Mth.clamp(age / length, 0.0F, 1.0F);
	}
}
