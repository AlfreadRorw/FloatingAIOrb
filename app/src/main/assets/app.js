const $=s=>document.querySelector(s);
const svgNS="http://www.w3.org/2000/svg";
const themes={
 gold:["#e8c66a","#8d6320"],violet:["#b89aff","#5c36a4"],cyan:["#65e9ff","#167d91"],
 red:["#ff7e7e","#8c2020"],blue:["#7eaaff","#3157a3"],emerald:["#75e6a6","#21784b"],rose:["#ff9bc5","#9a3561"]
};
let state={theme:localStorage.arachneTheme||"gold",roman:false,smooth:true,h24:true,showDate:true,ambient:false,glow:70,particles:55};
function save(){localStorage.arachne=JSON.stringify(state)}
try{Object.assign(state,JSON.parse(localStorage.arachne||"{}"))}catch{}
function theme(t){state.theme=t;document.body.dataset.theme=t;save();document.querySelectorAll(".swatch,.theme").forEach(x=>x.classList.toggle("active",x.dataset.theme===t))}
theme(state.theme);

const ticks=$("#ticks"),nums=$("#numbers"),orn=$("#ornaments");
const romans=["XII","I","II","III","IV","V","VI","VII","VIII","IX","X","XI"];
function polar(r,a){let x=300+Math.sin(a)*r,y=300-Math.cos(a)*r;return [x,y]}
for(let i=0;i<60;i++){
 let a=i*Math.PI*2/60,[x1,y1]=polar(252,a),[x2,y2]=polar(i%5===0?238:244,a);
 let l=document.createElementNS(svgNS,"line");l.setAttribute("x1",x1);l.setAttribute("y1",y1);l.setAttribute("x2",x2);l.setAttribute("y2",y2);l.setAttribute("class","tick"+(i%5===0?" major":""));ticks.appendChild(l);
}
for(let i=0;i<12;i++){
 let a=i*Math.PI*2/12,[x,y]=polar(218,a),t=document.createElementNS(svgNS,"text");
 t.setAttribute("x",x);t.setAttribute("y",y);t.setAttribute("class","num");t.textContent=state.roman?romans[i]:String(i===0?12:i);nums.appendChild(t);
}
function ornament(cx,cy,r){
 let p=document.createElementNS(svgNS,"path"),d="";
 for(let i=0;i<=48;i++){let a=i*Math.PI*2/48,rr=r*(.72+.2*Math.sin(a*6)**2),x=cx+Math.cos(a)*rr,y=cy+Math.sin(a)*rr;d+=(i?"L":"M")+x+" "+y}
 p.setAttribute("d",d);p.setAttribute("class","orn");orn.appendChild(p);
}
ornament(178,195,66);ornament(415,195,66);ornament(205,405,54);ornament(392,405,66);

