#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform ChronoConfig {
    float Strength;
};

layout(location = 0) out vec4 fragColor;

// A Chrono's slowed or stopped world (Andy, from the Hand of God mod's super speed, ideas only): the colour drains to an
// old gold, like a photograph, Strength of the way (0 to 1), and the edges of the view sink a little darker.
void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 gold = clamp(vec3(grey) * vec3(1.12, 0.98, 0.74), 0.0, 1.0);
    vec2 d = texCoord - 0.5;
    float edge = smoothstep(0.3, 0.75, length(d));
    vec3 graded = mix(color, gold, Strength) * (1.0 - edge * 0.35 * Strength);
    fragColor = vec4(graded, 1.0);
}
