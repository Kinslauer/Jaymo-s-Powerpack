package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Potion clashes: a second potion drunk while the first still works may fight it. There is a chance each time (less with
 * Wits: a sharper head keeps its brews apart), and then one of four things: the two cancel (the first is gone and the second
 * does nothing), both double (their strengths are as long again), the second turns harmful (a sour draught: poison and a
 * swimming head), or it blasts in the belly (a few points of damage and a puff of smoke; no block is touched).
 */
public final class PotionClash {
	/** The base chance of a clash, what each Wits point takes off it, its floor, and how long a drink counts as working (ticks). */
	static final double CHANCE = 0.45, WITS_CUT = 0.004, FLOOR = 0.12;
	static final int WORKING = 6000, SOUR_POISON = 120, SOUR_NAUSEA = 240;
	static final float BELLY = 4.0F;

	private record Drink(List<Holder<MobEffect>> effects, long at) {
	}

	private static final Map<UUID, Drink> LAST = new HashMap<>();

	private PotionClash() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/** The chance a second potion clashes with the first for {@code player}. */
	static double chance(ServerPlayer player) {
		return Math.max(FLOOR, CHANCE - WITS_CUT * Stats.value(player, Stats.Stat.WITS));
	}

	/**
	 * {@code player} drinks {@code contents}; true if the drink is then not to take effect as usual (the mixin cancels it).
	 */
	public static boolean drinking(ServerPlayer player, PotionContents contents) {
		List<Holder<MobEffect>> effects = new ArrayList<>();
		contents.forEachEffect(e -> effects.add(e.getEffect()), 1.0F);
		if (effects.isEmpty()) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getServer().getTickCount();
		Drink before = LAST.get(player.getUUID());
		LAST.put(player.getUUID(), new Drink(effects, now));
		if (before == null || now - before.at() > WORKING || before.effects().stream().noneMatch(player::hasEffect)
			|| player.getRandom().nextDouble() >= chance(player)) {
			return false;
		}
		switch (player.getRandom().nextInt(4)) {
			case 0 -> {
				// they cancel
				before.effects().forEach(player::removeEffect);
				say(player, "cancel");
				level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
				return true;
			}
			case 1 -> {
				// both double: the old effects are lengthened, the new ones will be lengthened as they land
				List<MobEffectInstance> old = new ArrayList<>(player.getActiveEffects());
				for (MobEffectInstance i : old) {
					if (before.effects().contains(i.getEffect())) {
						player.addEffect(new MobEffectInstance(i.getEffect(), i.getDuration() * 2, i.getAmplifier(), i.isAmbient(), i.isVisible(), i.showIcon()));
					}
				}
				contents.forEachEffect(e -> player.addEffect(new MobEffectInstance(e.getEffect(), e.getDuration() * 2, e.getAmplifier(), e.isAmbient(), e.isVisible(),
					e.showIcon())), 1.0F);
				say(player, "double");
				Fx.ring(level, player.position().add(0, 1, 0), 1.2F, 0x80E0A0);
				return true;
			}
			case 2 -> {
				// the second turns
				player.addEffect(new MobEffectInstance(MobEffects.POISON, SOUR_POISON, 0));
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SOUR_NAUSEA, 0));
				say(player, "sour");
				return true;
			}
			default -> {
				// it blasts in the belly, and then takes hold as usual
				player.hurtServer(level, level.damageSources().magic(), BELLY);
				Fx.blast(level, player.position().add(0, 1, 0), 1.0F, 0xB8A8C8);
				level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
				say(player, "belly");
				return false;
			}
		}
	}

	private static void say(ServerPlayer player, String outcome) {
		player.sendSystemMessage(Component.translatable("potion.jaymoskingdoms.clash." + outcome), true);
	}
}
