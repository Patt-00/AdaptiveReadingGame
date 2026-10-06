import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const zip = '/home/patt/Desktop/AdaptiveReadingGame-main_UI.zip';
const esc = s => String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const C = {red:'#73121F', top:'#A82234', gold:'#D4AF37', ink:'#000000', muted:'#555555', white:'#FFFFFF', gray:'#EEEEEE', slot:'#DCDCDC', error:'#B30000', success:'#2D7A2D'};
const ornament = 'M 316 88 C 291 22 233 26 206 63 C 164 119 82 92 71 55 C 61 19 102 6 115 29 C 129 53 97 49 105 34 M 316 88 C 275 49 232 61 190 78 C 149 95 122 80 150 54 C 177 31 239 36 259 65 C 284 99 248 104 237 88 M 316 88 C 277 18 219 18 171 44 C 120 70 66 116 21 75 C -18 39 7 -10 37 0 C 69 10 45 35 42 20 M 316 88 C 302 52 228 61 176 30 C 118 -5 138 48 230 64';
const screens = [];
function add(id,title,group,status,purpose,rule,render) { screens.push({id,title,group,status,purpose,rule,render}); }
function text(x,y,lines,size=24,color=C.ink,align='start',family='Georgia',weight='normal',italic=false) {
  lines = Array.isArray(lines)?lines:[lines];
  return `<text x="${x}" y="${y}" fill="${color}" font-family="${family}, Times New Roman, serif" font-size="${size}" font-weight="${weight}" text-anchor="${align}"${italic?' font-style="italic"':''}>${lines.map((l,i)=>`<tspan x="${x}" dy="${i?size*1.4:0}">${esc(l)}</tspan>`).join('')}</text>`;
}
function rect(x,y,w,h,fill=C.white,stroke='none',radius=0,sw=2) {return `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}" rx="${radius}"/>`;}
function action(label,target,body,disabled=false) {
  return `<g${target&&!disabled?` data-target="${target}" role="button" tabindex="0" aria-label="${esc(label)}" class="hotspot"`:''}${disabled?' opacity="0.4" aria-disabled="true"':''}>${body}</g>`;
}
function button(label,x,y,w=280,target=null,style='outline',disabled=false) {
  const h=54, gold=style==='gold', story=style==='story';
  const fill=story?'url(#burgundy)':gold?C.gold:C.white;
  const stroke=story?C.gold:gold?C.gold:C.ink;
  return action(label,target,rect(x,y,w,h,fill,stroke,story?16:4,story?3:2)+text(x+w/2,y+35,label,24,story?C.white:gold?C.red:C.ink,'middle','Georgia',story||gold?'bold':'normal'),disabled);
}
function link(label,x,y,target,size=22,color=C.ink) {return action(label,target,rect(x-10,y-size,wApprox(label,size)+20,size+18,'transparent')+text(x,y,label,size,color));}
function wApprox(s,size) {return s.length*size*.65;}
function flourish() {return `<g transform="translate(427 130) scale(.67)" fill="none" stroke="black" stroke-width="2.5"><path d="${ornament}"/><path d="${ornament}" transform="translate(648 0) scale(-1 1)"/></g>`;}
function title(label) {return text(640,100,label,58,C.ink,'middle','Times New Roman')+flourish();}
function footer(label='') {return text(640,690,label,17,C.muted,'middle');}
function auth(kind,message='',type='error') {
  const signup=kind==='SIGN UP';
  const hasValues=Boolean(message)&&type!=='success'&&!message.includes('Please enter');
  return title('WELCOME')+text(640,238,kind,28,C.ink,'middle')+
    text(447,277,'Name',18,C.muted)+rect(447,287,386,46,C.white,C.ink,4)+text(463,317,hasValues?'Player1':'Name',21,hasValues?C.ink:C.muted)+
    text(447,361,'Password',18,C.muted)+rect(447,370,386,46,C.white,C.ink,4)+text(463,400,hasValues?'••••••••':'Password',21,hasValues?C.ink:C.muted)+text(804,400,'Show',16,C.muted,'end')+
    (message?text(640,449,message,17,type==='success'?C.success:C.error,'middle'):text(640,449,'Use your account name and password.',17,C.muted,'middle'))+
    button(kind,523,477,234,signup?'05':'08')+button('BACK',573,552,134,'01')+footer('Design sample only — no credentials are entered or stored in this preview.');
}
function menu(resume=false) {
  let out=title('ADAPTIVE READING GAME')+text(1190,42,'Player1',17,C.muted,'end');
  const choices=[['NEW GAME',resume?'10':'11'],['CONTINUE',resume?'13':null],['SAVE / LOAD','30'],['SETTINGS','32'],['QUIT','35']];
  choices.forEach(([label,target],i)=>{out+=action(label,target,rect(420,226+i*72,440,61,'transparent')+text(640,270+i*72,label,34,C.ink,'middle'),i===1&&!resume);});
  out+=link('LOG OUT',1080,660,'34',18)+footer(resume?'Continue resumes your most recent checkpoint.':'No saved progress yet. Start a new game to begin.');
  return out;
}
function story(body='Pleased to meet you.',thought=false,choices=false) {
  let out=text(40,50,'Chapter 1: Beginning',26,C.red,'start','Georgia','bold')+button('MENU',1090,24,150,'26');
  if(choices){
    [['Ask about the letter','16'],['Offer to help','16'],['Wait quietly','16']].forEach(([label,target],i)=>out+=button(label,307,218+i*78,666,target,'story'));
  }
  out+=rect(240,505,800,147,thought?'#C76F7B':'url(#burgundy)',C.gold,14,3);
  out+=rect(540,481,200,34,'url(#burgundy)',C.gold,8,2)+text(640,505,thought?'You':'???',17,'#FFF8DC','middle','Georgia','bold');
  out+=text(270,555,body,22,thought?'#2B181C':C.white,'start','Georgia','normal',thought);
  if(!choices) out+=button('NEXT',900,583,112,thought?'15':body==='Pleased to meet you.'?'14':'17','gold');
  return out+footer(choices?'Story choice — changes narrative, not your reading score.':'Click NEXT or press Space to continue. Esc opens the game menu.');
}
const question='Why does Mara hide the letter?';
const answers=['A. She wants to throw it away.','B. She wants to keep the message private.','C. She has forgotten how to read.'];
function check(selected=-1,final=false) {
  let out=text(40,50,'Chapter 1: Reading Check',26,C.red,'start','Georgia','bold')+button('MENU',1090,24,150,'26')+
    text(110,116,final?'Question 5 of 5 · Easy':'Question 1 of 5 · Easy',20,C.muted)+
    rect(110,140,1060,145,C.white,C.red,10)+text(140,175,'Read the passage:',18,C.red,'start','Georgia','bold')+
    text(140,213,['Mara slipped the letter into her apron before the visitor entered.','She did not want anyone else to see the message.'],24)+
    text(110,338,question,27,C.red,'start','Georgia','bold');
  answers.forEach((a,i)=>{out+=action(a,final?'22':i===1?'18':'19',rect(110,365+i*72,1060,58,i===selected?C.red:C.white,i===selected?C.gold:C.red,9,i===selected?3:2)+text(137,403+i*72,(i===selected?'● ':'○ ')+a,23,i===selected?C.white:C.ink));});
  out+=text(110,620,selected<0?'Choose one answer, then submit.':'Selected answer — you can change it before submitting.',18,C.muted);
  out+=button('SUBMIT',930,592,240,final?'22b':selected===1?'20':'21','story',selected<0&&!final);
  return out+footer('No timed answering. Only your first submitted answer counts toward the score.');
}
function feedback(correct=true) {
  return text(40,50,'Chapter 1: Reading Check',26,C.red,'start','Georgia','bold')+
    rect(200,155,880,415,'url(#burgundy)',C.gold,16,3)+
    text(640,220,correct?'CORRECT':'NOT QUITE',34,'#FFF8DC','middle','Georgia','bold')+
    text(640,275,correct?'Your answer: B — keep the message private.':'Your answer: A — throw the letter away.',23,C.white,'middle')+
    text(640,330,['The passage says Mara did not want anyone else','to see the message. That shows she wanted privacy.'],24,C.white,'middle')+
    text(640,419,'Your first answer has been recorded.',20,'#FFF8DC','middle')+
    button('NEXT QUESTION',480,471,320,'22','gold')+footer('Feedback teaches the reading skill. Do not let the player resubmit for a higher score.');
}
function results(fallback=false) {
  return rect(0,0,1280,720,'#262626')+rect(310,130,660,465,'url(#burgundy)',C.gold,15,3)+
    text(640,200,'CHAPTER RESULTS',32,'#FFF8DC','middle','Georgia','bold')+
    text(640,268,'Comprehension Score: 80%',30,C.white,'middle')+text(640,314,'4 of 5 first answers correct',22,C.white,'middle')+
    text(640,375,fallback?'Next difficulty: Easy':'Next difficulty: Medium',25,'#FFF8DC','middle','Georgia','bold')+
    text(640,419,fallback?['Model unavailable. Java used its fallback policy.','Your progress was saved; you can continue.']:['Strong reading performance across recent checks.','Java confirmed the next challenge level.'],19,C.white,'middle')+
    button('CONTINUE',480,485,320,'40','gold')+text(640,574,'Chapter completed · Progress recorded',18,'#FFF8DC','middle');
}
function side(active='SAVE') {
  let out=rect(0,0,253,720,C.gray);
  const nav=[['SAVE','27'],['LOAD','30'],['QUIT','35'],['SETTINGS','32'],['MAIN MENU','33'],['RETURN','13']];
  nav.forEach(([label,target],i)=>out+=link(label,47,220+i*52+(i===5?35:0),target,21,label===active?C.top:'#333333'));
  return out;
}
function slots(mode='SAVE',saved=false) {
  let out=side(mode)+text(320,70,mode,34,C.red,'start','Georgia','bold')+
    text(320,106,mode==='SAVE'?'Choose an empty slot, or overwrite an existing save.':'Choose a saved checkpoint to resume.',18,C.muted);
  for(let i=0;i<6;i++) {
    const x=320+(i%3)*287,y=140+Math.floor(i/3)*220,filled=i===0||(saved&&i===1);
    const contents=rect(x,y,253,147,C.slot,C.red,6)+text(x+126,y+35,filled?'Chapter 1: Beginning':'EMPTY',20,filled?C.red:C.muted,'middle','Georgia',filled?'bold':'normal')+
      (filled?text(x+126,y+73,['Scene 4 · Easy','Reading check 0 / 5','Saved just now'],16,C.muted,'middle'):text(x+126,y+88,mode==='SAVE'?'Click to save here':'No saved checkpoint',16,C.muted,'middle'))+
      text(x+126,y+177,`SLOT ${i+1}`,17,C.muted,'middle');
    out+=action(`Slot ${i+1}`,mode==='SAVE'?filled?'29':'28':filled?'31':null,contents,mode==='LOAD'&&!filled);
  }
  out+=text(785,632,'AUTO   <   1   2   3   4   5   6   7   8   9   10   >',18,C.muted,'middle');
  if(saved) out+=rect(740,30,490,50,'#EEF6EE',C.success,6)+text(985,62,'Progress saved successfully.',19,C.success,'middle');
  return out+footer('Page 1 layout retained from the UI. Slot count and pagination need a shared backend contract.');
}
function modal(heading,lines,yes,target,noTarget='13',base='story') {
  const under=(base==='menu'?menu(true):base==='save'?slots():base==='load'?slots('LOAD'):story()).replace(/ data-target="[^"]*"| role="button"| tabindex="0"| class="hotspot"/g,'');
  return under+rect(0,0,1280,720,'#000000') .replace('fill="#000000"','fill="#000000" opacity="0.45"')+
    rect(290,220,700,295,C.white,C.ink,12,3)+text(640,278,heading,28,C.ink,'middle','Georgia','bold')+
    text(640,329,lines,20,C.muted,'middle')+button(yes,345,421,280,target)+button('CANCEL',655,421,280,noTarget);
}
function settings() {
  let out=side('SETTINGS')+text(320,70,'SETTINGS',34,C.red,'start','Georgia','bold');
  function slider(x,y,label,val){return text(x,y,label,20,C.muted)+rect(x,y+20,330,6,C.slot,'none',3)+rect(x,y+20,val*3.3,6,C.red,'none',3)+`<circle cx="${x+val*3.3}" cy="${y+23}" r="10" fill="${C.top}"/>`+text(x+330,y-2,`${val}%`,16,C.muted,'end');}
  out+=text(320,146,'Audio',24,C.red,'start','Georgia','bold')+slider(320,195,'Music Volume',80)+slider(320,285,'Sound Volume',65)+slider(320,375,'Voice Volume',90);
  out+=text(760,146,'Reading',24,C.red,'start','Georgia','bold')+slider(760,195,'Text Speed',70)+slider(760,285,'Autoplay Speed',40)+text(760,389,'Text size',20,C.muted)+button('A−',760,411,95,null)+button('A+',875,411,95,null)+text(760,502,'Autoplay pauses during Reading Checks.',17,C.muted);
  return out+footer('Control appearance reference only. Settings should persist per player; Return restores the prior screen.');
}

