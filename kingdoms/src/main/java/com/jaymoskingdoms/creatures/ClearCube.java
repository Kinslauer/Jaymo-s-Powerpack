package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Saves;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The clear cube: a see-through jelly that fills a dungeon corridor and oozes along it. You see swords and bones floating in
 * it, but too late: it swallows what it touches. A swallowed player is held in the middle of it (they can still strike from
 * inside, and hitting it from outside hurts it too), the acid eats at them every second and wears their armour and what they
 * hold, and a Might save every couple of seconds may tear them free. It also takes whatever lies on the floor, so what it
 * carries is what it has met; slain, it drops all of it.
 */
public class ClearCube extends Beast {
	static final double HEALTH = 50.0, SPEED = 0.12, DAMAGE = 3.0;
	/** The acid: harm to the swallowed each {@link #ACID_EVERY} ticks, and the wear on each piece of armour and what is held. */
	static final float ACID_HARM = 2.0F;
	static final int ACID_EVERY = 20, WEAR = 3;
	/** A Might save to tear free every this often (ticks); it spits out the swallowed after this long (ticks). */
	static final int SAVE_EVERY = 40, DIGEST_TICKS = 20 * 15;
	/** After spitting out, it will not swallow again for this long (ticks). */
	static final int REST_TICKS = 20 * 6;
	/** How many things it can carry in view. */
	public static final int SLOTS = 4;
	/** Look: not swallowing, or holding someone. */
	public static final int EMPTY = 0, HOLDING = 1;
	static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> ACID = net.minecraft.resources.ResourceKey.create(
		Registries.DAMAGE_TYPE, com.jaymoskingdoms.JaymosKingdoms.id("acid"));

	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<ItemStack>[] CARRIED = new EntityDataAccessor[SLOTS];

	static {
		for (int i = 0; i < SLOTS; i++) {
			CARRIED[i] = SynchedEntityData.defineId(ClearCube.class, EntityDataSerializers.ITEM_STACK);
		}
	}

	private @Nullable UUID held;
	private int sinceSwallow, restUntil;

	public ClearCube(EntityType<? extends ClearCube> type, Level level) {
		super(type, level);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.FOLLOW_RANGE, 16.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		for (EntityDataAccessor<ItemStack> slot : CARRIED) {
			entityData.define(slot, ItemStack.EMPTY);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
		this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0, false));
	}

	/** What it carries, for drawing (client and server). */
	public List<ItemStack> carried() {
		List<ItemStack> out = new ArrayList<>();
		for (EntityDataAccessor<ItemStack> slot : CARRIED) {
			ItemStack s = this.entityData.get(slot);
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	private boolean carry(ItemStack stack) {
		for (EntityDataAccessor<ItemStack> slot : CARRIED) {
			if (this.entityData.get(slot).isEmpty()) {
				this.entityData.set(slot, stack.copyWithCount(1));
				return true;
			}
		}
		return false;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		ItemStack[] pool = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BONE), new ItemStack(Items.SKELETON_SKULL), new ItemStack(Items.IRON_HELMET),
			new ItemStack(Items.ARROW), new ItemStack(Items.STONE_AXE), new ItemStack(Items.GOLD_NUGGET), new ItemStack(Items.BONE), new ItemStack(Items.SHIELD)};
		int n = 2 + this.random.nextInt(3);
		for (int i = 0; i < n; i++) {
			this.carry(pool[this.random.nextInt(pool.length)]);
		}
		return out;
	}

	/** The one it holds, if any. */
	private @Nullable LivingEntity swallowed(ServerLevel level) {
		return this.held != null && level.getEntity(this.held) instanceof LivingEntity l && l.isAlive() ? l : null;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity in = this.swallowed(level);
		if (in == null) {
			if (this.held != null) {
				this.release(level, null);
			}
			this.setLook(EMPTY);
			if (this.tickCount > this.restUntil) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().deflate(0.15), e ->
					(e instanceof Player p && !p.isCreative() && !p.isSpectator() || e instanceof AbstractVillager) && e.isAlive())) {
					this.swallow(level, e);
					break;
				}
			}
			// whatever lies in it is taken in
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox())) {
				if (this.carry(item.getItem())) {
					item.getItem().shrink(1);
					if (item.getItem().isEmpty()) {
						item.discard();
					}
					break;
				}
			}
			return;
		}
		this.setLook(HOLDING);
		this.sinceSwallow++;
		Vec3 mid = this.position().add(0.0, this.getBbHeight() * 0.35, 0.0);
		Vec3 pull = mid.subtract(in.position()).scale(0.5);
		in.setDeltaMovement(pull.x, pull.y * 0.5, pull.z);
		in.syncVelocity = true;
		in.resetFallDistance();
		in.setAirSupply(in.getMaxAirSupply());
		if (this.sinceSwallow % ACID_EVERY == 0) {
			in.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ACID), this), ACID_HARM);
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
				ItemStack s = in.getItemBySlot(slot);
				if (!s.isEmpty() && s.isDamageableItem()) {
					s.setDamageValue(Math.min(s.getMaxDamage(), s.getDamageValue() + WEAR));
				}
			}
			level.sendParticles(ParticleTypes.ITEM_SLIME, in.getX(), in.getY(0.6), in.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
			level.playSound(null, in.getX(), in.getY(), in.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 0.4F, 1.6F);
		}
		boolean escaped = this.sinceSwallow % SAVE_EVERY == 0 && Saves.resist(in, Saves.Kind.GRAB);
		if (escaped || this.sinceSwallow > DIGEST_TICKS) {
			this.release(level, in);
		}
	}

	private void swallow(ServerLevel level, LivingEntity e) {
		this.held = e.getUUID();
		this.sinceSwallow = 0;
		this.setTarget(null);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
	}

	private void release(ServerLevel level, @Nullable LivingEntity in) {
		this.held = null;
		this.restUntil = this.tickCount + REST_TICKS;
		if (in != null) {
			Vec3 out = in.position().subtract(this.position()).normalize().scale(0.7);
			in.setDeltaMovement(out.x, 0.35, out.z);
			in.syncVelocity = true;
			level.playSound(null, in.getX(), in.getY(), in.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 1.3F);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (ItemStack s : this.carried()) {
			this.spawnAtLocation(level, s);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SLIME_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SLIME_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		List<ItemStack> all = this.carried();
		for (int i = 0; i < all.size(); i++) {
			output.store("Carried" + i, ItemStack.CODEC, all.get(i));
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		for (int i = 0; i < SLOTS; i++) {
			int at = i;
			input.read("Carried" + i, ItemStack.CODEC).ifPresent(s -> this.entityData.set(CARRIED[at], s));
		}
	}
}
