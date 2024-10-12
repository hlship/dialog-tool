/** @type {import('tailwindcss').Config} */

import flowbitePlugin from 'flowbite/plugin'

import type { Config } from 'tailwindcss';

export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx,svelte}",
    "./node_modules/flowbite-svelte/**/*.{html,js,svelte,ts}",
    "./node_modules/flowbite-svelte-icons/**/*.{html,js,svelte,ts}"
  ],
  theme: {
    extend: {},
  },
  safelist: [],

  plugins: [flowbitePlugin]
}

