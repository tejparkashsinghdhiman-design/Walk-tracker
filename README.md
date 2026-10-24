# Walk Tracker

A single-file web app that tracks steps and distance while you walk.

- Steps counted with the phone's motion sensor (adaptive peak detection with cadence validation)
- Distance from GPS, with Kalman smoothing and noise/jump filtering
- Falls back to stride-based distance when GPS drops out (indoors, treadmill)
- Stride auto-calibrates from GPS after walks of 150+ steps
- Daily step goal, 7-day chart, streaks, calories and walk history
- Crash recovery: an unfinished walk is restored after a reload
- Data stays in your browser (localStorage); nothing is uploaded

## Run it

GPS and motion sensors only work on secure (HTTPS) pages, so opening `index.html` straight from storage may not work on a phone. Host it instead:

1. Repo **Settings > Pages**
2. Source: **Deploy from a branch**, branch `main`, folder `/ (root)`
3. Open the `https://<your-username>.github.io/Walk-tracker/` link on your phone

Keep the page open with the screen on while walking; browsers pause sensors when the screen is off.
