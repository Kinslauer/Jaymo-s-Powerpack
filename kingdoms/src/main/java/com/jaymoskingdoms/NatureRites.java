package com.jaymoskingdoms;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.Vec3;

/**
 * The land's bigger magic: plant growth and the rite of weather (moonbeam and the heroes' feast are {@link Moonbeam} and
 * {@link HeroesFeast}, the animals' tales {@link Passers}).
 * <ul>
 * <li><b>Plant growth:</b> the crops, stems, saplings and berries within {@link #GROW_RADIUS} of where you look ripen as a whole
 * season would, at once. The earth pays for it: the farmland under them dries out, and must be watered again.</li>
 * <li><b>Control weather:</b> a great rite (thirty seconds still with a book, {@link Rituals}) that turns the sky to clear, rain or
 * storm, the one picked on the Magic screen, for half a day. It only works under the open sky of the overworld.</li>
 * </ul>
 */
public final class NatureRites {
	public static final Set<String> SPELLS = Set.of("plant_growth", "control_weather", "moonbeam", "heroes_feast");
	/** Plant growth: how far from the spot it reaches (blocks) across and up and down, how many times a plant is coaxed. */
	static final int GROW_RADIUS = 12, GROW_UP = 3, GROW_DOWN = 2, COAXINGS = 6;
	/** The sky's rite: how long the new weather lasts (ticks), and how long the rite takes (ticks). */
	static final int SKY_TICKS = 12000;
	public static final int RITE_TICKS = 20 * 30;
	static final int GREEN = 0x7BC96A, SKY = 0xBFD8F0;

	private NatureRites() {
	}

	static void register() {
	}

	public static boolean ours(String spell) {
		return SPELLS.contains(spell);
	}

	static boolean cast(String spell, ServerLevel level, Player player, float power) {
		if (!(player instanceof ServerPlayer caster)) {
			return false;
		}
		return switch (spell) {
			case "plant_growth" -> grow(level, caster);
			case "moonbeam" -> Moonbeam.cast(level, caster, power);
			case "heroes_feast" -> HeroesFeast.cast(level, caster);
			default -> weather(level, caster);
		};
	}

	private static boolean grow(ServerLevel level, ServerPlayer caster) {
		Vec3 at = NewSpells.aim(level, caster, NewSpells.AIM_REACH);
		BlockPos centre = BlockPos.containing(at);
		int grown = 0;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-GROW_RADIUS, -GROW_DOWN, -GROW_RADIUS), centre.offset(GROW_RADIUS, GROW_UP, GROW_RADIUS))) {
			if (p.distSqr(centre) > GROW_RADIUS * GROW_RADIUS) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
				level.setBlock(p, crop.getStateForAge(crop.getMaxAge()), 2);
			} else if (state.getBlock() instanceof BonemealableBlock b && b.getType() == BonemealableBlock.Type.GROWER
				&& !(state.getBlock() instanceof CropBlock)) {
				for (int i = 0; i < COAXINGS; i++) {
					BlockState now = level.getBlockState(p);
					if (!(now.getBlock() instanceof BonemealableBlock nb) || !nb.isValidBonemealTarget(level, p, now, BonemealSource.INTERACTION)) {
						break;
					}
					nb.performBonemeal(level, level.getRandom(), p, now, BonemealSource.INTERACTION);
				}
			} else {
				continue;
			}
			grown++;
			tire(level, p.below());
			if (grown <= 40) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (grown == 0) {
			return false;
		}
		Fx.ring(level, at.add(0.0, 0.1, 0.0), GROW_RADIUS * 0.7F, GREEN);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 0.8F);
		return true;
	}

	/** The season's growth costs the ground: farmland under it is bone dry. */
	private static void tire(ServerLevel level, BlockPos soil) {
		BlockState s = level.getBlockState(soil);
		if (s.getBlock() instanceof FarmlandBlock) {
			level.setBlock(soil, s.setValue(FarmlandBlock.MOISTURE, 0), 2);
		}
	}

	private static boolean weather(ServerLevel level, ServerPlayer caster) {
		if (level.dimension() != Level.OVERWORLD || !level.canSeeSky(caster.blockPosition().above())) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rite.sky"));
			return false;
		}
		if (!level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rite.still"));
			return false;
		}
		String to = Choices.get(caster, "weather");
		WeatherData w = level.getWeatherData();
		switch (to) {
			case "clear" -> {
				w.setClearWeatherTime(SKY_TICKS);
				w.setRainTime(0);
				w.setThunderTime(0);
				w.setRaining(false);
				w.setThundering(false);
			}
			case "rain" -> {
				w.setClearWeatherTime(0);
				w.setRainTime(SKY_TICKS);
				w.setThunderTime(SKY_TICKS);
				w.setRaining(true);
				w.setThundering(false);
			}
			default -> {
				w.setClearWeatherTime(0);
				w.setRainTime(SKY_TICKS);
				w.setThunderTime(SKY_TICKS);
				w.setRaining(true);
				w.setThundering(true);
			}
		}
		Fx.sigil(level, caster.position().add(0.0, 0.05, 0.0), 5.0F, SKY, 60);
		Fx.beam(level, caster.position().add(0.0, 0.2, 0.0), caster.position().add(0.0, 24.0, 0.0), 0.5F, SKY, 40);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}
}
