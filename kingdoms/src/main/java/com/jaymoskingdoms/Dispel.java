package com.jaymoskingdoms;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dispel: ends a lasting spell or curse on a thing or a person. Aimed at someone it takes the held spell out of their
 * hands, lets go of fear, charm, holds and nets, lifts the moon's curse and a blight, strips an imbued blade, ends a walking
 * spell, unmakes a raised thrall and clears the spell-borne effects. Aimed at a field of ground magic it ends the field;
 * with a cursed thing in hand it lifts the curse. A boss may shrug it off (spending a resistance), and a foe may save.
 * Court wizards and camp shamans dispel each other's lasting spells too ({@link #answer}).
 */
public final class Dispel {
	/** How far a dispel reaches for a field. */
	static final double FIELD_REACH = 3.0;
	/** The spell-borne effects a dispel clears. */
	private static final Set<Holder<MobEffect>> SPELL_EFFECTS = Set.of(MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.DARKNESS,
		MobEffects.LEVITATION, MobEffects.SLOW_FALLING, MobEffects.ABSORPTION, MobEffects.STRENGTH, MobEffects.SPEED, MobEffects.INVISIBILITY,
		MobEffects.GLOWING, MobEffects.WITHER, MobEffects.MINING_FATIGUE, MobEffects.NAUSEA);
	/** A court wizard's or shaman's dispel: how often they may try (ticks), how far they see a rival, the chance it takes. */
	static final int NPC_GAP = 20 * 10;
	static final double NPC_REACH = 18.0;
	static final float NPC_CHANCE = 0.7F;

	private static final Map<LivingEntity, Long> NPC_READY = new WeakHashMap<>();

	private Dispel() {
	}

	/** Casts a dispel for {@code caster}: what they look at, else a field, else a cursed thing, else themselves. */
	static boolean cast(ServerLevel level, Player caster, float power) {
		LivingEntity target = Arcana.sighted(caster);
		boolean done;
		if (target != null) {
			done = person(level, caster, target);
			if (!done) {
				return true;
			}
		} else {
			Vec3 at = NewSpells.aim(level, caster, NewSpells.AIM_REACH);
			Fields.Field field = Fields.nearest(level, at, FIELD_REACH);
			if (field != null) {
				Fields.dispel(level, field.at, 0.5);
				done = true;
			} else {
				done = lift(level, caster) || person(level, caster, caster);
			}
		}
		if (!done) {
			return false;
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 1.5F);
		return true;
	}

	/** Lifts a curse from what {@code caster} holds or wears. */
	private static boolean lift(ServerLevel level, Player caster) {
		boolean any = false;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = caster.getItemBySlot(slot);
			if (ItemCurses.cursed(stack) && ItemCurses.lift(stack)) {
				any = true;
			}
		}
		if (any) {
			level.sendParticles(Fx.LIGHT, caster.getX(), caster.getY(1.0), caster.getZ(), 20, 0.4, 0.5, 0.4, 0.05);
		}
		return any;
	}

	/** Ends every lasting spell on {@code who}. False (and no sign) if there was nothing on them. */
	static boolean person(ServerLevel level, @Nullable LivingEntity by, LivingEntity who) {
		if (by != who && Legendary.shrugs(who)) {
			return true;
		}
		boolean foe = by != null && by != who && !who.isAlliedTo(by) && who instanceof Mob;
		if (foe && Saves.resist(who, Saves.Kind.CURSE)) {
			level.sendParticles(ParticleTypes.SMOKE, who.getX(), who.getY(1.0), who.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
			return true;
		}
		boolean any = Concentration.drop(who);
		if (Fear.of(who) != null) {
			Fear.calm(who);
			any = true;
		}
		if (Charm.charmed(who)) {
			Charm.free(who);
			any = true;
		}
		if (HeldFast.locked(who)) {
			HeldFast.release(who);
			any = true;
		}
		if (Nets.netted(who)) {
			Nets.free(who, false);
			any = true;
		}
		if (PowersCompat.cureCurses(who)) {
			any = true;
		}
		if (Deafened.clear(who)) {
			any = true;
		}
		Necromancy.cleanse(who);
		PowersCompat.endMoves(who, null);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = who.getItemBySlot(slot);
			if (!stack.isEmpty() && (Spellcraft.unimbue(stack) || foe && ItemCurses.cursed(stack) && ItemCurses.lift(stack))) {
				any = true;
			}
		}
		if (who instanceof Mob mob && Thralls.isThrall(mob) && by != who) {
			Thralls.crumble(level, mob);
			any = true;
		}
		for (Holder<MobEffect> effect : SPELL_EFFECTS) {
			if (who.hasEffect(effect) && who.removeEffect(effect)) {
				any = true;
			}
		}
		if (any) {
			level.sendParticles(Fx.LIGHT, who.getX(), who.getY(1.0), who.getZ(), 24, 0.4, 0.6, 0.4, 0.06);
			Fx.ring(level, who.position(), 1.4F, 0xFFFFFFFF);
		}
		return any;
	}

	/**
	 * A court wizard or camp shaman answers the other side: now and then they snuff the lasting spell a rival caster is
	 * holding (the shaman's war chant, the wizard's ward) or the ground magic of an enemy lying near them.
	 */
	static void answer(ServerLevel level, Sorcery.Caster caster) {
		PathfinderMob mob = caster.mob();
		long now = level.getGameTime();
		if (NPC_READY.getOrDefault(mob, 0L) > now || Will.hushed(mob) || !SpellParts.canSpeak(mob)) {
			return;
		}
		boolean wizard = caster.role() == Sorcery.WIZARD;
		boolean shaman = caster.role() == Sorcery.SHAMAN || caster.role() == Orcs.SHAMAN;
		if (!wizard && !shaman) {
			return;
		}
		for (PathfinderMob rival : level.getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(NPC_REACH),
			r -> r.isAlive() && Counterspell.opposed(mob, r) && Concentration.holding(r) != null && mob.hasLineOfSight(r))) {
			NPC_READY.put(mob, now + NPC_GAP);
			Sorcery.beam(level, mob, rival.getBoundingBox().getCenter(), Counterspell.colourOf(caster.role()));
			if (mob.getRandom().nextFloat() < NPC_CHANCE) {
				Concentration.drop(rival);
				level.sendParticles(Fx.LIGHT, rival.getX(), rival.getY(1.0), rival.getZ(), 18, 0.3, 0.5, 0.3, 0.05);
				level.playSound(null, rival.getX(), rival.getY(), rival.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.9F, 1.4F);
			}
			return;
		}
		// else, ground magic of a player (a web or a ward line) lying close to their post
		Fields.Field field = Fields.nearest(level, mob.position(), 6.0);
		if (field != null && !(field.owner.equals(mob.getUUID())) && level.getPlayerByUUID(field.owner) != null && mob.getRandom().nextFloat() < NPC_CHANCE) {
			NPC_READY.put(mob, now + NPC_GAP);
			Sorcery.beam(level, mob, field.at, Counterspell.colourOf(caster.role()));
			Fields.dispel(level, field.at, 0.5);
		}
	}
}
