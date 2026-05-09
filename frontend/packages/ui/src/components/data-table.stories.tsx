import type { Meta, StoryObj } from '@storybook/react';

import { DataTable, type DataTableColumn } from './data-table.js';

interface InventoryRow {
  id: string;
  sku: string;
  location: string;
  available: number;
  reserved: number;
}

const rows: InventoryRow[] = [
  { id: '1', sku: 'SKU-0001', location: 'TOKYO-WH-1', available: 100, reserved: 12 },
  { id: '2', sku: 'SKU-0002', location: 'TOKYO-WH-1', available: 80, reserved: 0 },
  { id: '3', sku: 'SKU-0003', location: 'OSAKA-WH-1', available: 250, reserved: 30 },
];

const columns: DataTableColumn<InventoryRow>[] = [
  { header: 'SKU', render: (r) => r.sku },
  { header: 'Location', render: (r) => r.location },
  {
    header: 'Available',
    className: 'text-right',
    render: (r) => r.available.toLocaleString(),
  },
  {
    header: 'Reserved',
    className: 'text-right',
    render: (r) => r.reserved.toLocaleString(),
  },
];

interface DataTableInventoryProps {
  columns: DataTableColumn<InventoryRow>[];
  rows: InventoryRow[];
  rowKey: (row: InventoryRow) => string;
}

/** generic 解消用 wrapper(LineChart story と同じ理由) */
function DataTableInventory(props: DataTableInventoryProps) {
  return <DataTable<InventoryRow> {...props} />;
}

const meta = {
  title: 'Components/DataTable',
  component: DataTableInventory,
  tags: ['autodocs'],
} satisfies Meta<typeof DataTableInventory>;

export default meta;
type Story = StoryObj<typeof meta>;

export const InventoryList: Story = {
  args: {
    columns,
    rows,
    rowKey: (r) => r.id,
  },
};

export const Empty: Story = {
  args: {
    columns,
    rows: [],
    rowKey: (r) => r.id,
  },
};
