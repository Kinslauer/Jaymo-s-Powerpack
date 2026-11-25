package com.jaymospowers.core.reader;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.WeaponKind;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.BlastGore;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.CutDepth;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The gore reader (ops only): {@code /gore reader} opens a screen listing how every living kind of creature was read
 * (its body plan, weight, blood, whether it is a metal body) and every weapon (its kind and so its blows, and how deep
 * a cut of it goes), and lets an op change a creature's blood, weight and body plan and a weapon's kind. What is changed
 * is kept in {@link GoreOverrides} and saved to its file at once.
 */
public final class GoreReader {
	/** What an {@link Edit} changes. */
	public static final int BLOOD = 0, WEIGHT = 1, BODY = 2, WEAPON = 3;

	/** One creature kind as it is read: {@code blood} is an RGB colour or {@link GoreOverrides#NO_BLOOD}; {@code bulk} is times a man's. */
	public record MobRow(String id, int plan, boolean planSet, float bulk, boolean bulkSet, int blood, boolean bloodSet, boolean metal) {
		static final StreamCodec<ByteBuf, MobRow> CODEC = StreamCodec.of((buf, r) -> {
			ByteBufCodecs.STRING_UTF8.encode(buf, r.id);
			ByteBufCodecs.VAR_INT.encode(buf, r.plan);
			buf.writeBoolean(r.planSet);
			buf.writeFloat(r.bulk);
			buf.writeBoolean(r.bulkSet);
			buf.writeInt(r.blood);
			buf.writeBoolean(r.bloodSet);
			buf.writeBoolean(r.metal);
		}, buf -> new MobRow(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean(), buf.readFloat(),
			buf.readBoolean(), buf.readInt(), buf.readBoolean(), buf.readBoolean()));
	}

