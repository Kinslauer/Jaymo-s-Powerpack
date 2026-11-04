package com.jaymospowers.alchemy.brewing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
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
 * (0.20.13) A Mortar and Pestle: up to {@link #HOLDS} of one ingredient ({@link #contents}), the grinding game
 * ({@link Grinding}, strokes judged by the server) and the ground batch left in the bowl ({@link #result}).
 * Walking away pauses the grinding.
 */
public class MortarEntity extends BlockEntity {
	/** How many of an ingredient the bowl takes. */
	public static final int HOLDS = 4;
	/** Strokes closer together than this (ticks) are dropped. */
	public static final int MIN_STROKE_GAP = 4;
	/** Ticks between the checks that the grinder is still near. */
	public static final int CHECK_TICKS = 20;
	/** Further than this from the mortar and someone else may take over (blocks, squared). */
	public static final double LEAVE_SQ = 10 * 10;
	/** A grinder who hasn't struck in this long gets the bar sent again on their next use. */
	public static final int RESEND_TICKS = 40;
	/** Ticks after the last stroke in which the same click can't take the batch out. */
	public static final int FINISH_GRACE_TICKS = 2;

	/** The progress of a grinding, saved on the mortar. */
	public record Grinding(int done, float score, int perfects, long seed) {
		public static final Codec<Grinding> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("done").forGetter(Grinding::done),
			Codec.FLOAT.fieldOf("score").forGetter(Grinding::score),
			Codec.INT.fieldOf("perfects").forGetter(Grinding::perfects),
			Codec.LONG.fieldOf("seed").forGetter(Grinding::seed)).apply(i, Grinding::new));

		/** The progress after a stroke that landed as {@code result}. */
		public Grinding hit(int result) {
			return new Grinding(done + 1, score + BrewQuality.score(result), perfects + (result == BrewQuality.PERFECT ? 1 : 0), seed);
		}
	}

	private ItemStack contents = ItemStack.EMPTY;
	private ItemStack result = ItemStack.EMPTY;
	private @Nullable Grinding grinding;
	private @Nullable UUID grinder;
	private long lastStroke;
	/** Who landed the final stroke and when, so the use packet of that same click is swallowed. */
	private @Nullable UUID finishedBy;
	private long finishedAt;

	public MortarEntity(BlockPos pos, BlockState state) {
		super(Brewing.MORTAR_ENTITY, pos, state);
	}

	public ItemStack contents() {
		return contents;
	}

	public ItemStack result() {
		return result;
	}

	public boolean isEmpty() {
		return contents.isEmpty() && result.isEmpty();
	}

	private long now() {
		return level == null ? 0L : level.getGameTime();
	}

	/** Whether this player is mid-grind here and the game is up on their screen (strokes come as packets). */
	public boolean grinding(ServerPlayer player) {
		return grinding != null && player.getUUID().equals(grinder) && now() - lastStroke < RESEND_TICKS;
	}

	/** Whether this player's final stroke landed a moment ago, so the click that follows must not take the batch. */
	public boolean justFinished(ServerPlayer player) {
		return finishedBy != null && player.getUUID().equals(finishedBy) && now() - finishedAt <= FINISH_GRACE_TICKS;
	}

	// --- using the mortar ---

	public void use(ServerPlayer player, ItemStack held) {
		long now = now();
		if (!result.isEmpty()) {
			player.getInventory().placeItemBackInInventory(result, Prediction.SERVER_ONLY);
			result = ItemStack.EMPTY;
			level.playSound(null, worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.6F, 1.2F);
			changed();
			return;
		}
		if (!held.isEmpty()) {
			if (BrewGrades.ground(held) >= 3 && held.is(Brewing.GRINDABLE)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortar.already_ground"));
			} else if (!held.is(Brewing.GRINDABLE)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortar.cant_grind"));
			} else if (grinding != null || (!contents.isEmpty() && !ItemStack.isSameItemSameComponents(held, contents))) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortar.other"));
			} else if (contents.getCount() >= HOLDS) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortar.full", HOLDS));
			} else {
				int move = Math.min(held.getCount(), HOLDS - contents.getCount());
				if (contents.isEmpty()) {
					contents = held.split(move);
				} else {
					contents.grow(move);
					held.shrink(move);
				}
				level.playSound(null, worldPosition, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
				changed();
			}
			return;
		}
		if (contents.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortar.empty"));
			return;
		}
		if (grinding == null) {
			if (player.isShiftKeyDown()) {
				player.getInventory().placeItemBackInInventory(contents, Prediction.SERVER_ONLY);
				contents = ItemStack.EMPTY;
				changed();
				return;
			}
			grinding = new Grinding(0, 0.0F, 0, level.getRandom().nextLong() | 1L);
			changed();
		} else if (grinder != null && !grinder.equals(player.getUUID()) && grinderNear()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortar.busy"));
			return;
		}
		grinder = player.getUUID();
		lastStroke = now - MIN_STROKE_GAP;
		sendSession(player, -1);
	}

	/** One stroke, with the arrow where the grinder's bar showed it. The server judges it. */
	public void stroke(ServerPlayer player, float arrow) {
		if (grinding == null || !player.getUUID().equals(grinder) || !Float.isFinite(arrow) || arrow < 0.0F || arrow > 100.0F
			|| contents.isEmpty() || !(level instanceof ServerLevel server)) {
			return;
		}
		long now = now();
		if (now - lastStroke < MIN_STROKE_GAP) {
			return;
		}
		int judged = BrewQuality.judge(BrewQuality.GRIND, arrow, grinding.seed(), grinding.done(), BrewQuality.bonus(player));
		grinding = grinding.hit(judged);
		lastStroke = now;
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		strokeEffects(server, judged);
		if (grinding.done() >= BrewQuality.GRIND.strokes()) {
			finish(player);
			return;
		}
		changed();
		sendSession(player, judged);
	}

	private void finish(ServerPlayer player) {
		int strokes = BrewQuality.GRIND.strokes();
		int ground = Math.max(BrewGrades.ground(contents), BrewQuality.groundLevel(grinding.score() / strokes));
		result = contents.copy();
		result.set(Brewing.GROUND, ground);
		contents = ItemStack.EMPTY;
		grinding = null;
		grinder = null;
		finishedBy = player.getUUID();
		finishedAt = now();
		changed();
		ServerPlayNetworking.send(player, BrewingNet.BrewBar.off(worldPosition));
		level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
			ground == 3 ? 1.0F : 0.6F, ground == 3 ? 1.4F : 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortar.done", result.getHoverName(),
			Component.translatable("tooltip.jaymospowers.ground." + ground)));
	}

	private void strokeEffects(ServerLevel server, int judged) {
		double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.4, z = worldPosition.getZ() + 0.5;
		SoundEvent sound = SoundEvents.GRINDSTONE_USE;
		float volume = judged == BrewQuality.PERFECT ? 0.7F : judged == BrewQuality.GOOD ? 0.5F : 0.6F;
		float pitch = judged == BrewQuality.PERFECT ? 1.5F : judged == BrewQuality.GOOD ? 1.1F : 0.6F;
		server.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
		server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, contents.getItem()), x, y, z, 6, 0.12, 0.05, 0.12, 0.04);
		if (judged == BrewQuality.PERFECT) {
			server.sendParticles(ParticleTypes.CRIT, x, y + 0.2, z, 4, 0.12, 0.1, 0.12, 0.05);
		}
	}

	private void sendSession(ServerPlayer player, int last) {
		Grinding g = grinding;
		if (g == null) {
			return;
		}
		ServerPlayNetworking.send(player, new BrewingNet.BrewBar(worldPosition, true, BrewQuality.GRIND_GAME, BrewQuality.GRIND.strokes(),
			g.done(), BrewQuality.bonus(player), g.seed(), last));
	}

	/** Ends the grinder's game on their screen; the progress stays on the mortar. */
	private void endSession() {
		if (grinder != null && level instanceof ServerLevel server && server.getPlayerByUUID(grinder) instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, BrewingNet.BrewBar.off(worldPosition));
		}
		grinder = null;
	}

	private boolean grinderNear() {
		if (grinder == null || !(level instanceof ServerLevel server) || !(server.getPlayerByUUID(grinder) instanceof ServerPlayer player)) {
			return false;
		}
		return player.blockPosition().distSqr(worldPosition) <= LEAVE_SQ;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MortarEntity mortar) {
		if ((level.getGameTime() + pos.asLong()) % CHECK_TICKS != 0) {
			return;
		}
		if (mortar.grinder != null && !mortar.grinderNear()) {
			mortar.endSession();
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
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), contents);
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), result);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		contents = input.read("contents", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		result = input.read("result", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		grinding = input.read("grinding", Grinding.CODEC).orElse(null);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("contents", ItemStack.OPTIONAL_CODEC, contents);
		output.store("result", ItemStack.OPTIONAL_CODEC, result);
		if (grinding != null) {
			output.store("grinding", Grinding.CODEC, grinding);
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
