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
 * The two voices in the band, and the void alien they lend ({@link com.jaymospowers.power.xenoform.forms.Nadir}).
 * Once the power has ascended <i>and</i> the band is corrupted, two voices speak from it: <b>Hush</b>, the still one,
 * and <b>Clamour</b>, the loud one. They never agree. The trial never ends: it runs in rounds.
 * <ul>
 * <li>A round begins with a pair of conflicting yes-or-no questions, one from each voice (clickable in chat, or
 * {@code /xenovoice hush yes}); each voice wants a yes, so pleasing both means telling each what it wants to hear. An
 * answer that displeases a voice is thrown back and asked again a minute later. Then a second pair. The questions
 * come from {@link #PAIRS} pairs and change from round to round.</li>
 * <li>Then a deed each, also by round: Hush wants changes that run their whole course without a blow
 * ({@link #PEACE_FORMS}) or changes you end by hand before the timer does ({@link #RESTRAINT}); Clamour wants lives
 * ended as an alien ({@link #KILLS}) or knockouts taken ({@link #KNOCKOUTS}).</li>
 * <li>With both deeds done the voices are content: Nadir goes on the dial (the first time) and may be slammed
 * <b>once</b>. The moment that change ends, the next round begins, with the next questions: Nadir stays on the dial
 * but the band refuses it until the voices are content again.</li>
 * </ul>
 * The trial is {@link XenoformState#TRIAL} (kept across deaths) and, once begun, goes on whatever Fate does after.
 * Every line is lang {@code voice.jaymospowers.xenoform.<voice>.<what>}.
 */
public final class XenoVoices {
	// ---- tuning ----
	/** The deeds' goals: Hush's two kinds, Clamour's two kinds. */
	public static final int PEACE_FORMS = 3, RESTRAINT = 3, KILLS = 12, KNOCKOUTS = 2;
	/** How many question pairs the voices have (lang {@code .q0}..); two pairs are asked a round. */
	public static final int PAIRS = 6;
	/** How often (ticks) an unanswered or re-asked question is put again, and how often the trial is looked at. */
	public static final int ASK_EVERY = 1200, EVERY = 40;

	public static final String HUSH = "hush", CLAMOUR = "clamour";
	public static final String PEACE = "peace", RESTRAIN = "restraint", KILL = "kills", KNOCKOUT = "knockouts";
	/** The stage at which the voices are content and Nadir may be slammed once. */
	public static final int CONTENT = 4;
	/** What each voice wants to hear (every question is written so that both want a yes, and they conflict). */
	private static final String WANTED = "yes";

	private static final Map<UUID, Map<String, Long>> LAST_ASKED = new HashMap<>();

	private XenoVoices() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> command(dispatcher));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && player != entity && XenoformPower.formOf(player) != null
				&& !entity.entityTags().contains(XenoformState.MINION_TAG)) {
				deed(player, CLAMOUR, KILL);
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

	// ---- what this round asks ----

	/** Which question pair {@code voice} puts at {@code stage} (1 or 2) of this round. */
	static int question(XenoformState.Trial trial, int stage) {
		return Math.floorMod((trial.round() - 1) * 2 + (stage - 1), PAIRS);
	}

	public static String hushDeed(XenoformState.Trial trial) {
		return trial.round() % 2 == 1 ? PEACE : RESTRAIN;
	}

	public static String clamourDeed(XenoformState.Trial trial) {
		return trial.round() % 2 == 1 ? KILL : KNOCKOUT;
	}

	public static int hushGoal(XenoformState.Trial trial) {
		return hushDeed(trial).equals(PEACE) ? PEACE_FORMS : RESTRAINT;
	}

	public static int clamourGoal(XenoformState.Trial trial) {
		return clamourDeed(trial).equals(KILL) ? KILLS : KNOCKOUTS;
	}

	/** Whether the voices are content right now: Nadir may be slammed. */
	public static boolean content(Player player) {
		return XenoformState.trial(player).stage() >= CONTENT;
	}

	/** Whether this player may ever be asked (for the debug command's readout). */
	public static boolean eligible(Player player) {
		return Fate.isAscended(player) && Fate.isCorrupted(player);
	}

	// ---- the trial's turns ----

	/** Every {@link #EVERY} ticks as a human: the voices speak when they have something to ask. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % EVERY != 0) {
			return;
		}
		XenoformState.Trial trial = XenoformState.trial(player);
		if (trial.stage() >= CONTENT) {
			return;
		}
		if (trial.stage() == 0) {
			if (!eligible(player) || player.isCreative()) {
				return;
			}
			// the voices wake: the first round
			trial = trial.nextRound();
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
					ask(player, voice, trial);
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

	/** A voice puts this round's question, with the two answers to click. */
	private static void ask(ServerPlayer player, String voice, XenoformState.Trial trial) {
		int q = question(trial, trial.stage());
		MutableComponent line = prefix(voice).append(Component.translatable("voice.jaymospowers.xenoform." + voice + ".q" + q)
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
		int q = question(trial, trial.stage());
		if (!answer.equals(WANTED)) {
			// thrown back: the question stands, and comes again
			say(player, voice, "no" + q);
			LAST_ASKED.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(key, player.level().getGameTime());
			return;
		}
		trial = trial.with(key, answer);
		say(player, voice, "yes" + q);
		String other = voice.equals(HUSH) ? CLAMOUR : HUSH;
		if (trial.answered(other + trial.stage())) {
			// both content with their answers, each told the opposite: on to the next
			say(player, other, "contradiction");
			trial = trial.stage(trial.stage() + 1);
			if (trial.stage() == 3) {
				say(player, HUSH, "deed." + hushDeed(trial), hushGoal(trial));
				say(player, CLAMOUR, "deed." + clamourDeed(trial), clamourGoal(trial));
			}
			fx(player);
		}
		player.setAttached(XenoformState.TRIAL, trial);
	}

	/**
	 * A change ended ({@code form}): a knockout, the timer out ({@code timedOut}), or by hand; {@code hurt} says whether a
	 * blow was struck in it. Counts toward the deeds, and spends Nadir.
	 */
	static void formEnded(ServerPlayer player, XenoForm form, boolean knockout, boolean timedOut, boolean hurt) {
		if (form.lent()) {
			spent(player);
			return;
		}
		if (knockout) {
			deed(player, CLAMOUR, KNOCKOUT);
		} else if (timedOut) {
			if (!hurt) {
				deed(player, HUSH, PEACE);
			}
		} else {
			deed(player, HUSH, RESTRAIN);
		}
	}

	/** One step of a deed, if it is the one {@code voice} asked for this round. */
	private static void deed(ServerPlayer player, String voice, String kind) {
		XenoformState.Trial trial = XenoformState.trial(player);
		if (trial.stage() != 3) {
			return;
		}
		boolean hush = voice.equals(HUSH);
		if (!(hush ? hushDeed(trial) : clamourDeed(trial)).equals(kind)) {
			return;
		}
		int goal = hush ? hushGoal(trial) : clamourGoal(trial);
		int count = (hush ? trial.hushCount() : trial.clamourCount()) + 1;
		if (count > goal) {
			return;
		}
		trial = hush ? trial.counts(count, trial.clamourCount()) : trial.counts(trial.hushCount(), count);
		player.setAttached(XenoformState.TRIAL, trial);
		say(player, voice, count >= goal ? "done." + kind : "count." + kind, count, goal);
		if (trial.hushCount() >= hushGoal(trial) && trial.clamourCount() >= clamourGoal(trial)) {
			contented(player, trial);
		}
	}

	private static void remind(ServerPlayer player, XenoformState.Trial trial) {
		if (trial.hushCount() < hushGoal(trial)) {
			say(player, HUSH, "count." + hushDeed(trial), trial.hushCount(), hushGoal(trial));
		}
		if (trial.clamourCount() < clamourGoal(trial)) {
			say(player, CLAMOUR, "count." + clamourDeed(trial), trial.clamourCount(), clamourGoal(trial));
		}
	}

	/** Both deeds done: the voices agree for once. Nadir goes on the dial (the first time) and may be slammed once. */
	private static void contented(ServerPlayer player, XenoformState.Trial trial) {
		player.setAttached(XenoformState.TRIAL, trial.stage(CONTENT));
		boolean first = !XenoformState.band(player).has(XenoForms.NADIR.id);
		say(player, HUSH, first ? "given" : "lent");
		say(player, CLAMOUR, first ? "given" : "lent");
		XenoformPower.unlock(player, XenoForms.NADIR);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.8, 1.0, 0.8, 0.4);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 0.5F);
		com.jaymospowers.core.Shakes.send(player, 1.0F);
	}

	/** A Nadir change ended: it's spent, and the next round begins with the next questions. */
	private static void spent(ServerPlayer player) {
		XenoformState.Trial trial = XenoformState.trial(player);
		if (trial.stage() < CONTENT) {
			return;
		}
		player.setAttached(XenoformState.TRIAL, trial.nextRound());
		LAST_ASKED.remove(player.getUUID());
		say(player, HUSH, "spent");
		say(player, CLAMOUR, "spent");
		fx(player);
	}

	/** The band was slammed on Nadir while the voices aren't content: they say why not. */
	static void refuse(ServerPlayer player) {
		XenoformState.Trial trial = XenoformState.trial(player);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.nadir_refused").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		if (trial.stage() == 3) {
			remind(player, trial);
		} else if (trial.stage() >= 1) {
			say(player, trial.answered(HUSH + trial.stage()) ? CLAMOUR : HUSH, "unanswered");
		}
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
}
