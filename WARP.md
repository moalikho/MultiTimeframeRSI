# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Build and Development Commands

This is a single-module Maven project targeting Java 24 and building a MotiveWave study (`jar` plugin).

### Prerequisites
- JDK 24 installed and configured on `PATH`.
- MotiveWave SDK JAR present at `lib/mwave_sdk.jar` (referenced as a `system`-scoped dependency in `pom.xml`).
- MotiveWave "Extensions" directory available under `${user.home}/MotiveWave Extensions`.

### Core Maven commands
- Development (deploy `.class` files for hot-reload into MotiveWave `dev` folder):
  - `mvn compile -Pdev`
  - This compiles sources to `target/classes` and copies them into `${mw.dev}` (defaults to `${user.home}/MotiveWave Extensions/dev`).
  - After running, refresh in MotiveWave by:
    1. Removing the study from the chart.
    2. Re-adding it from the `Biotak` menu.

- Release build (optimized JAR for distribution):
  - `mvn package -Prelease`
  - Produces `build/mtf-rsi.jar` and copies it into `${mw.lib}` (defaults to `${user.home}/MotiveWave Extensions/lib`).
  - Touches `${mw.extensions}/.last_updated` so MotiveWave can detect the new JAR.

- Clean build artifacts and dev deployment:
  - `mvn clean`
  - Cleans `${mw.dev}` and the local `build` directory, in addition to standard Maven targets.

### Tests and linting
- There is currently no `src/test` tree or test configuration in `pom.xml`; running `mvn test` does not exercise any project-specific tests.
- No linting or static-analysis plugins (Checkstyle, SpotBugs, etc.) are configured in `pom.xml`.

## High-Level Architecture

### Overall layout
- `pom.xml` — Maven configuration, including Java 24 settings, MotiveWave SDK dependency, and build/deploy profiles.
- `src/com/biotak/MultiTimeframeRSI.java` — the only Java source file; implements the entire multi-timeframe RSI study for MotiveWave.

The project is intentionally small and focused: all runtime behavior is encapsulated in a single `Study` subclass with internal helper types for performance and thread-safety.

### MultiTimeframeRSI study

`com.biotak.MultiTimeframeRSI` extends `com.motivewave.platform.sdk.study.Study` and is annotated with `@StudyHeader` to register it with MotiveWave under the `Biotak` namespace/menu.

Key responsibilities:
- Define 5 RSI variants with independent configuration (period, visibility, normalization, line style/color).
- Provide UI descriptors for settings and guides (overbought/oversold/midline) in MotiveWave.
- Implement an efficient, thread-safe RSI calculation pipeline with optional percentile-based normalization.

Important internal structures:

- `enum Values` — central registry for all computed series values:
  - Public RSIs: `RSI1`..`RSI5` (exported and plotted).
  - Raw RSIs: `RSI1_RAW`..`RSI5_RAW` (used for normalization lookback windows).
  - Internal smoothed components: `AVG_GAIN*` / `AVG_LOSS*` (used by Wilder smoothing, not exported).

- Static configuration arrays:
  - `PERIOD_KEYS`, `SHOW_KEYS`, `NORM_KEYS` — map logical RSI slots (1–5) to `Settings` keys.
  - `RSI_VALUES`, `RSI_RAW_VALUES`, `AVG_GAIN_VALUES`, `AVG_LOSS_VALUES` — map slots to `Values` enum entries.
  - `DEFAULT_PERIODS` — default periods for the 5 RSIs (5, 15, 30, 60, 240 minutes by default).
  - `DEFAULT_NORMALIZE` — which RSIs are normalized by default (all except the fastest).
  - Shared `Color` and dash-pattern constants reused across descriptors to reduce allocations.

### Settings and caching model

The class is built around an immutable snapshot pattern for settings and a centralized calculation state tracker to remain thread-safe inside MotiveWave's multi-threaded environment.

- `SettingsCache` (static inner class):
  - Holds a volatile `SettingsSnapshot` plus a cached `settingsHash`.
  - `SettingsSnapshot` copies all relevant options from `Settings` into primitive arrays (`showFlags`, `rsiPeriods`, `normalizeFlags`) and pre-computes a hash.
  - `updateIfNeeded(Settings)` computes a quick hash and only rebuilds the snapshot when settings actually change (using a lock and double-checked logic for safety).
  - `invalidate()` resets the snapshot and hash when a full recomputation is required.

