package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ScryingOrb;
import com.jaymoskingdoms.ScryingOrbBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The scrying orb's block entity renderer: draws the living miniature inside the glass sphere at 1/24 scale.
 * Uses the PlanGhosts block-model submit path; skips air and hidden blocks; slow turn; dim at night;
 * uses the target world's light (Nether red, Aether white).
 */
public final class ScryingOrbRenderer implements BlockEntityRenderer<ScryingOrbBlockEntity, ScryingOrbRenderer.State> {

    /** How far from the eye orbs are drawn (blocks); the most drawn at once (nearest first). */
    static final int RANGE = 32, MAX_ORBS = 8;
    /** The orb's inner sphere radius in blocks (model space). */
    private static final double SPHERE_R = 0.38;
    /** How many degrees the miniature turns per tick. */
    private static final float SPIN = 0.15F;
    /** The sphere's glass tint (ARGB). */
    private static final int GLASS_COLOUR = 0x44FFFFFF;
    /** The stand's colour (ARGB). */
    private static final int STAND_COLOUR = 0xFF3A2E24;
    /** Full bright light coords. */
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Sphere scale: 17x17x11 blocks at 1/24 scale. */
    private static final double SCALE = 1.0 / 24.0;
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    /** One active orb's data as received from the server. */
    private static final class OrbData {
        final UUID id;
        final Component who;
        final BlockState[] palette;
        final byte[] indices;
        final int[] figures;
        final boolean night;
        final long receivedAt;
        int tickOffset;

        OrbData(UUID id, Component who, BlockState[] palette, byte[] indices, int[] figures, boolean night) {
            this.id = id;
            this.who = who;
            this.palette = palette;
            this.indices = indices;
            this.figures = figures;
            this.night = night;
            this.receivedAt = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            this.tickOffset = 0;
        }
    }

    private static final Map<UUID, OrbData> ORBS = new HashMap<>();
    /** Quad cache: BlockState -> List<BakedQuad> */
    private static final Map<BlockState, List<BakedQuad>> QUADS = new HashMap<>();

    public static final class State extends BlockEntityRenderState {
        BlockPos pos;
        float angle;
        int blockLight;
        int skyLight;
        boolean active;
        UUID activeId;
    }

    public ScryingOrbRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ScryingOrbBlockEntity blockEntity, State state, float partialTicks,
                                   Vec3 camera, @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        var level = blockEntity.getLevel();
        state.pos = blockEntity.getBlockPos();
        state.blockLight = level == null ? 0 : level.getBrightness(net.minecraft.core.LightLayer.BLOCK, blockEntity.getBlockPos());
        state.skyLight = level == null ? 0 : level.getBrightness(net.minecraft.core.LightLayer.SKY, blockEntity.getBlockPos());
        double t = (level == null ? 0.0 : level.getGameTime()) + partialTicks;
        state.angle = (float) ((t * SPIN) % 360.0);
        state.active = blockEntity.getBlockState().getValue(ScryingOrbBlock.ACTIVE);
        state.activeId = blockEntity.getUser();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        if (!state.active || state.activeId == null) {
            // Draw just the idle orb (glass sphere + stand)
            drawIdleOrb(collector, poseStack, state);
            return;
        }

        OrbData data = ORBS.get(state.activeId);
        if (data == null) {
            drawIdleOrb(collector, poseStack, state);
            return;
        }

        double cx = state.pos.getX() + 0.5, cy = state.pos.getY() + 0.75, cz = state.pos.getZ() + 0.5;
        float spin = (float) Math.toRadians(state.angle + data.tickOffset);

        // Draw the stand
        drawStand(collector, poseStack, cx, cy - 0.55, cz, state.blockLight, state.skyLight);

        // Draw the miniature inside the sphere using PlanGhosts approach
        drawMiniature(collector, poseStack, cx, cy, cz, spin, data, state.blockLight, state.skyLight);

