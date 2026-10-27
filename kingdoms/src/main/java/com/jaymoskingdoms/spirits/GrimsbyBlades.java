package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.241) Grimsby's five blade forms. Andy: "the idea of summoning your mana spirit like a stand that forms into different
 * weapons". The hands form a weapon of aura and strike with it; each form is its own {@link Move} (the player cycles them with
 * the spirit key's NEXT), and each is a three-tap combo: the third blow in a row hits {@link #FINISHER} times as hard. A combo
 * lapses if the next tap comes more than {@link #COMBO_TICKS} after the move was ready again. Blows are magic from the
 * player ({@code indirectMagic}) scaled by {@link Spirits#damageScale}, and never land on a friend ({@link SpiritFoes}).
 */
final class GrimsbyBlades {
	/** The third tap of a combo hits this many times as hard; the combo lapses this many ticks after its cooldown. */
	static final float FINISHER = 1.5F;
	static final int COMBO_TICKS = 20;
	/** The axe and the heavy slam knock a raised shield aside for this long (seconds). */
	static final float SHIELD_SECONDS = 5.0F;
	/** The hammer throws its foe this far up (blocks per tick), and a greatsword sweep shoves this hard. */
	static final double HAMMER_LIFT = 0.7, SWEEP_SHOVE = 0.6;
	/** The slash mote colour: Grimsby's steel-blue-white. */
	static final DustParticleOptions EDGE = new DustParticleOptions(0x9AB8FF, 1.0F);

	/** One blade form: its tuning. */
	record Form(String id, int pose, float cost, int cooldown, float damage, double reach, double halfArc, int most, boolean breaks, boolean lifts) {
	}

	static final Form SWORD = new Form("blade_sword", Poses.SWORD, 5.0F, 12, 6.0F, 3.0, 28.0, 1, false, false);
	static final Form GREATSWORD = new Form("blade_greatsword", Poses.GREATSWORD, 8.0F, 22, 10.0F, 3.5, 60.0, 99, false, false);
	static final Form SPEAR = new Form("blade_spear", Poses.SPEAR, 6.0F, 16, 7.0F, 5.0, 10.0, 2, false, false);
	static final Form AXE = new Form("blade_axe", Poses.AXE, 7.0F, 18, 9.0F, 3.2, 35.0, 1, true, false);
	static final Form HAMMER = new Form("blade_hammer", Poses.HAMMER, 9.0F, 32, 12.0F, 3.2, 40.0, 1, false, true);

	/** How far into its combo each player is: the form, the blows struck, and when the last landed. */
	private record Combo(String form, int blows, long last) {
	}

	private static final Map<UUID, Combo> COMBOS = new HashMap<>();

	static void forget(UUID id) {
		COMBOS.remove(id);
	}

	private GrimsbyBlades() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> COMBOS.clear());
	}

	/** The five forms as moves. */
	static List<Move> moves() {
		return List.of(new Blade(SWORD), new Blade(GREATSWORD), new Blade(SPEAR), new Blade(AXE), new Blade(HAMMER));
	}

	/** A blade form as a move. */
	private record Blade(Form form) implements Move {
		@Override
		public String id() {
			return form.id();
		}

		@Override
		public float cost() {
			return form.cost();
		}

		@Override
		public int cooldown() {
			return form.cooldown();
		}

		@Override
		public int level() {
			return 1;
		}

		@Override
		public int pose() {
			return form.pose();
		}

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			return strike(player, state, form);
		}
	}

	/** One tap of {@code form}: false if there was nothing in reach. */
	private static boolean strike(ServerPlayer player, SpiritState state, Form form) {
		ServerLevel level = (ServerLevel)player.level();
		long now = level.getGameTime();
		List<LivingEntity> foes = SpiritFoes.cone(player, form.reach(), form.halfArc(), true);
		if (foes.isEmpty()) {
			return false;
		}
		Combo combo = COMBOS.get(player.getUUID());
		int blows = combo != null && combo.form().equals(form.id()) && now - combo.last() <= form.cooldown() + COMBO_TICKS ? combo.blows() : 0;
		blows++;
		boolean last = blows >= 3;
		COMBOS.put(player.getUUID(), new Combo(form.id(), last ? 0 : blows, now));
		float damage = form.damage() * Spirits.damageScale(state) * (last ? FINISHER : 1.0F);
		slash(level, player, form, last);
		for (LivingEntity foe : foes.subList(0, Math.min(form.most(), foes.size()))) {
			if (form.breaks()) {
				SpiritFoes.breakShield(level, foe, SHIELD_SECONDS);
			}
			if (SpiritFoes.strike(level, player, foe, damage)) {
				if (form.lifts()) {
					foe.setDeltaMovement(foe.getDeltaMovement().add(0.0, HAMMER_LIFT * (last ? 1.3 : 1.0), 0.0));
					foe.syncVelocity = true;
				} else if (form == GREATSWORD) {
					SpiritFoes.shove(level, player, foe, SWEEP_SHOVE * (last ? 1.5 : 1.0));
				}
				level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.6), foe.getZ(), last ? 14 : 6, 0.25, 0.3, 0.25, 0.2);
			}
		}
		if (form == HAMMER) {
			PowersCompat.shake(player, last ? 0.5F : 0.25F);
		}
		if (player.getRandom().nextFloat() < 0.15F) {
			SpiritVoice.say(player, "move");
		}
		return true;
	}

	/** The arc of the cut: motes the attuned see, a sparkle for all, and the sound of it. */
	private static void slash(ServerLevel level, ServerPlayer player, Form form, boolean last) {
		Vec3 eye = player.getEyePosition().add(0.0, -0.35, 0.0);
		Vec3 look = player.getLookAngle();
		double half = Math.toRadians(Math.max(form.halfArc(), 20.0));
		int points = form == GREATSWORD ? 14 : form == SPEAR ? 8 : 9;
		for (int i = 0; i < points; i++) {
			double t = (i / (double)(points - 1)) * 2.0 - 1.0;
			Vec3 dir = form == SPEAR ? look : look.yRot((float)(t * half));
			double dist = form == SPEAR ? form.reach() * (i + 1) / points : form.reach() * 0.85;
			Vec3 p = eye.add(dir.scale(dist));
			if (form == HAMMER) {
				p = p.add(0.0, 0.8 - Math.abs(t) * 0.8, 0.0);
			}
			Mana.sendSeen(level, EDGE, p.x, p.y, p.z, last ? 3 : 1, 0.05, 0.05, 0.05, 0.0);
			Mana.sendSeen(level, Mana.MOTE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
			level.sendParticles(form == GREATSWORD && i % 4 == 0 ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		float pitch = form == HAMMER ? 0.6F : form == GREATSWORD ? 0.8F : form == SPEAR ? 1.2F : 1.0F;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), form == HAMMER ? SoundEvents.ANVIL_LAND : last ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP,
			SoundSource.PLAYERS, form == HAMMER ? 0.5F : 0.9F, pitch);
	}
}
