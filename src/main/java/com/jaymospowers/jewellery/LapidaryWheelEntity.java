package com.jaymospowers.jewellery;

import com.jaymospowers.tinkering.smithing.Forging;
import com.jaymospowers.tinkering.smithing.Grade;
import com.jaymospowers.tinkering.smithing.Smithing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.87) A Lapidary Wheel: one raw gem laid on it ({@link #gem}), a cut chosen ({@link Cutting}), stroke after
 * stroke judged by the server, and the cut gem left on top ({@link #result}). Walking away pauses the cut.
 */
public class LapidaryWheelEntity extends BlockEntity {
	/** The progress of a cut, saved on the wheel. */
	public record Cutting(String cut, int done, float score, int perfects, int chips, long seed) {
		public static final Codec<Cutting> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("cut").forGetter(Cutting::cut),
			Codec.INT.fieldOf("done").forGetter(Cutting::done),
			Codec.FLOAT.fieldOf("score").forGetter(Cutting::score),
			Codec.INT.fieldOf("perfects").forGetter(Cutting::perfects),
			Codec.INT.fieldOf("chips").forGetter(Cutting::chips),
			Codec.LONG.fieldOf("seed").forGetter(Cutting::seed)).apply(i, Cutting::new));

		/** The progress after a stroke that landed as {@code result} (a chip scores nothing). */
		public Cutting hit(int result) {
			if (result == GemCutting.CHIP) {
				return new Cutting(cut, done + 1, score, perfects, chips + 1, seed);
			}
			return new Cutting(cut, done + 1, score + Forging.score(result), perfects + (result == Forging.PERFECT ? 1 : 0), chips, seed);
		}

		/** The same progress as the anvil's, for {@link Forging#grade}. */
		public Smithing.Work work(String gem) {
			return new Smithing.Work("cut/" + gem + "/" + cut, done, score, perfects, seed);
		}
	}

	private ItemStack gem = ItemStack.EMPTY;
	private static final int FINISH_GRACE_TICKS = 2;

	private ItemStack result = ItemStack.EMPTY;
	private @Nullable Cutting cutting;
	private @Nullable UUID smith;
	private long lastStroke;
	/** Who landed the final stroke and when, so the use packet of that same click is swallowed. */
	private @Nullable UUID finishedBy;
	private long finishedAt;

	public LapidaryWheelEntity(BlockPos pos, BlockState state) {
		super(Jewellery.LAPIDARY_WHEEL_ENTITY, pos, state);
	}

	public ItemStack gem() {
		return gem;
	}

	public ItemStack result() {
		return result;
	}

	public boolean isEmpty() {
		return gem.isEmpty() && result.isEmpty();
	}

	private long now() {
		return level == null ? 0L : level.getGameTime();
	}

	/** Whether this player is mid-cut here and the game is up on their screen (strokes come as packets). */
	public boolean cutting(ServerPlayer player) {
		return cutting != null && player.getUUID().equals(smith) && now() - lastStroke < GemCutting.RESEND_TICKS;
	}

	/** Whether this player's final stroke landed a moment ago, so the click that follows must not take the gem. */
	public boolean justFinished(ServerPlayer player) {
		return finishedBy != null && player.getUUID().equals(finishedBy) && now() - finishedAt <= FINISH_GRACE_TICKS;
	}

	// --- using the wheel ---

	public void use(ServerPlayer player, ItemStack held) {
		long now = now();
		if (!result.isEmpty()) {
			player.getInventory().placeItemBackInInventory(result, Prediction.SERVER_ONLY);
			result = ItemStack.EMPTY;
			level.playSound(null, worldPosition, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
			changed();
			return;
		}
		if (gem.isEmpty()) {
			if (Gems.of(held) != null) {
				gem = held.split(1);
				level.playSound(null, worldPosition, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.7F, 1.4F);
				changed();
			} else {
				player.sendOverlayMessage(Component.translatable(held.isEmpty() ? "message.jaymospowers.jewellery.wheel_empty"
					: "message.jaymospowers.jewellery.not_a_gem"));
			}
			return;
		}
		if (cutting == null) {
			if (player.isShiftKeyDown() && held.isEmpty()) {
				player.getInventory().placeItemBackInInventory(gem, Prediction.SERVER_ONLY);
				gem = ItemStack.EMPTY;
				changed();
				return;
			}
			ServerPlayNetworking.send(player, new JewelleryNet.OpenCuts(worldPosition, gem.copy(),
				java.util.Arrays.stream(Cut.values()).map(c -> new JewelleryNet.CutChoice(c.id, c.strokes)).toList()));
			return;
		}
		if (smith != null && !smith.equals(player.getUUID()) && smithNear()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.jewellery.busy"));
			return;
		}
		smith = player.getUUID();
		lastStroke = now - GemCutting.SETTLE_TICKS;
		sendSession(player, -1);
	}

	/** The cutter chose a cut. */
	public void choose(ServerPlayer player, String cutId) {
		Cut cut = Cut.find(cutId);
		if (cut == null || gem.isEmpty() || cutting != null || !result.isEmpty()) {
			return;
		}
		cutting = new Cutting(cut.id, 0, 0.0F, 0, 0, level.getRandom().nextLong());
		smith = player.getUUID();
		lastStroke = now() - GemCutting.SETTLE_TICKS;
		level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 1.0F);
		changed();
		sendSession(player, -1);
	}

	private float difficulty() {
		Gems.Gem type = Gems.of(gem);
		Cut cut = cutting == null ? Cut.TABLE : Cut.byId(cutting.cut());
		return (type == null ? 1.0F : type.hardness()) * cut.difficulty;
	}

	/** One stroke, with the arrow where the cutter's bar showed it. The server judges it and the wheel's spin-up. */
	public void stroke(ServerPlayer player, float arrow) {
		if (cutting == null || !player.getUUID().equals(smith) || !Float.isFinite(arrow) || arrow < 0.0F || arrow > 100.0F
			|| !(level instanceof ServerLevel server)) {
			return;
		}
		long now = now();
		if (now - lastStroke < GemCutting.MIN_STROKE_GAP) {
			return;
		}
		boolean rushed = now - lastStroke < GemCutting.SETTLE_TICKS - GemCutting.SETTLE_GRACE;
		int result = rushed ? GemCutting.CHIP : Forging.judge(arrow, cutting.seed(), cutting.done(), difficulty(), Forging.bonus(player));
		cutting = cutting.hit(result);
		lastStroke = now;
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		strokeEffects(server, result);
		if (result == GemCutting.CHIP) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.jewellery.chipped"));
		}
		if (cutting.done() >= Cut.byId(cutting.cut()).strokes) {
			finish(player);
			return;
		}
		changed();
		sendSession(player, result);
	}

	private void finish(ServerPlayer player) {
		Gems.Gem type = Gems.of(gem);
		Cut cut = Cut.byId(cutting.cut());
		if (type == null) {
			return;
		}
		Grade grade = Forging.grade(cutting.work(type.id()), cut.strokes, new Random(cutting.seed() ^ now()));
		for (int i = 0; i < cutting.chips(); i++) {
			grade = grade.lower();
		}
		result = CutGemItem.of(type, cut, grade, cutting.chips());
		gem = ItemStack.EMPTY;
		cutting = null;
		smith = null;
		finishedBy = player.getUUID();
		finishedAt = now();
		changed();
		ServerPlayNetworking.send(player, JewelleryNet.CutSession.off(worldPosition));
		ServerLevel server = (ServerLevel) level;
		boolean master = grade == Grade.MASTER;
		server.playSound(null, worldPosition, master ? SoundEvents.PLAYER_LEVELUP : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
			master ? 0.8F : 1.0F, master ? 1.2F : 1.0F);
		if (master) {
			server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5,
				30, 0.3, 0.3, 0.3, 0.3);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.jewellery.cut_done", result.getHoverName(),
			Component.translatable("grade.jaymospowers.cut." + grade.id).withStyle(grade.colour)));
	}

	private void strokeEffects(ServerLevel server, int result) {
		double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.8, z = worldPosition.getZ() + 0.5;
		SoundEvent sound;
		float volume, pitch;
		ParticleOptions particle;
		int count;
		switch (result) {
			case Forging.PERFECT -> {
				sound = SoundEvents.AMETHYST_BLOCK_CHIME;
				volume = 0.8F;
				pitch = 1.5F;
				particle = ParticleTypes.CRIT;
				count = 8;
			}
			case Forging.GOOD -> {
				sound = SoundEvents.GRINDSTONE_USE;
				volume = 0.5F;
				pitch = 1.2F;
				particle = ParticleTypes.ELECTRIC_SPARK;
				count = 4;
			}
			case GemCutting.CHIP -> {
				sound = SoundEvents.ITEM_BREAK.value();
				volume = 0.6F;
				pitch = 1.6F;
				particle = new ItemParticleOption(ParticleTypes.ITEM, gem.getItem());
				count = 8;
			}
			default -> {
				sound = SoundEvents.GRINDSTONE_USE;
				volume = 0.6F;
				pitch = 0.7F;
				particle = ParticleTypes.SMOKE;
				count = 3;
			}
		}
		server.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
		server.sendParticles(particle, x, y, z, count, 0.15, 0.1, 0.15, 0.05);
	}

	private void sendSession(ServerPlayer player, int last) {
		Cutting c = cutting;
		if (c == null) {
			return;
		}
		ServerPlayNetworking.send(player, new JewelleryNet.CutSession(worldPosition, true, Cut.byId(c.cut()).strokes, c.done(), difficulty(),
			Forging.bonus(player), c.seed(), c.chips(), last));
	}

	/** Ends the cutter's game on their screen; the progress stays on the wheel. */
	private void endSession() {
		if (smith != null && level instanceof ServerLevel server && server.getPlayerByUUID(smith) instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, JewelleryNet.CutSession.off(worldPosition));
		}
		smith = null;
	}

	private boolean smithNear() {
		if (smith == null || !(level instanceof ServerLevel server) || !(server.getPlayerByUUID(smith) instanceof ServerPlayer player)) {
			return false;
		}
		return player.blockPosition().distSqr(worldPosition) <= GemCutting.LEAVE_SQ;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, LapidaryWheelEntity wheel) {
		if ((level.getGameTime() + pos.asLong()) % GemCutting.CHECK_TICKS != 0) {
			return;
		}
		if (wheel.smith != null && !wheel.smithNear()) {
			wheel.endSession();
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
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), gem);
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), result);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		gem = input.read("gem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		result = input.read("result", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		cutting = input.read("cutting", Cutting.CODEC).orElse(null);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("gem", ItemStack.OPTIONAL_CODEC, gem);
		output.store("result", ItemStack.OPTIONAL_CODEC, result);
		if (cutting != null) {
			output.store("cutting", Cutting.CODEC, cutting);
		}
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
