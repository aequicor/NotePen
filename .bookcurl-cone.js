export const meta = {
  name: 'bookcurl-cone',
  description: 'Design + numerically verify a developable CONE page-curl: grip-driven, spine-anchored, lies flat at full turn, visible top-down, inextensible',
  phases: [
    { title: 'Design' },
    { title: 'Judge' },
    { title: 'Verify' },
  ],
}

const CONTEXT = [
  'GOAL: a realistic book page-turn for a top-down view. The current model is ONE non-tilted developable CYLINDER and it has two user-rejected flaws; we are replacing it with a developable CONE.',
  '',
  'FILE: reflow/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/reflow/ui/bookcurl/BookCurlPhysics.kt, class MeshBuild.',
  'Sheet coords: x in [0,W] (x=0 = spine/binding, x=W = free edge), y in [0,H]. The mesh march writes per vertex: worldX (page px, 0..W when flat), worldY, z (lift), theta (surface angle). writeVertex then does xPage = (direction>0)? worldX : W-worldX, perspective persp = camera/(camera - z) with camera = CURL_CAMERA_DISTANCE*W (~4.5W), and screen = center + (xPage-center, worldY-center)*persp. facing = cos(theta) drives the renderer front/back texture split. mesh() is pure/stateless; inputs available in BookCurlState: progress (0..1), gripY, fingerX, fingerY, direction, velocityX. radius = derived.rCurl (~0.085*W) is the material curl radius.',
  '',
  'WHY each simpler model FAILED (do not repeat):',
  '- Progressive arc / per-ROW independent folds => the sheet STRETCHES (curvature or across-row distance grows) = "rubber". The map MUST be an isometry (developable): along-row AND across-row neighbor distances preserved.',
  '- Non-tilted cylinder => anchored + visible + isometric, BUT (a) no dependence on grip position, and (b) at progress 1 the flipped panel floats at z~2*radius and never LIES FLAT on the far side, so you cannot manually flip the page fully down.',
  '- Tilted-axis cylinder (for a grip dog-ear) => the tilted fold reaches the spine and LIFTS it off the book = the page can be "torn out".',
  '- Spine-hinge FLAP (rigid rotate whole sheet about spine to alpha=pi) => lies flat, anchored, BUT the camera looks straight DOWN, so at alpha=90deg the page is perpendicular to the screen and goes EDGE-ON / vanishes. RENDER-CONFIRMED bad.',
  '',
  'THE CONE (what we want): the page wraps around a developable CONE whose APEX sits on the spine line x=0 (grip-driven: apex Y ~ gripY). Near the apex the effective radius -> 0 (tight crease at the binding, lies flat), far from the apex it opens (curl at the free edge). As progress grows the page wraps further and, at progress 1, lies FLAT on the FAR side over the next page (z~0), hinged at the binding. A cone is developable => isometric. It stays mostly in the book plane (not rotated to vertical) => visible top-down, no edge-on collapse.',
].join('\n')

const CONSTRAINTS = [
  'The cone model MUST satisfy ALL, and the spec must give CONCRETE closed-form per-vertex formulas (a senior dev implements directly in marchRow), plus how apex/half-angle/wrap are derived from gripY/fingerX/fingerY/progress:',
  'C1 ANCHORED SPINE: the spine column x=0 stays on the book z=0 for ALL progress in [0,1] and all grip — it can never lift off as a whole (no tear).',
  'C2 LIES FLAT AT FULL TURN: at progress 1 the bulk of the turned page is at z~0 on the FAR side (x mirrored across the spine), a real lying-flat page, NOT floating at ~2*radius. Continuous as progress->1.',
  'C3 VISIBLE TOP-DOWN (no edge-on): at every progress the projected page keeps a non-degenerate screen area; it must NOT collapse to a thin line (the flap failure). Quantify: the bounding-box screen width at progress 0.5 must be a meaningful fraction of W, not ~0.',
  'C4 ISOMETRIC: developable; along-row and across-row neighbor 3D distances preserved within ~3% (no rubber). The local curl curvature is bounded by the material (about 1/radius near the free edge), never growing unboundedly with pull.',
  'C5 GRIP-DRIVEN: the apex / cone orientation depends on gripY (and finger), so grabbing near a corner peels a diagonal dog-ear and grabbing mid-edge is near-symmetric. Shape answers where is the grip.',
  'C6 MESH CONTRACT: keep writeVertex(worldX, worldY, z, theta) and facing=cos(theta); renderer unchanged. Provide the marchRow producing (worldX, worldY, z, theta) per vertex (x,y). Note the existing direction mirror is applied later in writeVertex; build in canonical direction>0 (spine at x=0) coords.',
].join('\n')

