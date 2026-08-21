import { Performative } from './performative.js';

export const DEFAULT_PERF = Symbol('defaultPerf');
export const MESSAGE_REGISTRY = Object.create(null);

/**
 * Gets a registered message class by qualified or short name.
 *
 * @param {string} name - qualified or short message class name
 * @returns {Function|undefined} registered message class
 */
export function messageClassForName(name) {
  return MESSAGE_REGISTRY[name] || MESSAGE_REGISTRY[name.replace(/^.*\./, '')];
}

/**
 * Stores a message class in the internal registry.
 *
 * @param {string} className - qualified or unqualified message class name
 * @param {Function} messageClass - message class to register
 * @returns {Function} registered message class
 */
export function registerMessageClass(className, messageClass) {
  if (typeof className !== 'string' || className.trim() === '') {
    throw new Error('Message class name must be a non-empty string');
  }
  if (typeof messageClass !== 'function') {
    throw new Error('Message class must be a function');
  }

  const shortName = className.split('.').pop();
  if (MESSAGE_REGISTRY[className] && MESSAGE_REGISTRY[className] !== messageClass) {
    console.warn(`Overriding existing message class registered with name '${className}'`);
  }
  if (MESSAGE_REGISTRY[shortName] && MESSAGE_REGISTRY[shortName] !== messageClass) {
    console.warn(`Overriding existing message class registered with short name '${shortName}'`);
  }
  messageClass.prototype.__clazz__ = className;
  messageClass.prototype[DEFAULT_PERF] = shortName.toLowerCase().endsWith('req')
    ? Performative.REQUEST
    : Performative.INFORM;
  MESSAGE_REGISTRY[className] = messageClass;
  MESSAGE_REGISTRY[shortName] = messageClass;
  return messageClass;
}
