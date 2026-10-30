package com.jaymoskingdoms;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.215) Andy: the floating islands' hermit wizards ({@link Hermits}) love Jaymo's Powers' alchemy and can't wait to talk
 * about it. "Ask about alchemy" in MCA's menu (hermits only) gets an eager answer and a hint at the next thing worth
 * trying, from what the player has already learned ({@link PowersCompat#alchemy}); a player who has started gets the same
 * now and then in a chat, and an excited word when they climb up. Before a player has started, the hermit tells them how.
 * The hints never name numbers or recipes outright: they point, and the Codex teaches.
 */
public final class HermitAlchemy {
	/** MCA's {@code main} menu answer (our dialogue data): "Ask about alchemy". */
	public static final String ANSWER = "jaymoskingdoms_alchemy";
	/** How often a chat with a hermit is about alchemy, for a player who has started. */
	public static final float CHAT_CHANCE = 0.5F;
	/** Variants of each line in the lang file ({@code talk.jaymoskingdoms.hermit_alchemy.<line>.<n>}). */
	static final int EAGER = 6, GREETINGS = 4, TIPS = 10;
	/** Corruption at which the hermit stops everything to worry about it; flux in the player's chunk that worries them. */
	static final int WORRYING_CORRUPTION = 30, WORRYING_FLUX = 300;
	/** Corruption from which the hermit, uneasy, may let slip that some lore only opens to the corrupted. */
	static final int UNEASY_CORRUPTION = 15;
	/** Essences discovered before the hermit stops urging the player to scan more things. */
	static final int WELL_SCANNED = 12;

	/** The path through alchemy, in order: the first one the player hasn't learned is what the hermit hints at. */
	private static final List<String> PATH = List.of("alchemy_basics", "essences", "crucible", "principles", "orichalc", "alembic",
		"everflame", "transmutation", "flux", "cleansing", "infusion", "stability", "runic_amulet", "ichor", "artifice",
		"golemancy", "essence_enchanting", "spellbinding", "ichorium", "ichorcloth", "everflame_lantern", "infused_enchanting", "starlit_lens");

	private HermitAlchemy() {
	}

	/** The answer to "Ask about alchemy": eager words, then the hint that matters most now. */
	public static void answer(ServerLevel level, ServerPlayer player, java.util.function.Consumer<Component> say) {
		PowersCompat.Alchemy alchemy = PowersCompat.alchemy(player);
		RandomSource random = level.getRandom();
		if (!alchemy.started()) {
			say.accept(Component.translatable("talk.jaymoskingdoms.hermit_alchemy.begin." + random.nextInt(3), player.getDisplayName()));
			say.accept(Component.translatable("talk.jaymoskingdoms.hermit_alchemy.hint.alchemy_basics"));
			return;
		}
		say.accept(Component.translatable("talk.jaymoskingdoms.hermit_alchemy.eager." + random.nextInt(EAGER), player.getDisplayName()));
		say.accept(hint(level, player, alchemy, random));
	}

	/** A chat's worth of alchemy (only once the player has started), or null. */
	public static @Nullable Component chat(ServerLevel level, ServerPlayer player, RandomSource random) {
		PowersCompat.Alchemy alchemy = PowersCompat.alchemy(player);
		if (!alchemy.started() || random.nextFloat() >= CHAT_CHANCE) {
			return null;
		}
		return random.nextBoolean() ? hint(level, player, alchemy, random)
			: Component.translatable("talk.jaymoskingdoms.hermit_alchemy.tip." + random.nextInt(TIPS));
	}

	/** An excited word for a player who climbs up having started alchemy (said aloud by {@link Hermits}), or null. */
	public static @Nullable Component greeting(ServerPlayer player, RandomSource random) {
		return PowersCompat.alchemy(player).started()
			? Component.translatable("talk.jaymoskingdoms.hermit_alchemy.greet." + random.nextInt(GREETINGS), player.getDisplayName()) : null;
	}

	/** What the player most needs to hear now: a worry first (corruption, blight underfoot), else the next step. */
	static Component hint(ServerLevel level, ServerPlayer player, PowersCompat.Alchemy alchemy, RandomSource random) {
		if (alchemy.corruption() >= WORRYING_CORRUPTION) {
			// (a hint at the cures, by what they know)
			return Component.translatable("talk.jaymoskingdoms.hermit_alchemy.worry." + (alchemy.knows("cleansing") ? "cures" : "corruption"));
		}
		if (PowersCompat.flux(level, player.blockPosition()) >= WORRYING_FLUX) {
			return Component.translatable("talk.jaymoskingdoms.hermit_alchemy.worry.blight");
		}
		if (alchemy.corruption() >= UNEASY_CORRUPTION && random.nextInt(4) == 0) {
			return Component.translatable("talk.jaymoskingdoms.hermit_alchemy.whispers");
		}
		if (alchemy.essencesSeen() < WELL_SCANNED && random.nextInt(3) == 0) {
			return Component.translatable("talk.jaymoskingdoms.hermit_alchemy.hint.scan_more");
		}
		for (String step : PATH) {
			if (!alchemy.knows(step)) {
				return Component.translatable("talk.jaymoskingdoms.hermit_alchemy.hint." + step);
			}
		}
		return Component.translatable("talk.jaymoskingdoms.hermit_alchemy.mastered");
	}
}