add('01','Welcome','Account','Existing base','Players choose whether to log in or create an account. This preserves the original simple welcome screen and provides the entry point for personalized progress.','LOG IN → 06; SIGN UP → 02.',()=>title('WELCOME')+button('LOG IN',523,300,234,'06')+button('SIGN UP',523,385,234,'02')+footer('Adaptive Reading Game'));
add('02','Sign up','Account','Existing base','Players supply an account name and password. A successful signup takes them to login rather than entering the game automatically.','SIGN UP → 05 (sample success); BACK → 01. Validation variants: 03 and 04.',()=>auth('SIGN UP'));
add('03','Signup · missing fields','Account','Existing code state','An inline message explains that the required fields are missing. The form stays available so the player can correct the problem without losing context.','Keep the same form; do not create an account until valid. Preview SIGN UP follows the corrected path.',()=>auth('SIGN UP','Please enter your name and password.'));
add('04','Signup · duplicate name','Account','Existing code state','An inline message explains that the account name already exists. Players can choose another name or return to login.','Keep credentials masked; do not reveal another account’s details.',()=>auth('SIGN UP','Account name already exists. Please choose another.'));
add('05','Signup success → login','Account','Existing code state','A success message confirms account creation on the login form. The player then logs in to reach the main menu.','LOG IN → 08 (new-player example). Real code must read actual account progress.',()=>auth('LOG IN','Account created successfully! Please log in.','success'));
add('06','Log in','Account','Existing base','Players enter their account name and password to access their saved progress. The preview is a visual route demonstration and does not authenticate or collect credentials.','LOG IN → 08 (sample success); BACK → 01; error variant → 07.',()=>auth('LOG IN'));
add('07','Login · invalid credentials','Account','Existing code state','A neutral error message says the name or password is invalid. Players can correct their entries and retry without disclosing which credential was wrong.','Stay on login; LOG IN previews a successful corrected attempt.',()=>auth('LOG IN','Invalid name or password.'));
add('08','Main menu · no progress','Menu','Existing base + additions','New players can start a game, open load/settings, or quit. Continue is disabled and explained because no resumable checkpoint exists.','NEW GAME → 11; LOAD → 30; SETTINGS → 32; LOG OUT → 34; QUIT → 35. Account name and Logout are additions.',()=>menu());
add('09','Main menu · returning player','Menu','Proposed state','Returning players can continue their latest checkpoint or start a new game. This gives Continue a clear purpose and protects existing progress before a restart.','CONTINUE → 13 (sample checkpoint); NEW GAME → 10.',()=>menu(true));
add('10','New game confirmation','Menu','Proposed addition','A returning player confirms starting a new playthrough. The message distinguishes restarting current progress from deleting manual saves.','START NEW → 11; CANCEL → 09. Keep manual saves unless the player explicitly deletes them.',()=>modal('START A NEW GAME?',['Your current playthrough will restart.','Existing manual saves will remain available.'],'START NEW','11','09','menu'));
add('11','How to play','Story','Existing base + clarified copy','Players learn the story controls before playing. The explanation clearly distinguishes narrative choices from scored Reading Checks.','NEXT → 12; BACK → 08. Space advances narrative only, never submits an assessment answer.',()=>title('HOW TO PLAY?')+text(640,295,['Click NEXT or press Space to advance the story.','Press Esc to open the game menu and save your progress.'],27,C.ink,'middle')+text(640,417,['Story choices change the narrative.','Reading Checks measure comprehension; only first answers count.'],26,C.ink,'middle')+button('BACK',345,555,250,'08')+button('NEXT',685,555,250,'12'));
add('12','Introduction','Story','Existing base','The introduction establishes the player’s role before Chapter 1 begins. It preserves the existing brief servant premise, with final story copy still to be supplied by the team.','PLAY → 13; BACK → 11. Text is source placeholder, not an approved final script.',()=>title('INTRODUCTION')+text(640,327,'You are a servant....',30,C.ink,'middle')+button('BACK',345,515,250,'11')+button('PLAY',685,515,250,'13'));
add('13','Story · dialogue','Story','Existing base','The player reads a character’s dialogue in the existing burgundy-and-gold panel. NEXT advances the conversation, while MENU opens navigation without advancing the story.','NEXT / Space → 14; MENU / Esc → 26. Restore the exact scene when returning from a menu.',()=>story());
add('14','Story · thoughts','Story','Existing base','The lighter panel and italic text distinguish the player’s private thoughts from spoken dialogue. Players advance when they finish reading.','NEXT / Space → 15; MENU / Esc → 26.',()=>story('Should I trust this person...',true));
add('15','Story · narrative choice','Story','Existing base + sample copy','The player chooses how to respond to the character. These choices affect story branches and relationships, not comprehension score or difficulty.','All three preview choices → 16; real game must restore the chosen branch. Story choice text is illustrative.',()=>story('Choose your next move carefully.',false,true));
add('16','Story · branch continuation','Story','Proposed state','The story acknowledges the chosen narrative action before the Reading Check. This prevents a story choice from jumping directly to a fabricated comprehension result.','NEXT → 17. Branch-specific dialogue is sample content; author each actual branch separately.',()=>story(['Mara hides the letter before the visitor enters.','She does not want anyone else to see the message.']));
add('17','Reading Check · unanswered','Reading Check','Proposed addition','A separate passage and question measure comprehension. Submit stays disabled until an answer is selected, and the player can read at their own pace.','B → 18; A or C → 19. MENU pauses; no time limit or autoplay submission.',()=>check());
add('18','Reading Check · selected B','Reading Check','Proposed addition','The selected answer is visibly highlighted before submission. Players can change their selection until Submit records their first attempt.','SUBMIT → 20; A/C → 19. Selection alone must not score or advance the question.',()=>check(1));
add('19','Reading Check · selected A','Reading Check','Proposed addition','This variant shows a selected incorrect answer before submission. It demonstrates the wrong-answer route without disclosing correctness before the player submits.','SUBMIT → 21; B → 18. The example groups A/C into the incorrect selection route.',()=>check(0));
add('20','Reading Check · correct feedback','Reading Check','Proposed addition','The player sees confirmation and a passage-based explanation. The first submitted answer is locked, and the next action advances to another question.','NEXT QUESTION → 22 (representative final-question jump); the actual game must show questions 2–4.',()=>feedback());
add('21','Reading Check · incorrect feedback','Reading Check','Proposed addition','The player receives a supportive explanation of the correct reasoning. The incorrect first attempt remains recorded; feedback does not offer a score-changing retry.','NEXT QUESTION → 22 (representative final-question jump). No resubmission for this scored question.',()=>feedback(false));
add('22','Reading Check · final question','Reading Check','Proposed state','The final-question state marks the end of a five-question check. It reuses the same question layout so the team can implement one reusable assessment view.','SUBMIT → 22b after first-attempt recording. This is a fixed selected-state preview; sample passage repeats for layout only.',()=>check(1,true));
add('22b','Reading Check · final feedback','Reading Check','Proposed state','The final submitted answer receives the same explanatory feedback as earlier questions. View Results then moves to difficulty analysis rather than presenting another question.','VIEW RESULTS → 23. Reuse the feedback component with a final-question action variant; incorrect final feedback follows the same route.',()=>feedback().replaceAll('NEXT QUESTION','VIEW RESULTS').replace('data-target="22"','data-target="23"'));
add('23','Preparing the next challenge','Difficulty / Results','Proposed addition','A brief status panel tells the player that the completed answers are being analyzed. Progress is recorded before optional model analysis so a timeout does not lose the completed check.','NEXT (preview success) → 24; MODEL UNAVAILABLE (preview test route) → 25. Real Java code should transition automatically with a bounded timeout.',()=>rect(0,0,1280,720,'#262626')+rect(300,190,680,330,'url(#burgundy)',C.gold,15,3)+text(640,257,'PREPARING YOUR NEXT CHALLENGE',29,'#FFF8DC','middle','Georgia','bold')+text(640,321,['Your answers have been recorded.','Reviewing your recent reading performance...'],23,C.white,'middle')+button('NEXT',375,405,240,'24','gold')+button('MODEL UNAVAILABLE',645,405,260,'25','gold'));
add('24','Chapter results · normal','Difficulty / Results','Existing base + real-data design','The result combines a comprehension score, first-answer count, and the next difficulty. Java validates any model recommendation before the player continues.','CONTINUE → 40. 80% / Medium is illustrative, not a fixed rule or a model guarantee.',()=>results());
add('25','Chapter results · model fallback','Difficulty / Results','Proposed addition','The result remains usable when the optional model fails or times out. Java applies its deterministic fallback policy and explains the outcome without alarming the player.','CONTINUE → 40. Easy is an example outcome; use actual validated policy output.',()=>results(true));
add('26','Game menu / pause','Navigation / Saves','Proposed addition','This menu pauses narrative progression and exposes save, load, settings, and exit actions. Return restores the exact prior scene or assessment state instead of restarting it.','SAVE → 27; LOAD → 30; SETTINGS → 32; MAIN MENU → 33; RETURN → captured previous scene in the preview.',()=>side()+text(760,260,'GAME PAUSED',40,C.red,'middle','Georgia','bold')+text(760,330,['Your place in the story is held.','Choose an action on the left, or return to reading.'],24,C.muted,'middle')+button('RETURN TO GAME',590,435,340,'13'));
add('27','Save · occupied and empty slots','Navigation / Saves','Existing base + populated states','Players can identify saves by chapter, scene, difficulty, and timestamp. Empty slots accept new saves, while occupied slots require overwrite confirmation.','Slot 1 → 29; empty Slot 2–6 → 28. Preserve the original six-slot page visually; finalize count with backend team.',()=>slots());
add('28','Save · success','Navigation / Saves','Proposed addition','A visible confirmation appears only after persistence succeeds. The player stays on Save and can return to the held scene without losing their place.','RETURN → previous screen. Example shows a newly populated Slot 2; overwrite instead updates the chosen occupied slot.',()=>slots('SAVE',true));
add('29','Save · overwrite confirmation','Navigation / Saves','Proposed addition','The player confirms replacing an occupied slot before data is changed. Cancel preserves the existing save and returns to the slot list.','OVERWRITE → 28 after successful write; CANCEL → 27. Failure route is 39.',()=>modal('OVERWRITE SLOT 1?',['This will replace Chapter 1: Beginning.','Your other save slots will not be changed.'],'OVERWRITE','28','27','save'));
add('30','Load · occupied and empty slots','Navigation / Saves','Existing base + populated states','Players select a saved checkpoint using meaningful metadata. Empty slots are disabled, so the interface does not pretend to load nonexistent progress.','Slot 1 → 31 if current state is unsaved, otherwise resume immediately. If called from main menu with no active game, skip discard confirmation.',()=>slots('LOAD'));
add('31','Load · unsaved progress warning','Navigation / Saves','Proposed addition','The player confirms discarding unsaved session progress before loading a checkpoint. Cancel returns to Load without changing the current session.','LOAD SAVE → 13 only after validation and successful restore; CANCEL → 30. Invalid save route is 38.',()=>modal('LOAD SLOT 1?',['Unsaved progress in this session will be lost.','The saved checkpoint will not be deleted.'],'LOAD SAVE','13','30','load'));
add('32','Settings','Navigation / Saves','Existing base + text-size addition','The existing audio and reading-speed controls retain their two-column layout. Text-size controls are a proposed accessibility addition, and autoplay pauses whenever a Reading Check is active.','RETURN restores caller. Preview sliders/text-size are appearance specifications, not working controls.',()=>settings());
add('33','Return to main menu confirmation','Exit / Completion','Proposed addition','The player confirms leaving an active session before unsaved changes are discarded. This distinguishes Main Menu from Return, which resumes the held game.','MAIN MENU → 09; CANCEL → previous screen. Saved checkpoints remain available.',()=>modal('RETURN TO MAIN MENU?',['Any unsaved progress will be lost.','Saved checkpoints will remain available.'],'MAIN MENU','09','26'));
add('34','Logout confirmation','Exit / Completion','Proposed addition','The player confirms ending their authenticated session. Logout clears session access, but keeps saved progress associated with the account.','LOG OUT → 01; CANCEL → 09. If active unsaved play exists, use the same loss warning and allow cancel.',()=>modal('LOG OUT?',['Saved progress remains in your account.','Any unsaved session changes will be lost.'],'LOG OUT','01','09','menu'));
add('35','Quit confirmation','Exit / Completion','Existing base','The existing quit card warns that unsaved progress will be lost. Players can cancel without navigating or changing game state.','QUIT → 36 (terminal-state representation); CANCEL restores caller. Real app closes its window only on confirmation.',()=>modal('DO YOU WANT TO QUIT?',['Any unsaved progress will be lost.'],'QUIT','36','26'));
add('36','Application closed · flow endpoint','Exit / Completion','Guide endpoint, not app screen','This reference-only endpoint represents the application window closing. It is not a new screen for the JavaFX team to implement.','Preview RESTART → 01; real program has no rendered screen after exit.',()=>text(640,285,'APPLICATION CLOSED',40,C.ink,'middle','Times New Roman')+text(640,355,'Flow endpoint — not an in-game screen.',24,C.muted,'middle')+button('RESTART PREVIEW',450,440,380,'01'));
add('37','Story complete','Exit / Completion','Proposed addition','The player receives a clear endpoint after the last available chapter is completed. They can return to the main menu without a Continue button pointing to nonexistent content.','MAIN MENU → 09 with story-complete metadata. Continue should replay or be disabled only if the team explicitly specifies that behavior.',()=>title('STORY COMPLETE')+text(640,290,['You have completed all available chapters.','Your reading progress has been recorded.'],28,C.ink,'middle')+button('MAIN MENU',460,465,360,'09'));
add('38','Load · unreadable save','Navigation / Saves','Proposed addition','An error explains that the selected checkpoint could not be loaded. The current session and save data are preserved so the player can choose another slot.','BACK TO LOAD → 30. Do not partially restore or silently delete a damaged save.',()=>modal('COULD NOT LOAD THIS SAVE',['Your current progress has not been changed.','Choose another checkpoint or return to your game.'],'BACK TO LOAD','30','26','load'));
add('39','Save · persistence failed','Navigation / Saves','Proposed addition','The player is told that the save did not succeed and the session remains unsaved. Retry attempts persistence again; cancel restores the session without claiming progress was saved.','RETRY → 28 is the successful retry example; CANCEL → previous screen. Keep the old slot intact on failure.',()=>modal('COULD NOT SAVE PROGRESS',['Your game is still open, but this save did not complete.','Please retry before leaving the game.'],'RETRY','28','26','save'));
add('40','Next chapter · loading / completion','Exit / Completion','Proposed addition','The player sees a transition while the next chapter and validated difficulty are loaded. If there is no next chapter, the game shows Story Complete instead of entering an empty scene.','NEXT CHAPTER → 13 is a reusable-layout sample; STORY FINISHED → 37 is a reviewer test route. Real application chooses automatically.',()=>title('NEXT CHAPTER')+text(640,300,['Loading the next reading challenge...','Your difficulty and checkpoint have been recorded.'],27,C.ink,'middle')+button('NEXT CHAPTER',345,475,280,'13')+button('STORY FINISHED',655,475,280,'37'));

