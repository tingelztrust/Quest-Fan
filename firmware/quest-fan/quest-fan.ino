#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <Preferences.h>
#include "secrets.h"

// 两只 5V 两线 4010 并联，经 LR7843。GPIO4 高电平导通。
// 不读 USB。拔掉电脑线之后，芯片只能靠电池接到 5V 脚才继续听网页。
#define FAN_PIN 4
#define PWM_FREQ 1000
#define PWM_BITS 8

WebServer server(80);
Preferences prefs;

bool fanOn = false;
int speedPct = 80;

bool legalSpeed(int pct) {
  return pct == 80 || pct == 90 || pct == 100;
}

int dutyFromPct(int pct) {
  if (!legalSpeed(pct)) pct = 80;
  return (pct * 255) / 100;
}

void applyFan() {
  ledcWrite(FAN_PIN, fanOn ? dutyFromPct(speedPct) : 0);
}

void savePrefs() {
  prefs.putBool("on", fanOn);
  prefs.putInt("spd", speedPct);
}

String jsonState() {
  String s = "{\"on\":";
  s += fanOn ? "true" : "false";
  s += ",\"speed\":";
  s += String(speedPct);
  s += ",\"ip\":\"";
  s += WiFi.localIP().toString();
  s += "\"}";
  return s;
}

void sendState() {
  server.send(200, "application/json", jsonState());
}

void setGear(int pct) {
  if (!legalSpeed(pct)) {
    sendState();
    return;
  }
  speedPct = pct;
  fanOn = true;
  applyFan();
  savePrefs();
  sendState();
}

const char PAGE[] PROGMEM = R"HTML(<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>面罩风扇</title>
<style>
body{margin:0;background:#111;color:#eee;font:18px sans-serif}
main{max-width:420px;margin:0 auto;padding:20px}
h1{font-size:22px;margin:0 0 8px}
p{color:#aaa;font-size:14px;line-height:1.4}
button{font:inherit;width:100%;padding:16px;margin:8px 0;border:0;border-radius:12px;background:#333;color:#eee}
button.on{background:#0a7}
</style>
<main>
<h1>面罩风扇</h1>
<p id="meta">三挡：80、90、100。关就是停。</p>
<button id="b80" type="button">低 80%</button>
<button id="b90" type="button">中 90%</button>
<button id="b100" type="button">高 100%</button>
<button id="boff" type="button">关</button>
</main>
<script>
const ids={80:"b80",90:"b90",100:"b100"};
function paint(j){
  for (const k of [80,90,100]) document.getElementById(ids[k]).className=(j.on&&j.speed==k)?"on":"";
  document.getElementById("boff").className=j.on?"":"on";
  document.getElementById("meta").textContent=(j.on?j.speed+"% 运行":"已关")+" · "+j.ip;
}
async function post(u){paint(await (await fetch(u)).json())}
document.getElementById("b80").onclick=()=>post("/gear?v=80");
document.getElementById("b90").onclick=()=>post("/gear?v=90");
document.getElementById("b100").onclick=()=>post("/gear?v=100");
document.getElementById("boff").onclick=()=>post("/off");
post("/state");
</script>
)HTML";

void setup() {
  ledcAttach(FAN_PIN, PWM_FREQ, PWM_BITS);
  prefs.begin("fan", false);
  speedPct = prefs.getInt("spd", 80);
  if (!legalSpeed(speedPct)) speedPct = 80;
  fanOn = prefs.getBool("on", false);
  applyFan();

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setTxPower(WIFI_POWER_11dBm);
  WiFi.setHostname("questfan");
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  server.on("/", []() { server.send_P(200, "text/html; charset=utf-8", PAGE); });
  server.on("/state", sendState);
  server.on("/off", []() {
    fanOn = false;
    applyFan();
    savePrefs();
    sendState();
  });
  server.on("/gear", []() {
    if (!server.hasArg("v")) { sendState(); return; }
    setGear(server.arg("v").toInt());
  });
  server.begin();
  MDNS.begin("questfan");
}

void loop() {
  server.handleClient();
  if (WiFi.status() != WL_CONNECTED && millis() > 3000) {
    static unsigned long last = 0;
    if (millis() - last > 8000) {
      last = millis();
      WiFi.reconnect();
    }
  }
}
