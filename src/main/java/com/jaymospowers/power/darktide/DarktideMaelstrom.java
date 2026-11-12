package com.jaymospowers.power.darktide;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.zfighter.ZFighterKi;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * C held (ascended): the Maelstrom, Darktide's ultimate. A dome of dark water {@link #RADIUS} blocks across stands where
 * it was called for {@link #TICKS}. Its caster swims through it as through the sea (free flight while inside) with Flow
 * held full; everyone else in it is slowed, can't see out (players are blinded), can't get out (the skin pulls them
 * back), hangs in the water and drowns at {@link #DROWN_DAMAGE} every {@link #DROWN_EVERY} ticks. When it ends, or its
 * caster lets it go by holding C again, the water comes down: {@link #CRASH_DAMAGE} to everyone in it.
 */
final class DarktideMaelstrom {
	/** C held this long is the Maelstrom; let go sooner, it's the tap (raising a shadow). */
	static final int HOLD_TICKS = 12;
	static final double RADIUS = 12.0;
	static final int TICKS = 200, COOLDOWN = 2400, SWELL_TICKS = 16, FILM_TICKS = 40;
	static final float DROWN_DAMAGE = 2.0F, CRASH_DAMAGE = 16.0F;
	static final int DROWN_EVERY = 20;
	/** How fast its caster swims in it (the flight speed; a walking player's is 0.05). */
	static final float SWIM_SPEED = 0.06F, NORMAL_FLY_SPEED = 0.05F;
	static final int COLOR = 0x12356E, FOAM = 0xCFE8FF;
	/** The waves that roll in as it's called and out as it comes down, and how long the ones running out take (ticks). */
	static final int WAVES = 6, WAVE_TICKS = 14;

	static final class Dome {
		final ServerPlayer owner;
		final ServerLevel level;
		final Vec3 center;
		final long start;
		final long until;
		boolean swimming;

		Dome(ServerPlayer owner, Vec3 center, long start) {
			this.owner = owner;
			this.level = owner.level();
			this.center = center;
			this.start = start;
			this.until = start + TICKS;
		}
	}

	private static final List<Dome> DOMES = new ArrayList<>();

	private DarktideMaelstrom() {
	}

	/** C held long enough: calls the Maelstrom, or brings a standing one down early. */
	static void key(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		if (live.maelstrom != null) {
			end(player, true);
			return;
		}
		if (!Fate.isAscended(player) || !DarktidePower.ready(player, DarktideState.MAELSTROM)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 center = player.position();
		Dome dome = new Dome(player, center, level.getGameTime());
		live.maelstrom = dome;
		DOMES.add(dome);
		DarktideGuard.drop(player);
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.CRANE, FILM_TICKS, center, null, RADIUS * 2.0);
		DarktideMove.play(player, DarktideMove.CAST, DarktideMove.CAST_TICKS, 2);
		MagicFx.ring(level, center.add(0.0, 0.1, 0.0), (float) RADIUS, FOAM);
		// the sea comes in from every side and closes over you, and the ground under the dome is water while it stands
		MagicFx.pool(level, center, (float) RADIUS, DarktideLooks.WATER, TICKS, SWELL_TICKS);
		waves(level, center, true);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2.0F, 0.4F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, center, RADIUS * 2.0, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.maelstrom").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		DarktidePower.cooldown(player, DarktideState.MAELSTROM, COOLDOWN);
	}

	/** Waves round {@code center}: rolling in to it from the dome's edge, or out from it past the edge. */
	private static void waves(ServerLevel level, Vec3 center, boolean in) {
		for (int i = 0; i < WAVES; i++) {
			double a = Math.PI * 2.0 * i / WAVES;
			Vec3 out = new Vec3(Math.cos(a), 0.0, Math.sin(a));
			if (in) {
				MagicFx.wave(level, center.add(out.scale(RADIUS)), out.scale(-1.0), RADIUS * 0.8, (float) RADIUS * 0.9F, DarktideLooks.WATER, SWELL_TICKS);
			} else {
				MagicFx.wave(level, center.add(out.scale(RADIUS * 0.3)), out, RADIUS, (float) RADIUS * 0.9F, DarktideLooks.WATER, WAVE_TICKS);
			}
		}
	}

	private static boolean inside(Dome dome, Vec3 at) {
		return at.distanceToSqr(dome.center) <= RADIUS * RADIUS && at.y >= dome.center.y - 1.5;
	}

	/** The caster's own tick: Flow held full, and the swim (flight) for as long as they're in the water. */
	static void tick(ServerPlayer player, DarktideLive live) {
		Dome dome = live.maelstrom;
		if (dome == null) {
			return;
		}
		if (player.level() != dome.level || !Fate.isAscended(player)) {
			end(player, false);
			return;
		}
		live.flow = DarktideFlow.MAX;
		boolean in = inside(dome, player.position().add(0.0, player.getBbHeight() / 2.0, 0.0));
		if (in != dome.swimming) {
			dome.swimming = in;
			swim(player, in);
		}
	}

	private static void swim(ServerPlayer player, boolean on) {
		if (on) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.getAbilities().setFlyingSpeed(SWIM_SPEED);
		} else {
			player.getAbilities().setFlyingSpeed(NORMAL_FLY_SPEED);
			// creative and spectator keep their own flight
			if (!player.isCreative() && !player.isSpectator()) {
				player.getAbilities().mayfly = false;
				player.getAbilities().flying = false;
			}
		}
		player.onUpdateAbilities();
	}

	/** Ends {@code player}'s Maelstrom, if one stands: with {@code crash}, the water comes down on everyone in it. */
	static void end(ServerPlayer player, boolean crash) {
		DarktideLive live = DarktidePower.liveIfAny(player);
		Dome dome = live == null ? null : live.maelstrom;
		if (dome == null) {
			return;
		}
		live.maelstrom = null;
		DOMES.remove(dome);
		if (dome.swimming) {
			swim(player, false);
			// set down gently: the water's own fall is not the caster's
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
		}
		if (crash) {
			crash(dome);
		}
	}

	private static void crash(Dome dome) {
		ServerLevel level = dome.level;
		Vec3 c = dome.center;
		for (LivingEntity victim : victims(dome)) {
			if (DarktideMoves.hit(dome.owner, victim, CRASH_DAMAGE)) {
				victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.2, 0.0, 0.2).add(0.0, -0.9, 0.0));
				victim.syncVelocity = true;
				if (DarktideStatus.humanoid(victim)) {
					DarktideStatus.cripple(victim, dome.owner);
				}
			}
		}
		MagicFx.ring(level, c.add(0.0, 0.1, 0.0), (float) RADIUS * 1.3F, FOAM);
		MagicFx.blast(level, c.add(0.0, 1.0, 0.0), (float) RADIUS * 0.5F, COLOR);
		// it comes down and runs out every way, and leaves the ground wet
		waves(level, c, false);
		MagicFx.pool(level, c, (float) RADIUS * 0.9F, DarktideLooks.WATER, DarktideLooks.POOL_TICKS * 2, 4);
		level.sendParticles(MagicFx.miasma(DarktideLooks.WATER, 2.4F), c.x, c.y + 0.4, c.z, 60, RADIUS * 0.45, 0.3, RADIUS * 0.45, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2.5F, 0.35F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 2.0F, 0.5F);
		com.jaymospowers.core.Shakes.heavy(dome.owner, c, RADIUS * 2.5, 1.4F);
	}

	private static List<LivingEntity> victims(Dome dome) {
		return dome.level.getEntitiesOfClass(LivingEntity.class, new AABB(dome.center, dome.center).inflate(RADIUS + 1.0),
			e -> e.isAlive() && !e.isSpectator() && !DarktideMoves.friendly(dome.owner, e)
				&& inside(dome, e.position().add(0.0, e.getBbHeight() / 2.0, 0.0)));
	}

	static void tickWorld(MinecraftServer server) {
		if (DOMES.isEmpty()) {
			return;
		}
		for (Iterator<Dome> it = new ArrayList<>(DOMES).iterator(); it.hasNext(); ) {
			Dome dome = it.next();
			ServerLevel level = dome.level;
			long now = level.getGameTime();
			if (dome.owner.isRemoved() || !dome.owner.isAlive()) {
				end(dome.owner, false);
				DOMES.remove(dome);
				continue;
			}
			if (now >= dome.until) {
				end(dome.owner, true);
				continue;
			}
			// the dome: a great ball of dark water sunk to its middle in the ground, swelling up as it's called
			float swell = Math.min(1.0F, (now - dome.start + 1) / (float) SWELL_TICKS);
			ZFighterKi.waterOrb(level, -(dome.owner.getId() * 4 + 2), dome.center, RADIUS * 2.0 * (1.0 - (1.0 - swell) * (1.0 - swell)), COLOR);
			var random = level.getRandom();
			for (int i = 0; i < 10; i++) {
				// ink turning slowly through it
				double a = random.nextDouble() * Math.PI * 2.0;
				double r = Math.sqrt(random.nextDouble()) * RADIUS * 0.9;
				double y = random.nextDouble() * Math.sqrt(Math.max(0.0, RADIUS * RADIUS - r * r));
				level.sendParticles(MagicFx.miasma(0x0C2450, 1.8F), dome.center.x + Math.cos(a) * r, dome.center.y + y, dome.center.z + Math.sin(a) * r,
					1, 0.2, 0.2, 0.2, 0.0);
			}
			for (LivingEntity victim : victims(dome)) {
				Vec3 off = victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0).subtract(dome.center);
				double d = off.length();
				if (d > RADIUS - 1.5) {
					// can't swim out: pulled back from the skin
					Vec3 back = off.normalize().scale(-0.3);
					victim.setDeltaMovement(back.x, Math.min(back.y, 0.05), back.z);
				} else {
					// hanging in the water: no footing, a slow rise
					Vec3 v = victim.getDeltaMovement();
					victim.setDeltaMovement(v.x * 0.6, Math.min(0.06, v.y * 0.6 + 0.03), v.z * 0.6);
				}
				victim.syncVelocity = true;
				victim.resetFallDistance();
				victim.clearFire();
				victim.setAirSupply(Math.max(-19, victim.getAirSupply() - 6));
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2), dome.owner);
				if (victim instanceof Player) {
					victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, true, false, false), dome.owner);
				}
				if (now % DROWN_EVERY == 0) {
					victim.setInvulnerableTime(0);
					victim.hurtServer(level, level.damageSources().drown(), DROWN_DAMAGE);
					DarktideTide.enemy(dome.owner, victim);
				}
			}
		}
	}

	static void clear() {
		DOMES.clear();
	}
}
