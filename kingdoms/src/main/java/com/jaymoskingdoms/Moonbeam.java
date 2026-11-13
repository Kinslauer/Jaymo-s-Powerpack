package com.jaymoskingdoms;

import com.jaymoskingdoms.dragons.Disguises;
import com.jaymoskingdoms.monsters.Werewolf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Moonbeam (fifth edition): a column of cold light on the ground where you look, held for half a minute. Whatever wears a false
 * shape in it is forced into its true one: a werewolf (a monster, or a player under the curse) shows the beast and cannot keep its
 * human face while the light is on it, and a dragon walking as a villager is shown for what it is. Anything dead and walking
 * in the light burns. Other systems that have shapechangers (a copy that wears a face) plug in through {@link #addReveal}.
 */
public final class Moonbeam {
	/** The column's reach (blocks) and how long it is held (ticks). */
	static final double RADIUS = 3.0;
	static final int TICKS = 20 * 30, EVERY = 10;
	/** How long a body stays in its true form after the light leaves it (ticks). */
	static final int LINGER = 40;
	/** A burning dead thing takes this much each time. */
	static final float BURN = 2.0F;

	/** Something that can force a shapechanger back into its true form. */
	public interface Reveal {
		/**
		 * The moonbeam of {@code caster} falls on {@code who} until {@code until} (game time): true if they were a shapechanger and
		 * were shown.
		 */
		boolean reveal(ServerLevel level, LivingEntity who, ServerPlayer caster, long until);
	}

	private static final List<Reveal> REVEALS = new ArrayList<>();

	private Moonbeam() {
	}

	/** Lets another system's shapechangers be forced into their true form by a moonbeam. */
	public static void addReveal(Reveal reveal) {
		REVEALS.add(reveal);
	}

	static void register() {
		addReveal((level, who, caster, until) -> {
			if (who instanceof Werewolf w) {
				w.moonlight(until);
				return true;
			}
			return false;
		});
		addReveal((level, who, caster, until) -> who instanceof ServerPlayer p && WerewolfPlayers.moonbeam(p, until));
		addReveal((level, who, caster, until) -> who instanceof Villager v && Disguises.moonReveal(level, caster, v));
	}

	static boolean cast(ServerLevel level, ServerPlayer caster, float power) {
		Vec3 at = NewSpells.aim(level, caster, NewSpells.AIM_REACH);
		int ticks = Math.round(TICKS * power);
		Fields.Field field = Fields.add(Fields.Kind.MOONBEAM, level, at, RADIUS, 0.0, ticks, caster.getUUID(), null);
		Concentration.hold(caster, "moonbeam", ticks, field::end);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.7F);
		return true;
	}

	/** One tick of a moonbeam: the shapechangers in it are shown, the dead in it burn. */
	static void tick(Fields.Field f, long now) {
		if ((now - f.made) % EVERY != 0) {
			return;
		}
		ServerLevel level = f.level;
		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(f.owner);
		if (caster == null) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(f.at, f.at).inflate(f.radius + 1.0, 4.0, f.radius + 1.0),
			x -> Fields.body(x) && x != caster && f.contains(x.position()))) {
			for (Reveal r : REVEALS) {
				if (r.reveal(level, e, caster, now + LINGER)) {
					level.sendParticles(Fx.mote(f.kind.colour, 0.8F), e.getX(), e.getY(1.0), e.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
					break;
				}
			}
			if (e.is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
				e.hurtServer(level, level.damageSources().indirectMagic(caster, caster), BURN);
			}
		}
	}
}
