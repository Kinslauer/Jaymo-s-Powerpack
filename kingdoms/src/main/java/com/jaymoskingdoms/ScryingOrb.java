package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Locale;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
 * Packets: box of block states 17x17 wide, 11 tall round the target, cut to a sphere, as palette + indices (~3 KB),
 * sent whole once a second with the figures (kind, pos, facing, walking), up to 32. A place's 3x3 chunks are kept
 * loaded while it's watched by a portal ticket renewed each second, which lapses by itself when the watching ends.
 */
public final class ScryingOrb {

    /** The orb must be this near (blocks) to use; the view is this many columns across each way from the target; held this long (ticks). */
    static final double ORB_REACH = 4.0;
    public static final int RADIUS = 8, SIZE = RADIUS * 2 + 1, HEIGHT = 11;
    static final int HOLD_TICKS = 20 * 30, REFRESH = 20;
    /** The most dots/figures drawn. */
    static final int FIGURES_MOST = 32;
    /** How near an orb (blocks) its vision is seen. */
    static final double VIEWERS = 16.0;
    static final int COLOUR = 0x4C8DFF;

    /** Server to the players by an orb: what the orb at {@code orb} shows (palette + indices, and the figures). */
    public record OrbView(BlockPos orb, Component who, int[] palette, byte[] indices, int[] changed, int[] figures, boolean night) implements CustomPacketPayload {
        public static final Type<OrbView> TYPE = new Type<>(JaymosKingdoms.id("scrying_orb_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OrbView> CODEC = CustomPacketPayload.codec((v, buf) -> {
            BlockPos.STREAM_CODEC.encode(buf, v.orb);
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
            BlockPos orb = BlockPos.STREAM_CODEC.decode(buf);
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
            return new OrbView(orb, who, palette, indices, changed, figures, buf.readBoolean());
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
        PERSON, PLACE;

        public static final Codec<TargetKind> CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return com.mojang.serialization.DataResult.success(TargetKind.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return com.mojang.serialization.DataResult.error(() -> "Not a TargetKind: " + s);
                }
            },
            Enum::name
        );
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
    }

    /** A caster's watching: from where (the world and the orb), of what, until when, and when the view was last sent (game time). */
    private record Watch(ServerLevel from, BlockPos orb, Target target, long until, long lastRefresh) {
    }

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private ScryingOrb() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(OrbView.TYPE, OrbView.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Close.TYPE, Close.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Cloud.TYPE, Cloud.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(ScryingOrb::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WATCHES.clear());
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
            idle(w);
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
        WATCHES.put(caster.getUUID(), new Watch(level, orb.getBlockPos(), target, level.getGameTime() + HOLD_TICKS, level.getGameTime()));
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
        keepLoaded(level.getServer(), target);
        sendSnapshot(caster, orb.getBlockPos(), target);
    }

