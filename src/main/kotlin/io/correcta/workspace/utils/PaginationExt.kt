package io.correcta.workspace.utils

import io.correcta.workspace.exceptions.BadRequestException
import io.correcta.workspace.models.request.PaginationRequest
import io.correcta.workspace.models.response.PagedResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

object PaginationExt {

    fun PaginationRequest.toPageRequest(allowedSortFields: Set<String>): PageRequest {
        if (sortBy !in allowedSortFields) {
            throw BadRequestException(
                "Não é possível ordenar por '$sortBy'. Campos permitidos: ${allowedSortFields.joinToString(", ")}"
            )
        }

        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy))
    }

    fun <E, T> Page<E>.toPagedResponse(request: PaginationRequest, mapper: (E) -> T): PagedResponse<T> {
        return PagedResponse(
            content = content.map(mapper),
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            sortBy = request.sortBy,
            sortDirection = request.sortDirection.lowercase()
        )
    }

}