phase('Design')
const DESIGN_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  properties: {
    modelName: { type: 'string' },
    coreIdea: { type: 'string', description: 'The cone construction in 3-6 sentences: apex, axis, half-angle, how the flat page maps onto the cone.' },
    drivers: { type: 'string', description: 'Exact formulas mapping (gripY, fingerX, fingerY, progress, radius, W, H) -> apex position, cone half-angle, wrap amount.' },
    marchPseudocode: { type: 'string', description: 'Closed-form Kotlin-ish per-vertex code: given (x,y) -> (worldX, worldY, z, theta). No per-row independent progress. Must be implementable as-is.' },
    spineAnchor: { type: 'string', description: 'Proof/argument that worldX=0,z=0 at x=0 for all progress and grip.' },
    liesFlat: { type: 'string', description: 'Why the bulk lands at z~0 on the far side at progress 1 (with the algebra).' },
    notEdgeOn: { type: 'string', description: 'Why the page stays visible top-down at mid-turn (does not collapse to a line); estimate the screen width at progress 0.5.' },
    isometry: { type: 'string', description: 'Why the map is an isometry (developable), both directions.' },
    gripDriven: { type: 'string', description: 'How gripY/finger change the shape (dog-ear vs symmetric).' },
    risks: { type: 'string' },
  },
  required: ['modelName', 'coreIdea', 'drivers', 'marchPseudocode', 'spineAnchor', 'liesFlat', 'notEdgeOn', 'isometry', 'gripDriven'],
}
const designs = (await parallel([
  () => agent(
    [
      CONTEXT, '', CONSTRAINTS, '',
      'YOUR ANGLE: "ROLL A FLAT SECTOR INTO A CONE". Treat the page region near the free corner as a flat sector with vertex at the apex O=(0, apexY) on the spine; rolling that flat sector into a cone of half-angle gamma(progress) wraps it. A flat sector angle psi maps to cone azimuth psi/sin(gamma); generators (distance r from O) are preserved. Derive worldX,worldY,z and theta from (x,y) via O, r, psi, gamma. Show the apex on the spine keeps x=0 fixed and that full wrap lays the sector on the far side at z~0. You MAY read the file.',
    ].join('\n'),
    { label: 'design:cone-sector', phase: 'Design', schema: DESIGN_SCHEMA },
  ),
  () => agent(
    [
      CONTEXT, '', CONSTRAINTS, '',
      'YOUR ANGLE: "WRAP AROUND A CONE OF VARYING RADIUS". Keep a moving fold line (like the cylinder) but make the local fold RADIUS vary linearly with distance from the apex on the spine: rho(y-ish) = k * (distance from apex), so radius->0 at the binding (lies flat) and grows toward the far corner. Derive the per-vertex wrap so it is a TRUE cone (isometric), not a per-row hack. Tie the apex to gripY and the fold sweep to progress; at progress 1 the crease is at the binding and the page lies flat on the far side. You MAY read the file. Give closed-form worldX,worldY,z,theta.',
    ].join('\n'),
    { label: 'design:cone-radius', phase: 'Design', schema: DESIGN_SCHEMA },
  ),
])).filter(Boolean)

