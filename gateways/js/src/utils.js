/* global Buffer */

import { isBrowser, isNode, isJsDom, isWebWorker } from 'browser-or-node';

////// private utilities

// generate random ID with length 4*len characters
/**
 *
 * @private
 * @param {number} len
 */
export function _guid(len) {
  const s4 = () => Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
  return Array.from({ length: len }, s4).join('');
}

// node.js safe atob function
/**
 * @private
 * @param {string} a
 */
export function _atob(a){
  if (isBrowser || isWebWorker) return window.atob(a);
  else if (isJsDom || isNode) return Buffer.from(a, 'base64').toString('binary');
}