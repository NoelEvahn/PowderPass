import * as migration_20261027_013112_initial from './20261027_013112_initial';

export const migrations = [
  {
    up: migration_20261027_013112_initial.up,
    down: migration_20261027_013112_initial.down,
    name: '20261027_013112_initial'
  },
];
