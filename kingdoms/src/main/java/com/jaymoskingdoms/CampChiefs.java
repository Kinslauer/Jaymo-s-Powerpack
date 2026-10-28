package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * (0.7.293) A camp's leader as one of MCA's villagers: a barbarian chief ({@link Folk#CHIEF}, Andy retiring the folk:
 * "all mca please"), a goblin king, an orc warchief ({@link Greenskins}). Each keeps what made him a folk boss: his boss
 * bar, shown to those near ({@link BossBars}), his second wind at {@link Folk#RALLY_AT} that calls his camp to his foe,
 * and his death clearing the camp ({@link Camps#chiefSlain}).
 */
final class CampChiefs {
	/** How long a chief's rally lasts (ticks), and how far he calls his own in. */
	static final int RALLY_TICKS = 20 * 60;
	static final double RALLY_CALL = 48.0;

	/** A chief's boss bar, by his UUID. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** The chiefs who've had their second wind (kept in memory: a restart gives it back). */
	private static final Set<UUID> RALLIED = new HashSet<>();

	private CampChiefs() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (ServerBossEvent bar : BARS.values()) {
				bar.removeAllPlayers();
			}
			BARS.clear();
			RALLIED.clear();
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> dropBar(entity.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			dropBar(entity.getUUID());
			RALLIED.remove(entity.getUUID());
		});
	}

	private static void dropBar(UUID id) {
		ServerBossEvent bar = BARS.remove(id);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** A camp's leader: a barbarian chief, a goblin king, an orc warchief. */
	static boolean isRole(int role) {
		return role == Folk.CHIEF || role == Goblins.KING || role == Orcs.WARCHIEF;
	}

	/** A villager chief's step ({@code Workers.step}, his brain runs after): his bar to those near, and his rally. */
	static void step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (mob instanceof Folk || mob.isSleeping() || Downed.isDowned(mob)) {
			return;
		}
		if (mob.tickCount % 5 == 0) {
			ServerBossEvent bar = BARS.computeIfAbsent(mob.getUUID(), id -> new ServerBossEvent(id, mob.getDisplayName(),
				w.role() == Goblins.KING ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
			bar.setProgress(mob.getHealth() / mob.getMaxHealth());
			BossBars.show(level, mob, bar);
		}
		if (mob.getHealth() <= mob.getMaxHealth() * Folk.RALLY_AT && RALLIED.add(mob.getUUID())) {
			rally(level, mob, w.factionId());
		}
	}

	/** His second wind, as a folk leader's: stronger, harder, quicker, and his own called to his foe. */
	private static void rally(ServerLevel level, PathfinderMob chief, int camp) {
		chief.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, 1));
		chief.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RALLY_TICKS, 1));
		chief.addEffect(new MobEffectInstance(MobEffects.SPEED, RALLY_TICKS, 0));
		LivingEntity foe = Rulers.target(chief) != null ? Rulers.target(chief) : chief.getLastHurtByMob();
		if (foe != null) {
			for (Worker w : Workers.near(level, chief.getBoundingBox().inflate(RALLY_CALL), x -> x.factionId() == camp && x.mob() != chief)) {
				Rulers.charge(w.mob(), foe);
			}
		}
		level.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 1.0F);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, chief.getBoundingBox().inflate(32.0))) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.king_rallies", chief.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** A villager chief died ({@code Workers.died}): his camp is cleared. */
	static void died(ServerLevel level, Worker w, DamageSource source) {
		if (!(w.mob() instanceof Folk) && isRole(w.role())) {
			Camps.chiefSlain(level, w.mob(), w.factionId(), source);
		}
	}
}
