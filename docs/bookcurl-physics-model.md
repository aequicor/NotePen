# NotePen Book Page-Curl — Physically-Grounded 2.5D Paper Model: Specification & Phased Implementation Plan

## 0. Scope and the central tradeoff

The current curl reads as a "decoratively bent ribbon": one uniform cylinder radius over the whole height, no top/bottom difference, a too-perfect free edge, a grey slab edge, near-zero sag, no contact with the book, a shadow that looks like a printed gradient, and no local deformation. Root cause in code: `BookCurlPhysics.mesh()` (`BookCurlPhysics.kt`) drives the shape from a **single scalar** `sagFactor = weight / stiffness` and a **constant** `CURL_RADIUS_FRACTION`, and `mesh()` is a **pure, stateless** function memoized by `remember(...)` in `BookCurlOverlay.kt` — so it can never lag/oscillate (no inertia is structurally possible today).

**The tradeoff, stated plainly:** physical fidelity vs sustained 60 fps on a single-threaded Kotlin render path on a Huawei Kirin/EMUI tablet.

- The **physical-fidelity judge** ranked **PBD Cloth** first (9/10) because all 10 effects truly *emerge* from forces/constraints rather than being authored; AQSD and Hybrid (7 and 6) are "choreography tuned to look physical."
- The **performance/Huawei judge** ranked **AQSD and Hybrid tied first (9/9)** — same complexity class as today's `curlPoint` (one `sin`+`cos` per vertex) — and **PBD last (4/10)**: ~10× the per-frame work (dihedral bending + per-vertex normal recompute + multi-iteration Gauss-Seidel), with the worst possible coupling — *stiffness raises both cost and instability*, so Cardboard (the heaviest visual preset) is also the heaviest and most explosion-prone compute, exactly during the headline animation, with quality-downgrade-mid-animation as the only escape hatch.
- The **integration/determinism judge** ranked **Hybrid first (8/10)**, AQSD a close second (7), PBD last (4.5): PBD *requires* persistent per-particle `pos/prev` across frames, which breaks the stateless `remember(mesh)` memoization, forces a real overlay-loop + test-harness rewrite, and makes the existing `meshHasFiniteCoordinatesForViolentDrag` invariant far more likely to flake under an explicit solver.

### Recommended architecture: **Hybrid Developable Paper Model (HDPM)**

Adopt the **Hybrid** design: an **analytic arc-length developable base** (keeps inextensibility exact-by-construction and text foreshortening free) with a **non-uniform per-row curvature field** `k(s,v)` driven directly by material-derived constants, **plus a thin per-frame dynamic layer** (a handful of spring-damped scalars + per-row sag + ≤3 creases) for inertia/settle, **plus a one-sided contact clamp**. It wins two of three judges and ties the third; it is the only design that meets the Huawei budget without a mid-animation quality downgrade and lands incrementally without rewriting the renderer or the pure-function test contract. We knowingly accept its one real ceiling — grip/gravity/crease are *parameterized* rather than *emergent* — because on this hardware emergence is not affordable in the headline 60 fps path, and the parameterization is principled (curvature from EI, sag from the gravito-bending length `L_g`, settle from a damped oscillator).

PBD is explicitly **rejected** for the shipping path on perf + integration risk. Its math is retained as a documented future option (Phase 6, optional) should the renderer ever move off the single-threaded path.

---

## 1. Material model

### 1.1 Parameter definitions (the contract is the parameter struct; the preset list is data — extensible)

Each material carries **raw physical parameters** (the stored contract). Derived solver constants (EI, areal mass, `L_g`, curl radius, ω, edge px) are computed **once per material/size change**, never per vertex, and the solver never sees a 0..1 abstraction again.

