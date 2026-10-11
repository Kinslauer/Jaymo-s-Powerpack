package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The symbiote without a host: a black blob hauling itself over the ground on its tendrils. It leaves its host when
 * they die and stays near where they fell (their body, if Jaymo's Kitchen left one), looking for a new host: any web
 * slinger without a symbiote who comes near, or the dead host's body once it gets back up as a zombie (that one becomes
 * {@link WebBehemoth a behemoth}). Only fire, lava and explosions can hurt it; anything else it shrugs off.
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
	static final float METEOR_CHANCE = 0.2F;
	/** Of those, this share crawls out alive; the rest lies in the crater as goo (Black Ooze), as craters have it. */
	static final float METEOR_ALIVE = 0.3F;
	static final double METEOR_VICINITY = 128.0;
	static final int FIRE_GRACE = 300;

	public static EntityType<WebSymbioteBlob> TYPE;

	/** Ticks left that fire can't hurt it (it came down in a meteor). */
	private int fireGrace;

	/** Who (or what) it's after right now; worked out again every {@link #SCAN_EVERY}. */
	private @Nullable Entity quarry;

	public WebSymbioteBlob(EntityType<? extends WebSymbioteBlob> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
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
		this.goalSelector.addGoal(1, new SeekHostGoal());
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
		if (this.tickCount % SCAN_EVERY == 0) {
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

	/** A risen body first (its old host's or anyone's), then the nearest web slinger without a symbiote. */
	private @Nullable Entity findHost(ServerLevel level) {
		Vec3 home = Vec3.atCenterOf(this.getHomePosition());
		Entity zombie = level.getEntities(this, this.getBoundingBox().inflate(SEEK_RANGE),
				e -> e.isAlive() && KitchenCompat.isZombiePlayer(e) && !WebBehemoth.isHost(e)).stream()
			.min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
		if (zombie != null) {
			return zombie;
		}
		return level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(SEEK_RANGE), p -> wants(p, home)).stream()
			.min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
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
		} else if (host instanceof Mob mob) {
			WebBehemoth.possess(level, mob);
		} else {
			return;
		}
		level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.3, this.getZ(), 30, 0.3, 0.2, 0.3, 0.1);
		this.discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
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
			if (blob.distanceTo(q) <= BOND_REACH + q.getBbWidth() * 0.5 && blob.level() instanceof ServerLevel level) {
				blob.join(level, q);
			}
		}
	}
}
