package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.ThrownWeapon;
import com.jaymospowers.combat.WeaponKind;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Call it back: a Stormbound Wind user binds one weapon to the storm (sneak + the recall key at a storm while holding it;
 * a hammer they throw is bound by the throw), and the recall key calls it to their hand from wherever it lies in the
 * same dimension. It flies through what's in the way and hits what it passes. A weapon in a chunk that isn't loaded
 * can't hear: the call waits, and it comes once its chunk is there (when you next get near).
 * <p>
 * The bond is a token: the weapon's custom data carries it ({@link #KEY}) and so does the player ({@link #TOKEN}); binding
 * another weapon changes the player's token, so the old one is let go.
 */
final class StormRecall {
	static final String KEY = "jaymospowers_storm_bound";
	private static final String TABLE = "wind_storm";
	private static final int SLOT = 3;
	static final int COOLDOWN = 40;
	/** How fast it comes (blocks/tick, rising with distance up to the top), how near the hand it is caught, how far it blinks in from. */
	static final double SPEED_MIN = 0.8, SPEED_MAX = 2.4, CATCH = 1.8, BLINK_FROM = 48.0;
	static final float HIT_DAMAGE = 6.0F;
	static final double HIT_REACH = 1.1, HIT_PUSH = 0.6;
	/** A flight ends after this long; a waiting call gives up after this long; how often a waiting call looks again (ticks). */
	static final int FLIGHT_TICKS = 600, WAIT_TICKS = 6000, LOOK_EVERY = 20;

	/** The token of the weapon this player has bound. */
	public static final AttachmentType<UUID> TOKEN = AttachmentRegistry.create(JaymosPowers.id("wind_storm_token"),
		builder -> builder.persistent(UUIDUtil.CODEC).copyOnDeath());

	private static final class Flight {
		UUID weapon;
		long since;
		final Set<UUID> hit = new HashSet<>();
	}

	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	/** Players whose call found nothing loaded: when they called. */
	private static final Map<UUID, Long> WAITING = new HashMap<>();

	private StormRecall() {
	}

	static boolean bound(ServerPlayer player) {
		return player.getAttached(TOKEN) != null;
	}

	/** The token a stack carries, or null. */
	static @Nullable String tokenOf(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		String token = data == null ? "" : data.copyTag().getStringOr(KEY, "");
		return token.isEmpty() ? null : token;
	}

	/** Whether a stack is marked by the storm (it was bound to someone). */
	static boolean marked(ItemStack stack) {
		return tokenOf(stack) != null;
	}

