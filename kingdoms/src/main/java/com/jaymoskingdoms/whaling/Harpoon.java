package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.206) A thrown harpoon on its line. It flies like a heavy javelin; in a whale (or a carcass, or any big beast) it
 * lodges and stays, and its line runs back to where it was thrown from: the thrower's boat, the ship they stood on, or
 * the thrower. The line pays out while a whale runs ({@link #PAY_OUT}, up to {@link #MOST_LINE}), then drags the boat
 * after it; slack is taken in as the boat closes, so a tiring whale is hauled up to. A line strained past
 * {@link #SNAP} parts. A harpoon that misses drops where it lands to be picked up again.
 *
 * <p>The pull on the boat is worked out on whichever side moves the boat (the steering player's game, for a boat or
 * the player themself; the server for ships and anything else), the pull back on the whale on the server.
 */
public final class Harpoon extends Projectile {
	/** How hard it's thrown and how wide it may stray; how fast it falls and slows (in air and in water). */
	static final float SPEED = 2.0F, SPREAD = 1.0F;
	static final double GRAVITY = 0.04, AIR_DRAG = 0.99, WATER_DRAG = 0.7;
	/** What it does to what it strikes. */
	static final float DAMAGE = 7.0F;
	/** Beasts at least this wide take the iron and hold it (whales and carcasses always do). */
	static final float BIG = 1.4F;
	/** Line (blocks): out when it strikes, the most there is, the least it's hauled in to, and how fast a running whale takes it out. */
	static final double FIRST_LINE = 10.0, MOST_LINE = 48.0, LEAST_LINE = 4.0, PAY_OUT = 0.2;
	/** The pull on a taut line: per block it's stretched, and the most (blocks/tick); stretched further than this, it parts. */
	static final double PULL = 0.05, MOST_PULL = 0.3, SNAP = 14.0;
	/** The share of the pull that hauls back on a live whale, and on a carcass, and how much heavier than a boat a ship is per 100 weight. */
	static final double WHALE_SHARE = 0.35, CARCASS_SHARE = 1.0, SHIP_HEFT = 0.01;
	/** How long it flies at most (ticks). */
	static final int FLIGHT = 200;
	/** How far the line reaches to find the harpoons that might be yours (cutting lines). */
	static final double NEAR = 96.0;

	private static final EntityDataAccessor<Integer> LODGED = SynchedEntityData.defineId(Harpoon.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ANCHOR = SynchedEntityData.defineId(Harpoon.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> LINE = SynchedEntityData.defineId(Harpoon.class, EntityDataSerializers.FLOAT);
	/** Where it sits in what it's lodged in (that one's frame: x to its left, y up, z ahead). */
	private static final EntityDataAccessor<org.joml.Vector3fc> SPOT = SynchedEntityData.defineId(Harpoon.class, EntityDataSerializers.VECTOR3);
	/** Where its line is made fast aboard a ship (her frame). */
	private static final EntityDataAccessor<org.joml.Vector3fc> KNOT = SynchedEntityData.defineId(Harpoon.class, EntityDataSerializers.VECTOR3);

	private @Nullable EntityReference<Entity> lodged, anchor;
	private int life;

	public Harpoon(EntityType<? extends Harpoon> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Throws one from {@code thrower}, its line made fast to their boat, their ship or them. */
	static void thrown(ServerLevel level, LivingEntity thrower) {
		Harpoon h = new Harpoon(Whaling.HARPOON, level);
		h.setOwner(thrower);
		h.setPos(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
		h.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0.0F, SPEED, SPREAD);
		h.makeFast(level, thrower);
	}

	/** (stage 3) A whaling crew's harpooner throws one at {@code target}, leading it a little and lobbing for the fall. */
	public static void thrownAt(ServerLevel level, LivingEntity thrower, Entity target) {
		Harpoon h = new Harpoon(Whaling.HARPOON, level);
		h.setOwner(thrower);
		h.setPos(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0).add(target.getDeltaMovement().scale(6.0)).subtract(h.position());
		double flight = aim.length() / SPEED;
		h.shoot(aim.x, aim.y + 0.5 * GRAVITY * flight * flight, aim.z, SPEED, SPREAD * 2.0F);
		thrower.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), net.minecraft.sounds.SoundEvents.TRIDENT_THROW.value(),
			net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.8F);
		h.makeFast(level, thrower);
	}

	/** Its line made fast to {@code thrower}'s ship, boat or them, and it's in flight. */
	private void makeFast(ServerLevel level, LivingEntity thrower) {
		Harpoon h = this;
		Ship ship = Ships.aboard(thrower);
		if (ship != null) {
			Vec3 knot = ship.toLocal(thrower.position().add(0.0, 1.0, 0.0));
			h.entityData.set(KNOT, new Vector3f((float)knot.x, (float)knot.y, (float)knot.z));
		}
		Entity fast = ship != null ? ship : thrower.getVehicle() instanceof AbstractBoat boat ? boat : thrower;
		h.anchor = EntityReference.of(fast);
		level.addFreshEntity(h);
	}

	/** (0.7.210, Whalers) Its line, fast to a whaleboat, passed up and made fast aboard {@code ship} at {@code knot} (in her frame). */
	public void passTo(Ship ship, Vec3 knot) {
		this.entityData.set(KNOT, new Vector3f((float)knot.x, (float)knot.y, (float)knot.z));
		this.anchor = EntityReference.of(ship);
		this.entityData.set(ANCHOR, ship.getId());
		this.entityData.set(LINE, (float)Math.max(FIRST_LINE, ship.toWorld(knot.x, knot.y, knot.z).distanceTo(this.position()) + 1.0));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LODGED, -1);
		builder.define(ANCHOR, -1);
		builder.define(LINE, (float)FIRST_LINE);
		builder.define(SPOT, new Vector3f());
		builder.define(KNOT, new Vector3f());
	}

	/** What it's lodged in (null: in flight, or not yet found again after loading). */
	public @Nullable Entity lodged() {
		if (this.level() instanceof ServerLevel) {
			return this.lodged == null ? null : EntityReference.getEntity(this.lodged, this.level());
		}
		int id = this.entityData.get(LODGED);
		return id < 0 ? null : this.level().getEntity(id);
	}

	/** What its line is made fast to (null: none, or it parted). */
	public @Nullable Entity anchor() {
		if (this.level() instanceof ServerLevel) {
			return this.anchor == null ? null : EntityReference.getEntity(this.anchor, this.level());
		}
		int id = this.entityData.get(ANCHOR);
		return id < 0 ? null : this.level().getEntity(id);
	}

	/** Where the line is tied at the anchor's end, in the world. */
	public Vec3 knot(Entity anchor, float partialTicks) {
		Vec3 at = anchor.getPosition(partialTicks);
		if (anchor instanceof Ship ship) {
			org.joml.Vector3fc k = this.entityData.get(KNOT);
			return Ship.toWorld(k.x(), k.y(), k.z(), at, Mth.rotLerp(partialTicks, ship.yRotO, ship.getYRot()));
		}
		if (anchor instanceof AbstractBoat) {
			return at.add(0.0, 0.5, 0.0);
		}
		return at.add(0.0, anchor.getBbHeight() * 0.6, 0.0);
	}

	/** Where it sits in what it's lodged in, in the world. */
	public Vec3 spot(Entity in, float partialTicks) {
		org.joml.Vector3fc s = this.entityData.get(SPOT);
		float yaw = Mth.rotLerp(partialTicks, in.yRotO, in.getYRot()) * Mth.DEG_TO_RAD;
		double cos = Mth.cos(yaw), sin = Mth.sin(yaw);
		return in.getPosition(partialTicks).add(s.x() * cos - s.z() * sin, s.y(), s.x() * sin + s.z() * cos);
	}

	public float line() {
		return this.entityData.get(LINE);
	}

	/** Lodges it in {@code in} where it now is (or moves it, as it is, from a dying whale into its carcass). */
	void lodgeIn(Entity in) {
		Entity was = this.lodged();
		Vector3f spot;
		if (was != null) {
			spot = new Vector3f(this.entityData.get(SPOT));
		} else {
			Vec3 d = this.position().subtract(in.position());
			float yaw = in.getYRot() * Mth.DEG_TO_RAD;
			double cos = Mth.cos(yaw), sin = Mth.sin(yaw);
			spot = new Vector3f((float)(d.x * cos + d.z * sin), (float)d.y, (float)(-d.x * sin + d.z * cos));
		}
		this.lodged = EntityReference.of(in);
		this.entityData.set(LODGED, in.getId());
		this.entityData.set(SPOT, spot);
		this.setDeltaMovement(Vec3.ZERO);
	}

	/** Its line parts (or is cut). */
	void part(ServerLevel level) {
		if (this.anchor == null) {
			return;
		}
		this.anchor = null;
		this.entityData.set(ANCHOR, -1);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	@Override
	public void tick() {
		super.tick();
		Entity in = this.lodged();
		if (in != null) {
			if (!this.level().isClientSide()) {
				this.entityData.set(LODGED, in.getId());
			}
			this.setPos(this.spot(in, 1.0F));
			this.tether(in);
			if (this.level() instanceof ServerLevel level && in.isRemoved()) {
				this.loose(level);
			}
			return;
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.lodged != null) {
			// lodged in something that's gone (or not loaded yet): it waits a little, then drops free
			if (++this.life > 100) {
				this.loose(level);
			}
			return;
		}
		this.fly(level);
	}

	private void fly(ServerLevel level) {
		if (++this.life > FLIGHT) {
			this.loose(level);
			return;
		}
		Entity anchorEntity = this.anchor();
		this.entityData.set(ANCHOR, anchorEntity != null ? anchorEntity.getId() : -1);
		Vec3 pos = this.position(), move = this.getDeltaMovement(), next = pos.add(move);
		HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			next = hit.getLocation();
		}
		Entity owner = this.getOwner();
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(1.0),
			e -> e != owner && e != anchorEntity && e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity || e instanceof WhaleCarcass)
				&& !(owner != null && e == owner.getVehicle()) && !(anchorEntity != null && e.getVehicle() == anchorEntity), 0.3F);
		if (entityHit != null) {
			this.strike(level, entityHit.getEntity(), entityHit.getLocation());
			return;
		}
		if (hit.getType() == HitResult.Type.BLOCK) {
			level.playSound(null, next.x, next.y, next.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0F, 0.8F);
			this.setPos(next);
			this.loose(level);
			return;
		}
		boolean wet = level.getFluidState(BlockPos.containing(next)).is(FluidTags.WATER);
		if (wet && !level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER)) {
			level.sendParticles(ParticleTypes.SPLASH, next.x, next.y + 0.3, next.z, 12, 0.2, 0.1, 0.2, 0.1);
		}
		this.setPos(next);
		move = move.scale(wet ? WATER_DRAG : AIR_DRAG).add(0.0, -GRAVITY, 0.0);
		this.setDeltaMovement(move);
		if (wet && move.lengthSqr() < 0.04) {
			this.loose(level);
			return;
		}
		double horizontal = move.horizontalDistance();
		this.setYRot((float)(Mth.atan2(move.x, move.z) * Mth.RAD_TO_DEG));
		this.setXRot((float)(Mth.atan2(move.y, horizontal) * Mth.RAD_TO_DEG));
	}

	/** It strikes {@code target}: hurts it, and lodges if it's a whale, a carcass or a big beast; otherwise it falls free. */
	private void strike(ServerLevel level, Entity target, Vec3 at) {
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().trident(this, owner);
		if (target instanceof LivingEntity living) {
			living.hurtServer(level, source, DAMAGE);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.2F, 0.7F);
		this.setPos(at);
		if (target instanceof GreatWhale || target instanceof WhaleCarcass || target.getBbWidth() >= BIG) {
			this.lodgeIn(target);
			Entity anchorEntity = this.anchor();
			this.entityData.set(ANCHOR, anchorEntity != null ? anchorEntity.getId() : -1);
			if (anchorEntity != null) {
				this.entityData.set(LINE, (float)Math.max(FIRST_LINE, anchorEntity.distanceTo(this) + 1.0));
			}
			return;
		}
		this.loose(level);
	}

	/** The line on a lodged harpoon: paid out, taken in, parting, and the pull at each end where it's taut. */
	private void tether(Entity in) {
		Entity anchorEntity = this.anchor();
		if (anchorEntity == null) {
			return;
		}
		Level level = this.level();
		if (level instanceof ServerLevel server) {
			if (anchorEntity.isRemoved() || anchorEntity.level() != level) {
				this.part(server);
				return;
			}
			this.entityData.set(ANCHOR, anchorEntity.getId());
		}
		Vec3 tail = this.position(), knot = this.knot(anchorEntity, 1.0F), d = knot.subtract(tail);
		double dist = d.length();
		double line = this.line();
		if (level instanceof ServerLevel server) {
			if (dist < line - 0.5) {
				// slack is hauled in
				line = Math.max(LEAST_LINE, dist + 0.5);
			} else if (dist > line + 1.0 && (in instanceof GreatWhale whale ? !whale.spent() : skyWhale(in) && !in.entityTags().contains(SKY_WHALE_SPENT))
				&& line < MOST_LINE) {
				// a running whale takes the line out
				line = Math.min(MOST_LINE, line + PAY_OUT);
			}
			if (dist > line + SNAP) {
				this.part(server);
				return;
			}
			this.entityData.set(LINE, (float)line);
		}
		double strain = dist - line;
		if (strain <= 0.0 || dist < 1.0E-3) {
			return;
		}
		Vec3 dir = d.scale(1.0 / dist);
		double pull = Math.min(MOST_PULL, strain * PULL);
		// the boat (or ship, or thrower) is drawn toward the iron
		if (anchorEntity instanceof Ship ship) {
			if (level instanceof ServerLevel) {
				var hull = ship.hull();
				double heft = 1.0 + (hull != null ? hull.mass * SHIP_HEFT : 0.0);
				ship.tow(dir.scale(-pull / heft).multiply(1.0, 0.0, 1.0));
			}
		} else if (anchorEntity.isLocalInstanceAuthoritative()) {
			Vec3 drawn = dir.scale(-pull);
			if (anchorEntity instanceof AbstractBoat) {
				drawn = drawn.multiply(1.0, 0.0, 1.0);
			}
			anchorEntity.setDeltaMovement(anchorEntity.getDeltaMovement().add(drawn));
		}
		// and what it's in is held back
		if (level instanceof ServerLevel) {
			if (in instanceof GreatWhale whale) {
				whale.tow(dir.scale(pull * WHALE_SHARE));
			} else if (in instanceof WhaleCarcass carcass) {
				carcass.tow(dir.scale(pull * CARCASS_SHARE));
			} else if (skyWhale(in)) {
				in.setDeltaMovement(in.getDeltaMovement().add(dir.scale(pull * WHALE_SHARE)));
			}
		}
	}

	/** It drops free as a harpoon to pick up (and its line goes with it). */
	private void loose(ServerLevel level) {
		this.spawnAtLocation(level, new ItemStack(Whaling.HARPOON_ITEM));
		this.discard();
	}

	/** (0.7.232) Jaymo's Powers' aerwhale, hunted like a great whale: read by id (Powers is optional) and by the tag it wears spent. */
	static final String SKY_WHALE = "jaymospowers:aerwhale", SKY_WHALE_SPENT = "jaymospowers_spent_whale";

	static boolean skyWhale(Entity in) {
		return SKY_WHALE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(in.getType()).toString());
	}

	/** The harpoons lodged in {@code in}. */
	static List<Harpoon> in(ServerLevel level, Entity in) {
		return level.getEntitiesOfClass(Harpoon.class, in.getBoundingBox().inflate(8.0), h -> h.lodged() == in);
	}

	/** Cuts every line {@code player} holds (made fast to them, their boat or the ship they're aboard); how many. */
	static int cutLines(ServerLevel level, Player player) {
		List<Harpoon> cut = new ArrayList<>();
		for (Harpoon h : level.getEntitiesOfClass(Harpoon.class, player.getBoundingBox().inflate(NEAR), h -> h.holds(player))) {
			cut.add(h);
		}
		for (Harpoon h : cut) {
			h.part(level);
		}
		if (!cut.isEmpty()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		return cut.size();
	}

	/** (Client) whether {@code player} holds any line. */
	static boolean holdsLines(Player player) {
		return !player.level().getEntitiesOfClass(Harpoon.class, player.getBoundingBox().inflate(NEAR), h -> h.holds(player)).isEmpty();
	}

	private boolean holds(Player player) {
		Entity a = this.anchor();
		if (a == null) {
			return false;
		}
		return a == player || a == player.getVehicle() || a instanceof Ship ship && Ships.aboard(player) == ship;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0 * 160.0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		EntityReference.store(this.lodged, output, "Lodged");
		EntityReference.store(this.anchor, output, "Anchor");
		output.putFloat("Line", this.line());
		org.joml.Vector3fc s = this.entityData.get(SPOT), k = this.entityData.get(KNOT);
		output.putFloat("SpotX", s.x());
		output.putFloat("SpotY", s.y());
		output.putFloat("SpotZ", s.z());
		output.putFloat("KnotX", k.x());
		output.putFloat("KnotY", k.y());
		output.putFloat("KnotZ", k.z());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lodged = EntityReference.read(input, "Lodged");
		this.anchor = EntityReference.read(input, "Anchor");
		this.entityData.set(LINE, input.getFloatOr("Line", (float)FIRST_LINE));
		this.entityData.set(SPOT, new Vector3f(input.getFloatOr("SpotX", 0.0F), input.getFloatOr("SpotY", 0.0F), input.getFloatOr("SpotZ", 0.0F)));
		this.entityData.set(KNOT, new Vector3f(input.getFloatOr("KnotX", 0.0F), input.getFloatOr("KnotY", 0.0F), input.getFloatOr("KnotZ", 0.0F)));
	}
}
