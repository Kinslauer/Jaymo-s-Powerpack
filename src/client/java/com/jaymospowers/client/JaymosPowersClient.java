package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.power.arsenal.ArsenalPowerClient;
import com.jaymospowers.client.power.chrono.ChronoPowerClient;
import com.jaymospowers.client.power.gravity.GravityPowerClient;
import com.jaymospowers.client.power.web.WebPowerClient;
import com.jaymospowers.client.power.wind.WindPowerClient;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class JaymosPowersClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(JaymosPowers.id("powers"));
	private static final int[] DEFAULT_KEYS = {InputConstants.KEY_R, InputConstants.KEY_G, InputConstants.KEY_V, InputConstants.KEY_B};
	private static final KeyMapping[] ABILITY_KEYS = new KeyMapping[PowerKey.values().length];
	private static KeyMapping menuKey;
	/** The ascended ability's key (C by default): silent until the power ascends. */
	private static KeyMapping ultimateKey;
	private static boolean ultimateWasDown;
	/** The Power Mode key (Z by default): each power's own special (Web's Zip, Wind's modes...). */
	private static KeyMapping modeKey;
	private static boolean modeWasDown;

	private static final boolean[] wasDown = new boolean[PowerKey.values().length];
	/** (0.15.147) Keys the powers screen pressed for you (the abilities, then Z, then C): ticks left held down. */
	private static final int[] tapped = new int[PowerKey.values().length + 2];
	/** Ticks until a key queued by {@link #tapLater} is tapped (0: none queued). */
	private static final int[] tapDelay = new int[PowerKey.values().length + 2];
	public static final int TAP_MODE = PowerKey.values().length, TAP_ULTIMATE = TAP_MODE + 1;
	/** How long a press from the screen holds the key. */
	private static final int TAP_TICKS = 3;
	private static @Nullable Identifier lastPower;
	private static boolean affinityPending;

	@Override
	public void onInitializeClient() {
		// (0.17.67) after every mod's main entrypoint (TaCZ's vanilla registry lookup), for singleplayer worlds
		com.jaymospowers.power.zfighter.ZFighterDemonRealm.addBiomes();
		// (0.15.147) the inventory's tab to the powers screen
		PowerInventoryTab.init();
		for (PowerKey key : PowerKey.values()) {
			ABILITY_KEYS[key.ordinal()] = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.jaymospowers." + key.id(), DEFAULT_KEYS[key.ordinal()], CATEGORY));
		}
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.menu", InputConstants.KEY_H, CATEGORY));
		ultimateKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.ultimate", InputConstants.KEY_C, CATEGORY));
		modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.mode", InputConstants.KEY_Z, CATEGORY));
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.core.BlockCluster.TYPE, BlockClusterRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.core.SeveredPart.TYPE, SeveredPartRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(Dismembered::tick);
		com.jaymospowers.client.blood.BloodClient.init();
		com.jaymospowers.client.wounds.DressingsLayer.register();
		com.jaymospowers.client.finisher.FinisherPose.register();
		com.jaymospowers.client.finisher.FinisherClient.register();
		com.jaymospowers.client.HeldInput.register();
		// (0.18.12) no body in the logout panorama
		com.jaymospowers.client.power.web.FirstPersonCompat.hideInPanoramas();
		LockOnClient.init();
		com.jaymospowers.client.combat.GuardClient.init();
		com.jaymospowers.client.power.zfighter.RaceHoldClient.register();
		com.jaymospowers.client.power.ember.EmberKindleClient.init();
		com.jaymospowers.client.power.ember.FlameAvatarClient.init();
		com.jaymospowers.client.power.heritage.HeritageMovesClient.init();
		com.jaymospowers.client.tinkering.TinkeringClient.init();
		// (0.15.58) the Aether: the rising gravitite block, quicksoil's speed cap
		com.jaymospowers.client.aether.AetherClient.init();
		com.jaymospowers.client.frost.FrostClient.init();
		net.minecraft.client.gui.screens.MenuScreens.register(com.jaymospowers.enchanting.Enchanting.MENU,
			com.jaymospowers.client.enchanting.EnchantingScreen::new);
		// (BWM port) enchanted books say what gear each enchantment goes on
		com.jaymospowers.client.enchanting.EnchantSlotTooltip.init();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.armory.ArmoryEntities.THROWN_WEAPON,
			com.jaymospowers.client.armory.ThrownWeaponRenderer::new);
		// (0.18.47) the clockwork hornets: small brass drones, drawn as their item sprite
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.jaymospowers.alchemy.gear.ClockworkHornets.TYPE,
			context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 0.55F, true));
		// (0.13.7) the bomb glider: its renderer, hold screen and drop keys
		com.jaymospowers.client.armory.BombGliderClient.init();
		// (0.15.148) the flail's chain and ball, the whip's lash
		com.jaymospowers.client.armory.LashRenderer.init();
		// (0.17.98) the warhammer smash swings down from overhead
		com.jaymospowers.client.armory.HammerSlamClient.init();
		// once per install: move other mods' (and vanilla's) defaults off the power keys
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(KeyConflicts::resolveOnce);
		// once per install: switch on the pack's resource packs (medieval MCA villagers, Fresh Animations' player) the
		// CurseForge import downloaded
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(PackResourcePacks::enableOnce);
		// (0.17.56) shaders off and Fast graphics on weak PCs, once per install; /lowend on|off
		LowEndMode.init();
		// (0.15.124) Iris (with a shader pack on) first asks for the enchantment glint textures while a render pass is
		// open, and loading them then crashes the game ("Close the existing render pass"). Load them up front instead.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			for (String glint : new String[] {"textures/misc/enchanted_glint_item.png", "textures/misc/enchanted_glint_armor.png"}) {
				client.getTextureManager().getTexture(Identifier.withDefaultNamespace(glint));
			}
		});

		// Balkon's guns get solid 3D models in hand (an always-on built-in pack, above WeaponMod's own assets)
		net.fabricmc.fabric.api.resource.v1.ResourceLoader.registerBuiltinPack(JaymosPowers.id("held_guns"),
			net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(JaymosPowers.MOD_ID).orElseThrow(),
			net.minecraft.network.chat.Component.literal("Jaymo's Powers: 3D guns"),
			net.fabricmc.fabric.api.resource.v1.pack.PackActivationType.ALWAYS_ENABLED);
		// modded items redrawn in vanilla's style (tools/vanilla_style, docs/Vanilla-Style.md); on by default, and
		// switching it off in Resource Packs shows each mod's own art again
		net.fabricmc.fabric.api.resource.v1.ResourceLoader.registerBuiltinPack(JaymosPowers.id("vanilla_style"),
			net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(JaymosPowers.MOD_ID).orElseThrow(),
			net.minecraft.network.chat.Component.literal("Jaymo's Powers: vanilla-style items"),
			net.fabricmc.fabric.api.resource.v1.pack.PackActivationType.DEFAULT_ENABLED);
		ClientTickEvents.END_CLIENT_TICK.register(JaymosPowersClient::tick);
		ClientTickEvents.END_CLIENT_TICK.register(SeasonAura::tick);
		// the affinity choice waits until no other screen is open
		ClientPlayNetworking.registerGlobalReceiver(PowerPayloads.OpenAffinity.TYPE, (payload, context) -> affinityPending = true);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("power_hud"), PowerHud::render);

		ScreenShake.init();
		BeamClashClient.init();
		BeamCinematic.init();
		UltimateCinematic.init();
		AimCrosshair.init();
		Ambience.init();
		LeafRustle.init();
		CarryClient.init();
		com.jaymospowers.client.stamina.DodgeClient.init();
		com.jaymospowers.client.stamina.FeathersHud.init();
		JeiCheats.init();
		EmfCompat.init();
		com.jaymospowers.client.armory.TaczFirstPerson.init();
		// after every mod's main entrypoint (TaCZ adds its modern pack there), before resources load
		com.jaymospowers.armory.TaczGuns.trimDefaultPack();
		CrawlTaps.init();
		com.jaymospowers.client.wounds.WoundsHud.init();
		com.jaymospowers.client.wounds.DownedHud.init();
		com.jaymospowers.client.wounds.MedicineHud.init();
		LedgeClient.init();
		MeditateClient.init();
		SwallowedPowerClient.init();
		HiddenArmorClient.init();
		com.jaymospowers.client.costume.CostumeClient.init();
		com.jaymospowers.client.rig.RigClient.init();
		com.jaymospowers.client.backpack.BackpackClient.init();
		com.jaymospowers.client.baubles.BaublesClient.init();
		com.jaymospowers.client.alchemy.AlchemyClient.init();
		WindPowerClient.init();
		ArsenalPowerClient.init();
		WebPowerClient.init();
		ChronoPowerClient.init();
		GravityPowerClient.init();
		com.jaymospowers.client.power.resonance.ResonancePowerClient.init();
		com.jaymospowers.client.power.darktide.DarktidePowerClient.init();
		com.jaymospowers.client.power.xenoform.XenoformPowerClient.init();
		com.jaymospowers.client.power.zfighter.ZFighterPowerClient.init();
		com.jaymospowers.client.power.smoke.SmokePowerClient.init();
		com.jaymospowers.client.power.cryo.CryoPowerClient.init();
		// (0.14.3) freerunning: after the powers' own tick (a web slinger's parkour goes first) and the ledge grab's
		com.jaymospowers.client.freerun.FreerunClient.init();
	}

	/** Steering one of Jaymo's Kingdoms' dragons (R breathes its fire then), told by its id alone, so no compile-time tie. */
	private static boolean steeringDragon(net.minecraft.world.entity.player.Player player) {
		net.minecraft.world.entity.Entity mount = player.getVehicle();
		if (mount == null || mount.getControllingPassenger() != player) {
			return false;
		}
		net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType());
		return id.getNamespace().equals("jaymoskingdoms") && id.getPath().endsWith("_dragon");
	}

	/** The key mapping for an ability key (to show its bound key in a HUD, say). */
	public static KeyMapping keyMapping(PowerKey key) {
		return ABILITY_KEYS[key.ordinal()];
	}

	/** The Power Mode key mapping. */
	public static KeyMapping modeKey() {
		return modeKey;
	}

	/**
	 * (0.15.147) Presses a key for you, as if tapped, once no screen is open: an ability ({@link PowerKey#ordinal}),
	 * {@link #TAP_MODE} or {@link #TAP_ULTIMATE}. The powers screen closes first so it lands in the world.
	 */
	public static void tap(int key) {
		tapped[key] = TAP_TICKS;
	}

	/** (0.16.6) {@link #tap} {@code key} once {@code ticks} have passed (a key after another: Z, then C). */
	public static void tapLater(int key, int ticks) {
		tapDelay[key] = ticks;
	}

	/** The ascended ability's key mapping. */
	public static KeyMapping ultimateKey() {
		return ultimateKey;
	}

	/** Whether this key does anything for the local player yet (an unlocked ability, or the starter's key); HUDs dim the rest. */
	public static boolean isUnlocked(PowerKey key) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null || Mastery.isUsable(player, key);
	}

	/** True while the local player has only their power's starter (a fresh awakening). */
	public static boolean starterOnly() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && Mastery.starterOnly(player);
	}

	/** The local player's power's client half, or null. */
	public static @Nullable PowerClient active(LocalPlayer player) {
		Power power = Powers.of(player);
		return power == null ? null : PowerClients.get(power.id());
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			lastPower = null;
			return;
		}
		Power power = Powers.of(player);
		if (affinityPending && minecraft.gui.screen() == null) {
			affinityPending = false;
			if (power == null && !player.isCreative() && !com.jaymospowers.core.Affinity.chosen(player)) {
				minecraft.gui.setScreen(new AffinityScreen());
			}
		}
		while (menuKey.consumeClick()) {
			// in survival there's nothing to see until your power has awakened (but an unmade affinity choice comes back)
			if (power != null || player.isCreative()) {
				minecraft.gui.setScreen(new PowerScreen());
			} else if (com.jaymospowers.core.Affinity.ENABLED && !com.jaymospowers.core.Affinity.chosen(player)) {
				minecraft.gui.setScreen(new AffinityScreen());
			}
		}

		Identifier powerId = power == null ? null : power.id();
		if (!java.util.Objects.equals(powerId, lastPower)) {
			PowerClient old = PowerClients.get(lastPower);
			if (old != null) {
				old.onActiveChanged(player, false);
			}
			PowerClient now = PowerClients.get(powerId);
			if (now != null) {
				now.onActiveChanged(player, true);
			}
			lastPower = powerId;
		}
		PowerClient client = PowerClients.get(powerId);

		// while the symbiote holds the body every power key is "up" and a fresh press struggles against it instead
		boolean seized = com.jaymospowers.client.power.web.WebSymbioteClient.inputLocked();
		if (seized) {
			for (PowerKey key : PowerKey.values()) {
				com.jaymospowers.client.power.web.WebSymbioteClient.strugglePowerKey(key.ordinal(), ABILITY_KEYS[key.ordinal()].isDown());
			}
			com.jaymospowers.client.power.web.WebSymbioteClient.strugglePowerKey(6, modeKey.isDown());
			com.jaymospowers.client.power.web.WebSymbioteClient.strugglePowerKey(7, ultimateKey.isDown());
		}
		// inside someone else's Hush every power key goes dead (counts as released); the symbiote acts on its own regardless
		boolean hushed = power != null && com.jaymospowers.client.power.resonance.ResonanceHushClient.silenced(player);
		seized |= hushed;
		// report ability key changes (a key held while a screen opens counts as released)
		for (PowerKey key : PowerKey.values()) {
			// a locked ability's key does nothing here; the server still says it's locked when it's pressed
			// (0.13.7) on a bomb glider, ability 1 drops its dynamite instead (BombGliderClient)
			boolean physical = !seized && minecraft.gui.screen() == null && (ABILITY_KEYS[key.ordinal()].isDown() || tapped[key.ordinal()] > 0);
			// (0.15.151) a Flame Avatar's R, G and B (sneak + the key beside a power) go to the avatar instead
			boolean avatar = com.jaymospowers.client.power.ember.FlameAvatarClient.route(player, key, physical, wasDown[key.ordinal()]);
			boolean down = power != null && physical && !avatar
				&& !(key == PowerKey.ABILITY_1 && com.jaymospowers.client.armory.BombGliderClient.riding(player) != null)
				// (0.15.53) steering a Kingdoms dragon, ability 1's key (R) is its breath
				&& !(key == PowerKey.ABILITY_1 && steeringDragon(player));
			if (down != wasDown[key.ordinal()]) {
				wasDown[key.ordinal()] = down;
				ClientPlayNetworking.send(new PowerPayloads.KeyInput(key.ordinal(), down));
				if (client != null && Mastery.isUsable(player, key)) {
					// (0.15.136) a broken limb throws the aim off (LimbUse); the server throws its own
					boolean pressed = down;
					com.jaymospowers.core.wounds.LimbUse.aimed(player, com.jaymospowers.core.wounds.LimbUse.need(power, player, key.id()),
						() -> client.onKey(player, key, pressed));
				}
			}
		}
		boolean mode = power != null && !seized && minecraft.gui.screen() == null && (modeKey.isDown() || tapped[TAP_MODE] > 0);
		if (mode != modeWasDown) {
			modeWasDown = mode;
			ClientPlayNetworking.send(new PowerPayloads.ModeKey(mode));
			if (client != null) {
				boolean pressed = mode;
				com.jaymospowers.core.wounds.LimbUse.aimed(player, com.jaymospowers.core.wounds.LimbUse.need(power, player, "mode"),
					() -> client.onMode(player, pressed));
			}
		}
		// (0.17.194) a Z Fighter's C is held back a moment: a tap goes through, held it opens the form menu
		boolean ultimateHeld = com.jaymospowers.client.power.zfighter.ZFighterFormMenu.filter(minecraft, player, ultimateKey.isDown(),
			seized || minecraft.gui.screen() != null);
		boolean ultimate = power != null && power.ultimateAnswers(player)
			&& !seized && minecraft.gui.screen() == null && (ultimateHeld || tapped[TAP_ULTIMATE] > 0);
		if (ultimate != ultimateWasDown) {
			ultimateWasDown = ultimate;
			ClientPlayNetworking.send(new PowerPayloads.UltimateKey(ultimate));
			if (client != null) {
				boolean pressed = ultimate;
				com.jaymospowers.core.wounds.LimbUse.aimed(player, com.jaymospowers.core.wounds.LimbUse.need(power, player, "ultimate"),
					() -> client.onUltimate(player, pressed));
			}
		}
		if (minecraft.gui.screen() == null) {
			for (int i = 0; i < tapped.length; i++) {
				tapped[i] = Math.max(0, tapped[i] - 1);
				if (tapDelay[i] > 0 && --tapDelay[i] == 0) {
					tapped[i] = TAP_TICKS;
				}
			}
		}
		if (client != null && !minecraft.isPaused()) {
			client.tick(minecraft, player);
		}
		// (0.11.87) the Rig's hover and full flight, before the roll too
		com.jaymospowers.client.rig.RigClient.motion(minecraft, player);
		if (!minecraft.isPaused()) {
			// after the flight has set this tick's velocity: a barrel roll slides on top of it
			FlightRolls.tick(minecraft, player);
			// (0.15.136) then broken and severed limbs make flying and gliding harder
			com.jaymospowers.client.wounds.LimbMotion.tick(player);
		}
	}
}
