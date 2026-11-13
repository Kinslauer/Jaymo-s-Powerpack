package com.jaymoskingdoms.barrow;

import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.beasts.ActingMonster;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * The zombie knight, drawn from yair's Blockbench model with a head of our own (kingdoms/tools/gen_zombie_knight.py).
 * Andy: "this zombie knight needs a different head and should wield a sword from our pack, and correctly." One of the
 * old dead: a household knight buried with his lord to keep the watch with him ({@link Revenant}), a dead villager's
 * face under a rusted kettle helm. He carries a real sword, one of the pack's forged blades (Powers' Tinkers sword; a
 * plain iron one without Powers), drawn in his fist as any held item is, and what it does to you is that blade's own
 * damage. He comes on at a walk and hews overhead. Like his lord he lets pass whoever carries a lantern, as the
 * lantern-keepers do, unless they strike one of the barrow's dead. The helm keeps the sun off him.
 */
public class ZombieKnight extends ActingMonster {
	static final double HEALTH = 30.0, ARMOR = 10.0, DAMAGE = 2.0, SPEED = 0.23, SIGHT = 24.0, KNOCKBACK_RESISTANCE = 0.3;
	/** His blade: its metal and grip (Powers' Tinkers materials). */
	static final String BLADE_METAL = "iron", BLADE_GRIP = "wood";
	/** The hew: the animation's length, the tick it lands, its reach, and the ticks between hews. */
	static final int HEW_TICKS = 20, HEW_LANDS = 11, GAP = 26;
	static final double REACH = 1.7;
	/** The chance he leaves his sword behind. */
	static final float SWORD_DROP = 0.08F;

	private static final String[] ACTIONS = {"attack"};

	private int gap;

	public ZombieKnight(EntityType<? extends ZombieKnight> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, SIGHT).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ZombieKnight.class, Revenant.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> !Revenant.keeper(target)));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		ItemStack blade = PowersCompat.tinkerSword(BLADE_METAL, BLADE_GRIP);
		this.setItemSlot(EquipmentSlot.MAINHAND, blade != null ? blade : new ItemStack(Items.IRON_SWORD));
		this.setDropChance(EquipmentSlot.MAINHAND, SWORD_DROP);
		return data;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && Revenant.ally(target) ? null : target);
	}

	@Override
	protected boolean strikes(LivingEntity e) {
		return !Revenant.ally(e) && super.strikes(e);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.gap > 0) {
			this.gap--;
		}
		if (this.acting("attack")) {
			if (this.actionRun() == HEW_LANDS) {
				// (the attribute carries the blade's own damage)
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), REACH, true, 0.4, 0.0);
				this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 0.8F);
			}
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		this.getNavigation().moveTo(target, 1.0);
		if (this.gap <= 0 && this.distanceTo(target) < REACH + target.getBbWidth() / 2.0) {
			this.gap = GAP;
			this.act("attack", HEW_TICKS);
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (his blows are his hews)
		return false;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ZOMBIE_VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ZOMBIE_VILLAGER_DEATH;
	}
}
