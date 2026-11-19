"""Mide la latencia de inferencia en CPU (100 iteraciones) de midas_small_256_fp16.tflite."""

from __future__ import annotations

import sys
import time
import numpy as np

if sys.platform == "win32":
    try:
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

from reference_pipeline import load_interpreter, infer_depth_tflite

WARMUP_RUNS = 10
BENCHMARK_RUNS = 100


def run_benchmark():
    interpreter = load_interpreter()
    dummy_input = np.random.uniform(-2.0, 2.0, size=(1, 256, 256, 3)).astype(np.float32)

    print(f"[benchmark_pc] Ejecutando {WARMUP_RUNS} pasadas de calentamiento (warmup)...")
    for _ in range(WARMUP_RUNS):
        _ = infer_depth_tflite(interpreter, dummy_input)

    latencies_ms = []
    print(f"[benchmark_pc] Midiendo {BENCHMARK_RUNS} inferencias en CPU...")
    for _ in range(BENCHMARK_RUNS):
        t0 = time.perf_counter()
        _ = infer_depth_tflite(interpreter, dummy_input)
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        latencies_ms.append(elapsed_ms)

    arr = np.array(latencies_ms)
    p50 = float(np.percentile(arr, 50))
    p90 = float(np.percentile(arr, 90))
    p95 = float(np.percentile(arr, 95))
    mean = float(arr.mean())
    fps_est = 1000.0 / p50 if p50 > 0 else 0

    print("=" * 60)
    print("🚀 RESULTADOS DE BENCHMARK TFLite en CPU (PC)")
    print(f"   Modelo:         midas_small_256_fp16.tflite")
    print(f"   Iteraciones:    {BENCHMARK_RUNS}")
    print(f"   Mediana (p50):  {p50:.2f} ms (~{fps_est:.1f} FPS)")
    print(f"   Media:          {mean:.2f} ms")
    print(f"   p90:            {p90:.2f} ms")
    print(f"   p95:            {p95:.2f} ms")
    print(f"   Mín / Máx:      {arr.min():.2f} ms / {arr.max():.2f} ms")
    print("=" * 60)


if __name__ == "__main__":
    run_benchmark()
