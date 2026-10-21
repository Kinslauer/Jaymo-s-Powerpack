package com.jaymospowers.aether.gear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An aercloud glider: hold right-click in mid-air to glide. The fall slows to {@link #SINK} a tick (sneak to dive at
 * {@link #DIVE}); you steer by walking, forward at full strength, sideways at half and back at a quarter. The golden glider is
 * quicker and lasts longer; the blue one throws you {@link #BLUE_LIFT} blocks a tick up when you open it, the purple one
 * multiplies your sideways speed by {@link #PURPLE_PUSH}, each once until you land. Gliding costs one durability, uses up the
 * glide time (then a {@link #COOLDOWN_SPENT}-tick wait; letting go early waits only {@link #COOLDOWN_RELEASE}) and you never
 * take fall damage while it is open.
 * <p>
 * Players move on their own side (as every movement in the game does), so the steering happens on the gliding player's own
 * client, from {@code onUseTick}; the server only counts the time, wears the item and forgets the fall.
 */
public class AercloudGliderItem extends Item {
	// ---- tuning ----
	/** Vertical speed while gliding and while diving (blocks a tick, negative = down). */
	public static final double SINK = -0.025, DIVE = -0.15;
	/** Share of the forward push sideways and backwards. */
	public static final double SIDE = 0.5, BACK = 0.25;
	/** The blue glider's lift, the purple one's multiple of your sideways speed (and the least speed it multiplies, and the most it gives). */
	public static final double BLUE_LIFT = 2.0, PURPLE_PUSH = 6.0, PURPLE_MIN = 0.2, PURPLE_MAX = 3.0;
	/** Waits after the glide time runs out, and after letting go early (ticks). */
	public static final int COOLDOWN_SPENT = 80, COOLDOWN_RELEASE = 2;
	/** Air drag of the game's vertical movement (so the sink is what you end up with after it). */
	private static final double DRAG = 0.98;
	/** How long it takes to set the glider down: ticks before landing counts. */
	private static final int OPEN_GRACE = 2;

	/** The four gliders: id, durability (uses), glide time (ticks), push forward a tick (blocks a tick squared). */
	public enum Kind {
		COLD("cold", 5, 160, 0.03), GOLDEN("golden", 30, 400, 0.04), BLUE("blue", 3, 160, 0.03), PURPLE("purple", 3, 160, 0.03);

		public final String id;
		public final int durability, glideTicks;
		public final double push;

		Kind(String id, int durability, int glideTicks, double push) {
			this.id = id;
			this.durability = durability;
			this.glideTicks = glideTicks;
			this.push = push;
		}
	}

	/** The blue and purple boosts are spent until the player lands (on the local client only: the move is there). */
	private static boolean boostSpent;

	private final Kind kind;

	public AercloudGliderItem(Item.Properties properties, Kind kind) {
		super(properties.component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1.0F)));
		this.kind = kind;
	}

	static void init() {
	}

	/** Called every client tick: landing refuels the boosts. */
	public static void landed(Player player) {
		if (boostSpent && (player.onGround() || player.isInWater() || player.isInLava())) {
			boostSpent = false;
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
			|| player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			stack.hurtAndBreak(1, sp, hand);
			if (stack.isEmpty()) {
				return InteractionResult.CONSUME;
			}
		}
		if (level.isClientSide() && player.isLocalPlayer()) {
			this.boost(player);
		}
		player.startUsingItem(hand);
		level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.3F);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (!(user instanceof Player player)) {
			return;
		}
		int used = this.getUseDuration(stack, user) - remaining;
		user.resetFallDistance();
		boolean down = used > OPEN_GRACE && (player.onGround() || player.isInWater() || player.isInLava());
		if (down || used > this.kind.glideTicks || player.isPassenger()) {
			player.stopUsingItem();
			player.getCooldowns().addCooldown(stack, down ? COOLDOWN_RELEASE : COOLDOWN_SPENT);
			return;
		}
		if (level.isClientSide() && player.isLocalPlayer()) {
			this.glide(player);
			if (used % 4 == 0) {
				level.addParticle(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 0.0, -0.02, 0.0);
			}
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
		if (user instanceof Player player) {
			player.getCooldowns().addCooldown(stack, COOLDOWN_RELEASE);
		}
		return true;
	}

	/** The steering and the slow fall, once a tick, on the gliding player's own client. */
	private void glide(Player player) {
		Vec3 v = player.getDeltaMovement();
		double gravity = player.getGravity();
		double floor = (player.isShiftKeyDown() ? DIVE : SINK) / DRAG + gravity;
		double y = player.isShiftKeyDown() ? floor : Math.max(v.y, floor);
		Vec3 look = player.getLookAngle();
		double flat = Math.sqrt(look.x * look.x + look.z * look.z);
		double fx = flat < 1.0E-4 ? 0.0 : look.x / flat, fz = flat < 1.0E-4 ? 0.0 : look.z / flat;
		// forward is full, backward a quarter, sideways half (zza forward, xxa left)
		double forward = player.zza > 0.0F ? player.zza : player.zza * BACK;
		double side = player.xxa * SIDE;
		double ax = (fx * forward + fz * side) * this.kind.push;
		double az = (fz * forward - fx * side) * this.kind.push;
		player.setDeltaMovement(v.x + ax, y, v.z + az);
	}

	/** Opening the blue glider lifts you; the purple one multiplies your sideways speed. Once until you land. */
	private void boost(Player player) {
		if (boostSpent || this.kind != Kind.BLUE && this.kind != Kind.PURPLE) {
			return;
		}
		boostSpent = true;
		Vec3 v = player.getDeltaMovement();
		if (this.kind == Kind.BLUE) {
			player.setDeltaMovement(v.x, BLUE_LIFT, v.z);
			return;
		}
		double speed = Math.sqrt(v.x * v.x + v.z * v.z);
		double x = v.x, z = v.z;
		if (speed < PURPLE_MIN) {
			Vec3 look = player.getLookAngle();
			double flat = Math.max(1.0E-4, Math.sqrt(look.x * look.x + look.z * look.z));
			x = look.x / flat * PURPLE_MIN;
			z = look.z / flat * PURPLE_MIN;
			speed = PURPLE_MIN;
		}
		double scale = Math.min(PURPLE_PUSH, PURPLE_MAX / speed);
		player.setDeltaMovement(x * scale, v.y, z * scale);
	}
}
