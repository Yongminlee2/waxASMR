package com.waxball.asmr.gl

object Shaders {

    /**
     * 조각마다 변환이 다르므로 정점이 자기 조각 번호를 들고 다닌다.
     * 그 번호로 텍스처에서 3x4 변환 행렬과 (수축량, 투명도)를 읽는다.
     *
     * 수축량은 금이 갈수록 커진다. 조각이 자기 중심 쪽으로 조금씩 줄어들면서
     * 조각 사이에 틈이 벌어지는데, 그게 눈에는 금으로 보인다.
     */
    const val SHELL_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aShrink;
layout(location = 3) in vec2 aShardFace;

uniform mat4 uViewProj;
uniform sampler2D uXform;
uniform float uScale;

out vec3 vNormal;
out vec3 vWorld;
out float vFace;
out float vAlpha;

void main() {
    int id = int(aShardFace.x + 0.5);
    vec4 r0 = texelFetch(uXform, ivec2(id, 0), 0);
    vec4 r1 = texelFetch(uXform, ivec2(id, 1), 0);
    vec4 r2 = texelFetch(uXform, ivec2(id, 2), 0);
    vec4 st = texelFetch(uXform, ivec2(id, 3), 0);

    vec4 p = vec4(aPos - aShrink * st.x, 1.0);
    vec3 world = vec3(dot(r0, p), dot(r1, p), dot(r2, p)) * uScale;
    vec3 n = normalize(vec3(dot(r0.xyz, aNormal), dot(r1.xyz, aNormal), dot(r2.xyz, aNormal)));

    vNormal = n;
    vWorld = world;
    vFace = aShardFace.y;
    vAlpha = st.y;
    gl_Position = uViewProj * vec4(world, 1.0);
}
"""

    const val SHELL_FRAGMENT = """#version 300 es
precision mediump float;

in vec3 vNormal;
in vec3 vWorld;
in float vFace;
in float vAlpha;

uniform vec3 uShellColor;
uniform vec3 uFleshColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;

out vec4 fragColor;

void main() {
    if (vAlpha < 0.02) discard;

    vec3 base;
    if (vFace > 0.5) {
        base = uShellColor;
    } else if (vFace < -0.5) {
        base = uFleshColor * 0.62;          // 껍질 안쪽면은 그늘져 있다
    } else {
        base = mix(uShellColor, uFleshColor, 0.35) * 0.72;  // 깨진 단면
    }

    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);
    vec3 h = normalize(l + v);

    float wrap = dot(n, l) * 0.5 + 0.5;     // 왁스는 빛을 머금어 경계가 부드럽다
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    float spec = pow(max(dot(n, h), 0.0), 48.0);

    vec3 col = base * (0.22 + 0.9 * wrap * wrap) + vec3(rim * 0.22) + vec3(spec * 0.3);
    fragColor = vec4(col, vAlpha);
}
"""

    /** 말랑이 코어. 누르면 그 지점이 실제로 눌린다. */
    const val CORE_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;

uniform mat4 uViewProj;
uniform mat3 uRot;
uniform float uRadius;
uniform vec3 uPressPoint;
uniform float uPressAmount;

out vec3 vNormal;
out vec3 vWorld;

void main() {
    vec3 p = aPos;
    float d = max(0.0, 1.0 - length(p - uPressPoint) / 0.85);
    p -= normalize(uPressPoint + vec3(0.0001)) * (d * d) * uPressAmount;

    vec3 world = (uRot * p) * uRadius;
    vNormal = normalize(uRot * aPos);
    vWorld = world;
    gl_Position = uViewProj * vec4(world, 1.0);
}
"""

    const val CORE_FRAGMENT = """#version 300 es
precision mediump float;

in vec3 vNormal;
in vec3 vWorld;

uniform vec3 uColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);
    float wrap = dot(n, l) * 0.5 + 0.5;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 2.0);
    // 말랑한 것은 빛이 속으로 스며 가장자리가 밝다
    vec3 col = uColor * (0.3 + 0.8 * wrap) + uColor * rim * 0.5;
    fragColor = vec4(col, 1.0);
}
"""
}
