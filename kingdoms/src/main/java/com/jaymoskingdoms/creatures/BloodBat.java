package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Saves;
import com.jaymoskingdoms.Vampires;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A blood bat (the old stirge): a small flier that comes out at night and in the dark, latches on to something warm-blooded
 * (a player, a villager, a grazing animal), and drinks. Each sip bleeds its host a little and fills the bat's belly; when it is
 * full it lets go and flies off fat and slow, low and heavy, and goes to roost to digest, and until it has it hunts nothing.
 * Its bites do not close by themselves: they bleed ({@link PowersCompat#cut}), and a trail of that blood can be followed.
 * <ul>
 * <li><b>Pulled off or swatted</b>: any blow on a latched bat knocks it loose, and so does a tug of the empty hand
 * ({@link Latches}): a try that Might helps, which tears the bite when it fails.</li>
 * <li><b>Sympathy</b>: it behaves as a bat does: roosts by day, hunts the dark, and is as happy with a cow as a person.</li>
 * <li><b>Feedback</b>: a full bat is slow and easy to kill, and the more it drinks the longer it sleeps, so the world is
 * not thick with them.</li>
 * <li><b>Stewardship</b>: raw meat held out to a hungry bat feeds it, and it will not hunt the one who fed it for a day.</li>
 * </ul>
 * The vampire bats of the Nightfall ({@link com.jaymoskingdoms.nightfall.VampireBat}) work the same way.
 */
public class BloodBat extends Bat implements Clinger {
	static final double HEALTH = 6.0;
	/** How far it looks for something to drink from (blocks). */
	static final double HUNT_RANGE = 16.0;
	/** Cruising speed hunting and the share of it a full belly leaves. */
	static final double HUNT_SPEED = 0.45, SLOWEST = 0.3;
	/** Latched on: a sip this often (ticks), the harm of a sip, the belly it fills, and the longest it clings (ticks). */
	static final int SIP_EVERY = 20, CLING_TICKS = 20 * 12;
	static final float SIP_HARM = 1.0F, SIP_FILLS = 0.18F;
	/** The chance a sip opens a bleeding wound. */
	static final float BITE_BLEEDS = 0.6F;
	/** Full at this belly: it lets go. It hunts again under {@link #HUNGRY}. A tick of digesting takes this much. */
	static final float FULL = 1.0F, HUNGRY = 0.35F, DIGEST = 1.0F / (20 * 90);
	/** Pulled off or swatted, it is dazed this long (ticks). */
	static final int DAZE_TICKS = 40;
	/** Fed raw meat: the belly it gains, and how long (ticks) it leaves the one who fed it alone. */
	static final float FED_FILLS = 0.5F;
	static final long GRATEFUL_TICKS = 24000L;
	/** A pull of the empty hand: the base chance (Might adds to it). */
	static final float PULL_BASE = 0.4F;

	private static final EntityDataAccessor<Float> BELLY = SynchedEntityData.defineId(BloodBat.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> CLINGING = SynchedEntityData.defineId(BloodBat.class, EntityDataSerializers.BOOLEAN);

	protected @Nullable UUID preyId;
	private int cling = -1, dazed;
	private @Nullable UUID friend;
	private long friendUntil;

	public BloodBat(EntityType<? extends BloodBat> type, Level level) {
		super(type, level);
		this.xpReward = 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Bat.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, SIP_HARM);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(BELLY, 0.0F);
		entityData.define(CLINGING, false);
	}

	/** How full its belly is, 0 to 1. */
	public float belly() {
		return this.entityData.get(BELLY);
	}

	protected void setBelly(float belly) {
		this.entityData.set(BELLY, Math.max(0.0F, Math.min(1.0F, belly)));
	}

	/** Whether it is latched on and drinking. */
	public boolean latched() {
		return this.entityData.get(CLINGING);
	}

	/** Who it is clinging to, if anyone (server side). */
	@Override
	public @Nullable LivingEntity host(ServerLevel level) {
		return this.latched() && this.preyId != null && level.getEntity(this.preyId) instanceof LivingEntity l ? l : null;
	}

	/** Whether it is hungry enough to hunt (or already at it). */
	protected boolean hunting() {
		return this.belly() < HUNGRY || this.latched();
	}

	/** Whether it may go after {@code e}: warm-blooded and not its own kind or the undead, and not the one who fed it. */
	protected boolean huntable(LivingEntity e) {
		if (!e.isAlive() || e.isBaby() && !(e instanceof Player) || e instanceof Bat || e.is(EntityTypeTags.UNDEAD) || e.is(EntityTypeTags.ARTHROPOD)
			|| Vampires.isVampire(e) || e instanceof Monster) {
			return false;
		}
		if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return false;
		}
		return !(this.friend != null && this.friend.equals(e.getUUID()) && this.level().getGameTime() < this.friendUntil);
	}

	/** A tug of the empty hand by {@code by} at this bat (see {@link Latches}). Returns whether it came off. */
	@Override
	public boolean pulled(ServerLevel level, ServerPlayer by) {
		float chance = Math.min(0.95F, PULL_BASE + Saves.chance(by, Saves.Kind.GRAB));
		if (by.getRandom().nextFloat() < chance) {
			this.knockedLoose(level, true);
			return true;
		}
		// it hangs on, and the tug tears at the bite
		LivingEntity host = this.host(level);
		if (host != null) {
			host.hurtServer(level, level.damageSources().mobAttack(this), SIP_HARM);
			PowersCompat.cut(host);
		}
		return false;
	}

	/** It comes off its host (dazed), tearing the bite if it was pulled. */
	protected void knockedLoose(ServerLevel level, boolean torn) {
		LivingEntity host = this.host(level);
		this.entityData.set(CLINGING, false);
		this.cling = -1;
		this.dazed = DAZE_TICKS;
		if (host != null) {
			if (torn) {
				PowersCompat.cut(host);
			}
			Vec3 away = this.position().subtract(host.position()).normalize().scale(0.4);
			this.setDeltaMovement(away.x, 0.2, away.z);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAT_HURT, SoundSource.HOSTILE, 0.8F, 1.4F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean latched = this.latched();
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && latched && this.isAlive()) {
			this.knockedLoose(level, false);
		}
		return hurt;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(ItemTags.MEAT) && this.belly() < HUNGRY && !this.latched()) {
			if (this.level() instanceof ServerLevel level) {
				held.consume(1, player);
				this.setBelly(this.belly() + FED_FILLS);
				this.friend = player.getUUID();
				this.friendUntil = level.getGameTime() + GRATEFUL_TICKS;
				level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.4, this.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAT_AMBIENT, SoundSource.NEUTRAL, 0.7F, 1.5F);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// digesting, a little each tick
		if (this.belly() > 0.0F && !this.latched()) {
			this.setBelly(this.belly() - DIGEST);
		}
		if (this.dazed > 0) {
			this.dazed--;
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, 0.7, 0.7).add(0.0, -0.03, 0.0));
			return;
		}
		LivingEntity prey = this.hunting() ? this.prey(level) : null;
		if (prey == null) {
			this.entityData.set(CLINGING, false);
			this.cling = -1;
			super.customServerAiStep(level);
			this.slowFlight();
			return;
		}
		this.setResting(false);
		if (this.latched()) {
			this.drink(level, prey);
			return;
		}
		Vec3 to = prey.getEyePosition().add(0.0, -0.3, 0.0).subtract(this.position());
		double d = to.length();
		Vec3 dir = d < 1.0E-4 ? Vec3.ZERO : to.scale(1.0 / d);
		Vec3 v = this.getDeltaMovement();
		this.setDeltaMovement(v.add(dir.scale(HUNT_SPEED).subtract(v).scale(0.25)));
		this.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90.0F);
		this.zza = 0.5F;
		if (d < 1.2 && this.getBoundingBox().inflate(0.3).intersects(prey.getBoundingBox())) {
			this.entityData.set(CLINGING, true);
			this.cling = 0;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 0.6F, 0.6F);
		}
	}

	/** A full bat drags: its flight is slowed by how heavy its belly is. */
	private void slowFlight() {
		float b = this.belly();
		if (b > 0.0F) {
			double keep = 1.0 - (1.0 - SLOWEST) * b;
			Vec3 v = this.getDeltaMovement();
			this.setDeltaMovement(v.x * keep, v.y * keep - 0.01 * b, v.z * keep);
		}
	}

	/** Clings on, drinks, and lets go when it is full (or has clung long enough). */
	private void drink(ServerLevel level, LivingEntity prey) {
		this.cling++;
		Vec3 back = Vec3.directionFromRotation(0.0F, prey.getYRot()).scale(-0.25);
		Vec3 side = Vec3.directionFromRotation(0.0F, prey.getYRot() + 90.0F).scale(0.3);
		this.setPos(prey.getX() + back.x + side.x, prey.getEyeY() - 0.5, prey.getZ() + back.z + side.z);
		this.setDeltaMovement(Vec3.ZERO);
		if (this.cling % SIP_EVERY == 0) {
			if (prey.hurtServer(level, level.damageSources().mobAttack(this), SIP_HARM)) {
				this.setBelly(this.belly() + SIP_FILLS);
				if (this.random.nextFloat() < BITE_BLEEDS) {
					PowersCompat.cut(prey);
				}
				level.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), prey.getX(), prey.getEyeY() - 0.3, prey.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HONEY_DRINK, SoundSource.HOSTILE, 0.5F, 1.6F);
			}
		}
		if (this.belly() >= FULL || this.cling > CLING_TICKS || !prey.isAlive()) {
			// full: it lets go and drifts off, fat and slow
			this.entityData.set(CLINGING, false);
			this.cling = -1;
			this.setDeltaMovement(new Vec3(this.random.nextDouble() - 0.5, 0.4, this.random.nextDouble() - 0.5).scale(0.25));
			this.preyId = null;
		}
	}

	/** Its prey, kept or found. */
	protected @Nullable LivingEntity prey(ServerLevel level) {
		if (this.preyId != null && level.getEntity(this.preyId) instanceof LivingEntity kept && kept.isAlive() && kept.distanceTo(this) < HUNT_RANGE * 2
			&& this.huntable(kept)) {
			return kept;
		}
		this.preyId = null;
		if (!this.mayHunt(level) || this.tickCount % 10 != 0) {
			return null;
		}
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(this.blockPosition()).inflate(HUNT_RANGE),
			e -> this.huntable(e) && this.hasLineOfSight(e));
		LivingEntity best = null;
		for (LivingEntity e : near) {
			if (best == null || e.distanceToSqr(this) < best.distanceToSqr(this)) {
				best = e;
			}
		}
		if (best != null) {
			this.preyId = best.getUUID();
		}
		return best;
	}

	/** When it hunts: in the dark, or at night. */
	protected boolean mayHunt(ServerLevel level) {
		return !level.isBrightOutside() || level.getMaxLocalRawBrightness(this.blockPosition()) <= 7;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("Belly", this.belly());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setBelly(input.getFloatOr("Belly", 0.0F));
	}
}
