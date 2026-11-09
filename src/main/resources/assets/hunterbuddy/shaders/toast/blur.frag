#version 330 core

uniform sampler2D u_Texture;
layout(std140) uniform MaskData {
    vec4 u_Rect;
    vec4 u_Screen;
};

in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
    vec2 point = vec2(gl_FragCoord.x, u_Screen.y - gl_FragCoord.y);
    vec2 halfSize = u_Rect.zw * 0.5;
    vec2 offset = abs(point - u_Rect.xy - halfSize) - halfSize + u_Screen.z;
    float distance = length(max(offset, 0.0)) + min(max(offset.x, offset.y), 0.0) - u_Screen.z;
    float coverage = (1.0 - smoothstep(-0.75, 0.75, distance)) * u_Screen.w;
    if (coverage <= 0.0) discard;
    fragColor = vec4(texture(u_Texture, v_TexCoord).rgb, coverage);
}
