package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Fx;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Heritage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.522) Stegonaut, the first of the lesser dragons ({@code docs/SaintsDragons-Plan.md}): its moves and numbers are
 * ported from Saint's Dragons by Leon Saint (MIT licence; none of its art, models or sounds are used: the model is our
 * own, {@code kingdoms/art/stegonaut.bbmodel}, painted by {@code tools/gen_stegonaut.py} and animated by GeckoLib,
 * {@link StegonautAnimations}). A walker ({@link WindDragon#flies}):
 * a tall, plated dragon on long pillar legs with a long neck held high, of the jungles and plains. It never flies,
 * grazes about its home in ones and twos and only fights whoever hurts it.
 * <p>
 * Its moves on its feet, besides the bite and the tail sweep every dragon has:
 * <ul>
 * <li><b>Chin slam:</b> it drives its heavy jaw up under whoever's in front of it ({@link #CHIN}, a throw up).</li>
 * <li><b>Ground slam:</b> it rears and stamps both forefeet down: a ring of damage round it ({@link #SLAM}), then a line
 * of earth spikes bursting from the ground toward its foe, one after the other ({@link #PILLAR} each).</li>
 * <li><b>Ground eating:</b> at range it bites up a mouthful of earth, chews, and spits it in an arc ({@link #SPIT}).</li>
 * <li><b>Roar:</b> worn down, it roars and hardens its plates: Resistance and Absorption for itself, its riders and, tamed,
 * its owner near it.</li>
 * </ul>
 * Tamed and ridden it's steered like a horse; R stamps a ground slam with its line of spikes where the rider looks. Its
 * favourite is raw beef; Stoneborn win it over twice as fast.
 */
public class StegonautDragon extends WindDragon implements GeoEntity {
	static final double HEALTH = 100.0, ARMOR = 15.0, TOUGHNESS = 4.0, SPEED = 0.2, BITE = 5.0;
	/** The chin slam: damage, reach past the bite, pace, the throw up. */
	static final float CHIN = 8.0F;
	static final double CHIN_REACH = 1.0, CHIN_LIFT = 0.75;
	static final int CHIN_EVERY = 50;
	/** The ground slam: the ring's damage, radius and throw; its pace; how near its foe must be; its wind-up (ticks). */
	static final float SLAM = 20.0F;
	static final double SLAM_RADIUS = 5.0, SLAM_THROW = 1.35;
	static final int SLAM_EVERY = 160, SLAM_WINDUP = 14;
	static final double SLAM_NEAR = 8.0;
	/** The spikes after it: how many, how far apart (blocks), a tick gap between them, each one's damage, reach and throw up. */
	static final int PILLARS = 7, PILLAR_GAP = 2;
	static final double PILLAR_STEP = 1.6, PILLAR_RADIUS = 1.3, PILLAR_LIFT = 0.9;
	static final float PILLAR = 10.0F;
	/** Ground eating: its range, pace, the chew before it spits (ticks), the clod's speed, damage, reach and life (ticks). */
	static final double SPIT_MIN = 7.0, SPIT_MAX = 24.0, SPIT_SPEED = 1.15, SPIT_RADIUS = 1.1, SPIT_GRAVITY = 0.035;
	static final int SPIT_EVERY = 120, SPIT_CHEW = 24, SPIT_LIFE = 60;
	static final float SPIT = 10.0F;
	/** The roar: it roars below this share of its health; how long the hardening lasts, how far it reaches, its pace. */
	static final float ROAR_AT = 0.6F;
	static final int ROAR_TICKS = 200, ROAR_EVERY = 600;
	static final double ROAR_RANGE = 8.0;
	/** Ridden: the slam's pace (ticks). */
	static final int RIDER_SLAM_EVERY = 100;

	/** Its moves ({@link #action}); each is played by GeckoLib's {@code action} controller as it starts. */
	public static final int NONE = 0, CHIN_SLAM = 1, GROUND_SLAM = 2, CHEW = 3, ROAR = 4, BITE_MOVE = 5, GRAZE = 6;
	/** How long each lasts (ticks; its animation is as long). (0.7.531) The roar is held longer, a show of its plates, and the bite and
	 * the grazing are moves of their own. */
	static final int[] ACTION_TICKS = {0, 16, SLAM_WINDUP + 10, SPIT_CHEW + 8, 60, 20, 30};

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(StegonautDragon.class, EntityDataSerializers.INT);

	private int chinIn = CHIN_EVERY, slamIn = SLAM_EVERY / 2, spitIn = SPIT_EVERY / 2, roarIn, riderSlamIn, actionLeft;
	/** A slam being wound up: the tick it lands, and the way its spikes run. */
	private int slamAt = -1;
	private Vec3 slamAim = Vec3.ZERO;
	private int spitAt = -1;
	private @org.jspecify.annotations.Nullable LivingEntity spitTarget;
	/** The spikes on their way, and the clods in the air. */
	private final List<Pillar> pillars = new ArrayList<>();
	private final List<Clod> clods = new ArrayList<>();
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private record Pillar(Vec3 at, int when, boolean ridden) {
	}

	private static final class Clod {
		Vec3 at, v;
		int age;
		BlockState state;

		Clod(Vec3 at, Vec3 v, BlockState state) {
			this.at = at;
			this.v = v;
			this.state = state;
		}
	}

	public StegonautDragon(EntityType<? extends StegonautDragon> type, Level level) {
		super(type, level);
		this.xpReward = 25;
	}

	/** Drawn and animated by GeckoLib from our own Blockbench model ({@link StegonautAnimations}). */
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		StegonautAnimations.register(controllers);
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
	}

	// ---- what sets it apart ---------------------------------------------------------------------------------------------

	@Override
	protected boolean flies() {
		return false;
	}

	@Override
	protected boolean aggressive() {
		return false;
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
		return stack.is(Items.BEEF);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.STONEBORN) ? BOND_FACTOR : 1.0F;
	}

	/** It has no breath: its R is the ground slam ({@link #riderBreath}). */
	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
	}

	@Override
	protected double breathRange() {
		return 0.0;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, 3.0, index == 0 ? 0.2 : -0.75);
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return 4.4;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return 3.0;
	}

	@Override
	public int action() {
		return this.entityData.get(DATA_ACTION);
	}

	private void act(int action) {
		this.entityData.set(DATA_ACTION, action);
		this.actionLeft = ACTION_TICKS[action];
		this.triggerAnim("action", StegonautAnimations.name(action));
	}

	/** It grazes: a wild one stays about where it was born, as cattle do. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---- ticking ----------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.actionLeft > 0 && --this.actionLeft == 0) {
			this.entityData.set(DATA_ACTION, NONE);
		}
		this.roarIn--;
		this.riderSlamIn--;
		if (this.slamAt >= 0 && this.tickCount >= this.slamAt) {
			this.slamAt = -1;
			slam(level, this.slamAim, this.isVehicle());
		}
		if (this.spitAt >= 0 && this.tickCount >= this.spitAt) {
			this.spitAt = -1;
			spit(level, this.spitTarget);
			this.spitTarget = null;
		}
		pillars(level);
		clods(level);
	}

	@Override
	protected void perchedTick(ServerLevel level) {
		// grazing: now and then it noses the ground
		if (this.getNavigation().isDone() && this.random.nextInt(200) == 0) {
			BlockState under = level.getBlockState(this.blockPosition().below());
			Vec3 mouth = this.mouth();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), mouth.x, this.getY() + 0.1, mouth.z, 6, 0.3, 0.05, 0.3, 0.05);
			this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 0.5F);
			if (action() == NONE) {
				act(GRAZE);
			}
		}
	}

	/** (0.7.531) The bite: the neck draws back, jaws wide, and snaps in. */
	@Override
	protected void bitten(LivingEntity target) {
		if (action() == NONE) {
			act(BITE_MOVE);
		}
	}

	/** Its tail sweep: the body and tail swung round. */
	@Override
	protected void swept() {
		this.triggerAnim("action", "sweep");
	}

	// ---- fighting -------------------------------------------------------------------------------------------------------

	@Override
	protected void walkerFight(ServerLevel level, LivingEntity target) {
		if (this.slamAt >= 0 || this.spitAt >= 0) {
			// (winding up: it holds still)
			this.getNavigation().stop();
			lookAt(target);
			return;
		}
		groundFight(level, target, false);
		double distance = this.distanceTo(target);
		double reach = 3.2 + this.getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
		this.chinIn--;
		this.slamIn--;
		this.spitIn--;
		if (distance <= reach + CHIN_REACH && this.chinIn <= 0 && inFront(target)) {
			this.chinIn = CHIN_EVERY;
			chin(level, target);
		} else if (distance <= SLAM_NEAR && this.slamIn <= 0) {
			this.slamIn = SLAM_EVERY;
			windSlam(target.position().subtract(this.position()));
		} else if (distance >= SPIT_MIN && distance <= SPIT_MAX && this.spitIn <= 0) {
			this.spitIn = SPIT_EVERY;
			this.spitAt = this.tickCount + SPIT_CHEW;
			this.spitTarget = target;
			act(CHEW);
			BlockState under = level.getBlockState(this.blockPosition().below());
			Vec3 mouth = this.mouth();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), mouth.x, this.getY() + 0.2, mouth.z, 20, 0.5, 0.1, 0.5, 0.1);
			level.playSound(null, this.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
		}
	}

	private void chin(ServerLevel level, LivingEntity target) {
		act(CHIN_SLAM);
		if (target.hurtServer(level, this.damageSources().mobAttack(this), CHIN)) {
			target.push(0.0, CHIN_LIFT, 0.0);
			target.syncVelocity = true;
		}
		level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.6F, 0.45F);
	}

	private void windSlam(Vec3 toward) {
		Vec3 flat = toward.multiply(1.0, 0.0, 1.0);
		this.slamAim = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : flat.normalize();
		this.slamAt = this.tickCount + SLAM_WINDUP;
		act(GROUND_SLAM);
		this.playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 0.6F);
	}

	/** Both forefeet down: the ring, then the spikes along {@code aim}. */
	private void slam(ServerLevel level, Vec3 aim, boolean ridden) {
		Vec3 centre = this.position().add(Vec3.directionFromRotation(0.0F, this.getYRot()).scale(1.5));
		level.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F, 0.55F);
		BlockState under = level.getBlockState(BlockPos.containing(centre).below());
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2.0 / 24.0;
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), centre.x + Math.cos(a) * SLAM_RADIUS * 0.6, this.getY() + 0.1,
				centre.z + Math.sin(a) * SLAM_RADIUS * 0.6, 4, 0.3, 0.1, 0.3, 0.15);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(SLAM_RADIUS, 2.0, SLAM_RADIUS),
			e -> spareable(e) && e.position().distanceToSqr(centre) < SLAM_RADIUS * SLAM_RADIUS)) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), SLAM)) {
				Vec3 away = e.position().subtract(centre).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 0.01 ? aim : away.normalize();
				e.push(away.x * SLAM_THROW, 0.5, away.z * SLAM_THROW);
				e.syncVelocity = true;
			}
		}
		for (int i = 1; i <= PILLARS; i++) {
			this.pillars.add(new Pillar(centre.add(aim.scale(i * PILLAR_STEP)), this.tickCount + i * PILLAR_GAP, ridden));
		}
	}

	private void pillars(ServerLevel level) {
		for (Iterator<Pillar> it = this.pillars.iterator(); it.hasNext();) {
			Pillar p = it.next();
			if (this.tickCount < p.when()) {
				continue;
			}
			it.remove();
			BlockPos top = BlockPos.containing(p.at().x, floorBelow(level, BlockPos.containing(p.at().x, this.getY() + 3.0, p.at().z)) - 1.0, p.at().z);
			BlockState state = level.getBlockState(top);
			if (state.isAir()) {
				continue;
			}
			Vec3 at = new Vec3(p.at().x, top.getY() + 1.0, p.at().z);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + 0.6, at.z, 18, 0.25, 0.7, 0.25, 0.2);
			level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1.2, at.z, 2, 0.1, 0.3, 0.1, 0.02);
			level.playSound(null, top, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(PILLAR_RADIUS, 1.8, PILLAR_RADIUS), this::spareable)) {
				if (e.hurtServer(level, this.damageSources().mobAttack(this), PILLAR)) {
					e.push(0.0, PILLAR_LIFT, 0.0);
					e.syncVelocity = true;
				}
			}
		}
	}

	/** A mouthful of earth spat at {@code target}, aimed to lead them a little and arc down onto them. */
	private void spit(ServerLevel level, @org.jspecify.annotations.Nullable LivingEntity target) {
		if (target == null || !target.isAlive()) {
			return;
		}
		Vec3 mouth = this.mouth();
		Vec3 aimAt = target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(8.0));
		Vec3 to = aimAt.subtract(mouth);
		double flat = to.horizontalDistance();
		double ticks = Math.max(4.0, flat / SPIT_SPEED);
		Vec3 v = new Vec3(to.x / ticks, to.y / ticks + SPIT_GRAVITY * ticks * 0.5, to.z / ticks);
		BlockState state = level.getBlockState(this.blockPosition().below());
		if (state.isAir()) {
			state = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
		}
		this.clods.add(new Clod(mouth, v, state));
		level.playSound(null, this.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 2.0F, 0.4F);
	}

	private void clods(ServerLevel level) {
		for (Iterator<Clod> it = this.clods.iterator(); it.hasNext();) {
			Clod c = it.next();
			Vec3 next = c.at.add(c.v);
			c.v = c.v.add(0.0, -SPIT_GRAVITY, 0.0);
			c.age++;
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, c.state), next.x, next.y, next.z, 4, 0.15, 0.15, 0.15, 0.0);
			LivingEntity hit = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c.at, next).inflate(SPIT_RADIUS), this::spareable)) {
				hit = e;
				break;
			}
			boolean solid = !level.getBlockState(BlockPos.containing(next)).getCollisionShape(level, BlockPos.containing(next)).isEmpty();
			if (hit != null || solid || c.age > SPIT_LIFE) {
				it.remove();
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, c.state), next.x, next.y, next.z, 24, 0.4, 0.4, 0.4, 0.15);
				level.playSound(null, BlockPos.containing(next), SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
				if (hit != null && hit.hurtServer(level, this.damageSources().mobAttack(this), SPIT)) {
					Vec3 push = c.v.multiply(1.0, 0.0, 1.0);
					if (push.lengthSqr() > 0.0001) {
						push = push.normalize().scale(0.6);
						hit.push(push.x, 0.3, push.z);
						hit.syncVelocity = true;
					}
				}
				continue;
			}
			c.at = next;
		}
	}

	/** It roars and hardens its plates: Resistance and Absorption for it, its riders and its owner near it. */
	private void roar(ServerLevel level) {
		this.roarIn = ROAR_EVERY;
		act(ROAR);
		level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.4F);
		level.sendParticles(Fx.EMBER, this.getX(), this.getY() + 1.6, this.getZ(), 30, 1.2, 0.6, 1.8, 0.05);
		List<LivingEntity> hardened = new ArrayList<>();
		hardened.add(this);
		for (Entity passenger : this.getPassengers()) {
			if (passenger instanceof LivingEntity l) {
				hardened.add(l);
			}
		}
		if (this.isTamed() && this.getOwner() instanceof LivingEntity owner && owner.distanceToSqr(this) < ROAR_RANGE * ROAR_RANGE && !hardened.contains(owner)) {
			hardened.add(owner);
		}
		for (LivingEntity e : hardened) {
			e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ROAR_TICKS, 0));
			e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ROAR_TICKS, 0));
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive()) {
			this.triggerAnim("hurt", "hurt");
		}
		if (hurt && this.isAlive() && !grounded() && this.roarIn <= 0
			&& (this.getHealth() < this.getMaxHealth() * ROAR_AT || this.isTamed() && this.isVehicle())) {
			roar(level);
		}
		return hurt;
	}

	/** Ridden, R stamps a ground slam whose spikes run where the rider looks. */
	@Override
	public void riderBreath(float yaw, float pitch) {
		if (this.riderSlamIn > 0 || this.slamAt >= 0 || exhausted() || !this.onGround()) {
			return;
		}
		this.riderSlamIn = RIDER_SLAM_EVERY;
		windSlam(Vec3.directionFromRotation(0.0F, yaw));
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel) {
			this.triggerAnim("action", "death");
		}
		super.die(source);
	}
}
