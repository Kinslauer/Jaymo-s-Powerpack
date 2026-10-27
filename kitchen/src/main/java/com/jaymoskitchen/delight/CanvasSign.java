package com.jaymoskitchen.delight;

import java.util.Set;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's canvas signs, MIT; their art, our own code.) A sign of canvas, plain (beige) or dyed any of the
 * sixteen colours, standing, on a wall, hanging from a ceiling or from a wall ({@link Standing}, {@link Wall},
 * {@link Hanging}, {@link WallHanging}). They are spruce signs for every game purpose but their look and, when the
 * canvas is dark, their text, which starts out white.
 */
public interface CanvasSign {
	/** Backgrounds dark enough that a new sign's text starts white. */
	Set<DyeColor> DARK_BACKGROUNDS = Set.of(DyeColor.GRAY, DyeColor.PURPLE, DyeColor.BLUE, DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK);

	/** The dye this sign's canvas is, or null for plain canvas. */
	@Nullable
	DyeColor getBackgroundColor();

	/** Whether the canvas is dark. */
	default boolean isDarkBackground() {
		DyeColor color = getBackgroundColor();
		return color != null && DARK_BACKGROUNDS.contains(color);
	}

	class Standing extends StandingSignBlock implements CanvasSign {
		private final @Nullable DyeColor color;

		public Standing(Properties properties, @Nullable DyeColor color) {
			super(WoodType.SPRUCE, properties);
			this.color = color;
		}

		@Override
		public @Nullable DyeColor getBackgroundColor() {
			return color;
		}
	}

	class Wall extends WallSignBlock implements CanvasSign {
		private final @Nullable DyeColor color;

		public Wall(Properties properties, @Nullable DyeColor color) {
			super(WoodType.SPRUCE, properties);
			this.color = color;
		}

		@Override
		public @Nullable DyeColor getBackgroundColor() {
			return color;
		}
	}

	class Hanging extends CeilingHangingSignBlock implements CanvasSign {
		private final @Nullable DyeColor color;

		public Hanging(Properties properties, @Nullable DyeColor color) {
			super(WoodType.SPRUCE, properties);
			this.color = color;
		}

		@Override
		public @Nullable DyeColor getBackgroundColor() {
			return color;
		}
	}

	class WallHanging extends WallHangingSignBlock implements CanvasSign {
		private final @Nullable DyeColor color;

		public WallHanging(Properties properties, @Nullable DyeColor color) {
			super(WoodType.SPRUCE, properties);
			this.color = color;
		}

		@Override
		public @Nullable DyeColor getBackgroundColor() {
			return color;
		}
	}
}
