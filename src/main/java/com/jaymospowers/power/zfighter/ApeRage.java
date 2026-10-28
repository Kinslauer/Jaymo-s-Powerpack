package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * (0.15.148) Andy: "what does an ape takeover look like to you, add that and lemme see". A Z Fighter's Great Ape is his
 * to steer, but the ape has a mind of its own. Its <b>rage</b> ({@link #RAGE}, 0-100) builds while he's the ape (each
 * second, every blow he takes, every kill, being crippled); full, it throws a <b>fit</b>: the ape takes the wheel and
 * goes berserk on anything alive in sight, friend or not (the wild ape's own berserk walk, {@link ZFighterLineage}).
 * Every movement key is a heave against it (fill the bar, {@link #FIT}); tear free and the rage drops, or ride it out
 * and it comes back sooner.
 *
 * <p>Underneath it all is how much of him the ape owns, his <b>primal</b> ({@link #PRIMAL}, 0-100, kept): it grows with
 * every moon as the ape, every minute in it, every fit he loses and everyone it kills, and falls each time he wins one.
 * The more primal, the faster the rage builds, the longer the fits and the harder they are to break; at 100 the ape is
 * all that's left: a fit lasts until the moon lets him go.
 *
 * <p>Andy: "If you are able to fight it off successfully multiple times give access to a new form hidden until you do, a
 * super 4". Win {@link #SUPER_4_FITS} fits and the ape and the man make their peace: {@link #SUPER_4} opens, the
 * primal ape's look on his own body (red fur, a long red mane, fire and sparks), taken with C from his highest form.
 */
public final class ApeRage {
	/** Rage a second as the ape (times 1 + primal / {@code PRIMAL_RAGE_SCALE}), per point of damage taken, per kill, when crippled. */
	static final float RAGE_SECOND = 0.6F, PRIMAL_RAGE_SCALE = 50.0F, RAGE_PER_DAMAGE = 1.2F, RAGE_KILL = 6.0F, RAGE_CRIPPLED = 25.0F;
	/** After a fit: rage left when he tore free, when it ran its course. */
	static final float RAGE_AFTER_WIN = 30.0F, RAGE_AFTER_LOSS = 50.0F;
	/** A fit lasts this long (ticks), plus this per point of primal; at a whole primal, until the moon lets go. */
	static final int FIT_TICKS = 200, FIT_TICKS_PER_PRIMAL = 4;
	/** Presses to break it (plus a share of primal; at a whole primal, this many), and how much the bar drains a tick
	 * (0.17.1: eased from 15 / 60 / 0.25 / 0.12; Andy found the mashing overdone). */
	static final int NEEDED = 11, NEEDED_LOST = 42;
	static final float NEEDED_PER_PRIMAL = 0.18F, FADE = 0.09F, FADE_PER_PRIMAL = 0.001F;
	/** For the first ticks of a fit, presses don't count yet. */
	static final int GRACE = 20;
	/** Primal: gained each change, each minute as the ape, each fit lost, each person killed in a fit; lost each fit won. */
	static final int PRIMAL_CHANGE = 5, PRIMAL_MINUTE = 1, PRIMAL_LOST_FIT = 3, PRIMAL_KILL = 1, PRIMAL_WON_FIT = 4;
	public static final int PRIMAL_MAX = 100;
	/** Fits won (ever) before Super 4 opens; Super 4's form number (past the three bought ones). */
	public static final int SUPER_4_FITS = 5, SUPER_4 = 4;
	/** A fit shakes his screen: as it starts, and every this many ticks while it has him. */
	static final float FIT_SHAKE = 2.2F, FIT_PULSE_SHAKE = 0.8F;
	static final int FIT_PULSE_EVERY = 30;

	/** How much of him the ape owns (0-100). Saved, kept through death, synced to him. */
	public static final AttachmentType<Integer> PRIMAL = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_primal"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** The rage now (0-100), while he's the ape. Synced to him (his HUD). */
	public static final AttachmentType<Float> RAGE = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_rage"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));
	/** A fit has him: how far his struggle's got (0-1). Synced to him. */
	public static final AttachmentType<Float> FIT = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_fit"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	/** Fits he's won, ever (saved, kept through death). */
	public static final AttachmentType<Integer> TAMED = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_tamed"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).copyOnDeath());
	/** Super 4 is open to him (saved, kept through death, synced to him). */
	public static final AttachmentType<Boolean> SUPER_4_OPEN = AttachmentRegistry.create(JaymosPowers.id("zfighter_super_4"),
		builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Player → server: a heave against a fit. */
	public record Struggle() implements CustomPacketPayload {
		public static final Type<Struggle> TYPE = new Type<>(JaymosPowers.id("zfighter_ape_struggle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Struggle> CODEC = StreamCodec.unit(new Struggle());

		@Override
		public Type<Struggle> type() {
			return TYPE;
		}
	}

	/** A fit under way. */
	private static final class Fit {
		final long since;
		final long until;
		final int needed;
		final float fade;
		float struggle;
		long lastPress = -1L;

		Fit(long since, long until, int needed, float fade) {
			this.since = since;
			this.until = until;
			this.needed = needed;
			this.fade = fade;
		}
	}

	private static final Map<UUID, Fit> FITS = new HashMap<>();
	/** Ticks as the ape since the last minute's primal (not kept). */
	private static final Map<UUID, Integer> MINUTES = new HashMap<>();

	private ApeRage() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Struggle.TYPE, Struggle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Struggle.TYPE, (payload, context) -> struggle(context.player()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && ZFighterLineage.ape(killer) && !ZFighterLineage.wild(killer)) {
				addRage(killer, RAGE_KILL);
				if (FITS.containsKey(killer.getUUID()) && (entity instanceof Player || entity instanceof AbstractVillager)) {
					addPrimal(killer, PRIMAL_KILL);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			FITS.remove(handler.player.getUUID());
			MINUTES.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FITS.clear();
			MINUTES.clear();
		});
	}

	public static int primal(Player player) {
		Integer p = player.getAttached(PRIMAL);
		return p == null ? 0 : p;
	}

	public static float rage(Player player) {
		Float r = player.getAttached(RAGE);
		return r == null ? 0.0F : r;
	}

	/** Super 4 is his to take now: opened, a Warrior-born past the breakthrough, not the ape. */
	public static boolean super4(Player player) {
		return Boolean.TRUE.equals(player.getAttached(SUPER_4_OPEN)) && ZFighterLineage.of(player) == ZFighterLineage.WARRIOR
			&& ZFighterTraining.formsOpen(player) >= 1 && !ZFighterLineage.ape(player);
	}

	/**
	 * (0.17.90) Why Primal Super, though opened, isn't his to take now (a lang key), or null when it is or was never opened.
	 * Andy couldn't reach it after his heritage was cleared: the lineage follows the Kingdoms heritage, so a cleared one
	 * isn't Warrior-born any more, and C just said there was no higher form.
	 */
	public static @org.jspecify.annotations.Nullable String super4Blocked(Player player) {
		if (!Boolean.TRUE.equals(player.getAttached(SUPER_4_OPEN)) || super4(player)) {
			return null;
		}
		if (ZFighterLineage.of(player) != ZFighterLineage.WARRIOR) {
			return "message.jaymospowers.zfighter_super_4_not_warrior";
		}
		if (ZFighterTraining.formsOpen(player) < 1) {
			return "message.jaymospowers.zfighter_super_4_no_super";
		}
		return ZFighterLineage.ape(player) ? "message.jaymospowers.zfighter_super_4_ape" : null;
	}

	/** A fit has him (either side). */
	public static boolean inFit(Player player) {
		return player.hasAttached(FIT);
	}

	private static void addRage(ServerPlayer player, float by) {
		player.setAttached(RAGE, Mth.clamp(rage(player) + by, 0.0F, 100.0F));
	}

	private static void addPrimal(ServerPlayer player, int by) {
		int was = primal(player);
		int now = Mth.clamp(was + by, 0, PRIMAL_MAX);
		player.setAttached(PRIMAL, now);
		for (int stage = 1; stage <= 4; stage++) {
			int at = stage * 25;
			if (was < at && now >= at) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ape_primal." + stage)
					.withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
			} else if (was >= at && now < at) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ape_tamed." + stage)
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	/** He became the Great Ape. */
	static void began(ServerPlayer player) {
		if (ZFighterLineage.wild(player)) {
			return;
		}
		player.setAttached(RAGE, 0.0F);
		MINUTES.put(player.getUUID(), 0);
		addPrimal(player, PRIMAL_CHANGE);
	}

	/** He's himself again: the rage and any fit go with the ape. */
	static void ended(ServerPlayer player) {
		FITS.remove(player.getUUID());
		MINUTES.remove(player.getUUID());
		player.removeAttached(RAGE);
		player.removeAttached(FIT);
	}

	/** A blow on the ape. */
	static void hurt(ServerPlayer player, float taken) {
		if (!ZFighterLineage.wild(player)) {
			addRage(player, taken * RAGE_PER_DAMAGE * ZFighterGoldenApe.rageFactor(player));
		}
	}

	/** Brought down on his legs. */
	static void crippled(ServerPlayer player) {
		if (!ZFighterLineage.wild(player)) {
			addRage(player, RAGE_CRIPPLED);
		}
	}

	/**
	 * A tick as a Z Fighter's Great Ape (not crippled): the rage builds, and once full a fit takes him. True while a fit has
	 * him (the caller walks him berserk).
	 */
	static boolean tick(ServerPlayer player, ZFighterPower.Live live, ServerLevel level, long now) {
		if (player.isCreative() || player.isSpectator()) {
			return false;
		}
		int minute = MINUTES.merge(player.getUUID(), 1, Integer::sum);
		if (minute >= 1200) {
			MINUTES.put(player.getUUID(), 0);
			addPrimal(player, PRIMAL_MINUTE);
		}
		Fit fit = FITS.get(player.getUUID());
		if (fit == null) {
			if (now % 20 == 0) {
				addRage(player, RAGE_SECOND * (1.0F + primal(player) / PRIMAL_RAGE_SCALE) * ZFighterGoldenApe.rageFactor(player));
			}
			if (rage(player) >= 100.0F && live.apeSize >= 0.5F) {
				startFit(player, live, level, now);
				return true;
			}
			return false;
		}
		fit.struggle = Math.max(0.0F, fit.struggle - fit.fade);
		syncFit(player, fit);
		if (now >= fit.until) {
			endFit(player, live, false);
			return false;
		}
		if ((now - fit.since) % FIT_PULSE_EVERY == 0) {
			com.jaymospowers.core.Shakes.send(player, FIT_PULSE_SHAKE, null);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0F, 0.45F);
		}
		return true;
	}

	private static void startFit(ServerPlayer player, ZFighterPower.Live live, ServerLevel level, long now) {
		int primal = primal(player);
		boolean lost = primal >= PRIMAL_MAX;
		long until = lost ? Long.MAX_VALUE : now + FIT_TICKS + (long)FIT_TICKS_PER_PRIMAL * primal;
		int needed = lost ? NEEDED_LOST : Math.round(NEEDED + NEEDED_PER_PRIMAL * primal);
		Fit fit = new Fit(now, until, needed, FADE + FADE_PER_PRIMAL * primal);
		FITS.put(player.getUUID(), fit);
		live.berserkTarget = -1;
		live.berserkIgnore = -1;
		live.roamAt = 0L;
		syncFit(player, fit);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 5.0F, 0.45F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 5.0F, 0.35F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), ZFighterLineage.APE_STEP_RANGE * 1.5, FIT_SHAKE);
		player.sendOverlayMessage(Component.translatable(lost ? "message.jaymospowers.zfighter_ape_fit.lost"
			: "message.jaymospowers.zfighter_ape_fit." + (1 + player.getRandom().nextInt(4))).withStyle(ChatFormatting.RED));
	}

	private static void syncFit(ServerPlayer player, Fit fit) {
		float share = fit.needed > 0 ? Mth.clamp(fit.struggle / fit.needed, 0.0F, 1.0F) : 0.0F;
		// (in steps, so the sync isn't every tick)
		float stepped = Math.round(share * 50.0F) / 50.0F;
		Float was = player.getAttached(FIT);
		if (was == null || was != stepped) {
			player.setAttached(FIT, stepped);
		}
	}

	private static void struggle(ServerPlayer player) {
		Fit fit = FITS.get(player.getUUID());
		long now = player.level().getGameTime();
		if (fit == null || fit.lastPress == now || now - fit.since < GRACE) {
			return;
		}
		fit.lastPress = now;
		fit.struggle += 1.0F;
		if (fit.struggle >= fit.needed) {
			endFit(player, ZFighterPower.live(player), true);
		}
	}

	/** The fit's over: he tore free ({@code won}), or it ran its course. The berserk walk stops. */
	private static void endFit(ServerPlayer player, ZFighterPower.Live live, boolean won) {
		if (FITS.remove(player.getUUID()) == null) {
			return;
		}
		player.removeAttached(FIT);
		player.removeAttached(ZFighterLineage.BERSERK);
		player.removeAttached(ZFighterLineage.WILD_GOAL);
		player.removeAttached(ZFighterLineage.WILD_FLEE);
		live.berserkTarget = -1;
		live.fleeUntil = -1L;
		player.setAttached(RAGE, won ? RAGE_AFTER_WIN : RAGE_AFTER_LOSS);
		addPrimal(player, won ? -PRIMAL_WON_FIT : PRIMAL_LOST_FIT);
		ServerLevel level = player.level();
		if (won) {
			int tamed = player.getAttachedOrElse(TAMED, 0) + ZFighterGoldenApe.fitWeight(player);
			player.setAttached(TAMED, tamed);
			if (tamed >= SUPER_4_FITS && !Boolean.TRUE.equals(player.getAttached(SUPER_4_OPEN))) {
				openSuper4(player);
				return;
			}
		}
		if (won) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.PLAYERS, 3.0F, 0.5F);
			com.jaymospowers.core.Shakes.send(player, FIT_SHAKE, null);
		}
		player.sendOverlayMessage(Component.translatable(won ? "message.jaymospowers.zfighter_ape_fit.won." + (1 + player.getRandom().nextInt(3))
			: "message.jaymospowers.zfighter_ape_fit.over").withStyle(won ? ChatFormatting.GOLD : ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** The ape and the man make their peace: Super 4 opens. */
	static void openSuper4(ServerPlayer player) {
		player.setAttached(SUPER_4_OPEN, true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 4.0F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, true, false, player.getX(), player.getY() + player.getBbHeight() * 0.5,
			player.getZ(), 120, player.getBbWidth() * 0.5, player.getBbHeight() * 0.4, player.getBbWidth() * 0.5, 0.08);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), ZFighterLineage.APE_STEP_RANGE * 1.5, FIT_SHAKE);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_super_4_open").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
	}

	/** Operators: set primal (and, with {@code fit}, fill the rage so a fit comes now). */
	static void debug(ServerPlayer player, int primal, boolean fit) {
		if (primal >= 0) {
			addPrimal(player, primal - primal(player));
		}
		if (fit && ZFighterLineage.ape(player)) {
			player.setAttached(RAGE, 100.0F);
		}
	}
}
