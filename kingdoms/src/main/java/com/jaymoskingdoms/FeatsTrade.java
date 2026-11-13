package com.jaymoskingdoms;

import com.jaymoskingdoms.Feats.Feat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

/**
 * The feats that work among people and in the kitchen and sickroom (see {@link Feats}):
 * <ul>
 * <li><b>Healer</b>: Jaymo's Powers' bandages and rags heal {@code HEALER_DRESSING} more and every treatment goes
 * {@code HEALER_QUICKER} quicker (its {@code MedicineSkill} reads {@link Feats#has(Player, String)}).</li>
 * <li><b>Chef</b>: Jaymo's Kitchen's meals give {@link #CHEF_MEALS}x the health they do ({@link KitchenCompat#registerChef}).</li>
 * <li><b>Linguist</b>: taking the feat teaches {@link #LINGUIST_TONGUES} tongues fluently, and every word learned after counts
 * {@link #LINGUIST_FASTER}x ({@link Languages#learn}).</li>
 * <li><b>Actor</b>: {@link #ACTOR_PRESENCE} points more of the Presence that carries a word ({@link Presence#skill}), {@link #ACTOR_LIE} more
 * chance to lie round a villager ({@link Persuasion}), and a liar's tells seen {@link #ACTOR_SHARP} sooner ({@link Liars#sharp}).</li>
 * <li><b>Inspiring Leader</b>: the leader and friends within {@link #LEADER_RANGE} blocks (not foes) save {@link #LEADER_SAVES} better, fear
 * included ({@link Saves}); after a short rest ({@link Rests#shortRest}) they stand with {@link #LEADER_ABSORB_TICKS} ticks of absorption.</li>
 * </ul>
 */
public final class FeatsTrade {
	static final float CHEF_MEALS = 1.5F;
	static final int LINGUIST_TONGUES = 3, LINGUIST_FASTER = 2;
	static final int ACTOR_PRESENCE = 30;
	static final float ACTOR_LIE = 0.2F, ACTOR_SHARP = 0.1F;
	static final double LEADER_RANGE = 12.0;
	static final float LEADER_SAVES = 0.15F;
	static final int LEADER_ABSORB_TICKS = 20 * 60 * 2;

	private FeatsTrade() {
	}

	static void register() {
		KitchenCompat.registerChef();
	}

	/** A feat was just taken: the ones with something to give at once. */
	static void taken(ServerPlayer player, Feat feat) {
		if (feat != Feat.LINGUIST) {
			return;
		}
		int taught = 0;
		for (Languages.Tongue t : Languages.Tongue.values()) {
			if (taught < LINGUIST_TONGUES && !Languages.speaks(player, t)) {
				// (the feat doubles what is learned, so half is asked)
				Languages.learn(player, t, (Languages.FLUENT - Languages.points(player, t) + 1) / 2);
				taught++;
			}
		}
	}

	/** A Chef's meals: the factor on the health Kitchen's meals give {@code player}. */
	public static double meals(Player player) {
		return Feats.has(player, Feat.CHEF) ? CHEF_MEALS : 1.0;
	}

	/** Linguist: {@code points} of a tongue, doubled. */
	static int linguistPoints(ServerPlayer player, int points) {
		return Feats.has(player, Feat.LINGUIST) ? points * LINGUIST_FASTER : points;
	}

	static int actorPoints(Player player) {
		return Feats.has(player, Feat.ACTOR) ? ACTOR_PRESENCE : 0;
	}

	static float actorLie(ServerPlayer player) {
		return Feats.has(player, Feat.ACTOR) ? ACTOR_LIE : 0.0F;
	}

	static float actorSharp(ServerPlayer player) {
		return Feats.has(player, Feat.ACTOR) ? ACTOR_SHARP : 0.0F;
	}

	/** Whether {@code who} is a leader, or a friend standing near one. */
	private static boolean led(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level) || who instanceof Enemy) {
			return false;
		}
		for (ServerPlayer p : level.players()) {
			if (Feats.has(p, Feat.INSPIRING_LEADER) && p.distanceToSqr(who) <= LEADER_RANGE * LEADER_RANGE) {
				return true;
			}
		}
		return false;
	}

	/** What an Inspiring Leader near {@code who} adds to their saves. */
	public static float saves(LivingEntity who) {
		return led(who) ? LEADER_SAVES : 0.0F;
	}

	/** A short rest taken by a leader: friends near stand with absorption. */
	static void rested(ServerPlayer leader) {
		if (!Feats.has(leader, Feat.INSPIRING_LEADER)) {
			return;
		}
		ServerLevel level = leader.level();
		for (LivingEntity who : level.getEntitiesOfClass(LivingEntity.class, leader.getBoundingBox().inflate(LEADER_RANGE),
			e -> e.isAlive() && (e instanceof Player || e instanceof Villager) && !(e instanceof Enemy))) {
			who.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, LEADER_ABSORB_TICKS, 0, false, true, true));
		}
		level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 1.6F);
		Fx.ring(level, leader.position().add(0.0, 0.1, 0.0), (float) LEADER_RANGE * 0.5F, 0xFFD966);
	}
}
