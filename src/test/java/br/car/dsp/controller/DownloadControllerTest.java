package br.car.dsp.controller;

import br.car.dsp.dto.DownloadItemResponse;
import br.car.dsp.dto.DownloadSearchRequest;
import br.car.dsp.dto.DownloadThemeResponse;
import br.car.dsp.service.DownloadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadControllerTest {

	@Mock
	private DownloadService downloadService;

	@InjectMocks
	private DownloadController downloadController;

	@Test
	void getThemes_ShouldDelegateToService() {
		List<DownloadThemeResponse> themes = List.of(
				new DownloadThemeResponse("theme_alpha", "Theme Alpha", List.of("csv"), true)
		);
		when(downloadService.getThemes()).thenReturn(themes);

		List<DownloadThemeResponse> result = downloadController.getThemes();

		assertEquals(themes, result);
		verify(downloadService).getThemes();
	}

	@Test
	void search_ShouldDelegateToService() {
		DownloadSearchRequest request = new DownloadSearchRequest();
		request.setLevel2("DF");
		List<DownloadItemResponse> items = List.of(
				new DownloadItemResponse("theme_alpha", "Theme Alpha", List.of(), "2026-06-01", null)
		);
		when(downloadService.search(request)).thenReturn(items);

		List<DownloadItemResponse> result = downloadController.search(request);

		assertEquals(items, result);
		verify(downloadService).search(request);
	}

	@Test
	void downloadFile_ShouldDelegateToService() {
		ResponseEntity<byte[]> response = ResponseEntity.ok(new byte[]{1, 2, 3});
		when(downloadService.downloadFile("DF", null, "theme_alpha", "csv")).thenReturn(response);

		ResponseEntity<byte[]> result = downloadController.downloadFile("DF", null, "theme_alpha", "csv");

		assertEquals(response, result);
		verify(downloadService).downloadFile("DF", null, "theme_alpha", "csv");
	}

	@Test
	void downloadFeaturesBundle_ShouldDelegateToService() {
		ResponseEntity<byte[]> response = ResponseEntity.ok(new byte[]{80, 75});
		when(downloadService.downloadFeaturesBundle("DEMO-001")).thenReturn(response);

		ResponseEntity<byte[]> result = downloadController.downloadFeaturesBundle("DEMO-001");

		assertEquals(response, result);
		verify(downloadService).downloadFeaturesBundle("DEMO-001");
	}
}
