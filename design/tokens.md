# BLEP — Design Tokens

Single source of truth for the visual language shared by the **app** (Compose
Multiplatform) and the **website** (Tailwind). Values are derived from
[`logo.svg`](../logo.svg) — the "blep" mascot.

## Brand palette (from the logo)

| Token            | Hex       | Origin in logo            |
| ---------------- | --------- | ------------------------- |
| `brand.blue`     | `#5F90C3` | mascot body               |
| `brand.pink`     | `#FAB1B7` | tongue / cheeks           |
| `brand.cream`    | `#F4F5F0` | belly / highlight         |
| `brand.ink`      | `#27313B` | text on light             |
| `brand.mist`     | `#EEF2F6` | app / page background     |

## Proximity scale (the "getting warmer" gradient)

The tracking UI shifts the **background** and the **arrow** along this scale as
the target gets closer. `0.0` = far (cool), `1.0` = pinpoint (warm).

| Stop | Position | Hex       | Feeling          |
| ---- | -------- | --------- | ---------------- |
| Far  | 0.00     | `#7FA8D4` | cool pastel blue |
| —    | 0.40     | `#8FD0CB` | calm teal        |
| —    | 0.70     | `#AEDFA6` | pastel green     |
| Close| 1.00     | `#F4D58D` | warm pastel gold |

Interpolate in linear RGB between adjacent stops.

## Typography

Geometric, high-contrast sans. Self-hosted **Inter** (variable) with the system
geometric sans as fallback.

```
font.sans  : "Inter", "SF Pro Display", system-ui, -apple-system, sans-serif
```

| Role       | Size (web) | Weight | Tracking |
| ---------- | ---------- | ------ | -------- |
| display    | clamp 2.5–4rem | 700 | -0.03em |
| title      | 1.5rem     | 600    | -0.02em  |
| body       | 1rem       | 400    | 0        |
| caption    | 0.8125rem  | 500    | 0.01em   |
| instruction| 1.25rem    | 500    | -0.01em  |

## Motion

Subtle, never flashy. Spring-like ease, short durations.

| Token              | Value                                   |
| ------------------ | --------------------------------------- |
| `motion.fast`      | 180ms                                   |
| `motion.base`      | 320ms                                   |
| `motion.slow`      | 600ms                                   |
| `motion.ease`      | cubic-bezier(0.22, 1, 0.36, 1)          |
| `motion.color`     | 800ms (proximity background crossfade)  |

## Radii & spacing

```
radius.card : 20px
radius.pill : 999px
space.unit  : 4px   (use multiples: 4/8/12/16/24/32/48)
```
