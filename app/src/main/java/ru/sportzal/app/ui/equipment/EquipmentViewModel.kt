package ru.sportzal.app.ui.equipment

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.sportzal.app.data.files.EquipmentPhotoStore
import ru.sportzal.app.data.repository.SportzalRepository
import ru.sportzal.app.model.EquipmentCatalogItem
import ru.sportzal.app.model.SaveEquipmentCommand

data class EquipmentUiState(val items: List<EquipmentCatalogItem> = emptyList(), val loading: Boolean = true,
    val saving: Boolean = false, val error: String? = null)

class EquipmentViewModel(private val repository: SportzalRepository, private val photos: EquipmentPhotoStore) {
    private val mutableState = MutableStateFlow(EquipmentUiState())
    val state: StateFlow<EquipmentUiState> = mutableState

    suspend fun load() = runCatching { repository.equipmentCatalog() }
        .onSuccess { mutableState.value = EquipmentUiState(it, false) }
        .onFailure { mutableState.value = mutableState.value.copy(loading = false, error = "Не удалось загрузить тренажёры") }

    suspend fun save(command: SaveEquipmentCommand): String? {
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        return runCatching { repository.saveEquipment(command) }.fold({ id -> load(); id }, {
            mutableState.value = mutableState.value.copy(saving = false, error = it.message ?: "Не удалось сохранить тренажёр"); null
        })
    }

    suspend fun replacePhoto(item: EquipmentCatalogItem, uri: Uri): Boolean {
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        val newPath = runCatching { photos.copy(uri) }.getOrElse {
            mutableState.value = mutableState.value.copy(saving = false, error = "Не удалось сохранить фото"); return false
        }
        return runCatching { repository.setEquipmentPhoto(item.equipmentId, newPath) }.fold({
            photos.delete(item.photoPath); load(); true
        }, {
            photos.delete(newPath); mutableState.value = mutableState.value.copy(saving = false, error = "Не удалось сохранить фото"); false
        })
    }

    suspend fun removePhoto(item: EquipmentCatalogItem): Boolean = runCatching {
        repository.setEquipmentPhoto(item.equipmentId, null)
    }.fold({ photos.delete(item.photoPath); load(); true }, {
        mutableState.value = mutableState.value.copy(error = "Не удалось удалить фото"); false
    })
}
