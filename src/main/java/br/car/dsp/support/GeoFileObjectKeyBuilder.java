package br.car.dsp.support;

import br.car.dsp.model.TerritoryLevel2;
import br.car.dsp.model.TerritoryLevel3;
import br.car.dsp.repository.TerritoryLevel2Repository;
import br.car.dsp.repository.TerritoryLevel3Repository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the object key of a pre-generated download file.
 *
 * <p>The layout is a contract with {@code rer-dsp-job-geo-file-generation}, which writes the
 * files: {@code {format}/{level}/{slug}_{themeCode}.{ext}}, level 3 prefixed by the slug of its
 * level 2 — homonyms across parents are the rule, not the exception.
 *
 * <p>The API works with ids; the slug comes from {@code name}. The slug is deliberately not
 * built by {@link DownloadFileNameBuilder}: that one caps the segment length for a readable
 * download name, and a capped segment would not match the published key.
 */
@Component
@RequiredArgsConstructor
public class GeoFileObjectKeyBuilder {

	private final TerritoryLevel2Repository level2Repository;
	private final TerritoryLevel3Repository level3Repository;

	/**
	 * @return the key, or empty when a name in the chain cannot produce a slug — the caller
	 *         then falls back to the WFS instead of guessing a key.
	 */
	public Optional<String> build(String level2Id, String level3Id, String themeCode, String format) {
		Optional<String> level2Slug = level2Repository.findById(level2Id)
				.map(TerritoryLevel2::getName)
				.flatMap(GeoFileObjectKeyBuilder::slugify);
		if (level2Slug.isEmpty()) {
			return Optional.empty();
		}

		// The published formats name their own extension (csv, gpkg), so folder and extension
		// are the same token.
		String normalizedFormat = DownloadFileNameBuilder.normalizeExtension(format);
		if (level3Id == null || level3Id.isBlank()) {
			return Optional.of("%s/level-2/%s_%s.%s"
					.formatted(normalizedFormat, level2Slug.get(), themeCode, normalizedFormat));
		}

		return level3Repository.findById(level3Id.trim())
				.map(TerritoryLevel3::getName)
				.flatMap(GeoFileObjectKeyBuilder::slugify)
				.map(level3Slug -> "%s/level-3/%s_%s_%s.%s".formatted(
						normalizedFormat, level2Slug.get(), level3Slug, themeCode, normalizedFormat));
	}

	static Optional<String> slugify(String rawName) {
		if (rawName == null) {
			return Optional.empty();
		}
		String withoutAccents = Normalizer.normalize(rawName.trim(), Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "");
		String slug = withoutAccents
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");
		return slug.isBlank() ? Optional.empty() : Optional.of(slug);
	}
}
