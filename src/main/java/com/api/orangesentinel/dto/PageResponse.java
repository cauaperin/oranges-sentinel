package com.api.orangesentinel.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Representação estável de uma página para a API (evita serializar {@code PageImpl} diretamente).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
