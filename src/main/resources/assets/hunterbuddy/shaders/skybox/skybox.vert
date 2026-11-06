#version 330 core

layout (location = 0) in vec4 pos;

out vec2 v_Ndc;

void main() {
    gl_Position = pos;
    v_Ndc = pos.xy;
}