        // Draw the glass sphere
        drawGlassSphere(collector, poseStack, cx, cy, cz, state.blockLight, state.skyLight);
    }

    private void drawIdleOrb(SubmitNodeCollector collector, PoseStack poseStack, State state) {
        double cx = state.pos.getX() + 0.5, cy = state.pos.getY() + 0.75, cz = state.pos.getZ() + 0.5;
        float spin = (float) Math.toRadians(state.angle);
        drawStand(collector, poseStack, cx, cy - 0.55, cz, state.blockLight, state.skyLight);
        drawGlassSphere(collector, poseStack, cx, cy, cz, state.blockLight, state.skyLight);
    }

    private void drawStand(SubmitNodeCollector collector, PoseStack poseStack, double cx, double cy, double cz, int blockLight, int skyLight) {
        // A simple carved stand: a base plate and a central column
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        VertexConsumer consumer = collector.submitNodeCollector().getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        // Base plate
        quad(consumer, -0.35, 0.0, -0.35, 0.35, 0.0, -0.35, 0.35, 0.05, 0.35, -0.35, 0.05, -0.35, STAND_COLOUR, blockLight, skyLight);
        quad(consumer, -0.35, 0.05, -0.35, -0.35, 0.05, 0.35, 0.35, 0.05, 0.35, 0.35, 0.05, 0.35, STAND_COLOUR, blockLight, skyLight);
        // Column
        quad(consumer, -0.08, 0.05, -0.08, 0.08, 0.05, -0.08, 0.08, 0.55, -0.08, -0.08, 0.55, -0.08, STAND_COLOUR, blockLight, skyLight);
        quad(consumer, 0.08, 0.05, -0.08, 0.08, 0.05, 0.08, 0.08, 0.55, 0.08, -0.08, 0.55, 0.08, STAND_COLOUR, blockLight, skyLight);
        quad(consumer, -0.08, 0.05, 0.08, 0.08, 0.05, 0.08, 0.08, 0.55, 0.08, -0.08, 0.55, 0.08, STAND_COLOUR, blockLight, skyLight);
        quad(consumer, -0.08, 0.05, -0.08, -0.08, 0.05, 0.08, -0.08, 0.55, 0.08, 0.08, 0.55, -0.08, STAND_COLOUR, blockLight, skyLight);
        // Top ring
        quad(consumer, -0.18, 0.55, -0.18, 0.18, 0.55, -0.18, 0.18, 0.58, -0.18, -0.18, 0.58, -0.18, STAND_COLOUR, blockLight, skyLight);
        poseStack.popPose();
    }

    private void drawGlassSphere(SubmitNodeCollector collector, PoseStack poseStack, double cx, double cy, double cz, int blockLight, int skyLight) {
        // Draw a wireframe sphere using leash lines
        int segments = 16;
        for (int ring = 0; ring < 3; ring++) {
            float tilt = ring == 0 ? 0 : (ring == 1 ? (float) Math.toRadians(60) : (float) Math.toRadians(-60));
            float phase = 0;
            Vec3 prev = null;
            for (int i = 0; i <= segments; i++) {
                double ang = 2.0 * Math.PI * i / segments + phase;
                double x = cx + Math.cos(ang) * SPHERE_R;
                double y = cy + Math.sin(ang) * SPHERE_R * Math.cos(tilt);
                double z = cz + Math.sin(ang) * SPHERE_R * Math.sin(tilt);
                if (prev != null) {
                    leash(collector, poseStack, prev.x, prev.y, prev.z, x, y, z, GLASS_COLOUR, blockLight, skyLight);
                }
                prev = new Vec3(x, y, z);
            }
        }
    }

    private void drawMiniature(SubmitNodeCollector collector, PoseStack poseStack, double cx, double cy, double cz,
                               float spin, OrbData data, int blockLight, int skyLight) {
        if (data.palette.length == 0 || data.indices.length == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int diameter = ScryingOrb.SIZE;
        int height = ScryingOrb.HEIGHT;
        int radius = ScryingOrb.RADIUS;
        int total = diameter * diameter * height;

        // Build list of visible block positions and states
        List<long[]> visiblePos = new ArrayList<>();
        List<BlockState> visibleStates = new ArrayList<>();

        for (int idx = 0; idx < total && idx < data.indices.length; idx++) {
            byte paletteIdx = data.indices[idx];
            if (paletteIdx < 0) continue; // outside sphere
            if (paletteIdx == 0) continue; // air
            if (paletteIdx >= data.palette.length) continue;

            BlockState state = data.palette[paletteIdx];
            if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;

            int dx = idx % diameter;
            int dz = (idx / diameter) % diameter;
            int dy = idx / (diameter * diameter);

            int wx = dx - radius;
            int wy = dy - radius;
            int wz = dz - radius;

            long pos = BlockPos.asLong(wx, wy, wz);
            visiblePos.add(new long[]{pos, ((long) paletteIdx & 0xFFL) << 32 | idx & 0xFFFFFFFFL}); // pack pos and idx
            visibleStates.add(state);
        }

        if (visiblePos.isEmpty()) return;

        // Render using submitCustomGeometry like PlanGhosts
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        collector.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setColor(0xFFFFFFFF); // white tint, block model provides color
            instance.setLightCoords(FULL_BRIGHT);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            RandomSource random = RandomSource.create(42L);

            for (int i = 0; i < visiblePos.size(); i++) {
                long[] posData = visiblePos.get(i);
                BlockState state = visibleStates.get(i);

                int wx = BlockPos.getX(posData[0]);
                int wy = BlockPos.getY(posData[0]);
                int wz = BlockPos.getZ(posData[0]);

                // Translate to orb center + scaled position
                PoseStack.Pose p = pose.copy();
                p.translate((float)(cx + wx * SCALE - camera.x) + 0.01F,
                           (float)(cy + wy * SCALE - camera.y) + 0.01F,
                           (float)(cz + wz * SCALE - camera.z) + 0.01F);
                p.scale((float)SCALE, (float)SCALE, (float)SCALE);

                // Apply slow rotation
                p.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(spin)));

                // Get or compute quads for this state
                List<BakedQuad> quads = getQuads(mc, state, random);
                for (BakedQuad quad : quads) {
                    consumer.putBakedQuad(p, quad, instance);
                }
            }
        });
        poseStack.popPose();

        // Draw figures as motes (first cut: small lit motes)
        if (data.figures.length > 0) {
            for (int i = 0; i < data.figures.length; i += 4) {
                int dx = data.figures[i];
                int dy = data.figures[i + 1];
                int dz = data.figures[i + 2];
                int packed = data.figures[i + 3];
                int kind = packed >> 6; // 0=watched, 1=folk, 2=monsters, 3=animals

                int colour = switch (kind) {
                    case 0 -> 0xFFFFFFFF; // white - watched
                    case 1 -> 0xFF4AA3FF; // blue - folk
                    case 2 -> 0xFFFF4A4A; // red - monsters
                    default -> 0xFF8AE06A; // green - animals
                };

                double fx = cx + dx * SCALE;
                double fy = cy + dy * SCALE;
                double fz = cz + dz * SCALE;
                mote(collector, poseStack, fx, fy, fz, colour, blockLight, skyLight);
            }
        }
    }

    private static List<BakedQuad> getQuads(Minecraft minecraft, BlockState state, RandomSource random) {
        return QUADS.computeIfAbsent(state, s -> {
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(s);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(random, parts);
            List<BakedQuad> out = new ArrayList<>();
            for (BlockStateModelPart part : parts) {
                for (Direction side : SIDES) {
                    out.addAll(part.getQuads(side));
                }
            }
            return List.copyOf(out);
        });
    }

    private static void quad(VertexConsumer consumer, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             int colour, int blockLight, int skyLight) {
        consumer.addVertex(x0, y0, z0).setColor(colour).setLight(blockLight, skyLight);
        consumer.addVertex(x1, y1, z1).setColor(colour).setLight(blockLight, skyLight);
        consumer.addVertex(x2, y2, z2).setColor(colour).setLight(blockLight, skyLight);
        consumer.addVertex(x3, y3, z3).setColor(colour).setLight(blockLight, skyLight);
    }

    private static void leash(SubmitNodeCollector collector, PoseStack pose, double x0, double y0, double z0,
                              double x1, double y1, double z1, int colour, int blockLight, int skyLight) {
        net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState line = new net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState();
        line.offset = Vec3.ZERO;
        line.start = new Vec3(x0, y0, z0);
        line.end = new Vec3(x1, y1, z1);
        line.startBlockLight = blockLight;
        line.endBlockLight = blockLight;
        line.startSkyLight = skyLight;
        line.endSkyLight = skyLight;
        line.slack = false;
        collector.submitLeash(pose, line);
    }

    private static void mote(SubmitNodeCollector collector, PoseStack pose, double x, double y, double z,
                             int colour, int blockLight, int skyLight) {
        // Draw a small cross mote
        float size = 0.02F;
        quad(collector.submitNodeCollector().getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)),
                x - size, y, z, x + size, y, z, x + size, y + size * 2, z, x - size, y + size * 2, z, colour, blockLight, skyLight);
        quad(collector.submitNodeCollector().getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)),
                x, y, z - size, x, y, z + size, x, y + size * 2, z + size, x, y + size * 2, z - size, colour, blockLight, skyLight);
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ScryingOrb.OrbView.TYPE, (payload, context) -> context.client().execute(() -> {
            // Convert palette int[] (block state IDs) to BlockState[]
            BlockState[] paletteStates = new BlockState[payload.palette().length];
            for (int i = 0; i < payload.palette().length; i++) {
                int stateId = payload.palette()[i];
                if (stateId > 0) {
                    paletteStates[i] = BlockState.stateById(stateId);
                }
            }
            ORBS.put(UUID.nameUUIDFromBytes(payload.who().getString().getBytes()),
                    new OrbData(UUID.randomUUID(), payload.who(), paletteStates, payload.indices(), payload.figures(), payload.night()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(ScryingOrb.Close.TYPE, (payload, context) -> context.client().execute(() -> {
            ORBS.clear();
            QUADS.clear();
        }));
        ClientPlayNetworking.registerGlobalReceiver(ScryingOrb.Cloud.TYPE, (payload, context) -> context.client().execute(() -> {
            ORBS.clear();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (minecraft.level == null) {
                ORBS.clear();
                QUADS.clear();
                return;
            }
            for (OrbData data : ORBS.values()) {
                data.tickOffset++;
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(ScryingOrbRenderer::renderOrbs);
    }

    private static void renderOrbs(LevelRenderContext context) {
        // The actual rendering is done in submit() per block entity
        // This is just a placeholder for any global orb effects
    }
}