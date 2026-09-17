import type {
  Metadata,
} from "next";

import "leaflet/dist/leaflet.css";
import "./globals.css";

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
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
