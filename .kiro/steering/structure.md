# Project Structure

```
/
├── src/
│   └── com/biotak/           # Main source package
│       └── MultiTimeframeRSI.java  # Primary indicator class
├── lib/
│   └── mwave_sdk.jar         # MotiveWave SDK (local dependency)
├── pom.xml                   # Maven build configuration
├── PROJECT_INFO.md           # Technical documentation
└── README.md                 # User documentation (Persian/English)
```

## Code Organization

The `MultiTimeframeRSI.java` file contains:
- `MultiTimeframeRSI` - Main Study class with `@StudyHeader` annotation
- `SettingsCache` - Thread-safe settings management (inner class)
- `SettingsSnapshot` - Immutable settings container (inner class)
- `CalculationState` - Incremental calculation state manager (inner class)
- `Values` enum - Keys for calculated values and internal state

## Conventions
- Package: `com.biotak`
- Namespace in MotiveWave: `Biotak`
- Menu location: `Biotak` menu in MotiveWave
- Bilingual comments (Persian/English) in documentation
