import type { Metadata } from "next";
import "./styles.css";

export const metadata: Metadata = { title: "ContextMesh", description: "ContextMesh development status" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}

