#!/usr/bin/env node

const puppeteer = require('puppeteer');
const statik = require('node-static');
const Jasmine = require('jasmine');
const process = require('process');


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
  console.log('Launching puppeteer..');
  startTime = new Date();
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  page.on('console', msg => {
    msg.type() == 'log' && console.log('PAGE LOG:', msg.text());
    msg.type() == 'error' && console.log('PAGE ERR:', msg.text());
    msg.type() == 'warning' && console.log('PAGE WARN:', msg.text());
  });
  await page.goto('http://'+ip+':'+port+'/test', {waitUntil: 'networkidle2'});
  await page.waitForSelector('.jasmine-overall-result');
  await page.waitForTimeout(1000);
  await browser.close();
  console.log('Browser test Complete [' + (new Date() - startTime) + ' ms]');
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