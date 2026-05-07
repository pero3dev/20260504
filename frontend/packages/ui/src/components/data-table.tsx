import type { ReactNode } from 'react';

export interface DataTableColumn<T> {
  /** 列の見出し(<th> 内に出る文字列)。 */
  header: string;
  /** 各行の cell の中身を返す関数。 row オブジェクトから自由に文字列 / ReactNode を作る。 */
  render: (row: T) => ReactNode;
  /** ヘッダ + cell の独自クラス(列ごとの右寄せ等)。 */
  className?: string;
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
}

/**
 * 汎用 generic な表示 table。 sort / pagination / virtualization は F5(design system 拡張)で
 * 別 component(`PaginatedDataTable` 等)として追加する想定で、 本コンポーネントは「読みやすく出すだけ」に絞る。
 */
export function DataTable<T>({ columns, rows, rowKey }: DataTableProps<T>) {
  return (
    <table className="w-full overflow-hidden rounded-lg border border-border text-sm">
      <thead className="bg-muted text-left">
        <tr>
          {columns.map((c) => (
            <th key={c.header} className={`px-4 py-2 font-medium ${c.className ?? ''}`}>
              {c.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={rowKey(r)} className="border-t border-border">
            {columns.map((c) => (
              <td key={c.header} className={`px-4 py-2 ${c.className ?? ''}`}>
                {c.render(r)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
