package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.125, Xeno-Band patch 8) The band hunter changed: one of the rival band's darkened aliens, with that alien's size,
 * life, fists and pace ({@link XenoForm}), fighting whoever wears the Xeno-Band (and anyone who hits it). The hunter
 * himself goes unseen inside it ({@link RivalHunter}); when it falls, or its time runs out, he's back, dazed: beaten.
 * Never saved (a reload ends the change).
 */
public class RivalAlien extends PathfinderMob {
	// ---- tuning ----
	/** How long a change lasts (ticks) before he's back in his own body. */
	public static final int LIFE = 600;
	/** Its share of the alien's life and fists (the hunter's band is an older, cruder one). */
	public static final double HEALTH_SHARE = 1.2, DAMAGE_SHARE = 1.0;
	/** Its walking pace before the alien's own share is added, and the chase. */
	public static final double BASE_SPEED = 0.28, CHASE_SPEED = 1.25;

	public static EntityType<RivalAlien> TYPE;

	private static final EntityDataAccessor<String> DATA_LOOK = SynchedEntityData.defineId(RivalAlien.class, EntityDataSerializers.STRING);

	private @Nullable UUID hunter;
	private int age;

	public RivalAlien(EntityType<? extends RivalAlien> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("rival_alien"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<RivalAlien>of(RivalAlien::new, MobCategory.MISC)
				.sized(0.6F, 1.8F)
				.eyeHeight(1.62F)
				.clientTrackingRange(10)
				.noLootTable()
				.build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.SCALE, 1.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
			.add(Attributes.STEP_HEIGHT, 0.6);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, "gyre");
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, CHASE_SPEED, true));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false,
			(LivingEntity e, ServerLevel level) -> e instanceof Player p && XenoformPower.has(p) && !XenoformState.rival(p)));
	}

	/** Made {@code form} for {@code hunter}: its size, life, fists and pace. */
	void become(XenoForm form, UUID hunter) {
		this.hunter = hunter;
		this.entityData.set(DATA_LOOK, form.id);
		base(Attributes.MAX_HEALTH, form.health * HEALTH_SHARE);
		base(Attributes.ATTACK_DAMAGE, form.damage * DAMAGE_SHARE);
		base(Attributes.MOVEMENT_SPEED, BASE_SPEED * (1.0 + form.speed));
		base(Attributes.SCALE, form.scale);
		base(Attributes.KNOCKBACK_RESISTANCE, form.knockbackResist);
		base(Attributes.STEP_HEIGHT, 0.6 + form.stepUp);
		this.setHealth(this.getMaxHealth());
		this.refreshDimensions();
	}

	private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = this.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	/** The alien it's drawn as. */
	public String look() {
		return this.entityData.get(DATA_LOOK);
	}

	public @Nullable UUID hunter() {
		return this.hunter;
	}

	/** Whether its time is up. */
	boolean spent() {
		return this.age >= LIFE;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			this.age++;
			if (this.hunter == null || this.spent()) {
				// its time's up: the band lets go, and the hunter takes it from here
				level.sendParticles(ParticleTypes.GLOW, this.getX(), this.getY(0.5), this.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.8F, 0.7F);
				this.discard();
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/** A change is a moment's: never written to disk. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
