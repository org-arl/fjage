const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  await page.goto('http://127.0.0.1:8080/test', {waitUntil: 'networkidle2'});
  await browser.close();
})();
