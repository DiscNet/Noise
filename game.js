"use strict";

const canvas=document.getElementById("game");
const ctx=canvas.getContext("2d",{alpha:false});
ctx.imageSmoothingEnabled=false;

const W=960,H=540;
const storeKey="fox-clicker-v2";
const ui={
  coins:document.getElementById("coins"),
  level:document.getElementById("level"),
  power:document.getElementById("power"),
  cost:document.getElementById("upgrade-cost"),
  progress:document.getElementById("progress"),
  progressText:document.getElementById("chest-progress"),
  chest:document.getElementById("chest"),
  chestReward:document.getElementById("chest-reward"),
  combo:document.getElementById("combo"),
  message:document.getElementById("message"),
  upgrade:document.getElementById("upgrade"),
  reset:document.getElementById("reset")
};

const defaultState={coins:0,level:1,power:1,cost:12,clicks:0,totalClicks:0};
let state=load();
let fox={x:480,y:310,bounce:0,hover:0};
let particles=[];
let texts=[];
let coinsWorld=[];
let motes=[];
let time=0;
let last=0;
let messageTimer=0;
let combo=1;
let comboTimer=0;
let unlockedChest=false;

function load(){
  try{
    const raw=JSON.parse(localStorage.getItem(storeKey)||"null");
    if(!raw)return {...defaultState};
    return {
      coins:num(raw.coins,0,0),
      level:num(raw.level,1,1),
      power:num(raw.power,1,1),
      cost:num(raw.cost,12,1),
      clicks:num(raw.clicks,0,0),
      totalClicks:num(raw.totalClicks,0,0)
    };
  }catch{return {...defaultState}}
}
function num(value,fallback,min){
  const n=Number(value);
  return Number.isFinite(n)&&n>=min?n:fallback;
}
function save(){
  try{localStorage.setItem(storeKey,JSON.stringify(state))}catch{}
}
function updateUI(){
  ui.coins.textContent=Math.floor(state.coins).toLocaleString("pt-BR");
  ui.level.textContent=state.level;
  ui.power.textContent=state.power;
  ui.cost.textContent=state.cost.toLocaleString("pt-BR");
  const progress=state.clicks%25;
  ui.progress.style.width=(progress/25*100)+"%";
  ui.progressText.textContent=progress+" / 25";
  ui.chestReward.textContent="+"+(state.power*25);
  unlockedChest=progress===0&&state.clicks>0;
  ui.chest.disabled=!unlockedChest;
  ui.upgrade.disabled=state.coins<state.cost;
  ui.combo.innerHTML="COMBO <strong>x"+combo+"</strong>";
}
function say(text){
  ui.message.textContent=text;
  ui.message.classList.remove("show");
  void ui.message.offsetWidth;
  ui.message.classList.add("show");
}
function resize(){
  canvas.style.width="min(100vw,177.7778vh)";
  canvas.style.height="min(100vh,56.25vw)";
}
window.addEventListener("resize",resize,{passive:true});
resize();

