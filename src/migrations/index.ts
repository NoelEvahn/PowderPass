import * as migration_20261023_004544_initial from './20261023_004544_initial';

export const migrations = [
  {
    up: migration_20261023_004544_initial.up,
    down: migration_20261023_004544_initial.down,
    name: '20261023_004544_initial'
  },
];
