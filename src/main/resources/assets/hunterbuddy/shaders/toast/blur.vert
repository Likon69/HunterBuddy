#version 330 core

layout(location = 0) in vec2 pos;
out vec2 v_TexCoord;

void main() {
    gl_Position = vec4(pos, 0.0, 1.0);
    v_TexCoord = (pos + 1.0) * 0.5;
}