function updateNumbers(){nums.innerHTML="";for(let i=0;i<12;i++){let a=i*Math.PI*2/12,[x,y]=polar(218,a),t=document.createElementNS(svgNS,"text");t.setAttribute("x",x);t.setAttribute("y",y);t.setAttribute("class","num");t.textContent=state.roman?romans[i]:String(i===0?12:i);nums.appendChild(t)}}
function pad(n){return String(n).padStart(2,"0")}
function formatZone(d,tz){try{let p=new Intl.DateTimeFormat("en-US",{timeZone:tz,timeZoneName:"longOffset",hour:"2-digit",minute:"2-digit"}).formatToParts(d);return p.find(x=>x.type==="timeZoneName")?.value.replace("GMT","UTC")||"LOCAL"}catch{return "LOCAL"}}
function tick(){
 const d=new Date(),ms=d.getMilliseconds(),s=d.getSeconds()+ms/1000,m=d.getMinutes()+s/60,h=(d.getHours()%12)+m/60;
 const ha=h*30,ma=m*6,sa=s*6;
 $("#hourHand").style.transform=`rotate(${ha}deg)`;$("#hourHand").style.transformOrigin="300px 300px";
 $("#minuteHand").style.transform=`rotate(${ma}deg)`;$("#minuteHand").style.transformOrigin="300px 300px";
 $("#secondHand").style.transform=`rotate(${state.smooth?sa:Math.floor(s)*6}deg)`;$("#secondHand").style.transformOrigin="300px 300px";
 let hh=state.h24?d.getHours():((d.getHours()%12)||12);
 $("#time").textContent=`${pad(hh)}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
 $("#ampm").textContent=state.h24?"24H":(d.getHours()<12?"AM":"PM");
 $("#date").textContent=d.toLocaleDateString("en-US",{weekday:"long",day:"2-digit",month:"long",year:"numeric"}).toUpperCase();
 $("#date").style.display=state.showDate?"block":"none";
 $("#tz").textContent=formatZone(d,Intl.DateTimeFormat().resolvedOptions().timeZone);
 let tz=$("#city").value==="local"?Intl.DateTimeFormat().resolvedOptions().timeZone:$("#city").value;
 let w=new Intl.DateTimeFormat("en-US",{timeZone:tz,hour:"2-digit",minute:"2-digit",second:"2-digit",hour12:!state.h24}).format(d);
 $("#worldTime").textContent=w;
 $("#worldDate").textContent=new Intl.DateTimeFormat("en-US",{timeZone:tz,weekday:"long",day:"2-digit",month:"long",year:"numeric"}).format(d).toUpperCase();
 $("#zoneName").textContent=tz.split("/").pop().replaceAll("_"," ").toUpperCase();
 $("#zoneOffset").textContent=formatZone(d,tz);
}
setInterval(tick,50);tick();

const canvas=$("#particles"),ctx=canvas.getContext("2d");let pts=[];
function resize(){let r=canvas.getBoundingClientRect(),dpr=devicePixelRatio||1;canvas.width=r.width*dpr;canvas.height=r.height*dpr;ctx.setTransform(dpr,0,0,dpr,0,0)}
function makePts(){pts=[];let n=Math.round(state.particles*1.2);for(let i=0;i<n;i++)pts.push({a:Math.random()*6.28,r:.25+Math.random()*.44,s:.0002+Math.random()*.0007,o:.1+Math.random()*.5})}
function particles(t){let r=canvas.getBoundingClientRect(),cx=r.width/2,cy=r.height/2,R=Math.min(r.width,r.height)/2;ctx.clearRect(0,0,r.width,r.height);for(let p of pts){p.a+=p.s;let x=cx+Math.cos(p.a)*R*p.r,y=cy+Math.sin(p.a)*R*p.r;ctx.fillStyle=`rgba(255,255,255,${p.o})`;ctx.beginPath();ctx.arc(x,y,Math.random()*1.2+.3,0,6.28);ctx.fill()}requestAnimationFrame(particles)}
addEventListener("resize",resize);resize();makePts();requestAnimationFrame(particles);

document.querySelectorAll(".swatch").forEach(x=>x.onclick=()=>theme(x.dataset.theme));
const swatches=$("#swatches");Object.keys(themes).forEach(t=>{let b=document.createElement("button");b.className="swatch";b.dataset.theme=t;b.title=t;b.style.background=`radial-gradient(circle at 35% 30%,${themes[t][0]},${themes[t][1]})`;swatches.appendChild(b)});
theme(state.theme);
const grid=$("#themeGrid");Object.keys(themes).forEach(t=>{let b=document.createElement("button");b.className="theme";b.dataset.theme=t;b.innerHTML=`<b style="background:${themes[t][0]}"></b>${t.toUpperCase()}`;b.onclick=()=>theme(t);grid.appendChild(b)});

$("#roman").checked=state.roman;$("#smooth").checked=state.smooth;$("#glow").value=state.glow;$("#particleCount").value=state.particles;
$("#roman").onchange=e=>{state.roman=e.target.checked;updateNumbers();save()};$("#smooth").onchange=e=>{state.smooth=e.target.checked;save()};
$("#glow").oninput=e=>{state.glow=+e.target.value;document.documentElement.style.setProperty("--glow",state.glow);$(".halo").style.opacity=state.glow/700;save()};
$("#particleCount").oninput=e=>{state.particles=+e.target.value;makePts();save()};
$("#settingsBtn").onclick=()=>{$("#settings").classList.add("open");$("#backdrop").classList.add("open")};
$("#closeSettings").onclick=$("#backdrop").onclick=()=>{$("#settings").classList.remove("open");$("#backdrop").classList.remove("open")};
function sideState(){ $("#toggle24").innerHTML=`24 HOUR <b>${state.h24?"ON":"OFF"}</b>`;$("#toggleDate").innerHTML=`DATE <b>${state.showDate?"ON":"OFF"}</b>`;$("#toggleNumbers").innerHTML=`NUMERALS <b>${state.roman?"ROMAN":"ARABIC"}</b>`;$("#ambientSide").innerHTML=`CINEMATIC <b>${state.ambient?"ON":"OFF"}</b>`}
$("#toggle24").onclick=()=>{state.h24=!state.h24;sideState();save()};$("#toggleDate").onclick=()=>{state.showDate=!state.showDate;sideState();save()};$("#toggleNumbers").onclick=()=>{state.roman=!state.roman;$("#roman").checked=state.roman;updateNumbers();sideState();save()};
function ambient(){state.ambient=!state.ambient;document.body.classList.toggle("ambient",state.ambient);sideState();save()}
$("#ambientBtn").onclick=ambient;$("#ambientSide").onclick=ambient;
$("#fullscreen").onclick=()=>document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen?.();
sideState();

let start=0,elapsed=0,running=false,raf=0;
function chronoText(v){let cs=Math.floor(v/10)%100,s=Math.floor(v/1000)%60,m=Math.floor(v/60000);return `${pad(m)}:${pad(s)}.${pad(cs)}`}
function chrono(){if(running)elapsed=Date.now()-start;$("#chrono").textContent=chronoText(elapsed);raf=requestAnimationFrame(chrono)}
$("#startStop").onclick=()=>{if(!running){start=Date.now()-elapsed;running=true;$("#startStop").textContent="STOP";chrono()}else{running=false;cancelAnimationFrame(raf);$("#startStop").textContent="START"}};
$("#reset").onclick=()=>{running=false;elapsed=0;$("#startStop").textContent="START";$("#chrono").textContent="00:00.00";$("#laps").innerHTML=""};
$("#lap").onclick=()=>{if(!elapsed)return;let d=document.createElement("div");d.textContent=`LAP ${$("#laps").children.length+1}   ${chronoText(elapsed)}`;$("#laps").prepend(d)};

let last=performance.now(),frames=0;function fps(now){frames++;if(now-last>1000){$("#fps").textContent=frames+" FPS";frames=0;last=now}requestAnimationFrame(fps)}requestAnimationFrame(fps);
