package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Each power's own way in: things a powerless survival player can do to call one power to them. The first calling
 * claims that power for certain (over affinity and fate; a later calling for another power can't take it away) and
 * cuts the wait, by a day or all of it:
 * <ul>
 * <li>Wind: kill a Breeze. Awakens at once.</li>
 * <li>Gravity: land a fall of {@link #GRAVITY_FALL}+ blocks and live. A day off.</li>
 * <li>Chrono: sleep through the night holding a clock. A day off.</li>
 * <li>Web: be bitten by a cave spider and live. A day off, once a day.</li>
 * <li>Arsenal: kill with {@link #ARSENAL_TYPES} different kinds of weapon. Awakens at once.</li>
 * <li>Resonance: ring a village bell around midnight (a day off, once a night), or live through a Warden's sonic boom
 * (awakens at once).</li>
 * <li>Darktide: stay out at sea, swimming or in a boat, from dusk to dawn. Awakens at dawn.</li>
 * <li>Z Fighter: kill {@link #FIST_KILLS_NEEDED} monsters with your bare fists. Awakens at once.</li>
 * <li>Smoke: breathe a fire's smoke ({@link #SMOKE_FIRES}+ burning blocks or lit campfires round you) for
 * {@link #SMOKE_SECONDS} s in a row and live. A day off, once a day.</li>
 * <li>Xenoform: put on a Xeno-Band from a broken fallen star ({@code power/xenoform}). Awakens at once, and it is the
 * only way: fate never hands Xenoform out and it can't be an affinity ({@code Power.fated}).</li>
 * </ul>
 */
public final class Callings {
	/** The power a calling has claimed: it's the one that awakens. Saved, kept through death. */
	public static final AttachmentType<Identifier> CALLING = AttachmentRegistry.create(JaymosPowers.id("calling"),
		builder -> builder.persistent(Identifier.CODEC).copyOnDeath());
	/** The kinds of weapon a powerless player has killed with (for Arsenal). Saved, kept through death. */
	public static final AttachmentType<List<String>> WEAPON_KILLS = AttachmentRegistry.create(JaymosPowers.id("weapon_kills"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Monsters a powerless player has killed with bare fists (for Z Fighter). Saved, kept through death. */
	public static final AttachmentType<Integer> FIST_KILLS = AttachmentRegistry.create(JaymosPowers.id("fist_kills"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** One Minecraft day of the wait (20 minutes). */
	public static final int DAY = 24000;
	/** Gravity: the smallest fall (blocks) that counts. */
	public static final double GRAVITY_FALL = 40.0;
	/** Arsenal: how many different kinds of weapon must have killed something. */
	public static final int ARSENAL_TYPES = 5;
	/** Z Fighter: monsters to kill with bare fists. */
	public static final int FIST_KILLS_NEEDED = 20;
	/** Resonance: how near midnight (clock ticks either side of 18000) a bell has to be rung. */
	public static final int MIDNIGHT_WINDOW = 1000;
	/** Darktide: night ticks in a row at sea before dawn counts (a night is ~10000). */
	public static final int NIGHT_AT_SEA = 9000;
	/** Smoke: how many fires (burning blocks, lit campfires) within {@link #SMOKE_REACH} blocks, for how many seconds. */
	public static final int SMOKE_FIRES = 3;
	public static final int SMOKE_REACH = 4;
	public static final int SMOKE_SECONDS = 60;

	/** Highest fall distance this flight, per player (reset on landing). */
	private static final Map<UUID, Double> FALLS = new HashMap<>();
	/** Chrono: slept long enough holding a clock, waiting to wake up in daylight. */
	private static final Map<UUID, Boolean> SLEPT_WITH_CLOCK = new HashMap<>();
	/** Darktide: night ticks in a row spent at sea. */
	private static final Map<UUID, Integer> AT_SEA = new HashMap<>();
	/** Web and Resonance: the day number each was last counted, so they count once a day (or night). */
	private static final Map<UUID, Long> BITTEN_DAY = new HashMap<>();
	private static final Map<UUID, Long> BELL_DAY = new HashMap<>();
	/** Smoke: seconds in a row spent in a fire's smoke, and the day it last counted. */
	private static final Map<UUID, Integer> IN_SMOKE = new HashMap<>();
	private static final Map<UUID, Long> SMOKE_DAY = new HashMap<>();

	private Callings() {
	}

	/** The power a calling has claimed for this player, or null. */
	public static @Nullable Power power(Player player) {
		return Powers.get(player.getAttached(CALLING));
	}

	/**
	 * Calls {@code powerName}'s power to a powerless survival player and takes {@code ticks} off their wait (all of it, at
	 * {@link Integer#MAX_VALUE}).
	 */
	public static void call(ServerPlayer player, String powerName, int ticks) {
		Power power = Powers.byName(powerName);
		if (power == null || Powers.of(player) != null || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		// the first power to call them is theirs for certain; another power's calling changes nothing after that
		Power claimed = power(player);
		if (claimed != null && claimed != power) {
			return;
		}
		player.setAttached(CALLING, power.id());
		Integer left = player.getAttached(Powers.AWAKEN_IN);
		int now = left == null ? Powers.AWAKEN_MIN_TICKS : left;
		if (ticks >= now) {
			Powers.awaken(player);
			return;
		}
		player.setAttached(Powers.AWAKEN_IN, now - ticks);
		player.setAttached(Powers.AWAKEN_SLOW, true);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.calling", power.name().copy()
			.withStyle(style -> style.withColor(TextColor.fromRgb(power.color())))).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 0.7F);
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Callings::afterDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !player.isAlive()) {
				return;
			}
			if (source.is(DamageTypes.SONIC_BOOM)) {
				call(player, "resonance", Integer.MAX_VALUE);
			} else if (source.getEntity() instanceof CaveSpider && !source.is(DamageTypeTags.IS_PROJECTILE)
				&& once(BITTEN_DAY, player)) {
				call(player, "web", DAY);
			}
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (player instanceof ServerPlayer server && world instanceof ServerLevel level && level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)
				&& Powers.of(player) == null && Math.abs(level.getOverworldClockTime() % DAY - 18000) <= MIDNIGHT_WINDOW
				&& level.isCloseToVillage(hit.getBlockPos(), 2) && once(BELL_DAY, server)) {
				call(server, "resonance", DAY);
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FALLS.clear();
			SLEPT_WITH_CLOCK.clear();
			AT_SEA.clear();
			BITTEN_DAY.clear();
			BELL_DAY.clear();
			IN_SMOKE.clear();
			SMOKE_DAY.clear();
		});
	}

	/** Counts once per in-game day for this player: true the first time today. */
	private static boolean once(Map<UUID, Long> days, ServerPlayer player) {
		long day = player.level().getOverworldClockTime() / DAY;
		Long last = days.put(player.getUUID(), day);
		return last == null || last != day;
	}

	private static void forget(UUID id) {
		FALLS.remove(id);
		SLEPT_WITH_CLOCK.remove(id);
		AT_SEA.remove(id);
		IN_SMOKE.remove(id);
	}

	/** Wind (a Breeze), Z Fighter (bare fists) and Arsenal (weapon kinds): kills by a powerless player. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || Powers.of(player) != null) {
			return;
		}
		if (entity instanceof Breeze) {
			call(player, "wind", Integer.MAX_VALUE);
			return;
		}
		if (entity instanceof Enemy && player.getMainHandItem().isEmpty() && source.getDirectEntity() == player
			&& source.is(DamageTypes.PLAYER_ATTACK)) {
			int kills = player.getAttachedOrElse(FIST_KILLS, 0) + 1;
			player.setAttached(FIST_KILLS, kills);
			if (kills >= FIST_KILLS_NEEDED) {
				call(player, "zfighter", Integer.MAX_VALUE);
				return;
			}
			progress(player, "zfighter", kills, FIST_KILLS_NEEDED);
		}
		String kind = weaponKind(player, source);
		if (kind == null) {
			return;
		}
		List<String> kinds = new ArrayList<>(player.getAttachedOrElse(WEAPON_KILLS, List.of()));
		if (!kinds.contains(kind)) {
			kinds.add(kind);
			player.setAttached(WEAPON_KILLS, kinds);
			if (kinds.size() >= ARSENAL_TYPES) {
				call(player, "arsenal", Integer.MAX_VALUE);
			} else {
				progress(player, "arsenal", kinds.size(), ARSENAL_TYPES);
			}
		}
	}

	/** A counted step toward a calling (fist kills, weapon kinds), shown above the hotbar so the player sees it count. */
	private static void progress(ServerPlayer player, String powerName, int done, int needed) {
		Power power = Powers.byName(powerName);
		Power claimed = power(player);
		if (power == null || claimed != null && claimed != power || player.isCreative() || player.isSpectator()) {
			return;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.calling_progress." + powerName, done, needed)
			.withStyle(style -> style.withColor(TextColor.fromRgb(power.color())).withItalic(true)));
	}

	/** The kind of weapon a kill was made with: sword, axe, spear, trident, the bow or crossbow, or a modded weapon's id. */
	private static @Nullable String weaponKind(ServerPlayer player, DamageSource source) {
		if (source.getDirectEntity() instanceof ThrownTrident) {
			return "trident";
		}
		ItemStack weapon = source.getDirectEntity() instanceof AbstractArrow arrow ? arrow.getWeaponItem() : player.getMainHandItem();
		if (weapon == null || weapon.isEmpty()) {
			return null;
		}
		if (weapon.is(ItemTags.SWORDS)) {
			return "sword";
		}
		if (weapon.is(ItemTags.AXES)) {
			return "axe";
		}
		if (weapon.is(ItemTags.SPEARS)) {
			return "spear";
		}
		return BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
	}

	/** Every tick, for a powerless survival player: Gravity's falls, Chrono's sleep, Darktide's night at sea, Smoke's fires. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		// Gravity: the highest point of a fall, judged when they land (on ground, not in water)
		if (player.isInWater()) {
			FALLS.remove(id);
		} else if (player.onGround()) {
			Double fall = FALLS.remove(id);
			if (fall != null && fall >= GRAVITY_FALL) {
				call(player, "gravity", DAY);
			}
		} else if (player.fallDistance > 0) {
			FALLS.merge(id, player.fallDistance, Math::max);
		}
		// Chrono: slept the night through with a clock in hand, woken by daylight
		ServerLevel level = player.level();
		if (player.isSleeping()) {
			if (player.isSleepingLongEnough() && (player.getMainHandItem().is(Items.CLOCK) || player.getOffhandItem().is(Items.CLOCK))) {
				SLEPT_WITH_CLOCK.put(id, true);
			}
		} else if (SLEPT_WITH_CLOCK.remove(id) != null && level.isBrightOutside()) {
			call(player, "chrono", DAY);
		}
		// Darktide: dusk to dawn in an ocean, in the water or in a boat
		if (level.isDarkOutside()) {
			boolean atSea = level.getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN)
				&& (player.isInWater() || player.getVehicle() instanceof AbstractBoat);
			if (atSea) {
				AT_SEA.merge(id, 1, Integer::sum);
			} else {
				AT_SEA.remove(id);
			}
		} else {
			Integer night = AT_SEA.remove(id);
			if (night != null && night >= NIGHT_AT_SEA) {
				call(player, "darktide", Integer.MAX_VALUE);
			}
		}
		// Smoke: a minute in a burning building's smoke (or round a ring of campfires), once a day
		if (player.tickCount % 20 == 0) {
			if (fires(level, player.blockPosition()) >= SMOKE_FIRES) {
				int seconds = IN_SMOKE.merge(id, 1, Integer::sum);
				if (seconds >= SMOKE_SECONDS) {
					IN_SMOKE.remove(id);
					if (once(SMOKE_DAY, player)) {
						call(player, "smoke", DAY);
					}
				}
			} else {
				IN_SMOKE.remove(id);
			}
		}
	}

	/** Burning blocks and lit campfires within {@link #SMOKE_REACH} blocks of {@code center} (stops counting at enough). */
	private static int fires(ServerLevel level, BlockPos center) {
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SMOKE_REACH, -2, -SMOKE_REACH), center.offset(SMOKE_REACH, SMOKE_REACH, SMOKE_REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) && state.getValueOrElse(CampfireBlock.LIT, false)) {
				if (++found >= SMOKE_FIRES) {
					return found;
				}
			}
		}
		return found;
	}
}
