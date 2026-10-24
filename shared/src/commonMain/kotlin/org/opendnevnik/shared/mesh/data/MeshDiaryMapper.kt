package org.opendnevnik.shared.mesh.data

import org.opendnevnik.shared.mesh.DiarySnapshot
import org.opendnevnik.shared.mesh.api.dto.MeshDiaryResponse

/** Provider DTOs are translated here; Compose only receives existing domain models. */
class MeshDiaryMapper {
    fun toDomain(response: MeshDiaryResponse, studentId: String, syncedAt: String): DiarySnapshot {
        // TODO: validate response ownership and map homework, marks and assessments.
        TODO("Map the confirmed response to DiarySnapshot")
    }
}
