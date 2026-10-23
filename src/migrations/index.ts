import * as migration_20261023_004544_initial from './20261023_004544_initial';
import * as migration_20261023_042234_motion_options from './20261023_042234_motion_options';

export const migrations = [
  {
    up: migration_20261023_004544_initial.up,
    down: migration_20261023_004544_initial.down,
    name: '20261023_004544_initial',
  },
  {
    up: migration_20261023_042234_motion_options.up,
    down: migration_20261023_042234_motion_options.down,
    name: '20261023_042234_motion_options'
  },
];
