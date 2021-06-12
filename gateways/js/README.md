# fjåge JavaScript Gateway (fjage.js)

A fjåge Gateway implementation in JavaScript supports both browser (WebSocket) and Node.js (TCP) based connections to a fjåge [Master Container](https://fjage.readthedocs.io/en/latest/remote.html#master-and-slave-containers).

## Installation

```sh
$ npm install fjage
```

## Documentation

The API documentation of the latest version of fjage.js is published at https://org-arl.github.io/fjage/jsdoc/

## Usage

A distribution-ready bundle is available for types of module systems commonly used in the JS world. Examples of how to use it for the different module systems are available in the [examples](/examples) directory.

At runtime, fjage.js will check its context (browser or Node.js) and accordingly use the appropriate `Connector` for connecting to the master container.

### [CommonJS](dist/cjs)

```
const { Performative, AgentID, Message, Gateway, MessageClass } = require('fjage');
const shell = new AgentID('shell');
const gw = new Gateway({
    hostname: 'localhost',
    port : '5081',
});
```

### [ECMAScript modules](dist/esm)

```
import { Performative, AgentID, Message, Gateway, MessageClass } from 'fjage.js'
const shell = new AgentID('shell');
const gw = new Gateway({
    hostname: 'localhost',
    port : '5081',
});
```

### [UMD](dist)
```
<script src="fjage.min.js"></script>
<script>
    const shell = new fjage.AgentID('shell');
    const gw = new fjage.Gateway({
        hostname: 'localhost',
        port : '8080',
        pathname: '/ws/'
    });
</script>
```