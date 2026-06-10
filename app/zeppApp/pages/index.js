import { createWidget, widget, prop, align, text_style } from "@zos/ui";
import { getDeviceInfo } from "@zos/device";
import { push } from "@zos/router";
import { startScan } from "../libs/ble";

const { width: W, height: H } = getDeviceInfo();

// Drop a device from the list if we haven't heard an advert in this long.
const STALE_MS = 12000;

Page({
  state: {
    devices: {}, // mac -> { mac, name, rssi, t }
    rows: [], // { base, mac }
    stop: null,
    timer: null,
  },

  build() {
    createWidget(widget.FILL_RECT, { x: 0, y: 0, w: W, h: H, color: 0x111318 });

    createWidget(widget.TEXT, {
      x: 0, y: Math.round(H * 0.07), w: W, h: 40,
      color: 0xf4d58d, text_size: Math.round(W * 0.075),
      align_h: align.CENTER_H, align_v: align.CENTER_V,
      text_style: text_style.NONE, text: "blep",
    });

    this.subtitle = createWidget(widget.TEXT, {
      x: 0, y: Math.round(H * 0.07) + 44, w: W, h: 26,
      color: 0x8fa0b0, text_size: Math.round(W * 0.045),
      align_h: align.CENTER_H, align_v: align.CENTER_V,
      text_style: text_style.NONE, text: "scanning…",
    });

    // A small fixed stack of rows; we fill the strongest devices in and park the
    // unused ones off-screen (ZeppOS has no runtime visibility flag).
    const top = Math.round(H * 0.07) + 84;
    const rowH = Math.round(H * 0.12);
    const gap = Math.round(H * 0.02);
    const rowCount = Math.max(3, Math.floor((H - top - rowH * 0.4) / (rowH + gap)));
    const x = Math.round(W * 0.08);
    const w = Math.round(W * 0.84);

    this.state.rows = [];
    for (let i = 0; i < rowCount; i++) {
      const base = { x, y: top + i * (rowH + gap), w, h: rowH };
      const btn = createWidget(widget.BUTTON, {
        ...base, radius: 14,
        normal_color: 0x1e2230, press_color: 0x2c3346,
        text: "", text_size: Math.round(W * 0.05), color: 0xe8eef6,
        click_func: () => this.onPick(i),
      });
      btn.setProperty(prop.MORE, { ...base, y: -400, text: "" }); // start parked
      this.state.rows.push({ btn, base, mac: null });
    }

    this.state.stop = startScan((d) => {
      const prev = this.state.devices[d.mac];
      this.state.devices[d.mac] = {
        mac: d.mac,
        name: d.name || (prev && prev.name) || "",
        rssi: d.rssi,
        t: Date.now(),
      };
    });
    this.state.timer = setInterval(() => this.refresh(), 1000);
  },

  refresh() {
    const now = Date.now();
    const list = Object.keys(this.state.devices)
      .map((k) => this.state.devices[k])
      .filter((d) => now - d.t < STALE_MS)
      .sort((a, b) => b.rssi - a.rssi);

    this.subtitle.setProperty(
      prop.TEXT,
      list.length ? list.length + " nearby · pick one" : "scanning…"
    );

    for (let i = 0; i < this.state.rows.length; i++) {
      const row = this.state.rows[i];
      const d = list[i];
      if (d) {
        const name = d.name && d.name.length ? d.name : "· " + d.mac.slice(-5);
        row.btn.setProperty(prop.MORE, { ...row.base, text: name + "   " + d.rssi + " dBm" });
        row.mac = d.mac;
        row.name = d.name;
      } else {
        row.btn.setProperty(prop.MORE, { ...row.base, y: -400, text: "" });
        row.mac = null;
      }
    }
  },

  onPick(i) {
    const row = this.state.rows[i];
    if (!row || !row.mac) return;
    push({
      url: "pages/track",
      params: JSON.stringify({ mac: row.mac, name: row.name || "" }),
    });
  },

  onDestroy() {
    if (this.state.timer) clearInterval(this.state.timer);
    if (this.state.stop) this.state.stop();
  },
});
