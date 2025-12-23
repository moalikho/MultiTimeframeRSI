# Project Info: Multi-Timeframe RSI

## Technical Details

### Architecture
- **Pattern**: MotiveWave Study (extends `Study`)
- **Thread Safety**: Immutable Snapshot Pattern + Atomic Operations
- **Performance**: Lazy calculation with configurable lookback window

### RSI Algorithm (Wilder's Smoothing)
```
RSI = 100 - (100 / (1 + RS))
RS = Smoothed Average Gain / Smoothed Average Loss

First value: Simple average of first N periods
Subsequent: ((prev * (period-1)) + current) / period
```

### Default Periods
| Priority | Period | Color | Description |
|----------|--------|-------|-------------|
| ⭐⭐⭐ Primary | 100 | Forest Green | Long-term trend |
| ⭐⭐ Secondary | 50 | Bright Blue | Medium-term |
| ⭐ Tertiary | 21 | Purple | Short-term |
| Support | 14 | Orange | Standard RSI |
| Support | 7 | Dark Gray | Fast/Noise |

### Key Classes
- `MultiTimeframeRSI` - Main indicator class
- `SettingsCache` - Thread-safe immutable settings snapshot
- `CalculationState` - Manages incremental calculation state

### Optimizations
1. **GC Pressure Reduction**: Static Color/String constants
2. **Settings Cache**: Avoids repeated HashMap lookups
3. **Incremental Calculation**: Only calculates new/changed bars
4. **Wilder's Smoothing**: Stores intermediate values for efficiency

## Build
```bash
# ⚡ DEVELOPMENT (کلاس‌ها به پوشه dev کپی می‌شن)
mvn compile -Pdev
# بعد از compile، study رو از چارت حذف و دوباره اضافه کنید

# 📦 RELEASE (ساخت JAR برای انتشار)
mvn package
# یا با بهینه‌سازی کامل:
mvn package -Prelease

# 🧹 پاکسازی
mvn clean
```

### نکات مهم
- **Development**: از `mvn compile -Pdev` استفاده کنید. فایل‌ها به `~/MotiveWave Extensions/dev/` کپی می‌شن
- **Reload**: بعد از compile، study رو از چارت حذف و دوباره اضافه کنید (بدون restart)
- **Release**: از `mvn package -Prelease` برای JAR بهینه‌شده استفاده کنید
