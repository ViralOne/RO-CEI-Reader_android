package dev.ceireader.app.model

sealed interface ReadState {
    data object Idle : ReadState
    data object Started : ReadState
    data object ReadingCard : ReadState
    data class Finished(val data: CeiData) : ReadState
    data class Error(val kind: ReadErrorKind, val retriesLeft: Int? = null) : ReadState
}

enum class ReadErrorKind {
    WRONG_CAN,
    WRONG_PIN,
    PIN_BLOCKED,
    CARD_LOST,
    COMMUNICATION,
    UNKNOWN
}
