import RAPIER from '@dimforge/rapier3d-compat';
import type { Box, RoomData, Vec3 } from '@doors/shared';
import { PLAYER } from '@doors/shared';

/**
 * Rapier physics: static room colliders (loaded/unloaded with rooms), dynamic door blockers,
 * and a kinematic character controller with crouching and stair autostep.
 */
export class Physics {
  world!: RAPIER.World;
  private ctrl!: RAPIER.KinematicCharacterController;
  private body!: RAPIER.RigidBody;
  private collider!: RAPIER.Collider;
  private rooms = new Map<number, RAPIER.Collider[]>();
  private dyn = new Map<string, RAPIER.Collider>();
  crouched = false;
  grounded = false;
  private vy = 0;
  ready = false;

  async init() {
    await RAPIER.init();
    this.world = new RAPIER.World({ x: 0, y: -9.81, z: 0 });
    this.ctrl = this.world.createCharacterController(0.02);
    this.ctrl.enableAutostep(0.36, 0.15, true);
    this.ctrl.enableSnapToGround(0.35);
    this.ctrl.setMaxSlopeClimbAngle((50 * Math.PI) / 180);
    this.ctrl.setApplyImpulsesToDynamicBodies(false);
    this.body = this.world.createRigidBody(RAPIER.RigidBodyDesc.kinematicPositionBased().setTranslation(0, 1, 0));
    this.collider = this.world.createCollider(RAPIER.ColliderDesc.capsule(this.halfH(false), PLAYER.radius), this.body);
    this.ready = true;
  }

  private halfH(crouch: boolean) {
    return ((crouch ? PLAYER.crouchHeight : PLAYER.height) - 2 * PLAYER.radius) / 2;
  }
  private centerOffset(crouch: boolean) { return this.halfH(crouch) + PLAYER.radius; }

  private cuboid(b: Box): RAPIER.Collider {
    const hx = (b.maxX - b.minX) / 2, hy = (b.maxY - b.minY) / 2, hz = (b.maxZ - b.minZ) / 2;
    return this.world.createCollider(RAPIER.ColliderDesc.cuboid(Math.max(hx, 0.005), Math.max(hy, 0.005), Math.max(hz, 0.005)).setTranslation((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2));
  }

  addRoom(room: RoomData) {
    if (this.rooms.has(room.index)) return;
    const list: RAPIER.Collider[] = [];
    for (const b of room.colliders) list.push(this.cuboid(b));
    for (const b of room.crouchColliders) list.push(this.cuboid(b));
    this.rooms.set(room.index, list);
  }
  removeRoom(index: number) {
    const list = this.rooms.get(index);
    if (!list) return;
    for (const c of list) this.world.removeCollider(c, false);
    this.rooms.delete(index);
  }
  hasRoom(i: number) { return this.rooms.has(i); }

  /** Add/remove a named dynamic blocker (doors, gates). */
  setBlocker(key: string, b: Box | null) {
    const ex = this.dyn.get(key);
    if (ex) { this.world.removeCollider(ex, false); this.dyn.delete(key); }
    if (b) this.dyn.set(key, this.cuboid(b));
  }

  teleport(feet: Vec3) {
    this.body.setTranslation({ x: feet.x, y: feet.y + this.centerOffset(this.crouched) + 0.02, z: feet.z }, true);
    this.body.setNextKinematicTranslation({ x: feet.x, y: feet.y + this.centerOffset(this.crouched) + 0.02, z: feet.z });
    this.vy = 0;
  }

  feet(): Vec3 {
    const t = this.body.translation();
    return { x: t.x, y: t.y - this.centerOffset(this.crouched), z: t.z };
  }

  /** Try to crouch/stand. Standing up fails if there is no headroom. */
  setCrouch(want: boolean): boolean {
    if (want === this.crouched) return true;
    const feet = this.feet();
    if (!want) {
      const hh = this.halfH(false);
      const center = { x: feet.x, y: feet.y + hh + PLAYER.radius + 0.03, z: feet.z };
      let blocked = false;
      this.world.intersectionsWithShape(center, { x: 0, y: 0, z: 0, w: 1 }, new RAPIER.Capsule(hh, PLAYER.radius - 0.02), () => { blocked = true; return false; }, undefined, undefined, this.collider);
      if (blocked) return false;
    }
    this.crouched = want;
    this.collider.setHalfHeight(this.halfH(want));
    const off = this.centerOffset(want);
    this.body.setTranslation({ x: feet.x, y: feet.y + off, z: feet.z }, true);
    this.body.setNextKinematicTranslation({ x: feet.x, y: feet.y + off, z: feet.z });
    return true;
  }

  /** Move the character by a horizontal displacement; gravity applied internally. */
  move(dx: number, dz: number, dt: number): Vec3 {
    this.vy = this.grounded ? -1 : Math.max(-20, this.vy - 9.81 * dt);
    const desired = { x: dx, y: this.vy * dt, z: dz };
    this.ctrl.computeColliderMovement(this.collider, desired);
    const mv = this.ctrl.computedMovement();
    this.grounded = this.ctrl.computedGrounded();
    const t = this.body.translation();
    const next = { x: t.x + mv.x, y: t.y + mv.y, z: t.z + mv.z };
    this.body.setNextKinematicTranslation(next);
    this.body.setTranslation(next, true);
    this.world.step();
    return this.feet();
  }

  /** Raycast against the world (excluding the player). Returns hit distance or null. */
  ray(o: Vec3, d: Vec3, max: number): number | null {
    const hit = this.world.castRay(new RAPIER.Ray(o, d), max, true, undefined, undefined, this.collider);
    return hit ? hit.timeOfImpact : null;
  }
}
