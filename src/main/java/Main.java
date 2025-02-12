import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.SparkSession;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

public class Main {
    private static final String BUCKET_NAME = "test-bucket";

    public static void main(String[] args) {
        LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:s3-latest"))
                .withServices(S3);
        localstack.start();

        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build();

        s3.createBucket(b -> b.bucket(BUCKET_NAME));

        String folderNameEmpty = "";
        String folderNameNonEmptyWorks = "folder";
        String folder = folderNameEmpty;
        // Uncomment the below line to fix the exceptions: if the folder is not root-level (contains a `/something` part), it works
//          folder = folderNameNonEmptyWorks;

        SparkSession sparkSession = SparkSession.builder()
                .appName("IcebergRootPathTest")
                .master("local[*]")
                .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
                .config("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkCatalog")
                .config("spark.sql.catalog.spark_catalog.type", "hadoop")
                .config("spark.sql.catalog.spark_catalog.warehouse", "s3a://" + BUCKET_NAME + "/" + folder)
                .config("spark.hadoop.fs.s3a.access.key", localstack.getAccessKey())
                .config("spark.hadoop.fs.s3a.secret.key", localstack.getSecretKey())
                .config("spark.hadoop.fs.s3a.endpoint", localstack.getEndpointOverride(S3).toString())
                .config("spark.hadoop.fs.s3a.path.style.access", "true")
                .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
                .getOrCreate();

        try {
            sparkSession.catalog().listDatabases();
        } catch (NoSuchNamespaceException e) {
            System.out.println("Expected normal non-buggy behavior for non-existing namespace, NoSuchNamespaceException thrown");
        } catch (IllegalArgumentException e) {
            assert e.getMessage().equals("path must be absolute");
            System.out.println("buggy behavior: IllegalArgumentException for wrongly formatted path instead of expected NoSuchNamespaceException");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        try {
            sparkSession.catalog().listTables("namespace");
        } catch (IllegalArgumentException e) {
            assert (e.getMessage().equals("Relative path in absolute URI: s3a://test-bucketnamespace"));
            System.out.println("buggy behavior: IllegalArgumentException for wrongly formatted path instead of expected NoSuchNamespaceException");
            System.out.println(e.getMessage());
            e.printStackTrace();
        } catch (AnalysisException|NoSuchNamespaceException ignored) {
            System.out.println("Expected normal non-buggy behavior for non-existing namespace, NoSuchNamespaceException thrown");
        } finally {
            sparkSession.stop();
            localstack.stop();
        }
    }
}