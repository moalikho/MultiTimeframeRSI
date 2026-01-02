//+------------------------------------------------------------------+
//|                                                    Multi-RSI.mq4 |
//+------------------------------------------------------------------+
#property link          "Multi-Timeframe RSI Oscillator"
#property version       "1.01"
#property strict
#property copyright     "Multi-TF RSI - 2025"
#property description   "5 RSI oscillators"
#property indicator_separate_window
#property indicator_buffers 5
#property indicator_plots 5

// RSI 1 - Period 5 - DARK GRAY (Subtle)
#property indicator_color1 0x3C3C3C
#property indicator_type1 DRAW_LINE
#property indicator_width1 1
#property indicator_style1 STYLE_DOT
#property indicator_label1 "RSI5"

// RSI 2 - Period 15 - RED
#property indicator_color2 0x1414F0
#property indicator_type2 DRAW_LINE
#property indicator_width2 1
#property indicator_style2 STYLE_DASH
#property indicator_label2 "RSI15"

// RSI 3 - Period 30 - BLUE
#property indicator_color3 0xFF0000
#property indicator_type3 DRAW_LINE
#property indicator_width3 2
#property indicator_style3 STYLE_SOLID
#property indicator_label3 "RSI30"

// RSI 4 - Period 60 - BLACK
#property indicator_color4 0x282828
#property indicator_type4 DRAW_LINE
#property indicator_width4 3
#property indicator_style4 STYLE_DASH
#property indicator_label4 "RSI60"

// RSI 5 - Period 240 - GREEN
#property indicator_color5 0x3C8C28
#property indicator_type5 DRAW_LINE
#property indicator_width5 3
#property indicator_style5 STYLE_SOLID
#property indicator_label5 "RSI240"

// Reference Lines
#property indicator_level1 80.0
#property indicator_level2 50.0
#property indicator_level3 20.0
#property indicator_levelcolor clrGray
#property indicator_levelstyle STYLE_DOT

// Fixed Scale 0-100
#property indicator_minimum 0
#property indicator_maximum 100


//+------------------------------------------------------------------+
//| Input Parameters                                                  |
//+------------------------------------------------------------------+
input string Comment0="════════════════════════";   //═══ CALCULATION METHOD ═══
enum ENUM_RSI_METHOD { RSI_RMA=0, RSI_EMA=1, RSI_SMA=2, RSI_WMA=3 };
input ENUM_RSI_METHOD RSI_Method=RSI_RMA;         //Method (RMA=Wilder/MotiveWave)
input ENUM_APPLIED_PRICE GlobalAppliedPrice=PRICE_CLOSE; //Applied Price (All RSIs)

input string Comment1="════════════════════════";   //═══ RSI 1 SETTINGS ═══
input int RSI1_Period=5;                          //Period
input color RSI1_Color=0x3C3C3C;                  //Color (Dark Gray)
input int RSI1_Width=1;                           //Width (1-5)
input ENUM_LINE_STYLE RSI1_Style=STYLE_DOT;       //Style
input bool ShowRSI1=true;                         //Show

input string Comment2="════════════════════════";   //═══ RSI 2 SETTINGS ═══
input int RSI2_Period=15;                         //Period
input color RSI2_Color=0x1414F0;                  //Color (Red)
input int RSI2_Width=1;                           //Width (1-5)
input ENUM_LINE_STYLE RSI2_Style=STYLE_DASH;      //Style
input bool ShowRSI2=true;                         //Show

input string Comment3="════════════════════════";   //═══ RSI 3 SETTINGS ═══
input int RSI3_Period=30;                         //Period
input color RSI3_Color=0xFF0000;                  //Color (Blue)
input int RSI3_Width=2;                           //Width (1-5)
input ENUM_LINE_STYLE RSI3_Style=STYLE_SOLID;     //Style
input bool ShowRSI3=true;                         //Show

input string Comment4="════════════════════════";   //═══ RSI 4 SETTINGS ═══
input int RSI4_Period=60;                         //Period
input color RSI4_Color=0x282828;                  //Color (Black)
input int RSI4_Width=3;                           //Width (1-5)
input ENUM_LINE_STYLE RSI4_Style=STYLE_DASH;      //Style
input bool ShowRSI4=true;                         //Show

input string Comment5="════════════════════════";   //═══ RSI 5 SETTINGS ═══
input int RSI5_Period=240;                        //Period
input color RSI5_Color=0x3C8C28;                  //Color (Green)
input int RSI5_Width=3;                           //Width (1-5)
input ENUM_LINE_STYLE RSI5_Style=STYLE_SOLID;     //Style
input bool ShowRSI5=true;                         //Show

input string Comment6="════════════════════════";   //═══ OVERBOUGHT/OVERSOLD ═══
input double OverboughtLevel=80;                  //Overbought Level
input double OversoldLevel=20;                    //Oversold Level
input bool UseDynamicColors=false;                //Dynamic Colors
input color OverboughtColor=clrYellow;            //Overbought Color
input color OversoldColor=clrRed;                 //Oversold Color