    /** The orb {@code w} was watched in goes still again, for anyone to use. */
    private static void idle(Watch w) {
        if (w.from().getBlockEntity(w.orb()) instanceof ScryingOrbBlockEntity orb) {
            orb.setIdle();
        }
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
                idle(w);
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
                keepLoaded(server, w.target());
                sendSnapshot(caster, w.orb(), w.target());
                WATCHES.put(e.getKey(), new Watch(w.from(), w.orb(), w.target(), w.until(), w.from().getGameTime()));
            }
        }
    }

    /**
     * The view of {@code target} as it is now, to everyone within {@link #VIEWERS} of the orb at {@code orb}: the blocks of a sphere round it (palette of block state
     * ids, slot 0 always air, as the client reads them; an index of -1 is outside the sphere) and the figures in it. A
     * target that can't be found (gone, or its land not loaded) clouds the orb.
     */
    private static void sendSnapshot(ServerPlayer caster, BlockPos orb, Target target) {
        if (!(caster.level() instanceof ServerLevel from)) {
            return;
        }
        ServerLevel level = targetLevel(from, target);
        Vec3 centre = level == null ? null : targetCentre(level, target);
        if (centre == null) {
            if (ServerPlayNetworking.canSend(caster, Cloud.TYPE)) {
                ServerPlayNetworking.send(caster, new Cloud());
            }
            return;
        }
        int cx = Mth.floor(centre.x), cy = Mth.floor(centre.y), cz = Mth.floor(centre.z);
        Map<Integer, Integer> slots = new HashMap<>();
        java.util.List<Integer> palette = new java.util.ArrayList<>();
        palette.add(Block.getId(Blocks.AIR.defaultBlockState()));
        slots.put(palette.getFirst(), 0);
        byte[] indices = new byte[SIZE * SIZE * HEIGHT];
        int idx = 0;
        for (int dy = 0; dy < HEIGHT; dy++) {
            // the sphere is cut round the centre: the layers run from RADIUS below it upward
            int oy = dy - RADIUS;
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    if (dx * dx + oy * oy + dz * dz > RADIUS * RADIUS) {
                        indices[idx++] = -1;
                        continue;
                    }
                    BlockPos pos = new BlockPos(cx + dx, cy + oy, cz + dz);
                    BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
                    int id = Block.getId(state);
                    Integer slot = slots.get(id);
                    if (slot == null) {
                        // (a byte holds 127 kinds besides "outside"; past that a block is drawn as air)
                        slot = palette.size() < Byte.MAX_VALUE ? palette.size() : 0;
                        if (slot != 0) {
                            palette.add(id);
                            slots.put(id, slot);
                        }
                    }
                    indices[idx++] = (byte) (int) slot;
                }
            }
        }
        int[] figures = gatherFigures(level, centre);
        boolean night = level.isDarkOutside();
        OrbView view = new OrbView(orb, Component.literal(target.name()), palette.stream().mapToInt(Integer::intValue).toArray(), indices, new int[0],
            figures, night);
        for (ServerPlayer p : from.players()) {
            if (p.blockPosition().closerThan(orb, VIEWERS) && ServerPlayNetworking.canSend(p, OrbView.TYPE)) {
                ServerPlayNetworking.send(p, view);
            }
        }
    }

    /** The world {@code target} is in: a person's own (found from the caster's), a place's by its dimension; null if none. */
    private static @org.jspecify.annotations.Nullable ServerLevel targetLevel(ServerLevel from, Target target) {
        if (target.kind() == TargetKind.PERSON) {
            return Hair.find(from, target.person()) instanceof LivingEntity who && who.level() instanceof ServerLevel own ? own : null;
        }
        Identifier id = Identifier.tryParse(target.dimension() == null ? "" : target.dimension());
        return id == null ? null : from.getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    private static @org.jspecify.annotations.Nullable Vec3 targetCentre(ServerLevel level, Target target) {
        if (target.kind() == TargetKind.PERSON) {
            return Hair.find(level, target.person()) instanceof LivingEntity who ? who.getEyePosition() : null;
        }
        return target.pos() == null ? null : Vec3.atCenterOf(target.pos());
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
            int walking = !e.onGround() ? 1 : 0;
            figures[n * 4] = dx;
            figures[n * 4 + 1] = dy;
            figures[n * 4 + 2] = dz;
            figures[n * 4 + 3] = (kind << 6) | (facing << 1) | walking; // pack: kind(2 bits), facing(2 bits), walking(1 bit), rest unused
            n++;
        }
        return java.util.Arrays.copyOf(figures, n * 4);
    }

    /**
     * A watched place's 3x3 chunks kept loaded: a portal ticket on them, renewed each refresh, which lapses by itself
     * 15 s after the watching ends. A person's land is loaded or not as it is (the spell needs it loaded).
     */
    private static void keepLoaded(MinecraftServer server, Target target) {
        if (target.kind() != TargetKind.PLACE || target.pos() == null) {
            return;
        }
        Identifier id = Identifier.tryParse(target.dimension() == null ? "" : target.dimension());
        ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        if (level != null) {
            level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(target.pos()), 1);
        }
    }

    /** The point a view's centre sits over, for others to read. */
    static Vec3 eyes(LivingEntity who) {
        return who.getEyePosition();
    }
}