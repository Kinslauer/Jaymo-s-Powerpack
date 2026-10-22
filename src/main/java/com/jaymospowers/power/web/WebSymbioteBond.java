package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Andy: "symbiote's removal is different, its activation is added to the radial menu, the first few removals at low
 * bond animate like a fight or struggle, requiring the same takeover inputs to regain control, eventually showing just
 * the face, for a short variable duration until mastery increases. Upon first acquisition the suit lays dormant,
 * doesn't allow you use any of its abilities (it will still take over to survive) for up to two days, whispering to
 * you and conforming itself to its new home."
 * <ul>
 * <li><b>The fought removal:</b> taking the suit off from the radial menu (the mask in the middle, the same toggle that
 * tears it off, {@code WebSuitTear}) under {@link #FIGHT_BOND}, the first {@link #FIGHTS} times, it fights you for it:
 * your keys are swallowed and each press is a heave against it, exactly as against a takeover's hold (the same
 * {@link WebSymbiote#STRUGGLE} bar, the same struggle packet). Everyone sees the tear-off driven back and forth by the
 * bar ({@link #FIGHT}): you claw at your chest, the mask sliding off your face and back. Win and only the face comes
 * free ({@link #FACE}), for a random {@code FACE_MIN_*}-{@code FACE_MAX_*} ticks that grow with the bond; then it flows
 * back over your face. Lose ({@link #FIGHT_TICKS} up) and it keeps you covered. Past those, or with the bond at
 * {@link #FIGHT_BOND}, the toggle is the plain tear-off.</li>
 * <li><b>Dormancy:</b> the first time you ever bond ({@link #MET}), it lies dormant for a random share of up to
 * {@link #DORMANT_MAX_TICKS} (two Minecraft days, shorter the better the bond starts), counted while you're online and
 * saved ({@link #DORMANT}): none of its abilities answer, it doesn't hunger, wander, hunt or take the wheel on a whim,
 * but near death it still saves itself. It whispers now and then as it settles in, ripples over you, and wakes.</li>
 * </ul>
 */
public final class WebSymbioteBond {
	// ===== the fought removal ========================================================================================
	/** It fights you for the suit while the bond is under this, at most {@code FIGHTS} times. */
	public static final int FIGHT_BOND = 70, FIGHTS = 3;
	/**
	 * (0.15.69) Andy: "it should have a CHANCE to take up to 3 removals". Each time you take the suit off, it fights you
	 * for it only this often ({@code FIGHT_CHANCE_LOW} at no bond, down to {@code FIGHT_CHANCE_HIGH} at {@link #FIGHT_BOND});
	 * otherwise it comes off at once. Up to {@link #FIGHTS} fights a bond, as before (was: always, the first three).
	 */
	public static final float FIGHT_CHANCE_LOW = 0.6F, FIGHT_CHANCE_HIGH = 0.3F;
	/** Presses to win: {@code FIGHT_PRESSES_MAX} at no bond, down to {@code FIGHT_PRESSES_MIN} at {@code FIGHT_BOND}. */
	public static final int FIGHT_PRESSES_MAX = 30, FIGHT_PRESSES_MIN = 12;
	/**
	 * Andy: "maybe make it slightly easier as bond increases". The bar fades as a takeover's does at no bond, and only
	 * {@code FIGHT_FADE_AT_BOND} as fast at {@code FIGHT_BOND}; its heaves back come {@code FIGHT_SHOVE_AT_BOND} as often
	 * and as hard there (both eased in between).
	 */
	public static final float FIGHT_FADE_AT_BOND = 0.55F, FIGHT_SHOVE_AT_BOND = 0.5F;
	/** How long you have to win before it gives up the fight and keeps you covered. */
	public static final int FIGHT_TICKS = 400;
	/** Past {@code FIGHT_SHOVE_FROM} of the bar it shoves back, {@code FIGHT_SHOVE_CHANCE} a tick, by {@code FIGHT_SHOVE} of the bar. */
	public static final float FIGHT_SHOVE_FROM = 0.4F, FIGHT_SHOVE_CHANCE = 0.025F, FIGHT_SHOVE = 0.18F, FIGHT_SHOVE_SHAKE = 0.6F;
	/** Lines it has as the fight starts, when you win and when you lose. */
	public static final int FIGHT_LINES = 3;
	/**
	 * The face left bare: a random time between the low and high ends, each going from the {@code _LOW} value at no bond
	 * to the {@code _HIGH} value at a whole bond (ticks).
	 */
	public static final int FACE_MIN_LOW = 60, FACE_MAX_LOW = 160, FACE_MIN_HIGH = 400, FACE_MAX_HIGH = 1600;

	// ===== dormancy ==================================================================================================
	/** Up to two Minecraft days; at a whole bond the most is {@code DORMANT_FULL_BOND_SHARE} of it; then {@code DORMANT_MIN_SHARE}-1 of that. */
	public static final int DORMANT_MAX_TICKS = 48000;
	public static final float DORMANT_FULL_BOND_SHARE = 0.25F, DORMANT_MIN_SHARE = 0.35F;
	/** Counted down (and synced) in steps this big. */
	public static final int DORMANT_STEP = 20;
	/** It whispers every {@code DORMANT_WHISPER_MIN}-{@code DORMANT_WHISPER_MAX} ticks ({@code DORMANT_LINES} lines), the first {@code DORMANT_FIRST} after the bond. */
	public static final int DORMANT_WHISPER_MIN = 900, DORMANT_WHISPER_MAX = 2100, DORMANT_FIRST = 200, DORMANT_LINES = 10, DORMANT_REFUSE_LINES = 3;
	/** It ripples over you as it conforms, every {@code DORMANT_RIPPLE_TICKS} (a slow black spiral up the body). */
	public static final int DORMANT_RIPPLE_TICKS = 100, DORMANT_RIPPLE_STEPS = 18;

	/** Ticks of dormancy left (saved; synced to the owner, for the radial menu). */
	public static final AttachmentType<Integer> DORMANT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_dormant"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
			AttachmentSyncPredicate.targetOnly()));
	/** It has bonded with you before (saved, kept through death): dormancy happens once ever. */
	public static final AttachmentType<Boolean> MET = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_met"),
		builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath());
	/** Removals it has fought you over with this bond (saved; a new bond starts over). */
	public static final AttachmentType<Integer> FOUGHT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_fought"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT));
	/** It's fighting you for the suit right now (synced to everyone: the tear-off follows {@link WebSymbiote#STRUGGLE}). */
	public static final AttachmentType<Boolean> FIGHT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_fight"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** Only the face is bare, until this game time (synced to everyone, who see the mask gone). */
	public static final AttachmentType<Long> FACE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_face"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** Server-only bookkeeping (not saved, not synced). */
	private static final AttachmentType<Live> LIVE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_bond_live"));

	private static final class Live {
		/** The fight: presses (fading), how many win it, when it gives up, the last tick a press counted. */
		boolean fighting;
		/** (0.13.23) The fight is over the mask alone ({@link #resistMask}), not the whole suit. */
		boolean forMask;
		float struggle;
		int needed;
		long fightUntil;
		long struggleAt = -1L;
		/** Dormancy: when it next whispers (0: not yet set). */
		long whisperAt;
		int lastLine = -1;
	}

	private WebSymbioteBond() {
	}

	private static Live live(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null) {
			live = new Live();
			player.setAttached(LIVE, live);
		}
		return live;
	}

	// ---- shared queries (both sides) --------------------------------------------------------------------------------

	/** Still settling in: none of its abilities answer (never in creative). Synced to the owner only. */
	public static boolean dormant(Player player) {
		Integer left = player.getAttached(DORMANT);
		return left != null && left > 0 && !player.isCreative();
	}

	/** Ticks of dormancy left (0: none). */
	public static int dormantTicks(Player player) {
		Integer left = player.getAttached(DORMANT);
		return left == null ? 0 : Math.max(0, left);
	}

	/** Only the face is bare right now (either side). */
	public static boolean faceBare(Player player) {
		Long until = player.getAttached(FACE);
		return until != null && player.level().getGameTime() < until;
	}

	/** How far the fight's bar falls back each tick at this bond (both sides: the owner's own bar runs it too). */
	public static float fightFade(int bond) {
		return WebSymbiote.TAKEOVER_STRUGGLE_FADE * Mth.lerp(ease(bond), 1.0F, FIGHT_FADE_AT_BOND);
	}

	/** The bond's share of {@link #FIGHT_BOND} (0-1), eased. */
	private static float ease(int bond) {
		float b = Mth.clamp(bond / (float)FIGHT_BOND, 0.0F, 1.0F);
		return b * b * (3.0F - 2.0F * b);
	}

	/** It's fighting this player for the suit (either side). */
	public static boolean fighting(Player player) {
		return Boolean.TRUE.equals(player.getAttached(FIGHT));
	}

	// ---- bonding ----------------------------------------------------------------------------------------------------

	/** A new bond took (from {@link WebSymbiote#bond}): the first ever lies dormant; every new one fights its first removals. */
	static void onBond(ServerPlayer player, int bond) {
		clear(player);
		player.removeAttached(FOUGHT);
		if (!player.isCreative() && !com.jaymospowers.core.Powers.has(player, WebPower.INSTANCE)) {
			// (0.15.52) in someone who isn't a web slinger it acclimates every time, and quicker: WebSymbioteInfection
			player.setAttached(DORMANT, WebSymbioteInfection.ACCLIMATE_TICKS);
			live(player).whisperAt = player.level().getGameTime() + WebSymbiote.BOND_TICKS + DORMANT_FIRST;
			return;
		}
		if (Boolean.TRUE.equals(player.getAttached(MET)) || player.isCreative()) {
			return;
		}
		player.setAttached(MET, true);
		float b = Mth.clamp(bond / (float)WebSymbiote.BOND_MAX, 0.0F, 1.0F);
		float cap = Mth.lerp(b, DORMANT_MAX_TICKS, DORMANT_MAX_TICKS * DORMANT_FULL_BOND_SHARE);
		int ticks = Math.max(DORMANT_STEP, Math.round(cap * Mth.lerp(player.getRandom().nextFloat(), DORMANT_MIN_SHARE, 1.0F)));
		player.setAttached(DORMANT, ticks);
		live(player).whisperAt = player.level().getGameTime() + WebSymbiote.BOND_TICKS + DORMANT_FIRST;
	}

	/** The bond is gone (rejected, died, power lost): no fight, no bare face, no dormancy left. */
	static void clear(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live != null && live.fighting) {
			endFight(player, live);
		}
		player.removeAttached(FIGHT);
		player.removeAttached(FACE);
		player.removeAttached(DORMANT);
		player.removeAttached(WebSymbioteStrains.ASLEEP);
	}

	// ---- every tick (bonded, alive; from WebSymbiote.tick) ----------------------------------------------------------

	static void tick(ServerPlayer player, long now) {
		Live live = live(player);
		if (live.fighting) {
			tickFight(player, live, now);
		}
		// the face is covered again: the time's up, or something else has the suit (a takeover, the big body, the roar,
		// the suit gone)
		Long face = player.getAttached(FACE);
		if (face != null) {
			boolean taken = WebSymbiote.driving(player) || WebSymbiote.bulked(player) || WebSymbiote.roaring(player) || !WebSymbiote.suitShown(player);
			if (taken || now >= face) {
				player.removeAttached(FACE);
				if (!taken) {
					ServerLevel level = player.level();
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 0.6F);
					level.sendParticles(WebSymbioteStrains.dust(player, 1.0F), player.getX(), player.getEyeY(), player.getZ(), 10, 0.2, 0.2, 0.2, 0.0);
					WebSymbiote.whisper(player, "face_back." + (1 + player.getRandom().nextInt(FIGHT_LINES)));
				}
			}
		}
		Integer left = player.getAttached(DORMANT);
		if (left != null) {
			tickDormant(player, live, now, left);
		}
	}

	// ---- the fought removal -----------------------------------------------------------------------------------------

	/**
	 * The radial toggle is taking the suit off: under {@link #FIGHT_BOND}, its first {@link #FIGHTS} times, it fights you
	 * for it instead (returns true: the suit stays on for now). Otherwise false: the plain tear-off goes ahead.
	 */
	static boolean resistRemoval(ServerPlayer player) {
		return resistRemoval(player, false);
	}

	private static boolean resistRemoval(ServerPlayer player, boolean mask) {
		Live live = live(player);
		if (live.fighting) {
			// already at it
			return true;
		}
		int bond = WebSymbiote.state(player).bond();
		int fought = player.getAttachedOrElse(FOUGHT, 0);
		if (player.isCreative() || bond >= FIGHT_BOND || !mask && fought >= FIGHTS || WebSymbiote.driving(player) || WebSymbiote.roaring(player)
			|| WebSymbiote.bulked(player)) {
			return false;
		}
		if (!mask && player.getRandom().nextFloat() >= Mth.lerp(bond / (float)FIGHT_BOND, FIGHT_CHANCE_LOW, FIGHT_CHANCE_HIGH)) {
			// (0.15.69) not this time: it lets you
			return false;
		}
		long now = player.level().getGameTime();
		live.fighting = true;
		live.forMask = mask;
		live.struggle = 0.0F;
		live.struggleAt = -1L;
		live.needed = Math.round(Mth.lerp(bond / (float)FIGHT_BOND, FIGHT_PRESSES_MAX, FIGHT_PRESSES_MIN));
		live.fightUntil = now + FIGHT_TICKS;
		player.removeAttached(FACE);
		player.setAttached(FIGHT, true);
		player.setAttached(WebSymbiote.STRUGGLE, 0.0F);
		ServerPlayNetworking.send(player, new WebNet.Peel(live.needed, FIGHT_TICKS));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.5F);
		com.jaymospowers.core.Shakes.send(player, WebSymbiote.SURGE_BACK_SHAKE, null);
		WebSymbiote.whisper(player, "fight." + (1 + player.getRandom().nextInt(FIGHT_LINES)));
		return true;
	}

	/**
	 * (0.13.23) Andy: "it should still be a struggle at low bond like the rest of the suit". The Mask slot is taking the
	 * suit's head off: under {@link #FIGHT_BOND} it fights you for it every time (returns true: the mask stays on for
	 * now); win and the mask comes off ({@link WebSymbiote#UNMASKED}). Otherwise false: it comes off as asked.
	 */
	static boolean resistMask(ServerPlayer player) {
		return resistRemoval(player, true);
	}

	/** A key pressed against it (the same packet as against a takeover). Returns true if a fight took it. */
	static boolean struggle(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null || !live.fighting || WebSymbiote.driving(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		if (now == live.struggleAt) {
			return true;
		}
		live.struggleAt = now;
		live.struggle += 1.0F;
		if (live.struggle >= live.needed) {
			win(player, live, now);
			return true;
		}
		sync(player, live);
		return true;
	}

	private static void tickFight(ServerPlayer player, Live live, long now) {
		if (WebSymbiote.driving(player) || WebSymbiote.roaring(player) || WebSymbiote.bulked(player) || !WebSymbiote.suitShown(player)) {
			// something else has the suit now
			endFight(player, live);
			return;
		}
		if (now >= live.fightUntil) {
			endFight(player, live);
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.1F, 0.5F);
			WebSymbiote.whisper(player, "fight_lost." + (1 + player.getRandom().nextInt(FIGHT_LINES)));
			return;
		}
		int bond = WebSymbiote.state(player).bond();
		live.struggle = Math.max(0.0F, live.struggle - fightFade(bond));
		float shove = Mth.lerp(ease(bond), 1.0F, FIGHT_SHOVE_AT_BOND);
		if (live.struggle >= live.needed * FIGHT_SHOVE_FROM && player.getRandom().nextFloat() < FIGHT_SHOVE_CHANCE * shove) {
			// it heaves back over you: the mask slides back over your face
			live.struggle = Math.max(0.0F, live.struggle - live.needed * FIGHT_SHOVE * shove);
			com.jaymospowers.core.Shakes.send(player, FIGHT_SHOVE_SHAKE, null);
			WebSymbiote.dread(player, 20, false, true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
		}
		sync(player, live);
	}

	/** Tells everyone how far you've torn at it ({@link WebSymbiote#STRUGGLE}), in the takeover's steps. */
	private static void sync(ServerPlayer player, Live live) {
		float share = live.needed > 0 ? Mth.clamp(live.struggle / live.needed, 0.0F, 1.0F) : 0.0F;
		share = Math.round(share / WebSymbiote.STRUGGLE_SYNC_STEP) * WebSymbiote.STRUGGLE_SYNC_STEP;
		Float was = player.getAttached(WebSymbiote.STRUGGLE);
		if (was == null || Math.abs(was - share) > 1.0E-4F) {
			player.setAttached(WebSymbiote.STRUGGLE, share);
		}
	}

	/** The fight's over (either way): your keys are your own again. */
	private static void endFight(ServerPlayer player, Live live) {
		live.fighting = false;
		live.forMask = false;
		live.struggle = 0.0F;
		player.removeAttached(FIGHT);
		if (!WebSymbiote.driving(player)) {
			// (a takeover keeps its own bar)
			player.removeAttached(WebSymbiote.STRUGGLE);
		}
		ServerPlayNetworking.send(player, new WebNet.Peel(0, 0));
	}

	/** You tore its face off yours: only the face, and only for a while (longer the better the bond). */
	private static void win(ServerPlayer player, Live live, long now) {
		boolean mask = live.forMask;
		endFight(player, live);
		if (mask) {
			// (0.13.23) the mask fight: it comes off for good, until you put it back
			player.setAttached(WebSymbiote.UNMASKED, true);
		} else {
			player.setAttached(FOUGHT, player.getAttachedOrElse(FOUGHT, 0) + 1);
			bareFace(player, now);
		}
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 0.6F, 1.5F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, player.getX(), player.getEyeY(), player.getZ(), 14, 0.25, 0.2, 0.25, 0.04);
		com.jaymospowers.core.Shakes.send(player, 1.0F, null);
		WebSymbiote.whisper(player, "fight_won." + (1 + player.getRandom().nextInt(FIGHT_LINES)));
	}

	/** Only the face is bare, for a random while (see {@link #FACE_MIN_LOW}). */
	private static void bareFace(ServerPlayer player, long now) {
		float b = Mth.clamp(WebSymbiote.state(player).bond() / (float)WebSymbiote.BOND_MAX, 0.0F, 1.0F);
		int min = Math.round(Mth.lerp(b, FACE_MIN_LOW, FACE_MIN_HIGH));
		int max = Math.round(Mth.lerp(b, FACE_MAX_LOW, FACE_MAX_HIGH));
		player.setAttached(FACE, now + min + player.getRandom().nextInt(Math.max(1, max - min + 1)));
	}

	// ---- dormancy ---------------------------------------------------------------------------------------------------

	/** A radial pick while dormant: it doesn't answer, and tells you why. */
	static void refuse(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 0.4F);
		// (the white one asleep from healing has its own lines: WebSymbioteStrains.sleep)
		boolean asleep = WebSymbioteStrains.asleep(player);
		WebSymbiote.whisper(player, (asleep ? "white.asleep_refuse." : "dormant_refuse.") + (1 + player.getRandom().nextInt(DORMANT_REFUSE_LINES)));
	}

	private static void tickDormant(ServerPlayer player, Live live, long now, int left) {
		ServerLevel level = player.level();
		if (player.tickCount % DORMANT_STEP == 0) {
			left -= DORMANT_STEP;
			if (left <= 0) {
				// awake: it has made itself at home (or the white one has slept off its healing)
				boolean asleep = WebSymbioteStrains.asleep(player);
				player.removeAttached(DORMANT);
				player.removeAttached(WebSymbioteStrains.ASLEEP);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.7F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.sendParticles(WebSymbioteStrains.dust(player, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.35, 0.8, 0.35, 0.0);
				WebSymbiote.whisper(player, asleep ? "white.wake" : "dormant_wake");
				return;
			}
			player.setAttached(DORMANT, left);
		}
		// it conforms to you: a slow black spiral rippling up the body now and then (everyone sees it)
		int phase = (int)(now % DORMANT_RIPPLE_TICKS);
		if (phase < DORMANT_RIPPLE_STEPS && !player.isInvisible()) {
			float t = phase / (float)DORMANT_RIPPLE_STEPS;
			double a = t * Math.PI * 4.0;
			double r = 0.38;
			level.sendParticles(WebSymbioteStrains.dust(player, 0.7F), player.getX() + Math.cos(a) * r, player.getY() + t * player.getBbHeight(),
				player.getZ() + Math.sin(a) * r, 1, 0.02, 0.03, 0.02, 0.0);
			if (phase == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.25F, 0.5F);
			}
		}
		if (live.whisperAt == 0L) {
			live.whisperAt = now + DORMANT_WHISPER_MIN + player.getRandom().nextInt(DORMANT_WHISPER_MAX - DORMANT_WHISPER_MIN + 1);
		} else if (now >= live.whisperAt) {
			live.whisperAt = now + DORMANT_WHISPER_MIN + player.getRandom().nextInt(DORMANT_WHISPER_MAX - DORMANT_WHISPER_MIN + 1);
			// never the same line twice running
			int line = player.getRandom().nextInt(DORMANT_LINES - 1);
			live.lastLine = line >= live.lastLine && live.lastLine >= 0 ? line + 1 : line;
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.6, 0.3, 0.01);
			WebSymbiote.whisper(player, WebSymbioteStrains.asleep(player) ? "white.asleep." + (1 + live.lastLine % WebSymbiote.SAY_LINES)
				: "dormant." + (1 + live.lastLine));
		}
	}

	/** For the debug command: dormant for {@code ticks} from now (0 ends it). */
	static void setDormant(ServerPlayer player, int ticks) {
		if (ticks <= 0) {
			player.removeAttached(DORMANT);
		} else {
			player.setAttached(DORMANT, ticks);
			live(player).whisperAt = player.level().getGameTime() + DORMANT_FIRST;
		}
	}
}
