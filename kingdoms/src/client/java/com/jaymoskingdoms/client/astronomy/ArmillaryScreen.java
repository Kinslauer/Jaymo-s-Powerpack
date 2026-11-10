package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.astronomy.ArmillaryNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The armillary sphere's screen: it reads off the sky - the stars the player
 * has already discovered that are overhead, the world time and the player's
 * place - and records the measurement on the block entity.
 */
public final class ArmillaryScreen extends Screen {
    private final BlockPos pos;
    /** The last measurement the client recorded, as JSON. */
    private String measurement = "";

    public ArmillaryScreen(BlockPos pos) {
        super(Component.translatable("gui.jaymoskingdoms.armillary.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int cx = this.width / 2, cy = this.height / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.armillary.record"),
                button -> this.record()).bounds(cx - 106, cy + 52, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.armillary.done"),
                button -> Minecraft.getInstance().gui.setScreen(null)).bounds(cx + 6, cy + 52, 100, 20).build());
    }

    /** Read off the sky now and send the measurement to be recorded. */
    private void record() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Discovery discovery = Discovery.getOrCreate(minecraft);
        long time = Math.floorMod(level.getGameTime(), 24000L);
        long day = level.getGameTime() / 24000L;
        double lonDeg = player.getX() * SkyMath.LON_DEG_PER_BLOCK;
        int days = (int) (level.getGameTime() / 24000L);
        List<Integer> overhead = new ArrayList<>();
        for (Star star : StarCatalog.stars()) {
            if (discovery == null || !discovery.isDiscovered(star.hip())) {
                continue;
            }
            Vec3 dir = SkyMath.direction(star, time, partial, lonDeg, days);
            if (dir.y > 0.0) {
                overhead.add(star.hip());
            }
        }
        StringBuilder json = new StringBuilder("{\"day\":");
        json.append(day).append(",\"time\":").append(time);
        json.append(",\"x\":").append(player.getBlockX()).append(",\"y\":").append(player.getBlockY());
        json.append(",\"z\":").append(player.getBlockZ()).append(",\"stars\":[");
        for (int i = 0; i < overhead.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(overhead.get(i));
        }
        json.append("]}");
        String record = json.toString();
        ClientPlayNetworking.send(new ArmillaryNet.Measure(pos, record));
        this.measurement = record;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.fill(cx - 110, cy - 92, cx + 110, cy + 64, 0xFF2B211A);
        graphics.centeredText(minecraft.font, this.title.getString(), cx, cy - 80, 0xD4C8A8);
        int y = cy - 62;
        if (level != null && player != null) {
            float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float night = SkyMath.night(level, partial);
            long time = Math.floorMod(level.getGameTime(), 24000L);
            graphics.text(minecraft.font, "Position: " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ(), cx - 100, y, 0xE0E0C0);
            y += 11;
            graphics.text(minecraft.font, String.format("World time: %02d:%02d", (time / 1000) % 24, (time % 1000) * 60 / 1000), cx - 100, y, 0xE0E0C0);
            y += 11;
            graphics.text(minecraft.font, "Day: " + (level.getGameTime() / 24000L + 1), cx - 100, y, 0xE0E0C0);
            y += 11;
            graphics.text(minecraft.font, String.format("Sky darkness: %.0f%%", night * 100.0F), cx - 100, y, 0xE0E0C0);
            y += 14;
            Discovery discovery = Discovery.getOrCreate(minecraft);
            int overhead = 0;
            List<Integer> listed = new ArrayList<>();
            float p2 = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            long t = Math.floorMod(level.getGameTime(), 24000L);
            double lon = player.getX() * SkyMath.LON_DEG_PER_BLOCK;
            int d = (int) (level.getGameTime() / 24000L);
            for (Star star : StarCatalog.stars()) {
                if (discovery == null || !discovery.isDiscovered(star.hip())) {
                    continue;
                }
                Vec3 dir = SkyMath.direction(star, t, p2, lon, d);
                if (dir.y > 0.0) {
                    overhead++;
                    if (listed.size() < 5) {
                        listed.add(star.hip());
                    }
                }
            }
            graphics.text(minecraft.font, "Discovered stars overhead: " + overhead, cx - 100, y, 0xE0E0C0);
            y += 11;
            if (!listed.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < listed.size(); i++) {
                    if (i > 0) {
                        names.append(',');
                    }
                    Star star = StarCatalog.byHip(listed.get(i));
                    names.append(star.name() == null ? "Hipparcos " + listed.get(i) : star.name());
                }
                if (overhead > listed.size()) {
                    names.append(" ...").append(overhead - listed.size());
                }
                graphics.text(minecraft.font, names.toString(), cx - 100, y, 0xB0A890);
                y += 11;
            }
            if (measurement != null && !measurement.isEmpty()) {
                graphics.text(minecraft.font, "Last record: " + measurement, cx - 100, y, 0x8D7351);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
