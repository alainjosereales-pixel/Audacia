import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const JS_DIR = path.resolve(__dirname, '..', 'static', 'js');

async function loadModules() {
  const contourMod = await import('../static/js/contour_engine.js');
  const polyMod = await import('../static/js/polygon_model.js');
  const ContourEngine = contourMod.ContourEngine || contourMod.default?.ContourEngine || globalThis.ContourEngine;
  const ContourAnimator = contourMod.ContourAnimator || contourMod.default?.ContourAnimator || globalThis.ContourAnimator;
  const PolygonObject = polyMod.PolygonObject || polyMod.default?.PolygonObject || globalThis.PolygonObject;
  const SceneModel = polyMod.SceneModel || polyMod.default?.SceneModel || globalThis.SceneModel;
  return { ContourEngine, ContourAnimator, PolygonObject, SceneModel };
}

test('Node and classic browser <script> compatibility without artificial window mocks', async () => {
  assert.equal(typeof window, 'undefined', 'Node test environment must not rely on a window mock');
  const { ContourEngine, ContourAnimator, PolygonObject, SceneModel } = await loadModules();

  assert.equal(typeof ContourEngine, 'function');
  assert.equal(typeof ContourAnimator, 'function');
  assert.equal(typeof PolygonObject, 'function');
  assert.equal(typeof SceneModel, 'function');
  assert.equal(globalThis.ContourEngine, ContourEngine);
  assert.equal(globalThis.ContourAnimator, ContourAnimator);
  assert.equal(globalThis.PolygonObject, PolygonObject);
  assert.equal(globalThis.SceneModel, SceneModel);

  // Verify loading both scripts sequentially in a browser-like shared global scope causes no identifier collision.
  const browserContext = vm.createContext({ Math, Number, Boolean, Array, Object, JSON, Float64Array, Date, console });
  browserContext.window = browserContext;
  const contourCode = fs.readFileSync(path.join(JS_DIR, 'contour_engine.js'), 'utf8');
  const polyCode = fs.readFileSync(path.join(JS_DIR, 'polygon_model.js'), 'utf8');
  vm.runInContext(contourCode, browserContext, { filename: 'contour_engine.js' });
  vm.runInContext(polyCode, browserContext, { filename: 'polygon_model.js' });
  assert.equal(typeof browserContext.window.ContourEngine, 'function');
  assert.equal(typeof browserContext.window.PolygonObject, 'function');

  // Verify standalone polygon_model.js fallback when loaded without contour_engine.js.
  const standaloneContext = vm.createContext({ Math, Number, Boolean, Array, Object, JSON, Float64Array, Date, console });
  standaloneContext.window = standaloneContext;
  vm.runInContext(polyCode, standaloneContext, { filename: 'polygon_model.js' });
  const standalonePoly = standaloneContext.window.PolygonObject.fromJSON({ color: '#ff0055', points: [[0, 0], [1, 0], [1, 1], [0, 1]] });
  assert.equal(standalonePoly.fillEnabled, true);
  assert.equal(standalonePoly.stroke.visible, false);
  assert.equal(standalonePoly.stroke.color, '#ff0055');
});

test('Legacy PolygonObject.fromJSON defaults fillEnabled to true and stroke.visible to false', async () => {
  const { PolygonObject } = await loadModules();

  const legacyData = {
    id: 'poly_legacy_01',
    name: 'Superficie Legacy',
    type: 'quad',
    points: [
      [0.1, 0.1],
      [0.6, 0.1],
      [0.6, 0.6],
      [0.1, 0.6]
    ],
    color: '#ff0055',
    visible: true,
    locked: false,
    media: { type: 'color', src: null, opacity: 1.0, fit: 'stretch', loop: true, playing: true }
  };

  const poly = PolygonObject.fromJSON(legacyData);
  assert.equal(poly.fillEnabled, true);
  assert.ok(poly.stroke);
  assert.equal(poly.stroke.visible, false);
  assert.equal(poly.stroke.color, '#ff0055');
  assert.equal(poly.stroke.width, 4);
  assert.equal(poly.stroke.opacity, 1.0);
  assert.equal(poly.stroke.lineStyle, 'solid');
  assert.equal(poly.stroke.dashScale, 1.0);
  assert.equal(poly.stroke.glow, false);
  assert.deepEqual(poly.stroke.animation, {
    type: 'none',
    speed: 1.0,
    direction: 'cw'
  });
});

test('ContourEngine.normalizeStroke clamps out-of-range values and deep-clones animation', async () => {
  const { ContourEngine } = await loadModules();

  assert.deepEqual(ContourEngine.LINE_STYLES, ['solid', 'dashed', 'dotted', 'dash_dot', 'long_dash', 'double']);
  assert.deepEqual(ContourEngine.ANIMATION_TYPES, ['none', 'march', 'trace_chaser', 'laser_comet', 'dual_chase', 'draw_wipe', 'pulse', 'pulse_wave', 'edge_glitch', 'gradient_march', 'color_flow']);

  const raw = {
    visible: 1,
    color: '#AA00FF',
    width: 99,
    opacity: -0.5,
    lineStyle: 'invalid_style',
    dashScale: 10,
    glow: 'yes',
    animation: {
      type: 'unknown_anim',
      speed: 0.001,
      direction: 'ccw'
    }
  };

  const norm = ContourEngine.normalizeStroke(raw, '#112233');
  assert.deepEqual(norm, {
    visible: true,
    color: '#aa00ff',
    width: 32,
    opacity: 0.05,
    lineStyle: 'solid',
    dashScale: 4.0,
    segmentLength: 1.0,
    glow: true,
    animation: {
      type: 'none',
      speed: 0.1,
      direction: 'ccw'
    }
  });

  raw.animation.direction = 'cw';
  assert.equal(norm.animation.direction, 'ccw');

  const fallbackNorm = ContourEngine.normalizeStroke({ color: 'not-a-hex', width: 0, dashScale: 0.01, animation: { speed: 99 } }, '#ABCDEF');
  assert.equal(fallbackNorm.color, '#abcdef');
  assert.equal(fallbackNorm.width, 1);
  assert.equal(fallbackNorm.dashScale, 0.25);
  assert.equal(fallbackNorm.animation.speed, 5.0);
});

