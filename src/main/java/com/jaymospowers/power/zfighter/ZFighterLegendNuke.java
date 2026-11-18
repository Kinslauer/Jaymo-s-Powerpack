package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Nuke, the top of the legendary line's heart. Ascended and in its third step, with the heat full: holding sneak keeps
 * the heart from boiling over and winds it up instead ({@link #WIND} ticks, slowed to a crawl, a growing glow and hum).
 * Wound all the way, it goes off round him: {@link #RADIUS} blocks, up to {@link #HARM} at the middle, and he takes none
 * of it. It leaves <b>fallout</b>: for {@link #FALLOUT_TICKS} ticks the ground within {@link #FALLOUT_RADIUS} sickens
 * everyone in it but him ({@link #FALLOUT_HARM} a second, and poison). Let go of sneak early and the heat boils over as
 * ever (the surge). All the heat goes into it. No block is broken.
 */
final class ZFighterLegendNuke {
	// ---- tuning ----
	private static final String TABLE = "zfighter_legend_nuke";
	/** The step of the line that opens it. */
	static final int STEP = 3;
	/** The wind-up (ticks), the blast's reach and harm at the middle, the wait after. */
	static final int WIND = 100, COOLDOWN = 20 * 180;
	static final double RADIUS = 14.0;
	static final float HARM = 30.0F;
	/** The fallout: how long (ticks), how wide, harm a second. */
	static final int FALLOUT_TICKS = 20 * 30;
	static final double FALLOUT_RADIUS = 10.0;
	static final float FALLOUT_HARM = 1.0F;

	private static final Map<UUID, Long> WINDING = new HashMap<>();

	private record Fallout(UUID owner, ResourceKey<Level> dim, Vec3 at, long until) {
	}

	private static final List<Fallout> FALLOUT = new ArrayList<>();

	private ZFighterLegendNuke() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterLegendNuke::serverTick);
	}

	static void clear() {
		WINDING.clear();
		FALLOUT.clear();
	}

	/** Stops a wind-up (it winds down for nothing). */
	static void forget(ServerPlayer player) {
		if (WINDING.remove(player.getUUID()) != null) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
	}

	/**
	 * Every tick the heat is full: true while sneak holds it (winding, or just gone off); false lets it boil over (not
	 * sneaking, let go early, not open to him, or not ready again).
	 */
	static boolean hold(ServerPlayer player, ZFighterPower.Live live, ZFighterLegendHeart.Heart heart) {
		boolean creative = player.getAbilities().instabuild;
		if (!player.isShiftKeyDown() || ZFighterLegendHeart.step(live) < STEP || !Fate.isAscended(player) || !PickKit.ready(TABLE, player, 0) && !creative) {
			forget(player);
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long since = WINDING.get(player.getUUID());
		if (since == null) {
			WINDING.put(player.getUUID(), now);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.5F);
			since = now;
		}
		long t = now - since;
		if (t >= WIND) {
			WINDING.remove(player.getUUID());
			detonate(player, heart);
			return true;
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 4, false, false));
		if (t % 5 == 0) {
			float k = Math.min(1.0F, t / (float)WIND);
			MagicFx.orb(level, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), 0.6F + 1.4F * k, ZFighterLegendHeart.GLOW, 6);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.5F + k);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_legend_heart.nuke_wind", PickKit.gauge(k, 10))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	private static void detonate(ServerPlayer player, ZFighterLegendHeart.Heart heart) {
		ServerLevel level = player.level();
		PickKit.spend(TABLE, player, 0, COOLDOWN);
		heart.heat = 0.0F;
		double radius = AbilityPower.size(player, RADIUS);
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		ZFighterLegendMoves.blast(player, at, radius, HARM, 2.0);
		MagicFx.blast(level, at, (float)radius, ZFighterLegendHeart.CORE, 30);
		MagicFx.ring(level, player.position().add(0.0, 0.2, 0.0), (float)radius * 1.3F, ZFighterLegendHeart.GLOW);
		MagicFx.beam(level, player.position(), player.position().add(0.0, radius * 2.0, 0.0), 3.0F, ZFighterLegendHeart.CORE, 30);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6.0F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.5F);
		com.jaymospowers.core.Shakes.around(level, at, radius * 4.0, 2.5F);
		FALLOUT.add(new Fallout(player.getUUID(), level.dimension(), player.position(), level.getGameTime() + FALLOUT_TICKS));
	}

	private static void serverTick(MinecraftServer server) {
		if (FALLOUT.isEmpty()) {
			return;
		}
		for (Iterator<Fallout> it = FALLOUT.iterator(); it.hasNext();) {
			Fallout f = it.next();
			ServerLevel level = server.getLevel(f.dim());
			if (level == null || level.getGameTime() >= f.until()) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			double radius = AbilityPower.size(level, f.owner(), FALLOUT_RADIUS);
			if (now % 10 == 0) {
				level.sendParticles(MagicFx.miasma(ZFighterLegendHeart.GLOW, 1.4F), f.at().x, f.at().y + 0.6, f.at().z, 10, radius * 0.5, 0.4, radius * 0.5, 0.01);
			}
			if (now % 20 != 0) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(f.owner());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(f.at(), f.at()).inflate(radius, 4.0, radius),
				e -> e.isAlive() && !e.getUUID().equals(f.owner()) && e.position().distanceToSqr(f.at()) <= radius * radius)) {
				e.hurtServer(level, owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), FALLOUT_HARM);
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
			}
		}
	}
}