input string Comment7="════════════════════════";   //═══ DISPLAY OPTIONS ═══
input bool ShowDataOnChart=true;                  //Show Values on Chart
input int CornerPosition=1;                       //Corner (0=TL,1=TR,2=BL,3=BR)
input bool ShowToggleButtons=true;                //Show Toggle Buttons

input string Comment8="════════════════════════";   //═══ ADVANCED ═══
input bool UseIndividualPrice=false;              //Use Individual Applied Prices
input ENUM_APPLIED_PRICE RSI1_AppliedPrice=PRICE_CLOSE; //RSI 1 Applied Price
input ENUM_APPLIED_PRICE RSI2_AppliedPrice=PRICE_CLOSE; //RSI 2 Applied Price
input ENUM_APPLIED_PRICE RSI3_AppliedPrice=PRICE_CLOSE; //RSI 3 Applied Price
input ENUM_APPLIED_PRICE RSI4_AppliedPrice=PRICE_CLOSE; //RSI 4 Applied Price
input ENUM_APPLIED_PRICE RSI5_AppliedPrice=PRICE_CLOSE; //RSI 5 Applied Price

//+------------------------------------------------------------------+
//| Buffers                                                           |
//+------------------------------------------------------------------+
double RSI1_Buffer[];
double RSI2_Buffer[];
double RSI3_Buffer[];
double RSI4_Buffer[];
double RSI5_Buffer[];

// State for incremental RSI calculation (non-series arrays - index 0 = oldest)
// We only need to store the last calculated AvgGain/AvgLoss for each RSI
double g_LastAvgGain1, g_LastAvgLoss1;
double g_LastAvgGain2, g_LastAvgLoss2;
double g_LastAvgGain3, g_LastAvgLoss3;
double g_LastAvgGain4, g_LastAvgLoss4;
double g_LastAvgGain5, g_LastAvgLoss5;

// Track last calculated bar time to detect new bars
datetime g_LastBarTime = 0;

// Track if initial calculation is done
bool g_InitialCalcDone = false;

// Global toggle states (modifiable)
bool g_ShowRSI1 = true;
bool g_ShowRSI2 = true;
bool g_ShowRSI3 = true;
bool g_ShowRSI4 = true;
bool g_ShowRSI5 = true;

// Menu collapse state
bool g_MenuExpanded = false;

// Anti-double-click protection
ulong g_LastMenuClickMs = 0;

// Unique identifier for this chart
string g_ChartPrefix = "";

// Arrays for RSI calculation state (non-series - index from old to new)
double AvgGain1[], AvgLoss1[];
double AvgGain2[], AvgLoss2[];
double AvgGain3[], AvgLoss3[];
double AvgGain4[], AvgLoss4[];
double AvgGain5[], AvgLoss5[];

//+------------------------------------------------------------------+
//| Calculate RSI using stored state                                  |
//+------------------------------------------------------------------+
double CalculateRSI(int barIndex, int period, ENUM_APPLIED_PRICE appliedPrice,
                    double &avgGain[], double &avgLoss[])
{
   if(barIndex >= Bars - period - 1) return EMPTY_VALUE;
   
   double currentClose = GetPrice(appliedPrice, barIndex);
   double prevClose = GetPrice(appliedPrice, barIndex + 1);
   if(currentClose == 0 || prevClose == 0) return EMPTY_VALUE;
   
   double change = currentClose - prevClose;
   double gain = (change > 0) ? change : 0;
   double loss = (change < 0) ? -change : 0;
   
   double ag, al;
   
   // Check if we have previous values
   if(avgGain[barIndex + 1] == EMPTY_VALUE || avgLoss[barIndex + 1] == EMPTY_VALUE)
   {
      // First calculation - simple average
      double sumGain = 0, sumLoss = 0;
      for(int i = barIndex + period - 1; i >= barIndex; i--)
      {
         if(i >= Bars - 1 || i < 0) continue;
         double c = GetPrice(appliedPrice, i);
         double p = GetPrice(appliedPrice, i + 1);
         if(c == 0 || p == 0) continue;
         double chg = c - p;
         if(chg > 0) sumGain += chg;
         else sumLoss += (-chg);
      }
      ag = sumGain / period;
      al = sumLoss / period;
   }
   else
   {
      double prevAG = avgGain[barIndex + 1];
      double prevAL = avgLoss[barIndex + 1];
      
      switch(RSI_Method)
      {
         case RSI_SMA:
         {
            double sumGain = 0, sumLoss = 0;
            for(int i = barIndex + period - 1; i >= barIndex; i--)
            {
               if(i >= Bars - 1 || i < 0) continue;
               double c = GetPrice(appliedPrice, i);
               double p = GetPrice(appliedPrice, i + 1);
               if(c == 0 || p == 0) continue;
               double chg = c - p;
               if(chg > 0) sumGain += chg;
               else sumLoss += (-chg);
            }
            ag = sumGain / period;
            al = sumLoss / period;
            break;
         }
         case RSI_WMA:
         {
            double wSumGain = 0, wSumLoss = 0, wSum = 0;
            int weight = 1;
            for(int i = barIndex + period - 1; i >= barIndex; i--)
            {
               if(i >= Bars - 1 || i < 0) { weight++; continue; }
               double c = GetPrice(appliedPrice, i);
               double p = GetPrice(appliedPrice, i + 1);
               if(c == 0 || p == 0) { weight++; continue; }
               double chg = c - p;
               if(chg > 0) wSumGain += chg * weight;
               else wSumLoss += (-chg) * weight;
               wSum += weight;
               weight++;
            }
            ag = (wSum > 0) ? wSumGain / wSum : 0;
            al = (wSum > 0) ? wSumLoss / wSum : 0;
            break;
         }
         case RSI_EMA:
         {
            double alpha = 2.0 / (period + 1.0);
            ag = (alpha * gain) + ((1.0 - alpha) * prevAG);
            al = (alpha * loss) + ((1.0 - alpha) * prevAL);
            break;
         }
         default: // RSI_RMA
         {
            double alpha = 1.0 / period;
            ag = (alpha * gain) + ((1.0 - alpha) * prevAG);
            al = (alpha * loss) + ((1.0 - alpha) * prevAL);
            break;
         }
      }
   }
   
   avgGain[barIndex] = ag;
   avgLoss[barIndex] = al;
   
   if(al < 0.0000001) return 100.0;
   double rs = ag / al;
   double rsi = 100.0 - (100.0 / (1.0 + rs));
   return MathMax(0, MathMin(100, rsi));
}

