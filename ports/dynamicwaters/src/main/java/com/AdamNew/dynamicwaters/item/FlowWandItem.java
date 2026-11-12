package com.AdamNew.dynamicwaters.item;

import com.AdamNew.dynamicwaters.FlowZones;
import com.AdamNew.dynamicwaters.block.FlowOverrideBlock;
import com.AdamNew.dynamicwaters.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FlowWandItem extends Item {
    private static final int SCAN_RADIUS = 5;

    public static final double ZONE_REACH = 64.0;

    public static final double LEVEL_LOOK = 0.3;

    private static final int ZONE_CLICK_COOLDOWN = 5;

    private static final String ZONE_MODE = "ZoneMode";
    private static final String CORNER_A = "CornerA";
    private static final String CORNER_B = "CornerB";
    private static final String CORNER_DIM = "CornerDim";

    public FlowWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (isZoneMode(context.getItemInHand()))
            return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null)
            return InteractionResult.PASS;

        BlockPos waterPos = clickedPos;
        if (!isWater(level.getBlockState(waterPos))) {
            waterPos = clickedPos.relative(context.getClickedFace());
            if (!isWater(level.getBlockState(waterPos))) {
                return InteractionResult.PASS;
            }
        }

        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        boolean disableFlow = player.isShiftKeyDown();

        if (disableFlow) {
            applyOverride(level, waterPos, -1, false);
            player.sendSystemMessage(prefixed(Component.translatable("message.dynamicwaters.flow_wand.disabled"),
                    ChatFormatting.GOLD));
        } else {
            Vec3 lookVec = player.getLookAngle();
            int direction = FlowOverrideBlock.vec3ToDirection(lookVec.x, lookVec.z);
            applyOverride(level, waterPos, direction, true);
            player.sendSystemMessage(prefixed(Component.translatable("message.dynamicwaters.flow_wand.set",
                    FlowZones.directionName(direction)), ChatFormatting.AQUA));
        }

        level.playSound(null, waterPos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.5f, 1.2f);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!isZoneMode(stack)) {
            if (!player.isShiftKeyDown() || target(level, player).getType() != HitResult.Type.MISS)
                return InteractionResult.PASS;
            if (!level.isClientSide()) {
                setZoneMode(stack, true);
                player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_wand.mode.zone"));
                chime(player, 1.2f);
                player.getCooldowns().addCooldown(stack, ZONE_CLICK_COOLDOWN);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            zoneClick((ServerLevel) level, player, stack);
            player.getCooldowns().addCooldown(stack, ZONE_CLICK_COOLDOWN);
        }
        return InteractionResult.SUCCESS;
    }

    private static void zoneClick(ServerLevel level, Player player, ItemStack stack) {
        final BlockPos a = corner(stack, level, false);
        final BlockPos b = a != null ? corner(stack, level, true) : null;
        final boolean shift = player.isShiftKeyDown();

        if (b != null) {
            int direction = FlowZones.STILL;
            if (!shift) {
                final Vec3 look = player.getLookAngle();
                if (look.x * look.x + look.z * look.z < LEVEL_LOOK * LEVEL_LOOK) {
                    player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.look_level"));
                    return;
                }
                direction = FlowOverrideBlock.vec3ToDirection(look.x, look.z);
            }
            final FlowZones.Zone zone = FlowZones.add(level, a, b, direction);
            if (zone == null) {
                player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.too_many",
                        FlowZones.MAX_ZONES));
                return;
            }
            clearCorners(stack);
            player.sendSystemMessage(prefixed(Component.translatable("message.dynamicwaters.flow_zone.created",
                    zone.id(), zone.sizeX(), zone.sizeZ(), FlowZones.describe(zone.direction())), ChatFormatting.AQUA));
            level.playSound(null, player.blockPosition(), SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.5f, 1.2f);
            return;
        }

        final BlockHitResult hit = target(level, player);
        if (shift) {
            if (a != null) {
                clearCorners(stack);
                player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.cancelled"));
            } else if (hit.getType() == HitResult.Type.MISS) {
                setZoneMode(stack, false);
                player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_wand.mode.column"));
                chime(player, 0.9f);
            } else {
                final FlowZones.Zone removed = FlowZones.removeAt(level, hit.getBlockPos());
                if (removed != null) {
                    player.sendSystemMessage(prefixed(Component.translatable("message.dynamicwaters.flow_zone.removed",
                            removed.id()), ChatFormatting.GOLD));
                    level.playSound(null, hit.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6f, 1.0f);
                } else {
                    player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.none_here"));
                }
            }
            return;
        }

        if (hit.getType() == HitResult.Type.MISS) {
            player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.no_target",
                    (int) ZONE_REACH));
            return;
        }
        final BlockPos at = hit.getBlockPos();
        if (a == null) {
            setCorner(stack, level, false, at);
            player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.corner1",
                    at.getX(), at.getY(), at.getZ()));
            chime(player, 1.4f);
            return;
        }
        final int sizeX = Math.abs(at.getX() - a.getX()) + 1, sizeZ = Math.abs(at.getZ() - a.getZ()) + 1;
        if (sizeX > FlowZones.MAX_SIDE || sizeZ > FlowZones.MAX_SIDE) {
            player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.too_big",
                    FlowZones.MAX_SIDE));
            return;
        }
        setCorner(stack, level, true, at);
        player.sendOverlayMessage(Component.translatable("message.dynamicwaters.flow_zone.corner2", sizeX, sizeZ));
        chime(player, 1.7f);
    }

    public static BlockHitResult target(Level level, Player player) {
        final Vec3 eye = player.getEyePosition();
        return level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(ZONE_REACH)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
    }

    private void applyOverride(Level level, BlockPos clickedWaterPos, int direction, boolean enabled) {
        BlockPos surfacePos = clickedWaterPos;
        for (int i = 1; i <= SCAN_RADIUS * 2; i++) {
            BlockPos candidate = clickedWaterPos.above(i);
            if (isWater(level.getBlockState(candidate))) {
                surfacePos = candidate;
            } else {
                break;
            }
        }

        BlockPos overridePos = surfacePos.above();
        BlockState overrideState;
        if (enabled) {
            overrideState = ModBlocks.FLOW_OVERRIDE.get().defaultBlockState()
                    .setValue(FlowOverrideBlock.FLOW_DIRECTION, direction)
                    .setValue(FlowOverrideBlock.FLOW_ENABLED, true);
        } else {
            overrideState = ModBlocks.FLOW_OVERRIDE.get().defaultBlockState()
                    .setValue(FlowOverrideBlock.FLOW_DIRECTION, 0)
                    .setValue(FlowOverrideBlock.FLOW_ENABLED, false);
        }
        level.setBlock(overridePos, overrideState, 3);

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (int dy = 0; dy <= SCAN_RADIUS * 3; dy++) {
                BlockPos waterPos = new BlockPos(overridePos.getX(), overridePos.getY() - 1 - dy, overridePos.getZ());
                BlockState waterState = level.getBlockState(waterPos);
                if (!isWater(waterState))
                    break;

                serverLevel.sendBlockUpdated(waterPos, waterState, waterState, 3);
            }
        }
    }

    public static void removeOverrideColumn(Level level, BlockPos waterPos) {
        if (level.isClientSide())
            return;

        BlockPos scanPos = waterPos.above();
        for (int i = 0; i < SCAN_RADIUS + 2; i++) {
            BlockState state = level.getBlockState(scanPos);
            if (state.getBlock() instanceof FlowOverrideBlock) {
                level.setBlock(scanPos, Blocks.AIR.defaultBlockState(), 3);
                return;
            }

            if (!isWater(state))
                break;
            scanPos = scanPos.above();
        }
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    public static boolean isZoneMode(ItemStack stack) {
        return data(stack).contains(ZONE_MODE);
    }

    private static void setZoneMode(ItemStack stack, boolean zone) {
        update(stack, tag -> {
            if (zone)
                tag.putBoolean(ZONE_MODE, true);
            else
                tag.remove(ZONE_MODE);
            clearCornerTags(tag);
        });
    }

    public static BlockPos corner(ItemStack stack, Level level, boolean second) {
        final CompoundTag tag = data(stack);
        final int[] p = tag.getIntArray(second ? CORNER_B : CORNER_A).orElse(new int[0]);
        if (p.length != 3 || !level.dimension().identifier().toString().equals(tag.getStringOr(CORNER_DIM, "")))
            return null;
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static void setCorner(ItemStack stack, Level level, boolean second, BlockPos pos) {
        update(stack, tag -> {
            if (!second)
                tag.remove(CORNER_B);
            tag.putIntArray(second ? CORNER_B : CORNER_A, new int[] {pos.getX(), pos.getY(), pos.getZ()});
            tag.putString(CORNER_DIM, level.dimension().identifier().toString());
        });
    }

    private static void clearCorners(ItemStack stack) {
        update(stack, FlowWandItem::clearCornerTags);
    }

    private static void clearCornerTags(CompoundTag tag) {
        tag.remove(CORNER_A);
        tag.remove(CORNER_B);
        tag.remove(CORNER_DIM);
    }

    public static boolean isWater(BlockState state) {
        return state.getBlock() == Blocks.WATER
                || state.getFluidState().is(FluidTags.WATER);
    }

    private static Component prefixed(MutableComponent message, ChatFormatting colour) {
        return Component.literal("[").append(Component.translatable("item.dynamicwaters.flow_wand")).append("] ")
                .withStyle(colour).append(message.withStyle(ChatFormatting.GRAY));
    }

    private static void chime(Player player, float pitch) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp)
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_CHIME),
                    SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.6f, pitch, player.getRandom().nextLong()));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().append(Component.literal(" [")
                .append(Component.translatable(isZoneMode(stack) ? "item.dynamicwaters.flow_wand.mode.zone"
                        : "item.dynamicwaters.flow_wand.mode.column"))
                .append("]").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean allowComponentsUpdateAnimation(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        final String[] lines = isZoneMode(stack)
                ? new String[] {"zone1", "zone2", "zone3", "to_column"}
                : new String[] {"tooltip1", "tooltip2", "to_zone"};
        for (String line : lines)
            tooltip.accept(Component.translatable("item.dynamicwaters.flow_wand." + line).withStyle(ChatFormatting.GRAY));
    }
}
