package com.jaymospowers.core;

import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.KitchenCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Takedown and throw, by hand: sneak and <i>hold</i> right-click, both hands empty, on a standing mob that Carry doesn't
 * lift (Carry's own grab is the same hold on a person, a small animal or someone downed, and answers first). The bar
 * fills as you wrestle it ({@link #TAKEDOWN_TICKS}); full, the mob is brought to the ground and lies stunned
 * ({@link #STUN_TICKS}; in Kitchen's limp ragdoll when it's there). Looking up as it fills ({@link #THROW_PITCH}) heaves it
 * instead, along your look ({@link #THROW_SPEED}), if you have the brawn for it.
 * <p>
 * Brawn is Kitchen's hauling count ({@code KitchenCompat#brawn}: Might, Packhorse, Strength, size, a Z Fighter's): a man-
 * sized mob wants {@link #BASE_BRAWN}, a bulkier one one more for each time its bulk is a man's ({@link #need}), a throw
 * {@link #THROW_EXTRA} more. Someone a power grants it ({@link #granted}: Kingdoms' Brute, from the start) counts as having
 * {@link #GRANTED_BRAWN}. Nothing here takes a boss, a villager or a pet.
 */
public final class Wrestle {
	/** Ticks of the hold that brings a mob down, the gap in the holds' repeats that still counts as held, and how close you must stay (blocks). */
	public static final int TAKEDOWN_TICKS = 24, GRACE_TICKS = 8;
	public static final double REACH = 2.6;
	/** How long a mob brought down lies stunned (ticks). */
	public static final int STUN_TICKS = 70;
	/** Brawn a man-sized mob wants; each further man's bulk wants one more; a throw this many more again. */
	public static final int BASE_BRAWN = 1, THROW_EXTRA = 2;
	/** Brawn a granted wrestler counts as. */
	public static final int GRANTED_BRAWN = 8;
	/** Looking up past this (degrees, negative is up) as the hold fills makes it a throw. */
	public static final float THROW_PITCH = -25.0F;
	/** A throw's speed (blocks a tick) along the look, less for a heavier mob, and its lift. */
	public static final double THROW_SPEED = 0.8, THROW_LIFT = 0.2;
	/** Ticks between the "too strong" notes. */
	static final int NOTE_TICKS = 40;

	/** Who counts as strong enough from the start (Jaymo's Kingdoms sets this: a Brute). */
	public static Predicate<Player> granted = player -> false;

	private static final class Grab {
		final Mob target;
		final long start;
		long last;

		Grab(Mob target, long start) {
			this.target = target;
			this.start = start;
			this.last = start;
		}
	}

	private static final Map<UUID, Grab> GRABS = new HashMap<>();
	private static final Map<UUID, Long> NOTED = new HashMap<>();
	/** Each mob brought down: the game time its stun ends. */
	private static final Map<Mob, Long> STUNNED = new WeakHashMap<>();

	private Wrestle() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer wrestler)
				|| !(target instanceof Mob mob) || !may(wrestler, mob)) {
				return InteractionResult.PASS;
			}
			long now = level.getGameTime();
			int brawn = brawn(wrestler);
			if (need(mob) > brawn) {
				Long noted = NOTED.get(wrestler.getUUID());
				if (noted == null || now < noted || now - noted >= NOTE_TICKS) {
					NOTED.put(wrestler.getUUID(), now);
					wrestler.sendOverlayMessage(Component.translatable("message.jaymospowers.takedown.too_strong", mob.getDisplayName())
						.withStyle(ChatFormatting.GRAY));
				}
				return InteractionResult.PASS;
			}
			Grab grab = GRABS.get(wrestler.getUUID());
			if (grab != null && grab.target == mob) {
				grab.last = now;
			} else {
				GRABS.put(wrestler.getUUID(), new Grab(mob, now));
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Wrestle::tick);
	}

	/** Whether {@code mob} is stunned from a takedown just now (its AI stands still: {@code WrestleMobMixin}). */
	public static boolean stunned(Mob mob) {
		Long until = STUNNED.get(mob);
		if (until == null) {
			return false;
		}
		if (mob.level().getGameTime() >= until || !mob.isAlive()) {
			STUNNED.remove(mob);
			return false;
		}
		return true;
	}

	/** How much brawn {@code player} has to wrestle with. */
	public static int brawn(Player player) {
		return granted.test(player) ? GRANTED_BRAWN : KitchenCompat.brawn(player);
	}

	/** The brawn a takedown of {@code mob} wants: {@link #BASE_BRAWN}, and one more for each time its bulk is a man's. */
	public static int need(Mob mob) {
		double ratio = (double) mob.getBbWidth() * mob.getBbWidth() * mob.getBbHeight() / BlastGore.MAN_BULK;
		return BASE_BRAWN + Math.max(0, (int) Math.ceil(ratio) - 1);
	}

	private static boolean may(ServerPlayer player, Mob mob) {
		return player.isShiftKeyDown() && !player.isSpectator() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()
			&& !Carry.isCarried(player) && !Carry.isCarrying(player) && !Downed.isDowned(player)
			&& mob.isAlive() && !Downed.isDowned(mob) && !Carry.isCarried(mob) && !mob.isPassenger() && !mob.isVehicle() && !mob.isSleeping()
			&& !stunned(mob) && !mob.isNoAi() && !mob.getType().builtInRegistryHolder().is(BodyPlan.BOSSES) && !Carry.isVillager(mob)
			&& !(mob instanceof TamableAnimal pet && pet.isTame()) && player.distanceTo(mob) <= REACH;
	}

	private static void tick(MinecraftServer server) {
		if (GRABS.isEmpty()) {
			return;
		}
		for (var it = GRABS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Grab grab = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || !grab.target.isAlive() || grab.target.level() != player.level()) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			if (now - grab.last > GRACE_TICKS || !may(player, grab.target)) {
				it.remove();
				continue;
			}
			long held = now - grab.start;
			if (held >= TAKEDOWN_TICKS) {
				it.remove();
				finish(player, grab.target);
			} else if (held >= 2) {
				com.jaymospowers.core.HudBar.hold(player, "takedown", held / (float) TAKEDOWN_TICKS, com.jaymospowers.core.HudBar.GREY,
					Component.translatable("bar.jaymospowers.takedown", grab.target.getDisplayName()), true);
			}
		}
	}

	/** The hold is full: {@code mob} goes down, or, if {@code player} looks up and has the brawn, is thrown. */
	private static void finish(ServerPlayer player, Mob mob) {
		ServerLevel level = player.level();
		Vec3 from = player.position();
		boolean throwIt = player.getXRot() <= THROW_PITCH && brawn(player) >= need(mob) + THROW_EXTRA;
		if (throwIt) {
			Vec3 look = player.getLookAngle();
			double speed = THROW_SPEED * Math.min(1.0, (double) (brawn(player) - need(mob)) / (THROW_EXTRA + 2.0));
			mob.setDeltaMovement(look.x * speed, Math.max(look.y * speed, 0.0) + THROW_LIFT, look.z * speed);
			mob.needsSync = true;
			mob.syncVelocity = true;
		}
		mob.getNavigation().stop();
		mob.setTarget(null);
		STUNNED.put(mob, level.getGameTime() + STUN_TICKS);
		KitchenCompat.floorMob(mob, from);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, throwIt ? 0.8F : 0.6F);
	}
}
