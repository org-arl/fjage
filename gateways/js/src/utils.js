////// common utilities

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