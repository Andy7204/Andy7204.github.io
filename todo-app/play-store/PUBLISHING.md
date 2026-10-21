# Publishing Todo on Google Play – step by step

## 1. One-time: create your private upload key
Play builds must be signed with a key only you have. (The `todo-release.jks` in the repo is
public and only for sideloaded APKs.)

```sh
cd todo-app
./scripts/create-upload-key.sh ~/upload-key.jks
```

Then in GitHub → **Settings → Secrets and variables → Actions → New repository secret**, add:
`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`.

Back up `upload-key.jks` and its password in a password manager. If you lose it you can ask Google
to reset it, but it takes days.

## 2. Build the bundle
Push any change to `todo-app/` (or run the **Build Todo app** workflow manually from the Actions
tab). With the secrets present, the run produces a **Todo-play-bundle** artifact containing
`Todo-play.aab`. Download it from the workflow run page.

## 3. Create the app in Play Console
1. Pay the one-time $25 developer registration and verify your identity.
2. **Create app** → name “Todo – Tasks & Reminders”, App, Free.
3. Accept **Play App Signing** (Google holds the final signing key; you sign uploads with yours).
4. Fill in **App content** using the answers in `LISTING.md`.
5. Fill in the **Main store listing** with the text and graphics from `LISTING.md`.

> The package name is `com.andy.todo`. It's permanent once uploaded – if Play says it's taken,
> change `applicationId` in `app/build.gradle` before the first upload.

## 4. Closed test (required for new personal accounts)
Google requires **at least 12 testers opted in for 14 continuous days** before you can apply for
production access.
1. **Testing → Closed testing → Create track**, upload `Todo-play.aab`.
2. Add testers by email list (friends, family) and share the opt-in link.
3. Keep them installed for 14 days; ship an update or two in that time – Google looks for activity.
4. Then **Apply for production** and answer the short questionnaire about your test.

## 5. Production
Create a production release with the same bundle (or a newer one), add the release notes from
`LISTING.md`, and roll out.

## Every later update
Bump `versionCode` (and `versionName`) in `app/build.gradle`, push, download the new `.aab`, and
upload it as a new release.

## Note for the sideloaded app
The Play version is signed by Google, so Android treats it as a different app signature. Before
installing from Play, back up your tasks (Settings → Export backup), uninstall the sideloaded app,
install from Play, then restore the backup.
