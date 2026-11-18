package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Fx;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Afterblow, the labyrinth-builders' prize (found on a pedestal behind the last rune gate, {@link LabyrinthKeep}): a
 * bronze blade that strikes twice. Every blow that lands is followed, {@link #DELAY} ticks later, by a second at
 * {@link #SHARE} of the wielder's strength, straight through the pause a hit gives its victim; the second blow is a hit
 * of its own that does not echo again.
 */
public class Afterblow extends Item {
	/** How long after a blow the second lands (ticks), and the share of the wielder's attack damage it strikes with. */
	static final int DELAY = 8;
	static final float SHARE = 0.75F;
	/** The echo's colour (a pale amber). */
	static final int GLOW = 0xFFD890;

	/** A second blow waiting: who struck whom, the tick (server) it lands. */
	private record Echo(LivingEntity victim, LivingEntity wielder, int due) {
	}

	private static final List<Echo> WAITING = new ArrayList<>();

	public Afterblow(Item.Properties properties) {
		super(properties);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Afterblow::land);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity victim, LivingEntity wielder) {
		super.postHurtEnemy(stack, victim, wielder);
		if (victim.level() instanceof ServerLevel level && victim.isAlive()) {
			WAITING.add(new Echo(victim, wielder, level.getServer().getTickCount() + DELAY));
		}
	}

	/** Lands the second blows that are due. */
	private static void land(MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		List<Echo> due = new ArrayList<>();
		WAITING.removeIf(echo -> {
			if (echo.due() > now) {
				return false;
			}
			due.add(echo);
			return true;
		});
		for (Echo echo : due) {
			LivingEntity victim = echo.victim(), wielder = echo.wielder();
			if (!victim.isAlive() || !wielder.isAlive() || !(victim.level() instanceof ServerLevel level)) {
				continue;
			}
			DamageSource source = wielder instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(wielder);
			// the pause a blow gives its victim is skipped, so the second one lands
			victim.setInvulnerableTime(0);
			victim.hurtServer(level, source, (float) wielder.getAttributeValue(Attributes.ATTACK_DAMAGE) * SHARE);
			Vec3 at = victim.position().add(0.0, victim.getBbHeight() * 0.6, 0.0);
			Fx.burst(level, at.x, at.y, at.z, GLOW, 0.8F, 8, 0.25, 0.25, 0.25);
			level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.5F);
		}
	}
}
