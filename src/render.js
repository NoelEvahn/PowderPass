// Renderer, block/water shaders, sky, clouds, shadow mapping and post-processing (god rays).
import * as THREE from 'three';
import { getBlockTextureArray, TEX_INDEX } from './textures.js';
import { POS_SCALE } from './mesher.js';

const COMMON = /* glsl */ `
uniform float uTime;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform float uDay;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform float uFogStart;
uniform vec3 uCamPos;
uniform float uNether;
uniform float uUnderwater;
uniform float uDirect;
uniform float uShadows;
uniform float uFancy;
uniform sampler2D uShadowMap;
uniform mat4 uShadowMatrix;
uniform float uShadowTexel;
uniform vec3 uSkyTop;
uniform vec3 uSkyHorizon;
uniform float uGamma;

vec3 tonemap(vec3 c) {
  c *= uGamma;
  // ACES approximation
  vec3 x = c * (2.51 * c + 0.03);
  vec3 y = c * (2.43 * c + 0.59) + 0.14;
  return clamp(x / y, 0.0, 1.0);
}
vec3 toOutput(vec3 c) {
  return pow(tonemap(c), vec3(1.0 / 2.2));
}
float lightCurve(float l) {
  // Minecraft-like light falloff
  return pow(0.8, (1.0 - l) * 15.0);
}
vec3 applyFog(vec3 col, vec3 world) {
  float d = length(world - uCamPos);
  float f;
  if (uUnderwater > 0.5) f = 1.0 - exp(-d * 0.07);
  else f = smoothstep(uFogStart, uFogStart + 1.0 / max(uFogDensity, 0.0001), d);
  return mix(col, uFogColor, clamp(f, 0.0, 1.0));
}
float shadowAt(vec4 sp, float bias) {
  vec3 p = sp.xyz / sp.w * 0.5 + 0.5;
  if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
  float s = 0.0;
  float t = uShadowTexel;
  for (int i = -1; i <= 1; i++)
    for (int j = -1; j <= 1; j++) {
      float d = texture(uShadowMap, p.xy + vec2(float(i), float(j)) * t).r;
      s += p.z - bias > d ? 0.0 : 1.0;
    }
  s /= 9.0;
  // fade out at the edge of the shadow map
  vec2 e = smoothstep(0.0, 0.08, p.xy) * smoothstep(1.0, 0.92, p.xy);
  return mix(1.0, s, e.x * e.y);
}
`;

const FACE_NORMALS = /* glsl */ `
vec3 faceNormal(int f) {
  if (f == 0) return vec3(1.0, 0.0, 0.0);
  if (f == 1) return vec3(-1.0, 0.0, 0.0);
  if (f == 2) return vec3(0.0, 1.0, 0.0);
  if (f == 3) return vec3(0.0, -1.0, 0.0);
  if (f == 4) return vec3(0.0, 0.0, 1.0);
  return vec3(0.0, 0.0, -1.0);
}
`;

const SOLID_VS = /* glsl */ `
precision highp float;
in vec4 aLight;
in vec4 aTint;
in vec4 aData;
in float aLayer;
${COMMON}
${FACE_NORMALS}
out vec3 vUv;
out vec4 vLight;
out vec3 vTint;
out vec3 vWorld;
out vec3 vNormal;
out float vAnim;
out vec4 vShadowPos;
void main() {
  vec3 p = position / ${POS_SCALE.toFixed(1)};
  int face = int(aData.x + 0.5);
  float anim = aData.y;
  vec4 world = modelMatrix * vec4(p, 1.0);
  if (uFancy > 0.5) {
    vec3 w = world.xyz;
    if (anim > 0.5 && anim < 1.5) {
      world.x += sin(uTime * 1.6 + w.x * 0.7 + w.y * 0.5) * 0.035;
      world.z += cos(uTime * 1.3 + w.z * 0.6 + w.y * 0.4) * 0.035;
      world.y += sin(uTime * 1.9 + w.x * 0.5 + w.z * 0.5) * 0.02;
    } else if (anim > 1.5 && anim < 2.5) {
      float top = 1.0 - aData.w / 16.0;
      world.x += sin(uTime * 2.0 + w.x * 0.9 + w.z * 0.3) * 0.09 * top;
      world.z += cos(uTime * 1.7 + w.z * 0.8 + w.x * 0.2) * 0.07 * top;
    }
  }
  vUv = vec3(aData.z / 16.0, aData.w / 16.0, aLayer);
  vLight = aLight;
  vTint = pow(aTint.rgb, vec3(2.2));
  vWorld = world.xyz;
  vNormal = faceNormal(face);
  vAnim = anim;
  vShadowPos = uShadowMatrix * vec4(world.xyz + vNormal * 0.04, 1.0);
  gl_Position = projectionMatrix * viewMatrix * world;
}
`;

