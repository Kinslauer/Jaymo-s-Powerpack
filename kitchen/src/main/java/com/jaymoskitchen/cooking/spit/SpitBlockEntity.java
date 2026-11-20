package com.jaymoskitchen.cooking.spit;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One side of the spit: what's roasting and how done it is (0 raw, {@link #COOKED} cooked, {@link #BURNT} burnt).
 * The server ticks the doneness while the block sits over a lit heat source; the client draws it turning and
 * changing colour (see {@link com.jaymoskitchen.client.spit.SpitBlockEntityRenderer}).
 */
public class SpitBlockEntity extends BlockEntity {
	/** Doneness at which the roast is cooked through. */
	public static final float COOKED = 1.0F;
	/** Doneness at which it's burnt to a crisp. */
	public static final float BURNT = 2.0F;
	/** Ticks from raw to cooked over a lit heat source. */
	public static final int COOK_TICKS = 10 * 20;
	/** Ticks from cooked to burnt. */
	public static final int BURNT_TICKS = 8 * 20;
	/** Doneness a single torn chunk takes off. */
	public static final float CHUNK = 0.25F;
	/** Chunks a whole animal carcass yields when torn off the spit. */
	public static final int CHUNKS_PER_ANIMAL = 4;
	/** Chunks a single piece of food yields. */
	public static final int CHUNKS_PER_PIECE = 1;
	/** A whole beast on the great spit ({@link GreatSpitBlock}): ticks from raw to cooked through, and from cooked to burnt. */
	public static final int WHOLE_COOK_TICKS = 3 * 60 * 20, WHOLE_BURNT_TICKS = 2 * 60 * 20;
	/** What can go on the spit: raw meats and fish, and carcasses in your pocket. */
	public static final TagKey<Item> ROASTABLE = TagKey.create(Registries.ITEM, JaymosKitchen.id("spit_foods"));

	/**
	 * Places along the bar (Andy, 2026-11-19: "they could put maybe 5 steaks on that thing"): each its own piece of meat,
	 * cooking at its own pace. A body (a small animal, a whole beast) takes the whole bar, in the first place.
	 */
	public static final int PLACES = 5;

	final NonNullList<ItemStack> items = NonNullList.withSize(PLACES, ItemStack.EMPTY);
	/** How done each place's piece is. */
	final float[] done = new float[PLACES];
	/** How many cooked chunks are left to tear off a body (pieces of meat are one chunk each). */
	int chunks;
	/** Ticks this roast has been cooking: for periodic client colour updates. */
	int cookTicks;
	/**
	 * A whole beast's: how it was dressed ({@link com.jaymoskitchen.corpse.CorpseEntity#dressed}, for the client to draw
	 * it skinned and gutted), and how many pieces it had to start with. 0 for anything else.
	 */
	int dressed, pieces;

	public SpitBlockEntity(BlockPos pos, BlockState state) {
		super(com.jaymoskitchen.cooking.spit.SpitBlock.ENTITY, pos, state);
	}

