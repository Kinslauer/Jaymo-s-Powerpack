package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Fractures;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import java.util.Comparator;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The symbiote without a host: a black blob hauling itself over the ground on its tendrils. It leaves its host when
 * they die and stays near where they fell (their body, if Jaymo's Kitchen left one), looking for a new host: any web
 * slinger without a symbiote who comes near, or the dead host's body once it gets back up as a zombie (that one becomes
 * {@link WebBehemoth a behemoth}). With neither about it makes do with any mob nearby ({@link WebInhabit}), until that
 * one proves unfit; it won't take one it has just left again. Only fire, lava and explosions can hurt it; anything else it shrugs off.
 * <p>
 * It fights back ({@link #defend}): whoever goes for it (a blow, a shot, a fire or blast they set, or burning it with
 * them standing by) is marked. A web slinger without a symbiote is taken there and then: it leaps up its tendril and
 * bonds to them. Anyone else is lashed: a blow that leaves them badly hurt (a broken limb, reeling) or, often, dead; it
 * comes after them for a while to finish it. A web slinger who already wears a symbiote is kin and only gets shrugged off.
 * <p>
 * Fire and blasts are the one thing it fears ({@link #danger}): flames, lava, a lit campfire, anything burning, primed
 * TNT, a hissing creeper, a fireball, someone holding flint and steel, a fire charge or lava, or being burnt or blasted
 * itself send it fleeing at full speed until it's {@link #FLEE_DISTANCE} away, and it makes its new home there.
 */
public class WebSymbioteBlob extends PathfinderMob {
	// ---- tuning ----
	public static final double MAX_HEALTH = 20.0;
	public static final double MOVE_SPEED = 0.28;
	/** It wanders this far around its home (where its host died, or their body). */
	static final int ROAM_RADIUS = 10;
	/** It notices a web slinger or a risen body this far off. */
	static final double SEEK_RANGE = 16.0;
	/** It gives up a chase this far from home. */
	static final double LEASH = 32.0;
	/** Close enough to leap on and take hold. */
	static final double BOND_REACH = 1.4;
	/** It looks around for a host this often (ticks), and for its old host's body this often. */
	static final int SCAN_EVERY = 20, HOME_EVERY = 40;
	/** A player's body this far from home becomes its home. */
	static final double CORPSE_RANGE = 24.0;
	static final double SEEK_SPEED = 1.4, ROAM_SPEED = 0.8;
	/**
	 * A Gravity meteor carries one this often, unless a symbiote (roaming, a behemoth, or bonded to a player) is
	 * already within {@code METEOR_VICINITY}. Out of the burning crater, fire can't touch it for {@code FIRE_GRACE} ticks.
	 */
	static final float METEOR_CHANCE = 0.1F;
	/** Of those, this share crawls out alive; the rest lies in the crater as goo (Black Ooze), as craters have it. */
	static final float METEOR_ALIVE = 0.3F;
	static final double METEOR_VICINITY = 128.0;
	static final int FIRE_GRACE = 300;
	/** Its tendril lash reaches this far; one who attacked it from farther off is chased until in reach. */
	static final double LASH_REACH = 7.0;
	/** Ticks between lashes. */
	static final int LASH_COOLDOWN = 30;
	/** A lash: this much damage, a broken limb, and this share of the time it simply kills. */
	static final float LASH_DAMAGE = 14.0F, KILL_CHANCE = 0.35F, KILL_DAMAGE = 1000.0F;
	/** How long the lashed reel (slowed, weakened; ticks). */
	static final int LASH_REEL = 200;
	/** It hunts whoever went for it this long (ticks), unless they get past its leash. */
	static final int GRUDGE_TICKS = 300;
	/** Burnt or blasted with no one to blame, it blames the nearest player this close. */
	static final double BLAME_RANGE = 8.0;
	/** It looks for fire this far off (blocks), and for burning or exploding things / fire in hand this far. */
	static final int FIRE_SCAN = 6;
	static final double THREAT_SCAN = 12.0, HELD_FIRE_SCAN = 8.0;
	/** It checks for fire this often (ticks). */
	static final int DANGER_EVERY = 10;
	/** It runs until this far from the fire (blocks), or for this long at most (ticks). */
	static final double FLEE_DISTANCE = 40.0, FLEE_SPEED = 2.2;
	static final int FLEE_TICKS = 400;

	public static EntityType<WebSymbioteBlob> TYPE;

	/** Ticks left that fire can't hurt it (it came down in a meteor). */
	private int fireGrace;

	/** With no host this long (ticks; 5 minutes) it gives up and settles where it is as goo, a block, still and waiting. */
	static final int SETTLE_TICKS = 6000;
	/** Fresh out of a mob, it rests this long (ticks) before looking for another mob. */
	static final int MOB_REST_TICKS = 100;
	/** The mobs it has been in already: it won't take them again. */
	private final java.util.Set<java.util.UUID> spurned = new java.util.HashSet<>();
	private int mobRest;

	/** Who (or what) it's after right now; worked out again every {@link #SCAN_EVERY}. */
	private @Nullable Entity quarry;
	/** Whoever went for it, and until when it hunts them. */
	private @Nullable LivingEntity grudge;
	private int grudgeTicks, lashCooldown;
	/** The fire it's running from, and for how much longer. */
	private @Nullable Vec3 fleeFrom;
	private int fleeTicks;

	public WebSymbioteBlob(EntityType<? extends WebSymbioteBlob> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.FIRE, -1.0F);
		this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.FIRE_IN_NEIGHBOR, -1.0F);
		this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA, -1.0F);
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("web_symbiote_blob"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<WebSymbioteBlob>of(WebSymbioteBlob::new, MobCategory.MISC)
				.sized(0.8F, 0.5F)
				.eyeHeight(0.35F)
				.clientTrackingRange(8)
				.noLootTable()
				.build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, MOVE_SPEED)
			.add(Attributes.FOLLOW_RANGE, LEASH);
	}

	/** Out of a host at {@code at}: it stays around there. */
	static WebSymbioteBlob release(ServerLevel level, Vec3 at) {
		WebSymbioteBlob blob = new WebSymbioteBlob(TYPE, level);
		blob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		blob.setHomeTo(BlockPos.containing(at), ROAM_RADIUS);
		level.addFreshEntity(blob);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.8, at.z, 50, 0.4, 0.7, 0.4, 0.1);
		level.sendParticles(WebSymbiote.dust(1.8F), at.x, at.y + 0.6, at.z, 40, 0.5, 0.6, 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 1.4F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.HOSTILE, 1.0F, 0.5F);
		return blob;
	}

	/** It has just left {@code host} (rejected, or dead): it won't go back, and rests a moment first. */
	WebSymbioteBlob spurn(Entity host) {
		this.spurned.add(host.getUUID());
		this.mobRest = MOB_REST_TICKS;
		return this;
	}

	/** No host found: it sinks into a puddle of goo where it lies (Black Ooze), if there's room for one. */
	private boolean settle(ServerLevel level) {
		if (WebPower.GOO == null) {
			return false;
		}
		BlockPos.MutableBlockPos pos = this.blockPosition().above().mutable();
		for (int i = 0; i < 4; i++, pos.move(0, -1, 0)) {
			BlockPos below = pos.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
				&& (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) && level.getFluidState(pos).isEmpty()) {
				level.setBlock(pos, WebPower.GOO.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 25, 0.3, 0.2, 0.3, 0.05);
				level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.5F);
				level.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.8F, 0.5F);
				this.discard();
				return true;
			}
		}
		return false;
	}

	/** A meteor has struck at {@code at}: maybe a symbiote crawls out of the rock. Returns whether one did. */
	public static boolean fromMeteor(ServerLevel level, Vec3 at) {
		if (level.getRandom().nextFloat() >= METEOR_CHANCE) {
			return false;
		}
		net.minecraft.world.phys.AABB vicinity = new net.minecraft.world.phys.AABB(at, at).inflate(METEOR_VICINITY);
		boolean taken = !level.getEntities((Entity)null, vicinity, e -> e instanceof WebSymbioteBlob || WebBehemoth.isHost(e)
			|| e instanceof Player p && WebSymbiote.state(p).bonded()).isEmpty();
		if (taken) {
			return false;
		}
		if (level.getRandom().nextFloat() >= METEOR_ALIVE) {
			return gooAt(level, at);
		}
		WebSymbioteBlob blob = release(level, at.add(0.0, 0.5, 0.0));
		blob.fireGrace = FIRE_GRACE;
		blob.clearFire();
		return true;
	}

	/** The goo on the crater floor below {@code at}, on a glassy seat, the way the natural craters hold it. */
	private static boolean gooAt(ServerLevel level, Vec3 at) {
		if (WebPower.GOO == null) {
			return false;
		}
		BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 2.0, at.z).mutable();
		for (int i = 0; i < 12 && pos.getY() > level.getMinY(); i++, pos.move(0, -1, 0)) {
			if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
				&& (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced())) {
				level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				level.setBlock(pos, WebPower.GOO.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 20, 0.3, 0.2, 0.3, 0.05);
				level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 0.5F);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FleeFireGoal());
		this.goalSelector.addGoal(2, new SeekHostGoal());
		this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, ROAM_SPEED));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, ROAM_SPEED));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.mobRest > 0) {
			this.mobRest--;
		}
		if (this.lashCooldown > 0) {
			this.lashCooldown--;
		}
		if (this.grudge != null && (--this.grudgeTicks <= 0 || !hunts(this.grudge))) {
			this.grudge = null;
		}
		if (this.isOnFire() && this.tickCount % SCAN_EVERY == 0 && this.grudge == null) {
			this.blameNearest(level);
		}
		if (this.fleeTicks > 0) {
			this.fleeTicks--;
		}
		if (this.isOnFire()) {
			this.flee(this.fleeFrom != null ? this.fleeFrom : this.position());
		} else if (this.tickCount % DANGER_EVERY == 0) {
			Vec3 threat = this.danger(level);
			if (threat != null) {
				this.flee(threat);
			}
		}
		if (this.grudge != null) {
			// someone is trying to kill it: they come first
			this.quarry = this.grudge;
			if (this.distanceTo(this.grudge) <= LASH_REACH) {
				this.defend(level, this.grudge);
				if (this.isRemoved()) {
					return;
				}
			}
		}
		if (this.tickCount >= SETTLE_TICKS && this.quarry == null && this.fleeFrom == null && this.onGround() && this.settle(level)) {
			return;
		}
		if (this.fireGrace > 0) {
			this.fireGrace--;
			this.clearFire();
		}
		if (this.tickCount % HOME_EVERY == 0) {
			// it keeps close to its old host's body, wherever that's carried
			Vec3 home = Vec3.atCenterOf(this.getHomePosition());
			level.getEntities(this, this.getBoundingBox().inflate(CORPSE_RANGE), KitchenCompat::isPlayerCorpse).stream()
				.filter(e -> e.position().distanceTo(home) <= CORPSE_RANGE)
				.min(Comparator.comparingDouble(e -> e.distanceToSqr(this)))
				.ifPresent(corpse -> this.setHomeTo(corpse.blockPosition(), ROAM_RADIUS));
		}
		if (this.tickCount % SCAN_EVERY == 0 && this.grudge == null) {
			this.quarry = findHost(level);
		}
		boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
		if (moving && this.tickCount % 10 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.HOSTILE, 0.5F, 0.6F);
		}
		if (this.random.nextInt(moving ? 4 : 12) == 0) {
			level.sendParticles(WebSymbiote.dust(0.9F), this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.3, 0.02, 0.3, 0.0);
		}
		if (this.random.nextInt(240) == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, 0.4F, 1.6F);
		}
	}

	/** A risen body first (its old host's or anyone's), then the nearest web slinger without a symbiote, then any mob. */
	private @Nullable Entity findHost(ServerLevel level) {
		Vec3 home = Vec3.atCenterOf(this.getHomePosition());
		Entity zombie = level.getEntities(this, this.getBoundingBox().inflate(SEEK_RANGE),
				e -> e.isAlive() && KitchenCompat.isZombiePlayer(e) && !WebBehemoth.isHost(e)).stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
		if (zombie != null) {
			return zombie;
		}
		Entity player = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(SEEK_RANGE), p -> wants(p, home)).stream()
			.min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
		if (player != null || this.mobRest > 0) {
			return player;
		}
		return level.getEntities(this, this.getBoundingBox().inflate(SEEK_RANGE), e -> WebInhabit.takeable(e) && !this.spurned.contains(e.getUUID())
				&& e.position().distanceTo(home) <= LEASH).stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
	}

	private static boolean wants(Player player, Vec3 home) {
		return player.isAlive() && !player.isSpectator() && Powers.has(player, WebPower.INSTANCE) && !WebSymbiote.state(player).bonded()
			&& player.position().distanceTo(home) <= LEASH;
	}

	/** Leaps on and takes hold. */
	private void join(ServerLevel level, Entity host) {
		if (host instanceof ServerPlayer player) {
			if (!wants(player, Vec3.atCenterOf(this.getHomePosition()))) {
				return;
			}
			WebSymbiote.bond(player, this.position());
		} else if (host instanceof Mob mob && KitchenCompat.isZombiePlayer(mob)) {
			WebBehemoth.possess(level, mob);
		} else if (host instanceof Mob mob && WebInhabit.takeable(mob) && !this.spurned.contains(mob.getUUID())) {
			WebInhabit.inhabit(level, mob);
		} else {
			return;
		}
		level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.3, this.getZ(), 30, 0.3, 0.2, 0.3, 0.1);
		this.discard();
	}

	private boolean hunts(LivingEntity target) {
		return target.isAlive() && !target.isRemoved() && target.level() == this.level() && !target.isSpectator()
			&& !(target instanceof Player p && (p.isCreative() || kin(p))) && target.distanceTo(this) <= LEASH;
	}

	/** A web slinger already wearing a symbiote: it won't turn on one of its own. */
	private static boolean kin(Player player) {
		return Powers.has(player, WebPower.INSTANCE) && WebSymbiote.state(player).bonded();
	}

	/** {@code source} hurt it (or tried): mark whoever did it. */
	private void provoked(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return;
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != this) {
			this.marks(attacker);
			if (this.distanceTo(attacker) <= LASH_REACH) {
				this.defend(level, attacker);
			}
		} else if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			this.blameNearest(level);
		}
	}

	private void marks(LivingEntity attacker) {
		if (this.hunts(attacker)) {
			this.grudge = attacker;
			this.grudgeTicks = GRUDGE_TICKS;
		}
	}

	/** Burning with nobody to blame: whoever stands nearest set it, as far as it's concerned. */
	private void blameNearest(ServerLevel level) {
		level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(BLAME_RANGE), this::hunts).stream()
			.min(Comparator.comparingDouble(p -> p.distanceToSqr(this)))
			.ifPresent(this::marks);
	}

	/**
	 * It turns on {@code foe}: a web slinger without a symbiote is taken on the spot; anyone else gets a tendril lash
	 * that breaks a limb and leaves them reeling, or kills them outright.
	 */
	private void defend(ServerLevel level, LivingEntity foe) {
		if (this.isRemoved() || this.lashCooldown > 0 || !this.hunts(foe)) {
			return;
		}
		this.lashCooldown = LASH_COOLDOWN;
		Vec3 from = this.position().add(0.0, 0.3, 0.0);
		Vec3 to = foe.position().add(0.0, foe.getBbHeight() * 0.6, 0.0);
		this.tendril(level, from, to);
		this.getLookControl().setLookAt(foe, 90.0F, 90.0F);
		if (foe instanceof ServerPlayer player && wants(player, player.position())) {
			// it goes for the one it was always after: up the tendril and in
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8F, 1.6F);
			Shakes.send(player, 8.0F, from);
			WebSymbiote.bond(player, from);
			level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.3, this.getZ(), 30, 0.3, 0.2, 0.3, 0.1);
			this.discard();
			return;
		}
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.4F, 0.7F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.HOSTILE, 0.8F, 1.6F);
		if (foe instanceof ServerPlayer player) {
			Shakes.send(player, 12.0F, from);
		}
		DamageSource lash = level.damageSources().mobAttack(this);
		if (this.random.nextFloat() < KILL_CHANCE) {
			// straight through
			level.sendParticles(ParticleTypes.SQUID_INK, to.x, to.y, to.z, 40, 0.3, 0.4, 0.3, 0.15);
			foe.hurtServer(level, lash, KILL_DAMAGE);
			return;
		}
		Vec3 away = foe.position().subtract(this.position()).horizontal();
		foe.hurtServer(level, lash, LASH_DAMAGE);
		if (away.lengthSqr() > 1.0E-4) {
			foe.setDeltaMovement(foe.getDeltaMovement().add(away.normalize().scale(1.1).add(0.0, 0.45, 0.0)));
			foe.needsSync = true;
			foe.syncVelocity = true;
		}
		if (foe.isAlive()) {
			int[] limbs = {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG};
			Fractures.breakBone(foe, limbs[this.random.nextInt(limbs.length)], this);
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LASH_REEL, 1), this);
			foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LASH_REEL, 1), this);
			foe.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false), this);
		}
	}

	/** The lash itself: a black tendril snapping out from {@code from} to {@code to}. */
	private void tendril(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int n = Math.max(2, (int)(step.length() / 0.25));
		for (int i = 0; i <= n; i++) {
			Vec3 at = from.add(step.scale(i / (double)n));
			level.sendParticles(WebSymbiote.dust(1.2F), at.x, at.y, at.z, 2, 0.04, 0.04, 0.04, 0.0);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y, from.z, 12, 0.3, 0.15, 0.3, 0.08);
	}

	/** Runs from {@code threat} (again, if it was already running). */
	private void flee(Vec3 threat) {
		if (this.fleeFrom == null) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_HURT, SoundSource.HOSTILE, 0.7F, 1.8F);
		}
		this.fleeFrom = threat;
		this.fleeTicks = FLEE_TICKS;
	}

	/** The nearest fire or blast-to-be it can see, or null if there's none. */
	private @Nullable Vec3 danger(ServerLevel level) {
		AABB near = this.getBoundingBox().inflate(THREAT_SCAN);
		Entity threat = level.getEntities(this, near, e -> e.isAlive() && (e.isOnFire() || e instanceof PrimedTnt || e instanceof MinecartTNT
				|| e instanceof AbstractHurtingProjectile || e instanceof Creeper c && (c.getSwellDir() > 0 || c.isIgnited())
				|| e instanceof LivingEntity l && l.distanceTo(this) <= HELD_FIRE_SCAN && (holdsFire(l.getMainHandItem()) || holdsFire(l.getOffhandItem())))).stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
		if (threat != null) {
			return threat.position();
		}
		BlockPos centre = this.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-FIRE_SCAN, -FIRE_SCAN / 2, -FIRE_SCAN), centre.offset(FIRE_SCAN, FIRE_SCAN / 2, FIRE_SCAN))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || CampfireBlock.isLitCampfire(state)) {
				double d = pos.distSqr(centre);
				if (d < bestDist) {
					bestDist = d;
					best = pos.immutable();
				}
			}
		}
		return best == null ? null : Vec3.atCenterOf(best);
	}

	private static boolean holdsFire(ItemStack stack) {
		return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.LAVA_BUCKET) || stack.is(Items.TNT);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			Vec3 at = source.getSourcePosition();
			this.flee(at != null ? at : this.position());
		}
		this.provoked(level, source);
		if (this.isRemoved()) {
			return false;
		}
		if (this.fireGrace > 0 && (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION))
			&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return super.hurtServer(level, source, damage);
		}
		// it splashes apart around the blow and flows back together
		level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.3, this.getZ(), 10, 0.3, 0.15, 0.3, 0.06);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
		if (source.getEntity() != null) {
			Vec3 away = this.position().subtract(source.getEntity().position()).horizontal();
			if (away.lengthSqr() > 1.0E-4) {
				this.setDeltaMovement(this.getDeltaMovement().add(away.normalize().scale(0.4).add(0.0, 0.25, 0.0)));
			}
		}
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.3, this.getZ(), 60, 0.4, 0.3, 0.4, 0.12);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 20, 0.4, 0.3, 0.4, 0.03);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 0.8F, 1.6F);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/** Runs flat out from {@link #fleeFrom} until it's far enough away, then settles there as its new home. */
	private final class FleeFireGoal extends Goal {
		private int repath;

		FleeFireGoal() {
			this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private boolean running() {
			WebSymbioteBlob blob = WebSymbioteBlob.this;
			return blob.fleeFrom != null && blob.fleeTicks > 0 && blob.position().distanceTo(blob.fleeFrom) < FLEE_DISTANCE;
		}

		@Override
		public boolean canUse() {
			return this.running();
		}

		@Override
		public boolean canContinueToUse() {
			return this.running();
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			WebSymbioteBlob blob = WebSymbioteBlob.this;
			blob.getNavigation().stop();
			if (blob.fleeFrom != null && blob.position().distanceTo(blob.fleeFrom) >= FLEE_DISTANCE * 0.5) {
				// far from the fire: it stays out here now
				blob.setHomeTo(blob.blockPosition(), ROAM_RADIUS);
			}
			blob.fleeFrom = null;
			blob.fleeTicks = 0;
		}

		@Override
		public void tick() {
			WebSymbioteBlob blob = WebSymbioteBlob.this;
			if (blob.fleeFrom == null) {
				return;
			}
			if (--this.repath <= 0 || blob.getNavigation().isDone()) {
				this.repath = 20;
				Vec3 to = DefaultRandomPos.getPosAway(blob, 16, 7, blob.fleeFrom);
				if (to == null) {
					// nowhere mapped: straight away from it
					Vec3 away = blob.position().subtract(blob.fleeFrom).horizontal();
					to = blob.position().add(away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize().scale(10.0));
				}
				blob.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
			}
		}
	}

	/** Goes after whatever {@link #findHost} picked, and leaps on it once it's close. */
	private final class SeekHostGoal extends Goal {
		private int repath;

		SeekHostGoal() {
			this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private boolean valid() {
			Entity q = WebSymbioteBlob.this.quarry;
			return q != null && q.isAlive() && !q.isRemoved() && q.level() == WebSymbioteBlob.this.level()
				&& q.distanceTo(WebSymbioteBlob.this) <= LEASH;
		}

		@Override
		public boolean canUse() {
			return valid();
		}

		@Override
		public boolean canContinueToUse() {
			return valid();
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			WebSymbioteBlob.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			WebSymbioteBlob blob = WebSymbioteBlob.this;
			Entity q = blob.quarry;
			if (q == null) {
				return;
			}
			blob.getLookControl().setLookAt(q, 30.0F, 30.0F);
			if (--this.repath <= 0) {
				this.repath = 10;
				blob.getNavigation().moveTo(q, SEEK_SPEED);
			}
			if (q == blob.grudge) {
				return; // tick() lashes it once it's in reach
			}
			if (blob.distanceTo(q) <= BOND_REACH + q.getBbWidth() * 0.5 && blob.level() instanceof ServerLevel level) {
				blob.join(level, q);
			}
		}
	}
}
