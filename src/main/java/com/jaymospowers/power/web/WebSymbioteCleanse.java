package com.jaymospowers.power.web;

import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.fx.MagicFx;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The pale burst: the anti (white) strain's host throws out a wash of itself that the black and the red cannot bear
 * (the radial menu's Burst chip). Within {@link #RANGE} blocks it tears the black and the red symbiote off every other
 * host it reaches (players, whatever they were wearing it as, an infection too; a mob a roaming symbiote took is spat
 * out, a risen body one has swollen is brought down), sends every loose symbiote crawling away, and leaves nobody
 * carrying its seed. The anti's own kind is untouched. Costs {@link #COST} satiety, then {@link #COOLDOWN} ticks.
 */
public final class WebSymbioteCleanse {
	/** The pale wash reaches this far (blocks), costs this much satiety, and is ready again after this many ticks. */
	public static final double RANGE = 6.0;
	public static final int COST = 10, COOLDOWN = 600;
	/** The wash's colour. */
	private static final int PALE = 0xF2F0E4;
	/** The ability index of its cooldown (past the ring's six abilities), and how many cooldown slots there are. */
	public static final int ABILITY = 11;
	private static final int COOLDOWN_SLOTS = 13;
	private static final Soundscape.Layer CHIME = Soundscape.Layer.of(0.8F, 1.0F, 1.4F, 1.7F, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundEvents.BELL_RESONATE);
	private static final Soundscape.Layer HISS = Soundscape.Layer.of(0.7F, 0.9F, 0.5F, 0.7F, SoundEvents.SLIME_BLOCK_BREAK, SoundEvents.SLIME_DEATH);

	private WebSymbioteCleanse() {
	}

	/** The host is the anti strain, and may throw its wash. */
	public static boolean available(Player player) {
		return WebSymbiote.isBonded(player) && WebSymbiote.state(player).kind() == WebSymbioteStrain.WHITE;
	}

	/** The radial menu's Burst chip. */
	static void burst(ServerPlayer player) {
		if (!available(player) || !player.isAlive() || player.isSpectator() || WebSymbiote.driving(player) || WebSymbioteBond.dormant(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebSymbioteState.Cooldowns cooldowns = player.getAttached(WebSymbiote.COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebSymbioteState.Cooldowns.NONE;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		boolean cooling = cooldowns.end(ABILITY) > now;
		if (cooling || state.satiety() < COST && !player.isCreative()) {
			WebSymbiote.whisper(player, cooling ? "cleanse.spent" : "cleanse.hungry");
			return;
		}
		player.setAttached(WebSymbiote.COOLDOWNS, cooldowns.with(ABILITY, now + COOLDOWN, COOLDOWN_SLOTS));
		if (!player.isCreative()) {
			WebSymbiote.set(player, state.withSatiety(state.satiety() - COST));
		}
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		MagicFx.blast(level, at, (float)RANGE, PALE);
		MagicFx.ring(level, player.position(), (float)RANGE, PALE);
		MagicFx.burst(level, at.x, at.y, at.z, PALE, 1.1F, 30, RANGE * 0.4, 0.6, RANGE * 0.4);
		Soundscape.play(level, at, SoundSource.PLAYERS, CHIME);
		Soundscape.play(level, at, SoundSource.PLAYERS, HISS);
		com.jaymospowers.core.Shakes.send(player, 0.5F, null);
		WebSymbiote.whisper(player, "cleanse.1");

		AABB box = player.getBoundingBox().inflate(RANGE);
		List<Entity> near = level.getEntities(player, box, e -> e.distanceToSqr(player) <= RANGE * RANGE);
		for (Entity entity : near) {
			if (entity instanceof ServerPlayer host && host.isAlive() && !host.isSpectator() && WebSymbiote.isBonded(host)
				&& WebSymbiote.state(host).kind() != WebSymbioteStrain.WHITE) {
				// torn off whatever it was wearing: the suit, the armour, the big body, the infection alike
				WebSymbiote.whisper(host, "cleanse.torn");
				WebSymbiote.withdraw(host).spurn(host);
				host.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false));
				host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, false, false));
				level.sendParticles(ParticleTypes.SQUID_INK, host.getX(), host.getY() + 1.0, host.getZ(), 30, 0.4, 0.7, 0.4, 0.06);
			} else if (entity instanceof Mob mob && mob.isAlive() && WebSymbioteStrains.of(mob) != WebSymbioteStrain.WHITE) {
				if (WebInhabit.isHost(mob)) {
					WebInhabit.reject(level, mob);
				} else if (WebBehemoth.isHost(mob)) {
					// the risen body it swelled falls when the symbiote is torn out of it (it leaves as it falls)
					mob.kill(level);
				}
			}
		}
		// every loose one (the ones just torn out too) runs
		for (WebSymbioteBlob blob : level.getEntitiesOfClass(WebSymbioteBlob.class, box, b -> b.isAlive())) {
			blob.startle(player.position());
		}
	}
}
