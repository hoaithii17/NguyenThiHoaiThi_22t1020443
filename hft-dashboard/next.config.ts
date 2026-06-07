import type { NextConfig } from "next";

const API_URL = process.env.API_URL ?? "https://localhost:8443";

const nextConfig: NextConfig = {
  reactCompiler: true,
  async rewrites() {
    // IMPORTANT: use `fallback` (step 8 in routing order) so that the dynamic
    // route handler at src/app/api/stream/[...path]/route.ts (step 7) is
    // checked first.  If we used the default array form (afterFiles, step 6),
    // Next.js would buffer the SSE response before the route handler could
    // intercept it, resulting in 0 bytes delivered to the browser.
    return {
      beforeFiles: [],
      afterFiles: [],
      fallback: [
        {
          source: "/api/:path*",
          destination: `${API_URL}/api/:path*`,
        },
      ],
    };
  },
};

export default nextConfig;
