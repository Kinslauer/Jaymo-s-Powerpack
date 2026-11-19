package com.jaymospowers.divine;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.stamina.Feathers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Divine-born's ward (Andy: "a healing ward that pushes"): a dome of soft light round them, {@link #RADIUS} blocks
 * across, for {@link #TICKS}. Inside it the Divine-born, other players, tame animals and villagers are healed
 * ({@link #HEAL} every second); hostile creatures are shoved out and kept out. The dome follows the one who raised it.
 * Sprint + the heritage key (Kingdoms asks by name: {@link #ward}); costs {@link #COST} half-feathers, then
 * {@link #COOLDOWN} ticks before another.
 */
public final class DivineWard {
	public static final double RADIUS = 5.0;
	public static final int TICKS = 20 * 8, COOLDOWN = 20 * 60, COST = 8;
	/** Health every healed one gets each second, and how hard a hostile is shoved (blocks/tick) while inside. */
	public static final float HEAL = 1.5F;
	public static final double PUSH = 0.55;

	private record Ward(ServerLevel level, UUID owner, long until) {
	}

	private static final List<Ward> WARDS = new ArrayList<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private DivineWard() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Ward ward : List.copyOf(WARDS)) {
				ServerPlayer owner = server.getPlayerList().getPlayer(ward.owner);
				if (owner == null || !owner.isAlive() || owner.level() != ward.level || ward.level.getGameTime() >= ward.until) {
					WARDS.remove(ward);
					if (owner != null) {
						MagicFx.ring(ward.level, owner.position().add(0.0, 0.1, 0.0), (float)RADIUS, DivineSense.LIGHT);
					}
					continue;
				}
				step(ward, owner);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WARDS.clear();
			READY.clear();
		});
	}

	/** Sprint + the heritage key (Kingdoms calls this by name): raises the ward. False if {@code player} isn't Divine-born. */
	public static boolean ward(ServerPlayer player) {
		if (!DivineSense.is(player) || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long left = READY.getOrDefault(player.getUUID(), 0L) - now;
		if (left > 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.divine.ward_wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (!player.isCreative() && !Feathers.spend(player, COST)) {
			return true;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		WARDS.add(new Ward(level, player.getUUID(), now + TICKS));
		MagicFx.sigil(level, player.position().add(0.0, 0.05, 0.0), (float)RADIUS, DivineSense.LIGHT, 30);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 1.5F);
		return true;
	}

	private static boolean kin(LivingEntity entity, Player owner) {
		return entity instanceof Player || entity instanceof AbstractVillager || entity instanceof TamableAnimal pet && pet.isTame();
	}

	private static void step(Ward ward, ServerPlayer owner) {
		ServerLevel level = ward.level;
		long now = level.getGameTime();
		Vec3 at = owner.position();
		if (now % 40 == 0) {
			MagicFx.orb(level, at.add(0.0, RADIUS * 0.2, 0.0), (float)RADIUS, DivineSense.LIGHT, 44);
		}
		AABB box = new AABB(at, at).inflate(RADIUS, RADIUS, RADIUS);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
			double flat = Math.hypot(entity.getX() - at.x, entity.getZ() - at.z);
			if (flat > RADIUS) {
				continue;
			}
			if (kin(entity, owner)) {
				if (now % 20 == 0 && entity.getHealth() < entity.getMaxHealth()) {
					entity.heal(HEAL);
					level.sendParticles(MagicFx.mote(DivineSense.LIGHT, 0.6F), entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
				}
			} else if (entity instanceof Mob mob && mob instanceof Enemy && !(mob instanceof TamableAnimal pet && pet.isTame())) {
				Vec3 away = new Vec3(entity.getX() - at.x, 0.0, entity.getZ() - at.z);
				away = away.lengthSqr() < 1.0E-4 ? owner.getViewVector(1.0F).horizontal().reverse() : away.normalize();
				entity.setDeltaMovement(away.x * PUSH, Math.max(entity.getDeltaMovement().y, 0.1), away.z * PUSH);
				entity.syncVelocity = true;
				if (now % 20 == 0) {
					level.sendParticles(MagicFx.mote(DivineSense.LIGHT, 0.8F), entity.getX(), entity.getY() + 0.8, entity.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
				}
			}
		}
	}
}
