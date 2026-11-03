package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Mana;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * (Andy, 2026-11-01) Thessaly's mana wings: "Thessaly should grow mana wings for users, that allow them to hover, with the wind
 * hover animation, and increases height and speed as they master it. the mana wings animate like angelic wispy 2-3 player wide
 * hands with wings that extend beneath it, and flap gently as they hover."
 * <p>
 * A toggle move from level 1 ({@link SpiritState#WINGS}). While the wings are spread the keeper hovers whenever they are off the
 * ground: the client moves them ({@code client/spirits/ManaWings}, the wind hover's controls: WASD, Jump to rise, Sneak to sink)
 * and Jaymo's Powers' hover sway hangs the body in the air. How high above the ground they can hold ({@link #height}), how fast
 * they drift ({@link #speed}) and rise ({@link #rise}), and how big the hands are ({@link #size}) all grow with the spirit's level
 * (its mastery), and the upkeep falls. Here, on the server: no fall damage while they are spread, the upkeep each second aloft,
 * a little of the spirit's experience for time in the air, and the wings fading (into a slow fall) when the mana runs dry.
 * Like the rest of the spirit, only the attuned see them; everyone sees the keeper float.
 */
public final class ThessalyWings {
	/** Spreading them: mana and cooldown (ticks). */
	static final float SPREAD_COST = 6.0F;
	static final int SPREAD_COOLDOWN = 20;
	/** Mana each second aloft: at level 1 and at the top level. */
	static final float UPKEEP_FIRST = 2.0F, UPKEEP_MASTERED = 0.8F;
	/** Blocks above the ground the wings hold at most: at level 1 and at the top level. */
	public static final double HEIGHT_FIRST = 4.0, HEIGHT_MASTERED = 24.0;
	/** Drift speed (blocks a tick; the wind's hover is 0.55): at level 1 and at the top level. */
	public static final double SPEED_FIRST = 0.22, SPEED_MASTERED = 0.5;
	/** Rise and sink speed (blocks a tick): at level 1 and at the top level. */
	public static final double RISE_FIRST = 0.16, RISE_MASTERED = 0.36;
	/** The hands' scale (1.0 is a little over two player-widths across each): at level 1 and at the top level (three). */
	public static final float SIZE_FIRST = 0.9F, SIZE_MASTERED = 1.35F;
	/** Seconds aloft for each point of the spirit's experience. */
	static final int XP_EVERY_SECONDS = 5;
	/** The slow fall when the wings fade from a lack of mana (ticks). */
	static final int FADE_FALL_TICKS = 100;

	private ThessalyWings() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ThessalyWings::tick);
	}

	/** How far {@code level} is toward mastery (0 at level 1, 1 at {@link Spirits#MAX_LEVEL}). */
	public static float mastery(int level) {
		return Mth.clamp((level - 1) / (float)(Spirits.MAX_LEVEL - 1), 0.0F, 1.0F);
	}

	public static double height(int level) {
		return Mth.lerp(mastery(level), HEIGHT_FIRST, HEIGHT_MASTERED);
	}

	public static double speed(int level) {
		return Mth.lerp(mastery(level), SPEED_FIRST, SPEED_MASTERED);
	}

	public static double rise(int level) {
		return Mth.lerp(mastery(level), RISE_FIRST, RISE_MASTERED);
	}

	public static float size(int level) {
		return Mth.lerp(mastery(level), SIZE_FIRST, SIZE_MASTERED);
	}

	static float upkeep(int level) {
		return Mth.lerp(mastery(level), UPKEEP_FIRST, UPKEEP_MASTERED);
	}

	/** Whether {@code state}'s wings are spread (Thessaly's, out, toggled on). */
	public static boolean spread(SpiritState state) {
		return state != null && state.out() && state.kind() == Spirits.THESSALY && state.toggled(SpiritState.WINGS);
	}

	/** The move: spreads the wings, or folds them away. */
	static boolean cast(ServerPlayer player, SpiritState state, int charge) {
		if (state.toggled(SpiritState.WINGS)) {
			Spirits.toggle(player, SpiritState.WINGS, false);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.wings.off").withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.6F);
			// (folding them costs nothing)
			CastHints.setCost(player, 0.0F);
			return true;
		}
		Spirits.toggle(player, SpiritState.WINGS, true);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.wings.on").withStyle(ChatFormatting.LIGHT_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.4F, 1.6F);
		if (player.getRandom().nextFloat() < HandMoves.MOVE_SAY) {
			SpiritVoice.say(player, "move");
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			SpiritState state = Spirits.state(player);
			if (state == null || !state.toggled(SpiritState.WINGS)) {
				continue;
			}
			if (!spread(state)) {
				// (put away, or no longer Thessaly's: the bit goes with them)
				Spirits.toggle(player, SpiritState.WINGS, false);
				continue;
			}
			// held up by the wings, a fall never builds
			player.fallDistance = 0.0;
			if (player.onGround() || player.isInWater() || player.isPassenger() || player.isSpectator()) {
				continue;
			}
			long now = player.level().getGameTime();
			if ((now + player.getId()) % 20L != 0L) {
				continue;
			}
			if (!player.isCreative() && !Spirits.upkeep(player, upkeep(state.level()))) {
				Spirits.toggle(player, SpiritState.WINGS, false);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.wings.fade").withStyle(ChatFormatting.GRAY));
				// the feathers give way slowly: a drift down, never a drop
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FADE_FALL_TICKS, 0, false, false));
				continue;
			}
			long second = (now + player.getId()) / 20L;
			if (second % XP_EVERY_SECONDS == 0L) {
				Spirits.addXp(player, 1);
			}
			if (second % 3L == 0L) {
				// the soft beat of them, now and then (only the attuned see the wings, but anyone hears the air move)
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.15F, 1.7F);
			}
			Mana.sendSeen((net.minecraft.server.level.ServerLevel)player.level(), Mana.MOTE, player.getX(), player.getY() + 0.9, player.getZ(),
				2, 0.6, 0.3, 0.6, 0.01);
		}
	}
}
