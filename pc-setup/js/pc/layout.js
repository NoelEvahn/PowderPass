// Shared dimensions (meters) for the NZXT H6 Flow and the parts inside it.
// Case-local frame: origin at floor centre of the footprint,
//  +X → right side (rear chamber),  -X → left tempered glass,
//  +Z → front glass,  -Z → rear I/O,  +Y → up.
import * as THREE from 'three';

export const CASE = {
	W: 0.287, D: 0.415, H: 0.435,
	feet: 0.015,
	sheet: 0.0012,          // steel sheet thickness
	glassT: 0.004,          // tempered glass thickness
	trayX: 0.032,           // motherboard tray surface (faces -X)
};
CASE.xL = - CASE.W / 2; CASE.xR = CASE.W / 2;
CASE.zB = - CASE.D / 2; CASE.zF = CASE.D / 2;

// Motherboard (ATX 305 x 244). Board-space: u = mm from rear edge toward the front,
// v = mm from top edge downward, h = height above PCB surface (toward -X).
export const BOARD = {
	w: 0.244, h: 0.305, t: 0.0016,
	rearZ: - 0.19, topY: 0.405, surfX: 0.024,
};

export function bpos( u, v, h = 0 ) {

	return new THREE.Vector3( BOARD.surfX - h, BOARD.topY - v, BOARD.rearZ + u );

}

export const SOCKET = { u: 0.1, v: 0.095 };
export const DIMM = { v0: 0.034, len: 0.13335, us: [ 0.150, 0.1585, 0.170, 0.1785 ], used: [ 1, 3 ] };
export const PCIE1 = { v: 0.178, u0: 0.03 };
export const M2_1 = { v: 0.157, u0: 0.045 };

// Radiator (DeepCool LQ360: 402 x 120 x 27 mm) mounted under the top panel.
export const RAD = { len: 0.402, w: 0.12, t: 0.027, fanT: 0.025, xC: - 0.0793, yTop: 0.427 };

// PSU (Corsair RMe 750W: 150 x 86 x 140 mm) in the rear chamber, fan facing the right panel.
export const PSU = { w: 0.150, h: 0.086, l: 0.140 };

// GPU (ASUS PRIME RX 9060 XT: 304 x 126 x 50 mm)
export const GPU = { L: 0.304, H: 0.126, T: 0.05 };
