#!/usr/bin/env node
/*
Creates the specs to be used by Jasmine in both browser and nodejs
by adding the appropriate import/require statements
*/

const fs = require('fs');
const { exit } = require('process');

const cjs_includes = [
    "const { Performative, AgentID, Message, Gateway, MessageClass } = require('../../dist/cjs/fjage.cjs')",
    "const { isBrowser, isJsDom, isNode } = require('../../node_modules/browser-or-node/lib/index.js')",
    ""
]

const esm_includes = [
    "import { Performative, AgentID, Message, Gateway, MessageClass } from '../../dist/esm/fjage.js'",
    "import { isBrowser, isNode, isJsDom } from '../../node_modules/browser-or-node/src/index.js'",
    ""
]


function copyFile(source, target, cb) {
    var cbCalled = false;
    
    var rd = fs.createReadStream(source);
    rd.on("error", function(err) {
        done(err);
    });
    var wr = fs.createWriteStream(target, {flags: 'a'});
    wr.on("error", function(err) {
        done(err);
    });
    wr.on("close", function(ex) {
        done();
    });
    rd.pipe(wr);
    
    function done(err) {
        if (!cbCalled) {
            cb(err);
            cbCalled = true;
        }
    }
}

let src_spec = __dirname+'/fjage.spec.js'
let cjs_spec = __dirname+'/fjageSpec.cjs'
let esm_spec = __dirname+'/fjageSpec.mjs'

fs.writeFile(cjs_spec, cjs_includes.join('\n'), err => {
    if (err) {
        console.error(err);
        exit(1);
    }
    copyFile(src_spec,cjs_spec, err=>{
        if (err) {
            console.error(err);
            exit(1);
        } 
    })
})


fs.writeFile(esm_spec, esm_includes.join('\n'), err => {
    if (err) {
        console.error(err);
        exit(1);
    }
    copyFile(src_spec,esm_spec, err=>{
        if (err) {
            console.error(err);
            exit(1);
        } 
    })
})