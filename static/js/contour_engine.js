/**
 * contour_engine.js - Deterministic geometric discretization, dash quantization,
 * and temporal contour evaluation shared by SVG live preview and offline video export.
 */

class ContourEngine {
  static _finiteOr(value, fallback) {
    if (value === undefined || value === null || value === '' || typeof value === 'boolean') {
      return fallback;
    }
    const num = Number(value);
    return Number.isFinite(num) ? num : fallback;
  }

  static _evalCubicBezier(p0, c1, c2, p1, t) {
    const mt = 1 - t;
    const mt2 = mt * mt;
    const mt3 = mt2 * mt;
    const t2 = t * t;
    const t3 = t2 * t;
    return [
      mt3 * p0[0] + 3 * mt2 * t * c1[0] + 3 * mt * t2 * c2[0] + t3 * p1[0],
      mt3 * p0[1] + 3 * mt2 * t * c1[1] + 3 * mt * t2 * c2[1] + t3 * p1[1]
    ];
  }

  static normalizeStroke(raw, fallbackColor = '#00f0ff') {
    const hexRe = /^#[0-9a-fA-F]{6}$/;
    const resolvedFallback = (typeof fallbackColor === 'string' && hexRe.test(fallbackColor))
      ? fallbackColor.toLowerCase()
      : '#00f0ff';
    const color = (typeof raw?.color === 'string' && hexRe.test(raw.color))
      ? raw.color.toLowerCase()
      : resolvedFallback;

    const width = Math.max(1, Math.min(32, ContourEngine._finiteOr(raw?.width, 4)));
    const opacity = Math.max(0.05, Math.min(1.0, ContourEngine._finiteOr(raw?.opacity, 1.0)));
    const lineStyle = ContourEngine.LINE_STYLES.includes(raw?.lineStyle) ? raw.lineStyle : 'solid';
    const dashScale = Math.max(0.25, Math.min(4.0, ContourEngine._finiteOr(raw?.dashScale, 1.0)));
    const rawType = raw?.animation?.type;
    const mappedType = rawType === 'deep_pulse' ? 'pulse' : rawType;
    const animType = ContourEngine.ANIMATION_TYPES.includes(mappedType) ? mappedType : 'none';
    const animSpeed = Math.max(0.1, Math.min(5.0, ContourEngine._finiteOr(raw?.animation?.speed, 1.0)));
    const animDir = raw?.animation?.direction === 'ccw' ? 'ccw' : 'cw';
    const isSegmented = (animType === 'trace_chaser' || animType === 'laser_comet' || animType === 'dual_chase' || animType === 'pulse_wave');
    const segmentLength = Math.max(0.05, Math.min(1.0, ContourEngine._finiteOr(raw?.segmentLength, isSegmented ? 0.35 : 1.0)));
    const pulseStyle = (raw?.animation?.pulseStyle === 'deep' || rawType === 'deep_pulse') ? 'deep' : 'rhythmic';

    // Para marcha continua, si el estilo es continuo (solid o double), forzamos discontinuo
    const effectiveLineStyle = (animType === 'march' && (lineStyle === 'solid' || lineStyle === 'double'))
      ? 'dashed'
      : lineStyle;

    return {
      visible: Boolean(raw?.visible ?? false),
      color,
      width,
      opacity,
      lineStyle: effectiveLineStyle,
      dashScale,
      segmentLength,
      glow: Boolean(raw?.glow ?? false),
      animation: {
        type: animType,
        speed: animSpeed,
        direction: animDir,
        ...(animType === 'dual_chase' || raw?.animation?.chaseStyle ? { chaseStyle: raw?.animation?.chaseStyle === 'clean' ? 'clean' : 'comet' } : {}),
        ...(animType === 'pulse' || raw?.animation?.pulseStyle ? { pulseStyle } : {})
      }
    };
  }

  static getClosedPolylinePx(poly, width = 1920, height = 1080) {
    if (!poly) {
      return { points: [], cumulative: new Float64Array(0), totalLength: 0, svgPathD: '' };
    }

    const isCircular = poly.type === 'circle' || poly.type === 'circle_occluder';
    const normPts = [];

    if (isCircular) {
      const cx = Array.isArray(poly.center) ? Number(poly.center[0]) : 0.5;
      const cy = Array.isArray(poly.center) ? Number(poly.center[1]) : 0.5;
      const rx = poly.radiusX !== undefined ? Number(poly.radiusX) : 0.18;
      const ry = poly.radiusY !== undefined ? Number(poly.radiusY) : 0.18;
      const rotationDeg = poly.rotation !== undefined ? Number(poly.rotation) : 0;
      if (rx <= 0 || ry <= 0) {
        return { points: [], cumulative: new Float64Array(0), totalLength: 0, svgPathD: '' };
      }

      const samples = 96;
      const rad = (rotationDeg * Math.PI) / 180;
      const cosR = Math.cos(rad);
      const sinR = Math.sin(rad);
      for (let i = 0; i < samples; i++) {
        const theta = (i / samples) * 2 * Math.PI;
        const ex = rx * Math.cos(theta);
        const ey = ry * Math.sin(theta);
        normPts.push([
          Math.max(0, Math.min(1, cx + (ex * cosR - ey * sinR))),
          Math.max(0, Math.min(1, cy + (ex * sinR + ey * cosR)))
        ]);
      }
    } else {
      const rawPoints = Array.isArray(poly.points) ? poly.points : [];
      const n = rawPoints.length;
      if (n < 3) {
        return { points: [], cumulative: new Float64Array(0), totalLength: 0, svgPathD: '' };
      }

      const edgeCurves = poly.edgeCurves || {};
      const samplesPerEdge = 24;
      for (let i = 0; i < n; i++) {
        const nextIdx = (i + 1) % n;
        const ptA = [Number(rawPoints[i][0]), Number(rawPoints[i][1])];
        const ptB = [Number(rawPoints[nextIdx][0]), Number(rawPoints[nextIdx][1])];
        const curve = edgeCurves[`${i}-${nextIdx}`] || edgeCurves[`${i}`];

        if (curve && Array.isArray(curve.c1) && Array.isArray(curve.c2)) {
          const c1 = [Number(curve.c1[0]), Number(curve.c1[1])];
          const c2 = [Number(curve.c2[0]), Number(curve.c2[1])];
          for (let s = 0; s < samplesPerEdge; s++) {
            normPts.push(ContourEngine._evalCubicBezier(ptA, c1, c2, ptB, s / samplesPerEdge));
          }
        } else {
          normPts.push(ptA);
        }
      }
    }

    if (normPts.length < 3) {
      return { points: [], cumulative: new Float64Array(0), totalLength: 0, svgPathD: '' };
    }

    const points = normPts.map(([nx, ny]) => [nx * width, ny * height]);
    // Sampled polygonal path keeps SVG path arc-length within <0.1% of Float64Array totalLength.
    const pathParts = [`M ${points[0][0].toFixed(2)},${points[0][1].toFixed(2)}`];
    for (let i = 1; i < points.length; i++) {
      pathParts.push(`L ${points[i][0].toFixed(2)},${points[i][1].toFixed(2)}`);
    }
    pathParts.push('Z');
    const svgPathD = pathParts.join(' ');

    points.push([points[0][0], points[0][1]]);

    const cumulative = new Float64Array(points.length);
    cumulative[0] = 0;
    for (let i = 1; i < points.length; i++) {
      const dx = points[i][0] - points[i - 1][0];
      const dy = points[i][1] - points[i - 1][1];
      cumulative[i] = cumulative[i - 1] + Math.hypot(dx, dy);
    }
    const totalLength = cumulative[cumulative.length - 1];

    return { points, cumulative, totalLength, svgPathD };
  }

