const fs = require('fs');
const vm = require('vm');

describe('Minified fjage.js', function () {
  it('should serialize an unregistered message with its own class name', function () {
    const context = { console, crypto };
    context.globalThis = context;
    vm.runInNewContext(fs.readFileSync('dist/fjage.min.js', 'utf8'), context);
    const { Message } = context.fjage;
    class UnregisteredMessage extends Message { value = null; }
    const message = new UnregisteredMessage();
    message.value = 1;
    const json = message.toJSON();
    expect(json.clazz).toBe('UnregisteredMessage');
    expect(json.data.value).toBe(1);
  });
});
