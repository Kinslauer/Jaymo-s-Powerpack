package com.jaymospowers.armory;

import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.freerun.moves.Acrobatics;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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
 * The Hoverboard: the bomb glider's own hull (its model and texture, wings and burner left off) as the future's
 * courier board of Los Perrito, built at the Future Workbench ({@link FutureWorkbenchBlock}) and never found. One
 * player rides it standing.
 * <ul>
 * <li>Powered, it hovers {@link #HOVER} blocks over ground and water at a steady height, climbs gentle slopes (up to
 * {@link #CLIMB_RATE} a tick) and hums louder and higher with its speed; a trail of motes follows it.
 * W speeds up, S brakes, A/D turn, Jump hops, Sneak steps off.</li>
 * <li>It runs on a power cell, charged with redstone: right-click it with redstone dust ({@link #DUST_CHARGE} ticks)
 * or a redstone block ({@link #BLOCK_CHARGE}); a cell holds {@link #MAX_CHARGE}. Flat, it drops to the ground and the
 * rider pushes it along like a skateboard ({@link #PUSH}).</li>
 * <li>Dye: a dye right-click colours its hull, a dye on Sneak right-click its trail (the board keeps both when picked
 * up). Sneak right-click on it with an empty hand picks it up, charge and colours kept.</li>
 * <li>Tricks: in the air Jump throws one of Acrobatics' tricks (the keys pick the family, as for a freerunner), drawn
 * as the web slinger's pose is. Land with the trick done and you keep the board's speed and gain {@link #TRICK_BOOST};
 * land mid-trick, or from a drop faster than {@link #BAD_SINK}, and you are thrown.</li>
 * <li>Crash: lose more than {@link #CRASH_SPEED} of speed against a wall and the rider is flung, ragdolled, with every
 * fling capped ({@link #FLING_CAP}, {@link #FLING_UP_CAP}, {@link #THROW_DAMAGE}); the board skids away.</li>
 * </ul>
 * The server flies it from its rider's keys, as {@link BombGlider} is flown.
 */
public class Hoverboard extends Entity {
	/** The hum is a beacon's drone: its volume and pitch follow the speed, between these. */
	static final float HUM_QUIET = 0.15F, HUM_LOUD = 0.75F, HUM_LOW = 0.7F, HUM_HIGH = 1.6F;
	/** Height it hovers at over the surface (blocks), and how far below that it still finds a surface to follow. */
	static final double HOVER = 1.0, REACH = 2.5;
	/** The most it climbs or sinks a tick following the surface (blocks/tick). */
	static final double CLIMB_RATE = 0.45, DROP_RATE = 0.3;
	/** Rising at least this fast (blocks/tick) when the surface falls away is a ramp: it launches. */
	static final double LAUNCH_RISE = 0.25;
	/** Jump's hop off the surface, and gravity in the air (blocks/tick). */
	static final double HOP = 0.42, GRAVITY = 0.08;
	/** Powered top speed, its acceleration and how much it keeps coasting (blocks/tick; share a tick). */
	static final double MAX_SPEED = 0.8, ACCEL = 0.03, COAST = 0.985, BRAKE = 0.9;
	/** Flat or in the air: how fast pushing gets it going (blocks/tick), the push's gain and the ground's grip. */
	static final double PUSH = 0.2, PUSH_GAIN = 0.02, GROUND_KEEP = 0.93, AIR_KEEP = 0.995, WATER_KEEP = 0.85;
	/** Degrees a tick A/D turn it (and the share of that in the air). */
	static final float TURN = 4.0F, AIR_TURN = 0.4F;
	/** Charge: ticks a cell holds, a new board's, what a redstone dust and a block give. */
	public static final int MAX_CHARGE = 6000, START_CHARGE = 1200, DUST_CHARGE = 600, BLOCK_CHARGE = 5400;
	/** Ticks in the air before Jump throws a trick, and the speed a stuck trick adds (blocks/tick). */
	static final int TRICK_AFTER = 4;
	static final double TRICK_BOOST = 0.08;
	/** Landing faster than this (blocks/tick) throws the rider whatever they did. */
	static final double BAD_SINK = 1.0;
	/** Speed lost against a wall (blocks/tick) that crashes, and the damage per lost speed squared. */
	static final double CRASH_SPEED = 0.35;
	static final float CRASH_DAMAGE = 14.0F;
	/** Capped flings: the most speed a thrown rider gets (blocks/tick), the most lift, and the most it hurts. */
	static final double FLING_CAP = 0.9, FLING_UP_CAP = 0.45;
	static final float THROW_DAMAGE = 8.0F;
	/** After a crash or a throw: ticks it is stunned (no hover, no rider), the spin it skids with (degrees a tick) and the share of its lost speed it goes back with. */
	static final int STUN_TICKS = 60;
	static final float SKID_SPIN = 14.0F;
	static final double REBOUND = 0.4;
	/** Damage it takes to break. */
	static final float HEALTH = 20.0F;
	/** Its deck's height above its feet: where the rider stands (blocks). */
	public static final double DECK = 0.3;
	/** Ticks between the rider's readout. */
	static final int HUD_TICKS = 10;
	/** The hull's and the trail's colours when undyed (RGB; the hull's -1 is the texture as it is). */
	public static final int DEFAULT_HULL = -1, DEFAULT_TRAIL = 0x55E6FF;
	static final String HULL_KEY = "hull", TRAIL_KEY = "trail", CHARGE_KEY = "charge";

	private static final EntityDataAccessor<Integer> HULL = SynchedEntityData.defineId(Hoverboard.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TRAIL = SynchedEntityData.defineId(Hoverboard.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> HOVERING = SynchedEntityData.defineId(Hoverboard.class, EntityDataSerializers.BOOLEAN);

	private int charge = START_CHARGE;
	private float damage;
	/** Server: forward speed (negative skidding back), vertical speed, and the last tick's rise. */
	private double speed;
	private double vy;
	private double lastDy;
	/** Server: in the air (off a ramp or a hop), for how long, whether a trick was thrown, ticks stunned, Jump held last tick. */
	private boolean air;
	private int airTicks;
	private boolean tricked;
	private int stun;
	private boolean jumpWas;

	/** Client: its bank now and last tick (degrees). */
	public float roll, rollO;

	public Hoverboard(EntityType<? extends Hoverboard> type, Level level) {
		super(type, level);
	}

	/** Colour of its hull (RGB, or -1 undyed), both sides. */
	public int hull() {
		return this.entityData.get(HULL);
	}

	/** Colour of its trail (RGB), both sides. */
	public int trail() {
		return this.entityData.get(TRAIL);
	}

	/** Hovering now (both sides). */
	public boolean hovering() {
		return this.entityData.get(HOVERING);
	}

	public @Nullable ServerPlayer pilot() {
		return this.getFirstPassenger() instanceof ServerPlayer player ? player : null;
	}

	/** Its state from a board item. */
	void load(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return;
		}
		var tag = data.copyTag();
		this.entityData.set(HULL, tag.getIntOr(HULL_KEY, DEFAULT_HULL));
		this.entityData.set(TRAIL, tag.getIntOr(TRAIL_KEY, DEFAULT_TRAIL));
		this.charge = Mth.clamp(tag.getIntOr(CHARGE_KEY, START_CHARGE), 0, MAX_CHARGE);
	}

	/** The board as an item, charge and colours kept. */
	ItemStack toItem() {
		ItemStack stack = new ItemStack(Armory.HOVERBOARD);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt(HULL_KEY, this.hull());
			tag.putInt(TRAIL_KEY, this.trail());
			tag.putInt(CHARGE_KEY, this.charge);
		});
		return stack;
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
			this.rollO = this.roll;
			float turned = Mth.wrapDegrees(this.getYRot() - this.yRotO);
			this.roll += (Mth.clamp(turned * 4.0F, -20.0F, 20.0F) - this.roll) * 0.15F;
			return;
		}
		ServerPlayer pilot = this.pilot();
		Input input = pilot != null ? pilot.getLastClientInput() : Input.EMPTY;
		if (this.stun > 0) {
			this.stun--;
		}
		boolean powered = pilot != null && this.charge > 0 && this.stun <= 0;
		if (powered) {
			this.charge--;
			if (this.charge == 0) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.7F, 1.4F);
				pilot.sendOverlayMessage(Component.translatable("message.jaymospowers.hoverboard.flat").withStyle(ChatFormatting.GOLD));
			}
		}
		boolean water = this.isInWater();
		boolean jump = input.jump() && !this.jumpWas;
		this.jumpWas = input.jump();
		// A/D turn it (a skid spins it instead)
		float yaw = this.getYRot();
		if (this.stun > 0) {
			yaw += SKID_SPIN * Math.min(1.0F, this.stun / 20.0F);
		} else if (pilot != null && input.left() != input.right()) {
			yaw += (input.left() ? -TURN : TURN) * (this.air ? AIR_TURN : 1.0F);
		}
		this.setYRot(yaw);
		// its speed
		if (this.air) {
			this.speed *= AIR_KEEP;
		} else if (powered) {
			if (input.backward()) {
				this.speed *= BRAKE;
			} else {
				double want = input.forward() ? MAX_SPEED : this.speed * COAST;
				this.speed += Mth.clamp(want - this.speed, -ACCEL, ACCEL);
			}
		} else if (pilot != null && input.forward() && !water) {
			this.speed = Math.min(PUSH, this.speed + PUSH_GAIN);
		} else {
			this.speed *= water ? WATER_KEEP : GROUND_KEEP;
		}
		if (Math.abs(this.speed) < 1.0E-3) {
			this.speed = 0.0;
		}
		double radians = this.getYRot() * Mth.DEG_TO_RAD;
		double hx = -Mth.sin(radians) * this.speed, hz = Mth.cos(radians) * this.speed;
		// its height
		double dy;
		if (powered && !this.air) {
			double surface = this.surface(level, this.getX() + hx, this.getZ() + hz, this.getY() + 0.5);
			double target = surface + HOVER;
			if (jump) {
				this.leave(HOP);
				dy = this.vy;
			} else if (Double.isNaN(surface) || surface < this.getY() - HOVER - REACH || this.lastDy >= LAUNCH_RISE && target < this.getY() - 0.05) {
				// off the end of a ramp, or a cliff: it flies on with the rise it had
				this.leave(Math.max(this.lastDy, 0.0));
				dy = this.vy;
			} else {
				dy = Mth.clamp(target - this.getY(), -DROP_RATE, CLIMB_RATE);
			}
		} else if (water && !powered) {
			this.vy = 0.05;
			dy = this.vy;
		} else {
			this.vy -= GRAVITY;
			dy = this.vy;
		}
		Vec3 from = this.position();
		this.move(MoverType.SELF, new Vec3(hx, dy, hz));
		Vec3 moved = this.position().subtract(from);
		this.setDeltaMovement(moved);
		if (this.horizontalCollision) {
			double kept = moved.horizontalDistance();
			double lost = this.speed - kept;
			this.speed = kept;
			if (lost > CRASH_SPEED && this.stun <= 0) {
				this.crash(level, pilot, lost, new Vec3(hx, 0.0, hz));
				if (this.isRemoved()) {
					return;
				}
			}
		}
		// where it is now: back on the surface, or on the ground
		if (powered && this.air) {
			this.airTicks++;
			double surface = this.surface(level, this.getX(), this.getZ(), this.getY() + 0.5);
			if (this.vy <= 0.0 && !Double.isNaN(surface) && this.getY() <= surface + HOVER) {
				this.setPos(this.getX(), surface + HOVER, this.getZ());
				this.touchDown(level, pilot, this.vy);
			} else if (this.verticalCollision) {
				if (this.vy < 0.0) {
					this.touchDown(level, pilot, this.vy);
				} else {
					this.vy = 0.0;
				}
			} else if (jump && this.airTicks >= TRICK_AFTER) {
				this.trick(level, pilot, input);
			}
		} else if (!powered && this.onGround()) {
			if (this.air && this.vy < -BAD_SINK && pilot != null) {
				this.touchDown(level, pilot, this.vy);
			}
			this.air = false;
			this.airTicks = 0;
			this.tricked = false;
			this.vy = 0.0;
		}
		this.lastDy = moved.y;
		this.entityData.set(HOVERING, powered);
		this.setXRot(0.0F);
		this.effects(level, pilot, powered);
	}

	/** Takes off with vertical speed {@code rise}. */
	private void leave(double rise) {
		this.air = true;
		this.airTicks = 0;
		this.tricked = false;
		this.vy = rise;
	}

	/** The top of whatever is under (x, z), looking down from {@code fromY}: ground, or the water's surface; NaN if nothing. */
	private double surface(ServerLevel level, double x, double z, double fromY) {
		Vec3 at = new Vec3(x, fromY, z);
		HitResult hit = level.clip(new ClipContext(at, at.add(0.0, -(HOVER + REACH + 0.5), 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
			CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getLocation().y;
	}

	/** An Acrobatics trick off the keys, thrown while Jump is pressed in the air; drawn as the web slinger's pose is. */
	private void trick(ServerLevel level, ServerPlayer pilot, Input input) {
		WebPose now = pilot.getAttached(WebPower.POSE);
		if (now != null && WebPose.trick(now.kind()) && level.getGameTime() - now.start() < now.ticks()) {
			return;
		}
		int family = input.backward() ? Acrobatics.BACK : input.left() != input.right() ? Acrobatics.SIDE : Acrobatics.FORWARD;
		List<Acrobatics.Trick> tricks = Acrobatics.Trick.family(family, 2);
		Acrobatics.Trick pick = tricks.get(this.random.nextInt(tricks.size()));
		WebPower.pose(pilot, pick.kind, input.left() ? -1 : 1);
		this.tricked = true;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	/** Back on the surface at vertical speed {@code fell}: a stuck trick is a boost, a trick unfinished or a hard drop throws the rider. */
	private void touchDown(ServerLevel level, @Nullable ServerPlayer pilot, double fell) {
		boolean tricked = this.tricked;
		this.air = false;
		this.airTicks = 0;
		this.tricked = false;
		this.vy = 0.0;
		if (pilot == null) {
			return;
		}
		WebPose pose = pilot.getAttached(WebPower.POSE);
		boolean mid = pose != null && WebPose.trick(pose.kind()) && level.getGameTime() - pose.start() < pose.ticks();
		if (mid || -fell > BAD_SINK) {
			this.throwRider(level, pilot, Math.max(this.speed, 0.3));
			return;
		}
		if (tricked) {
			this.speed = Math.min(MAX_SPEED, this.speed + TRICK_BOOST);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.5F);
			MagicFx.ring(level, this.position().add(0.0, 0.1, 0.0), 1.2F, this.trail());
		}
	}

	/** Flown into a wall, {@code lost} blocks/tick of speed gone at once: the rider is flung, ragdolled, and the board skids away. */
	private void crash(ServerLevel level, @Nullable ServerPlayer pilot, double lost, Vec3 heading) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.0F, 0.7F);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.3, this.getZ(), 10, 0.5, 0.2, 0.5, 0.2);
		if (pilot != null) {
			this.throwRider(level, pilot, lost, heading);
		} else {
			this.stun = STUN_TICKS;
		}
		this.speed = -REBOUND * lost;
		this.hurtSelf(level, (float)Math.min(THROW_DAMAGE, lost * lost * CRASH_DAMAGE) * 0.5F);
	}

	private void throwRider(ServerLevel level, ServerPlayer pilot, double power) {
		double radians = this.getYRot() * Mth.DEG_TO_RAD;
		this.throwRider(level, pilot, power, new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians)));
	}

	/** Throws {@code pilot} off along {@code heading}: ragdolled, the fling and the hurt capped. The board is stunned and skids on. */
	private void throwRider(ServerLevel level, ServerPlayer pilot, double power, Vec3 heading) {
		this.ejectPassengers();
		Vec3 dir = heading.lengthSqr() < 1.0E-6 ? Vec3.ZERO : heading.normalize();
		double fling = Math.min(power, FLING_CAP);
		double up = Math.min(0.2 + fling * 0.3, FLING_UP_CAP);
		pilot.setDeltaMovement(dir.x * fling, up, dir.z * fling);
		pilot.connection.send(new ClientboundSetEntityMotionPacket(pilot.getId(), pilot.getDeltaMovement()));
		WebPower.pose(pilot, WebPose.RAGDOLL, 1);
		pilot.hurtServer(level, level.damageSources().flyIntoWall(), (float)Math.min(THROW_DAMAGE, power * power * CRASH_DAMAGE));
		this.stun = STUN_TICKS;
		this.air = true;
		this.airTicks = 0;
		this.tricked = false;
		level.playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** The hum (louder and higher with speed), the trail, the rider's readout. */
	private void effects(ServerLevel level, @Nullable ServerPlayer pilot, boolean powered) {
		double share = Mth.clamp(Math.abs(this.speed) / MAX_SPEED, 0.0, 1.0);
		if (powered) {
			if (Soundscape.due(this, "hoverboard.hum", 14, 20)) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL,
					(float)Mth.lerp(share, HUM_QUIET, HUM_LOUD), (float)Mth.lerp(share, HUM_LOW, HUM_HIGH));
			}
			if (share > 0.05) {
				double radians = this.getYRot() * Mth.DEG_TO_RAD;
				level.sendParticles(MagicFx.mote(this.trail(), 0.7F), this.getX() + Mth.sin(radians), this.getY() + 0.2, this.getZ() - Mth.cos(radians),
					1 + (int)(share * 2.0), 0.15, 0.03, 0.15, 0.0);
			}
		}
		if (pilot != null && this.tickCount % HUD_TICKS == 0) {
			Component line = this.charge > 0
				? Component.translatable("message.jaymospowers.hoverboard.hud", (this.charge + 19) / 20)
				: Component.translatable("message.jaymospowers.hoverboard.pushing");
			pilot.sendOverlayMessage(line.copy().withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- its rider ---------------------------------------------------------------------------------------------------

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && this.getPassengers().isEmpty();
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		moveFunction.accept(passenger, this.getX(), this.getY() + DECK, this.getZ());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		double radians = this.getYRot() * Mth.DEG_TO_RAD;
		return this.position().add(new Vec3(-Mth.cos(radians), 0.0, -Mth.sin(radians)).scale(1.2)).add(0.0, 0.1, 0.0);
	}

	// ---- players -----------------------------------------------------------------------------------------------------

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp) || !(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		DyeColor dye = held.get(DataComponents.DYE);
		if (dye != null) {
			// the hull, or (sneaking) the trail
			int rgb = dye.getTextureDiffuseColor() & 0xFFFFFF;
			this.entityData.set(player.isSecondaryUseActive() ? TRAIL : HULL, rgb);
			if (!player.isCreative()) {
				held.shrink(1);
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
			return InteractionResult.SUCCESS_SERVER;
		}
		int add = held.is(Items.REDSTONE) ? DUST_CHARGE : held.is(Items.REDSTONE_BLOCK) ? BLOCK_CHARGE : 0;
		if (add > 0) {
			if (this.charge >= MAX_CHARGE) {
				sp.sendOverlayMessage(Component.translatable("message.jaymospowers.hoverboard.full").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS_SERVER;
			}
			this.charge = Math.min(MAX_CHARGE, this.charge + add);
			if (!player.isCreative()) {
				held.shrink(1);
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.5F);
			MagicFx.ring(level, this.position().add(0.0, 0.3, 0.0), 0.8F, 0xFF3030);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (this.getPassengers().isEmpty() && player.isSecondaryUseActive() && held.isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.2, this.getZ(), this.toItem()));
			this.discard();
			return InteractionResult.SUCCESS_SERVER;
		}
		if (this.getPassengers().isEmpty() && !player.isPassenger() && !player.isSecondaryUseActive()) {
			player.startRiding(this);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
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
		return this.toItem();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved()) {
			return false;
		}
		Entity by = source.getEntity();
		if (by != null && this.hasPassenger(by)) {
			return false;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 0.5F, 1.4F);
		if (by instanceof Player player && player.isCreative()) {
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

	private void breakApart(ServerLevel level, boolean dropItem) {
		this.ejectPassengers();
		if (dropItem && level.getGameRules().get(GameRules.ENTITY_DROPS)) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.2, this.getZ(), this.toItem()));
		}
		level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.3, this.getZ(), 12, 0.8, 0.2, 0.8, 0.02);
		this.discard();
	}

	// ---- saving ------------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(HULL, DEFAULT_HULL);
		entityData.define(TRAIL, DEFAULT_TRAIL);
		entityData.define(HOVERING, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.charge = input.getIntOr("charge", START_CHARGE);
		this.damage = input.getFloatOr("damage", 0.0F);
		this.entityData.set(HULL, input.getIntOr("hull", DEFAULT_HULL));
		this.entityData.set(TRAIL, input.getIntOr("trail", DEFAULT_TRAIL));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("charge", this.charge);
		output.putFloat("damage", this.damage);
		output.putInt("hull", this.hull());
		output.putInt("trail", this.trail());
	}
}
