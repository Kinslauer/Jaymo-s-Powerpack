package com.jaymospowers.client.hud;

import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BodyMenu;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.finisher.Finishers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Andy (2026-11-23): "the player should now be prompted on screen for a stealth kill when eligible, actually, to reduce
 * confusion maybe a low profile floating menu with all the possible controls when in context, for example corpse lifting
 * looting, villager greetings and pickpocketing along with the aforementioned stealth kills". With the crosshair on
 * someone or something you can act on, a small see-through list right of the crosshair ({@link HudZones#INSPECT}, row
 * {@link #ROW}) says what each key does to them now, in {@link MoveHints}' look: "Shift + hold Left Button: stealth kill".
 * Keys are the player's own bindings. It settles for {@link #SETTLE_TICKS} before showing and fades out when the
 * crosshair leaves, so a glance past someone shows nothing.
 * <p>
 * Powers' own lines: someone downed, tied or out cold (revive, drag, the body menu: {@link #body}), the stealth kill
 * (sneaking, behind one {@link Finishers#stealthKillable} who isn't after you) and a body lying loose (Jaymo's
 * Kitchen's {@link CorpseEntity}, an empty hand): loot it with a tap, drag or pick it up with a hold, lift it into your
 * arms with sneak and a hold, or pat out its flames. Jaymo's Kingdoms adds its own (talking, trading, busy, picking a
 * pocket) through {@link #SHARE} in Fabric Loader's object share, as it does for the body menu: an
 * {@code Object[]} of {id, {@code BiFunction<Player, Entity, List<String[]>>}}, each line a {@link #line} array.
 */
public final class ContextHints {
	// ===== the knobs ================================================================================================
	/** The inspect zone's row (under the forms chain, the lens and the scouter). */
	public static final int ROW = 40;
	/** Ticks the same lines must hold before showing, and ticks to fade in or out. */
	static final int SETTLE_TICKS = 4, FADE_TICKS = 6;
	/** The most lines at once. */
	static final int MAX_LINES = 5;
	/** Line height and padding (gui pixels), and the opacity of the whole list (0-1). */
	static final int LINE = 10, PAD = 3;
	static final float OPACITY = 0.8F;
	// ================================================================================================================

	/** Where other mods hand in their lines (see the class note). */
	public static final String SHARE = "jaymos:context_hints";
	/** How a line's keys are worked: pressed once, or held. */
	public static final String TAP = "tap", HOLD = "hold";

	/** Lines from other mods, in the order they came. */
	private static final List<BiFunction<Player, Entity, List<String[]>>> SOURCES = new CopyOnWriteArrayList<>();

	private static String signature = "";
	private static List<String[]> shown = List.of();
	private static int steady;
	private static float fade, lastFade;

	private ContextHints() {
	}

	/**
	 * A line: {@code how} ({@link #TAP} or {@link #HOLD}, the last key held), the lang key of what it does, and the
	 * key bindings pressed together by name ({@code key.sneak}, {@code key.use}).
	 */
	public static String[] line(String how, String action, String... keys) {
		String[] line = new String[keys.length + 2];
		line[0] = how;
		line[1] = action;
		System.arraycopy(keys, 0, line, 2, keys.length);
		return line;
	}

	/** From {@code JaymosPowersClient}. */
	@SuppressWarnings("unchecked")
	public static void init() {
		FabricLoader.getInstance().getObjectShare().put(SHARE, (Consumer<Object[]>) o -> SOURCES.add((BiFunction<Player, Entity, List<String[]>>) o[1]));
		ClientTickEvents.END_CLIENT_TICK.register(ContextHints::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosPowers.id("context_hints"), ContextHints::draw);
	}

	private static void tick(Minecraft minecraft) {
		lastFade = fade;
		LocalPlayer player = minecraft.player;
		List<String[]> now = player == null || minecraft.gui.screen() != null || !player.isAlive() || player.isSpectator()
			|| Finishers.busy(player) || !(minecraft.hitResult instanceof EntityHitResult hit) ? List.of() : lines(player, hit.getEntity());
		StringBuilder sig = new StringBuilder();
		for (String[] line : now) {
			sig.append(String.join(",", line)).append(';');
		}
		steady = sig.toString().equals(signature) ? steady + 1 : 0;
		signature = sig.toString();
		boolean show = !now.isEmpty() && steady >= SETTLE_TICKS;
		if (show) {
			shown = now;
		}
		fade = Mth.clamp(fade + (show ? 1.0F : -1.0F) / FADE_TICKS, 0.0F, 1.0F);
		if (fade <= 0.0F) {
			shown = List.of();
		}
	}

	/** Everything the keys do to {@code target} now (at most {@link #MAX_LINES}). */
	private static List<String[]> lines(LocalPlayer player, Entity target) {
		List<String[]> list = new ArrayList<>();
		if (target == player || !player.isWithinEntityInteractionRange(target, 0.0)) {
			return list;
		}
		if (target instanceof CorpseEntity corpse) {
			corpse(player, corpse, list);
		} else if (BodyMenu.body(target) && player.getMainHandItem().isEmpty()) {
			// someone downed, tied up or out cold: what's done to them is a body's, so the other mods' lines (talk, trade) are left out
			body(player, (LivingEntity) target, list);
			return list.size() > MAX_LINES ? list.subList(0, MAX_LINES) : list;
		} else if (player.isShiftKeyDown() && target instanceof LivingEntity body && Finishers.stealthKillable(player, body)
			&& !(body instanceof Mob mob && mob.isAggressive())) {
			// (the server also asks that they never saw you; a mob that's after you shows it, the rest it keeps to itself)
			list.add(line(HOLD, "jaymospowers.context.stealth_kill", "key.sneak", "key.attack"));
		}
		// Andy (2026-11-26): "make it all empty hand": with something in hand (a fight, most likely) the people lines stay
		// away, though the clicks still work; only the stealth kill, which is the weapon's, shows then
		if (!player.getMainHandItem().isEmpty()) {
			return list;
		}
		for (BiFunction<Player, Entity, List<String[]>> source : SOURCES) {
			try {
				List<String[]> more = source.apply(player, target);
				if (more != null) {
					list.addAll(more);
				}
			} catch (RuntimeException e) {
				// (another mod's lines failed: leave them out this tick)
			}
		}
		return list.size() > MAX_LINES ? list.subList(0, MAX_LINES) : list;
	}

	/** A body lying loose, as Kitchen's {@code CorpseEntity#interact} answers an empty hand. */
	private static void corpse(LocalPlayer player, CorpseEntity corpse, List<String[]> list) {
		if (!player.getMainHandItem().isEmpty() || corpse.carrier() != null || corpse.dragger() != null || corpse.isTumbling() || corpse.falling()) {
			return;
		}
		if (corpse.isOnFire() && corpse.burns()) {
			list.add(line(TAP, "jaymospowers.context.pat_out", "key.use"));
			return;
		}
		// Andy (2026-11-25): a body that can't be opened or has nothing left shows no search line, so nobody taps on and
		// on at it; arrows come out first, a tap each, and a picked-clean skeleton gives up its bones
		boolean animal = !corpse.isFish() && !corpse.isMachine() && corpse.clientBody() instanceof Animal;
		if (handSized(corpse)) {
			// Andy (2026-11-27): "there shouldnt be a lift prompt for something so light you could grab in hand like any
			// other item": a click takes it into the pack (any arrows out of it first), nothing to hold
			list.add(line(TAP, com.jaymoskitchen.archery.StuckArrows.total(corpse.arrows()) > 0
				? "jaymospowers.context.pull_arrow" : "jaymospowers.context.pick_up", "key.use"));
			return;
		}
		if (com.jaymoskitchen.archery.StuckArrows.total(corpse.arrows()) > 0) {
			list.add(line(TAP, "jaymospowers.context.pull_arrow", "key.use"));
		} else if (!animal) {
			int tap = tapDoes(corpse);
			if (tap == CORPSE_LOOT) {
				list.add(line(TAP, "jaymospowers.context.loot", "key.use"));
			} else if (tap == CORPSE_BONE) {
				list.add(line(TAP, "jaymospowers.context.take_bone", "key.use"));
			}
		}
		if (corpse.isHeavy()) {
			return;
		}
		list.add(line(HOLD, corpse.oneHanded() && !corpse.isPlayerCorpse() ? "jaymospowers.context.pick_up" : "jaymospowers.context.drag", "key.use"));
		if (!corpse.tooBigToLift()) {
			list.add(line(HOLD, "jaymospowers.context.lift", "key.sneak", "key.use"));
		}
	}

	/**
	 * Andy (2026-11-26): "a similar box should appear when interacting with npcs, like corpses, that tells you all the
	 * controls, such as ... revive". Someone alive and down, tied up or out cold, as {@code Downed}'s and {@link BodyMenu}'s
	 * clicks answer: a tap opens the body menu (revive, untie, pockets, weapons...), and, downed, a hold drags them and sneak
	 * and a hold brings them round.
	 */
	private static void body(LocalPlayer player, LivingEntity body, List<String[]> list) {
		if (Downed.isDowned(player) || Carry.isCarried(player) || Carry.isCarried(body)) {
			return;
		}
		if (Downed.isDowned(body)) {
			list.add(line(HOLD, "jaymospowers.context.revive", "key.sneak", "key.use"));
			list.add(line(HOLD, "jaymospowers.context.drag", "key.use"));
		}
		list.add(line(TAP, "jaymospowers.context.body_menu", "key.use"));
	}

	/** Whether Kitchen picks this body up like an item ({@code Corpses.SHARE_HAND_SIZED}); an older Kitchen: never. */
	@SuppressWarnings("unchecked")
	private static boolean handSized(CorpseEntity corpse) {
		return FabricLoader.getInstance().getObjectShare().get("jaymoskitchen:corpse_hand_sized") instanceof java.util.function.Predicate<?> f
			&& ((java.util.function.Predicate<Entity>) f).test(corpse);
	}

	/** Kitchen's {@code CorpseEntity.TAP_LOOT} and {@code TAP_BONE}. */
	private static final int CORPSE_LOOT = 1, CORPSE_BONE = 2;

	/** What a tap does to the body now, as Kitchen shares it ({@code Corpses.SHARE_TAP}); an older Kitchen: a search. */
	@SuppressWarnings("unchecked")
	private static int tapDoes(CorpseEntity corpse) {
		return FabricLoader.getInstance().getObjectShare().get("jaymoskitchen:corpse_tap") instanceof java.util.function.ToIntFunction<?> f
			? ((java.util.function.ToIntFunction<Entity>) f).applyAsInt(corpse) : CORPSE_LOOT;
	}

		/** "Shift + hold Right Button: lift": the keys in gold, the last one held if the line says so. */
	private static Component text(String[] line) {
		int gold = HudStyle.GOLD & 0xFFFFFF, dim = HudStyle.DIM & 0xFFFFFF;
		MutableComponent out = Component.empty();
		for (int i = 2; i < line.length; i++) {
			if (i > 2) {
				out.append(Component.literal(" + ").withColor(dim));
			}
			if (i == line.length - 1 && HOLD.equals(line[0])) {
				out.append(Component.translatable("jaymospowers.context.hold").withColor(dim)).append(" ");
			}
			out.append(Component.keybind(line[i]).withColor(gold));
		}
		return out.append(Component.literal(": ").withColor(dim)).append(Component.translatable(line[1]).withColor(HudStyle.TEXT & 0xFFFFFF));
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float visible = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastFade, fade) * OPACITY;
		if (visible <= 0.02F || shown.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		List<Component> lines = new ArrayList<>();
		int width = 0;
		for (String[] line : shown) {
			Component text = text(line);
			lines.add(text);
			width = Math.max(width, minecraft.font.width(text));
		}
		int w = width + PAD * 2, h = lines.size() * LINE + PAD * 2 - 1;
		int[] at = HudZones.place(HudZones.INSPECT, "jaymospowers:context_hints", ROW, w, h);
		if (at == null) {
			return;
		}
		int alpha = Math.round(255.0F * visible);
		graphics.fill(at[0], at[1], at[0] + w, at[1] + h, HudStyle.PLATE_SOFT & 0xFFFFFF | Math.round(0x60 * visible) << 24);
		int y = at[1] + PAD;
		for (Component line : lines) {
			graphics.text(minecraft.font, line, at[0] + PAD, y, alpha << 24 | 0xFFFFFF, true);
			y += LINE;
		}
	}
}