test('Round-trip toJSON -> fromJSON and SceneModel Undo/Redo preserve fillEnabled and stroke', async () => {
  const { PolygonObject, SceneModel } = await loadModules();

  const customStroke = {
    visible: true,
    color: '#12abef',
    width: 10,
    opacity: 0.8,
    lineStyle: 'dash_dot',
    dashScale: 1.75,
    segmentLength: 0.35,
    glow: true,
    animation: {
      type: 'dual_chase',
      speed: 2.4,
      direction: 'ccw',
      chaseStyle: 'comet'
    }
  };

  const poly = new PolygonObject({
    id: 'poly_contour_01',
    type: 'quad',
    points: [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
    color: '#00f0ff',
    fillEnabled: false,
    stroke: customStroke
  });

  const serialized = poly.toJSON();
  poly.stroke.width = 2;
  poly.stroke.animation.speed = 0.5;
  assert.equal(serialized.stroke.width, 10, 'toJSON must deep-clone stroke');
  assert.equal(serialized.stroke.animation.speed, 2.4, 'toJSON must deep-clone stroke.animation');

  const restored = PolygonObject.fromJSON(JSON.parse(JSON.stringify(serialized)));
  assert.equal(restored.fillEnabled, false);
  assert.deepEqual(restored.stroke, customStroke);

  // SceneModel Undo/Redo lifecycle
  const scene = new SceneModel('Escena Contornos');
  const scenePoly = PolygonObject.createDefaultQuad();
  scene.addPolygon(scenePoly);
  assert.equal(scene.getSelectedPolygon().fillEnabled, true);
  assert.equal(scene.getSelectedPolygon().stroke.visible, false);

  scenePoly.fillEnabled = false;
  scenePoly.stroke = JSON.parse(JSON.stringify(customStroke));
  scene.saveStateToHistory();

  assert.equal(scene.undo(), true);
  assert.equal(scene.getSelectedPolygon().fillEnabled, true);
  assert.equal(scene.getSelectedPolygon().stroke.visible, false);

  assert.equal(scene.redo(), true);
  assert.equal(scene.getSelectedPolygon().fillEnabled, false);
  assert.deepEqual(scene.getSelectedPolygon().stroke, customStroke);
});

test('ContourEngine.getClosedPolylinePx discretizes quads, curved Bezier edges, and circles accurately', async () => {
  const { ContourEngine, PolygonObject } = await loadModules();

  // Exact 400x300 px rectangle on 1000x1000 canvas -> perimeter = 1400
  const quad = new PolygonObject({
    type: 'quad',
    points: [[0.1, 0.2], [0.5, 0.2], [0.5, 0.5], [0.1, 0.5]]
  });
  const quadGeo = ContourEngine.getClosedPolylinePx(quad, 1000, 1000);
  assert.equal(quadGeo.points.length, 5);
  assert.deepEqual(quadGeo.points[0], quadGeo.points[4]);
  assert.ok(quadGeo.cumulative instanceof Float64Array);
  assert.equal(quadGeo.cumulative.length, 5);
  assert.equal(quadGeo.cumulative[0], 0);
  assert.ok(Math.abs(quadGeo.totalLength - 1400) < 1e-9);
  assert.match(quadGeo.svgPathD, /^M .+ Z$/);

  // Curved edge 0-1 samples 24 steps
  const curved = PolygonObject.fromJSON(quad.toJSON());
  curved.toggleEdgeCurved(0);
  const curvedGeo = ContourEngine.getClosedPolylinePx(curved, 1000, 1000);
  assert.equal(curvedGeo.points.length, 24 + 3 + 1);
  assert.ok(curvedGeo.totalLength > quadGeo.totalLength);

  // Circle radius 0.2 on 1000x1000 canvas -> circumference = 2 * pi * 200
  const circle = new PolygonObject({
    type: 'circle',
    center: [0.5, 0.5],
    radiusX: 0.2,
    radiusY: 0.2,
    rotation: 0
  });
  const circleGeo = ContourEngine.getClosedPolylinePx(circle, 1000, 1000);
  assert.equal(circleGeo.points.length, 97);
  const expectedCirc = 2 * Math.PI * 200;
  assert.ok(Math.abs(circleGeo.totalLength - expectedCirc) / expectedCirc < 0.001);
});

test('ContourEngine.computeDashPattern quantizes seamlessly across arbitrary perimeters and all dashed styles', async () => {
  const { ContourEngine } = await loadModules();

  assert.deepEqual(ContourEngine.computeDashPattern('solid', 4, 1.0, 1000), []);
  assert.deepEqual(ContourEngine.computeDashPattern('double', 4, 1.0, 1000), []);

  const perimeters = [317.4, 1024.9, 2890.1];
  const dashedStyles = ['dashed', 'dotted', 'dash_dot', 'long_dash'];

  for (const L of perimeters) {
    for (const style of dashedStyles) {
      const pattern = ContourEngine.computeDashPattern(style, 6, 1.3, L);
      assert.ok(pattern.length >= 2, `Expected non-empty pattern for ${style}`);
      const period = pattern.reduce((a, b) => a + b, 0);
      const repeats = Math.round(L / period);
      assert.ok(repeats >= 1);
      assert.ok(
        Math.abs(repeats * period - L) < 1e-6,
        `Seam mismatch for style=${style}, L=${L}: repeats=${repeats}, period=${period}`
      );
    }
  }
});

test('ContourEngine.evaluateAtTime is deterministic and closes seamless loops at loopDuration = 8.0', async () => {
  const { ContourEngine, PolygonObject } = await loadModules();

  const animTypes = ['march', 'trace_chaser', 'laser_comet', 'dual_chase', 'draw_wipe', 'pulse', 'gradient_march', 'color_flow'];
  const directions = ['cw', 'ccw'];

  for (const animType of animTypes) {
    for (const direction of directions) {
      const poly = new PolygonObject({
        type: 'quad',
        points: [[0.15, 0.15], [0.85, 0.15], [0.85, 0.85], [0.15, 0.85]],
        color: '#ffffff',
        fillEnabled: false,
        stroke: {
          visible: true,
          color: '#ffffff',
          width: 6,
          opacity: 0.9,
          lineStyle: 'dashed',
          dashScale: 1.0,
          glow: true,
          animation: {
            type: animType,
            speed: 1.0,
            direction
          }
        }
      });

      const s0 = ContourEngine.evaluateAtTime(poly, 0.0, 1920, 1080, 8.0);
      const s0Repeat = ContourEngine.evaluateAtTime(poly, 0.0, 1920, 1080, 8.0);
      const sEnd = ContourEngine.evaluateAtTime(poly, 8.0, 1920, 1080, 8.0);
      const sMid = ContourEngine.evaluateAtTime(poly, 1.1, 1920, 1080, 8.0);

      // Pure function determinism
      assert.equal(s0.phase, s0Repeat.phase);
      assert.equal(s0.dashOffset, s0Repeat.dashOffset);
      assert.equal(s0.color, s0Repeat.color);

      // Seamless loop closure between t = 0.0 and t = 8.0
      assert.equal(s0.phase, sEnd.phase, `${animType} (${direction}) phase mismatch at loop boundary`);
      assert.equal(s0.dashOffset, sEnd.dashOffset, `${animType} (${direction}) dashOffset mismatch`);
      assert.equal(s0.color, sEnd.color, `${animType} (${direction}) color mismatch`);
      assert.ok(Math.abs(s0.opacity - sEnd.opacity) < 1e-12, `${animType} (${direction}) opacity mismatch`);
      assert.ok(Math.abs(s0.width - sEnd.width) < 1e-12, `${animType} (${direction}) width mismatch`);
      assert.ok(Math.abs(s0.wipeLength - sEnd.wipeLength) < 1e-12, `${animType} (${direction}) wipeLength mismatch`);
      assert.ok(Math.abs(s0.wipeStartS - sEnd.wipeStartS) < 1e-12, `${animType} (${direction}) wipeStartS mismatch`);
      assert.deepEqual(s0.cometHeads, sEnd.cometHeads, `${animType} (${direction}) cometHeads mismatch`);

      // Non-static progression at t = 1.1
      assert.notEqual(sMid.phase, s0.phase, `${animType} (${direction}) must advance at t = 1.1`);
      if (animType === 'march') assert.notEqual(sMid.dashOffset, s0.dashOffset);
      if (animType === 'laser_comet' || animType === 'dual_chase') assert.notDeepEqual(sMid.cometHeads, s0.cometHeads);
      if (animType === 'draw_wipe') assert.notEqual(sMid.wipeLength, s0.wipeLength);
      if (animType === 'trace_chaser') assert.notEqual(sMid.chaserHeadS, s0.chaserHeadS);
      if (animType === 'pulse') {
        assert.notEqual(sMid.opacity, s0.opacity);
        assert.notEqual(sMid.width, s0.width, 'Pulse must modulate physical width');
      }
      if (animType === 'color_flow') assert.notEqual(sMid.color, s0.color);
      if (animType === 'gradient_march') assert.notEqual(sMid.gradientPhase, s0.gradientPhase);
    }
  }

  // Verify hslShiftHex handles both chromatic and achromatic (#ffffff / #000000) bases visibly
  const white0 = ContourEngine.hslShiftHex('#ffffff', 0);
  const white120 = ContourEngine.hslShiftHex('#ffffff', 120);
  const white360 = ContourEngine.hslShiftHex('#ffffff', 360);
  assert.match(white120, /^#[0-9a-f]{6}$/);
  assert.notEqual(white120, '#ffffff');
  assert.notEqual(white120, white0);
  assert.equal(white0, white360);
  assert.equal(ContourEngine.hslShiftHex('#00f0ff', 0), '#00f0ff');
  assert.equal(ContourEngine.hslShiftHex('#00f0ff', 360), '#00f0ff');

  // Verify buildSvgElements visibility gate
  const hiddenPoly = PolygonObject.createDefaultQuad();
  assert.equal(ContourEngine.buildSvgElements(hiddenPoly, 0), '');
  hiddenPoly.stroke.visible = true;
  const svgMarkup = ContourEngine.buildSvgElements(hiddenPoly, 0);
  assert.match(svgMarkup, /<path\b/);
});

test('ContourAnimator maintains 0% idle CPU when inactive and cancels RAF immediately when paused or static', async () => {
  const { ContourAnimator, PolygonObject } = await loadModules();

  let nowSec = 0;
  let nextId = 0;
  const scheduled = new Map();
  const cancelledIds = [];
  const tickTimes = [];

  const raf = (cb) => {
    const id = ++nextId;
    scheduled.set(id, cb);
    return id;
  };
  const caf = (id) => {
    cancelledIds.push(id);
    scheduled.delete(id);
  };

  const animator = new ContourAnimator((t) => tickTimes.push(t), () => nowSec, raf, caf);
  assert.equal(scheduled.size, 0, 'Must not schedule RAF on construction');

  // 1. Stroke hidden -> 0 RAF
  const pHidden = PolygonObject.createDefaultQuad();
  pHidden.stroke.visible = false;
  pHidden.stroke.animation.type = 'march';
  animator.syncPolygons([pHidden], false);
  assert.equal(scheduled.size, 0);

  // 2. Stroke visible but animation === 'none' -> 0 RAF
  const pStatic = PolygonObject.createDefaultQuad();
  pStatic.stroke.visible = true;
  pStatic.stroke.animation.type = 'none';
  animator.syncPolygons([pStatic], false);
  assert.equal(scheduled.size, 0);

  // 3. Occluder with animated stroke -> 0 RAF
  const pOccluder = PolygonObject.createDefaultSquare(0.5, 0.5, 0.25, true);
  pOccluder.stroke.visible = true;
  pOccluder.stroke.animation.type = 'laser_comet';
  animator.syncPolygons([pOccluder], false);
  assert.equal(scheduled.size, 0);

  // 4. Active animated polygon while paused === true -> 0 RAF
  const pActive = PolygonObject.createDefaultQuad();
  pActive.stroke.visible = true;
  pActive.stroke.animation.type = 'march';
  animator.syncPolygons([pActive], true);
  assert.equal(scheduled.size, 0);

  // 5. Active animated polygon while paused === false -> starts RAF loop
  animator.syncPolygons([pActive], false);
  assert.equal(scheduled.size, 1);
  const firstId = [...scheduled.keys()][0];

  // Stepping frame invokes onTick and schedules next frame
  nowSec = 0.5;
  const firstCb = scheduled.get(firstId);
  scheduled.delete(firstId);
  firstCb();
  assert.deepEqual(tickTimes, [0.5]);
  assert.equal(scheduled.size, 1);
  const secondId = [...scheduled.keys()][0];

  // 6. Pausing immediately cancels pending RAF
  animator.syncPolygons([pActive], true);
  assert.equal(scheduled.size, 0);
  assert.ok(cancelledIds.includes(secondId));

  // 7. Resuming then switching animation.type to 'none' immediately cancels RAF
  animator.syncPolygons([pActive], false);
  assert.equal(scheduled.size, 1);
  const thirdId = [...scheduled.keys()][0];
  pActive.stroke.animation.type = 'none';
  animator.syncPolygons([pActive], false);
  assert.equal(scheduled.size, 0);
  assert.ok(cancelledIds.includes(thirdId));

  // 8. Destroy cancels RAF and prevents future scheduling
  pActive.stroke.animation.type = 'pulse';
  animator.syncPolygons([pActive], false);
  assert.equal(scheduled.size, 1);
  const fourthId = [...scheduled.keys()][0];
  animator.destroy();
  assert.equal(scheduled.size, 0);
  assert.ok(cancelledIds.includes(fourthId));

  animator.syncPolygons([pActive], false);
  assert.equal(scheduled.size, 0);
});

test('ContourEngine.buildSvgElements renders double twin-rail mask, comet trails, progressive wipe mask, and isolated optical glow', async () => {
  const { ContourEngine, PolygonObject } = await loadModules();

  const makeQuad = (id, strokeOverrides) => new PolygonObject({
    id,
    type: 'quad',
    points: [[0.15, 0.2], [0.85, 0.2], [0.85, 0.8], [0.15, 0.8]],
    color: '#00f0ff',
    fillEnabled: false,
    stroke: {
      visible: true,
      color: '#00f0ff',
      width: 6,
      opacity: 0.9,
      lineStyle: 'solid',
      dashScale: 1.0,
      glow: false,
      animation: { type: 'none', speed: 1.0, direction: 'cw' },
      ...strokeOverrides
    }
  });

  // 1. lineStyle: 'double' generates twin-rail <mask id="cdouble-...">
  const doublePoly = makeQuad('poly_dbl_1', { lineStyle: 'double' });
  const doubleSvg = ContourEngine.buildSvgElements(doublePoly, 0);
  assert.match(doubleSvg, /<mask id="cdouble-poly_dbl_1"/);
  assert.match(doubleSvg, /mask="url\(#cdouble-poly_dbl_1\)"/);
  assert.match(doubleSvg, /stroke="#000000"/, 'Double mask must cut out center rail with black stroke');

  // 2. animation.type: 'laser_comet' (>= 10 <path) and 'dual_chase' (>= 20 <path, twice as many comet segments)
  const cometPoly = makeQuad('poly_comet_1', {
    animation: { type: 'laser_comet', speed: 1.0, direction: 'cw' }
  });
  const dualPoly = makeQuad('poly_dual_1', {
    animation: { type: 'dual_chase', speed: 1.0, direction: 'ccw' }
  });
  const cometSvg = ContourEngine.buildSvgElements(cometPoly, 0.7);
  const dualSvg = ContourEngine.buildSvgElements(dualPoly, 0.7);
  const countPaths = (s) => (s.match(/<path\b/g) || []).length;
  const cometPaths = countPaths(cometSvg);
  const dualPaths = countPaths(dualSvg);
  assert.ok(cometPaths >= 10, `Expected >= 10 <path> elements for laser_comet, got ${cometPaths}`);
  assert.ok(dualPaths >= 20, `Expected >= 20 <path> elements for dual_chase, got ${dualPaths}`);
  assert.ok(dualPaths >= (cometPaths - 1) * 2, `Expected dual_chase (${dualPaths}) to have twice the comet segments of laser_comet (${cometPaths})`);

  // 3. animation.type: 'draw_wipe' generates progressive wipe <mask id="cwipe-...">
  const wipePoly = makeQuad('poly_wipe_1', {
    lineStyle: 'dashed',
    animation: { type: 'draw_wipe', speed: 1.0, direction: 'cw' }
  });
  const wipeSvg = ContourEngine.buildSvgElements(wipePoly, 1.0);
  assert.match(wipeSvg, /<mask id="cwipe-poly_wipe_1"/);
  assert.match(wipeSvg, /mask="url\(#cwipe-poly_wipe_1\)"/);

  // 4. glow: true generates isolated <filter id="cglow-..."> and filter="url(#cglow-...)"
  const glowPoly = makeQuad('poly "unsafe" #1', {
    lineStyle: 'dotted',
    glow: true
  });
  const glowSvg = ContourEngine.buildSvgElements(glowPoly, 0);
  assert.match(glowSvg, /<filter id="cglow-poly__unsafe___1"/);
  assert.match(glowSvg, /<feGaussianBlur\b/);
  assert.match(glowSvg, /<feMerge>/);
  assert.match(glowSvg, /filter="url\(#cglow-poly__unsafe___1\)"/);
  assert.match(glowSvg, /stroke-linecap="round"/, 'Dotted lineStyle must use round linecap');

  // 5. NaN / degenerate guard
  const nanPoly = makeQuad('poly_nan', {});
  nanPoly.points = [[NaN, 0.2], [0.5, 0.2], [0.5, 0.5]];
  assert.equal(ContourEngine.buildSvgElements(nanPoly, 0), '');
});

test('ContourEngine.computeControllerGuideSpec covers all 4 fill/stroke states plus occluder and media hit-testing', async () => {
  const { ContourEngine, PolygonObject } = await loadModules();

  assert.equal(typeof ContourEngine.computeControllerGuideSpec, 'function');

  // 1. fillEnabled: true, stroke.visible: false
  const pFillOnly = PolygonObject.createDefaultQuad();
  pFillOnly.color = '#12abef';
  pFillOnly.fillEnabled = true;
  pFillOnly.stroke.visible = false;
  const sFillOnlyUnsel = ContourEngine.computeControllerGuideSpec(pFillOnly, false);
  const sFillOnlySel = ContourEngine.computeControllerGuideSpec(pFillOnly, true);
  assert.equal(sFillOnlyUnsel.renderLiveContour, false);
  assert.equal(sFillOnlyUnsel.fill, '#12abef');
  assert.equal(sFillOnlyUnsel.fillOpacity, 0.18);
  assert.equal(sFillOnlyUnsel.guideStroke, '#12abef');
  assert.equal(sFillOnlyUnsel.guideDash, 'none');
  assert.equal(sFillOnlySel.fillOpacity, 0.35);
  assert.equal(sFillOnlySel.guideWidth, 2.4);

  // 2. fillEnabled: true, stroke.visible: true
  const pBoth = PolygonObject.fromJSON(pFillOnly.toJSON());
  pBoth.stroke.visible = true;
  const sBothUnsel = ContourEngine.computeControllerGuideSpec(pBoth, false);
  const sBothSel = ContourEngine.computeControllerGuideSpec(pBoth, true);
  assert.equal(sBothUnsel.renderLiveContour, true);
  assert.equal(sBothUnsel.fill, '#12abef');
  assert.equal(sBothUnsel.guideStroke, 'none');
  assert.equal(sBothSel.renderLiveContour, true);
  assert.equal(sBothSel.guideStroke, '#ffffff');
  assert.equal(sBothSel.guideDash, '4 4');

  // 3. fillEnabled: false, stroke.visible: true
  const pContourOnly = PolygonObject.fromJSON(pBoth.toJSON());
  pContourOnly.fillEnabled = false;
  const sContourUnsel = ContourEngine.computeControllerGuideSpec(pContourOnly, false);
  const sContourSel = ContourEngine.computeControllerGuideSpec(pContourOnly, true);
  assert.equal(sContourUnsel.renderLiveContour, true);
  assert.equal(sContourUnsel.fill, '#000000');
  assert.ok(sContourUnsel.fillOpacity > 0, 'Interior hit-testing requires non-zero fillOpacity');
  assert.equal(sContourSel.fillOpacity, 0.06);

  // 4. fillEnabled: false, stroke.visible: false -> controller visibility guarantee
  const pGhost = PolygonObject.fromJSON(pFillOnly.toJSON());
  pGhost.fillEnabled = false;
  pGhost.stroke.visible = false;
  const sGhostUnsel = ContourEngine.computeControllerGuideSpec(pGhost, false);
  const sGhostSel = ContourEngine.computeControllerGuideSpec(pGhost, true);
  assert.equal(sGhostUnsel.renderLiveContour, false);
  assert.equal(sGhostUnsel.guideDash, '5 4');
  assert.ok(sGhostUnsel.guideOpacity > 0.3);
  assert.ok(sGhostUnsel.fillOpacity > 0);
  assert.equal(sGhostSel.guideDash, '5 4');
  assert.ok(sGhostSel.guideOpacity > 0.8);
  assert.ok(sGhostSel.fillOpacity > 0);

  // 5. Occluder & Media
  const pOcc = PolygonObject.createDefaultSquare(0.5, 0.5, 0.2, true);
  const sOcc = ContourEngine.computeControllerGuideSpec(pOcc, false);
  assert.equal(sOcc.fill, '#000000');
  assert.equal(sOcc.fillOpacity, 0.95);
  assert.equal(sOcc.renderLiveContour, false);
  assert.equal(sOcc.guideDash, '6 4');

  const pMedia = PolygonObject.fromJSON(pFillOnly.toJSON());
  pMedia.media = { type: 'video', src: '/media/sample.mp4', opacity: 1.0 };
  const sMedia = ContourEngine.computeControllerGuideSpec(pMedia, false);
  assert.equal(sMedia.fill, '#000000');
  assert.equal(sMedia.fillOpacity, 0.001);
});

function createFakeDomEnv() {
  class FakeElement {
    constructor(tagName, id = '') {
      this.tagName = String(tagName).toUpperCase();
      this.id = id;
      this.className = '';
      this.style = {};
      this.dataset = {};
      this.attributes = {};
      this.children = [];
      this.parentNode = null;
      this._innerHTML = '';
      this.textContent = '';
      const classes = new Set();
      this.classList = {
        add: (...names) => names.forEach(n => classes.add(n)),
        remove: (...names) => names.forEach(n => classes.delete(n)),
        contains: (n) => classes.has(n)
      };
    }

    get innerHTML() {
      return this._innerHTML;
    }

    set innerHTML(val) {
      this._innerHTML = String(val);
      this.children = [];
    }

    setAttribute(name, val) {
      const str = String(val);
      this.attributes[name] = str;
      if (name === 'class') this.className = str;
      if (name === 'id') this.id = str;
      if (name.startsWith('data-')) {
        const camel = name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
        this.dataset[camel] = str;
      }
    }

    getAttribute(name) {
      return this.attributes[name] ?? null;
    }

    appendChild(child) {
      if (child.parentNode) {
        const idx = child.parentNode.children.indexOf(child);
        if (idx !== -1) child.parentNode.children.splice(idx, 1);
      }
      child.parentNode = this;
      this.children.push(child);
      return child;
    }

    remove() {
      if (this.parentNode) {
        const idx = this.parentNode.children.indexOf(this);
        if (idx !== -1) this.parentNode.children.splice(idx, 1);
        this.parentNode = null;
      }
    }

    addEventListener() {}

    getBoundingClientRect() {
      return { width: 960, height: 540, top: 0, left: 0 };
    }

    querySelectorAll(selector) {
      const results = [];
      const visit = (node) => {
        for (const child of node.children) {
          if (matchesSelector(child, selector)) results.push(child);
          visit(child);
        }
      };
      visit(this);
      return results;
    }

    querySelector(selector) {
      return this.querySelectorAll(selector)[0] || null;
    }
  }

  function matchesSelector(el, selector) {
    if (selector.startsWith('#')) return el.id === selector.slice(1);
    if (selector.startsWith('.')) return (el.className || '').split(/\s+/).includes(selector.slice(1));
    const dataExact = selector.match(/^\[data-([a-z-]+)="([^"]+)"\]$/);
    if (dataExact) {
      const camel = dataExact[1].replace(/-([a-z])/g, (_, c) => c.toUpperCase());
      return el.dataset[camel] === dataExact[2];
    }
    const dataAttr = selector.match(/^\[data-([a-z-]+)\]$/);
    if (dataAttr) {
      const camel = dataAttr[1].replace(/-([a-z])/g, (_, c) => c.toUpperCase());
      return el.dataset[camel] !== undefined;
    }
    return el.tagName.toLowerCase() === selector.toLowerCase();
  }

  const byId = new Map();
  const register = (el) => {
    if (el.id) byId.set(el.id, el);
    return el;
  };

  return { FakeElement, byId, register };
}

test('ProjectorView interleaves fill/contour Z-indices, omits disabled fills, and pauses ContourAnimator during export', async () => {
  const { FakeElement, byId, register } = createFakeDomEnv();

  const projectorContainer = register(new FakeElement('div', 'projector-container'));
  const mediaViewport = register(new FakeElement('div', 'projector-media-viewport'));
  const projectorSvg = register(new FakeElement('svg', 'projector-svg'));
  const vectorLayer = register(new FakeElement('g', 'p-vector-layer'));
  const calibLayer = register(new FakeElement('g', 'p-calibration-layer'));
  const statusPill = register(new FakeElement('div', 'status-pill'));
  const pauseOverlay = register(new FakeElement('div', 'export-pause-overlay'));
  const pauseBar = register(new FakeElement('div', 'export-pause-bar-fill'));
  const pauseTxt = register(new FakeElement('div', 'export-pause-pct-txt'));

  // Intercept container.innerHTML in initDOM so pre-registered child IDs stay resolvable.
  Object.defineProperty(projectorContainer, 'innerHTML', {
    get() { return this._innerHTML; },
    set(v) { this._innerHTML = v; }
  });

  const syncHandlers = {};
  class FakeMappingSync {
    constructor() {}
    on(evt, cb) { syncHandlers[evt] = cb; }
    requestSync() {}
    broadcastDisplayMode() {}
  }

  let rafSeq = 0;
  const activeRafs = new Map();
  const ctx = vm.createContext({
    Math, Number, Boolean, String, Array, Object, Set, Map, JSON, Float64Array, Date, console,
    performance: { now: () => 1200 },
    requestAnimationFrame: (cb) => { const id = ++rafSeq; activeRafs.set(id, cb); return id; },
    cancelAnimationFrame: (id) => { activeRafs.delete(id); },
    MappingSync: FakeMappingSync,
    innerWidth: 1920,
    innerHeight: 1080,
    addEventListener: () => {},
    document: {
      body: new FakeElement('body'),
      fullscreenElement: null,
      documentElement: { requestFullscreen: () => Promise.resolve() },
      getElementById: (id) => byId.get(id) || null,
      createElement: (tag) => new FakeElement(tag),
      createElementNS: (_ns, tag) => new FakeElement(tag)
    }
  });
  ctx.window = ctx;

  for (const scriptName of ['math_warp.js', 'contour_engine.js', 'polygon_model.js', 'projector_view.js']) {
    const code = fs.readFileSync(path.join(JS_DIR, scriptName), 'utf8');
    vm.runInContext(code, ctx, { filename: scriptName });
  }

  const projector = new ctx.window.ProjectorView();
  const PolygonObject = ctx.window.PolygonObject;

  const p0 = new PolygonObject({
    id: 'p_base',
    type: 'quad',
    points: [[0.1, 0.1], [0.5, 0.1], [0.5, 0.5], [0.1, 0.5]],
    color: '#00f0ff',
    fillEnabled: true,
    stroke: {
      visible: true,
      color: '#ff0055',
      width: 6,
      opacity: 1.0,
      lineStyle: 'solid',
      glow: true,
      animation: { type: 'laser_comet', speed: 1.0, direction: 'cw' }
    }
  });

  const p1 = new PolygonObject({
    id: 'p_hollow',
    type: 'quad',
    points: [[0.2, 0.2], [0.6, 0.2], [0.6, 0.6], [0.2, 0.6]],
    color: '#00ff88',
    fillEnabled: false,
    stroke: {
      visible: true,
      color: '#00ff88',
      width: 4,
      opacity: 0.9,
      lineStyle: 'double',
      glow: false,
      animation: { type: 'none', speed: 1.0, direction: 'cw' }
    }
  });

  const p2 = PolygonObject.createDefaultSquare(0.4, 0.4, 0.25, true);
  p2.id = 'p_occ_top';
  p2.stroke.visible = true;

  syncHandlers.SYNC_FULL_STATE({
    displayMode: 'show',
    polygons: [p0.toJSON(), p1.toJSON(), p2.toJSON()]
  });

  // Vector SVG layer must stay empty so solid fills never sit above videos/occluders
  assert.equal(vectorLayer.innerHTML, '');

  const fill0 = mediaViewport.querySelector('[data-poly-id="p_base"]');
  const contour0 = mediaViewport.querySelector('[data-contour-id="p_base"]');
  const fill1 = mediaViewport.querySelector('[data-poly-id="p_hollow"]');
  const contour1 = mediaViewport.querySelector('[data-contour-id="p_hollow"]');
  const fill2 = mediaViewport.querySelector('[data-poly-id="p_occ_top"]');
  const contour2 = mediaViewport.querySelector('[data-contour-id="p_occ_top"]');

  assert.ok(fill0, 'p0 with fillEnabled: true must render color-fill div');
  assert.equal(fill0.dataset.kind, 'color-fill');
  assert.equal(fill0.style.zIndex, '1');
  assert.ok(contour0, 'p0 with stroke.visible: true must render contour svg');
  assert.equal(contour0.style.zIndex, '2');

  assert.equal(fill1, null, 'p1 with fillEnabled: false must render ZERO fill DOM element');
  assert.ok(contour1, 'p1 with stroke.visible: true must still render contour svg');
  assert.equal(contour1.style.zIndex, '4');

  assert.ok(fill2, 'p2 occluder must render occluder div');
  assert.equal(fill2.dataset.kind, 'occluder');
  assert.equal(fill2.style.zIndex, '5', 'Higher-layer occluder (zIndex=5) must cover lower contours (zIndex=2,4)');
  assert.equal(contour2, null, 'Occluders must never render projected contours');

  // Active animation started RAF; export state must pause and resume it cleanly
  assert.equal(projector.contourAnimator.isRunning, true);
  assert.equal(activeRafs.size, 1);

  const beforeTickHtml = contour0.innerHTML;
  const staticBeforeHtml = contour1.innerHTML;
  projector.updateAnimatedContours(2.35);
  assert.notEqual(contour0.innerHTML, beforeTickHtml, 'Animated contour must update on tick');
  assert.equal(contour1.innerHTML, staticBeforeHtml, 'Static contour must not be rewritten on tick');

  projector.handleExportState(true, 45, 'Exportando...');
  assert.equal(projector.isExportPaused, true);
  assert.equal(projector.contourAnimator.isRunning, false);
  assert.equal(activeRafs.size, 0);

  projector.handleExportState(false, 100, '');
  assert.equal(projector.isExportPaused, false);
  assert.equal(projector.contourAnimator.isRunning, true);
  assert.equal(activeRafs.size, 1);
});

test('EditorCanvas renders 4-state controller guides, live contours, and updates only animated contour groups on tick', async () => {
  const { FakeElement } = createFakeDomEnv();

  const containerEl = new FakeElement('div', 'canvas-container');
  const childIds = [
    ['div', 'canvas-viewport'],
    ['div', 'warped-media-layer'],
    ['svg', 'editor-svg'],
    ['defs', 'svg-defs'],
    ['g', 'grid-layer'],
    ['g', 'polygons-layer'],
    ['g', 'draft-layer'],
    ['g', 'gizmo-layer'],
    ['g', 'marquee-layer']
  ];
  const nodes = {};
  for (const [tag, id] of childIds) {
    const el = new FakeElement(tag, id);
    nodes[id] = el;
    containerEl.appendChild(el);
  }
  Object.defineProperty(containerEl, 'innerHTML', {
    get() { return this._innerHTML; },
    set(v) { this._innerHTML = v; }
  });

  // Hydrate .editor-contour-group children whenever polygonsLayer.innerHTML is assigned.
  const polygonsLayer = nodes['polygons-layer'];
  Object.defineProperty(polygonsLayer, 'innerHTML', {
    get() { return this._innerHTML; },
    set(v) {
      this._innerHTML = String(v);
      this.children = [];
      const re = /<g\s+class="editor-contour-group"\s+data-editor-contour-id="([^"]+)"[^>]*>([\s\S]*?)<\/g>(?=\s*<text\b)/g;
      let m;
      while ((m = re.exec(this._innerHTML)) !== null) {
        const gEl = new FakeElement('g');
        gEl.setAttribute('class', 'editor-contour-group');
        gEl.setAttribute('data-editor-contour-id', m[1]);
        gEl.innerHTML = m[2];
        this.appendChild(gEl);
      }
    }
  });

  let rafSeq = 0;
  const activeRafs = new Map();
  const ctx = vm.createContext({
    Math, Number, Boolean, String, Array, Object, Set, Map, JSON, Float64Array, Date, console,
    performance: { now: () => 500 },
    requestAnimationFrame: (cb) => { const id = ++rafSeq; activeRafs.set(id, cb); return id; },
    cancelAnimationFrame: (id) => { activeRafs.delete(id); },
    localStorage: { getItem: () => null, setItem: () => {} },
    addEventListener: () => {},
    document: {
      createElement: (tag) => new FakeElement(tag),
      createElementNS: (_ns, tag) => new FakeElement(tag)
    }
  });
  ctx.window = ctx;

  for (const scriptName of ['math_warp.js', 'contour_engine.js', 'polygon_model.js', 'editor_canvas.js']) {
    const code = fs.readFileSync(path.join(JS_DIR, scriptName), 'utf8');
    vm.runInContext(code, ctx, { filename: scriptName });
  }

  const { SceneModel, PolygonObject, EditorCanvas } = ctx.window;
  const scene = new SceneModel('Test Editor');

  const pGhost = new PolygonObject({
    id: 'p_ghost',
    type: 'quad',
    points: [[0.1, 0.1], [0.3, 0.1], [0.3, 0.3], [0.1, 0.3]],
    color: '#00f0ff',
    fillEnabled: false,
    stroke: { visible: false }
  });
  const pStatic = new PolygonObject({
    id: 'p_static',
    type: 'quad',
    points: [[0.4, 0.1], [0.6, 0.1], [0.6, 0.3], [0.4, 0.3]],
    color: '#ffaa00',
    fillEnabled: false,
    stroke: { visible: true, color: '#ffaa00', width: 5, animation: { type: 'none', speed: 1, direction: 'cw' } }
  });
  const pAnim = new PolygonObject({
    id: 'p_anim',
    type: 'quad',
    points: [[0.4, 0.5], [0.8, 0.5], [0.8, 0.85], [0.4, 0.85]],
    color: '#ff0055',
    fillEnabled: true,
    stroke: { visible: true, color: '#ff0055', width: 6, animation: { type: 'march', speed: 1.5, direction: 'cw' } }
  });

  scene.addPolygon(pGhost);
  scene.addPolygon(pStatic);
  scene.addPolygon(pAnim);
  scene.selectedId = 'p_ghost';

  const syncStub = {
    broadcastDraftClear: () => {},
    broadcastDraftUpdate: () => {},
    broadcastSelection: () => {},
    broadcastPolygonUpdate: () => {},
    broadcastPolygonDelete: () => {},
    broadcastFullState: () => {}
  };

  const editor = new EditorCanvas(containerEl, scene, syncStub);

  // Selected ghost surface has dashed 5 4 controller guide and pointer-events="all"
  assert.match(polygonsLayer.innerHTML, /stroke-dasharray="5 4"/);
  assert.match(polygonsLayer.innerHTML, /pointer-events="all"/);

  const staticGroup = polygonsLayer.querySelector('[data-editor-contour-id="p_static"]');
  const animGroup = polygonsLayer.querySelector('[data-editor-contour-id="p_anim"]');
  const ghostGroup = polygonsLayer.querySelector('[data-editor-contour-id="p_ghost"]');

  assert.equal(ghostGroup, null, 'Surface with stroke.visible=false must not render live contour group');
  assert.ok(staticGroup, 'Static visible contour must render editor-contour-group');
  assert.ok(animGroup, 'Animated visible contour must render editor-contour-group');
  assert.equal(editor.contourAnimator.isRunning, true);

  const staticBefore = staticGroup.innerHTML;
  const animBefore = animGroup.innerHTML;
  const gizmoBefore = nodes['gizmo-layer'].innerHTML;

  editor.updateAnimatedContours(1.75);
  assert.equal(staticGroup.innerHTML, staticBefore, 'Static contour group must not be touched on tick');
  assert.notEqual(animGroup.innerHTML, animBefore, 'Animated contour group must update on tick');
  assert.equal(nodes['gizmo-layer'].innerHTML, gizmoBefore, 'Gizmo layer must not re-render on tick');
});

