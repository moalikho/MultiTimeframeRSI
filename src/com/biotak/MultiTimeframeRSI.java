package com.biotak;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.motivewave.platform.sdk.common.desc.*;

import java.awt.Color;

/**
 * Multi-Timeframe RSI Indicator with Adaptive Normalization
 * نمایش 5 RSI با دوره‌های مختلف به صورت همزمان
 * 
 * الگوریتم RSI (Wilder's Smoothing):
 * RSI = 100 - (100 / (1 + RS))
 * RS = Smoothed Average Gain / Smoothed Average Loss
 * 
 * الگوریتم نرمالایزیشن:
 * normalized_RSI = ((RSI - min_RSI) / (max_RSI - min_RSI)) * 100
 * - RSI های بلندمدت نوسان کمی دارن و نزدیک 50 میمونن
 * - نرمالایزیشن باعث میشه نوسان کامل 0-100 داشته باشن
 * 
 * @author Biotak Development Team
 * @version 2.0.0
 */
@StudyHeader(
    namespace = "Biotak",
    id = "MTFRSI",
    name = "MTF RSI",
    label = "MTF RSI",
    desc = "Multi-Timeframe RSI Oscillator with Adaptive Normalization",
    menu = "Biotak",
    overlay = false,
    studyOverlay = false
)
public class MultiTimeframeRSI extends Study {
    
    /** Enum برای مقادیر محاسبه شده */
    enum Values { 
        RSI1, RSI2, RSI3, RSI4, RSI5,
        // Raw RSI values for normalization
        RSI1_RAW, RSI2_RAW, RSI3_RAW, RSI4_RAW, RSI5_RAW,
        // Internal values for smoothed averages (not exported)
        AVG_GAIN1, AVG_LOSS1, AVG_GAIN2, AVG_LOSS2, AVG_GAIN3, AVG_LOSS3,
        AVG_GAIN4, AVG_LOSS4, AVG_GAIN5, AVG_LOSS5,
        // Min/Max values for normalization (internal)
        RSI3_MIN, RSI3_MAX, RSI4_MIN, RSI4_MAX, RSI5_MIN, RSI5_MAX
    }
    
    // Min/Max keys mapping for normalization
    private static final Values[] MIN_VALUES = {null, null, Values.RSI3_MIN, Values.RSI4_MIN, Values.RSI5_MIN};
    private static final Values[] MAX_VALUES = {null, null, Values.RSI3_MAX, Values.RSI4_MAX, Values.RSI5_MAX};
    
    // ═══════════════════════════════════════════════════════════════════════════
    // THREAD-SAFE SETTINGS CACHE (Immutable Snapshot Pattern)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Thread-safe settings cache manager using immutable snapshot pattern
     * Eliminates race conditions and repeated HashMap lookups
     */
    private static class SettingsCache {
        private volatile SettingsSnapshot snapshot = null;
        private volatile int lastSettingsHash = 0;
        private final Object updateLock = new Object();
        
        /** Immutable snapshot of settings - thread-safe by design */
        private static class SettingsSnapshot {
            final int maxLookback;
            final boolean[] showFlags;
            final double[] rsiPeriods;
            final boolean[] normalizeFlags;
            final int normLookback;
            final int settingsHash;
            
            SettingsSnapshot(Settings settings) {
                this.maxLookback = settings.getInteger("maxLookback", DEFAULT_MAX_LOOKBACK);
                this.normLookback = settings.getInteger("normLookback", DEFAULT_NORM_LOOKBACK);
                this.showFlags = new boolean[NUM_RSI];
                this.rsiPeriods = new double[NUM_RSI];
                this.normalizeFlags = new boolean[NUM_RSI];
                
                int hash = 17;
                hash = 31 * hash + maxLookback;
                hash = 31 * hash + normLookback;
                
                for (int i = 0; i < NUM_RSI; i++) {
                    showFlags[i] = settings.getBoolean(SHOW_KEYS[i], true);
                    rsiPeriods[i] = settings.getDouble(PERIOD_KEYS[i], DEFAULT_PERIODS[i]);
                    normalizeFlags[i] = settings.getBoolean(NORM_KEYS[i], DEFAULT_NORMALIZE[i]);
                    
                    hash = 31 * hash + (showFlags[i] ? 1 : 0);
                    hash = 31 * hash + Double.hashCode(rsiPeriods[i]);
                    hash = 31 * hash + (normalizeFlags[i] ? 1 : 0);
                }
                this.settingsHash = hash;
            }
        }
        
