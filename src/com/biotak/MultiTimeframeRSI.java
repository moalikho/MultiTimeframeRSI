package com.biotak;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;
import com.motivewave.platform.sdk.common.desc.*;

import java.awt.Color;

/**
 * Multi-Timeframe RSI Indicator
 * نمایش 5 RSI با دوره‌های مختلف به صورت همزمان
 * 
 * الگوریتم RSI:
 * RSI = 100 - (100 / (1 + RS))
 * RS = Average Gain / Average Loss
 * 
 * @author Biotak Development Team
 * @version 2.1.0
 */
@StudyHeader(
    namespace = "Biotak",
    id = "MTFRSI",
    name = "MTF RSI",
    label = "MTF RSI",
    desc = "Multi-Timeframe RSI Oscillator",
    menu = "Biotak",
    overlay = false,
    studyOverlay = false
)
public class MultiTimeframeRSI extends Study {
    
    enum Values { 
        RSI1, RSI2, RSI3, RSI4, RSI5,
        AVG_GAIN1, AVG_LOSS1, AVG_GAIN2, AVG_LOSS2, AVG_GAIN3, AVG_LOSS3,
        AVG_GAIN4, AVG_LOSS4, AVG_GAIN5, AVG_LOSS5
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SETTINGS CACHE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static class SettingsCache {
        private volatile SettingsSnapshot snapshot = null;
        private volatile int lastSettingsHash = 0;
        private final Object updateLock = new Object();
        
        private static class SettingsSnapshot {
            final int maxLookback;
            final boolean[] showFlags;
            final double[] rsiPeriods;
            final Enums.MAMethod rsiMethod;
            final int settingsHash;
            
            SettingsSnapshot(Settings settings) {
                this.maxLookback = settings.getInteger("maxLookback", DEFAULT_MAX_LOOKBACK);
                this.rsiMethod = settings.getMAMethod("rsiMethod", Enums.MAMethod.EMA);
                this.showFlags = new boolean[NUM_RSI];
                this.rsiPeriods = new double[NUM_RSI];
                
                int hash = 17;
                hash = 31 * hash + maxLookback;
                hash = 31 * hash + rsiMethod.ordinal();
                
                for (int i = 0; i < NUM_RSI; i++) {
                    showFlags[i] = settings.getBoolean(SHOW_KEYS[i], true);
                    rsiPeriods[i] = settings.getDouble(PERIOD_KEYS[i], DEFAULT_PERIODS[i]);
                    hash = 31 * hash + (showFlags[i] ? 1 : 0);
                    hash = 31 * hash + Double.hashCode(rsiPeriods[i]);
                }
                this.settingsHash = hash;
            }
        }
        
        SettingsSnapshot updateIfNeeded(Settings settings) {
            if (settings == null) return null;
            SettingsSnapshot current = snapshot;
            if (current != null) {
                int quickHash = computeQuickHash(settings);
                if (quickHash == lastSettingsHash) return current;
                synchronized (updateLock) {
                    if (snapshot != null && lastSettingsHash == quickHash) return snapshot;
                    current = new SettingsSnapshot(settings);
                    snapshot = current;
                    lastSettingsHash = current.settingsHash;
                    return current;
                }
            }
            synchronized (updateLock) {
                if (snapshot != null) return snapshot;
                current = new SettingsSnapshot(settings);
                snapshot = current;
                lastSettingsHash = current.settingsHash;
                return current;
            }
        }
        
        private int computeQuickHash(Settings settings) {
            int hash = 17;
            hash = 31 * hash + settings.getInteger("maxLookback", DEFAULT_MAX_LOOKBACK);
            hash = 31 * hash + settings.getMAMethod("rsiMethod", Enums.MAMethod.EMA).ordinal();
            for (int i = 0; i < NUM_RSI; i++) {
                hash = 31 * hash + (settings.getBoolean(SHOW_KEYS[i], true) ? 1 : 0);
                hash = 31 * hash + Double.hashCode(settings.getDouble(PERIOD_KEYS[i], DEFAULT_PERIODS[i]));
            }
            return hash;
        }
        
        void invalidate() {
            synchronized (updateLock) {
                snapshot = null;
                lastSettingsHash = 0;
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CALCULATION STATE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static class CalculationState {
        private final java.util.concurrent.atomic.AtomicInteger lastCalculatedIndex = 
            new java.util.concurrent.atomic.AtomicInteger(-1);
        private final java.util.concurrent.atomic.AtomicInteger currentMaxLookback = 
            new java.util.concurrent.atomic.AtomicInteger(-1);
        private final java.util.concurrent.atomic.AtomicInteger lastTotalBars = 
            new java.util.concurrent.atomic.AtomicInteger(0);
        private final Object stateLock = new Object();
        
        boolean shouldCalculate(int index, int totalBars, int maxLookback) {
            if (index < 0 || totalBars <= 0) return false;
            int calcStartIndex = (maxLookback > 0) ? Math.max(0, totalBars - maxLookback) : 0;
            
            synchronized (stateLock) {
                int prevMaxLookback = currentMaxLookback.get();
                int prevTotalBars = lastTotalBars.get();
                
                if (prevMaxLookback != maxLookback) {
                    currentMaxLookback.set(maxLookback);
                    lastTotalBars.set(totalBars);
                    lastCalculatedIndex.set(calcStartIndex - 1);
                } else if (totalBars != prevTotalBars) {
                    lastTotalBars.set(totalBars);
                    if (totalBars < prevTotalBars) lastCalculatedIndex.set(calcStartIndex - 1);
                }
            }
            
            if (index < calcStartIndex) return false;
            return index > lastCalculatedIndex.get() || index == totalBars - 1;
        }
        
        void markCalculated(int index) {
            lastCalculatedIndex.updateAndGet(current -> Math.max(current, index));
        }
        
        void reset() {
            synchronized (stateLock) {
                lastCalculatedIndex.set(-1);
                currentMaxLookback.set(-1);
                lastTotalBars.set(0);
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final int NUM_RSI = 5;
    private static final String[] PERIOD_KEYS = {"period1", "period2", "period3", "period4", "period5"};
    private static final String[] SHOW_KEYS = {"show1", "show2", "show3", "show4", "show5"};
    private static final Values[] RSI_VALUES = {Values.RSI1, Values.RSI2, Values.RSI3, Values.RSI4, Values.RSI5};
    private static final Values[] AVG_GAIN_VALUES = {Values.AVG_GAIN1, Values.AVG_GAIN2, Values.AVG_GAIN3, Values.AVG_GAIN4, Values.AVG_GAIN5};
    private static final Values[] AVG_LOSS_VALUES = {Values.AVG_LOSS1, Values.AVG_LOSS2, Values.AVG_LOSS3, Values.AVG_LOSS4, Values.AVG_LOSS5};
    private static final double[] DEFAULT_PERIODS = {5.0, 15.0, 30.0, 60.0, 240.0};
    
    private static final Color COLOR_GREEN = new Color(40, 140, 60);
    private static final Color COLOR_BLACK = new Color(40, 40, 40);
    private static final Color COLOR_BLUE = new Color(0, 0, 255);
    private static final Color COLOR_RED = new Color(240, 20, 20);
    private static final Color COLOR_DARK_GRAY = new Color(60, 60, 60);
    private static final Color COLOR_LIGHT_GRAY = new Color(140, 140, 140);
    private static final float[] DASH_4_3 = {4, 3};
    private static final float[] DASH_2_4 = {2, 4};
    private static final float[] DASH_4_4 = {4, 4};
    
    private static final String[] KEY_P1 = {"period1"};
    private static final String[] KEY_P2 = {"period2"};
    private static final String[] KEY_P3 = {"period3"};
    private static final String[] KEY_P4 = {"period4"};
    private static final String[] KEY_P5 = {"period5"};
    
    private final SettingsCache settingsCache = new SettingsCache();
    private final CalculationState calculationState = new CalculationState();
    private static final int DEFAULT_MAX_LOOKBACK = 1000;

    
    @Override
    public void clearState() {
        super.clearState();
        settingsCache.invalidate();
        calculationState.reset();
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
        
        // RSI Method Settings
        var methodGrp = tab.addGroup("RSI Calculation Method");
        methodGrp.addRow(new MAMethodDescriptor("rsiMethod", "Method", Enums.MAMethod.EMA));

        // RSI 240 - Green
        var grp5 = tab.addGroup("RSI 240 (Primary)");
        grp5.addRow(new BooleanDescriptor("show5", "Show RSI", false));
        grp5.addRow(new DoubleDescriptor("period5", "Period", 240.0, 2.0, 500.0, 0.01));
        grp5.addRow(new PathDescriptor("path5", "RSI Line", COLOR_GREEN, 3.0f, null, true, true, true));
        
        // RSI 60 - Black
        var grp4 = tab.addGroup("RSI 60 (Secondary)");
        grp4.addRow(new BooleanDescriptor("show4", "Show RSI", true));
        grp4.addRow(new DoubleDescriptor("period4", "Period", 60.0, 2.0, 300.0, 0.01));
        grp4.addRow(new PathDescriptor("path4", "RSI Line", COLOR_BLACK, 2.0f, null, true, true, true));
        
        // RSI 30 - Blue
        var grp3 = tab.addGroup("RSI 30 (Tertiary)");
        grp3.addRow(new BooleanDescriptor("show3", "Show RSI", true));
        grp3.addRow(new DoubleDescriptor("period3", "Period", 30.0, 2.0, 100.0, 0.01));
        grp3.addRow(new PathDescriptor("path3", "RSI Line", COLOR_BLUE, 1.8f, null, true, true, true));
        
        // RSI 15 - Red
        var grp2 = tab.addGroup("RSI 15 (Support)");
        grp2.addRow(new BooleanDescriptor("show2", "Show RSI", true));
        grp2.addRow(new DoubleDescriptor("period2", "Period", 15.0, 2.0, 100.0, 0.01));
        grp2.addRow(new PathDescriptor("path2", "RSI Line", COLOR_RED, 1.2f, DASH_4_3, true, true, true));
        
        // RSI 5 - Gray
        var grp1 = tab.addGroup("RSI 5 (Fast)");
        grp1.addRow(new BooleanDescriptor("show1", "Show RSI", false));
        grp1.addRow(new DoubleDescriptor("period1", "Period", 5.0, 2.0, 100.0, 0.01));
        grp1.addRow(new PathDescriptor("path1", "RSI Line", COLOR_DARK_GRAY, 1.0f, DASH_2_4, true, false, true));
        
        // Guides Tab
        var guidesTab = sd.addTab("Guides");
        var guidesGrp = guidesTab.addGroup("Horizontal Levels");
        
        GuideDescriptor zeroGuide = new GuideDescriptor("zeroGuide", "Zero (0)", 0, 0, 100, 1, true);
        zeroGuide.setLineColor(COLOR_LIGHT_GRAY);
        zeroGuide.setDash(DASH_4_4);
        zeroGuide.setWidth(0.5f);
        guidesGrp.addRow(zeroGuide);
        
        GuideDescriptor lowerGuide = new GuideDescriptor("lowerGuide", "Oversold (20)", 20, 0, 100, 1, true);
        lowerGuide.setLineColor(COLOR_LIGHT_GRAY);
        lowerGuide.setDash(DASH_4_4);
        guidesGrp.addRow(lowerGuide);
        
        GuideDescriptor middleGuide = new GuideDescriptor("middleGuide", "Middle (50)", 50, 0, 100, 1, true);
        middleGuide.setLineColor(COLOR_LIGHT_GRAY);
        middleGuide.setDash(DASH_4_4);
        guidesGrp.addRow(middleGuide);
        
        GuideDescriptor upperGuide = new GuideDescriptor("upperGuide", "Overbought (80)", 80, 0, 100, 1, true);
        upperGuide.setLineColor(COLOR_LIGHT_GRAY);
        upperGuide.setDash(DASH_4_4);
        guidesGrp.addRow(upperGuide);
        
        GuideDescriptor hundredGuide = new GuideDescriptor("hundredGuide", "Hundred (100)", 100, 0, 100, 1, true);
        hundredGuide.setLineColor(COLOR_LIGHT_GRAY);
        hundredGuide.setDash(DASH_4_4);
        hundredGuide.setWidth(0.5f);
        guidesGrp.addRow(hundredGuide);
        
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
        
        // Fixed 0-100 range - no auto-scaling
        desc.setFixedBottomValue(0);
        desc.setFixedTopValue(100);
        desc.setMaxBottomValue(0);
        desc.setMinTopValue(100);
        desc.setBottomInsetPixels(0);
        desc.setTopInsetPixels(0);
        
        desc.getDefaultPlot().declareGuide("zeroGuide");
        desc.getDefaultPlot().declareGuide("lowerGuide");
        desc.getDefaultPlot().declareGuide("middleGuide");
        desc.getDefaultPlot().declareGuide("upperGuide");
        desc.getDefaultPlot().declareGuide("hundredGuide");
        
        setRuntimeDescriptor(desc);
    }

    
    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN CALCULATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Override
    protected void calculate(int index, DataContext ctx) {
        DataSeries series = ctx.getDataSeries();
        if (series == null || series.size() == 0) return;
        
        int totalBars = series.size();
        SettingsCache.SettingsSnapshot settings = settingsCache.updateIfNeeded(getSettings());
        if (settings == null) return;
        
        if (!calculationState.shouldCalculate(index, totalBars, settings.maxLookback)) return;
        
        // Calculate all RSIs
        for (int i = 0; i < NUM_RSI; i++) {
            if (!settings.showFlags[i]) {
                series.setDouble(index, RSI_VALUES[i], Double.NaN);
                continue;
            }
            
            double rsi = calculateRSI(series, index, settings.rsiPeriods[i], 
                AVG_GAIN_VALUES[i], AVG_LOSS_VALUES[i], settings.rsiMethod);
            series.setDouble(index, RSI_VALUES[i], rsi);
        }
        
        calculationState.markCalculated(index);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // RSI CALCULATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private double calculateRSI(DataSeries series, int index, double period, 
                                Values avgGainKey, Values avgLossKey, Enums.MAMethod method) {
        if (series == null || period < 2.0 || index < 1) return Double.NaN;
        
        int periodInt = (int) Math.ceil(period);
        if (index < periodInt) return Double.NaN;
        
        float currentClose = series.getClose(index);
        float prevClose = series.getClose(index - 1);
        if (Float.isNaN(currentClose) || Float.isNaN(prevClose)) return Double.NaN;
        
        double change = currentClose - prevClose;
        double currentGain = (change > 0) ? change : 0.0;
        double currentLoss = (change < 0) ? -change : 0.0;
        
        double avgGain, avgLoss;
        double prevAvgGain = series.getDouble(index - 1, avgGainKey, Double.NaN);
        double prevAvgLoss = series.getDouble(index - 1, avgLossKey, Double.NaN);
        
        if (Double.isNaN(prevAvgGain) || Double.isNaN(prevAvgLoss)) {
            // First calculation - simple average
            double sumGain = 0.0, sumLoss = 0.0;
            for (int i = index - periodInt + 1; i <= index; i++) {
                if (i < 1) continue;
                float close = series.getClose(i);
                float prev = series.getClose(i - 1);
                if (Float.isNaN(close) || Float.isNaN(prev)) continue;
                double chg = close - prev;
                if (chg > 0) sumGain += chg;
                else sumLoss += (-chg);
            }
            avgGain = sumGain / period;
            avgLoss = sumLoss / period;
        } else {
            // Apply smoothing method
            switch (method) {
                case SMA:
                    double sumGain = 0.0, sumLoss = 0.0;
                    for (int i = index - periodInt + 1; i <= index; i++) {
                        if (i < 1) continue;
                        float close = series.getClose(i);
                        float prev = series.getClose(i - 1);
                        if (Float.isNaN(close) || Float.isNaN(prev)) continue;
                        double chg = close - prev;
                        if (chg > 0) sumGain += chg;
                        else sumLoss += (-chg);
                    }
                    avgGain = sumGain / period;
                    avgLoss = sumLoss / period;
                    break;
                case SMMA:
                    avgGain = ((prevAvgGain * (period - 1.0)) + currentGain) / period;
                    avgLoss = ((prevAvgLoss * (period - 1.0)) + currentLoss) / period;
                    break;
                case WMA:
                    double wSumGain = 0.0, wSumLoss = 0.0, wSum = 0.0;
                    for (int i = 0; i < periodInt; i++) {
                        int idx = index - periodInt + 1 + i;
                        if (idx < 1) continue;
                        float close = series.getClose(idx);
                        float prev = series.getClose(idx - 1);
                        if (Float.isNaN(close) || Float.isNaN(prev)) continue;
                        double w = i + 1;
                        double chg = close - prev;
                        if (chg > 0) wSumGain += chg * w;
                        else wSumLoss += (-chg) * w;
                        wSum += w;
                    }
                    avgGain = wSum > 0 ? wSumGain / wSum : 0;
                    avgLoss = wSum > 0 ? wSumLoss / wSum : 0;
                    break;
                default: // EMA
                    double alpha = 2.0 / (period + 1.0);
                    avgGain = (alpha * currentGain) + ((1.0 - alpha) * prevAvgGain);
                    avgLoss = (alpha * currentLoss) + ((1.0 - alpha) * prevAvgLoss);
                    break;
            }
        }
        
        series.setDouble(index, avgGainKey, avgGain);
        series.setDouble(index, avgLossKey, avgLoss);
        
        if (avgLoss < 1e-10) return 100.0;
        double rs = avgGain / avgLoss;
        return Math.max(0.0, Math.min(100.0, 100.0 - (100.0 / (1.0 + rs))));
    }
}
