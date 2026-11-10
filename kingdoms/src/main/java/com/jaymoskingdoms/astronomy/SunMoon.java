package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Moons;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Sun, the Moon and its eight phases: our own take on the Astronomy mod's idea (MIT; none of its code or art).
 * <p>
 * <b>The orbit.</b> The Moon goes round the sky once every eight days (the game's own phases), so it no longer
 * sits opposite the Sun every night. Its angle from the Sun ({@link #elongation}) grows 45 degrees a day: beside the
 * Sun at the new moon (up by day, lost in its glare), a quarter of the sky behind it at the first quarter (high at
 * sunset, down by midnight), opposite at the full moon (up all night), and a quarter ahead at the last quarter (up from
 * midnight until the morning). The game's moon is drawn there through its own sky angle ({@link #addLayers}), on both
 * sides, so what the sky shows, where the moonlight comes from ({@code DarkNights}) and what the server checks agree.
 * A lunar event's moon still rises where a full moon would ({@link #elongation(Level)}).
 * <p>
 * <b>Observing.</b> Watch the Moon steadily for {@link #WATCH_TICKS} under a clear sky and its phase goes in your
 * records ({@link #OBSERVED}). The new moon can't be seen; it is recorded by watching the dark sky on its night. A
 * phase counts once. The scholar journal reads these records.
 * <p>
 * <b>The Sun.</b> Clicking on the Sun before you know Optics leaves you dazzled ({@link #GLARE_TICKS} of Blindness,
 * the same darkening as our other blinding) and, until it passes, you can't read the sky: no stars are found and nothing
 * is recorded. With Optics the click records the Sun instead.
 */
public final class SunMoon {
	/** Degrees the Moon falls behind the Sun each day; a full turn is the game's eight phases. */
	static final float DEGREES_PER_DAY = 360.0F / MoonPhase.COUNT;
	/** The clock time (into a day) at which the Moon's angle is exactly its phase's: midnight. */
	static final long PHASE_CENTRE = 18000L;

	/** Below this many degrees under the horizon the Moon gives no light, and from this many above it gives all of it. */
	static final float MOONLIGHT_FROM_DEG = -3.0F, MOONLIGHT_FULL_DEG = 10.0F;

	/** How often each player's gaze is checked (ticks), and how long they must hold it to record something (ticks). */
	static final int WATCH_EVERY = 5, WATCH_TICKS = 80;
	/** How close the gaze must be to the Moon's middle, and to the Sun's (degrees). */
	static final double MOON_LOOK_DEG = 4.0, SUN_LOOK_DEG = 6.0;
	/** The Moon must be this far above the horizon to be read (degrees). */
	static final double MIN_ALTITUDE_DEG = 2.0;
	/** The dark sky of a new moon: how high to look (degrees), and how far below the horizon the Sun must be. */
	static final double DARK_SKY_LOOK_DEG = 30.0, DARK_SUN_BELOW_DEG = 8.0;
	/** Rain this heavy (0-1) hides the Sun and the Moon, as it does in the sky. */
	static final float RAIN_HIDES = 0.5F;
	/** How far along the line of sight terrain can hide what's looked at (blocks). */
	static final double SIGHT_REACH = 64.0;
	/** Dazzled by the Sun: how long the Blindness lasts (ticks). */
	static final int GLARE_TICKS = 70;

	/** What each player has observed, in the order recorded: {@code moon/<phase>} (the game's phase names) and {@code sun}. */
	public static final AttachmentType<List<String>> OBSERVED = AttachmentRegistry.create(JaymosKingdoms.id("sun_moon_observed"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	public static final String SUN = "sun";

	/** By player: the phase they're watching (its record id) and for how many ticks. */
	private static final Map<UUID, Watch> WATCHING = new HashMap<>();

	private record Watch(String what, int ticks) {
	}

	/** Player to server: they clicked on the Sun. */
	public record Glance() implements CustomPacketPayload {
		public static final Type<Glance> TYPE = new Type<>(JaymosKingdoms.id("sun_glance"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Glance> CODEC = StreamCodec.unit(new Glance());

		@Override
		public Type<Glance> type() {
			return TYPE;
		}
	}

	private SunMoon() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Glance.TYPE, Glance.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Glance.TYPE, (payload, context) -> glanced(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(SunMoon::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
	}

	// ---- the orbit -------------------------------------------------------------------------------------------------------

	/** The Moon's angle east of the Sun (degrees, 0-360) at overworld clock time {@code clock}: 180 at a full moon's midnight. */
	public static float elongation(long clock) {
		return Mth.positiveModulo(180.0F + DEGREES_PER_DAY * ((clock - PHASE_CENTRE) / 24000.0F), 360.0F);
	}

	/** (EnvironmentAttributeSystemBuilderMixin) The Overworld's moon is drawn where its orbit has it, on both sides. */
	public static void addLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		// the game's moon angle is the Sun's plus 180; the Moon trails the Sun by its elongation instead
		builder.addTimeBasedLayer(EnvironmentAttributes.MOON_ANGLE, (angle, tick) -> angle + 180.0F - elongation(level));
	}

	/**
	 * The Moon's elongation in {@code level} now. A lunar event's moon (blood, harvest, blue) rises where a full moon
	 * would, whatever the phase, so it's up all night as it always was; it glides there as the event fades in.
	 */
	static float elongation(Level level) {
		return Mth.lerp(Moons.fadeFor(level), elongation(level.getOverworldClockTime()), 180.0F);
	}

	/** The unit direction (y up) to a body at sky angle {@code degrees}, as the game's sky draws the Sun and the Moon. */
	public static Vec3 direction(float degrees) {
		float rad = degrees * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(rad), Mth.cos(rad), 0.0);
	}

	public static Vec3 sun(Level level, Vec3 at) {
		return direction(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, at));
	}

	public static Vec3 moon(Level level, Vec3 at) {
		return direction(level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, at));
	}

	/** How much of its light the Moon gives at sky angle {@code degrees} (0 once set, 1 once risen). */
	public static float moonlight(float degrees) {
		float altitude = (float) Math.toDegrees(Math.asin(Mth.clamp(Mth.cos(degrees * Mth.DEG_TO_RAD), -1.0F, 1.0F)));
		return Mth.clamp((altitude - MOONLIGHT_FROM_DEG) / (MOONLIGHT_FULL_DEG - MOONLIGHT_FROM_DEG), 0.0F, 1.0F);
	}

	// ---- seeing ----------------------------------------------------------------------------------------------------------

	/** Nothing is read off the sky while dazzled or blinded. */
	public static boolean blinded(Player player) {
		return player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS);
	}

	/** {@code player} has the sky open along {@code dir}, rain isn't hiding it, and it's in the Overworld. */
	static boolean clearSight(Level level, Player player, Vec3 dir) {
		if (level.dimension() != Level.OVERWORLD || level.getRainLevel(1.0F) >= RAIN_HIDES) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		return level.clip(new ClipContext(eye, eye.add(dir.scale(SIGHT_REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
			.getType() == HitResult.Type.MISS;
	}

	/** {@code player} is looking right at the Sun, and nothing is in the way. */
	public static boolean lookingAtSun(Level level, Player player) {
		Vec3 sun = sun(level, player.getEyePosition());
		return sun.y > 0.0 && player.getLookAngle().dot(sun) > Math.cos(Math.toRadians(SUN_LOOK_DEG)) && clearSight(level, player, sun);
	}

	// ---- the Sun ---------------------------------------------------------------------------------------------------------

	/** A click on the Sun: dazzled, unless they know Optics (then it's recorded). */
	private static void glanced(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || blinded(player) || !lookingAtSun(level, player)) {
			return;
		}
		if (knowsOptics(player)) {
			record(player, SUN);
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, GLARE_TICKS, 0, false, false, false));
		WATCHING.remove(player.getUUID());
	}

	/**
	 * Whether {@code player} has learned Optics. The skill belongs to the scholar's astronomy ladder (the scholar journal),
	 * which isn't in yet, so until then no one has.
	 */
	static boolean knowsOptics(ServerPlayer player) {
		return false;
	}

	// ---- the Moon --------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if ((now + player.getId()) % WATCH_EVERY == 0) {
				watch(level, player);
			}
		}
	}

	/**
	 * The phase {@code player} is watching now (its record id), or null: the Moon itself, or on a new moon's night the
	 * dark sky where no Moon shows.
	 */
	private static String watched(ServerLevel level, ServerPlayer player) {
		if (blinded(player)) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		MoonPhase phase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, eye);
		Vec3 look = player.getLookAngle();
		if (phase == MoonPhase.NEW_MOON) {
			boolean dark = sun(level, eye).y < -Math.sin(Math.toRadians(DARK_SUN_BELOW_DEG));
			return dark && look.y > Math.sin(Math.toRadians(DARK_SKY_LOOK_DEG)) && clearSight(level, player, look) ? moonId(phase) : null;
		}
		Vec3 moon = moon(level, eye);
		return moon.y > Math.sin(Math.toRadians(MIN_ALTITUDE_DEG)) && look.dot(moon) > Math.cos(Math.toRadians(MOON_LOOK_DEG))
			&& clearSight(level, player, moon) ? moonId(phase) : null;
	}

	private static void watch(ServerLevel level, ServerPlayer player) {
		String id = watched(level, player);
		if (id == null || observed(player).contains(id)) {
			WATCHING.remove(player.getUUID());
			return;
		}
		Watch was = WATCHING.get(player.getUUID());
		int ticks = (was != null && was.what().equals(id) ? was.ticks() : 0) + WATCH_EVERY;
		if (ticks < WATCH_TICKS) {
			WATCHING.put(player.getUUID(), new Watch(id, ticks));
			return;
		}
		WATCHING.remove(player.getUUID());
		record(player, id);
	}

	static String moonId(MoonPhase phase) {
		return "moon/" + phase.getSerializedName();
	}

	// ---- the records -----------------------------------------------------------------------------------------------------

	/** What {@code player} has observed (empty if nothing). */
	public static List<String> observed(ServerPlayer player) {
		return player.getAttachedOrElse(OBSERVED, List.of());
	}

	/** {@code id} goes in {@code player}'s records, once, with the scratch of a quill for them alone. */
	private static void record(ServerPlayer player, String id) {
		List<String> known = observed(player);
		if (known.contains(id)) {
			return;
		}
		List<String> next = new ArrayList<>(known);
		next.add(id);
		player.setAttached(OBSERVED, List.copyOf(next));
		player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT), SoundSource.PLAYERS,
			player.getX(), player.getEyeY(), player.getZ(), 0.8F, 1.0F, player.getRandom().nextLong()));
	}
}