	void markUpdated() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Something on the spit (the first place's, else any): empty if it's bare. */
	public ItemStack roasting() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** What's at place {@code i} along the bar. */
	public ItemStack at(int i) {
		return items.get(i);
	}

	/** How done place {@code i}'s piece is. */
	public float doneness(int i) {
		return done[i];
	}

	/** How done the most done thing on it is (0 when it's bare). */
	public float doneness() {
		int i = mostDone();
		return i < 0 ? 0.0F : done[i];
	}

	/** The place with the most done thing on it, or -1 when it's bare. */
	int mostDone() {
		int best = -1;
		for (int i = 0; i < PLACES; i++) {
			if (!items.get(i).isEmpty() && (best < 0 || done[i] > done[best])) {
				best = i;
			}
		}
		return best;
	}

	/** No room for another piece: every place taken, or a body on it. */
	public boolean full() {
		if (items.get(0).is(com.jaymoskitchen.corpse.Corpses.CORPSE_ITEM)) {
			return true;
		}
		for (ItemStack stack : items) {
			if (stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** A whole beast's dressing ({@link com.jaymoskitchen.corpse.CorpseEntity#dressed}), 0 for anything else. */
	public int dressed() {
		return dressed;
	}

	/** A whole beast turning on the great spit (not a piece of meat, nor a small body). */
	public boolean whole() {
		return pieces > 0 && !items.get(0).isEmpty();
	}

	/**
	 * A dressed carcass ({@link com.jaymoskitchen.corpse.CorpseEntity#dressedWhole}) goes on this great spit whole, by
	 * anyone (a player, or Jaymo's Kingdoms' camp cook): it's taken out of the world and turns here. True if it went on
	 * (a great spit, bare, and the body ready for it).
	 */
	public boolean skewerWhole(net.minecraft.world.entity.Entity body) {
		if (!(body instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) || !(level instanceof ServerLevel server)
			|| !GreatSpitBlock.is(getBlockState()) || !roasting().isEmpty() || !corpse.dressedWhole()) {
			return false;
		}
		ItemStack stack = com.jaymoskitchen.corpse.CorpseItem.of(corpse);
		int meat = SpitBlock.wholePieces(stack, server).size();
		if (meat <= 0) {
			return false;
		}
		items.set(0, stack);
		chunks = pieces = meat;
		dressed = corpse.dressed();
		done[0] = 0.0F;
		corpse.discard();
		server.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.WOOD_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.7F);
		markUpdated();
		return true;
	}

	/**
	 * One of {@code raw} goes on the spit (shrinking it), by anyone: a player, or someone who isn't (Kingdoms' camp
	 * cooks). A piece of meat takes the next free place along the bar; a small body only a bare spit, and all of it. True
	 * if it went on.
	 */
	public boolean skewer(ItemStack raw) {
		if (raw.isEmpty() || !raw.is(ROASTABLE) || full()) {
			return false;
		}
		boolean body = raw.is(com.jaymoskitchen.corpse.Corpses.CORPSE_ITEM);
		int place = -1;
		for (int i = 0; i < PLACES && place < 0; i++) {
			if (items.get(i).isEmpty()) {
				place = i;
			}
		}
		if (place < 0 || body && (place != 0 || !roasting().isEmpty())) {
			return false;
		}
		items.set(place, raw.copyWithCount(1));
		done[place] = 0.0F;
		if (body) {
			chunks = SpitBlock.chunksFor(raw);
			dressed = pieces = 0;
		}
		raw.shrink(1);
		markUpdated();
		return true;
	}

	/**
	 * Someone who isn't a player tears a cooked chunk off, as a player's empty hand does: one cooked piece, the last tear
	 * clearing the spit. A burnt roast is thrown out (nothing comes off); a raw one gives nothing.
	 */
	public ItemStack tear(ServerLevel level) {
		return tear(level, null);
	}

	/**
	 * {@link #tear(ServerLevel)} by {@code by} (a player, or Jaymo's Kingdoms' camp cook), whom everyone near sees reach
	 * in and rip it off: from the most done thing on the spit. A burnt piece is thrown out instead (nothing comes off). A
	 * whole beast's pieces are its own meat, cooked, one after another, and its bones fall away with the last; tearing it
	 * doesn't cool it.
	 */
	public ItemStack tear(ServerLevel level, net.minecraft.world.entity.@Nullable Entity by) {
		int i = mostDone();
		if (i < 0 || done[i] < COOKED) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = items.get(i);
		boolean body = stack.is(com.jaymoskitchen.corpse.Corpses.CORPSE_ITEM);
		if (done[i] >= BURNT || body && chunks <= 0) {
			clear(i);
			markUpdated();
			return ItemStack.EMPTY;
		}
		ItemStack chunk;
		if (whole()) {
			java.util.List<ItemStack> meat = SpitBlock.wholePieces(stack, level);
			chunk = meat.isEmpty() ? ItemStack.EMPTY : meat.get(Mth.clamp(pieces - chunks, 0, meat.size() - 1)).copyWithCount(1);
			chunks -= 1;
			if (chunks <= 0) {
				// down to the bones
				Block.popResource(level, worldPosition, new ItemStack(net.minecraft.world.item.Items.BONE, 2 + level.getRandom().nextInt(3)));
				clear(i);
			}
		} else if (body) {
			chunk = SpitBlock.cookedProduct(stack, level).get(0).copyWithCount(1);
			chunks -= 1;
			done[i] = Mth.clamp(done[i] - CHUNK, 0.0F, BURNT);
			if (chunks <= 0) {
				clear(i);
			}
		} else {
			// a piece of meat comes off whole
			chunk = SpitBlock.cookedProduct(stack, level).get(0).copyWithCount(1);
			clear(i);
		}
		markUpdated();
		SpitBlock.ripSeen(level, worldPosition, by);
		return chunk;
	}

	/**
	 * Everything cooked taken off at once (a player's sneak + right-click): every cooked piece, or all that's left of a
	 * body (a whole beast's meat and its bones). Burnt and raw pieces stay.
	 */
	List<ItemStack> takeCooked(ServerLevel level) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < PLACES; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty() || done[i] < COOKED || done[i] >= BURNT) {
				continue;
			}
			if (whole()) {
				List<ItemStack> meat = SpitBlock.wholePieces(stack, level);
				for (int k = Math.max(0, pieces - chunks); k < meat.size(); k++) {
					out.add(meat.get(k).copy());
				}
				out.add(new ItemStack(net.minecraft.world.item.Items.BONE, 2 + level.getRandom().nextInt(3)));
			} else {
				out.addAll(SpitBlock.cookedProduct(stack, level));
			}
			clear(i);
		}
		if (!out.isEmpty()) {
			markUpdated();
		}
		return out;
	}

