# Product Overview

Multi-Timeframe RSI (MTF RSI) is a technical analysis indicator for the MotiveWave trading platform.

## Purpose
Displays 5 independent RSI oscillators with different periods (7, 14, 21, 50, 100) simultaneously, enabling traders to analyze momentum across multiple timeframes in a single view.

## Key Features
- Visual hierarchy with distinct colors and line styles for each RSI period
- Thread-safe implementation using Immutable Snapshot Pattern
- Hot-reload development workflow (no MotiveWave restart needed)
- Configurable lookback window for performance optimization
- Wilder's Smoothing algorithm for accurate RSI calculation

## Target Platform
MotiveWave charting platform (extends `com.motivewave.platform.sdk.study.Study`)
