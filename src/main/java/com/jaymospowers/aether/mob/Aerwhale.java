package com.jaymospowers.aether.mob;

import com.jaymospowers.armory.ThrownWeapon;
import java.lang.reflect.Method;
import java.util.EnumSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The aerwhale: a huge, quiet whale (as long as Kingdoms' great whales, about fifteen blocks) that drifts across the sky on its own, picking a spot 16 to 32 blocks away and sailing
 * to it, never into a wall. Its call carries far. No riding.
 *
 * <p>(0.17.20) It is hunted like Kingdoms' great whales: thick blubber turns most blows ({@link #BLUBBER}); only
 * harpoons (Kingdoms' roped ones, the Armory's thrown one, or a trident) bite deep. A struck whale bolts away from its
 * hunters, climbing. Every Kingdoms harpoon lodged in it bleeds it and tires it faster, and Kingdoms holds it back on the
 * lines, until, spent ({@link #SPENT_AT}), it sinks low over the ground, bleeding, and can be finished. With Kingdoms
 * loaded it drops whale blubber, meat, bone and sometimes ambergris.
 */
public class Aerwhale extends Mob {
	// ---- tuning ----
	/** As tough as Kingdoms' great whale. */
	public static final double HEALTH = 260.0, FLYING_SPEED = 0.2, SPEED = 0.2;
	/** The next waypoint is this far (blocks), and it pushes this hard toward one (scaled by its flying speed). */
	public static final int TRAVEL_MIN = 16, TRAVEL_MAX = 32;
	public static final double PUSH = 0.05;
	/** Air drag each tick, and the song's gap (ticks). */
	public static final double DRAG = 0.91;
	public static final int SONG_EVERY = 1200;
	/** The sky it stays inside (blocks from the world's ends). */
	public static final int MARGIN = 24;
	/** Its body (blocks; the head and fluke reach far past it, about fifteen blocks nose to tail). At least the great whale's 4 x 2.8. */
	public static final float WIDTH = 5.0F, HEIGHT = 3.6F;
	/** A wild one, once it has found room on the ground, rises this far (blocks) into open sky before it is seen. */
	public static final int SPAWN_RISE_MIN = 8, SPAWN_RISE_MAX = 20;

	// ---- the hunt (numbers after Kingdoms' GreatWhale) ----
	/** The share of any blow but a harpoon's that gets through its blubber. */
	public static final float BLUBBER = 0.3F;
	/** Each iron in it bleeds it this much every this many ticks (counting at most this many irons). */
	public static final int BLEED_EVERY = 40, MOST_BLEEDING = 10;
	public static final float BLEED = 1.0F;
	/** How long a fright lasts with no iron in it (ticks). */
	public static final int FRIGHT_TICKS = 900;
	/** What bolting takes out of it a tick (more for each iron), what rest gives back; spent below this. */
	public static final float TIRE = 1.0F / 1400.0F, TIRE_PER_IRON = 1.0F / 1400.0F, REST = 1.0F / 3000.0F, SPENT_AT = 0.12F;
	/** Its push a tick (blocks/tick) bolting at full strength, and spent; how fast it climbs bolting, and how low it sinks spent (blocks over the ground). */
	public static final double FLEE_PUSH = 0.035, SPENT_PUSH = 0.006, FLEE_CLIMB = 0.004, SPENT_HOVER = 4.0;
	/** The entity tag Kingdoms' harpoon lines read: a spent whale takes no more line out. */
	public static final String SPENT_TAG = "jaymospowers_spent_whale";
	/** What it drops with Kingdoms loaded: blubber, meat and bone (min, max), and the chance of a lump of ambergris. */
	public static final int BLUBBER_MIN = 16, BLUBBER_MAX = 28, MEAT_MIN = 8, MEAT_MAX = 14, BONE_MIN = 4, BONE_MAX = 8;
	public static final float AMBERGRIS = 0.12F;

	private static final boolean KINGDOMS = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static final Identifier HARPOON = Identifier.fromNamespaceAndPath("jaymoskingdoms", "harpoon");
	/** Kingdoms' Harpoon.lodged(), read by reflection (null without Kingdoms). */
	private static final @Nullable Method LODGED = lodgedMethod();

	private int fright, irons;
	private float stamina = 1.0F;
	private @Nullable Vec3 threat;
	/** While it bleeds from its irons, so that blow isn't turned by its blubber. */
	private boolean bleeding;

	public Aerwhale(EntityType<? extends Aerwhale> type, Level level) {
		super(type, level);
		this.moveControl = new WhaleMoveControl(this);
		this.xpReward = 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FLYING_SPEED, FLYING_SPEED).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(5, new FloatAroundGoal());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		// spawn rules place it on the ground; a wild one starts up in the sky instead, where there is room for all of it
		if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
			int high = level.getMaxY() - MARGIN;
			for (int attempt = 0; attempt < 4; attempt++) {
				double y = Math.min(this.getY() + SPAWN_RISE_MIN + this.random.nextInt(SPAWN_RISE_MAX - SPAWN_RISE_MIN + 1), high);
				if (y > this.getY() && level.noCollision(this, this.getBoundingBox().move(0.0, y - this.getY(), 0.0))) {
					this.setPos(this.getX(), y, this.getZ());
					break;
				}
			}
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	private static @Nullable Method lodgedMethod() {
		if (!KINGDOMS) {
			return null;
		}
		try {
			return Class.forName("com.jaymoskingdoms.whaling.Harpoon").getMethod("lodged");
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	public boolean spent() {
		return this.stamina < SPENT_AT;
	}

	/** Only a harpoon (or a trident) gets through the blubber whole, and so does the bleeding from its irons. */
	private boolean bitesDeep(DamageSource source) {
		if (this.bleeding || source.is(DamageTypes.TRIDENT)) {
			return true;
		}
		return source.getDirectEntity() instanceof ThrownWeapon thrown && BuiltInRegistries.ITEM.getKey(thrown.getItem().getItem()).getPath().equals("harpoon");
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!this.bitesDeep(source)) {
			amount *= BLUBBER;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && !this.bleeding) {
			Entity by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
			this.threat = by != null ? by.position() : this.threat;
			this.fright = FRIGHT_TICKS;
		}
		return hurt;
	}

	/** Kingdoms' harpoons lodged in it. */
	private int countIrons(ServerLevel level) {
		if (LODGED == null) {
			return 0;
		}
		int n = 0;
		for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(8.0), e -> HARPOON.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())))) {
			try {
				if (LODGED.invoke(e) == this) {
					n++;
				}
			} catch (ReflectiveOperationException ex) {
				return n;
			}
		}
		return n;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 20 == 0) {
			this.irons = this.countIrons(level);
		}
		if (this.irons > 0) {
			this.fright = FRIGHT_TICKS;
		} else if (this.fright > 0) {
			this.fright--;
		}
		boolean fleeing = this.fright > 0;
		if (fleeing && !this.spent()) {
			this.stamina -= TIRE + this.irons * TIRE_PER_IRON;
		} else if (this.irons == 0) {
			this.stamina += REST;
		}
		this.stamina = Mth.clamp(this.stamina, 0.0F, 1.0F);
		if (this.spent()) {
			this.addTag(SPENT_TAG);
		} else {
			this.removeTag(SPENT_TAG);
		}
		if (this.irons > 0 && this.tickCount % BLEED_EVERY == 0) {
			this.bleeding = true;
			this.hurtServer(level, this.damageSources().generic(), BLEED * Math.min(this.irons, MOST_BLEEDING));
			this.bleeding = false;
			level.sendParticles(new DustParticleOptions(0x7A0A0A, 2.5F), this.getX(), this.getY() + HEIGHT * 0.5, this.getZ(), 8 * this.irons,
				WIDTH * 0.4, HEIGHT * 0.4, WIDTH * 0.4, 0.0);
		}
		if (!fleeing) {
			return;
		}
		// bolting (or spent): it steers itself, not its waypoints
		this.getMoveControl().setWait();
		Vec3 push;
		if (this.spent()) {
			// spent: it sinks low over the ground, slow, where its hunters can reach it
			double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getBlockX(), this.getBlockZ());
			double sink = Mth.clamp((ground + SPENT_HOVER - this.getY()) * 0.01, -0.02, 0.01);
			push = this.forward().scale(SPENT_PUSH).add(0.0, sink, 0.0);
		} else {
			Vec3 away = this.threat != null ? this.position().subtract(this.threat).multiply(1.0, 0.0, 1.0) : this.forward();
			away = away.lengthSqr() < 1.0E-4 ? this.forward() : away.normalize();
			// weaving as it goes, and climbing away from the ground
			float weave = Mth.sin(this.tickCount * 0.02F) * 0.6F;
			away = away.yRot(weave);
			boolean roof = this.getY() > level.getMaxY() - MARGIN;
			push = away.scale(FLEE_PUSH * (0.5 + 0.5 * this.stamina)).add(0.0, roof ? -FLEE_CLIMB : FLEE_CLIMB, 0.0);
		}
		this.setDeltaMovement(this.getDeltaMovement().add(push));
		Vec3 motion = this.getDeltaMovement();
		if (motion.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = -((float)Mth.atan2(motion.x, motion.z)) * Mth.RAD_TO_DEG;
			this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 4.0F));
			this.yBodyRot = this.getYRot();
		}
	}

	/** Which way it faces, flat. */
	private Vec3 forward() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (!KINGDOMS) {
			return;
		}
		this.drop(level, "whale_blubber", Mth.nextInt(this.random, BLUBBER_MIN, BLUBBER_MAX));
		this.drop(level, "whale_meat", Mth.nextInt(this.random, MEAT_MIN, MEAT_MAX));
		this.drop(level, "whale_bone", Mth.nextInt(this.random, BONE_MIN, BONE_MAX));
		if (this.random.nextFloat() < AMBERGRIS) {
			this.drop(level, "ambergris", 1);
		}
	}

	/** Drops {@code count} of Kingdoms' item {@code name}, in stacks. */
	private void drop(ServerLevel level, String name, int count) {
		Item item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskingdoms", name)).orElse(null);
		if (item == null) {
			return;
		}
		while (count > 0) {
			int n = Math.min(count, item.getDefaultMaxStackSize());
			this.spawnAtLocation(level, new ItemStack(item, n));
			count -= n;
		}
	}

	@Override
	public void travel(Vec3 input) {
		// it floats: moved by its own push, slowed by the air, never pulled down
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(DRAG));
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
	}

	@Override
	public int getAmbientSoundInterval() {
		return SONG_EVERY;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MobSounds.get("entity.dolphin.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DOLPHIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.DOLPHIN_DEATH;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		// not in the middle of a hunt
		return this.fright <= 0 && this.getHealth() >= this.getMaxHealth();
	}

	/** Pushes toward its waypoint, stops if the way is blocked, and turns to face where it goes. */
	private static final class WhaleMoveControl extends MoveControl<Aerwhale> {
		private int pause;

		WhaleMoveControl(Aerwhale whale) {
			super(whale);
		}

		@Override
		public void tick() {
			if (this.operation != MoveControl.Operation.MOVE_TO) {
				return;
			}
			if (this.pause-- <= 0) {
				this.pause += this.mob.getRandom().nextInt(5) + 2;
				Vec3 way = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
				double length = way.length();
				if (length < 2.0) {
					this.operation = MoveControl.Operation.WAIT;
					return;
				}
				way = way.scale(1.0 / length);
				if (clear(this.mob, way, (int)Math.ceil(length))) {
					double push = PUSH * this.mob.getAttributeValue(Attributes.FLYING_SPEED) * 10.0 * this.speedModifier;
					this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(way.scale(push)));
				} else {
					this.operation = MoveControl.Operation.WAIT;
				}
			}
			Vec3 motion = this.mob.getDeltaMovement();
			if (motion.horizontalDistanceSqr() > 1.0E-4) {
				float yaw = -((float)Mth.atan2(motion.x, motion.z)) * Mth.RAD_TO_DEG;
				this.mob.setYRot(yaw);
				this.mob.yBodyRot = yaw;
			}
			this.mob.setXRot((float)(-Math.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG) * 0.6F);
		}

		/** Whether its box can travel {@code steps} blocks along {@code way}. */
		static boolean clear(Aerwhale whale, Vec3 way, int steps) {
			AABB box = whale.getBoundingBox();
			for (int i = 1; i <= steps; i += 2) {
				box = box.move(way.scale(2.0));
				if (!whale.level().noCollision(whale, box)) {
					return false;
				}
			}
			return true;
		}
	}

	/** Picks a spot 16 to 32 blocks off that it can sail to in a straight line, and goes there. */
	private final class FloatAroundGoal extends Goal {
		FloatAroundGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return Aerwhale.this.fright <= 0 && !Aerwhale.this.getMoveControl().hasWanted();
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			Aerwhale self = Aerwhale.this;
			var random = self.getRandom();
			int low = self.level().getMinY() + MARGIN, high = self.level().getMaxY() - MARGIN;
			for (int attempt = 0; attempt < 6; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double dist = TRAVEL_MIN + random.nextDouble() * (TRAVEL_MAX - TRAVEL_MIN);
				double y = Mth.clamp(self.getY() + (random.nextDouble() - 0.5) * 12.0, low, high);
				Vec3 to = new Vec3(self.getX() + Math.cos(angle) * dist, y, self.getZ() + Math.sin(angle) * dist);
				Vec3 way = to.subtract(self.position());
				if (WhaleMoveControl.clear(self, way.normalize(), (int)Math.ceil(way.length()))) {
					self.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
					return;
				}
			}
		}
	}
}
