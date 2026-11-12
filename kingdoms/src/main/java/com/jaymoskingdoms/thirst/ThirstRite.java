package com.jaymoskingdoms.thirst;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Hemomancy;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Purse;
import com.jaymoskingdoms.Sorcery;
import com.jaymoskingdoms.Speech;
import com.jaymoskingdoms.TalkNet;
import com.jaymoskingdoms.Worker;
import com.jaymoskingdoms.money.Money;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The last rite of the blood arts. A blood witch (a barbarian camp's, {@link Sorcery#BLOOD_WITCH}) gives the thirst, by her own
 * choice, to a Blood Sorcerer deep in the art ({@link #standing}) who brings her what she asks ({@link #WINGS} wings of the night's
 * flyers and {@link #COIN} emeralds' worth of coin). Crouch and use her, and she speaks in two steps: first a warning in oblique words,
 * then her price and the last question. It is not given back after the first few nights ({@link Thirst#cure}). Her ordinary menu
 * (without crouching) is not touched. She does not talk while she has a foe.
 */
public final class ThirstRite {
	// ---- tuning ----
	/** The wings and the coin (emeralds' worth) she asks for. */
	static final int WINGS = 6, COIN = 12;
	/** The corruption a Blood Sorcerer must carry to be deep enough (when Jaymo's Powers keeps it). */
	static final int DEEP = 8;
	/** How near (blocks) they must stay, and how long a question waits (ticks). */
	static final double STAY = 6.0;
	static final long WAIT_TICKS = 1200L;

	/** A question asked: whose, which step (1 the warning, 2 the price), and when it lapses. */
	private record Rite(int witch, int step, long until) {
	}

	private static final Map<UUID, Rite> RITES = new HashMap<>();

	private ThirstRite() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !sp.isShiftKeyDown() || !(entity instanceof LivingEntity witch) || !(entity instanceof Worker w) || w.role() != Sorcery.BLOOD_WITCH
				|| !witch.isAlive() || entity instanceof Mob m && m.getTarget() != null) {
				return InteractionResult.PASS;
			}
			if (Thirst.thirsty(sp) || Thirst.excluded(sp)) {
				Speech.tell(level, witch, sp, Component.translatable("talk.jaymoskingdoms.drinker.rite.nothing"), Speech.RED);
			} else if (!standing(sp)) {
				Speech.tell(level, witch, sp, Component.translatable("talk.jaymoskingdoms.drinker.rite.unready"), Speech.RED);
			} else {
				ask(sp, witch, 1);
			}
			return InteractionResult.SUCCESS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RITES.clear());
	}

	/** Whether {@code player} is deep enough in blood magic: a Blood Sorcerer carrying corruption (or, without Jaymo's Powers, only the art). */
	static boolean standing(Player player) {
		return Hemomancy.bloodMage(player) && (!FabricLoader.getInstance().isModLoaded("jaymospowers") || PowersCompat.corruption(player) >= DEEP);
	}

	private static boolean paid(ServerPlayer player) {
		return Purse.count(player, Arcana.BAT_WING) >= WINGS && Money.canPay(player, Money.worth(COIN));
	}

	private static void ask(ServerPlayer player, LivingEntity witch, int step) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		String base = "talk.jaymoskingdoms.drinker.rite.step" + step;
		Component words = step == 1 ? Component.translatable(base + ".words")
			: Component.translatable(base + ".words", WINGS, Money.text(player, Money.worth(COIN)));
		List<Component> options = List.of(Component.translatable(base + ".yes"), Component.translatable(base + ".no"));
		List<Boolean> enabled = List.of(step == 1 || paid(player), true);
		RITES.put(player.getUUID(), new Rite(witch.getId(), step, player.level().getGameTime() + WAIT_TICKS));
		ServerPlayNetworking.send(player, new TalkNet.Ask(witch.getId(), witch.getDisplayName(), words, options, enabled));
	}

	/** An answer to the witch; false when it was not one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Rite rite = RITES.get(player.getUUID());
		if (rite == null || rite.witch() != answer.entityId()) {
			return false;
		}
		RITES.remove(player.getUUID());
		if (answer.option() != 0 || player.level().getGameTime() > rite.until() || !(player.level() instanceof ServerLevel level)
			|| !(level.getEntity(rite.witch()) instanceof LivingEntity witch) || !witch.isAlive() || witch.distanceTo(player) > STAY) {
			return true;
		}
		if (rite.step() == 1) {
			ask(player, witch, 2);
			return true;
		}
		if (!standing(player) || !paid(player) || Thirst.thirsty(player) || Thirst.excluded(player) || player.isCreative() || player.isSpectator()) {
			Speech.tell(level, witch, player, Component.translatable("talk.jaymoskingdoms.drinker.rite.unready"), Speech.RED);
			return true;
		}
		Money.charge(player, Money.worth(COIN));
		Purse.take(player, Arcana.BAT_WING, WINGS);
		if (Thirst.give(player, false)) {
			Fx.sigil(level, witch.position(), 3.0F, Thirst.BLOOD, 60);
			Fx.blast(level, player.position().add(0.0, 1.0, 0.0), 2.0F, Thirst.BLOOD);
			level.playSound(null, player.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 1.0F, 0.6F);
			Speech.tell(level, witch, player, Component.translatable("talk.jaymoskingdoms.drinker.rite.done"), Speech.RED);
		} else {
			Speech.tell(level, witch, player, Component.translatable("talk.jaymoskingdoms.drinker.rite.unready"), Speech.RED);
		}
		return true;
	}
}
