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

export interface LineChartProps<TPoint extends Record<string, unknown>> {
  data: TPoint[];
  /** 横軸に使う key(時系列なら `date` 等) */
  xKey: keyof TPoint & string;
  series: LineChartSeries[];
  /** 高さ(px、 default 320) */
  height?: number;
  /** 凡例を表示するか(default true) */
  showLegend?: boolean;
}

export function LineChart<TPoint extends Record<string, unknown>>({
  data,
  xKey,
  series,
  height = 320,
  showLegend = true,
}: LineChartProps<TPoint>) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <RechartsLineChart data={data} margin={{ top: 12, right: 16, bottom: 12, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis dataKey={xKey} stroke="hsl(var(--muted-foreground))" fontSize={12} />
        <YAxis stroke="hsl(var(--muted-foreground))" fontSize={12} />
        <Tooltip
          contentStyle={{
            backgroundColor: 'hsl(var(--background))',
            border: '1px solid hsl(var(--border))',
            borderRadius: '0.5rem',
            fontSize: '0.875rem',
          }}
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
