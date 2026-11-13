package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The thing under the wreck, the Deep Mother's own: sailors speak round it and never name it. It lies under some of the
 * treasure wrecks ({@link #wake}: each wreck is looked at once, when somebody first comes near, and about one in
 * {@link #WAKE_ODDS} has it).
 * <p>
 * It is a body with its arms standing up from it, and it never leaves the water ({@link #swim}): it keeps itself
 * {@link #HANG} blocks under its quarry, so everything it does comes up from below, at whoever is in the column over it
 * ({@link #over}), swimmer, boat or deck.
 * <ul>
 * <li>The lash: its arms open, then clap shut over it ({@link #LASH_DAMAGE}, with a tell).</li>
 * <li>The grab: an arm takes its quarry and draws them down to it, squeezing ({@link #SQUEEZE_DAMAGE} every
 * {@link #SQUEEZE_EVERY} ticks) and taking their breath, until the hold runs out or it is hurt {@link #GRAB_BREAK}
 * while it holds them. Somebody in a boat is pulled out of it; a ship built of blocks is held fast by her hull
 * ({@code Ship.holdFast}) while it reaches for whoever is on her deck.</li>
 * <li>The ink: at each of {@link #INK_AT} of its health it lets go, blinds everyone near in a cloud and darts off.</li>
 * </ul>
 * Its quarry out of reach for {@link #GIVE_UP} ticks (ashore, say), it goes back to where it lay. It leaves the clinging
 * coil and its teeth (Powers' items, when Powers is there).
 */
public class WreckThing extends ActingMonster {
	static final double HEALTH = 320.0, ARMOR = 4.0;
	/** Its pace through the water and darting off behind its ink (blocks a tick), and how far under its quarry it keeps. */
	static final double SWIM = 0.24, DART = 0.75, HANG = 5.0;
	/** The column over it that its arms reach: how wide and how high. */
	static final double REACH = 4.2, REACH_UP = 12.0;
	/** The lash: the tell and the whole move (ticks), how often, its harm and how hard it throws upward. */
	static final int LASH_TELL = 14, LASH_TICKS = 26, LASH_EVERY = 48;
	static final float LASH_DAMAGE = 11.0F;
	static final double LASH_UP = 0.5;
	/** The grab: the tell and the whole move (ticks), how often, the squeeze, the breath it takes a tick, what breaks it, where it holds them. */
	static final int GRAB_TELL = 14, GRAB_TICKS = 90, GRAB_MISSED = 14, GRAB_EVERY = 170, SQUEEZE_EVERY = 15;
	static final float SQUEEZE_DAMAGE = 4.0F, GRAB_BREAK = 14.0F;
	static final int AIR_DRAIN = 4;
	static final double HOLD_ABOVE = 3.4, PULL = 0.2;
	/** The ink: the shares of its health that set it off, how long it darts (ticks), how far the cloud reaches and how long it blinds. */
	static final float[] INK_AT = {0.5F, 0.2F};
	static final int INK_TICKS = 36, INK_BLIND = 20 * 7;
	static final double INK_RANGE = 9.0;
	/** How long its quarry may stay out of reach before it goes back (ticks). */
	static final int GIVE_UP = 20 * 14;
	/** Waking: how near a wreck somebody must come (blocks), and one wreck in this many has it. */
	static final double WAKE_RANGE = 28.0;
	static final int WAKE_ODDS = 3, WAKE_TRIES = 10;
	private static final Identifier COIL = Identifier.fromNamespaceAndPath("jaymospowers", "clinging_coil"),
		TOOTH = Identifier.fromNamespaceAndPath("jaymospowers", "deep_tooth");

	/** Its moves, synced by their place here (the client's WreckThingModel poses each). */
	public static final String[] ACTIONS = {"lash", "grab", "ink"};

	/** On the wrecks' level: the wrecks already looked at (packed places). */
	public static final AttachmentType<List<Long>> CHECKED = AttachmentRegistry.create(JaymosKingdoms.id("wrecks_checked"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private @Nullable Vec3 home;
	private int lashIn = LASH_EVERY, grabIn = GRAB_EVERY / 2, lost, inks;
	private int heldId = -1, heldBoat = -1;
	private float grabHurt;
	private @Nullable ServerBossEvent bar;

	public WreckThing(EntityType<? extends WreckThing> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 30.0).add(Attributes.ATTACK_DAMAGE, LASH_DAMAGE);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	// ---- waking --------------------------------------------------------------------------------------------------------

	/**
	 * (TreasureWrecks, every few seconds) {@code laid} are the level's treasure wrecks: each is looked at once, when
	 * somebody first comes near it, and one in {@link #WAKE_ODDS} has the thing lying beside it.
	 */
	public static void wake(ServerLevel level, List<Long> laid) {
		if (laid.isEmpty() || Beasts.WRECK_THING == null) {
			return;
		}
		List<Long> checked = level.getAttachedOrElse(CHECKED, List.of());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			for (long packed : laid) {
				BlockPos wreck = BlockPos.of(packed);
				double dx = wreck.getX() - player.getX(), dz = wreck.getZ() - player.getZ();
				if (dx * dx + dz * dz > WAKE_RANGE * WAKE_RANGE || checked.contains(packed) || !level.isPositionEntityTicking(wreck)) {
					continue;
				}
				List<Long> all = new ArrayList<>(checked);
				all.add(packed);
				while (all.size() > 512) {
					all.removeFirst();
				}
				level.setAttached(CHECKED, List.copyOf(all));
				if (level.getRandom().nextInt(WAKE_ODDS) == 0) {
					lieAt(level, wreck);
				}
				return;
			}
		}
	}

	/** The thing put in open water beside {@code wreck} (null: no room for it there, a chest buried ashore say). */
	public static @Nullable WreckThing lieAt(ServerLevel level, BlockPos wreck) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < WAKE_TRIES; i++) {
			BlockPos at = wreck.offset(random.nextInt(13) - 6, 1 + random.nextInt(3), random.nextInt(13) - 6);
			if (!open(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5)) {
				continue;
			}
			WreckThing thing = Beasts.WRECK_THING.create(level, EntitySpawnReason.EVENT);
			if (thing == null) {
				return null;
			}
			thing.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			thing.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			level.addFreshEntity(thing);
			JaymosKingdoms.LOGGER.info("Something lies under the wreck at {}", wreck);
			return thing;
		}
		return null;
	}

	/** Water for its body at this place: at its foot, its middle and its top. */
	private static boolean open(Level level, double x, double y, double z) {
		for (double up = 0.3; up < 3.0; up += 1.2) {
			if (!level.getFluidState(BlockPos.containing(x, y + up, z)).is(FluidTags.WATER)) {
				return false;
			}
		}
		return true;
	}

	// ---- its round -----------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.setDeltaMovement(Vec3.ZERO);
		if (this.home == null) {
			this.home = this.position();
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(null);
			target = null;
		}
		if (target != null) {
			if (this.bar == null) {
				this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
			}
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
			BossBars.show(level, this, this.bar);
		}
		if (this.acting("ink")) {
			// off behind its cloud, away from whoever hurt it
			Vec3 from = target != null ? target.position() : this.home;
			Vec3 away = this.position().subtract(from).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 0.01) {
				away = Vec3.directionFromRotation(0.0F, this.getYRot());
			}
			this.swim(level, this.position().add(away.normalize().scale(4.0)), DART);
			return;
		}
		if (this.inks < INK_AT.length && this.getHealth() <= this.getMaxHealth() * INK_AT[this.inks]) {
			this.ink(level);
			return;
		}
		if (this.acting("grab")) {
			this.grabbing(level, target);
			return;
		}
		if (this.acting("lash")) {
			if (this.actionRun() == LASH_TELL) {
				this.lashLands(level);
			}
			return;
		}
		if (target == null) {
			this.lost = 0;
			if (this.position().distanceToSqr(this.home) > 1.0) {
				this.swim(level, this.home, SWIM);
			}
			return;
		}
		this.swim(level, new Vec3(target.getX(), target.getY() - HANG, target.getZ()), SWIM);
		Vec3 to = target.position().subtract(this.position());
		this.setYRot((float) (Math.atan2(to.z, to.x) * 180.0 / Math.PI) - 90.0F);
		this.yBodyRot = this.getYRot();
		this.lashIn--;
		this.grabIn--;
		if (!this.over(target)) {
			if (++this.lost > GIVE_UP) {
				this.setTarget(null);
				this.setLastHurtByMob(null);
			}
			return;
		}
		this.lost = 0;
		if (this.grabIn <= 0) {
			this.startGrab(level, target);
		} else if (this.lashIn <= 0) {
			this.lashIn = LASH_EVERY + this.random.nextInt(20);
			this.act("lash", LASH_TICKS);
			PowersCompat.tellIncoming(this, target, LASH_TELL, false);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
		}
	}

	/** One stroke toward {@code to}, through water only: straight if it can, else along the bottom or straight up or down. */
	private void swim(ServerLevel level, Vec3 to, double pace) {
		Vec3 gap = to.subtract(this.position());
		double far = gap.length();
		if (far < 0.05) {
			return;
		}
		Vec3 step = gap.scale(Math.min(pace, far) / far);
		Vec3[] tries = {step, new Vec3(step.x, 0.0, step.z), new Vec3(0.0, step.y, 0.0)};
		for (Vec3 by : tries) {
			if (by.lengthSqr() > 1.0E-4 && open(level, this.getX() + by.x, this.getY() + by.y, this.getZ() + by.z)) {
				this.setPos(this.getX() + by.x, this.getY() + by.y, this.getZ() + by.z);
				return;
			}
		}
	}

	/** In the column its arms reach: over it, not too far out or up. */
	private boolean over(Entity e) {
		double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ(), up = e.getY() - this.getY();
		double out = REACH + e.getBbWidth() * 0.5;
		return dx * dx + dz * dz <= out * out && up > -1.0 && up <= REACH_UP;
	}

	private boolean fair(LivingEntity e) {
		return e != this && e.isAlive() && !(e instanceof WreckThing) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** Its arms clap shut: everyone in the column over it is hurt and thrown up. */
	private void lashLands(ServerLevel level) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(REACH + 1.0, REACH_UP, REACH + 1.0), this::fair)) {
			if (this.over(e) && this.strikes(e) && e.hurtServer(level, this.damageSources().mobAttack(this), LASH_DAMAGE)) {
				e.setDeltaMovement(e.getDeltaMovement().add(0.0, LASH_UP, 0.0));
				e.needsSync = true;
			}
		}
		level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 5.0, this.getZ(), 120, 2.0, 2.5, 2.0, 0.3);
		level.sendParticles(ParticleTypes.SPLASH, this.getX(), level.getSeaLevel(), this.getZ(), 80, 2.5, 0.2, 2.5, 0.4);
		level.playSound(null, this.getX(), this.getY() + 4.0, this.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.5F);
	}

	// ---- the grab ------------------------------------------------------------------------------------------------------

	private void startGrab(ServerLevel level, LivingEntity target) {
		this.grabIn = GRAB_EVERY + this.random.nextInt(60);
		this.lashIn = Math.max(this.lashIn, 30);
		this.grabHurt = 0.0F;
		this.heldId = -1;
		this.heldBoat = target.getVehicle() instanceof AbstractBoat boat ? boat.getId() : -1;
		Ship ship = Ships.aboard(target);
		if (ship != null) {
			ship.holdFast(GRAB_TICKS);
			level.playSound(null, ship.getX(), ship.getY(), ship.getZ(), SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 2.5F, 0.4F);
		}
		this.act("grab", GRAB_TICKS);
		PowersCompat.tellIncoming(this, target, GRAB_TELL, true);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5F, 0.6F);
	}

	private @Nullable LivingEntity held(ServerLevel level) {
		return this.heldId >= 0 && level.getEntity(this.heldId) instanceof LivingEntity e && this.fair(e) ? e : null;
	}

	/** The grab, tick by tick: the boat held still, the snatch at the end of the tell, then the pull down, the squeeze and the breath taken. */
	private void grabbing(ServerLevel level, @Nullable LivingEntity target) {
		int run = this.actionRun();
		if (this.heldBoat >= 0 && level.getEntity(this.heldBoat) instanceof AbstractBoat boat) {
			boat.setDeltaMovement(Vec3.ZERO);
			boat.needsSync = true;
		}
		if (run < GRAB_TELL) {
			return;
		}
		if (run == GRAB_TELL) {
			if (target != null && this.over(target) && !PowersCompat.slippery(target)) {
				if (target.isPassenger()) {
					target.stopRiding();
				}
				this.heldId = target.getId();
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
			}
			return;
		}
		LivingEntity prey = this.held(level);
		if (prey != null && PowersCompat.slippery(prey)) {
			prey = null;
		}
		if (prey == null) {
			// nothing in its arms: they close on water, and it is open for a moment
			this.heldId = -1;
			if (run >= GRAB_TELL + GRAB_MISSED) {
				this.stopActing();
				this.heldBoat = -1;
			}
			return;
		}
		Vec3 hold = new Vec3(this.getX(), this.getY() + HOLD_ABOVE, this.getZ());
		Vec3 to = prey.position().lerp(hold, PULL);
		if (prey instanceof ServerPlayer player) {
			player.teleportTo(level, to.x, to.y, to.z, Relative.ROTATION, 0.0F, 0.0F, false);
		} else {
			prey.setPos(to.x, to.y, to.z);
		}
		prey.setDeltaMovement(Vec3.ZERO);
		prey.needsSync = true;
		prey.syncVelocity = true;
		prey.resetFallDistance();
		if (prey.isUnderWater() && !prey.canBreatheUnderwater()) {
			prey.setAirSupply(Math.max(-19, prey.getAirSupply() - AIR_DRAIN));
		}
		if ((run - GRAB_TELL) % SQUEEZE_EVERY == 0) {
			prey.hurtServer(level, this.damageSources().mobAttack(this), SQUEEZE_DAMAGE);
			level.sendParticles(ParticleTypes.BUBBLE, prey.getX(), prey.getY() + 1.0, prey.getZ(), 14, 0.3, 0.5, 0.3, 0.1);
		}
	}

	private void letGo() {
		this.heldId = -1;
		this.heldBoat = -1;
	}

	@Override
	protected void onActionEnd() {
		this.letGo();
	}

	// ---- the ink -------------------------------------------------------------------------------------------------------

	private void ink(ServerLevel level) {
		this.inks++;
		this.letGo();
		this.act("ink", INK_TICKS);
		this.lashIn = INK_TICKS + 30;
		this.grabIn = Math.max(this.grabIn, INK_TICKS + 60);
		level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 3.0, this.getZ(), 400, 3.0, 3.0, 3.0, 0.08);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SQUID_SQUIRT, SoundSource.HOSTILE, 3.0F, 0.4F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(INK_RANGE), this::fair)) {
			if (e.isInWater() && (e instanceof Player || this.strikes(e))) {
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, INK_BLIND, 0), this);
				e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, INK_BLIND, 0), this);
			}
		}
	}

	// ---- what it is ----------------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return false;
		}
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.acting("grab") && this.heldId >= 0) {
			this.grabHurt += before - this.getHealth();
			if (this.grabHurt >= GRAB_BREAK) {
				// hurt enough while it holds somebody, it lets them go
				this.stopActing();
				this.letGo();
				this.lashIn = Math.max(this.lashIn, 30);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.HOSTILE, 2.0F, 0.5F);
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			boolean powers = BuiltInRegistries.ITEM.getOptional(COIL).isPresent();
			BuiltInRegistries.ITEM.getOptional(COIL).ifPresent(item -> this.spawnAtLocation(level, new ItemStack(item)));
			BuiltInRegistries.ITEM.getOptional(TOOTH).ifPresent(item -> this.spawnAtLocation(level, new ItemStack(item, 3 + this.random.nextInt(4))));
			this.spawnAtLocation(level, new ItemStack(Items.INK_SAC, 4 + this.random.nextInt(5)));
			if (!powers) {
				this.spawnAtLocation(level, new ItemStack(Items.HEART_OF_THE_SEA));
			}
		}
		super.die(source);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean isNoGravity() {
		return true;
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
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ELDER_GUARDIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ELDER_GUARDIAN_DEATH;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