        /** 
         * Update cache with new settings - synchronized to prevent duplicate creation
         * Returns the snapshot (existing or newly created)
         */
        SettingsSnapshot updateIfNeeded(Settings settings) {
            if (settings == null) return null;
            
            // Fast path - check if snapshot exists and settings haven't changed
            SettingsSnapshot current = snapshot;
            if (current != null) {
                int quickHash = computeQuickHash(settings);
                if (quickHash == lastSettingsHash) {
                    return current;
                }
                // Hash changed - need to create new snapshot
                synchronized (updateLock) {
                    // Double-check after acquiring lock
                    if (snapshot != null && lastSettingsHash == quickHash) {
                        return snapshot;
                    }
                    current = new SettingsSnapshot(settings);
                    snapshot = current;
                    lastSettingsHash = current.settingsHash;
                    return current;
                }
            }
            
            // No snapshot exists - create one
            synchronized (updateLock) {
                if (snapshot != null) {
                    return snapshot; // Another thread created it
                }
                current = new SettingsSnapshot(settings);
                snapshot = current;
                lastSettingsHash = current.settingsHash;
                return current;
            }
        }
        
        /** Compute quick hash without creating full snapshot */
        private int computeQuickHash(Settings settings) {
            int hash = 17;
            hash = 31 * hash + settings.getInteger("maxLookback", DEFAULT_MAX_LOOKBACK);
            hash = 31 * hash + settings.getInteger("normLookback", DEFAULT_NORM_LOOKBACK);
            for (int i = 0; i < NUM_RSI; i++) {
                hash = 31 * hash + (settings.getBoolean(SHOW_KEYS[i], true) ? 1 : 0);
                hash = 31 * hash + Double.hashCode(settings.getDouble(PERIOD_KEYS[i], DEFAULT_PERIODS[i]));
                hash = 31 * hash + (settings.getBoolean(NORM_KEYS[i], DEFAULT_NORMALIZE[i]) ? 1 : 0);
            }
            return hash;
        }
        
        void invalidate() {
            synchronized (updateLock) {
                snapshot = null;
                lastSettingsHash = 0;
            }
        }
        
        boolean isValid() { return snapshot != null; }
        SettingsSnapshot getSnapshot() { return snapshot; }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CALCULATION STATE MANAGER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Manages calculation state with proper synchronization
     * Handles window changes and prevents conflicting logic
     */
    private static class CalculationState {
        private final java.util.concurrent.atomic.AtomicInteger lastCalculatedIndex = 
            new java.util.concurrent.atomic.AtomicInteger(-1);
        private final java.util.concurrent.atomic.AtomicInteger currentMaxLookback = 
            new java.util.concurrent.atomic.AtomicInteger(-1);
        private final java.util.concurrent.atomic.AtomicInteger lastTotalBars = 
            new java.util.concurrent.atomic.AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicLong lastCandleHash = 
            new java.util.concurrent.atomic.AtomicLong(0);
        private final Object stateLock = new Object();
        
