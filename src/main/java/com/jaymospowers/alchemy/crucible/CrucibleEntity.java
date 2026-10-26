package com.jaymospowers.alchemy.crucible;

import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Flux;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.essence.Essences;
import com.jaymospowers.core.Soundscape;
import com.jaymospowers.tinkering.kiln.Heat;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a crucible holds and does: water, the essences dissolved in it, how warm it has got. While water sits over a
 * fire it warms for {@link Crucibles#HEAT_TICKS}, then it is hot: it simmers, and each {@link Crucibles#DISSOLVE_TICKS}
 * it takes one thrown item out of the liquid. A catalyst that matches a {@link Crucibles.Recipe} the pot covers, thrown
 * by someone who has its research, becomes the result; any other item with essence dissolves (a tenth of it lost as
 * {@link Flux}), and one with none bobs back out. An alembic on top of the pot ({@link AlembicBlock}) leads one
 * essence at a time into a jar beside it.
 */
public class CrucibleEntity extends BlockEntity {
	/** The inside of the pot: the floor and the rim's opening, in block units. */
	private static final double INNER_MIN = 3 / 16.0, INNER_MAX = 13 / 16.0, FLOOR = 4 / 16.0, OPENING = 15 / 16.0;
	/** The surface height: from this share of the way up when there's only water to the top wall when full. */
	private static final double WATER_ONLY_LEVEL = 0.3, WALL_TOP = 13 / 16.0;
	/** Warmth lost per tick when there's no fire (twice as fast as it's gained). */
	private static final int COOL_RATE = 2;
	/** How hard a rejected item is pushed back out. */
	private static final double BOB_SPEED = 0.25;

	private boolean water, hot;
	private int warm;
	private int dissolveIn = Crucibles.DISSOLVE_TICKS, distilIn = Crucibles.DISTIL_TICKS;
	private EssenceList essences = EssenceList.EMPTY;

	public CrucibleEntity(BlockPos pos, BlockState state) {
		super(Crucibles.CRUCIBLE_ENTITY, pos, state);
	}

	public boolean water() {
		return water;
	}

	public boolean hot() {
		return hot;
	}

	public EssenceList essences() {
		return essences;
	}

	/** How full of essence it is, 0..1. */
	public float fill() {
		return Math.min(1.0F, essences.total() / (float) Crucibles.CAPACITY);
	}

	/** The height of the liquid's surface (block units) for a fill of 0..1. */
	public static double surface(float fill) {
		return FLOOR + (WALL_TOP - FLOOR) * (WATER_ONLY_LEVEL + (1.0 - WATER_ONLY_LEVEL) * fill);
	}

	public void setWater(boolean water) {
		this.water = water;
		if (!water) {
			warm = 0;
			hot = false;
		}
		changed();
	}

	/** Adds essences; what doesn't fit boils away as flux. */
	public void add(ServerLevel level, EssenceList in) {
		int room = Math.max(0, Crucibles.CAPACITY - essences.total());
		Map<Essence, Integer> take = new EnumMap<>(Essence.class);
		int over = 0;
		for (Map.Entry<Essence, Integer> en : in.amounts().entrySet()) {
			int t = Math.min(en.getValue(), room);
			room -= t;
			take.put(en.getKey(), t);
			over += en.getValue() - t;
		}
		essences = essences.plus(new EssenceList(take));
		if (over > 0) {
			Flux.add(level, worldPosition, over);
			steam(level, 0xFF000000 | Essence.FLUX.colour, 6);
			level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
		}
		changed();
	}

	/** Draws up to {@code amount} of one essence out; returns how much came. */
	public int draw(Essence e, int amount) {
		int taken = Math.min(amount, essences.get(e));
		if (taken > 0) {
			essences = essences.minus(EssenceList.of(e, taken));
			changed();
		}
		return taken;
	}

	/** Empties the pot: all its essence becomes flux, the water goes, a hiss and a purple steam. */
	public void tipOut(ServerLevel level) {
		int total = essences.total();
		if (total > 0) {
			Flux.add(level, worldPosition, total);
			steam(level, 0xFF000000 | Essence.FLUX.colour, 24);
		}
		essences = EssenceList.EMPTY;
		water = false;
		hot = false;
		warm = 0;
		level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.8F);
		level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.2F);
		changed();
	}

	private void steam(ServerLevel level, int argb, int count) {
		level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb), worldPosition.getX() + 0.5,
			worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, count, 0.25, 0.1, 0.25, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
			Math.max(2, count / 3), 0.2, 0.05, 0.2, 0.02);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleEntity pot) {
		pot.tick((ServerLevel) level, pos);
	}

	private void tick(ServerLevel level, BlockPos pos) {
		boolean fire = water && Heat.at(level, pos.below()) > 0;
		warm = fire ? Math.min(Crucibles.HEAT_TICKS, warm + 1) : Math.max(0, warm - COOL_RATE);
		boolean nowHot = warm >= Crucibles.HEAT_TICKS;
		if (nowHot != hot) {
			hot = nowHot;
			if (hot) {
				level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7F, 0.8F);
			}
			changed();
		}
		if (!hot) {
			return;
		}
		if (Soundscape.due(level, pos.asLong(), "simmer", 30, 70)) {
			Soundscape.play(level, Vec3.atCenterOf(pos), SoundSource.BLOCKS,
				Soundscape.Layer.of(0.25F, 0.4F, 0.85F, 1.1F, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT),
				Soundscape.Layer.of(0.15F, 0.3F, 0.8F, 1.3F, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP).chance(0.6F));
		}
		if (--dissolveIn <= 0) {
			dissolveIn = Crucibles.DISSOLVE_TICKS;
			dissolveOne(level, pos);
		}
		if (level.getBlockState(pos.above()).is(Crucibles.ALEMBIC) && !essences.isEmpty()) {
			if (--distilIn <= 0) {
				distilIn = Crucibles.DISTIL_TICKS;
				distil(level, pos);
			}
		} else {
			distilIn = Crucibles.DISTIL_TICKS;
		}
	}

	/** Takes the first thrown item in the liquid: makes something of it, dissolves it, or sends it back up. */
	private void dissolveOne(ServerLevel level, BlockPos pos) {
		AABB inside = new AABB(pos.getX() + INNER_MIN, pos.getY() + FLOOR - 0.05, pos.getZ() + INNER_MIN, pos.getX() + INNER_MAX,
			pos.getY() + OPENING, pos.getZ() + INNER_MAX);
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, inside,
			e -> e.isAlive() && !e.getItem().isEmpty() && !e.entityTags().contains(Crucibles.SPENT_TAG))) {
			// one item a pass; the rest wait their turn (never bobbed out for being second)
			ItemStack stack = entity.getItem();
			if (transmute(level, pos, entity, stack)) {
				return;
			}
			EssenceList raw = Essences.of(stack);
			if (raw.isEmpty()) {
				bobOut(level, pos, entity);
			} else {
				dissolve(level, pos, entity, raw);
				return;
			}
		}
	}

	/** A catalyst the pot covers, thrown by a player who has the research: becomes its result. */
	private boolean transmute(ServerLevel level, BlockPos pos, ItemEntity entity, ItemStack stack) {
		Entity owner = entity.getOwner();
		if (!(owner instanceof Player thrower)) {
			return false;
		}
		for (Crucibles.Recipe recipe : Crucibles.recipes()) {
			if (!recipe.matches(stack) || !essences.covers(recipe.essences()) || !Knowledge.knows(thrower, recipe.research())) {
				continue;
			}
			ItemStack made = recipe.make();
			if (made == null) {
				continue;
			}
			essences = essences.minus(recipe.essences());
			takeOne(entity);
			ItemEntity out = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, made);
			out.setDeltaMovement(0.0, 0.3, 0.0);
			out.syncVelocity = true;
			out.addTag(Crucibles.SPENT_TAG);
			out.setPickUpDelay(20);
			level.addFreshEntity(out);
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), pos.getX() + 0.5, pos.getY() + 1.0,
				pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 14, 0.2, 0.2, 0.2, 0.08);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
			level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
			changed();
			return true;
		}
		return false;
	}

	/** One of the item goes into the pot: its essences, less the tenth that is lost as flux. */
	private void dissolve(ServerLevel level, BlockPos pos, ItemEntity entity, EssenceList raw) {
		RandomSource random = level.getRandom();
		Map<Essence, Integer> kept = new EnumMap<>(Essence.class);
		int lost = 0;
		for (Map.Entry<Essence, Integer> en : raw.amounts().entrySet()) {
			double owed = en.getValue() * Crucibles.LOSS;
			int l = (int) Math.floor(owed);
			if (random.nextDouble() < owed - l) {
				l++;
			}
			l = Math.min(l, en.getValue());
			kept.put(en.getKey(), en.getValue() - l);
			lost += l;
		}
		takeOne(entity);
		Flux.add(level, pos, lost);
		add(level, new EssenceList(kept));
		int colour = 0xFF000000 | Crucibles.mix(essences);
		double top = pos.getY() + surface(fill());
		level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, colour), pos.getX() + 0.5, top + 0.1, pos.getZ() + 0.5, 8,
			0.2, 0.05, 0.2, 0.02);
		level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, top, pos.getZ() + 0.5, 5, 0.2, 0.02, 0.2, 0.0);
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.7F, 0.9F + random.nextFloat() * 0.3F);
	}

	private static void takeOne(ItemEntity entity) {
		ItemStack stack = entity.getItem();
		if (stack.getCount() <= 1) {
			entity.discard();
		} else {
			entity.setItem(stack.copyWithCount(stack.getCount() - 1));
		}
	}

	/** An item with nothing in it to dissolve floats back up out of the pot. */
	private void bobOut(ServerLevel level, BlockPos pos, ItemEntity entity) {
		RandomSource random = level.getRandom();
		entity.addTag(Crucibles.SPENT_TAG);
		entity.setPos(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
		entity.setDeltaMovement((random.nextDouble() - 0.5) * 0.1, BOB_SPEED, (random.nextDouble() - 0.5) * 0.1);
		entity.syncVelocity = true;
		entity.setPickUpDelay(10);
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.5F, 1.4F);
	}

	/** The alembic leads one of the pot's main essence into a jar beside it or the pot. */
	private void distil(ServerLevel level, BlockPos pos) {
		Essence main = essences.main();
		if (main == null) {
			return;
		}
		JarEntity jar = findJar(level, pos, main);
		if (jar == null || jar.add(main, 1) < 1) {
			return;
		}
		essences = essences.minus(EssenceList.of(main, 1));
		BlockPos at = jar.getBlockPos();
		level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | main.colour), pos.getX() + 0.5,
			pos.getY() + 1.6, pos.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | main.colour), at.getX() + 0.5,
			at.getY() + 1.0, at.getZ() + 0.5, 2, 0.1, 0.05, 0.1, 0.0);
		level.playSound(null, at, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.3F, 1.4F);
		changed();
	}

	/** A jar next to the alembic, or failing that next to the pot, that can take this essence. */
	private @Nullable JarEntity findJar(ServerLevel level, BlockPos pos, Essence e) {
		for (BlockPos base : new BlockPos[] {pos.above(), pos}) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (level.getBlockEntity(base.relative(side)) instanceof JarEntity jar && jar.accepts(e)) {
					return jar;
				}
			}
		}
		return null;
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Breaking a pot spills whatever it held: all of the essence to flux. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server && !essences.isEmpty()) {
			Flux.add(server, pos, essences.total());
			steam(server, 0xFF000000 | Essence.FLUX.colour, 20);
			server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.8F);
			essences = EssenceList.EMPTY;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		water = input.getBooleanOr("water", false);
		warm = input.getIntOr("warm", 0);
		hot = warm >= Crucibles.HEAT_TICKS && water;
		essences = input.read("essences", EssenceList.CODEC).orElse(EssenceList.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("water", water);
		output.putInt("warm", warm);
		output.store("essences", EssenceList.CODEC, essences);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(out);
		return out.buildResult();
	}
}
