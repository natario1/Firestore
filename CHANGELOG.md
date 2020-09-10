Starting from v0.7.0, you can [support development](https://github.com/sponsors/natario1) through the GitHub Sponsors program. 
Companies can share a tiny part of their revenue and get private support hours in return. Thanks!

## v0.7.0

- New: Upgrade to Kotlin 1.4, Firestore 21.6.0 ([#12][12])
- Breaking change: `FirestoreParcelers.add` is now `registerParceler` or `FirestoreParceler.register` ([#12][12])
- Breaking change: `FirestoreDocument.CacheState` is now `FirestoreCacheState` ([#12][12])
- Breaking change: parcelers moved to `com.otaliastudios.firestore.parcel.*` package ([#12][12])
- Fix: do not crash exception when metadata is not present ([#12][12])

<https://github.com/natario1/Firestore/compare/v0.6.0...v0.7.0>

[natario1]: https://github.com/natario1

[12]: https://github.com/natario1/Firestore/pull/12
