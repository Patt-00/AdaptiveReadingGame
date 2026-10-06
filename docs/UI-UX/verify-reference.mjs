import { chromium } from '/home/patt/.npm/_npx/51691537fc71f2b0/node_modules/playwright/index.mjs';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';

const root=path.dirname(fileURLToPath(import.meta.url));
const screenshotDir=path.join(root,'screens-png');
fs.mkdirSync(screenshotDir,{recursive:true});
const manifest=JSON.parse(fs.readFileSync(path.join(root,'screen-manifest.json'),'utf8'));
const sourceHash=crypto.createHash('sha256').update(fs.readFileSync(manifest.sourceZip)).digest('hex');
assert.equal(sourceHash,manifest.sourceSha256);
const browser=await chromium.launch({executablePath:'/home/patt/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome',headless:true});
const page=await browser.newPage({viewport:{width:1328,height:1200}});
const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto('file://'+path.join(root,'index.html'));
const current=()=>page.evaluate(()=>window.referenceTest.current);
const show=id=>page.evaluate(id=>window.referenceTest.show(id),id);
const click=async target=>{await page.locator('#canvas [data-target="'+target+'"]').first().click();};
const checks=[];
await click('02');assert.equal(await current(),'02');await click('05');assert.equal(await current(),'05');await click('08');assert.equal(await current(),'08');checks.push('Signup → success → main menu');
assert.equal(await page.locator('#canvas [aria-disabled="true"]').count(),1);checks.push('Continue disabled with no progress');
await click('11');await click('12');await click('13');await page.keyboard.press('Space');assert.equal(await current(),'14');checks.push('Instructions → introduction → dialogue; Space advances story');
await page.keyboard.press('Escape');assert.equal(await current(),'26');await click('13');assert.equal(await current(),'14');checks.push('Escape / Return restores exact story caller');
await show('17');assert.equal(await page.locator('#canvas [aria-disabled="true"]').count(),1);await click('18');await click('20');assert.equal(await current(),'20');await click('22');await click('22');assert.equal(await current(),'22');await click('22b');assert.equal(await current(),'22b');await click('23');await click('24');assert.equal(await current(),'24');checks.push('Unanswered Submit disabled; selection never auto-submits; answer → feedback → final feedback → results');
await show('19');await click('21');assert.equal(await current(),'21');checks.push('Incorrect answer → supportive feedback');
await show('23');await click('25');assert.equal(await current(),'25');await click('40');await click('37');assert.equal(await current(),'37');checks.push('Model fallback → continue → story completion');
await show('18');await click('26');await click('32');await click('13');assert.equal(await current(),'18');checks.push('Settings Return restores selected assessment state');
await show('13');await click('26');await click('27');await click('29');assert.equal(await page.locator('#canvas [data-target]').count(),2);await click('27');await click('28');assert.equal(await current(),'28');checks.push('Overwrite Cancel leaves slots; empty slot → save-success state; modal background is inert');
await show('13');await click('26');await click('30');assert.equal(await page.locator('#canvas [aria-disabled="true"]').count(),5);await click('31');await click('13');assert.equal(await current(),'13');checks.push('Load empty slots disabled; occupied slot → confirmation → restored checkpoint');
await show('09');await click('34');await click('01');assert.equal(await current(),'01');checks.push('Logout → Welcome');
await show('09');await click('35');await click('26');assert.equal(await current(),'09');checks.push('Quit Cancel restores menu caller');
await show('35');await click('36');assert.equal(await current(),'36');checks.push('Quit → explicit guide endpoint');
await page.locator('#catalog-tab').click();assert.equal(await page.locator('#cards .tile').count(),manifest.screens.length);await page.locator('#flow-tab').click();assert.equal(await page.locator('#flow svg').count(),1);await page.locator('#guide-tab').click();checks.push('All-screen catalogue, flow, and build guide render');
const clipped=[];
for(const s of manifest.screens){
  await show(s.id);
  const bounds=await page.locator('#canvas svg').evaluate(svg=>Array.from(svg.querySelectorAll('text')).map(t=>({text:t.textContent,box:t.getBBox()})).map(({text,box:b})=>({text,x:b.x,y:b.y,w:b.width,h:b.height})).filter(b=>b.x< -1||b.y< -1||b.x+b.w>1281||b.y+b.h>721));
  for(const b of bounds)clipped.push({screen:s.id,...b});
  await page.locator('#canvas svg').screenshot({path:path.join(screenshotDir,s.id+'.png')});
}
await page.locator('#flow-tab').click();await page.locator('#flow svg').screenshot({path:path.join(root,'player-flow.png')});
// Browser print is used for the same layout that the user can export offline.
await page.evaluate(()=>window.print=()=>{});
await page.locator('#print').click();
assert.equal(await page.locator('#print-catalog [fill="url(#burgundy)"]').count(),0);
checks.push('Print uses unique gradient references, avoiding invisible white-on-white dialogue');
await page.pdf({path:path.join(root,'UIUX-Reference.pdf'),format:'A4',margin:{top:'15mm',bottom:'15mm',left:'15mm',right:'15mm'},printBackground:true,displayHeaderFooter:true,headerTemplate:'<span></span>',footerTemplate:'<div style="width:100%;font-size:9px;color:#555;text-align:center">Adaptive Reading Game - UI/UX Reference | <span class="pageNumber"></span> / <span class="totalPages"></span></div>'});
const report={sourceZipUnchanged:sourceHash,screenCount:manifest.screens.length,checks,scriptErrors:errors,clippedText:clipped,pdf:'UIUX-Reference.pdf',fonts:'Georgia requested; Times New Roman fallback used locally'};
fs.writeFileSync(path.join(root,'verification.json'),JSON.stringify(report,null,2));
assert.deepEqual(errors,[]);assert.deepEqual(clipped,[]);
for(let part=0;part<Math.ceil(manifest.screens.length/20);part++){
 const items=manifest.screens.slice(part*20,part*20+20);
 const content='<html><body style="margin:0;background:#eee;font-family:serif"><div style="display:grid;grid-template-columns:repeat(4,400px);gap:12px;padding:12px">'+items.map(s=>'<div style="background:white"><img style="width:400px;display:block" src="data:image/png;base64,'+fs.readFileSync(path.join(screenshotDir,s.id+'.png')).toString('base64')+'"><div style="padding:10px">'+s.id+' · '+s.title+'</div></div>').join('')+'</div></body></html>';
 await page.setViewportSize({width:1660,height:1400});await page.setContent(content);await page.screenshot({path:'/tmp/arg-contact-'+(part+1)+'.png',fullPage:true});
}
await browser.close();console.log(JSON.stringify(report,null,2));