const SOLID_FS = /* glsl */ `
precision highp float;
precision highp sampler2DArray;
uniform sampler2DArray uTex;
${COMMON}
in vec3 vUv;
in vec4 vLight;
in vec3 vTint;
in vec3 vWorld;
in vec3 vNormal;
in float vAnim;
in vec4 vShadowPos;
out vec4 fragColor;

float caustic(vec2 p, float t) {
  float c = 0.0;
  vec2 q = p;
  for (int i = 0; i < 3; i++) {
    q = vec2(q.x + sin(q.y * 1.7 + t * 0.9), q.y + cos(q.x * 1.3 - t * 0.7));
    c += abs(sin(q.x * 2.1) * cos(q.y * 2.3));
  }
  return pow(c / 3.0, 3.0);
}

void main() {
  vec2 uv = vUv.xy;
  bool lava = vAnim > 2.5 && vAnim < 3.5;
  if (lava) {
    vec2 w = vWorld.xz * 0.5 + vWorld.y * 0.25;
    uv = fract(vec2(w.x + sin(uTime * 0.4 + w.y * 2.0) * 0.08, w.y + uTime * 0.03 + cos(uTime * 0.3 + w.x * 2.0) * 0.08));
  }
  vec4 tex = texture(uTex, vec3(uv, vUv.z));
  if (tex.a < 0.5) discard;
  vec3 albedo = tex.rgb * vTint;
  vec3 col;
  if (lava) {
    float pulse = 0.85 + 0.15 * sin(uTime * 1.5 + vWorld.x * 0.3 + vWorld.z * 0.2);
    col = albedo * 1.35 * pulse;
  } else {
    float sky = vLight.x, blk = vLight.y, ao = vLight.z;
    float skyB = lightCurve(sky);
    float blkB = lightCurve(blk);
    vec3 ambient;
    vec3 sun = vec3(0.0);
    if (uNether > 0.5) {
      ambient = vec3(0.42, 0.27, 0.22);
    } else {
      vec3 skyAmb = mix(vec3(0.035, 0.04, 0.08), mix(uSkyHorizon, vec3(0.75, 0.82, 0.95), 0.5) * 0.85, uDay);
      ambient = skyAmb * skyB;
      float nd = dot(vNormal, uSunDir);
      if (uShadows > 0.5) {
        float sh = nd > 0.0 ? shadowAt(vShadowPos, 0.0006) : 0.0;
        sun = uSunColor * max(nd, 0.0) * sh * smoothstep(0.55, 0.95, sky) * 0.9;
        ambient *= 0.6;
      } else {
        sun = uSunColor * max(nd, 0.0) * skyB * 0.35;
      }
    }
    vec3 torch = vec3(1.0, 0.72, 0.42) * blkB * 1.25;
    vec3 lightCol = ambient + sun + torch * (1.0 - 0.4 * uDay * skyB) + vec3(0.015);
    col = albedo * lightCol * (0.25 + 0.75 * ao);
    // caustics on surfaces next to water
    if (vLight.w > 0.5 && uFancy > 0.5) {
      float c = caustic(vWorld.xz * 1.3 + vWorld.y * 0.3, uTime);
      col += albedo * c * 0.55 * (uDay * skyB + blkB * 0.4) * vec3(0.7, 0.9, 1.0);
      col *= vec3(0.8, 0.92, 1.0);
    }
  }
  col = applyFog(col, vWorld);
  if (uDirect > 0.5) col = toOutput(col);
  fragColor = vec4(col, 1.0);
}
`;

const WATER_VS = /* glsl */ `
precision highp float;
in vec4 aLight;
in vec4 aTint;
in vec4 aData;
in float aLayer;
in vec2 aFlow;
${COMMON}
${FACE_NORMALS}
out vec4 vLight;
out vec4 vTint;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vFlow;
out vec3 vUv;
out vec4 vShadowPos;
out float vViewZ;
void main() {
  vec3 p = position / ${POS_SCALE.toFixed(1)};
  int face = int(aData.x + 0.5);
  vec4 world = modelMatrix * vec4(p, 1.0);
  if (face == 2 && aTint.a < 0.2 && uFancy > 0.5) {
    world.y += (sin(uTime * 1.4 + world.x * 0.9 + world.z * 0.4) * 0.5 + sin(uTime * 1.1 - world.z * 0.8 + world.x * 0.3) * 0.5) * 0.035 - 0.06;
  }
  vLight = aLight;
  vTint = vec4(pow(aTint.rgb, vec3(2.2)), aTint.a);
  vWorld = world.xyz;
  vNormal = faceNormal(face);
  vFlow = aFlow;
  vUv = vec3(aData.z / 16.0, aData.w / 16.0, aLayer);
  vShadowPos = uShadowMatrix * vec4(world.xyz + vec3(0.0, 0.05, 0.0), 1.0);
  vec4 vp = viewMatrix * world;
  vViewZ = -vp.z;
  gl_Position = projectionMatrix * vp;
}
`;

