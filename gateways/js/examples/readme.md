# Examples of using fjage.js

This directory contains examples of how to use the fjage.js in the various module system it's supported in.

The example code in all the examples sends a `ShellExecReq` to the fjåge Master Container which does some simple calculations and returns the result to the fjage.js Gateway which is then printed on the console.

For the browser-based examples, you'll need to run a local web-server e.g. [http-server](https://github.com/http-party/http-server). You could run it in the examples directory as with the command `npx http-server -p 8000`.

## Installing fjage.js

To be able to install fjage.js, you'll need to **copy this examples directory outside the fjage directory**. Once you are done copying this directory out, install fjage.js in this `examples` directory using `npm install fjage`.

## Starting a fjåge Master Container

To use any of the examples in this directory, you will need to run a fjåge Master Container. The easiest way to do this is : 

1. Build all the fjåge jars using `gradle` in the fjåge root directory.
2. Run a web-enabled fjåge instance using `./fjage.sh -web` in the fjåge root directory.

## CommonJS

[example.js](example.js) is a simple Node.js script. It can be run using `node example.js`. It uses the CommonJS version of fjage.js (dist/cjs/fjage.js).

> Message {
>  __clazz__: 'org.arl.fjage.GenericMessage',
>  msgID: 'f0716464-e7ef-4e1d-8391-722d633ef573',
>  sender: 'shell',
>  recipient: 'NodeGW-48b30f47d61d38bc',
>  perf: 'AGREE',
>  inReplyTo: 'd3371ad7586ee06457e5ceef117ef369',
>  ans: 4
}


## ECMAScript modules

[example-esm.html](example-esm.html) is a simple HTML page. If you have the local web-server running as above, you can access it at [http://localhost:8000/example-esm.html](http://localhost:8000/example-esm.html). The response from fjåge Master Container is will be printed in the browser console.

> Message {__clazz__: "org.arl.fjage.GenericMessage", msgID: "452e851a-69cd-4782-acc4-7c71135333e1", sender: "shell", recipient: "WebGW-", perf: "AGREE", …}

As in this example web page, the ESM version of fjage.js (dist/esm/fjage.js) can be imported into your script using the `import` statement, as long as the script tag has the `type="module"` attribute. This follows the standard ECMAScript modules syntax.

## ECMAScript modules (bundle as UMD)

Before running this example, you'll need to ensure that the `bundle.js` is generated using `npx rollup app.js --file bundle.js --format umd` in this directory. 

[example-bundle.html](example-bundle.html) is a simple HTML page. If you have the local web-server running as above, you can access it at [http://localhost:8000/example-bundle.html](http://localhost:8000/example-esm.html). The response from fjåge Master Container is will be printed in the browser console.

> Message {__clazz__: "org.arl.fjage.GenericMessage", msgID: "452e851a-69cd-4782-acc4-7c71135333e1", sender: "shell", recipient: "WebGW-", perf: "AGREE", …}

The ESM version of fjage.js (dist/esm/fjage.js) can also be used to create bundles using some bundling tools like [rollup](https://rollupjs.org). We have an example `app.js` which uses the ESM version of fjage.js using the `import` statement. This can be bundled with its dependencies into `bundle.js`. Running `npx rollup app.js --file bundle.js --format umd` in this directory will do this for you.

## UMD

[example-umd.html](example-umd.html) is a simple HTML page. If you have the local web-server running as above, you can access it at [http://localhost:8000/example-esm.html](http://localhost:8000/example-umd.html). The response from fjåge Master Container is will be printed in the browser console.

> Message {__clazz__: "org.arl.fjage.GenericMessage", msgID: "452e851a-69cd-4782-acc4-7c71135333e1", sender: "shell", recipient: "WebGW-", perf: "AGREE", …}

As in this example web page, fjage.js can be used in your script by having a separate script tag to import the UMD version (dist/fjage.min.js). Once this script is run, all the fjage.js classes ( Performative, AgentID, Message, Gateway, MessageClass, etc) are available to your script under the namespace `fjage`.

