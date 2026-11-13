package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Modify memory (fifth edition): the villager or folk you look at forgets the ill they hold of you (a theft they saw, a blow, a
 * lie, a threat), and is left with the warm feeling of a pleasant word. If their mind holds, they feel the tampering and
 * remember that instead: a threat, and a worse standing than before. A boss's mind shrugs it off ({@link Legendary}).
 */
public final class Forgetting {
	static final int COLOUR = 0x9A7AD8;

	private Forgetting() {
	}

	static boolean cast(ServerLevel level, ServerPlayer caster) {
		LivingEntity target = MindSpells.target(caster);
		if (target == null || !Recollections.rememberer(target) || !SpellParts.canSpeak(caster)) {
			return false;
		}
		Fx.beam(level, caster.getEyePosition().add(0.0, -0.3, 0.0), target.getEyePosition(), 0.1F, COLOUR, 12);
		if (Legendary.shrugs(target) || Saves.resist(target, Saves.Kind.ILLUSION)) {
			// a mind that holds feels the tug at it
			Recollections.remember(target, caster, Recollections.Kind.THREATENED, Component.empty());
			MindSpells.shrugged(level, target);
			return true;
		}
		Recollections.forgetIll(target, caster);
		Recollections.remember(target, caster, Recollections.Kind.KIND, Component.empty());
		if (target instanceof Mob mob && mob.getTarget() == caster) {
			mob.setTarget(null);
		}
		level.sendParticles(Fx.rune(COLOUR, 0.9F), target.getX(), target.getEyeY() + 0.3, target.getZ(), 10, 0.25, 0.2, 0.25, 0.03);
		level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 0.5F);
		Speech.privately(target, caster, Component.translatable("speech.jaymoskingdoms.forgetting." + level.getRandom().nextInt(3)), COLOUR);
		return true;
	}
}
