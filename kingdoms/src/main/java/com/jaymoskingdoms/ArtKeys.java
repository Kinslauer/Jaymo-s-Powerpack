package com.jaymoskingdoms;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The art keys (Andy's Awakening picks, the rows folded into what the pack already has): four keys, one for each set of arts
 * that had no key of its own left, read the way the Rot key is ({@link AshRot}): the server gets the press and the release and
 * reads the gesture from how long it was held and what the player was doing as it went down.
 * <ul>
 * <li>the <b>Soul key</b>: the Reaper's keeper ({@link SoulArts});</li>
 * <li>the <b>Claw key</b>: a vampire ({@link ClawArts});</li>
 * <li>the <b>Wild key</b>: a Druid, the green arts out of a shape and the sizes in one, or at any size but their own
 * ({@link GreenArts}, {@link SizeArts});</li>
 * <li>the <b>Sun key</b>: a Dawnborn, or a Cleric or Paladin who has learned the Sun's arts ({@link SunArts}).</li>
 * </ul>
 * A gesture is a tap, a tap sneaking, a tap sprinting, or a hold (let go after {@link #HOLD_TICKS}), plain or sneaking.
 */
public final class ArtKeys {
	/** Health at which a creature is too great for the arts that take hold of one ({@link #great}). */
	static final float GREAT_HEALTH = 100.0F;
	/** An art's aim: within this cosine of the look, or anything this near (blocks). */
	static final double SIGHT_CONE = 0.94, SIGHT_NEAR = 1.5;

	// ---- tuning ----
	/** Held this long (ticks) before it is let go, a press is a hold. */
	static final int HOLD_TICKS = 10;

	public static final int SOUL = 0, CLAW = 1, WILD = 2, SUN = 3, ARTS = 4;

	enum Gesture {
		TAP, SNEAK, SPRINT, HOLD, SNEAK_HOLD
	}

	/** An art key went down or up. */
	public record Key(int art, boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosKingdoms.id("art_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Key::art, ByteBufCodecs.BOOL, Key::down, Key::new);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	private record Press(long at, boolean sneak, boolean sprint) {
	}

	private static final Map<UUID, Press[]> DOWN = new HashMap<>();
	private static final Map<UUID, Map<String, Long>> READY = new HashMap<>();

	private ArtKeys() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, context) -> key(context.player(), payload.art(), payload.down()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DOWN.remove(handler.player.getUUID());
			READY.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DOWN.clear();
			READY.clear();
		});
		SoulArts.register();
		ClawArts.register();
		GreenArts.register();
		SizeArts.register();
		SunArts.register();
	}

	private static void key(ServerPlayer player, int art, boolean down) {
		if (art < 0 || art >= ARTS || !player.isAlive() || player.isSpectator()) {
			return;
		}
		Press[] presses = DOWN.computeIfAbsent(player.getUUID(), id -> new Press[ARTS]);
		long now = player.level().getGameTime();
		if (down) {
			presses[art] = new Press(now, player.isShiftKeyDown(), player.isSprinting());
			return;
		}
		Press press = presses[art];
		presses[art] = null;
		if (press == null || Downed.isDowned(player)) {
			return;
		}
		boolean held = now - press.at >= HOLD_TICKS;
		Gesture gesture = held ? press.sneak ? Gesture.SNEAK_HOLD : Gesture.HOLD : press.sneak ? Gesture.SNEAK : press.sprint ? Gesture.SPRINT : Gesture.TAP;
		switch (art) {
			case SOUL -> SoulArts.key(player, gesture);
			case CLAW -> ClawArts.key(player, gesture);
			case WILD -> {
				if (Druids.shaped(player) || SizeArts.size(player) != SizeArts.NATURAL) {
					SizeArts.key(player, gesture);
				} else {
					GreenArts.key(player, gesture);
				}
			}
			default -> SunArts.key(player, gesture);
		}
	}

	/** How long (ticks) {@code player} has had {@code art}'s key down, or -1 when it is up. */
	static long heldFor(ServerPlayer player, int art) {
		Press[] presses = DOWN.get(player.getUUID());
		return presses == null || presses[art] == null ? -1L : player.level().getGameTime() - presses[art].at;
	}

	// ---- what the arts share -------------------------------------------------------------------------------------------------

	/** True (and a word above the hotbar) while {@code move} cools down for {@code player}; else its cooldown of {@code ticks} starts. */
	static boolean cooling(ServerPlayer player, String move, int ticks) {
		long now = player.level().getGameTime();
		Map<String, Long> ready = READY.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		long wait = ready.getOrDefault(move, 0L) - now;
		if (wait > 0L) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.art.wait", (wait + 19L) / 20L).withStyle(ChatFormatting.GRAY));
			return true;
		}
		ready.put(move, now + ticks);
		return false;
	}

	/** A word above the hotbar, in grey: why an art did not go. */
	static void refuse(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.art." + key).withStyle(ChatFormatting.GRAY));
	}

	/** The living thing nearest along {@code player}'s look within {@code reach} that passes {@code test} and is in plain sight. */
	static @Nullable LivingEntity sight(ServerPlayer player, double reach, Predicate<LivingEntity> test) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
			e -> e != player && e.isAlive() && !e.isSpectator() && test.test(e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d <= reach && d < bestD && (d < SIGHT_NEAR || to.normalize().dot(look) > SIGHT_CONE) && player.hasLineOfSight(e)) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** The block {@code player} looks at within {@code reach}, or null. */
	static @Nullable BlockHitResult block(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? null : hit;
	}

	/** One of {@code player}'s own: a party ally, their pet, their follower. */
	static boolean mine(ServerPlayer player, LivingEntity e) {
		return e instanceof ServerPlayer other && Parties.allied(player, other)
			|| e instanceof OwnableEntity pet && pet.getOwnerReference() != null && player.getUUID().equals(pet.getOwnerReference().getUUID())
			|| Followers.takesOrders(e, player);
	}

	/**
	 * Too great for an art that bottles, shrinks, grabs or kills outright: a boss, whatever shows a boss bar (a ruler, a lord, a
	 * warlord, a quest's champion), or anything of {@link #GREAT_HEALTH} or more.
	 */
	static boolean great(LivingEntity e) {
		return e.getMaxHealth() >= GREAT_HEALTH || Legendary.boss(e);
	}

	/** Someone an art that helps reaches: {@code player}'s own, another player, the townsfolk ({@link FaithExtras#friend}). */
	static boolean friend(ServerPlayer player, LivingEntity e) {
		return mine(player, e) || FaithExtras.friend(player, e);
	}

	/**
	 * Someone an art that harms everything near takes for a foe: a monster, a creature that is after {@code player}, or another
	 * player where players may fight; never {@code player}'s own.
	 */
	static boolean foe(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e.isSpectator() || mine(player, e)) {
			return false;
		}
		if (e instanceof Player other) {
			return player.level() instanceof ServerLevel level && level.isPvpAllowed() && !other.isCreative();
		}
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player;
	}
}
