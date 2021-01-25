const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  page.on('console', msg => {
    msg.type() == "error" && console.log('PAGE ERR:', msg.text());
    msg.type() == "warning" && console.log('PAGE WARN:', msg.text());
  });
  await page.goto('http://127.0.0.1:8080/test', {waitUntil: 'networkidle2'});
  await page.waitForSelector('.jasmine-overall-result');
  await page.waitForTimeout(100);
  await browser.close();
})();
