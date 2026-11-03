import type {
  Metadata,
} from "next";

import "leaflet/dist/leaflet.css";
import "./globals.css";
import "./light-theme.generated.css";

import { THEME_INIT_SCRIPT } from "@/lib/theme";

export const metadata: Metadata = {
  title:
    "TerraScope | Earth Monitoring Platform",
  description:
    "Interactive natural disaster and Earth monitoring platform.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>

      <body>{children}</body>
    </html>
  );
}
