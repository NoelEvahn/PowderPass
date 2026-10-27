import { generateRun, validateRun } from '../shared/src/index';
const t0 = Date.now();
let total = 0;
const counts: Record<string, number> = {};
for (let s = 1; s <= 30; s++) {
  const run = generateRun(s * 7919);
  const issues = validateRun(run);
  total += issues.length;
  for (const i of issues) { const k = i.msg.replace(/\d+:\S+/g, '').replace(/\d+/g, 'N'); counts[k] = (counts[k] || 0) + 1; }
  if (s <= 3) console.log(s, issues.slice(0, 8).map((i) => `#${i.room}(${run.rooms[i.room]?.kind}) ${i.msg}`));
}
console.log('time', Date.now() - t0, 'issues', total, counts);