//+------------------------------------------------------------------+
//| Get price based on applied price type                             |
//+------------------------------------------------------------------+
double GetPrice(ENUM_APPLIED_PRICE priceType, int index)
{
   switch(priceType)
   {
      case PRICE_OPEN:    return Open[index];
      case PRICE_HIGH:    return High[index];
      case PRICE_LOW:     return Low[index];
      case PRICE_MEDIAN:  return (High[index] + Low[index]) / 2;
      case PRICE_TYPICAL: return (High[index] + Low[index] + Close[index]) / 3;
      case PRICE_WEIGHTED: return (High[index] + Low[index] + Close[index] + Close[index]) / 4;
      default:            return Close[index];
   }
}

//+------------------------------------------------------------------+
//| Custom indicator initialization function                          |
//+------------------------------------------------------------------+
int OnInit()
{
   IndicatorSetString(INDICATOR_SHORTNAME, "Multi-TF RSI");
   IndicatorSetInteger(INDICATOR_DIGITS, 1);

   // Reset calculation state
   g_LastBarTime = 0;
   g_InitialCalcDone = false;

   // Create unique prefix for this chart
   g_ChartPrefix = "RSI_" + IntegerToString(ChartID()) + "_";

   // Delete old buttons first (they may have stale state from previous timeframe)
   DeleteAllButtons();

   // Load saved state from GlobalVariables (persists across timeframes)
   LoadState();

   // Buffer 0: RSI 5
   SetIndexBuffer(0, RSI1_Buffer);
   ArraySetAsSeries(RSI1_Buffer, true);
   SetIndexLabel(0, "RSI" + IntegerToString(RSI1_Period));
   SetIndexStyle(0, DRAW_LINE, RSI1_Style, RSI1_Width, g_ShowRSI1 ? RSI1_Color : clrNONE);

   // Buffer 1: RSI 15
   SetIndexBuffer(1, RSI2_Buffer);
   ArraySetAsSeries(RSI2_Buffer, true);
   SetIndexLabel(1, "RSI" + IntegerToString(RSI2_Period));
   SetIndexStyle(1, DRAW_LINE, RSI2_Style, RSI2_Width, g_ShowRSI2 ? RSI2_Color : clrNONE);

   // Buffer 2: RSI 30
   SetIndexBuffer(2, RSI3_Buffer);
   ArraySetAsSeries(RSI3_Buffer, true);
   SetIndexLabel(2, "RSI" + IntegerToString(RSI3_Period));
   SetIndexStyle(2, DRAW_LINE, RSI3_Style, RSI3_Width, g_ShowRSI3 ? RSI3_Color : clrNONE);

   // Buffer 3: RSI 60
   SetIndexBuffer(3, RSI4_Buffer);
   ArraySetAsSeries(RSI4_Buffer, true);
   SetIndexLabel(3, "RSI" + IntegerToString(RSI4_Period));
   SetIndexStyle(3, DRAW_LINE, RSI4_Style, RSI4_Width, g_ShowRSI4 ? RSI4_Color : clrNONE);

   // Buffer 4: RSI 240
   SetIndexBuffer(4, RSI5_Buffer);
   ArraySetAsSeries(RSI5_Buffer, true);
   SetIndexLabel(4, "RSI" + IntegerToString(RSI5_Period));
   SetIndexStyle(4, DRAW_LINE, RSI5_Style, RSI5_Width, g_ShowRSI5 ? RSI5_Color : clrNONE);

   // Create toggle buttons
   if(ShowToggleButtons)
   {
      CreateMainMenuButton();
      if(g_MenuExpanded)
         CreateToggleButtons();
   }

   return(INIT_SUCCEEDED);
}


