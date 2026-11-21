package com.jaymospowers.power.zfighter;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.HudBar;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The legendary line's heart. In a legendary form (never as the Great Ape) the rage is a <b>heat</b> (0-{@link #FULL})
 * that everything moving him fills: running ({@link #MOVE_GAIN} a block), landing from a height ({@link #LAND_GAIN} a
 * block fallen, from {@link #LAND_FROM} up) and above all being hurt ({@link #HIT_GAIN} a point of harm). Mastery of the
 * step he is in slows the fill. Full, it boils over: the rage's surge ({@link ZFighterLegendary#surge}), friend or foe, and
 * the heat is gone. Out of a legendary form the heat stops filling and cools {@link #COOL} a tick.
 * <p>
 * The heat is also what his line's moves burn ({@link ZFighterLegendMoves}), on sprint (on foot) or sneak with the four keys, so no
 * plain key changes:
 * <ul>
 * <li>Sprint + R: Cone Burst. Sneak + R tapped (let go within {@link #DIAL_TAP} ticks): the <b>dial</b>, how much of the
 * heat each move pours in (a quarter, a half, all of it). Sneak + R held still charges ki (lending ki needs it).</li>
 * <li>Sprint + G: Blast Dash. Sneak + G: Recoil.</li>
 * <li>Sprint + V: Shell Beam with an empty hand, Charged Throw with something in it. (Sneak + V stays the charged technique.)</li>
 * <li>Sprint + B (held): the Drill. Sneak + B: Ground Crack.</li>
 * <li>Ascended, in the third step, full and sneaking: the Nuke ({@link ZFighterLegendNuke}).</li>
 * </ul>
 * Legend 1 has the heat, the dial, Cone Burst, Blast Dash and Recoil; Legend 2 adds the rest; Legend 3 the Nuke.
 */
final class ZFighterLegendHeart {
	/** The heart's glow, and its core's hot white. */
	static final int GLOW = 0x9CFF3A, CORE = 0xEFFFD0;

	// ---- tuning ----
	/** The heat's top, and what fills it: a block run, a block fallen on landing (from {@link #LAND_FROM} up), a point of harm. */
	static final float FULL = 100.0F, MOVE_GAIN = 0.15F, LAND_GAIN = 1.5F, HIT_GAIN = 3.0F;
	static final double LAND_FROM = 3.0;
	/** Heat lost a tick out of a legendary form. */
	static final float COOL = 0.05F;
	/** The dial's settings (shares of the heat a move spends). */
	static final float[] DIAL = {0.25F, 0.5F, 1.0F};
	/** Sneak + R let go within this many ticks turns the dial (held longer it is only ki charging). */
	static final int DIAL_TAP = 6;
	/** A move's strength from what it spent: {@code STRENGTH_BASE + spent / STRENGTH_PER}. */
	static final float STRENGTH_BASE = 0.5F, STRENGTH_PER = 40.0F;
	/** The gauge on the action bar every this many ticks; sparks from this share of full. */
	static final int HUD_EVERY = 10;
	static final float SPARK_FROM = 0.8F;

	static final class Heart {
		float heat;
		int dial = 1;
		double fallen;
		Vec3 last;
	}

	private static final Map<UUID, Heart> HEARTS = new HashMap<>();
	/** When a sneak + R went down (the dial's tap). */
	private static final Map<UUID, Long> DIAL_PRESS = new HashMap<>();
	/** The keys (bits by ordinal) whose press the heart took, so their release is its too. */
	private static final Map<UUID, Integer> TAKEN = new HashMap<>();

	private ZFighterLegendHeart() {
	}

	/** Called once from {@link ZFighterLegendary#init}. */
	static void init() {
		ZFighterLegendMoves.init();
		ZFighterLegendNuke.init();
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && Powers.has(player, ZFighterPower.INSTANCE)) {
				ZFighterPower.Live live = ZFighterPower.live(player);
				if (active(player, live)) {
					add(player, live, heart(player), taken * HIT_GAIN);
				}
			}
		});
	}

	static void clear() {
		HEARTS.clear();
		DIAL_PRESS.clear();
		TAKEN.clear();
		ZFighterLegendMoves.clear();
		ZFighterLegendNuke.clear();
	}

	/** Everything of his: a logout, a death, the power gone. */
	static void forget(ServerPlayer player) {
		HEARTS.remove(player.getUUID());
		downed(player);
	}

	/** What was running ends (the drill, the wind-up, held keys); the heat stays. */
	static void downed(ServerPlayer player) {
		DIAL_PRESS.remove(player.getUUID());
		TAKEN.remove(player.getUUID());
		ZFighterLegendMoves.forget(player);
		ZFighterLegendNuke.forget(player);
	}

	static Heart heart(ServerPlayer player) {
		return HEARTS.computeIfAbsent(player.getUUID(), id -> new Heart());
	}

	/** In a legendary form, and not the Great Ape. */
	static boolean active(ServerPlayer player, ZFighterPower.Live live) {
		return ZFighterLegendary.legend(live.form) && !ZFighterLineage.ape(player);
	}

	/** The step 1-3 of the line he is in. */
	static int step(ZFighterPower.Live live) {
		return ZFighterLegendary.chain(live.form);
	}

	/** Heat in, slower the more of this step he has mastered. */
	private static void add(ServerPlayer player, ZFighterPower.Live live, Heart heart, float amount) {
		float control = ZFighterTraining.mastery(player, live.form);
		heart.heat = Math.min(FULL, heart.heat + amount / (1.0F + ZFighterLegendary.CONTROL_GAIN * control));
	}

	/** Takes what the dial pours in (at least {@code least}); 0 if there isn't the least. */
	static float spend(ServerPlayer player, float least) {
		Heart heart = heart(player);
		if (player.getAbilities().instabuild) {
			return Math.max(least, FULL * DIAL[heart.dial]);
		}
		if (heart.heat + 1.0E-3F < least) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_legend_heart.low", Math.round(least)).withStyle(ChatFormatting.GRAY));
			return 0.0F;
		}
		float want = Math.min(Math.max(least, heart.heat * DIAL[heart.dial]), heart.heat);
		heart.heat -= want;
		return want;
	}

	/** How much harder a move is for what it spent. */
	static float strength(float spent) {
		return STRENGTH_BASE + spent / STRENGTH_PER;
	}

	/** Whether this step opens a move; if not, says which step does. */
	static boolean opened(ServerPlayer player, ZFighterPower.Live live, int need, String move) {
		if (step(live) >= need) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_legend_heart.locked", Component.translatable(move),
			Component.translatable("form.jaymospowers.zfighter." + (ZFighterForms.LEGEND_1 + need - 1))).withStyle(ChatFormatting.GRAY));
		return false;
	}

	// ---- keys ----

	/**
	 * From {@link ZFighterPower#onKey}, ahead of the ordinary keys: true when the heart took the press (or its release),
	 * so the ordinary move is skipped. Only sprint or sneak presses in a legendary form are its.
	 */
	static boolean key(ServerPlayer player, ZFighterPower.Live live, PowerKey key, boolean down) {
		UUID id = player.getUUID();
		int bit = 1 << key.ordinal();
		if (!down) {
			Long pressed = key == PowerKey.ABILITY_1 ? DIAL_PRESS.remove(id) : null;
			if (pressed != null && active(player, live) && player.level().getGameTime() - pressed < DIAL_TAP) {
				dial(player);
			}
			int taken = TAKEN.getOrDefault(id, 0);
			if ((taken & bit) == 0) {
				return false;
			}
			TAKEN.put(id, taken & ~bit);
			if (key == PowerKey.ABILITY_4) {
				ZFighterLegendMoves.drill(player, false);
			}
			return true;
		}
		boolean sneak = player.isShiftKeyDown();
		// (in ki flight Sprint is the cruise, so the sprint moves are on foot only)
		boolean sprint = player.isSprinting() && !sneak && !live.flying;
		if (!active(player, live) || !sneak && !sprint) {
			return false;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (sneak) {
					// the dial is the tap's: held, it is ki charging as ever
					DIAL_PRESS.put(id, player.level().getGameTime());
					return false;
				}
				ZFighterLegendMoves.cone(player);
			}
			case ABILITY_2 -> {
				if (sneak) {
					ZFighterLegendMoves.recoil(player);
				} else {
					ZFighterLegendMoves.dash(player);
				}
			}
			case ABILITY_3 -> {
				if (sneak) {
					return false;
				}
				if (player.getMainHandItem().isEmpty()) {
					if (opened(player, live, 2, "technique.jaymospowers.zfighter.legend.beam")) {
						ZFighterLegendMoves.beam(player);
					}
				} else if (opened(player, live, 2, "technique.jaymospowers.zfighter.legend.toss")) {
					ZFighterLegendMoves.toss(player);
				}
			}
			case ABILITY_4 -> {
				if (sneak) {
					if (opened(player, live, 2, "technique.jaymospowers.zfighter.legend.crack")) {
						ZFighterLegendMoves.crack(player);
					}
				} else if (opened(player, live, 2, "technique.jaymospowers.zfighter.legend.drill")) {
					ZFighterLegendMoves.drill(player, true);
				}
			}
			default -> {
				return false;
			}
		}
		TAKEN.merge(id, bit, (a, b) -> a | b);
		return true;
	}

	private static void dial(ServerPlayer player) {
		Heart heart = heart(player);
		heart.dial = (heart.dial + 1) % DIAL.length;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.2F + 0.3F * heart.dial);
		hud(player, heart, true);
	}

	// ---- every tick ----

	/** Every tick, per Z Fighter, from {@link ZFighterLegendary#tick}: the heat fills, boils over, or cools. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (!active(player, live)) {
			downed(player);
			Heart heart = HEARTS.get(player.getUUID());
			if (heart != null) {
				heart.last = null;
				heart.fallen = 0.0;
				heart.heat = Math.max(0.0F, heart.heat - COOL);
				if (heart.heat <= 0.0F) {
					HEARTS.remove(player.getUUID());
				}
			}
			return;
		}
		Heart heart = heart(player);
		Vec3 pos = player.position();
		if (heart.last != null && player.onGround() && !player.isPassenger()) {
			double run = Math.sqrt(pos.subtract(heart.last).multiply(1.0, 0.0, 1.0).lengthSqr());
			if (run > 0.05 && run < 2.0) {
				add(player, live, heart, (float)(run * MOVE_GAIN));
			}
		}
		heart.last = pos;
		// landing from a height
		if (player.fallDistance > heart.fallen) {
			heart.fallen = player.fallDistance;
		}
		if (player.onGround() || player.isInWater()) {
			if (heart.fallen >= LAND_FROM && player.onGround()) {
				add(player, live, heart, (float)(heart.fallen * LAND_GAIN));
			}
			heart.fallen = 0.0;
		}
		ZFighterLegendMoves.tick(player, heart);
		if (heart.heat >= FULL) {
			// full: it boils over, unless the Nuke is being wound up
			if (!ZFighterLegendNuke.hold(player, live, heart)) {
				heart.heat = 0.0F;
				ZFighterLegendary.surge(player, live);
			}
			return;
		}
		ZFighterLegendNuke.forget(player);
		if (player.tickCount % HUD_EVERY == 0 && heart.heat > 0.5F) {
			hud(player, heart, false);
		}
		if (heart.heat >= FULL * SPARK_FROM && player.tickCount % 6 == 0) {
			player.level().sendParticles(MagicFx.spark(GLOW, 0.7F), player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 2, 0.2, 0.3, 0.2, 0.05);
		}
	}

	private static void hud(ServerPlayer player, Heart heart, boolean dialed) {
		HudBar.gauge(player, "legend_heat", heart.heat / FULL, dialed ? HudBar.GREY : HudBar.GREEN, Component.translatable("bar.jaymospowers.heat"),
			Component.translatable("bar.jaymospowers.legend_heat", Math.round(heart.heat), Math.round(DIAL[heart.dial] * 100)));
	}
}
