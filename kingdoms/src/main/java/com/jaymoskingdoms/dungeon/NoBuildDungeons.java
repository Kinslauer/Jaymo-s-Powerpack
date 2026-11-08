package com.jaymoskingdoms.dungeon;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.jaymoskingdoms.JaymosKingdoms;

/**
 * (Andy, 2026-11-08) DungeonZ-style rule for Kingdoms, our own version of dungeonz's approach (MIT; approach and code
 * may be reused with credit). Structures in the {@link #NO_BUILD_DUNGEONS} tag (the Stormborn tombs once their ids are
 * added to it by the stormborn-tomb-structures task) are dungeons you play, not tunnel: breaking and placing most blocks
 * inside them is forbidden, interacting still works (doors, chests, levers, buttons, pressure plates), and a short
 * action-bar line explains why. Creative mode and operators are never blocked. Which blocks stay breakable (cobwebs,
 * pots) is the block tag {@link #BREAKABLE_IN_DUNGEON}, kept as a constant set for tuning.
 */
public final class NoBuildDungeons {
    /** Structures in this tag are no-build dungeons; empty until stormborn-tomb-structures lands its ids. */
    public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> NO_BUILD_DUNGEONS =
            TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("no_build_dungeons"));
    /** Blocks that stay breakable inside a no-build dungeon. */
    public static final TagKey<net.minecraft.world.level.block.Block> BREAKABLE_IN_DUNGEON =
            TagKey.create(Registries.BLOCK, JaymosKingdoms.id("no_build_dungeons_breakable"));

    private NoBuildDungeons() {
    }

    /** Whether {@code pos} is inside a piece of a structure in the no-build tag. */
    public static boolean is(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, NO_BUILD_DUNGEONS).isValid();
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) {
                return true;
            }
            if (!(level instanceof ServerLevel server)) {
                return true;
            }
            if (state.isAir() || state.is(BREAKABLE_IN_DUNGEON)) {
                return true;
            }
            if (!is(server, pos)) {
                return true;
            }
            sp.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.jaymoskingdoms.no_build_dungeons")));
            return false;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp) || sp.isCreative() || sp.isSpectator()) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem)) {
                return InteractionResult.PASS;
            }
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (!is(server, pos)) {
                return InteractionResult.PASS;
            }
            sp.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.jaymoskingdoms.no_build_dungeons")));
            return InteractionResult.FAIL;
        });
    }
}
