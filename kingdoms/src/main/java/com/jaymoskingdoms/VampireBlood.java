package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * One body of blood (Night's Blood, patch 1). A full vampire's hunger is blood measured in millibuckets, 0 to
 * {@link #MAX} mB. Andy, on what felt wrong in play: "it doesnt feel as coherent or fluid as any of the other powers";
 * and, on blood flechettes (a later patch): "draw from vampire hunger (blood measured in Mb".
 *
 * <ul>
 * <li>{@link #PER_FOOD} mB for each food point of the old food-bar costs, so every cost converts cleanly: a bite that gave
 * four food points gives 400 mB, a lapped unit, a bottle of Kitchen's blood ({@link #BOTTLE_MB}, one grate level,
 * {@code Butchery.MB_PER_LEVEL}) 250 mB.</li>
 * <li>Kept on the person ({@link #BLOOD}, saved, told only to the vampire themselves) and shown instead of the food bar as a
 * row of dark vials ({@code VampireBloodHud}), the exact mB on hover in the inventory.</li>
 * <li>Bites, the throat grab, lapping, bottles and sleeping prey add to it ({@link #feed}); shapes, the stare, the mask and
 * every move on the skill bar spend it ({@link #pay}). Under {@link #STARVE_BELOW} mB they are starving
 * ({@link #starving}); with none they can do nothing that costs.</li>
 * <li>It runs down a little as time passes ({@link #DRAIN_MB} every {@link #DRAIN_EVERY} ticks: two nights from full to
 * starving, as the old two nights without a bite were), and a little faster in the open sun.</li>
 * <li>What exertion wears off the food bar (sprinting, fighting, mending, blood fever's hunger near people) is taken from
 * the reserve instead, {@link #PER_FOOD} mB a point; a starving vampire's bar is held at {@link #STARVING_FOOD}, so they stop
 * mending.</li>
 * <li>The vanilla food bar is kept full and ignored for a full vampire (ordinary food already gives them nothing:
 * {@code VampireFoodMixin}). A dhampir has none of this: they keep the food bar, and every move is free to them.</li>
 * </ul>
 */
public final class VampireBlood {
	// ---- tuning ----
	/** The reserve's size, what one old food point is worth, and what a freshly turned vampire wakes with (mB). */
	public static final int MAX = 2000, PER_FOOD = 100, START = 1000;
	/** Below this they are starving (mB). */
	public static final int STARVE_BELOW = 200;
	/** A vial of the row (mB): ten of them hold the whole reserve. */
	public static final int VIAL = 200;
	/** What a bottle of Kitchen's blood gives (mB; Kitchen's grate level, {@code Butchery.MB_PER_LEVEL}). */
	public static final int BOTTLE_MB = 250;
	/** The reserve ebbs: this much (mB) every this many ticks (a Nightborn, whose thirst is long, a share of it). */
	static final int DRAIN_MB = 15, DRAIN_EVERY = 400;
	/** In the open sun by day, a second's extra drain (mB; what the sun's old food exhaustion cost). */
	static final int SUN_DRAIN_MB = 1;
	/** The food bar is put back to full this often (ticks). */
	static final int FOOD_EVERY = 4;
	/**
	 * The food bar a starving vampire is held at instead of full: under vanilla's 18, so the body stops mending itself, as it
	 * did when the old food bar ran low.
	 */
	static final int STARVING_FOOD = 17;
	/** At or over this (mB) a vampire counts as nourished (a thrown body breaks what is softer than stone). */
	public static final int NOURISHED = 1600;

	/** The reserve in mB, kept; told to its owner only. */
	public static final AttachmentType<Integer> BLOOD = AttachmentRegistry.create(JaymosKingdoms.id("vampire_blood"),
		builder -> builder.persistent(Codec.INT).initializer(() -> START).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** The food level each full vampire was last put back to, to see how far exertion has worn it down since. */
	private static final Map<UUID, Integer> KEPT_AT = new HashMap<>();

	private VampireBlood() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VampireBlood::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> KEPT_AT.remove(handler.player.getUUID()));
	}

	/** A full vampire: has the reserve and no food bar (a dhampir, a mortal and a werewolf are not); on either side. */
	public static boolean full(Entity e) {
		return e instanceof Player && Vampires.isVampire(e) && !VampirePlayers.dhampir(e);
	}

	/** The reserve in mB (a full vampire's; the maximum for anyone else, who has no need of it); on either side. */
	public static int get(Player player) {
		return full(player) ? Mth.clamp(player.getAttachedOrElse(BLOOD, START), 0, MAX) : MAX;
	}

	/** Whether {@code player} is starving: a full vampire under {@link #STARVE_BELOW} mB. */
	public static boolean starving(Player player) {
		return full(player) && get(player) < STARVE_BELOW;
	}

	/** Whether {@code player} is nourished: a full vampire with {@link #NOURISHED} mB or more, anyone else with sixteen food points. */
	public static boolean nourished(Player player) {
		return full(player) ? get(player) >= NOURISHED : player.getFoodData().getFoodLevel() >= 16;
	}

	private static void set(ServerPlayer player, int mb) {
		int now = Mth.clamp(mb, 0, MAX);
		if (player.getAttachedOrElse(BLOOD, START) != now) {
			player.setAttached(BLOOD, now);
		}
	}

	/** Gives {@code mb} to a full vampire (never past {@link #MAX}). */
	public static void add(ServerPlayer player, int mb) {
		if (full(player)) {
			set(player, get(player) + mb);
		}
	}

	/**
	 * A meal of {@code food} old food points (and {@code saturation}): a full vampire's reserve gains {@link #PER_FOOD} mB for
	 * each; a dhampir's food bar eats it as it always did.
	 */
	public static void feed(ServerPlayer player, int food, float saturation) {
		if (full(player)) {
			add(player, food * PER_FOOD);
		} else {
			player.getFoodData().eat(food, saturation);
		}
	}

	/** A drink of {@code mb} (a bottle's, say): a full vampire's reserve gains it, a dhampir eats {@code food} instead. */
	public static void feedMb(ServerPlayer player, int mb, int food, float saturation) {
		if (full(player)) {
			add(player, mb);
		} else {
			player.getFoodData().eat(food, saturation);
		}
	}

	/** Takes up to {@code mb} out of a full vampire's reserve, with no questions (the stare's second, the mask's, a bat's flight); how much it took. */
	public static int drain(ServerPlayer player, int mb) {
		if (!full(player) || mb <= 0) {
			return 0;
		}
		int had = get(player);
		int took = Math.min(had, mb);
		set(player, had - took);
		return took;
	}

	/** Whether {@code player} could pay {@code mb} (a dhampir, who pays nothing, always can). */
	public static boolean afford(Player player, int mb) {
		return !full(player) || get(player) >= mb;
	}

	/**
	 * Pays {@code mb} for a move: true when it was paid (a dhampir pays nothing), else false with a word above the hotbar and
	 * nothing taken.
	 */
	public static boolean pay(ServerPlayer player, int mb) {
		if (!full(player) || mb <= 0 || player.isCreative()) {
			return true;
		}
		if (get(player) < mb) {
			lack(player);
			return false;
		}
		set(player, get(player) - mb);
		return true;
	}

	/** The word for a move the reserve could not pay for. */
	public static void lack(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood.short").withStyle(ChatFormatting.DARK_RED));
	}

	/** A freshly turned vampire's reserve. */
	static void wake(ServerPlayer player) {
		player.setAttached(BLOOD, START);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!full(player) || !player.isAlive()) {
				continue;
			}
			// the food bar stays full and counts for nothing (saturation none, so the bar's own healing stays the slow kind).
			// What exertion wears off it (sprinting, fighting, mending, the hunger near people) comes out of the reserve
			// instead, a food point's worth of mB for each point lost, as the old food bar paid it.
			if ((now + player.getId()) % FOOD_EVERY == 0) {
				var food = player.getFoodData();
				int kept = KEPT_AT.getOrDefault(player.getUUID(), 20);
				int worn = kept - food.getFoodLevel();
				if (worn > 0 && !player.isCreative() && !player.isSpectator()) {
					drain(player, worn * PER_FOOD);
				}
				int keep = starving(player) ? STARVING_FOOD : 20;
				if (food.getFoodLevel() != keep) {
					food.setFoodLevel(keep);
				}
				KEPT_AT.put(player.getUUID(), keep);
				if (food.getSaturationLevel() > 0.0F) {
					food.setSaturation(0.0F);
				}
			}
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			if ((now + player.getId()) % DRAIN_EVERY == 0) {
				drain(player, Math.max(1, Math.round(DRAIN_MB * VampirePlayers.STARVE_NIGHTS / (float)NightbornBlood.starveNights(player))));
			}
			if ((now + player.getId()) % 20 == 3 && NightGifts.sunDrains(player)) {
				drain(player, SUN_DRAIN_MB);
			}
		}
	}
}
