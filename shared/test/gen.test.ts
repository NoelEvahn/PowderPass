import { describe, expect, it } from 'vitest';
import { generateRun, validateRun } from '../src';

describe('procedural generation', () => {
  it('is deterministic for the same seed', () => {
    const a = generateRun(12345), b = generateRun(12345);
    expect(JSON.stringify(a)).toBe(JSON.stringify(b));
  });
  it('differs between seeds', () => {
    const a = generateRun(1), b = generateRun(2);
    expect(a.rooms.map((r) => r.kind).join()).not.toBe(b.rooms.map((r) => r.kind).join());
  });
  it('produces valid, solvable runs across many seeds', () => {
    const failures: string[] = [];
    for (let s = 1; s <= 60; s++) {
      const run = generateRun(s * 7919);
      const issues = validateRun(run);
      if (issues.length) failures.push(`seed ${s * 7919}: ` + issues.slice(0, 4).map((i) => `#${i.room} ${i.msg}`).join('; '));
    }
    expect(failures).toEqual([]);
  });
});
