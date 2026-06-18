package dev.otherworld.shoppinglist.data.repo

import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.CreateTagRequest
import dev.otherworld.shoppinglist.domain.model.TagModel
import dev.otherworld.shoppinglist.domain.model.toModel
import javax.inject.Inject
import javax.inject.Singleton

/** Global per-user tags. Online-only CRUD (the backend exposes no item-tag assignment). */
@Singleton
class TagRepository @Inject constructor(
    private val service: OcsService,
) {
    suspend fun getTags(): List<TagModel> =
        service.getTags().ocs.data.map { it.toModel() }.sortedBy { it.name.lowercase() }

    suspend fun createTag(name: String) {
        service.createTag(CreateTagRequest(name.trim()))
    }

    suspend fun deleteTag(id: Long) = service.deleteTag(id)
}
