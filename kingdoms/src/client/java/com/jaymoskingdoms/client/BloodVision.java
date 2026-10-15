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

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");
	/**
	 * MCA's Sirben trait screen (a blur that grows toward the edges, tinted red), worn by every vampire player day and
	 * night. MCA (GPL-3.0) ships it in the pack; we only ask for it by name, so without MCA there's none.
	 */
	public static final Identifier SIRBEN = Identifier.fromNamespaceAndPath("mca", "sirben");
	private static final boolean MCA = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mca");

	/** 0 by day, 1 at night; eased. */
	private static float strength, lastStrength;
	private static boolean vampire, on, starving;

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
			strength = lastStrength = 0.0F;
			vampire = on = false;
			return;
		}
		vampire = Vampires.isVampire(player) && !player.isSpectator();
		on = vampire && Vampires.night(minecraft.level.getOverworldClockTime());
		starving = vampire && VampirePlayers.starving(player);
		strength = Mth.clamp(strength + (on ? FADE : -FADE), 0.0F, 1.0F);
	}

	/** The Sirben screen to ask for this frame, or null. */
	public static Identifier sirben() {
		return MCA && vampire ? SIRBEN : null;
	}

	/** The beat now: 0 at rest, 1 at the top of the first thump, a smaller second thump after it. */
	static float beat(float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return 0.0F;
		}
		float period = starving ? STARVING_BEAT : BEAT;
		float t = ((minecraft.level.getGameTime() % 1000L) + partialTicks) % period / period;
		return Math.max(thump(t, 0.0F), 0.6F * thump(t, 0.22F));
	}

	private static float thump(float t, float at) {
		float d = (t - at) / 0.09F;
		return d < 0.0F || d > 1.0F ? 0.0F : Mth.sin(d * Mth.PI);
	}

	/** Whether {@code entity} glows for this client's blood vision. */
	public static boolean sensed(Entity entity) {
		if (NightGiftsClient.revealed(entity) && entity.isAlive()) {
			return true; // (0.7.63) shown by a groundpound or howl, for anyone with the gift
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

	/** The outline colour of a sensed creature right now. */
	public static int color(float partialTicks) {
		float k = beat(partialTicks) * Mth.lerp(partialTicks, lastStrength, strength);
		return ARGB.opaque(ARGB.srgbLerp(k, DIM, BRIGHT));
	}

	/** The red edges: vanilla's vignette pipeline multiplies the screen down; leaving red alone and darkening green and blue tints it blood red. */
	private static void edges(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float s = Mth.lerp(partialTicks, lastStrength, strength);
		if (s <= 0.0F) {
			return;
		}
		float k = s * ((starving ? STARVING_EDGE : EDGE) + EDGE_BEAT * beat(partialTicks));
		int color = ARGB.colorFromFloat(1.0F, 0.15F * k, Math.min(1.0F, k), Math.min(1.0F, k));
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), color);
	}
}
