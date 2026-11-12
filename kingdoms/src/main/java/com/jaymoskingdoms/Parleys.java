package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * A band's tongue-speaker bargains with you (docs/Pawn-Plan.md row G2, the foe side; after the pawn inclinations of
 * Dragon's Dogma, a commercial game: ideas only). Come near a band whose tongue-speaker ({@link Specialties}) sees you
 * before blood is drawn, and they hold their side back and name a toll: two coins, and one for each of the band near,
 * at most {@link #MOST}. Pay it (coin used on the tongue-speaker) inside {@link #OFFER_FOR} and the band lets you pass
 * for a day ({@link #TOLLS}); walk off or let it run out and they come for you. Strike any of them and the bargain's off.
 * One offer a day from each band.
 */
public final class Parleys {
	/** A tongue-speaker offers this near (blocks); the band near it counts this far. */
	static final double OFFER_NEAR = 14.0, BAND_NEAR = 24.0, WALKED_OFF = 40.0;
	/** You have this long to pay (ticks); a toll paid holds this long; a band asks once in this long. */
	static final int OFFER_FOR = 600;
	static final long TRUCE_FOR = 24000L, ASK_AGAIN = 24000L, STRUCK_GAP = 200L;
	/** The toll: this, and one for each of the band near; at most this (emeralds' worth). */
	static final int BASE = 2, MOST = 12;
	static final int EVERY = 20;

	/** On a player: a band's side → until when it lets them pass (game time). Saved. */
	public static final AttachmentType<Map<String, Long>> TOLLS = AttachmentRegistry.create(JaymosKingdoms.id("band_tolls"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private record Offer(UUID player, String side, int price, long until) {
	}

	private static final Map<Mob, Offer> OFFERS = new IdentityHashMap<>();
	/** player|side → when last asked, when last struck. */
	private static final Map<String, Long> ASKED = new HashMap<>(), STRUCK = new HashMap<>();

	private Parleys() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Parleys::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> struck(victim, source));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OFFERS.clear();
			ASKED.clear();
			STRUCK.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof Mob m)
				|| !Coinage.isMoney(sp.getMainHandItem())) {
				return InteractionResult.PASS;
			}
			Offer o = OFFERS.get(m);
			if (o == null || !o.player().equals(sp.getUUID())) {
				return InteractionResult.PASS;
			}
			long milli = Money.worth(o.price());
			if (!Money.canPay(sp, milli)) {
				Callouts.say(m, "parley.short", sp, true, Money.text(sp, milli));
				return InteractionResult.SUCCESS;
			}
			Money.charge(sp, milli);
			OFFERS.remove(m);
			toll(sp, o.side(), world.getGameTime() + TRUCE_FOR);
			Callouts.say(m, "parley.paid", sp, true, sp.getDisplayName());
			return InteractionResult.SUCCESS;
		});
	}

	/** (ThrallTargetMixin) {@code attacker}'s band was paid to let {@code target} pass, or is hearing them out. */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		if (!(target instanceof ServerPlayer p) || !(attacker instanceof Mob)) {
			return false;
		}
		Map<String, Long> tolls = p.getAttached(TOLLS);
		if (tolls == null || tolls.isEmpty()) {
			return false;
		}
		Long until = tolls.get(Comrades.side(attacker));
		return until != null && until > attacker.level().getGameTime();
	}

	private static void toll(ServerPlayer p, String side, long until) {
		Map<String, Long> tolls = new HashMap<>(p.getAttachedOrElse(TOLLS, Map.of()));
		long now = p.level().getGameTime();
		tolls.values().removeIf(t -> t <= now);
		if (until <= now) {
			tolls.remove(side);
		} else {
			tolls.put(side, until);
		}
		p.setAttached(TOLLS, Map.copyOf(tolls));
	}

	/** A player struck one of a band: the bargain, paid or not, is off. */
	private static void struck(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer p) || !(victim instanceof Mob m)) {
			return;
		}
		String side = Comrades.side(m);
		if (!FoeLore.keeps(side)) {
			return;
		}
		STRUCK.put(p.getUUID() + "|" + side, p.level().getGameTime());
		Map<String, Long> tolls = p.getAttached(TOLLS);
		if (tolls != null && tolls.containsKey(side)) {
			toll(p, side, 0L);
		}
		OFFERS.values().removeIf(o -> o.player().equals(p.getUUID()) && o.side().equals(side));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		// offers running out
		for (var it = OFFERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Offer> e = it.next();
			Mob m = e.getKey();
			Offer o = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(o.player());
			if (!m.isAlive() || Downed.isDowned(m) || p == null || p.level() != m.level()) {
				it.remove();
				if (p != null) {
					toll(p, o.side(), 0L);
				}
				continue;
			}
			if (now > o.until() || m.distanceTo(p) > WALKED_OFF) {
				it.remove();
				toll(p, o.side(), 0L);
				Callouts.say(m, "parley.refused", p, true, p.getDisplayName());
				if (m.distanceTo(p) < BAND_NEAR) {
					Comrades.aim(m, p);
				}
				continue;
			}
			// the tongue-speaker comes up to talk
			m.getLookControl().setLookAt(p, 30.0F, 30.0F);
			if (m.distanceTo(p) > 3.0 && m.getNavigation().isDone()) {
				m.getNavigation().moveTo(p, 0.6);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative() || Downed.isDowned(player)) {
				continue;
			}
			ServerLevel level = player.level();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(OFFER_NEAR), e -> e.isAlive() && !Downed.isDowned(e)
				&& band(server, Comrades.side(e)) && Temperaments.humanoid(e))) {
				Specialties.roll(m);
				if (!Specialties.is(m, Specialties.Specialty.TONGUE) || OFFERS.containsKey(m) || !m.hasLineOfSight(player)) {
					continue;
				}
				offer(level, player, m, now);
			}
		}
	}

	/** A camp's band (or loose illagers): not a town's folk, nor a rival party. */
	private static boolean band(MinecraftServer server, String side) {
		if (side.equals("illager")) {
			return true;
		}
		if (!side.startsWith("f:")) {
			return false;
		}
		try {
			return Camps.data(server, Integer.parseInt(side.substring(2))) != null;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static void offer(ServerLevel level, ServerPlayer player, Mob m, long now) {
		String side = Comrades.side(m);
		String key = player.getUUID() + "|" + side;
		if (now - ASKED.getOrDefault(key, -ASK_AGAIN * 2L) < ASK_AGAIN || now - STRUCK.getOrDefault(key, -STRUCK_GAP * 2L) < STRUCK_GAP
			|| spares(m, player)) {
			return;
		}
		ASKED.put(key, now);
		int band = 0;
		for (Mob mate : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(BAND_NEAR), e -> e != m && e.isAlive() && !Downed.isDowned(e)
			&& Comrades.side(e).equals(side))) {
			band++;
			// they hold while it's talked out
			if (mate.getTarget() == player) {
				mate.setTarget(null);
			}
		}
		if (m.getTarget() == player) {
			m.setTarget(null);
		}
		int price = Math.min(MOST, BASE + band);
		OFFERS.put(m, new Offer(player.getUUID(), side, price, now + OFFER_FOR));
		toll(player, side, now + OFFER_FOR);
		Callouts.say(m, "parley.offer", player, true, player.getDisplayName(), Money.text(player, Money.worth(price)));
	}
}
