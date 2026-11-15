package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (am-fixer, medieval picks; Armed Mobs' fixer, an idea only) A speakeasy's fixer ({@link Underworld}: one of the den's
 * hands, by the bookie) knows whose palms to grease. Talk to him and he names the realms that think ill of you (or
 * hunt you), up to {@link #SHOWN}, and what he'd want to put it right with each: {@link #BASE} and one more for every
 * {@link #PER_POINTS} of ill will, for up to {@link #MOST} of it mended (and the hunt for you there called off). Once
 * a day for each realm (saved on you, {@link #FIXED}); he can't make a realm love you.
 */
final class Fixers {
	static final String TAG = "jaymoskingdoms.fixer";
	/** The price (emeralds' worth): this, and one for every this much ill will he mends; at most this much mended at once. */
	static final int BASE = 5, PER_POINTS = 10, MOST = 150;
	/** He names at most this many realms; a realm's name is mended once in this long (ticks). */
	static final int SHOWN = 4;
	static final long GAP_TICKS = 24000L;
	static final int LINES = 2;

	/** On a player: realm → when he last mended their name there (game time). Saved. */
	static final AttachmentType<Map<String, Long>> FIXED = AttachmentRegistry.create(JaymosKingdoms.id("fixer_mended"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private Fixers() {
	}

	private static long last(ServerPlayer p, int realm) {
		return p.getAttachedOrElse(FIXED, Map.of()).getOrDefault(Integer.toString(realm), -GAP_TICKS);
	}

	/** Where he stands, beside the bookie at the back of the room. */
	static Vec3 spot(BlockPos hatch) {
		Vec3 bookie = Speakeasy.bookieSpot(hatch);
		return new Vec3(bookie.x + 1.0, bookie.y, bookie.z);
	}

	/** (Underworld) {@code p} talks to the fixer {@code v}. */
	static void open(ServerLevel level, ServerPlayer p, Villager v) {
		MinecraftServer server = level.getServer();
		Faction here = Theft.lawAt(level, v.blockPosition());
		long now = level.getGameTime();
		List<Faction> ill = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type().isRealm() && (Reputation.get(p, f.id()) < Reputation.WELCOME || Theft.wanted(p, f.id()))
				&& now - last(p, f.id()) >= GAP_TICKS) {
				ill.add(f);
			}
		}
		ill.sort(Comparator.comparingInt(f -> Reputation.get(p, f.id())));
		v.getLookControl().setLookAt(p, 30.0F, 30.0F);
		if (ill.isEmpty()) {
			Speech.tell(level, v, p, Component.translatable("speech.jaymoskingdoms.fixer.none." + level.getRandom().nextInt(LINES)), Speech.WHITE);
			return;
		}
		Component words = Component.translatable("speech.jaymoskingdoms.fixer.open." + level.getRandom().nextInt(LINES));
		List<Menus.Choice> choices = new ArrayList<>();
		for (Faction f : ill.subList(0, Math.min(SHOWN, ill.size()))) {
			int mend = mend(p, f);
			long milli = Money.worth(price(mend));
			choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.fixer.mend", f.name(), Money.text(here, milli)),
				Money.canPay(p, here, milli), sp -> fix(level, sp, v, f, here)));
		}
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.fixer.leave"), sp -> {
		}));
		Speech.tell(level, v, p, words, Speech.WHITE);
		Menus.ask(p, v, words, choices);
	}

	/** How much of {@code p}'s ill will with {@code f} he'd mend. */
	private static int mend(ServerPlayer p, Faction f) {
		int rep = Reputation.get(p, f.id());
		return Math.max(0, Math.min(MOST, Reputation.WELCOME - rep));
	}

	private static int price(int mend) {
		return BASE + mend / PER_POINTS;
	}

	private static void fix(ServerLevel level, ServerPlayer p, Villager v, Faction f, @Nullable Faction here) {
		int mend = mend(p, f);
		long milli = Money.worth(price(mend));
		long now = level.getGameTime();
		if (now - last(p, f.id()) < GAP_TICKS || !Money.canPay(p, here, milli) || !Money.charge(p, here, milli)) {
			return;
		}
		Map<String, Long> fixed = new HashMap<>(p.getAttachedOrElse(FIXED, Map.of()));
		fixed.values().removeIf(t -> now - t >= GAP_TICKS);
		fixed.put(Integer.toString(f.id()), now);
		p.setAttached(FIXED, Map.copyOf(fixed));
		if (mend > 0) {
			Reputation.change(p, f, mend);
		}
		Theft.clearWanted(p, f.id());
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 0.9F);
		Speech.tell(level, v, p, Component.translatable("speech.jaymoskingdoms.fixer.done." + level.getRandom().nextInt(LINES), f.name()), Speech.GOLD);
	}
}
