package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.VampirePlayers;
import com.jaymoskingdoms.Vampires;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * (0.7.24) A vampire player's sight (Andy: "vampirism should give you the sirben visual modifier", MCA's Sirben trait
 * screen, {@link #SIRBEN}, worn always). At night, too, the screen's edges darken to blood red and beat like a heart; every warm-blooded creature near them glows red through
 * walls, beating in time. Starving, the beat quickens, the red deepens, and they smell blood from further off. Only
 * the vampire sees any of it. Undead, golems and armour stands have no blood to see.
 *
 * <p>(0.7.332) Another mortal player in their sight, up to 150 blocks off ({@link com.jaymoskingdoms.BloodFever#HEARTBEAT}),
 * beats for them day or night: the edges pulse red and a heartbeat pounds in their ears, louder and quicker the
 * closer the player is.
 */
public final class BloodVision {
	/** How far they see blood (blocks); further when starving. */
	static final double RANGE = 24.0, STARVING_RANGE = 40.0;
	/** Heartbeat period (ticks), fed and starving. */
	static final float BEAT = 24.0F, STARVING_BEAT = 14.0F;
	/** How much the edges darken (0-1): always, and at the top of a beat; when starving. */
	static final float EDGE = 0.45F, EDGE_BEAT = 0.25F, STARVING_EDGE = 0.65F;
	/** Outline colours: blood at rest and at the top of a beat. */
	static final int DIM = 0x7A0A0A, BRIGHT = 0xE01818;
	/** How fast the sight fades in at dusk and out at dawn (per tick). */
	static final float FADE = 0.02F;
	/** A player in sight: the beat's period face to face (ticks), how much the edges darken at rest and at the top of a beat, and how fast it fades in and out (per tick). */
	static final float SEEN_BEAT = 11.0F, SEEN_EDGE = 0.2F, SEEN_EDGE_BEAT = 0.55F, SEEN_FADE = 0.05F;
	/** The heartbeat's volume at the range's edge and face to face (Andy: "grows to an 80% maximum"), and its pitch. */
	static final float HEART_QUIET = 0.25F, HEART_LOUD = 0.8F, HEART_PITCH = 1.0F;

	/**
	 * (0.7.332) Their favourite's heartbeat ({@link com.jaymoskingdoms.Favourites}): the volume at the range's edge is raised to this (the
	 * maximum, {@link #HEART_LOUD}, stays the cap).
	 */
	static final float FAVOURITE_QUIET = 0.55F;
	/**
	 * (0.7.332) Mood: as surrender rises the red edges darken (this much more red taken from them at a whole surrender) and the
	 * beat's pitch falls to {@link #CURDLED_PITCH}; a restrained vampire (under {@link #RESTRAINED} surrender) gets a warmer,
	 * softer beat ({@link #SOFT_VOLUME} of the volume, edges {@link #SOFT_EDGE} as strong, less green and blue taken off).
	 */
	static final float EDGE_DARKEN = 0.55F, CURDLED_PITCH = 0.6F, RESTRAINED = 25.0F, SOFT_VOLUME = 0.75F, SOFT_EDGE = 0.85F, WARMTH = 0.25F;
	/** (0.7.332) A hurt mortal player within this (blocks) glows red for the vampire, day or night; the pulse's period (ticks). */
	static final double WOUNDED_RANGE = 30.0;
	static final float WOUNDED_PULSE = 14.0F;
	/** (0.7.332) A thrall hears their master: the beat's period (ticks), pitch, and volume at the range's edge and face to face. */
	static final float MASTER_BEAT = 30.0F, MASTER_PITCH = 0.55F, MASTER_QUIET = 0.12F, MASTER_LOUD = 0.45F;

	private static final Identifier VIGNETTE =Identifier.withDefaultNamespace("textures/misc/vignette.png");
	/**
	 * MCA's Sirben trait screen (a blur that grows toward the edges, tinted red), worn by every vampire player day and
	 * night. MCA (GPL-3.0) ships it in the pack; we only ask for it by name, so without MCA there's none.
	 */
	public static final Identifier SIRBEN = Identifier.fromNamespaceAndPath("mca", "sirben");
	private static final boolean MCA = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mca");

	/** 0 by day, 1 at night; eased. */
	private static float strength, lastStrength;
	/** A player in sight (0-1), eased; and the beat's phase (0-1) and period (ticks) now. */
	private static float seen, lastSeen, phase, period = BEAT;
	private static boolean vampire, on, starving;
	/** (0.7.332) Surrender (0-1), and how restrained they are (1 at none, 0 from {@link #RESTRAINED}). */
	private static float surrender, warm;
	/** (0.7.332) A thrall's master's beat: the phase (0-1). */
	private static float masterPhase;

	private BloodVision() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BloodVision::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("blood_vision"), BloodVision::edges);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		lastStrength = strength;
		if (player == null || minecraft.level == null) {
			strength = lastStrength = seen = lastSeen = 0.0F;
			vampire = on = false;
			return;
		}
		thrall(minecraft, player);
		vampire = Vampires.isVampire(player) && !player.isSpectator();
		on = vampire && Vampires.night(minecraft.level.getOverworldClockTime());
		// (0.7.185) a fever past the urge's line runs as hot as starving ({@link com.jaymoskingdoms.BloodFever})
		starving = vampire && (VampirePlayers.starving(player) || com.jaymoskingdoms.BloodFever.burning(player));
		strength = Mth.clamp(strength + (on ? FADE : -FADE), 0.0F, 1.0F);
		lastSeen = seen;
		float target = vampire ? com.jaymoskingdoms.BloodFever.heartbeat(player) : 0.0F;
		seen = target > seen ? Math.min(target, seen + SEEN_FADE) : Math.max(target, seen - SEEN_FADE);
		period = Mth.lerp(seen, starving ? STARVING_BEAT : BEAT, SEEN_BEAT);
		int give = vampire ? com.jaymoskingdoms.BloodFever.surrender(player) : 0;
		surrender = give / 100.0F;
		warm = Mth.clamp(1.0F - give / RESTRAINED, 0.0F, 1.0F);
		phase += 1.0F / period;
		if (phase >= 1.0F) {
			phase -= 1.0F;
			if (seen > 0.05F) {
				// (0.7.332) their favourite's floor is higher; surrender curdles the pitch; a restrained one beats softer
				float quiet = com.jaymoskingdoms.Favourites.seen(player) ? FAVOURITE_QUIET : HEART_QUIET;
				float volume = Mth.lerp(seen, quiet, HEART_LOUD) * Mth.lerp(warm, 1.0F, SOFT_VOLUME);
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, Mth.lerp(surrender, HEART_PITCH, CURDLED_PITCH), Math.min(HEART_LOUD, volume)));
			}
		}
	}

	/** (0.7.332) A thrall (not a vampire) hears their master's heartbeat while the master is in sight: lower and slower than the vampire's own. */
	private static void thrall(Minecraft minecraft, LocalPlayer player) {
		float beat = Vampires.isVampire(player) ? 0.0F : com.jaymoskingdoms.BloodThralls.beat(player);
		if (beat <= 0.0F) {
			masterPhase = 0.0F;
			return;
		}
		masterPhase += 1.0F / MASTER_BEAT;
		if (masterPhase >= 1.0F) {
			masterPhase -= 1.0F;
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, MASTER_PITCH, Mth.lerp(beat, MASTER_QUIET, MASTER_LOUD)));
		}
	}

	/** The Sirben screen to ask for this frame, or null. */
	public static Identifier sirben() {
		return MCA && vampire ? SIRBEN : null;
	}

	/** The beat now: 0 at rest, 1 at the top of the first thump, a smaller second thump after it. */
	static float beat(float partialTicks) {
		if (Minecraft.getInstance().level == null) {
			return 0.0F;
		}
		float t = (phase + partialTicks / period) % 1.0F;
		return Math.max(thump(t, 0.0F), 0.6F * thump(t, 0.22F));
	}

	private static float thump(float t, float at) {
		float d = (t - at) / 0.09F;
		return d < 0.0F || d > 1.0F ? 0.0F : Mth.sin(d * Mth.PI);
	}

	/** Whether {@code entity} glows for this client's blood vision. */
	public static boolean sensed(Entity entity) {
		if (NightGiftsClient.revealed(entity) && entity.isAlive()) {
			return true; // (0.7.66) shown by a groundpound or howl, for anyone with the gift
		}
		if (wounded(entity)) {
			return true; // (0.7.332) a hurt friend glows for them, day or night
		}
		if (strength <= 0.0F || !(entity instanceof LivingEntity living) || !living.isAlive() || living instanceof ArmorStand
			|| living instanceof AbstractGolem || living.is(EntityTypeTags.UNDEAD) || living.isSpectator()) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || entity == player) {
			return false;
		}
		double range = starving ? STARVING_RANGE : RANGE;
		return entity.distanceToSqr(player) < range * range;
	}

	/** (0.7.332) {@code entity} is another mortal player, hurt (a blow just now, or under half health), near enough for a vampire to smell it. */
	private static boolean wounded(Entity entity) {
		if (!vampire || !(entity instanceof net.minecraft.world.entity.player.Player other) || !other.isAlive() || other.isSpectator()
			|| Vampires.isVampire(other)) {
			return false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || other == player || other.distanceToSqr(player) > WOUNDED_RANGE * WOUNDED_RANGE) {
			return false;
		}
		return other.hurtTime > 0 || other.getHealth() < other.getMaxHealth() * 0.5F;
	}

	/** The outline colour of {@code entity} (sensed) right now: a hurt friend pulses red on its own quick beat. */
	public static int color(Entity entity, float partialTicks) {
		if (wounded(entity) && Minecraft.getInstance().level != null) {
			float t = ((Minecraft.getInstance().level.getGameTime() % 1000L) + partialTicks) / WOUNDED_PULSE;
			return ARGB.opaque(ARGB.srgbLerp(0.5F + 0.5F * Mth.sin(t * Mth.TWO_PI), DIM, BRIGHT));
		}
		return color(partialTicks);
	}

	/** The outline colour of a sensed creature right now. */
	public static int color(float partialTicks) {
		float k = beat(partialTicks) * Math.max(Mth.lerp(partialTicks, lastStrength, strength), Mth.lerp(partialTicks, lastSeen, seen));
		return ARGB.opaque(ARGB.srgbLerp(k, DIM, BRIGHT));
	}

	/** The red edges: vanilla's vignette pipeline multiplies the screen down; leaving red alone and darkening green and blue tints it blood red. */
	private static void edges(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float s = Mth.lerp(partialTicks, lastStrength, strength);
		float p = Mth.lerp(partialTicks, lastSeen, seen);
		if (s <= 0.0F && p <= 0.0F) {
			return;
		}
		float b = beat(partialTicks);
		float k = Math.max(s * ((starving ? STARVING_EDGE : EDGE) + EDGE_BEAT * b), p * (SEEN_EDGE + SEEN_EDGE_BEAT * b));
		// (0.7.332) mood: surrender takes more red off the edges (darker, then blacker), and a restrained vampire's are softer and warmer
		k *= Mth.lerp(warm, 1.0F, SOFT_EDGE);
		float red = Math.min(1.0F, (0.15F + EDGE_DARKEN * surrender) * k);
		float green = Math.min(1.0F, k) * Mth.lerp(warm, 1.0F, 1.0F - WARMTH);
		int color = ARGB.colorFromFloat(1.0F, red, green, Math.min(1.0F, k));
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), color);
	}
}
