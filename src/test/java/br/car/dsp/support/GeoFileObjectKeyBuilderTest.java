package br.car.dsp.support;

import br.car.dsp.model.TerritoryLevel2;
import br.car.dsp.model.TerritoryLevel3;
import br.car.dsp.repository.TerritoryLevel2Repository;
import br.car.dsp.repository.TerritoryLevel3Repository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoFileObjectKeyBuilderTest {

	@Mock
	private TerritoryLevel2Repository level2Repository;

	@Mock
	private TerritoryLevel3Repository level3Repository;

	@InjectMocks
	private GeoFileObjectKeyBuilder keyBuilder;

	@Test
	void build_Level2KeyUsesTheSlugOfTheName() {
		when(level2Repository.findById("35")).thenReturn(Optional.of(level2("São Paulo")));

		assertEquals(
				Optional.of("csv/level-2/sao-paulo_area_of_interest.csv"),
				keyBuilder.build("35", null, "area_of_interest", "csv"));
	}

	@Test
	void build_Level3KeyIsPrefixedByTheParentSlug() {
		when(level2Repository.findById("35")).thenReturn(Optional.of(level2("São Paulo")));
		when(level3Repository.findById("3509502")).thenReturn(Optional.of(level3("Campinas")));

		assertEquals(
				Optional.of("csv/level-3/sao-paulo_campinas_area_of_interest.csv"),
				keyBuilder.build("35", "3509502", "area_of_interest", "csv"));
	}

	@Test
	void build_KeepsTheSameLayoutForAnotherFormat() {
		when(level2Repository.findById("35")).thenReturn(Optional.of(level2("São Paulo")));

		assertEquals(
				Optional.of("gpkg/level-2/sao-paulo_area_of_interest.gpkg"),
				keyBuilder.build("35", null, "area_of_interest", "gpkg"));
	}

	@Test
	void build_IsEmptyWhenTheTerritoryIsUnknown() {
		when(level2Repository.findById("99")).thenReturn(Optional.empty());

		assertTrue(keyBuilder.build("99", null, "area_of_interest", "csv").isEmpty());
	}

	@Test
	void build_IsEmptyWhenTheLevel3IsUnknown() {
		when(level2Repository.findById("35")).thenReturn(Optional.of(level2("São Paulo")));
		when(level3Repository.findById("999")).thenReturn(Optional.empty());

		assertTrue(keyBuilder.build("35", "999", "area_of_interest", "csv").isEmpty());
	}

	@Test
	void build_IsEmptyWhenTheNameHasNoUsableCharacter() {
		when(level2Repository.findById("35")).thenReturn(Optional.of(level2("///")));

		assertTrue(keyBuilder.build("35", null, "area_of_interest", "csv").isEmpty());
	}

	@Test
	void slugify_DoesNotTruncateLikeTheDownloadFileName() {
		String longName = "Sao Joao do Rio do Peixe da Serra do Cabral de Baixo";

		String slug = GeoFileObjectKeyBuilder.slugify(longName).orElseThrow();

		assertEquals(
				"sao-joao-do-rio-do-peixe-da-serra-do-cabral-de-baixo",
				slug);
	}

	private static TerritoryLevel2 level2(String name) {
		TerritoryLevel2 territory = new TerritoryLevel2();
		territory.setName(name);
		return territory;
	}

	private static TerritoryLevel3 level3(String name) {
		TerritoryLevel3 territory = new TerritoryLevel3();
		territory.setName(name);
		return territory;
	}
}
