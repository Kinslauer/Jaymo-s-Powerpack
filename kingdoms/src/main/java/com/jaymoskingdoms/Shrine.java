package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.462) The shrine: a small stone altar with candles. Hold use on it for {@link #PRAY_TICKS} (five seconds) to pray: once
 * a Minecraft day for each player, every part of the body heals a little ({@link #PRAYER_HEAL}) and festering is cleared,
 * with a soft chime and motes of light. The block gets a use every few ticks while the button is held, so the prayer counts
 * from the first use and is lost if the uses stop for {@link #BREAK_TICKS}, if you walk off, or if you turn to another block.
 * The day you last prayed is kept on the player ({@link #PRAYED}), through death.
 * <p>
 * (0.7.509) Patron gods: use a patron's offering on a plain shrine to give it to them ({@link #DEDICATION}, a glyph shows on the
 * altar); an offering at a dedicated shrine is made by the sworn ({@link Patrons#offered}); hold use to pray as before (three
 * days running at a patron's shrine swears you, {@link Patrons#prayed}); sneak and use with an empty hand opens the patrons screen.
 */
public final class Shrine extends Block {
	/** (0.7.462) How long to hold use (ticks), the longest pause between uses before the prayer is broken, how far you may stand. */
	static final int PRAY_TICKS = 100, BREAK_TICKS = 10;
	static final double REACH = 4.5;
	/** (0.7.462) The heal each part gets (health points), and the altar's glow. */
	static final float PRAYER_HEAL = 3.0F;
	static final int LIGHT = 6;
	/** (0.7.462) One Minecraft day, in ticks. */
	static final long DAY = 24000L;

	/** The day (overworld clock time / 24000) a player last prayed. Saved, kept through death. */
	static final AttachmentType<Long> PRAYED = AttachmentRegistry.create(JaymosKingdoms.id("shrine_prayed"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** (0.7.509) Whom the altar is given to: none (a plain shrine) or one of the eight, by the patron's key. */
	public enum Dedication implements StringRepresentable {
		NONE("none", null),
		HEARTHKEEPER(Patron.HEARTHKEEPER),
		ANVIL_FATHER(Patron.ANVIL_FATHER),
		DEEP_MOTHER(Patron.DEEP_MOTHER),
		GREEN_HUNTSMAN(Patron.GREEN_HUNTSMAN),
		LANTERN_WARDEN(Patron.LANTERN_WARDEN),
		CROWNED_SUN(Patron.CROWNED_SUN),
		ASH_WHISPERER(Patron.ASH_WHISPERER),
		TRUTH(Patron.TRUTH);

		private final String name;
		private final @Nullable Patron patron;

		Dedication(Patron patron) {
			this(patron.key, patron);
		}

		Dedication(String name, @Nullable Patron patron) {
			this.name = name;
			this.patron = patron;
		}

		public @Nullable Patron patron() {
			return this.patron;
		}

		public static Dedication of(Patron patron) {
			for (Dedication d : values()) {
				if (d.patron == patron) {
					return d;
				}
			}
			return NONE;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	/** (0.7.509) Whom this altar belongs to. */
	public static final EnumProperty<Dedication> DEDICATION = EnumProperty.create("patron", Dedication.class);

	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

	/** A prayer begun: where, when it began and when it was last held (game time). */
	private record Prayer(BlockPos pos, long began, long last) {
	}

	private static final Map<UUID, Prayer> PRAYERS = new HashMap<>();

	/** (0.7.509) An offering made: where, and when it was last held (game time). One click, one offering. */
	private record Offer(BlockPos pos, long last) {
	}

	private static final Map<UUID, Offer> OFFERING = new HashMap<>();

	public Shrine(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(DEDICATION, Dedication.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(DEDICATION);
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PRAYERS.remove(handler.player.getUUID());
			OFFERING.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PRAYERS.clear();
			OFFERING.clear();
		});
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** (0.7.509) An offering in hand: dedicate a plain shrine to its patron, or make the offering at a dedicated one. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return Patrons.offeringFor(stack) != null ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		long now = server.getGameTime();
		Prayer prayer = PRAYERS.get(sp.getUUID());
		if (prayer != null && prayer.pos().equals(pos) && now - prayer.last() <= BREAK_TICKS) {
			// (a prayer is under way: carry on praying)
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		Offer held = OFFERING.get(sp.getUUID());
		if (held != null && held.pos().equals(pos) && now - held.last() <= BREAK_TICKS) {
			// (a held button never feeds a stack in)
			OFFERING.put(sp.getUUID(), new Offer(pos.immutable(), now));
			return InteractionResult.SUCCESS;
		}
		Patron p = Patrons.offeringFor(stack);
		if (p == null) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		Dedication given = state.getValue(DEDICATION);
		if (given == Dedication.NONE) {
			server.setBlock(pos, state.setValue(DEDICATION, Dedication.of(p)), 3);
			if (!sp.isCreative()) {
				stack.shrink(1);
			}
			server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 1.2F);
			server.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
			server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.03);
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.dedicated", Patrons.coloured(p)));
			Patrons.met(sp, p, pos);
		} else if (given.patron() == p) {
			Patrons.offered(sp, p, stack);
		} else {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.theirs", Patrons.coloured(given.patron())));
		}
		OFFERING.put(sp.getUUID(), new Offer(pos.immutable(), now));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (sp.isShiftKeyDown()) {
			// (0.7.509) sneak and use with an empty hand: the patrons screen, with this shrine's patron picked
			Patrons.openShrine(sp, pos, state);
			return InteractionResult.SUCCESS;
		}
		long today = Math.floorDiv(server.getOverworldClockTime(), DAY);
		Long last = sp.getAttached(PRAYED);
		if (last != null && last == today) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.quiet"));
			return InteractionResult.SUCCESS;
		}
		if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH) {
			return InteractionResult.PASS;
		}
		long now = server.getGameTime();
		Prayer prayer = PRAYERS.get(sp.getUUID());
		if (prayer == null || !prayer.pos().equals(pos) || now - prayer.last() > BREAK_TICKS) {
			prayer = new Prayer(pos.immutable(), now, now);
			server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 0.8F);
		}
		long held = now - prayer.began();
		if (held >= PRAY_TICKS) {
			PRAYERS.remove(sp.getUUID());
			pray(server, sp, pos, state, today);
			return InteractionResult.SUCCESS;
		}
		PRAYERS.put(sp.getUUID(), new Prayer(prayer.pos(), prayer.began(), now));
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.praying", (PRAY_TICKS - held + 19) / 20));
		if (held % 20 < BREAK_TICKS) {
			server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.15, 0.1, 0.15, 0.01);
		}
		return InteractionResult.SUCCESS;
	}

	/** The prayer answered: a small heal on every part, festering cleared, a chime and motes. */
	private static void pray(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, long today) {
		player.setAttached(PRAYED, today);
		// (0.7.513) the shrine of a patron whose trials you are under is cold: no heal, but praying your way back still counts
		Patron owner = state.getValue(DEDICATION).patron();
		if (owner != null && Turning.trial(player, owner) > 0.0F) {
			level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.8F);
			// cold line first: the turning progress and the turned line matter more, so they go last
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.cold", Patrons.coloured(owner)));
			Patrons.prayed(player, owner, pos, today);
			return;
		}
		if (PowersCompat.healEveryPart(player, PRAYER_HEAL) < 0.0F) {
			player.heal(PRAYER_HEAL);
		}
		PowersCompat.easeFever(player);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.1F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.8, player.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.02);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.answered"));
		// (0.7.509) at a patron's shrine, the prayer counts towards swearing and favour
		Patrons.prayed(player, state.getValue(DEDICATION).patron(), pos, today);
	}
}
