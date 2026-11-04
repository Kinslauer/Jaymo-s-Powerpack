package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.3) Andy: "while holding a shield and wielding a weapon, left click slams the shield generating noise and
 * attracting mobs (or repelling sensitive ones...)". With a shield raised in the off hand and a weapon in the main hand,
 * a left click (which does nothing while you block) bangs the weapon on the shield: a loud clang, the weapon arm swings,
 * a vibration for sculk and wardens. Hostile mobs that hear it and aren't already fighting come to see what made it
 * ({@link #DRAW_RANGE}); wild animals, bats and fish bolt away from it ({@link #STARTLE_RANGE}). Neutral mobs (wolves,
 * bees, bears, golems) and anything tamed, ridden or leashed don't care. One bang per click, {@link #COOLDOWN} apart.
 * The client spots the click ({@code client/combat/ShieldBangClient}); the server checks the hands again.
 */
public final class ShieldBang {
	/** Hostiles this far (blocks) come to the noise; skittish mobs this far run from it. */
	public static final double DRAW_RANGE = 24.0, STARTLE_RANGE = 16.0;
	/** Ticks between bangs. */
	public static final int COOLDOWN = 10;
	/** Walking pace of a drawn hostile, the sprint of a startled animal (navigation speed factors). */
	public static final double DRAW_SPEED = 1.0, FLEE_SPEED = 1.8;
	/** How far (blocks) a startled mob runs, and how far up or down it may look for the spot. */
	public static final int FLEE_DISTANCE = 14, FLEE_HEIGHT = 6;

	/** Client → server: the player banged their shield. */
	public record Bang() implements CustomPacketPayload {
		public static final Bang INSTANCE = new Bang();
		public static final Type<Bang> TYPE = new Type<>(JaymosPowers.id("shield_bang"));
		public static final StreamCodec<ByteBuf, Bang> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Bang> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private ShieldBang() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Bang.TYPE, Bang.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Bang.TYPE, (payload, context) -> bang(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	/**
	 * Whether {@code player} can bang right now (both sides): a shield (something that blocks and isn't a weapon) raised in
	 * the off hand, a weapon in the main hand.
	 */
	public static boolean ready(Player player) {
		return !player.isSpectator() && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND
			&& Guard.raised(player) && !Guard.weaponGuard(player.getUseItem()) && player.getMainHandItem().has(DataComponents.WEAPON);
	}

	private static void bang(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		// a couple of ticks' grace for network jitter
		if (!ready(player) || last != null && now - last < COOLDOWN - 2) {
			return;
		}
		LAST.put(player.getUUID(), now);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		float pitch = 0.75F + level.getRandom().nextFloat() * 0.2F;
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 2.0F, pitch);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.4F + pitch * 0.3F);
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0.0, -0.4, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.25);
		level.gameEvent(GameEvent.ENTITY_ACTION, player.position(), GameEvent.Context.of(player));
		Vec3 from = player.position();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DRAW_RANGE), mob -> mob.isAlive() && !mob.isNoAi())) {
			double distance = mob.distanceToSqr(player);
			if (skittish(mob)) {
				if (distance <= STARTLE_RANGE * STARTLE_RANGE && mob instanceof PathfinderMob runner) {
					Vec3 away = DefaultRandomPos.getPosAway(runner, FLEE_DISTANCE, FLEE_HEIGHT, from);
					if (away != null) {
						send(mob, away, FLEE_SPEED);
					}
				}
			} else if (mob instanceof Enemy && !(mob instanceof NeutralMob) && mob.getTarget() == null && distance <= DRAW_RANGE * DRAW_RANGE) {
				send(mob, from, DRAW_SPEED);
			}
		}
	}

	/** Wild, untamed, unridden animals, bats and fish: they run from the noise. */
	private static boolean skittish(Mob mob) {
		MobCategory category = mob.getType().getCategory();
		boolean shy = mob instanceof Animal || mob instanceof AmbientCreature
			|| category == MobCategory.WATER_AMBIENT || category == MobCategory.WATER_CREATURE;
		if (!shy || mob instanceof Enemy || mob instanceof NeutralMob || mob.isLeashed() || mob.isVehicle() || mob.isPassenger()) {
			return false;
		}
		return !(mob instanceof TamableAnimal pet && pet.isTame() || mob instanceof AbstractHorse horse && horse.isTamed());
	}

	/** Walk (or run) {@code mob} to {@code to}: its navigation for goal mobs, its walk target for brain mobs. */
	private static void send(Mob mob, Vec3 to, double speed) {
		mob.getNavigation().moveTo(to.x, to.y, to.z, speed);
		// a brain that doesn't keep a walk target ignores this
		mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, (float) speed, 2));
	}
}