const defs = `<defs><linearGradient id="burgundy" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="${C.top}"/><stop offset="100%" stop-color="${C.red}"/></linearGradient></defs>`;
for (const s of screens) {
  s.svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720" aria-labelledby="screen-title-${s.id}"><title id="screen-title-${s.id}">${esc(s.title)}</title>${defs}${rect(0,0,1280,720)}${s.render()}</svg>`;
  delete s.render;
}
const ids=new Set(screens.map(s=>s.id));
for(const s of screens) for(const match of s.svg.matchAll(/data-target="([^"]+)"/g)) if(!ids.has(match[1])) throw new Error(`Broken target ${s.id} → ${match[1]}`);
fs.mkdirSync(path.join(root,'screens'),{recursive:true});
for(const s of screens) fs.writeFileSync(path.join(root,'screens',`${s.id}-${s.title.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/-$/,'')}.svg`),s.svg);

function flowDiagram() {
  const W=1440,H=1230;
  let out=rect(0,0,W,H)+text(50,60,'Adaptive Reading Game · Player flow',38,C.red,'start','Times New Roman')+text(50,102,'Proposed complete flow. Screen numbers refer to the reference package.',20,C.muted);
  const node=(x,y,label,small='',kind='box')=>{
    const shape=kind==='diamond'?`<path d="M ${x+130} ${y} L ${x+260} ${y+65} L ${x+130} ${y+130} L ${x} ${y+65} Z" fill="#F8F5EF" stroke="${C.red}" stroke-width="2"/>`:rect(x,y,260,100,kind==='end'?C.gray:C.white,C.red,kind==='end'?40:8);
    return shape+text(x+130,y+(kind==='diamond'?58:42),label,22,C.red,'middle','Georgia','bold')+text(x+130,y+(kind==='diamond'?85:74),small,16,C.muted,'middle');
  };
  const arrow=(d,label='',x=0,y=0)=>`<path d="${d}" fill="none" stroke="${C.red}" stroke-width="2" marker-end="url(#arrow)"/>`+(label?rect(x-5,y-19,label.length*10+12,25,C.white)+text(x,y,label,16,C.muted):'');
  out+=`<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M0 0 L10 5 L0 10 Z" fill="${C.red}"/></marker></defs>`;
  out+=node(50,150,'Welcome / account','01–07')+node(410,150,'Main menu','08–10')+node(770,150,'Story & choices','11–16')+node(1130,150,'Reading Check','17–22b');
  out+=arrow('M310 200 H400')+arrow('M670 200 H760','New game',688,181)+arrow('M1030 200 H1120');
  out+=node(1130,345,'More questions?','First attempt locked','diamond')+node(770,360,'Analyze / validate','23 · Java owns decision')+node(410,360,'Model available?','Bounded timeout','diamond');
  out+=arrow('M1260 250 V335')+arrow('M1390 410 H1415 V200 H1390','Yes',1380,295)+arrow('M1130 410 H1040','No',1070,391)+arrow('M770 410 H680');
  out+=node(50,555,'Fallback results','25 · deterministic policy')+node(410,555,'Normal results','24 · validated next level')+node(770,555,'Next chapter?','40','diamond')+node(1130,555,'Story complete','37','end');
  out+=arrow('M540 490 V545','Yes',560,528)+arrow('M410 425 H180 V545','No / timeout',215,406)+arrow('M310 605 H375 V740 H870 V680')+arrow('M670 605 H725 V620 H760')+arrow('M1030 620 H1120','No',1060,598);
  out+=node(770,780,'Continue story','Repeat story / reading loop')+arrow('M900 685 V770','Yes',925,744);
  out+=rect(50,930,1340,225,'#F8F5EF',C.red,8)+text(80,970,'In-session menu · 26–35, 38–39',25,C.red,'start','Georgia','bold')+text(80,1010,['Story / Reading Check → pause → Save / Load / Settings / Main Menu / Quit','Return → restore the exact caller; cancellation → no state change','Save occupied slot → confirm overwrite → success or failure; Load empty slot → disabled','Logout → clear account session → Welcome; Quit → application closes (36)'],20,C.ink);
  out+=text(50,1195,'Narrative choices never determine the score. Model failure must not block continuing.',20,C.red);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${out}</svg>`;
}
const flow=flowDiagram();
fs.writeFileSync(path.join(root,'player-flow.svg'),flow);
fs.writeFileSync(path.join(root,'screen-manifest.json'),JSON.stringify({version:1,frame:{width:1280,height:720},sourceZip:zip,sourceSha256:crypto.createHash('sha256').update(fs.readFileSync(zip)).digest('hex'),fonts:{title:'Times New Roman',body:'Georgia',localFallback:'Times New Roman',note:'Georgia not installed locally. No font files distributed.'},screens:screens.map(({svg,...s})=>s)},null,2));

const data=JSON.stringify(screens).replaceAll('<','\\u003c');
const html=`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Adaptive Reading Game · UI/UX Reference</title><style>
*{box-sizing:border-box}body{margin:0;background:#f8f5ef;color:#222;font:16px Georgia,'Times New Roman',serif}header{padding:22px 30px;background:#73121f;color:#fff8dc}h1{font:32px 'Times New Roman',serif;margin:0 0 8px}header p{margin:0;line-height:1.5}button,select{font:inherit;cursor:pointer;border:1px solid #73121f;border-radius:4px;padding:9px 14px;color:#73121f;background:white}button:focus-visible,select:focus-visible,.hotspot:focus-visible{outline:3px solid #D4AF37;outline-offset:4px}nav{display:flex;gap:10px;flex-wrap:wrap;padding:14px 30px;border-bottom:1px solid #ddd}main{max-width:1440px;margin:0 auto;padding:24px}.meta{display:flex;justify-content:space-between;align-items:center;gap:12px}.badge{border:1px solid #73121f;color:#73121f;padding:5px 10px;font-size:14px}.screen{background:white;border:1px solid #ddd;margin:16px 0;box-shadow:0 8px 24px #00000012}.screen>svg{width:100%;height:auto;display:block}.hotspot{cursor:pointer}.hotspot:hover{filter:brightness(.91)}.hotspot:focus-visible{outline:none}.hotspot:focus-visible>rect{stroke:#655438;stroke-width:4}.notes{background:white;border-left:4px solid #D4AF37;padding:16px 22px;line-height:1.6}.notes p{margin:4px 0 14px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(290px,1fr));gap:20px}.tile{padding:0;text-align:left;border:1px solid #ddd;overflow:hidden}.tile svg{width:100%;height:auto;display:block}.tile .caption{padding:12px;line-height:1.5}.warning{padding:12px 18px;background:#fff4cd;border:1px solid #D4AF37;line-height:1.5}.muted{color:#555}.hidden{display:none!important}#flow svg{width:100%;height:auto}.page-description{max-width:1100px;line-height:1.6}.print-card{break-after:page;padding-top:16px}.print-card>svg{width:100%;height:auto}#print-catalog{display:none}footer{padding:24px 30px;color:#555}a{color:#73121f}@media print{body{background:white}header,nav,main,footer{display:none!important}#print-catalog{display:block}#print-catalog h2{font-size:22px}#print-catalog p{font-size:12px;line-height:1.4}.print-card{max-width:100%}}@media(max-width:700px){header,nav{padding-left:16px;padding-right:16px}main{padding:16px}.meta{align-items:flex-start;flex-direction:column}h1{font-size:27px}}
</style></head><body><header><h1>Adaptive Reading Game</h1><p>Complete UI/UX reference · based on your groupmate’s JavaFX design · ${screens.length} screens and states</p></header><nav aria-label="Reference navigation"><button id="preview-tab">Clickable preview</button><button id="catalog-tab">All screens</button><button id="flow-tab">Player flow</button><button id="guide-tab">Build guide</button><button id="print">Print / Save PDF</button><select id="jump" aria-label="Jump to screen"></select><button id="back">Back in preview</button><button id="reset">Reset preview</button></nav><main><div class="warning">This is a design reference, not the Java game. Data, scores, and model responses are illustrative. Georgia is specified but unavailable here; local body text uses Times New Roman. Controls on visual screens are static unless they lead to another reference state.</div><section id="preview"><div class="meta"><h2 id="screen-name"></h2><span class="badge" id="screen-status"></span></div><div class="screen" id="canvas"></div><div class="notes"><p id="purpose"></p><strong>Interaction / implementation note</strong><p id="rule"></p></div></section><section id="catalog" class="hidden"><h2>Screen catalogue</h2><p>Existing base = preserved visual layout, not proof of completed functionality. Proposed additions and code-only states extend that base.</p><div class="grid" id="cards"></div></section><section id="flow" class="hidden"><h2>Player flow</h2>${flow}</section><section id="guide" class="hidden page-description"><h2>What to build first</h2><ol><li><strong>Shared UI foundations:</strong> serif typography, menu buttons, burgundy/gold panels, answer-selection states, confirmation cards, sidebar, save-slot card.</li><li><strong>Account and menu routes:</strong> 01–10 and Logout 34. Replace the prototype’s in-memory account map with the agreed authentication service.</li><li><strong>Story navigation:</strong> 11–16 and pause/return 26. Story choices affect narrative only.</li><li><strong>Reading Checks:</strong> 17–22b. Build one reusable view; store first submissions; provide passage-based feedback and real question data.</li><li><strong>Results and difficulty:</strong> 23–25, then 40/37. Java validates difficulty and provides bounded-time fallback if the optional model is unavailable.</li><li><strong>Persistence and navigation safety:</strong> 27–31, 33, 35, 38–39. Show success only after the write finishes; never destroy a valid save on failure.</li><li><strong>Settings and accessibility:</strong> 32, keyboard focus, readable sizes, password visibility as a deliberate click/keyboard action, no timed assessments.</li></ol><h2>Contracts the team must agree on</h2><ul><li><strong>Save capacity:</strong> this reference preserves the ZIP’s six-slot page and ten-page indicator. Those are UI placeholders; do not infer that storage supports sixty saves. Agree on capacity/page mapping before integration.</li><li><strong>Continue:</strong> resume the latest eligible checkpoint for the logged-in player. No checkpoint means disabled Continue; final-story behavior needs an explicit decision.</li><li><strong>Resume:</strong> hold scene, branch, question index, selected answer, and all submitted first attempts. Menu Return, load, and login must not accidentally reset answers or scoring.</li><li><strong>Result example:</strong> 80%, Medium, and fallback Easy are sample values, not final thresholds. Use the actual Java policy.</li><li><strong>Story and assessment copy:</strong> the ZIP’s introduction is a placeholder. Added story and question copy is illustrative, not a finished content bank.</li><li><strong>Progress before analysis:</strong> record submitted answers and completion before optional model inference. A failure still gives usable results and a next step.</li></ul><h2>Figma import</h2><p>Drag files from <code>screens/</code> into Figma in numerical order. They are vector SVGs, not flattened screenshots. Shapes can be edited; SVG text remains text in the source, but Figma’s import may outline or alter text depending on font/import support. Check font availability before import. These files do not include native Figma components, variables, auto-layout, or prototype reactions.</p><p>Use 1280 × 720 frames. Retain Georgia for body/menu text and Times New Roman for titles when those fonts are available. Create reusable Figma components from the repeated elements, then connect buttons using the interaction notes and flow SVG.</p><h2>Read-only source review</h2><p>Original ZIP: AdaptiveReadingGame-main_UI.zip. Its results label is fixed at 100%; save/load slot handlers print messages; account storage is an in-memory map. The reference does not claim these features are implemented, and it makes no changes to the source.</p><h2>Accessibility / intentional refinements</h2><p>Chapter headers use the existing dark burgundy on white instead of low-contrast gold. Selected answers have a visible marker as well as color. New error copy explains recovery, and modal Cancel restores the caller. Final accessibility checks should use the actual Georgia font and JavaFX layout.</p><h2>Preview limitations</h2><p>The preview follows representative paths. Questions 2–4 are skipped between feedback and the final question; story branches share a sample continuation. Forms, sliders, save persistence, model calls, and credentials are not implemented. Navigation history and the captured caller let Cancel and Return restore the reference screen.</p></section></main><div id="print-catalog"></div><footer>Source-preserving design package · no account, game, repository, or database changes.</footer><script>
const screens=${data};
const byId=new Map(screens.map(s=>[s.id,s]));
let current='01',history=[],caller='13',menuCaller='09';
const menuIds=new Set(['26','27','28','29','30','31','32','33','35','38','39']);
const sections=['preview','catalog','flow','guide'];
function tab(name){sections.forEach(x=>document.getElementById(x).classList.toggle('hidden',x!==name));}
function show(id,push=true){
 if(!byId.has(id))return;
 if(push&&id!==current)history.push(current);
 const s=byId.get(id);current=id;
 document.getElementById('screen-name').textContent=s.id+' · '+s.title;
 document.getElementById('screen-status').textContent=s.status;
 document.getElementById('canvas').innerHTML=s.svg;
 document.getElementById('purpose').textContent=s.purpose;
 document.getElementById('rule').textContent=s.rule;
 document.getElementById('jump').value=id;
 document.getElementById('back').disabled=!history.length;
 tab('preview');
 window.location.hash='screen-'+id;
}
function go(id){
 if(id==='26'&&!menuIds.has(current)){caller=current;menuCaller=current;}
 if(['30','32','35'].includes(id)&&['08','09'].includes(current)){caller=current;menuCaller=current;}
 if(id==='34')menuCaller=current;
 const restore=(['26','27','28','30','32'].includes(current)&&id==='13')||(['33','35','38','39'].includes(current)&&id==='26');
 if(restore)id=caller;
 if(current==='35'&&id==='26')id=menuCaller;
 if(current==='34'&&id==='09')id=menuCaller;
 if(current==='30'&&id==='31'&&['08','09'].includes(caller))id='13';
 show(id);
}
document.getElementById('canvas').addEventListener('click',e=>{const t=e.target.closest('[data-target]');if(t)go(t.dataset.target);});
document.getElementById('canvas').addEventListener('keydown',e=>{const t=e.target.closest('[data-target]');if(t&&(e.key==='Enter'||e.key===' ')){e.preventDefault();go(t.dataset.target);}});
document.addEventListener('keydown',e=>{if(e.target.matches('select,button')||e.target.closest('[data-target]')||document.getElementById('preview').classList.contains('hidden'))return;if(e.key==='Escape'&&['13','14','15','16','17','18','19','22'].includes(current)){e.preventDefault();go('26');}else if(e.key===' '&&['13','14','16'].includes(current)){e.preventDefault();go(current==='13'?'14':current==='14'?'15':'17');}});
for(const s of screens){const op=document.createElement('option');op.value=s.id;op.textContent=s.id+' · '+s.title;document.getElementById('jump').append(op);const tile=document.createElement('button');tile.className='tile';tile.innerHTML=s.svg+'<div class="caption"><strong>'+s.id+' · '+s.title+'</strong><br>'+s.status+'</div>';tile.setAttribute('aria-label','Open '+s.title);tile.addEventListener('click',()=>show(s.id));document.getElementById('cards').append(tile);}
document.getElementById('jump').addEventListener('change',e=>show(e.target.value));
document.getElementById('back').onclick=()=>{const last=history.pop();if(last)show(last,false);};
document.getElementById('reset').onclick=()=>{history=[];caller='13';menuCaller='09';show('01',false);};
sections.forEach(x=>document.getElementById(x+'-tab').onclick=()=>tab(x));
function fixPrintGradients(){document.querySelectorAll('#print-catalog svg').forEach((svg,i)=>{const gradient=svg.querySelector('linearGradient');if(!gradient)return;const id='print-burgundy-'+i;gradient.id=id;svg.querySelectorAll('[fill="url(#burgundy)"]').forEach(el=>el.setAttribute('fill','url(#'+id+')'));});}
document.getElementById('print').onclick=()=>{document.getElementById('print-catalog').innerHTML='<article class="print-card"><h2>Adaptive Reading Game · Complete UI/UX Reference</h2><p>Design states, not a functional application. Sample content/data. Georgia requested; Times New Roman used locally as fallback.</p>'+${JSON.stringify(flow.replaceAll('id="arrow"','id="arrow-print"').replaceAll('url(#arrow)','url(#arrow-print)')).replaceAll('<','\u003c')}+'</article>'+screens.filter(s=>s.id!=='36').map(s=>'<article class="print-card"><h2>'+s.id+' · '+s.title+' — '+s.status+'</h2>'+s.svg+'<p>'+s.purpose+'</p><p><strong>Interaction:</strong> '+s.rule+'</p></article>').join('');window.print();};
show(byId.has(location.hash.replace('#screen-',''))?location.hash.replace('#screen-',''):'01',false);
window.referenceTest={screens:screens.map(s=>({id:s.id,svg:s.svg})),show,go,get current(){return current;}};
</script></body></html>`;
fs.writeFileSync(path.join(root,'index.html'),html.replace('window.print();};','fixPrintGradients();window.print();};').replace(/[–—]/g,'-'));
const md = ['# Adaptive Reading Game — UI/UX build guide','','This is a proposed complete design reference, not an implementation or a claim that the source features work.','','## Open the reference','','Open `index.html` in a browser. It is self-contained and works without a server or internet connection. Use All screens to inspect every state, Player flow for the overview, and Build guide for priorities and contracts. The Print / Save PDF button lays out the flow and screen explanations for optional export.','','## Scope and source','','Original ZIP: `/home/patt/Desktop/AdaptiveReadingGame-main_UI.zip`. The original is unchanged. The package uses the existing JavaFX theme and flourish paths, and adds the requested assessment, persistence, authentication, and exit states. Frames are 1280 × 720, a 2/3 reference scale of the source 1920 × 1080 canvas.','','Existing base means the layout is retained, not that its backend is complete. Existing code state means a message is in source but not necessarily in submitted screenshots. Proposed addition means a new design state. The closed application endpoint is a guide only.','','## Build order','','1. Shared controls and navigation state.','2. Authentication + main menu + logout.','3. Story dialogue, branches, and pause/resume.','4. Reusable Reading Check, selection, first-attempt recording, and feedback.','5. Java-owned results, validated difficulty, optional-model timeout fallback.','6. Save/load persistence, confirmations, and error recovery.','7. Settings, keyboard navigation, text-size support, and end-of-story handling.','','## Important contracts','','- Narrative choices do not determine comprehension score or difficulty.','- Selection can change before Submit; the first submission is recorded once and cannot be rescored.','- Source UI has six slots per page and a ten-page label, not working pagination. Agree on slot count and backend mapping before implementing.','- Return restores the exact caller, branch, question index, selection, and locked submissions.','- Load should validate the full checkpoint before replacing the live session.','- Save success is shown only after persistence succeeds; keep the prior save intact on failure.','- Record completed answers before optional model analysis; Java owns final difficulty and fallback.','- Result scores and next difficulties are illustrative values, not thresholds.','- Replace placeholder introduction and sample passages with authored, reviewed game content.','- No password recovery, verification email, teacher dashboard, leaderboard, or cloud sync has been assumed. These are outside the supplied requirements.','','## Fonts and Figma import','','Source title: Times New Roman. Source body/menu: Georgia. Georgia is not installed here; the preview falls back explicitly to Times New Roman. No font files are distributed. Install a licensed Georgia font or approve a substitute before final visual signoff. SVGs specify Georgia first.','','Import numbered SVGs from `screens/` into the existing Figma file manually. These are real vector shapes and SVG text, not screenshot images. Figma may alter/outline text during import; verify font handling and rebuild native text if necessary. Native components, auto-layout, variables, and clickable Figma reactions are NOT included by SVG import. Use the interaction notes below to connect prototype routes manually.','','Direct Figma work was blocked by the connected Starter plan tool-call limit. This package does not populate or verify the online Figma file.','','## Preview limitations','','Forms, sliders, model calls, and saving are not functional. No credentials are entered or stored. Screens are representative states linked for review. The preview skips assessment questions 2–4 and shares a sample story branch; production must use real distinct content. The final question reuses sample copy only to show a reusable layout. Save success represents a state, not real storage.','','## Screen explanations and destinations',''];
for(const s of screens)md.push(`### ${s.id} — ${s.title}`,``, `Group: ${s.group}. Status: ${s.status}.`, ``,s.purpose,``, `Interaction: ${s.rule}`,``);
fs.writeFileSync(path.join(root,'BUILD-GUIDE.md'),md.join('\n'));
console.log(JSON.stringify({root,screenCount:screens.length,svgCount:screens.length+1,sourceSha256:crypto.createHash('sha256').update(fs.readFileSync(zip)).digest('hex')},null,2));
