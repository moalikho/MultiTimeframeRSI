# Tech Stack

## Language & Runtime
- Java 24
- MotiveWave SDK (local JAR: `lib/mwave_sdk.jar`)

## Build System
- Maven 3.x
- Encoding: UTF-8

## Dependencies
- `com.motivewave:mwave-sdk:1.0` (system scope, local JAR)

## Build Commands

```bash
# Development - copies .class files to dev folder
mvn compile -Pdev
# After compile: remove study from chart, re-add to reload

# Release - optimized JAR
mvn package -Prelease
# Outputs to ~/MotiveWave Extensions/lib/

# Default package (with debug info)
mvn package

# Clean build artifacts
mvn clean
```

## Development Workflow
1. Run `mvn compile -Pdev`
2. In MotiveWave: remove study from chart
3. Re-add study from Biotak menu
4. New classes are loaded (no full restart needed)

## Key Patterns
- Immutable Snapshot Pattern for thread-safe settings
- Atomic operations for calculation state management
- Static constants to reduce GC pressure
- Wilder's Smoothing for RSI calculation

## MotiveWave Integration
- Studies extend `com.motivewave.platform.sdk.study.Study`
- Use `@StudyHeader` annotation for metadata
- Output directory: `~/MotiveWave Extensions/dev/` (development)
- Output directory: `~/MotiveWave Extensions/lib/` (release JAR)
