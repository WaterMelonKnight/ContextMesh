// CONTEXTMESH_INTERNAL_API_ORIGIN is read here only, without a NEXT_PUBLIC_ prefix, so it stays a
// server-side setting: the rewrite runs inside the Next.js server and the value never reaches the
// browser bundle.
const DEFAULT_INTERNAL_API_ORIGIN = "http://127.0.0.1:8080";

const internalApiOrigin = validateInternalApiOrigin(
  process.env.CONTEXTMESH_INTERNAL_API_ORIGIN ?? DEFAULT_INTERNAL_API_ORIGIN,
);

const configuredHost = process.env.CONTEXTMESH_DEV_ALLOWED_ORIGIN_HOST;

if (configuredHost && !/^[a-z0-9.-]+(?::\d+)?$/i.test(configuredHost)) {
  throw new Error("CONTEXTMESH_DEV_ALLOWED_ORIGIN_HOST must be a hostname, optionally followed by a port");
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  // Next.js uses hostnames here to protect development-only cross-origin asset requests.
  allowedDevOrigins: ["localhost", ...(configuredHost ? [configuredHost] : [])],
  // Keeps every browser API call same-origin, so no public backend origin and no CORS are needed.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${internalApiOrigin}/api/:path*` }];
  },
};

export default nextConfig;

/** @param {string} value */
function validateInternalApiOrigin(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    url = undefined;
  }
  if (
    !url
    || !["http:", "https:"].includes(url.protocol)
    || url.username
    || url.password
    || url.origin !== value.replace(/\/$/, "")
  ) {
    throw new Error(
      "CONTEXTMESH_INTERNAL_API_ORIGIN must be an HTTP(S) origin without a path, query, fragment, or credentials: "
        + value,
    );
  }
  return url.origin;
}
