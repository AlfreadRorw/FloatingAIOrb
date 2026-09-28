# Build validation notes

The Android resource XML files were parsed successfully before packaging.

The project is Java-only, so it avoids the Kotlin stdlib duplicate-class issue encountered in the previous ARACHNE project.

The Shizuku integration uses API 13.1.5. Shizuku documents that `newProcess` is deprecated and planned for removal in API 14; it is used here through a small compatibility reflection bridge because API 13.1.5 still exposes that underlying capability. A future version can migrate the bridge to a dedicated Shizuku UserService without changing the UI or window engine.
