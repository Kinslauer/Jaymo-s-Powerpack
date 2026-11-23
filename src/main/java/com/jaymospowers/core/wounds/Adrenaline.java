package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * (0.20.110) Andy (2026-11-05): "adrenaline should kick in if you are low health and fighting or running, meaning you can
 * be damaged beyond repair on your limbs, (not head or Torso) and still sprint, and attack, and stand yourself up, but
 * only for 10s after being hit below half health. or within 40% of whatever death threshold."
 * <p>
 * A player struck by someone or something ({@link Downed#attack}: not fire, a fall, hunger) who is left
 * <ul>
 * <li>at or under {@link #HALF} of their most health, or</li>
 * <li>with their head or body (the parts that down or kill when they run out) at or under {@link #NEAR_DEATH} of whole, or</li>
 * <li>downed (one hard blow from death),</li>
 * </ul>
 * has adrenaline for {@link #TICKS} from that hit (each such hit starts it afresh). Going down gives it too: the downed
 * state's own first rush ({@link Downed#adrenaline}) counts. While it lasts, broken arms, legs and feet hinder nothing:
 * <ul>
 * <li>they sprint, and two broken legs stand instead of crawling ({@link Breaks#crawlOnly});</li>
 * <li>broken arms swing, dig, hold a shield and draw a bow at full strength ({@link Breaks#armBroken}, {@link Breaks#apply}),
 * and every power that uses the limb works as if it were sound ({@link LimbUse#strength});</li>
 * <li>downed, a broken or red arm no longer keeps them from getting up by themselves ({@link Downed#blocksRise}); a rise
 * begun on adrenaline is finished on it.</li>
 * </ul>
 * The head and body still count as they always did, and a severed limb is still gone (nothing to sprint or swing with).
 * When it wears off, the breaks take hold again (two broken legs put them back on the ground). Nothing is said: a
 * heartbeat as it kicks in is the only sign, heard only by them.
 * <p>Andy (2026-11-23): "the adrenaline should activate once per 15m and it should fill the new stamina 'wind' meter,
 * when it depletes you lose the adrenaline". A hit kicks it in at most once every {@link #COOLDOWN} ({@link #READY},
 * saved, kept through death), and while it runs further hits add nothing. With Jaymo's Kitchen's wind kept (its stamina
 * rule), kicking in fills the wind, the wind burns while it lasts (Kitchen's {@code Stamina.addBurning}: about 30 s
 * standing, 11 sprinting), and it lasts until the wind runs dry (at most {@link #WIND_LONGEST}). Without the wind it
 * lasts {@link #TICKS} as before.
 */
public final class Adrenaline {
	/** How long it lasts after the hit that starts it (ticks), where there's no wind to run it (no Kitchen, or its stamina rule off). */
	public static final int TICKS = 200;
	/** It kicks in at most once in this long (ticks: 15 minutes). */
	public static final int COOLDOWN = 20 * 60 * 15;
	/** Running on the wind, it lasts at most this long (ticks), should the wind stop burning (the stamina rule turned off). */
	static final int WIND_LONGEST = 20 * 60;
	/** Health this share of the most or less, after a hit, starts it. */
	public static final float HALF = 0.5F;
	/** The head or body this whole or less (40% of the way from their death at zero), after a hit, starts it. */
	public static final float NEAR_DEATH = 0.4F;
	/** A rise from the downed begun on adrenaline keeps it going this long past each tick of rising (ticks). */
	static final int RISE_GRACE = 20;

	/** Adrenaline until (game time). Synced to everyone (whose clients draw the gait and the crawl), not saved. */
	public static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(JaymosPowers.id("wounds_adrenaline"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));

	/** When it may kick in again (game time). Saved, and kept through death; only the server reads it. */
	public static final AttachmentType<Long> READY = AttachmentRegistry.create(JaymosPowers.id("wounds_adrenaline_ready"),
		builder -> builder.persistent(com.mojang.serialization.Codec.LONG).copyOnDeath());

	/** Players with adrenaline now, till it wears off. */
	private static final Set<ServerPlayer> TRACKED = new HashSet<>();
	/** Of those, the ones running on their wind (it ends when the wind runs dry). */
	private static final Set<ServerPlayer> ON_WIND = new HashSet<>();
	/** Kitchen's {@code Stamina} methods (by reflection, so an older or missing Kitchen just means no wind): fill, wind. */
	private static java.lang.reflect.@org.jspecify.annotations.Nullable Method fillWind, readWind;

	private Adrenaline() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked && damageTaken > 0.0F) {
				hit(player, source);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Adrenaline::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TRACKED.clear();
			ON_WIND.clear();
		});
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				Class<?> stamina = Class.forName("com.jaymoskitchen.climate.Stamina");
				java.lang.reflect.Method fill = stamina.getMethod("fill", ServerPlayer.class);
				java.lang.reflect.Method wind = stamina.getMethod("wind", net.minecraft.world.entity.player.Player.class);
				java.util.function.Predicate<net.minecraft.world.entity.player.Player> burning = player -> player instanceof ServerPlayer sp && ON_WIND.contains(sp);
				stamina.getMethod("addBurning", java.util.function.Predicate.class).invoke(null, burning);
				fillWind = fill;
				readWind = wind;
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Jaymo's Kitchen has no wind for adrenaline to run on ({}); it lasts {} ticks instead", e.toString(), TICKS);
			}
		}
	}

	/** Kitchen's wind filled for {@code player}; false where there's none kept (no Kitchen, an older one, its stamina rule off). */
	private static boolean fillWind(ServerPlayer player) {
		java.lang.reflect.Method fill = fillWind;
		if (fill == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(fill.invoke(null, player));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Whether {@code player}'s wind has run dry (Kitchen's {@code Stamina.wind(player).winded()}). */
	private static boolean windDry(ServerPlayer player) {
		java.lang.reflect.Method wind = readWind;
		if (wind == null) {
			return true;
		}
		try {
			Object now = wind.invoke(null, player);
			return Boolean.TRUE.equals(now.getClass().getMethod("winded").invoke(now));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true;
		}
	}

	/** Whether {@code entity} has adrenaline now (both sides): from a hit, or the first rush of going down. */
	public static boolean active(Entity entity) {
		Long until = entity.getAttached(UNTIL);
		return until != null && entity.level().getGameTime() < until || Downed.adrenaline(entity);
	}

	/** Whether {@code player}, just hit by {@code source}, is low enough for it to kick in. */
	static boolean low(LivingEntity player) {
		if (Downed.isDowned(player) || player.getHealth() <= player.getMaxHealth() * HALF) {
			return true;
		}
		BodyPlan plan = BodyPlan.of(player);
		if (plan == null) {
			return false;
		}
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (part.vital() && Wounds.fraction(player, part) <= NEAR_DEATH) {
				return true;
			}
		}
		return false;
	}

	private static void hit(ServerPlayer player, DamageSource source) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || !Downed.attack(source) || !low(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Long until = player.getAttached(UNTIL);
		Long ready = player.getAttached(READY);
		if (until != null && now < until || ready != null && now < ready) {
			// running already (the wind, or the time, runs it), or kicked in less than COOLDOWN ago
			return;
		}
		boolean fresh = !active(player);
		player.setAttached(READY, now + COOLDOWN);
		if (fillWind(player)) {
			ON_WIND.add(player);
			player.setAttached(UNTIL, now + WIND_LONGEST);
		} else {
			player.setAttached(UNTIL, now + TICKS);
		}
		TRACKED.add(player);
		if (fresh) {
			// a heartbeat in their ears, no one else's
			player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_HEARTBEAT),
				SoundSource.PLAYERS, player.getX(), player.getEyeY(), player.getZ(), 0.9F, 0.8F, player.getRandom().nextLong()));
			kickIn(player);
		}
	}

	/** The breaks let go at once: the limp, the weakness, and two broken legs' crawl. */
	private static void kickIn(ServerPlayer player) {
		if (!Downed.isDowned(player) && Breaks.bothLegsBroken(player) && player.hasAttached(com.jaymospowers.core.Crawl.CRAWLING)) {
			player.removeAttached(com.jaymospowers.core.Crawl.CRAWLING);
		}
		Breaks.refresh(player);
	}

	private static void tick(MinecraftServer server) {
		if (TRACKED.isEmpty()) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(TRACKED)) {
			if (player.isRemoved() || !player.isAlive()) {
				TRACKED.remove(player);
				ON_WIND.remove(player);
				player.removeAttached(UNTIL);
				continue;
			}
			long now = player.level().getGameTime();
			Long until = player.getAttached(UNTIL);
			if (until == null) {
				TRACKED.remove(player);
				ON_WIND.remove(player);
				continue;
			}
			if (ON_WIND.contains(player) && windDry(player)) {
				// the wind ran dry: it's over (a rise from the downed begun on it is still finished on it)
				ON_WIND.remove(player);
				player.setAttached(UNTIL, until = player.hasAttached(Downed.RISING) ? now + RISE_GRACE : now);
			}
			// (a rise begun on adrenaline is finished on it)
			if (until > now && player.hasAttached(Downed.RISING) && until < now + RISE_GRACE) {
				player.setAttached(UNTIL, until = now + RISE_GRACE);
			}
			if (now >= until) {
				TRACKED.remove(player);
				ON_WIND.remove(player);
				player.removeAttached(UNTIL);
				// the breaks take hold again (two broken legs: back on the ground, at the refresh)
				Breaks.refresh(player);
			}
		}
	}
}
