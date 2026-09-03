/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        paper: "#f3eee4",
        ink: "#1a1714",
        muted: "#6b645c",
        line: "#ddd4c8",
        card: "#fffaf3",
        pine: {
          DEFAULT: "#0f5c56",
          dark: "#0a3f3b",
          light: "#e4f1ef",
        },
        clay: "#c45c26",
        rust: "#9f1239",
      },
      fontFamily: {
        display: ["Fraunces", "Georgia", "serif"],
        sans: ["Source Sans 3", "Segoe UI", "sans-serif"],
      },
      boxShadow: {
        card: "0 1px 0 rgba(26, 23, 20, 0.06), 0 12px 32px -18px rgba(26, 23, 20, 0.25)",
      },
    },
  },
  plugins: [],
};
