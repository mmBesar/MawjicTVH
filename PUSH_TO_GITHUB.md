# Publish the clean repository

The deliverable bundle contains only this clean project and its new history. It does not contain the previous renamed IPTV source.

After deleting the old GitHub repository and recreating `mmBesar/MawjicTVH` as an **empty** repository:

```bash
git clone MawjicTVH.bundle MawjicTVH
cd MawjicTVH
git remote set-url origin git@github.com:mmBesar/MawjicTVH.git
git push -u origin main
```

Or extract `MawjicTVH-source.zip`, initialize Git, commit, add the remote, and push. The bundle method preserves the prepared commit history.

The Android application ID is `app.mawjic.tvh`, so it installs as a separate app and will not update or replace `app.mawjic.tv`.
