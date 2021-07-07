#!/usr/bin/env node

const puppeteer = require('puppeteer');
const statik = require('node-static');

console.log('\nSetting up local static server at http://localhost:8000/test');
const file = new statik.Server('.');
let server = require('http').createServer(function (request, response) {
  request.addListener('end', function () {
    file.serve(request, response);
  }).resume();
}).listen(8000);


(async () => {
  console.log('Launching puppeteer..');
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  page.on('console', msg => {
    msg.type() == 'error' && console.log('PAGE ERR:', msg.text());
    msg.type() == 'warning' && console.log('PAGE WARN:', msg.text());
  });
  await page.goto('http://127.0.0.1:8000/test', {waitUntil: 'networkidle2'});
  await page.waitForSelector('.jasmine-overall-result');
  await page.waitForTimeout(100);
  await browser.close();
  console.log('Complete');
  server.close();
})();