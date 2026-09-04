package br.car.dsp.service;

import br.car.dsp.config.download.DownloadThemeConfig;
import br.car.dsp.dto.DownloadFormatStatus;
import br.car.dsp.dto.DownloadItemResponse;
import br.car.dsp.dto.DownloadSearchRequest;
import br.car.dsp.dto.DownloadThemeResponse;
import br.car.dsp.repository.AreaOfInterestRepository;
import br.car.dsp.support.DownloadFileNameBuilder;
import br.car.dsp.support.FeaturesBundleZipBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DownloadService {

	/** Formats the WFS can produce, and therefore the only ones with a fallback. */
	private static final Set<String> WFS_BACKED_FORMATS = Set.of("csv");

	private final DownloadConfigService downloadConfigService;
	private final DownloadTerritoryFilterBuilder territoryFilterBuilder;
	private final GeoServerWfsClient geoServerWfsClient;
	private final DownloadFileNameBuilder downloadFileNameBuilder;
	private final AreaOfInterestRepository areaOfInterestRepository;
	private final FeaturesBundleZipBuilder featuresBundleZipBuilder;
	private final PreGeneratedGeoFileService preGeneratedGeoFileService;

	public List<DownloadThemeResponse> getThemes() {
		return downloadConfigService.getEnabledThemes().stream()
				.map(theme -> new DownloadThemeResponse(
						theme.code(),
						theme.name(),
						theme.formats(),
						theme.enabled()
				))
				.toList();
	}

	public List<DownloadItemResponse> search(DownloadSearchRequest request) {
		if (request == null || request.getLevel2() == null || request.getLevel2().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Level 2 is required to search downloads");
		}

		String level2 = request.getLevel2().trim();
		String level3 = blankToNull(request.getLevel3());
		territoryFilterBuilder.validateTerritory(level2, level3);

		List<DownloadThemeConfig> themes = downloadConfigService.getEnabledThemes();
		if (request.getTheme() != null && !request.getTheme().isBlank()) {
			themes = downloadConfigService.findEnabledTheme(request.getTheme())
					.map(List::of)
					.orElse(List.of());
		}

		String wfsBaseUrl = downloadConfigService.resolveWfsBaseUrl();
		List<DownloadItemResponse> items = new ArrayList<>();
		for (DownloadThemeConfig theme : themes) {
			String cqlFilter = territoryFilterBuilder.buildCqlFilter(theme, level2, level3);
			long matched = geoServerWfsClient.countFeatures(wfsBaseUrl, theme.typeName(), cqlFilter);
			boolean available = matched > 0;
			List<DownloadFormatStatus> formats = theme.formats().stream()
					.map(format -> new DownloadFormatStatus(
							format,
							available
									? DownloadFormatStatus.AVAILABLE
									: DownloadFormatStatus.UNAVAILABLE
					))
					.toList();
			String lastUpdate = null;
			if (available) {
				// The pre-generated file carries the date of the data it holds; the WFS answers
				// the date of the data it has now. When both exist the file is what the citizen
				// downloads, so its date is the honest one.
				lastUpdate = firstFormat(theme)
						.flatMap(format -> preGeneratedGeoFileService.findLastUpdate(
								level2, level3, theme.code(), format))
						.orElseGet(() -> geoServerWfsClient.fetchLatestAttributeValue(
								wfsBaseUrl,
								theme.typeName(),
								cqlFilter
						).orElse(null));
			}
			items.add(new DownloadItemResponse(
					theme.code(),
					theme.name(),
					formats,
					lastUpdate
			));
		}
		return items;
	}

	public ResponseEntity<byte[]> downloadFile(String level2, String level3, String theme, String format) {
		if (level2 == null || level2.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Level 2 is required");
		}
		if (theme == null || theme.isBlank() || format == null || format.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Theme and format are required");
		}

		String normalizedLevel3 = blankToNull(level3);
		territoryFilterBuilder.validateTerritory(level2.trim(), normalizedLevel3);

		DownloadThemeConfig themeConfig = downloadConfigService.findEnabledTheme(theme)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Theme not found"));

		String normalizedFormat = format.trim().toLowerCase(Locale.ROOT);
		if (!themeConfig.formats().contains(normalizedFormat)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format not supported for the theme");
		}

		// S3-first: the file was already generated from the same base the WFS reads, so serving
		// it skips the heavy query. Only the formats the WFS can produce have a fallback.
		byte[] content = preGeneratedGeoFileService.fetch(
				level2.trim(),
				normalizedLevel3,
				themeConfig.code(),
				normalizedFormat
		).orElseGet(() -> downloadFromWfs(themeConfig, level2.trim(), normalizedLevel3, normalizedFormat));

		String fileName = downloadFileNameBuilder.build(
				level2.trim(),
				normalizedLevel3,
				themeConfig.name(),
				normalizedFormat
		);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
		headers.setContentLength(content.length);

		return new ResponseEntity<>(content, headers, HttpStatus.OK);
	}

	/**
	 * Fallback for what the object storage does not have. Only {@code csv} exists in the WFS:
	 * any other format is served exclusively from the pre-generated file, so its absence is a
	 * "not ready yet", not a broken request.
	 */
	private byte[] downloadFromWfs(DownloadThemeConfig themeConfig, String level2, String level3,
			String format) {
		if (!WFS_BACKED_FORMATS.contains(format)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File unavailable for download");
		}

		String wfsBaseUrl = downloadConfigService.resolveWfsBaseUrl();
		String cqlFilter = territoryFilterBuilder.buildCqlFilter(themeConfig, level2, level3);
		long matched = geoServerWfsClient.countFeatures(wfsBaseUrl, themeConfig.typeName(), cqlFilter);
		if (matched <= 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File unavailable for download");
		}
		return geoServerWfsClient.downloadCsv(wfsBaseUrl, themeConfig.typeName(), cqlFilter);
	}

	/** First format of the theme: enough to find the file whose date represents the theme. */
	private static Optional<String> firstFormat(DownloadThemeConfig theme) {
		List<String> formats = theme.formats();
		return formats == null || formats.isEmpty()
				? Optional.empty()
				: Optional.of(formats.getFirst());
	}

	public ResponseEntity<byte[]> downloadFeaturesBundle(String aoiId) {
		String normalizedAoiId = blankToNull(aoiId);
		if (normalizedAoiId == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Area of interest id is required");
		}
		if (!areaOfInterestRepository.existsById(normalizedAoiId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Area of interest not found");
		}

		String wfsBaseUrl = downloadConfigService.resolveWfsBaseUrl();
		Map<String, byte[]> entries = new LinkedHashMap<>();

		for (DownloadThemeConfig theme : downloadConfigService.getEnabledThemes()) {
			if (!theme.formats().contains("csv")) {
				continue;
			}

			String cqlFilter = territoryFilterBuilder.buildAoiScopedCqlFilter(theme, normalizedAoiId);
			long matched = geoServerWfsClient.countFeatures(wfsBaseUrl, theme.typeName(), cqlFilter);
			if (matched <= 0) {
				continue;
			}

			byte[] content = geoServerWfsClient.downloadCsv(wfsBaseUrl, theme.typeName(), cqlFilter);
			String entryName = downloadFileNameBuilder.buildForAoi(normalizedAoiId, theme.name(), "csv");
			entries.put(entryName, content);
		}

		if (entries.isEmpty()) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"No downloadable features for the selected area of interest"
			);
		}

		byte[] zipBytes;
		try {
			zipBytes = featuresBundleZipBuilder.build(entries);
		} catch (IOException ex) {
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Failed to build features bundle",
					ex
			);
		}

		String fileName = downloadFileNameBuilder.buildBundleArchiveName(normalizedAoiId);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/zip"));
		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
		headers.setContentLength(zipBytes.length);

		return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
