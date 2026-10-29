package com.jaymospowers.aether.guardian;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Powers 0.17.148) The Visage of War: the infected guardian tree's guardian (docs/aether.md listed "a guardian tree boss"
 * as not done), a golden winged knight with a lance, drawn as eanncrod's model (Blockbench Workshop, CC BY 4.0). It
 * hangs asleep in the hollow under the tree's vault until someone climbs within {@link #WAKE_RANGE}, then flies at them
 * in the trunk: it circles above, and in turn thrusts its lance, sweeps it round in a wide cleave, rises and comes down
 * in a cut, or shoves off anyone pressing it. Below {@link #RAGE_FRACTION} of its health it charges too, and every
 * move comes quicker. It never leaves its hollow; when everyone has gone it floats home and heals. On death it falls,
 * bursting light, and gives up a great deal of experience. Fire doesn't touch it; it can't drown or fall.
 * <p>
 * Its moves, by number for its client, each with its animation, length, and when its blow lands (seconds in).
 */
public class VisageOfWar extends Monster {
	public static final double HEALTH = 300.0, ARMOR = 10.0, FOLLOW = 48.0;
	/** Someone this close (blocks) inside its hollow wakes it. */
	public static final double WAKE_RANGE = 20.0;
	/** It circles this far from its foe and this high over them (blocks), and flies this fast (blocks per tick). */
	public static final double CIRCLE = 6.0, CIRCLE_UP = 3.0, FLY_SPEED = 0.22, CHARGE_SPEED = 0.9;
	/** Its blows: thrust (one foe in front), cleave (all round), cut (where it comes down), shove (close in), charge (passing). */
	public static final float THRUST_DAMAGE = 10.0F, CLEAVE_DAMAGE = 9.0F, CUT_DAMAGE = 12.0F, SHOVE_DAMAGE = 4.0F, CHARGE_DAMAGE = 8.0F;
	public static final double THRUST_REACH = 4.5, CLEAVE_REACH = 5.0, CUT_REACH = 3.5, SHOVE_REACH = 2.5, CHARGE_REACH = 2.0;
	/** Ticks between its moves (fewer when enraged), and the share of health it rages below. */
	public static final int GAP_MIN = 30, GAP_MAX = 60, RAGE_GAP_MIN = 15, RAGE_GAP_MAX = 35;
	public static final float RAGE_FRACTION = 0.5F;
	/** How long it falls dying (ticks); how long it waits with nobody in its hollow before going home (ticks). */
	public static final int DEATH_TICKS = 80, QUIET_TICKS = 200;
	public static final int XP = 300;

	/** Its moves: what the client plays, how long (ticks), and when its blow lands (ticks in, -1 for a blow on contact). */
	public enum Move {
		ASLEEP("promo", 0, -1), HOVER("hover", 0, -1), PURSUE("pursue", 0, -1), THRUST("thrust", 29, 11), WIDE_CLEAVE("wide_cleave", 54, 26),
		DESCENDING_CUT("descending_cut", 38, 22), SHOVE("shove", 16, 6), CHARGE("charge", 32, -1), RECOVER("recover", 18, -1);

		public final String anim;
		public final int ticks, hitAt;

		Move(String anim, int ticks, int hitAt) {
			this.anim = anim;
			this.ticks = ticks;
			this.hitAt = hitAt;
		}
	}

	private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(VisageOfWar.class, EntityDataSerializers.INT);

	private @Nullable BoundingBox hollow;
	private @Nullable Vec3 home;
	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.YELLOW,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private int moveTicks, gap = GAP_MAX, quiet;
	private @Nullable Vec3 diveTo;
	private final List<Entity> chargeHit = new ArrayList<>();
	private float circleAngle;
	/** (Client) the tick its current move began. */
	private int moveStartedAt;

	public VisageOfWar(EntityType<? extends VisageOfWar> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPersistenceRequired();
		this.xpReward = 0;
		this.bar.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.ATTACK_DAMAGE, THRUST_DAMAGE);
	}

	/** Sets the hollow it guards and the spot it hangs in; the guardian tree calls this when it places it. */
	public void setHollow(BoundingBox hollow, Vec3 home) {
		this.hollow = hollow;
		this.home = home;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(MOVE, Move.ASLEEP.ordinal());
	}

	public Move move() {
		Move[] all = Move.values();
		return all[Math.floorMod(this.entityData.get(MOVE), all.length)];
	}

	/** (Client) how long its current move has run (ticks). */
	public int moveAge() {
		return this.tickCount - this.moveStartedAt;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (MOVE.equals(key)) {
			this.moveStartedAt = this.tickCount;
		}
	}

	private void setMove(Move move) {
		this.entityData.set(MOVE, move.ordinal());
		this.moveTicks = 0;
		this.chargeHit.clear();
	}

	public boolean awake() {
		return this.move() != Move.ASLEEP;
	}

	private boolean enraged() {
		return this.getHealth() < this.getMaxHealth() * RAGE_FRACTION;
	}

	// ---- flying ----

	@Override
	public void travel(Vec3 input) {
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.82));
	}

	/** Steers towards {@code to} at up to {@code speed} blocks a tick. */
	private void flyTo(Vec3 to, double speed) {
		Vec3 d = to.subtract(this.position());
		double len = d.length();
		if (len < 0.05) {
			return;
		}
		Vec3 want = d.scale(Math.min(speed, len * 0.3) / len);
		this.setDeltaMovement(this.getDeltaMovement().add(want.subtract(this.getDeltaMovement()).scale(0.25)));
	}

	private void face(Vec3 at) {
		Vec3 d = at.subtract(this.position());
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	// ---- thinking ----

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null) {
			this.home = this.position();
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		List<ServerPlayer> inside = this.playersInside(level);
		if (!this.awake()) {
			this.flyTo(this.home, 0.05);
			for (ServerPlayer player : inside) {
				if (player.distanceToSqr(this) < WAKE_RANGE * WAKE_RANGE) {
					this.wake(level);
					break;
				}
			}
			return;
		}
		if (inside.isEmpty()) {
			if (++this.quiet > QUIET_TICKS) {
				this.sleep();
			}
			this.flyTo(this.home, FLY_SPEED);
			return;
		}
		this.quiet = 0;
		this.bar.setVisible(true);
		LivingEntity target = this.getTarget();
		if (!(target instanceof ServerPlayer) || !target.isAlive() || !inside.contains(target)) {
			target = nearest(inside, this.position());
			this.setTarget(target);
		}
		this.fight(level, target);
		this.keepInside();
	}

	private void fight(ServerLevel level, LivingEntity target) {
		Move move = this.move();
		this.moveTicks++;
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		switch (move) {
			case HOVER, PURSUE, ASLEEP -> {
				// circle above, coming round the foe
				this.circleAngle += this.enraged() ? 0.05F : 0.035F;
				Vec3 spot = target.position().add(Mth.cos(this.circleAngle) * CIRCLE, CIRCLE_UP, Mth.sin(this.circleAngle) * CIRCLE);
				this.flyTo(spot, FLY_SPEED);
				this.face(at);
				Move circling = this.getDeltaMovement().lengthSqr() > 0.02 ? Move.PURSUE : Move.HOVER;
				if (circling != move && this.moveTicks > 10) {
					this.entityData.set(MOVE, circling.ordinal());
				}
				if (--this.gap <= 0) {
					this.choose(target);
				}
			}
			case THRUST -> {
				this.face(at);
				this.flyTo(at.subtract(this.getLookAngle().scale(2.5)), FLY_SPEED * 1.5);
				if (this.moveTicks == move.hitAt) {
					this.setDeltaMovement(this.getLookAngle().scale(0.8));
					this.strike(level, THRUST_DAMAGE, THRUST_REACH, true);
				}
			}
			case WIDE_CLEAVE -> {
				this.face(at);
				this.flyTo(target.position().add(0.0, 1.0, 0.0), FLY_SPEED * 0.6);
				if (this.moveTicks == move.hitAt) {
					this.strike(level, CLEAVE_DAMAGE, CLEAVE_REACH, false);
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY() + 1.2, this.getZ(), 8, 2.0, 0.3, 2.0, 0.0);
				}
			}
			case DESCENDING_CUT -> {
				this.face(at);
				if (this.moveTicks < move.hitAt - 8) {
					// up first, over them
					this.flyTo(target.position().add(0.0, 6.0, 0.0), FLY_SPEED * 1.4);
					this.diveTo = target.position();
				} else if (this.moveTicks <= move.hitAt && this.diveTo != null) {
					this.flyTo(this.diveTo, CHARGE_SPEED);
				}
				if (this.moveTicks == move.hitAt) {
					this.strike(level, CUT_DAMAGE, CUT_REACH, false);
					level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.5, 0.2, 0.5, 0.0);
					level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.5F, 0.8F);
				}
			}
			case SHOVE -> {
				this.face(at);
				if (this.moveTicks == move.hitAt) {
					this.strike(level, SHOVE_DAMAGE, SHOVE_REACH, true);
				}
			}
			case CHARGE -> {
				if (this.moveTicks == 1) {
					this.face(at);
					this.diveTo = at.add(at.subtract(this.position()).normalize().scale(4.0));
					level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 1.5F, 0.9F);
				}
				if (this.diveTo != null) {
					this.flyTo(this.diveTo, CHARGE_SPEED);
				}
				for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(CHARGE_REACH), e -> e != this && e.isAlive())) {
					if (!this.chargeHit.contains(hit) && (hit instanceof ServerPlayer || hit == target)) {
						this.chargeHit.add(hit);
						DamageSource source = this.damageSources().mobAttack(this);
						hit.hurtServer(level, source, CHARGE_DAMAGE);
						hit.knockback(1.2, this.getX() - hit.getX(), this.getZ() - hit.getZ(), source, CHARGE_DAMAGE);
					}
				}
			}
			case RECOVER -> this.setDeltaMovement(this.getDeltaMovement().scale(0.7));
		}
		if (move.ticks > 0 && this.moveTicks >= move.ticks) {
			boolean enraged = this.enraged();
			this.setMove(move == Move.CHARGE ? Move.RECOVER : Move.HOVER);
			this.gap = enraged ? RAGE_GAP_MIN + this.random.nextInt(RAGE_GAP_MAX - RAGE_GAP_MIN + 1)
				: GAP_MIN + this.random.nextInt(GAP_MAX - GAP_MIN + 1);
		}
	}

	/** Its next move, by how far off its foe is. */
	private void choose(LivingEntity target) {
		double d = this.distanceTo(target);
		Move next;
		if (d < SHOVE_REACH) {
			next = Move.SHOVE;
		} else if (this.enraged() && this.random.nextFloat() < 0.35F) {
			next = Move.CHARGE;
		} else {
			float r = this.random.nextFloat();
			next = r < 0.4F ? Move.THRUST : r < 0.7F ? Move.DESCENDING_CUT : Move.WIDE_CLEAVE;
		}
		this.setMove(next);
		if (next == Move.WIDE_CLEAVE || next == Move.DESCENDING_CUT) {
			this.playSound(SoundEvents.TRIDENT_THROW.value(), 1.5F, 0.6F);
		}
	}

	/** Its blow lands: on the foes within {@code reach}, only those in front if {@code front}. */
	private void strike(ServerLevel level, float damage, double reach, boolean front) {
		Vec3 look = this.getLookAngle();
		AABB box = this.getBoundingBox().inflate(reach, reach * 0.6, reach);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !(e instanceof VisageOfWar))) {
			Vec3 to = e.position().subtract(this.position());
			if (to.horizontalDistance() > reach + e.getBbWidth() / 2.0) {
				continue;
			}
			if (front && to.horizontalDistance() > 0.5 && to.normalize().dot(look) < 0.3) {
				continue;
			}
			if (e instanceof ServerPlayer || e == this.getTarget()) {
				DamageSource source = this.damageSources().mobAttack(this);
				e.hurtServer(level, source, damage);
				e.knockback(0.8, this.getX() - e.getX(), this.getZ() - e.getZ(), source, damage);
			}
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, 0.7F);
	}

	/** It never leaves its hollow. */
	private void keepInside() {
		BoundingBox h = this.hollow;
		if (h == null) {
			return;
		}
		double x = Mth.clamp(this.getX(), h.minX() + 1.0, h.maxX());
		double y = Mth.clamp(this.getY(), h.minY(), h.maxY() - this.getBbHeight());
		double z = Mth.clamp(this.getZ(), h.minZ() + 1.0, h.maxZ());
		if (x != this.getX() || y != this.getY() || z != this.getZ()) {
			this.setPos(x, y, z);
		}
	}

	private List<ServerPlayer> playersInside(ServerLevel level) {
		List<ServerPlayer> inside = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
				continue;
			}
			boolean there = this.hollow != null ? this.hollow.inflatedBy(2).isInside(player.blockPosition()) : player.distanceToSqr(this) < FOLLOW * FOLLOW;
			if (there) {
				inside.add(player);
			}
		}
		return inside;
	}

	private static @Nullable ServerPlayer nearest(List<ServerPlayer> players, Vec3 from) {
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			double d = p.position().distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	private void wake(ServerLevel level) {
		this.setMove(Move.HOVER);
		this.gap = GAP_MAX;
		this.quiet = 0;
		this.bar.setVisible(true);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.8F);
		level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.5, this.getZ(), 40, 1.0, 1.5, 1.0, 0.05);
		for (ServerPlayer player : this.playersInside(level)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.visage_of_war.wakes"));
		}
	}

	/** Everyone has left or fallen: home, healed, asleep. */
	private void sleep() {
		this.setMove(Move.ASLEEP);
		this.setHealth(this.getMaxHealth());
		this.setTarget(null);
		this.bar.setVisible(false);
		this.bar.removeAllPlayers();
		if (this.home != null) {
			this.setPos(this.home.x, this.home.y, this.home.z);
		}
	}

	// ---- being hurt ----

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && source.getEntity() instanceof ServerPlayer && !this.awake()) {
			this.wake(level);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---- dying ----

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bar.setProgress(0.0F);
		this.setDeltaMovement(0.0, -0.04, 0.0);
		this.move(MoverType.SELF, this.getDeltaMovement());
		if (this.deathTime % 4 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.5, this.getZ(), 12, 0.8, 1.2, 0.8, 0.08);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5F, 0.5F + this.random.nextFloat() * 0.5F);
		}
		if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE08A), this.getX(), this.getY() + 1.5, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.5F);
			ExperienceOrb.award(level, this.position(), XP);
			this.bar.removeAllPlayers();
			this.remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		this.bar.removeAllPlayers();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.awake() ? null : SoundEvents.AMETHYST_BLOCK_RESONATE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ARMOR_EQUIP_GOLD.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BELL_RESONATE;
	}

	// ---- saving ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("awake", this.awake());
		if (this.hollow != null) {
			BoundingBox h = this.hollow;
			output.putIntArray("hollow", new int[] {h.minX(), h.minY(), h.minZ(), h.maxX(), h.maxY(), h.maxZ()});
		}
		if (this.home != null) {
			output.putIntArray("home", new int[] {Mth.floor(this.home.x * 16.0), Mth.floor(this.home.y * 16.0), Mth.floor(this.home.z * 16.0)});
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getIntArray("hollow").ifPresent(h -> {
			if (h.length == 6) {
				this.hollow = new BoundingBox(h[0], h[1], h[2], h[3], h[4], h[5]);
			}
		});
		input.getIntArray("home").ifPresent(h -> {
			if (h.length == 3) {
				this.home = new Vec3(h[0] / 16.0, h[1] / 16.0, h[2] / 16.0);
			}
		});
		this.entityData.set(MOVE, (input.getBooleanOr("awake", false) ? Move.HOVER : Move.ASLEEP).ordinal());
		this.bar.setVisible(this.awake());
		if (this.hasCustomName()) {
			this.bar.setName(this.getDisplayName());
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bar.setName(this.getDisplayName());
	}
}