test('Task 3: ContourEngine.applyInspectorUpdate updates properties and enforces smart auto-switch', () => {
  assert.equal(typeof ContourEngine.applyInspectorUpdate, 'function', 'ContourEngine.applyInspectorUpdate must be defined');

  const poly = new PolygonObject({
    id: 'p_inspect',
    color: '#00f0ff',
    stroke: { visible: false, lineStyle: 'solid', animation: { type: 'none' } }
  });

  // Toggle fillEnabled off
  const res1 = ContourEngine.applyInspectorUpdate(poly, { fillEnabled: false });
  assert.equal(poly.fillEnabled, false);
  assert.equal(res1.autoSwitchedLineStyle, false);

  // Toggle stroke visible
  ContourEngine.applyInspectorUpdate(poly, { strokeVisible: true, strokeWidth: 8, strokeOpacity: 0.85, strokeGlow: true });
  assert.equal(poly.stroke.visible, true);
  assert.equal(poly.stroke.width, 8);
  assert.equal(poly.stroke.opacity, 0.85);
  assert.equal(poly.stroke.glow, true);

  // Selecting march while in solid automatically switches lineStyle to dashed
  assert.equal(poly.stroke.lineStyle, 'solid');
  const resMarch = ContourEngine.applyInspectorUpdate(poly, { animationType: 'march' });
  assert.equal(poly.stroke.animation.type, 'march');
  assert.equal(poly.stroke.lineStyle, 'dashed', 'March on solid must auto-switch to dashed');
  assert.equal(resMarch.autoSwitchedLineStyle, true);

  // Selecting march while already in dotted preserves dotted
  poly.stroke.lineStyle = 'dotted';
  const resDotted = ContourEngine.applyInspectorUpdate(poly, { animationType: 'march' });
  assert.equal(poly.stroke.lineStyle, 'dotted', 'March on non-solid preserves existing lineStyle');
  assert.equal(resDotted.autoSwitchedLineStyle, false);

  // Direction and speed
  ContourEngine.applyInspectorUpdate(poly, { animationSpeed: 2.5, animationDirection: 'ccw' });
  assert.equal(poly.stroke.animation.speed, 2.5);
  assert.equal(poly.stroke.animation.direction, 'ccw');
});

