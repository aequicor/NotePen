# Local network connect diagnostics

КД: Android 17/API 37 вводит runtime permission `android.permission.ACCESS_LOCAL_NETWORK`; при запрете LAN sync может выглядеть как обычный timeout. Плюс активный VPN часто уводит LAN-сокеты в туннель. Поэтому connect failure классифицируется в sync infrastructure, а UI добавляет подсказки из platform health snapshot.

Источник истины: `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/infrastructure/ConnectFailureClassifier.kt`, `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/exception/SyncConnectException.kt`, `sync/src/commonMain/kotlin/ru/kyamshanov/notepen/sync/domain/model/ConnectFailureKind.kt`, `sync/src/androidMain/kotlin/ru/kyamshanov/notepen/sync/infrastructure/AndroidLocalNetworkDiagnostics.kt`, `qr-connect/src/commonMain/kotlin/ru/kyamshanov/notepen/qrconnect/application/ConnectFailureMessages.kt`, и pairing UI wiring в `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/qrconnect`.

Инварианты: domain получает только coarse `ConnectFailureKind`, без Android/Ktor типов. `PAIRING_REJECTED` не получает VPN/permission hints, потому что транспорт уже работал. `LOCAL_NETWORK_BLOCKED` должен побеждать timeout при обходе cause-chain: Android denial может прийти как timeout с `EPERM` глубже в причине. Runtime permission запрашивается в `LocalNetworkPermissionEffect` только на Android 17+, desktop actual остается no-op.

Проверка: `./gradlew :sync:jvmTest :qr-connect:jvmTest :app:byCompose:common:jvmTest`. Связанные тесты: `ConnectFailureClassifierTest`, `ConnectFailureMessagesTest`.
