const configuredHost = process.env.CONTEXTMESH_DEV_ALLOWED_ORIGIN_HOST;

if (configuredHost && !/^[a-z0-9.-]+(?::\d+)?$/i.test(configuredHost)) {
  throw new Error("CONTEXTMESH_DEV_ALLOWED_ORIGIN_HOST must be a hostname, optionally followed by a port");
}

/** @type {import('next').NextConfig} */
const nextConfig = {
  // Next.js uses hostnames here to protect development-only cross-origin asset requests.
  allowedDevOrigins: ["localhost", ...(configuredHost ? [configuredHost] : [])],
};

export default nextConfig;
