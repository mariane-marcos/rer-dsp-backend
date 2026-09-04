package br.car.dsp.config;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Holds the S3 client of the object storage, or nothing when it is not configured.
 *
 * <p>The bean always exists and answers {@link Optional#empty()} when there is no storage —
 * an installation without pre-generated files is normal, not a startup failure, and the
 * download falls back to the WFS.
 */
@Slf4j
@Component
public class ObjectStorageClientProvider {

	private final S3Client s3Client;

	public ObjectStorageClientProvider(ObjectStorageProperties properties) {
		this.s3Client = createClient(properties);
	}

	public Optional<S3Client> client() {
		return Optional.ofNullable(s3Client);
	}

	@PreDestroy
	void close() {
		if (s3Client != null) {
			s3Client.close();
		}
	}

	private static S3Client createClient(ObjectStorageProperties properties) {
		if (!properties.isConfigured()) {
			log.info("Object storage not configured — downloads will be served by the WFS");
			return null;
		}

		URI endpoint;
		try {
			endpoint = new URI(properties.getEndpoint());
		} catch (URISyntaxException ex) {
			// An unusable endpoint must not stop the application: the WFS still answers.
			log.error("Invalid dsp.object-storage.endpoint '{}' — falling back to the WFS: {}",
					properties.getEndpoint(), ex.getMessage());
			return null;
		}

		log.info("Object storage enabled: bucket '{}' at {}", properties.getBucket(), endpoint);
		return S3Client.builder()
				.endpointOverride(endpoint)
				.region(Region.of(properties.getRegion()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
				.serviceConfiguration(S3Configuration.builder()
						.pathStyleAccessEnabled(properties.isPathStyleAccess())
						.build())
				.build();
	}
}