  static computeDashPattern(lineStyle, strokeWidth, dashScale = 1.0, totalLength = 0) {
    if (lineStyle === 'solid' || lineStyle === 'double') {
      return [];
    }
    const multipliers = ContourEngine.DASH_MULTIPLIERS[lineStyle];
    if (!multipliers) {
      return [];
    }

    const sw = Math.max(1, ContourEngine._finiteOr(strokeWidth, 4));
    const ds = Math.max(0.25, ContourEngine._finiteOr(dashScale, 1.0));
    const basePattern = multipliers.map(m => m * sw * ds);
    const rawPeriod = basePattern.reduce((a, b) => a + b, 0);

    // Quantize period to an integer divisor of totalLength so vertex 0 never clips a partial dash.
    if (totalLength > 1e-3 && rawPeriod > 1e-3) {
      const repeats = Math.max(1, Math.round(totalLength / rawPeriod));
      const targetPeriod = totalLength / repeats;
      const scale = targetPeriod / rawPeriod;
      return basePattern.map(v => v * scale);
    }

    return basePattern;
  }

  static computePhase(animType, speed, direction, tSec, loopDuration = null) {
    if (!animType || animType === 'none') {
      return 0;
    }

    const baseFreq = ContourEngine.BASE_FREQS[animType] || 0.5;
    const spd = Math.max(0.1, ContourEngine._finiteOr(speed, 1.0));
    const t = ContourEngine._finiteOr(tSec, 0);

    let u;
    if (loopDuration !== null && loopDuration !== undefined && Number(loopDuration) > 0) {
      const dur = Number(loopDuration);
      const cycles = Math.max(1, Math.round(dur * baseFreq * spd));
      u = (t / dur) * cycles;
    } else {
      u = t * baseFreq * spd;
    }

    const rawProgress = ((u % 1) + 1) % 1;
    const phase = direction === 'ccw' ? (1 - rawProgress) % 1 : rawProgress;
    return phase || 0;
  }

