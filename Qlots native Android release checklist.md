# Qlots native Android release checklist

This project is a source-code handoff, not a Play Store submission. Do not mark a release ready until the following are verified on the exact signed candidate.

Build verification in this handoff: the native debug APK was built, archive-checked and verified as debug-signed. The minified release App Bundle also built and passed release lint, but it is **unsigned** because no upload key was supplied. No emulator/physical-device acceptance run or Play Console upload was performed.

## Build and app behavior

- [x] Native Kotlin debug APK builds; package ID, API/target levels, archive and debug signature verified.
- [x] Minified release App Bundle compiles and passes release lint; current AAB is unsigned and must not be uploaded as-is.
- [ ] Configure the production HTTPS API URL and confirm the certificate is valid.
- [ ] Build a release App Bundle signed with the protected upload key; test the resulting bundle through an internal Play track.
- [ ] Verify registration, sign-in, token renewal, sign-out, expired-session handling and user data isolation.
- [ ] Verify asset, liability, income and expense create/edit/delete; FD/RD dates and compounding; loan APR/EMI/tenure; and monthly cash-flow frequencies.
- [ ] Verify dashboard totals, history snapshots, asset allocation, health score, conservative/base/optimistic projections, helper disclaimers and PDF sharing against known test cases.
- [ ] Exercise network loss, server errors, empty states, negative values, small displays, keyboard overlap, rotation and accessibility text scaling.
- [ ] Confirm production release builds reject cleartext HTTP and do not contain debug endpoints, test accounts, signing credentials or secrets.
- [ ] Review the shrinker output and crash logs for the minified release.

## Backend, privacy and financial disclosures

- [ ] Set a unique high-entropy `JWT_SECRET`, production database, backup/restore plan, retention policy and secure operations monitoring.
- [ ] Confirm account deletion and financial-data deletion work end-to-end, including data stored in backups according to the stated retention policy.
- [ ] Publish an accurate privacy policy that covers account details, financial records, API hosting, retention, deletion, transport and support contact.
- [ ] Complete Play Console Data safety and account-deletion disclosures to match actual backend behavior and infrastructure.
- [ ] Review security and applicable data-protection requirements for the jurisdictions where the app will be available.
- [ ] Keep the app's calculation-backed helper and projection disclosures visible; avoid representing estimates as guaranteed returns or professional advice.
- [ ] Confirm no bank or brokerage integrations are claimed; the MVP uses figures the user enters.

## Google Play setup

- [ ] Verify current target API policy. As of 31 August 2026, Google's stated requirement for new apps and updates is Android 16 / API 36 or higher.
- [ ] Create and verify the Play Console app; prepare store name, description, icon, screenshots, category, contact details, content rating and country availability.
- [ ] Complete privacy, data-safety, financial-features and account-deletion declarations as applicable.
- [ ] Review Play Console's current testing-track, tester and new-developer production access requirements before scheduling a launch.
- [ ] Upload the signed `.aab`, review Play pre-launch test results, resolve policy/quality issues, and only then choose a release rollout.

The project does not include a generated production keystore, Play Console credentials, store listing assets, or a published release.
