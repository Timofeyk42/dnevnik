package org.opendnevnik.shared.mesh

/** Provider boundary. API endpoints and remote DTOs must stay inside the future adapter. */
interface MeshRepository {
    suspend fun fetchDiary(studentId: String): DiaryResult
    suspend fun signOut()
}

data class Homework(val id: String, val subject: String, val dueDate: String, val text: String)
data class Mark(val id: String, val subject: String, val date: String, val value: String, val weight: Int)
data class Assessment(val id: String, val subject: String, val date: String, val title: String)
data class DiarySnapshot(
    val studentId: String,
    val homework: List<Homework>,
    val marks: List<Mark>,
    val assessments: List<Assessment>,
    val syncedAt: String,
)
sealed interface DiaryResult {
    data class Success(val snapshot: DiarySnapshot) : DiaryResult
    data object NotConnected : DiaryResult
    data object SessionExpired : DiaryResult
    data object Unavailable : DiaryResult
}

/** Does not fabricate homework, marks, or upcoming assessments while API integration is pending. */
class DisconnectedMeshRepository : MeshRepository {
    override suspend fun fetchDiary(studentId: String): DiaryResult = DiaryResult.NotConnected
    override suspend fun signOut() = Unit
}
