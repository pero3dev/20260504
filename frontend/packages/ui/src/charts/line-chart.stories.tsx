import type { Meta, StoryObj } from '@storybook/react';

import { LineChart, type LineChartProps } from './line-chart.js';

interface TrendPoint extends Record<string, unknown> {
  date: string;
  inbound: number;
  outbound: number;
}

const data: TrendPoint[] = [
  { date: '5/2', inbound: 120, outbound: 80 },
  { date: '5/3', inbound: 90, outbound: 110 },
  { date: '5/4', inbound: 140, outbound: 60 },
  { date: '5/5', inbound: 70, outbound: 100 },
  { date: '5/6', inbound: 160, outbound: 90 },
  { date: '5/7', inbound: 100, outbound: 120 },
  { date: '5/8', inbound: 130, outbound: 95 },
];

/**
 * `LineChart` は generic component なので、 Storybook の Meta 推論を働かせるため
 * specialized wrapper を経由する(generic 化されたままだと argTypes が unknown に潰れる)。
 *
 * <p>recharts は ResponsiveContainer の親要素サイズを基準に描画するため、
 * 親に明示的な高さ / 幅を持たせる decorator が必要。
 */
function LineChartStory(props: LineChartProps<TrendPoint>) {
  return <LineChart<TrendPoint> {...props} />;
}

// 注意: `satisfies Meta<...>` で書くと decorators の inferred 型が Storybook の
// 内部 csf module(.pnpm 配下)を参照して TS2742(portable でない)で落ちるため、
// explicit annotation を使う。
const meta: Meta<typeof LineChartStory> = {
  title: 'Charts/LineChart',
  component: LineChartStory,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <div className="w-[600px] p-6">
        <Story />
      </div>
    ),
  ],
};

export default meta;
type Story = StoryObj<typeof LineChartStory>;

export const Default: Story = {
  args: {
    data,
    xKey: 'date',
    series: [
      { dataKey: 'inbound', label: '入庫' },
      { dataKey: 'outbound', label: '出庫' },
    ],
    height: 240,
  },
};

export const WithUnitFormatter: Story = {
  args: {
    data,
    xKey: 'date',
    series: [{ dataKey: 'inbound', label: '入庫' }],
    height: 240,
    valueFormatter: (value) => `${value.toLocaleString()} 個`,
  },
};
