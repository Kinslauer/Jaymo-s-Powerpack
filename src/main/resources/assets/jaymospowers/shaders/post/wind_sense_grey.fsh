#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SenseConfig {
    float Strength;
};

layout(location = 0) out vec4 fragColor;

// Wind's airflow sense (Jung: "make the screen go grey and white colour wise"): the world drains to greys, washed a
// little toward white, Strength of the way (0 to 1). Light and dark stay where they were, so nothing is hidden.
void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 washed = mix(vec3(grey), vec3(1.0), 0.3);
    fragColor = vec4(mix(color, washed, Strength), 1.0);
}
