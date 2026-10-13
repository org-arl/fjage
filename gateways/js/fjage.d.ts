import './dist/esm/message.js';

export as namespace fjage;
export * from './dist/esm/fjage.js';

declare module './dist/esm/message.js' {
  interface GenericMessage {
    [key: string]: unknown;
  }
}
