package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterPatrol;
import com.jaymospowers.power.zfighter.ZFighterPodTrip;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import com.jaymospowers.tinkering.smithing.Grade;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.49, Andy) The space pod itself: an entity built from {@link PodPart}s. Which slots are filled is {@link #FILLED},
 * which of those are damaged (a bent fin, a cracked canopy) is {@link #DAMAGED}; both are synced so the model draws
 * exactly the parts that are on. Fuel ({@link #FUEL}, 0 to {@code ZFighterSpace.MAX_FUEL}) needs a tank.
 * <p>
 * It stands where it was set down and settles onto the ground (a little gravity, like a boat's; it never drives). One
 * seat, inside the hull ({@link #SEAT}). Right-click it empty-handed for the star map ({@link ZFighterSpace#open}); sneak
 * and right-click to climb in; coal or blaze powder in hand fuels it, if it has a tank. Hits do nothing to it: the
 * parts come off to a hammer (the parts' callback, not here). Pods the Patrol and the raiders fly, or that are on a trip,
 * are {@link #locked()}: nobody can take them.
 * <p>
 * CONTRACT (track B calls only what is public here): {@link #has}, {@link #damaged},
 * {@link #attach}, {@link #detach}, {@link #complete}, {@link #fuel}, {@link #setFuel}, {@link #nextFree},
 * {@link #outermost}. Track A may add anything else.
 */
public class PodEntity extends Entity {
	/** (0.18.51) Hammered to a finish (flight-ready), and how well it was mended (0 to {@link PodGrades#MAX_GRADE}). */
	static final EntityDataAccessor<Boolean> FINISHED = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.BOOLEAN);
	static final EntityDataAccessor<Integer> GRADE = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> FILLED = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> DAMAGED = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.INT);
	/** (0.18.50) The grade of the fuel in the tank (0 = put in before grades; see {@code PodFuel}). */
	static final EntityDataAccessor<Integer> FUEL_GRADE = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.INT);
	/** (0.18.56) A Starlit Lens is fitted to the beacon mast (the flight screen then shows the world's mission zones). */
	static final EntityDataAccessor<Boolean> LENS = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.BOOLEAN);
	/** The modules in the keel's sockets, a nibble each (a {@link PodModules.Kind}'s ordinal + 1, 0 for an empty socket). */
	static final EntityDataAccessor<Integer> MODULES = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.INT);
	/** How many sockets the keel has, {@link PodModules#FEWEST_SOCKETS} to {@link PodModules#MOST_SOCKETS}. */
	static final EntityDataAccessor<Integer> SOCKETS = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.INT);
	/** The forging grade each part slot was hammered to, three bits a slot (a {@link Grade}'s ordinal + 1, 0 for not graded). */
	static final EntityDataAccessor<Long> FORGED = SynchedEntityData.defineId(PodEntity.class, EntityDataSerializers.LONG);

	// ---- tuning ----
	/** Its size (blocks), and how far it is tracked. */
	static final float WIDTH = 2.0F, HEIGHT = 3.5F;
	/** The pilot's seat: how high above its feet (blocks), inside the hull. */
	public static final double SEAT = 1.1;
	/** (0.18.51) With two seated, how far (blocks) each sits from the middle. */
	static final double SEAT_APART = 0.4;
	/**
	 * Andy (2026-11-25): "you should be sitting in that cockpit". Drawn as Alex's Caves' submarine (client PodSubmarine),
	 * the pilot sits in its seat under the glass: this high above the pod's feet (the seat's top, 1.0 blocks up, less a
	 * seated body's 0.625 from its feet to the underside of its thighs), this far forward of the middle, and two this far
	 * apart. Read on both sides, so the server puts the pilot where the client draws the seat.
	 */
	static final boolean SUBMARINE = FabricLoader.getInstance().isModLoaded("alexscaves");
	public static final double SUB_SEAT = 0.38, SUB_SEAT_FORWARD = 0.31, SUB_SEAT_APART = 0.3;
	/** A seated pilot can look this far round (degrees) from the pod's heading; their body keeps to it, legs under the panel. */
	static final float LOOK_ROUND = 105.0F;
	/** The canopy (client only): ticks to tilt open, when it starts to shut, and when it is shut, after someone gets in or out. */
	static final int CANOPY_OPENED = 6, CANOPY_SHUTS = 18, CANOPY_SHUT = 30;
	/** Settling: gravity a tick, the share of fall speed kept, and how often (ticks) a pod at rest checks its footing. */
	static final double GRAVITY = 0.06, DRAG = 0.98;
	static final int REST_CHECK = 10;
	/** Where the pilot is put when they climb out: how far to the side (blocks). */
	static final double DISMOUNT = 2.4;

	/** The sockets a pod made from nothing has, and the slots the cargo hold can have at most. */
	static final int DEFAULT_SOCKETS = PodModules.FEWEST_SOCKETS, HOLD_SLOTS = 27;
	/** A pod saved before modules had its legs, and this many sockets. */
	static final int LEGACY_SOCKETS = 3;
	/** Bits a part slot's forging grade takes in {@link #FORGED}. */
	static final int FORGED_BITS = 3, FORGED_MASK = (1 << FORGED_BITS) - 1;

	public static EntityType<PodEntity> TYPE;

	/** The cargo hold ({@link PodModules.Kind#HOLD}); only the first {@link PodClass#holdRows} rows show. Server only, saved. */
	private final SimpleContainer hold = new SimpleContainer(HOLD_SLOTS);

	/** Server: held up by a trip or a flying Patrol pod (it does not settle). Not saved. */
	private boolean hovering;
	/** Client: how many sat in it last tick (-1 before its first), and the tick that changed, for the canopy. */
	private int canopyRiders = -1;
	private int canopyMoved = -CANOPY_SHUT;

	public PodEntity(EntityType<? extends PodEntity> type, Level level) {
		super(type, level);
	}

	/** Registers the pod's entity type, and clears a trip's tag off a pod loaded without its trip. */
	public static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("space_pod"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<PodEntity>of(PodEntity::new, MobCategory.MISC)
			.noLootTable()
			.sized(WIDTH, HEIGHT)
			.clientTrackingRange(10)
			.updateInterval(1)
			.build(key));
		// a pod that was on a trip when the server stopped is just a pod again (it keeps its parts)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PodEntity pod && pod.entityTags().contains(ZFighterPodTrip.TAG) && !ZFighterPodTrip.tracked(pod)) {
				pod.removeTag(ZFighterPodTrip.TAG);
			}
		});
	}

	/**
	 * A pod standing at {@code at} (its feet): a bare keel, or every part sound when {@code complete}, holding {@code fuel}.
	 * Added to the world; the parts of a bare one are for the caller to attach.
	 */
	public static PodEntity spawn(ServerLevel level, Vec3 at, boolean complete, int fuel) {
		PodEntity pod = new PodEntity(TYPE, level);
		pod.setPos(at.x, at.y, at.z);
		pod.entityData.set(FILLED, PodPart.KEEL.bits());
		if (complete) {
			pod.makeComplete();
		}
		pod.setFuel(fuel);
		level.addFreshEntity(pod);
		return pod;
	}

	/** Sets the parts outright (a pod rebuilt from a trip's record). */
	public void setParts(int filled, int damaged) {
		entityData.set(FILLED, filled & PodPart.ALL_SLOTS);
		entityData.set(DAMAGED, damaged & filled & PodPart.ALL_SLOTS);
	}

	/** The first part {@link #canLaunch()} lacks (as told to the pilot), or null when it can lift. */
	public @Nullable PodPart missingForLaunch() {
		for (PodPart part : new PodPart[] {PodPart.KEEL, PodPart.FIN, PodPart.BELL, PodPart.TANK, PodPart.CANOPY}) {
			if (nextFree(part) >= 0) {
				return part;
			}
		}
		return null;
	}

	/** Held up in the air by a trip or a flying pod (server only). */
	public void setHovering(boolean hovering) {
		this.hovering = hovering;
	}

	/** On a trip, or a Patrol's or raider's pod: nobody can open it or climb in. */
	public boolean locked() {
		return entityTags().contains(ZFighterPodTrip.TAG) || entityTags().contains(ZFighterPatrol.POD_TAG) || entityTags().contains(ZFighterPatrol.FLYING_TAG)
			|| wreckDeck() >= 0;
	}

	/** (0.18.51) Which Maw wreck deck this pod is the wreck of ({@link PodWrecks}), or -1 for a pod that isn't one. */
	public int wreckDeck() {
		return PodWrecks.deckOf(this);
	}

	// ---------------------------------------------------------------- standing, and the pilot

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			// someone got in or out: the canopy tilts up and shuts after (a pod first seen with a pilot in is just shut)
			int riders = getPassengers().size();
			if (riders != canopyRiders) {
				if (canopyRiders >= 0) {
					canopyMoved = tickCount;
				}
				canopyRiders = riders;
			}
			return;
		}
		if (hovering) {
			return;
		}
		// it settles onto the ground and stays: a pod at rest only checks its footing now and then
		boolean rest = onGround() && getDeltaMovement().y >= -GRAVITY;
		if (rest && (tickCount + getId()) % REST_CHECK != 0) {
			return;
		}
		setDeltaMovement(0.0, onGround() ? -GRAVITY : getDeltaMovement().y - GRAVITY, 0.0);
		move(MoverType.SELF, getDeltaMovement());
		setDeltaMovement(0.0, onGround() ? 0.0 : getDeltaMovement().y * DRAG, 0.0);
		resetFallDistance();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && getPassengers().size() < seats();
	}

	/**
	 * Client: how far open the canopy is, 0 (shut) to 1, tilting up as someone gets in or out and shutting after: up over
	 * {@link #CANOPY_OPENED} ticks, held while they climb, down by {@link #CANOPY_SHUT}.
	 */
	public float canopyOpen(float partialTicks) {
		float t = tickCount - canopyMoved + partialTicks;
		if (t < 0.0F || t >= CANOPY_SHUT) {
			return 0.0F;
		}
		if (t < CANOPY_OPENED) {
			return ease(t / CANOPY_OPENED);
		}
		return t < CANOPY_SHUTS ? 1.0F : 1.0F - ease((t - CANOPY_SHUTS) / (CANOPY_SHUT - CANOPY_SHUTS));
	}

	private static float ease(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	/** The pilot sits inside the hull; with a second seat (grade II) the two sit side by side. */
	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		double apart = SUBMARINE ? SUB_SEAT_APART : SEAT_APART;
		double side = 0.0;
		if (getPassengers().size() > 1) {
			side = getPassengers().indexOf(passenger) == 0 ? -apart : apart;
		}
		double forward = SUBMARINE ? SUB_SEAT_FORWARD : 0.0;
		float radians = getYRot() * Mth.DEG_TO_RAD;
		float sin = Mth.sin(radians), cos = Mth.cos(radians);
		// forward is (-sin, cos), the right hand (-cos, -sin)
		moveFunction.accept(passenger, getX() - cos * side - sin * forward, getY() + (SUBMARINE ? SUB_SEAT : SEAT),
			getZ() - sin * side + cos * forward);
		if (SUBMARINE && passenger instanceof LivingEntity living) {
			living.setYBodyRot(getYRot());
		}
	}

	/** Seated in the cockpit, the pilot's body faces the way the pod does and they can look about {@link #LOOK_ROUND} degrees. */
	@Override
	public void onPassengerTurned(Entity passenger) {
		if (!SUBMARINE) {
			return;
		}
		passenger.setYBodyRot(getYRot());
		float off = Mth.wrapDegrees(passenger.getYRot() - getYRot());
		float kept = Mth.clamp(off, -LOOK_ROUND, LOOK_ROUND);
		passenger.yRotO += kept - off;
		passenger.setYRot(passenger.getYRot() + kept - off);
		passenger.setYHeadRot(passenger.getYRot());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		float radians = getYRot() * Mth.DEG_TO_RAD;
		// out of the hatch, on its right
		return position().add(new Vec3(-Mth.cos(radians), 0.0, -Mth.sin(radians)).scale(DISMOUNT)).add(0.0, 0.1, 0.0);
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	/** Blows do nothing (it is attackable so the hammer's callback fires); it is taken apart, not broken. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || locked()) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer server) || !(level() instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		// a module used on the pod fits a socket; the last one comes out sneaking at the bay with an empty hand; the hold opens at the bay
		if (PodModules.tryFit(server, this, held)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		boolean bay = location.y < PodModules.BAY_HEIGHT;
		if (held.isEmpty() && bay) {
			if (player.isSecondaryUseActive()) {
				PodModules.tryTake(server, this);
				return InteractionResult.SUCCESS_SERVER;
			}
			if (PodModules.has(this, PodModules.Kind.HOLD)) {
				PodHold.open(server, this);
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		// (Patch 9, tr_tow) sneaking at a crashed pod with an empty hand and a towing pod near: the line is made fast
		if (held.isEmpty() && !bay && player.isSecondaryUseActive() && PodTow.hook(server, this)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		com.jaymospowers.power.zfighter.pod.PodLog.note(server, PodLog.Step.KEEL);
		// (0.18.51) a world's own part, sneaking: the pod is mended a grade better
		if (player.isSecondaryUseActive() && PodGrades.tryGrade(server, this, held)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		// (0.18.56) a Starlit Lens set in the beacon mast
		if (com.jaymospowers.power.zfighter.backhome.BackHome.fitLens(server, this, held)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		int worth = ZFighterSpace.fuelWorth(held);
		if (worth > 0) {
			fuelUp(server, held, worth);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (player.isSecondaryUseActive() && getPassengers().size() < seats() && !player.isPassenger()) {
			player.startRiding(this);
			return InteractionResult.SUCCESS_SERVER;
		}
		ZFighterSpace.open(server, this);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Coal or blaze powder used on it: into the tank, if it has one. */
	private void fuelUp(ServerPlayer player, ItemStack held, int worth) {
		if (!has(PodPart.TANK, 0)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.no_tank").withStyle(ChatFormatting.GRAY));
			return;
		}
		int fuel = fuel();
		if (fuel >= maxFuel()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_fuel_full", maxFuel()).withStyle(ChatFormatting.GRAY));
			return;
		}
		int now = Math.min(maxFuel(), fuel + worth);
		setFuelGrade(com.jaymospowers.power.zfighter.fuel.PodFuel.mixed(this, held));
		setFuel(now);
		held.consume(1, player);
		level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_fuel", now, maxFuel()).withStyle(ChatFormatting.GOLD));
		PodLog.note(player, PodLog.Step.FUELED);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FINISHED, false);
		builder.define(GRADE, 0);
		builder.define(FILLED, 0);
		builder.define(DAMAGED, 0);
		builder.define(FUEL, 0);
		builder.define(FUEL_GRADE, 0);
		builder.define(LENS, false);
		builder.define(MODULES, 0);
		builder.define(SOCKETS, DEFAULT_SOCKETS);
		builder.define(FORGED, 0L);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		entityData.set(GRADE, Mth.clamp(input.getIntOr("grade", 0), 0, PodGrades.MAX_GRADE));
		entityData.set(FILLED, input.getIntOr("filled", 0));
		entityData.set(DAMAGED, input.getIntOr("damaged", 0));
		// a pod saved before pods were finished by hand is finished if it was whole
		entityData.set(FINISHED, input.getBooleanOr("finished", complete()));
		entityData.set(FUEL, input.getIntOr("fuel", 0));
		entityData.set(FUEL_GRADE, input.getIntOr("fuel_grade", 0));
		entityData.set(LENS, input.getBooleanOr("lens", false));
		entityData.set(FORGED, input.getLongOr("forged", 0L));
		// a pod saved before modules flew on its legs
		boolean legacy = input.getInt("modules").isEmpty();
		entityData.set(SOCKETS, Mth.clamp(input.getIntOr("sockets", legacy ? LEGACY_SOCKETS : DEFAULT_SOCKETS), PodModules.FEWEST_SOCKETS, PodModules.MOST_SOCKETS));
		entityData.set(MODULES, legacy ? PodModules.Kind.LEGS.stored() : input.getIntOr("modules", 0));
		hold.clearContent();
		NonNullList<ItemStack> held = NonNullList.withSize(HOLD_SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input.childOrEmpty("hold"), held);
		for (int i = 0; i < HOLD_SLOTS; i++) {
			hold.setItem(i, held.get(i));
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("grade", entityData.get(GRADE));
		output.putBoolean("finished", entityData.get(FINISHED));
		output.putInt("filled", entityData.get(FILLED));
		output.putInt("damaged", entityData.get(DAMAGED));
		output.putInt("fuel", entityData.get(FUEL));
		output.putInt("fuel_grade", entityData.get(FUEL_GRADE));
		output.putBoolean("lens", entityData.get(LENS));
		output.putLong("forged", entityData.get(FORGED));
		output.putInt("sockets", entityData.get(SOCKETS));
		output.putInt("modules", entityData.get(MODULES));
		NonNullList<ItemStack> held = NonNullList.withSize(HOLD_SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < HOLD_SLOTS; i++) {
			held.set(i, hold.getItem(i));
		}
		ContainerHelper.saveAllItems(output.child("hold"), held);
	}

	// ---------------------------------------------------------------- the contract

	/** The filled slots, as {@link PodPart} bits. */
	public int filled() {
		return entityData.get(FILLED);
	}

	/** The damaged slots (always a subset of {@link #filled()}). */
	public int damagedMask() {
		return entityData.get(DAMAGED);
	}

	/** Whether slot {@code index} of {@code part} is on. */
	public boolean has(PodPart part, int index) {
		return (filled() & part.bit(index)) != 0;
	}

	/** Whether that slot is on and damaged. */
	public boolean damaged(PodPart part, int index) {
		return (damagedMask() & part.bit(index)) != 0;
	}

	/** How many of {@code part}'s slots are on. */
	public int countOf(PodPart part) {
		return Integer.bitCount(filled() & part.bits());
	}

	/** The first empty slot of {@code part}, or -1 when every one is filled. */
	public int nextFree(PodPart part) {
		for (int i = 0; i < part.count; i++) {
			if (!has(part, i)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Puts {@code part} into its first free slot (damaged or sound). The keel must be on before anything else, and a part
	 * only goes on when the part before it in {@link PodPart#ORDER} has at least one slot filled. False when it can't.
	 */
	public boolean attach(PodPart part, boolean isDamaged) {
		return attach(part, isDamaged, 0);
	}

	/** As {@link #attach(PodPart, boolean)}, with the forging grade the part was hammered to ({@code forged}: a {@link Grade} ordinal + 1, or 0 for none). */
	public boolean attach(PodPart part, boolean isDamaged, int forged) {
		int index = nextFree(part);
		if (index < 0) {
			return false;
		}
		if (part != PodPart.KEEL) {
			int before = PodPart.ORDER.indexOf(part) - 1;
			if (countOf(PodPart.ORDER.get(before)) == 0) {
				return false;
			}
		}
		int bit = part.bit(index);
		setForged(bit, forged);
		entityData.set(FILLED, filled() | bit);
		entityData.set(DAMAGED, isDamaged ? damagedMask() | bit : damagedMask() & ~bit);
		if (isDamaged) {
			setFinished(false);
		}
		return true;
	}

	/** The outermost filled slot's bit (the last in {@link PodPart#ORDER} that has something on), or 0 on a bare keel-less pod. */
	public int outermost() {
		int filled = filled();
		for (int i = PodPart.ORDER.size() - 1; i >= 0; i--) {
			PodPart part = PodPart.ORDER.get(i);
			for (int j = part.count - 1; j >= 0; j--) {
				if ((filled & part.bit(j)) != 0) {
					return part.bit(j);
				}
			}
		}
		return 0;
	}

	/**
	 * Takes the slot {@code bit} off. Returns the part, or null if it wasn't on. Whether it was damaged is told by
	 * {@code wasDamaged[0]} when an array is passed. Fuel is lost with the tank.
	 */
	public @Nullable PodPart detach(int bit, boolean @Nullable [] wasDamaged) {
		if ((filled() & bit) == 0) {
			return null;
		}
		PodPart part = PodPart.ofBit(bit);
		if (wasDamaged != null) {
			wasDamaged[0] = (damagedMask() & bit) != 0;
		}
		entityData.set(FILLED, filled() & ~bit);
		entityData.set(DAMAGED, damagedMask() & ~bit);
		setForged(bit, 0);
		setFinished(false);
		if (part == PodPart.TANK) {
			entityData.set(FUEL, 0);
		}
		return part;
	}

	/** Every slot filled and none damaged: fit to fly. */
	public boolean complete() {
		return filled() == PodPart.ALL_SLOTS && damagedMask() == 0;
	}

	/** Whether it has what a lift needs: keel, every fin on, bell, tank and canopy on (plates and mast may be missing or damaged). */
	public boolean launchParts() {
		return has(PodPart.KEEL, 0) && countOf(PodPart.FIN) == 3 && has(PodPart.BELL, 0) && has(PodPart.TANK, 0) && has(PodPart.CANOPY, 0);
	}

	/** (0.18.51) Whether it can lift at all: it has {@link #launchParts} and a hammer has finished it. */
	public boolean canLaunch() {
		return finished() && launchParts();
	}

	/** (0.18.51) Whether a hammer blow finishes it (rather than takes a part off): it could fly but hasn't been finished. */
	public boolean readyToFinish() {
		return !finished() && launchParts();
	}

	public boolean finished() {
		return entityData.get(FINISHED);
	}

	public void setFinished(boolean finished) {
		entityData.set(FINISHED, finished);
	}

	/** (0.18.51) How well it was mended, 0 (as built) to {@link PodGrades#MAX_GRADE}. */
	public int grade() {
		return entityData.get(GRADE);
	}

	public void setGrade(int grade) {
		entityData.set(GRADE, Mth.clamp(grade, 0, PodGrades.MAX_GRADE));
	}

	/** (0.18.51) How many can ride: the pilot, and from grade {@link PodGrades#SECOND_SEAT_GRADE} a passenger. */
	public int seats() {
		return grade() >= PodGrades.SECOND_SEAT_GRADE || PodModules.has(this, PodModules.Kind.SEAT) ? 2 : 1;
	}

	/** (0.18.56) Whether a Starlit Lens is fitted to the mast. */
	public boolean lens() {
		return entityData.get(LENS);
	}

	public void setLens(boolean lens) {
		entityData.set(LENS, lens);
	}

	public int fuel() {
		return has(PodPart.TANK, 0) ? entityData.get(FUEL) : 0;
	}

	public void setFuel(int fuel) {
		entityData.set(FUEL, Mth.clamp(fuel, 0, maxFuel()));
	}

	/** How much fuel the tank holds: the base, the class's share, and half as much again with an extra tank. */
	public int maxFuel() {
		int base = ZFighterSpace.MAX_FUEL + PodClass.of(this).fuel;
		return PodModules.has(this, PodModules.Kind.EXTRA_TANK) ? Math.round(base * PodModules.EXTRA_TANK_SHARE) : base;
	}

	/** (0.18.50) The stored grade of the fuel in the tank; read it through {@code PodFuel.effective}. */
	public int fuelGrade() {
		return entityData.get(FUEL_GRADE);
	}

	public void setFuelGrade(int grade) {
		entityData.set(FUEL_GRADE, Mth.clamp(grade, 0, 3));
	}

	/** Fills every slot, sound, as the Patrol's pods and the raiders' kept pod are. */
	public void makeComplete() {
		entityData.set(FILLED, PodPart.ALL_SLOTS);
		entityData.set(DAMAGED, 0);
		setFinished(true);
		// a whole pod lands on its legs
		if (!PodModules.has(this, PodModules.Kind.LEGS)) {
			PodModules.fit(this, PodModules.Kind.LEGS);
		}
	}

	// ---------------------------------------------------------------- sockets and modules

	/** How many sockets the keel has. */
	public int sockets() {
		return entityData.get(SOCKETS);
	}

	/** Sets the keel's sockets ({@link PodModules#FEWEST_SOCKETS} to {@link PodModules#MOST_SOCKETS}); modules past the new count come out. */
	public void setSockets(int sockets) {
		int now = Mth.clamp(sockets, PodModules.FEWEST_SOCKETS, PodModules.MOST_SOCKETS);
		entityData.set(SOCKETS, now);
		int modules = entityData.get(MODULES);
		int keep = (1 << (4 * now)) - 1;
		if ((modules & ~keep) != 0) {
			entityData.set(MODULES, modules & keep);
		}
	}

	/** The module in {@code socket}, or null when it is empty or there is no such socket. */
	public PodModules.@Nullable Kind moduleAt(int socket) {
		if (socket < 0 || socket >= sockets()) {
			return null;
		}
		return PodModules.Kind.ofStored(entityData.get(MODULES) >> (4 * socket) & 15);
	}

	/** Puts {@code kind} into {@code socket} (null empties it). */
	void setModule(int socket, PodModules.@Nullable Kind kind) {
		int shift = 4 * socket;
		entityData.set(MODULES, entityData.get(MODULES) & ~(15 << shift) | (kind == null ? 0 : kind.stored()) << shift);
	}

	/** The cargo hold. */
	public SimpleContainer holdContainer() {
		return hold;
	}

	/** Whether the hold has nothing in it. */
	public boolean holdEmpty() {
		return hold.isEmpty();
	}

	/** Every stack in the hold, to drop when the pod is taken apart. */
	public List<ItemStack> holdContents() {
		List<ItemStack> all = new ArrayList<>();
		for (int i = 0; i < hold.getContainerSize(); i++) {
			if (!hold.getItem(i).isEmpty()) {
				all.add(hold.removeItemNoUpdate(i));
			}
		}
		return all;
	}

	// ---------------------------------------------------------------- the forging each part was given

	private static int slotOf(int bit) {
		return Integer.numberOfTrailingZeros(bit);
	}

	/** The forging grade stored for slot {@code bit}: a {@link Grade} ordinal + 1, or 0 for one nobody graded. */
	public int forgedAt(int bit) {
		return (int)(entityData.get(FORGED) >> FORGED_BITS * slotOf(bit)) & FORGED_MASK;
	}

	void setForged(int bit, int forged) {
		int shift = FORGED_BITS * slotOf(bit);
		long cleared = entityData.get(FORGED) & ~((long)FORGED_MASK << shift);
		entityData.set(FORGED, cleared | (long)(forged & FORGED_MASK) << shift);
	}

	/** The grade slot {@code index} of {@code part} was hammered to (Well Forged when nobody graded it). */
	public Grade forgedGrade(PodPart part, int index) {
		int forged = forgedAt(part.bit(index));
		return forged <= 0 ? Grade.WELL : Grade.values()[Math.min(forged, Grade.values().length) - 1];
	}

	// ---------------------------------------------------------------- what a trip carries

	/** What a trip must carry from one pod to the pod that lands: the sockets and modules, the forging, the hold. */
	public record Extras(int sockets, int modules, long forged, List<ItemStack> hold) {
	}

	public Extras extras() {
		List<ItemStack> held = new ArrayList<>();
		for (int i = 0; i < HOLD_SLOTS; i++) {
			held.add(hold.getItem(i).copy());
		}
		return new Extras(sockets(), entityData.get(MODULES), entityData.get(FORGED), held);
	}

	public void applyExtras(Extras extras) {
		entityData.set(SOCKETS, extras.sockets());
		entityData.set(MODULES, extras.modules());
		entityData.set(FORGED, extras.forged());
		for (int i = 0; i < HOLD_SLOTS; i++) {
			hold.setItem(i, i < extras.hold().size() ? extras.hold().get(i).copy() : ItemStack.EMPTY);
		}
	}
}
