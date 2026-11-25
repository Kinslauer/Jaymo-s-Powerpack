package com.jaymospowers.power.zfighter.sky;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.pod.PodLog;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Finding a world through a telescope. Each world whose chart is not read yet still shines somewhere in the night sky (the
 * places the sky draws a read world's light, {@code SkyPlanets}): point a telescope (a spyglass, or Jaymo's Kingdoms' telescope,
 * both in {@link #TELESCOPES}) at that light under the open night sky and keep it in view for {@link #WATCH_TICKS}, and the world is
 * recorded: its chart counts as read, so it is on the pod's map beside the ones bought. {@link #direction} is the one place the
 * sky's geometry lives, for the server's looking and the client's drawing.
 */
public final class PodScope {
	// ---- tuning ----
	/** Right ascension and declination (degrees) of each world's light, by destination index (the Pale Moon to the Verdant World). */
	public static final float[][] RA_DEC = {
		{128.0F, 38.0F}, {205.0F, 24.0F}, {245.0F, 46.0F}, {170.0F, 14.0F}, {190.0F, 62.0F}, {105.0F, 22.0F},
	};
	/** Telescopes in item tag form. */
	public static final TagKey<Item> TELESCOPES = TagKey.create(Registries.ITEM, JaymosPowers.id("telescopes"));
	/** How close (degrees) the view must be to the light, how long (ticks) it must stay there, and how often the view is looked at. */
	static final double AIM_DEGREES = 2.5;
	static final int WATCH_TICKS = 100, LOOK_EVERY = 5;
	/** The sky is dark enough when the sun is this far below the horizon (share of a quarter turn, as {@code SkyPlanets}). */
	static final double DUSK_AT = 0.05;
	/** How far (blocks) the line to the light must run clear of blocks. */
	static final double SEE_RANGE = 160.0;
	/** The best time of night a hint names (ticks of the day): sampled this often between dusk and dawn. */
	static final int HINT_FROM = 13000, HINT_TO = 23000, HINT_STEP = 250;

	/** By player: the world being watched and the looks it has had in a row. */
	private static final Map<UUID, int[]> WATCHING = new HashMap<>();

	private PodScope() {
	}

	/** Called once, from {@code PodTravel.init}. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PodScope::tick);
	}

	/** The turn of the sky from noon (radians): the sun is overhead at 0 and under the world at pi. */
	public static double turn(long clockTime, float partial) {
		return Math.PI * 2.0 * ((Math.floorMod(clockTime, 24000L) - 6000L + partial) / 24000.0);
	}

	/** The direction to {@code world}'s light at {@code turn}: east is +x, up is +y. */
	public static Vec3 direction(int world, double turn) {
		double ra = Math.toRadians(RA_DEC[world][0]), dec = Math.toRadians(RA_DEC[world][1]);
		double x0 = Math.cos(dec) * Math.sin(ra), y0 = Math.cos(dec) * Math.cos(ra), z0 = Math.sin(dec);
		double c = Math.cos(turn), s = Math.sin(turn);
		return new Vec3(x0 * c - y0 * s, x0 * s + y0 * c, z0);
	}

	/** How dark the sky is, 0 (day) to 1, by the sun's depth. */
	public static boolean dark(Level level) {
		return -Math.cos(turn(level.getOverworldClockTime(), 0.0F)) > DUSK_AT && !level.isRaining();
	}

	// ---------------------------------------------------------------- looking

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.overworld().players()) {
			look(server.overworld(), player);
		}
		WATCHING.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void look(ServerLevel level, ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !player.isUsingItem() || !player.getUseItem().is(TELESCOPES) || !dark(level)
			|| !level.canSeeSky(player.blockPosition().above())) {
			WATCHING.remove(player.getUUID());
			return;
		}
		Vec3 look = player.getLookAngle();
		double turn = turn(level.getOverworldClockTime(), 0.0F);
		Vec3 eye = player.getEyePosition();
		int found = -1;
		for (int world = 0; world < Charts.WORLDS && found < 0; world++) {
			if (Charts.read(player, world)) {
				continue;
			}
			Vec3 to = direction(world, turn);
			if (to.y > 0.0 && Math.toDegrees(Math.acos(Mth.clamp(look.dot(to.normalize()), -1.0, 1.0))) <= AIM_DEGREES
				&& level.clip(new ClipContext(eye, eye.add(to.scale(SEE_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				found = world;
			}
		}
		int[] watch = WATCHING.get(player.getUUID());
		if (found < 0) {
			WATCHING.remove(player.getUUID());
			return;
		}
		if (watch == null || watch[0] != found) {
			watch = new int[] {found, 0};
			WATCHING.put(player.getUUID(), watch);
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.3F, 1.8F);
		}
		watch[1] += LOOK_EVERY;
		if (watch[1] < WATCH_TICKS) {
			return;
		}
		WATCHING.remove(player.getUUID());
		if (Charts.mark(player, found)) {
			PodLog.note(player, PodLog.Step.SCOPED);
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
			level.sendParticles(MagicFx.LIGHT, player.getX(), player.getEyeY() + 0.4, player.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.scope.found", Component.translatable("space.jaymospowers.dest." + ZFighterSpace.DESTS[found].key()))
				.withStyle(ChatFormatting.AQUA));
		}
	}

	// ---------------------------------------------------------------- the hint

	/** The compass point (0 north, 1 north-east ... 7 north-west) of a direction. */
	static int compass(Vec3 dir) {
		double bearing = Math.toDegrees(Math.atan2(dir.x, -dir.z));
		return Math.floorMod(Math.round(bearing / 45.0), 8);
	}

	/**
	 * Which part of the sky to search for {@code world}: where its light stands highest in the night (a compass point, low, middling
	 * or high, and early, deep or late in the night), as a line of talk.
	 */
	public static Component hint(int world) {
		double bestHeight = -2.0;
		int bestTime = HINT_FROM;
		Vec3 best = direction(world, turn(HINT_FROM, 0.0F));
		for (int time = HINT_FROM; time <= HINT_TO; time += HINT_STEP) {
			Vec3 dir = direction(world, turn(time, 0.0F));
			if (dir.y > bestHeight) {
				bestHeight = dir.y;
				bestTime = time;
				best = dir;
			}
		}
		if (bestHeight <= 0.0) {
			return Component.translatable("talk.jaymospowers.stardust.hint_below", Component.translatable("space.jaymospowers.dest." + ZFighterSpace.DESTS[world].key()));
		}
		int height = bestHeight < 0.35 ? 0 : bestHeight < 0.7 ? 1 : 2;
		int night = bestTime < 16000 ? 0 : bestTime < 20000 ? 1 : 2;
		return Component.translatable("talk.jaymospowers.stardust.hint", Component.translatable("space.jaymospowers.dest." + ZFighterSpace.DESTS[world].key() + ".light"),
			Component.translatable("talk.jaymospowers.stardust.compass." + compass(best)), Component.translatable("talk.jaymospowers.stardust.height." + height),
			Component.translatable("talk.jaymospowers.stardust.night." + night));
	}
}
