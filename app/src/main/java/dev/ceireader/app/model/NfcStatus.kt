package dev.ceireader.app.model

/** Coarse NFC adapter state, used to decide what the entry screen should tell the user. */
enum class NfcStatus {
    /** The device has no NFC radio at all. */
    NO_HARDWARE,

    /** NFC hardware is present but currently turned off in system settings. */
    DISABLED,

    /** NFC is on; a card tap can be answered. */
    ENABLED,
}
