package org.openflux.app.data

// Stored as activeProfileId when the balancer is picked instead of a real profile - never a real
// row id (ProfileRepository ids are Room-generated UUIDs), so lookups for it always miss and
// callers branch on this constant first.
const val BALANCER_PROFILE_ID = "__balancer__"

enum class ProfileMode { KEY, MANUAL }

// Despite the name, this is set for both profile modes - see ProfileEditScreen.
enum class ManualTransport { YANDEX, VOLGA, MAX, YANDEX_MULTISTREAM, MAILRU, BOARDS, MTS }

/** The wire name mobile.Config and deep links use for a transport - see mobile/mobile.go. */
fun transportName(t: ManualTransport): String = when (t) {
    ManualTransport.YANDEX -> "yandex"
    ManualTransport.VOLGA -> "volga"
    ManualTransport.MAX -> "max"
    ManualTransport.YANDEX_MULTISTREAM -> "yandex_multistream"
    ManualTransport.MAILRU -> "mailru"
    ManualTransport.BOARDS -> "boards"
    ManualTransport.MTS -> "mts"
}

fun parseTransportName(name: String): ManualTransport = when (name) {
    "volga" -> ManualTransport.VOLGA
    "max" -> ManualTransport.MAX
    "yandex_multistream" -> ManualTransport.YANDEX_MULTISTREAM
    "mailru" -> ManualTransport.MAILRU
    "boards" -> ManualTransport.BOARDS
    "mts" -> ManualTransport.MTS
    else -> ManualTransport.YANDEX
}

fun transportLabel(t: ManualTransport): String = when (t) {
    ManualTransport.YANDEX -> "Yandex"
    ManualTransport.VOLGA -> "Volga"
    ManualTransport.MAX -> "MAX"
    ManualTransport.YANDEX_MULTISTREAM -> "Yandex multi"
    ManualTransport.MAILRU -> "Mail.ru"
    ManualTransport.BOARDS -> "Boards"
    ManualTransport.MTS -> "MTS"
}

// [ProfileEntity]'s non-secret fields joined with the secret material [SecretsStore] holds for the same id.
data class Profile(
    val id: String,
    val name: String,
    val mode: ProfileMode,
    val controlUrl: String = "",
    val keyToken: String = "",
    val manualTransport: ManualTransport = ManualTransport.YANDEX,
    val docUrl: String = "",
    // YANDEX_MULTISTREAM only: 2+ independent doc URLs; the exit node needs the exact same list (any order).
    val docUrls: List<String> = emptyList(),
    val maxToken: String = "",
    val maxUid: Long = 0,
    val mtu: Int = 1400,
    val dnsUpstream: String = "77.88.8.8",
    // Overrides the resolver for the transport's own pre-tunnel bootstrap lookups; blank keeps the defaults.
    val forceBootstrapDns: String = "",
    val autoReconnect: Boolean = true,
    // Off by default even when keyToken is set, since the exit node may not understand it yet.
    val e2eEncryption: Boolean = false,
)
