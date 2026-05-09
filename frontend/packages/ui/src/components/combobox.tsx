import { useState, type ReactNode } from 'react';

import { cn } from '../lib/cn.js';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from './command.js';
import { Popover, PopoverContent, PopoverTrigger } from './popover.js';

/**
 * 値選択 + 検索 (autosuggest) UI。 SKU / 取引先 / location 等、 候補が多くて native `<Select>`
 * では捌けないケースで使う。
 *
 * <p>キー操作: Trigger 押下で Popover 展開、 input に focus、 Down/Up で候補移動、 Enter で確定、
 * ESC で閉じる(全て cmdk + Radix が担保)。
 *
 * <p>使用例(controlled):
 * <pre>{@code
 * const items = [{ value: 'SKU-0001', label: 'SKU-0001 - 牛乳 1L' }, ...];
 * const [value, setValue] = useState<string | null>(null);
 *
 * <Combobox
 *   items={items}
 *   value={value}
 *   onChange={setValue}
 *   placeholder="SKU を選択..."
 *   searchPlaceholder="SKU を検索..."
 *   emptyMessage="該当なし"
 * />
 * }</pre>
 */
export interface ComboboxItem {
  value: string;
  label: ReactNode;
  /** 検索 match 用の文字列(label が ReactNode の時に必要)。 default は value */
  keywords?: string[];
  disabled?: boolean;
}

export interface ComboboxProps {
  items: ComboboxItem[];
  value: string | null;
  onChange: (value: string | null) => void;
  /** Trigger に未選択時表示する文言 */
  placeholder: string;
  /** Command Input の placeholder */
  searchPlaceholder: string;
  /** 結果 0 件時の文言 */
  emptyMessage: string;
  /** Trigger button の追加 className */
  triggerClassName?: string;
  /** Popover content の追加 className(width 調整等) */
  contentClassName?: string;
  disabled?: boolean;
  /** aria-label(label 要素を別途置かない時に必須) */
  ariaLabel?: string;
}

export function Combobox({
  items,
  value,
  onChange,
  placeholder,
  searchPlaceholder,
  emptyMessage,
  triggerClassName,
  contentClassName,
  disabled,
  ariaLabel,
}: ComboboxProps) {
  const [open, setOpen] = useState(false);
  const selected = items.find((item) => item.value === value) ?? null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        type="button"
        disabled={disabled}
        aria-label={ariaLabel ?? placeholder}
        aria-expanded={open}
        className={cn(
          'inline-flex h-9 w-full items-center justify-between rounded-md border border-border bg-background px-3 py-1 text-sm text-left transition-opacity hover:opacity-90 disabled:pointer-events-none disabled:opacity-50',
          triggerClassName,
        )}
      >
        <span className={cn(!selected && 'text-muted-foreground')}>
          {selected ? selected.label : placeholder}
        </span>
        <span className="ml-2 text-xs opacity-60" aria-hidden>
          ▼
        </span>
      </PopoverTrigger>
      <PopoverContent className={cn('w-72 p-0', contentClassName)} align="start">
        <Command>
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            <CommandEmpty>{emptyMessage}</CommandEmpty>
            <CommandGroup>
              {items.map((item) => (
                <CommandItem
                  key={item.value}
                  value={item.value}
                  keywords={item.keywords ?? [item.value]}
                  disabled={item.disabled ?? false}
                  onSelect={(picked) => {
                    onChange(picked === value ? null : picked);
                    setOpen(false);
                  }}
                >
                  <span className="flex-1">{item.label}</span>
                  {value === item.value && (
                    <span className="ml-2 text-xs" aria-hidden>
                      ✓
                    </span>
                  )}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
