// Add inside the Android APPLICATION module's `android { }` block so parallel branches install
// side-by-side on one device/emulator. APP_ID_SUFFIX comes from the worktree lane (.envrc);
// it is empty in the main checkout, so the main build keeps its canonical applicationId.
//
// (For a KMP Android *library* this does not apply — libraries have no applicationId.)

android {
    defaultConfig {
        val worktreeSuffix = System.getenv("APP_ID_SUFFIX").orEmpty()
        applicationIdSuffix = worktreeSuffix
        versionNameSuffix = worktreeSuffix
    }
}