phase('Judge')
const JUDGE_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  properties: {
    winningModel: { type: 'string' },
    rationale: { type: 'string' },
    finalSpec: { type: 'string', description: 'ONE concrete implementable cone: the full marchRow per-vertex closed-form (x,y)->(worldX,worldY,z,theta), the apex/half-angle/wrap drivers from grip+progress+radius, anchored-spine + lies-flat + not-edge-on + isometry arguments, degenerate cases (progress 0/1, grip at edges, both directions), and which existing tests stay green.' },
    constants: { type: 'string', description: 'New constants/fields to add and old ones (foldCenterS/wrap/droop/etc) to remove.' },
    testPlan: { type: 'string', description: 'Unit tests: spine z=0 all progress, liesFlatAtFullTurn (bulk z<small at progress 1), notEdgeOn (screen-x span at progress 0.5 > some fraction of W), isometry along+across, gripChangesShape.' },
    risks: { type: 'string' },
  },
  required: ['winningModel', 'rationale', 'finalSpec', 'constants', 'testPlan'],
}
const winner = await agent(
  [
    'Lead graphics engineer: pick + synthesize the best CONE design. Priority: anchored spine > lies flat at full turn > NOT edge-on (visible top-down) > isometric > grip-driven > mesh contract.',
    CONTEXT, '', CONSTRAINTS, '',
    'CANDIDATE DESIGNS:\n' + JSON.stringify(designs),
    '',
    'Produce ONE decisive, directly-implementable finalSpec with closed-form per-vertex formulas. Be explicit about the algebra for the spine pin, the lies-flat completion, and the top-down visibility (no edge-on).',
  ].join('\n'),
  { label: 'judge:synthesize', phase: 'Judge', schema: JUDGE_SCHEMA },
)

phase('Verify')
const VERIFY_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  properties: {
    checks: { type: 'array', items: { type: 'object', additionalProperties: false, properties: { invariant: { type: 'string' }, verdict: { type: 'string', enum: ['pass', 'fail', 'uncertain'] }, numbers: { type: 'string', description: 'Concrete computed values supporting the verdict.' } }, required: ['invariant', 'verdict', 'numbers'] } },
    mathErrors: { type: 'string' },
    fixes: { type: 'string' },
    finalVerdict: { type: 'string', enum: ['ship', 'ship-with-fixes', 'redesign'] },
    correctedSpec: { type: 'string', description: 'If fixes/redesign needed, the corrected full per-vertex formulas; else repeat the spec verbatim.' },
  },
  required: ['checks', 'mathErrors', 'fixes', 'finalVerdict', 'correctedSpec'],
}
const verdict = await agent(
  [
    'Adversarially VERIFY the CONE spec by WORKING THE NUMBERS at W=800,H=1200, radius=68, gripY=600 (and gripY=120 for grip-driven), progress in {0,0.5,1}. Compute and REPORT actual numbers to REFUTE or confirm:',
    'C1 spine: worldX and z at x=0 for several y at progress {0,0.5,1} — must be 0,0.',
    'C2 lies flat: at progress 1, the max z over the bulk of vertices — must be small (<< 2*radius=136, ideally < radius), and worldX mirrored to the far side (negative / x<0 region).',
    'C3 not edge-on: at progress 0.5 compute the screen-x span (max-min of projected xPage) — must be a real fraction of W (e.g. > 0.3*W), NOT near 0 (the flap collapsed to ~0).',
    'C4 isometry: pick adjacent vertices along a row and across rows at progress 0.5 and compute 3D distance vs the flat spacing — must match within ~3%.',
    'C5 grip-driven: show the shape differs for gripY=600 vs gripY=120.',
    'Also check finiteness and continuity (no jump at the flat/cone seam). Do NOT rubber-stamp; if any check fails, finalVerdict=redesign or ship-with-fixes and give correctedSpec with the actual fix.',
    '',
    'SPEC:\n' + JSON.stringify({ finalSpec: winner.finalSpec, constants: winner.constants }),
  ].join('\n'),
  { label: 'verify:numeric', phase: 'Verify', schema: VERIFY_SCHEMA },
)

return { winner, verdict }
