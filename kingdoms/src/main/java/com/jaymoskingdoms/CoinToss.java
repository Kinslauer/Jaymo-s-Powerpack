package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-15): "you should be able to throw coins, and it will attract most villagers." Use (right click) with
 * coins in the main hand, aimed past any block in reach, tosses one ahead of you; sneak + use scatters a handful
 * ({@link #HANDFUL}). The coins thrown are
 * the ones you held, spent from the stack (no free projectiles), and lie on the ground as ordinary coins anyone can pick
 * up. Villagers who hear one land ({@link #HEAR}) or see it lying ({@link #SEE}) run for it, and the first to reach it
 * pockets it in their own bag (where a pickpocket can find it again). Most come: guards on watch don't, nor anyone asleep,
 * trading, downed, in chains, held, fleeing or under attack, and a few ({@link #PROUD_PERCENT}, always the same few) are
 * too proud to stoop for a coin. The coins are a realm's (silver, hacksilver, dinars, shells, mon), the copper bit and the
 * greenskins' bent copper; emeralds (a gem now) and gold ingots are not thrown by use, so they keep their own uses.
 */
public final class CoinToss {
	// ---- tuning ----
	/** How fast a tossed coin leaves the hand (blocks/tick), and how much it is lifted. */
	static final double TOSS_SPEED = 0.45, TOSS_LIFT = 0.18;
	/** Coins scattered by a sneaking toss (at most, and never more than held). */
	static final int HANDFUL = 5;
	/** The wait between tosses (ticks). */
	static final int COOLDOWN = 6;
	/** Before anyone at all (the thrower too) can pick a tossed coin up by walking over it (ticks). */
	static final int PICKUP_DELAY = 30;
	/** Villagers hear a coin land this near (blocks) and come without seeing it; out to {@link #SEE} they must see it. */
	static final double HEAR = 10.0, SEE = 16.0;
	/** How near a villager must be to pick the coin up (blocks). */
	static final double REACH = 1.4;
	/** How fast they run for it (a walk target's speed: villagers stroll at 0.5). */
	static final float RUN_SPEED = 0.7F;
	/** Share of villagers too proud to run after a coin (percent, by who they are). */
	static final int PROUD_PERCENT = 20;
	/** How often the villagers near a coin are looked over (ticks). */
	static final int LOOK_EVERY = 10;
	/** After this long on the ground (ticks) a coin is just a lost coin: nobody comes for it any more. */
	static final int NOTICED_FOR = 1200;
	/** Odds (1 in this) that the one who gets it says something. */
	static final int SAY_ODDS = 2;
	static final int GRAB_LINES = 8, CHILD_LINES = 4;
	static final int SAY_COLOUR = 0xF2D27A;
	static final double SAY_RANGE = 16.0;

	/** A tossed coin: whether it has landed yet, and the tick it did. */
	private static final class Toss {
		boolean landed;
		long landedAt;
	}

	/** Tossed coins on their way or lying, until someone takes them or they are forgotten. */
	private static final Map<ItemEntity, Toss> TOSSED = new HashMap<>();
	/** Who is running for which coin, to call them off when it's gone. */
	private static final Map<Villager, ItemEntity> CHASING = new HashMap<>();

	private CoinToss() {
	}

	static void register() {
		// after the default phase, so a hogtied, held or otherwise stopped player's FAIL comes first
		Identifier late = JaymosKingdoms.id("coin_toss_late");
		UseItemCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		UseItemCallback.EVENT.register(late, (player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !isCoin(stack) || player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.PASS;
			}
			// a block in reach is a click on that block (a cell door's fine, a market stall, a dice table), never a toss:
			// some of those are only answered on the server, so the client would toss a coin as well
			if (player.pick(player.blockInteractionRange(), 1.0F, false).getType() == HitResult.Type.BLOCK) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer thrower) {
				toss(server, thrower, stack, thrower.isShiftKeyDown() ? Math.min(HANDFUL, stack.getCount()) : 1);
			}
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(CoinToss::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TOSSED.clear();
			CHASING.clear();
		});
	}

	/** Whether {@code stack} is coins a toss throws: any money coin but the emerald, and the pirates' gold ingots. */
	static boolean isCoin(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Currency c = Currency.byItem(stack.getItem());
		return c != null && c != Currency.EMERALD && c != Currency.GOLD;
	}

	/** {@code count} coins leave {@code player}'s hand, spent from {@code stack}, one item on the ground each. */
	private static void toss(ServerLevel level, ServerPlayer player, ItemStack stack, int count) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		for (int i = 0; i < count; i++) {
			ItemStack coin = stack.consumeAndReturn(1, player);
			if (coin.isEmpty()) {
				break;
			}
			// a handful fans out; one coin goes where you look
			double spread = count > 1 ? 0.12 : 0.02;
			Vec3 v = look.scale(TOSS_SPEED).add(level.getRandom().triangle(0.0, spread), TOSS_LIFT + level.getRandom().triangle(0.0, spread * 0.5),
				level.getRandom().triangle(0.0, spread));
			ItemEntity item = new ItemEntity(level, eye.x, eye.y - 0.3, eye.z, coin, v.x, v.y, v.z);
			item.setThrower(player);
			item.setPickUpDelay(PICKUP_DELAY);
			level.addFreshEntity(item);
			TOSSED.put(item, new Toss());
		}
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.5F,
			count > 1 ? 1.5F : 1.8F);
	}

	private static void tick(MinecraftServer server) {
		if (TOSSED.isEmpty()) {
			if (!CHASING.isEmpty()) {
				CHASING.keySet().forEach(CoinToss::callOff);
				CHASING.clear();
			}
			return;
		}
		boolean look = server.getTickCount() % LOOK_EVERY == 0;
		for (Iterator<Map.Entry<ItemEntity, Toss>> it = TOSSED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<ItemEntity, Toss> e = it.next();
			ItemEntity coin = e.getKey();
			Toss toss = e.getValue();
			if (coin.isRemoved() || !(coin.level() instanceof ServerLevel level) || coin.getItem().isEmpty()) {
				it.remove();
				continue;
			}
			if (!toss.landed && (coin.onGround() || coin.isInWater())) {
				toss.landed = true;
				toss.landedAt = level.getGameTime();
				// the clink that turns heads
				level.playSound(null, coin.getX(), coin.getY(), coin.getZ(), SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL, 0.7F, 1.9F);
			}
			if (toss.landed && level.getGameTime() - toss.landedAt > NOTICED_FOR) {
				it.remove();
			}
		}
		if (look) {
			draw();
		}
	}

	/** Every villager who notices a landed coin runs for the nearest one, and whoever reaches one takes it. */
	private static void draw() {
		Map<Villager, ItemEntity> wanted = new HashMap<>();
		for (Map.Entry<ItemEntity, Toss> e : List.copyOf(TOSSED.entrySet())) {
			ItemEntity coin = e.getKey();
			if (!e.getValue().landed || coin.isRemoved() || !(coin.level() instanceof ServerLevel level)) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(coin.blockPosition()).inflate(SEE), CoinToss::comes)) {
				double d = v.distanceToSqr(coin);
				if (d > SEE * SEE || d > HEAR * HEAR && !v.hasLineOfSight(coin)) {
					continue;
				}
				ItemEntity was = wanted.get(v);
				if (was == null || d < v.distanceToSqr(was)) {
					wanted.put(v, coin);
				}
			}
		}
		for (Map.Entry<Villager, ItemEntity> e : wanted.entrySet()) {
			Villager v = e.getKey();
			ItemEntity coin = e.getValue();
			if (coin.isRemoved()) {
				// someone nearer got it this same look; they'll find the next one on the next
				continue;
			}
			if (v.distanceToSqr(coin) <= REACH * REACH) {
				grab((ServerLevel) coin.level(), v, coin);
			} else {
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(coin.blockPosition(), RUN_SPEED, 0));
				CHASING.put(v, coin);
			}
		}
		// those whose coin is gone (taken by someone else, picked up by a player) stop running for it
		for (Iterator<Map.Entry<Villager, ItemEntity>> it = CHASING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Villager, ItemEntity> e = it.next();
			if (!wanted.containsKey(e.getKey()) || e.getValue().isRemoved()) {
				callOff(e.getKey());
				it.remove();
			}
		}
	}

	/** Whether {@code v} would run for a coin just now. */
	private static boolean comes(Villager v) {
		if (!v.isAlive() || v.isNoAi() || v.isSleeping() || v.isTrading() || v.isPassenger() || proud(v) || Downed.isGuard(v)
			|| Downed.isDowned(v) || Captives.bound(v) || HeldFast.locked(v) || CreeperFlight.fleeing(v)) {
			return false;
		}
		var brain = v.getBrain();
		return !brain.isActive(Activity.PANIC) && !brain.isActive(Activity.RAID) && !brain.isActive(Activity.PRE_RAID)
			&& !brain.hasMemoryValue(MemoryModuleType.HURT_BY) && !brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
	}

	/** The few who never stoop for a thrown coin: always the same ones. */
	private static boolean proud(Villager v) {
		return Math.floorMod(v.getUUID().getLeastSignificantBits() ^ 0x5C01AL, 100L) < PROUD_PERCENT;
	}

	/** {@code v} picks {@code coin} up: into their bag (or just their pocket, when that's full). */
	private static void grab(ServerLevel level, Villager v, ItemEntity coin) {
		ItemStack stack = coin.getItem().copy();
		v.take(coin, stack.getCount());
		v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		v.getInventory().addItem(stack);
		coin.discard();
		TOSSED.remove(coin);
		callOff(v);
		CHASING.remove(v);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.4F);
		if (level.getRandom().nextInt(SAY_ODDS) == 0) {
			String key = v.isBaby() ? "coin_toss.jaymoskingdoms.child." + level.getRandom().nextInt(CHILD_LINES)
				: "coin_toss.jaymoskingdoms.grab." + level.getRandom().nextInt(GRAB_LINES);
			Speech.say(level, v, Component.translatable(key), SAY_COLOUR, SAY_RANGE);
		}
	}

	/** {@code v} stops running for a coin. */
	private static void callOff(@Nullable Villager v) {
		if (v != null && v.isAlive()) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}
}
