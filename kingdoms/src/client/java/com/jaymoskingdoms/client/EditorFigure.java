package com.jaymoskingdoms.client;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.236) The figure of your own character on MCA's open editor screen, which isn't in the world: so it's drawn with
 * your heritage's skin ({@code McaVerdantSkinMixin}) and its skin picker kept to your heritage ({@code McaEditorSkinMixin}).
 */
public final class EditorFigure {
	private static @Nullable Entity figure;

	private EditorFigure() {
	}

	public static @Nullable Entity current() {
		return figure;
	}

	public static void set(@Nullable Entity entity) {
		figure = entity;
	}

	/** The figure, if {@code id} is its entity id (the editor's draws ask for it by id). */
	public static @Nullable Entity byId(int id) {
		Entity f = figure;
		return f != null && f.getId() == id ? f : null;
	}
}
