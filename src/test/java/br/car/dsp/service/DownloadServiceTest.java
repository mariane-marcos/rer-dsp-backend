package br.car.dsp.service;

import br.car.dsp.config.download.DownloadTerritoryFilterConfig;
import br.car.dsp.config.download.DownloadThemeConfig;
import br.car.dsp.dto.DownloadFormatStatus;
import br.car.dsp.dto.DownloadItemResponse;
import br.car.dsp.dto.DownloadSearchRequest;
import br.car.dsp.dto.DownloadThemeResponse;
import br.car.dsp.support.DownloadFileNameBuilder;
import br.car.dsp.support.FeaturesBundleZipBuilder;
import br.car.dsp.repository.AreaOfInterestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DownloadServiceTest {

	@Mock
	private DownloadConfigService downloadConfigService;

	@Mock
	private DownloadTerritoryFilterBuilder territoryFilterBuilder;

	@Mock
	private GeoServerWfsClient geoServerWfsClient;

	@Mock
	private DownloadFileNameBuilder downloadFileNameBuilder;

	@Mock
	private AreaOfInterestRepository areaOfInterestRepository;

	@Mock
	private FeaturesBundleZipBuilder featuresBundleZipBuilder;

	@Mock
	private PreGeneratedGeoFileService preGeneratedGeoFileService;

	@InjectMocks
	private DownloadService downloadService;

	private DownloadThemeConfig areaTheme;
	private DownloadThemeConfig linkedTheme;

	@BeforeEach
	void setUp() {
		lenient().when(preGeneratedGeoFileService.fetch(anyString(), any(), anyString(), anyString()))
				.thenReturn(java.util.Optional.empty());
		lenient().when(preGeneratedGeoFileService.findGeneratedAt(anyString(), any(), anyString(), anyString()))
				.thenReturn(java.util.Optional.empty());
		areaTheme = new DownloadThemeConfig(
				"area_of_interest",
				"Area of interest",
				"dsp:area-of-interest",
				List.of("csv"),
				true,
				new DownloadTerritoryFilterConfig("direct", "territory_level_3_id", null)
		);
		linkedTheme = new DownloadThemeConfig(
				"generic_layer",
				"Generic layer",
				"dsp:generic-layer",
				List.of("csv"),
				true,
				new DownloadTerritoryFilterConfig("aoi_linked", null, "area_of_interest_id")
		);
	}

	@Test
	void getThemes_ShouldReturnEnabledThemesFromConfig() {
		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme));

		List<DownloadThemeResponse> themes = downloadService.getThemes();

		assertEquals(1, themes.size());
		assertEquals("area_of_interest", themes.getFirst().code());
		assertEquals("Area of interest", themes.getFirst().name());
	}

	@Test
	void search_ShouldRequireLevel2() {
		DownloadSearchRequest request = new DownloadSearchRequest();

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> downloadService.search(request)
		);

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}

	@Test
	void search_ShouldReturnAvailableFormatsWhenWfsHasFeatures() {
		DownloadSearchRequest request = new DownloadSearchRequest();
		request.setLevel2("DF");

		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(2L);
		when(geoServerWfsClient.fetchLatestAttributeValue(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(java.util.Optional.of("2024-06-15T10:30:00Z"));

		List<DownloadItemResponse> items = downloadService.search(request);

		assertEquals(1, items.size());
		assertEquals("area_of_interest", items.getFirst().themeCode());
		assertEquals("2024-06-15T10:30:00Z", items.getFirst().lastUpdate());
		assertNull(items.getFirst().lastFileGenerated());
		assertTrue(items.getFirst().formats().stream().anyMatch(format ->
				"csv".equals(format.format()) && DownloadFormatStatus.AVAILABLE.equals(format.status())));
		verify(territoryFilterBuilder).validateTerritory("DF", null);
	}

	@Test
	void search_ShouldSkipLastUpdateFetchWhenNoFeaturesMatched() {
		DownloadSearchRequest request = new DownloadSearchRequest();
		request.setLevel2("DF");

		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(0L);

		List<DownloadItemResponse> items = downloadService.search(request);

		assertEquals(1, items.size());
		assertNull(items.getFirst().lastUpdate());
		assertNull(items.getFirst().lastFileGenerated());
		verify(geoServerWfsClient, never()).fetchLatestAttributeValue(anyString(), anyString(), anyString());
	}

	@Test
	void search_ShouldKeepAvailableWhenLastUpdateFetchFails() {
		DownloadSearchRequest request = new DownloadSearchRequest();
		request.setLevel2("DF");

		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(1L);
		when(geoServerWfsClient.fetchLatestAttributeValue(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(java.util.Optional.empty());

		List<DownloadItemResponse> items = downloadService.search(request);

		assertEquals(1, items.size());
		assertNull(items.getFirst().lastUpdate());
		assertTrue(items.getFirst().formats().stream().anyMatch(format ->
				DownloadFormatStatus.AVAILABLE.equals(format.status())));
	}

	@Test
	void search_ShouldFilterByTheme() {
		DownloadSearchRequest request = new DownloadSearchRequest();
		request.setLevel2("DF");
		request.setTheme("area_of_interest");

		when(downloadConfigService.findEnabledTheme("area_of_interest")).thenReturn(java.util.Optional.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(0L);

		List<DownloadItemResponse> items = downloadService.search(request);

		assertEquals(1, items.size());
		assertTrue(items.getFirst().formats().stream().allMatch(format ->
				DownloadFormatStatus.UNAVAILABLE.equals(format.status())));
	}

	@Test
	void downloadFile_ShouldReturnCsvBytesWhenAvailable() {
		when(downloadConfigService.findEnabledTheme("area_of_interest")).thenReturn(java.util.Optional.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(1L);
		when(geoServerWfsClient.downloadCsv(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn("id,name\n1,test\n".getBytes());
		when(downloadFileNameBuilder.build("DF", null, "Area of interest", "csv"))
				.thenReturn("area-of-interest_df.csv");

		ResponseEntity<byte[]> response = downloadService.downloadFile("DF", null, "area_of_interest", "csv");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().length > 0);
		assertTrue(response.getHeaders().getFirst("Content-Disposition")
				.contains("area-of-interest_df.csv"));
	}

	@Test
	void downloadFile_ShouldReturnNotFoundWhenUnavailable() {
		when(downloadConfigService.findEnabledTheme("area_of_interest")).thenReturn(java.util.Optional.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(0L);

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> downloadService.downloadFile("DF", null, "area_of_interest", "csv")
		);

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	@Test
	void downloadFile_ShouldServePreGeneratedFileWithoutTouchingTheWfs() {
		when(downloadConfigService.findEnabledTheme("area_of_interest")).thenReturn(java.util.Optional.of(areaTheme));
		when(preGeneratedGeoFileService.fetch("DF", null, "area_of_interest", "csv"))
				.thenReturn(java.util.Optional.of("id,name\n1,pre-generated\n".getBytes()));
		when(downloadFileNameBuilder.build("DF", null, "Area of interest", "csv"))
				.thenReturn("area-of-interest_df.csv");

		ResponseEntity<byte[]> response = downloadService.downloadFile("DF", null, "area_of_interest", "csv");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("id,name\n1,pre-generated\n", new String(response.getBody()));
		verify(geoServerWfsClient, never()).downloadCsv(anyString(), anyString(), anyString());
		verify(geoServerWfsClient, never()).countFeatures(anyString(), anyString(), anyString());
	}

	@Test
	void downloadFile_ShouldFallBackToTheWfsWhenTheFileIsNotPublishedYet() {
		when(downloadConfigService.findEnabledTheme("area_of_interest")).thenReturn(java.util.Optional.of(areaTheme));
		when(preGeneratedGeoFileService.fetch("DF", null, "area_of_interest", "csv"))
				.thenReturn(java.util.Optional.empty());
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(1L);
		when(geoServerWfsClient.downloadCsv(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn("id,name\n1,from-wfs\n".getBytes());
		when(downloadFileNameBuilder.build("DF", null, "Area of interest", "csv"))
				.thenReturn("area-of-interest_df.csv");

		ResponseEntity<byte[]> response = downloadService.downloadFile("DF", null, "area_of_interest", "csv");

		assertEquals("id,name\n1,from-wfs\n", new String(response.getBody()));
	}

	@Test
	void downloadFile_ShouldReturnNotFoundForFormatsTheWfsCannotProduce() {
		DownloadThemeConfig gpkgTheme = new DownloadThemeConfig(
				"area_of_interest",
				"Area of interest",
				"dsp:area-of-interest",
				List.of("csv", "gpkg"),
				true,
				new DownloadTerritoryFilterConfig("direct", "territory_level_3_id", null)
		);
		when(downloadConfigService.findEnabledTheme("area_of_interest")).thenReturn(java.util.Optional.of(gpkgTheme));
		when(preGeneratedGeoFileService.fetch("DF", null, "area_of_interest", "gpkg"))
				.thenReturn(java.util.Optional.empty());

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> downloadService.downloadFile("DF", null, "area_of_interest", "gpkg")
		);

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
		verify(geoServerWfsClient, never()).countFeatures(anyString(), anyString(), anyString());
	}

	@Test
	void search_ShouldKeepLastUpdateFromTheWfsEvenWhenAFileIsPublished() {
		DownloadSearchRequest request = new DownloadSearchRequest();
		request.setLevel2("DF");

		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(2L);
		when(geoServerWfsClient.fetchLatestAttributeValue(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(java.util.Optional.of("2026-09-01T12:00:00Z"));
		when(preGeneratedGeoFileService.findGeneratedAt("DF", null, "area_of_interest", "csv"))
				.thenReturn(java.util.Optional.of("2026-03-04T10:00:00Z"));

		List<DownloadItemResponse> items = downloadService.search(request);

		assertEquals("2026-09-01T12:00:00Z", items.getFirst().lastUpdate());
		assertEquals("2026-03-04T10:00:00Z", items.getFirst().lastFileGenerated());
		verify(geoServerWfsClient).fetchLatestAttributeValue(anyString(), eq("dsp:area-of-interest"), anyString());
	}

	@Test
	void search_ShouldLeaveLastFileGeneratedEmptyWhenTheObjectIsMissing() {
		DownloadSearchRequest request = new DownloadSearchRequest();
		request.setLevel2("DF");

		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildCqlFilter(areaTheme, "DF", null))
				.thenReturn("territory_level_3_id IN ('5300108')");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(2L);
		when(geoServerWfsClient.fetchLatestAttributeValue(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(java.util.Optional.of("2026-09-01T12:00:00Z"));
		when(preGeneratedGeoFileService.findGeneratedAt("DF", null, "area_of_interest", "csv"))
				.thenReturn(java.util.Optional.empty());

		List<DownloadItemResponse> items = downloadService.search(request);

		assertEquals("2026-09-01T12:00:00Z", items.getFirst().lastUpdate());
		assertNull(items.getFirst().lastFileGenerated());
	}

	@Test
	void downloadFeaturesBundle_ShouldReturnZipWithAvailableThemes() throws Exception {
		when(areaOfInterestRepository.existsById("DEMO-001")).thenReturn(true);
		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme, linkedTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildAoiScopedCqlFilter(areaTheme, "DEMO-001"))
				.thenReturn("id = 'DEMO-001'");
		when(territoryFilterBuilder.buildAoiScopedCqlFilter(linkedTheme, "DEMO-001"))
				.thenReturn("area_of_interest_id = 'DEMO-001'");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(1L);
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:generic-layer"), anyString()))
				.thenReturn(1L);
		when(geoServerWfsClient.downloadCsv(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn("id\nDEMO-001\n".getBytes());
		when(geoServerWfsClient.downloadCsv(anyString(), eq("dsp:generic-layer"), anyString()))
				.thenReturn("area_of_interest_id\nDEMO-001\n".getBytes());
		when(downloadFileNameBuilder.buildForAoi("DEMO-001", "Area of interest", "csv"))
				.thenReturn("area-of-interest_demo-001.csv");
		when(downloadFileNameBuilder.buildForAoi("DEMO-001", "Generic layer", "csv"))
				.thenReturn("generic-layer_demo-001.csv");
		when(downloadFileNameBuilder.buildBundleArchiveName("DEMO-001")).thenReturn("demo-001_features.zip");
		when(featuresBundleZipBuilder.build(anyMap())).thenReturn(new byte[] { 80, 75, 3, 4 });

		ResponseEntity<byte[]> response = downloadService.downloadFeaturesBundle("DEMO-001");

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("application/zip", response.getHeaders().getContentType().toString());
		assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("demo-001_features.zip"));
		verify(featuresBundleZipBuilder).build(anyMap());
	}

	@Test
	void downloadFeaturesBundle_ShouldReturnNotFoundWhenAoiDoesNotExist() {
		when(areaOfInterestRepository.existsById("UNKNOWN")).thenReturn(false);

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> downloadService.downloadFeaturesBundle("UNKNOWN")
		);

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	@Test
	void downloadFeaturesBundle_ShouldReturnNotFoundWhenNoThemesHaveFeatures() {
		when(areaOfInterestRepository.existsById("DEMO-001")).thenReturn(true);
		when(downloadConfigService.getEnabledThemes()).thenReturn(List.of(areaTheme));
		when(downloadConfigService.resolveWfsBaseUrl()).thenReturn("http://localhost:22669/geoserver/dsp/wfs");
		when(territoryFilterBuilder.buildAoiScopedCqlFilter(areaTheme, "DEMO-001"))
				.thenReturn("id = 'DEMO-001'");
		when(geoServerWfsClient.countFeatures(anyString(), eq("dsp:area-of-interest"), anyString()))
				.thenReturn(0L);

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> downloadService.downloadFeaturesBundle("DEMO-001")
		);

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}
}
