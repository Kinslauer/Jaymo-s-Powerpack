package com.jaymospowers.aether.dungeon;

import com.jaymospowers.core.Shakes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Slider: the Sentry Ruins' boss, a 2 by 2 by 2 block of carved stone that floats and slides along one axis at a time.
 * It sleeps until a player hits it with a pickaxe from inside its room (nothing else hurts it, and not from outside); then
 * the room's doors shut and it comes at you in straight lunges, speeding up as it goes, breaking whatever is soft
 * enough in its way. What it touches takes 6 and is thrown. At each sixteenth of its health it springs one of the room's
 * traps; under a quarter it lunges more often. Dead, it drifts to the middle of the room, bursts, and the doors open.
 * If everyone leaves or falls it goes back to sleep at full health.
 */
public class Slider extends Monster {
	// ---- tuning (the notes' numbers) ----
	public static final double HEALTH = 350.0, FOLLOW = 64.0;
	/** A lunge speeds up this much a tick, to at most MAX_SPEED blocks a tick. */
	public static final double ACCEL = 0.023, MAX_SPEED = 2.5;
	/** Ticks of rest between lunges, and under a quarter of its health. */
	public static final int GAP_MIN = 2, GAP_MAX = 15, RAGE_GAP_MIN = 1, RAGE_GAP_MAX = 10;
	public static final float RAGE_FRACTION = 0.25F;
	/** What it does to what it touches: damage, how hard it throws it sideways (1.6 is four times a sword's knock) and up. */
	public static final float CRUSH_DAMAGE = 6.0F;
	public static final double LAUNCH = 1.6, LAUNCH_UP = 0.4;
	/** After a hit it waits this long, then backs off this many blocks at this speed. */
	public static final int BACKOFF_WAIT = 20;
	public static final double BACKOFF_BLOCKS = 2.0, BACK_SPEED = 0.4;
	/** A trap goes off for every this-many-th of its health lost. */
	public static final int TRAP_STEPS = 16;
	/** Ticks it takes to die; ticks with nobody in the room before it sleeps again. */
	public static final int DEATH_TICKS = 110, QUIET_TICKS = 100;
	/** Blocks as hard as this or harder (and anything unbreakable) stop it. */
	public static final float HARDNESS_LIMIT = 100.0F;
	public static final int XP = 250;
	/** Players this near see its boss bar. */
	public static final double BAR_RANGE = 64.0;
	/** Its wall-hit shake and the chance it picks the second-best direction. */
	public static final float HIT_SHAKE = 0.8F, SHAKE_RANGE = 40.0F;
	public static final float SECOND_CHOICE = 0.3F;

	private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(Slider.class, EntityDataSerializers.BOOLEAN);

	private @Nullable BoundingBox room;
	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
	private @Nullable Direction heading;
	private double speed;
	private int gap = 20, pause, backing, quiet, trapsFired;
	private @Nullable Direction backDir;

