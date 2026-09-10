package br.car.dsp.service;

import br.car.dsp.config.ObjectStorageClientProvider;
import br.car.dsp.config.ObjectStorageProperties;
import br.car.dsp.support.GeoFileObjectKeyBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service exists to answer "is there a published file"; every failure has to read as "no",
 * because the caller's fallback is the WFS.
 */
class PreGeneratedGeoFileServiceTest {

	private static final String KEY = "csv/level-2/sao-paulo_area_of_interest.csv";

	private final S3Client s3Client = mock(S3Client.class);
	private final GeoFileObjectKeyBuilder keyBuilder = mock(GeoFileObjectKeyBuilder.class);
	private ObjectStorageProperties properties;
	private PreGeneratedGeoFileService service;

	@BeforeEach
	void setUp() {
		properties = new ObjectStorageProperties();
		properties.setEndpoint("http://storage:9000");
		properties.setBucket("dsp-geo-files");
		properties.setAccessKey("ak");
		properties.setSecretKey("sk");
		service = new PreGeneratedGeoFileService(provider(s3Client), properties, keyBuilder);
	}

	@Test
	void isEnabled_FollowsWhetherTheProviderHasAClient() {
		assertTrue(service.isEnabled());
		assertFalse(new PreGeneratedGeoFileService(provider(null), properties, keyBuilder).isEnabled());
	}

	@Test
	void fetch_ReturnsThePublishedBytes() {
		byte[] content = "FID,id\n".getBytes(StandardCharsets.UTF_8);
		givenKey();
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
				ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

		assertArrayEquals(content, service.fetch("35", null, "area_of_interest", "csv").orElseThrow());
	}

	@Test
	void fetch_IsEmptyWhenTheFileWasNotPublishedYet() {
		givenKey();
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
				.thenThrow(NoSuchKeyException.builder().build());

		assertTrue(service.fetch("35", null, "area_of_interest", "csv").isEmpty());
	}

	@Test
	void fetch_IsEmptyWhenTheStorageIsUnreachable() {
		givenKey();
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
				.thenThrow((S3Exception) S3Exception.builder().statusCode(503).build());

		assertTrue(service.fetch("35", null, "area_of_interest", "csv").isEmpty());
	}

	@Test
	void fetch_DoesNotCallTheStorageWithoutAKey() {
		when(keyBuilder.build("35", null, "area_of_interest", "csv")).thenReturn(Optional.empty());

		assertTrue(service.fetch("35", null, "area_of_interest", "csv").isEmpty());
		verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
	}

	@Test
	void fetch_DoesNotCallTheStorageWhenThereIsNone() {
		PreGeneratedGeoFileService disabled = new PreGeneratedGeoFileService(
				provider(null), new ObjectStorageProperties(), keyBuilder);

		assertTrue(disabled.fetch("35", null, "area_of_interest", "csv").isEmpty());
		verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
	}

	@Test
	void findGeneratedAt_ReadsTheUserMetadataWrittenByTheJob() {
		givenKey();
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
				HeadObjectResponse.builder()
						.metadata(Map.of("generated-at", "2026-03-04T10:00:00Z"))
						.build());

		assertEquals(
				Optional.of("2026-03-04T10:00:00Z"),
				service.findGeneratedAt("35", null, "area_of_interest", "csv"));
	}

	@Test
	void findGeneratedAt_IsEmptyWhenTheFileCarriesNoDate() {
		givenKey();
		when(s3Client.headObject(any(HeadObjectRequest.class)))
				.thenReturn(HeadObjectResponse.builder().metadata(Map.of()).build());

		assertTrue(service.findGeneratedAt("35", null, "area_of_interest", "csv").isEmpty());
	}

	@Test
	void findGeneratedAt_IgnoresLegacyLastUpdateMetadata() {
		givenKey();
		when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
				HeadObjectResponse.builder()
						.metadata(Map.of("last-update", "2026-03-04T10:00:00Z"))
						.build());

		assertTrue(service.findGeneratedAt("35", null, "area_of_interest", "csv").isEmpty());
	}

	@Test
	void findGeneratedAt_TreatsA404AsMissingBecauseSomeEndpointsDoNotTypeIt() {
		givenKey();
		when(s3Client.headObject(any(HeadObjectRequest.class)))
				.thenThrow((S3Exception) S3Exception.builder().statusCode(404).build());

		assertTrue(service.findGeneratedAt("35", null, "area_of_interest", "csv").isEmpty());
	}

	@Test
	void findGeneratedAt_IsEmptyWhenTheStorageFails() {
		givenKey();
		when(s3Client.headObject(any(HeadObjectRequest.class)))
				.thenThrow((S3Exception) S3Exception.builder().statusCode(500).build());

		assertTrue(service.findGeneratedAt("35", null, "area_of_interest", "csv").isEmpty());
	}

	private void givenKey() {
		when(keyBuilder.build("35", null, "area_of_interest", "csv")).thenReturn(Optional.of(KEY));
	}

	private static ObjectStorageClientProvider provider(S3Client client) {
		ObjectStorageClientProvider provider = mock(ObjectStorageClientProvider.class);
		when(provider.client()).thenReturn(Optional.ofNullable(client));
		return provider;
	}
}
