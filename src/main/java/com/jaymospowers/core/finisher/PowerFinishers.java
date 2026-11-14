package com.jaymospowers.core.finisher;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Crushing;
import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.finisher.Finishers.Kind;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Malfunction;
import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.chrono.ChronoPower;
import com.jaymospowers.power.cryo.CryoPower;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.resonance.ResonancePower;
import com.jaymospowers.power.smoke.SmokePower;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "a qte finisher per power per enemy type". A power holder's empty hands finish someone downed with the power
 * itself, one move per power (the {@code P_*} kinds, {@link Finishers.Strike#POWER}), and every one of them is prompted
 * ({@link Prompts}): two blows, a key before each. The Z Fighter and the symbiote suit keep their own executions.
 * <p>
 * The move is the power's; what it does to the body is the body's ({@link Body}, the eight kinds the downed poses know):
 * the effect is sized and aimed by the body's own box, and the kill breaks each kind its own way ({@link #broken}).
 */
public final class PowerFinishers {
	/** How far round the kill shakes other players' screens, and how hard. */
	static final double AROUND_RANGE = 12.0;
	static final float AROUND_SHAKE = 0.5F;
	/** A crushing kill's cracks in the ground under them. */
	static final int CRACK_LENGTH = 4;
	/** How high over the body a thing called down starts (Arsenal's rift, Gravity's weight, Wind's column), in the body's heights. */
	static final double ABOVE = 2.5;
	/** Colours the powers don't carry themselves: the web's silk, frost's white, the rift. */
	static final int SILK = 0xF2F2F2, FROST_WHITE = 0xEAF8FF;

	/** What kind of body it is done to. */
	public enum Body { PERSON, MACHINE, BEAST, BUG, WINGED, FISH, BLOB, LUMP }

	private PowerFinishers() {
	}

	/** The body kind of {@code body}, as the downed poses sort them. */
	public static Body body(LivingEntity body) {
		if (Malfunction.is(body)) {
			return Body.MACHINE;
		}
		BodyPlan plan = BodyPlan.of(body);
		if (plan == BodyPlan.HUMANOID) {
			return Body.PERSON;
		}
		if (plan == BodyPlan.ARTHROPOD) {
			return Body.BUG;
		}
		if (plan == BodyPlan.FLYER) {
			return Body.WINGED;
		}
		MobCategory category = body.getType().getCategory();
		if (category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT || category == MobCategory.UNDERGROUND_WATER_CREATURE) {
			return Body.FISH;
		}
		if (plan == BodyPlan.SIMPLE) {
			return body instanceof Slime ? Body.BLOB : Body.LUMP;
		}
		return Body.BEAST;
	}

	/** The move {@code player}'s power gives their empty hands, or null (no power, or one with executions of its own). */
	public static @Nullable Kind kind(ServerPlayer player) {
		for (Kind kind : Kind.values()) {
			Power power = power(kind);
			if (power != null && Powers.has(player, power)) {
				return kind;
			}
		}
		return null;
	}

	private static @Nullable Power power(Kind kind) {
		return switch (kind) {
			case P_ARSENAL -> ArsenalPower.INSTANCE;
			case P_CHRONO -> ChronoPower.INSTANCE;
			case P_CRYO -> CryoPower.INSTANCE;
			case P_DARKTIDE -> DarktidePower.INSTANCE;
			case P_GRAVITY -> GravityPower.INSTANCE;
			case P_RESONANCE -> ResonancePower.INSTANCE;
			case P_SMOKE -> SmokePower.INSTANCE;
			case P_WEB -> WebPower.INSTANCE;
			case P_WIND -> WindPower.INSTANCE;
			case P_XENOFORM -> XenoformPower.INSTANCE;
			default -> null;
		};
	}

	private static int color(Kind kind) {
		Power power = power(kind);
		return kind == Kind.P_WEB ? SILK : kind == Kind.P_ARSENAL ? MagicFx.VOID_COLOR : power == null ? MagicFx.LIGHT_COLOR : power.color() & 0xFFFFFF;
	}

	// ---- beat by beat -----------------------------------------------------------------------------------------------------

	/** It begins ({@code Finishers.begin}): what's heard first. */
	static void begin(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind) {
		double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
		switch (kind) {
			case P_ARSENAL -> level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.6F);
			case P_CHRONO -> level.playSound(null, x, y, z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, 1.6F);
			case P_CRYO -> level.playSound(null, x, y, z, SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.0F, 0.6F);
			case P_DARKTIDE -> level.playSound(null, x, y, z, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 0.8F, 0.7F);
			case P_RESONANCE -> level.playSound(null, x, y, z, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0F, 0.8F);
			case P_SMOKE -> level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.6F);
			case P_WEB -> level.playSound(null, x, y, z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.3F);
			case P_WIND -> level.playSound(null, x, y, z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.9F, 1.0F);
			default -> level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
	}

	/** Each tick before the kill ({@code Finishers.plays}): the power gathering on them, every other tick. */
	static void tick(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, int t, boolean present) {
		if (!present || t >= kind.kill() || t % 2 != 0) {
			return;
		}
		Vec3 middle = body.getBoundingBox().getCenter();
		Vec3 hand = hand(player, body);
		double w = body.getBbWidth() * 0.5, h = body.getBbHeight();
		int color = color(kind);
		float fill = t / (float) kind.kill();
		switch (kind) {
			// the rift open over them, turning
			case P_ARSENAL -> level.sendParticles(MagicFx.rune(color, 1.0F), middle.x, body.getY() + h * ABOVE, middle.z, 2, w, 0.05, w, 0.0);
			// their hours running out of them, back toward the hand
			case P_CHRONO -> MagicFx.line(level, middle, hand, MagicFx.rune(color, 0.6F + fill * 0.6F), 4);
			// frost creeping up over them from the ground
			case P_CRYO -> level.sendParticles(MagicFx.mote(FROST_WHITE, 0.8F), middle.x, body.getY() + h * fill, middle.z, 3 + (int) (fill * 5), w, 0.05, w, 0.0);
			// the dark water rising round them
			case P_DARKTIDE -> level.sendParticles(MagicFx.miasma(color, 1.0F + fill), middle.x, body.getY() + 0.1, middle.z, 3, w * 1.4, 0.05, w * 1.4, 0.0);
			// lifted dust, falling upward
			case P_GRAVITY -> level.sendParticles(MagicFx.mote(color, 0.8F), middle.x, body.getY() + h * (0.5 + fill), middle.z, 3, w * 1.2, 0.2, w * 1.2, 0.0);
			// the note, closing on their pitch
			case P_RESONANCE -> {
				if (t % 4 == 0) {
					MagicFx.ring(level, middle, null, (float) (w * 3.0 * (1.0F - fill) + w), color, 6);
					level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6F, 0.6F + fill * 1.4F);
				}
			}
			// smoke run from the hand into their mouth
			case P_SMOKE -> MagicFx.line(level, hand, head(body), MagicFx.miasma(color, 0.7F), 5);
			// silk from the hands, wrapping them
			case P_WEB -> MagicFx.line(level, hand, middle.add(0.0, (fill - 0.5) * h, 0.0), MagicFx.mote(color, 0.5F), 6);
			// the air drawn up off them into the fist
			case P_WIND -> MagicFx.line(level, middle, hand.add(0.0, 0.6, 0.0), ParticleTypes.CLOUD, 2);
			// the band's light, brightening on the wrist
			default -> level.sendParticles(MagicFx.wisp(color, 0.6F + fill * 1.2F), hand.x, hand.y, hand.z, 2, 0.06, 0.06, 0.06, 0.0);
		}
	}

	/** One of the move's two blows ({@code Finishers.blow}, which then jolts the body, shakes the screens and kills on the last). */
	static void blow(ServerLevel level, ServerPlayer player, LivingEntity body, Kind kind, boolean kill) {
		Vec3 middle = body.getBoundingBox().getCenter();
		Vec3 hand = hand(player, body);
		Vec3 above = new Vec3(middle.x, body.getY() + body.getBbHeight() * ABOVE, middle.z);
		double x = middle.x, y = middle.y, z = middle.z;
		float w = body.getBbWidth();
		float pitch = (kill ? 0.7F : 1.0F) + level.getRandom().nextFloat() * 0.2F;
		int color = color(kind);
		boolean crush = false;
		switch (kind) {
			case P_ARSENAL -> {
				// Whatever Answers: a blade out of the rift pins them, then a greater one falls through them
				MagicFx.sigil(level, above, w * (kill ? 1.2F : 0.7F), color, 12);
				MagicFx.stroke(level, above, middle, kill ? 0.5F : 0.25F, color);
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, pitch);
				level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, pitch * 1.4F);
			}
			case P_CHRONO -> {
				// Borrowed Hour: a year taken off them at a pull, then all the rest at once
				MagicFx.ring(level, middle, null, w * (kill ? 2.2F : 1.2F), color, MagicFx.RING_TICKS);
				level.sendParticles(MagicFx.rune(color, 1.2F), x, y, z, kill ? 30 : 10, w * 0.5, 0.4, w * 0.5, 0.05);
				level.playSound(null, x, y, z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, kill ? 1.2F : 0.7F, kill ? 0.5F : 1.4F);
				if (kill) {
					level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 40, w * 0.5, 0.4, w * 0.5, 0.02);
				}
			}
			case P_CRYO -> {
				// Deep Frost: frozen where they lie, then broken like ice
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), x, y, z, kill ? 40 : 12, w * 0.5, 0.3, w * 0.5, 0.1);
				level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, kill ? 30 : 10, w * 0.5, 0.3, w * 0.5, 0.05);
				level.playSound(null, x, y, z, kill ? SoundEvents.GLASS_BREAK : SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.1F, pitch);
				if (kill) {
					MagicFx.blast(level, middle, w * 1.5F, FROST_WHITE);
				} else {
					body.setTicksFrozen(body.getTicksRequiredToFreeze() + 100);
				}
			}
			case P_DARKTIDE -> {
				// Undertow: the pool opens under them and takes hold, then one stroke of the tide across them
				MagicFx.pool(level, body.position().add(0.0, 0.05, 0.0), w * (kill ? 1.8F : 1.2F), color, 24);
				level.sendParticles(ParticleTypes.SPLASH, x, body.getY() + 0.1, z, kill ? 40 : 14, w * 0.6, 0.1, w * 0.6, 0.2);
				level.playSound(null, x, y, z, kill ? SoundEvents.PLAYER_SPLASH_HIGH_SPEED : SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0F, pitch);
				if (kill) {
					Vec3 side = side(player, body).scale(w * 1.5 + 0.5);
					MagicFx.stroke(level, middle.subtract(side), middle.add(side), 0.5F, color);
					level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
				}
			}
			case P_GRAVITY -> {
				// Dead Weight: their own weight piled on them, twice
				MagicFx.ring(level, body.position().add(0.0, 0.05, 0.0), null, w * (kill ? 2.4F : 1.4F), color, MagicFx.RING_TICKS);
				MagicFx.beam(level, above, middle, w * 0.3F, color, 6);
				level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, kill ? 1.2F : 0.8F, pitch * 0.8F);
				dust(level, body, kill ? 32 : 14);
				crush = true;
			}
			case P_RESONANCE -> {
				// Breaking Pitch: the note found, then sung through them
				MagicFx.ring(level, middle, null, w * (kill ? 2.6F : 1.4F), color, MagicFx.RING_TICKS);
				level.playSound(null, x, y, z, kill ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, kill ? 1.0F : 0.5F, kill ? 1.2F : 1.6F);
				if (kill) {
					MagicFx.wave(level, hand, middle.subtract(hand).normalize(), hand.distanceTo(middle) + 1.0, w, color, 8);
				}
			}
			case P_SMOKE -> {
				// Last Breath: the smoke goes in, and comes out all at once
				MagicFx.line(level, hand, head(body), MagicFx.miasma(color, 1.2F), 8);
				level.sendParticles(kill ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE, x, y, z, kill ? 24 : 8, w * 0.4, 0.3, w * 0.4, 0.03);
				level.playSound(null, x, y, z, kill ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0F, pitch * 0.7F);
			}
			case P_WEB -> {
				// Cocoon: wound tight in silk, then the lines hauled apart
				Vec3 side = side(player, body).scale(w + 0.4);
				MagicFx.stroke(level, hand, middle.add(side), 0.12F, color);
				MagicFx.stroke(level, hand, middle.subtract(side), 0.12F, color);
				level.playSound(null, x, y, z, kill ? SoundEvents.SKELETON_HURT : SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.1F, pitch * 0.7F);
				level.sendParticles(MagicFx.mote(color, 0.8F), x, y, z, kill ? 20 : 8, w * 0.5, 0.3, w * 0.5, 0.02);
			}
			case P_WIND -> {
				// Emptied Sky: the air pulled out of them, then all of it brought down on them
				MagicFx.wave(level, above, new Vec3(0.0, -1.0, 0.0), above.y - body.getY(), w * (kill ? 1.6F : 0.9F), color, 8);
				level.sendParticles(ParticleTypes.GUST, x, body.getY() + 0.2, z, kill ? 3 : 1, w * 0.3, 0.0, w * 0.3, 0.0);
				level.playSound(null, x, y, z, kill ? SoundEvents.WIND_CHARGE_BURST.value() : SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.1F, pitch);
				dust(level, body, kill ? 28 : 10);
				crush = kill;
			}
			default -> {
				// Star-Metal: the band's light through them, a thread and then the whole of it
				MagicFx.beam(level, hand, middle, kill ? 0.3F : 0.1F, color, kill ? 10 : 5);
				MagicFx.blast(level, middle, w * (kill ? 1.4F : 0.6F), color);
				level.playSound(null, x, y, z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, kill ? 1.1F : 0.6F, kill ? 0.7F : 1.6F);
			}
		}
		if (kill) {
			broken(level, body, crush);
			Shakes.around(level, middle, AROUND_RANGE, AROUND_SHAKE);
		}
	}

	/**
	 * How the kill breaks each kind of body: a person or beast bleeds, a machine sparks and rings, a bug is crushed flat
	 * under a crushing move (Kitchen's legs, no body), a winged thing sheds, a fish thrashes water, a slime bursts, and a
	 * lump just raises dust.
	 */
	private static void broken(ServerLevel level, LivingEntity body, boolean crush) {
		Vec3 middle = body.getBoundingBox().getCenter();
		double x = middle.x, y = middle.y, z = middle.z, w = body.getBbWidth() * 0.5;
		if (crush) {
			GroundCracks.crack(level, BlockPos.containing(body.getX(), body.getBoundingBox().minY - 0.1, body.getZ()), level.getRandom(), CRACK_LENGTH);
		}
		switch (body(body)) {
			case MACHINE -> {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, w, 0.3, w, 0.2);
				level.playSound(null, x, y, z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			case BUG -> {
				if (crush) {
					Crushing.mark(body);
				}
				BladeBlood.crushed(body);
			}
			case WINGED -> {
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.FEATHER), x, y, z, 14, w, 0.3, w, 0.12);
				BladeBlood.splash(body, 4.0F);
			}
			case FISH -> {
				level.sendParticles(ParticleTypes.SPLASH, x, y, z, 30, w, 0.2, w, 0.3);
				BladeBlood.splash(body, 3.0F);
			}
			case BLOB -> {
				level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 40, w, 0.3, w, 0.2);
				level.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.6F);
			}
			case LUMP -> dust(level, body, 24);
			default -> {
				if (crush) {
					BladeBlood.crushed(body);
				} else {
					BladeBlood.splash(body, body(body) == Body.BEAST ? 8.0F : 6.0F);
				}
			}
		}
	}

	// ---- where things are --------------------------------------------------------------------------------------------------

	/** About where the hand held out at them is. */
	private static Vec3 hand(ServerPlayer player, LivingEntity body) {
		Vec3 d = body.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		d = d.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : d.normalize();
		double scale = player.getScale();
		return player.position().add(d.scale(0.6 * scale)).add(0.0, 1.0 * scale, 0.0);
	}

	/** Across them, level, as the player sees it. */
	private static Vec3 side(ServerPlayer player, LivingEntity body) {
		Vec3 d = body.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		d = d.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : d.normalize();
		return new Vec3(-d.z, 0.0, d.x);
	}

	/** Their head, lying or kneeling. */
	private static Vec3 head(LivingEntity body) {
		return Finishers.headAt(body).add(0.0, 0.25, 0.0);
	}

	private static void dust(ServerLevel level, LivingEntity body, int count) {
		BlockState on = body.getBlockStateOn();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, on.isAir() ? Blocks.DIRT.defaultBlockState() : on), body.getX(),
			body.getBoundingBox().minY + 0.05, body.getZ(), count, body.getBbWidth() * 0.6, 0.05, body.getBbWidth() * 0.6, 0.15);
	}
}