        boolean shouldCalculate(int index, int totalBars, int maxLookback, DataSeries series) {
            if (index < 0 || totalBars <= 0) return false;
            
            int calcStartIndex = (maxLookback > 0) ? Math.max(0, totalBars - maxLookback) : 0;
            
            synchronized (stateLock) {
                int prevMaxLookback = currentMaxLookback.get();
                int prevTotalBars = lastTotalBars.get();
                
                if (prevMaxLookback != maxLookback) {
                    currentMaxLookback.set(maxLookback);
                    lastTotalBars.set(totalBars);
                    lastCalculatedIndex.set(calcStartIndex - 1);
                } else if (totalBars > prevTotalBars) {
                    lastTotalBars.set(totalBars);
                } else if (totalBars < prevTotalBars) {
                    lastTotalBars.set(totalBars);
                    lastCalculatedIndex.set(calcStartIndex - 1);
                }
            }
            
            if (index < calcStartIndex) return false;
            
            // بهینه‌سازی: آخرین کندل فقط وقتی قیمت تغییر کرده محاسبه شود
            boolean isLastCandle = (index == totalBars - 1);
            if (isLastCandle && index <= lastCalculatedIndex.get()) {
                // چک کن آیا قیمت تغییر کرده
                long currentHash = computeCandleHash(series, index);
                long prevHash = lastCandleHash.get();
                if (currentHash == prevHash) {
                    return false; // قیمت تغییر نکرده، نیازی به محاسبه نیست
                }
                lastCandleHash.set(currentHash);
                return true;
            }
            
            return index > lastCalculatedIndex.get();
        }
        
        /** محاسبه هش سریع برای تشخیص تغییر قیمت - شامل همه فیلدهای OHLC */
        private long computeCandleHash(DataSeries series, int index) {
            if (series == null || index < 0 || index >= series.size()) return 0;
            float open = series.getOpen(index);
            float high = series.getHigh(index);
            float low = series.getLow(index);
            float close = series.getClose(index);
            // ترکیب همه مقادیر برای هش دقیق‌تر
            long hash = 17;
            hash = 31 * hash + Float.floatToIntBits(open);
            hash = 31 * hash + Float.floatToIntBits(high);
            hash = 31 * hash + Float.floatToIntBits(low);
            hash = 31 * hash + Float.floatToIntBits(close);
            return hash;
        }
        
        void markCalculated(int index) {
            lastCalculatedIndex.updateAndGet(current -> Math.max(current, index));
        }
        
        void reset() {
            synchronized (stateLock) {
                lastCalculatedIndex.set(-1);
                currentMaxLookback.set(-1);
                lastTotalBars.set(0);
                lastCandleHash.set(0);
            }
        }
    }

    
    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC CONSTANTS (Reduce GC Pressure)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final int NUM_RSI = 5;
    private static final String[] PERIOD_KEYS = {"period1", "period2", "period3", "period4", "period5"};
    private static final String[] SHOW_KEYS = {"show1", "show2", "show3", "show4", "show5"};
    private static final String[] NORM_KEYS = {"norm1", "norm2", "norm3", "norm4", "norm5"};
    private static final Values[] RSI_VALUES = {Values.RSI1, Values.RSI2, Values.RSI3, Values.RSI4, Values.RSI5};
    private static final Values[] RSI_RAW_VALUES = {Values.RSI1_RAW, Values.RSI2_RAW, Values.RSI3_RAW, Values.RSI4_RAW, Values.RSI5_RAW};
    private static final Values[] AVG_GAIN_VALUES = {Values.AVG_GAIN1, Values.AVG_GAIN2, Values.AVG_GAIN3, Values.AVG_GAIN4, Values.AVG_GAIN5};
    private static final Values[] AVG_LOSS_VALUES = {Values.AVG_LOSS1, Values.AVG_LOSS2, Values.AVG_LOSS3, Values.AVG_LOSS4, Values.AVG_LOSS5};
    
    // Default periods: 5, 15, 30, 60, 240 (مثل Stochastic)
    private static final double[] DEFAULT_PERIODS = {5.0, 15.0, 30.0, 60.0, 240.0};
    
    // Default normalize flags: کوتاه‌مدت‌ها نه، بلندمدت‌ها آره
    private static final boolean[] DEFAULT_NORMALIZE = {false, false, true, true, true};
    
