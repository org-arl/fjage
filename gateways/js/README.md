# fjåge JavaScript Gateway (fjage.js)

![npm](https://img.shields.io/npm/v/fjage)

A fjåge Gateway implementation in JavaScript supports both browser (WebSocket) and Node.js (TCP) based connections to a fjåge [Master Container](https://fjage.readthedocs.io/en/latest/remote.html#master-and-slave-containers).

## Versions

fjage.js is included as a part of the [fjage.jar](https://search.maven.org/artifact/com.github.org-arl/fjage) package and also available seperately as a [npm package](https://www.npmjs.com/package/fjage.js).

> Note: The latest version of fjage.jar, 1.9.1 is bundled with a fjage.js versioned as 1.9.1-rc5 because of some issues in publishing. The future versions of fjage.jar will contain the same version of fjage.js. Due to this publishing issue as well, fjage.js 1.9.1 is unavailable and fjage.js 1.9.1-rc6 should instead.


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

```js
const { Performative, AgentID, Message, Gateway, MessageClass } = require('fjage');
const shell = new AgentID('shell');
const gw = new Gateway({
    hostname: 'localhost',
    port : '5081',
});
```

### [ECMAScript modules](dist/esm)

```js
import { Performative, AgentID, Message, Gateway, MessageClass } from 'fjage.js'
const shell = new AgentID('shell');
const gw = new Gateway({
    hostname: 'localhost',
    port : '5081',
});
```

### [UMD](dist)
```js
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