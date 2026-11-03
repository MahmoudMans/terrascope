import {
  AuthGate,
} from "@/components/auth-gate";

import {
  DisasterDashboard,
} from "@/components/disaster-dashboard";

export default function Home() {
  return (
    <AuthGate>
      <DisasterDashboard />
    </AuthGate>
  );
}
