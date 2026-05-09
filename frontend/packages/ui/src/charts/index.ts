// `@inventory/ui/charts` subpath エントリ。 web app は import { LineChart } from '@inventory/ui/charts'。
//
// recharts(主)を本 wrapper の背後で使う。 visx 逃がしが必要な個別 chart は
// 本 directory に追加するが、 wrapper の API を維持して call site への影響を最小化する。
export { LineChart, type LineChartProps, type LineChartSeries } from './line-chart.js';
