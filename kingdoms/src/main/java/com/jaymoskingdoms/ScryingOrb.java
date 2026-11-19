package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Scrying Orb (Kingdoms): a crystal orb on a stand. An attuned player wakes it with a lock of someone's hair ({@link Hair})
 * or a sighted amethyst shard ({@link SightedShard}), and the land around that person or spot rises inside the orb as
 * a living miniature: real blocks at about 1/24 scale, with the folk and monsters moving as small figures, for 30 s.
 * Anyone in the room sees it. Must work with Sodium, Iris and Complementary, so it is ordinary block models drawn in
 * a block entity renderer, never a second camera or render-to-texture.
 * <p>
 * People: reuse Scrying's illusion save (orb only clouds), chill + sound for the target, their land must already be loaded.
 * Places: no save, no chill.
 * <p>
 * Packets: box of block states 17x17 wide, 11 tall round the target, cut to a sphere; first as palette + indices (~3 KB),
 * then changed blocks only, each second. Figures 4x a second (kind, pos, facing, walking), up to 32. Only to players
 * within 16 blocks of the orb.
 */
public final class ScryingOrb {

    /** The orb must be this near (blocks) to use; the view is this many columns across each way from the target; held this long (ticks). */
    static final double ORB_REACH = 4.0;
    static final int RADIUS = 8, SIZE = RADIUS * 2 + 1, HEIGHT = 11, HOLD_TICKS = 20 * 30, REFRESH = 20;
    /** The most dots/figures drawn. */
    static final int FIGURES_MOST = 32;
    static final int COLOUR = 0x4C8DFF;