    // Reusable Color constants (مثل Stochastic)
    private static final Color COLOR_FOREST_GREEN = new Color(40, 140, 60);
    private static final Color COLOR_BRIGHT_BLUE = new Color(70, 130, 220);
    private static final Color COLOR_ORANGE = new Color(210, 130, 50);
    private static final Color COLOR_DARK_GRAY = new Color(60, 60, 60);
    private static final Color COLOR_MED_GRAY = new Color(100, 100, 100);
    private static final Color COLOR_LIGHT_GRAY = new Color(140, 140, 140);
    
    // Reusable dash patterns
    private static final float[] DASH_6_3 = {6, 3};
    private static final float[] DASH_4_3 = {4, 3};
    private static final float[] DASH_2_4 = {2, 4};
    private static final float[] DASH_4_4 = {4, 4};
    
    // Reusable String arrays for ValueDescriptor
    private static final String[] KEY_P1 = {"period1"};
    private static final String[] KEY_P2 = {"period2"};
    private static final String[] KEY_P3 = {"period3"};
    private static final String[] KEY_P4 = {"period4"};
    private static final String[] KEY_P5 = {"period5"};
    
    // Thread-safe components
    private final SettingsCache settingsCache = new SettingsCache();
    private final CalculationState calculationState = new CalculationState();
    private final java.util.concurrent.atomic.AtomicBoolean needsFullRecalc = 
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicReference<Object> lastBarSize = 
        new java.util.concurrent.atomic.AtomicReference<>(null);
    
    private static final int DEFAULT_MAX_LOOKBACK = 1000;
    private static final int DEFAULT_NORM_LOOKBACK = 100;
    private static final double MIN_RANGE = 10.0; // مثل MT4 - حداقل range برای نرمالایزیشن
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Trigger full recalculation of all candles */
    public void triggerFullRecalculation() {
        needsFullRecalc.set(true);
    }
    
    /** Invalidate caches and force recalculation */
    public void invalidateCaches() {
        settingsCache.invalidate();
        calculationState.reset();
    }
    
