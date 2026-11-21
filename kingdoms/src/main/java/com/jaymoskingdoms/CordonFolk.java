package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
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
 * (Awakening pick) One of the Cordon ({@link Cordon}), by rank (its entity type), drawn by the client's {@code CordonRenderer}
 * in the sect's ash-grey robes with the black band of the blindfolded over their eyes:
 * <ul>
 * <li><b>Sentinel</b>: a spear, and the patrol's numbers.</li>
 * <li><b>Stalker</b>: unseen until it is within {@link #REVEAL} blocks of its quarry or is struck; its knife lays Rot
 * ({@link AshRot}).</li>
 * <li><b>Enforcer</b>: its blows bind the struck one's gift for {@link #SEAL_TICKS} ticks (Jaymo's Powers' seal: no power, no
 * form).</li>
 * <li><b>Overseer</b>: leads the patrol; once badly hurt it calls the others on ({@link #RALLY_RANGE}); it carries a summons
 * to the Court of Judgment.</li>
 * <li><b>Arbiter</b> ({@code aw-adapt}): the judge in the court, with a boss bar. It learns the harm dealt it, by kind
 * ({@link CaptainWays.Cause}: fire, shot, blunt, blade, magic, beast): each kind dealt is taken less and less, down to
 * {@link #MOST_RESIST} once {@link #LEARN_FULL} of it has been dealt. What it soaks builds up in it; at {@link #OVERLOAD_AT}
 * it overloads: a burst of binding red that throws back and hurts those near, then it staggers {@link #STAGGER_TICKS}
 * ticks, unable to strike and taking {@link #STAGGERED_TAKEN} times the harm, and forgets everything it had learned.</li>
 * </ul>
 */
public class CordonFolk extends Monster {
	// ---- tuning ----
	/** How near a stalker comes before it is seen (blocks), and how many Rot stacks its blow lays. */
	static final double REVEAL = 6.0;
	static final int STALK_ROT = 2;
	/** How long an enforcer's blow (and the Arbiter's) binds a gift (ticks). */
	static final int SEAL_TICKS = 100, ARBITER_SEAL_TICKS = 60;
	/** The Overseer's rally: below this share of health, the Cordon within range strike harder for a while. */
	static final float RALLY_AT = 0.5F;
	static final double RALLY_RANGE = 16.0;
	static final int RALLY_TICKS = 400;
	/** The Arbiter: harm of one kind it must take to learn it fully, the least share of it it then takes. */
	static final float LEARN_FULL = 60.0F, MOST_RESIST = 0.75F;
	/** What it soaks before it overloads, the burst's reach and harm, the stagger, and the harm it takes while staggered. */
	static final float OVERLOAD_AT = 80.0F, BURST_RANGE = 6.0F, BURST_HARM = 8.0F, STAGGERED_TAKEN = 2.0F;
	static final int STAGGER_TICKS = 100;

	private Cordon.@Nullable Rank rank;
	/** The Arbiter's court (packed position), 0 for none. */
	private long court;
	/** The Arbiter: harm learned of each kind, what it has soaked, and when its stagger ends (game time). */
	private final float[] learned = new float[CaptainWays.Cause.values().length];
	private float soaked;
	private long staggeredUntil;
	private boolean rallied;
	private @Nullable ServerBossEvent bar;

	public CordonFolk(EntityType<? extends CordonFolk> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	static AttributeSupplier.Builder attributes(Cordon.Rank rank) {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, rank.health).add(Attributes.ATTACK_DAMAGE, rank.damage)
			.add(Attributes.ARMOR, rank.armour).add(Attributes.MOVEMENT_SPEED, rank.speed).add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, rank == Cordon.Rank.ARBITER ? 0.8 : 0.2);
	}

	public Cordon.Rank rank() {
		if (this.rank == null) {
			Cordon.Rank r = Cordon.rank(this.getType());
			this.rank = r == null ? Cordon.Rank.SENTINEL : r;
		}
		return this.rank;
	}

	void setCourt(long court) {
		this.court = court;
	}

	/** Whether the Arbiter is staggered now (it has overloaded). */
	public boolean staggered() {
		return this.level().getGameTime() < this.staggeredUntil;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, CordonFolk.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> target instanceof Player player && Cordon.heretic(player)));
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		ItemStack held = switch (this.rank()) {
			case SENTINEL -> new ItemStack(Items.IRON_SPEAR);
			case STALKER -> new ItemStack(Items.IRON_SWORD);
			case ENFORCER -> new ItemStack(Items.IRON_AXE);
			case OVERSEER -> new ItemStack(Items.IRON_SWORD);
			case ARBITER -> new ItemStack(Items.NETHERITE_SPEAR);
		};
		this.setItemSlot(EquipmentSlot.MAINHAND, held);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.05F);
		if (this.rank() == Cordon.Rank.ENFORCER || this.rank() == Cordon.Rank.OVERSEER) {
			this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			this.setDropChance(EquipmentSlot.CHEST, 0.0F);
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		return data;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (this.rank() == Cordon.Rank.STALKER && this.tickCount % 10 == 0) {
			boolean seen = target != null && this.distanceTo(target) <= REVEAL || this.hurtTime > 0;
			if (seen) {
				this.removeEffect(MobEffects.INVISIBILITY);
			} else {
				this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
			}
		}
		if (this.rank() == Cordon.Rank.ARBITER) {
			if (this.staggered()) {
				this.getNavigation().stop();
				if (this.tickCount % 10 == 0) {
					level.sendParticles(Fx.spark(Cordon.BINDING, 1.0F), this.getX(), this.getY(0.7), this.getZ(), 4, 0.4, 0.5, 0.4, 0.05);
				}
			}
			if (this.tickCount % 10 == 0) {
				if (this.bar == null) {
					this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
				}
				this.bar.setProgress(this.getHealth() / this.getMaxHealth());
				BossBars.show(level, this, this.bar);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (this.rank() == Cordon.Rank.ARBITER && this.staggered()) {
			return false;
		}
		boolean hit = super.doHurtTarget(level, target);
		if (!hit || !(target instanceof LivingEntity living)) {
			return hit;
		}
		switch (this.rank()) {
			case STALKER -> AshRot.rot(living, this, STALK_ROT);
			case ENFORCER -> bind(level, living, SEAL_TICKS);
			case ARBITER -> {
				bind(level, living, ARBITER_SEAL_TICKS);
				AshRot.rot(living, this, 1);
			}
			default -> {
			}
		}
		return hit;
	}

	/** Binds {@code living}'s gift (powers and forms refused) for {@code ticks}; the bound player is told on the action bar. */
	private void bind(ServerLevel level, LivingEntity living, int ticks) {
		PowersCompat.seal(living, ticks);
		level.sendParticles(Fx.rune(Cordon.BINDING, 0.8F), living.getX(), living.getY(0.6), living.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		if (living instanceof ServerPlayer player && PowersCompat.power(player) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cordon.bound", ticks / 20));
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.rank() == Cordon.Rank.ARBITER) {
			damage = this.judge(level, source, damage);
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.rank() == Cordon.Rank.STALKER) {
			this.removeEffect(MobEffects.INVISIBILITY);
		}
		if (hurt && this.rank() == Cordon.Rank.OVERSEER && !this.rallied && this.getHealth() <= this.getMaxHealth() * RALLY_AT) {
			this.rallied = true;
			Speech.say(level, this, Component.translatable("speech.jaymoskingdoms.cordon.rally"), Speech.RED, 32.0);
			for (CordonFolk other : level.getEntitiesOfClass(CordonFolk.class, this.getBoundingBox().inflate(RALLY_RANGE))) {
				other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, 0));
				if (other.getTarget() == null && this.getTarget() != null) {
					other.setTarget(this.getTarget());
				}
			}
		}
		return hurt;
	}

	/** The Arbiter weighs a blow: the harm it lets through, after what it has learned; it may overload. */
	private float judge(ServerLevel level, DamageSource source, float damage) {
		if (this.staggered()) {
			return damage * STAGGERED_TAKEN;
		}
		CaptainWays.Cause cause = CaptainWays.cause(source);
		if (cause == null || damage <= 0.0F) {
			return damage;
		}
		int i = cause.ordinal();
		float resist = MOST_RESIST * Math.min(1.0F, this.learned[i] / LEARN_FULL);
		float through = damage * (1.0F - resist);
		boolean knewIt = this.learned[i] >= LEARN_FULL;
		this.learned[i] += damage;
		this.soaked += damage - through;
		if (!knewIt && this.learned[i] >= LEARN_FULL) {
			Speech.say(level, this, Component.translatable("speech.jaymoskingdoms.cordon.learned." + cause.name().toLowerCase(java.util.Locale.ROOT)),
				Speech.RED, 32.0);
		}
		if (this.soaked >= OVERLOAD_AT) {
			this.overload(level);
		}
		return through;
	}

	/** Too much learned and held: it bursts, staggers and forgets. */
	private void overload(ServerLevel level) {
		this.soaked = 0.0F;
		java.util.Arrays.fill(this.learned, 0.0F);
		this.staggeredUntil = level.getGameTime() + STAGGER_TICKS;
		this.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 5, false, false));
		Vec3 at = this.position().add(0.0, this.getBbHeight() * 0.5, 0.0);
		Fx.blast(level, at, BURST_RANGE, Cordon.BINDING);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(BURST_RANGE),
			e -> e != this && !(e instanceof CordonFolk) && e.isAlive())) {
			near.hurtServer(level, this.damageSources().indirectMagic(this, this), BURST_HARM);
			Vec3 away = near.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize().scale(1.2);
			near.push(away.x, 0.4, away.z);
			near.syncVelocity = true;
		}
		Speech.say(level, this, Component.translatable("speech.jaymoskingdoms.cordon.overload"), Speech.RED, 32.0);
	}

	@Override
	public void die(DamageSource source) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		if (this.level() instanceof ServerLevel level) {
			if (this.rank() == Cordon.Rank.OVERSEER) {
				this.spawnAtLocation(level, new ItemStack(Cordon.SUMMONS));
			}
			if (this.rank() == Cordon.Rank.ARBITER && this.court != 0L) {
				Cordon.judged(level, this.court);
			}
		}
		super.die(source);
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
	public boolean removeWhenFarAway(double distSqr) {
		return this.court == 0L && this.rank() != Cordon.Rank.ARBITER && super.removeWhenFarAway(distSqr);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("court", this.court);
		output.putFloat("soaked", this.soaked);
		int[] packed = new int[this.learned.length];
		for (int i = 0; i < packed.length; i++) {
			packed[i] = Float.floatToIntBits(this.learned[i]);
		}
		output.putIntArray("learned", packed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.court = input.getLongOr("court", 0L);
		this.soaked = input.getFloatOr("soaked", 0.0F);
		input.getIntArray("learned").ifPresent(packed -> {
			for (int i = 0; i < Math.min(packed.length, this.learned.length); i++) {
				this.learned[i] = Float.intBitsToFloat(packed[i]);
			}
		});
	}
}
