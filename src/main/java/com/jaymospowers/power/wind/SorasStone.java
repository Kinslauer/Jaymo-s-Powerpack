package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Only the worthy lift it: a third way to find Sora's Dagger, beside the village child and the smith's wall
 * ({@link SorasLineage}, the same one-find-per-dagger rule). Where a falling star comes down (see
 * {@code FallingStars}), the stone blade may be found driven into a dark stone beside the crater, while no line is out.
 * Nobody can draw it until they have earned it: {@link #KILLS_NEEDED} hostile monsters killed (counted here) and a village
 * saved (the Hero of the Village effect earned at least once, or the scoreboard tag {@link #SAVED_TAG}). The unworthy
 * strain at it and it does not move: a shudder, a grunt, a jolt through the stone, and no word of why.
 */
public final class SorasStone {
	static final String TAG = "jaymospowers_soras_stone";
	/** The scoreboard tag any mod may give a player who has saved a village (Hero of the Village is read too). */
	public static final String SAVED_TAG = "jaymos_village_saved";
	public static final int KILLS_NEEDED = 200;
	/** The odds a fallen star leaves the blade in a stone beside its crater, while no line is out and none waits in a stone. */
	static final float CHANCE = 0.3F;
	/** How far round the star (blocks) the stone is looked for. */
	static final int RING_MIN = 2, RING_MAX = 5;
	/** How often (ticks) players are checked for the effect that says a village was saved. */
	static final int CHECK_EVERY = 40;

	/** Where the stone stands: the world, the stone's foot and the frame holding the blade. */
	public record Spot(String dimension, BlockPos pos, UUID frame) {
		public static final Codec<Spot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Spot::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos),
			UUIDUtil.CODEC.fieldOf("frame").forGetter(Spot::frame)
		).apply(i, Spot::new));
	}

	/** On the Overworld: the stone waiting to be found, if any. */
	public static final AttachmentType<Spot> STONE = AttachmentRegistry.create(JaymosPowers.id("soras_stone"),
		builder -> builder.persistent(Spot.CODEC));
	/** On a player: hostile monsters they have killed. */
	public static final AttachmentType<Integer> KILLS = AttachmentRegistry.create(JaymosPowers.id("soras_kills"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** On a player: they have earned the Hero of the Village. */
	public static final AttachmentType<Boolean> SAVED = AttachmentRegistry.create(JaymosPowers.id("soras_saved"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private SorasStone() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer killer && killer != entity) {
				killer.setAttached(KILLS, kills(killer) + 1);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && player.getAttached(SAVED) == null) {
						player.setAttached(SAVED, true);
					}
				}
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof ItemFrame frame) || !entity.entityTags().contains(TAG)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				pull(server, frame);
			}
			return InteractionResult.SUCCESS;
		});
	}

	static int kills(ServerPlayer player) {
		Integer kills = player.getAttached(KILLS);
		return kills == null ? 0 : kills;
	}

	/** Whether this player has killed enough monsters and saved a village. */
	public static boolean worthy(ServerPlayer player) {
		boolean saved = player.getAttached(SAVED) != null || player.entityTags().contains(SAVED_TAG) || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
		return saved && kills(player) >= KILLS_NEEDED;
	}

	private static void pull(ServerPlayer player, ItemFrame frame) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		Spot spot = server.overworld().getAttached(STONE);
		ItemStack blade = frame.getItem().copy();
		if (spot == null || !spot.frame().equals(frame.getUUID()) || !SorasLineage.open(server) || !SorasLineage.marked(blade)) {
			frame.discard();
			return;
		}
		if (!worthy(player)) {
			strain(player, level, frame);
			return;
		}
		frame.discard();
		level.playSound(null, frame.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, frame.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
		MagicFx.blast(level, frame.position(), 1.6F, StormDraw.BOLT);
		if (!player.getInventory().add(blade)) {
			player.drop(blade, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		SorasLineage.claim(server);
	}

	/** The unworthy pull and the stone does not move: a grunt, a shudder through them, grit knocked loose. No words. */
	private static void strain(ServerPlayer player, ServerLevel level, ItemFrame frame) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.9F, 0.6F);
		level.playSound(null, frame.blockPosition(), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.CRIT, frame.getX(), frame.getY(), frame.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
		Shakes.send(player, 0.6F);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4, true, false, false));
		player.causeFoodExhaustion(1.0F);
	}

	/** A fallen star has come down at {@code star}: the blade may be set in a stone beside it. */
	public static void crater(ServerLevel level, BlockPos star) {
		MinecraftServer server = level.getServer();
		if (server == null || !SorasLineage.open(server) || server.overworld().getAttached(STONE) != null
			|| level.getRandom().nextFloat() >= CHANCE) {
			return;
		}
		var random = level.getRandom();
		for (int tries = 0; tries < 60; tries++) {
			int dx = random.nextIntBetweenInclusive(-RING_MAX, RING_MAX);
			int dz = random.nextIntBetweenInclusive(-RING_MAX, RING_MAX);
			if (Math.abs(dx) < RING_MIN && Math.abs(dz) < RING_MIN) {
				continue;
			}
			for (int dy = 1; dy >= -1; dy--) {
				BlockPos foot = star.offset(dx, dy, dz);
				if (!level.getBlockState(foot.below()).isSolid() || !level.getBlockState(foot).isAir() || !level.getBlockState(foot.above()).isAir()
					|| !level.getBlockState(foot.above(2)).isAir()) {
					continue;
				}
				for (Direction facing : Direction.Plane.HORIZONTAL) {
					BlockPos open = foot.above().relative(facing);
					if (!level.getBlockState(open).isAir()) {
						continue;
					}
					level.setBlock(foot, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
					level.setBlock(foot.above(), Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
					ItemFrame frame = hang(level, open, facing, SorasLineage.foundBlade(server));
					if (frame != null) {
						server.overworld().setAttached(STONE, new Spot(level.dimension().identifier().toString(), foot.immutable(), frame.getUUID()));
						return;
					}
					level.setBlock(foot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}

	private static @Nullable ItemFrame hang(ServerLevel level, BlockPos at, Direction facing, ItemStack blade) {
		ItemFrame frame = new ItemFrame(level, at.immutable(), facing);
		if (!frame.survives()) {
			return null;
		}
		((com.jaymospowers.power.wind.mixin.ItemFrameAccess) frame).jaymospowers$setFixed(true);
		frame.setItem(blade, false);
		frame.addTag(TAG);
		frame.setInvisible(true);
		frame.setPermanentlyInvulnerable(true);
		level.addFreshEntity(frame);
		return frame;
	}

	/** The line is out by another find: the stone gives up its blade (its frame goes now, or when its chunk next loads). */
	static void claimed(MinecraftServer server) {
		Spot spot = server.overworld().getAttached(STONE);
		if (spot == null) {
			return;
		}
		server.overworld().removeAttached(STONE);
		for (ServerLevel level : server.getAllLevels()) {
			Entity frame = level.getEntity(spot.frame());
			if (frame != null) {
				frame.discard();
			}
		}
	}

	/** A frame of this find that is no longer the stone that waits: it goes. */
	static boolean stale(MinecraftServer server, Entity frame) {
		Spot spot = server.overworld().getAttached(STONE);
		return spot == null || !spot.frame().equals(frame.getUUID());
	}
}
