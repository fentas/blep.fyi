import { createWidget, widget, prop, align, text_style } from "@zos/ui";
import { getDeviceInfo } from "@zos/device";
import { back } from "@zos/router";
import {
  Vibrator,
  VIBRATOR_SCENE_SHORT_STRONG,
  VIBRATOR_SCENE_DURATION,
} from "@zos/sensor";
import { startScan } from "../libs/ble";
import {
  ema,
  proximityOf,
  proximityColor,
  hapticInterval,
  roughDistanceM,
  trend,
  PINPOINT_PROXIMITY,
  COMPLETE_PROXIMITY,
} from "../libs/signal";

const { width: W, height: H } = getDeviceInfo();
const CX = W / 2;
const CY = H / 2;
const LOST_MS = 4000; // no advert for this long → "signal lost"

Page({
  state: {
    mac: null,
    name: "",
    rssiEma: null,
    lastSeen: 0,
    prevProx: 0,
    stop: null,
    render: null,
    hapticT: null,
    vibrator: null,
  },

  onInit(param) {
    try {
      const p = JSON.parse(param || "{}");
      this.state.mac = p.mac || null;
      this.state.name = p.name || "";
    } catch (e) {
      this.state.mac = null;
    }
  },

  build() {
    const s = this.state;

    s.bg = createWidget(widget.FILL_RECT, {
      x: 0, y: 0, w: W, h: H, color: proximityColor(0),
    });

    // Faint full ring (track) + the progress arc on top.
    const m = Math.round(W * 0.05);
    this.arcBox = { x: m, y: m, w: W - 2 * m, h: H - 2 * m };
    const lw = Math.max(8, Math.round(W * 0.025));

    createWidget(widget.ARC, {
      ...this.arcBox, start_angle: 0, end_angle: 360,
      color: 0x000000, line_width: lw,
    });
    this.arcBase = { ...this.arcBox, start_angle: 0, line_width: lw, color: 0x12202e };
    s.arc = createWidget(widget.ARC, { ...this.arcBase, end_angle: 0 });

    createWidget(widget.TEXT, {
      x: 0, y: Math.round(H * 0.16), w: W, h: 28,
      color: 0x12202e, text_size: Math.round(W * 0.05),
      align_h: align.CENTER_H, align_v: align.CENTER_V,
      text_style: text_style.NONE,
      text: this.state.name && this.state.name.length ? this.state.name : "tracking",
    });

    s.pct = createWidget(widget.TEXT, {
      x: 0, y: CY - Math.round(H * 0.16), w: W, h: Math.round(H * 0.24),
      color: 0x0c1620, text_size: Math.round(W * 0.22),
      align_h: align.CENTER_H, align_v: align.CENTER_V,
      text_style: text_style.NONE, text: "··",
    });

    s.guide = createWidget(widget.TEXT, {
      x: 0, y: CY + Math.round(H * 0.10), w: W, h: 30,
      color: 0x12202e, text_size: Math.round(W * 0.06),
      align_h: align.CENTER_H, align_v: align.CENTER_V,
      text_style: text_style.NONE, text: "move around to get a read",
    });

    s.dist = createWidget(widget.TEXT, {
      x: 0, y: CY + Math.round(H * 0.16), w: W, h: 24,
      color: 0x35506a, text_size: Math.round(W * 0.042),
      align_h: align.CENTER_H, align_v: align.CENTER_V,
      text_style: text_style.NONE, text: "",
    });

    const bw = Math.round(W * 0.22);
    createWidget(widget.BUTTON, {
      x: Math.round(CX - bw / 2), y: Math.round(H * 0.8), w: bw, h: Math.round(H * 0.1),
      radius: Math.round(H * 0.05), text: "‹", text_size: Math.round(W * 0.07),
      normal_color: 0x000000, press_color: 0x222222, color: 0xffffff,
      click_func: () => back(),
    });

    try {
      s.vibrator = new Vibrator();
      s.vibrator.setMode(VIBRATOR_SCENE_SHORT_STRONG);
    } catch (e) {
      s.vibrator = null;
    }

    s.stop = startScan((d) => {
      if (d.mac !== s.mac) return;
      s.rssiEma = ema(s.rssiEma, d.rssi);
      s.lastSeen = Date.now();
    });

    s.render = setInterval(() => this.render(), 200);
    this.scheduleHaptic();
  },

  render() {
    const s = this.state;
    const lost = s.rssiEma == null || Date.now() - s.lastSeen > LOST_MS;

    if (lost) {
      s.bg.setProperty(prop.MORE, { x: 0, y: 0, w: W, h: H, color: 0x2a2f38 });
      s.arc.setProperty(prop.MORE, { ...this.arcBase, color: 0x3a4250, end_angle: 0 });
      s.pct.setProperty(prop.TEXT, "··");
      s.guide.setProperty(prop.TEXT, "signal lost — move around");
      s.dist.setProperty(prop.TEXT, "");
      s.curProx = 0;
      return;
    }

    const prox = proximityOf(s.rssiEma);
    s.curProx = prox;

    s.bg.setProperty(prop.MORE, { x: 0, y: 0, w: W, h: H, color: proximityColor(prox) });
    s.arc.setProperty(prop.MORE, {
      ...this.arcBase, color: 0x12202e, end_angle: 360 * prox,
    });
    s.pct.setProperty(prop.TEXT, Math.round(prox * 100) + "%");

    let guide;
    if (prox >= COMPLETE_PROXIMITY) guide = "right here 🎯";
    else if (prox >= PINPOINT_PROXIMITY) guide = "almost on it";
    else {
      const t = trend(s.prevProx, prox);
      guide = t === "warmer" ? "warmer — keep going"
        : t === "colder" ? "colder — turn around"
        : "move around to get a read";
    }
    s.guide.setProperty(prop.TEXT, guide);

    const d = roughDistanceM(s.rssiEma);
    const distStr = d < 1.5 ? "almost on it" : "~" + (d < 10 ? d.toFixed(1) : Math.round(d)) + " m";
    s.dist.setProperty(prop.TEXT, distStr + "   " + Math.round(s.rssiEma) + " dBm");

    s.prevProx = prox;
  },

  // Self-scheduling pulse: the gap shrinks as you close in (Geiger-counter feel).
  scheduleHaptic() {
    const s = this.state;
    const prox = s.curProx || 0;
    const gap = hapticInterval(prox);
    if (gap > 0 && s.vibrator) {
      try {
        if (prox >= COMPLETE_PROXIMITY) s.vibrator.setMode(VIBRATOR_SCENE_DURATION);
        else s.vibrator.setMode(VIBRATOR_SCENE_SHORT_STRONG);
        s.vibrator.start();
      } catch (e) {}
    }
    s.hapticT = setTimeout(() => this.scheduleHaptic(), gap > 0 ? gap : 400);
  },

  onDestroy() {
    const s = this.state;
    if (s.render) clearInterval(s.render);
    if (s.hapticT) clearTimeout(s.hapticT);
    if (s.stop) s.stop();
    if (s.vibrator) {
      try { s.vibrator.stop(); } catch (e) {}
    }
  },
});
