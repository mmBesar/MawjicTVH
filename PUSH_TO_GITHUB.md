# Publish the clean repository

The playback-ready bundle contains this clean Tvheadend client and its complete Git history. It does not contain the previous renamed IPTV source.

After deleting the old GitHub repository and recreating `mmBesar/MawjicTVH` as an **empty** repository:

```bash
git clone MawjicTVH-0.1.0-playback-ready.bundle MawjicTVH
cd MawjicTVH
git remote set-url origin git@github.com:mmBesar/MawjicTVH.git
git push -u origin main
```

Or extract `MawjicTVH-0.1.0-playback-ready.zip`, initialize Git, commit, add the remote, and push. The bundle method preserves the prepared three-commit history.

The Android application ID is `app.mawjic.tvh`, so it installs as a separate app and will not update or replace `app.mawjic.tv`.

Linux playback requires the system `mpv` executable. See `README.md` before installing the Linux package.
