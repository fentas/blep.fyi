# Backlog — to investigate / research

Ideas parked for later, with enough design to pick up cold. Not committed work —
these need spiking/validation (most touch the BLE scan path and need real hardware).
See [detection.md](detection.md) for the shipped detection design.

## Device identity & correlation

Shipped today: `RotationTracker` correlates rotating addresses into logical devices
via the RSSI handover (one id goes quiet as a new one appears at the same range),
with branching for ambiguous collisions. The items below build a real **identity
layer** on top of it. Suggested order is 1 → 4; each step ships value on its own.

### 1. Internal-identity spine (`IdentityStore`)  ⟵ start here
- Give each `RotationTracker` lineage a **stable internal id**. Expose
  `resolve(observation) → [(identityId, probability, why)]` — return **provenance**
  (`why`: payload-match / same-range-as-dying-id / strong-co-presence), not a bare score.
- **Re-key `DeviceAliases` / `DeviceFlags` on the internal id** → renames and flags
  **survive rotation** (today they die with the address). This is the user-visible win
  and the reason to do any of this.
- Degrade gracefully: when evidence can't carry the link across a rotation, lose it
  (like today) — **never mis-attach** (a wrong merge would move your rename onto a
  stranger's device).

### 2. Feature enrichment — payload bridge (existing task #46)
- Plumb the advertisement payload from the scanner into the correlator: manufacturer-
  specific data, service UUIDs, TX power, and any **sequence counters** (which often
  increment without rotating in lockstep with the MAC). An exact payload match across a
  handover makes the link near-certain even when RSSI is ambiguous.
- Touches the platform BLE layer (`AndroidBleScanner` / `KableScanner`) → the sighting
  model → `RotationTracker`. Critical scan path; needs real BLE to validate. This is the
  **fuel** the identity layer is starved without.

### 3. Co-presence graph + clustering
- Maintain a **weighted, undirected** identity↔identity graph: how often two identities
  appear together at the same range. It **has cycles** (phone–watch–earbuds co-occur), so
  it's a graph, *not* a DAG.
- **Union-find clusters** = "things that travel together" (your usual crowd). The
  anti-stalking payoff: a tracker that follows you but is **not** in your travel cluster
  is the smoking gun. Generalizes the safety detector's `backdropFingerprint`.

### 4. GATT deep-fingerprint (strongest, on-demand — pairs with the flag feature)
- For a **flagged** device: connect (no pairing needed) + service discovery → the GATT
  structure (standard + custom service UUIDs, firmware/hardware revision) is a **static
  fingerprint that survives MAC rotation** (e.g. a Sony WH-1000XM4's profile is
  unmistakable). Costs a connection (battery/time; a rotating device may refuse it while
  advertising), so do it only for the one device the user flagged.

### Out of scope
- **Radiometric / clock-skew fingerprinting** — needs specialized hardware or sample
  rates that blow past phone BLE limits + battery.

### Principles to hold the line on
- **Conservative merges** — high-precision signals only (stable address, exact payload,
  strong co-presence); stay forked-and-explicit otherwise.
- **Explainable + confidence-scored** — surface the *why*; never silently override the user.
- **On-device, bounded, decays, one-tap clearable** — an identity/co-presence graph is a
  behavioural fingerprint of the user's life; same privacy stance as the baseline-allowlist
  note in detection.md.
- **Don't over-build** into a learned record-linkage system — grow the explainable heuristic.