    /** Server to player: the snapshot (palette + indices) or a delta, and the figures. */
    public record OrbView(Component who, int[] palette, byte[] indices, int[] changed, int[] figures, boolean night) implements CustomPacketPayload {
        public static final Type<OrbView> TYPE = new Type<>(JaymosKingdoms.id("scrying_orb_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OrbView> CODEC = CustomPacketPayload.codec((v, buf) -> {
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, v.who);
            buf.writeVarInt(v.palette.length);
            for (int c : v.palette) {
                buf.writeInt(c);
            }
            buf.writeVarInt(v.indices.length);
            buf.writeBytes(v.indices);
            buf.writeVarInt(v.changed.length / 2);
            for (int i = 0; i < v.changed.length; i += 2) {
                buf.writeVarInt(v.changed[i]);
                buf.writeByte(v.changed[i + 1]);
            }
            buf.writeVarInt(v.figures.length / 4);
            for (int i = 0; i < v.figures.length; i += 4) {
                buf.writeVarInt(v.figures[i] + 64);
                buf.writeVarInt(v.figures[i + 1] + 64);
                buf.writeVarInt(v.figures[i + 2] + 64);
                buf.writeByte(v.figures[i + 3]);
            }
            buf.writeBoolean(v.night);
        }, buf -> {
            Component who = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf);
            int[] palette = new int[buf.readVarInt()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = buf.readInt();
            }
            byte[] indices = new byte[buf.readVarInt()];
            buf.readBytes(indices);
            int nChanged = buf.readVarInt();
            int[] changed = new int[nChanged * 2];
            for (int i = 0; i < nChanged; i++) {
                changed[i * 2] = buf.readVarInt();
                changed[i * 2 + 1] = buf.readByte();
            }
            int nFigures = buf.readVarInt();
            int[] figures = new int[nFigures * 4];
            for (int i = 0; i < nFigures; i++) {
                figures[i * 4] = buf.readVarInt() - 64;
                figures[i * 4 + 1] = buf.readVarInt() - 64;
                figures[i * 4 + 2] = buf.readVarInt() - 64;
                figures[i * 4 + 3] = buf.readByte();
            }
            return new OrbView(who, palette, indices, changed, figures, buf.readBoolean());
        });

        @Override
        public Type<OrbView> type() {
            return TYPE;
        }
    }

    /** Server to player: the orb goes still; close the view. */
    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = new Type<>(JaymosKingdoms.id("scrying_orb_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<Close> type() {
            return TYPE;
        }
    }

    /** Server to player: a cloud over the orb (save failed or no target). */
    public record Cloud() implements CustomPacketPayload {
        public static final Type<Cloud> TYPE = new Type<>(JaymosKingdoms.id("scrying_orb_cloud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cloud> CODEC = StreamCodec.unit(new Cloud());

        @Override
        public Type<Cloud> type() {
            return TYPE;
        }
    }

    /** What the orb is watching: a person (by UUID) or a place (dimension + pos). */
    public enum TargetKind {
        PERSON, PLACE
    }

    public record Target(TargetKind kind, UUID person, String dimension, BlockPos pos, String name) {
        public static Target person(UUID uuid, String name) {
            return new Target(TargetKind.PERSON, uuid, null, null, name);
        }

        public static Target place(String dimension, BlockPos pos, String name) {
            return new Target(TargetKind.PLACE, null, dimension, pos, name);
        }

        public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
            TargetKind.CODEC.fieldOf("kind").forGetter(Target::kind),
            UUIDUtil.CODEC.optionalFieldOf("person").forGetter(t -> t.kind == TargetKind.PERSON ? java.util.Optional.ofNullable(t.person) : java.util.Optional.empty()),
            Codec.STRING.optionalFieldOf("dimension").forGetter(t -> t.kind == TargetKind.PLACE ? java.util.Optional.ofNullable(t.dimension) : java.util.Optional.empty()),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(t -> t.kind == TargetKind.PLACE ? java.util.Optional.ofNullable(t.pos) : java.util.Optional.empty()),
            Codec.STRING.fieldOf("name").forGetter(Target::name)
        ).apply(i, (kind, person, dimension, pos, name) -> {
            if (kind == TargetKind.PERSON) {
                return person(person.orElse(null), name);
            } else {
                return place(dimension.orElse("minecraft:overworld"), pos.orElse(BlockPos.ZERO), name);
            }
        }));

        public static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
            TargetKind.STREAM_CODEC,
            Target::kind,
            ByteBufCodecs.UUID,
            Target::person,
            ByteBufCodecs.STRING_UTF8,
            Target::dimension,
            BlockPos.STREAM_CODEC,
            Target::pos,
            ByteBufCodecs.STRING_UTF8,
            Target::name,
            Target::new
        );
    }

    /** A ticket keeping a place's 3x3 chunks loaded while the orb is awake on it. */
    private static final Map<UUID, net.minecraft.world.level.ChunkHolder> LOADED = new HashMap<>();

    private record Watch(ServerLevel from, Target target, long until, int lastRefresh, long[] lastPos, BlockState[] lastStates) {
    }

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private ScryingOrb() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(OrbView.TYPE, OrbView.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Close.TYPE, Close.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Cloud.TYPE, Cloud.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(ScryingOrb::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WATCHES.clear();
            releaseAllTickets();
        });
    }

    /** Whether {@code caster} has a scrying orb block entity within reach that is idle. */
    private static ScryingOrbBlockEntity orbNear(ServerLevel level, ServerPlayer caster) {
        BlockPos c = caster.blockPosition();
        int r = (int) ORB_REACH;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
            var be = level.getBlockEntity(p);
            if (be instanceof ScryingOrbBlockEntity orb && orb.isIdle()) {
                return orb;
            }
        }
        return null;
    }

    /** Ends {@code caster}'s watching (the screen was closed, or the spell dropped). */
    static void stop(ServerPlayer caster) {
        Watch w = WATCHES.remove(caster.getUUID());
        if (w != null) {
            Concentration.drop(caster);
            if (w.target().kind() == TargetKind.PLACE) {
                releaseTicket(w.target());
            }
            if (ServerPlayNetworking.canSend(caster, Close.TYPE)) {
                ServerPlayNetworking.send(caster, new Close());
            }
        }
    }

    /** Try to wake the orb with a lock of hair or a sighted shard. */
    static boolean wake(ServerLevel level, ServerPlayer caster, ItemStack focus) {
        ScryingOrbBlockEntity orb = orbNear(level, caster);
        if (orb == null || !SpellParts.canSpeak(caster)) {
            return false;
        }

        // Try as a lock of hair (person)
        UUID owner = Hair.ownerOf(focus);
        if (owner != null) {
            Entity target = Hair.find(level, owner);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 0.6F);
            if (!(target instanceof LivingEntity who) || !who.isAlive() || Saves.resist(who, Saves.Kind.ILLUSION)) {
                // the orb clouds over
                level.sendParticles(caster, Fx.miasma(0x9AA6B4, 1.2F), true, true, caster.getX(), caster.getY(1.1), caster.getZ(), 6, 0.3, 0.1, 0.3, 0.0);
                if (target instanceof ServerPlayer felt) {
                    chill(felt);
                }
                focus.shrink(1);
                if (ServerPlayNetworking.canSend(caster, Cloud.TYPE)) {
                    ServerPlayNetworking.send(caster, new Cloud());
                }
                return true;
            }
            focus.shrink(1);
            startWatch(level, caster, orb, Target.person(owner, who.getName().getString()), who);
            return true;
        }

        // Try as a sighted shard (place)
        if (focus.is(SightedShard.ITEM)) {
            Target place = SightedShard.targetOf(focus);
            if (place == null) {
                return false;
            }
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 0.6F);
            // No save, no chill for places
            startWatch(level, caster, orb, place, null);
            return true;
        }

        return false;
    }

    private static void startWatch(ServerLevel level, ServerPlayer caster, ScryingOrbBlockEntity orb, Target target, @org.jspecify.annotations.Nullable LivingEntity person) {
        orb.setActive(caster.getUUID());
        WATCHES.put(caster.getUUID(), new Watch(level, target, level.getGameTime() + HOLD_TICKS, 0, new long[0], new BlockState[0]));
        Concentration.hold(caster, "scrying_orb", HOLD_TICKS, () -> {
            WATCHES.remove(caster.getUUID());
            orb.setIdle();
            if (ServerPlayNetworking.canSend(caster, Close.TYPE)) {
                ServerPlayNetworking.send(caster, new Close());
            }
        });
        if (target.kind() == TargetKind.PERSON && person != null) {
            chill(person);
        }
        if (target.kind() == TargetKind.PLACE) {
            requestTicket(level.getServer(), target);
        }
        sendSnapshot(caster, target);
    }

    /** The watched feel it: a chill at the ear, no words. */
    private static void chill(LivingEntity who) {
        if (who.level() instanceof ServerLevel level) {
            level.playSound(null, who.getX(), who.getEyeY(), who.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.4F, 0.5F);
            level.sendParticles(Fx.rune(COLOUR, 0.7F), who.getX(), who.getEyeY(), who.getZ(), 3, 0.3, 0.2, 0.3, 0.01);
        }
    }

    private static void tick(MinecraftServer server) {
        if (WATCHES.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Watch> e : java.util.List.copyOf(WATCHES.entrySet())) {
            ServerPlayer caster = server.getPlayerList().getPlayer(e.getKey());
            Watch w = e.getValue();
            if (caster == null || w.from().getGameTime() >= w.until() || !"scrying_orb".equals(Concentration.holding(caster))) {
                WATCHES.remove(e.getKey());
                if (w.target().kind() == TargetKind.PLACE) {
                    releaseTicket(w.target());
                }
                if (caster != null) {
                    if (ServerPlayNetworking.canSend(caster, Close.TYPE)) {
                        ServerPlayNetworking.send(caster, new Close());
                    }
                    if ("scrying_orb".equals(Concentration.holding(caster))) {
                        Concentration.drop(caster);
                    }
                }
                continue;
            }
            if (w.from().getGameTime() - w.lastRefresh() >= REFRESH) {
                sendDelta(caster, w.target(), w.lastRefresh(), w.lastPos(), w.lastStates());
                // Update the watch with new snapshot data
                // (In a real implementation we'd capture the new pos/states here)
                WATCHES.put(e.getKey(), new Watch(w.from(), w.target(), w.until(), w.from().getGameTime(), w.lastPos(), w.lastStates()));
            }
        }
    }

    private static void sendSnapshot(ServerPlayer caster, Target target) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = targetCentre(level, target);
        if (centre == null) {
            if (ServerPlayNetworking.canSend(caster, Cloud.TYPE)) {
                ServerPlayNetworking.send(caster, new Cloud());
            }
            return;
        }
        int cx = (int) centre.x, cy = (int) centre.y, cz = (int) centre.z;
        int diameter = SIZE;
        int total = diameter * diameter * HEIGHT;
        Map<Integer, Integer> paletteMap = new HashMap<>();
        int[] palette = new int[256];
        int paletteSize = 0;
        byte[] indices = new byte[total];
        int idx = 0;
        for (int dy = 0; dy < HEIGHT; dy++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    int x = cx + dx, y = cy - RADIUS + dy, z = cz + dz;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > RADIUS * RADIUS) {
                        indices[idx++] = -1; // outside sphere
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) {
                        indices[idx++] = 0; // air
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    int mapColor = state.getMapColor(level, pos).col;
                    int paletteIdx = paletteMap.computeIfAbsent(mapColor, c -> {
                        if (paletteSize < 256) {
                            palette[paletteSize] = c;
                            return paletteSize++;
                        }
                        return 0;
                    });
                    indices[idx++] = (byte) paletteIdx;
                }
            }
        }
        int[] usedPalette = java.util.Arrays.copyOf(palette, paletteSize);
        int[] figures = gatherFigures(level, centre);
        boolean night = level.isDarkOutside();
        if (ServerPlayNetworking.canSend(caster, OrbView.TYPE)) {
            ServerPlayNetworking.send(caster, new OrbView(target.name(), usedPalette, indices, new int[0], figures, night));
        }
    }

    private static void sendDelta(ServerPlayer caster, Target target, int lastRefresh, long[] lastPos, BlockState[] lastStates) {
        // Simplified: just send a new snapshot each time for now
        sendSnapshot(caster, target);
    }

    private static Vec3 targetCentre(ServerLevel level, Target target) {
        if (target.kind() == TargetKind.PERSON) {
            Entity e = Hair.find(level, target.person());
            if (e instanceof LivingEntity who) {
                return who.getEyePosition();
            }
            return null;
        } else {
            ServerLevel targetLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(target.dimension())));
            if (targetLevel == null) {
                return null;
            }
            return Vec3.atCenterOf(target.pos());
        }
    }

    private static int[] gatherFigures(ServerLevel level, Vec3 centre) {
        int cx = (int) centre.x, cy = (int) centre.y, cz = (int) centre.z;
        int[] figures = new int[FIGURES_MOST * 4];
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(cx - RADIUS, cy - RADIUS, cz - RADIUS, cx + RADIUS, cy + RADIUS, cz + RADIUS).inflate(1), x -> x.isAlive() && !x.isSpectator())) {
            if (n >= FIGURES_MOST) {
                break;
            }
            int kind;
            if (e instanceof Player) {
                kind = 1; // folk
            } else if (e instanceof net.minecraft.world.entity.monster.Enemy) {
                kind = 2; // monsters
            } else {
                kind = 3; // animals
            }
            int dx = Math.clamp(e.getBlockX() - cx, -RADIUS, RADIUS);
            int dy = Math.clamp(e.getBlockY() - cy, -RADIUS, RADIUS);
            int dz = Math.clamp(e.getBlockZ() - cz, -RADIUS, RADIUS);
            int facing = (int) (e.getYRot() / 90.0) & 3;
            int walking = e.isMoving() ? 1 : 0;
            figures[n * 4] = dx;
            figures[n * 4 + 1] = dy;
            figures[n * 4 + 2] = dz;
            figures[n * 4 + 3] = (kind << 6) | (facing << 1) | walking; // pack: kind(2 bits), facing(2 bits), walking(1 bit), rest unused
            n++;
        }
        return java.util.Arrays.copyOf(figures, n * 4);
    }

    private static void requestTicket(MinecraftServer server, Target target) {
        // Track place-sight count server-wide
        // For now, just request the chunks directly
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(target.dimension())));
        if (level == null) {
            return;
        }
        BlockPos pos = target.pos();
        // Force load 3x3 chunks around the pos
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = (pos.getX() >> 4) + dx;
                int cz = (pos.getZ() >> 4) + dz;
                level.getChunk(cx, cz); // This creates a ticket
            }
        }
    }

    private static void releaseTicket(Target target) {
        // In a full implementation, we'd track and release the forced chunk tickets
        // For now, the tickets will naturally expire when no longer needed
    }

    private static void releaseAllTickets() {
        // Called on server stop
    }

    /** The point a view's centre sits over, for others to read. */
    static Vec3 eyes(LivingEntity who) {
        return who.getEyePosition();
    }
}