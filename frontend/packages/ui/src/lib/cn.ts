import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * shadcn/ui 標準の className マージヘルパー。 clsx で truthy のみ繋いで twMerge で
 * Tailwind の重複(例: `px-2 px-4`)を後勝ち解決する。
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
