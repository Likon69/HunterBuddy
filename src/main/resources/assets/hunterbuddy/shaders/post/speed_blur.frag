#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Scene;

layout (std140) uniform BlurData {
    vec4 u_FocusPan;
    vec4 u_Subject;
    vec4 u_Params;
};

out vec4 color;

const int SAMPLES = 16;
const float REACH = 0.04;
const float CLEAR_RADIUS = 0.08;
const float FULL_RADIUS = 0.6;
const float MAX_DISTANCE = 1.0;
const float SUBJECT_SOFT_EDGE = 1.6;

float gradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec2 focus = u_FocusPan.xy;
    vec2 pan = u_FocusPan.zw;
    vec2 subject = u_Subject.xy;
    float subjectRadius = u_Subject.z;
    float cap = u_Subject.w;
    float strength = u_Params.x;

    float aspect = v_OneTexel.y / v_OneTexel.x;
    vec2 toFocus = (focus - v_TexCoord) * vec2(aspect, 1.0);
    float distanceToFocus = length(toFocus);

    vec2 line = pan * vec2(aspect, 1.0);
    if (distanceToFocus > 0.0) {
        float radial = strength * REACH * min(distanceToFocus, MAX_DISTANCE)
            * smoothstep(CLEAR_RADIUS, FULL_RADIUS, distanceToFocus);
        line += (toFocus / distanceToFocus) * radial;
    }

    float lineLength = length(line);
    if (lineLength > cap) {
        line *= cap / lineLength;
    }

    if (subjectRadius > 0.0) {
        float fromSubject = length((v_TexCoord - subject) * vec2(aspect, 1.0));
        line *= smoothstep(subjectRadius, subjectRadius * SUBJECT_SOFT_EDGE, fromSubject);
    }

    if (dot(line, line) < 1.0e-8) {
        color = texture(u_Scene, v_TexCoord);
        return;
    }

    vec2 stride = line * vec2(1.0 / aspect, 1.0);
    float jitter = gradientNoise(gl_FragCoord.xy);
    vec2 low = 0.5 * v_OneTexel;
    vec2 high = vec2(1.0) - low;

    vec4 sum = vec4(0.0);
    for (int i = 0; i < SAMPLES; i++) {
        float t = (float(i) + jitter) / float(SAMPLES) - 0.5;
        sum += texture(u_Scene, clamp(v_TexCoord + stride * t, low, high));
    }

    color = sum / float(SAMPLES);
}