	public Slider(EntityType<? extends Slider> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPersistenceRequired();
		this.xpReward = 0;
		this.bar.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	/** Sets the room it guards (its interior); the structure calls this when it places it. */
	public void setRoom(BoundingBox interior) {
		this.room = interior;
	}

	public @Nullable BoundingBox room() {
		return this.room;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(AWAKE, false);
	}

	/** Awake (fighting) or asleep, for its face. */
	public boolean awake() {
		return this.entityData.get(AWAKE);
	}

	// ---- being hurt ----

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		Entity attacker = source.getEntity();
		// shots and anything thrown bounce off; only a player's own blow with a pickaxe counts
		if (!(attacker instanceof ServerPlayer player) || source.getDirectEntity() != attacker) {
			return false;
		}
		if (!player.getMainHandItem().is(ItemTags.PICKAXES)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.slider.pickaxe"));
			return false;
		}
		if (this.room != null && !this.room.isInside(player.blockPosition())) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.slider.too_far"));
			return false;
		}
		if (!this.awake()) {
			this.wake(level);
		}
		return super.hurtServer(level, source, amount);
	}

	private void wake(ServerLevel level) {
		this.entityData.set(AWAKE, true);
		this.setHealth(this.getMaxHealth());
		this.quiet = 0;
		this.trapsFired = 0;
		this.gap = 20;
		this.heading = null;
		this.speed = 0.0;
		this.pause = 0;
		this.backing = 0;
		if (this.room != null) {
			Doorways.setClosed(level, this.room, true);
		}
		this.bar.setVisible(true);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), DungeonSounds.get("block.anvil.place"), SoundSource.HOSTILE, 1.5F, 0.5F);
		Shakes.around(level, this.position(), SHAKE_RANGE, HIT_SHAKE);
	}

	/** Everyone has left or fallen: back to sleep, fully healed, the doors open. */
	private void sleep(ServerLevel level) {
		this.entityData.set(AWAKE, false);
		this.setHealth(this.getMaxHealth());
		this.heading = null;
		this.speed = 0.0;
		this.pause = 0;
		this.backing = 0;
		if (this.room != null) {
			Doorways.setClosed(level, this.room, false);
			Vec3 home = this.home();
			this.setPos(home.x, home.y, home.z);
		}
		this.bar.setVisible(false);
		this.bar.removeAllPlayers();
	}

	/** The middle of its room, where it sleeps and where it floats to when it dies. */
	private Vec3 home() {
		BoundingBox r = this.room;
		return new Vec3((r.minX() + r.maxX() + 1) / 2.0, r.minY() + 2.0, (r.minZ() + r.maxZ() + 1) / 2.0);
	}

	// ---- being awake ----

	private boolean immune(ServerLevel level, BlockPos pos, BlockState state) {
		return state.is(DungeonBlocks.SLIDER_IMMUNE) || state.getDestroySpeed(level, pos) < 0.0F || state.getDestroySpeed(level, pos) >= HARDNESS_LIMIT;
	}

	private List<ServerPlayer> playersInside(ServerLevel level) {
		List<ServerPlayer> inside = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			boolean there = this.room != null ? this.room.isInside(player.blockPosition()) : player.distanceToSqr(this) < BAR_RANGE * BAR_RANGE;
			if (there) {
				inside.add(player);
			}
		}
		return inside;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.getCustomName() == null) {
			this.setCustomName(Component.literal(this.makeName()));
			this.bar.setName(this.getDisplayName());
		}
		this.setDeltaMovement(Vec3.ZERO);
		if (!this.awake()) {
			return;
		}
		List<ServerPlayer> inside = this.playersInside(level);
		if (inside.isEmpty()) {
			if (++this.quiet >= QUIET_TICKS) {
				this.sleep(level);
			}
			return;
		}
		this.quiet = 0;
		this.syncBar(level);
		this.springTraps(level);
		this.step(level, inside);
	}

	private String makeName() {
		String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
		String both = letters + "0123456789";
		return "Mk." + letters.charAt(this.random.nextInt(letters.length())) + "-" + this.random.nextInt(10)
			+ both.charAt(this.random.nextInt(both.length())) + letters.charAt(this.random.nextInt(letters.length())) + this.random.nextInt(10);
	}

	/** The bar shows to everyone near, with the health left. */
	private void syncBar(ServerLevel level) {
		this.bar.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
		if (this.tickCount % 10 != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			boolean near = player.distanceToSqr(this) < BAR_RANGE * BAR_RANGE;
			if (near && !this.bar.getPlayers().contains(player)) {
				this.bar.addPlayer(player);
			} else if (!near && this.bar.getPlayers().contains(player)) {
				this.bar.removePlayer(player);
			}
		}
	}

	/** One loaded trap goes off for every sixteenth of its health that is gone. */
	private void springTraps(ServerLevel level) {
		if (this.room == null) {
			return;
		}
		int due = (int)Math.floor((this.getMaxHealth() - this.getHealth()) / (this.getMaxHealth() / TRAP_STEPS));
		while (this.trapsFired < due) {
			this.trapsFired++;
			List<BlockPos> traps = Doorways.loadedTraps(level, this.room);
			if (!traps.isEmpty()) {
				SentryTrapBlock.spring(level, traps.get(this.random.nextInt(traps.size())));
			}
		}
	}

	private int nextGap() {
		boolean rage = this.getHealth() <= this.getMaxHealth() * RAGE_FRACTION;
		int lo = rage ? RAGE_GAP_MIN : GAP_MIN, hi = rage ? RAGE_GAP_MAX : GAP_MAX;
		return lo + this.random.nextInt(hi - lo + 1);
	}

	/** One tick of the fight: rest, back off, pick a direction, or lunge. */
	private void step(ServerLevel level, List<ServerPlayer> inside) {
		if (this.pause > 0) {
			this.pause--;
			return;
		}
		if (this.backing > 0 && this.backDir != null) {
			this.backing--;
			Vec3 before = this.position();
			Vec3 back = new Vec3(this.backDir.getStepX(), this.backDir.getStepY(), this.backDir.getStepZ()).scale(BACK_SPEED);
			this.move(MoverType.SELF, back);
			if (this.position().subtract(before).length() < BACK_SPEED * 0.5) {
				this.backing = 0;
			}
			if (this.backing == 0) {
				this.gap = this.nextGap();
			}
			return;
		}
		if (this.heading == null) {
			if (this.gap > 0) {
				this.gap--;
			} else {
				this.choose(level, inside);
			}
			return;
		}
		this.speed = Math.min(MAX_SPEED, this.speed + ACCEL);
		Vec3 step = new Vec3(this.heading.getStepX(), this.heading.getStepY(), this.heading.getStepZ()).scale(this.speed);
		this.breakAhead(level, step);
		Vec3 before = this.position();
		this.move(MoverType.SELF, step);
		if (this.crush(level)) {
			this.backDir = this.heading.getOpposite();
			this.backing = (int)Math.ceil(BACKOFF_BLOCKS / BACK_SPEED);
			this.pause = BACKOFF_WAIT;
			this.heading = null;
			this.speed = 0.0;
			return;
		}
		if (this.position().subtract(before).length() < step.length() - 1.0E-3) {
			// it hit something it cannot break
			if (this.speed > 0.4) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), DungeonSounds.get("entity.iron_golem.damage"), SoundSource.HOSTILE, 2.0F, 0.5F);
				Shakes.around(level, this.position(), SHAKE_RANGE, (float)Math.min(HIT_SHAKE, this.speed * 0.4));
			}
			this.heading = null;
			this.speed = 0.0;
			this.gap = this.nextGap();
		}
	}

	/** Picks the axis that is furthest from the nearest player (so it closes that gap), skipping a way that is walled with something unbreakable. */
	private void choose(ServerLevel level, List<ServerPlayer> inside) {
		Vec3 center = this.position().add(0.0, this.getBbHeight() / 2.0, 0.0);
		ServerPlayer target = inside.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
		if (target == null) {
			return;
		}
		Vec3 d = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0).subtract(center);
		List<Direction> options = new ArrayList<>(List.of(
			d.x >= 0 ? Direction.EAST : Direction.WEST,
			d.y >= 0 ? Direction.UP : Direction.DOWN,
			d.z >= 0 ? Direction.SOUTH : Direction.NORTH));
		options.sort(Comparator.comparingDouble((Direction dir) -> -Math.abs(dir.getAxis().choose(d.x, d.y, d.z))));
		List<Direction> free = new ArrayList<>();
		for (Direction dir : options) {
			if (!this.walled(level, dir)) {
				free.add(dir);
			}
		}
		if (free.isEmpty()) {
			// boxed in by unbreakable blocks on its way: it goes up, or any way that is open
			for (Direction dir : new Direction[] {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				if (!this.walled(level, dir)) {
					free.add(dir);
					break;
				}
			}
		}
		if (free.isEmpty()) {
			this.gap = this.nextGap();
			return;
		}
		int pick = free.size() > 1 && this.random.nextFloat() < SECOND_CHOICE ? 1 : 0;
		this.heading = free.get(pick);
		this.speed = 0.0;
	}

	/** Whether the blocks right beside it that way include one it cannot break. */
	private boolean walled(ServerLevel level, Direction dir) {
		AABB next = this.getBoundingBox().move(dir.getStepX() * 0.6, dir.getStepY() * 0.6, dir.getStepZ() * 0.6);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(next.minX + 1.0E-3, next.minY + 1.0E-3, next.minZ + 1.0E-3),
			BlockPos.containing(next.maxX - 1.0E-3, next.maxY - 1.0E-3, next.maxZ - 1.0E-3))) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && this.immune(level, pos, state)) {
				return true;
			}
		}
		return false;
	}

	/** Breaks everything breakable in the box it is about to sweep. */
	private void breakAhead(ServerLevel level, Vec3 step) {
		AABB sweep = this.getBoundingBox().expandTowards(step);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(sweep.minX + 1.0E-3, sweep.minY + 1.0E-3, sweep.minZ + 1.0E-3),
			BlockPos.containing(sweep.maxX - 1.0E-3, sweep.maxY - 1.0E-3, sweep.maxZ - 1.0E-3))) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty() && !this.immune(level, pos, state)) {
				level.destroyBlock(pos.immutable(), false);
			}
		}
	}

	/** Whatever it is touching is hurt and thrown. Returns true if it hit anything. */
	private boolean crush(ServerLevel level) {
		boolean hit = false;
		List<LivingEntity> touched = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.15),
			e -> e != this && e.isAlive() && !(e instanceof Slider) && !(e instanceof Player p && p.isSpectator()));
		for (LivingEntity e : touched) {
			hit = true;
			e.hurtServer(level, this.damageSources().mobAttack(this), CRUSH_DAMAGE);
			Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				away = new Vec3(this.heading != null ? this.heading.getStepX() : 1, 0.0, this.heading != null ? this.heading.getStepZ() : 0);
			}
			away = away.normalize();
			e.setDeltaMovement(away.x * LAUNCH, LAUNCH_UP, away.z * LAUNCH);
			e.syncVelocity = true;
		}
		if (hit) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), DungeonSounds.get("entity.iron_golem.attack"), SoundSource.HOSTILE, 2.0F, 0.6F);
			Shakes.around(level, this.position(), SHAKE_RANGE, HIT_SHAKE);
		}
		return hit;
	}

	// ---- standing still, not being pushed ----

	@Override
	public void travel(Vec3 input) {
		// it moves only the way step() moves it
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	// ---- dying ----

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.room != null) {
			Vec3 to = this.home().subtract(this.position());
			double len = to.length();
			if (len > 0.1) {
				Vec3 move = to.scale(Math.min(0.15 / len, 1.0));
				this.setPos(this.getX() + move.x, this.getY() + move.y, this.getZ() + move.z);
			}
		}
		this.bar.setProgress(0.0F);
		if (this.deathTime % 5 == 0) {
			level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.9, 0.9, 0.9, 0.05);
			level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 10, 1.0, 1.0, 1.0, 0.3);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), DungeonSounds.get("block.stone.break"), SoundSource.HOSTILE, 1.5F, 0.5F + this.random.nextFloat() * 0.3F);
		}
		if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
			this.finishDeath(level);
		}
	}

	/** The burst: the doors open, the treasure wall falls, the locked walls turn back to plain stone, the experience comes out. */
	private void finishDeath(ServerLevel level) {
		if (this.room != null) {
			Doorways.setClosed(level, this.room, false);
			Doorways.breakTreasure(level, this.room);
			Doorways.unlock(level, this.room);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 3, 0.8, 0.8, 0.8, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), DungeonSounds.get("entity.generic.explode"), SoundSource.HOSTILE, 3.0F, 0.6F);
		Shakes.around(level, this.position(), SHAKE_RANGE, 1.2F);
		ExperienceOrb.award(level, this.position(), XP);
		this.bar.removeAllPlayers();
		this.bar.setVisible(false);
		this.remove(Entity.RemovalReason.KILLED);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		this.bar.removeAllPlayers();
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return DungeonSounds.get("block.stone.hit");
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return DungeonSounds.get("block.stone.break");
	}

	// ---- saving ----

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("awake", this.awake());
		output.putInt("traps_fired", this.trapsFired);
		if (this.room != null) {
			output.putIntArray("room", new int[] {this.room.minX(), this.room.minY(), this.room.minZ(), this.room.maxX(), this.room.maxY(), this.room.maxZ()});
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(AWAKE, input.getBooleanOr("awake", false));
		this.trapsFired = input.getIntOr("traps_fired", 0);
		input.getIntArray("room").ifPresent(r -> {
			if (r.length == 6) {
				this.room = new BoundingBox(r[0], r[1], r[2], r[3], r[4], r[5]);
			}
		});
		this.bar.setVisible(this.awake());
		if (this.hasCustomName()) {
			this.bar.setName(this.getDisplayName());
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (this.awake()) {
			this.bar.addPlayer(player);
		}
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
