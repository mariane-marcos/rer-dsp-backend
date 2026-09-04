package br.car.dsp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStorageClientProviderTest {

	@Test
	void client_IsPresentWhenEveryConnectionFieldIsFilled() {
		ObjectStorageClientProvider provider = new ObjectStorageClientProvider(properties(
				"http://storage:9000", "dsp-geo-files", "ak", "sk"));

		assertTrue(provider.client().isPresent());
		provider.close();
	}

	@Test
	void client_IsEmptyWithoutAnEndpoint() {
		assertTrue(new ObjectStorageClientProvider(properties(null, "dsp-geo-files", "ak", "sk"))
				.client().isEmpty());
	}

	@Test
	void client_IsEmptyWhenCredentialsAreMissing() {
		assertTrue(new ObjectStorageClientProvider(properties("http://storage:9000", "b", "", ""))
				.client().isEmpty());
	}

	@Test
	void client_IsEmptyForAnUnusableEndpointInsteadOfFailingStartup() {
		assertTrue(new ObjectStorageClientProvider(properties(":::not a uri", "b", "ak", "sk"))
				.client().isEmpty());
	}

	@Test
	void isConfigured_RequiresEndpointBucketAndCredentials() {
		assertTrue(properties("http://storage:9000", "b", "ak", "sk").isConfigured());
		assertFalse(properties("http://storage:9000", "", "ak", "sk").isConfigured());
	}

	private static ObjectStorageProperties properties(String endpoint, String bucket,
			String accessKey, String secretKey) {
		ObjectStorageProperties properties = new ObjectStorageProperties();
		properties.setEndpoint(endpoint);
		properties.setBucket(bucket);
		properties.setAccessKey(accessKey);
		properties.setSecretKey(secretKey);
		return properties;
	}
}
