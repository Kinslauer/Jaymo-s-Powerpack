package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

/**
 * (am-whiteflag, medieval picks; Armed Mobs' white flag, an idea only) Raise a plain white banner (use it, in your main
 * hand, held up in the air) in a fight with a realm's guards or a band, and those set on you look to it: if your
 * standing with them allows (not past {@link #REFUSE_REP}, and for a realm not a hated outlaw), they lower their weapons
 * and hold off for {@link #TRUCE_TICKS}. One truce a fight: once it's given, that side won't heed your flag again for
 * {@link #SHUN_TICKS} after it. A band's tongue-speaker comes to name its terms ({@link Parleys#hail}); a realm's guard
 * hears yours if you go up to him (use him): pay what's owed (the whole fine to its treasury, and the hunt for you
 * called off; once in {@link #FINE_GAP}), give yourself up ({@link Busts}), or go in peace. Strike any of them under the
 * flag and it's broken: they come on, think worse of you, and won't heed it for {@link #BROKEN_SHUN}. Monsters don't
 * know what it means. The waits are saved on you ({@link #WAITS}).
 */
public final class WhiteFlags {
	/** Those set on you this near see the flag; checked every this many ticks. */
	static final double SEEN_RANGE = 24.0;
	static final int EVERY = 10;
	/** A flag used is up this long (ticks; held right-click keeps it up). */
	static final long RAISE_TICKS = 20 * 2;
	/** A truce holds this long (ticks); after it the flag's no good with that side for this long; broken, for this long. */
	static final long TRUCE_TICKS = 20 * 60, SHUN_TICKS = 20L * 60 * 10, BROKEN_SHUN = 20L * 60 * 20;
	/** A fine paid buys back this much good name (at most), and can be paid to a realm once in this long (ticks). */
	static final int FINE_REP = 30;
	static final long FINE_GAP = 24000L;
	/** They won't stand down for one they think this little of. */
	static final int REFUSE_REP = -400;
	/** Breaking the flag costs this much good name with a realm. */
	static final int BROKEN_REP = -40;
	/** The fine to settle with a realm (emeralds' worth), and what good name it buys back (up to just above hostile). */
	static final int FINE = 10;
	static final int LINES = 2;