const WATER_FS = /* glsl */ `
precision highp float;
precision highp sampler2DArray;
uniform sampler2DArray uTex;
uniform float uWaterLayer;
uniform sampler2D tScene;
uniform float uRefract;
uniform vec2 uResolution;
uniform mat4 uProj;
${COMMON}
in vec4 vLight;
in vec4 vTint;
in vec3 vWorld;
in vec3 vNormal;
in vec2 vFlow;
in vec3 vUv;
in vec4 vShadowPos;
in float vViewZ;
out vec4 fragColor;

float waveH(vec2 p, float t) {
  float h = 0.0;
  h += sin(p.x * 1.1 + t * 1.3) * 0.25;
  h += sin(p.y * 1.4 - t * 1.1 + p.x * 0.4) * 0.22;
  h += sin((p.x + p.y) * 2.3 + t * 2.1) * 0.12;
  h += sin((p.x - p.y) * 3.1 - t * 2.7) * 0.07;
  h += sin(p.x * 5.3 + p.y * 2.1 + t * 3.3) * 0.035;
  return h;
}
vec3 skyColor(vec3 dir) {
  float y = clamp(dir.y, 0.0, 1.0);
  vec3 c = mix(uSkyHorizon, uSkyTop, pow(y, 0.6));
  return c;
}

void main() {
  float kind = vTint.a;
  float sky = vLight.x, blk = vLight.y;
  float skyB = lightCurve(sky), blkB = lightCurve(blk);
  vec3 lightCol = (uNether > 0.5 ? vec3(0.42, 0.27, 0.22) : mix(vec3(0.04, 0.05, 0.09), vec3(1.0), uDay) * skyB) + vec3(1.0, 0.72, 0.42) * blkB + vec3(0.02);
  vec3 col;
  float alpha;
  if (kind > 0.5 && kind < 0.8) {
    // nether portal swirl
    vec2 p = vUv.xy - 0.5;
    float a = atan(p.y, p.x), r = length(p);
    vec2 sw = vec2(cos(a + uTime * 0.8 + r * 6.0), sin(a + uTime * 0.8 + r * 6.0)) * r + 0.5;
    vec4 t1 = texture(uTex, vec3(fract(sw + vWorld.xz * 0.0), vUv.z));
    vec4 t2 = texture(uTex, vec3(fract(vUv.xy + vec2(uTime * 0.05, uTime * 0.11)), vUv.z));
    col = (t1.rgb * 0.6 + t2.rgb * 0.6) * 1.6;
    alpha = 0.78;
  } else if (kind > 0.8) {
    // ice
    vec4 t = texture(uTex, vUv);
    col = t.rgb * vTint.rgb * lightCol * 0.9;
    alpha = 0.72;
  } else {
    vec3 V = normalize(uCamPos - vWorld);
    vec3 n = vNormal;
    vec2 flow = vFlow;
    bool top = abs(vNormal.y) > 0.5;
    vec2 tuv;
    if (top) {
      vec2 p = vWorld.xz;
      float t = uTime;
      vec2 fo = flow * t * 1.2;
      if (uFancy > 0.5) {
        float e = 0.08;
        vec2 q = p * 1.6 - fo * 1.6;
        float h0 = waveH(q, t);
        float hx = waveH(q + vec2(e, 0.0), t);
        float hz = waveH(q + vec2(0.0, e), t);
        n = normalize(vec3(-(hx - h0) / e * 0.11, 1.0, -(hz - h0) / e * 0.11));
        if (!gl_FrontFacing) n = -n;
      }
      tuv = fract(p * 0.5 - fo * 0.5);
    } else {
      vec2 p = abs(vNormal.x) > 0.5 ? vWorld.zy : vWorld.xy;
      tuv = fract(vec2(p.x * 0.5, -p.y * 0.5 - uTime * 0.9));
    }
    float detail = texture(uTex, vec3(tuv, uWaterLayer)).r;
    float depth = vLight.z; // 0..1 water column depth
    vec3 base = vTint.rgb * (0.55 + detail * 0.5);
    if (uFancy > 0.5) {
      float ndv = max(dot(n, V), 0.0);
      float fres = 0.04 + 0.96 * pow(1.0 - ndv, 5.0);
      vec3 R = reflect(-V, n);
      vec3 refl = uNether > 0.5 ? vec3(0.4, 0.15, 0.1) : skyColor(R) * mix(0.15, 1.0, uDay);
      float sh = 1.0;
      if (uShadows > 0.5 && uNether < 0.5) sh = shadowAt(vShadowPos, 0.001);
      float spec = pow(max(dot(R, uSunDir), 0.0), 220.0) * 6.0 + pow(max(dot(R, uSunDir), 0.0), 24.0) * 0.25;
      vec3 deep = base * lightCol * mix(0.9, 0.35, depth);
      col = mix(deep, refl * skyB, fres * (top ? 1.0 : 0.3));
      col += uSunColor * spec * sh * smoothstep(0.6, 0.95, sky) * (top && vNormal.y > 0.0 ? 1.0 : 0.0);
      alpha = clamp(mix(0.55, 0.92, depth) + fres * 0.4, 0.0, 0.97);
      if (!gl_FrontFacing && top) { alpha = 0.65; col = mix(base * lightCol * 1.3, refl * skyB, 0.25); }
      if (uRefract > 0.5) {
        // ---- screen-space refraction, absorption, reflections and shore foam ----
        vec2 suv = gl_FragCoord.xy / uResolution;
        float pathScale = length(uCamPos - vWorld) / max(vViewZ, 0.001);
        float sceneZ = texture(tScene, suv).a;
        float thick = max(sceneZ - vViewZ, 0.0) * pathScale;
        vec2 offs = (top ? n.xz : n.xy) * 0.035 * clamp(thick, 0.0, 1.5) * (1.0 / (1.0 + vViewZ * 0.03));
        vec2 ruv = suv + offs;
        float rz = texture(tScene, ruv).a;
        if (rz < vViewZ) { ruv = suv; rz = sceneZ; }
        vec3 behind = texture(tScene, ruv).rgb;
        float path = min(max(rz - vViewZ, 0.0) * pathScale, 64.0);
        vec3 absorb = (vec3(1.0) - clamp(vTint.rgb * 1.6, 0.0, 1.0)) * 0.55 + vec3(0.04, 0.02, 0.015);
        vec3 T = exp(-absorb * path);
        vec3 scatter = vTint.rgb * lightCol * 0.55 * (1.0 - T) * (0.7 + 0.3 * detail);
        vec3 refr = behind * T + scatter;
        // screen-space reflection of terrain, falling back to the sky
        vec3 rcol = refl * skyB;
        if (top && gl_FrontFacing && uNether < 0.5) {
          vec3 Rr = normalize(vec3(R.x, max(R.y, 0.02), R.z));
          float stepLen = 0.6;
          vec3 pos = vWorld;
          for (int i = 0; i < 28; i++) {
            pos += Rr * stepLen;
            stepLen *= 1.12;
            vec4 cp = uProj * viewMatrix * vec4(pos, 1.0);
            if (cp.w <= 0.0) break;
            vec2 puv = cp.xy / cp.w * 0.5 + 0.5;
            if (puv.x < 0.0 || puv.x > 1.0 || puv.y < 0.0 || puv.y > 1.0) break;
            float sz = texture(tScene, puv).a;
            float pz = cp.w;
            if (pz > sz + 0.05 && pz - sz < stepLen * 2.5 + 0.5 && sz < 900.0) {
              vec2 e = smoothstep(0.0, 0.12, puv) * smoothstep(1.0, 0.88, puv);
              rcol = mix(rcol, texture(tScene, puv).rgb, e.x * e.y);
              break;
            }
          }
        }
        float f2 = top && gl_FrontFacing ? fres : 0.02;
        col = mix(refr, rcol, f2);
        col += uSunColor * spec * sh * smoothstep(0.6, 0.95, sky) * (top && gl_FrontFacing ? 1.0 : 0.0);
        // foam where water meets shore or falls
        if (top && gl_FrontFacing) {
          float fn = texture(uTex, vec3(fract(vWorld.xz * 0.37 + uTime * 0.03), uWaterLayer)).r;
          float foam = smoothstep(0.55, 0.0, path) * smoothstep(0.45, 0.75, fn + 0.25 * sin(uTime * 2.0 + vWorld.x + vWorld.z));
          col = mix(col, vec3(0.9) * lightCol, foam * 0.7);
        } else if (!top) {
          col = mix(col, vec3(0.85) * lightCol, 0.18 * smoothstep(0.5, 0.9, detail));
        }
        if (!gl_FrontFacing) {
          // looking up from underwater: Snell's window
          float win = smoothstep(0.35, 0.6, ndv);
          col = mix(base * lightCol * 0.5, behind * vec3(0.7, 0.9, 1.0), win);
        }
        alpha = 1.0;
      }
    } else {
      col = base * lightCol;
      alpha = 0.72;
    }
  }
  col = applyFog(col, vWorld);
  if (uDirect > 0.5) col = toOutput(col);
  fragColor = vec4(col, alpha);
}
`;

