package br.car.dsp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to the object storage that holds the pre-generated download files.
 * An empty {@code endpoint} disables the whole feature: downloads keep coming from the WFS.
 */
@ConfigurationProperties(prefix = "dsp.object-storage")
public class ObjectStorageProperties {

	private String endpoint;

	private String region = "us-east-1";

	private String bucket;

	private String accessKey;

	private String secretKey;

	/**
	 * Keeps the bucket in the path instead of the host name. Endpoints that are not AWS
	 * usually need it on.
	 */
	private boolean pathStyleAccess = true;

	public boolean isConfigured() {
		return isFilled(endpoint) && isFilled(bucket) && isFilled(accessKey) && isFilled(secretKey);
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public boolean isPathStyleAccess() {
		return pathStyleAccess;
	}

	public void setPathStyleAccess(boolean pathStyleAccess) {
		this.pathStyleAccess = pathStyleAccess;
	}

	private static boolean isFilled(String value) {
		return value != null && !value.isBlank();
	}
}
