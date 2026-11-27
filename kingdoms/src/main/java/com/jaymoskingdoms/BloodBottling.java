package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fill your own bottles (Night's Blood, patch 3): "Holding someone by the throat ({@link BloodGrab}) with a glass bottle in
 * the off hand fills it (250 mB from them, a Kitchen blood bottle remembering whose blood it is). A knife's bleeding wound
 * over a bottle does the same, slower. Drinking a bottle gives its mB."
 * <ul>
 * <li><b>The throat.</b> While a vampire holds someone up, each gulp goes into the bottle in the off hand instead of the
 * vampire: every {@link VampireBlood#BOTTLE_MB} mB fills one glass bottle (the prey's heritage taste and a king's or lord's
 * greatness go in with it, {@link BloodTaste}). With no glass bottle left the gulps feed the vampire as before.</li>
 * <li><b>The wound.</b> Anyone who uses a glass bottle (main hand) on a person with an open cut (Powers' wounds) holds it under
 * the wound: it fills at {@link #WOUND_MB_PER_TICK} mB a tick, far slower than a held throat gives, while the person bleeds and stays
 * within {@link #REACH} blocks and the bottle stays in the hand.</li>
 * <li><b>Drinking.</b> A filled bottle drunk gives {@link VampireBlood#BOTTLE_MB} mB, doubled for a great one's, halved for a
 * Verdant's, and leaves its taste ({@link Hemomancy#drankBottle}).</li>
 * </ul>
 * Needs Jaymo's Kitchen for the bottle.
 */
public final class BloodBottling {
	/** A held-over wound's pace (mB a tick), and how near the bottle's holder must stay (blocks). */
	static final float WOUND_MB_PER_TICK = 1.5F;
	static final double REACH = 3.0;
	/** The longest a wound is held over a bottle (ticks). */
	static final int WOUND_LIMIT = 20 * 60;

	/** Blood gathered toward the next bottle in a throat hold: from whom, and how much (mB). */
	private record Gathered(UUID from, int mb) {
	}

	/** A bottle held under a wound: who bleeds, how much has run in (mB), the tick it began. */
	private static final class Catch {
		final LivingEntity from;
		float mb;
		final long since;

		Catch(LivingEntity from, long since) {
			this.from = from;
			this.since = since;
		}
	}

	private static final Map<UUID, Gathered> GATHERED = new HashMap<>();
	private static final Map<UUID, Catch> CATCHES = new HashMap<>();

	private BloodBottling() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer p) || !(entity instanceof LivingEntity target)
				|| !p.getMainHandItem().is(Items.GLASS_BOTTLE) || p.isSpectator() || !KitchenCompat.hasBottles() || !bleeds(p, target)) {
				return InteractionResult.PASS;
			}
			if (CATCHES.containsKey(p.getUUID())) {
				return InteractionResult.PASS;
			}
			CATCHES.put(p.getUUID(), new Catch(target, level.getGameTime()));
			level.playSound(null, target.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.6F, 0.6F);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(BloodBottling::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GATHERED.clear();
			CATCHES.clear();
		});
	}

	/** Whether {@code target} is a person with an open cut that {@code player} could hold a bottle under. */
	static boolean bleeds(ServerPlayer player, LivingEntity target) {
		return target != player && target.isAlive() && (Vampires.person(target) || Vampires.netherPrey(target) || Vampires.mortal(target))
			&& PowersCompat.injuries(target).bleeding() != 0 && target.distanceTo(player) <= REACH;
	}

	// ---- the throat ------------------------------------------------------------------------------------------------------

	/**
	 * A gulp of {@code mb} from {@code target}, held by the throat by {@code player}: if there is a glass bottle in their off
	 * hand it goes into it (and not into them). Whether it did.
	 */
	static boolean catches(ServerPlayer player, LivingEntity target, int mb) {
		if (!BloodGrab.holding(player) || !player.getOffhandItem().is(Items.GLASS_BOTTLE) || !KitchenCompat.hasBottles()) {
			GATHERED.remove(player.getUUID());
			return false;
		}
		Gathered g = GATHERED.get(player.getUUID());
		int have = g != null && g.from().equals(target.getUUID()) ? g.mb() + mb : mb;
		while (have >= VampireBlood.BOTTLE_MB && player.getOffhandItem().is(Items.GLASS_BOTTLE)) {
			if (!fill(player, target, InteractionHand.OFF_HAND)) {
				break;
			}
			have -= VampireBlood.BOTTLE_MB;
		}
		GATHERED.put(player.getUUID(), new Gathered(target.getUUID(), have));
		return true;
	}

	/** One glass bottle in {@code hand} becomes a bottle of {@code from}'s blood. */
	private static boolean fill(ServerPlayer player, LivingEntity from, InteractionHand hand) {
		Heritage.Kind kind = BloodTaste.blood(from);
		ItemStack filled = KitchenCompat.filledBottle(from.getName().getString(), from.getUUID(), BloodTaste.key(kind), BloodTaste.great(from));
		if (filled.isEmpty()) {
			return false;
		}
		player.getItemInHand(hand).shrink(1);
		player.getInventory().placeItemBackInInventory(filled, net.minecraft.util.Prediction.SERVER_ONLY);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 0.7F);
		return true;
	}

	// ---- the wound -------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (CATCHES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Catch>> it = CATCHES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Catch> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Catch c = e.getValue();
			if (player == null || !player.isAlive() || !player.getMainHandItem().is(Items.GLASS_BOTTLE) || !bleeds(player, c.from)
				|| player.level().getGameTime() - c.since > WOUND_LIMIT) {
				it.remove();
				continue;
			}
			c.mb += WOUND_MB_PER_TICK;
			ServerLevel level = player.level();
			if (level.getGameTime() % 10 == 0) {
				level.sendParticles(PowersCompat.blood(0x8A0303, 0.6F), c.from.getX(), c.from.getY(0.6), c.from.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
			}
			if (c.mb >= VampireBlood.BOTTLE_MB) {
				c.mb -= VampireBlood.BOTTLE_MB;
				if (!fill(player, c.from, InteractionHand.MAIN_HAND)) {
					it.remove();
				}
			}
		}
	}

	/** Forgets what {@code player} has gathered (their hold has ended). */
	static void endHold(ServerPlayer player) {
		GATHERED.remove(player.getUUID());
	}
}