	/** One weapon as it is read: {@code kind} is a {@link WeaponKind} ordinal, or -1 for not a weapon; {@code depth} a {@link CutDepth} tier. */
	public record WeaponRow(String id, int kind, boolean kindSet, int depth) {
		static final StreamCodec<ByteBuf, WeaponRow> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, WeaponRow::id,
			ByteBufCodecs.VAR_INT, WeaponRow::kind, ByteBufCodecs.BOOL, WeaponRow::kindSet, ByteBufCodecs.VAR_INT, WeaponRow::depth, WeaponRow::new);
	}

	/** Server to client: open the screen with these rows ({@code open}), or replace just these rows in it. */
	public record Data(boolean open, List<MobRow> mobs, List<WeaponRow> weapons) implements CustomPacketPayload {
		public static final Type<Data> TYPE = new Type<>(JaymosPowers.id("gore_reader_data"));
		public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Data::open,
			MobRow.CODEC.apply(ByteBufCodecs.list()), Data::mobs, WeaponRow.CODEC.apply(ByteBufCodecs.list()), Data::weapons, Data::new);

		@Override
		public Type<Data> type() {
			return TYPE;
		}
	}

	/** Client to server: change {@code what} ({@link #BLOOD} to {@link #WEAPON}) of {@code id} to {@code value}; empty clears it. */
	public record Edit(int what, String id, String value) implements CustomPacketPayload {
		public static final Type<Edit> TYPE = new Type<>(JaymosPowers.id("gore_reader_edit"));
		public static final StreamCodec<ByteBuf, Edit> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Edit::what,
			ByteBufCodecs.STRING_UTF8, Edit::id, ByteBufCodecs.STRING_UTF8, Edit::value, Edit::new);

		@Override
		public Type<Edit> type() {
			return TYPE;
		}
	}

	private GoreReader() {
	}

	public static void init() {
		GoreOverrides.load();
		// (a second read once the server is up: an override file edited by hand while the game was loading)
		ServerLifecycleEvents.SERVER_STARTING.register(server -> GoreOverrides.load());
		PayloadTypeRegistry.clientboundPlay().register(Data.TYPE, Data.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Edit.TYPE, Edit.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE, (payload, context) -> edit(context.player(), payload));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("gore").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reader").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					ServerPlayNetworking.send(player, new Data(true, mobRows(player.level()), weaponRows(player.level())));
					return 1;
				}))));
	}

	private static boolean op(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	// ---------------------------------------------------------------- reading

	private static List<MobRow> mobRows(Level level) {
		List<MobRow> rows = new ArrayList<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (DefaultAttributes.hasSupplier(type)) {
				rows.add(mobRow(level, type));
			}
		}
		rows.sort(Comparator.comparing(MobRow::id));
		return rows;
	}

	/** How {@code type} is read now: its plan, its weight, and the blood a real one would bleed (by its class too). */
	private static MobRow mobRow(Level level, EntityType<?> type) {
		BodyPlan plan = BodyPlan.read(type);
		Float weight = GoreOverrides.weight(type);
		float bulk = weight != null ? weight : (float) ((double) type.getWidth() * type.getWidth() * type.getHeight() / BlastGore.MAN_BULK);
		Integer set = GoreOverrides.blood(type);
		int blood = BladeBlood.bleeds(type) ? BladeBlood.color(type) : GoreOverrides.NO_BLOOD;
		Entity entity = null;
		try {
			entity = type.create(level, EntitySpawnReason.COMMAND);
			if (entity instanceof LivingEntity living) {
				blood = BladeBlood.bleeds(living) ? BladeBlood.color(living) : GoreOverrides.NO_BLOOD;
			}
		} catch (RuntimeException | LinkageError e) {
			// a mod's creature that won't be made without a world: read by its type alone
		} finally {
			if (entity != null) {
				entity.discard();
			}
		}
		return new MobRow(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), plan.ordinal(), GoreOverrides.body(type) != null, bulk,
			weight != null, blood, set != null, type.builtInRegistryHolder().is(BladeBlood.METAL_BODIES));
	}

	private static List<WeaponRow> weaponRows(Level level) {
		List<WeaponRow> rows = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			WeaponRow row = weaponRow(level, item, false);
			if (row != null) {
				rows.add(row);
			}
		}
		rows.sort(Comparator.comparing(WeaponRow::id));
		return rows;
	}

	/** {@code item} as a weapon is read, or null if it isn't one and nothing says it is ({@code always}: a row either way). */
	private static @Nullable WeaponRow weaponRow(Level level, Item item, boolean always) {
		try {
			ItemStack stack = new ItemStack(item);
			Optional<WeaponKind> set = GoreOverrides.weapon(item);
			WeaponKind kind = WeaponKind.of(stack);
			if (kind == null && !always && WeaponKind.guess(stack) == null && set == null) {
				return null;
			}
			return new WeaponRow(BuiltInRegistries.ITEM.getKey(item).toString(), kind == null ? -1 : kind.ordinal(), set != null, CutDepth.of(level, stack));
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- editing

	private static void edit(ServerPlayer player, Edit edit) {
		if (!op(player)) {
			return;
		}
		Identifier id = Identifier.tryParse(edit.id());
		String value = edit.value().trim();
		if (id == null) {
			return;
		}
		try {
			if (edit.what() == WEAPON) {
				Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				if (item == null) {
					return;
				}
				GoreOverrides.setWeapon(id, value.isEmpty() ? null : value.equalsIgnoreCase("none") ? Optional.empty()
					: Optional.of(WeaponKind.valueOf(value.toUpperCase(Locale.ROOT))));
				GoreOverrides.save();
				WeaponRow row = weaponRow(player.level(), item, true);
				ServerPlayNetworking.send(player, new Data(false, List.of(), row == null ? List.of() : List.of(row)));
				return;
			}
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
			if (type == null) {
				return;
			}
			switch (edit.what()) {
				case BLOOD -> GoreOverrides.setBlood(id, value.isEmpty() ? null : value.equalsIgnoreCase("none") ? GoreOverrides.NO_BLOOD
					: Integer.parseInt(value.replace("#", ""), 16) & 0xFFFFFF);
				case WEIGHT -> GoreOverrides.setWeight(id, value.isEmpty() ? null : Float.parseFloat(value));
				case BODY -> GoreOverrides.setBody(id, value.isEmpty() ? null : BodyPlan.valueOf(value.toUpperCase(Locale.ROOT)));
				default -> {
					return;
				}
			}
			GoreOverrides.save();
			ServerPlayNetworking.send(player, new Data(false, List.of(mobRow(player.level(), type)), List.of()));
		} catch (IllegalArgumentException e) {
			// a value that isn't one: nothing changes
		}
	}
}
