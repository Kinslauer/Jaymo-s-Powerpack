package com.jaymospowers.client.hud;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Training;
import com.jaymospowers.power.zfighter.ZFighterForms;
import com.jaymospowers.power.zfighter.ZFighterGod;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterTraining;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntFunction;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Andy, of the experience meter (Jaymo's Kingdoms' {@code client.hud.SkillMeter}: the bar that drops in at the top of the
 * screen when a skill gains experience, burns along to the new amount and slides back out): "feed em". Powers' own
 * training shows on the same meter: a power's mastery (the way to its next ability, then through the ranks, Adept to
 * Paragon), and for a Z Fighter the Training Points towards the next stat level and the mastery of each form.
 *
 * <p>All of it is already synced to its own player, so this only watches for what went up. The mods don't depend on each
 * other: gains go on a queue in Fabric Loader's object share ({@link #SHARE}) that the meter empties; without Kingdoms
 * nothing reads it and nothing is queued past {@link #QUEUE_MAX}.
 *
 * <p>These tracks also trickle (a mastery point a minute just for playing, a Training Point every twenty seconds), and a
 * meter that dropped in for each would never leave: a track waits until it has moved {@link #MIN_SHARE} of its bar, or
 * reached a level, and then shows the whole of it at once.
 */
public final class MeterFeed {
	/** The queue the meter reads: each entry {id, colour, highest level, label by level, toast lines by level, from, to}. */
	static final String SHARE = "jaymos:skill_meter";
	static final int QUEUE_MAX = 32;
	/** A track shows once it has moved this share of its bar since it last showed (or reached a level). */
	static final float MIN_SHARE = 0.04F;
	/** After joining, respawning or changing world, changes for this many ticks are the save arriving, not gains. */
	static final int SETTLE_TICKS = 60;
	/** Training Points have no top: the bar is the way to the next stat level, as many times over as he can afford. */
	static final int NO_TOP = 1_000_000;
	/** Training's colour (ki gold). */
	static final int TRAINING_COLOR = 0xF2C230;

	private static @Nullable WeakReference<LocalPlayer> watched;
	private static int settle;
	/** Track id -> where it stands now, and where it stood when it last showed. */
	private static final Map<String, Float> NOW = new HashMap<>(), SHOWN = new HashMap<>();
	private static @Nullable Queue<Object[]> queue;

	private MeterFeed() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(MeterFeed::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			watched = null;
			NOW.clear();
			SHOWN.clear();
			return;
		}
		if (watched == null || watched.get() != player) {
			watched = new WeakReference<>(player);
			settle = SETTLE_TICKS;
		}
		boolean settling = settle > 0;
		if (settling) {
			settle--;
		}
		Power power = Powers.of(player);
		if (power == null) {
			NOW.clear();
			SHOWN.clear();
			return;
		}
		Component name = power.name();
		int unlocked = Mastery.unlocked(player);
		float mastery = unlocked < Mastery.ALL ? unlocked + Math.min(0.999F, Math.max(0.0F, Mastery.progress(player)))
			: Mastery.ALL + Mastery.rank(player) + Math.min(0.999F, Math.max(0.0F, Mastery.rankProgress(player)));
		// (an ability learned and a rank reached have the big fanfare: the meter only flashes for them)
		track(settling, "mastery." + power.id(), power.color(), Mastery.ALL + Training.RANKS, mastery,
			level -> level <= Mastery.ALL ? Component.translatable("hud.jaymospowers.meter.mastery", name)
				: Component.translatable("hud.jaymospowers.meter.rank", name, Training.rankName(level - Mastery.ALL)),
			level -> null);
		if (!ZFighterPower.ID.equals(power.id())) {
			return;
		}
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		track(settling, "zfighter.tp", TRAINING_COLOR, NO_TOP, data.tp() / (float)Math.max(1, ZFighterTraining.statCost(player)),
			level -> Component.translatable("hud.jaymospowers.meter.training", tp()),
			level -> level == 1 ? List.of(Component.translatable("hud.jaymospowers.meter.stat_ready"),
				Component.translatable("hud.jaymospowers.meter.stat_ready.hint", Component.keybind("key.jaymospowers.web_symbiote"))) : null);
		for (int form = 1; form <= ZFighterTraining.FORMS; form++) {
			int f = form;
			form(settling, "zfighter.form." + form, power.color(), data.mastery(form), ZFighterLineage.formKey(player, form),
				() -> current() == null ? 0 : ZFighterTraining.of(current()).mastery(f));
		}
		if (ZFighterGod.unlocked(player)) {
			form(settling, "zfighter.form.god", power.color(), ZFighterGod.mastery(player), ZFighterLineage.formKey(player, ZFighterForms.GOD),
				() -> current() == null ? 0 : ZFighterGod.mastery(current()));
		}
	}

	private static void form(boolean settling, String id, int color, int mastery, String nameKey, java.util.function.IntSupplier live) {
		track(settling, id, color, 1, Math.min(1.0F, mastery / (float)ZFighterTraining.MASTERY_MAX),
			level -> Component.translatable("hud.jaymospowers.meter.form", Component.translatable(nameKey),
				Math.min(100, live.getAsInt() * 100 / ZFighterTraining.MASTERY_MAX)),
			level -> null);
	}

	private static @Nullable LocalPlayer current() {
		return Minecraft.getInstance().player;
	}

	private static int tp() {
		LocalPlayer player = current();
		return player == null ? 0 : ZFighterTraining.of(player).tp();
	}

	/** Notes where track {@code id} stands; once it has gained enough since it last showed, hands the gain to the meter. */
	private static void track(boolean settling, String id, int color, int max, float position, IntFunction<Component> label,
		IntFunction<@Nullable List<Component>> toast) {
		NOW.put(id, position);
		Float shown = SHOWN.get(id);
		// (first sight, the save arriving, or something spent or reset: start again from here)
		if (settling || shown == null || position < shown) {
			SHOWN.put(id, position);
			return;
		}
		if (position - shown < MIN_SHARE && Math.floor(position) <= Math.floor(shown)) {
			return;
		}
		SHOWN.put(id, position);
		Queue<Object[]> q = queue();
		if (q.size() < QUEUE_MAX) {
			q.add(new Object[] {id, color, max, label, toast, shown, position});
		}
	}

	@SuppressWarnings("unchecked")
	private static Queue<Object[]> queue() {
		if (queue == null) {
			Object shared = FabricLoader.getInstance().getObjectShare().get(SHARE);
			if (shared == null) {
				FabricLoader.getInstance().getObjectShare().putIfAbsent(SHARE, new ConcurrentLinkedQueue<Object[]>());
				shared = FabricLoader.getInstance().getObjectShare().get(SHARE);
			}
			queue = (Queue<Object[]>)shared;
		}
		return queue;
	}
}
