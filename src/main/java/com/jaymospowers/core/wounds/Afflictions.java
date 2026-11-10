package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;

/**
 * (0.20.26) What one left for dead carries away that kills them unless it's cured ({@link Mortality}, Jungxvv's ideas):
 * <ul>
 * <li><b>Venom</b> (a spider's or a snake's): {@link Kind#VENOM}'s two days, worse each half day, then death. Honey, or the
 * Potion of Cleansing (the "major antidote").</li>
 * <li><b>Poisoned blood</b>: ten days. Honey, the Potion of Cleansing, or a healer: a town healer's treatment, an infirmary, a
 * cleric, a court wizard, Restoration, a shrine (whatever eases a fever, {@link Mending#easeFever}).</li>
 * <li><b>Water in the lungs</b>: twenty days; every drink has {@link #CHOKE_CHANCE} to go down the wrong pipe (it hurts, and
 * the end comes a day sooner, {@link #CHOKE_SOONER}). A healer clears it.</li>
 * <li><b>Withering</b>: half a heart of most health gone each day ({@link #lost}); none left and they're gone. Milk, honey or a
 * healer stops it, and what was lost comes back half a heart a day.</li>
 * </ul>
 * Death by any of them is final, with no going down first ({@link Mortality#succumb}).
 */
public final class Afflictions {
	/** Twenty minutes, an in-game day. */
	static final long DAY = 24000L;
	/** A drink's chance to go down the wrong pipe with water in the lungs, the hurt, and how much sooner the end comes (ticks). */
	static final float CHOKE_CHANCE = 0.4F;
	static final float CHOKE_HURT = 2.0F;
	static final long CHOKE_SOONER = DAY;
	/** A cough's chance each second with water in the lungs. */
	static final float COUGH_CHANCE = 1.0F / 240.0F;
	/** A wave of sickness's chance each second, late in venom or poison. */
	static final float WAVE_CHANCE = 1.0F / 60.0F;

	public enum Kind {
		VENOM(2 * DAY, Mortality.Cause.VENOM), POISONED(10 * DAY, Mortality.Cause.POISON), WET_LUNGS(20 * DAY, Mortality.Cause.DROWNING),
		WITHERING(0L, Mortality.Cause.WITHER);

		/** How long it runs before it kills (0: by its own reckoning). */
		final long lasts;
		final Mortality.Cause cause;

		Kind(long lasts, Mortality.Cause cause) {
			this.lasts = lasts;
			this.cause = cause;
		}

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** What each player carries, by {@link Kind#key()}: the server game time it began (or, for {@link #REGROW}, last regrew). */
	public static final AttachmentType<Map<String, Long>> CARRIED = AttachmentRegistry.create(JaymosPowers.id("afflictions"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** Half-hearts of most health withered away. */
	public static final AttachmentType<Integer> WITHERED = AttachmentRegistry.create(JaymosPowers.id("withered"),
		builder -> builder.persistent(Codec.INT));
	static final String REGROW = "wither_regrow";
	static final Identifier WITHER_ID = JaymosPowers.id("withered");

	/** The last stage each was told of, so each worsening is told once. */
	private static final Map<UUID, Map<String, Integer>> TOLD = new HashMap<>();

	private Afflictions() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Afflictions::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOLD.clear());
		// (Kitchen's drinks: a sip, a bottle, a cup; without Kitchen, a water bottle is seen by the item hook)
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				Object list = Class.forName("com.jaymoskitchen.climate.Thirst").getField("DRANK").get(null);
				@SuppressWarnings("unchecked")
				java.util.List<java.util.function.Consumer<ServerPlayer>> drank = (java.util.List<java.util.function.Consumer<ServerPlayer>>)list;
				drank.add(Afflictions::drank);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				JaymosPowers.LOGGER.warn("Couldn't hear Jaymo's Kitchen's drinks (older Kitchen?)", e);
			}
		}
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	private static Map<String, Long> carried(Player player) {
		Map<String, Long> map = player.getAttached(CARRIED);
		return map == null ? Map.of() : map;
	}

	public static boolean has(Player player, Kind kind) {
		return carried(player).containsKey(kind.key());
	}

