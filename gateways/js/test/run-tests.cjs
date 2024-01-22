#!/usr/bin/env node

const statik = require('node-static');
const Jasmine = require('jasmine');
const process = require('process');
const { chromium,  webkit, firefox} = require('playwright');

const ip = 'localhost';
const port = 8000;

// Setup static web server for testing browser version
console.log('Setting up local static server at http://'+ip+':'+port+'/test');
const file = new statik.Server('.');
let server = require('http').createServer(function (request, response) {
  request.addListener('end', function () {
    file.serve(request, response);
  }).resume();
}).listen(port);


(async () => {

  // Execute Node.js(CJS) test using Jasmine
  let startTime = new Date();
  console.log('Running jasmine..');
  await runJasmine('test/spec/', ['fjageSpec.cjs']);
  console.log('Node.js test Complete [' + (new Date() - startTime) + ' ms]');


  // Execute Browser(MJS) test using puppteer
  console.log('Launching playright..');
  startTime = new Date();
  let browser = null;
  // on MacOS use webkit instead of chromium
  if (process.platform === 'darwin') {
    browser = await webkit.launch();
  } else if (process.platform === 'win32') {
    browser = await chromium.launch();
  } else {
    browser = await firefox.launch();
  }
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on('console', msg => {
    if (msg.type() === 'log') console.log(`PAGE LOG: ${msg.text()}`);
    if (msg.type() === 'error') console.log(`PAGE ERR: ${msg.text()}`);
    if (msg.type() === 'warning') console.log(`PAGE WARN: ${msg.text()}`);
  });
  await page.goto(`http://${ip}:${port}/test`, {waitUntil: 'networkidle'});
  await page.waitForSelector('.jasmine-overall-result', {timeout: 60000});
  await page.waitForTimeout(1000);
  await browser.close();
  console.log(`Browser test Complete [${new Date() - startTime} ms]`);
  server.close();
  console.log('Run Tests Complete!');
  process.exit(0);
})();


// Helpers

async function runJasmine (dir, specs) {
  let jasmine = new Jasmine();
  jasmine.loadConfig({
    spec_dir: dir,
    spec_files: specs,
  });
  jasmine.exitOnCompletion = false;
  await jasmine.execute();
}