package br.car.dsp.dto;

import java.util.List;

/**
 * Downloads table row (theme + formats + last update + lastFileGenerated).
 */
public record DownloadItemResponse(
		String themeCode,
		String themeName,
		List<DownloadFormatStatus> formats,
		String lastUpdate,
		String lastFileGenerated
) {
}