//+------------------------------------------------------------------+
//| Custom indicator iteration function                               |
//+------------------------------------------------------------------+
int OnCalculate(const int rates_total,
                const int prev_calculated,
                const datetime &time[],
                const double &open[],
                const double &high[],
                const double &low[],
                const double &close[],
                const long &tick_volume[],
                const long &volume[],
                const int &spread[])
{
   // Check minimum bars requirement
   int minBars = MathMax(RSI5_Period, 150);
   if(rates_total < minBars) return(0);

   // Detect new bar using bar time
   datetime currentBarTime = Time[0];
   bool isNewBar = (currentBarTime != g_LastBarTime);
   if(isNewBar) g_LastBarTime = currentBarTime;
   
   // Determine if full recalculation is needed
   bool needFullRecalc = (prev_calculated == 0) || !g_InitialCalcDone || isNewBar;

   // Resize and initialize helper arrays when needed
   static int lastRatesTotal = 0;
   if(rates_total != lastRatesTotal || needFullRecalc)
   {
      ArrayResize(AvgGain1, rates_total); ArrayResize(AvgLoss1, rates_total);
      ArrayResize(AvgGain2, rates_total); ArrayResize(AvgLoss2, rates_total);
      ArrayResize(AvgGain3, rates_total); ArrayResize(AvgLoss3, rates_total);
      ArrayResize(AvgGain4, rates_total); ArrayResize(AvgLoss4, rates_total);
      ArrayResize(AvgGain5, rates_total); ArrayResize(AvgLoss5, rates_total);
      
      ArraySetAsSeries(AvgGain1, true); ArraySetAsSeries(AvgLoss1, true);
      ArraySetAsSeries(AvgGain2, true); ArraySetAsSeries(AvgLoss2, true);
      ArraySetAsSeries(AvgGain3, true); ArraySetAsSeries(AvgLoss3, true);
      ArraySetAsSeries(AvgGain4, true); ArraySetAsSeries(AvgLoss4, true);
      ArraySetAsSeries(AvgGain5, true); ArraySetAsSeries(AvgLoss5, true);
      
      ArrayInitialize(AvgGain1, EMPTY_VALUE); ArrayInitialize(AvgLoss1, EMPTY_VALUE);
      ArrayInitialize(AvgGain2, EMPTY_VALUE); ArrayInitialize(AvgLoss2, EMPTY_VALUE);
      ArrayInitialize(AvgGain3, EMPTY_VALUE); ArrayInitialize(AvgLoss3, EMPTY_VALUE);
      ArrayInitialize(AvgGain4, EMPTY_VALUE); ArrayInitialize(AvgLoss4, EMPTY_VALUE);
      ArrayInitialize(AvgGain5, EMPTY_VALUE); ArrayInitialize(AvgLoss5, EMPTY_VALUE);
      
      lastRatesTotal = rates_total;
   }

   // Determine Applied Prices
   ENUM_APPLIED_PRICE ap1 = UseIndividualPrice ? RSI1_AppliedPrice : GlobalAppliedPrice;
   ENUM_APPLIED_PRICE ap2 = UseIndividualPrice ? RSI2_AppliedPrice : GlobalAppliedPrice;
   ENUM_APPLIED_PRICE ap3 = UseIndividualPrice ? RSI3_AppliedPrice : GlobalAppliedPrice;
   ENUM_APPLIED_PRICE ap4 = UseIndividualPrice ? RSI4_AppliedPrice : GlobalAppliedPrice;
   ENUM_APPLIED_PRICE ap5 = UseIndividualPrice ? RSI5_AppliedPrice : GlobalAppliedPrice;

   // Calculate limit
   int limit;
   if(needFullRecalc)
      limit = rates_total - RSI5_Period - 10;
   else
      limit = 0;
   
   if(limit < 0) limit = 0;

   // Calculate from oldest to newest
   for(int i = limit; i >= 0; i--)
   {
      RSI1_Buffer[i] = CalculateRSI(i, RSI1_Period, ap1, AvgGain1, AvgLoss1);
      RSI2_Buffer[i] = CalculateRSI(i, RSI2_Period, ap2, AvgGain2, AvgLoss2);
      RSI3_Buffer[i] = CalculateRSI(i, RSI3_Period, ap3, AvgGain3, AvgLoss3);
      RSI4_Buffer[i] = CalculateRSI(i, RSI4_Period, ap4, AvgGain4, AvgLoss4);
      RSI5_Buffer[i] = CalculateRSI(i, RSI5_Period, ap5, AvgGain5, AvgLoss5);
   }
   
   g_InitialCalcDone = true;

   // Update UI every tick
   if(UseDynamicColors)
      ApplyDynamicColors();
   if(ShowDataOnChart)
      DisplayDataOnChart();

   return(rates_total);
}


