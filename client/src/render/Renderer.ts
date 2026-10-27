import * as THREE from 'three';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { GTAOPass } from 'three/examples/jsm/postprocessing/GTAOPass.js';
import { setTextureQuality } from '../assets/textures';

export type QualityName = 'low' | 'medium' | 'high';
export interface QualityPreset {
  name: QualityName; level: number; pixelRatio: number; shadows: boolean; shadowLights: number; shadowSize: number;
  lightPool: number; bloom: boolean; ao: boolean; texSize: number; aniso: number; post: boolean; antialias: boolean;
}
export const PRESETS: Record<QualityName, QualityPreset> = {
  low: { name: 'low', level: 0, pixelRatio: 0.75, shadows: false, shadowLights: 0, shadowSize: 256, lightPool: 4, bloom: false, ao: false, texSize: 256, aniso: 1, post: true, antialias: false },
  medium: { name: 'medium', level: 1, pixelRatio: 1, shadows: true, shadowLights: 0, shadowSize: 512, lightPool: 6, bloom: true, ao: false, texSize: 512, aniso: 4, post: true, antialias: false },
  high: { name: 'high', level: 2, pixelRatio: 1.5, shadows: true, shadowLights: 1, shadowSize: 1024, lightPool: 10, bloom: true, ao: true, texSize: 1024, aniso: 8, post: true, antialias: true },
};

export function detectQuality(): QualityName {
  const mobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent) || (navigator.maxTouchPoints > 1 && window.innerWidth < 1100);
  if (mobile) return 'low';
  const cores = navigator.hardwareConcurrency ?? 4;
  return cores >= 8 ? 'high' : 'medium';
}

/** Cinematic grading: vignette, grain, chromatic aberration, damage pulse, tints and distortion. */
const GradeShader = {
  uniforms: {
    tDiffuse: { value: null }, time: { value: 0 }, vignette: { value: 1.0 }, grain: { value: 0.03 }, damage: { value: 0 },
    aberration: { value: 0.0015 }, tint: { value: new THREE.Vector3(1, 1, 1) }, distort: { value: 0 }, exposure: { value: 1 }, darkness: { value: 0 },
  },
  vertexShader: `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }`,
  fragmentShader: `
    uniform sampler2D tDiffuse; uniform float time, vignette, grain, damage, aberration, distort, exposure, darkness; uniform vec3 tint;
    varying vec2 vUv;
    float rand(vec2 c){ return fract(sin(dot(c, vec2(12.9898,78.233))) * 43758.5453); }
    void main(){
      vec2 uv = vUv;
      if (distort > 0.0) {
        uv.x += sin(uv.y * 40.0 + time * 30.0) * 0.006 * distort;
        uv.y += sin(uv.x * 22.0 + time * 17.0) * 0.004 * distort;
        if (rand(vec2(floor(uv.y * 40.0), floor(time * 20.0))) > 1.0 - distort * 0.3) uv.x += (rand(vec2(time)) - 0.5) * 0.08 * distort;
      }
      vec2 dir = uv - 0.5;
      float ab = aberration * (1.0 + damage * 6.0 + distort * 4.0);
      vec3 col;
      col.r = texture2D(tDiffuse, uv + dir * ab).r;
      col.g = texture2D(tDiffuse, uv).g;
      col.b = texture2D(tDiffuse, uv - dir * ab).b;
      col *= exposure;
      // gentle filmic warm grade
      col = mix(col, col * vec3(1.06, 0.98, 0.88), 0.5);
      col *= tint;
      float v = smoothstep(0.85, 0.2, length(dir) * vignette);
      col *= mix(0.25, 1.0, v);
      col = mix(col, vec3(0.35, 0.0, 0.0), damage * (1.0 - v) * 0.8);
      col *= 1.0 - darkness;
      col += (rand(uv * 1000.0 + time) - 0.5) * grain;
      gl_FragColor = vec4(col, 1.0);
    }`,
};

