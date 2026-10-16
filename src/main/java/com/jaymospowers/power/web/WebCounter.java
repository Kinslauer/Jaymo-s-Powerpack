package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spider-Sense counters. For {@link #WINDOW} ticks after a sense dodge ({@link WebAscension}), right click strikes
 * back at whatever you dodged (or, if that's gone, whatever's under the crosshair), with one of nine moves picked at
 * random (never the same twice running). In the symbiote's suit the nine are its own: tendrils that impale, whip,
 * hurl, constrict and flurry, and the head swelling into the maw to snap, maul, roar and bite. The maw only bites;
 * it never feeds on anything living.
 *
 * <p>Each move plays out over a few ticks ({@link #ticks}): every move has its own whole-body animation, drawn by the
 * client from {@link #ACTIVE} ({@code WebCounterPose}); web moves leave cobweb trails, and maw moves are drawn from
 * {@link #ACTIVE} ({@link #maw}). The symbiote's moves are the PS5 tendril slam: a thick bundle of glossy tendrils
 * whips out of the arm ({@link #bundle}, drawn by {@code WebTendrilBundle}), wraps it, hauls it through a big arc
 * ({@link WebHaul}) and slams it into the ground in a burst of black mass and dust, then recoils into the arm.
 *
 * <p>Every counter spends one Combat Roll charge ({@link WebRolls}); with none left, a dodge opens no counter.
 */
public final class WebCounter {
	/** Ticks after a dodge that right click counters. */
	public static final int WINDOW = 30;
	/** The dodged attacker is countered if it's this close (else whatever's under the crosshair, this far). */
	public static final double REACH = 7.0, AIM_REACH = 10.0;

	// ---- the web slinger's nine ----
	/** A corkscrew into a spinning heel kick. */
	public static final int HEEL_KICK = 0;
	/** A web yanks it in, onto an uppercut that launches it. */
	public static final int YANK_UPPERCUT = 1;
	/** Two webs haul it up overhead and slam it down. */
	public static final int WEB_SLAM = 2;
	/** Web to the eyes (blinded), then three quick jabs and a cross. */
	public static final int BLIND_JABS = 3;
	/** A front flip over it, kicking down on the way. */
	public static final int FLIP_KICK = 4;
	/** It's spun up in a cocoon and hurled away. */
	public static final int COCOON_THROW = 5;
	/** A zip line to it and a flying kick at the end of it. */
	public static final int ZIP_KICK = 6;
	/** A low roll, its legs webbed and swept out from under it. */
	public static final int TRIP_SWEEP = 7;
	/** A web ball pins its feet; a haymaker lands while it's stuck. */
	public static final int PINNED_HAYMAKER = 8;
	public static final int WEB_COUNTERS = 9;

	// ---- the symbiote's nine ----
	/** A tendril rears and drives down through it, pinning it. */
	public static final int IMPALE = 9;
	/** A tendril whips across and flings it aside. */
	public static final int WHIPLASH = 10;
	/** A tendril coils it up and hurls it over your shoulder. */
	public static final int HURL = 11;
	/** Tendrils wrap it and squeeze, three times. */
	public static final int CONSTRICT = 12;
	/** The head lunges into the maw and snaps once, hard. */
	public static final int JAW_SNAP = 13;
	/** The maw bites, worries it, bites again, and tosses it. */
	public static final int MAUL = 14;
	/** The maw roars in its face (everything near reels back), then bites. */
	public static final int MAW_ROAR = 15;
	/** Three tendril stabs, one after another. */
	public static final int SPEAR_FLURRY = 16;
	/** A tendril drags it to your face, into the maw's jaws. */
	public static final int DRAG_TO_JAWS = 17;
	public static final int COUNTERS = 18;

	/** Damage (the web moves; then the symbiote's). */
	static final float HEEL_DAMAGE = 8.0F, UPPERCUT_DAMAGE = 7.0F, SLAM_DAMAGE = 9.0F, JAB_DAMAGE = 3.0F, CROSS_DAMAGE = 4.0F,
		FLIP_DAMAGE = 8.0F, COCOON_DAMAGE = 6.0F, ZIP_DAMAGE = 9.0F, SWEEP_DAMAGE = 5.0F, HAYMAKER_DAMAGE = 11.0F;
	static final float IMPALE_DAMAGE = 11.0F, WHIP_DAMAGE = 8.0F, HURL_DAMAGE = 6.0F, SQUEEZE_DAMAGE = 4.5F, SNAP_DAMAGE = 12.0F,
		MAUL_DAMAGE = 5.0F, ROAR_BITE_DAMAGE = 8.0F, ROAR_DAMAGE = 3.0F, STAB_DAMAGE = 4.5F, DRAG_BITE_DAMAGE = 13.0F;
	/** Maw Roar: how far it reaches and how hard it shoves. */
	static final double ROAR_RADIUS = 5.0, ROAR_PUSH = 1.1;
	/** Zip Kick: how fast you fly at it and how close counts as a hit. */
	static final double ZIP_SPEED = 1.5, ZIP_HIT = 2.2;
	/** Spear Flurry: its stabs land at these ticks. */
	public static final int[] FLURRY_STABS = {9, 16, 23};
	/** Constrict: it squeezes at these ticks (the last squeeze is the slam, at its bundle's {@code haulEnd}). */
	static final int[] CONSTRICT_SQUEEZES = {9, 17};

	// ---- the tendril slam (the symbiote's moves) ----
	/**
	 * A symbiote counter's tendril bundle, in ticks from the start: it has reached the target by {@code reached}, wrapped
	 * it by {@code wrapped}, hauls it until {@code haulEnd} (the slam, for those that slam), and lets go at
	 * {@code release}, recoiling into the arm over {@link #RECOIL_TICKS}. {@code spear}: braided into a point that stabs
	 * instead of wrapping.
	 */
	public record Bundle(int reached, int wrapped, int haulEnd, int release, boolean spear) {
	}

	/** How long the bundle takes to recoil back into the arm after letting go (ticks). */
	public static final int RECOIL_TICKS = 6;
	/** Impale: lifted this high (blocks) and driven back down where it stood. */
	static final double IMPALE_LIFT = 1.9;
	/** Hurl: over your head (this high over your eyes) and down onto the ground this far behind you. */
	static final double HURL_APEX = 1.6, HURL_BEHIND = 2.4;
	/** Whiplash: swung round in a wide arc (this far out to the side at its height, this high) and slammed down at your side. */
	static final double WHIP_SWING = 3.0, WHIP_APEX = 1.6, WHIP_LAND = 2.6;
	/** Constrict: held up this high, this far ahead, while it squeezes, then slammed down. */
	static final double CONSTRICT_LIFT = 1.0, CONSTRICT_AHEAD = 2.0;
	/** Maw moves: the bundle reels it in to this far in front of your eyes. */
	static final double JAWS_AHEAD = 1.2;
	/** A slam's screen shake (yours; whoever's slammed gets more), how far others feel it, and the burst's size. */
	static final float SLAM_SHAKE = 1.0F, SLAMMED_SHAKE = 1.6F, SLAM_BURST = 1.0F;
	static final double SLAM_FELT = 12.0;
	/** Slammed: stunned (Slowness) for this long. */
	static final int SLAM_STUN_TICKS = 30;

	/** A symbiote counter's bundle, or null (a web move). */
	public static @Nullable Bundle bundle(int kind) {
		return switch (kind) {
			case IMPALE -> new Bundle(5, 7, 12, 14, false);
			case WHIPLASH -> new Bundle(5, 7, 12, 14, false);
			case HURL -> new Bundle(5, 8, 17, 21, false);
			case CONSTRICT -> new Bundle(5, 8, 27, 28, false);
			case SPEAR_FLURRY -> new Bundle(6, 6, 6, 24, true);
			case DRAG_TO_JAWS -> new Bundle(5, 8, 17, 18, false);
			case JAW_SNAP -> new Bundle(3, 5, 8, 10, false);
			case MAUL -> new Bundle(3, 5, 22, 22, false);
			case MAW_ROAR -> new Bundle(11, 13, 16, 17, false);
			default -> null;
		};
	}

	/** A counter under way (synced to everyone, who see the maw): which, at whom, and when it began (game time). */
	public record Active(int kind, int target, long start) {
		public static final StreamCodec<ByteBuf, Active> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Active::kind,
			ByteBufCodecs.VAR_INT, Active::target,
			ByteBufCodecs.VAR_LONG, Active::start,
			Active::new);
	}

	public static final AttachmentType<Active> ACTIVE = AttachmentRegistry.create(JaymosPowers.id("web_counter"),
		builder -> builder.syncWith(Active.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/**
	 * Until when (game time) right click counters: set by a dodge. Synced to everyone: the owner's client listens for
	 * the click, and every client flashes the Spider-Sense bolts over their head meanwhile.
	 */
	public static final AttachmentType<Long> READY = AttachmentRegistry.create(JaymosPowers.id("web_counter_ready"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	private static final AttachmentType<Live> LIVE = AttachmentRegistry.create(JaymosPowers.id("web_counter_live"));

	private static final class Live {
		/** What was just dodged. */
		int attacker = -1;
		int lastKind = -1;
		/** The haul under way: from where, through what high point, to where (feet), and whether it's being hauled at all. */
		Vec3 haulFrom = Vec3.ZERO, haulApex = Vec3.ZERO, haulTo = Vec3.ZERO;
		boolean hauling;
	}

	/** Client → server: right click inside the window. */
	public record Strike() implements CustomPacketPayload {
		public static final Type<Strike> TYPE = new Type<>(JaymosPowers.id("web_counter_strike"));
		public static final StreamCodec<ByteBuf, Strike> CODEC = StreamCodec.unit(new Strike());

		@Override
		public Type<Strike> type() {
			return TYPE;
		}
	}

	private WebCounter() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Strike.TYPE, Strike.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Strike.TYPE, (payload, ctx) -> strike(ctx.player()));
		WebRolls.register();
	}

	/** How long each counter lasts (ticks). */
	public static int ticks(int kind) {
		return switch (kind) {
			case HEEL_KICK, TRIP_SWEEP -> 10;
			case YANK_UPPERCUT, FLIP_KICK, ZIP_KICK -> 14;
			case WEB_SLAM, BLIND_JABS, COCOON_THROW, PINNED_HAYMAKER -> 16;
			case JAW_SNAP -> 18;
			case IMPALE, WHIPLASH -> 20;
			case DRAG_TO_JAWS, MAW_ROAR -> 24;
			case MAUL, HURL -> 28;
			case SPEAR_FLURRY -> 30;
			case CONSTRICT -> 34;
			default -> 10;
		};
	}

	/** The counter is the maw's (drawn on the head). */
	public static boolean mawKind(int kind) {
		return kind == JAW_SNAP || kind == MAUL || kind == MAW_ROAR || kind == DRAG_TO_JAWS;
	}

	/** The ticks a maw counter's jaws snap shut on. */
	private static int[] bites(int kind) {
		return switch (kind) {
			case JAW_SNAP -> new int[] {8};
			case MAUL -> new int[] {8, 15, 22};
			case MAW_ROAR -> new int[] {16};
			case DRAG_TO_JAWS -> new int[] {17};
			default -> new int[0];
		};
	}

	/** How wide a maw counter's jaws are open {@code t} ticks in (0 shut, 1 widest, past 1 a roar's gape). */
	public static float maw(int kind, float t) {
		float open = 0.0F;
		for (int bite : bites(kind)) {
			float before = bite - t;
			if (before >= 0.0F && before < 6.0F) {
				float g = 1.0F - before / 6.0F;
				open = Math.max(open, g * g * (3.0F - 2.0F * g));
			}
		}
		if (kind == MAW_ROAR && t >= 2.0F && t < 10.0F) {
			// thrown open as wide as it goes for the roar
			open = Math.max(open, 1.2F * Mth.clamp(Math.min((t - 2.0F) / 2.0F, (10.0F - t) / 2.0F), 0.0F, 1.0F));
		}
		if (kind == MAUL && t > 8.0F && t < 24.0F) {
			// worrying it between bites
			open = Math.max(open, 0.15F * Math.abs(Mth.sin(t * 0.9F)));
		}
		return open;
	}

	static void clear(ServerPlayer player) {
		player.removeAttached(ACTIVE);
		player.removeAttached(READY);
		player.removeAttached(LIVE);
	}

	/** A sense dodge just happened: right click counters for a moment (if there's a Combat Roll charge to spend on it). */
	static void opened(ServerPlayer player, Entity attacker) {
		if (!WebRolls.has(player)) {
			// out of roll charges: the dodge still happens, but no counter
			return;
		}
		Live live = live(player);
		live.attacker = attacker.getId();
		player.setAttached(READY, player.level().getGameTime() + WINDOW);
	}

	private static Live live(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null) {
			live = new Live();
			player.setAttached(LIVE, live);
		}
		return live;
	}

	/** Right click: the counter, if the window's open and there's something to hit. */
	private static void strike(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long ready = player.getAttached(READY);
		if (ready == null || now > ready || player.getAttached(ACTIVE) != null || !player.isAlive() || WebPower.ragdolled(player)) {
			return;
		}
		if (!WebRolls.has(player)) {
			// the roll charges went on rolling since the dodge: no counter
			player.removeAttached(READY);
			return;
		}
		Live live = live(player);
		LivingEntity target = level.getEntity(live.attacker) instanceof LivingEntity attacker && attacker.isAlive()
			&& attacker.distanceTo(player) <= REACH ? attacker : null;
		if (target == null && WebPower.entityInSight(player, AIM_REACH) instanceof LivingEntity seen && seen.isAlive()) {
			target = seen;
		}
		if (target == null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0F, 1.2F);
			return;
		}
		boolean suit = WebSymbiote.suitShown(player);
		int base = suit ? WEB_COUNTERS : 0;
		int kind;
		do {
			kind = base + player.getRandom().nextInt(WEB_COUNTERS);
		} while (kind == live.lastKind);
		live.lastKind = kind;
		// every counter spends a Combat Roll charge
		WebRolls.spend(player);
		player.removeAttached(READY);
		player.setAttached(ACTIVE, new Active(kind, target.getId(), now));
		begin(player, level, kind, target);
	}

	/** Every tick, for a web slinger: the counter under way plays out. */
	static void tick(ServerPlayer player) {
		Active active = player.getAttached(ACTIVE);
		if (active == null) {
			return;
		}
		ServerLevel level = player.level();
		int t = (int)(level.getGameTime() - active.start());
		Entity found = level.getEntity(active.target());
		if (t >= ticks(active.kind()) || !player.isAlive()) {
			player.removeAttached(ACTIVE);
			return;
		}
		if (!(found instanceof LivingEntity target) || !target.isAlive()) {
			// it's dead (or gone): the web moves stop there; the symbiote's bundle still recoils, the body still follows through
			if (active.kind() < WEB_COUNTERS) {
				player.removeAttached(ACTIVE);
			}
			return;
		}
		if (t > 0) {
			step(player, level, active.kind(), t, target);
		}
	}

	// ---- the moves -----------------------------------------------------------------------------------------------

	/** The first tick of each. */
	private static void begin(ServerPlayer player, ServerLevel level, int kind, LivingEntity target) {
		Vec3 away = away(player, target);
		switch (kind) {
			case HEEL_KICK -> {
				nudge(player, away.scale(0.2).add(0.0, 0.3, 0.0));
				sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.4F);
			}
			case YANK_UPPERCUT -> {
				webTrail(level, player, target);
				push(target, away.scale(-0.85).add(0.0, 0.25, 0.0));
				sound(level, player, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 1.4F);
			}
			case WEB_SLAM -> {
				webTrail(level, player, target);
				webTrail(level, player, target);
				push(target, new Vec3(0.0, 0.95, 0.0).add(away.scale(-0.2)));
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				sound(level, player, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 1.0F);
			}
			case BLIND_JABS -> {
				webTrail(level, player, target);
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0), player);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), player);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getEyeY(), target.getZ(), 16, 0.2, 0.15, 0.2, 0.02);
				sound(level, target, SoundEvents.SLIME_SQUISH_SMALL, 1.0F, 1.2F);
			}
			case FLIP_KICK -> {
				nudge(player, away.scale(0.45).add(0.0, 0.55, 0.0));
				sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.6F);
			}
			case COCOON_THROW -> {
				webTrail(level, player, target);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), player);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 40,
					target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.03);
				sound(level, target, SoundEvents.WOOL_PLACE, 1.0F, 0.7F);
			}
			case ZIP_KICK -> {
				webTrail(level, player, target);
				zip(player, target);
				sound(level, player, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7F, 1.6F);
			}
			case TRIP_SWEEP -> {
				nudge(player, away.scale(0.35));
				level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + 0.2, target.getZ(), 16, 0.3, 0.1, 0.3, 0.02);
				sound(level, player, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 1.6F);
			}
			case PINNED_HAYMAKER -> {
				webTrail(level, player, target);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 6), player);
				push(target, new Vec3(0.0, -0.2, 0.0));
				level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + 0.1, target.getZ(), 24, 0.35, 0.05, 0.35, 0.02);
				sound(level, target, SoundEvents.SLIME_SQUISH, 1.0F, 0.8F);
			}
			case IMPALE, HURL, DRAG_TO_JAWS, WHIPLASH, CONSTRICT, SPEAR_FLURRY -> lash(player);
			case JAW_SNAP -> {
				lash(player);
				nudge(player, away.scale(0.45).add(0.0, 0.1, 0.0));
				sound(level, player, SoundEvents.WARDEN_AGITATED, 0.8F, 1.6F);
			}
			case MAUL -> {
				lash(player);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 28, 5), player);
				nudge(player, away.scale(0.35));
				sound(level, player, SoundEvents.WARDEN_AGITATED, 0.9F, 1.3F);
			}
			case MAW_ROAR -> sound(level, player, SoundEvents.WARDEN_TENDRIL_CLICKS, 1.0F, 0.6F);
			default -> {
			}
		}
	}

	/** Each later tick. */
	private static void step(ServerPlayer player, ServerLevel level, int kind, int t, LivingEntity target) {
		Vec3 away = away(player, target);
		switch (kind) {
			case HEEL_KICK -> {
				if (t == 4) {
					hit(player, target, HEEL_DAMAGE, away.scale(1.4).add(0.0, 0.35, 0.0), ParticleTypes.SWEEP_ATTACK, SoundEvents.PLAYER_ATTACK_STRONG);
				}
			}
			case YANK_UPPERCUT -> {
				if (t == 6) {
					hit(player, target, UPPERCUT_DAMAGE, away.scale(0.3).add(0.0, 1.05, 0.0), ParticleTypes.CRIT, SoundEvents.PLAYER_ATTACK_KNOCKBACK);
				}
			}
			case WEB_SLAM -> {
				if (t == 9) {
					player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
					push(target, new Vec3(0.0, -2.0, 0.0));
				} else if (t == 12) {
					hit(player, target, SLAM_DAMAGE, new Vec3(0.0, 0.25, 0.0), ParticleTypes.EXPLOSION, SoundEvents.MACE_SMASH_GROUND);
					com.jaymospowers.core.Shakes.heavy(player, target.position(), 10.0, 0.8F);
				}
			}
			case BLIND_JABS -> {
				if (t == 3 || t == 6 || t == 9) {
					player.swing(t == 6 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					hit(player, target, JAB_DAMAGE, away.scale(0.15), ParticleTypes.CRIT, SoundEvents.PLAYER_ATTACK_WEAK);
				} else if (t == 12) {
					hit(player, target, CROSS_DAMAGE, away.scale(0.9).add(0.0, 0.25, 0.0), ParticleTypes.CRIT, SoundEvents.PLAYER_ATTACK_STRONG);
				}
			}
			case FLIP_KICK -> {
				if (t == 7) {
					hit(player, target, FLIP_DAMAGE, away.scale(0.9).add(0.0, target.onGround() ? 0.2 : -0.6, 0.0), ParticleTypes.CRIT,
						SoundEvents.PLAYER_ATTACK_STRONG);
				}
			}
			case COCOON_THROW -> {
				if (t < 8) {
					level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 4,
						target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.0);
				} else if (t == 8) {
					player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					hit(player, target, COCOON_DAMAGE, player.getLookAngle().horizontal().normalize().scale(2.0).add(0.0, 0.6, 0.0),
						ParticleTypes.ITEM_COBWEB, SoundEvents.PLAYER_ATTACK_KNOCKBACK);
				}
			}
			case ZIP_KICK -> {
				boolean close = player.distanceTo(target) - target.getBbWidth() * 0.5 <= ZIP_HIT;
				if (t < 10 && !close) {
					zip(player, target);
				} else if (close) {
					hit(player, target, ZIP_DAMAGE, away.scale(1.8).add(0.0, 0.45, 0.0), ParticleTypes.SWEEP_ATTACK, SoundEvents.PLAYER_ATTACK_STRONG);
					nudge(player, away.scale(-0.35).add(0.0, 0.35, 0.0));
					// done: it ends a tick later
					player.setAttached(ACTIVE, new Active(kind, target.getId(), level.getGameTime() - ticks(kind) + 1));
				}
			}
			case TRIP_SWEEP -> {
				if (t == 3) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), player);
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), player);
					hit(player, target, SWEEP_DAMAGE, away.scale(0.2).add(0.0, 0.4, 0.0), ParticleTypes.SWEEP_ATTACK, SoundEvents.PLAYER_ATTACK_SWEEP);
				}
			}
			case PINNED_HAYMAKER -> {
				if (t == 8) {
					player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					hit(player, target, HAYMAKER_DAMAGE, away.scale(1.6).add(0.0, 0.4, 0.0), ParticleTypes.CRIT, SoundEvents.PLAYER_ATTACK_CRIT);
					com.jaymospowers.core.Shakes.send(player, 0.6F, target.getBoundingBox().getCenter());
				}
			}
			case IMPALE, WHIPLASH, HURL -> {
				haulAndSlam(player, level, kind, t, target);
				if (t == bundle(kind).haulEnd()) {
					float damage = kind == IMPALE ? IMPALE_DAMAGE : kind == WHIPLASH ? WHIP_DAMAGE : HURL_DAMAGE;
					Vec3 bounce = kind == WHIPLASH ? sideOf(player).scale(0.6).add(0.0, 0.3, 0.0) : new Vec3(0.0, 0.25, 0.0);
					slam(player, level, target, damage, bounce);
				}
			}
			case CONSTRICT -> {
				for (int squeeze : CONSTRICT_SQUEEZES) {
					if (t == squeeze) {
						hit(player, target, SQUEEZE_DAMAGE, Vec3.ZERO, WebSymbiote.dust(1.2F), SoundEvents.SLIME_SQUISH);
						sound(level, target, SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.6F);
					}
				}
				if (t == bundle(kind).wrapped()) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks(kind) - t, 6), player);
				}
				haulAndSlam(player, level, kind, t, target);
				if (t == bundle(kind).haulEnd()) {
					slam(player, level, target, SQUEEZE_DAMAGE, new Vec3(0.0, 0.2, 0.0));
				}
			}
			case SPEAR_FLURRY -> {
				for (int i = 0; i < FLURRY_STABS.length; i++) {
					if (t == FLURRY_STABS[i]) {
						boolean last = i == FLURRY_STABS.length - 1;
						hit(player, target, STAB_DAMAGE, last ? away.scale(1.3).add(0.0, 0.3, 0.0) : away.scale(0.15), WebSymbiote.dust(1.2F),
							SoundEvents.TRIDENT_HIT);
						if (last) {
							WebHaul.burst(level, target.getBoundingBox().getCenter().subtract(0.0, target.getBbHeight() * 0.3, 0.0), SLAM_BURST * 0.5F);
							com.jaymospowers.core.Shakes.send(player, SLAM_SHAKE * 0.6F, target.getBoundingBox().getCenter());
						}
					}
				}
			}
			case JAW_SNAP -> {
				reelToJaws(player, kind, t, target);
				if (t == 8) {
					bite(player, target, SNAP_DAMAGE, away.scale(0.8).add(0.0, 0.3, 0.0));
				}
			}
			case MAUL -> {
				reelToJaws(player, kind, t, target);
				if (t == 8 || t == 15) {
					bite(player, target, MAUL_DAMAGE, Vec3.ZERO);
				} else if (t == 22) {
					Vec3 right = new Vec3(-away.z, 0.0, away.x);
					bite(player, target, MAUL_DAMAGE + 1.0F, right.scale(player.getRandom().nextBoolean() ? 1.4 : -1.4).add(0.0, 0.5, 0.0));
				}
			}
			case MAW_ROAR -> {
				if (t == 3) {
					roar(player, level, target);
				} else if (t == 16) {
					bite(player, target, ROAR_BITE_DAMAGE, away.scale(0.7).add(0.0, 0.3, 0.0));
				} else {
					reelToJaws(player, kind, t, target);
				}
			}
			case DRAG_TO_JAWS -> {
				if (t == bundle(kind).wrapped()) {
					sound(level, target, SoundEvents.WARDEN_TENDRIL_CLICKS, 1.0F, 0.5F);
				}
				reelToJaws(player, kind, t, target);
				if (t == 17) {
					bite(player, target, DRAG_BITE_DAMAGE, away.scale(1.0).add(0.0, 0.35, 0.0));
				}
			}
			default -> {
			}
		}
	}

	/**
	 * The bundle whips out of the arm (drawn by every client from {@link #ACTIVE} and {@link #bundle}); the back
	 * tendrils come out writhing with it.
	 */
	private static void lash(ServerPlayer player) {
		long now = player.level().getGameTime();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		Long out = player.getAttached(WebSymbiote.TENDRILS_OUT);
		long until = now + WebSymbiote.TENDRILS_OUT_TICKS;
		if (out == null || out < until) {
			player.setAttached(WebSymbiote.TENDRILS_OUT, until);
		}
		sound(player.level(), player, SoundEvents.WARDEN_TENDRIL_CLICKS, 1.0F, 0.6F);
	}

	/** The side the counter goes (as the clients draw it, from its start tick): flat, to your right (+1) or left. */
	private static Vec3 sideOf(ServerPlayer player) {
		Active active = player.getAttached(ACTIVE);
		int side = active == null || (active.start() & 1L) == 0L ? 1 : -1;
		Vec3 ahead = player.getLookAngle().horizontal();
		ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : ahead.normalize();
		// (Minecraft's right of a heading (x, z) is (-z, x))
		return new Vec3(-ahead.z, 0.0, ahead.x).scale(side);
	}

	/**
	 * The tendril slam's haul: wrapped at the bundle's {@code wrapped} tick, it's hauled through its arc until
	 * {@code haulEnd} (Impale: up and straight back down; Hurl: up over your head to the ground behind you; Whiplash:
	 * swung round wide to your side; Constrict: held up in front while it squeezes, then down). Too big to lift, it's
	 * only held.
	 */
	private static void haulAndSlam(ServerPlayer player, ServerLevel level, int kind, int t, LivingEntity target) {
		Bundle bundle = bundle(kind);
		Live live = live(player);
		if (t == bundle.wrapped()) {
			sound(level, target, SoundEvents.WARDEN_TENDRIL_CLICKS, 1.0F, 0.5F);
			sound(level, target, SoundEvents.SLIME_ATTACK, 1.0F, 0.5F);
			live.hauling = WebHaul.haulable(target);
			Vec3 from = target.position();
			Vec3 ahead = away(player, target);
			Vec3 side = sideOf(player);
			Vec3 feet = player.position();
			live.haulFrom = from;
			switch (kind) {
				case IMPALE -> {
					live.haulApex = from.lerp(feet, 0.25).add(0.0, IMPALE_LIFT, 0.0);
					live.haulTo = WebHaul.ground(level, from, target);
				}
				case HURL -> {
					live.haulApex = player.getEyePosition().add(0.0, HURL_APEX, 0.0);
					live.haulTo = WebHaul.ground(level, feet.subtract(ahead.scale(HURL_BEHIND)), target);
				}
				case WHIPLASH -> {
					live.haulApex = feet.add(side.scale(WHIP_SWING)).add(ahead.scale(1.0)).add(0.0, WHIP_APEX, 0.0);
					live.haulTo = WebHaul.ground(level, feet.add(side.scale(WHIP_LAND)).subtract(ahead.scale(0.3)), target);
				}
				default -> {
					// constrict: held up in front, then brought down there
					live.haulApex = feet.add(ahead.scale(CONSTRICT_AHEAD)).add(0.0, CONSTRICT_LIFT, 0.0);
					live.haulTo = WebHaul.ground(level, feet.add(ahead.scale(CONSTRICT_AHEAD + 0.2)), target);
				}
			}
		}
		if (t <= bundle.wrapped() || t > bundle.haulEnd()) {
			return;
		}
		if (!live.hauling) {
			// too big to lift: held where it is
			WebHaul.push(target, Vec3.ZERO);
			return;
		}
		Vec3 spot;
		if (kind == CONSTRICT) {
			// up into the hold, kept there through the squeezes, then down hard over the last few ticks
			int drop = bundle.haulEnd() - 4;
			spot = t < drop ? live.haulFrom.lerp(live.haulApex, Math.min(1.0, (t - bundle.wrapped()) / 4.0))
				: live.haulApex.lerp(live.haulTo, Math.pow((t - drop) / 4.0, 1.6));
		} else {
			// slow off the ground, faster and faster over the top and down
			double u = (t - bundle.wrapped()) / (double)(bundle.haulEnd() - bundle.wrapped());
			spot = WebHaul.arc(live.haulFrom, live.haulApex, live.haulTo, Math.pow(u, 1.35));
		}
		WebHaul.steer(target, spot);
	}

	/** The slam: hurt, stunned, a burst of black mass and dust where it hits, every screen near shaken. */
	private static void slam(ServerPlayer player, ServerLevel level, LivingEntity target, float damage, Vec3 bounce) {
		Live live = live(player);
		Vec3 at = live.hauling ? live.haulTo : WebHaul.ground(level, target.position(), target);
		live.hauling = false;
		hit(player, target, damage, bounce, ParticleTypes.EXPLOSION, SoundEvents.PLAYER_ATTACK_KNOCKBACK);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLAM_STUN_TICKS, 3), player);
		WebHaul.burst(level, at, SLAM_BURST);
		com.jaymospowers.core.Shakes.heavy(player, at, SLAM_FELT, SLAM_SHAKE);
		if (target instanceof ServerPlayer slammed) {
			com.jaymospowers.core.Shakes.send(slammed, SLAMMED_SHAKE, player.position());
		}
	}

	/** Maw moves: once the bundle has it, it's reeled in to your face for the jaws (until it lets go). */
	private static void reelToJaws(ServerPlayer player, int kind, int t, LivingEntity target) {
		Bundle bundle = bundle(kind);
		if (t < bundle.wrapped() || t >= bundle.release() || !WebHaul.haulable(target)) {
			return;
		}
		Vec3 spot = player.getEyePosition().add(player.getLookAngle().scale(JAWS_AHEAD)).subtract(0.0, target.getBbHeight() * 0.5, 0.0);
		WebHaul.steer(target, target.position().lerp(spot, kind == MAUL ? 0.6 : 0.45));
	}

	/** The maw's jaws close on it: a bite (it never feeds on the living). */
	private static void bite(ServerPlayer player, LivingEntity target, float damage, Vec3 push) {
		ServerLevel level = player.level();
		hit(player, target, damage, push, WebSymbiote.dust(1.5F), SoundEvents.EVOKER_FANGS_ATTACK);
		sound(level, target, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
		sound(level, player, SoundEvents.WARDEN_ATTACK_IMPACT, 0.7F, 1.2F);
		com.jaymospowers.core.Shakes.send(player, 0.7F, target.getBoundingBox().getCenter());
	}

	/** Maw Roar: everything near reels back from it, weakened and slowed. */
	private static void roar(ServerPlayer player, ServerLevel level, LivingEntity target) {
		sound(level, player, SoundEvents.WARDEN_ROAR, 1.2F, 1.3F);
		com.jaymospowers.core.Shakes.send(player, 1.2F, null);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ROAR_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceTo(player) <= ROAR_RADIUS && !(e instanceof Player p && p.isCreative()))) {
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), player);
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), player);
			Vec3 push = away(player, living).scale(ROAR_PUSH).add(0.0, 0.3, 0.0);
			if (living == target) {
				living.hurtServer(level, level.damageSources().playerAttack(player), ROAR_DAMAGE);
				push = push.scale(0.4);
			}
			push(living, push);
		}
		level.sendParticles(WebSymbiote.dust(1.6F), player.getX(), player.getEyeY(), player.getZ(), 30, 1.2, 0.6, 1.2, 0.0);
	}

	/** A blow lands: the damage, the knock, a burst and a sound. */
	private static void hit(ServerPlayer player, LivingEntity target, float damage, Vec3 push, ParticleOptions particle, SoundEvent sound) {
		ServerLevel level = player.level();
		target.hurtServer(level, level.damageSources().playerAttack(player), damage);
		push(target, push);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(particle, at.x, at.y, at.z, particle == ParticleTypes.SWEEP_ATTACK || particle == ParticleTypes.EXPLOSION ? 1 : 12,
			0.3, 0.3, 0.3, 0.2);
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.1F, 0.9F + player.getRandom().nextFloat() * 0.2F);
	}

	/** Zip Kick: fly straight at it. */
	private static void zip(ServerPlayer player, LivingEntity target) {
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.position().add(0.0, 0.9, 0.0));
		if (to.lengthSqr() > 1.0E-4) {
			nudge(player, to.normalize().scale(ZIP_SPEED));
		}
	}

	/** A line of cobweb from your hand to it, for a web that's there and gone. */
	private static void webTrail(ServerLevel level, ServerPlayer player, Entity target) {
		Vec3 from = player.getEyePosition().subtract(0.0, 0.4, 0.0);
		Vec3 to = target.getBoundingBox().getCenter();
		int steps = Mth.clamp((int)(from.distanceTo(to) * 3.0), 3, 30);
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double)steps);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	/** Flat, from you to it. */
	private static Vec3 away(Entity from, Entity to) {
		Vec3 d = to.position().subtract(from.position());
		Vec3 flat = new Vec3(d.x, 0.0, d.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = from.getLookAngle().horizontal();
		}
		return flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
	}

	/** Sets its motion (a player's own client has to be told). */
	private static void push(Entity target, Vec3 velocity) {
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(velocity));
		}
	}

	private static void nudge(ServerPlayer player, Vec3 velocity) {
		ServerPlayNetworking.send(player, new WebNet.Nudge(velocity));
	}

	private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** The Spider-Sense is tingling (a dodge's counter window): how many ticks into it, or -1. */
	public static float sensing(Player player, float partialTick) {
		Long until = player.getAttached(READY);
		if (until == null) {
			return -1.0F;
		}
		float t = player.level().getGameTime() + partialTick - (until - WINDOW);
		return t >= 0.0F && t <= WINDOW ? t : -1.0F;
	}

	/** Whether the local counter window is open (the client's check before it sends {@link Strike}). */
	public static boolean ready(@Nullable Player player) {
		if (player == null) {
			return false;
		}
		Long until = player.getAttached(READY);
		return until != null && player.level().getGameTime() <= until && player.getAttached(ACTIVE) == null;
	}
}
