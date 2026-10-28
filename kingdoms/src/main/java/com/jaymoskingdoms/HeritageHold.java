package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.262, Andy's NOEA picks) Absorbing with the heritage key ({@code ;}, the same key as a Warrior-born's tail): the
 * Bio-built and the Rubber-folk take a foe into themselves.
 * <p>
 * (0.7.288, Andy: "it should animate with some part of the body touching the enemy, and pulsing through both entities,
 * until it gradually shrinks to nothing. instead of a hold this is a toggle. that gets interrupted if you are damaged.")
 * A press on a fitting foe up close starts it ({@link #start}), another press lets go ({@link #stop}). While it runs
 * ({@link #step}) the foe is drawn in under the reaching hand ({@link #ABSORBING}, synced: the client's
 * {@code BioAbsorbPose}), pulses run round both bodies and along the arm, quicker as it goes, and the foe shrinks away to
 * nothing; then it's theirs. Any harm to them breaks it off, and a foe let go grows back.
 */
public final class HeritageHold {
	/** A message on the action bar every this many ticks while absorbing. */
	static final int PROGRESS_EVERY = 5;
	/**
	 * The foe is drawn in to this far (blocks) at up to this much a tick, so the reaching hand is on it; it slips free this
	 * much past the reach it was taken at; and it is this much smaller (share) when it's gone.
	 */
	static final double TOUCH = 0.9, DRAW = 0.08, SLACK = 1.5, SHRINK = 0.95;
	/** A pulse every this many ticks at first, and at the end. */
	static final int PULSE_FIRST = 14, PULSE_LAST = 4;

	/** The foe's entity id while a player is absorbing it, synced to everyone (the reaching hand). */
	public static final AttachmentType<Integer> ABSORBING = AttachmentRegistry.create(JaymosKingdoms.id("heritage_absorbing"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** The foe shrinking away as it's taken in. */
	private static final Identifier SHRINK_ID = JaymosKingdoms.id("heritage_absorb_shrink");

	/**
	 * One absorbing under way: the foe, its shape as it was taken (before it shrank), how many ticks it has run, and how
	 * far along it was at the last pulse (0 .. 1).
	 */
	static final class Channel {
		final LivingEntity target;
		final net.minecraft.world.entity.EntityDimensions shape;
		int ticks;
		double pulsed;

		Channel(LivingEntity target) {
			this.target = target;
			this.shape = target.getType().getDimensions().scale(target.getScale());
		}
	}

	/** Player → the foe they are taking in. */
	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

	private HeritageHold() {
	}

	static void register() {
		// hurt while absorbing, they lose hold of it
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof ServerPlayer player && CHANNELS.containsKey(player.getUUID())) {
				stop(player, "message.jaymoskingdoms.absorb_hurt");
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player, null));
	}

	/**
	 * Something a heritage may take as a foe: a monster, or anything going for {@code player}; never a player, a villager, a
	 * kingdom's folk, or a tamed animal.
	 */
	static boolean foe(ServerPlayer player, LivingEntity e) {
		if (!e.isAlive() || e instanceof Player || e instanceof Villager || e instanceof Folk || e instanceof OwnableEntity owned && owned.getOwner() != null) {
			return false;
		}
		return e instanceof Enemy || e instanceof Mob m && m.getTarget() == player;
	}

	/** The channel {@code player} has running, or null. */
	static @Nullable Channel channel(ServerPlayer player) {
		return CHANNELS.get(player.getUUID());
	}

	/**
	 * The heritage key pressed: absorbing, it lets go (true); looking at a foe that {@code fits} within {@code reach}, it
	 * starts taking it in (true, {@code bar} shown); else false, for the heritage to do whatever else the key does.
	 */
	static boolean press(ServerPlayer player, Predicate<LivingEntity> fits, double reach, String bar) {
		if (CHANNELS.containsKey(player.getUUID())) {
			stop(player, "message.jaymoskingdoms.absorb_stopped");
			return true;
		}
		if (!player.isAlive() || player.isSpectator() || Downed.isDowned(player)) {
			return false;
		}
		LivingEntity sighted = Arcana.sighted(player);
		if (sighted == null || !fits.test(sighted) || player.distanceTo(sighted) > reach) {
			return false;
		}
		CHANNELS.put(player.getUUID(), new Channel(sighted));
		player.setAttached(ABSORBING, sighted.getId());
		player.sendOverlayMessage(Component.translatable(bar, 0));
		return true;
	}

	/** Lets go of the foe being absorbed (if any): it grows back, and {@code why} says so (null: nothing said). */
	static void stop(ServerPlayer player, @Nullable String why) {
		Channel channel = CHANNELS.remove(player.getUUID());
		if (player.hasAttached(ABSORBING)) {
			player.removeAttached(ABSORBING);
		}
		if (channel == null) {
			return;
		}
		shrink(channel.target, 0.0);
		if (why != null) {
			player.sendOverlayMessage(Component.translatable(why).withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * One tick of {@code player}'s absorbing, if they have one running: the foe drawn in under the hand, a pulse through both
	 * now and then ({@code fx}, {@code throb}), the foe shrinking, the bar ({@code bar}). It ends if the foe stops being one
	 * that {@code still} fits or gets past {@code reach}. Returns the foe once {@code need} ticks have run, else null.
	 */
	static @Nullable LivingEntity step(ServerLevel level, ServerPlayer player, Predicate<LivingEntity> still, double reach, int need,
		ParticleOptions fx, SoundEvent throb, String bar) {
		return step(level, player, still, reach, need, fx, throb, bar, TOUCH);
	}

	/** (0.7.313) As above, with the foe drawn in only to {@code touch} blocks (the Bio-built's tail reaches farther than a hand). */
	static @Nullable LivingEntity step(ServerLevel level, ServerPlayer player, Predicate<LivingEntity> still, double reach, int need,
		ParticleOptions fx, SoundEvent throb, String bar, double touch) {
		Channel channel = CHANNELS.get(player.getUUID());
		if (channel == null) {
			return null;
		}
		LivingEntity foe = channel.target;
		if (!player.isAlive() || player.isSpectator() || Downed.isDowned(player) || !foe.isAlive() || foe.isRemoved() || foe.level() != level
			|| !still.test(foe) || player.distanceTo(foe) > reach + SLACK) {
			stop(player, "message.jaymoskingdoms.absorb_lost");
			return null;
		}
		channel.ticks++;
		double progress = Math.min(1.0, channel.ticks / (double)need);
		// drawn in close, under the hand
		Vec3 away = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() > 1.0E-4) {
			Vec3 to = player.position().add(away.normalize().scale(touch)).subtract(foe.position()).multiply(1.0, 0.0, 1.0);
			double length = to.length();
			if (length > 0.1) {
				Vec3 move = to.scale(Math.min(DRAW, length) / length);
				foe.setDeltaMovement(move.x, foe.getDeltaMovement().y, move.z);
				foe.needsSync = true;
			}
		}
		shrink(foe, SHRINK * progress * progress * (3.0 - 2.0 * progress));
		int every = Math.max(PULSE_LAST, Math.round(Mth.lerp((float)progress, PULSE_FIRST, PULSE_LAST)));
		if (channel.ticks % every == 0) {
			channel.pulsed = progress;
			pulse(level, player, foe, fx, throb, progress);
		}
		if (channel.ticks % PROGRESS_EVERY == 0) {
			player.sendOverlayMessage(Component.translatable(bar, (int)(progress * 100)));
		}
		if (channel.ticks < need) {
			return null;
		}
		CHANNELS.remove(player.getUUID());
		player.removeAttached(ABSORBING);
		return foe;
	}

	static void shrink(LivingEntity foe, double share) {
		AttributeInstance scale = foe.getAttribute(Attributes.SCALE);
		if (scale == null) {
			return;
		}
		if (share <= 0.0) {
			scale.removeModifier(SHRINK_ID);
		} else {
			scale.addOrUpdateTransientModifier(new AttributeModifier(SHRINK_ID, -share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** A pulse through both: a ring of motes round each body and a run of them along the arm between, with a throb. */
	private static void pulse(ServerLevel level, ServerPlayer player, LivingEntity foe, ParticleOptions fx, SoundEvent throb, double progress) {
		for (LivingEntity body : new LivingEntity[] {foe, player}) {
			double w = body.getBbWidth() * 0.6, h = body.getBbHeight();
			for (int i = 0; i < 10; i++) {
				double a = i * (Math.PI * 2.0 / 10.0);
				level.sendParticles(fx, body.getX() + Math.cos(a) * w, body.getY() + h * 0.5, body.getZ() + Math.sin(a) * w, 1, 0.0, h * 0.25, 0.0, 0.0);
			}
		}
		pull(level, foe, player, fx, progress);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), throb, SoundSource.PLAYERS, 0.6F, 0.5F + (float)progress * 0.8F);
	}

	/** A few motes along the line from {@code from} to {@code to}, thicker toward the end ({@code progress} 0..1). */
	static void pull(ServerLevel level, LivingEntity from, LivingEntity to, ParticleOptions fx, double progress) {
		double fx0 = from.getX(), fy0 = from.getY(0.5), fz0 = from.getZ();
		double tx = to.getX(), ty = to.getY(0.6), tz = to.getZ();
		int n = 3 + (int)(progress * 4.0);
		for (int i = 0; i < n; i++) {
			double t = level.getRandom().nextDouble();
			level.sendParticles(fx, fx0 + (tx - fx0) * t, fy0 + (ty - fy0) * t, fz0 + (tz - fz0) * t, 1, 0.1, 0.1, 0.1, 0.0);
		}
	}
}
