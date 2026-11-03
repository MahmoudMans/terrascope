export const OPEN_ACTIVITY_EVENT = "terrascope:open-activity";

export function requestActivityLog(): void {
  window.dispatchEvent(new Event(OPEN_ACTIVITY_EVENT));
}