	/** Every burnt piece thrown out; true if there was any. */
	boolean throwOutBurnt() {
		boolean any = false;
		for (int i = 0; i < PLACES; i++) {
			if (!items.get(i).isEmpty() && done[i] >= BURNT) {
				clear(i);
				any = true;
			}
		}
		if (any) {
			markUpdated();
		}
		return any;
	}

	private void clear(int i) {
		items.set(i, ItemStack.EMPTY);
		done[i] = 0.0F;
		if (i == 0) {
			dressed = pieces = chunks = 0;
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SpitBlockEntity be) {
		if (!com.jaymoskitchen.cooking.spit.SpitBlock.heat(level.getBlockState(pos.below()))) {
			return;
		}
		boolean whole = be.whole();
		if (whole && level instanceof ServerLevel server && level.getRandom().nextInt(10) == 0) {
			// fat dripping into the fire
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
				2, 0.4, 0.05, 0.4, 0.01);
			if (level.getRandom().nextInt(4) == 0) {
				server.playSound(null, pos, net.minecraft.sounds.SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.8F);
			}
		}
		boolean changed = false, any = false;
		for (int i = 0; i < PLACES; i++) {
			if (be.items.get(i).isEmpty()) {
				continue;
			}
			any = true;
			float before = be.done[i];
			float rate = before < COOKED ? 1.0F / (whole ? WHOLE_COOK_TICKS : COOK_TICKS) : 1.0F / (whole ? WHOLE_BURNT_TICKS : BURNT_TICKS);
			be.done[i] = Mth.clamp(before + rate, 0.0F, BURNT);
			changed |= (before >= COOKED) != (be.done[i] >= COOKED) || (before >= BURNT) != (be.done[i] >= BURNT);
		}
		if (any && (changed || ++be.cookTicks % 20 == 0)) {
			be.markUpdated();
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		net.minecraft.world.ContainerHelper.loadAllItems(input, items);
		readDone(input);
		chunks = input.getIntOr("Chunks", 0);
		dressed = input.getIntOr("Dressed", 0);
		pieces = input.getIntOr("Pieces", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		writeShared(output);
	}

	/** Each place's doneness: the first under the spit's old key, so a spit saved before it had places keeps its roast. */
	private void readDone(ValueInput input) {
		for (int i = 0; i < PLACES; i++) {
			done[i] = input.getFloatOr(i == 0 ? "Doneness" : "Doneness" + i, 0.0F);
		}
	}

	private void writeShared(ValueOutput output) {
		net.minecraft.world.ContainerHelper.saveAllItems(output, items, true);
		for (int i = 0; i < PLACES; i++) {
			output.putFloat(i == 0 ? "Doneness" : "Doneness" + i, done[i]);
		}
		output.putInt("Chunks", chunks);
		output.putInt("Dressed", dressed);
		output.putInt("Pieces", pieces);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		writeShared(output);
		return output.buildResult();
	}
}
