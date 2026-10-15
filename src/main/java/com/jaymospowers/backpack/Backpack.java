package com.jaymospowers.backpack;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.82) The Backpack (Andy: "a backpack that animates on the player and can be placed on the ground. it is only
 * broken by strong explosions"). Our own; Traveler's Backpack has no 26.3 Fabric build.
 * <ul>
 * <li>Four rows of storage. Right-click it in the air to put it on your back: it is drawn there over the body's pose
 * (Fresh Animations' too) and swings, bounces and flaps as you move. The backpack key (` by default) opens it; sneak
 * and press it to take it off into your hand.</li>
 * <li>Right-click a block with it to set it down. Right-click the placed backpack to open it; sneak-right-click it with
 * an empty hand to put it on (into the inventory if you already wear one).</li>
 * <li>Set down it can't be mined, pushed or carried, and ordinary blasts (TNT, creepers, beds) leave it alone. Only an
 * explosion of power {@link #STRONG_BLAST} or more (a charged creeper, an end crystal, the wither) tears it apart, and
 * its contents scatter. The wither and the dragon can't break it either.</li>
 * <li>Die wearing it and it is set down where you fell, so the blast-proof bag keeps your things (kept on your back
 * with keepInventory).</li>
 * </ul>
 */
public final class Backpack {
	/** Rows of nine slots. */
	public static final int ROWS = 4;
	/** The weakest explosion that tears a set-down backpack apart (TNT is 4, a charged creeper and an end crystal 6). */
	public static final float STRONG_BLAST = 6.0F;
	/** Leather left behind when one is torn apart. */
	public static final int SCRAPS = 3;

	/** On a player: the backpack on their back (server only: its contents are nobody else's business). */
	public static final AttachmentType<ItemStack> WORN = AttachmentRegistry.create(JaymosPowers.id("worn_backpack"),
		builder -> builder.persistent(ItemStack.CODEC).copyOnDeath());
	/** On a player: whether they wear one (synced to everyone, for drawing it). */
	public static final AttachmentType<Boolean> SHOWN = AttachmentRegistry.create(JaymosPowers.id("backpack_shown"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	public static Block BLOCK;
	public static Item ITEM;
	public static BlockEntityType<BackpackEntity> ENTITY;

	private Backpack() {
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("backpack"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new BackpackBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
			.strength(-1.0F, 3600000.0F).sound(SoundType.WOOL).noOcclusion().noLootTable().pushReaction(PushReaction.IMMOVEABLE).setId(key)));
		ITEM = item("backpack", p -> new BackpackItem(BLOCK, p.stacksTo(1).useBlockDescriptionPrefix()));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("backpack"),
			new BlockEntityType<>(BackpackEntity::new, Set.of(BLOCK)));
		BackpackNet.init();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(ITEM));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
	}

	private static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	/** Whether this player wears a backpack (either side). */
	public static boolean wearing(Player player) {
		return Boolean.TRUE.equals(player.getAttached(SHOWN));
	}

	/** The backpack on this player's back (server), or null. */
	public static @Nullable ItemStack worn(Player player) {
		ItemStack stack = player.getAttached(WORN);
		return stack == null || stack.isEmpty() ? null : stack;
	}

	/** Puts {@code stack} (one backpack) on this player's back (server). */
	public static void putOn(ServerPlayer player, ItemStack stack) {
		player.setAttached(WORN, stack.copyWithCount(1));
		player.setAttached(SHOWN, true);
		rustle(player);
	}

	/** Takes the worn backpack off this player (server) and gives it back to the caller, or null if none. */
	public static @Nullable ItemStack takeOff(ServerPlayer player) {
		ItemStack stack = worn(player);
		if (stack == null) {
			return null;
		}
		if (player.containerMenu instanceof BackpackMenu menu && menu.worn()) {
			player.closeContainer();
			stack = worn(player);
			if (stack == null) {
				return null;
			}
		}
		player.removeAttached(WORN);
		player.removeAttached(SHOWN);
		rustle(player);
		return stack;
	}

	/** The backpack key: open the worn one, or (sneaking) take it off into the hand. */
	static void useKey(ServerPlayer player, boolean sneaking) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		if (sneaking) {
			ItemStack stack = takeOff(player);
			if (stack == null) {
				return;
			}
			if (player.getMainHandItem().isEmpty()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			} else {
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
			return;
		}
		ItemStack stack = worn(player);
		if (stack == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.backpack.none"));
			return;
		}
		Component title = stack.getHoverName();
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> BackpackMenu.worn(id, inventory, new WornContainer(player)), title));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 0.9F);
	}

	/** Dying with one on: it is set down where they fell (or dropped, if there's nowhere), unless keepInventory holds. */
	private static void onDeath(ServerPlayer player) {
		ServerLevel level = player.level();
		if (worn(player) == null || level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		ItemStack stack = takeOff(player);
		if (stack == null) {
			return;
		}
		BlockPos at = player.blockPosition();
		for (int up = 0; up < 4; up++) {
			BlockPos pos = at.above(up);
			if (level.isInWorldBounds(pos) && level.getBlockState(pos).canBeReplaced() && level.getWorldBorder().isWithinBounds(pos)) {
				setDown(level, pos, player.getDirection().getOpposite(), stack);
				return;
			}
		}
		Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), stack);
	}

	/** Sets {@code stack} down as a placed backpack at {@code pos}, facing {@code facing}. */
	static void setDown(Level level, BlockPos pos, Direction facing, ItemStack stack) {
		BlockState state = BLOCK.defaultBlockState().setValue(BackpackBlock.FACING, facing);
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof BackpackEntity entity) {
			entity.applyComponentsFromItemStack(stack);
			entity.setChanged();
		}
	}

	/**
	 * After an explosion (ServerExplosionMixin): one of {@link #STRONG_BLAST} or more tears apart every set-down backpack
	 * within its radius. Its contents scatter and a little leather is left.
	 */
	public static void blast(ServerLevel level, Vec3 center, float radius) {
		if (radius < STRONG_BLAST) {
			return;
		}
		int r = (int)Math.ceil(radius);
		BlockPos middle = BlockPos.containing(center);
		double reach = (double)radius * radius;
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-r, -r, -r), middle.offset(r, r, r))) {
			if (!level.getBlockState(pos).is(BLOCK) || pos.distToCenterSqr(center) > reach) {
				continue;
			}
			BlockPos at = pos.immutable();
			// the block entity spills what it holds as it goes (BlockEntity.preRemoveSideEffects)
			level.removeBlock(at, false);
			Containers.dropItemStack(level, at.getX(), at.getY(), at.getZ(), new ItemStack(Items.LEATHER, SCRAPS));
			level.playSound(null, at, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
		}
	}

	static void rustle(Player player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
