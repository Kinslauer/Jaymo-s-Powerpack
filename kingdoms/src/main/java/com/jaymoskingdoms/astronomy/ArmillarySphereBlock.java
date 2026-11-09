package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import org.jspecify.annotations.Nullable;

/**
 * The armillary sphere: an observatory instrument. A right click records the
 * current sky (world time, day and place) on its block entity and opens its
 * screen, which reads off the stars the player has already discovered.
 */
public final class ArmillarySphereBlock extends BaseEntityBlock {
    public static Block BLOCK;
    public static Item ITEM;
    public static BlockEntityType<ArmillaryBlockEntity> ENTITY;

    public ArmillarySphereBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("armillary_sphere"));
        Registry.register(BuiltInRegistries.BLOCK, blockKey,
                BLOCK = new ArmillarySphereBlock(BlockBehaviour.Properties.of()
                        .setId(blockKey)
                        .mapColor(MapColor.STONE)
                        .strength(2.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("armillary_sphere"));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                ITEM = new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("armillary_sphere"),
                new BlockEntityType<>(ArmillaryBlockEntity::new, java.util.Set.of(BLOCK)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(output -> output.insertAfter(Items.SMOKER, ITEM));
        PayloadTypeRegistry.clientboundPlay().register(ArmillaryNet.Open.TYPE, ArmillaryNet.Open.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(ArmillaryNet.Measure.TYPE, ArmillaryNet.Measure.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(DiscoveryC2SPacket.TYPE, DiscoveryC2SPacket.CODEC);
                ServerPlayNetworking.registerGlobalReceiver(DiscoveryC2SPacket.TYPE, (payload, context) -> {
                    context.player().ifPresent(player -> {
                        if (player.level() instanceof ServerLevel serverLevel) {
                            DiscoveryAttachment.setDiscovered(serverLevel.getPlayerList().getPlayer(player.getUUID()), DiscoveryC2SPacket.readDiscovered(payload));
                        }
                    });
                });
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArmillaryBlockEntity e) {
                e.record((ServerLevel) level);
                ServerPlayNetworking.send(serverPlayer, new ArmillaryNet.Open(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArmillaryBlockEntity(pos, state);
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
