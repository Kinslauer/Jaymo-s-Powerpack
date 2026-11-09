package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.equestrian.FreeReinClient;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.dragons.DragonBreath;
import com.jaymoskingdoms.dragons.Dragons;
import com.jaymoskingdoms.dragons.WindDragon;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.137) The dragons on the client ({@link Dragons}): their renderers ({@link WindDragonRenderer}); flying a ridden
 * dragon (the rider's client moves it, as with the bat horse's flight in {@code MythicMountsClient}, with the free-rein
 * camera); the breath key; and the HUD (bottom right, right of the hotbar, in Equestrian's place and style): the
 * dragon's stamina while you ride it, and its trust while you're winning it over (only you see it).
 * <p>
 * Flight: Space takes off from the ground and climbs; W flies where you look (the camera under free rein) and keeps its
 * momentum; let go and it glides, sinking slowly; S brakes to a hover; Shift dives (in the air it doesn't dismount,
 * {@code DragonDiveInputMixin}; on the ground it does). An exhausted dragon can only glide down.
 * <p>
 * The breath key is R by default, the same as Jaymo's Powers' first ability key: it's polled (not clicked) so it works
 * whichever mapping the game hands the key to; with a power, R in the saddle also fires the ability (rebind one of
 * them to keep them apart).
 */
public final class DragonsClient {
	/** Climb per tick holding Space, the fastest climb; the hop that takes it off the ground. */
	static final double LIFT = 0.07, MAX_CLIMB = 0.55, TAKE_OFF = 0.75;
	/** Thrust per tick holding W (along the look), the top speed, the top speed diving. */
	static final double THRUST = 0.06, CRUISE = 1.0, MAX_DIVE_SPEED = 1.6;
	/** Gliding: how fast it sinks and the most it sinks; how much way it loses a tick without W. */
	static final double SINK = 0.02, MAX_SINK = 0.3, GLIDE_DRAG = 0.992;
	/** S: the brake (share of speed kept a tick) and the hover's damping of any rise or fall. */
	static final double BRAKE = 0.88, HOVER_DAMP = 0.8;
	/** Shift: how hard it dives and the fastest fall. */
	static final double DIVE = 0.09, MAX_DIVE = 1.4;
	/** Exhausted, it can't keep height: it sinks at least this fast. */
	static final double EXHAUSTED_SINK = 0.15;
	/**
	 * Vanilla's air drag on a mount's motion each tick (horizontal, vertical): the flight undoes it, so the dragon keeps
	 * the momentum it's given.
	 */
	static final double AIR_DRAG = 0.91, AIR_DRAG_Y = 0.98;
	/** The trust meter shows for a dragon this near. */
	static final double TRUST_RANGE = 24.0;
	static final int BAR_WIDTH = 64, CHIP = 20;
	/** The species' shadows (blocks). */
	static final float NOTUS_SHADOW = 2.0F, BOREAS_SHADOW = 1.8F, ZEPHYRUS_SHADOW = 1.8F, EURUS_SHADOW = 1.6F, AEOLUS_SHADOW = 5.0F;
	/** (0.7.326) How big the Fire Dragon model is drawn for each: its back under the riders' seats. */
	static final float NOTUS_SCALE = 0.53F, BOREAS_SCALE = 0.45F;
	/** (0.7.329) How big the Kintsugi wyvern is drawn for each (it stands 7.9 blocks, 25 across the wings), and how far it reaches from its middle at full size. */
	static final float EURUS_SCALE = 0.28F, AEOLUS_SCALE = 0.75F, KINTSUGI_REACH = 13.0F;
	/** (0.7.522) The lesser dragons ({@code docs/SaintsDragons-Plan.md}): Stegonaut's shadow, how big its model is drawn, its reach (head and tail). */
	static final float STEGONAUT_SHADOW = 2.0F, STEGONAUT_SCALE = 1.3F, STEGONAUT_REACH = 4.5F;
	/** (0.7.522) Atroxiia's and Varasuchus's: shadow, drawn size, reach (Atroxiia's neck and tail; Varasuchus's jaws and long tail). */
	static final float ATROXIIA_SHADOW = 2.0F, ATROXIIA_SCALE = 1.15F, ATROXIIA_REACH = 4.5F;
	static final float VARASUCHUS_SHADOW = 2.4F, VARASUCHUS_SCALE = 1.3F, VARASUCHUS_REACH = 9.0F;
	/** (0.7.522) Ignivorus, the Nether's fire dragon on the Kintsugi wyvern: shadow, drawn size. */
	static final float IGNIVORUS_SHADOW = 3.6F, IGNIVORUS_SCALE = 0.55F;
	/** (0.7.522) Aetherion, the star dragon: shadow, drawn size, reach (its wings: about 17 blocks across). */
	static final float AETHERION_SHADOW = 2.8F, AETHERION_SCALE = 1.25F, AETHERION_REACH = 9.0F;
	static final int STAMINA_COLOR = 0xFF9A3A, LOW_COLOR = 0xFFD45A, EXHAUSTED_COLOR = 0xE04040, TRUST_COLOR = 0xFFC86A;

	private static @Nullable KeyMapping breathKey;

	private DragonsClient() {
	}

	public static void register() {
		// both nyxaris' Fire Dragon, drawn and animated by GeckoLib, down to each one's size: Notus's back as high as its
		// saddle, Boreas smaller
		EntityRenderers.register(Dragons.NOTUS_DRAGON, c -> new FireDragonRenderer<>(c, "notus", NOTUS_SHADOW, NOTUS_SCALE));
		EntityRenderers.register(Dragons.BOREAS_DRAGON, c -> new FireDragonRenderer<>(c, "boreas", BOREAS_SHADOW, BOREAS_SCALE));
		EntityRenderers.register(Dragons.ZEPHYRUS_DRAGON, c -> new WindDragonRenderer<>(c, "zephyrus", new ZephyrusDragonModel(ZephyrusDragonModel
			.createBodyLayer().bakeRoot()), ZEPHYRUS_SHADOW));
		// (0.7.329) both eanncrod's Kintsugi wyvern: Eurus small enough to ride, Aeolus vast
		EntityRenderers.register(Dragons.EURUS_DRAGON, c -> new WindDragonRenderer<>(c, "eurus", new KintsugiDragonModel(), EURUS_SHADOW, EURUS_SCALE,
			KINTSUGI_REACH).ownDeath());
		EntityRenderers.register(Dragons.AEOLUS_DRAGON, c -> new WindDragonRenderer<>(c, "aeolus", new KintsugiDragonModel(), AEOLUS_SHADOW, AEOLUS_SCALE,
			KINTSUGI_REACH).ownDeath());
		// (0.7.522) our own model of Saint's Dragons' Stegonaut (its art isn't used)
		EntityRenderers.register(Dragons.STEGONAUT_DRAGON, c -> new WindDragonRenderer<>(c, "stegonaut", new StegonautDragonModel(StegonautDragonModel
			.createBodyLayer().bakeRoot()), STEGONAUT_SHADOW, STEGONAUT_SCALE, STEGONAUT_REACH));
		EntityRenderers.register(Dragons.ATROXIIA_DRAGON, c -> new AtroxiiaDragonRenderer(c, ATROXIIA_SHADOW, ATROXIIA_SCALE,
			ATROXIIA_REACH));
		EntityRenderers.register(Dragons.VARASUCHUS_DRAGON, c -> new WindDragonRenderer<>(c, "varasuchus", new VarasuchusDragonModel(VarasuchusDragonModel
			.createBodyLayer().bakeRoot()), VARASUCHUS_SHADOW, VARASUCHUS_SCALE, VARASUCHUS_REACH));
		// (0.7.522) Ignivorus: the Kintsugi wyvern again, cooled from lava
		EntityRenderers.register(Dragons.IGNIVORUS_DRAGON, c -> new WindDragonRenderer<>(c, "ignivorus", new KintsugiDragonModel(true), IGNIVORUS_SHADOW,
			IGNIVORUS_SCALE, KINTSUGI_REACH).ownDeath());
		EntityRenderers.register(Dragons.AETHERION_DRAGON, c -> new WindDragonRenderer<>(c, "aetherion", new AetherionDragonModel(AetherionDragonModel
			.createBodyLayer().bakeRoot()), AETHERION_SHADOW, AETHERION_SCALE, AETHERION_REACH));
		breathKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.dragon_breath", InputConstants.KEY_R,
			KeyMapping.Category.register(JaymosKingdoms.id("dragons"))));
		ClientTickEvents.END_CLIENT_TICK.register(DragonsClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("dragon"), DragonsClient::hud);
	}

	/** The dragon {@code player} rides and steers. */
	public static @Nullable WindDragon steered(@Nullable Player player) {
		return player != null && player.getVehicle() instanceof WindDragon dragon && dragon.getControllingPassenger() == player ? dragon : null;
	}

	private static boolean breathHeld(Minecraft minecraft) {
		if (breathKey == null || minecraft.gui.screen() != null) {
			return false;
		}
		if (breathKey.isDown()) {
			return true;
		}
		// (polled: the key may be shared with another mod's mapping, which then gets its presses)
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(breathKey);
		return key.getType() == InputConstants.Type.KEYBOARD && key.getValue() != InputConstants.UNKNOWN.getValue()
			&& InputConstants.isKeyDown(key.getValue());
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		WindDragon dragon = steered(player);
		if (dragon == null) {
			return;
		}
		float yaw = FreeReinClient.on() ? FreeReinClient.yaw() : player.getYRot();
		float pitch = FreeReinClient.on() ? FreeReinClient.pitch() : player.getXRot();
		if (dragon.isTamed() && breathHeld(minecraft) && player.tickCount % 2 == 0 && ClientPlayNetworking.canSend(Dragons.Breathe.TYPE)) {
			ClientPlayNetworking.send(new Dragons.Breathe(yaw, pitch));
		}
		if (dragon.swims() && dragon.isInWater() && dragon.isNoGravity()) {
			swim(minecraft, dragon, yaw, pitch);
		} else if (dragon.isNoGravity()) {
			fly(minecraft, dragon, yaw, pitch);
		}
	}

	/** (0.7.522) Swimming a ridden swimmer (Atroxiia, Varasuchus): W where you look, Space up (and out at a bank), Shift down, S brakes. */
	static final double SWIM_THRUST = 0.05, SWIM_DRAG = 0.9, SWIM_RISE = 0.05, SWIM_HOP = 0.5;

	private static void swim(Minecraft minecraft, WindDragon dragon, float yaw, float pitch) {
		boolean up = minecraft.options.keyJump.isDown();
		boolean forward = minecraft.options.keyUp.isDown();
		boolean brake = minecraft.options.keyDown.isDown();
		boolean dive = minecraft.options.keyShift.isDown();
		double top = dragon.swimSpeed() * (dragon.exhausted() ? 0.5 : 1.0);
		Vec3 m = dragon.getDeltaMovement();
		// (undo the drag its own travel put on it this tick: it keeps its way)
		double x = m.x / AIR_DRAG, y = m.y / AIR_DRAG_Y, z = m.z / AIR_DRAG;
		if (forward) {
			Vec3 look = DragonBreath.aim(yaw, pitch);
			x += look.x * SWIM_THRUST;
			y += look.y * SWIM_THRUST;
			z += look.z * SWIM_THRUST;
		} else {
			x *= SWIM_DRAG;
			y *= SWIM_DRAG;
			z *= SWIM_DRAG;
		}
		if (up) {
			y = Math.min(y + SWIM_RISE, top);
		} else if (dive) {
			y = Math.max(y - SWIM_RISE, -top);
		}
		if (brake) {
			x *= BRAKE;
			y *= BRAKE;
			z *= BRAKE;
		}
		double speed = Math.sqrt(x * x + y * y + z * z);
		if (speed > top) {
			x *= top / speed;
			y *= top / speed;
			z *= top / speed;
		}
		if (up && dragon.horizontalCollision) {
			// heaving itself out onto the bank
			y = SWIM_HOP;
		}
		dragon.setDeltaMovement(x, y, z);
	}

	private static void fly(Minecraft minecraft, WindDragon dragon, float yaw, float pitch) {
		// (0.7.141) some fly faster for some riders (Eurus for a Wind user in a gale)
		LocalPlayer rider = minecraft.player;
		double boost = rider == null ? 1.0 : dragon.riderSpeedFactor(rider);
		boolean tired = dragon.exhausted();
		boolean up = minecraft.options.keyJump.isDown() && !tired;
		boolean forward = minecraft.options.keyUp.isDown();
		boolean brake = minecraft.options.keyDown.isDown();
		boolean dive = minecraft.options.keyShift.isDown();
		Vec3 m = dragon.getDeltaMovement();
		if (dragon.onGround()) {
			if (up) {
				dragon.setDeltaMovement(m.x, TAKE_OFF, m.z);
			}
			return;
		}
		// (undo the drag the mount's own movement put on it this tick: momentum)
		double x = m.x / AIR_DRAG, y = m.y / AIR_DRAG_Y, z = m.z / AIR_DRAG;
		if (forward && !tired) {
			Vec3 look = DragonBreath.aim(yaw, pitch);
			x += look.x * THRUST * boost;
			y += look.y * THRUST * boost;
			z += look.z * THRUST * boost;
		} else {
			x *= GLIDE_DRAG;
			z *= GLIDE_DRAG;
		}
		if (up) {
			y = Math.min(y + LIFT, MAX_CLIMB);
		}
		if (brake) {
			x *= BRAKE;
			z *= BRAKE;
			y *= HOVER_DAMP;
		} else if (dive) {
			y = Math.max(y - DIVE, -MAX_DIVE);
		} else if (!up && !forward) {
			y = Math.max(y - SINK, -MAX_SINK);
		}
		if (tired) {
			y = Math.min(y, -EXHAUSTED_SINK);
		}
		double speed = Math.sqrt(x * x + y * y + z * z), top = (dive ? MAX_DIVE_SPEED : CRUISE) * boost;
		if (speed > top) {
			x *= top / speed;
			y *= top / speed;
			z *= top / speed;
		}
		dragon.setDeltaMovement(x, y, z);
	}

	// ---- the HUD -------------------------------------------------------------------------------------------------------

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		if (player.getVehicle() instanceof WindDragon riding) {
			float share = riding.stamina() / WindDragon.STAMINA;
			int color = riding.exhausted() ? pulse(EXHAUSTED_COLOR, 0xFFFFFF, time * 0.5F) : share < WindDragon.RECOVER / WindDragon.STAMINA ? LOW_COLOR
				: STAMINA_COLOR;
			MutableComponent label = riding.getDisplayName().copy().withColor(0xB0B0B0);
			if (riding.exhausted()) {
				label.append(Component.literal("  ")).append(Component.translatable("hud.jaymoskingdoms.dragon_exhausted").withColor(EXHAUSTED_COLOR));
			}
			bar(graphics, minecraft.font, share, color, label, WindDragon.RECOVER / WindDragon.STAMINA);
			return;
		}
		WindDragon wooed = null;
		for (WindDragon dragon : player.level().getEntitiesOfClass(WindDragon.class, player.getBoundingBox().inflate(TRUST_RANGE),
			d -> !d.isTamed() && d.tamerId() == player.getId() && d.trust() > 0.0F)) {
			wooed = dragon;
		}
		if (wooed != null) {
			float share = wooed.trust() / WindDragon.TRUST_FULL;
			Component label = Component.translatable("hud.jaymoskingdoms.dragon_trust", wooed.getDisplayName()).withColor(0xB0B0B0);
			bar(graphics, minecraft.font, share, pulse(TRUST_COLOR, 0xFFFFFF, time * 0.15F), label, -1.0F);
		}
	}

	/** A bar in Equestrian's place (right of the hotbar, over the powers' chips), with its label over it. */
	private static void bar(GuiGraphicsExtractor graphics, Font font, float share, int color, Component label, float mark) {
		// (HUD rework) right dock, row 41 (under the riding bar's row 40 in the stack's order); label above the bar
		int boxW = Math.max(BAR_WIDTH, font.width(label));
		int[] at = HudZones.place(HudZones.DOCK_RIGHT, "jaymoskingdoms:dragon", 41, boxW, 15);
		if (at == null) {
			return;
		}
		int x0 = at[0];
		int y = at[1] + 12;
		share = Mth.clamp(share, 0.0F, 1.0F);
		graphics.fill(x0 - 1, y - 1, x0 + BAR_WIDTH + 1, y + 3, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(BAR_WIDTH * share), y + 2, 0xFF000000 | color);
		if (mark > 0.0F) {
			int markX = x0 + Math.round(BAR_WIDTH * mark);
			graphics.fill(markX, y - 1, markX + 1, y + 3, 0xFF606060);
		}
		graphics.text(font, label, x0, y - 11, 0xE0FFFFFF);
	}

	private static int pulse(int a, int b, float t) {
		float k = (Mth.sin(t) + 1.0F) * 0.5F;
		int r = (int)Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int)Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int)Mth.lerp(k, a & 255, b & 255);
		return r << 16 | g << 8 | bl;
	}
}
