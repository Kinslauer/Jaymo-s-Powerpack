package com.jaymoskingdoms.client.bb;

import com.google.gson.JsonElement;

/**
 * (0.7.326) The bit of Molang a Blockbench keyframe can hold: numbers, {@code + - * /}, brackets, {@code query.anim_time}
 * (the animation's own clock, seconds) and {@code math.sin/cos} (degrees), {@code abs, max, min, clamp, sqrt, floor,
 * round, pi}. Read once when the model loads; anything else reads as 0.
 */
@FunctionalInterface
public interface Molang {
	float eval(float time);

	static Molang of(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			float v = json.getAsFloat();
			return t -> v;
		}
		String s = json.getAsString().trim();
		if (s.isEmpty()) {
			return t -> 0.0F;
		}
		try {
			float v = Float.parseFloat(s);
			return t -> v;
		} catch (NumberFormatException e) {
			try {
				return new Parser(s.toLowerCase(java.util.Locale.ROOT)).parse();
			} catch (RuntimeException bad) {
				return t -> 0.0F;
			}
		}
	}

	final class Parser {
		private final String s;
		private int at;

		Parser(String s) {
			this.s = s;
		}

		Molang parse() {
			Molang m = this.sum();
			this.space();
			if (this.at < this.s.length()) {
				throw new IllegalArgumentException("trailing " + this.s.substring(this.at));
			}
			return m;
		}

		private void space() {
			while (this.at < this.s.length() && Character.isWhitespace(this.s.charAt(this.at))) {
				this.at++;
			}
		}

		private boolean eat(char c) {
			this.space();
			if (this.at < this.s.length() && this.s.charAt(this.at) == c) {
				this.at++;
				return true;
			}
			return false;
		}

		private Molang sum() {
			Molang left = this.product();
			while (true) {
				if (this.eat('+')) {
					Molang a = left, b = this.product();
					left = t -> a.eval(t) + b.eval(t);
				} else if (this.eat('-')) {
					Molang a = left, b = this.product();
					left = t -> a.eval(t) - b.eval(t);
				} else {
					return left;
				}
			}
		}

		private Molang product() {
			Molang left = this.unary();
			while (true) {
				if (this.eat('*')) {
					Molang a = left, b = this.unary();
					left = t -> a.eval(t) * b.eval(t);
				} else if (this.eat('/')) {
					Molang a = left, b = this.unary();
					left = t -> {
						float d = b.eval(t);
						return d == 0.0F ? 0.0F : a.eval(t) / d;
					};
				} else {
					return left;
				}
			}
		}

		private Molang unary() {
			if (this.eat('-')) {
				Molang a = this.unary();
				return t -> -a.eval(t);
			}
			if (this.eat('+')) {
				return this.unary();
			}
			return this.atom();
		}

		private Molang atom() {
			if (this.eat('(')) {
				Molang m = this.sum();
				this.eat(')');
				return m;
			}
			this.space();
			int start = this.at;
			char c = this.s.charAt(this.at);
			if (Character.isDigit(c) || c == '.') {
				while (this.at < this.s.length() && (Character.isDigit(this.s.charAt(this.at)) || this.s.charAt(this.at) == '.' || this.s.charAt(this.at) == 'e')) {
					this.at++;
				}
				float v = Float.parseFloat(this.s.substring(start, this.at));
				return t -> v;
			}
			while (this.at < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.at)) || this.s.charAt(this.at) == '_' || this.s.charAt(this.at) == '.')) {
				this.at++;
			}
			String name = this.s.substring(start, this.at);
			if (name.equals("query.anim_time") || name.equals("q.anim_time")) {
				return t -> t;
			}
			if (name.equals("math.pi")) {
				return t -> (float) Math.PI;
			}
			if (!this.eat('(')) {
				return t -> 0.0F;
			}
			java.util.List<Molang> args = new java.util.ArrayList<>();
			if (!this.eat(')')) {
				do {
					args.add(this.sum());
				} while (this.eat(','));
				this.eat(')');
			}
			Molang a = args.isEmpty() ? t -> 0.0F : args.get(0);
			Molang b = args.size() > 1 ? args.get(1) : t -> 0.0F;
			Molang c3 = args.size() > 2 ? args.get(2) : t -> 0.0F;
			return switch (name) {
				case "math.sin" -> t -> (float) Math.sin(Math.toRadians(a.eval(t)));
				case "math.cos" -> t -> (float) Math.cos(Math.toRadians(a.eval(t)));
				case "math.abs" -> t -> Math.abs(a.eval(t));
				case "math.max" -> t -> Math.max(a.eval(t), b.eval(t));
				case "math.min" -> t -> Math.min(a.eval(t), b.eval(t));
				case "math.clamp" -> t -> Math.max(b.eval(t), Math.min(c3.eval(t), a.eval(t)));
				case "math.sqrt" -> t -> (float) Math.sqrt(Math.max(0.0F, a.eval(t)));
				case "math.floor" -> t -> (float) Math.floor(a.eval(t));
				case "math.round" -> t -> Math.round(a.eval(t));
				default -> t -> 0.0F;
			};
		}
	}
}
