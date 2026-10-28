package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.110) Andy: "only one discovery method per dagger. if one is used the other dissappears until the dagger is
 * gone." One Sora's Dagger line is out in the world at a time, saved on the Overworld ({@link #STATE}).
 * <p>
 * The found stone blade, the gold cast poured over it, the iron blade cast in that, and every stage of the dagger made
 * from it carry the line's mark ({@link #MARK} in custom data, the line's serial). While no line is out, the stone blade
 * waits to be found two ways: a village child plays with it ({@link SorasBladeChild}) and a blacksmith shows it off
 * ({@link SorasBladeDisplay}). The first time a marked blade reaches a player, the line is out and the other find goes
 * away (cleared when its entity is next loaded, if not now). The line is gone, and the finds come back, when a marked
 * item is destroyed: a dropped one despawns, burns or falls out of the world, or the dagger breaks
 * ({@code mixin/SorasBreakMixin}). Marked things never melt in the smeltery, and the marked cast is used up by its one
 * iron blade.
 */
public final class SorasLineage {
	static final String MARK = "jaymospowers_soras_line";
	/** How often players' packs are checked for a found blade (ticks). */
	static final int CLAIM_CHECK_EVERY = 40;

	/** The current line: its serial, whether it's out, and the finds waiting for it (and when each was set out). */
	public record State(int serial, boolean out, Optional<UUID> child, long childAt, Optional<UUID> display, long displayAt) {
		static final State NEW = new State(1, false, Optional.empty(), 0L, Optional.empty(), 0L);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("serial").forGetter(State::serial),
			Codec.BOOL.fieldOf("out").forGetter(State::out),
			UUIDUtil.CODEC.optionalFieldOf("child").forGetter(State::child),
			Codec.LONG.optionalFieldOf("child_at", 0L).forGetter(State::childAt),
			UUIDUtil.CODEC.optionalFieldOf("display").forGetter(State::display),
			Codec.LONG.optionalFieldOf("display_at", 0L).forGetter(State::displayAt)
		).apply(i, State::new));
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosPowers.id("soras_lineage"),
		builder -> builder.persistent(State.CODEC));

	private static @Nullable MinecraftServer server;

	private SorasLineage() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		ServerTickEvents.END_SERVER_TICK.register(SorasLineage::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> tidy(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			// a dropped piece of the line destroyed (not picked up: a pickup empties its stack first)
			if (entity instanceof ItemEntity item && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()
				&& !item.getItem().isEmpty()) {
				gone(item.getItem());
			}
		});
	}

	// ---------------------------------------------------------------- state

	static State state(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(STATE, State.NEW);
	}

	static void set(MinecraftServer server, State state) {
		server.overworld().setAttached(STATE, state);
	}

	/** Whether a blade can be found right now (no line is out). */
	static boolean open(MinecraftServer server) {
		return !state(server).out();
	}

	// ---------------------------------------------------------------- the mark

	/** The line this stack belongs to, or 0. */
	public static int serial(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(MARK, 0);
	}

	public static boolean marked(ItemStack stack) {
		return serial(stack) != 0;
	}

	public static ItemStack mark(ItemStack stack, int serial) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MARK, serial));
		return stack;
	}

	/** Carries {@code from}'s mark (if any) onto {@code to}, which is returned. */
	public static ItemStack carry(ItemStack from, ItemStack to) {
		int serial = serial(from);
		return serial == 0 || to.isEmpty() ? to : mark(to, serial);
	}

	/** A stone blade for the line now waiting to be found. */
	static ItemStack foundBlade(MinecraftServer server) {
		return mark(SorasBladeChild.blade(), state(server).serial());
	}

	// ---------------------------------------------------------------- found, gone

	/** The line's blade has reached a player: the line is out, and the other find goes away. */
	static void claim(MinecraftServer server) {
		State state = state(server);
		if (state.out()) {
			return;
		}
		set(server, new State(state.serial(), true, Optional.empty(), 0L, Optional.empty(), 0L));
		List<Entity> finds = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity.entityTags().contains(SorasBladeChild.TAG) || entity.entityTags().contains(SorasBladeDisplay.TAG)) {
					finds.add(entity);
				}
			}
		}
		finds.forEach(SorasLineage::tidy);
	}

	/** A piece of the line is destroyed: if it's the line that's out, it's gone, and a new blade waits to be found. */
	public static void gone(ItemStack stack) {
		MinecraftServer server = SorasLineage.server;
		int serial = serial(stack);
		if (server == null || serial == 0) {
			return;
		}
		State state = state(server);
		if (state.out() && state.serial() == serial) {
			set(server, new State(serial + 1, false, Optional.empty(), 0L, Optional.empty(), 0L));
		}
	}

	/** Takes a find's blade away if it's no longer the find that's waiting (the line went out, or it was replaced). */
	private static void tidy(Entity entity) {
		MinecraftServer server = SorasLineage.server;
		if (server == null) {
			return;
		}
		State state = state(server);
		if (entity instanceof Villager child && child.entityTags().contains(SorasBladeChild.TAG)) {
			if (state.out() || state.child().filter(entity.getUUID()::equals).isEmpty()) {
				if (marked(child.getMainHandItem())) {
					child.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}
				child.removeTag(SorasBladeChild.TAG);
			}
		} else if (entity instanceof ItemFrame frame && frame.entityTags().contains(SorasBladeDisplay.TAG)) {
			if (state.out() || state.display().filter(entity.getUUID()::equals).isEmpty()) {
				frame.discard();
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CLAIM_CHECK_EVERY != 0) {
			return;
		}
		State state = state(server);
		if (state.out()) {
			return;
		}
		// however it reached them (taken, picked up after the child fell, knocked off the wall), it's found
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (ItemStack stack : player.getInventory()) {
				if (serial(stack) == state.serial()) {
					claim(server);
					return;
				}
			}
		}
	}
}