  static hslShiftHex(baseHex, hueOffsetDeg) {
    const hex = (typeof baseHex === 'string' && /^#[0-9a-fA-F]{6}$/.test(baseHex))
      ? baseHex.toLowerCase()
      : '#00f0ff';

    const r = parseInt(hex.slice(1, 3), 16) / 255;
    const g = parseInt(hex.slice(3, 5), 16) / 255;
    const b = parseInt(hex.slice(5, 7), 16) / 255;

    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const d = max - min;
    let h = 0;
    let s = 0;
    let l = (max + min) / 2;

    if (d > 1e-6) {
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      if (max === r) {
        h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
      } else if (max === g) {
        h = ((b - r) / d + 2) * 60;
      } else {
        h = ((r - g) / d + 4) * 60;
      }
    }

    // Force chroma on near-achromatic white/black/grey strokes so color_flow remains visible.
    if (s < 0.15 || l < 0.10 || l > 0.90) {
      s = Math.max(0.75, s);
      l = Math.max(0.45, Math.min(0.65, l));
    }

    const offset = ContourEngine._finiteOr(hueOffsetDeg, 0);
    const hShift = (((h + offset) % 360) + 360) % 360;

    const c = (1 - Math.abs(2 * l - 1)) * s;
    const x = c * (1 - Math.abs(((hShift / 60) % 2) - 1));
    const m = l - c / 2;

    let r1 = 0;
    let g1 = 0;
    let b1 = 0;
    if (hShift < 60) {
      r1 = c; g1 = x; b1 = 0;
    } else if (hShift < 120) {
      r1 = x; g1 = c; b1 = 0;
    } else if (hShift < 180) {
      r1 = 0; g1 = c; b1 = x;
    } else if (hShift < 240) {
      r1 = 0; g1 = x; b1 = c;
    } else if (hShift < 300) {
      r1 = x; g1 = 0; b1 = c;
    } else {
      r1 = c; g1 = 0; b1 = x;
    }

    const toByteHex = (v) => Math.round(Math.max(0, Math.min(255, (v + m) * 255)))
      .toString(16)
      .padStart(2, '0');
    return `#${toByteHex(r1)}${toByteHex(g1)}${toByteHex(b1)}`;
  }

  static evaluateAtTime(poly, tSec = 0, width = 1920, height = 1080, loopDuration = null) {
    const stroke = ContourEngine.normalizeStroke(poly?.stroke, poly?.color);
    const { points, cumulative, totalLength, svgPathD } = ContourEngine.getClosedPolylinePx(poly, width, height);
    const resScale = width / 1920;
    const scaledWidth = stroke.width * resScale;
    const pattern = ContourEngine.computeDashPattern(stroke.lineStyle, scaledWidth, stroke.dashScale, totalLength);
    const patternPeriod = pattern.reduce((a, b) => a + b, 0);
    const phase = ContourEngine.computePhase(
      stroke.animation.type,
      stroke.animation.speed,
      stroke.animation.direction,
      tSec,
      loopDuration
    );

    let effectiveWidth = scaledWidth;
    let effectiveColor = stroke.color;
    let effectiveOpacity = stroke.opacity;
    let dashArray = pattern;
    let dashOffset = 0;
    let cometHeads = [];
    let cometLength = 0;
    let tailOpacity = 0;
    let wipeStartS = 0;
    let wipeLength = 0;
    let chaserHeadS = 0;
    let chaserHeads = [];
    let chaserLength = 0;
    let glitchActive = false;
    let glitchFlash = false;
    let glitchSeed = 0;
    let glitchIntensity = 0;
    let gradientPhase = 0;
    let waveCenterS = 0;
    let waveWidthS = 0;
    const chaseStyle = stroke.animation?.chaseStyle === 'comet' ? 'comet' : 'clean';

    switch (stroke.animation.type) {
      case 'march': {
        const effectivePattern = pattern.length > 0
          ? pattern
          : ContourEngine.computeDashPattern('dashed', scaledWidth, stroke.dashScale, totalLength);
        const period = effectivePattern.reduce((a, b) => a + b, 0);
        dashArray = effectivePattern;
        dashOffset = (-phase * period) || 0;
        break;
      }
      case 'pulse': {
        const isDeep = stroke.animation?.pulseStyle === 'deep';
        const wave = 0.5 + 0.5 * Math.cos(2 * Math.PI * phase);
        if (isDeep) {
          // Pulso Profundo: se desvanece por completo a 0.00 (blackout total)
          effectiveOpacity = stroke.opacity * Math.pow(wave, 1.8);
          effectiveWidth = scaledWidth * (0.25 + 1.15 * wave);
        } else {
          // Pulso Rítmico Suave: respiración continua
          effectiveOpacity = stroke.opacity * (0.30 + 0.70 * wave);
          effectiveWidth = scaledWidth * (0.55 + 0.85 * wave);
        }
        break;
      }
      case 'pulse_wave': {
        // Onda Perimetral: viaja alrededor del contorno modulando la intensidad en una ventana móvil
        waveCenterS = (phase * totalLength) || 0;
        waveWidthS = totalLength * (stroke.segmentLength ?? 0.35);
        break;
      }
      case 'trace_chaser': {
        chaserLength = totalLength * (stroke.segmentLength ?? 0.35);
        chaserHeadS = (phase * totalLength) || 0;
        chaserHeads = [chaserHeadS];
        break;
      }
      case 'laser_comet': {
        cometHeads = [(phase * totalLength) || 0];
        cometLength = totalLength * (stroke.segmentLength ?? 0.35);
        tailOpacity = 0.0;
        break;
      }
      case 'dual_chase': {
        const headS1 = (phase * totalLength) || 0;
        const headS2 = (((phase + 0.5) % 1) * totalLength) || 0;
        const segLen = totalLength * ((stroke.segmentLength ?? 0.35) * 0.75);
        chaserHeadS = headS1;
        if (chaseStyle === 'clean') {
          // Trazo definido dual (2 gusanos simétricos opuestos a 180°)
          chaserHeads = [headS1, headS2];
          chaserLength = segLen;
        } else {
          // Cometa láser dual (por defecto)
          cometHeads = [headS1, headS2];
          cometLength = segLen;
          tailOpacity = 0.0;
        }
        break;
      }
      case 'draw_wipe': {
        if (phase < 0.5) {
          wipeStartS = 0;
          wipeLength = (phase * 2 * totalLength) || 0;
        } else {
          wipeStartS = (((phase - 0.5) * 2) * totalLength) || 0;
          wipeLength = (((1 - phase) * 2) * totalLength) || 0;
        }
        break;
      }
      case 'edge_glitch': {
        // Disrupción digital determinista de alta frecuencia (22 Hz) modulada por velocidad
        const spd = Math.max(0.1, ContourEngine._finiteOr(stroke.animation.speed, 1.0));
        let stepSeed;
        if (loopDuration !== null && loopDuration !== undefined && Number(loopDuration) > 0) {
          const dur = Number(loopDuration);
          const totalGlitchSteps = Math.max(1, Math.round(dur * 22 * spd));
          stepSeed = Math.floor((tSec / dur) * totalGlitchSteps);
        } else {
          stepSeed = Math.floor(tSec * 22 * spd);
        }
        const prng = (s) => (Math.abs(Math.sin(s * 12.9898 + 78.233) * 43758.5453) % 1.0);
        const burst = prng(stepSeed);
        glitchActive = burst > 0.55;
        glitchFlash = burst > 0.90;
        glitchSeed = stepSeed;
        glitchIntensity = glitchActive ? ((burst - 0.55) / 0.45) : 0;
        if (glitchFlash) {
          effectiveColor = ContourEngine._tintTowardsWhite(stroke.color, 0.70);
          effectiveWidth = scaledWidth * 1.35;
          effectiveOpacity = Math.min(1.0, stroke.opacity * 1.4);
        } else if (glitchActive) {
          effectiveWidth = scaledWidth * (0.85 + 0.30 * prng(stepSeed * 3));
        }
        break;
      }
      case 'gradient_march': {
        gradientPhase = phase;
        dashOffset = patternPeriod > 0 ? ((-phase * patternPeriod) || 0) : 0;
        break;
      }
      case 'color_flow': {
        effectiveColor = ContourEngine.hslShiftHex(stroke.color, phase * 360);
        dashOffset = patternPeriod > 0 ? ((-phase * patternPeriod) || 0) : 0;
        break;
      }
      case 'none':
      default:
        break;
    }

    return {
      visible: stroke.visible,
      lineStyle: stroke.lineStyle,
      dashScale: stroke.dashScale,
      segmentLength: stroke.segmentLength,
      glow: stroke.glow,
      animationType: stroke.animation.type,
      animationSpeed: stroke.animation.speed,
      direction: stroke.animation.direction,
      chaseStyle,
      phase,
      totalLength,
      points,
      cumulative,
      svgPathD,
      width: effectiveWidth,
      baseWidth: scaledWidth,
      color: effectiveColor,
      opacity: effectiveOpacity,
      dashArray,
      dashOffset,
      cometHeads,
      cometLength,
      tailOpacity,
      wipeStartS,
      wipeLength,
      chaserHeadS,
      chaserHeads,
      chaserLength,
      glitchActive,
      glitchFlash,
      glitchSeed,
      glitchIntensity,
      waveCenterS,
      waveWidthS,
      gradientPhase
    };
  }

  static _tintTowardsWhite(hexColor, ratio) {
    const hex = (typeof hexColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(hexColor))
      ? hexColor
      : '#00f0ff';
    const t = Math.max(0, Math.min(1, ratio));
    const r = Math.round(parseInt(hex.slice(1, 3), 16) * (1 - t) + 255 * t);
    const g = Math.round(parseInt(hex.slice(3, 5), 16) * (1 - t) + 255 * t);
    const b = Math.round(parseInt(hex.slice(5, 7), 16) * (1 - t) + 255 * t);
    return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
  }

  static _buildArcPaths(svgPathD, startS, arcLen, totalLength, strokeColor, strokeWidth, strokeOpacity, linecap = 'round') {
    if (!(arcLen > 0.01) || !(totalLength > 0) || !svgPathD) {
      return '';
    }

    const s0 = ((startS % totalLength) + totalLength) % totalLength;
    const clampedLen = Math.min(arcLen, totalLength);
    const gap = totalLength + 16;
    const baseAttrs =
      `d="${svgPathD}" fill="none" stroke="${strokeColor}" ` +
      `stroke-width="${strokeWidth.toFixed(2)}" stroke-opacity="${strokeOpacity.toFixed(3)}" ` +
      `stroke-linejoin="round" stroke-linecap="${linecap}"`;

    if (s0 + clampedLen <= totalLength + 1e-6) {
      return `<path ${baseAttrs} stroke-dasharray="${clampedLen.toFixed(2)} ${gap.toFixed(2)}" stroke-dashoffset="${(-s0).toFixed(2)}" />`;
    }

    if (clampedLen < totalLength * 0.95) {
      const wrapGap = Math.max(0.01, totalLength - clampedLen);
      return `<path ${baseAttrs} stroke-dasharray="${clampedLen.toFixed(2)} ${wrapGap.toFixed(2)}" stroke-dashoffset="${(-s0).toFixed(2)}" />`;
    }

    const len1 = Math.max(0.01, totalLength - s0);
    const len2 = Math.max(0.01, (s0 + clampedLen) - totalLength);
    return (
      `<path ${baseAttrs} stroke-dasharray="${len1.toFixed(2)} ${gap.toFixed(2)}" stroke-dashoffset="${(-s0).toFixed(2)}" />` +
      `<path ${baseAttrs} stroke-dasharray="${len2.toFixed(2)} ${gap.toFixed(2)}" stroke-dashoffset="0.00" />`
    );
  }

  static buildSvgElements(poly, tSec = 0, opts = {}) {
    if (!poly || poly.visible === false || poly.type === 'occluder' || poly.type === 'circle_occluder') {
      return '';
    }
    if (!poly.stroke?.visible) {
      return '';
    }

    const width = opts.width || 1920;
    const height = opts.height || 1080;
    const loopDuration = opts.loopDuration ?? null;
    const state = ContourEngine.evaluateAtTime(poly, tSec, width, height, loopDuration);
    if (!state.visible || !(state.totalLength > 0) || !state.svgPathD) {
      return '';
    }

    const safeId = String(poly.id || 'p').replace(/[^a-zA-Z0-9_-]/g, '_');
    const pad = Math.ceil(Math.max(64, state.baseWidth * 6));
    const defs = [];

    if (state.glow) {
      const stdDev = Math.max(2.5, state.baseWidth * 1.1).toFixed(2);
      defs.push(
        `<filter id="cglow-${safeId}" x="-25%" y="-25%" width="150%" height="150%">` +
        `<feGaussianBlur stdDeviation="${stdDev}" result="blur"/>` +
        `<feMerge><feMergeNode in="blur"/><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>` +
        `</filter>`
      );
    }

    if (state.lineStyle === 'double') {
      const outerW = (state.width * 2.4).toFixed(2);
      const innerCutW = Math.max(1, state.width * 0.85).toFixed(2);
      defs.push(
        `<mask id="cdouble-${safeId}" maskUnits="userSpaceOnUse" x="${-pad}" y="${-pad}" width="${width + pad * 2}" height="${height + pad * 2}">` +
        `<path d="${state.svgPathD}" fill="none" stroke="#ffffff" stroke-width="${outerW}" stroke-linejoin="round" stroke-linecap="round" />` +
        `<path d="${state.svgPathD}" fill="none" stroke="#000000" stroke-width="${innerCutW}" stroke-linejoin="round" stroke-linecap="round" />` +
        `</mask>`
      );
    }

    if (state.animationType === 'draw_wipe') {
      const wipeStrokeW = Math.max(state.baseWidth * 4, state.width * 3.2);
      const wipeArcs = ContourEngine._buildArcPaths(
        state.svgPathD,
        state.wipeStartS,
        state.wipeLength,
        state.totalLength,
        '#ffffff',
        wipeStrokeW,
        1.0,
        'butt'
      );
      defs.push(
        `<mask id="cwipe-${safeId}" maskUnits="userSpaceOnUse" x="${-pad}" y="${-pad}" width="${width + pad * 2}" height="${height + pad * 2}">` +
        wipeArcs +
        `</mask>`
      );
    }

    let innerMarkup = '';
    const doubleMult = state.lineStyle === 'double' ? 2.2 : 1.0;
    const baseAttrs = [
      `d="${state.svgPathD}"`,
      'fill="none"',
      `stroke="${state.color}"`,
      `stroke-width="${(state.width * doubleMult).toFixed(2)}"`,
      `stroke-opacity="${state.opacity.toFixed(3)}"`,
      'stroke-linejoin="round"',
      `stroke-linecap="${state.lineStyle === 'dotted' ? 'round' : 'butt'}"`
    ];
    if (state.dashArray.length > 0) {
      baseAttrs.push(`stroke-dasharray="${state.dashArray.map(v => v.toFixed(3)).join(' ')}"`);
      baseAttrs.push(`stroke-dashoffset="${state.dashOffset.toFixed(3)}"`);
    }

    if (state.animationType === 'laser_comet' || (state.animationType === 'dual_chase' && state.chaseStyle === 'comet')) {
      // COMETA LÁSER SUAVE CON SOPORTE COMPLETO DE ESTILOS DE LÍNEA Y CERO CUENTAS FALSAS
      const cometMaskSlices = [];
      const numSlices = 18;
      const stepLen = state.cometLength / numSlices;
      const sliceLen = stepLen * 1.08;
      const maskW = Math.max(state.baseWidth * 4, state.width * 3.4);

      for (const headS of state.cometHeads) {
        for (let k = 1; k <= numSlices; k++) {
          const norm = k / numSlices;
          const sliceOp = Math.pow(norm, 1.8);
          const sPos = state.direction === 'ccw'
            ? headS + (numSlices - k) * stepLen
            : headS - (numSlices - k + 1) * stepLen;
          const cap = k === numSlices ? 'round' : 'butt';
          cometMaskSlices.push(
            ContourEngine._buildArcPaths(
              state.svgPathD,
              sPos,
              sliceLen,
              state.totalLength,
              '#ffffff',
              maskW,
              sliceOp,
              cap
            )
          );
        }
      }

      defs.push(
        `<mask id="ccomet-${safeId}" maskUnits="userSpaceOnUse" x="${-pad}" y="${-pad}" width="${width + pad * 2}" height="${height + pad * 2}">` +
        cometMaskSlices.join('') +
        `</mask>`
      );

      const cometPath = `<path ${baseAttrs.join(' ')} mask="url(#ccomet-${safeId})" />`;
      // Núcleo radiante en cada cabeza
      const headCores = state.cometHeads.map(hS => {
        const coreLen = Math.max(4, stepLen * 0.7);
        const coreStart = state.direction === 'ccw' ? hS : hS - coreLen;
        return ContourEngine._buildArcPaths(
          state.svgPathD,
          coreStart,
          coreLen,
          state.totalLength,
          ContourEngine._tintTowardsWhite(state.color, 0.88),
          0.65 * state.width * doubleMult,
          Math.min(1.0, state.opacity * 0.98),
          'round'
        );
      }).join('');

      innerMarkup = cometPath + headCores;

    } else if (state.animationType === 'trace_chaser' || (state.animationType === 'dual_chase' && state.chaseStyle !== 'comet')) {
      // SEGMENTOS VIAJEROS DEFINIDOS (EL GUSANO Y DOBLE PERSECUCIÓN EN MODO FIJO - SIN PUNTOS BLANCOS)
      const segLen = Math.min(state.totalLength, Math.max(4, state.chaserLength));
      const maskW = Math.max(state.baseWidth * 4, state.width * 3.4);
      const chaserMaskArcs = [];

      for (const headS of state.chaserHeads) {
        const startS = state.direction === 'ccw' ? headS : headS - segLen;
        chaserMaskArcs.push(
          ContourEngine._buildArcPaths(
            state.svgPathD,
            startS,
            segLen,
            state.totalLength,
            '#ffffff',
            maskW,
            1.0,
            'round'
          )
        );
      }

      defs.push(
        `<mask id="cchaser-${safeId}" maskUnits="userSpaceOnUse" x="${-pad}" y="${-pad}" width="${width + pad * 2}" height="${height + pad * 2}">` +
        chaserMaskArcs.join('') +
        `</mask>`
      );

      innerMarkup = `<path ${baseAttrs.join(' ')} mask="url(#cchaser-${safeId})" />`;

    } else if (state.animationType === 'pulse_wave') {
      // ONDA PERIMETRAL: EL CONTORNO ESTÁ A PLENO COLOR Y UNA ONDA MÓVIL LO DESVANECE SUAVEMENTE
      const maskW = Math.max(state.baseWidth * 4, state.width * 3.4);
      const waveLen = Math.min(state.totalLength * 0.9, Math.max(8, state.waveWidthS));
      const halfWave = waveLen / 2;
      const numSlices = 18;
      const stepLen = waveLen / numSlices;
      const sliceLen = stepLen * 1.08;

      // Base blanca: 100% visible en todo el perímetro físico
      const baseFullMask = `<path d="${state.svgPathD}" fill="none" stroke="#ffffff" stroke-width="${maskW}" stroke-linejoin="round" stroke-linecap="round" />`;

      // Slices negros superpuestos en la máscara que desvanecen suavemente la línea a 0% en el centro
      const waveFadeSlices = [];
      for (let k = 0; k < numSlices; k++) {
        const sMid = (k + 0.5) * stepLen;
        const distFromCenter = Math.abs(sMid - halfWave);
        const fadeNorm = (1 - Math.cos(Math.PI * distFromCenter / halfWave)) * 0.5;
        // Modulación suave: en el centro desciende al 45% de intensidad (blackOp 0.55) y en los extremos permanece al 100%
        const blackOp = (1.0 - fadeNorm) * 0.55;
        if (blackOp > 0.01) {
          const sPos = state.waveCenterS - halfWave + k * stepLen;
          waveFadeSlices.push(
            ContourEngine._buildArcPaths(
              state.svgPathD,
              sPos,
              sliceLen,
              state.totalLength,
              '#000000',
              maskW + 2,
              blackOp,
              'butt'
            )
          );
        }
      }

      defs.push(
        `<mask id="cpulsewave-${safeId}" maskUnits="userSpaceOnUse" x="${-pad}" y="${-pad}" width="${width + pad * 2}" height="${height + pad * 2}">` +
        baseFullMask +
        waveFadeSlices.join('') +
        `</mask>`
      );

      innerMarkup = `<path ${baseAttrs.join(' ')} mask="url(#cpulsewave-${safeId})" />`;

    } else if (state.animationType === 'gradient_march') {
      const rotDeg = ((state.gradientPhase * 360 * (state.direction === 'ccw' ? -1 : 1)) % 360).toFixed(1);
      defs.push(
        `<linearGradient id="cmarchgrad-${safeId}" x1="0%" y1="0%" x2="100%" y2="100%" gradientTransform="rotate(${rotDeg} 0.5 0.5)">` +
        `<stop offset="0%" stop-color="#00f0ff" />` +
        `<stop offset="25%" stop-color="#ff00aa" />` +
        `<stop offset="50%" stop-color="#ffbb00" />` +
        `<stop offset="75%" stop-color="#00ff66" />` +
        `<stop offset="100%" stop-color="#00f0ff" />` +
        `</linearGradient>`
      );
      const gradAttrs = [...baseAttrs];
      const strokeIdx = gradAttrs.findIndex(a => a.startsWith('stroke="'));
      if (strokeIdx !== -1) gradAttrs[strokeIdx] = `stroke="url(#cmarchgrad-${safeId})"`;
      innerMarkup = `<path ${gradAttrs.join(' ')} />`;

    } else if (state.animationType === 'edge_glitch') {
      innerMarkup = ContourEngine._buildGlitchSvg(poly, state, width, height, pad, safeId);

    } else {
      // none, march, pulse, deep_pulse, color_flow
      innerMarkup = `<path ${baseAttrs.join(' ')} />`;
    }

    if (state.lineStyle === 'double') {
      innerMarkup = `<g mask="url(#cdouble-${safeId})">${innerMarkup}</g>`;
    }
    if (state.animationType === 'draw_wipe') {
      innerMarkup = `<g mask="url(#cwipe-${safeId})">${innerMarkup}</g>`;
    }

    const defsMarkup = defs.length > 0 ? `<defs>${defs.join('')}</defs>` : '';
    const filterAttr = state.glow ? ` filter="url(#cglow-${safeId})"` : '';
    return `${defsMarkup}<g${filterAttr}>${innerMarkup}</g>`;
  }

  static _buildGlitchSvg(poly, state, width, height, pad, safeId) {
    const doubleMult = state.lineStyle === 'double' ? 2.2 : 1.0;
    const baseAttrs = [
      'fill="none"',
      `stroke="${state.color}"`,
      `stroke-width="${(state.width * doubleMult).toFixed(2)}"`,
      `stroke-opacity="${state.opacity.toFixed(3)}"`,
      'stroke-linejoin="round"',
      `stroke-linecap="${state.lineStyle === 'dotted' ? 'round' : 'butt'}"`
    ];
    if (state.dashArray.length > 0) {
      baseAttrs.push(`stroke-dasharray="${state.dashArray.map(v => v.toFixed(3)).join(' ')}"`);
      baseAttrs.push(`stroke-dashoffset="${state.dashOffset.toFixed(3)}"`);
    }

    if (!state.glitchActive) {
      return `<path d="${state.svgPathD}" ${baseAttrs.join(' ')} />`;
    }

    // Desplazamiento vectorial real sobre vértices de las aristas
    const pts = state.points;
    const n = pts.length;
    if (n < 3) {
      return `<path d="${state.svgPathD}" ${baseAttrs.join(' ')} />`;
    }

    const prng = (s) => (Math.abs(Math.sin(s * 12.9898 + 78.233) * 43758.5453) % 1.0);
    const stepSeed = state.glitchSeed;
    const displacedPaths = [];
    const ghostPaths = [];

    for (let i = 0; i < n; i++) {
      const p1 = pts[i];
      const p2 = pts[(i + 1) % n];
      const dx = p2[0] - p1[0];
      const dy = p2[1] - p1[1];
      const len = Math.hypot(dx, dy);
      if (len < 1e-3) continue;

      const nx = -dy / len;
      const ny = dx / len;
      const h = prng(stepSeed * 17 + i * 31);

      if (h < 0.40) {
        // Arista anclada sólida
        displacedPaths.push(`M ${p1[0].toFixed(1)} ${p1[1].toFixed(1)} L ${p2[0].toFixed(1)} ${p2[1].toFixed(1)}`);
      } else if (h < 0.78) {
        // Desplazamiento perpendicular de arista completa hacia afuera/adentro
        const dist = (4 + 14 * prng(stepSeed * 7 + i)) * (prng(stepSeed * 5 + i) > 0.4 ? 1 : -1) * state.glitchIntensity;
        const q1x = p1[0] + nx * dist;
        const q1y = p1[1] + ny * dist;
        const q2x = p2[0] + nx * dist;
        const q2y = p2[1] + ny * dist;
        displacedPaths.push(`M ${q1x.toFixed(1)} ${q1y.toFixed(1)} L ${q2x.toFixed(1)} ${q2y.toFixed(1)}`);
        if (state.glitchFlash) {
          ghostPaths.push(`M ${q1x.toFixed(1)} ${q1y.toFixed(1)} L ${q2x.toFixed(1)} ${q2y.toFixed(1)}`);
        }
      } else {
        // Fractura de arista en sub-tramos con salto escalonado
        const midRatio = 0.35 + 0.30 * prng(stepSeed * 9 + i);
        const mx = p1[0] + dx * midRatio;
        const my = p1[1] + dy * midRatio;
        const dist = (6 + 12 * prng(stepSeed * 11 + i)) * state.glitchIntensity;
        displacedPaths.push(`M ${p1[0].toFixed(1)} ${p1[1].toFixed(1)} L ${mx.toFixed(1)} ${my.toFixed(1)}`);
        displacedPaths.push(`M ${(mx + nx * dist).toFixed(1)} ${(my + ny * dist).toFixed(1)} L ${(p2[0] + nx * dist).toFixed(1)} ${(p2[1] + ny * dist).toFixed(1)}`);
      }
    }

    const dMain = displacedPaths.join(' ');
    let res = `<path d="${dMain}" ${baseAttrs.join(' ')} />`;
    if (ghostPaths.length > 0) {
      const ghostD = ghostPaths.join(' ');
      res += `<path d="${ghostD}" fill="none" stroke="#ffffff" stroke-width="${Math.max(1, state.width * 0.75).toFixed(2)}" stroke-opacity="0.90" stroke-linejoin="round" stroke-linecap="round" />`;
    }
    return res;
  }

  static computeControllerGuideSpec(poly, isSelected = false) {
    const isOccluder = Boolean(
      poly && (typeof poly.isOccluder === 'function'
        ? poly.isOccluder()
        : (poly.type === 'occluder' || poly.type === 'circle_occluder'))
    );

    if (isOccluder) {
      return {
        fill: '#000000',
        fillOpacity: 0.95,
        renderLiveContour: false,
        guideStroke: '#ffffff',
        guideWidth: isSelected ? 3 : 1.5,
        guideOpacity: 1.0,
        guideDash: '6 4'
      };
    }

    const hasMedia = Boolean(
      poly?.media
      && (poly.media.type === 'image' || poly.media.type === 'video')
      && poly.media.src
    );
    const fillEnabled = poly?.fillEnabled !== false;
    const fill = (hasMedia || !fillEnabled) ? '#000000' : (poly?.color || '#00f0ff');
    const fillOpacity = hasMedia
      ? (isSelected ? 0.08 : 0.001)
      : (!fillEnabled ? (isSelected ? 0.06 : 0.001) : (isSelected ? 0.35 : 0.18));
    const strokeVisible = Boolean(poly?.stroke && poly.stroke.visible);

    if (strokeVisible) {
      return {
        fill,
        fillOpacity,
        renderLiveContour: true,
        guideStroke: isSelected ? '#ffffff' : 'none',
        guideWidth: isSelected ? 1.2 : 0,
        guideOpacity: isSelected ? 0.55 : 0,
        guideDash: isSelected ? '4 4' : 'none'
      };
    }

    return {
      fill,
      fillOpacity,
      renderLiveContour: false,
      guideStroke: poly?.color || '#00f0ff',
      guideWidth: isSelected ? 2.4 : 1.3,
      guideOpacity: isSelected ? 0.9 : (fillEnabled ? 0.65 : 0.38),
      guideDash: fillEnabled ? 'none' : '5 4'
    };
  }

  static applyInspectorUpdate(poly, patch = {}) {
    if (!poly) return { autoSwitchedLineStyle: false };
    let autoSwitchedLineStyle = false;

    if (patch.fillEnabled !== undefined) {
      poly.fillEnabled = Boolean(patch.fillEnabled);
    }

    if (!poly.stroke || typeof poly.stroke !== 'object') {
      poly.stroke = ContourEngine.normalizeStroke(null, poly.color);
    }

    if (patch.strokeVisible !== undefined) {
      poly.stroke.visible = Boolean(patch.strokeVisible);
    }
    if (typeof patch.strokeColor === 'string' && /^#[0-9a-fA-F]{6}$/.test(patch.strokeColor)) {
      poly.stroke.color = patch.strokeColor.toLowerCase();
    }
    if (patch.strokeWidth !== undefined) {
      const w = Number(patch.strokeWidth);
      if (Number.isFinite(w)) poly.stroke.width = Math.max(1, Math.min(32, w));
    }
    if (patch.strokeOpacity !== undefined) {
      const op = Number(patch.strokeOpacity);
      if (Number.isFinite(op)) poly.stroke.opacity = Math.max(0.05, Math.min(1.0, op));
    }
    if (patch.strokeGlow !== undefined) {
      poly.stroke.glow = Boolean(patch.strokeGlow);
    }
    if (patch.lineStyle !== undefined && ContourEngine.LINE_STYLES.includes(patch.lineStyle)) {
      // Para marcha continua no permitimos 'solid' ni 'double'
      if (poly.stroke?.animation?.type === 'march' && (patch.lineStyle === 'solid' || patch.lineStyle === 'double')) {
        // Se preserva el estilo discontinuo
      } else {
        poly.stroke.lineStyle = patch.lineStyle;
      }
    }
    if (patch.dashScale !== undefined) {
      const ds = Number(patch.dashScale);
      if (Number.isFinite(ds)) poly.stroke.dashScale = Math.max(0.25, Math.min(4.0, ds));
    }
    if (patch.chaseStyle !== undefined) {
      poly.stroke.animation = poly.stroke.animation || {};
      poly.stroke.animation.chaseStyle = patch.chaseStyle === 'comet' ? 'comet' : 'clean';
    }
    if (patch.pulseStyle !== undefined) {
      poly.stroke.animation = poly.stroke.animation || {};
      poly.stroke.animation.pulseStyle = patch.pulseStyle === 'deep' ? 'deep' : 'rhythmic';
    }
    if (patch.animationType !== undefined && ContourEngine.ANIMATION_TYPES.includes(patch.animationType)) {
      poly.stroke.animation = poly.stroke.animation || {};
      if (patch.animationType === 'march' && (poly.stroke.lineStyle === 'solid' || poly.stroke.lineStyle === 'double')) {
        poly.stroke.lineStyle = 'dashed';
        autoSwitchedLineStyle = true;
      }
      const isSegmented = (patch.animationType === 'trace_chaser' || patch.animationType === 'laser_comet' || patch.animationType === 'dual_chase' || patch.animationType === 'pulse_wave');
      if (isSegmented && (poly.stroke.segmentLength === undefined || poly.stroke.segmentLength === 1.0)) {
        poly.stroke.segmentLength = 0.35;
      }
      poly.stroke.animation.type = patch.animationType;
    }
    if (patch.segmentLength !== undefined) {
      const sl = Number(patch.segmentLength);
      if (Number.isFinite(sl)) {
        poly.stroke.segmentLength = Math.max(0.05, Math.min(1.0, sl));
      }
    }
    if (patch.animationSpeed !== undefined) {
      const sp = Number(patch.animationSpeed);
      if (Number.isFinite(sp)) {
        poly.stroke.animation = poly.stroke.animation || {};
        poly.stroke.animation.speed = Math.max(0.1, Math.min(5.0, sp));
      }
    }
    if (patch.animationDirection !== undefined) {
      poly.stroke.animation = poly.stroke.animation || {};
      poly.stroke.animation.direction = patch.animationDirection === 'ccw' ? 'ccw' : 'cw';
    }

    poly.stroke = ContourEngine.normalizeStroke(poly.stroke, poly.color);
    return { autoSwitchedLineStyle };
  }
}

ContourEngine.LINE_STYLES = ['solid', 'dashed', 'dotted', 'dash_dot', 'long_dash', 'double'];
ContourEngine.ANIMATION_TYPES = ['none', 'march', 'trace_chaser', 'laser_comet', 'dual_chase', 'draw_wipe', 'pulse', 'pulse_wave', 'edge_glitch', 'gradient_march', 'color_flow'];
ContourEngine.BASE_FREQS = {
  none: 0,
  march: 0.5,
  trace_chaser: 0.35,
  laser_comet: 0.35,
  dual_chase: 0.35,
  draw_wipe: 0.25,
  pulse: 0.6,
  pulse_wave: 0.35,
  edge_glitch: 0.8,
  gradient_march: 0.25,
  color_flow: 0.25
};
ContourEngine.DASH_MULTIPLIERS = {
  dashed: [4.0, 3.0],
  dotted: [0.25, 2.5],
  dash_dot: [5.0, 2.4, 0.35, 2.4],
  long_dash: [10.0, 3.8]
};

class ContourAnimator {
  constructor(onTick, timeProvider = null, rafScheduler = null, cafScheduler = null) {
    const root = typeof window !== 'undefined' ? window : globalThis;
    this._onTick = typeof onTick === 'function' ? onTick : () => {};
    this._timeProvider = timeProvider || (() => (
      typeof performance !== 'undefined' && typeof performance.now === 'function'
        ? performance.now() / 1000
        : Date.now() / 1000
    ));
    this._raf = rafScheduler || (typeof root.requestAnimationFrame === 'function'
      ? (cb) => root.requestAnimationFrame(cb)
      : (cb) => setTimeout(cb, 16));
    this._caf = cafScheduler || (typeof root.cancelAnimationFrame === 'function'
      ? (id) => root.cancelAnimationFrame(id)
      : (id) => clearTimeout(id));

    this._running = false;
    this._rafId = null;
    this._destroyed = false;
  }