/** WebGL2 renderer with quality presets and post-processing chain. */
export class Renderer {
  renderer: THREE.WebGLRenderer;
  composer: EffectComposer | null = null;
  grade: ShaderPass | null = null;
  bloom: UnrealBloomPass | null = null;
  scene: THREE.Scene;
  camera: THREE.PerspectiveCamera;
  preset: QualityPreset;
  private ao: GTAOPass | null = null;
  fpsSamples: number[] = [];

  constructor(canvas: HTMLCanvasElement, quality: QualityName) {
    this.preset = PRESETS[quality];
    setTextureQuality(this.preset.texSize, this.preset.aniso);
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: this.preset.antialias, powerPreference: 'high-performance', stencil: false });
    if (!this.renderer.capabilities.isWebGL2) console.warn('WebGL2 not available; running in compatibility mode');
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.25;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.shadowMap.enabled = this.preset.shadows;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x020202);
    this.scene.fog = new THREE.FogExp2(0x0a0705, 0.07);
    this.camera = new THREE.PerspectiveCamera(75, 1, 0.05, 120);
    this.scene.add(this.camera);
    this.setupPost();
    this.resize();
    window.addEventListener('resize', () => this.resize());
    window.visualViewport?.addEventListener('resize', () => this.resize());
  }

  setQuality(q: QualityName) {
    this.preset = PRESETS[q];
    setTextureQuality(this.preset.texSize, this.preset.aniso);
    this.renderer.shadowMap.enabled = this.preset.shadows;
    this.setupPost();
    this.resize();
  }

  private setupPost() {
    this.composer?.dispose();
    this.composer = null; this.grade = null; this.bloom = null; this.ao = null;
    const p = this.preset;
    if (!p.post) return;
    const size = this.renderer.getSize(new THREE.Vector2());
    const comp = new EffectComposer(this.renderer);
    comp.addPass(new RenderPass(this.scene, this.camera));
    if (p.ao) {
      this.ao = new GTAOPass(this.scene, this.camera, Math.max(1, size.x), Math.max(1, size.y));
      this.ao.output = GTAOPass.OUTPUT.Default;
      this.ao.blendIntensity = 0.85;
      comp.addPass(this.ao);
    }
    if (p.bloom) {
      this.bloom = new UnrealBloomPass(new THREE.Vector2(Math.max(1, size.x / 2), Math.max(1, size.y / 2)), 0.55, 0.6, 0.82);
      comp.addPass(this.bloom);
    }
    this.grade = new ShaderPass(GradeShader);
    comp.addPass(this.grade);
    comp.addPass(new OutputPass());
    this.composer = comp;
  }

  resize() {
    const vv = window.visualViewport;
    const w = Math.floor(vv?.width ?? window.innerWidth), h = Math.floor(vv?.height ?? window.innerHeight);
    const pr = Math.min(window.devicePixelRatio || 1, this.preset.pixelRatio);
    this.renderer.setPixelRatio(pr);
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / Math.max(1, h);
    this.camera.updateProjectionMatrix();
    this.composer?.setPixelRatio(pr);
    this.composer?.setSize(w, h);
  }

  render(dt: number) {
    if (this.grade) this.grade.uniforms.time.value += dt;
    if (this.composer) this.composer.render(dt);
    else this.renderer.render(this.scene, this.camera);
    this.fpsSamples.push(dt);
    if (this.fpsSamples.length > 120) this.fpsSamples.shift();
  }

  get fps() {
    if (!this.fpsSamples.length) return 0;
    return this.fpsSamples.length / this.fpsSamples.reduce((a, b) => a + b, 0);
  }

  setGrade(u: Partial<Record<'vignette' | 'grain' | 'damage' | 'aberration' | 'distort' | 'exposure' | 'darkness', number>>, tint?: [number, number, number]) {
    if (!this.grade) return;
    for (const [k, v] of Object.entries(u)) this.grade.uniforms[k].value = v;
    if (tint) this.grade.uniforms.tint.value.set(...tint);
  }
}
