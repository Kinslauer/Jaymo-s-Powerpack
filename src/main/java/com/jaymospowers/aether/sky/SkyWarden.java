package com.jaymospowers.aether.sky;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.96) Andy (seven-mods plan, patch 10): the sky warden, a great holystone sentry standing guard on a floating arena
 * high over the islands ({@link SkyArenaFeature}). It stands still until someone comes within {@link #WAKE_RANGE}.
 * <p>
 * <b>First phase:</b> it walks at its foe and clubs them, calls down a bolt on a marked spot (step off it), and now and
 * then breathes out a gust that throws everyone back. <b>Second phase</b> (below half its health): the arena's gravity
 * flips. Everyone in it falls up onto the ceiling and fights from there while it flies between floor and ceiling, its
 * bolts and gusts quicker. When it dies, or anyone leaves the arena, gravity rights itself and they get a slow fall to
 * come down on. It never leaves its arena; when everyone is gone it walks home and heals.
 */
public class SkyWarden extends Monster {
	public static final double HEALTH = 260.0, ARMOR = 12.0, DAMAGE = 11.0, SPEED = 0.26, SCALE = 2.5;
	public static final double WAKE_RANGE = 14.0, REACH = 4.2;
	/** The arena: this far round its home and this high over the floor (blocks); {@link SkyArenaFeature} builds to these. */
	public static final int ARENA_RADIUS = 12, ARENA_HEIGHT = 14;
	/** The flipped arena's pull: a modifier on each player's gravity (base 0.08), so they fall up at about the usual speed. */
	public static final double FLIP_GRAVITY = -0.16;
	public static final int SLOW_FALL_TICKS = 200;
	/** Ticks between its moves (fewer flipped); how long a marked bolt waits; how long it waits alone before going home. */
	public static final int GAP_MIN = 50, GAP_MAX = 90, FLIP_GAP_MIN = 30, FLIP_GAP_MAX = 55, BOLT_WAIT = 24, QUIET_TICKS = 200;
	public static final float BOLT_DAMAGE = 10.0F, GUST_DAMAGE = 4.0F;
	public static final double BOLT_RADIUS = 2.5, GUST_RADIUS = 9.0;
	public static final int XP = 250;
	static final Identifier FLIP_ID = JaymosPowers.id("sky_warden_flip");

	private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(SkyWarden.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FLIPPED = SynchedEntityData.defineId(SkyWarden.class, EntityDataSerializers.BOOLEAN);

	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.BLUE,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private @Nullable Vec3 home;
	private final Set<UUID> pulled = new HashSet<>();
	private final List<Vec3> marks = new ArrayList<>();
	private int gap = GAP_MAX, boltTicks = -1, quiet, swingCooldown;

	public SkyWarden(EntityType<? extends SkyWarden> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 0;
		this.bar.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.SCALE, SCALE)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** Its post; the arena feature calls this when it stands it there. */
	public void setHome(Vec3 home) {
		this.home = home;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(AWAKE, false);
		entityData.define(FLIPPED, false);
	}

	public boolean awake() {
		return this.entityData.get(AWAKE);
	}

	public boolean flipped() {
		return this.entityData.get(FLIPPED);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	// ---- moving ----

	@Override
	public void travel(Vec3 input) {
		if (!this.flipped()) {
			super.travel(input);
			return;
		}
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
	}

	private void flyTo(Vec3 to, double speed) {
		Vec3 d = to.subtract(this.position());
		double len = d.length();
		if (len < 0.05) {
			return;
		}
		Vec3 want = d.scale(Math.min(speed, len * 0.3) / len);
		this.setDeltaMovement(this.getDeltaMovement().add(want.subtract(this.getDeltaMovement()).scale(0.25)));
	}

	// ---- thinking ----

	/** Whether {@code e} stands in its arena. */
	private boolean inArena(Entity e) {
		if (this.home == null) {
			return e.distanceToSqr(this) < (ARENA_RADIUS + 2) * (ARENA_RADIUS + 2);
		}
		double dx = e.getX() - this.home.x, dz = e.getZ() - this.home.z;
		return dx * dx + dz * dz <= (ARENA_RADIUS + 1.5) * (ARENA_RADIUS + 1.5) && e.getY() > this.home.y - 3.0 && e.getY() < this.home.y + ARENA_HEIGHT + 2.0;
	}

	private List<ServerPlayer> fighters(ServerLevel level) {
		List<ServerPlayer> in = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && this.inArena(p)) {
				in.add(p);
			}
		}
		return in;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null) {
			this.home = this.position();
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		List<ServerPlayer> fighters = this.fighters(level);
		this.pull(level, fighters);
		if (!this.awake()) {
			this.setTarget(null);
			for (ServerPlayer p : fighters) {
				if (p.distanceToSqr(this) < WAKE_RANGE * WAKE_RANGE) {
					this.wake(level);
					break;
				}
			}
			return;
		}
		if (fighters.isEmpty()) {
			if (++this.quiet > QUIET_TICKS) {
				this.sleep();
			}
			return;
		}
		this.quiet = 0;
		LivingEntity target = this.getTarget();
		if (!(target instanceof ServerPlayer sp) || !fighters.contains(sp)) {
			target = fighters.get(this.random.nextInt(fighters.size()));
			this.setTarget(target);
		}
		if (!this.flipped() && this.getHealth() < this.getMaxHealth() * 0.5F) {
			this.flip(level);
		}
		// walk or fly at it, club it in reach
		if (this.flipped()) {
			double y = Mth.clamp(target.getY() - 1.5, this.home.y + 1.0, this.home.y + ARENA_HEIGHT - 6.0);
			this.flyTo(new Vec3(target.getX(), y, target.getZ()), 0.28);
		} else if (this.tickCount % 10 == 0 || this.getNavigation().isDone()) {
			this.getNavigation().moveTo(target, 1.0);
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (this.swingCooldown > 0) {
			this.swingCooldown--;
		} else if (this.distanceToSqr(target) < REACH * REACH) {
			this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
			this.doHurtTarget(level, target);
			this.swingCooldown = 25;
		}
		// bolts and gusts
		if (this.boltTicks >= 0) {
			this.boltTicks++;
			for (Vec3 m : this.marks) {
				level.sendParticles(MagicFx.CRACKLE, m.x, m.y + 0.2, m.z, 6, BOLT_RADIUS * 0.5, 0.05, BOLT_RADIUS * 0.5, 0.0);
			}
			if (this.boltTicks >= BOLT_WAIT) {
				this.strike(level);
			}
		} else if (--this.gap <= 0) {
			if (this.random.nextInt(3) == 0) {
				this.gust(level, fighters);
			} else {
				this.mark(level, fighters);
			}
			this.gap = this.flipped() ? FLIP_GAP_MIN + this.random.nextInt(FLIP_GAP_MAX - FLIP_GAP_MIN) : GAP_MIN + this.random.nextInt(GAP_MAX - GAP_MIN);
		}
	}

	private void wake(ServerLevel level) {
		this.entityData.set(AWAKE, true);
		this.bar.setVisible(true);
		this.gap = GAP_MAX;
		this.quiet = 0;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 2.0, this.getZ(), 40, 1.5, 2.0, 1.5, 0.05);
	}

	private void sleep() {
		this.entityData.set(AWAKE, false);
		this.entityData.set(FLIPPED, false);
		this.setNoGravity(false);
		this.setHealth(this.getMaxHealth());
		this.setTarget(null);
		this.marks.clear();
		this.boltTicks = -1;
		this.bar.setVisible(false);
		if (this.home != null) {
			this.setPos(this.home.x, this.home.y, this.home.z);
		}
	}

	/** Halfway: the arena turns over. */
	private void flip(ServerLevel level) {
		this.entityData.set(FLIPPED, true);
		this.setNoGravity(true);
		this.setDeltaMovement(0.0, 0.4, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 2.0F, 1.4F);
		level.sendParticles(MagicFx.VOID, this.getX(), this.getY() + 4.0, this.getZ(), 120, ARENA_RADIUS * 0.5, 4.0, ARENA_RADIUS * 0.5, 0.05);
	}

	/** Keeps the flipped pull on everyone in the arena, and takes it off anyone who is not (or once it is over). */
	private void pull(ServerLevel level, List<ServerPlayer> fighters) {
		boolean on = this.flipped() && this.isAlive();
		Set<UUID> now = new HashSet<>();
		if (on) {
			for (ServerPlayer p : fighters) {
				now.add(p.getUUID());
				SkyPockets.set(p, Attributes.GRAVITY, FLIP_ID, FLIP_GRAVITY, AttributeModifier.Operation.ADD_VALUE);
				p.resetFallDistance();
			}
		}
		for (UUID id : this.pulled) {
			if (!now.contains(id) && level.getEntity(id) instanceof ServerPlayer p) {
				release(p);
			}
		}
		this.pulled.clear();
		this.pulled.addAll(now);
	}

	static void release(ServerPlayer p) {
		SkyPockets.set(p, Attributes.GRAVITY, FLIP_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, true));
	}

	private void releaseAll() {
		if (this.level() instanceof ServerLevel level) {
			for (UUID id : this.pulled) {
				if (level.getEntity(id) instanceof ServerPlayer p) {
					release(p);
				}
			}
		}
		this.pulled.clear();
	}

	/** Marks a spot under each foe (two flipped); the bolt falls there {@link #BOLT_WAIT} ticks later. */
	private void mark(ServerLevel level, List<ServerPlayer> fighters) {
		this.marks.clear();
		int n = this.flipped() ? Math.min(2, fighters.size()) : 1;
		for (int i = 0; i < n; i++) {
			ServerPlayer p = fighters.get((i + this.random.nextInt(fighters.size())) % fighters.size());
			this.marks.add(p.position());
		}
		this.boltTicks = 0;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 1.6F);
	}

	private void strike(ServerLevel level) {
		for (Vec3 m : this.marks) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(m.x, m.y, m.z);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			for (ServerPlayer p : level.players()) {
				if (p.isAlive() && p.position().distanceToSqr(m) < BOLT_RADIUS * BOLT_RADIUS) {
					p.hurtServer(level, this.damageSources().lightningBolt(), BOLT_DAMAGE);
				}
			}
		}
		this.marks.clear();
		this.boltTicks = -1;
	}

	/** Throws everyone near back (and, flipped, harder against the ceiling). */
	private void gust(ServerLevel level, List<ServerPlayer> fighters) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, this.getX(), this.getY() + 2.0, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		for (ServerPlayer p : fighters) {
			Vec3 d = p.position().subtract(this.position());
			if (d.lengthSqr() > GUST_RADIUS * GUST_RADIUS) {
				continue;
			}
			Vec3 push = new Vec3(d.x, 0.0, d.z).normalize().scale(1.4);
			p.push(push.x, this.flipped() ? 0.3 : 0.5, push.z);
			p.syncVelocity = true;
			p.hurtServer(level, this.damageSources().mobAttack(this), GUST_DAMAGE);
		}
	}

	// ---- being hurt, dying ----

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer && !this.awake()) {
			this.wake(level);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		this.releaseAll();
		if (this.level() instanceof ServerLevel level) {
			ExperienceOrb.award(level, this.position(), XP);
			this.spawnAtLocation(level, new ItemStack(com.jaymospowers.aether.AetherBlocks.GRAVITITE_BLOCK), 1.0F);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0F, 1.2F);
		}
		this.entityData.set(FLIPPED, false);
		this.bar.removeAllPlayers();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		this.releaseAll();
		super.remove(reason);
		this.bar.removeAllPlayers();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.awake() ? SoundEvents.BREEZE_IDLE_AIR : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	// ---- saving ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.putIntArray("home", new int[] {Mth.floor(this.home.x * 16.0), Mth.floor(this.home.y * 16.0), Mth.floor(this.home.z * 16.0)});
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.getIntArray("home").ifPresent(h -> {
			if (h.length == 3) {
				this.home = new Vec3(h[0] / 16.0, h[1] / 16.0, h[2] / 16.0);
			}
		});
		// a fight never survives a reload: it wakes again when someone comes
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