  get isRunning() {
    return this._running;
  }

  syncPolygons(polygons, paused = false) {
    if (this._destroyed) return;

    const shouldAnimate = !paused
      && Array.isArray(polygons)
      && polygons.some(p => (
        p
        && p.visible !== false
        && p.type !== 'occluder'
        && p.type !== 'circle_occluder'
        && p.stroke?.visible
        && p.stroke?.animation?.type
        && p.stroke.animation.type !== 'none'
      ));

    if (shouldAnimate && !this._running) {
      this._running = true;
      this._scheduleNext();
    } else if (!shouldAnimate && this._running) {
      this._stop();
    }
  }

  _scheduleNext() {
    if (!this._running || this._destroyed || this._rafId !== null) return;
    this._rafId = this._raf(() => {
      this._rafId = null;
      if (!this._running || this._destroyed) return;
      const tSec = this._timeProvider();
      this._onTick(tSec);
      if (this._running && !this._destroyed) {
        this._scheduleNext();
      }
    });
  }

  _stop() {
    this._running = false;
    if (this._rafId !== null) {
      const id = this._rafId;
      this._rafId = null;
      this._caf(id);
    }
  }

  destroy() {
    this._destroyed = true;
    this._stop();
  }
}

{
  const _root = typeof window !== 'undefined' ? window : globalThis;
  _root.ContourEngine = ContourEngine;
  _root.ContourAnimator = ContourAnimator;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { ContourEngine, ContourAnimator };
}
