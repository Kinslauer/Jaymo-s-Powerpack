package com.jaymospowers.client.fx.effekseer;

import static org.lwjgl.opengl.GL33C.*;

/** Gives the native renderer conventional depth while drawing directly to Minecraft's color target. */
final class SpectacleFramebuffer implements AutoCloseable {
	private final int program;
	private final int vao;
	private final int framebuffer;
	private final int depth;
	private final int background;
	private int width;
	private int height;

	SpectacleFramebuffer() {
		program = depthProgram();
		vao = glGenVertexArrays();
		framebuffer = glGenFramebuffers();
		depth = glGenTextures();
		background = glGenTextures();
	}

	void bind(int width, int height, int color, int worldDepth) {
		glActiveTexture(GL_TEXTURE0);
		glBindSampler(0, 0);
		if (this.width != width || this.height != height) {
			allocate(depth, GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, width, height);
			allocate(background, GL_RGBA16F, GL_RGBA, width, height);
			this.width = width;
			this.height = height;
		}

		glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
		if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
			throw new IllegalStateException("Effekseer framebuffer is incomplete");
		}

		// Refraction needs a copy of scene color. Sampling the attachment while writing it is undefined.
		glBindTexture(GL_TEXTURE_2D, background);
		glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
		glBindTexture(GL_TEXTURE_2D, worldDepth);
		glViewport(0, 0, width, height);
		glDisable(GL_SCISSOR_TEST);
		glDisable(GL_STENCIL_TEST);
		glDisable(GL_CULL_FACE);
		glEnable(GL_DEPTH_TEST);
		glDepthMask(true);
		glDepthFunc(GL_ALWAYS);
		glColorMask(false, false, false, false);
		glUseProgram(program);
		glBindVertexArray(vao);
		glDrawArrays(GL_TRIANGLES, 0, 3);
		glColorMask(true, true, true, true);
		glDepthFunc(GL_LEQUAL);
	}

	int background() {
		return background;
	}

	@Override
	public void close() {
		glDeleteFramebuffers(framebuffer);
		glDeleteTextures(depth);
		glDeleteTextures(background);
		glDeleteVertexArrays(vao);
		glDeleteProgram(program);
	}

	private static void allocate(int texture, int internal, int format, int width, int height) {
		glBindTexture(GL_TEXTURE_2D, texture);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, internal, width, height, 0, format, GL_FLOAT, 0L);
	}

	private static int depthProgram() {
		int vertex = shader(GL_VERTEX_SHADER, """
			#version 150
			void main() {
				vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
				gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
			}
			""");
		try {
			int fragment = shader(GL_FRAGMENT_SHADER, """
				#version 150
				uniform sampler2D WorldDepth;
				void main() {
					gl_FragDepth = 1.0 - texelFetch(WorldDepth, ivec2(gl_FragCoord.xy), 0).r;
				}
				""");
			try {
				int program = glCreateProgram();
				glAttachShader(program, vertex);
				glAttachShader(program, fragment);
				glLinkProgram(program);
				if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
					String error = glGetProgramInfoLog(program);
					glDeleteProgram(program);
					throw new IllegalStateException(error);
				}
				return program;
			} finally {
				glDeleteShader(fragment);
			}
		} finally {
			glDeleteShader(vertex);
		}
	}

	private static int shader(int stage, String source) {
		int shader = glCreateShader(stage);
		glShaderSource(shader, source);
		glCompileShader(shader);
		if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
			String error = glGetShaderInfoLog(shader);
			glDeleteShader(shader);
			throw new IllegalStateException(error);
		}
		return shader;
	}

	/** Preserve raw GL bindings because RenderPearl and Iris cache their own state. */
	static final class GlState implements AutoCloseable {
		private static final int[] UNPACK = {
			GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT, GL_UNPACK_SKIP_ROWS,
			GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_IMAGES, GL_UNPACK_SWAP_BYTES, GL_UNPACK_LSB_FIRST
		};
		private final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
		private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
		private final int program = glGetInteger(GL_CURRENT_PROGRAM);
		private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
		private final int array = glGetInteger(GL_ARRAY_BUFFER_BINDING);
		private final int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
		private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
		private final int depthFunc = glGetInteger(GL_DEPTH_FUNC);
		private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
		private final boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
		private final boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
		private final boolean stencil = glIsEnabled(GL_STENCIL_TEST);
		private final boolean cull = glIsEnabled(GL_CULL_FACE);
		private final int[] viewport = new int[4];
		private final boolean[] colorMask = new boolean[4];
		private final int[] unpack = new int[UNPACK.length];
		private final int[] textures = new int[8];
		private final int[] samplers = new int[8];

		GlState() {
			glGetIntegerv(GL_VIEWPORT, viewport);
			try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
				var mask = stack.malloc(4);
				glGetBooleanv(GL_COLOR_WRITEMASK, mask);
				for (int i = 0; i < colorMask.length; i++) {
					colorMask[i] = mask.get(i) != 0;
				}
			}
			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
			for (int i = 0; i < UNPACK.length; i++) {
				unpack[i] = glGetInteger(UNPACK[i]);
				glPixelStorei(UNPACK[i], UNPACK[i] == GL_UNPACK_ALIGNMENT ? 1 : 0);
			}
			for (int i = 0; i < textures.length; i++) {
				glActiveTexture(GL_TEXTURE0 + i);
				textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
				samplers[i] = glGetInteger(GL_SAMPLER_BINDING);
				glBindSampler(i, 0);
			}
			glActiveTexture(GL_TEXTURE0);
		}

		@Override
		public void close() {
			glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
			glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
			glUseProgram(program);
			glBindVertexArray(vao);
			glBindBuffer(GL_ARRAY_BUFFER, array);
			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
			glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
			glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
			glDepthFunc(depthFunc);
			glDepthMask(depthMask);
			restore(GL_DEPTH_TEST, depthTest);
			restore(GL_SCISSOR_TEST, scissor);
			restore(GL_STENCIL_TEST, stencil);
			restore(GL_CULL_FACE, cull);
			for (int i = 0; i < UNPACK.length; i++) {
				glPixelStorei(UNPACK[i], unpack[i]);
			}
			for (int i = 0; i < textures.length; i++) {
				glActiveTexture(GL_TEXTURE0 + i);
				glBindTexture(GL_TEXTURE_2D, textures[i]);
				glBindSampler(i, samplers[i]);
			}
			glActiveTexture(activeTexture);
		}

		private static void restore(int feature, boolean enabled) {
			if (enabled) {
				glEnable(feature);
			} else {
				glDisable(feature);
			}
		}
	}
}

