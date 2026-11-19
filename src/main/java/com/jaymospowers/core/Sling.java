package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.KitchenCompat;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Heave a downed friend onto a horse. Carrying someone downed ({@link Carry}, not dragged), right-click a horse, donkey,
 * mule, camel or llama (any grown {@link AbstractHorse}): they're heaved up and lie slung across its back, face down,
 * head to one side and legs to the other, and ride along wherever it goes (ridden, led, or on a rope). One body to a
 * horse, as Kitchen's slung corpses.
 * <p>
 * A slung body counts as a carried one for every rule that asks {@link Carry#isCarried} (the horse's id is on it as its
 * {@link Carry#CARRIED_BY}): the downed can't act, crawl or struggle, no respawn countdown starts, nobody shoves or aims at
 * them. They aren't vehicle passengers, so nothing treats them as riders (the mount climb, the seat). Their client (and every
 * other) holds them at the horse's feet each tick and the pose lays them across its back ({@code SlingPose}); the server holds
 * a mob there too. They're set down beside the horse when they come to ({@link Downed#isDowned} no longer true), or if the
 * horse dies or goes.
 * <p>
 * Someone standing by the horse with free hands lifts the body off again: sneak + right-click the horse. It goes into their
 * arms (as a lift from the ground does), or is set down beside the horse if it's too heavy in its armour for them.
 * <p>
 * Never saved: a relog or a restart ends it, the body staying down where the horse was.
 */
public final class Sling {
	/** Heaving up takes this long (ticks): the body's arc onto the back, and the carrier's heave clip. */
	public static final int HEAVE_TICKS = 12;
	/** The body lies this far up the horse's height (share) and this far behind its middle (blocks). */
	public static final float BACK_HEIGHT = 0.8F, BACK_BEHIND = 0.35F;
	/** Set down: this far out from the horse's side (blocks, past its half width). */
	static final double SET_DOWN_OUT = 0.7;

	/** On the slung body: the horse (entity id), when it was heaved (game time), and which side the carrier stood (+1 its right, -1 its left). */
	public record Slung(int horse, long at, int side) {
		public static final StreamCodec<ByteBuf, Slung> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Slung::horse,
			ByteBufCodecs.VAR_LONG, Slung::at,
			ByteBufCodecs.VAR_INT, Slung::side,
			Slung::new);
	}

	/** On a body slung over a horse (synced to everyone, who draw it). Never saved. */
	public static final AttachmentType<Slung> SLUNG = AttachmentRegistry.create(JaymosPowers.id("sling_slung"),
		builder -> builder.syncWith(Slung.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** On whoever heaved: when (game time), for their heave clip. Synced to everyone. Never saved. */
	public static final AttachmentType<Long> HEAVED = AttachmentRegistry.create(JaymosPowers.id("sling_heaved"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private record Entry(LivingEntity body, Entity horse) {
	}

	/** Body → its horse. */
	private static final Map<UUID, Entry> SLINGS = new HashMap<>();

	private Sling() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register(Sling::onUseEntity);
		ServerTickEvents.END_SERVER_TICK.register(Sling::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Entry entry : SLINGS.values()) {
				entry.body.removeAttached(SLUNG);
				entry.body.removeAttached(Carry.CARRIED_BY);
			}
			SLINGS.clear();
		});
	}

	// ---- queries (both sides)

	/** Whether this is slung over a horse's back. */
	public static boolean isSlung(Entity entity) {
		return entity.hasAttached(SLUNG);
	}

	/** Whether a grown horse (or donkey, mule, camel, llama...) can have someone slung over it. */
	public static boolean slingable(Entity entity) {
		return entity instanceof AbstractHorse horse && horse.isAlive() && !horse.isBaby();
	}

	/**
	 * Both sides: whether {@code body} is a downed one that could be heaved onto a horse from a carry. A carry that drags,
	 * or holds a block or a severed part, is none.
	 */
	public static boolean heavable(@Nullable Entity body, int hold) {
		return body instanceof LivingEntity && Downed.isDowned(body) && hold != Carry.DRAG && hold != Carry.BLOCK && hold != Carry.PART;
	}

	/** Server: the body slung over this horse, if any. */
	public static @Nullable LivingEntity slungOn(Entity horse) {
		for (Entry entry : SLINGS.values()) {
			if (entry.horse == horse) {
				return entry.body;
			}
		}
		return null;
	}

	// ---- heaving up, lifting off

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !slingable(target)) {
			return InteractionResult.PASS;
		}
		Carry.Carrying carrying = player.getAttached(Carry.CARRYING);
		if (carrying != null && heavable(level.getEntity(carrying.target()), carrying.hold())) {
			if (player instanceof ServerPlayer carrier) {
				heave(carrier, target);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown() && carrying == null && player.getMainHandItem().isEmpty() && !Carry.isCarried(player) && !Downed.isDowned(player)
			&& !KitchenCompat.isCarryingCorpse(player) && hasSlung(level, target)) {
			if (player instanceof ServerPlayer taker) {
				liftOff(taker, slungOn(target));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Someone is slung over this horse (read from the synced attachments, so either side can ask). */
	private static boolean hasSlung(Level level, Entity horse) {
		for (Entity e : level.getEntities(horse, horse.getBoundingBox().inflate(2.0), other -> true)) {
			Slung slung = e.getAttached(SLUNG);
			if (slung != null && slung.horse() == horse.getId()) {
				return true;
			}
		}
		return false;
	}

	/** The carrier heaves who they carry up over {@code horse}'s back. */
	private static void heave(ServerPlayer carrier, Entity horse) {
		Entity held = Carry.held(carrier);
		if (!(held instanceof LivingEntity body) || !(carrier.level() instanceof ServerLevel level) || body.level() != level) {
			return;
		}
		if (slungOn(horse) != null) {
			carrier.sendOverlayMessage(Component.translatable("message.jaymospowers.sling.full").withStyle(ChatFormatting.GRAY));
			return;
		}
		Carry.handOver(carrier);
		long now = level.getGameTime();
		// which side the carrier stands: the body starts out there and swings up over the back
		float yaw = (horse instanceof LivingEntity living ? living.yBodyRot : horse.getYRot()) * Mth.DEG_TO_RAD;
		double rightX = -Mth.cos(yaw), rightZ = -Mth.sin(yaw);
		int side = (carrier.getX() - horse.getX()) * rightX + (carrier.getZ() - horse.getZ()) * rightZ >= 0.0 ? 1 : -1;
		if (body instanceof ServerPlayer player) {
			// (they were held by the carrier's own pose: that ends now)
			ServerPlayNetworking.send(player, new Carry.Self(-1));
			player.stopUsingItem();
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.sling.slung", carrier.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
		body.stopRiding();
		body.removeAttached(Carry.THROWN);
		body.setAttached(Carry.CARRIED_BY, horse.getId());
		body.setAttached(SLUNG, new Slung(horse.getId(), now, side));
		carrier.setAttached(HEAVED, now);
		SLINGS.put(body.getUUID(), new Entry(body, horse));
		carrier.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7F, 0.8F);
	}

	/** Takes the body off the horse into {@code taker}'s arms, or sets it down beside the horse if it's too heavy in its armour for them. */
	private static void liftOff(ServerPlayer taker, @Nullable LivingEntity body) {
		if (body == null) {
			return;
		}
		Entry entry = SLINGS.get(body.getUUID());
		if (entry == null || taker.level() != body.level() || Carry.liftGap(taker, entry.horse) > Carry.LIFT_GAP + Carry.LIFT_SLACK) {
			return;
		}
		if (Carry.overloaded(taker, body) > 0 || !Carry.canBeCarriedBy(taker, body)) {
			setDown(entry);
			return;
		}
		clear(entry);
		Carry.pickUp(taker, body);
	}

	// ---- every tick

	private static void tick(MinecraftServer server) {
		if (SLINGS.isEmpty()) {
			return;
		}
		for (Entry entry : new ArrayList<>(SLINGS.values())) {
			LivingEntity body = entry.body;
			Entity horse = entry.horse;
			if (body.isRemoved() || !body.isAlive()) {
				SLINGS.remove(body.getUUID());
				continue;
			}
			if (horse.isRemoved() || !horse.isAlive() || horse.level() != body.level() || body.isPassenger() || !Downed.isDowned(body)
				|| body instanceof Player player && (player.isSleeping() || player.isSpectator())) {
				// came to, or the horse is gone: down beside it
				setDown(entry);
				continue;
			}
			body.resetFallDistance();
			if (!(body instanceof Player)) {
				// (a player's own client holds them there; the server holds a mob at the horse's feet, and every client draws it across the back)
				body.setPos(horse.getX(), horse.getY(), horse.getZ());
				body.setDeltaMovement(Vec3.ZERO);
				body.setYRot(horse.getYRot());
				body.yBodyRot = horse instanceof LivingEntity living ? living.yBodyRot : horse.getYRot();
				if (body instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}
	}

	/** Forgets the sling, leaving the body where it is. */
	private static void clear(Entry entry) {
		SLINGS.remove(entry.body.getUUID());
		entry.body.removeAttached(SLUNG);
		entry.body.removeAttached(Carry.CARRIED_BY);
	}

	/** Off the horse and down on the ground beside it (the first side with room). */
	private static void setDown(Entry entry) {
		clear(entry);
		LivingEntity body = entry.body;
		if (body.isRemoved() || !body.isAlive() || !(body.level() instanceof ServerLevel level)) {
			return;
		}
		Entity horse = entry.horse;
		Vec3 at = body.position();
		if (!horse.isRemoved() && horse.level() == level) {
			float yaw = (horse instanceof LivingEntity living ? living.yBodyRot : horse.getYRot()) * Mth.DEG_TO_RAD;
			double out = horse.getBbWidth() * 0.5 + SET_DOWN_OUT;
			at = horse.position();
			for (int side : new int[] {1, -1}) {
				Vec3 spot = horse.position().add(-Mth.cos(yaw) * out * side, 0.0, -Mth.sin(yaw) * out * side);
				if (level.noCollision(body, body.getBoundingBox().move(spot.subtract(body.position())))) {
					at = spot;
					break;
				}
			}
		}
		body.resetFallDistance();
		if (body instanceof ServerPlayer player) {
			Carry.grace(player, level.getGameTime());
			player.connection.teleport(new PositionMoveRotation(at, Vec3.ZERO, player.getYRot(), player.getXRot()), Set.of());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.sling.off").withStyle(ChatFormatting.GRAY));
		} else {
			body.setPos(at.x, at.y, at.z);
			body.setDeltaMovement(Vec3.ZERO);
			body.syncVelocity = true;
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
	}
}
