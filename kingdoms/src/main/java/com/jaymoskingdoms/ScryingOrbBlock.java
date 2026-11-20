package com.jaymoskingdoms;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The scrying orb: a glass sphere on a carved stand. Right-click with a lock of hair or a sighted shard
 * while attuned to wake it. The orb shows a living miniature of the land round that person or place.
 */
public final class ScryingOrbBlock extends BaseEntityBlock {
    public static Block BLOCK;
    public static Item ITEM;
    public static BlockEntityType<ScryingOrbBlockEntity> ENTITY;

    public ScryingOrbBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("scrying_orb"));
        Registry.register(BuiltInRegistries.BLOCK, blockKey,
                BLOCK = new ScryingOrbBlock(BlockBehaviour.Properties.of()
                        .setId(blockKey)
                        .mapColor(MapColor.COLOR_LIGHT_GRAY)
                        .strength(1.5F)
                        .sound(SoundType.GLASS)
                        .noOcclusion()
                        .lightLevel(state -> state.getValue(ACTIVE) ? 8 : 0)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("scrying_orb"));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                ITEM = new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("scrying_orb"),
                new BlockEntityType<>(ScryingOrbBlockEntity::new, java.util.Set.of(BLOCK)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(output -> output.insertAfter(Items.END_CRYSTAL, ITEM));
        // Packets are registered in ScryingOrb.register()
        PayloadTypeRegistry.clientboundPlay().register(ScryingOrb.OrbView.TYPE, ScryingOrb.OrbView.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScryingOrb.Close.TYPE, ScryingOrb.Close.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScryingOrb.Cloud.TYPE, ScryingOrb.Cloud.CODEC);
    }

    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE = net.minecraft.world.level.block.state.properties.BooleanProperty.create("active");

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ScryingOrbBlockEntity orb) {
                ItemStack mainHand = serverPlayer.getMainHandItem();
                ItemStack offHand = serverPlayer.getOffhandItem();
                ItemStack focus = mainHand.isEmpty() ? offHand : mainHand;
                if (!focus.isEmpty() && Mana.attuned(serverPlayer)) {
                    if (ScryingOrb.wake((ServerLevel) level, serverPlayer, focus)) {
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScryingOrbBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(new ItemStack(BLOCK));
    }
}