| Param (field) | Symbol | Unit | Meaning | Primarily drives |
|---|---|---|---|---|
| `thicknessMm` | t | mm | Single-sheet caliper | EI (∝t³), thin edge band (#6) |
| `grammageGsm` | W | g/m² | Basis weight (mass per area) | areal mass → gravity (#3), inertia (#8) |
| `youngsModulusGpa` | E | GPa | In-plane elastic modulus | EI, inextensibility (#1, #2) |
| `frictionStatic` | μ | – | Coulomb friction vs page below | contact slide/drag (#5) |
| `dampingRatio` | ζ | – | Settle damping ratio | oscillation/settle (#8) |
| `creaseTendency` | c01 | 0..1 | Plastic-fold readiness | creases/waves (#7) |
| `glossiness` | g01 | 0..1 | Specular fraction / finish | lighting & specular streak (#10) |
| `opacity` | αp | 0..1 | Show-through (optional, future) | back-lit ghost (#9/#10, low priority) |

The struct is **open for extension**: adding a field is a column; adding a material is a row. Nothing else changes.

### 1.2 Material preset table (all 7, realistic values)

| Preset | t (mm) | W (gsm) | E (GPa) | μs | ζ | crease c01 | gloss g01 | opacity |
|---|---|---|---|---|---|---|---|---|
| **Office** (default) | 0.104 | 80 | 4.5 | 0.50 | 0.22 | 0.40 | 0.10 | 0.93 |
| **Book** | 0.090 | 65 | 4.0 | 0.48 | 0.24 | 0.42 | 0.08 | 0.90 |
| **Newsprint** | 0.070 | 48 | 3.0 | 0.55 | 0.50 | 0.80 | 0.05 | 0.88 |
| **Coated** | 0.095 | 120 | 6.5 | 0.30 | 0.16 | 0.40 | 0.55 | 0.97 |
| **Glossy** | 0.115 | 160 | 7.5 | 0.25 | 0.15 | 0.35 | 0.85 | 0.98 |
| **Matte** | 0.135 | 130 | 5.5 | 0.55 | 0.28 | 0.45 | 0.12 | 0.96 |
| **Cardboard** | 0.55 | 300 | 3.5 | 0.45 | 0.35 | 0.75 | 0.12 | 0.99 |

These are realistic engineering ranges (TAPPI/ISO grammage & caliper, E 2–9 GPa for cellulose, EI ∝ E·t³/12, 60° gloss). They are **center values to tune on the physical Huawei tablet**, not lab-certified.

### 1.3 Derivation formulas (computed once per material + sheet size)

Let `widthPx` be the cropped sheet width in px, and assume a nominal physical page width `PAGE_WIDTH_MM = 150f` so `pxPerMm = widthPx / PAGE_WIDTH_MM`. SI throughout, then mapped to px.

```
t_m      = thicknessMm / 1000                          // m
arealMass m = grammageGsm / 1000                       // kg/m^2
I        = t_m^3 / 12                                  // m^3 per m width
EI       = youngsModulusGpa * 1e9 * I                  // N·m  (E·t^3/12)
L_g      = cbrt(EI / (m * 9.81))                       // m   elasto-gravity length
EI_OFFICE= cached EI of Office preset                  // reference

// Curl radius: emerges from EI, replaces the constant CURL_RADIUS_FRACTION.
R_ref    = R_REF_FRAC * widthPx                         // R_REF_FRAC = 0.18 (old default = reference)
R_curl   = (R_ref * (EI / EI_OFFICE).pow(0.4f))
              .coerceIn(0.10f * widthPx, 0.45f * widthPx)

// Gravity gain (dimensionless cantilever droop S): replaces sagFactor = weight/stiffness.
sagGain  = ((PAGE_WIDTH_MM / 1000f) / L_g).coerceIn(0f, SAG_CAP)   // SAG_CAP = 2.0

// Grip localization width: stiff spreads deformation, floppy localizes it.
normStiff = ((EI / EI_OFFICE).coerceIn(0.05f, 30f)).pow(0.2f) ... mapped to 0..1
gripSpread = lerp(0.22f, 0.55f, normStiff01)           // Gaussian std-dev (fraction of height)

// Thin edge band (#6): never a slab.
edgePx   = (thicknessMm * pxPerMm).coerceIn(0f, 3f)    // Office ~0.7px, Cardboard ~3.7→clamped 3px

// Settle oscillator (#8):
omega    = (K_OMEGA * sqrt(EI / (m * (PAGE_WIDTH_MM/1000f).pow(4)))).coerceIn(8f, 40f)
zeta     = dampingRatio                                // straight from preset
glossShininess = lerp(8f, 200f, glossiness)            // broad (matte) → tight (glossy)
```

`K_OMEGA`, `R_REF_FRAC`, `SAG_CAP` are tuning constants. Worked sanity check (Office): I = 104e-6³/12 ≈ 9.37e-14, EI = 4.5e9·9.37e-14 ≈ 4.2e-4 N·m, L_g = cbrt(4.2e-4/(0.080·9.81)) ≈ 0.092 m → moderate sag. Cardboard: EI ≈ 0.049 N·m, L_g ≈ 0.275 m → barely sags, large clamped R_curl → near-rigid hinge. Newsprint: EI ≈ 1e-4 N·m, L_g ≈ 0.058 m → deep droop, tight curl.

---

## 2. Per-frame deformation algorithm

The grid stays `(columns+1) × (rows+1)`, indexed `row*(columns+1)+col`. `mesh()` keeps its **exact signature and output contract**: `vertices2d`, `vertices3d`, `light`, `facing`, `maxLiftPx`, `progress`, `direction`. `theta` replaces `phi` 1:1, so `facing = cos(theta)` and the front/back triangle split in both renderers keep working unchanged. One **optional** new field `spec: FloatArray` carries per-vertex specular for the gloss highlight (JVM additive pass; Android folds into `light[]`).

### 2.1 Pipeline (per frame)

**Step 0 — Derive material** (cached by preset+size): EI, m, L_g, R_curl, sagGain, gripSpread, edgePx, omega, zeta, glossShininess (§1.3).

**Step 1 — Dynamic integration (inertia/settle, effect #8).** A small `BookCurlDynamics` object is held in `remember` across frames (the missing piece — today mesh is stateless). Accumulate real `frameDt`; run `N = ceil(frameDt / FIXED_STEP_SECONDS)` symplectic-Euler substeps (`FIXED_STEP_SECONDS = 1/120` already exists). For each driven scalar `q ∈ {prog, lift, fold}` and each per-row `sag[v]`:

```
a = omega^2 * (target - q) - 2f * zeta * omega * v
v += a * dt          // update v BEFORE q  → symplectic, stable for stiff Cardboard
q += v * dt
```

`target_prog = state.progress` (the gesture/auto-flip output the code already computes); **the mesh reads the integrated `prog`, never the raw target** — that is what produces lag/overshoot/settle. On finger release seed `prog.v = -K_V * state.velocityX * direction` (fling overshoot). Per-row `sag[v]` uses `omega * (0.6 + 0.4*gripFalloff(v))` so rows far from the grip are laggier → bottom overshoots/settles slower than top. Stop substepping and snap when every `|target−q| < eps_q` and `|v| < eps_v`.

**Step 2 — Crease nucleation (effect #7).** While `phase == Dragging`, track `peakCurvature * |prog.v|`; if `creaseTendency * peakCurvature * |prog.v| > C_YIELD`, push/refresh a `Crease(sCenter, vCenter, amp)` (cap 3). Each frame relax `amp` toward `A_residual = retain(c01) * A_peak` (≈0 for glossy/coated full recovery, ~0.3–0.4 for newsprint/cardboard plastic memory).

**Step 3 — Build curvature field + arc-length march (effects #1,#2,#3,#4,#7).** For each row `v = row/rows`:

```
gripFalloff(v) = exp(-((v - gripFrac)/gripSpread)^2)      // Gaussian about grip row
kappaMax(v)    = gripFalloff(v) / R_curl                  // curvature peaks at grip, radius from EI
```

March columns from the spine `s=0` (pinned: `h=0, z=0, theta=0`) outward in equal `ds = widthPx/columns` (arc-length steps ⇒ inextensible by construction):

```
sFrac = s / widthPx
k     = kappaMax(v) * smoothstep((sFrac - onset(v)) / band)    // ≈0 at spine, ramps near free edge
k    += creaseDeltaK(s, v, dyn)                                // Gaussian spike, not a position kink
thetaGrav = -sagGain * sag[v] * (v - gripFrac) * sFrac*sFrac   // cantilever droop, signed top≠bottom
thetaMid  = theta + 0.5f * k * ds + thetaGrav                  // 2nd-order midpoint integrator
h    += ds * cos(thetaMid)
z    += ds * sin(thetaMid)
theta += k * ds
```

where `onset(v) = ONSET_BASE - 0.15f * gripFalloff(v)` (bend band starts later far from grip) and `band` is the smoothstep ramp width. `(v - gripFrac)` is the signed vertical weight that makes rows **below** the grip droop more than rows above — the top/bottom asymmetry.

**Step 4 — Contact clamp (effect #5).** In **model z, before perspective**:

```
zBook(h) = SPINE_DOME * exp(-(h/SPINE_SIGMA)^2)   // ≈0, optional soft dome near spine
z = max(z, zBook(h))
```

Track per-row first lift-off `s_contact`; vertices inboard stay flat on the book. To avoid the "stolen arc length / flattened dead zone" artifact, redistribute the un-spent arc length of a clamped vertex into a small lift at the first inboard free vertex so a real resting **contact line** forms (not a dead patch). Damp in-plane velocity of clamped vertices by `μ` (glossy slides, newsprint/matte drags).

**Step 5 — Thin back-face offset (effect #6).** For back-facing vertices (`cos(theta) < 0`) nudge `z` by `edgePx * cos(theta)` — a thin offset along the normal, **not** an extruded slab.

**Step 6 — Project + light (effects #9,#10).**

```
camera = (widthPx * CURL_CAMERA_DISTANCE).coerceAtLeast(1f)     // 2.6 kept
persp  = camera / (camera - z.coerceAtMost(camera * 0.82f))     // guard divide (finite-coords test)
xPage  = if (direction > 0) h else widthPx - h
vertices2d = center + (page - center) * persp
nL  = cos(theta) * L.z - sin(theta) * L.x                       // diffuse from true normal
light[idx] = (CURL_AMBIENT + CURL_DIFFUSE * max(0f, nL)).coerceIn(0f,1f)   // nL<0 ⇒ inner self-shadow
spec[idx]  = if (glossiness > 0.05f) glossiness * pow(max(0f, nH(theta)), glossShininess) else 0f
facing[idx] = cos(theta)                                        // front/back split unchanged
```

`texCoords` stay proportional to arc length `s` (already true in `bookCurlMeshBuffers`) → text foreshortening (`Δx_screen ≈ ds·cos(theta)·persp`) falls out automatically; no per-glyph code.

### 2.2 Exact formula per effect, and the driving parameter

| # | Effect | Formula (model) | Driven by |
|---|---|---|---|
| 1 | Bending elasticity | `R_curl = clamp(0.18·w·(EI/EI_office)^0.4, .10w, .45w)`, `kappaMax = gripFalloff/R_curl`; `k = kappaMax·smoothstep((sFrac−onset)/band)` (k→0 at spine, non-constant) | EI = E·t³/12 → **E, t** |
| 2 | Inextensibility | equal `ds` arc march, `h+=ds·cos`, `z+=ds·sin`; texCoords ∝ s; optional 1-pass Gauss-Seidel on vertical edges | **E** (developable assumption); structural |
| 3 | Gravity / sag | `thetaGrav = −sagGain·sag[v]·(v−gripFrac)·sFrac²`, `sagGain = (pageW/L_g)`, `L_g = cbrt(EI/(m·g))`; signed (v−gripFrac) ⇒ bottom>top | **W** (m), **t/E** (EI) |
| 4 | Grip / direction | `kappaMax(v) = exp(−((v−gripFrac)/gripSpread)²)/R_curl`, peak at `gripFrac`; `gripSpread = lerp(.22,.55, normStiff)`; `xPage` mirrored by `direction` | grip = `state.gripY`; spread = **E,t** |
| 5 | Contact | `z = max(z, zBook(h))` in model-z + arc-length redistribution → contact line; in-plane damping by μ | **μ** |
| 6 | Thickness edge | `edgePx = clamp(t·pxPerMm, 0..3)`; back-face z offset by `edgePx·cos θ`; thin silhouette-only stroke | **t** |
| 7 | Creases / waves | `creaseDeltaK = A·exp(−((s−sc)²)/(2wc²))·exp(−((v−vc)²)/(2wv²))` added to k; nucleate if `c01·κ·|prog.v| > C_YIELD`; `A→A_residual = retain(c01)·A_peak`; free-edge wave `a·windStrength·sin(2πf·v)` | **c01**, gesture velocity |
| 8 | Inertia & damping | `a = ω²(target−q) − 2ζω·v; v+=a·dt; q+=v·dt` (symplectic, fixed substep); release seeds `v` from fling; per-row ω lowered far from grip | **ζ** (preset), ω = f(EI, m) |
| 9 | Text deformation | free: texCoords ∝ s, `Δx_screen ≈ ds·cos θ·persp`; stronger bend (smaller R_curl) ⇒ more compression; mirrors on inner face | (falls out of #1/#2) |
| 10 | Light / shadow | `light = ambient + diffuse·max(0,n·L)` (n·L<0 inner self-shadow); `spec = g01·max(0,n·H)^lerp(8,200,g01)`; cast shadow = curled silhouette projected onto z=0 along L | **g01**; geometry |

---

## 3. Settings change (remove sliders → add preset picker)

**Remove** the two `Float` fields `bookCurlWeight` / `bookCurlStiffness` and their two `LabeledSlider`s. **Add** a single serializable enum field `bookCurlMaterial`.

### 3.1 API: `reflow/api/.../ReaderSettings.kt`

- Add a serializable enum (extensible list; persisted by name so reordering is safe but renaming is not):

```kotlin
@Serializable
public enum class BookCurlMaterialId { OFFICE, BOOK, NEWSPRINT, COATED, GLOSSY, MATTE, CARDBOARD }
```

- In `ReaderSettings`: **remove** `bookCurlWeight`, `bookCurlStiffness`; **add** `public val bookCurlMaterial: BookCurlMaterialId = BookCurlMaterialId.OFFICE`.
- In `coerced()`: **remove** the two `.coerceIn(0f,1f)` lines for the dropped fields (enum needs no coercion).
- Update KDoc on `ReaderSettings` (drop the two `@property` lines, add `@property bookCurlMaterial`).

### 3.2 Persistence & migration

`StoredReaderSettings.current` is `@Serializable ReaderSettings` decoded with `ignoreUnknownKeys = true` (per the brief). Therefore:
- Old blobs containing `bookCurlWeight`/`bookCurlStiffness` → those keys are **ignored** on load; `bookCurlMaterial` is absent → falls back to its default `OFFICE`. No crash, clean removal (project convention: clean removal over defensive retention — do **not** keep dead float fields or a legacy→preset mapper).
- New blobs serialize `bookCurlMaterial` by enum name; older app versions ignore the unknown key. Fully tolerant both directions.

Add a serialization round-trip test asserting an old JSON with the two floats decodes to `OFFICE` without error (Phase 4).

### 3.3 Impl-Compose: `ReflowReaderSettings.kt`

- Remove `bookCurlWeight`, `bookCurlStiffness`; add `public val bookCurlMaterial: BookCurlMaterialId = BookCurlMaterialId.OFFICE`.
- In `toRenderSettings()`: replace the two `bookCurlWeight/Stiffness =` copies with `bookCurlMaterial = s.bookCurlMaterial`.

### 3.4 UI: `ReaderAirbar.kt` (~lines 804–821, under `Group("Поведение")`)

Replace the two `LabeledSlider`s (shown when `pageTransition == BOOK`) with a single `LabeledChoice` using the existing generic helper (`LabeledChoice<T>(label, options, selected, labelOf, onSelect)`):

```kotlin
if (settings.pageTransition == PageTransition.BOOK) {
    LabeledChoice(
        label = "Материал листа",
        options = BookCurlMaterialId.entries,
        selected = settings.bookCurlMaterial,
        labelOf = ::bookCurlMaterialName,
        textColor = textColor,
        onSelect = { onChange(settings.copy(bookCurlMaterial = it)) },
    )
}
```

Add a private `bookCurlMaterialName(id)` mapping to Russian display strings (Office→«Офисная», Book→«Книжная», Newsprint→«Газетная», Coated→«Мелованная», Glossy→«Глянцевая», Matte→«Матовая», Cardboard→«Картон»), mirroring `transitionName`. Remove now-unused slider plumbing if no other consumer.

---

## 4. File-by-file change map

### `reflow/api/src/.../ReaderSettings.kt` — REPLACE
- ADD enum `BookCurlMaterialId` (serializable).
- REMOVE `bookCurlWeight`, `bookCurlStiffness` from `ReaderSettings` + their `coerced()` lines + KDoc.
- ADD `bookCurlMaterial: BookCurlMaterialId = OFFICE` + KDoc.

### `reflow/impl/.../ui/bookcurl/BookCurlPhysics.kt` — REPLACE (core)
- REPLACE `data class BookCurlMaterial(weight, stiffness)` with the raw-param material + derived constants:

```kotlin
internal data class BookCurlMaterial(
    val id: BookCurlMaterialId = BookCurlMaterialId.OFFICE,
    val thicknessMm: Float, val grammageGsm: Float, val youngsModulusGpa: Float,
    val frictionStatic: Float, val dampingRatio: Float,
    val creaseTendency: Float, val glossiness: Float, val opacity: Float = 0.93f,
) {
    companion object {
        val TABLE: Map<BookCurlMaterialId, BookCurlMaterial>   // the 7 presets from §1.2
        val Default = TABLE.getValue(BookCurlMaterialId.OFFICE)
        fun of(id: BookCurlMaterialId): BookCurlMaterial = TABLE.getValue(id)
    }
}

// Derived once per material+size (NOT in the data class — keep px scale out of the table):
internal class BookCurlDerived(material: BookCurlMaterial, widthPx: Float, eiOffice: Float) {
    val arealMass: Float; val ei: Float; val lg: Float
    val rCurl: Float; val sagGain: Float; val gripSpread: Float
    val edgePx: Float; val omega: Float; val zeta: Float; val glossShininess: Float
}
```

- ADD `internal class BookCurlDynamics(rows: Int)` holding `(value,velocity)` for `prog/lift/fold`, `FloatArray sag + sagVel`, and `creaseS/creaseV/creaseA` (cap 3). Plus `fun advance(target, derived, frameDt, releaseVx)` doing the symplectic substep loop (§2.1 Step 1).
- ADD `spec: FloatArray` to `BookCurlMesh` (default empty for back-compat with tests that don't read it).
- REPLACE `mesh(...)` body: keep the **signature** but accept `(material: BookCurlMaterial, derived: BookCurlDerived, dynamics: BookCurlDynamics)` (overload or extra params with defaults so the test can still call it statelessly with a default dynamics). Implement §2.1 Steps 2–6. Split into private helpers `buildRowCurvature`, `marchRow`, `shadeVertex` to stay under detekt `LongMethod` (baseline is signature-keyed — new helper signatures are clean).
- REPLACE `curlPoint`/`cylinderPoint` with the arc-length integrator (`marchRow`). REMOVE the `CurlPoint`/`cylinderPoint` two-piece model.
- REPLACE `curlLight(phi)` with diffuse+specular split (§2.1 Step 6).
- KEEP `settleProgress`, `windStrength`, `shouldComplete`, `autoProfile`, `bookCurlMeshBuffers`, `shadeColors`, `BookCurlState`, `BookCurlProfile`, `BookCurlPhase` unchanged.
- REPLACE constants: `CURL_RADIUS_FRACTION`→`R_REF_FRAC`; remove `CURL_LIFT_ANGLE/FRACTION` two-phase assumptions if subsumed; remove `CURL_SAG_STRENGTH/MAX_SAG/SAG_SHADOW`, `CURL_BACK_DIM`; add `PAGE_WIDTH_MM, K_OMEGA, SAG_CAP, ONSET_BASE, BAND, C_YIELD, K_V, SPINE_DOME, SPINE_SIGMA, eps_q, eps_v`. Keep `CURL_CAMERA_DISTANCE, CURL_AMBIENT, CURL_DIFFUSE`.

### `reflow/impl/.../ui/bookcurl/BookCurlOverlay.kt` — REPLACE (state threading)
- Hold `BookCurlDynamics` and `BookCurlDerived` in `remember(sheet.width, sheet.height, material)`; reset on size/material change.
- Drive `dynamics.advance(...)` from the existing per-frame path. Since the overlay currently has **no** `withFrameNanos` loop (it recomputes reactively from `progress`), add a `LaunchedEffect`/`withFrameNanos` ticker **only while `progress in (0,1)`** that advances dynamics and triggers recomposition; feed the integrated `dynamics.prog` into the mesh build. Keep the `remember(state, profile, material)` memo but key it additionally on a frame counter so the lagged value updates. (This is the one real structural change Hybrid requires; it is local to the overlay.)
- Pass `derived`/`dynamics` into `BookCurlPhysics.mesh(...)`.

### `reflow/impl/.../ui/bookcurl/BookCurlRenderer.jvm.kt` — REPLACE (render additions)
- REMOVE `drawPaperBase` full-outline fill (the grey slab). Replace with: nothing (front texture covers) plus a **thin stroked edge band** along the silhouette of width `edgePx` (darker, desaturated paper), drawn only where `|n·view|` is small.
- REPLACE `drawCastShadow`: project the curled silhouette vertices onto `z=0` along light dir L (`shadowPt = (x - z·L.x/L.z, y - z·L.y/L.z)`), fill that crescent polygon, blur ∝ lift, alpha ∝ lift — instead of translating the flat `outlinePath`.
- ADD an **additive specular pass**: a second `drawVertices` with an additive paint and per-vertex white scaled by `mesh.spec[]`, **skipped entirely when the material's glossiness < 0.05** (Office/Book/Newsprint/Matte ⇒ zero extra pass).
- KEEP `drawTexturedMesh`, `facingIndices`, `drawRimHighlight` (rim alpha now ∝ glossiness).

### `reflow/impl/.../ui/bookcurl/BookCurlRenderer.android.kt` — REPLACE (mirror)
- Same edits mirrored: remove `drawPaperBase` slab; thin edge stroke along silhouette; projected-silhouette cast shadow; specular folded into `shadeColors()` (Android `drawBitmapMesh` has one shade set, no additive pass) so the glossy streak reads slightly weaker on Android (acceptable per perf judge). Keep the back-face `clipPath(backFacingPath())` approach; the new edge/specular must live **inside** that clip where they apply to the back face.

### `reflow/impl/.../ui/ReflowReaderSettings.kt` — REPLACE
- Remove `bookCurlWeight/Stiffness`; add `bookCurlMaterial: BookCurlMaterialId`. Update `toRenderSettings()` copy and KDoc.

### `reflow/impl/.../ui/ReaderAirbar.kt` — REPLACE (UI)
- Replace the two `LabeledSlider`s with one `LabeledChoice` (§3.4). Add `bookCurlMaterialName`.

### `reflow/impl/.../ui/ReflowReader.kt` — REPLACE (instantiation, ~1568–1572)
- Replace `BookCurlMaterial(weight=..., stiffness=...)` with `BookCurlMaterial.of(settings.bookCurlMaterial)`.

### `reflow/impl/src/commonTest/.../BookCurlPhysicsTest.kt` — REPLACE/EXTEND
- The two material-bearing tests construct `BookCurlMaterial(weight=…, stiffness=…)`; rewrite to use presets/derived (§5). Keep `liftAt`, the geometry tests, and `meshHasFiniteCoordinatesForViolentDrag` (the latter is the safety net for the new integrator).

### `reflow/impl/.../ui/bookcurl/BookCurlPaint.kt` — KEEP (optionally add an `edge` color field for the thin band).

---

## 5. Deterministic unit-test plan

All pure math, no rendering (headless `GraphicsLayer.toImageBitmap()` is blank per project memory — visuals verified on the Huawei device by the user). Mirror the existing `BookCurlPhysicsTest` style; use the existing `liftAt(row,col)` helper.

**Keep (regression):**
- `meshHasFiniteCoordinatesForViolentDrag` — extended to drive `dynamics.advance` a few steps first; guards the symplectic integrator + persp divide.
- `staticBuffersMatchMeshTopology`, `twoPageSpread*`, `under/committed*`, `windDecaysAfterRelease` — unchanged.
- `spinePivotStaysOnSurface`: `liftAt(row=rows/2, col=0) == 0f` (pinned arc start).

**Replace/generalize (material-driven):**
- `weightlessSheetLiftsEveryRowEqually`: build with a synthetic weightless material (huge EI / tiny grammage so `sagGain ≈ 0`) → `abs(edgeTop − edgeBottom) < freeEdgeLift*0.05 + 0.5` (generalizes the old `weight=0f` test).
- `heavyFloppySheetSagsAwayFromGrip`: Newsprint preset, grip at bottom → `liftAt(rows-1, cols) > liftAt(1, cols) * 1.25` (signed `(v−gripFrac)` asymmetry).

**New invariants:**
- `inextensibilityAlongBend`: for every along-bend edge, `abs((|p3d−q3d| − ds)/ds) < 0.005`.
- `gripConcentratesCurvature`: peak per-row lift occurs at the row nearest `gripFrac` and decays monotonically with `|v−gripFrac|`.
- `bottomDroopsMoreThanTop`: top grip + heavy preset ⇒ bottom-edge droop > top-edge droop.
- `contactNoPenetration`: after clamp, no vertex `z < zBook(h)`.
- `thinEdgeBand`: `derived.edgePx ≤ 3` and monotone in thickness across presets.
- `oscillatorSettleMonotonicForOverdamped`: `advance()` with `ζ ≥ 1` converges to target with no overshoot; with `ζ < 1` overshoots at least once then settles, and stops at `eps`.
- `creaseOnlyAboveYield`: no crease nucleates below `C_YIELD`; one nucleates above it for high-`c01`.
- `radiusFromStiffness`: `R_curl(Cardboard) > R_curl(Office) > R_curl(Newsprint)`, all within `[0.10w, 0.45w]`.
- `serializationDropsLegacyFloats` (api test): JSON with `bookCurlWeight/Stiffness` decodes (ignoreUnknownKeys) to `bookCurlMaterial == OFFICE`.

Green gate after each phase: `./gradlew :reflow:jvmTest`, then `:reflow:test`, `detekt`, `ktlintCheck`.

---

## 6. Per-frame performance budget (Huawei Kirin/EMUI, ~16.67 ms @ 60 Hz)

| Stage | Work | Cost |
|---|---|---|
| Derive material | once per material/size (cached), ~12 float ops | ~0 |
| Dynamics integrate | 3 scalars + (rows+1)≈53 per-row sag + ≤3 creases × N≤4 substeps | a few hundred float ops, ≪ vertex loop |
| Mesh vertex loop | 1 `sin`+1 `cos` + ~6 mul-adds per vertex; `gripFalloff` exp hoisted per-row | High 37×53=1961 verts → ~1–2 ms (same class as today) |
| Optional Gauss-Seidel | 1 pass over vertical edges (~2k ops), skip on Low | ~0.3 ms |
| Contact clamp | O(verts) `max()` in the same loop | free |
| JVM render | `drawVertices` MODULATE (1 call) + **gated** additive specular pass (skipped for non-glossy) + thin edge stroke + projected shadow | ~2–3 ms |
| Android render | `drawBitmapMesh` + `clipPath(backFacingPath)` + edge stroke + shadow (specular folded into shade) | ~3–5 ms |
| **Total** | | **~4–9 ms, 50–60 fps sustained** |

A page turn is a **transient ~420 ms event** (`PAGE_CURL_SETTLE_NANOS`), not a sustained scroll. Zero per-frame allocation: dynamics + mesh `FloatArray`s are reused, keyed on size in `remember`. Material switch changes constants only — mesh resolution never changes mid-drag (buffers re-memoized only on size change). **Throttle levers if a frame budget is breached:** drop substeps (4→2→1), skip the relaxation pass, drop to `BookCurlProfile.Low`. Specular `pow` is the only added transcendental and it is gated off for the 4 non-glossy presets.

---

## 7. Phased plan (incremental, each phase green-gated)

**Phase 1 — Material model + derivations (pure, no behavior change yet).**
- Add `BookCurlMaterialId` (api), `BookCurlMaterial` raw-param struct + `TABLE` + `BookCurlDerived` (impl). Keep the old scalar shape but feed it `derived.sagGain` and `derived.rCurl` as drop-in replacements for `sagFactor`/`CURL_RADIUS_FRACTION`.
- Gate: derivation unit tests (`radiusFromStiffness`, `thinEdgeBand`); existing mesh tests still green via a back-compat `mesh()` overload.

**Phase 2 — Non-uniform curvature field + arc march + gravity asymmetry (effects 1,2,3,4,9).**
- Replace `curlPoint`/`cylinderPoint` with `buildRowCurvature` + `marchRow`. Add `inextensibilityAlongBend`, `gripConcentratesCurvature`, `bottomDroopsMoreThanTop`, generalize the two material tests.
- Gate: `:reflow:jvmTest` green; visual check handed to user on tablet.

**Phase 3 — Contact clamp + thin edge + lighting/specular + projected shadow (effects 5,6,10).**
- Add contact clamp + arc-length redistribution; remove `drawPaperBase` slab in both renderers; projected-silhouette shadow; diffuse+specular split with gated additive pass.
- Gate: `contactNoPenetration`; both renderer compile (`compileKotlinDesktop`, `compileAndroidMain`); user device check for slab/shadow/specular.

**Phase 4 — Settings swap (remove sliders, add picker) + persistence migration.**
- Edit api/impl settings, airbar, ReflowReader instantiation. Add `serializationDropsLegacyFloats`.
- Gate: full `:reflow:test`, `ktlintCheck`, `detekt`.

**Phase 5 — Dynamic layer: inertia/settle + creases (effects 7,8).**
- Add `BookCurlDynamics.advance`, the overlay frame ticker, fling seeding, crease nucleation/relaxation.
- Add `oscillatorSettleMonotonicForOverdamped`, `creaseOnlyAboveYield`; keep `meshHasFiniteCoordinatesForViolentDrag` extended through the integrator.
- Gate: `:reflow:test`, `detekt`, `ktlintCheck`; final on-device tuning pass with the user (preset feel: Newsprint floppy, Cardboard near-rigid, Glossy specular streak).

**Phase 6 (optional, future) — PBD upgrade.** Documented but **not** scheduled: only if the renderer moves off the single-threaded path; would replace the analytic base with a persistent XPBD solver per the rejected PBD design, behind the same `mesh()` output contract.

---

### Key risks carried forward
- Per-row independence is quasi-developable; keep all per-row params smooth (Gaussian/smoothstep) and enable the optional vertical-edge relaxation if shading seams appear on device.
- Contact clamp must redistribute arc length, not just `max(z,0)`, or the sheet looks stretched/short — highest-quality-risk item, clamp in model-z before perspective.
- Symplectic Euler (v before q) at fixed substep with `ω ≤ 40` is mandatory; explicit Euler at variable dt explodes Cardboard.
- Thin edge must be a silhouette-only stroke (≤3 px), never a fill — the rejected slab.
- Preset center values are engineering estimates; final feel is tuned on the physical Huawei tablet (assistant cannot drive the app; headless capture is blank).

**Relevant files (absolute):**
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\commonMain\kotlin\ru\kyamshanov\notepen\reflow\ui\bookcurl\BookCurlPhysics.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\commonMain\kotlin\ru\kyamshanov\notepen\reflow\ui\bookcurl\BookCurlOverlay.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\jvmMain\kotlin\ru\kyamshanov\notepen\reflow\ui\bookcurl\BookCurlRenderer.jvm.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\androidMain\kotlin\ru\kyamshanov\notepen\reflow\ui\bookcurl\BookCurlRenderer.android.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\commonMain\kotlin\ru\kyamshanov\notepen\reflow\ui\bookcurl\BookCurlPaint.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\api\src\commonMain\kotlin\ru\kyamshanov\notepen\reflow\api\ReaderSettings.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\commonMain\kotlin\ru\kyamshanov\notepen\reflow\ui\ReflowReaderSettings.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\commonMain\kotlin\ru\kyamshanov\notepen\reflow\ui\ReaderAirbar.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\commonMain\kotlin\ru\kyamshanov\notepen\reflow\ui\ReflowReader.kt`
- `C:\Users\kruz18\IdeaProjects\NotePen2\reflow\impl\src\commonTest\kotlin\ru\kyamshanov\notepen\reflow\ui\BookCurlPhysicsTest.kt`