//+------------------------------------------------------------------+
//| Apply Dynamic Colors                                              |
//+------------------------------------------------------------------+
void ApplyDynamicColors()
{
   static int lastState1 = -1, lastState2 = -1, lastState3 = -1, lastState4 = -1, lastState5 = -1;
   int state;
   
   if(g_ShowRSI1)
   {
      state = (RSI1_Buffer[0] >= OverboughtLevel) ? 1 : (RSI1_Buffer[0] <= OversoldLevel) ? 2 : 0;
      if(state != lastState1)
      {
         if(state == 1) SetIndexStyle(0, DRAW_LINE, RSI1_Style, RSI1_Width + 1, OverboughtColor);
         else if(state == 2) SetIndexStyle(0, DRAW_LINE, RSI1_Style, RSI1_Width + 1, OversoldColor);
         else SetIndexStyle(0, DRAW_LINE, RSI1_Style, RSI1_Width, RSI1_Color);
         lastState1 = state;
      }
   }

   if(g_ShowRSI2)
   {
      state = (RSI2_Buffer[0] >= OverboughtLevel) ? 1 : (RSI2_Buffer[0] <= OversoldLevel) ? 2 : 0;
      if(state != lastState2)
      {
         if(state == 1) SetIndexStyle(1, DRAW_LINE, RSI2_Style, RSI2_Width + 1, OverboughtColor);
         else if(state == 2) SetIndexStyle(1, DRAW_LINE, RSI2_Style, RSI2_Width + 1, OversoldColor);
         else SetIndexStyle(1, DRAW_LINE, RSI2_Style, RSI2_Width, RSI2_Color);
         lastState2 = state;
      }
   }

   if(g_ShowRSI3)
   {
      state = (RSI3_Buffer[0] >= OverboughtLevel) ? 1 : (RSI3_Buffer[0] <= OversoldLevel) ? 2 : 0;
      if(state != lastState3)
      {
         if(state == 1) SetIndexStyle(2, DRAW_LINE, RSI3_Style, RSI3_Width + 1, OverboughtColor);
         else if(state == 2) SetIndexStyle(2, DRAW_LINE, RSI3_Style, RSI3_Width + 1, OversoldColor);
         else SetIndexStyle(2, DRAW_LINE, RSI3_Style, RSI3_Width, RSI3_Color);
         lastState3 = state;
      }
   }

   if(g_ShowRSI4)
   {
      state = (RSI4_Buffer[0] >= OverboughtLevel) ? 1 : (RSI4_Buffer[0] <= OversoldLevel) ? 2 : 0;
      if(state != lastState4)
      {
         if(state == 1) SetIndexStyle(3, DRAW_LINE, RSI4_Style, RSI4_Width + 1, OverboughtColor);
         else if(state == 2) SetIndexStyle(3, DRAW_LINE, RSI4_Style, RSI4_Width + 1, OversoldColor);
         else SetIndexStyle(3, DRAW_LINE, RSI4_Style, RSI4_Width, RSI4_Color);
         lastState4 = state;
      }
   }

   if(g_ShowRSI5)
   {
      state = (RSI5_Buffer[0] >= OverboughtLevel) ? 1 : (RSI5_Buffer[0] <= OversoldLevel) ? 2 : 0;
      if(state != lastState5)
      {
         if(state == 1) SetIndexStyle(4, DRAW_LINE, RSI5_Style, RSI5_Width + 1, OverboughtColor);
         else if(state == 2) SetIndexStyle(4, DRAW_LINE, RSI5_Style, RSI5_Width + 1, OversoldColor);
         else SetIndexStyle(4, DRAW_LINE, RSI5_Style, RSI5_Width, RSI5_Color);
         lastState5 = state;
      }
   }
}

//+------------------------------------------------------------------+
//| Display Data on Chart                                             |
//+------------------------------------------------------------------+
void DisplayDataOnChart()
{
   // Build object name (g_ChartPrefix may change on reinit)
   string objName = g_ChartPrefix + "RSIData";
   
   if(g_ChartPrefix == "") return;  // Safety check
   
   // Create object if not exists
   if(ObjectFind(0, objName) < 0)
   {
      ObjectCreate(0, objName, OBJ_LABEL, ChartWindowFind(), 0, 0);
      ObjectSetInteger(0, objName, OBJPROP_CORNER, CornerPosition);
      ObjectSetInteger(0, objName, OBJPROP_XDISTANCE, 10);
      ObjectSetInteger(0, objName, OBJPROP_YDISTANCE, 20);
      ObjectSetInteger(0, objName, OBJPROP_COLOR, clrWhite);
      ObjectSetInteger(0, objName, OBJPROP_FONTSIZE, 8);
      ObjectSetString(0, objName, OBJPROP_FONT, "Courier New");
   }

   // Build text
   string text = "\n=== MULTI-TIMEFRAME RSI ===\n";
   text += StringFormat("RSI %d: %.1f\n", RSI1_Period, RSI1_Buffer[0]);
   text += StringFormat("RSI %d: %.1f\n", RSI2_Period, RSI2_Buffer[0]);
   text += StringFormat("RSI %d: %.1f\n", RSI3_Period, RSI3_Buffer[0]);
   text += StringFormat("RSI %d: %.1f\n", RSI4_Period, RSI4_Buffer[0]);
   text += StringFormat("RSI %d: %.1f\n", RSI5_Period, RSI5_Buffer[0]);

   // Overbought/Oversold count
   int overbought = 0, oversold = 0;
   if(RSI1_Buffer[0] >= OverboughtLevel) overbought++;
   if(RSI2_Buffer[0] >= OverboughtLevel) overbought++;
   if(RSI3_Buffer[0] >= OverboughtLevel) overbought++;
   if(RSI4_Buffer[0] >= OverboughtLevel) overbought++;
   if(RSI5_Buffer[0] >= OverboughtLevel) overbought++;

   if(RSI1_Buffer[0] <= OversoldLevel) oversold++;
   if(RSI2_Buffer[0] <= OversoldLevel) oversold++;
   if(RSI3_Buffer[0] <= OversoldLevel) oversold++;
   if(RSI4_Buffer[0] <= OversoldLevel) oversold++;
   if(RSI5_Buffer[0] <= OversoldLevel) oversold++;
   
   if(overbought >= 3)
      text += "\nOVERBOUGHT (" + IntegerToString(overbought) + "/5)";
   else if(oversold >= 3)
      text += "\nOVERSOLD (" + IntegerToString(oversold) + "/5)";
   else if(overbought >= 1 || oversold >= 1)
      text += "\nDIVERGENCE POSSIBLE";

   ObjectSetString(0, objName, OBJPROP_TEXT, text);
}


