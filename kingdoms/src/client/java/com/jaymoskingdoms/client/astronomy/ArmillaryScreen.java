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
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The armillary sphere's screen: it reads off the sky - the stars the player
 * has already discovered that are overhead, the world time and the player's
 * place - and asks the server to take a reading, which is written in the
 * player's Astronomer's Journal ({@link com.jaymoskingdoms.astronomy.Measurements}).
 * The server's answer shows as one line: what was written, or why it failed.
 */
public final class ArmillaryScreen extends Screen {
    private final BlockPos pos;
    /** How the last reading went, from the server, or null before one is asked for. */
    private @Nullable Component result;

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

    /** Ask the server to take a reading here; its answer comes back to {@link #reading}. */
    private void record() {
        ClientPlayNetworking.send(new ArmillaryNet.Measure(pos));
    }

    /** The server's answer to a reading. */
    void reading(ArmillaryNet.Reading reading) {
        this.result = Component.translatable("gui.jaymoskingdoms.armillary.reading." + reading.outcome(), reading.count(), reading.xp());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.fill(cx - 110, cy - 92, cx + 110, cy + 64, 0xFF2B211A);
        graphics.centeredText(minecraft.font, this.title.getString(), cx, cy - 80, 0xFFD4C8A8);
        int y = cy - 62;
        if (level != null && player != null) {
            float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float night = SkyMath.night(level, partial);
            long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
            graphics.text(minecraft.font, "Position: " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ(), cx - 100, y, 0xFFE0E0C0);
            y += 11;
            graphics.text(minecraft.font, String.format("World time: %02d:%02d", (time / 1000 + 6) % 24, (time % 1000) * 60 / 1000), cx - 100, y, 0xFFE0E0C0);
            y += 11;
            graphics.text(minecraft.font, "Day: " + (level.getGameTime() / 24000L + 1), cx - 100, y, 0xFFE0E0C0);
            y += 11;
            graphics.text(minecraft.font, String.format("Sky darkness: %.0f%%", night * 100.0F), cx - 100, y, 0xFFE0E0C0);
            y += 14;
            Discovery discovery = Discovery.getOrCreate(minecraft);
            int overhead = 0;
            List<Integer> listed = new ArrayList<>();
            float p2 = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
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
            graphics.text(minecraft.font, "Discovered stars overhead: " + overhead, cx - 100, y, 0xFFE0E0C0);
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
                graphics.text(minecraft.font, names.toString(), cx - 100, y, 0xFFB0A890);
                y += 11;
            }
            if (result != null) {
                for (net.minecraft.util.FormattedCharSequence line : minecraft.font.split(result, 200)) {
                    graphics.text(minecraft.font, line, cx - 100, y, 0xFFD0AB5A);
                    y += 10;
                }
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