	/** Whether {@code player} carries anything that will kill them (what Kingdoms' healers look for, too). */
	public static boolean any(Player player) {
		for (Kind kind : Kind.values()) {
			if (has(player, kind)) {
				return true;
			}
		}
		return false;
	}

	/** {@code kind} sets in (from now; once only). */
	public static void start(ServerPlayer player, Kind kind) {
		if (has(player, kind)) {
			return;
		}
		Map<String, Long> map = new HashMap<>(carried(player));
		map.put(kind.key(), now(player));
		player.setAttached(CARRIED, Map.copyOf(map));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.affliction." + kind.key() + ".start").withStyle(ChatFormatting.DARK_RED));
	}

	/** {@code kind} cured; true if they had it. */
	public static boolean cure(ServerPlayer player, Kind kind) {
		if (!has(player, kind)) {
			return false;
		}
		Map<String, Long> map = new HashMap<>(carried(player));
		map.remove(kind.key());
		if (kind == Kind.WITHERING && player.getAttachedOrElse(WITHERED, 0) > 0) {
			map.put(REGROW, now(player));
		}
		player.setAttached(CARRIED, Map.copyOf(map));
		Map<String, Integer> told = TOLD.get(player.getUUID());
		if (told != null) {
			told.remove(kind.key());
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.affliction." + kind.key() + ".cured").withStyle(ChatFormatting.GREEN));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	/** Everything gone (a new life, {@link Mortality#endLife}). */
	static void clear(ServerPlayer player) {
		player.removeAttached(CARRIED);
		player.removeAttached(WITHERED);
		TOLD.remove(player.getUUID());
		applyWithered(player, 0);
	}

	/** A healer's work ({@link Mending#easeFever}): poisoned blood, water in the lungs and withering. True if any. */
	public static boolean heal(ServerPlayer player) {
		return cure(player, Kind.POISONED) | cure(player, Kind.WET_LUNGS) | cure(player, Kind.WITHERING);
	}

	/** {@code stack} (as it was before) was eaten or drunk by {@code player}: honey, milk, the Potion of Cleansing, water, raw garlic. */
	public static void consumed(ServerPlayer player, ItemStack stack) {
		if (stack.is(com.jaymospowers.core.wounds.herbs.Herbs.WILD_GARLIC)) {
			Herbal.garlicEaten(player);
		} else if (stack.is(Items.HONEY_BOTTLE)) {
			boolean any = cure(player, Kind.VENOM) | cure(player, Kind.POISONED) | cure(player, Kind.WITHERING);
			if (!any) {
				return;
			}
		} else if (stack.is(Items.MILK_BUCKET)) {
			cure(player, Kind.WITHERING);
		} else if (stack.is(Items.POTION)) {
			PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
			if (potion == null) {
				return;
			}
			if (Cleansing.POTION != null && (potion.is(Cleansing.POTION) || potion.is(Cleansing.LONG_POTION))) {
				cure(player, Kind.VENOM);
				cure(player, Kind.POISONED);
			} else if (potion.is(Potions.WATER) && !FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
				drank(player);
			}
		}
	}

	/** {@code player} drank water: with water in the lungs, it may go down the wrong pipe. */
	static void drank(ServerPlayer player) {
		if (!has(player, Kind.WET_LUNGS) || player.getRandom().nextFloat() >= CHOKE_CHANCE) {
			return;
		}
		Map<String, Long> map = new HashMap<>(carried(player));
		map.computeIfPresent(Kind.WET_LUNGS.key(), (k, v) -> v - CHOKE_SOONER);
		player.setAttached(CARRIED, Map.copyOf(map));
		player.hurtServer(player.level(), player.level().damageSources().drown(), CHOKE_HURT);
		cough(player);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.affliction.wet_lungs.choke").withStyle(ChatFormatting.DARK_AQUA));
	}

	private static void cough(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.7F, 0.7F);
		player.level().sendParticles(ParticleTypes.SPLASH, player.getX(), player.getEyeY() - 0.1, player.getZ(), 6, 0.15, 0.05, 0.15, 0.1);
	}

	// ---- the course of it -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		players:
		for (ServerPlayer player : java.util.List.copyOf(server.getPlayerList().getPlayers())) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			Map<String, Long> map = player.getAttached(CARRIED);
			int withered = player.getAttachedOrElse(WITHERED, 0);
			if (map == null && withered == 0) {
				continue;
			}
			if (map != null) {
				for (Kind kind : Kind.values()) {
					Long since = map.get(kind.key());
					if (since != null && step(player, kind, now(player) - since)) {
						continue players;
					}
				}
			}
			regrow(player);
		}
	}

