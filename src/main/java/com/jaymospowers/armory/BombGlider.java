package com.jaymospowers.armory;

import com.jaymospowers.core.Soundscape;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * The Bomb Glider (Andy: "make it just dynamite it drops"): an iron board on a bat's wings, with a little burner under
 * its tail, that one player rides standing up. Our own design, model and texture.
 * <ul>
 * <li>Placed from its item on a block. Right-click it to step on; sneak-right-click it (or right-click it from on board)
 * to open its hold, or press the inventory key on board): a fuel slot and a rack of up to {@link #MAX_DYNAMITE} sticks.
 * Fuel or dynamite in hand loads straight in.</li>
 * <li>Flying: it glides forward on its own; W speeds up, S slows down, A/D turn, Jump climbs, Sneak dives. Burning fuel
 * (a blaze rod {@link #FUEL_BLAZE_TICKS}, a lava bucket {@link #FUEL_LAVA_TICKS}, the bucket given back) it hums and
 * puffs flame; out of fuel it only glides down. On the ground Jump lifts it straight off and W taxis it.</li>
 * <li>Getting off: Sneak once it's down (in the air Sneak dives), or Sneak and Jump together to bail out.</li>
 * <li>Left click (or the ability 1 key) drops a stick straight down, falling with the glider's speed: WeaponMod's
 * dynamite through {@link BombGliderCompat}, or primed TNT on a short fuse ({@link #TNT_FUSE}). Dropped from high up,
 * a fuse is stretched to last the fall ({@link #LANDED_FUSE} past touching down), so it never bursts in the air.</li>
 * <li>No fall-damage waiver (Andy's rule): a dive faster than {@link #SAFE_SINK} into the ground hurts the rider as a
 * fall from that high; a gentle glide down is a controlled descent. Flying into a wall fast hurts by the speed lost,
 * squared. {@link #HEALTH} worth of blows break it into its item and whatever it held.</li>
 * </ul>
 * The server flies it from its rider's keys ({@link ServerPlayer#getLastClientInput()}, as Kingdoms' ships sail); the
 * client's BombGliderInputMixin turns Sneak in the air into the dive bit so it doesn't dismount. It has no controlling
 * passenger, so the server moves it and the rider is never checked (or kicked) for floating.
 */
public class BombGlider extends Entity implements HasCustomInventoryScreen {
	/** The powered glider's drone and burner, at uneven beats. */
	static final Soundscape.Layer DRONE = Soundscape.Layer.of(0.6F, 0.8F, 1.6F, 1.8F, SoundEvents.BEACON_AMBIENT);
	static final Soundscape.Layer BURNER = Soundscape.Layer.of(0.2F, 0.3F, 1.45F, 1.75F, SoundEvents.BLAZE_BURN, SoundEvents.FIRE_AMBIENT);

	/** Damage it takes to break (blows, our own rider's excepted). */
	static final float HEALTH = 20.0F;
	/** How long a blaze rod and a lava bucket burn (ticks). */
	public static final int FUEL_BLAZE_TICKS = 1200, FUEL_LAVA_TICKS = 6000;
	/** Most dynamite (or TNT) its rack holds. */
	public static final int MAX_DYNAMITE = 16;
	/** Ticks between two drops. */
	static final int DROP_COOLDOWN = 8;
	/** Primed TNT's fuse when it drops TNT (vanilla 80 ticks). */
	static final int TNT_FUSE = 40;
	/** A stick dropped from high up: its fuse lasts the fall and then this long (ticks); and the most fall it reckons. */
	static final int LANDED_FUSE = 10, MAX_FALL_TICKS = 200;
	/** Air drag a tick reckoned for a falling stick (TNT's own; WeaponMod's 0.99 falls a little faster). */
	static final double FALL_DRAG = 0.98;
	/** How far under its middle a stick is let go (blocks). */
	static final double DROP_BELOW = 0.6;
	/** Under power, its forward speed (blocks/tick): cruising, W, S; and how quickly it gets there (share a tick). */
	static final double CRUISE = 0.55, BOOST = 0.85, SLOW = 0.3, ACCEL = 0.05;
	/** Diving gathers speed, climbing costs some (blocks/tick). */
	static final double DIVE_GAIN = 0.2, CLIMB_COST = 0.1;
	/** Out of fuel (or riderless): its glide's speed and sink (blocks/tick). */
	static final double GLIDE_SPEED = 0.45, GLIDE_SINK = 0.12;
	/** Jump's climb, Sneak's dive, level flight's sink (blocks/tick), and how quickly its rise or sink follows the keys. */
	static final double CLIMB = 0.3, DIVE = 0.75, LEVEL_SINK = 0.0, LIFT_EASE = 0.15;
	/** Degrees a tick it turns with A/D (half as fast on the ground). */
	static final float TURN = 4.5F;
	/** On the ground: W taxis it this fast (with fuel); its speed kept each tick otherwise. */
	static final double TAXI = 0.12, GROUND_KEEP = 0.7;
	/** In water it bobs up this fast and keeps this much of its speed a tick. */
	static final double FLOAT = 0.05, WATER_KEEP = 0.8;
	/** Sinking slower than this (blocks/tick) is a controlled descent: nothing builds toward a fall (HANDOFF's rule). */
	static final double SAFE_SINK = 0.5;
	/** A fall's first blocks are free (vanilla's 3); each block past them hurts the rider 1. */
	static final double FALL_FREE = 3.0;
	/** Speed lost against a wall (blocks/tick) that hurts, and how hard: rider damage = lost² x this. */
	static final double CRASH_SPEED = 0.35;
	static final float CRASH_DAMAGE = 24.0F;
	/** Its nose tips up and down with its climb, at most this far (degrees). */
	static final float MAX_PITCH = 35.0F;
	/** Client: its bank in a turn (degrees per degree turned a tick, the most, how quickly it eases in). */
	static final float ROLL_PER_TURN = 5.0F, MAX_ROLL = 25.0F, ROLL_EASE = 0.15F;
	/** Its deck's height above its feet: where the rider stands (blocks). */
	public static final double DECK = 0.3;
	/** Ticks between the burner's hum, its flame puffs, the wings' flap while gliding and the rider's readout. */
	static final int HUM_TICKS = 40, PUFF_TICKS = 2, FLAP_TICKS = 30, HUD_TICKS = 10;
	/** Its hold's slots. */
	public static final int FUEL_SLOT = 0, DYNAMITE_SLOT = 1;

	private static final EntityDataAccessor<Boolean> BURNING = SynchedEntityData.defineId(BombGlider.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(BombGlider.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DYNAMITE = SynchedEntityData.defineId(BombGlider.class, EntityDataSerializers.INT);

	/** Its fuel slot and its rack. */
	final SimpleContainer hold = new SimpleContainer(2);
	/** What the hold's screen shows: the burner's ticks left, and how long its fuel lasts in all. */
	final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return index == 0 ? BombGlider.this.burn : BombGlider.this.burnMax;
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 2;
		}
	};
	private int burn;
	private int burnMax;
	/** Server: its forward speed and its rise (+) or sink (-), blocks/tick. */
	private double speed;
	private double lift;
	/** Server: how far it has dropped faster than a controlled descent, toward a hard landing (blocks). */
	private double fallen;
	private float damage;
	private int dropCooldown;
	/** Server: it was burning last tick (for the sputter when it runs dry). */
	private boolean wasBurning;

	/** Client: its bank now and last tick (degrees, + to the right). */
	public float roll, rollO;

	public BombGlider(EntityType<? extends BombGlider> type, Level level) {
		super(type, level);
	}

	/** Fuel the burner takes. */
	public static boolean isFuel(ItemStack stack) {
		return stack.is(Items.BLAZE_ROD) || stack.is(Items.LAVA_BUCKET);
	}

	/** What its rack takes: WeaponMod's dynamite (when loaded) and TNT. */
	public static boolean isExplosive(ItemStack stack) {
		return stack.is(Items.TNT) || BombGliderCompat.isDynamite(stack);
	}

	/** Its fuel burning now (both sides). */
	public boolean burning() {
		return this.entityData.get(BURNING);
	}

	/** On the ground (both sides): Sneak gets off rather than dives. */
	public boolean landed() {
		return this.entityData.get(LANDED);
	}

	/** Sticks in its rack (both sides). */
	public int dynamite() {
		return this.entityData.get(DYNAMITE);
	}

	/** Its rider, on the server. */
	public @Nullable ServerPlayer pilot() {
		return this.getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	// ---- flying ------------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			// it banks into its turns
			this.rollO = this.roll;
			float turned = Mth.wrapDegrees(this.getYRot() - this.yRotO);
			this.roll += (Mth.clamp(turned * ROLL_PER_TURN, -MAX_ROLL, MAX_ROLL) - this.roll) * ROLL_EASE;
			return;
		}
		if (this.dropCooldown > 0) {
			this.dropCooldown--;
		}
		ServerPlayer pilot = this.pilot();
		Input input = pilot != null ? pilot.getLastClientInput() : Input.EMPTY;
		boolean ground = this.onGround();
		boolean water = this.isInWater();
		// (in the air the client sends Sneak as the sprint bit, so it doesn't dismount)
		boolean dive = input.sprint() || input.shift();
		// the burner: it burns while flown (on the ground only while taxiing or lifting off)
		boolean wants = pilot != null && (!ground || input.forward() || input.jump());
		if (wants && this.burn <= 0) {
			this.refuel(level, pilot);
		}
		boolean powered = wants && this.burn > 0;
		if (powered) {
			this.burn--;
		}
		if (this.wasBurning && this.burn <= 0 && pilot != null) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6F, 1.2F);
			pilot.sendOverlayMessage(Component.translatable("message.jaymospowers.bomb_glider.out_of_fuel").withStyle(ChatFormatting.GOLD));
		}
		this.wasBurning = this.burn > 0;
		this.entityData.set(BURNING, powered);
		// what the keys ask of it
		double wantSpeed, wantLift;
		if (water) {
			wantSpeed = powered && input.forward() ? TAXI : 0.0;
			wantLift = powered && input.jump() ? CLIMB : FLOAT;
		} else if (ground && !(powered && input.jump())) {
			wantSpeed = powered && input.forward() ? TAXI : 0.0;
			wantLift = -0.08;
		} else if (pilot == null) {
			wantSpeed = GLIDE_SPEED;
			wantLift = -GLIDE_SINK;
		} else if (powered) {
			wantSpeed = input.forward() ? BOOST : input.backward() ? SLOW : CRUISE;
			wantLift = input.jump() ? CLIMB : dive ? -DIVE : -LEVEL_SINK;
		} else {
			wantSpeed = GLIDE_SPEED;
			wantLift = dive ? -DIVE : -GLIDE_SINK;
		}
		if (!ground && !water && pilot != null) {
			if (dive) {
				wantSpeed += DIVE_GAIN;
			} else if (powered && input.jump()) {
				wantSpeed -= CLIMB_COST;
			}
		}
		if (ground || water) {
			this.speed = wantSpeed > this.speed ? this.speed + (wantSpeed - this.speed) * 0.3 : this.speed * (water ? WATER_KEEP : GROUND_KEEP);
			this.lift = wantLift;
		} else {
			this.speed += (wantSpeed - this.speed) * ACCEL;
			this.lift += (wantLift - this.lift) * LIFT_EASE;
		}
		if (Math.abs(this.speed) < 1.0E-3) {
			this.speed = 0.0;
		}
		// A/D turn it
		float yaw = this.getYRot();
		if (pilot != null && input.left() != input.right()) {
			yaw += (input.left() ? -TURN : TURN) * (ground ? 0.5F : 1.0F);
		}
		this.setYRot(yaw);
		Vec3 from = this.position();
		double radians = yaw * Mth.DEG_TO_RAD;
		Vec3 motion = new Vec3(-Mth.sin(radians) * this.speed, this.lift, Mth.cos(radians) * this.speed);
		this.move(MoverType.SELF, motion);
		Vec3 moved = this.position().subtract(from);
		this.setDeltaMovement(moved);
		if (this.horizontalCollision) {
			// into a wall: what speed it lost hurts
			double kept = moved.horizontalDistance();
			double lost = this.speed - kept;
			this.speed = kept;
			if (lost > CRASH_SPEED) {
				this.crash(level, pilot, lost);
				if (this.isRemoved()) {
					return;
				}
			}
		}
		this.landing(level, pilot, moved.y);
		this.entityData.set(LANDED, this.onGround());
		boolean flying = !this.onGround() && !water;
		this.setXRot(flying ? (float)Mth.clamp(-Mth.atan2(moved.y, Math.max(moved.horizontalDistance(), 0.05)) * Mth.RAD_TO_DEG,
			-MAX_PITCH, MAX_PITCH) : 0.0F);
		this.effects(level, pilot, powered, flying);
		// the rack's count, for the client (the left click, the screen)
		this.entityData.set(DYNAMITE, this.hold.getItem(DYNAMITE_SLOT).getCount());
	}

	/** The burner's next fuel from its slot: a blaze rod, or a lava bucket (the bucket back to its rider). */
	private void refuel(ServerLevel level, ServerPlayer pilot) {
		ItemStack fuel = this.hold.getItem(FUEL_SLOT);
		int ticks;
		if (fuel.is(Items.BLAZE_ROD)) {
			ticks = FUEL_BLAZE_TICKS;
			fuel.shrink(1);
		} else if (fuel.is(Items.LAVA_BUCKET)) {
			ticks = FUEL_LAVA_TICKS;
			fuel.shrink(1);
			pilot.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET), Prediction.SERVER_ONLY);
		} else {
			return;
		}
		this.hold.setChanged();
		this.burn = ticks;
		this.burnMax = ticks;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.7F, 1.3F);
	}

	/**
	 * Its fall: sinking faster than a controlled descent builds toward a hard landing; easing off (or levelling out) is
	 * a controlled descent and forgets it. Landing hard hurts the rider as a fall from that high, and the glider a little.
	 */
	private void landing(ServerLevel level, @Nullable ServerPlayer pilot, double dy) {
		// (its own fall is reckoned here, not by vanilla's, so a long gentle glide isn't a long fall)
		this.resetFallDistance();
		if (dy < -SAFE_SINK) {
			this.fallen -= dy;
		} else if (!this.onGround()) {
			this.fallen = 0.0;
		}
		if (!this.onGround()) {
			return;
		}
		double hard = this.fallen - FALL_FREE;
		this.fallen = 0.0;
		if (hard <= 0.0) {
			return;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 0.8F, 0.8F);
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.2, this.getZ(), 8, 0.6, 0.1, 0.6, 0.02);
		if (pilot != null) {
			pilot.hurtServer(level, level.damageSources().fall(), (float)hard);
		}
		this.hurtSelf(level, (float)hard * 0.5F);
	}

	/** Flown into a wall, {@code lost} blocks/tick of speed gone at once. */
	private void crash(ServerLevel level, @Nullable ServerPlayer pilot, double lost) {
		float hurt = (float)(lost * lost * CRASH_DAMAGE);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.0F, 0.7F);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.3, this.getZ(), 10, 0.5, 0.2, 0.5, 0.2);
		if (pilot != null) {
			pilot.hurtServer(level, level.damageSources().flyIntoWall(), hurt);
		}
		this.hurtSelf(level, hurt * 0.5F);
	}

	/** The burner's hum and flame, the wings' flap in a glide, the rider's readout. */
	private void effects(ServerLevel level, @Nullable ServerPlayer pilot, boolean powered, boolean flying) {
		double radians = this.getYRot() * Mth.DEG_TO_RAD;
		Vec3 back = new Vec3(Mth.sin(radians), 0.0, -Mth.cos(radians));
		if (powered) {
			if (this.tickCount % PUFF_TICKS == 0) {
				Vec3 nozzle = this.position().add(back.scale(1.0)).add(0.0, 0.1, 0.0);
				level.sendParticles(ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z, 2, 0.05, 0.05, 0.05, 0.01);
				level.sendParticles(ParticleTypes.SMOKE, nozzle.x, nozzle.y, nozzle.z, 1, 0.05, 0.05, 0.05, 0.01);
			}
			if (Soundscape.due(this, "glider.hum", HUM_TICKS * 3 / 4, HUM_TICKS * 5 / 4)) {
				Soundscape.play(level, this.position(), SoundSource.NEUTRAL, DRONE);
			}
			if (Soundscape.due(this, "glider.burner", 5, 10)) {
				Soundscape.play(level, this.position(), SoundSource.NEUTRAL, BURNER);
			}
		} else if (flying && pilot != null && this.tickCount % FLAP_TICKS == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.5F, 1.3F);
		}
		if (pilot != null && this.tickCount % HUD_TICKS == 0) {
			Component line;
			if (this.landed() && this.tickCount / 60 % 2 == 1) {
				line = Component.translatable("message.jaymospowers.bomb_glider.keys");
			} else if (this.burn > 0) {
				line = Component.translatable("message.jaymospowers.bomb_glider.hud", (this.burn + 19) / 20, this.dynamite());
			} else {
				line = Component.translatable("message.jaymospowers.bomb_glider.gliding", this.dynamite());
			}
			pilot.sendOverlayMessage(line.copy().withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- bombing -----------------------------------------------------------------------------------------------------

	/** Its rider's left click: one stick from the rack, let go straight down with the glider's speed. */
	void drop(ServerPlayer player) {
		if (!(this.level() instanceof ServerLevel level) || !this.hasPassenger(player) || this.dropCooldown > 0) {
			return;
		}
		ItemStack stack = this.hold.getItem(DYNAMITE_SLOT);
		if (stack.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.bomb_glider.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 at = this.position().add(0.0, -DROP_BELOW, 0.0);
		Entity bomb = BombGliderCompat.isDynamite(stack) ? BombGliderCompat.dynamite(level) : stack.is(Items.TNT) ? primedTnt(level, at, player) : null;
		if (bomb == null) {
			return;
		}
		bomb.snapTo(at.x, at.y, at.z, this.getYRot(), 0.0F);
		bomb.setDeltaMovement(this.getDeltaMovement());
		if (bomb instanceof Projectile projectile) {
			projectile.setOwner(player);
		}
		// it lasts the fall, whatever the height
		int fall = fallTicks(level, at, this.getDeltaMovement().y, bomb.getGravity());
		if (bomb instanceof PrimedTnt tnt) {
			tnt.setFuse(Math.max(TNT_FUSE, fall + LANDED_FUSE));
		} else {
			BombGliderCompat.atLeastFuse(bomb, fall + LANDED_FUSE);
		}
		level.addFreshEntity(bomb);
		stack.shrink(1);
		this.hold.setChanged();
		this.dropCooldown = DROP_COOLDOWN;
		this.entityData.set(DYNAMITE, this.hold.getItem(DYNAMITE_SLOT).getCount());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** A block of vanilla TNT, lit by {@code by} (so its blast is theirs), on a short fuse. */
	private static Entity primedTnt(ServerLevel level, Vec3 at, ServerPlayer by) {
		PrimedTnt tnt = new PrimedTnt(level, at.x, at.y, at.z, by);
		tnt.setFuse(TNT_FUSE);
		return tnt;
	}

	/** About how many ticks something let go at {@code at}, sinking {@code dy}, takes to reach the ground (or water) below. */
	static int fallTicks(ServerLevel level, Vec3 at, double dy, double gravity) {
		Vec3 floor = at.add(0.0, -level.getHeight(), 0.0);
		HitResult hit = level.clip(new ClipContext(at, floor, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
		double ground = hit.getType() == HitResult.Type.MISS ? level.getMinY() : hit.getLocation().y;
		double y = at.y, vy = dy;
		int ticks = 0;
		while (y > ground && ticks < MAX_FALL_TICKS) {
			vy -= gravity;
			y += vy;
			vy *= FALL_DRAG;
			ticks++;
		}
		return ticks;
	}

	// ---- its rider ---------------------------------------------------------------------------------------------------

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && this.getPassengers().isEmpty();
	}

	/** Its rider stands on the deck. */
	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		moveFunction.accept(passenger, this.getX(), this.getY() + DECK, this.getZ());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		double radians = this.getYRot() * Mth.DEG_TO_RAD;
		// off to its right, clear of the wing root
		return this.position().add(new Vec3(-Mth.cos(radians), 0.0, -Mth.sin(radians)).scale(1.4)).add(0.0, 0.1, 0.0);
	}

	// ---- players -----------------------------------------------------------------------------------------------------

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp) || !(this.level() instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if ((isFuel(held) || isExplosive(held)) && this.load(sp, held)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		if (this.hasPassenger(player) || player.isSecondaryUseActive()) {
			this.openHold(sp);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (this.getPassengers().isEmpty() && !player.isPassenger()) {
			player.startRiding(this);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	/** Fuel or dynamite in hand, loaded straight into its slot (as much as fits). False if none did. */
	private boolean load(ServerPlayer player, ItemStack held) {
		int slot = isFuel(held) ? FUEL_SLOT : DYNAMITE_SLOT;
		ItemStack in = this.hold.getItem(slot);
		int most = Math.min(held.getMaxStackSize(), slot == DYNAMITE_SLOT ? MAX_DYNAMITE : held.getMaxStackSize());
		if (!in.isEmpty() && !ItemStack.isSameItemSameComponents(in, held)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.bomb_glider.full").withStyle(ChatFormatting.GRAY));
			return true;
		}
		int n = Math.min(held.getCount(), most - in.getCount());
		if (n <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.bomb_glider.full").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (in.isEmpty()) {
			this.hold.setItem(slot, held.copyWithCount(n));
		} else {
			in.grow(n);
		}
		if (!player.isCreative()) {
			held.shrink(n);
		}
		this.hold.setChanged();
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.0F);
		return true;
	}

	/** The inventory key, on board (vanilla sends it here for a vehicle with its own screen). */
	@Override
	public void openCustomInventoryScreen(Player player) {
		if (player instanceof ServerPlayer sp && this.hasPassenger(player)) {
			this.openHold(sp);
		}
	}

	private void openHold(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BombGliderMenu(id, inventory, this.hold, this.data, this),
			Component.translatable("container.jaymospowers.bomb_glider")));
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return new ItemStack(Armory.BOMB_GLIDER);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved()) {
			return false;
		}
		Entity by = source.getEntity();
		if (by != null && this.hasPassenger(by)) {
			// its own rider's swings and its own bombs' blasts
			return false;
		}
		boolean creative = by instanceof Player player && player.isCreative();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 0.5F, 1.4F);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.3, this.getZ(), 5, 0.4, 0.1, 0.4, 0.1);
		if (creative) {
			this.breakApart(level, false);
		} else {
			this.hurtSelf(level, amount);
		}
		return true;
	}

	private void hurtSelf(ServerLevel level, float amount) {
		this.damage += amount;
		if (this.damage >= HEALTH) {
			this.breakApart(level, true);
		}
	}

	/** Broken: its item (unless a creative player broke it) and everything in its hold, and its rider falls. */
	private void breakApart(ServerLevel level, boolean dropItem) {
		this.ejectPassengers();
		if (dropItem && level.getGameRules().get(GameRules.ENTITY_DROPS)) {
			Containers.dropItemStack(level, this.getX(), this.getY() + 0.2, this.getZ(), new ItemStack(Armory.BOMB_GLIDER));
		}
		for (int i = 0; i < this.hold.getContainerSize(); i++) {
			ItemStack stack = this.hold.getItem(i);
			if (!stack.isEmpty()) {
				Containers.dropItemStack(level, this.getX(), this.getY() + 0.2, this.getZ(), stack.copy());
			}
		}
		this.hold.clearContent();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.3, this.getZ(), 12, 0.8, 0.2, 0.8, 0.02);
		this.discard();
	}

	// ---- saving ------------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(BURNING, false);
		entityData.define(LANDED, true);
		entityData.define(DYNAMITE, 0);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.burn = input.getIntOr("burn", 0);
		this.burnMax = input.getIntOr("burn_max", 0);
		this.damage = input.getFloatOr("damage", 0.0F);
		this.hold.setItem(FUEL_SLOT, input.read("fuel", ItemStack.CODEC).orElse(ItemStack.EMPTY));
		this.hold.setItem(DYNAMITE_SLOT, input.read("dynamite", ItemStack.CODEC).orElse(ItemStack.EMPTY));
		this.entityData.set(DYNAMITE, this.hold.getItem(DYNAMITE_SLOT).getCount());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("burn", this.burn);
		output.putInt("burn_max", this.burnMax);
		output.putFloat("damage", this.damage);
		ItemStack fuel = this.hold.getItem(FUEL_SLOT);
		if (!fuel.isEmpty()) {
			output.store("fuel", ItemStack.CODEC, fuel);
		}
		ItemStack dynamite = this.hold.getItem(DYNAMITE_SLOT);
		if (!dynamite.isEmpty()) {
			output.store("dynamite", ItemStack.CODEC, dynamite);
		}
	}
}