test('Task 3: Icons and UI Inspector integration meets anti-slop rules', async () => {
  const fs = await import('node:fs/promises');
  const { fileURLToPath } = await import('node:url');
  const iconsPath = fileURLToPath(new URL('../static/js/icons.js', import.meta.url));
  const uiPath = fileURLToPath(new URL('../static/js/ui_controller.js', import.meta.url));

  const iconsSrc = await fs.readFile(iconsPath, 'utf-8');
  const uiSrc = await fs.readFile(uiPath, 'utf-8');

  // Icons check
  for (const iconName of ['slash-circle', 'droplet', 'sun', 'rotate-cw', 'rotate-ccw', 'activity']) {
    assert.match(iconsSrc, new RegExp(`'${iconName}'|"${iconName}"`), `Icon ${iconName} must be defined in icons.js`);
  }

  // Required UI IDs in ui_controller.js
  for (const id of [
    'btn-fill-on', 'btn-fill-off', 'btn-toggle-stroke',
    'prop-stroke-color', 'prop-stroke-width', 'prop-stroke-opacity',
    'btn-stroke-glow', 'prop-stroke-style', 'prop-stroke-anim',
    'prop-stroke-speed', 'btn-dir-cw', 'btn-dir-ccw'
  ]) {
    assert.ok(uiSrc.includes(id), `ui_controller.js must include control #${id}`);
  }

  // Zero em-dashes in ui_controller.js (R-02)
  assert.equal(uiSrc.includes('—'), false, 'ui_controller.js must contain zero em-dashes (—)');
});




