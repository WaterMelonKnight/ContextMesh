"use client";

import { useEffect, useState } from "react";

type Health = { status: string; application: string; database: string };

export default function Home() {
  const [health, setHealth] = useState<Health | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"}/api/v1/health`, { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error("Backend unavailable");
        return response.json() as Promise<Health>;
      })
      .then(setHealth)
      .catch(() => setHealth(null));
    return () => controller.abort();
  }, []);

  return (
    <main>
      <section>
        <p className="eyebrow">Development foundation</p>
        <h1>ContextMesh</h1>
        <p className="tagline">Turn AI conversations into evolving context.</p>
        <dl>
          <div><dt>Backend</dt><dd data-up={health?.status === "UP"}>{health?.status === "UP" ? "healthy" : "unavailable"}</dd></div>
          <div><dt>Database</dt><dd data-up={health?.database === "UP"}>{health?.database === "UP" ? "healthy" : "unavailable"}</dd></div>
        </dl>
      </section>
    </main>
  );
}

