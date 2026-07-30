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

/** 표면 무늬 종류. 0=민무늬 1=가로띠 2=소용돌이 3=분화구 4=얼음균열 5=용암 6=불꽃 7=점박이 */
uniform int uSurface;
/** 무늬에 쓰는 두 번째 색. 띠 사이나 균열 안쪽에 들어간다. */
uniform vec3 uAccentColor;

out vec4 fragColor;

/** 해시 기반 값잡음. 텍스처를 넣으면 용량이 커져서 셰이더로 만든다. */
float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i);
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    return 0.5 * noise(p) + 0.25 * noise(p * 2.03) + 0.125 * noise(p * 4.01)
         + 0.0625 * noise(p * 8.07);
}

/** 표면 무늬 세기 0~1. 이 값으로 껍질색과 강조색을 섞는다. */
float pattern(vec3 n) {
    if (uSurface == 1) {
        // 가로 띠. 목성처럼 위도를 따라 흐르고 경계가 물결친다.
        return smoothstep(0.35, 0.65, fract(n.y * 4.0 + fbm(n * 3.0) * 0.7));
    } else if (uSurface == 2) {
        // 소용돌이. 위도에 따라 비틀어 감는다.
        float a = atan(n.z, n.x) + n.y * 3.4 + fbm(n * 2.2) * 2.0;
        return smoothstep(0.3, 0.7, fract(a * 0.6));
    } else if (uSurface == 3) {
        // 분화구. 낮은 잡음의 골짜기를 파낸다.
        float c = fbm(n * 6.0);
        return smoothstep(0.52, 0.36, c);
    } else if (uSurface == 4) {
        // 얼음 균열. 잡음이 0을 지나는 자리가 실금이 된다.
        float c = abs(fbm(n * 5.0) - 0.5);
        return smoothstep(0.06, 0.0, c);
    } else if (uSurface == 5) {
        // 용암. 굵은 덩어리 사이로 갈라진 틈이 빛난다.
        float c = fbm(n * 3.2);
        return smoothstep(0.44, 0.58, c);
    } else if (uSurface == 6) {
        // 불꽃. 잘게 일렁이는 표면.
        return smoothstep(0.4, 0.75, fbm(n * 4.5) + fbm(n * 11.0) * 0.4);
    } else if (uSurface == 7) {
        // 점박이. 자잘한 알갱이가 박혀 있다.
        return smoothstep(0.62, 0.78, fbm(n * 9.0));
    }
    return 0.0;
}

void main() {
    if (vAlpha < 0.02) discard;

    vec3 n = normalize(vNormal);
    // 무늬는 껍질 바깥면에만 그린다. 깨진 단면과 안쪽면은 왁스 속살이라 민무늬다.
    vec3 shell = uShellColor;
    if (uSurface > 0 && vFace > 0.5) {
        shell = mix(uShellColor, uAccentColor, pattern(n));
    }

    vec3 base;
    if (vFace > 0.5) {
        base = shell;
    } else if (vFace < -0.5) {
        base = uFleshColor * 0.62;          // 껍질 안쪽면은 그늘져 있다
    } else {
        base = mix(uShellColor, uFleshColor, 0.35) * 0.72;  // 깨진 단면
    }

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
uniform vec3 uOffset;

out vec3 vNormal;
out vec3 vWorld;

void main() {
    vec3 p = aPos;
    float d = max(0.0, 1.0 - length(p - uPressPoint) / 0.85);
    p -= normalize(uPressPoint + vec3(0.0001)) * (d * d) * uPressAmount;

    // 여러 개를 흩어 놓을 때 껍질과 같은 자리로 옮겨야 한다.
    vec3 world = (uRot * p) * uRadius + uOffset;
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

/**
 * 1이면 속살(불투명), 1보다 작으면 고무풍선 껍질이다.
 * 같은 구 메시를 반지름과 이 값만 바꿔 두 번 그린다.
 */
uniform float uAlpha;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    vec3 v = normalize(uCamPos - vWorld);
    float wrap = dot(n, l) * 0.5 + 0.5;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 2.0);
    // 말랑한 것은 빛이 속으로 스며 가장자리가 밝다
    vec3 col = uColor * (0.3 + 0.8 * wrap) + uColor * rim * 0.5;

    if (uAlpha < 0.999) {
        // 고무풍선은 가운데가 비치고 가장자리에서 두꺼워 보인다. 알파를 프레넬로 준다.
        // 균일하게 주면 비닐랩을 씌운 것처럼 보이고 안이 훤히 들여다보인다.
        float edge = pow(1.0 - max(dot(n, v), 0.0), 1.6);
        float a = uAlpha * (0.35 + 1.9 * edge);
        // 젖은 고무의 반짝임
        vec3 h = normalize(l + v);
        float spec = pow(max(dot(n, h), 0.0), 48.0);
        fragColor = vec4(col * 0.7 + vec3(spec) * 0.6, clamp(a, 0.0, 0.9));
        return;
    }
    fragColor = vec4(col, 1.0);
}
"""
}