//+------------------------------------------------------------------+
//| Create Main Menu Button                                          |
//+------------------------------------------------------------------+
void CreateMainMenuButton()
{
   int btnWidth = 45;
   int btnHeight = 18;
   int startX = 10;
   int startY = 25;

   string btnName = g_ChartPrefix + "Btn_MainMenu";

   if(ObjectFind(0, btnName) >= 0)
      ObjectDelete(0, btnName);

   ObjectCreate(0, btnName, OBJ_BUTTON, ChartWindowFind(), 0, 0);
   ObjectSetInteger(0, btnName, OBJPROP_CORNER, CORNER_LEFT_UPPER);
   ObjectSetInteger(0, btnName, OBJPROP_XDISTANCE, startX);
   ObjectSetInteger(0, btnName, OBJPROP_YDISTANCE, startY);
   ObjectSetInteger(0, btnName, OBJPROP_XSIZE, btnWidth);
   ObjectSetInteger(0, btnName, OBJPROP_YSIZE, btnHeight);
   ObjectSetString(0, btnName, OBJPROP_TEXT, g_MenuExpanded ? "HIDE" : "RSI");
   ObjectSetInteger(0, btnName, OBJPROP_FONTSIZE, 7);
   ObjectSetString(0, btnName, OBJPROP_FONT, "Arial Bold");
   ObjectSetInteger(0, btnName, OBJPROP_COLOR, clrWhite);
   ObjectSetInteger(0, btnName, OBJPROP_BGCOLOR, g_MenuExpanded ? clrCrimson : clrRoyalBlue);
   ObjectSetInteger(0, btnName, OBJPROP_BORDER_COLOR, clrGray);
   ObjectSetInteger(0, btnName, OBJPROP_BACK, false);
   ObjectSetInteger(0, btnName, OBJPROP_STATE, false);
   ObjectSetInteger(0, btnName, OBJPROP_SELECTABLE, false);
   ObjectSetInteger(0, btnName, OBJPROP_HIDDEN, true);
   ObjectSetInteger(0, btnName, OBJPROP_ZORDER, 0);
}

//+------------------------------------------------------------------+
//| Create Toggle Buttons                                             |
//+------------------------------------------------------------------+
void CreateToggleButtons()
{
   int btnWidth = 45;
   int btnHeight = 18;
   int spacing = 2;
   int startX = 65;
   int startY = 25;

   // RSI buttons (reversed order: 240->5)
   string buttons[5];
   buttons[0] = "Btn_R240";
   buttons[1] = "Btn_R60";
   buttons[2] = "Btn_R30";
   buttons[3] = "Btn_R15";
   buttons[4] = "Btn_R5";

   int periods[5];
   periods[0] = RSI5_Period;   // 240
   periods[1] = RSI4_Period;   // 60
   periods[2] = RSI3_Period;   // 30
   periods[3] = RSI2_Period;   // 15
   periods[4] = RSI1_Period;   // 5

   color bgColors[5];
   bgColors[0] = RSI5_Color;   // 240 - Green
   bgColors[1] = RSI4_Color;   // 60 - Black
   bgColors[2] = RSI3_Color;   // 30 - Blue
   bgColors[3] = RSI2_Color;   // 15 - Red
   bgColors[4] = RSI1_Color;   // 5 - Dark Gray

   bool states[5];
   states[0] = g_ShowRSI5;   // 240
   states[1] = g_ShowRSI4;   // 60
   states[2] = g_ShowRSI3;   // 30
   states[3] = g_ShowRSI2;   // 15
   states[4] = g_ShowRSI1;   // 5

   for(int i = 0; i < 5; i++)
   {
      string btnName = g_ChartPrefix + buttons[i];
      ObjectCreate(0, btnName, OBJ_BUTTON, ChartWindowFind(), 0, 0);
      ObjectSetInteger(0, btnName, OBJPROP_CORNER, CORNER_LEFT_UPPER);
      ObjectSetInteger(0, btnName, OBJPROP_XDISTANCE, startX + (btnWidth + spacing) * i);
      ObjectSetInteger(0, btnName, OBJPROP_YDISTANCE, startY);
      ObjectSetInteger(0, btnName, OBJPROP_XSIZE, btnWidth);
      ObjectSetInteger(0, btnName, OBJPROP_YSIZE, btnHeight);
      ObjectSetString(0, btnName, OBJPROP_TEXT, "R:" + IntegerToString(periods[i]));
      ObjectSetInteger(0, btnName, OBJPROP_FONTSIZE, 7);
      ObjectSetString(0, btnName, OBJPROP_FONT, "Arial Bold");
      ObjectSetInteger(0, btnName, OBJPROP_COLOR, clrWhite);
      ObjectSetInteger(0, btnName, OBJPROP_BGCOLOR, states[i] ? bgColors[i] : clrBlack);
      ObjectSetInteger(0, btnName, OBJPROP_BORDER_COLOR, clrDimGray);
      ObjectSetInteger(0, btnName, OBJPROP_BACK, false);
      ObjectSetInteger(0, btnName, OBJPROP_STATE, false);
      ObjectSetInteger(0, btnName, OBJPROP_SELECTABLE, false);
      ObjectSetInteger(0, btnName, OBJPROP_HIDDEN, true);
      ObjectSetInteger(0, btnName, OBJPROP_ZORDER, 0);
   }
}