const SHADOW_VS = /* glsl */ `
precision highp float;
in vec4 aData;
in float aLayer;
uniform float uTime;
out vec3 vUv;
void main() {
  vec3 p = position / ${POS_SCALE.toFixed(1)};
  vUv = vec3(aData.z / 16.0, aData.w / 16.0, aLayer);
  gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(p, 1.0);
}
`;
const SHADOW_FS = /* glsl */ `
precision highp float;
precision highp sampler2DArray;
uniform sampler2DArray uTex;
in vec3 vUv;
out vec4 fragColor;
void main() {
  if (texture(uTex, vUv).a < 0.5) discard;
  fragColor = vec4(1.0);
}
`;

const SKY_VS = /* glsl */ `
out vec3 vDir;
void main() {
  vDir = normalize(position);
  vec4 p = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
  gl_Position = p.xyww;
}
`;
const SKY_FS = /* glsl */ `
precision highp float;
${COMMON}
uniform vec3 uMoonDir;
in vec3 vDir;
out vec4 fragColor;
float hash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
void main() {
  vec3 d = normalize(vDir);
  float y = d.y;
  vec3 col = mix(uSkyHorizon, uSkyTop, pow(clamp(y, 0.0, 1.0), 0.5));
  if (y < 0.0) col = mix(uSkyHorizon, uSkyHorizon * 0.5, clamp(-y * 3.0, 0.0, 1.0));
  // sunset glow around the sun near the horizon
  float sd = max(dot(d, uSunDir), 0.0);
  float horizonGlow = pow(1.0 - abs(y), 6.0) * smoothstep(-0.3, 0.25, uSunDir.y) * smoothstep(0.6, 0.0, uSunDir.y);
  col += vec3(1.0, 0.45, 0.15) * pow(sd, 6.0) * horizonGlow * 1.4;
  col += uSunColor * pow(sd, 28.0) * 0.5 * smoothstep(-0.1, 0.1, uSunDir.y);
  // sun disk (square like Minecraft)
  vec3 sdir = uSunDir;
  vec3 su = normalize(cross(sdir, vec3(0.0, 0.0, 1.0)));
  vec3 sv = cross(su, sdir);
  if (dot(d, sdir) > 0.0) {
    vec2 q = vec2(dot(d, su), dot(d, sv)) / dot(d, sdir);
    if (max(abs(q.x), abs(q.y)) < 0.065) col = mix(col, vec3(1.0, 0.95, 0.8) * 6.0, 1.0);
  }
  vec3 mdir = uMoonDir;
  if (dot(d, mdir) > 0.0) {
    vec2 q = vec2(dot(d, su), dot(d, sv)) / dot(d, mdir);
    float m = max(abs(q.x), abs(q.y));
    if (m < 0.05) col = mix(col, vec3(0.85, 0.88, 0.95) * 1.6, 1.0 - step(0.02, abs(q.x + 0.015)) * step(0.0, q.x) * 0.3);
  }
  // stars
  float night = 1.0 - smoothstep(0.0, 0.35, uDay);
  if (night > 0.0 && y > 0.0) {
    vec3 sp = floor(d * 220.0);
    float h = hash(sp);
    if (h > 0.9965) col += vec3(0.9, 0.9, 1.0) * night * (h - 0.9965) / 0.0035 * 1.4;
  }
  if (uDirect > 0.5) col = toOutput(col);
  fragColor = vec4(col, 1.0);
}
`;

