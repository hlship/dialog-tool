/** @type {import('tailwindcss').Config} */

import flowbitePlugin from 'flowbite/plugin'

import type { Config } from 'tailwindcss';

export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx,svelte}",
    "./node_modules/flowbite-svelte/**/*.{html,js,svelte,ts}"
  ],
  safelist: [
    "bg-yellow-200", "border-yellow-200",
    "bg-rose-400", "border-rose-400",
    "bg-stone-200", "border-stone-200",
    ],
  theme: {
    extend: {},
  },
  plugins: [flowbitePlugin]
}

