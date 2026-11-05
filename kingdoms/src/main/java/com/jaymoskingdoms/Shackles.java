package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.584) What holds a captive, seen. Andy (2026-11-05): "there should be some kind of visible chain or yoke around the
 * slaves/ prisoners necks that stops them from going too far. chans can be broken with rocks or flint and repeatedly
 * slamming, and the rope can be sawed through similarly."
 * <p>
 * A player put in a camp's shackle ({@link StoryHard#shackle}) is collared and tied ({@link #tie}) to a stake driven in
 * beside them: an iron collar on a chain to a stone post, or a rope noose to a wooden one. The tether is synced to
 * everyone, so every client draws the collar and the line from neck to stake ({@code ShackleTether}). It's the same leash
 * {@link StoryHard#CHAIN_REACH} the chains always had.
 * <p>
 * Getting it off besides the lockpick and the slip in the dark: a <b>chain</b> is beaten apart with a rock (flint, a
 * flint shard, cobble or any stone held and used) in {@link #CHAIN_SLAMS} loud slams, each heard by any captor within
 * {@link #HEAR_RANGE} at {@link #HEARD_CHANCE}; a <b>rope</b> is sawn through with anything sharp (flint, a shard, a
 * blade, shears) in {@link #ROPE_STROKES} strokes, each seen by a watching captor at {@link #SEEN_CHANCE}. Caught, the
 * captive has acted up ({@link CaptiveConduct#actUp}). Through, they're free and hunted as from a picked lock.
 */
public final class Shackles {
	/** Slams a chain takes to break (a rock), and strokes a rope takes to saw through; the time (ticks) between each. */
	static final int CHAIN_SLAMS = 24, ROPE_STROKES = 16, SLAM_COOLDOWN = 10, STROKE_COOLDOWN = 8;
	/** A slam's chance to crack a flint or a rock in the hand. */
	static final float ROCK_CRACK = 0.08F;
	/** How far (blocks) a slam carries to a captor, and the chance one hears each; a saw stroke's chance to be seen. */
	static final double HEAR_RANGE = 14.0;
	static final float HEARD_CHANCE = 0.15F, SEEN_CHANCE = 0.3F;
	/** The chance a captive is tied with rope rather than chained. */
	static final float ROPE_CHANCE = 0.4F;

	/** The stake a captive is tied to, and whether by rope (else chain). */
	public record Tether(BlockPos stake, boolean rope) {
		static final Codec<Tether> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("stake").forGetter(Tether::stake),
			Codec.BOOL.fieldOf("rope").forGetter(Tether::rope)
		).apply(i, Tether::new));
		static final StreamCodec<RegistryFriendlyByteBuf, Tether> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Tether::stake, ByteBufCodecs.BOOL, Tether::rope, Tether::new);
	}

	/** On a shackled player: their tether. Saved, kept through death, synced to everyone (they draw it). */
	public static final AttachmentType<Tether> TETHER = AttachmentRegistry.create(JaymosKingdoms.id("captive_tether"),
		builder -> builder.persistent(Tether.CODEC).copyOnDeath().syncWith(Tether.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Player → slams or strokes done on their tether so far. */
	private static final Map<UUID, Integer> WORN = new HashMap<>();

	private Shackles() {
	}

	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			Tether tether = sp.getAttached(TETHER);
			if (tether == null || !StoryHard.shackled(sp)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			if (sp.getCooldowns().isOnCooldown(held)) {
				return InteractionResult.FAIL;
			}
			if (tether.rope() ? cutter(held) : rock(held)) {
				work(sp, tether, held);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			// (the stake doesn't come up while someone is tied to it)
			Tether tether = player.getAttached(TETHER);
			return tether == null || !tether.stake().equals(pos);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WORN.clear());
	}

	/** {@code player}, just shackled at {@code at}: collared and tied to a stake driven in beside them. */
	static void tie(ServerPlayer player, BlockPos at, RandomSource random) {
		ServerLevel level = player.level();
		boolean rope = random.nextFloat() < ROPE_CHANCE;
		BlockPos stake = stakeSpot(level, at);
		if (stake == null) {
			stake = at.below();
		} else {
			level.setBlock(stake, rope ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
		}
		player.setAttached(TETHER, new Tether(stake, rope));
		WORN.remove(player.getUUID());
	}

	/** The collar comes off (the shackle's gone, however). */
	static void untie(ServerPlayer player) {
		player.removeAttached(TETHER);
		WORN.remove(player.getUUID());
	}

	/** A free spot beside {@code at} (sides, then corners) on firm ground for the stake, or null. */
	private static BlockPos stakeSpot(ServerLevel level, BlockPos at) {
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
		for (int[] d : around) {
			BlockPos p = at.offset(d[0], 0, d[1]);
			BlockState here = level.getBlockState(p);
			if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty()
				&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
				return p;
			}
		}
		return null;
	}

	/** A rock to beat a chain with: flint, a flint shard, cobble or any stone. */
	static boolean rock(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(Items.FLINT) || stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("flint_shard") || path.contains("rock") || path.contains("pebble") || path.equals("stone");
	}

	/** Something sharp to saw a rope with: flint, a flint shard, a blade, shears. */
	static boolean cutter(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(Items.FLINT) || stack.is(Items.SHEARS) || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("flint_shard") || path.contains("knife") || path.contains("dagger") || path.contains("blade")
			|| path.contains("cleaver") || path.contains("machete") || path.contains("saw");
	}

	/** One slam on the chain, or one stroke of the saw on the rope. */
	private static void work(ServerPlayer player, Tether tether, ItemStack held) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		double nx = player.getX(), ny = player.getEyeY() - 0.3, nz = player.getZ();
		if (tether.rope()) {
			player.getCooldowns().addCooldown(held, STROKE_COOLDOWN);
			level.playSound(null, nx, ny, nz, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.35F, 0.7F + random.nextFloat() * 0.2F);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.STRING))), nx, ny, nz, 3, 0.1, 0.05, 0.1, 0.02);
			if (held.isDamageableItem()) {
				held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			}
		} else {
			player.getCooldowns().addCooldown(held, SLAM_COOLDOWN);
			level.playSound(null, nx, ny, nz, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.3F, 0.7F + random.nextFloat() * 0.3F);
			level.playSound(null, nx, ny, nz, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.9F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, nx, ny, nz, 4, 0.1, 0.05, 0.1, 0.1);
			if (!held.isDamageableItem() && random.nextFloat() < ROCK_CRACK) {
				held.shrink(1);
				level.playSound(null, nx, ny, nz, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
			} else if (held.isDamageableItem()) {
				held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			}
		}
		if (caught(player, tether.rope())) {
			CaptiveConduct.actUp(player, tether.rope() ? "saw" : "slam");
		}
		int need = tether.rope() ? ROPE_STROKES : CHAIN_SLAMS;
		int done = WORN.merge(player.getUUID(), 1, Integer::sum);
		if (done >= need) {
			WORN.remove(player.getUUID());
			level.playSound(null, nx, ny, nz, tether.rope() ? SoundEvents.LEAD_BREAK : SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
			StoryHard.free(player, tether.rope() ? "cut" : "broken");
			return;
		}
		player.sendOverlayMessage(Component.translatable(tether.rope() ? "message.jaymoskingdoms.shackle.sawing" : "message.jaymoskingdoms.shackle.slamming",
			done * 100 / need).withStyle(ChatFormatting.GRAY));
	}

	/** Whether a captor hears the slam (any within {@link #HEAR_RANGE}) or sees the sawing (one watching). */
	private static boolean caught(ServerPlayer player, boolean rope) {
		if (!CaptiveConduct.behaving(player)) {
			return false;
		}
		RandomSource random = player.getRandom();
		if (rope) {
			return CaptiveConduct.watched(player) && random.nextFloat() < SEEN_CHANCE;
		}
		int camp = StoryHard.shackleCamp(player);
		int hearers = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HEAR_RANGE),
			m -> m.isAlive() && CaptiveConduct.captor(m, camp)).size();
		for (int i = 0; i < hearers; i++) {
			if (random.nextFloat() < HEARD_CHANCE) {
				return true;
			}
		}
		return false;
	}
}
