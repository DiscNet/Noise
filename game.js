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
let fox={x:480,y:320,bounce:0,hover:0};
const foxSprite=new Image();
foxSprite.src="fox.png";
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
  ctx.fillStyle="#76b6c4";ctx.fillRect(0,0,W,H);

  // Céu em faixas para dar profundidade ao cenário.
  rect(0,0,W,115,"#79bdc9");
  rect(0,115,W,70,"#69aeb0");
  rect(0,185,W,70,"#559c91");

  // Nuvens pixeladas.
  drawCloud(150,68,1.25);
  drawCloud(690,91,.85);
  drawCloud(825,48,.65);

  // Montanhas distantes.
  ctx.fillStyle="#477f75";
  ctx.beginPath();
  ctx.moveTo(0,238);ctx.lineTo(0,190);ctx.lineTo(95,122);ctx.lineTo(170,190);
  ctx.lineTo(265,106);ctx.lineTo(375,202);ctx.lineTo(475,130);ctx.lineTo(590,213);
  ctx.lineTo(700,112);ctx.lineTo(810,192);ctx.lineTo(895,132);ctx.lineTo(960,182);
  ctx.lineTo(960,238);ctx.closePath();ctx.fill();

  // Neve/luz nas cristas.
  rect(82,139,25,4,"#b7d1bd");rect(91,134,13,5,"#b7d1bd");
  rect(253,121,28,4,"#a9c9b5");rect(262,116,14,5,"#a9c9b5");
  rect(688,126,25,4,"#b7d1bd");rect(696,121,12,5,"#b7d1bd");

  // Floresta distante.
  rect(0,238,W,18,"#356e54");
  for(let x=-10;x<W+30;x+=31){
    const h=18+(x*7%24+24)%24;
    rect(x,256-h,10,h,"#285d4a");
    rect(x-8,247-h,26,15,"#326f4e");
    rect(x-4,235-h,18,16,"#3d8155");
  }

  // Campo principal.
  rect(0,256,W,284,"#4f9a55");
  rect(0,256,W,8,"#43864e");

  // Caminho de terra em perspectiva.
  ctx.fillStyle="#b78957";
  ctx.beginPath();
  ctx.moveTo(404,540);ctx.lineTo(556,540);ctx.lineTo(514,310);ctx.lineTo(446,310);ctx.closePath();ctx.fill();
  rect(437,328,86,8,"#c79b63");
  rect(444,370,72,7,"#c79b63");
  rect(454,421,53,7,"#c79b63");
  rect(466,474,31,7,"#c79b63");

  // Pequeno lago lateral.
  ctx.fillStyle="#3e8f9b";
  ctx.beginPath();
  ctx.moveTo(0,430);ctx.lineTo(0,350);ctx.lineTo(105,354);ctx.lineTo(175,385);
  ctx.lineTo(153,430);ctx.lineTo(80,451);ctx.closePath();ctx.fill();
  rect(20,377,67,3,"#75c4c5");rect(52,396,82,3,"#75c4c5");
  rect(6,414,55,3,"#6ab6ba");
  const wave=(time*.025)%90;
  for(let i=0;i<4;i++){
    const wx=(wave+i*27)%125;
    rect(wx,365+i*13,22,2,"#78c5c4");
  }

  // Margens de grama.
  for(let x=0;x<W;x+=23){
    const offset=(x*13)%17;
    rect(x,250-offset,4,12+offset,"#397d49");
    rect(x+8,252-(offset>>1),3,10+(offset>>1),"#6aad58");
  }

  // Árvores em primeiro plano.
  drawTree(78,278,1.35);
  drawTree(872,287,1.2);
  drawTree(210,322,.72);
  drawTree(758,337,.78);

  // Arbustos e flores.
  drawBush(305,382);
  drawBush(651,392);
  drawBush(114,472);
  drawBush(846,465);

  for(let i=0;i<26;i++){
    const x=(i*83+29)%W;
    const y=275+(i*47%225);
    if(x>395&&x<565&&y>315)continue;
    drawFlower(x,y,i%3);
  }

  // Pequenos detalhes de terreno.
  for(let i=0;i<18;i++){
    const x=(i*137+41)%W;
    const y=285+(i*71%220);
    rect(x,y,7,3,"#3d8148");
    rect(x+6,y-3,4,3,"#68ad57");
  }

  // Reflexo/luz perto da área da raposa.
  rect(365,283,230,3,"#69a95b");
  for(let i=0;i<7;i++){
    const fx=(i*143+Math.floor(time*.012))%930;
    const fy=295+(i*31%165);
    rect(fx,fy,2,2,i%2?"#d5e17b":"#b9d86e");
  }
}
function drawCloud(x,y,s){
  const p=(a,b,w,h,c)=>rect(x+a*s,y+b*s,w*s,h*s,c);
  p(-30,5,62,12,"#d8e9d7");
  p(-17,-3,25,12,"#e5f1dc");
  p(3,-9,24,18,"#e5f1dc");
  p(21,1,26,12,"#d8e9d7");
}
function drawTree(x,y,s){
  const p=(a,b,w,h,c)=>rect(x+a*s,y+b*s,w*s,h*s,c);
  p(-8,0,16,70,"#70472f");
  p(-47,-24,94,44,"#275b45");
  p(-35,-48,70,42,"#326f4d");
  p(-18,-69,40,34,"#438657");
  p(-44,-9,25,18,"#3b8052");
  p(19,-7,25,17,"#2d6749");
  p(-28,-31,18,10,"#5b9b5c");
}
function drawBush(x,y){
  rect(x-32,y,64,18,"#2e6c47");
  rect(x-24,y-15,48,19,"#397c4d");
  rect(x-8,y-25,22,21,"#54a05a");
  rect(x+16,y-9,20,13,"#438c50");
}
function drawFlower(x,y,type){
  const petals=["#f2c84b","#e9897d","#e8d8a0"][type];
  rect(x,y,3,8,"#397b45");
  rect(x-3,y-2,4,4,petals);
  rect(x+2,y-3,4,4,petals);
  rect(x-1,y-6,4,4,petals);
  rect(x,y-2,3,3,"#f4d86a");
}
function drawFox(){
  if(!foxSprite.complete||foxSprite.naturalWidth===0)return;
  const bob=Math.sin(time*.006)*2;
  const press=fox.bounce>0?3:0;
  const width=128,height=168;
  const x=Math.round(fox.x-width/2);
  const y=Math.round(fox.y-height/2+bob+press);
  ctx.drawImage(foxSprite,x,y,width,height);
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
  ctx.fillRect(418,401,124,7);
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
  coinsWorld.push({x:fox.x+(Math.random()-.5)*28,y:fox.y-80,vx:(Math.random()-.5)*.8,vy:-2.1,life:55,size:10});
}
function clickAt(clientX,clientY){
  const r=canvas.getBoundingClientRect();
  const scaleX=W/r.width,scaleY=H/r.height;
  const x=(clientX-r.left)*scaleX,y=(clientY-r.top)*scaleY;
  const dx=x-fox.x,dy=y-(fox.y-10);
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
  texts.push({x:fox.x,y:fox.y-105,vy:-.8,life:38,text:"+"+reward,c:combo>1?"#ffe36a":"#fff"});
  if(state.clicks%25===0){
    unlockedChest=true;
    say("BAÚ DISPONÍVEL");
    texts.push({x:fox.x,y:fox.y-125,vy:-.5,life:60,text:"25 CLIQUES",c:"#e8c8ff"});
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
  texts.push({x:fox.x,y:fox.y-105,vy:-.6,life:45,text:"PODER +1",c:"#fff"});
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
  texts.push({x:fox.x,y:fox.y-125,vy:-.5,life:70,text:"BAÚ +"+reward,c:"#ffe05b"});
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
