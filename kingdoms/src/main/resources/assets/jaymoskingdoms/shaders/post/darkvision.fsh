#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DarkvisionConfig {
    float Reach;
    float Near;
};

layout(location = 0) out vec4 fragColor;

// Seeing in the dark by blood: whatever is dim, within Reach blocks of the eye, is lifted to grey shapes with no colour
// in them. Lit places keep their colour; the dark past Reach stays dark. The distance comes from the depth buffer
// (a perspective depth: 1 - Near / distance).
void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;
    float gap = Near / max(1.0 - depth, 0.000001);

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float dim = 1.0 - smoothstep(0.10, 0.45, luma);
    float within = 1.0 - smoothstep(Reach - 3.0, Reach, gap);
    float amount = dim * within;

    float shape = clamp(luma * 3.2 + 0.05, 0.0, 0.62);
    fragColor = vec4(mix(color, vec3(shape), amount), 1.0);
}