const CLOUD_VS = /* glsl */ `
out vec3 vWorld;
void main() {
  vec4 w = modelMatrix * vec4(position, 1.0);
  vWorld = w.xyz;
  gl_Position = projectionMatrix * viewMatrix * w;
}
`;
const CLOUD_FS = /* glsl */ `
precision highp float;
${COMMON}
in vec3 vWorld;
out vec4 fragColor;
float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash2(i), hash2(i + vec2(1, 0)), f.x), mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), f.x), f.y);
}
void main() {
  vec2 p = vWorld.xz / 12.0 + vec2(uTime * 0.08, 0.0);
  vec2 cell = floor(p);
  float n = vnoise(cell * 0.35) * 0.65 + vnoise(cell * 0.9) * 0.35;
  if (n < 0.56) discard;
  float d = length(vWorld.xz - uCamPos.xz);
  float fade = 1.0 - smoothstep(180.0, 320.0, d);
  vec3 c = mix(vec3(0.12, 0.13, 0.18), vec3(1.0, 1.0, 1.0), uDay) * (0.9 + 0.1 * vnoise(p * 4.0));
  c = mix(c, uSkyHorizon, 0.25);
  if (uDirect > 0.5) c = toOutput(c);
  fragColor = vec4(c, 0.82 * fade);
}
`;

const POST_VS = /* glsl */ `
out vec2 vUv;
void main() { vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }
`;
const POST_FS = /* glsl */ `
precision highp float;
uniform sampler2D tColor;
uniform sampler2D tDepth;
uniform vec2 uSunScreen;
uniform float uSunVisible;
uniform float uRays;
uniform float uTime;
uniform float uUnderwater;
uniform float uInLava;
uniform vec3 uRayColor;
uniform float uAspect;
uniform float uGamma;
uniform float uDamage;
uniform float uPortal;
in vec2 vUv;
out vec4 fragColor;
vec3 tonemap(vec3 c) {
  c *= uGamma;
  vec3 x = c * (2.51 * c + 0.03);
  vec3 y = c * (2.43 * c + 0.59) + 0.14;
  return clamp(x / y, 0.0, 1.0);
}
void main() {
  vec2 uv = vUv;
  if (uUnderwater > 0.5) {
    uv += vec2(sin(uv.y * 20.0 + uTime * 2.0), cos(uv.x * 18.0 + uTime * 1.7)) * 0.0025;
  }
  if (uPortal > 0.0) {
    uv += vec2(sin(uv.y * 10.0 + uTime * 3.0), cos(uv.x * 9.0 + uTime * 2.5)) * 0.01 * uPortal;
  }
  vec3 col = texture(tColor, uv).rgb;
  if (uRays > 0.5 && uSunVisible > 0.0) {
    vec2 delta = (uSunScreen - uv);
    const int N = 48;
    delta *= 1.0 / float(N) * 0.95;
    vec2 p = uv;
    float decay = 1.0, acc = 0.0;
    float dither = fract(sin(dot(uv * 1000.0, vec2(12.9898, 78.233))) * 43758.5453);
    p += delta * dither;
    for (int i = 0; i < N; i++) {
      p += delta;
      float d = texture(tDepth, p).r;
      float s = d >= 0.99999 ? 1.0 : 0.0;
      acc += s * decay;
      decay *= 0.965;
    }
    acc /= float(N);
    vec2 sd = (uv - uSunScreen) * vec2(uAspect, 1.0);
    float falloff = 1.0 / (1.0 + dot(sd, sd) * 4.0);
    col += uRayColor * acc * falloff * uSunVisible * 0.75;
  }
  if (uUnderwater > 0.5) col = col * vec3(0.45, 0.7, 1.0) + vec3(0.0, 0.02, 0.05);
  if (uInLava > 0.5) col = mix(col, vec3(1.0, 0.35, 0.0), 0.85);
  col = tonemap(col);
  float luma = dot(col, vec3(0.299, 0.587, 0.114));
  col = clamp(mix(vec3(luma), col, 1.1), 0.0, 1.0);
  // vignette
  vec2 v = vUv - 0.5;
  col *= 1.0 - dot(v, v) * 0.55;
  if (uDamage > 0.0) col = mix(col, vec3(0.8, 0.0, 0.0), uDamage * 0.35 * smoothstep(0.2, 0.7, length(v)));
  if (uPortal > 0.0) col = mix(col, vec3(0.6, 0.2, 0.9), uPortal * 0.4);
  fragColor = vec4(pow(col, vec3(1.0 / 2.2)), 1.0);
}
`;

