package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.xenoform.nadir.NadirAim;
import com.jaymospowers.power.xenoform.nadir.NadirGraviton;
import com.jaymospowers.power.xenoform.nadir.NadirNet;
import com.jaymospowers.power.xenoform.nadir.NadirRift;
import com.jaymospowers.power.xenoform.nadir.NadirRules;
import com.jaymospowers.power.xenoform.nadir.NadirStash;
import com.jaymospowers.power.xenoform.nadir.NadirState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Nadir, the void given shape: a dark figure, an imposing silhouette with a face of nothing and only the xeno
 * insignia on its chest to say what it is. No creature carries it: the two voices in the band give it
 * ({@link com.jaymospowers.power.xenoform.XenoVoices}), and only to a band that has ascended and been corrupted.
 * <ul>
 * <li>R, Beacon: tap to pick a player, anywhere in the world (they show through everything, to everyone, for a
 * moment); hold for {@link #PHASE_HOLD} ticks to phase to them, whatever world they're in.</li>
 * <li>G, the Rift ({@link NadirRift}): tap at a creature for Recursive Drop, at nothing to blink; hold to Surface;
 * sneak + G opens the Void Pocket ({@link com.jaymospowers.power.xenoform.nadir.NadirStash}).</li>
 * <li>V, the Graviton ({@link NadirGraviton}): at a creature, Heavy Hand (tap yank, hold slam; sneaking, shove and
 * launch); at nothing, tap for a Weightless zone, hold for an Event Horizon.</li>
 * <li>B, the Command ({@link NadirRules}): the Rules screen, where the void writes what the world must do.</li>
 * </ul>
 * It takes no fall, fire or drowning, and its body is the strongest the band knows. G V B are from-scratch takes on
 * the Rift, Graviton and Command powers of The Awakening (all rights reserved: ideas only).
 */
public final class Nadir extends XenoForm {
	// ---- tuning ----
	/** Tap R again within this many ticks to go to the next player; hold R this long to phase. */
	public static final int TAP_WINDOW = 10, PHASE_HOLD = 30, PHASE_COOLDOWN = 100;
	/** The beacon's glow (ticks), refreshed while R is held. */
	public static final int GLOW_TICKS = 60;

	/** Flight like creative's (double-tap jump), this fast (creative's is 0.05). Free and endless. */
	public static final float FLY_SPEED = 0.16F;
	/** Creative's flying speed, put back when Nadir goes. */
	private static final float NORMAL_FLY_SPEED = 0.05F;

	public Nadir() {
		super("nadir", 1.15F, 60.0F, 6.0, 0.2, 1.0, 0.6, Items.ENDER_EYE, 0x7A4FD0);
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		player.getAbilities().mayfly = true;
		player.getAbilities().setFlyingSpeed(FLY_SPEED);
		player.onUpdateAbilities();
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return player.getAbilities().flying ? XenoformState.FLY_VOID : XenoformState.FLY_NONE;
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return true;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return player.getAbilities().flying;
	}

	@Override
	public boolean scans(Entity creature) {
		return false;
	}

	@Override
	public boolean hidden() {
		return true;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.beaconHeld = 0;
		live.beaconTarget = null;
		NadirGraviton.end(player, live.nadir);
		live.nadir.clear();
		// back to the player's own flight: creative and spectator keep theirs
		player.getAbilities().setFlyingSpeed(NORMAL_FLY_SPEED);
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
		}
		player.onUpdateAbilities();
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1) {
			if (down) {
				if (!ready(player, live, R)) {
					return;
				}
				long now = player.level().getGameTime();
				boolean again = now - live.beaconTapAt <= TAP_WINDOW + PHASE_HOLD;
				live.beaconTapAt = now;
				live.beaconHeld = 1;
				pick(player, live, again);
			} else {
				live.beaconHeld = 0;
			}
			return;
		}
		NadirState st = live.nadir;
		long now = player.level().getGameTime();
		if (key == PowerKey.ABILITY_2) {
			rift(player, live, st, now, down);
		} else if (key == PowerKey.ABILITY_3) {
			graviton(player, live, st, now, down);
		} else if (key == PowerKey.ABILITY_4 && down && ready(player, live, B)) {
			NadirNet.open(player);
			sound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 0.6F);
			cooldown(player, live, B, 10);
		}
	}

	/** G: sneak for the pocket; a tap drops what you look at, or blinks; the hold (in {@link #tick}) surfaces. */
	private static void rift(ServerPlayer player, XenoformPower.Live live, NadirState st, long now, boolean down) {
		if (down) {
			if (player.isShiftKeyDown()) {
				NadirStash.open(player);
			} else if (ready(player, live, G)) {
				st.riftDownAt = now;
				st.riftFired = false;
			}
			return;
		}
		if (st.riftDownAt < 0L || st.riftFired) {
			st.riftDownAt = -1L;
			return;
		}
		st.riftDownAt = -1L;
		LivingEntity target = NadirAim.aimed(player, NadirRift.DROP_REACH);
		if (target != null) {
			if (NadirRift.drop(player, st, target)) {
				cooldown(player, live, G, NadirRift.DROP_COOLDOWN);
			} else {
				say(player, "rift_no_room");
			}
		} else if (NadirRift.blink(player)) {
			cooldown(player, live, G, NadirRift.BLINK_COOLDOWN);
		} else {
			say(player, "rift_nowhere");
		}
	}

	/** V: picks its grip as it goes down; the tap fires on release, the hold (in {@link #tick}) once it's long enough. */
	private static void graviton(ServerPlayer player, XenoformPower.Live live, NadirState st, long now, boolean down) {
		if (down) {
			if (ready(player, live, V)) {
				LivingEntity target = NadirAim.aimed(player, NadirGraviton.HAND_REACH);
				st.gravDownAt = now;
				st.gravTarget = target == null ? -1 : target.getId();
				st.gravFired = false;
			}
			return;
		}
		if (st.gravDownAt < 0L || st.gravFired) {
			st.gravDownAt = -1L;
			return;
		}
		st.gravDownAt = -1L;
		LivingEntity target = gripped(player, st);
		if (target != null) {
			NadirGraviton.tap(player, target, player.isShiftKeyDown());
			cooldown(player, live, V, NadirGraviton.TAP_COOLDOWN);
		} else if (st.gravTarget < 0) {
			NadirGraviton.weightless(player, st);
			cooldown(player, live, V, NadirGraviton.ZONE_COOLDOWN);
		}
	}

	/** The creature V took hold of, while it's still alive and in reach (the grip breaks past half again the reach). */
	private static LivingEntity gripped(ServerPlayer player, NadirState st) {
		if (st.gravTarget < 0) {
			return null;
		}
		Entity e = player.level().getEntity(st.gravTarget);
		double reach = NadirGraviton.HAND_REACH * 1.25;
		return e instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) <= reach * reach ? living : null;
	}

	private static void say(ServerPlayer player, String what) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.nadir." + what).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		NadirState st = live.nadir;
		long now = level.getGameTime();
		holds(player, live, st, now);
		NadirRift.tickDrop(player, st);
		NadirGraviton.tick(player, st);
		NadirRules.tick(player, st);
		// the void about it
		if (player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.7, 0.4, 0.0);
		}
		if (player.tickCount % 7 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.5, 0.8, 0.5, 0.0);
		}
		if (live.beaconHeld > 0) {
			ServerPlayer target = target(player, live);
			if (target == null) {
				live.beaconHeld = 0;
				return;
			}
			live.beaconHeld++;
			if (live.beaconHeld % 20 == 1) {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
			}
			int bars = Math.min(10, live.beaconHeld * 10 / PHASE_HOLD);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.beacon_hold", target.getDisplayName(),
				"|".repeat(bars) + ".".repeat(10 - bars)).withStyle(ChatFormatting.LIGHT_PURPLE));
			if (live.beaconHeld >= PHASE_HOLD) {
				phase(player, live, target);
			}
		}
	}

	/** G and V held: Surface, the Heavy Hand's hold, the Event Horizon, once they've been held long enough. */
	private static void holds(ServerPlayer player, XenoformPower.Live live, NadirState st, long now) {
		if (st.riftDownAt >= 0L && !st.riftFired) {
			long held = now - st.riftDownAt;
			if (held >= NadirRift.SURFACE_HOLD) {
				st.riftFired = true;
				if (NadirRift.surface(player)) {
					cooldown(player, live, G, NadirRift.SURFACE_COOLDOWN);
				} else {
					say(player, "surface_none");
				}
			} else if (held >= 4) {
				bar(player, "surface_hold", held, NadirRift.SURFACE_HOLD);
			}
		}
		if (st.gravDownAt >= 0L && !st.gravFired) {
			long held = now - st.gravDownAt;
			LivingEntity target = gripped(player, st);
			if (st.gravTarget >= 0 && target == null) {
				// the grip broke
				st.gravFired = true;
			} else if (target != null && held >= NadirGraviton.HAND_HOLD) {
				st.gravFired = true;
				NadirGraviton.hold(player, st, target, player.isShiftKeyDown());
				cooldown(player, live, V, NadirGraviton.HOLD_COOLDOWN);
			} else if (target == null && held >= NadirGraviton.HORIZON_HOLD) {
				st.gravFired = true;
				if (st.horizon != null) {
					say(player, "horizon_open");
				} else if (now < st.horizonNext) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.nadir.horizon_wait",
						(st.horizonNext - now + 19) / 20).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
				} else {
					NadirGraviton.horizon(player, st);
					cooldown(player, live, V, NadirGraviton.HOLD_COOLDOWN);
				}
			} else if (target == null && held >= 6) {
				bar(player, "horizon_hold", held, NadirGraviton.HORIZON_HOLD);
			}
		}
	}

	private static void bar(ServerPlayer player, String what, long held, int full) {
		int bars = (int)Math.min(10, held * 10 / full);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.nadir." + what, "|".repeat(bars) + ".".repeat(10 - bars))
			.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Nothing of the world touches it; what does strike it answers to the Command's "what hits me" rules. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
			|| source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) {
			return false;
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
			NadirRules.onHurtBy(player, live.nadir, attacker);
		}
		return true;
	}

	/** What Nadir hurts answers to the Command's "what I hit" rules. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		NadirRules.onHit(player, live.nadir, target);
	}

	/** Every other player on the server, by name. */
	private static List<ServerPlayer> others(ServerPlayer player) {
		List<ServerPlayer> list = new ArrayList<>(player.level().getServer().getPlayerList().getPlayers());
		list.removeIf(p -> p == player || p.isSpectator());
		list.sort(Comparator.comparing(p -> p.getGameProfile().name()));
		return list;
	}

	private static ServerPlayer target(ServerPlayer player, XenoformPower.Live live) {
		if (live.beaconTarget == null) {
			return null;
		}
		ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(live.beaconTarget);
		return target == null || target == player || target.isSpectator() ? null : target;
	}

	/** Picks the beacon: the next player after the current one ({@code again}), else the first; says who. */
	private static void pick(ServerPlayer player, XenoformPower.Live live, boolean again) {
		List<ServerPlayer> others = others(player);
		if (others.isEmpty()) {
			live.beaconTarget = null;
			live.beaconHeld = 0;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.beacon_alone").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return;
		}
		int index = 0;
		if (again && live.beaconTarget != null) {
			for (int i = 0; i < others.size(); i++) {
				if (others.get(i).getUUID().equals(live.beaconTarget)) {
					index = (i + 1) % others.size();
					break;
				}
			}
		}
		ServerPlayer target = others.get(index);
		live.beaconTarget = target.getUUID();
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
		target.level().sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
	}

	/** Through the void, to just behind them. */
	private static void phase(ServerPlayer player, XenoformPower.Live live, ServerPlayer target) {
		ServerLevel from = player.level();
		ServerLevel to = target.level();
		Vec3 flat = target.getLookAngle().horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 behind = target.position().subtract(flat.scale(1.5));
		if (!to.noCollision(player, player.getBoundingBox().move(behind.subtract(player.position())))) {
			behind = target.position();
		}
		from.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.8, 0.4, 0.2);
		from.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.5F);
		if (from != to) {
			// what Nadir holds open stays in the world it was made in: let it go quietly
			NadirGraviton.end(player, live.nadir);
			live.nadir.clear();
		}
		player.teleportTo(to, behind.x, behind.y, behind.z, Set.of(), target.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		to.sendParticles(ParticleTypes.REVERSE_PORTAL, behind.x, behind.y + 1.0, behind.z, 60, 0.4, 0.8, 0.4, 0.2);
		to.playSound(null, behind.x, behind.y, behind.z, SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.5F);
		to.playSound(null, behind.x, behind.y, behind.z, SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
		target.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.beacon_arrived", player.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
		live.beaconHeld = 0;
		cooldown(player, live, R, PHASE_COOLDOWN);
	}
}