function rect(x,y,w,h,c){ctx.fillStyle=c;ctx.fillRect(Math.round(x),Math.round(y),Math.round(w),Math.round(h))}
function world(){
  ctx.fillStyle="#163b40";ctx.fillRect(0,0,W,H);
  rect(0,0,W,330,"#237d76");
  for(let y=0;y<330;y+=30)for(let x=0;x<W;x+=30){
    if((x/30+y/30)%2===0)rect(x,y,30,30,"#287f74");
  }
  rect(0,330,W,210,"#4c9854");
  for(let x=0;x<W;x+=24){
    rect(x,326,4,15,"#34794a");rect(x+8,321,3,20,"#34794a");rect(x+15,327,4,14,"#3e844d");
  }
  for(let i=0;i<12;i++){
    const x=(i*97+50)%W,y=60+(i*61)%240;
    rect(x,y,3,3,i%2?"#70b17d":"#b5d06d");
  }
  rect(82,245,80,5,"#72b969");rect(104,239,32,6,"#72b969");
  rect(755,268,92,5,"#72b969");rect(784,262,30,6,"#72b969");
  rect(182,119,50,4,"#43a08b");rect(200,113,17,6,"#43a08b");
  rect(650,150,58,4,"#43a08b");rect(672,144,17,6,"#43a08b");
  drawTree(72,180,1.1);drawTree(850,200,.9);drawBush(275,355);drawBush(690,372);
}
function drawTree(x,y,s){
  rect(x-7*s,y,14*s,66*s,"#6d472d");
  rect(x-42*s,y-20*s,84*s,45*s,"#285d46");
  rect(x-31*s,y-44*s,62*s,42*s,"#347553");
  rect(x-13*s,y-59*s,31*s,30*s,"#43865a");
  rect(x-27*s,y-28*s,12*s,10*s,"#65a766");
}
function drawBush(x,y){
  rect(x-28,y,56,17,"#326f48");rect(x-19,y-13,39,20,"#3d8450");rect(x-7,y-23,18,18,"#55a15b");
}
function drawFox(){
  const bob=Math.sin(time*.006)*2;
  const press=fox.bounce>0?3:0;
  const x=Math.round(fox.x),y=Math.round(fox.y+bob+press);
  ctx.save();ctx.translate(x,y);
  const p=(a,b,w,h,c)=>rect(a,b,w,h,c);
  p(-58,34,116,67,"#d95c2d");
  p(-46,82,28,27,"#b5462d");p(18,82,28,27,"#b5462d");
  p(-22,66,44,37,"#ffad35");p(-11,72,22,27,"#fff0bd");
  p(-67,5,134,104,"#e56a2e");
  p(-55,-28,39,34,"#d75a2d");p(16,-28,39,34,"#d75a2d");
  p(-47,-18,26,23,"#ffd064");p(21,-18,26,23,"#ffd064");
  p(-58,-30,28,13,"#6d3040");p(30,-30,28,13,"#6d3040");
  p(-50,14,31,26,"#fff0bd");p(19,14,31,26,"#fff0bd");
  p(-36,18,14,14,"#172a2d");p(22,18,14,14,"#172a2d");
  p(-7,35,14,11,"#172a2d");p(-17,47,34,10,"#fff0bd");
  p(-61,47,24,18,"#ffad35");p(37,47,24,18,"#ffad35");
  p(-67,105,29,17,"#ffd064");p(38,105,29,17,"#ffd064");
  p(45,73,54,15,"#c64e2b");p(79,65,26,19,"#b9472d");p(94,52,18,18,"#b9472d");
  ctx.restore();
}
function drawCoins(){
  for(const c of coinsWorld){
    c.y+=c.vy;c.vy+=.035;c.x+=c.vx;c.life--;
    coin(c.x,c.y,c.size);
  }
  coinsWorld=coinsWorld.filter(c=>c.life>0);
}
function coin(x,y,s=13){
  x=Math.round(x);y=Math.round(y);
  rect(x,y,s,s,"#89501d");
  rect(x+4,y-4,s,s,"#f6c63b");
  rect(x+6,y-2,5,7,"#fff09a");
}
function drawParticles(){
  for(const p of particles){
    p.x+=p.vx;p.y+=p.vy;p.vy+=.04;p.life--;
    rect(p.x,p.y,p.s,p.s,p.c);
  }
  particles=particles.filter(p=>p.life>0);
  for(const t of texts){
    t.y+=t.vy;t.life--;
    ctx.globalAlpha=Math.max(0,t.life/38);
    ctx.font="900 18px Courier New";ctx.textAlign="center";ctx.fillStyle=t.c;
    ctx.fillText(t.text,t.x,t.y);
  }
  ctx.globalAlpha=1;
  texts=texts.filter(t=>t.life>0);
}
function drawFoxShadow(){
  ctx.fillStyle="rgba(20,47,42,.3)";
  ctx.fillRect(420,421,120,7);
}
function spawnClick(){
  for(let i=0;i<Math.min(12,state.power+3);i++){
    particles.push({
      x:fox.x+(Math.random()-.5)*70,y:fox.y-20,
      vx:(Math.random()-.5)*2.5,vy:-2-Math.random()*2,
      s:Math.random()>0.5?5:7,life:30+Math.random()*20,
      c:i%3===0?"#fff1a0":i%3===1?"#f6c63b":"#ffad35"
    });
  }
  coinsWorld.push({x:fox.x+(Math.random()-.5)*28,y:fox.y-75,vx:(Math.random()-.5)*.8,vy:-2.1,life:55,size:10});
}
function clickAt(clientX,clientY){
  const r=canvas.getBoundingClientRect();
  const scaleX=W/r.width,scaleY=H/r.height;
  const x=(clientX-r.left)*scaleX,y=(clientY-r.top)*scaleY;
  const dx=x-fox.x,dy=y-(fox.y-20);
  if(dx*dx+dy*dy>95*95)return;
  const now=performance.now();
  if(now-comboTimer<900)combo=Math.min(9,combo+1);else combo=1;
  comboTimer=now;
  const reward=state.power*combo;
  state.coins+=reward;
  state.clicks++;
  state.totalClicks++;
  fox.bounce=120;
  spawnClick();
  texts.push({x:fox.x,y:fox.y-110,vy:-.8,life:38,text:"+"+reward,c:combo>1?"#ffe36a":"#fff"});
  if(state.clicks%25===0){
    unlockedChest=true;
    say("BAÚ DISPONÍVEL");
    texts.push({x:fox.x,y:fox.y-145,vy:-.5,life:60,text:"25 CLIQUES",c:"#e8c8ff"});
  }
  if(state.clicks%10===0)say("BÔNUS DE CLIQUE");
  save();updateUI();
}
canvas.addEventListener("pointerdown",e=>{
  if(e.button!==undefined&&e.button!==0)return;
  clickAt(e.clientX,e.clientY);
});
ui.upgrade.addEventListener("click",()=>{
  if(state.coins<state.cost)return;
  state.coins-=state.cost;
  state.level++;
  state.power++;
  state.cost=Math.ceil(state.cost*1.58);
  fox.bounce=120;
  texts.push({x:fox.x,y:fox.y-115,vy:-.6,life:45,text:"PODER +1",c:"#fff"});
  say("RAPOSA MELHORADA");
  save();updateUI();
});
ui.chest.addEventListener("click",()=>{
  if(!unlockedChest)return;
  const reward=state.power*25;
  state.coins+=reward;
  unlockedChest=false;
  state.clicks=0;
  for(let i=0;i<20;i++)coinsWorld.push({x:fox.x+(Math.random()-.5)*120,y:fox.y-20,vx:(Math.random()-.5)*2.2,vy:-3-Math.random()*2,life:70,size:12});
  texts.push({x:fox.x,y:fox.y-135,vy:-.5,life:70,text:"BAÚ +"+reward,c:"#ffe05b"});
  say("BAÚ ABERTO");
  save();updateUI();
});
ui.reset.addEventListener("click",()=>{
  if(!confirm("Resetar todo o progresso?"))return;
  state={...defaultState};combo=1;comboTimer=0;unlockedChest=false;particles=[];texts=[];coinsWorld=[];save();updateUI();say("PROGRESSO RESETADO");
});

function frame(t){
  const dt=Math.min(40,t-last||16);last=t;time=t;
  if(fox.bounce>0)fox.bounce=Math.max(0,fox.bounce-dt);
  if(performance.now()-comboTimer>1100&&combo>1){combo=1;updateUI()}
  ctx.clearRect(0,0,W,H);
  world();drawFoxShadow();drawCoins();drawFox();drawParticles();
  requestAnimationFrame(frame);
}
updateUI();
requestAnimationFrame(frame);
