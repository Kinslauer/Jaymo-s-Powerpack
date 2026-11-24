package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnifferEggBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

/**
 * The lines of a scouter scan, built on the server when a hold begins: a creature's (health, strike, speed, armour, and its
 * kind's own: a horse's stats, a person's realm and mood, a Z Fighter's forms, an alien's growth, a dead walker's road to
 * the horde), a plant's or living block's (what stage, what it needs, how long), and the place's (heat, how harsh the land,
 * the sky). Every line is a translatable component, so the client's own language reads them.
 */
final class ZFighterScanInfo {
	/** Something a person's mood bands: at most this is low, uneasy, steady, cheerful; above the last, elated. */
	static final int[] MOOD_EDGES = {-8, -2, 3, 9};
	/** Heat bands by a biome's base temperature: freezing, cold, mild, warm; hotter is scorching. */
	static final float[] HEAT_EDGES = {0.0F, 0.3F, 0.95F, 1.5F};
	/** The game's default random tick speed, and ticks to a minute (the growth estimates). */
	static final double RANDOM_TICK_SPEED = 3.0, MINUTE = 1200.0;
	/** A crop and a sapling need this much light to grow. */
	static final int GROW_LIGHT = 9;
	/** Chance a random tick advances a sapling's stage, as one in this. */
	static final int SAPLING_ONE_IN = 7;
	/** A sniffer egg hatches a stage this often (ticks), half that long on moss. */
	static final long EGG_STAGE_TICKS = 24000L;
	/** Branch forms are told apart from the regular forms in the forms-seen list by this much. */
	static final int BRANCH_BASE = 100;

	private ZFighterScanInfo() {
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("hud.jaymospowers.scan." + key, args);
	}

	private static String one(double value) {
		return String.format("%.1f", value);
	}

	// ---- creatures ----

