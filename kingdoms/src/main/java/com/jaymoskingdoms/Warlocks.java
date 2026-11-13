package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import org.jspecify.annotations.Nullable;

/**
 * The Warlock: a pact with a patron (picked with the class and sworn at once) buys their magic in pact slots rather than mana.
 *
 * <ul>
 * <li><b>Pact magic:</b> the pact seal in hand casts the spell it is turned to ({@link CastingFocus}), each cast spending one of
 * {@link #SLOTS} slots, and hits {@link #POWER} harder than a scroll. The slots come back after a short rest ({@link CatchBreath},
 * {@link Rests}).</li>
 * <li><b>The debt:</b> every {@link #CAST_PER_DEBT} casts, the patron asks something back, due within {@link #DEBT_DAYS} days: their
 * <i>favour</i> (spent with the class key), an <i>errand</i> (prayer at their shrine), <i>blood</i> ({@link #BLOOD} health, with the
 * key), or <i>coin</i> ({@link #COIN_SILVER} silver, with the key). While a debt is open the slots are withheld after
 * {@link #GRACE_CASTS} more casts; if it falls overdue, the patron collects: favour lost, the slots spent, a curse on the body.</li>
 * </ul>
 */
public final class Warlocks {
	public static Item SEAL;
	/** Slots, how much harder the magic hits (a share of a scroll's). */
	static final int SLOTS = 2;
	static final float POWER = 1.5F;
	/** Casts between debts, the casts allowed while one is open, the days it is due in. */
	static final int CAST_PER_DEBT = 5, GRACE_CASTS = 3, DEBT_DAYS = 3;
	/** What each debt takes: health, silver, favour. */
	static final float BLOOD = 6.0F;
	static final int COIN_SILVER = 6, FAVOUR_COST = 10;
	/** What a collection takes: favour, and how long the curse lasts (ticks). */
	static final int COLLECT_FAVOUR = 25, CURSE_TICKS = 20 * 60 * 4;
	static final String[] KINDS = {"favour", "errand", "blood", "coin"};