    @Override
    public void clearState() {
        super.clearState();
        invalidateCaches();
        lastBarSize.set(null);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void initialize(Defaults defaults) {
        settingsCache.invalidate();
        calculationState.reset();
        
        var sd = createSD();
        var tab = sd.addTab("Settings");
        
        // Performance Settings
        var perfGrp = tab.addGroup("Performance Settings");
        perfGrp.addRow(new IntegerDescriptor("maxLookback", "Max Candles to Calculate (0 = All)", 
            DEFAULT_MAX_LOOKBACK, 0, 10000, 100));
        
        // Normalization Settings
        var normGrp = tab.addGroup("Normalization Settings");
        normGrp.addRow(new IntegerDescriptor("normLookback", "Normalization Lookback Period", 
            DEFAULT_NORM_LOOKBACK, 10, 500, 10));

        // ⭐⭐⭐ PRIMARY: RSI 240 - Forest Green
        var grp5 = tab.addGroup("⭐⭐⭐ PRIMARY: RSI 240");
        grp5.addRow(new BooleanDescriptor("show5", "Show RSI", true));
        grp5.addRow(new DoubleDescriptor("period5", "Period", 240.0, 2.0, 500.0, 0.01));
        grp5.addRow(new BooleanDescriptor("norm5", "Normalize", true));
        grp5.addRow(new PathDescriptor("path5", "RSI Line", COLOR_FOREST_GREEN, 3.0f, null, true, true, true));
        
        // ⭐⭐ SECONDARY: RSI 60 - Bright Blue
        var grp4 = tab.addGroup("⭐⭐ SECONDARY: RSI 60");
        grp4.addRow(new BooleanDescriptor("show4", "Show RSI", true));
        grp4.addRow(new DoubleDescriptor("period4", "Period", 60.0, 2.0, 300.0, 0.01));
        grp4.addRow(new BooleanDescriptor("norm4", "Normalize", true));
        grp4.addRow(new PathDescriptor("path4", "RSI Line", COLOR_BRIGHT_BLUE, 2.5f, DASH_6_3, true, true, true));
        
        // ⭐ TERTIARY: RSI 30 - Orange
        var grp3 = tab.addGroup("⭐ TERTIARY: RSI 30");
        grp3.addRow(new BooleanDescriptor("show3", "Show RSI", true));
        grp3.addRow(new DoubleDescriptor("period3", "Period", 30.0, 2.0, 100.0, 0.01));
        grp3.addRow(new BooleanDescriptor("norm3", "Normalize", true));
        grp3.addRow(new PathDescriptor("path3", "RSI Line", COLOR_ORANGE, 2.0f, DASH_4_3, true, true, true));
        
        // Support: RSI 15 - Medium Gray
        var grp2 = tab.addGroup("Support: RSI 15");
        grp2.addRow(new BooleanDescriptor("show2", "Show RSI", true));
        grp2.addRow(new DoubleDescriptor("period2", "Period", 15.0, 2.0, 100.0, 0.01));
        grp2.addRow(new BooleanDescriptor("norm2", "Normalize", false));
        grp2.addRow(new PathDescriptor("path2", "RSI Line", COLOR_MED_GRAY, 1.5f, DASH_2_4, true, true, true));
        
        // Support: RSI 5 - Dark Gray (Fast)
        var grp1 = tab.addGroup("Support: RSI 5 (Fast)");
        grp1.addRow(new BooleanDescriptor("show1", "Show RSI", true));
        grp1.addRow(new DoubleDescriptor("period1", "Period", 5.0, 2.0, 100.0, 0.01));
        grp1.addRow(new BooleanDescriptor("norm1", "Normalize", false));
        grp1.addRow(new PathDescriptor("path1", "RSI Line", COLOR_DARK_GRAY, 1.2f, DASH_2_4, true, false, true));
        
        // Guides Tab
        var guidesTab = sd.addTab("Guides");
        var guidesGrp = guidesTab.addGroup("Horizontal Levels");
        
        GuideDescriptor upperGuide = new GuideDescriptor("upperGuide", "Overbought Level (80)", 80, 0, 100, 1, true);
        upperGuide.setLineColor(COLOR_LIGHT_GRAY);
        upperGuide.setDash(DASH_4_4);
        upperGuide.setWidth(1.0f);
        guidesGrp.addRow(upperGuide);
        
        GuideDescriptor middleGuide = new GuideDescriptor("middleGuide", "Middle Level (50)", 50, 0, 100, 1, true);
        middleGuide.setLineColor(COLOR_LIGHT_GRAY);
        middleGuide.setDash(DASH_4_4);
        middleGuide.setWidth(1.0f);
        guidesGrp.addRow(middleGuide);
        
        GuideDescriptor lowerGuide = new GuideDescriptor("lowerGuide", "Oversold Level (20)", 20, 0, 100, 1, true);
        lowerGuide.setLineColor(COLOR_LIGHT_GRAY);
        lowerGuide.setDash(DASH_4_4);
        lowerGuide.setWidth(1.0f);
        guidesGrp.addRow(lowerGuide);
        
        setSettingsDescriptor(sd);
        
        // Runtime descriptor
        var desc = createRD();
        desc.setLabelSettings("period1", "period2", "period3", "period4", "period5");
        
        desc.exportValue(new ValueDescriptor(Values.RSI1, "RSI (5)", KEY_P1));
        desc.exportValue(new ValueDescriptor(Values.RSI2, "RSI (15)", KEY_P2));
        desc.exportValue(new ValueDescriptor(Values.RSI3, "RSI (30)", KEY_P3));
        desc.exportValue(new ValueDescriptor(Values.RSI4, "RSI (60)", KEY_P4));
        desc.exportValue(new ValueDescriptor(Values.RSI5, "RSI (240)", KEY_P5));
        
        desc.declarePath(Values.RSI1, "path1");
        desc.declarePath(Values.RSI2, "path2");
        desc.declarePath(Values.RSI3, "path3");
        desc.declarePath(Values.RSI4, "path4");
        desc.declarePath(Values.RSI5, "path5");
        
        desc.setRangeKeys(Values.RSI1, Values.RSI2, Values.RSI3, Values.RSI4, Values.RSI5);
        
        // Fixed range for RSI (0-100)
        desc.setFixedBottomValue(0);
        desc.setFixedTopValue(100);
        desc.setBottomInsetPixels(0);
        desc.setTopInsetPixels(0);
        
        desc.getDefaultPlot().declareGuide("upperGuide");
        desc.getDefaultPlot().declareGuide("middleGuide");
        desc.getDefaultPlot().declareGuide("lowerGuide");
        
        setRuntimeDescriptor(desc);
    }

    
    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN CALCULATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * محاسبه اصلی اندیکاتور برای هر کندل
     * 
     * @param index اندیس کندل جاری
     * @param ctx   کانتکست داده‌ها
     */
    @Override
    protected void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        
        // Early exit for empty series
        if (series == null || series.size() == 0) {
            return;
        }
        
        int totalBars = series.size();
        
        try {
            // Handle fullRecalc flag - atomic compare-and-set
            if (needsFullRecalc.compareAndSet(true, false)) {
                calculationState.reset();
                settingsCache.invalidate();
            }
            
            // Detect timeframe changes
            Object currentBarSize = series.getBarSize();
            Object prevBarSize = lastBarSize.get();
            boolean barSizeChanged = (prevBarSize == null && currentBarSize != null) ||
                                     (prevBarSize != null && !prevBarSize.equals(currentBarSize));
            if (barSizeChanged) {
                if (lastBarSize.compareAndSet(prevBarSize, currentBarSize)) {
                    settingsCache.invalidate();
                    calculationState.reset();
                }
            }
            
            // Get cached settings
            SettingsCache.SettingsSnapshot settings = settingsCache.updateIfNeeded(getSettings());
            if (settings == null) return;
            
            int maxLookback = settings.maxLookback;
            
            // Check if we should calculate this index
            if (!calculationState.shouldCalculate(index, totalBars, maxLookback, series)) {
                return;
            }
            
            // Calculate all RSIs
            for (int i = 0; i < NUM_RSI; i++) {
                if (!settings.showFlags[i]) {
                    series.setDouble(index, RSI_VALUES[i], Double.NaN);
                    continue;
                }
                
                double period = settings.rsiPeriods[i];
                
                // Validate period
                if (period < 2.0) {
                    series.setDouble(index, RSI_VALUES[i], Double.NaN);
                    continue;
                }
                
                // Calculate raw RSI using Wilder's smoothing method
                double rawRsi = calculateRSI(series, index, period, 
                    AVG_GAIN_VALUES[i], AVG_LOSS_VALUES[i]);
                
                // Store raw RSI for normalization lookback
                series.setDouble(index, RSI_RAW_VALUES[i], rawRsi);
                
                // Apply normalization if enabled
                double finalRsi;
                if (settings.normalizeFlags[i] && !Double.isNaN(rawRsi)) {
                    finalRsi = normalizeRSI(series, index, i, RSI_RAW_VALUES[i], settings.normLookback, rawRsi);
                } else {
                    finalRsi = rawRsi;
                }
                
                series.setDouble(index, RSI_VALUES[i], finalRsi);
            }
            
            calculationState.markCalculated(index);
            
        } catch (Exception e) {
            System.err.println("[MTFRSI] Error calculating index " + index + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RSI CALCULATION (Wilder's Smoothing Method)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * محاسبه RSI با روش Wilder's Smoothing
     * 
     * الگوریتم:
     * 1. محاسبه تغییرات قیمت (change = close - prevClose)
     * 2. جداسازی gains و losses
     * 3. محاسبه میانگین smoothed با فرمول Wilder:
     *    - اولین مقدار: SMA ساده
     *    - مقادیر بعدی: ((prev * (period-1)) + current) / period
     * 4. RSI = 100 - (100 / (1 + RS))
     * 
     * @param series      DataSeries
     * @param index       اندیس جاری
     * @param period      دوره RSI (اعشاری)
     * @param avgGainKey  کلید ذخیره میانگین gain
     * @param avgLossKey  کلید ذخیره میانگین loss
     * @return            مقدار RSI (0-100)
     */
    private double calculateRSI(DataSeries series, int index, double period, 
                                Values avgGainKey, Values avgLossKey) {
        // Validate inputs - index باید حداقل 1 باشد تا بتوانیم prevClose را بخوانیم
        if (series == null || period < 2.0) {
            return Double.NaN;
        }
        
        // Early exit برای index نامعتبر
        if (index < 1 || index >= series.size()) {
            return Double.NaN;
        }
        
        int periodInt = (int) Math.ceil(period); // Use ceiling for lookback
        
        // Need at least 'period' bars for first calculation
        if (index < periodInt) {
            return Double.NaN;
        }
        
        // Get current price change
        float currentClose = series.getClose(index);
        float prevClose = series.getClose(index - 1);
        
        if (Float.isNaN(currentClose) || Float.isNaN(prevClose)) {
            return Double.NaN;
        }
        
        double change = currentClose - prevClose;
        double currentGain = (change > 0) ? change : 0.0;
        double currentLoss = (change < 0) ? -change : 0.0;
        
        double avgGain, avgLoss;
        
        // Check if we have previous smoothed values
        double prevAvgGain = series.getDouble(index - 1, avgGainKey, Double.NaN);
        double prevAvgLoss = series.getDouble(index - 1, avgLossKey, Double.NaN);
        
        if (Double.isNaN(prevAvgGain) || Double.isNaN(prevAvgLoss)) {
            // First calculation - use simple average of first 'period' bars
            if (index < periodInt) {
                return Double.NaN;
            }
            
            double sumGain = 0.0;
            double sumLoss = 0.0;
            int validCount = 0;
            int seriesSize = series.size();
            
            for (int i = index - periodInt + 1; i <= index; i++) {
                if (i < 1 || i >= seriesSize) continue;
                
                float close = series.getClose(i);
                float prev = series.getClose(i - 1);
                
                if (Float.isNaN(close) || Float.isNaN(prev)) continue;
                
                double chg = close - prev;
                if (chg > 0) {
                    sumGain += chg;
                } else {
                    sumLoss += (-chg);
                }
                validCount++;
            }
            
            if (validCount < periodInt / 2) {
                return Double.NaN; // Not enough valid data
            }
            
            avgGain = sumGain / period;
            avgLoss = sumLoss / period;
            
        } else {
            // Wilder's smoothing: ((prev * (period-1)) + current) / period
            avgGain = ((prevAvgGain * (period - 1.0)) + currentGain) / period;
            avgLoss = ((prevAvgLoss * (period - 1.0)) + currentLoss) / period;
        }
        
        // Store smoothed averages for next calculation
        series.setDouble(index, avgGainKey, avgGain);
        series.setDouble(index, avgLossKey, avgLoss);
        
        // Calculate RSI
        if (avgLoss < 1e-10) {
            // No losses - RSI = 100
            return 100.0;
        }
        
        double rs = avgGain / avgLoss;
        double rsi = 100.0 - (100.0 / (1.0 + rs));
        
        // Clamp to valid range [0, 100]
        return Math.max(0.0, Math.min(100.0, rsi));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // NORMALIZATION (Adaptive Scaling) - Optimized with Incremental Min/Max
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * نرمالایز کردن RSI برای نوسان کامل 0-100
     * بهینه‌سازی شده با Incremental Min/Max (مثل MT4)
     * 
     * @param series      DataSeries
     * @param index       اندیس جاری
     * @param rsiIndex    شماره RSI (0-4)
     * @param rawRsiKey   کلید RSI خام
     * @param lookback    بازه lookback برای min/max
     * @param currentRsi  مقدار RSI فعلی
     * @return            RSI نرمالایز شده (0-100)
     */
    private double normalizeRSI(DataSeries series, int index, int rsiIndex, Values rawRsiKey, 
                                int lookback, double currentRsi) {
        if (Double.isNaN(currentRsi)) {
            return Double.NaN;
        }
        
        // Need enough data for normalization
        if (index < lookback / 2) {
            return currentRsi;
        }
        
        // Get min/max keys for this RSI (only RSI 3,4,5 have them)
        Values minKey = MIN_VALUES[rsiIndex];
        Values maxKey = MAX_VALUES[rsiIndex];
        
        // If no min/max keys defined, do full scan (RSI 1,2 don't need normalization usually)
        if (minKey == null || maxKey == null) {
            return doFullScanNormalize(series, index, rawRsiKey, lookback, currentRsi);
        }
        
        double minRsi, maxRsi;
        
        // Try to use incremental update from previous bar
        if (index > 0) {
            double prevMin = series.getDouble(index - 1, minKey, Double.NaN);
            double prevMax = series.getDouble(index - 1, maxKey, Double.NaN);
            
            if (!Double.isNaN(prevMin) && !Double.isNaN(prevMax) && prevMin < 101) {
                // Incremental update
                minRsi = Math.min(prevMin, currentRsi);
                maxRsi = Math.max(prevMax, currentRsi);
                
                // Check if old value exits window - need rescan
                int oldIndex = index - lookback;
                if (oldIndex >= 0) {
                    double oldRsi = series.getDouble(oldIndex, rawRsiKey, Double.NaN);
                    if (!Double.isNaN(oldRsi) && (oldRsi <= minRsi || oldRsi >= maxRsi)) {
                        // Rescan needed - old value was min or max
                        minRsi = 100.0;
                        maxRsi = 0.0;
                        int actualLookback = Math.min(lookback, index + 1);
                        for (int i = 0; i < actualLookback; i++) {
                            double rsi = series.getDouble(index - i, rawRsiKey, Double.NaN);
                            if (!Double.isNaN(rsi)) {
                                if (rsi < minRsi) minRsi = rsi;
                                if (rsi > maxRsi) maxRsi = rsi;
                            }
                        }
                    }
                }
            } else {
                // Full scan for first calculation
                minRsi = 100.0;
                maxRsi = 0.0;
                int actualLookback = Math.min(lookback, index + 1);
                for (int i = 0; i < actualLookback; i++) {
                    double rsi = series.getDouble(index - i, rawRsiKey, Double.NaN);
                    if (!Double.isNaN(rsi)) {
                        if (rsi < minRsi) minRsi = rsi;
                        if (rsi > maxRsi) maxRsi = rsi;
                    }
                }
            }
        } else {
            minRsi = currentRsi;
            maxRsi = currentRsi;
        }
        
        // Store min/max for next bar's incremental update
        series.setDouble(index, minKey, minRsi);
        series.setDouble(index, maxKey, maxRsi);
        
        // Fallback if no valid data
        if (minRsi > maxRsi) {
            return currentRsi;
        }
        
        // Calculate range with protection
        double range = maxRsi - minRsi;
        if (range < MIN_RANGE) {
            range = MIN_RANGE;
        }
        
        // Normalize
        double normalized = ((currentRsi - minRsi) / range) * 100.0;
        
        // Clamp to 0-100
        return Math.max(0.0, Math.min(100.0, normalized));
    }
    
    /** Full scan normalization for RSIs without cached min/max */
    private double doFullScanNormalize(DataSeries series, int index, Values rawRsiKey, 
                                       int lookback, double currentRsi) {
        double minRsi = 100.0;
        double maxRsi = 0.0;
        int actualLookback = Math.min(lookback, index + 1);
        
        for (int i = 0; i < actualLookback; i++) {
            double rsi = series.getDouble(index - i, rawRsiKey, Double.NaN);
            if (!Double.isNaN(rsi)) {
                if (rsi < minRsi) minRsi = rsi;
                if (rsi > maxRsi) maxRsi = rsi;
            }
        }
        
        if (minRsi > maxRsi) return currentRsi;
        
        double range = maxRsi - minRsi;
        if (range < MIN_RANGE) range = MIN_RANGE;
        
        double normalized = ((currentRsi - minRsi) / range) * 100.0;
        return Math.max(0.0, Math.min(100.0, normalized));
    }
}
