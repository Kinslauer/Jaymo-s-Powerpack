package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) A Smithing Anvil: hot ingots laid on it ({@link #work}), turned into a forging blank once you choose what
 * to make, hammered blow by blow by one smith at a time, and the finished head left on top ({@link #result}).
 */
public class SmithingAnvilEntity extends BlockEntity {
	/** How often the anvil checks its metal for cooling and its smith for wandering off (ticks), and how far is too far. */
	static final int CHECK_TICKS = 20;
	static final double LEAVE_SQ = 10 * 10;
	/** A smith who hasn't struck in this long gets the game sent again on their next swing (a dropped packet, a relog). */
	static final int RESEND_TICKS = 40;

	private ItemStack work = ItemStack.EMPTY;
	private ItemStack result = ItemStack.EMPTY;
	private @Nullable UUID smith;
	private long lastBlow;
	/** Finished forgings so far (a Stone Anvil crumbles after {@link StoneAnvil#MAX_USES}). */
	private int uses;

	public SmithingAnvilEntity(BlockPos pos, BlockState state) {
		super(Smithing.SMITHING_ANVIL_ENTITY, pos, state);
	}

	public ItemStack work() {
		return work;
	}

	public ItemStack result() {
		return result;
	}

	/** Whether this anvil's block is a stone anvil and the like (see {@link SmithingAnvilBlock#tier}). */
	public int tier() {
		return getBlockState().getBlock() instanceof SmithingAnvilBlock block ? block.tier : SmithingAnvilBlock.IRON_TIER;
	}

	public int uses() {
		return uses;
	}

	/** Counts a finished forging; returns the total. */
	public int addUse() {
		uses++;
		setChanged();
		return uses;
	}

	private long now() {
		return level == null ? 0L : level.getGameTime();
	}

	// --- laying metal on, taking it off ---

	/** Lays hot ingots (more of the same metal, up to {@link Forging#MAX_INGOTS}) or a forging blank on the anvil. */
	public boolean put(ServerPlayer player, ItemStack stack) {
		if (!result.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.take_result"));
			return false;
		}
		if (stack.is(Smithing.FORGING_BLANK)) {
			if (!work.isEmpty()) {
				return false;
			}
			work = stack.split(1);
			changed();
			return true;
		}
		if (!stack.is(Smithing.HOT_INGOT)) {
			return false;
		}
		if (work.isEmpty()) {
			work = stack.split(Math.min(stack.getCount(), Forging.MAX_INGOTS));
		} else if (work.is(Smithing.HOT_INGOT) && HotMetal.metal(work) == HotMetal.metal(stack) && work.getCount() < Forging.MAX_INGOTS) {
			int moved = Math.min(stack.getCount(), Forging.MAX_INGOTS - work.getCount());
			// the pile is as hot as its coolest ingot
			long at = Math.min(work.getOrDefault(Smithing.HEATED_AT, now()), stack.getOrDefault(Smithing.HEATED_AT, now()));
			work.grow(moved);
			work.set(Smithing.HEATED_AT, at);
			stack.shrink(moved);
		} else {
			return false;
		}
		level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 0.8F);
		changed();
		return true;
	}

	/** Takes what's on it: the finished head first, then the work (which ends any hammering). */
	public ItemStack take() {
		ItemStack out;
		if (!result.isEmpty()) {
			out = result;
			result = ItemStack.EMPTY;
		} else if (!work.isEmpty()) {
			out = work;
			work = ItemStack.EMPTY;
			endSession();
		} else {
			return ItemStack.EMPTY;
		}
		changed();
		return out;
	}

	// --- the hammer ---

	/** A hammer swung at the anvil (server side; the blows themselves come as {@link SmithingNet.Hit}). */
	public void hammer(ServerPlayer player, ItemStack hammer) {
		long now = now();
		if (!result.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.take_result"));
			return;
		}
		if (work.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.empty"));
			return;
		}
		if (!HotMetal.hot(work, now)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.cold"));
			return;
		}
		if (work.is(Smithing.HOT_INGOT)) {
			openForge(player);
			return;
		}
		Smithing.Work progress = work.get(Smithing.FORGE_WORK);
		Forging.Option option = progress == null ? null : Forging.byId(progress.option());
		if (option == null) {
			return;
		}
		if (smith != null && !smith.equals(player.getUUID()) && smithNear()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.busy"));
			return;
		}
		if (player.getUUID().equals(smith) && now - lastBlow < RESEND_TICKS) {
			return;
		}
		Component refusal = refuse(player, option);
		if (refusal != null) {
			player.sendOverlayMessage(refusal);
			return;
		}
		smith = player.getUUID();
		lastBlow = now;
		sendSession(player, progress, option);
	}

	private void openForge(ServerPlayer player) {
		Molten metal = HotMetal.metal(work);
		if (metal == null) {
			return;
		}
		List<SmithingNet.Choice> choices = new ArrayList<>();
		Component refusal = null;
		for (Forging.Option option : Forging.forMetal(metal, work.getCount())) {
			Component why = refuse(player, option);
			if (why == null) {
				choices.add(new SmithingNet.Choice(option.id(), option.result(), option.ingots()));
			} else if (refusal == null) {
				refusal = why;
			}
		}
		if (choices.isEmpty()) {
			player.sendOverlayMessage(refusal != null ? refusal : Component.translatable("message.jaymospowers.smithing.nothing"));
			return;
		}
		ServerPlayNetworking.send(player, new SmithingNet.OpenForge(worldPosition, choices));
	}

	private @Nullable Component refuse(ServerPlayer player, Forging.Option option) {
		for (Forging.Check check : Forging.CHECKS) {
			Component why = check.refuse(player, this, option);
			if (why != null) {
				return why;
			}
		}
		return null;
	}

	/** The smith chose what to make: the ingots it takes become a forging blank, the rest go back to them. */
	public void choose(ServerPlayer player, String id) {
		Forging.Option option = Forging.byId(id);
		long now = now();
		if (option == null || !work.is(Smithing.HOT_INGOT) || !HotMetal.hot(work, now) || HotMetal.metal(work) != option.metal()
			|| work.getCount() < option.ingots() || refuse(player, option) != null || !result.isEmpty()) {
			return;
		}
		long heatedAt = work.getOrDefault(Smithing.HEATED_AT, now);
		int left = work.getCount() - option.ingots();
		if (left > 0) {
			ItemStack rest = work.copyWithCount(left);
			player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
		}
		ItemStack blank = new ItemStack(Smithing.FORGING_BLANK);
		Smithing.Work progress = new Smithing.Work(option.id(), 0, 0.0F, 0, level.getRandom().nextLong());
		blank.set(Smithing.FORGE_WORK, progress);
		blank.set(Smithing.HEATED_AT, heatedAt);
		work = blank;
		smith = player.getUUID();
		lastBlow = now;
		changed();
		level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
		sendSession(player, progress, option);
	}

	/** One blow, judged on the smith's screen. */
	public void hit(ServerPlayer player, int blow) {
		long now = now();
		Smithing.Work progress = work.get(Smithing.FORGE_WORK);
		Forging.Option option = progress == null ? null : Forging.byId(progress.option());
		ItemStack hammer = player.getMainHandItem();
		if (option == null || !player.getUUID().equals(smith) || blow < Forging.MISS || blow > Forging.PERFECT) {
			return;
		}
		// a blow the anvil won't take still puts the smith's bar back to the anvil's count
		if (!(hammer.getItem() instanceof SmithingHammerItem) || now - lastBlow < Forging.BLOW_TICKS - 2) {
			sendSession(player, progress, option);
			return;
		}
		// the rules hold for every blow, not just the first (a steel forging can't be finished with a copper hammer)
		Component why = refuse(player, option);
		if (why != null) {
			player.sendOverlayMessage(why);
			sendSession(player, progress, option);
			return;
		}
		if (!HotMetal.hot(work, now)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.cold"));
			endSession();
			return;
		}
		lastBlow = now;
		progress = progress.hit(blow);
		player.getCooldowns().addCooldown(hammer, Forging.BLOW_TICKS);
		hammer.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		blowEffects((ServerLevel) level, blow);
		if (progress.hits() >= option.hits()) {
			finish(player, progress, option);
			return;
		}
		work.set(Smithing.FORGE_WORK, progress);
		changed();
		sendSession(player, progress, option);
	}

	private void finish(ServerPlayer player, Smithing.Work progress, Forging.Option option) {
		Grade grade = Forging.grade(progress, option.hits(), new Random(progress.seed() ^ level.getGameTime()));
		for (Forging.Cap cap : Forging.CAPS) {
			Grade most = cap.cap(player, this, option);
			if (most != null) {
				grade = Grade.min(grade, most);
			}
		}
		ItemStack made = option.result().copy();
		Grades.set(made, grade);
		made.set(Smithing.HEATED_AT, now());
		for (Forging.Finish finish : Forging.FINISHES) {
			finish.finish(player, this, option, made);
		}
		result = made;
		work = ItemStack.EMPTY;
		endSession();
		changed();
		ServerLevel server = (ServerLevel) level;
		server.playSound(null, worldPosition, grade == Grade.MASTER ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_USE, SoundSource.BLOCKS,
			grade == Grade.MASTER ? 0.8F : 1.0F, grade == Grade.MASTER ? 1.2F : 0.7F);
		if (grade == Grade.MASTER) {
			server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
				30, 0.3, 0.3, 0.3, 0.3);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.done", made.getHoverName(), grade.displayName()));
		ServerPlayNetworking.send(player, SmithingNet.Session.off(worldPosition));
	}

	private void blowEffects(ServerLevel level, int blow) {
		double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.0, z = worldPosition.getZ() + 0.5;
		switch (blow) {
			case Forging.PERFECT -> {
				level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 1.5F + level.getRandom().nextFloat() * 0.2F);
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 4, 0.1, 0.05, 0.1, 0.0);
				level.sendParticles(ParticleTypes.CRIT, x, y, z, 10, 0.2, 0.1, 0.2, 0.3);
			}
			case Forging.GOOD -> {
				level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.1F + level.getRandom().nextFloat() * 0.2F);
				level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 5, 0.15, 0.05, 0.15, 0.05);
			}
			default -> {
				level.playSound(null, worldPosition, SoundEvents.ANVIL_HIT, SoundSource.BLOCKS, 0.7F, 0.6F);
				level.sendParticles(ParticleTypes.SMOKE, x, y, z, 4, 0.15, 0.05, 0.15, 0.01);
			}
		}
	}

	private void sendSession(ServerPlayer player, Smithing.Work progress, Forging.Option option) {
		ServerPlayNetworking.send(player, new SmithingNet.Session(worldPosition, true, option.hits(), progress.hits(),
			Forging.difficulty(option.metal()), Forging.bonus(player), progress.seed()));
	}

	private void endSession() {
		if (smith != null && level instanceof ServerLevel server && server.getPlayerByUUID(smith) instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, SmithingNet.Session.off(worldPosition));
		}
		smith = null;
	}

	private boolean smithNear() {
		if (smith == null || !(level instanceof ServerLevel server) || !(server.getPlayerByUUID(smith) instanceof ServerPlayer player)) {
			return false;
		}
		return player.blockPosition().distSqr(worldPosition) <= LEAVE_SQ;
	}

	// --- cooling ---

	public static void serverTick(Level level, BlockPos pos, BlockState state, SmithingAnvilEntity anvil) {
		long now = level.getGameTime();
		if ((now + pos.asLong()) % CHECK_TICKS != 0) {
			return;
		}
		ServerLevel server = (ServerLevel) level;
		boolean changed = false;
		if (HotMetal.expired(anvil.work, now)) {
			anvil.work = HotMetal.cooled(anvil.work, server);
			changed = true;
			if (anvil.smith != null && server.getPlayerByUUID(anvil.smith) instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.went_cold"));
			}
			anvil.endSession();
		}
		if (HotMetal.expired(anvil.result, now)) {
			anvil.result = HotMetal.cooled(anvil.result, server);
			changed = true;
		}
		if (anvil.smith != null && !anvil.smithNear()) {
			anvil.endSession();
		}
		if (anvil.tier() == SmithingAnvilBlock.STONE_TIER && anvil.uses >= StoneAnvil.MAX_USES) {
			StoneAnvil.crumbleSoon(anvil);
		}
		if (changed) {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
			anvil.changed();
		}
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			endSession();
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), work);
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), result);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		work = input.read("work", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		result = input.read("result", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		uses = input.getIntOr("uses", 0);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("work", ItemStack.OPTIONAL_CODEC, work);
		output.store("result", ItemStack.OPTIONAL_CODEC, result);
		output.putInt("uses", uses);
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