export class Renderer {
  constructor(canvas, settings) {
    this.settings = settings;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, settings.pixelRatio));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.autoClear = true;
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(settings.fov, 1, 0.05, 1000);
    this.camera.rotation.order = 'YXZ';
    this.camera.layers.enable(2);
    this.tex = getBlockTextureArray();

    this.uniforms = {
      uTime: { value: 0 },
      uSunDir: { value: new THREE.Vector3(0.3, 1, 0.2).normalize() },
      uMoonDir: { value: new THREE.Vector3(0, -1, 0) },
      uSunColor: { value: new THREE.Color(1, 0.95, 0.85) },
      uDay: { value: 1 },
      uFogColor: { value: new THREE.Color(0.7, 0.8, 1) },
      uFogDensity: { value: 0.02 },
      uFogStart: { value: 60 },
      uCamPos: { value: new THREE.Vector3() },
      uNether: { value: 0 },
      uUnderwater: { value: 0 },
      uDirect: { value: 1 },
      uShadows: { value: 0 },
      uFancy: { value: 1 },
      uShadowMap: { value: null },
      uShadowMatrix: { value: new THREE.Matrix4() },
      uShadowTexel: { value: 1 / 2048 },
      uSkyTop: { value: new THREE.Color(0.3, 0.5, 0.9) },
      uSkyHorizon: { value: new THREE.Color(0.7, 0.8, 1) },
      uGamma: { value: 1.0 },
      uTex: { value: this.tex },
      uWaterLayer: { value: TEX_INDEX.water_still },
      tScene: { value: null },
      uRefract: { value: 0 },
      uResolution: { value: new THREE.Vector2(1, 1) },
      uProj: { value: new THREE.Matrix4() },
    };
    const u = this.uniforms;
    this.solidMat = new THREE.ShaderMaterial({ uniforms: u, vertexShader: SOLID_VS, fragmentShader: SOLID_FS, glslVersion: THREE.GLSL3 });
    this.waterMat = new THREE.ShaderMaterial({
      uniforms: u, vertexShader: WATER_VS, fragmentShader: WATER_FS, glslVersion: THREE.GLSL3,
      transparent: true, depthWrite: false, side: THREE.DoubleSide,
    });
    this.shadowChunkMat = new THREE.ShaderMaterial({ uniforms: u, vertexShader: SHADOW_VS, fragmentShader: SHADOW_FS, glslVersion: THREE.GLSL3, side: THREE.DoubleSide });
    this.shadowEntityMat = new THREE.MeshBasicMaterial({ color: 0xffffff });

    // Sky
    const skyGeo = new THREE.SphereGeometry(1, 32, 16);
    this.sky = new THREE.Mesh(skyGeo, new THREE.ShaderMaterial({ uniforms: u, vertexShader: SKY_VS, fragmentShader: SKY_FS, glslVersion: THREE.GLSL3, side: THREE.BackSide, depthWrite: false, depthTest: false }));
    this.sky.scale.setScalar(500);
    this.sky.renderOrder = -10;
    this.sky.frustumCulled = false;
    this.scene.add(this.sky);
    // Clouds
    const cg = new THREE.PlaneGeometry(800, 800);
    cg.rotateX(-Math.PI / 2);
    this.clouds = new THREE.Mesh(cg, new THREE.ShaderMaterial({ uniforms: u, vertexShader: CLOUD_VS, fragmentShader: CLOUD_FS, glslVersion: THREE.GLSL3, transparent: true, depthWrite: false, side: THREE.DoubleSide }));
    this.clouds.position.y = 150;
    this.clouds.frustumCulled = false;
    this.clouds.renderOrder = 5;
    this.scene.add(this.clouds);

    // Shadow map
    this.shadowSize = 2048;
    this.shadowTarget = this.makeShadowTarget(this.shadowSize);
    this.shadowCam = new THREE.OrthographicCamera(-80, 80, 80, -80, 1, 600);
    this.shadowCam.layers.set(1);

    // Post
    this.postScene = new THREE.Scene();
    this.postCam = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
    this.postUniforms = {
      tColor: { value: null }, tDepth: { value: null },
      uSunScreen: { value: new THREE.Vector2() }, uSunVisible: { value: 0 }, uRays: { value: 1 },
      uTime: u.uTime, uUnderwater: u.uUnderwater, uInLava: { value: 0 }, uRayColor: { value: new THREE.Color(1, 0.9, 0.7) },
      uAspect: { value: 1 }, uGamma: u.uGamma, uDamage: { value: 0 }, uPortal: { value: 0 },
    };
    this.postQuad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), new THREE.ShaderMaterial({ uniforms: this.postUniforms, vertexShader: POST_VS, fragmentShader: POST_FS, glslVersion: THREE.GLSL3, depthTest: false, depthWrite: false }));
    this.postQuad.frustumCulled = false;
    this.postScene.add(this.postQuad);
    this.rt = null;

    // Selection box + crack overlay
    const box = new THREE.BoxGeometry(1.004, 1.004, 1.004);
    this.selection = new THREE.LineSegments(new THREE.EdgesGeometry(box), new THREE.LineBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.55 }));
    this.selection.visible = false;
    this.scene.add(this.selection);
    this.crackMat = new THREE.ShaderMaterial({
      uniforms: { uTex: u.uTex, uLayer: { value: TEX_INDEX.destroy_stage_0 } },
      vertexShader: `varying vec2 vUv; void main(){ vUv = vec2(uv.x, 1.0 - uv.y); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
      fragmentShader: `precision highp sampler2DArray; uniform sampler2DArray uTex; uniform float uLayer; varying vec2 vUv; void main(){ vec4 t = texture(uTex, vec3(vUv, uLayer)); if (t.a < 0.1) discard; gl_FragColor = vec4(0.0,0.0,0.0,0.6); }`,
      transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -1,
    });
    this.crack = new THREE.Mesh(new THREE.BoxGeometry(1.002, 1.002, 1.002), this.crackMat);
    this.crack.visible = false;
    this.scene.add(this.crack);

    // copy pass: scene colour + linear view depth for water refraction / reflections
    this.copyUniforms = { tColor: { value: null }, tDepth: { value: null }, uNear: { value: 0.05 }, uFar: { value: 1000 } };
    this.copyScene = new THREE.Scene();
    const cq = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), new THREE.ShaderMaterial({
      uniforms: this.copyUniforms, depthTest: false, depthWrite: false,
      vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }',
      fragmentShader: `uniform sampler2D tColor; uniform sampler2D tDepth; uniform float uNear; uniform float uFar; varying vec2 vUv;
        void main(){ float d = texture2D(tDepth, vUv).r; float z = d * 2.0 - 1.0;
        float lin = d >= 1.0 ? 1000.0 : (2.0 * uNear * uFar) / (uFar + uNear - z * (uFar - uNear));
        gl_FragColor = vec4(texture2D(tColor, vUv).rgb, lin); }`,
    }));
    cq.frustumCulled = false;
    this.copyScene.add(cq);

    this.applySettings();
    this.resize();
    window.addEventListener('resize', () => this.resize());
  }

  makeShadowTarget(size) {
    const t = new THREE.WebGLRenderTarget(size, size, { depthBuffer: true });
    t.depthTexture = new THREE.DepthTexture(size, size);
    t.depthTexture.type = THREE.UnsignedIntType;
    t.depthTexture.minFilter = t.depthTexture.magFilter = THREE.NearestFilter;
    return t;
  }

  applySettings() {
    const s = this.settings;
    const q = s.quality; // 0 low, 1 fancy, 2 shaders
    this.uniforms.uFancy.value = q >= 1 ? 1 : 0;
    this.uniforms.uShadows.value = q >= 2 && s.shadows ? 1 : 0;
    this.postEnabled = q >= 2;
    this.uniforms.uDirect.value = this.postEnabled ? 0 : 1;
    this.postUniforms.uRays.value = s.godRays ? 1 : 0;
    this.uniforms.uGamma.value = 0.9 + s.brightness * 0.6;
    this.camera.fov = s.fov;
    this.camera.updateProjectionMatrix();
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, s.pixelRatio));
    this.resize();
  }

  resize() {
    const w = window.innerWidth, h = window.innerHeight;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.postUniforms.uAspect.value = w / h;
    const pr = this.renderer.getPixelRatio();
    if (this.rt) this.rt.dispose();
    this.rt = new THREE.WebGLRenderTarget(Math.floor(w * pr), Math.floor(h * pr), { type: THREE.HalfFloatType, depthBuffer: true });
    this.rt.depthTexture = new THREE.DepthTexture(Math.floor(w * pr), Math.floor(h * pr));
    this.rt.depthTexture.type = THREE.UnsignedIntType;
    this.postUniforms.tColor.value = this.rt.texture;
    this.postUniforms.tDepth.value = this.rt.depthTexture;
    if (this.rtScene) this.rtScene.dispose();
    this.rtScene = new THREE.WebGLRenderTarget(Math.floor(w * pr), Math.floor(h * pr), { type: THREE.HalfFloatType, depthBuffer: false });
    this.uniforms.tScene.value = this.rtScene.texture;
    this.uniforms.uResolution.value.set(Math.floor(w * pr), Math.floor(h * pr));
    if (this.copyUniforms) { this.copyUniforms.tColor.value = this.rt.texture; this.copyUniforms.tDepth.value = this.rt.depthTexture; }
  }

  // Update time-of-day colours. dayTime in [0,1): 0 = sunrise, 0.25 noon, 0.5 sunset, 0.75 midnight.
  updateEnvironment(dayTime, dim, underwater, renderDist, biome) {
    const u = this.uniforms;
    const nether = dim === 'nether';
    u.uNether.value = nether ? 1 : 0;
    u.uUnderwater.value = underwater ? 1 : 0;
    const ang = dayTime * Math.PI * 2;
    const sun = new THREE.Vector3(Math.cos(ang), Math.sin(ang), 0.25).normalize();
    u.uSunDir.value.copy(sun);
    u.uMoonDir.value.copy(sun).multiplyScalar(-1);
    const day = THREE.MathUtils.smoothstep(sun.y, -0.18, 0.25);
    u.uDay.value = nether ? 0 : day;
    const sunset = Math.max(0, 1 - Math.abs(sun.y) * 3.5);
    const top = new THREE.Color(0.02, 0.025, 0.06).lerp(new THREE.Color(0.32, 0.55, 0.95), day);
    const hor = new THREE.Color(0.04, 0.05, 0.1).lerp(new THREE.Color(0.72, 0.83, 1.0), day);
    hor.lerp(new THREE.Color(1.0, 0.55, 0.3), sunset * 0.6 * (sun.x > 0 ? 1 : 1));
    u.uSkyTop.value.copy(top);
    u.uSkyHorizon.value.copy(hor);
    u.uSunColor.value.setRGB(1.0, 0.92 - sunset * 0.35, 0.82 - sunset * 0.5).multiplyScalar(day * 1.15);
    const fogDist = renderDist * 16;
    if (nether) {
      const NF = { crimson_forest: [0.3, 0.03, 0.03], warped_forest: [0.06, 0.12, 0.14], soul_sand_valley: [0.1, 0.17, 0.2] };
      const t = NF[biome] || [0.24, 0.05, 0.03];
      this.netherFog = this.netherFog || new THREE.Color(t[0], t[1], t[2]);
      this.netherFog.lerp(new THREE.Color(t[0], t[1], t[2]), 0.02);
      u.uFogColor.value.copy(this.netherFog);
      u.uFogStart.value = 8;
      u.uFogDensity.value = 1 / Math.min(70, fogDist);
    } else if (underwater) {
      u.uFogColor.value.setRGB(0.05, 0.18, 0.35).multiplyScalar(0.3 + day * 0.7);
    } else {
      u.uFogColor.value.copy(hor);
      u.uFogStart.value = fogDist * 0.55;
      u.uFogDensity.value = 1 / (fogDist * 0.42);
    }
    this.sky.visible = !nether;
    this.clouds.visible = !nether && this.settings.quality >= 1;
    this.postUniforms.uRayColor.value.setRGB(1.0, 0.85 - sunset * 0.3, 0.6 - sunset * 0.3).multiplyScalar(0.35 + sunset * 0.6);
  }

  updateShadows(center) {
    const u = this.uniforms;
    if (!u.uShadows.value || u.uNether.value || u.uDay.value < 0.02) return false;
    const sun = u.uSunDir.value;
    const size = this.settings.shadowDistance;
    const cam = this.shadowCam;
    cam.left = -size; cam.right = size; cam.top = size; cam.bottom = -size;
    cam.updateProjectionMatrix();
    // snap to texel grid to avoid shimmering
    const texel = (size * 2) / this.shadowSize;
    cam.position.copy(center).addScaledVector(sun, 250);
    cam.up.set(0, 1, 0);
    cam.lookAt(center);
    cam.updateMatrixWorld();
    const inv = cam.matrixWorldInverse;
    const c = center.clone().applyMatrix4(inv);
    c.x = Math.round(c.x / texel) * texel;
    c.y = Math.round(c.y / texel) * texel;
    const snapped = c.applyMatrix4(cam.matrixWorld);
    cam.position.copy(snapped).addScaledVector(sun, 250);
    cam.lookAt(snapped);
    cam.updateMatrixWorld();
    u.uShadowMatrix.value.multiplyMatrices(cam.projectionMatrix, cam.matrixWorldInverse);
    u.uShadowTexel.value = 1 / this.shadowSize;
    return true;
  }

  render(dt, extra) {
    const u = this.uniforms;
    u.uTime.value += dt;
    u.uCamPos.value.copy(this.camera.position);
    this.sky.position.copy(this.camera.position);
    this.clouds.position.x = this.camera.position.x;
    this.clouds.position.z = this.camera.position.z;
    const r = this.renderer;

    // Shadow pass
    if (this.updateShadows(extra.shadowCenter)) {
      const swapped = [];
      this.scene.traverseVisible((o) => {
        if (o.isMesh && o.layers.isEnabled(1)) {
          swapped.push(o, o.material);
          o.material = o.userData.chunk ? this.shadowChunkMat : this.shadowEntityMat;
        }
      });
      const skyVis = this.sky.visible, cloudVis = this.clouds.visible;
      this.sky.visible = false; this.clouds.visible = false;
      r.setRenderTarget(this.shadowTarget);
      r.setClearColor(0x000000, 1);
      r.clear();
      r.render(this.scene, this.shadowCam);
      r.setRenderTarget(null);
      for (let i = 0; i < swapped.length; i += 2) swapped[i].material = swapped[i + 1];
      this.sky.visible = skyVis; this.clouds.visible = cloudVis;
      u.uShadowMap.value = this.shadowTarget.depthTexture;
    }

    const fog = u.uFogColor.value;
    r.setClearColor(fog, 1);
    const cam = this.camera;
    if (this.postEnabled) {
      r.setRenderTarget(this.rt);
      r.clear();
      const realWater = this.settings.realWater !== false;
      u.uRefract.value = realWater ? 1 : 0;
      if (realWater) {
        // opaque pass, copy colour+depth, then water on top sampling the copy
        cam.layers.set(0);
        r.render(this.scene, cam);
        r.setRenderTarget(this.rtScene);
        this.copyUniforms.uNear.value = cam.near;
        this.copyUniforms.uFar.value = cam.far;
        r.render(this.copyScene, this.postCam);
        r.setRenderTarget(this.rt);
        u.uProj.value.copy(cam.projectionMatrix);
        cam.layers.set(2);
        const ac = r.autoClear;
        r.autoClear = false;
        r.render(this.scene, cam);
        r.autoClear = ac;
        cam.layers.set(0);
        cam.layers.enable(2);
      } else {
        r.render(this.scene, cam);
      }
      r.setRenderTarget(null);
      // sun screen position for god rays
      const sunWorld = this.camera.position.clone().addScaledVector(u.uSunDir.value, 400);
      const sp = sunWorld.project(this.camera);
      const camDir = new THREE.Vector3();
      this.camera.getWorldDirection(camDir);
      const facing = camDir.dot(u.uSunDir.value);
      let vis = facing > 0 ? THREE.MathUtils.smoothstep(facing, 0.0, 0.5) : 0;
      vis *= THREE.MathUtils.smoothstep(u.uSunDir.value.y, -0.05, 0.12);
      if (u.uNether.value || u.uUnderwater.value) vis = 0;
      this.postUniforms.uSunScreen.value.set(sp.x * 0.5 + 0.5, sp.y * 0.5 + 0.5);
      this.postUniforms.uSunVisible.value = vis;
      this.postUniforms.uInLava.value = extra.inLava ? 1 : 0;
      this.postUniforms.uDamage.value = extra.damage || 0;
      this.postUniforms.uPortal.value = extra.portal || 0;
      r.render(this.postScene, this.postCam);
    } else {
      r.render(this.scene, this.camera);
    }
  }
}
