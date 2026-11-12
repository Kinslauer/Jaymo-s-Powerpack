package com.jaymospowers.gear.arrow;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/**
 * Moves for a trained archer. There is no archery skill in the pack, so training is the number of arrows you have loosed
 * (the item-used count of every bow and crossbow, vanilla's own statistic).
 * <ul>
 * <li><b>Slow fall</b> ({@link #SLOWFALL_NEED} shots): drawing a bow in the air, you drift down.</li>
 * <li><b>Double shot</b> ({@link #DOUBLE_NEED}): sneak and loose a full draw, and a second arrow follows
 * {@link #DOUBLE_DELAY} ticks behind it (it costs a second arrow of the same kind).</li>
 * <li><b>Arrow rain</b> ({@link #RAIN_NEED}): loose a full draw while airborne and the arrow does not fly; a volley falls on the
 * spot your crosshair marked instead.</li>
 * </ul>
 */
public final class ArcheryMoves {
	// ---- tuning ----
	public static final int SLOWFALL_NEED = 20, DOUBLE_NEED = 60, RAIN_NEED = 150;
	/** An arrow loosed at least this fast (blocks/tick) came off a full draw. */
	public static final double FULL_SPEED = 2.4;
	public static final int DOUBLE_DELAY = 4, DOUBLE_COOLDOWN = 40, RAIN_COOLDOWN = 300;
	/** The rain: arrows, radius (blocks) they fall in, height they start at, ticks before the first and between each, fall speed. */
	public static final int RAIN_ARROWS = 9, RAIN_WAIT = 12, RAIN_GAP = 3;
	public static final double RAIN_RADIUS = 3.0, RAIN_HEIGHT = 14.0, RAIN_SPEED = 2.6, RAIN_REACH = 64.0;
	/** Slow falling kept on while drawing in the air (ticks); falls slower than this don't need it. */
	public static final int SLOWFALL_TICKS = 5;

	private static final String MOVE_TAG = "jaymospowers.bow_move";

	private record Task(long at, Runnable run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final Map<UUID, Long> DOUBLE_READY = new HashMap<>(), RAIN_READY = new HashMap<>();
	private static List<Item> launchers;

	private ArcheryMoves() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && !arrow.entityTags().contains(MOVE_TAG)) {
				loosed(arrow, level);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (int i = TASKS.size() - 1; i >= 0; i--) {
				Task task = TASKS.get(i);
				if (server.getTickCount() >= task.at()) {
					TASKS.remove(i);
					task.run().run();
				}
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isUsingItem() && !player.onGround() && !player.getAbilities().flying && player.getDeltaMovement().y < -0.1
					&& (player.getUseItem().getItem() instanceof BowItem || player.getUseItem().getItem() instanceof CrossbowItem)
					&& shots(player) >= SLOWFALL_NEED) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOWFALL_TICKS, 0, true, false, false));
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DOUBLE_READY.remove(handler.player.getUUID());
			RAIN_READY.remove(handler.player.getUUID());
		});
	}

	/** Arrows this archer has loosed, over every bow and crossbow in the game. */
	public static int shots(ServerPlayer player) {
		if (launchers == null) {
			List<Item> found = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item instanceof ProjectileWeaponItem) {
					found.add(item);
				}
			}
			launchers = found;
		}
		int total = 0;
		for (Item item : launchers) {
			total += player.getStats().getValue(Stats.ITEM_USED.get(item));
		}
		return total;
	}

	private static void loosed(AbstractArrow arrow, ServerLevel level) {
		if (!(arrow.getOwner() instanceof ServerPlayer player) || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)
			|| arrow.getDeltaMovement().length() < FULL_SPEED) {
			return;
		}
		long now = level.getGameTime();
		int shots = shots(player);
		ItemStack bow = player.getUseItem().copy();
		ItemStack origin = arrow.getPickupItemStackOrigin().copyWithCount(1);
		if (!(origin.getItem() instanceof ArrowItem)) {
			return;
		}
		if (!player.onGround() && shots >= RAIN_NEED && now >= RAIN_READY.getOrDefault(player.getUUID(), 0L)) {
			RAIN_READY.put(player.getUUID(), now + RAIN_COOLDOWN);
			arrow.addTag(MOVE_TAG);
			rain(level, player, bow, origin, arrow);
		} else if (player.isShiftKeyDown() && shots >= DOUBLE_NEED && now >= DOUBLE_READY.getOrDefault(player.getUUID(), 0L)) {
			if (!player.hasInfiniteMaterials() && !spend(player, origin)) {
				return;
			}
			DOUBLE_READY.put(player.getUUID(), now + DOUBLE_COOLDOWN);
			arrow.addTag(MOVE_TAG);
			double speed = arrow.getDeltaMovement().length();
			later(level, DOUBLE_DELAY, () -> second(level, player, bow, origin, speed));
		}
	}

	private static void later(ServerLevel level, int ticks, Runnable run) {
		TASKS.add(new Task(level.getServer().getTickCount() + ticks, run));
	}

	/** Takes one arrow like {@code origin} from the archer's pack; false if there is none. */
	private static boolean spend(ServerPlayer player, ItemStack origin) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack held = player.getInventory().getItem(slot);
			if (ItemStack.isSameItemSameComponents(held, origin)) {
				held.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static void second(ServerLevel level, ServerPlayer player, ItemStack bow, ItemStack origin, double speed) {
		if (!player.isAlive() || player.level() != level) {
			return;
		}
		AbstractArrow arrow = ((ArrowItem) origin.getItem()).createArrow(level, origin.copy(), player, bow);
		Vec3 look = player.getLookAngle();
		arrow.addTag(MOVE_TAG);
		arrow.shoot(look.x, look.y, look.z, (float) speed, 0.6F);
		arrow.setCritArrow(true);
		arrow.pickup = player.hasInfiniteMaterials() ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
		level.addFreshEntity(arrow);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	private static void rain(ServerLevel level, ServerPlayer player, ItemStack bow, ItemStack origin, AbstractArrow shot) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RAIN_REACH));
		Vec3 spot = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
		// the arrow itself never flies: the volley is what the draw bought
		later(level, 1, shot::discard);
		MagicFx.sigil(level, spot.add(0.0, 0.05, 0.0), (float) RAIN_RADIUS, 0xC8B070, RAIN_WAIT + RAIN_GAP * RAIN_ARROWS + 20);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
		for (int i = 0; i < RAIN_ARROWS; i++) {
			later(level, RAIN_WAIT + i * RAIN_GAP, () -> {
				if (!player.isAlive()) {
					return;
				}
				double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
				double reach = Math.sqrt(level.getRandom().nextDouble()) * RAIN_RADIUS;
				AbstractArrow arrow = ((ArrowItem) origin.getItem()).createArrow(level, origin.copy(), player, bow);
				arrow.addTag(MOVE_TAG);
				arrow.setPos(spot.x + Math.cos(angle) * reach, spot.y + RAIN_HEIGHT, spot.z + Math.sin(angle) * reach);
				arrow.setDeltaMovement(0.0, -RAIN_SPEED, 0.0);
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
				level.addFreshEntity(arrow);
			});
		}
	}
}
