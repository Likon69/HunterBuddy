#version 330 core

uniform sampler2D SkyTexture;

in vec2 v_Ndc;

out vec4 fragColor;

void main() {
    fragColor = texture(SkyTexture, v_Ndc * 0.5 + 0.5);
}