	/** Binds {@code stack} (a weapon about to be thrown or held) to the player, letting go of what they had bound before. */
	static void bind(ServerPlayer player, ItemStack stack) {
		UUID token = UUID.randomUUID();
		player.setAttached(TOKEN, token);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEY, token.toString()));
	}

	/** Whether a stack is a weapon the storm will take: a hammer or mace, or anything with a weapon's kind. */
	static boolean weapon(ItemStack stack) {
		return !stack.isEmpty() && (StormHammer.hammer(stack) || WeaponKind.of(stack) != null);
	}

	static void press(ServerPlayer player) {
		ServerLevel level = player.level();
		if (player.isShiftKeyDown()) {
			ItemStack held = player.getMainHandItem();
			if (!weapon(held)) {
				StormNet.say(player, "bind_nothing");
				return;
			}
			if (!level.isRaining()) {
				StormNet.say(player, "bind_clear");
				return;
			}
			bind(player, held);
			FLIGHTS.remove(player.getUUID());
			WAITING.remove(player.getUUID());
			MagicFx.blast(level, player.getEyePosition().add(player.getLookAngle().scale(0.8)), 1.2F, StormDraw.BOLT);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7F, 1.7F);
			StormNet.say(player, "bound");
			return;
		}
		if (!bound(player)) {
			StormNet.say(player, "recall_none");
			return;
		}
		if (!PickKit.ready(TABLE, player, SLOT)) {
			return;
		}
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		if (carried(player)) {
			StormNet.say(player, "recall_here");
			return;
		}
		if (StormHammer.riding(player)) {
			return;
		}
		if (!start(player, level)) {
			WAITING.put(player.getUUID(), level.getGameTime());
			StormNet.say(player, "recall_far");
		}
	}

	/** Whether the bound weapon is already on the player. */
	private static boolean carried(ServerPlayer player) {
		String token = String.valueOf(player.getAttached(TOKEN));
		for (ItemStack stack : player.getInventory()) {
			if (token.equals(tokenOf(stack))) {
				return true;
			}
		}
		return token.equals(tokenOf(player.getOffhandItem()));
	}

	private static @Nullable ThrownWeapon find(ServerPlayer player, ServerLevel level) {
		String token = String.valueOf(player.getAttached(TOKEN));
		for (Entity e : level.getAllEntities()) {
			if (e instanceof ThrownWeapon w && w.isAlive() && token.equals(tokenOf(w.getPickupItemStackOrigin()))) {
				return w;
			}
		}
		return null;
	}

	/** Sends the weapon home; false if none is loaded to hear. */
	private static boolean start(ServerPlayer player, ServerLevel level) {
		ThrownWeapon w = find(player, level);
		if (w == null) {
			return false;
		}
		Flight flight = new Flight();
		flight.weapon = w.getUUID();
		flight.since = level.getGameTime();
		FLIGHTS.put(player.getUUID(), flight);
		WAITING.remove(player.getUUID());
		Vec3 from = w.position();
		Vec3 away = from.subtract(player.position());
		if (away.length() > BLINK_FROM) {
			// too far to fly in earshot: it comes out of the storm at the edge of sight
			Vec3 edge = player.position().add(away.normalize().scale(BLINK_FROM)).add(0.0, 4.0, 0.0);
			WindBolts.zap(level, from.add(0.0, 30.0, 0.0), from);
			w.setPos(edge.x, edge.y, edge.z);
			WindBolts.zap(level, edge.add(0.0, 30.0, 0.0), edge);
		}
		w.setNoPhysics(true);
		w.setNoGravity(true);
		w.spent();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.9F);
		return true;
	}

	static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long waiting = WAITING.get(player.getUUID());
		if (waiting != null) {
			if (now - waiting > WAIT_TICKS) {
				WAITING.remove(player.getUUID());
			} else if (now % LOOK_EVERY == 0 && start(player, level)) {
				StormNet.say(player, "recall_came");
			}
		}
		Flight flight = FLIGHTS.get(player.getUUID());
		if (flight == null) {
			return;
		}
		Entity found = level.getEntity(flight.weapon);
		if (!(found instanceof ThrownWeapon w) || !w.isAlive() || now - flight.since > FLIGHT_TICKS) {
			FLIGHTS.remove(player.getUUID());
			if (found instanceof ThrownWeapon lost) {
				lost.setNoPhysics(false);
				lost.setNoGravity(false);
			}
			return;
		}
		Vec3 to = player.getEyePosition().add(0.0, -0.3, 0.0).subtract(w.position());
		double dist = to.length();
		if (dist < CATCH) {
			catchIt(player, level, w);
			FLIGHTS.remove(player.getUUID());
			return;
		}
		double speed = Math.min(SPEED_MAX, SPEED_MIN + dist * 0.06);
		Vec3 velocity = to.normalize().scale(Math.min(speed, dist));
		w.setDeltaMovement(velocity);
		w.needsSync = true;
		if (now % 2 == 0) {
			level.sendParticles(MagicFx.spark(StormDraw.BOLT, 0.7F), w.getX(), w.getY(), w.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		}
		DamageSource source = level.damageSources().thrown(w, player);
		List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, w.getBoundingBox().inflate(HIT_REACH),
			e -> PickKit.foe(player, e) && !flight.hit.contains(e.getUUID())));
		for (LivingEntity foe : foes) {
			flight.hit.add(foe.getUUID());
			foe.hurtServer(level, source, HIT_DAMAGE);
			foe.push(velocity.x * HIT_PUSH, 0.2, velocity.z * HIT_PUSH);
			foe.syncVelocity = true;
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8F, 1.1F);
		}
	}

	private static void catchIt(ServerPlayer player, ServerLevel level, ThrownWeapon w) {
		ItemStack back = w.getPickupItemStackOrigin().copy();
		w.discard();
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, back);
		} else if (!player.getInventory().add(back)) {
			player.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.9F, 1.2F);
		MagicFx.blast(level, player.getEyePosition().add(player.getLookAngle().scale(0.5)), 0.9F, StormDraw.BOLT);
	}

	static void forget(ServerPlayer player) {
		Flight flight = FLIGHTS.remove(player.getUUID());
		WAITING.remove(player.getUUID());
		if (flight != null && player.level().getEntity(flight.weapon) instanceof ThrownWeapon w) {
			w.setNoPhysics(false);
			w.setNoGravity(false);
		}
	}
}
