package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * The Monk. Bare hands (or a boxer's fist gear, Jung's {@code Boxing}) hit like a weapon ({@link ClassPerks#MONK_FIST_DAMAGE}) while
 * no plate is worn; the first of Jung's boxing styles is taught at the start and the rest are the Monk's to learn from boxing
 * teachers. The class key does the rest:
 * <ul>
 * <li><b>Catch an arrow:</b> with an arrow in the air toward you, the key puts you in a catching stance for
 * {@link #CATCH_WINDOW} ticks: the first arrow that comes within {@link #CATCH_REACH} blocks in that time is caught, hands free
 * required. Pressing early or late catches nothing and leaves you open for {@link #CATCH_COOLDOWN} ticks. The caught arrow is the
 * arrow itself: it goes into your hands as the item it was (tipping and all), marked as caught. <b>Sneak and the key</b> throws one
 * back, the real arrow, spent from your pack, fast and for a little more than it would have done.</li>
 * <li><b>Stunning blow:</b> with no arrow coming, the key readies one for {@link #READY_TICKS} ticks; the next blow of the fist spends
 * a ki ({@link #KI_BASE} and a Grit share, back after a short rest, {@link Rests}) and, unless a Grit save turns it,
 * stuns what it strikes ({@link HeldFast}); a boss shrugs it off until its {@link Legendary} resistances are spent.</li>
 * <li><b>Slow fall beside walls:</b> falling beside a wall, you drag your hands down it: the fall slows to a crawl and the harm of it
 * is cut.</li>
 * </ul>
 */
public final class Monks {
	/** Ki before Grit adds to it (one in {@link #GRIT_PER_KI} points). */
	static final int KI_BASE = 3, GRIT_PER_KI = 40;
	/** The catching stance: how long it holds, how near an arrow must come, how far off one is sensed, the wait after a miss (ticks). */
	static final int CATCH_WINDOW = 12, CATCH_COOLDOWN = 30;
	static final double CATCH_REACH = 5.0, CATCH_SENSE = 24.0;
	/** The stun: how long it readies for (ticks), how long it holds (ticks). */
	static final int READY_TICKS = 100, STUN_TICKS = 50;
	/** A thrown-back arrow: its speed and the extra it does. */
	static final float THROW_SPEED = 2.8F;
	static final double THROW_DAMAGE = 2.5, BASE_ARROW = 2.0;
	/** The caught arrow's mark, and how long a catch can be thrown back (ticks). */
	static final String CAUGHT = "jaymoskingdoms_caught";
	/** The wall: how far a hand reaches (blocks), the fall speed it slows to, and how fast a long fall is forgiven. */
	static final double WALL_REACH = 0.3, SLOW_FALL = -0.14;
	static final float WALL_FALL_KEPT = 0.25F;

	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final Map<UUID, long[]> CATCHING = new HashMap<>();

	private Monks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Monks::tick);
	}

	static void clear() {
		READY.clear();
		CATCHING.clear();
	}

	/** A new Monk is taught the first of Jung's boxing styles. */
	static void begin(ServerPlayer player) {
		PowersCompat.boxingTeach(player, "arrow_slit");
	}

	/** Whether {@code held} is a Monk's fist: nothing in the hand, or a boxer's fist gear. */
	public static boolean fists(ItemStack held) {
		return held.isEmpty() || PowersCompat.fistGear(held);
	}

	/** How much ki {@code player} has in all. */
	public static int ki(ServerPlayer player) {
		return Classes.scaled(player, PlayerClass.MONK, KI_BASE + Stats.value(player, Stats.Stat.GRIT) / GRIT_PER_KI);
	}

	// ---- the key ---------------------------------------------------------------------------------------------------------------

	static void key(ServerPlayer player, boolean sneak) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (sneak) {
			throwBack(player, level);
			return;
		}
		long now = level.getGameTime();
		long[] stance = CATCHING.get(player.getUUID());
		if (stance != null && stance[1] > now) {
			return;
		}
		if (arrowComing(player, level) != null) {
			CATCHING.put(player.getUUID(), new long[] {now + CATCH_WINDOW, now + CATCH_WINDOW + CATCH_COOLDOWN});
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.8F);
			return;
		}
		if (!fists(player.getMainHandItem())) {
			return;
		}
		READY.put(player.getUUID(), now + READY_TICKS);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(1.0), player.getZ(), 8, 0.3, 0.2, 0.3, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.ready", Rests.left(player, Rests.KI, ki(player))));
	}

	/** An arrow in flight toward {@code player}, or null. */
	private static AbstractArrow arrowComing(ServerPlayer player, ServerLevel level) {
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(CATCH_SENSE), a -> a.getOwner() != player && !a.isRemoved())) {
			Vec3 v = arrow.getDeltaMovement();
			if (v.lengthSqr() < 0.25 || arrow.pickup != AbstractArrow.Pickup.ALLOWED) {
				continue;
			}
			Vec3 to = player.getBoundingBox().getCenter().subtract(arrow.position());
			if (to.lengthSqr() > 1.0E-4 && v.normalize().dot(to.normalize()) > 0.8) {
				return arrow;
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!READY.isEmpty() && server.getTickCount() % 20 == 0) {
			READY.values().removeIf(until -> until <= now);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Classes.is(player, PlayerClass.MONK) || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long[] stance = CATCHING.get(player.getUUID());
			if (stance != null && stance[0] > now && fists(player.getMainHandItem())) {
				catchOne(player, level);
			}
			if (stance != null && stance[1] <= now) {
				CATCHING.remove(player.getUUID());
			}
			cling(player, level);
		}
	}

	private static void catchOne(ServerPlayer player, ServerLevel level) {
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(CATCH_REACH), a -> a.getOwner() != player && !a.isRemoved())) {
			Vec3 v = arrow.getDeltaMovement();
			if (v.lengthSqr() < 0.25 || arrow.pickup != AbstractArrow.Pickup.ALLOWED) {
				continue;
			}
			Vec3 to = player.getBoundingBox().getCenter().subtract(arrow.position());
			if (to.lengthSqr() < 1.0E-4 || v.normalize().dot(to.normalize()) < 0.5) {
				continue;
			}
			ItemStack stack = arrow.getPickupItemStackOrigin().copyWithCount(1);
			if (stack.isEmpty()) {
				continue;
			}
			CompoundTag tag = new CompoundTag();
			tag.putBoolean(CAUGHT, true);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			arrow.discard();
			if (player.getMainHandItem().isEmpty()) {
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			} else {
				player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			CATCHING.remove(player.getUUID());
			level.sendParticles(ParticleTypes.CRIT, arrow.getX(), arrow.getY(), arrow.getZ(), 10, 0.2, 0.2, 0.2, 0.2);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8F, 1.8F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 1.4F);
			return;
		}
	}

	/** Throws back the arrow caught last: the real arrow, taken from the pack. */
	private static void throwBack(ServerPlayer player, ServerLevel level) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.isEmpty() || !isCaught(stack)) {
				continue;
			}
			ItemStack arrowItem = stack.copyWithCount(1);
			arrowItem.remove(DataComponents.CUSTOM_DATA);
			Arrow arrow = new Arrow(level, player, arrowItem, null);
			arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_SPEED, 0.5F);
			arrow.setCritArrow(true);
			arrow.setBaseDamage(BASE_ARROW + THROW_DAMAGE);
			level.addFreshEntity(arrow);
			stack.shrink(1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			return;
		}
	}

	private static boolean isCaught(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(CAUGHT, false);
	}

	// ---- the fists ---------------------------------------------------------------------------------------------------------------

	/** A blow of the fist (or, readied, a stunning one) landing: returns the damage. */
	static float struck(ServerPlayer monk, LivingEntity target, ItemStack held, float amount) {
		if (!fists(held)) {
			return amount;
		}
		Long until = READY.get(monk.getUUID());
		if (until == null || until <= monk.level().getGameTime()) {
			return amount;
		}
		if (!Rests.spend(monk, Rests.KI, 1, ki(monk))) {
			READY.remove(monk.getUUID());
			monk.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.monk.spent"));
			return amount;
		}
		READY.remove(monk.getUUID());
		if (monk.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.3);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
			// the bosses' great resistances answer first ({@link HeldFast#afflict} → {@link Legendary})
			if (HeldFast.afflict(target, STUN_TICKS)) {
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(1.0), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
			}
		}
		return amount;
	}

	/** Falling beside a wall: the hands drag down it. */
	private static void cling(ServerPlayer player, ServerLevel level) {
		if (player.onGround() || player.isInWater() || player.isFallFlying() || player.getAbilities().flying || player.isPassenger()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		if (v.y > -0.25 || player.fallDistance < 2.0F || !wallBeside(player, level)) {
			return;
		}
		player.setDeltaMovement(v.x, Math.max(v.y, SLOW_FALL), v.z);
		player.syncVelocity = true;
		player.fallDistance = Math.min(player.fallDistance, 2.0F) * WALL_FALL_KEPT;
		if (player.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(0.8), player.getZ(), 2, 0.3, 0.1, 0.3, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_STEP, SoundSource.PLAYERS, 0.4F, 1.4F);
		}
	}

	private static boolean wallBeside(ServerPlayer player, ServerLevel level) {
		return !level.noCollision(player, player.getBoundingBox().inflate(WALL_REACH, -0.1, WALL_REACH))
			&& level.noCollision(player, player.getBoundingBox().inflate(0.0, -0.1, 0.0));
	}
}
