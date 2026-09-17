import type { LucideIcon } from "lucide-react";

type StatCardProps = {
  icon: LucideIcon;
  label: string;
  value: string;
  detail: string;
  tone?: "default" | "warning" | "danger";
};

export function StatCard({
  icon: Icon,
  label,
  value,
  detail,
  tone = "default",
}: StatCardProps) {
  return (
    <article className={`stat-card stat-card--${tone}`}>
      <div className="stat-card__top">
        <span className="stat-card__label">
          {label}
        </span>

        <span className="stat-card__icon">
          <Icon size={17} strokeWidth={1.8} />
        </span>
      </div>

      <strong className="stat-card__value">
        {value}
      </strong>

      <span className="stat-card__detail">
        {detail}
      </span>
    </article>
  );
}