	/** The lines of {@code target}'s reading; {@code seen} are the form codes {@code viewer} has seen a Z Fighter take. */
	static List<Component> creature(ServerPlayer viewer, LivingEntity target, Collection<Integer> seen) {
		List<Component> lines = new ArrayList<>();
		lines.add(t("health", one(target.getHealth()), one(target.getMaxHealth())));
		if (target.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) && target.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0) {
			lines.add(t("strike", one(target.getAttributeValue(Attributes.ATTACK_DAMAGE))));
		}
		List<Component> horse = ZFighterScanHorse.lines(target);
		if (horse.isEmpty()) {
			lines.add(t("speed", one(target.getAttributeValue(Attributes.MOVEMENT_SPEED) * ZFighterScanHorse.BLOCKS_PER_SECOND)));
		}
		lines.addAll(horse);
		if (target.getAttributeValue(Attributes.ARMOR) > 0.0) {
			lines.add(t("armour", one(target.getAttributeValue(Attributes.ARMOR))));
		}
		if (target.isBaby()) {
			lines.add(t("young"));
		}
		if (target instanceof TamableAnimal pet && pet.isTame()) {
			lines.add(t("tame"));
		}
		if (target instanceof AbstractVillager || target instanceof Player) {
			String realm = ZFighterScanKingdoms.realm(viewer.level(), target);
			if (!realm.isEmpty()) {
				lines.add(t("realm", realm));
			}
		}
		int mood = ZFighterScanKingdoms.mood(target);
		if (mood != ZFighterScanKingdoms.NO_MOOD) {
			int band = 0;
			while (band < MOOD_EDGES.length && mood > MOOD_EDGES[band]) {
				band++;
			}
			lines.add(t("mood", t("mood." + band)));
		}
		forms(target, seen, lines);
		return lines;
	}

	/** The form lines: a Z Fighter's ladder, an alien's growth, a dead walker's road. Stages not yet met show as blanks. */
	private static void forms(LivingEntity target, Collection<Integer> seen, List<Component> lines) {
		if (target instanceof ServerPlayer fighter && Powers.has(fighter, ZFighterPower.INSTANCE)) {
			List<Component> ladder = new ArrayList<>();
			for (int form : ZFighterForms.choices(fighter)) {
				if (form <= 10) {
					ladder.add(form == 0 ? t("form_base") : seen.contains(form) ? Component.translatable(ZFighterLineage.formKey(fighter, form)) : null);
				}
			}
			for (int branch : ZFighterBranchForms.ladder(fighter)) {
				ladder.add(seen.contains(BRANCH_BASE + branch) ? Component.translatable(ZFighterBranchForms.key(branch)) : null);
			}
			if (ladder.size() > 1) {
				lines.add(t("forms", road(ladder)));
			}
		}
		if (target instanceof Player player) {
			XenoForm alien = XenoformState.form(player);
			if (alien != null) {
				XenoUltimate ultimate = XenoformState.ultimate(player);
				List<Component> growth = new ArrayList<>();
				growth.add(alien.name());
				if (alien.evolved() != null) {
					growth.add(ultimate != null ? ultimate.name() : null);
				}
				lines.add(t("growth", road(growth)));
			}
		}
		if (target instanceof Zombie dead) {
			int stage = ZFighterScanKingdoms.horde(dead);
			if (stage >= 0) {
				List<Component> road = new ArrayList<>();
				for (int i = 0; i <= 2; i++) {
					road.add(i <= stage ? t("horde." + i) : null);
				}
				lines.add(t("road", road(road)));
			}
		}
	}

	/** Stages joined by arrows; a null stage is a blank. */
	private static Component road(List<Component> stages) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < stages.size(); i++) {
			out.append(i == 0 ? Component.empty() : Component.literal(" > "));
			out.append(stages.get(i) == null ? Component.literal("_") : stages.get(i));
		}
		return out;
	}

	/** The form code a Z Fighter's current form is remembered by in a viewer's list (regular, or branch plus {@link #BRANCH_BASE}). */
	static int formCode(ServerPlayer fighter) {
		int branch = ZFighterBranchForms.of(fighter);
		return branch != 0 ? BRANCH_BASE + branch : ZFighterPower.live(fighter).form;
	}

	// ---- plants and living blocks ----

	/** The lines of the plant or living block at {@code pos}; empty if it is neither. */
	static List<Component> block(ServerLevel level, BlockPos pos) {
		List<Component> lines = new ArrayList<>();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof CropBlock crop) {
			int age = crop.getAge(state), max = crop.getMaxAge();
			lines.add(t("stage", age, max));
			if (age >= max) {
				lines.add(t("ripe"));
			} else {
				int light = level.getRawBrightness(pos, 0);
				lines.add(t("needs.crop", GROW_LIGHT, light, t(wet(level, pos) ? "wet" : "dry")));
				if (light >= GROW_LIGHT) {
					lines.add(t("time", minutes((max - age) * cropStageTicks(level, pos))));
				}
			}
		} else if (state.getBlock() instanceof SaplingBlock) {
			int stage = state.getValue(SaplingBlock.STAGE);
			int light = level.getRawBrightness(pos.above(), 0);
			lines.add(t("stage", stage, 1));
			lines.add(t("needs.sapling", GROW_LIGHT, light));
			if (light >= GROW_LIGHT) {
				lines.add(t("time", minutes((2 - stage) * SAPLING_ONE_IN * stageTicksPerTick())));
			}
		} else if (state.getBlock() instanceof BeehiveBlock) {
			int honey = BeehiveBlockEntity.getHoneyLevel(state);
			lines.add(t("honey", honey, BeehiveBlock.MAX_HONEY_LEVELS));
			if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
				lines.add(t("bees", hive.getOccupantCount(), BeehiveBlockEntity.MAX_OCCUPANTS));
			}
			lines.add(t(honey >= BeehiveBlock.MAX_HONEY_LEVELS ? "ripe" : "honey_needs"));
		} else if (state.getBlock() instanceof TurtleEggBlock) {
			lines.add(t("stage", state.getValue(TurtleEggBlock.HATCH), TurtleEggBlock.MAX_HATCH_LEVEL));
			lines.add(t("eggs", state.getValue(TurtleEggBlock.EGGS)));
			lines.add(t("needs.turtle"));
		} else if (state.getBlock() instanceof SnifferEggBlock egg) {
			int hatch = egg.getHatchLevel(state);
			boolean moss = SnifferEggBlock.hatchBoost(level, pos);
			lines.add(t("stage", hatch, SnifferEggBlock.MAX_HATCH_LEVEL));
			lines.add(t(moss ? "needs.sniffer_moss" : "needs.sniffer"));
			lines.add(t("time", minutes((SnifferEggBlock.MAX_HATCH_LEVEL - hatch + 1) * EGG_STAGE_TICKS / (moss ? 2.0 : 1.0))));
		} else if (state.is(BlockTags.CORALS) || state.is(BlockTags.CORAL_BLOCKS) || state.is(BlockTags.WALL_CORALS)) {
			lines.add(t("coral"));
			lines.add(t(waterSide(level, pos) ? "needs.coral_ok" : "needs.coral_dry"));
		} else if (state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET)) {
			lines.add(t("moss"));
		} else if (state.getBlock() instanceof BonemealableBlock) {
			lines.add(t("bonemeal"));
		}
		return lines;
	}

	private static String minutes(double ticks) {
		return Long.toString(Math.max(1L, Math.round(ticks / MINUTE)));
	}

	/** Ticks a block waits, on average, between random ticks. */
	private static double stageTicksPerTick() {
		return 4096.0 / RANDOM_TICK_SPEED;
	}

	/** Whether the farmland under the crop is watered. */
	private static boolean wet(Level level, BlockPos pos) {
		BlockState soil = level.getBlockState(pos.below());
		return soil.is(Blocks.FARMLAND) && soil.getValue(FarmlandBlock.MOISTURE) > 0;
	}

	/** About how many ticks one stage of the crop takes: the game's own odds (its growth speed from the soil around it). */
	private static double cropStageTicks(Level level, BlockPos pos) {
		double speed = 1.0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockState soil = level.getBlockState(pos.below().offset(dx, 0, dz));
				double f = 0.0;
				if (soil.is(Blocks.FARMLAND)) {
					f = soil.getValue(FarmlandBlock.MOISTURE) > 0 ? 3.0 : 1.0;
				}
				speed += dx == 0 && dz == 0 ? f : f / 4.0;
			}
		}
		return ((int)(25.0 / speed) + 1) * stageTicksPerTick();
	}

	private static boolean waterSide(Level level, BlockPos pos) {
		for (Direction side : Direction.values()) {
			if (level.getFluidState(pos.relative(side)).is(net.minecraft.tags.FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	// ---- the place ----

	/** The place's reading: its heat, how harsh the land is (when Kingdoms is there), the sky and the light. */
	static List<Component> place(ServerLevel level, BlockPos pos) {
		List<Component> lines = new ArrayList<>();
		float temperature = level.dimension() == Level.NETHER ? 2.0F : level.getBiome(pos).value().getBaseTemperature();
		int heat = 0;
		while (heat < HEAT_EDGES.length && temperature > HEAT_EDGES[heat]) {
			heat++;
		}
		lines.add(t("heat", t("heat." + heat)));
		if (ZFighterScanKingdoms.present() && level.dimension() == Level.OVERWORLD) {
			lines.add(t("land", t("land." + Math.clamp(ZFighterScanKingdoms.land(level, pos), 0, 3))));
		}
		lines.add(t("sky", t(level.isThundering() ? "sky.storm" : level.isRaining() ? "sky.rain" : "sky.clear")));
		lines.add(t("light", level.getMaxLocalRawBrightness(pos)));
		return lines;
	}
}
