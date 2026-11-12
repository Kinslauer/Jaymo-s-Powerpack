package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BladeBlood;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One of the Unseeing, Andy (2026-11-11): "a feral cult, that believes in nonsense ... they do the blindfold ritual, but
 * they also do the eye gouging, both the bandage and blindfold need to be visible, and the zealous unseeing will attack
 * you when they go blind, as they start to lose their mind. Villagers who have done this start to get shunned by
 * others." Villagers once, shunned for it; drawn as robed folk of their own (MCA draws the pack's villagers, so a
 * blindfold couldn't be laid on one), in one of four looks ({@link Look}): a follower (eyes still theirs), blindfolded,
 * gouged (linen bound over the sockets) and zealous (the linen soaked through).
 * <ul>
 * <li>They can't see, so they find you by ear: only within {@link #HEAR} blocks, and a sneaking player only within
 * {@link #HEAR_SNEAKING}.</li>
 * <li>The gouged lose their minds in time: {@link #MADNESS_MIN}-{@link #MADNESS_MAX} ticks after the cut they turn
 * zealous, and a zealot goes for anyone it hears who hasn't taken the dark on themselves (a blindfolded or eyeless
 * player is left alone, {@link BlindnessDevotion#devout}). Strike any of them and they strike back.</li>
 * <li>The sane ones talk (right-click): a line of their creed. They never trade.</li>
 * </ul>
 */
public class Unseeing extends PathfinderMob {
	static final double HEALTH = 20.0, DAMAGE = 3.0, ZEALOT_DAMAGE = 5.0, SPEED = 0.25;
	/** How far they hear a player, and a sneaking one (blocks). */
	static final double HEAR = 10.0, HEAR_SNEAKING = 3.0;
	/** A zealot's Pulse: from how near to how far it screams (blocks), what it does, how hard it throws, how often (ticks). */
	static final double PULSE_MIN = 2.5, PULSE_RANGE = 7.0, PULSE_PUSH = 0.9;
	static final float PULSE_DAMAGE = 4.0F;
	static final int PULSE_EVERY = 100;
	/** Ticks after the gouging before madness takes a gouged one (a Minecraft day or two). */
	static final int MADNESS_MIN = 24000, MADNESS_MAX = 48000;
	/** How many creed lines there are (lang message.jaymospowers.unseeing.creed.N). */
	static final int CREED_LINES = 8;

	public enum Look {
		FOLLOWER, BLINDFOLD, GOUGED, ZEALOUS;

		static Look byIndex(int i) {
			Look[] all = values();
			return all[Math.floorMod(i, all.length)];
		}
	}

	public static EntityType<Unseeing> TYPE;

	private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Unseeing.class, EntityDataSerializers.INT);
	/** Ticks left till madness (gouged only; -1 for none). */
	private int madness = -1;
	private boolean elder;
	/** The tick (of its own life) a zealot may next Pulse. */
	private int pulseAt;

	public Unseeing(EntityType<? extends Unseeing> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("unseeing"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Unseeing>of(Unseeing::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, HEAR * 2.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(LOOK, Look.BLINDFOLD.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Unseeing.class).setAlertOthers());
		// a zealot hunts by ear: no sight needed, and the devout are its own
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
			(target, level) -> this.look() == Look.ZEALOUS && heard(target)));
	}

	/** Whether this one would hear {@code target} now. */
	private boolean heard(net.minecraft.world.entity.LivingEntity target) {
		if (!(target instanceof Player player) || player.isCreative() || player.isSpectator() || BlindnessDevotion.devout(player)) {
			return false;
		}
		double reach = player.isShiftKeyDown() ? HEAR_SNEAKING : HEAR;
		return distanceToSqr(player) <= reach * reach;
	}

	public Look look() {
		return Look.byIndex(this.entityData.get(LOOK));
	}

	public boolean elder() {
		return this.elder;
	}

	void setElder(boolean elder) {
		this.elder = elder;
	}

	/** Sets the look; a fresh gouging starts the clock to madness. */
	public void setLook(Look look) {
		this.entityData.set(LOOK, look.ordinal());
		if (look == Look.GOUGED && this.madness < 0) {
			this.madness = MADNESS_MIN + getRandom().nextInt(MADNESS_MAX - MADNESS_MIN + 1);
		} else if (look != Look.GOUGED) {
			this.madness = -1;
		}
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(look == Look.ZEALOUS ? ZEALOT_DAMAGE : DAMAGE);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level) || isNoAi()) {
			return;
		}
		if (this.madness > 0 && --this.madness == 0) {
			setLook(Look.ZEALOUS);
			say(level, "message.jaymospowers.unseeing.madness", ChatFormatting.DARK_RED);
		}
		if (look() == Look.ZEALOUS) {
			pulse(level);
		}
		if (look() == Look.ZEALOUS && this.tickCount % 60 == 0 && getRandom().nextFloat() < 0.3F) {
			// the linen never dries
			BladeBlood.drip(level, this, 0.85, false);
		}
	}

	/**
	 * A zealot fights by sound: with its quarry near but out of arm's reach it screams a Pulse at it, as a Resonance
	 * holder would (hurt, thrown back, a raised shield or a meal knocked down).
	 */
	private void pulse(ServerLevel level) {
		net.minecraft.world.entity.LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || this.tickCount < this.pulseAt) {
			return;
		}
		double distance = distanceTo(target);
		if (distance < PULSE_MIN || distance > PULSE_RANGE || !hasLineOfSight(target)) {
			return;
		}
		this.pulseAt = this.tickCount + PULSE_EVERY;
		net.minecraft.world.phys.Vec3 eye = getEyePosition();
		net.minecraft.world.phys.Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
		net.minecraft.world.phys.Vec3 way = to.normalize();
		getLookControl().setLookAt(target);
		if (target.hurtServer(level, level.damageSources().mobAttack(this), PULSE_DAMAGE)) {
			target.push(way.x * PULSE_PUSH, 0.3, way.z * PULSE_PUSH);
			target.syncVelocity = true;
			com.jaymospowers.power.resonance.ResonancePulse.interrupt(target);
		}
		for (double d = 1.5; d <= distance; d += 2.0) {
			com.jaymospowers.core.fx.MagicFx.ring(level, eye.add(way.scale(d)), way, (float) (0.4 + d * 0.3),
				com.jaymospowers.power.resonance.ResonancePower.COLOR, com.jaymospowers.core.fx.MagicFx.RING_TICKS);
		}
		level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 1.5F);
		level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK, net.minecraft.sounds.SoundSource.HOSTILE, 0.5F, 1.6F);
	}

	/** A line to everyone within earshot, under this one's name. */
	void say(ServerLevel level, String key, ChatFormatting colour) {
		Component line = Component.translatable("message.jaymospowers.blindness.service.says", getDisplayName(), Component.translatable(key)).withStyle(colour);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24.0))) {
			player.sendSystemMessage(line);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && player instanceof ServerPlayer server) {
			String key = isNoAi() ? "message.jaymospowers.blindness.service.hush"
				: look() == Look.ZEALOUS ? "message.jaymospowers.unseeing.zealot"
				: "message.jaymospowers.unseeing.creed." + (1 + getRandom().nextInt(CREED_LINES));
			server.sendSystemMessage(isNoAi() ? Component.translatable(key, getDisplayName()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
				: Component.translatable("message.jaymospowers.blindness.service.says", getDisplayName(), Component.translatable(key)).withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getTypeName() {
		return Component.translatable(this.elder ? "entity.jaymospowers.unseeing.elder" : switch (look()) {
			case FOLLOWER -> "entity.jaymospowers.unseeing.follower";
			case ZEALOUS -> "entity.jaymospowers.unseeing.zealot";
			default -> "entity.jaymospowers.unseeing";
		});
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("look", look().ordinal());
		output.putInt("madness", this.madness);
		output.putBoolean("elder", this.elder);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(LOOK, Look.byIndex(input.getIntOr("look", Look.BLINDFOLD.ordinal())).ordinal());
		this.madness = input.getIntOr("madness", -1);
		this.elder = input.getBooleanOr("elder", false);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(look() == Look.ZEALOUS ? ZEALOT_DAMAGE : DAMAGE);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return look() == Look.ZEALOUS ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.75F + getRandom().nextFloat() * 0.1F;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
