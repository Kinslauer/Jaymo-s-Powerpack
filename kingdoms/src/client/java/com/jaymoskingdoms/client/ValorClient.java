package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Valor;
import com.jaymoskingdoms.Vampires;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.360) Valor on this client ({@link Valor}): the Valor key, \ (tap: the coatings up or down; hold a second: King's Valor),
 * the pool's thin bar over the hotbar while it isn't full or a coating is up, a pulse of colour at the screen's edges when
 * Valor wakes or ranks up, and Senses: the living hidden from sight near you show as pale outlines, beating slowly; from
 * Steeled a shot coming at you glows red; at Unbroken a disguised vampire's cold heartbeat shows blue, seen or not.
 */
public final class ValorClient {
	/** Holding the key this long (ticks) is King's Valor. */
	static final int HOLD_TICKS = 20;
	/** How often Senses look round (ticks); how far a coming shot is seen (blocks), how straight at you (cosine), how fast. */
	static final int SENSE_EVERY = 5;
	static final double SHOT_REACH = 24.0, SHOT_COS = 0.9, SHOT_SPEED = 0.3;
	/** Outline colours: a hidden body, a Valor holder, a coming shot, a vampire. */
	static final int HIDDEN = 0x9FB8D0, HOLDER = 0xE8C860, SHOT = 0xFF4040, VAMPIRE = 0x5A7CFF;
	/** Senses' beat (ticks); the edges' pulse on waking and on a rank (ticks), and how strong. */
	static final float BEAT = 30.0F, PULSE_TICKS = 40.0F, PULSE = 0.7F;
	/** The pool's bar: its size, how far above the hotbar. */
	static final int BAR_W = 80, BAR_H = 2, BAR_ABOVE = 50;

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");
	private static KeyMapping key;
	private static int held = -1;
	private static final Set<Integer> SENSED = new HashSet<>(), SHOTS = new HashSet<>(), VAMPIRES = new HashSet<>();
	/** The last rank seen (-1 without Valor), the pulse left (ticks) and its colour. */
	private static int lastRank = -2;
	private static float pulse;
	private static int pulseColor;

	private ValorClient() {
	}

	public static void register() {
		// (Andy: "move valor to \"; N, the plan's key, is the horse whistle's and Web's mantle's)
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.valor", InputConstants.KEY_BACKSLASH,
			KeyMapping.Category.register(JaymosKingdoms.id("valor"))));
		ClientTickEvents.END_CLIENT_TICK.register(ValorClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("valor"), ValorClient::hud);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			SENSED.clear();
			SHOTS.clear();
			VAMPIRES.clear();
			held = -1;
			lastRank = -2;
			return;
		}
		// the key: a tap toggles, a held second releases King's Valor
		boolean down = key.isDown() && minecraft.gui.screen() == null;
		while (key.consumeClick()) {
			if (held < 0) {
				held = 0;
			}
		}
		if (held >= 0) {
			if (down) {
				held++;
				if (held == HOLD_TICKS) {
					send(1);
				}
			} else {
				if (held < HOLD_TICKS) {
					send(0);
				}
				held = -1;
			}
		}
		// a pulse when it wakes or ranks up
		int rank = Valor.rank(player);
		if (lastRank != -2 && rank > lastRank) {
			Valor.Data data = Valor.of(player);
			pulse = PULSE_TICKS;
			pulseColor = data == null ? HOLDER : data.lookKind().color == 0x0D0D12 ? 0x404050 : data.lookKind().color;
		}
		lastRank = rank;
		if (pulse > 0.0F) {
			pulse--;
		}
		if (player.tickCount % SENSE_EVERY == 0) {
			sense(minecraft, player, rank);
		}
	}

	private static void send(int what) {
		if (ClientPlayNetworking.canSend(Valor.Use.TYPE)) {
			ClientPlayNetworking.send(new Valor.Use(what));
		}
	}

	/** Senses look round: the hidden living near, Valor holders brighter; from Steeled, shots coming; at Unbroken, vampires. */
	private static void sense(Minecraft minecraft, LocalPlayer player, int rank) {
		SENSED.clear();
		SHOTS.clear();
		VAMPIRES.clear();
		int senses = Valor.sensesRank(player);
		if (senses < 0 || player.isSpectator()) {
			return;
		}
		double reach = Valor.sensesReach(player);
		boolean survivalist = Valor.proficientSenses(com.jaymoskingdoms.Classes.classOf(player))
			&& com.jaymoskingdoms.Classes.classOf(player) == com.jaymoskingdoms.Classes.PlayerClass.SURVIVALIST;
		double far = Math.max(reach * (survivalist ? 1.5 : 1.0), SHOT_REACH);
		for (Entity e : minecraft.level.getEntities(player, player.getBoundingBox().inflate(far))) {
			double d2 = e.distanceToSqr(player);
			if (e instanceof Projectile shot) {
				if (rank >= Valor.STEELED && d2 <= SHOT_REACH * SHOT_REACH && shot.getOwner() != player) {
					Vec3 v = shot.getDeltaMovement();
					Vec3 to = player.getEyePosition().subtract(shot.position());
					if (v.length() > SHOT_SPEED && to.lengthSqr() > 1.0E-3 && v.normalize().dot(to.normalize()) > SHOT_COS) {
						SHOTS.add(e.getId());
					}
				}
				continue;
			}
			if (!(e instanceof LivingEntity living) || !living.isAlive() || living instanceof ArmorStand || living.isSpectator()) {
				continue;
			}
			// (Andy) at Unbroken, a disguised vampire's cold heartbeat: seen or not
			if (rank >= Valor.UNBROKEN && Vampires.isVampire(living) && d2 <= reach * reach) {
				VAMPIRES.add(e.getId());
				continue;
			}
			double r = survivalist && living instanceof Animal ? reach * 1.5 : reach;
			if (d2 <= r * r && !player.hasLineOfSight(living)) {
				SENSED.add(e.getId());
			}
		}
	}

	/** Whether {@code entity} glows for this client's Senses. */
	public static boolean sensed(Entity entity) {
		int id = entity.getId();
		return SENSED.contains(id) || SHOTS.contains(id) || VAMPIRES.contains(id);
	}

	/** {@code entity}'s outline colour (sensed): a shot red, a vampire cold blue, a Valor holder gold, else pale, beating slowly. */
	public static int color(Entity entity, float partialTicks) {
		int id = entity.getId();
		if (SHOTS.contains(id)) {
			return ARGB.opaque(SHOT);
		}
		float t = Minecraft.getInstance().level == null ? 0.0F : ((Minecraft.getInstance().level.getGameTime() % 1000L) + partialTicks) / BEAT;
		float k = 0.55F + 0.45F * Mth.sin(t * Mth.TWO_PI);
		int base = VAMPIRES.contains(id) ? VAMPIRE : Valor.has(entity) ? HOLDER : HIDDEN;
		return ARGB.opaque(ARGB.srgbLerp(k, ARGB.scaleRGB(base, 0.45F), base));
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		if (pulse > 0.0F) {
			float k = PULSE * Mth.sin(pulse / PULSE_TICKS * Mth.PI);
			float r = ARGB.red(pulseColor) / 255.0F, g = ARGB.green(pulseColor) / 255.0F, b = ARGB.blue(pulseColor) / 255.0F;
			// (the vignette multiplies the screen down: taking off what the colour lacks tints the edges with it)
			int color = ARGB.colorFromFloat(1.0F, k * (1.0F - r), k * (1.0F - g), k * (1.0F - b));
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
				graphics.guiHeight(), color);
		}
		Valor.Data data = Valor.of(player);
		if (data == null) {
			return;
		}
		float share = Valor.poolShare(player);
		boolean coated = Valor.reinforced(player) || Valor.armed(player);
		if (!coated && share >= 0.999F && !Valor.spent(player)) {
			return;
		}
		int x = graphics.guiWidth() / 2 - BAR_W / 2, y = graphics.guiHeight() - BAR_ABOVE;
		int color = Valor.spent(player) ? 0xFF606060 : (Valor.state(player) & Valor.KINGS) != 0 ? ARGB.opaque(data.soulColor()) : 0xFFB0B8C8;
		Ui.bar(graphics, x, y, BAR_W, BAR_H, share, color);
	}
}