//+------------------------------------------------------------------+
//| OnChartEvent - Handle button clicks instantly                     |
//+------------------------------------------------------------------+
void OnChartEvent(const int id,
                  const long &lparam,
                  const double &dparam,
                  const string &sparam)
{
   // CHARTEVENT_OBJECT_CLICK = 1
   if(id == 1)
   {
      // Handle main menu toggle
      if(sparam == g_ChartPrefix + "Btn_MainMenu")
      {
         // Anti-double-click: prevent rapid clicks (300ms)
         ulong currentTimeMs = GetTickCount();
         if(currentTimeMs - g_LastMenuClickMs < 300)
         {
            ObjectSetInteger(0, sparam, OBJPROP_STATE, false);
            return;
         }
         g_LastMenuClickMs = currentTimeMs;

         ObjectSetInteger(0, sparam, OBJPROP_STATE, false);
         g_MenuExpanded = !g_MenuExpanded;
         SaveState();

         DeleteAllButtons();
         CreateMainMenuButton();
         if(g_MenuExpanded)
            CreateToggleButtons();
         ChartRedraw();
         return;
      }

      // Reset button state for all buttons
      ObjectSetInteger(0, sparam, OBJPROP_STATE, false);

      if(sparam == g_ChartPrefix + "Btn_R240")
         ToggleRSI(5);
      else if(sparam == g_ChartPrefix + "Btn_R60")
         ToggleRSI(4);
      else if(sparam == g_ChartPrefix + "Btn_R30")
         ToggleRSI(3);
      else if(sparam == g_ChartPrefix + "Btn_R15")
         ToggleRSI(2);
      else if(sparam == g_ChartPrefix + "Btn_R5")
         ToggleRSI(1);
   }
}

//+------------------------------------------------------------------+
//| Toggle RSI Display                                                |
//+------------------------------------------------------------------+
void ToggleRSI(int rsiNum)
{
   int bufferIndex = rsiNum - 1;
   string btnName = g_ChartPrefix + "Btn_R" + IntegerToString(
      (rsiNum == 1) ? RSI1_Period :
      (rsiNum == 2) ? RSI2_Period :
      (rsiNum == 3) ? RSI3_Period :
      (rsiNum == 4) ? RSI4_Period : RSI5_Period);

   switch(rsiNum)
   {
      case 1:
         g_ShowRSI1 = !g_ShowRSI1;
         if(g_ShowRSI1)
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI1_Style, RSI1_Width, RSI1_Color);
         else
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI1_Style, RSI1_Width, clrNONE);
         ObjectSetInteger(0, btnName, OBJPROP_BGCOLOR, g_ShowRSI1 ? RSI1_Color : clrBlack);
         break;
      case 2:
         g_ShowRSI2 = !g_ShowRSI2;
         if(g_ShowRSI2)
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI2_Style, RSI2_Width, RSI2_Color);
         else
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI2_Style, RSI2_Width, clrNONE);
         ObjectSetInteger(0, btnName, OBJPROP_BGCOLOR, g_ShowRSI2 ? RSI2_Color : clrBlack);
         break;
      case 3:
         g_ShowRSI3 = !g_ShowRSI3;
         if(g_ShowRSI3)
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI3_Style, RSI3_Width, RSI3_Color);
         else
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI3_Style, RSI3_Width, clrNONE);
         ObjectSetInteger(0, btnName, OBJPROP_BGCOLOR, g_ShowRSI3 ? RSI3_Color : clrBlack);
         break;
      case 4:
         g_ShowRSI4 = !g_ShowRSI4;
         if(g_ShowRSI4)
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI4_Style, RSI4_Width, RSI4_Color);
         else
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI4_Style, RSI4_Width, clrNONE);
         ObjectSetInteger(0, btnName, OBJPROP_BGCOLOR, g_ShowRSI4 ? RSI4_Color : clrBlack);
         break;
      case 5:
         g_ShowRSI5 = !g_ShowRSI5;
         if(g_ShowRSI5)
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI5_Style, RSI5_Width, RSI5_Color);
         else
            SetIndexStyle(bufferIndex, DRAW_LINE, RSI5_Style, RSI5_Width, clrNONE);
         ObjectSetInteger(0, btnName, OBJPROP_BGCOLOR, g_ShowRSI5 ? RSI5_Color : clrBlack);
         break;
   }

   SaveState();
   
   // Force indicator window to redraw
   ChartRedraw(0);
   
   // Also redraw the indicator subwindow specifically
   int window = ChartWindowFind(0, "Multi-TF RSI");
   if(window >= 0)
      ChartRedraw(0);
}