	/** Casts since the last debt (or the open one's), the open debt's kind ("" for none) and the day it is due. */
	record Pact(int casts, String kind, long due) {
		static final Pact NONE = new Pact(0, "", -1L);
		static final Codec<Pact> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("casts", 0).forGetter(Pact::casts),
			Codec.STRING.optionalFieldOf("kind", "").forGetter(Pact::kind),
			Codec.LONG.optionalFieldOf("due", -1L).forGetter(Pact::due)
		).apply(i, Pact::new));

		boolean owing() {
			return !kind.isEmpty();
		}
	}

	static final AttachmentType<Pact> PACT = AttachmentRegistry.create(JaymosKingdoms.id("pact"), b -> b.persistent(Pact.CODEC).copyOnDeath());

	private Warlocks() {
	}

	static void register() {
		SEAL = Arcana.item("pact_seal", p -> new CastingFocus(p.stacksTo(1), false));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(SEAL));
		ServerTickEvents.END_SERVER_TICK.register(Warlocks::tick);
	}

	/** The spell a patron gives their Warlock to begin with besides the arcane one. */
	static String patronSpell(String patron) {
		return switch (patron) {
			case "anvil_father" -> "fire";
			case "deep_mother" -> "fog";
			case "green_huntsman" -> "web";
			case "lantern_warden" -> "darkness";
			case "crowned_sun" -> "draining";
			case "ash_whisperer" -> "silence";
			case "truth" -> "command";
			default -> "charm";
		};
	}

	/** A new Warlock swears to the patron they picked. */
	/** The pact slots {@code player} has (the second class has a share; the Tome path one more). */
	static int slots(ServerPlayer player) {
		return Classes.scaled(player, PlayerClass.WARLOCK, SLOTS) + Paths.extraSlots(player);
	}

	static void begin(ServerPlayer player, String option) {
		Patron patron = Patron.byKey(option);
		if (patron != null && Patrons.of(player) != patron) {
			Patrons.swear(player, patron, Patrons.FAVOUR_DEVOUT, Patrons.today(player.level().getServer()));
		}
		player.removeAttached(PACT);
	}

	static void unmake(ServerPlayer player) {
		player.removeAttached(PACT);
	}

	static Pact pact(ServerPlayer player) {
		return player.getAttachedOrElse(PACT, Pact.NONE);
	}

	/** Whether {@code player} can pay a slot for a cast now. */
	static boolean afford(ServerPlayer player) {
		if (player.isCreative()) {
			return true;
		}
		Pact pact = pact(player);
		if (Rests.left(player, Rests.PACT, slots(player)) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.no_slots"));
			return false;
		}
		if (pact.owing() && pact.casts() >= GRACE_CASTS) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.withheld", debtName(pact.kind())));
			return false;
		}
		return true;
	}

	/** A cast was made: a slot is spent and the patron's account grows. */
	static void pay(ServerPlayer player, String spell) {
		Rests.spend(player, Rests.PACT, 1, slots(player));
		Pact pact = pact(player);
		int casts = pact.casts() + 1;
		if (pact.owing()) {
			player.setAttached(PACT, new Pact(casts, pact.kind(), pact.due()));
			return;
		}
		if (casts < CAST_PER_DEBT) {
			player.setAttached(PACT, new Pact(casts, "", -1L));
			return;
		}
		long today = Patrons.today(player.level().getServer());
		String kind = KINDS[(int) Math.floorMod(today + player.getRandom().nextInt(KINDS.length), KINDS.length)];
		int days = Paths.debtDays(player, DEBT_DAYS);
		player.setAttached(PACT, new Pact(0, kind, today + days));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.debt", debtName(kind), days).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	static Component debtName(String kind) {
		return Component.translatable("message.jaymoskingdoms.warlock.kind." + kind, kind.equals("coin") ? COIN_SILVER : kind.equals("blood") ? (int) BLOOD : FAVOUR_COST);
	}

	/** The class key: settle the debt (favour, blood or coin); with nothing owed, or with sneak held, say how things stand. */
	static void key(ServerPlayer player, boolean sneak) {
		Pact pact = pact(player);
		if (!pact.owing() || sneak) {
			long days = pact.owing() ? Math.max(0L, pact.due() - Patrons.today(player.level().getServer())) : 0L;
			player.sendOverlayMessage(pact.owing() ? Component.translatable("message.jaymoskingdoms.warlock.owing", debtName(pact.kind()), days)
				: Component.translatable("message.jaymoskingdoms.warlock.clear", Rests.left(player, Rests.PACT, slots(player))));
			return;
		}
		boolean paid = switch (pact.kind()) {
			case "favour" -> {
				Patrons.lose(player, FAVOUR_COST);
				yield true;
			}
			case "blood" -> {
				if (player.getHealth() <= BLOOD + 2.0F && !player.isCreative()) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.too_weak"));
					yield false;
				}
				if (!player.isCreative()) {
					player.hurt(player.damageSources().magic(), BLOOD);
				}
				yield true;
			}
			case "coin" -> {
				if (!com.jaymoskingdoms.money.Money.canPay(player, com.jaymoskingdoms.money.Money.worth(COIN_SILVER))) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.no_coin"));
					yield false;
				}
				yield com.jaymoskingdoms.money.Money.charge(player, com.jaymoskingdoms.money.Money.worth(COIN_SILVER));
			}
			default -> {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.errand"));
				yield false;
			}
		};
		if (paid) {
			settled(player);
		}
	}

	private static void settled(ServerPlayer player) {
		player.setAttached(PACT, Pact.NONE);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.settled"));
		if (player.level() instanceof ServerLevel level) {
			Fx.burst(level, player.getX(), player.getY() + 1.0, player.getZ(), 0x8A4FD0, 1.0F, 18, 0.4, 0.7, 0.4);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}

	/** Prayer at {@code shrine}: an errand to the patron's shrine is done. Called from {@link Patrons#prayed}. */
	static void prayed(ServerPlayer player, @Nullable Patron shrine) {
		if (shrine == null || !Classes.is(player, PlayerClass.WARLOCK)) {
			return;
		}
		Pact pact = pact(player);
		if (pact.owing() && pact.kind().equals("errand") && shrine == Patrons.of(player)) {
			settled(player);
		}
	}

	/** A short rest: the slots are back (they are, by {@link Rests}); the patron is quiet for a while. */
	static void rested(ServerPlayer player) {
		if (Classes.is(player, PlayerClass.WARLOCK)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.rested", slots(player)));
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 200 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Classes.is(player, PlayerClass.WARLOCK) || player.isSpectator()) {
				continue;
			}
			Pact pact = pact(player);
			if (pact.owing() && Patrons.today(server) > pact.due()) {
				collect(player);
			}
		}
	}

	/** The patron collects: favour lost, the slots spent, a curse on the body. */
	private static void collect(ServerPlayer player) {
		player.setAttached(PACT, Pact.NONE);
		Patrons.lose(player, COLLECT_FAVOUR);
		Rests.spend(player, Rests.PACT, slots(player), slots(player));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CURSE_TICKS, 0));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 20, 0));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.collected").withStyle(net.minecraft.ChatFormatting.DARK_RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 0.5F);
	}
}
