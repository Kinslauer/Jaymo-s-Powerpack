package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.KitchenCompat;
import com.jaymoskingdoms.creatures.Beast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Unburied: a giant that gets up at night where many dead were left lying together ({@code NightThings.unburied}).
 * Andy: "can it be made from a conglomerate of the bodies? and it NEEDS to disappear / dissassemble during the day
 * instead of buring".
 * <ul>
 * <li><b>Made of its dead.</b> The bodies it rises from are taken into it whole ({@link #take}: each Kitchen corpse's
 * own saved data, kept in the giant) and it is drawn as them: two stand as its legs, two hang head-down from its
 * shoulders as its arms, more lie crosswise as its trunk and one is its head; with more dead it grows a shoulder yoke,
 * bodies clinging front and back and doubled limbs ({@link #SLOTS}, up to {@link #DRAWN} drawn). Each is drawn as that
 * body looks (its kind, its skin, an MCA villager's own face and clothes, the parts it had lost), limbs splayed and
 * eyes shut as a corpse's are. One raised from an egg gathers the dead round it and the old dead of the place
 * ({@link #finalizeSpawn}); only without Kitchen (no bodies at all) is it the old giant of bones.</li>
 * <li><b>It comes apart by day.</b> Once the night is over it stops where it stands, shudders, and
 * the dead drop out of it one by one from the top down ({@link #DROP_EVERY} ticks apart), each a real corpse again
 * (loot, rot and all), tumbling to the ground from where it was in the giant; then there is nothing left of it.
 * Killed, all its dead fall out of it at once, and they are all that is left of it (it leaves no body of its own).</li>
 * </ul>
 * A great foe (its own bar, a posture to break). It is slow and its blows throw people. A field where the dead are
 * buried or burned never raises one.
 */
public class Unburied extends Beast {
	static final double HEALTH = 220.0, SPEED = 0.2, DAMAGE = 12.0, KNOCKBACK = 2.5;

	/** How many of its dead are drawn (the rest are carried just the same and fall out with the others). */
	public static final int DRAWN = 24;
	/** Coming apart: one body drops out every this many ticks. */
	static final int DROP_EVERY = 4;
	/** How hard a body falls out of it: outward, and up. */
	static final double SPILL_OUT = 0.22, SPILL_UP = 0.12;

	/** The parts of its frame a body hangs off (as the bone giant's model parts, which move the same way). */
	public static final int TRUNK = 0, HEAD = 1, ARM_R = 2, ARM_L = 3, LEG_R = 4, LEG_L = 5;
	/**
	 * Where each part pivots, in blocks from its feet, facing its way (x to its right, y up, z backward): the bone
	 * giant's model (scale 2.6, 1/16 block a pixel), hips at 1.95, shoulders at 3.575, neck at 3.9.
	 */
	public static final float[][] PIVOTS = {{0.0F, 1.95F, 0.0F}, {0.0F, 3.9F, 0.0F}, {0.8125F, 3.575F, 0.0F}, {-0.8125F, 3.575F, 0.0F},
		{0.325F, 1.95F, 0.0F}, {-0.325F, 1.95F, 0.0F}};

	/** How a body lies in its place, turned from standing (feet down, face forward). */
	public enum Lay {
		/** Standing, face forward. */
		UP,
		/** Standing, face back. */
		UP_BACK,
		/** Head down, face forward. */
		DOWN,
		/** Head down, face back. */
		DOWN_BACK,
		/** Lying crosswise, head to the giant's right. */
		ACROSS_R,
		/** Lying crosswise, head to its left. */
		ACROSS_L,
		/** Lying front to back on its belly, head forward. */
		ALONG
	}

	/**
	 * One body's place: the part it hangs off, where its middle is from that part's pivot (blocks: x right, y up, z
	 * back), how it lies and how long it is drawn (blocks, head to foot).
	 */
	public record Slot(int part, float x, float y, float z, Lay lay, float length) {
	}

	/**
	 * Its dead, in the order they're taken: the first eight make a whole giant (legs, trunk, arms, head); more thicken
	 * it.
	 */
	public static final Slot[] SLOTS = {
		// legs: standing, their feet its feet, their heads at its hips
		new Slot(LEG_R, 0.0F, -0.975F, 0.0F, Lay.UP, 1.95F),
		new Slot(LEG_L, 0.0F, -0.975F, 0.0F, Lay.UP, 1.95F),
		// the trunk: three lying crosswise, heaped one on another
		new Slot(TRUNK, 0.0F, 0.35F, 0.0F, Lay.ACROSS_R, 1.6F),
		new Slot(TRUNK, 0.0F, 0.95F, 0.05F, Lay.ACROSS_L, 1.6F),
		new Slot(TRUNK, 0.0F, 1.55F, 0.0F, Lay.ACROSS_R, 1.7F),
		// arms: hanging head down from the shoulders, their heads its fists
		new Slot(ARM_R, 0.0F, -1.1375F, 0.0F, Lay.DOWN, 2.275F),
		new Slot(ARM_L, 0.0F, -1.1375F, 0.0F, Lay.DOWN, 2.275F),
		// the head: one lying crosswise atop the heap, face forward
		new Slot(HEAD, 0.0F, 0.5F, 0.0F, Lay.ACROSS_L, 1.3F),
		// more dead: a yoke across the shoulders, one clinging to its back, one to its front
		new Slot(TRUNK, 0.0F, 1.95F, 0.2F, Lay.ACROSS_L, 2.1F),
		new Slot(TRUNK, 0.0F, 0.95F, 0.45F, Lay.DOWN_BACK, 1.9F),
		new Slot(TRUNK, 0.0F, 0.95F, -0.45F, Lay.UP, 1.8F),
		// doubled legs: hanging head down on the outsides of the thighs
		new Slot(LEG_R, 0.25F, -0.85F, 0.0F, Lay.DOWN, 1.7F),
		new Slot(LEG_L, -0.25F, -0.85F, 0.0F, Lay.DOWN, 1.7F),
		// doubled arms: head up at the shoulder, feet at the fist
		new Slot(ARM_R, 0.25F, -1.0F, 0.0F, Lay.UP, 2.0F),
		new Slot(ARM_L, -0.25F, -1.0F, 0.0F, Lay.UP, 2.0F),
		// a thicker trunk: two more crosswise behind, one in front
		new Slot(TRUNK, 0.0F, 0.6F, 0.35F, Lay.ACROSS_L, 1.6F),
		new Slot(TRUNK, 0.0F, 1.3F, 0.35F, Lay.ACROSS_R, 1.6F),
		new Slot(TRUNK, 0.0F, 0.3F, -0.35F, Lay.ACROSS_L, 1.5F),
		// a crown: one lying front to back on top of its head
		new Slot(HEAD, 0.0F, 0.95F, 0.1F, Lay.ALONG, 1.2F),
		// over each shoulder, front to back
		new Slot(TRUNK, 0.7F, 1.9F, 0.0F, Lay.ALONG, 1.5F),
		new Slot(TRUNK, -0.7F, 1.9F, 0.0F, Lay.ALONG, 1.5F),
		new Slot(TRUNK, 0.0F, 1.5F, -0.35F, Lay.ACROSS_L, 1.6F),
		// behind the legs, head down
		new Slot(LEG_R, 0.0F, -0.85F, 0.25F, Lay.DOWN_BACK, 1.7F),
		new Slot(LEG_L, 0.0F, -0.85F, 0.25F, Lay.DOWN_BACK, 1.7F)};

	/** Each drawn body's look, for clients: the parts it had lost, "|", then the dead mob's saved data (Kitchen's "Look"). Empty for none. */
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<String>[] BODY = new EntityDataAccessor[DRAWN];
	/** How many dead it was made of (0: raised from an egg, the bone giant). */
	private static final EntityDataAccessor<Integer> TOOK = SynchedEntityData.defineId(Unburied.class, EntityDataSerializers.INT);
	/** Ticks since it began to come apart (0: whole). */
	private static final EntityDataAccessor<Integer> APART = SynchedEntityData.defineId(Unburied.class, EntityDataSerializers.INT);

	static {
		for (int i = 0; i < DRAWN; i++) {
			BODY[i] = SynchedEntityData.defineId(Unburied.class, EntityDataSerializers.STRING);
		}
	}

	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_12);
	/** Server: its dead, each a Kitchen corpse's full saved data, in slot order; null once it has fallen out. */
	private final List<@Nullable CompoundTag> bodies = new ArrayList<>();
	/** Client: whatever the renderer keeps of the bodies it has rebuilt to draw. */
	public @Nullable Object drawn;

	public Unburied(EntityType<? extends Unburied> type, Level level) {
		super(type, level);
		this.xpReward = 100;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ATTACK_KNOCKBACK, KNOCKBACK).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.STEP_HEIGHT, 1.6).add(Attributes.ENTITY_INTERACTION_RANGE, 4.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		for (EntityDataAccessor<String> body : BODY) {
			entityData.define(body, "");
		}
		entityData.define(TOOK, 0);
		entityData.define(APART, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	// ---- its dead ------------------------------------------------------------------------------------------------------

	/** Takes these dead into itself (they leave the world, kept whole in it). */
	void take(ServerLevel level, List<Entity> dead) {
		for (Entity body : dead) {
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			if (body.saveAsPassenger(out)) {
				this.bodies.add(out.buildResult());
			}
			body.discard();
		}
		this.entityData.set(TOOK, this.bodies.size());
		refreshLooks();
	}

	/** One raised with no dead of its own (an egg, a summons) is made of this many, and of these. */
	static final int RAISED_DEAD = 10;

	/**
	 * Raised from nothing (an egg, a summons), it is still made of the dead: whatever lies unburied round it, and for
	 * the rest the old dead of the place, come up out of the ground with it. Without Kitchen there are no bodies to be
	 * made of, and it is the giant of bones.
	 */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		if (this.bodies.isEmpty() && KitchenCompat.makesCorpses()) {
			ServerLevel world = level.getLevel();
			List<Entity> dead = new ArrayList<>(KitchenCompat.fallenNear(world, this.position(), NightThings.DEAD_RANGE));
			List<EntityType<? extends net.minecraft.world.entity.Mob>> kinds = List.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.ZOMBIE);
			for (int i = dead.size(); i < RAISED_DEAD; i++) {
				net.minecraft.world.entity.Mob old = kinds.get(this.random.nextInt(kinds.size())).create(world, EntitySpawnReason.EVENT);
				if (old == null) {
					continue;
				}
				old.snapTo(this.getX(), this.getY(), this.getZ(), this.random.nextFloat() * 360.0F, 0.0F);
				Entity body = KitchenCompat.bodyOf(world, old);
				if (body != null) {
					dead.add(body);
				}
			}
			if (!dead.isEmpty()) {
				take(world, dead);
			}
		}
		return data;
	}

	/** Each drawn slot's look, from the body in it (none once it has fallen out). */
	private void refreshLooks() {
		for (int i = 0; i < DRAWN; i++) {
			CompoundTag body = i < this.bodies.size() ? this.bodies.get(i) : null;
			this.entityData.set(BODY[i], body == null ? "" : body.getIntOr("Missing", 0) + "|" + body.getStringOr("Look", ""));
		}
	}

	/** How many dead it was made of (0 for one from an egg). */
	public int took() {
		return this.entityData.get(TOOK);
	}

	/** Slot {@code i}'s look as synced: the parts the body had lost, "|", its saved data; empty for none. */
	public String bodyLook(int i) {
		return this.entityData.get(BODY[i]);
	}

	/** Ticks since it began to come apart (0 while it's whole). */
	public int apart() {
		return this.entityData.get(APART);
	}

	/** Where slot {@code i}'s body is in the world, the giant standing still. */
	private Vec3 slotPosition(int i) {
		Slot slot = SLOTS[Math.min(i, SLOTS.length - 1)];
		float[] pivot = PIVOTS[slot.part()];
		double right = pivot[0] + slot.x(), up = pivot[1] + slot.y(), back = pivot[2] + slot.z();
		float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
		// facing yaw: forward (-sin, cos), its right (-cos, -sin)
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		return new Vec3(this.getX() - fz * right - fx * back, this.getY() + up, this.getZ() + fx * right - fz * back);
	}

	/** The next body to fall out (the highest left), or -1 when none is left. */
	private int nextToFall() {
		int best = -1;
		double bestY = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < this.bodies.size(); i++) {
			if (this.bodies.get(i) == null) {
				continue;
			}
			double y = slotPosition(i).y;
			if (y > bestY) {
				best = i;
				bestY = y;
			}
		}
		return best;
	}

	/** Body {@code i} falls out of it, a corpse again, tumbling down from where it was. */
	private void spill(ServerLevel level, int i) {
		CompoundTag tag = this.bodies.get(i);
		this.bodies.set(i, null);
		if (i < DRAWN) {
			this.entityData.set(BODY[i], "");
		}
		if (tag == null) {
			return;
		}
		tag = tag.copy();
		// it lay somewhere else, in a pose it doesn't have now: it falls fresh
		tag.remove("Points");
		tag.remove("Pinned");
		tag.remove("SlungOn");
		tag.remove("Hook");
		Vec3 at = slotPosition(i);
		Entity body = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level,
			EntitySpawnReason.LOAD, e -> e);
		if (body == null) {
			return;
		}
		body.snapTo(at.x, at.y - body.getBbHeight() / 2.0, at.z, body.getYRot(), 0.0F);
		if (!level.noCollision(body)) {
			// no room up there (a low roof): it slumps off at the feet
			body.snapTo(this.getX(), this.getY(), this.getZ(), body.getYRot(), 0.0F);
		}
		if (!level.addFreshEntity(body)) {
			JaymosKingdoms.LOGGER.debug("An Unburied's body could not be put back: {}", tag.getStringOr("id", "?"));
			return;
		}
		Vec3 out = new Vec3(at.x - this.getX(), 0.0, at.z - this.getZ());
		out = out.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, this.random.nextFloat() * 360.0F) : out.normalize();
		Vec3 velocity = out.scale(SPILL_OUT * (0.6 + this.random.nextDouble() * 0.8)).add(0.0, SPILL_UP * this.random.nextDouble(), 0.0);
		KitchenCompat.spill(body, velocity);
		level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.01);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.2F, 0.4F + this.random.nextFloat() * 0.2F);
	}

	/** All its dead fall out of it at once. */
	private void spillAll(ServerLevel level) {
		for (int i = 0; i < this.bodies.size(); i++) {
			spill(level, i);
		}
	}

	// ---- night and day -------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		// not in its AI step: that stops running the moment it stops moving (isImmobile), and it would stand there
		// whole all day
		if (this.level() instanceof ServerLevel level && this.isAlive()) {
			nightAndDay(level);
		}
	}

	private void nightAndDay(ServerLevel level) {
		if (this.tickCount % 5 == 0) {
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
			BossBars.show(level, this, this.bar);
		}
		int apart = apart();
		if (apart == 0 && !level.isDarkOutside()) {
			// the night is over: it stops, shudders and lets go of its dead
			apart = 1;
			level.playSound(null, this.getX(), this.getY() + 3.0, this.getZ(), SoundEvents.SKELETON_HORSE_DEATH, SoundSource.HOSTILE, 3.0F, 0.3F);
		} else if (apart > 0) {
			apart++;
		}
		if (apart == 0) {
			return;
		}
		this.entityData.set(APART, apart);
		this.setTarget(null);
		this.getNavigation().stop();
		this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
		if (apart % DROP_EVERY != 0) {
			return;
		}
		int next = nextToFall();
		if (next >= 0) {
			spill(level, next);
			return;
		}
		// nothing left of it: what held them together falls away as dust and old bone
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.BONE), this.getX(), this.getY() + 2.0, this.getZ(), 40, 0.6, 1.5, 0.6, 0.1);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 2.0, this.getZ(), 20, 0.6, 1.5, 0.6, 0.02);
		level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.SKELETON_DEATH, SoundSource.HOSTILE, 2.0F, 0.5F);
		this.discard();
	}

	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || apart() > 0;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			spillAll(level);
		}
	}

	// ---- saved ---------------------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		List<CompoundTag> list = new ArrayList<>();
		for (CompoundTag body : this.bodies) {
			if (body != null) {
				list.add(body);
			}
		}
		output.store("Bodies", CompoundTag.CODEC.listOf(), list);
		output.putInt("Took", took());
		output.putInt("Apart", apart());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.bodies.clear();
		input.read("Bodies", CompoundTag.CODEC.listOf()).ifPresent(this.bodies::addAll);
		this.entityData.set(TOOK, input.getIntOr("Took", this.bodies.size()));
		this.entityData.set(APART, input.getIntOr("Apart", 0));
		refreshLooks();
	}

	// ---- the rest ------------------------------------------------------------------------------------------------------

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SKELETON_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.4F;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}
}