//+------------------------------------------------------------------+
//| Delete All Buttons                                               |
//+------------------------------------------------------------------+
void DeleteAllButtons()
{
   ObjectDelete(0, g_ChartPrefix + "Btn_MainMenu");
   ObjectDelete(0, g_ChartPrefix + "Btn_R5");
   ObjectDelete(0, g_ChartPrefix + "Btn_R15");
   ObjectDelete(0, g_ChartPrefix + "Btn_R30");
   ObjectDelete(0, g_ChartPrefix + "Btn_R60");
   ObjectDelete(0, g_ChartPrefix + "Btn_R240");
}

//+------------------------------------------------------------------+
//| Load State - Global Variables (persists across MT4 restarts)      |
//+------------------------------------------------------------------+
void LoadState()
{
   // Use Symbol only (not timeframe) so settings apply to all timeframes
   string prefix = "MTFRSI_" + Symbol() + "_";
   
   if(GlobalVariableCheck(prefix + "RSI1"))
   {
      g_ShowRSI1 = (GlobalVariableGet(prefix + "RSI1") > 0);
      g_ShowRSI2 = (GlobalVariableGet(prefix + "RSI2") > 0);
      g_ShowRSI3 = (GlobalVariableGet(prefix + "RSI3") > 0);
      g_ShowRSI4 = (GlobalVariableGet(prefix + "RSI4") > 0);
      g_ShowRSI5 = (GlobalVariableGet(prefix + "RSI5") > 0);
      g_MenuExpanded = (GlobalVariableGet(prefix + "MenuExpanded") > 0);
      
      // Debug: Print loaded state
      Print("LoadState: RSI1=", g_ShowRSI1, " RSI2=", g_ShowRSI2, " RSI3=", g_ShowRSI3, 
            " RSI4=", g_ShowRSI4, " RSI5=", g_ShowRSI5, " Menu=", g_MenuExpanded);
   }
   else
   {
      // First run - use input defaults
      g_ShowRSI1 = ShowRSI1;
      g_ShowRSI2 = ShowRSI2;
      g_ShowRSI3 = ShowRSI3;
      g_ShowRSI4 = ShowRSI4;
      g_ShowRSI5 = ShowRSI5;
      g_MenuExpanded = false;
      
      // Save initial state
      SaveState();
      Print("LoadState: First run, using defaults");
   }
}

//+------------------------------------------------------------------+
//| Save State - Global Variables (persists across MT4 restarts)      |
//+------------------------------------------------------------------+
void SaveState()
{
   // Use Symbol only (not timeframe) so settings apply to all timeframes
   string prefix = "MTFRSI_" + Symbol() + "_";
   
   GlobalVariableSet(prefix + "RSI1", g_ShowRSI1 ? 1 : 0);
   GlobalVariableSet(prefix + "RSI2", g_ShowRSI2 ? 1 : 0);
   GlobalVariableSet(prefix + "RSI3", g_ShowRSI3 ? 1 : 0);
   GlobalVariableSet(prefix + "RSI4", g_ShowRSI4 ? 1 : 0);
   GlobalVariableSet(prefix + "RSI5", g_ShowRSI5 ? 1 : 0);
   GlobalVariableSet(prefix + "MenuExpanded", g_MenuExpanded ? 1 : 0);
   
   // Debug: Print saved state
   Print("SaveState: RSI1=", g_ShowRSI1, " RSI2=", g_ShowRSI2, " RSI3=", g_ShowRSI3, 
         " RSI4=", g_ShowRSI4, " RSI5=", g_ShowRSI5, " Menu=", g_MenuExpanded);
}

//+------------------------------------------------------------------+
//| OnDeinit                                                          |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
{
   // Debug: Print reason
   Print("OnDeinit: reason=", reason, " (1=REMOVE, 3=CHARTCHANGE, 4=CLOSE)");
   
   // Always save state before cleanup
   SaveState();

   // Clean up chart objects
   ObjectDelete(0, g_ChartPrefix + "RSIData");
   DeleteAllButtons();

   // Only delete GlobalVariables when indicator is manually removed (not on timeframe change or MT4 close)
   // REASON_REMOVE = 1, but we keep data for MT4 restart
   // REASON_CHARTCHANGE = 3 (timeframe change) - keep data
   // REASON_CLOSE = 4 (MT4 closing) - keep data
   // Only delete on explicit removal AND user confirmation would be needed
   // For now, keep all data to persist across restarts
}



