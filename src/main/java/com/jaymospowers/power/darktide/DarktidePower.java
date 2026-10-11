package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Darktide: water and darkness. A guard-and-parry fighter that runs on {@link DarktideFlow Flow}, built around a katana
 * (Balkon's WeaponMod) but usable unarmed or with knives. Everything is decided here, on the server.
 * <ul>
 * <li>Starter: the guard (right click held, {@link DarktideGuard}) with its parry and reflect.</li>
 * <li>R Dark Strike / Pounce (knives, ascended), V Undertow, G Crescent (katana) / Ink Cross (knife) / Fang Barrage
 * (knives, ascended), B Black Tide / Riptide Dash (knives, ascended): {@link DarktideMoves}.</li>
 * <li>Z Veil, X Gedan: Stillwater ({@link DarktideGedan}), C Tide Arms / Necromancy / Drown ({@link DarktideTide}).</li>
 * </ul>
 * Ascends after {@link #ASCEND_PARRIES} katana parries with every ability unlocked. From dusk to dawn it's always the
 * Drowned Dark ({@link #drowned}): that's Darktide's own state, never written into Fate.
 */
public final class DarktidePower implements Power {
	public static final DarktidePower INSTANCE = new DarktidePower();
	public static final Identifier ID = JaymosPowers.id("darktide");

	/** Katana parries (with all four abilities unlocked) to ascend, and the whispers on the way. */
	public static final int ASCEND_PARRIES = 40;
	static final int[] WHISPER_AT = {15, 30};
	/** Veil: Flow drained a second. */
	static final float VEIL_DRAIN_PER_SECOND = 2.0F;
	/** Veil: mobs past this many blocks lose you, in the dark / in light. */
	public static final double VEIL_DARK_RANGE = 6.0, VEIL_LIGHT_RANGE = 12.0;

	private static final Map<UUID, DarktideLive> LIVE = new HashMap<>();

	private DarktidePower() {
	}

	public static void init() {
		Powers.register(INSTANCE);
		DarktideNet.register();
		ServerPlayNetworking.registerGlobalReceiver(DarktideNet.Guard.TYPE, (payload, ctx) -> DarktideGuard.setGuard(ctx.player(), payload.up()));
		ServerPlayNetworking.registerGlobalReceiver(DarktideNet.Skill.TYPE, (payload, ctx) -> {
			if (has(ctx.player()) && ctx.player().isAlive()) {
				DarktideGedan.key(ctx.player(), payload.down());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(DarktideNet.NightKey.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (has(player) && player.isAlive() && !player.isSpectator() && !Fate.isAscended(player) && drowned(player)) {
				DarktideTide.drownKey(player, payload.down());
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DarktideGuard::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(DarktideGuard::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(DarktideTide::allowDeath);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DarktideStatus.tick(server);
			DarktideGuard.tickReflections();
			DarktideMoves.tickWorld(server);
			DarktideTide.tickWorld(server);
		});
		// Balkon's knives throw on right click: for a Darktide user plain right click is the guard; sneak + right click throws
		UseItemCallback.EVENT.register((player, level, hand) -> {
			// WeaponMod knives throw and katanas raise their own block on right click: for Darktide the plain
			// right click is the guard instead, sneaking gives WeaponMod's use back.
			if (has(player) && !player.isShiftKeyDown()
					&& (DarktideWeapons.isKnife(player.getItemInHand(hand)) || DarktideWeapons.isKatana(player.getItemInHand(hand)))) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		CombatRollCompat.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			if (has(player)) {
				cleanUp(player, false);
			}
			LIVE.remove(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (has(player)) {
					cleanUp(player, false);
				}
			}
			LIVE.clear();
			DarktideStatus.clear();
			DarktideMoves.clear();
			DarktideTide.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DarktideDebugCommand.register(dispatcher));
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return 0x2F6FB5;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.INK_SAC);
	}

	/** Dark Strike, Undertow, Crescent, Black Tide. */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_3, PowerKey.ABILITY_2, PowerKey.ABILITY_4};
	}

	/** The guard is the starter, and it's passive (right click), so no key. */
	@Override
	public @Nullable PowerKey starterKey() {
		return null;
	}

	@Override
	public @Nullable String statusSuffix(Player player) {
		if (!drowned(player)) {
			return null;
		}
		return Fate.isAscended(player) ? ".night_ascended" : ".night";
	}

	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return Mastery.unlocked(player) >= Mastery.ALL;
	}

	public static boolean has(Player player) {
		return Powers.has(player, INSTANCE);
	}

	/** The Drowned Dark: always at night (where there is one), and for a power corrupted by command. Both sides. */
	public static boolean drowned(Player player) {
		return DarktideFlow.night(player.level()) || Fate.isCorrupted(player);
	}

	static DarktideLive live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player.getUUID(), uuid -> new DarktideLive());
	}

	static @Nullable DarktideLive liveIfAny(Player player) {
		return LIVE.get(player.getUUID());
	}

	// ---- Flow ---------------------------------------------------------------------------------------------------------

	static void addFlow(ServerPlayer player, float amount) {
		DarktideLive live = live(player);
		live.flow = Math.max(0.0F, Math.min(DarktideFlow.MAX, live.flow + amount));
	}

	/** Spends {@code amount} Flow if there's that much; otherwise says so and spends nothing. */
	static boolean spend(ServerPlayer player, float amount) {
		DarktideLive live = live(player);
		if (live.flow + 1.0E-3F < amount) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_flow").withStyle(ChatFormatting.DARK_AQUA));
			return false;
		}
		live.flow -= amount;
		return true;
	}

	// ---- cooldowns ----------------------------------------------------------------------------------------------------

	static boolean ready(ServerPlayer player, int slot) {
		return player.level().getGameTime() >= live(player).until[slot];
	}

	static void cooldown(ServerPlayer player, int slot, int ticks) {
		DarktideLive live = live(player);
		live.until[slot] = player.level().getGameTime() + ticks;
		live.length[slot] = Math.max(1, ticks);
	}

	static void clearCooldowns(ServerPlayer player) {
		DarktideLive live = live(player);
		java.util.Arrays.fill(live.until, 0L);
	}

	// ---- keys ---------------------------------------------------------------------------------------------------------

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		DarktideWeapons.Kit kit = DarktideWeapons.kit(player);
		boolean knives = kit == DarktideWeapons.Kit.KNIFE && Fate.isAscended(player);
		switch (key) {
			case ABILITY_1 -> {
				if (knives) {
					DarktideMoves.pounce(player, down);
				} else if (down) {
					DarktideMoves.darkStrike(player);
				}
			}
			case ABILITY_2 -> {
				if (knives) {
					DarktideMoves.barrage(player, down);
				} else if (down) {
					switch (kit) {
						case KATANA -> DarktideMoves.crescent(player);
						case KNIFE -> DarktideMoves.inkCross(player);
						default -> player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.blade_only").withStyle(ChatFormatting.GRAY));
					}
				}
			}
			case ABILITY_3 -> {
				if (down) {
					DarktideMoves.undertow(player);
				}
			}
			case ABILITY_4 -> {
				if (down) {
					if (knives) {
						DarktideMoves.dash(player);
					} else {
						DarktideMoves.blackTide(player);
					}
				}
			}
		}
	}

	/** Veil on and off (once all four abilities are unlocked). */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (!down || !slotUnlocked(player, "mode")) {
			return;
		}
		DarktideLive live = live(player);
		if (!live.veil && live.flow < 1.0F) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_flow").withStyle(ChatFormatting.DARK_AQUA));
			return;
		}
		setVeil(player, !live.veil);
	}

	static void setVeil(ServerPlayer player, boolean on) {
		DarktideLive live = live(player);
		if (live.veil == on) {
			return;
		}
		live.veil = on;
		if (on) {
			player.setAttached(DarktideState.VEIL, true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 0.6F, 0.6F);
			player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(),
				20, 0.4, 0.6, 0.4, 0.02);
		} else {
			player.removeAttached(DarktideState.VEIL);
		}
	}

	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		DarktideTide.ultimateKey(player, down);
	}

	// ---- fate ---------------------------------------------------------------------------------------------------------

	/** A katana parry: one step toward ascension, once every ability is unlocked. */
	static void countParry(ServerPlayer player) {
		if (Fate.isAscended(player) || Mastery.unlocked(player) < Mastery.ALL) {
			return;
		}
		DarktideState.Save save = DarktideState.save(player);
		int parries = save.parries() + 1;
		player.setAttached(DarktideState.SAVE, save.withParries(parries));
		for (int at : WHISPER_AT) {
			if (parries == at) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.whisper_" + at)
					.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}
		if (parries >= ASCEND_PARRIES && Fate.canAscend(player)) {
			Fate.ascend(player);
		}
	}

	// ---- lifecycle ----------------------------------------------------------------------------------------------------

	@Override
	public void onGained(ServerPlayer player) {
		LIVE.put(player.getUUID(), new DarktideLive());
	}

	@Override
	public void onLost(ServerPlayer player) {
		cleanUp(player, true);
		LIVE.remove(player.getUUID());
		player.removeAttached(DarktideState.HUD);
		player.removeAttached(DarktideState.SAVE);
	}

	/** Ends everything running and hands the real items back (logging out, losing the power, the server stopping). */
	private static void cleanUp(ServerPlayer player, boolean lost) {
		DarktideGuard.drop(player);
		setVeil(player, false);
		DarktideGedan.cancel(player);
		DarktideMoves.cancel(player);
		DarktideTide.revertArms(player, false);
		DarktideTide.meltAll(player);
		CombatRollCompat.apply(player, false);
		DarktideMove.clear(player);
	}

	/** Called by {@code DarktideDropMixin} before a dying player's inventory drops. */
	public static void beforeDrops(ServerPlayer player) {
		if (has(player)) {
			DarktideTide.revertArms(player, false);
			DarktideGedan.cancel(player);
		}
	}

	/** Dashes, pounces, Undertow and Gedan's slashes move you faster than walking. */
	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		DarktideLive live = LIVE.get(player.getUUID());
		return live != null && (DarktideMoves.moving(live) || live.gedan != null);
	}

	@Override
	public void tick(ServerPlayer player) {
		DarktideLive live = live(player);
		if (!player.isAlive()) {
			if (live.guarding) {
				DarktideGuard.drop(player);
			}
			return;
		}
		boolean drowned = drowned(player);
		live.flow = Math.max(0.0F, Math.min(DarktideFlow.MAX, live.flow + DarktideFlow.tickGain(player, drowned, live.flow)));
		if (live.veil) {
			live.flow -= VEIL_DRAIN_PER_SECOND / 20.0F;
			if (live.flow <= 0.0F) {
				live.flow = 0.0F;
				setVeil(player, false);
			} else if (player.tickCount % 5 == 0) {
				veilTick(player);
			}
		}
		DarktideGuard.tick(player, live);
		DarktideMoves.tick(player, live);
		DarktideGedan.tick(player, live);
		DarktideTide.tick(player, live);
		if (player.tickCount % 20 == 0) {
			CombatRollCompat.apply(player, true);
		}
		CombatRollCompat.tickEvasion(player, live);
		sync(player, live);
	}

	/** Mobs that can't see through the veil lose you. */
	private static void veilTick(ServerPlayer player) {
		double range = DarktideFlow.dark(player.level(), player.blockPosition().above()) ? VEIL_DARK_RANGE : VEIL_LIGHT_RANGE;
		for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
				player.getBoundingBox().inflate(48.0), mob -> mob.getTarget() == player)) {
			if (mob.distanceTo(player) > range) {
				mob.setTarget(null);
			}
		}
	}

	/** How far a veiled player can be seen from (for the targeting mixin): the range, or -1 when not veiled. */
	public static double veilRange(Player player) {
		if (player.getAttached(DarktideState.VEIL) == null) {
			return -1.0;
		}
		return DarktideFlow.dark(player.level(), player.blockPosition().above()) ? VEIL_DARK_RANGE : VEIL_LIGHT_RANGE;
	}

	/** A veiled user's name tag hides in the dark (both sides; the veil is synced). */
	public static boolean veilHidesName(Player player) {
		return player.getAttached(DarktideState.VEIL) != null && DarktideFlow.dark(player.level(), player.blockPosition().above());
	}

	static void sync(ServerPlayer player, DarktideLive live) {
		DarktideState.Save save = DarktideState.save(player);
		DarktideState.Hud hud = new DarktideState.Hud(Math.round(live.flow), live.until.clone(), live.length.clone(), live.empoweredUntil,
			live.veil, live.arms, save.shadows().size(), save.parries(), live.guardLockedUntil, live.risen.size());
		if (live.sent == null || !live.sent.same(hud)) {
			live.sent = hud;
			player.setAttached(DarktideState.HUD, hud);
		}
	}
}
