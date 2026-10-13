package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * (0.7.0) Stage 3 of the 0.10.0 drop: a player as a vampire. Bitten by one (and living through it), they turn at the
 * next nightfall (dying first cures it). Then:
 *
 * <ul>
 * <li>At night: strong (Strength), quick (Speed), seeing in the dark (Night Vision).</li>
 * <li>By day under the open sky: weak; round midday ({@link #NOON_FROM}-{@link #NOON_TO}) with nothing on their head,
 * they burn.</li>
 * <li>Blood: sneak and right-click a grown villager or a realm's folk with an empty hand to drink (a bite every
 * second): it feeds them, heals them, and the bitten turn too, if they live. Two nights without it and they're
 * starving (Hunger, Weakness) until they drink. Anyone of that realm who sees it cries out (a heavy crime); at night
 * they show themselves too, and the guards hunt them till dawn.</li>
 * <li>The cure, for the bitten or the turned (or a werewolf, {@link WerewolfPlayers}): by day, a golden apple given to a
 * cleric (right-click them holding it). The rite takes the apple.</li>
 * </ul>
 */
public final class VampirePlayers {
	/** At night: Strength and Speed levels (amplifiers). */
	static final int NIGHT_STRENGTH = 0, NIGHT_SPEED = 0;
	/** Midday, when the sun burns a bare head (time of day); how long it burns (seconds). */
	static final long NOON_FROM = 3000L, NOON_TO = 9000L;
	static final float BURN_SECONDS = 3.0F;
	/** A bite: what it takes, what it gives (food, saturation, health), how often (ticks). */
	static final float DRINK_DAMAGE = 4.0F, DRINK_HEAL = 3.0F, DRINK_SATURATION = 1.5F;
	static final int DRINK_FOOD = 4, DRINK_EVERY = 20;
	/** Nights without blood before they starve. */
	static final int STARVE_NIGHTS = 2;

	private static final Map<UUID, Long> LAST_DRINK = new HashMap<>();

	private VampirePlayers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VampirePlayers::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND
				|| !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}
			if (target instanceof Villager v && sp.getMainHandItem().is(Items.GOLDEN_APPLE) && !sp.isShiftKeyDown()
				&& v.getVillagerData().profession().is(VillagerProfession.CLERIC)) {
				return cure(server, sp, v) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if (Vampires.isVampire(sp) && sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && Vampires.person(target)) {
				drink(server, sp, target);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_DRINK.clear());
	}

	static boolean vampire(Player player) {
		return Vampires.isVampire(player);
	}

	/** A vampire's bite on {@code player}: they'll turn at the next nightfall (unless they die first, or carry the wolf's curse). */
	static void bitten(ServerPlayer player) {
		if (!Vampires.isVampire(player) && !Lycans.cursed(player) && !player.hasAttached(Vampires.BITTEN)) {
			player.setAttached(Vampires.BITTEN, player.level().getOverworldClockTime());
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		boolean night = Vampires.night(clock);
		long nightNo = Vampires.nightIndex(clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((overworld.getGameTime() + player.getId()) % 20 != 0 || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			Long bitten = player.getAttached(Vampires.BITTEN);
			if (bitten != null && night && nightIndex(bitten) < nightNo) {
				turn(player, nightNo);
			}
			Vampires.Vampire v = player.getAttached(Vampires.VAMPIRE);
			if (v == null || player.isCreative()) {
				continue;
			}
			if (night) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, NIGHT_STRENGTH, true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, NIGHT_SPEED, true, false, true));
			} else if (sunlit(player)) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
				long t = Math.floorMod(clock, 24000L);
				if (t >= NOON_FROM && t < NOON_TO && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
					player.igniteForSeconds(BURN_SECONDS);
				}
			}
			if (nightNo - v.fedNight() >= STARVE_NIGHTS) {
				player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0, true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
			}
		}
	}

	private static long nightIndex(long clock) {
		return Vampires.nightIndex(clock);
	}

	private static boolean sunlit(ServerPlayer player) {
		ServerLevel level = player.level();
		return !level.isRainingAt(player.blockPosition()) && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	/** They wake changed (vaguely told: they find out the rest). */
	private static void turn(ServerPlayer player, long nightNo) {
		player.removeAttached(Vampires.BITTEN);
		player.setAttached(Vampires.VAMPIRE, new Vampires.Vampire(Vampires.Kind.PLAYER.name().toLowerCase(java.util.Locale.ROOT), nightNo - 1, 0L));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.vampire.turned").withStyle(ChatFormatting.DARK_RED));
		player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	/** A bite from a player who's one of them. */
	private static void drink(ServerLevel level, ServerPlayer player, LivingEntity target) {
		long now = level.getGameTime();
		Long last = LAST_DRINK.get(player.getUUID());
		if (last != null && now - last < DRINK_EVERY) {
			return;
		}
		LAST_DRINK.put(player.getUUID(), now);
		long clock = level.getOverworldClockTime();
		long nightNo = Vampires.nightIndex(clock);
		if (target.isSleeping()) {
			target.stopSleeping();
		}
		target.hurtServer(level, level.damageSources().playerAttack(player), DRINK_DAMAGE);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3, false, false, false));
		player.heal(DRINK_HEAL);
		player.getFoodData().eat(DRINK_FOOD, DRINK_SATURATION);
		Vampires.Vampire v = player.getAttached(Vampires.VAMPIRE);
		if (v != null) {
			player.setAttached(Vampires.VAMPIRE, new Vampires.Vampire(v.kind(), nightNo, now));
		}
		level.sendParticles(new DustParticleOptions(0x8A0303, 1.0F), target.getX(), target.getEyeY() - 0.25, target.getZ(), 8, 0.15, 0.15, 0.15, 0.0);
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.9F, 0.55F);
		if (target.isAlive() && target instanceof PathfinderMob p && !Lycans.cursed(p) && !p.hasAttached(Vampires.BITTEN)) {
			Vampires.bite(p, clock);
		}
		if (Vampires.night(clock)) {
			Vampires.expose(player, nightNo);
		}
		Faction f = Theft.lawAt(level, target.blockPosition());
		if (f != null) {
			Theft.commit(player, level, f, Theft.Crime.BLOODLETTING, target.blockPosition(), null, false);
		}
	}

	/** A cleric's rite: by day, a golden apple; the bitten, the turned and the wolf-cursed are whole again. */
	private static boolean cure(ServerLevel level, ServerPlayer player, Villager cleric) {
		boolean vampire = Vampires.isVampire(player) || player.hasAttached(Vampires.BITTEN);
		boolean wolf = Lycans.cursed(player);
		if (!vampire && !wolf) {
			return false;
		}
		if (Vampires.night(level.getOverworldClockTime())) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.cure.night").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
		}
		player.removeAttached(Vampires.VAMPIRE);
		player.removeAttached(Vampires.BITTEN);
		if (wolf) {
			WerewolfPlayers.cure(player);
		}
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.0), player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		cleric.getLookControl().setLookAt(player);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.cure.done").withStyle(ChatFormatting.GOLD));
		return true;
	}
}
