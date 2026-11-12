package com.AdamNew.dynamicwaters.client.gui.shore;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BlockSwatch {
    private record Face(TextureAtlasSprite sprite, int tint) {
    }

    private static final Map<String, List<Face>> FACES = new HashMap<>();
    private static List<Block> dressable;
    private static List<Block> plantable;

    private static final String[] WATER_PLANTS = {
            "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:sea_pickle",
            "minecraft:lily_pad", "minecraft:sugar_cane", "minecraft:frogspawn",
            "minecraft:small_dripleaf", "minecraft:big_dripleaf",
            "minecraft:tube_coral", "minecraft:brain_coral", "minecraft:bubble_coral", "minecraft:fire_coral", "minecraft:horn_coral",
            "minecraft:tube_coral_fan", "minecraft:brain_coral_fan", "minecraft:bubble_coral_fan", "minecraft:fire_coral_fan",
            "minecraft:horn_coral_fan",
    };

    private BlockSwatch() {
    }

    static Block block(String id) {
        if (RiverbankPaletteDraft.KEEP.equals(id)) return null;
        final Identifier rl = Identifier.tryParse(id);
        if (rl == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
    }

    static boolean exists(String id) {
        return RiverbankPaletteDraft.KEEP.equals(id) || block(id) != null;
    }

    static boolean loose(String id) {
        return block(id) instanceof FallingBlock;
    }

    static Component name(String id) {
        if (RiverbankPaletteDraft.KEEP.equals(id)) return Component.translatable("dynamicwaters.gui.banks.keep");
        final Block b = block(id);
        return b == null ? Component.literal(id).withStyle(ChatFormatting.RED) : b.getName();
    }

    static void draw(GuiGraphicsExtractor g, String id, int x, int y, int size) {
        final List<Face> faces = faces(id);
        if (faces.isEmpty()) {
            g.fill(x, y, x + size, y + size, 0xFFB000B0);
            return;
        }
        for (Face f : faces) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, f.sprite(), x, y, size, size,
                    f.tint() == -1 ? 0xFFFFFFFF : 0xFF000000 | (f.tint() & 0xFFFFFF));
        }
    }

    private static List<Face> faces(String id) {
        final List<Face> cached = FACES.get(id);
        if (cached != null) return cached;
        final List<Face> out = new ArrayList<>();
        final Block b = block(RiverbankPaletteDraft.KEEP.equals(id) ? "minecraft:grass_block" : id);
        if (b != null) {
            try {
                final BlockState state = b.defaultBlockState();
                final Minecraft mc = Minecraft.getInstance();
                final BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
                final List<BlockStateModelPart> parts = new ArrayList<>();
                model.collectParts(RandomSource.create(42L), parts);
                List<BakedQuad> quads = new ArrayList<>();
                for (BlockStateModelPart part : parts) quads.addAll(part.getQuads(Direction.NORTH));
                if (quads.isEmpty()) for (BlockStateModelPart part : parts) quads.addAll(part.getQuads(null));
                for (BakedQuad q : quads) {
                    final BakedQuad.MaterialInfo info = q.materialInfo();
                    final BlockTintSource source = info.isTinted() ? mc.getBlockColors().getTintSource(state, info.tintIndex()) : null;
                    out.add(new Face(info.sprite(), source != null ? source.color(state) : -1));
                    if (out.size() >= 3) break;
                }
                if (out.isEmpty()) out.add(new Face(model.particleMaterial().sprite(), -1));
            } catch (Throwable t) {
                out.clear();
            }
        }
        FACES.put(id, out);
        return out;
    }

    static List<Block> dressable() {
        if (dressable != null) return dressable;
        final List<Block> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            final Item item = b.asItem();
            if (item == Items.AIR || item instanceof GameMasterBlockItem) continue;
            final BlockState s = b.defaultBlockState();
            try {
                if (s.hasBlockEntity() || !s.isSolidRender()) continue;
            } catch (Throwable t) {
                continue;
            }
            out.add(b);
        }
        out.sort(Comparator.comparing(b -> b.getName().getString().toLowerCase()));
        dressable = out;
        return out;
    }

    static List<Block> plantable() {
        if (plantable != null) return plantable;
        final List<Block> out = new ArrayList<>();
        for (String id : WATER_PLANTS) {
            final Block b = block(id);
            if (b != null) out.add(b);
        }
        final List<Block> rest = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (out.contains(b)) continue;
            final Item item = b.asItem();
            if (item == Items.AIR || item instanceof GameMasterBlockItem) continue;
            try {
                if (b.defaultBlockState().hasBlockEntity()) continue;
            } catch (Throwable t) {
                continue;
            }
            rest.add(b);
        }
        rest.sort(Comparator.comparing(b -> b.getName().getString().toLowerCase()));
        out.addAll(rest);
        plantable = out;
        return out;
    }

    static String idOf(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).toString();
    }
}