	/** One second of {@code kind}, {@code elapsed} ticks in; true if it killed them. */
	private static boolean step(ServerPlayer player, Kind kind, long elapsed) {
		if (kind == Kind.WITHERING) {
			int lost = (int)(elapsed / DAY);
			if (lost > player.getAttachedOrElse(WITHERED, 0)) {
				player.setAttached(WITHERED, lost);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.affliction.withering.day").withStyle(ChatFormatting.DARK_GRAY));
			}
			applyWithered(player, player.getAttachedOrElse(WITHERED, 0));
			if (player.getMaxHealth() <= 1.0F) {
				Mortality.succumb(player, kind.cause);
				return true;
			}
			if (player.getRandom().nextFloat() < WAVE_CHANCE) {
				player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.25, 0.4, 0.25, 0.01);
			}
			return false;
		}
		float course = (float)elapsed / kind.lasts;
		if (course >= 1.0F) {
			Mortality.succumb(player, kind.cause);
			return true;
		}
		int stage = Math.min(3, (int)(course * 4.0F));
		Map<String, Integer> told = TOLD.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		if (stage > 0 && told.getOrDefault(kind.key(), 0) < stage) {
			told.put(kind.key(), stage);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.affliction." + kind.key() + "." + stage).withStyle(ChatFormatting.DARK_RED));
		}
		switch (kind) {
			case VENOM -> {
				if (stage >= 1) {
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, stage >= 3 ? 1 : 0, true, false));
				}
				if (stage >= 2) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, stage >= 3 ? 1 : 0, true, false));
					if (player.getRandom().nextFloat() < WAVE_CHANCE * stage) {
						player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 140, 0, true, false));
					}
				}
				if (stage >= 3) {
					player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0, true, false));
				}
			}
			case POISONED -> {
				player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, stage >= 2 ? 1 : 0, true, false));
				if (stage >= 2) {
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, stage >= 3 ? 1 : 0, true, false));
				}
				if (stage >= 3 && player.getRandom().nextFloat() < WAVE_CHANCE) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 140, 0, true, false));
				}
			}
			case WET_LUNGS -> {
				if (player.getRandom().nextFloat() < COUGH_CHANCE * (1 + stage)) {
					cough(player);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false));
				}
			}
			default -> {
			}
		}
		return false;
	}

	/** Withering stopped: what it took comes back, half a heart a day. */
	private static void regrow(ServerPlayer player) {
		int withered = player.getAttachedOrElse(WITHERED, 0);
		if (withered <= 0 || has(player, Kind.WITHERING)) {
			return;
		}
		Map<String, Long> map = new HashMap<>(carried(player));
		long since = map.getOrDefault(REGROW, now(player));
		if (!map.containsKey(REGROW)) {
			map.put(REGROW, since);
			player.setAttached(CARRIED, Map.copyOf(map));
		}
		if (now(player) - since >= DAY) {
			withered--;
			if (withered <= 0) {
				player.removeAttached(WITHERED);
				map.remove(REGROW);
			} else {
				player.setAttached(WITHERED, withered);
				map.put(REGROW, now(player));
			}
			player.setAttached(CARRIED, Map.copyOf(map));
		}
		applyWithered(player, withered);
	}

	/** Most health less {@code halfHearts} half-hearts (none: as it was). */
	private static void applyWithered(ServerPlayer player, int halfHearts) {
		AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
		if (max == null) {
			return;
		}
		if (halfHearts <= 0) {
			max.removeModifier(WITHER_ID);
			return;
		}
		AttributeModifier now = max.getModifier(WITHER_ID);
		if (now == null || now.amount() != -halfHearts) {
			max.addOrUpdateTransientModifier(new AttributeModifier(WITHER_ID, -halfHearts, AttributeModifier.Operation.ADD_VALUE));
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}
}