test('v2.9.1: trace_chaser, segmentLength, physical pulse width, and zero background silhouette', async () => {
  const { ContourEngine, PolygonObject } = await loadModules();

  // 1. laser_comet and dual_chase have ZERO background rail with dim opacity
  const cometPoly = PolygonObject.createDefaultQuad();
  cometPoly.stroke = {
    visible: true,
    color: '#00f0ff',
    width: 6,
    opacity: 0.9,
    lineStyle: 'solid',
    segmentLength: 0.25,
    animation: { type: 'laser_comet', speed: 1.0, direction: 'cw' }
  };
  const cometSvg = ContourEngine.buildSvgElements(cometPoly, 0.5);
  assert.ok(!cometSvg.includes('stroke-opacity="0.126"'), 'Must NOT render dim background rail');
  assert.ok(!cometSvg.includes('stroke-opacity="0.14"'), 'Must NOT render dim background rail');

  // 2. trace_chaser (El Gusano) renders moving segment and head dot
  const chaserPoly = PolygonObject.createDefaultQuad();
  chaserPoly.stroke = {
    visible: true,
    color: '#ff00aa',
    width: 8,
    opacity: 1.0,
    lineStyle: 'solid',
    segmentLength: 0.40,
    animation: { type: 'trace_chaser', speed: 1.2, direction: 'cw' }
  };
  const chaserSvg = ContourEngine.buildSvgElements(chaserPoly, 0.3);
  assert.ok(chaserSvg.includes('<path'), 'trace_chaser must render SVG path elements');
  assert.ok(!chaserSvg.includes('stroke-opacity="0.14"'), 'trace_chaser must have zero background silhouette');

  // 3. Styled trace_chaser (e.g. dotted) uses an arc mask
  chaserPoly.stroke.lineStyle = 'dotted';
  const dottedChaserSvg = ContourEngine.buildSvgElements(chaserPoly, 0.3);
  assert.ok(dottedChaserSvg.includes('<mask id="cchaser-'), 'Styled chaser must use moving arc mask');

  // 4. gradient_march renders dynamic rotating linearGradient
  const gradPoly = PolygonObject.createDefaultQuad();
  gradPoly.stroke = {
    visible: true,
    color: '#00f0ff',
    width: 6,
    opacity: 1.0,
    lineStyle: 'solid',
    animation: { type: 'gradient_march', speed: 1.0, direction: 'cw' }
  };
  const gradSvg = ContourEngine.buildSvgElements(gradPoly, 0.25);
  assert.ok(gradSvg.includes('<linearGradient id="cmarchgrad-'), 'gradient_march must define rotating linearGradient');
  assert.ok(gradSvg.includes('gradientTransform="rotate('), 'gradient_march must rotate gradient with phase');

  // 5. Physical pulse thickness modulation
  const pulsePoly = PolygonObject.createDefaultQuad();
  pulsePoly.stroke = {
    visible: true,
    color: '#00ff88',
    width: 10,
    opacity: 1.0,
    lineStyle: 'solid',
    animation: { type: 'pulse', speed: 1.0, direction: 'cw' }
  };
  const pulse0 = ContourEngine.evaluateAtTime(pulsePoly, 0.0);
  const pulsePeak = ContourEngine.evaluateAtTime(pulsePoly, 0.5);
  assert.ok(pulse0.width > pulsePeak.width, `Pulse width at wave=1.0 (${pulse0.width}) must exceed wave=0.0 (${pulsePeak.width})`);
  assert.ok(pulse0.opacity > pulsePeak.opacity, `Pulse opacity at wave=1.0 (${pulse0.opacity}) must exceed wave=0.0 (${pulsePeak.opacity})`);
});

