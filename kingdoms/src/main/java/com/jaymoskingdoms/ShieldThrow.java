package com.jaymoskingdoms;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.7.3) Shield Throw, a Combat Art ({@link CombatArts}): with a shield in either hand, {@code Y} hurls it. It flies out
 * spinning flat, strikes the first creature in its way, ricochets on to the next foe (and off walls), and once it has
 * nothing left to hit it flies home to your hand. Each rank adds a ricochet.
 *
 * <p>The shield really leaves your hand while it flies (a spinning item display shows it). Anything that cuts the flight
 * short (you die, change dimension, log out, the server stops, it's out too long) puts it straight back in your
 * inventory, or at your feet if there's no room, so it can never be lost.
 */
public final class ShieldThrow {
	/** Out: speed (blocks a tick) and how far it goes before turning home if it hits nothing. */
	static final double OUT_SPEED = 1.3, OUT_RANGE = 22.0;
	/** Home: speed (blocks a tick), and how close it must come to be caught. */
	static final double HOME_SPEED = 1.5, CATCH_REACH = 1.4;
	/** Hits: the first one plus this many ricochets per rank. A wall bounce counts as a ricochet too. */
	static final int RICOCHETS_PER_RANK = 1;
	/** A ricochet looks for the next foe this far from the last thing it hit (and in sight of it). */
	static final double RICOCHET_REACH = 9.0;
	/** What a hit deals (times your attack damage, at least {@link #MIN_DAMAGE}), and how hard it shoves. */
	static final float DAMAGE = 1.2F, MIN_DAMAGE = 5.0F;
	static final double SHOVE = 0.6, SHOVE_UP = 0.25;
	/** The shield's own wear: durability lost per hit. */
	static final int WEAR = 1;
	/** Its wait (ticks), counted from the throw; a flight longer than this is cut short and the shield handed back. */
	static final int COOLDOWN = 100, MAX_FLIGHT = 160;
	static final float HUNGER = 1.5F;
	/** How fast the shield spins (radians a tick) and how big it looks. */
	static final float SPIN = 1.1F, SCALE = 0.9F;

	/** A shield in the air. */
	private static final class Flight {
		final UUID owner;
		final InteractionHand hand;
		final ItemStack shield;
		final Display.ItemDisplay display;
		Vec3 pos, motion;
		double travelled;
		int ricochetsLeft, ticks;
		boolean home;
		final Set<UUID> hit = new HashSet<>();
		LivingEntity next;

		Flight(UUID owner, InteractionHand hand, ItemStack shield, Display.ItemDisplay display, Vec3 pos, Vec3 motion, int ricochets) {
			this.owner = owner;
			this.hand = hand;
			this.shield = shield;
			this.display = display;
			this.pos = pos;
			this.motion = motion;
			this.ricochetsLeft = ricochets;
		}
	}

	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private ShieldThrow() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ShieldThrow::tick);
		// (before the player is saved, so the shield is back in their inventory when they are)
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Flight flight = FLIGHTS.remove(handler.player.getUUID());
			if (flight != null) {
				giveBack(handler.player, flight);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Flight flight : new ArrayList<>(FLIGHTS.values())) {
				ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner);
				if (owner != null) {
					giveBack(owner, flight);
				} else {
					flight.display.discard();
				}
			}
			FLIGHTS.clear();
			READY.clear();
		});
	}

	/** The hand holding a shield (the off hand first), or null. */
	static InteractionHand shieldHand(ServerPlayer player) {
		if (player.getOffhandItem().is(Items.SHIELD)) {
			return InteractionHand.OFF_HAND;
		}
		return player.getMainHandItem().is(Items.SHIELD) ? InteractionHand.MAIN_HAND : null;
	}

	/** Whether {@code Y} should throw: the perk is known and a shield is in hand. */
	static boolean wanted(ServerPlayer player) {
		return Skills.rank(player, Skills.Perk.SHIELD_THROW) > 0 && (shieldHand(player) != null || FLIGHTS.containsKey(player.getUUID()));
	}

	/** {@code Y} with a shield: throw it (or, while it's out, call it home). */
	static void use(ServerLevel level, ServerPlayer player) {
		Flight out = FLIGHTS.get(player.getUUID());
		if (out != null) {
			out.home = true;
			return;
		}
		long now = level.getGameTime();
		long left = READY.getOrDefault(player.getUUID(), 0L) - now;
		if (left > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.combat_art.wait",
				Component.translatable("skill.jaymoskingdoms.perk.shield_throw"), (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		InteractionHand hand = shieldHand(player);
		if (hand == null) {
			return;
		}
		ItemStack shield = player.getItemInHand(hand).copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		player.stopUsingItem();

		Vec3 look = player.getLookAngle();
		Vec3 start = player.getEyePosition().add(look.scale(0.6)).add(0.0, -0.2, 0.0);
		Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		display.setItemStack(shield.copyWithCount(1));
		display.setItemTransform(ItemDisplayContext.NONE);
		display.setPos(start.x, start.y, start.z);
		display.setPosRotInterpolationDuration(1);
		display.setTransformationInterpolationDuration(1);
		spin(display, 0);
		level.addFreshEntity(display);

		int rank = Skills.rank(player, Skills.Perk.SHIELD_THROW);
		FLIGHTS.put(player.getUUID(), new Flight(player.getUUID(), hand, shield, display, start, look.normalize().scale(OUT_SPEED),
			rank * RICOCHETS_PER_RANK));
		READY.put(player.getUUID(), now + COOLDOWN);
		player.causeFoodExhaustion(HUNGER);
		player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** The shield lies flat and turns like a thrown disc. */
	private static void spin(Display.ItemDisplay display, int ticks) {
		Quaternionf turn = new Quaternionf().rotateY(ticks * SPIN).rotateX((float) (Math.PI / 2));
		display.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(SCALE), null));
		display.setTransformationInterpolationDelay(0);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Flight>> it = FLIGHTS.entrySet().iterator(); it.hasNext(); ) {
			Flight flight = it.next().getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner);
			if (owner == null) {
				flight.display.discard();
				it.remove();
				continue;
			}
			if (!owner.isAlive() || owner.level() != flight.display.level() || flight.display.isRemoved() || ++flight.ticks > MAX_FLIGHT) {
				giveBack(owner, flight);
				it.remove();
				continue;
			}
			if (flight.shield.isEmpty()) {
				// it broke on its last blow
				flight.display.discard();
				it.remove();
				continue;
			}
			if (fly((ServerLevel) owner.level(), owner, flight)) {
				catchIt(owner, flight);
				it.remove();
			}
		}
	}

	/** One tick of flight. True once it's back in the hand. */
	private static boolean fly(ServerLevel level, ServerPlayer owner, Flight flight) {
		Vec3 from = flight.pos;
		if (flight.home) {
			Vec3 chest = owner.position().add(0.0, owner.getBbHeight() * 0.6, 0.0);
			Vec3 to = chest.subtract(from);
			if (to.length() <= Math.max(CATCH_REACH, HOME_SPEED)) {
				return true;
			}
			// home through anything in the way, like it knows the road back
			flight.motion = to.normalize().scale(HOME_SPEED);
			move(level, flight, from.add(flight.motion));
			return false;
		}
		if (flight.next != null) {
			// on a ricochet: steer for the next foe
			if (!flight.next.isAlive() || flight.next.level() != level) {
				flight.next = null;
			} else {
				Vec3 aim = flight.next.position().add(0.0, flight.next.getBbHeight() * 0.5, 0.0).subtract(from);
				if (aim.lengthSqr() > 1.0E-4) {
					flight.motion = aim.normalize().scale(OUT_SPEED);
				}
			}
		}
		Vec3 to = from.add(flight.motion);
		// the first living thing on the way
		LivingEntity struck = null;
		double best = Double.MAX_VALUE;
		AABB sweep = new AABB(from, to).inflate(0.6);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != owner && e.isAlive() && !e.isSpectator()
			&& !flight.hit.contains(e.getUUID()) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
			if (living.getBoundingBox().inflate(0.4).clip(from, to).isPresent() || living.getBoundingBox().inflate(0.4).contains(from)) {
				double d = living.distanceToSqr(from);
				if (d < best) {
					best = d;
					struck = living;
				}
			}
		}
		BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, flight.display));
		boolean walled = wall.getType() != HitResult.Type.MISS && (struck == null || wall.getLocation().distanceToSqr(from) < best);
		if (struck != null && !walled) {
			Vec3 at = struck.position().add(0.0, struck.getBbHeight() * 0.5, 0.0);
			strike(level, owner, flight, struck);
			move(level, flight, at);
			flight.next = flight.ricochetsLeft > 0 ? nextFoe(level, owner, flight, struck) : null;
			if (flight.next != null) {
				flight.ricochetsLeft--;
			} else {
				flight.home = true;
			}
			return false;
		}
		if (walled) {
			Vec3 at = wall.getLocation();
			BlockPos pos = wall.getBlockPos();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9F, 1.4F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)), at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.1);
			Direction face = wall.getDirection();
			flight.next = null;
			if (flight.ricochetsLeft > 0) {
				// off the wall like a ball off a cushion, on towards a foe if there's one in sight of the bounce
				flight.ricochetsLeft--;
				Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
				flight.motion = flight.motion.subtract(n.scale(2.0 * flight.motion.dot(n)));
				Vec3 bounced = at.add(n.scale(0.3));
				move(level, flight, bounced);
				flight.next = nextFoe(level, owner, flight, null);
			} else {
				move(level, flight, at.add(new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()).scale(0.3)));
				flight.home = true;
			}
			return false;
		}
		move(level, flight, to);
		flight.travelled += flight.motion.length();
		if (flight.travelled >= OUT_RANGE && flight.next == null) {
			flight.home = true;
		}
		return false;
	}

	private static void move(ServerLevel level, Flight flight, Vec3 to) {
		flight.pos = to;
		flight.display.setPos(to.x, to.y, to.z);
		spin(flight.display, flight.ticks);
		if (flight.ticks % 2 == 0) {
			level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (flight.ticks % 5 == 0) {
			level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.35F, 1.8F);
		}
	}

	private static void strike(ServerLevel level, ServerPlayer owner, Flight flight, LivingEntity target) {
		flight.hit.add(target.getUUID());
		float damage = Math.max(MIN_DAMAGE, (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE);
		CombatArts.strike(level, owner, target, damage);
		Vec3 push = flight.motion.multiply(1.0, 0.0, 1.0);
		if (push.lengthSqr() > 1.0E-4) {
			push = push.normalize().scale(SHOVE);
			target.setDeltaMovement(target.getDeltaMovement().add(push.x, SHOVE_UP, push.z));
			target.needsSync = true;
		}
		flight.shield.hurtAndBreak(WEAR, level, owner, item -> { });
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.9F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
		if (flight.shield.isEmpty()) {
			// worn through: it breaks on the blow
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	/** The nearest foe in sight of where the shield is, not yet struck this throw. */
	private static LivingEntity nextFoe(ServerLevel level, ServerPlayer owner, Flight flight, LivingEntity last) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity foe : CombatArts.foes(level, owner, new AABB(flight.pos, flight.pos).inflate(RICOCHET_REACH))) {
			if (foe == last || flight.hit.contains(foe.getUUID())) {
				continue;
			}
			double d = foe.distanceToSqr(flight.pos);
			if (d > RICOCHET_REACH * RICOCHET_REACH || d >= bestD) {
				continue;
			}
			Vec3 at = foe.position().add(0.0, foe.getBbHeight() * 0.5, 0.0);
			if (level.clip(new ClipContext(flight.pos, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, flight.display)).getType() == HitResult.Type.MISS) {
				best = foe;
				bestD = d;
			}
		}
		return best;
	}

	/** Back in the hand it left (or anywhere in the inventory, or at your feet). */
	private static void catchIt(ServerPlayer owner, Flight flight) {
		giveBack(owner, flight);
		if (!flight.shield.isEmpty()) {
			owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.9F, 1.3F);
		}
	}

	private static void giveBack(ServerPlayer owner, Flight flight) {
		flight.display.discard();
		ItemStack shield = flight.shield;
		if (shield.isEmpty()) {
			return;
		}
		if (owner.getItemInHand(flight.hand).isEmpty()) {
			owner.setItemInHand(flight.hand, shield);
		} else if (!owner.getInventory().add(shield)) {
			owner.drop(shield, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}
}