	/** On a player: "shun|side" → their flag's not heeded by that side until; "fine|realm" → a fine paid there at. Saved. */
	static final AttachmentType<Map<String, Long>> WAITS = AttachmentRegistry.create(JaymosKingdoms.id("white_flag_waits"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	/** player → side → truce until. */
	private static final Map<UUID, Map<String, Long>> TRUCES = new HashMap<>();
	/** player|side → refused at (so a refusal isn't said every check); player → their flag raised at. */
	private static final Map<String, Long> REFUSED = new HashMap<>();
	private static final Map<UUID, Long> RAISED = new HashMap<>();

	private WhiteFlags() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WhiteFlags::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer p && victim instanceof Mob m) {
				struck(p, m);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TRUCES.clear();
			REFUSED.clear();
			RAISED.clear();
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			// raised: used in the air, in the main hand (held right-click keeps it up)
			if (hand == InteractionHand.MAIN_HAND && !world.isClientSide() && player instanceof ServerPlayer sp && white(sp.getMainHandItem())) {
				if (!RAISED.containsKey(sp.getUUID()) || world.getGameTime() - RAISED.get(sp.getUUID()) > RAISE_TICKS) {
					sp.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				}
				RAISED.put(sp.getUUID(), world.getGameTime());
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof PathfinderMob m)
				|| !(m instanceof Worker w) || w.role() != Folk.GUARD && w.role() != Folk.ARCHER || !truce(sp, Comrades.side(m))) {
				return InteractionResult.PASS;
			}
			Faction f = Factions.get(sp.level().getServer(), w.factionId());
			if (f == null || !f.type().isRealm()) {
				return InteractionResult.PASS;
			}
			terms(sp.level(), sp, m, f);
			return InteractionResult.SUCCESS;
		});
	}

	/** {@code stack} is a plain white banner. */
	static boolean white(ItemStack stack) {
		return stack.getItem() instanceof BannerItem b && b.getColor() == DyeColor.WHITE
			&& stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty();
	}

	/** {@code p} is holding one up: in the main hand, raised a moment ago. */
	static boolean raised(ServerPlayer p) {
		Long at = RAISED.get(p.getUUID());
		return at != null && p.level().getGameTime() - at <= RAISE_TICKS && white(p.getMainHandItem());
	}

	private static long wait(ServerPlayer p, String key) {
		return p.getAttachedOrElse(WAITS, Map.of()).getOrDefault(key, 0L);
	}

	private static void wait(ServerPlayer p, String key, long value) {
		Map<String, Long> waits = new HashMap<>(p.getAttachedOrElse(WAITS, Map.of()));
		long now = p.level().getGameTime();
		waits.values().removeIf(t -> t < now - FINE_GAP);
		waits.put(key, value);
		p.setAttached(WAITS, Map.copyOf(waits));
	}

	private static boolean truce(ServerPlayer p, String side) {
		Map<String, Long> mine = TRUCES.get(p.getUUID());
		Long until = mine == null ? null : mine.get(side);
		return until != null && until > p.level().getGameTime();
	}

	/** (ThrallTargetMixin, McaGuardFoesMixin) {@code attacker}'s side is keeping a truce with {@code target} under the white flag. */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		return target instanceof ServerPlayer p && attacker instanceof Mob && !TRUCES.isEmpty() && TRUCES.containsKey(p.getUUID())
			&& truce(p, Comrades.side(attacker));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.isCreative() || !raised(p)) {
				continue;
			}
			ServerLevel level = p.level();
			long now = level.getGameTime();
			for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SEEN_RANGE), e -> e.isAlive() && !Downed.isDowned(e)
				&& Rulers.target(e) == p)) {
				String side = Comrades.side(m);
				Faction realm = realm(server, m);
				if (realm == null && !band(server, m)) {
					// a monster (or a beast) doesn't know what it means
					continue;
				}
				if (truce(p, side)) {
					// (one of the side who's come on since: he's called off too)
					Comrades.stand(m);
					continue;
				}
				String key = p.getUUID() + "|" + side;
				if (!honours(p, realm, m, side, now)) {
					if (now - REFUSED.getOrDefault(key, -TRUCE_TICKS) >= TRUCE_TICKS) {
						REFUSED.put(key, now);
						Speech.tell(level, m, p, Component.translatable("speech.jaymoskingdoms.whiteflag.refuse." + level.getRandom().nextInt(LINES)), Speech.RED);
					}
					continue;
				}
				stand(level, p, m, side, now);
				if (realm == null) {
					Parleys.hail(level, p, m);
				}
			}
		}
	}

	/** Its realm, if {@code m} is a realm's guard or archer. */
	private static @Nullable Faction realm(MinecraftServer server, Mob m) {
		if (!(m instanceof Worker w) || w.role() != Folk.GUARD && w.role() != Folk.ARCHER || w.factionId() < 0) {
			return null;
		}
		Faction f = Factions.get(server, w.factionId());
		return f != null && f.type().isRealm() ? f : null;
	}

	/** {@code m} is one of a camp's band, or an illager. */
	private static boolean band(MinecraftServer server, Mob m) {
		if (m instanceof AbstractIllager || Illagers.tag(m) != null) {
			return true;
		}
		String side = Comrades.side(m);
		if (!side.startsWith("f:")) {
			return false;
		}
		try {
			return Camps.data(server, Integer.parseInt(side.substring(2))) != null;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** Their standing allows it: not one they think too little of, nor shunned for breaking a flag; a realm's, no hated outlaw. */
	private static boolean honours(ServerPlayer p, @Nullable Faction realm, Mob m, String side, long now) {
		if (wait(p, "shun|" + side) > now) {
			return false;
		}
		int id = realm != null ? realm.id() : m instanceof Worker w ? w.factionId() : -1;
		int rep = id >= 0 ? Reputation.get(p, id) : 0;
		if (rep <= REFUSE_REP) {
			return false;
		}
		return realm == null || !Theft.outlaw(p, realm.id()) || rep > Reputation.HOSTILE;
	}

	/** {@code m}'s side lowers its weapons to {@code p}. */
	private static void stand(ServerLevel level, ServerPlayer p, Mob m, String side, long now) {
		TRUCES.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(side, now + TRUCE_TICKS);
		// one truce a fight: after this one, the flag's no good with them a while
		wait(p, "shun|" + side, now + TRUCE_TICKS + SHUN_TICKS);
		for (Mob mate : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SEEN_RANGE), e -> e.isAlive() && Comrades.side(e).equals(side))) {
			if (Rulers.target(mate) == p) {
				Comrades.stand(mate);
			}
		}
		m.getLookControl().setLookAt(p, 30.0F, 30.0F);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		Speech.tell(level, m, p, Component.translatable("speech.jaymoskingdoms.whiteflag.stand." + level.getRandom().nextInt(LINES)), Speech.WHITE);
	}

	/** {@code p} struck one of a side keeping a truce with them: the flag is broken. */
	private static void struck(ServerPlayer p, Mob m) {
		String side = Comrades.side(m);
		Map<String, Long> mine = TRUCES.get(p.getUUID());
		if (mine == null || mine.remove(side) == null) {
			return;
		}
		ServerLevel level = p.level();
		wait(p, "shun|" + side, level.getGameTime() + BROKEN_SHUN);
		Faction realm = realm(level.getServer(), m);
		if (realm != null) {
			Reputation.change(p, realm, BROKEN_REP);
		}
		Speech.tell(level, m, p, Component.translatable("speech.jaymoskingdoms.whiteflag.broken." + level.getRandom().nextInt(LINES)), Speech.RED);
		for (Mob mate : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SEEN_RANGE), e -> e.isAlive() && Comrades.side(e).equals(side))) {
			Comrades.aim(mate, p);
		}
	}

	/** {@code p} goes up to {@code guard} under the flag: the terms. */
	private static void terms(ServerLevel level, ServerPlayer p, PathfinderMob guard, Faction f) {
		boolean owes = Theft.wanted(p, f.id()) || Reputation.get(p, f.id()) <= Reputation.HOSTILE;
		long milli = Money.worth(FINE);
		String fined = "fine|" + f.id();
		Component words = Component.translatable("speech.jaymoskingdoms.whiteflag.terms." + (owes ? "owed" : "clear"), f.name());
		List<Menus.Choice> choices = new ArrayList<>();
		boolean fineDue = owes && level.getGameTime() - wait(p, fined) >= FINE_GAP;
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.whiteflag.pay", Money.text(f, milli)), fineDue && Money.canPay(p, f, milli), sp -> {
			// (asked again as it's picked: the whole fine, or nothing)
			if (level.getGameTime() - wait(sp, fined) < FINE_GAP || !Money.canPay(sp, f, milli) || !Money.charge(sp, f, milli)) {
				return;
			}
			wait(sp, fined, level.getGameTime());
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + FINE));
			Theft.clearWanted(sp, f.id());
			Reputation.change(sp, f, FINE_REP);
			Speech.tell(level, guard, sp, Component.translatable("speech.jaymoskingdoms.whiteflag.paid." + level.getRandom().nextInt(LINES)), Speech.GOLD);
		}));
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.whiteflag.give_up"), owes, sp -> {
			if (!Busts.giveUp(level, sp, f, guard)) {
				Theft.clearWanted(sp, f.id());
				Speech.tell(level, guard, sp, Component.translatable("speech.jaymoskingdoms.whiteflag.let_go." + level.getRandom().nextInt(LINES)), Speech.WHITE);
			}
		}));
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.whiteflag.go"), sp -> Speech.tell(level, guard, sp,
			Component.translatable("speech.jaymoskingdoms.whiteflag.gone." + level.getRandom().nextInt(LINES)), Speech.WHITE)));
		guard.getLookControl().setLookAt(p, 30.0F, 30.0F);
		Speech.tell(level, guard, p, words, Speech.WHITE);
		Menus.ask(p, guard, words, choices);
	}
}
