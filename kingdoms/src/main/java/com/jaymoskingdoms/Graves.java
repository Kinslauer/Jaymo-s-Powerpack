package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Burying the dead (the death and land picks, E4 and E5). Use a shovel on a dead player's body and it is lowered into the
 * ground under a {@link GraveBlock} (the Kitchen's {@code Burial} saves the whole body, so nothing is lost and nothing rots in
 * the ground). Dig the mound out, or lose the block any way at all, and the body lies there again. A buried body is a body for
 * the rite of a new blood ({@link Rebirths}) just the same: the rite digs it up itself. Burial is what the player in Limbo
 * watches their friends do.
 */
public final class Graves {
	// ---- tuning ----
	/** How many uses a burial takes off a shovel. */
	static final int SHOVEL_WEAR = 6;
	/** Graves are looked at this often (ticks): a mound that is gone brings its body up. */
	static final int WATCH_EVERY = 100;

	public static Block GRAVE;
	public static Item GRAVE_ITEM;

	/** One body in the ground: where, whose, when (game day), and the body saved as data. */
	record Grave(long pos, String owner, long day, CompoundTag body) {
		static final Codec<Grave> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("pos").forGetter(Grave::pos),
			Codec.STRING.fieldOf("owner").forGetter(Grave::owner),
			Codec.LONG.fieldOf("day").forGetter(Grave::day),
			CompoundTag.CODEC.fieldOf("body").forGetter(Grave::body)).apply(i, Grave::new));
	}

	private static final AttachmentType<List<Grave>> BURIED = AttachmentRegistry.create(JaymosKingdoms.id("graves"),
		b -> b.persistent(Grave.CODEC.listOf()));

	private Graves() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("grave_mound"));
		GRAVE = Registry.register(BuiltInRegistries.BLOCK, key, new GraveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6F)
			.sound(SoundType.GRAVEL).noOcclusion().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("grave_mound"));
		GRAVE_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(GRAVE, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.accept(GRAVE_ITEM));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(level instanceof ServerLevel server) || !player.getItemInHand(hand).is(ItemTags.SHOVELS) || !DeadBodies.isPlayerBody(entity)) {
				return InteractionResult.PASS;
			}
			if (bury(server, entity)) {
				player.getItemInHand(hand).hurtAndBreak(SHOVEL_WEAR, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.grave.no_ground"));
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Graves::watch);
	}

	private static List<Grave> all(ServerLevel level) {
		return level.getAttachedOrElse(BURIED, List.<Grave>of());
	}

	/** Lays {@code body} in the ground under a mound: false if there is no ground or no room for the mound. */
	static boolean bury(ServerLevel level, Entity body) {
		BlockPos at = BlockPos.containing(body.position());
		for (int i = 0; i < 3 && !level.getBlockState(at).isAir() && !level.getBlockState(at).canBeReplaced(); i++) {
			at = at.above();
		}
		BlockState ground = level.getBlockState(at.below());
		if ((!level.getBlockState(at).isAir() && !level.getBlockState(at).canBeReplaced()) || ground.isAir() || !ground.isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
			|| level.getBlockEntity(at) != null) {
			return false;
		}
		String owner = DeadBodies.owner(body).map(UUID::toString).orElse("");
		CompoundTag saved = DeadBodies.lower(body);
		if (saved == null) {
			return false;
		}
		List<Grave> graves = new ArrayList<>(all(level));
		graves.add(new Grave(at.asLong(), owner, level.getGameTime() / 24000L, saved));
		level.setAttached(BURIED, graves);
		level.setBlockAndUpdate(at, GRAVE.defaultBlockState());
		level.playSound(null, at, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 14, 0.4, 0.1, 0.4, 0.01);
		return true;
	}

	/** The mound at {@code pos} is gone (dug, blown, burnt): its body lies there again. */
	static void opened(ServerLevel level, BlockPos pos) {
		Entity body = exhume(level, pos);
		if (body != null) {
			level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		}
	}

	/** Takes the grave at {@code pos} out of the ground: the body lies there again, the entry is gone. Null if there was none. */
	static @Nullable Entity exhume(ServerLevel level, BlockPos pos) {
		List<Grave> graves = new ArrayList<>(all(level));
		for (Grave g : graves) {
			if (g.pos == pos.asLong()) {
				graves.remove(g);
				level.setAttached(BURIED, graves);
				if (level.getBlockState(pos).is(GRAVE)) {
					level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				}
				return DeadBodies.raise(level, g.body, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			}
		}
		return null;
	}

	/** A grave of {@code owner} within {@code range} of {@code at}, as the place of its mound; null if there is none. */
	static @Nullable BlockPos graveOf(ServerLevel level, UUID owner, Vec3 at, double range) {
		for (Grave g : all(level)) {
			BlockPos pos = BlockPos.of(g.pos);
			if (g.owner.equals(owner.toString()) && Vec3.atCenterOf(pos).distanceToSqr(at) <= range * range) {
				return pos;
			}
		}
		return null;
	}

	/** Every so often: a mound that is gone (replaced, not broken) brings its body up. */
	private static void watch(MinecraftServer server) {
		if (server.getTickCount() % WATCH_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Grave g : List.copyOf(all(level))) {
				BlockPos pos = BlockPos.of(g.pos);
				if (level.isLoaded(pos) && !level.getBlockState(pos).is(GRAVE)) {
					opened(level, pos);
				}
			}
		}
	}
}
