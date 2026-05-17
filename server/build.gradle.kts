plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

// Thin host-side aggregation module. WebSocket server logic lives in :sync (jvmMain).
// This module is reserved for the future standalone host entrypoint (main() / DI wiring).
dependencies {
    api(projects.sync)
}
