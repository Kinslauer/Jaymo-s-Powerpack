package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.power.chrono.ChronoCheatDeath;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * (0.20.111) Andy (2026-11-05): "players can pass out at low thirst or hunger, their fov slowly closing to implicate
 * something is wrong, increasing as hunger/thirst lowers, eventually they could be doing anything and fall over, the screen
 * slowly letterboxing fully shut, as they collapse over, losing control even as they walk and falling to the floor flat on
 * their face, but ultimately ragdolling in any position. their FOV stays with the head as usual."
 * <ul>
 * <li><b>Light-headed:</b> with food or water (Jaymo's Kitchen's thirst) at {@link #LIGHTHEADED} or less, the edges of the
 * screen darken inward, more the lower the emptier one gets ({@link #weakness}; drawn by the client's {@code FaintHud}).</li>
 * <li><b>Swooning:</b> at {@link #FAINT_FROM} weakness or worse, each second has a chance ({@link #CHANCE_LEAST} to
 * {@link #CHANCE_MOST}, doubled while sprinting or swimming) that they start to go. For {@link #SWOON} the letterbox closes
 * fully shut, the darkness closes in, their head droops and sways, and their keys stop answering: whatever they were doing
 * (walking, running, standing) they keep on doing, slower and staggering ({@code FaintInputMixin}).</li>
 * <li><b>Out:</b> they collapse forward onto their face as Kitchen's ragdoll ({@link #TIP}), and lie as it lands for
 * {@link #OUT}, the screen black; first person rides in the ragdoll's head as always. A blow ({@link Downed#attack}) brings
 * them round at once.</li>
 * <li><b>Coming round:</b> they get up and the letterbox opens over {@link #WAKE}. No fainting again for {@link #COOLDOWN}.</li>
 * </ul>
 * Not while downed, carried, held in Chrono's window, left for dead, on adrenaline (it keeps them up), riding, asleep, in
 * creative or spectating. Passed out, nobody walks up to talk ({@link com.jaymospowers.core.OutOfPlay}). No text: a slow
 * heartbeat in their own ears is the only sound.
 */
public final class Fainting {
	/** Food or water at or under this (of 20) starts the darkness at the edges; at nothing it's the worst. */
	public static final int LIGHTHEADED = 6;
	/** Weakness (0-1) from which a faint can come: 0.5 is 3 food or water left. */
	static final float FAINT_FROM = 0.5F;
	/** The chance a second of a faint starting: just at {@link #FAINT_FROM}, and at nothing at all. */
	static final float CHANCE_LEAST = 1.0F / 240.0F, CHANCE_MOST = 1.0F / 40.0F;
	/** Sprinting or swimming, the chance is this many times greater. */
	static final float EXERTION = 2.0F;
	/** Going (letterbox closing, keys lost), lying out, and coming round (ticks). */
	public static final int SWOON = 80, OUT = 260, WAKE = 60;
	/** After coming round, no fainting again for this long (ticks). */
	static final int COOLDOWN = 1200;
	/** The collapse: tipped forward this fast (blocks a tick), on top of whatever speed they still had. */
	static final double TIP = 0.22;
	/**
	 * The client holds them limp this long after the swoon ends (ticks), so the server's forward tip gets there first and
	 * starts the fall; if it never does, the hold puts them down anyway.
	 */
	static final int HOLD_GRACE = 6;

	/** When the faint began (game time). Synced to the fainting player, not saved. */
	public static final AttachmentType<Long> FAINT = AttachmentRegistry.create(JaymosPowers.id("wounds_faint"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.targetOnly()));

	/** Server: no fainting before this (game time), by player. */
	private static final Map<UUID, Long> RESTED = new HashMap<>();
	/** Server: fainting players who have already been tipped over. */
	private static final Map<UUID, Boolean> TIPPED = new HashMap<>();

	private Fainting() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Fainting::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked && damageTaken > 0.0F && out(player) && Downed.attack(source)) {
				// a blow brings them round
				bringRound(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RESTED.clear();
			TIPPED.clear();
		});
	}

	/**
	 * How weak {@code player} is (0: fine, 1: no food or no water at all): from whichever of food and water is lower, or
	 * the steps of exhaustion ({@link Exhaustion#weakness}) if those are worse. Both sides.
	 */
	public static float weakness(Player player) {
		return Math.max(hungerWeakness(player), Exhaustion.weakness(player));
	}

	/** How empty {@code player} is of food or water alone (0-1). Both sides. */
	public static float hungerWeakness(Player player) {
		int least = Math.min(player.getFoodData().getFoodLevel(), water(player));
		return Mth.clamp((LIGHTHEADED - least) / (float) LIGHTHEADED, 0.0F, 1.0F);
	}

	/** (0.20.112) Whether it's thirst more than hunger that has {@code player} fainting (their water is the lower). Both sides. */
	public static boolean thirst(Player player) {
		return water(player) < player.getFoodData().getFoodLevel();
	}

	/**
	 * (0.20.112) Someone shakes {@code player} awake (Kingdoms' villagers, {@code FaintWakers}): lying out, they come round
	 * now, the letterbox opening as after any faint. Returns whether they were out.
	 */
	public static boolean bringRound(ServerPlayer player) {
		if (!out(player)) {
			return false;
		}
		player.setAttached(FAINT, player.level().getGameTime() - SWOON - OUT);
		return true;
	}

	/** Water left (Kitchen's thirst), or full without it. */
	private static int water(Player player) {
		try {
			return com.jaymoskitchen.climate.Thirst.water(player);
		} catch (LinkageError e) {
			return 20;
		}
	}

	/** Ticks since {@code entity} began to faint, or -1. */
	public static long since(Entity entity) {
		Long start = entity.getAttached(FAINT);
		return start == null ? -1L : Math.max(0L, entity.level().getGameTime() - start);
	}

	/** Going: keys lost, the letterbox closing. */
	public static boolean swooning(Entity entity) {
		long t = since(entity);
		return t >= 0 && t < SWOON;
	}

	/** Collapsed and lying out. */
	public static boolean out(Entity entity) {
		long t = since(entity);
		return t >= SWOON && t < SWOON + OUT;
	}

	/** Going or out: nobody should walk up and talk to them ({@link com.jaymospowers.core.OutOfPlay}). */
	public static boolean is(Entity entity) {
		long t = since(entity);
		return t >= 0 && t < SWOON + OUT;
	}

	/** Client, for Kitchen's ragdoll hold: lying out, a moment after the swoon ends (see {@link #HOLD_GRACE}). */
	public static boolean heldDown(Player player) {
		long t = since(player);
		return t >= SWOON + HOLD_GRACE && t < SWOON + OUT;
	}

	/** Whether a faint may start for {@code player} now. */
	private static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.isSleeping() && !player.isPassenger()
			&& !Downed.isDowned(player) && !Carry.isCarried(player) && !Adrenaline.active(player) && !ChronoCheatDeath.held(player)
			&& !Mortality.waking(player) && !player.hasAttached(Mortality.REBORNING) && !limp(player);
	}

	private static boolean limp(ServerPlayer player) {
		try {
			return com.jaymoskitchen.ragdoll.LivingRagdolls.isLimp(player);
		} catch (LinkageError e) {
			return false;
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			long now = player.level().getGameTime();
			if (player.hasAttached(FAINT)) {
				following(player, id, now);
			} else if (player.tickCount % 20 == 0 && now >= RESTED.getOrDefault(id, Long.MIN_VALUE)) {
				maybeFaint(player, now);
			}
		}
	}

	/** A faint under way: the collapse when the swoon ends, and the end of it. */
	private static void following(ServerPlayer player, UUID id, long now) {
		long t = since(player);
		if (!player.isAlive() || Downed.isDowned(player) || Carry.isCarried(player) || ChronoCheatDeath.held(player) || player.isCreative()
			|| player.isSpectator() || t >= SWOON + OUT + WAKE) {
			player.removeAttached(FAINT);
			TIPPED.remove(id);
			RESTED.put(id, now + COOLDOWN);
			return;
		}
		if (t < SWOON) {
			player.setSprinting(false);
		} else if (t < SWOON + OUT && TIPPED.putIfAbsent(id, true) == null) {
			// over onto their face, the way they were facing
			Vec3 look = player.getLookAngle();
			double flat = Math.hypot(look.x, look.z);
			double x = flat < 1.0E-4 ? 0.0 : look.x / flat * TIP, z = flat < 1.0E-4 ? 0.0 : look.z / flat * TIP;
			try {
				com.jaymoskitchen.ragdoll.LivingRagdolls.knock(player, x, 0.0, z);
			} catch (LinkageError e) {
				// an older Kitchen: the client's hold still puts them down
			}
		}
	}

	private static void maybeFaint(ServerPlayer player, long now) {
		float weak = weakness(player);
		if (weak < FAINT_FROM || !able(player)) {
			return;
		}
		float chance = Mth.lerp((weak - FAINT_FROM) / (1.0F - FAINT_FROM), CHANCE_LEAST, CHANCE_MOST);
		if (player.isSprinting() || player.isSwimming()) {
			chance *= EXERTION;
		}
		if (player.getRandom().nextFloat() >= chance) {
			return;
		}
		player.setAttached(FAINT, now);
		TIPPED.remove(player.getUUID());
		player.setSprinting(false);
		// a slow, heavy heartbeat in their own ears
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_HEARTBEAT),
			SoundSource.PLAYERS, player.getX(), player.getEyeY(), player.getZ(), 0.8F, 0.55F, player.getRandom().nextLong()));
	}
}
