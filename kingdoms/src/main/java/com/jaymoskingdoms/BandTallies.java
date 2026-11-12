package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * A band's own parting word (docs/Pawn-Plan.md row A6, the foe side; after the word and gift a Dragon's Dogma pawn
 * takes home, a commercial game: ideas only). Every hit a band member lands on a player or a player's companion counts
 * to its tally. When the fight's over, its leader has a word for them: the best of them is praised and comes out of it
 * blooded ({@link #BLOODED}: a little more health, up to {@link #MAX_BLOODED} times, so the ones who did well are
 * harder next time), and one who landed nothing is told off.
 */
public final class BandTallies {
	/** The least tally that earns praise; health per blooding; how many times. */
	static final float PRAISE_AT = 6.0F;
	static final double HEALTH_PER = 2.0;
	static final int MAX_BLOODED = 3;
	/** Mates this near (blocks) the leader are given a word. */
	static final double NEAR = 24.0;

	/** Times a band member was praised. Saved. */
	public static final AttachmentType<Integer> BLOODED = AttachmentRegistry.create(JaymosKingdoms.id("band_blooded"),
		builder -> builder.persistent(Codec.INT));

	/** Damage each band member dealt in its current fight (0 for one that was in it and landed nothing). */
	private static final Map<UUID, Float> TALLY = new HashMap<>();

	private BandTallies() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> dealt(entity, source, taken));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TALLY.clear());
	}

	private static boolean band(LivingEntity e) {
		return FoeLore.keeps(Comrades.side(e));
	}

	private static void dealt(LivingEntity victim, DamageSource source, float taken) {
		if (taken <= 0.0F || !(source.getEntity() instanceof Mob m)) {
			if (source.getEntity() instanceof Player && victim instanceof Mob hurt && band(hurt)) {
				// in it, at least
				TALLY.putIfAbsent(hurt.getUUID(), 0.0F);
			}
			return;
		}
		if (band(m) && (victim instanceof Player || Bonds.lordOf(victim) != null)) {
			TALLY.merge(m.getUUID(), taken, Float::sum);
		}
	}

	/** (Comrades) {@code m}'s fight is over: if it leads a band, it has a word for the best and the worst of them. */
	static void ended(Mob m) {
		if (!(m.level() instanceof ServerLevel level) || !BandLeaders.isLeader(m) || !band(m)) {
			TALLY.remove(m.getUUID());
			return;
		}
		String side = Comrades.side(m);
		Mob best = null, worst = null;
		float bestTally = PRAISE_AT;
		for (Mob mate : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(NEAR), e -> e.isAlive() && !Downed.isDowned(e)
			&& TALLY.containsKey(e.getUUID()) && Comrades.side(e).equals(side))) {
			float t = TALLY.get(mate.getUUID());
			if (t >= bestTally) {
				bestTally = t;
				best = mate;
			}
			if (t <= 0.0F && mate != m && worst == null) {
				worst = mate;
			}
		}
		if (best != null) {
			if (best == m) {
				Callouts.say(m, "band.boast", null, true);
			} else {
				Callouts.say(m, "band.praise", best, true, best.getDisplayName());
			}
			blood(best);
		} else if (worst != null) {
			Callouts.say(m, "band.scold", worst, true, worst.getDisplayName());
		}
		for (Mob mate : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(NEAR), e -> Comrades.side(e).equals(side))) {
			TALLY.remove(mate.getUUID());
		}
		TALLY.remove(m.getUUID());
	}

	/** {@code m} comes out of it blooded: a little harder next time. */
	private static void blood(Mob m) {
		int times = Math.min(MAX_BLOODED, m.getAttachedOrElse(BLOODED, 0) + 1);
		m.setAttached(BLOODED, times);
		AttributeInstance health = m.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("band_blooded"), times * HEALTH_PER,
				AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
