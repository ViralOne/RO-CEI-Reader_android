package dev.ceireader.app.model

data class AddressPeriod(
    val address: String,
    val startDate: String?,
    val endDate: String?
)

data class CeiData(
    val lastName: String?,
    val firstName: String?,
    val gender: String?,
    val citizenship: String?,
    val birthDate: String?,
    val cnp: String?,
    val faceImage: ByteArray?,
    val placeOfBirth: String?,
    val fatherName: String?,
    val motherName: String?,
    val documentSerialNo: String?,
    val issuingAuthority: String?,
    val issuingDate: String?,
    val expiryDate: String?,
    val currentAddress: String?,
    val temporaryAddresses: List<AddressPeriod>,
    val foreignAddresses: List<AddressPeriod>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CeiData

        if (lastName != other.lastName) return false
        if (firstName != other.firstName) return false
        if (gender != other.gender) return false
        if (citizenship != other.citizenship) return false
        if (birthDate != other.birthDate) return false
        if (cnp != other.cnp) return false
        if (faceImage != null) {
            if (other.faceImage == null) return false
            if (!faceImage.contentEquals(other.faceImage)) return false
        } else if (other.faceImage != null) return false
        if (placeOfBirth != other.placeOfBirth) return false
        if (fatherName != other.fatherName) return false
        if (motherName != other.motherName) return false
        if (documentSerialNo != other.documentSerialNo) return false
        if (issuingAuthority != other.issuingAuthority) return false
        if (issuingDate != other.issuingDate) return false
        if (expiryDate != other.expiryDate) return false
        if (currentAddress != other.currentAddress) return false
        if (temporaryAddresses != other.temporaryAddresses) return false
        if (foreignAddresses != other.foreignAddresses) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lastName?.hashCode() ?: 0
        result = 31 * result + (firstName?.hashCode() ?: 0)
        result = 31 * result + (gender?.hashCode() ?: 0)
        result = 31 * result + (citizenship?.hashCode() ?: 0)
        result = 31 * result + (birthDate?.hashCode() ?: 0)
        result = 31 * result + (cnp?.hashCode() ?: 0)
        result = 31 * result + (faceImage?.contentHashCode() ?: 0)
        result = 31 * result + (placeOfBirth?.hashCode() ?: 0)
        result = 31 * result + (fatherName?.hashCode() ?: 0)
        result = 31 * result + (motherName?.hashCode() ?: 0)
        result = 31 * result + (documentSerialNo?.hashCode() ?: 0)
        result = 31 * result + (issuingAuthority?.hashCode() ?: 0)
        result = 31 * result + (issuingDate?.hashCode() ?: 0)
        result = 31 * result + (expiryDate?.hashCode() ?: 0)
        result = 31 * result + (currentAddress?.hashCode() ?: 0)
        result = 31 * result + temporaryAddresses.hashCode()
        result = 31 * result + foreignAddresses.hashCode()
        return result
    }
}
