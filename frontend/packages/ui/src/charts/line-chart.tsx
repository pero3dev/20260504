import {
  CartesianGrid,
  Legend,
  Line,
  LineChart as RechartsLineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

/**
 * @inventory/ui 共通の `<LineChart>` wrapper(ADR-0022 phase 1)。
 *
 * <p>recharts を直接使わずに本 wrapper を介すことで:
 *
 * <ul>
 *   <li>色 token は CSS 変数(`--primary` / `--border` 等)から引いて design system と整合
 *   <li>ResponsiveContainer 標準適用で web app 側の boilerplate を削減
 *   <li>将来 visx へ逃がす chart が出ても、 本 component の API を維持すれば call site は無修正
 * </ul>
 *
 * <p>大規模 time-series(>10k points)が必要になったら本 component に `mode="canvas"` を
 * 追加し recharts の SVG → Canvas 切替で対処する(visx 逃がしは個別 chart のみ)。
 */
export interface LineChartSeries {
  /** data point の key 名(`{ date: '...', revenue: 100 }` の `revenue` 部分) */
  dataKey: string;
  /** 凡例 / tooltip に表示するラベル(default: dataKey) */
  label?: string;
  /** 線色(CSS 変数 OK)。 default は `hsl(var(--primary))` */
  color?: string;
}

/**
 * Tooltip / YAxis に流す値整形 callback。 数値だけでなく series 名と data point も渡るので
 * 「currency なら通貨記号付き」「数量なら 件 を末尾」など unit を寄せやすい。
 */
export type LineChartValueFormatter<TPoint extends Record<string, unknown>> = (
  value: number,
  ctx: { dataKey: string; seriesLabel: string; point?: TPoint },
) => string;

export interface LineChartProps<TPoint extends Record<string, unknown>> {
  data: TPoint[];
  /** 横軸に使う key(時系列なら `date` 等) */
  xKey: keyof TPoint & string;
  series: LineChartSeries[];
  /** 高さ(px、 default 320) */
  height?: number;
  /** 凡例を表示するか(default true) */
  showLegend?: boolean;
  /** Tooltip / YAxis tick 値の表示整形(default は `Number.toLocaleString()`) */
  valueFormatter?: LineChartValueFormatter<TPoint>;
  /** Tooltip ヘッダ(x 値)の表示整形(default はそのまま文字列化) */
  labelFormatter?: (label: string | number) => string;
}

export function LineChart<TPoint extends Record<string, unknown>>({
  data,
  xKey,
  series,
  height = 320,
  showLegend = true,
  valueFormatter,
  labelFormatter,
}: LineChartProps<TPoint>) {
  const formatValue: LineChartValueFormatter<TPoint> =
    valueFormatter ?? ((value) => value.toLocaleString());
  const seriesLabelByKey = new Map(series.map((s) => [s.dataKey, s.label ?? s.dataKey]));

  return (
    <ResponsiveContainer width="100%" height={height}>
      <RechartsLineChart data={data} margin={{ top: 12, right: 16, bottom: 12, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis dataKey={xKey} stroke="hsl(var(--muted-foreground))" fontSize={12} />
        <YAxis
          stroke="hsl(var(--muted-foreground))"
          fontSize={12}
          tickFormatter={(v: number) =>
            formatValue(v, { dataKey: '', seriesLabel: '' })
          }
        />
        <Tooltip
          contentStyle={{
            backgroundColor: 'hsl(var(--background))',
            border: '1px solid hsl(var(--border))',
            borderRadius: '0.5rem',
            fontSize: '0.875rem',
          }}
          formatter={(value, name, item) => {
            const numeric = typeof value === 'number' ? value : Number(value);
            if (Number.isNaN(numeric)) {
              return [String(value), String(name)];
            }
            const dataKey = String((item as { dataKey?: unknown }).dataKey ?? name);
            const seriesLabel = seriesLabelByKey.get(dataKey) ?? String(name);
            const point = (item as { payload?: TPoint }).payload;
            return [
              formatValue(numeric, { dataKey, seriesLabel, ...(point ? { point } : {}) }),
              seriesLabel,
            ];
          }}
          labelFormatter={
            labelFormatter
              ? (label: string | number) => labelFormatter(label)
              : undefined
          }
        />
        {showLegend && <Legend wrapperStyle={{ fontSize: '0.75rem' }} />}
        {series.map((s) => (
          <Line
            key={s.dataKey}
            type="monotone"
            dataKey={s.dataKey}
            name={s.label ?? s.dataKey}
            stroke={s.color ?? 'hsl(var(--primary))'}
            strokeWidth={2}
            dot={false}
          />
        ))}
      </RechartsLineChart>
    </ResponsiveContainer>
  );
}