- `CalculationState` (static inner class):
  - Uses `AtomicInteger` and `AtomicLong` fields (`lastCalculatedIndex`, `currentMaxLookback`, `lastTotalBars`, `lastCandleHash`) plus a `stateLock` object.
  - `shouldCalculate(...)` determines whether a given bar index needs recalculation based on:
    - Current max lookback window.
    - Total bar count changes (append, shrink, or full reset).
    - A lightweight OHLC hash to avoid recalculation for the latest bar when prices have not changed.
  - `markCalculated(int)` advances `lastCalculatedIndex` to the furthest computed bar.

- Public maintenance APIs:
  - `triggerFullRecalculation()` sets a flag to recompute all bars on the next `calculate()` calls.
  - `invalidateCaches()` resets both `SettingsCache` and `CalculationState`.
  - `clearState()` overrides the base implementation to clear runtime caches and bar-size tracking.

### Initialization and descriptors

`initialize(Defaults defaults)` wires the study into MotiveWave's UI and plotting system:

- Creates a primary "Settings" tab with groups for:
  - Performance: `maxLookback` (limit on number of candles to compute; `0` means all).
  - Normalization: `normLookback` (lookback window for percentile-based normalization).
  - Five RSI groups (`RSI 5`, `15`, `30`, `60`, `240`) each with:
    - `showN` (visibility toggle).
    - `periodN` (double period, with a reasonable min/max range per RSI).
    - `normN` (enable/disable normalization per line).
    - `pathN` (PathDescriptor specifying color, width, and dash style to convey a visual hierarchy).

- Creates a "Guides" tab with three horizontal guide lines:
  - Overbought (80), middle (50), oversold (20), all with light-gray dashed styling.

- Runtime descriptor (`createRD()`):
  - Labels RSIs with their periods for the data box.
  - Exports `Values.RSI1`..`Values.RSI5` with associated keys for external consumers.
  - Declares plotting paths mapping each `Values.RSI*` to the corresponding `pathN`.
  - Fixes the plot range to 0–100 with no vertical padding and attaches the three guide descriptors.

### Calculation pipeline

The main work happens in `calculate(int index, DataContext ctx)`:

1. Early exits when there is no data.
2. Handles a pending full recalculation by resetting caches.
3. Detects bar-size (timeframe) changes via `series.getBarSize()`; if changed, invalidates caches and resets state.
4. Retrieves or refreshes the `SettingsSnapshot` from `SettingsCache`.
5. Uses `CalculationState.shouldCalculate(...)` to skip redundant work outside the active window or when the last bar's OHLC has not changed.
6. Loops across all 5 RSIs:
   - Skips disabled lines, writing `NaN` into the exported value.
   - Validates the period.
   - Computes a raw RSI using `calculateRSI(...)` (Wilder's smoothing, with initial SMA bootstrapping and NaN-safe handling).
   - Stores the raw RSI in the `RSI*_RAW` slots.
   - Optionally normalizes the value via `normalizeRSI(...)` (percentile ranking over a configurable lookback) when `normN` is enabled.
   - Writes the final RSI value into the main `RSI*` slots.
7. Marks the current index as calculated.

`calculateRSI(...)` and `normalizeRSI(...)` are self-contained helpers that rely exclusively on the `DataSeries` API and `Values` keys; they do not depend on MotiveWave UI constructs, making them relatively easy to refactor or reuse if needed.

## MotiveWave Integration Details

- The study is registered with `@StudyHeader` namespace `"Biotak"` and id `"MTFRSI"`; it appears under the `Biotak` menu inside MotiveWave.
- The Maven build uses properties to derive MotiveWave paths:
  - `${mw.extensions}` → `${user.home}/MotiveWave Extensions`.
  - `${mw.dev}` → `${mw.extensions}/dev` for development `.class` deployment.
  - `${mw.lib}` → `${mw.extensions}/lib` for release JAR deployment.
- The AntRun plugin handles copying artifacts and touching `${mw.extensions}/.last_updated` so MotiveWave can reload the extension set without a full application restart.

For day-to-day development, the common loop is:
1. Edit `src/com/biotak/MultiTimeframeRSI.java`.
2. Run `mvn compile -Pdev`.
3. In MotiveWave, remove the study from the chart and re-add it from the `Biotak` menu to pick up the new `.class` files.
4. When ready to ship or test with a stable JAR, run `mvn package -Prelease` and ensure MotiveWave is pointing at the updated `mtf-rsi.jar` in the `lib` directory.
