package com.jaymospowers.power.xenoform;

import com.jaymospowers.core.Fate;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The two voices in the band, and the void alien they keep ({@link com.jaymospowers.power.xenoform.forms.Nadir}).
 * Once the power has ascended <i>and</i> the band is corrupted, two voices speak from it: <b>Hush</b>, the still one,
 * and <b>Clamour</b>, the loud one. They never agree. Each asks you a question with a yes or no to it (clickable in
 * chat, or {@code /xenovoice hush yes}); an answer that displeases a voice is thrown back, and it asks again a minute
 * later. When both are content with your first answers they ask a second pair; when both are content with those, each
 * names a deed: Hush wants {@link #PEACE_FORMS} changes run their whole course without a blow struck, Clamour wants
 * {@link #KILLS} lives ended as an alien. With every requirement met, the voices give you Nadir. The trial, once
 * begun, goes on whatever Fate does after: it's kept in {@link XenoformState#TRIAL}, across deaths.
 *
 * <p>Every line is lang {@code voice.jaymospowers.xenoform.<voice>.<what>}.
 */
public final class XenoVoices {
	// ---- tuning ----
	/** The deeds: changes that run their whole course without a blow (Hush), and lives ended as an alien (Clamour). */
	public static final int PEACE_FORMS = 3, KILLS = 12;
	/** How often (ticks) an unanswered or re-asked question is put again, and how often the trial is looked at. */
	public static final int ASK_EVERY = 1200, EVERY = 40;

	public static final String HUSH = "hush", CLAMOUR = "clamour";
	/** What each voice wants to hear at each stage (the questions are written so that both want a yes, and conflict). */
	private static final String WANTED = "yes";

	private static final Map<UUID, Map<String, Long>> LAST_ASKED = new HashMap<>();

	private XenoVoices() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> command(dispatcher));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && player != entity && XenoformPower.formOf(player) != null
				&& !entity.entityTags().contains(XenoformState.MINION_TAG)) {
				killed(player, entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_ASKED.clear());
	}

	private static void command(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("xenovoice")
			.then(Commands.argument("voice", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {HUSH, CLAMOUR}, b))
				.then(Commands.argument("answer", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"yes", "no"}, b))
					.executes(c -> {
						ServerPlayer player = c.getSource().getPlayerOrException();
						String voice = StringArgumentType.getString(c, "voice");
						String answer = StringArgumentType.getString(c, "answer");
						if (!voice.equals(HUSH) && !voice.equals(CLAMOUR) || !answer.equals("yes") && !answer.equals("no")) {
							return 0;
						}
						answer(player, voice, answer);
						return 1;
					}))));
	}

	/** Every {@link #EVERY} ticks as a human: the voices speak when they have something to ask. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % EVERY != 0) {
			return;
		}
		XenoformState.Trial trial = XenoformState.trial(player);
		if (trial.stage() >= 4) {
			return;
		}
		if (trial.stage() == 0) {
			if (!Fate.isAscended(player) || !Fate.isCorrupted(player) || player.isCreative()) {
				return;
			}
			// the voices wake
			trial = trial.stage(1);
			player.setAttached(XenoformState.TRIAL, trial);
			say(player, HUSH, "wake");
			say(player, CLAMOUR, "wake");
			fx(player);
		}
		long now = player.level().getGameTime();
		if (trial.stage() <= 2) {
			for (String voice : new String[] {HUSH, CLAMOUR}) {
				String key = voice + trial.stage();
				if (!trial.answered(key) && due(player, key, now)) {
					ask(player, voice, trial.stage());
				}
			}
		} else if (trial.stage() == 3 && due(player, "deeds", now)) {
			remind(player, trial);
		}
	}

	private static boolean due(ServerPlayer player, String key, long now) {
		Map<String, Long> asked = LAST_ASKED.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
		Long last = asked.get(key);
		if (last != null && now - last < ASK_EVERY) {
			return false;
		}
		asked.put(key, now);
		return true;
	}

	/** A voice puts its question, with the two answers to click. */
	private static void ask(ServerPlayer player, String voice, int stage) {
		MutableComponent line = prefix(voice).append(Component.translatable("voice.jaymospowers.xenoform." + voice + ".ask" + stage)
			.withStyle(voice.equals(HUSH) ? ChatFormatting.ITALIC : ChatFormatting.BOLD));
		line.append(Component.literal("  "));
		line.append(choice(voice, "yes")).append(Component.literal(" ")).append(choice(voice, "no"));
		player.sendSystemMessage(line);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
			voice.equals(HUSH) ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, voice.equals(HUSH) ? 0.6F : 1.4F);
	}

	private static Component choice(String voice, String answer) {
		return Component.translatable("voice.jaymospowers.xenoform.choice." + answer).withStyle(style -> style
			.withColor(answer.equals("yes") ? ChatFormatting.GREEN : ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand("/xenovoice " + voice + " " + answer))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("voice.jaymospowers.xenoform.choice.hover"))));
	}

	/** The player answered {@code voice} (by click or command). */
	static void answer(ServerPlayer player, String voice, String answer) {
		XenoformState.Trial trial = XenoformState.trial(player);
		if (trial.stage() < 1 || trial.stage() > 2) {
			return;
		}
		String key = voice + trial.stage();
		if (trial.answered(key)) {
			say(player, voice, "already");
			return;
		}
		if (!answer.equals(WANTED)) {
			// thrown back: the question stands, and comes again
			say(player, voice, "displeased" + trial.stage());
			LAST_ASKED.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(key, player.level().getGameTime());
			return;
		}
		trial = trial.with(key, answer);
		say(player, voice, "pleased" + trial.stage());
		String other = voice.equals(HUSH) ? CLAMOUR : HUSH;
		if (trial.answered(other + trial.stage())) {
			// both content, and both told what they wanted to hear: on to the next
			say(player, other, "contradiction" + trial.stage());
			trial = trial.stage(trial.stage() + 1);
			if (trial.stage() == 3) {
				say(player, HUSH, "deed");
				say(player, CLAMOUR, "deed");
			}
			fx(player);
		}
		player.setAttached(XenoformState.TRIAL, trial);
	}

	/** A change ran its whole course without a blow: Hush's deed. */
	static void peacefulForm(ServerPlayer player) {
		XenoformState.Trial trial = XenoformState.trial(player);
		if (trial.stage() != 3 || trial.peaceForms() >= PEACE_FORMS) {
			return;
		}
		trial = trial.counts(trial.peaceForms() + 1, trial.kills());
		player.setAttached(XenoformState.TRIAL, trial);
		say(player, HUSH, trial.peaceForms() >= PEACE_FORMS ? "deed_done" : "deed_count", trial.peaceForms(), PEACE_FORMS);
		finishIfDone(player, trial);
	}

	/** A life ended as an alien: Clamour's deed. */
	private static void killed(ServerPlayer player, LivingEntity victim) {
		XenoformState.Trial trial = XenoformState.trial(player);
		if (trial.stage() != 3 || trial.kills() >= KILLS) {
			return;
		}
		trial = trial.counts(trial.peaceForms(), trial.kills() + 1);
		player.setAttached(XenoformState.TRIAL, trial);
		say(player, CLAMOUR, trial.kills() >= KILLS ? "deed_done" : "deed_count", trial.kills(), KILLS);
		finishIfDone(player, trial);
	}

	private static void remind(ServerPlayer player, XenoformState.Trial trial) {
		if (trial.peaceForms() < PEACE_FORMS) {
			say(player, HUSH, "deed_count", trial.peaceForms(), PEACE_FORMS);
		}
		if (trial.kills() < KILLS) {
			say(player, CLAMOUR, "deed_count", trial.kills(), KILLS);
		}
	}

	/** Every requirement met: the voices agree for once, and the void alien is yours. */
	private static void finishIfDone(ServerPlayer player, XenoformState.Trial trial) {
		if (trial.peaceForms() < PEACE_FORMS || trial.kills() < KILLS) {
			return;
		}
		player.setAttached(XenoformState.TRIAL, trial.stage(4));
		say(player, HUSH, "given");
		say(player, CLAMOUR, "given");
		XenoformPower.unlock(player, XenoForms.NADIR);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.8, 1.0, 0.8, 0.4);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	// ---- the voices' lines ----

	private static MutableComponent prefix(String voice) {
		boolean hush = voice.equals(HUSH);
		return Component.translatable("voice.jaymospowers.xenoform." + voice + ".name")
			.withStyle(hush ? ChatFormatting.AQUA : ChatFormatting.DARK_RED, ChatFormatting.BOLD)
			.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
	}

	private static void say(ServerPlayer player, String voice, String what, Object... args) {
		boolean hush = voice.equals(HUSH);
		player.sendSystemMessage(prefix(voice).append(Component.translatable("voice.jaymospowers.xenoform." + voice + "." + what, args)
			.withStyle(hush ? ChatFormatting.ITALIC : ChatFormatting.BOLD)
			.withStyle(hush ? ChatFormatting.WHITE : ChatFormatting.RED)));
	}

	private static void fx(ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.5F);
	}

	/** Whether this player may ever be asked (for the debug command's readout). */
	public static boolean eligible(Player player) {
		return Fate.isAscended(player) && Fate.isCorrupted(player);
	}
}