test('v2.9.2: deep_pulse blackout, pulse_wave traveling notch, edge_glitch normal displacement, and lineStyle respect', async () => {
  const { ContourEngine, PolygonObject } = await loadModules();

  const makeQuad = (id, strokePatch) => new PolygonObject({
    id,
    type: 'quad',
    points: [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
    color: '#ff00aa',
    fillEnabled: false,
    stroke: {
      visible: true,
      color: '#00f0ff',
      width: 8,
      opacity: 1.0,
      lineStyle: 'solid',
      dashScale: 1.0,
      segmentLength: 0.35,
      glow: true,
      animation: { type: 'none', speed: 1.0, direction: 'cw' },
      ...strokePatch
    }
  });

  // 1. pulse (pulseStyle: 'deep'): reaches 0.00 opacity at trough (total blackout in projector)
  const deepPoly = makeQuad('poly_deep', {
    animation: { type: 'pulse', pulseStyle: 'deep', speed: 1.0, direction: 'cw' }
  });
  // With BASE_FREQS.pulse = 0.6, passing loopDuration = 2.0 makes cycles = round(2.0 * 0.6) = 1, freq = 0.5.
  // At t = 1.0, phase = 0.5, cos(pi) = -1, wave = 0, opacity = 0.00.
  const statePeak = ContourEngine.evaluateAtTime(deepPoly, 0.0, 1920, 1080, 2.0);
  const stateTrough = ContourEngine.evaluateAtTime(deepPoly, 1.0, 1920, 1080, 2.0);
  assert.ok(statePeak.opacity > 0.9, `Peak opacity should be near 1.0, got ${statePeak.opacity}`);
  assert.ok(stateTrough.opacity < 0.001, `Trough opacity must reach 0.00 for total blackout, got ${stateTrough.opacity}`);

  // 1b. trace_chaser without white head dots (only single masked path in <g>, no radiant white head arc)
  const chaserPoly = makeQuad('poly_chaser', {
    animation: { type: 'trace_chaser', speed: 1.0, direction: 'cw' }
  });
  const chaserSvg = ContourEngine.buildSvgElements(chaserPoly, 0.2);
  const innerGroup = chaserSvg.match(/<g>([\s\S]*?)<\/g>/)?.[1] || '';
  assert.ok(!innerGroup.includes('#ffffff'), 'Trace chaser inner rendered stroke must NOT contain white head core dot');

  // 2. march: does NOT allow lineStyle solid or double (auto-switches to dashed)
  const marchPoly = makeQuad('poly_march', {
    lineStyle: 'solid',
    animation: { type: 'none' }
  });
  ContourEngine.applyInspectorUpdate(marchPoly, { animationType: 'march' });
  assert.equal(marchPoly.stroke.lineStyle, 'dashed', 'Switching to march must auto-switch solid to dashed');
  ContourEngine.applyInspectorUpdate(marchPoly, { lineStyle: 'solid' });
  assert.equal(marchPoly.stroke.lineStyle, 'dashed', 'Applying solid while in march must be rejected');

  // 3. laser_comet with dashed lineStyle: SVG mask must contain smooth slices with butt linecaps and path with stroke-dasharray
  const dashedComet = makeQuad('poly_dash_comet', {
    lineStyle: 'dashed',
    animation: { type: 'laser_comet', speed: 1.0, direction: 'cw' }
  });
  const cometSvg = ContourEngine.buildSvgElements(dashedComet, 0.5);
  assert.match(cometSvg, /<mask id="ccomet-poly_dash_comet"/, 'Must use gradient comet mask');
  assert.match(cometSvg, /stroke-linecap="butt"/, 'Intermediate mask slices must use butt linecap to avoid beaded pills');
  assert.match(cometSvg, /stroke-dasharray=/, 'Must preserve and render dashed line style in comet body');

  // 4. dual_chase: clean vs comet styles
  const cleanDual = makeQuad('poly_dual_clean', {
    lineStyle: 'dotted',
    animation: { type: 'dual_chase', speed: 1.0, direction: 'cw', chaseStyle: 'clean' }
  });
  const cleanDualSvg = ContourEngine.buildSvgElements(cleanDual, 0.3);
  assert.match(cleanDualSvg, /<mask id="cchaser-poly_dual_clean"/, 'Clean dual chase must use twin arc reveal mask');
  assert.match(cleanDualSvg, /stroke-linecap="round"/, 'Dotted style in dual chase must use round linecap');

  // 5. pulse_wave: creates traveling dimming notch mask
  const wavePoly = makeQuad('poly_wave', {
    animation: { type: 'pulse_wave', speed: 1.0, direction: 'cw' }
  });
  const waveSvg = ContourEngine.buildSvgElements(wavePoly, 0.2);
  assert.match(waveSvg, /<mask id="cpulsewave-poly_wave"/, 'Pulse wave must generate luminance dip mask');

  // 6. edge_glitch: uses real edge normal displacement modulated by speed
  const glitchPoly = makeQuad('poly_glitch', {
    lineStyle: 'dash_dot',
    animation: { type: 'edge_glitch', speed: 1.0, direction: 'cw' }
  });
  // Test both stable frame and active burst frame
  const glitchSvgActive = ContourEngine.buildSvgElements(glitchPoly, 0.05);
  assert.match(glitchSvgActive, /<path d="M/, 'Glitch SVG must produce path segments');

  const glitchPolySlow = makeQuad('poly_glitch_slow', {
    lineStyle: 'dash_dot',
    animation: { type: 'edge_glitch', speed: 0.5, direction: 'cw' }
  });
  const glitchPolyFast = makeQuad('poly_glitch_fast', {
    lineStyle: 'dash_dot',
    animation: { type: 'edge_glitch', speed: 3.0, direction: 'cw' }
  });
  const slowState = ContourEngine.evaluateAtTime(glitchPolySlow, 0.15);
  const fastState = ContourEngine.evaluateAtTime(glitchPolyFast, 0.15);
  assert.notEqual(slowState.glitchSeed, fastState.glitchSeed, 'Speed must modulate glitch seed frequency');
});

test('PolygonObject.scaleBy scales quads, curved bezier polygons, and circles uniformly from centroid', async () => {
  const { PolygonObject } = await loadModules();

  // 1. Quad scaling
  const quad = new PolygonObject({
    type: 'quad',
    points: [[0.3, 0.3], [0.7, 0.3], [0.7, 0.7], [0.3, 0.7]]
  });
  const [c0x, c0y] = quad.getCentroid();
  assert.ok(Math.abs(c0x - 0.5) < 1e-4 && Math.abs(c0y - 0.5) < 1e-4);

  // Scale up by 1.2 (+20%)
  quad.scaleBy(1.2);
  const [c1x, c1y] = quad.getCentroid();
  assert.ok(Math.abs(c1x - 0.5) < 1e-4 && Math.abs(c1y - 0.5) < 1e-4, 'Centroid must remain invariant');
  assert.ok(Math.abs((quad.points[1][0] - quad.points[0][0]) - 0.48) < 1e-4, 'Width must scale by factor');

  // Scale down by 0.5 (-50%)
  quad.scaleBy(0.5);
  assert.ok(Math.abs((quad.points[1][0] - quad.points[0][0]) - 0.24) < 1e-4, 'Width must shrink by factor');

  // 2. Circle scaling
  const circle = new PolygonObject({
    type: 'circle',
    center: [0.4, 0.6],
    radiusX: 0.1,
    radiusY: 0.1
  });
  circle.scaleBy(1.5);
  assert.ok(Math.abs(circle.radiusX - 0.15) < 1e-4);
  assert.ok(Math.abs(circle.radiusY - 0.15) < 1e-4);
  assert.deepEqual(circle.center, [0.4, 0.6]);

  // 3. Curved polygon scaling
  const curvedQuad = new PolygonObject({
    type: 'quad',
    points: [[0.2, 0.2], [0.8, 0.2], [0.8, 0.8], [0.2, 0.8]],
    edgeCurves: {
      '0-1': { c1: [0.4, 0.1], c2: [0.6, 0.1] }
    }
  });
  curvedQuad.scaleBy(1.1);
  assert.equal(curvedQuad.points.length, 4);
  assert.ok(curvedQuad.edgeCurves['0-1'].c1[0] !== 0.4, 'Curve control points must be scaled');
});

