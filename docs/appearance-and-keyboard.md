# Appearance and keyboard

## Theme

Open **Settings** and choose a theme under **Display**: Dark (the default), Light, or System, which follows your operating system. The choice is remembered in this browser, and the page loads in the right theme without a flash.

The light theme is generated from the dark one, so there is only one stylesheet to maintain. `npm run theme` rebuilds `apps/web/src/app/light-theme.generated.css` from `apps/web/src/app/globals.css`. `npm run dev` and `npm run build` do it for you. If you edit `globals.css` while `npm run dev` is already running, run `npm run theme` again to see the change in light mode. Commit the generated file together with your change.

## Command palette

Press **Ctrl+K** (**⌘K** on a Mac) anywhere, or choose **Commands** at the bottom of the sidebar. Type to filter, use the arrow keys to move, **Enter** to run, **Esc** to close.

## Keyboard shortcuts

Press **?** to see this list in the app.

| Keys           | What it does              |
| -------------- | ------------------------- |
| `G` then `T`   | Open the timeline         |
| `G` then `R`   | Open regions              |
| `G` then `S`   | Open simulations          |
| `G` then `I`   | Open regional analysis    |
| `G` then `A`   | Open the alert center     |
| `G` then `C`   | Open the situation room   |
| `G` then `W`   | Open saved events         |
| `G` then `L`   | Open the activity log     |
| `R`            | Refresh data              |
| `/`            | Search hazards            |
| `T`            | Switch between dark and light |
| `,`            | Open settings             |
| `1` to `4`     | Show earthquakes, volcanoes, wildfires or storms |
| `?`            | Show the shortcut list    |

Single-key shortcuts do nothing while you are typing in a field or while a panel is open. If they get in your way, turn **Keyboard shortcuts** off under Settings, Display. Ctrl+K keeps working. The activity log shortcut and command are only offered to Operators and Administrators.
