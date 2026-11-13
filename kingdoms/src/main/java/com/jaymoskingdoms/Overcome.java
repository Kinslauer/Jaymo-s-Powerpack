package com.jaymoskingdoms;

import com.jaymoskingdoms.Temperaments.Temperament;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Overcome by the other side. Andy (2026-11-13): "villagers and players with low exposure have a chance to faint upon
 * seeing an especially strong supernatural mob, they will feel uneasy if this starts happening and they will have the
 * chance to withdraw from the battle. Brave temperaments do not deal with this at all."
 * <p>
 * Whoever has an overwhelming thing ({@link Exposure#overwhelming}) before their eyes builds up unease ({@link #RISE} each
 * look, scaled by how little they know of the other side and by their temperament, {@link #weight}); out of its sight it
 * ebbs ({@link #EBB}). Players and the world's folk (villagers, MCA's, the realms' and camps' folk, traders) alike.
 * <ul>
 * <li><b>Uneasy</b> (from {@link #UNEASY}): a player's screen goes cold at the edges and their heart beats in their ears,
 * louder as it climbs ({@link #UNEASE}, drawn by {@code UneaseClient}); that is their warning, and their chance to get out
 * of its sight. One of the folk says so (a speech bubble), and may withdraw ({@link #withdrawChance}, by temperament): one
 * in a fight breaks off and runs from it, one going about their day runs home ({@link Frights}). A companion or a hired
 * sword withdraws the same way: your side can lose its nerve.</li>
 * <li><b>Overcome</b> (at {@link #FAINT_AT}): someone still below Seasoned exposure ({@link Exposure#SEASONED}) may faint
 * outright, a chance each look ({@link #FAINT_CHANCE}). A player goes down in Jaymo's Powers' faint (the letterbox shuts,
 * they fall on their face, a blow brings them round; {@code Fainting.overcome}); one of the folk crumples where they stand
 * for {@link #FOLK_OUT} ticks, out cold (a blow wakes them, {@link HeldFast#sleep}). Either way the sight leaves its mark:
 * exposure ({@link #FAINTED}), and on a player the living nightmare ({@link LivingNightmare}).</li>
 * <li><b>Facing it</b>: unease that ebbs away again after being uneasy counts as having faced it ({@link #FACED}).</li>
 * </ul>
 * Never: the Brave, the Unveiled ({@link Exposure#UNVEILED}), bosses, players out of play (creative, spectating, downed),
 * anyone kept fearless by a hero's feast or a paladin's aura ({@link Fear#can}), the dead and the other side's own.
 */
public final class Overcome {
	// ================================================================================================================
	// Tuning
	/** How often everyone looks about them (ticks). */
	static final int EVERY = 10;
	/** How far an overwhelming thing is seen from (blocks), and how far round each player such things are looked for. */
	static final double RANGE = 24.0, AROUND = 48.0;
	/** Unease gained each look with one in sight (at no exposure, an ordinary temperament), and lost each look without. */
	static final float RISE = 7.0F, EBB = 5.0F;
	/** A boss's sight weighs this much more. */
	static final float BOSS = 1.5F;
	/** Uneasy from this; at this, someone below Seasoned may faint, this likely each look. */
	static final float UNEASY = 35.0F, FAINT_AT = 100.0F, FAINT_CHANCE = 0.2F;
	/** Unease weight by temperament (others 1). */
	static final float TIMID = 1.6F, GLOOMY = 1.25F, SUSPICIOUS = 1.1F, DEVOUT = 0.8F, PROUD = 0.8F, HOTHEADED = 0.7F;
	/** The chance one of the folk withdraws when they grow uneasy, by temperament (others {@link #WITHDRAW}). */
	static final float WITHDRAW = 0.35F, WITHDRAW_TIMID = 0.85F, WITHDRAW_GLOOMY = 0.6F, WITHDRAW_KIND = 0.45F, WITHDRAW_DUTIFUL = 0.15F,
		WITHDRAW_BOLD = 0.08F;
	/** How long one who withdraws keeps away (ticks). */
	static final int WITHDRAW_TICKS = 20 * 12;
	/** How long one of the folk lies out cold (ticks). */
	static final int FOLK_OUT = 20 * 15;
	/** Exposure for a faint, and for facing one down. */
	static final int FAINTED = 2, FACED = 1;
	/** Lines each: uneasy, withdrawing, coming round. */
	static final int LINES = 4;
	// ================================================================================================================

	/** A player's unease (0-100, in steps of 5), synced to them for their screen. Not saved. */
	public static final AttachmentType<Integer> UNEASE = AttachmentRegistry.create(JaymosKingdoms.id("unease"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final class State {
		float unease;
		boolean uneasy, withdrew;
		/** When one of the folk comes round (game time), or 0. */
		long outUntil;
	}

	private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

	private Overcome() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Overcome::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
		// a blow wakes one of the folk who fainted (HeldFast lets a sleeper go when hurt; this stands them up)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			State s = STATES.get(victim);
			if (s != null && s.outUntil > 0L && taken > 0.0F) {
				comeRound(victim, s);
			}
		});
	}

	/** Whether {@code who} is one of the folk lying out cold just now. */
	public static boolean out(LivingEntity who) {
		State s = STATES.get(who);
		return s != null && s.outUntil > 0L;
	}

	/** Whether nothing of this ever touches {@code who}. */
	static boolean immune(LivingEntity who) {
		if (who instanceof Player p) {
			if (p.isCreative() || p.isSpectator() || Downed.isDowned(p)) {
				return true;
			}
		} else if (!Fear.fearsAtAll(who) || who.isBaby() || Exposure.supernatural(who) || Legendary.boss(who) || Downed.isDowned(who)) {
			return true;
		}
		return brave(who) || !Fear.can(who) || Exposure.rankOf(who) >= Exposure.UNVEILED || Vampires.isVampire(who) || Lycans.cursed(who);
	}

	private static boolean brave(LivingEntity who) {
		return Temperament.byKey(who.getAttached(Temperaments.TEMPERAMENT)) == Temperament.BRAVE;
	}

	/** How hard an overwhelming sight weighs on {@code who}: less the more they've seen, more for the timid. */
	static float weight(LivingEntity who) {
		int points = Exposure.of(who);
		float known = Math.max(0.0F, 1.0F - points / (float)Exposure.RANK_FROM[Exposure.UNVEILED]);
		Temperament t = Temperament.byKey(who.getAttached(Temperaments.TEMPERAMENT));
		float temper = t == null ? 1.0F : switch (t) {
			case TIMID -> TIMID;
			case GLOOMY -> GLOOMY;
			case SUSPICIOUS -> SUSPICIOUS;
			case DEVOUT -> DEVOUT;
			case PROUD -> PROUD;
			case HOTHEADED -> HOTHEADED;
			case BRAVE -> 0.0F;
			default -> 1.0F;
		};
		return known * temper;
	}

	/** The chance one of the folk withdraws on growing uneasy. */
	static float withdrawChance(LivingEntity who) {
		Temperament t = Temperaments.of(who);
		if (t == null) {
			return WITHDRAW;
		}
		return switch (t) {
			case TIMID -> WITHDRAW_TIMID;
			case GLOOMY -> WITHDRAW_GLOOMY;
			case KINDHEARTED, CHEERFUL -> WITHDRAW_KIND;
			case DUTIFUL, DEVOUT -> WITHDRAW_DUTIFUL;
			case PROUD, HOTHEADED -> WITHDRAW_BOLD;
			case BRAVE -> 0.0F;
			default -> WITHDRAW;
		};
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		// the overwhelming near any player, then who sees each
		Map<LivingEntity, LivingEntity> seeing = new HashMap<>();
		List<LivingEntity> things = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(AROUND), Exposure::overwhelming)) {
				if (!things.contains(e)) {
					things.add(e);
				}
			}
		}
		for (LivingEntity thing : things) {
			for (LivingEntity who : thing.level().getEntitiesOfClass(LivingEntity.class, thing.getBoundingBox().inflate(RANGE),
				w -> w != thing && w.isAlive() && (w instanceof ServerPlayer || Fear.fearsAtAll(w)))) {
				if (!seeing.containsKey(who) && !immune(who) && Exposure.sees(who, thing, RANGE)) {
					seeing.put(who, thing);
				}
			}
		}
		for (Map.Entry<LivingEntity, LivingEntity> e : seeing.entrySet()) {
			LivingEntity who = e.getKey();
			State s = STATES.computeIfAbsent(who, k -> new State());
			if (s.outUntil > 0L) {
				continue;
			}
			float gain = RISE * weight(who) * (Legendary.boss(e.getValue()) ? BOSS : 1.0F);
			s.unease = Math.min(FAINT_AT, s.unease + gain);
			if (!s.uneasy && s.unease >= UNEASY) {
				s.uneasy = true;
				uneasy(who, e.getValue(), s);
			}
			if (s.unease >= FAINT_AT && Exposure.rankOf(who) < Exposure.SEASONED && who.getRandom().nextFloat() < FAINT_CHANCE) {
				faint(who, e.getValue(), s);
			}
			sync(who, s);
		}
		// everyone else: it ebbs; those out cold come round in time
		for (Map.Entry<LivingEntity, State> e : new ArrayList<>(STATES.entrySet())) {
			LivingEntity who = e.getKey();
			State s = e.getValue();
			if (who == null || who.isRemoved() || !who.isAlive()) {
				if (who != null) {
					STATES.remove(who);
				}
				continue;
			}
			if (s.outUntil > 0L) {
				lieStill(who, s);
				continue;
			}
			if (seeing.containsKey(who)) {
				continue;
			}
			s.unease = Math.max(0.0F, s.unease - EBB);
			if (s.unease <= 0.0F) {
				if (s.uneasy) {
					Exposure.add(who, FACED);
				}
				STATES.remove(who);
				who.removeAttached(UNEASE);
				continue;
			}
			sync(who, s);
		}
	}

	private static void sync(LivingEntity who, State s) {
		if (who instanceof ServerPlayer player) {
			int step = Math.round(s.unease / 5.0F) * 5;
			Integer had = player.getAttached(UNEASE);
			if (had == null || had != step) {
				player.setAttached(UNEASE, step);
			}
		}
	}

	/** {@code who} has grown uneasy at {@code thing}: one of the folk says so, and may withdraw. */
	private static void uneasy(LivingEntity who, LivingEntity thing, State s) {
		if (!(who.level() instanceof ServerLevel level) || who instanceof Player) {
			return;
		}
		if (level.getRandom().nextFloat() < withdrawChance(who)) {
			s.withdrew = true;
			say(level, who, "withdraw");
			withdraw(level, who, thing);
		} else {
			say(level, who, "uneasy");
		}
	}

	/** One of the folk breaks off from {@code thing} and gets away from it. */
	private static void withdraw(ServerLevel level, LivingEntity who, LivingEntity thing) {
		boolean fighting = who instanceof Mob m && Comrades.target(m) != null;
		if (who instanceof Mob m) {
			Comrades.stand(m);
		}
		if (who instanceof Villager v) {
			// home, or just away (a villager's brain walks it); one in a fight is also kept off it as it runs
			Frights.scare(level, v, thing.position());
			if (fighting) {
				Dread.flee(v, thing, WITHDRAW_TICKS);
			} else {
				Fear.frighten(v, thing, WITHDRAW_TICKS);
			}
		} else if (who instanceof PathfinderMob pm) {
			Dread.flee(pm, thing, WITHDRAW_TICKS);
		}
	}

	/** {@code who} faints at the sight of {@code thing}. */
	private static void faint(LivingEntity who, LivingEntity thing, State s) {
		if (who instanceof ServerPlayer player) {
			if (PowersCompat.overcome(player)) {
				Exposure.add(player, FAINTED);
				LivingNightmare.overcome(player);
				s.unease = UNEASY;
			}
			return;
		}
		if (!(who.level() instanceof ServerLevel level) || !HeldFast.sleep(who, FOLK_OUT)) {
			return;
		}
		s.outUntil = level.getGameTime() + FOLK_OUT;
		s.unease = UNEASY;
		if (who instanceof Mob m) {
			Comrades.stand(m);
			m.getNavigation().stop();
		}
		who.setPose(Pose.SLEEPING);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.5F, 0.6F);
		Exposure.add(who, FAINTED);
	}

	/** One of the folk lying out cold: kept down until their time is up. */
	private static void lieStill(LivingEntity who, State s) {
		if (who.level().getGameTime() >= s.outUntil || !HeldFast.asleep(who)) {
			comeRound(who, s);
			return;
		}
		if (who.getPose() != Pose.SLEEPING) {
			who.setPose(Pose.SLEEPING);
		}
		if (who instanceof Mob m) {
			m.getNavigation().stop();
		}
	}

	private static void comeRound(LivingEntity who, State s) {
		s.outUntil = 0L;
		HeldFast.release(who);
		if (who.getPose() == Pose.SLEEPING && !who.isSleeping()) {
			who.setPose(Pose.STANDING);
		}
		if (who.level() instanceof ServerLevel level) {
			say(level, who, "woke");
		}
	}

	private static void say(ServerLevel level, LivingEntity who, String moment) {
		Speech.say(level, who, Component.translatable("talk.jaymoskingdoms.overcome." + moment + "." + level.getRandom().nextInt(LINES)), Speech.YELLOW,
			Captains.SAY_RANGE);
	}

	/** For the menu and the client: a player's unease (0-100). */
	public static int unease(@Nullable Player player) {
		Integer u = player == null ? null : player.getAttached(UNEASE);
		return u == null ? 0 : u;
	}
}
