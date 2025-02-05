/* global Buffer */

import { isBrowser, isNode, isJsDom, isWebWorker } from '../node_modules/browser-or-node/src/index.js';

////// private utilities

// generate random ID with length 4*len characters
/** @private */
export function _guid(len) {
    function s4() {
      return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
    }
    let s = s4();
    for (var i = 0; i < len-1; i++)
      s += s4();
    return s;
}

// node.js safe atob function
/** @private */
// TODO: Check if this can be replaced by Uint8Array.fromBase64()
export function _atob(a){
    if (isBrowser || isWebWorker) return window.atob(a);
    else if (isJsDom || isNode) return Buffer.from(a, 'base64').toString('binary');
}